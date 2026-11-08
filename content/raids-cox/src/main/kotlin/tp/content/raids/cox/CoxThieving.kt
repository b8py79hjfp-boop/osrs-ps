package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.util.CoordGrid

/**
 * The thieving puzzle (docs/mechanics/raids/cox/puzzles.md TH1-TH8): the corrupted scavenger blocks the way until it has
 * eaten enough cavern grubs from the trough; grubs come from the chests (one attempt a tick, 39-61% by level, +21 with a
 * lockpick or hairclip); one chest holds psykk bats, three are poison chests; 115 points per grub deposited up to the cap.
 */
internal object CoxThieving {
    /** Per cell: the room's state. */
    private class Room(val cell: CoxCell, val needed: Int, val maxGrubs: Int) {
        var trough = 0
        var eaten = 0
        var lastFed = 0L
        var grubsCounted = 0
        var scavenger: NpcRef? = null
        var bats: CoordGrid? = null
        val poison = HashSet<CoordGrid>()
        val opened = HashSet<CoordGrid>()
    }

    private fun rooms(run: RaidRun): HashMap<Int, Room> {
        val st = requireNotNull(CoxFlow.state(run))
        @Suppress("UNCHECKED_CAST")
        return st.puzzles.getOrPut(KEY) { HashMap<Int, Room>() } as HashMap<Int, Room>
    }

    fun register(scope: ContentScope) {
        CoxFlow.scripts[CoxKind.THIEVING] = CoxRoomScript { run, cell, any -> start(run, cell, any) }
        with(scope) {
            onOpLoc(Locs.RAIDS_THIEVINGCHEST_CLOSED, OP1) { loc -> open(player, loc) }
            onOpLoc(Locs.RAIDS_THIEVINGCHEST_FOODTROUGH_EMPTY, OP1) { deposit(player) }
            onOpLoc(Locs.RAIDS_THIEVINGCHEST_FOODTROUGH_FULL, OP1) { deposit(player) }
            onOpLoc(Locs.RAIDS_THIEVINGCHEST_CREATUREKEEPER, OP_SEARCH) { journal(player) }
        }
    }

    private fun start(run: RaidRun, cell: CoxCell, any: EntityRef) {
        val n = run.scaling.partySize
        // TH2: at least 16 per player, 30 solo (OPEN-PZ20: the linear scale on the team's Thieving up to its cap).
        val needed = if (n <= 1) SOLO_GRUBS else GRUBS_PER_PLAYER * n
        // TH8: maxPoints = max(4500, floor(total Thieving / 6) x 150); maxGrubs = ceil(maxPoints / 115).
        val total = run.scaling.skillSums.getOrElse(Stats.THIEVING) { 0 }
        val maxPoints = maxOf(MIN_MAX_POINTS, total / THIEVING_DIVISOR * POINTS_STEP)
        val room = Room(cell, needed, (maxPoints + GRUB_POINTS - 1) / GRUB_POINTS)
        rooms(run)[cell.index] = room
        val chests = CoxMap.findLocs(any, run, cell, Locs.RAIDS_THIEVINGCHEST_CLOSED).map { it.coord }.toMutableList()
        // TH6 (OPEN-PZ26): which chests are special is per layout; one bat chest and three poison chests drawn per run.
        for (i in chests.indices.reversed()) {
            val j = run.rng.next(i + 1, "cox thieving chest")
            chests[i] = chests[j].also { chests[j] = chests[i] }
        }
        room.bats = chests.firstOrNull()
        room.poison += chests.drop(1).take(POISON_CHESTS)
        val trough =
            CoxMap.findLoc(any, run, cell, Locs.RAIDS_THIEVINGCHEST_FOODTROUGH_EMPTY)?.coord ?: CoxRoomGeo(any, run, cell).centre(1)
        val beast =
            Raids.spawnAt(
                any,
                run,
                Npcs.RAIDS_THIEVINGCHEST_BEAST_ACTIVE,
                CoxMap.freeNear(any, CoordGrid(trough.x + 1, trough.z + 1, trough.level)),
            )
        beast.huntRange = 0
        room.scavenger = beast
        room.lastFed = any.tick
        CoxRooms.placeBlocker(any, run, cell, SCAVENGER_BLOCKER)
        tick(run, room, any)
    }

