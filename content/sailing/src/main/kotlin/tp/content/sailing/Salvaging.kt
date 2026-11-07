package tp.content.sailing

import tp.plugin.AttrKey
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.WorldRef
import tp.symbols.Invs
import tp.util.CoordGrid
import kotlin.math.abs
import kotlin.math.max

/**
 * Shipwreck salvaging and sorting (docs/mechanics/sailing-salvaging.md S1-S10).
 *
 * The cache map holds a stump on each of the 186 wreck sites. At world start every cluster of sites (a hotspot) shows its
 * wrecks on all but [INACTIVE_PER_CLUSTER] of them. A player aboard a boat deploys a salvaging hook ("Deploy"); every
 * [PLAYER_CYCLE] ticks, while a wreck stands within [HOOK_RANGE] tiles of the boat, the hook rolls for a piece of salvage of
 * the wreck's type and pays the salvage XP. The first roll on a wreck starts its life timer; when it ends the wreck sinks to
 * its stump and another site of the cluster rises. Salvage is sorted at a salvaging station (a port's, or the boat facility):
 * one piece per [SORT_TICKS] ticks pays the sorting XP and rolls the Wiki drop table.
 */
internal class Salvaging(private val state: SailingState) {
    private class Wreck(val site: WreckSite, var up: Boolean) {
        /** Tick at which the wreck sinks, or 0 while nobody has started to salvage it. */
        var deadline: Long = 0
        var starter: PlayerRef? = null
    }

    /** A player's run on one wreck: when the next roll is due. */
    private class Run(val wreck: Wreck, var next: Long)

    private lateinit var data: SalvageData
    private lateinit var clusters: List<List<Wreck>>
    private lateinit var objOf: Map<String, Int>
    private lateinit var hooks: List<BoatFacility>
    private val timed = ArrayList<Wreck>()

    fun ContentScope.register() {
        onWorldStart { prepare(this) }
        for ((tier, name) in HOOK_NAMES.withIndex()) onOpLocNamed(name, OP_DEPLOY_TEXT) { deploy(tier) }
        onOpLocNamed(STATION_NAME, OP_SORT_TEXT) {
            state.charting.stationUsed(player, it.coord)
            sort()
        }
        onWorldTick { sinkExpired(this) }
    }

    private fun prepare(world: WorldRef) {
        data = SalvageData.load()
        val unresolved = data.loot.unresolved(world)
        check(unresolved.isEmpty()) { "salvage-loot.tsv items missing from the cache: $unresolved" }
        objOf =
            data.loot
                .names()
                .mapNotNull { name -> SalvageLoot.resolve(world, name)?.let { name to it } }
                .toMap()
        // One cache row per tier and boat size (28); the tier is its Sailing level.
        hooks =
            state.data.facilities
                .filter { it.subtype == HOOK_SUBTYPE }
                .sortedBy { it.requirements.sailing }
                .distinctBy { it.requirements.sailing }
        check(hooks.size == HOOK_NAMES.size) { "expected ${HOOK_NAMES.size} salvaging hook tiers, the cache has ${hooks.size}" }
        clusters =
            data.sites.groupBy { it.type to it.cluster }.values.map { sites ->
                val up = max(1, sites.size - INACTIVE_PER_CLUSTER)
                sites.mapIndexed { i, site -> Wreck(site, i < up) }
            }
        for (wreck in clusters.flatten()) if (wreck.up) show(world, wreck)
    }

    private fun show(world: WorldRef, wreck: Wreck) = world.addLoc(wreck.site.type.wreck, tileOf(wreck), SHAPE, wreck.site.angle)

    private fun sink(world: WorldRef, wreck: Wreck) = world.addLoc(wreck.site.type.stump, tileOf(wreck), SHAPE, wreck.site.angle)

    private fun tileOf(wreck: Wreck): CoordGrid = CoordGrid(wreck.site.x, wreck.site.z, wreck.site.level)

    // ---- hooks

