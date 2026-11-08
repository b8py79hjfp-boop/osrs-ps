package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.plugin.AttrKey
import tp.plugin.ConsumeRequest
import tp.plugin.Consumed
import tp.plugin.ContentScope
import tp.plugin.DamageRequest
import tp.plugin.EntityRef
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
import tp.util.CoordGrid
import kotlin.math.abs

/**
 * The resource rooms' food (resources.md FD1-FD6): fishing rooms with three spots and a cave snake, bat rooms, the seven
 * tiers, cooking on a fire or a lit brazier, and eating with the cook's points. Where a source is silent the number is a
 * named constant marked `OPEN-RS<n>`.
 */
internal object CoxFood {
    // ---- the tiers (FD4)

    /** FD4: the Fishing / Hunter / Cooking level of each tier, 0..6. */
    private val LEVELS = intArrayOf(1, 15, 30, 45, 60, 75, 90)

    /** FD4: Fishing XP per raw fish. */
    private val FISH_XP = doubleArrayOf(20.0, 23.0, 26.0, 29.0, 32.0, 35.0, 38.0)

    /** FD4: Hunter XP per bat. */
    private val BAT_XP = doubleArrayOf(5.0, 9.0, 13.0, 17.0, 21.0, 25.0, 29.0)

    /** FD4: Cooking XP per cooked fish or bat. */
    private val COOK_XP = doubleArrayOf(10.0, 13.0, 16.0, 19.0, 22.0, 25.0, 28.0)

    /** FD4: a tier heals 5 + 3 x tier. */
    private const val HEAL_BASE = 5
    private const val HEAL_PER_TIER = 3
    private const val TIERS = 7

    /** FD4: raw and cooked items of a tier are pairs, +2 per tier (`RAIDS_FISH<n>_RAW / _COOKED`, `RAIDS_BAT<n>_RAW / _COOKED`). */
    private const val ITEM_STEP = 2

    fun fishRaw(tier: Int): Int = Objs.RAIDS_FISH0_RAW + ITEM_STEP * tier

    fun fishCooked(tier: Int): Int = Objs.RAIDS_FISH0_COOKED + ITEM_STEP * tier

    fun batRaw(tier: Int): Int = Objs.RAIDS_BAT0_RAW + ITEM_STEP * tier

    fun batCooked(tier: Int): Int = Objs.RAIDS_BAT0_COOKED + ITEM_STEP * tier

    /** Raw item -> (tier, bat); cooked item -> tier. */
    private val RAW: Map<Int, Pair<Int, Boolean>> =
        (0 until TIERS).flatMap { listOf(fishRaw(it) to (it to false), batRaw(it) to (it to true)) }.toMap()
    private val COOKED: Map<Int, Int> = (0 until TIERS).flatMap { listOf(fishCooked(it) to it, batCooked(it) to it) }.toMap()
    private val FISH_COOKED_IDS: Set<Int> = (0 until TIERS).map { fishCooked(it) }.toSet()

    /** Every item the food rules use (the raid-only list, CX35). */
    val ITEMS: Set<Int> = RAW.keys + COOKED.keys + setOf(Objs.RAIDS_FISH_BURNT, Objs.RAIDS_BAT_BURNT, Objs.RAIDS_FISHINGBAIT)

    /** The highest tier whose level [level] reaches. */
    fun tierFor(level: Int): Int = LEVELS.indexOfLast { it <= level }.coerceAtLeast(0)

    /**
     * FD4 / OPEN-RS10: the Main page says the tier "is determined by the team's overall Hunter / Fishing level"; read as
     * the team's average at the start. A player catches the lower of that tier and the one their own level allows.
     */
    private fun teamTier(run: RaidRun, stat: Int): Int = tierFor(CoxRes.teamAverage(run, stat))

    // ---- OPEN numbers

    /** OPEN-RS26: ticks per fishing attempt; the doc gives no timing. */
    private const val FISH_TICKS = 5

    /** OPEN-RS26: ticks per bat attempt. */
    private const val CATCH_TICKS = 3

