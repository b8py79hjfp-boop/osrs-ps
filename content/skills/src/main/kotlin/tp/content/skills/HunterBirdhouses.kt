package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varps

/**
 * Bird house trapping (docs/mechanics/hunter.md HU20 .. HU24). The four spaces on Fossil Island are multi-locs (cache locs 30565 ..
 * 30568) that the client draws from the varps `BIRDHOUSE_TRANSMIT_A..D`; the plugin keeps each space's state per player in `saved`
 * (so it keeps running while the player is logged out) and writes the varp on login and after every change.
 */
public class HunterBirdhouses : ContentPlugin {
    /** One tier of bird house (Wiki "Bird house trapping/Table" and the "Other nests" chart). */
    private class Tier(
        val house: Int,
        val logs: Int,
        val craftLevel: Int,
        val craftXp: Double,
        val hunterLevel: Int,
        val xp: Double,
        val nestPermille: Int,
    )

    override fun ContentScope.register() {
        for ((space, loc) in SPACE_LOCS.withIndex()) {
            onOpLoc(loc, OP_BUILD_OR_EMPTY) { onPrimary(player, space) }
            onOpLoc(loc, OP_SEEDS) { seed(player, space, null) }
            onOpLoc(loc, OP_DISMANTLE_OR_EMPTY) { onSecondary(player, space) }
            onOpLoc(loc, OP_RESET) { reset(player, space) }
            onUseOnLoc(ANY_ITEM, loc) { useOn(player, space, used?.obj ?: ANY_ITEM) }
        }
        // "Logs on a clockwork with a chisel and a hammer" (Wiki "Bird house (item)", Creation).
        for (tier in TIERS) onUseOnItem(CLOCKWORK, tier.logs) { craft(this, tier) }
        onLogin { sync(this) }
    }

    // ---- state: saved per space, `tier | state << 4 | startMinute << 8`

    private fun load(p: PlayerRef, space: Int): Long = p.saved(KEY + space) ?: 0L

    private fun store(p: PlayerRef, space: Int, state: Int, tier: Int, startMinute: Long) {
        p.setSaved(
            KEY + space,
            if (state ==
                EMPTY
            ) {
                null
            } else {
                tier.toLong() or (state.toLong() shl STATE_SHIFT) or (startMinute shl MINUTE_SHIFT)
            },
        )
        draw(p, space)
    }

    private fun stateOf(v: Long): Int = ((v shr STATE_SHIFT) and STATE_MASK).toInt()

    private fun tierOf(v: Long): Int = (v and TIER_MASK).toInt()

    private fun startOf(v: Long): Long = v shr MINUTE_SHIFT

    /** Writes the varp that draws space [space] (cache loc config: value 3 * tier + 2 is built, 3 * tier + 3 is seeded). */
    private fun draw(p: PlayerRef, space: Int) {
        val v = load(p, space)
        val value =
            when (stateOf(v)) {
                BUILT -> VALUES_PER_TIER * tierOf(v) + BUILT_OFFSET
                SEEDED -> VALUES_PER_TIER * tierOf(v) + SEEDED_OFFSET
                else -> 0
            }
        p.setVarp(Varps.BIRDHOUSE_TRANSMIT_A + space, value)
    }

    private fun sync(p: PlayerRef) {
        for (space in SPACE_LOCS.indices) draw(p, space)
    }

    private fun ready(p: PlayerRef, v: Long): Boolean = p.epochMinutes - startOf(v) >= FILL_MINUTES

    // ---- ops

    /** Op 1: "Build" on a space, "Interact" (= empty) on a seeded house. */
    private suspend fun PlayerScript.onPrimary(p: PlayerRef, space: Int) {
        when (stateOf(load(p, space))) {
            EMPTY -> placeBest(p, space)
            SEEDED -> empty(p, space)
            else -> seed(p, space, null)
        }
    }

    /** Op 3: "Dismantle" on a built house, "Empty" on a seeded one. */
    private suspend fun PlayerScript.onSecondary(p: PlayerRef, space: Int) {
        when (stateOf(load(p, space))) {
            BUILT -> dismantle(p, space)
            SEEDED -> empty(p, space)
            else -> Unit
        }
    }

