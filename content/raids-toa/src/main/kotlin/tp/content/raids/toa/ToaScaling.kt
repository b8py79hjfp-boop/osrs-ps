package tp.content.raids.toa

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.ScalingContext
import tp.plugin.NpcRef
import tp.plugin.SharedAttrs

/**
 * ToA scaling (toa.md TA28-TA33). HP, in this order: the raid level adds trunc(4 x RL / 10) percent (the Wardens' ejected
 * cores a quarter of that); the path level adds 3 + 5 x level percent to the path bosses; the party adds 90% of the base
 * for the 2nd and 3rd member and 60% for each later one; above 100 the result is rounded to the nearest 5 (up to 300) or
 * 10. Which NPCs scale and the operation order follow the wiki DPS calculator's ToA module (facts only). Defence and
 * accuracy rolls x (250 + RL) / 250, damage likewise up to +150% (TA31), set as per-mille attrs for the combat formulas;
 * the overlord scarabs' defence is not raid-scaled (25 Jun 2025).
 */
internal object ToaScaling {
    /** The run setting holding the raid level fixed at the start (ToaFlow.start). */
    const val LEVEL_SETTING = "toa_level"

    fun apply(ctx: ScalingContext, npc: NpcRef) {
        val run = npc[SharedAttrs.RAID_RUN]?.let { Raids.of(npc).run(it) }
        // The run's current level (OPEN-TA6: a missed time limit can lower it mid-raid), else the starting one.
        val level = run?.let { ToaFlow.state(it)?.raidLevel } ?: ctx.settings[LEVEL_SETTING] ?: 0
        val type = npc.type
        if (type in HP_SCALED) {
            val path = ToaNpcs.pathOf(type)?.let { p -> run?.let { ToaFlow.pathLevel(it, p) } } ?: 0
            val levels = IntArray(STATS) { npc.baseLevel(it) }
            val base = if (type in ToaNpcs.EJECTED_CORES) EJECTED_CORE_HP else levels[HITPOINTS]
            val hp =
                hitpoints(
                    base,
                    level,
                    if (type in
                        ToaNpcs.PATH_SCALED
                    ) {
                        path
                    } else {
                        0
                    },
                    partySize(ctx, run),
                    type in ToaNpcs.EJECTED_CORES,
                )
            levels[HITPOINTS] = hp
            npc.overrideStats(levels)
            npc.hitpoints = hp
        }
        // The fights read these (content/combat cannot see raids-toa): raid level, this NPC's path level, the invocations.
        npc[SharedAttrs.RAID_LEVEL] = level
        npc[SharedAttrs.RAID_PATH_LEVEL] = ToaNpcs.pathOf(type)?.let { p -> run?.let { ToaFlow.pathLevel(it, p) } }
            ?: run?.let { r -> ToaFlow.state(r)?.path?.let { ToaFlow.pathLevel(r, it) } } ?: 0
        run?.let { r -> ToaFlow.state(r)?.modifierNames?.let { npc[SharedAttrs.RAID_MODIFIERS] = it } }
        val permille = PER_MILLE + LEVEL_PERMILLE * level
        if (type !in ToaNpcs.OVERLORDS) npc[SharedAttrs.RAID_DEFENCE_PERMILLE] = permille
        npc[SharedAttrs.RAID_ACCURACY_PERMILLE] = permille
        npc[SharedAttrs.RAID_DAMAGE_PERMILLE] = minOf(permille, MAX_DAMAGE_PERMILLE)
    }

    /** TA30 / OPEN-TA11: the party size at the start ([ToaConfig.partySizeAtStart]) or the members still inside. */
    private fun partySize(ctx: ScalingContext, run: RaidRun?): Int =
        if (ToaConfig.partySizeAtStart || run == null) {
            ctx.partySize
        } else {
            run.members.values
                .count { it.inside }
                .coerceAtLeast(1)
        }

    /** TA32 (the DPS calculator's order and truncation). */
    fun hitpoints(base: Int, raidLevel: Int, pathLevel: Int, partySize: Int, core: Boolean): Int {
        var hp = base
        val levelPercent = (if (core) CORE_LEVEL_RATE else LEVEL_RATE) * raidLevel / LEVEL_DIVISOR
        hp += hp * levelPercent / PERCENT
        val path = pathLevel.coerceIn(0, MAX_PATH_LEVEL)
        if (path >= 1) hp = hp * (PERCENT + PATH_BASE + PATH_STEP * path) / PERCENT
        val n = partySize.coerceIn(1, ToaRaid.MAX_PARTY)
        if (n >= 2) {
            var factor = PARTY_EARLY * if (n >= 3) 2 else 1
            if (n >= PARTY_LATE_FROM) factor += PARTY_LATE * (n - (PARTY_LATE_FROM - 1))
            hp = hp * (PARTY_DIVISOR + factor) / PARTY_DIVISOR
        }
        if (hp > ROUND_FROM) {
            val step = if (hp > ROUND_TENS_FROM) ROUND_TENS else ROUND_FIVES
            hp = (hp + step / 2) / step * step
        }
        return hp
    }

    private val HP_SCALED = ToaNpcs.PATH_SCALED + ToaNpcs.OBELISKS + ToaNpcs.P2_WARDENS + ToaNpcs.EJECTED_CORES + ToaNpcs.P3_WARDENS

    private const val STATS = 6
    private const val HITPOINTS = 3
    private const val PERCENT = 100
    private const val PER_MILLE = 1000

    /** TA31: +2% per 5 levels = 4 per mille per level; damage capped at +150%. */
    private const val LEVEL_PERMILLE = 4
    private const val MAX_DAMAGE_PERMILLE = 2500

    /** TA31: trunc(4 x RL / 10) percent; the cores trunc(RL / 10). */
    private const val LEVEL_RATE = 4
    private const val CORE_LEVEL_RATE = 1
    private const val LEVEL_DIVISOR = 10

    /** TA28: 3 + 5 x level percent, levels 1-6. */
    private const val PATH_BASE = 3
    private const val PATH_STEP = 5
    private const val MAX_PATH_LEVEL = 6

    /** TA30: tenths added by the 2nd / 3rd member (9 each) and each from the 4th (6). */
    private const val PARTY_EARLY = 9
    private const val PARTY_LATE = 6
    private const val PARTY_LATE_FROM = 4
    private const val PARTY_DIVISOR = 10

    /** TA32 rounding. */
    private const val ROUND_FROM = 100
    private const val ROUND_TENS_FROM = 300
    private const val ROUND_FIVES = 5
    private const val ROUND_TENS = 10

    /** TA32: the ejected cores' fixed base HP (DPS calculator). */
    private const val EJECTED_CORE_HP = 4500
}
