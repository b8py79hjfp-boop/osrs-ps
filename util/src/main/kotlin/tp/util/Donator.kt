package tp.util

/**
 * Donator tiers (D-093), by the total a player has donated: the six tiers and thresholds of the Elorin donator page
 * (https://wiki.elorin.org/wiki/Donator_benefits, saved verbatim as resources/donator/perks.tsv). A donator tier is not
 * a staff rank: it never grants a command or a power over other players.
 */
enum class DonatorTier(val dollars: Int, val title: String) {
    NONE(0, "None"),
    REGULAR(25, "Regular"),
    SUPER(50, "Super"),
    EXTREME(250, "Extreme"),
    LEGENDARY(1_000, "Legendary"),
    SUPREME(2_500, "Supreme"),
    UBER(5_000, "Uber"),
    ;

    fun atLeast(other: DonatorTier): Boolean = ordinal >= other.ordinal

    companion object {
        /** The highest tier whose threshold [cents] reaches. */
        fun forCents(cents: Long): DonatorTier = entries.last { cents >= it.dollars * CENTS }

        fun parse(name: String): DonatorTier? = entries.firstOrNull { it.name.equals(name, true) || it.title.equals(name, true) }

        const val CENTS: Long = 100
    }
}

/**
 * One row of the donator benefit table, by its exact text there. [value] reads the cell for a tier as a number in the
 * row's own unit: a percentage (20% -> 20), seconds (30s), minutes (50m, 35min, 01:50 -> 110), a count, 1 for a
 * tick, 0 for a cross or a missing cell, and N for a 1/N chance.
 */
