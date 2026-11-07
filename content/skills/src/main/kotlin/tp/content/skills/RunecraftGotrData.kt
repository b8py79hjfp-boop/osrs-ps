@file:Suppress("MagicNumber") // data tables: Wiki "Guardians of the Rift" and cache 2757 (docs/mechanics/guardians-of-the-rift.md)

package tp.content.skills

import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * One copy of the Temple of the Eye's minigame floor (docs/mechanics/guardians-of-the-rift.md GR1). The cache holds
 * two: the
 * minigame itself and the quest tutorial, an exact copy shifted by ([dx], [dz]). All coordinates in this file are
 * the minigame's
 * ("main"); [at] moves them into this arena.
 */
internal class Arena(val dx: Int, val dz: Int, val tutorial: Boolean) {
    fun at(x: Int, z: Int): CoordGrid = CoordGrid(x + dx, z + dz, 0)

    fun at(c: CoordGrid): CoordGrid = CoordGrid(c.x + dx, c.z + dz, 0)

    /** The minigame coordinate that [c] (a tile of this arena) corresponds to. */
    fun main(c: CoordGrid): CoordGrid = CoordGrid(c.x - dx, c.z - dz, c.level)

    /** The whole floor including the lobby south of the barrier. */
    operator fun contains(c: CoordGrid): Boolean = c.level == 0 && c.x - dx in FLOOR_X && c.z - dz in FLOOR_Z

    /** North of the barrier line: where the game is played. */
    fun inside(c: CoordGrid): Boolean = c in this && c.z - dz >= INSIDE_FROM_Z

    companion object {
        /** The cache map square 56,148 (tiles 3584..3647 x 9472..9535) plus the entry portal's strip to the south. */
        val FLOOR_X = 3584..3647
        val FLOOR_Z = 9456..9535

        /**
         * The barrier loc stands at z 9483; everything from z 9484 is inside (cache loc `gotr_barrier` at 3613,9483).
         */
        const val INSIDE_FROM_Z = 9484

        val MAIN = Arena(0, 0, false)

        /** The quest tutorial: cache `workbench` at 2459,5710 against 3611,9486 in the minigame. */
        val TUTORIAL = Arena(-1152, -3776, true)
        val ALL = listOf(MAIN, TUTORIAL)

        fun of(c: CoordGrid): Arena? = ALL.firstOrNull { c in it }
    }
}

/** The four cell tiers (Wiki "Guardians of the Rift", Charging cells / Gaining energy tables). */
internal enum class CellTier(
    val obj: Int,
    val place: Int,
    val strengthen: Int,
    val recharge: Int,
    val restore: Int,
    val xp: Double,
    val guardianEnergy: Int,
    val barrierNpc: Int,
    val tileLoc: Int,
    val elementalGuardian: Int,
    val catalyticGuardian: Int,
) {
    WEAK(
        Objs.GOTR_CELL_TIER1,
        17,
        0,
        2,
        10,
        30.0,
        6,
        Npcs.GOTR_BARRIER_NPC_TIER1,
        Locs.GOTR_CELL_TILE_TIER1,
        Npcs.GOTR_GUARDIAN_AIR,
        Npcs.GOTR_GUARDIAN_MIND,
    ),
    MEDIUM(
        Objs.GOTR_CELL_TIER2,
        20,
        7,
        5,
        25,
        100.0,
        15,
        Npcs.GOTR_BARRIER_NPC_TIER2,
        Locs.GOTR_CELL_TILE_TIER2,
        Npcs.GOTR_GUARDIAN_WATER,
        Npcs.GOTR_GUARDIAN_CHAOS,
    ),
    STRONG(
        Objs.GOTR_CELL_TIER3,
        24,
        13,
        9,
        50,
        180.0,
        27,
        Npcs.GOTR_BARRIER_NPC_TIER3,
        Locs.GOTR_CELL_TILE_TIER3,
        Npcs.GOTR_GUARDIAN_EARTH,
        Npcs.GOTR_GUARDIAN_DEATH,
    ),
    OVERCHARGED(
        Objs.GOTR_CELL_TIER4,
        30,
        22,
        15,
        100,
        250.0,
        45,
        Npcs.GOTR_BARRIER_NPC_TIER4,
        Locs.GOTR_CELL_TILE_TIER4,
        Npcs.GOTR_GUARDIAN_FIRE,
        Npcs.GOTR_GUARDIAN_BLOOD,
    ),
    ;

    /** 1-based tier as the Wiki numbers them. */
    val level: Int get() = ordinal + 1

    companion object {
        fun ofObj(obj: Int): CellTier? = entries.firstOrNull { it.obj == obj }
    }
}

