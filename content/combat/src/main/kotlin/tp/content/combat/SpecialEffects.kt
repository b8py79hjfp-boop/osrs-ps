@file:Suppress("ktlint:standard:max-line-length", "MaxLineLength") // per-special data rows read best on one line

package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Varps

/**
 * Special attack behaviour (docs/mechanics/special-attacks.md SP1-SP40; numbers from docs/research/special-attacks.tsv):
 * the generic roll with the row's multipliers, plus the per-special rules keyed by name — formula max hits, hit
 * patterns, multi-target shapes, stat effects and the buffs that need no target.
 */
@Suppress("LargeClass") // one table of per-special rules; split by kind would scatter each special's behaviour
internal object SpecialEffects {
    /** SP6: energy actually spent (Unleash takes every bit, at least 5%). */
    fun cost(player: PlayerRef, row: SpecialRow): Int =
        if (row.name ==
            "Unleash"
        ) {
            maxOf(UNLEASH_MIN, player.varp(Varps.SA_ENERGY))
        } else {
            row.energy
        }

    /** SP5: an instant attack special fires now at the current target, ignoring the attack clock. */
    fun instantAttack(player: PlayerRef) {
        val target = player.interactionTarget ?: return
        if (!CombatSystem.canFight(player, target, HitStyle.MELEE, quiet = true)) return
        Attacks.playerAttack(player, target)
    }

    /** One special attack from [player] at [target] (the energy is already spent). */
    @Suppress("CyclomaticComplexMethod") // style and visuals per special
    fun perform(player: PlayerRef, target: EntityRef, row: SpecialRow, stance: Stance, type: AttackType, category: WeaponCategory) {
        val energyUsed = if (row.name == "Unleash") player[UNLEASH_SPENT] ?: UNLEASH_MIN else row.energy
        player.setAttackClock(player.tick + if (row.instant) 0 else speed(player, row))
        val style =
            when (row.style) {
                SpecStyle.MAGIC -> CombatStyle.MAGIC
                SpecStyle.RANGED -> CombatStyle.RANGED
                else -> type.style
            }
        val attackType =
            when (style) {
                CombatStyle.MAGIC -> AttackType.MAGIC
                CombatStyle.RANGED -> AttackType.RANGED
                else -> type
            }
        if (style == CombatStyle.RANGED && with(Attacks) { category.usesAmmo() } && !CombatSystem.useAmmo(player)) {
            player.message("There is no ammo left in your quiver.")
            player.stopInteraction()
            return
        }
        if (row.anim >= 0) player.anim(row.anim)
        if (row.spot >= 0) player.spotAnim(row.spot, height = if (style == CombatStyle.MELEE) 0 else LAUNCH_HEIGHT)
        val delay = Attacks.hitDelay(style, category, player.distanceTo(target))
        if (row.projectile >= 0) {
            val shape =
                if (style ==
                    CombatStyle.MAGIC
                ) {
                    ProjType.MAGIC
                } else if (category === Weapons.CROSSBOW) {
                    ProjType.BOLT
                } else {
                    ProjType.ARROW
                }
            shape.launch(player, target, row.projectile, delay)
            if (row.hits >= 2 && style == CombatStyle.RANGED && row.name.startsWith("Descent")) {
                ProjType.DOUBLE_ARROW_TWO.launch(player, target, row.projectile, delay + 1)
            }
        } else if (style == CombatStyle.RANGED) {
            Attacks.rangedVisualsFor(player, target, category, delay)
        }
        for ((i, victim) in targets(player, target, row).withIndex()) {
            CombatSystem.announceAttack(player, victim, style)
            CombatVisuals.block(victim, delay)
            if (row.targetSpot >= 0) victim.spotAnim(row.targetSpot, delay = victim.ticksUntilHit(delay) * CYCLES_PER_TICK)
            strike(player, victim, row, stance, attackType, category, delay, energyUsed, i)
        }
    }

    /** SP7: attack speed of the special (Concentrated Shot +4, Soul Rend 5). */
    private fun speed(player: PlayerRef, row: SpecialRow): Int =
        when (row.name) {
            "Concentrated Shot" -> player.attackSpeed() + CONCENTRATED_EXTRA
            "Soul Rend" -> SOUL_REND_SPEED
            "Wrath of Amascut" -> WRATH_LOCK
            else -> player.attackSpeed()
        }

