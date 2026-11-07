package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs

/**
 * Forester's campfire (docs/mechanics/firemaking.md FM8, Wiki "Forester's Campfire"): a log used on a fire turns it into a
 * campfire that burns for the log's duration, and logs added with "Tend-to" extend it up to [MAX_BURN_TICKS]. Each log
 * gives its normal Firemaking xp.
 */
public class ForesterCampfire : ContentPlugin {
    /** A world's campfires by packed tile: the tick each stops burning. */
    private class Campfire(var expiry: Long)

    override fun ContentScope.register() {
        for (log in BURNABLE_LOGS) {
            if (log.obj !in ADDED_TICKS) continue
            for (fire in BASE_TO_CAMPFIRE.keys + BASE_TO_CAMPFIRE.values) {
                onUseOnLoc(log.obj, fire) { loc -> feed(player, loc, log) }
            }
        }
        for (campfire in BASE_TO_CAMPFIRE.values) {
            onOpLoc(campfire, OP_TEND) { loc -> tend(loc) }
            onOpLoc(campfire, OP_CHECK) { loc -> player.check(loc) }
        }
    }

    private fun feed(p: PlayerRef, loc: LocRef, log: BurnableLog): Boolean {
        val added = ADDED_TICKS[log.obj] ?: return false
        if (p.level(Stats.FIREMAKING) < log.level) {
            p.message("You need a Firemaking level of ${log.level} to burn ${p.objName(log.obj).lowercase()}.")
            return false
        }
        val fires = fires(p)
        val existing = fires[loc.coord.packed]?.takeIf { it.expiry > p.tick && loc.id in BASE_TO_CAMPFIRE.values }
        if (existing == null) {
            if (p.inv(Invs.INV).total(Objs.TINDERBOX) == 0L) {
                p.message("You need a tinderbox to light a fire.")
                return false
            }
            if (nearby(p, fires, loc)) {
                p.message("There's a Forester's Campfire nearby, help tend to that one or move further away.")
                return false
            }
        }
        if (!p.transaction("forester campfire") { remove(Invs.INV, log.obj, 1) }) return false
        val remaining = if (existing == null) BASE_BURN_TICKS + added else minOf(MAX_BURN_TICKS, (existing.expiry - p.tick).toInt() + added)
        val expiry = p.tick + remaining
        fires.getOrPut(loc.coord.packed) { Campfire(expiry) }.expiry = expiry
        val campfire = BASE_TO_CAMPFIRE[loc.id] ?: loc.id
        p.addLoc(campfire, loc.coord, loc.shape, loc.angle, remaining)
        OutfitBonuses.addFiremakingXp(p, log.xp)
        p.queueHit(remaining, strong = false) {
            // Only the latest feeding leaves ashes; an extended fire's earlier timers see a later expiry.
            if (fires[loc.coord.packed]?.expiry == expiry) {
                fires.remove(loc.coord.packed)
                p.transaction("campfire ashes") { createOnGround(Objs.ASHES, 1, loc.coord, p.username) }
            }
        }
        return true
    }

    /** "Tend-to": keeps adding the best logs in the backpack every [TEND_TICKS] until the fire is full, gone or the logs run out. */
    private suspend fun PlayerScript.tend(loc: LocRef) {
        val p = player
        while (p.locAt(loc.coord, loc.shape)?.id == loc.id) {
            val log = bestLog(p) ?: return p.message("You have no logs to add to the fire.")
            if ((fires(p)[loc.coord.packed]?.expiry ?: 0L) - p.tick >= MAX_BURN_TICKS) return
            if (!feed(p, loc, log)) return
            delay(TEND_TICKS)
        }
    }

    private fun bestLog(p: PlayerRef): BurnableLog? {
        val inv = p.inv(Invs.INV)
        val level = p.level(Stats.FIREMAKING)
        return BURNABLE_LOGS.filter { it.obj in ADDED_TICKS && it.level <= level && inv.total(it.obj) > 0 }.maxByOrNull { it.level }
    }

