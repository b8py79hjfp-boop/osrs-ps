package tp.cache.interfaces

import tp.util.buf.ReadBuf
import java.io.IOException

/**
 * Decoder for [ComponentType]. Layouts: RuneLite 1.13.6 `cache/.../loaders/InterfaceLoader.java` (`decodeIf1` / `decodeIf3`,
 * `rev237` branch, i.e. model ids are i32). RS Mod (rev 233) `ComponentTypeDecoder` agrees except that it reads model ids
 * as u16 (pre-237 layout); RuneLite wins for rev 242, with one deviation for IF3 model size overrides (see
 * [IF3_MODEL_SIZE_OVERRIDE_BYTES]; OPEN: confirm against the client). Unlike RuneLite, anything unexpected throws: unknown IF3 hook
 * argument tags, and files with unread trailing bytes (checked by [InterfaceTypes.load]).
 */
object ComponentTypeDecoder {
    /** First byte of an IF3 component file (-1 as a signed byte). */
    private const val IF3_MARKER = 0xFF
    private const val NULL_U16 = 0xFFFF
    private const val COMPONENT_MASK = 0xFFFF.inv()

    private const val TYPE_LAYER = 0
    private const val TYPE_TEXT_BUTTON = 1
    private const val TYPE_INVENTORY = 2
    private const val TYPE_RECTANGLE = 3
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5
    private const val TYPE_MODEL = 6
    private const val TYPE_TEXT_INVENTORY = 7
    private const val TYPE_IF1_TEXT = 8
    private const val TYPE_LINE = 9

    private const val MENU_OK = 1
    private const val MENU_TARGET = 2
    private const val MENU_SELECT = 4
    private const val MENU_TOGGLE = 5
    private const val MENU_CONTINUE = 6

    /** Inventory item-sprite slots (IF1 type 2): one optional sprite per slot. Source: `InterfaceLoader.decodeIf1`. */
    private const val INVENTORY_SPRITE_SLOTS = 20

    /** Slot action strings on IF1 inventory components. Source: `InterfaceLoader.decodeIf1`. */
    private const val CONFIG_ACTION_COUNT = 5

    /** IF1 target flags occupy the low 6 bits of a u16, stored at `shl 11` in the click mask. */
    private const val IF1_TARGET_FLAG_MASK = 63
    private const val IF1_TARGET_FLAG_SHIFT = 11

    /** Hook argument tags: 0 = i32, 1 = string. Source: `InterfaceLoader.decodeListener`. */
    private const val HOOK_ARG_INT = 0
    private const val HOOK_ARG_STRING = 1

    /** Number of hook lists then trigger lists in an IF3 file: 18 listeners, 3 transmit trigger lists. */
    private const val IF3_LISTENER_COUNT = 18
    private const val IF3_TRIGGER_LIST_COUNT = 3

    fun decode(interfaceId: Int, componentId: Int, buf: ReadBuf): ComponentType {
        val c = ComponentType(interfaceId, componentId)
        if (buf.peek() == -1) {
            buf.g1()
            decodeIf3(c, buf)
        } else {
            decodeIf1(c, buf)
        }
        return c
    }

    private fun readParent(c: ComponentType, buf: ReadBuf) {
        val raw = buf.g2()
        c.parentId = if (raw == NULL_U16) -1 else raw + (c.packedId and COMPONENT_MASK)
    }

