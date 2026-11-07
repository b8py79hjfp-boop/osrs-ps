package tp.tools.cachepack

import tp.util.buf.ReadBuf
import java.io.IOException

/**
 * Lossless model of one IF3 interface component (cache archive 3, group = interface id, file = component id).
 *
 * `tp.cache.interfaces.ComponentTypeDecoder` is the read-only view the server uses and drops sprites, fonts, colours and
 * hooks; this class keeps every byte so [If3Codec] can both decode and encode, and the pack tool can prove the encoder
 * against every IF3 component in the cache. Field order and widths: RuneLite 1.13.6 `InterfaceLoader.decodeIf3`
 * (`rev237` branch), with the model size override rule from `ComponentTypeDecoder` (both u16 whenever either size mode is set).
 */
internal class If3Component(var type: Int) {
    var contentType: Int = 0
    var x: Int = 0
    var y: Int = 0
    var width: Int = 0
    var height: Int = 0
    var widthMode: Int = 0
    var heightMode: Int = 0
    var xMode: Int = 0
    var yMode: Int = 0

    /** Component id of the parent layer in the same interface, or [NO_PARENT]. */
    var parent: Int = NO_PARENT
    var hidden: Boolean = false

    // layer
    var scrollWidth: Int = 0
    var scrollHeight: Int = 0
    var noClickThrough: Boolean = false

    // graphic
    var sprite: Int = -1
    var texture: Int = 0
    var tiling: Boolean = false
    var opacity: Int = 0
    var border: Int = 0
    var shadowColor: Int = 0
    var flipVertical: Boolean = false
    var flipHorizontal: Boolean = false

    // model
    var model: Int = -1
    var offsetX: Int = 0
    var offsetY: Int = 0
    var rotationX: Int = 0
    var rotationZ: Int = 0
    var rotationY: Int = 0
    var zoom: Int = 0
    var animation: Int = -1
    var orthogonal: Boolean = false
    var modelUnknown: Int = 0
    var modelSizeOverrides: IntArray = IntArray(MODEL_OVERRIDES)

    // text
    var font: Int = -1
    var text: String = ""
    var lineHeight: Int = 0
    var xAlign: Int = 0
    var yAlign: Int = 0
    var shadow: Boolean = false

    /** Text colour, rectangle colour and line colour (one field in the file layout of each type). */
    var color: Int = 0

    // rectangle
    var filled: Boolean = false

    // line
    var lineWidth: Int = 1
    var lineDirection: Boolean = false

    var clickMask: Int = 0
    var name: String = ""
    var actions: List<String> = emptyList()
    var dragDeadZone: Int = 0
    var dragDeadTime: Int = 0
    var dragRender: Boolean = false
    var targetVerb: String = ""

    /** The 18 listeners in file order ([HOOK_NAMES]); each is `[scriptId, args...]` of Int / String, or empty. */
    var hooks: Array<List<Any>> = Array(HOOK_NAMES.size) { emptyList() }

    /** The three transmit trigger lists in file order ([TRIGGER_NAMES]): var, inv, stat. */
    var triggers: Array<IntArray> = Array(TRIGGER_NAMES.size) { IntArray(0) }

    companion object {
        const val NO_PARENT = -1
        const val TYPE_LAYER = 0
        const val TYPE_RECTANGLE = 3
        const val TYPE_TEXT = 4
        const val TYPE_GRAPHIC = 5
        const val TYPE_MODEL = 6
        const val TYPE_LINE = 9
        const val MODEL_OVERRIDES = 2

        /** Listener order in the file; `InterfaceLoader.decodeIf3`. */
        val HOOK_NAMES: List<String> =
            listOf(
                "on_load",
                "on_mouse_over",
                "on_mouse_leave",
                "on_target_leave",
                "on_target_enter",
                "on_var_transmit",
                "on_inv_transmit",
                "on_stat_transmit",
                "on_timer",
                "on_op",
                "on_mouse_repeat",
                "on_click",
                "on_click_repeat",
                "on_release",
                "on_hold",
                "on_drag",
                "on_drag_complete",
                "on_scroll_wheel",
            )

        /** Transmit trigger list order in the file. */
        val TRIGGER_NAMES: List<String> = listOf("var_triggers", "inv_triggers", "stat_triggers")
    }
}

