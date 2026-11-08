package tp.content.raids.tob

import tp.content.raids.RaidMember
import tp.content.raids.RaidRun
import tp.plugin.EntityRef
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.symbols.Locs
import tp.util.CoordGrid

/**
 * The midway supply chests after Bloat and Sotetseg (tob.md TB22 / TB23; lobby-rewards LR9-LR13). The chest is a server
 * spawn (client-contract 2.7: 32758 "Open" swaps to 32759 "Search"); the stores interface is [TobStoresUi]. Normal /
 * Hard members are credited points by their own performance, which carry over to the second chest; Entry has no points
 * (its chest holds Bandages, see [TobStoresUi]).
 */
internal object TobSupplies {
    /** Spawns the room's supply chest and credits each member's points (Entry: no points, 10 Bandages instead). */
    fun open(run: RaidRun, any: EntityRef, room: String) {
        val at = CHESTS[room] ?: return
        any.addLoc(Locs.TOB_MIDWAY_CHEST_CLOSED, run.at(at), SHAPE_CENTREPIECE, 0)
        if (run.scaling.mode == TobRaid.ENTRY) return
        val state = TobFlow.state(run)
        val present = run.members.values.filter { it.inside }
        val scores = present.associate { it.name to score(state, it) }
        val total = scores.values.sum()
        for (m in present) {
            // Compared with the team average without dividing: score x members against the sum of scores.
            val gap = scores.getValue(m.name) * present.size - total
            val points = pointsFor(gap, m.deaths)
            state.supplyPoints.merge(m.name, points) { have, add -> minOf(MAX_POINTS, have + add) }
        }
        for (p in run.presentPlayers(any)) TobStoresUi.sync(p, run)
    }

    /** The chest [loc] is one of [run]'s two supply chests: its room, or null. */
    fun roomAt(run: RaidRun, loc: LocRef): String? = CHESTS.entries.firstOrNull { run.at(it.value) == loc.coord }?.key

    /** A member used the chest: "Open" (the closed chest) swaps it to its "Search" form first. */
    fun use(player: PlayerRef, loc: LocRef, opening: Boolean) {
        val run = TobFlow.runOf(player) ?: return
        val room = roomAt(run, loc) ?: return
        if (opening) player.addLoc(Locs.TOB_MIDWAY_CHEST_OPEN, loc.coord, loc.shape, loc.angle)
        TobStoresUi.open(player, run, room)
    }

    /**
     * OPEN-LR5: a member's standing so far, from the terms of the reward points (LR16: MVP points minus 4 per death).
     * The in-game midway formula is not published.
     */
    private fun score(state: TobState, m: RaidMember): Int {
        val mvp =
            state.mvp.entries
                .firstOrNull { it.key.equals(m.name, ignoreCase = true) }
                ?.value ?: 0
        return mvp - DEATH_PENALTY * m.deaths
    }

    /**
     * OPEN-LR5: LR10's tiers. [gap] is the member's score against the team average (positive = above): above average
     * 10-13, average 8-11, below average 6-9. Inside the tier a deathless member gets the top and each death takes one
     * point off, down to the tier's bottom. Model "flat" gives everyone the average tier's bottom.
     */
    private fun pointsFor(gap: Int, deaths: Int): Int {
        if (TobConfig.supplyPointsModel == TobConfig.MODEL_FLAT) return AVERAGE.first
        val tier =
            when {
                gap > 0 -> ABOVE
                gap < 0 -> BELOW
                else -> AVERAGE
            }
        return (tier.last - deaths).coerceIn(tier.first, tier.last)
    }

    /** LR16: the reward points lose 4 per death. */
    private const val DEATH_PENALTY = 4

    /** LR10 (Strat page): points per chest by performance against the team. */
    private val BELOW = 6..9
    private val AVERAGE = 8..11
    private val ABOVE = 10..13

    /** Client-contract 1.2: varbit 6460 `TOB_MIDWAYCHEST_POINTS` is 5 bits wide (0..31). */
    const val MAX_POINTS = 31

    /** Loc shape 10: a centrepiece (the cache places 32736 / 32996 / 33014 with it, client-contract 5). */
    const val SHAPE_CENTREPIECE = 10

    /** Bloat's chest (client-contract 5, Wiki pin). */
    val BLOAT_CHEST = CoordGrid(3269, 4449)

    /**
     * OPEN-LR15: no source places Sotetseg's chest. It stands on the free tile north of the arena barrier (client-contract
     * 5: (3279, 4308) is free), where the party arrives from.
     */
    val SOTETSEG_CHEST = CoordGrid(3279, 4308)

    private val CHESTS: Map<String, CoordGrid> = mapOf("bloat" to BLOAT_CHEST, "sotetseg" to SOTETSEG_CHEST)
}
