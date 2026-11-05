package tp.cache.config.loc

import tp.cache.config.ConfigDecoder
import tp.cache.config.EntityOps
import tp.cache.config.ParamMap
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/**
 * Loc (scenery, "object") config, archive 2 group 6. Follows the [tp.cache.config.obj.ObjType] pattern.
 *
 * Collision-relevant fields ([blockWalk], [blockRange], [breakRouteFinding], [forceApproach], [width], [length]) use the
 * client's `LocType` names and defaults; their effect is documented in docs/mechanics/collision.md.
 */
class LocType internal constructor(val id: Int) {
    var name: String = "null"
        internal set
    var width: Int = 1
        internal set
    var length: Int = 1
        internal set

    /** 0 = walkable, 1 = blocks walking (op 27), 2 = blocks walking (default). Op 17 sets 0. */
    var blockWalk: Int = 2
        internal set

    /** Blocks projectiles / line of sight. Default true; ops 17 and 18 clear it. */
    var blockRange: Boolean = true
        internal set

    /** Op 74 (RuneLite `isHollow`, RS Mod `breakroutefinding`): sets the LOC_ROUTE_BLOCKER flag. */
    var breakRouteFinding: Boolean = false
        internal set

    /** Op 69: block-access mask for approach directions (RS Mod `forceapproach`, rsmod-pathfinder `blockAccessFlags`). */
    var forceApproach: Int = 0
        internal set

    /** Op 19 "active"/wallOrDoor; -1 until resolved by the client's post-decode rule (see [LocTypeDecoder]). */
    var active: Int = -1
        internal set
    var anim: Int = -1
        internal set
    var category: Int = -1
        internal set
    var mapScene: Int = -1
        internal set
    var mapArea: Int = -1
        internal set
    var supportItems: Int = -1
        internal set
    var multiVarbit: Int = -1
        internal set
    var multiVarp: Int = -1
        internal set

    /** Multi-loc transforms; last entry is the default. Null when the loc doesn't transform. */
    var multiLocs: IntArray? = null
        internal set
    val ops: EntityOps = EntityOps()
    var params: ParamMap = ParamMap.EMPTY
        internal set

    /** Whether opcode 1/6 gave explicit model shapes and the first is shape 10 (used by [active] resolution). */
    internal var firstModelShapeIsCentre: Boolean = false
    internal var hasModels: Boolean = false
}

/**
 * Decoder for [LocType]. Opcode table: RuneLite 1.13.6 `cache/.../loaders/ObjectLoader.java` with rev >= 220 sound
 * layout (opcodes 78/79 carry an extra "retain" byte).
 */
object LocTypeDecoder : ConfigDecoder<LocType> {
    override val name: String = "loc"

    private const val OP_FIRST = 30
    private const val OP_END = 35

    /** Model shape value meaning "centrepiece" (LocShape 10) in the op 1/6 model table. */
    private const val SHAPE_CENTREPIECE = 10

    /** Fixed-size payloads with no server use: opcode -> bytes. */
    private val SKIP: Map<Int, Int> =
        mapOf(
            28 to 1,
            29 to 1,
            39 to 1,
            42 to 2,
            65 to 2,
            66 to 2,
            67 to 2,
            70 to 2,
            71 to 2,
            72 to 2,
            81 to 1,
            91 to 1,
            93 to 6,
            95 to 1,
            96 to 1,
        )

    /** Flags with no payload and no server use. */
    private val FLAGS: Set<Int> = setOf(21, 22, 23, 62, 64, 73, 89, 90, 94)

