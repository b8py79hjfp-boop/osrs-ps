package tp.content.raids.cox

import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs

/** The resource rooms' item lists and the tool checks they share. */
internal object CoxItems {
    /**
     * CX35 / OPEN-CX18: the raid-only items the resource rooms use, for `RaidDefinition.raidOnlyItems` (dropped on death,
     * never taken out). The likely set the doc names: potions, planks, food, seeds, herbs, secondaries, kindling, grubs and
     * the keystone. The rack's rake, spade and seed dibber, the hammer and other ordinary items are left out (a player's
     * own items are never lost).
     */
    val RAID_ONLY: Set<Int> =
        CoxFood.ITEMS +
            CoxHerblore.ITEMS +
            setOf(
                Objs.RAIDS_WOOD,
                Objs.RAIDS_VIAL_EMPTY,
                Objs.RAIDS_VIAL_WATER,
                Objs.RAIDS_GRIMY_NOXIFER,
                Objs.RAIDS_NOXIFER,
                Objs.RAIDS_SEED_NOXIFER,
                Objs.RAIDS_GRIMY_GOLPAR,
                Objs.RAIDS_GOLPAR,
                Objs.RAIDS_SEED_GOLPAR,
                Objs.RAIDS_GRIMY_BUCHULEAF,
                Objs.RAIDS_BUCHULEAF,
                Objs.RAIDS_SEED_BUCHULEAF,
                Objs.RAIDS_STINKHORN_MUSHROOM,
                Objs.RAIDS_ENDARKENED_JUICE,
                Objs.RAIDS_CICELY,
                Objs.RAIDS_PLANK,
                Objs.RAIDS_THIEVINGCHEST_GRUBS,
                Objs.RAIDS_TIGHTROPE_KEYSTONE,
            )

    /** Worn weapon slot (RS Mod `Wear` index 3). */
    private const val WEAPON_SLOT = 3

    private fun carried(p: PlayerRef, test: (String) -> Boolean): Boolean {
        fun matches(obj: Int): Boolean = obj > 0 && test(p.objName(obj).lowercase())
        if (matches(p.inv(Invs.WORN).obj(WEAPON_SLOT))) return true
        val inv = p.inv(Invs.INV)
        return (0 until inv.size).any { matches(inv.obj(it)) }
    }

    /** Any axe (KN1), carried or wielded. */
    fun hasAxe(p: PlayerRef): Boolean = carried(p) { it.endsWith(" axe") }

    /** Any pickaxe (SC5), carried or wielded. */
    fun hasPickaxe(p: PlayerRef): Boolean = carried(p) { it.endsWith("pickaxe") }
}
