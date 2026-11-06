package tp.content.pvp

import tp.plugin.AttackCheck
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.DuelRules
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Jingles
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * The Al Kharid Duel Arena as it was before its removal (docs/mechanics/duel-arena.md DA1–DA9): Challenge, the duel
 * options (rules and disabled worn slots, presets), coin stakes within 10,000 of each other and at most 10M each, the
 * confirm screen, a 3-second countdown in one of the four arenas, forfeit, a safe death, and the taxed winnings.
 *
 * Rev 242 keeps the rules and confirm screens as the Emir's Arena "legacy duel" interfaces (755 / 756, client scripts
 * 6169 / 6193); the stake screen is gone, so stakes are entered in the chatbox (OPEN-DA1).
 */
public class DuelArena : ContentPlugin {
    private enum class Stage { OPTIONS, STAKES, CONFIRM, COUNTDOWN, FIGHT, ENDED }

    private class Duel(val a: PlayerRef, val b: PlayerRef) {
        var stage = Stage.OPTIONS
        var options = 0
        val accepted = HashSet<PlayerRef>()
        val stakes = HashMap<PlayerRef, Int>()

        /** Set while this plugin swaps modals itself, so the close isn't taken as a decline. */
        var switching = false

        fun other(p: PlayerRef): PlayerRef = if (p == a) b else a
    }

    override fun ContentScope.register() {
        onLogin {
            timer(AREA_TIMER, 1, soft = true) { updateArea() }
            refundEscrow(this)
        }
        onLogout { duelOf(this)?.let { onLeave(this, it) } }
        onOpPlayer(CHALLENGE_OP) { other -> challenge(player, other) }
        for ((component, bit) in OPTION_BITS) onButton(component) { toggle(this, 1 shl bit) }
        for ((component, slot) in WORN_BITS) onButton(component) { toggle(this, 1 shl (DuelRules.WORN_SHIFT + slot)) }
        onButton(Components.Pvp_Arena_Legacyduel_Options.ACCEPT) { acceptOptions(this) }
        onButton(Components.Pvp_Arena_Legacyduel_Options.DECLINE) { closeModal() }
        onButton(Components.Pvp_Arena_Legacyduel_Options.SAVE) { click -> preset(this, click.op) }
        onButton(Components.Pvp_Arena_Legacyduel_Options.WHIP) { load(this, WHIP_OPTIONS) }
        onButton(Components.Pvp_Arena_Legacyduel_Options.BOX) { load(this, BOXING_OPTIONS) }
        onButton(Components.Pvp_Arena_Legacyduel_Confirm.ACCEPT) { acceptConfirm(this) }
        onButton(Components.Pvp_Arena_Legacyduel_Confirm.DECLINE) { closeModal() }
        onModalClosed(Interfaces.PVP_ARENA_LEGACYDUEL_OPTIONS) { closed(this) }
        onModalClosed(Interfaces.PVP_ARENA_LEGACYDUEL_CONFIRM) { closed(this) }
        events.on<AttackCheck> { check -> attackCheck(check) }
        events.on<DeathEvent> { e -> (e.entity as? PlayerRef)?.let(::died) }
        onWorldStart { for (arena in ARENAS) for (t in arena.forfeits) spawnLoc(this, t) }
        onOpLoc(Locs.BR_DOOR_FORFEIT, OP_FIRST) { forfeit(player) }
        onOpLoc(Locs.PVPA_SCOREBOARD, OP_FIRST) { scoreboard(player) }
    }

    private fun died(dead: PlayerRef) {
        val duel = duelOf(dead) ?: return
        if (duel.stage == Stage.FIGHT) finish(duel, duel.other(dead), dead)
    }

    private fun spawnLoc(world: tp.plugin.WorldRef, at: CoordGrid) {
        world.addLoc(Locs.BR_DOOR_FORFEIT, at, FORFEIT_SHAPE, 0)
    }

    private fun duelOf(p: PlayerRef): Duel? = p[DUEL]

    // ---- DA1 area and Challenge

