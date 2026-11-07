package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.OfferRemains
import tp.plugin.Stats
import tp.symbols.Invs

/**
 * Sinister / Demonic Offering's Prayer side (prayer-training.md PT13): the first [OfferRemains.max] bones or ashes in
 * the backpack are removed for [OfferRemains.multiplier] times their burying / scattering xp; remains above the
 * player's Prayer level are skipped (superior dragon bones: "You require a Prayer level of 70 to do that.").
 */
public class RemainsOffering : ContentPlugin {
    override fun ContentScope.register() {
        val remains = RemainsTable.load().associateBy { it.obj }
        events.on<OfferRemains> { e ->
            val p = e.player
            val kind = if (e.ashes) RemainsKind.ASH else RemainsKind.BONE
            val backpack = p.inv(Invs.INV)
            val picks =
                (0 until backpack.size)
                    .mapNotNull { slot -> remains[backpack.obj(slot)]?.takeIf { it.kind == kind }?.let { slot to it } }
                    .filter { (_, r) -> p.level(Stats.PRAYER) >= r.level }
                    .take(e.max)
            if (picks.isEmpty()) {
                e.refusal = if (e.ashes) "You don't have any demonic ashes to offer." else "You don't have any bones to offer."
                return@on
            }
            if (!p.transaction("offering") { picks.all { (slot, r) -> removeAt(Invs.INV, slot, r.obj, 1) } }) return@on
            for ((_, r) in picks) p.addXp(Stats.PRAYER, r.xp * e.multiplier)
            e.offered = picks.size
        }
    }
}