/**
 * What an altar gives in the minigame: its portal guardian loc, the cell tier it charges and its stone kind (Wiki
 * cell tables).
 */
internal class GotrAltar(
    val name: String,
    val portal: Int,
    val portalTutorial: Int,
    val tier: CellTier,
    val elemental: Boolean,
    val portalTalisman: Int,
)

internal object GotrData {
    val ALTARS: List<GotrAltar> =
        listOf(
            GotrAltar("air", Locs.GOTR_PORTAL_AIR, Locs.TOTE_TUTORIAL_PORTAL_AIR, CellTier.WEAK, true, Objs.GOTR_PORTAL_TALISMAN_AIR),
            GotrAltar(
                "water",
                Locs.GOTR_PORTAL_WATER,
                Locs.TOTE_TUTORIAL_PORTAL_WATER,
                CellTier.MEDIUM,
                true,
                Objs.GOTR_PORTAL_TALISMAN_WATER,
            ),
            GotrAltar(
                "earth",
                Locs.GOTR_PORTAL_EARTH,
                Locs.TOTE_TUTORIAL_PORTAL_EARTH,
                CellTier.STRONG,
                true,
                Objs.GOTR_PORTAL_TALISMAN_EARTH,
            ),
            GotrAltar(
                "fire",
                Locs.GOTR_PORTAL_FIRE,
                Locs.TOTE_TUTORIAL_PORTAL_FIRE,
                CellTier.OVERCHARGED,
                true,
                Objs.GOTR_PORTAL_TALISMAN_FIRE,
            ),
            GotrAltar("mind", Locs.GOTR_PORTAL_MIND, Locs.TOTE_TUTORIAL_PORTAL_MIND, CellTier.WEAK, false, Objs.GOTR_PORTAL_TALISMAN_MIND),
            GotrAltar("body", Locs.GOTR_PORTAL_BODY, Locs.TOTE_TUTORIAL_PORTAL_BODY, CellTier.WEAK, false, Objs.GOTR_PORTAL_TALISMAN_BODY),
            GotrAltar(
                "cosmic",
                Locs.GOTR_PORTAL_COSMIC,
                Locs.TOTE_TUTORIAL_PORTAL_COSMIC,
                CellTier.MEDIUM,
                false,
                Objs.GOTR_PORTAL_TALISMAN_COSMIC,
            ),
            GotrAltar(
                "chaos",
                Locs.GOTR_PORTAL_CHAOS,
                Locs.TOTE_TUTORIAL_PORTAL_CHAOS,
                CellTier.MEDIUM,
                false,
                Objs.GOTR_PORTAL_TALISMAN_CHAOS,
            ),
            GotrAltar(
                "nature",
                Locs.GOTR_PORTAL_NATURE,
                Locs.TOTE_TUTORIAL_PORTAL_NATURE,
                CellTier.STRONG,
                false,
                Objs.GOTR_PORTAL_TALISMAN_NATURE,
            ),
            GotrAltar("law", Locs.GOTR_PORTAL_LAW, Locs.TOTE_TUTORIAL_PORTAL_LAW, CellTier.STRONG, false, Objs.GOTR_PORTAL_TALISMAN_LAW),
            GotrAltar(
                "death",
                Locs.GOTR_PORTAL_DEATH,
                Locs.TOTE_TUTORIAL_PORTAL_DEATH,
                CellTier.OVERCHARGED,
                false,
                Objs.GOTR_PORTAL_TALISMAN_DEATH,
            ),
            GotrAltar(
                "blood",
                Locs.GOTR_PORTAL_BLOOD,
                Locs.TOTE_TUTORIAL_PORTAL_BLOOD,
                CellTier.OVERCHARGED,
                false,
                Objs.GOTR_PORTAL_TALISMAN_BLOOD,
            ),
        )

    val ELEMENTAL: List<GotrAltar> = ALTARS.filter { it.elemental }
    val CATALYTIC: List<GotrAltar> = ALTARS.filterNot { it.elemental }

    /**
     * The seven inactive cell tiles in front of the Great Guardian (cache `gotr_cell_tile_inactive` placements,
     * minigame tiles).
     */
    val CELL_TILES: List<CoordGrid> =
        listOf(
            CoordGrid(3608, 9503, 0),
            CoordGrid(3609, 9507, 0),
            CoordGrid(3611, 9509, 0),
            CoordGrid(3615, 9510, 0),
            CoordGrid(3619, 9509, 0),
            CoordGrid(3621, 9507, 0),
            CoordGrid(3622, 9503, 0),
        )

