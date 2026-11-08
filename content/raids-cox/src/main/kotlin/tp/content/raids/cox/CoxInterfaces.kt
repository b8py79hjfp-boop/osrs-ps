package tp.content.raids.cox

import tp.content.raids.RaidEvent
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs

/**
 * The Chambers' lobby, storage and reward plumbing (cox.md CX1-CX6, CX37-CX41, CX50-CX51, CX57-CX58; cox/client-contract.md
 * section 2): the recruiting board and party tab ([CoxBoard], [CoxPartyTab]), the storage units ([CoxStorage]), the Ancient
 * chest ([CoxChest]), the Challenge Mode scoreboard and Captain Rimor's capes ([CoxScoreboard]) and the lore books
 * ([CoxBooks]). State lives in raids-core's parties and runs and in world attributes; the plugin has no fields.
 */
public class CoxInterfaces : ContentPlugin {
    override fun ContentScope.register() {
        var world: WorldRef? = null
        onWorldStart { world = this }

        // The recruiting board 499 / 507 (client-contract 2.4 / 2.5); pause-button clicks arrive as op 1 with the sub-id in the slot.
        onOpLoc(Locs.RAIDS_PARTY_RECRUITMENT, OP1) { CoxBoard.openList(player) }
        onButton(Components.Raids_Lobby_Partylist.CONTENTS, PAUSE_BUTTON_OP) { CoxBoard.listButton(this, it.slot) }
        onButton(Components.Raids_Lobby_Partylist.LIST, PAUSE_BUTTON_OP) { CoxBoard.listRow(this, it.slot) }
        onButton(Components.Raids_Lobby_Partydetails.CONTENTS, PAUSE_BUTTON_OP) { CoxBoard.detailsButton(this, it.slot) }
        onButton(Components.Raids_Sidepanel.START, OP1) { CoxStart.fromTab(this) }
        onButton(Components.Raids_Sidepanel.REFRESH, OP1) { CoxPartyTab.refresh(this) }
        onLogin { CoxBoard.login(this) }
        onLogout { CoxBoard.logout(this) }

        // Storage units: the tiny unit is the hotspot itself (ops Private, Build), the built ones Shared, Private, Upgrade.
        onOpLoc(Locs.RAIDS_STORAGE_HOTSPOT, OP2) { CoxStorage.usePrivate(player) }
        onOpLoc(Locs.RAIDS_STORAGE_HOTSPOT, OP5) { CoxStorage.build(player) }
        for (unit in BUILT_UNITS) {
            onOpLoc(unit, OP1) { CoxStorage.useShared(player) }
            onOpLoc(unit, OP2) { CoxStorage.usePrivate(player) }
        }
        // CX38: "Upgrade" on the small, medium and large units (the massive one has none).
        for (unit in BUILT_UNITS.take(UPGRADABLE)) onOpLoc(unit, OP5) { CoxStorage.build(player) }
        onOpLoc(Locs.RAIDS_STORAGE_LOBBY, OP2) { CoxStorage.openPrivate(player, null) }
        onButton(Components.Raids_Storage_Private.ITEMS) { CoxStorage.withdrawClick(this, it, shared = false) }
        onButton(Components.Raids_Storage_Shared.ITEMS) { CoxStorage.withdrawClick(this, it, shared = true) }
        onButton(Components.Raids_Storage_Side.ITEMS) { CoxStorage.depositClick(this, it) }
        onButton(Components.Raids_Storage_Side.DISMISS, OP1) { CoxStorage.dismissWarning(this) }
        onButton(Components.Raids_Storage_Private.SHARED, OP1) { CoxStorage.switchToShared(this) }
        onButton(Components.Raids_Storage_Private.WITHDRAW, OP1) { CoxStorage.withdrawAll(this) }
        onButton(Components.Raids_Storage_Private.BANK, OP1) { CoxStorage.depositOrBankAll(this) }
        onButton(Components.Raids_Storage_Shared.PRIVATE, OP1) { CoxStorage.switchToPrivate(this) }
        onButtonDrag(Components.Raids_Storage_Private.ITEMS) { CoxStorage.drag(this, it) }
        onModalClosed(Interfaces.RAIDS_STORAGE_PRIVATE) { CoxStorage.closed(this) }
        onModalClosed(Interfaces.RAIDS_STORAGE_SHARED) { CoxStorage.closed(this) }

        // The Ancient chest 539 and the exit steps (client-contract 2.9, 5.3 and 5.4).
        onOpLoc(Locs.RAIDS_REWARD_CHEST, OP1) { loc -> CoxChest.open(player, loc) }
        onButton(Components.Raids_Rewards.ITEMS) { CoxChest.itemOp(this, it) }
        onButton(Components.Raids_Rewards.INVENTORY) { CoxChest.takeAll(this) }
        onButton(Components.Raids_Rewards.BANK) { CoxChest.bankAll(this) }
        onButton(Components.Raids_Rewards.DISCARD) { CoxChest.discardAll(this) }
        // The exit steps (lobby and reward chamber) are CoxFlow's Climb: Raids.leave, which runs the unclaimed-loot rule on Left.

        // The Challenge Mode scoreboard 21 and Captain Rimor's "Claim-cape" (op3, cache npc 7595).
        onOpLoc(Locs.RAIDS_CHALLENGE_SCORES, OP1) { CoxScoreboard.open(player) }
        onOpNpc(Npcs.RAIDS_TEMPLE_CAPTAIN, OP3) { rimor -> claimCape(rimor) }

        // The lore books: "Take" on the visible loc form (op5), "Read" on the item.
        for (book in CoxBooks.LOC_BOOKS) onOpLoc(book.visible, OP5) { CoxBooks.take(player, book) }
        for (item in CoxBooks.ITEM_FLAGS.keys) {
            onItemOp(item, READ) { if (inv(Invs.INV).obj(it.slot) == item) CoxBooks.read(this, item) }
        }

        onWorldTick {
            CoxPartyTab.tick(this)
            CoxChest.tick(this)
            if (tick % PRUNE_TICKS == 0L) CoxStorage.prune(this)
        }
        events.on<RaidEvent.Completed> { e ->
            if (e.run.raid.id == CoxRaid.ID) {
                world?.let {
                    CoxChest.completed(it, e.run)
                    CoxScoreboard.completed(it, e)
                }
            }
        }
        events.on<RaidEvent.Left> { e ->
            if (e.run.raid.id == CoxRaid.ID) world?.onlinePlayer(e.member)?.let { left(it, e) }
        }
    }

