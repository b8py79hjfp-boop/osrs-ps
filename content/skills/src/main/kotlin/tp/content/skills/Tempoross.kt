package tp.content.skills

import tp.content.skills.TemporossGame.Companion.GAME
import tp.content.skills.TemporossGame.Companion.POINTS
import tp.content.skills.TemporossGame.Companion.TETHER
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Jingles
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.util.CoordGrid
import tp.content.skills.TemporossConfig as C

/**
 * The Tempoross minigame (docs/mechanics/tempoross.md): the boat at the Ruins of Unkah, one instanced cove per crew, harpoonfish
 * fishing and cooking, the cannons, storm, waves, fires, masts and totems, the spirit pools, permits and the reward pool.
 * The per-encounter state lives in [TemporossGame] and advances from the world tick hook; this class wires the interactions.
 */
public class Tempoross : ContentPlugin {
    private val rewards = TemporossRewards()
    private val games = ArrayList<TemporossGame>()
    private val lobby = ArrayList<PlayerRef>()
    private var lobbyEnd = NONE

    /** Last HUD values sent to a player, so interface text is only written on a change. */
    internal class Hud(var energy: Int = -1, var essence: Int = -1, var storm: Int = -1, var points: Int = -1)

    override fun ContentScope.register() {
        onWorldStart {
            rewards.resolve(this)
            if (System.getProperty("tp.worldSpawns") != "false") spawnStatic(this)
        }
        onWorldTick { drive(this) }
        onLogin { setVarbit(Varbits.TEMPOROSS_REWARDPERMITS, permits(this).coerceAtMost(PERMIT_VARBIT_MAX.toLong()).toInt()) }
        onLogout { leave(this, closeHud = false) }
        registerLobby()
        registerFishing()
        registerGame()
        registerRewards()
    }

    private fun ContentScope.registerLobby() {
        onOpLoc(Locs.TEMPOROSS_LOBBY_LADDER, OP_FIRST) { board(player, solo = false) }
        onOpLoc(Locs.TEMPOROSS_LOBBY_LADDER, OP_SECOND) { board(player, solo = false) }
        onOpLoc(Locs.TEMPOROSS_LOBBY_LADDER, OP_THIRD) { board(player, solo = true) }
        for (host in listOf(Npcs.TEMPOROSS_INSTANCE_HOST_E, Npcs.TEMPOROSS_INSTANCE_HOST_W)) {
            onOpNpc(host, OP_THIRD) { forfeit(player) }
        }
        for (host in listOf(Npcs.TEMPOROSS_INSTANCE_HOST_E_VICTORY, Npcs.TEMPOROSS_INSTANCE_HOST_W_VICTORY)) {
            onOpNpc(host, OP_THIRD) { forfeit(player) }
        }
    }

    private fun ContentScope.registerFishing() {
        for (spot in SPOTS) onOpNpc(spot, OP_FIRST) { npc -> harpoonfish(npc) }
        onOpNpc(Npcs.TEMPOROSS_P2_FISHINGSPOT, OP_FIRST) { npc -> spiritPool(npc) }
        onOpLoc(Locs.TEMPOROSS_SHRINE_FIRE, OP_FIRST) { cook(player) }
        onUseOnLoc(Objs.TEMPOROSS_RAW_HARPOONFISH, Locs.TEMPOROSS_SHRINE_FIRE) { cook(player) }
    }

