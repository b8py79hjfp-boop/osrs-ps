package tp.cache.config.dbtable

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/** One column of a db table: its tuple of script var type ids and an optional default value set. */
class DbTableColumn internal constructor(val types: IntArray, val defaults: DbColumnValues?)

/** Db table schema, archive 2 group 39. */
class DbTableType internal constructor(val id: Int) {
    /** Declared column count (op 1 header byte). */
    var columnCount: Int = 0
        internal set

    /** Columns by id; absent ids are not part of the table. */
    var columns: Map<Int, DbTableColumn> = emptyMap()
        internal set
}

/**
 * Decoder for [DbTableType]. Opcode table: RuneLite 1.13.6 `DBTableLoader.java`. RS Mod `DbTableTypeDecoder` reads
 * the same op 1; its op 200 is server-only packing and is not accepted.
 */
object DbTableTypeDecoder : ConfigDecoder<DbTableType> {
    override val name: String = "dbtable"

    /** Column list terminator byte. */
    private const val END_OF_COLUMNS = 255

    /** Bit set in a column header when a default value set follows. */
    private const val FLAG_HAS_DEFAULT = 0x80

    /** Mask for the column id in a column header. */
    private const val COLUMN_ID_MASK = 0x7F

    override fun decode(id: Int, buf: ReadBuf): DbTableType {
        val t = DbTableType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                1 -> {
                    t.columnCount = buf.g1()
                    val columns = LinkedHashMap<Int, DbTableColumn>()
                    while (true) {
                        val setting = buf.g1()
                        if (setting == END_OF_COLUMNS) break
                        val types = DbColumns.readTypes(buf)
                        val defaults = if (setting and FLAG_HAS_DEFAULT != 0) DbColumns.readValues(buf, types) else null
                        columns[setting and COLUMN_ID_MASK] = DbTableColumn(types, defaults)
                    }
                    t.columns = columns
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }
}
