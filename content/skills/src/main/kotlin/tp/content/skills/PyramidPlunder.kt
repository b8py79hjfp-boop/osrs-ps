package tp.content.skills

import tp.plugin.AttackCheck
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DamageRequest
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.util.CoordGrid

/** One player's running game: when it started, the room they are in and what they did there. */
internal class PpRun(val started: Long) {
    var room = 1

    /** Bit mask of the wrong doors the player has opened in this room (PP9). */
    var tried = 0

    /** Bit 0: the sarcophagus has been searched; bit 1: the chest has been opened. */
    var looted = 0

    /** The scarab swarms and mummies the player woke up (removed when the game ends). */
    val npcs = ArrayList<NpcRef>(NPCS_CAPACITY)

    private companion object {
        /** A handful of swarms and mummies per game. */
        const val NPCS_CAPACITY = 8
    }
}

/**
 * The Pyramid Plunder minigame (docs/mechanics/pyramid-plunder.md): the Guardian mummy's lobby behind the pyramid's four
 * doors, the eight rooms in the shared map square, the five-minute clock and the HUD. Every player has their own state in
 * varbits, so the rooms are shared and look different to each player (urns opened, chest, doors), as in the game. The only
 * world state is the correct entrance and the correct door of each room.
 */
public class PyramidPlunder : ContentPlugin {
    /** Index (0..3, north east south west) of the pyramid door that leads to the Guardian mummy, and when it changes (PP2). */
    private var entrance = -1
    private var entranceUntil = 0L

    /** Index (0..3) of the correct tomb door of each room, re-rolled whenever a game starts (PP9). */
    private val doors = IntArray(PpData.ROOMS) { -1 }

    override fun ContentScope.register() {
        onWorldStart {
            entrance = -1
            doors.fill(-1)
            if (System.getProperty("tp.worldSpawns") != "false") spawnNpc(Npcs.NTK_MUMMY_GUARDIAN, GUARDIAN_AT, 0)
        }
        onLogin { if (PpData.inside(coord)) teleport(OUTSIDE) }
        onLogout { end(this, closeHud = false, message = null) }
        events.on<AttackCheck> { check -> denyOthers(check) }

        // the pyramid's four doors (PP2)
        for ((side, ids) in OUTSIDE_DOORS.withIndex()) {
            for (id in ids) onOpLoc(id, OP_FIRST) { outsideDoor(side) }
        }
        onOpLoc(Locs.NTK_ANTECHAMBER_EXIT, OP_FIRST) { leave() }
        onOpLoc(Locs.NTK_TOMB_DOOR_EXIT, OP_FIRST) { leave() }
        onOpLoc(Locs.NTK_TOMB_DOOR_EXIT, OP_SECOND) { leave() }
        onOpNpc(Npcs.NTK_MUMMY_GUARDIAN, OP_START) { start() }

        // the rooms
        for (id in URN_IDS) onOpLoc(id, OP_FIRST) { loc -> searchUrn(loc) }
        for (id in URN_OP_SECOND) onOpLoc(id, OP_SECOND) { loc -> urnSecond(loc) }
        for (id in CHEST_IDS) onOpLoc(id, OP_FIRST) { loc -> openChest(loc) }
        for (id in SARCOPHAGUS_IDS) onOpLoc(id, OP_FIRST) { loc -> sarcophagus(loc) }
        for (id in DOOR_IDS) onOpLoc(id, OP_FIRST) { loc -> tombDoor(loc) }
        onOpLoc(Locs.NTK_SPEARTRAP_INMOTION, OP_FIRST) { loc -> passTrap(loc) }
    }

    // ---- the pyramid's doors and the lobby

    /** Re-rolls the entrance every [ENTRANCE_TICKS] (PP2). Lazily, on the next use: nobody can tell the difference. */
    private fun rollEntrance(p: PlayerRef) {
        if (entrance >= 0 && p.tick < entranceUntil) return
        entrance = p.random(OUTSIDE_DOORS.size)
        entranceUntil = p.tick + ENTRANCE_TICKS
    }

