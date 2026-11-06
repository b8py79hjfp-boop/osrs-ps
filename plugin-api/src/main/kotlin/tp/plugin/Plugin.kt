package tp.plugin

/**
 * A unit of content. Each content module's plugins are found at compile time by KSP (tools/ksp-registry), which
 * generates that module's [ContentRegistry]; nothing scans the classpath (brief §6).
 */
public interface ContentPlugin {
    public fun ContentScope.register()
}

/** Generated per content module: the module's plugins. */
public interface ContentRegistry {
    public val plugins: List<ContentPlugin>
}

/** An item dragged from slot [fromSlot] of [fromComponent] onto slot [toSlot] of [toComponent] (-1 = no slot). */
public class ButtonDrag internal constructor(
    public val fromComponent: Int,
    public val fromSlot: Int,
    public val toComponent: Int,
    public val toSlot: Int,
)

/** IF3 button click as delivered to an [ContentScope.onButton] handler (already validated: the interface is open). */
public class ButtonClick internal constructor(
    /** Packed component (`iface << 16 | comp`), as in `tp.symbols.Components`. */
    public val component: Int,
    /** 1-based op. */
    public val op: Int,
    /** Slot / sub-component, -1 if none. */
    public val slot: Int,
    /** Obj in that slot, -1 if none. */
    public val obj: Int,
    /** Right-click sub-option (IF_SUBOP), -1 for the plain op. */
    public val subop: Int = -1,
)

/**
 * An item (or spell component) used on an inventory item: [from] slot [fromSlot] holding [fromObj] onto [to] slot
 * [toSlot] holding [toObj].
 */
public class ItemOnItem internal constructor(
    public val from: Int,
    public val fromSlot: Int,
    public val fromObj: Int,
    public val to: Int,
    public val toSlot: Int,
    public val toObj: Int,
) {
    internal fun swapped(): ItemOnItem = ItemOnItem(to, toSlot, toObj, from, fromSlot, fromObj)
}

/** The inventory slot a use-on interaction started from ([PlayerScript.used]). */
public class UsedItem internal constructor(public val component: Int, public val slot: Int, public val obj: Int)
