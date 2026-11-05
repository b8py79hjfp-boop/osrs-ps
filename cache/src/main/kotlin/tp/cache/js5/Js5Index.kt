package tp.cache.js5

import java.io.IOException
import java.nio.ByteBuffer

/**
 * A decoded JS5 archive index (the group table for one archive, stored as group `255/<archive>`).
 *
 * Field order follows OpenRS2 `Js5Index.read` (https://github.com/openrs2/openrs2, cache/Js5Index.kt):
 * protocol, [version], flags, group count, delta-coded group ids, [name hashes], checksums,
 * [uncompressed checksums], [digests], [lengths], versions, file counts, delta-coded file ids, [file name hashes].
 */
class Js5Index(
    val protocol: Int,
    val version: Int,
    val groupIds: IntArray,
    val groupChecksums: IntArray,
    val groupVersions: IntArray,
    val fileCounts: IntArray,
) {
    val groupCount: Int get() = groupIds.size

    companion object {
        private const val PROTOCOL_ORIGINAL = 5
        private const val PROTOCOL_VERSIONED = 6
        private const val PROTOCOL_SMART = 7

        private const val FLAG_NAMED = 0x1
        private const val FLAG_DIGESTS = 0x2
        private const val FLAG_LENGTHS = 0x4
        private const val FLAG_UNCOMPRESSED_CHECKSUMS = 0x8

        /** Whirlpool digest size per group. */
        private const val DIGEST_BYTES = 64

        /** Two i32s per group: compressed and uncompressed length. */
        private const val LENGTH_BYTES = 8
        private const val U8 = 0xFF
        private const val U16 = 0xFFFF

        fun decode(data: ByteArray): Js5Index {
            val buf = ByteBuffer.wrap(data)
            val protocol = buf.get().toInt() and U8
            if (protocol !in PROTOCOL_ORIGINAL..PROTOCOL_SMART) {
                throw IOException("Unsupported index protocol $protocol")
            }
            val version = if (protocol >= PROTOCOL_VERSIONED) buf.getInt() else 0
            val flags = buf.get().toInt() and U8
            val smart = protocol >= PROTOCOL_SMART

            fun count(): Int = if (smart) buf.getBigSmart() else buf.getShort().toInt() and U16

            val size = count()
            val groupIds = IntArray(size)
            var prev = 0
            for (i in 0 until size) {
                prev += count()
                groupIds[i] = prev
            }
            if (flags and FLAG_NAMED != 0) buf.skip(size * Int.SIZE_BYTES)
            val checksums = IntArray(size) { buf.getInt() }
            if (flags and FLAG_UNCOMPRESSED_CHECKSUMS != 0) buf.skip(size * Int.SIZE_BYTES)
            if (flags and FLAG_DIGESTS != 0) buf.skip(size * DIGEST_BYTES)
            if (flags and FLAG_LENGTHS != 0) buf.skip(size * LENGTH_BYTES)
            val versions = IntArray(size) { buf.getInt() }
            val fileCounts = IntArray(size) { count() }
            // File ids are delta-coded per group; Phase 1 keeps them (needed for multi-file groups).
            for (g in 0 until size) repeat(fileCounts[g]) { count() }
            if (flags and FLAG_NAMED != 0) buf.skip(fileCounts.sum() * Int.SIZE_BYTES)
            if (buf.hasRemaining()) throw IOException("${buf.remaining()} trailing bytes in index")
            return Js5Index(protocol, version, groupIds, checksums, versions, fileCounts)
        }

        private fun ByteBuffer.skip(n: Int) {
            position(position() + n)
        }

        /** Big smart: u16 when the top bit is clear, otherwise i32 with the top bit masked off. */
        private fun ByteBuffer.getBigSmart(): Int =
            if (get(position()) >= 0) getShort().toInt() and U16 else getInt() and Int.MAX_VALUE
    }
}
