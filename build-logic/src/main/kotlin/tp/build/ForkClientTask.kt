package tp.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * `./gradlew forkClient` builds the RuneLite fork (client/runelite):
 * 1. Clone the pinned tag outside the repository, check its commit and apply `patches/` with `git am`.
 * 2. Overlay the allowlist, licenses and branding, and substitute the release tokens from `release.properties`.
 * 3. Build with RuneLite's own Gradle wrapper and copy the shaded jar to `.data/client/fork/`.
 *
 * It is the same as `client/runelite/build.sh`, but runs anywhere Gradle does (Windows without bash). Line endings
 * are pinned to LF, so a Windows checkout with `core.autocrlf=true` still applies the patches.
 */
@UntrackedTask(because = "Clones and builds an external project; always runs.")
public abstract class ForkClientTask : DefaultTask() {
    @get:Internal
    public abstract val repoDir: DirectoryProperty

    @get:Internal
    public abstract val workDir: DirectoryProperty

    /** Clone source: the RuneLite GitHub repository, or a local clone (`-PruneliteGit=/path`) to work offline. */
    @get:Input
    public abstract val runeliteGit: Property<String>

    @get:Input
    public abstract val tag: Property<String>

    /** Extra arguments for RuneLite's Gradle build (`-PforkGradleArgs=--offline`). */
    @get:Input
    @get:Optional
    public abstract val gradleArgs: Property<String>

    @TaskAction
    public fun run() {
        val repo = repoDir.get().asFile
        val here = File(repo, "client/runelite")
        val release = Properties().apply { File(here, "release.properties").reader().use { load(it) } }
        val tokens =
            mapOf(
                "@@NAME@@" to release.required("name"),
                "@@SITE@@" to release.required("site"),
                "@@API@@" to release.required("api"),
            )
        val work = workDir.get().asFile
        if (work.toPath().toAbsolutePath().startsWith(repo.toPath().toAbsolutePath())) throw GradleException("work dir must be outside the repository: $work")
        if (work.exists()) {
            if (!File(work, MARKER).isFile) throw GradleException("$work exists and was not created by forkClient; remove it or pick -PforkWorkDir")
            work.deleteRecursively()
        }
        work.mkdirs()
        File(work, MARKER).writeText("forkClient work dir\n")
        val src = File(work, "src")

        // 1. Clone + verify + patch.
        git(work, "-c", "core.autocrlf=false", "clone", "--quiet", "--depth", "1", "--branch", tag.get(), runeliteGit.get(), src.path)
        val head = git(src, "rev-parse", "HEAD").trim()
        val expected = release.required("base.commit")
        if (head != expected) throw GradleException("tag ${tag.get()} is $head, expected $expected (release.properties base.commit)")
        git(src, "config", "core.autocrlf", "false")
        git(src, "config", "user.name", "tickperfect build")
        git(src, "config", "user.email", "build@localhost")
        val patchDir = File(work, "patches").apply { mkdirs() }
        val patches = File(here, "patches").listFiles { f -> f.name.endsWith(".patch") }?.sortedBy { it.name }.orEmpty()
        if (patches.isEmpty()) throw GradleException("no patches in ${File(here, "patches")}")
        // Normalise to LF: a Windows checkout may have converted the patch files to CRLF.
        val normalised = patches.map { p -> File(patchDir, p.name).also { it.writeText(p.readText().replace("\r\n", "\n")) } }
        git(src, *(listOf("am", "--quiet", "--3way") + normalised.map { it.path }).toTypedArray())

        // 2. Overlay + tokens.
        val res = File(src, "runelite-client/src/main/resources")
        File(here, "allowlist.txt").copyTo(File(res, "net/runelite/client/plugins/allowlist.txt"), overwrite = true)
        File(here, "LICENSES.txt").copyTo(File(res, "LICENSES.txt"), overwrite = true)
        File(here, "branding/resources").copyRecursively(res, overwrite = true)
        for (f in listOf(File(res, "net/runelite/client/runelite.properties"), File(res, "LICENSES.txt"))) {
            var text = f.readText()
            for ((token, value) in tokens) text = text.replace(token, value)
            f.writeText(text)
        }
        val leftover = File(src, "runelite-client/src").walkTopDown().filter { it.isFile && it.extension in TEXT_EXTENSIONS }.firstOrNull { TOKEN.containsMatchIn(it.readText()) }
        if (leftover != null) throw GradleException("unsubstituted release token in $leftover")
        git(src, "add", "-A")
        git(src, "commit", "--quiet", "-m", "Release overlay: allowlist, licenses, branding and tokens")

        // 3. Build with RuneLite's wrapper.
        val windows = System.getProperty("os.name").lowercase().startsWith("windows")
        val wrapper = if (windows) listOf("cmd", "/c", "gradlew.bat") else listOf("sh", "gradlew")
        val extra = gradleArgs.orNull?.split(' ')?.filter { it.isNotBlank() }.orEmpty()
        run(src, wrapper + extra + listOf("--no-daemon", ":client:assemble", "-x", "javadoc"), BUILD_TIMEOUT_MINUTES)
        val version = Properties().apply { File(src, "gradle.properties").reader().use { load(it) } }.getProperty("project.build.version")
        val jar = File(src, "runelite-client/build/libs/client-$version-shaded.jar")
        if (!jar.isFile) throw GradleException("expected $jar was not produced")
        val out = File(repo, ".data/client/fork").apply { mkdirs() }
        jar.copyTo(File(out, jar.name), overwrite = true)
        logger.lifecycle("built ${File(out, jar.name)} (game client not yet patched: run releaseClient)")
    }

    private fun Properties.required(key: String): String = getProperty(key)?.takeIf { it.isNotBlank() } ?: throw GradleException("$key is missing from client/runelite/release.properties")

    private fun git(dir: File, vararg args: String): String = run(dir, listOf("git") + args, GIT_TIMEOUT_MINUTES)

    private fun run(dir: File, command: List<String>, timeoutMinutes: Long): String {
        logger.info("> ${command.joinToString(" ")}")
        val process = ProcessBuilder(command).directory(dir).redirectErrorStream(true).start()
        val output = StringBuilder()
        process.inputStream.bufferedReader().forEachLine { line ->
            output.appendLine(line)
            logger.lifecycle(line)
        }
        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw GradleException("timed out: ${command.joinToString(" ")}")
        }
        if (process.exitValue() != 0) throw GradleException("failed (${process.exitValue()}): ${command.joinToString(" ")}")
        return output.toString()
    }

    private companion object {
        const val MARKER = ".tp-runelite-work"
        val TOKEN = Regex("@@[A-Z]+@@")
        val TEXT_EXTENSIONS = setOf("java", "properties", "txt", "json", "kts")
        const val GIT_TIMEOUT_MINUTES = 10L
        const val BUILD_TIMEOUT_MINUTES = 30L
    }
}