    /** A member left the raid: interfaces that only exist inside close, the tab goes back, unclaimed loot is placed. */
    private fun left(p: PlayerRef, e: RaidEvent.Left) {
        if (STORAGE_INTERFACES.any { p.isOpen(it) }) p.closeModal()
        CoxPartyTab.leave(p)
        CoxChest.left(p, e.run)
        CoxStorage.clearRaidItems(p)
    }

    private companion object {
        const val OP1 = 1
        const val OP2 = 2
        const val OP3 = 3
        const val OP5 = 5

        /** The op the engine gives a pause-button click that no dialogue script waits for (World `PAUSE_BUTTON_OP`). */
        const val PAUSE_BUTTON_OP = 1

        /** Obj iop text of the lore books and the Dark journal (cache 2757). */
        const val READ = "Read"

        /** Storage maintenance runs this often (ticks). */
        const val PRUNE_TICKS = 600L

        /** The built units (client-contract 2.7): small, medium, large (ops Shared, Private, Upgrade) and massive (no upgrade). */
        const val UPGRADABLE = 3
        val BUILT_UNITS = intArrayOf(Locs.RAIDS_STORAGE_1, Locs.RAIDS_STORAGE_2, Locs.RAIDS_STORAGE_3, Locs.RAIDS_STORAGE_4)

        /** The exit steps in the Olm room and the lobby room: "Climb" (op1) leaves the raid; "Reload" (op2) is the main session's. */

        val STORAGE_INTERFACES = intArrayOf(Interfaces.RAIDS_STORAGE_PRIVATE, Interfaces.RAIDS_STORAGE_SHARED, Interfaces.RAIDS_REWARDS)
    }
}
