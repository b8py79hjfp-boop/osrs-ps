package tp.content.combat

import tp.plugin.AppearanceChanged
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Varps

/** Moves the item in worn [slot] to the backpack (one transaction); false if there's no room or nothing there. */
internal fun unequip(player: PlayerRef, slot: Int): Boolean {
    val obj = player.worn(slot)
    if (obj < 0) return false
    val ok = player.transaction("unequip") { move(Invs.WORN, slot, obj, player.inv(Invs.WORN).count(slot), Invs.INV) }
    if (ok) player.afterChange(slot)
    return ok
}

/** Weapon-dependent state and the appearance after worn slot [pos] changed. */
internal fun PlayerRef.afterChange(pos: Int) {
    if (pos == Wear.WEAPON || pos == Wear.SHIELD) {
        setVarbit(Varbits.COMBAT_WEAPON_CATEGORY, weaponCategory().id)
        // Switching weapons turns the special attack bar off (RS Mod `resetSpecialAttack` on weapon change).
        setVarp(Varps.SA_ATTACK, 0)
        val slots = weaponCategory().slots
        if (slots.getOrNull(varp(Varps.COM_MODE)) == null) setVarp(Varps.COM_MODE, slots.indexOfFirst { it != null })
        Autocast.onWeaponChange(this)
    }
    Appearance.refresh(this)
}

/** Appearance = worn objs + combat level + icons; sent whenever any of them change. */
internal object Appearance {
    fun refresh(player: PlayerRef) {
        val worn = IntArray(APPEARANCE_SLOTS) { player.worn(it) }
        player.appearance(worn, player.combatLevel(), player[SharedAttrs.SKULL_ICON] ?: -1, player.headIcon())
    }

    private const val APPEARANCE_SLOTS = 12
}

/**
 * Wield/wear from the backpack (iop1 = button op 2), remove from the worn tab, weapon category for the combat tab,
 * appearance. Every inventory item action interrupts (interactions.md I7); worn-tab actions don't.
 */
public class Equipment : ContentPlugin {
    override fun ContentScope.register() {
        events.on<AppearanceChanged> { Appearance.refresh(it.player) }
        onWorldStart { Consumables.load(this) }
        onLogin {
            Appearance.refresh(this)
            setVarbit(Varbits.COMBAT_WEAPON_CATEGORY, weaponCategory().id)
        }
        // iop1..iop3 = button ops 2..4; what an op does depends on the obj's op text (cache iops): wieldables have
        // "Wield"/"Wear" as iop2, food/potions "Eat"/"Drink" as iop1, potions "Empty" as iop4.
        for (op in ITEM_OPS) {
            onButton(Components.Inventory.ITEMS, op) { click -> itemOp(click) }
        }
        for ((slot, component) in WORN_COMPONENTS) {
            onButton(component, OP_REMOVE) { remove(slot) }
        }
    }