    /** OPEN-RS26 / OPEN-RS27: success chances out of 256 at level 1 and 99 (`statRandom`), for fishing and hunting. */
    private const val GATHER_LOW = 64
    private const val GATHER_HIGH = 256

    /** OPEN-RS11: ticks per cooking action; the burn chance out of 256 at level 1 and 99. */
    private const val COOK_TICKS = 4
    private const val COOK_LOW = 128
    private const val COOK_HIGH = 256

    /** OPEN-RS9: ticks between the snake moving to another spot, and between its bites. */
    private const val SNAKE_SWAP_TICKS = 50L
    private const val SNAKE_BITE_TICKS = 5L

    /** FD2: the cave snake bites for 3-6 (a roll of [SNAKE_DAMAGE_SPREAD] values from [SNAKE_DAMAGE_MIN]). */
    private const val SNAKE_DAMAGE_MIN = 3
    private const val SNAKE_DAMAGE_SPREAD = 4

    /** Cache: the cave snake (npc 7594) is size 3. */
    private const val SNAKE_SIZE = 3

    /** OPEN-RS9: tiles around the snake's centre in which players are bitten ("any player next to it"). */
    private const val SNAKE_REACH = 2

    /** OPEN-RS28: bats re-route every this many ticks, to a tile at most [BAT_RANGE] away; a caught bat reappears elsewhere. */
    private const val BAT_MOVE_TICKS = 8L
    private const val BAT_RANGE = 8

    /** FD3: a bat room holds 2-3 bats. */
    private const val BATS_MIN = 2
    private const val BATS_SPREAD = 2

    /** OPEN-RS29: Firemaking XP for lighting a kindling (KN4 "Firemaking XP 48 listed"), and how long that fire burns. */
    private const val KINDLING_FIRE_XP = 48.0
    private const val FIRE_TICKS = 100
    private const val FIRE_SHAPE = 10

    // ---- FD6 points

    /** FD6 / CX27: the cook gets 4 + 8 x tier points, and 50% more when somebody else eats it. */
    private const val POINTS_BASE = 4.0
    private const val POINTS_PER_TIER = 8.0
    private const val OTHER_EATER_BONUS = 1.5

    /** FD6: the soft cap is 20 x players pieces of food, the hard cap 40 x players. */
    private const val SOFT_CAP_PER_PLAYER = 20
    private const val HARD_CAP_PER_PLAYER = 40

    /** FD1 and the vanilla food clock: eating blocks food for 3 ticks (docs/mechanics/consumables.md E1). */
    private const val EAT_DELAY = 3L

    private val EAT_CLOCK = AttrKey<Long>("cox_eat_clock")

    private const val OP1 = 1

    private val COOK_LOCS = intArrayOf(Locs.FIRE, Locs.RAIDS_ICEDEMON_BRAZIER_LIT)

    fun register(scope: ContentScope) {
        CoxFlow.scripts[CoxKind.FARMING_FISH] = CoxRoomScript { run, cell, any -> startFish(run, cell, any) }
        CoxFlow.scripts[CoxKind.FARMING_BATS] = CoxRoomScript { run, cell, any -> startBats(run, cell, any) }
        with(scope) {
            onOpLoc(Locs.RAIDS_FLOOR_WATER_EDGE1_FISHING, OP1) { loc -> fish(loc) }
            for (t in 0 until TIERS) onOpNpc(Npcs.RAIDS_BAT_0 + t, OP1) { bat -> catchBat(bat) }
            for ((raw, _) in RAW) for (loc in COOK_LOCS) onUseOnLoc(raw, loc) { target -> cook(raw, target) }
            onUseOnItem(Objs.TINDERBOX, Objs.RAIDS_WOOD) { lightKindling() }
            for (cooked in COOKED.keys) onItemOp(cooked, "Eat") { click -> eat(click.slot, click.obj) }
            events.on<ConsumeRequest> { r ->
                if (r.invId == Invs.INV && r.obj in COOKED &&
                    r.player.inv(Invs.INV).obj(r.slot) == r.obj
                ) {
                    r.handled = r.player.eat(r.slot, r.obj)
                }
            }
        }
    }

