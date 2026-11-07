package tp.content.magic

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * Spellbook Swap (docs/mechanics/magic-utility.md SB3): picks one of the other spellbooks for one cast or two minutes. The
 * runes and xp are spent when the book is picked.
 */
public class SpellbookSwap : ContentPlugin {
    override fun ContentScope.register() {
        onButton(SPELL.component, SpellTeleports.OP_CAST) { choose() }
    }

    private fun PlayerRef.choose() {
        if (!Casting.ready(this, SPELL) { choose() }) return
        val books = BOOKS.filter { it.first != Book.LUNAR }
        choiceDialog(TITLE, books.map { it.second }) { pick -> swap(books[pick - 1].first) }
    }

    private fun PlayerRef.swap(book: Int) {
        // The menu is answered later: everything is checked again (the book may have changed in the meantime).
        if (!Casting.ready(this, SPELL)) return
        if (!transaction("spellbook swap runes") { SpellRunes.take(this, this@swap, SPELL.runes) }) return message(Casting.NO_RUNES)
        Casting.finish(this, SPELL)
        Spellbooks.beginSwap(this, book)
        // Wiki: cast while standing still, "it will prevent interaction for 4 ticks"; while moving there is no stall.
        if (!isMoving) startScript { delay(SPELL.ticks) }
    }

    private companion object {
        /** OPEN-MU4: the Wiki describes "a choice of the spellbooks" without text. */
        const val TITLE = "Choose a spellbook"

        /** Spellbook varbit value and the menu name of each book. */
        val BOOKS: List<Pair<Int, String>> =
            listOf(Book.STANDARD to "Standard", Book.ANCIENT to "Ancient", Book.LUNAR to "Lunar", Book.ARCEUUS to "Arceuus")

        /**
         * Wiki "Spellbook Swap": level 96, 130 xp, astral 3 + cosmic 2 + law 1, speed 4 ticks. Visuals: cache
         * `dream_player_spellbook_swap` (seq 6299), no graphic (OPEN-MU3).
         */
        @Suppress("MagicNumber")
        val SPELL =
            UtilitySpell(
                Components.Magic_Spellbook.SPELLBOOK_SWAP,
                Book.LUNAR,
                96,
                130.0,
                listOf(Objs.ASTRALRUNE to 3, Objs.COSMICRUNE to 2, Objs.LAWRUNE to 1),
                4,
                Seqs.DREAM_PLAYER_SPELLBOOK_SWAP,
                -1,
            )
    }
}
