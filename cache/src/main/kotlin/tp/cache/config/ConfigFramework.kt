package tp.cache.config

import tp.cache.js5.Js5Cache
import tp.util.buf.ReadBuf
import java.io.IOException

/**
 * Config group ids inside archive 2. Names: RS Mod `api/cache/.../Js5Configs.kt` (rev 233) and
 * RuneLite `cache/.../ConfigType.java` (1.13.6). Groups present in cache 2757 but named by no reference are listed
 * in [OPAQUE] and are not decoded (DECISIONS D-012).
 */
object ConfigGroup {
    const val UNDERLAY = 1
    const val HUNT = 2
    const val IDK = 3
    const val OVERLAY = 4
    const val INV = 5
    const val LOC = 6
    const val ENUM = 8
    const val NPC = 9
    const val OBJ = 10
    const val PARAM = 11
    const val SEQ = 12
    const val SPOTANIM = 13
    const val VARBIT = 14
    const val VARCLIENTSTRING = 15
    const val VARP = 16
    const val AREA = 18
    const val VARCLIENT = 19
    const val VAROBJ = 20
    const val HITMARK = 32
    const val HEADBAR = 33
    const val STRUCT = 34
    const val MAPELEMENT = 35
    const val DBROW = 38
    const val DBTABLE = 39
    const val GAMELOGEVENT = 70

    /** World entity configs (boats); decoder from the rev-242 client (D-046). */
    const val WORLDENTITY = 72

    /** Named groups with no reference decoder in RuneLite 1.13.6 or RS Mod: left undecoded, OPEN (see Phase 1 report). */
    val UNDECODED: Set<Int> = setOf(VARCLIENTSTRING, VARCLIENT, VAROBJ, GAMELOGEVENT)

    /** Present in cache 2757, unnamed by RS Mod / RuneLite / RSProx / RSProt at time of writing. */
    val OPAQUE: Set<Int> = setOf(7, 22, 24, 25, 26, 27, 28, 29, 30, 31, 37, 47, 54, 71, 73)
}

/** Archive ids (RuneLite `IndexType`, RS Mod `Js5Archives`). */
object Js5Archive {
    const val CONFIG = 2
    const val INTERFACES = 3
    const val SPRITES = 8
    const val MAPS = 5
    const val CLIENTSCRIPTS = 12
    const val DBTABLEINDEX = 21
    const val GAMEVALS = 24
}

/** Thrown for an opcode the decoder doesn't know: cache drift must fail loudly (Phase 1 plan). */
class UnknownOpcodeException(type: String, id: Int, opcode: Int, offset: Int) :
    IOException("$type $id: unknown opcode $opcode at offset $offset")

/** Decodes one config file. Implementations are stateless and hand-written (`when (opcode)` loops). */
interface ConfigDecoder<T : Any> {
    /** Human name used in errors and dumps, e.g. "obj". */
    val name: String

    fun decode(id: Int, buf: ReadBuf): T
}

/** Id-indexed, immutable list of decoded types. `null` slots are ids absent from the group. */
class TypeList<T : Any>(private val types: Array<T?>) : Iterable<T> {
    val size: Int get() = types.size

    operator fun get(id: Int): T? = types.getOrNull(id)

    fun getValue(id: Int): T = types.getOrNull(id) ?: throw NoSuchElementException("No type with id $id")

    override fun iterator(): Iterator<T> = types.asSequence().filterNotNull().iterator()

    val count: Int get() = types.count { it != null }
}

object ConfigLoader {
    /** Decodes every file of config [group] with [decoder]. Each file must be fully consumed. */
    fun <T : Any> load(cache: Js5Cache, group: Int, decoder: ConfigDecoder<T>): TypeList<T> {
        val files = cache.readFiles(Js5Archive.CONFIG, group) ?: throw IOException("Config group $group is absent")
        val maxId = files.ids.maxOrNull() ?: -1
        val types = arrayOfNulls<Any>(maxId + 1)
        for (i in 0 until files.size) {
            val id = files.ids[i]
            val buf = ReadBuf(files.data[i])
            types[id] = decoder.decode(id, buf)
            if (buf.isReadable) throw IOException("${decoder.name} $id: ${buf.remaining} unread bytes")
        }
        @Suppress("UNCHECKED_CAST") // every non-null slot holds a T produced by decoder
        return TypeList(types as Array<T?>)
    }
}

/** Typed config params (opcode 249 in most configs). Values are Int, Long or String. */
class ParamMap private constructor(private val map: Map<Int, Any>) {
    val size: Int get() = map.size

    fun int(key: Int): Int? = map[key] as? Int

    fun long(key: Int): Long? = map[key] as? Long

    fun string(key: Int): String? = map[key] as? String

    operator fun contains(key: Int): Boolean = key in map

    val keys: Set<Int> get() = map.keys

    companion object {
        val EMPTY: ParamMap = ParamMap(emptyMap())
        private const val TYPE_STRING = 1
        private const val TYPE_LONG = 2

        /** u8 count; per entry: u8 type (1 string, 2 long, else int), u24 key, value. RuneLite `InputStream.readParams`. */
        fun decode(buf: ReadBuf): ParamMap {
            val count = buf.g1()
            val map = HashMap<Int, Any>(count * 2)
            repeat(count) {
                val type = buf.g1()
                val key = buf.g3()
                map[key] =
                    when (type) {
                        TYPE_STRING -> buf.gjstr()
                        TYPE_LONG -> buf.g8()
                        else -> buf.g4()
                    }
            }
            return ParamMap(map)
        }
    }
}

/**
 * Right-click ops on a world entity (obj on the ground, npc, loc). Slot text is null when absent or "Hidden".
 * Conditional ops show only when a varp/varbit lies in [min, max]. Encoding: RuneLite `EntityOpsLoader`.
 */
class EntityOps {
    val ops: Array<String?> = arrayOfNulls(OP_COUNT)
    val subOps: MutableMap<Int, MutableMap<Int, String>> = HashMap(0)
    val conditionalOps: MutableList<ConditionalOp> = ArrayList(0)

    data class ConditionalOp(val index: Int, val subId: Int, val text: String, val varp: Int, val varbit: Int, val min: Int, val max: Int)

    internal fun decodeOp(buf: ReadBuf, index: Int) {
        val text = buf.gjstr()
        if (!text.equals("Hidden", ignoreCase = true)) ops[index] = text
    }

    internal fun decodeSubOp(buf: ReadBuf) {
        val index = buf.g1()
        val subId = buf.g1()
        subOps.getOrPut(index) { HashMap() }[subId] = buf.gjstr()
    }

    internal fun decodeConditionalOp(buf: ReadBuf) {
        val index = buf.g1()
        val varp = buf.g2()
        val varbit = buf.g2()
        val min = buf.g4()
        val max = buf.g4()
        conditionalOps += ConditionalOp(index, NO_SUB, buf.gjstr(), varp, varbit, min, max)
    }

    /** Note: the sub id is a u16 here but a u8 in [decodeSubOp] (RuneLite 1.13.6 `EntityOpsLoader`). */
    internal fun decodeConditionalSubOp(buf: ReadBuf) {
        val index = buf.g1()
        val subId = buf.g2()
        val varp = buf.g2()
        val varbit = buf.g2()
        val min = buf.g4()
        val max = buf.g4()
        conditionalOps += ConditionalOp(index, subId, buf.gjstr(), varp, varbit, min, max)
    }

    companion object {
        /** Five op slots (op1..op5), client `ObjType.op` / `NpcType.op` / `LocType.op`. */
        const val OP_COUNT = 5
        const val NO_SUB = -1
    }
}
