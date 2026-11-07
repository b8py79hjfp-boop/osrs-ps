package tp.content.sailing

import tp.plugin.ButtonClick
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.WorldRef
import tp.plugin.chatNpc
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.util.CoordGrid
import kotlin.math.max

/**
 * Sea charting (docs/mechanics/sailing-charting.md C1-C12): the 358 tasks of `Sailing_Charting_Core`, six tool types. A
 * task is done once; it pays the type's XP, then the bonus of every sea whose tasks are now all done, and the ocean bonuses
 * are claimed from Chartin' Charles McAtless. State is [ChartingSave]; the captain's log reads it from the completion varps.
 *
 *  - Generic: op "Chart" on a charting loc (the cache map places them at the task tiles), with the log in the backpack.
 *  - Spyglass: "Look-through" on the spyglass while the player or their boat stands within [SPOT_RADIUS] of the task tile.
 *  - Current: "Track-current" on the duck within [SPOT_RADIUS] of the start tile; the duck comes to rest at the end tile
 *    ([DUCK_TICKS_PER_TILE] ticks per tile away), where "Collect" on it completes the task; it vanishes [DUCK_LIFETIME] later.
 *  - Crate: "Pry-open" on the sealed crate at the task tile with a crowbar, then the choice to drink.
 *  - Diving: a mermaid guide at the task tile; "Dive" with the diving gear or the Medallion of the Deep.
 *  - Weather: a Meaty Aura Logist gives a station and a task; "Take-reading" at the task tile fills it; "Collect-data" hands it in.
 */
internal class SeaCharting(private val state: SailingState) {
    private lateinit var data: ChartingData
    private lateinit var tasks: Map<Int, ChartingTask>
    private lateinit var siteAt: Map<Long, ChartingSite>
    private lateinit var oceans: List<String>
    private lateinit var seaTasks: Map<Int, List<ChartingTask>>

    /** Pending timed events (duck arrival and despawn), processed by the world tick. */
    private val due = ArrayList<Due>()

    private class Due(val tick: Long, val action: () -> Unit)

    fun ContentScope.register() {
        onWorldStart { prepare(this) }
        onLogin { ChartingSave.mirror(this) }
        for (loc in Locs.SAILING_CHARTING_GENERIC_ROCK_BAY_OF_SARIM..Locs.SAILING_CHARTING_GENERIC_CRAB_HOLE) {
            onOpLoc(loc, OP_FIRST) { chartGeneric(player, it) }
        }
        onOpLoc(Locs.SAILING_CHARTING_DRINK_CRATE, OP_FIRST) { pry(it) }
        onItemOp(Objs.SAILING_CHARTING_SPYGLASS, "Look-through") { if (holds(it)) lookThrough(this) }
        onItemOp(Objs.SAILING_CHARTING_CURRENT_DUCK, "Track-current") { if (holds(it)) trackCurrent(this) }
        onItemOp(Objs.SAILING_CHARTING_WEATHER_STATION_EMPTY, "Take-reading") { if (holds(it)) takeReading(this, it.slot) }
        onOpNpc(Npcs.SAILING_CHARTING_CURRENT_DUCK_STOPPED, OP_FIRST) { collectDuck(player, it) }
        for (guide in Npcs.SAILING_CHARTING_MERMAID_GUIDE_1..Npcs.SAILING_CHARTING_MERMAID_GUIDE_5) {
            onOpNpc(
                guide,
                OP_FIRST,
            ) { chatNpc(it, "Come back wearing something that lets you breathe down there, and I will take you diving.") }
            onOpNpc(guide, OP_THIRD) { dive(player, it) }
        }
        onOpNpc(Npcs.SAILING_CHARTING_WEATHER_TROLL, OP_FIRST) { troll(it) }
        onOpNpc(Npcs.SAILING_CHARTING_WEATHER_TROLL, OP_THIRD) { handIn(player, it) }
        onOpNpc(Npcs.SAILING_CHARTING_TUTOR_2OP, OP_FIRST) { charles(it) }
        onOpNpc(Npcs.SAILING_CHARTING_TUTOR_2OP, OP_THIRD) { claimOceans(player) }
        onWorldTick { runDue(this) }
    }

