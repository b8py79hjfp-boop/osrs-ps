package tp.content.raids.cox

import tp.content.raids.ScalingContext
import tp.plugin.NpcRef
import tp.plugin.Stats
import tp.symbols.Npcs

/**
 * CoX NPC scaling (cox.md CX17-CX19, the formula in CX17a): every raid NPC is scaled by the party size at the start and
 * the party's highest combat level and Hitpoints level, and Challenge Mode adds 50% (Tekton's defence 20% / 35%, the
 * glowing crystal nothing). Base stats are the wiki infobox values for a maxed solo player, which the formula leaves
 * unchanged at combat 126 / Hitpoints 99 (the cache holds 1 in every stat).
 *
 * Source: the OSRS Wiki DPS calculator (weirdgloop/osrs-dps-calc @ 701b2da, `src/lib/scaling/ChambersOfXeric.ts`, GPL-3.0;
 * facts only, no code copied) and its monster data (wiki infoboxes).
 */
internal object CoxScaling {
    private val TEKTON_RANGE = Npcs.RAIDS_TEKTON_WAITING..Npcs.RAIDS_TEKTON_HAMMERING
    private val VANGUARD_IDS = 7525..7529
    private val VESPULA_IDS = 7530..7532
    private const val PORTAL_ID = 7533
    private val SOLDIER_IDS = listOf(7538, 7539)
    private val SCAVENGER_IDS = listOf(Npcs.RAIDS_SCAVENGER_BEAST_A, Npcs.RAIDS_SCAVENGER_BEAST_B)
    private const val RANGER_ID = 7559
    private const val MAGE_ID = 7560
    private const val MUTTADILE_LARGE_ID = 7561
    private const val MUTTADILE_SMALL_ID = 7562
    private const val MUTTADILE_LARGE_HUNGRY_ID = 7563
    private val VASA_IDS = listOf(7565, 7566, 7567)
    private const val CRYSTAL_ID = 7568
    private val GUARDIAN_IDS = setOf(7569, 7570, 7571, 7572)
    private val SHAMAN_IDS = listOf(7573, 7574)
    private val ICE_DEMON_IDS = listOf(7584, 7585)
    private val MYSTIC_IDS = 7604..7606
    private val OLM_HEAD_IDS = setOf(7551, 7554)
    private val OLM_MAGE_HAND_IDS = setOf(7550, 7553)
    private val OLM_HAND_IDS = OLM_MAGE_HAND_IDS + setOf(7552, 7555)

    /** Base levels per NPC family: attack, strength, defence, magic, ranged, hitpoints. */
    private class Base(
        val atk: Int,
        val str: Int,
        val def: Int,
        val magic: Int,
        val ranged: Int,
        val hp: Int,
        val magicDefensive: Boolean = false,
    )

    private val TEKTON = Base(390, 390, 205, 205, 1, 300, magicDefensive = true)
    private val VANGUARD = Base(150, 150, 160, 150, 150, 180)
    private val VESPULA = Base(150, 150, 88, 88, 150, 200, magicDefensive = true)
    private val PORTAL = Base(1, 1, 176, 176, 1, 250, magicDefensive = true)
    private val SOLDIER = Base(150, 150, 40, 40, 1, 100, magicDefensive = true)
    private val SCAVENGER = Base(120, 120, 45, 1, 1, 30)
    private val RANGER = Base(1, 1, 155, 155, 210, 120, magicDefensive = true)
    private val MAGE = Base(1, 1, 155, 210, 1, 120)
    private val MUTTADILE_LARGE = Base(250, 250, 220, 250, 250, 250)
    private val MUTTADILE_SMALL = Base(150, 150, 138, 1, 150, 250)
    private val VASA = Base(1, 1, 175, 230, 230, 300)
    private val CRYSTAL = Base(1, 1, 100, 100, 1, 120)
    private val GUARDIAN = Base(140, 140, 100, 1, 1, GUARDIAN_HP_BASE)
    private val SHAMAN = Base(130, 130, 210, 130, 130, 190)
    private val ICE_DEMON = Base(1, 1, 160, 390, 390, 140)
    private val MYSTIC = Base(140, 140, 187, 140, 1, 160)
    private val OLM_HEAD = Base(250, 250, 150, 250, 250, OLM_HEAD_HP)
    private val OLM_HAND = Base(250, 250, 175, 175, 250, OLM_HAND_HP, magicDefensive = true)

