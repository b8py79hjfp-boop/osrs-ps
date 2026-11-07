package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varps

/** Player melee/ranged attack resolution: rolls on the attack tick, hit queued with the hit delay (C1, F1–F19). */
internal object Attacks {
    fun playerAttack(player: PlayerRef, target: EntityRef) {
        val (stance, type) = player.stanceAndType()
        val category = player.weaponCategory()
        if (category === Weapons.CHINCHOMPA) return Chinchompas.attack(player, target, stance, type)
        PoweredStaves.of(player.worn(Wear.WEAPON))?.let { staff ->
            if (!PoweredStaves.attack(player, target, staff, stance)) player.stopInteraction()
            return
        }
        val spec = Specials.take(player)
        if (spec != null) return SpecialEffects.perform(player, target, spec, stance, type, category)
        val blowpipe = Blowpipe.isBlowpipe(player.worn(Wear.WEAPON))
        // GE62 Wiki "Toxic blowpipe": attack speed 3 against monsters, 4 (a shortbow's) against players.
        player.setAttackClock(player.tick + player.attackSpeed() + if (blowpipe && target is PlayerRef) 1 else 0)
        if (type.style == CombatStyle.RANGED && !CombatSystem.useAmmo(player, target, category)) {
            if (!blowpipe) player.message("There is no ammo left in your quiver.")
            player.stopInteraction()
            return
        }
        val c = GearContext.ctx(player, target, type)
        val attackRoll = attackRoll(player, stance, type, target = target)
        val defenceRoll = defenceRoll(target, type, rangedDefenceOf(category))
        val range = minMaxHit(player, stance, type, target)
        var max = range[1]
        if (target is PlayerRef && target.protects(type.style)) max = Formulas.protectedMaxHit(max)
        val min = minOf(range[0], max)
        val distance = player.distanceTo(target)
        val delay = hitDelay(type.style, category, distance)
        player.anim(attackAnim(player, category, type))
        if (type.style == CombatStyle.RANGED) rangedVisuals(player, target, category, delay)
        CombatVisuals.block(target, delay)
        CombatSystem.announceAttack(player, target, type.style)
        val splats =
            if (type.style == CombatStyle.MELEE) {
                PostRoll.melee(player, c, attackRoll, defenceRoll, min, max, unprotectedMax = range[1])
            } else {
                PostRoll.ranged(player, target, c, attackRoll, defenceRoll, max, category === Weapons.CROSSBOW)
            }
        land(player, target, stance, type, splats, delay)
        SetEffects.afterAttack(player)
        SetEffects.bloodMoon(player, splats)
        // GE51 Wiki "Dark bow": every attack fires two arrows, each rolled on its own; the second lands later.
        if (type.style == CombatStyle.RANGED && c.kit.has("dark bow") && CombatSystem.useAmmo(player, target, category)) {
            val second = Formulas.darkBowSecondDelay(distance)
            rangedVisuals(player, target, category, second)
            land(player, target, stance, type, PostRoll.ranged(player, target, c, attackRoll, defenceRoll, max, crossbow = false), second)
        }
    }

    /** Queues [splats] on [target] after [delay] ticks and grants the xp for their damage. */
    private fun land(player: PlayerRef, target: EntityRef, stance: Stance, type: AttackType, splats: List<PostRoll.Splat>, delay: Int) {
        var total = 0
        for ((i, s) in splats.withIndex()) {
            val damage = minOf(Formulas.DAMAGE_CAP, s.damage)
            total += damage
            CombatSystem.queueHit(target, player, damage, CombatSystem.hitStyle(type.style), delay + s.later) {
                s.onLand?.invoke()
                if (i == 0 && s.accurate) {
                    Toxic.onPlayerHit(player, target, type.style, damage)
                    SetEffects.onLanded(player, target, type.style, damage)
                }
            }
        }
        if (splats.any { it.accurate }) grantXp(player, stance, type.style, total)
    }

    /**
     * NPC ranged defence column for a weapon (Wiki "Ranged weapons": bows standard, crossbows and chinchompas heavy,
     * thrown weapons light; npc-combat.md N4).
     */
    fun rangedDefenceOf(category: WeaponCategory): Int =
        when (category) {
            Weapons.THROWN -> NpcCombatDef.DEF_LIGHT
            Weapons.CHINCHOMPA -> NpcCombatDef.DEF_HEAVY
            Weapons.CROSSBOW -> NpcCombatDef.DEF_HEAVY
            else -> NpcCombatDef.DEF_STANDARD
        }

    fun WeaponCategory.usesAmmo(): Boolean = this === Weapons.BOW || this === Weapons.CROSSBOW

