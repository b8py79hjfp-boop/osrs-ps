package tp.content.magic

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
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
 * Bake Pie and String Jewellery (docs/mechanics/magic-utility.md LB1-LB2): one click works through every matching item
 * in the backpack, one item per cast and one set of runes per item, until something is interrupted or runs out.
 */
public class LunarBatches : ContentPlugin {
    /** What one cast does to one item: [to] replaces [from]; [level] of the skill is needed and [xp] is gained in it. */
    private class Step(val to: Int, val level: Int, val xp: Double)

    /** A spell, the skill whose xp each item gives and the (from -> step) table it works through. */
    private class Batch(val spell: UtilitySpell, val skill: Int, val steps: () -> Map<Int, Step>)

    private var pies: Map<Int, Step> = emptyMap()
    private var amulets: Map<Int, Step> = emptyMap()

    override fun ContentScope.register() {
        onWorldStart {
            pies = resolve(this, PIES)
            amulets = resolve(this, AMULETS)
        }
        val bake = Batch(BAKE_PIE, Stats.COOKING) { pies }
        val string = Batch(STRING_JEWELLERY, Stats.CRAFTING) { amulets }
        onButton(BAKE_PIE.component, SpellTeleports.OP_CAST) { start(bake) }
        onButton(STRING_JEWELLERY.component, SpellTeleports.OP_CAST) { start(string) }
    }

    private fun resolve(world: WorldRef, rows: List<Row>): Map<Int, Step> {
        val map = HashMap<Int, Step>()
        for (row in rows) {
            val from = ItemNames.id(world, row.from)
            val to = ItemNames.id(world, row.to)
            if (from >= 0 && to >= 0) map[from] = Step(to, row.level, row.xp)
        }
        return map
    }

    private fun PlayerRef.start(batch: Batch) {
        if (!Casting.ready(this, batch.spell) { start(batch) }) return
        step(batch, first = true)
    }

    /** One cast: the first workable item, then the next cast [UtilitySpell.ticks] later (LB1). */
    private fun PlayerRef.step(batch: Batch, first: Boolean) {
        if (!first && !Casting.ready(this, batch.spell)) return
        val steps = batch.steps()
        val backpack = inv(Invs.INV)
        val slots = (0 until backpack.size).filter { backpack.obj(it) in steps }
        // The player must have the skill level the item needs (Wiki "Bake Pie"); string jewellery has no requirement.
        val slot = slots.firstOrNull { steps.getValue(backpack.obj(it)).level <= level(batch.skill) }
        if (slot == null) {
            if (first) message(if (slots.isEmpty()) NOTHING_TO_DO else "You don't have the level to use that item with this spell.")
            return
        }
        val obj = backpack.obj(slot)
        val item = steps.getValue(obj)
        val done =
            transaction(REASON) {
                SpellRunes.take(this, this@step, batch.spell.runes) && replaceAt(Invs.INV, slot, obj, item.to)
            }
        if (!done) {
            if (first) message(Casting.NO_RUNES)
            return
        }
        Casting.finish(this, batch.spell)
        addXp(batch.skill, item.xp)
        // Walking, a click or a modal removes the weak script, which ends the batch (LB1).
        weakQueue(batch.spell.ticks) { player.step(batch, first = false) }
    }

    /** A pie or amulet by cache name (Wiki "Bake Pie" and "String Jewellery"), its result, level and skill xp. */
    private class Row(val from: String, val to: String, val level: Int, val xp: Double)

    private companion object {
        const val REASON = "lunar batch"
        const val NOTHING_TO_DO = "You have nothing to cast this spell on."

        /** Wiki "Bake Pie": level 65, 60 xp, astral 1 + fire 5 + water 4, 3 ticks, one cast per pie. */
        @Suppress("MagicNumber")
        val BAKE_PIE =
            UtilitySpell(
                Components.Magic_Spellbook.BAKE_PIE,
                Book.LUNAR,
                65,
                60.0,
                listOf(Objs.ASTRALRUNE to 1, Objs.FIRERUNE to 5, Objs.WATERRUNE to 4),
                3,
                Seqs.LUNAR_HUMAN_MAGIC_SUMMON2,
                SpotAnims.QUEST_LUNAR_BAKEPIE_SPOTANIM,
            )

        /** Wiki "String Jewellery": level 80, 83 xp, astral 2 + earth 10 + water 5, 3 ticks, 4 Crafting xp per amulet. */
        @Suppress("MagicNumber")
        val STRING_JEWELLERY =
            UtilitySpell(
                Components.Magic_Spellbook.STRING_JEWEL,
                Book.LUNAR,
                80,
                83.0,
                listOf(Objs.ASTRALRUNE to 2, Objs.EARTHRUNE to 10, Objs.WATERRUNE to 5),
                3,
                Seqs.LUNAR_HUMAN_MAGIC_SUMMON1,
                SpotAnims.QUEST_LUNAR_SPELLBOOK_STRING_JEWELERY_SPOT_ANIM,
            )

        /** Wiki "Bake Pie" table: the pie, its Cooking level and the Cooking xp of the baked pie. */
        @Suppress("MagicNumber")
        val PIES: List<Row> =
            listOf(
                Row("Uncooked berry pie", "Redberry pie", 10, 78.0),
                Row("Uncooked meat pie", "Meat pie", 20, 110.0),
                Row("Raw mud pie", "Mud pie", 29, 128.0),
                Row("Uncooked apple pie", "Apple pie", 30, 130.0),
                Row("Raw garden pie", "Garden pie", 34, 138.0),
                Row("Raw fish pie", "Fish pie", 47, 164.0),
                Row("Uncooked botanical pie", "Botanical pie", 52, 180.0),
                Row("Uncooked mushroom pie", "Mushroom pie", 60, 200.0),
                Row("Raw admiral pie", "Admiral pie", 70, 210.0),
                Row("Uncooked dragonfruit pie", "Dragonfruit pie", 73, 220.0),
                Row("Raw wild pie", "Wild pie", 85, 240.0),
                Row("Raw summer pie", "Summer pie", 95, 260.0),
            )

        /**
         * Wiki "String Jewellery": the amulets and the holy symbol, 4 Crafting xp each. Salve amulets, m'speak amulets and
         * emblems are left out (OPEN-MU5).
         */
        @Suppress("MagicNumber")
        val AMULETS: List<Row> =
            listOf("Gold", "Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone", "Onyx", "Zenyte", "Opal", "Jade", "Topaz")
                .map { gem -> Row("$gem amulet (u)", "$gem amulet", 0, 4.0) } + Row("Unstrung symbol", "Holy symbol", 0, 4.0)
    }
}
