package tp.content.raids.toa

import tp.content.raids.RaidRun
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.HitEvent
import tp.plugin.HitStyle
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Path of Het (puzzles.md HE1-HE12): the caster statue fires a beam every [ToaConfig.hetBeamPeriodTicks]; mirrors
 * (pick up, place, rotate, push, clean) and barriers (Break) steer it into the shielded statue, which weakens Het's Seal
 * for 15 ticks, and the Seal is mined to death. The room is the 21 x 21 arena of the template (see [ORIGIN]); a wipe
 * redrafts the puzzle (Strat). TODO hooks for the main session (orb of darkness AI): [orbHit] and [dirtyMirrorAt].
 */
internal object ToaHet {
    private const val ROOM = "het"
    private const val SIZE = 21
    private const val CENTREPIECE = 10

    // cell kinds
    private const val NONE = 0
    private const val MIRROR = 1
    private const val DIRTY = 2
    private const val WALL = 3
    private const val WORN = 4

    private class Cell(var kind: Int, var angle: Int, val movable: Boolean)

    private class State(val seal: NpcRef?) {
        val cells = arrayOfNulls<Cell>(SIZE * SIZE)
        var nextBeam = 0L
        var weakUntil = -1L
        var damageAt = -1L
        val hit = ArrayList<String>()
        var done = false
    }

    private val store = RunStore<State>("toa_het")

    fun register(scope: ContentScope) {
        with(scope) {
            onOpLoc(Locs.TOA_HET_MIRROR, 1) { loc -> pickUp(player, loc) }
            onOpLoc(Locs.TOA_HET_MIRROR, 2) { loc -> rotate(player, loc, 1) }
            onOpLoc(Locs.TOA_HET_MIRROR, 3) { loc -> rotate(player, loc, -1) }
            onOpLoc(Locs.TOA_HET_MIRROR, 4) { loc -> push(player, loc) }
            onOpLoc(Locs.TOA_HET_MIRROR_DIRTY, 1) { loc -> clean(player, loc) }
            onOpLoc(Locs.TOA_HET_BARRIER_DESTRUCTABLE_INTERNAL, 1) { loc -> breakWall(player, loc) }
            onOpLoc(Locs.TOA_HET_BARRIER_DESTRUCTABLE_END, 1) { loc -> breakWall(player, loc) }
            onItemOp(Objs.TOA_HET_MIRROR, "Place") { place(this) }
            onOpNpc(Npcs.TOA_HET_GOAL_VULNERABLE, 1) { seal -> mine(player, seal) }
            onOpNpc(Npcs.TOA_HET_GOAL, 1) { player.message("The seal is protected by a shield of light.") }
            for (statue in STATUES) {
                onOpLoc(statue, 1) { takePickaxe(player) }
                onOpLoc(statue, 2) { depositPickaxe(player) }
            }
        }
    }

    // ---- geometry

    private fun base(run: RaidRun): CoordGrid = run.at(ORIGIN)

    private fun inst(run: RaidRun, x: Int, y: Int): CoordGrid = run.at(CoordGrid(ORIGIN.x + x, ORIGIN.z + y))

    private fun idx(x: Int, y: Int) = y * SIZE + x

    private fun stopsBeam(cell: Cell) = cell.kind == WALL || cell.kind == WORN || cell.kind == DIRTY

    /** A tile a mirror may stand on: inside the arena, empty, not the statue or the Seal. */
    private fun free(s: State, x: Int, y: Int): Boolean = inGrid(x, y) && s.cells[idx(x, y)] == null && !isStatue(x, y) && !isSeal(x, y)

    private fun inGrid(x: Int, y: Int) = x in 0 until SIZE && y in 0 until SIZE

    private fun isStatue(x: Int, y: Int) = x in STATUE_X && y in STATUE_Y

    private fun isSeal(x: Int, y: Int) = x in SEAL_X && y in SEAL_Y

