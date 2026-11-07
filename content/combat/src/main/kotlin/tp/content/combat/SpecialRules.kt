package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Objs
import tp.symbols.Varbits

/**
 * Per-special rules beyond a [SpecialRow] (docs/mechanics/special-attacks.md SP41-SP70; each rule's Wiki page named
 * there): readiness checks for specials that need a set or a resource instead of energy, accuracy / max hit formulas,
 * hit patterns and what happens after the hit. [SpecialEffects] asks these first and falls back to the row.
 */
@Suppress("MagicNumber", "CyclomaticComplexMethod") // special-attacks.md rule tables: the Wiki's numbers
internal object SpecialRules {
    // ---- before the energy is spent

    /** False (with the game's message) when the special can't be used now. */
    fun ready(player: PlayerRef, row: SpecialRow): Boolean =
        when (row.name) {
            "Behead" -> {
                stacks(player) > 0 || refuse(player, "You need soul stacks to use this special attack.")
            }

            "Shield Slam" -> {
                Shields.rondacheCharges(player) >= Shields.SLAM_MIN ||
                    refuse(player, "Your rondache needs at least 10 charges.")
            }

            "Blood infusion" -> {
                wearsAll(player, BLOOD_MOON) || refuse(player, SET_MESSAGE.format("Blood Moon"))
            }

            "Break Shackles" -> {
                wearsAll(player, BLUE_MOON) || refuse(player, SET_MESSAGE.format("Blue Moon"))
            }

            "Eclipse" -> {
                wearsAll(player, ECLIPSE_MOON) || refuse(player, SET_MESSAGE.format("Eclipse Moon"))
            }

            "Scatter ashes" -> {
                player.varbit(Varbits.SPELLBOOK) == SpellBook.ARCEUUS.varbit ||
                    refuse(player, "You need to be on the Arceuus spellbook to do that.")
            }

            "Descent of Darkness / Descent of Dragons", "Descent of Darkness" -> {
                val ammo = player.worn(Wear.AMMO)
                (ammo >= 0 && player.inv(tp.symbols.Invs.WORN).count(Wear.AMMO) > 1) ||
                    refuse(player, "You need at least two arrows in your quiver to use this special attack.")
            }

            else -> {
                true
            }
        }

    private fun refuse(player: PlayerRef, message: String): Boolean {
        player.message(message)
        return false
    }

    private fun wearsAll(player: PlayerRef, pieces: Array<String>): Boolean = GearContext.kit(player).all(*pieces)

    fun stacks(player: PlayerRef): Int = player[Attacks.SOULREAPER_STACKS] ?: 0

    /** Whether the target may be hit by this special at all; false refunds (Scorching shackles vs non-demons). */
    fun targetAllowed(player: PlayerRef, target: EntityRef, row: SpecialRow): Boolean {
        // Wiki "Zorya's tome": like other powered staves it can't be used against players.
        if (row.name == "Zorya's flow" && target is PlayerRef) {
            player.message("You can't use this weapon's spell on other players.")
            return false
        }
        if (row.name != "Scorching shackles") return true
        val demon = (target as? NpcRef)?.let { NpcCombatTable.of(it.type)?.attributes?.contains(GearEffects.DEMON) } == true
        if (!demon) player.message("Scorching shackles won't work against a non-demon enemy.")
        return demon
    }

    // ---- speed, accuracy, max hit

    /** SP42 Wiki "Dragon longsword (bh)": its special is a tick faster. */
    fun speed(row: SpecialRow, normal: Int): Int = if (row.obj == Objs.BH_DRAGON_LONGSWORD_IMBUE) normal - 1 else normal

    /** The accuracy multiplier (permille) for rule specials; [permille] otherwise. */
    fun accuracy(player: PlayerRef, target: EntityRef, row: SpecialRow, permille: Int): Int =
        when (row.name) {
            // SP43 Wiki "Soulreaper axe": +12% accuracy per stack consumed.
            "Behead" -> {
                PERMILLE + 120 * stacks(player)
            }

            // SP45 Wiki "Crimson kisten": no accuracy bonus; four separate rolls (see [hits]).
            "Brutal Swing" -> {
                PERMILLE
            }

            // SP46 Wiki "Blue moon spear": +1.5% accuracy per tick of bind removed, uncapped.
            "Break Shackles" -> {
                PERMILLE + 15 * bindTicks(target)
            }

            // SP50 Wiki "Zorya's tome": with stored buffed attacks the bonuses add (+300%).
            "Zorya's flow" -> {
                if (Zorya.charges(player) > 0) permille + 1500 else permille
            }

            // SP53 Wiki "Rondache": +6 crush accuracy per charge consumed on top of the worn bonus.
            "Shield Slam" -> {
                val bonus = GearContext.bonus(player, Param.ATTACK_CRUSH)
                PERMILLE * (bonus + 6 * Shields.rondacheCharges(player) + 64) / (bonus + 64)
            }

            else -> {
                permille
            }
        }

