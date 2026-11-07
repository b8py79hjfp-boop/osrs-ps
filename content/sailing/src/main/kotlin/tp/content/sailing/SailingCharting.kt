package tp.content.sailing

import tp.symbols.DbTables

/**
 * The tool kind of a charting task: the id of the db table holding its type rows, which is also the `CHARTING_TYPE` cell of
 * `Sailing_Charting_Core`. Names follow docs/mechanics/sailing-charting.md C1 (the Wiki calls DRINK_CRATE "Crate" and
 * MERMAID_GUIDE "Diving").
 */
internal enum class ChartingType(val tableId: Int) {
    GENERIC(DbTables.Sailing_Charting_Generic.ID),
    SPYGLASS(DbTables.Sailing_Charting_Spyglass.ID),
    CURRENT_DUCK(DbTables.Sailing_Charting_Current_Duck.ID),
    DRINK_CRATE(DbTables.Sailing_Charting_Drink_Crate.ID),
    WEATHER_TROLL(DbTables.Sailing_Charting_Weather_Troll.ID),
    MERMAID_GUIDE(DbTables.Sailing_Charting_Mermaid_Guide.ID),
    ;

    companion object {
        fun ofTable(tableId: Int): ChartingType? = entries.firstOrNull { it.tableId == tableId }
    }
}

/**
 * One row of `Sailing_Charting_Core` (db 154) joined with the row of its type table that points back at it. Coordinates,
 * feedback strings, routes and drinks of the type tables are server-only and absent from the cache; the Wiki import
 * supplies task locations (`charting-tasks.tsv`).
 */
internal class ChartingTask(
    val row: Int,
    /** `UNIQUE_ID`: the task number (the Wiki's `SeaChartRow` id, the suffix of the `SAILING_CHARTING_*` varbits). */
    val uniqueId: Int,
    val hint: String,
    val type: ChartingType,
    /** Row of the type table that references this task, or null if the type table has none (only server data would). */
    val typeRow: Int?,
    val seaRow: Int,
    val secondarySeaRow: Int?,
    /** `GIVE_REPEAT_XP`: the task pays XP again when repeated (set on the current-duck tasks only in the cache). */
    val giveRepeatXp: Boolean,
    /** Row of the hazard (db 200) the task sits in, or null. */
    val hazardRow: Int?,
    /** Row of the dock (db 194) that must be unlocked first, or null. */
    val requiredDockRow: Int?,
)

/** One row of `Sailing_Charting_Tool_Recovery` (db 161): a charting tool the captain's log can hand out again. */
internal class ChartingTool(val row: Int, val name: String, val inlineName: String?, val objs: List<Int>, val uniqueId: Int)
