@file:Suppress("MagicNumber")
// data tables: Wiki "Giants' Foundry" and its subpages, client scripts 6115-6121 (docs/mechanics/giants-foundry.md)

package tp.content.skills

import tp.symbols.Objs

/**
 * One purchasable or default mould piece (Wiki "Giants' Foundry/Mould List"): [type] 0 forte, 1 blade, 2 tip; [level] to use it;
 * [cost] in foundry reputation (0 for the defaults); [scores] by descriptor in the order of [GiantsFoundryData.Word].
 */
internal class Mould(val name: String, val type: Int, val level: Int, val cost: Int, val scores: IntArray) {
    fun score(first: Int, second: Int): Int = scores[first] + scores[second]
}

internal object GiantsFoundryData {
    /** The six commission descriptors, in the column order of the Wiki's mould list after Broad/Narrow: index = [Mould.scores] slot. */
    const val BROAD = 0
    const val NARROW = 1
    const val LIGHT = 2
    const val HEAVY = 3
    const val FLAT = 4
    const val SPIKED = 5
    val WORD_NAMES = arrayOf("Broad", "Narrow", "Light", "Heavy", "Flat", "Spiked")

    /**
     * Wiki "Giants' Foundry" > Preform creation: the first descriptor is Broad, Heavy, Light or Narrow; the second Flat, Heavy,
     * Light or Spiked.
     */
    val FIRST_WORDS = intArrayOf(BROAD, HEAVY, LIGHT, NARROW)
    val SECOND_WORDS = intArrayOf(FLAT, HEAVY, LIGHT, SPIKED)

    const val FORTE = 0
    const val BLADE = 1
    const val TIP = 2
    val TYPE_NAMES = arrayOf("Forte", "Blade", "Tip")

    private fun m(name: String, type: Int, level: Int, cost: Int, narrow: Int, broad: Int, light: Int, heavy: Int, flat: Int, spiked: Int) =
        Mould(name, type, level, cost, intArrayOf(broad, narrow, light, heavy, flat, spiked))

    /**
     * Every mould in the order of the Wiki's mould list: six defaults (level 15) then the five purchasable ones by level, per type. The
     * 1-based position within a type is the value of `GIANTS_FOUNDRY_MOULD_SELECTED_*` and the number in
     * `GIANTS_FOUNDRY_UNLOCKED_MOULD_*_7..11`
     * (OPEN-GF9 for the default order).
     */
    val MOULDS: List<List<Mould>> =
        listOf(
            listOf(
                m("Serrated Forte", FORTE, 15, 0, 8, 0, 0, 8, 0, 6),
                m("Serpent Ricasso", FORTE, 15, 0, 6, 0, 8, 0, 8, 0),
                m("Medusa Ricasso", FORTE, 15, 0, 0, 8, 0, 6, 8, 0),
                m("Disarming Forte", FORTE, 15, 0, 4, 0, 4, 0, 0, 4),
                m("Gladius Ricasso", FORTE, 15, 0, 0, 4, 0, 4, 4, 0),
                m("Chopper Forte", FORTE, 15, 0, 0, 4, 4, 0, 4, 0),
                m("Stiletto Forte", FORTE, 50, 300, 8, 0, 10, 0, 8, 0),
                m("Defender Base", FORTE, 51, 300, 0, 8, 0, 10, 8, 0),
                m("Juggernaut Forte", FORTE, 61, 350, 0, 4, 0, 4, 0, 16),
                m("Chopper Forte +1", FORTE, 70, 400, 0, 3, 4, 0, 18, 0),
                m("Spiker!", FORTE, 79, 450, 1, 0, 0, 2, 0, 22),
            ),
            listOf(
                m("Gladius Edge", BLADE, 15, 0, 6, 0, 0, 8, 8, 0),
                m("Stiletto Blade", BLADE, 15, 0, 8, 0, 6, 0, 8, 0),
                m("Medusa Blade", BLADE, 15, 0, 0, 8, 0, 8, 6, 0),
                m("Fish Blade", BLADE, 15, 0, 4, 0, 4, 0, 4, 0),
                m("Defenders Edge", BLADE, 15, 0, 0, 4, 0, 4, 0, 4),
                m("Saw Blade", BLADE, 15, 0, 0, 4, 4, 0, 0, 4),
                m("Flamberge Blade", BLADE, 48, 300, 8, 0, 8, 0, 0, 10),
                m("Serpent Blade", BLADE, 50, 300, 10, 0, 8, 0, 8, 0),
                m("Claymore Blade", BLADE, 59, 350, 0, 16, 0, 4, 4, 0),
                m("Fleur de Blade", BLADE, 71, 400, 0, 4, 0, 18, 0, 1),
                m("Choppa!", BLADE, 80, 450, 0, 1, 22, 0, 2, 0),
            ),
            listOf(
                m("Saw Tip", TIP, 15, 0, 0, 6, 0, 8, 0, 8),
                m("Gladius Point", TIP, 15, 0, 8, 0, 0, 8, 6, 0),
                m("Serpent's Fang", TIP, 15, 0, 8, 0, 6, 0, 0, 8),
                m("Medusa's Head", TIP, 15, 0, 0, 4, 0, 4, 0, 4),
                m("Chopper Tip", TIP, 15, 0, 0, 4, 4, 0, 0, 4),
                m("People Poker Point", TIP, 15, 0, 4, 0, 0, 4, 4, 0),
                m("Corrupted Point", TIP, 49, 300, 8, 0, 10, 0, 0, 8),
                m("Defenders Tip", TIP, 52, 300, 0, 10, 0, 8, 0, 8),
                m("Serrated Tip", TIP, 60, 350, 4, 0, 16, 0, 0, 4),
                m("Needle Point", TIP, 69, 400, 18, 0, 3, 0, 4, 0),
                m("The Point!", TIP, 81, 450, 0, 2, 1, 0, 22, 0),
            ),
        )