    /** The scavenger eats one grub a tick from the trough while it has any (OPEN-PZ21: the eating speed). */
    private fun tick(run: RaidRun, room: Room, any: EntityRef) {
        any.queueHit(1, strong = false) {
            val st = CoxFlow.state(run) ?: return@queueHit
            if (st.done[room.cell.index] || run.instance == null) return@queueHit
            val beast = room.scavenger
            if (room.trough > 0 && beast != null) {
                room.trough--
                room.eaten++
                room.lastFed = any.tick
                beast.anim(Seqs.RAIDS_THIEVINGCHESTS_EAT)
                if (room.trough == 0) swapTrough(any, run, room, full = false)
            } else if (any.tick - room.lastFed > HUNGER_DELAY && room.eaten > 0 && any.tick % HUNGER_EVERY == 0L) {
                // TH3: unfed for 60 s after it stopped eating, its hunger comes back (OPEN-PZ21: one grub per 10 ticks).
                room.eaten--
            }
            if (room.eaten >= room.needed) {
                finish(run, room, any)
                return@queueHit
            }
            tick(run, room, any)
        }
    }

    /** TH4-TH6: open a chest: success a tick at a time, then grubs, bats or poison; the chest stays open (locked). */
    private suspend fun tp.plugin.PlayerScript.open(player: PlayerRef, loc: LocRef) {
        val run = CoxFlow.runOf(player) ?: return
        val st = CoxFlow.state(run) ?: return
        val cell = CoxMap.cellAt(st.plan, run, loc.coord) ?: return
        val room = rooms(run)[cell.index] ?: return
        if (loc.coord in room.opened) return
        while (true) {
            player.anim(OPEN_ANIM)
            val level = player.level(Stats.THIEVING)
            var chance = CHANCE_MIN + (CHANCE_MAX - CHANCE_MIN) * (level.coerceIn(1, Stats.MAX_LEVEL) - 1) / (Stats.MAX_LEVEL - 1)
            if (hasPick(player)) chance += LOCKPICK_BONUS
            if (player.random(PERCENT) < chance) break
            delay(1)
            if (loc.coord in room.opened) return
        }
        room.opened += loc.coord
        val level = player.level(Stats.THIEVING)
        when (loc.coord) {
            room.bats -> {
                // TH6: one psykk bat per player in the raid, 5-25; they count as food cooked by the finder.
                val n = run.scaling.partySize.coerceIn(MIN_BATS, MAX_BATS)
                if (player.transaction("cox_thieving_bats") { add(Invs.INV, Objs.RAIDS_BAT6_COOKED, n) }) {
                    CoxFood.credit(run, player, Objs.RAIDS_BAT6_COOKED, player.username, n)
                }
                swapChest(player, loc, Locs.RAIDS_THIEVINGCHEST_EGGS_HATCHED)
            }

            in room.poison -> {
                // TH6: 1-3 to anyone next to the chest, no poison status.
                for (p in run.presentPlayers(player)) {
                    if (maxOf(kotlin.math.abs(p.coord.x - loc.coord.x), kotlin.math.abs(p.coord.z - loc.coord.z)) <= 1) {
                        player.events.publish(tp.plugin.DamageRequest(p, null, POISON_MIN + player.random(POISON_MAX - POISON_MIN + 1)))
                    }
                }
                swapChest(player, loc, Locs.RAIDS_THIEVINGCHEST_OPEN)
            }

            else -> {
                // TH5: 0..floor(Thieving / 25), at least 1 (2 at 95+); cavern grubs stack to 28.
                val roll = player.random(level / GRUB_DIVISOR + 1)
                val grubs = maxOf(roll, if (level >= TWO_GRUB_LEVEL) 2 else 1)
                val have = player.inv(Invs.INV).total(Objs.RAIDS_THIEVINGCHEST_GRUBS).toInt()
                val add = minOf(grubs, GRUB_CAP - have)
                if (add > 0) player.transaction("cox_thieving_grubs") { add(Invs.INV, Objs.RAIDS_THIEVINGCHEST_GRUBS, add) }
                swapChest(player, loc, Locs.RAIDS_THIEVINGCHEST_EGGS_HATCHED)
            }
        }
    }

    private fun swapChest(any: EntityRef, loc: LocRef, to: Int) {
        any.addLoc(to, loc.coord, loc.shape, loc.angle)
    }

    /** TH1 / TH8: grubs into the trough; 115 points each up to the cap. */
    private fun deposit(player: PlayerRef) {
        val run = CoxFlow.runOf(player) ?: return
        val st = CoxFlow.state(run) ?: return
        val cell = CoxMap.cellAt(st.plan, run, player.coord) ?: return
        val room = rooms(run)[cell.index] ?: return
        val have = player.inv(Invs.INV).total(Objs.RAIDS_THIEVINGCHEST_GRUBS).toInt()
        if (have <= 0) {
            player.message(NO_GRUBS)
            return
        }
        if (!player.transaction("cox_thieving_deposit") { remove(Invs.INV, Objs.RAIDS_THIEVINGCHEST_GRUBS, have) }) return
        val counted = minOf(have, room.maxGrubs - room.grubsCounted).coerceAtLeast(0)
        room.grubsCounted += counted
        CoxPoints.award(run, player.username, counted * GRUB_POINTS.toDouble())
        room.trough += have
        swapTrough(player, run, room, full = true)
    }

