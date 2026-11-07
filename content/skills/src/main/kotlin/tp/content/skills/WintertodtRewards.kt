@file:Suppress("MagicNumber") // data tables: Wiki "Reward cart" and Module:Wintertodt supply crate (docs/mechanics/wintertodt.md WT9)

package tp.content.skills

import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.WorldRef
import tp.symbols.Invs
import tp.symbols.Objs

/** One material of a skill subtable: Wiki cache [name], [min]..[max] amount and the (low, high) roll at levels 1 and 99. */
private class Material(val name: String, val min: Int, val max: Int, val low: Int, val high: Int)

/** A resolved reward: obj, amount range and whether it is delivered noted. */
private class Resolved(val obj: Int, val min: Int, val max: Int, val low: Int, val high: Int, val noted: Boolean)

/** Level-based subtable of one skill (Module:Wintertodt supply crate `items.<skill>`), best item first, last item always. */
private class SkillTable(val stat: Int, val noted: Boolean, val materials: List<Material>)

/**
 * The reward cart's loot (Wiki "Reward cart", docs/mechanics/wintertodt.md WT9): the unique chain, then one of six
 * level-weighted skill subtables (3/25 each) or coins, dynamite or saltpetre. Obj ids that have no gameval symbol are
 * found by cache name when the world starts.
 */
internal class WintertodtRewards {
    private var tables: List<Pair<SkillTable, List<Resolved>>> = emptyList()
    private var dynamite = -1
    private var saltpetre = -1
    private var seedIds: Map<String, Int> = emptyMap()

    fun resolve(world: WorldRef) {
        tables =
            SKILL_TABLES.map { table ->
                table to
                    table.materials.mapNotNull { m ->
                        world.objIdsNamed(m.name).minOrNull()?.let { Resolved(it, m.min, m.max, m.low, m.high, table.noted) }
                    }
            }
        dynamite = world.objIdsNamed("Dynamite").minOrNull() ?: -1
        saltpetre = world.objIdsNamed("Saltpetre").minOrNull() ?: -1
        seedIds = listOf("Magic seed", "Torstol seed").mapNotNull { n -> world.objIdsNamed(n).minOrNull()?.let { n to it } }.toMap()
    }

    /** Number of rolls for [points]: 2 at 500, one more per 500, and a 1% chance per 5 points toward the next (Wiki "Reward cart"). */
    fun rolls(p: PlayerRef, points: Int): Int {
        if (points < THRESHOLD) return 0
        val past = points - THRESHOLD
        val extra = if (p.random(THRESHOLD) < past % THRESHOLD) 1 else 0
        return BASE_ROLLS + past / THRESHOLD + extra
    }

    /** Rolls one reward for [p] and gives it. Returns false when it could not be delivered. */
    fun give(p: PlayerRef): Boolean {
        val (obj, count) = roll(p)
        val ok = p.transaction("wintertodt reward") { addOrDrop(Invs.INV, obj, count) }
        if (ok) p.events.publish(tp.plugin.LootEvent(p, obj, count))
        return ok
    }

    private fun roll(p: PlayerRef): Pair<Int, Int> {
        unique(p)?.let { return it }
        val r = p.random(SLOTS)
        if (r < SKILL_SLOTS) {
            val (table, items) = tables[r / SLOTS_PER_SKILL]
            return material(p, table, items)
        }
        return when (r - SKILL_SLOTS) {
            in 0 until COIN_SLOTS -> Objs.COINS to range(p, COINS_MIN, COINS_MAX)
            COIN_SLOTS -> notedOrSelf(p, dynamite) to range(p, EXPLOSIVE_MIN, EXPLOSIVE_MAX)
            else -> notedOrSelf(p, saltpetre) to range(p, EXPLOSIVE_MIN, EXPLOSIVE_MAX)
        }
    }

    /** The unique chain in the Wiki's order; null when none hit. */
    private fun unique(p: PlayerRef): Pair<Int, Int>? {
        if (owned(p, Objs.PHOENIXPET) == 0L && p.random(PHOENIX_ODDS) == 0) return Objs.PHOENIXPET to 1
        if (p.random(DRAGON_AXE_ODDS) == 0) return Objs.DRAGON_AXE to 1
        if (p.random(TOME_ODDS) == 0) return Objs.TOME_OF_FIRE_UNCHARGED to 1
        if (p.random(PIECE_ODDS) == 0) return glovesOrSeed(p)
        if (p.random(PIECE_ODDS) == 0) return torchOrSeed(p)
        if (p.random(PIECE_ODDS) == 0) return pyromancerPiece(p)?.let { it to 1 }
        if (p.random(PAGE_ODDS) == 0) return Objs.WINT_BURNT_PAGE to range(p, PAGE_MIN, PAGE_MAX)
        return null
    }

