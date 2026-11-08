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
            // Some raid NPCs earn nothing for a while (CoX: a recovered Olm hand).
            if (npc[tp.plugin.SharedAttrs.RAID_NO_POINTS] != true) Raids.damage(run, player.username, npc.type, e.damage)
        }
        events.on<DeathPolicyRequest> { req ->
            val run = runInside(req.player) ?: return@on
            Raids.died(req.player, run, req)
        }
        // Dev / load command (raids brief section 7, botswarm `raids` profile): `::raid <tob|cox|toa|tfa>` starts a solo run
        // of that raid, `::raid next` moves the run to its next room in order, `::raid leave` leaves it.
        onCommand("raid", tp.util.Rank.DEVELOPER) { args -> devRaid(this, args) }
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
        // Tick hot loop (raids brief section 7): no copy of the runs; the few that close are collected and closed after.
        var closing: ArrayList<RaidRun>? = null
        for (run in Raids.of(world).activeRuns) {
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
            if (world.tick - run.emptySinceTick >= Raids.EMPTY_GRACE_TICKS || (ended && nobodyInside)) {
                (closing ?: ArrayList<RaidRun>().also { closing = it }) += run
            }
        }
        closing?.forEach { Raids.close(world, it) }
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
        m.roomsAtLogout = run.roomsTaken.size
        val encounter = (run.state as? RunState.InRoom)?.room?.kind == RoomKind.ENCOUNTER
        if (run.raid.disconnect == DisconnectPolicy.DEATH_IN_ENCOUNTER && encounter && !run.raid.safeLogout(player)) {
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
        // A member who reconnects after the party moved on (or who died) joins the party where it is now.
        val room = (run.state as? RunState.InRoom)?.room
        val movedOn = m.roomsAtLogout >= 0 && m.roomsAtLogout != run.roomsTaken.size
        if (room != null &&
            (!m.alive || movedOn)
        ) {
            player.teleport(if (!m.alive) Raids.waitOf(run, room, player) else Raids.entryOf(run, room, player))
        }
        m.roomsAtLogout = -1
        if (run.state == RunState.Completed) Raids.pay(player, run)
        run.markDirty()
        player.events.publish(RaidEvent.Reconnected(run, m.name))
    }
}

private fun devRaid(p: PlayerRef, args: List<String>) {
    val raids = Raids.of(p)
    val run = raids.runOf(p.username)
    when (val arg = args.firstOrNull()?.lowercase()) {
        null -> {
            p.message("::raid <${raids.allDefinitions().joinToString("|") { it.id }}> | next | leave")
        }

        "next" -> {
            val r = run ?: return p.message("You are not in a raid.")
            val rooms = r.raid.slots.map { it.first() }
            val now = (r.state as? RunState.InRoom)?.room?.id
            Raids.enterRoom(p, r, rooms[(rooms.indexOf(now) + 1).mod(rooms.size)])
        }

        "leave" -> {
            run?.let { Raids.leave(p, it) } ?: p.message("You are not in a raid.")
        }

        else -> {
            if (run != null) return p.message("You are already in a raid.")
            val def = raids.definition(arg) ?: return p.message("No raid '$arg'.")
            val party = raids.partyOf(p.username)?.takeIf { it.raid.id == def.id } ?: raids.createParty(def, p.username)
            if (party == null) return p.message("You are already in another party.")
            Raids.start(p, party)?.let { p.message(it) }
        }
    }
}
