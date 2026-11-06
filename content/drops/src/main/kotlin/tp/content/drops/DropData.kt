package tp.content.drops

/**
 * One line of a drop table (docs/mechanics/drops.md). [obj] is a cache obj id, [NOTHING] for an explicit empty slot,
 * or ignored when [table] names a shared table to roll instead.
 */
internal class DropLine(
    val obj: Int,
    val table: String?,
    /** Obj dropped instead of [obj] inside the underground talisman region (nature/chaos talisman), or -1. */
    val undergroundObj: Int,
    /** Inclusive (low, high) pairs flattened; every listed integer is equally likely. */
    val ranges: IntArray,
    val noted: Boolean,
    /** Chance of this line as written by the Wiki (num/den). */
    val chance: Double,
    val rolls: Int,
    /** Empty slot that a worn ring of wealth removes (gem and mega-rare tables). */
    val ringRemoves: Boolean,
)

/** An NPC's lines by roll kind, in the Wiki's order. */
internal class NpcDrops(
    val always: List<DropLine>,
    val pre: List<DropLine>,
    val main: List<DropLine>,
    /** Main-table rows whose rarities add up to more than 1: each is rolled on its own (OPEN-D3). */
    val independent: List<DropLine>,
    val secondary: List<DropLine>,
    val tertiary: List<DropLine>,
)

/**
 * The Wiki drop data: `npc-drops.tsv` and `drop-tables.tsv`, written by tools/wiki-import/npc_drops.py (CC BY-NC-SA 3.0,
 * data/PROVENANCE.md). Immutable, loaded once per JVM.
 */
internal object DropData {
    const val NOTHING = -1

    private val npcs: Map<Int, NpcDrops> = parseNpcs()
    private val tables: Map<String, List<DropLine>> = parseTables()

    val npcCount: Int get() = npcs.size

    fun of(npc: Int): NpcDrops? = npcs[npc]

    fun table(name: String): List<DropLine>? = tables[name]

    private fun read(name: String): List<Map<String, String>> {
        val text = DropData::class.java.getResource(name)?.readText() ?: error("$name missing")
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.first().split('\t')
        return lines.drop(1).map { line -> header.zip(line.split('\t')).toMap() }
    }

    private fun parseNpcs(): Map<Int, NpcDrops> {
        val byNpc = HashMap<Int, MutableMap<String, MutableList<DropLine>>>()
        for (row in read("npc-drops.tsv")) {
            val line = line(row)
            val kind = row.getValue("kind")
            for (id in row.getValue("npcs").split(',')) {
                byNpc.getOrPut(id.toInt()) { HashMap() }.getOrPut(kind) { ArrayList() } += line
            }
        }
        return byNpc.mapValues { (_, kinds) ->
            NpcDrops(
                kinds["always"].orEmpty(),
                kinds["pre"].orEmpty(),
                kinds["main"].orEmpty(),
                kinds["independent"].orEmpty(),
                kinds["secondary"].orEmpty(),
                kinds["tertiary"].orEmpty(),
            )
        }
    }

    private fun parseTables(): Map<String, List<DropLine>> {
        val out = LinkedHashMap<String, MutableList<DropLine>>()
        for (row in read("drop-tables.tsv")) out.getOrPut(row.getValue("table")) { ArrayList() } += line(row)
        return out
    }

    /** `item` cell: obj id, "-" (empty), "-w" (empty that a ring of wealth removes), "@table" or "nature/chaos" ids. */
    private fun line(row: Map<String, String>): DropLine {
        val item = row.getValue("item")
        var obj = NOTHING
        var table: String? = null
        var underground = -1
        if (item.startsWith("@")) {
            table = item.substring(1)
        } else if ('/' in item) {
            val pair = item.split('/')
            obj = pair[0].toInt()
            underground = pair[1].toInt()
        } else if (item != "-" && item != "-w") {
            obj = item.toInt()
        }
        val ranges =
            row.getValue("qty").split(',').flatMap { part ->
                val bounds = part.split('-')
                listOf(bounds[0].toInt(), bounds.last().toInt())
            }
        return DropLine(
            obj,
            table,
            underground,
            ranges.toIntArray(),
            row.getValue("noted") == "1",
            row.getValue("num").toDouble() / row.getValue("den").toDouble(),
            row["rolls"]?.toInt() ?: 1,
            item == "-w",
        )
    }
}
