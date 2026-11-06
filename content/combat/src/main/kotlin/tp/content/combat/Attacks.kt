package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Seqs
import tp.symbols.Varps

/** Player melee/ranged attack resolution: rolls on the attack tick, hit queued with the hit delay (C1, F1–F19). */
internal object Attacks {
    fun playerAttack(player: PlayerRef, target: EntityRef) {
        val (stance, type) = player.stanceAndType()
        val category = player.weaponCategory()
        val now = player.tick
        val spec = Specials.take(player)
        if (spec?.instant != true) player.setAttackClock(now + player.attackSpeed())
        if (type.style == CombatStyle.RANGED && category.usesAmmo() && !CombatSystem.useAmmo(player)) {
            player.message("There is no ammo left in your quiver.")
            player.stopInteraction()
            return
        }
        val defenceType = spec?.defenceType ?: type
        val attackRoll = attackRoll(player, stance, type) * (spec?.accuracyPercent ?: PERCENT) / PERCENT
        val defenceRoll = defenceRoll(target, defenceType)
        var max = maxHit(player, stance, type) * (spec?.damagePermille ?: PERMILLE) / PERMILLE
        if (target is PlayerRef && target.protects(type.style)) max = Formulas.protectedMaxHit(max)
        val distance = player.distanceTo(target)
        val delay = hitDelay(type.style, category, distance)
        player.anim(spec?.anim ?: attackAnim(category, type))
        CombatSystem.announceAttack(player, target, type.style)
        repeat(spec?.hits ?: 1) {
            val hit = Formulas.hits(attackRoll, defenceRoll, player::random)
            val damage = minOf(Formulas.DAMAGE_CAP, if (hit) Formulas.damage(max, player::random) else 0)
            val onLand = spec?.let { s -> { Specials.onLand(s, player, target, hit) } }
            CombatSystem.queueHit(target, player, damage, CombatSystem.hitStyle(type.style), delay, onLand)
            if (hit) grantXp(player, stance, type.style, damage)
        }
    }

    private fun WeaponCategory.usesAmmo(): Boolean = this === Weapons.BOW || this === Weapons.CROSSBOW

    fun attackRoll(player: PlayerRef, stance: Stance, type: AttackType): Int =
        when (type.style) {
            CombatStyle.MELEE -> {
                val eff = Formulas.effectiveLevel(player.level(Stat.ATTACK), player.prayerPercent { it.attack }, stance.attackBonus)
                Formulas.roll(eff, player.bonus(type.attackParam))
            }

            CombatStyle.RANGED -> {
                val eff = Formulas.effectiveLevel(player.level(Stat.RANGED), player.prayerPercent { it.rangedAttack }, stance.rangedBonus)
                Formulas.roll(eff, player.bonus(Param.ATTACK_RANGED))
            }

            CombatStyle.MAGIC -> {
                val eff = Formulas.effectiveLevel(player.level(Stat.MAGIC), player.prayerPercent { it.magic }, stance.magicBonus)
                Formulas.roll(eff, player.bonus(Param.ATTACK_MAGIC))
            }
        }

    /** F6/F7/F15/F16 defence roll of [target] against [type]. NPC defence bonuses: OPEN-N1 (0 until wiki-import). */
    fun defenceRoll(target: EntityRef, type: AttackType): Int =
        when (target) {
            is NpcRef -> {
                val level = if (type.style == CombatStyle.MAGIC) target.level(NpcStat.MAGIC) else target.level(NpcStat.DEFENCE)
                Formulas.npcDefenceRoll(level, 0)
            }

            is PlayerRef -> {
                val stance = target.stanceAndType().first
                val level =
                    if (type.style == CombatStyle.MAGIC) {
                        Formulas.playerMagicDefenceLevel(
                            target.level(Stat.MAGIC),
                            target.prayerPercent { it.magic },
                            target.level(Stat.DEFENCE),
                            target.prayerPercent { it.defence },
                        ) + stance.defenceBonus + EFFECTIVE_BASE
                    } else {
                        Formulas.effectiveLevel(target.level(Stat.DEFENCE), target.prayerPercent { it.defence }, stance.defenceBonus)
                    }
                Formulas.roll(level, target.bonus(type.defenceParam))
            }
        }

