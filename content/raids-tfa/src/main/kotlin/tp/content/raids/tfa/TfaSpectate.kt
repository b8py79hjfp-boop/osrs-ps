package tp.content.raids.tfa

import tp.content.raids.PartyState
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.TeleportRequest
import tp.plugin.WorldRef
import tp.plugin.choice
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Locs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Spectating (tfa.md TFA17; client-contract.md 2.1): the Strategy Board in the lobby lists raids in progress that allow
 * spectators (up to 100 each); a spectator becomes an observer (instances.md IN11) that follows one party member's
 * view, chosen on the HUD's party orbs, or the room ("Spectate Room"), and leaves with the HUD's "Leave". Dead raid
 * members waiting for their room to end can watch a teammate the same way (spectate mode 1).
 */
public class TfaSpectate : ContentPlugin {
    override fun ContentScope.register() {
        onOpLoc(Locs.TFA_BOARD_GUTHIXIAN01_PLANNING01, 1) {
            val runs =
                Raids
                    .of(player)
                    .activeRuns
                    .filter { open(it) }
                    .take(MAX_LISTED)
            if (runs.isEmpty()) {
                player.message(NONE_TO_WATCH)
                return@onOpLoc
            }
            val pick = choice("Spectate which raid?", *runs.map { "Party of ${it.party.leader} (${it.party.members.size})" }.toTypedArray())
            val run = runs.getOrNull(pick - 1) ?: return@onOpLoc
            start(player, run)
        }
        // HUD 974:5 CONTENT_NAMES "Spectate" (client script 10040): args (slot, 1); slot -1 = the room.
        onScriptTrigger(Components.Tfa_Hud.CONTENT_NAMES, "ii") { t ->
            val slot = t.args.getOrNull(0) as? Int ?: return@onScriptTrigger
            watch(this, slot)
        }
        // HUD 974:1 TRIGGER_GENERIC (client script 10035): arg 2 = "Leave" for external spectators.
        onScriptTrigger(Components.Tfa_Hud.TRIGGER_GENERIC, "i") { t ->
            if (t.args.getOrNull(0) == ARG_LEAVE && this[WATCHING] != null) stop(this, toLobby = true)
        }
        events.on<TeleportRequest> { req ->
            if (req.handled || req.player[WATCHING] == null) return@on
            req.handled = true
            req.denied = NO_TELEPORT
        }
        onLogout { if (this[WATCHING] != null) stop(this, toLobby = true) }
        // A run that closes (completed, wiped, abandoned) releases its spectators; the engine has moved them out.
        events.on<tp.plugin.InstanceLost> { e -> if (e.player[WATCHING] != null) stop(e.player, toLobby = true) }
        onWorldTick { tick(this) }
    }

    private fun open(run: RaidRun): Boolean =
        run.raid.id == TfaRaid.ID &&
            run.state !is RunState.Building &&
            run.state != RunState.Closed &&
            run.instance != null &&
            (run.party.settings[TfaParties.SETTING_SPECTATE_OFF] ?: 0) == 0