    private suspend fun PlayerScript.outsideDoor(side: Int) {
        val p = player
        if (p[RUN] != null) return
        rollEntrance(p)
        if (side != entrance) return p.teleport(LOBBIES[1 + side - if (side > entrance) 1 else 0])
        // PP2: the right door needs 21 Thieving (not boostable); the roll is the Wiki chart's 160..250.
        val opened = p.baseLevel(Stats.THIEVING) >= PpData.LEVEL[0] && p.statRandom(Stats.THIEVING, ENTRY_LOW, ENTRY_HIGH)
        if (opened) return p.teleport(LOBBIES[0])
        p.message("A mysterious bandaged hand punches you away from the door.  Ouch!")
        hurt(p, null, 1 + p.random(FAIL_DAMAGE))
        stun()
    }

    private fun PlayerScript.leave() {
        val p = player
        if (p[RUN] != null) end(p, closeHud = true, message = null) else p.teleport(OUTSIDE)
    }

    private fun PlayerScript.start() {
        val p = player
        if (p[RUN] != null) return
        if (p.baseLevel(Stats.THIEVING) < PpData.LEVEL[0]) {
            return p.message("You need a Thieving level of ${PpData.LEVEL[0]} to enter the pyramid.")
        }
        // PP9: the doors change whenever a player enters the first room.
        for (i in doors.indices) doors[i] = p.random(DOORS_PER_ROOM)
        val run = PpRun(p.tick)
        p[RUN] = run
        p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.NTK_OVERLAY)
        p.setVarbit(Varbits.NTK_PLAYER_TIMER_COUNT, 0)
        enter(p, run, 1)
        p.timer(CLOCK, 1, soft = true) { clock() }
    }

    /** Puts [p] at the start of [room] with a fresh state (PP4). */
    private fun enter(p: PlayerRef, run: PpRun, room: Int) {
        run.room = room
        run.tried = 0
        run.looted = 0
        resetRoom(p)
        p.setVarbit(Varbits.NTK_ROOM_NUMBER, room)
        p.setVarbit(Varbits.NTK_CURRENT_ROOM_LEVEL, room)
        p.setVarbit(Varbits.NTK_THIEVING_REQUIRED, PpData.LEVEL[room - 1])
        p.teleport(PpData.ARRIVAL[room - 1])
    }

    private fun resetRoom(p: PlayerRef) {
        for (slot in 0 until URN_SLOTS) p.setVarbit(Varbits.NTK_URN1_STATE + slot, 0)
        for (door in 0 until DOORS_PER_ROOM) p.setVarbit(Varbits.NTK_DOOR1_STATE + door, 0)
        p.setVarbit(Varbits.NTK_GOLDEN_CHEST_STATE, 0)
        p.setVarbit(Varbits.NTK_SARCOPHAGUS_STATE, 0)
        p.setVarbit(Varbits.NTK_TRAP_ACTIVE, 0)
    }

    /** The five-minute clock (PP3); a player who left the rooms by any route ends the game. */
    private fun PlayerRef.clock() {
        val run = this[RUN] ?: return stopTimer(CLOCK)
        val elapsed = tick - run.started
        if (!PpData.inside(coord) || coord.level != 0) return end(this, closeHud = true, message = null)
        if (elapsed >= RUN_TICKS) return end(this, closeHud = true, message = "Your time in the pyramid has run out.")
        setVarbit(Varbits.NTK_PLAYER_TIMER_COUNT, elapsed.toInt())
    }

    /** Ends [p]'s game: the clock, HUD, state and the monsters go, and the player is put outside if still inside (PP3). */
    private fun end(p: PlayerRef, closeHud: Boolean, message: String?) {
        val run = p[RUN] ?: return
        p[RUN] = null
        p.stopTimer(CLOCK)
        for (npc in run.npcs) if (npc.isInWorld) npc.despawn()
        run.npcs.clear()
        resetRoom(p)
        p.setVarbit(Varbits.NTK_PLAYER_TIMER_COUNT, 0)
        p.setVarbit(Varbits.NTK_ROOM_NUMBER, 0)
        p.setVarbit(Varbits.NTK_CURRENT_ROOM_LEVEL, 0)
        p.setVarbit(Varbits.NTK_THIEVING_REQUIRED, 0)
        if (closeHud) p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
        if (PpData.inside(p.coord)) p.teleport(OUTSIDE)
        if (message != null) p.message(message)
    }

    // ---- helpers shared by the rooms

    /** The running game and the placed loc [loc] if it is in the player's room (the packet may name any loc), else null. */
    private fun PlayerRef.spot(loc: LocRef, kind: PpKind): Pair<PpRun, PpSpot>? {
        val run = this[RUN] ?: return null
        val spot = PpData.SPOTS[PpData.key(loc.coord)] ?: return null
        return if (spot.kind == kind && spot.room == run.room) run to spot else null
    }

    /** A regular hit from [source]; the combat module deals it, otherwise a non-lethal fallback (as pickpocketing, OPEN-TH2). */
    private fun hurt(p: PlayerRef, source: NpcRef?, damage: Int) {
        val request = DamageRequest(p, source, damage)
        p.events.publish(request)
        if (request.handled) return
        val dealt = minOf(damage, p.hitpoints - 1).coerceAtLeast(0)
        p.hitpoints -= dealt
        p.hitsplat(source, dealt, HITMARK_DAMAGE_LIT, HITMARK_DAMAGE_LIT, HITMARK_DAMAGE_TINT)
    }

    private suspend fun PlayerScript.stun() {
        player.anim(Seqs.STUNNED_THIEVING)
        player.spotAnim(SpotAnims.STUNNED_THIEVING)
        delay(STUN_TICKS)
    }

    /** Gives one [obj] or tells the player the backpack is full; false if nothing was given. */
    private fun give(p: PlayerRef, obj: Int): Boolean {
        val given = p.transaction("pyramid plunder") { add(Invs.INV, obj, 1) }
        if (!given) p.message("You don't have enough inventory space.")
        return given
    }

    private fun pick(p: PlayerRef, table: List<PpLoot>): Int {
        var roll = p.random(PpData.LOOT_DENOMINATOR)
        for (line in table) {
            roll -= line.weight
            if (roll < 0) return line.obj
        }
        return table.last().obj
    }

    /** PP7: the sceptre rolls once per chest search and per sarcophagus attempt; finding it ends the game (PP8). */
    private fun sceptre(p: PlayerRef, room: Int): Boolean {
        if (p.random(PpData.SCEPTRE_ODDS[room - 1]) != 0) return false
        end(p, closeHud = true, message = "You find the Pharaoh's sceptre! The Guardian mummy throws you out of the pyramid.")
        p.transaction("pyramid plunder") { addOrDrop(Invs.INV, Objs.PHARAOHS_SCEPTRE, 1) }
        return true
    }

    /** Wiki "Pyramid Plunder": a success chart's chances drop by 1/256 for every room after the first (urns, doors). */
    private fun roomRate(base: Int, room: Int): Int = base - (room - 1)

    // ---- urns (PP6)

    private suspend fun PlayerScript.searchUrn(loc: LocRef) {
        val p = player
        val (run, spot) = p.spot(loc, PpKind.URN) ?: return
        val room = run.room
        val varbit = Varbits.NTK_URN1_STATE + spot.index - 1
        val state = p.varbit(varbit)
        if (state == URN_OPEN) return p.message("You search the urn but find nothing.")
        p.stopInteraction()
        val opened =
            when (state) {
                URN_CHARMED -> true
                URN_SNAKE -> p.statRandom(Stats.THIEVING, roomRate(SNAKE_LOW, room), roomRate(SNAKE_HIGH, room))
                else -> p.statRandom(Stats.THIEVING, roomRate(SEARCH_LOW, room), roomRate(SEARCH_HIGH, room))
            }
        if (!opened) {
            p.message("A snake bites you!")
            hurt(p, null, 1 + p.random(FAIL_DAMAGE))
            return delay(ACTION_TICKS)
        }
        val loot = PpData.urnLoot(room)
        if (!give(p, loot[p.random(loot.size)])) return
        p.setVarbit(varbit, URN_OPEN)
        // Wiki: searching grants as much as checking and then searching; with the snake found only the rest is left.
        val xp =
            if (state == URN_SNAKE ||
                state == URN_CHARMED
            ) {
                PpData.URN_XP[room - 1] - PpData.CHECK_XP[room - 1]
            } else {
                PpData.URN_XP[room - 1]
            }
        p.addXp(Stats.THIEVING, xp)
        delay(ACTION_TICKS)
    }

    /** Op 2 on an urn: "Check for Snakes" on a closed one, "Charm Snake" on one with a snake. */
    private suspend fun PlayerScript.urnSecond(loc: LocRef) {
        val p = player
        val (run, spot) = p.spot(loc, PpKind.URN) ?: return
        val room = run.room
        val varbit = Varbits.NTK_URN1_STATE + spot.index - 1
        when (p.varbit(varbit)) {
            URN_CLOSED -> {
                p.stopInteraction()
                if (p.statRandom(Stats.THIEVING, roomRate(CHECK_LOW, room), roomRate(CHECK_HIGH, room))) {
                    p.setVarbit(varbit, URN_SNAKE)
                    p.addXp(Stats.THIEVING, PpData.CHECK_XP[room - 1])
                    p.message("You find a snake. It stares at you menacingly.")
                } else {
                    p.message("You check the urn but find nothing.")
                }
                delay(ACTION_TICKS)
            }

            URN_SNAKE -> {
                if (p.inv(Invs.INV).total(Objs.SNAKE_FLUTE) == 0L) return p.message("You need a snake charm to charm the snake.")
                p.setVarbit(varbit, URN_CHARMED)
                p.message("You play the snake charm and the snake sways calmly.")
                delay(ACTION_TICKS)
            }
        }
    }

    // ---- the grand gold chest (PP7)

    private suspend fun PlayerScript.openChest(loc: LocRef) {
        val p = player
        val (run, _) = p.spot(loc, PpKind.CHEST) ?: return
        if ((run.looted and LOOTED_CHEST) != 0) return
        val room = run.room
        p.stopInteraction()
        if (sceptre(p, room)) return
        // Wiki: the chest opens at once; failing the Thieving roll is the trap (scarab swarm, no xp, artefact still given).
        val trapped = !p.statRandom(Stats.THIEVING, CHEST_LOW, CHEST_HIGH)
        if (!give(p, pick(p, PpData.CHEST_LOOT[room - 1]))) return
        run.looted = run.looted or LOOTED_CHEST
        p.setVarbit(Varbits.NTK_GOLDEN_CHEST_STATE, 1)
        if (trapped) {
            p.message("A scarab swarm bursts out of the chest!")
            hurt(p, null, SWARM_DAMAGE)
            wake(p, run, Npcs.NTK_SCARAB_SWARM)
        } else {
            p.addXp(Stats.THIEVING, PpData.CHEST_XP[room - 1])
        }
    }

    // ---- the sarcophagus (PP7)

    /** Op 1 is "Open" on the closed sarcophagus and "Search" on the open one: the player's varbit tells which. */
    private suspend fun PlayerScript.sarcophagus(loc: LocRef) {
        val p = player
        val (run, _) = p.spot(loc, PpKind.SARCOPHAGUS) ?: return
        if (p.varbit(Varbits.NTK_SARCOPHAGUS_STATE) == 0) openSarcophagus(run) else searchSarcophagus(run)
    }

    private suspend fun PlayerScript.openSarcophagus(run: PpRun) {
        val p = player
        val room = run.room
        val level = PpData.LEVEL[room - 1]
        p.stopInteraction()
        // Wiki: it keeps trying until it opens; each attempt rolls the sceptre, even a failed one.
        while (true) {
            delay(SARCOPHAGUS_TICKS)
            if (p[RUN] !== run || run.room != room) return
            if (sceptre(p, room)) return
            if (p.statRandom(Stats.STRENGTH, SARCOPHAGUS_LOW - level, SARCOPHAGUS_HIGH - level)) break
        }
        p.setVarbit(Varbits.NTK_SARCOPHAGUS_STATE, 1)
        p.addXp(Stats.STRENGTH, PpData.SARCOPHAGUS_XP[room - 1])
        if (p.random(MUMMY_DENOMINATOR) < room) {
            p.message("A mummy rises from the sarcophagus!")
            wake(p, run, Npcs.NTK_MUMMY_1)
        }
    }

    private fun PlayerScript.searchSarcophagus(run: PpRun) {
        val p = player
        if ((run.looted and LOOTED_SARCOPHAGUS) != 0) return p.message("You search the sarcophagus but find nothing.")
        if (!give(p, pick(p, PpData.SARCOPHAGUS_LOOT[run.room - 1]))) return
        run.looted = run.looted or LOOTED_SARCOPHAGUS
    }

    /** A scarab swarm or mummy that is aggressive to [p] only (Wiki "Scarab Swarm" / "Mummy (Pyramid Plunder)"). */
    private fun wake(p: PlayerRef, run: PpRun, type: Int) {
        val npc = p.spawnNpc(type, p.coord, 0)
        npc[OWNER] = p.username
        run.npcs += npc
    }

    private fun denyOthers(check: AttackCheck) {
        val owner = (check.attacker as? NpcRef)?.get(OWNER) ?: return
        if ((check.target as? PlayerRef)?.username != owner) check.deny("It is not interested in you.")
    }

    // ---- tomb doors and the speartrap (PP5, PP9)

    private suspend fun PlayerScript.tombDoor(loc: LocRef) {
        val p = player
        val (run, spot) = p.spot(loc, PpKind.DOOR) ?: return
        val room = run.room
        val slot = spot.index - 1
        if (room == PpData.ROOMS) return end(p, closeHud = true, message = null)
        val passes = slot == doors[room - 1] || (Integer.bitCount(run.tried) == WRONG_DOORS && (run.tried and (1 shl slot)) == 0)
        val varbit = Varbits.NTK_DOOR1_STATE + slot
        p.stopInteraction()
        if (p.varbit(varbit) == 0) {
            val lockpick = p.inv(Invs.INV).total(Objs.LOCKPICK) > 0
            val low = roomRate(if (lockpick) PICK_LOW_LOCKPICK else PICK_LOW, room)
            val high = roomRate(if (lockpick) PICK_HIGH_LOCKPICK else PICK_HIGH, room)
            if (!p.statRandom(Stats.THIEVING, low, high)) {
                p.message("You fail to pick the lock.")
                return delay(ACTION_TICKS)
            }
            p.addXp(Stats.THIEVING, PpData.DOOR_XP[room - 1] / if (lockpick) 2 else 1)
            p.setVarbit(varbit, 1)
            if (!passes) run.tried = run.tried or (1 shl slot)
        }
        if (!passes) return p.message("The door opens but leads nowhere.")
        if (p.baseLevel(Stats.THIEVING) < PpData.LEVEL[room]) {
            return p.message("You need a Thieving level of ${PpData.LEVEL[room]} to enter the next room.")
        }
        delay(ACTION_TICKS)
        if (p[RUN] === run) enter(p, run, room + 1)
    }

    private suspend fun PlayerScript.passTrap(loc: LocRef) {
        val p = player
        val (run, _) = p.spot(loc, PpKind.TRAP) ?: return
        p.stopInteraction()
        if (!p.statRandom(Stats.THIEVING, TRAP_LOW, TRAP_HIGH)) {
            p.message("You fail to disarm the trap.")
            hurt(p, null, 1 + p.random(FAIL_DAMAGE))
            return delay(ACTION_TICKS)
        }
        p.addXp(Stats.THIEVING, PpData.TRAP_XP)
        val past = PpData.PAST_TRAP[run.room - 1]
        if (!p.walkTo(past)) p.teleport(past)
    }

    private companion object {
        const val OP_FIRST = 1
        const val OP_SECOND = 2

        /** Cache 2757: the Guardian mummy's op 3 "Start-minigame" (op 1 is "Talk-to", handled by the dialogue). */
        const val OP_START = 3

        /** Wiki "Pyramid Plunder", Gameplay: five minutes, 500 ticks of 0.6 seconds. */
        const val RUN_TICKS = 500L

        /** Wiki "An anonymous looking door": the correct entrance is randomised every 25 minutes (2,500 ticks). */
        const val ENTRANCE_TICKS = 2500L

        /** Four doors on each pyramid wall's side and in each room (Wiki "Tomb Door": one correct, three fake). */
        const val DOORS_PER_ROOM = 4

        /** Wiki "Tomb Door": after three wrong doors the fourth always lets the player through. */
        const val WRONG_DOORS = 3

        /** Cache 2757: the urn varbits `ntk_urn1_state`..`ntk_urn15_state` (15 slots per room, 13 or 12 used). */
        const val URN_SLOTS = 15

        /** States of an urn's multi loc (cache 2757 `multiLocs`: closed, open, snake, charmed snake). */
        const val URN_CLOSED = 0
        const val URN_OPEN = 1
        const val URN_SNAKE = 2
        const val URN_CHARMED = 3

        const val LOOTED_SARCOPHAGUS = 1
        const val LOOTED_CHEST = 2

        /** Wiki "Pyramid Plunder": failures (stunned at a door, urn bite, speartrap) hurt for 1-4. */
        const val FAIL_DAMAGE = 4

        /** Wiki "Grand Gold Chest": finding a swarm "instantly and unavoidably damages the player by 3 hitpoints" (OPEN-PP7). */
        const val SWARM_DAMAGE = 3

        /** Wiki "Sarcophagus (Pyramid Plunder)": the mummy appears with chance room number out of 9. */
        const val MUMMY_DENOMINATOR = 9

        /** Skilling chart `low` / `high` (chances out of 256 at levels 1 / 99) of the Wiki pages, first room. */
        const val ENTRY_LOW = 160
        const val ENTRY_HIGH = 250
        const val SEARCH_LOW = 100
        const val SEARCH_HIGH = 180
        const val SNAKE_LOW = 130
        const val SNAKE_HIGH = 220
        const val CHECK_LOW = 160
        const val CHECK_HIGH = 250
        const val CHEST_LOW = 130
        const val CHEST_HIGH = 220
        const val PICK_LOW = 130
        const val PICK_HIGH = 220
        const val PICK_LOW_LOCKPICK = 160
        const val PICK_HIGH_LOCKPICK = 250
        const val TRAP_LOW = 160
        const val TRAP_HIGH = 250

        /** Wiki "Sarcophagus (Pyramid Plunder)": Strength chart `low = 80 - room Thieving level`, `high = 270 - room level`. */
        const val SARCOPHAGUS_LOW = 80
        const val SARCOPHAGUS_HIGH = 270

        /** Ticks an action takes (OPEN-PP10: no source; the standard skilling pace) and the sarcophagus attempt and stun lengths. */
        const val ACTION_TICKS = 2
        const val SARCOPHAGUS_TICKS = 4
        const val STUN_TICKS = 5

        /** Hitmark config ids of a regular hit (same as the combat module's: lit for the victim, tint for others). */
        const val HITMARK_DAMAGE_LIT = 16
        const val HITMARK_DAMAGE_TINT = 17

        val RUN = AttrKey<PpRun>("pyramid_plunder_run")
        val OWNER = AttrKey<String>("pyramid_plunder_owner")
        val CLOCK = TimerId("pyramid_plunder")

        /** Wiki "Jalsavrah Pyramid": leaving puts the player outside the north door (the free tile in the cache's wall gap). */
        val OUTSIDE = CoordGrid(3288, 2801, 0)

        /** Where the Guardian mummy stands in his lobby (OPEN-PP3). */
        val GUARDIAN_AT = CoordGrid(1932, 4454, 2)

        /**
         * The four lobbies behind the pyramid doors (cache: the antechamber exit doors `ntk_antechamber_exit`), first tile
         * inside each door. Index 0 holds the Guardian mummy; the others are the empty rooms (OPEN-PP3).
         */
        val LOBBIES =
            listOf(CoordGrid(1934, 4450, 2), CoordGrid(1968, 4420, 2), CoordGrid(1934, 4420, 3), CoordGrid(1968, 4450, 3))

        /** Cache 2757: the pyramid's door multi loc and its three states per side (north, east, south, west). */
        val OUTSIDE_DOORS =
            listOf(
                listOf(
                    Locs.NTK_PYRAMID_DOOR_NORTH_MULTI,
                    Locs.NTK_PYRAMID_DOOR_NORTH_NOANIM,
                    Locs.NTK_PYRAMID_DOOR_NORTH_ANIM,
                    Locs.NTK_PYRAMID_DOOR_NORTH_OPEN_NOANIM,
                ),
                listOf(
                    Locs.NTK_PYRAMID_DOOR_EAST_MULTI,
                    Locs.NTK_PYRAMID_DOOR_EAST_NOANIM,
                    Locs.NTK_PYRAMID_DOOR_EAST_ANIM,
                    Locs.NTK_PYRAMID_DOOR_EAST_OPEN_NOANIM,
                ),
                listOf(
                    Locs.NTK_PYRAMID_DOOR_SOUTH_MULTI,
                    Locs.NTK_PYRAMID_DOOR_SOUTH_NOANIM,
                    Locs.NTK_PYRAMID_DOOR_SOUTH_ANIM,
                    Locs.NTK_PYRAMID_DOOR_SOUTH_OPEN_NOANIM,
                ),
                listOf(
                    Locs.NTK_PYRAMID_DOOR_WEST_MULTI,
                    Locs.NTK_PYRAMID_DOOR_WEST_NOANIM,
                    Locs.NTK_PYRAMID_DOOR_WEST_ANIM,
                    Locs.NTK_PYRAMID_DOOR_WEST_OPEN_NOANIM,
                ),
            )

        /** The urn multi locs in slot order and the children of the three urn kinds (the client may report either, OPEN-PP11). */
        val URN_PARENTS =
            listOf(
                Locs.NTK_URN_TYPE1_MULTI_1,
                Locs.NTK_URN_TYPE1_MULTI_2,
                Locs.NTK_URN_TYPE1_MULTI_3,
                Locs.NTK_URN_TYPE1_MULTI_4,
                Locs.NTK_URN_TYPE1_MULTI_5,
                Locs.NTK_URN_TYPE2_MULTI_6,
                Locs.NTK_URN_TYPE2_MULTI_7,
                Locs.NTK_URN_TYPE2_MULTI_8,
                Locs.NTK_URN_TYPE2_MULTI_9,
                Locs.NTK_URN_TYPE2_MULTI_10,
                Locs.NTK_URN_TYPE3_MULTI_11,
                Locs.NTK_URN_TYPE3_MULTI_12,
                Locs.NTK_URN_TYPE3_MULTI_13,
                Locs.NTK_URN_TYPE3_MULTI_14,
                Locs.NTK_URN_TYPE3_MULTI_15,
            )
        val URN_CLOSED_LOCS = listOf(Locs.NTK_URN1_CLOSED, Locs.NTK_URN2_CLOSED, Locs.NTK_URN3_CLOSED)
        val URN_SNAKE_LOCS = listOf(Locs.NTK_URN1_SNAKE, Locs.NTK_URN2_SNAKE, Locs.NTK_URN3_SNAKE)
        val URN_OTHER_LOCS =
            listOf(
                Locs.NTK_URN1_OPEN,
                Locs.NTK_URN2_OPEN,
                Locs.NTK_URN3_OPEN,
                Locs.NTK_URN1_SNAKE_CHARMED,
                Locs.NTK_URN2_SNAKE_CHARMED,
                Locs.NTK_URN3_SNAKE_CHARMED,
            )

        /** Op 1 "Search" on every urn state; op 2 on closed ("Check for Snakes") and snake ("Charm Snake") urns. */
        val URN_IDS: Set<Int> = (URN_PARENTS + URN_CLOSED_LOCS + URN_SNAKE_LOCS + URN_OTHER_LOCS).toSet()
        val URN_OP_SECOND: Set<Int> = (URN_PARENTS + URN_CLOSED_LOCS + URN_SNAKE_LOCS).toSet()

        val CHEST_IDS = listOf(Locs.NTK_GOLDEN_CHEST_MULTI, Locs.NTK_GOLDEN_CHEST_MULTI_1, Locs.NTK_GOLDEN_CHEST_CLOSED)
        val SARCOPHAGUS_IDS =
            listOf(Locs.NTK_SARCOPHAGUS_MULTI, Locs.NTK_SARCOPHAGUS_MULTI_1, Locs.NTK_SARCOPHAGUS, Locs.NTK_SARCOPHAGUS_OPEN)
        val DOOR_IDS =
            listOf(
                Locs.NTK_TOMB_DOOR,
                Locs.NTK_TOMB_DOOR1,
                Locs.NTK_TOMB_DOOR2,
                Locs.NTK_TOMB_DOOR3,
                Locs.NTK_TOMB_DOOR4,
                Locs.NTK_TOMB_DOOR_NOANIM,
                Locs.NTK_TOMB_DOOR_ANIM,
            )
    }
}
