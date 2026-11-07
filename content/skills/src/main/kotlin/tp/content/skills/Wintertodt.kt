package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ButtonClick
import tp.plugin.Consumed
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.ItemOnItem
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * The Wintertodt minigame (docs/mechanics/wintertodt.md): the shared prison behind the Doors of Dinh, its rounds, the
 * braziers and pyromancers, the cold, points and the reward cart. Game state lives in [WintertodtGame] and advances from
 * the world tick hook (one clock for the whole prison); this class wires the interactions (light, feed, fix, chop, fletch,
 * brew, heal, drink, search) to it.
 */
public class Wintertodt : ContentPlugin {
    private val rewards = WintertodtRewards()
    private var axeLevels: Map<Int, Int> = emptyMap()
    private var game = WintertodtGame()

    /** Last HUD values sent to a player, so the interface is only written on a change. */
    internal class Hud(var energy: Int = -1, var points: Int = -1, var warmth: Int = -1)

    override fun ContentScope.register() {
        onWorldStart {
            axeLevels = AXES.keys.associateWith { objParamInt(it, Skilling.PARAM_LEVEL_REQUIRE) ?: 1 }
            rewards.resolve(this)
            game = WintertodtGame().also { it.warmIds = WARM_NAMES.flatMap { name -> objIdsNamed(name) }.toSet() }
        }
        onWorldTick { drive(tick) }
        events.on<Consumed> { eaten -> restoreWarmthOnEating(eaten) }
        for (potion in POTIONS) onItemOp(potion, DRINK) { click -> drinkPotion(click) }
        onLogin {
            setVarbit(Varbits.WINT_REWARD_POOL, (saved(REWARDS_KEY) ?: 0L).toInt())
            if (WintertodtGame.inside(coord)) enter(this)
        }
        onLogout { leave(this, closeHud = false) }
        onOpLoc(Locs.WINT_DOOR, OP_FIRST) { door() }
        onOpLoc(Locs.WINT_BRAZIER, OP_FIRST) { loc -> light(loc) }
        onOpLoc(Locs.WINT_BRAZIER_LIT, OP_FIRST) { loc -> feed(loc) }
        onOpLoc(Locs.WINT_BRAZIER_BROKEN, OP_FIRST) { loc -> fix(loc) }
        onOpLoc(Locs.WINT_ROOTS, OP_FIRST) { loc -> chop(loc) }
        onOpLoc(Locs.WINT_HERB_ROOTS, OP_FIRST) { loc -> pick(loc) }
        onOpLoc(Locs.WINT_CHEST_HAMMER, OP_FIRST) { crate(Objs.HAMMER, 1) }
        onOpLoc(Locs.WINT_CHEST_KNIFE, OP_FIRST) { crate(Objs.KNIFE, 1) }
        onOpLoc(Locs.WINT_CHEST_AXE, OP_FIRST) { crate(Objs.BRONZE_AXE, 1) }
        onOpLoc(Locs.WINT_CHEST_TINDERBOX, OP_FIRST) { crate(Objs.TINDERBOX, 1) }
        for ((op, count) in listOf(OP_FIRST to 1, OP_SECOND to VIALS_FIVE, OP_THIRD to VIALS_TEN)) {
            onOpLoc(Locs.WINT_CHEST_VIAL, op) { crate(Objs.WINT_VIAL, count) }
        }
        for (cart in CARTS) {
            onOpLoc(cart, OP_FIRST) { search(CART_BATCH_ONE) }
            onOpLoc(cart, OP_SECOND) { checkCart() }
            onOpLoc(cart, OP_THIRD) { search(CART_BATCH_BIG) }
        }
        onUseOnItem(Objs.WINT_HERB, Objs.WINT_VIAL) { brewOne() }
        onUseOnItem(Objs.KNIFE, Objs.WINT_BRUMA_ROOT) { fletch(fletchingKnife = false) }
        onUseOnItem(Objs.FLETCHING_KNIFE, Objs.WINT_BRUMA_ROOT) { fletch(fletchingKnife = true) }
        onUseOnNpc(Objs.WINT_HERB, Npcs.WINT_BREWMA) { brewAll() }
        onUseOnNpc(Objs.WINT_VIAL, Npcs.WINT_BREWMA) { brewAll() }
        for (npc in listOf(Npcs.WINT_WIZARD, Npcs.WINT_WIZARD_DOWN)) {
            onOpNpc(npc, OP_FIRST) { target -> help(target) }
            for (potion in POTIONS) onUseOnNpc(potion, npc) { target -> help(target) }
        }
    }