    private suspend fun PlayerScript.useOn(p: PlayerRef, space: Int, obj: Int) {
        val tier = TIERS.indexOfFirst { it.house == obj }
        when {
            tier >= 0 && stateOf(load(p, space)) == EMPTY -> place(p, space, tier)
            seedPoints(p, obj) > 0 -> seed(p, space, obj)
            else -> p.message("Nothing interesting happens.")
        }
    }

    // ---- building and placing

    /** Crafts a bird house from [tier]'s logs and a clockwork (needs a chisel and a hammer, 2 ticks). */
    private fun craft(p: PlayerRef, tier: Tier) {
        val inv = p.inv(Invs.INV)
        val problem =
            when {
                p.level(Stats.CRAFTING) < tier.craftLevel -> "You need a Crafting level of ${tier.craftLevel} to make this bird house."
                inv.total(Objs.CHISEL) <= 0 || inv.total(Objs.HAMMER) <= 0 -> "You need a chisel and a hammer to make a bird house."
                else -> null
            }
        if (problem != null) return p.message(problem)
        val made =
            p.transaction("hunter birdhouse craft") {
                remove(Invs.INV, tier.logs, 1) && remove(Invs.INV, CLOCKWORK, 1) && add(Invs.INV, tier.house, 1)
            }
        if (!made) return p.message("You need logs and a clockwork to make a bird house.")
        p.addXp(Stats.CRAFTING, tier.craftXp)
    }

    /** A pre-made house of the best tier [p] may place, else one crafted from the best logs [p] can use (Wiki "Changes", 12 Feb 2025). */
    private suspend fun PlayerScript.placeBest(p: PlayerRef, space: Int) {
        val inv = p.inv(Invs.INV)
        var tier = TIERS.indices.lastOrNull { inv.total(TIERS[it].house) > 0 && p.level(Stats.HUNTER) >= TIERS[it].hunterLevel }
        if (tier == null) {
            tier = TIERS.indices.lastOrNull { craftable(p, TIERS[it]) }
            if (tier == null) return p.message("You need a bird house, or logs, a clockwork, a chisel and a hammer, to build one here.")
            craft(p, TIERS[tier])
            delay(CRAFT_TICKS)
        }
        place(p, space, tier)
    }

    private fun craftable(p: PlayerRef, tier: Tier): Boolean {
        val inv = p.inv(Invs.INV)
        return p.level(Stats.CRAFTING) >= tier.craftLevel && p.level(Stats.HUNTER) >= tier.hunterLevel && inv.total(tier.logs) > 0 &&
            inv.total(CLOCKWORK) > 0 && inv.total(Objs.CHISEL) > 0 && inv.total(Objs.HAMMER) > 0
    }

    private suspend fun PlayerScript.place(p: PlayerRef, space: Int, tier: Int) {
        val t = TIERS[tier]
        if (p.level(Stats.HUNTER) < t.hunterLevel) return p.message("You need a Hunter level of ${t.hunterLevel} to place this bird house.")
        p.stopInteraction()
        p.anim(Seqs.HUMAN_PICKUPTABLE)
        delay(PLACE_TICKS)
        if (stateOf(load(p, space)) != EMPTY || !p.transaction("hunter birdhouse place") { remove(Invs.INV, t.house, 1) }) return
        store(p, space, BUILT, tier, 0)
    }

    private suspend fun PlayerScript.dismantle(p: PlayerRef, space: Int) {
        val v = load(p, space)
        p.stopInteraction()
        p.anim(Seqs.HUMAN_PICKUPTABLE)
        delay(PLACE_TICKS)
        if (stateOf(load(p, space)) != BUILT) return
        if (!p.transaction("hunter birdhouse dismantle") { add(Invs.INV, TIERS[tierOf(v)].house, 1) }) return p.message(FULL_MESSAGE)
        store(p, space, EMPTY, 0, 0)
    }

    // ---- seeds