    /** SP8: multi-target specials hit everyone in their shape who is in multi-combat and attackable. */
    private fun targets(player: PlayerRef, primary: EntityRef, row: SpecialRow): List<EntityRef> {
        val (radius, cap, aroundSelf) =
            when (row.name) {
                "Powerstab" -> Triple(1, POWERSTAB_CAP, true)
                "Spear Wall" -> Triple(1, SPEAR_WALL_CAP, true)
                "Shield Bash" -> Triple(SHIELD_BASH_RADIUS, SHIELD_BASH_CAP, true)
                "Annihilate" -> Triple(1, ANNIHILATE_CAP, false)
                "Sweep" -> Triple(1, SWEEP_CAP, false)
                "Chainhit" -> Triple(CHAINHIT_RADIUS, CHAINHIT_CAP, false)
                else -> return listOf(primary)
            }
        val multiAt = player.worldAttr(SharedAttrs.MULTI_AREA)
        if (multiAt == null || !multiAt(primary.coord)) return listOf(primary)
        val centre = if (aroundSelf) player.coord else primary.coord
        val around: List<EntityRef> = player.npcsWithin(centre, radius) + player.playersAround(centre, radius)
        val out = arrayListOf(primary)
        for (other in around) {
            if (out.size >= cap) break
            if (other == primary || other == player || !multiAt(other.coord)) continue
            if (CombatSystem.canFight(player, other, quiet = true)) out += other
        }
        return out
    }

    private fun strike(
        player: PlayerRef,
        target: EntityRef,
        row: SpecialRow,
        stance: Stance,
        type: AttackType,
        category: WeaponCategory,
        delay: Int,
        energyUsed: Int,
        index: Int,
    ) {
        val style = type.style
        val defenceType = row.defenceType ?: type
        val accuracy = accuracyPermille(target, row, energyUsed)
        val sure = accuracy >= GUARANTEED
        val attackRoll = if (sure) 0 else (Attacks.attackRoll(player, stance, type).toLong() * accuracy / PERMILLE).toInt()
        var defenceRoll = Attacks.defenceRoll(target, defenceType, Attacks.rangedDefenceOf(category))
        if (row.name == "Feint") defenceRoll /= FEINT_DEFENCE_DIVISOR
        var max = baseMax(player, row, stance, type, energyUsed)
        if (row.name == "Annihilate" && index > 0) max = max * ANNIHILATE_FALLOFF / PERCENT
        val ignoresPrayer = row.name in IGNORES_PROTECTION
        if (target is PlayerRef && target.protects(style) && !ignoresPrayer) max = Formulas.protectedMaxHit(max)
        val rolls = hitsOf(player, row, sure, attackRoll, defenceRoll, max)
        var first = true
        for ((hit, damage) in rolls) {
            val dealt = minOf(Formulas.DAMAGE_CAP, damage)
            val landingHit = hit
            val thisDelay = if (first) delay else delay + secondDelay(row, target)
            CombatSystem.queueHit(target, player, dealt, CombatSystem.hitStyle(style), thisDelay) {
                onLand(player, target, row, landingHit, dealt)
                if (landingHit) Toxic.onPlayerHit(player, target, style, dealt)
            }
            if (dealt > 0) Attacks.grantXp(player, stance, style, dealt)
            first = false
        }
        // SP10: Saradomin's Lightning adds a magic hit of 1-16 when the melee part is accurate.
        if (row.name == "Saradomin's Lightning" && rolls.firstOrNull()?.first == true) {
            val bolt = 1 + player.random(SS_BOLT_MAX)
            CombatSystem.queueHit(target, player, bolt, HitStyle.MAGIC, delay)
            player.addXp(Stat.MAGIC, bolt * 2.0)
        }
        if (row.name == "Hammer Blow" && rolls.all { !it.first }) {
            CombatSystem.queueHit(target, player, HAMMER_FLAT, CombatSystem.hitStyle(style), delay)
        }
    }

    /** Puncture's second hit lands a tick later on NPCs (SP9). */
    private fun secondDelay(row: SpecialRow, target: EntityRef): Int = if (row.name == "Puncture" && target is NpcRef) 1 else 0

