package tp.plugin

/** Client stat ids (cache `stat` config order, RuneLite `Skill`) and their display names. */
public object Stats {
    public const val ATTACK: Int = 0
    public const val DEFENCE: Int = 1
    public const val STRENGTH: Int = 2
    public const val HITPOINTS: Int = 3
    public const val RANGED: Int = 4
    public const val PRAYER: Int = 5
    public const val MAGIC: Int = 6
    public const val COOKING: Int = 7
    public const val WOODCUTTING: Int = 8
    public const val FLETCHING: Int = 9
    public const val FISHING: Int = 10
    public const val FIREMAKING: Int = 11
    public const val CRAFTING: Int = 12
    public const val SMITHING: Int = 13
    public const val MINING: Int = 14
    public const val HERBLORE: Int = 15
    public const val AGILITY: Int = 16
    public const val THIEVING: Int = 17
    public const val SLAYER: Int = 18
    public const val FARMING: Int = 19
    public const val RUNECRAFT: Int = 20
    public const val HUNTER: Int = 21
    public const val CONSTRUCTION: Int = 22
    public const val SAILING: Int = 23

    /** Highest base level of every stat (OSRS Wiki "Skills": levels range up to 99). */
    public const val MAX_LEVEL: Int = 99

    private val NAMES =
        arrayOf(
            "Attack",
            "Defence",
            "Strength",
            "Hitpoints",
            "Ranged",
            "Prayer",
            "Magic",
            "Cooking",
            "Woodcutting",
            "Fletching",
            "Fishing",
            "Firemaking",
            "Crafting",
            "Smithing",
            "Mining",
            "Herblore",
            "Agility",
            "Thieving",
            "Slayer",
            "Farming",
            "Runecraft",
            "Hunter",
            "Construction",
            "Sailing",
        )

    /** Number of stats. */
    public val COUNT: Int get() = NAMES.size

    /** Display name of [stat] (the skills tab's tooltip). */
    public fun name(stat: Int): String = NAMES[stat]
}

/** [player]'s base level of [stat] rose from [from] to [to] (published by [PlayerRef.addXp]). */
public class StatAdvanceEvent(
    public val player: PlayerRef,
    public val stat: Int,
    public val from: Int,
    public val to: Int,
) : Event