    /** Seed points of one [obj]: 2 for a seed that needs 5 (high-level), 1 for one that needs 10, 0 for other items (Wiki "Seeds"). */
    private fun seedPoints(p: PlayerRef, obj: Int): Int = if (p.unnoted(obj) != obj) 0 else SEEDS[p.objName(obj).lowercase()] ?: 0

    private suspend fun PlayerScript.seed(p: PlayerRef, space: Int, first: Int?) {
        val v = load(p, space)
        if (stateOf(v) == EMPTY) return p.message("There is no bird house here to put seeds in.")
        if (stateOf(v) == SEEDED) return p.message("The bird house is already full of seeds.")
        val inv = p.inv(Invs.INV)
        val used = LinkedHashMap<Int, Int>()
        var points = 0
        // The clicked seed first, then every other bait stack in slot order, each as far as the 10 points go (Wiki "Changes", 3 May 2018).
        val order = ArrayList<Int>()
        if (first != null) order += first
        for (slot in 0 until inv.size) inv.obj(slot).takeIf { it >= 0 && it !in order && seedPoints(p, it) > 0 }?.let { order += it }
        for (obj in order) {
            val per = seedPoints(p, obj)
            val take = minOf(inv.total(obj).toInt(), (SEED_POINTS - points + per - 1) / per)
            if (take <= 0) continue
            used[obj] = take
            points += take * per
            if (points >= SEED_POINTS) break
        }
        // OPEN-HU12: the Wiki lets the seeds go in over several uses; one use here needs all of them.
        if (points < SEED_POINTS) return p.message("You need 10 seeds (5 for the high-level ones) to bait the bird house.")
        p.anim(Seqs.HUMAN_PICKUPTABLE)
        delay(PLACE_TICKS)
        if (stateOf(load(p, space)) != BUILT) return
        if (!p.transaction("hunter birdhouse seed") { used.all { (obj, n) -> remove(Invs.INV, obj, n) } }) return
        store(p, space, SEEDED, tierOf(v), p.epochMinutes)
        p.message("You fill the bird house with seeds.")
    }

    // ---- emptying

    private suspend fun PlayerScript.empty(p: PlayerRef, space: Int) {
        val v = load(p, space)
        if (stateOf(v) != SEEDED) return
        if (!ready(p, v)) {
            // Wiki: "A warning message is given in case the player selects the option to empty before the birdhouse is full."
            val pick = choice("The bird house is not full yet. Empty it anyway?", "Yes, lose the birds and the seeds.", "No.")
            if (pick != 1) return
            if (stateOf(load(p, space)) != SEEDED) return
            if (!p.transaction("hunter birdhouse abandon") { add(Invs.INV, CLOCKWORK, 1) }) return p.message(FULL_MESSAGE)
            return store(p, space, EMPTY, 0, 0)
        }
        harvest(p, space, v)
    }

    /** "Reset": empties a full house and builds and places a new one (Wiki "Changes", 12 Feb 2025). */
    private suspend fun PlayerScript.reset(p: PlayerRef, space: Int) {
        val v = load(p, space)
        if (stateOf(v) != SEEDED || !ready(p, v)) return empty(p, space)
        if (!harvest(p, space, v)) return
        delay(1)
        placeBest(p, space)
    }

    private fun harvest(p: PlayerRef, space: Int, v: Long): Boolean {
        val t = TIERS[tierOf(v)]
        val level = p.level(Stats.HUNTER)
        val feathers = FEATHER_CHOICES[p.random(FEATHER_CHOICES.size)]
        val nests = nests(p, t, level)
        val given =
            p.transaction("hunter birdhouse harvest") {
                var ok = addOrDrop(Invs.INV, CLOCKWORK, 1) && addOrDrop(Invs.INV, Objs.FEATHER, feathers)
                ok = ok && createOnGround(RAW_BIRD_MEAT, MEAT_COUNT, p.coord, p.username)
                for (nest in nests) ok = ok && addOrDrop(Invs.INV, nest, 1)
                ok
            }
        if (!given) return false
        p.addXp(Stats.HUNTER, t.xp)
        store(p, space, EMPTY, 0, 0)
        return true
    }

