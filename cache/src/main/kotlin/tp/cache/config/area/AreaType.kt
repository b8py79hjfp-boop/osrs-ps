// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.area

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/**
 * Area config, archive 2 group 18. Reference: RS Mod `AreaTypeDecoder` only (RuneLite has no loader; its AREA is
 * group 35, see [tp.cache.config.mapelement]). Cache 2757 holds 1155 files, all empty; the one RS Mod opcode is its
 * own server packing.
 */
class AreaType internal constructor(val id: Int) {
    /** Debug colour (op 200, RS Mod `colour`), or 0. */
    var colour: Int = 0
        internal set
}

/** Decoder for [AreaType]. Opcode table: RS Mod `AreaTypeDecoder` (rev 233). */
object AreaTypeDecoder : ConfigDecoder<AreaType> {
    override val name: String = "area"

    override fun decode(id: Int, buf: ReadBuf): AreaType {
        val t = AreaType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                200 -> {
                    t.colour = buf.g4()
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }
}
