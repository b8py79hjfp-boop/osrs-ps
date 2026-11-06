package tp.content.magic

import tp.plugin.AppearanceChanged
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EventBus
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Invs
import tp.util.CoordGrid

/** One teleport of a charged item: its name in menus and its tile. */
internal class Destination(val name: String, val x: Int, val z: Int) {
    val coord: CoordGrid get() = CoordGrid(x, z, 0)
}

/**
 * A charged teleport item family (OSRS Wiki item pages). [charged] names the cache obj with n charges; [uncharged] is
 * the cache name it turns into after its last charge, null if it crumbles to dust. [destinations] are in the order of
 * the item's worn-menu options (cache obj params 451+). [wildernessLimit] is the highest Wilderness level it works in.
 */
internal class Jewel(
    val noun: String,
    val itemName: String,
    val maxCharges: Int,
    val charged: (Int) -> String,
    val uncharged: String?,
    val wildernessLimit: Int,
    val destinations: List<Destination>,
    val wording: Wording,
)

/** How an item reports its charges (teleports.md J3). */
internal enum class Wording {
    /** "Your games necklace has three uses left." (Wiki Games necklace, Ring of dueling). */
    USES,

    /** "Your amulet of glory has 3 charges left." (OPEN-TP11: no Wiki text for the dragonstone jewellery). */
    CHARGES,
}

/** A charged obj id: its [jewel], remaining [charges] (0 = uncharged) and the obj it becomes after one use (-1 = gone). */
private class Held(val jewel: Jewel, val charges: Int, val next: Int)

/** Where a jewel sits: inventory [inv] slot [slot]. */
private class Place(val inv: Int, val slot: Int)

/**
 * Amulet of glory, Games necklace, Ring of dueling, Combat bracelet, Skills necklace and Ring of wealth
 * (docs/mechanics/teleports.md J1-J5): "Rub" in the backpack opens a chatbox menu, the worn tab lists the destinations
 * as ops 2.., each teleport uses one charge.
 */
public class Jewellery : ContentPlugin {
    override fun ContentScope.register() {
        val bus = events
        onWorldStart { load(this) }
        onItemOp("Rub") { click -> rubFromBackpack(click, bus) }
        for ((component, wearPos) in WORN) {
            for (op in FIRST_WORN_DESTINATION_OP..LAST_WORN_DESTINATION_OP) {
                onButton(component, op) { wornDestination(wearPos, op - FIRST_WORN_DESTINATION_OP, bus) }
            }
        }
    }

