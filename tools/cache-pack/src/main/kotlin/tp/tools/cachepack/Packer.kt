package tp.tools.cachepack

import tp.cache.config.Js5Archive
import tp.cache.js5.Js5Cache
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class PackOptions(
    /** The untouched source cache directory (`main_file_cache.*`). */
    val sourceDisk: Path,
    /** Output root: gets `disk/`, `keys.json`, `custom-symbols.tsv` and `work/`. */
    val outRoot: Path,
    val packs: List<PackSource>,
)

internal class PackReport(
    val customSymbols: List<CustomSymbol>,
    val groupsWritten: List<String>,
    val indexes: List<Triple<Int, Int, Int>>,
    val duplicateNames: Map<String, Int>,
)

/**
 * The whole run: assign ids, compile scripts, encode interfaces and configs, write the groups and indexes into a copy of the
 * source cache, and record the new names. The source cache is only read.
 */
internal object Packer {
    fun run(options: PackOptions): PackReport {
        if (options.packs.isEmpty() || options.packs.all { it.isEmpty }) throw IOException("No pack sources found")
        val outDisk = options.outRoot.resolve("disk")
        val work = options.outRoot.resolve("work")
        Js5Cache.open(options.sourceDisk).use { source ->
            val symbols = CacheSymbols.load(source)
            val custom = CustomIds()

            // 1. Parse everything first: nothing is written until the whole input is understood.
            val configs = ConfigPacker.parse(options.packs.flatMap { it.configs })
            val interfaces =
                options.packs
                    .flatMap { it.interfaces }
                    .map(InterfaceDef::parse)
                    .sortedBy { it.name }
            val scriptSources = options.packs.flatMap { it.scripts }
            val scriptNames = scriptSources.flatMap { ScriptName.headers(it).map { n -> n to it } }
            if (scriptNames.map { it.first }.distinct().size !=
                scriptNames.size
            ) {
                throw IOException("Duplicate script names in the pack sources")
            }

            // 2. Ids. Everything new goes above the cache's maximum so no existing id can collide.
            val legacy = Resources.sym("legacy.sym")
            assignIds(source, symbols, custom, configs, interfaces, scriptNames.map { it.first }, legacy)

            // 3. Compiler inputs.
            writeWork(work, symbols, custom, options.packs)
            val commands = Resources.sym("commands.sym")
            val scriptIds = LinkedHashMap<String, Int>(legacy)
            for (n in scriptNames.map { it.first }) scriptIds[n.full] = custom.get(n.trigger, n.name) ?: error("no id for ${n.full}")
            extraScriptIds(options.packs, scriptIds, scriptNames.map { it.first.full }.toSet())
            val compiled =
                if (scriptNames.isEmpty()) {
                    emptyMap()
                } else {
                    ScriptPacker
                        .compile(
                            work,
                            scriptSources,
                            scriptIds,
                            commands,
                        ).compiled
                }

            // 4. Encode and write.
            val resolver = Resolver(symbols, custom, curatedTables(legacy))
            val packed = PackedCache.create(source, options.sourceDisk, outDisk)
            packed.use {
                for ((name, _) in scriptNames) {
                    val id = custom.get(name.trigger, name.name) ?: error("no id for ${name.full}")
                    val bytes = compiled[id] ?: throw IOException("Neptune wrote no output for ${name.full} (id $id)")
                    it.putGroup(Js5Archive.CLIENTSCRIPTS, id, intArrayOf(0), listOf(bytes), name.full.hashCode())
                }
                for (def in interfaces) {
                    val components = InterfacePacker.build(def, resolver)
                    val id = custom.get("interface", def.name) ?: error("no id for interface ${def.name}")
                    it.putGroup(Js5Archive.INTERFACES, id, IntArray(components.size) { i -> i }, components.map(If3Codec::encode))
                }
                writeConfigs(source, symbols, it, custom, configs)
                val indexes = it.finish()
                Files.copy(
                    options.sourceDisk.resolveSibling("keys.json"),
                    options.outRoot.resolve("keys.json"),
                    StandardCopyOption.REPLACE_EXISTING,
                )
                writeCustomSymbols(options.outRoot.resolve("custom-symbols.tsv"), custom.symbols)
                val sourceGroups = source.groupCount
                val newGroups =
                    it.written.count { g ->
                        source.index(g.substringBefore('/').toInt()).slotOf(g.substringAfter('/').toInt()) <
                            0
                    }
                Files.writeString(
                    options.outRoot.resolve("pack.properties"),
                    "# Facts about this packed cache; the server's spot checks expect the group count (CacheSpotChecks).\n" +
                        "sourceGroups=$sourceGroups\ngroups=${sourceGroups + newGroups}\n" +
                        "archivesTouched=${indexes.map { t -> t.first }.sorted().joinToString(",")}\n",
                )
                return PackReport(custom.symbols, it.written, indexes, symbols.duplicates)
            }
        }
    }

