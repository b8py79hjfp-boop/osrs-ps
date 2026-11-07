package tp.content.skills

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * Hunters' loot sacks (docs/mechanics/hunter.md HU47 .. HU49): "Open" rolls the tier's loot table 5 / 7 / 9 / 11 times, each roll
 * picking one of the table's rows with equal chance (the Wiki gives every row 1/7, 1/12 or 1/15), and then the tertiary rolls once
 * per sack. Everything goes into the backpack in one transaction; with no room the sack stays.
 */
public class HunterLootSacks : ContentPlugin {
    /** What one row of a sack's table gives. */
    private sealed interface Row

    /** [min]..[max] of [obj] (noted when [noted]). */
    private class Fixed(val obj: Int, val min: Int, val max: Int, val noted: Boolean = false) : Row

    /** The Wiki's herb table: four noted herbs, the kind picked by [cascade] on Herblore. */
    private data object Herbs : Row

    /** The Wiki's log table: four noted logs, the kind picked by [cascade] on Woodcutting. */
    private data object Logs : Row

    /** One line of a Wiki "Skilling success chart" with `cascade=yes`: [obj] for levels from [req], rolled with [low] / [high]. */
    private class Step(val obj: Int, val req: Int, val low: Int, val high: Int)

    /** A sack tier: the [sack] item, the number of [rolls] and the table [rows] (each 1 in rows.size per roll). */
    private class Tier(val sack: Int, val rolls: Int, val rows: List<Row>)

    override fun ContentScope.register() {
        for (tier in TIERS) onItemOp(tier.sack, OP_OPEN) { click -> open(this, click, tier) }
    }