    /** The one tile of the tutorial arena (cache `gotr_cell_tile_inactive` at 2463,5734) is the minigame's fourth. */
    const val TUTORIAL_TILE = 3

    // Placements of the minigame (cache 2757, map square 56,148).
    val GREAT_GUARDIAN = CoordGrid(3612, 9500, 0)
    val PILE_ELEMENTAL = CoordGrid(3618, 9490, 0)
    val PILE_CATALYTIC = CoordGrid(3611, 9490, 0)
    val RIFT = CoordGrid(3612, 9526, 0)
    val LOBBY = CoordGrid(3613, 9472, 0)
    val BARRIER = CoordGrid(3613, 9483, 0)
    val EAST_RUBBLE = CoordGrid(3634, 9503, 0)
    val EAST_SHELF = CoordGrid(3638, 9503, 0)
    val WEST_SHELF = CoordGrid(3592, 9503, 0)

    /** Barrier loc x range (the loc is 5 wide) where a player crosses. */
    val BARRIER_X = 3611..3615

    /**
     * Candidate tiles of the huge-mine shortcut portals, with the direction the Great Guardian announces (Wiki
     * "Apprentice Tamara").
     */
    val MINE_PORTALS: List<Pair<String, CoordGrid>> =
        listOf(
            "south east" to CoordGrid(3624, 9494, 0),
            "south" to CoordGrid(3614, 9492, 0),
            "east" to CoordGrid(3626, 9503, 0),
            "south west" to CoordGrid(3604, 9494, 0),
        )

    /**
     * Items the minigame takes away at the start of a game and when the player leaves (Wiki "Guardians of the Rift",
     * Phases).
     */
    val GAME_ITEMS: IntArray =
        intArrayOf(
            Objs.GOTR_GUARDIAN_FRAGMENT,
            Objs.GOTR_GUARDIAN_ESSENCE,
            Objs.GOTR_GUARDIAN_STONE_ELEMENTAL,
            Objs.GOTR_GUARDIAN_STONE_CATALYTIC,
            Objs.GOTR_GUARDIAN_STONE_POLYELEMENTAL,
            Objs.GOTR_GUARDIAN_STONE_POLYCATALYTIC,
            Objs.GOTR_CELL_UNCHARGED,
            Objs.GOTR_CELL_TIER1,
            Objs.GOTR_CELL_TIER2,
            Objs.GOTR_CELL_TIER3,
            Objs.GOTR_CELL_TIER4,
            Objs.GOTR_PORTAL_TALISMAN_AIR,
            Objs.GOTR_PORTAL_TALISMAN_WATER,
            Objs.GOTR_PORTAL_TALISMAN_EARTH,
            Objs.GOTR_PORTAL_TALISMAN_FIRE,
            Objs.GOTR_PORTAL_TALISMAN_MIND,
            Objs.GOTR_PORTAL_TALISMAN_CHAOS,
            Objs.GOTR_PORTAL_TALISMAN_DEATH,
            Objs.GOTR_PORTAL_TALISMAN_BLOOD,
            Objs.GOTR_PORTAL_TALISMAN_BODY,
            Objs.GOTR_PORTAL_TALISMAN_COSMIC,
            Objs.GOTR_PORTAL_TALISMAN_NATURE,
            Objs.GOTR_PORTAL_TALISMAN_LAW,
        )
}

/**
 * Numbers of the minigame. Those the Wiki gives carry its wording; the others are OPEN
 * (docs/mechanics/guardians-of-the-rift.md
 * OPEN-GR1..GR8) and live here so one place changes them.
 */
internal object GotrConfig {
    /** Wiki: "giving players two minutes to prepare" (1 tick = 0.6 s). */
    const val PREP_TICKS = 200

    /** OPEN-GR1: the Wiki gives no length for the wait between two games. */
    const val LOBBY_TICKS = 100

    /**
     * OPEN-GR2: how long one pair of active portal guardians stays; the Wiki only says "Only two portals are active
     * at a time".
     */
    const val ROTATE_TICKS = 150

    /** Wiki: shortcut portals "spawning approximately every 2 minutes" and "remain active for around 25 seconds". */
    const val MINE_PORTAL_EVERY = 200
    const val MINE_PORTAL_OPEN = 42

    /** Wiki "Fallen guardian": despawn "after roughly 28 ticks". */
    const val FALLEN_TICKS = 28

    /** OPEN-GR3: the pause after the rift closes or the Great Guardian falls ("a brief period of respite"). */
    const val GRACE_TICKS = 50

