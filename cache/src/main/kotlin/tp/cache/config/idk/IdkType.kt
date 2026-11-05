// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.idk

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/** Identity kit (character appearance part) config, archive 2 group 3. Models and recolours are dropped. */
class IdkType internal constructor(val id: Int) {
    /** Body part slot (op 1), or -1. */
    var bodyPart: Int = -1
        internal set

    /** Op 3: not offered in the character designer. */
    var nonSelectable: Boolean = false
        internal set
}

/** Decoder for [IdkType]. Opcode table: RuneLite 1.13.6 `KitLoader.java`. */
object IdkTypeDecoder : ConfigDecoder<IdkType> {
    override val name: String = "idk"

    private const val OP_CHATHEAD_U16_FIRST = 60
    private const val OP_CHATHEAD_U16_END = 70
    private const val OP_CHATHEAD_I32_FIRST = 70
    private const val OP_CHATHEAD_I32_END = 80

    override fun decode(id: Int, buf: ReadBuf): IdkType {
        val t = IdkType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                1 -> {
                    t.bodyPart = buf.g1()
                }

                // model ids (u16)
                2 -> {
                    buf.skip(buf.g1() * 2)
                }

                3 -> {
                    t.nonSelectable = true
                }

                // model ids (i32)
                5 -> {
                    buf.skip(buf.g1() * 4)
                }

                // recolour / retexture pairs
                40, 41 -> {
                    buf.skip(buf.g1() * 4)
                }

                in OP_CHATHEAD_U16_FIRST until OP_CHATHEAD_U16_END -> {
                    buf.skip(2)
                }

                in OP_CHATHEAD_I32_FIRST until OP_CHATHEAD_I32_END -> {
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