    private fun PlayerRef.updateArea() {
        val inLobby = inLobby(coord) && duelOf(this)?.stage.let { it == null || it < Stage.COUNTDOWN }
        if (this[IN_LOBBY] != inLobby) {
            this[IN_LOBBY] = inLobby
            areaQueue { playerOp(CHALLENGE_OP, if (inLobby) "Challenge" else null) }
        }
        // Nobody stays in a combat arena without a duel (a login there after a crash, a stray teleport).
        if (duelOf(this) == null && ARENAS.any { it.contains(coord) }) teleport(HOSPITAL)
    }

    /**
     * DA1: "players had to right-click their intended opponent and select the Challenge option ... accepted by
     * challenging that player in return."
     */
    private fun challenge(player: PlayerRef, other: PlayerRef) {
        if (!inLobby(player.coord) || !inLobby(other.coord)) return
        if (duelOf(player) != null) return
        if (duelOf(other) != null) {
            player.message("Other player is busy at the moment.")
            return
        }
        val theirs = other[CHALLENGED]
        if (theirs != null && theirs.first == player && player.tick - theirs.second <= REQUEST_TICKS) {
            other[CHALLENGED] = null
            start(player, other)
            return
        }
        player[CHALLENGED] = other to player.tick
        player.message("Sending duel request...")
        other.message("${player.username} wishes to duel with you.")
    }

    // ---- DA2 options

    private fun start(a: PlayerRef, b: PlayerRef) {
        val duel = Duel(a, b)
        for (p in listOf(a, b)) {
            p[DUEL] = duel
            p[SharedAttrs.DUEL_OPPONENT] = duel.other(p)
        }
        duel.options = 0
        openOptions(duel)
    }

    private fun openOptions(duel: Duel) {
        duel.stage = Stage.OPTIONS
        duel.accepted.clear()
        duel.stakes.clear()
        duel.switching = true
        for (p in listOf(duel.a, duel.b)) {
            val o = duel.other(p)
            p.openModal(Components.Toplevel.MAINMODAL, Interfaces.PVP_ARENA_LEGACYDUEL_OPTIONS)
            p.setVarp(Varps.DUELOPTIONS, duel.options)
            for ((component, _) in OPTION_BITS) p.ifEvents(component, -1..-1, tp.plugin.IfEvents.op(1))
            for ((component, _) in WORN_BITS) p.ifEvents(component, -1..-1, tp.plugin.IfEvents.op(1))
            p.ifText(Components.Pvp_Arena_Legacyduel_Options.OPPONENT_NAME, o.username)
            p.ifText(Components.Pvp_Arena_Legacyduel_Options.OPPONENT_LEVEL, "Combat level: ${o.combatLevel}")
            for ((component, stat) in STAT_TEXTS) p.ifText(component, "${o.level(stat)}")
            status(p, "")
        }
        duel.switching = false
    }

    private fun status(p: PlayerRef, text: String) {
        p.ifText(Components.Pvp_Arena_Legacyduel_Options.DUELSTATUS, text)
    }

    /** DA2: "There are 12 different options that can be turned on or off and are adjustable by either player." */
    private fun toggle(player: PlayerRef, mask: Int) {
        val duel = duelOf(player) ?: return
        if (duel.stage != Stage.OPTIONS) return
        set(duel, duel.options xor mask)
    }

    private fun set(duel: Duel, options: Int) {
        duel.options = options and VALID_MASK
        duel.accepted.clear()
        for (p in listOf(duel.a, duel.b)) {
            p.setVarp(Varps.DUELOPTIONS, duel.options)
            status(p, "")
        }
    }

    /** DA3 presets: save / load / last duel, and the fixed Whip and Boxing sets (client script 6193 values). */
    private fun preset(player: PlayerRef, op: Int) {
        val duel = duelOf(player) ?: return
        if (duel.stage != Stage.OPTIONS) return
        when (op) {
            OP_SAVE -> {
                player.setSaved(PRESET_KEY, duel.options.toLong())
                player.message("Your duel options have been saved as a preset.")
            }

            OP_LOAD -> {
                load(player, player.saved(PRESET_KEY)?.toInt() ?: return)
            }

            OP_LAST -> {
                load(player, player.saved(LAST_KEY)?.toInt() ?: return)
            }
        }
    }

