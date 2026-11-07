package tp.tools.cachepack

import tp.cache.config.Js5Archive
import tp.cache.js5.Js5Cache
import tp.cache.js5.Js5Container
import tp.util.buf.ReadBuf
import java.nio.file.Files
import java.nio.file.Path

/**
 * Name tables read from the source cache: archive 24 (Jagex gamevals) for everything that has names there, plus the string
 * var-clients found by scanning the cache's own scripts (the varc config files carry no type, see [ScriptScan]).
 *
 * Gameval group ids and the interface/component encoding: `tp.tools.symbolgen.SymbolGen` (RuneLite 1.13.6 `GameValLoader`).
 * Each namespace maps name to id; the first holder of a duplicated name wins, so `.sym` files and lookups agree.
 */
internal class CacheSymbols(
    /** Namespace (`obj`, `inv`, `varp`, `sprite`, ...) to name to id. Components are `interface:component` to the packed id. */
    val tables: Map<String, Map<String, Int>>,
    val stringVarcs: Set<Int>,
    /** Count of names dropped because an earlier id already held them, per namespace (reported, never silent). */
    val duplicates: Map<String, Int>,
) {
    fun lookup(namespace: String, name: String): Int? = tables[namespace]?.get(name)

    /** Writes the `.sym` files Neptune loads (`<type>.sym`, `id<TAB>name[<TAB>subtype]`) into [dir]. */
    fun writeSymFiles(dir: Path) {
        Files.createDirectories(dir)
        for ((namespace, file, subtype) in SYM_FILES) {
            val table = tables[namespace] ?: continue
            val sb = StringBuilder()
            for ((name, id) in table.entries.sortedBy { it.value }) {
                if (namespace == "varc" && id in stringVarcs) continue
                sb.append(idText(namespace, id)).append('\t').append(name)
                if (subtype != null) sb.append('\t').append(subtype)
                sb.append('\n')
            }
            Files.writeString(dir.resolve("$file.sym"), sb)
        }
        val varcs = tables["varc"].orEmpty()
        val strings = StringBuilder()
        for ((name, id) in varcs.entries.sortedBy { it.value }) {
            if (id in stringVarcs) {
                strings
                    .append(id)
                    .append('\t')
                    .append(name)
                    .append('\n')
            }
        }
        Files.writeString(dir.resolve("varcstr.sym"), strings)
    }

    private fun idText(namespace: String, id: Int): String =
        if (namespace == "component") "${id ushr COMPONENT_BITS}:${id and COMPONENT_MASK}" else id.toString()

    companion object {
        private const val COMPONENT_BITS = 16
        private const val COMPONENT_MASK = 0xFFFF
        private const val COMPONENT_END = 0xFFFF

        /** Gameval group ids to namespace (same table as `SymbolGen.FLAT`). */
        private val FLAT_GROUPS: Map<Int, String> =
            linkedMapOf(
                0 to "obj",
                1 to "npc",
                2 to "inv",
                3 to "varp",
                4 to "varbit",
                6 to "loc",
                7 to "seq",
                8 to "spotanim",
                9 to "dbrow",
                11 to "jingle",
                12 to "sprite",
                15 to "varc",
            )
        private const val GROUP_DBTABLE = 10
        private const val GROUP_INTERFACES = 14

        /** Namespace, Neptune file name, optional subtype column. Varps are all int in the cache (no long varps are named). */
        private val SYM_FILES: List<Triple<String, String, String?>> =
            listOf(
                Triple("obj", "obj", null),
                Triple("npc", "npc", null),
                Triple("inv", "inv", null),
                Triple("varp", "varp", "int"),
                Triple("varbit", "varbit", null),
                Triple("loc", "loc", null),
                Triple("seq", "seq", null),
                Triple("spotanim", "spotanim", null),
                Triple("dbrow", "dbrow", null),
                Triple("dbtable", "dbtable", null),
                Triple("jingle", "jingle", null),
                Triple("sprite", "graphic", null),
                Triple("varc", "varc", "int"),
                Triple("interface", "interface", null),
                Triple("component", "component", null),
            )

        fun load(cache: Js5Cache): CacheSymbols {
            val tables = LinkedHashMap<String, MutableMap<String, Int>>()
            val duplicates = HashMap<String, Int>()

            fun put(namespace: String, name: String, id: Int) {
                val table = tables.getOrPut(namespace) { LinkedHashMap() }
                if (table.putIfAbsent(name, id) != null) duplicates.merge(namespace, 1, Int::plus)
            }
            val index = cache.index(Js5Archive.GAMEVALS)
            for (group in index.groupIds) {
                val files = cache.readFiles(Js5Archive.GAMEVALS, group) ?: continue
                when {
                    group in FLAT_GROUPS -> {
                        for (i in 0 until files.size) put(FLAT_GROUPS.getValue(group), String(files.data[i], Charsets.UTF_8), files.ids[i])
                    }

                    group == GROUP_DBTABLE -> {
                        for (i in 0 until files.size) {
                            val buf = ReadBuf(files.data[i])
                            buf.g1()
                            put("dbtable", buf.gjstr(), files.ids[i])
                        }
                    }

                    group == GROUP_INTERFACES -> {
                        for (i in 0 until files.size) {
                            val id = files.ids[i]
                            val buf = ReadBuf(files.data[i])
                            val name = buf.gjstr()
                            put("interface", name, id)
                            while (buf.isReadable) {
                                val comp = buf.g2()
                                if (comp == COMPONENT_END) break
                                put("component", "$name:${buf.gjstr()}", (id shl COMPONENT_BITS) or comp)
                            }
                        }
                    }
                }
            }
            return CacheSymbols(tables, ScriptScan.stringVarcs(cache), duplicates)
        }
    }
}

