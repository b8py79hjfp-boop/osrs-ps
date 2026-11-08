package tp.content.raids.cox

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope

/**
 * The Chambers' resource rooms (docs/mechanics/raids/cox/resources.md): scavenger rooms and their shortcuts, farming,
 * food, Herblore and the Ice Demon saplings. The combat and puzzle rooms stay in [CoxFlow].
 */
public class CoxResources : ContentPlugin {
    override fun ContentScope.register() {
        CoxScavengers.register(this)
        CoxFarming.register(this)
        CoxFood.register(this)
        CoxHerblore.register(this)
        CoxKindling.register(this)
        onWorldTick { CoxRes.tick(this) }
    }
}
