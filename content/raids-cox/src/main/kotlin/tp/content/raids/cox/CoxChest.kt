package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.ButtonClick
import tp.plugin.IfEvents
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * The Ancient chest and its light (cox.md CX57; lobby-rewards CL7, CR12; client-contract 2.9 and 5.4). The rewards are paid
 * into the claim inventory `RAIDS_REWARDS` 581 by raids-core when the Olm dies; once the raid is complete the closed crystal
 * 30027 becomes the chest 30028 ("Search"), which opens interface 539 over inv 581. The loot beam 28848 is a multi on the
 * viewer's varbit 5456, so each player sees their own colour. Leaving with loot left sends it to the private storage
 * (CR12, flag [CoxUiConfig.unclaimedLootToStorage]) or the ground.
 */
internal object CoxChest {
    // ---- the chest

    /** The raid is complete: the crystal opens into the chest (visible to all) and every member's light is set. */
    fun completed(world: WorldRef, run: RaidRun) {
        val any = run.members.keys.firstNotNullOfOrNull { world.onlinePlayer(it) } ?: return
        val at = CoxMap.olm(run, CHEST)
        val crystal = SHAPES.asList().firstNotNullOfOrNull { any.locAt(at, it) }?.takeIf { it.id == Locs.RAIDS_REWARD_CRYSTAL }
        if (crystal != null) any.addLoc(Locs.RAIDS_REWARD_CHEST, at, crystal.shape, crystal.angle)
        for (p in run.presentPlayers(any)) light(p, run, any.tick)
    }

