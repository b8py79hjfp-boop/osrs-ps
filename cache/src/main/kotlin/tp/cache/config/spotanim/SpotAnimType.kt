// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.spotanim

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/** Spot animation ("graphic") config, archive 2 group 13. Only the animation link is kept; model/render data is dropped. */
class SpotAnimType internal constructor(val id: Int) {
    /** Sequence played by the graphic (op 2), or -1. */
    var seq: Int = -1
        internal set
}

/** Decoder for [SpotAnimType]. Opcode table: RuneLite 1.13.6 `SpotAnimLoader.java`, cross-checked with RS Mod `SpotanimTypeDecoder`. */
object SpotAnimTypeDecoder : ConfigDecoder<SpotAnimType> {
    override val name: String = "spotanim"

    override fun decode(id: Int, buf: ReadBuf): SpotAnimType {
        val t = SpotAnimType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                // model (u16), resize h/v, rotation
                1, 4, 5, 6 -> {
                    buf.skip(2)
                }

                2 -> {
                    t.seq = buf.g2().let { if (it == NULL_U16) -1 else it }
                }

                // model (i32)
                3 -> {
                    buf.skip(4)
                }

                // ambient, contrast
                7, 8 -> {
                    buf.skip(1)
                }

                // debug name
                9 -> {
                    buf.gjstr()
                }

                // not clickable (flag)
                10 -> {
                }

                // recolour / retexture pairs
                40, 41 -> {
                    buf.skip(buf.g1() * 4)
                }

                // full recolour
                42 -> {
                    buf.skip(2)
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }

    private const val NULL_U16 = 0xFFFF
}
