package tp.content.skills

import tp.content.skills.VmGame.Companion.CAPPED
import tp.content.skills.VmGame.Companion.GAME
import tp.content.skills.VmGame.Companion.LAST_SWING
import tp.content.skills.VmGame.Companion.POINTS
import tp.content.skills.VmGame.Companion.WATER
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DamageRequest
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.WorldRef
import tp.plugin.chatNpc
import tp.plugin.choice
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid
import tp.content.skills.VolcanicMineData as D

/**
 * The Volcanic Mine (docs/mechanics/volcanic-mine.md): Petrified Pete's entry and shop, the shared preparation countdown and ten-minute
 * game of the world, the boulders and their fragments, the gas chambers, vents and stability, the heat-proof vessel and the
 * payout on leaving.
 * Lava beasts, meteorites, earthquakes, stalactites, lava falls and the gas holes are not implemented (they need combat or damage rules).
 */
public class VolcanicMine : ContentPlugin {
    private var pickLevels: Map<Int, Int> = emptyMap()
    private var named: List<Pair<VmReward, Int>> = emptyList()

    override fun ContentScope.register() {
        onWorldStart {
            pickLevels = PICKAXES.keys.associateWith { objParamInt(it, Skilling.PARAM_LEVEL_REQUIRE) ?: 1 }
            named =
                D.NAMED_REWARDS.mapNotNull { (name, cost) ->
                    objIdsNamed(name).minOrNull()?.let { VmReward(name, it, cost, false) to cost }
                }
            addLoc(Locs.FOSSIL_MINING_START_EXIT, D.EXIT_AT, EXIT_SHAPE, 0)
            if (System.getProperty("tp.worldSpawns") != "false") spawnNpc(Npcs.FOSSIL_MINEGUARD, D.PETE_AT, 0)
        }
        onWorldTick { drive(this) }
        onLogin { if (inMine(coord)) teleport(D.OUTSIDE) }
        onLogout { leave(this, payout = false) }
        onOpNpc(Npcs.FOSSIL_MINEGUARD, OP_FIRST) { npc -> talk(npc) }
        onOpNpc(Npcs.FOSSIL_MINEGUARD, OP_SECOND) { pay(player) }
        onOpNpc(Npcs.FOSSIL_MINEGUARD, OP_THIRD) { shop(player) }
        onOpLoc(Locs.FOSSIL_VOLCANO_ENTRANCE, OP_FIRST) { pay(player) }
        onOpLoc(Locs.FOSSIL_VOLCANO_STAIRCASE_TOP, OP_FIRST) { loc -> descend(player, loc) }
        onOpLoc(Locs.FOSSIL_VOLCANO_STAIRCASE_BOTTOM, OP_FIRST) { loc -> ascend(player, loc) }
        onOpLoc(Locs.FOSSIL_MINING_START_EXIT, OP_FIRST) { leave(player, payout = true) }
        for (stage in D.STAGE_LOCS) onOpLoc(stage, OP_FIRST) { loc -> mineBoulder(loc) }
        onOpLoc(Locs.FOSSIL_VOLCANO_CHAMBER, OP_FIRST) { loc -> block(player, loc) }
        onOpLoc(Locs.FOSSIL_VOLCANO_CHAMBER_BLOCKED, OP_FIRST) { loc -> unblock(player, loc) }
        onOpLoc(Locs.FOSSIL_VOLCANO_LARGE_ROCK, OP_FIRST) { loc -> takeRock(player, loc) }
        onOpLoc(Locs.FOSSIL_VOLCANO_HOTSPOT, OP_FIRST) { loc -> checkVent(player, loc) }
        onItemOp(Objs.FOSSIL_MINE_WATER_CONTAINER, "Throw-water") { throwWater(this) }
        onItemOp(Objs.FOSSIL_MINE_WATER_CONTAINER, "Throw-water direction") { throwWater(this) }
        onItemOp(Objs.FOSSIL_MINE_WATER_CONTAINER, "Check-level") { message("The vessel has ${this[WATER] ?: 0} uses of water left.") }
    }

    // ---- Pete

    private suspend fun PlayerScript.talk(npc: NpcRef) {
        chatNpc(npc, "I guard the mine. What do you want?")
        when (choice("Select an Option", "Pay to enter the mine.", "Show me your wares.", "Nothing.")) {
            1 -> pay(player)
            2 -> shop(player)
        }
    }