    // ---- set-up

    private fun prepare(world: WorldRef) {
        data = ChartingData.load()
        tasks = state.data.chartingTasks.associateBy { it.uniqueId }
        check(tasks.keys == data.sites.keys) { "charting-tasks.tsv and Sailing_Charting_Core list different tasks" }
        siteAt = data.sites.values.associateBy { key(it.tile, it.type) }
        oceans =
            data.bonuses.ocean.keys
                .sorted()
        seaTasks =
            buildMap<Int, MutableList<ChartingTask>> {
                for (task in tasks.values) {
                    getOrPut(task.seaRow) { ArrayList() }.add(task)
                    task.secondarySeaRow?.let { getOrPut(it) { ArrayList() }.add(task) }
                }
            }
        // The cache map places the generic locs; the sealed crates and the mermaid guides are server-placed (C9).
        var guide = 0
        for (site in data.sites.values.sortedBy { it.uniqueId }) {
            when (site.type) {
                ChartingType.DRINK_CRATE -> {
                    world.addLoc(Locs.SAILING_CHARTING_DRINK_CRATE, site.tile, CENTREPIECE, 0)
                }

                ChartingType.MERMAID_GUIDE -> {
                    world.spawnNpc(Npcs.SAILING_CHARTING_MERMAID_GUIDE_1 + guide % GUIDE_VARIANTS, site.tile, 0)
                    guide++
                }

                else -> {
                    Unit
                }
            }
        }
        for ((x, z) in TROLLS) world.spawnNpc(Npcs.SAILING_CHARTING_WEATHER_TROLL, CoordGrid(x, z, 0), 0)
        world.spawnNpc(Npcs.SAILING_CHARTING_TUTOR_2OP, CHARLES, 0)
    }

    private fun key(tile: CoordGrid, type: ChartingType): Long =
        (tile.x.toLong() shl KEY_X_SHIFT) or (tile.z.toLong() shl KEY_Z_SHIFT) or type.ordinal.toLong()

    private fun siteOf(tile: CoordGrid, type: ChartingType): ChartingSite? = siteAt[key(tile, type)]

    /** The nearest site of [type] to [at] within [radius] tiles that [p] has not done, else the nearest done one, else null. */
    private fun nearest(p: PlayerRef, type: ChartingType, at: CoordGrid, radius: Int): ChartingSite? {
        val inRange = data.sites.values.filter { it.type == type && chebyshev(it.tile, at) <= radius }
        return inRange.filter { !ChartingSave.isDone(p, it.uniqueId) }.minByOrNull { chebyshev(it.tile, at) }
            ?: inRange.minByOrNull { chebyshev(it.tile, at) }
    }

    /** Where the player stands for charting: their boat's pivot aboard, else their own tile (Wiki Spyglass: player or boat). */
    private fun position(p: PlayerRef): CoordGrid = ActiveBoats.of(p)?.ref?.rootCoord ?: p.coord

    private fun PlayerRef.holds(click: ButtonClick): Boolean = click.slot >= 0 && inv(Invs.INV).obj(click.slot) == click.obj

    // ---- completing

    private fun hasLog(p: PlayerRef): Boolean {
        val inv = p.inv(Invs.INV)
        return inv.total(Objs.SAILING_LOG) + inv.total(Objs.SAILING_LOG_INITIAL) > 0
    }

    private fun carries(p: PlayerRef, obj: Int): Boolean = p.inv(Invs.INV).total(obj) + p.inv(Invs.WORN).total(obj) > 0

    /** The level check of a task. Charting levels are boostable (Wiki Sea charting, 10 Dec 2025). */
    private fun levelProblem(p: PlayerRef, site: ChartingSite): String? =
        if (p.level(Stats.SAILING) < site.level) "You need a Sailing level of ${site.level} to chart this." else null

