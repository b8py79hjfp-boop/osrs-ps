package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.TimerId

/** What a spell does when its hit lands (docs/mechanics/combat-magic.md CM7, CM9-CM12) and the Arceuus buff states. */
internal object SpellEffects {
    /** CM16 / CM17: Mark of Darkness and Ward of Arceuus end at these ticks. */
    val MARK_UNTIL = AttrKey<Long>("arceuus_mark_until")
    val WARD_UNTIL = AttrKey<Long>("arceuus_ward_until")

    fun marked(player: PlayerRef): Boolean = (player[MARK_UNTIL] ?: 0L) > player.tick

    fun warded(target: EntityRef): Boolean = target is PlayerRef && (target[WARD_UNTIL] ?: 0L) > target.tick

    fun land(caster: PlayerRef, target: EntityRef, spell: CombatSpell, damage: Int) {
        when (val effect = spell.effect) {
            SpellEffect.None -> {
                Unit
            }

            is SpellEffect.Debuff -> {
                lower(target, effect.stat, effect.npcStat, effect.percent, 0)
            }

            is SpellEffect.Drain -> {
                drain(target, effect)
            }

            is SpellEffect.Bind -> {
                Freezes.freeze(target, caster, effect.ticks + BARK_TICKS * pieces(caster, SWAMPBARK), fade = true)
            }

            is SpellEffect.Freeze -> {
                Freezes.freeze(target, caster, effect.ticks * sceptre(caster) / PERCENT)
            }

            is SpellEffect.Poison -> {
                if (caster.random(SMOKE_CHANCE) ==
                    0
                ) {
                    Poison.poison(target, effect.severity * sceptre(caster) / PERCENT)
                }
            }

            is SpellEffect.Heal -> {
                heal(caster, effect.percent, damage)
            }

            is SpellEffect.Grasp -> {
                grasp(caster, target, effect)
            }
        }
    }

    /** CM7, CM11: shadow and god drains apply only while the stat is at or above its base level. */
    private fun drain(target: EntityRef, effect: SpellEffect.Drain) {
        if (target is NpcRef && effect.npcStat < 0) return // Saradomin Strike: NPCs have no Prayer
        lower(target, effect.stat, effect.npcStat, effect.percent, effect.flat)
    }

    private fun lower(target: EntityRef, stat: Int, npcStat: Int, percent: Int, flat: Int) {
        if (SpellCasting.lowered(target, stat, npcStat)) return
        when (target) {
            is PlayerRef -> target.setLevel(stat, target.level(stat) - amount(target.baseLevel(stat), percent, flat))
            is NpcRef -> target.setLevel(npcStat, target.level(npcStat) - amount(target.baseLevel(npcStat), percent, flat))
        }
    }

    /** OPEN-CM9: a percentage drain lowers by at least 1. */
    private fun amount(base: Int, percent: Int, flat: Int): Int = maxOf(if (percent > 0) 1 else 0, base * percent / PERCENT) + flat

    /** CM11: 25% of the rolled damage, +2% per bloodbark piece, ×1.1 with an ancient sceptre; not above max Hitpoints. */
    private fun heal(caster: PlayerRef, percent: Int, damage: Int) {
        val share = (percent + BARK_HEAL * pieces(caster, BLOODBARK)) * sceptre(caster) / PERCENT
        val amount = damage * share / PERCENT
        if (amount <= 0) return
        caster.hitpoints = minOf(caster.maxHitpoints, caster.hitpoints + amount)
        caster.message("You drain some of your opponent's health.")
    }

    /** CM12: the bind chance (doubled by Mark of Darkness) on a hit; a warded player is held for one tick. */
    private fun grasp(caster: PlayerRef, target: EntityRef, effect: SpellEffect.Grasp) {
        val chance = if (marked(caster)) effect.chance * 2 else effect.chance
        if (caster.random(PERCENT) >= chance) return
        Freezes.freeze(target, caster, if (warded(target)) 1 else effect.ticks)
    }

    /** Swampbark / bloodbark pieces worn: helm, body, legs (Wiki "Swampbark armour", "Bloodbark armour"). */
    private fun pieces(player: PlayerRef, word: String): Int =
        intArrayOf(Wear.HEAD, Wear.BODY, Wear.LEGS).count { slot ->
            val obj = player.worn(slot)
            obj >= 0 && word in player.objName(obj).lowercase()
        }

    /** CM10, CM11 (Wiki "Ancient sceptre"): every ancient sceptre variant adds 10% (as a percent multiplier). */
    private fun sceptre(player: PlayerRef): Int {
        val weapon = player.worn(Wear.WEAPON)
        return if (weapon >= 0 && "ancient sceptre" in player.objName(weapon).lowercase()) SCEPTRE_PERCENT else PERCENT
    }

    private const val PERCENT = 100
    private const val SCEPTRE_PERCENT = 110
    private const val SWAMPBARK = "swampbark"
    private const val BLOODBARK = "bloodbark"
    private const val BARK_TICKS = 2
    private const val BARK_HEAL = 2

    /** Wiki "Smoke spells": 1 in 8 on a hit. */
    private const val SMOKE_CHANCE = 8
}

/** Arceuus combat buffs the spellbook buttons start (CM16-CM19). */
internal object ArceuusBuffs {
    val CORRUPTION = AttrKey<Int>("arceuus_corruption_buff")
    val CORRUPTED = AttrKey<Boolean>("arceuus_corrupted")
    val DEATH_CHARGE_UNTIL = AttrKey<Long>("arceuus_death_charge_until")
    val MARK_FADE = TimerId("arceuus_mark")
    val CORRUPT = TimerId("arceuus_corruption")
}
