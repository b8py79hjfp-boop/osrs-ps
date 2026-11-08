package tp.content.raids.tfa

import tp.content.raids.PartyState
import tp.content.raids.RaidEvent
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PrayersOffRequest
import tp.plugin.WorldRef
import tp.plugin.mesbox
import tp.symbols.Jingles
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * The Fractured Archive's room flow (tfa.md TFA4-TFA7, TFA15a, TFA24; tfa/lobby-nexus-vault.md): the lobby door and
 * exits, the nexus doors, the side rooms' passageways and transporters, the passage to Ocellus, the Blade and the Void
 * vault. Boss scripts end their room with [cleared].
 */
public class TfaFlow : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart { Raids.of(this).register(TfaRaid.DEFINITION) }

        // ---- lobby
        onOpLoc(Locs.TFA_LOBBY_RAID_ENTRY, OP1) { enterArchive(player) }
        onOpLoc(Locs.TFA_LOBBY_RAID_EXIT, OP1) { player.teleport(TfaRaid.OUTSIDE) }
        for (entrance in intArrayOf(Locs.LUC2_RAID_ENTRY_MULTI, OPENING_OUTSIDE)) {
            onOpLoc(entrance, OP1) { player.teleport(TfaRaid.LOBBY_FROM_OUTSIDE) }
        }
        // TFA2: the outside opening shows once WGS is done; without the quest on this server it is open (TfaConfig).
        onLogin {
            if (!TfaConfig.requireWgs &&
                varbit(Varbits.WGS_TFA_SHORTCUT_UNLOCKED) != 1
            ) {
                setVarbit(Varbits.WGS_TFA_SHORTCUT_UNLOCKED, 1)
            }
        }

        // ---- nexus doors (TFA6)
        for ((section, doors) in DOORS.withIndex()) {
            val (west, east) = TfaRaid.SECTIONS[section]
            for (op in intArrayOf(OP1, OP2)) {
                onOpLoc(doors.first, op) { chooseRoom(player, west) }
                onOpLoc(doors.second, op) { chooseRoom(player, east) }
            }
        }
        // ---- side rooms back to the nexus
        onOpLoc(Locs.TFA_EXIT, OP1) { backToNexus(player) }
        for (t in intArrayOf(Locs.TFA_BOSS_TELEPORTER, Locs.TFA_TELEPORTER_MOIA, Locs.TFA_TELEPORTER_JELLY)) {
            onOpLoc(t, OP1) { backToNexus(player) }
            onOpLoc(t, OP2) { backToNexus(player) }
        }
        // ---- Ocellus and the vault
        onOpLoc(Locs.TFA_EXIT_LARGE, OP1) { chooseRoom(player, "ocellus") }
        onOpNpc(Npcs.TFA_BLADE_NPC_VAULT_ENTRY, OP1) { mesbox(BLADE_COMMUNE) }
        onOpNpc(Npcs.TFA_BLADE_NPC_VAULT_ENTRY, OP3) { chooseRoom(player, "vault") }
        onOpNpc(Npcs.TFA_VAULT_EXIT_SMALL, OP1) { leaveVault(player) }
        onOpNpc(Npcs.TFA_VAULT_EXIT_SMALL, OP3) { leaveVault(player) }

        events.on<RaidEvent.Left> { e -> if (e.run.raid.id == TfaRaid.ID) state(e.run).hudDirty = true }
        events.on<RaidEvent.Entered> { e -> if (e.run.raid.id == TfaRaid.ID) state(e.run).hudDirty = true }
        onLogout { if (isOpen(tp.symbols.Interfaces.TFA_HUD)) TfaHud.close(this) }
        onWorldTick { worldTick(this) }
    }

    private fun worldTick(world: WorldRef) {
        for (run in Raids.of(world).activeRuns) {
            if (run.raid.id != TfaRaid.ID || run.state == RunState.Building || run.state == RunState.Closed) continue
            val state = state(run)
            TfaHud.tick(world, run, state)
            if (state.chestsAt >= 0 && world.tick >= state.chestsAt && !state.chestsSpawned) {
                val any = run.party.members.firstNotNullOfOrNull { world.onlinePlayer(it) } ?: continue
                TfaVault.spawnChests(any, run)
                state.chestsSpawned = true
            }
        }
    }

    /** TFA4: the leader opens the raid; the rest of the party follows through the door while it is in its first room. */
    private fun enterArchive(player: PlayerRef) {
        val raids = Raids.of(player)
        val party = raids.partyOf(player.username)
        if (party == null || party.raid.id != TfaRaid.ID) {
            player.message(NO_PARTY)
            return
        }
        when (val s = party.state) {
            is PartyState.Locked -> {
                Raids.enter(player, s.run)?.let { player.message(it) }
            }

            PartyState.Forming -> {
                if (!party.leader.equals(player.username, ignoreCase = true)) {
                    player.message(LEADER_FIRST)
                } else {
                    val size = maxOf(TfaRaid.MIN_SCALE, party.members.size)
                    Raids
                        .start(
                            player,
                            party,
                            mode = TfaParties.modeOf(party),
                            scaledSize = size,
                            enterNow = false,
                        )?.let { player.message(it) }
                }
            }

            else -> {
                player.message(NO_PARTY)
            }
        }
    }

    /** A nexus door, the Ocellus passage or the Blade: the party moves to [room] when the current slot offers it. */
    private fun chooseRoom(player: PlayerRef, room: String) {
        val run = runOf(player) ?: return
        if (!Raids.advance(player, run, room)) player.message(SEALED)
    }

    /** A side room's passageway or transporter, once the room is done. */
    private fun backToNexus(player: PlayerRef) {
        val run = runOf(player) ?: return
        if (run.state !is RunState.BetweenRooms) {
            player.message(TRANSPORTER_INACTIVE)
            return
        }
        player.teleport(run.at(TfaRaid.NEXUS_RETURN))
    }

    private fun leaveVault(player: PlayerRef) {
        val run = runOf(player) ?: return
        TfaHud.close(player)
        Raids.leave(player, run)
    }

    private fun runOf(player: PlayerRef): RaidRun? {
        val run = Raids.of(player).runOf(player.username) ?: return null
        return run.takeIf { it.raid.id == TfaRaid.ID && it.member(player.username)?.inside == true }
    }

    public companion object {
        /**
         * A TFA room's end condition is met (boss scripts call this): contribution is split, prayers go off (TFA15a),
         * and the nexus moves on (Guardian corpse, the Quartermaster after room 3, the Ocellus passage after room 4, the
         * Blade after Ocellus).
         */
        internal fun cleared(any: EntityRef, run: RaidRun) {
            if (run.state !is RunState.InRoom) return
            Raids.roomCleared(any, run)
            val slot = (run.state as? RunState.BetweenRooms)?.slot ?: return
            for (p in run.presentPlayers(any)) {
                any.events.publish(PrayersOffRequest(p))
                p.jingle(Jingles.TFA_BOSS_DEFEATED)
            }
            val state = state(run)
            when (slot) {
                AFTER_GUARDIAN -> nexusLevel(run, state, NEXUS_GUARDIAN_DEAD)
                AFTER_ROOM_3 -> Raids.spawn(any, run, Npcs.TFA_MIDRAID_SUPPLIES_NPC, TfaRaid.QUARTERMASTER)
                AFTER_ROOM_4 -> nexusLevel(run, state, NEXUS_PASSAGE_OPEN)
                AFTER_OCELLUS -> Raids.spawn(any, run, Npcs.TFA_BLADE_NPC_VAULT_ENTRY, TfaRaid.BLADE)
            }
            state.hudDirty = true
        }

        internal fun state(run: RaidRun): TfaState = run.content as? TfaState ?: TfaState().also { run.content = it }

        /** OPEN-NX4: the nexus square's level [level] copy replaces level 0 (doors, corpse, supply chest, passage). */
        private fun nexusLevel(run: RaidRun, state: TfaState, level: Int) {
            val inst = run.instance ?: return
            if (state.nexusLevel == level) return
            state.nexusLevel = level
            val (sx, sz) = TfaRaid.NEXUS_SQUARE
            for (zx in 0 until ZONES_PER_SQUARE) {
                for (zz in 0 until ZONES_PER_SQUARE) {
                    val x = sx * SQUARE + zx * ZONE
                    val z = sz * SQUARE + zz * ZONE
                    inst.copyZone(CoordGrid(x, z, level), run.at(CoordGrid(x, z, 0)))
                }
            }
        }

        /** Guardian start: the Inert Guardian waits in the nexus to be touched. */
        internal fun guardianRoom(
            run: RaidRun,
            any: EntityRef,
        ): NpcRef = Raids.spawn(any, run, Npcs.TFA_GUARDIAN_START, TfaRaid.INERT_GUARDIAN)

        internal val DOORS =
            listOf(
                Locs.TFA_NEXUS_SECTION1_DOOR_WEST to Locs.TFA_NEXUS_SECTION1_DOOR_EAST,
                Locs.TFA_NEXUS_SECTION2_DOOR_WEST to Locs.TFA_NEXUS_SECTION2_DOOR_EAST,
                Locs.TFA_NEXUS_SECTION3_DOOR_WEST to Locs.TFA_NEXUS_SECTION3_DOOR_EAST,
            )

        private const val SQUARE = 64
        private const val ZONE = 8
        private const val ZONES_PER_SQUARE = 8
        private const val NEXUS_GUARDIAN_DEAD = 2

        /** Slot indices once a room is done (0 Guardian, 1-3 the sections, 4 Ocellus, 5 the vault). */
        private const val AFTER_GUARDIAN = 1
        private const val AFTER_ROOM_3 = 3
        private const val AFTER_ROOM_4 = 4
        private const val AFTER_OCELLUS = 5
        private const val NEXUS_PASSAGE_OPEN = 3
    }
}

private const val OP1 = 1
private const val OP2 = 2
private const val OP3 = 3

/** Loc 62543, the outside opening shown by the multi 62542 when `WGS_TFA_SHORTCUT_UNLOCKED` = 1. */
private const val OPENING_OUTSIDE = 62543

// OPEN-NX8: none of these texts is captured; plausible placeholders until seen in game.
private const val NO_PARTY = "You need to be in a party to enter the Archive. Use the Grouping Board."
private const val LEADER_FIRST = "Your party leader must enter the Archive first."
private const val SEALED = "The way is sealed."
private const val TRANSPORTER_INACTIVE = "The transporter will not work until the room is cleared."
private const val BLADE_COMMUNE = "The Blade hums. It will carry you onward when you are ready."
