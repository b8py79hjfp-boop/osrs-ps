package tp.content.magic

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * Bones to Bananas and Bones to Peaches (docs/mechanics/magic-utility.md BF1-BF2): every unnoted ordinary bone in the
 * backpack becomes the fruit, for one set of runes.
 */
public class BonesToFruit : ContentPlugin {
    private var bones: Set<Int> = emptySet()

    override fun ContentScope.register() {
        onWorldStart { bones = BONE_NAMES.flatMap { name -> objIdsNamed(name) }.toSet() }
        onButton(BANANAS.component, SpellTeleports.OP_CAST) { cast(BANANAS, Objs.BANANA) }
        onButton(PEACHES.component, SpellTeleports.OP_CAST) { cast(PEACHES, Objs.PEACH) }
    }

    private fun PlayerRef.cast(spell: UtilitySpell, fruit: Int) {
        if (!Casting.ready(this, spell) { cast(spell, fruit) }) return
        val backpack = inv(Invs.INV)
        val slots = (0 until backpack.size).filter { backpack.obj(it) in bones }
        if (slots.isEmpty()) return message(NO_BONES)
        val done =
            transaction("bones to fruit") {
                SpellRunes.take(this, this@cast, spell.runes) && slots.all { replaceAt(Invs.INV, it, backpack.obj(it), fruit) }
            }
        if (!done) return message(Casting.NO_RUNES)
        Casting.finish(this, spell)
    }

    private companion object {
        /** Wiki "Bones to Peaches": the message for a cast without bones. */
        const val NO_BONES = "You aren't holding any bones!"

        /**
         * Wiki "Bones to Bananas": normal, burnt, bat, bleached, jogre, wolf, monkey, Alan's, animals' and big bones; the
         * long and curved bones are ignored since 11 May 2017. Cache names (all ids of a name count).
         */
        val BONE_NAMES: List<String> =
            listOf("Bones", "Burnt bones", "Bat bones", "Bleached bones", "Jogre bones", "Wolf bones", "Monkey bones", "Big bones")
                .plus(listOf("Alan's bones", "Animals' bones"))

        /**
         * Wiki "Bones to Bananas": level 15, 25 xp, nature 1 + earth 2 + water 2, speed 1 tick. "Bones to Peaches": level 60,
         * 35.5 xp, nature 2 + water 4 + earth 4, speed 1 tick. Visuals: cache `human_castbonestobananas` and `bonestobananas_casting`.
         */
        @Suppress("MagicNumber")
        val BANANAS =
            UtilitySpell(
                Components.Magic_Spellbook.BONES_BANANAS,
                Book.STANDARD,
                15,
                25.0,
                listOf(Objs.NATURERUNE to 1, Objs.EARTHRUNE to 2, Objs.WATERRUNE to 2),
                1,
                Seqs.HUMAN_CASTBONESTOBANANAS,
                SpotAnims.BONESTOBANANAS_CASTING,
            )

        @Suppress("MagicNumber")
        val PEACHES =
            UtilitySpell(
                Components.Magic_Spellbook.BONES_PEACHES,
                Book.STANDARD,
                60,
                35.5,
                listOf(Objs.NATURERUNE to 2, Objs.WATERRUNE to 4, Objs.EARTHRUNE to 4),
                1,
                Seqs.HUMAN_CASTBONESTOBANANAS,
                SpotAnims.BONESTOBANANAS_CASTING,
            )
    }
}
