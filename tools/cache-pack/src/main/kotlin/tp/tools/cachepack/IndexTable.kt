package tp.tools.cachepack

import tp.util.buf.ReadBuf
import java.io.IOException

/**
 * A JS5 archive index (reference table) that keeps every optional column, so it can be re-encoded without loss.
 * `tp.cache.js5.Js5Index` is the read-only view and drops the uncompressed checksums, digests and lengths.
 *
 * Layout: OpenRS2 `Js5Index` (https://github.com/openrs2/openrs2, cache/Js5Index.kt), the same one `Js5Index.decode` follows.
 */
internal class IndexTable(
    val protocol: Int,
    var version: Int,
    val flags: Int,
    /** Groups in ascending id order. */
    val groups: MutableList<Group>,
) {
    class Group(
        val id: Int,
        var nameHash: Int,
        var crc: Int,
        var uncompressedCrc: Int,
        var digest: ByteArray,
        var compressedLength: Int,
        var uncompressedLength: Int,
        var version: Int,
        var fileIds: IntArray,
        var fileNameHashes: IntArray,
    )

    val named: Boolean get() = flags and FLAG_NAMED != 0
    val hasUncompressedCrc: Boolean get() = flags and FLAG_UNCOMPRESSED_CHECKSUMS != 0
    val hasDigests: Boolean get() = flags and FLAG_DIGESTS != 0
    val hasLengths: Boolean get() = flags and FLAG_LENGTHS != 0

    fun group(id: Int): Group? = groups.firstOrNull { it.id == id }

    /** Highest group id in the archive, or -1 when empty. */
    val maxGroupId: Int get() = groups.maxOfOrNull { it.id } ?: -1

    @Suppress("CyclomaticComplexMethod") // one branch per optional column of the format
    fun encode(): ByteArray {
        val smart = protocol >= PROTOCOL_SMART
        val out = WriteBuf()

        fun count(v: Int) {
            if (smart) out.pSmart2or4(v) else out.p2(v)
        }
        out.p1(protocol)
        if (protocol >= PROTOCOL_VERSIONED) out.p4(version)
        out.p1(flags)
        count(groups.size)
        var prev = 0
        for (g in groups) {
            count(g.id - prev)
            prev = g.id
        }
        if (named) groups.forEach { out.p4(it.nameHash) }
        groups.forEach { out.p4(it.crc) }
        if (hasUncompressedCrc) groups.forEach { out.p4(it.uncompressedCrc) }
        if (hasDigests) groups.forEach { out.pBytes(it.digest) }
        if (hasLengths) {
            groups.forEach {
                out.p4(it.compressedLength)
                out.p4(it.uncompressedLength)
            }
        }
        groups.forEach { out.p4(it.version) }
        groups.forEach { count(it.fileIds.size) }
        for (g in groups) {
            var id = 0
            for (f in g.fileIds) {
                count(f - id)
                id = f
            }
        }
        if (named) groups.forEach { g -> g.fileNameHashes.forEach { out.p4(it) } }
        return out.toByteArray()
    }

    companion object {
        const val PROTOCOL_ORIGINAL = 5
        const val PROTOCOL_VERSIONED = 6
        const val PROTOCOL_SMART = 7
        const val FLAG_NAMED = 0x1
        const val FLAG_DIGESTS = 0x2
        const val FLAG_LENGTHS = 0x4
        const val FLAG_UNCOMPRESSED_CHECKSUMS = 0x8

        /** Whirlpool digest size per group. */
        const val DIGEST_BYTES = 64

        fun decode(data: ByteArray): IndexTable {
            val buf = ReadBuf(data)
            val protocol = buf.g1()
            if (protocol !in PROTOCOL_ORIGINAL..PROTOCOL_SMART) throw IOException("Unsupported index protocol $protocol")
            val version = if (protocol >= PROTOCOL_VERSIONED) buf.g4() else 0
            val flags = buf.g1()
            val smart = protocol >= PROTOCOL_SMART

            fun count(): Int = if (smart) buf.gSmart2or4() else buf.g2()

            val size = count()
            val ids = IntArray(size)
            var prev = 0
            for (i in 0 until size) {
                prev += count()
                ids[i] = prev
            }
            val named = flags and FLAG_NAMED != 0
            val nameHashes = if (named) IntArray(size) { buf.g4() } else IntArray(size)
            val crcs = IntArray(size) { buf.g4() }
            val uncompressed = if (flags and FLAG_UNCOMPRESSED_CHECKSUMS != 0) IntArray(size) { buf.g4() } else IntArray(size)
            val digests = if (flags and FLAG_DIGESTS != 0) Array(size) { buf.gArray(DIGEST_BYTES) } else Array(size) { ByteArray(0) }
            val compressedLengths = IntArray(size)
            val uncompressedLengths = IntArray(size)
            if (flags and FLAG_LENGTHS != 0) {
                for (i in 0 until size) {
                    compressedLengths[i] = buf.g4()
                    uncompressedLengths[i] = buf.g4()
                }
            }
            val versions = IntArray(size) { buf.g4() }
            val fileCounts = IntArray(size) { count() }
            val fileIds =
                Array(size) { g ->
                    var id = 0
                    IntArray(fileCounts[g]) {
                        id += count()
                        id
                    }
                }
            val fileNames = Array(size) { g -> if (named) IntArray(fileCounts[g]) { buf.g4() } else IntArray(0) }
            if (buf.isReadable) throw IOException("${buf.remaining} trailing bytes in index")
            val groups =
                MutableList(size) { i ->
                    Group(
                        ids[i],
                        nameHashes[i],
                        crcs[i],
                        uncompressed[i],
                        digests[i],
                        compressedLengths[i],
                        uncompressedLengths[i],
                        versions[i],
                        fileIds[i],
                        fileNames[i],
                    )
                }
            return IndexTable(protocol, version, flags, groups)
        }
    }
}
