package tp.tools.cachepack

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * A sprite group (archive 8) as the client reads it (D-095; layout as RuneLite's `SpriteLoader`, BSD-2): the frames'
 * pixel blocks, then the shared palette (entry 0 transparent, at most 256), then a trailer of max width, max height,
 * palette length - 1, per-frame offsets and sizes, and the frame count. [decode] keeps each frame's block verbatim so
 * [encode] reproduces an unchanged group byte for byte; [append] adds frames (and palette colours) after them.
 */
internal class SpriteGroup private constructor(
    private var maxWidth: Int,
    private var maxHeight: Int,
    private val palette: MutableList<Int>,
    val frames: MutableList<Frame>,
) {
    class Frame(val offsetX: Int, val offsetY: Int, val width: Int, val height: Int, val block: ByteArray)

    /** Adds [image] as a new frame of the group's max size, transparent where alpha is 0; returns its index. */
    fun append(image: BufferedImage): Int {
        if (image.width > maxWidth || image.height > maxHeight) {
            throw IOException("sprite ${image.width}x${image.height} is larger than the group's ${maxWidth}x$maxHeight")
        }
        val out = ByteArrayOutputStream()
        out.write(0) // flags: horizontal, no alpha channel (binary transparency)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                if (argb ushr ALPHA_SHIFT == 0) {
                    out.write(0)
                } else {
                    // Palette colour 0 means transparent, so pure black is stored as 1 (as the client reads it).
                    val rgb = (argb and RGB_MASK).let { if (it == 0) 1 else it }
                    out.write(indexOf(rgb))
                }
            }
        }
        frames += Frame(0, 0, image.width, image.height, out.toByteArray())
        return frames.lastIndex
    }

    /**
     * The palette index for [rgb]: the exact colour, a new entry while the palette has room, else the nearest existing
     * colour (the official `mod_icons` palette is already full, so our icons are drawn in its colours).
     */
    private fun indexOf(rgb: Int): Int {
        val exact = palette.indexOf(rgb)
        if (exact >= 1) return exact
        if (palette.size < MAX_PALETTE) {
            palette += rgb
            return palette.lastIndex
        }
        return (1 until palette.size).minBy { distance(palette[it], rgb) }
    }

    private fun distance(a: Int, b: Int): Int {
        val dr = (a ushr BYTE2 and BYTE) - (b ushr BYTE2 and BYTE)
        val dg = (a ushr BYTE1 and BYTE) - (b ushr BYTE1 and BYTE)
        val db = (a and BYTE) - (b and BYTE)
        return dr * dr + dg * dg + db * db
    }

    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        for (f in frames) out.write(f.block)
        for (rgb in palette.drop(1)) {
            out.write(rgb ushr BYTE2 and BYTE)
            out.write(rgb ushr BYTE1 and BYTE)
            out.write(rgb and BYTE)
        }

        fun u16(v: Int) {
            out.write(v ushr BYTE1 and BYTE)
            out.write(v and BYTE)
        }
        u16(maxWidth)
        u16(maxHeight)
        out.write(palette.size - 1)
        frames.forEach { u16(it.offsetX) }
        frames.forEach { u16(it.offsetY) }
        frames.forEach { u16(it.width) }
        frames.forEach { u16(it.height) }
        u16(frames.size)
        return out.toByteArray()
    }

    companion object {
        const val FLAG_ALPHA = 0b10
        private const val MAX_PALETTE = 256
        private const val ALPHA_SHIFT = 24
        private const val RGB_MASK = 0xFFFFFF
        private const val BYTE = 0xFF
        private const val BYTE1 = 8
        private const val BYTE2 = 16
        private const val TRAILER_FIXED = 7
        private const val PER_FRAME = 8
        private const val RGB_BYTES = 3

        /** Trailer head: max width (2), max height (2), palette length - 1 (1). */
        private const val PALETTE_BYTE = 4
        private const val TRAILER_HEAD = 5

        fun decode(b: ByteArray): SpriteGroup {
            fun u16(at: Int) = (b[at].toInt() and BYTE shl BYTE1) or (b[at + 1].toInt() and BYTE)
            val count = u16(b.size - 2)
            var p = b.size - TRAILER_FIXED - count * PER_FRAME
            val maxW = u16(p)
            val maxH = u16(p + 2)
            val paletteLength = (b[p + PALETTE_BYTE].toInt() and BYTE) + 1
            p += TRAILER_HEAD
            val ox = IntArray(count) { u16(p + it * 2) }
            p += count * 2
            val oy = IntArray(count) { u16(p + it * 2) }
            p += count * 2
            val w = IntArray(count) { u16(p + it * 2) }
            p += count * 2
            val h = IntArray(count) { u16(p + it * 2) }
            val paletteAt = b.size - TRAILER_FIXED - count * PER_FRAME - (paletteLength - 1) * RGB_BYTES
            val palette = mutableListOf(0)
            for (i in 1 until paletteLength) {
                val q = paletteAt + (i - 1) * RGB_BYTES
                palette += (b[q].toInt() and BYTE shl BYTE2) or (b[q + 1].toInt() and BYTE shl BYTE1) or (b[q + 2].toInt() and BYTE)
            }
            val frames = ArrayList<Frame>()
            var at = 0
            for (i in 0 until count) {
                val flags = b[at].toInt() and BYTE
                val dimension = w[i] * h[i]
                val length = 1 + dimension * (if (flags and FLAG_ALPHA != 0) 2 else 1)
                frames += Frame(ox[i], oy[i], w[i], h[i], b.copyOfRange(at, at + length))
                at += length
            }
            if (at != paletteAt) throw IOException("sprite group: pixel data ends at $at, palette starts at $paletteAt")
            return SpriteGroup(maxW, maxH, palette, frames)
        }
    }
}
