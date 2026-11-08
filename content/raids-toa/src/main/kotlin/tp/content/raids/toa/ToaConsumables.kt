package tp.content.raids.toa

import tp.plugin.AttrKey
import tp.plugin.Consumed
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * What the ToA supply items do (toa.md TA55-TA59) and what the Restoration invocations forbid (TA15). Where the sources
 * give no number or text the constant is marked `OPEN-TA<n>` and the placeholder line is not a captured one.
 */
internal object ToaConsumables {
    /** The cache names of the Restoration invocations (client-contract 4.1; `ToaState.modifierNames`). */
    const val ON_A_DIET = "On a Diet"
    const val DEHYDRATION = "Dehydration"
    const val OVERLY_DRAINING = "Overly Draining"

    /** The five combat stats the supplies change (TA56-TA59 name Attack, Strength, Defence, Ranged and Magic). */
    private val COMBAT_STATS = intArrayOf(Stats.ATTACK, Stats.STRENGTH, Stats.DEFENCE, Stats.RANGED, Stats.MAGIC)

    private const val PERCENT = 100

    // ---- nectar (TA57)

    /** TA57: heals `floor(base x 15/100) + 3` Hitpoints and lowers the five combat stats by `floor(current/20 + 5)`. */
    /** OPEN-TA42: the nectar's "small attack delay" (U-Rev), assumed 1 tick. */
    private const val NECTAR_ATTACK_DELAY = 1

    private const val NECTAR_HEAL_PERCENT = 15
    private const val NECTAR_HEAL_FLAT = 3
    private const val NECTAR_DRAIN_DIVISOR = 20
    private const val NECTAR_DRAIN_FLAT = 5

    // ---- tears of Elidinis (TA59)

    /** TA59: combat stats `floor(base/4 + 3)`, Prayer `floor(base x 25/100) + 10`; one tile away: `floor(base x 10/100) + 10`. */
    private const val TEARS_STAT_DIVISOR = 4
    private const val TEARS_STAT_FLAT = 3
    private const val TEARS_PRAYER_PERCENT = 25
    private const val TEARS_PRAYER_FLAT = 10
    private const val TEARS_NEARBY_PERCENT = 10
    private const val TEARS_NEARBY_RADIUS = 1

    // ---- ambrosia (TA56)

    /** TA56: Hitpoints boosted by `floor(base/4 + 2)` and Prayer by `floor(base/5 + 5)` above the maximum after a full restore. */
    private const val AMBROSIA_HP_DIVISOR = 4
    private const val AMBROSIA_HP_FLAT = 2
    private const val AMBROSIA_PRAYER_DIVISOR = 5
    private const val AMBROSIA_PRAYER_FLAT = 5

    /** TA56: poison immunity 12 minutes = 1,200 ticks (venom immunity 30-60 ticks needs a combat hook, see the report). */
    private const val AMBROSIA_POISON_IMMUNITY_TICKS = 1_200

    // ---- silk dressing and blessed crystal scarab (TA58)

    /** TA58: 5 Hitpoints every 5 ticks, 20 times, up to 5 above the maximum. OPEN-TA41: the first heal comes after 5 ticks. */
    private const val SILK_HEAL = 5
    private const val SILK_INTERVAL = 5
    private const val SILK_APPLICATIONS = 20
    private const val SILK_OVERHEAL = 5

    /** TA58 / OPEN-TA36: 8 Prayer points every 4 ticks, 9 applications at ticks 0, 4, ..., 32 (72 points). */
    private const val SCARAB_PRAYER = 8
    private const val SCARAB_INTERVAL = 4
    private const val SCARAB_APPLICATIONS = 9

    // ---- smelling salts and liquid adrenaline (TA59)

    /** TA59: 25% run energy (the run bar is 10,000 units, 100 = 1%). */
    private const val SALTS_ENERGY = 2_500
    private const val FULL_RUN_ENERGY = 10_000

    /** TA59: the five combat stats are boosted by `floor(base x 16/100) + 11`, refreshed every 25 ticks for 800 ticks. */
    private const val SALTS_BOOST_PERCENT = 16
    private const val SALTS_BOOST_FLAT = 11
    private const val SALTS_REFRESH_TICKS = 25
    private const val SALTS_DURATION_TICKS = 800

    /** TA59: liquid adrenaline halves special attack costs for 150 seconds = 250 ticks. */
    private const val ADRENALINE_TICKS = 250

