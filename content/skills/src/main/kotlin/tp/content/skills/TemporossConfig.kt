package tp.content.skills

import tp.util.CoordGrid

/**
 * Every number of the Tempoross minigame (docs/mechanics/tempoross.md). Sections marked OPEN-TM<n> are not published: they are
 * placeholders, kept here and nowhere else so they can be tuned in one place. The rest cite the Wiki page they come from
 * ("Tempoross" = W, "Tempoross/Strategies" = S, "Reward pool" = RP, "Fire (Tempoross Cove)" = F, "Raw harpoonfish" = RH).
 */
internal object TemporossConfig {
    // ---- entering (W "Starting a game", "Required items")

    /** W: 35 Fishing is required to fight Tempoross. */
    const val MIN_FISHING = 35

    /** W: the encounter starts 30 seconds (50 ticks) after the first player boards, or as soon as 40 players are aboard. */
    const val LOBBY_WAIT_TICKS = 50
    const val LOBBY_MAX_PLAYERS = 40

    /** W: the ship leaves 30 seconds after the first boarding; the players are taken to the cove after this many ticks of sailing. */
    const val SAIL_TICKS = 3

    /** W: "automatically sent back to the Ruins of Unkah after approximately 20 seconds" (33 ticks). */
    const val VICTORY_LEAVE_TICKS = 33

    // ---- map (cache map square 47,44; Wiki pins agree with the cache loc tiles)

    /** South-west tile of the cove's map square (3008, 2816); the template of the instance. */
    val TEMPLATE_BASE = CoordGrid(3008, 2816, 0)

    /** The cove spans levels 0 (island, docks) to 2 (masts); all three are copied into the instance. */
    const val TEMPLATE_LEVELS = 3
    const val ZONE = 8
    const val ZONES_PER_SQUARE = 8

    /** Where a player arriving by ship stands: the free deck tiles of the west and east ships (cache collision). */
    val ARRIVAL = listOf(CoordGrid(3036, 2852, 0), CoordGrid(3058, 2852, 0))

    /** OPEN-TM8: the lobby ship deck and the dock below the rope ladder (loc 41305 at 3135,2840) are read off the cache collision. */
    val LOBBY_DECK = CoordGrid(3139, 2840, 0)
    val DOCK = CoordGrid(3133, 2840, 0)

    /** The wiki pins of the harpoonfish spots (W "Fishing spot (Tempoross Cove)"): three on the north island, three on the south. */
    val NORTH_SPOTS = listOf(CoordGrid(3048, 2874, 0), CoordGrid(3051, 2867, 0), CoordGrid(3037, 2868, 0))
    val SOUTH_SPOTS = listOf(CoordGrid(3036, 2835, 0), CoordGrid(3048, 2829, 0), CoordGrid(3046, 2826, 0))

    /** Wiki pins of the two spirit pools (centres of 3x3 NPCs; NPCs are placed by their south-west tile). */
    val POOL_CENTRES = listOf(CoordGrid(3047, 2854, 0), CoordGrid(3047, 2844, 0))
    const val POOL_SIZE = 3

    /** Tempoross is a 9x9 NPC in the water between the ships (OPEN-TM8: tile estimated from the cove layout). */
    val BOSS_TILE = CoordGrid(3043, 2845, 0)

    /** OPEN-TM8: the instance hosts (Captain Pudi east, Captain Dudi west) stand by the ships' crates; tiles estimated. */
    val HOST_EAST = CoordGrid(3057, 2852, 0)
    val HOST_WEST = CoordGrid(3037, 2852, 0)

    /** A rectangle of the cove on one level. */
    class Area(val level: Int, val x: IntRange, val z: IntRange)

    /** Where the random lightning clouds may strike: the island and the two ship decks (OPEN-TM8: bounds read off the cache collision). */
    val STRIKE_AREAS = listOf(Area(0, 3030..3064, 2824..2876))

    // ---- energy and essence (W "Energy phase", "Essence phase"; hitsplat values are x10 "behind the scenes", Mod Squid)

    /**
     * OPEN-TM1: a solo game starts at about 2.6k energy; the Strategies table (27 raw or 18 cooked fish) and "4%
     * left after 17 cooked" fit 2650.
     */
    const val ENERGY_SOLO = 2650

    /** W (Mod Squid): each of 40 players adds about 1.8k energy. */
    const val ENERGY_AT_40 = 1800

    /** W (Mod Squid): about 2.5k essence per player alone, 1.7k each at 40 players. */
    const val ESSENCE_SOLO = 2500
    const val ESSENCE_AT_40 = 1700

    /** W: a raw (or crystallised) harpoonfish drains 10 energy, a cooked one 15 (x10 internal). */
    const val RAW_DRAIN = 100
    const val COOKED_DRAIN = 150

    /**
     * W: "the max hit any player can deal is 12" at the spirit pool (x10 internal); OPEN-TM4: every success is
     * assumed to hit the maximum.
     */
    const val POOL_HIT = 120

    /**
     * OPEN-TM3: how fast the energy comes back while Tempoross is submerged is not published; S says ~18 scatters (4
     * ticks each) refill it.
     */
    const val REGEN_TICKS_TO_FULL = 75

    /** W: attacks happen only while Tempoross has more than 10% energy. */
    const val NO_ATTACK_PERCENT = 10

    /** W/S: after three times drained Tempoross is "enraged"; the storm then rises quickly. */
    const val DRAINS_BEFORE_ENRAGE = 3

    // ---- storm intensity

    /** OPEN-TM2: the rise of the storm is not published; S: the fish must be loaded by 93 to 95%, within about 150 ticks of cooking. */
    const val STORM_TICKS_PER_PERCENT = 2
    const val ENRAGED_STORM_TICKS_PER_PERCENT = 1
    const val STORM_MAX = 100