    private fun PlayerRef.rubFromBackpack(click: ButtonClick, bus: EventBus) {
        val inv = inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != click.obj) return
        val held = heldByObj[click.obj] ?: return
        if (isDelayed || Teleports.isBusy(this)) return
        interrupt() // inventory item actions interrupt (interactions.md I7)
        if (held.charges == 0) return message("Your ${held.jewel.itemName} has no charges.")
        val place = Place(Invs.INV, click.slot)
        val jewel = held.jewel
        // Right-click "Rub" sub-options (IF_SUBOP) name the destination; the plain op asks in the chatbox.
        if (click.subop >= 0) {
            val text = objSubOp(click.obj, click.op, click.subop)
            val destination = jewel.destinations.firstOrNull { text != null && it.name.contains(text, ignoreCase = true) }
            if (destination != null) return use(place, click.obj, destination, bus)
        }
        choiceDialog(MENU_TITLE, jewel.destinations.map { it.name }) { pick -> use(place, click.obj, jewel.destinations[pick - 1], bus) }
    }

    private fun PlayerRef.wornDestination(wearPos: Int, index: Int, bus: EventBus) {
        val obj = inv(Invs.WORN).obj(wearPos)
        val held = heldByObj[obj] ?: return
        if (held.charges == 0) return message("Your ${held.jewel.itemName} has no charges.")
        // Ops past the teleports (the Ring of wealth's Boss Log and Coin Collection) are features we don't have (OPEN-TP12).
        val destination = held.jewel.destinations.getOrNull(index) ?: return message("That option isn't available yet.")
        use(Place(Invs.WORN, wearPos), obj, destination, bus)
    }

    /** Spends one charge of [obj] at [place] and teleports to [destination] (teleports.md J3-J5). */
    private fun PlayerRef.use(place: Place, obj: Int, destination: Destination, bus: EventBus) {
        if (isDelayed || Teleports.isBusy(this)) return
        val view = inv(place.inv)
        if (place.slot !in 0 until view.size || view.obj(place.slot) != obj) return
        val held = heldByObj[obj] ?: return
        if (held.charges == 0) return
        val jewel = held.jewel
        val denial = Teleports.denial(this, jewel.wildernessLimit)
        if (denial != null) return message(denial)
        val spent =
            transaction("${jewel.itemName} charge") {
                if (held.next >= 0) replaceAt(place.inv, place.slot, obj, held.next) else removeAt(place.inv, place.slot, obj, 1)
            }
        if (!spent) return
        message("You rub the ${jewel.noun}...")
        message(chargeMessage(held))
        if (place.inv == Invs.WORN) bus.publish(AppearanceChanged(this))
        Teleports.start(this, CastStyle.STANDARD, destination.coord)
    }

    private fun chargeMessage(held: Held): String {
        val jewel = held.jewel
        val left = held.charges - 1
        return when {
            left == 0 && jewel.uncharged == null -> "Your ${jewel.itemName} crumbles to dust."
            left == 0 -> "Your ${jewel.itemName} has run out of charges."
            jewel.wording == Wording.USES -> "Your ${jewel.itemName} has ${NUMBER_WORDS[left]} ${if (left == 1) "use" else "uses"} left."
            else -> "Your ${jewel.itemName} has $left ${if (left == 1) "charge" else "charges"} left."
        }
    }

    private companion object {
        /** Worn tab components of the neck, hands and ring slots with the wear position each shows (RS Mod wear positions). */
        val WORN: List<Pair<Int, Int>> =
            listOf(
                Components.Wornitems.SLOT2 to 2,
                Components.Wornitems.SLOT9 to 9,
                Components.Wornitems.SLOT12 to 12,
            )

        /** Worn op 1 is "Remove"; the destinations follow as ops 2.. (cache obj params 451+; OPEN-TP12). */
        const val FIRST_WORN_DESTINATION_OP = 2

        /** The widest worn menu: Ring of wealth, 6 options (Wiki wornoptions). */
        const val LAST_WORN_DESTINATION_OP = 7

        /** OPEN-TP1: the Wiki gives no title for the destination menu. */
        const val MENU_TITLE = "Where would you like to teleport to?"

        /** Number words for the games necklace and ring of dueling messages (8 charges at most, Wiki). */
        val NUMBER_WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight")

        /** Obj id -> charges, built once at world start from the cache names (immutable afterwards). */
        var heldByObj: Map<Int, Held> = emptyMap()

        fun load(world: WorldRef) {
            val table = HashMap<Int, Held>()
            for (jewel in JEWELS) {
                val uncharged = jewel.uncharged?.let { world.objIdsNamed(it) }.orEmpty()
                for (id in uncharged) table[id] = Held(jewel, 0, -1)
                for (charges in 1..jewel.maxCharges) {
                    val next =
                        if (charges ==
                            1
                        ) {
                            uncharged.firstOrNull() ?: -1
                        } else {
                            world.objIdsNamed(jewel.charged(charges - 1)).firstOrNull() ?: -1
                        }
                    for (id in world.objIdsNamed(jewel.charged(charges))) table[id] = Held(jewel, charges, next)
                }
            }
            heldByObj = table
        }

        @Suppress("MagicNumber")
        val JEWELS: List<Jewel> =
            listOf(
                // Wiki "Amulet of glory": 4 charges (6 from the Fountain of Rune); destinations from "Teleportation options".
                Jewel(
                    "amulet",
                    "amulet of glory",
                    6,
                    { "Amulet of glory($it)" },
                    "Amulet of glory",
                    Teleports.DRAGONSTONE_WILDERNESS_LIMIT,
                    listOf(
                        Destination("Edgeville", 3087, 3496),
                        Destination("Karamja", 2918, 3176),
                        Destination("Draynor Village", 3105, 3251),
                        Destination("Al Kharid", 3293, 3163),
                    ),
                    Wording.CHARGES,
                ),
                // Wiki "Games necklace": 8 charges, destroyed after the last.
                Jewel(
                    "necklace",
                    "games necklace",
                    8,
                    { "Games necklace($it)" },
                    null,
                    Teleports.WILDERNESS_LIMIT,
                    listOf(
                        Destination("Burthorpe", 2899, 3553),
                        Destination("Barbarian Outpost", 2520, 3571),
                        Destination("Corporeal Beast", 2967, 4254),
                        Destination("Tears of Guthix", 3245, 9500),
                        Destination("Wintertodt Camp", 1631, 3940),
                    ),
                    Wording.USES,
                ),
                // Wiki "Ring of dueling": 8 charges, destroyed after the last.
                Jewel(
                    "ring",
                    "ring of dueling",
                    8,
                    { "Ring of dueling($it)" },
                    null,
                    Teleports.WILDERNESS_LIMIT,
                    listOf(
                        Destination("Emir's Arena", 3315, 3235),
                        Destination("Castle Wars", 2440, 3090),
                        Destination("Ferox Enclave", 3151, 3635),
                        Destination("Fortis Colosseum", 1793, 3107),
                    ),
                    Wording.USES,
                ),
                // Wiki "Combat bracelet": 4 charges (6 from the Fountain of Rune).
                Jewel(
                    "bracelet",
                    "combat bracelet",
                    6,
                    { "Combat bracelet($it)" },
                    "Combat bracelet",
                    Teleports.DRAGONSTONE_WILDERNESS_LIMIT,
                    listOf(
                        Destination("Warriors' Guild", 2882, 3547),
                        Destination("Champions' Guild", 3192, 3368),
                        Destination("Monastery", 3052, 3490),
                        Destination("Ranging Guild", 2653, 3439),
                    ),
                    Wording.CHARGES,
                ),
                // Wiki "Skills necklace": 4 charges (6 from the Fountain of Rune).
                Jewel(
                    "necklace",
                    "skills necklace",
                    6,
                    { "Skills necklace($it)" },
                    "Skills necklace",
                    Teleports.DRAGONSTONE_WILDERNESS_LIMIT,
                    listOf(
                        Destination("Fishing Guild", 2611, 3390),
                        Destination("Mining Guild", 3049, 9763),
                        Destination("Crafting Guild", 2933, 3295),
                        Destination("Cooking Guild", 3144, 3438),
                        Destination("Woodcutting Guild", 1662, 3505),
                        Destination("Farming Guild", 1248, 3719),
                    ),
                    Wording.CHARGES,
                ),
                // Wiki "Ring of wealth": 5 charges, only from the Fountain of Rune. Boss Log / Coin Collection are no teleports.
                Jewel(
                    "ring",
                    "ring of wealth",
                    5,
                    { "Ring of wealth ($it)" },
                    "Ring of wealth",
                    Teleports.DRAGONSTONE_WILDERNESS_LIMIT,
                    listOf(
                        Destination("Miscellania", 2534, 3862),
                        Destination("Grand Exchange", 3163, 3478),
                        Destination("Falador", 2995, 3375),
                        Destination("Dondakan", 2824, 10168),
                    ),
                    Wording.CHARGES,
                ),
            )
    }
}
