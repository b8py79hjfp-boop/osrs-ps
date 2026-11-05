// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.struct

import tp.cache.config.ConfigDecoder
import tp.cache.config.ParamMap
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/** Struct config, archive 2 group 34: a bag of params. */
class StructType internal constructor(val id: Int) {
    var params: ParamMap = ParamMap.EMPTY
        internal set
}

/** Decoder for [StructType]. Opcode table: RuneLite 1.13.6 `StructLoader.java` (only op 249), same in RS Mod. */
object StructTypeDecoder : ConfigDecoder<StructType> {
    override val name: String = "struct"

    override fun decode(id: Int, buf: ReadBuf): StructType {
        val t = StructType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                249 -> {
                    t.params = ParamMap.decode(buf)
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }
}
