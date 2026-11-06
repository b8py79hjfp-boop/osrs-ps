package tp.content.combat

import tp.plugin.DuelRules
import tp.plugin.PlayerRef

/** Whether [this] player is in a running duel with rule [bit] (duel-arena.md DA2). */
internal fun PlayerRef.duelRule(bit: Int): Boolean = DuelRules.has(this, bit)

/**
 * The combat side of the Duel Arena rules (duel-arena.md DA2): food, drinks, prayer, specials, weapon switching,
 * disabled worn slots, fun weapons and hold spells. OPEN-DA4: the refusal messages aren't published; plain wording.
 */
internal object DuelChecks {
    const val NO_FOOD = "Food has been disabled for this duel."
    const val NO_DRINKS = "Drinks have been disabled for this duel."
    const val NO_PRAYER = "Prayer has been disabled for this duel."
    const val NO_SPECIALS = "Special attacks have been disabled for this duel."
    const val HOLD_SPELLS = "Holding spells can't be used in a no-movement duel."
    private const val NO_SWITCH = "Weapon switching has been disabled for this duel."
    private const val SLOT_DISABLED = "You can't wear that in this duel."
    private const val FUN_ONLY = "You can only attack with 'fun' weapons in this duel."

    /** Why [player] may not wield into [pos] now, or null. */
    fun wieldDenial(player: PlayerRef, pos: Int, twoHanded: Boolean): String? =
        when {
            (pos == Wear.WEAPON || pos == Wear.SHIELD) && player.duelRule(DuelRules.NO_WEAPON_SWITCH) -> NO_SWITCH
            DuelRules.slotDisabled(player, pos) -> SLOT_DISABLED
            twoHanded && DuelRules.slotDisabled(player, Wear.SHIELD) -> SLOT_DISABLED
            else -> null
        }

    /**
     * "Fun Weapons: The only allowed weapons are those with negative attack stats, such as the Rubber chicken ...
     * Bare fists/feet are not allowed."
     */
    fun weaponAllowed(player: PlayerRef): Boolean {
        if (!player.duelRule(DuelRules.FUN_WEAPONS)) return true
        val funWeapon = player.worn(Wear.WEAPON) >= 0 && ATTACK_PARAMS.all { player.bonus(it) < 0 }
        if (!funWeapon) player.message(FUN_ONLY)
        return funWeapon
    }

    private val ATTACK_PARAMS = intArrayOf(Param.ATTACK_STAB, Param.ATTACK_SLASH, Param.ATTACK_CRUSH)
}