    private fun PlayerScript.deploy(tier: Int) {
        val p = player
        val boat = ActiveBoats.of(p) ?: return p.message("You need to be aboard a boat to use the hook.")
        val hook = hooks[tier]
        if (!ShipbuildingData.canOperate(p.level(Stats.SAILING), hook)) {
            return p.message("You need a Sailing level of ${hook.requirements.sailing} to use this hook.")
        }
        val run = p[RUN]
        val wreck = run?.wreck?.takeIf { it.up && inRange(boat, it) } ?: nearest(boat)
        if (wreck == null) {
            p[RUN] = null
            if (run != null) {
                p.message("You salvage all you can from the shipwreck before it is reclaimed by the sea.")
            } else {
                p.message("There is no shipwreck within reach of the hook.")
            }
            return p.stopInteraction()
        }
        if (p.level(Stats.SAILING) < wreck.site.type.level) {
            p.message("You need a Sailing level of ${wreck.site.type.level} to salvage this shipwreck.")
            return p.stopInteraction()
        }
        keepInteraction()
        val now = p.tick
        val current = run?.takeIf { it.wreck === wreck } ?: Run(wreck, now + PLAYER_CYCLE).also { p[RUN] = it }
        if (now < current.next) return
        current.next = now + PLAYER_CYCLE
        if (wreck.deadline == 0L) {
            wreck.deadline = now + wreck.site.type.lifeTicks
            wreck.starter = p
            timed += wreck
        }
        if (!p.statRandom(Stats.SAILING, SUCCESS_LOW + SUCCESS_LOW_PER_TIER * tier, SUCCESS_HIGH + SUCCESS_HIGH_PER_TIER * tier)) return
        val type = wreck.site.type
        if (!p.transaction("salvage") { add(Invs.INV, type.salvage, 1) }) {
            p.message("Your inventory is too full to hold any more salvage.")
            p[RUN] = null
            return p.stopInteraction()
        }
        p.addXp(Stats.SAILING, type.xp)
    }

    /** S2: whether the wreck is within [HOOK_RANGE] tiles of its edge from the boat's pivot (OPEN-SV4: not the hook's offset). */
    private fun inRange(boat: ActiveBoat, wreck: Wreck): Boolean {
        val at = boat.ref.rootCoord
        val size = wreck.site.type.size
        val dx = max(0, max(wreck.site.x - at.x, at.x - (wreck.site.x + size - 1)))
        val dz = max(0, max(wreck.site.z - at.z, at.z - (wreck.site.z + size - 1)))
        return max(dx, dz) <= HOOK_RANGE
    }

    private fun nearest(boat: ActiveBoat): Wreck? {
        val at = boat.ref.rootCoord
        return clusters
            .asSequence()
            .flatten()
            .filter { it.up && abs(it.site.x - at.x) <= SEARCH && abs(it.site.z - at.z) <= SEARCH && inRange(boat, it) }
            .minByOrNull { max(abs(it.site.x - at.x), abs(it.site.z - at.z)) }
    }

    /** S5: a wreck whose timer ran out sinks, and another site of its cluster rises (the starter's world RNG picks it). */
    private fun sinkExpired(world: WorldRef) {
        if (timed.isEmpty()) return
        val now = world.tick
        var i = 0
        while (i < timed.size) {
            val wreck = timed[i]
            if (wreck.deadline > now) {
                i++
                continue
            }
            timed.removeAt(i)
            wreck.up = false
            wreck.deadline = 0
            sink(world, wreck)
            val cluster = clusters.first { wreck in it }
            val down = cluster.filter { !it.up && it !== wreck }
            val starter = wreck.starter
            wreck.starter = null
            if (down.isNotEmpty() && starter != null) {
                val next = down[starter.random(down.size)]
                next.up = true
                show(world, next)
            } else {
                wreck.up = true
                show(world, wreck)
            }
        }
    }

    // ---- sorting