    private fun complete(p: PlayerRef, site: ChartingSite, text: String) {
        if (ChartingSave.isDone(p, site.uniqueId)) return p.message("You have already charted this.")
        ChartingSave.setDone(p, site.uniqueId)
        p.message(text)
        p.addXp(Stats.SAILING, XP.getValue(site.type).toDouble())
        val task = tasks.getValue(site.uniqueId)
        for (sea in listOfNotNull(task.seaRow, task.secondarySeaRow)) paySea(p, sea)
    }

    /** C7: the bonus of a sea when its last task is done (OPEN-C10: whether the Wiki's sea bonus is paid at once). */
    private fun paySea(p: PlayerRef, seaRow: Int) {
        val sea = state.data.seas.firstOrNull { it.row == seaRow } ?: return
        if (ChartingSave.seaPaid(p, sea.uniqueId)) return
        if (seaTasks[seaRow].orEmpty().any { !ChartingSave.isDone(p, it.uniqueId) }) return
        val bonus = data.bonuses.sea[ChartingBonuses.normalise(sea.name)] ?: return
        ChartingSave.setSeaPaid(p, sea.uniqueId)
        p.message("You have charted every part of ${sea.name}.")
        p.addXp(Stats.SAILING, bonus.toDouble())
    }

    // ---- generic

    private fun chartGeneric(p: PlayerRef, loc: LocRef) {
        val site = siteOf(loc.coord, ChartingType.GENERIC) ?: return p.message("Nothing interesting happens.")
        levelProblem(p, site)?.let { return p.message(it) }
        if (!hasLog(p)) return p.message("You need your captain's log to chart this.")
        complete(
            p,
            site,
            "You chart ${tasks.getValue(site.uniqueId).hint.removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }}.",
        )
    }

    /** Task 0, "Look at the port task board in Port Sarim": the board at [at] was opened. */
    fun boardOpened(p: PlayerRef, at: CoordGrid) = touch(p, at, BOARD_TASK, "You look over the port task board and add it to your chart.")

    /** Task 1, "Interact with the salvaging station in Port Sarim": the station at [at] was used. */
    fun stationUsed(
        p: PlayerRef,
        at: CoordGrid,
    ) = touch(p, at, STATION_TASK, "You look over the salvaging station and add it to your chart.")

    /** Charts generic task [task] when [at] is within [TOUCH_RADIUS] of its tile and the player carries the log; silent otherwise. */
    private fun touch(p: PlayerRef, at: CoordGrid, task: Int, text: String) {
        val site = data.sites[task] ?: return
        if (chebyshev(site.tile, at) > TOUCH_RADIUS || ChartingSave.isDone(p, task) || !hasLog(p)) return
        if (levelProblem(p, site) == null) complete(p, site, text)
    }

    // ---- spyglass

    private fun lookThrough(p: PlayerRef) {
        val site =
            nearest(p, ChartingType.SPYGLASS, position(p), SPOT_RADIUS) ?: return p.message("There is nothing worth viewing from here.")
        levelProblem(p, site)?.let { return p.message(it) }
        complete(p, site, "You look through the spyglass and take in the view.")
    }

    // ---- current duck

    private fun trackCurrent(p: PlayerRef) {
        val site =
            nearest(p, ChartingType.CURRENT_DUCK, position(p), SPOT_RADIUS) ?: return p.message("There are no currents to track here.")
        levelProblem(p, site)?.let { return p.message(it) }
        if (!site.hasEnd) return p.message("The duck bobs in place.")
        if (p[DUCK_OUT] != null) return p.message("Your current duck is already in the water.")
        p[DUCK_OUT] = site.uniqueId
        p.message("You set your current duck down in the water.")
        val travel = max(1, chebyshev(site.tile, site.endTile)) * DUCK_TICKS_PER_TILE
        val now = p.tick
        due += Due(now + travel) { duckStops(p, site) }
    }

