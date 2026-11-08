package tp.tools.cachepack

import me.filby.neptune.clientscript.compiler.ClientScriptOpcode
import tp.cache.config.Configs
import tp.cache.config.Js5Archive
import tp.cache.interfaces.InterfaceTypes
import tp.cache.js5.Js5Cache
import tp.cache.js5.Js5Container
import java.nio.file.Path

/**
 * Checks a packed cache against the source it was made from. Everything here uses the same decoders the server boots with
 * (`Configs.load`, `InterfaceTypes.load`) plus the lossless IF3 decoder and an instruction walk of the new scripts.
 */
internal object Verifier {
    class Result(val lines: List<String>, val failures: List<String>) {
        val ok: Boolean get() = failures.isEmpty()
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod") // one block per check, each a few lines
    fun verify(
        sourceDisk: Path,
        packedDisk: Path,
        custom: List<CustomSymbol>,
        patchedScripts: Set<Int> = emptySet(),
        patchedSprites: Set<Int> = emptySet(),
    ): Result {
        val lines = ArrayList<String>()
        val failures = ArrayList<String>()

        fun check(ok: Boolean, text: String) {
            (if (ok) lines else failures) += (if (ok) "ok    " else "FAIL  ") + text
        }
        Js5Cache.open(sourceDisk).use { source ->
            Js5Cache.open(packedDisk).use { packed ->
                // D-094: patched existing scripts are rewritten on purpose; everything else must stay byte-identical.
                val written =
                    custom.writtenGroups() + patchedScripts.map { Js5Archive.CLIENTSCRIPTS to it } +
                        patchedSprites.map { Js5Archive.SPRITES to it }
                // 1. Index integrity: every group's container matches the CRC (and lengths, for rewritten ones) its index records.
                var crcChecked = 0
                var crcBad = 0
                for (info in packed.archives.filterNotNull()) {
                    val table = IndexTable.decode(Js5Container.decode(packed.store.read(255, info.archive)!!))
                    for (g in table.groups) {
                        val raw = packed.store.read(info.archive, g.id)
                        if (raw == null) {
                            crcBad++
                            continue
                        }
                        crcChecked++
                        if (Js5Container.crc(raw) != g.crc) crcBad++
                        if ((info.archive to g.id) in written) {
                            val decoded = Js5Container.decode(raw)
                            if (g.uncompressedLength != decoded.size || g.compressedLength != Js5Container.containerLength(raw)) crcBad++
                        }
                    }
                }
                check(
                    crcBad == 0,
                    "$crcChecked groups: container CRC equals the index CRC ($crcBad bad); lengths checked on rewritten groups",
                )

                // 2. Untouched groups are byte-identical to the source.
                var same = 0
                var different = 0
                for (info in source.archives.filterNotNull()) {
                    for (id in info.index.groupIds) {
                        if ((info.archive to id) in written) continue
                        val a = source.store.read(info.archive, id)
                        val b = packed.store.read(info.archive, id)
                        if (a != null && a.contentEquals(b)) same++ else different++
                    }
                }
                check(different == 0, "$same untouched groups byte-identical to the source ($different differ)")
                val touchedArchives = written.map { it.first }.toSet()
                for (info in source.archives.filterNotNull()) {
                    val pa = packed.archive(info.archive)
                    val isTouched = info.archive in touchedArchives
                    check(
                        (pa?.crc == info.crc) != isTouched,
                        "archive ${info.archive}: index ${if (isTouched) "changed" else "unchanged"} " +
                            "(crc ${info.crc} -> ${pa?.crc}, version ${info.version} -> ${pa?.version})",
                    )
                }
                check(
                    packed.groupCount == source.groupCount + written.count { (a, g) -> source.index(a).slotOf(g) < 0 },
                    "group count ${source.groupCount} -> ${packed.groupCount}",
                )

                // 3. The server's decoders accept the packed cache.
                val configs = Configs.load(packed)
                val interfaces = InterfaceTypes.load(packed)
                check(true, "Configs.load and InterfaceTypes.load decode the packed cache (${interfaces.interfaceCount} interfaces)")

                // 4. What was added decodes to what was asked for.
                custom.verifyInto(::check, packed, configs, interfaces)

                // 5. Neptune's core opcode table against every script of the cache.
                opcodeTable(source, ::check)
            }
        }
        return Result(lines, failures)
    }

    /** Groups the run wrote, recomputed from the custom symbols: interfaces, scripts and the three config groups. */
    private fun List<CustomSymbol>.writtenGroups(): Set<Pair<Int, Int>> {
        val out = HashSet<Pair<Int, Int>>()
        for (s in this) {
            when (s.type) {
                "interface" -> out += Js5Archive.INTERFACES to s.id
                "inv" -> out += Js5Archive.CONFIG to ConfigPacker.GROUP_INV
                "varp" -> out += Js5Archive.CONFIG to ConfigPacker.GROUP_VARP
                "varbit" -> out += Js5Archive.CONFIG to ConfigPacker.GROUP_VARBIT
                "component" -> Unit
                else -> out += Js5Archive.CLIENTSCRIPTS to s.id
            }
        }
        return out
    }

