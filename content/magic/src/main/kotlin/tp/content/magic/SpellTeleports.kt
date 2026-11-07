package tp.content.magic

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid

/** Spellbook varbit `spellbook` values (RS Mod `Spellbook`: standard 0, ancients 1, lunars 2, arceuus 3). */
internal object Book {
    const val STANDARD = 0
    const val ANCIENT = 1
    const val LUNAR = 2
    const val ARCEUUS = 3
}

/**
 * A rune-costing teleport spell (OSRS Wiki spell pages, cited per row in teleports.md). [tablet] is the matching magic
 * tablet obj (-1 = none), [unlock] the quest or unlock the Wiki requires (teleports.md OPEN-TP5), [deepWarning] the
 * "Deep Wilderness" confirmation of Ice Plateau Teleport.
 */
internal class TeleportSpell(
    val component: Int,
    val book: Int,
    val level: Int,
    val xp: Double,
    val runes: List<Pair<Int, Int>>,
    val destination: CoordGrid,
    val style: CastStyle,
    val tablet: Int = -1,
    val unlock: String? = null,
    val deepWarning: Boolean = false,
)

/** Rune-costing teleport spells of the standard, ancient and lunar spellbooks, cast from the spellbook tab. */
public class SpellTeleports : ContentPlugin {
    override fun ContentScope.register() {
        for (spell in SPELLS) onButton(spell.component, OP_CAST) { cast(spell, confirmed = false) }
    }

    private fun PlayerRef.cast(spell: TeleportSpell, confirmed: Boolean) {
        // A delayed player ignores the click; a second click in the same input phase would otherwise cast twice.
        if (isDelayed || Teleports.isBusy(this)) return
        // The ancient and lunar components are in the same interface: only the active book's spells may be cast.
        if (varbit(Varbits.SPELLBOOK) != spell.book) return
        if (ENFORCE_UNLOCKS && spell.unlock != null) return message("You need to have completed ${spell.unlock} to cast this spell.")
        if (level(MAGIC) < spell.level) return message("You need a Magic level of ${spell.level} to cast this spell.")
        val denial = Teleports.denial(this, Teleports.WILDERNESS_LIMIT)
        if (denial != null) return message(denial)
        if (spell.deepWarning && !confirmed) {
            choiceDialog(DEEP_WILDERNESS_WARNING, listOf("Yes", "No")) { pick -> if (pick == 1) cast(spell, confirmed = true) }
            return
        }
        if (!transaction("teleport runes") { SpellRunes.take(this, this@cast, spell.runes) }) {
            return message(Casting.NO_RUNES)
        }
        addXp(MAGIC, spell.xp)
        Spellbooks.onCast(this)
        Teleports.start(this, spell.style, spell.destination)
    }