    /**
     * The player's max attack roll against [target] (F5/F10/F14 with gear effects, gear-effects.md). Magic callers pass
     * the [spell] traits and the elemental [weakness] percent; [magicBonusMultiplier] is Tumeken's shadow's ×3.
     */
    fun attackRoll(
        player: PlayerRef,
        stance: Stance,
        type: AttackType,
        magicBonusMultiplier: Int = 1,
        target: EntityRef? = null,
        special: Boolean = false,
        spell: GearEffects.SpellInfo? = null,
        weakness: Int = 0,
    ): Int {
        val c = GearContext.ctx(player, target, type, special)
        return when (type.style) {
            CombatStyle.MELEE -> {
                val eff = Formulas.effectiveLevel(player.level(Stat.ATTACK), player.prayerPercent { it.attack }, stance.attackBonus)
                GearEffects.meleeAttackRoll(
                    c,
                    GearEffects.meleeAccuracyLevel(eff, c.kit),
                    GearContext.bonus(player, type.attackParam, c.kit),
                )
            }

            CombatStyle.RANGED -> {
                val eff = Formulas.effectiveLevel(player.level(Stat.RANGED), player.prayerPercent { it.rangedAttack }, stance.rangedBonus)
                GearEffects.rangedAttackRoll(
                    c,
                    GearEffects.rangedAccuracyLevel(eff, c.kit),
                    GearContext.bonus(player, Param.ATTACK_RANGED, c.kit),
                )
            }

            CombatStyle.MAGIC -> {
                val eff = Formulas.effectiveLevel(player.level(Stat.MAGIC), player.prayerPercent { it.magic }, stance.magicBonus)
                GearEffects.magicAttackRoll(
                    c,
                    GearEffects.magicAccuracyLevel(eff, c.kit),
                    player.bonus(Param.ATTACK_MAGIC) * magicBonusMultiplier,
                    spell,
                    weakness,
                    SpellEffects.marked(player),
                )
            }
        }
    }