    private fun permanent(p: PlayerRef): Boolean = (p.saved(PERMANENT_KEY) ?: 0L) != 0L

    private fun points(p: PlayerRef): Long = p.saved(REWARD_KEY) ?: 0L

    private fun inMine(c: CoordGrid): Boolean = c.x in D.MINE_X && c.z in D.MINE_Z

    /** Entry (Wiki "Volcanic Mine"): 50 Mining, 30 numulite or the permanent access, and no joining once the game has started. */
    private fun pay(p: PlayerRef) {
        if (p[GAME] != null) return
        if (p.baseLevel(Stats.MINING) <
            D.MINING_LEVEL
        ) {
            return p.message("You need a Mining level of ${D.MINING_LEVEL} to enter the Volcanic Mine.")
        }
        val g = p.worldAttr(GAME_STATE)
        if (g != null && g.running) return p.message("A game is already under way; wait for it to finish.")
        if (!permanent(p) && !p.transaction("volcanic mine fee") { remove(Invs.INV, Objs.FOSSIL_NUMULITE, D.ENTRY_FEE) }) {
            return p.message("You need ${D.ENTRY_FEE} numulite to enter the Volcanic Mine.")
        }
        if (!p.transaction("volcanic mine vessel") { addOrDrop(Invs.INV, Objs.FOSSIL_MINE_WATER_CONTAINER, 1) }) return
        val running = g ?: VmGame(p.tick + D.PREPARATION_TICKS).also { p.setWorldAttr(GAME_STATE, it) }
        join(p, running)
    }

