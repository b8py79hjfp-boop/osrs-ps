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
 * Superheat Item (docs/mechanics/magic-utility.md SH1-SH4): smelts the ore it is cast on, and the other ores and coal
 * the bar needs, into a bar without a furnace.
 */
public class Superheat : ContentPlugin {
    /** A bar: its cache name, Smithing level and xp, and its materials by cache name (ore name to count). */
    private class Bar(val name: String, val level: Int, val xp: Double, val materials: List<Pair<String, Int>>)

    /** [Bar] with the objs looked up once at world start. */
    private class Resolved(val bar: Bar, val barObj: Int, val materials: List<Pair<Int, Int>>)

    private var recipes: List<Resolved> = emptyList()

    override fun ContentScope.register() {
        onWorldStart { recipes = BARS.map { bar -> resolve(this, bar) }.filter { it.barObj >= 0 } }
        onButtonT(SPELL.component) { event -> cast(event) }
    }

    private fun resolve(world: WorldRef, bar: Bar): Resolved =
        Resolved(bar, ItemNames.id(world, bar.name), bar.materials.map { (name, count) -> ItemNames.id(world, name) to count })

    private fun PlayerRef.cast(event: ItemOnItem) {
        Casting.targetSlot(this, event) ?: return
        if (!Casting.ready(this, SPELL) { cast(event) }) return
        val obj = event.toObj
        // SH1: noted ore and every other item are refused with the Wiki's message; elemental ore has its own.
        if (objName(obj) == ELEMENTAL_ORE) return message(TOO_COLD)
        val candidates = recipes.filter { r -> r.materials.any { (ore, _) -> ore == obj } && obj != Objs.COAL }
        if (candidates.isEmpty()) return message(NOT_ORE)
        // SH2: iron ore with enough coal always makes steel (Wiki); otherwise the cheapest bar the ore is in that can be made.
        val backpack = inv(Invs.INV)
        val recipe =
            candidates
                .filter { r -> r.materials.all { (ore, count) -> backpack.total(ore) >= count } }
                .maxByOrNull { it.bar.level } ?: return message("You do not have the materials to smelt that.")
        if (level(Stats.SMITHING) < recipe.bar.level) {
            return message("You need a Smithing level of ${recipe.bar.level} to smelt ${objName(recipe.barObj)}.")
        }
        val smelted =
            transaction("superheat") {
                SpellRunes.take(this, this@cast, SPELL.runes) &&
                    recipe.materials.all { (ore, count) -> remove(Invs.INV, ore, count) } &&
                    add(Invs.INV, recipe.barObj, 1)
            }
        if (!smelted) return message(Casting.NO_RUNES)
        Casting.finish(this, SPELL)
        addXp(Stats.SMITHING, smithingXp(recipe))
    }

    /** SH3: the gold bar's xp rises to 56.2 with goldsmith gauntlets (Wiki "Superheat Item": their effect applies). */
    private fun PlayerRef.smithingXp(recipe: Resolved): Double {
        val gloves = inv(Invs.WORN).obj(GLOVES_SLOT)
        val goldsmith = gloves >= 0 && objName(gloves) == GOLDSMITH_GAUNTLETS
        return if (goldsmith && recipe.bar.name == GOLD_BAR) GOLDSMITH_GOLD_XP else recipe.bar.xp
    }

    private companion object {
        /**
         * Wiki "Superheat Item": level 43, 53 xp, nature 1 + fire 4, 3 ticks. Visuals: cache `human_castsuperheatitem` and
         * `superheatitem_casting`.
         */
        @Suppress("MagicNumber")
        val SPELL =
            UtilitySpell(
                Components.Magic_Spellbook.SUPERHEAT,
                Book.STANDARD,
                43,
                53.0,
                listOf(Objs.NATURERUNE to 1, Objs.FIRERUNE to 4),
                3,
                Seqs.HUMAN_CASTSUPERHEATITEM,
                SpotAnims.SUPERHEATITEM_CASTING,
            )

        /** Worn slot of the gloves (RS Mod wear positions; same as content/skills `SmithingData.GLOVES_SLOT`). */
        const val GLOVES_SLOT = 9
        const val GOLDSMITH_GAUNTLETS = "Gauntlets of goldsmithing"
        const val GOLD_BAR = "Gold bar"

        /** Wiki "Smelting": gold bars give 56.2 xp with the gauntlets. */
        const val GOLDSMITH_GOLD_XP = 56.2
        const val ELEMENTAL_ORE = "Elemental ore"

        /** Wiki "Superheat Item", Trivia. */
        const val NOT_ORE = "You need to cast superheat item on ore."

        /** Wiki "Superheat Item", Trivia. */
        const val TOO_COLD = "Even this spell is not hot enough to heat this item."

        /**
         * Bars the Wiki's cost table lists (level, Smithing xp, materials) without the members' alloys (lead, lovakite,
         * cupronickel: OPEN-MU5). Ores and bars are cache names.
         */
        @Suppress("MagicNumber")
        val BARS: List<Bar> =
            listOf(
                Bar("Bronze bar", 1, 6.2, listOf("Tin ore" to 1, "Copper ore" to 1)),
                Bar("Iron bar", 15, 12.5, listOf("Iron ore" to 1)),
                Bar("Silver bar", 20, 13.7, listOf("Silver ore" to 1)),
                Bar("Steel bar", 30, 17.5, listOf("Iron ore" to 1, "Coal" to 2)),
                Bar("Gold bar", 40, 22.5, listOf("Gold ore" to 1)),
                Bar("Mithril bar", 50, 30.0, listOf("Mithril ore" to 1, "Coal" to 4)),
                Bar("Adamantite bar", 70, 37.5, listOf("Adamantite ore" to 1, "Coal" to 6)),
                Bar("Runite bar", 85, 50.0, listOf("Runite ore" to 1, "Coal" to 8)),
            )
    }
}