enum class DonatorPerk(val label: String) {
    EXCLUSIVE_CHAT_CROWN("Exclusive chat-crown"),
    EXCLUSIVE_ZONE("Exclusive zone"),
    YELL_CHANNEL_TIMER("Yell-channel (::yell) timer"),
    ABILITY_TO_SET_A_CUSTOM_YELL_TAG("Ability to set a custom yell-tag"),
    REDUCED_COOLDOWN_ON_RESTORING_SPECIAL_ATTACK_AT_RESTORATION("Reduced cooldown on restoring special attack at Restoration pools"),
    CHANCE_OF_RECEIVING_AN_ADDITIONAL_TOKEN_WHEN_VOTING("Chance of receiving an additional Token when voting"),
    MARKET_TAX_CUT("Market tax cut"),
    EXTRA_TOWN_BOARD_SLOTS("Extra Town board slots"),
    BONUS_TOWN_BOARD_EXPERIENCE_POINTS("Bonus Town board experience & points"),
    EXTRA_BANK_SPACE("Extra Bank space"),
    EXTRA_PRESET_SLOTS("Extra Preset slots"),
    YOUR_THRALLS_LAST_LONGER("Your Thralls last longer"),
    EXTRA_PK_POINTS_WHEN_KILLING_OTHER_PLAYERS("Extra Pk points when killing other players"),
    EXTRA_POINTS_WHEN_SPOTTING_PENGUINS("Extra points when spotting Penguins"),
    EXTRA_POINTS_FROM_PEST_CONTROL("Extra points from Pest Control"),
    GET_YOUR_FREE_SLAYER_TASK_RESET_MORE_OFTEN("Get your free Slayer task reset more often"),
    FREE_CLUE_SCROLL_DAILY_SKIPS("Free clue scroll daily skips"),
    INCREASED_CLUE_SCROLL_REWARDS_DROP_RATE("Increased Clue scroll rewards drop rate"),
    CHANCE_OF_RECEIVING_EXTRA_REWARDS_WHEN_OPENING_THE_CRYSTAL_C("Chance of receiving extra rewards when opening the Crystal Chest"),
    YOU_HAVE_A_CHANCE_OF_KEEPING_YOUR_FROZEN_KEY_WHEN_ENTERING_N("You have a chance of keeping your Frozen key when entering Nex's lair"),
    GODWARS_DUNGEON_KILLCOUNT_REQUIREMENT_IS_LOWERED("Godwars dungeon killcount requirement is lowered"),
    DISCOUNT_WHEN_DECANTING_POTIONS("Discount when decanting potions"),
    RECEIVE_MORE_POINTS_BACK_WHEN_REVERTING_UPGRADED_ITEMS("Receive more points back when reverting upgraded items"),
    BONUS_EXP_FROM_TEARS_OF_GUTHIX("Bonus exp from Tears of Guthix"),
    BONUS_EXP_WHEN_EXCHANGING_AGILITY_TICKETS("Bonus exp when exchanging Agility tickets"),
    BONUS_EXP_FROM_EFFIGIES_LAMPS("Bonus exp from effigies/lamps"),
    BONUS_EXP_WHEN_DOING_DUNGEONEERING("Bonus exp when doing Dungeoneering"),
    RECEIVE_EXTRA_RUNES_WHEN_RUNECRAFTING("Receive extra runes when Runecrafting"),
    BIRD_HOUSES_CATCH_TIME("Bird Houses catch time"),
    INCREASED_CHANCE_OF_RECEIVING_A_MARK_OF_GRACE("Increased chance of receiving a Mark of grace"),
    DECREASED_CHANCE_OF_BURNING_FOOD("Decreased chance of burning food"),
    CHANCE_OF_RECEIVING_ADDITIONAL_SUMMONING_CHARMS("Chance of receiving additional Summoning charms (with RoW equipped)"),
    GAIN_IMMUNITY_TO_POISON_VENOM_AND_DRAGONFIRE_WHEN_NOT_IN_THE(
        "Gain immunity to poison, venom and dragonfire when not in the Wilderness",
    ),
    REGEN_1_PRAYER_POINT_EVERY_5_SECONDS_WHEN_NOT_IN_THE_WILDERN("Regen 1 prayer point every 5 seconds when not in the Wilderness"),
    GAIN_THE_ABILITY_TO_CLAIM_DAILY_REWARDS("Gain the ability to claim daily rewards"),
    CHANCE_TO_AUTOMATICALLY_TRIGGER_IMBUED_RING_OF_WEALTH_EFFECT(
        "Chance to automatically trigger imbued Ring of Wealth effect on monster drop",
    ),
    GAIN_EFFIGY_DROP_RATE_CHANCE("Gain Effigy drop rate chance"),
    GAIN_THE_ABILITY_TO_CREATE_LIZARD_SHAMAN_INSTANCES_WITHOUT_A("Gain the ability to create Lizard shaman instances without a tablet"),
    TOKKUL_MONSTER_DROPS_ARE_DOUBLED("Tokkul monster drops are doubled"),
    THIEVING_FROM_STALLS_IS_AUTOMATED("Thieving from stalls is automated"),
    GAIN_THE_ABILITY_TO_CONFIGURE_THE_LOOTING_BAG_TO_AUTO_DEPOSI("Gain the ability to configure the Looting bag to auto-deposit loot"),
    YOU_LOSE_LESS_POINTS_WHEN_DYING_IN_RAIDS("You lose less points when dying in Raids"),
    GAIN_A_CHANCE_OF_AUTOMATICALLY_NOTING_RESOURCES_WHEN_FISHING(
        "Gain a chance of automatically noting resources when Fishing, Woodcutting or Mining",
    ),
    RECEIVE_BONUS_STARDUST_FROM_SHOOTING_STARS("Receive bonus Stardust from Shooting stars"),
    GAIN_THE_ABILITY_TO_TELEPORT_DIRECTLY_TO_PENGUINS_USING_THE(
        "Gain the ability to teleport directly to penguins using the Penguin spy notebook",
    ),
    CHANCE_OF_CATCHING_AN_EXTRA_FISH_WHEN_FISHING("Chance of catching an extra fish when fishing"),
    CHANCE_OF_BURNING_AN_EXTRA_LOG_WHEN_FIREMAKING("Chance of burning an extra log when firemaking"),
    CHANCE_OF_RECEIVING_AN_EXTRA_COOKED_FOOD_WHEN_COOKING("Chance of receiving an extra cooked food when cooking"),
    CHANCE_OF_RECEIVING_AN_EXTRA_POTION_WHEN_MIXING_POTIONS("Chance of receiving an extra potion when mixing potions"),
    CHANCE_OF_RECEIVING_EXTRA_LOOT_FROM_HUNTER_TRAPS("Chance of receiving extra loot from hunter traps"),
    REMOVES_THE_REQUIREMENT_FOR_NEEDING_BAIT_WHEN_HUNTING_GRENWA("Removes the requirement for needing bait when hunting Grenwalls"),
    ABILITY_TO_SEE_WHEN_THE_NEXT_SHOOTING_STAR_WILL_CRASH("Ability to see when the next Shooting Star will crash (::nextstar)"),
    ABILITY_TO_TELEPORT_DIRECTLY_TO_CRASHED_SHOOTING_STARS("Ability to teleport directly to crashed Shooting Stars (::star)"),
    ABILITY_TO_TELEPORT_DIRECTLY_TO_ACTIVE_EVIL_TREES("Ability to teleport directly to active Evil Trees (::eviltree)"),
    ABILITY_TO_SEE_GRIFOLAROO_LOCATION_NEXT_SPAWN_TIME("Ability to see Grifolaroo location/next spawn time (::grif)"),
    ABILITY_TO_SEE_WILDY_WYRM_NEXT_SPAWN_TIME("Ability to see Wildy Wyrm next spawn time (::wyrm)"),
    RECEIVE_NOTED_BARS_WHEN_SMELTING_IN_THE_BLAST_FURNACE("Receive noted bars when smelting in the Blast Furnace"),
    BOB_OFFERS_A_DISCOUNT_WHEN_REPAIRING_YOUR_GEAR("Bob offers a discount when repairing your gear"),
    CHANCE_TO_RECEIVE_A_FULL_CRYSTAL_KEY_AS_A_DROP_FROM_MONSTERS("Chance to receive a full Crystal key as a drop from monsters"),
    EFFIGIES_AND_CRYSTAL_KEYS_MONSTER_DROPS_ARE_AUTOMATICALLY_SE(
        "Effigies and Crystal keys monster drops are automatically sent to your bank",
    ),
    IMMUNITY_TO_EVIL_TREE_ROOTS_SPAWNING_AND_INTERRUPTING_YOU("Immunity to Evil Tree roots spawning and interrupting you"),
    BURNT_PAGES_WILL_AUTOMATICALLY_BE_PLACED_IN_YOUR_INVENTORY_W(
        "Burnt pages will automatically be placed in your inventory when Firemaking",
    ),
    BIRD_NESTS_WILL_AUTOMATICALLY_BE_PLACED_INTO_YOUR_INVENTORY(
        "Bird nests' will automatically be placed into your inventory when Woodcutting",
    ),
    GAIN_ACCESS_TO_AN_EXCLUSIVE_GEM_MINE_LOCATED_BELOW_SHILO_VIL("Gain access to an exclusive Gem mine located below Shilo Village"),
    GAIN_THE_ABILITY_TO_BLACKLIST_AN_ADDITIONAL_PLAYER_IN_EDGEVI("Gain the ability to blacklist an additional player in Edgeville PvP"),
    GAIN_BONUS_EXPERIENCE_IN_THE_EXTREME_DONATOR_ZONE("Gain bonus experience in the Extreme donator zone"),
    DISCOUNT_WHEN_PURCHASING_FLASKS_FROM_THE_EXTREME_DONATOR_ZON("Discount when purchasing flasks from the Extreme donator zone merchant"),
    GAIN_BONUS_EXPERIENCE_IN_THE_UBER_DONATOR_ZONE("Gain bonus experience in the Uber donator zone"),
    SMITHING_SPEED_IS_INCREASED_IN_THE_UBER_DONATOR_ZONE("Smithing speed is increased in the Uber donator zone"),
    PETS_OBTAINED_OUTSIDE_THE_WILDERNESS_ARE_AUTOMATICALLY_INSUR("Pets obtained outside the Wilderness are automatically insured"),
    CHANCE_TO_RECEIVE_EXTRA_BIRDS_NEST_WHEN_EMPTYING_BIRD_HOUSES("Chance to receive extra Birds' nest when emptying Bird Houses"),
    GAIN_ACCESS_TO_THE_EXCLUSIVE_MINIGAME_RUNE_SUDOKU("Gain access to the exclusive minigame Rune Sudoku (in ezone)"),
    AUTO_BANK_CLUE_SCROLLS_BOTTLES_NESTS_GEODES("Auto-bank clue scrolls / bottles / nests / geodes"),
    EXTRA_SLAYER_MASTER_CAPE_DAILY_USES("Extra Slayer master cape daily uses"),
    INCREASES_THE_CHANCE_OF_RECEIVING_UPGRADE_POINT_TOKENS("Increases the chance of receiving Upgrade Point Tokens"),
    GLOBAL_DROP_RATE_BOOST("Global drop rate boost"),
    ;

