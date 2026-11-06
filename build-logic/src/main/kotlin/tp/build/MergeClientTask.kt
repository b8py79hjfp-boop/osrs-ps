package tp.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Puts the patched game client into the fork's shaded jar: every class that exists in [patched] replaces the entry
 * of the same name in [forkJar] (the shaded jar bundles RuneLite's unpatched injected client). Fails if any patched
 * class other than the ones gamepatch adds (`tp/gamepatch/`) is missing from the fork jar, which means the fork was built against a different game client.
 */
@UntrackedTask(because = "Rewrites a jar in place; cheap and always run.")
public abstract class MergeClientTask : DefaultTask() {
    @get:Internal
    public abstract val forkJar: RegularFileProperty

    @get:Internal
    public abstract val patched: RegularFileProperty

    @TaskAction
    public fun run() {
        val fork = forkJar.get().asFile
        val patchedJar = patched.get().asFile
        if (!fork.isFile) throw GradleException("missing $fork: run forkClient first")
        val replacements = HashMap<String, ByteArray>()
        ZipFile(patchedJar).use { z -> for (e in z.entries()) if (e.name.endsWith(".class")) replacements[e.name] = z.getInputStream(e).readBytes() }
        val tmp = File(fork.parentFile, fork.name + ".tmp")
        val replaced = HashSet<String>()
        ZipFile(fork).use { z ->
            ZipOutputStream(tmp.outputStream()).use { out ->
                for (e in z.entries()) {
                    val bytes = replacements[e.name]?.also { replaced += e.name } ?: z.getInputStream(e).readBytes()
                    out.putNextEntry(ZipEntry(e.name))
                    out.write(bytes)
                    out.closeEntry()
                }
                // Classes the patcher adds (tp/gamepatch/...) are new, not replacements.
                for ((name, bytes) in replacements) {
                    if (name in replaced || !name.startsWith(ADDED_PREFIX)) continue
                    replaced += name
                    out.putNextEntry(ZipEntry(name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }
        val missing = replacements.keys - replaced
        if (missing.isNotEmpty()) {
            tmp.delete()
            throw GradleException("${missing.size} patched game-client classes are not in the fork jar (e.g. ${missing.first()})")
        }
        if (!fork.delete() || !tmp.renameTo(fork)) throw GradleException("could not replace $fork")
        logger.lifecycle("release client ready: $fork (${replaced.size} game-client classes patched)")
    }

    private companion object {
        /** Package of the helper classes gamepatch adds to the game client (LoginNagPatch.HELPER). */
        const val ADDED_PREFIX = "tp/gamepatch/"
    }
}