    private fun load(player: PlayerRef, options: Int) {
        val duel = duelOf(player) ?: return
        if (duel.stage == Stage.OPTIONS) set(duel, options)
    }

    private fun acceptOptions(player: PlayerRef) {
        val duel = duelOf(player) ?: return
        if (duel.stage != Stage.OPTIONS) return
        val styles = (1 shl DuelRules.NO_MELEE) or (1 shl DuelRules.NO_RANGED) or (1 shl DuelRules.NO_MAGIC)
        if (duel.options and styles == styles) {
            player.message("You can't have no melee, no magic and no ranged - you'd be unable to fight!")
            return
        }
        duel.accepted += player
        val other = duel.other(player)
        status(player, "Waiting for other player...")
        status(other, "Other player has accepted.")
        if (duel.accepted.size < 2) return
        duel.stage = Stage.STAKES
        duel.accepted.clear()
        for (p in listOf(duel.a, duel.b)) askStake(duel, p)
    }

    // ---- DA4 stakes

    /** DA4: "could not stake more than 10 million coins ... the difference between the stakes could not exceed 10,000 coins". */
    private fun askStake(duel: Duel, p: PlayerRef) {
        status(p, "Enter your stake in the chatbox.")
        p.countDialog("How many coins do you want to stake? (0 for none)") { amount ->
            if (duelOf(this) !== duel || duel.stage != Stage.STAKES) return@countDialog
            val coins = funds(this)
            when {
                amount > MAX_STAKE -> {
                    message("You can't stake more than 10,000,000 coins.")
                    askStake(duel, this)
                }

                amount > coins -> {
                    message("You don't have that many coins.")
                    askStake(duel, this)
                }

                else -> {
                    duel.stakes[this] = amount
                    status(this, "Waiting for other player...")
                    if (duel.stakes.size == 2) stakesEntered(duel)
                }
            }
        }
    }

    private fun stakesEntered(duel: Duel) {
        val a = duel.stakes[duel.a] ?: 0
        val b = duel.stakes[duel.b] ?: 0
        if (kotlin.math.abs(a - b) > MAX_STAKE_DIFFERENCE) {
            for (p in listOf(duel.a, duel.b)) p.message("Stakes must be within 10,000 coins of each other.")
            openOptions(duel)
            return
        }
        openConfirm(duel)
    }

    // ---- DA5 confirm

    private fun openConfirm(duel: Duel) {
        duel.stage = Stage.CONFIRM
        duel.accepted.clear()
        duel.switching = true
        for (p in listOf(duel.a, duel.b)) {
            val o = duel.other(p)
            p.openModal(Components.Toplevel.MAINMODAL, Interfaces.PVP_ARENA_LEGACYDUEL_CONFIRM)
            val details =
                "${o.username} (level-${o.combatLevel})<br>" +
                    "Your stake: %,d coins<br>Their stake: %,d coins".format(duel.stakes[p] ?: 0, duel.stakes[o] ?: 0)
            val preset = p.saved(PRESET_KEY)?.toInt() ?: -1
            val last = p.saved(LAST_KEY)?.toInt() ?: -1
            p.clientScript(CONFIRM_SCRIPT, duel.options, preset, last, details)
            if (duel.options and (1 shl DuelRules.SHOW_INVENTORIES) != 0) {
                p.clientScript(OTHER_INV_SCRIPT, *IntArray(INV_SIZE) { o.inv(Invs.INV).obj(it) }.toTypedArray())
                p.clientScript(OTHER_WORN_SCRIPT, *IntArray(WORN_SIZE) { o.inv(Invs.WORN).obj(it) }.toTypedArray())
            }
            p.ifText(Components.Pvp_Arena_Legacyduel_Confirm.STATUS, "")
        }
        duel.switching = false
    }

    private fun acceptConfirm(player: PlayerRef) {
        val duel = duelOf(player) ?: return
        if (duel.stage != Stage.CONFIRM) return
        duel.accepted += player
        player.ifText(Components.Pvp_Arena_Legacyduel_Confirm.STATUS, "Waiting for other player...")
        duel.other(player).ifText(Components.Pvp_Arena_Legacyduel_Confirm.STATUS, "Other player has accepted.")
        if (duel.accepted.size == 2) begin(duel)
    }

