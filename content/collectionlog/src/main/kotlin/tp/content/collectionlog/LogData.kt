package tp.content.collectionlog

/** One page of the log (docs/mechanics/collection-log.md CLOG1): a boss, a clue tier, a minigame, ... */
internal class LogPage(
    val tab: Int,
    /** Page struct id (the key client scripts 2735 and 4103 switch on). */
    val struct: Int,
    /** Row of the page in its tab's list: the child index the client reports when the row is clicked. */
    val index: Int,
    val name: String,
    /** Varbit that colours the page "completed" (client script 4103), or -1. */
    val completeVarbit: Int,
    val counters: List<LogCounter>,
    /** Distinct log keys of the page's slots, in display order. */
    val keys: IntArray,
)

/** A counter line above a page (client script 2735): [label] is shown with the value of [varp]. */
internal class LogCounter(
    val label: String,
    val varp: Int,
    /** Saved key of the kill count when [label] is "<npc name> kills", else null (count not tracked, OPEN-CLOG3). */
    val killKey: String?,
)

/**
 * The log's layout from the cache (`collection-log.tsv`, written by tools/wiki-import/collection_log.py) with the
 * client's item remap applied. Immutable.
 */
internal class LogData(
    val pages: List<LogPage>,
    /** Obj handed out by a source (unnoted) to the key the client reads the count under (enum 3721 remap). */
    val keyOf: Map<Int, Int>,
    /** Distinct keys: the "max" of the header counter (items listed on several pages count once, Wiki "Collection log"). */
    val total: Int,
    val pagesOfKey: Map<Int, List<LogPage>>,
    private val byTabRow: Map<Long, LogPage>,
    /** Lower-case NPC name to the saved key of its kill count. */
    val killKeyByName: Map<String, String>,
) {
    fun page(tab: Int, row: Int): LogPage? = byTabRow[tabRowKey(tab, row)]

    companion object {
        private const val TSV = "collection-log.tsv"
        private const val COLUMNS = 7
        private const val KILLS_SUFFIX = " kills"

        /** Saved key of the count of [key]; the value is the number obtained (capped at Int.MAX_VALUE). */
        fun savedKey(key: Int): String = "clog.$key"

        /** Saved key of a kill count. */
        fun killSavedKey(name: String): String = "clog.kc.$name"

        private fun tabRowKey(tab: Int, row: Int): Long = (tab.toLong() shl Int.SIZE_BITS) or row.toLong()

        /** Parses the TSV; [remap] is cache enum 3721 (log item to the obj the client counts it under). */
        fun load(remap: Map<Int, Int>): LogData {
            val text = LogData::class.java.getResource(TSV)?.readText() ?: error("$TSV missing")
            val keyOf = HashMap<Int, Int>()
            val kills = HashMap<String, String>()
            val pages =
                text
                    .lineSequence()
                    .drop(1)
                    .filter { it.isNotBlank() }
                    .map { line -> page(line.split('\t'), remap, keyOf, kills) }
                    .toList()
            val pagesOfKey = HashMap<Int, MutableList<LogPage>>()
            val byTabRow = HashMap<Long, LogPage>()
            for (page in pages) {
                byTabRow[tabRowKey(page.tab, page.index)] = page
                for (key in page.keys) pagesOfKey.getOrPut(key) { ArrayList() } += page
            }
            return LogData(pages, keyOf, pagesOfKey.size, pagesOfKey, byTabRow, kills)
        }

        private fun page(
            c: List<String>,
            remap: Map<Int, Int>,
            keyOf: MutableMap<Int, Int>,
            kills: MutableMap<String, String>,
        ): LogPage {
            check(c.size == COLUMNS) { "$TSV: ${c.size} columns" }
            val keys = LinkedHashSet<Int>()
            for (raw in c[COL_ITEMS].split(',').map { it.toInt() }) {
                val key = remap[raw] ?: raw
                keyOf[raw] = key
                keyOf.putIfAbsent(key, key)
                keys += key
            }
            val counters =
                c[COL_COUNTERS]
                    .split(';')
                    .filter { it.isNotBlank() }
                    .mapNotNull { pair ->
                        val label = pair.substringBeforeLast('=').trim()
                        if (label.isEmpty()) return@mapNotNull null
                        val name = label.takeIf { it.endsWith(KILLS_SUFFIX) }?.removeSuffix(KILLS_SUFFIX)?.lowercase()
                        val killKey = name?.let { killSavedKey(it) }
                        if (name != null && killKey != null) kills[name] = killKey
                        LogCounter(label, pair.substringAfterLast('=').toInt(), killKey)
                    }
            return LogPage(c[0].toInt(), c[1].toInt(), c[2].toInt(), c[3], c[4].toInt(), counters, keys.toIntArray())
        }

        private const val COL_COUNTERS = 5
        private const val COL_ITEMS = 6
    }
}
