package tp.content.raids.cox

import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Varbits

/**
 * The raid's lore books (cox.md CX58; client-contract 1.2 and 5.5). Five books stand in the rooms as locs that are multis
 * on their read flag: the visible form (id [Book.visible], "Take" on op5) shows while the flag is 0. Taking one gives the
 * book item; reading the item sets the flag, so the loc is gone for the player and the book does not drop again (26 Mar 2025).
 * The Creature keeper's journal and the Dark journal have a flag and an item but no loc here. The book texts are not in the
 * sources, so reading only sets the flag (OPEN-CXUI11).
 */
internal object CoxBooks {
    class Book(val visible: Int, val parent: Int, val item: Int, val flag: Int)

    /** The five book locs (client-contract 5.5): visible form, multi parent, item and read-flag varbit. */
    val LOC_BOOKS: List<Book> =
        listOf(
            Book(
                Locs.RAIDS_VASANISTIRIO_BOOK_VIS,
                Locs.RAIDS_VASANISTIRIO_BOOK,
                Objs.RAIDS_VASANISTIRIO_BOOK,
                Varbits.RAIDS_BOOK_VASANISTIRIO_READ,
            ),
            Book(Locs.RAIDS_TEKTON_BOOK_VIS, Locs.RAIDS_TEKTON_BOOK, Objs.RAIDS_TEKTON_BOOK, Varbits.RAIDS_BOOK_TEKTON_READ),
            Book(Locs.RAIDS_VESPULA_BOOK_VIS, Locs.RAIDS_VESPULA_BOOK, Objs.RAIDS_VESPULA_BOOK, Varbits.RAIDS_BOOK_VESPULA_READ),
            Book(Locs.RAIDS_VANGUARD_BOOK_VIS, Locs.RAIDS_VANGUARD_BOOK, Objs.RAIDS_VANGUARD_BOOK, Varbits.RAIDS_BOOK_VANGUARD_READ),
            Book(
                Locs.RAIDS_HOUNDMASTER_BOOK_VIS,
                Locs.RAIDS_HOUNDMASTER_BOOK,
                Objs.RAIDS_HOUNDMASTER_BOOK,
                Varbits.RAIDS_BOOK_HOUNDMASTER_READ,
            ),
        )

    /** Book items and their flags, with the two that have no loc (client-contract 1.2: 16187, 16190). */
    val ITEM_FLAGS: Map<Int, Int> =
        LOC_BOOKS.associate { it.item to it.flag } +
            mapOf(
                Objs.RAIDS_THIEVINGCHEST_BOOK to Varbits.RAIDS_BOOK_CREATUREKEEPER_READ,
                Objs.RAIDS_REWARD_BOOK to Varbits.RAIDS_BOOK_REWARDCHEST_READ,
            )

    /** "Take" on a book loc: the item goes to the backpack once (not again while the player already holds it). */
    fun take(p: PlayerRef, book: Book) {
        if (p.varbit(book.flag) == 1) return
        val inv = p.inv(Invs.INV)
        if (inv.total(book.item) > 0) {
            p.message(ALREADY_HOLDING)
            return
        }
        if (!p.transaction("cox book take") { add(Invs.INV, book.item) }) p.message(NO_SPACE)
    }

    /** "Read" on a book item: the flag is set (the loc vanishes for this player). */
    fun read(p: PlayerRef, item: Int) {
        val flag = ITEM_FLAGS[item] ?: return
        if (p.varbit(flag) != 1) p.setVarbit(flag, 1)
        p.message(READ_BOOK)
    }

    // OPEN-CXUI1: the texts are not captured.
    private const val ALREADY_HOLDING = "You already have that book."
    private const val NO_SPACE = "You don't have enough inventory space."
    private const val READ_BOOK = "You read the book."
}
