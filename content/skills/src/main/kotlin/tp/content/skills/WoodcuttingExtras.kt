package tp.content.skills

import tp.plugin.ButtonClick
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.plugin.commands.perk
import tp.symbols.Invs
import tp.symbols.Objs
import tp.util.DonatorPerk

/**
 * Woodcutting side effects of a log (docs/mechanics/woodcutting.md WC5 to WC8): bird nests and their contents, the
 * lumberjack outfit's xp bonus and the infernal axe's burning. World-start data (obj ids found by cache name) lives in
 * the owning [Woodcutting] plugin instance, not in a global.
 */
internal class WoodcuttingExtras {
    /** Lumberjack and forestry outfit piece obj -> outfit slot (0 hat, 1 top, 2 legs, 3 boots). */
    private var outfit: Map<Int, Int> = emptyMap()

    /** Seed nest contents: obj id and weight out of [SEED_NEST_TOTAL] (Wiki "Bird nest (seeds)"). */
    private var seedTable: List<Pair<Int, Int>> = emptyList()

    fun resolve(world: WorldRef) {
        val pieces = HashMap<Int, Int>()
        for ((slot, suffix) in OUTFIT_SLOTS.withIndex()) {
            for (prefix in OUTFIT_PREFIXES) world.objIdsNamed("$prefix $suffix").forEach { pieces[it] = slot }
        }
        outfit = pieces
        seedTable = SEED_NEST.mapNotNull { (name, weight) -> world.objIdsNamed(name).minOrNull()?.let { it to weight } }
    }

    /** The Woodcutting xp multiplier of the worn lumberjack (or forestry) outfit (Wiki "Lumberjack outfit"). */
    fun xpMultiplier(p: PlayerRef): Double {
        val worn = p.inv(Invs.WORN)
        var mask = 0
        var tenths = 0
        for (slot in 0 until worn.size) {
            val piece = outfit[worn.obj(slot)] ?: continue
            if (mask and (1 shl piece) != 0) continue
            mask = mask or (1 shl piece)
            tenths += OUTFIT_TENTHS[piece]
        }
        if (mask == FULL_SET_MASK) tenths += OutfitBonuses.SET_BONUS_TENTHS
        return 1.0 + tenths / TENTHS_PER_UNIT
    }

    /** WC5: a log was cut; maybe a nest falls on an adjacent tile (Wiki "Bird nest"). */
    fun rollNest(p: PlayerRef) {
        val capeBonus = if (wearsAny(p, WOODCUTTING_CAPES)) NEST_CAPE_NUMERATOR else NEST_NUMERATOR
        if (p.random(NEST_DENOMINATOR) >= capeBonus) return
        val rabbitFoot = wearsAny(p, intArrayOf(Objs.HUNTING_STRUNG_RABBIT_FOOT))
        val pick = p.random(if (rabbitFoot) NEST_SLOTS_RABBIT_FOOT else NEST_SLOTS)
        val nest =
            when {
                pick < EGG_NESTS.size -> EGG_NESTS[pick]
                pick < FIRST_SEED_SLOT -> Objs.BIRD_NEST_RING
                else -> Objs.BIRD_NEST_SEEDS_JAN2019
            }
        val at = landing(p)
        // D-093 donator perk (perks.tsv row "Bird nests' will automatically be placed into your inventory"): ground if full.
        val kept =
            p.perk(DonatorPerk.BIRD_NESTS_WILL_AUTOMATICALLY_BE_PLACED_INTO_YOUR_INVENTORY) == 1 &&
                p.transaction("bird nest") { add(Invs.INV, nest, 1) }
        if (!kept) p.transaction("bird nest") { createOnGround(nest, 1, at, p.username) }
        p.message("<col=ef1020>A bird's nest falls out of the tree.</col>")
    }

