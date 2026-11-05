// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.hunt

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/**
 * Hunt mode config, archive 2 group 2. Reference: RS Mod `HuntModeTypeDecoder` only (RuneLite has no loader). Cache
 * 2757 holds 342 files, all empty, so this layout is unverified against real data. Enum-like bytes are kept raw;
 * their meaning is RS Mod's `HuntType`, `HuntVis`, `NpcMode`, ... tables and is OPEN here.
 */
class HuntType internal constructor(val id: Int) {
    var type: Int = -1
        internal set
    var checkVis: Int = -1
        internal set
    var checkNotTooStrong: Int = -1
        internal set
    var checkNotBusy: Boolean = false
        internal set
    var findKeepHunting: Boolean = false
        internal set
    var findNewMode: Int = -1
        internal set
    var nobodyNear: Int = -1
        internal set
    var checkNotCombat: Int = -1
        internal set
    var checkNotCombatSelf: Int = -1
        internal set
    var checkAfk: Boolean = true
        internal set
    var rate: Int = 0
        internal set

    /** Ops 12-14: npc / obj / loc filter as (type, category), -1 = null. Index 0 npc, 1 obj, 2 loc; null when absent. */
    val checkEntity: Array<IntArray?> = arrayOfNulls(ENTITY_CHECKS)

    /** Ops 15-16: inventory conditions as (inv, obj-or-param, operator, value). Index 0 obj, 1 param; null when absent. */
    val checkInv: Array<IntArray?> = arrayOfNulls(INV_CHECKS)

    /** Ops 17-19: varp conditions as (varp, operator, value); null when absent. */
    val checkVar: Array<IntArray?> = arrayOfNulls(VAR_CHECKS)

    companion object {
        const val ENTITY_CHECKS = 3
        const val INV_CHECKS = 2
        const val VAR_CHECKS = 3
    }
}

/** Decoder for [HuntType]. Opcode table: RS Mod `HuntModeTypeDecoder` (rev 233). */
object HuntTypeDecoder : ConfigDecoder<HuntType> {
    override val name: String = "hunt"

    private const val NULL_U16 = 0xFFFF

    override fun decode(id: Int, buf: ReadBuf): HuntType {
        val t = HuntType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                1 -> {
                    t.type = buf.g1()
                }

                2 -> {
                    t.checkVis = buf.g1()
                }

                3 -> {
                    t.checkNotTooStrong = buf.g1()
                }

                4 -> {
                    t.checkNotBusy = true
                }

                5 -> {
                    t.findKeepHunting = true
                }

                6 -> {
                    t.findNewMode = buf.g1()
                }

                7 -> {
                    t.nobodyNear = buf.g1()
                }

                8 -> {
                    t.checkNotCombat = buf.g2()
                }

                9 -> {
                    t.checkNotCombatSelf = buf.g2()
                }

                10 -> {
                    t.checkAfk = false
                }

                11 -> {
                    t.rate = buf.g2()
                }

                12, 13, 14 -> {
                    t.checkEntity[opcode - 12] = intArrayOf(nullable(buf.g2()), nullable(buf.g2()))
                }

                15, 16 -> {
                    t.checkInv[opcode - 15] = intArrayOf(buf.g2(), buf.g2(), buf.g1(), buf.g4())
                }

                17, 18, 19 -> {
                    t.checkVar[opcode - 17] = intArrayOf(buf.g2(), buf.g1(), buf.g4())
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }

    private fun nullable(v: Int): Int = if (v == NULL_U16) -1 else v
}
