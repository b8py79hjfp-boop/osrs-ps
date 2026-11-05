package tp.cache.js5

import java.io.Closeable
import java.io.IOException
import java.nio.file.Path

/** Master-index entry for one archive: the CRC and version the client validates (brief §4.3 login CRC check). */
data class ArchiveInfo(val archive: Int, val crc: Int, val version: Int, val index: Js5Index)

/** The files of one group: parallel arrays of file id and decompressed content. */
class GroupFiles(val ids: IntArray, val data: Array<ByteArray>) {
    val size: Int get() = ids.size
}

/**
 * An opened JS5 cache with its master index decoded. Immutable after [open]; shared by every world in a JVM.
 * Reads go to the disk store; callers decode once at boot and keep the decoded types.
 */
class Js5Cache private constructor(
    val store: Js5DiskStore,
    /** Indexed by archive id; null where the master index slot is empty. */
    val archives: List<ArchiveInfo?>,
) : Closeable {
    /**
     * Number of master-index slots, including empty ones. Cache 2757 has 25 slots, two of them empty (16 and 23).
     * This matches OpenRS2's "archives" figure and the client's master index (empty slots: CRC 0, version 0).
     */
    val archiveCount: Int get() = archives.size

    /** Archives that actually have an index on disk. */
    val populatedArchiveCount: Int get() = archives.count { it != null }
    val groupCount: Int get() = archives.sumOf { it?.index?.groupCount ?: 0 }

    fun archive(id: Int): ArchiveInfo? = archives.getOrNull(id)

    fun index(archive: Int): Js5Index = archive(archive)?.index ?: throw IOException("Archive $archive is absent")

    /** Decompressed data of [archive]/[group], or null if the group is absent. */
    fun readGroup(archive: Int, group: Int, key: XteaKey = XteaKey.ZERO): ByteArray? =
        store.read(archive, group)?.let { Js5Container.decode(it, key) }

    /** The files of [archive]/[group], or null if the group is absent from the index or the store. */
    fun readFiles(archive: Int, group: Int, key: XteaKey = XteaKey.ZERO): GroupFiles? {
        val index = index(archive)
        val slot = index.slotOf(group)
        if (slot < 0) return null
        val data = readGroup(archive, group, key) ?: return null
        val ids = index.fileIds[slot]
        return GroupFiles(ids, Js5Group.split(data, ids.size))
    }

    override fun close(): Unit = store.close()

    companion object {
        fun open(dir: Path): Js5Cache {
            val store = Js5DiskStore.open(dir)
            var opened = false
            try {
                val archives =
                    (0 until store.masterEntryCount()).map { id ->
                        store.read(Js5DiskStore.MASTER_ARCHIVE, id)?.let { raw ->
                            val index = Js5Index.decode(Js5Container.decode(raw))
                            ArchiveInfo(id, Js5Container.crc(raw), index.version, index)
                        }
                    }
                opened = true
                return Js5Cache(store, archives)
            } finally {
                if (!opened) store.close()
            }
        }
    }
}