    /** Wiki: "250 guardian stones for each player"; beyond 20 players each further one needs 200. */
    const val STONES_PER_PLAYER = 250
    const val STONES_PER_LATE_PLAYER = 200
    const val FULL_TEAM = 20

    /** Wiki: the temple rumbles and damages every barrier by 50 at 60% charge. */
    const val RUMBLE_PERCENT = 60
    const val RUMBLE_DAMAGE = 50

    /**
     * Wiki "Guardian parts"/"Guardian remains" 1-3, "Large guardian remains" 2-4, "Huge guardian remains" 4-6,
     * "Fallen guardian" 2-4 per
     * roll.
     */
    const val FRAGMENT_LOW = 1
    const val FRAGMENT_HIGH = 3
    const val LARGE_LOW = 2
    const val LARGE_HIGH = 4
    const val HUGE_LOW = 4
    const val HUGE_HIGH = 6
    const val FALLEN_LOW = 2
    const val FALLEN_HIGH = 4

    /**
     * Wiki: "The first two cycles of portal guardians that open up are now guaranteed to be medium-tier or higher."
     */
    const val GUARANTEED_CYCLES = 2

    /**
     * OPEN-GR5: ticks a monster takes to reach the barriers, and the chance (1 in n per wave step) that a rift
     * guardian falls while
     * monsters live.
     */
    const val ARRIVAL_TICKS = 12
    const val GUARDIAN_LOSS_ONE_IN = 6

    /** Wiki: a polyelemental / polycatalytic stone "gives three energy". */
    const val POLY_STONE_ENERGY = 3

    /** Wiki "Rift guardian (combatant)": "Only ten rift guardians can be active at any time." */
    const val GUARDIAN_CAP = 10

    /** OPEN-GR4: the Great Guardian's charge when the first phase ends, in percent of its maximum. */
    const val START_PERCENT = 10

    /**
     * OPEN-GR5: ticks between two monster waves, monsters per wave at the start, extra per wave elapsed, and the live
     * cap.
     */
    const val WAVE_TICKS = 8
    const val WAVE_BASE = 2
    const val WAVE_RAMP_TICKS = 100
    const val MONSTER_CAP = 30

    /**
     * OPEN-GR6: health of a fresh barrier (the Wiki only gives restored health 10/25/50/100) and the damage a
     * monster deals per wave step.
     */
    const val BARRIER_HEALTH = 100
    const val MONSTER_DAMAGE = 2

    /** OPEN-GR7: charge a monster at the Great Guardian takes per wave step while no barrier stands, in stones. */
    const val GUARDIAN_DRAIN = 1

    /** OPEN-GR8: damage one rift guardian deals per wave step, times its tier. */
    const val GUARDIAN_DAMAGE = 3

    /** OPEN-GR9: monster health by kind (leech, abyssal guardian, walker). */
    val MONSTER_HEALTH = intArrayOf(6, 12, 20)

    /**
     * Wiki: points need a game's total energy of at least 300; at most 1,200 in all and 1,000 of one kind; xp is
     * Runecraft level x 45.
     */
    const val MIN_ENERGY = 300
    const val MAX_ENERGY_TOTAL = 1200
    const val MAX_ENERGY_KIND = 1000
    const val XP_PER_LEVEL = 45

    /** Wiki: stones give 2 energy and 5 Runecraft xp each; a portal talisman drops 1 in 250 per essence imbued. */
    const val STONE_ENERGY = 2
    const val STONE_XP = 5.0
    const val TALISMAN_ONE_IN = 250

    /**
     * Wiki: recharging a barrier is capped at 2,500 Runecraft xp and 450 energy per game; repairing a cell tile
     * takes 12 fragments for 25
     * energy.
     */
    const val RECHARGE_XP_CAP = 2500.0
    const val RECHARGE_ENERGY_CAP = 450
    const val REPAIR_FRAGMENTS = 12
    const val REPAIR_ENERGY = 25

    /** Wiki: Runecraft 27 to play; Mining xp stops after 250 fragments; at most 10 uncharged cells carried. */
    const val MIN_LEVEL = 27
    const val FRAGMENT_XP_CAP = 250
    const val UNCHARGED_CAP = 10
    const val MINING_XP = 5.0

    /**
     * Wiki "Rubble (Temple of the Eye)" (via "Guardians of the Rift"): the shortcut to the large remains needs 56
     * Agility.
     */
    const val SHORTCUT_AGILITY = 56

    /**
     * Wiki "Rewards Guardian": 25 pearls buy a roll; "1 reward for every 3 ticks"; "Big-search" rolls five at once.
     */
    const val PEARL_ROLL_COST = 25
    const val BIG_SEARCH = 5
}