    /** At most one seed nest, then [NEST_ROLLS] rolls of the nest table (Wiki "Bird house trapping", Loot). */
    private fun nests(p: PlayerRef, t: Tier, level: Int): List<Int> {
        val out = ArrayList<Int>()
        if (p.statRandom(Stats.HUNTER, 0, SEED_NEST_HIGH)) out += Objs.BIRD_NEST_SEEDS
        // The chance per roll is `nestPermille` at 99, half of it up to level 50, linear in between (Mod Ash, OPEN-HU11).
        val over = (level.coerceAtLeast(HALF_LEVEL) - HALF_LEVEL).toDouble() / (Stats.MAX_LEVEL - HALF_LEVEL)
        val chance = t.nestPermille / PERMILLE * (HALF_SCALE + HALF_SCALE * over)
        val foot = wearsRabbitFoot(p)
        repeat(NEST_ROLLS) {
            if (p.random(PERMILLE_INT) < (chance * PERMILLE_INT).toInt()) out += nestTable(p, foot)
        }
        return out
    }

    /** The ring / egg / empty nest table, out of 100 (or 95 with a strung rabbit foot, which drops 5 empty-nest points). */
    private fun nestTable(p: PlayerRef, foot: Boolean): Int {
        val roll = p.random(if (foot) TABLE_FOOT else TABLE_PLAIN)
        return when {
            roll < NEST_RING -> Objs.BIRD_NEST_RING
            roll < NEST_RING + 1 -> Objs.BIRD_NEST_EGG_RED
            roll < NEST_RING + 2 -> Objs.BIRD_NEST_EGG_GREEN
            roll < NEST_RING + 3 -> Objs.BIRD_NEST_EGG_BLUE
            else -> Objs.BIRD_NEST_EMPTY
        }
    }

    private fun wearsRabbitFoot(p: PlayerRef): Boolean {
        val worn = p.inv(Invs.WORN)
        return (0 until worn.size).any { worn.obj(it) >= 0 && p.objName(worn.obj(it)) == RABBIT_FOOT }
    }

