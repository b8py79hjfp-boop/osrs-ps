package tp.build

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
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

/**
 * `./gradlew setupClient`: mirrors the RuneLite bootstrap pinned in gradle.properties (`osrs.runeliteBootstrap`, a
 * `runelite/static.runelite.net` commit, DECISIONS D-007) into `.data/client/`:
 * - `bootstrap.json`, fetched at that commit, so its content can't change;
 * - every artifact it lists, in `artifacts/`, each checked against the sha256 from the bootstrap.
 *
 * These are Jagex/RuneLite binaries, so they never enter git (brief §0.6). In CI they come from the private artifact
 * store instead; the same checksums apply.
 */
@UntrackedTask(because = "Downloads external artifacts; verified by checksum inside the action.")
public abstract class SetupClientTask : DefaultTask() {
    @get:Input
    public abstract val bootstrapCommit: Property<String>

    @get:Internal
    public abstract val outputDir: DirectoryProperty

    @TaskAction
    public fun run() {
        val out = outputDir.get().asFile
        val artifactsDir = File(out, "artifacts").apply { mkdirs() }
        val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(TIMEOUT).build()
        val commit = bootstrapCommit.get()
        val bootstrapFile = File(out, "bootstrap.json")
        download(http, "$BOOTSTRAP_REPO/$commit/bootstrap.json", bootstrapFile)

        @Suppress("UNCHECKED_CAST")
        val bootstrap = JsonSlurper().parse(bootstrapFile) as Map<String, Any?>
        val artifacts = bootstrap["artifacts"] as? List<Map<String, Any?>> ?: throw GradleException("bootstrap.json has no artifacts")
        var fetched = 0
        for (a in artifacts) {
            val name = a["name"].toString()
            val hash = a["hash"].toString()
            val target = File(artifactsDir, name)
            if (target.isFile && sha256(target).equals(hash, ignoreCase = true)) continue
            download(http, a["path"].toString(), target)
            val actual = sha256(target)
            if (!actual.equals(hash, ignoreCase = true)) {
                target.delete()
                throw GradleException("sha256 mismatch for $name: expected $hash, got $actual")
            }
            fetched++
        }
        logger.lifecycle("RuneLite ${bootstrap["version"]} bootstrap ${commit.take(SHORT)}: ${artifacts.size} artifacts verified ($fetched downloaded) in $artifactsDir")
    }

    private fun download(http: HttpClient, url: String, target: File) {
        val tmp = File(target.parentFile, target.name + ".part")
        val response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).build(), HttpResponse.BodyHandlers.ofFile(tmp.toPath()))
        if (response.statusCode() != HTTP_OK) {
            tmp.delete()
            throw GradleException("GET $url -> ${response.statusCode()}")
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return HexFormat.of().formatHex(md.digest())
    }

    private companion object {
        const val BOOTSTRAP_REPO = "https://raw.githubusercontent.com/runelite/static.runelite.net"
        val TIMEOUT: Duration = Duration.ofSeconds(120)
        const val HTTP_OK = 200
        const val BUFFER = 1 shl 16
        const val SHORT = 8
    }
}
