package tp.content.raids.tob

import tp.content.raids.PartyState
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.EntityRef
import tp.plugin.HitEvent
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.WorldRef
import tp.symbols.Locs
import tp.symbols.Varps
import tp.util.CoordGrid

/** A run's ToB bookkeeping (tob.md TB8, TB9, TB27; lobby-rewards LR16-LR18, LR22). */
internal class TobState {
    /** Whether the current room's fight has started (a player crossed its barrier, TB8). */
    var engaged = false
    var fightStartTick = -1L

    /** Members who took part in each room's fight (crossed or stood inside when it started). */
    val entered = HashMap<String, MutableSet<String>>()

    /** MVP points per member (LR18). */
    val mvp = HashMap<String, Int>()

    /** Challenge time: ticks spent in boss fights (TB34). */
    var challengeTicks = 0L

    /** Ticks per room fight, in order (the performance panel, LR22). */
    val roomTicks = LinkedHashMap<String, Long>()

    /** Verzik's damage per member per phase (P1, P2, P3) for her three MVP awards. */
    val verzikPhaseDamage = Array(VERZIK_PHASES) { HashMap<String, Int>() }
    var verzikPhase = 0

    /** The tick Verzik died: the overall time ends at her final blow (LR22). */
    var verzikDeadTick = -1L

    /** Midway supply points per member (TB22). */
    val supplyPoints = HashMap<String, Int>()

    companion object {
        const val VERZIK_PHASES = 3
    }
}

/**
 * The Theatre of Blood's room flow (tob.md TB7-TB9, TB14, TB24; lobby-rewards LR14): the theatre entrance, the
 * barriers that start each fight, a room's exit into the next (the hallways are not in the cache, OPEN-TB7), the
 * full restore when a boss dies, and the throne door into the treasure room.
 */