    private fun PlayerRef.itemOp(click: ButtonClick) {
        val inv = inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != click.obj) return
        when (objIop(click.obj, iopIndex(click.op))?.lowercase()) {
            "eat", "drink" -> Consumables.consume(this, click.slot, click.obj)
            "wield", "wear", "equip" -> if (objWearPos(click.obj) >= 0) wield(click.slot, click.obj)
            "empty" -> if (Consumables.isPotion(click.obj)) empty(click.slot, click.obj)
        }
    }

    /** Potion "Empty": the potion becomes an empty vial. */
    private fun PlayerRef.empty(slot: Int, obj: Int) {
        interrupt()
        if (transaction("empty") { replaceAt(Invs.INV, slot, obj, Objs.VIAL_EMPTY) }) message("You empty the vial.")
    }

    private fun PlayerRef.wield(slot: Int, obj: Int) {
        interrupt()
        val pos = objWearPos(obj)
        val twoHanded = pos == Wear.WEAPON && objWearPos2(obj) == Wear.SHIELD
        if (!meetsRequirements(obj)) return
        DuelChecks.wieldDenial(this, pos, twoHanded)?.let {
            message(it)
            return
        }
        val displaced = ArrayList<Int>(2)
        displaced += pos
        if (twoHanded) displaced += Wear.SHIELD
        // A shield displaces a two-handed weapon.
        val weapon = worn(Wear.WEAPON)
        if (pos == Wear.SHIELD && weapon >= 0 && objWearPos2(weapon) == Wear.SHIELD) displaced += Wear.WEAPON
        val count = inv(Invs.INV).count(slot)
        val ok =
            transaction("wield") {
                removeAt(Invs.INV, slot, obj, count) &&
                    displaced.all { d -> worn(d) < 0 || move(Invs.WORN, d, worn(d), inv(Invs.WORN).count(d), Invs.INV) } &&
                    setAt(Invs.WORN, pos, obj, count)
            }
        if (!ok) {
            message("You don't have enough free inventory space to do that.")
            return
        }
        afterChange(pos)
    }

    /**
     * EQ1 (RS Mod `HeldEquipOp`): obj params `statreq1_skill`/`statreq1_level` (434/436) and `statreq2_*` (435/437)
     * against base levels (boosts don't count); "You are not a high enough level to use this item." then which levels.
     */
    private fun PlayerRef.meetsRequirements(obj: Int): Boolean {
        val missing =
            REQUIREMENTS.mapNotNull { (skillParam, levelParam) ->
                val skill = objParam(obj, skillParam, -1).takeIf { it >= 0 } ?: return@mapNotNull null
                val level = objParam(obj, levelParam, 0)
                (skill to level).takeIf { skill in STAT_NAMES.indices && baseLevel(skill) < level }
            }
        if (missing.isEmpty()) return true
        message("You are not a high enough level to use this item.")
        message(
            "You need to have " + missing.joinToString(" and ") { (skill, level) -> "${article(STAT_NAMES[skill])} level of $level" } + ".",
        )
        return false
    }

    private fun article(name: String): String = (if (name.first().lowercaseChar() in "aeiou") "an " else "a ") + name

    private fun PlayerRef.remove(slot: Int) {
        if (worn(slot) < 0) return
        if (!unequip(this, slot)) message("You don't have enough free inventory space to do that.")
    }

    private companion object {
        /** Obj param ids: (skill, level) of the two stat requirements (cache; RS Mod `statreq1_*`, `statreq2_*`). */
        @Suppress("MagicNumber") // cache param ids
        val REQUIREMENTS = listOf(434 to 436, 435 to 437)

        /** Stat names in client stat order. */
        val STAT_NAMES =
            listOf(
                "Attack",
                "Defence",
                "Strength",
                "Hitpoints",
                "Ranged",
                "Prayer",
                "Magic",
                "Cooking",
                "Woodcutting",
                "Fletching",
                "Fishing",
                "Firemaking",
                "Crafting",
                "Smithing",
                "Mining",
                "Herblore",
                "Agility",
                "Thieving",
                "Slayer",
                "Farming",
                "Runecraft",
                "Hunter",
                "Construction",
                "Sailing",
            )

        /**
         * Button ops of iop1..iop4: iop1..3 = ops 2..4, iop4 = op 6, iop5 = op 7 (RS Mod `InvOpenScript` events Op2, Op3,
         * Op4, Op6, Op7). Op 7 (Drop/Destroy) is handled by the backpack plugin.
         */
        val ITEM_OPS = intArrayOf(2, 3, 4, 6)

        /** iop index (0-based) of a backpack button op. */
        fun iopIndex(op: Int): Int = if (op >= IOP4_OP) op - IOP4_SHIFT else op - 2

        private const val IOP4_OP = 6
        private const val IOP4_SHIFT = 3

        /** Worn tab slot click (Remove). */
        const val OP_REMOVE = 1

        val WORN_COMPONENTS: List<Pair<Int, Int>> =
            listOf(
                Wear.HEAD to Components.Wornitems.SLOT0,
                Wear.CAPE to Components.Wornitems.SLOT1,
                Wear.NECK to Components.Wornitems.SLOT2,
                Wear.WEAPON to Components.Wornitems.SLOT3,
                Wear.BODY to Components.Wornitems.SLOT4,
                Wear.SHIELD to Components.Wornitems.SLOT5,
                Wear.LEGS to Components.Wornitems.SLOT7,
                Wear.HANDS to Components.Wornitems.SLOT9,
                Wear.FEET to Components.Wornitems.SLOT10,
                Wear.RING to Components.Wornitems.SLOT12,
                Wear.AMMO to Components.Wornitems.SLOT13,
            )
    }
}
