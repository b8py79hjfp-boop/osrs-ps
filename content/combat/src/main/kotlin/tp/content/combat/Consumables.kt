package tp.content.combat

import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * Food and potions with separate eat / potion / karambwan clocks (docs/mechanics/consumables.md E1–E6, brief §8).
 * Called from the backpack op-1 handler; returns true if the obj is a consumable (handled or refused).
 */
internal object Consumables {
    /** Heal and attack delay per food (E1, E5; Wiki item pages). */
    private class Food(val heal: Int, val attackDelay: Int, val karambwan: Boolean = false)

    @Suppress("MagicNumber") // heal values (OSRS Wiki item pages)
    private val foods: Map<Int, Food> =
        mapOf(
            Objs.SHARK to Food(20, STANDARD_DELAY),
            Objs.MANTARAY to Food(22, STANDARD_DELAY),
            Objs.MONKFISH to Food(16, STANDARD_DELAY),
            Objs.TBWT_COOKED_KARAMBWAN to Food(18, FAST_DELAY, karambwan = true),
        )

    /** Saradomin brew doses: obj → next dose (1-dose → vial). */
    private val brews: Map<Int, Int> =
        mapOf(
            Objs._4DOSEPOTIONOFSARADOMIN to Objs._3DOSEPOTIONOFSARADOMIN,
            Objs._3DOSEPOTIONOFSARADOMIN to Objs._2DOSEPOTIONOFSARADOMIN,
            Objs._2DOSEPOTIONOFSARADOMIN to Objs._1DOSEPOTIONOFSARADOMIN,
            Objs._1DOSEPOTIONOFSARADOMIN to Objs.VIAL_EMPTY,
        )

    fun consume(player: PlayerRef, slot: Int, obj: Int): Boolean {
        val food = foods[obj]
        if (food != null) {
            eat(player, slot, obj, food)
            return true
        }
        val next = brews[obj] ?: return false
        drinkBrew(player, slot, obj, next)
        return true
    }

    private fun eat(player: PlayerRef, slot: Int, obj: Int, food: Food) {
        val now = player.tick
        // E3: a karambwan uses its own clock (it can follow other food in the same tick); other food the food clock.
        val clock = if (food.karambwan) Attr.KARAMBWAN_CLOCK else Attr.FOOD_CLOCK
        if ((player[clock] ?: 0L) > now) return
        if (!player.transaction("eat") { removeAt(Invs.INV, slot, obj, 1) }) return
        player.interrupt() // interactions.md I7: inventory item actions interrupt
        player[Attr.FOOD_CLOCK] = now + EAT_DELAY
        if (food.karambwan) {
            // E3b (brief §8): a karambwan blocks food, potions and karambwans for 3 ticks.
            player[Attr.POTION_CLOCK] = maxOf(player[Attr.POTION_CLOCK] ?: 0L, now + EAT_DELAY)
            player[Attr.KARAMBWAN_CLOCK] = now + EAT_DELAY
        }
        // C9 / E2: food adds to the attack clock only while the weapon is not ready.
        val attack = player.attackClock()
        if (attack > now) player.setAttackClock(attack + food.attackDelay)
        player.hitpoints = minOf(player.maxHitpoints, player.hitpoints + food.heal)
        player.anim(Seqs.HUMAN_EAT)
        player.message("You eat the ${player.objName(obj).lowercase()}.")
    }

    /** E6: heal ⌊base HP × 15/100⌋ + 2 (overheal to base + that), Defence +⌊base/5⌋ + 2, others −(⌊current/10⌋ + 2). */
    @Suppress("MagicNumber") // formula constants (Saradomin brew page, SB:66-115)
    private fun drinkBrew(player: PlayerRef, slot: Int, obj: Int, next: Int) {
        val now = player.tick
        if ((player[Attr.POTION_CLOCK] ?: 0L) > now) return
        if (!player.transaction("drink") { replaceAt(Invs.INV, slot, obj, next) }) return
        player.interrupt()
        player[Attr.POTION_CLOCK] = now + POTION_DELAY
        val baseHp = player.baseLevel(Stat.HITPOINTS)
        val heal = baseHp * 15 / 100 + 2
        player.hitpoints = minOf(baseHp + heal, player.hitpoints + heal)
        val baseDef = player.baseLevel(Stat.DEFENCE)
        val defBoost = baseDef / 5 + 2
        player.setLevel(Stat.DEFENCE, minOf(baseDef + defBoost, player.level(Stat.DEFENCE) + defBoost))
        for (stat in intArrayOf(Stat.ATTACK, Stat.STRENGTH, Stat.RANGED, Stat.MAGIC)) {
            val current = player.level(stat)
            player.setLevel(stat, maxOf(0, current - (current / 10 + 2)))
        }
        player.anim(Seqs.HUMAN_EAT) // potions use the eat animation (seq 829)
        player.message("You drink some of the foul liquid.")
    }

    /** E1: standard food blocks food for 3 ticks and adds 3 to a running attack clock. */
    private const val EAT_DELAY = 3
    private const val STANDARD_DELAY = 3

    /** E1: karambwan, halibut, gnome foods add 2. */
    private const val FAST_DELAY = 2

    /** OPEN-E1: potion sip clock length. */
    private const val POTION_DELAY = 3
}