    /**
     * F6/F7/F15/F16 defence roll of [target] against [type]. NPC defence bonuses come from the Wiki table
     * (npc-combat.md N3); [rangedDefence] picks the light/standard/heavy column for ranged attacks.
     */
    fun defenceRoll(target: EntityRef, type: AttackType, rangedDefence: Int = NpcCombatDef.DEF_STANDARD): Int =
        when (target) {
            is NpcRef -> {
                val level = if (type.style == CombatStyle.MAGIC) target.level(NpcStat.MAGIC) else target.level(NpcStat.DEFENCE)
                val column =
                    when (type) {
                        AttackType.STAB -> NpcCombatDef.DEF_STAB
                        AttackType.SLASH -> NpcCombatDef.DEF_SLASH
                        AttackType.CRUSH -> NpcCombatDef.DEF_CRUSH
                        AttackType.MAGIC -> NpcCombatDef.DEF_MAGIC
                        AttackType.RANGED -> rangedDefence
                    }
                Formulas.npcDefenceRoll(level, NpcCombatTable.of(target.type)?.defence?.get(column) ?: 0)
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
                        Formulas.effectiveLevel(
                            SetEffects.toragDefence(target, target.level(Stat.DEFENCE)),
                            target.prayerPercent { it.defence },
                            stance.defenceBonus,
                        )
                    }
                Formulas.roll(level, target.bonus(type.defenceParam))
            }
        }

    /** The melee / ranged max hit against [target] (F3/F9 with gear effects). */
    fun maxHit(player: PlayerRef, stance: Stance, type: AttackType, target: EntityRef? = null, special: Boolean = false): Int =
        minMaxHit(player, stance, type, target, special)[1]

    /** [min, max] of a melee / ranged hit (Osmumten's fang raises the min, gear-effects.md GE16). */
    fun minMaxHit(player: PlayerRef, stance: Stance, type: AttackType, target: EntityRef? = null, special: Boolean = false): IntArray {
        val c = GearContext.ctx(player, target, type, special)
        return when (type.style) {
            CombatStyle.MELEE -> {
                val level = player.level(Stat.STRENGTH)
                val prayed = Formulas.effectiveLevel(level, player.prayerPercent { it.strength }, 0, strength = true) - EFFECTIVE_BASE
                GearEffects.meleeMax(
                    c,
                    level,
                    prayed,
                    stance.strengthBonus,
                    GearContext.bonus(player, Param.MELEE_STRENGTH, c.kit),
                    player[SOULREAPER_STACKS] ?: 0,
                )
            }

            CombatStyle.RANGED -> {
                val atlatl = GearEffects.strengthScaledRanged(c.kit)
                val eff =
                    Formulas.effectiveLevel(
                        player.level(if (atlatl) Stat.STRENGTH else Stat.RANGED),
                        player.prayerPercent { it.rangedStrength },
                        stance.rangedBonus,
                        strength = true,
                    )
                GearEffects.rangedMax(c, eff, GearContext.bonus(player, if (atlatl) Param.MELEE_STRENGTH else Param.RANGED_STRENGTH, c.kit))
            }

            CombatStyle.MAGIC -> {
                intArrayOf(0, 0)
            }
        }
    }

    /** Soulreaper axe stacks (0..5, gear-effects.md GE18). */
    val SOULREAPER_STACKS = tp.plugin.AttrKey<Int>("soulreaper_stacks")

    /** combat-timeline.md per attack type. */
    fun hitDelay(style: CombatStyle, category: WeaponCategory, distance: Int): Int =
        when {
            style == CombatStyle.MELEE -> 0
            style == CombatStyle.MAGIC -> Formulas.magicDelay(distance)
            category === Weapons.THROWN || category === Weapons.CHINCHOMPA -> Formulas.thrownDelay(distance)
            else -> Formulas.bowDelay(distance)
        }

    /** CD-W2: the wielded weapon's anim for the current combat-tab slot (RS Mod obj data), else the category's. */
    private fun attackAnim(player: PlayerRef, category: WeaponCategory, type: AttackType): Int {
        val weapon = player.worn(Wear.WEAPON)
        val slot = player.varp(Varps.COM_MODE).coerceIn(0, SLOTS - 1)
        val own =
            if (weapon >=
                0
            ) {
                WeaponData.of(weapon)?.attackAnims?.let { it[slot].takeIf { a -> a >= 0 } ?: it.firstOrNull { a -> a >= 0 } }
            } else {
                null
            }
        return own ?: categoryAnim(category, type)
    }

    private fun categoryAnim(category: WeaponCategory, type: AttackType): Int =
        when {
            category === Weapons.UNARMED -> Seqs.HUMAN_UNARMEDPUNCH
            category === Weapons.BOW -> Seqs.HUMAN_BOW
            category === Weapons.CROSSBOW -> Seqs.HUMAN_CROSSBOW
            category === Weapons.STAB_SWORD -> if (type == AttackType.STAB) Seqs.HUMAN_DDAGGER_LUNGE else Seqs.HUMAN_DDAGGER_HACK
            category === Weapons.TWO_HANDED || category === Weapons.GODSWORD -> Seqs.HUMAN_DHSWORD_SLASH
            category === Weapons.STAFF -> Seqs.HUMAN_STAFF_POUND
            else -> Seqs.HUMAN_SWORD_SLASH
        }

    /**
     * CD-W4: the launch graphic on the player (height 96) and the projectile of the ammo, or of the thrown weapon
     * itself, in its shape (RS Mod `PvNCombat.attackRanged`).
     */
    fun rangedVisualsFor(
        player: PlayerRef,
        target: EntityRef,
        category: WeaponCategory,
        delay: Int,
    ): Unit = rangedVisuals(player, target, category, delay)

    private fun rangedVisuals(player: PlayerRef, target: EntityRef, category: WeaponCategory, delay: Int) {
        val thrown = category === Weapons.THROWN
        val source = if (thrown) player.worn(Wear.WEAPON) else player.worn(Wear.AMMO)
        val def = if (source >= 0) WeaponData.of(source) else null
        if (def != null && def.projLaunch >= 0) player.spotAnim(def.projLaunch, height = LAUNCH_HEIGHT)
        val travel =
            def?.projTravel?.takeIf { it >= 0 }
                ?: if (category === Weapons.CROSSBOW) SpotAnims.CROSSBOWBOLT_TRAVEL else SpotAnims.RUNE_ARROW_TRAVEL
        val shape =
            def?.projAnim?.let(ProjType::named) ?: when (category) {
                Weapons.CROSSBOW -> ProjType.BOLT
                Weapons.THROWN -> ProjType.THROWN
                else -> ProjType.ARROW
            }
        shape.launch(player, target, travel, delay)
    }

    /** Combat XP per damage (Combat Options CO:17-46): 4 per damage to the style's skill, 4/3 to Hitpoints. */
    @Suppress("MagicNumber") // xp rates
    fun grantXp(player: PlayerRef, stance: Stance, style: CombatStyle, damage: Int) {
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

    private const val SLOTS = 4

    /** RS Mod `PvNCombat`: the ranged launch spot anim at height 96. */
    private const val LAUNCH_HEIGHT = 96

    private const val EFFECTIVE_BASE = 8
}