    /** SP9: the accuracy multiplier (permille), with the formula specials. */
    private fun accuracyPermille(target: EntityRef, row: SpecialRow, energyUsed: Int): Int =
        when (row.name) {
            "Unleash" -> PERMILLE + UNLEASH_ACC_STEP * (energyUsed / UNLEASH_STEP)
            "Backstab", "Snipe" -> if (target[Attr.LAST_HIT_BY] == null) GUARANTEED else PERMILLE
            else -> if (row.guaranteed) GUARANTEED else row.accuracyPermille
        }

    /** SP11: the max hit before protection prayers, with the specials whose numbers are formulas. */
    private fun baseMax(player: PlayerRef, row: SpecialRow, stance: Stance, type: AttackType, energyUsed: Int): Int {
        val normal = Attacks.maxHit(player, stance, type)
        return when (row.name) {
            "The Judgement", "Echo slash", "Power of the Gods" -> {
                normal * GODSWORD_BASE / PERMILLE * AGS_FACTOR / PERMILLE
            }

            "Warstrike" -> {
                normal * GODSWORD_BASE / PERMILLE * BGS_FACTOR / PERMILLE
            }

            "Powershot", "Soulshot", "Snapshot" -> {
                (player.level(Stat.RANGED) + OWN_FORMULA_BASE) *
                    (player.bonus(Param.RANGED_STRENGTH) + FORMULA_OFFSET) /
                    FORMULA_OFFSET
            }

            "Penance" -> {
                normal * (PERMILLE + PENANCE_STEP * (player.baseLevel(Stat.PRAYER) - player.level(Stat.PRAYER)).coerceAtLeast(0)) /
                    PERMILLE
            }

            "Unleash" -> {
                normal * (PERMILLE + UNLEASH_DMG_STEP * (energyUsed / UNLEASH_STEP)) / PERMILLE
            }

            "Immolate" -> {
                Formulas.magicMaxHit(
                    minOf(IMMOLATE_CAP, (LEVEL_99 + IMMOLATE_CAP * player.level(Stat.MAGIC)) / LEVEL_99),
                    magicDamage(player),
                )
            }

            "Invocate" -> {
                Formulas.magicMaxHit(
                    minOf(INVOCATE_CAP, (LEVEL_99 + INVOCATE_CAP * player.level(Stat.MAGIC)) / LEVEL_99),
                    magicDamage(player),
                )
            }

            "Condemn" -> {
                Formulas.magicMaxHit(maxOf(1, player.level(Stat.MAGIC) / 3 - ACCURSED_OFFSET), magicDamage(player)) *
                    row.damagePermille /
                    PERMILLE
            }

            "Soul Rend" -> {
                Formulas.magicMaxHit(maxOf(1, player.level(Stat.MAGIC) / 3 - AYAK_OFFSET), magicDamage(player)) *
                    row.damagePermille /
                    PERMILLE
            }

            "Swarm" -> {
                (normal * SWARM_PERCENT + PERCENT - 1) / PERCENT
            }

            else -> {
                normal * row.damagePermille / PERMILLE
            }
        }
    }

    private fun magicDamage(player: PlayerRef): Int = player.bonus(Param.MAGIC_DAMAGE) / MAGIC_DAMAGE_UNIT

    /** SP12: each hit as (accurate, damage), with the specials' own patterns. */
    @Suppress("CyclomaticComplexMethod") // one branch per hit pattern
    private fun hitsOf(
        player: PlayerRef,
        row: SpecialRow,
        guaranteed: Boolean,
        attackRoll: Int,
        defenceRoll: Int,
        max: Int,
    ): List<Pair<Boolean, Int>> {
        fun roll(): Boolean = guaranteed || Formulas.hits(attackRoll, defenceRoll, player::random)

        fun dmg(lo: Int, hi: Int): Int = if (hi <= lo) lo else lo + player.random(hi - lo + 1)
        return when (row.name) {
            "Slice and Dice" -> {
                claws(player, max) { guaranteed || roll() }
            }

            "Abyssal Puncture" -> {
                val hit = guaranteed || roll()
                List(2) { hit to if (hit) dmg(0, max * ABYSSAL_PUNCTURE / PERMILLE) else 0 }
            }

            "Descent of Darkness / Dragons", "Descent of Darkness" -> {
                val minimum = if (row.damagePermille >= DRAGON_ARROWS) DESCENT_DRAGON_MIN else DESCENT_MIN
                List(2) { minOf(DESCENT_CAP, maxOf(minimum, if (roll()) dmg(0, max) else 0)).let { true to it } }
            }

            "Feint", "Hamstring" -> {
                List(1) {
                    val hit = roll()
                    hit to
                        if (hit) dmg(max * FEINT_MIN / PERCENT, max * FEINT_MAX / PERCENT) else 0
                }
            }

            "Disrupt" -> {
                List(1) { true to dmg(max / 2, max * DISRUPT_MAX / PERCENT) }
            }

            "Seeking Lunge" -> {
                List(1) {
                    val hit = roll()
                    hit to if (hit) max * LUNGE_PERCENT / PERCENT + 1 else 0
                }
            }

            "Shove", "Celebrate" -> {
                List(1) { true to 0 }
            }

            else -> {
                List(maxOf(1, row.hits)) {
                    val hit = guaranteed || roll()
                    hit to if (hit) dmg(0, max) else 0
                }
            }
        }
    }