    /** Closing either screen (decline, walking away, another interface) cancels the duel for both. */
    private fun closed(player: PlayerRef) {
        val duel = duelOf(player) ?: return
        if (duel.switching || duel.stage >= Stage.COUNTDOWN) return
        cancel(duel, player)
    }

    private fun cancel(duel: Duel, by: PlayerRef?) {
        duel.stage = Stage.ENDED
        for (p in listOf(duel.a, duel.b)) {
            p[DUEL] = null
            p[SharedAttrs.DUEL_OPPONENT] = null
            duel.switching = true
            if (p.isOpen(Interfaces.PVP_ARENA_LEGACYDUEL_OPTIONS) || p.isOpen(Interfaces.PVP_ARENA_LEGACYDUEL_CONFIRM)) p.closeModal()
            if (by != null && p != by) p.message("Other player declined the duel.")
        }
    }

    // ---- DA6 the fight

    /**
     * DA6: stakes are taken, items in disabled slots come off, "Boosted stats will be restored" (no drinks),
     * "Existing prayers will be stopped" (no prayer); then both are put in an arena for the 3-second countdown.
     */
    private fun begin(duel: Duel) {
        for (p in listOf(duel.a, duel.b)) {
            val stake = duel.stakes[p] ?: 0
            if (stake > funds(p)) {
                for (x in listOf(duel.a, duel.b)) x.message("${p.username} no longer has the coins they staked.")
                return cancel(duel, null)
            }
            if (removedCount(p, duel.options) > p.inv(Invs.INV).freeSlots) {
                for (x in listOf(duel.a, duel.b)) x.message("${p.username} doesn't have enough space for the items that will be taken off.")
                return cancel(duel, null)
            }
        }
        for (p in listOf(duel.a, duel.b)) {
            val stake = duel.stakes[p] ?: 0
            if (stake > 0 && !takeStake(p, stake)) return cancel(duel, null)
            if (stake > 0) p.setSaved(ESCROW_KEY, stake.toLong())
        }
        duel.stage = Stage.COUNTDOWN
        duel.switching = true
        val arena = ARENAS[duel.a.random(ARENAS.size)]
        val first = arena.randomTile(duel.a)
        val noMovement = duel.options and (1 shl DuelRules.NO_MOVEMENT) != 0
        val second = if (noMovement) arena.besides(duel.a, first) else arena.randomTile(duel.a)
        for ((p, at) in listOf(duel.a to first, duel.b to second)) {
            p.closeModal()
            p.setSaved(LAST_KEY, duel.options.toLong())
            prepare(p, duel.options)
            p.teleport(at)
            p[SharedAttrs.SAFE_DEATH_AT] = HOSPITAL
            p[IN_LOBBY] = false
            p.playerOp(CHALLENGE_OP, null)
            p.playerOp(FIGHT_OP, "Fight", top = true)
            if (noMovement) p.frozenUntil = Long.MAX_VALUE / 2
        }
        duel.switching = false
        countdown(duel, COUNTDOWN.size)
    }

    /** DA4: "only Coins and Platinum tokens can be used" — a token is worth 1,000 coins. */
    private fun funds(p: PlayerRef): Long = p.inv(Invs.INV).total(COINS) + p.inv(Invs.INV).total(Objs.PLATINUM) * TOKEN_VALUE

    /** Takes [stake] in one transaction: coins first, then whole tokens, with the change back in coins. */
    private fun takeStake(p: PlayerRef, stake: Int): Boolean {
        val coins = minOf(p.inv(Invs.INV).total(COINS), stake.toLong()).toInt()
        val rest = stake - coins
        val tokens = ((rest + TOKEN_VALUE - 1) / TOKEN_VALUE).toInt()
        val change = (tokens * TOKEN_VALUE - rest).toInt()
        return p.transaction("duel stake") {
            (coins == 0 || remove(Invs.INV, COINS, coins)) &&
                (tokens == 0 || remove(Invs.INV, Objs.PLATINUM, tokens)) &&
                (change == 0 || add(Invs.INV, COINS, change))
        }
    }

