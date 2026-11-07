package tp.content.magic

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.ItemOnItem
import tp.plugin.PlayerRef
import tp.plugin.SpellRunes
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/** Low and High Level Alchemy (docs/mechanics/magic-utility.md AL1-AL4): one item becomes coins worth a share of its value. */
public class Alchemy : ContentPlugin {
    /** A spell and the share of the item's value it pays, in per cent (Wiki: 40% and 60% of the value). */
    private class Alch(val spell: UtilitySpell, val percent: Int)

    override fun ContentScope.register() {
        for (alch in ALCHS) onButtonT(alch.spell.component) { event -> cast(alch, event) }
    }

    private fun PlayerRef.cast(alch: Alch, event: ItemOnItem) {
        val slot = Casting.targetSlot(this, event) ?: return
        if (!Casting.ready(this, alch.spell) { cast(alch, event) }) return
        val obj = event.toObj
        // AL2: coins and placeholders can't be alchemised; items with no value pay nothing.
        val value = if (isPlaceholder(obj) || obj == Objs.COINS) 0 else objCost(unnoted(obj))
        val coins = value * alch.percent / PERCENT
        if (coins <= 0) return message(NOT_ALCHEMISABLE)
        val paid =
            transaction("alchemy") {
                SpellRunes.take(this, this@cast, alch.spell.runes) &&
                    removeAt(Invs.INV, slot, obj, 1) &&
                    addOrDrop(Invs.INV, Objs.COINS, coins)
            }
        if (!paid) return message(Casting.NO_RUNES)
        Casting.finish(this, alch.spell)
    }

    private companion object {
        const val PERCENT = 100

        /** OPEN-MU4: the Wiki gives no text for an item that can't be alchemised. */
        const val NOT_ALCHEMISABLE = "You can't cast that spell on this item."

        /**
         * Wiki "Low Level Alchemy": level 21, 31 xp, nature 1 + fire 3, 3 ticks, 40%. Wiki "High Level Alchemy": level 55, 65 xp,
         * nature 1 + fire 5, 5 ticks, 60%. Visuals: cache `human_castlowlvlalchemy` / `lowlvlalchemy_casting` and the high pair.
         */
        @Suppress("MagicNumber")
        val ALCHS: List<Alch> =
            listOf(
                Alch(
                    UtilitySpell(
                        Components.Magic_Spellbook.LOW_ALCHEMY,
                        Book.STANDARD,
                        21,
                        31.0,
                        listOf(Objs.NATURERUNE to 1, Objs.FIRERUNE to 3),
                        3,
                        Seqs.HUMAN_CASTLOWLVLALCHEMY,
                        SpotAnims.LOWLVLALCHEMY_CASTING,
                    ),
                    40,
                ),
                Alch(
                    UtilitySpell(
                        Components.Magic_Spellbook.HIGH_ALCHEMY,
                        Book.STANDARD,
                        55,
                        65.0,
                        listOf(Objs.NATURERUNE to 1, Objs.FIRERUNE to 5),
                        5,
                        Seqs.HUMAN_CASTHIGHLVLALCHEMY,
                        SpotAnims.HIGHLVLALCHEMY_CASTING,
                    ),
                    60,
                ),
            )
    }
}
