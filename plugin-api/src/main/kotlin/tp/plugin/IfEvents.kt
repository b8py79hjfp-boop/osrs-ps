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

    /** Drag depth 2 (value 2 of the 3-bit depth field at bit 17; RS Mod `IfEvent.Depth2`). */
    public const val DEPTH2: Long = 2L shl 17
    public const val DRAG_TARGET: Long = 1L shl 20
    public const val TARGET: Long = 1L shl 21

    /**
     * Lets the component's clientscripts send IF_SCRIPTTRIGGER (`if_runscript*`): the rev 242 client checks
     * `(events >> 23) & 1` before sending (injected client `is.ft`, D-072); without it the call does nothing.
     */
    public const val SCRIPT_TRIGGER: Long = 1L shl 23

    /** Op [n] (1..32). */
    public fun op(n: Int): Long {
        require(n in 1..32) { "op $n" }
        return 1L shl (31 + n)
    }
}
