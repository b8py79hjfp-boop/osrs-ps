@file:Suppress(
    "TooManyFunctions",
    "LargeClass",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ReturnCount",
    "NestedBlockDepth",
)

package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.plugin.chatNpc
import tp.plugin.chatPlayer
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Per-player state of the minigame; [arena] is the floor the player entered
 * (docs/mechanics/guardians-of-the-rift.md).
 */
internal class GotrPlayer(val arena: Arena) {
    var elemental = 0
    var catalytic = 0
    var rechargeEnergy = 0
    var rechargeXp = 0.0
    var fragmentsMined = 0
    var talismanGiven = false
    var attunedWarned = false

    /** Where the player stood when entering a portal guardian: the altar's exit portal brings them back here. */
    var back: CoordGrid? = null
    var mineKey = 0L
    var mineNext = 0L

    /** Start of a game: the per-game counters begin again. */
    fun reset() {
        elemental = 0
        catalytic = 0
        rechargeEnergy = 0
        rechargeXp = 0.0
        fragmentsMined = 0
        talismanGiven = false
        attunedWarned = false
    }
}

internal enum class GotrPhase { WAITING, PREP, FIGHT, GRACE }

internal class Monster(val npc: NpcRef, val kind: Int, var health: Int, val born: Long)

internal class RiftGuardian(val npc: NpcRef, val tier: CellTier, val elemental: Boolean)

/**
 * The running game of one [arena] (GR2-GR12). World-thread state, advanced once per tick by the players inside
 * ([GuardiansOfTheRift.advance]).
 */
internal class GotrGame(val arena: Arena) {
    val players = LinkedHashSet<PlayerRef>()
    var phase = GotrPhase.WAITING
    var phaseEnd = 0L
    var lastTick = -1L
    var won = false
    var power = 0
    var maxPower = GotrConfig.STONES_PER_PLAYER
    val tier = IntArray(GotrData.CELL_TILES.size)
    val health = IntArray(GotrData.CELL_TILES.size)
    val broken = BooleanArray(GotrData.CELL_TILES.size)
    val barrierNpc = arrayOfNulls<NpcRef>(GotrData.CELL_TILES.size)
    val guards = ArrayList<RiftGuardian>()
    val monsters = ArrayList<Monster>()
    var elemental: GotrAltar = GotrData.ELEMENTAL[0]
    var catalytic: GotrAltar = GotrData.CATALYTIC[0]
    var cycles = 0
    var rotateAt = 0L
    var mineAt = 0L
    var mineEnd = 0L
    var minePortal: Pair<String, CoordGrid>? = null
    var nextWave = 0L
    var fightStart = 0L
    var rumbled = false
    var locked = false

    fun percent(): Int = if (maxPower == 0) 0 else power * PERCENT / maxPower

    private companion object {
        const val PERCENT = 100
    }
}

/**
 * Guardians of the Rift (docs/mechanics/guardians-of-the-rift.md): the Temple of the Eye's minigame. Lobby, barrier,
 * game timers,
 * fragment mining, the workbench, cells, barriers, rift guardians, the Great Guardian, portal guardians and altar
 * imbuing, the
 * shortcut portals and the rubble. The Rewards Guardian and the apprentices are in `RunecraftGotrRewards.kt`. The
 * quest tutorial
 * runs the same handlers on its own copy of the floor ([Arena.TUTORIAL]).
 */
public class GuardiansOfTheRift : ContentPlugin {
    private val games = Arena.ALL.associateWith { GotrGame(it) }
    private var pickLevels: Map<Int, Int> = emptyMap()

    override fun ContentScope.register() {
        onWorldStart {
            pickLevels = PICKAXES.keys.associateWith { objParamInt(it, Skilling.PARAM_LEVEL_REQUIRE) ?: 1 }
            for (arena in Arena.ALL) spawnFloor(this, arena)
        }
        onLogout { leave(this) }
        // A player who logged out inside the minigame comes back outside it: the client's flag starts again.
        onLogin { setVarbit(Varbits.GOTR_IS_PLAYING, 0) }
        for (barrier in intArrayOf(Locs.GOTR_BARRIER, Locs.GOTR_BARRIER_CLOSED, Locs.GOTR_BARRIER_NOENTRY)) {
            onOpLoc(barrier, OP_PASS) { pass(player) }
            onOpLoc(barrier, OP_PEEK) { peek(player) }
            onOpLoc(barrier, OP_QUICK_PASS) { pass(player) }
            onOpLoc(barrier, OP_EFFECTS) { toggleEffects(player) }
        }
        for (loc in intArrayOf(Locs.GOTR_ENTRY, Locs.GOTR_ENTRY_NOOP)) onOpLoc(loc, OP_PASS) { exitTemple(player) }
        onOpLoc(Locs.TOTE_PORTAL_TO_GOTR_PARENT, OP_PASS) { enterTemple(player) }
        registerFloor(this)
    }

    private fun registerFloor(scope: ContentScope) =
        with(scope) {
            for (id in PARTS) onOpLoc(id, OP_MINE) { mineRemains(it, GotrConfig.FRAGMENT_LOW, GotrConfig.FRAGMENT_HIGH, true) }
            for (id in REMAINS) onOpLoc(id, OP_MINE) { mineRemains(it, GotrConfig.FRAGMENT_LOW, GotrConfig.FRAGMENT_HIGH, true) }
            onOpLoc(Locs.GOTR_ESSENCE_TIER_2, OP_MINE) { mineRemains(it, GotrConfig.LARGE_LOW, GotrConfig.LARGE_HIGH, true) }
            onOpLoc(Locs.GOTR_ESSENCE_TIER_3, OP_MINE) { mineRemains(it, GotrConfig.HUGE_LOW, GotrConfig.HUGE_HIGH, false) }
            onOpLoc(Locs.GOTR_FALLEN_GUARDIAN, OP_MINE) { mineRemains(it, GotrConfig.FALLEN_LOW, GotrConfig.FALLEN_HIGH, false) }
            onOpLoc(Locs.GOTR_WORKBENCH, OP_WORKBENCH) { workbench(player) }
            onOpLoc(Locs.GOTR_UNCHARGED_CELLS, OP_TAKE_TEN) { takeUncharged(player, GotrConfig.UNCHARGED_CAP) }
            onOpLoc(Locs.GOTR_UNCHARGED_CELLS, OP_TAKE_ONE) { takeUncharged(player, 1) }
            onOpLoc(Locs.TOTE_TUTORIAL_UNCHARGED_CELLS, OP_TAKE_TEN) { takeUncharged(player, GotrConfig.UNCHARGED_CAP) }
            onOpLoc(Locs.TOTE_TUTORIAL_UNCHARGED_CELLS, OP_TAKE_ONE) { takeUncharged(player, 1) }
            onOpLoc(Locs.GOTR_WEAK_CELLS, OP_TAKE_CELL) { takeWeakCell(player) }
            onOpLoc(Locs.GOTR_WEAK_CELLS, OP_TAKE_CHISEL) { takeChisel(player) }
            onOpLoc(Locs.GOTR_GUARDIAN_SPAWN_EAST, OP_ASSEMBLE) { assemble(player, true) }
            onOpLoc(Locs.GOTR_GUARDIAN_SPAWN_WEST, OP_ASSEMBLE) { assemble(player, false) }
            for (tile in TILE_OPS) {
                onOpLoc(tile, OP_PLACE) { placeCell(player, it) }
            }
            onOpLoc(Locs.GOTR_CELL_TILE_BROKEN_REPAIRABLE, OP_PLACE) { repairTile(player, it) }
            onOpNpc(Npcs.GOTR_TAMARA_PARENT, OP_POWER_UP) { tamara(it) }
            onOpNpc(Npcs.GOTR_GREAT_GUARDIAN, OP_POWER_UP) { powerUp(player, it) }
            onOpNpc(Npcs.TOTE_GREAT_GUARDIAN, OP_POWER_UP) { powerUp(player, it) }
            for (altar in GotrData.ALTARS) {
                onOpLoc(altar.portal, OP_PASS) { enterPortal(player, altar, false) }
                onOpLoc(altar.portalTutorial, OP_PASS) { enterPortal(player, altar, false) }
                onUseOnLoc(altar.portalTalisman, altar.portal) { enterPortal(player, altar, true) }
                onUseOnLoc(altar.portalTalisman, altar.portalTutorial) { enterPortal(player, altar, true) }
            }
            onOpLoc(Locs.GOTR_AGILITY_SHORTCUT_TOP, OP_PASS) { climb(player, it) }
            onOpLoc(Locs.GOTR_AGILITY_SHORTCUT_BOTTOM, OP_PASS) { climb(player, it) }
            onOpLoc(Locs.GOTR_AGILITY_PORTAL_TOP, OP_PASS) { minePortalIn(player) }
            onOpLoc(Locs.GOTR_AGILITY_PORTAL_BOTTOM_CHILD, OP_PASS) { minePortalOut(player) }
        }