    private fun removedCount(p: PlayerRef, options: Int): Int =
        (0 until WORN_SIZE).count { slot -> options and (1 shl (DuelRules.WORN_SHIFT + slot)) != 0 && p.inv(Invs.WORN).obj(slot) >= 0 }

    private fun prepare(p: PlayerRef, options: Int) {
        for (slot in 0 until WORN_SIZE) {
            if (options and (1 shl (DuelRules.WORN_SHIFT + slot)) == 0) continue
            val obj = p.inv(Invs.WORN).obj(slot)
            if (obj >= 0) p.transaction("duel unequip") { move(Invs.WORN, slot, obj, p.inv(Invs.WORN).count(slot), Invs.INV) }
        }
        p.events.publish(tp.plugin.AppearanceChanged(p))
        if (options and (1 shl DuelRules.NO_DRINKS) != 0) {
            for (stat in 0 until STAT_COUNT) if (stat != HITPOINTS && p.level(stat) > p.baseLevel(stat)) p.setLevel(stat, p.baseLevel(stat))
        }
        if (options and (1 shl DuelRules.NO_PRAYER) != 0) {
            p.setVarbit(Varbits.PRAYER_ALLACTIVE, 0)
            p.setVarbit(Varbits.QUICKPRAYER_ACTIVE, 0)
            p.events.publish(tp.plugin.AppearanceChanged(p))
        }
    }

    /** DA6: "a 3-second countdown before the fighting actually began" — 3, 2, 1, then FIGHT! (OPEN-DA2: 2 ticks a step). */
    private fun countdown(duel: Duel, left: Int) {
        if (duel.stage != Stage.COUNTDOWN) return
        val text = if (left > 0) COUNTDOWN[COUNTDOWN.size - left] else "FIGHT!"
        for (p in listOf(duel.a, duel.b)) p.say(text)
        if (left > 0) {
            duel.a.queueHit(COUNTDOWN_STEP, strong = false) { countdown(duel, left - 1) }
            return
        }
        duel.stage = Stage.FIGHT
        for (p in listOf(duel.a, duel.b)) {
            p[SharedAttrs.DUEL_RULES] = duel.options
            p.jingle(Jingles.DUEL_ARENA)
        }
    }

    /** DA6: only your own opponent, only after "FIGHT!"; rules of style (no melee / ranged / magic). */
    private fun attackCheck(check: AttackCheck) {
        val attacker = check.attacker as? PlayerRef ?: return
        val target = check.target as? PlayerRef ?: return
        val duel = duelOf(attacker) ?: duelOf(target) ?: return
        when {
            duelOf(attacker) !== duelOf(target) -> check.deny("You can only attack your opponent.")
            duel.stage != Stage.FIGHT -> check.deny("The duel hasn't started yet!")
            styleDisabled(duel.options, check.style) -> check.deny(styleDenial(check.style))
            else -> check.allow()
        }
    }

    private fun styleDisabled(options: Int, style: tp.plugin.HitStyle?): Boolean {
        val bit =
            when (style) {
                tp.plugin.HitStyle.MELEE -> DuelRules.NO_MELEE
                tp.plugin.HitStyle.RANGED -> DuelRules.NO_RANGED
                tp.plugin.HitStyle.MAGIC -> DuelRules.NO_MAGIC
                else -> return false
            }
        return options and (1 shl bit) != 0
    }

    /** OPEN-DA4: refusal wording isn't published. */
    private fun styleDenial(style: tp.plugin.HitStyle?): String =
        when (style) {
            tp.plugin.HitStyle.MELEE -> "Melee combat has been disabled for this duel."
            tp.plugin.HitStyle.RANGED -> "Ranged combat has been disabled for this duel."
            else -> "Magic combat has been disabled for this duel."
        }

