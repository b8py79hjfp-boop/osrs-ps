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
     * = Grand Exchange guide price, else the cache cost. OPEN-D1: stackables are never kept here. A PvP death (killed
     * by a player in the Wilderness or on a PvP world) drops the rest for the killer, except untradeables, which go to
     * the grave; any other death puts the rest in the grave (death.md DE1-DE4, DE10).
     */
    fun dropItems(dead: PlayerRef, killer: EntityRef?) {
        val keep = (if (dead[SharedAttrs.SKULL_ICON] != null) 0 else KEPT) + if (dead.hasPrayer("Protect Item")) 1 else 0
        val inWilderness = dead[SharedAttrs.IN_WILDERNESS] == true
        val pvp = killer is PlayerRef && (inWilderness || dead.isPvpWorld)
        val units = ArrayList<Triple<Int, Int, Int>>() // inv, slot, value
        for (invId in intArrayOf(Invs.INV, Invs.WORN)) {
            val inv = dead.inv(invId)
            for (slot in 0 until inv.size) {
                val obj = inv.obj(slot)
                if (obj < 0) continue
                // DE11 / DE12: never kept: the looting bag on any Wilderness death, an unlocked rune pouch on a PvP death.
                val value = if (inv.count(slot) > 1 || StorageDeath.alwaysLost(obj, pvp, inWilderness)) -1 else dead.objValue(obj)
                units += Triple(invId, slot, value)
            }
        }
        val kept =
            units
                .filter { it.third >= 0 }
                .sortedByDescending { it.third }
                .take(keep)
                .toSet()
        var lost = units.filter { it !in kept }
        // DE3: a death in instance space leaves the grave at the respawn point.
        val at = if (dead.coord.x shr SQUARE_SHIFT >= FIRST_INSTANCE_SQUARE) CombatSystem.RESPAWN else dead.coord
        if (!pvp) {
            val bag = if (inWilderness) StorageDeath.destroyLootingBag(dead, lost) else emptyList()
            return Graves.bury(
                dead,
                (lost - bag.toSet()).map { it.first to it.second } + StorageDeath.bagSlots(dead, bag),
                at,
                inWilderness,
            )
        }
        val owner = (killer as PlayerRef).username
        lost = lost - StorageDeath.pvpLoss(dead, lost, owner).toSet()
        // GE64 Wiki "Toxic blowpipe": unprotected in PvP, it drops empty with its scales and darts beside it.
        val blowpipes = lost.filter { (invId, slot, _) -> Blowpipe.isLoaded(dead.inv(invId).obj(slot)) }
        for ((invId, slot, _) in blowpipes) Blowpipe.dropOnDeath(dead, invId, slot, dead.coord, owner)
        lost = lost - blowpipes.toSet()
        val untradeable = lost.filter { (invId, slot, _) -> !dead.objTradeable(dead.inv(invId).obj(slot)) }
        dead.transaction("death drop") {
            (lost - untradeable.toSet()).all { (invId, slot, _) ->
                val inv = dead.inv(invId)
                dropAt(invId, slot, inv.obj(slot), inv.count(slot), dead.coord, owner)
            }
        }
        if (untradeable.isNotEmpty()) Graves.bury(dead, untradeable.map { it.first to it.second }, at, inWilderness)
    }

    /** Instance space starts at map square x 100 (instances.md IN1). */
    private const val FIRST_INSTANCE_SQUARE = 100
    private const val SQUARE_SHIFT = 6

    private const val KEPT = 3
}
