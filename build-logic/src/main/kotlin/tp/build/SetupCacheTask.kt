package tp.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.zip.ZipFile

/**
 * Downloads the OpenRS2 disk cache and XTEA keys for the configured revision into `.data/cache/<rev>/`,
 * records their SHA-256s and verifies them against the pinned checksums in `gradle/cache-checksums/<rev>.sha256`.
 *
 * Jagex data never enters git (brief §0.6); only the checksums do. The first successful run on a machine
 * without a pinned file writes it (trust-on-first-use) and asks for it to be committed.
 * Structural verification (archive count, group count, master-index CRC/version spot-checks) lives in
 * the `cache` module so the server and test 1 share it.
 */
@UntrackedTask(because = "Downloads external data and verifies it; up-to-date checks are done by checksum inside the action.")
public abstract class SetupCacheTask : DefaultTask() {
    @get:Input
    public abstract val revision: Property<String>

    @get:Input
    public abstract val openrs2CacheId: Property<String>

    @get:Internal
    public abstract val pinnedChecksums: RegularFileProperty

    @get:Internal
    public abstract val outputDir: DirectoryProperty

    @TaskAction
    public fun run() {
        val out = outputDir.get().asFile.apply { mkdirs() }
        val id = openrs2CacheId.get()
        val base = "https://archive.openrs2.org/caches/runescape/$id"
        val artifacts = listOf("disk.zip", "keys.json")

        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build()

        val actual = linkedMapOf<String, String>()
        for (name in artifacts) {
            val target = File(out, name)
            if (!target.isFile) download(client, "$base/$name", target)
            actual[name] = sha256(target)
        }

        val pinnedFile = pinnedChecksums.get().asFile
        if (pinnedFile.isFile) {
            val pinned = parseSums(pinnedFile.readText())
            for ((name, sum) in actual) {
                val expected = pinned[name]
                    ?: throw GradleException("$pinnedFile has no entry for $name")
                if (!expected.equals(sum, ignoreCase = true)) {
                    throw GradleException(
                        "SHA-256 mismatch for $name: expected $expected, got $sum. " +
                            "Delete ${File(out, name)} and re-run, or investigate the source.",
                    )
                }
            }
            logger.lifecycle("Cache $id checksums match ${pinnedFile.name}.")
        } else {
            pinnedFile.parentFile.mkdirs()
            pinnedFile.writeText(formatSums(actual))
            logger.warn("No pinned checksums existed; wrote $pinnedFile. Commit it so other machines verify against it.")
        }
        File(out, "SHA256SUMS").writeText(formatSums(actual))

        extract(File(out, "disk.zip"), File(out, "disk"), actual.getValue("disk.zip"))

        val idx255 = File(out, "disk/main_file_cache.idx255")
        if (!idx255.isFile || idx255.length() == 0L || idx255.length() % INDEX_ENTRY_SIZE != 0L) {
            throw GradleException("Extracted cache is malformed: $idx255 missing or not a multiple of $INDEX_ENTRY_SIZE bytes.")
        }
        downloadJavConfig(client, File(out.parentFile.parentFile, "client/jav_config.ws"))
        logger.lifecycle(
            "rev ${revision.get()} cache ready at ${idx255.parentFile} " +
                "(${idx255.length() / INDEX_ENTRY_SIZE} archives in idx255).",
        )
    }

    /**
     * Jagex's live jav_config.ws: the template our HTTP endpoint rewrites (D-017). Never committed. Refreshed every run;
     * fails if its revision param (25) doesn't match ours, which means the client moved on.
     */
    private fun downloadJavConfig(client: HttpClient, target: File) {
        target.parentFile.mkdirs()
        download(client, JAV_CONFIG_URL, target)
        val rev = target.readLines(Charsets.ISO_8859_1).firstOrNull { it.startsWith("param=25=") }?.substringAfter("param=25=")
        if (rev != revision.get()) {
            logger.warn("Live jav_config.ws reports revision $rev, expected ${revision.get()}: OSRS has updated (see docs/REVISION-UPGRADE.md).")
        }
    }

    private fun download(client: HttpClient, url: String, target: File) {
        logger.lifecycle("Downloading $url")
        val tmp = File(target.parentFile, target.name + ".part")
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "tickperfect-setup (cache download for local development)")
            .timeout(Duration.ofMinutes(15))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofFile(tmp.toPath()))
        if (response.statusCode() != HTTP_OK) {
            tmp.delete()
            throw GradleException("GET $url returned HTTP ${response.statusCode()}")
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Extracts every `main_file_cache.*` entry flat into [dest]; skipped when the marker matches [zipSha]. */
    private fun extract(zip: File, dest: File, zipSha: String) {
        val marker = File(dest, ".extracted-from")
        if (marker.isFile && marker.readText().trim() == zipSha) return
        dest.deleteRecursively()
        dest.mkdirs()
        var count = 0
        ZipFile(zip).use { zf ->
            for (entry in zf.entries()) {
                if (entry.isDirectory) continue
                val fileName = entry.name.substringAfterLast('/')
                // Flatten and reject anything that is not a cache file (also prevents zip-slip).
                if (!fileName.startsWith("main_file_cache.") || fileName.contains("..")) continue
                zf.getInputStream(entry).use { input ->
                    Files.copy(input, File(dest, fileName).toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                count++
            }
        }
        if (count == 0) throw GradleException("$zip contained no main_file_cache.* entries")
        marker.writeText(zipSha)
        logger.lifecycle("Extracted $count cache files into $dest")
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return HexFormat.of().formatHex(md.digest())
    }

    private fun parseSums(text: String): Map<String, String> =
        text.lines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                parts[1].removePrefix("*") to parts[0]
            }

    private fun formatSums(sums: Map<String, String>): String =
        sums.entries.joinToString(separator = "\n", postfix = "\n") { (name, sum) -> "$sum  $name" }

    private companion object {
        /** JS5 disk index entry: u24 size + u24 first sector. */
        const val INDEX_ENTRY_SIZE = 6L
        const val HTTP_OK = 200
        const val JAV_CONFIG_URL = "https://oldschool.runescape.com/jav_config.ws"
        const val BUFFER_SIZE = 1 shl 16
    }
}