    /** "Search" on the Ancient chest 30028: the claim inventory in interface 539, set before it opens (client-contract 2.9). */
    fun open(p: PlayerRef, loc: LocRef) {
        val run = CoxFlow.runOf(p) ?: return
        if (run.state != RunState.Completed || run.instance?.let { loc.coord in it } != true) return
        p.transmitInv(Invs.RAIDS_REWARDS)
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.RAIDS_REWARDS)
        p.ifEvents(Components.Raids_Rewards.ITEMS, 0 until p.inv(Invs.RAIDS_REWARDS).size, ITEM_OPS)
        p.ifEvents(Components.Raids_Rewards.INVENTORY, NO_SLOT..NO_SLOT, IfEvents.op(OP_TAKE_ALL))
        p.ifEvents(Components.Raids_Rewards.BANK, NO_SLOT..NO_SLOT, IfEvents.op(OP_BANK_ALL))
        p.ifEvents(Components.Raids_Rewards.DISCARD, NO_SLOT..NO_SLOT, IfEvents.op(OP_DISCARD_ALL))
        sync(p, run)
    }

    // ---- interface 539

    /** An item slot's op: 1 Take, 2 Bank, 3 Discard (OPEN-CXUI6: the client script labels are not in the dump). */
    fun itemOp(p: PlayerRef, click: ButtonClick) {
        val inv = p.inv(Invs.RAIDS_REWARDS)
        if (click.slot !in 0 until inv.size || click.obj < 0 || inv.obj(click.slot) != click.obj) return
        when (click.op) {
            OP_TAKE -> {
                take(p, click.slot, click.obj)
            }

            OP_BANK -> {
                bank(p, click.slot, click.obj)
            }

            OP_DISCARD -> {
                val slot = click.slot
                val obj = click.obj
                val count = inv.count(slot)
                p.choiceDialog("Discard $count x ${p.objName(obj)}?", listOf(YES_DISCARD, NO)) { pick ->
                    if (pick == 1) discard(this, slot, obj, count)
                }
            }
        }
        refresh(p)
    }

    /** 539:8 "Take-all": every stack into the backpack as far as it fits. */
    fun takeAll(p: PlayerRef) {
        for (slot in 0 until p.inv(Invs.RAIDS_REWARDS).size) {
            val obj = p.inv(Invs.RAIDS_REWARDS).obj(slot)
            if (obj >= 0 && !take(p, slot, obj)) break
        }
        refresh(p)
    }

    /** 539:6 "Bank-all": every stack into the bank without a bank open (notes as the item they stand for). */
    fun bankAll(p: PlayerRef) {
        for (slot in 0 until p.inv(Invs.RAIDS_REWARDS).size) {
            val obj = p.inv(Invs.RAIDS_REWARDS).obj(slot)
            if (obj >= 0 && !bank(p, slot, obj)) break
        }
        refresh(p)
    }

    /** 539:10 "Discard-all": asked first; destroys the whole claim inventory. */
    fun discardAll(p: PlayerRef) {
        if (isEmpty(p)) return
        p.choiceDialog(DISCARD_ALL, listOf(YES_DISCARD_ALL, NO)) { pick ->
            if (pick == 1) {
                val inv = inv(this)
                val slots = (0 until inv.size).filter { inv.obj(it) >= 0 }
                if (slots.isNotEmpty()) {
                    transaction("cox chest discard all") { slots.all { removeAt(Invs.RAIDS_REWARDS, it, inv.obj(it), inv.count(it)) } }
                }
                refresh(this)
            }
        }
    }

    private fun inv(p: PlayerRef) = p.inv(Invs.RAIDS_REWARDS)

    private fun take(p: PlayerRef, slot: Int, obj: Int): Boolean {
        val count = p.inv(Invs.RAIDS_REWARDS).count(slot)
        val ok = p.transaction("cox chest take") { move(Invs.RAIDS_REWARDS, slot, obj, count, Invs.INV) }
        if (!ok) p.message(NO_INV_SPACE)
        return ok
    }

    private fun bank(p: PlayerRef, slot: Int, obj: Int): Boolean {
        if (p.objParam(obj, PARAM_NO_BANK, 0) != 0) {
            p.message(NO_BANK_MESSAGE)
            return false
        }
        val count = p.inv(Invs.RAIDS_REWARDS).count(slot)
        val real = p.unnoted(obj)
        val ok = p.transaction("cox chest bank") { removeAt(Invs.RAIDS_REWARDS, slot, obj, count) && add(Invs.BANK, real, count) }
        if (!ok) p.message(NO_BANK_SPACE)
        return ok
    }

    private fun discard(p: PlayerRef, slot: Int, obj: Int, count: Int) {
        val inv = p.inv(Invs.RAIDS_REWARDS)
        if (slot !in 0 until inv.size || inv.obj(slot) != obj || inv.count(slot) != count) return
        p.transaction("cox chest discard") { removeAt(Invs.RAIDS_REWARDS, slot, obj, count) }
        refresh(p)
    }

    private fun isEmpty(p: PlayerRef): Boolean {
        val inv = p.inv(Invs.RAIDS_REWARDS)
        return inv.freeSlots == inv.size
    }

    private fun refresh(p: PlayerRef) {
        val run = CoxFlow.runOf(p) ?: return
        sync(p, run)
    }

    // ---- the light (5456) and the book icon (5457)

    /** Rewards left in the claim inventory change the light and the book icon. */
    fun sync(p: PlayerRef, run: RaidRun) {
        light(p, run, p.tick)
    }

    /** Called often for each completed run: the colours alternate, so the varbit is re-evaluated, changed values only. */
    fun tick(world: WorldRef) {
        if (world.tick % LIGHT_TICKS != 0L) return
        for (run in Raids.of(world).activeRuns) {
            if (run.raid.id != CoxRaid.ID || run.state != RunState.Completed) continue
            val inst = run.instance ?: continue
            for (m in run.members.values) {
                if (!m.inside || !m.online) continue
                val p = world.onlinePlayer(m.name)?.takeIf { it.coord in inst } ?: continue
                light(p, run, world.tick)
            }
        }
    }

    /**
     * CR12: white for commons, purple for a unique, turquoise for dust, green for the kit; several at once alternate (every
     * [LIGHT_ALTERNATE_TICKS], OPEN-CXUI7). The light shows while rewards wait. 5457 is 1 while the claim inventory holds the
     * Dark journal (OPEN-CXUI8: whether the client's sixth icon replaces that slot).
     */
    private fun light(p: PlayerRef, run: RaidRun, now: Long) {
        val inv = p.inv(Invs.RAIDS_REWARDS)
        val waiting = inv.freeSlots < inv.size
        val tags =
            run
                .member(p.username)
                ?.reward
                ?.tags
                .orEmpty()
        val unique = CoxLoot.TAG_UNIQUE in tags
        val dust = CoxLoot.TAG_DUST in tags
        val kit = CoxLoot.TAG_KIT in tags
        val count = if (waiting) 1 + (if (dust) 1 else 0) + (if (kit) 1 else 0) else 0
        // The k-th colour of the set {unique or common, dust, kit}, k = the current alternation step.
        val step = if (count == 0) 0 else ((now / LIGHT_ALTERNATE_TICKS) % count).toInt()
        val value =
            when {
                count == 0 -> BEAM_NONE
                step == 0 -> if (unique) BEAM_SPECIAL else BEAM_BASIC
                step == 1 && dust -> BEAM_DUST
                else -> BEAM_KIT
            }
        setIfChanged(p, Varbits.RAIDS_CLIENT_REWARDINDICATOR, value)
        setIfChanged(p, Varbits.RAIDS_CLIENT_NEEDSREWARDBOOK, if (inv.total(Objs.RAIDS_REWARD_BOOK) > 0) 1 else 0)
    }

    // ---- leaving

    /**
     * [p] left the raid: the chest light goes and rewards not claimed go to the private storage when there is room, else
     * onto the ground at the chest (CR12; Ultimate Ironmen cannot use storage, so theirs drop).
     */
    fun left(p: PlayerRef, run: RaidRun) {
        setIfChanged(p, Varbits.RAIDS_CLIENT_REWARDINDICATOR, BEAM_NONE)
        setIfChanged(p, Varbits.RAIDS_CLIENT_NEEDSREWARDBOOK, 0)
        val inv = p.inv(Invs.RAIDS_REWARDS)
        val slots = (0 until inv.size).filter { inv.obj(it) >= 0 }
        if (slots.isEmpty()) return
        val toStorage =
            CoxUiConfig.unclaimedLootToStorage && !(p.varbit(Varbits.IRONMAN) == IRONMAN_ULTIMATE && !CoxUiConfig.uimPrivateStorage)
        val chest = run.instance?.let { CoxMap.olm(run, CHEST) }
        for (slot in slots) {
            val obj = inv.obj(slot)
            val count = inv.count(slot)
            if (toStorage &&
                p.transaction("cox unclaimed to storage") { move(Invs.RAIDS_REWARDS, slot, obj, count, Invs.RAIDS_PRIVATESTORAGE) }
            ) {
                continue
            }
            if (chest != null) p.transaction("cox unclaimed to ground") { dropAt(Invs.RAIDS_REWARDS, slot, obj, count, chest, p.username) }
        }
    }

    private fun setIfChanged(p: PlayerRef, varbit: Int, value: Int) {
        if (p.varbit(varbit) != value) p.setVarbit(varbit, value)
    }

    // ---- constants

    /** Client-contract 6.6: the reward crystal 30027 stands at (3233, 5751) in the Olm square. */
    private val CHEST = CoordGrid(3233, 5751)

    /** Centrepiece loc shapes (10 plain, 11 diagonal). */
    private val SHAPES = intArrayOf(10, 11)

    /** Client-contract 5.4: the values of `RAIDS_CLIENT_REWARDINDICATOR` 5456 (multi 28848). */
    private const val BEAM_NONE = 0
    private const val BEAM_BASIC = 1
    private const val BEAM_SPECIAL = 2
    private const val BEAM_DUST = 3
    private const val BEAM_KIT = 4

    /** OPEN-CXUI7: CR12 says only that colours "alternate"; the period is a guess, in ticks. */
    private const val LIGHT_ALTERNATE_TICKS = 4L

    /** The light is re-evaluated this often (ticks); a multiple of nothing else, so the alternation keeps its rhythm. */
    private const val LIGHT_TICKS = 2L

    /** Varbit 1777 `IRONMAN`: 2 = Ultimate (client-contract 2.7). */
    private const val IRONMAN_ULTIMATE = 2

    private const val NO_SLOT = -1
    private const val OP_TAKE = 1
    private const val OP_BANK = 2
    private const val OP_DISCARD = 3

    /** Client-contract 2.9: 539:8 Take-all is op 1, 539:6 Bank-all and 539:10 Discard-all are op 6. */
    private const val OP_TAKE_ALL = 1
    private const val OP_BANK_ALL = 6
    private const val OP_DISCARD_ALL = 6

    @Suppress("MagicNumber") // op numbers
    private val ITEM_OPS: Long = IfEvents.op(OP_TAKE) or IfEvents.op(OP_BANK) or IfEvents.op(OP_DISCARD)

    /** Obj param 59: the item cannot be banked (content/gameframe Bank `PARAM_NO_BANK`). */
    private const val PARAM_NO_BANK = 59

    private const val NO_BANK_MESSAGE = "A magical force prevents you from banking this item!"
    private const val NO_INV_SPACE = "You don't have enough inventory space."
    private const val NO_BANK_SPACE = "You don't have enough space in your bank account."

    // OPEN-CXUI1: the texts are not captured.
    private const val DISCARD_ALL = "Discard all of your loot?"
    private const val YES_DISCARD = "Yes, discard it."
    private const val YES_DISCARD_ALL = "Yes, discard it all."
    private const val NO = "No."
}
