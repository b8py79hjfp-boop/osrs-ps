// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.overlay

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/** Floor overlay config, archive 2 group 4. */
class OverlayType internal constructor(val id: Int) {
    /** 24-bit RGB colour (op 1). */
    var rgb: Int = 0
        internal set

    /** Texture id (op 2), or -1. */
    var texture: Int = -1
        internal set

    /** False when op 5 is present: the underlay shows through. */
    var hideUnderlay: Boolean = true
        internal set

    /** Secondary 24-bit RGB colour (op 7), or -1. */
    var secondaryRgb: Int = -1
        internal set
}

/** Decoder for [OverlayType]. Opcode table: RuneLite 1.13.6 `OverlayLoader.java`. */
object OverlayTypeDecoder : ConfigDecoder<OverlayType> {
    override val name: String = "overlay"

    override fun decode(id: Int, buf: ReadBuf): OverlayType {
        val t = OverlayType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                1 -> {
                    t.rgb = buf.g3()
                }

                2 -> {
                    t.texture = buf.g1()
                }

                5 -> {
                    t.hideUnderlay = false
                }

                7 -> {
                    t.secondaryRgb = buf.g3()
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }
}
