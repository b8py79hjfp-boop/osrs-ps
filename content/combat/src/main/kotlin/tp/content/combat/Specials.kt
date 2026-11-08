package tp.content.combat

import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Objs
import tp.symbols.Varps

/** How a special attack is delivered (special-attacks.md SP2). */
internal enum class SpecStyle { MELEE, RANGED, MAGIC, INSTANT }

/**
 * One weapon's special attack ([SpecialTable], generated): energy in tenths of a percent (varp `sa_energy`, 1000 =
 * 100%), accuracy and damage multipliers in permille (accuracy 0 = the attack always hits), hit count, forced defence
 * roll, anim and graphics, and whether it fires at once instead of waiting for the attack clock.
 */
internal class SpecialRow(
    val obj: Int,
    val name: String,
    val energy: Int,
    val style: SpecStyle,
    val accuracyPermille: Int,
    val damagePermille: Int,
    val hits: Int,
    val defenceType: AttackType?,
    val anim: Int,
    val spot: Int,
    val targetSpot: Int,
    val projectile: Int,
    val instant: Boolean,
) {
    val guaranteed: Boolean get() = accuracyPermille == 0
}

internal object Specials {
    const val FULL_ENERGY = 1000

    /** SP3 (Wiki "Special attacks"): +10% every 50 ticks; every 25 with a lightbearer. */
    private const val REGEN_INTERVAL = 50
    private const val LIGHTBEARER_INTERVAL = 25
    private const val REGEN_AMOUNT = 100
    private val REGEN = TimerId("special_regen")
    private val COUNTER = tp.plugin.AttrKey<Int>("special_regen_counter")

    private val byObj: Map<Int, SpecialRow> = SpecialTable.ALL.associateBy { it.obj }

    fun of(obj: Int): SpecialRow? = byObj[obj]

    fun startRegen(player: PlayerRef) {
        player.timer(REGEN, 1, soft = true) {
            val interval = if (worn(Wear.RING) == Objs.LIGHTBEARER) LIGHTBEARER_INTERVAL else REGEN_INTERVAL
            val n = (this[COUNTER] ?: 0) + 1
            if (n < interval) {
                this[COUNTER] = n
                return@timer
            }
            this[COUNTER] = 0
            val energy = varp(Varps.SA_ENERGY)
            if (energy < FULL_ENERGY) setVarp(Varps.SA_ENERGY, minOf(FULL_ENERGY, energy + REGEN_AMOUNT))
        }
    }

    /** SP4: the spec bar / orb. Buff specials fire at once; the rest arm the next attack. */
    fun toggle(player: PlayerRef) {
        if (player.varp(Varps.SA_ATTACK) != 0) {
            player.setVarp(Varps.SA_ATTACK, 0)
            return
        }
        if (player.duelRule(tp.plugin.DuelRules.NO_SPECIAL_ATTACKS)) {
            player.message(DuelChecks.NO_SPECIALS)
            return
        }
        val row = rowFor(player)
        if (row == null) {
            player.message("This weapon does not have a special attack.")
            return
        }
        if (player.varp(Varps.SA_ENERGY) < SpecialEffects.energy(player, row)) {
            player.message(NO_ENERGY)
            return
        }
        if (!SpecialRules.ready(player, row)) return
        if (row.style == SpecStyle.INSTANT && !row.instantAttack()) {
            if (SpecialEffects.buff(player, row)) player.setVarp(Varps.SA_ENERGY, player.varp(Varps.SA_ENERGY) - SpecialEffects.energy(player, row))
            return
        }
        player.setVarp(Varps.SA_ATTACK, 1)
        // SP5: Quick Smash and Momentum Throw hit now, without waiting for the attack clock.
        if (row.instant) SpecialEffects.instantAttack(player)
    }

    /**
     * The weapon's special; without one, a Rondache's Shield Slam (SP53: it "can be activated by clicking the usual
     * special attack bar"). Operate shields (dragonfire shield / ward, ancient wyvern shield) are worn ops, not specs.
     */
    private fun rowFor(player: PlayerRef): SpecialRow? =
        byObj[player.worn(Wear.WEAPON)] ?: byObj[player.worn(Wear.SHIELD)]?.takeIf { it.name == "Shield Slam" }

    /** Instant-style rows that are attacks (granite maul, thrownhammer-like) rather than buffs. */
    private fun SpecialRow.instantAttack(): Boolean = name in INSTANT_ATTACKS

    private val INSTANT_ATTACKS = setOf("Quick Smash", "Momentum Throw", "Ferocity", "Shield Slam")

    /** C10: the spec used by this attack, energy deducted now; null if the bar is off or energy is short. */
    fun take(player: PlayerRef): SpecialRow? {
        if (player.varp(Varps.SA_ATTACK) == 0) return null
        player.setVarp(Varps.SA_ATTACK, 0)
        val row =
            rowFor(player)?.takeIf { (it.style != SpecStyle.INSTANT || it.instantAttack()) && SpecialRules.ready(player, it) }
                ?: return null
        val energy = player.varp(Varps.SA_ENERGY)
        val cost = SpecialEffects.cost(player, row)
        if (energy < cost) {
            player.message(NO_ENERGY)
            return null
        }
        player.setVarp(Varps.SA_ENERGY, energy - cost)
        return row
    }

    /** OPEN-SA-25: the Wiki gives this text for the bar; kept for every refusal. */
    const val NO_ENERGY = "You do not have enough special attack energy left."
}
