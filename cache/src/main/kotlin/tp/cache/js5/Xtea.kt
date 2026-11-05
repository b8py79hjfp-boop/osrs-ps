package tp.cache.js5

/** A 128-bit XTEA key as used for map loc groups. Cache 2757 ships 0 keys, but support stays (brief §1). */
@JvmInline
value class XteaKey private constructor(private val k: IntArray) {
    val isZero: Boolean get() = k.all { it == 0 }

    operator fun get(i: Int): Int = k[i]

    companion object {
        private const val WORDS = 4
        val ZERO: XteaKey = XteaKey(IntArray(WORDS))

        fun of(words: IntArray): XteaKey {
            require(words.size == WORDS) { "XTEA key must have $WORDS words" }
            return XteaKey(words.copyOf())
        }
    }
}

/** Standard 32-round XTEA, big-endian blocks. Bytes past the last whole 8-byte block are left untouched. */
internal object Xtea {
    /** XTEA key schedule constant (golden ratio). */
    private const val DELTA = -0x61c88647
    private const val ROUNDS = 32
    private const val BLOCK = 8

    /** Feistel mixing shifts and the key-index shift from the XTEA reference algorithm (Needham & Wheeler, 1997). */
    private const val MIX_LEFT = 4
    private const val MIX_RIGHT = 5
    private const val KEY_SHIFT = 11

    fun decipher(data: ByteArray, start: Int, end: Int, key: XteaKey) {
        val blocks = (end - start) / BLOCK
        for (b in 0 until blocks) {
            val off = start + b * BLOCK
            var v0 = readInt(data, off)
            var v1 = readInt(data, off + 4)
            var sum = DELTA * ROUNDS
            repeat(ROUNDS) {
                v1 -= (((v0 shl MIX_LEFT) xor (v0 ushr MIX_RIGHT)) + v0) xor (sum + key[(sum ushr KEY_SHIFT) and 3])
                sum -= DELTA
                v0 -= (((v1 shl MIX_LEFT) xor (v1 ushr MIX_RIGHT)) + v1) xor (sum + key[sum and 3])
            }
            writeInt(data, off, v0)
            writeInt(data, off + 4, v1)
        }
    }

    private fun readInt(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun writeInt(b: ByteArray, o: Int, v: Int) {
        b[o] = (v ushr 24).toByte()
        b[o + 1] = (v ushr 16).toByte()
        b[o + 2] = (v ushr 8).toByte()
        b[o + 3] = v.toByte()
    }
}
