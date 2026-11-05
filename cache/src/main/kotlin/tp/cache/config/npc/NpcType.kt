// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.npc

import tp.cache.config.ConfigDecoder
import tp.cache.config.EntityOps
import tp.cache.config.ParamMap
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/**
 * NPC config, archive 2 group 9. Server-relevant fields only; models, recolours and render scale are consumed and
 * dropped. Defaults are the client's `NpcType` defaults (RuneLite `NpcDefinition`). Field names follow RuneLite 1.13.6.
 */
class NpcType internal constructor(val id: Int) {
    var name: String = "null"
        internal set

    /** Footprint in tiles (op 12). */
    var size: Int = 1
        internal set
    var category: Int = -1
        internal set

    /** Combat level (op 95), or -1 when not attackable by level. */
    var combatLevel: Int = -1
        internal set
    val ops: EntityOps = EntityOps()

    /** Stand / idle sequence (op 13). */
    var standSeq: Int = -1
        internal set
    var idleRotateLeftSeq: Int = -1
        internal set
    var idleRotateRightSeq: Int = -1
        internal set

    /** Walk sequence (ops 14, 17). */
    var walkSeq: Int = -1
        internal set
    var walkTurnAroundSeq: Int = -1
        internal set
    var walkTurnLeftSeq: Int = -1
        internal set
    var walkTurnRightSeq: Int = -1
        internal set

    /** Run sequence (ops 114, 115). */
    var runSeq: Int = -1
        internal set
    var runTurnAroundSeq: Int = -1
        internal set
    var runTurnLeftSeq: Int = -1
        internal set
    var runTurnRightSeq: Int = -1
        internal set

    /** Crawl sequence (ops 116, 117). */
    var crawlSeq: Int = -1
        internal set
    var crawlTurnAroundSeq: Int = -1
        internal set
    var crawlTurnLeftSeq: Int = -1
        internal set
    var crawlTurnRightSeq: Int = -1
        internal set

    /** Combat stats from ops 74-79: attack, defence, strength, hitpoints, ranged, magic (client default 1 each). */
    val stats: IntArray = IntArray(STAT_COUNT) { 1 }

    /** False when op 93 is present. */
    var minimapVisible: Boolean = true
        internal set

    /** False when op 107 is present: the npc has no right-click menu. */
    var interactable: Boolean = true
        internal set

    /** False when op 109 is present: the npc does not rotate to face targets. */
    var rotationFlag: Boolean = true
        internal set

    /** Turn speed (op 103); client default 32. */
    var rotationSpeed: Int = DEFAULT_ROTATION_SPEED
        internal set

    /** Overlay height (op 124), or -1. */
    var height: Int = -1
        internal set

    /** Footprint override (op 126), or -1. */
    var footprintSize: Int = -1
        internal set

    /** Render priority: 1 from op 99, 2 from op 111 (rev >= 233). */
    var renderPriority: Int = 0
        internal set

    /** Op 122. */
    var follower: Boolean = false
        internal set

    /** Op 123. */
    var lowPriorityFollowerOps: Boolean = false
        internal set

    /** Head icon archive ids (op 102 sprite group, per slot, -1 = none), or null. */
    var headIconArchives: IntArray? = null
        internal set

    /** Head icon sprite indices (op 102, per slot, -1 = none), or null. */
    var headIconSprites: IntArray? = null
        internal set
    var multiVarbit: Int = -1
        internal set
    var multiVarp: Int = -1
        internal set

    /** Multi-npc transforms; last entry is the default. Null when the npc doesn't transform. */
    var multiNpcs: IntArray? = null
        internal set
    var params: ParamMap = ParamMap.EMPTY
        internal set

    companion object {
        /** Attack, defence, strength, hitpoints, ranged, magic. */
        const val STAT_COUNT = 6

        /** Client `NpcType` default turn speed (RuneLite `NpcDefinition.rotationSpeed`). */
        const val DEFAULT_ROTATION_SPEED = 32
    }
}

/**
 * Decoder for [NpcType]. Opcode table: RuneLite 1.13.6 `NpcLoader.java` with `rev210HeadIcons` and `rev233` true
 * (op 111 = render priority 2; op 102 uses the bitfield layout). RS Mod `NpcTypeDecoder` (rev 233) agrees except for
 * server-only ops 200-216, which are not cache data.
 */
object NpcTypeDecoder : ConfigDecoder<NpcType> {
    override val name: String = "npc"

    private const val OP_FIRST = 30
    private const val OP_END = 35
    private const val OP_STAT_FIRST = 74
    private const val OP_STAT_END = 80
    private const val NULL_U16 = 0xFFFF

