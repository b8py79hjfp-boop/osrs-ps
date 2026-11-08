package tp.content.raids.tob

import tp.content.raids.Raids
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits

/**
 * The escape crystal (tob.md TB18-TB20; obj `TOB_TELEPORT` 22517): the only way out of the theatre. "Teleport" leaves
 * through [Raids.leave]; a crystal used on a party member who holds one takes both out ([useOn]); logging out inside with
 * one consumes it and the next login takes the player out. The Mysterious Stranger's shop (shops.tsv, id 246, stock 9,500,
 * restock 1) already sells it.
 *
 * OPEN-TB9: a crystal used in the middle of a fight is a plain leave here (no dangerous death; Raids has no hook to
 * mark one). OPEN-TB20: the purpose of `TOB_CRYSTAL_ANTISPAM` is not documented; it stamps the tick of the last use so
 * that a repeated click in the same tick does nothing. "Auto-afk" and "Configure" (TB20) are not implemented.
 */
public class TobCrystal : ContentPlugin {
    override fun ContentScope.register() {
        onItemOp(Objs.TOB_TELEPORT, "Teleport") { click ->
            if (click.slot in 0 until inv(Invs.INV).size && click.obj == Objs.TOB_TELEPORT) teleport(this, click.slot)
        }
        onLogout { loggedOut(this) }
        onLogin { loggedIn(this) }
    }

    internal companion object {
        /** Saved-value key: the crystal was consumed by a logout inside the theatre, so the next login leaves it. */
        private const val PENDING = "tob.crystalPending"

        /** The antispam varbit is 7 bits (cache `TOB_CRYSTAL_ANTISPAM` 14980, bits 4..10): stamps run 1..127. */
        private const val STAMP_RANGE = 127

        /** Shown to a player who uses a crystal outside the theatre (OPEN: the line is not captured). */
        private const val NOT_INSIDE = "Nothing interesting happens."

        /** Shown to the user when the other player holds no crystal (OPEN: the line is not captured). */
        private const val TARGET_HAS_NONE = "That player does not have an escape crystal."

        /** TB20: "Teleport" from the backpack slot [slot]: one crystal is consumed and the player leaves the run. */
        fun teleport(player: PlayerRef, slot: Int) {
            val run = insideRun(player)
            if (run == null) {
                player.message(NOT_INSIDE)
                return
            }
            if (spam(player)) return
            if (!player.transaction("tob escape crystal") { removeAt(Invs.INV, slot, Objs.TOB_TELEPORT, 1) }) return
            Raids.leave(player, run)
        }

        /**
         * TB20: the crystal in backpack slot [slot] of [player] used on [target]: when both are inside the same run and
         * [target] also holds a crystal, both are consumed and both leave. The plugin API owns one handler per
         * `Components.Inventory.ITEMS` item-on-player use, so the dispatcher that owns it must call this.
         */
        fun useOn(player: PlayerRef, target: PlayerRef, slot: Int) {
            val run = insideRun(player) ?: return
            if (target == player || insideRun(target) !== run) return
            if (player.inv(Invs.INV).obj(slot.coerceAtLeast(0)) != Objs.TOB_TELEPORT) return
            val theirs = slotOf(target)
            if (theirs < 0) {
                player.message(TARGET_HAS_NONE)
                return
            }
            if (spam(player)) return
            if (!player.transaction("tob escape crystal pair") { removeAt(Invs.INV, slot, Objs.TOB_TELEPORT, 1) }) return
            if (!target.transaction("tob escape crystal pair") { removeAt(Invs.INV, theirs, Objs.TOB_TELEPORT, 1) }) return
            Raids.leave(player, run)
            Raids.leave(target, run)
        }

        private fun slotOf(p: PlayerRef): Int {
            val inv = p.inv(Invs.INV)
            return (0 until inv.size).firstOrNull { inv.obj(it) == Objs.TOB_TELEPORT } ?: -1
        }

        private fun insideRun(p: PlayerRef) =
            TobFlow.runOf(p)?.takeIf { run -> run.instance?.let { p.coord in it } == true }

        /** OPEN-TB20: true when [p] already used a crystal this tick (stamp in `TOB_CRYSTAL_ANTISPAM`). */
        private fun spam(p: PlayerRef): Boolean {
            val stamp = (p.tick % STAMP_RANGE).toInt() + 1
            if (p.varbit(Varbits.TOB_CRYSTAL_ANTISPAM) == stamp) return true
            p.setVarbit(Varbits.TOB_CRYSTAL_ANTISPAM, stamp)
            return false
        }

        /** Whether [p] holds a crystal or already spent one on this logout (raids-core safe-logout rule). */
        fun holdsOrSpent(p: PlayerRef): Boolean =
            p.saved(PENDING) != null || slotOf(p) >= 0 || p.inv(Invs.WORN).total(Objs.TOB_TELEPORT) > 0

        /** TB20: logging out inside while holding a crystal (backpack or ring slot) consumes it; the next login leaves. */
        private fun loggedOut(p: PlayerRef) {
            if (insideRun(p) == null) return
            val worn = p.inv(Invs.WORN).total(Objs.TOB_TELEPORT) > 0
            val bag = slotOf(p)
            if (bag < 0 && !worn) return
            val ok =
                p.transaction("tob escape crystal logout") {
                    if (bag >= 0) removeAt(Invs.INV, bag, Objs.TOB_TELEPORT, 1) else remove(Invs.WORN, Objs.TOB_TELEPORT, 1)
                }
            if (ok) p.setSaved(PENDING, 1L)
        }

        private fun loggedIn(p: PlayerRef) {
            if (p.saved(PENDING) == null) return
            p.setSaved(PENDING, null)
            val run = TobFlow.runOf(p) ?: return
            Raids.leave(p, run)
        }
    }
}
