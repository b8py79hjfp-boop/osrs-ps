@file:Suppress("MagicNumber") // data tables: Wiki "Mahogany Homes" and its Mechanics page (docs/mechanics/mahogany-homes.md)

package tp.content.skills

import tp.symbols.Objs

/**
 * A contract tier. [level] is the Construction level that unlocks it, [plank] the plank every build uses, [build] and [repair] the
 * xp per plank built and per plank repair, [done] the xp for handing the contract in, [points] the carpenter points it gives.
 * Wiki "Mahogany Homes" (Experience costs table and Rewards table); [state] is the varbit value of a finished furniture
 * (children 5..8 of the hotspot multi-locs: regular, oak, teak, mahogany; cache 2757).
 */
internal enum class MahoganyTier(
    val label: String,
    val level: Int,
    val plank: Int,
    val build: Double,
    val repair: Double,
    val done: Double,
    val points: Int,
    val state: Int,
) {
    BEGINNER("Beginner", 1, Objs.WOODPLANK, 22.5, 127.5, 500.0, 2, 5),
    NOVICE("Novice", 20, Objs.PLANK_OAK, 48.0, 160.0, 1250.0, 3, 6),
    ADEPT("Adept", 50, Objs.PLANK_TEAK, 72.0, 190.0, 2250.0, 4, 7),
    EXPERT("Expert", 70, Objs.PLANK_MAHOGANY, 112.0, 240.0, 2750.0, 5, 8),
}

/** The eight client houses, in the order used by the saved contract: client name, town, the NPC id of the client. */
internal class MahoganyHome(val name: String, val town: String, val npc: Int)

/**
 * One furniture slot of a contract layout: [slot] 0..7 is the client varbit `10554 + slot`, [state] the value it starts at
 * (1 repair, 3 replace), [count] the planks or steel bars used, [bar] whether the material is a steel bar, [name] the Wiki's name.
 */
internal class MahoganySlot(val slot: Int, val state: Int, val count: Int, val bar: Boolean, val name: String)

/** One contract layout: the [slots] that are not finished at the start. */
internal class MahoganyLayout(val tier: MahoganyTier, val home: Int, val slots: List<MahoganySlot>)

/**
 * A hotspot as the map places it: the multi-loc parent [loc] at ([x], [z], [level]) of house [home], drawn by varbit slot
 * [slot]. A click on it reports the parent id (docs/mechanics/farming.md F1 has the same rule for patches).
 */
internal class MahoganyHotspot(val home: Int, val x: Int, val z: Int, val level: Int, val loc: Int, val slot: Int)

internal object MahoganyData {
    /** The first client varbit (cache 2757: `con_contract_hotspot_1_display`); slot n is this plus n. */
    const val FIRST_VARBIT = 10554
    const val SLOTS = 8

    /** Varbit states shared by every hotspot multi-loc (cache 2757: child 0 and 2 fixed, 1 broken, 3 old piece, 4 hotspot, 5..8 new). */
    const val FIXED = 0
    const val REPAIR = 1
    const val REPLACE = 3
    const val BUILD = 4

    val HOMES: List<MahoganyHome> =
        listOf(
            MahoganyHome("Jess", "East Ardougne", 10421),
            MahoganyHome("Noella", "East Ardougne", 10419),
            MahoganyHome("Ross", "East Ardougne", 10420),
            MahoganyHome("Larry", "Falador", 10418),
            MahoganyHome("Norman", "Falador", NORMAN_NPC),
            MahoganyHome("Tau", "Falador", 10417),
            MahoganyHome("Barbara", "Hosidius", 10424),
            MahoganyHome("Leela", "Hosidius", 10423),
            MahoganyHome("Mariah", "Hosidius", 10422),
            MahoganyHome("Bob", "Varrock", 10414),
            MahoganyHome("Jeff", "Varrock", 10415),
            MahoganyHome("Sarah", "Varrock", 10416),
        )

    /** Cache 2757 has no `con_contract_client` npc for Norman; the plain npc 3266 "Norman" is used (OPEN-CO14). */
    private const val NORMAN_NPC = 3266

    private val TIERS: Map<String, MahoganyTier> = MahoganyTier.entries.associateBy { it.label }

    /** Layouts by tier then home (Wiki "Mahogany Homes/Mechanics"). */
    val LAYOUTS: Map<MahoganyTier, List<List<MahoganyLayout>>> by lazy { loadLayouts() }

    /** Hotspots by parent loc id (`mahogany-hotspots.tsv`: the multi-loc parents with varbit 10554..10561 placed in the four towns). */
    val HOTSPOTS: Map<Int, List<MahoganyHotspot>> by lazy { loadHotspots() }

    private fun loadLayouts(): Map<MahoganyTier, List<List<MahoganyLayout>>> {
        val out = MahoganyTier.entries.associateWith { List(HOMES.size) { ArrayList<MahoganyLayout>() } }
        for (c in rows("mahogany-layouts.tsv")) {
            val tier = TIERS.getValue(c[0])
            val home = HOMES.indexOfFirst { it.name == c[1] }
            require(home >= 0) { "mahogany-layouts.tsv: unknown home ${c[1]}" }
            val slots = ArrayList<MahoganySlot>()
            for (i in 0 until SLOTS) {
                val cell = c[SLOT_COLUMN + i]
                if (cell.isEmpty()) continue
                val part = cell.split(':', limit = 4)
                slots += MahoganySlot(i, part[0].toInt(), part[1].toInt(), part[2] == "B", part[3])
            }
            out.getValue(tier)[home].add(MahoganyLayout(tier, home, slots))
        }
        return out
    }

    private fun loadHotspots(): Map<Int, List<MahoganyHotspot>> =
        rows("mahogany-hotspots.tsv")
            .map { c ->
                val home = HOMES.indexOfFirst { it.name == c[0] }
                require(home >= 0) { "mahogany-hotspots.tsv: unknown home ${c[0]}" }
                MahoganyHotspot(home, c[1].toInt(), c[2].toInt(), c[3].toInt(), c[4].toInt(), c[5].toInt() - FIRST_VARBIT)
            }.groupBy { it.loc }

    /** Tier, home, region, layout number, then the eight slot cells (`mahogany-layouts.tsv`). */
    private const val SLOT_COLUMN = 4

    private fun rows(file: String): List<List<String>> {
        val text = MahoganyData::class.java.getResource(file)?.readText() ?: error("$file missing")
        return text
            .lineSequence()
            .filter { it.isNotBlank() }
            .map { it.split('\t') }
            .toList()
    }
}
