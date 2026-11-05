package tp.plugin

/**
 * IF_SETEVENTS bits (client `ServerActiveProperties`; layout as in RS Mod `IfEvent`): low word = flags, high word =
 * ops 1..32.
 */
@Suppress("MagicNumber") // bit positions are the client's
public object IfEvents {
    public const val PAUSE_BUTTON: Long = 1L shl 0
    public const val TGT_OBJ: Long = 1L shl 11
    public const val TGT_NPC: Long = 1L shl 12
    public const val TGT_LOC: Long = 1L shl 13
    public const val TGT_PLAYER: Long = 1L shl 14
    public const val TGT_INV: Long = 1L shl 15
    public const val TGT_COM: Long = 1L shl 16
    public const val DEPTH1: Long = 1L shl 17
    public const val DRAG_TARGET: Long = 1L shl 20
    public const val TARGET: Long = 1L shl 21

    /** Op [n] (1..32). */
    public fun op(n: Int): Long {
        require(n in 1..32) { "op $n" }
        return 1L shl (31 + n)
    }
}