    // ---- setup (HE1-HE3, HE10)

    fun start(run: RaidRun, any: EntityRef) {
        // HE10: a wipe resets the statue progress and drafts a new puzzle: take the old mirrors and walls away.
        store[any, run]?.let { old ->
            for (y in 0 until SIZE) {
                for (x in 0 until SIZE) if (old.cells[idx(x, y)] != null) any.deleteLoc(inst(run, x, y), CENTREPIECE)
            }
        }
        val seal = ToaRooms.spawn(any, run, ROOM, Npcs.TOA_HET_GOAL, SEAL_AT)
        val hp = SEAL_HP[run.scaling.partySize.coerceIn(1, SEAL_HP.size) - 1]
        val levels = IntArray(STATS) { seal.baseLevel(it) }
        levels[HITPOINTS] = hp
        seal.overrideStats(levels)
        seal.hitpoints = hp
        val s = State(seal)
        s.nextBeam = any.tick + FIRST_BEAM
        store[any, run] = s
        any.addLoc(Locs.TOA_HET_TARGET, run.at(CoordGrid(ORIGIN.x + STATUE_X.first, ORIGIN.z + STATUE_Y.first)), CENTREPIECE, 0)
        any.addLoc(Locs.TOA_HET_SOURCE, run.at(CASTER_AT), CENTREPIECE, 0)
        draft(run, any, s)
    }

    /** HE3 / OPEN-PZ25: one of LD's nine routes; the first three mirrors are loose, a fourth is fixed (dirty in groups). */
    private fun draft(run: RaidRun, any: EntityRef, s: State) {
        val route = ROUTES[run.rng.next(ROUTES.size, "toa het route")]
        var dir = EAST
        val path = ArrayList<IntArray>()
        for ((i, m) in route.withIndex()) {
            val out = if (i + 1 < route.size) dirTo(m, route[i + 1]) else dirToStatue(m)
            val type = typeFor(dir, out)
            if (i < LOOSE) {
                put(run, any, s, LOOSE_AT[i].first, LOOSE_AT[i].second, Cell(MIRROR, type, movable = true))
            } else {
                val kind = if (run.scaling.partySize >= 2) DIRTY else MIRROR
                put(run, any, s, m[0], m[1], Cell(kind, type, movable = false))
            }
            dir = out
        }
        // The beam line: every tile between the start and the statue along the route.
        var cx = BEAM_X
        var cy = BEAM_Y
        var d = EAST
        val line = ArrayList<IntArray>()
        for ((i, m) in route.withIndex()) {
            while (cx != m[0] || cy != m[1]) {
                cx += DX[d]
                cy += DY[d]
                if (cx != m[0] || cy != m[1]) line += intArrayOf(cx, cy)
            }
            d = if (i + 1 < route.size) dirTo(m, route[i + 1]) else dirToStatue(m)
        }
        path += line
        // OPEN-PZ25: worn walls (Break) stand on the beam line.
        repeat(minOf(ToaConfig.hetWornWalls, path.size)) {
            val t = path.removeAt(run.rng.next(path.size, "toa het wall"))
            put(run, any, s, t[0], t[1], Cell(WORN, 0, movable = false))
        }
    }

    private fun put(run: RaidRun, any: EntityRef, s: State, x: Int, y: Int, cell: Cell) {
        s.cells[idx(x, y)] = cell
        show(run, any, x, y, cell)
    }

    private fun show(run: RaidRun, any: EntityRef, x: Int, y: Int, cell: Cell) {
        val at = inst(run, x, y)
        when (cell.kind) {
            MIRROR -> any.addLoc(if (cell.movable) Locs.TOA_HET_MIRROR else Locs.TOA_HET_MIRROR_FIXED, at, CENTREPIECE, cell.angle)
            DIRTY -> any.addLoc(Locs.TOA_HET_MIRROR_DIRTY, at, CENTREPIECE, cell.angle)
            WALL -> any.addLoc(Locs.TOA_HET_BARRIER_INTERNAL, at, CENTREPIECE, 0)
            WORN -> any.addLoc(Locs.TOA_HET_BARRIER_DESTRUCTABLE_INTERNAL, at, CENTREPIECE, 0)
            else -> any.deleteLoc(at, CENTREPIECE)
        }
    }