internal object If3Codec {
    /** First byte of an IF3 component file. */
    private const val IF3_MARKER = 0xFF
    private const val NULL_U16 = 0xFFFF
    private const val NULL_FONT_OR_ANIM = 0xFFFF
    private const val HOOK_ARG_INT = 0
    private const val HOOK_ARG_STRING = 1

    /** Decodes an IF3 file (leading 0xFF) or returns null when it is an IF1 file. */
    fun decode(data: ByteArray, packedBase: Int): If3Component? {
        if (data.isEmpty() || data[0].toInt() != -1) return null
        val buf = ReadBuf(data)
        buf.g1()
        val c = If3Component(buf.g1())
        c.contentType = buf.g2()
        c.x = buf.g2s()
        c.y = buf.g2s()
        c.width = buf.g2()
        c.height = if (c.type == If3Component.TYPE_LINE) buf.g2s() else buf.g2()
        c.widthMode = buf.g1s()
        c.heightMode = buf.g1s()
        c.xMode = buf.g1s()
        c.yMode = buf.g1s()
        val parent = buf.g2()
        c.parent = if (parent == NULL_U16) If3Component.NO_PARENT else parent
        c.hidden = buf.g1() == 1
        decodeTypeFields(c, buf)
        c.clickMask = buf.g3()
        c.name = buf.gjstr()
        c.actions = List(buf.g1()) { buf.gjstr() }
        c.dragDeadZone = buf.g1()
        c.dragDeadTime = buf.g1()
        c.dragRender = buf.g1() == 1
        c.targetVerb = buf.gjstr()
        c.hooks = Array(If3Component.HOOK_NAMES.size) { decodeHook(buf, packedBase) }
        c.triggers = Array(If3Component.TRIGGER_NAMES.size) { IntArray(buf.g1()) { buf.g4() } }
        if (buf.isReadable) throw IOException("${buf.remaining} unread bytes in IF3 component")
        return c
    }

    @Suppress("CyclomaticComplexMethod")
    private fun decodeTypeFields(c: If3Component, buf: ReadBuf) {
        when (c.type) {
            If3Component.TYPE_LAYER -> {
                c.scrollWidth = buf.g2()
                c.scrollHeight = buf.g2()
                c.noClickThrough = buf.g1() == 1
            }

            If3Component.TYPE_GRAPHIC -> {
                c.sprite = buf.g4()
                c.texture = buf.g2()
                c.tiling = buf.g1() == 1
                c.opacity = buf.g1()
                c.border = buf.g1()
                c.shadowColor = buf.g4()
                c.flipVertical = buf.g1() == 1
                c.flipHorizontal = buf.g1() == 1
            }

            If3Component.TYPE_MODEL -> {
                c.model = buf.g4()
                c.offsetX = buf.g2s()
                c.offsetY = buf.g2s()
                c.rotationX = buf.g2()
                c.rotationZ = buf.g2()
                c.rotationY = buf.g2()
                c.zoom = buf.g2()
                val anim = buf.g2()
                c.animation = if (anim == NULL_FONT_OR_ANIM) -1 else anim
                c.orthogonal = buf.g1() == 1
                c.modelUnknown = buf.g2()
                if (c.widthMode != 0 || c.heightMode != 0) c.modelSizeOverrides = intArrayOf(buf.g2(), buf.g2())
            }

            If3Component.TYPE_TEXT -> {
                val font = buf.g2()
                c.font = if (font == NULL_FONT_OR_ANIM) -1 else font
                c.text = buf.gjstr()
                c.lineHeight = buf.g1()
                c.xAlign = buf.g1()
                c.yAlign = buf.g1()
                c.shadow = buf.g1() == 1
                c.color = buf.g4()
            }

            If3Component.TYPE_RECTANGLE -> {
                c.color = buf.g4()
                c.filled = buf.g1() == 1
                c.opacity = buf.g1()
            }

            If3Component.TYPE_LINE -> {
                c.lineWidth = buf.g1()
                c.color = buf.g4()
                c.lineDirection = buf.g1() == 1
            }

            else -> {
                throw IOException("IF3 component type ${c.type} is not supported")
            }
        }
    }

