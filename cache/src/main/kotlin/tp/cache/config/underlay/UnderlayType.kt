package tp.cache.config.underlay

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/** Floor underlay config, archive 2 group 1. */
class UnderlayType internal constructor(val id: Int) {
    /** 24-bit RGB colour (op 1). */
    var rgb: Int = 0
        internal set
}

/** Decoder for [UnderlayType]. Opcode table: RuneLite 1.13.6 `UnderlayLoader.java`. */
object UnderlayTypeDecoder : ConfigDecoder<UnderlayType> {
    override val name: String = "underlay"

    override fun decode(id: Int, buf: ReadBuf): UnderlayType {
        val t = UnderlayType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                1 -> {
                    t.rgb = buf.g3()
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }
}
