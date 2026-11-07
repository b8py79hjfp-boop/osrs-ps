package tp.content.combat

import tp.plugin.PlayerRef
import tp.plugin.RunePouch
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * Storage items on death (docs/mechanics/death.md DE11-DE12). Wiki "Rune pouch": a PvP death in the Wilderness or on a
 * PvP world loses the pouch unless trouver-locked, and the trouver parchment "does not protect runes stored within"; a
 * PvM death sends the pouch to the grave with its runes. Wiki "Looting bag": a PvP death always loses the bag and the
 * killer "will get everything inside the bag, excluding food and potions"; a PvM death in the Wilderness destroys the bag
 * and its contents go to the gravestone.
 */
internal object StorageDeath {
    private val LOOTING_BAGS = setOf(Objs.LOOTING_BAG, Objs.LOOTING_BAG_OPEN)
    private val UNLOCKED_POUCHES = setOf(Objs.BH_RUNE_POUCH, Objs.DIVINE_RUNE_POUCH)

    /** Never among the kept items: the looting bag on any Wilderness death (PvP world: any PvP death), an unlocked pouch on a PvP death. */
    fun alwaysLost(obj: Int, pvp: Boolean, inWilderness: Boolean): Boolean =
        (obj in LOOTING_BAGS && (pvp || inWilderness)) || (obj in UNLOCKED_POUCHES && pvp)

    private fun objAt(p: PlayerRef, unit: Triple<Int, Int, Int>): Int = p.inv(unit.first).obj(unit.second)

    /** PvM death in the Wilderness: removes the lost looting bags and returns them (their contents go to the grave). */
    fun destroyLootingBag(dead: PlayerRef, lost: List<Triple<Int, Int, Int>>): List<Triple<Int, Int, Int>> {
        val bags = lost.filter { objAt(dead, it) in LOOTING_BAGS }
        if (bags.isEmpty()) return bags
        dead.transaction("looting bag destroyed") { bags.all { removeAt(it.first, it.second, objAt(dead, it), 1) } }
        return bags
    }

    /** The looting bag inv's filled slots, for the grave, when [bags] were destroyed. */
    fun bagSlots(dead: PlayerRef, bags: List<Triple<Int, Int, Int>>): List<Pair<Int, Int>> {
        if (bags.isEmpty()) return emptyList()
        val bag = dead.inv(Invs.LOOTING_BAG)
        return (0 until bag.size).filter { bag.obj(it) >= 0 }.map { Invs.LOOTING_BAG to it }
    }

    /**
     * PvP death: the pouch's runes drop for [owner] (any pouch in the backpack, locked or not), unlocked pouches and the
     * looting bag are deleted, and the bag's contents drop for [owner] except food and potions (deleted). Returns the
     * [lost] units it dealt with.
     */
    fun pvpLoss(dead: PlayerRef, lost: List<Triple<Int, Int, Int>>, owner: String): List<Triple<Int, Int, Int>> {
        dropRunes(dead, owner)
        val pouches = lost.filter { objAt(dead, it) in UNLOCKED_POUCHES }
        val bags = lost.filter { objAt(dead, it) in LOOTING_BAGS }
        if (bags.isNotEmpty()) dropBagContents(dead, owner)
        val gone = pouches + bags
        if (gone.isNotEmpty()) dead.transaction("storage lost on death") { gone.all { removeAt(it.first, it.second, objAt(dead, it), 1) } }
        return gone
    }

    private fun dropRunes(dead: PlayerRef, owner: String) {
        val slots = RunePouch.slots(dead)
        val held = (0 until slots).mapNotNull { RunePouch.slot(dead, it) }
        if (held.isEmpty()) return
        val ok = dead.transaction("rune pouch death drop") { held.all { (rune, n) -> createOnGround(rune, n, dead.coord, owner) } }
        if (ok) for (slot in 0 until RunePouch.MAX_SLOTS) RunePouch.set(dead, slot, -1, 0)
    }

    private fun dropBagContents(dead: PlayerRef, owner: String) {
        val bag = dead.inv(Invs.LOOTING_BAG)
        dead.transaction("looting bag death drop") {
            (0 until bag.size).all { s ->
                val obj = bag.obj(s)
                when {
                    obj < 0 -> true
                    consumable(dead, obj) -> removeAt(Invs.LOOTING_BAG, s, obj, bag.count(s))
                    else -> dropAt(Invs.LOOTING_BAG, s, obj, bag.count(s), dead.coord, owner)
                }
            }
        }
    }

    /** Food or a potion: an "Eat" or "Drink" backpack op. */
    private fun consumable(p: PlayerRef, obj: Int): Boolean = (0 until IOPS).any { p.objIop(obj, it) in CONSUME_OPS }

    private const val IOPS = 5
    private val CONSUME_OPS = setOf("Eat", "Drink")
}