    private fun duckStops(p: PlayerRef, site: ChartingSite) {
        val duck = p.spawnNpc(Npcs.SAILING_CHARTING_CURRENT_DUCK_STOPPED, site.endTile, 0)
        p[DUCK_AT] = duck.index
        p.message("<col=0000ff>Your current duck comes to a stop.</col>")
        due += Due(p.tick + DUCK_LIFETIME) { vanish(p, duck) }
    }

    private fun vanish(p: PlayerRef, duck: NpcRef) {
        if (duck.isInWorld) duck.despawn()
        if (p[DUCK_AT] == duck.index) {
            p[DUCK_AT] = null
            p[DUCK_OUT] = null
        }
    }

    private fun collectDuck(p: PlayerRef, duck: NpcRef) {
        val id = p[DUCK_OUT]
        if (id == null || p[DUCK_AT] != duck.index) return p.message("This isn't your duck.")
        val site = data.sites[id] ?: return
        p[DUCK_OUT] = null
        p[DUCK_AT] = null
        duck.despawn()
        complete(p, site, "You collect your current duck and note down the currents.")
    }

    // ---- sealed crate

    private suspend fun PlayerScript.pry(loc: LocRef) {
        val p = player
        val site = siteOf(loc.coord, ChartingType.DRINK_CRATE) ?: return p.message("Nothing interesting happens.")
        levelProblem(p, site)?.let { return p.message(it) }
        if (!carries(p, Objs.SAILING_CHARTING_CROWBAR)) return p.message("You need a crowbar to pry this open.")
        p.message("You pry open the sealed crate.")
        // OPEN-C7: the drink of each crate and its effect are server data the cache strips.
        if (choice("Drink what is inside?", "Drink it", "Leave it") == 1) {
            complete(p, site, "You sample the drink inside.")
        }
    }

    // ---- diving

    private fun dive(p: PlayerRef, guide: NpcRef) {
        val site = siteOf(guide.spawn, ChartingType.MERMAID_GUIDE) ?: return
        levelProblem(p, site)?.let { return p.message(it) }
        val geared =
            carries(p, Objs.MEDALLION_OF_THE_DEEP) ||
                (carries(p, Objs.HUNDRED_PIRATE_DIVING_HELMET) && carries(p, Objs.HUNDRED_PIRATE_DIVING_BACKPACK))
        if (!geared) return p.message("You need a fishbowl helmet and diving apparatus, or the Medallion of the Deep, to dive.")
        // OPEN-C5: the mermaid's riddle is not asked.
        complete(p, site, "You dive with the mermaid and record the depths.")
    }

    // ---- weather

    private suspend fun PlayerScript.troll(troll: NpcRef) {
        val p = player
        val station = Objs.SAILING_CHARTING_WEATHER_STATION_EMPTY
        if (carries(p, station) || carries(p, Objs.SAILING_CHARTING_WEATHER_STATION_FULL)) {
            return chatNpc(troll, "You already have my weather station. Take it where the wind stops, then bring it back.")
        }
        val site = nearest(p, ChartingType.WEATHER_TROLL, troll.coord, WEATHER_RANGE)
        if (site == null || ChartingSave.isDone(p, site.uniqueId)) {
            return chatNpc(troll, "I have no more weather for you to find.")
        }
        levelProblem(p, site)?.let { return p.message(it) }
        if (!p.transaction("weather station") { add(Invs.INV, station, 1) }) return p.message("You have no room for the weather station.")
        p.setSaved(WEATHER_KEY, site.uniqueId + 1L)
        chatNpc(troll, "Take this to where the wind has stopped, and bring it back. I cannot tell you where that is.")
    }