    private fun swapTrough(any: EntityRef, run: RaidRun, room: Room, full: Boolean) {
        val from = if (full) Locs.RAIDS_THIEVINGCHEST_FOODTROUGH_EMPTY else Locs.RAIDS_THIEVINGCHEST_FOODTROUGH_FULL
        val loc = CoxMap.findLoc(any, run, room.cell, from) ?: return
        any.addLoc(
            if (full) Locs.RAIDS_THIEVINGCHEST_FOODTROUGH_FULL else Locs.RAIDS_THIEVINGCHEST_FOODTROUGH_EMPTY,
            loc.coord,
            loc.shape,
            loc.angle,
        )
    }

    /** TH1 / TH7: full, it sleeps on the hay; every player gets the completion items. */
    private fun finish(run: RaidRun, room: Room, any: EntityRef) {
        room.scavenger?.let {
            it.anim(Seqs.RAIDS_THIEVINGCHESTS_TIRED)
            it.transform(Npcs.RAIDS_THIEVINGCHEST_BEAST_SLEEPING, restoreStats = false)
        }
        CoxRooms.removeBlocker(any, run, room.cell)
        for (p in run.presentPlayers(any)) {
            // TH7 (Strat solo table; OPEN-PZ13 the party scaling): 5 endarkened juice, 5 stinkhorns, 4 cicely.
            p.transaction("cox_thieving_reward") {
                add(Invs.INV, Objs.RAIDS_ENDARKENED_JUICE, JUICE)
                add(Invs.INV, Objs.RAIDS_STINKHORN_MUSHROOM, STINKHORNS)
                add(Invs.INV, Objs.RAIDS_CICELY, CICELY)
            }
        }
        CoxFlow.roomDone(run, room.cell)
    }

    /** TH7: the creature keeper's journal, once. */
    private fun journal(player: PlayerRef) {
        if (player.inv(Invs.INV).total(Objs.RAIDS_THIEVINGCHEST_BOOK) > 0) return
        player.transaction("cox_thieving_journal") { add(Invs.INV, Objs.RAIDS_THIEVINGCHEST_BOOK) }
    }

    private fun hasPick(p: PlayerRef): Boolean {
        val inv = p.inv(Invs.INV)
        return (0 until inv.size).any {
            val n = p.objName(inv.obj(it)).lowercase()
            n == "lockpick" || n == "hairclip"
        }
    }

    private const val KEY = "thieving"
    private const val OP1 = 1
    private const val OP_SEARCH = 5
    private const val CENTREPIECE = 10

    /** Loc 26209: the shamans' / thieving / guardians' blocker (runelite-timings). */
    private const val SCAVENGER_BLOCKER = 26209
    private const val SOLO_GRUBS = 30
    private const val GRUBS_PER_PLAYER = 16
    private const val MIN_MAX_POINTS = 4500
    private const val THIEVING_DIVISOR = 6
    private const val POINTS_STEP = 150
    private const val GRUB_POINTS = 115
    private const val POISON_CHESTS = 3
    private const val POISON_MIN = 1
    private const val POISON_MAX = 3
    private const val MIN_BATS = 5
    private const val MAX_BATS = 25

    /** TH4 (Mod Ash, 8 Jan 2024): about 39% at 1 to 61% at 99 (OPEN-PZ22: linear), +21 with a lockpick or hairclip. */
    private const val CHANCE_MIN = 39
    private const val CHANCE_MAX = 61
    private const val LOCKPICK_BONUS = 21
    private const val PERCENT = 100
    private const val GRUB_DIVISOR = 25
    private const val TWO_GRUB_LEVEL = 95
    private const val GRUB_CAP = 28

    /** TH3: 60 s (100 ticks) unfed before its hunger comes back. */
    private const val HUNGER_DELAY = 100L
    private const val HUNGER_EVERY = 10L
    private const val JUICE = 5
    private const val STINKHORNS = 5
    private const val CICELY = 4

    /** The generic pick-lock / search animation. */
    private val OPEN_ANIM = Seqs.HUMAN_PICKUPTABLE
    private const val NO_GRUBS = "You have no cavern grubs to deposit."
}