    /** TFA17: becomes an external spectator of [run]. */
    internal fun start(player: PlayerRef, run: RaidRun) {
        if (Raids.of(player).partyOf(player.username)?.state is PartyState.Locked) {
            player.message(IN_RAID)
            return
        }
        if (run.spectators.size >= MAX_SPECTATORS) {
            player.message(FULL)
            return
        }
        run.spectators += player.username
        player[WATCHING] = run.id.value
        player[FOLLOW] = 0
        player.observer = true
        player.setInstanceExit(SPECTATE_RETURN)
        player.setVarbit(Varbits.TFA_CLIENT_SPECTATE_MODE, MODE_EXTERNAL)
        player.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.TFA_HUD)
        TfaFlow.state(run).hudDirty = true
    }

    /** An orb click: follow party slot [slot] (0-based), or the room for -1. */
    private fun watch(player: PlayerRef, slot: Int) {
        val run = watchedRun(player) ?: deadMemberRun(player) ?: return
        if (slot != ROOM_SLOT && run.party.members.getOrNull(slot) == null) return
        if (player[WATCHING] == null) {
            // A dead member waiting for the room to end (mode 1).
            player[WATCHING] = run.id.value
            player.observer = true
            player.setVarbit(Varbits.TFA_CLIENT_SPECTATE_MODE, MODE_TEAMMATE)
        }
        player[FOLLOW] = slot
        player.setVarbit(Varbits.TFA_SPECTATE_SLOT, if (slot == ROOM_SLOT) 0 else slot + 1)
    }

    private fun watchedRun(player: PlayerRef): RaidRun? = player[WATCHING]?.let { Raids.of(player).run(it) }

    private fun deadMemberRun(player: PlayerRef): RaidRun? {
        val run = Raids.of(player).runOf(player.username) ?: return null
        val m = run.member(player.username) ?: return null
        return run.takeIf { it.raid.id == TfaRaid.ID && m.inside && !m.alive && it.state is RunState.InRoom }
    }

    internal fun stop(player: PlayerRef, toLobby: Boolean) {
        val id = player[WATCHING] ?: return
        player[WATCHING] = null
        player[FOLLOW] = null
        player.observer = false
        player.setVarbit(Varbits.TFA_CLIENT_SPECTATE_MODE, 0)
        player.setVarbit(Varbits.TFA_SPECTATE_SLOT, 0)
        val run = Raids.of(player).run(id)
        val member = run?.member(player.username)
        if (member == null) {
            run?.spectators?.remove(player.username)
            player.setInstanceExit(null)
            TfaHud.close(player)
            if (toLobby) player.teleport(SPECTATE_RETURN)
        } else {
            // A teammate stops watching: back to the room's waiting tile (or wherever the flow put them).
            (run.state as? RunState.InRoom)?.room?.let { player.teleport(run.at(it.waitAt ?: it.entry)) }
        }
        run?.let { TfaFlow.state(it).hudDirty = true }
    }

    /** Every tick: spectators follow their pick; the run's end, or a member back alive, ends the watch. */
    private fun tick(world: WorldRef) {
        for (run in Raids.of(world).activeRuns) {
            if (run.raid.id != TfaRaid.ID) continue
            val inst = run.instance ?: continue
            // TFA14: a dead member waits by watching the fight (as a teammate spectator) until the room ends, rather
            // than standing up in the arena again.
            if (run.state is RunState.InRoom) watchWhileDead(world, run)
            // Every member is checked (not only the dead): one back alive stops watching on the next tick.
            val watchers = run.spectators.toList() + run.members.keys
            for (name in watchers) follow(world, run, inst, name)
            for (name in run.party.members) {
                world.onlinePlayer(name)?.setVarbit(Varbits.TFA_CLIENT_NUM_EXTERNAL_SPECTATORS, minOf(run.spectators.size, MAX_COUNTER))
            }
        }
    }

    private fun watchWhileDead(world: WorldRef, run: RaidRun) {
        for (m in run.members.values) {
            if (m.alive || !m.inside || !m.online) continue
            val p = world.onlinePlayer(m.name) ?: continue
            if (p[WATCHING] != null || p.hitpoints <= 0) continue
            p[WATCHING] = run.id.value
            p[FOLLOW] =
                run.party.members
                    .indexOfFirst { n -> run.member(n)?.let { it.alive && it.inside } == true }
                    .coerceAtLeast(0)
            p.observer = true
            p.setVarbit(Varbits.TFA_CLIENT_SPECTATE_MODE, MODE_TEAMMATE)
            TfaFlow.state(run).hudDirty = true
        }
    }

    private fun follow(world: WorldRef, run: RaidRun, inst: tp.plugin.InstanceRef, name: String) {
        val p = world.onlinePlayer(name) ?: return
        if (p[WATCHING] != run.id.value) return
        val m = run.member(name)
        val ended = run.state == RunState.Closed || run.state == RunState.Abandoned || run.state is RunState.Failed
        val memberBack = m != null && (m.alive || !m.inside)
        if (ended || memberBack) {
            stop(p, toLobby = m == null)
            return
        }
        val slot = p[FOLLOW] ?: 0
        val target =
            if (slot == ROOM_SLOT) {
                (run.state as? RunState.InRoom)?.room?.entry?.let { run.at(it) }
            } else {
                followTile(world, run, slot)
            }
        if (target != null && target in inst && target != p.coord) p.teleport(target)
    }

    /** The tile of party slot [slot], or of the first present member when that one is not inside. */
    private fun followTile(world: WorldRef, run: RaidRun, slot: Int): CoordGrid? {
        val inst = run.instance ?: return null
        val order =
            listOf(slot) +
                run.party.members.indices
                    .filter { it != slot }
        for (k in order) {
            val name = run.party.members.getOrNull(k) ?: continue
            val m = run.member(name) ?: continue
            if (!m.inside || !m.alive) continue
            val p = world.onlinePlayer(name) ?: continue
            if (p.coord in inst && !p.observer) return p.coord
        }
        return null
    }

    internal companion object {
        val WATCHING = AttrKey<Long>("tfa_spectating")
        val FOLLOW = AttrKey<Int>("tfa_spectate_follow")

        /** TFA17: "up to 100 spectators per raid". */
        const val MAX_SPECTATORS = 100

        /** A chat choice lists at most five options. */
        const val MAX_LISTED = 5
        const val ROOM_SLOT = -1
        const val ARG_LEAVE = 2

        /** client-contract 2.1: SPECTATE_MODE 1 = in-raid (teammates), 2 = external (adds "Leave"). */
        const val MODE_TEAMMATE = 1
        const val MODE_EXTERNAL = 2

        /** `TFA_CLIENT_NUM_EXTERNAL_SPECTATORS` is 8 bits. */
        const val MAX_COUNTER = 255

        /** In front of the Strategy Board (4116, 5547) in the lobby (OPEN-NX2: a free tile east of it). */
        val SPECTATE_RETURN = CoordGrid(4118, 5547)

        /** TFA17's refusal; the others are OPEN-NX8 placeholders. */
        const val FULL = "That raid has reached the maximum number of spectators."
        const val NONE_TO_WATCH = "There are no raids you can spectate right now."
        const val IN_RAID = "You can't spectate while your party is in a raid."
        const val NO_TELEPORT = "You can't teleport while spectating."
    }
}