    // ---- the floor

    /**
     * Great Guardian, Felix, Tamara and Cordelia on both floors (Wiki NPC pages; the tutorial's cache NPCs are the
     * `tote_*` ones).
     */
    private fun spawnFloor(w: tp.plugin.WorldRef, arena: Arena) {
        val main = !arena.tutorial
        w.spawnNpc(if (main) Npcs.GOTR_GREAT_GUARDIAN else Npcs.TOTE_GREAT_GUARDIAN, arena.at(GotrData.GREAT_GUARDIAN), 0)
        val felix = if (main) Npcs.GOTR_REWARD_TRADER else Npcs.TOTE_FELIX_TEMPLE
        w.spawnNpc(felix, arena.at(if (main) FELIX_MAIN else FELIX_TUTORIAL), FELIX_WANDER)
        w.spawnNpc(if (main) Npcs.GOTR_TAMARA_PARENT else Npcs.TOTE_TAMARA_TEMPLE, arena.at(TAMARA), TAMARA_WANDER)
        w.spawnNpc(if (main) Npcs.GOTR_CORDELIA else Npcs.TOTE_CORDELIA_TEMPLE, arena.at(CORDELIA), CORDELIA_WANDER)
    }

    /**
     * The player's state; players standing on the tutorial floor are in it without a barrier (the quest teleports
     * them there).
     */
    private fun state(p: PlayerRef): GotrPlayer? {
        p[STATE]?.let { st ->
            if (!st.arena.tutorial || st.back != null || p.coord in st.arena) return st
            leave(p)
            return null
        }
        val arena = Arena.of(p.coord)?.takeIf { it.tutorial } ?: return null
        return GotrPlayer(arena).also {
            p[STATE] = it
            games.getValue(arena).players += p
        }
    }

    // ---- entering and leaving

    /** Wizards' Tower portal (loc `tote_portal_to_gotr`): the Temple of the Eye quest must be done (GR1). */
    private fun enterTemple(p: PlayerRef) {
        p.stopInteraction()
        val stage = RunecraftQuests.TEMPLE_OF_THE_EYE.stage(p)
        // The quest's own visit (stage 91 to 129) goes to the tutorial floor, a copy of the temple
        // (quest-temple-of-the-eye.md TE6).
        if (stage in TUTORIAL_FROM..TUTORIAL_TO) return p.teleport(landing(p, Arena.TUTORIAL.at(GotrData.LOBBY)))
        if (!RunecraftQuests.TEMPLE_OF_THE_EYE.done(p)) return p.message("Nothing interesting happens.")
        p.teleport(landing(p, Arena.MAIN.at(GotrData.LOBBY)))
    }

    private fun exitTemple(p: PlayerRef) {
        p.stopInteraction()
        leave(p)
        p.teleport(WIZARDS_TOWER_PORTAL)
    }

    private fun landing(p: PlayerRef, at: CoordGrid): CoordGrid =
        (0..LANDING_RADIUS)
            .flatMap { r -> (-r..r).flatMap { dx -> (-r..r).map { dz -> at.translate(dx, dz) } } }
            .firstOrNull { p.isWalkable(it) } ?: at

    private fun pass(p: PlayerRef) {
        p.stopInteraction()
        val arena = Arena.of(p.coord) ?: return
        val game = games.getValue(arena)
        val x = p.coord.x.coerceIn(arena.at(GotrData.BARRIER_X.first, 0).x, arena.at(GotrData.BARRIER_X.last, 0).x)
        if (arena.inside(p.coord)) {
            leave(p)
            return p.teleport(CoordGrid(x, GotrData.BARRIER.z + arena.dz - 1, 0))
        }
        if (!RunecraftQuests.TEMPLE_OF_THE_EYE.done(p) && !arena.tutorial) return p.message("Nothing interesting happens.")
        if (p.level(Stats.RUNECRAFT) < GotrConfig.MIN_LEVEL) {
            return p.message("You need a Runecraft level of ${GotrConfig.MIN_LEVEL} to take part.")
        }
        val running = game.phase == GotrPhase.FIGHT || game.phase == GotrPhase.GRACE
        if (running || (game.locked && game.phase != GotrPhase.WAITING)) return p.message("The barrier is closed while the rift is open.")
        p.teleport(CoordGrid(x, GotrData.BARRIER.z + arena.dz + 1, 0))
        join(p, game)
    }

    private fun peek(p: PlayerRef) {
        p.stopInteraction()
        val game = games.getValue(Arena.of(p.coord) ?: return)
        p.message("The Great Guardian's power: ${game.percent()}%. Players inside: ${game.players.size}.")
    }

    private fun toggleEffects(p: PlayerRef) {
        p.stopInteraction()
        val off = p.varbit(Varbits.GOTR_COLOURFUL_FADE_DISABLED) == 0
        p.setVarbit(Varbits.GOTR_COLOURFUL_FADE_DISABLED, if (off) 1 else 0)
        p.message("Visual effects are now ${if (off) "off" else "on"}.")
    }