    private fun assignIds(
        source: Js5Cache,
        symbols: CacheSymbols,
        custom: CustomIds,
        configs: ConfigPacker.Defs,
        interfaces: List<InterfaceDef>,
        scripts: List<ScriptName>,
        legacy: Map<String, Int>,
    ) {
        var scriptId = source.index(Js5Archive.CLIENTSCRIPTS).groupIds.max() + 1
        for (n in scripts.sortedBy { it.full }) {
            if (n.full in legacy) throw IOException("${n.full} is already defined by the cache (legacy.sym)")
            custom.add(n.trigger, n.name, scriptId++)
        }

        var interfaceId = source.index(Js5Archive.INTERFACES).groupIds.max() + 1
        for (def in interfaces) {
            custom.add("interface", def.name, interfaceId)
            for ((component, id) in def.ids) custom.add("component", "${def.name}:$component", (interfaceId shl COMPONENT_BITS) or id)
            interfaceId++
        }

        var invId = nextConfigId(source, ConfigPacker.GROUP_INV, symbols.tables["inv"])
        for (d in configs.invs.sortedBy { it.name }) custom.add("inv", d.name, invId++, d.attributes)
        var varpId = nextConfigId(source, ConfigPacker.GROUP_VARP, symbols.tables["varp"])
        for (d in configs.varps.sortedBy { it.name }) custom.add("varp", d.name, varpId++, d.attributes)
        var varbitId = nextConfigId(source, ConfigPacker.GROUP_VARBIT, symbols.tables["varbit"])
        for (d in configs.varbits.sortedBy { it.name }) custom.add("varbit", d.name, varbitId++)
    }

    /** One above the highest id the config group holds or the gamevals name. */
    private fun nextConfigId(source: Js5Cache, group: Int, named: Map<String, Int>?): Int {
        val files = source.readFiles(Js5Archive.CONFIG, group) ?: throw IOException("Config group $group is absent")
        return maxOf(files.ids.max(), named?.values?.maxOrNull() ?: -1) + 1
    }

    private fun writeConfigs(source: Js5Cache, symbols: CacheSymbols, packed: PackedCache, custom: CustomIds, configs: ConfigPacker.Defs) {
        fun append(group: Int, additions: List<Pair<Int, ByteArray>>) {
            if (additions.isEmpty()) return
            val files = source.readFiles(Js5Archive.CONFIG, group) ?: throw IOException("Config group $group is absent")
            val all = files.ids.indices.map { files.ids[it] to files.data[it] } + additions
            val ordered = all.sortedBy { it.first }
            packed.putGroup(Js5Archive.CONFIG, group, ordered.map { it.first }.toIntArray(), ordered.map { it.second })
        }
        append(
            ConfigPacker.GROUP_INV,
            configs.invs.map { (custom.get("inv", it.name) ?: error(it.name)) to ConfigPacker.encodeInv(it) },
        )
        append(
            ConfigPacker.GROUP_VARP,
            configs.varps.map { (custom.get("varp", it.name) ?: error(it.name)) to ConfigPacker.encodeVarp(it) },
        )
        append(
            ConfigPacker.GROUP_VARBIT,
            configs.varbits.map {
                val varp =
                    custom.get("varp", it.varp)
                        ?: symbols.lookup("varp", it.varp)
                        ?: throw IOException("varbit ${it.name}: unknown varp '${it.varp}'")
                (custom.get("varbit", it.name) ?: error(it.name)) to ConfigPacker.encodeVarbit(varp, it)
            },
        )
    }