    // ---- entering and leaving

    private suspend fun PlayerScript.door() {
        val p = player
        if (WintertodtGame.inside(p.coord)) {
            leave(p)
            p.teleport(OUTSIDE)
            return
        }
        if (p.baseLevel(Stats.FIREMAKING) < MIN_FIREMAKING) {
            return p.message("You need a Firemaking level of $MIN_FIREMAKING to enter the Wintertodt's prison.")
        }
        p.teleport(INSIDE)
        enter(p)
    }

    private fun enter(p: PlayerRef) {
        if (p in game.players) return
        if (game.players.isEmpty()) game.reset(p)
        game.players += p
        p[WintertodtGame.POINTS] = 0
        p[WintertodtGame.WARMTH] = WintertodtGame.WARMTH_FULL
        p[NEXT_COLD] = p.tick + COLD_COOLDOWN_TICKS
        p[HUD] = Hud()
        p.setVarbit(Varbits.WINT_WARMTH, WintertodtGame.WARMTH_FULL)
        p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.WINT_STATUS)
        if (game.warmClothing(p) < WintertodtGame.WARM_CAP) {
            p.message("You feel the cold wind strike you as you enter; perhaps you should find some warmer clothes.")
        }
    }

    /** Takes [p] out of the game: points are lost (Wiki "Wintertodt" Reward points) and the HUD closes unless the player is logging out. */
    private fun leave(p: PlayerRef, closeHud: Boolean = true) {
        if (!game.players.remove(p)) return
        p[WintertodtGame.POINTS] = null
        p[WintertodtGame.WARMTH] = null
        p[HUD] = null
        p.setVarbit(Varbits.WINT_WARMTH, 0)
        if (closeHud) p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
    }

    // ---- the world tick

    /**
     * The one clock of the prison (docs/mechanics/wintertodt.md WT2 to WT5): advances the round, then each player inside takes
     * the standard attack, regenerates warmth and gets the HUD. Players who left the prison by any route are taken out here.
     * With nobody inside nothing is simulated (OPEN-WT1); the first entrant resets the game.
     */
    private fun drive(now: Long) {
        val inside = game.players
        if (inside.isEmpty()) return
        if (game.advance(inside[0], now)) endRound(inside[0], game)
        for (i in inside.indices.reversed()) {
            val p = inside.getOrNull(i) ?: continue
            if (!WintertodtGame.inside(p.coord)) {
                leave(p)
                continue
            }
            if (game.active && now >= (p[NEXT_COLD] ?: 0L)) {
                p[NEXT_COLD] = now + COLD_COOLDOWN_TICKS
                // WT4: the roll is up to the maximum energy and the attack goes through when it is below the energy left.
                if (!WintertodtGame.isSafe(p.coord) && p.random(WintertodtGame.ENERGY_MAX) < game.energy) {
                    game.cold(p, game.standardDamage(p))
                }
            }
            if (now % REGEN_INTERVAL_TICKS == 0L) p.regenerate(game)
            p.updateHud(game)
        }
    }

    // ---- warmth from food and potions

    /** WT5 (Wiki "Warmth"): food that heals at least 4 Hitpoints restores 35% warmth; triangle sandwiches and wine are excluded. */
    private fun restoreWarmthOnEating(eaten: Consumed) {
        val p = eaten.player
        if (eaten.drink || p !in game.players) return
        if (p.objName(eaten.obj).lowercase() in NO_WARMTH_FOOD) return
        restoreWarmth(p, FOOD_WARMTH_PERCENT)
    }

    /** WT5 (Wiki "Rejuvenation potion"): a dose of the clicked potion restores 30% warmth. */
    private fun PlayerRef.drinkPotion(click: ButtonClick) {
        val inv = inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != click.obj) return
        val doses = POTIONS.indexOf(click.obj) + 1
        if (doses == 0) return
        interrupt() // inventory item actions interrupt (interactions.md I7)
        val drunk =
            transaction("wintertodt drink") {
                if (doses ==
                    1
                ) {
                    removeAt(Invs.INV, click.slot, click.obj, 1)
                } else {
                    replaceAt(Invs.INV, click.slot, click.obj, POTIONS[doses - 2])
                }
            }
        if (!drunk) return
        anim(Seqs.HUMAN_EAT)
        message("You drink some of your rejuvenation potion.")
        if (this in game.players) restoreWarmth(this, POTION_WARMTH_PERCENT)
    }

    private fun restoreWarmth(p: PlayerRef, percent: Int) {
        val warmth = p[WintertodtGame.WARMTH] ?: return
        val next = minOf(WintertodtGame.WARMTH_FULL, warmth + percent * WintertodtGame.WARMTH_PER_PERCENT)
        p[WintertodtGame.WARMTH] = next
        p.setVarbit(Varbits.WINT_WARMTH, next)
    }

    /** Wiki "Warmth": 8% per minute plus 1% per warm item, i.e. (8 + items) tenths of a percent every 10 ticks. */
    private fun PlayerRef.regenerate(game: WintertodtGame) {
        val warmth = this[WintertodtGame.WARMTH] ?: return
        val gain = REGEN_BASE + game.warmClothing(this)
        val next = minOf(WintertodtGame.WARMTH_FULL, warmth + gain)
        if (next == warmth) return
        this[WintertodtGame.WARMTH] = next
        setVarbit(Varbits.WINT_WARMTH, next)
    }

    private fun PlayerRef.updateHud(game: WintertodtGame) {
        val hud = this[HUD] ?: return
        val points = this[WintertodtGame.POINTS] ?: 0
        val warmth = (this[WintertodtGame.WARMTH] ?: 0) / WintertodtGame.WARMTH_PER_PERCENT
        if (hud.energy != game.energy) {
            hud.energy = game.energy
            ifText(Components.Wint_Status.ENERGY_TITLE, "Wintertodt's Energy: ${game.energy}%")
        }
        if (hud.points != points) {
            hud.points = points
            ifText(Components.Wint_Status.POINTS, "Points: $points")
        }
        if (hud.warmth != warmth) {
            hud.warmth = warmth
            ifText(Components.Wint_Status.WARMTH_TITLE, "Warmth: $warmth%")
        }
    }

    /** WT8: the storm is subdued. Everyone with 500 points gets Firemaking xp and reward-cart rolls; the game then resets. */
    private fun endRound(anchor: PlayerRef, game: WintertodtGame) {
        for (p in game.players.toList()) {
            val points = p[WintertodtGame.POINTS] ?: 0
            if (points < WintertodtRewards.THRESHOLD) {
                p.message("You did not earn enough points to be worthy of a gift from the citizens of Kourend this time.")
            } else {
                OutfitBonuses.addFiremakingXp(p, p.baseLevel(Stats.FIREMAKING) * SUBDUE_XP_MULTIPLIER.toDouble())
                val owed = ((p.saved(REWARDS_KEY) ?: 0L) + rewards.rolls(p, points)).coerceAtMost(CART_MAX)
                p.setSaved(REWARDS_KEY, owed)
                p.setVarbit(Varbits.WINT_REWARD_POOL, owed.toInt())
                p.setVarp(Varps.TOTAL_WINTERTODT_KILLS, p.varp(Varps.TOTAL_WINTERTODT_KILLS) + 1)
            }
            p[WintertodtGame.POINTS] = 0
        }
        game.reset(anchor)
    }

    // ---- braziers

    private fun brazierIndex(loc: LocRef): Int? =
        WintertodtGame.BRAZIER_TILES.indexOfFirst { it.first == loc.coord.x && it.second == loc.coord.z }.takeIf { it >= 0 }

    private fun has(p: PlayerRef, vararg objs: Int): Boolean {
        val inv = p.inv(Invs.INV)
        val worn = p.inv(Invs.WORN)
        for (obj in objs) if (inv.total(obj) > 0 || worn.total(obj) > 0) return true
        return false
    }

    /** WT6 (Wiki "Brazier"): a tinderbox or bruma torch lights an unlit brazier in 4 ticks, 25 points and 6x the Firemaking level in xp. */
    private suspend fun PlayerScript.light(loc: LocRef) {
        val p = player
        val i = brazierIndex(loc) ?: return
        if (!ready(p, game)) return
        if (!has(
                p,
                Objs.TINDERBOX,
                Objs.WINT_TORCH,
                Objs.WINT_TORCH_OFFHAND,
            )
        ) {
            return p.message("You need a tinderbox to light the brazier.")
        }
        if (game.pyromancers[i].hp <=
            0
        ) {
            return p.message("The pyromancer is incapacitated; she must be healed before the brazier can be lit.")
        }
        val brazier = game.braziers[i]
        val seen = brazier.generation
        p.anim(Seqs.HUMAN_CREATEFIRE)
        p.weakQueue(BRAZIER_TICKS) {
            // Everyone who began before the state changed gets the reward (Wiki "Wintertodt/Strategies").
            val first = brazier.generation == seen && brazier.state == WintertodtGame.UNLIT
            val joined = brazier.generation == seen + 1 && brazier.state == WintertodtGame.LIT
            if (first) {
                game.setBrazier(p, brazier, WintertodtGame.LIT)
                game.resumeBolts(p, i)
            }
            if (first || joined) {
                OutfitBonuses.addFiremakingXp(p, p.baseLevel(Stats.FIREMAKING) * LIGHT_XP_MULTIPLIER)
                addPoints(p, LIGHT_POINTS)
            }
        }
    }

    /** WT6 (Wiki "Brazier"): a hammer repairs a broken brazier in 4 ticks, 25 points and 4x the Construction level in xp. */
    private suspend fun PlayerScript.fix(loc: LocRef) {
        val p = player
        val i = brazierIndex(loc) ?: return
        if (!ready(p, game)) return
        if (!has(
                p,
                Objs.HAMMER,
                Objs.IMCANDO_HAMMER,
                Objs.IMCANDO_HAMMER_OFFHAND,
            )
        ) {
            return p.message("You need a hammer to fix the brazier.")
        }
        val brazier = game.braziers[i]
        val seen = brazier.generation
        p.weakQueue(BRAZIER_TICKS) {
            val first = brazier.generation == seen && brazier.state == WintertodtGame.BROKEN
            val joined = brazier.generation == seen + 1 && brazier.state == WintertodtGame.UNLIT
            if (first) game.setBrazier(p, brazier, WintertodtGame.UNLIT)
            if (first || joined) {
                p.addXp(Stats.CONSTRUCTION, p.baseLevel(Stats.CONSTRUCTION) * FIX_XP_MULTIPLIER)
                addPoints(p, FIX_POINTS)
            }
        }
    }

    /** WT6: feeding a lit brazier one bruma kindling (25 points, 3.8x xp) or root (10 points, 3x xp) every [FEED_TICKS]. */
    private suspend fun PlayerScript.feed(loc: LocRef) {
        val p = player
        if (!ready(p, game)) return
        cycle(loc, FEED_TICKS) {
            if (interrupted(p)) return@cycle false
            val inv = p.inv(Invs.INV)
            val item = if (inv.total(Objs.WINT_BRUMA_KINDLING) > 0) Objs.WINT_BRUMA_KINDLING else Objs.WINT_BRUMA_ROOT
            if (inv.total(item) == 0L) return@cycle false
            if (!p.transaction("wintertodt feed") { remove(Invs.INV, item, 1) }) return@cycle false
            val kindling = item == Objs.WINT_BRUMA_KINDLING
            OutfitBonuses.addFiremakingXp(p, p.baseLevel(Stats.FIREMAKING) * if (kindling) KINDLING_XP_MULTIPLIER else ROOT_XP_MULTIPLIER)
            addPoints(p, if (kindling) KINDLING_POINTS else ROOT_POINTS)
            true
        }
    }

    private fun ready(p: PlayerRef, game: WintertodtGame): Boolean {
        if (p.baseLevel(Stats.FIREMAKING) < MIN_FIREMAKING) {
            p.message("You need a Firemaking level of $MIN_FIREMAKING to do that.")
            return false
        }
        if (!game.active) {
            p.message("The Wintertodt has not woken yet.")
            return false
        }
        return true
    }

    private fun addPoints(p: PlayerRef, points: Int) {
        p[WintertodtGame.POINTS] = (p[WintertodtGame.POINTS] ?: 0) + points
    }

    /** Runs [act] every [ticks] while the interaction is kept; [act] returns false to end it. */
    private fun PlayerScript.cycle(loc: LocRef, ticks: Int, act: () -> Boolean) {
        val p = player
        val clock = Skilling.clock(p, Skilling.locKey(loc.id, loc.coord.packed))
        val tick = p.tick
        if (clock.fresh) {
            clock.actionDelay = tick + ticks
            p[ACTION_START] = tick
        }
        if (tick == clock.actionDelay) {
            if (!act()) return Skilling.stop(p)
            clock.actionDelay = tick + ticks
        }
        keepInteraction()
    }

    /** True when a cold hit landed since the running action began (it interrupts feeding and fletching, Wiki "Wintertodt/Strategies"). */
    private fun interrupted(p: PlayerRef): Boolean = (p[WintertodtGame.HIT_AT] ?: -1L) >= (p[ACTION_START] ?: 0L)

    // ---- bruma roots, herbs and crates

    /** Wiki "Bruma roots": a 3-tick skilling timer, 0.3x the Woodcutting level in xp, the plain tree success rates (Mod Ash, OPEN-WT6). */
    private suspend fun PlayerScript.chop(loc: LocRef) {
        val p = player
        val axe = Skilling.bestTool(p, Stats.WOODCUTTING, axeLevels)
        if (axe < 0) return p.message("You need an axe to chop the roots.")
        if (p.inv(Invs.INV).freeSlots == 0) return p.message("Your inventory is too full to hold any more bruma roots.")
        cycle(loc, CHOP_TICKS) {
            p.anim(AXES.getValue(axe).anim)
            val tier = AXES.getValue(axe).tier
            if (p.statRandom(Stats.WOODCUTTING, AxeRates.REGULAR.low(tier), AxeRates.REGULAR.high(tier))) {
                if (!p.transaction("wintertodt roots") { add(Invs.INV, Objs.WINT_BRUMA_ROOT, 1) }) return@cycle false
                p.addXp(Stats.WOODCUTTING, p.baseLevel(Stats.WOODCUTTING) * CHOP_XP_MULTIPLIER)
            }
            p.inv(Invs.INV).freeSlots > 0
        }
    }

    private suspend fun PlayerScript.pick(loc: LocRef) {
        val p = player
        if (p.inv(Invs.INV).freeSlots == 0) return p.message("Your inventory is too full to hold any more bruma herbs.")
        cycle(loc, PICK_TICKS) {
            p.transaction("wintertodt herb") { add(Invs.INV, Objs.WINT_HERB, 1) } && p.inv(Invs.INV).freeSlots > 0
        }
    }

    private fun PlayerScript.crate(obj: Int, count: Int) {
        val p = player
        if (!p.transaction("wintertodt crate") { add(Invs.INV, obj, count) }) p.message("You don't have enough inventory space.")
    }

    // ---- fletching and herblore

    /**
     * WT7 (Wiki "Bruma kindling"): a knife makes kindling from a root in 4 ticks (a fletching knife: 3 from the second),
     * 0.6x the Fletching level in xp.
     */
    private fun PlayerRef.fletch(fletchingKnife: Boolean) {
        if (!WintertodtGame.inside(coord)) return message(OUTSIDE_INFLUENCE)
        if (WintertodtGame.isSafe(coord)) return message("You can't fletch here.")
        fletchStep(fletchingKnife, first = true)
    }

    private fun PlayerRef.fletchStep(fletchingKnife: Boolean, first: Boolean) {
        val started = tick
        weakQueue(if (first || !fletchingKnife) FLETCH_TICKS else FLETCH_QUICK_TICKS) {
            val p = player
            if ((p[WintertodtGame.HIT_AT] ?: -1L) > started) return@weakQueue
            if (!p.transaction("wintertodt fletch") {
                    remove(Invs.INV, Objs.WINT_BRUMA_ROOT, 1) &&
                        add(Invs.INV, Objs.WINT_BRUMA_KINDLING, 1)
                }
            ) {
                return@weakQueue
            }
            p.addXp(Stats.FLETCHING, p.baseLevel(Stats.FLETCHING) * FLETCH_XP_MULTIPLIER)
            if (p.inv(Invs.INV).total(Objs.WINT_BRUMA_ROOT) > 0) p.fletchStep(fletchingKnife, first = false)
        }
    }

    /** Wiki "Rejuvenation potion": a bruma herb on an unfinished potion, 0.1x the Herblore level in xp, only in the prison. */
    private fun PlayerRef.brewOne() {
        if (!WintertodtGame.inside(coord)) return message(OUTSIDE_INFLUENCE)
        val done =
            transaction("wintertodt brew") {
                remove(Invs.INV, Objs.WINT_HERB, 1) && remove(Invs.INV, Objs.WINT_VIAL, 1) &&
                    add(Invs.INV, Objs.WINT_POTION4, 1)
            }
        if (done) addXp(Stats.HERBLORE, baseLevel(Stats.HERBLORE) * BREW_XP_MULTIPLIER)
    }

    /** Wiki "Brew'ma": every pair of herb and unfinished potion in the backpack becomes a potion at once, with no xp. */
    private suspend fun PlayerScript.brewAll() {
        val p = player
        val inv = p.inv(Invs.INV)
        val pairs = minOf(inv.total(Objs.WINT_HERB), inv.total(Objs.WINT_VIAL)).toInt()
        if (pairs == 0) return
        p.transaction("wintertodt brew'ma") {
            remove(Invs.INV, Objs.WINT_HERB, pairs) && remove(Invs.INV, Objs.WINT_VIAL, pairs) && add(Invs.INV, Objs.WINT_POTION4, pairs)
        }
    }

    /** WT5 (Wiki "Pyromancer"): one dose, taken from the potion with fewest doses, heals a pyromancer fully for 75 points. */
    private suspend fun PlayerScript.help(target: NpcRef) {
        val p = player
        val i = game.pyromancers.indexOfFirst { it.npc == target }
        if (i < 0) return
        val pyro = game.pyromancers[i]
        if (pyro.hp >= WintertodtGame.PYRO_MAX_HP) return p.message("The pyromancer is not hurt.")
        val inv = p.inv(Invs.INV)
        val slot =
            (0 until inv.size).filter { inv.obj(it) in POTIONS }.minByOrNull { POTIONS.indexOf(inv.obj(it)) }
                ?: return p.message("You need a dose of rejuvenation potion to help her.")
        val potion = inv.obj(slot)
        val doses = POTIONS.indexOf(potion) + 1
        val taken =
            p.transaction("wintertodt heal") {
                if (doses == 1) removeAt(Invs.INV, slot, potion, 1) else replaceAt(Invs.INV, slot, potion, POTIONS[doses - 2])
            }
        if (!taken) return
        game.heal(p, i)
        addPoints(p, HEAL_POINTS)
    }

    // ---- the reward cart

    private suspend fun PlayerScript.search(batch: Int) {
        val p = player
        repeat(batch) {
            val owed = p.saved(REWARDS_KEY) ?: 0L
            if (owed <= 0L) return p.message("There is nothing left in the cart.")
            if (p.inv(Invs.INV).freeSlots == 0) return p.message("You don't have enough inventory space.")
            if (!rewards.give(p)) return
            p.setSaved(REWARDS_KEY, owed - 1)
            p.setVarbit(Varbits.WINT_REWARD_POOL, (owed - 1).toInt())
            delay(SEARCH_TICKS)
        }
    }

    private fun PlayerScript.checkCart() {
        val owed = player.saved(REWARDS_KEY) ?: 0L
        player.message("You have $owed ${if (owed == 1L) "reward" else "rewards"} waiting in the cart.")
    }

    internal companion object {
        val HUD = AttrKey<Hud>("wintertodt.hud")
        val NEXT_COLD = AttrKey<Long>("wintertodt.next_cold")
        val ACTION_START = AttrKey<Long>("wintertodt.action_start")

        /** Cache item action text of a potion. */
        const val DRINK = "Drink"

        /** Wiki "Warmth": food of 4+ Hitpoints restores 35% warmth, a rejuvenation potion dose 30%. */
        const val FOOD_WARMTH_PERCENT = 35
        const val POTION_WARMTH_PERCENT = 30

        /** Wiki "Warmth": the foods that heal 4+ Hitpoints but do not restore warmth (lower-case cache names). */
        val NO_WARMTH_FOOD = setOf("triangle sandwich", "bottle of wine")

        const val OP_FIRST = 1
        const val OP_SECOND = 2
        const val OP_THIRD = 3
        const val VIALS_FIVE = 5
        const val VIALS_TEN = 10

        /** Wiki "Wintertodt": 50 Firemaking to take part. */
        const val MIN_FIREMAKING = 50

        /** Just inside and just outside the Doors of Dinh (cache loc 29322 at 1627,3964; free tiles by `cache-dump collision`). */
        val INSIDE = CoordGrid(1630, 3968, 0)
        val OUTSIDE = CoordGrid(1630, 3962, 0)

        /** Wiki "Wintertodt/Strategies": the standard attack has a 10 tick cooldown. */
        const val COLD_COOLDOWN_TICKS = 10

        /** Wiki "Warmth": 8% a minute is 8 tenths of a percent every 10 ticks, plus one per warm item. */
        const val REGEN_INTERVAL_TICKS = 10L
        const val REGEN_BASE = 8

        /** Wiki "Wintertodt" Reward points and Reward experience tables. */
        const val LIGHT_POINTS = 25
        const val ROOT_POINTS = 10
        const val KINDLING_POINTS = 25
        const val FIX_POINTS = 25
        const val HEAL_POINTS = 75
        const val LIGHT_XP_MULTIPLIER = 6.0
        const val ROOT_XP_MULTIPLIER = 3.0
        const val KINDLING_XP_MULTIPLIER = 3.8
        const val FIX_XP_MULTIPLIER = 4.0
        const val CHOP_XP_MULTIPLIER = 0.3
        const val FLETCH_XP_MULTIPLIER = 0.6
        const val BREW_XP_MULTIPLIER = 0.1
        const val SUBDUE_XP_MULTIPLIER = 100

        /** Wiki: relighting and repairing take 4 ticks, roots a 3 tick timer, fletching 4 (3 with a fletching knife later). */
        const val BRAZIER_TICKS = 4
        const val CHOP_TICKS = 3
        const val FLETCH_TICKS = 4
        const val FLETCH_QUICK_TICKS = 3

        /** OPEN-WT4: the feeding and herb-picking cadence is not published; 3 and 2 ticks are placeholders. */
        const val FEED_TICKS = 3
        const val PICK_TICKS = 2

        /** Wiki "Reward cart": one reward per 3 ticks, five per "Big-search", 8,000 at most. */
        const val SEARCH_TICKS = 3
        const val CART_BATCH_ONE = 1
        const val CART_BATCH_BIG = 5
        const val CART_MAX = 8000L
        const val REWARDS_KEY = "wintertodt.rewards"

        const val OUTSIDE_INFLUENCE = "You can only do that within the influence of the Bruma tree."

        /** Reward cart locs: the multiloc parent and its six states (cache 55423, 55411 to 55416). */
        val CARTS = listOf(Locs.WINT_REWARD_PILE) + (Locs.WINT_REWARD_PILE_EMPTY..Locs.WINT_REWARD_PILE_FULL)

        /** Rejuvenation potions by dose count 1 to 4. */
        val POTIONS = listOf(Objs.WINT_POTION1, Objs.WINT_POTION2, Objs.WINT_POTION3, Objs.WINT_POTION4)

        /** Wiki "Warmth" warm clothing, by cache name (names the cache does not have simply match nothing). */
        val WARM_NAMES: List<String> =
            listOf(
                "Pyromancer hood",
                "Pyromancer garb",
                "Pyromancer robe",
                "Pyromancer boots",
                "Warm gloves",
                "Bruma torch",
                "Bruma torch (off-hand)",
                "Tome of Fire",
                "Santa mask",
                "Antisanta mask",
                "Bunnyman mask",
                "Larupia hat",
                "Graahk headdress",
                "Kyatt hat",
                "Chicken head",
                "Evil chicken head",
                "Santa hat",
                "Black santa hat",
                "Inverted santa hat",
                "Festive elf hat",
                "Festive games crown",
                "Bearhead",
                "Fire tiara",
                "Elemental tiara",
                "Lumberjack hat",
                "Forestry hat",
                "Snow goggles & hat",
                "Snowglobe helmet",
                "Firemaking hood",
                "Fire max hood",
                "Infernal max hood",
                "Bomber cap",
                "Cap and goggles",
                "Bobble hat",
                "Earmuffs",
                "Wolf mask",
                "Woolly hat",
                "Jester hat",
                "Tri-jester hat",
                "Slayer helmet",
                "Slayer helmet (i)",
                "Black slayer helmet",
                "Black slayer helmet (i)",
                "Green slayer helmet",
                "Green slayer helmet (i)",
                "Red slayer helmet",
                "Red slayer helmet (i)",
                "Purple slayer helmet",
                "Purple slayer helmet (i)",
                "Turquoise slayer helmet",
                "Turquoise slayer helmet (i)",
                "Hydra slayer helmet",
                "Hydra slayer helmet (i)",
                "Twisted slayer helmet",
                "Twisted slayer helmet (i)",
                "Tztok slayer helmet",
                "Tztok slayer helmet (i)",
                "Vampyric slayer helmet",
                "Vampyric slayer helmet (i)",
                "Tzkal slayer helmet",
                "Tzkal slayer helmet (i)",
                "Araxyte slayer helmet",
                "Araxyte slayer helmet (i)",
                "Hooded slayer helmet",
                "Hooded slayer helmet (i)",
                "Jester scarf",
                "Tri-jester scarf",
                "Woolly scarf",
                "Bobble scarf",
                "Gnome scarf",
                "Rainbow scarf",
                "Festive scarf",
                "Santa gloves",
                "Antisanta gloves",
                "Bunny paws",
                "Clue hunter gloves",
                "Gloves of silence",
                "Fremennik gloves",
                "Grey gloves",
                "Red gloves",
                "Yellow gloves",
                "Teal gloves",
                "Purple gloves",
                "Firemaking cape",
                "Max cape",
                "Fire cape",
                "Fire max cape",
                "Infernal cape",
                "Infernal max cape",
                "Obsidian cape",
                "Accumulator max cape",
                "Ardougne max cape",
                "Assembler max cape",
                "Mythical max cape",
                "Imbued Guthix max cape",
                "Imbued Saradomin max cape",
                "Imbued Zamorak max cape",
                "Guthix max cape",
                "Saradomin max cape",
                "Zamorak max cape",
                "Wolf cloak",
                "Rainbow cape",
                "Clue hunter cloak",
                "Staff of fire",
                "Fire battlestaff",
                "Lava battlestaff",
                "Steam battlestaff",
                "Smoke battlestaff",
                "Mystic fire staff",
                "Mystic lava staff",
                "Mystic steam staff",
                "Mystic smoke staff",
                "Twinflame staff",
                "Infernal axe",
                "Infernal pickaxe",
                "Infernal harpoon",
                "Volcanic abyssal whip",
                "Ale of the gods",
                "Dragon candle dagger",
                "Lit bug lantern",
                "Santa jacket",
                "Antisanta jacket",
                "Bunny top",
                "Clue hunter garb",
                "Polar camo top",
                "Wood camo top",
                "Jungle camo top",
                "Desert camo top",
                "Larupia top",
                "Graahk top",
                "Kyatt top",
                "Bomber jacket",
                "Yak-hide armour (top)",
                "Chicken wings",
                "Evil chicken wings",
                "Ugly halloween jumper",
                "Christmas jumper",
                "Oldschool jumper",
                "Rainbow jumper",
                "Icy jumper",
                "Santa pantaloons",
                "Antisanta pantaloons",
                "Bunny legs",
                "Clue hunter trousers",
                "Polar camo legs",
                "Wood camo legs",
                "Jungle camo legs",
                "Desert camo legs",
                "Larupia legs",
                "Graahk legs",
                "Kyatt legs",
                "Yak-hide armour (legs)",
                "Chicken legs",
                "Evil chicken legs",
                "Ring of the elements",
                "Santa boots",
                "Antisanta boots",
                "Bunny feet",
                "Clue hunter boots",
                "Chicken feet",
                "Evil chicken feet",
                "Festive elf slippers",
                "Mole slippers",
                "Holy moleys",
                "Bear feet",
                "Demon feet",
                "Frog slippers",
                "Bob the cat slippers",
                "Jad slippers",
                "Cow slippers",
                "Brutus slippers",
                "Lumberjack top",
                "Lumberjack legs",
                "Lumberjack boots",
                "Spotted cape",
                "Spottier cape",
                "Obsidian armour",
                "Bunny ears",
                "Strung rabbit foot",
                "Wise old man's santa hat",
                "Hat of the Eye",
                "Abyssal lantern",
                "Burning amulet",
                "Magma helm",
                "Blazing blowpipe",
                "Scorching bow",
                "Burning claws",
                "Emberlight",
                "Helm of Raedwald",
                "Beer belly sweater",
                "Jad jumper",
                "Devil's element",
                "Searing boots",
                "Amulet of fire",
                "Elemental amulet",
            )
    }
}
