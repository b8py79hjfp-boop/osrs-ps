package tp.content.raids

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathPolicyRequest
import tp.plugin.HitEvent
import tp.plugin.InstanceLost
import tp.plugin.LoginEvent
import tp.plugin.LogoutEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.TeleportRequest

/**
 * Wires game events into the raid framework (docs/mechanics/raids/common.md): damage on a run's NPCs feeds its ledger,
 * deaths inside a run follow its death policy, logouts and logins its disconnect policy, teleports out are refused,
 * and empty or finished runs are torn down after a grace period.
 */
public class RaidsPlugin : ContentPlugin {
    override fun ContentScope.register() {
        events.on<HitEvent> { e ->
            val npc = e.target as? NpcRef ?: return@on
            val runId = RaidRun.npcRun(npc) ?: return@on
            val player = e.source as? PlayerRef ?: return@on
            val run = Raids.of(npc).run(runId) ?: return@on
            Raids.damage(run, player.username, npc.type, e.damage)
        }
        events.on<DeathPolicyRequest> { req ->
            val run = runInside(req.player) ?: return@on
            Raids.died(req.player, run, req)
        }
        events.on<LogoutEvent> { e -> loggedOut(e.player) }
        events.on<LoginEvent> { e -> loggedIn(e.player) }
        events.on<InstanceLost> { e ->
            // IN8: the run is gone (restart, teardown): raid-only items of every raid are removed.
            val raids = Raids.of(e.player)
            for (run in raids.activeRuns.toList()) run.member(e.player.username)?.let { it.inside = false }
            raids.allDefinitions().forEach { Raids.removeRaidItems(e.player, it) }
        }
        events.on<TeleportRequest> { req ->
            if (runInside(req.player) == null || req.handled) return@on
            req.handled = true
            req.denied = Raids.NO_TELEPORT
        }
        onWorldTick { worldTick(this) }
    }

    /** End of tick: drivers sync changed runs; empty or finished runs close after the grace period. */
    private fun worldTick(world: tp.plugin.WorldRef) {
        for (run in Raids.of(world).activeRuns.toList()) {
            if (run.dirty) {
                run.dirty = false
                run.raid.driver.sync(run)
            }
            if (run.state == RunState.Building) continue
            if (run.members.values.any { it.inside && it.online }) {
                run.emptySinceTick = -1
                continue
            }
            if (run.emptySinceTick < 0) run.emptySinceTick = world.tick
            val nobodyInside = run.members.values.none { it.inside }
            val ended = run.state == RunState.Completed || run.state is RunState.Failed || run.state == RunState.Abandoned
            if (!ended && nobodyInside) run.state = RunState.Abandoned
            if (world.tick - run.emptySinceTick >= Raids.EMPTY_GRACE_TICKS || (ended && nobodyInside)) Raids.close(world, run)
        }
    }

    private fun runInside(player: PlayerRef): RaidRun? {
        val run = Raids.of(player).runOf(player.username) ?: return null
        val m = run.member(player.username) ?: return null
        val inst = run.instance ?: return null
        return if (m.inside && player.coord in inst) run else null
    }

    private fun loggedOut(player: PlayerRef) {
        val run = runInside(player) ?: return
        val m = run.member(player.username) ?: return
        m.online = false
        if (run.raid.disconnect == DisconnectPolicy.DEATH_IN_ENCOUNTER &&
            (run.state as? RunState.InRoom)?.room?.kind == RoomKind.ENCOUNTER
        ) {
            m.deaths++
            run.teamDeaths++
            m.alive = false
            m.points = maxOf(0.0, m.points - run.raid.contribution.deathPenalty(m, run))
        }
        run.markDirty()
        player.events.publish(RaidEvent.Disconnected(run, m.name))
    }

    private fun loggedIn(player: PlayerRef) {
        val run = Raids.of(player).runOf(player.username) ?: return
        val m = run.member(player.username) ?: return
        val inst = run.instance
        if (!m.inside || inst == null || player.coord !in inst) return
        m.online = true
        player[RaidRun.PLAYER_RUN] = run.id.value
        // A member who reconnects after the room moved on joins the party where it is now.
        val room = (run.state as? RunState.InRoom)?.room
        if (room != null && !m.alive) player.teleport(run.at(room.waitAt ?: room.entry))
        if (run.state == RunState.Completed) Raids.pay(player, run)
        run.markDirty()
        player.events.publish(RaidEvent.Reconnected(run, m.name))
    }
}
