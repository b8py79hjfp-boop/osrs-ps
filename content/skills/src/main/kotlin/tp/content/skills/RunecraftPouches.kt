package tp.content.skills

import tp.plugin.BankItemOp
import tp.plugin.BankTransfer
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid

/** The kinds of essence a pouch holds (Wiki "Essence pouch"); [type] is the value of the `*_essence_pouch_type` varbit (OPEN-RC12). */
internal enum class PouchEssence(val type: Int, val obj: Int, val label: String) {
    PURE(2, Objs.BLANKRUNE_HIGH, "pure essence"),
    RUNE(1, Objs.BLANKRUNE, "rune essence"),
    GUARDIAN(4, Objs.GOTR_GUARDIAN_ESSENCE, "guardian essence"),
    DAEYALT(3, Objs.BLANKRUNE_DAEYALT, "daeyalt essence"),
}

/**
 * One essence pouch (docs/mechanics/runecraft.md RC12-RC14). [stages] are (wear counter from, capacity) rows of the Wiki's
 * degrading table; the pouch is destroyed at [destroyAt]. [size] and [type] are the cache varbits that hold the essence count
 * and its kind, so the client sees the contents.
 */
internal class Pouch(
    val name: String,
    val obj: Int,
    val degraded: Int,
    val size: Int,
    val type: Int,
    val level: Int,
    val stages: List<Pair<Int, Int>>,
    val destroyAt: Int,
) {
    /** Capacity at wear counter [wear]. */
    fun capacityAt(wear: Int): Int = stages.last { wear >= it.first }.second

    /** Whether the pouch has left its first stage (the darker item). */
    fun isDegraded(wear: Int): Boolean = stages.size > 1 && wear >= stages[1].first
}

internal object RunecraftPouchData {
    /**
     * Wiki "Essence pouch" (levels, capacity, "Degrading" table of counter ranges) and "Colossal pouch" (counter ranges at
     * level 85). The small pouch does not degrade.
     */
    @Suppress("MagicNumber")
    val POUCHES: List<Pouch> =
        listOf(
            Pouch(
                "small",
                Objs.RCU_POUCH_SMALL,
                Objs.RCU_POUCH_SMALL,
                Varbits.SMALL_ESSENCE_POUCH,
                Varbits.SMALL_ESSENCE_POUCH_TYPE,
                1,
                listOf(0 to 3),
                Int.MAX_VALUE,
            ),
            Pouch(
                "medium",
                Objs.RCU_POUCH_MEDIUM,
                Objs.RCU_POUCH_MEDIUM_DEGRADE,
                Varbits.MEDIUM_ESSENCE_POUCH,
                Varbits.MEDIUM_ESSENCE_POUCH_TYPE,
                25,
                listOf(0 to 6, 400 to 3),
                800,
            ),
            Pouch(
                "large",
                Objs.RCU_POUCH_LARGE,
                Objs.RCU_POUCH_LARGE_DEGRADE,
                Varbits.LARGE_ESSENCE_POUCH,
                Varbits.LARGE_ESSENCE_POUCH_TYPE,
                50,
                listOf(0 to 9, 400 to 7, 600 to 5, 800 to 3),
                1000,
            ),
            Pouch(
                "giant",
                Objs.RCU_POUCH_GIANT,
                Objs.RCU_POUCH_GIANT_DEGRADE,
                Varbits.GIANT_ESSENCE_POUCH,
                Varbits.GIANT_ESSENCE_POUCH_TYPE,
                75,
                listOf(0 to 12, 200 to 9, 300 to 8, 400 to 7, 600 to 6, 800 to 5, 1000 to 3),
                1200,
            ),
            Pouch(
                "colossal",
                Objs.RCU_POUCH_COLOSSAL,
                Objs.RCU_POUCH_COLOSSAL_DEGRADE,
                Varbits.COLOSSAL_ESSENCE_POUCH,
                Varbits.COLOSSAL_ESSENCE_POUCH_TYPE,
                25,
                listOf(0 to 40, 320 to 35, 565 to 30, 745 to 25, 870 to 20, 950 to 15, 995 to 10, 1015 to 5),
                1021,
            ),
        )

    /** Wiki "Colossal pouch" (Capacity): Runecraft level 25 -> 8, 50 -> 16, 75 -> 27, 85 -> 40 (OPEN-RC13: bands start there). */
    @Suppress("MagicNumber")
    val COLOSSAL_BY_LEVEL: List<Pair<Int, Int>> = listOf(25 to 8, 50 to 16, 75 to 27, 85 to 40)
}

/**
 * Essence pouches (runecraft.md RC12-RC14): Fill / Empty / Check, level requirements, capacity, degrading, the Dark Mage's
 * repairs and stitching the colossal pouch with the abyssal needle.
 */
