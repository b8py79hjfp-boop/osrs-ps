package tp.content.raids.tob

import tp.content.raids.PartyState
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RoomKind
import tp.content.raids.RunState
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.TeleportRequest
import tp.plugin.WorldRef
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * Spectating (tob.md TB21; client-contract.md 2.9): Abigaila's "Scry" (and an attempt to enter the theatre without a
 * party, see [offerSpectating]) lets a player watch a party of this world. The spectator is an observer (instances.md IN11) that
 * stands at the room's spectator tile; the HUD (party slot 0, "Exit" button) is the HUD work's, which calls [stop].
 *
 * OPEN-TB10: no spectator cap, no wipe view and no enter / leave messages are sourced. Not enforced because the plugin
 * API has no accessor: the target's "Friends only" private chat versus the spectator's friends list, and choosing a party
 * by typed name (the choice lists the most recent parties by leader instead).
 */
public class TobSpectate : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.TOB_SPECTATOR_MISTHALIN, OP_SCRY) { offerSpectating() }
        // Client contract 2.9: the spectator HUD's "Exit" button (28:15).
        onButton(SPECTATOR_EXIT, 1) { if (isOpen(tp.symbols.Interfaces.TOB_HUD) && this[WATCHING] != null) stop(this) }
        events.on<TeleportRequest> { req ->
            if (req.handled || req.player[WATCHING] == null) return@on
            req.handled = true
            req.denied = NO_TELEPORT
        }
        onLogout { if (this[WATCHING] != null) stop(this) }
        onWorldTick { tick(this) }
    }

    internal companion object {
        /** Abigaila's cache ops: op1 "Talk-to", op3 "Scry" (client-contract 2.9). */
        private const val OP_SCRY = 3

        /** The run id being watched. */
        val WATCHING = AttrKey<Long>("tob_spectating")

        /** The room the spectator was last placed for ([PLACED_NONE] before the first placement). */
        private val PLACED = AttrKey<String>("tob_spectate_room")

        /** Where the spectator came from, restored on stop. */
        private val ORIGIN = AttrKey<CoordGrid>("tob_spectate_origin")

        /** A chat choice lists at most five options: four parties and "Cancel". */
        const val MAX_LISTED = 4

        private const val PLACED_NONE = ""

        const val NO_TELEPORT = "You can't teleport while spectating."
        const val NONE_TO_WATCH = "There are no Theatre of Blood parties you can spectate right now."
        private const val IN_RAID = "You can't spectate while your party is in the Theatre of Blood."
        private const val CARRYING = "You can't spectate while carrying items, other than an oculus orb."
        private const val AFFLICTED = "You can't spectate while poisoned or diseased."

        /**
         * Spectator tiles per room, template (real map) coordinates on level 1 beside the room's spectator barriers 32760-32762
         * (client-contract 5), on the corridor side the barriers close off (OPEN-TB10: which side is the ledge is not in the
         * cache). Maiden (3174, 4430-4432), Bloat (3280-3282, 4440), Sotetseg (3273, 4300-4302). Verzik: beside the throne
         * multi 32736 (3166, 4324, L1). Treasure room: beside the war table 33011 (3232, 4325). All tiles are free in the cache
         * collision. Nylocas (ceiling web) and Xarpus (high ledge) have no sourced tile: the room's purgatory tile is used.
         */
        private val SPOTS: Map<String, CoordGrid> =
            mapOf(
                "maiden" to CoordGrid(3176, 4431, 1),
                "bloat" to CoordGrid(3281, 4436, 1),
                "nylocas" to TobRaid.NYLOCAS_WAIT,
                "sotetseg" to CoordGrid(3276, 4301, 1),
                "xarpus" to TobRaid.XARPUS_WAIT,
                "verzik" to CoordGrid(3168, 4326, 1),
                "treasure" to CoordGrid(3231, 4326, 0),
            )

        /** The runs that can be watched, the most recent first. */
        fun openRuns(
            player: PlayerRef,
        ): List<RaidRun> =
            Raids
                .of(player)
                .activeRuns
                .filter { open(it) }
                .sortedByDescending { it.startedTick }

        private fun open(run: RaidRun): Boolean =
            run.raid.id == TobRaid.ID &&
                run.instance != null &&
                (run.state is RunState.InRoom || run.state is RunState.BetweenRooms)

        /** TB21: the entry conditions, then [player] becomes an observer of [run]. */
        fun start(player: PlayerRef, run: RaidRun) {
            if (!open(run)) return
            if (Raids.of(player).partyOf(player.username)?.state is PartyState.Locked) {
                player.message(IN_RAID)
                return
            }
            if (carriesItems(player)) {
                player.message(CARRYING)
                return
            }
            if (player.varp(Varps.POISON) > 0 || player.varp(Varps.DISEASE) > 0) {
                player.message(AFFLICTED)
                return
            }
            run.spectators += player.username
            player[WATCHING] = run.id.value
            player[PLACED] = PLACED_NONE
            player[ORIGIN] = player.coord
            player.observer = true
            player.setInstanceExit(player.coord)
        }

        /** TB21: no items may be carried except the oculus orb, in the backpack or worn. */
        private fun carriesItems(player: PlayerRef): Boolean =
            listOf(Invs.INV, Invs.WORN).any { invId ->
                val inv = player.inv(invId)
                (0 until inv.size).any { slot -> inv.obj(slot) != EMPTY && inv.obj(slot) != Objs.OCULUS }
            }

        private const val EMPTY = -1

        /** Ends [player]'s watch and returns them to where they started (also the HUD's spectator "Exit"). */
        fun stop(player: PlayerRef) {
            val id = player[WATCHING] ?: return
            val origin = player[ORIGIN]
            player[WATCHING] = null
            player[PLACED] = null
            player[ORIGIN] = null
            player.observer = false
            player.setInstanceExit(null)
            Raids
                .of(player)
                .run(id)
                ?.spectators
                ?.remove(player.username)
            if (origin != null) player.teleport(origin)
        }

        /** Every tick: a spectator is moved to the tile of the room being fought; a finished run ends the watch. */
        private fun tick(world: WorldRef) {
            for (run in Raids.of(world).activeRuns) {
                if (run.raid.id != TobRaid.ID || run.spectators.isEmpty()) continue
                val ended = run.state is RunState.Failed || run.state == RunState.Abandoned || run.state == RunState.Closed
                for (name in run.spectators.toList()) {
                    val p = world.onlinePlayer(name)
                    if (p == null) {
                        run.spectators.remove(name)
                    } else if (p[WATCHING] != run.id.value || ended) {
                        stop(p)
                    } else {
                        place(p, run)
                    }
                }
            }
        }

        /** Moves [p] to the spectator tile of the room in progress, once per room; between rooms they stay put. */
        private fun place(p: PlayerRef, run: RaidRun) {
            val room = (run.state as? RunState.InRoom)?.room ?: return
            if (room.kind != RoomKind.ENCOUNTER && room.kind != RoomKind.REWARD) return
            if (p[PLACED] == room.id) return
            val spot = SPOTS[room.id] ?: return
            p[PLACED] = room.id
            p.teleport(run.at(spot))
        }
    }
}

/**
 * TB21: asks which party to watch (the most recent first) and starts watching it. Called from Abigaila's "Scry"; the
 * theatre entrance calls it too when the player has no party.
 */
internal suspend fun PlayerScript.offerSpectating() {
    val runs =
        TobSpectate
            .openRuns(player)
            .take(TobSpectate.MAX_LISTED)
    if (runs.isEmpty()) {
        player.message(TobSpectate.NONE_TO_WATCH)
        return
    }
    val labels = runs.map { "${it.party.leader}'s party" } + "Cancel"
    val pick = choice("Spectate which party?", *labels.toTypedArray())
    val run = runs.getOrNull(pick - 1) ?: return
    TobSpectate.start(player, run)
}

/** Component 28:15, the spectator "Exit" button (two after the spectator container 28:13). */
private const val SPECTATOR_EXIT = tp.symbols.Components.Tob_Hud.SPECTATOR_CONTAINER + 2
