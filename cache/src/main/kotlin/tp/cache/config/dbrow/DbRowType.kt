package tp.cache.config.dbrow

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.cache.config.dbtable.DbColumnValues
import tp.cache.config.dbtable.DbColumns
import tp.util.buf.ReadBuf

/** Db row, archive 2 group 38: typed cell values keyed by column id. These drive shops and other table content. */
class DbRowType internal constructor(val id: Int) {
    /** Owning table id (op 4), or -1. */
    var tableId: Int = -1
        internal set

    /** Declared column count (op 3 header byte). */
    var columnCount: Int = 0
        internal set

    /** Cell values by column id; absent columns hold the table default. */
    var columns: Map<Int, DbColumnValues> = emptyMap()
        internal set
}

/**
 * Decoder for [DbRowType]. Opcode table: RuneLite 1.13.6 `DBRowLoader.java` (op 4 table id is a base-128 varint),
 * identical in RS Mod `DbRowTypeDecoder`.
 */
object DbRowTypeDecoder : ConfigDecoder<DbRowType> {
    override val name: String = "dbrow"

    /** Column list terminator byte. */
    private const val END_OF_COLUMNS = 255

    override fun decode(id: Int, buf: ReadBuf): DbRowType {
        val t = DbRowType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                3 -> {
                    t.columnCount = buf.g1()
                    val columns = LinkedHashMap<Int, DbColumnValues>()
                    while (true) {
                        val column = buf.g1()
                        if (column == END_OF_COLUMNS) break
                        val types = DbColumns.readTypes(buf)
                        columns[column] = DbColumns.readValues(buf, types)
                    }
                    t.columns = columns
                }

                4 -> {
                    t.tableId = buf.gVarInt()
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }
}