    // ---- fishing rooms (FD2)

    private fun startFish(run: RaidRun, cell: CoxCell, any: EntityRef) {
        val spots = CoxMap.findLocs(any, run, cell, Locs.RAIDS_FLOOR_WATER_EDGE1_FISHING).map { it.coord }
        if (spots.isEmpty()) return
        val geo = CoxRoomGeo(any, run, cell)
        val tiles = spots.map { geo.spotNear(it, SNAKE_SIZE) }
        val first = run.rng.next(spots.size, "cox_snake_start")
        val room = CoxFishRoom(spots, tiles, any.spawnNpc(Npcs.RAIDS_FISHING_SNAKE, tiles[first]))
        room.snakeAt = first
        room.swapTick = any.tick + SNAKE_SWAP_TICKS
        room.biteTick = any.tick + SNAKE_BITE_TICKS
        CoxRes.state(any, run).fishRooms += room
    }

    private fun roomOf(st: CoxResState, spot: CoordGrid): CoxFishRoom? = st.fishRooms.firstOrNull { spot in it.spots }

    /** FD2: "Fish" with a fishing rod and cave worms; a catch uses one bait. */
    private suspend fun PlayerScript.fish(loc: LocRef) {
        val run = CoxFlow.runOf(player) ?: return
        val st = CoxRes.state(player, run)
        val tier = minOf(teamTier(run, Stats.FISHING), tierFor(player.level(Stats.FISHING)))
        if (CoxRes.held(player, Objs.FISHING_ROD) == 0L) return player.message(NEED_ROD)
        while (CoxRes.held(player, Objs.RAIDS_FISHINGBAIT) > 0L) {
            val room = roomOf(st, loc.coord)
            if (room != null && room.spots[room.snakeAt] == loc.coord) return player.message(BAD_MOMENT)
            player.anim(Seqs.HUMAN_FISHING_CASTING)
            delay(FISH_TICKS)
            if (!player.statRandom(Stats.FISHING, GATHER_LOW, GATHER_HIGH)) continue
            val caught =
                player.transaction("cox_fish") {
                    remove(Invs.INV, Objs.RAIDS_FISHINGBAIT, 1) &&
                        addOrDrop(Invs.INV, fishRaw(tier), 1)
                }
            if (!caught) return
            player.addXp(Stats.FISHING, FISH_XP[tier])
        }
        player.message(NEED_BAIT)
    }

    // ---- bat rooms (FD3)

    private fun startBats(run: RaidRun, cell: CoxCell, any: EntityRef) {
        val geo = CoxRoomGeo(any, run, cell)
        val tier = teamTier(run, Stats.HUNTER)
        val spots = geo.spread(BATS_MIN + run.rng.next(BATS_SPREAD, "cox_bats"), 1)
        val bats = spots.map { any.spawnNpc(Npcs.RAIDS_BAT_0 + tier, it) }
        CoxRes.state(any, run).batRooms += CoxBatRoom(bats, geo.floor)
    }

    /** FD3: a butterfly net (or a magic one), or bare hands at Hunter 99; one attempt per click. */
    private suspend fun PlayerScript.catchBat(bat: NpcRef) {
        val run = CoxFlow.runOf(player) ?: return
        val tier = bat.type - Npcs.RAIDS_BAT_0
        if (player.level(Stats.HUNTER) < LEVELS[tier]) return player.message("You need a Hunter level of ${LEVELS[tier]} to catch that.")
        val net = CoxRes.held(player, Objs.HUNTING_BUTTERFLY_NET) + CoxRes.held(player, Objs.II_MAGIC_BUTTERFLY_NET) > 0L
        if (!net && player.baseLevel(Stats.HUNTER) < Stats.MAX_LEVEL) return player.message(NEED_NET)
        player.anim(Seqs.HUMAN_BUTTERFLYNET_SWING)
        delay(CATCH_TICKS)
        if (!bat.isInWorld || player.distanceTo(bat) > 1) return
        if (!player.statRandom(Stats.HUNTER, GATHER_LOW, GATHER_HIGH)) return
        val room = CoxRes.state(player, run).batRooms.firstOrNull { bat in it.bats } ?: return
        if (!player.transaction("cox_catch_bat") { addOrDrop(Invs.INV, batRaw(tier), 1) }) return
        player.addXp(Stats.HUNTER, BAT_XP[tier])
        bat.teleport(room.floor[player.random(room.floor.size)])
    }