    /** Direction index 0 east, 1 north, 2 west, 3 south from mirror [a] to mirror [b] (same row or column). */
    private fun dirTo(a: IntArray, b: IntArray): Int =
        when {
            a[0] == b[0] -> if (b[1] > a[1]) NORTH else SOUTH
            else -> if (b[0] > a[0]) EAST else WEST
        }

    private fun dirToStatue(m: IntArray): Int =
        when {
            m[0] in STATUE_X -> if (m[1] > STATUE_Y.last) SOUTH else NORTH
            else -> if (m[0] > STATUE_X.last) WEST else EAST
        }

    /** The mirror angle (even '/', odd '\') turning [from] into [to]. */
    private fun typeFor(from: Int, to: Int): Int = if (SLASH[from] == to) 0 else 1

    private fun reflect(angle: Int, dir: Int): Int = if (angle % 2 == 0) SLASH[dir] else BACKSLASH[dir]

    // ---- tick: HE4 beam, HE7 window

    fun tick(run: RaidRun, any: EntityRef) {
        val s = store[any, run] ?: return
        if (s.done) return
        val now = any.tick
        if (now == s.damageAt) {
            for (name in s.hit) any.onlinePlayer(name)?.let { ToaPuzzles.hit(it, ToaConfig.hetBeamDamage) }
            s.hit.clear()
            s.damageAt = -1
        }
        val seal = s.seal ?: return
        if (s.weakUntil >= 0 && now >= s.weakUntil) {
            seal.transform(Npcs.TOA_HET_GOAL, restoreStats = false)
            s.weakUntil = -1
            s.nextBeam = now + FIRST_BEAM
        }
        if (s.weakUntil < 0 && now >= s.nextBeam) fire(run, any, s, now)
    }

    @Suppress("CyclomaticComplexMethod") // one beam step: bounds, statue, Seal, cell, hit players
    private fun fire(run: RaidRun, any: EntityRef, s: State, now: Long) {
        s.nextBeam = now + ToaConfig.hetBeamPeriodTicks
        var x = BEAM_X
        var y = BEAM_Y
        var dir = EAST
        val players = ToaPuzzles.alive(run, any)
        var weakened = false
        for (step in 0 until MAX_STEPS) {
            x += DX[dir]
            y += DY[dir]
            if (!inGrid(x, y) || isSeal(x, y)) break
            val at = inst(run, x, y)
            if (isStatue(x, y)) {
                weakened = true
                any.spotAnimAt(at, SpotAnims.FX_BEAM_IMPACT01)
                break
            }
            val cell = s.cells[idx(x, y)]
            if (cell != null && stopsBeam(cell)) {
                any.spotAnimAt(at, SpotAnims.FX_BEAM_IMPACT01)
                break
            }
            if (cell == null && !any.isWalkable(at)) break
            any.spotAnimAt(at, if (dir == EAST || dir == WEST) SpotAnims.FX_BEAM_ATTACK03 else SpotAnims.FX_BEAM_ATTACK01)
            for (p in players) if (p.coord == at && p.username.lowercase() !in s.hit) s.hit += p.username.lowercase()
            if (cell != null && cell.kind == MIRROR) dir = reflect(cell.angle, dir)
        }
        // HE4: the damage lands one tick after the graphic.
        if (s.hit.isNotEmpty()) s.damageAt = now + 1
        if (weakened) weaken(s, now)
    }

