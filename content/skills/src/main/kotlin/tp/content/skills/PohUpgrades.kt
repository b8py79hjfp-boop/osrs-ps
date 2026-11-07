package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs

/**
 * In-place upgrades of built furniture (docs/mechanics/construction.md CO29): the op 4 "Upgrade" of pools, steel framed
 * workbenches and benches with a vice, jewellery boxes and portal nexuses. Table: `construction-upgrades.tsv` (the Wiki's
 * "Upgrade" recipes).
 */
public class PohUpgrades : ContentPlugin {
    override fun ContentScope.register() {
        for (upgrade in ConstructionData.upgrades()) {
            val from = PohFurniture.byName(upgrade.from) ?: continue
            val to = PohFurniture.byName(upgrade.to) ?: continue
            for (loc in from.locs) onOpLoc(loc, OP_UPGRADE) { upgrade(it, from, to, upgrade) }
        }
    }

    private suspend fun PlayerScript.upgrade(loc: LocRef, from: Piece, to: Piece, u: Upgrade) {
        val house = HouseMenus.ownBuildHouse(player) ?: return
        val group = house.groupAt(loc.coord) ?: return
        if (group.built !== from) return
        shortfall(u, to)?.let { return player.message(it) }
        player.anim(HouseMenus.animFor(loc.shape))
        delay(to.furniture.ticks)
        if (house.instance.destroyed || player[House.CURRENT] !== house || group.built !== from) return
        shortfall(u, to)?.let { return player.message(it) }
        if (!PohMaterials.take(player, "construction upgrade", u.inputs)) {
            return player.message("You don't have the materials to build that.")
        }
        HouseBuilder.buildFurniture(player, house, group, to)
        player.addXp(Stats.CONSTRUCTION, u.xp * CarpenterOutfit.multiplier(player))
    }

    /** The first reason the upgrade can't be done now (level, tools of the new piece, materials), or null. */
    private fun PlayerScript.shortfall(u: Upgrade, to: Piece): String? {
        val inv = player.inv(Invs.INV)
        return when {
            player.level(Stats.CONSTRUCTION) < u.level -> "You need a Construction level of ${u.level} to build that."
            to.furniture.tools.any { inv.total(it) == 0L } -> "You need a hammer and a saw to build that."
            !PohMaterials.covers(player, u.inputs) -> "You don't have the materials to build that."
            else -> null
        }
    }

    private companion object {
        /** Cache 2757: op 4 "Upgrade" on the pools, the benches, the jewellery boxes and the portal nexus. */
        const val OP_UPGRADE = 4
    }
}