/** Instruction-level scan of the cache's client scripts (archive 12). */
internal object ScriptScan {
    private const val SCONST = 3
    private const val RETURN = 21
    private const val POP_INT = 38
    private const val POP_OBJECT = 39
    private const val LCONST = 61
    private const val PUSH_NULL = 63

    /** `get_varc_string_old`, `set_varc_string_old`, `get_varc_string`, `set_varc_string` (RuneLite `Opcodes`). */
    private val STRING_VARC_OPCODES: Set<Int> = setOf(47, 48, 49, 50)

    /** Opcodes below [FIRST_COMMAND] whose operand is one byte (`return`, `pop_int`, `pop_object`, `push_null`). */
    private val ONE_BYTE_OPERAND: Set<Int> = setOf(RETURN, POP_INT, POP_OBJECT, PUSH_NULL)

    /** Opcodes below this take a 4-byte operand (except the 1-byte ones above); the rest take 1 byte. RuneLite `ScriptLoader`. */
    private const val FIRST_COMMAND = 100

    /** Bytes after the instruction stream that are not instructions: counts (16), the switch tables and their length (2). */
    private const val TRAILER_FIXED = 16
    private const val TRAILER_LENGTH = 2

    /** Var-client ids that scripts read or write with the string opcodes: those are the string varcs. */
    fun stringVarcs(cache: Js5Cache): Set<Int> {
        val out = HashSet<Int>()
        for (group in cache.index(Js5Archive.CLIENTSCRIPTS).groupIds) {
            val raw = cache.store.read(Js5Archive.CLIENTSCRIPTS, group) ?: continue
            if (isStub(Js5Container.decode(raw))) continue
            forEachInstruction(Js5Container.decode(raw)) { opcode, operand ->
                if (opcode in STRING_VARC_OPCODES) {
                    out += operand
                }
            }
        }
        return out
    }

    /** Debug name (1) + instruction count (4) + six counts (12) + switch count (1) + switch length (2). */
    const val MIN_SCRIPT_BYTES = 20

    /** Script 0 of the cache is a 2-byte placeholder rather than a script. */
    fun isStub(data: ByteArray): Boolean = data.size < MIN_SCRIPT_BYTES

    /** The fixed-size trailer of a compiled script (layout: Neptune `BinaryScriptWriterContext.finish`, RuneLite `ScriptLoader`). */
    class Shape(
        val declaredInstructions: Int,
        val walkedInstructions: Int,
        val localInts: Int,
        val localStrings: Int,
        val localLongs: Int,
        val intArgs: Int,
        val stringArgs: Int,
        val longArgs: Int,
    )

    /**
     * Walks the instruction stream of a compiled script; [visit] gets the opcode and its int operand (0 for string / long).
     * Throws when the stream does not end exactly where the trailer starts, i.e. when an operand width is wrong.
     */
    fun forEachInstruction(data: ByteArray, visit: (opcode: Int, operand: Int) -> Unit): Shape {
        val buf = ReadBuf(data)
        buf.position = data.size - TRAILER_LENGTH
        val switchLength = buf.g2()
        val end = data.size - TRAILER_LENGTH - switchLength - TRAILER_FIXED
        buf.position = end
        val declared = buf.g4()
        val counts = IntArray(COUNT_FIELDS) { buf.g2() }
        buf.position = 0
        buf.gjstr() // debug name, empty in the cache
        var walked = 0
        while (buf.position < end) {
            val opcode = buf.g2()
            walked++
            when {
                opcode == SCONST -> {
                    buf.gjstr()
                    visit(opcode, 0)
                }

                opcode == LCONST -> {
                    buf.skip(Long.SIZE_BYTES)
                    visit(opcode, 0)
                }

                opcode == RETURN || opcode == POP_INT || opcode == POP_OBJECT || opcode == PUSH_NULL -> {
                    visit(opcode, buf.g1())
                }

                opcode < FIRST_COMMAND -> {
                    visit(opcode, buf.g4())
                }

                else -> {
                    visit(opcode, buf.g1())
                }
            }
        }
        if (buf.position != end) throw java.io.IOException("instruction stream ends at ${buf.position}, trailer starts at $end")
        return Shape(
            declared,
            walked,
            counts[LOCAL_INT],
            counts[LOCAL_STRING],
            counts[LOCAL_LONG],
            counts[ARG_INT],
            counts[ARG_STRING],
            counts[ARG_LONG],
        )
    }

    /** Local int, string, long, then argument int, string, long counts. */
    private const val COUNT_FIELDS = 6
    private const val LOCAL_INT = 0
    private const val LOCAL_STRING = 1
    private const val LOCAL_LONG = 2
    private const val ARG_INT = 3
    private const val ARG_STRING = 4
    private const val ARG_LONG = 5
}