    /** The number of moulds per type that need no unlock (Wiki "Giants' Foundry" > Default moulds). */
    const val DEFAULT_MOULDS = 6

    // ---- metal

    /** Metals in tier order 1..6 (Wiki "Giants' Foundry" > Metal score): bronze, iron, steel, mithril, adamant, rune. */
    val METAL_NAMES = arrayOf("Bronze", "Iron", "Steel", "Mithril", "Adamant", "Rune")
    val BARS = intArrayOf(Objs.BRONZE_BAR, Objs.IRON_BAR, Objs.STEEL_BAR, Objs.MITHRIL_BAR, Objs.ADAMANTITE_BAR, Objs.RUNITE_BAR)

    /** Smithing level to add a metal (Wiki "Giants' Foundry" > Metal score table; OPEN-GF3 for bronze, which the table lists at 15). */
    val METAL_LEVELS = intArrayOf(15, 15, 30, 50, 70, 85)

    /** The crucible holds 28 bars' worth (Wiki "Crucible"). */
    const val CRUCIBLE_CAPACITY = 28

    /** Cache names of the equipment accepted by the crucible and the bars it is worth (Wiki "Giants' Foundry" > Preform creation table). */
    val EQUIPMENT_VALUES: Map<String, Int> =
        mapOf(
            "scimitar" to 1,
            "longsword" to 1,
            "full helm" to 1,
            "sq shield" to 1,
            "claws" to 1,
            "warhammer" to 2,
            "battleaxe" to 2,
            "chainbody" to 2,
            "kiteshield" to 2,
            "2h sword" to 2,
            "platelegs" to 2,
            "plateskirt" to 2,
            "platebody" to 4,
        )

