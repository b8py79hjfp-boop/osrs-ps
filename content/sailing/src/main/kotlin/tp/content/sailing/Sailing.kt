package tp.content.sailing

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope

/**
 * The Sailing skill (docs/plans/sailing.md, docs/mechanics/sailing-*.md). At world start the cache tables are read into
 * [SailingData] and cross-checked, so a cache without the Sailing tables or with broken references stops the boot instead
 * of failing in a player's boat; then the boat factory is built. S1 content: boat ownership and spawning ([Boats]), docking
 * and boarding ([Docking]) and the Pandemonium quest ([Pandemonium]). Movement is [BoatMotion].
 */
public class Sailing : ContentPlugin {
    private val state = SailingState()
    private val docking = Docking(state)
    private val pandemonium = Pandemonium(state, docking)

    override fun ContentScope.register() {
        onWorldStart {
            val data = SailingData(this)
            data.check()
            state.data = data
            state.boats = Boats(data, DockSites(data.docks))
        }
        with(docking) { register() }
        with(pandemonium) { register() }
    }
}
