// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.worldentity

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/**
 * World entity config (boats and other moving worlds), archive 2 group 72. No reference decoder exists in RuneLite
 * 1.13.6, RS Mod or RSProt; the opcode table is read from the rev-242 client's decoder (the class RuneLite maps to
 * `net.runelite.api.WorldEntityConfig`, constructed from the config buffer; DECISIONS D-046). Names follow the
 * RuneLite API where it has one (`id`, `category`, `bounds*`); the remaining fields keep neutral names, OPEN.
 */
class WorldEntityType internal constructor(val id: Int) {
    var name: String = "null"
        internal set
    var category: Int = -1
        internal set

    /**
     * Bounds (`getBoundsX/Y/Width/Height` in the RuneLite API), ops 8, 9, 6, 7, in fine units (128 per tile). The data
     * reads as size then offset: config 1 (the raft, a 1×3-tile deck in the cache) has 128 × 384.
     */
    var boundsX: Int = 0
        internal set
    var boundsY: Int = 0
        internal set
    var boundsWidth: Int = 0
        internal set
    var boundsHeight: Int = 0
        internal set

    /** Ops 15..19: the five right-click options (null = none). */
    val ops: Array<String?> = arrayOfNulls(OPS)

    /** Op 14 (no payload): a flag, meaning OPEN. */
    var flag14: Boolean = false
        internal set

    /** Op 2 (byte), ops 4 and 5 (signed shorts), op 23 and 24 (enum bytes), op 25 and 27 (shorts), op 26 (2-or-4-byte smart). */
    var op2: Int = 0
        internal set
    var op4: Int = 0
        internal set
    var op5: Int = 0
        internal set
    var op23: Int = -1
        internal set
    var op24: Int = -1
        internal set
    var op25: Int = 0
        internal set
    var op26: Int = -1
        internal set
    var op27: Int = DEFAULT_OP27
        internal set

    companion object {
        const val OPS = 5

        /** Client default of the op-27 field before decoding. */
        const val DEFAULT_OP27 = 39188
    }
}

/** Decoder for [WorldEntityType] (opcodes from the rev-242 client, see the class KDoc). */
object WorldEntityTypeDecoder : ConfigDecoder<WorldEntityType> {
    override val name: String = "worldentity"

    override fun decode(id: Int, buf: ReadBuf): WorldEntityType {
        val t = WorldEntityType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                2 -> t.op2 = buf.g1()
                4 -> t.op4 = buf.g2s()
                5 -> t.op5 = buf.g2s()
                6 -> t.boundsWidth = buf.g2s()
                7 -> t.boundsHeight = buf.g2s()
                8 -> t.boundsX = buf.g2()
                9 -> t.boundsY = buf.g2()
                12 -> t.name = buf.gjstr()
                14 -> t.flag14 = true
                in 15..19 -> t.ops[opcode - 15] = buf.gjstr()
                20 -> t.category = buf.g2()
                23 -> t.op23 = buf.g1()
                24 -> t.op24 = buf.g1()
                25 -> t.op25 = buf.g2()
                26 -> t.op26 = buf.gSmart2or4()
                27 -> t.op27 = buf.g2()
                else -> throw UnknownOpcodeException(name, id, opcode, buf.position)
            }
        }
        return t
    }
}
