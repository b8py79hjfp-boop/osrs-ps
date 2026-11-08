package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.plugin.AttrKey
import tp.plugin.ConsumeRequest
import tp.plugin.Consumed
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/** The raid potions (resources.md HL1): a kind, whose dose items are 12 consecutive ids (weak 1-4, standard 1-4, strong 1-4). */
internal enum class CoxPotion(val first: Int, val title: String) {
    ELDER(Objs.RAIDS_VIAL_ELDER_WEAK_1, "elder potion"),
    TWISTED(Objs.RAIDS_VIAL_TWISTED_WEAK_1, "twisted potion"),
    KODAI(Objs.RAIDS_VIAL_KODAI_WEAK_1, "kodai potion"),
    REVITALISATION(Objs.RAIDS_VIAL_REVITALISATION_WEAK_1, "revitalisation potion"),
    PRAYER(Objs.RAIDS_VIAL_PRAYER_WEAK_1, "prayer enhance"),
    XERICAID(Objs.RAIDS_VIAL_XERICAID_WEAK_1, "Xeric's aid"),
    OVERLOAD(Objs.RAIDS_VIAL_OVERLOAD_WEAK_1, "overload"),

    /** OPEN-RS1 is settled by the generated symbols: `RAIDS_VIAL_ANTIPOISON_*` 25754-25765. */
    ANTIPOISON(Objs.RAIDS_VIAL_ANTIPOISON_WEAK_1, "antipoison"),
    ;

    fun item(tier: Int, dose: Int): Int = first + tier * DOSES + dose - 1

    companion object {
        const val DOSES = 4
        const val TIERS = 3
    }
}

/**
 * Herblore inside the Chambers (resources.md HL1-HL10): making the weak, standard and strong potions from a clean herb, a
 * secondary and a water-filled gourd vial, drinking them (their effects), and the maker's points. The number the doc
 * leaves out is a named constant marked `OPEN-RS<n>`.
 */
internal object CoxHerblore {
    /** Potion item -> (kind, tier, dose). */
    private val DECODE: Map<Int, Triple<CoxPotion, Int, Int>> =
        CoxPotion.entries
            .flatMap { k ->
                (0 until CoxPotion.TIERS).flatMap { t -> (1..CoxPotion.DOSES).map { d -> k.item(t, d) to Triple(k, t, d) } }
            }.toMap()

    /** Every potion item (the raid-only list, CX35). */
    val ITEMS: Set<Int> = DECODE.keys

    /** One recipe: [herb] + [second] on a water vial; the levels and XP are those of the herb's family (HL1 table). */
    private class Recipe(val kind: CoxPotion, val herb: Int, val second: Int, val levels: IntArray, val xp: DoubleArray)

    /** HL1: golpar potions 47 / 59 / 70, buchu potions 52 / 65 / 78, antipoison 60 / 75 / 90. */
    private val GOLPAR_LEVELS = intArrayOf(47, 59, 70)
    private val BUCHU_LEVELS = intArrayOf(52, 65, 78)
    private val NOXIFER_LEVELS = intArrayOf(60, 75, 90)
    private val GOLPAR_XP = doubleArrayOf(6.5, 10.0, 13.0)
    private val BUCHU_XP = doubleArrayOf(13.5, 20.0, 26.5)

    /** HL1: overload 60 / 75 / 90 for 33.5 / 50 / 66.5 XP. */
    private val OVERLOAD_XP = doubleArrayOf(33.5, 50.0, 66.5)