    private fun open(p: PlayerRef, click: ButtonClick, tier: Tier) {
        val inv = p.inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != tier.sack || p.isDelayed) return
        val loot = LinkedHashMap<Int, Int>()
        repeat(tier.rolls) {
            when (val row = tier.rows[p.random(tier.rows.size)]) {
                is Fixed -> loot.merge(stackOf(p, row), amount(p, row), Int::plus)
                Herbs -> loot.merge(noted(p, cascade(p, HERBS, Stats.HERBLORE)), HERB_STACK, Int::plus)
                Logs -> loot.merge(noted(p, cascade(p, LOGS, Stats.WOODCUTTING)), LOG_STACK, Int::plus)
            }
        }
        if (tier.rolls > BASIC_ROLLS) tertiary(p, tier, loot)
        val given =
            p.transaction("hunter loot sack") {
                var ok = remove(Invs.INV, tier.sack, 1)
                for ((obj, count) in loot) ok = ok && add(Invs.INV, obj, count)
                ok
            }
        if (!given) return p.message("You don't have enough inventory space.")
        p.message("You open the hunters' loot sack.")
    }

    private fun amount(p: PlayerRef, row: Fixed): Int = row.min + p.random(row.max - row.min + 1)

    private fun stackOf(p: PlayerRef, row: Fixed): Int = if (row.noted) noted(p, row.obj) else row.obj

    /** The noted form of [obj], else [obj] itself (an item without a note). */
    private fun noted(p: PlayerRef, obj: Int): Int = p.noted(obj).takeIf { it >= 0 } ?: obj

    /** A Wiki cascade: the first [Step] (best first) whose level is met and whose roll succeeds; the last one is the sure fallback. */
    private fun cascade(p: PlayerRef, steps: List<Step>, stat: Int): Int {
        val level = p.level(stat)
        for (step in steps) {
            if (level >= step.req && (step === steps.last() || p.statRandom(stat, step.low, step.high))) return step.obj
        }
        return steps.last().obj
    }

    // ---- tertiary rolls

    /** The single roll per sack (Wiki "Tertiary"), and the guaranteed torn blueprints at 100 and 250 rumours. */
    private fun tertiary(p: PlayerRef, tier: Tier, loot: MutableMap<Int, Int>) {
        val done = p.saved(HunterRumours.KEY_DONE) ?: 0L
        if (done >= ENHANCED_MILESTONE && p.saved(KEY_TORN_ENHANCED) == null) {
            p.setSaved(KEY_TORN_ENHANCED, 1L)
            loot.merge(Objs.HG_WHISTLE_BLUEPRINT_2_TORN, 1, Int::plus)
        }
        if (done >= PERFECTED_MILESTONE && p.saved(KEY_TORN_PERFECTED) == null && !owns(p, Objs.HG_WHISTLE_BLUEPRINT_3)) {
            p.setSaved(KEY_TORN_PERFECTED, 1L)
            loot.merge(Objs.HG_WHISTLE_BLUEPRINT_3_TORN, 1, Int::plus)
        }
        if (p.random(TERTIARY_DENOMINATOR) == 0) loot.merge(Objs.HG_WHISTLE_BLUEPRINT_2, 1, Int::plus)
        if (tier.sack in EXPERT_SACKS && owns(p, Objs.HG_WHISTLE_BLUEPRINT_2) && p.random(TERTIARY_DENOMINATOR) == 0) {
            loot.merge(Objs.HG_WHISTLE_BLUEPRINT_3, 1, Int::plus)
        }
        if (p.random(TERTIARY_DENOMINATOR) == 0 && !owns(p, Objs.HUNTSMANS_KIT)) loot.merge(Objs.HUNTSMANS_KIT, 1, Int::plus)
        if (p.random(TERTIARY_DENOMINATOR) == 0) {
            // Pieces drop in the order headwear, top, legs, boots; a duplicate only once the set is complete.
            val piece = OUTFIT.firstOrNull { !owns(p, it) } ?: OUTFIT.first()
            loot.merge(piece, 1, Int::plus)
        }
    }

    private fun owns(p: PlayerRef, obj: Int): Boolean =
        p.inv(Invs.INV).total(obj) + p.inv(Invs.WORN).total(obj) + p.inv(Invs.BANK).total(obj) > 0

    private companion object {
        const val OP_OPEN = "Open"
        const val KEY_TORN_ENHANCED = "hunter.sack.tornEnhanced"
        const val KEY_TORN_PERFECTED = "hunter.sack.tornPerfected"

        /** The basic sack (5 rolls) has no tertiary table (Wiki "Hunters' loot sack (basic)"). */
        const val BASIC_ROLLS = 5

        /** Wiki: every tertiary drop is 1/50 per sack. */
        const val TERTIARY_DENOMINATOR = 50

        /** Wiki: a torn enhanced blueprint is guaranteed on opening a sack at 100 rumours, a torn perfected one at 250. */
        const val ENHANCED_MILESTONE = 100L
        const val PERFECTED_MILESTONE = 250L

        /** Wiki: "four noted herbs" / "four noted logs" per herb or log row. */
        const val HERB_STACK = 4
        const val LOG_STACK = 4

        val OUTFIT = intArrayOf(Objs.HG_HUNTER_HOOD, Objs.HG_HUNTER_TOP, Objs.HG_HUNTER_LEGS, Objs.HG_HUNTER_BOOTS)
        val EXPERT_SACKS = setOf(Objs.HG_LOOTSACK_T2, Objs.HG_LOOTSACK_T3)

        /** Quetzal feed (Wiki id 29307; the cache calls the obj `hg_seedsack`). */
        val QUETZAL_FEED = Objs.HG_SEEDSACK

        private fun fixed(obj: Int, min: Int, max: Int = min, noted: Boolean = false) = Fixed(obj, min, max, noted)

        /** The rows shared by all four tiers (Wiki "Loot", Main drops; the low tiers stop earlier in the list). */
        private val COMMON =
            listOf(
                fixed(QUETZAL_FEED, 1),
                fixed(Objs.BLESSED_BONE_SHARD, 100, 200),
                fixed(Objs.HG_SPEARTIP, 15, 30),
                fixed(Objs.HUNTING_KYATT_MEAT, 2, noted = true),
                fixed(Objs.HUNTING_FENNECFOX_MEAT, 3, noted = true),
                fixed(Objs.COINS, 750, 1250),
            )

        private val ADEPT_EXTRA =
            listOf(
                fixed(Objs.BIRD_NEST_EMPTY, 1, noted = true),
                fixed(Objs.BIRD_NEST_EMPTY, 2, 4, noted = true),
                fixed(Objs.SUN_KISSED_BONE, 2, noted = true),
                fixed(Objs.HUNTING_ANTELOPESUN_MEAT, 2, noted = true),
            )

        private val EXPERT_EXTRA =
            listOf(
                fixed(Objs.SUN_KISSED_BONE, 3, noted = true),
                fixed(Objs.HUNTING_ANTELOPEMOON_MEAT, 2, noted = true),
                fixed(Objs.COINS, 2500, 3500),
            )

        /** Wiki "Hunters' loot sack (basic/adept/expert/master)": rolls 5 / 7 / 9 / 11, rows 1/7, 1/12, 1/15, 1/15. */
        val TIERS =
            listOf(
                Tier(Objs.HG_LOOTSACK_T0, 5, COMMON + Logs),
                Tier(Objs.HG_LOOTSACK_T1, 7, COMMON + ADEPT_EXTRA + Herbs + Logs),
                Tier(Objs.HG_LOOTSACK_T2, 9, COMMON + ADEPT_EXTRA + EXPERT_EXTRA + Herbs + Logs),
                Tier(Objs.HG_LOOTSACK_T3, 11, COMMON + ADEPT_EXTRA + EXPERT_EXTRA + Herbs + Logs),
            )

        /** Wiki "Herb chance" chart (cascade, Herblore): lantadyme from 34, cadantine from 13, then kwuarm .. harralander (sure). */
        val HERBS =
            listOf(
                Step(Objs.UNIDENTIFIED_LANTADYME, 34, -30, 60),
                Step(Objs.UNIDENTIFIED_CADANTINE, 13, -10, 70),
                Step(Objs.UNIDENTIFIED_KWUARM, 1, 10, 85),
                Step(Objs.UNIDENTIFIED_AVANTOE, 1, 20, 100),
                Step(Objs.UNIDENTIFIED_IRIT, 1, 30, 115),
                Step(Objs.UNIDENTIFIED_RANARR, 1, 10, 170),
                Step(Objs.UNIDENTIFIED_TARROMIN, 1, 70, -20),
                Step(Objs.UNIDENTIFIED_HARRALANDER, 1, 999, 999),
            )

        /** Wiki "Log chance" chart (cascade, Woodcutting): magic from 50, yew from 36, mahogany from 24, maple, teak (sure). */
        val LOGS =
            listOf(
                Step(Objs.MAGIC_LOGS, 50, -60, 60),
                Step(Objs.YEW_LOGS, 36, -50, 90),
                Step(Objs.MAHOGANY_LOGS, 24, -40, 130),
                Step(Objs.MAPLE_LOGS, 1, 0, 160),
                Step(Objs.TEAK_LOGS, 1, 999, 999),
            )
    }
}
