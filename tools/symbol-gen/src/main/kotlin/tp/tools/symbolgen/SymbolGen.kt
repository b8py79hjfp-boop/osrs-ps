package tp.tools.symbolgen

import tp.cache.config.Js5Archive
import tp.cache.js5.Js5Cache
import tp.util.buf.ReadBuf
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Generates `tp.symbols.*` constants from cache archive 24 (Jagex gamevals). DECISIONS D-014.
 *
 * Output is **Java** source on purpose: large namespaces (objs 34,766, locs 63,320) exceed one class's 65,535-entry
 * constant pool, so each namespace is a chain of classes (`Locs extends Locs$1 ...`) of at most [CHUNK] constants.
 * Kotlin resolves inherited Java statics through the subclass, and they stay compile-time constants (usable in `when`).
 *
 * Gameval file encodings: RuneLite 1.13.6 `cache/.../loaders/GameValLoader.java`.
 */
object SymbolGen {
    /** Max constants per generated class; ~2 constant-pool entries each keeps us well under 65,535. */
    const val CHUNK = 20_000
    const val PACKAGE = "tp.symbols"

    /** Archive-24 group ids (RuneLite `GameValLoader`, RS Mod `Js5GameValGroup`). */
    private const val GROUP_DBTABLE = 10
    private const val GROUP_IFTYPE_LEGACY = 13
    private const val GROUP_IFTYPE = 14
    private const val COMPONENT_END = 0xFFFF
    private const val COMPONENT_SHIFT = 16
    private const val COMPONENT_MASK = 0xFFFF

    /** Flat namespaces: group id -> generated class name. Groups 16-19 are unnamed by every reference (OPEN). */
    private val FLAT: Map<Int, String> =
        linkedMapOf(
            0 to "Objs",
            1 to "Npcs",
            2 to "Invs",
            3 to "Varps",
            4 to "Varbits",
            6 to "Locs",
            7 to "Seqs",
            8 to "SpotAnims",
            9 to "DbRows",
            11 to "Jingles",
            12 to "Sprites",
            15 to "Varcs",
            16 to "Gameval16",
            17 to "Gameval17",
            18 to "Gameval18",
            19 to "Gameval19",
        )

    data class Report(val namespace: String, val count: Int, val renamed: Int)

    /**
     * Names added by `tools/cache-pack` (`custom-symbols.tsv`: type, name, id): invs / varps / varbits join their flat
     * namespace, interfaces and `iface:comp` components join Interfaces / Components, clientscripts and procs go to
     * Clientscripts (D-074).
     */
    class Custom(val rows: List<Triple<String, String, Int>>) {
        fun of(type: String): List<Pair<Int, String>> = rows.filter { it.first == type }.map { it.third to it.second }

        companion object {
            fun read(path: Path?): Custom {
                if (path == null || !Files.isRegularFile(path)) return Custom(emptyList())
                val rows =
                    Files.readAllLines(path).filter { it.isNotBlank() && !it.startsWith("#") }.map {
                        val f = it.split('\t')
                        Triple(f[0], f[1], f[2].toInt())
                    }
                return Custom(rows)
            }
        }
    }

    private val CUSTOM_FLAT = mapOf("Invs" to "inv", "Varps" to "varp", "Varbits" to "varbit")

    /** Writes every namespace under [outDir]/tp/symbols and returns a per-namespace report. */
    fun generate(cache: Js5Cache, outDir: Path, custom: Custom = Custom(emptyList())): List<Report> {
        val pkgDir = outDir.resolve(PACKAGE.replace('.', '/'))
        Files.createDirectories(pkgDir)
        val index = cache.index(Js5Archive.GAMEVALS)
        val present = index.groupIds.toSet()
        val reports = ArrayList<Report>()

        for ((group, className) in FLAT) {
            if (group !in present) continue
            val files = cache.readFiles(Js5Archive.GAMEVALS, group) ?: continue
            val entries = (0 until files.size).map { files.ids[it] to String(files.data[it], Charsets.UTF_8) }
            reports += writeFlat(pkgDir, className, entries + CUSTOM_FLAT[className]?.let { custom.of(it) }.orEmpty())
        }
        if (GROUP_DBTABLE in present) reports += writeDbTables(cache, pkgDir)
        if (GROUP_IFTYPE in present) reports += writeInterfaces(cache, pkgDir, custom)
        reports += writeClientscripts(pkgDir, custom.of("clientscript") + custom.of("proc"))
        return reports
    }

