package tp.tools.cachepack

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads `.if3` files (TOML, see below) and builds the [If3Component]s of one interface.
 *
 * ```
 * [[interface]]                  one per file
 * name = "interface.spawn_menu"  gameval name; the part after the dot is the interface's name
 * width = 512                    size of the root layer (component 0), which the packer adds
 * height = 334
 * on_load = ["clientscript.x", "component.spawn_menu:grid"]   any hook key of a component may appear here (root layer)
 *
 * [[component]]                  ids follow file order from 1
 * name = "grid"                  required, unique; referenced as `component.<interface>:<name>`
 * type = "layer"                 layer | rectangle | text | graphic | model | line
 * parent = "other"               component name; omitted = child of the root layer
 * ```
 *
 * Fields (all optional but `name` and `type`): `x y width height`, `x_mode` (`abs_left` ... `ratio_right`, or 0..5), `y_mode`
 * (`abs_top` ... `ratio_bottom`), `width_mode` / `height_mode` (`abs`, `minus`, `ratio`), `hidden`, `content_type`,
 * `options` (op texts; op N is enabled in the click mask), `op_target`, `target_verb`, `click_mask` (overrides the derived mask),
 * `drag_dead_zone`, `drag_dead_time`, `drag_render`;
 * layer: `scroll_width scroll_height no_click_through`; rectangle: `color filled opacity`;
 * text: `text font color shadow h_align v_align line_height`; graphic: `sprite texture tiling opacity border shadow_color
 * flip_vertical flip_horizontal`; model: `model offset_x offset_y rotation_x rotation_y rotation_z zoom animation orthogonal`;
 * line: `line_width color direction`; hooks: `on_load on_mouse_over on_mouse_leave on_target_leave on_target_enter on_var_transmit
 * on_inv_transmit on_stat_transmit on_timer on_op on_mouse_repeat on_click on_click_repeat on_release on_hold on_drag
 * on_drag_complete on_scroll_wheel`, each `[script, args...]`; triggers: `var_triggers inv_triggers stat_triggers`.
 *
 * A string in a hook list or trigger list that looks like `namespace.name` (see [Resolver]) is replaced by the id it names; any
 * other string is passed to the script as a string. Colours are `"#RRGGBB"` or integers. `font` is a font name or id, with
 * `regular` = p12_full and `bold` = b12_full (D-073). Component 0 is the root layer, as in every interface of the cache
 * that has one root; children without `parent` hang off it.
 */
internal class InterfaceDef(
    val name: String,
    val width: Int,
    val height: Int,
    val rootSection: Map<String, Any>,
    val components: List<MiniToml.Section>,
    val source: Path,
) {
    /** Component name to component id (the root layer is `0` and has no name). */
    val ids: Map<String, Int> = components.mapIndexed { i, s -> componentName(s) to (i + 1) }.toMap()

    companion object {
        const val ROOT = 0

        fun componentName(section: MiniToml.Section): String =
            section.values["name"] as? String ?: throw IOException("line ${section.line}: component without a name")

        fun parse(path: Path): InterfaceDef {
            val sections = MiniToml.parse(Files.readString(path), path.toString())
            val iface = sections.singleOrNull { it.name == "interface" } ?: throw IOException("$path: needs exactly one [[interface]]")
            val components = sections.filter { it.name == "component" }
            val unknown = sections.filter { it.name != "interface" && it.name != "component" }
            if (unknown.isNotEmpty()) throw IOException("$path: unknown section [${unknown.first().name}] on line ${unknown.first().line}")
            val full = iface.values["name"] as? String ?: throw IOException("$path: [[interface]] needs a name")
            val name = full.removePrefix("interface.")
            val names = components.map(::componentName)
            names.groupBy { it }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
                throw IOException(
                    "$path: duplicate component '$it'",
                )
            }
            return InterfaceDef(
                name,
                (iface.values["width"] as? Int) ?: DEFAULT_WIDTH,
                (iface.values["height"] as? Int) ?: DEFAULT_HEIGHT,
                iface.values,
                components,
                path,
            )
        }

        /** Size of a full modal in the standard gameframe; used when the file does not say. */
        private const val DEFAULT_WIDTH = 512
        private const val DEFAULT_HEIGHT = 334
    }
}

internal object InterfacePacker {
    private val TYPES = mapOf("layer" to 0, "rectangle" to 3, "text" to 4, "graphic" to 5, "model" to 6, "line" to 9)

    /** `cc_setsize` modes: `^setsize_abs`, `^setsize_minus`, `^setsize_ratio`. */
    private val SIZE_MODES = mapOf("abs" to 0, "minus" to 1, "ratio" to 2)