    private fun takeReading(p: PlayerRef, slot: Int) {
        val task = (p.saved(WEATHER_KEY) ?: 0L).toInt() - 1
        val site =
            data.sites[task]?.takeIf { it.type == ChartingType.WEATHER_TROLL }
                ?: return p.message("Nobody has asked you to measure the weather.")
        levelProblem(p, site)?.let { return p.message(it) }
        if (chebyshev(position(p), site.tile) > SPOT_RADIUS) return p.message("The station shows wind here. The weather you want is calm.")
        val done =
            p.transaction("weather reading") {
                replaceAt(Invs.INV, slot, Objs.SAILING_CHARTING_WEATHER_STATION_EMPTY, Objs.SAILING_CHARTING_WEATHER_STATION_FULL)
            }
        if (done) p.message("The station records the still air.")
    }

    private fun handIn(p: PlayerRef, troll: NpcRef) {
        val task = (p.saved(WEATHER_KEY) ?: 0L).toInt() - 1
        val site = data.sites[task]?.takeIf { it.type == ChartingType.WEATHER_TROLL }
        if (site == null ||
            !carries(p, Objs.SAILING_CHARTING_WEATHER_STATION_FULL)
        ) {
            return p.message("You have no weather data for ${troll.displayName}.")
        }
        if (!p.transaction("weather hand-in") { remove(Invs.INV, Objs.SAILING_CHARTING_WEATHER_STATION_FULL, 1) }) return
        p.setSaved(WEATHER_KEY, null)
        complete(p, site, "You hand in the data filled weather station.")
    }

    // ---- Chartin' Charles McAtless

    private suspend fun PlayerScript.charles(npc: NpcRef) {
        val p = player
        val missing = state.data.chartingTools.firstOrNull { tool -> tool.name == "Spyglass" && !carries(p, tool.objs.first()) }
        if (missing != null && p.transaction("charles spyglass") { add(Invs.INV, missing.objs.first(), 1) }) {
            return chatNpc(npc, "Here, take a spyglass. Stand on the glowing spots at sea and look through it.")
        }
        chatNpc(npc, "Bring me your captain's log once you have charted a whole ocean, and ask me for training.")
    }

    /** C7: pays the ocean bonuses [p] has earned and not yet claimed, and the 100% bonus. */
    private fun claimOceans(p: PlayerRef) {
        if (!hasLog(p)) return p.message("Bring your captain's log to claim the bonus.")
        var paid = false
        for ((index, ocean) in oceans.withIndex()) {
            val (xp, level) = data.bonuses.ocean.getValue(ocean)
            if (ChartingSave.claimed(p, index)) continue
            if (data.sites.values.any { it.ocean == ocean && !ChartingSave.isDone(p, it.uniqueId) }) continue
            if (p.level(Stats.SAILING) < level) {
                p.message("You need a Sailing level of $level to claim the bonus for charting ${oceanLabel(ocean)}.")
                continue
            }
            ChartingSave.setClaimed(p, index)
            p.addXp(Stats.SAILING, xp.toDouble())
            p.message("You claim the bonus for charting ${oceanLabel(ocean)}.")
            paid = true
        }
        val allDone = data.sites.keys.all { ChartingSave.isDone(p, it) }
        if (allDone && !ChartingSave.claimed(p, FULL_CLAIM)) {
            if (p.level(Stats.SAILING) < FULL_LEVEL) {
                p.message("You need a Sailing level of $FULL_LEVEL to claim the bonus for charting everything.")
            } else {
                ChartingSave.setClaimed(p, FULL_CLAIM)
                p.addXp(Stats.SAILING, data.bonuses.full.toDouble())
                p.message("You claim the bonus for charting every part of the sea.")
                paid = true
            }
        }
        if (!paid) p.message("You have no completed ocean to claim a bonus for.")
    }

    private fun runDue(world: WorldRef) {
        if (due.isEmpty()) return
        val now = world.tick
        var i = 0
        while (i < due.size) {
            val event = due[i]
            if (event.tick <= now) {
                due.removeAt(i)
                event.action()
            } else {
                i++
            }
        }
    }