    private fun ContentScope.registerGame() {
        for ((i, npc) in CRATE_NPCS.withIndex()) {
            onOpNpc(npc, OP_FIRST) { target -> fill(target, i) }
            onOpNpc(npc, OP_FOURTH) { target -> checkAmmo(player, target) }
            for (fish in FISH_ITEMS) onUseOnNpc(fish, npc) { target -> fill(target, i) }
        }
        onOpNpc(Npcs.TEMPOROSS_FIRE_HITBOX, OP_FIRST) { npc -> douse(player, npc) }
        for (loc in listOf(Locs.TEMPOROSS_MAST_BOTTOM_UNTETHERED, Locs.TEMPOROSS_TOTEM_UNTETHERED)) {
            onOpLoc(loc, OP_FIRST) { target -> tether(player, target) }
        }
        for (loc in listOf(Locs.TEMPOROSS_MAST_BOTTOM_TETHERED, Locs.TEMPOROSS_TOTEM_TETHERED)) {
            onOpLoc(loc, OP_FIRST) { player.untether() }
        }
        for (loc in BROKEN_SITES) onOpLoc(loc, OP_FIRST) { target -> repair(player, target) }
        onOpLoc(Locs.TEMPOROSS_CRATE_HAMMER, OP_FIRST) { supply(player, Objs.HAMMER, 1) }
        onOpLoc(Locs.TEMPOROSS_CRATE_ROPE, OP_FIRST) { supply(player, Objs.ROPE, 1) }
        onOpLoc(Locs.TEMPOROSS_CRATE_HARPOON, OP_FIRST) { supply(player, Objs.HARPOON, 1) }
        onOpLoc(Locs.TEMPOROSS_CRATE_BUCKET, OP_FIRST) { supply(player, Objs.BUCKET_EMPTY, 1) }
        onOpLoc(Locs.TEMPOROSS_CRATE_BUCKET, OP_SECOND) { supply(player, Objs.BUCKET_EMPTY, BUCKETS_FIVE) }
        onOpLoc(Locs.TEMPOROSS_CRATE_BUCKET, OP_THIRD) { supply(player, Objs.BUCKET_EMPTY, BUCKETS_TEN) }
        onOpLoc(Locs.TEMPOROSS_CRATE_BUCKET, OP_FOURTH) {
            player.countDialog("Enter amount:") { amount -> supply(this, Objs.BUCKET_EMPTY, amount) }
        }
        for (pump in listOf(Locs.TEMPOROSS_WATER_PUMP, Locs.TEMPOROSS_WATER_PUMP_DOCK)) {
            onOpLoc(pump, OP_FIRST) { fillBuckets(player) }
            onUseOnLoc(Objs.BUCKET_EMPTY, pump) { fillBuckets(player) }
        }
    }

    private fun ContentScope.registerRewards() {
        for (pool in POOLS) {
            onOpLoc(pool, OP_FIRST) { net() }
            onOpLoc(pool, OP_SECOND) { player.message("You have ${permits(player)} reward permits waiting in the pool.") }
            onOpLoc(pool, OP_THIRD) { bigSearch(player) }
        }
        onOpNpc(Npcs.TEMPOROSS_LOBBY_POOL_WATCHER, OP_THIRD) { takeNet(player) }
        onOpNpc(Npcs.TEMPOROSS_HUT_CHARACTER, OP_THIRD) { exchange(player) }
        onItemOp(Objs.TEMPOROSS_REWARD_CASKET, "Open") { rewards.openCasket(this) }
    }

    // ---- the world

    /** Spawns the static NPCs of the Ruins of Unkah (Wiki pins) and the harpoonfish spots of the open cove. */
    private fun spawnStatic(world: tp.plugin.WorldRef) {
        for ((npc, at) in UNKAH_NPCS) world.spawnNpc(npc, at, UNKAH_WANDER)
        for (t in C.NORTH_SPOTS) world.spawnNpc(Npcs.TEMPOROSS_HARPOONFISH_FISHINGSPOT_NORTH, t, 0)
        for (t in C.SOUTH_SPOTS) world.spawnNpc(Npcs.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SOUTH, t, 0)
    }

    // ---- the boat

    private suspend fun PlayerScript.board(p: PlayerRef, solo: Boolean) {
        if (p[GAME] != null || p in lobby) return
        if (p.baseLevel(Stats.FISHING) < C.MIN_FISHING) return p.message("You need a Fishing level of ${C.MIN_FISHING} to fight Tempoross.")
        if (solo) return start(listOf(p))
        p.teleport(C.LOBBY_DECK)
        if (lobby.isEmpty()) lobbyEnd = p.tick + C.LOBBY_WAIT_TICKS
        lobby += p
        if (lobby.size >= C.LOBBY_MAX_PLAYERS) depart()
    }