    override fun decode(id: Int, buf: ReadBuf): LocType {
        val t = LocType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            val skip = SKIP[opcode]
            when {
                skip != null -> buf.skip(skip)
                opcode in FLAGS -> Unit
                else -> decodeOp(t, opcode, buf)
            }
        }
        // Client post-decode (RuneLite `ObjectLoader.post`).
        if (t.active == -1) {
            t.active = if (t.hasModels && t.firstModelShapeIsCentre) 1 else 0
            if (t.ops.ops.any { it != null }) t.active = 1
        }
        if (t.supportItems == -1) t.supportItems = if (t.blockWalk != 0) 1 else 0
        return t
    }

    private fun decodeOp(t: LocType, opcode: Int, buf: ReadBuf) {
        when (opcode) {
            1, 6 -> {
                val n = buf.g1()
                if (n > 0) {
                    t.hasModels = true
                    for (i in 0 until n) {
                        if (opcode == 1) buf.g2() else buf.g4()
                        val shape = buf.g1()
                        if (i == 0) t.firstModelShapeIsCentre = shape == SHAPE_CENTREPIECE
                    }
                }
            }

            5, 7 -> {
                val n = buf.g1()
                if (n > 0) {
                    t.hasModels = true
                    t.firstModelShapeIsCentre = true // no shape table: models apply to shape 10
                    buf.skip(n * if (opcode == 5) 2 else 4)
                }
            }

            2 -> {
                t.name = buf.gjstr()
            }

            14 -> {
                t.width = buf.g1()
            }

            15 -> {
                t.length = buf.g1()
            }

            17 -> {
                t.blockWalk = 0
                t.blockRange = false
            }

            18 -> {
                t.blockRange = false
            }

            19 -> {
                t.active = buf.g1()
            }

            24 -> {
                t.anim = buf.g2().let { if (it == NULL_U16) -1 else it }
            }

            27 -> {
                t.blockWalk = 1
            }

            in OP_FIRST until OP_END -> {
                t.ops.decodeOp(buf, opcode - OP_FIRST)
            }

            40, 41 -> {
                buf.skip(buf.g1() * 4)
            }

            61 -> {
                t.category = buf.g2()
            }

            68 -> {
                t.mapScene = buf.g2()
            }

            69 -> {
                t.forceApproach = buf.g1()
            }

            74 -> {
                t.breakRouteFinding = true
            }

            75 -> {
                t.supportItems = buf.g1()
            }

            77, 92 -> {
                decodeMulti(t, buf, extended = false, withDefault = opcode == 92)
            }

            97, 98 -> {
                decodeMulti(t, buf, extended = true, withDefault = opcode == 98)
            }

            78 -> {
                buf.skip(SOUND_78_BYTES)
            }

            79 -> {
                buf.skip(SOUND_79_FIXED_BYTES)
                buf.skip(buf.g1() * 2)
            }

            82 -> {
                t.mapArea = buf.g2()
            }

            100 -> {
                t.ops.decodeSubOp(buf)
            }

            101 -> {
                t.ops.decodeConditionalOp(buf)
            }

            102 -> {
                t.ops.decodeConditionalSubOp(buf)
            }

            249 -> {
                t.params = ParamMap.decode(buf)
            }

            else -> {
                throw UnknownOpcodeException(name, t.id, opcode, buf.position - 1)
            }
        }
    }

    /**
     * Multi-loc (ops 77/92: u16 ids; 97/98: signed 24-bit ids). Layout: varbit u16, varp u16, [default], u8 count,
     * count+1 ids. Last entry of [LocType.multiLocs] is the default (-1 when absent).
     */
    private fun decodeMulti(t: LocType, buf: ReadBuf, extended: Boolean, withDefault: Boolean) {
        t.multiVarbit = buf.g2().let { if (it == NULL_U16) -1 else it }
        t.multiVarp = buf.g2().let { if (it == NULL_U16) -1 else it }

        fun id(): Int = if (extended) buf.g3s() else buf.g2().let { if (it == NULL_U16) -1 else it }
        val default = if (withDefault) id() else -1
        val count = buf.g1()
        val ids = IntArray(count + 2)
        for (i in 0..count) ids[i] = id()
        ids[count + 1] = default
        t.multiLocs = ids
    }

    private const val NULL_U16 = 0xFFFF

    /** Op 78: sound id u16, distance u8, retain u8 (rev >= 220). */
    private const val SOUND_78_BYTES = 4

    /** Op 79: min u16, max u16, distance u8, retain u8 (rev >= 220), then u8 count + u16 ids. */
    private const val SOUND_79_FIXED_BYTES = 6
}