    internal companion object {
        /** Button op 1 = the first action, "Cast" (cache interface 218 actions). */
        const val OP_CAST = 1

        /** Magic skill id (cache stat order, as in content/combat `Stat`). */
        const val MAGIC = 6

        /**
         * OPEN-TP5: no quest or unlock state exists, so by default level and runes decide. Turning this on refuses every
         * spell that has an [TeleportSpell.unlock] (nothing can satisfy it yet); it exists to flip once quests do.
         */
        const val ENFORCE_UNLOCKS = false

        /** OPEN-TP1: the Wiki says Ice Plateau Teleport "warns the player ... with an option to abort" but gives no text. */
        const val DEEP_WILDERNESS_WARNING = "You are about to teleport into the Deep Wilderness. Continue?"

        /**
         * Destination of Teleport to House: no player-owned houses exist, so this is our Lumbridge respawn point
         * (content/combat `Combat.RESPAWN`). OPEN-TP4.
         */
        val HOUSE_FALLBACK: CoordGrid = CoordGrid(3222, 3218, 0)

        /** Magic tablet obj -> spell it stores. */
        val BY_TABLET: Map<Int, TeleportSpell> by lazy { SPELLS.filter { it.tablet >= 0 }.associateBy { it.tablet } }

        /**
         * Level, XP, runes and destination from each spell's Wiki page (infobox and cost table; destinations are the
         * Wiki's map pin or the centre of its map polygon). Components: `tp.symbols.Components.Magic_Spellbook`.
         */
        @Suppress("MagicNumber")
        val SPELLS: List<TeleportSpell> =
            listOf(
                // Standard spellbook
                TeleportSpell(
                    Components.Magic_Spellbook.VARROCK_TELEPORT,
                    Book.STANDARD,
                    25,
                    35.0,
                    listOf(Objs.LAWRUNE to 1, Objs.AIRRUNE to 3, Objs.FIRERUNE to 1),
                    CoordGrid(3213, 3424, 0),
                    CastStyle.STANDARD,
                    tablet = Objs.POH_TABLET_VARROCKTELEPORT,
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.LUMBRIDGE_TELEPORT,
                    Book.STANDARD,
                    31,
                    41.0,
                    listOf(Objs.LAWRUNE to 1, Objs.AIRRUNE to 3, Objs.EARTHRUNE to 1),
                    CoordGrid(3222, 3218, 0),
                    CastStyle.STANDARD,
                    tablet = Objs.POH_TABLET_LUMBRIDGETELEPORT,
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.FALADOR_TELEPORT,
                    Book.STANDARD,
                    37,
                    48.0,
                    listOf(Objs.LAWRUNE to 1, Objs.AIRRUNE to 3, Objs.WATERRUNE to 1),
                    CoordGrid(2964, 3378, 0),
                    CastStyle.STANDARD,
                    tablet = Objs.POH_TABLET_FALADORTELEPORT,
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.TELEPORT_TO_YOUR_HOUSE,
                    Book.STANDARD,
                    40,
                    30.0,
                    listOf(Objs.LAWRUNE to 1, Objs.AIRRUNE to 1, Objs.EARTHRUNE to 1),
                    HOUSE_FALLBACK,
                    CastStyle.STANDARD,
                    tablet = Objs.POH_TABLET_TELEPORTTOHOUSE,
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.CAMELOT_TELEPORT,
                    Book.STANDARD,
                    45,
                    55.5,
                    listOf(Objs.LAWRUNE to 1, Objs.AIRRUNE to 5),
                    CoordGrid(2757, 3479, 0),
                    CastStyle.STANDARD,
                    tablet = Objs.POH_TABLET_CAMELOTTELEPORT,
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.KOUREND_TELEPORT,
                    Book.STANDARD,
                    48,
                    58.0,
                    listOf(Objs.LAWRUNE to 2, Objs.FIRERUNE to 1, Objs.WATERRUNE to 1),
                    CoordGrid(1641, 3673, 0),
                    CastStyle.STANDARD,
                    tablet = Objs.POH_TABLET_KOURENDTELEPORT,
                    unlock = "Client of Kourend",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.ARDOUGNE_TELEPORT,
                    Book.STANDARD,
                    51,
                    61.0,
                    listOf(Objs.LAWRUNE to 2, Objs.WATERRUNE to 2),
                    CoordGrid(2662, 3305, 0),
                    CastStyle.STANDARD,
                    tablet = Objs.POH_TABLET_ARDOUGNETELEPORT,
                    unlock = "Plague City",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.FORTIS_TELEPORT,
                    Book.STANDARD,
                    54,
                    64.0,
                    listOf(Objs.LAWRUNE to 2, Objs.EARTHRUNE to 1, Objs.FIRERUNE to 1),
                    CoordGrid(1681, 3133, 0),
                    CastStyle.STANDARD,
                    tablet = Objs.POH_TABLET_FORTISTELEPORT,
                    unlock = "Twilight's Promise",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.WATCHTOWER_TELEPORT,
                    Book.STANDARD,
                    58,
                    68.0,
                    listOf(Objs.LAWRUNE to 2, Objs.EARTHRUNE to 2),
                    CoordGrid(2546, 3114, 0),
                    CastStyle.STANDARD,
                    tablet = Objs.POH_TABLET_WATCHTOWERTELEPORT,
                    unlock = "Watchtower",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.TROLLHEIM_TELEPORT,
                    Book.STANDARD,
                    61,
                    68.0,
                    listOf(Objs.LAWRUNE to 2, Objs.FIRERUNE to 2),
                    CoordGrid(2890, 3679, 0),
                    CastStyle.STANDARD,
                    unlock = "Eadgar's Ruse",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.APE_TELEPORT,
                    Book.STANDARD,
                    64,
                    74.0,
                    listOf(Objs.LAWRUNE to 2, Objs.FIRERUNE to 2, Objs.WATERRUNE to 2, Objs.BANANA to 1),
                    CoordGrid(2798, 2799, 0),
                    CastStyle.STANDARD,
                    unlock = "Recipe for Disaster (Freeing King Awowogei)",
                ),
                // Ancient Magicks (components `zarosteleport1..8` in level order: OPEN-TP7)
                TeleportSpell(
                    Components.Magic_Spellbook.ZAROSTELEPORT1,
                    Book.ANCIENT,
                    54,
                    64.0,
                    listOf(Objs.LAWRUNE to 2, Objs.AIRRUNE to 1, Objs.FIRERUNE to 1),
                    CoordGrid(3097, 9881, 0),
                    CastStyle.ANCIENT,
                    tablet = Objs.TABLET_PADDEWA,
                    unlock = "Desert Treasure I",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.ZAROSTELEPORT2,
                    Book.ANCIENT,
                    60,
                    70.0,
                    listOf(Objs.LAWRUNE to 2, Objs.SOULRUNE to 1),
                    CoordGrid(3320, 3337, 0),
                    CastStyle.ANCIENT,
                    tablet = Objs.TABLET_SENNTISTEN,
                    unlock = "Desert Treasure I",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.ZAROSTELEPORT3,
                    Book.ANCIENT,
                    66,
                    76.0,
                    listOf(Objs.LAWRUNE to 2, Objs.BLOODRUNE to 1),
                    CoordGrid(3494, 3473, 0),
                    CastStyle.ANCIENT,
                    tablet = Objs.TABLET_KHARYLL,
                    unlock = "Desert Treasure I",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.ZAROSTELEPORT4,
                    Book.ANCIENT,
                    72,
                    82.0,
                    listOf(Objs.LAWRUNE to 2, Objs.WATERRUNE to 4),
                    CoordGrid(3004, 3470, 0),
                    CastStyle.ANCIENT,
                    tablet = Objs.TABLET_LASSAR,
                    unlock = "Desert Treasure I",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.ZAROSTELEPORT5,
                    Book.ANCIENT,
                    78,
                    88.0,
                    listOf(Objs.LAWRUNE to 2, Objs.FIRERUNE to 3, Objs.AIRRUNE to 2),
                    CoordGrid(2968, 3696, 0),
                    CastStyle.ANCIENT,
                    tablet = Objs.TABLET_DAREEYAK,
                    unlock = "Desert Treasure I",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.ZAROSTELEPORT6,
                    Book.ANCIENT,
                    84,
                    94.0,
                    listOf(Objs.LAWRUNE to 2, Objs.SOULRUNE to 2),
                    CoordGrid(3158, 3666, 0),
                    CastStyle.ANCIENT,
                    tablet = Objs.TABLET_CARRALLANGAR,
                    unlock = "Desert Treasure I",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.ZAROSTELEPORT7,
                    Book.ANCIENT,
                    90,
                    100.0,
                    listOf(Objs.LAWRUNE to 2, Objs.BLOODRUNE to 2),
                    CoordGrid(3288, 3888, 0),
                    CastStyle.ANCIENT,
                    tablet = Objs.TABLET_ANNAKARL,
                    unlock = "Desert Treasure I",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.ZAROSTELEPORT8,
                    Book.ANCIENT,
                    96,
                    106.0,
                    listOf(Objs.LAWRUNE to 2, Objs.WATERRUNE to 8),
                    CoordGrid(2976, 3872, 0),
                    CastStyle.ANCIENT,
                    tablet = Objs.TABLET_GHORROCK,
                    unlock = "Desert Treasure I",
                ),
                // Lunar spellbook (single-target teleports; the Tele Group spells are not implemented: OPEN-TP8)
                TeleportSpell(
                    Components.Magic_Spellbook.TELE_MOONCLAN,
                    Book.LUNAR,
                    69,
                    66.0,
                    listOf(Objs.ASTRALRUNE to 2, Objs.LAWRUNE to 1, Objs.EARTHRUNE to 2),
                    CoordGrid(2113, 3915, 0),
                    CastStyle.LUNAR,
                    unlock = "Lunar Diplomacy",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.OURANIA_TELEPORT,
                    Book.LUNAR,
                    71,
                    69.0,
                    listOf(Objs.ASTRALRUNE to 2, Objs.LAWRUNE to 1, Objs.EARTHRUNE to 6),
                    CoordGrid(2468, 3246, 0),
                    CastStyle.LUNAR,
                    unlock = "Lunar Diplomacy and Baba Yaga's unlock",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.TELE_WATERBIRTH,
                    Book.LUNAR,
                    72,
                    71.0,
                    listOf(Objs.ASTRALRUNE to 2, Objs.LAWRUNE to 1, Objs.WATERRUNE to 1),
                    CoordGrid(2546, 3755, 0),
                    CastStyle.LUNAR,
                    unlock = "Lunar Diplomacy",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.TELE_BARB_OUT,
                    Book.LUNAR,
                    75,
                    76.0,
                    listOf(Objs.ASTRALRUNE to 2, Objs.LAWRUNE to 2, Objs.FIRERUNE to 3),
                    CoordGrid(2543, 3569, 0),
                    CastStyle.LUNAR,
                    unlock = "Lunar Diplomacy",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.TELE_KHAZARD,
                    Book.LUNAR,
                    78,
                    80.0,
                    listOf(Objs.ASTRALRUNE to 2, Objs.LAWRUNE to 2, Objs.WATERRUNE to 4),
                    CoordGrid(2636, 3167, 0),
                    CastStyle.LUNAR,
                    unlock = "Lunar Diplomacy",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.TELE_FISH,
                    Book.LUNAR,
                    85,
                    89.0,
                    listOf(Objs.ASTRALRUNE to 3, Objs.LAWRUNE to 3, Objs.WATERRUNE to 10),
                    CoordGrid(2613, 3391, 0),
                    CastStyle.LUNAR,
                    unlock = "Lunar Diplomacy",
                ),
                TeleportSpell(
                    Components.Magic_Spellbook.TELE_CATHER,
                    Book.LUNAR,
                    87,
                    92.0,
                    listOf(Objs.ASTRALRUNE to 3, Objs.LAWRUNE to 3, Objs.WATERRUNE to 10),
                    CoordGrid(2801, 3450, 0),
                    CastStyle.LUNAR,
                    unlock = "Lunar Diplomacy",
                ),
                // Component `tele_ghorrock` is the cache's name for Ice Plateau Teleport (OPEN-TP7).
                TeleportSpell(
                    Components.Magic_Spellbook.TELE_GHORROCK,
                    Book.LUNAR,
                    89,
                    96.0,
                    listOf(Objs.ASTRALRUNE to 3, Objs.LAWRUNE to 3, Objs.WATERRUNE to 8),
                    CoordGrid(2975, 3938, 0),
                    CastStyle.LUNAR,
                    unlock = "Lunar Diplomacy",
                    deepWarning = true,
                ),
            ) + arceuusSpells()

        /**
         * Arceuus spellbook teleports (OSRS Wiki spell pages, magic-utility.md AR1). Destinations are the Wiki's map pin or
         * the centre of its polygon; Respawn Teleport goes to our respawn point, Lumbridge (OPEN-MU7).
         */
        @Suppress("MagicNumber")
        private fun arceuusSpells(): List<TeleportSpell> =
            listOf(
                arceuus(Components.Magic_Spellbook.TELEPORT_ARCEUUS_LIBRARY, 6, 9.5, 1632, 3837, Objs.LAWRUNE to 1, Objs.EARTHRUNE to 2),
                arceuus(
                    Components.Magic_Spellbook.TELEPORT_DRAYNOR_MANOR,
                    17,
                    16.0,
                    3109,
                    3351,
                    Objs.LAWRUNE to 1,
                    Objs.EARTHRUNE to 1,
                    Objs.WATERRUNE to 1,
                ),
                arceuus(
                    Components.Magic_Spellbook.TELEPORT_BATTLEFRONT,
                    23,
                    19.0,
                    1347,
                    3740,
                    Objs.LAWRUNE to 1,
                    Objs.EARTHRUNE to 1,
                    Objs.FIRERUNE to 1,
                ),
                arceuus(Components.Magic_Spellbook.TELEPORT_MIND_ALTAR, 28, 22.0, 2979, 3510, Objs.LAWRUNE to 1, Objs.MINDRUNE to 2),
                arceuus(Components.Magic_Spellbook.TELEPORT_RESPAWN, 34, 27.0, 3222, 3218, Objs.LAWRUNE to 1, Objs.SOULRUNE to 1),
                arceuus(Components.Magic_Spellbook.TELEPORT_SALVE_GRAVEYARD, 40, 30.0, 3433, 3461, Objs.LAWRUNE to 1, Objs.SOULRUNE to 2),
                arceuus(
                    Components.Magic_Spellbook.TELEPORT_FENKENSTRAIN_CASTLE,
                    48,
                    50.0,
                    3548,
                    3529,
                    Objs.LAWRUNE to 1,
                    Objs.SOULRUNE to 1,
                    Objs.EARTHRUNE to 1,
                ),
                arceuus(Components.Magic_Spellbook.TELEPORT_WEST_ARDOUGNE, 61, 68.0, 2500, 3290, Objs.LAWRUNE to 2, Objs.SOULRUNE to 2),
                arceuus(
                    Components.Magic_Spellbook.TELEPORT_HARMONY_ISLAND,
                    65,
                    74.0,
                    3797,
                    2866,
                    Objs.LAWRUNE to 1,
                    Objs.SOULRUNE to 1,
                    Objs.NATURERUNE to 1,
                ),
                arceuus(
                    Components.Magic_Spellbook.TELEPORT_CEMETERY,
                    71,
                    82.0,
                    2980,
                    3763,
                    Objs.LAWRUNE to 1,
                    Objs.SOULRUNE to 1,
                    Objs.BLOODRUNE to 1,
                ),
                arceuus(
                    Components.Magic_Spellbook.TELEPORT_BARROWS,
                    83,
                    90.0,
                    3564,
                    3315,
                    Objs.LAWRUNE to 2,
                    Objs.SOULRUNE to 2,
                    Objs.BLOODRUNE to 1,
                ),
            )

        @Suppress("LongParameterList")
        private fun arceuus(component: Int, level: Int, xp: Double, x: Int, z: Int, vararg runes: Pair<Int, Int>): TeleportSpell =
            TeleportSpell(
                component,
                Book.ARCEUUS,
                level,
                xp,
                runes.toList(),
                CoordGrid(x, z, 0),
                CastStyle.ARCEUUS,
                unlock = "the Arceuus spellbook",
            )
    }
}
