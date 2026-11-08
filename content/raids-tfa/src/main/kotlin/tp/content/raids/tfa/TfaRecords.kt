package tp.content.raids.tfa

import tp.content.raids.RaidEvent
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * The records room, the archive key and the Living record capes (docs/mechanics/raids/tfa.md TFA26,
 * tfa/lobby-nexus-vault.md "Records room").
 *
 * The strange door 63279 sits in the nexus wall at (4123, 5572) and shows 63280 (locked) or 63281 (records room) from
 * `TFA_RECORDS_ROOM_UNLOCKED`. The archive key used on it unlocks it for good. Behind it is the records room on levels 1 / 3
 * of the nexus square, which is not part of the run's instance (it copies level 0 only), so the room is entered at its real
 * map coordinates and left by the exit loc 63282. The five claim locs (multis 63287 / 63289 / 63291 / 63293 / 63295) show
 * their `*_VISIBLE` child once the player's completions reach the cape's param 2722.
 */
public class TfaRecords : ContentPlugin {
    override fun ContentScope.register() {
        var world: WorldRef? = null
        onWorldStart { world = this }

        // The click reports the multi's parent id (hunter.md / farming.md precedent); the visible children are registered too.
        for (door in intArrayOf(Locs.TFA_RECORDS_ROOM_DOOR, Locs.TFA_RECORDS_ROOM_DOOR_LOCKED)) {
            onUseOnLoc(Objs.TFA_RECORDS_ROOM_KEY, door) { TfaRecordsRoom.unlock(player) }
        }
        for (door in intArrayOf(Locs.TFA_RECORDS_ROOM_DOOR, Locs.TFA_RECORDS_ROOM_DOOR_UNLOCKED)) {
            onOpLoc(door, OP1) { TfaRecordsRoom.enter(player) }
        }
        onOpLoc(Locs.TFA_RECORDS_ROOM_EXIT, OP1) { TfaRecordsRoom.leave(player) }
        for ((tier, cape) in TfaRecordsRoom.CAPES.withIndex()) {
            for (claim in intArrayOf(cape.claim, cape.claimVisible)) {
                onOpLoc(claim, OP1) { TfaRecordsRoom.claim(player, tier) }
                onOpLoc(claim, OP2) { TfaRecordsRoom.inspect(player, tier) }
            }
        }

        onLogin { TfaRecordsRoom.refreshCapes(this) }
        // Raids.complete adds the completion to the KC varp before it publishes Completed.
        events.on<RaidEvent.Completed> { e ->
            val w = world
            if (w != null && e.run.raid.id == TfaRaid.ID) {
                for (m in e.run.members.values) {
                    if (m.inside) w.onlinePlayer(m.name)?.let(TfaRecordsRoom::refreshCapes)
                }
            }
        }

        // TFA26 / AG6: the Archive Guardian's death drops the archive key to every eligible player present.
        events.on<DeathEvent> { e ->
            val npc = e.entity as? NpcRef
            if (npc != null && npc.type in GUARDIANS) TfaRecordsRoom.guardianDied(npc)
        }
    }

    private companion object {
        /** The Archive Guardian's NPC types: shielded and grounded (tfa/archive-guardian.md AG0). */
        val GUARDIANS = intArrayOf(Npcs.TFA_GUARDIAN, Npcs.TFA_GUARDIAN_UNSHIELDED)
    }
}

private const val OP1 = 1
private const val OP2 = 2

/** One Living record cape: the cape obj, its claim multi (and visible child) and the varbit that draws the child. */
internal class TfaCape(val obj: Int, val claim: Int, val claimVisible: Int, val visibleVarbit: Int)

internal object TfaRecordsRoom {
    /**
     * Client-contract 1.6: obj param 2722 of the cape objs holds the completions required (100 / 500 / 1000 / 1500 / 2000,
     * the Wiki "Living record" page); client script 10173 reads it.
     */
    const val PARAM_COMPLETIONS_REQUIRED: Int = 2722

    /** The completions that make the Guardian drop the archive key (tfa.md TFA26; Wiki "Archive key"). */
    const val KEY_MIN_COMPLETIONS: Int = 100

    /** Wiki "Archive key": the message shown when the key drops (colour #3366ff). */
    const val KEY_DROP_MESSAGE: String = "<col=3366ff>A small object falls out of the defeated Archive Guardian.</col>"

    /** Wiki "Strange door": the refusal while the Archive Guardian is active. */
    const val SEALED: String = "The door has sealed itself while the Archive Guardian is active."

    /** OPEN-RC2: same placeholder text as raids-core's teleport block. */
    const val NO_TELEPORT: String = "A magical force prevents you from teleporting."