    /** TA59 / TA15: the special attack bar is 1,000 points (tenths of a percent); Overly Draining makes every cost 100%. */
    private const val FULL_SPECIAL_COST = 1_000

    // ---- honey locust (TA55)

    /** TA55: heals 20 Hitpoints; Prayer `floor(base/4) + 7` (prayer potion); combat stats `floor(level/4) + 8` (super restore). */
    private const val LOCUST_HEAL = 20
    private const val LOCUST_PRAYER_DIVISOR = 4
    private const val LOCUST_PRAYER_FLAT = 7
    private const val LOCUST_STAT_DIVISOR = 4
    private const val LOCUST_STAT_FLAT = 8

    /** Expert mode (TA55): the message when a stack above the amount given on death is reset to one. */
    private const val LOCUST_FLY_AWAY = "Some of your honey locusts fly away under the immense pressure of the tomb."

    // ---- the buff bar (client-contract 2.8)

    /** Server-run script 5927(buffId, endTick); the ids are the mid-raid healing / prayer / energy / stats buffs. */
    private const val SCRIPT_BUFF = 5927
    private const val BUFF_HEALING = 4373
    private const val BUFF_PRAYER = 4374
    private const val BUFF_ENERGY = 4375
    private const val BUFF_STATS = 4376

    /** The potion and food clocks: a sip or a bite blocks the next for 3 ticks (consumables.md E1, OPEN-E1). */
    private const val EAT_DELAY = 3L

    private val POTION_CLOCK = AttrKey<Long>("toa_potion_clock")
    private val FOOD_CLOCK = AttrKey<Long>("toa_food_clock")
    private val ADRENALINE_UNTIL = AttrKey<Long>("toa_adrenaline_until")
    private val SILK_LEFT = AttrKey<IntArray>("toa_silk_left")
    private val SCARAB_LEFT = AttrKey<IntArray>("toa_scarab_left")
    private val SALTS_LEFT = AttrKey<IntArray>("toa_salts_left")
    private val SILK_TIMER = TimerId("toa.silk")
    private val SCARAB_TIMER = TimerId("toa.scarab")
    private val SALTS_TIMER = TimerId("toa.salts")
    private val ADRENALINE_TIMER = TimerId("toa.adrenaline")

    // OPEN-TA38: none of these lines was captured.
    private const val DIET_BLOCKED = "You can't eat that while On a Diet is active."
    private const val DEHYDRATION_BLOCKED = "You can't drink that while Dehydration is active."

    fun register(scope: ContentScope) {
        with(scope) {
            for (supply in ToaSupply.entries) {
                for (dose in 1..supply.maxDoses) {
                    val obj = supply.item(dose)
                    onItemOp(obj, supply.verb) { click -> use(click.slot, obj) }
                }
            }
            onItemOp(Objs.TOA_HONEY_LOCUST, "Eat") { click -> eatLocust(click.slot) }
            // The effects and the bag exist only inside the raid.
            onLogin { if (ToaFlow.runOf(this) == null) clear(this) }
        }
    }

    // ---- the restrictions (TA15)

    /**
     * The refusal for a consumable under the run's Restoration invocations, or null. [food] is food in the OSRS sense
     * (On a Diet blocks it, Guthix Rest included, TI18), [restoresHealth] a potion that restores health (Dehydration
     * blocks it, TI19). The ToA items ask this themselves; the standard food and potions of content/combat need the
     * consume hook described in the report.
     */
    fun refusal(player: PlayerRef, food: Boolean, restoresHealth: Boolean): String? {
        val st = ToaFlow.runOf(player)?.let { ToaFlow.state(it) } ?: return null
        return when {
            food && ON_A_DIET in st.modifierNames -> DIET_BLOCKED
            restoresHealth && DEHYDRATION in st.modifierNames -> DEHYDRATION_BLOCKED
            else -> null
        }
    }

    /**
     * TA15 / TA59: the energy a special attack of base cost [baseTenths] (tenths of a percent, content/combat `sa_energy`)
     * costs [player] now: Overly Draining makes it 100%, liquid adrenaline halves it without rounding (so 50% under Overly
     * Draining). Outside a ToA run the cost is unchanged. Meant for the special attack hook described in the report.
     */
    fun specialCostTenths(player: PlayerRef, baseTenths: Int): Int {
        val st = ToaFlow.runOf(player)?.let { ToaFlow.state(it) } ?: return baseTenths
        val base = if (OVERLY_DRAINING in st.modifierNames) FULL_SPECIAL_COST else baseTenths
        return if ((player[ADRENALINE_UNTIL] ?: 0L) > player.tick) base / 2 else base
    }