    private val BASES: Map<Int, Base> =
        buildMap {
            for (id in Npcs.RAIDS_TEKTON_WAITING..Npcs.RAIDS_TEKTON_HAMMERING) put(id, TEKTON)
            for (id in VANGUARD_IDS) put(id, VANGUARD)
            for (id in VESPULA_IDS) put(id, VESPULA)
            put(PORTAL_ID, PORTAL)
            for (id in SOLDIER_IDS) put(id, SOLDIER)
            for (id in SCAVENGER_IDS) put(id, SCAVENGER)
            put(RANGER_ID, RANGER)
            put(MAGE_ID, MAGE)
            put(MUTTADILE_LARGE_ID, MUTTADILE_LARGE)
            put(MUTTADILE_LARGE_HUNGRY_ID, MUTTADILE_LARGE)
            put(MUTTADILE_SMALL_ID, MUTTADILE_SMALL)
            for (id in VASA_IDS) put(id, VASA)
            put(CRYSTAL_ID, CRYSTAL)
            for (id in GUARDIAN_IDS) put(id, GUARDIAN)
            for (id in SHAMAN_IDS) put(id, SHAMAN)
            for (id in ICE_DEMON_IDS) put(id, ICE_DEMON)
            for (id in MYSTIC_IDS) put(id, MYSTIC)
            for (id in OLM_HEAD_IDS) put(id, OLM_HEAD)
            for (id in OLM_HAND_IDS) put(id, OLM_HAND)
        }

    /** "Singles" scaling (things fought alone): scavenger beasts and vespine soldiers. */
    private val SINGLES = SCAVENGER_IDS.toSet() + SOLDIER_IDS

    fun apply(ctx: ScalingContext, npc: NpcRef) {
        val base = BASES[npc.type] ?: return
        val cm = ctx.mode == CoxRaid.CHALLENGE
        val levels = if (npc.type in SINGLES) singles(ctx, base, cm) else multi(ctx, npc.type, base, cm)
        npc.overrideStats(levels)
        npc.hitpoints = levels[NPC_HP]
    }

    /** NPC level order (plugin-api `overrideStats`): attack, defence, strength, hitpoints, ranged, magic. */
    private fun levels(base: Base, off: (Int) -> Int, def: (Int) -> Int, hp: Int): IntArray {
        fun o(v: Int) = if (v == 1) 1 else off(v)

        fun d(v: Int) = if (v == 1) 1 else def(v)
        val magic = if (base.magicDefensive) d(base.magic) else o(base.magic)
        return intArrayOf(o(base.atk), d(base.def), o(base.str), hp, o(base.ranged), magic)
    }

    private fun singles(ctx: ScalingContext, base: Base, cm: Boolean): IntArray {
        var hpScaler = ctx.highestCombatLevel.coerceIn(MIN_COMBAT, MAX_COMBAT)
        var statScaler = highestHitpoints(ctx).coerceIn(MIN_HP_LEVEL, Stats.MAX_LEVEL)
        if (cm) {
            statScaler = addPercent(statScaler, CM_PERCENT)
            hpScaler = addPercent(hpScaler, CM_PERCENT)
        }
        val hp = maxOf(base.hp * hpScaler / MAX_COMBAT, MIN_SINGLES_HP)
        val scale = { v: Int -> maxOf(v * statScaler / Stats.MAX_LEVEL, 1) }
        return levels(base, scale, scale, hp)
    }