    private fun join(p: PlayerRef, game: GotrGame) {
        if (p[STATE] != null) return
        p[STATE] = GotrPlayer(game.arena)
        game.players += p
        removeGameItems(p)
        p.setVarbit(Varbits.GOTR_IS_PLAYING, 1)
        if (!p.isOpen(Interfaces.GOTR_HUD)) p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.GOTR_HUD)
        p.ifHide(Components.Gotr_Hud.UNIVERSE, false)
        p.timer(TICK, 1, soft = true) { advance(this) }
    }

    private fun leave(p: PlayerRef) {
        val st = p[STATE] ?: return
        games[st.arena]?.players?.remove(p)
        p[STATE] = null
        p.stopTimer(TICK)
        removeGameItems(p)
        p.setVarbit(Varbits.GOTR_IS_PLAYING, 0)
        if (p.isOpen(Interfaces.GOTR_HUD)) p.ifHide(Components.Gotr_Hud.UNIVERSE, true)
    }

    /**
     * Wiki: "all guardian fragments, essence, and portal talismans acquired during the game will be removed" (also
     * stones and cells).
     */
    private fun removeGameItems(p: PlayerRef) {
        val inv = p.inv(Invs.INV)
        val held = GotrData.GAME_ITEMS.filter { inv.total(it) > 0 }
        if (held.isEmpty()) return
        p.transaction("gotr clear") { held.all { remove(Invs.INV, it, inv.total(it).toInt()) } }
    }

    // ---- the game clock (GR3-GR6)

    /** Called every tick by each player in the game; only the first call of a tick does anything. */
    private fun advance(p: PlayerRef) {
        val st = p[STATE] ?: return p.stopTimer(TICK)
        val game = games.getValue(st.arena)
        val now = p.tick
        if (game.lastTick != now) {
            game.lastTick = now
            step(game, p, now)
        }
        if (now % HUD_TICKS == 0L) hud(p, st, game, now)
    }

    private fun step(game: GotrGame, p: PlayerRef, now: Long) {
        when (game.phase) {
            GotrPhase.WAITING -> {
                if (game.phaseEnd == 0L) game.phaseEnd = now + GotrConfig.LOBBY_TICKS
                if (now >= game.phaseEnd) startGame(game, p, now)
            }

            GotrPhase.PREP -> {
                rotate(game, p, now)
                if (now >= game.phaseEnd) beginFight(game, now)
            }

            GotrPhase.FIGHT -> {
                rotate(game, p, now)
                minePortals(game, p, now)
                if (now >= game.nextWave) {
                    game.nextWave = now + GotrConfig.WAVE_TICKS
                    wave(game, p, now)
                }
                if (game.power >= game.maxPower) {
                    endFight(game, p, now, true)
                } else if (game.power <= 0) {
                    endFight(game, p, now, false)
                }
            }

            GotrPhase.GRACE -> {
                if (now >= game.phaseEnd) finish(game, p, now)
            }
        }
    }

    private fun startGame(game: GotrGame, p: PlayerRef, now: Long) {
        val n = game.players.size
        val full = minOf(n, GotrConfig.FULL_TEAM)
        game.maxPower = full * GotrConfig.STONES_PER_PLAYER + (n - full) * GotrConfig.STONES_PER_LATE_PLAYER
        game.power = 0
        game.phase = GotrPhase.PREP
        game.phaseEnd = now + GotrConfig.PREP_TICKS
        game.cycles = 0
        game.rumbled = false
        game.rotateAt = 0
        for (m in game.players) {
            m[STATE]?.reset()
            removeGameItems(m)
            m.setVarbit(Varbits.GOTR_ELEMENTAL_EARNED_THIS_GAME, 0)
            m.setVarbit(Varbits.GOTR_CATALYTIC_EARNED_THIS_GAME, 0)
            m.message("The Great Guardian's power is low. The rift will open in two minutes; prepare barriers and guardians.")
        }
        swapBarrier(p, game, Locs.GOTR_BARRIER_CLOSED)
        rotate(game, p, now)
    }

    private fun beginFight(game: GotrGame, now: Long) {
        game.phase = GotrPhase.FIGHT
        game.power = game.maxPower * GotrConfig.START_PERCENT / PERCENT
        game.fightStart = now
        game.nextWave = now
        game.mineAt = now
        for (m in game.players) m.message("The rift has opened! Protect the Great Guardian.")
    }

    /**
     * Wiki: "Only two portals are active at a time: one elemental and one catalytic"; the first two cycles are medium
     * tier or higher.
     */
    private fun rotate(game: GotrGame, p: PlayerRef, now: Long) {
        if (now < game.rotateAt) return
        game.rotateAt = now + GotrConfig.ROTATE_TICKS
        val strong = game.cycles < GotrConfig.GUARANTEED_CYCLES

        fun pick(list: List<GotrAltar>, current: GotrAltar): GotrAltar =
            list.filter { it != current && (!strong || it.tier != CellTier.WEAK) }.let { it[p.random(it.size)] }
        game.elemental = pick(GotrData.ELEMENTAL, game.elemental)
        game.catalytic = pick(GotrData.CATALYTIC, game.catalytic)
        game.cycles++
        for (m in game.players) m.message("The portals to the ${game.elemental.name} and ${game.catalytic.name} altars have opened.")
    }

    private fun minePortals(game: GotrGame, p: PlayerRef, now: Long) {
        val open = game.minePortal
        if (open != null && now >= game.mineEnd) game.minePortal = null
        if (game.minePortal == null && now >= game.mineAt) {
            val (name, at) = GotrData.MINE_PORTALS[p.random(GotrData.MINE_PORTALS.size)]
            val spot = landing(p, game.arena.at(at))
            game.minePortal = name to spot
            game.mineEnd = now + GotrConfig.MINE_PORTAL_OPEN
            game.mineAt = now + GotrConfig.MINE_PORTAL_EVERY
            p.addLoc(Locs.GOTR_AGILITY_PORTAL_TOP, spot, LOC_SHAPE, 0, GotrConfig.MINE_PORTAL_OPEN)
            val shelf = landing(p, game.arena.at(GotrData.WEST_SHELF.translate(1, 0)))
            p.addLoc(Locs.GOTR_AGILITY_PORTAL_BOTTOM_CHILD, shelf, LOC_SHAPE, 0, GotrConfig.MINE_PORTAL_OPEN)
            for (m in game.players) m.message("I've opened a portal to the huge guardian fragment mine to the $name!")
        }
    }

    /** One monster wave and its resolution (GR8; every number is OPEN-GR4..GR9). */
    private fun wave(game: GotrGame, p: PlayerRef, now: Long) {
        val arena = game.arena
        val count = GotrConfig.WAVE_BASE + ((now - game.fightStart) / GotrConfig.WAVE_RAMP_TICKS).toInt() + if (game.rumbled) 1 else 0
        repeat(count) {
            if (game.monsters.size >= GotrConfig.MONSTER_CAP) return@repeat
            val kind = monsterKind(p, game.rumbled)
            val at = arena.at(GotrData.RIFT.translate(p.random(RIFT_SPREAD) - RIFT_SPREAD / 2, -2))
            val npc = p.spawnNpc(MONSTER_NPCS[kind], at, 0)
            npc.walkTo(arena.at(GotrData.GREAT_GUARDIAN.translate(p.random(RIFT_SPREAD), RIFT_SPREAD)))
            game.monsters += Monster(npc, kind, GotrConfig.MONSTER_HEALTH[kind], now)
        }
        for (g in game.guards.toList()) {
            val target = game.monsters.firstOrNull() ?: break
            target.health -= GotrConfig.GUARDIAN_DAMAGE * g.tier.level
            if (target.health <= 0) killMonster(game, target)
        }
        val fight = game.monsters.isNotEmpty() && game.guards.isNotEmpty()
        if (fight && p.random(GotrConfig.GUARDIAN_LOSS_ONE_IN) == 0) fallGuardian(game, p, game.guards.first())
        for (m in game.monsters) {
            if (now - m.born < GotrConfig.ARRIVAL_TICKS) continue
            val idx = (0 until game.tier.size).firstOrNull { game.tier[it] > 0 }
            val hit = m.kind + 1
            if (idx != null) {
                damageBarrier(game, p, idx, GotrConfig.MONSTER_DAMAGE * hit)
            } else {
                game.power = (game.power - GotrConfig.GUARDIAN_DRAIN * hit).coerceAtLeast(0)
            }
        }
        if (!game.rumbled && game.percent() >= GotrConfig.RUMBLE_PERCENT) {
            game.rumbled = true
            for (i in game.tier.indices) if (game.tier[i] > 0) damageBarrier(game, p, i, GotrConfig.RUMBLE_DAMAGE)
            for (m in game.players) m.message("The temple rumbles as the rift glows more intensely!")
        }
    }

    private fun monsterKind(p: PlayerRef, rumbled: Boolean): Int {
        val roll = p.random(MONSTER_WEIGHT)
        return when {
            roll < (if (rumbled) RUMBLED_LEECH else LEECH_WEIGHT) -> 0
            roll < (if (rumbled) RUMBLED_GUARDIAN else GUARDIAN_WEIGHT) -> 1
            else -> 2
        }
    }

    private fun killMonster(game: GotrGame, m: Monster) {
        if (m.npc.isInWorld) m.npc.despawn()
        game.monsters.remove(m)
    }

    private fun fallGuardian(game: GotrGame, p: PlayerRef, g: RiftGuardian) {
        val at = g.npc.coord
        if (g.npc.isInWorld) g.npc.despawn()
        game.guards.remove(g)
        p.addLoc(Locs.GOTR_FALLEN_GUARDIAN, at, LOC_SHAPE, 0, GotrConfig.FALLEN_TICKS)
    }

    private fun damageBarrier(game: GotrGame, p: PlayerRef, idx: Int, amount: Int) {
        game.health[idx] -= amount
        if (game.health[idx] > 0) return
        game.tier[idx] = 0
        game.health[idx] = 0
        game.broken[idx] = true
        game.barrierNpc[idx]?.let { if (it.isInWorld) it.despawn() }
        game.barrierNpc[idx] = null
        setTileLoc(p, game, idx, Locs.GOTR_CELL_TILE_BROKEN_REPAIRABLE)
    }

    private fun endFight(game: GotrGame, p: PlayerRef, now: Long, won: Boolean) {
        game.won = won
        game.phase = GotrPhase.GRACE
        game.phaseEnd = now + GotrConfig.GRACE_TICKS
        for (m in game.monsters.toList()) killMonster(game, m)
        for (g in game.guards.toList()) fallGuardian(game, p, g)
        for (m in game.players) {
            m.message(if (won) WON_LINE else LOST_LINE)
            if (won) m.jingle(JINGLE_RIFT_CLOSED)
        }
    }

    private fun finish(game: GotrGame, p: PlayerRef, now: Long) {
        for (m in game.players.toList()) {
            val st = m[STATE] ?: continue
            if (game.won) award(m, st)
            removeGameItems(m)
        }
        for (i in game.tier.indices) clearTile(p, game, i)
        game.phase = GotrPhase.WAITING
        game.phaseEnd = now + GotrConfig.LOBBY_TICKS
        game.minePortal = null
        swapBarrier(p, game, Locs.GOTR_BARRIER)
        game.locked = false
    }

    /**
     * Wiki "Rewards": xp = Runecraft level x 45 with at least 300 energy; a point per 100 energy of each kind,
     * rounding up by chance.
     */
    private fun award(p: PlayerRef, st: GotrPlayer) {
        if (st.elemental + st.catalytic < GotrConfig.MIN_ENERGY) return p.message("You did not contribute enough energy to earn a reward.")
        p.addXp(Stats.RUNECRAFT, (p.baseLevel(Stats.RUNECRAFT) * GotrConfig.XP_PER_LEVEL).toDouble())
        val e = points(p, st.elemental)
        val c = points(p, st.catalytic)
        p.setSaved(POINTS_ELEMENTAL, (p.saved(POINTS_ELEMENTAL) ?: 0L) + e)
        p.setSaved(POINTS_CATALYTIC, (p.saved(POINTS_CATALYTIC) ?: 0L) + c)
        p.setSaved(GAMES, (p.saved(GAMES) ?: 0L) + 1)
        p.message(
            "You have been awarded $e elemental and $c catalytic points. Total: " +
                "${p.saved(POINTS_ELEMENTAL)} elemental, ${p.saved(POINTS_CATALYTIC)} catalytic.",
        )
    }

    private fun points(p: PlayerRef, energy: Int): Int {
        val chance = energy % ENERGY_PER_POINT
        return energy / ENERGY_PER_POINT + if (p.random(ENERGY_PER_POINT) < chance) 1 else 0
    }

    // ---- barrier and tile locs

    private fun swapBarrier(p: PlayerRef, game: GotrGame, id: Int) {
        val at = game.arena.at(GotrData.BARRIER)
        val old = findLoc(p, at, BARRIER_IDS) ?: return
        if (old.id != id) p.addLoc(id, at, old.shape, old.angle)
    }

    private fun findLoc(p: PlayerRef, at: CoordGrid, ids: Set<Int>): LocRef? =
        (0..LOC_SHAPES).firstNotNullOfOrNull { s -> p.locAt(at, s)?.takeIf { it.id in ids } }

    private fun setTileLoc(p: PlayerRef, game: GotrGame, idx: Int, id: Int) {
        val at = game.arena.at(GotrData.CELL_TILES[idx])
        val old = findLoc(p, at, TILE_IDS) ?: return
        if (old.id != id) p.addLoc(id, at, old.shape, old.angle)
    }

    private fun clearTile(p: PlayerRef, game: GotrGame, idx: Int) {
        game.barrierNpc[idx]?.let { if (it.isInWorld) it.despawn() }
        game.barrierNpc[idx] = null
        game.tier[idx] = 0
        game.health[idx] = 0
        game.broken[idx] = false
        if (!game.arena.tutorial || idx == GotrData.TUTORIAL_TILE) setTileLoc(p, game, idx, Locs.GOTR_CELL_TILE_INACTIVE)
    }

    // ---- hud

    private fun hud(p: PlayerRef, st: GotrPlayer, game: GotrGame, now: Long) {
        if (st.arena.tutorial || !p.isOpen(Interfaces.GOTR_HUD)) return
        val left = ((game.phaseEnd - now).coerceAtLeast(0) * TICK_MS / MS_PER_SECOND).toInt()
        val status =
            when (game.phase) {
                GotrPhase.WAITING -> "Next game in ${clock(left)}"
                GotrPhase.PREP -> "Rift opens in ${clock(left)}"
                GotrPhase.FIGHT -> "Rift open"
                GotrPhase.GRACE -> if (game.won) "Rift closed" else "Rift lost"
            }
        p.ifText(Components.Gotr_Hud.PORTAL_TIME, status)
        p.ifText(Components.Gotr_Hud.ENERGY_TITLE, "Power: ${game.percent()}%")
        p.ifText(Components.Gotr_Hud.ELEMENTAL_ENERGY_TEXT, "${game.elemental.name} (${st.elemental})")
        p.ifText(Components.Gotr_Hud.CATALYTIC_ENERGY_TEXT, "${game.catalytic.name} (${st.catalytic})")
        p.ifText(Components.Gotr_Hud.PORTAL_POSITION, game.minePortal?.first ?: "")
        p.ifText(Components.Gotr_Hud.GUARDIAN_LIMIT, "${game.guards.size}/${GotrConfig.GUARDIAN_CAP}")
    }

    private fun setEarned(p: PlayerRef, varbit: Int, value: Int) {
        if (p.varbitFits(varbit, value)) p.setVarbit(varbit, value)
    }

    private fun clock(seconds: Int): String = "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)

    // ---- energy

    /** Adds earned energy of each kind, capped at the Wiki's 1,000 per kind and 1,200 in all. */
    private fun addEnergy(p: PlayerRef, st: GotrPlayer, elemental: Int, catalytic: Int) {
        val room = GotrConfig.MAX_ENERGY_TOTAL - st.elemental - st.catalytic
        val e = elemental.coerceAtMost(GotrConfig.MAX_ENERGY_KIND - st.elemental).coerceAtMost(room).coerceAtLeast(0)
        val c = catalytic.coerceAtMost(GotrConfig.MAX_ENERGY_KIND - st.catalytic).coerceAtMost(room - e).coerceAtLeast(0)
        st.elemental += e
        st.catalytic += c
        setEarned(p, Varbits.GOTR_ELEMENTAL_EARNED_THIS_GAME, st.elemental)
        setEarned(p, Varbits.GOTR_CATALYTIC_EARNED_THIS_GAME, st.catalytic)
        if ((e < elemental || c < catalytic) && !st.attunedWarned) {
            st.attunedWarned = true
            p.message("You are fully attuned with runic energy and cannot earn anymore until the rift has been closed.")
        }
    }

    // ---- mining (GR7)

    /**
     * Guardian parts, remains and large remains give fragments, the huge remains and fallen guardians give guardian
     * essence
     * (Wiki "Guardian fragments", "Huge guardian remains", "Fallen guardian"): 5 Mining xp a roll, the rock never
     * runs out and the
     * pickaxe's cycle is the roll time (Wiki "time = NA"). OPEN-GR11: no doubling items, no level scaling of the
     * amount.
     */
    private fun PlayerScript.mineRemains(loc: LocRef, low: Int, high: Int, fragments: Boolean) {
        val p = player
        val st = state(p) ?: return p.stopInteraction()
        val tool = Skilling.bestTool(p, Stats.MINING, pickLevels)
        if (tool < 0) {
            p.message("You need a pickaxe to mine this rock.")
            return p.stopInteraction()
        }
        val pick = PICKAXES.getValue(tool)
        keepInteraction()
        val key = loc.coord.packed.toLong()
        val now = p.tick
        if (st.mineKey != key) {
            st.mineKey = key
            st.mineNext = now + pick.ticks
            p.anim(pick.anim)
            return
        }
        if (now < st.mineNext) return
        st.mineNext = now + pick.ticks
        p.anim(pick.anim)
        val product = if (fragments) Objs.GOTR_GUARDIAN_FRAGMENT else Objs.GOTR_GUARDIAN_ESSENCE
        val amount = low + p.random(high - low + 1)
        if (!GotrAltars.give(p, product, amount)) {
            p.message("Your inventory is too full to hold any more ${p.objName(product).lowercase()}.")
            return p.stopInteraction()
        }
        if (fragments) {
            st.fragmentsMined += amount
            if (state(p)?.arena?.tutorial == true) tutorialFragments(p, amount)
        }
        if (!fragments || st.fragmentsMined - amount < GotrConfig.FRAGMENT_XP_CAP) p.addXp(Stats.MINING, GotrConfig.MINING_XP)
    }

    private fun tutorialFragments(p: PlayerRef, amount: Int) {
        val n = (p.saved(T_FRAGMENTS) ?: 0L) + amount
        p.setSaved(T_FRAGMENTS, n)
    }

    // ---- workbench

    /**
     * Wiki "Guardian essence": a fragment and the workbench's pure essence become guardian essence, 1 tick each,
     * Crafting xp level/10
     * within 1..5.
     */
    private suspend fun PlayerScript.workbench(p: PlayerRef) {
        p.stopInteraction()
        state(p) ?: return
        var made = 0
        while (p.inv(Invs.INV).total(Objs.GOTR_GUARDIAN_FRAGMENT) > 0) {
            val ok =
                p.transaction("gotr workbench") {
                    remove(Invs.INV, Objs.GOTR_GUARDIAN_FRAGMENT, 1) && add(Invs.INV, Objs.GOTR_GUARDIAN_ESSENCE, 1)
                }
            if (!ok) {
                p.message("You don't have enough inventory space.")
                break
            }
            p.addXp(Stats.CRAFTING, (p.level(Stats.CRAFTING) / CRAFTING_DIVISOR).coerceIn(1, MAX_BENCH_XP).toDouble())
            made++
            delay(1)
        }
        val none = p.inv(Invs.INV).total(Objs.GOTR_GUARDIAN_FRAGMENT) == 0L
        if (made == 0 && none) p.message("You need guardian fragments to make guardian essence.")
        if (made > 0 && state(p)?.arena?.tutorial == true) p.setSaved(T_ESSENCE, 1L)
    }

    // ---- cells

    private fun takeUncharged(p: PlayerRef, want: Int) {
        p.stopInteraction()
        state(p) ?: return
        val have = p.inv(Invs.INV).total(Objs.GOTR_CELL_UNCHARGED).toInt()
        val n = want.coerceAtMost(GotrConfig.UNCHARGED_CAP - have)
        if (n <= 0) return p.message("You cannot carry more than ${GotrConfig.UNCHARGED_CAP} uncharged cells.")
        if (!GotrAltars.give(p, Objs.GOTR_CELL_UNCHARGED, n)) p.message("You don't have enough inventory space.")
    }

    private fun takeWeakCell(p: PlayerRef) {
        p.stopInteraction()
        val st = state(p) ?: return
        if (GotrAltars.heldCell(p) != null) return p.message("You can only carry one charged cell at a time.")
        if (GotrAltars.give(p, Objs.GOTR_CELL_TIER1, 1)) {
            if (st.arena.tutorial) p.setSaved(T_CELL, 1L)
        } else {
            p.message("You don't have enough inventory space.")
        }
    }

    private fun takeChisel(p: PlayerRef) {
        p.stopInteraction()
        state(p) ?: return
        if (p.inv(Invs.INV).total(Objs.CHISEL) > 0) return p.message("You already have a chisel.")
        if (!GotrAltars.give(p, Objs.CHISEL, 1)) p.message("You don't have enough inventory space.")
    }

    /**
     * Wiki "Charging cells": place on an empty tile, strengthen one tier with a better cell, or recharge with an
     * equal or lesser one.
     */
    private fun placeCell(p: PlayerRef, loc: LocRef) {
        p.stopInteraction()
        val st = state(p) ?: return
        val game = games.getValue(st.arena)
        val idx = GotrData.CELL_TILES.indexOfFirst { st.arena.at(it) == loc.coord }
        if (idx < 0) return
        val cell = GotrAltars.heldCell(p) ?: return p.message("You need a charged cell to place here.")
        val have = game.tier[idx]
        val gained: Int
        when {
            have == 0 -> {
                if (!p.transaction("gotr cell") { remove(Invs.INV, cell.obj, 1) }) return
                game.tier[idx] = cell.level
                game.health[idx] = GotrConfig.BARRIER_HEALTH
                game.broken[idx] = false
                setTileLoc(p, game, idx, cell.tileLoc)
                game.barrierNpc[idx]?.let { if (it.isInWorld) it.despawn() }
                game.barrierNpc[idx] = p.spawnNpc(cell.barrierNpc, loc.coord, 0)
                gained = cell.place
                p.addXp(Stats.RUNECRAFT, cell.xp)
                if (st.arena.tutorial) p.setSaved(T_BARRIER, 1L)
                addEnergy(p, st, gained, gained)
            }

            cell.level > have -> {
                if (!p.transaction("gotr cell") { remove(Invs.INV, cell.obj, 1) }) return
                val next = CellTier.entries[have]
                game.tier[idx] = next.level
                game.health[idx] = GotrConfig.BARRIER_HEALTH
                setTileLoc(p, game, idx, next.tileLoc)
                game.barrierNpc[idx]?.let { if (it.isInWorld) it.despawn() }
                game.barrierNpc[idx] = p.spawnNpc(next.barrierNpc, loc.coord, 0)
                p.addXp(Stats.RUNECRAFT, cell.xp)
                if (st.arena.tutorial) p.setSaved(T_STRENGTHENED, 1L)
                addEnergy(p, st, cell.strengthen, cell.strengthen)
            }

            else -> {
                val capped = st.rechargeEnergy >= GotrConfig.RECHARGE_ENERGY_CAP || st.rechargeXp >= GotrConfig.RECHARGE_XP_CAP
                if (capped) return p.message("This barrier cannot absorb more of your energy this game.")
                if (!p.transaction("gotr cell") { remove(Invs.INV, cell.obj, 1) }) return
                game.health[idx] = (game.health[idx] + cell.restore).coerceAtMost(GotrConfig.BARRIER_HEALTH)
                st.rechargeEnergy += cell.recharge
                st.rechargeXp += cell.xp
                p.addXp(Stats.RUNECRAFT, cell.xp)
                if (st.arena.tutorial) p.setSaved(T_BARRIER, 1L)
                addEnergy(p, st, cell.recharge, cell.recharge)
            }
        }
    }

    private fun repairTile(p: PlayerRef, loc: LocRef) {
        p.stopInteraction()
        val st = state(p) ?: return
        val game = games.getValue(st.arena)
        val idx = GotrData.CELL_TILES.indexOfFirst { st.arena.at(it) == loc.coord }
        if (idx < 0) return
        val paid = p.transaction("gotr repair") { remove(Invs.INV, Objs.GOTR_GUARDIAN_FRAGMENT, GotrConfig.REPAIR_FRAGMENTS) }
        if (!paid) return p.message("You need ${GotrConfig.REPAIR_FRAGMENTS} guardian fragments to repair the cell tile.")
        game.broken[idx] = false
        setTileLoc(p, game, idx, Locs.GOTR_CELL_TILE_INACTIVE)
        addEnergy(p, st, GotrConfig.REPAIR_ENERGY, GotrConfig.REPAIR_ENERGY)
    }

    /**
     * Wiki "Rift guardian (combatant)": a chisel and a charged cell on an essence pile make a guardian of the cell's
     * tier (tutorial: no
     * cap).
     */
    private fun assemble(p: PlayerRef, elemental: Boolean) {
        p.stopInteraction()
        val st = state(p) ?: return
        val game = games.getValue(st.arena)
        if (p.inv(Invs.INV).total(Objs.CHISEL) == 0L) return p.message("You need a chisel to shape the essence.")
        val cell = GotrAltars.heldCell(p) ?: return p.message("You need a charged cell to infuse the essence.")
        if (game.guards.size >= GotrConfig.GUARDIAN_CAP) return p.message("There are already ${GotrConfig.GUARDIAN_CAP} rift guardians.")
        if (!p.transaction("gotr guardian") { remove(Invs.INV, cell.obj, 1) }) return
        p.anim(Seqs.GOTR_CHISEL_GUARDIAN)
        val pile = if (elemental) GotrData.PILE_ELEMENTAL else GotrData.PILE_CATALYTIC
        val type = if (elemental) cell.elementalGuardian else cell.catalyticGuardian
        val npc = p.spawnNpc(type, st.arena.at(pile.translate(0, GUARDIAN_OFFSET)), 0)
        npc.walkTo(st.arena.at(GotrData.GREAT_GUARDIAN.translate(if (elemental) GUARD_SPREAD else -GUARD_SPREAD, GUARD_AHEAD)))
        game.guards += RiftGuardian(npc, cell, elemental)
        p.addXp(Stats.RUNECRAFT, cell.xp)
        p.addXp(Stats.CRAFTING, GUARDIAN_CRAFTING_XP)
        if (elemental) addEnergy(p, st, cell.guardianEnergy, 0) else addEnergy(p, st, 0, cell.guardianEnergy)
        if (st.arena.tutorial) {
            p.setSaved(if (elemental) T_ELEMENTAL else T_CATALYTIC, 1L)
            if (cell.level >= CellTier.MEDIUM.level) p.setSaved(T_MEDIUM, 1L)
        }
    }

    // ---- the Great Guardian

    /**
     * GR9: every guardian stone is +1 charge, 2 energy of its kind, 5 Runecraft xp and 1% run energy (Wiki "Gaining
     * energy"/"Imbuing
     * essence").
     */
    private fun powerUp(p: PlayerRef, gg: NpcRef) {
        p.stopInteraction()
        val st = state(p) ?: return
        val game = games.getValue(st.arena)
        val inv = p.inv(Invs.INV)
        val elemental = inv.total(Objs.GOTR_GUARDIAN_STONE_ELEMENTAL).toInt()
        val catalytic = inv.total(Objs.GOTR_GUARDIAN_STONE_CATALYTIC).toInt()
        val polyE = inv.total(Objs.GOTR_GUARDIAN_STONE_POLYELEMENTAL).toInt()
        val polyC = inv.total(Objs.GOTR_GUARDIAN_STONE_POLYCATALYTIC).toInt()
        val total = elemental + catalytic + polyE + polyC
        if (total == 0) {
            val line =
                if (game.percent() >= PERCENT_FULL &&
                    !st.arena.tutorial
                ) {
                    "Thanks, adventurer. I am now fully powered up."
                } else {
                    TOO_LOW
                }
            return gg.say(line)
        }
        val ok =
            p.transaction("gotr power") {
                (elemental == 0 || remove(Invs.INV, Objs.GOTR_GUARDIAN_STONE_ELEMENTAL, elemental)) &&
                    (catalytic == 0 || remove(Invs.INV, Objs.GOTR_GUARDIAN_STONE_CATALYTIC, catalytic)) &&
                    (polyE == 0 || remove(Invs.INV, Objs.GOTR_GUARDIAN_STONE_POLYELEMENTAL, polyE)) &&
                    (polyC == 0 || remove(Invs.INV, Objs.GOTR_GUARDIAN_STONE_POLYCATALYTIC, polyC))
            }
        if (!ok) return
        val e = elemental * GotrConfig.STONE_ENERGY + polyE * GotrConfig.POLY_STONE_ENERGY
        val c = catalytic * GotrConfig.STONE_ENERGY + polyC * GotrConfig.POLY_STONE_ENERGY
        addEnergy(p, st, e, c)
        p.addXp(Stats.RUNECRAFT, total * GotrConfig.STONE_XP)
        p.runEnergy = (p.runEnergy + total * RUN_PER_STONE).coerceAtMost(MAX_RUN)
        if (st.arena.tutorial) {
            val n = (p.saved(T_POWERS) ?: 0L) + 1
            p.setSaved(T_POWERS, n)
            gg.say(if (n >= TUTORIAL_FEEDS) TUTORIAL_CLOSE else TUTORIAL_MORE)
        } else {
            game.power = (game.power + total).coerceAtMost(game.maxPower)
            gg.say("The stones have fueled me.")
        }
    }

    /**
     * Wiki "Transcript:Apprentice Tamara": she can lock the barrier once the first stage of a game begins (not on
     * themed worlds; ours is
     * not one).
     */
    private suspend fun PlayerScript.tamara(tamara: NpcRef) {
        player.stopInteraction()
        val game = games.getValue(Arena.MAIN)
        chatPlayer("I was wondering if you could do something for me while I work to close the rift.")
        chatNpc(tamara, "What is it? The rift will open soon and we should all be preparing to close it.")
        if (!game.locked) {
            chatPlayer("I was wondering if you could close the barrier so that other adventurers don't interfere with us closing the rift.")
            chatNpc(tamara, "Why would you want to do that? Wouldn't more help be appreciated?")
            chatPlayer(
                "I believe that we already have what it takes to close the rift and I wouldn't want " +
                    "any other adventurers getting in the way.",
            )
            chatNpc(tamara, "I'll close the barrier once the rift opens. If you change your mind before the rift opens I can revert this.")
            game.locked = true
        } else {
            chatPlayer("I've changed my mind, we should allow other adventurers to join us in closing the rift.")
            chatNpc(tamara, "I'll no longer close the barrier once the rift opens.")
            game.locked = false
        }
    }

    // ---- portal guardians and altars

    /**
     * Entering an active portal guardian (or any one with its portal talisman) leads to the real altar (Wiki "Imbuing
     * essence").
     */
    private fun enterPortal(p: PlayerRef, altar: GotrAltar, talisman: Boolean) {
        p.stopInteraction()
        val st = state(p) ?: return p.message("Nothing interesting happens.")
        val game = games.getValue(st.arena)
        val temple = RunecraftData.TEMPLES.first { it.name == altar.name }
        val gate = TEMPLE_QUESTS[altar.name]
        if (gate != null && !gate.done(p)) return p.message("You need to have completed ${gate.name} to enter this altar.")
        if (!talisman && !activePortal(p, game, altar)) return p.message("This portal is not active.")
        if (STONES.sumOf { inv(p, it) } > 0) {
            p.say("Human, I need the power of the guardian stones.")
            return p.message("You should feed the guardian stones to the Great Guardian before making more of them.")
        }
        st.back = p.coord
        TempleEntry.enter(p, temple)
    }

    private fun inv(p: PlayerRef, obj: Int): Long = p.inv(Invs.INV).total(obj)

    private fun activePortal(p: PlayerRef, game: GotrGame, altar: GotrAltar): Boolean {
        if (game.arena.tutorial) {
            val powers = p.saved(T_POWERS) ?: 0L
            return altar.name == (if (powers == 0L) "mind" else "water")
        }
        return altar === game.elemental || altar === game.catalytic
    }

    // ---- shortcuts

    private fun climb(p: PlayerRef, loc: LocRef) {
        p.stopInteraction()
        if (p.level(Stats.AGILITY) < GotrConfig.SHORTCUT_AGILITY) {
            return p.message("You need an Agility level of ${GotrConfig.SHORTCUT_AGILITY} to climb the rubble.")
        }
        val arena = Arena.of(loc.coord) ?: return
        val west = p.coord.x <= arena.at(GotrData.EAST_RUBBLE).x
        p.teleport(if (west) landing(p, arena.at(GotrData.EAST_SHELF)) else landing(p, arena.at(GotrData.EAST_RUBBLE.translate(-1, 0))))
    }

    private fun minePortalIn(p: PlayerRef) {
        p.stopInteraction()
        val st = state(p) ?: return
        p.teleport(landing(p, st.arena.at(GotrData.WEST_SHELF)))
    }

    private fun minePortalOut(p: PlayerRef) {
        p.stopInteraction()
        val st = state(p) ?: return
        p.teleport(landing(p, st.arena.at(GotrData.GREAT_GUARDIAN.translate(0, -GUARD_AHEAD))))
    }

    internal companion object {
        val PARTS = intArrayOf(Locs.GOTR_ESSENCE_TIER_1, Locs.GOTR_ESSENCE_TIER_1B)
        val REMAINS = intArrayOf(Locs.GOTR_ESSENCE_TIER_1_LARGE, Locs.GOTR_ESSENCE_TIER_1_LARGE_B)
        val TILE_OPS =
            intArrayOf(
                Locs.GOTR_CELL_TILE_INACTIVE,
                Locs.GOTR_CELL_TILE_TIER1,
                Locs.GOTR_CELL_TILE_TIER2,
                Locs.GOTR_CELL_TILE_TIER3,
                Locs.GOTR_CELL_TILE_TIER4,
            )
        val STONES =
            intArrayOf(
                Objs.GOTR_GUARDIAN_STONE_ELEMENTAL,
                Objs.GOTR_GUARDIAN_STONE_CATALYTIC,
                Objs.GOTR_GUARDIAN_STONE_POLYELEMENTAL,
                Objs.GOTR_GUARDIAN_STONE_POLYCATALYTIC,
            )

        /** Great Guardian lines (Wiki "Transcript:The Great Guardian" and the quest's "Powering up the Great Guardian"). */
        const val TOO_LOW = "My power level is too low. Bring me guardian stones."
        const val WON_LINE = "Humans, I now have the power to close the rift. Thank you, brave ones."
        const val LOST_LINE = "The Great Guardian has fallen. The rift is lost."
        const val TUTORIAL_CLOSE = "Humans, I now have the power to close the rift. Brace yourselves!"
        const val TUTORIAL_MORE = "Human, the stones have fueled me, but more are needed."

        val TICK = TimerId("gotr.tick")
        val STATE = AttrKey<GotrPlayer>("gotr.state")

        const val OP_PASS = 1
        const val OP_PEEK = 2
        const val OP_QUICK_PASS = 3
        const val OP_EFFECTS = 4
        const val OP_MINE = 1
        const val OP_WORKBENCH = 1
        const val OP_TAKE_TEN = 1
        const val OP_TAKE_ONE = 2
        const val OP_TAKE_CELL = 1
        const val OP_TAKE_CHISEL = 2
        const val OP_ASSEMBLE = 1
        const val OP_PLACE = 1
        const val OP_POWER_UP = 1

        /**
         * Account values: points for the Rewards Guardian (OPEN-GR10: the cache varps
         * `gotr_perm_0`/`gotr_rewards_perm` have an unknown
         * layout) and games won.
         */
        const val POINTS_ELEMENTAL = "gotr.points.elemental"
        const val POINTS_CATALYTIC = "gotr.points.catalytic"
        const val GAMES = "gotr.games"
        const val SEARCHES = "gotr.searches"

        /** Account values the quest tutorial reads (docs/mechanics/quest-temple-of-the-eye.md TE6). */
        const val T_CELL = "gotr.t.cell"
        const val T_BARRIER = "gotr.t.barrier"
        const val T_ELEMENTAL = "gotr.t.elemental"
        const val T_CATALYTIC = "gotr.t.catalytic"
        const val T_FRAGMENTS = "gotr.t.fragments"
        const val T_ESSENCE = "gotr.t.essence"
        const val T_IMBUES = "gotr.t.imbues"
        const val T_POWERS = "gotr.t.powers"
        const val T_MEDIUM = "gotr.t.medium"
        const val T_STRENGTHENED = "gotr.t.strengthened"

        /**
         * The tutorial's Great Guardian closes the rift on the third feeding (Wiki quest walkthrough: Mind, Water,
         * Water).
         */
        const val TUTORIAL_FEEDS = 3L

        val TEMPLE_QUESTS: Map<String, QuestGate> =
            mapOf(
                "cosmic" to RunecraftQuests.LOST_CITY,
                "law" to RunecraftQuests.TROLL_STRONGHOLD,
                "death" to RunecraftQuests.MOURNINGS_END_II,
                "blood" to RunecraftQuests.SINS_OF_THE_FATHER,
            )

        /**
         * Wiki NPC pages (maps): Felix 3619,9475 (tutorial: the table beside the cells), Tamara 3614,9489, Cordelia
         * 3617,9491 r=4.
         */
        val FELIX_MAIN = CoordGrid(3619, 9475, 0)
        val FELIX_TUTORIAL = CoordGrid(3617, 9488, 0)
        val TAMARA = CoordGrid(3614, 9489, 0)
        val CORDELIA = CoordGrid(3617, 9491, 0)
        const val FELIX_WANDER = 2
        const val TAMARA_WANDER = 1
        const val CORDELIA_WANDER = 4

        /** The Wizards' Tower basement portal (cache `tote_portal_to_gotr_parent` placed at 3104,9574). */
        val WIZARDS_TOWER_PORTAL = CoordGrid(3104, 9573, 0)

        val BARRIER_IDS = setOf(Locs.GOTR_BARRIER, Locs.GOTR_BARRIER_CLOSED, Locs.GOTR_BARRIER_NOENTRY)
        val TILE_IDS =
            setOf(
                Locs.GOTR_CELL_TILE_INACTIVE,
                Locs.GOTR_CELL_TILE_INACTIVE_NOOP,
                Locs.GOTR_CELL_TILE_TIER1,
                Locs.GOTR_CELL_TILE_TIER2,
                Locs.GOTR_CELL_TILE_TIER3,
                Locs.GOTR_CELL_TILE_TIER4,
                Locs.GOTR_CELL_TILE_BROKEN_REPAIRABLE,
                Locs.GOTR_CELL_TILE_BROKEN,
            )

        /** Monsters by kind: leech, abyssal guardian, walker (cache npcs). */
        val MONSTER_NPCS = intArrayOf(Npcs.GOTR_ABYSSAL_LEECH, Npcs.GOTR_ABYSSAL_PYRAMID, Npcs.GOTR_ABYSSAL_WALKER)

        /** Cache jingle played when the rift closes. */
        val JINGLE_RIFT_CLOSED = tp.symbols.Jingles.QUEST_COMPLETE_1

        /**
         * Temple of the Eye stages (varbit 13738) in which the tower portal leads to the tutorial floor: in the
         * temple up to the closed
         * rift.
         */
        const val TUTORIAL_FROM = 91
        const val TUTORIAL_TO = 129

        const val LOC_SHAPE = 10
        const val LOC_SHAPES = 22
        const val LANDING_RADIUS = 4
        const val HUD_TICKS = 5L
        const val TICK_MS = 600L
        const val MS_PER_SECOND = 1000L
        const val SECONDS_PER_MINUTE = 60
        const val PERCENT = 100
        const val PERCENT_FULL = 100
        const val ENERGY_PER_POINT = 100
        const val RIFT_SPREAD = 5
        const val MONSTER_WEIGHT = 10
        const val LEECH_WEIGHT = 6
        const val GUARDIAN_WEIGHT = 9
        const val RUMBLED_LEECH = 3
        const val RUMBLED_GUARDIAN = 8
        const val GUARDIAN_OFFSET = 3
        const val GUARD_SPREAD = 3
        const val GUARD_AHEAD = 6
        const val CRAFTING_DIVISOR = 10
        const val MAX_BENCH_XP = 5
        const val GUARDIAN_CRAFTING_XP = 80.0
        const val RUN_PER_STONE = 100
        const val MAX_RUN = 10_000
    }
}