    // ---- using a supply potion (TA56-TA59)

    private fun PlayerRef.use(slot: Int, obj: Int) {
        val (supply, dose) = ToaSupply.of(obj) ?: return
        if (inv(Invs.INV).obj(slot) != obj) return
        if (ToaFlow.runOf(this) == null) return
        val food = supply == ToaSupply.SILK
        val restoresHealth = supply == ToaSupply.NECTAR || supply == ToaSupply.AMBROSIA
        refusal(this, food, restoresHealth)?.let { return message(it) }
        val clock = if (food) FOOD_CLOCK else POTION_CLOCK
        val now = tick
        if ((this[clock] ?: 0L) > now) return
        val ok =
            transaction("toa supply") {
                if (dose == 1) removeAt(Invs.INV, slot, obj, 1) else replaceAt(Invs.INV, slot, obj, supply.item(dose - 1))
            }
        if (!ok) return
        this[clock] = now + EAT_DELAY
        anim(Seqs.HUMAN_EAT)
        message(useMessage(supply))
        when (supply) {
            ToaSupply.NECTAR -> nectar()
            ToaSupply.TEARS -> tears()
            ToaSupply.AMBROSIA -> ambrosia()
            ToaSupply.SILK -> startSilk()
            ToaSupply.SCARAB -> startScarab()
            ToaSupply.SALTS -> startSalts()
            ToaSupply.ADRENALINE -> startAdrenaline()
        }
        events.publish(Consumed(this, obj, drink = !food))
    }

    /** OPEN-TA38: the use lines are not captured; the item's own verb. */
    private fun useMessage(supply: ToaSupply): String =
        when (supply.verb) {
            "Drink" -> "You drink some of your ${supply.label}."
            "Apply" -> "You apply the ${supply.label}."
            "Crack" -> "You crack the ${supply.label}."
            else -> "You crush the ${supply.label}."
        }

    /**
     * TA57: heals `floor(base x 15/100) + 3` (can overheal; OPEN-TA40: up to that much above the maximum, as the Saradomin
     * brew) and lowers the combat stats by `floor(current/20 + 5)`; the "small attack delay" (U-Rev) is OPEN-TA42 (working
     * assumption [NECTAR_ATTACK_DELAY] ticks).
     */
    private fun PlayerRef.nectar() {
        events.publish(tp.plugin.AttackDelayRequest(this, NECTAR_ATTACK_DELAY))
        val heal = baseLevel(Stats.HITPOINTS) * NECTAR_HEAL_PERCENT / PERCENT + NECTAR_HEAL_FLAT
        overheal(heal, maxHitpoints + heal)
        for (stat in COMBAT_STATS) {
            val current = level(stat)
            setLevel(stat, maxOf(0, current - (current / NECTAR_DRAIN_DIVISOR + NECTAR_DRAIN_FLAT)))
        }
    }

    /** TA59: combat stats and Prayer restored up to their base; party members within one tile get a smaller Prayer restore. */
    private fun PlayerRef.tears() {
        for (stat in COMBAT_STATS) restore(stat, baseLevel(stat) / TEARS_STAT_DIVISOR + TEARS_STAT_FLAT)
        restore(Stats.PRAYER, baseLevel(Stats.PRAYER) * TEARS_PRAYER_PERCENT / PERCENT + TEARS_PRAYER_FLAT)
        val run = ToaFlow.runOf(this) ?: return
        for (other in playersWithin(TEARS_NEARBY_RADIUS)) {
            if (ToaFlow.runOf(other)?.id != run.id) continue
            other.restore(Stats.PRAYER, other.baseLevel(Stats.PRAYER) * TEARS_NEARBY_PERCENT / PERCENT + TEARS_PRAYER_FLAT)
        }
    }