    /** Wiki: a fourth pair of warm gloves is a magic seed instead (3 or more owned). */
    private fun glovesOrSeed(p: PlayerRef): Pair<Int, Int> =
        if (owned(p, Objs.PYROMANCER_GLOVES) >=
            MAX_KEPT
        ) {
            (seed("Magic seed") ?: Objs.PYROMANCER_GLOVES) to 1
        } else {
            Objs.PYROMANCER_GLOVES to 1
        }

    /** Wiki: with three bruma torches owned, torstol seeds instead. */
    private fun torchOrSeed(p: PlayerRef): Pair<Int, Int> {
        val owned = owned(p, Objs.WINT_TORCH) + owned(p, Objs.WINT_TORCH_OFFHAND)
        if (owned >= MAX_KEPT) return (seed("Torstol seed") ?: Objs.WINT_TORCH) to range(p, TORSTOL_MIN, TORSTOL_MAX)
        return Objs.WINT_TORCH to 1
    }

    /** The outfit piece the player has least of; ties go garb, hood, robe, boots (Wiki "Reward cart" note). */
    private fun pyromancerPiece(p: PlayerRef): Int? = PIECES.minByOrNull { owned(p, it) }

    private fun seed(name: String): Int? = seedIds[name]

    /** Items of [table] in order: item hits when a 0..255 roll is below its interpolated chance (Module:Wintertodt supply crate). */
    private fun material(p: PlayerRef, table: SkillTable, items: List<Resolved>): Pair<Int, Int> {
        val level = p.level(table.stat).coerceIn(1, Stats.MAX_LEVEL)
        for (m in items) {
            if (p.random(ROLL_SCALE) < chance(level, m.low, m.high)) return deliver(p, m)
        }
        return deliver(p, items.last())
    }

    private fun deliver(p: PlayerRef, m: Resolved): Pair<Int, Int> {
        val obj = if (m.noted) notedOrSelf(p, m.obj) else m.obj
        return obj to range(p, m.min, m.max)
    }

    private fun notedOrSelf(p: PlayerRef, obj: Int): Int = p.noted(obj).takeIf { it >= 0 } ?: obj

    private fun owned(p: PlayerRef, obj: Int): Long = p.inv(Invs.INV).total(obj) + p.inv(Invs.BANK).total(obj) + wornCount(p, obj)

    private fun wornCount(p: PlayerRef, obj: Int): Long = p.inv(Invs.WORN).total(obj)

    private fun range(p: PlayerRef, min: Int, max: Int): Int = min + p.random(max - min + 1)

