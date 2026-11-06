package tp.content.combat

import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * Food and potions with separate eat / potion / karambwan clocks (docs/mechanics/consumables.md, brief §8). Items are
 * found by their cache name at world start; potion dose chains "X(4)" → "X(3)" → … → vial come from the cache names.
 * Effect values: OSRS Wiki item pages (cited per row in consumables.md).
 */
internal object Consumables {
    private class Food(
        val heal: (PlayerRef) -> Int,
        val attackDelay: Int = STANDARD_DELAY,
        val karambwan: Boolean = false,
        val overheal: Boolean = false,
    )

    private class Potion(val effect: (PlayerRef) -> Unit)

    @Suppress("MagicNumber") // heal values (OSRS Wiki item pages)
    private val foodsByName: Map<String, Food> =
        mapOf(
            "Lobster" to Food({ 12 }),
            "Swordfish" to Food({ 14 }),
            "Monkfish" to Food({ 16 }),
            "Shark" to Food({ 20 }),
            "Manta ray" to Food({ 22 }),
            "Dark crab" to Food({ 22 }),
            "Tuna potato" to Food({ 22 }),
            // Anglerfish: ⌊x/10⌋ + 2⌊x/25⌋ + 5⌊x/93⌋ + 2 by Hitpoints level, and it can overheal by that amount.
            "Anglerfish" to Food({ p -> anglerfish(p.baseLevel(Stat.HITPOINTS)) }, overheal = true),
            "Cooked karambwan" to Food({ 18 }, FAST_DELAY, karambwan = true),
        )

    @Suppress("MagicNumber") // potion formulas (OSRS Wiki potion pages)
    private val potionsByName: Map<String, Potion> =
        mapOf(
            "Saradomin brew" to Potion(::brew),
            // Antipoison family (poison.md PO6): immunity per dose in 30-tick intervals (Poison page table, minutes × 60 / 18).
            "Antipoison" to Potion { p -> Poison.cure(p, immunity = 5, antiVenom = false) },
            "Superantipoison" to Potion { p -> Poison.cure(p, immunity = 20, antiVenom = false) },
            "Antidote+" to Potion { p -> Poison.cure(p, immunity = 30, antiVenom = false) },
            "Antidote++" to Potion { p -> Poison.cure(p, immunity = 40, antiVenom = false) },
            "Anti-venom" to Potion { p -> Poison.cure(p, immunity = 41, antiVenom = true) },
            "Anti-venom+" to Potion { p -> Poison.cure(p, immunity = 50, antiVenom = true) },
            "Extended anti-venom+" to Potion { p -> Poison.cure(p, immunity = 59, antiVenom = true) },
            // 8 + ⌊base/4⌋ to every lowered stat except Hitpoints, Prayer included.
            "Super restore" to Potion { p -> restoreAll(p) { base -> 8 + base / 4 } },
            // 7 + ⌊Prayer level/4⌋ prayer points.
            "Prayer potion" to Potion { p -> restore(p, Stat.PRAYER, 7 + p.baseLevel(Stat.PRAYER) / 4) },
            // ⌊level × 15/100⌋ + 5 to Attack, Strength and Defence.
            "Super combat potion" to Potion { p -> for (s in intArrayOf(Stat.ATTACK, Stat.STRENGTH, Stat.DEFENCE)) boost(p, s, 15, 5) },
            "Super attack" to Potion { p -> boost(p, Stat.ATTACK, 15, 5) },
            "Super strength" to Potion { p -> boost(p, Stat.STRENGTH, 15, 5) },
            "Super defence" to Potion { p -> boost(p, Stat.DEFENCE, 15, 5) },
            // ⌊Ranged × 1/10⌋ + 4.
            "Ranging potion" to Potion { p -> boost(p, Stat.RANGED, 10, 4) },
            // Ranged ⌊10%⌋ + 4, Defence ⌊15%⌋ + 5.
            "Bastion potion" to
                Potion { p ->
                    boost(p, Stat.RANGED, 10, 4)
                    boost(p, Stat.DEFENCE, 15, 5)
                },
            // "temporarily raises the player's Magic level by 4".
            "Magic potion" to Potion { p -> boost(p, Stat.MAGIC, 0, 4) },
            // 20% run energy per dose (the drain reduction isn't modelled: run drain isn't either).
            "Stamina potion" to Potion { p -> p.runEnergy += 2000 },
        )

    private var foods: Map<Int, Food> = emptyMap()
    private var potions: Map<Int, Pair<Potion, Int>> = emptyMap() // obj → effect, next dose (or vial)