    private val RECIPES =
        listOf(
            Recipe(CoxPotion.ELDER, Objs.RAIDS_GOLPAR, Objs.RAIDS_STINKHORN_MUSHROOM, GOLPAR_LEVELS, GOLPAR_XP),
            Recipe(CoxPotion.TWISTED, Objs.RAIDS_GOLPAR, Objs.RAIDS_CICELY, GOLPAR_LEVELS, GOLPAR_XP),
            Recipe(CoxPotion.KODAI, Objs.RAIDS_GOLPAR, Objs.RAIDS_ENDARKENED_JUICE, GOLPAR_LEVELS, GOLPAR_XP),
            Recipe(CoxPotion.REVITALISATION, Objs.RAIDS_BUCHULEAF, Objs.RAIDS_STINKHORN_MUSHROOM, BUCHU_LEVELS, BUCHU_XP),
            Recipe(CoxPotion.PRAYER, Objs.RAIDS_BUCHULEAF, Objs.RAIDS_CICELY, BUCHU_LEVELS, BUCHU_XP),
            Recipe(CoxPotion.XERICAID, Objs.RAIDS_BUCHULEAF, Objs.RAIDS_ENDARKENED_JUICE, BUCHU_LEVELS, BUCHU_XP),
            Recipe(CoxPotion.ANTIPOISON, Objs.RAIDS_NOXIFER, Objs.RAIDS_CICELY, NOXIFER_LEVELS, BUCHU_XP),
        )
    private val HERBS = intArrayOf(Objs.RAIDS_GOLPAR, Objs.RAIDS_BUCHULEAF, Objs.RAIDS_NOXIFER)
    private val OVERLOAD_PARTS = listOf(CoxPotion.ELDER, CoxPotion.TWISTED, CoxPotion.KODAI)

    // ---- effects (HL4-HL9)

    /** HL4 / HL5: the boost is `floor(base x percent) + flat`; weak 4 + 10%, standard 5 + 13%, strong 6 + 16%. */
    private val BOOST_FLAT = intArrayOf(4, 5, 6)
    private val BOOST_PERCENT = intArrayOf(10, 13, 16)

    /** HL4, conflict 5: the Strat table boosts only Ranged with twisted; the (+) page adds Defence. The table is used. */
    private const val TWISTED_BOOSTS_DEFENCE = false

    /** HL6 / OPEN-RS16: restores `flat + percent of base` per dose; only the strong values (11, 30%) are stated, used for all tiers. */
    private const val REVIT_FLAT = 11
    private const val REVIT_PERCENT = 30

    /** HL7: strong prayer enhance lasts 290 s (483 ticks); OPEN-RS17: weak and standard use 275 s (458 ticks). */
    private val PRAYER_DURATION = intArrayOf(458, 458, 483)

    /** HL7: `activations = floor(Prayer level / 2) + 31`, one point every `floor(500 / activations)` ticks. */
    private const val PRAYER_ACTIVATION_BASE = 31
    private const val PRAYER_WINDOW_TICKS = 500

    /** HL8 table, weak / standard / strong: HP, Defence and the Attack, Strength, Ranged and Magic drain as percent + flat. */
    private class Aid(val hpPercent: Int, val hpFlat: Int, val defPercent: Int, val defFlat: Int, val drainPercent: Int, val drainFlat: Int)

    private val AID = listOf(Aid(7, 1, 14, 1, 7, 1), Aid(12, 2, 18, 2, 9, 2), Aid(15, 5, 20, 5, 10, 4))

    /** HL5: the boost is re-applied every 15 s (25 ticks) for 5 minutes (500 ticks), then 50 hitpoints are healed. */
    private const val OVERLOAD_REAPPLY_TICKS = 25
    private const val OVERLOAD_TICKS = 500

    /** HL9: antipoison immunity 90 s / 6 min / 9 min by tier. */
    private val ANTIPOISON_TICKS = intArrayOf(150, 600, 900)

    /** Varbit widths (client-contract 1.2): 5419 is 6 bits, 5417 is 7 bits. */
    private const val RATE_MAX = 63
    private const val TIMER_MAX = 127
    private const val OVERLOAD_HEAL = 50

    /** HL5: 50 damage in hits of 10; OPEN-RS15: the hits come one per tick and never take the player below 1 hitpoint. */
    private const val OVERLOAD_HIT = 10
    private const val OVERLOAD_HITS = 5
    private const val HITMARK_DAMAGE = 16
    private const val HITMARK_TINT = 17

    /** OPEN-RS33: an overload cannot be drunk while its effect runs (the doc: "reset drained stats at that interval only"). */
    private const val OVERLOAD_ACTIVE = "The overload's effect is still active."