    /**
     * Metal score of a crucible holding [counts] bars per metal (Wiki "Giants' Foundry" > Metal score): only the two most abundant metals
     * count (ties prefer the better metal), V = 10 x tier x amount / 28 and the score is floor((floor(10 V1) + floor(10 V2) +
     * floor(V1 V2)) / 10).
     */
    fun metalScore(counts: IntArray): Int {
        val order = counts.indices.filter { counts[it] > 0 }.sortedWith(compareByDescending<Int> { counts[it] }.thenByDescending { it })
        if (order.isEmpty()) return 0
        val v1 = value(order[0], counts)
        val v2 = if (order.size > 1) value(order[1], counts) else 0.0
        return ((Math.floor(10 * v1) + Math.floor(10 * v2) + Math.floor(v1 * v2)) / 10).toInt()
    }

    private fun value(metal: Int, counts: IntArray): Double = 10.0 * (metal + 1) * counts[metal] / CRUCIBLE_CAPACITY

    /**
     * Sections of a sword by metal score (Wiki "Giants' Foundry" > Difficulty, and client script 6119): 3 below 20, then 4, 5,
     * 6, 7 from 20 / 60 / 90 / 120.
     */
    fun sections(metalScore: Int): Int =
        when {
            metalScore < 20 -> 3
            metalScore < 60 -> 4
            metalScore < 90 -> 5
            metalScore < 120 -> 6
            else -> 7
        }

    /** Sweet spots per sword: three for 6 and 7 sections, otherwise two (Wiki "Giants' Foundry" > Sweet Spot). */
    fun sweetSpots(sections: Int): Int = if (sections >= 6) 3 else 2

    // ---- temperature and progress (client script 6121 / 6115: both gauges run 0..1000)

    const val SCALE = 1000

    /**
     * Best metal score; the temperature bars shrink linearly to half size here (Wiki "Giants' Foundry" > Difficulty; script
     * 6121 divides by 130).
     */
    const val MAX_METAL_SCORE = 130

    /**
     * Tools: the trip hammer (hot, red), the grindstone (medium, orange), the polishing wheel (cold, green) - script 6121's
     * argument and the seed values.
     */
    const val HAMMER = 0
    const val GRINDSTONE = 1
    const val POLISH = 2

    /**
     * Range centres of the three tools on the temperature gauge: 832, 499 and 166 (script 6121: 1000/3 x 2.5, x 1.5, x 0.5,
     * rounded down).
     */
    val TOOL_CENTRE = intArrayOf(832, 499, 166)

    /** Width of a tool's temperature range at [metalScore]: 333 - score x 167 / 130 (script 6121, integer arithmetic). */
    fun rangeWidth(metalScore: Int): Int = 333 - metalScore * 167 / MAX_METAL_SCORE

    /** Whether [temperature] is inside the range of [tool] at [metalScore]; both ends count, as the HUD highlights it (script 6115). */
    fun inRange(tool: Int, temperature: Int, metalScore: Int): Boolean {
        val width = rangeWidth(metalScore)
        val start = TOOL_CENTRE[tool] - width / 2
        return temperature in start..(start + width)
    }

    /**
     * Machine timing and effect per tool in permille of the gauges (Wiki "Giants' Foundry" > Refinement): period in ticks,
     * progress, temperature change.
     */
    val PERIOD = intArrayOf(5, 2, 2)
    val PROGRESS = intArrayOf(20, 10, 10)
    val TEMPERATURE = intArrayOf(-25, 15, -17)

    /**
     * Smithing xp per successful machine action: trip hammer 4.5, grindstone 3.2, polishing wheel 3.3 (Wiki "Giants' Foundry" >
     * Experience).
     */
    val ACTION_XP = doubleArrayOf(4.5, 3.2, 3.3)

    // ---- quality, rewards

    /**
     * Quality lost for a wrong tool or a tool out of range, and regained by a sweet spot (Wiki "Giants' Foundry" > Sword
     * quality; OPEN-GF4 on the 5 per mistake line).
     */
    const val MISTAKE_PENALTY = 10

    /** A sweet spot window stays open for 10 ticks (Wiki "Giants' Foundry" > Sweet Spot). */
    const val SWEET_WINDOW_TICKS = 10

