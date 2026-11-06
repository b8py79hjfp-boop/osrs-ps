package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varps

/**
 * A special attack as data (brief §8: "special attacks as data plus hooks"). Energy in tenths of a percent (varp
 * sa_energy, 1000 = 100%). Values: OSRS Wiki "Special attacks" (SA) and weapon pages; combat-formulas.md table.
 */
internal class SpecialDef(
    val energy: Int,
    val accuracyPercent: Int = 100,
    /** Max-hit multiplier in permille (1375 = ×1.375). */
    val damagePermille: Int = 1000,
    val hits: Int = 1,
    val defenceType: AttackType? = null,
    val anim: Int? = null,
    /** Freeze applied when a hit lands successfully (ZGS) / regardless (tentacle), in ticks. */
    val freezeOnHit: Int = 0,
    val freezeAlways: Int = 0,
    /** No attack cooldown (granite maul: C6). */
    val instant: Boolean = false,
)

internal object Specials {
    const val FULL_ENERGY = 1000

    /** Special energy +10% per 50 ticks (brief §8; Wiki "10% per 30 seconds"). */
    private const val REGEN_INTERVAL = 50
    private const val REGEN_AMOUNT = 100
    private val REGEN = TimerId("special_regen")

    @Suppress("MagicNumber") // Wiki values
    private val defs: Map<Int, SpecialDef> =
        mapOf(
            // Dragon dagger "Puncture": 25%, two hits, +15% accuracy and damage, slash (SA:2181-2193).
            Objs.DRAGON_DAGGER to SpecialDef(250, 115, 1150, hits = 2, defenceType = AttackType.SLASH, anim = Seqs.PUNCTURE),
            Objs.DRAGON_DAGGER_P to SpecialDef(250, 115, 1150, hits = 2, defenceType = AttackType.SLASH, anim = Seqs.PUNCTURE),
            Objs.DRAGON_DAGGER_P_ to SpecialDef(250, 115, 1150, hits = 2, defenceType = AttackType.SLASH, anim = Seqs.PUNCTURE),
            Objs.DRAGON_DAGGER_P__ to SpecialDef(250, 115, 1150, hits = 2, defenceType = AttackType.SLASH, anim = Seqs.PUNCTURE),
            // Armadyl godsword "The Judgement": 50%, double accuracy, ×1.375, slash (SA:1281-1293).
            Objs.AGS to SpecialDef(500, 200, 1375, defenceType = AttackType.SLASH, anim = Seqs.ARMADYL_SPECIAL_ATTACK),
            // Zamorak godsword "Ice Cleave": 50%, double accuracy, ×1.1, 32-tick freeze on a hit (SA:2565-2578).
            Objs.ZGS to SpecialDef(500, 200, 1100, defenceType = AttackType.SLASH, freezeOnHit = 32),
            // Abyssal whip "Energy Drain": 50%, +25% accuracy (Abyssal_whip:56).
            Objs.ABYSSAL_WHIP to SpecialDef(500, 125),
            // Abyssal tentacle "Binding Tentacle": 50%, 8-tick freeze regardless of hit (Abyssal_tentacle:60).
            Objs.ABYSSAL_TENTACLE to SpecialDef(500, freezeAlways = 8),
            // Granite maul "Quick Smash": 60%, no accuracy change, no attack cooldown (GM:78-89).
            Objs.GRANITE_MAUL to SpecialDef(600, instant = true, anim = Seqs.SLAYER_GRANITE_MAUL_SPECIAL_ATTACK),
            // Dragon claws "Slice and Dice": 50%, 4 hits (simplified: OPEN-SP1, the 4-2-1-1 roll pattern isn't modelled).
            Objs.DRAGON_CLAWS to SpecialDef(500, hits = 4, damagePermille = 500, defenceType = AttackType.SLASH),
        )

    fun startRegen(player: PlayerRef) {
        player.timer(REGEN, REGEN_INTERVAL, soft = true) {
            val energy = varp(Varps.SA_ENERGY)
            if (energy < FULL_ENERGY) setVarp(Varps.SA_ENERGY, minOf(FULL_ENERGY, energy + REGEN_AMOUNT))
        }
    }

    /** Spec bar click: toggle if the weapon has a special and enough energy. */
    fun toggle(player: PlayerRef) {
        if (player.varp(Varps.SA_ATTACK) == 0 && player.duelRule(tp.plugin.DuelRules.NO_SPECIAL_ATTACKS)) {
            player.message(DuelChecks.NO_SPECIALS)
            return
        }
        val def = defs[player.worn(Wear.WEAPON)]
        if (player.varp(Varps.SA_ATTACK) != 0) {
            player.setVarp(Varps.SA_ATTACK, 0)
            return
        }
        if (def == null) return
        if (player.varp(Varps.SA_ENERGY) < def.energy) {
            player.message("You do not have enough special attack energy left.")
            return
        }
        player.setVarp(Varps.SA_ATTACK, 1)
    }

    /** C10: the spec used by this attack, energy deducted now; null if the bar is off or energy is short. */
    fun take(player: PlayerRef): SpecialDef? {
        if (player.varp(Varps.SA_ATTACK) == 0) return null
        player.setVarp(Varps.SA_ATTACK, 0)
        val def = defs[player.worn(Wear.WEAPON)] ?: return null
        val energy = player.varp(Varps.SA_ENERGY)
        if (energy < def.energy) {
            player.message("You do not have enough special attack energy left.")
            return null
        }
        player.setVarp(Varps.SA_ENERGY, energy - def.energy)
        return def
    }

    /** On the application tick: freeze effects. */
    fun onLand(def: SpecialDef, attacker: EntityRef, target: EntityRef, hit: Boolean) {
        if (def.freezeAlways > 0) Freezes.freeze(target, attacker, def.freezeAlways)
        if (hit && def.freezeOnHit > 0) Freezes.freeze(target, attacker, def.freezeOnHit)
    }
}
