package tp.tools.cachedump

import tp.cache.config.EntityOps
import tp.cache.config.ParamMap
import tp.cache.config.TypeList
import tp.cache.config.dbtable.DbColumnValues
import tp.cache.config.dbtable.DbTableColumn

/** Ordered `name to value` pairs describing one decoded config. */
internal typealias Fields = List<Pair<String, Any?>>

/**
 * Prints the configs of one type. [fields] lists every public property of the type (written out by hand, no
 * reflection); [brief] picks the few most useful ones for the one-line-per-id `--all` mode.
 */
internal class ConfigPrinter<T : Any>(
    private val types: TypeList<T>,
    private val fields: (T) -> Fields,
    private val brief: (T) -> Fields,
) {
    /** `field = value` lines for config [id], or null when the id is absent. */
    fun one(id: Int): List<String>? =
        types[id]?.let { type ->
            listOf("id = $id") + fields(type).map { (name, value) -> "$name = ${formatValue(value)}" }
        }

    /** One line per present id. */
    fun all(): List<String> =
        (0 until types.size).mapNotNull { id ->
            types[id]?.let { type -> "$id ${brief(type).joinToString(" ") { (name, value) -> "$name=${formatValue(value)}" }}" }
        }
}

/** Compact, single-line rendering of a config field value. Array indices are 0-based, except entity ops (op1..op5). */
internal fun formatValue(value: Any?): String =
    when (value) {
        null -> "null"
        is String -> "\"$value\""
        is IntArray -> value.joinToString(",", "[", "]")
        is Array<*> -> formatArray(value)
        is Map<*, *> -> value.entries.joinToString(", ", "{", "}") { "${formatValue(it.key)}=${formatValue(it.value)}" }
        is ParamMap -> formatParams(value)
        is EntityOps -> formatOps(value)
        is DbColumnValues -> formatColumnValues(value)
        is DbTableColumn -> "types=${formatValue(value.types)} defaults=${formatValue(value.defaults)}"
        else -> value.toString()
    }

private fun formatArray(array: Array<*>): String =
    array
        .withIndex()
        .filter { (_, element) -> element != null && element != "" }
        .joinToString(", ", "[", "]") { (index, element) -> "$index=${formatValue(element)}" }

private fun formatParams(params: ParamMap): String =
    params.keys.sorted().joinToString(", ", "{", "}") { key ->
        val value: Any? = params.int(key) ?: params.long(key) ?: params.string(key)
        "$key=${formatValue(value)}"
    }

private fun formatOps(ops: EntityOps): String {
    val parts = ArrayList<String>()
    ops.ops.forEachIndexed { index, text -> if (text != null) parts += "op${index + 1}=\"$text\"" }
    if (ops.subOps.isNotEmpty()) parts += "subOps=${formatValue(ops.subOps)}"
    ops.conditionalOps.forEach { parts += "conditional=$it" }
    return parts.joinToString(", ", "[", "]")
}

private fun formatColumnValues(values: DbColumnValues): String {
    val tuples =
        (0 until values.fieldCount).joinToString(", ", "[", "]") { field ->
            values.types.indices.joinToString(",", "(", ")") { component -> formatValue(values[field, component]) }
        }
    return "types=${formatValue(values.types)} values=$tuples"
}
