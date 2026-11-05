// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.mapelement

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/** World-map element config, archive 2 group 35 (RuneLite calls it AREA). Render data is dropped. */
class MapElementType internal constructor(val id: Int) {
    var name: String? = null
        internal set

    /** Graphic (sprite) id (op 1), or -1. */
    var sprite: Int = -1
        internal set

    /** Menu options (ops 10-14). */
    val ops: Array<String?> = arrayOfNulls(OP_COUNT)
    var category: Int = -1
        internal set

    companion object {
        /** Five op slots (ops 10..14). */
        const val OP_COUNT = 5
    }
}

/** Decoder for [MapElementType]. Opcode table: RuneLite 1.13.6 `AreaLoader.java`. */
object MapElementTypeDecoder : ConfigDecoder<MapElementType> {
    override val name: String = "mapelement"

    private const val OP_FIRST = 10
    private const val OP_END = 15

    /** Op 15: bytes per polygon vertex pair (two i16). */
    private const val VERTEX_BYTES = 4

    /** Op 15: bytes per entry in the trailing i32 table. */
    private const val INT_BYTES = 4

    override fun decode(id: Int, buf: ReadBuf): MapElementType {
        val t = MapElementType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                1 -> {
                    t.sprite = buf.gSmart2or4Null()
                }

                // second sprite, op 18 and 25 sprites (big smart)
                2, 18, 25 -> {
                    buf.gSmart2or4Null()
                }

                3 -> {
                    t.name = buf.gjstr()
                }

                // text colour, extra colour (u24)
                4, 5 -> {
                    buf.skip(3)
                }

                // text scale, flags, byte 8, byte 28, enums 29 / 30
                6, 7, 8, 28, 29, 30 -> {
                    buf.skip(1)
                }

                in OP_FIRST until OP_END -> {
                    t.ops[opcode - OP_FIRST] = buf.gjstr()
                }

                15 -> {
                    val n = buf.g1()
                    buf.skip(n * VERTEX_BYTES)
                    buf.skip(INT_BYTES)
                    val m = buf.g1()
                    buf.skip(m * INT_BYTES)
                    buf.skip(n)
                }

                // empty flag
                16 -> {
                }

                17 -> {
                    buf.gjstr()
                }

                19 -> {
                    t.category = buf.g2()
                }

                21, 22 -> {
                    buf.skip(4)
                }

                23 -> {
                    buf.skip(3)
                }

                24 -> {
                    buf.skip(4)
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }
}
