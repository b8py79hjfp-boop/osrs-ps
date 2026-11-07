package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.util.CoordGrid
import tp.content.skills.TemporossConfig as C

/**
 * One Tempoross encounter (docs/mechanics/tempoross.md): an instanced copy of the cove (map square 47,44) with its own
 * clock, Tempoross' energy and essence, the storm, the four cannons and tether sites, the fires and the fishing spots. The
 * world tick hook advances it once per tick ([advance]); with nobody left inside the plugin tears it down.
 */
internal class TemporossGame(val instance: tp.plugin.InstanceRef, val startTick: Long) {
    enum class Phase { ENERGY, ESSENCE, ENDED }

    enum class Outcome { NONE, LOST }

    /** A mast or totem pole players tether to with a rope; [index] is the value of varbit `tempoross_tethered` that draws it tethered. */
    class Site(val index: Int, val tile: CoordGrid, val parent: Int, val brokenLoc: Int, val angle: Int) {
        var intact: Boolean = true
    }

    /** An ammunition crate with the cannon it feeds. The fish loaded wait in it until the cannon fires them. */
    class Crate(val index: Int, val tile: CoordGrid, val angle: Int, val cannon: CoordGrid, val cannonAngle: Int) {
        var cooked: Int = 0
        var raw: Int = 0
        var nextFire: Long = 0
        var electrifiedUntil: Long = 0
        var look: Int = 0
        var npc: NpcRef? = null

        val stored: Int get() = cooked + raw
    }

    class Fire(val tile: CoordGrid, val npc: NpcRef, var spreadAt: Long) {
        var children: Int = 0
    }

    class Spot(var npc: NpcRef, val tile: CoordGrid, val type: Int)

    private class Pending(val at: Long, val kind: Int, val tile: CoordGrid?, val index: Int)

    val dx: Int = instance.base.x - C.TEMPLATE_BASE.x
    val dz: Int = instance.base.z - C.TEMPLATE_BASE.z
    val players = ArrayList<PlayerRef>()
    val sites = ArrayList<Site>()
    val crates = ArrayList<Crate>()
    val fires = ArrayList<Fire>()
    val spots = ArrayList<Spot>()
    val hosts = ArrayList<NpcRef>()
    private val pools = ArrayList<NpcRef>()
    private val pending = ArrayList<Pending>()
    private var boss: NpcRef? = null

    var phase: Phase = Phase.ENERGY
    var energyMax: Int = 1
    var essenceMax: Int = 1
    var energy: Int = 1
    var essence: Int = 1
    var storm: Int = 0
    var drains: Int = 0
    var enraged: Boolean = false
    var endsAt: Long = 0
    private var stormTicks = 0
    private var regen = 0
    private var nextAttack = 0L
    private var nextDouble = 0L
    private var doubleSpot: Spot? = null
    private var doubleUntil = 0L
    private var lastAdvance = -1L

    val energyPercent: Int get() = (energy * C.PERCENT + energyMax - 1) / energyMax
    val essencePercent: Int get() = (essence * C.PERCENT + essenceMax - 1) / essenceMax

    /** Template tile -> instance tile. */
    fun at(t: CoordGrid): CoordGrid = CoordGrid(t.x + dx, t.z + dz, t.level)

    // ---- setup

    /** Copies the cove into the instance, sizes Tempoross for [count] players and populates the cove. */
    fun build(anchor: PlayerRef, count: Int) {
        for (level in 0 until C.TEMPLATE_LEVELS) {
            for (zx in 0 until C.ZONES_PER_SQUARE) {
                for (zz in 0 until C.ZONES_PER_SQUARE) {
                    val from = CoordGrid(C.TEMPLATE_BASE.x + zx * C.ZONE, C.TEMPLATE_BASE.z + zz * C.ZONE, level)
                    instance.copyZone(from, at(from))
                }
            }
        }
        // W (Mod Squid): the per-player share falls linearly from the solo value to the 40-player value.
        energyMax = total(count, C.ENERGY_SOLO, C.ENERGY_AT_40)
        essenceMax = total(count, C.ESSENCE_SOLO, C.ESSENCE_AT_40)
        energy = energyMax
        essence = essenceMax
        nextAttack = startTick + C.ATTACK_FIRST_TICKS
        nextDouble = startTick + C.DOUBLE_FIRST_TICKS
        placeSites()
        placeCrates(anchor)
        boss = anchor.spawnNpc(Npcs.TEMPOROSS_BOSS_READY, at(C.BOSS_TILE), 0)
        for (c in C.POOL_CENTRES) {
            val sw = CoordGrid(c.x - 1, c.z - 1, c.level)
            pools += anchor.spawnNpc(Npcs.TEMPOROSS_P2_FISHINGSPOT_INACTIVE, at(sw), 0)
        }
        for (t in C.NORTH_SPOTS) spots += Spot(anchor.spawnNpc(Npcs.TEMPOROSS_HARPOONFISH_FISHINGSPOT_NORTH, at(t), 0), at(t), NORTH)
        for (t in C.SOUTH_SPOTS) spots += Spot(anchor.spawnNpc(Npcs.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SOUTH, at(t), 0), at(t), SOUTH)
        hosts += anchor.spawnNpc(Npcs.TEMPOROSS_INSTANCE_HOST_E, at(C.HOST_EAST), 0)
        hosts += anchor.spawnNpc(Npcs.TEMPOROSS_INSTANCE_HOST_W, at(C.HOST_WEST), 0)
    }

    private fun total(count: Int, solo: Int, at40: Int): Int {
        val n = count.coerceIn(1, C.LOBBY_MAX_PLAYERS)
        val perPlayer = solo - (solo - at40) * (n - 1) / (C.LOBBY_MAX_PLAYERS - 1)
        return perPlayer * n
    }

    private fun placeSites() {
        sites += Site(SITE_NORTH, at(NORTH_TOTEM), Locs.TEMPOROSS_TOTEM_NORTH, Locs.TEMPOROSS_TOTEM_NORTH_BROKEN, 0)
        sites += Site(SITE_WEST, at(WEST_MAST), Locs.TEMPOROSS_MAST_BOTTOM_WEST, Locs.TEMPOROSS_MAST_BOTTOM_WEST_BROKEN, 2)
        sites += Site(SITE_EAST, at(EAST_MAST), Locs.TEMPOROSS_MAST_BOTTOM_EAST, Locs.TEMPOROSS_MAST_BOTTOM_EAST_BROKEN, 0)
        sites += Site(SITE_SOUTH, at(SOUTH_TOTEM), Locs.TEMPOROSS_TOTEM_SOUTH, Locs.TEMPOROSS_TOTEM_SOUTH_BROKEN, 0)
    }

    /**
     * Crates and cannons by cache loc (raw map level 1, on bridge tiles, so level 0 here): crate i (locs 40968+i) feeds
     * cannon i (locs 41238+i) (cache map square 47,44).
     */
    private fun placeCrates(anchor: PlayerRef) {
        val crateTiles = listOf(CoordGrid(3038, 2850, 0), CoordGrid(3038, 2848, 0), CoordGrid(3056, 2850, 0), CoordGrid(3056, 2848, 0))
        val crateAngles = intArrayOf(3, 3, 1, 1)
        val cannonTiles = listOf(CoordGrid(3038, 2851, 0), CoordGrid(3038, 2846, 0), CoordGrid(3054, 2851, 0), CoordGrid(3054, 2846, 0))
        for (i in crateTiles.indices) {
            val crate = Crate(i, at(crateTiles[i]), crateAngles[i], at(cannonTiles[i]), crateAngles[i])
            crate.npc = anchor.spawnNpc(Npcs.TEMPOROSS_NPC_CRATE_AMMUNITION_1 + i, crate.tile, 0)
            crates += crate
        }
    }

    // ---- the clock

    /** Advances the encounter to [now]; [anchor] is any player inside. */
    fun advance(anchor: PlayerRef, now: Long): Outcome {
        if (now == lastAdvance) return Outcome.NONE
        lastAdvance = now
        runPending(anchor, now)
        runFires(anchor, now)
        doubleSpots(anchor, now)
        return when (phase) {
            Phase.ENERGY -> energyPhase(anchor, now)
            Phase.ESSENCE -> essencePhase(now)
            Phase.ENDED -> Outcome.NONE
        }
    }

    private fun energyPhase(anchor: PlayerRef, now: Long): Outcome {
        if (++stormTicks >= (if (enraged) C.ENRAGED_STORM_TICKS_PER_PERCENT else C.STORM_TICKS_PER_PERCENT)) {
            stormTicks = 0
            if (++storm >= C.STORM_MAX) return Outcome.LOST
        }
        for (crate in crates) fireCannon(anchor, crate, now)
        if (energy <= 0) {
            submerge()
            return Outcome.NONE
        }
        if (now >= nextAttack && energyPercent > C.NO_ATTACK_PERCENT && pending.none { it.kind == WAVE_HIT }) attack(anchor, now)
        return Outcome.NONE
    }

    /** OPEN-TM5: while Tempoross is surfaced each working cannon fires one stored fish every [C.CANNON_FIRE_TICKS]. */
    private fun fireCannon(anchor: PlayerRef, crate: Crate, now: Long) {
        if (crate.stored == 0 || now < crate.nextFire || now < crate.electrifiedUntil) return
        crate.nextFire = now + C.CANNON_FIRE_TICKS
        if (crate.cooked > 0) {
            crate.cooked--
            energy -= C.COOKED_DRAIN
        } else {
            crate.raw--
            energy -= C.RAW_DRAIN
        }
        energy = energy.coerceAtLeast(0)
        showCrate(anchor, crate)
    }

    private fun essencePhase(now: Long): Outcome {
        regen += energyMax
        val gain = regen / C.REGEN_TICKS_TO_FULL
        regen -= gain * C.REGEN_TICKS_TO_FULL
        energy = (energy + gain).coerceAtMost(energyMax)
        if (energy >= energyMax) surface(now)
        return Outcome.NONE
    }

    /** W: at 0% energy Tempoross submerges, the storm resets and the spirit pools open. */
    private fun submerge() {
        phase = Phase.ESSENCE
        drains++
        storm = 0
        stormTicks = 0
        regen = 0
        pending.removeAll { it.kind == WAVE_HIT }
        boss?.transform(Npcs.TEMPOROSS_BOSS_WHIRLPOOL, true)
        for (pool in pools) pool.transform(Npcs.TEMPOROSS_P2_FISHINGSPOT, true)
    }

    /** W: at full energy again Tempoross emerges; after three drains it is enraged (S: "gathered too much energy"). */
    private fun surface(now: Long) {
        phase = Phase.ENERGY
        storm = 0
        stormTicks = 0
        nextAttack = now + C.ATTACK_FIRST_TICKS
        for (pool in pools) pool.transform(Npcs.TEMPOROSS_P2_FISHINGSPOT_INACTIVE, true)
        if (drains >= C.DRAINS_BEFORE_ENRAGE) {
            enraged = true
            boss?.transform(Npcs.TEMPOROSS_BOSS_ENRAGED, true)
            broadcast("Tempoross has gathered too much energy. The storm is out of control!")
        } else {
            boss?.transform(Npcs.TEMPOROSS_BOSS_READY, true)
        }
    }

    /** A scatter at the spirit pool takes [C.POOL_HIT] essence. True when the essence is gone and Tempoross is subdued. */
    fun hitEssence(): Boolean {
        essence = (essence - C.POOL_HIT).coerceAtLeast(0)
        return essence <= 0
    }

    /** Tempoross is subdued: the players are sent home after [C.VICTORY_LEAVE_TICKS]. */
    fun subdue(now: Long) {
        phase = Phase.ENDED
        endsAt = now + C.VICTORY_LEAVE_TICKS
        pending.clear()
        boss?.transform(Npcs.TEMPOROSS_BOSS_WHIRLPOOL, true)
        hosts.getOrNull(0)?.transform(Npcs.TEMPOROSS_INSTANCE_HOST_E_VICTORY, true)
        hosts.getOrNull(1)?.transform(Npcs.TEMPOROSS_INSTANCE_HOST_W_VICTORY, true)
    }

    // ---- attacks (W "Tempoross' attacks")

    private fun attack(anchor: PlayerRef, now: Long) {
        nextAttack = now + C.ATTACK_GAP_MIN + anchor.random(C.ATTACK_GAP_SPREAD)
        when (anchor.random(ATTACKS)) {
            0 -> {
                broadcast("A colossal wave closes in...")
                pending += Pending(now + C.WAVE_WARNING_TICKS, WAVE_HIT, null, 0)
            }

            1 -> {
                lightning(anchor, now)
            }

            else -> {
                torrent(anchor, now)
            }
        }
    }

    /** W: grey clouds expand over the cove and then light fires. */
    private fun lightning(anchor: PlayerRef, now: Long) {
        broadcast("A strong wind blows as clouds roll in...")
        repeat(C.CLOUDS) {
            val target = strikeTile(anchor) ?: return@repeat
            anchor.spotAnimAt(target, SpotAnims.TEMPOROSS_FIRE_SPAWN)
            pending += Pending(now + C.CLOUD_DELAY_TICKS, IGNITE, target, 0)
        }
    }

    private fun strikeTile(anchor: PlayerRef): CoordGrid? {
        repeat(STRIKE_ATTEMPTS) {
            val area = C.STRIKE_AREAS[anchor.random(C.STRIKE_AREAS.size)]
            val x = area.x.first + anchor.random(area.x.last - area.x.first + 1)
            val z = area.z.first + anchor.random(area.z.last - area.z.first + 1)
            val tile = at(CoordGrid(x, z, area.level))
            if (fireFits(anchor, tile)) return tile
        }
        return null
    }

    private fun fireFits(anchor: EntityRef, tile: CoordGrid): Boolean {
        for (ox in 0 until C.FIRE_SIZE) {
            for (oz in 0 until C.FIRE_SIZE) {
                if (!anchor.isWalkable(CoordGrid(tile.x + ox, tile.z + oz, tile.level))) return false
            }
        }
        return fires.none { overlaps(it.tile, tile) }
    }

    private fun overlaps(a: CoordGrid, b: CoordGrid): Boolean =
        a.level == b.level && kotlin.math.abs(a.x - b.x) < C.FIRE_SIZE && kotlin.math.abs(a.z - b.z) < C.FIRE_SIZE

    /** W: a charged torrent of water is launched at a cannon. */
    private fun torrent(anchor: PlayerRef, now: Long) {
        val ready = crates.filter { now >= it.electrifiedUntil }
        if (ready.isEmpty()) return
        val crate = ready[anchor.random(ready.size)]
        broadcast("A charged torrent of water surges towards a nearby cannon...")
        pending += Pending(now + C.TORRENT_DELAY_TICKS, TORRENT_HIT, null, crate.index)
    }

    private fun runPending(anchor: PlayerRef, now: Long) {
        var i = 0
        while (i < pending.size) {
            val ev = pending[i]
            if (ev.at > now) {
                i++
                continue
            }
            pending.removeAt(i)
            when (ev.kind) {
                WAVE_HIT -> wave(anchor, now)
                IGNITE -> ev.tile?.let { ignite(anchor, it, now) }
                TORRENT_HIT -> electrify(anchor, crates[ev.index], now)
                TORRENT_END -> recover(anchor, crates[ev.index])
            }
        }
    }

    private fun electrify(anchor: PlayerRef, crate: Crate, now: Long) {
        crate.electrifiedUntil = now + C.TORRENT_DISABLE_TICKS
        anchor.addLoc(Locs.TEMPOROSS_CANNON_1_DISABLED + crate.index, crate.cannon, LOC_SHAPE, crate.cannonAngle)
        broadcast("A cannon is stunned by lightning...")
        for (p in players) {
            if (p.coord.level != crate.tile.level || p.distanceTo(crate.npc ?: continue) > C.STUN_RANGE) continue
            p.frozenUntil = now + C.STUN_TICKS
            p[STUNNED_UNTIL] = now + C.STUN_TICKS
            p.message("The surge of lightning arcs from the cannon into you...")
        }
        pending += Pending(crate.electrifiedUntil, TORRENT_END, null, crate.index)
    }

    private fun recover(anchor: PlayerRef, crate: Crate) {
        anchor.addLoc(Locs.TEMPOROSS_CANNON_1 + crate.index, crate.cannon, LOC_SHAPE, crate.cannonAngle)
    }

    /**
     * W: the wave sweeps the cove: it puts out the fires, pushes untethered players away, destroys their minigame
     * items and may break the tether sites.
     */
    private fun wave(anchor: PlayerRef, now: Long) {
        for (f in fires.toList()) removeFire(anchor, f)
        for (p in players) {
            val site = p[TETHER]
            if (site != null && site.intact) {
                p.frozenUntil = now + C.WAVE_DURATION_TICKS
                addPoints(p, C.POINTS_WAVE)
            } else {
                push(anchor, p)
                destroyItems(p, all = true, anchor = anchor)
            }
        }
        val percent = C.BREAK_MIN_PERCENT + C.BREAK_SPREAD_PERCENT * storm / C.PERCENT
        for (site in sites) {
            if (site.intact && anchor.random(C.PERCENT) < percent) breakSite(anchor, site)
        }
    }

    private fun push(anchor: PlayerRef, p: PlayerRef) {
        val c = p.coord
        val r = C.WAVE_PUSH_TILES
        val options = ArrayList<CoordGrid>()
        for (x in c.x - r..c.x + r) {
            for (z in c.z - r..c.z + r) {
                val t = CoordGrid(x, z, c.level)
                if ((x != c.x || z != c.z) && anchor.isWalkable(t)) options += t
            }
        }
        if (options.isNotEmpty()) p.teleport(options[anchor.random(options.size)])
    }

    private fun breakSite(anchor: PlayerRef, site: Site) {
        site.intact = false
        anchor.addLoc(site.brokenLoc, site.tile, LOC_SHAPE, site.angle)
        for (p in players) {
            if (p[TETHER] === site) untether(p)
        }
    }

    fun repair(anchor: PlayerRef, site: Site) {
        site.intact = true
        anchor.revertLoc(site.tile, LOC_SHAPE)
    }

    fun untether(p: PlayerRef) {
        p[TETHER] = null
        p.setVarbit(Varbits.TEMPOROSS_TETHERED, 0)
    }

    // ---- fires (F)

    private fun ignite(anchor: PlayerRef, tile: CoordGrid, now: Long) {
        if (!fireFits(anchor, tile)) return
        val npc = anchor.spawnNpc(Npcs.TEMPOROSS_FIRE_HITBOX, tile, 0)
        anchor.addLoc(Locs.TEMPOROSS_FIRE_VISUALS, tile, LOC_SHAPE, 0)
        fires += Fire(tile, npc, now + C.FIRE_SPREAD_TICKS)
    }

    fun removeFire(anchor: PlayerRef, fire: Fire) {
        fires.remove(fire)
        fire.npc.despawn()
        anchor.revertLoc(fire.tile, LOC_SHAPE)
    }

    private fun runFires(anchor: PlayerRef, now: Long) {
        if (fires.isEmpty()) return
        for (f in fires.toList()) {
            if (f.children < C.FIRE_MAX_SPREADS && now >= f.spreadAt) {
                f.spreadAt = now + C.FIRE_SPREAD_TICKS
                if (spread(anchor, f, now)) f.children++
            }
        }
        if (now % C.FIRE_ITEM_LOSS_TICKS != 0L) return
        for (p in players) {
            val c = p.coord
            if (fires.any {
                    it.tile.level == c.level && c.x - it.tile.x in 0 until C.FIRE_SIZE && c.z - it.tile.z in 0 until C.FIRE_SIZE
                }
            ) {
                destroyItems(p, all = false, anchor = anchor)
            }
        }
    }

    /** F: a fire spreads to a free 2x2 beside it. */
    private fun spread(anchor: PlayerRef, f: Fire, now: Long): Boolean {
        val start = anchor.random(SPREAD_OFFSETS.size)
        for (k in SPREAD_OFFSETS.indices) {
            val (ox, oz) = SPREAD_OFFSETS[(start + k) % SPREAD_OFFSETS.size]
            val tile = CoordGrid(f.tile.x + ox * C.FIRE_SIZE, f.tile.z + oz * C.FIRE_SIZE, f.tile.level)
            if (!fireFits(anchor, tile)) continue
            ignite(anchor, tile, now)
            return true
        }
        return false
    }

    // ---- fishing spots

    /** OPEN-TM7: one random spot at a time is "active" (jumping fish, a 50% bonus fish) for a while. */
    private fun doubleSpots(anchor: PlayerRef, now: Long) {
        val active = doubleSpot
        if (active != null && now >= doubleUntil) {
            active.npc.transform(active.type, true)
            doubleSpot = null
            nextDouble = now + C.DOUBLE_GAP_TICKS
        } else if (active == null && now >= nextDouble && phase == Phase.ENERGY) {
            val pick = spots[anchor.random(spots.size)]
            pick.npc.transform(Npcs.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SPECIAL, true)
            doubleSpot = pick
            doubleUntil = now + C.DOUBLE_DURATION_TICKS
        }
    }

    // ---- crates

    /** Adds [count] fish of [cooked] kind to [crate] and updates how it looks. */
    fun load(anchor: PlayerRef, crate: Crate, cooked: Boolean, count: Int) {
        if (cooked) crate.cooked += count else crate.raw += count
        showCrate(anchor, crate)
    }

