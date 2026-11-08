package tp.tools.cachepack

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * One `pack/` source directory:
 *
 * ```
 * pack/cs2/script/NAME.cs2      ClientScript sources ([clientscript,..], [proc,..])
 * pack/cs2/symbols/NAME.sym     extra Neptune symbols (varp, varc, param, if_script, ...)
 * pack/interfaces/NAME.if3      interface definitions (TOML, see [InterfacePacker])
 * pack/configs/NAME.toml        inv / varp / varbit definitions (see [ConfigPacker])
 * pack/cs2/patch/NAME.diff      diffs of existing client scripts (see [ScriptPatches])
 * pack/sprites/GROUP/NN_x.png   frames appended to existing sprite group GROUP (see [SpriteAppends])
 * pack/interface-patches/X.toml changes to existing interfaces (see [InterfacePatches])
 * ```
 */
internal class PackSource(val root: Path) {
    val scripts: List<Path> = filesIn(root.resolve("cs2/script"), "cs2")
    val symbols: List<Path> = filesIn(root.resolve("cs2/symbols"), "sym")
    val interfaces: List<Path> = filesIn(root.resolve("interfaces"), "if3")
    val configs: List<Path> = filesIn(root.resolve("configs"), "toml")
    val patches: List<Path> = filesIn(root.resolve("cs2/patch"), "diff")
    val interfacePatches: List<Path> = filesIn(root.resolve("interface-patches"), "toml")
    val spriteDirs: List<Path> =
        root
            .resolve("sprites")
            .takeIf { it.isDirectory() }
            ?.let { d ->
                Files.list(d).use { s -> s.filter { it.isDirectory() }.sorted().toList() }
            }.orEmpty()

    val isEmpty: Boolean get() =
        scripts.isEmpty() && interfaces.isEmpty() && configs.isEmpty() && patches.isEmpty() && spriteDirs.isEmpty() &&
            interfacePatches.isEmpty()

    override fun toString(): String = root.toString()

    companion object {
        /** Content modules keep their pack sources at `content/<module>/src/main/resources/pack`. */
        fun discover(repoRoot: Path): List<PackSource> {
            val content = repoRoot.resolve("content")
            if (!content.isDirectory()) return emptyList()
            return Files.newDirectoryStream(content).use { modules ->
                modules
                    .filter { it.isDirectory() }
                    .sortedBy { it.name }
                    .map { it.resolve("src/main/resources/pack") }
                    .filter { it.isDirectory() }
                    .map(::PackSource)
            }
        }

        private fun filesIn(dir: Path, extension: String): List<Path> {
            if (!dir.isDirectory()) return emptyList()
            return Files.walk(dir).use { s -> s.filter { Files.isRegularFile(it) && it.extension == extension }.sorted().toList() }
        }
    }
}

/** A script's identity as Neptune writes it: `[trigger,name]`. */
internal data class ScriptName(val trigger: String, val name: String) {
    val full: String get() = "[$trigger,$name]"

    companion object {
        private val HEADER = Regex("""^\[([a-z_]+),([A-Za-z0-9_.:*]+)]""")

        /** Reads the `[trigger,name]` header of a `.cs2` source (the file may hold several scripts; each starts a line). */
        fun headers(source: Path): List<ScriptName> =
            Files.readAllLines(source).mapNotNull { line -> HEADER.find(line)?.let { ScriptName(it.groupValues[1], it.groupValues[2]) } }

        fun parse(full: String): ScriptName {
            val m = HEADER.find(full) ?: throw IOException("Not a script name: $full")
            return ScriptName(m.groupValues[1], m.groupValues[2])
        }
    }
}