    /** The "Search" op of a bird nest: its contents replace it with an empty nest (Wiki "Bird nest" types). */
    fun search(p: PlayerRef, click: ButtonClick) {
        val inv = p.inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != click.obj) return
        val contents = contents(p, click.obj) ?: return
        val done =
            p.transaction("search nest") {
                replaceAt(Invs.INV, click.slot, click.obj, Objs.BIRD_NEST_EMPTY) && add(Invs.INV, contents, 1)
            }
        if (!done) p.message("You don't have enough inventory space.")
    }

    private fun contents(p: PlayerRef, nest: Int): Int? =
        when (nest) {
            Objs.BIRD_NEST_EGG_RED -> Objs.BIRD_EGG_RED
            Objs.BIRD_NEST_EGG_BLUE -> Objs.BIRD_EGG_BLUE
            Objs.BIRD_NEST_EGG_GREEN -> Objs.BIRD_EGG_GREEN
            Objs.BIRD_NEST_RING -> weighted(p, RING_NEST, RING_NEST_TOTAL)
            Objs.BIRD_NEST_SEEDS_JAN2019 -> weighted(p, seedTable, SEED_NEST_TOTAL)
            else -> null
        }

    /** One entry of [table] by weight (out of [total]); null if the table is empty. */
    private fun weighted(p: PlayerRef, table: List<Pair<Int, Int>>, total: Int): Int? {
        var roll = p.random(total)
        for ((obj, weight) in table) {
            if (roll < weight) return obj
            roll -= weight
        }
        return table.lastOrNull()?.first
    }

    /** Where a nest lands: a random adjacent tile you can stand on, else under the player. OPEN-WC8: no west bias. */
    private fun landing(p: PlayerRef): tp.util.CoordGrid {
        val here = p.coord
        val open = NEIGHBOURS.filter { (dx, dz) -> p.canStep(here, dx, dz) }
        if (open.isEmpty()) return here
        val (dx, dz) = open[p.random(open.size)]
        return tp.util.CoordGrid(here.x + dx, here.z + dz, here.level)
    }

    /**
     * WC7 (Wiki "Infernal axe"): with the axe usable, a cut log burns instead of being kept 1 time in 3 (while it has
     * charges), giving half the log's Firemaking xp with the pyromancer bonus. Returns true when the log was burned.
     */
    fun infernalBurn(p: PlayerRef, axe: Int, logs: Int): Boolean {
        if (axe != Objs.INFERNAL_AXE) return false
        val burnable = BURNABLE_LOGS.firstOrNull { it.obj == logs } ?: return false
        val charges = p.saved(INFERNAL_CHARGES_KEY) ?: INFERNAL_MAX_CHARGES
        if (charges <= 0L || p.random(INFERNAL_ODDS) != 0) return false
        p.setSaved(INFERNAL_CHARGES_KEY, charges - 1)
        OutfitBonuses.addFiremakingXp(p, burnable.xp * INFERNAL_XP_SHARE)
        return true
    }

    private fun wearsAny(p: PlayerRef, objs: IntArray): Boolean {
        val worn = p.inv(Invs.WORN)
        for (slot in 0 until worn.size) if (worn.obj(slot) in objs) return true
        return false
    }

    companion object {
        private val OUTFIT_SLOTS = listOf("hat", "top", "legs", "boots")
        private val OUTFIT_PREFIXES = listOf("Lumberjack", "Forestry")

        /** Wiki "Lumberjack outfit": hat 0.4%, top 0.8%, legs 0.6%, boots 0.2% (tenths of a percent), by [OUTFIT_SLOTS] order. */
        private val OUTFIT_TENTHS = intArrayOf(4, 8, 6, 2)
        private const val FULL_SET_MASK = 0b1111
        private const val TENTHS_PER_UNIT = 1000.0

        private val WOODCUTTING_CAPES = intArrayOf(Objs.SKILLCAPE_WOODCUTTING, Objs.SKILLCAPE_WOODCUTTING_TRIMMED)

        /** Wiki "Bird nest": 1/256 per log, 10% more with a Woodcutting cape, as numerators over [NEST_DENOMINATOR]. */
        private const val NEST_NUMERATOR = 10
        private const val NEST_CAPE_NUMERATOR = 11
        private const val NEST_DENOMINATOR = 2560

        /** Wiki "Bird nest": 100 slots: 3 eggs, 32 rings (slots 3 to 34), 65 seeds; a rabbit foot removes 5 seed slots. */
        private const val NEST_SLOTS = 100
        private const val NEST_SLOTS_RABBIT_FOOT = 95
        private const val FIRST_SEED_SLOT = 35
        private val EGG_NESTS = intArrayOf(Objs.BIRD_NEST_EGG_RED, Objs.BIRD_NEST_EGG_BLUE, Objs.BIRD_NEST_EGG_GREEN)

        /** Wiki "Bird nest (ring)": gold 35, sapphire 40, emerald 15, ruby 9, diamond 1, out of 100. */
        private val RING_NEST =
            listOf(
                Objs.GOLD_RING to 35,
                Objs.SAPPHIRE_RING to 40,
                Objs.EMERALD_RING to 15,
                Objs.RUBY_RING to 9,
                Objs.DIAMOND_RING to 1,
            )
        private const val RING_NEST_TOTAL = 100

        /** Wiki "Bird nest (seeds)" (post Farming Guild table), weights out of [SEED_NEST_TOTAL]. */
        private val SEED_NEST =
            listOf(
                "Acorn" to 214,
                "Apple tree seed" to 170,
                "Banana tree seed" to 108,
                "Orange tree seed" to 85,
                "Willow seed" to 135,
                "Teak seed" to 4,
                "Curry tree seed" to 68,
                "Maple seed" to 54,
                "Pineapple seed" to 42,
                "Mahogany seed" to 4,
                "Papaya tree seed" to 34,
                "Palm tree seed" to 22,
                "Calquat tree seed" to 17,
                "Yew seed" to 27,
                "Magic seed" to 5,
                "Spirit seed" to 11,
                "Celastrus seed" to 3,
                "Redwood tree seed" to 2,
                "Dragonfruit tree seed" to 6,
            )
        private const val SEED_NEST_TOTAL = 1011

        private val NEIGHBOURS = listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)

        /** Wiki "Infernal axe": 1/3 chance a cut log burns, half the Firemaking xp, 5,000 charges. */
        private const val INFERNAL_ODDS = 3
        private const val INFERNAL_XP_SHARE = 0.5
        private const val INFERNAL_MAX_CHARGES = 5000L
        private const val INFERNAL_CHARGES_KEY = "woodcutting.infernal_charges"
    }
}
