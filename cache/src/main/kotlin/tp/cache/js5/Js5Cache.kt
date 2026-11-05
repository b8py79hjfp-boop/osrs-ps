package tp.cache.js5

import java.io.Closeable
import java.nio.file.Path

/** Master-index entry for one archive: the CRC and version the client validates (brief §4.3 login CRC check). */
data class ArchiveInfo(val archive: Int, val crc: Int, val version: Int, val index: Js5Index)

/**
 * An opened JS5 cache with its master index decoded. Immutable after [open]; shared by every world in a JVM.
 */
class Js5Cache private constructor(
    val store: Js5DiskStore,
    /** Indexed by archive id; null where the master index has no entry. */
    val archives: List<ArchiveInfo?>,
) : Closeable {
    val archiveCount: Int get() = archives.count { it != null }
    val groupCount: Int get() = archives.sumOf { it?.index?.groupCount ?: 0 }

    fun archive(id: Int): ArchiveInfo? = archives.getOrNull(id)

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
