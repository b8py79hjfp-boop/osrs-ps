package tp.cache.config.dbtable

import tp.cache.config.Js5Archive
import tp.cache.js5.Js5Cache
import tp.util.buf.ReadBuf
import java.io.IOException

/**
 * Lookup index over a db table column (archive 21: group = table id, file = column id + 1; file 0 is the table's
 * master index, [columnId] = -1). For each tuple component, maps a value (Int, Long or String) to the row ids holding it.
 */
class DbTableIndex internal constructor(
    val tableId: Int,
    val columnId: Int,
    /** Base var type per tuple component: 0 int, 1 long, 2 string (RuneLite `BaseVarType`). */
    val tupleTypes: IntArray,
    val tupleIndexes: List<Map<Any, IntArray>>,
) {
    /** Row ids whose [component] value equals [value], or an empty array. */
    fun rows(value: Any, component: Int = 0): IntArray = tupleIndexes[component][value] ?: NO_ROWS

    companion object {
        private val NO_ROWS = IntArray(0)
    }
}

/** Loader for archive 21. Layout: RuneLite 1.13.6 `DBTableIndexLoader.java` / `DBTableIndexManager.java`. */
object DbTableIndexLoader {
    const val NAME: String = "dbtableindex"

    private const val BASE_INT = 0
    private const val BASE_LONG = 1
    private const val BASE_STRING = 2

    /** Decodes every file of archive 21. Each file must be fully consumed. */
    fun load(cache: Js5Cache): List<DbTableIndex> {
        val archive = cache.archive(Js5Archive.DBTABLEINDEX) ?: return emptyList()
        val out = ArrayList<DbTableIndex>()
        for (tableId in archive.index.groupIds) {
            val files = cache.readFiles(Js5Archive.DBTABLEINDEX, tableId) ?: throw IOException("$NAME group $tableId is absent")
            for (i in 0 until files.size) {
                val columnId = files.ids[i] - 1
                val buf = ReadBuf(files.data[i])
                out += decode(tableId, columnId, buf)
                if (buf.isReadable) throw IOException("$NAME $tableId/${files.ids[i]}: ${buf.remaining} unread bytes")
            }
        }
        return out
    }

    private fun decode(tableId: Int, columnId: Int, buf: ReadBuf): DbTableIndex {
        val tupleSize = buf.gVarInt()
        val types = IntArray(tupleSize)
        val indexes = ArrayList<Map<Any, IntArray>>(tupleSize)
        for (i in 0 until tupleSize) {
            val type = buf.g1()
            types[i] = type
            val valueCount = buf.gVarInt()
            val map = HashMap<Any, IntArray>(valueCount * 2)
            repeat(valueCount) {
                val value: Any =
                    when (type) {
                        BASE_INT -> buf.g4()
                        BASE_LONG -> buf.g8()
                        BASE_STRING -> buf.gjstr()
                        else -> throw IOException("$NAME $tableId/${columnId + 1}: unknown base var type $type")
                    }
                val rowCount = buf.gVarInt()
                map[value] = IntArray(rowCount) { buf.gVarInt() }
            }
            indexes += map
        }
        return DbTableIndex(tableId, columnId, types, indexes)
    }
}
