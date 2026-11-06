package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * Player-to-player trading (docs/mechanics/trade.md TR1–TR8): "Trade with", the offer screen (`trademain` + `tradeside`,
 * offers in inv `tradeoffer` 90, the partner's shown as the client's `invother` 90), the confirm screen
 * (`tradeconfirm`), and one transaction that swaps both offers. Offers live in a real, saved inventory, so a crash or
 * logout mid-trade returns them at the next login.
 */
public class Trade : ContentPlugin {
    private class Session(val a: PlayerRef, val b: PlayerRef) {
        var confirm = false
        val accepted = HashSet<PlayerRef>()
        var closing = false

        fun other(p: PlayerRef) = if (p == a) b else a
    }

    override fun ContentScope.register() {
        onLogin { returnOffer(this) }
        onLogout { sessionOf(this)?.let { decline(it, this) } }
        onOpPlayer(TRADE_OP) { other -> request(player, other) }
        onButton(Components.Tradeside.SIDE_LAYER) { click -> offer(this, click) }
        onButton(Components.Trademain.YOUR_OFFER) { click -> takeBack(this, click) }
        onButton(Components.Trademain.ACCEPT) { accept(this) }
        onButton(Components.Trademain.DECLINE) { closeModal() }
        onButton(Components.Tradeconfirm.TRADE2ACCEPT) { accept(this) }
        onButton(Components.Tradeconfirm.TRADE2DECLINE) { closeModal() }
        onModalClosed(Interfaces.TRADEMAIN) { sessionOf(this)?.let { if (!it.closing) decline(it, this) } }
        onModalClosed(Interfaces.TRADECONFIRM) { sessionOf(this)?.let { if (!it.closing) decline(it, this) } }
    }

    private fun sessionOf(p: PlayerRef): Session? = p[SESSION]

    // ---- TR1 request

    /**
     * TR1: "a player must right-click the person and select the "Trade with" option. The message "Sending trade
     * offer..." will appear in the chat box, and "[player name] wishes to trade with you." will appear in the other
     * player's chat box. That person must then accept the invitation by clicking on that message."
     */
    private fun request(player: PlayerRef, other: PlayerRef) {
        if (sessionOf(player) != null) return
        if (sessionOf(other) != null || other.hasModal) {
            player.message("Other player is busy at the moment.")
            return
        }
        val theirs = other[REQUESTED]
        if (theirs != null && theirs.first == player && player.tick - theirs.second <= REQUEST_TICKS) {
            other[REQUESTED] = null
            open(Session(other, player))
            return
        }
        player[REQUESTED] = other to player.tick
        player.message("Sending trade offer...")
        other.requestMessage(player, "${player.username} wishes to trade with you.", duel = false)
    }

    private fun open(s: Session) {
        for (p in listOf(s.a, s.b)) {
            p.closeModal()
            p[SESSION] = s
        }
        s.closing = true
        for (p in listOf(s.a, s.b)) {
            SideInventory.openPair(p, Interfaces.TRADEMAIN, Interfaces.TRADESIDE)
            SideInventory.bindGrid(p, Components.Tradeside.SIDE_LAYER, Invs.INV, 0, "Offer", "Offer-5", "Offer-10", "Offer-All", "Offer-X")
            p.ifEvents(Components.Tradeside.SIDE_LAYER, 0..LAST_SLOT, OFFER_EVENTS)
            p.ifEvents(Components.Trademain.YOUR_OFFER, 0..LAST_SLOT, OFFER_EVENTS)
            p.transmitInv(Invs.TRADEOFFER)
            p.ifText(Components.Trademain.TITLE, "Trading With: ${s.other(p).username}")
            p.setVarp(Varps.TRADEREMOVED, 0)
            p.setVarp(Varps.TRADEREMOVED_OTHER, 0)
        }
        s.closing = false
        refresh(s)
    }