    /** SP47 Wiki "Dragon mace (bh)": Shatter rolls against 60% of the crush defence. */
    fun defence(row: SpecialRow, defenceRoll: Int): Int = if (row.obj == Objs.BH_DRAGON_MACE_IMBUE) defenceRoll * 60 / 100 else defenceRoll

    /**
     * [min, max] for rule specials from the normal (special = true) [range]; null = the row's damage multiplier on
     * the normal max. Magic / atlatl specials compute their own normal hit.
     */
    fun range(player: PlayerRef, target: EntityRef, row: SpecialRow, stance: Stance, range: IntArray): IntArray? =
        when (row.name) {
            "Behead" -> {
                val s = stacks(player)
                intArrayOf(range[1] * 6 * s / 100, range[1] * (100 + 6 * s) / 100)
            }

            "Break Shackles" -> {
                intArrayOf(0, range[1] * (PERMILLE + minOf(BREAK_DAMAGE_CAP, 15 * bindTicks(target))) / PERMILLE)
            }

            "Blood infusion" -> {
                intArrayOf(0, range[1] * 125 / 100)
            }

            "Descent of Darkness / Descent of Dragons", "Descent of Darkness" -> {
                val dragon = dragonArrows(player)
                val bh = row.name == "Descent of Darkness"
                val min = if (bh) (if (dragon) 10 else 7) else (if (dragon) 8 else 5)
                intArrayOf(min, range[1] * (if (dragon) 150 else 130) / 100)
            }

            "Zorya's flow" -> {
                val max = PoweredStaves.magicMax(player, target)
                val bonus = if (Zorya.charges(player) > 0) 220 else 160
                intArrayOf(0, max * bonus / 100)
            }

            "Eclipse" -> {
                // SP51 Wiki "Eclipse atlatl": the remaining burn damage adds to the max (half to the min), at most 50.
                val atlatl = Attacks.minMaxHit(player, stance, AttackType.RANGED, target, special = true)
                val burn = minOf(ECLIPSE_BURN_CAP, Burns.consume(target))
                intArrayOf(burn / 2, atlatl[1] + burn)
            }

            "Pulsate" -> {
                // SP52 Wiki "Dawnbringer": 75-150, regardless of gear, prayer and level.
                intArrayOf(75, 150)
            }

            "Shield Slam" -> {
                // SP53 Wiki "Rondache": flat x + x% for x charges consumed.
                val x = Shields.rondacheCharges(player)
                intArrayOf(x + x * x / 100, x + x * x / 100)
            }

            else -> {
                null
            }
        }

    fun dragonArrows(player: PlayerRef): Boolean = player.objName(player.worn(Wear.AMMO)).lowercase().startsWith("dragon arrow")

    // ---- hit patterns