    /**
     * OPEN-TFA28: texts that no source gives (unlock, ownership and shortfall lines); the shortfall follows the tail of
     * client script 10173 quoted in client-contract 1.6.
     */
    private const val UNLOCKED_TEXT = "You unlock the door with the archive key."
    private const val ALREADY_UNLOCKED_TEXT = "The door is already unlocked."
    private const val NOT_YET_TEXT = "You have not unlocked the records room."
    private const val OWNED_TEXT = "You already have that Living record."
    private const val NO_SPACE_TEXT = "You don't have enough inventory space."

    /** Template tile in front of the strange door (4123, 5572) inside the nexus (free tile, collision at level 0). */
    val DOOR_FRONT = CoordGrid(4125, 5572)

    /** The records room entrance on level 1 by its exit loc 63282 (4109, 5573); in the run instance (OPEN-TFA31). */
    val ARRIVAL = CoordGrid(4107, 5574, 1)

    /** The records room spans x 4096..4119, z 5568..5579 on levels 1 and 3 of the nexus square (cache map 64,87). */
    private val ROOM_X = 4096..4119
    private val ROOM_Z = 5568..5579

    /** Levels of the room's two copies (cache: exit and claims exist on levels 1 and 3). */
    private const val LEVEL_A = 1
    private const val LEVEL_B = 3

    /** TFA26: tier 1..5 capes, claim multis and visible varbits (client-contract 1.2). */
    val CAPES: List<TfaCape> =
        listOf(
            TfaCape(Objs.TFA_CAPE_T1, Locs.TFA_CAPE_CLAIM_T1, Locs.TFA_CAPE_CLAIM_T1_VISIBLE, Varbits.TFA_CAPE_CLAIM_T1_VISIBLE),
            TfaCape(Objs.TFA_CAPE_T2, Locs.TFA_CAPE_CLAIM_T2, Locs.TFA_CAPE_CLAIM_T2_VISIBLE, Varbits.TFA_CAPE_CLAIM_T2_VISIBLE),
            TfaCape(Objs.TFA_CAPE_T3, Locs.TFA_CAPE_CLAIM_T3, Locs.TFA_CAPE_CLAIM_T3_VISIBLE, Varbits.TFA_CAPE_CLAIM_T3_VISIBLE),
            TfaCape(Objs.TFA_CAPE_T4, Locs.TFA_CAPE_CLAIM_T4, Locs.TFA_CAPE_CLAIM_T4_VISIBLE, Varbits.TFA_CAPE_CLAIM_T4_VISIBLE),
            TfaCape(Objs.TFA_CAPE_T5, Locs.TFA_CAPE_CLAIM_T5, Locs.TFA_CAPE_CLAIM_T5_VISIBLE, Varbits.TFA_CAPE_CLAIM_T5_VISIBLE),
        )

    fun inRoom(at: CoordGrid): Boolean = (at.level == LEVEL_A || at.level == LEVEL_B) && at.x in ROOM_X && at.z in ROOM_Z

    /**
     * OPEN-TFA31: the room lives on level 1 of the nexus square, whose other level-1 parts belong to later nexus
     * states; only the four zones holding the room (x 4096-4111, z 5568-5583, solid rock below on level 0) are copied
     * into the run's instance, at the run's start.
     */
    fun copyInto(run: RaidRun) {
        val inst = run.instance ?: return
        for (x in ZONE_XS) for (z in ZONE_ZS) inst.copyZone(CoordGrid(x, z, LEVEL_A), upper(run, CoordGrid(x, z)))
    }

    /** [t] on the instance's level 1: the nexus square is mapped from its level 0 only (TfaRaid squareLevels). */
    private fun upper(run: RaidRun, t: CoordGrid): CoordGrid {
        val at = run.at(CoordGrid(t.x, t.z, 0))
        return CoordGrid(at.x, at.z, LEVEL_A)
    }

    private val ZONE_XS = intArrayOf(4096, 4104)
    private val ZONE_ZS = intArrayOf(5568, 5576)

    // ---- the door

    /** The archive key used on the locked door: it is consumed and the door stays unlocked for good (varbit). */
    fun unlock(player: PlayerRef) {
        if (player.varbit(Varbits.TFA_RECORDS_ROOM_UNLOCKED) == 1) {
            player.message(ALREADY_UNLOCKED_TEXT)
            return
        }
        val ok = player.transaction("tfa records room unlock") { remove(Invs.INV, Objs.TFA_RECORDS_ROOM_KEY, 1) }
        if (ok) {
            player.setVarbit(Varbits.TFA_RECORDS_ROOM_UNLOCKED, 1)
            player.message(UNLOCKED_TEXT)
        }
    }

    /** "Enter" on the unlocked door: only inside a run, not while the Guardian is active. */
    fun enter(player: PlayerRef) {
        val run = runOf(player) ?: return
        when {
            player.varbit(Varbits.TFA_RECORDS_ROOM_UNLOCKED) != 1 -> player.message(NOT_YET_TEXT)
            guardianActive(player, run) -> player.message(SEALED)
            else -> player.teleport(upper(run, ARRIVAL))
        }
    }

