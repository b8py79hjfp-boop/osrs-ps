package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits

/** What a player picked from an open [SkillMulti] menu: [items] index [index] and the chosen [quantity]. */
internal class SkillMultiMenu(val items: IntArray, val onPick: PlayerRef.(index: Int, quantity: Int) -> Unit)

/**
 * The "make-X" production menu (interface `skillmulti` 270): up to [MAX_ITEMS] products with the quantity buttons
 * (1, 5, 10, X, All). docs/mechanics/skilling.md MU1-MU4.
 *
 * Usage: `SkillMulti.open(player, SkillMulti.SMELT, "What would you like to smelt?", objs) { index, quantity -> ... }`.
 * The callback runs when the player clicks a product, after the menu is closed; it must validate everything again
 * (levels, materials) because the menu is only a request.
 */
internal object SkillMulti {
    /** The interface has 18 item slots (components `a`..`r`). */
    const val MAX_ITEMS = 18

    /** Largest quantity the menu reports: the client's quantity buttons clamp to 1..28 (client script 2052). */
    const val MAX_QUANTITY = 28

    /** Menu type keys of cache enum 1809 (the verb on each item: "Make", "Smelt", ...); enums 5178 and 1810 pick the quantity buttons. */
    const val MAKE = 22
    const val SMELT = 13
    const val SMITH = 25
    const val SPIN = 16
    const val CUT = 12
    const val STRING = 10
    const val FIRE = 9

    /** Client script `skillmulti_setup`: (type, max quantity, 18 products or -1, suggested quantity, title). */
    private const val CS_SETUP = 2046

    /** Client script that resets the chatbox background before an unclamped chat modal (dialogue.md). */
    private const val CS_CHATBOX_RESET_BACKGROUND = 2379

    /** Op of the resume-pausebutton click the client sends when a product is chosen (World `PAUSE_BUTTON_OP`). */
    const val PICK_OP = 1

    private val MENU = AttrKey<SkillMultiMenu>("skillmulti.menu")

    /** Product slot components, `a` to `r`, are consecutive. */
    fun component(index: Int): Int = Components.Skillmulti.A + index

    /**
     * Opens the menu for [p]: [type] (one of the keys above), [title] over [items] (obj ids, at most [MAX_ITEMS]), with
     * [suggested] preselected as the quantity.
     */
    fun open(
        p: PlayerRef,
        type: Int,
        title: String,
        items: IntArray,
        suggested: Int = MAX_QUANTITY,
        onPick: PlayerRef.(index: Int, quantity: Int) -> Unit,
    ) {
        require(items.size in 1..MAX_ITEMS) { "a skill menu shows 1..$MAX_ITEMS products" }
        p[MENU] = SkillMultiMenu(items, onPick)
        // A menu opened by an op script ends that interaction, or it would run again when the menu closes (MU3).
        p.stopInteraction()
        p.setVarbit(Varbits.CHATMODAL_UNCLAMP, 1)
        p.clientScript(CS_CHATBOX_RESET_BACKGROUND)
        p.openModal(Components.Chatbox.CHATMODAL, Interfaces.SKILLMULTI)
        for (i in items.indices) p.ifEvents(component(i), 0..MAX_QUANTITY, IfEvents.PAUSE_BUTTON)
        val args = ArrayList<Any>(MAX_ITEMS + 4)
        args += type
        args += MAX_QUANTITY
        for (i in 0 until MAX_ITEMS) args += items.getOrElse(i) { -1 }
        args += suggested.coerceIn(1, MAX_QUANTITY)
        args += title
        p.clientScript(CS_SETUP, *args.toTypedArray())
    }

    /** A product click (hostile input: the slot and quantity are checked here). */
    fun picked(p: PlayerRef, index: Int, sub: Int) {
        val menu = p[MENU] ?: return
        p[MENU] = null
        if (index !in menu.items.indices) return
        p.closeModal()
        menu.onPick(p, index, sub.coerceIn(1, MAX_QUANTITY))
    }
}

/** Registers the product buttons of [SkillMulti]; the helper is shared by every production skill. */
public class SkillMultiMenus : ContentPlugin {
    override fun ContentScope.register() {
        for (i in 0 until SkillMulti.MAX_ITEMS) {
            onButton(SkillMulti.component(i), SkillMulti.PICK_OP) { click -> SkillMulti.picked(this, i, click.slot) }
        }
    }
}
