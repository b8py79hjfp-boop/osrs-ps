// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.inv

import tp.cache.config.ConfigDecoder
import tp.cache.config.ParamMap
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/** Inventory config, archive 2 group 5. */
class InvType internal constructor(val id: Int) {
    /** Slot count (op 2). */
    var size: Int = 0
        internal set
    var params: ParamMap = ParamMap.EMPTY
        internal set
}

/**
 * Decoder for [InvType]. Opcode table: RuneLite 1.13.6 `InventoryLoader.java` (ops 2 and 249).
 * RS Mod's ops 200-205 (scope, stack type, default stock) are its own server-side packing, not cache data.
 */
object InvTypeDecoder : ConfigDecoder<InvType> {
    override val name: String = "inv"

    override fun decode(id: Int, buf: ReadBuf): InvType {
        val t = InvType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                2 -> {
                    t.size = buf.g2()
                }

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