    /** The exit loc: back to the nexus in front of the door, or to the lobby when the run is gone. */
    fun leave(player: PlayerRef) {
        val run = Raids.of(player).runOf(player.username)?.takeIf { it.raid.id == TfaRaid.ID && it.member(player.username)?.inside == true }
        if (run?.instance != null) {
            player.teleport(run.at(DOOR_FRONT))
        } else {
            player.teleport(TfaRaid.LOBBY_DOOR)
        }
    }

    private fun runOf(player: PlayerRef): RaidRun? {
        val run = Raids.of(player).runOf(player.username) ?: return null
        return run.takeIf { it.raid.id == TfaRaid.ID && it.member(player.username)?.inside == true && it.instance != null }
    }

    /** The door is sealed while the Archive Guardian is awake and alive in this run's nexus. */
    private fun guardianActive(player: PlayerRef, run: RaidRun): Boolean {
        val room = (run.state as? RunState.InRoom)?.room?.id
        val inst = run.instance
        return room == GUARDIAN_ROOM && inst != null &&
            player.npcsWithin(player.coord, TfaBosses.ROOM_SCAN).any {
                (it.type == Npcs.TFA_GUARDIAN || it.type == Npcs.TFA_GUARDIAN_UNSHIELDED) && it.hitpoints > 0 && it.coord in inst
            }
    }

    // ---- the key

    fun guardianDied(npc: NpcRef) {
        val id = RaidRun.npcRun(npc) ?: return
        val run = Raids.of(npc).run(id)?.takeIf { it.raid.id == TfaRaid.ID } ?: return
        val state = TfaFlow.state(run)
        if (state.keyHandled) return
        state.keyHandled = true
        for (p in run.presentPlayers(npc)) giveKey(p)
    }

    /**
     * TFA26 / OPEN-TFA13: the key drops once the player has 100 completions, has not unlocked the room and holds no key (a
     * lost or destroyed key comes back on the next kill: the item's destroy text promises another from the Guardian). The
     * Wiki gives no rate, only "the first kill after 100", so it is guaranteed.
     */
    private fun giveKey(p: PlayerRef) {
        if (p.varp(Varps.TOTAL_COMPLETED_THEFRACTUREDARCHIVE) < KEY_MIN_COMPLETIONS) return
        if (p.varbit(Varbits.TFA_RECORDS_ROOM_UNLOCKED) == 1 || owns(p, Objs.TFA_RECORDS_ROOM_KEY)) return
        if (p.transaction("tfa archive key") { addOrDrop(Invs.INV, Objs.TFA_RECORDS_ROOM_KEY, 1) }) p.message(KEY_DROP_MESSAGE)
    }

    private fun owns(p: PlayerRef, obj: Int): Boolean =
        p.inv(Invs.INV).total(obj) + p.inv(Invs.WORN).total(obj) + p.inv(Invs.BANK).total(obj) > 0

    // ---- the capes

    private fun required(p: PlayerRef, cape: TfaCape): Int = p.objParamInt(cape.obj, PARAM_COMPLETIONS_REQUIRED) ?: Int.MAX_VALUE

    /** Sets every claim loc's visible varbit from the player's completions (login and after each completion). */
    fun refreshCapes(p: PlayerRef) {
        val kc = p.varp(Varps.TOTAL_COMPLETED_THEFRACTUREDARCHIVE)
        for (cape in CAPES) {
            val value = if (kc >= required(p, cape)) 1 else 0
            if (p.varbit(cape.visibleVarbit) != value) p.setVarbit(cape.visibleVarbit, value)
        }
    }

    /** "Claim": the cape comes into the backpack when the completions are there and the player holds none of it. */
    fun claim(player: PlayerRef, tier: Int) {
        val cape = CAPES.getOrNull(tier) ?: return
        val kc = player.varp(Varps.TOTAL_COMPLETED_THEFRACTUREDARCHIVE)
        when {
            kc < required(player, cape) -> {
                inspect(player, tier)
            }

            owns(player, cape.obj) -> {
                player.message(OWNED_TEXT)
            }

            player.transaction("tfa living record ${cape.obj}") { add(Invs.INV, cape.obj, 1) } -> {
                player.message("You claim ${player.objName(cape.obj)}.")
            }

            else -> {
                player.message(NO_SPACE_TEXT)
            }
        }
    }

    /** "Inspect": the completions needed and the player's own (the tail of client script 10173; OPEN-TFA28). */
    fun inspect(player: PlayerRef, tier: Int) {
        val cape = CAPES.getOrNull(tier) ?: return
        val kc = player.varp(Varps.TOTAL_COMPLETED_THEFRACTUREDARCHIVE)
        player.message(
            "You need ${required(player, cape)} completions of the Fractured Archive to gain that Record. Current completions: $kc",
        )
    }

    /** The Guardian's room id in [TfaRaid.DEFINITION]. */
    private const val GUARDIAN_ROOM = "guardian"
}
