package tp.content.raids.toa

import tp.plugin.ConsumeCheck
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.SpecialCostQuery

/**
 * ToA's rules on the combat module's consumables and specials (toa.md TA15): On a Diet / Dehydration refuse food and
 * health-restoring potions in the raid; Overly Draining makes every special cost 100% and liquid adrenaline halves it.
 * The rules themselves are [ToaConsumables]'s; outside a ToA run they change nothing.
 */
public class ToaHooks : ContentPlugin {
    override fun ContentScope.register() {
        events.on<ConsumeCheck> { c ->
            if (c.refusal == null) c.refusal = ToaConsumables.refusal(c.player, c.food, c.restoresHealth)
        }
        events.on<SpecialCostQuery> { q -> if (q.special != UNLEASH) q.tenths = ToaConsumables.specialCostTenths(q.player, q.tenths) }
    }

    private companion object {
        /** Unleash spends all energy by its own rule (SP6). */
        const val UNLEASH = "Unleash"
    }
}