    private fun multi(ctx: ScalingContext, type: Int, base: Base, cm: Boolean): IntArray {
        val n = ctx.scaledSize.coerceIn(1, CoxRaid.MAX_PARTY)
        val k = n - 1
        val combat = ctx.highestCombatLevel.coerceIn(MIN_COMBAT, MAX_COMBAT)
        val hpLevel = (MIN_HP_LEVEL + HP_SPREAD * highestHitpoints(ctx) / Stats.MAX_LEVEL).coerceIn(MIN_HP_LEVEL, Stats.MAX_LEVEL)
        val offPct = PERCENT + isqrt(k) * OFF_ROOT + k
        val defPct = PERCENT + isqrt(k) + k * DEF_TENTHS / TENTHS
        val crystal = type == CRYSTAL_ID
        val off = { v: Int ->
            var o = v * hpLevel / Stats.MAX_LEVEL * offPct / PERCENT
            if (cm) o = addPercent(o, CM_PERCENT)
            o.coerceIn(MIN_STAT, MAX_OFFENCE)
        }
        val def = { v: Int ->
            var d = v * hpLevel / Stats.MAX_LEVEL * defPct / PERCENT
            if (cm && !crystal) d = addPercent(d, if (type in TEKTON_RANGE) tektonCm(n) else CM_PERCENT)
            d.coerceIn(MIN_STAT, MAX_DEFENCE)
        }
        val hpBase =
            if (type in
                GUARDIAN_IDS
            ) {
                GUARDIAN_HP_BASE + ctx.skillSums.getOrElse(Stats.MINING) { 0 } / maxOf(1, ctx.partySize)
            } else {
                base.hp
            }
        var hp = hpBase * combat / MAX_COMBAT
        hp += hp * (n * HALF / PERCENT)
        if (cm && !crystal) hp = addPercent(hp, CM_PERCENT)
        hp = hp.coerceIn(MIN_HP, MAX_HP)
        if (type in OLM_HEAD_IDS || type in OLM_HAND_IDS) hp = olmHp(n, type in OLM_HAND_IDS)
        val out = levels(base, off, def, hp)
        // The mage (right) claw's magic level is halved after scaling.
        if (type in OLM_MAGE_HAND_IDS) out[NPC_MAGIC] = out[NPC_MAGIC] / 2
        return out
    }

    /** The Olm's parts take their HP from the party size alone: 600 / 800 plus 300 / 400 per extra player, less per extra phase. */
    private fun olmHp(n: Int, hand: Boolean): Int {
        val factor = minOf(n - 1, OLM_SIZE_CAP) - OLM_PHASE_WEIGHT * (minOf(n, OLM_SIZE_CAP) / OLM_PHASE_SIZE)
        return if (hand) OLM_HAND_HP + OLM_HAND_STEP * factor else OLM_HEAD_HP + OLM_HEAD_STEP * factor
    }

    /** Tekton's CM defence is 20% for parties under four, 35% otherwise (tekton.md, 24 May 2018). */
    private fun tektonCm(n: Int): Int = if (n < TEKTON_SMALL_PARTY) TEKTON_CM_SMALL else TEKTON_CM_LARGE

    private fun highestHitpoints(ctx: ScalingContext): Int = ctx.highestLevels.getOrElse(Stats.HITPOINTS) { Stats.MAX_LEVEL }

    private fun addPercent(x: Int, pct: Int): Int = x + x * pct / PERCENT

    private fun isqrt(x: Int): Int = kotlin.math.sqrt(x.toDouble()).toInt()

    private const val NPC_HP = 3
    private const val NPC_MAGIC = 5
    private const val PERCENT = 100
    private const val HALF = 50
    private const val CM_PERCENT = 50
    private const val MIN_COMBAT = 60
    private const val MAX_COMBAT = 126
    private const val MIN_HP_LEVEL = 55
    private const val HP_SPREAD = 44
    private const val OFF_ROOT = 7
    private const val DEF_TENTHS = 7
    private const val TENTHS = 10
    private const val MIN_STAT = 50
    private const val MAX_OFFENCE = 5000
    private const val MAX_DEFENCE = 20_000
    private const val MIN_HP = 50
    private const val MAX_HP = 30_000
    private const val MIN_SINGLES_HP = 5
    private const val TEKTON_SMALL_PARTY = 4
    private const val TEKTON_CM_SMALL = 20
    private const val TEKTON_CM_LARGE = 35
    private const val GUARDIAN_HP_BASE = 151
    private const val OLM_HEAD_HP = 800
    private const val OLM_HAND_HP = 600
    private const val OLM_HEAD_STEP = 400
    private const val OLM_HAND_STEP = 300
    private const val OLM_SIZE_CAP = 50
    private const val OLM_PHASE_WEIGHT = 3
    private const val OLM_PHASE_SIZE = 8
}