    /** `cc_setposition` modes: `^setpos_abs_left` ... `^setpos_ratio_right`. */
    private val X_MODES =
        mapOf(
            "abs_left" to 0,
            "abs_centre" to 1,
            "abs_center" to 1,
            "abs_right" to 2,
            "ratio_left" to 3,
            "ratio_centre" to 4,
            "ratio_center" to 4,
            "ratio_right" to 5,
        )
    private val Y_MODES =
        mapOf(
            "abs_top" to 0,
            "abs_centre" to 1,
            "abs_center" to 1,
            "abs_bottom" to 2,
            "ratio_top" to 3,
            "ratio_centre" to 4,
            "ratio_center" to 4,
            "ratio_bottom" to 5,
        )
    private val H_ALIGN = mapOf("left" to 0, "centre" to 1, "center" to 1, "right" to 2)
    private val V_ALIGN = mapOf("top" to 0, "centre" to 1, "center" to 1, "bottom" to 2)

    /** Font aliases of the `.if3` format (D-073); other names come from `fontmetrics.sym`. */
    private val FONT_ALIASES = mapOf("regular" to "p12_full", "bold" to "b12_full", "small" to "p11_full")

    /** Op N (1-based) is enabled by bit N of the IF3 click mask: every op-bearing cache component has `1 shl N` set (checked). */
    private fun opMask(actions: List<String>): Int =
        actions.withIndex().filter { it.value.isNotEmpty() }.fold(0) { mask, op -> mask or (1 shl (op.index + 1)) }

    /** Builds the component list of [def] indexed by component id (0 is the root layer). */
    fun build(def: InterfaceDef, resolver: Resolver): List<If3Component> {
        val out = ArrayList<If3Component>(def.components.size + 1)
        val root = If3Component(If3Component.TYPE_LAYER)
        root.width = def.width
        root.height = def.height
        applyHooks(root, def.rootSection, resolver, "interface")
        out += root
        for (section in def.components) {
            out += component(def, section, resolver)
        }
        return out
    }

    private fun component(def: InterfaceDef, section: MiniToml.Section, resolver: Resolver): If3Component =
        component(section, "${def.source}: component '${InterfaceDef.componentName(section)}'", resolver) { parent, where ->
            when (parent) {
                null -> InterfaceDef.ROOT
                is String -> def.ids[parent] ?: throw IOException("$where: unknown parent '$parent'")
                else -> throw IOException("$where: parent must be a component name")
            }
        }

    /** One component from its section; [parentOf] turns the `parent` value into a component id (D-096 patches allow ids). */
    fun component(
        section: MiniToml.Section,
        where: String,
        resolver: Resolver,
        parentOf: (Any?, String) -> Int,
    ): If3Component {
        val v = section.values
        val typeName = v["type"] ?: throw IOException("$where: missing type")
        val type = (typeName as? Int) ?: TYPES[typeName] ?: throw IOException("$where: unknown type '$typeName'")
        val c = If3Component(type)
        c.parent = parentOf(v["parent"], where)
        c.x = int(v, "x", where)
        c.y = int(v, "y", where)
        c.width = int(v, "width", where)
        c.height = int(v, "height", where)
        c.xMode = mode(v, "x_mode", X_MODES, where)
        c.yMode = mode(v, "y_mode", Y_MODES, where)
        c.widthMode = mode(v, "width_mode", SIZE_MODES, where)
        c.heightMode = mode(v, "height_mode", SIZE_MODES, where)
        c.hidden = bool(v, "hidden", false, where)
        c.contentType = int(v, "content_type", where)
        c.name = string(v, "op_target", where)
        c.targetVerb = string(v, "target_verb", where)
        c.actions = strings(v, "options", where)
        c.clickMask = if ("click_mask" in v) int(v, "click_mask", where) else opMask(c.actions)
        c.dragDeadZone = int(v, "drag_dead_zone", where)
        c.dragDeadTime = int(v, "drag_dead_time", where)
        c.dragRender = bool(v, "drag_render", false, where)
        typeFields(c, v, where, resolver)
        applyHooks(c, v, resolver, where)
        return c
    }

    @Suppress("CyclomaticComplexMethod")
    private fun typeFields(c: If3Component, v: Map<String, Any>, where: String, resolver: Resolver) {
        when (c.type) {
            If3Component.TYPE_LAYER -> {
                c.scrollWidth = int(v, "scroll_width", where)
                c.scrollHeight = int(v, "scroll_height", where)
                c.noClickThrough = bool(v, "no_click_through", false, where)
            }

            If3Component.TYPE_RECTANGLE -> {
                c.color = color(v, "color", where)
                c.filled = bool(v, "filled", true, where)
                c.opacity = int(v, "opacity", where)
            }

            If3Component.TYPE_TEXT -> {
                c.text = string(v, "text", where)
                c.font = font(v["font"], where, resolver)
                c.color = color(v, "color", where)
                c.shadow = bool(v, "shadow", true, where)
                c.xAlign = mode(v, "h_align", H_ALIGN, where)
                c.yAlign = mode(v, "v_align", V_ALIGN, where)
                c.lineHeight = int(v, "line_height", where)
            }

            If3Component.TYPE_GRAPHIC -> {
                c.sprite = reference(v["sprite"], "sprite", where, resolver)
                c.texture = int(v, "texture", where)
                c.tiling = bool(v, "tiling", false, where)
                c.opacity = int(v, "opacity", where)
                c.border = int(v, "border", where)
                c.shadowColor = color(v, "shadow_color", where)
                c.flipVertical = bool(v, "flip_vertical", false, where)
                c.flipHorizontal = bool(v, "flip_horizontal", false, where)
            }

            If3Component.TYPE_MODEL -> {
                c.model = reference(v["model"], "model", where, resolver)
                c.offsetX = int(v, "offset_x", where)
                c.offsetY = int(v, "offset_y", where)
                c.rotationX = int(v, "rotation_x", where)
                c.rotationY = int(v, "rotation_y", where)
                c.rotationZ = int(v, "rotation_z", where)
                c.zoom = int(v, "zoom", where)
                c.animation = if ("animation" in v) reference(v["animation"], "seq", where, resolver) else -1
                c.orthogonal = bool(v, "orthogonal", false, where)
            }

            If3Component.TYPE_LINE -> {
                c.lineWidth = if ("line_width" in v) int(v, "line_width", where) else 1
                c.color = color(v, "color", where)
                c.lineDirection = bool(v, "direction", false, where)
            }
        }
    }