    /** HE7: the Seal is exposed for 15 ticks, and no beam fires meanwhile (LD). */
    private fun weaken(s: State, now: Long) {
        s.seal?.transform(Npcs.TOA_HET_GOAL_VULNERABLE, restoreStats = false)
        s.weakUntil = now + WEAK_TICKS
    }

    // ---- mirrors (HE3)

    private fun cellAt(run: RaidRun, s: State, loc: LocRef): Triple<Int, Int, Cell>? {
        val b = base(run)
        val x = loc.coord.x - b.x
        val y = loc.coord.z - b.z
        if (!inGrid(x, y) || loc.coord.level != b.level) return null
        val cell = s.cells[idx(x, y)] ?: return null
        return Triple(x, y, cell)
    }

    private fun pickUp(player: PlayerRef, loc: LocRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        val (x, y, cell) = cellAt(run, s, loc) ?: return
        if (!cell.movable || cell.kind != MIRROR) return
        if (ToaPuzzles.give(player, Objs.TOA_HET_MIRROR, 1, "toa het mirror")) {
            s.cells[idx(x, y)] = null
            player.deleteLoc(loc.coord, CENTREPIECE)
        }
    }

    /** HE3: the mirror goes down on the tile the raider stands on, if that tile is free floor. */
    private fun place(player: PlayerRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        val b = base(run)
        val x = player.coord.x - b.x
        val y = player.coord.z - b.z
        if (!free(s, x, y)) {
            player.message("You can't place a mirror there.")
            return
        }
        if (!ToaPuzzles.take(player, Objs.TOA_HET_MIRROR, 1, "toa het mirror placed")) return
        put(run, player, s, x, y, Cell(MIRROR, 0, movable = true))
    }

