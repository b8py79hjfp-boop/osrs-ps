package tp.cache.js5

import tp.util.buf.ReadBuf
import java.io.IOException

/**
 * A decoded JS5 archive index (the group table for one archive, stored as group `255/<archive>`).
 *
 * Field order follows OpenRS2 `Js5Index.read` (https://github.com/openrs2/openrs2, cache/Js5Index.kt):
 * protocol, [version], flags, group count, delta-coded group ids, [name hashes], checksums,
 * [uncompressed checksums], [digests], [lengths], versions, file counts, delta-coded file ids, [file name hashes].
 *
 * Arrays are parallel and indexed by *slot* (position in the index), not by group id. Use [slotOf] to map an id.
 */
class Js5Index(
    val protocol: Int,
    val version: Int,
    val groupIds: IntArray,
    /** Group name hashes ([nameHash]), or null when the archive is unnamed. */
    val groupNameHashes: IntArray?,
    val groupChecksums: IntArray,
    val groupVersions: IntArray,
    val fileCounts: IntArray,
    /** Per slot: the file ids of that group, ascending. */
    val fileIds: Array<IntArray>,
) {
    val groupCount: Int get() = groupIds.size

    private val slotById: Map<Int, Int> =
        HashMap<Int, Int>(groupIds.size * 2).also { m ->
            for (slot in groupIds.indices) m[groupIds[slot]] = slot
        }

    private val slotByName: Map<Int, Int> =
        HashMap<Int, Int>().also { m ->
            groupNameHashes?.forEachIndexed { slot, hash -> m[hash] = slot }
        }

    /** Slot of [groupId], or -1 when the archive has no such group. */
    fun slotOf(groupId: Int): Int = slotById[groupId] ?: -1

    /** Group id whose name hashes to [name] (e.g. `m50_50`), or -1. */
    fun groupIdByName(name: String): Int = slotByName[nameHash(name)]?.let { groupIds[it] } ?: -1

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

        /** JS5 name hash: Java `String.hashCode` of the lower-cased name (client `Js5Index.getGroupId`). */
        fun nameHash(name: String): Int = name.lowercase().hashCode()

        fun decode(data: ByteArray): Js5Index {
            val buf = ReadBuf(data)
            val protocol = buf.g1()
            if (protocol !in PROTOCOL_ORIGINAL..PROTOCOL_SMART) {
                throw IOException("Unsupported index protocol $protocol")
            }
            val version = if (protocol >= PROTOCOL_VERSIONED) buf.g4() else 0
            val flags = buf.g1()
            val smart = protocol >= PROTOCOL_SMART

            fun count(): Int = if (smart) buf.gSmart2or4() else buf.g2()

            val size = count()
            val groupIds = IntArray(size)
            var prev = 0
            for (i in 0 until size) {
                prev += count()
                groupIds[i] = prev
            }
            val named = flags and FLAG_NAMED != 0
            val nameHashes = if (named) IntArray(size) { buf.g4() } else null
            val checksums = IntArray(size) { buf.g4() }
            if (flags and FLAG_UNCOMPRESSED_CHECKSUMS != 0) buf.skip(size * Int.SIZE_BYTES)
            if (flags and FLAG_DIGESTS != 0) buf.skip(size * DIGEST_BYTES)
            if (flags and FLAG_LENGTHS != 0) buf.skip(size * LENGTH_BYTES)
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
            if (named) buf.skip(fileCounts.sum() * Int.SIZE_BYTES)
            if (buf.isReadable) throw IOException("${buf.remaining} trailing bytes in index")
            return Js5Index(protocol, version, groupIds, nameHashes, checksums, versions, fileCounts, fileIds)
        }
    }
}
