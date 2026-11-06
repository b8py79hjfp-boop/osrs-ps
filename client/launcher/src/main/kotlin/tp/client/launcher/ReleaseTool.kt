package tp.client.launcher

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

/**
 * Release tooling (run on the release machine, never shipped):
 * - `keygen <dir>` writes `manifest-private.key` (keep secret, never in git) and `manifest-public.key` (goes into the
 *   launcher's `launcher.properties` as `publicKey`).
 * - `publish <artifacts-dir> <update-dir> <version> <revision> <private-key-file>` copies every jar from
 *   artifacts-dir into update-dir/artifacts/, writes update-dir/manifest.json and its signature manifest.json.sig.
 *   The hub serves update-dir at `/update`.
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "keygen" -> {
            val dir = Path.of(args[1])
            Files.createDirectories(dir)
            val (public, private) = ManifestSigning.generate()
            Files.writeString(dir.resolve("manifest-public.key"), public)
            Files.writeString(dir.resolve("manifest-private.key"), private)
            println("wrote $dir/manifest-public.key and manifest-private.key (keep the private key out of git)")
        }

        "publish" -> {
            val (artifactsDir, updateDir) = Path.of(args[1]) to Path.of(args[2])
            val key = ManifestSigning.privateKey(Files.readString(Path.of(args[5])))
            val out = updateDir.resolve("artifacts")
            Files.createDirectories(out)
            val jars = Files.list(artifactsDir).use { s -> s.filter { it.toString().endsWith(".jar") }.sorted().toList() }
            val artifacts =
                jars.map { jar ->
                    Files.copy(jar, out.resolve(jar.fileName), StandardCopyOption.REPLACE_EXISTING)
                    Artifact(jar.fileName.toString(), "artifacts/${jar.fileName}", sha256(jar), Files.size(jar))
                }
            val manifest = Manifest(args[3], args[4].toInt(), MAIN_CLASS, JVM_ARGS, artifacts)
            val bytes = manifest.toJson().toByteArray()
            Files.write(updateDir.resolve("manifest.json"), bytes)
            Files.writeString(updateDir.resolve("manifest.json.sig"), ManifestSigning.sign(bytes, key))
            println("published ${artifacts.size} artifacts, manifest ${args[3]} -> $updateDir")
        }

        else -> {
            System.err.println(
                "usage: release keygen <dir> | release publish <artifacts-dir> <update-dir> <version> <revision> <private-key>",
            )
            exitProcess(2)
        }
    }
}

/** RuneLite's client entry point. */
private const val MAIN_CLASS = "net.runelite.client.RuneLite"

/** The bootstrap's `clientJvm17Arguments` minus RuneLite-launcher-only properties (pinned bootstrap b4dab8e7). */
private val JVM_ARGS =
    listOf(
        "-XX:+DisableAttachMechanism",
        "-Xmx768m",
        "-Xss2m",
        "-XX:CompileThreshold=1500",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )
