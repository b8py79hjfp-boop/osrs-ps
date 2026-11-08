package tp.content.raids.toa

import tp.content.raids.RaidRun
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.ItemOnPlayerEvent
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Path of Apmeken (puzzles.md AP1-AP17): eight baboon waves at the six spawn tiles, and Apmeken's Sight dangers (roof
 * supports, floor vents, corruption) answered with the hammer and the neutralising potion. The baboons fight through the
 * generic NPC combat; the special behaviours (volatile explosion, cursed pools, shaman thralls) are hooks for the main
 * session: [addVenomPool] and the volatile / thrall TODOs listed in the hand-over.
 */
internal object ToaApmeken {
    private const val ROOM = "apmeken"
    private const val OP1 = 1

    private enum class Kind { PILLAR, VENT, CORRUPTION }

    private class Danger(val kind: Kind, val deadline: Long, var remaining: Int) {
        /** Instance tiles of the pillars / vents still unanswered. */
        val tiles = ArrayList<CoordGrid>()

        /** Corrupted raiders (lower case names) still to be cured. */
        val corrupted = ArrayList<String>()
    }

    private class Pool(val at: CoordGrid, val since: Long)

    private class State(val startTick: Long) {
        var wave = 0
        var nextWaveAt = 0L
        var nextDangerAt = 0L
        var danger: Danger? = null
        var holder: String? = null
        var holderSince = 0L
        var done = false
        val baboons = ArrayList<NpcRef>()
        val pools = ArrayList<Pool>()
        val hammered = HashSet<String>()
    }

    private val store = RunStore<State>("toa_apmeken")

    fun register(scope: ContentScope) {
        with(scope) {
            onOpLoc(Locs.TOA_PATH_APMEKEN_PILLAR_SHAKING, OP1) { loc -> repair(player, loc, needed = true) }
            onOpLoc(Locs.TOA_PATH_APMEKEN_PILLAR, OP1) { loc -> repair(player, loc, needed = false) }
            onOpLoc(Locs.TOA_PATH_APMEKEN_HAMMERS, OP1) { takeHammer(player) }
            onOpLoc(Locs.TOA_PATH_APMEKEN_POTIONS, OP1) { takePotions(player) }
            onItemOp(Objs.TOA_PATH_APMEKEN_POTION, "Pour") { pour(this) }
            events.on<ItemOnPlayerEvent> { e -> if (e.obj == Objs.TOA_PATH_APMEKEN_POTION) cureOne(e.player, e.target) }
        }
    }

    fun start(run: RaidRun, any: EntityRef) {
        val s = State(any.tick)
        s.nextWaveAt = any.tick + ToaConfig.apmekenFirstWaveDelay
        s.nextDangerAt = any.tick + ToaConfig.apmekenDangerPeriodTicks
        s.holder =
            run
                .presentPlayers(any)
                .firstOrNull()
                ?.username
                ?.lowercase()
        s.holderSince = any.tick
        store[any, run] = s
        for (p in run.presentPlayers(
            any,
        )) {
            if (p.username.equals(s.holder, ignoreCase = true)) p.spotAnim(SpotAnims.TOA_PATH_APMEKEN_GAIN_SIGHT)
        }
        for (pillar in PILLARS) any.addLoc(Locs.TOA_PATH_APMEKEN_PILLAR, run.at(pillar), CENTREPIECE, 0)
    }

    fun tick(run: RaidRun, any: EntityRef) {
        val s = store[any, run] ?: return
        if (s.done) return
        val now = any.tick
        val players = ToaPuzzles.alive(run, any)
        cycleSight(s, players, now)
        poolTick(s, players, now)
        s.baboons.removeAll { !it.isInWorld }
        waves(run, any, s, now)
        dangers(run, any, s, players, now)
        if (s.wave >= WAVES.size && s.baboons.isEmpty() && s.danger == null) finish(run, any, s)
    }

    // ---- AP7 waves

    private fun waves(run: RaidRun, any: EntityRef, s: State, now: Long) {
        if (s.wave >= WAVES.size || now < s.nextWaveAt) return
        val combat = s.baboons.count { it.type != Npcs.TOA_PATH_APMEKEN_BABOON_THRALL }
        if (s.wave > 0 && combat > ToaConfig.apmekenWaveThreshold) return
        spawnWave(run, any, s)
        s.nextWaveAt = now + 1
    }

    private fun spawnWave(run: RaidRun, any: EntityRef, s: State) {
        val wave = WAVES[s.wave]
        s.wave++
        val large = s.wave >= ToaConfig.apmekenLargeFromWave
        val types = ArrayList<Int>()
        for ((slot, n) in wave) repeat(n) { types += typeOf(slot, large, run) }
        // AP7: in a group "an extra combat baboon is added into the mix" (OPEN-PZ13: the type).
        if (run.scaling.partySize >= 2) types += ToaConfig.apmekenGroupExtraBaboon
        for ((i, type) in types.withIndex()) {
            val npc = ToaRooms.spawn(any, run, ROOM, type, SPAWN_TILES[i % SPAWN_TILES.size])
            npc.huntRange =
                if (type == Npcs.TOA_PATH_APMEKEN_BABOON_CURSED || type == Npcs.TOA_PATH_APMEKEN_BABOON_ZOMBIE) 0 else HUNT_RANGE
            s.baboons += npc
        }
    }

    /** AP7: "Special" is one of the specialists, drawn from the run's RNG (OPEN-PZ13). */
    private fun typeOf(slot: Slot, large: Boolean, run: RaidRun): Int =
        when (slot) {
            Slot.BRAWLER -> if (large) Npcs.TOA_PATH_APMEKEN_BABOON_MELEE_2 else Npcs.TOA_PATH_APMEKEN_BABOON_MELEE_1
            Slot.THROWER -> if (large) Npcs.TOA_PATH_APMEKEN_BABOON_RANGED_2 else Npcs.TOA_PATH_APMEKEN_BABOON_RANGED_1
            Slot.MAGE -> if (large) Npcs.TOA_PATH_APMEKEN_BABOON_MAGIC_2 else Npcs.TOA_PATH_APMEKEN_BABOON_MAGIC_1
            Slot.SHAMAN -> Npcs.TOA_PATH_APMEKEN_BABOON_SHAMAN
            Slot.VOLATILE -> Npcs.TOA_PATH_APMEKEN_BABOON_ZOMBIE
            Slot.CURSED -> Npcs.TOA_PATH_APMEKEN_BABOON_CURSED
            Slot.SPECIAL -> SPECIALS[run.rng.next(SPECIALS.size, "toa apmeken special")]
        }

    // ---- AP3 the Sight

    /** AP3 (OPEN-PZ11): the Sight moves to the next raider every [ToaConfig.apmekenSightCycleTicks]. */
    private fun cycleSight(s: State, players: List<PlayerRef>, now: Long) {
        if (players.size < 2 || now - s.holderSince < ToaConfig.apmekenSightCycleTicks) return
        val at = players.indexOfFirst { it.username.equals(s.holder, ignoreCase = true) }
        val next = players[(at + 1).mod(players.size)]
        players.getOrNull(at)?.spotAnim(SpotAnims.TOA_PATH_APMEKEN_LOSE_SIGHT)
        next.spotAnim(SpotAnims.TOA_PATH_APMEKEN_GAIN_SIGHT)
        s.holder = next.username.lowercase()
        s.holderSince = now
    }

    // ---- AP4-AP6 dangers

    private fun dangers(run: RaidRun, any: EntityRef, s: State, players: List<PlayerRef>, now: Long) {
        val d = s.danger
        if (d == null) {
            if (now >= s.nextDangerAt && s.wave in 1..WAVES.size && players.isNotEmpty()) begin(run, any, s, players, now)
            return
        }
        if (d.remaining <= 0) {
            s.danger = null
            s.nextDangerAt = now + ToaConfig.apmekenDangerPeriodTicks
        } else if (now >= d.deadline) {
            fail(any, d, players)
            s.danger = null
            s.nextDangerAt = now + ToaConfig.apmekenDangerPeriodTicks
        }
    }

    /** AP5: solo needs one action, then one more per player (OPEN-PZ12: the room has four pillars and four vents). */
    private fun begin(run: RaidRun, any: EntityRef, s: State, players: List<PlayerRef>, now: Long) {
        val n = players.size
        var kind = Kind.entries[run.rng.next(Kind.entries.size, "toa apmeken danger")]
        if (kind == Kind.CORRUPTION && n < 2) kind = Kind.PILLAR
        val count = minOf(n, PILLARS.size)
        val d = Danger(kind, now + ToaConfig.apmekenDangerCountdownTicks, count)
        when (kind) {
            Kind.PILLAR -> {
                for (i in pick(run, PILLARS.size, count)) {
                    val at = run.at(PILLARS[i])
                    d.tiles += at
                    any.addLoc(Locs.TOA_PATH_APMEKEN_PILLAR_SHAKING, at, CENTREPIECE, 0)
                }
            }

            Kind.VENT -> {
                for (i in pick(run, VENTS.size, count)) {
                    val at = run.at(VENTS[i])
                    d.tiles += at
                    any.spotAnimAt(at, SpotAnims.TOA_PATH_APMEKEN_SIGHT_WARNING)
                }
            }

            Kind.CORRUPTION -> {
                d.remaining = count.coerceAtMost(n)
                for (i in pick(run, n, d.remaining)) {
                    d.corrupted += players[i].username.lowercase()
                    players[i].spotAnim(SpotAnims.TOA_PATH_APMEKEN_GAIN_CURSED)
                }
            }
        }
        s.danger = d
        players.firstOrNull { it.username.equals(s.holder, ignoreCase = true) }?.message(SENSE)
    }

    /** [count] distinct indexes below [bound] from the run's RNG. */
    private fun pick(run: RaidRun, bound: Int, count: Int): List<Int> {
        val pool = (0 until bound).toMutableList()
        return List(count.coerceAtMost(bound)) { pool.removeAt(run.rng.next(pool.size, "toa apmeken pick")) }
    }

    /** AP6: every raider takes damage scaled to the actions missed. */
    private fun fail(any: EntityRef, d: Danger, players: List<PlayerRef>) {
        val damage = ToaConfig.apmekenFailureDamage * d.remaining
        val message =
            when (d.kind) {
                Kind.PILLAR -> {
                    for (at in d.tiles) {
                        any.spotAnimAt(at, SpotAnims.TOA_PATH_APMEKEN_RUBBLE)
                        any.addLoc(Locs.TOA_PATH_APMEKEN_PILLAR, at, CENTREPIECE, 0)
                    }
                    PILLAR_FAIL
                }

                Kind.VENT -> {
                    for (at in d.tiles) any.spotAnimAt(at, SpotAnims.TOA_PATH_APMEKEN_GAS)
                    VENT_FAIL
                }

                Kind.CORRUPTION -> {
                    CORRUPTION_FAIL
                }
            }
        for (p in players) {
            p.message(message)
            ToaPuzzles.hit(p, damage)
        }
    }

    private fun repair(player: PlayerRef, loc: LocRef, needed: Boolean) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        if (ToaPuzzles.count(player, Objs.HAMMER) + ToaPuzzles.count(player, Objs.DRAGON_WARHAMMER) == 0) {
            player.message("You need a hammer to repair the roof support.")
            return
        }
        val d = s.danger
        val active = needed && d != null && d.kind == Kind.PILLAR && loc.coord in d.tiles
        if (!active) {
            // AP6: repairing a support that did not need it hurts.
            player.message(WRONG_ACTION)
            ToaPuzzles.hit(player, ToaConfig.apmekenWrongActionDamage)
            return
        }
        d.tiles.remove(loc.coord)
        d.remaining--
        player.anim(REPAIR_ANIM)
        player.addLoc(Locs.TOA_PATH_APMEKEN_PILLAR, loc.coord, CENTREPIECE, 0)
    }

    private fun pour(player: PlayerRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        val d = s.danger
        val onVent = VENTS.any { run.at(it) == player.coord }
        if (d != null && d.kind == Kind.VENT && player.coord in d.tiles) {
            if (!ToaPuzzles.take(player, Objs.TOA_PATH_APMEKEN_POTION, 1, "toa apmeken vent")) return
            d.tiles.remove(player.coord)
            d.remaining--
            player.spotAnim(SpotAnims.TOA_PATH_APMEKEN_LOSE_SIGHT)
        } else if (d != null && d.kind == Kind.CORRUPTION && player.username.equals(s.holder, ignoreCase = true)) {
            if (!ToaPuzzles.take(player, Objs.TOA_PATH_APMEKEN_POTION, 1, "toa apmeken cure")) return
            // AP4: the holder's potion cures every corrupted raider standing on the holder's tile.
            for (p in ToaPuzzles.alive(run, player)) {
                if (p.coord == player.coord && p.username.lowercase() in d.corrupted) cure(d, p)
            }
        } else if (onVent) {
            // AP6: a vent cleaned without need hurts.
            player.message(WRONG_ACTION)
            ToaPuzzles.hit(player, ToaConfig.apmekenWrongActionDamage)
        } else {
            player.message("You can't pour that here.")
        }
    }

    /** AP4: the holder's potion used on one corrupted raider cures that raider. */
    private fun cureOne(user: PlayerRef, target: PlayerRef) {
        val run = ToaFlow.runOf(user) ?: return
        val s = store[user, run] ?: return
        val d = s.danger ?: return
        if (d.kind != Kind.CORRUPTION || !user.username.equals(s.holder, ignoreCase = true)) return
        if (target.username.lowercase() !in d.corrupted) return
        if (ToaPuzzles.take(user, Objs.TOA_PATH_APMEKEN_POTION, 1, "toa apmeken cure one")) cure(d, target)
    }

    private fun cure(d: Danger, p: PlayerRef) {
        if (d.corrupted.remove(p.username.lowercase())) {
            d.remaining--
            p.spotAnim(SpotAnims.TOA_PATH_APMEKEN_LOSE_SIGHT)
        }
    }

    // ---- AP2 crates

    /** AP2: one hammer per raider (the hammer goes when the challenge completes). */
    private fun takeHammer(player: PlayerRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        if (ToaPuzzles.count(player, Objs.HAMMER) > 0 || ToaPuzzles.count(player, Objs.DRAGON_WARHAMMER) > 0) return
        if (ToaPuzzles.give(player, Objs.HAMMER, 1, "toa apmeken hammer")) s.hammered += player.username.lowercase()
    }

    /** AP2: a take gives 100 neutralising potions. */
    private fun takePotions(player: PlayerRef) {
        ToaPuzzles.give(player, Objs.TOA_PATH_APMEKEN_POTION, POTIONS_PER_TAKE, "toa apmeken potions")
    }

    // ---- AP13 venom pools (hook: the cursed baboon's walk calls this)

    /** TODO hook for the main session (cursed baboon AI): leaves a venom pool at instance tile [at] for 20 s. */
    fun addVenomPool(run: RaidRun, any: EntityRef, at: CoordGrid) {
        val s = store[any, run] ?: return
        s.pools += Pool(at, any.tick)
        any.addLoc(Locs.TOA_PATH_APMEKEN_VENOM_POOL, at, CENTREPIECE, 0, POOL_TICKS)
    }

    /** AP13: 6-8 damage a tick on a pool, none on the tick it spawns. */
    private fun poolTick(s: State, players: List<PlayerRef>, now: Long) {
        if (s.pools.isEmpty()) return
        s.pools.removeAll { now - it.since >= POOL_TICKS }
        for (pool in s.pools) {
            if (now == pool.since) continue
            for (p in players) {
                if (p.coord == pool.at) ToaPuzzles.hit(p, POOL_MIN + p.random(POOL_MAX - POOL_MIN + 1))
            }
        }
    }

    // ---- AP15 completion

    private fun finish(run: RaidRun, any: EntityRef, s: State) {
        s.done = true
        // AP2: the hammer is removed when the challenge is completed.
        for (name in s.hammered) any.onlinePlayer(name)?.let { ToaPuzzles.take(it, Objs.HAMMER, 1, "toa apmeken hammer removed") }
        ToaFlow.roomDone(any, run, ROOM)
    }

    private enum class Slot { BRAWLER, THROWER, MAGE, SHAMAN, VOLATILE, CURSED, SPECIAL }

    private val SPECIALS =
        intArrayOf(Npcs.TOA_PATH_APMEKEN_BABOON_SHAMAN, Npcs.TOA_PATH_APMEKEN_BABOON_ZOMBIE, Npcs.TOA_PATH_APMEKEN_BABOON_CURSED)

    /** AP7: the solo wave table (Strat, LD `Baboon.WAVES`). */
    private val WAVES: List<List<Pair<Slot, Int>>> =
        listOf(
            listOf(Slot.BRAWLER to 2, Slot.SHAMAN to 1),
            listOf(Slot.THROWER to 2, Slot.VOLATILE to 1),
            listOf(Slot.MAGE to 2, Slot.CURSED to 1),
            listOf(Slot.THROWER to 2, Slot.SPECIAL to 3),
            listOf(Slot.MAGE to 2, Slot.SHAMAN to 1, Slot.SPECIAL to 2),
            listOf(Slot.BRAWLER to 2, Slot.SHAMAN to 1, Slot.SPECIAL to 2),
            listOf(Slot.BRAWLER to 1, Slot.THROWER to 1, Slot.SHAMAN to 1, Slot.CURSED to 2),
            listOf(Slot.SHAMAN to 1, Slot.VOLATILE to 2, Slot.SPECIAL to 2),
        )

    /** Data: the six wave spawn tiles (LD `ApmekenWaveOverlay`, region (59, 82) local (24|32|40, 40|24)). */
    private val SPAWN_TILES =
        listOf(
            CoordGrid(3800, 5288),
            CoordGrid(3808, 5288),
            CoordGrid(3816, 5288),
            CoordGrid(3800, 5272),
            CoordGrid(3808, 5272),
            CoordGrid(3816, 5272),
        )

    /** Data: the roof supports 45494 (cache map, region (59, 82)). */
    private val PILLARS = listOf(CoordGrid(3803, 5269), CoordGrid(3803, 5289), CoordGrid(3811, 5269), CoordGrid(3811, 5289))

    /** Data: the floor vents 45499 (cache map). */
    private val VENTS = listOf(CoordGrid(3800, 5276), CoordGrid(3800, 5284), CoordGrid(3816, 5276), CoordGrid(3816, 5284))

    private const val CENTREPIECE = 10

    /** OPEN-PZ13: the room is 49 tiles wide; baboons aggro across the room (the wiki gives no range). */
    private const val HUNT_RANGE = 24

    /** AP2: the potions a take gives (1 Sep 2022). */
    private const val POTIONS_PER_TAKE = 100

    /** AP13: a pool lasts 20 s = 33 ticks (rounded down, OPEN-PZ17) and hits for 6-8 a tick. */
    private const val POOL_TICKS = 33
    private const val POOL_MIN = 6
    private const val POOL_MAX = 8

    /** The hammering animation (cache seq `HUMAN_PICKUPFLOOR` stands in: no repair seq is named, OPEN-PZ4). */
    private const val REPAIR_ANIM = tp.symbols.Seqs.HUMAN_PICKUPFLOOR
    private const val SENSE = "You sense an issue somewhere in the room."
    private const val VENT_FAIL = "The fumes filling the room suddenly ignite!"
    private const val PILLAR_FAIL = "Damaged roof supports cause some debris to fall on you!"
    private const val CORRUPTION_FAIL = "Your group is overwhelmed by Amascut's corruption!"
    private const val WRONG_ACTION = "You hurt yourself by acting where nothing was wrong."
}
