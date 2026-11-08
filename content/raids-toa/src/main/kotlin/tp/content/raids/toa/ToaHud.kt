package tp.content.raids.toa

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.AttrKey
import tp.plugin.InstanceRef
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits

/**
 * The in-raid HUD 481 (toa/client-contract.md 2.1): open for the whole run (RuneLite's in-raid test is 481:40), the
 * names through script 6585 before the orbs, the orbs `TOA_CLIENT_P0..P7` (0 vacant, 30 dead, 1 + round(26 x HP
 * fraction) alive), the own slot `TOA_CLIENT_PARTYSLOT` (1-based), the raid level 14380, the path levels 14376-14379,
 * the current path 14381 (0 Nexus, 1-4 a path, 5 the Wardens) and the raid timer by script 6580 (ticks, frozen). Only
 * changed varbits are sent; the client redraws on varps 3588-3592. Driven by a world tick ([tick]), like ToB's HUD.
 */
internal object ToaHud {
    fun sync(
        @Suppress("UNUSED_PARAMETER") run: RaidRun,
    ) {
        // Intentionally empty: the run alone reaches no player; [tick] does the work.
    }

    fun tick(world: WorldRef) {
        val scratch = Scratch.of(world)
        for (run in Raids.of(world).activeRuns) {
            if (run.raid.id != ToaRaid.ID) continue
            val inst = run.instance ?: continue
            update(world, run, inst, scratch)
        }
    }

    private fun update(world: WorldRef, run: RaidRun, inst: InstanceRef, scratch: Scratch) {
        val names = run.party.members
        val st = ToaFlow.state(run)
        for (k in 0 until SLOTS) {
            val name = names.getOrNull(k)
            val online = if (name == null) null else world.onlinePlayer(name)
            scratch.players[k] = online?.takeIf { run.members[name]?.inside == true && it.coord in inst }
            scratch.orbs[k] = orb(run, name, scratch.players[k])
        }
        val signature = run.id.value.toInt() * SIGNATURE_PRIME + names.hashCode()
        val path = hudPath(run, st)
        val frozen = (st?.endTick ?: -1L) >= 0
        val timer = timerTicks(st, world.tick)
        for (k in 0 until SLOTS) {
            val p = scratch.players[k] ?: continue
            show(p, names, k + 1, scratch.orbs, signature)
            setIfChanged(p, Varbits.TOA_CLIENT_RAID_LEVEL, st?.raidLevel ?: 0)
            for (pa in ToaPath.entries) setIfChanged(p, pa.levelVarbit, st?.pathLevels?.get(pa.ordinal) ?: 0)
            setIfChanged(p, Varbits.TOA_CLIENT_CURRENT_PATH, path)
            sendTimer(p, timer, frozen)
        }
    }

    /** 0 in the Nexus (all four levels), 1-4 on a path, 5 for the Wardens (client-contract 2.1; 5 vs 6 is OPEN-TC3). */
    private fun hudPath(run: RaidRun, st: ToaState?): Int {
        val room = (run.state as? RunState.InRoom)?.room?.id
        return when {
            room == ToaRaid.WARDENS || room == ToaRaid.TOMB -> HUD_WARDENS
            else -> st?.path?.hudPath ?: 0
        }
    }

    /** TA12: the timer runs from the first path; -1 (cleared) before it. */
    private fun timerTicks(st: ToaState?, now: Long): Int {
        val start = st?.timerStart ?: -1L
        if (start < 0) return -1
        val end = st?.endTick?.takeIf { it >= 0 } ?: now
        return (end - start).toInt()
    }

    private fun sendTimer(p: PlayerRef, ticks: Int, frozen: Boolean) {
        // The client counts by itself (6581); resend only when the timer starts, stops or the HUD was reopened.
        val key =
            if (ticks < 0) {
                TIMER_CLEARED
            } else if (frozen) {
                TIMER_FROZEN
            } else {
                TIMER_RUNNING
            }
        if (p[TIMER_STATE] == key) return
        p[TIMER_STATE] = key
        p.clientScript(SCRIPT_TIMER, ticks, if (frozen) 1 else 0)
    }