    private fun decodeIf3(c: ComponentType, buf: ReadBuf) {
        c.isIf3 = true
        c.type = buf.g1()
        c.contentType = buf.g2()
        c.x = buf.g2s()
        c.y = buf.g2s()
        c.width = buf.g2()
        c.height = if (c.type == TYPE_LINE) buf.g2s() else buf.g2()
        c.widthMode = buf.g1s()
        c.heightMode = buf.g1s()
        c.xPositionMode = buf.g1s()
        c.yPositionMode = buf.g1s()
        readParent(c, buf)
        c.isHidden = buf.g1() == 1
        decodeIf3TypeFields(c, buf)
        c.clickMask = buf.g3()
        c.name = buf.gjstr()
        val actionCount = buf.g1()
        c.actions = Array(actionCount) { buf.gjstr() }
        c.dragDeadZone = buf.g1()
        c.dragDeadTime = buf.g1()
        buf.g1() // drag render behaviour
        c.targetVerb = buf.gjstr()
        repeat(IF3_LISTENER_COUNT) { skipHook(c, buf) }
        repeat(IF3_TRIGGER_LIST_COUNT) { buf.skip(buf.g1() * Int.SIZE_BYTES) }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun decodeIf3TypeFields(c: ComponentType, buf: ReadBuf) {
        when (c.type) {
            TYPE_LAYER -> {
                buf.skip(SCROLL_SIZE_BYTES) // scroll width + height
                buf.g1() // no-click-through
            }

            TYPE_GRAPHIC -> {
                buf.skip(IF3_GRAPHIC_BYTES)
            }

            TYPE_MODEL -> {
                buf.skip(IF3_MODEL_BYTES)
                if (c.widthMode != 0 || c.heightMode != 0) buf.skip(IF3_MODEL_SIZE_OVERRIDE_BYTES)
            }

            TYPE_TEXT -> {
                buf.g2() // font
                c.text = buf.gjstr()
                buf.skip(IF3_TEXT_STYLE_BYTES)
            }

            TYPE_RECTANGLE -> {
                buf.skip(IF3_RECTANGLE_BYTES)
            }

            TYPE_LINE -> {
                buf.skip(IF3_LINE_BYTES)
            }
        }
    }

    /** layer scroll width + height (u16 each). */
    private const val SCROLL_SIZE_BYTES = 4

    /** graphic: sprite i32, texture u16, tiling u8, opacity u8, border u8, shadow i32, flip v u8, flip h u8. */
    private const val IF3_GRAPHIC_BYTES = 4 + 2 + 1 + 1 + 1 + 4 + 1 + 1

    /** model: model i32, offset x/y i16 x2, rotation x/z/y u16 x3, zoom u16, animation u16, orthogonal u8, unknown u16. */
    private const val IF3_MODEL_BYTES = 4 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 1 + 2

    /**
     * Model size overrides: two u16 whenever either size mode is non-zero. RuneLite reads the first only when widthMode != 0
     * and the second only when heightMode != 0, which leaves 2 bytes unread on the 4 cache-2757 components with
     * widthMode 1 and heightMode 0 (interface 19 component 19 among them). Reading both whenever either mode is set
     * consumes every one of the 4,300+ type-6 files exactly; (widthMode 0, heightMode != 0) never occurs in the cache.
     */
    private const val IF3_MODEL_SIZE_OVERRIDE_BYTES = 2 + 2

    /** text after the string: line height u8, align x u8, align y u8, shadow u8, colour i32. */
    private const val IF3_TEXT_STYLE_BYTES = 1 + 1 + 1 + 1 + 4

    /** rectangle: colour i32, filled u8, opacity u8. */
    private const val IF3_RECTANGLE_BYTES = 4 + 1 + 1

    /** line: width u8, colour i32, direction u8. */
    private const val IF3_LINE_BYTES = 1 + 4 + 1

    /** Consumes one hook: u8 argument count, then per argument a u8 tag and an i32 or string payload. */
    private fun skipHook(c: ComponentType, buf: ReadBuf) {
        repeat(buf.g1()) {
            when (val tag = buf.g1()) {
                HOOK_ARG_INT -> buf.g4()
                HOOK_ARG_STRING -> buf.gjstr()
                else -> throw IOException("component ${c.packedId}: unknown hook argument tag $tag at offset ${buf.position - 1}")
            }
        }
    }

    private fun decodeIf1(c: ComponentType, buf: ReadBuf) {
        c.isIf3 = false
        c.type = buf.g1()
        c.menuType = buf.g1()
        c.contentType = buf.g2()
        c.x = buf.g2s()
        c.y = buf.g2s()
        c.width = buf.g2()
        c.height = buf.g2()
        buf.g1() // opacity
        readParent(c, buf)
        val hovered = buf.g2()
        c.hoveredSiblingId = if (hovered == NULL_U16) -1 else hovered
        buf.skip(buf.g1() * 3) // alternate-state comparisons: u8 operator + u16 value each
        repeat(buf.g1()) { buf.skip(buf.g2() * 2) } // clientscript1 programs: u16 length, u16 words
        decodeIf1TypeFields(c, buf)
        if (c.menuType == MENU_TARGET || c.type == TYPE_INVENTORY) {
            c.targetVerb = buf.gjstr()
            c.spellName = buf.gjstr()
            c.clickMask = c.clickMask or ((buf.g2() and IF1_TARGET_FLAG_MASK) shl IF1_TARGET_FLAG_SHIFT)
        }
        if (c.menuType in TOOLTIP_MENU_TYPES) {
            c.tooltip = buf.gjstr().ifEmpty { defaultTooltip(c.menuType) }
        }
    }

    /** Client fallbacks for an empty IF1 tooltip. Source: `InterfaceLoader.decodeIf1`. */
    private fun defaultTooltip(menuType: Int): String =
        when (menuType) {
            MENU_OK -> "Ok"
            MENU_CONTINUE -> "Continue"
            else -> "Select"
        }

    private fun decodeIf1TypeFields(c: ComponentType, buf: ReadBuf) {
        when (c.type) {
            TYPE_LAYER -> {
                buf.g2() // scroll height
                c.isHidden = buf.g1() == 1
            }

            TYPE_TEXT_BUTTON -> {
                buf.g2()
                buf.g1()
                decodeIf1Text(c, buf)
            }

            TYPE_INVENTORY -> {
                buf.skip(INVENTORY_FLAG_BYTES) // 4 u8 flags: swap, replace, delete (client-side drag), use
                c.xPitch = buf.g1()
                c.yPitch = buf.g1()
                repeat(INVENTORY_SPRITE_SLOTS) { if (buf.g1() == 1) buf.skip(INVENTORY_SPRITE_BYTES) }
                c.configActions = Array(CONFIG_ACTION_COUNT) { buf.gjstr() }
            }

            TYPE_RECTANGLE -> {
                buf.g1() // filled
                buf.g4() // colour
                buf.skip(IF1_HOVER_COLOUR_BYTES)
            }

            TYPE_TEXT -> {
                decodeIf1Text(c, buf)
                c.text = buf.gjstr()
                buf.gjstr() // alternate text
                buf.skip(IF1_TEXT_COLOUR_BYTES)
            }

            TYPE_GRAPHIC -> {
                buf.skip(IF1_GRAPHIC_BYTES)
            }

            TYPE_MODEL -> {
                buf.skip(IF1_MODEL_BYTES)
            }

            TYPE_TEXT_INVENTORY -> {
                buf.g1() // text alignment
                buf.g2() // font
                buf.g1() // shadowed
                buf.g4() // colour
                c.xPitch = buf.g2s()
                c.yPitch = buf.g2s()
                buf.g1() // flag
                c.configActions = Array(CONFIG_ACTION_COUNT) { buf.gjstr() }
            }

            TYPE_IF1_TEXT -> {
                c.text = buf.gjstr()
            }
        }
    }

    /** Text style shared by IF1 types 1 and 4: align x/y u8, line height u8, font u16, shadow u8. */
    private fun decodeIf1Text(c: ComponentType, buf: ReadBuf) {
        buf.skip(IF1_TEXT_STYLE_BYTES)
        if (c.type == TYPE_TEXT_BUTTON) buf.g4() // colour (type 1 carries only the first colour)
    }

    /** IF1 menu types that carry a tooltip string. Source: `InterfaceLoader.decodeIf1`. */
    private val TOOLTIP_MENU_TYPES: Set<Int> = setOf(MENU_OK, MENU_SELECT, MENU_TOGGLE, MENU_CONTINUE)

    /** Inventory slot flags: swap, replace, delete, use (u8 each). */
    private const val INVENTORY_FLAG_BYTES = 4

    /** Optional inventory sprite: x i16, y i16, sprite i32. */
    private const val INVENTORY_SPRITE_BYTES = 2 + 2 + 4

    /** IF1 text style: align x u8, align y u8, line height u8, font u16, shadow u8. */
    private const val IF1_TEXT_STYLE_BYTES = 1 + 1 + 1 + 2 + 1

    /** IF1 rectangle: colour2, hover colour 1, hover colour 2 (i32 each) after the first colour. */
    private const val IF1_HOVER_COLOUR_BYTES = 4 + 4 + 4

    /** IF1 text: colour, then colour2 + hover colour 1 + hover colour 2 (i32 each). */
    private const val IF1_TEXT_COLOUR_BYTES = 4 + 4 + 4 + 4

    /** IF1 graphic: sprite i32, alternate sprite i32. */
    private const val IF1_GRAPHIC_BYTES = 4 + 4

    /** IF1 model: model i32, alternate model i32, animation u16, alternate animation u16, zoom/rotation x/rotation z u16 x3. */
    private const val IF1_MODEL_BYTES = 4 + 4 + 2 + 2 + 2 + 2 + 2
}
