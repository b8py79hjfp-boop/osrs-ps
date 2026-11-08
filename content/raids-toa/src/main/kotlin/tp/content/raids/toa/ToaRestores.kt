package tp.content.raids.toa

import tp.content.raids.RaidEvent
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.ContentScope
import tp.plugin.CurePoisonRequest
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.WorldRef
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varps

/**
 * The Nexus and boss restores (toa.md TA21) and the honey locusts of a wipe (TA42, TA55): a cleared challenge room removes
 * poison and venom; entering a boss chamber restores run energy; clearing a boss or wiping on it restores Hitpoints,
 * Prayer, run energy and special attack, removes poison and venom and restores every drained stat. A wipe also gives each
 * player honey locusts unless On a Diet is active.
 */
internal object ToaRestores {
    /** TA21: run energy "restored to full" (the run bar is 10,000 units). */
    private const val FULL_RUN_ENERGY = 10_000

    /** TA21: special attack "fully restored" (varp `sa_energy`, tenths of a percent, content/combat `Specials`). */
    private const val FULL_SPECIAL = 1_000

    /**
     * A wipe is handled one tick after `RoomFailed`: the event fires inside the death flow, before combat respawns the
     * players, so a restore made at once could be overwritten by the respawn.
     */
    private const val WIPE_DELAY = 1L

    private val BOSS_ROOMS: Set<String> = ToaPath.entries.map { it.boss }.toSet()
    private val PUZZLE_ROOMS: Set<String> = ToaPath.entries.map { it.puzzle }.toSet()

    /** A wipe waiting for its tick. */
    private class Wipe(val runId: Long, val at: Long, val room: String)

    /** The run's members that are online and inside (the event handlers have no entity to ask). */
    fun present(world: WorldRef, run: RaidRun): List<PlayerRef> =
        run.members.values
            .filter { it.inside && it.online }
            .mapNotNull { world.onlinePlayer(it.name) }

    /** Removes poison and venom (one venom-curing request), then gives [immunityTicks] of immunity. */
    fun curePoison(p: PlayerRef, immunityTicks: Int) {
        p.events.publish(CurePoisonRequest(p, immunityTicks, curesVenom = true))
    }

    fun register(scope: ContentScope, world: () -> WorldRef?) {
        val wipes = ArrayList<Wipe>()
        with(scope) {
            events.on<RaidEvent.RoomStarted> { e ->
                val w = world()
                // TA21: on entering a boss chamber run energy is restored to full.
                if (e.run.raid.id == ToaRaid.ID && w != null && e.room.id in BOSS_ROOMS) {
                    for (p in present(w, e.run)) p.runEnergy = FULL_RUN_ENERGY
                }
            }
            events.on<RaidEvent.RoomCleared> { e ->
                val w = world()
                if (e.run.raid.id != ToaRaid.ID || w == null) return@on
                // TA21: after each challenge room poison and venom are removed; after a boss everything is restored.
                if (e.room.id in PUZZLE_ROOMS) for (p in present(w, e.run)) curePoison(p, 0)
                if (e.room.id in BOSS_ROOMS) for (p in present(w, e.run)) restoreBoss(p)
            }
            events.on<RaidEvent.RoomFailed> { e ->
                val w = world()
                if (e.run.raid.id == ToaRaid.ID && w != null) wipes += Wipe(e.run.id.value, w.tick + WIPE_DELAY, e.room.id)
            }
            events.on<RaidEvent.Left> { e ->
                if (e.run.raid.id == ToaRaid.ID) world()?.onlinePlayer(e.member)?.let(ToaConsumables::clear)
            }
            onWorldTick {
                if (wipes.isNotEmpty()) settle(this, wipes)
            }
        }
    }

    /** Applies the wipes whose tick has come (TA21, TA42). */
    private fun settle(world: WorldRef, wipes: MutableList<Wipe>) {
        var i = 0
        while (i < wipes.size) {
            val wipe = wipes[i]
            if (wipe.at > world.tick) {
                i++
                continue
            }
            wipes.removeAt(i)
            val run = Raids.of(world).run(wipe.runId) ?: continue
            // The attempts ran out: the team was ejected with its items, there is nothing to restore or give.
            if (run.state is RunState.Failed || run.state == RunState.Closed) continue
            val st = ToaFlow.state(run) ?: continue
            val players = present(world, run)
            // TA21: "this will also apply in the case of wipes".
            if (wipe.room in BOSS_ROOMS) for (p in players) restoreBoss(p)
            // TA42: honey locusts to every player unless On a Diet.
            val locusts = honeyLocusts(wipe.room)
            if (locusts > 0 && ToaConsumables.ON_A_DIET !in st.modifierNames) {
                for (p in players) p.transaction("toa honey locusts") { addOrDrop(Invs.INV, Objs.TOA_HONEY_LOCUST, locusts) }
            }
        }
    }

    /** OPEN-TA26: honey locusts per wipe by room kind (puzzle, boss, Wardens: "more after the Wardens"). */
    private fun honeyLocusts(room: String): Int =
        when (room) {
            in PUZZLE_ROOMS -> ToaConfig.honeyLocustsPuzzle
            in BOSS_ROOMS -> ToaConfig.honeyLocustsBoss
            ToaRaid.WARDENS -> ToaConfig.honeyLocustsWardens
            else -> 0
        }

    /**
     * TA21: Hitpoints, Prayer, run energy and special attack fully restored, poison and venom removed, every drained
     * stat back to its base level. Boosts above the base are left alone.
     */
    private fun restoreBoss(p: PlayerRef) {
        p.hitpoints = maxOf(p.hitpoints, p.maxHitpoints)
        for (stat in 0 until Stats.COUNT) {
            val base = p.baseLevel(stat)
            if (p.level(stat) < base) p.setLevel(stat, base)
        }
        p.runEnergy = FULL_RUN_ENERGY
        p.setVarp(Varps.SA_ENERGY, FULL_SPECIAL)
        curePoison(p, 0)
    }
}