    /** Builds the id tables from cache names (once per world start; immutable afterwards). */
    fun load(world: WorldRef) {
        foods = foodsByName.flatMap { (name, food) -> world.objIdsNamed(name).map { it to food } }.toMap()
        val table = HashMap<Int, Pair<Potion, Int>>()
        for ((name, potion) in potionsByName) {
            for (dose in 1..MAX_DOSES) {
                val next = if (dose == 1) listOf(Objs.VIAL_EMPTY) else world.objIdsNamed("$name(${dose - 1})")
                for (id in world.objIdsNamed("$name($dose)")) table[id] = potion to (next.firstOrNull() ?: Objs.VIAL_EMPTY)
            }
        }
        potions = table
    }

    /** Whether [obj] is a potion (for "Empty"). */
    fun isPotion(obj: Int): Boolean = obj in potions

    fun consume(player: PlayerRef, slot: Int, obj: Int): Boolean {
        val food = foods[obj]
        if (food != null) {
            eat(player, slot, obj, food)
            return true
        }
        val (potion, next) = potions[obj] ?: return false
        drink(player, slot, obj, next, potion)
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
        val heal = food.heal(player)
        val cap = if (food.overheal) player.maxHitpoints + heal else player.maxHitpoints
        player.hitpoints = maxOf(player.hitpoints, minOf(cap, player.hitpoints + heal))
        player.anim(Seqs.HUMAN_EAT)
        player.message("You eat the ${player.objName(obj).lowercase()}.")
    }

    private fun drink(player: PlayerRef, slot: Int, obj: Int, next: Int, potion: Potion) {
        val now = player.tick
        if ((player[Attr.POTION_CLOCK] ?: 0L) > now) return
        if (!player.transaction("drink") { replaceAt(Invs.INV, slot, obj, next) }) return
        player.interrupt()
        player[Attr.POTION_CLOCK] = now + POTION_DELAY
        potion.effect(player)
        player.anim(Seqs.HUMAN_EAT) // potions use the eat animation (seq 829)
        player.message("You drink some of your ${player.objName(obj).substringBefore('(').lowercase()}.")
    }

    /** E6 Saradomin brew: heal ⌊base HP × 15/100⌋ + 2 (overheal), Defence +⌊base/5⌋ + 2, others −(⌊current/10⌋ + 2). */
    @Suppress("MagicNumber")
    private fun brew(player: PlayerRef) {
        val baseHp = player.baseLevel(Stat.HITPOINTS)
        val heal = baseHp * 15 / 100 + 2
        player.hitpoints = maxOf(player.hitpoints, minOf(baseHp + heal, player.hitpoints + heal))
        val baseDef = player.baseLevel(Stat.DEFENCE)
        val defBoost = baseDef / 5 + 2
        player.setLevel(Stat.DEFENCE, maxOf(player.level(Stat.DEFENCE), minOf(baseDef + defBoost, player.level(Stat.DEFENCE) + defBoost)))
        for (stat in intArrayOf(Stat.ATTACK, Stat.STRENGTH, Stat.RANGED, Stat.MAGIC)) {
            val current = player.level(stat)
            player.setLevel(stat, maxOf(0, current - (current / 10 + 2)))
        }
    }

    /** A potion boost: base + ⌊base × percent/100⌋ + flat, never lowering a higher current level (OPEN-E5). */
    private fun boost(player: PlayerRef, stat: Int, percent: Int, flat: Int) {
        val base = player.baseLevel(stat)
        val target = base + base * percent / PERCENT + flat
        if (player.level(stat) < target) player.setLevel(stat, target)
    }

    /** Restores [stat] by [amount], up to its base level. */
    private fun restore(player: PlayerRef, stat: Int, amount: Int) {
        val base = player.baseLevel(stat)
        val current = player.level(stat)
        if (current < base) player.setLevel(stat, minOf(base, current + amount))
    }

    private fun restoreAll(player: PlayerRef, amount: (Int) -> Int) {
        for (stat in 0 until STAT_COUNT) {
            if (stat != Stat.HITPOINTS) restore(player, stat, amount(player.baseLevel(stat)))
        }
    }

    @Suppress("MagicNumber")
    private fun anglerfish(x: Int): Int = x / 10 + 2 * (x / 25) + 5 * (x / 93) + 2

    /** E1: standard food blocks food for 3 ticks and adds 3 to a running attack clock. */
    private const val EAT_DELAY = 3
    private const val STANDARD_DELAY = 3

    /** E1: karambwan, halibut, gnome foods add 2. */
    private const val FAST_DELAY = 2

    /** OPEN-E1: potion sip clock length. */
    private const val POTION_DELAY = 3
    private const val MAX_DOSES = 4
    private const val PERCENT = 100
    private const val STAT_COUNT = 24
}
