// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.hitmark

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/** Hitsplat config, archive 2 group 32. Fonts, sprites and offsets are render data and are dropped. */
class HitmarkType internal constructor(val id: Int) {
    /** Text format (op 8, RuneLite `stringFormat`), or null. */
    var format: String? = null
        internal set

    /** Cycles the splat stays visible (op 9, RS Mod `stickTime`); client default 70. */
    var displayCycles: Int = DEFAULT_DISPLAY_CYCLES
        internal set

    /** Op 12 (RuneLite `useDamage`, RS Mod `replaceMode`); -1 when absent. */
    var useDamage: Int = -1
        internal set
    var multiVarbit: Int = -1
        internal set
    var multiVarp: Int = -1
        internal set

    /** Multi-hitmark transforms; last entry is the default. Null when the hitmark doesn't transform. */
    var multiHitmarks: IntArray? = null
        internal set

    companion object {
        /** Client `HitSplatType` default display time; RuneLite `HitSplatDefinition.displayCycles`. */
        const val DEFAULT_DISPLAY_CYCLES = 70
    }
}

/** Decoder for [HitmarkType]. Opcode table: RuneLite 1.13.6 `HitSplatLoader.java`, cross-checked with RS Mod `HitmarkTypeDecoder`. */
object HitmarkTypeDecoder : ConfigDecoder<HitmarkType> {
    override val name: String = "hitmark"

    override fun decode(id: Int, buf: ReadBuf): HitmarkType {
        val t = HitmarkType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                // font, left sprite, left sprite 2, background sprite, right sprite (big smart)
                1, 3, 4, 5, 6 -> {
                    buf.gSmart2or4Null()
                }

                // text colour (u24)
                2 -> {
                    buf.skip(3)
                }

                // scroll offset x, scroll offset y, text offset y, fade start
                7, 10, 13, 14 -> {
                    buf.skip(2)
                }

                8 -> {
                    t.format = buf.gjstr2()
                }

                9 -> {
                    t.displayCycles = buf.g2()
                }

                // fade start = 0 (flag)
                11 -> {
                }

                12 -> {
                    t.useDamage = buf.g1()
                }

                17, 18 -> {
                    decodeMulti(t, buf, withDefault = opcode == 18)
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }

    /** Layout: varbit u16, varp u16, [default u16], u8 count, count+1 u16 ids; 0xFFFF = -1. Last entry is the default. */
    private fun decodeMulti(t: HitmarkType, buf: ReadBuf, withDefault: Boolean) {
        t.multiVarbit = nullable(buf.g2())
        t.multiVarp = nullable(buf.g2())
        val default = if (withDefault) nullable(buf.g2()) else -1
        val count = buf.g1()
        val ids = IntArray(count + 2)
        for (i in 0..count) ids[i] = nullable(buf.g2())
        ids[count + 1] = default
        t.multiHitmarks = ids
    }

    private fun nullable(v: Int): Int = if (v == NULL_U16) -1 else v

    private const val NULL_U16 = 0xFFFF
}