    private val OVERLOAD_TIMER = TimerId("cox.overload")
    private val PRAYER_TIMER = TimerId("cox.prayer_enhance")
    private val OVERLOAD_STATE = AttrKey<IntArray>("cox_overload")
    private val PRAYER_STATE = AttrKey<IntArray>("cox_prayer_enhance")
    private val POTION_CLOCK = AttrKey<Long>("cox_potion_clock")

    /** The vanilla potion clock: a sip blocks the next for 3 ticks (docs/mechanics/consumables.md, OPEN-E1). */
    private const val SIP_DELAY = 3L

    // ---- points (HL10)

    /** HL10 / OPEN-RS18: points per dose are not stated ("overloads give the most"); these are placeholders. */
    private const val DOSE_POINTS = 10.0
    private const val OVERLOAD_DOSE_POINTS = 20.0

    /** HL10: +50% when somebody else drinks it (the exact bonus is OPEN-RS18; the food bonus is reused). */
    private const val OTHER_DRINKER_BONUS = 1.5

    /** HL10: 5 doses x players at full value, then 5 more at a reduced amount (OPEN-RS18: half). */
    private const val DOSES_PER_PLAYER = 5
    private const val REDUCED_DOSES = 5
    private const val REDUCED_SHARE = 0.5

    private const val OP_DRINK = "Drink"

    fun register(scope: ContentScope) {
        with(scope) {
            for (herb in HERBS) onUseOnItem(herb, Objs.RAIDS_VIAL_WATER) { make(herb) }
            for (kind in OVERLOAD_PARTS) {
                for (t in 0 until CoxPotion.TIERS) {
                    for (d in 1..CoxPotion.DOSES) onUseOnItem(Objs.RAIDS_NOXIFER, kind.item(t, d)) { makeOverload() }
                }
            }
            for (potion in ITEMS) onItemOp(potion, OP_DRINK) { click -> drink(click.slot, click.obj) }
            events.on<ConsumeRequest> { r ->
                if (r.invId == Invs.INV && r.obj in DECODE &&
                    r.player.inv(Invs.INV).obj(r.slot) == r.obj
                ) {
                    r.handled = r.player.drink(r.slot, r.obj)
                }
            }
        }
    }

    // ---- making (HL1)

    /** The highest tier of [levels] that [level] reaches, or -1. */
    private fun tierOf(levels: IntArray, level: Int): Int = levels.indexOfLast { it <= level }

    /** HL1: the potions [herb] can make with the secondaries carried and the Herblore level; asks when there are several. */
    private fun PlayerRef.make(herb: Int) {
        val run = CoxFlow.runOf(this) ?: return
        val options = RECIPES.filter { it.herb == herb && CoxRes.held(this, it.second) > 0L }
        if (options.isEmpty()) return message("You need a secondary ingredient for that herb.")
        val usable = options.filter { tierOf(it.levels, level(Stats.HERBLORE)) >= 0 }
        if (usable.isEmpty()) return message("You need a higher Herblore level to make that.")
        if (usable.size == 1) return brew(run, usable[0])
        choiceDialog("Make which potion?", usable.map { it.kind.title }) { pick -> CoxFlow.runOf(this)?.let { brew(it, usable[pick - 1]) } }
    }

    /** HL1: the tier follows the Herblore level at creation; one herb, one secondary and one vial make a 4-dose potion. */
    private fun PlayerRef.brew(run: RaidRun, recipe: Recipe) {
        val tier = tierOf(recipe.levels, level(Stats.HERBLORE))
        if (tier < 0) return
        val potion = recipe.kind.item(tier, CoxPotion.DOSES)
        val done =
            transaction("cox_make_potion") {
                remove(Invs.INV, Objs.RAIDS_VIAL_WATER, 1) &&
                    remove(Invs.INV, recipe.herb, 1) &&
                    remove(Invs.INV, recipe.second, 1) &&
                    add(Invs.INV, potion, 1)
            }
        if (!done) return
        anim(Seqs.HUMAN_FILLBUCKET_SANDPIT)
        addXp(Stats.HERBLORE, recipe.xp[tier])
        CoxRes.push(CoxRes.state(this, run).makers, family(recipe.kind, tier), username, CoxPotion.DOSES)
        message("You make a ${objName(potion).lowercase()}.")
    }