    /** TA56: full Hitpoints, Prayer and run energy, then the boosts above the maximum; poison and venom cured. */
    private fun PlayerRef.ambrosia() {
        val baseHp = baseLevel(Stats.HITPOINTS)
        hitpoints = maxHitpoints + baseHp / AMBROSIA_HP_DIVISOR + AMBROSIA_HP_FLAT
        val basePrayer = baseLevel(Stats.PRAYER)
        setLevel(Stats.PRAYER, basePrayer + basePrayer / AMBROSIA_PRAYER_DIVISOR + AMBROSIA_PRAYER_FLAT)
        runEnergy = FULL_RUN_ENERGY
        ToaRestores.curePoison(this, AMBROSIA_POISON_IMMUNITY_TICKS)
    }

    private fun PlayerRef.startSilk() {
        this[SILK_LEFT] = intArrayOf(SILK_APPLICATIONS)
        buff(BUFF_HEALING, SILK_APPLICATIONS * SILK_INTERVAL)
        timer(SILK_TIMER, SILK_INTERVAL, soft = true) {
            val left = this[SILK_LEFT] ?: return@timer stopTimer(SILK_TIMER)
            overheal(SILK_HEAL, maxHitpoints + SILK_OVERHEAL)
            if (--left[0] <= 0) endSilk()
        }
    }

    private fun PlayerRef.endSilk() {
        this[SILK_LEFT] = null
        stopTimer(SILK_TIMER)
        buff(BUFF_HEALING, 0)
    }

    private fun PlayerRef.startScarab() {
        restore(Stats.PRAYER, SCARAB_PRAYER)
        this[SCARAB_LEFT] = intArrayOf(SCARAB_APPLICATIONS - 1)
        buff(BUFF_PRAYER, (SCARAB_APPLICATIONS - 1) * SCARAB_INTERVAL)
        timer(SCARAB_TIMER, SCARAB_INTERVAL, soft = true) {
            val left = this[SCARAB_LEFT] ?: return@timer stopTimer(SCARAB_TIMER)
            restore(Stats.PRAYER, SCARAB_PRAYER)
            if (--left[0] <= 0) endScarab()
        }
    }

    private fun PlayerRef.endScarab() {
        this[SCARAB_LEFT] = null
        stopTimer(SCARAB_TIMER)
        buff(BUFF_PRAYER, 0)
    }

    /**
     * TA59 / OPEN-TA27: 25% run energy once ([ToaConfig.saltsEnergyEveryRefresh] repeats it at each refresh) and the stat
     * boost, refreshed every 25 ticks for 800 ticks. The stats bar reads `TOA_MIDRAIDLOOT_STATS_TIMER` in 25-tick cycles
     * (client-contract 2.8; OPEN-TC9).
     */
    private fun PlayerRef.startSalts() {
        runEnergy = minOf(FULL_RUN_ENERGY, runEnergy + SALTS_ENERGY)
        boostSalts()
        val cycles = SALTS_DURATION_TICKS / SALTS_REFRESH_TICKS
        this[SALTS_LEFT] = intArrayOf(cycles)
        setVarbit(Varbits.TOA_MIDRAIDLOOT_STATS_TIMER, cycles)
        buff(BUFF_STATS, SALTS_DURATION_TICKS)
        timer(SALTS_TIMER, SALTS_REFRESH_TICKS, soft = true) {
            val left = this[SALTS_LEFT] ?: return@timer stopTimer(SALTS_TIMER)
            boostSalts()
            if (ToaConfig.saltsEnergyEveryRefresh) runEnergy = minOf(FULL_RUN_ENERGY, runEnergy + SALTS_ENERGY)
            left[0]--
            setVarbit(Varbits.TOA_MIDRAIDLOOT_STATS_TIMER, maxOf(0, left[0]))
            if (left[0] <= 0) endSalts()
        }
    }

    private fun PlayerRef.boostSalts() {
        for (stat in COMBAT_STATS) {
            val base = baseLevel(stat)
            setLevel(stat, maxOf(level(stat), base + base * SALTS_BOOST_PERCENT / PERCENT + SALTS_BOOST_FLAT))
        }
    }

    private fun PlayerRef.endSalts() {
        this[SALTS_LEFT] = null
        stopTimer(SALTS_TIMER)
        setVarbit(Varbits.TOA_MIDRAIDLOOT_STATS_TIMER, 0)
        buff(BUFF_STATS, 0)
    }

