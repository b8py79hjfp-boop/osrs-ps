package tp.content.magic

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.ItemOnItem
import tp.plugin.PlayerRef
import tp.plugin.SpellRunes
import tp.plugin.Stats
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * The one-cast Lunar item spells (docs/mechanics/magic-utility.md LC1-LC5): Humidify, Plank Make, Spin Flax, Superglass
 * Make and Tan Leather. The spells that work through the whole backpack over time are in [LunarBatches].
 */
public class LunarCrafting : ContentPlugin {
    private var vessels: List<Pair<Int, Int>> = emptyList()
    private var planks: Map<Int, Pair<Int, Int>> = emptyMap()
    private var hides: List<Pair<Int, Int>> = emptyList()
    private var giantSeaweed = -1
    private var swampWeed = -1

    override fun ContentScope.register() {
        onWorldStart { load(this) }
        onButton(HUMIDIFY.component, SpellTeleports.OP_CAST) { humidify() }
        onButtonT(PLANK_MAKE.component) { event -> plankMake(event) }
        onButton(SPIN_FLAX.component, SpellTeleports.OP_CAST) { spinFlax() }
        onButton(SUPERGLASS.component, SpellTeleports.OP_CAST) { superglass() }
        onButton(TAN_LEATHER.component, SpellTeleports.OP_CAST) { tanLeather() }
    }

    private fun load(world: WorldRef) {
        vessels = SYMBOL_VESSELS + named(world, VESSELS)
        planks =
            PLANKS
                .map { p -> ItemNames.id(world, p.log) to (ItemNames.id(world, p.plank) to p.coins) }
                .filter { it.first >= 0 && it.second.first >= 0 }
                .toMap()
        hides = named(world, HIDES)
        giantSeaweed = ItemNames.id(world, "Giant seaweed")
        swampWeed = ItemNames.id(world, "Swamp weed")
    }

    private fun named(world: WorldRef, names: List<Pair<String, String>>): List<Pair<Int, Int>> =
        names.map { (from, to) -> ItemNames.id(world, from) to ItemNames.id(world, to) }.filter { it.first >= 0 && it.second >= 0 }

    /** LC1: every empty vessel in the backpack is filled for one cast. */
    private fun PlayerRef.humidify() {
        if (!Casting.ready(this, HUMIDIFY) { humidify() }) return
        val backpack = inv(Invs.INV)
        val targets = vessels.toMap()
        val slots = (0 until backpack.size).filter { backpack.obj(it) in targets }
        if (slots.isEmpty()) return message(NOTHING_TO_FILL)
        val done =
            transaction("humidify") {
                SpellRunes.take(this, this@humidify, HUMIDIFY.runes) &&
                    slots.all { replaceAt(Invs.INV, it, backpack.obj(it), targets.getValue(backpack.obj(it))) }
            }
        if (!done) return message(Casting.NO_RUNES)
        Casting.finish(this, HUMIDIFY)
    }

    /** LC2: one log becomes a plank for 70% of the sawmill's price in coins. */
    private fun PlayerRef.plankMake(event: ItemOnItem) {
        val slot = Casting.targetSlot(this, event) ?: return
        if (!Casting.ready(this, PLANK_MAKE) { plankMake(event) }) return
        val (plank, coins) = planks[event.toObj] ?: return message(NOT_A_LOG)
        val done =
            transaction("plank make") {
                SpellRunes.take(this, this@plankMake, PLANK_MAKE.runes) &&
                    remove(Invs.INV, Objs.COINS, coins) &&
                    replaceAt(Invs.INV, slot, event.toObj, plank)
            }
        if (!done) return message(Casting.NO_RUNES)
        Casting.finish(this, PLANK_MAKE)
    }

    /** LC3: up to five flax become five bow strings, 15 Crafting xp each. */
    private fun PlayerRef.spinFlax() {
        if (!Casting.ready(this, SPIN_FLAX) { spinFlax() }) return
        val flax = minOf(inv(Invs.INV).total(Objs.FLAX).toInt(), FLAX_PER_CAST)
        if (flax == 0) return message("You don't have any flax to spin.")
        val done =
            transaction("spin flax") {
                SpellRunes.take(this, this@spinFlax, SPIN_FLAX.runes) &&
                    remove(Invs.INV, Objs.FLAX, flax) &&
                    addOrDrop(Invs.INV, Objs.BOW_STRING, flax)
            }
        if (!done) return message(Casting.NO_RUNES)
        Casting.finish(this, SPIN_FLAX)
        addXp(Stats.CRAFTING, FLAX_CRAFTING_XP * flax)
    }

