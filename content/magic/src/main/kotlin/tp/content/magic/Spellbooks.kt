package tp.content.magic

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Varbits

/**
 * The active spellbook and the temporary Spellbook Swap (docs/mechanics/magic-utility.md SB1-SB3). The book is the
 * `spellbook` varbit (RS Mod `Spellbook`); the temporary swap is remembered in the account's saved values so a logout
 * can't keep the borrowed book.
 */
public class Spellbooks : ContentPlugin {
    override fun ContentScope.register() {
        onLogin { if (saved(SWAP_SAVED) != null) end(this) }
        events.on<tp.plugin.CombatSpellCast> { onCast(it.caster) }
    }

    internal companion object {
        /** Saved-value key of a running Spellbook Swap (SB3). */
        private const val SWAP_SAVED = "magic.spellbook_swap"

        /** Tick at which the running swap runs out. */
        private val SWAP_UNTIL = AttrKey<Long>("magic_swap_until")

        private val SWAP_TIMER = TimerId("spellbook_swap")

        /** Wiki "Spellbook Swap": two minutes to cast one spell from the chosen book, 200 ticks. */
        const val SWAP_TICKS = 200L

        /** Sets the active spellbook (SB1). */
        fun switch(player: PlayerRef, book: Int) {
            player.setVarbit(Varbits.SPELLBOOK, book)
        }

        /** Starts a Spellbook Swap into [book]: it ends after one cast from it or [SWAP_TICKS] ticks (SB3). */
        fun beginSwap(player: PlayerRef, book: Int) {
            switch(player, book)
            player[SWAP_UNTIL] = player.tick + SWAP_TICKS
            player.setSaved(SWAP_SAVED, 1L)
            player.timer(SWAP_TIMER, 1, soft = true) {
                if ((this[SWAP_UNTIL] ?: 0L) <= tick) end(this)
            }
        }

        /** A spell of the active book was cast: a running Spellbook Swap is over (SB3). */
        fun onCast(player: PlayerRef) {
            if (player[SWAP_UNTIL] != null) end(player)
        }

        private fun end(player: PlayerRef) {
            player[SWAP_UNTIL] = null
            player.setSaved(SWAP_SAVED, null)
            player.stopTimer(SWAP_TIMER)
            switch(player, Book.LUNAR)
        }

        /** Altar rule (SB2): [book] if another book is active, else the standard book. Returns the book now active. */
        fun toggle(player: PlayerRef, book: Int): Int {
            val next = if (player.varbit(Varbits.SPELLBOOK) == book) Book.STANDARD else book
            switch(player, next)
            return next
        }
    }
}