    private fun writeWork(work: Path, symbols: CacheSymbols, custom: CustomIds, packs: List<PackSource>) {
        Files.createDirectories(work)
        val base = work.resolve("symbols")
        val pack = work.resolve("symbols-pack")
        for (dir in listOf(base, pack, work.resolve("library"))) {
            if (Files.exists(dir)) {
                Files.walk(dir).use { s ->
                    s.sorted(Comparator.reverseOrder()).filter { it != dir }.forEach(Files::delete)
                }
            }
            Files.createDirectories(dir)
        }
        symbols.writeSymFiles(base)
        for (name in listOf("constant.sym", "fontmetrics.sym")) Files.writeString(base.resolve(name), Resources.text(name))
        for (name in listOf("commands.cs2", "legacy.cs2")) Files.writeString(work.resolve("library").resolve(name), Resources.text(name))

        // Names the pack defines get the ids assigned above, in files of their own.
        fun file(name: String, rows: Map<String, Int>, sub: String? = null) {
            if (rows.isEmpty()) return
            val text = rows.entries.sortedBy { it.value }.joinToString("") { "${it.value}\t${it.key}${sub?.let { s -> "\t$s" } ?: ""}\n" }
            Files.writeString(pack.resolve("$name.sym"), text)
        }
        file("interface", custom.all("interface"))
        file("component", custom.all("component"))
        file("inv", custom.all("inv"))
        file("varp", custom.all("varp"), "int")
        file("varbit", custom.all("varbit"))

        // Symbols the packs bring (varc, param, if_script, dbcolumn ...), minus any line naming something the pack itself defines.
        val own = custom.symbols.map { it.name }.toSet()
        for (p in packs) {
            for (sym in p.symbols) {
                if (sym.fileName.toString() == "clientscript.sym" || sym.fileName.toString() == "commands.sym") continue
                val kept = Files.readAllLines(sym).filter { line -> line.split('\t').getOrNull(1) !in own }
                val target = pack.resolve(sym.fileName.toString())
                val previous = if (Files.exists(target)) Files.readAllLines(target) else emptyList()
                Files.write(target, previous + kept)
            }
        }
    }

    /** `clientscript.sym` lines in a pack that name scripts the pack does not define: other packs' or the cache's scripts. */
    private fun extraScriptIds(packs: List<PackSource>, scriptIds: MutableMap<String, Int>, own: Set<String>) {
        for (p in packs) {
            for (sym in p.symbols.filter { it.fileName.toString() == "clientscript.sym" }) {
                for (line in Files.readAllLines(sym)) {
                    val parts = line.split('\t')
                    if (parts.size < 2 || parts[1] in own) continue
                    scriptIds.putIfAbsent(parts[1], parts[0].toInt())
                }
            }
        }
    }

    private fun curatedTables(legacy: Map<String, Int>): Map<String, Map<String, Int>> =
        mapOf(
            "fontmetrics" to Resources.sym("fontmetrics.sym"),
            "clientscript" to
                legacy.filterKeys { it.startsWith("[clientscript,") }.mapKeys { ScriptName.parse(it.key).name },
        )

    private fun writeCustomSymbols(path: Path, symbols: List<CustomSymbol>) {
        val text =
            buildString {
                append("# type\tname\tid\tattributes (server-side config keys the cache cannot hold)\n")
                for (s in symbols.sortedWith(compareBy({ it.type }, { it.id }))) {
                    append(s.type)
                        .append('\t')
                        .append(s.name)
                        .append('\t')
                        .append(s.id)
                        .append('\t')
                        .append(s.attributes)
                        .append('\n')
                }
            }
        Files.writeString(path, text)
    }

    private const val COMPONENT_BITS = 16
}

/** The curated tables bundled with the tool (`src/main/resources/cs2`). */
internal object Resources {
    fun text(name: String): String =
        (Resources::class.java.getResourceAsStream("/cs2/$name") ?: throw IOException("Missing resource /cs2/$name"))
            .use { String(it.readAllBytes(), Charsets.UTF_8) }

    /** A two-column `.sym` resource as name to id (the id column is first in the file). Signed 32-bit ids. */
    fun sym(name: String): Map<String, Int> =
        text(name).lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.associate {
            val parts = it.split('\t')
            parts[1] to parts[0].toInt()
        }
}