    /**
     * SP13 (Wiki "Dragon claws"): up to four accuracy rolls, stopping at the first success, whose position sets the
     * pattern; with no success the last two hits are 0-1. OPEN-SA-11 (the Wiki flags possible errors in its table).
     */
    private fun claws(player: PlayerRef, max: Int, roll: () -> Boolean): List<Pair<Boolean, Int>> {
        fun between(lo: Int, hi: Int): Int = if (hi <= lo) lo else lo + player.random(hi - lo + 1)
        val first = (1..CLAW_HITS).firstOrNull { roll() }
        val hits =
            when (first) {
                1 -> between(max / 2, max - 1).let { h1 -> intArrayOf(h1, h1 / 2, h1 / 4, h1 / 4 + 1) }
                2 -> between(max * 3 / 8, max * 7 / 8).let { h2 -> intArrayOf(0, h2, h2 / 2, h2 / 2 + 1) }
                3 -> between(max / 4, max * 3 / 4).let { h3 -> intArrayOf(0, 0, h3, h3 + 1) }
                4 -> between(max / 4, max * 5 / 4).let { h4 -> intArrayOf(0, 0, 0, h4) }
                else -> intArrayOf(0, 0, player.random(2), player.random(2))
            }
        return hits.mapIndexed { i, d -> (first != null && i >= first - 1) to d }
    }