    /**
     * HL1, conflict 4: one elder, one twisted and one kodai potion (any variant) and a noxifer make an overload. The tier
     * is the one the Herblore level allows, but not above the weakest of the three (both readings agree there).
     */
    private fun PlayerRef.makeOverload() {
        val run = CoxFlow.runOf(this) ?: return
        val inv = inv(Invs.INV)
        val slots = OVERLOAD_PARTS.map { kind -> (0 until inv.size).firstOrNull { DECODE[inv.obj(it)]?.first == kind } }
        if (slots.any { it == null } ||
            CoxRes.held(this, Objs.RAIDS_NOXIFER) == 0L
        ) {
            return message("You need a noxifer and an elder, twisted and kodai potion.")
        }
        val levelTier = tierOf(NOXIFER_LEVELS, level(Stats.HERBLORE))
        if (levelTier < 0) return message("You need a Herblore level of ${NOXIFER_LEVELS[0]} to make that.")
        val parts = slots.map { requireNotNull(it) }
        val tier = minOf(levelTier, parts.minOf { requireNotNull(DECODE[inv.obj(it)]).second })
        val potion = CoxPotion.OVERLOAD.item(tier, CoxPotion.DOSES)
        val done =
            transaction("cox_make_overload") {
                remove(Invs.INV, Objs.RAIDS_NOXIFER, 1) &&
                    parts.all { removeAt(Invs.INV, it, inv.obj(it)) } &&
                    add(Invs.INV, potion, 1)
            }
        if (!done) return
        addXp(Stats.HERBLORE, OVERLOAD_XP[tier])
        CoxRes.push(CoxRes.state(this, run).makers, family(CoxPotion.OVERLOAD, tier), username, CoxPotion.DOSES)
        message("You make a ${objName(potion).lowercase()}.")
    }

    // ---- drinking (HL4-HL9)

    private fun family(kind: CoxPotion, tier: Int): Int = kind.ordinal * CoxPotion.TIERS + tier

    private fun PlayerRef.drink(slot: Int, obj: Int): Boolean {
        val (kind, tier, dose) = DECODE[obj] ?: return false
        val now = tick
        if ((this[POTION_CLOCK] ?: 0L) > now) return false
        if (inv(Invs.INV).obj(slot) != obj) return false
        if (kind == CoxPotion.OVERLOAD && hasTimer(OVERLOAD_TIMER)) {
            message(OVERLOAD_ACTIVE)
            return false
        }
        // HL2: the gourd vial breaks when the last dose is drunk.
        val ok =
            transaction("cox_drink") {
                if (dose == 1) removeAt(Invs.INV, slot, obj, 1) else replaceAt(Invs.INV, slot, obj, kind.item(tier, dose - 1))
            }
        if (!ok) return false
        this[POTION_CLOCK] = now + SIP_DELAY
        anim(Seqs.HUMAN_EAT)
        message("You drink some of your ${kind.title}.")
        effect(kind, tier)
        events.publish(Consumed(this, obj, drink = true))
        creditMaker(kind, tier)
        return true
    }

    private fun PlayerRef.effect(kind: CoxPotion, tier: Int) {
        when (kind) {
            CoxPotion.ELDER -> {
                for (s in intArrayOf(Stats.ATTACK, Stats.STRENGTH, Stats.DEFENCE)) boost(s, tier)
            }

            CoxPotion.TWISTED -> {
                boost(Stats.RANGED, tier)
                if (TWISTED_BOOSTS_DEFENCE) boost(Stats.DEFENCE, tier)
            }

            CoxPotion.KODAI -> {
                boost(Stats.MAGIC, tier)
            }

            CoxPotion.REVITALISATION -> {
                revitalise()
            }

            CoxPotion.PRAYER -> {
                startPrayerEnhance(tier)
            }

            CoxPotion.XERICAID -> {
                xericAid(tier)
            }

            CoxPotion.OVERLOAD -> {
                startOverload(tier)
            }

            // HL9: cures poison with 150 / 600 / 900 ticks of immunity (the poison state is the combat module's).
            CoxPotion.ANTIPOISON -> {
                events.publish(tp.plugin.CurePoisonRequest(this, ANTIPOISON_TICKS[tier]))
            }
        }
    }

