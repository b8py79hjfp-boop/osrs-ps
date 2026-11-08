package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Sotetseg, ToB room 4 (docs/mechanics/raids/tob/sotetseg.md SO1-SO6; timings runelite-timings.md and capture-values.md,
 * damage from the wiki, D-080), all three modes.
 * - Immobile in the north of its arena; it wakes (combat form) when a raider crosses the barrier. Every 5 ticks: the horn
 *   lunge at a raider beside it (45, 22 prayed; lands +1) or the red ball (magic, lands +6): up to 50, nothing through
 *   Protect from Magic. A struck raider sends a red and a grey bounce on to up to two others within 9 (grey end 216,
 *   red 36 + 30 x max(d, 3) cycles). A failed protection disables protection prayers 5 ticks.
 * - After 10 balls the death ball at one raider ("... has discovered a large ball of energy coming their way..."), end 480
 *   cycles, landing +16, shared by everyone in the 3 x 3 around the target; the next attack 10 ticks after it.
 * - At 66.6% and 33.3% the Shadow Realm: one raider (Hard: all but one, the maze split by orb order) runs a random path in
 *   the realm (chip 1-3 every 7 ticks, landing 2 later); the rest go to the south of the arena and must walk the same
 *   path (the runner's tile glows). A wrong tile hurts 6.67% of current HP + 15 (Entry + 11) every tick; past the third
 *   row a tornado follows the path (OPEN-SO8). Its Defence is restored after the maze; it never drops below 100.
 */
internal object TobSotetseg : Boss(
    Npcs.TOB_SOTETSEG_NONCOMBAT,
    Npcs.TOB_SOTETSEG_COMBAT,
    Npcs.TOB_SOTETSEG_NONCOMBAT_STORY,
    Npcs.TOB_SOTETSEG_COMBAT_STORY,
    Npcs.TOB_SOTETSEG_NONCOMBAT_HARD,
    Npcs.TOB_SOTETSEG_COMBAT_HARD,
) {
    private class Maze(val path: List<Pair<Int, Int>>, val runners: List<PlayerRef>, val started: Long) {
        var furthest = 0
        var tornadoAt = -1
        var done = false
    }

    private class State(val mode: Int, val home: CoordGrid) {
        var awake = false
        var balls = 0
        var mazes = 0
        var maze: Maze? = null
    }

    private val STATE = AttrKey<State>("tob_sotetseg")

    private fun state(npc: NpcRef): State = npc[STATE] ?: State(TobModes.of(npc.type), npc.spawn).also { npc[STATE] = it }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
        loop(npc, state(npc))
    }

    /** Raiders in its arena (template 3273..3286 x 4306..4333, offsets from its spawn 3277, 4325). */
    private fun raiders(npc: NpcRef, s: State): List<PlayerRef> =
        TfaKit.arena(npc).filter {
            it.coord.level == s.home.level && it.coord.x - s.home.x in ARENA_MIN_X..ARENA_MAX_X &&
                it.coord.z - s.home.z in ARENA_MIN_Z..ARENA_MAX_Z
        }

    private fun loop(npc: NpcRef, s: State) {
        npc.queueHit(1, strong = false) {
            if (!npc.isInWorld) return@queueHit
            tick(npc, s)
            loop(npc, s)
        }
    }

    private fun tick(npc: NpcRef, s: State) {
        if (!s.awake) {
            if (raiders(npc, s).none { it.coord.z - s.home.z >= BARRIER_Z }) return
            s.awake = true
            npc.transform(npc.type + 1, restoreStats = false)
            npc.huntRange = HUNT
            npc.setAttackClock(npc.tick + ATTACK_SPEED)
        }
        // SO-data: its Defence can be lowered to 100 at most.
        if (npc.level(Stats.DEFENCE) < DEFENCE_FLOOR) npc.setLevel(Stats.DEFENCE, DEFENCE_FLOOR)
        s.maze?.let { mazeTick(npc, s, it) }
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        if (!s.awake || s.maze != null) return
        npc.setAttackClock(npc.tick + ATTACK_SPEED)
        val raiders = raiders(npc, s)
        val target = victim as? PlayerRef ?: return
        if (s.balls >= BALLS_BEFORE_DEATH_BALL) {
            deathBall(npc, s, raiders.getOrNull(npc.random(maxOf(1, raiders.size))) ?: target)
            return
        }
        if (!distance) {
            npc.anim(Seqs.TOB_SOTETSEG_ATTACK_MELEE)
            CombatSystem.announceAttack(npc, target, CombatStyle.MELEE)
            val max = TobModes.damage(s.mode, MELEE_MAX)
            val raw = BossCombat.roll(npc, 0, max)
            CombatSystem.queueHit(target, npc, if (target.protects(CombatStyle.MELEE)) raw / 2 else raw, HitStyle.MELEE, 1)
            return
        }
        s.balls++
        npc.anim(Seqs.TOB_SOTETSEG_ATTACK_RANGED)
        npc.projectile(target, SpotAnims.TOB_SOTETSEG_MAGING, PROJ_HEIGHT, PROJ_HEIGHT, BALL_START, BALL_END)
        orb(npc, s, target, CombatStyle.MAGIC, BALL_LAND, bounce = true)
    }

    /** SO3 / capture: an orb lands; unprotected up to 50; a struck raider bounces a red and a grey one on (at most two). */
    private fun orb(npc: NpcRef, s: State, target: PlayerRef, style: CombatStyle, delay: Int, bounce: Boolean) {
        npc.queueHit(delay, strong = false) {
            if (!npc.isInWorld || target.isDying || s.maze != null) return@queueHit
            val prayed = target.protects(style)
            val dmg = if (prayed) 0 else TobModes.damage(s.mode, BossCombat.roll(npc, 0, ORB_MAX))
            CombatSystem.applyHit(target, npc, dmg, CombatSystem.hitStyle(style))
            if (!prayed) target.disableProtection(PRAYER_LOCK)
            if (!bounce) return@queueHit
            val others = raiders(npc, s).filter { it != target && it.distanceTo(target) <= BOUNCE_REACH }.sortedBy { it.distanceTo(target) }
            others.getOrNull(0)?.let { bounceTo(npc, s, target, it, CombatStyle.MAGIC) }
            others.getOrNull(1)?.let { bounceTo(npc, s, target, it, CombatStyle.RANGED) }
        }
    }

    private fun bounceTo(npc: NpcRef, s: State, from: PlayerRef, to: PlayerRef, style: CombatStyle) {
        val d = maxOf(MIN_BOUNCE_TILES, from.distanceTo(to))
        val end = if (style == CombatStyle.RANGED) GREY_END else RED_END_BASE + RED_END_PER_TILE * d
        from.projectile(
            to,
            if (style ==
                CombatStyle.RANGED
            ) {
                SpotAnims.TOB_SOTETSEG_RANGING
            } else {
                SpotAnims.TOB_SOTETSEG_MAGING
            },
            PROJ_HEIGHT,
            PROJ_HEIGHT,
            0,
            end,
        )
        orb(npc, s, to, style, end / CYCLES_PER_TICK, bounce = false)
    }

    /** SO4 / capture: the death ball (end 480, landing +16), shared by the 3 x 3 around its target. */
    private fun deathBall(npc: NpcRef, s: State, target: PlayerRef) {
        s.balls = 0
        npc.anim(Seqs.TOB_SOTETSEG_ATTACK_RANGED)
        for (p in raiders(
            npc,
            s,
        )) {
            p.message("<col=ff0000>${target.username} has discovered a large ball of energy coming their way...</col>")
        }
        npc.projectile(target, SpotAnims.TOB_SOTETSEG_SHAREDATTACK, PROJ_HEIGHT, PROJ_HEIGHT, 0, DEATH_BALL_END)
        val count = raiders(npc, s).size
        npc.setAttackClock(npc.tick + AFTER_DEATH_BALL)
        npc.queueHit(DEATH_BALL_LAND, strong = false) {
            if (!npc.isInWorld || s.maze != null) return@queueHit
            npc.spotAnimAt(target.coord, SpotAnims.TOB_SOTETSEG_SHAREDATTACK_IMPACT)
            val sharers = raiders(npc, s).filter { it.distanceTo(target) <= 1 }
            if (sharers.isEmpty()) return@queueHit
            val total = deathBallDamage(s.mode, count)
            for (p in sharers) CombatSystem.applyHit(p, npc, total / sharers.size, HitStyle.TYPELESS)
        }
    }

    /**
     * SO4: "up to 188 damage (scaling with party size)"; Entry 15 solo, about 80 in a group (OPEN-SO10). OPEN-SO4: 47 a
     * raider in the arena at the throw (188 at four).
     */
    private fun deathBallDamage(mode: Int, raiders: Int): Int =
        when {
            mode == TobModes.ENTRY -> if (raiders <= 1) ENTRY_SOLO_BALL else ENTRY_GROUP_BALL
            else -> minOf(DEATH_BALL_MAX, DEATH_BALL_PER_RAIDER * maxOf(1, raiders))
        }

    // ---- the Shadow Realm (SO5)

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        if (s.maze != null || s.mazes >= MAZE_AT.size) return
        if (npc.hitpoints * PER_MILLE > npc.maxHitpoints * MAZE_AT[s.mazes]) return
        s.mazes++
        startMaze(npc, s)
    }

    private fun startMaze(npc: NpcRef, s: State) {
        val raiders = raiders(npc, s)
        if (raiders.isEmpty()) return
        val order = npc[tp.plugin.SharedAttrs.RAID_ORDER].orEmpty()
        val byOrder =
            raiders.sortedBy { p ->
                order.indexOfFirst { it.equals(p.username, ignoreCase = true) }.let {
                    if (it <
                        0
                    ) {
                        Int.MAX_VALUE
                    } else {
                        it
                    }
                }
            }
        val runners =
            if (s.mode == TobModes.HARD && raiders.size > 1) byOrder.dropLast(1) else listOf(raiders[npc.random(raiders.size)])
        val path = randomPath(npc)
        val maze = Maze(path, runners, npc.tick)
        s.maze = maze
        npc.stopInteraction()
        for (r in runners) {
            r.message("Sotetseg chooses you...")
            r.anim(Seqs.HUMAN_TELEPORT_OTHER_IMPACT)
            val (x, z) = path.first()
            r.teleport(realmTile(s, x, z))
        }
        for (p in raiders - runners.toSet()) p.teleport(CoordGrid(s.home.x + SOUTH_X, s.home.z + SOUTH_Z, s.home.level))
        for ((x, z) in path) npc.addLoc(Locs.TOB_SOTETSEG_LIGHTTILE, realmTile(s, x, z), FLOOR_SHAPE, 0, MAZE_LIMIT)
    }

    /** OPEN-SO5: a path from the south row to the north row of the 14 x 15 grid, one row a step, drifting sideways. */
    private fun randomPath(npc: NpcRef): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var x = npc.random(GRID_W)
        for (z in 0 until GRID_H) {
            out += x to z
            val drift = npc.random(MAX_DRIFT * 2 + 1) - MAX_DRIFT
            val nx = (x + drift).coerceIn(0, GRID_W - 1)
            val step = Integer.signum(nx - x)
            while (x != nx) {
                x += step
                out += x to z
            }
        }
        return out
    }

    private fun realmTile(s: State, x: Int, z: Int) = CoordGrid(s.home.x + REALM_X + x, s.home.z + REALM_Z + z, s.home.level + REALM_LEVEL)

    private fun arenaTile(s: State, x: Int, z: Int) = CoordGrid(s.home.x + GRID_X + x, s.home.z + GRID_Z + z, s.home.level)

    private fun mazeTick(npc: NpcRef, s: State, m: Maze) {
        val age = (npc.tick - m.started).toInt()
        val onPath = m.path.toSet()
        // Capture: every 7 ticks the realm drains the runners 1-3, landing 2 ticks later.
        if (age > 0 && age % CHIP_EVERY == 0) {
            for (r in m.runners) {
                r.spotAnim(SpotAnims.TOB_SOTETSEG_DRAIN)
                CombatSystem.queueHit(r, npc, BossCombat.roll(npc, CHIP_MIN, CHIP_MAX), HitStyle.TYPELESS, CHIP_LAND)
            }
        }
        for (r in m.runners) {
            val gx = r.coord.x - s.home.x - REALM_X
            val gz = r.coord.z - s.home.z - REALM_Z
            if (gx in 0 until GRID_W && gz in 0 until GRID_H) {
                if ((gx to gz) !in onPath) wrongTile(npc, s, r) else m.furthest = maxOf(m.furthest, gz)
                npc.addLoc(Locs.TOB_SOTETSEG_TRIGGEREDTILE, arenaTile(s, gx, gz), FLOOR_SHAPE, 0, 1)
            }
            if (gz >= GRID_H) m.done = true
        }
        for (p in raiders(npc, s)) {
            val gx = p.coord.x - s.home.x - GRID_X
            val gz = p.coord.z - s.home.z - GRID_Z
            if (gx !in 0 until GRID_W || gz !in 0 until GRID_H) continue
            if ((gx to gz) !in onPath) wrongTile(npc, s, p)
            if (gz >= TORNADO_ROW && m.tornadoAt < 0) m.tornadoAt = 0
            if (gz < TORNADO_ROW - 1 && raiders(npc, s).all { it.coord.z - s.home.z - GRID_Z < TORNADO_ROW }) m.tornadoAt = -1
        }
        tornado(npc, s, m)
        if (m.done || age >= MAZE_LIMIT) endMaze(npc, s, m)
    }

    /** SO5: a wrong tile hurts 6.67% of current HP + 15 (Entry + 11) every tick, zapping the tile. */
    private fun wrongTile(npc: NpcRef, s: State, p: PlayerRef) {
        npc.spotAnimAt(p.coord, SpotAnims.TOB_SOTETSEG_ZAP)
        val flat = if (s.mode == TobModes.ENTRY) ENTRY_WRONG_FLAT else WRONG_FLAT
        CombatSystem.applyHit(p, npc, p.hitpoints * WRONG_PER_TEN_THOUSAND / TEN_THOUSAND + flat, HitStyle.TYPELESS)
    }

    /** SO5 / OPEN-SO8: the tornado follows the path a tile a tick; anyone it catches takes 50 (the wiki: "massive"). */
    private fun tornado(npc: NpcRef, s: State, m: Maze) {
        if (m.tornadoAt < 0) return
        val (x, z) = m.path[minOf(m.tornadoAt, m.path.size - 1)]
        val tile = arenaTile(s, x, z)
        npc.spotAnimAt(tile, SpotAnims.TOB_SOTETSEG_ZAP)
        for (p in raiders(npc, s)) {
            if (maxOf(kotlin.math.abs(p.coord.x - tile.x), kotlin.math.abs(p.coord.z - tile.z)) <=
                1
            ) {
                CombatSystem.applyHit(p, npc, TORNADO_DAMAGE, HitStyle.TYPELESS)
            }
        }
        m.tornadoAt++
    }

    private fun endMaze(npc: NpcRef, s: State, m: Maze) {
        s.maze = null
        for (r in m.runners) if (!r.isDying) r.teleport(CoordGrid(s.home.x + SOUTH_X, s.home.z + SOUTH_Z, s.home.level))
        // SO5: Defence restored; the 4 Jun 2021 hotfix: no death ball straight after the maze.
        npc.setLevel(Stats.DEFENCE, npc.baseLevel(Stats.DEFENCE))
        s.balls = 0
        npc.setAttackClock(npc.tick + ATTACK_SPEED)
    }

    /** No damage reaches it while it is asleep (SO5: during the maze only its own attacks are harmless). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        return if (!s.awake) 0 else damage
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        state(npc).maze?.let { endMaze(npc, state(npc), it) }
    }

    private val MAZE_AT = intArrayOf(666, 333)

    private const val HUNT = 20
    private const val ARENA_MIN_X = -4
    private const val ARENA_MAX_X = 9
    private const val ARENA_MIN_Z = -19
    private const val ARENA_MAX_Z = 8

    /** The barrier row (template z 4307) from its spawn: raiders north of it wake it. */
    private const val BARRIER_Z = -17
    private const val ATTACK_SPEED = 5
    private const val DEFENCE_FLOOR = 100
    private const val MELEE_MAX = 45
    private const val ORB_MAX = 50
    private const val PRAYER_LOCK = 5
    private const val PROJ_HEIGHT = 43
    private const val BALL_START = 23
    private const val BALL_END = 203
    private const val BALL_LAND = 6
    private const val BOUNCE_REACH = 9
    private const val MIN_BOUNCE_TILES = 3
    private const val GREY_END = 216
    private const val RED_END_BASE = 36
    private const val RED_END_PER_TILE = 30
    private const val CYCLES_PER_TICK = 30
    private const val BALLS_BEFORE_DEATH_BALL = 10
    private const val DEATH_BALL_END = 480
    private const val DEATH_BALL_LAND = 16
    private const val AFTER_DEATH_BALL = 10
    private const val DEATH_BALL_MAX = 188
    private const val DEATH_BALL_PER_RAIDER = 47
    private const val ENTRY_SOLO_BALL = 15

    /** OPEN-SO10: Entry group death ball (Entry page "70+", Sotetseg page "around 80"). */
    private const val ENTRY_GROUP_BALL = 80
    private const val PER_MILLE = 1000
    private const val GRID_W = 14
    private const val GRID_H = 15
    private const val MAX_DRIFT = 2

    /** Grids from its spawn: arena 3273..3286 x 4310..4324; realm 3354..3367 x 4311..4325 on level 3. */
    private const val GRID_X = -4
    private const val GRID_Z = -15
    private const val REALM_X = 77
    private const val REALM_Z = -14
    private const val REALM_LEVEL = 3

    /** The arena's south end, where the others wait (template 3279, 4308). */
    private const val SOUTH_X = 2
    private const val SOUTH_Z = -17
    private const val FLOOR_SHAPE = 22

    /** OPEN-SO5: the longest a maze lasts before the runners are brought back. */
    private const val MAZE_LIMIT = 200
    private const val CHIP_EVERY = 7
    private const val CHIP_MIN = 1
    private const val CHIP_MAX = 3
    private const val CHIP_LAND = 2
    private const val TORNADO_ROW = 3
    private const val TORNADO_DAMAGE = 50
    private const val WRONG_FLAT = 15
    private const val ENTRY_WRONG_FLAT = 11
    private const val WRONG_PER_TEN_THOUSAND = 667
    private const val TEN_THOUSAND = 10_000
}