    private fun chebyshev(a: CoordGrid, b: CoordGrid): Int = maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))

    private fun depart() {
        val crew = lobby.toList()
        lobby.clear()
        lobbyEnd = NONE
        if (crew.isNotEmpty()) start(crew)
    }

    /** Builds the cove for [crew] and moves them in. */
    private fun start(crew: List<PlayerRef>) {
        val anchor = crew[0]
        val game = TemporossGame(anchor.createInstance(1, 1), anchor.tick)
        game.build(anchor, crew.size)
        games += game
        for ((i, p) in crew.withIndex()) {
            p.teleport(game.at(C.ARRIVAL[i % C.ARRIVAL.size]))
            p[GAME] = game
            p[POINTS] = 0
            p[HUD] = Hud()
            p.setVarbit(Varbits.TEMPOROSS_TETHERED, 0)
            p.setVarbit(Varbits.TEMPOROSS_CURRENT_POINTS, 0)
            p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.TEMPOROSS_HUD)
            game.players += p
        }
    }

    private fun forfeit(p: PlayerRef) {
        leave(p, closeHud = true)
        p.teleport(C.DOCK)
    }

    /** Takes [p] out of the encounter: points are lost with it. */
    private fun leave(p: PlayerRef, closeHud: Boolean) {
        lobby.remove(p)
        val game = p[GAME] ?: return
        game.players.remove(p)
        game.untether(p)
        p[GAME] = null
        p[POINTS] = null
        p[HUD] = null
        p.setVarbit(Varbits.TEMPOROSS_CURRENT_POINTS, 0)
        if (closeHud) p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
    }

    // ---- the world tick

    private fun drive(world: tp.plugin.WorldRef) {
        val now = world.tick
        if (lobby.isNotEmpty()) {
            lobby.removeAll { it.coord.level != C.LOBBY_DECK.level || chebyshev(it.coord, C.LOBBY_DECK) > LOBBY_RADIUS }
            if (lobby.isNotEmpty() && now >= lobbyEnd) depart()
        }
        for (game in games.toList()) tick(game, now)
    }

    private fun tick(game: TemporossGame, now: Long) {
        for (p in game.players.toList()) if (p.coord !in game.instance) leave(p, closeHud = true)
        val anchor = game.players.firstOrNull()
        if (anchor == null) {
            game.teardown()
            games.remove(game)
            return
        }
        when (game.advance(anchor, now)) {
            TemporossGame.Outcome.LOST -> washedAway(game)
            TemporossGame.Outcome.NONE -> Unit
        }
        if (game.phase == TemporossGame.Phase.ENDED && now >= game.endsAt) {
            for (p in game.players.toList()) forfeit(p)
            return
        }
        for (p in game.players) hud(p, game)
    }

    /** W: if the storm reaches 100% the players are washed away to the Ruins of Unkah and lose their points. */
    private fun washedAway(game: TemporossGame) {
        game.broadcast("A colossal wave washes you away.")
        for (p in game.players.toList()) forfeit(p)
    }

    /** W: the permits depend on the points; the kill count rises for everyone present. */
    private fun win(game: TemporossGame, now: Long) {
        game.subdue(now)
        for (p in game.players) {
            val permits = rewards.permits(p, p[POINTS] ?: 0)
            if (permits > 0) {
                val total = (permits(p) + permits).coerceAtMost(C.PERMITS_MAX.toLong())
                p.setSaved(C.PERMITS_KEY, total)
                p.setVarbit(Varbits.TEMPOROSS_REWARDPERMITS, total.toInt().coerceAtMost(PERMIT_VARBIT_MAX))
            }
            p.setVarp(Varps.TOTAL_TEMPOROSS_KILLS, p.varp(Varps.TOTAL_TEMPOROSS_KILLS) + 1)
            p.jingle(Jingles.TEMPOROSS_WIN_JINGLE)
        }
    }

    private fun hud(p: PlayerRef, game: TemporossGame) {
        val hud = p[HUD] ?: return
        val points = p[POINTS] ?: 0
        if (hud.energy != game.energyPercent) {
            hud.energy = game.energyPercent
            p.ifText(Components.Tempoross_Hud.ENERGY_TITLE, "Energy: ${hud.energy}%")
        }
        if (hud.essence != game.essencePercent) {
            hud.essence = game.essencePercent
            p.ifText(Components.Tempoross_Hud.ESSENCE_TITLE, "Essence: ${hud.essence}%")
        }
        if (hud.storm != game.storm) {
            hud.storm = game.storm
            p.ifText(Components.Tempoross_Hud.STORM_INTENSITY_TITLE, "Storm Intensity: ${hud.storm}%")
        }
        if (hud.points != points) {
            hud.points = points
            p.ifText(Components.Tempoross_Hud.POINTS_TEXT, "Points: $points")
            p.setVarbit(Varbits.TEMPOROSS_CURRENT_POINTS, points.coerceAtMost(POINTS_VARBIT_MAX))
        }
    }

    // ---- fishing

    /**
     * A harpoon the player can fish with: its Fishing level, animation and the (low, high) catch rates out of 256
     * (Wiki "Raw harpoonfish").
     */
    private class Harpoon(
        val level: Int,
        val anim: Int,
        val low: Int,
        val high: Int,
        val crystal: Boolean = false,
        val infernal: Boolean = false,
    )

    private fun harpoonOf(p: PlayerRef): Harpoon? {
        val obj = Skilling.bestTool(p, Stats.FISHING, HARPOON_LEVELS)
        return HARPOONS[obj]
    }

    private fun PlayerScript.cycle(key: Long, first: Int, ticks: Int, act: () -> Boolean) {
        val p = player
        val clock = Skilling.clock(p, key)
        val tick = p.tick
        if (clock.fresh) clock.actionDelay = tick + first
        if (tick == clock.actionDelay) {
            if (!act()) {
                Skilling.stop(p)
                p.stopInteraction()
                return
            }
            clock.actionDelay = tick + ticks
        }
        keepInteraction()
    }

    /** The fishing problem of [p] (level, harpoon, space), or null with the harpoon. */
    private fun readyToFish(p: PlayerRef): Harpoon? {
        if (p.baseLevel(Stats.FISHING) < C.MIN_FISHING) {
            p.message("You need a Fishing level of ${C.MIN_FISHING} to fish here.")
            return null
        }
        val harpoon = harpoonOf(p)
        if (harpoon == null) {
            p.message("You need a harpoon to catch these fish.")
            return null
        }
        if (p.inv(Invs.INV).freeSlots == 0) {
            p.message("Your inventory is too full to hold any more fish.")
            return null
        }
        return harpoon
    }

    /** RH: a roll every 5 ticks at the Tempoross Cove spots; a jumping spot gives a bonus fish half the time; 5 points a fish. */
    private suspend fun PlayerScript.harpoonfish(npc: NpcRef) {
        val p = player
        if (!npc.isInWorld) return Skilling.stop(p)
        val harpoon = readyToFish(p) ?: return Skilling.stop(p)
        cycle(Skilling.locKey(npc.type, npc.index), C.FISH_ARM_TICKS, C.FISH_CYCLE_TICKS) {
            p.anim(harpoon.anim)
            if (p.inv(Invs.INV).freeSlots == 0) {
                p.message("Your inventory is too full to hold any more fish.")
                return@cycle false
            }
            if (p.statRandom(Stats.FISHING, harpoon.low, harpoon.high)) {
                catchFish(
                    p,
                    harpoon,
                    npc.type == Npcs.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SPECIAL,
                )
            }
            true
        }
    }

    private fun catchFish(p: PlayerRef, harpoon: Harpoon, jumping: Boolean) {
        val bonus = jumping && p.inv(Invs.INV).freeSlots >= 2 && p.random(C.PERCENT) < C.BONUS_FISH_PERCENT
        val game = p[GAME]
        repeat(if (bonus) 2 else 1) {
            val obj = fishObj(p, harpoon)
            if (!p.transaction("tempoross fish") { add(Invs.INV, obj, 1) }) return
            if (game != null) game.addPoints(p, C.POINTS_FISH)
        }
        p.message("You catch a raw harpoonfish.", Skilling.SPAM)
        p.addXp(Stats.FISHING, catchXp(p.baseLevel(Stats.FISHING)) * anglerBonus(p))
    }

    /**
     * W: a crystal harpoon makes one catch in three crystallised; the infernal harpoon cooks one in three while it
     * has charges (Fishing.kt).
     */
    private fun fishObj(p: PlayerRef, harpoon: Harpoon): Int {
        if (harpoon.crystal && p.random(C.CRYSTAL_ONE_IN) == 0) return Objs.TEMPOROSS_CRYSTALLISED_HARPOONFISH
        if (harpoon.infernal && p.random(C.INFERNAL_ONE_IN) == 0) {
            val charges = p.saved(INFERNAL_CHARGES_KEY) ?: INFERNAL_CHARGES
            if (charges > 0) {
                p.setSaved(INFERNAL_CHARGES_KEY, charges - 1)
                p.addXp(Stats.COOKING, C.COOK_XP / 2)
                return Objs.TEMPOROSS_HARPOONFISH
            }
        }
        return Objs.TEMPOROSS_RAW_HARPOONFISH
    }

    /** W "Experience per action": the harpoonfish catch xp from the base Fishing level (levels 35 to 69, then 70 to 99). */
    private fun baseXp(level: Int): Int =
        if (level < C.XP_MID_LEVEL) {
            C.XP_LOW_BASE + (C.XP_TOP_BASE - C.XP_LOW_BASE) * (level - C.MIN_FISHING) / (C.XP_LAST_LEVEL - C.MIN_FISHING)
        } else {
            C.XP_MID_BASE + (C.XP_TOP_BASE - C.XP_MID_BASE) * (level - C.XP_MID_LEVEL) / (C.XP_LAST_LEVEL - C.XP_MID_LEVEL)
        }

    private fun catchXp(level: Int): Double = baseXp(level).toDouble() / C.XP_SCALE

    /**
     * The full angler's outfit (or its spirit version, piece for piece) gives 2.5% more Fishing xp (Wiki "Angler's
     * outfit"; as Fishing.kt).
     */
    private fun anglerBonus(p: PlayerRef): Double {
        val worn = p.inv(Invs.WORN)
        return if (ANGLER_PIECES.all { (slot, objs) -> worn.obj(slot) in objs }) ANGLER_MULTIPLIER else 1.0
    }

    /**
     * W: a scatter every 4 ticks at the spirit pool; success from the base Fishing level, 55 points, 11 times the
     * catch xp, 3% run energy.
     */
    private suspend fun PlayerScript.spiritPool(npc: NpcRef) {
        val p = player
        val game = p[GAME] ?: return p.message("Nothing interesting happens.")
        if (game.phase != TemporossGame.Phase.ESSENCE) return
        val harpoon = readyToFish(p) ?: return Skilling.stop(p)
        cycle(Skilling.locKey(npc.type, npc.index), C.POOL_CYCLE_TICKS, C.POOL_CYCLE_TICKS) {
            if (game.phase != TemporossGame.Phase.ESSENCE) return@cycle false
            p.anim(harpoon.anim)
            val level = p.baseLevel(Stats.FISHING)
            // The same rounding as PlayerRef.statRandom, but from the base level (W: boosts do not help at the pool).
            val chance = 1 + (2 * (harpoon.low * (Stats.MAX_LEVEL - level) + harpoon.high * (level - 1)) + POOL_SPAN) / (2 * POOL_SPAN)
            if (p.random(ROLL_SCALE) >= chance) return@cycle true
            p.message("The glowing fish scatter, shedding their magical scales.", Skilling.SPAM)
            game.addPoints(p, C.POINTS_POOL)
            p.addXp(Stats.FISHING, baseXp(level).toDouble() * C.POOL_XP_TIMES / C.XP_SCALE * anglerBonus(p))
            p.runEnergy = (p.runEnergy + C.POOL_RUN_RESTORE).coerceAtMost(RUN_MAX)
            if (game.hitEssence()) win(game, p.tick)
            game.phase == TemporossGame.Phase.ESSENCE
        }
    }

    // ---- cooking

    /**
     * Wiki "Harpoonfish": raw harpoonfish cook at the shrine in 3 ticks for 10 Cooking xp; they never burn. 10
     * points a fish in the game.
     */
    private fun cook(p: PlayerRef) {
        if (p.inv(Invs.INV).total(Objs.TEMPOROSS_RAW_HARPOONFISH) == 0L) return p.message("You have no raw harpoonfish to cook.")
        p.anim(Seqs.HUMAN_COOKING)
        p.weakQueue(C.COOK_TICKS) {
            val done =
                player.transaction("tempoross cook") {
                    remove(Invs.INV, Objs.TEMPOROSS_RAW_HARPOONFISH, 1) && add(Invs.INV, Objs.TEMPOROSS_HARPOONFISH, 1)
                }
            if (!done) return@weakQueue
            player.addXp(Stats.COOKING, C.COOK_XP)
            player[GAME]?.addPoints(player, C.POINTS_COOK)
            cook(player)
        }
    }

    // ---- the cannons

    private suspend fun PlayerScript.fill(npc: NpcRef, index: Int) {
        val p = player
        val game = p[GAME] ?: return
        val crate = game.crates.getOrNull(index) ?: return
        if (game.phase == TemporossGame.Phase.ENDED) return p.message("The cannons aren't needed anymore.")
        if (p.tick <
            crate.electrifiedUntil
        ) {
            return p.message("The magic of Tempoross coursing through the cannon makes it impossible to operate.")
        }
        if (p.tick < (p[TemporossGame.STUNNED_UNTIL] ?: 0L)) return
        val inv = p.inv(Invs.INV)
        // OPEN-TM5: cooked fish go in first, then crystallised, then raw (Strategies: "cooked fish will be deposited before the raw fish").
        val obj = FISH_ITEMS.firstOrNull { inv.total(it) > 0 } ?: return p.message("You don't have any harpoonfish to load.")
        keepInteraction()
        val clock = Skilling.clock(p, Skilling.locKey(npc.type, npc.index))
        if (clock.fresh) clock.actionDelay = p.tick
        if (p.tick < clock.actionDelay) return
        if (!p.transaction("tempoross load") { remove(Invs.INV, obj, 1) }) return
        clock.actionDelay = p.tick + C.LOAD_TICKS
        val cooked = obj == Objs.TEMPOROSS_HARPOONFISH
        game.load(p, crate, cooked, 1)
        game.addPoints(p, if (cooked) C.POINTS_DEPOSIT_COOKED else C.POINTS_DEPOSIT_RAW)
        val xp = baseXp(p.baseLevel(Stats.FISHING)).toDouble() * C.CRATE_XP_TIMES / C.XP_SCALE
        p.addXp(
            Stats.FISHING,
            xp * (if (obj == Objs.TEMPOROSS_CRYSTALLISED_HARPOONFISH) C.CRYSTAL_XP_PERCENT / PERCENT_DIVISOR else 1.0) * anglerBonus(p),
        )
    }

    private fun checkAmmo(p: PlayerRef, npc: NpcRef) {
        val crate = p[GAME]?.crates?.firstOrNull { it.npc == npc } ?: return
        p.message("The crate holds ${crate.cooked} harpoonfish and ${crate.raw} raw harpoonfish.")
    }

    // ---- fires, masts, totems, supplies

    private fun douse(p: PlayerRef, npc: NpcRef) {
        val game = p[GAME] ?: return
        val fire = game.fires.firstOrNull { it.npc == npc } ?: return
        val done = p.transaction("tempoross douse") { remove(Invs.INV, Objs.BUCKET_WATER, 1) && add(Invs.INV, Objs.BUCKET_EMPTY, 1) }
        if (!done) return p.message("You need a bucket of water to douse the fire.")
        game.removeFire(p, fire)
        game.addPoints(p, C.POINTS_DOUSE)
    }

    private fun siteAt(game: TemporossGame, loc: LocRef) = game.sites.firstOrNull { it.tile == loc.coord }

    private fun hasRope(p: PlayerRef): Boolean {
        val worn = p.inv(Invs.WORN)
        val outfit = SPIRIT_PIECES.all { (slot, obj) -> worn.obj(slot) == obj }
        return outfit || p.inv(Invs.INV).total(Objs.ROPE) > 0
    }

    /** W: a rope (or the full spirit angler's outfit) ties the player to an intact mast or totem pole. */
    private fun tether(p: PlayerRef, loc: LocRef) {
        val game = p[GAME] ?: return
        val site = siteAt(game, loc) ?: return
        if (!site.intact) return
        if (!hasRope(p)) return p.message("You need a rope to tether yourself.")
        p.anim(Seqs.TEMPOROSS_TETHER)
        p[TETHER] = site
        p.setVarbit(Varbits.TEMPOROSS_TETHERED, site.index)
    }

    private fun PlayerRef.untether() {
        val game = this[GAME] ?: return
        anim(Seqs.TEMPOROSS_UNTETHER)
        game.untether(this)
    }

    /**
     * Wiki "Totem pole" / "Mast": a hammer repairs it for 4x the Construction level in xp (OPEN-TM6: everyone gets
     * the xp) and 40 points.
     */
    private fun repair(p: PlayerRef, loc: LocRef) {
        val game = p[GAME] ?: return
        val site = siteAt(game, loc) ?: return
        val inv = p.inv(Invs.INV)
        val worn = p.inv(Invs.WORN)
        if (inv.total(Objs.HAMMER) == 0L && worn.total(Objs.IMCANDO_HAMMER) == 0L && worn.total(Objs.IMCANDO_HAMMER_OFFHAND) == 0L) {
            return p.message("You need a hammer to repair this.")
        }
        game.repair(p, site)
        game.addPoints(p, C.POINTS_REPAIR)
        p.addXp(Stats.CONSTRUCTION, p.baseLevel(Stats.CONSTRUCTION) * C.REPAIR_XP_PER_LEVEL)
    }

    private fun supply(p: PlayerRef, obj: Int, count: Int) {
        if (p[GAME] == null || count <= 0) return
        if (obj != Objs.BUCKET_EMPTY && p.inv(Invs.INV).total(obj) > 0) return p.message("You already have one.")
        if (!p.transaction("tempoross supply") { add(Invs.INV, obj, count) }) p.message("You don't have enough inventory space.")
    }

    /** The pump fills every empty bucket in the backpack. */
    private fun fillBuckets(p: PlayerRef) {
        val n = p.inv(Invs.INV).total(Objs.BUCKET_EMPTY).toInt()
        if (n == 0) return p.message("You have no empty buckets.")
        p.anim(Seqs.HUMAN_FILLBUCKET_SANDPIT)
        p.transaction("tempoross pump") { remove(Invs.INV, Objs.BUCKET_EMPTY, n) && add(Invs.INV, Objs.BUCKET_WATER, n) }
    }

    // ---- the reward pool

    private fun permits(p: PlayerRef): Long = p.saved(C.PERMITS_KEY) ?: 0L

    private fun hasNet(p: PlayerRef): Boolean = p.inv(Invs.INV).total(SMALL_NET) > 0 || p.inv(Invs.INV).total(Objs.BIG_NET) > 0

    /** RP: one reward every 3 ticks while permits and space last, 10 Fishing xp each. */
    private suspend fun PlayerScript.net() {
        val p = player
        if (!hasNet(p)) return p.message("You need a fishing net to fish from the pool. The Spirit Angler can give you one.")
        if (permits(p) <= 0L) return p.message("There is nothing left in the pool.")
        cycle(Skilling.locKey(Locs.TEMPOROSS_REWARDPOOL, 0), C.SEARCH_TICKS, C.SEARCH_TICKS) { searchOnce(p) }
    }

    private fun searchOnce(p: PlayerRef): Boolean {
        val owed = permits(p)
        if (owed <= 0L) return false
        if (p.inv(Invs.INV).freeSlots == 0) {
            p.message("You don't have enough inventory space.")
            return false
        }
        if (!rewards.search(p)) return false
        p.setSaved(C.PERMITS_KEY, owed - 1)
        p.setVarbit(Varbits.TEMPOROSS_REWARDPERMITS, (owed - 1).toInt().coerceAtMost(PERMIT_VARBIT_MAX))
        return owed - 1 > 0
    }

    private suspend fun PlayerScript.bigSearch(p: PlayerRef) {
        if (!hasNet(p)) return p.message("You need a fishing net to fish from the pool. The Spirit Angler can give you one.")
        repeat(C.BIG_SEARCH) { if (!searchOnce(p)) return }
    }

    private fun takeNet(p: PlayerRef) {
        if (hasNet(p)) return p.message("You already have a fishing net.")
        p.transaction("tempoross net") { add(Invs.INV, SMALL_NET, 1) }
    }

    /** Wiki "Retired sailor": a fish barrel, tackle box, empty tome of water or big harpoonfish for 25 soaked pages. */
    private fun exchange(p: PlayerRef) {
        val inv = p.inv(Invs.INV)
        val obj = EXCHANGEABLE.firstOrNull { inv.total(it) > 0 } ?: return p.message("You have nothing I want.")
        p.transaction("tempoross exchange") { remove(Invs.INV, obj, 1) && add(Invs.INV, Objs.SOAKED_PAGE, EXCHANGE_PAGES) }
    }

    private companion object {
        val HUD = AttrKey<Hud>("tempoross.hud")
        const val NONE = -1L
        const val OP_FIRST = 1
        const val OP_SECOND = 2
        const val OP_THIRD = 3
        const val OP_FOURTH = 4
        const val BUCKETS_FIVE = 5
        const val BUCKETS_TEN = 10
        const val LOBBY_RADIUS = 12
        const val UNKAH_WANDER = 3
        const val ROLL_SCALE = 256
        const val POOL_SPAN = 98
        const val RUN_MAX = 10000
        const val PERCENT_DIVISOR = 100.0
        const val EXCHANGE_PAGES = 25

        /** The permit varbit (`tempoross_rewardpermits`) and the points varbit hold at most this many (their bit widths in the cache). */
        const val PERMIT_VARBIT_MAX = 8191
        const val POINTS_VARBIT_MAX = 32767

        /** Small fishing net (cache obj 303). */
        const val SMALL_NET = 303

        /** Same saved key as Fishing.kt: the infernal harpoon's 5,000 charges (Wiki "Infernal harpoon"). */
        const val INFERNAL_CHARGES_KEY = "fishing.infernal_charges"
        const val INFERNAL_CHARGES = 5000L
        const val ANGLER_MULTIPLIER = 1.025

        val SPOTS =
            listOf(
                Npcs.TEMPOROSS_HARPOONFISH_FISHINGSPOT_NORTH,
                Npcs.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SOUTH,
                Npcs.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SPECIAL,
            )
        val CRATE_NPCS =
            listOf(
                Npcs.TEMPOROSS_NPC_CRATE_AMMUNITION_1,
                Npcs.TEMPOROSS_NPC_CRATE_AMMUNITION_2,
                Npcs.TEMPOROSS_NPC_CRATE_AMMUNITION_3,
                Npcs.TEMPOROSS_NPC_CRATE_AMMUNITION_4,
            )

        /** Cooked first, then crystallised, then raw. */
        val FISH_ITEMS = listOf(Objs.TEMPOROSS_HARPOONFISH, Objs.TEMPOROSS_CRYSTALLISED_HARPOONFISH, Objs.TEMPOROSS_RAW_HARPOONFISH)
        val BROKEN_SITES =
            listOf(
                Locs.TEMPOROSS_MAST_BOTTOM_WEST_BROKEN,
                Locs.TEMPOROSS_MAST_BOTTOM_EAST_BROKEN,
                Locs.TEMPOROSS_TOTEM_NORTH_BROKEN,
                Locs.TEMPOROSS_TOTEM_SOUTH_BROKEN,
            )
        val POOLS = (Locs.TEMPOROSS_REWARDPOOL_0..Locs.TEMPOROSS_REWARDPOOL_8).toList()
        val EXCHANGEABLE =
            listOf(
                Objs.FISH_BARREL_CLOSED,
                Objs.FISH_BARREL_OPEN,
                Objs.TACKLE_BOX,
                Objs.TOME_OF_WATER_UNCHARGED,
                Objs.POH_TROPHYDROP_HARPOONFISH,
            )

        /**
         * Wiki "Raw harpoonfish": rates (low, high) out of 256 for the harpoon, dragon and crystal harpoon; the infernal
         * harpoon uses the dragon's (OPEN-FS4).
         */
        val HARPOONS =
            mapOf(
                Objs.HARPOON to Harpoon(1, Seqs.HUMAN_HARPOON, 170, 255),
                Objs.HUNTING_BARBED_HARPOON to Harpoon(1, Seqs.HUMAN_HARPOON_BARBED, 170, 255),
                Objs.DRAGON_HARPOON to Harpoon(DRAGON_LEVEL, Seqs.HUMAN_HARPOON_DRAGON, 178, 267),
                Objs.INFERNAL_HARPOON to Harpoon(DRAGON_LEVEL, Seqs.HUMAN_HARPOON_INFERNAL, 178, 267, infernal = true),
                Objs.CRYSTAL_HARPOON to Harpoon(CRYSTAL_LEVEL, Seqs.HUMAN_HARPOON_CRYSTAL, 187, 280, crystal = true),
            )
        val HARPOON_LEVELS: Map<Int, Int> = HARPOONS.mapValues { it.value.level }

        /** Worn slots head 0, body 4, legs 7, feet 10 with the angler's and spirit angler's pieces (as Fishing.kt). */
        val ANGLER_PIECES: List<Pair<Int, Set<Int>>> =
            listOf(
                0 to setOf(Objs.TRAWLER_REWARD_HAT, Objs.SPIRIT_ANGLER_HAT),
                4 to setOf(Objs.TRAWLER_REWARD_TOP, Objs.SPIRIT_ANGLER_TOP),
                7 to setOf(Objs.TRAWLER_REWARD_LEGS, Objs.SPIRIT_ANGLER_LEGS),
                10 to setOf(Objs.TRAWLER_REWARD_BOOTS, Objs.SPIRIT_ANGLER_BOOTS),
            )
        val SPIRIT_PIECES =
            listOf(0 to Objs.SPIRIT_ANGLER_HAT, 4 to Objs.SPIRIT_ANGLER_TOP, 7 to Objs.SPIRIT_ANGLER_LEGS, 10 to Objs.SPIRIT_ANGLER_BOOTS)

        /**
         * The Ruins of Unkah NPCs at the Wiki pins: Captain Pudi, the pool's Spirit Angler, the Retired sailor and the
         * four Spirit Anglers.
         */
        val UNKAH_NPCS =
            listOf(
                Npcs.TEMPOROSS_LOBBY_HOST to CoordGrid(3153, 2840, 0),
                Npcs.TEMPOROSS_LOBBY_POOL_WATCHER to CoordGrid(3153, 2831, 0),
                Npcs.TEMPOROSS_HUT_CHARACTER to CoordGrid(3160, 2848, 0),
                Npcs.TEMPOROSS_LOBBY_SPIRIT_ANGLER_MAN_1 to CoordGrid(3173, 2856, 0),
                Npcs.TEMPOROSS_LOBBY_SPIRIT_ANGLER_MAN_2 to CoordGrid(3171, 2831, 0),
                Npcs.TEMPOROSS_LOBBY_SPIRIT_ANGLER_WOMAN_1 to CoordGrid(3174, 2846, 0),
                Npcs.TEMPOROSS_LOBBY_SPIRIT_ANGLER_WOMAN_2 to CoordGrid(3176, 2831, 0),
            )

        /** Wiki: the dragon and infernal harpoons need 61 Fishing, the crystal harpoon 71. */
        const val DRAGON_LEVEL = 61
        const val CRYSTAL_LEVEL = 71
    }
}
