package tp.client.launcher

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Properties
import kotlin.system.exitProcess

/**
 * The release-client launcher (brief §4.2): fetch the signed manifest from our update endpoint, verify it against the
 * built-in public key, bring the local artifact repository up to date (sha256-checked), and start the client.
 *
 * Offline or update server down: the last manifest that verified (kept with its signature) is used again, so players
 * can still start the client. An unsigned, badly signed or tampered manifest is never used.
 */
fun main(args: Array<String>) {
    val config = LauncherConfig.load()
    val home = Path.of(System.getProperty("user.home"), ".${config.appId}")
    val repo = home.resolve("repository")
    Files.createDirectories(repo)
    val key = ManifestSigning.publicKey(config.publicKey)
    val http =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build()

    val manifest =
        try {
            val body = get(http, "${config.updateUrl}/manifest.json")
            val sig = String(get(http, "${config.updateUrl}/manifest.json.sig"))
            if (!ManifestSigning.verify(body, sig, key)) fail("The update manifest's signature is invalid. Not starting.")
            Files.write(home.resolve("manifest.json"), body)
            Files.writeString(home.resolve("manifest.json.sig"), sig)
            Manifest.parse(String(body))
        } catch (e: IOException) {
            log("update server unreachable ($e); using the last verified manifest")
            cached(home, key) ?: fail("Can't reach the update server and no verified client is installed yet.")
        }

    for (a in manifest.artifacts) {
        val file = repo.resolve(a.name)
        if (Files.isRegularFile(file) && sha256(file) == a.sha256) continue
        log("downloading ${a.name}")
        val tmp = repo.resolve(a.name + ".part")
        Files.write(tmp, get(http, "${config.updateUrl}/${a.path}"))
        if (sha256(tmp) != a.sha256) {
            Files.deleteIfExists(tmp)
            fail("${a.name} failed its integrity check. Not starting.")
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    // Files no longer in the manifest are removed, so old client versions never mix into the classpath.
    val keep = manifest.artifacts.map { it.name }.toSet()
    Files.list(repo).use { files -> files.filter { it.fileName.toString() !in keep }.forEach { Files.deleteIfExists(it) } }

    val javaBin = Path.of(System.getProperty("java.home"), "bin", if (isWindows()) "java.exe" else "java").toString()
    val classpath = manifest.artifacts.joinToString(java.io.File.pathSeparator) { repo.resolve(it.name).toString() }
    val command = listOf(javaBin) + manifest.jvmArgs + listOf("-cp", classpath, manifest.mainClass) + args
    log("starting ${config.appName} ${manifest.version} (revision ${manifest.revision})")
    ProcessBuilder(command).inheritIO().start()
}

private fun cached(home: Path, key: java.security.PublicKey): Manifest? {
    val body = home.resolve("manifest.json")
    val sig = home.resolve("manifest.json.sig")
    if (!Files.isRegularFile(body) || !Files.isRegularFile(sig)) return null
    val bytes = Files.readAllBytes(body)
    return if (ManifestSigning.verify(bytes, Files.readString(sig), key)) Manifest.parse(String(bytes)) else null
}

private fun get(http: HttpClient, url: String): ByteArray {
    val response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).build(), HttpResponse.BodyHandlers.ofByteArray())
    if (response.statusCode() != HTTP_OK) throw IOException("GET $url -> ${response.statusCode()}")
    return response.body()
}

internal fun sha256(file: Path): String {
    val md = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file).use { input ->
        val buf = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

private fun isWindows() = System.getProperty("os.name").lowercase().startsWith("windows")

private fun log(message: String) = println("[launcher] $message")

private fun fail(message: String): Nothing {
    System.err.println("[launcher] $message")
    exitProcess(1)
}

/** `launcher.properties` (built in): app name/id, update URL and the manifest public key. */
class LauncherConfig(val appName: String, val appId: String, val updateUrl: String, val publicKey: String) {
    companion object {
        fun load(): LauncherConfig {
            val p = Properties()
            LauncherConfig::class.java.getResourceAsStream("launcher.properties").use { p.load(it) }
            // A system property may point a dev build at a local hub; the key can't be overridden.
            val url = System.getProperty("launcher.updateUrl") ?: p.getProperty("updateUrl")
            return LauncherConfig(p.getProperty("name"), p.getProperty("id"), url.trimEnd('/'), p.getProperty("publicKey"))
        }
    }
}

private val TIMEOUT: Duration = Duration.ofSeconds(30)
private const val HTTP_OK = 200
private const val BUFFER = 1 shl 16
