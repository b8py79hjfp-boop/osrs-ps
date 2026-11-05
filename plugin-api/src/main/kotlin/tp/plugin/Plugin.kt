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
)