    override fun decode(id: Int, buf: ReadBuf): NpcType {
        val t = NpcType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                // models: u8 count + u16 each; chathead models (60) the same
                1, 60 -> {
                    buf.skip(buf.g1() * 2)
                }

                // models / chathead models: u8 count + i32 each
                61, 62 -> {
                    buf.skip(buf.g1() * 4)
                }

                2 -> {
                    t.name = buf.gjstr()
                }

                12 -> {
                    t.size = buf.g1()
                }

                13 -> {
                    t.standSeq = buf.g2()
                }

                14 -> {
                    t.walkSeq = buf.g2()
                }

                15 -> {
                    t.idleRotateLeftSeq = buf.g2()
                }

                16 -> {
                    t.idleRotateRightSeq = buf.g2()
                }

                17 -> {
                    t.walkSeq = buf.g2()
                    t.walkTurnAroundSeq = buf.g2()
                    t.walkTurnLeftSeq = buf.g2()
                    t.walkTurnRightSeq = buf.g2()
                }

                18 -> {
                    t.category = buf.g2()
                }

                in OP_FIRST until OP_END -> {
                    t.ops.decodeOp(buf, opcode - OP_FIRST)
                }

                // recolour / retexture pairs
                40, 41 -> {
                    buf.skip(buf.g1() * 4)
                }

                // full recolour
                42 -> {
                    buf.skip(2)
                }

                in OP_STAT_FIRST until OP_STAT_END -> {
                    t.stats[opcode - OP_STAT_FIRST] = buf.g2()
                }

                93 -> {
                    t.minimapVisible = false
                }

                95 -> {
                    t.combatLevel = buf.g2()
                }

                // width / height scale
                97, 98 -> {
                    buf.skip(2)
                }

                99 -> {
                    t.renderPriority = 1
                }

                // ambient, contrast
                100, 101 -> {
                    buf.skip(1)
                }

                102 -> {
                    decodeHeadIcons(t, buf)
                }

                103 -> {
                    t.rotationSpeed = buf.g2()
                }

                106, 118 -> {
                    decodeMulti(t, buf, withDefault = opcode == 118)
                }

                107 -> {
                    t.interactable = false
                }

                109 -> {
                    t.rotationFlag = false
                }

                111 -> {
                    t.renderPriority = 2
                }

                114 -> {
                    t.runSeq = buf.g2()
                }

                115 -> {
                    t.runSeq = buf.g2()
                    t.runTurnAroundSeq = buf.g2()
                    t.runTurnLeftSeq = buf.g2()
                    t.runTurnRightSeq = buf.g2()
                }

                116 -> {
                    t.crawlSeq = buf.g2()
                }

                117 -> {
                    t.crawlSeq = buf.g2()
                    t.crawlTurnAroundSeq = buf.g2()
                    t.crawlTurnLeftSeq = buf.g2()
                    t.crawlTurnRightSeq = buf.g2()
                }

                122 -> {
                    t.follower = true
                }

                123 -> {
                    t.lowPriorityFollowerOps = true
                }

                124 -> {
                    t.height = buf.g2()
                }

                126 -> {
                    t.footprintSize = buf.g2()
                }

                // flags: 129 unknown, 130 idle anim restart, 145 can hide for overlap, 147 no z-buffer
                129, 130, 145, 147 -> {
                }

                // overlap tint HSL
                146 -> {
                    buf.skip(2)
                }

                249 -> {
                    t.params = ParamMap.decode(buf)
                }

                251 -> {
                    t.ops.decodeSubOp(buf)
                }

                252 -> {
                    t.ops.decodeConditionalOp(buf)
                }

                253 -> {
                    t.ops.decodeConditionalSubOp(buf)
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }

    /** Op 102 (rev >= 210): bitfield of present slots; per slot a nullable big smart archive + smart-minus-one index. */
    private fun decodeHeadIcons(t: NpcType, buf: ReadBuf) {
        val bitfield = buf.g1()
        val slots = Int.SIZE_BITS - Integer.numberOfLeadingZeros(bitfield)
        val archives = IntArray(slots)
        val sprites = IntArray(slots)
        for (i in 0 until slots) {
            if ((bitfield and (1 shl i)) == 0) {
                archives[i] = -1
                sprites[i] = -1
            } else {
                archives[i] = buf.gSmart2or4Null()
                sprites[i] = buf.gSmart1or2Null()
            }
        }
        t.headIconArchives = archives
        t.headIconSprites = sprites
    }

    /** Layout: varbit u16, varp u16, [default u16 for op 118], u8 count, count+1 u16 ids; 0xFFFF = -1. */
    private fun decodeMulti(t: NpcType, buf: ReadBuf, withDefault: Boolean) {
        t.multiVarbit = nullable(buf.g2())
        t.multiVarp = nullable(buf.g2())
        val default = if (withDefault) nullable(buf.g2()) else -1
        val count = buf.g1()
        val ids = IntArray(count + 2)
        for (i in 0..count) ids[i] = nullable(buf.g2())
        ids[count + 1] = default
        t.multiNpcs = ids
    }

    private fun nullable(v: Int): Int = if (v == NULL_U16) -1 else v
}