    /** Smithing xp for a sword of quality [q]: (floor(q^2 / 73) + floor(1.5 q) + 1) x 30 (Wiki "Giants' Foundry" > Experience). */
    fun handInXp(q: Int): Int = (q * q / 73 + (q * 3 / 2) + 1) * 30

    /**
     * Coins for a sword: twice the experience (Wiki "Giants' Foundry" > Rewards); a ruined sword is worth 30 xp and 60 coins
     * (Wiki > Sword quality).
     */
    fun handInCoins(q: Int): Int = handInXp(q) * 2

    /** Hand-in restores half the run energy bar (Wiki "Giants' Foundry" > Handing in); run energy is in hundredths of a percent. */
    const val RUN_RESTORE = 5000

    // ---- reward shop (Wiki "Giants' Foundry Reward Shop")

    /**
     * A shop line: [obj] for [cost] reputation; [refund] whether Kovac buys it back at 80%; [stack] whether more than one may
     * be asked for.
     */
    class Reward(val obj: Int, val cost: Int, val refund: Boolean, val stack: Boolean)

    /** Wiki "Giants' Foundry Reward Shop" > Stock. The Dwarf Cannon quest gate of the double ammo mould is OPEN-GF11. */
    val REWARDS: List<Reward> =
        listOf(
            Reward(Objs.DOUBLE_AMMO_MOULD, 2000, true, false),
            Reward(Objs.KOVACS_GROG, 300, false, true),
            Reward(Objs.SMITHING_CATALYST, 15, true, true),
            Reward(Objs.GIANTS_FOUNDRY_ORE_PACK, 200, true, false),
            Reward(Objs.SMITHING_UNIFORM_TORSO, 4000, true, false),
            Reward(Objs.SMITHING_UNIFORM_LEGS, 4000, true, false),
            Reward(Objs.SMITHING_UNIFORM_BOOTS, 3500, true, false),
            Reward(Objs.SMITHING_UNIFORM_GLOVES, 3500, true, false),
            Reward(Objs.GIANTS_FOUNDRY_COLOSSAL_BLADE, 5000, false, false),
        )

    /** Wiki "Giants' Foundry Reward Shop": items other than the grog and the colossal blade sell back at 80% (percent). */
    const val REFUND_PERCENT = 80

    /**
     * Smiths' uniform pieces: each worn piece gives a 20% chance of bonus progress on a machine tick, the full set 100% (Wiki
     * "Giants' Foundry/Strategies").
     */
    val UNIFORM = intArrayOf(Objs.SMITHING_UNIFORM_TORSO, Objs.SMITHING_UNIFORM_LEGS, Objs.SMITHING_UNIFORM_BOOTS)
    val UNIFORM_GLOVES = intArrayOf(Objs.SMITHING_UNIFORM_GLOVES, Objs.SMITHING_UNIFORM_GLOVES_ICE)
    const val UNIFORM_PIECE_PERCENT = 20

    // ---- OPEN: values the Wiki does not give (docs/mechanics/giants-foundry.md)

    /** OPEN-GF5: the preform's temperature when picked up (the middle of the trip hammer's range, which is always the first tool). */
    const val START_TEMPERATURE = 832

    /** OPEN-GF6: natural cooling per tick away from the lava (permille). */
    const val PASSIVE_COOLING = 2

    /**
     * OPEN-GF6: lava / waterfall rate in permille per tick: [slow] and [fast] start values, the gain per tick of staying, and
     * the 10% per tick ceiling the Wiki names.
     */
    const val SLOW_START = 6
    const val FAST_START = 24
    const val RAMP_GAIN = 3
    const val RAMP_CAP = 100

    /** OPEN-GF7: a sweet spot multiplies the next machine tick's progress by this much. */
    const val SWEET_PROGRESS_MULTIPLIER = 4

    /** OPEN-GF8: bonus progress of a Smiths' uniform proc, in permille. */
    const val UNIFORM_BONUS_PROGRESS = 5

    /** OPEN-GF10: ticks the poured metal takes to cool before the preform can be picked up. */
    const val COOLING_TICKS = 8
}