    private fun List<CustomSymbol>.verifyInto(
        check: (Boolean, String) -> Unit,
        packed: Js5Cache,
        configs: Configs,
        interfaces: InterfaceTypes,
    ) {
        val scriptIds = filter { it.type !in NON_SCRIPT }.map { it.id }.toSet()
        for (s in this) {
            when (s.type) {
                "inv" -> {
                    check(configs.invs[s.id]?.size != null, "inv ${s.name} (${s.id}) decodes: size ${configs.invs[s.id]?.size}")
                }

                "varp" -> {
                    check(configs.varps[s.id] != null, "varp ${s.name} (${s.id}) decodes")
                }

                "varbit" -> {
                    val v = configs.varbits[s.id]
                    check(v != null, "varbit ${s.name} (${s.id}) decodes: varp ${v?.baseVar} bits ${v?.startBit}..${v?.endBit}")
                }

                "interface" -> {
                    verifyInterface(check, packed, interfaces, s, scriptIds)
                }

                "component" -> {
                    Unit
                }

                else -> {
                    val raw = packed.readGroup(Js5Archive.CLIENTSCRIPTS, s.id)
                    var missing = 0
                    val shape =
                        raw?.let {
                            ScriptScan.forEachInstruction(it) { opcode, operand ->
                                if (opcode == GOSUB && packed.index(Js5Archive.CLIENTSCRIPTS).slotOf(operand) < 0) missing++
                            }
                        }
                    val consistent =
                        shape != null && shape.walkedInstructions > 0 && shape.walkedInstructions == shape.declaredInstructions
                    check(
                        consistent && missing == 0,
                        "script ${s.name} (${s.id}): ${shape?.walkedInstructions} instructions " +
                            "(trailer says ${shape?.declaredInstructions}), " +
                            "args int/string/long ${shape?.intArgs}/${shape?.stringArgs}/${shape?.longArgs}, " +
                            "$missing missing gosub targets",
                    )
                }
            }
        }
    }

    private fun verifyInterface(
        check: (Boolean, String) -> Unit,
        packed: Js5Cache,
        interfaces: InterfaceTypes,
        s: CustomSymbol,
        scriptIds: Set<Int>,
    ) {
        val files = packed.readFiles(Js5Archive.INTERFACES, s.id)
        check(files != null, "interface ${s.name} (${s.id}) is in the packed cache")
        if (files == null) return
        val decoded = interfaces[s.id]
        check(
            decoded != null && decoded.components.size == files.size,
            "interface ${s.name}: server decoder reads ${decoded?.components?.size} of ${files.size} components",
        )
        var roundTrip = true
        var hookScripts = 0
        var hookMissing = 0
        for (i in 0 until files.size) {
            val c = If3Codec.decode(files.data[i], i)
            if (c == null || !If3Codec.encode(c).contentEquals(files.data[i])) roundTrip = false
            c?.hooks?.forEach { hook ->
                val script = hook.firstOrNull() as? Int ?: return@forEach
                hookScripts++
                if (script !in scriptIds && packed.index(Js5Archive.CLIENTSCRIPTS).slotOf(script) < 0) hookMissing++
            }
        }
        check(roundTrip, "interface ${s.name}: ${files.size} components decode and re-encode byte for byte")
        check(hookMissing == 0, "interface ${s.name}: $hookScripts hooks, $hookMissing point at a script that does not exist")
    }

    /** Core opcodes (below 100) whose operand is one byte, or a string / long literal, in the cache: RuneLite `ScriptLoader`. */
    private val SMALL_OPERAND = setOf(21, 38, 39, 63)

    /**
     * Walks every script of [cache] and checks Neptune's `ClientScriptOpcode` against what the cache uses: each core opcode the
     * cache uses must exist in Neptune with the operand width the cache needs (a wrong width would misalign a stream, and the
     * walker throws on that), and the ones the cache never uses are listed as unverified.
     */
    private fun opcodeTable(cache: Js5Cache, check: (Boolean, String) -> Unit) {
        val used = java.util.TreeMap<Int, Int>()
        var scripts = 0
        val stubs = ArrayList<Int>()
        for (group in cache.index(Js5Archive.CLIENTSCRIPTS).groupIds) {
            val data = cache.readGroup(Js5Archive.CLIENTSCRIPTS, group) ?: continue
            if (ScriptScan.isStub(data)) {
                stubs += group
                continue
            }
            val shape = ScriptScan.forEachInstruction(data) { opcode, _ -> if (opcode < FIRST_COMMAND) used.merge(opcode, 1, Int::plus) }
            check(shape.walkedInstructions == shape.declaredInstructions) { "script $group: instruction count differs" }
            scripts++
        }
        val neptune = ClientScriptOpcode.entries.filter { it.id < FIRST_COMMAND }.associateBy { it.id }
        val unknown = used.keys.filter { it !in neptune && it !in LITERALS }
        val widthBad =
            neptune.values.filter { it.id in used && it.id !in LITERALS && it.largeOperand == (it.id in SMALL_OPERAND) }
        val unverified = neptune.values.filter { it.id !in used }.map { "${it.name}(${it.id})" }
        check(
            unknown.isEmpty(),
            "opcode table: $scripts cache scripts walk cleanly (${stubs.size} stub groups under ${ScriptScan.MIN_SCRIPT_BYTES} " +
                "bytes skipped: ${stubs.take(STUB_PREVIEW)}); opcodes the cache uses that Neptune lacks: $unknown",
        )
        check(
            widthBad.isEmpty(),
            "opcode table: operand widths of the ${used.size} core opcodes the cache uses agree with Neptune ($widthBad)",
        )
        check(true, "opcode table: Neptune core opcodes the cache never uses (unverified): $unverified")
    }

    private const val FIRST_COMMAND = 100
    private const val STUB_PREVIEW = 10

    /** `sconst` and `lconst` carry a string / long literal rather than an int operand. */
    private val LITERALS = setOf(3, 61)

    private val NON_SCRIPT = setOf("interface", "component", "inv", "varp", "varbit")

    /** `gosub_with_params`. */
    private const val GOSUB = 40
}