    /** HL4: raises [stat] to base + `floor(base x percent / 100) + flat`, never lowering a higher boost. */
    private fun PlayerRef.boost(stat: Int, tier: Int) {
        val base = baseLevel(stat)
        setLevel(stat, maxOf(level(stat), base + base * BOOST_PERCENT[tier] / PERCENT + BOOST_FLAT[tier]))
    }

    /** HL6: every drained stat and Prayer is restored by 11 + 30% of its base level (Hitpoints are not drained). */
    private fun PlayerRef.revitalise() {
        for (stat in 0 until Stats.COUNT) {
            if (stat == Stats.HITPOINTS) continue
            val base = baseLevel(stat)
            if (level(stat) < base) setLevel(stat, minOf(base, level(stat) + REVIT_FLAT + base * REVIT_PERCENT / PERCENT))
        }
    }

    /** HL8: heals above the maximum, raises Defence, drains Attack, Strength, Ranged and Magic (all rounded down). */
    private fun PlayerRef.xericAid(tier: Int) {
        val a = AID[tier]
        val baseHp = baseLevel(Stats.HITPOINTS)
        val heal = baseHp * a.hpPercent / PERCENT + a.hpFlat
        hitpoints = maxOf(hitpoints, minOf(maxHitpoints + heal, hitpoints + heal))
        val baseDef = baseLevel(Stats.DEFENCE)
        setLevel(Stats.DEFENCE, maxOf(level(Stats.DEFENCE), baseDef + baseDef * a.defPercent / PERCENT + a.defFlat))
        for (s in intArrayOf(Stats.ATTACK, Stats.STRENGTH, Stats.RANGED, Stats.MAGIC)) {
            val now = level(s)
            setLevel(s, maxOf(1, now - (now * a.drainPercent / PERCENT + a.drainFlat)))
        }
    }

    /**
     * HL7: one Prayer point every `floor(500 / activations)` ticks, `activations` times (capped by the potion's duration);
     * a new sip restarts it.
     */
    private fun PlayerRef.startPrayerEnhance(tier: Int) {
        val activations = baseLevel(Stats.PRAYER) / 2 + PRAYER_ACTIVATION_BASE
        val interval = maxOf(1, PRAYER_WINDOW_TICKS / activations)
        stopTimer(PRAYER_TIMER)
        this[PRAYER_STATE] = intArrayOf(minOf(activations, PRAYER_DURATION[tier] / interval))
        // Client-contract 2.2 buff bar: tier 12397, restores left 5417, interval 5419 (RuneLite: ticks = 5417 x interval).
        setVarbit(tp.symbols.Varbits.RAIDS_PRAYER_ENHANCE_TIER, tier + 1)
        setVarbit(tp.symbols.Varbits.RAIDS_PRAYERENHANCE_RATE, minOf(interval, RATE_MAX))
        setVarbit(tp.symbols.Varbits.RAIDS_PRAYERENHANCE_TIMER, minOf(this[PRAYER_STATE]?.get(0) ?: 0, TIMER_MAX))
        timer(PRAYER_TIMER, interval, soft = true) {
            val left = this[PRAYER_STATE] ?: return@timer stopTimer(PRAYER_TIMER)
            if (level(Stats.PRAYER) < baseLevel(Stats.PRAYER)) setLevel(Stats.PRAYER, level(Stats.PRAYER) + 1)
            setVarbit(tp.symbols.Varbits.RAIDS_PRAYERENHANCE_TIMER, minOf(maxOf(0, left[0] - 1), TIMER_MAX))
            if (left[0] - 1 <= 0) setVarbit(tp.symbols.Varbits.RAIDS_PRAYER_ENHANCE_TIER, 0)
            if (--left[0] <= 0) {
                this[PRAYER_STATE] = null
                stopTimer(PRAYER_TIMER)
            }
        }
    }

