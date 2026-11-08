package tp.content.raids.tob

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.AttrKey
import tp.plugin.InstanceRef
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits

/**
 * The in-raid HUD 28 (client-contract.md 2.1): party orbs `TOB_CLIENT_P0..P4` (0 vacant, 30 dead, 1 + 26 x HP fraction
 * alive), the own slot `TOB_CLIENT_PARTYSLOT` (1-based, 0 for a spectator), `TOB_CLIENT_PARTYSTATUS` 2 (the only value
 * that draws the raid HUD, and only at x >= 6400) and the five names through client script 2301. The client redraws on
 * varps 1745 and 1737, so only changed varbits are sent; the names are re-sent only when they change or the HUD opens.
 *
 * [RaidDriver][tp.content.raids.RaidDriver] hands over the run but no world, and players can only be found through a
 * world, so the work runs every tick from [tick] (a world tick of [TobPartyUi]) and [sync] is the driver's hook.
 */
internal object TobHud {
    /**
     * The raid driver hook (brief 3.15). A run alone reaches no player, so the HUD is rebuilt by [tick]; its per-player
     * dirty tracking (varbit compares and the names signature) makes the driver's "changed" flag redundant.
     */
    fun sync(
        @Suppress("UNUSED_PARAMETER") run: RaidRun,
    ) {
        // Intentionally empty: see the class KDoc.
    }

    /** Whether the raid HUD (not the lobby text) is on [p]'s screen. */
    fun active(p: PlayerRef): Boolean = p[RAID_HUD] == true

    /** Called every world tick: each live ToB run's present members and spectators. */
    fun tick(world: WorldRef) {
        val scratch = Scratch.of(world)
        for (run in Raids.of(world).activeRuns) {
            if (run.raid.id != TobRaid.ID) continue
            val inst = run.instance ?: continue
            update(world, run, inst, scratch)
        }
    }

    private fun update(world: WorldRef, run: RaidRun, inst: InstanceRef, scratch: Scratch) {
        val names = run.party.members
        val players = scratch.players
        val orbs = scratch.orbs
        for (k in 0 until SLOTS) {
            val name = names.getOrNull(k)
            val online = if (name == null) null else world.onlinePlayer(name)
            players[k] = online?.takeIf { run.members[name]?.inside == true && it.coord in inst }
            orbs[k] = orb(run, name, players[k])
        }
        val signature = run.id.value.toInt() * SIGNATURE_PRIME + names.hashCode()
        for (k in 0 until SLOTS) players[k]?.let { show(it, names, k + 1, orbs, signature) }
        if (run.spectators.isNotEmpty()) {
            for (name in run.spectators) {
                val p = world.onlinePlayer(name)?.takeIf { it.coord in inst } ?: continue
                show(p, names, NO_SLOT, orbs, signature)
            }
        }
    }

    private fun show(p: PlayerRef, names: List<String>, slot: Int, orbs: IntArray, signature: Int) {
        if (!p.isOpen(Interfaces.TOB_HUD)) {
            p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.TOB_HUD)
            p[HUD_SENT] = null
            // OPEN-TB-UI8: no source says which room uses the wave bar; type 0 hides it (client-contract 2.1, 8.3).
            setIfChanged(p, Varbits.TOB_CLIENT_WAVEPROGRESS_TYPE, 0)
        }
        p[RAID_HUD] = true
        setIfChanged(p, Varbits.TOB_CLIENT_PARTYSTATUS, STATUS_IN_RAID)
        setIfChanged(p, Varbits.TOB_CLIENT_PARTYSLOT, slot)
        for (k in 0 until SLOTS) setIfChanged(p, ORB_VARBITS[k], orbs[k])
        if (p[HUD_SENT] != signature) {
            p[HUD_SENT] = signature
            p.clientScript(SCRIPT_NAMES, *Array(SLOTS) { names.getOrElse(it) { "" } })
        }
    }

    /**
     * Takes the raid HUD off [p] (left the instance, logged out): orbs and slot cleared; the overlay is closed unless the
     * player stands in the lobby, where it stays for the lobby text.
     */
    fun leave(p: PlayerRef) {
        p[RAID_HUD] = null
        p[HUD_SENT] = null
        for (v in ORB_VARBITS) setIfChanged(p, v, 0)
        setIfChanged(p, Varbits.TOB_CLIENT_PARTYSLOT, 0)
        if (TobBoard.inLobby(p.coord)) {
            TobBoard.syncLobby(p)
        } else {
            if (p.isOpen(Interfaces.TOB_HUD)) p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
            setIfChanged(p, Varbits.TOB_CLIENT_PARTYSTATUS, 0)
        }
    }

    fun logout(p: PlayerRef) {
        if (active(p)) leave(p)
    }

    /** Orb of slot [name]: 0 vacant; 30 for a member who is dead, offline, not inside or gone (OPEN-TB-UI7); else 1 + HP fraction. */
    private fun orb(run: RaidRun, name: String?, player: PlayerRef?): Int {
        name ?: return 0
        val member = run.members[name] ?: return 0
        if (!member.alive || player == null) return ORB_DEAD
        val max = maxOf(1, player.maxHitpoints)
        // Overheal (brews) can exceed max: the orb shows full.
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
            private val KEY = AttrKey<Scratch>("tob_hud_scratch")

            fun of(world: WorldRef): Scratch = world.worldAttr(KEY) ?: Scratch().also { world.setWorldAttr(KEY, it) }
        }
    }

    /** Player keys: the raid HUD is up, and the names signature last sent. */
    private val RAID_HUD = AttrKey<Boolean>("tob_raid_hud")
    private val HUD_SENT = AttrKey<Int>("tob_hud_sent")

    /** Orb slots of the HUD (P0..P4; TB1: 1-5 players). */
    private const val SLOTS = TobRaid.MAX_PARTY
    private const val NO_SLOT = 0
    private const val SIGNATURE_PRIME = 31

    /** client-contract 2.2: `PARTYSTATUS` 2 draws the raid HUD (inside the instance, x >= 6400). */
    private const val STATUS_IN_RAID = 2

    /** client-contract 2.1: P == 30 draws the slot red with no filler (dead). */
    private const val ORB_DEAD = 30

    /** client-contract 2.1: filler height = min(26, P - 1) px. */
    private const val ORB_HEIGHT = 26

    /** Server-run client script 2301 (`sssss`): the five party names (varcs 330-334); also forces a timer redraw. */
    private const val SCRIPT_NAMES = 2301

    private val ORB_VARBITS =
        intArrayOf(
            Varbits.TOB_CLIENT_P0,
            Varbits.TOB_CLIENT_P1,
            Varbits.TOB_CLIENT_P2,
            Varbits.TOB_CLIENT_P3,
            Varbits.TOB_CLIENT_P4,
        )
}