    /** This perk's cell for [tier] as written on the page ("" for NONE). */
    fun raw(tier: DonatorTier): String = if (tier == DonatorTier.NONE) "" else TABLE[this]?.getOrNull(tier.ordinal - 1).orEmpty()

    fun value(tier: DonatorTier): Int = parse(raw(tier))

    companion object {
        private val TABLE: Map<DonatorPerk, List<String>> by lazy {
            val text =
                DonatorPerk::class.java
                    .getResourceAsStream("/donator/perks.tsv")
                    ?.bufferedReader()
                    ?.readText()
                    ?: error("donator/perks.tsv missing")
            val rows =
                text
                    .lines()
                    .drop(1)
                    .filter { it.isNotBlank() }
                    .map { it.split('\t') }
            val byLabel = rows.associate { it[0] to it.drop(1) }
            entries.associateWith { byLabel[it.label] ?: error("perks.tsv has no row '${it.label}'") }
        }

        private val CLOCK = Regex("(\\d+):(\\d+)")
        private val NUMBER = Regex("(\\d+(?:[.,]\\d+)?)")

        /** The number a cell holds in its unit (see the class KDoc). */
        fun parse(cell: String): Int {
            val c = cell.trim()
            return when {
                c.isEmpty() || c == "\u2717" -> {
                    0
                }

                c == "\u2713" -> {
                    1
                }

                c.startsWith("1/") -> {
                    c.removePrefix("1/").toInt()
                }

                else -> {
                    CLOCK.matchEntire(c)?.let { m -> return m.groupValues[1].toInt() * MINUTES_PER_HOUR + m.groupValues[2].toInt() }
                    val n =
                        NUMBER
                            .find(c)
                            ?.groupValues
                            ?.get(1)
                            ?.replace(",", "") ?: return 0
                    // Percentages with decimals ("0.5%") are kept in tenths of a percent by [tenths].
                    n.toDouble().toInt()
                }
            }
        }

        /** A cell in tenths of its unit ("0.5%" -> 5, "3.0%" -> 30): the market tax cut. */
        fun tenths(cell: String): Int =
            NUMBER
                .find(cell)
                ?.groupValues
                ?.get(1)
                ?.toDouble()
                ?.let { (it * TEN).toInt() } ?: 0

        private const val MINUTES_PER_HOUR = 60
        private const val TEN = 10
    }
}
