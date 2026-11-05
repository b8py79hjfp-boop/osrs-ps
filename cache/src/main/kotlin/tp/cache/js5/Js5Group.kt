package tp.cache.js5

import java.io.IOException

/**
 * Splits a decompressed multi-file group into its files.
 *
 * Layout (client `Js5.unpackGroup`; RuneLite `ArchiveFiles.loadContents`): file data interleaved in `chunks` rounds,
 * then per round one delta-coded i32 size per file, then a trailing u8 chunk count. A single-file group is its data.
 */
object Js5Group {
    fun split(data: ByteArray, fileCount: Int): Array<ByteArray> {
        if (fileCount == 1) return arrayOf(data)
        if (data.isEmpty()) throw IOException("Empty multi-file group")
        val chunks = data[data.size - 1].toInt() and 0xFF
        var tablePos = data.size - 1 - chunks * fileCount * Int.SIZE_BYTES
        if (tablePos < 0) throw IOException("Chunk table ($chunks x $fileCount) exceeds group size ${data.size}")

        val chunkSizes = Array(chunks) { IntArray(fileCount) }
        val fileSizes = IntArray(fileCount)
        for (chunk in 0 until chunks) {
            var size = 0
            for (file in 0 until fileCount) {
                size += readInt(data, tablePos)
                tablePos += Int.SIZE_BYTES
                chunkSizes[chunk][file] = size
                fileSizes[file] += size
            }
        }

        val files = Array(fileCount) { ByteArray(fileSizes[it]) }
        val offsets = IntArray(fileCount)
        var pos = 0
        for (chunk in 0 until chunks) {
            for (file in 0 until fileCount) {
                val size = chunkSizes[chunk][file]
                System.arraycopy(data, pos, files[file], offsets[file], size)
                offsets[file] += size
                pos += size
            }
        }
        return files
    }

    private fun readInt(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
}