    private fun rotate(player: PlayerRef, loc: LocRef, by: Int) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        val (x, y, cell) = cellAt(run, s, loc) ?: return
        if (!cell.movable) return
        cell.angle = (cell.angle + by).mod(ANGLES)
        show(run, player, x, y, cell)
    }

    /** HE3: a push moves the mirror one tile away from the raider; into a wall it stays (24 Aug 2022). */
    private fun push(player: PlayerRef, loc: LocRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        val (x, y, cell) = cellAt(run, s, loc) ?: return
        if (!cell.movable) return
        val dx = Integer.signum(loc.coord.x - player.coord.x)
        val dz = Integer.signum(loc.coord.z - player.coord.z)
        val horizontal = kotlin.math.abs(loc.coord.x - player.coord.x) >= kotlin.math.abs(loc.coord.z - player.coord.z)
        val nx = x + if (horizontal) dx else 0
        val ny = y + if (horizontal) 0 else dz
        if (!free(s, nx, ny) || !player.isWalkable(inst(run, nx, ny))) return
        s.cells[idx(x, y)] = null
        player.deleteLoc(loc.coord, CENTREPIECE)
        put(run, player, s, nx, ny, cell)
    }

    private fun clean(player: PlayerRef, loc: LocRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        val (x, y, cell) = cellAt(run, s, loc) ?: return
        if (cell.kind != DIRTY) return
        cell.kind = MIRROR
        show(run, player, x, y, cell)
    }

    /** HE3 / OPEN-PZ25: a mirror hit by an orb turns dirty (hook for the orb AI). */
    fun dirtyMirrorAt(run: RaidRun, any: EntityRef, at: CoordGrid) {
        val s = store[any, run] ?: return
        val b = base(run)
        val x = at.x - b.x
        val y = at.z - b.z
        if (!inGrid(x, y)) return
        val cell = s.cells[idx(x, y)] ?: return
        if (cell.kind != MIRROR) return
        cell.kind = DIRTY
        show(run, any, x, y, cell)
    }

    /** HE5 hook for the orb of darkness AI: [player] was hit on this tile (MT: graphic 379, damage per OPEN-PZ27). */
    fun orbHit(player: PlayerRef) {
        player.spotAnim(SpotAnims.SHADOW_RUSH_IMPACT)
        ToaPuzzles.hit(player, ToaConfig.hetOrbDamage)
    }

    /** HE3: "Break" with a pickaxe; better pickaxes break faster (Strat). */
    private suspend fun PlayerScript.breakWall(player: PlayerRef, loc: LocRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        val (x, y, cell) = cellAt(run, s, loc) ?: return
        if (cell.kind != WORN) return
        val tier = pickaxeTier(player)
        if (tier == NO_PICKAXE) {
            player.message("You need a pickaxe to break the barrier.")
            return
        }
        player.anim(miningAnim(tier))
        delay(ToaConfig.hetBreakTicks - tier.coerceAtMost(ToaConfig.hetBreakTicks - 1))
        if (s.cells[idx(x, y)] !== cell) return
        s.cells[idx(x, y)] = null
        player.addLoc(Locs.TOA_HET_BARRIER_DESTROYED, loc.coord, CENTREPIECE, 0)
        s.cells[idx(x, y)] = null
    }

    // ---- HE6-HE9 the Seal

    private suspend fun PlayerScript.mine(player: PlayerRef, seal: NpcRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        val tier = pickaxeTier(player)
        if (tier == NO_PICKAXE) {
            player.message("You need a pickaxe to destroy the seal.")
            return
        }
        while (!s.done && seal.isInWorld && seal.type == Npcs.TOA_HET_GOAL_VULNERABLE) {
            player.anim(miningAnim(tier))
            val dealt = sealDamage(player, tier).coerceAtMost(seal.hitpoints)
            seal.hitpoints -= dealt
            seal.hitsplat(player, dealt, HITMARK_LIT, HITMARK_LIT, HITMARK_TINT)
            player.addXp(Stats.MINING, SEAL_XP)
            player.events.publish(HitEvent(seal, player, dealt, HitStyle.TYPELESS))
            if (seal.hitpoints <= 0) {
                destroyed(run, player, s, seal)
                return
            }
            delay(ToaConfig.hetSealHitIntervalTicks)
        }
    }

    /** HE8: base 8 / 15 / 17 by visible Mining (<85 / 85-98 / 99+), +2 dragon or better, -3 below rune, then +0..2. */
    private fun sealDamage(player: PlayerRef, tier: Int): Int {
        val level = player.level(Stats.MINING)
        var base =
            if (level >= LEVEL_TOP) {
                BASE_TOP
            } else if (level >= LEVEL_MID) {
                BASE_MID
            } else {
                BASE_LOW
            }
        if (tier >= TIER_DRAGON) {
            base += DRAGON_BONUS
        } else if (tier < TIER_RUNE) {
            base -= LOW_PENALTY
        }
        return base + player.random(ROLL_SPREAD)
    }

    private fun destroyed(run: RaidRun, any: EntityRef, s: State, seal: NpcRef) {
        s.done = true
        val at = seal.coord
        seal.despawn()
        any.addLoc(Locs.TOA_HET_GOAL_DEAD, at, CENTREPIECE, 0)
        ToaFlow.roomDone(any, run, ROOM)
    }

    /** OPEN-PZ29: tier 0 none, 1 below rune, 2 rune, 3 dragon or better (gilded, infernal, trailblazer, Echo count as dragon). */
    private fun pickaxeTier(p: PlayerRef): Int {
        var best = NO_PICKAXE
        for (inv in intArrayOf(Invs.INV, Invs.WORN)) {
            val v = p.inv(inv)
            for (slot in 0 until v.size) {
                val obj = v.obj(slot)
                if (obj < 0) continue
                val name = p.objName(obj).lowercase()
                if (!name.endsWith("pickaxe") && !name.contains("pickaxe")) continue
                val t =
                    when {
                        name.contains("rune") -> TIER_RUNE

                        name.contains("bronze") || name.contains("iron") || name.contains("steel") || name.contains("black") ||
                            name.contains("mithril") || name.contains("adamant") -> TIER_LOW

                        else -> TIER_DRAGON
                    }
                if (t > best) best = t
            }
        }
        return best
    }

    private fun miningAnim(tier: Int): Int =
        when {
            tier >= TIER_DRAGON -> Seqs.HUMAN_MINING_HET_DRAGON_PICKAXE
            tier == TIER_RUNE -> Seqs.HUMAN_MINING_HET_RUNE_PICKAXE
            else -> Seqs.HUMAN_MINING_HET_BRONZE_PICKAXE
        }

    // ---- HE2 pickaxe statues

    private fun takePickaxe(player: PlayerRef) {
        val stored = player.varbit(Varbits.TOA_PICKAXE_STORED)
        val obj = if (stored == 0) Objs.BRONZE_PICKAXE else STORED_PICKAXES[stored] ?: return
        if (pickaxeTier(player) != NO_PICKAXE && stored == 0) {
            player.message("You already carry a pickaxe.")
            return
        }
        if (ToaPuzzles.give(player, obj, 1, "toa het statue pickaxe") && stored != 0) player.setVarbit(Varbits.TOA_PICKAXE_STORED, 0)
    }

    private fun depositPickaxe(player: PlayerRef) {
        if (player.varbit(Varbits.TOA_PICKAXE_STORED) != 0) {
            player.message("The statue already holds a pickaxe.")
            return
        }
        val inv = player.inv(Invs.INV)
        for (slot in 0 until inv.size) {
            val value = STORED_PICKAXES.entries.firstOrNull { it.value == inv.obj(slot) }?.key ?: continue
            if (player.transaction("toa het statue deposit") { removeAt(Invs.INV, slot, inv.obj(slot), 1) }) {
                player.setVarbit(Varbits.TOA_PICKAXE_STORED, value)
            }
            return
        }
        player.message("You have no pickaxe the statue can hold.")
    }

    // ---- data

    /** Data: the arena's south-west tile: the 21 x 21 disc of the Het square (cache map; best fit for LD's nine routes). */
    private val ORIGIN = CoordGrid(3670, 5270)

    /** Data: the Seal's south-west tile, the arena centre (3 x 3 blocked tiles in the map). */
    private val SEAL_AT = CoordGrid(3679, 5279)
    private val SEAL_X = 9..11
    private val SEAL_Y = 9..11

    /** Data (LD `HetSolver`): the shielded statue's 3 x 3 sits at puzzle-space (12..14, 9..11). */
    private val STATUE_X = 12..14
    private val STATUE_Y = 9..11

    /**
     * OPEN-PZ26: where the caster stands is not documented. It must not block the exit corridor (rows 5279-5281 through the
     * flame barrier at x 3669), so it stands just north of it; the beam starts at puzzle (1, 10) heading east.
     */
    private val CASTER_AT = CoordGrid(3668, 5282)
    private const val BEAM_X = 1
    private const val BEAM_Y = 10

    /** OPEN-PZ25: the three loose mirrors start south-west of the Seal, off every route of [ROUTES]. */
    private val LOOSE_AT = listOf(6 to 4, 7 to 4, 8 to 4)
    private const val LOOSE = 3

    /** Data (LD `HetSolution`, runelite-timings RT111): the mirror positions of the nine routes, in beam order. */
    private val ROUTES: List<List<IntArray>> =
        listOf(
            listOf(intArrayOf(5, 10), intArrayOf(5, 18), intArrayOf(14, 18)),
            listOf(intArrayOf(5, 10), intArrayOf(5, 6), intArrayOf(18, 6), intArrayOf(18, 10)),
            listOf(intArrayOf(4, 10), intArrayOf(4, 2), intArrayOf(13, 2)),
            listOf(intArrayOf(3, 10), intArrayOf(3, 2), intArrayOf(17, 2), intArrayOf(17, 11)),
            listOf(intArrayOf(2, 10), intArrayOf(2, 17), intArrayOf(13, 17)),
            listOf(intArrayOf(3, 10), intArrayOf(3, 17), intArrayOf(14, 17)),
            listOf(intArrayOf(5, 10), intArrayOf(5, 13), intArrayOf(12, 13)),
            listOf(intArrayOf(4, 10), intArrayOf(4, 3), intArrayOf(13, 3)),
            listOf(intArrayOf(5, 10), intArrayOf(5, 3), intArrayOf(13, 3)),
        )

    private const val EAST = 0
    private const val NORTH = 1
    private const val WEST = 2
    private const val SOUTH = 3
    private val DX = intArrayOf(1, 0, -1, 0)
    private val DY = intArrayOf(0, 1, 0, -1)

    /** A '/' mirror turns east to north, north to east, west to south, south to west; '\' the other diagonal. */
    private val SLASH = intArrayOf(NORTH, EAST, SOUTH, WEST)
    private val BACKSLASH = intArrayOf(SOUTH, WEST, NORTH, EAST)
    private const val ANGLES = 4
    private const val MAX_STEPS = 64

    /** HE4 (LD): the first shot comes 10 ticks after the start, and 10 ticks after the Seal is protected again. */
    private const val FIRST_BEAM = 10L

    /** HE7: the Seal is exposed for 15 ticks (9 s). */
    private const val WEAK_TICKS = 15L

    /** HE6: Het's Seal hitpoints for 1-8 players. */
    private val SEAL_HP = intArrayOf(119, 225, 321, 417, 512, 608, 704, 800)
    private const val STATS = 6
    private const val HITPOINTS = 3

    /** HE8: Mining brackets and damage bases (Seal, Mod Ash 25 Mar 2023). */
    private const val LEVEL_MID = 85
    private const val LEVEL_TOP = 99
    private const val BASE_LOW = 8
    private const val BASE_MID = 15
    private const val BASE_TOP = 17
    private const val DRAGON_BONUS = 2
    private const val LOW_PENALTY = 3
    private const val ROLL_SPREAD = 3

    /** HE8: 35 Mining XP per hit (Seal). */
    private const val SEAL_XP = 35.0

    private const val NO_PICKAXE = 0
    private const val TIER_LOW = 1
    private const val TIER_RUNE = 2
    private const val TIER_DRAGON = 3

    private const val HITMARK_LIT = 16
    private const val HITMARK_TINT = 17

    /** Data: the pickaxe statues' loc ids (parent 45468, the empty and tier children, cache). */
    private val STATUES =
        listOf(Locs.TOA_HET_STATUE_PARENT) + (Locs.TOA_HET_STATUE_PICKAXE_EMPTY01..Locs.TOA_HET_STATUE_PICKAXE_TRAILBLAZER01)

    /**
     * HE2 (cache multi order of varbit 14440, RT113 item ids): stored value -> pickaxe. 0 is the free bronze pickaxe; 9 is
     * the upgraded dragon (12797), 10 the (or) variant (25376), 13 the 3rd age pickaxe (20014).
     */
    private val STORED_PICKAXES: Map<Int, Int> =
        mapOf(
            1 to Objs.IRON_PICKAXE,
            2 to Objs.STEEL_PICKAXE,
            3 to Objs.BLACK_PICKAXE,
            4 to Objs.MITHRIL_PICKAXE,
            5 to Objs.ADAMANT_PICKAXE,
            6 to Objs.RUNE_PICKAXE,
            7 to Objs.TRAIL_GILDED_PICKAXE,
            8 to Objs.DRAGON_PICKAXE,
            9 to 12797,
            10 to 25376,
            11 to Objs.INFERNAL_PICKAXE,
            13 to 20014,
            14 to Objs.CRYSTAL_PICKAXE,
            17 to Objs.TRAILBLAZER_PICKAXE,
            20 to Objs.TRAILBLAZER_RELOADED_PICKAXE,
        )
}