    private fun writeFlat(dir: Path, className: String, entries: List<Pair<Int, String>>): Report {
        val names = Names()
        val consts = entries.sortedBy { it.first }.map { (id, raw) -> names.unique(raw, id) to id }
        val chunks = consts.chunked(CHUNK)
        // Chain: Name$N (base) <- ... <- Name$1 <- Name (final). Only the last class is meant to be referenced.
        for ((i, chunk) in chunks.withIndex()) {
            val isTop = i == chunks.lastIndex
            val name = if (isTop) className else "$className$${chunks.size - 1 - i}"
            val parent =
                if (i == 0) {
                    null
                } else if (chunks.size - i == 1) {
                    "$className$1"
                } else {
                    "$className$${chunks.size - i}"
                }
            val sb = StringBuilder()
            sb.header()
            sb.append(if (isTop) "public final class $name" else "public class $name")
            if (parent != null) sb.append(" extends ").append(parent)
            sb.append(" {\n")
            sb.append(if (isTop) "    private $name() {}\n" else "    protected $name() {}\n")
            for ((n, id) in chunk) {
                sb
                    .append("    public static final int ")
                    .append(n)
                    .append(" = ")
                    .append(id)
                    .append(";\n")
            }
            sb.append("}\n")
            Files.writeString(dir.resolve("$name.java"), sb)
        }
        return Report(className, consts.size, names.renamed)
    }

    /** dbtable files: u8 (unused), name, then (u8 non-zero marker, column name)* until 0. Column index = position. */
    private fun writeDbTables(cache: Js5Cache, dir: Path): Report {
        val files = cache.readFiles(Js5Archive.GAMEVALS, GROUP_DBTABLE) ?: return Report("DbTables", 0, 0)
        val tableNames = Names()
        val sb = StringBuilder().header()
        sb.append("public final class DbTables {\n    private DbTables() {}\n")
        for (i in 0 until files.size) {
            val buf = ReadBuf(files.data[i])
            buf.g1()
            val table = tableNames.unique(buf.gjstr(), files.ids[i])
            sb.append("    public static final class ").append(pascal(table)).append(" {\n")
            sb.append("        private ").append(pascal(table)).append("() {}\n")
            sb.append("        public static final int ID = ").append(files.ids[i]).append(";\n")
            val cols = Names(reserved = setOf("ID"))
            var col = 0
            while (buf.isReadable && buf.g1() != 0) {
                sb
                    .append(
                        "        public static final int COL_",
                    ).append(cols.unique(buf.gjstr(), col))
                    .append(" = ")
                    .append(col)
                    .append(";\n")
                col++
            }
            sb.append("    }\n")
        }
        sb.append("}\n")
        Files.writeString(dir.resolve("DbTables.java"), sb)
        return Report("DbTables", files.size, tableNames.renamed)
    }

    /** iftype files: name, then (u16 component id, name)* until 0xFFFF. Emits Interfaces (ids) and Components (packed). */
    private fun writeClientscripts(dir: Path, entries: List<Pair<Int, String>>): Report {
        val sb = StringBuilder().header().append("public final class Clientscripts {\n    private Clientscripts() {}\n")
        val names = Names()
        for ((id, name) in entries.sortedBy { it.first }) {
            sb
                .append("    public static final int ")
                .append(names.unique(name, id))
                .append(" = ")
                .append(id)
                .append(";\n")
        }
        Files.writeString(dir.resolve("Clientscripts.java"), sb.append("}\n"))
        return Report("Clientscripts", entries.size, 0)
    }