public class TobFlow : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart { Raids.of(this).register(TobRaid.DEFINITION) }
        onOpLoc(Locs.TOB_SURFACE_RAID_ENTRANCE, OP1) {
            // TB21: without a party, the entrance offers spectating.
            if (!enterTheatre(player)) offerSpectating()
        }
        onOpLoc(Locs.TOB_ARENA_BARRIER, OP1) { loc -> pass(player, loc) }
        onOpLoc(Locs.TOB_DUNGEON_VERZIK_THRONE_DOOR_OPENED, OP1) { toTreasure(player) }
        events.on<DeathEvent> { e -> (e.entity as? NpcRef)?.let { bossDied(it) } }
        events.on<HitEvent> { e -> verzikDamage(e) }
        onWorldTick { worldTick(this) }
    }

    /** TB1 / TB3: the leader starts the raid in the party's mode; the others follow while it is in the Maiden's room. */
    private fun enterTheatre(player: PlayerRef): Boolean {
        val party = Raids.of(player).partyOf(player.username)
        if (party == null || party.raid.id != TobRaid.ID) return false
        when (val s = party.state) {
            is PartyState.Locked -> {
                Raids.enter(player, s.run)?.let { player.message(it) }
            }

            PartyState.Forming -> {
                if (!party.leader.equals(player.username, ignoreCase = true)) {
                    player.message(LEADER_FIRST)
                    return true
                }
                val mode = TobParties.modeOf(party)
                if (mode == TobRaid.HARD) {
                    for (name in party.members) {
                        val p = player.onlinePlayer(name) ?: continue
                        TobConfig.hardRequirement(p)?.let {
                            player.message(if (p == player) it else "${p.username} has not completed the Theatre of Blood in Normal Mode.")
                            return true
                        }
                    }
                }
                Raids.start(player, party, mode = mode, enterNow = false)?.let { player.message(it) }
            }

            else -> {
                return false
            }
        }
        return true
    }

    /**
     * TB8: the barrier (32755 "Pass") lets a player into the fight, which starts with the first crossing; once the fight
     * is on nobody passes back out until the room is won. After the fight the barrier lets anyone through both ways.
     */
    private fun pass(player: PlayerRef, loc: LocRef) {
        val run = runOf(player) ?: return
        val state = state(run)
        val inRoom = run.state as? RunState.InRoom
        val inside = towardsArena(run, player, loc)
        if (inRoom != null && state.engaged && !inside) {
            player.message(NO_RETREAT)
            return
        }
        player.teleport(across(player.coord, loc))
        if (inRoom != null && inside && inRoom.room.id in TobRaid.ORDER) {
            state.entered.getOrPut(inRoom.room.id) { LinkedHashSet() } += player.username
            if (!state.engaged) {
                state.engaged = true
                state.fightStartTick = player.tick
            }
        }
    }

    /** The tile on the far side of a barrier [loc] from [from]: two tiles across its line (the barrier is one tile thick). */
    private fun across(from: CoordGrid, loc: LocRef): CoordGrid {
        val vertical = loc.angle % 2 == 1
        return if (vertical) {
            CoordGrid(if (from.x < loc.coord.x) loc.coord.x + 1 else loc.coord.x - 1, from.z, from.level)
        } else {
            CoordGrid(from.x, if (from.z < loc.coord.z) loc.coord.z + 1 else loc.coord.z - 1, from.level)
        }
    }

    /** Whether crossing [loc] takes [player] into the current room's arena (the side the boss is on). */
    private fun towardsArena(run: RaidRun, player: PlayerRef, loc: LocRef): Boolean {
        val room = (run.state as? RunState.InRoom)?.room ?: return false
        val wait = run.at(room.waitAt ?: room.entry)
        val to = across(player.coord, loc)
        // The purgatory / antechamber tile lies outside: moving away from it is moving in.
        val before = kotlin.math.abs(player.coord.x - wait.x) + kotlin.math.abs(player.coord.z - wait.z)
        val after = kotlin.math.abs(to.x - wait.x) + kotlin.math.abs(to.z - wait.z)
        return after > before
    }

    /** LR18: Verzik's MVP points are awarded per phase, so her damage is kept per phase form. */
    private fun verzikDamage(e: HitEvent) {
        val npc = e.target as? NpcRef ?: return
        val p = e.source as? PlayerRef ?: return
        val phase = TobBosses.verzikPhase(npc.type)
        if (phase < 0 || e.damage <= 0) return
        val run = runOfNpc(npc) ?: return
        state(run).verzikPhaseDamage[phase].merge(p.username, e.damage, Int::plus)
    }

    /** A boss died: the room is won once every NPC it needs is gone (TobBosses), and Verzik's phases are counted. */
    private fun bossDied(npc: NpcRef) {
        val run = runOfNpc(npc) ?: return
        val room = (run.state as? RunState.InRoom)?.room?.id ?: return
        val needed = TobBosses.required(room)
        if (npc.type !in needed) return
        val inst = run.instance ?: return
        val others = npc.npcsWithin(npc.coord, ROOM_SCAN).any { it != npc && it.type in needed && it.hitpoints > 0 && it.coord in inst }
        if (others) return
        roomWon(npc, run, room)
        Raids.roomCleared(npc, run)
    }

    /**
     * TB9: a boss's death restores every member's HP, prayer, run energy and special energy (Entry: HP and prayer); the
     * room's MVP points go to its top damage dealer (LR18); the fight's time is added to the challenge time. Midway
     * supplies open after Bloat and Sotetseg (TB22).
     */
    private fun roomWon(world: NpcRef, run: RaidRun, room: String) {
        val state = state(run)
        val ticks = if (state.fightStartTick >= 0) world.tick - state.fightStartTick else 0L
        state.roomTicks[room] = ticks
        state.challengeTicks += ticks
        state.engaged = false
        state.fightStartTick = -1
        if (room == "verzik") {
            for ((k, damage) in state.verzikPhaseDamage.withIndex()) award(state, damage, TobRewards.MVP_POINTS["verzik_p${k + 1}"] ?: 0)
        } else {
            award(state, run.members.mapValues { it.value.roomDamage.toInt() }, TobRewards.MVP_POINTS[room] ?: 0)
        }
        for (p in run.presentPlayers(world)) restore(p, run.scaling.mode)
        if (room == "bloat" || room == "sotetseg") TobSupplies.open(run, world, room)
        if (room == "verzik") {
            state.verzikDeadTick = world.tick
            // LR16: rooms fought and MVP points (TobLoot takes 4 a death off); TB29: Hard inside the target time.
            for (name in run.members.keys) Raids.addPoints(run, name, TobLoot.points(state, name).toDouble())
            if (world.tick - run.startedTick <=
                TobLoot.targetTicks(run.scaling.partySize)
            ) {
                run.extraSettings[TobLoot.TARGET_TIME_SETTING] = 1
            }
            TobVault.prepare(run, world)
        }
    }

    private fun award(state: TobState, damage: Map<String, Int>, points: Int) {
        val top = damage.filter { it.value > 0 }.maxByOrNull { it.value }?.key ?: return
        state.mvp.merge(top, points, Int::plus)
    }

    private fun restore(p: PlayerRef, mode: String) {
        p.hitpoints = maxOf(p.hitpoints, p.maxHitpoints)
        p.setLevel(Stats.PRAYER, maxOf(p.level(Stats.PRAYER), p.baseLevel(Stats.PRAYER)))
        if (mode == TobRaid.ENTRY) return
        p.runEnergy = FULL_RUN
        p.setVarp(Varps.SA_ENERGY, FULL_SPEC)
    }

    /** Between rooms, a member walking into the finished room's exit takes the party on (OPEN-TB7). */
    private fun worldTick(world: WorldRef) {
        for (run in Raids.of(world).activeRuns) {
            if (run.raid.id != TobRaid.ID) continue
            val between = run.state as? RunState.BetweenRooms ?: continue
            val next =
                run.raid.slots
                    .getOrNull(between.slot)
                    ?.firstOrNull() ?: continue
            val finished = TobRaid.ORDER.getOrNull(between.slot - 1) ?: continue
            val exit = EXITS[finished] ?: continue
            val any =
                run.members.values
                    .filter { it.inside && it.online }
                    .mapNotNull { world.onlinePlayer(it.name) }
                    .firstOrNull { p -> run.instance?.let { exit.contains(it.toTemplate(p.coord)) } == true }
                    ?: continue
            Raids.advance(any, run, next)
        }
    }

    /** The throne door (32738 "Treasure room") after Verzik (LR14). */
    private fun toTreasure(player: PlayerRef) {
        val run = runOf(player) ?: return
        if (run.state !is RunState.BetweenRooms) return
        Raids.advance(player, run, "treasure")
    }

    /** A box of template tiles on one level. */
    private class Box(val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int, val level: Int = 0) {
        fun contains(t: CoordGrid?): Boolean = t != null && t.level == level && t.x in minX..maxX && t.z in minZ..maxZ
    }

    internal companion object {
        fun state(run: RaidRun): TobState = run.content as? TobState ?: TobState().also { run.content = it }

        fun runOf(player: PlayerRef): RaidRun? {
            val run = Raids.of(player).runOf(player.username) ?: return null
            return run.takeIf { it.raid.id == TobRaid.ID && it.member(player.username)?.inside == true }
        }

        fun runOfNpc(npc: NpcRef): RaidRun? {
            val id = RaidRun.npcRun(npc) ?: return null
            return Raids.of(npc).run(id)?.takeIf { it.raid.id == TobRaid.ID }
        }

        /** RoomDef.onStart: a fresh room has no fight yet; its NPCs spawn. */
        fun roomStarted(run: RaidRun, any: EntityRef, room: String) {
            val state = state(run)
            state.engaged = false
            state.fightStartTick = -1
            TobBosses.spawn(run, any, room)
        }

        /** The treasure room (LR14): the chests appear. */
        fun treasure(run: RaidRun, any: EntityRef) {
            TobVault.start(run, any)
        }

        /** Template exit boxes (client-contract 5 corridors; OPEN-TB7). */
        private val EXITS: Map<String, Box> =
            mapOf(
                "maiden" to Box(3213, 3225, 4456, 4460),
                "bloat" to Box(3320, 3322, 4433, 4462),
                "nylocas" to Box(3292, 3306, 4281, 4286),
                "sotetseg" to Box(3273, 3286, 4332, 4334),
                "xarpus" to Box(3164, 3176, 4374, 4377, level = 1),
            )

        private const val OP1 = 1
        private const val ROOM_SCAN = 40
        private const val FULL_RUN = 10_000
        private const val FULL_SPEC = 1000
        private const val LEADER_FIRST = "Your party leader must enter the Theatre first."

        /** OPEN-TB7: the line when a fighter tries to leave through the barrier mid-fight is not captured. */
        private const val NO_RETREAT = "You can't leave while the fight is in progress."
    }
}
