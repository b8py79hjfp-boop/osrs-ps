package tp.content.magic

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats

/**
 * Altars that change the spellbook (docs/mechanics/magic-utility.md SB1-SB2): the Ancient Pyramid altar (Ancient
 * Magicks, drains Prayer), the Astral Altar on Lunar Isle (Lunar) and the Ancient, Lunar and Dark altars of a
 * player-owned house (no Prayer drain). The Dark Altar of Arceuus changes nothing: Tyss does that (OPEN-MU11).
 */
public class SpellbookAltars : ContentPlugin {
    override fun ContentScope.register() {
        onOpLoc(PYRAMID_ALTAR, PRAY_AT) { pyramid(player) }
        onOpLoc(ASTRAL_ALTAR, PRAY) { astral(player) }
        onOpLoc(HOUSE_ANCIENT_ALTAR, VENERATE) { house(player, Book.ANCIENT) }
        onOpLoc(HOUSE_LUNAR_ALTAR, VENERATE) { house(player, Book.LUNAR) }
        onOpLoc(HOUSE_DARK_ALTAR, VENERATE) { house(player, Book.ARCEUUS) }
    }

    /** Wiki "Altar (Ancient Pyramid)": drains all Prayer points, then swaps Ancient Magicks and the standard book. */
    private fun pyramid(player: PlayerRef) {
        player.setLevel(Stats.PRAYER, 0)
        val now = Spellbooks.toggle(player, Book.ANCIENT)
        player.message(if (now == Book.ANCIENT) ANCIENT_ON else ANCIENT_OFF)
    }

    /** Wiki "Astral Altar": Lunar spells on from any other book, off (standard) from the Lunar book. */
    private fun astral(player: PlayerRef) {
        val now = Spellbooks.toggle(player, Book.LUNAR)
        player.message(if (now == Book.LUNAR) LUNAR_ON else LUNAR_OFF)
    }

    /** Wiki "Ancient altar" / "Lunar altar": the house altars swap with the standard book and don't drain Prayer. */
    private fun house(player: PlayerRef, book: Int) {
        val now = Spellbooks.toggle(player, book)
        when (book) {
            Book.ANCIENT -> player.message(if (now == book) ANCIENT_ON else ANCIENT_OFF)
            Book.LUNAR -> player.message(if (now == book) LUNAR_ON else LUNAR_OFF)
            else -> Unit // OPEN-MU11: no message is known for the Arceuus book.
        }
    }

    private companion object {
        /** Wiki "Altar (Ancient Pyramid)": loc 6552, option "Pray-at". */
        const val PYRAMID_ALTAR = 6552
        const val PRAY_AT = 1
        const val ANCIENT_ON = "You feel a strange wisdom fill your mind..."
        const val ANCIENT_OFF = "You feel a strange drain upon your memory..."

        /** Wiki "Astral Altar": loc 34771, option 2 "Pray". */
        const val ASTRAL_ALTAR = 34771
        const val PRAY = 2
        const val LUNAR_ON = "Lunar spells activated!"
        const val LUNAR_OFF = "Lunar spells deactivated!"

        /** Cache locs of the built house altars (`poh_altar_ancient`, `_lunar`, `_dark`), option 1 "Venerate". */
        const val HOUSE_ANCIENT_ALTAR = 29147
        const val HOUSE_LUNAR_ALTAR = 29148
        const val HOUSE_DARK_ALTAR = 29149
        const val VENERATE = 1
    }
}
