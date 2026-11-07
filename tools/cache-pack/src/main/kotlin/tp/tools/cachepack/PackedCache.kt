package tp.tools.cachepack

import tp.cache.js5.Js5Cache
import tp.cache.js5.Js5Container
import tp.cache.js5.Js5DiskStore
import tp.cache.js5.Js5DiskStoreWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * A writable copy of the source cache. [putGroup] replaces or adds a group, updating that group's entry in its archive index
 * (CRC, lengths, version, files) in memory; [finish] re-encodes every touched index and stores it as the master-index entry
 * `255/<archive>`, which is where the server's master checksum table (255/255) gets its per-archive CRC and version.
 *
 * Compression is gzip everywhere (see `Js5Container.encode`). Group versions go up by one so a client with a cached copy
 * of the old group reloads it; an index version goes up by one per run.
 */
internal class PackedCache private constructor(private val source: Js5Cache, private val writer: Js5DiskStoreWriter) : AutoCloseable {
    private val tables = HashMap<Int, IndexTable>()
    private val touched = LinkedHashSet<Int>()

    /** Groups written, `archive/group`, in order. */
    val written = ArrayList<String>()

    private fun table(archive: Int): IndexTable =
        tables.getOrPut(archive) {
            val raw = source.store.read(Js5DiskStore.MASTER_ARCHIVE, archive) ?: throw IOException("Archive $archive is absent")
            val table = IndexTable.decode(Js5Container.decode(raw))
            if (table.hasDigests) throw IOException("Archive $archive carries Whirlpool digests; the packer cannot update them")
            table
        }

    fun index(archive: Int): IndexTable = table(archive)

    /**
     * Writes `archive/group` made of [fileIds] (ascending) with their [fileData]. [nameHash] is stored when the archive is
     * named and ignored otherwise.
     */
    fun putGroup(archive: Int, group: Int, fileIds: IntArray, fileData: List<ByteArray>, nameHash: Int = 0) {
        require(fileIds.size == fileData.size && fileIds.isNotEmpty()) { "Group $archive/$group needs one data array per file" }
        require(fileIds.toList() == fileIds.sorted()) { "Group $archive/$group: file ids must be ascending" }
        val table = table(archive)
        val data = if (fileData.size == 1) fileData[0] else GroupCodec.join(fileData)
        val existing = table.group(group)
        val version = (existing?.version ?: 0) + 1
        val container = Js5Container.encode(data, Js5Container.GZIP, version)
        writer.write(archive, group, container)

        val crc = Js5Container.crc(container)
        val compressed = Js5Container.containerLength(container)
        val names = IntArray(fileIds.size)
        if (existing != null) {
            existing.crc = crc
            existing.compressedLength = compressed
            existing.uncompressedLength = data.size
            existing.version = version
            existing.fileIds = fileIds
            existing.fileNameHashes = names
            if (table.named && nameHash != 0) existing.nameHash = nameHash
        } else {
            val entry =
                IndexTable.Group(
                    group,
                    if (table.named) nameHash else 0,
                    crc,
                    0,
                    ByteArray(0),
                    compressed,
                    data.size,
                    version,
                    fileIds,
                    names,
                )
            val at = table.groups.indexOfFirst { it.id > group }
            if (at < 0) table.groups += entry else table.groups.add(at, entry)
        }
        touched += archive
        written += "$archive/$group"
    }

    /** Writes the touched indexes. Returns the new `(archive, crc, version)` of each, for the log. */
    fun finish(): List<Triple<Int, Int, Int>> {
        val result = ArrayList<Triple<Int, Int, Int>>()
        for (archive in touched) {
            val table = table(archive)
            table.version += 1
            val container = Js5Container.encode(table.encode(), Js5Container.GZIP)
            writer.write(Js5DiskStore.MASTER_ARCHIVE, archive, container)
            result += Triple(archive, Js5Container.crc(container), table.version)
        }
        return result
    }

    override fun close() = writer.close()

    companion object {
        /** Copies every `main_file_cache.*` file of [sourceDir] into [targetDir] (replacing it) and opens the copy for writing. */
        fun create(source: Js5Cache, sourceDir: Path, targetDir: Path): PackedCache {
            if (Files.exists(targetDir)) {
                Files.walk(targetDir).use { s -> s.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
            }
            Files.createDirectories(targetDir)
            Files.newDirectoryStream(sourceDir, "main_file_cache.*").use { files ->
                for (f in files) Files.copy(f, targetDir.resolve(f.fileName), StandardCopyOption.REPLACE_EXISTING)
            }
            return PackedCache(source, Js5DiskStoreWriter.open(targetDir))
        }
    }
}

/** Joins files into a multi-file group with a single chunk: the inverse of `Js5Group.split`. */
internal object GroupCodec {
    fun join(files: List<ByteArray>): ByteArray {
        val out = WriteBuf()
        files.forEach { out.pBytes(it) }
        var previous = 0
        for (f in files) {
            out.p4(f.size - previous)
            previous = f.size
        }
        out.p1(1)
        return out.toByteArray()
    }
}