    private fun showCrate(anchor: PlayerRef, crate: Crate) {
        val look =
            when {
                crate.stored >= C.CRATE_OVERFLOW -> 2
                crate.stored >= C.CRATE_MODERATE -> 1
                else -> 0
            }
        if (look == crate.look) return
        crate.look = look
        anchor.addLoc(Locs.TEMPOROSS_CRATE_AMMUNITION_EMPTY_1 + crate.index + look * CRATE_LOOK_STEP, crate.tile, LOC_SHAPE, crate.angle)
    }

    // ---- players

    fun broadcast(text: String) {
        for (p in players) p.message(text)
    }

    fun addPoints(p: PlayerRef, points: Int) {
        p[POINTS] = ((p[POINTS] ?: 0) + points).coerceAtMost(C.POINTS_MAX)
    }

    /**
     * W: only hammers, ropes, buckets, buckets of water and harpoonfish can be lost; [all] takes every one,
     * otherwise a single random item.
     */
    fun destroyItems(p: PlayerRef, all: Boolean, anchor: PlayerRef) {
        val inv = p.inv(Invs.INV)
        val slots = ArrayList<Int>()
        for (slot in 0 until inv.size) if (inv.obj(slot) in LOSABLE) slots += slot
        if (slots.isEmpty()) return
        val chosen = if (all) slots else listOf(slots[anchor.random(slots.size)])
        p.transaction("tempoross destroy") {
            for (slot in chosen) removeAt(Invs.INV, slot, inv.obj(slot), inv.count(slot))
            true
        }
    }

    /** Takes down every NPC and the instance. */
    fun teardown() {
        for (f in fires) f.npc.despawn()
        fires.clear()
        for (s in spots) s.npc.despawn()
        for (c in crates) c.npc?.despawn()
        for (n in pools + hosts + listOfNotNull(boss)) n.despawn()
        pools.clear()
        hosts.clear()
        boss = null
        instance.destroy()
    }

    companion object {
        val POINTS = AttrKey<Int>("tempoross.points")
        val TETHER = AttrKey<Site>("tempoross.tether")
        val GAME = AttrKey<TemporossGame>("tempoross.game")
        val STUNNED_UNTIL = AttrKey<Long>("tempoross.stunned_until")

        /** Loc shape of scenery that stands on a tile ("centrepiece, straight"; the cache masts, totems, crates and cannons). */
        const val LOC_SHAPE = 10

        /** The values of varbit `tempoross_tethered` that draw each site tethered (multiloc children, cache locs 41352 to 41355). */
        const val SITE_NORTH = 1
        const val SITE_WEST = 2
        const val SITE_EAST = 3
        const val SITE_SOUTH = 4

        /** The tether sites' tiles and angles are those of the cache locs 41352 to 41355 in map square 47,44. */
        val NORTH_TOTEM = CoordGrid(3044, 2867, 0)
        val WEST_MAST = CoordGrid(3036, 2849, 0)
        val EAST_MAST = CoordGrid(3058, 2849, 0)
        val SOUTH_TOTEM = CoordGrid(3043, 2833, 0)

        const val NORTH = Npcs.TEMPOROSS_HARPOONFISH_FISHINGSPOT_NORTH
        const val SOUTH = Npcs.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SOUTH

        /** Cache locs: crate empty 40968, moderate 40972, overflow 40976 (four of each, step 4). */
        const val CRATE_LOOK_STEP = 4

        private const val WAVE_HIT = 1
        private const val IGNITE = 2
        private const val TORRENT_HIT = 3
        private const val TORRENT_END = 4
        private const val ATTACKS = 3
        private const val STRIKE_ATTEMPTS = 30

        /** The four neighbours of a 2x2 fire, in units of its size. */
        private val SPREAD_OFFSETS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        /** W: the only items the wave and the fires can destroy (and crystallised harpoonfish, F). */
        val LOSABLE: Set<Int> =
            setOf(
                Objs.HAMMER,
                Objs.ROPE,
                Objs.BUCKET_EMPTY,
                Objs.BUCKET_WATER,
                Objs.TEMPOROSS_RAW_HARPOONFISH,
                Objs.TEMPOROSS_HARPOONFISH,
                Objs.TEMPOROSS_CRYSTALLISED_HARPOONFISH,
            )
    }
}
