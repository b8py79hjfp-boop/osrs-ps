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
        events.on<SpecialUsed> { u -> stat(u.player, ToaStat.SPECIAL_ENERGY, u.tenths / TENTHS_PER_PERCENT) }
        events.on<Consumed> { c -> if (!c.drink) stat(c.player, ToaStat.FOOD_EATEN, 1) }
        events.on<DeathEvent> { e ->
            val npc = e.entity as? NpcRef ?: return@on
            val killer = e.killer as? PlayerRef ?: return@on
            if (npc.type in SCARABS) stat(killer, ToaStat.SCARABS_KILLED, 1)
        }
    }

    private fun stat(p: PlayerRef, stat: ToaStat, amount: Int) {
        val run = ToaFlow.runOf(p) ?: return
        ToaPoints.stat(run, p.username, stat, amount)
    }

    private fun signal(s: RaidSignal) {
        val runId = s.npc[tp.plugin.SharedAttrs.RAID_RUN] ?: return
        val run = tp.content.raids.Raids.of(s.npc).run(runId)?.takeIf { it.raid.id == ToaRaid.ID } ?: return
        when (s.key) {
            "toa.wardens.down" -> ToaPoints.wardenP2Down(run)
            "toa.wardens.finished" -> s.player?.let { ToaPoints.stat(run, it.username, ToaStat.WARDENS_FINISHED, 1) }
            "toa.akkha.orb" -> s.player?.let { ToaPoints.stat(run, it.username, ToaStat.ORBS_DROPPED, s.amount) }
            "toa.zebak.water_tick" -> s.player?.let { ToaPoints.stat(run, it.username, ToaStat.POOL_TICKS, s.amount) }
        }
    }

    private companion object {
        /** Unleash spends all energy by its own rule (SP6). */
        const val UNLEASH = "Unleash"
        const val TENTHS_PER_PERCENT = 10

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