    /** SP14-SP30: what lands with each hit. */
    @Suppress("CyclomaticComplexMethod", "LongMethod") // one branch per special
    private fun onLand(player: PlayerRef, target: EntityRef, row: SpecialRow, hit: Boolean, damage: Int) {
        when (row.name) {
            "Ice Cleave" -> {
                if (hit) Freezes.freeze(target, player, ZGS_FREEZE)
            }

            "Binding Tentacle" -> {
                Freezes.freeze(target, player, TENTACLE_BIND)
                if (player.random(2) == 0) Poison.poison(target, TENTACLE_POISON)
            }

            "Healing Blade" -> {
                if (hit) {
                    player.hitpoints = minOf(player.maxHitpoints, player.hitpoints + maxOf(SGS_MIN_HEAL, damage / 2))
                    restorePrayer(player, maxOf(SGS_MIN_PRAYER, damage / 4))
                }
            }

            "Toxic Siphon" -> {
                player.hitpoints = minOf(player.maxHitpoints, player.hitpoints + damage / 2)
            }

            "Warstrike" -> {
                if (hit) drainInOrder(target, damage, WARSTRIKE_ORDER)
            }

            "Smash" -> {
                if (damage > 0) lowerPercent(target, Stat.DEFENCE, NpcStat.DEFENCE, SMASH_PERCENT)
            }

            "Pulverize" -> {
                if (hit) lowerPercent(target, Stat.DEFENCE, NpcStat.DEFENCE, PULVERIZE_PERCENT)
            }

            "Weaken" -> {
                if (hit) {
                    val pct =
                        if ((target as? NpcRef)?.let { NpcCombatTable.of(it.type)?.attributes?.contains("demon") } ==
                            true
                        ) {
                            DEMON_WEAKEN
                        } else {
                            WEAKEN_PERCENT
                        }
                    for ((s, n) in listOf(
                        Stat.ATTACK to NpcStat.ATTACK,
                        Stat.STRENGTH to NpcStat.STRENGTH,
                        Stat.DEFENCE to NpcStat.DEFENCE,
                    )) {
                        lower(target, s, n) { base -> base * pct / PERCENT + 1 }
                    }
                }
            }

            "Backstab", "Snipe" -> {
                if (damage > 0 &&
                    !SpellCasting.lowered(target, Stat.DEFENCE, NpcStat.DEFENCE)
                ) {
                    lower(target, Stat.DEFENCE, NpcStat.DEFENCE) { damage }
                }
            }

            "Sunder" -> {
                if (hit) drainInOrder(target, damage / SUNDER_DIVISOR, SUNDER_ORDER, firstOnly = true)
            }

            "Soulshot" -> {
                if (damage > 0 &&
                    !SpellCasting.lowered(target, Stat.MAGIC, NpcStat.MAGIC)
                ) {
                    lower(target, Stat.MAGIC, NpcStat.MAGIC) { damage }
                }
            }

            "Condemn" -> {
                if (hit) {
                    for ((s, n) in listOf(
                        Stat.DEFENCE to NpcStat.DEFENCE,
                        Stat.MAGIC to NpcStat.MAGIC,
                    )) {
                        lowerTo(target, s, n, CONDEMN_PERCENT)
                    }
                }
            }

            "Favour of the War God" -> {
                if (damage > 0) {
                    if (target is PlayerRef) target.setLevel(Stat.PRAYER, target.level(Stat.PRAYER) - damage)
                    restorePrayer(player, damage)
                }
            }

            "Liquify" -> {
                if (damage > 0) {
                    for (s in intArrayOf(Stat.STRENGTH, Stat.ATTACK, Stat.DEFENCE)) {
                        val cap = player.baseLevel(s) + LIQUIFY_CAP_BASE + player.baseLevel(s) / TEN
                        player.setLevel(s, minOf(cap, player.level(s) + damage / LIQUIFY_DIVISOR))
                    }
                }
            }

            "Invocate" -> {
                restorePrayer(player, minOf(INVOCATE_PRAYER_CAP, damage / 2))
            }

            "Energy Drain" -> {
                if (hit && target is PlayerRef) {
                    val taken = target.runEnergy / TEN
                    target.runEnergy -= taken
                    player.runEnergy = minOf(RUN_ENERGY_MAX, player.runEnergy + taken)
                    target.message("You feel drained!")
                }
            }

            "Sever" -> {
                if (hit && target is PlayerRef) target.disableProtection(SEVER_TICKS)
            }

            "Impale" -> {
                player.setAttackClock(player.attackClock() + 1)
            }

            "Shove" -> {
                Freezes.freeze(target, player, SHOVE_STUN)
                if (target is PlayerRef) target.message("You've been stunned!")
            }

            "Bear Down", "Phantom Strike" -> {
                if (hit) bleed(player, target, if (row.name == "Bear Down") BEAR_DOWN_BLEED else damage)
            }

            "Flames of Ralos" -> {
                if (hit) bleed(player, target, RALOS_BURN)
            }
        }
    }

    /** SP27: damage over time as typeless hits of 5 every 2 ticks (OPEN-SA-20: cadence). */
    private fun bleed(player: PlayerRef, target: EntityRef, total: Int) {
        var left = total
        var at = BLEED_INTERVAL
        while (left > 0) {
            val part = minOf(BLEED_PART, left)
            CombatSystem.queueHit(target, player, part, HitStyle.TYPELESS, at)
            left -= part
            at += BLEED_INTERVAL
        }
    }

    private fun restorePrayer(player: PlayerRef, amount: Int) {
        player.setLevel(Stat.PRAYER, minOf(player.baseLevel(Stat.PRAYER) + amount.coerceAtLeast(0), player.level(Stat.PRAYER) + amount))
        if (player.level(Stat.PRAYER) > player.baseLevel(Stat.PRAYER)) player.setLevel(Stat.PRAYER, player.baseLevel(Stat.PRAYER))
    }

    private fun lower(target: EntityRef, stat: Int, npcStat: Int, amount: (Int) -> Int) {
        when (target) {
            is PlayerRef -> target.setLevel(stat, maxOf(0, target.level(stat) - amount(target.baseLevel(stat))))
            is NpcRef -> if (npcStat >= 0) target.setLevel(npcStat, maxOf(0, target.level(npcStat) - amount(target.baseLevel(npcStat))))
        }
    }

    /** Lowers by [percent] of the current level, compounding (Smash, Pulverize). */
    private fun lowerPercent(target: EntityRef, stat: Int, npcStat: Int, percent: Int) {
        when (target) {
            is PlayerRef -> target.setLevel(stat, target.level(stat) - target.level(stat) * percent / PERCENT)
            is NpcRef -> target.setLevel(npcStat, target.level(npcStat) - target.level(npcStat) * percent / PERCENT)
        }
    }

