@file:Suppress("MagicNumber") // data table: destination tiles of docs/mechanics/teleports.md (Jewellery), listed per box in the Wiki

package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerScript
import tp.util.CoordGrid

/** One teleport of a jewellery box: the [name] on the menu and its [tile]. */
internal class BoxTeleport(val name: String, x: Int, z: Int) {
    val tile: CoordGrid = CoordGrid(x, z, 0)
}

/** The teleports of one kind of jewellery the box holds (Wiki "Basic jewellery box", Locations). */
internal class BoxItem(val item: String, val teleports: List<BoxTeleport>)

/**
 * The jewellery boxes of the achievement gallery (docs/mechanics/construction.md CO27): op 2 "Teleport Menu" asks for the kind of
 * jewellery and then the destination, and teleports there without charges. The basic box holds the ring of dueling and the
 * games necklace; the fancy box adds the combat bracelet and the skills necklace; the ornate box the ring of wealth and the
 * amulet of glory.
 */
public class PohJewellery : ContentPlugin {
    override fun ContentScope.register() {
        for ((tier, name) in BOXES.withIndex()) {
            val piece = PohFurniture.byName(name) ?: continue
            for (loc in piece.locs) onOpLoc(loc, OP_MENU) { menu(tier) }
        }
    }

    private suspend fun PlayerScript.menu(tier: Int) {
        val kinds = ITEMS.take(KINDS_PER_TIER.take(tier + 1).sum())
        val kind = with(HouseMenus) { pick("Teleport menu", kinds) { it.item } } ?: return
        val to = with(HouseMenus) { pick(kind.item, kind.teleports) { it.name } } ?: return
        with(PohTravel) { travel(to.tile) }
    }

    private companion object {
        /** Cache 2757: op 2 "Teleport Menu" of the box locs (op 3 is the previous destination: OPEN-CO27). */
        const val OP_MENU = 2

        /** Piece names of the boxes by tier. */
        val BOXES = listOf("Basic jewellery box", "Fancy jewellery box", "Ornate jewellery box")

        /** Kinds of jewellery the basic, fancy and ornate boxes add (Wiki "Fancy jewellery box" / "Ornate jewellery box", Locations). */
        val KINDS_PER_TIER = listOf(2, 2, 2)

        /** Destination tiles: docs/mechanics/teleports.md, table "Jewellery"; Chasm of Tears is the games necklace's Tears of Guthix. */
        val ITEMS =
            listOf(
                BoxItem(
                    "Ring of dueling",
                    listOf(
                        BoxTeleport("Emir's Arena", 3315, 3235),
                        BoxTeleport("Castle Wars Arena", 2440, 3090),
                        BoxTeleport("Ferox Enclave", 3151, 3635),
                        BoxTeleport("Fortis Colosseum", 1793, 3107),
                    ),
                ),
                BoxItem(
                    "Games necklace",
                    listOf(
                        BoxTeleport("Burthorpe", 2899, 3553),
                        BoxTeleport("Barbarian Outpost", 2520, 3571),
                        BoxTeleport("Corporeal Beast", 2967, 4254),
                        BoxTeleport("Chasm of Tears", 3245, 9500),
                        BoxTeleport("Wintertodt Camp", 1631, 3940),
                    ),
                ),
                BoxItem(
                    "Combat bracelet",
                    listOf(
                        BoxTeleport("Warriors' Guild", 2882, 3547),
                        BoxTeleport("Champions' Guild", 3192, 3368),
                        BoxTeleport("Edgeville Monastery", 3052, 3490),
                        BoxTeleport("Ranging Guild", 2653, 3439),
                    ),
                ),
                BoxItem(
                    "Skills necklace",
                    listOf(
                        BoxTeleport("Fishing Guild", 2611, 3390),
                        BoxTeleport("Mining Guild", 3049, 9763),
                        BoxTeleport("Crafting Guild", 2933, 3295),
                        BoxTeleport("Cooks' Guild", 3144, 3438),
                        BoxTeleport("Woodcutting Guild", 1662, 3505),
                        BoxTeleport("Farming Guild", 1248, 3719),
                    ),
                ),
                BoxItem(
                    "Ring of wealth",
                    listOf(
                        BoxTeleport("Miscellania", 2534, 3862),
                        BoxTeleport("Grand Exchange", 3163, 3478),
                        BoxTeleport("Falador Park", 2995, 3375),
                        BoxTeleport("Dondakan's Rock", 2824, 10168),
                    ),
                ),
                BoxItem(
                    "Amulet of glory",
                    listOf(
                        BoxTeleport("Edgeville", 3087, 3496),
                        BoxTeleport("Karamja", 2918, 3176),
                        BoxTeleport("Draynor Village", 3105, 3251),
                        BoxTeleport("Al Kharid", 3293, 3163),
                    ),
                ),
            )
    }
}