public class RunecraftPouches : ContentPlugin {
    override fun ContentScope.register() {
        for (pouch in RunecraftPouchData.POUCHES) {
            for (obj in setOf(pouch.obj, pouch.degraded)) {
                onItemOp(obj, "Fill") { fill(this, pouch, it) }
                onItemOp(obj, "Empty") { empty(this, pouch, it) }
                onItemOp(obj, "Check") { check(this, pouch, it) }
            }
        }
        for (pouch in RunecraftPouchData.POUCHES.take(COLOSSAL_PARTS)) {
            onUseOnItem(Objs.ABYSSAL_NEEDLE, pouch.obj) { stitch(this) }
        }
        events.on<BankItemOp> { op ->
            RunecraftPouchData.POUCHES.firstOrNull { op.obj == it.obj || op.obj == it.degraded }?.let { bankOp(op, it) }
        }
        onOpNpc(Npcs.RCU_ZAMMY_MAGE2, OP_REPAIRS) { repairAll(player) }
        onWorldStart { spawnNpc(Npcs.RCU_ZAMMY_MAGE2, DARK_MAGE_AT, 0) }
    }

    // ---- fill, empty, check

    private fun holds(p: PlayerRef, pouch: Pouch, click: ButtonClick): Boolean =
        p.inv(Invs.INV).let { click.slot in 0 until it.size && it.obj(click.slot) == click.obj } &&
            (click.obj == pouch.obj || click.obj == pouch.degraded)

    private fun usable(p: PlayerRef, pouch: Pouch): Boolean {
        // Wiki "Essence pouch": "It is not possible to boost your Runecrafting level in order to use a higher levelled pouch."
        if (p.baseLevel(Stats.RUNECRAFT) >= pouch.level) return true
        p.message("You need level ${pouch.level} Runecrafting to use this pouch.")
        return false
    }

    private fun wear(p: PlayerRef, pouch: Pouch): Int = (p.saved(wearKey(pouch)) ?: 0L).toInt()

    /** The pouch's capacity now: its wear stage, and for the colossal pouch the Runecraft level too (RC13). */
    private fun capacity(p: PlayerRef, pouch: Pouch, wear: Int): Int {
        val stage = pouch.capacityAt(wear)
        if (pouch.obj != Objs.RCU_POUCH_COLOSSAL) return stage
        val byLevel = RunecraftPouchData.COLOSSAL_BY_LEVEL.lastOrNull { p.baseLevel(Stats.RUNECRAFT) >= it.first }?.second ?: 0
        return minOf(stage, byLevel)
    }

    private fun fill(p: PlayerRef, pouch: Pouch, click: ButtonClick) {
        if (!holds(p, pouch, click) || !usable(p, pouch)) return
        val inv = p.inv(Invs.INV)
        val contents = p.varbit(pouch.size)
        val kind = kindToFill(p, pouch, contents)
        if (kind == null || inv.total(kind.obj) == 0L) {
            // OPEN-RC6: texts.
            val other = "You can only store one type of essence in the pouch."
            return p.message(if (contents > 0) other else "You have no essence to put in the pouch.")
        }
        val wear = wear(p, pouch)
        val room = capacity(p, pouch, wear) - contents
        if (room <= 0) return p.message("Your pouch is full.")
        val put = minOf(room.toLong(), inv.total(kind.obj)).toInt()
        val worn = wearAfter(p, pouch, wear, put)
        if (worn >= pouch.destroyAt) return destroy(p, pouch, click)
        // Wiki "Essence pouch": a pouch that degrades while filling takes what fits the new capacity; essence beyond it comes back
        // to the backpack if there is room, else it is lost.
        val newCap = capacity(p, pouch, worn)
        val stored = minOf(put, (newCap - contents).coerceAtLeast(0))
        val excess = (contents - newCap).coerceAtLeast(0)
        if (!p.transaction("essence pouch fill") { stored == 0 || remove(Invs.INV, kind.obj, stored) }) return
        val roomBack = excess > 0 && inv.freeSlots >= excess
        if (roomBack) p.transaction("essence pouch excess") { add(Invs.INV, kind.obj, excess) }
        val finalContents = minOf(contents + stored, newCap)
        p.setVarbit(pouch.size, finalContents)
        p.setVarbit(pouch.type, if (finalContents > 0) kind.type else 0)
        if (worn == wear) return
        p.setSaved(wearKey(pouch), worn.toLong())
        if (newCap < capacity(p, pouch, wear)) p.message("Your pouch has decayed through use.")
        if (pouch.isDegraded(worn) && !pouch.isDegraded(wear)) {
            p.transaction("essence pouch degrade") { replaceAt(Invs.INV, click.slot, pouch.obj, pouch.degraded) }
        }
    }