    /** Lowers to at most [percent] below base, without stacking (Condemn). */
    private fun lowerTo(target: EntityRef, stat: Int, npcStat: Int, percent: Int) {
        when (target) {
            is PlayerRef -> {
                target.setLevel(
                    stat,
                    minOf(
                        target.level(stat),
                        target.baseLevel(stat) - target.baseLevel(stat) * percent / PERCENT,
                    ),
                )
            }

            is NpcRef -> {
                target.setLevel(
                    npcStat,
                    minOf(
                        target.level(npcStat),
                        target.baseLevel(npcStat) - target.baseLevel(npcStat) * percent / PERCENT,
                    ),
                )
            }
        }
    }

    /** Warstrike / Sunder: [amount] taken from the stats in [order], spilling to the next while there is some left. */
    private fun drainInOrder(target: EntityRef, amount: Int, order: List<Pair<Int, Int>>, firstOnly: Boolean = false) {
        var left = amount
        for ((stat, npcStat) in order) {
            if (left <= 0) return
            val current =
                when (target) {
                    is PlayerRef -> target.level(stat)
                    is NpcRef -> if (npcStat >= 0) target.level(npcStat) else 0
                }
            if (current <= 0) continue
            val taken = minOf(current, left)
            lower(target, stat, npcStat) { taken }
            left -= taken
            if (firstOnly) return
        }
    }

    /** SP31-SP35: the buff specials (no target); false if nothing happened. */
    fun buff(player: PlayerRef, row: SpecialRow): Boolean {
        if (row.anim >= 0) player.anim(row.anim)
        if (row.spot >= 0) player.spotAnim(row.spot)
        when (row.name) {
            "Sanctuary" -> {
                boost(player, Stat.DEFENCE, SANCTUARY_BOOST)
            }

            "Rampage" -> {
                var drained = 0
                for (s in intArrayOf(Stat.ATTACK, Stat.DEFENCE, Stat.RANGED, Stat.MAGIC)) {
                    val d = player.level(s) / TEN
                    player.setLevel(s, player.level(s) - d)
                    drained += d
                }
                boost(player, Stat.STRENGTH, RAMPAGE_BASE + drained / RAMPAGE_DIVISOR)
                player.say("Raarrrrrgggggghhhhhhh!")
            }

            "Lumber Up" -> {
                skillBoost(player, Stats.WOODCUTTING, "Chop chop!")
            }

            "Rock Knocker" -> {
                skillBoost(player, Stats.MINING, "Smashing!")
            }

            "Fishstabber" -> {
                skillBoost(player, Stats.FISHING, "Here fishy fishies!")
            }

            "Power of Death" -> {
                player[POWER_OF_DEATH_UNTIL] = player.tick + POWER_OF_DEATH_TICKS
            }

            else -> {
                player.message("This special attack is not available here.") // OPEN-SA: Leagues / Deadman buffs
                return false
            }
        }
        return true
    }

    private fun boost(player: PlayerRef, stat: Int, amount: Int) {
        player.setLevel(stat, maxOf(player.level(stat), minOf(player.baseLevel(stat) + amount, player.level(stat) + amount)))
    }

    private fun skillBoost(player: PlayerRef, stat: Int, shout: String) {
        boost(player, stat, SKILL_BOOST)
        player.say(shout)
    }

    /** SP33 (Wiki "Staff of the dead"): melee damage taken is halved while Power of Death lasts. */
    fun incoming(target: EntityRef, damage: Int, style: HitStyle): Int {
        if (target !is PlayerRef || style != HitStyle.MELEE) return damage
        val until = target[POWER_OF_DEATH_UNTIL] ?: return damage
        if (until <= target.tick) return damage
        return damage / 2
    }

    val POWER_OF_DEATH_UNTIL = tp.plugin.AttrKey<Long>("spec_power_of_death_until")
    val UNLEASH_SPENT = tp.plugin.AttrKey<Int>("spec_unleash_spent")

    private object Stats {
        const val WOODCUTTING = 8
        const val FISHING = 10
        const val MINING = 14
    }

