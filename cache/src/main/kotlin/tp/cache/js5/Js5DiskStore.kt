package tp.cache.js5

import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Read-only access to a JS5 disk cache (`main_file_cache.dat2` + `main_file_cache.idx*`).
 *
 * Format reference: OpenRS2 `DiskStore` (https://github.com/openrs2/openrs2, archive/cache/store/DiskStore.kt)
 * and RuneLite `cache/src/main/java/net/runelite/cache/fs/jagex/DiskStorage.java`.
 * Returned bytes are the raw on-disk container (compression header, payload, optional 2-byte version trailer).
 */
class Js5DiskStore private constructor(
    private val data: FileChannel,
    private val indexes: Map<Int, FileChannel>,
) : Closeable {
    /** Archive ids with an `idx` file present, excluding the master index. */
    val archiveIds: Set<Int> get() = indexes.keys - MASTER_ARCHIVE

    /** Number of entries in `idx255`, i.e. the number of archives the master index describes. */
    fun masterEntryCount(): Int = groupCapacity(MASTER_ARCHIVE)

    /** Number of 6-byte entries in `idx<archive>` (an upper bound on group ids + 1). */
    fun groupCapacity(archive: Int): Int {
        val channel = indexes[archive] ?: return 0
        return (channel.size() / INDEX_ENTRY_SIZE).toInt()
    }

    /** Reads the raw container for [archive]/[group], or null if absent. */
    fun read(archive: Int, group: Int): ByteArray? {
        val index = indexes[archive] ?: return null
        val entryPos = group.toLong() * INDEX_ENTRY_SIZE
        if (group < 0 || entryPos + INDEX_ENTRY_SIZE > index.size()) return null

        val entry = ByteBuffer.allocate(INDEX_ENTRY_SIZE)
        readFully(index, entry, entryPos)
        entry.flip()
        val size = entry.getMedium()
        var sector = entry.getMedium()
        if (size <= 0 || sector <= 0) return null

        val extended = group > MAX_SHORT_GROUP
        val headerSize = if (extended) EXTENDED_HEADER_SIZE else HEADER_SIZE
        val out = ByteArray(size)
        val buf = ByteBuffer.allocate(SECTOR_SIZE)
        var written = 0
        var chunk = 0
        while (written < size) {
            if (sector <= 0 || sector.toLong() * SECTOR_SIZE >= data.size()) {
                throw IOException("Group $archive/$group: sector $sector out of bounds")
            }
            val payload = minOf(size - written, SECTOR_SIZE - headerSize)
            buf.clear().limit(headerSize + payload)
            readFully(data, buf, sector.toLong() * SECTOR_SIZE)
            buf.flip()
            val sGroup = if (extended) buf.getInt() else buf.getShort().toInt() and U16
            val sChunk = buf.getShort().toInt() and U16
            val next = buf.getMedium()
            val sArchive = buf.get().toInt() and U8
            if (sGroup != group || sChunk != chunk || sArchive != archive) {
                throw IOException(
                    "Group $archive/$group chunk $chunk: sector $sector header mismatch " +
                        "(group=$sGroup chunk=$sChunk archive=$sArchive)",
                )
            }
            buf.get(out, written, payload)
            written += payload
            sector = next
            chunk++
        }
        return out
    }

    override fun close() {
        data.close()
        indexes.values.forEach(FileChannel::close)
    }

    companion object {
        const val MASTER_ARCHIVE: Int = 255

        /** Sector layout: 520 bytes = 8-byte header + 512 data, or 10-byte header + 510 data for group ids > 0xFFFF. */
        internal const val SECTOR_SIZE = 520
        internal const val HEADER_SIZE = 8
        internal const val EXTENDED_HEADER_SIZE = 10
        internal const val MAX_SHORT_GROUP = 0xFFFF

        /** Index entry: u24 size + u24 first sector. */
        internal const val INDEX_ENTRY_SIZE = 6
        private const val U8 = 0xFF
        private const val U16 = 0xFFFF
        private const val MAX_ARCHIVE_FILE = 254

        fun open(dir: Path): Js5DiskStore {
            val dat2 = dir.resolve("main_file_cache.dat2")
            if (!Files.isRegularFile(dat2)) throw IOException("Missing $dat2 — run ./gradlew setup")
            val indexes = HashMap<Int, FileChannel>()
            for (id in 0..MAX_ARCHIVE_FILE) {
                val idx = dir.resolve("main_file_cache.idx$id")
                if (Files.isRegularFile(idx)) indexes[id] = FileChannel.open(idx, StandardOpenOption.READ)
            }
            val master = dir.resolve("main_file_cache.idx$MASTER_ARCHIVE")
            if (!Files.isRegularFile(master)) throw IOException("Missing $master")
            indexes[MASTER_ARCHIVE] = FileChannel.open(master, StandardOpenOption.READ)
            return Js5DiskStore(FileChannel.open(dat2, StandardOpenOption.READ), indexes)
        }

        private fun readFully(channel: FileChannel, buf: ByteBuffer, position: Long) {
            var pos = position
            while (buf.hasRemaining()) {
                val n = channel.read(buf, pos)
                if (n < 0) throw IOException("Unexpected EOF at $pos")
                pos += n
            }
        }

        private fun ByteBuffer.getMedium(): Int =
            ((get().toInt() and U8) shl 16) or ((get().toInt() and U8) shl 8) or (get().toInt() and U8)
    }
}