    /** LC4: sand buckets and weed in the backpack become molten glass (the bonus yield is OPEN-MU9). */
    private fun PlayerRef.superglass() {
        if (!Casting.ready(this, SUPERGLASS) { superglass() }) return
        val backpack = inv(Invs.INV)
        val single = listOf(Objs.SODA_ASH, Objs.SEAWEED, swampWeed).filter { it >= 0 }
        val singles = single.associateWith { backpack.total(it).toInt() }
        val giant = if (giantSeaweed >= 0) backpack.total(giantSeaweed).toInt() else 0
        val pairs = minOf(backpack.total(Objs.BUCKET_SAND).toInt(), singles.values.sum() + giant * GIANT_SEAWEED_UNITS)
        if (pairs == 0) return message("You need sand and soda ash, seaweed or swamp weed to make glass.")
        // The bonus is rolled before the transaction so a failed cast doesn't consume random numbers twice.
        val glass = pairs + (0 until pairs).count { random(PERCENT) < SUPERGLASS_BONUS_PERCENT }
        val done =
            transaction("superglass make") {
                var left = pairs
                var ok = SpellRunes.take(this, this@superglass, SUPERGLASS.runes) && remove(Invs.INV, Objs.BUCKET_SAND, pairs)
                for ((weed, held) in singles) {
                    val used = minOf(left, held)
                    if (used > 0) ok = ok && remove(Invs.INV, weed, used)
                    left -= used
                }
                if (left > 0) ok = ok && remove(Invs.INV, giantSeaweed, (left + GIANT_SEAWEED_UNITS - 1) / GIANT_SEAWEED_UNITS)
                ok && addOrDrop(Invs.INV, Objs.MOLTEN_GLASS, glass)
            }
        if (!done) return message(Casting.NO_RUNES)
        Casting.finish(this, SUPERGLASS)
        addXp(Stats.CRAFTING, GLASS_CRAFTING_XP * pairs)
    }

    /** LC5: up to five hides in the backpack are tanned (the hard Fremennik Diary unlock is OPEN-TP5). */
    private fun PlayerRef.tanLeather() {
        if (!Casting.ready(this, TAN_LEATHER) { tanLeather() }) return
        val backpack = inv(Invs.INV)
        val targets = hides.toMap()
        val slots = (0 until backpack.size).filter { backpack.obj(it) in targets }.take(HIDES_PER_CAST)
        if (slots.isEmpty()) return message("You have no hides to tan.")
        val done =
            transaction("tan leather") {
                SpellRunes.take(this, this@tanLeather, TAN_LEATHER.runes) &&
                    slots.all { replaceAt(Invs.INV, it, backpack.obj(it), targets.getValue(backpack.obj(it))) }
            }
        if (!done) return message(Casting.NO_RUNES)
        Casting.finish(this, TAN_LEATHER)
    }

    /** A log, the plank it makes and the coin price of Plank Make (Wiki "Plank Make", cost comparison). */
    private class PlankRow(val log: String, val plank: String, val coins: Int)