    private fun PlayerScript.sort() {
        val p = player
        val inv = p.inv(Invs.INV)
        val slot = (0 until inv.size).firstOrNull { WreckType.ofSalvage(inv.obj(it)) != null }
        if (slot == null) {
            p.message("You have no salvage to sort.")
            return p.stopInteraction()
        }
        val type = WreckType.ofSalvage(inv.obj(slot)) ?: return
        if (p.level(Stats.SAILING) < type.level) {
            p.message("You need a Sailing level of ${type.level} to sort this salvage.")
            return p.stopInteraction()
        }
        keepInteraction()
        val now = p.tick
        val next = p[SORT_NEXT]
        if (next == null) {
            p[SORT_NEXT] = now + SORT_TICKS
            return
        }
        if (now < next) return
        p[SORT_NEXT] = now + SORT_TICKS
        val drops = roll(p, type)
        val taken =
            p.transaction("sort salvage") {
                remove(Invs.INV, type.salvage, 1) && drops.all { (obj, count) -> addOrDrop(Invs.INV, obj, count) }
            }
        if (!taken) return p.stopInteraction()
        p.addXp(Stats.SAILING, type.sortXp)
    }

    /** One sort of [type]: the pre-rolls, the main table and the tertiary rolls (see [SalvageLoot]). */
    private fun roll(p: PlayerRef, type: WreckType): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        val loot = data.loot
        for (line in loot.preRoll(type.page) + loot.tertiary(type.page)) if (p.random(line.den) < line.num) add(p, line, out)
        val main = loot.main(type.page)
        if (main.isNotEmpty()) {
            var r = p.random(main.first().den)
            for (line in main) {
                if (r < line.num) {
                    add(p, line, out)
                    break
                }
                r -= line.num
            }
        }
        return out
    }

    private fun add(p: PlayerRef, line: LootLine, out: MutableList<Pair<Int, Int>>) {
        val obj = objOf[line.item] ?: return
        out += obj to line.min + p.random(line.max - line.min + 1)
    }

    private companion object {
        val RUN = AttrKey<Run>("sailing.salvage.run")
        val SORT_NEXT = AttrKey<Long>("sailing.salvage.sort")

        const val OP_DEPLOY_TEXT = "Deploy"
        const val OP_SORT_TEXT = "Sort-salvage"
        const val STATION_NAME = "Salvaging station"

        /** Cache loc names of the seven hook tiers, bronze to dragon (also the raft and large variants). */
        val HOOK_NAMES: List<String> =
            listOf("Bronze", "Iron", "Steel", "Mithril", "Adamant", "Rune", "Dragon").map { "$it salvaging hook" }

        /** `FACILITY_SUBTYPE` of the salvaging hooks (docs/plans/sailing.md 2.3). */
        const val HOOK_SUBTYPE = 1

        /** Loc shape of the wreck and stump locs in the cache map (`map` shows shape 10 on all 186). */
        const val SHAPE = 10

        /** Tiles from the wreck's edge within which a hook reaches it (Wiki Shipwreck salvaging, Sailing training: 7). */
        const val HOOK_RANGE = 7

        /** Wrecks farther than this on either axis are not looked at (a wreck reaches at most [HOOK_RANGE] + its size). */
        const val SEARCH = 16

        /** Ticks between a player's salvage rolls (Wiki Shipwreck salvaging; the 5 Dec 2025 hotfix restored 4). */
        const val PLAYER_CYCLE = 4

        /** Sites of a cluster that stand as a stump (Wiki Shipwreck salvaging, 5 Dec 2025: every hotspot has 2 inactive). */
        const val INACTIVE_PER_CLUSTER = 2

        /**
         * OPEN-SV2: the Wiki gives no success rate. These feed [PlayerRef.statRandom] (chance out of 256 at level 1 and 99) so a
         * better hook and a higher level salvage more often, as the Wiki says; the numbers are placeholders.
         */
        const val SUCCESS_LOW = 60
        const val SUCCESS_LOW_PER_TIER = 20
        const val SUCCESS_HIGH = 140
        const val SUCCESS_HIGH_PER_TIER = 16

        /** OPEN-SV7: ticks per sorted piece; the Wiki quotes close to 1800 sorts an hour (3.3 ticks each) at a station. */
        const val SORT_TICKS = 3
    }
}
