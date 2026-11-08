package tp.content.raids.tfa

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Npcs
import tp.symbols.Varbits

/**
 * The Void vault (tfa.md TFA24; client-contract.md 2.6): entering completes the raid (rewards rolled once and paid
 * into each player's `TFA_CHESTS`), the braziers light, then one chest per player appears; the player's own chest
 * (`TFA_VAULT_OWN_CHEST` = 1-based party slot) opens into a rift that shows the loot. With a unique the special chest
 * stands on the northern platform.
 */
internal object TfaVault {
    fun start(run: RaidRun, any: EntityRef) {
        Raids.complete(any, run)
        for (p in run.presentPlayers(any)) {
            p.setVarbit(Varbits.TFA_VAULT_OWN_CHEST, run.party.members.indexOfFirst { it.equals(p.username, ignoreCase = true) } + 1)
            TfaChestUi.sync(p)
        }
        Raids.spawn(any, run, Npcs.TFA_VAULT_EXIT_SMALL, TfaRaid.VAULT_RIFT)
        TfaFlow.state(run).chestsAt = any.tick + TfaConfig.brazierTicks
    }

    fun spawnChests(any: EntityRef, run: RaidRun) {
        for (k in run.party.members.indices) {
            if (k >= TfaRaid.VAULT_CHESTS.size) break
            Raids.spawn(any, run, Npcs.TFA_REWARDS_CHEST_VAULT_1_CLOSED + CHEST_STRIDE * k, TfaRaid.VAULT_CHESTS[k])
        }
        if (run.members.values.any { it.reward?.unique == true }) {
            Raids.spawn(any, run, Npcs.TFA_UNIQUE_CHEST_VAULT_CLOSED, TfaRaid.UNIQUE_CHEST)
        }
    }

    /** Own closed chest "Open": it shatters into the player's rift (the open type) and shows the loot. */
    fun open(player: PlayerRef, chest: NpcRef) {
        chest.transform(chest.type + 1, restoreStats = false)
        TfaChestUi.open(player)
    }

    /** Chest k's CLOSED and OPEN NPC types are consecutive; chests are two ids apart (16736/16737, 16738/16739, ...). */
    const val CHEST_STRIDE = 2
}

/** Vault chest ops (the multi-NPCs resolve to the MINE types for their owner). */
public class TfaVaultPlugin : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.TFA_REWARDS_CHEST_VAULT_CLOSED_MINE, 1) { chest -> TfaVault.open(player, chest) }
        onOpNpc(Npcs.TFA_REWARDS_CHEST_VAULT_OPEN_MINE, 1) { TfaChestUi.open(player) }
        onOpNpc(Npcs.TFA_UNIQUE_CHEST_VAULT_CLOSED, 1) { chest -> chest.transform(Npcs.TFA_UNIQUE_CHEST_VAULT_OPEN, restoreStats = false) }
        onOpNpc(Npcs.TFA_UNIQUE_CHEST_VAULT_OPEN, 1) { TfaChestUi.open(player) }
    }
}