/**
 * The minigame's altar side, used by the regular altar handlers in [Runecraft] (no world state: everything is on the
 * player).
 */
internal object GotrAltars {
    /** The highest charged cell in the backpack (the minigame lets a player hold one). */
    fun heldCell(p: PlayerRef): CellTier? = CellTier.entries.lastOrNull { p.inv(Invs.INV).total(it.obj) > 0 }

    fun give(p: PlayerRef, obj: Int, count: Int): Boolean = p.transaction("gotr") { add(Invs.INV, obj, count) }

    /** The altar's exit portal sends a minigame visitor back to the portal guardian. */
    fun returnFromAltar(p: PlayerRef): Boolean {
        val st = p[GuardiansOfTheRift.STATE] ?: return false
        val back = st.back ?: return false
        st.back = null
        p.teleport(back)
        return true
    }

    /**
     * Imbuing at an altar (Wiki "Imbuing essence", "Charging cells"): guardian essence becomes runes and guardian
     * stones (the
     * same Runecraft level as crafting them regularly), and an uncharged cell becomes the altar's cell. Returns
     * false when the
     * player is not a visitor or carries neither, so the regular crafting runs.
     */
    fun imbue(p: PlayerRef, t: Temple): Boolean {
        val st = p[GuardiansOfTheRift.STATE]?.takeIf { it.back != null } ?: return false
        val altar = GotrData.ALTARS.firstOrNull { it.name == t.name } ?: return false
        val inv = p.inv(Invs.INV)
        val essence = inv.total(Objs.GOTR_GUARDIAN_ESSENCE).toInt()
        val cells = inv.total(Objs.GOTR_CELL_UNCHARGED).toInt()
        if (essence == 0 && cells == 0) return false
        val level = p.level(Stats.RUNECRAFT)
        if (level < t.level) {
            p.message("You need a Runecraft level of ${t.level} to craft ${t.name} runes.")
            return true
        }
        val stone = if (altar.elemental) Objs.GOTR_GUARDIAN_STONE_ELEMENTAL else Objs.GOTR_GUARDIAN_STONE_CATALYTIC
        val runes = essence * t.runesPerEssence(level)
        val held = heldCell(p)
        val upgrade = cells > 0 && (held == null || held.level < altar.tier.level)
        val ok =
            p.transaction("gotr imbue") {
                (essence == 0 || swapEssence(this, t, essence, runes, stone)) &&
                    (!upgrade || swapCell(this, held, altar.tier))
            }
        if (!ok) {
            p.message("You don't have enough inventory space.")
            return true
        }
        p.anim(Seqs.HUMAN_RUNECRAFT)
        p.spotAnim(tp.symbols.SpotAnims.RUNECRAFTING)
        if (essence > 0) p.addXp(Stats.RUNECRAFT, t.xp * essence)
        if (upgrade) p.addXp(Stats.RUNECRAFT, CELL_XP)
        if (essence > 0) rollTalisman(p, st, altar, essence)
        if (st.arena.tutorial) p.setSaved(GuardiansOfTheRift.T_IMBUES, (p.saved(GuardiansOfTheRift.T_IMBUES) ?: 0L) + 1)
        return true
    }

