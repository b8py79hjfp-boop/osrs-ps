package tp.cache.js5

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.SequenceInputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream

/**
 * JS5 container codec.
 *
 * Layout: u8 compression, i32 compressed length, [i32 uncompressed length if compressed], payload,
 * optional u16 version trailer. XTEA, when keyed, covers every byte after the 5-byte header.
 * Reference: OpenRS2 `Js5Compression` (https://github.com/openrs2/openrs2, cache/Js5Compression.kt).
 */
object Js5Container {
    const val NONE: Int = 0
    const val BZIP2: Int = 1
    const val GZIP: Int = 2

    private const val HEADER = 5
    private const val LENGTH_FIELD = 4
    private const val U8 = 0xFF

    /** The bzip2 stream header ("BZh" + block size 1) that JS5 strips from its payloads. */
    private val BZIP2_HEADER = byteArrayOf('B'.code.toByte(), 'Z'.code.toByte(), 'h'.code.toByte(), '1'.code.toByte())

    /** Length of the container proper (excluding any version trailer). */
    fun containerLength(raw: ByteArray): Int {
        if (raw.size < HEADER) throw IOException("Container too short: ${raw.size}")
        val buf = ByteBuffer.wrap(raw)
        val compression = buf.get().toInt() and U8
        val length = buf.getInt()
        val total = HEADER + length + if (compression == NONE) 0 else LENGTH_FIELD
        if (length < 0 || total > raw.size) throw IOException("Container length $length exceeds ${raw.size} bytes")
        return total
    }

    /** CRC-32 of the container as the client checks it (header + payload, no version trailer). */
    fun crc(raw: ByteArray): Int {
        val crc = CRC32()
        crc.update(raw, 0, containerLength(raw))
        return crc.value.toInt()
    }

    /** Decompresses [raw], first deciphering it with [key] when the key is non-zero. */
    fun decode(raw: ByteArray, key: XteaKey = XteaKey.ZERO): ByteArray {
        val total = containerLength(raw)
        val bytes = if (key.isZero) raw else raw.copyOf().also { Xtea.decipher(it, HEADER, total, key) }
        val buf = ByteBuffer.wrap(bytes, 0, total)
        val compression = buf.get().toInt() and U8
        val length = buf.getInt()
        if (compression == NONE) return bytes.copyOfRange(HEADER, HEADER + length)

        val uncompressedLength = buf.getInt()
        if (uncompressedLength < 0) throw IOException("Negative uncompressed length (wrong XTEA key?)")
        val payload = ByteArrayInputStream(bytes, HEADER + LENGTH_FIELD, length)
        val stream =
            when (compression) {
                BZIP2 -> BZip2CompressorInputStream(SequenceInputStream(ByteArrayInputStream(BZIP2_HEADER), payload))
                GZIP -> GZIPInputStream(payload)
                else -> throw IOException("Unsupported compression type $compression")
            }
        val out = stream.use { it.readNBytes(uncompressedLength) }
        if (out.size != uncompressedLength) throw IOException("Decompressed ${out.size} of $uncompressedLength bytes")
        return out
    }
}