    private fun join(p: PlayerRef, g: VmGame) {
        g.players += p
        p[GAME] = g
        p[POINTS] = 0
        p[CAPPED] = 0
        p[WATER] = if (p.varbit(Varbits.FOSSIL_MINE_UNLOCKED_MOREWATER) == 1) D.WATER_USES_UPGRADED else D.WATER_USES
        p.teleport(D.LOBBY)
        p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.FOSSIL_VOLCANIC_MINE)
        p.setVarbit(Varbits.FOSSIL_MINE_PROGRESS_STATUS, STATUS_PREPARING)
        p.setVarbit(Varbits.FOSSIL_MINE_TEMP_PTS, 0)
        p.setVarbit(Varbits.FOSSIL_MINE_TIME_REMAINING, (g.startsAt - p.tick).toInt())
        syncAll(g)
    }

    /** Leaves the game: alive by the exit pays points and xp (Wiki "Volcanic Mine" > Rewards); anything else loses them. */
    private fun leave(p: PlayerRef, payout: Boolean) {
        val g = p[GAME] ?: return
        val earned = p[POINTS] ?: 0
        g.players.remove(p)
        p[GAME] = null
        p[POINTS] = null
        p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
        clearItems(p)
        p.setVarbit(Varbits.FOSSIL_MINE_TEMP_PTS, 0)
        p.setVarbit(Varbits.FOSSIL_MINE_PROGRESS_STATUS, 0)
        if (inMine(p.coord)) p.teleport(D.OUTSIDE)
        if (payout && earned > 0) {
            val total = (points(p) + earned).coerceAtMost(D.POINTS_CAP.toLong())
            p.setSaved(REWARD_KEY, total)
            MiningOutfit.addXp(p, earned * D.XP_PER_POINT)
            p.message("Petrified Pete gives you $earned points; you now have $total.")
        }
        if (g.players.isNotEmpty()) syncAll(g)
    }

    /** Fragments, the large rock and the vessel stay in the mine (Wiki "Large rock", "Volcanic Mine" > Rewards). */
    private fun clearItems(p: PlayerRef) {
        val items = D.FRAGMENTS.map { it.obj } + listOf(Objs.FOSSIL_MINE_LARGE_ROCK, Objs.FOSSIL_MINE_WATER_CONTAINER)
        p.transaction("volcanic mine clear") {
            for (obj in items) {
                val held = p.inv(Invs.INV).total(obj)
                if (held > 0) remove(Invs.INV, obj, held.toInt())
                val worn = p.inv(Invs.WORN).total(obj)
                if (worn > 0) remove(Invs.WORN, obj, worn.toInt())
            }
            true
        }
    }

    // ---- the stairs

    private fun descend(p: PlayerRef, loc: LocRef) {
        val g = p[GAME] ?: return
        if (!g.running) return p.message("The volcano has not rumbled yet; wait for the game to start.")
        p.teleport(CoordGrid(loc.coord.x, loc.coord.z + STAIR_OFFSET, 1))
    }

    private fun ascend(p: PlayerRef, loc: LocRef) {
        if (p[GAME] == null) return
        p.teleport(CoordGrid(loc.coord.x, loc.coord.z + STAIR_OFFSET, D.LOBBY.level))
    }

    // ---- the world tick

    private fun drive(world: WorldRef) {
        val g = world.worldAttr(GAME_STATE) ?: return
        val now = world.tick
        val gone = g.players.filter { !inMine(it.coord) }
        if (gone.isNotEmpty()) {
            g.players.removeAll(gone.toSet())
            for (p in gone) forget(p, g)
        }
        if (g.players.isEmpty() && !g.running) return world.setWorldAttr(GAME_STATE, null)
        if (!g.running) {
            if (now >= g.startsAt) start(world, g, now)
            return
        }
        if (now >= g.endsAt || g.players.isEmpty()) return finish(world, g)
        cycle(world, g, now)
    }

    /** A player who left the area without the exit: no payout, and their items are gone. */
    private fun forget(p: PlayerRef, g: VmGame) {
        p[GAME] = null
        p[POINTS] = null
        p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
        clearItems(p)
        p.setVarbit(Varbits.FOSSIL_MINE_PROGRESS_STATUS, 0)
        p.setVarbit(Varbits.FOSSIL_MINE_TEMP_PTS, 0)
        if (g.players.size > 1) syncAll(g)
    }

    private fun start(world: WorldRef, g: VmGame, now: Long) {
        g.running = true
        g.starters = g.players.size
        g.shiftAt = now + D.SHIFT_TICKS
        for (c in 0 until VmGame.CHAMBERS) shift(world, g, c)
        val count = D.boulders(g.starters)
        val hp = (D.STAGE_HP_PER_PLAYER * g.starters + count - 1) / count
        for (channel in 0 until count) {
            val b = VmBoulder(channel).also { it.hp = hp }
            g.boulders += b
            world.addLoc(D.STAGE_LOCS[0], b.at(), BOULDER_SHAPE, 0)
        }
        for (p in g.players) {
            p.setVarbit(Varbits.FOSSIL_MINE_PROGRESS_STATUS, STATUS_RUNNING)
            p.setVarbit(Varbits.FOSSIL_MINE_TIME_REMAINING, D.GAME_TICKS)
            p.message("The volcano rumbles.")
        }
        syncAll(g)
    }

    /** Sets chamber [c] to a random pressure and blocked state at the start and at every shift (OPEN-VM5) and shows the matching loc. */
    private fun shift(world: WorldRef, g: VmGame, c: Int) {
        val rng = g.players.first()
        g.pressure[c] = rng.random(PERCENT_RANGE)
        g.blocked[c] = rng.random(2) == 0
        g.revealed[c] = VmGame.UNKNOWN
        world.addLoc(
            if (g.blocked[c]) Locs.FOSSIL_VOLCANO_CHAMBER_BLOCKED else Locs.FOSSIL_VOLCANO_CHAMBER,
            D.CHAMBERS[c],
            CHAMBER_SHAPE,
            0,
        )
    }

    private fun cycle(world: WorldRef, g: VmGame, now: Long) {
        val elapsed = now - g.startsAt
        if (g.closing(now)) {
            if (!g.closed) {
                g.closed = true
                g.stability = D.CLOSING_STABILITY
                syncAll(g)
            }
        } else {
            if (elapsed % D.PRESSURE_STEP_TICKS == 0L) {
                for (c in 0 until VmGame.CHAMBERS) g.pressure[c] = (g.pressure[c] + if (g.blocked[c]) 1 else -1).coerceIn(0, PERCENT)
            }
            if (elapsed % D.STABILITY_STEP_TICKS == 0L) {
                val unsafe = g.pressure.count { it < D.SAFE_LOW || it > D.SAFE_HIGH }
                if (unsafe > 0) {
                    g.stability = (g.stability - unsafe).coerceAtLeast(0)
                    syncAll(g)
                    if (g.stability == 0) return finish(world, g)
                }
            }
            if (now >= g.shiftAt) {
                g.shiftAt = now + D.SHIFT_TICKS
                for (c in 0 until VmGame.CHAMBERS) shift(world, g, c)
                syncAll(g)
            }
        }
        melt(world, g, now)
    }

    /** The mine erupts: stability gone or time up. Everyone still inside dies (Wiki "Volcanic Mine" > Eruption). */
    private fun finish(world: WorldRef, g: VmGame) {
        for (b in g.boulders) world.deleteLoc(b.at(), BOULDER_SHAPE)
        world.setWorldAttr(GAME_STATE, null)
        for (p in g.players.toList()) {
            p[GAME] = null
            p[POINTS] = null
            p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
            clearItems(p)
            p.setVarbit(Varbits.FOSSIL_MINE_PROGRESS_STATUS, 0)
            p.message("The volcano erupts!")
            val request = DamageRequest(p, null, p.hitpoints)
            p.events.publish(request)
            if (!request.handled) p.hitpoints = 0
        }
        g.players.clear()
    }

    // ---- the HUD

    private fun syncAll(g: VmGame) {
        for (p in g.players) {
            p.setVarbit(Varbits.FOSSIL_MINE_NUMBERPLAYERS, g.players.size)
            p.setVarbit(Varbits.FOSSIL_MINE_CURRENT_STABILITY, g.stability)
            p.setVarbit(Varbits.FOSSIL_MINE_CHAMBER_STATUS, g.blockedBits())
            p.setVarbit(Varbits.FOSSIL_MINE_VENTA_STATUS, g.revealed[0])
            p.setVarbit(Varbits.FOSSIL_MINE_VENTB_STATUS, g.revealed[1])
            p.setVarbit(Varbits.FOSSIL_MINE_VENTC_STATUS, g.revealed[2])
            p.setVarbit(Varbits.FOSSIL_MINE_TEMP_PTS, p[POINTS] ?: 0)
        }
    }

    private fun award(p: PlayerRef, amount: Int) {
        val total = (p[POINTS] ?: 0) + amount
        p[POINTS] = total
        p.setVarbit(Varbits.FOSSIL_MINE_TEMP_PTS, total)
    }

    // ---- the boulder

    private suspend fun PlayerScript.mineBoulder(loc: LocRef) {
        val p = player
        val g = p[GAME] ?: return p.message("You can't mine that now.")
        val now = p.tick
        if (!g.running) return p.message("The volcano has not rumbled yet; wait for the game to start.")
        if (g.closing(now)) return p.message("It is too late to mine the boulder; get out of the volcano.")
        val boulder = g.boulderAt(loc.coord, loc.id) ?: return
        if (p.level(Stats.MINING) < D.MINING_LEVEL) return p.message("You need a Mining level of ${D.MINING_LEVEL} to mine this.")
        val pickObj = Skilling.bestTool(p, Stats.MINING, pickLevels)
        if (pickObj < 0) return p.message("You need a pickaxe to mine this.")
        val pick = PICKAXES.getValue(pickObj)
        keepInteraction()
        val clock = Skilling.clock(p, Skilling.locKey(loc.id, loc.coord.packed))
        if (clock.fresh && clock.actionDelay < now) {
            clock.actionDelay = now + pick.ticks - 1
            clock.animDelay = now + pick.ticks - 1
            return
        }
        if (clock.animDelay <= now) {
            clock.animDelay = now + pick.ticks
            p.anim(pick.anim)
        }
        if (clock.actionDelay < now) {
            clock.actionDelay = now + pick.ticks - 1
        } else if (clock.actionDelay == now) {
            p[LAST_SWING] = now
            hit(p, g, boulder)
        }
    }

    /**
     * One successful swing (OPEN-VM7: every swing at a boulder succeeds): damage, then the fragment roll (Wiki "Boulder
     * (Volcanic Mine)").
     */
    private fun hit(p: PlayerRef, g: VmGame, boulder: VmBoulder) {
        reward(p, boulder.stage)
        var damage = 1
        if (boulder.stage == D.STAGES - 1) {
            val miners = g.players.count { (it[LAST_SWING] ?: Long.MIN_VALUE) >= p.tick - D.MINING_RECENT_TICKS }.coerceAtLeast(1)
            if (miners < g.players.size) damage = D.SMALL_BOOST
        }
        boulder.hp -= damage
        if (boulder.hp > 0) return
        advance(g, boulder, p)
    }

    private fun advance(g: VmGame, boulder: VmBoulder, p: PlayerRef) {
        val old = boulder.at()
        p.deleteLoc(old, BOULDER_SHAPE)
        if (boulder.stage == D.STAGES - 1) {
            g.boulders.remove(boulder)
            for (q in g.players) {
                award(q, D.COMPLETION_POINTS)
                q.message("The boulder falls into the lava vortex; you gain ${D.COMPLETION_POINTS} points.")
            }
            return
        }
        boulder.stage++
        val per = if (boulder.stage == D.STAGES - 1) D.SMALL_HP_PER_PLAYER else D.STAGE_HP_PER_PLAYER
        val count = D.boulders(g.starters)
        boulder.hp = (per * g.starters + count - 1) / count
        p.addLoc(D.STAGE_LOCS[boulder.stage], boulder.at(), BOULDER_SHAPE, 0)
    }

    /**
     * The fragment roll of a success: a fragment with 1 in [stage odds], else a chip (1 point, 5 xp); then the mineral and
     * fossil rolls.
     */
    private fun reward(p: PlayerRef, stage: Int) {
        val fragment = if (p.random(D.STAGE_ONE_IN[stage]) == 0) pickFragment(p) else null
        if (fragment == null || p.inv(Invs.INV).freeSlots == 0) {
            award(p, D.CHIP_POINTS)
            MiningOutfit.addXp(p, D.CHIP_XP)
            if (fragment == null) p.message("You chip off a part of the boulder.")
            return
        }
        if (!p.transaction("volcanic mine fragment") { add(Invs.INV, fragment.obj, 1) }) return
        award(p, fragment.points)
        MiningOutfit.addXp(p, fragment.xp)
        extras(p)
    }

    /** The success chart cascades from runite down; the last (iron) is the default (Wiki "Boulder (Volcanic Mine)" > Mining info). */
    private fun pickFragment(p: PlayerRef): Fragment {
        val level = p.level(Stats.MINING)
        for (f in D.FRAGMENTS.dropLast(1)) {
            if (level >= f.level && p.statRandom(Stats.MINING, f.low, f.high)) return f
        }
        return D.FRAGMENTS.last()
    }

    private fun extras(p: PlayerRef) {
        val mineral = p.random(D.MINERAL_ONE_IN * 2)
        if (mineral < 2) {
            val obj = if (mineral == 0) Objs.FOSSIL_CALCITE else Objs.FOSSIL_PYROPHOSPHITE
            p.transaction("volcanic mine mineral") { addOrDrop(Invs.INV, obj, 1 + p.random(D.MINERAL_MAX)) }
            return
        }
        var roll = p.random(D.FOSSIL_ROLL)
        if (roll < D.NUMULITE_ROLL) {
            p.transaction("volcanic mine numulite") {
                addOrDrop(Invs.INV, Objs.FOSSIL_NUMULITE, D.NUMULITE_MIN + p.random(D.NUMULITE_MAX - D.NUMULITE_MIN + 1))
            }
            return
        }
        roll -= D.NUMULITE_ROLL
        for (i in D.FOSSILS.indices) {
            if (roll < D.FOSSIL_SHARES[i]) return run { p.transaction("volcanic mine fossil") { addOrDrop(Invs.INV, D.FOSSILS[i], 1) } }
            roll -= D.FOSSIL_SHARES[i]
        }
    }

    // ---- the chambers

    private fun chamberAt(at: CoordGrid): Int = D.CHAMBERS.indexOfFirst { it == at }

    private fun open(p: PlayerRef, g: VmGame): Boolean {
        if (!g.running) {
            p.message("The volcano has not rumbled yet; wait for the game to start.")
            return false
        }
        if (g.closing(p.tick)) {
            p.message("It is too late to do this; get out of the volcano.")
            return false
        }
        return true
    }

    private fun takeRock(p: PlayerRef, loc: LocRef) {
        val g = p[GAME] ?: return
        val c = D.ROCKS.indexOfFirst { it == loc.coord }
        if (c < 0 || !open(p, g)) return
        if (p.tick < g.rockBackAt[c]) return p.message("There are no rocks left here.")
        if (!p.transaction("volcanic mine rock") {
                add(Invs.INV, Objs.FOSSIL_MINE_LARGE_ROCK, 1)
            }
        ) {
            return p.message("You don't have enough inventory space.")
        }
        g.rockBackAt[c] = p.tick + D.ROCK_RESPAWN_TICKS
    }

    private fun block(p: PlayerRef, loc: LocRef) {
        val g = p[GAME] ?: return
        val c = chamberAt(loc.coord)
        if (c < 0 || !open(p, g)) return
        if (!p.transaction("volcanic mine block") { remove(Invs.INV, Objs.FOSSIL_MINE_LARGE_ROCK, 1) }) {
            return p.message("You need a large rock to block the chamber.")
        }
        g.blocked[c] = true
        p.addLoc(Locs.FOSSIL_VOLCANO_CHAMBER_BLOCKED, loc.coord, CHAMBER_SHAPE, 0)
        cap(p)
        syncAll(g)
    }

    private fun unblock(p: PlayerRef, loc: LocRef) {
        val g = p[GAME] ?: return
        val c = chamberAt(loc.coord)
        if (c < 0 || !open(p, g)) return
        val pick = Skilling.bestTool(p, Stats.MINING, pickLevels)
        if (pick < 0) return p.message("You need a pickaxe to clear the chamber.")
        // OPEN-VM4: clearing the blockage gives the large rock back, as the Wiki's capping route reuses one rock.
        p.transaction("volcanic mine unblock") { addOrDrop(Invs.INV, Objs.FOSSIL_MINE_LARGE_ROCK, 1) }
        g.blocked[c] = false
        p.addLoc(Locs.FOSSIL_VOLCANO_CHAMBER, loc.coord, CHAMBER_SHAPE, 0)
        MiningOutfit.addXp(p, D.UNBLOCK_XP)
        cap(p)
        syncAll(g)
    }

    /** Blocking or unblocking earns 50 points up to 300 per player (Wiki "Volcanic Mine" > Strategy). */
    private fun cap(p: PlayerRef) {
        val used = p[CAPPED] ?: 0
        if (used >= D.CHAMBER_POINTS_CAP) return
        p[CAPPED] = used + D.CHAMBER_POINTS
        award(p, D.CHAMBER_POINTS)
    }

    /** Checking a vent shows its chamber's pressure to every player until the mine shifts (Wiki "Volcanic Mine" > Mine stability). */
    private fun checkVent(p: PlayerRef, loc: LocRef) {
        val g = p[GAME] ?: return
        if (!open(p, g)) return
        val c = D.chamberOfVent(loc.coord)
        g.revealed[c] = g.pressure[c]
        syncAll(g)
    }

    // ---- the heat-proof vessel

    private fun throwWater(p: PlayerRef) {
        if (p[GAME] == null) return
        p.choiceDialog("Throw water", listOf("North", "East", "South", "West")) { pick -> throwIn(this, pick - 1) }
    }

    /** Hardens the lava tile next to the player in [direction] (0 N, 1 E, 2 S, 3 W) for 90 s, if it is lava (Wiki "Heat-proof vessel"). */
    private fun throwIn(p: PlayerRef, direction: Int) {
        val g = p[GAME] ?: return
        val left = p[WATER] ?: 0
        if (left <= 0) return p.message("The vessel is empty.")
        val at = p.coord
        val tile = CoordGrid(at.x + DX[direction], at.z + DZ[direction], at.level)
        val lava = p.locAt(tile, LAVA_SHAPE) ?: return p.message("There is no lava to harden there.")
        if (lava.id != Locs.FOSSIL_VOLCANO_PRESTEPPING) return p.message("There is no lava to harden there.")
        p[WATER] = left - 1
        p.addLoc(Locs.FOSSIL_VOLCANO_STEPPING, tile, LAVA_SHAPE, lava.angle, D.HARDEN_ORANGE_AT)
        g.hardened += VmHard(tile, lava.angle, p.tick + D.HARDEN_ORANGE_AT, 0)
    }

    /** Turns hardened lava orange after 45 s and white for its last 10 ticks; it melts back by itself (the loc timer). */
    private fun melt(world: WorldRef, g: VmGame, now: Long) {
        val it = g.hardened.iterator()
        while (it.hasNext()) {
            val h = it.next()
            if (now < h.due) continue
            when (h.stage) {
                0 -> {
                    world.addLoc(Locs.FOSSIL_VOLCANO_STEPPING_1, h.tile, LAVA_SHAPE, h.angle, D.HARDEN_WHITE_AT - D.HARDEN_ORANGE_AT)
                    h.due = now + D.HARDEN_WHITE_AT - D.HARDEN_ORANGE_AT
                    h.stage = 1
                }

                1 -> {
                    world.addLoc(Locs.FOSSIL_VOLCANO_STEPPING_2, h.tile, LAVA_SHAPE, h.angle, D.HARDEN_TICKS - D.HARDEN_WHITE_AT)
                    h.due = now + D.HARDEN_TICKS - D.HARDEN_WHITE_AT
                    h.stage = 2
                }

                else -> {
                    it.remove()
                }
            }
        }
    }

    // ---- the shop

    private suspend fun PlayerScript.shop(p: PlayerRef) {
        val lines = D.SHOP.map { it to it.cost } + named
        val labels =
            lines.map { "${it.first.name} - ${it.second}" } +
                listOf("Large water container - ${D.WATER_UPGRADE_COST}", "Ash covered tome - ${D.TOME_COST}")
        var page = 0
        while (true) {
            val start = page * PAGE
            val slice = labels.drop(start).take(PAGE)
            val more = start + PAGE < labels.size
            val answer = choice("Points: ${points(p)}", *(slice + (if (more) "More..." else "Cancel")).toTypedArray())
            when {
                answer in 1..slice.size -> return buy(p, start + answer - 1, lines)
                more && answer == slice.size + 1 -> page++
                else -> return
            }
        }
    }

    private fun buy(p: PlayerRef, index: Int, lines: List<Pair<VmReward, Int>>) {
        if (index < lines.size) {
            val line = lines[index].first
            val cost = lines[index].second
            if (line.noted || line.obj == Objs.FOSSIL_TABLET_VOLCANOTELEPORT) {
                p.countDialog("Enter amount:") { amount -> purchase(this, line, cost, amount) }
            } else {
                purchase(p, line, cost, 1)
            }
        } else if (index == lines.size) {
            unlock(p, D.WATER_UPGRADE_COST, Varbits.FOSSIL_MINE_UNLOCKED_MOREWATER)
        } else {
            unlock(p, D.TOME_COST, Varbits.FOSSIL_MINE_UNLOCKED_ULTRAFERTILESOIL)
        }
    }

    private fun unlock(p: PlayerRef, cost: Int, varbit: Int) {
        if (p.varbit(varbit) == 1) return p.message("You have already unlocked that.")
        val have = points(p)
        if (have < cost) return p.message("You don't have enough points.")
        p.setSaved(REWARD_KEY, have - cost)
        p.setVarbit(varbit, 1)
    }

    private fun purchase(p: PlayerRef, line: VmReward, cost: Int, amount: Int) {
        if (amount <= 0) return
        val total = cost.toLong() * amount
        val have = points(p)
        if (have < total) return p.message("You don't have enough points.")
        val obj = if (line.noted) p.noted(line.obj).takeIf { it >= 0 } ?: line.obj else line.obj
        if (!p.transaction("volcanic mine shop") { add(Invs.INV, obj, amount) }) return p.message("You don't have enough inventory space.")
        p.setSaved(REWARD_KEY, have - total)
    }

    private companion object {
        /** This world's game (world-scoped: the plugin instance is shared by every world in the JVM). */
        val GAME_STATE = tp.plugin.AttrKey<VmGame>("volcanic_mine_game")

        const val OP_FIRST = 1
        const val OP_SECOND = 2
        const val OP_THIRD = 3
        const val PAGE = 4

        /** Saved values: the reward points and the permanent access flag (Wiki "Petrified Pete's Ore Shop", "Volcanic Mine"). */
        const val REWARD_KEY = "volcanicMine.points"
        const val PERMANENT_KEY = "volcanicMine.permanent"

        /** Progress status varbit 5941 (client script 2026): 1 counts down to the start, 2 and up shows the time remaining. */
        const val STATUS_PREPARING = 1
        const val STATUS_RUNNING = 2

        /** Cache shapes: the boulders, chambers and exit are centrepieces (10), the lava tiles shape 10 on level 1. */
        const val BOULDER_SHAPE = 10
        const val CHAMBER_SHAPE = 10
        const val EXIT_SHAPE = 10
        const val LAVA_SHAPE = 10
        const val PERCENT = 100
        const val PERCENT_RANGE = 101

        /** OPEN-VM1: the landing tile is two tiles beyond a staircase. */
        const val STAIR_OFFSET = 2

        val DX = intArrayOf(0, 1, 0, -1)
        val DZ = intArrayOf(1, 0, -1, 0)
    }
}