    /**
     * DA7: "players who were losing ... could retreat through one of the trapdoors ... Anything staked was awarded to
     * the other player."
     */
    private fun forfeit(player: PlayerRef) {
        val duel = duelOf(player) ?: return
        if (duel.stage != Stage.FIGHT) return
        if (duel.options and (1 shl DuelRules.NO_FORFEIT) != 0) {
            player.message("Forfeiting has been disabled for this duel.")
            return
        }
        finish(duel, duel.other(player), player)
    }

    /** A dueller who logs out loses (DA7). */
    private fun onLeave(player: PlayerRef, duel: Duel) {
        if (duel.stage >= Stage.COUNTDOWN) finish(duel, duel.other(player), player) else cancel(duel, player)
    }

    // ---- DA8 outcome

    /**
     * DA8: "players were teleported back to the building beside the arena and healed (poison and stat-reducing effects
     * are negated as well) ... the victor claimed the prize" less the tax: "less than ten million coins, then 0.25% ...
     * over ten million coins, then 0.5%".
     */
    private fun finish(duel: Duel, winner: PlayerRef, loser: PlayerRef) {
        if (duel.stage == Stage.ENDED) return
        duel.stage = Stage.ENDED
        val total = (duel.stakes[duel.a] ?: 0).toLong() + (duel.stakes[duel.b] ?: 0)
        val tax = total * (if (total < TAX_THRESHOLD) LOW_TAX else HIGH_TAX) / TAX_DIVISOR
        for (p in listOf(duel.a, duel.b)) {
            p[DUEL] = null
            p[SharedAttrs.DUEL_OPPONENT] = null
            p[SharedAttrs.DUEL_RULES] = null
            p[SharedAttrs.SAFE_DEATH_AT] = null
            p.setSaved(ESCROW_KEY, null)
            p.playerOp(FIGHT_OP, null, top = true)
            p.frozenUntil = 0
            restore(p)
            p.teleport(HOSPITAL)
        }
        val prize = (total - tax).toInt()
        if (prize > 0) winner.transaction("duel winnings") { addOrDrop(Invs.INV, COINS, prize) }
        winner.message(if (prize > 0) "You won the duel! You receive %,d coins.".format(prize) else "You won the duel!")
        loser.message("You lost the duel.")
        winner.setSaved(WINS_KEY, (winner.saved(WINS_KEY) ?: 0) + 1)
        loser.setSaved(LOSSES_KEY, (loser.saved(LOSSES_KEY) ?: 0) + 1)
        val results = winner.worldAttr(RESULTS) ?: ArrayDeque<String>().also { winner.setWorldAttr(RESULTS, it) }
        results.addFirst("${winner.username} (${winner.combatLevel}) beat ${loser.username} (${loser.combatLevel})")
        while (results.size > SCOREBOARD_SIZE) results.removeLast()
    }

    private fun restore(p: PlayerRef) {
        for (stat in 0 until STAT_COUNT) p.setLevel(stat, p.baseLevel(stat))
        p.hitpoints = p.maxHitpoints
        p.setVarp(Varps.POISON, 0)
        p.runEnergy = FULL_RUN
    }

    /** A crash or disconnect mid-duel leaves stakes in escrow: they go back to their owner (DA9). */
    private fun refundEscrow(p: PlayerRef) {
        val escrow = p.saved(ESCROW_KEY)?.toInt() ?: return
        p.setSaved(ESCROW_KEY, null)
        if (escrow > 0) p.transaction("duel refund") { addOrDrop(Invs.INV, COINS, escrow) }
        p.message("Your stake of %,d coins from an unfinished duel has been returned.".format(escrow))
    }

    /** DA8: "a scoreboard where players could view the last 50 battles on that server" (OPEN-DA3: shown as messages). */
    private fun scoreboard(p: PlayerRef) {
        val results = p.worldAttr(RESULTS)
        if (results.isNullOrEmpty()) {
            p.message("No duels have been fought on this world yet.")
            return
        }
        for (line in results.take(SCOREBOARD_SHOWN)) p.message(line)
        p.message("Your record: ${p.saved(WINS_KEY) ?: 0} wins, ${p.saved(LOSSES_KEY) ?: 0} losses.")
    }

