package tp.content.sailing

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope

/**
 * The Sailing skill (docs/plans/sailing.md, docs/mechanics/sailing-*.md). Milestone S0 is the data layer only: at world start
 * the cache tables are read into [SailingData] and cross-checked, so a cache without the Sailing tables or with broken
 * references stops the boot instead of failing in a player's boat. Gameplay lands in S1 and later.
 */
public class Sailing : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart { SailingData(this).check() }
    }
}
