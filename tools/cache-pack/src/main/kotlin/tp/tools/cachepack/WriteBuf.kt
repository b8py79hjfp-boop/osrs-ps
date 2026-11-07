package tp.tools.cachepack

import java.io.ByteArrayOutputStream

/**
 * Big-endian write cursor, the mirror of `tp.util.buf.ReadBuf` (same Jagex names: `p1` = byte, `p2` = short, `p4` = int).
 * Strings are CP-1252 and NUL-terminated.
 */
internal class WriteBuf {
    private val out = ByteArrayOutputStream()

    val size: Int get() = out.size()

    fun p1(v: Int): WriteBuf = apply { out.write(v) }

    fun p2(v: Int): WriteBuf =
        apply {
            out.write(v ushr 8)
            out.write(v)
        }

    fun p3(v: Int): WriteBuf =
        apply {
            out.write(v ushr 16)
            out.write(v ushr 8)
            out.write(v)
        }

    fun p4(v: Int): WriteBuf =
        apply {
            out.write(v ushr 24)
            out.write(v ushr 16)
            out.write(v ushr 8)
            out.write(v)
        }

    fun p8(v: Long): WriteBuf =
        apply {
            p4((v ushr Int.SIZE_BITS).toInt())
            p4(v.toInt())
        }

    fun pBytes(b: ByteArray): WriteBuf = apply { out.write(b) }

    /** NUL-terminated CP-1252 string. A character with no CP-1252 code point is an error: silently writing `?` hides typos. */
    fun pjstr(s: String): WriteBuf =
        apply {
            for (c in s) out.write(Cp1252.encode(c))
            out.write(0)
        }

    /** Big smart, the inverse of `ReadBuf.gSmart2or4`: a u16 below 0x8000, otherwise an i32 with the top bit set. */
    fun pSmart2or4(v: Int): WriteBuf =
        apply {
            if (v < SMART_2_LIMIT) p2(v) else p4(v or Int.MIN_VALUE)
        }

    fun toByteArray(): ByteArray = out.toByteArray()

    private companion object {
        /** `gSmart2or4` reads a u16 while the first byte is below 0x80, i.e. values up to 0x7FFF. */
        const val SMART_2_LIMIT = 0x8000
    }
}

/** CP-1252 code points for 0x80..0x9F (undefined slots omitted), the inverse of the table in `ReadBuf`. */
internal object Cp1252 {
    private const val EXTENDED_START = 0x80
    private const val EXTENDED_END = 0xA0
    private const val LATIN1_START = 0xA0
    private const val LATIN1_END = 0x100

    private val EXTENDED: Map<Char, Int> =
        "€\u0000‚ƒ„…†‡ˆ‰Š‹Œ\u0000Ž\u0000\u0000‘’“”•–—˜™š›œ\u0000žŸ"
            .withIndex()
            .filter { it.value != '\u0000' }
            .associate { it.value to (EXTENDED_START + it.index) }

    fun encode(c: Char): Int =
        when {
            c.code in 1 until EXTENDED_START -> c.code
            c.code in LATIN1_START until LATIN1_END -> c.code
            else -> EXTENDED[c] ?: throw IllegalArgumentException("Character '$c' (U+${c.code.toString(HEX)}) has no CP-1252 code")
        }

    private const val HEX = 16
}