    private fun PlayerRef.check(loc: LocRef) {
        val remaining = (fires(this)[loc.coord.packed]?.expiry ?: tick) - tick
        val tier = TIER_LIMITS.indexOfFirst { remaining <= it }.let { if (it < 0) TIER_LIMITS.size else it }
        message(CHECK_MESSAGES[tier])
    }

    /** Wiki: campfires need "at least 5 tiles between them"; read as a Chebyshev distance of more than [MIN_SPACING] (OPEN-FM9). */
    private fun nearby(p: PlayerRef, fires: HashMap<Int, Campfire>, loc: LocRef): Boolean =
        fires.entries.any { (packed, fire) ->
            if (fire.expiry <= p.tick || packed == loc.coord.packed) return@any false
            val other = tp.util.CoordGrid(packed)
            other.level == loc.coord.level &&
                maxOf(kotlin.math.abs(other.x - loc.coord.x), kotlin.math.abs(other.z - loc.coord.z)) <= MIN_SPACING
        }

    private fun fires(p: PlayerRef): HashMap<Int, Campfire> =
        p.worldAttr(FIRES) ?: HashMap<Int, Campfire>().also { p.setWorldAttr(FIRES, it) }

    private companion object {
        val FIRES = AttrKey<HashMap<Int, Campfire>>("forester.campfires")

        /** Cache ops of the campfire loc: "Tend-to", "Check", "Rest" (Wiki infobox). */
        const val OP_TEND = 1
        const val OP_CHECK = 2

        /** Normal and coloured fire locs and the campfire loc each becomes (cache `forestry_fire*`, in colour order). */
        val BASE_TO_CAMPFIRE: Map<Int, Int> =
            mapOf(
                Locs.FIRE to Locs.FORESTRY_FIRE,
                Locs.RED_FIRE to Locs.FORESTRY_FIRE_RED,
                Locs.GREEN_FIRE to Locs.FORESTRY_FIRE_GREEN,
                Locs.BLUE_FIRE to Locs.FORESTRY_FIRE_BLUE,
                Locs.TRAIL_WHITE_FIRE to Locs.FORESTRY_FIRE_WHITE,
                Locs.TRAIL_PURPLE_FIRE to Locs.FORESTRY_FIRE_PURPLE,
            )

        /** Wiki "Forester's Campfire" table, "Burn Time added (ticks)" per log; camphor, ironwood and rosewood are unknown (OPEN-FM9). */
        val ADDED_TICKS: Map<Int, Int> =
            mapOf(
                Objs.LOGS to 3,
                Objs.ACHEY_TREE_LOGS to 3,
                Objs.OAK_LOGS to 10,
                Objs.WILLOW_LOGS to 17,
                Objs.TEAK_LOGS to 19,
                Objs.ARCTIC_PINE_LOG to 22,
                Objs.MAPLE_LOGS to 24,
                Objs.MAHOGANY_LOGS to 26,
                Objs.YEW_LOGS to 31,
                Objs.BLISTERWOOD_LOGS to 32,
                Objs.MAGIC_LOGS to 38,
                Objs.REDWOOD_LOGS to 45,
            )

        /** Wiki: a fresh campfire burns 99 ticks plus the log's added time (102 for plain logs). */
        const val BASE_BURN_TICKS = 99

        /** Wiki: "up to a maximum of 300 ticks". */
        const val MAX_BURN_TICKS = 300

        /** OPEN-FM9: about 975 logs an hour with immediate tending (Wiki) is one log per 6 ticks. */
        const val TEND_TICKS = 6

        const val MIN_SPACING = 5

        /** Upper tick bounds of "Check" tiers 1 to 4 (Wiki: 34.8 s, 70.8 s, 106.8 s, 142.8 s at 0.6 s per tick). */
        val TIER_LIMITS = intArrayOf(58, 118, 178, 238)
        val CHECK_MESSAGES =
            arrayOf(
                "The embers glow softly.",
                "The flames flicker gently.",
                "The fire burns steadily.",
                "The fire burns brightly.",
                "The roaring fire crackles invitingly.",
            )
    }
}
