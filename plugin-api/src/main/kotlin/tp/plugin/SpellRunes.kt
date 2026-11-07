package tp.plugin

import tp.symbols.Invs
import tp.symbols.Objs

/**
 * Pays a spell's rune cost (docs/mechanics/magic-utility.md SP1-SP3): an equipped elemental staff supplies its element
 * without end, combination runes in the backpack count as both of their elements, and whatever is left is taken from the
 * backpack as plain runes. The whole payment is steps of the caller's item transaction, so a spell that fails halfway
 * costs nothing.
 */
public object SpellRunes {
    /** Worn slot of the weapon (RS Mod wear positions). */
    private const val WEAPON_SLOT = 3

    /** A combination rune and the two elemental runes it counts as (OSRS Wiki "Combination rune"). */
    private class Combo(val rune: Int, val first: Int, val second: Int, val name: String)

    private val COMBOS: List<Combo> =
        listOf(
            Combo(Objs.MISTRUNE, Objs.AIRRUNE, Objs.WATERRUNE, "mist"),
            Combo(Objs.DUSTRUNE, Objs.AIRRUNE, Objs.EARTHRUNE, "dust"),
            Combo(Objs.MUDRUNE, Objs.WATERRUNE, Objs.EARTHRUNE, "mud"),
            Combo(Objs.SMOKERUNE, Objs.AIRRUNE, Objs.FIRERUNE, "smoke"),
            Combo(Objs.STEAMRUNE, Objs.WATERRUNE, Objs.FIRERUNE, "steam"),
            Combo(Objs.LAVARUNE, Objs.EARTHRUNE, Objs.FIRERUNE, "lava"),
        )

    /** Elemental runes by the staff-name word (OSRS Wiki "Elemental staff": basic staff, battlestaff and mystic staff). */
    private val ELEMENTS: Map<String, Int> =
        mapOf("air" to Objs.AIRRUNE, "water" to Objs.WATERRUNE, "earth" to Objs.EARTHRUNE, "fire" to Objs.FIRERUNE)

    /** Staff cache names (lower case) -> the runes that staff supplies. Built once; the names are the Wiki's staff lists. */
    private val STAFF_SUPPLY: Map<String, List<Int>> by lazy {
        val map = HashMap<String, List<Int>>()
        for ((word, rune) in ELEMENTS) {
            map["staff of $word"] = listOf(rune)
            map["$word battlestaff"] = listOf(rune)
            map["mystic $word staff"] = listOf(rune)
        }
        for (combo in COMBOS) {
            map["${combo.name} battlestaff"] = listOf(combo.first, combo.second)
            map["mystic ${combo.name} staff"] = listOf(combo.first, combo.second)
        }
        // Wiki "Elemental staff": the Twinflame staff is an unlimited source of fire and water runes.
        map["twinflame staff"] = listOf(Objs.FIRERUNE, Objs.WATERRUNE)
        map
    }

    /** Whether the equipped weapon supplies [rune] without end. */
    public fun staffSupplies(player: PlayerRef, rune: Int): Boolean {
        val weapon = player.inv(Invs.WORN).obj(WEAPON_SLOT)
        if (weapon < 0) return false
        return STAFF_SUPPLY[player.objName(weapon).lowercase()]?.contains(rune) == true
    }

    /**
     * Takes [runes] (rune obj to count) as steps of [tx]; false if the player can't pay. Nothing is removed when this
     * returns false only because the transaction as a whole is then undone by the caller's `transaction`.
     */
    public fun take(tx: ItemTx, player: PlayerRef, runes: List<Pair<Int, Int>>): Boolean {
        val need = LinkedHashMap<Int, Int>()
        for ((rune, count) in runes) {
            if (!staffSupplies(player, rune)) need.merge(rune, count, Int::plus)
        }
        for (combo in COMBOS) {
            val held = held(player, combo.rune)
            var used = 0
            while (used < held && covers(need, combo)) {
                reduce(need, combo.first)
                reduce(need, combo.second)
                used++
            }
            if (used > 0 && !spend(tx, player, combo.rune, used)) return false
        }
        return need.all { (rune, count) -> count <= 0 || spend(tx, player, rune, count) }
    }

    /** Runes the player can spend: the backpack's and the rune pouch's (docs/mechanics/rune-pouch.md RP5). */
    private fun held(player: PlayerRef, rune: Int): Long = player.inv(Invs.INV).total(rune) + RunePouch.count(player, rune)

    /** RP5 (OpenRune `MagicRunes`): the backpack's runes go first, the rest come out of the pouch when the transaction commits. */
    private fun spend(tx: ItemTx, player: PlayerRef, rune: Int, count: Int): Boolean {
        val fromBackpack = minOf(player.inv(Invs.INV).total(rune), count.toLong()).toInt()
        val rest = count - fromBackpack
        if (rest > RunePouch.count(player, rune)) return false
        if (fromBackpack > 0 && !tx.remove(Invs.INV, rune, fromBackpack)) return false
        if (rest > 0) tx.onCommit { RunePouch.remove(player, rune, rest) }
        return true
    }

    /** Whether [player] could pay [runes] now (staff, combination and plain runes), without taking anything. */
    public fun has(player: PlayerRef, runes: List<Pair<Int, Int>>): Boolean {
        val need = LinkedHashMap<Int, Int>()
        for ((rune, count) in runes) {
            if (!staffSupplies(player, rune)) need.merge(rune, count, Int::plus)
        }
        for (combo in COMBOS) {
            val held = held(player, combo.rune)
            var used = 0
            while (used < held && covers(need, combo)) {
                reduce(need, combo.first)
                reduce(need, combo.second)
                used++
            }
        }
        return need.all { (rune, count) -> count <= 0 || held(player, rune) >= count }
    }

    private fun covers(need: Map<Int, Int>, combo: Combo): Boolean = (need[combo.first] ?: 0) > 0 || (need[combo.second] ?: 0) > 0

    private fun reduce(need: MutableMap<Int, Int>, rune: Int) {
        val left = need[rune] ?: return
        if (left > 0) need[rune] = left - 1
    }
}
