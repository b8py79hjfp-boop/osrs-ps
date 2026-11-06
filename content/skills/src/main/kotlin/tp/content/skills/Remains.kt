package tp.content.skills

/** What kind of remains an obj is: bones are buried or offered, ashes are scattered. */
internal enum class RemainsKind { BONE, ASH }

/**
 * A buryable bone or scatterable ash from `remains.tsv` (tools/wiki-import/prayer_remains.py, the Wiki's `{{Prayer info}}`):
 * [obj] needs [level] Prayer and gives [xp] when buried or scattered.
 */
internal class Remains(val obj: Int, val kind: RemainsKind, val level: Int, val xp: Double, val name: String)

internal object RemainsTable {
    /** The columns of `remains.tsv`: obj, kind, level, xp, name. */
    private const val COLUMNS = 5

    /** Reads `remains.tsv`. */
    fun load(): List<Remains> {
        val text = RemainsTable::class.java.getResource("remains.tsv")?.readText() ?: error("remains.tsv missing")
        return text
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line -> parse(line.split('\t')) }
            .toList()
    }

    private fun parse(cols: List<String>): Remains {
        require(cols.size == COLUMNS) { "remains.tsv row has ${cols.size} columns: $cols" }
        val kind = if (cols[1] == "bone") RemainsKind.BONE else RemainsKind.ASH
        return Remains(cols[0].toInt(), kind, cols[2].toInt(), cols[3].toDouble(), cols[4])
    }
}