    private val IGNORES_PROTECTION = setOf("Favour of the War God", "Wild Stab", "Unleash")
    private val WARSTRIKE_ORDER =
        listOf(
            Stat.DEFENCE to NpcStat.DEFENCE,
            Stat.STRENGTH to NpcStat.STRENGTH,
            Stat.PRAYER to -1,
            Stat.ATTACK to NpcStat.ATTACK,
            Stat.MAGIC to NpcStat.MAGIC,
            Stat.RANGED to NpcStat.RANGED,
        )
    private val SUNDER_ORDER =
        listOf(
            Stat.DEFENCE to NpcStat.DEFENCE,
            Stat.ATTACK to NpcStat.ATTACK,
            Stat.RANGED to NpcStat.RANGED,
            Stat.MAGIC to NpcStat.MAGIC,
        )

    private const val PERMILLE = 1000

    /** Magic damage param unit (OPEN-D1). */
    private const val MAGIC_DAMAGE_UNIT = 10
    private const val PERCENT = 100
    private const val TEN = 10
    private const val CYCLES_PER_TICK = 30
    private const val LAUNCH_HEIGHT = 96

    /** A multiplier large enough that every roll succeeds. */
    private const val GUARANTEED = 1_000_000

    private const val GODSWORD_BASE = 1100
    private const val AGS_FACTOR = 1250
    private const val BGS_FACTOR = 1100
    private const val OWN_FORMULA_BASE = 10
    private const val FORMULA_OFFSET = 64
    private const val PENANCE_STEP = 5
    private const val UNLEASH_MIN = 50
    private const val UNLEASH_STEP = 50
    private const val UNLEASH_ACC_STEP = 50
    private const val UNLEASH_DMG_STEP = 25
    private const val IMMOLATE_CAP = 58
    private const val INVOCATE_CAP = 44
    private const val INVOCATE_PRAYER_CAP = 120
    private const val LEVEL_99 = 99
    private const val ACCURSED_OFFSET = 6
    private const val AYAK_OFFSET = 6
    private const val SWARM_PERCENT = 40
    private const val ABYSSAL_PUNCTURE = 850
    private const val DRAGON_ARROWS = 1500
    private const val DESCENT_MIN = 5
    private const val DESCENT_DRAGON_MIN = 8
    private const val DESCENT_CAP = 48
    private const val FEINT_MIN = 20
    private const val FEINT_MAX = 120
    private const val FEINT_DEFENCE_DIVISOR = 4
    private const val DISRUPT_MAX = 150
    private const val LUNGE_PERCENT = 70
    private const val CLAW_HITS = 4
    private const val SS_BOLT_MAX = 16
    private const val HAMMER_FLAT = 5
    private const val ZGS_FREEZE = 32
    private const val TENTACLE_BIND = 8
    private const val TENTACLE_POISON = 4
    private const val SGS_MIN_HEAL = 10
    private const val SGS_MIN_PRAYER = 5
    private const val SMASH_PERCENT = 30
    private const val PULVERIZE_PERCENT = 35
    private const val WEAKEN_PERCENT = 5
    private const val DEMON_WEAKEN = 10
    private const val SUNDER_DIVISOR = 10
    private const val CONDEMN_PERCENT = 15
    private const val LIQUIFY_CAP_BASE = 3
    private const val LIQUIFY_DIVISOR = 4
    private const val RUN_ENERGY_MAX = 10_000
    private const val SEVER_TICKS = 8
    private const val SHOVE_STUN = 5
    private const val BEAR_DOWN_BLEED = 20
    private const val RALOS_BURN = 10
    private const val BLEED_INTERVAL = 2
    private const val BLEED_PART = 5
    private const val SANCTUARY_BOOST = 8
    private const val RAMPAGE_BASE = 10
    private const val RAMPAGE_DIVISOR = 4
    private const val SKILL_BOOST = 3
    private const val POWER_OF_DEATH_TICKS = 100L
    private const val CONCENTRATED_EXTRA = 4
    private const val SOUL_REND_SPEED = 5
    private const val WRATH_LOCK = 8
    private const val POWERSTAB_CAP = 15
    private const val SPEAR_WALL_CAP = 17
    private const val SHIELD_BASH_RADIUS = 5
    private const val SHIELD_BASH_CAP = 11
    private const val ANNIHILATE_CAP = 9
    private const val ANNIHILATE_FALLOFF = 80
    private const val SWEEP_CAP = 11
    private const val CHAINHIT_RADIUS = 3
    private const val CHAINHIT_CAP = 5
}