    private fun oceanLabel(ocean: String): String = if (ocean == ChartingBonuses.MISC) "the miscellaneous charts" else ocean

    private fun chebyshev(a: CoordGrid, b: CoordGrid): Int = max(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))

    private companion object {
        val DUCK_OUT = tp.plugin.AttrKey<Int>("sailing.duck.task")
        val DUCK_AT = tp.plugin.AttrKey<Int>("sailing.duck.npc")

        /** Saved: `UNIQUE_ID` + 1 of the weather task a Meaty Aura Logist gave the player, absent if none. */
        const val WEATHER_KEY = "sailing.chart.weather"

        /** Bit shifts of the (x, z, type) key of [siteAt]: z and the type ordinal fit the 24 bits below x. */
        const val KEY_X_SHIFT = 32
        const val KEY_Z_SHIFT = 8

        /** Generic tasks done by using a Port Sarim board and salvaging station instead of a charting loc (`UNIQUE_ID` 0 and 1). */
        const val BOARD_TASK = 0
        const val STATION_TASK = 1

        /** Tiles between the Wiki's tile of those two tasks and the board / station loc (they differ by one tile and a level). */
        const val TOUCH_RADIUS = 3

        const val OP_FIRST = 1
        const val OP_THIRD = 3

        /** Loc shape 10, the centrepiece shape of every placed loc of this skill (the wreck stumps use it too). */
        const val CENTREPIECE = 10

        /** Mermaid guide NPC variants `SAILING_CHARTING_MERMAID_GUIDE_1..5`, one per guide on the Wiki's riddle page. */
        const val GUIDE_VARIANTS = 5

        /**
         * OPEN-C3: how close the player's boat or feet must be to a task tile for a spyglass view, a duck set-down or a weather
         * reading. The cache's `INZONE` columns that hold the real zone are stripped.
         */
        const val SPOT_RADIUS = 3

        /** OPEN-C4: ticks the duck takes per tile of its route; the route, its speed and its end are server data. */
        const val DUCK_TICKS_PER_TILE = 1

        /** The duck despawns two minutes after it stops (Wiki Current duck): 200 game ticks of 0.6 s. */
        const val DUCK_LIFETIME = 200L

        /** OPEN-C8: how far from a troll a weather task counts as hers; the task-to-troll link is server data. */
        const val WEATHER_RANGE = 400

        /** The 100% bonus needs Sailing 78 (Wiki Sea charting, Experience) and is bit 31 of the claims. */
        const val FULL_LEVEL = 78
        const val FULL_CLAIM = 31

        /** One-off XP per task type (docs/mechanics/sailing-charting.md C2; Wiki Sea charting, Tools). */
        val XP: Map<ChartingType, Int> =
            mapOf(
                ChartingType.GENERIC to 35,
                ChartingType.SPYGLASS to 50,
                ChartingType.DRINK_CRATE to 75,
                ChartingType.CURRENT_DUCK to 125,
                ChartingType.MERMAID_GUIDE to 175,
                ChartingType.WEATHER_TROLL to 250,
            )

        /** Tiles of the Meaty Aura Logist (Wiki infobox map pins; 20 of her 31 places). */
        val TROLLS: List<Pair<Int, Int>> =
            listOf(
                2903 to 2698,
                2140 to 2806,
                2979 to 2905,
                2335 to 2921,
                1779 to 2940,
                2638 to 3009,
                2149 to 3068,
                1714 to 3193,
                2656 to 3195,
                2912 to 3213,
                2130 to 3395,
                1867 to 3458,
                1651 to 3461,
                2280 to 3518,
                2498 to 3546,
                2594 to 3644,
                2341 to 3790,
                2600 to 3910,
                2951 to 3962,
                2447 to 3995,
            )

        /** OPEN-C11: Charles stands in the Pandemonium Cave; the tile is the Wiki's hint tile of the spyglass task that views him. */
        val CHARLES = CoordGrid(3051, 9388, 0)
    }
}