    fun maxHit(player: PlayerRef, stance: Stance, type: AttackType): Int =
        when (type.style) {
            CombatStyle.MELEE -> {
                val eff =
                    Formulas.effectiveLevel(
                        player.level(Stat.STRENGTH),
                        player.prayerPercent { it.strength },
                        stance.strengthBonus,
                        strength = true,
                    )
                Formulas.maxHit(eff, player.bonus(Param.MELEE_STRENGTH))
            }

            CombatStyle.RANGED -> {
                val eff =
                    Formulas.effectiveLevel(
                        player.level(Stat.RANGED),
                        player.prayerPercent {
                            it.rangedStrength
                        },
                        stance.rangedBonus,
                        strength = true,
                    )
                Formulas.maxHit(eff, player.bonus(Param.RANGED_STRENGTH))
            }

            CombatStyle.MAGIC -> {
                0
            }
        }

    /** combat-timeline.md per attack type. */
    fun hitDelay(style: CombatStyle, category: WeaponCategory, distance: Int): Int =
        when {
            style == CombatStyle.MELEE -> 0
            style == CombatStyle.MAGIC -> Formulas.magicDelay(distance)
            category === Weapons.THROWN -> Formulas.thrownDelay(distance)
            else -> Formulas.bowDelay(distance)
        }

    /** Visual only (OPEN-V1: per-weapon attack animations aren't in the cache). */
    private fun attackAnim(category: WeaponCategory, type: AttackType): Int =
        when {
            category === Weapons.UNARMED -> Seqs.HUMAN_UNARMEDPUNCH
            category === Weapons.BOW -> Seqs.HUMAN_BOW
            category === Weapons.CROSSBOW -> Seqs.HUMAN_CROSSBOW
            category === Weapons.STAB_SWORD -> if (type == AttackType.STAB) Seqs.HUMAN_DDAGGER_LUNGE else Seqs.HUMAN_DDAGGER_HACK
            category === Weapons.TWO_HANDED -> Seqs.HUMAN_DHSWORD_SLASH
            category === Weapons.STAFF -> Seqs.HUMAN_STAFF_POUND
            else -> Seqs.HUMAN_SWORD_SLASH
        }

    /** Combat XP per damage (Combat Options CO:17-46): 4 per damage to the style's skill, 4/3 to Hitpoints. */
    @Suppress("MagicNumber") // xp rates
    private fun grantXp(player: PlayerRef, stance: Stance, style: CombatStyle, damage: Int) {
        if (damage <= 0) return
        val xp = damage * 4.0
        when {
            style == CombatStyle.RANGED && stance == Stance.LONGRANGE -> {
                player.addXp(Stat.RANGED, xp / 2)
                player.addXp(Stat.DEFENCE, xp / 2)
            }

            style == CombatStyle.RANGED -> {
                player.addXp(Stat.RANGED, xp)
            }

            stance == Stance.ACCURATE -> {
                player.addXp(Stat.ATTACK, xp)
            }

            stance == Stance.AGGRESSIVE -> {
                player.addXp(Stat.STRENGTH, xp)
            }

            stance == Stance.DEFENSIVE -> {
                player.addXp(Stat.DEFENCE, xp)
            }

            else -> {
                for (s in intArrayOf(Stat.ATTACK, Stat.STRENGTH, Stat.DEFENCE)) player.addXp(s, damage * 4.0 / 3)
            }
        }
        player.addXp(Stat.HITPOINTS, damage * 4.0 / 3)
    }

    private const val PERCENT = 100
    private const val PERMILLE = 1000
    private const val EFFECTIVE_BASE = 8
}