    /** One combat arena: interior bounds (cache flood fill) and its forfeit doors. */
    private class Arena(val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int) {
        fun contains(c: CoordGrid) = c.level == 0 && c.x in minX..maxX && c.z in minZ..maxZ

        fun randomTile(p: PlayerRef): CoordGrid {
            repeat(TRIES) {
                val t = CoordGrid(minX + 1 + p.random(maxX - minX - 1), minZ + 1 + p.random(maxZ - minZ - 1))
                if (p.isWalkable(t) && t !in forfeits) return t
            }
            return CoordGrid((minX + maxX) / 2, (minZ + maxZ) / 2)
        }

        fun besides(p: PlayerRef, t: CoordGrid): CoordGrid {
            val east = CoordGrid(t.x + 1, t.z)
            return if (p.isWalkable(east)) east else CoordGrid(t.x - 1, t.z)
        }

        /** OPEN-DA5: the classic trapdoors are gone from the rev 242 map; a Forfeit door on each side wall. */
        val forfeits = listOf(CoordGrid(minX, (minZ + maxZ) / 2), CoordGrid(maxX, (minZ + maxZ) / 2))
    }

    private companion object {
        const val CHALLENGE_OP = 1
        const val FIGHT_OP = 2
        const val OP_FIRST = 1
        const val OP_SAVE = 1
        const val OP_LOAD = 2
        const val OP_LAST = 3
        const val COINS = 995
        const val TOKEN_VALUE = 1000L
        const val MAX_STAKE = 10_000_000
        const val MAX_STAKE_DIFFERENCE = 10_000
        const val TAX_THRESHOLD = 10_000_000L
        const val LOW_TAX = 25L
        const val HIGH_TAX = 50L
        const val TAX_DIVISOR = 10_000L
        const val REQUEST_TICKS = 100L
        const val COUNTDOWN_STEP = 2
        const val INV_SIZE = 28
        const val WORN_SIZE = 14
        const val STAT_COUNT = 24
        const val HITPOINTS = 3
        const val FULL_RUN = 10_000
        const val SCOREBOARD_SIZE = 50
        const val SCOREBOARD_SHOWN = 10
        const val TRIES = 30
        const val FORFEIT_SHAPE = 10

        /** Client scripts: 6193 confirm options list, 6189 / 6190 the opponent's backpack / worn items. */
        const val CONFIRM_SCRIPT = 6193
        const val OTHER_INV_SCRIPT = 6189
        const val OTHER_WORN_SCRIPT = 6190

        /**
         * Client script 6193's 'Whip' ("Only allowed the use of any one-handed weapon, no armour permitted") and
         * 'Boxing' ("No weapons or armour permitted") values on members worlds (free worlds add bit 13).
         */
        const val WHIP_OPTIONS = 229_499_867
        const val BOXING_OPTIONS = 229_630_939

        /** Rule bits 0–13 and worn slot bits 14–27 of varp `duel_options`. */
        const val VALID_MASK = (1 shl 28) - 1
        val COUNTDOWN = listOf("3", "2", "1")

        val DUEL = AttrKey<Duel>("duel")
        val CHALLENGED = AttrKey<Pair<PlayerRef, Long>>("duel_challenged")
        val IN_LOBBY = AttrKey<Boolean>("duel_in_lobby")
        val RESULTS = AttrKey<ArrayDeque<String>>("duel_results")
        val AREA_TIMER = TimerId("duel_area")
        const val PRESET_KEY = "duel_preset"
        const val LAST_KEY = "duel_last"
        const val ESCROW_KEY = "duel_escrow"
        const val WINS_KEY = "duel_wins"
        const val LOSSES_KEY = "duel_losses"

        /** Where duellers end up afterwards: the hospital beside the arenas (OPEN-DA6). */
        val HOSPITAL = CoordGrid(3364, 3267)

        /** The four combat arenas (cache flood fill of map square 52,50). */
        val ARENAS =
            listOf(
                Arena(3333, 3352, 3246, 3256),
                Arena(3369, 3388, 3246, 3256),
                Arena(3333, 3352, 3208, 3218),
                Arena(3369, 3388, 3208, 3218),
            )

        /** The Duel Arena grounds (map squares 52,50–51), outside the combat arenas. */
        @Suppress("MagicNumber") // map bounds
        fun inLobby(c: CoordGrid): Boolean = c.level == 0 && c.x in 3326..3394 && c.z in 3196..3290 && ARENAS.none { it.contains(c) }

        /** Rule buttons and their bits (client script 6169). */
        val OPTION_BITS =
            listOf(
                Components.Pvp_Arena_Legacyduel_Options.OPTION_RANGED to DuelRules.NO_RANGED,
                Components.Pvp_Arena_Legacyduel_Options.OPTION_MELEE to DuelRules.NO_MELEE,
                Components.Pvp_Arena_Legacyduel_Options.OPTION_MAGIC to DuelRules.NO_MAGIC,
                Components.Pvp_Arena_Legacyduel_Options.OPTION_SPECIALATTACKS to DuelRules.NO_SPECIAL_ATTACKS,
                Components.Pvp_Arena_Legacyduel_Options.OPTION_FUNWEAPONS to DuelRules.FUN_WEAPONS,
                Components.Pvp_Arena_Legacyduel_Options.OPTION_FORFEIT to DuelRules.NO_FORFEIT,
                Components.Pvp_Arena_Legacyduel_Options.OPTION_PRAYER to DuelRules.NO_PRAYER,
                Components.Pvp_Arena_Legacyduel_Options.OPTION_POTIONS to DuelRules.NO_DRINKS,
                Components.Pvp_Arena_Legacyduel_Options.OPTION_FOOD to DuelRules.NO_FOOD,
                Components.Pvp_Arena_Legacyduel_Options.OPTION_MOVEMENT to DuelRules.NO_MOVEMENT,
                Components.Pvp_Arena_Legacyduel_Options.OPTION_NOSWITCH to DuelRules.NO_WEAPON_SWITCH,
                Components.Pvp_Arena_Legacyduel_Options.OPTION_SHOWINV to DuelRules.SHOW_INVENTORIES,
            )

        /** Worn slot buttons (`duel_wornoption<slot>`). */
        val WORN_BITS =
            listOf(
                Components.Pvp_Arena_Legacyduel_Options.DUEL_WORNOPTION0 to 0,
                Components.Pvp_Arena_Legacyduel_Options.DUEL_WORNOPTION1 to 1,
                Components.Pvp_Arena_Legacyduel_Options.DUEL_WORNOPTION2 to 2,
                Components.Pvp_Arena_Legacyduel_Options.DUEL_WORNOPTION3 to 3,
                Components.Pvp_Arena_Legacyduel_Options.DUEL_WORNOPTION4 to 4,
                Components.Pvp_Arena_Legacyduel_Options.DUEL_WORNOPTION5 to 5,
                Components.Pvp_Arena_Legacyduel_Options.DUEL_WORNOPTION7 to 7,
                Components.Pvp_Arena_Legacyduel_Options.DUEL_WORNOPTION9 to 9,
                Components.Pvp_Arena_Legacyduel_Options.DUEL_WORNOPTION10 to 10,
                Components.Pvp_Arena_Legacyduel_Options.DUEL_WORNOPTION12 to 12,
                Components.Pvp_Arena_Legacyduel_Options.DUEL_WORNOPTION13 to 13,
            )

        /** The opponent's levels on the options screen (stat ids: attack, strength, defence, hitpoints, prayer, ranged, magic). */
        val STAT_TEXTS =
            listOf(
                Components.Pvp_Arena_Legacyduel_Options.ATTACK_LEVEL to 0,
                Components.Pvp_Arena_Legacyduel_Options.STRENGTH_LEVEL to 2,
                Components.Pvp_Arena_Legacyduel_Options.DEFENCE_LEVEL to 1,
                Components.Pvp_Arena_Legacyduel_Options.HITPOINTS_LEVEL to 3,
                Components.Pvp_Arena_Legacyduel_Options.PRAYER_LEVEL to 5,
                Components.Pvp_Arena_Legacyduel_Options.RANGED_LEVEL to 4,
                Components.Pvp_Arena_Legacyduel_Options.MAGIC_LEVEL to 6,
            )
    }
}
