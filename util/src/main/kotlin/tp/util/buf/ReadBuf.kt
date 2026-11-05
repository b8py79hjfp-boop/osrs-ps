package tp.util.buf

/**
 * Big-endian read cursor over a [ByteArray]. Not thread-safe; never copies.
 *
 * Encodings follow the client's `Packet` reads as mirrored by RuneLite
 * (`cache/src/main/java/net/runelite/cache/io/InputStream.java`) and RSProt's `JagByteBuf`.
 * Names: `g1` = unsigned byte, `g2` = unsigned short, `g4` = int (Jagex naming kept, it matches every reference).
 */
class ReadBuf(
    val array: ByteArray,
    var position: Int = 0,
    val limit: Int = array.size,
) {
    val remaining: Int get() = limit - position
    val isReadable: Boolean get() = position < limit

    fun peek(): Int = array[position].toInt()

    fun skip(n: Int) {
        position += n
    }

    fun g1(): Int = array[position++].toInt() and 0xFF

    fun g1s(): Int = array[position++].toInt()

    fun g2(): Int = (g1() shl 8) or g1()

    fun g2s(): Int = g2().toShort().toInt()

    fun g3(): Int = (g1() shl 16) or (g1() shl 8) or g1()

    /** Signed 24-bit. */
    fun g3s(): Int = (g3() shl 8) shr 8

    fun g4(): Int = (g1() shl 24) or (g1() shl 16) or (g1() shl 8) or g1()

    fun g8(): Long = (g4().toLong() shl Int.SIZE_BITS) or (g4().toLong() and LOW_32_MASK)

    /** Unsigned smart: 1 byte (0..127) or 2 bytes minus 0x8000 (0..32767). */
    fun gSmart1or2(): Int = if ((peek() and 0xFF) < SMART_THRESHOLD) g1() else g2() - SMART_U2_BIAS

    /** Signed smart: 1 byte minus 64 (-64..63) or 2 bytes minus 0xC000 (-16384..16383). */
    fun gSmart1or2s(): Int = if ((peek() and 0xFF) < SMART_THRESHOLD) g1() - SMART_S1_BIAS else g2() - SMART_S2_BIAS

    /** Unsigned smart minus one ("smart1or2null"): -1..32766. */
    fun gSmart1or2Null(): Int = if ((peek() and 0xFF) < SMART_THRESHOLD) g1() - 1 else g2() - SMART_U2_NULL_BIAS

    /** Repeated unsigned smarts summed while the value is 32767 ("IntSmartShortCompat", map loc ids). */
    fun gExtended1or2(): Int {
        var total = 0
        var value = gSmart1or2()
        while (value == SMART_U2_MAX) {
            total += SMART_U2_MAX
            value = gSmart1or2()
        }
        return total + value
    }

    /** Big smart: u16 when the top bit is clear, else i32 with the top bit masked off. */
    fun gSmart2or4(): Int = if (peek() >= 0) g2() else g4() and Int.MAX_VALUE

    /** Big smart with null: like [gSmart2or4] but a 2-byte 32767 decodes as -1. */
    fun gSmart2or4Null(): Int {
        if (peek() < 0) return g4() and Int.MAX_VALUE
        val value = g2()
        return if (value == SMART_U2_MAX) -1 else value
    }

    /** Little-endian base-128 varint: 7 bits per byte, low group first, top bit = continue (RuneLite `readVarInt2`). */
    fun gVarInt(): Int {
        var value = 0
        var shift = 0
        var read: Int
        do {
            read = g1()
            value = value or ((read and VARINT_PAYLOAD_MASK) shl shift)
            shift += VARINT_PAYLOAD_BITS
        } while (read > VARINT_PAYLOAD_MASK)
        return value
    }

    /** NUL-terminated CP-1252 string ("jstr"). Undefined 0x80..0x9F code points decode as '?', as the client does. */
    fun gjstr(): String {
        val start = position
        while (array[position] != 0.toByte()) position++
        val end = position
        position++ // NUL
        val chars = CharArray(end - start)
        for (i in chars.indices) chars[i] = cp1252(array[start + i].toInt() and 0xFF)
        return String(chars)
    }

    /** "jstr2": a version byte that must be 0, then a [gjstr]. */
    fun gjstr2(): String {
        val version = g1()
        check(version == 0) { "Invalid jstr2 version $version at ${position - 1}" }
        return gjstr()
    }

    fun gArray(n: Int): ByteArray {
        val out = array.copyOfRange(position, position + n)
        position += n
        return out
    }

    companion object {
        /** Mask keeping the low 32 bits of a sign-extended int. */
        private const val LOW_32_MASK = 0xFFFF_FFFFL
        private const val VARINT_PAYLOAD_MASK = 0x7F
        private const val VARINT_PAYLOAD_BITS = 7
        private const val SMART_THRESHOLD = 128
        private const val SMART_U2_BIAS = 0x8000
        private const val SMART_U2_NULL_BIAS = 0x8001
        private const val SMART_S1_BIAS = 64
        private const val SMART_S2_BIAS = 0xC000
        private const val SMART_U2_MAX = 32767
        private const val CP1252_EXTENDED_START = 0x80
        private const val CP1252_EXTENDED_END = 0xA0

        /** Windows-1252 0x80..0x9F, with the 5 undefined slots as '?' (client behaviour, RuneLite `InputStream.CHARACTERS`). */
        private val CP1252_EXTENDED =
            charArrayOf(
                '€',
                '?',
                '‚',
                'ƒ',
                '„',
                '…',
                '†',
                '‡',
                'ˆ',
                '‰',
                'Š',
                '‹',
                'Œ',
                '?',
                'Ž',
                '?',
                '?',
                '‘',
                '’',
                '“',
                '”',
                '•',
                '–',
                '—',
                '˜',
                '™',
                'š',
                '›',
                'œ',
                '?',
                'ž',
                'Ÿ',
            )

        private fun cp1252(b: Int): Char =
            if (b in CP1252_EXTENDED_START until CP1252_EXTENDED_END) CP1252_EXTENDED[b - CP1252_EXTENDED_START] else b.toChar()
    }
}
