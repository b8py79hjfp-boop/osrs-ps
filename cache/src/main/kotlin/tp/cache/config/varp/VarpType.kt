// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.varp

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/** Player variable config, archive 2 group 16. */
class VarpType internal constructor(val id: Int) {
    /** Client-side behaviour selector (op 5, RS Mod `clientCode`); 0 = plain varp. */
    var clientCode: Int = 0
        internal set
}

/**
 * Decoder for [VarpType]. Opcode table: RS Mod `VarpTypeDecoder` (rev 233); RuneLite has no varp loader.
 * RS Mod's ops 202-204 are its own server-side packing and are not part of the cache, so they are not accepted here.
 */
object VarpTypeDecoder : ConfigDecoder<VarpType> {
    override val name: String = "varp"

    override fun decode(id: Int, buf: ReadBuf): VarpType {
        val t = VarpType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                5 -> {
                    t.clientCode = buf.g2()
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }
}