    /** Both screens follow every change: own offer, the partner's mirror, values, free space, status. */
    private fun refresh(s: Session) {
        for (p in listOf(s.a, s.b)) {
            val o = s.other(p)
            val theirs = o.inv(Invs.TRADEOFFER)
            p.showInv(Invs.TRADEOFFER, IntArray(theirs.size) { theirs.obj(it) }, IntArray(theirs.size) { theirs.count(it) }, other = true)
            p.clientScript(CS_YOUR_VALUES, *values(p))
            p.clientScript(CS_OTHER_VALUES, *values(o))
            p.ifText(Components.Trademain.FREE_SPACE_TEXT, "${o.username} has ${o.inv(Invs.INV).freeSlots} free inventory slots.")
            p.ifText(Components.Trademain.STATUS, statusFor(s, p))
        }
    }

    /** Guide value of each offered slot (client scripts 1216 / 1217 → `9913`). OPEN-TR2: the cache `cost`. */
    private fun values(p: PlayerRef): Array<Any> {
        val offer = p.inv(Invs.TRADEOFFER)
        return Array(SLOTS) { slot -> if (slot < offer.size && offer.obj(slot) >= 0) p.objValue(offer.obj(slot)).toLong() else 0L }
    }

    private fun statusFor(s: Session, p: PlayerRef): String =
        when {
            p in s.accepted -> "Waiting for other player..."
            s.other(p) in s.accepted -> "Other player has accepted."
            else -> ""
        }

    // ---- TR2 offers