    companion object {
        /** Wiki "Reward cart": at least 500 points for any reward; 2 rolls at 500 and one more per further 500. */
        const val THRESHOLD = 500
        private const val BASE_ROLLS = 2

        /** Module:Wintertodt supply crate `interpolate`: floor(low + (high - low) * (level - 1) / 98) + 1, clamped to 0..256. */
        fun chance(level: Int, low: Int, high: Int): Int = (Math.floorDiv(low * 98 + (high - low) * (level - 1), 98) + 1).coerceIn(0, 256)

        private const val ROLL_SCALE = 256

        /** Wiki "Reward cart" unique chain: 1/5000, 1/10000, 1/1000, 1/150 (gloves, torch, outfit piece), 1/45 (burnt page). */
        private const val PHOENIX_ODDS = 5000
        private const val DRAGON_AXE_ODDS = 10000
        private const val TOME_ODDS = 1000
        private const val PIECE_ODDS = 150
        private const val PAGE_ODDS = 45
        private const val PAGE_MIN = 7
        private const val PAGE_MAX = 29
        private const val TORSTOL_MIN = 2
        private const val TORSTOL_MAX = 3

        /** Wiki: gloves and torches stop at three; the fourth roll gives a seed. */
        private const val MAX_KEPT = 3L

        /** Wiki: 25 slots; six skill tables at 3/25, coins 5/25, dynamite 1/25, saltpetre 1/25. */
        private const val SLOTS = 25
        private const val SLOTS_PER_SKILL = 3
        private const val SKILL_SLOTS = 18
        private const val COIN_SLOTS = 5
        private const val COINS_MIN = 2000
        private const val COINS_MAX = 4999
        private const val EXPLOSIVE_MIN = 3
        private const val EXPLOSIVE_MAX = 5

        /** Pyromancer outfit pieces in tie-break order: garb, hood, robe, boots. */
        private val PIECES = listOf(Objs.PYROMANCER_TOP, Objs.PYROMANCER_HOOD, Objs.PYROMANCER_BOTTOM, Objs.PYROMANCER_BOOTS)

        /** Module:Wintertodt supply crate item lists, in its order: Crafting, Herblore, Farming, Woodcutting, Mining, Fishing. */
        private val SKILL_TABLES: List<SkillTable> =
            listOf(
                SkillTable(
                    Stats.CRAFTING,
                    true,
                    listOf(
                        Material("Uncut diamond", 1, 3, 10, 120),
                        Material("Uncut ruby", 2, 4, 50, 140),
                        Material("Uncut emerald", 1, 3, 90, 160),
                        Material("Uncut sapphire", 1, 3, 255, 255),
                    ),
                ),
                SkillTable(
                    Stats.HERBLORE,
                    true,
                    listOf(
                        Material("Grimy torstol", 1, 3, -70, 40),
                        Material("Grimy dwarf weed", 2, 4, -50, 50),
                        Material("Grimy lantadyme", 2, 4, -30, 60),
                        Material("Grimy cadantine", 2, 4, -10, 70),
                        Material("Grimy kwuarm", 2, 4, 10, 85),
                        Material("Grimy avantoe", 3, 5, 20, 100),
                        Material("Grimy irit leaf", 3, 5, 30, 115),
                        Material("Grimy ranarr weed", 1, 3, 10, 170),
                        Material("Grimy tarromin", 3, 6, 70, -20),
                        Material("Grimy marrentill", 3, 6, 100, -30),
                        Material("Grimy guam leaf", 3, 6, 170, -40),
                        Material("Grimy harralander", 3, 6, 255, 255),
                    ),
                ),
                SkillTable(
                    Stats.FARMING,
                    false,
                    listOf(
                        Material("Spirit seed", 1, 1, -20, 5),
                        Material("Dwarf weed seed", 1, 3, -60, 25),
                        Material("Lantadyme seed", 1, 3, -60, 30),
                        Material("Cadantine seed", 1, 3, -40, 40),
                        Material("Snapdragon seed", 1, 3, -15, 60),
                        Material("Yew seed", 1, 2, -10, 70),
                        Material("Snape grass seed", 3, 7, -8, 78),
                        Material("Kwuarm seed", 1, 3, -10, 80),
                        Material("Ranarr seed", 1, 3, 0, 110),
                        Material("Avantoe seed", 1, 3, 0, 130),
                        Material("Watermelon seed", 3, 7, -10, 180),
                        Material("Irit seed", 1, 3, 0, 170),
                        Material("Teak seed", 1, 2, 10, 160),
                        Material("Maple seed", 1, 2, 15, 190),
                        Material("Mahogany seed", 1, 2, 20, 190),
                        Material("Toadflax seed", 1, 3, 20, 190),
                        Material("Banana tree seed", 1, 2, 30, 180),
                        Material("Willow seed", 1, 2, 60, 120),
                        Material("Harralander seed", 1, 3, 80, -150),
                        Material("Tarromin seed", 1, 3, 150, -150),
                        Material("Acorn", 1, 1, 255, 255),
                    ),
                ),
                SkillTable(
                    Stats.WOODCUTTING,
                    true,
                    listOf(
                        Material("Magic logs", 10, 20, -60, 60),
                        Material("Yew logs", 10, 20, -50, 90),
                        Material("Mahogany logs", 10, 20, -40, 130),
                        Material("Maple logs", 10, 20, 0, 160),
                        Material("Teak logs", 10, 20, 30, 200),
                        Material("Willow logs", 10, 20, 120, 100),
                        Material("Oak logs", 10, 20, 255, 255),
                    ),
                ),
                SkillTable(
                    Stats.MINING,
                    true,
                    listOf(
                        Material("Runite ore", 1, 2, -60, 40),
                        Material("Adamantite ore", 2, 3, -50, 80),
                        Material("Mithril ore", 3, 5, -20, 140),
                        Material("Gold ore", 8, 11, 0, 160),
                        Material("Coal", 10, 14, 0, 180),
                        Material("Pure essence", 20, 70, 40, 190),
                        Material("Silver ore", 10, 12, 140, 10),
                        Material("Limestone", 2, 7, 200, -20),
                        Material("Iron ore", 5, 15, 255, 255),
                    ),
                ),
                SkillTable(
                    Stats.FISHING,
                    true,
                    listOf(
                        Material("Raw shark", 6, 11, -60, 80),
                        Material("Raw swordfish", 6, 11, -50, 100),
                        Material("Raw lobster", 6, 11, -20, 130),
                        Material("Raw tuna", 6, 11, 10, 160),
                        Material("Raw salmon", 6, 11, 40, 180),
                        Material("Raw anchovies", 6, 11, 160, 0),
                        Material("Raw trout", 6, 11, 255, 255),
                    ),
                ),
            )
    }
}