    private fun show(p: PlayerRef, names: List<String>, slot: Int, orbs: IntArray, signature: Int) {
        if (!p.isOpen(Interfaces.TOA_HUD)) {
            p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.TOA_HUD)
            p[HUD_SENT] = null
            p[TIMER_STATE] = null
        }
        // Names before the orbs that reveal the slots (client-contract 2.1 send order).
        if (p[HUD_SENT] != signature) {
            p[HUD_SENT] = signature
            p.clientScript(SCRIPT_NAMES, *Array(SLOTS) { names.getOrElse(it) { "" } })
        }
        setIfChanged(p, Varbits.TOA_CLIENT_PARTYSLOT, slot)
        for (k in 0 until SLOTS) setIfChanged(p, ORB_VARBITS[k], orbs[k])
    }

    /** Takes the HUD off [p] (left the raid): orbs, slot and levels cleared, the overlay closed. */
    fun leave(p: PlayerRef) {
        p[HUD_SENT] = null
        p[TIMER_STATE] = null
        for (v in ORB_VARBITS) setIfChanged(p, v, 0)
        setIfChanged(p, Varbits.TOA_CLIENT_PARTYSLOT, 0)
        setIfChanged(p, Varbits.TOA_CLIENT_CURRENT_PATH, 0)
        if (p.isOpen(Interfaces.TOA_HUD)) p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
    }

    private fun orb(run: RaidRun, name: String?, player: PlayerRef?): Int {
        name ?: return 0
        val member = run.members[name] ?: return 0
        if (!member.alive || player == null) return ORB_DEAD
        val max = maxOf(1, player.maxHitpoints)
        return 1 + (ORB_HEIGHT * player.hitpoints.coerceIn(0, max) + max / 2) / max
    }

    private fun setIfChanged(p: PlayerRef, varbit: Int, value: Int) {
        if (p.varbit(varbit) != value) p.setVarbit(varbit, value)
    }

    /** World-scoped scratch buffers, so the tick allocates nothing per run. */
    private class Scratch {
        val players = arrayOfNulls<PlayerRef>(SLOTS)
        val orbs = IntArray(SLOTS)

        companion object {
            private val KEY = AttrKey<Scratch>("toa_hud_scratch")

            fun of(world: WorldRef): Scratch = world.worldAttr(KEY) ?: Scratch().also { world.setWorldAttr(KEY, it) }
        }
    }

    private val HUD_SENT = AttrKey<Int>("toa_hud_names")
    private val TIMER_STATE = AttrKey<Int>("toa_hud_timer")

    private const val SLOTS = 8

    /** Script 6585 (8 strings): the names into varcs 1099-1106; 6580 (ticks, frozen): the raid timer. */
    private const val SCRIPT_NAMES = 6585
    private const val SCRIPT_TIMER = 6580

    /** Orb values (client-contract 2.1): 30 dead, 26 px filler height. */
    private const val ORB_DEAD = 30
    private const val ORB_HEIGHT = 26
    private const val HUD_WARDENS = 5
    private const val SIGNATURE_PRIME = 31
    private const val TIMER_CLEARED = 0
    private const val TIMER_RUNNING = 1
    private const val TIMER_FROZEN = 2

    private val ORB_VARBITS =
        intArrayOf(
            Varbits.TOA_CLIENT_P0,
            Varbits.TOA_CLIENT_P1,
            Varbits.TOA_CLIENT_P2,
            Varbits.TOA_CLIENT_P3,
            Varbits.TOA_CLIENT_P4,
            Varbits.TOA_CLIENT_P5,
            Varbits.TOA_CLIENT_P6,
            Varbits.TOA_CLIENT_P7,
        )
}