    /** TA59: the cost halving runs 250 ticks ([specialCostTenths]); `TOA_MIDRAIDLOOT_ENERGY_ACTIVE` is up meanwhile. */
    private fun PlayerRef.startAdrenaline() {
        this[ADRENALINE_UNTIL] = tick + ADRENALINE_TICKS
        setVarbit(Varbits.TOA_MIDRAIDLOOT_ENERGY_ACTIVE, 1)
        buff(BUFF_ENERGY, ADRENALINE_TICKS)
        timer(ADRENALINE_TIMER, ADRENALINE_TICKS, soft = true) { endAdrenaline() }
    }

    private fun PlayerRef.endAdrenaline() {
        this[ADRENALINE_UNTIL] = null
        stopTimer(ADRENALINE_TIMER)
        setVarbit(Varbits.TOA_MIDRAIDLOOT_ENERGY_ACTIVE, 0)
        buff(BUFF_ENERGY, 0)
    }

    // ---- honey locust (TA55)

    private fun PlayerRef.eatLocust(slot: Int) {
        val obj = Objs.TOA_HONEY_LOCUST
        if (inv(Invs.INV).obj(slot) != obj) return
        val run = ToaFlow.runOf(this) ?: return
        refusal(this, food = true, restoresHealth = false)?.let { return message(it) }
        val now = tick
        if ((this[FOOD_CLOCK] ?: 0L) > now) return
        // TA55: in Expert Mode a stack above the amount given on death is reset to one by eating from it.
        val reset = run.scaling.mode == ToaRaid.EXPERT && inv(Invs.INV).total(obj) > ToaConfig.honeyLocustExpertCap
        val ok = transaction("toa honey locust") { if (reset) setAt(Invs.INV, slot, obj, 1) else removeAt(Invs.INV, slot, obj, 1) }
        if (!ok) return
        this[FOOD_CLOCK] = now + EAT_DELAY
        anim(Seqs.HUMAN_EAT)
        message("You eat the honey locust.")
        if (reset) message(LOCUST_FLY_AWAY)
        // OPEN-TA40: "can overheal" is read as up to that much above the maximum.
        overheal(LOCUST_HEAL, maxHitpoints + LOCUST_HEAL)
        restore(Stats.PRAYER, baseLevel(Stats.PRAYER) / LOCUST_PRAYER_DIVISOR + LOCUST_PRAYER_FLAT)
        for (stat in COMBAT_STATS) restore(stat, baseLevel(stat) / LOCUST_STAT_DIVISOR + LOCUST_STAT_FLAT)
        events.publish(Consumed(this, obj, drink = false))
    }

    // ---- helpers

    /** Adds [amount] Hitpoints up to [cap], never lowering a higher value. */
    private fun PlayerRef.overheal(amount: Int, cap: Int) {
        hitpoints = maxOf(hitpoints, minOf(cap, hitpoints + amount))
    }

    /** Restores a lowered [stat] by [amount], up to its base level. */
    private fun PlayerRef.restore(stat: Int, amount: Int) {
        val base = baseLevel(stat)
        val current = level(stat)
        if (current < base) setLevel(stat, minOf(base, current + amount))
    }

    /** Server-run 5927: the buff bar entry [id] ends in [remaining] ticks on the MAP_CLOCK clock; 0 hides it. */
    private fun PlayerRef.buff(id: Int, remaining: Int) {
        clientScript(SCRIPT_BUFF, id, if (remaining <= 0) 0 else varp(Varps.MAP_CLOCK) + remaining)
    }

    /** The effects end with the raid (or at login outside one): timers, bars, varbits and the supplies bag are cleared. */
    fun clear(p: PlayerRef) {
        if (p.hasTimer(SILK_TIMER)) p.endSilk()
        if (p.hasTimer(SCARAB_TIMER)) p.endScarab()
        if (p.hasTimer(SALTS_TIMER)) p.endSalts()
        if (p.hasTimer(ADRENALINE_TIMER)) p.endAdrenaline()
        p[POTION_CLOCK] = null
        p[FOOD_CLOCK] = null
        if (p.varbit(Varbits.TOA_MIDRAIDLOOT_STATS_TIMER) != 0) p.setVarbit(Varbits.TOA_MIDRAIDLOOT_STATS_TIMER, 0)
        if (p.varbit(Varbits.TOA_MIDRAIDLOOT_ENERGY_ACTIVE) != 0) p.setVarbit(Varbits.TOA_MIDRAIDLOOT_ENERGY_ACTIVE, 0)
        ToaSupplies.resetVarbits(p)
        ToaSupplyBag.empty(p)
    }
}
