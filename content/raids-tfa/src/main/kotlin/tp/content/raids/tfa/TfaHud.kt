package tp.content.raids.tfa

import tp.content.raids.RaidRun
import tp.content.raids.RunState
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits

/**
 * The in-raid HUD 974 (tfa/client-contract.md section 2.1): party orbs `TFA_CLIENT_P0..P7` (0 empty, 30 dead,
 * 1 + 26 x HP fraction alive), the own slot `TFA_CLIENT_PARTYSLOT` (1-based), names through client script 10042 and the
 * raid timer through 10036. The client redraws on varps 5910-5912, so only changed varbits are sent.
 */
internal object TfaHud {
    /** Called every world tick for a live TFA run. */
    fun tick(world: WorldRef, run: RaidRun, state: TfaState) {
        val names = run.party.members
        val inst = run.instance ?: return
        // Tick hot loop (raids brief section 7): no lists; the orbs go in the run state's array.
        val orbs = state.orbs
        for (k in 0 until SLOTS) orbs[k] = orb(world, run, names.getOrNull(k), inst)
        for (n in names) {
            val p = present(world, run, n, inst) ?: continue
            show(world, run, state, p)
            setIfChanged(p, Varbits.TFA_CLIENT_PARTYSLOT, names.indexOfFirst { it.equals(p.username, ignoreCase = true) } + 1)
        }
        // TFA17: spectators see the same orbs, names and timer (their own slot is none).
        for (name in run.spectators) {
            val p = world.onlinePlayer(name)?.takeIf { it.coord in inst } ?: continue
            show(world, run, state, p)
        }
        state.hudDirty = false
    }

    /** The HUD, this tick's orbs and (first time or when dirty) the names and timer, for a member or a spectator. */
    private fun show(world: WorldRef, run: RaidRun, state: TfaState, p: PlayerRef) {
        if (!p.isOpen(Interfaces.TFA_HUD)) {
            p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.TFA_HUD)
            state.hudSentTo -= p.username
        }
        for (k in 0 until SLOTS) setIfChanged(p, ORB_VARBITS[k], state.orbs[k])
        if (state.hudSentTo.add(p.username) || state.hudDirty) {
            val names = run.party.members
            p.clientScript(SCRIPT_NAMES, *Array(SLOTS) { names.getOrElse(it) { "" } })
            val ended = run.state == RunState.Completed
            val elapsed = (if (ended) run.endedTick else world.tick) - run.startedTick
            p.clientScript(SCRIPT_TIMER, elapsed.toInt(), if (ended) 1 else 0)
        }
    }

    /** Closes the HUD and clears the orbs for a player leaving the raid. */
    fun close(p: PlayerRef) {
        p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
        for (v in ORB_VARBITS) setIfChanged(p, v, 0)
        setIfChanged(p, Varbits.TFA_CLIENT_PARTYSLOT, 0)
    }

    /** [name] online, inside the run and in its instance, or null. */
    private fun present(world: WorldRef, run: RaidRun, name: String, inst: tp.plugin.InstanceRef): PlayerRef? =
        world.onlinePlayer(name)?.takeIf { p -> run.member(name)?.inside == true && p.coord in inst }

    private fun orb(world: WorldRef, run: RaidRun, name: String?, inst: tp.plugin.InstanceRef): Int {
        name ?: return 0
        val m = run.member(name) ?: return 0
        if (!m.inside) return 0
        if (!m.alive) return ORB_DEAD
        val p = present(world, run, name, inst) ?: return ORB_DEAD
        val max = maxOf(1, p.maxHitpoints)
        // Overheal (brews) can exceed max: the orb shows full.
        return 1 + (ORB_HEIGHT * p.hitpoints.coerceIn(0, max) + max / 2) / max
    }

    private fun setIfChanged(p: PlayerRef, varbit: Int, value: Int) {
        if (p.varbit(varbit) != value) p.setVarbit(varbit, value)
    }

    private const val SLOTS = 8

    /** client-contract 2.1: P == 30 draws the slot red with no filler (dead). */
    private const val ORB_DEAD = 30

    /** client-contract 2.1: filler height = min(26, P - 1) px. */
    private const val ORB_HEIGHT = 26

    /** Server-run client script 10042 (`ssssssss`): the eight party names. */
    private const val SCRIPT_NAMES = 10042

    /** Server-run client script 10036 (`ii`): (elapsed ticks, frozen). */
    private const val SCRIPT_TIMER = 10036

    private val ORB_VARBITS =
        intArrayOf(
            Varbits.TFA_CLIENT_P0,
            Varbits.TFA_CLIENT_P1,
            Varbits.TFA_CLIENT_P2,
            Varbits.TFA_CLIENT_P3,
            Varbits.TFA_CLIENT_P4,
            Varbits.TFA_CLIENT_P5,
            Varbits.TFA_CLIENT_P6,
            Varbits.TFA_CLIENT_P7,
        )
}

/** Per-run TFA state ([RaidRun.content]). */
internal class TfaState {
    /** Nexus level copy in use (OPEN-NX4: 0 start, 2 Guardian dead, 3 Ocellus passage open). */
    var nexusLevel: Int = 0
    var hudDirty: Boolean = true

    /** The HUD's orb values of this tick (reused: no array per tick). */
    val orbs: IntArray = IntArray(PARTY_SLOTS)
    val hudSentTo: MutableSet<String> = HashSet()

    /** Members whose own-chest slot (`TFA_VAULT_OWN_CHEST`) was set in this run's vault (the varbit outlives runs). */
    val ownChestSet: MutableSet<String> = HashSet()

    /** Tick the vault chests appear (braziers lit), -1 = not due. */
    var chestsAt: Long = -1
    var chestsSpawned: Boolean = false

    /** Supply credits per member (lower-case name), granted when the third room is cleared (OPEN-TFA7). */
    val supplyCredits: MutableMap<String, Int> = HashMap()

    /** Members offered the failure shop (row 17151, with the Leftovers). */
    val poorPerformers: MutableSet<String> = HashSet()

    /** Alliance Supplies left: owner ("" = the whole run, or a lower-case name) to stock dbrow to count. */
    val supplyStock: MutableMap<String, MutableMap<Int, Int>> = HashMap()

    /** Members whose raid attempt is already in their stats (a member enters a run once). */
    val statsAttempted: MutableSet<String> = HashSet()

    /** The Archive Guardian's death already dropped the archive keys (TfaRecordsRoom.guardianDied runs once per run). */
    var keyHandled: Boolean = false
}

/** HUD 974's eight party slots. */
private const val PARTY_SLOTS = 8