    private fun decodeHook(buf: ReadBuf, packedBase: Int): List<Any> =
        List(buf.g1()) {
            when (val tag = buf.g1()) {
                HOOK_ARG_INT -> buf.g4()
                HOOK_ARG_STRING -> buf.gjstr()
                else -> throw IOException("component $packedBase: unknown hook argument tag $tag")
            }
        }

    fun encode(c: If3Component): ByteArray {
        val out = WriteBuf()
        out
            .p1(IF3_MARKER)
            .p1(c.type)
            .p2(c.contentType)
            .p2(c.x)
            .p2(c.y)
            .p2(c.width)
        out.p2(c.height)
        out
            .p1(c.widthMode)
            .p1(c.heightMode)
            .p1(c.xMode)
            .p1(c.yMode)
        out.p2(if (c.parent == If3Component.NO_PARENT) NULL_U16 else c.parent)
        out.p1(if (c.hidden) 1 else 0)
        encodeTypeFields(c, out)
        out.p3(c.clickMask)
        out.pjstr(c.name)
        out.p1(c.actions.size)
        c.actions.forEach { out.pjstr(it) }
        out.p1(c.dragDeadZone).p1(c.dragDeadTime).p1(if (c.dragRender) 1 else 0)
        out.pjstr(c.targetVerb)
        require(c.hooks.size == If3Component.HOOK_NAMES.size)
        c.hooks.forEach { hook ->
            out.p1(hook.size)
            for (arg in hook) {
                when (arg) {
                    is Int -> out.p1(HOOK_ARG_INT).p4(arg)
                    is String -> out.p1(HOOK_ARG_STRING).pjstr(arg)
                    else -> throw IllegalStateException("hook argument $arg")
                }
            }
        }
        c.triggers.forEach { list ->
            out.p1(list.size)
            list.forEach { out.p4(it) }
        }
        return out.toByteArray()
    }

    @Suppress("CyclomaticComplexMethod")
    private fun encodeTypeFields(c: If3Component, out: WriteBuf) {
        when (c.type) {
            If3Component.TYPE_LAYER -> {
                out.p2(c.scrollWidth).p2(c.scrollHeight).p1(if (c.noClickThrough) 1 else 0)
            }

            If3Component.TYPE_GRAPHIC -> {
                out
                    .p4(c.sprite)
                    .p2(c.texture)
                    .p1(if (c.tiling) 1 else 0)
                    .p1(c.opacity)
                    .p1(c.border)
                    .p4(c.shadowColor)
                out.p1(if (c.flipVertical) 1 else 0).p1(if (c.flipHorizontal) 1 else 0)
            }

            If3Component.TYPE_MODEL -> {
                out
                    .p4(c.model)
                    .p2(c.offsetX)
                    .p2(c.offsetY)
                    .p2(c.rotationX)
                    .p2(c.rotationZ)
                    .p2(c.rotationY)
                    .p2(c.zoom)
                out.p2(if (c.animation < 0) NULL_FONT_OR_ANIM else c.animation)
                out.p1(if (c.orthogonal) 1 else 0).p2(c.modelUnknown)
                if (c.widthMode != 0 || c.heightMode != 0) c.modelSizeOverrides.forEach { out.p2(it) }
            }

            If3Component.TYPE_TEXT -> {
                out.p2(if (c.font < 0) NULL_FONT_OR_ANIM else c.font).pjstr(c.text)
                out
                    .p1(c.lineHeight)
                    .p1(c.xAlign)
                    .p1(c.yAlign)
                    .p1(if (c.shadow) 1 else 0)
                    .p4(c.color)
            }

            If3Component.TYPE_RECTANGLE -> {
                out.p4(c.color).p1(if (c.filled) 1 else 0).p1(c.opacity)
            }

            If3Component.TYPE_LINE -> {
                out.p1(c.lineWidth).p4(c.color).p1(if (c.lineDirection) 1 else 0)
            }

            else -> {
                throw IllegalArgumentException("IF3 component type ${c.type} is not supported")
            }
        }
    }
}
