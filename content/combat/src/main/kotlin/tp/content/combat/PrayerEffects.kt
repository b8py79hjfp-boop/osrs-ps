package tp.content.combat

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.HitEvent
import tp.plugin.HitStyle
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Invs

/**
 * Prayer effects on hits and deaths (docs/mechanics/prayers.md P6–P9): Smite, Redemption, Retribution, Protect Item
 * and items kept on death. Sources: OSRS Wiki "Smite", "Redemption", "Retribution", "Items Kept on Death".
 */
public class PrayerEffects : ContentPlugin {
    override fun ContentScope.register() {
        events.on<HitEvent> { e -> onHit(e) }
    }

    private fun onHit(e: HitEvent) {
        val target = e.target as? PlayerRef ?: return
        val source = e.source
        // P6 Smite: drains ⌊damage / 4⌋ prayer; typeless (recoil, vengeance) doesn't.
        val smiting = source is PlayerRef && source.hasPrayer("Smite")
        if (smiting && e.style != HitStyle.TYPELESS && e.damage > 0) {
            target.setLevel(Stat.PRAYER, maxOf(0, target.level(Stat.PRAYER) - e.damage / SMITE_DIVISOR))
        }
        // P7 Redemption: below 10% HP, drains all prayer and heals ⌊base Prayer / 4⌋.
        if (target.hitpoints in 1 until target.maxHitpoints / REDEMPTION_DIVISOR && target.hasPrayer("Redemption")) {
            target.hitpoints += target.baseLevel(Stat.PRAYER) / QUARTER
            target.setLevel(Stat.PRAYER, 0)
            target.deactivateAllPrayers()
        }
    }

    private companion object {
        const val SMITE_DIVISOR = 4
        const val REDEMPTION_DIVISOR = 10
        const val QUARTER = 4
    }
}

internal fun PlayerRef.hasPrayer(name: String): Boolean = activePrayers().any { it.name == name }

/** Death-time prayer and item rules, run by [CombatSystem] when a player dies. */
internal object DeathRules {
    /** P8 Retribution: up to ⌊base Prayer / 4⌋ typeless damage to players in the 3x3 around the dead player. */
    fun retribution(dead: PlayerRef) {
        if (!dead.hasPrayer("Retribution")) return
        val max = dead.baseLevel(Stat.PRAYER) / 4
        for (other in dead.playersWithin(1)) {
            val damage = dead.random(max + 1)
            CombatSystem.queueHit(other, dead, damage, HitStyle.TYPELESS, 0)
        }
    }

    /**
     * Items kept on death (Wiki "Items Kept on Death"): 3 most valuable, 0 when skulled, +1 with Protect Item. Value
     * = cache `cost`. OPEN-D1: stackables are never kept here; untradeable handling isn't modelled.
     */
    fun dropItems(dead: PlayerRef, killer: EntityRef?) {
        val keep = (if (dead[SharedAttrs.SKULL_ICON] != null) 0 else KEPT) + if (dead.hasPrayer("Protect Item")) 1 else 0
        val units = ArrayList<Triple<Int, Int, Int>>() // inv, slot, value
        for (invId in intArrayOf(Invs.INV, Invs.WORN)) {
            val inv = dead.inv(invId)
            for (slot in 0 until inv.size) {
                val obj = inv.obj(slot)
                if (obj < 0) continue
                val value = if (inv.count(slot) > 1) -1 else dead.objValue(obj)
                units += Triple(invId, slot, value)
            }
        }
        val kept =
            units
                .filter { it.third >= 0 }
                .sortedByDescending { it.third }
                .take(keep)
                .toSet()
        val owner = (killer as? PlayerRef)?.username ?: dead.username
        val at = dead.coord
        dead.transaction("death drop") {
            units.filter { it !in kept }.all { (invId, slot, _) ->
                val inv = dead.inv(invId)
                dropAt(invId, slot, inv.obj(slot), inv.count(slot), at, owner)
            }
        }
    }

    private const val KEPT = 3
}