    // ---- cannons

    /** OPEN-TM5: the crates hold the fish and the cannons fire one every this many ticks while Tempoross is surfaced. */
    const val CANNON_FIRE_TICKS = 2

    /** Wiki "Ammunition crate": the crate looks moderate from 8 stored fish and overflowing from 15. */
    const val CRATE_MODERATE = 8
    const val CRATE_OVERFLOW = 15

    /** OPEN-TM5: one fish is loaded per tick while the player keeps the crate selected. */
    const val LOAD_TICKS = 1

    // ---- attacks (W "Tempoross' attacks"; all timings are OPEN-TM6)

    const val ATTACK_FIRST_TICKS = 80
    const val ATTACK_GAP_MIN = 60
    const val ATTACK_GAP_SPREAD = 60
    const val WAVE_WARNING_TICKS = 12
    const val WAVE_DURATION_TICKS = 4

    /** W: how many tiles a wave pushes an untethered player ("a few tiles"). */
    const val WAVE_PUSH_TILES = 3

    /** W/Mod Ash: a tether site breaks with 15% to 25%, depending on the storm intensity. */
    const val BREAK_MIN_PERCENT = 15
    const val BREAK_SPREAD_PERCENT = 10
    const val PERCENT = 100

    /** OPEN-TM6: lightning strikes this many spots, this long after the clouds roll in. */
    const val CLOUDS = 3
    const val CLOUD_DELAY_TICKS = 8

    /** OPEN-TM6: the torrent reaches the cannon after this long and disables it (and stuns those loading) for this long. */
    const val TORRENT_DELAY_TICKS = 4
    const val TORRENT_DISABLE_TICKS = 30
    const val STUN_TICKS = 4
    const val STUN_RANGE = 2

    // ---- fires (F)

    /** F: a fire is a 2x2 area; it spreads at most twice; standing in it destroys an item every two ticks. */
    const val FIRE_SIZE = 2
    const val FIRE_MAX_SPREADS = 2
    const val FIRE_ITEM_LOSS_TICKS = 2

    /** OPEN-TM6: a fire spreads this long after it was lit. */
    const val FIRE_SPREAD_TICKS = 10

    // ---- fishing (RH, W "Fishing spot (Tempoross Cove)")

    /** W: 50% chance of a bonus fish at the jumping spots (Mod Squid). */
    const val BONUS_FISH_PERCENT = 50

    /** The crystal harpoon: one in three catches is crystallised; the infernal harpoon cooks one in three (S, W). */
    const val CRYSTAL_ONE_IN = 3
    const val INFERNAL_ONE_IN = 3

    /** Same 5-tick fishing cycle as every other spot (Fishing.kt, Wiki "Tick manipulation"). */
    const val FISH_CYCLE_TICKS = 5
    const val FISH_ARM_TICKS = 4

    /** W: the spirit pool restores 3% run energy "every four ticks" (a scatter per cycle); run energy is 0..10000. */
    const val POOL_CYCLE_TICKS = 4
    const val POOL_RUN_RESTORE = 300

    /** OPEN-TM7: when the jumping ("double") spot appears, how long it stays and the gap before the next one. */
    const val DOUBLE_FIRST_TICKS = 60
    const val DOUBLE_DURATION_TICKS = 80
    const val DOUBLE_GAP_TICKS = 40

    /** Wiki "Harpoonfish": cooking takes 3 ticks, gives 10 Cooking xp, needs level 1, never burns. */
    const val COOK_TICKS = 3
    const val COOK_XP = 10.0

    // ---- fishing xp formulas (W "Experience per action")

    const val XP_LOW_BASE = 450
    const val XP_MID_BASE = 890
    const val XP_TOP_BASE = 1000
    const val XP_MID_LEVEL = 70
    const val XP_LAST_LEVEL = 99
    const val XP_SCALE = 100

    /** Filling the crate gives the catch xp times 10; the spirit pool the catch xp times 11 (W). Crystallised fish: 20% more crate xp. */
    const val CRATE_XP_TIMES = 10
    const val POOL_XP_TIMES = 11
    const val CRYSTAL_XP_PERCENT = 120

    /** Wiki "Tempoross" Reward pool: 10 Fishing xp per reward, Construction xp 4x level for repairs (Wiki "Totem pole", "Mast"). */
    const val REWARD_XP = 10.0
    const val REPAIR_XP_PER_LEVEL = 4.0

    // ---- points (W "Reward points")

    const val POINTS_FISH = 5
    const val POINTS_COOK = 10
    const val POINTS_DEPOSIT_RAW = 20
    const val POINTS_DEPOSIT_CRYSTAL = 20
    const val POINTS_DEPOSIT_COOKED = 65
    const val POINTS_REPAIR = 40
    const val POINTS_DOUSE = 40
    const val POINTS_WAVE = 10
    const val POINTS_POOL = 55
    const val POINTS_MAX = 32000

    // ---- permits (W "Rewards")

    /** W: one permit for the first 2,000 points, one more for every 700 beyond; the remainder is a linear chance for one more. */
    const val PERMIT_FIRST = 2000
    const val PERMIT_STEP = 700

    /** RP: up to 8,000 permits can be stored; one reward is taken every 3 ticks; "Big-search" takes five. */
    const val PERMITS_MAX = 8000
    const val SEARCH_TICKS = 3
    const val BIG_SEARCH = 5

    /** The saved-state key of the stored permits and its varbit (`tempoross_rewardpermits`). */
    const val PERMITS_KEY = "tempoross.permits"
}