    /** Each hit as (accurate, damage) for rule specials; null = the generic pattern. */
    fun hits(
        player: PlayerRef,
        target: EntityRef,
        row: SpecialRow,
        attackRoll: Int,
        defenceRoll: Int,
        min: Int,
        max: Int,
    ): List<Pair<Boolean, Int>>? {
        fun roll(def: Int = defenceRoll): Boolean = Formulas.hits(attackRoll, def, player::random)

        fun dmg(lo: Int, hi: Int): Int = if (hi <= lo) lo else lo + player.random(hi - lo + 1)
        return when (row.name) {
            "Brutal Swing" -> {
                // SP45: n of four rolls succeed -> 70+20(n-1)% .. 110+20(n-1)% of max, one hitsplat.
                val n = (1..4).count { roll() }
                listOf(if (n == 0) false to 0 else true to dmg(max * (70 + 20 * (n - 1)) / 100, max * (110 + 20 * (n - 1)) / 100))
            }

            "Blood infusion" -> {
                // SP44 Wiki "Dual macuahuitl": the boosted max splits ⌊half⌋ + rest; each half rolls on its own.
                val first = max / 2
                listOf(first, max - first).map { m -> roll().let { it to if (it) dmg(0, m) else 0 } }
            }

            "Burning barrage" -> {
                burningClaws(player, target, max) { roll() }
            }

            "Division" -> {
                // SP48 Wiki "Tonalztics of Ralos": each successful hit lowers Defence by the target's Magic / 8;
                // against NPCs the first hit's drain counts for the second roll (not in PvP).
                val count = if (GearContext.kit(player).has("tonalztics of ralos")) 2 else 1
                var def = defenceRoll
                (1..count).map {
                    val hit = roll(def)
                    if (hit) {
                        divide(target)
                        if (target is NpcRef) def = Attacks.defenceRoll(target, AttackType.RANGED, NpcCombatDef.DEF_LIGHT)
                    }
                    hit to if (hit) dmg(min, max) else 0
                }
            }

            "Pulsate", "Shield Slam" -> {
                listOf(true to dmg(min, max))
            }

            "Descent of Darkness / Descent of Dragons", "Descent of Darkness" -> {
                // SP54 Wiki "Dark bow": each arrow deals at least the minimum (a miss too), at most 48; the (bh) bow rolls
                // from the minimum.
                val bh = row.name == "Descent of Darkness"
                List(2) {
                    val hit = roll()
                    true to minOf(DESCENT_CAP, maxOf(min, if (hit) dmg(if (bh) min else 0, max) else 0))
                }
            }

            else -> {
                null
            }
        }
    }

    /**
     * SP49 Wiki "Burning claws": up to three accuracy rolls, stopping at the first success. First: 75-175% of max split
     * 25/25/50, 15% burn chance per splat; second: 50-150% split 50/50/0 then -1, -1, +2, 30%; third: 25-125% all in the
     * last splat then +1, +1, -2, 45%; none: 0, 1 or 2 in total (20/40/40%).
     */
    private fun burningClaws(player: PlayerRef, target: EntityRef, max: Int, roll: () -> Boolean): List<Pair<Boolean, Int>> {
        fun between(lo: Int, hi: Int): Int = if (hi <= lo) lo else lo + player.random(hi - lo + 1)
        val first = (1..3).firstOrNull { roll() }
        val (splats, burnPercent) =
            when (first) {
                1 -> between(max * 75 / 100, max * 175 / 100).let { t -> intArrayOf(t / 4, t / 4, t - 2 * (t / 4)) to 15 }
                2 -> between(max * 50 / 100, max * 150 / 100).let { t -> intArrayOf(t / 2 - 1, t - t / 2 - 1, 2) to 30 }
                3 -> between(max * 25 / 100, max * 125 / 100).let { t -> intArrayOf(1, 1, t - 2) to 45 }
                else -> intArrayOf(0, 0, listOf(0, 1, 1, 2, 2)[player.random(5)]) to 0
            }
        for (d in splats) if (d > 0 && burnPercent > 0 && player.random(100) < burnPercent) Burns.burn(target, player)
        return splats.map { (first != null) to maxOf(0, it) }
    }

    private fun divide(target: EntityRef) {
        val magic = if (target is NpcRef) target.level(NpcStat.MAGIC) else (target as PlayerRef).level(Stat.MAGIC)
        when (target) {
            is NpcRef -> target.setLevel(NpcStat.DEFENCE, target.level(NpcStat.DEFENCE) - magic / 8)
            is PlayerRef -> target.setLevel(Stat.DEFENCE, maxOf(0, target.level(Stat.DEFENCE) - magic / 8))
        }
    }

    // ---- after the hit