    /** The kind in the pouch, or for an empty one the first kind in the backpack (pure first: OPEN-RC12). */
    private fun kindToFill(p: PlayerRef, pouch: Pouch, contents: Int): PouchEssence? =
        if (contents > 0) {
            PouchEssence.entries.firstOrNull { it.type == p.varbit(pouch.type) }
        } else {
            PouchEssence.entries.firstOrNull { p.inv(Invs.INV).total(it.obj) > 0 }
        }

    /** RC13: "For each essence stored in a pouch, the game chooses a random value between 0 and 3" (none while protected). */
    private fun wearAfter(p: PlayerRef, pouch: Pouch, wear: Int, put: Int): Int {
        if (pouch.destroyAt == Int.MAX_VALUE || protectedFromWear(p)) return wear
        var counter = wear
        repeat(put) { counter += p.random(WEAR_PER_ESSENCE + 1) }
        return counter
    }

    /** Wiki "Essence pouch": a worn Runecraft cape or Max cape stops degrading (the redwood abyssal lantern: OPEN-RC15). */
    private fun protectedFromWear(p: PlayerRef): Boolean {
        val name = p.objName(p.inv(Invs.WORN).obj(RunecraftData.CAPE_SLOT)).lowercase()
        return "runecraft cape" in name || "max cape" in name
    }

    private fun destroy(p: PlayerRef, pouch: Pouch, click: ButtonClick) {
        p.message("Your pouch has decayed through use.")
        p.setVarbit(pouch.size, 0)
        p.setVarbit(pouch.type, 0)
        p.setSaved(wearKey(pouch), null)
        p.transaction("essence pouch destroyed") { removeAt(Invs.INV, click.slot, click.obj, 1) }
    }

    private fun empty(p: PlayerRef, pouch: Pouch, click: ButtonClick) {
        if (!holds(p, pouch, click) || !usable(p, pouch)) return
        val contents = p.varbit(pouch.size)
        val kind = PouchEssence.entries.firstOrNull { it.type == p.varbit(pouch.type) }
        if (contents == 0 || kind == null) return p.message("There is no essence in the pouch.")
        val n = minOf(contents, p.inv(Invs.INV).freeSlots)
        if (n == 0) return p.message("You don't have enough inventory space.")
        if (!p.transaction("essence pouch empty") { add(Invs.INV, kind.obj, n) }) return
        p.setVarbit(pouch.size, contents - n)
        if (contents == n) p.setVarbit(pouch.type, 0)
    }

    private fun check(p: PlayerRef, pouch: Pouch, click: ButtonClick) {
        if (!holds(p, pouch, click)) return
        val contents = p.varbit(pouch.size)
        val kind = PouchEssence.entries.firstOrNull { it.type == p.varbit(pouch.type) }
        p.message(if (contents == 0 || kind == null) "The pouch is empty." else "The pouch contains $contents ${kind.label}.")
    }

    // ---- in the bank

    /**
     * Bank B30: "Fill" takes essence from the bank (the kind in the pouch, else the first kind banked, pure first), with the
     * same wear as filling from the backpack; "Empty" puts the pouch's essence into the bank.
     */
    private fun bankOp(op: BankItemOp, pouch: Pouch) {
        op.handled = true
        if (!usable(op.player, pouch)) return
        if (op.text == "Fill") bankFill(op, pouch) else bankEmpty(op.player, pouch)
    }

    private fun bankEmpty(p: PlayerRef, pouch: Pouch) {
        val contents = p.varbit(pouch.size)
        val kind = PouchEssence.entries.firstOrNull { it.type == p.varbit(pouch.type) }
        if (contents == 0 || kind == null) return p.message("There is no essence in the pouch.")
        p.events.publish(
            BankTransfer(p, kind.obj, contents, deposit = true, onMoved = { n ->
                p.setVarbit(pouch.size, contents - n)
                if (n == contents) p.setVarbit(pouch.type, 0)
            }),
        )
    }

    private fun bankFill(op: BankItemOp, pouch: Pouch) {
        val p = op.player
        val contents = p.varbit(pouch.size)
        val wear = wear(p, pouch)
        val room = capacity(p, pouch, wear) - contents
        if (room <= 0) return p.message("Your pouch is full.")
        val kinds = if (contents > 0) PouchEssence.entries.filter { it.type == p.varbit(pouch.type) } else PouchEssence.entries
        for (kind in kinds) {
            val t = BankTransfer(p, kind.obj, room, deposit = false, onMoved = { n -> stored(p, pouch, op.slot, kind, contents, wear, n) })
            p.events.publish(t)
            if (t.moved > 0) return
        }
        p.message("You have no essence in your bank to put in the pouch.")
    }

