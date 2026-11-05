package tp.cache.interfaces

/**
 * One interface component ("IfType"), cache archive 3, group = interface id, file = component id. Server-relevant
 * fields only; sprites, fonts, colours, models, scroll sizes and every clientscript hook / transmit trigger are consumed
 * and dropped. Defaults are the client's. Mutable only inside this module (decoder).
 *
 * Two on-disk layouts exist: IF1 (legacy, first byte is the type) and IF3 (first byte 0xFF, then the type). Fields a
 * layout lacks keep their default. Position/size values are raw as stored (IF3 position/size modes are separate fields).
 */
class ComponentType internal constructor(val interfaceId: Int, val componentId: Int) {
    /** `(interfaceId shl 16) or componentId`, the id the client and protocol use for a component. */
    val packedId: Int = (interfaceId shl COMPONENT_BITS) or componentId

    /** True for the IF3 layout (leading 0xFF byte), false for IF1. */
    var isIf3: Boolean = false
        internal set

    /** Component type: 0 layer, 3 rectangle, 4 text, 5 graphic, 6 model, 9 line; IF1 only: 2 inventory, 7 text inventory, 8 text. */
    var type: Int = 0
        internal set

    /** Content type (client-side data binding), raw u16. */
    var contentType: Int = 0
        internal set

    /** IF1 menu type (button behaviour: 1 ok, 2 target, 4 select, 5 toggle, 6 continue); 0 for IF3. */
    var menuType: Int = 0
        internal set
    var x: Int = 0
        internal set
    var y: Int = 0
        internal set
    var width: Int = 0
        internal set
    var height: Int = 0
        internal set

    /** IF3 width / height / x / y position modes (raw signed bytes); 0 for IF1. */
    var widthMode: Int = 0
        internal set
    var heightMode: Int = 0
        internal set
    var xPositionMode: Int = 0
        internal set
    var yPositionMode: Int = 0
        internal set

    /** Packed id of the parent layer component, or -1. */
    var parentId: Int = -1
        internal set

    /** IF1 only: component id (same interface) of the sibling shown on hover, or -1. */
    var hoveredSiblingId: Int = -1
        internal set
    var isHidden: Boolean = false
        internal set

    /**
     * IF3 click mask (u24: op enable bits, target flags, drag flags). For IF1 only the target-flag bits
     * (`(u16 and 63) shl 11`) of menu type 2 / inventory components.
     */
    var clickMask: Int = 0
        internal set

    /** IF3 component name (op target text, e.g. a coloured item name), or empty. */
    var name: String = ""
        internal set

    /** IF3 op texts (op 1..n); entries may be empty strings, meaning no op. Empty array when none. */
    var actions: Array<String> = NO_STRINGS
        internal set

    /** Target verb ("Use", "Cast", ...) for IF3 targetable components and IF1 menu type 2 / inventories; empty if none. */
    var targetVerb: String = ""
        internal set

    /** IF1 spell name (menu type 2 or inventory components), or empty. */
    var spellName: String = ""
        internal set

    /** IF1 tooltip (menu types 1, 4, 5, 6) with the client's empty-string fallbacks applied, or empty. */
    var tooltip: String = ""
        internal set

    /** IF1 inventory (type 2) / text inventory (type 7) slot actions, 5 entries, empty string = none; empty array otherwise. */
    var configActions: Array<String> = NO_STRINGS
        internal set

    /** IF1 inventory slot pitch (type 2 u8, type 7 i16) in pixels; slot count is [width] * [height]. */
    var xPitch: Int = 0
        internal set
    var yPitch: Int = 0
        internal set

    /** IF3 drag dead zone (pixels) and dead time (client ticks), raw u8 each. */
    var dragDeadZone: Int = 0
        internal set
    var dragDeadTime: Int = 0
        internal set

    /** Text for text components (IF1 type 4 / 8, IF3 type 4), or empty. */
    var text: String = ""
        internal set

    val isInventory: Boolean get() = !isIf3 && (type == IF1_TYPE_INVENTORY || type == IF1_TYPE_TEXT_INVENTORY)

    companion object {
        /** Component ids occupy the low 16 bits of a packed id. */
        const val COMPONENT_BITS = 16

        /** IF1 type 2: item inventory grid. Source: RuneLite `InterfaceLoader.decodeIf1`. */
        const val IF1_TYPE_INVENTORY = 2

        /** IF1 type 7: text inventory grid. Source: RuneLite `InterfaceLoader.decodeIf1`. */
        const val IF1_TYPE_TEXT_INVENTORY = 7

        private val NO_STRINGS: Array<String> = emptyArray()
    }
}

/** An interface (cache archive 3 group) and its components, indexed by component id. */
class InterfaceType internal constructor(val id: Int, val components: Map<Int, ComponentType>) {
    operator fun get(componentId: Int): ComponentType? = components[componentId]
}