    private fun writeInterfaces(cache: Js5Cache, dir: Path, custom: Custom): List<Report> {
        val files = cache.readFiles(Js5Archive.GAMEVALS, GROUP_IFTYPE) ?: return emptyList()
        val ifNames = Names()
        val ifaces = StringBuilder().header().append("public final class Interfaces {\n    private Interfaces() {}\n")
        val comps = StringBuilder().header().append("public final class Components {\n    private Components() {}\n")
        var componentCount = 0
        for (i in 0 until files.size) {
            val id = files.ids[i]
            val buf = ReadBuf(files.data[i])
            val name = ifNames.unique(buf.gjstr(), id)
            ifaces
                .append("    public static final int ")
                .append(name)
                .append(" = ")
                .append(id)
                .append(";\n")
            comps.append("    public static final class ").append(pascal(name)).append(" {\n")
            comps.append("        private ").append(pascal(name)).append("() {}\n")
            val compNames = Names()
            while (buf.isReadable) {
                val comp = buf.g2()
                if (comp == COMPONENT_END) break
                val packed = (id shl COMPONENT_SHIFT) or comp
                comps
                    .append("        public static final int ")
                    .append(compNames.unique(buf.gjstr(), comp))
                    .append(" = ")
                    .append(packed)
                    .append(";\n")
                componentCount++
            }
            comps.append("    }\n")
        }
        val customComps = custom.of("component").groupBy({ it.second.substringBefore(':') }, { it.second.substringAfter(':') to it.first })
        for ((id, ifName) in custom.of("interface")) {
            val name = ifNames.unique(ifName, id)
            ifaces
                .append("    public static final int ")
                .append(name)
                .append(" = ")
                .append(id)
                .append(";\n")
            comps.append("    public static final class ").append(pascal(name)).append(" {\n")
            comps.append("        private ").append(pascal(name)).append("() {}\n")
            val compNames = Names()
            for ((comp, packed) in customComps[ifName].orEmpty()) {
                comps.append("        public static final int ").append(compNames.unique(comp, packed and COMPONENT_MASK)).append(" = ")
                comps.append(packed).append(";\n")
                componentCount++
            }
            comps.append("    }\n")
        }
        Files.writeString(dir.resolve("Interfaces.java"), ifaces.append("}\n"))
        Files.writeString(dir.resolve("Components.java"), comps.append("}\n"))
        return listOf(Report("Interfaces", files.size, ifNames.renamed), Report("Components", componentCount, 0))
    }

    private fun StringBuilder.header(): StringBuilder =
        append("// GENERATED by tools/symbol-gen from cache archive 24 (Jagex gamevals). Do not edit.\n")
            .append("package ")
            .append(PACKAGE)
            .append(";\n\n")
            .append("@SuppressWarnings(\"unused\")\n")

    /** Upper-snake identifiers; collisions get `_<id>` appended. */
    private class Names(reserved: Set<String> = emptySet()) {
        private val used = HashSet<String>(reserved)
        var renamed = 0
            private set

        fun unique(raw: String, id: Int): String {
            val base = sanitize(raw)
            if (used.add(base)) return base
            renamed++
            val alt = "${base}_$id"
            check(used.add(alt)) { "Duplicate symbol $alt" }
            return alt
        }
    }

    private val JAVA_IDENT = Regex("[^A-Za-z0-9_]")
    private const val UNNAMED = "UNNAMED"

    fun sanitize(raw: String): String {
        val s =
            raw
                .trim()
                .uppercase()
                .replace(JAVA_IDENT, "_")
        // Blank / punctuation-only gameval names: `_` is a reserved identifier in Java 9+.
        if (s.all { it == '_' }) return UNNAMED
        return if (s[0].isDigit()) "_$s" else s
    }

    /** Nested class name for a namespace member: `PRAYERBOOK` -> `Prayerbook`, `_100GUIDE_X` -> `_100guide_x`. */
    private fun pascal(upper: String): String =
        upper.lowercase().split('_').joinToString("_") { part -> part.replaceFirstChar { it.uppercaseChar() } }
}

fun main(args: Array<String>) {
    if (args.size !in 2..3) {
        System.err.println("usage: symbol-gen <cache-dir> <out-dir> [custom-symbols.tsv]")
        exitProcess(2)
    }
    val cacheDir = Path.of(args[0])
    if (!Files.isDirectory(cacheDir)) {
        System.err.println("Cache not found at $cacheDir — run ./gradlew setup first.")
        exitProcess(1)
    }
    val out = Path.of(args[1])
    out.toFile().deleteRecursively()
    Js5Cache.open(cacheDir).use { cache ->
        val custom = SymbolGen.Custom.read(args.getOrNull(2)?.let { Path.of(it) })
        for (r in SymbolGen.generate(cache, out, custom)) {
            println("${r.namespace}: ${r.count} symbols (${r.renamed} renamed for collisions)")
        }
    }
}