    /** After [n] essence came out of the bank: the pouch's count and kind, and its wear (RC13) as if filled from the backpack. */
    private fun stored(p: PlayerRef, pouch: Pouch, slot: Int, kind: PouchEssence, contents: Int, wear: Int, n: Int) {
        p.setVarbit(pouch.size, contents + n)
        p.setVarbit(pouch.type, kind.type)
        val worn = wearAfter(p, pouch, wear, n)
        if (worn == wear) return
        // OPEN-RC23: a pouch that would break while filling from the bank keeps its essence and stops one short of breaking.
        p.setSaved(wearKey(pouch), minOf(worn, pouch.destroyAt - 1).toLong())
        if (capacity(p, pouch, worn) < capacity(p, pouch, wear)) p.message("Your pouch has decayed through use.")
        if (pouch.isDegraded(worn) && !pouch.isDegraded(wear) && p.inv(Invs.INV).obj(slot) == pouch.obj) {
            p.transaction("essence pouch degrade") { replaceAt(Invs.INV, slot, pouch.obj, pouch.degraded) }
        }
    }

    // ---- repair

    /** RC14: the Dark Mage's "Repairs": every counter back to 0 and every degraded pouch, in the backpack or bank, whole again. */
    private fun repairAll(p: PlayerRef) {
        for (pouch in RunecraftPouchData.POUCHES) {
            p.setSaved(wearKey(pouch), null)
            if (pouch.degraded == pouch.obj) continue
            for (invId in listOf(Invs.INV, Invs.BANK)) {
                val view = p.inv(invId)
                for (slot in 0 until view.size) {
                    if (view.obj(slot) != pouch.degraded) continue
                    p.transaction("essence pouch repair") { replaceAt(invId, slot, pouch.degraded, pouch.obj) }
                }
            }
        }
        p.message("The dark mage repairs your pouches.")
    }

    // ---- colossal pouch

    /** RC15: abyssal needle + the four whole pouches (empty) make the colossal pouch (Wiki "Colossal pouch", Creation). */
    private fun stitch(p: PlayerRef) {
        val inv = p.inv(Invs.INV)
        if (inv.total(Objs.ABYSSAL_NEEDLE) == 0L) return
        if (p.baseLevel(Stats.CRAFTING) < COLOSSAL_CRAFTING) {
            return p.message("You need level $COLOSSAL_CRAFTING Crafting to even attempt stitching these together.")
        }
        if (p.baseLevel(Stats.RUNECRAFT) < COLOSSAL_RUNECRAFT) {
            return p.message("You need level $COLOSSAL_RUNECRAFT Runecrafting to stitch these together.")
        }
        val parts = RunecraftPouchData.POUCHES.take(COLOSSAL_PARTS)
        if (parts.any { inv.total(it.obj) == 0L }) {
            return p.message("You need a small, medium, large and giant pouch in good condition.")
        }
        if (parts.any { p.varbit(it.size) > 0 }) return p.message("Empty the pouches first.")
        val done =
            p.transaction("colossal pouch") {
                parts.all { remove(Invs.INV, it.obj, 1) } &&
                    remove(Invs.INV, Objs.ABYSSAL_NEEDLE, 1) &&
                    add(Invs.INV, Objs.RCU_POUCH_COLOSSAL, 1)
            }
        if (!done) return
        for (part in parts) p.setSaved(wearKey(part), null)
        val colossal = RunecraftPouchData.POUCHES.last()
        p.addXp(Stats.CRAFTING, COLOSSAL_CRAFTING_XP)
        p.message(
            "You attempt to stitch together all of the pouches. It's difficult, but you eventually craft a strange, kind of gross, " +
                "fleshy " +
                "container. It seems capable of storing up to ${capacity(p, colossal, 0)} essence.",
        )
    }

    private fun wearKey(pouch: Pouch): String = "runecraft.wear.${pouch.name}"

    private companion object {
        /** Cache npc "Dark Mage": op 3 "Repairs". */
        const val OP_REPAIRS = 3

        /** Small, medium, large and giant: the four pouches stitched into the colossal one. */
        const val COLOSSAL_PARTS = 4

        /** Wiki "Colossal pouch": "the unboosted levels of 25 Runecraft and 56 Crafting", 1,000 Crafting xp. */
        const val COLOSSAL_RUNECRAFT = 25
        const val COLOSSAL_CRAFTING = 56
        const val COLOSSAL_CRAFTING_XP = 1000.0

        /** Wiki "Essence pouch": "a random value between 0 and 3" per essence stored. */
        const val WEAR_PER_ESSENCE = 3

        /** Wiki "Dark Mage (Abyss)": infobox map (3039, 4834), centre of the Abyss. */
        val DARK_MAGE_AT = CoordGrid(3039, 4834, 0)
    }
}