    private fun applyHooks(c: If3Component, v: Map<String, Any>, resolver: Resolver, where: String) {
        for ((i, key) in If3Component.HOOK_NAMES.withIndex()) {
            val list = v[key] ?: continue
            c.hooks[i] = hookArgs(list, key, where, resolver)
        }
        for ((i, key) in If3Component.TRIGGER_NAMES.withIndex()) {
            val list = v[key] as? List<*> ?: continue
            c.triggers[i] = list.map { reference(it, "trigger", "$where $key", resolver) }.toIntArray()
        }
    }

    private fun hookArgs(list: Any, key: String, where: String, resolver: Resolver): List<Any> {
        val items = list as? List<*> ?: throw IOException("$where: $key must be an array: [script, args...]")
        if (items.isEmpty()) return emptyList()
        return items.mapIndexed { i, item ->
            when {
                item is Int -> {
                    item
                }

                item is String && resolver.looksLikeReference(item) -> {
                    resolver.resolve(item)
                }

                item is String -> {
                    if (i == 0) throw IOException("$where: $key must start with a script reference, got \"$item\"")
                    item
                }

                else -> {
                    throw IOException("$where: $key has an unsupported element $item")
                }
            }
        }
    }

    private fun reference(value: Any?, what: String, where: String, resolver: Resolver): Int =
        when (value) {
            null -> {
                -1
            }

            is Int -> {
                value
            }

            is String -> {
                if (resolver.looksLikeReference(
                        value,
                    )
                ) {
                    resolver.resolve(value)
                } else {
                    throw IOException("$where: bad $what '$value'")
                }
            }

            else -> {
                throw IOException("$where: bad $what '$value'")
            }
        }

    private fun font(value: Any?, where: String, resolver: Resolver): Int =
        when (value) {
            null -> {
                -1
            }

            is Int -> {
                value
            }

            is String -> {
                resolver.resolve(
                    "fontmetrics." + (FONT_ALIASES[value] ?: value.removePrefix("font.").removePrefix("fontmetrics.")),
                )
            }

            else -> {
                throw IOException("$where: bad font '$value'")
            }
        }

    private fun int(v: Map<String, Any>, key: String, where: String): Int =
        when (val x = v[key]) {
            null -> 0
            is Int -> x
            else -> throw IOException("$where: $key must be an integer")
        }

    private fun bool(v: Map<String, Any>, key: String, default: Boolean, where: String): Boolean =
        when (val x = v[key]) {
            null -> default
            is Boolean -> x
            else -> throw IOException("$where: $key must be true or false")
        }

    private fun string(v: Map<String, Any>, key: String, where: String): String =
        when (val x = v[key]) {
            null -> ""
            is String -> x
            else -> throw IOException("$where: $key must be a string")
        }

    private fun strings(v: Map<String, Any>, key: String, where: String): List<String> =
        when (val x = v[key]) {
            null -> emptyList()
            is List<*> -> x.map { it as? String ?: throw IOException("$where: $key must hold strings") }
            else -> throw IOException("$where: $key must be an array of strings")
        }

    private fun mode(v: Map<String, Any>, key: String, names: Map<String, Int>, where: String): Int =
        when (val x = v[key]) {
            null -> 0
            is Int -> x
            is String -> names[x] ?: throw IOException("$where: unknown $key '$x'")
            else -> throw IOException("$where: bad $key")
        }

    /** `"#RRGGBB"` or an integer. */
    private fun color(v: Map<String, Any>, key: String, where: String): Int =
        when (val x = v[key]) {
            null -> {
                0
            }

            is Int -> {
                x
            }

            is String -> {
                if (x.startsWith("#") &&
                    x.length == COLOR_LENGTH
                ) {
                    x.substring(1).toIntOrNull(HEX) ?: bad(where, key, x)
                } else {
                    bad(where, key, x)
                }
            }

            else -> {
                bad(where, key, x)
            }
        }

    private fun bad(where: String, key: String, value: Any): Nothing = throw IOException("$where: bad $key '$value'")

    private const val COLOR_LENGTH = 7
    private const val HEX = 16
}