    /**
     * HL5: boosts the five combat stats and takes 50 hitpoints in five hits of 10; the boost is re-applied every 25 ticks
     * for 500, then 50 hitpoints are healed.
     */
    private fun PlayerRef.startOverload(tier: Int) {
        for (s in intArrayOf(Stats.ATTACK, Stats.DEFENCE, Stats.STRENGTH, Stats.RANGED, Stats.MAGIC)) boost(s, tier)
        this[OVERLOAD_STATE] = intArrayOf(tier, 0)
        // Client-contract 2.2 buff bar: tier 5420 (1-3) and the cycles of 25 ticks left in 5418 (RuneLite: ticks = x25).
        setVarbit(tp.symbols.Varbits.RAIDS_OVERLOAD_TIER, tier + 1)
        setVarbit(tp.symbols.Varbits.RAIDS_OVERLOAD_TIMER, OVERLOAD_TICKS / OVERLOAD_REAPPLY_TICKS)
        timer(OVERLOAD_TIMER, 1, soft = true) {
            val state = this[OVERLOAD_STATE] ?: return@timer stopTimer(OVERLOAD_TIMER)
            val elapsed = ++state[1]
            if (elapsed % OVERLOAD_REAPPLY_TICKS == 0) {
                setVarbit(tp.symbols.Varbits.RAIDS_OVERLOAD_TIMER, maxOf(0, (OVERLOAD_TICKS - elapsed) / OVERLOAD_REAPPLY_TICKS))
            }
            if (elapsed >= OVERLOAD_TICKS) setVarbit(tp.symbols.Varbits.RAIDS_OVERLOAD_TIER, 0)
            if (elapsed <= OVERLOAD_HITS) overloadHit()
            if (elapsed % OVERLOAD_REAPPLY_TICKS == 0) {
                for (s in intArrayOf(Stats.ATTACK, Stats.DEFENCE, Stats.STRENGTH, Stats.RANGED, Stats.MAGIC)) boost(s, state[0])
            }
            if (elapsed >= OVERLOAD_TICKS) {
                hitpoints = minOf(maxHitpoints, hitpoints + OVERLOAD_HEAL)
                this[OVERLOAD_STATE] = null
                stopTimer(OVERLOAD_TIMER)
            }
        }
    }

    private fun PlayerRef.overloadHit() {
        val dealt = minOf(OVERLOAD_HIT, hitpoints - 1).coerceAtLeast(0)
        hitpoints -= dealt
        hitsplat(null, dealt, HITMARK_DAMAGE, HITMARK_DAMAGE, HITMARK_TINT)
    }

    // ---- points (HL10)

    /** HL10: the maker gets points per dose drunk, more when another player drinks it, up to the dose cap. */
    private fun PlayerRef.creditMaker(kind: CoxPotion, tier: Int) {
        val run = CoxFlow.runOf(this) ?: return
        val st = CoxRes.state(this, run)
        val maker = CoxRes.pop(st.makers, family(kind, tier)) ?: return
        val drunk = st.dosesDrunk.merge(maker, 1, Int::plus) ?: 1
        val cap = DOSES_PER_PLAYER * run.scaling.partySize
        val share =
            when {
                drunk <= cap -> 1.0
                drunk <= cap + REDUCED_DOSES -> REDUCED_SHARE
                else -> 0.0
            }
        val base = if (kind == CoxPotion.OVERLOAD) OVERLOAD_DOSE_POINTS else DOSE_POINTS
        val bonus = if (maker.equals(username, ignoreCase = true)) 1.0 else OTHER_DRINKER_BONUS
        CoxPoints.award(run, maker, base * bonus * share)
    }

    /** HL10: a potion not made here (a boss drop) counts as made by [name] for [doses] doses of [kind] at [tier]. */
    fun credit(run: RaidRun, any: EntityRef, kind: CoxPotion, tier: Int, name: String, doses: Int) {
        CoxRes.push(CoxRes.state(any, run).makers, family(kind, tier), name, doses)
    }

    private const val PERCENT = 100
}