    // ---- cooking (FD5)

    private suspend fun PlayerScript.cook(raw: Int, target: LocRef) {
        val run = CoxFlow.runOf(player) ?: return
        val (tier, bat) = RAW[raw] ?: return
        if (player.level(Stats.COOKING) < LEVELS[tier]) return player.message("You need a Cooking level of ${LEVELS[tier]} to cook that.")
        val st = CoxRes.state(player, run)
        while (CoxRes.held(player, raw) > 0L && player.locAt(target.coord, target.shape)?.id == target.id) {
            player.anim(Seqs.HUMAN_FIRECOOKING)
            delay(COOK_TICKS)
            val good = player.statRandom(Stats.COOKING, COOK_LOW, COOK_HIGH)
            val cooked = if (bat) batCooked(tier) else fishCooked(tier)
            val burnt = if (bat) Objs.RAIDS_BAT_BURNT else Objs.RAIDS_FISH_BURNT
            if (!player.transaction("cox_cook") { remove(Invs.INV, raw, 1) && add(Invs.INV, if (good) cooked else burnt, 1) }) return
            if (good) {
                player.addXp(Stats.COOKING, COOK_XP[tier])
                CoxRes.push(st.cooks, cooked, player.username, 1)
            } else {
                player.message(BURNT)
            }
        }
    }

    /** KN4 / OPEN-RS29: kindling is lit with a tinderbox into a fire on the player's tile (no level or roll is stated). */
    private fun PlayerRef.lightKindling() {
        if (CoxFlow.runOf(this) == null) return
        if (locAt(coord, FIRE_SHAPE) != null) return message(CANT_LIGHT)
        if (!transaction("cox_light_kindling") { remove(Invs.INV, Objs.RAIDS_WOOD, 1) }) return
        anim(Seqs.HUMAN_CREATEFIRE)
        addLoc(Locs.FIRE, coord, FIRE_SHAPE, 0, FIRE_TICKS)
        addXp(Stats.FIREMAKING, KINDLING_FIRE_XP)
    }

    // ---- eating (FD4, FD6)

    private fun PlayerRef.eat(slot: Int, obj: Int): Boolean {
        val tier = COOKED[obj] ?: return false
        val now = tick
        if ((this[EAT_CLOCK] ?: 0L) > now) return false
        if (inv(Invs.INV).obj(slot) != obj || !transaction("cox_eat") { removeAt(Invs.INV, slot, obj, 1) }) return false
        this[EAT_CLOCK] = now + EAT_DELAY
        val heal = HEAL_BASE + HEAL_PER_TIER * tier
        val healed = hitpoints < maxHitpoints
        hitpoints = minOf(maxHitpoints, hitpoints + heal)
        anim(Seqs.HUMAN_EAT)
        message("You eat the ${objName(obj).lowercase()}.")
        events.publish(Consumed(this, obj, drink = false))
        if (healed) creditCook(obj, tier)
        return true
    }

    /** FD6: points to the cook of [obj] when it heals; 50% more when another player eats it; soft / hard caps by pieces eaten. */
    private fun PlayerRef.creditCook(obj: Int, tier: Int) {
        val run = CoxFlow.runOf(this) ?: return
        val st = CoxRes.state(this, run)
        val cook = CoxRes.pop(st.cooks, obj) ?: return
        val eaten = st.foodEaten.merge(cook, 1, Int::plus) ?: 1
        val soft = SOFT_CAP_PER_PLAYER * run.scaling.partySize
        val hard = HARD_CAP_PER_PLAYER * run.scaling.partySize
        // OPEN-RS12: the curve past the soft cap is not stated; linear to nothing at the hard cap.
        val share = if (eaten <= soft) 1.0 else ((hard - eaten).toDouble() / (hard - soft)).coerceIn(0.0, 1.0)
        val bonus = if (cook.equals(username, ignoreCase = true)) 1.0 else OTHER_EATER_BONUS
        CoxPoints.award(run, cook, (POINTS_BASE + POINTS_PER_TIER * tier) * bonus * share)
    }