    /** TR2: "Offer", "Offer-5", "Offer-10", "Offer-All", "Offer-X" (ops 1..5 of the side grid). */
    private fun offer(player: PlayerRef, click: ButtonClick) {
        val s = sessionOf(player) ?: return
        if (s.confirm) return
        val inv = player.inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != click.obj) return
        if (!player.objTradeable(click.obj)) {
            player.message("You can't trade this item.")
            return
        }
        when (click.op) {
            OP_X -> player.countDialog("Enter amount:") { n -> if (n > 0) moveOffer(s, this, click.slot, click.obj, n, toOffer = true) }
            else -> moveOffer(s, player, click.slot, click.obj, amount(click.op), toOffer = true)
        }
    }

    /** TR2: removing works the same from the offer ("Remove", "Remove-5", ... set by client script 763). */
    private fun takeBack(player: PlayerRef, click: ButtonClick) {
        val s = sessionOf(player) ?: return
        if (s.confirm) return
        val offer = player.inv(Invs.TRADEOFFER)
        if (click.slot !in 0 until offer.size || offer.obj(click.slot) != click.obj) return
        when (click.op) {
            OP_X -> player.countDialog("Enter amount:") { n -> if (n > 0) moveOffer(s, this, click.slot, click.obj, n, toOffer = false) }
            else -> moveOffer(s, player, click.slot, click.obj, amount(click.op), toOffer = false)
        }
    }

    private fun amount(op: Int): Int =
        when (op) {
            OP_1 -> 1
            OP_5 -> FIVE
            OP_10 -> TEN
            else -> Int.MAX_VALUE
        }

    /** Moves up to [wanted] of [obj] between the backpack and the offer (all its stacks for unstackables). */
    private fun moveOffer(s: Session, player: PlayerRef, slot: Int, obj: Int, wanted: Int, toOffer: Boolean) {
        if (sessionOf(player) !== s || s.confirm) return
        val from = if (toOffer) Invs.INV else Invs.TRADEOFFER
        val to = if (toOffer) Invs.TRADEOFFER else Invs.INV
        val view = player.inv(from)
        val total = view.total(obj)
        val amount = minOf(wanted.toLong(), total).toInt()
        if (amount <= 0) return
        val moved =
            player.transaction("trade ${if (toOffer) "offer" else "remove"}") {
                var left = amount
                if (view.obj(slot) == obj) {
                    val n = minOf(left, view.count(slot))
                    if (!move(from, slot, obj, n, to)) return@transaction false
                    left -= n
                }
                var i = 0
                while (left > 0 && i < view.size) {
                    if (view.obj(i) == obj) {
                        val n = minOf(left, view.count(i))
                        if (!move(from, i, obj, n, to)) return@transaction false
                        left -= n
                    }
                    i++
                }
                true
            }
        if (!moved) {
            player.message("You don't have enough inventory space.")
            return
        }
        if (!toOffer) {
            // TR3: "If an item is removed from an offer, a big red flashing exclamation point will appear where the
            // item used to be".
            player.setVarp(Varps.TRADEREMOVED, player.varp(Varps.TRADEREMOVED) or (1 shl slot))
            s.other(player).setVarp(Varps.TRADEREMOVED_OTHER, s.other(player).varp(Varps.TRADEREMOVED_OTHER) or (1 shl slot))
            player.setVarbit(Varbits.TRADE_THIS_PLAYER_REMOVED, 1)
            s.other(player).setVarbit(Varbits.TRADE_OTHER_PLAYER_REMOVED, 1)
        }
        s.accepted.clear()
        refresh(s)
    }

    // ---- TR4 accept, TR5 confirm, TR6 completion

    private fun accept(player: PlayerRef) {
        val s = sessionOf(player) ?: return
        val other = s.other(player)
        if (!fits(other, player)) {
            player.message("Other player doesn't have enough inventory space for this trade.")
            return
        }
        if (!fits(player, other)) {
            player.message("You don't have enough inventory space for this trade.")
            return
        }
        s.accepted += player
        if (s.accepted.size < 2) {
            if (s.confirm) {
                player.ifText(Components.Tradeconfirm.TITLE, "Waiting for other player...")
                other.ifText(Components.Tradeconfirm.TITLE, "Other player has accepted.")
            } else {
                refresh(s)
            }
            return
        }
        if (!s.confirm) openConfirm(s) else complete(s)
    }

    /** Whether [receiver]'s backpack can take [giver]'s whole offer (stacking onto what they keep). */
    private fun fits(receiver: PlayerRef, giver: PlayerRef): Boolean {
        val offer = giver.inv(Invs.TRADEOFFER)
        val inv = receiver.inv(Invs.INV)
        var needed = 0
        val counted = HashSet<Int>()
        for (slot in 0 until offer.size) {
            val obj = offer.obj(slot)
            if (obj < 0) continue
            if (receiver.objStackable(obj)) {
                if (obj in counted) continue
                counted += obj
                if (inv.total(obj) == 0L) needed++
            } else {
                needed++
            }
        }
        // The receiver's own offer comes back out of their backpack, freeing those slots.
        val freedByOwnOffer = (0 until receiver.inv(Invs.TRADEOFFER).size).count { receiver.inv(Invs.TRADEOFFER).obj(it) >= 0 }
        return needed <= inv.freeSlots + freedByOwnOffer
    }

    /** TR5: "a final confirmation ... players are able to review which items have been offered by each party". */
    private fun openConfirm(s: Session) {
        s.confirm = true
        s.accepted.clear()
        s.closing = true
        for (p in listOf(s.a, s.b)) {
            val o = s.other(p)
            SideInventory.openMain(p, Interfaces.TRADECONFIRM)
            SideInventory.restore(p)
            p.ifText(Components.Tradeconfirm.YOUR_OFFER, list(p))
            p.ifText(Components.Tradeconfirm.OTHER_OFFER, list(o))
            p.ifText(Components.Tradeconfirm.TRADEOPPONENT, "Trading with:<br>${o.username}")
        }
        s.closing = false
    }

    /** One line per item; "the full value is shown" (exact counts, not 10M). */
    private fun list(p: PlayerRef): String {
        val offer = p.inv(Invs.TRADEOFFER)
        val lines = ArrayList<String>()
        val counts = LinkedHashMap<Int, Long>()
        for (slot in 0 until offer.size) {
            val obj = offer.obj(slot)
            if (obj >= 0) counts[obj] = (counts[obj] ?: 0L) + offer.count(slot)
        }
        for ((obj, n) in counts) {
            lines +=
                if (n >
                    1
                ) {
                    "<col=ff9040>${p.objName(obj)}</col><col=ffffff> x </col><col=ffff00>%,d</col>".format(n)
                } else {
                    "<col=ff9040>${p.objName(obj)}</col>"
                }
        }
        return if (lines.isEmpty()) "Absolutely nothing!" else lines.joinToString("<br>")
    }

    /** TR6: both offers change hands in one item transaction (brief §12): all of it happens, or none. */
    private fun complete(s: Session) {
        val ok =
            s.a.transaction("trade with ${s.b.username}") {
                giveAll(s.a, s.b) && giveAll(s.b, s.a)
            }
        end(s)
        for (p in listOf(s.a, s.b)) {
            p.closeModal()
            p.message(if (ok) "Accepted trade." else "The trade couldn't be completed.")
            if (!ok) returnOffer(p)
        }
    }

    private fun tp.plugin.ItemTx.giveAll(from: PlayerRef, to: PlayerRef): Boolean {
        val offer = from.inv(Invs.TRADEOFFER)
        for (slot in 0 until offer.size) {
            val obj = offer.obj(slot)
            if (obj < 0) continue
            if (!moveTo(from, Invs.TRADEOFFER, slot, obj, offer.count(slot), to, Invs.INV)) return false
        }
        return true
    }

    /** TR7: "the player will be notified with the 'Other player declined trade.' message"; offers go back. */
    private fun decline(s: Session, by: PlayerRef) {
        end(s)
        for (p in listOf(s.a, s.b)) {
            returnOffer(p)
            if (p != by) {
                p.closeModal()
                p.message("Other player declined trade.")
            }
        }
    }

    private fun end(s: Session) {
        s.closing = true
        for (p in listOf(s.a, s.b)) {
            p[SESSION] = null
            p.setVarbit(Varbits.TRADE_THIS_PLAYER_REMOVED, 0)
            p.setVarbit(Varbits.TRADE_OTHER_PLAYER_REMOVED, 0)
            p.setVarp(Varps.TRADEREMOVED, 0)
            p.setVarp(Varps.TRADEREMOVED_OTHER, 0)
            SideInventory.restore(p)
        }
    }

    /** Puts whatever is in the offer back in the backpack (decline, a crash mid-trade at the next login: TR8). */
    private fun returnOffer(p: PlayerRef) {
        val offer = p.inv(Invs.TRADEOFFER)
        if ((0 until offer.size).none { offer.obj(it) >= 0 }) return
        p.transaction("trade return") {
            for (slot in 0 until offer.size) {
                val obj = offer.obj(slot)
                if (obj >= 0 && !(removeAt(Invs.TRADEOFFER, slot, obj, offer.count(slot)) && addOrDrop(Invs.INV, obj, offer.count(slot)))) {
                    return@transaction false
                }
            }
            true
        }
    }

    private companion object {
        /** Player op slot of "Trade with" (gameframe `Login`). */
        const val TRADE_OP = 4
        const val REQUEST_TICKS = 100L
        const val LAST_SLOT = 27
        const val SLOTS = 28
        const val OP_1 = 1
        const val OP_5 = 2
        const val OP_10 = 3
        const val OP_X = 5
        const val FIVE = 5
        const val TEN = 10

        /** Client scripts storing the per-slot values of your offer / the partner's (varc longs 1512.. / 1540..). */
        const val CS_YOUR_VALUES = 1216
        const val CS_OTHER_VALUES = 1217

        @Suppress("MagicNumber") // ops 1..5 and Examine (10)
        val OFFER_EVENTS: Long = IfEvents.op(1) or IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(5) or IfEvents.op(10)

        val SESSION = AttrKey<Session>("trade_session")
        val REQUESTED = AttrKey<Pair<PlayerRef, Long>>("trade_requested")
    }
}
