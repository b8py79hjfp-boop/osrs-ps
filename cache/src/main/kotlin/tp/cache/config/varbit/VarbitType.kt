package tp.cache.config.varbit

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/** Varbit config, archive 2 group 14: a bit range inside a varp. */
class VarbitType internal constructor(val id: Int) {
    /** Varp holding the bits (RS Mod `baseVar`). */
    var baseVar: Int = -1
        internal set

    /** Least significant bit of the range, inclusive. */
    var startBit: Int = 0
        internal set

    /** Most significant bit of the range, inclusive. */
    var endBit: Int = 0
        internal set
}

/** Decoder for [VarbitType]. Opcode table: RuneLite 1.13.6 `VarbitLoader.java` (identical in RS Mod `VarBitTypeDecoder`). */
object VarbitTypeDecoder : ConfigDecoder<VarbitType> {
    override val name: String = "varbit"

    override fun decode(id: Int, buf: ReadBuf): VarbitType {
        val t = VarbitType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                1 -> {
                    t.baseVar = buf.g2()
                    t.startBit = buf.g1()
                    t.endBit = buf.g1()
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }
}