    /** FD6: food that was not cooked here (Psykk bats from the Thieving chest) counts as cooked by [name]. */
    fun credit(run: RaidRun, any: EntityRef, cooked: Int, name: String, count: Int) {
        CoxRes.push(CoxRes.state(any, run).cooks, cooked, name, count)
    }

    // ---- the world tick: the snake and the bats

    fun tick(run: RaidRun, st: CoxResState, now: Long) {
        for (i in st.fishRooms.indices) {
            val room = st.fishRooms[i]
            if (!room.snake.isInWorld) continue
            if (now >= room.swapTick) swapSnake(run, room, now)
            if (now >= room.biteTick) bite(run, room, now)
        }
        for (i in st.batRooms.indices) {
            val room = st.batRooms[i]
            if (now >= room.moveTick) flyBats(run, room, now)
        }
    }

    /** FD2: the snake moves to another of the three spots now and then (OPEN-RS9: how often). */
    private fun swapSnake(run: RaidRun, room: CoxFishRoom, now: Long) {
        room.swapTick = now + SNAKE_SWAP_TICKS
        if (room.spots.size < 2) return
        room.snakeAt = (room.snakeAt + 1 + run.rng.next(room.spots.size - 1, "cox_snake_swap")) % room.spots.size
        room.snake.teleport(room.snakeTiles[room.snakeAt])
    }

    /** FD2: 3-6 damage to the players by the snake, and one cooked fish stolen from each (never raw). */
    private fun bite(run: RaidRun, room: CoxFishRoom, now: Long) {
        room.biteTick = now + SNAKE_BITE_TICKS
        for (p in room.snake.playersWithin(SNAKE_REACH)) {
            if (CoxFlow.runOf(p)?.id != run.id) continue
            damage(p, room.snake, SNAKE_DAMAGE_MIN + p.random(SNAKE_DAMAGE_SPREAD))
            val inv = p.inv(Invs.INV)
            val slot = (0 until inv.size).firstOrNull { inv.obj(it) in FISH_COOKED_IDS } ?: continue
            if (p.transaction("cox_snake_steal") { removeAt(Invs.INV, slot, inv.obj(slot), 1) }) p.message(STOLEN)
        }
    }

    private fun damage(p: PlayerRef, source: NpcRef, amount: Int) {
        val request = DamageRequest(p, source, amount)
        p.events.publish(request)
        if (request.handled) return
        // The combat module did not answer: the damage is dealt but never kills (as Pickpocketing's fallback).
        val dealt = minOf(amount, p.hitpoints - 1).coerceAtLeast(0)
        p.hitpoints -= dealt
    }

    private fun flyBats(run: RaidRun, room: CoxBatRoom, now: Long) {
        room.moveTick = now + BAT_MOVE_TICKS
        for (i in room.bats.indices) {
            val bat = room.bats[i]
            if (!bat.isInWorld) continue
            val to = room.floor[run.rng.next(room.floor.size, "cox_bat_move")]
            if (maxOf(abs(to.x - bat.coord.x), abs(to.z - bat.coord.z)) <= BAT_RANGE) bat.walkTo(to)
        }
    }

    /** FD2, snake. */
    private const val BAD_MOMENT = "This seems like a very bad moment to fish here."
    private const val STOLEN = "The snake stole some food off you!"

    /** OPEN-RS11 / OPEN-RS26: the lines the doc does not capture. */
    private const val NEED_ROD = "You need a fishing rod to fish here."
    private const val NEED_BAIT = "You have no more bait."
    private const val NEED_NET = "You need a butterfly net to catch that."
    private const val BURNT = "You accidentally burn it."
    private const val CANT_LIGHT = "You can't light a fire here."
}
