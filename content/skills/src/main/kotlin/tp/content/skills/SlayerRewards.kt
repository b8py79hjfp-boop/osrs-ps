package tp.content.skills

import tp.plugin.ButtonClick
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * The Slayer Rewards interface (cache interface 426, docs/mechanics/slayer.md SL13). The client builds every tab itself from the
 * cache db tables and the variables (onload scripts 405, 190, 1089, 328, 411); the server opens it, keeps the variables and answers
 * the clicks that reach it. Found by disassembling the rev 242 client scripts:
 * - A reward row opens the client's confirm panel (script 414); "Confirm" is a child of `confirm_button` whose index is the
 *   reward's bit, or `66 + n` for the other actions (script 423, 427, 1092): 1 cancel, 2 block, 9 extend everything, 10 store,
 *   11 swap / unstore, and 3-7, 12, 8 for unblocking slots 1-7. The packet is IF_BUTTON1 on `confirm_button` with that slot.
 * - A Buy item is child `n` of `buy_items` (enum 840); its ops are Buy 1 / 5 / 10 / 50 = ops 2-5 (script 9789).
 * - The tasks tab reads the current and stored task from the scratch varps `if1`-`if6` = 261-266 (script 9736): kills left, task id,
 *   boss id; the master whose list it edits is varbit `slayer_master_in_focus`.
 */
internal class SlayerRewards(private val runtime: () -> SlayerRuntime, private val assign: SlayerAssign) {
    fun ContentScope.register() {
        onButton(Components.Slayer_Rewards.CONFIRM_BUTTON) { click -> confirm(this, click) }
        onButton(Components.Slayer_Rewards.BUY_ITEMS) { click -> buy(this, click) }
        onButton(Components.Slayer_Rewards.VIEW_TASKS) { message(VIEW_LIST_MESSAGE) }
    }