    private companion object {
        const val KEY = "hunter.birdhouse."
        const val ANY_ITEM = -1
        const val FULL_MESSAGE = "You don't have enough inventory space."

        /** Cache locs of the four spaces, in the order of the varps `BIRDHOUSE_TRANSMIT_A..D` (placements: `find-locs`). */
        val SPACE_LOCS = intArrayOf(30565, 30566, 30567, 30568)

        /** Ops of the space and house locs (cache locs 30552 .. 30555): 1 Build / Interact, 2 Seeds, 3 Dismantle / Empty, 4 Reset. */
        const val OP_BUILD_OR_EMPTY = 1
        const val OP_SEEDS = 2
        const val OP_DISMANTLE_OR_EMPTY = 3
        const val OP_RESET = 4

        /** Saved-state layout and the three states of a space. */
        const val EMPTY = 0
        const val BUILT = 1
        const val SEEDED = 2
        const val STATE_SHIFT = 4
        const val MINUTE_SHIFT = 8
        const val STATE_MASK = 3L
        const val TIER_MASK = 15L

        /** The multi-loc (cache loc 30565) has three variants per tier after the bare space: 3 * tier + 2 built, + 3 seeded (OPEN-HU10). */
        const val VALUES_PER_TIER = 3
        const val BUILT_OFFSET = 2
        const val SEEDED_OFFSET = 3

        /** Wiki: "After waiting around 50 minutes, a bird house will be completely filled" (OPEN-HU9: exactly this here). */
        const val FILL_MINUTES = 50L

        /** Ticks of crafting a house (Wiki "Bird house (item)": 2 ticks) and of placing, seeding or taking one (OPEN-HU13: assumed). */
        const val CRAFT_TICKS = 2
        const val PLACE_TICKS = 2

        /** Cache obj 8792 "Clockwork" (`POH_CLOCKWORK_MECHANISM`). */
        val CLOCKWORK = Objs.POH_CLOCKWORK_MECHANISM

        /** Cache obj 9978 "Raw bird meat" (hunter.tsv bird loot). */
        const val RAW_BIRD_MEAT = 9978

        /** Wiki: 10 raw bird meat, always dropped on the ground. */
        const val MEAT_COUNT = 10

        /** Wiki: "{{DropsLineSkill|name=Feather|quantity=30,40,50,60|rarity=Always}}" (OPEN-HU9: one of the four, evenly). */
        val FEATHER_CHOICES = intArrayOf(30, 40, 50, 60)

        /** Seed points a house needs: 10 low-level seeds or 5 high-level ones (Wiki "Seeds"). */
        const val SEED_POINTS = 10

        /** Seed nest chart: `low1=0 high1=200` (Wiki "Bird house trapping"; 0% at level 1, 78.5% at 99 as Mod Ash says). */
        const val SEED_NEST_HIGH = 200

        /** The nest table is rolled 5 times per house (Wiki, Aug 2026 change). */
        const val NEST_ROLLS = 5
        const val PERMILLE = 1000.0
        const val PERMILLE_INT = 1000

        /** The nest chance is halved at level 50 and below (Mod Ash, 5 June 2019). */
        const val HALF_LEVEL = 50
        const val HALF_SCALE = 0.5

        /** Nest table out of 100: ring 32, each egg 1, empty 65; 95 with a strung rabbit foot (Wiki "Ring and egg nests"). */
        const val TABLE_PLAIN = 100
        const val TABLE_FOOT = 95
        const val NEST_RING = 32
        const val RABBIT_FOOT = "Strung rabbit foot"

        /** Wiki "Bird house trapping/Table": item, logs, Crafting level and xp, Hunter level, Hunter xp, nest chance per mille at 99. */
        val TIERS =
            listOf(
                Tier(Objs.BIRDHOUSE_NORMAL, Objs.LOGS, 5, 15.0, 5, 112.0, 300),
                Tier(Objs.BIRDHOUSE_OAK, Objs.OAK_LOGS, 15, 20.0, 14, 168.0, 375),
                Tier(Objs.BIRDHOUSE_WILLOW, Objs.WILLOW_LOGS, 25, 25.0, 24, 224.0, 384),
                Tier(Objs.BIRDHOUSE_TEAK, Objs.TEAK_LOGS, 35, 30.0, 34, 280.0, 390),
                Tier(Objs.BIRDHOUSE_MAPLE, Objs.MAPLE_LOGS, 45, 35.0, 44, 369.0, 420),
                Tier(Objs.BIRDHOUSE_MAHOGANY, Objs.MAHOGANY_LOGS, 50, 40.0, 49, 480.0, 450),
                Tier(Objs.BIRDHOUSE_YEW, Objs.YEW_LOGS, 60, 45.0, 59, 612.0, 480),
                Tier(Objs.BIRDHOUSE_MAGIC, Objs.MAGIC_LOGS, 75, 50.0, 74, 969.0, 510),
                Tier(Objs.BIRDHOUSE_REDWOOD, Objs.REDWOOD_LOGS, 90, 55.0, 89, 1200.0, 525),
            )

        /** Wiki "Bird house trapping/Seeds": seed name -> seed points (2 = needs only 5). */
        val SEEDS: Map<String, Int> =
            buildMap {
                val low =
                    "barley hammerstone asgarnian jute yanillian krandorian guam marrentill tarromin harralander marigold rosemary " +
                        "nasturtium woad limpwurt white_lily potato onion cabbage tomato sweetcorn strawberry watermelon snape_grass " +
                        "redberry poison_ivy cadavaberry dwellberry jangerberry whiteberry"
                val high = "wildblood ranarr toadflax irit avantoe kwuarm snapdragon cadantine lantadyme dwarf_weed torstol"
                for (n in low.split(' ')) put("${n.replace('_', ' ')} seed", 1)
                for (n in high.split(' ')) put("${n.replace('_', ' ')} seed", 2)
            }
    }
}
