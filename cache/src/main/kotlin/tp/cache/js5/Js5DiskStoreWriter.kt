package tp.cache.js5

import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Append-only writer for a JS5 disk cache directory that already exists (copy it first; the reader's layout is documented
 * on [Js5DiskStore]). A group is written to fresh sectors at the end of `main_file_cache.dat2` and its `idx` entry is
 * pointed at them; the sectors of the container it replaces become unreachable garbage, which the reader never visits.
 * Not thread-safe. The caller supplies finished containers (see [Js5Container.encode]); nothing here knows about indexes
 * or CRCs.
 */
class Js5DiskStoreWriter private constructor(
    private val data: FileChannel,
    private val indexes: MutableMap<Int, FileChannel>,
    private val dir: Path,
) : Closeable {
    /** Writes [container] as `archive/group`, replacing any existing one. Archive 255 is the master index. */
    fun write(archive: Int, group: Int, container: ByteArray) {
        require(
            group >= 0 && container.isNotEmpty() && container.size <= MAX_CONTAINER,
        ) { "Bad group $archive/$group (${container.size} bytes)" }
        val extended = group > Js5DiskStore.MAX_SHORT_GROUP
        val headerSize = if (extended) Js5DiskStore.EXTENDED_HEADER_SIZE else Js5DiskStore.HEADER_SIZE
        val payloadSize = Js5DiskStore.SECTOR_SIZE - headerSize
        val chunks = (container.size + payloadSize - 1) / payloadSize
        val first = maxOf(1L, (data.size() + Js5DiskStore.SECTOR_SIZE - 1) / Js5DiskStore.SECTOR_SIZE)

        for (chunk in 0 until chunks) {
            val sector = ByteBuffer.allocate(Js5DiskStore.SECTOR_SIZE)
            val offset = chunk * payloadSize
            val length = minOf(payloadSize, container.size - offset)
            val next = if (chunk == chunks - 1) 0 else (first + chunk + 1).toInt()
            if (extended) sector.putInt(group) else sector.putShort(group.toShort())
            sector.putShort(chunk.toShort())
            putMedium(sector, next)
            sector.put(archive.toByte())
            sector.put(container, offset, length)
            sector.clear()
            writeFully(data, sector, (first + chunk) * Js5DiskStore.SECTOR_SIZE)
        }

        val index = indexes.getOrPut(archive) { openNewIndex(archive) }
        val entryPos = group.toLong() * Js5DiskStore.INDEX_ENTRY_SIZE
        if (index.size() < entryPos) writeFully(index, ByteBuffer.allocate((entryPos - index.size()).toInt()), index.size())
        val entry = ByteBuffer.allocate(Js5DiskStore.INDEX_ENTRY_SIZE)
        putMedium(entry, container.size)
        putMedium(entry, first.toInt())
        entry.flip()
        writeFully(index, entry, entryPos)
    }

    private fun openNewIndex(archive: Int): FileChannel =
        FileChannel.open(
            dir.resolve("main_file_cache.idx$archive"),
            StandardOpenOption.CREATE,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
        )

    override fun close() {
        data.close()
        indexes.values.forEach(FileChannel::close)
    }

    companion object {
        /** Sizes are u24 in the index entry. */
        private const val MAX_CONTAINER = 0xFFFFFF
        private const val MAX_ARCHIVE_FILE = 255

        /** Opens the cache copy in [dir] for writing. */
        fun open(dir: Path): Js5DiskStoreWriter {
            val dat2 = dir.resolve("main_file_cache.dat2")
            if (!Files.isRegularFile(dat2)) throw IOException("Missing $dat2")
            val indexes = HashMap<Int, FileChannel>()
            for (id in 0..MAX_ARCHIVE_FILE) {
                val idx = dir.resolve("main_file_cache.idx$id")
                if (Files.isRegularFile(idx)) {
                    indexes[id] = FileChannel.open(idx, StandardOpenOption.READ, StandardOpenOption.WRITE)
                }
            }
            return Js5DiskStoreWriter(FileChannel.open(dat2, StandardOpenOption.READ, StandardOpenOption.WRITE), indexes, dir)
        }

        private fun putMedium(buf: ByteBuffer, value: Int) {
            buf.put((value ushr 16).toByte()).put((value ushr 8).toByte()).put(value.toByte())
        }

        private fun writeFully(channel: FileChannel, buf: ByteBuffer, position: Long) {
            var pos = position
            while (buf.hasRemaining()) pos += channel.write(buf, pos)
        }
    }
}
