package tp.content.raids.toa

import tp.plugin.ConsumeCheck
import tp.plugin.Consumed
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.RaidSignal
import tp.plugin.SpecialCostQuery
import tp.plugin.SpecialUsed

/**
 * ToA's rules on the combat module's consumables and specials (toa.md TA15): On a Diet / Dehydration refuse food and
 * health-restoring potions in the raid; Overly Draining makes every special cost 100% and liquid adrenaline halves it.
 * The rules themselves are [ToaConsumables]'s; outside a ToA run they change nothing.
 */
public class ToaHooks : ContentPlugin {
    override fun ContentScope.register() {
        events.on<ConsumeCheck> { c ->
            if (c.refusal == null) c.refusal = ToaConsumables.refusal(c.player, c.food, c.restoresHealth)
        }
        events.on<SpecialCostQuery> { q -> if (q.special != UNLEASH) q.tenths = ToaConsumables.specialCostTenths(q.player, q.tenths) }
        // The summary titles' tallies (toa.md TA67) and the Wardens' downs (WD26) from the fights.
        events.on<RaidSignal> { s -> signal(s) }
        onWorldTick { rooms(this) }
        events.on<SpecialUsed> { u -> stat(u.player, ToaStat.SPECIAL_ENERGY, u.tenths / TENTHS_PER_PERCENT) }
        events.on<Consumed> { c -> if (!c.drink) stat(c.player, ToaStat.FOOD_EATEN, 1) }
        events.on<DeathEvent> { e ->
            val npc = e.entity as? NpcRef ?: return@on
            val killer = e.killer as? PlayerRef ?: return@on
            if (npc.type in SCARABS) stat(killer, ToaStat.SCARABS_KILLED, 1)
        }
    }

    /**
     * Keeps the Crondis crocodiles' view of the players (SharedAttrs.RAID_CARRYING / RAID_TRAP_HITS, CR6 / CR7) and sends
     * Het's orbs of darkness from the room's edges (HE5, OPEN-PZ27: one every [ORB_EVERY] ticks).
     */
    private fun rooms(world: tp.plugin.WorldRef) {
        for (run in tp.content.raids.Raids
            .of(world)
            .activeRuns) {
            if (run.raid.id != ToaRaid.ID) continue
            ToaFlow.state(run)?.let { timeLimit(it, world.tick) }
            val room = (run.state as? tp.content.raids.RunState.InRoom)?.room?.id ?: continue
            // Tick hot loop: no lists per run per tick (CLAUDE.md conventions).
            val any = ToaPuzzles.anyPlayer(world, run) ?: continue
            when (room) {
                "crondis" -> {
                    for (m in run.members.values) {
                        if (!m.inside || !m.online) continue
                        val p = world.onlinePlayer(m.name) ?: continue
                        p[tp.plugin.SharedAttrs.RAID_CARRYING] = ToaCrondis.carrying(run, any, p.username)
                        p[tp.plugin.SharedAttrs.RAID_TRAP_HITS] = ToaCrondis.recentTrapHits(run, any, p.username)
                    }
                }

                "het" -> {
                    if (world.tick % ORB_EVERY == 0L && !ToaHet.sealWeakened(run, any)) {
                        val edge = HET_EDGES[(world.tick / ORB_EVERY % HET_EDGES.size).toInt()]
                        ToaRooms.spawn(any, run, "het", tp.symbols.Npcs.TOA_HET_ORB, edge)
                    }
                }
            }
        }
    }

    /** OPEN-TA6 (flag off): the tick the timer passes the limit, the raid level drops by twice the invocation's level. */
    private fun timeLimit(st: ToaState, now: Long) {
        if (ToaConfig.timeLimitPenaltyAtEndOnly || st.timePenalised) return
        val running = st.limitTicks >= 0 && st.timerStart >= 0 && st.endTick < 0
        if (!running || now - st.timerStart <= st.limitTicks) return
        st.timePenalised = true
        st.raidLevel = maxOf(0, st.raidLevel - ToaRecords.TIME_PENALTY_FACTOR * st.limitLevel)
    }

    private fun stat(p: PlayerRef, stat: ToaStat, amount: Int) {
        val run = ToaFlow.runOf(p) ?: return
        ToaPoints.stat(run, p.username, stat, amount)
    }

    private fun signal(s: RaidSignal) {
        val runId = s.npc[tp.plugin.SharedAttrs.RAID_RUN] ?: return
        val run =
            tp.content.raids.Raids
                .of(s.npc)
                .run(runId)
                ?.takeIf { it.raid.id == ToaRaid.ID } ?: return
        when (s.key) {
            "toa.wardens.down" -> ToaPoints.wardenP2Down(run)

            "toa.wardens.finished" -> s.player?.let { ToaPoints.stat(run, it.username, ToaStat.WARDENS_FINISHED, 1) }

            "toa.akkha.orb" -> s.player?.let { ToaPoints.stat(run, it.username, ToaStat.ORBS_DROPPED, s.amount) }

            "toa.zebak.water_tick" -> s.player?.let { ToaPoints.stat(run, it.username, ToaStat.POOL_TICKS, s.amount) }

            // The puzzle foes (content/combat ToaPuzzleFoes) report the room effects.
            "toa.apmeken.venom" -> ToaApmeken.addVenomPool(run, s.npc, s.npc.coord)

            "toa.crondis.palm_bite" -> ToaCrondis.palmDamaged(run, s.npc, s.amount)

            "toa.crondis.container_bite" -> s.player?.let { ToaCrondis.containerBitten(run, s.npc, it.username) }

            "toa.het.orb_hit" -> s.player?.let { ToaHet.orbHit(it) }

            "toa.het.dirty_mirror" -> ToaHet.dirtyMirrorAt(run, s.npc, s.npc.coord)
        }
    }

    private companion object {
        /** Unleash spends all energy by its own rule (SP6). */
        const val UNLEASH = "Unleash"
        const val TENTHS_PER_PERCENT = 10

        /** HE5 (OPEN-PZ27): an orb every 12 ticks from one of the four edge midpoints of Het's grid (3670-3690 x 5270-5290). */
        const val ORB_EVERY = 12L
        val HET_EDGES =
            listOf(
                tp.util.CoordGrid(3670, 5280),
                tp.util.CoordGrid(3690, 5280),
                tp.util.CoordGrid(3680, 5270),
                tp.util.CoordGrid(3680, 5290),
            )

        /** The Entomophobe's scarabs: the Scabaras puzzle's (11697) and Kephri's (11723-11727). */
        val SCARABS =
            setOf(
                tp.symbols.Npcs.TOA_SCABARAS_SCARAB,
                tp.symbols.Npcs.TOA_KEPHRI_SHIELD_SCARAB,
                tp.symbols.Npcs.TOA_KEPHRI_GUARDIAN_MELEE,
                tp.symbols.Npcs.TOA_KEPHRI_GUARDIAN_RANGED,
                tp.symbols.Npcs.TOA_KEPHRI_GUARDIAN_MAGE,
                tp.symbols.Npcs.TOA_KEPHRI_SCARAB_RANGEKITE,
            )
    }
}