    /** What happens once the special's hits are rolled ([rolls]: accurate, damage), with the landing [delay]. */
    fun after(player: PlayerRef, target: EntityRef, row: SpecialRow, rolls: List<Pair<Boolean, Int>>, delay: Int) {
        val hit = rolls.any { it.first }
        when (row.name) {
            "Behead" -> {
                player[Attacks.SOULREAPER_STACKS] = null
            }

            "Blood infusion" -> {
                // SP44: costs ⌊25%⌋ of current Hitpoints, hit or miss; Bloodrager is guaranteed if either splat hits.
                player.hitpoints -= player.hitpoints / 4
                if (hit) player.setAttackClock(player.attackClock() - 1)
            }

            "Blood Sacrifice" -> {
                if (rolls.firstOrNull()?.first == true) bloodSacrifice(player, target, delay)
            }

            "Break Shackles" -> {
                target.frozenUntil = target.tick
            }

            "Scorching shackles" -> {
                // SP55 Wiki "Scorching bow": hit or miss, demons are bound 20 ticks and burn 5 (1 now, 1 every 4 ticks).
                target.frozenUntil = maxOf(target.frozenUntil, target.tick + SHACKLE_TICKS)
                Burns.burn(target, player, total = SHACKLE_BURN, now = true)
            }

            "Zorya's flow" -> {
                if (hit) Zorya.addCharges(player)
            }

            "Shield Slam" -> {
                Shields.useRondache(player)
            }
        }
    }

    /**
     * SP56 Wiki "Ancient godsword": 8 ticks after a successful Blood Sacrifice, a target that hasn't moved 5 tiles away
     * takes 25 typeless damage; the attacker heals that much, at most 15% of the target's max Hitpoints, capped at 25
     * (15 against players; OPEN-SP12: 14 in the changelog).
     */
    private fun bloodSacrifice(player: PlayerRef, target: EntityRef, delay: Int) {
        target.queueHit(delay) {
            val at = target.coord
            target.queueHit(SACRIFICE_TICKS) {
                if (target.isDying) return@queueHit
                val moved = maxOf(kotlin.math.abs(target.coord.x - at.x), kotlin.math.abs(target.coord.z - at.z))
                if (moved >= SACRIFICE_ESCAPE || target.coord.level != at.level) return@queueHit
                val dealt = minOf(SACRIFICE_DAMAGE, target.hitpoints)
                CombatSystem.applyHit(target, player, SACRIFICE_DAMAGE, HitStyle.TYPELESS)
                val cap = minOf(target.maxHitpoints * 15 / 100, if (target is PlayerRef) 15 else 25)
                player.hitpoints = minOf(player.maxHitpoints, player.hitpoints + minOf(dealt, cap))
            }
        }
    }

    private fun bindTicks(target: EntityRef): Int = maxOf(0L, target.frozenUntil - target.tick).toInt()

    private const val PERMILLE = 1000
    private const val BREAK_DAMAGE_CAP = 1125
    private const val ECLIPSE_BURN_CAP = 50
    private const val DESCENT_CAP = 48
    private const val SHACKLE_TICKS = 20
    private const val SHACKLE_BURN = 5
    private const val SACRIFICE_TICKS = 8
    private const val SACRIFICE_ESCAPE = 5
    private const val SACRIFICE_DAMAGE = 25
    private const val SET_MESSAGE = "You need to wear the full %s set to use this special attack."

    private val BLOOD_MOON = arrayOf("dual macuahuitl", "blood moon helm", "blood moon chestplate", "blood moon tassets")
    private val BLUE_MOON = arrayOf("blue moon spear", "blue moon helm", "blue moon chestplate", "blue moon tassets")
    private val ECLIPSE_MOON = arrayOf("eclipse atlatl", "eclipse moon helm", "eclipse moon chestplate", "eclipse moon tassets")
}

/**
 * SP50 Wiki "Zorya's tome": a successful Zorya's flow stores 3 buffed attacks (at most 5): +150% accuracy, +60% damage
 * and a 2-tick attack. They last 30 seconds, each buffed attack restarting the timer; a teleport clears them.
 */
internal object Zorya {
    private val CHARGES = AttrKey<Int>("zorya_buffed_attacks")
    private val EXPIRY = TimerId("zorya_buff_expiry")

    fun charges(player: PlayerRef): Int = player[CHARGES] ?: 0

    fun addCharges(player: PlayerRef) {
        player[CHARGES] = minOf(MAX, charges(player) + ADD)
        arm(player)
    }

    /** Uses one buffed attack: true when the attack is buffed. */
    fun use(player: PlayerRef): Boolean {
        val n = charges(player)
        if (n <= 0) return false
        player[CHARGES] = (n - 1).takeIf { it > 0 }
        arm(player)
        return true
    }

    private fun arm(player: PlayerRef) {
        player.timer(EXPIRY, TICKS, soft = true) {
            this[CHARGES] = null
            stopTimer(EXPIRY)
        }
    }

    private const val ADD = 3
    private const val MAX = 5
    private const val TICKS = 50
}
