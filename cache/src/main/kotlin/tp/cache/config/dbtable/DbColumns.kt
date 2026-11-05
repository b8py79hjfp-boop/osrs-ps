package tp.cache.config.dbtable

import tp.util.buf.ReadBuf

/**
 * Typed values of one db column: [fieldCount] tuples of `types.size` values, flattened row-major. Each value is a
 * [String] when its type is [DbColumns.TYPE_STRING], else an [Int] (RuneLite `DBTableLoader.decodeColumnFields`,
 * RS Mod `readColumnValues`). Shared by dbtable defaults and dbrow cells.
 */
class DbColumnValues internal constructor(val types: IntArray, private val values: Array<Any>) {
    val fieldCount: Int get() = if (types.isEmpty()) 0 else values.size / types.size

    /** Raw value at tuple [field], component [component]. */
    operator fun get(field: Int, component: Int = 0): Any = values[field * types.size + component]

    fun int(field: Int = 0, component: Int = 0): Int = get(field, component) as Int

    fun string(field: Int = 0, component: Int = 0): String = get(field, component) as String
}

object DbColumns {
    /** Script var type id of `string` (RuneLite `ScriptVarType.STRING`); every other column type is read as an i32. */
    const val TYPE_STRING = 36

    /** Reads `count (smart) x types.size` values. */
    internal fun readValues(buf: ReadBuf, types: IntArray): DbColumnValues {
        val fieldCount = buf.gSmart1or2()
        val values =
            Array<Any>(fieldCount * types.size) { i ->
                if (types[i % types.size] == TYPE_STRING) buf.gjstr() else buf.g4()
            }
        return DbColumnValues(types, values)
    }

    /** Reads `u8 count` then that many smart-encoded script var type ids. */
    internal fun readTypes(buf: ReadBuf): IntArray {
        val count = buf.g1()
        return IntArray(count) { buf.gSmart1or2() }
    }
}