    private fun swapEssence(tx: tp.plugin.ItemTx, t: Temple, essence: Int, runes: Int, stone: Int): Boolean =
        tx.remove(Invs.INV, Objs.GOTR_GUARDIAN_ESSENCE, essence) && tx.add(Invs.INV, t.rune, runes) && tx.add(Invs.INV, stone, essence)

    private fun swapCell(tx: tp.plugin.ItemTx, held: CellTier?, to: CellTier): Boolean =
        tx.remove(Invs.INV, Objs.GOTR_CELL_UNCHARGED, 1) && (held == null || tx.remove(Invs.INV, held.obj, 1)) &&
            tx.add(Invs.INV, to.obj, 1)

    /**
     * Wiki: 1/250 per guardian essence imbued until the player has a portal talisman (the tutorial always gives one
     * at the Water altar).
     */
    private fun rollTalisman(p: PlayerRef, st: GotrPlayer, altar: GotrAltar, essence: Int) {
        if (st.talismanGiven) return
        val tutorialGift = st.arena.tutorial && altar.name == "water"
        val lucky = tutorialGift || (0 until essence).any { p.random(GotrConfig.TALISMAN_ONE_IN) == 0 }
        if (lucky && give(p, altar.portalTalisman, 1)) {
            st.talismanGiven = true
            p.message("The essence has formed a strange talisman.")
        }
    }

    /**
     * Runecraft xp of imbuing an uncharged cell (Wiki "Weak cell": 10; OPEN-GR12: the other tiers are not listed, the
     * same is used).
     */
    private const val CELL_XP = 10.0
}