    /** Opens the rewards of [master]: the block list and prices of that master apply while it is open. */
    fun open(p: PlayerRef, master: SlayerMaster) {
        p.setVarbit(Varbits.SLAYER_MASTER_IN_FOCUS, master.id)
        sync(p, force = true)
        p.clientScript(CS_MAINMODAL_BACKGROUND, -1, -1)
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.SLAYER_REWARDS)
        p.ifEvents(Components.Slayer_Rewards.CONFIRM_BUTTON, 0..CONFIRM_CHILDREN, IfEvents.op(1))
        p.ifEvents(Components.Slayer_Rewards.BUY_ITEMS, 0..BUY_CHILDREN, BUY_OPS)
        p.ifEvents(Components.Slayer_Rewards.VIEW_TASKS, -1..-1, IfEvents.op(1))
    }

    // ---- the confirm panel

    private fun confirm(p: PlayerRef, click: ButtonClick) {
        if (click.op != 1) return
        val master = assign.masterOf(p.varbit(Varbits.SLAYER_MASTER_IN_FOCUS)) ?: return
        when (val slot = click.slot) {
            CANCEL -> cancel(p)
            BLOCK -> blockCurrent(p, master)
            EXTEND_ALL -> extendAll(p)
            STORE -> store(p)
            SWAP -> swap(p)
            in UNBLOCK -> unblock(p, master, UNBLOCK.getValue(slot))
            else -> reward(p, slot)
        }
        sync(p, force = false)
    }

    /** A reward row: buy it, or switch it off / on again. */
    private fun reward(p: PlayerRef, bit: Int) {
        val rt = runtime()
        val unlock = rt.cache.unlocksByBit[bit] ?: return
        if (unlock.tab !in TAB_UNLOCK..TAB_COSMETIC) return
        if (SlayerState.unlocked(p, bit)) {
            toggle(p, unlock)
            return
        }
        if (!rt.gate.met(p, SlayerData.REQUIREMENTS[REQUIREMENT_PREFIX + unlock.name.lowercase()].orEmpty())) {
            p.message(BIRDIE_MESSAGE)
            return
        }
        if (!pay(p, unlock.cost)) return
        SlayerState.setUnlocked(p, bit, true)
        p.message("You have unlocked ${unlock.name}.")
    }

    /**
     * A bought reward is clicked: three of them switch off and on for free with a `slayer_toggleoff_*` varbit (client script 413, bits
     * 35, 43 and 53); the others with a "Disable" op are switched off for good and cost again to buy back (script 414).
     */
    private fun toggle(p: PlayerRef, unlock: SlayerUnlock) {
        val varbit = TOGGLES[unlock.bit]
        if (varbit != null) {
            val off = p.varbit(varbit) == 0
            p.setVarbit(varbit, if (off) 1 else 0)
            p.message("${unlock.name} is now ${if (off) "disabled" else "enabled"}.")
        } else if (unlock.disableable) {
            SlayerState.setUnlocked(p, unlock.bit, false)
            p.message("${unlock.name} has been disabled.")
        }
    }

    private fun pay(p: PlayerRef, cost: Int): Boolean {
        val have = SlayerState.points(p)
        if (have < cost) {
            p.message("You need $cost Slayer reward points for that; you have $have.")
            return false
        }
        SlayerState.setPoints(p, have - cost)
        return true
    }

    /**
     * "Extend everything" (client script 1092): every extension not yet bought, for the sum of their costs; with more than one the
     * sum is cut to 95% (`interpolate(0, 95, 0, 100)`), at least 2 points.
     */
    private fun extendAll(p: PlayerRef) {
        val open =
            runtime()
                .cache.unlocksByBit.values
                .filter { it.tab == TAB_EXTEND && !SlayerState.unlocked(p, it.bit) }
        if (open.isEmpty()) return
        val sum = open.sumOf { it.cost }
        val cost = if (open.size > 1) maxOf(sum * EXTEND_ALL_PERCENT / PERCENT, EXTEND_ALL_MIN) else sum
        if (!pay(p, cost)) return
        for (u in open) SlayerState.setUnlocked(p, u.bit, true)
        p.message("You have extended all your tasks.")
    }

    // ---- the tasks tab

    private fun cancel(p: PlayerRef) {
        if (!SlayerState.hasTask(p)) return p.message(NO_TASK)
        if (!pay(p, CANCEL_COST)) return
        SlayerState.clear(p)
        p.message("Your task has been cancelled.")
    }

    private fun blockCurrent(p: PlayerRef, master: SlayerMaster) {
        val id = SlayerState.taskId(p)
        if (!SlayerState.hasTask(p) || id in UNBLOCKABLE) return p.message(NO_TASK)
        if (SlayerState.isBlocked(p, master, id)) return p.message("That task is already blocked.")
        val full = (1..SlayerData.BLOCK_SLOTS).none { SlayerState.blocked(p, master)[it - 1] == 0 && SlayerState.slotOpen(p, it) }
        if (full) return p.message("You have no free block slot; one opens for every 50 quest points.")
        if (!pay(p, master.blockCost)) return
        SlayerState.block(p, master, id)
        SlayerState.clear(p)
        p.message("Your task has been blocked.")
    }

    private fun unblock(p: PlayerRef, master: SlayerMaster, slot: Int) {
        if (SlayerState.blocked(p, master)[slot - 1] == 0) return
        p.setVarbit(master.blockVarbits[slot - 1], 0)
        p.message("The task is no longer blocked.")
    }

    private fun storageBought(p: PlayerRef): Boolean = SlayerState.unlocked(p, runtime().cache.unlock(TASK_STORAGE).bit)

    private fun store(p: PlayerRef) {
        if (!storageBought(p)) return
        if (!SlayerState.hasTask(p)) return p.message(NO_TASK)
        if (SlayerState.hasStored(p)) return p.message("You already have a task in storage.")
        SlayerState.store(p)
        SlayerState.clear(p)
        p.message("Your task has been stored.")
    }

    /** Unstores the stored task, or swaps it with the current one (free, Wiki "Task Storage"). */
    private fun swap(p: PlayerRef) {
        if (!storageBought(p) || !SlayerState.hasStored(p)) return
        val id = (p.saved(SlayerState.STORED_ID) ?: 0L).toInt()
        val left = (p.saved(SlayerState.STORED_LEFT) ?: 0L).toInt()
        val boss = (p.saved(SlayerState.STORED_BOSS) ?: 0L).toInt()
        val master = (p.saved(SlayerState.STORED_MASTER) ?: 0L).toInt()
        if (SlayerState.hasTask(p)) {
            SlayerState.store(p)
        } else {
            SlayerState.clearStored(p)
        }
        SlayerState.set(p, id, left, boss, master)
        p.message("Your stored task is now your task.")
    }

    // ---- the Buy tab

    private fun buy(p: PlayerRef, click: ButtonClick) {
        val times = BUY_TIMES[click.op] ?: return
        val cache = runtime().cache
        val obj = cache.buyObjs[click.slot] ?: return
        if (obj in BUY_ONE && times != 1) return
        if (obj == Objs.BH_RUNE_POUCH && owns(p, obj)) return p.message("You can only have one rune pouch.")
        val cost = (cache.buyCost[obj] ?: return) * times
        if (SlayerState.points(p) <
            cost
        ) {
            return p.message("You need $cost Slayer reward points for that; you have ${SlayerState.points(p)}.")
        }
        val count = (cache.buySet[obj] ?: 1) * times
        if (!p.transaction("slayer reward") { add(Invs.INV, obj, count) }) return p.message("You don't have enough inventory space.")
        SlayerState.setPoints(p, SlayerState.points(p) - cost)
    }

    private fun owns(p: PlayerRef, obj: Int): Boolean = p.inv(Invs.INV).total(obj) + p.inv(Invs.BANK).total(obj) > 0

    // ---- the scratch varps

    /** Copies the current and stored task into `if1`-`if6`, which the tasks tab reads; only while the interface is open. */
    fun sync(p: PlayerRef, force: Boolean) {
        if (!force && !p.isOpen(Interfaces.SLAYER_REWARDS)) return
        p.setVarp(Varps.IF1, SlayerState.left(p))
        p.setVarp(Varps.IF2, SlayerState.taskId(p))
        p.setVarp(Varps.IF3, SlayerState.bossId(p))
        p.setVarp(Varps.IF4, (p.saved(SlayerState.STORED_LEFT) ?: 0L).toInt())
        p.setVarp(Varps.IF5, (p.saved(SlayerState.STORED_ID) ?: 0L).toInt())
        p.setVarp(Varps.IF6, (p.saved(SlayerState.STORED_BOSS) ?: 0L).toInt())
    }

    private companion object {
        /** Client script 2524 `toplevel_mainmodal_open`: the main modal's background (colour, transparency; -1 = default). */
        const val CS_MAINMODAL_BACKGROUND = 2524

        /** `confirm_button` child index of a non-reward action: `66 + n` (client scripts 423, 427, 1092). */
        const val CONFIRM_OFFSET = 66
        const val CANCEL = CONFIRM_OFFSET + 1
        const val BLOCK = CONFIRM_OFFSET + 2
        const val EXTEND_ALL = CONFIRM_OFFSET + 9
        const val STORE = CONFIRM_OFFSET + 10
        const val SWAP = CONFIRM_OFFSET + 11

        /** Unblock of block slot 1-7 (script 426 passes n = 3, 4, 5, 6, 7, 12, 8). */
        val UNBLOCK: Map<Int, Int> =
            mapOf(
                CONFIRM_OFFSET + 3 to 1,
                CONFIRM_OFFSET + 4 to 2,
                CONFIRM_OFFSET + 5 to 3,
                CONFIRM_OFFSET + 6 to 4,
                CONFIRM_OFFSET + 7 to 5,
                CONFIRM_OFFSET + 12 to 6,
                CONFIRM_OFFSET + 8 to 7,
            )

        /** Highest child the confirm panel can create (script 423: `66 + 12`). */
        const val CONFIRM_CHILDREN = CONFIRM_OFFSET + 12

        /** The `buy_items` row count of enum 840 is 6; 10 children are enabled. */
        const val BUY_CHILDREN = 9

        /** Ops Info (1), Buy 1 / 5 / 10 / 50 (2-5) and Examine (10) of a Buy item (script 9789). */
        val BUY_OPS: Long = (1..5).fold(IfEvents.op(10)) { acc, op -> acc or IfEvents.op(op) }

        /** Quantity of the Buy ops (script 9789). */
        val BUY_TIMES: Map<Int, Int> = mapOf(2 to 1, 3 to 5, 4 to 10, 5 to 50)

        /** Items with a Buy 1 op only (script 9789): herb sack, rune pouch, looting bag. */
        val BUY_ONE: Set<Int> = setOf(Objs.SLAYER_HERB_SACK, Objs.BH_RUNE_POUCH, Objs.LOOTING_BAG)

        /** The tabs of the unlock table: unlock, extend, cosmetic (client script 407 lists them as 0, 1, 2). */
        const val TAB_UNLOCK = 0
        const val TAB_EXTEND = 1
        const val TAB_COSMETIC = 2

        /** Wiki "Slayer Rewards", Tasks: cancel 30 points. */
        const val CANCEL_COST = 30

        /** Script 1092: with several extensions left the price is 95% of their sum, at least 2. */
        const val EXTEND_ALL_PERCENT = 95
        const val PERCENT = 100
        const val EXTEND_ALL_MIN = 2

        /** Task ids the Block button is disabled for (script 9736): 97 TzTok-Jad, 98 boss, 105 TzKal-Zuk. */
        val UNBLOCKABLE: Set<Int> = setOf(97, SlayerCache.BOSS_TASK_ID, 105)

        /** The free toggles (client script 413): bit 35 Bigger and Badder, 43 Stop the Wyvern, 53 the Revenant extension. */
        val TOGGLES: Map<Int, Int> =
            mapOf(
                35 to Varbits.SLAYER_TOGGLEOFF_SUPERIORMOBS,
                43 to Varbits.SLAYER_TOGGLEOFF_FOSSILWYVERNBLOCK,
                53 to Varbits.SLAYER_TOGGLEOFF_LONGERREVENANTS,
            )

        const val TASK_STORAGE = "Task Storage"

        /** Key prefix of the requirements for buying a reward in `slayer-requirements.tsv`. */
        const val REQUIREMENT_PREFIX = "unlock:"
        const val NO_TASK = "You have no task to do that with."

        /** OPEN-SL4: no sourced text. */
        const val BIRDIE_MESSAGE = "You need level 60 Agility or Strength to unlock that."
        const val VIEW_LIST_MESSAGE = "You can block your current task with the Block button."
    }
}