    private companion object {
        const val PERCENT = 100

        /** OPEN-MU4: no Wiki text for a Humidify cast with nothing to fill, or Plank Make on something else. */
        const val NOTHING_TO_FILL = "You have nothing to fill with water."
        const val NOT_A_LOG = "You can only cast this spell on logs the sawmill takes."

        /** Wiki "Spin Flax": five flax per cast, 15 Crafting xp each. */
        const val FLAX_PER_CAST = 5
        const val FLAX_CRAFTING_XP = 15.0

        /** Wiki "Superglass Make": 10 Crafting xp per bucket of sand; a giant seaweed counts as six seaweed. */
        const val GLASS_CRAFTING_XP = 10.0
        const val GIANT_SEAWEED_UNITS = 6

        /**
         * OPEN-MU9: the Wiki says only that the bonus yield nets "on average 1.3 molten glass per pair of raw materials";
         * a 30% chance of one extra glass per pair gives that average.
         */
        const val SUPERGLASS_BONUS_PERCENT = 30

        /** Wiki "Tan Leather": "Tans up to 5 hides". */
        const val HIDES_PER_CAST = 5

        // Visuals are the cache's spell animations and graphics (OPEN-MU3).
        @Suppress("LongParameterList")
        private fun spell(component: Int, level: Int, xp: Double, ticks: Int, seq: Int, spot: Int, vararg runes: Pair<Int, Int>) =
            UtilitySpell(component, Book.LUNAR, level, xp, runes.toList(), ticks, seq, spot)

        /** Wiki "Humidify": level 68, 65 xp, astral 1 + water 3 + fire 1, 4 ticks. */
        @Suppress("MagicNumber")
        val HUMIDIFY =
            spell(
                Components.Magic_Spellbook.HUMIDIFY,
                68,
                65.0,
                4,
                Seqs.DREAM_PLAYER_HUMIDIFY_SPELL,
                SpotAnims.DREAM_HUMIDIFY_SPELL_SPOTANIM,
                Objs.ASTRALRUNE to 1,
                Objs.WATERRUNE to 3,
                Objs.FIRERUNE to 1,
            )

        /** Wiki "Plank Make": level 86, 90 xp, astral 2 + nature 1 + earth 15, 3 ticks. */
        @Suppress("MagicNumber")
        val PLANK_MAKE =
            spell(
                Components.Magic_Spellbook.PLANK_MAKE,
                86,
                90.0,
                3,
                Seqs.DREAM_PLAYER_MAKE_PLANK_SPELL,
                SpotAnims.DREAM_PLANK_MAKE_SPELL_SPOTANIM,
                Objs.ASTRALRUNE to 2,
                Objs.NATURERUNE to 1,
                Objs.EARTHRUNE to 15,
            )

        /** Wiki "Spin Flax": level 76, 75 xp, astral 1 + nature 2 + air 5, 5 ticks. */
        @Suppress("MagicNumber")
        val SPIN_FLAX =
            spell(
                Components.Magic_Spellbook.SPIN_FLAX,
                76,
                75.0,
                5,
                Seqs.LUNAR_HUMAN_MAGIC_SUMMON2,
                SpotAnims.QUEST_LUNAR_MAGIC_FLAX_STRIM,
                Objs.ASTRALRUNE to 1,
                Objs.NATURERUNE to 2,
                Objs.AIRRUNE to 5,
            )

        /** Wiki "Superglass Make": level 77, 78 xp, air 10 + fire 6 + astral 2, 3 ticks. */
        @Suppress("MagicNumber")
        val SUPERGLASS =
            spell(
                Components.Magic_Spellbook.SUPERGLASS,
                77,
                78.0,
                3,
                Seqs.LUNAR_HUMAN_MAGIC_SUMMON2,
                SpotAnims.QUEST_LUNAR_MAGIC_SUPERGLASS,
                Objs.AIRRUNE to 10,
                Objs.FIRERUNE to 6,
                Objs.ASTRALRUNE to 2,
            )

        /** Wiki "Tan Leather": level 78, 81 xp, astral 2 + nature 1 + fire 5, 3 ticks. */
        @Suppress("MagicNumber")
        val TAN_LEATHER =
            spell(
                Components.Magic_Spellbook.TAN_LEATHER,
                78,
                81.0,
                3,
                Seqs.TAN_LEATHER,
                SpotAnims.LUNAR_TAN_LEATHER,
                Objs.ASTRALRUNE to 2,
                Objs.NATURERUNE to 1,
                Objs.FIRERUNE to 5,
            )

        /** Wiki "Humidify": buckets, jugs, vials, bowls and clay (cache symbols), ... */
        val SYMBOL_VESSELS: List<Pair<Int, Int>> =
            listOf(
                Objs.BUCKET_EMPTY to Objs.BUCKET_WATER,
                Objs.JUG_EMPTY to Objs.JUG_WATER,
                Objs.VIAL_EMPTY to Objs.VIAL_WATER,
                Objs.BOWL_EMPTY to Objs.BOWL_WATER,
                Objs.CLAY to Objs.SOFTCLAY,
            )

        /** ... and waterskins and watering cans (cache names; unwatered saplings and Gricoller's can are OPEN-MU5). */
        val VESSELS: List<Pair<String, String>> =
            listOf(
                "Waterskin(0)" to "Waterskin(4)",
                "Watering can" to "Watering can(8)",
            )

        /** Wiki "Plank Make": the coin price is 70% of the sawmill's (100, 250, 500, 1500, 2500, 5000, 7500). */
        @Suppress("MagicNumber")
        val PLANKS: List<PlankRow> =
            listOf(
                PlankRow("Logs", "Plank", 70),
                PlankRow("Oak logs", "Oak plank", 175),
                PlankRow("Teak logs", "Teak plank", 350),
                PlankRow("Mahogany logs", "Mahogany plank", 1050),
                PlankRow("Camphor logs", "Camphor plank", 1750),
                PlankRow("Ironwood logs", "Ironwood plank", 3500),
                PlankRow("Rosewood logs", "Rosewood plank", 5250),
            )

        /** Wiki "Tan Leather": cowhide, snake hide and the dragonhides (soft leather only: OPEN-MU10). */
        val HIDES: List<Pair<String, String>> =
            listOf(
                "Cowhide" to "Leather",
                "Snake hide" to "Snakeskin",
                "Green dragonhide" to "Green dragon leather",
                "Blue dragonhide" to "Blue dragon leather",
                "Red dragonhide" to "Red dragon leather",
                "Black dragonhide" to "Black dragon leather",
            )
    }
}
