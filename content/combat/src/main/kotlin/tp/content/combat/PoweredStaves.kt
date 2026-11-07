package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * Powered staves (docs/mechanics/combat-magic.md CM20-CM23; OSRS Wiki "Powered staff" and each staff's page): the
 * weapon casts its own spell on every attack, using one charge; charges are bought with runes used on the staff and
 * kept per player and staff (OPEN-CM4). Uncharged, the staff swaps to its uncharged obj.
 */
internal object PoweredStaves {
    enum class Effect { NONE, VENOM, SANGUINE, SHADOW }

    class Staff(
        val family: String,
        val charged: IntArray,
        val uncharged: Int,
        val capacity: Long,
        val speed: Int,
        val runes: List<Pair<Int, Int>>,
        val max: (Int) -> Int,
        val castAnim: Int,
        val castSpot: Int,
        val projectile: Int,
        val impact: Int,
        val effect: Effect = Effect.NONE,
        /** Wiki "Powered staff": only Thammaron's and the accursed sceptre may be used on players. */
        val onPlayers: Boolean = false,
    )

    @Suppress("MagicNumber")
    private val STAVES: List<Staff> =
        listOf(
            // Wiki "Trident of the seas": ⌊Magic / 3⌋ - 5 (at least 1); death 1 + chaos 1 + fire 5 + 10 coins; 2,500 ((e) 20,000).
            Staff(
                "trident_seas",
                intArrayOf(Objs.TOTS_CHARGED, Objs.TOTS),
                Objs.TOTS_UNCHARGED,
                2_500,
                4,
                listOf(Objs.DEATHRUNE to 1, Objs.CHAOSRUNE to 1, Objs.FIRERUNE to 5, Objs.COINS to 10),
                { maxOf(1, it / 3 - 5) },
                -1,
                SpotAnims.SLAYER_TOTS_CASTING,
                SpotAnims.SLAYER_TOTS_PROJECTILE,
                SpotAnims.SLAYER_TOTS_IMPACT,
            ),
            Staff(
                "trident_seas_e",
                intArrayOf(Objs.TOTS_I_CHARGED),
                Objs.TOTS_I_UNCHARGED,
                20_000,
                4,
                listOf(Objs.DEATHRUNE to 1, Objs.CHAOSRUNE to 1, Objs.FIRERUNE to 5, Objs.COINS to 10),
                { maxOf(1, it / 3 - 5) },
                -1,
                SpotAnims.SLAYER_TOTS_CASTING,
                SpotAnims.SLAYER_TOTS_PROJECTILE,
                SpotAnims.SLAYER_TOTS_IMPACT,
            ),
            // Wiki "Trident of the swamp": ⌊Magic / 3⌋ - 2; + 1 Zulrah's scale per charge; 25% venom on a successful hit.
            Staff(
                "trident_swamp",
                intArrayOf(Objs.TOXIC_TOTS_CHARGED),
                Objs.TOXIC_TOTS_UNCHARGED,
                2_500,
                4,
                listOf(Objs.DEATHRUNE to 1, Objs.CHAOSRUNE to 1, Objs.FIRERUNE to 5, Objs.SNAKEBOSS_SCALE to 1),
                { maxOf(1, it / 3 - 2) },
                -1,
                SpotAnims.TOXIC_TOTS_CASTING,
                SpotAnims.TOXIC_TOTS_PROJECTILE,
                SpotAnims.TOXIC_TOTS_IMPACT,
                Effect.VENOM,
            ),
            Staff(
                "trident_swamp_e",
                intArrayOf(Objs.TOXIC_TOTS_I_CHARGED),
                Objs.TOXIC_TOTS_I_UNCHARGED,
                20_000,
                4,
                listOf(Objs.DEATHRUNE to 1, Objs.CHAOSRUNE to 1, Objs.FIRERUNE to 5, Objs.SNAKEBOSS_SCALE to 1),
                { maxOf(1, it / 3 - 2) },
                -1,
                SpotAnims.TOXIC_TOTS_CASTING,
                SpotAnims.TOXIC_TOTS_PROJECTILE,
                SpotAnims.TOXIC_TOTS_IMPACT,
                Effect.VENOM,
            ),
            // Wiki "Sanguinesti staff": ⌊Magic / 3⌋ (at least 6); blood 2; 20,000; 1/5 on a hit: +8 damage, heal half.
            Staff(
                "sanguinesti",
                intArrayOf(Objs.SANGUINESTI_STAFF, Objs.SANGUINESTI_STAFF_OR),
                Objs.SANGUINESTI_STAFF_UNCHARGED,
                20_000,
                4,
                listOf(Objs.BLOODRUNE to 2),
                { maxOf(6, it / 3) },
                -1,
                SpotAnims.SANGUINESTI_STAFF_CASTING,
                SpotAnims.SANGUINESTI_STAFF_TRAVEL,
                SpotAnims.SANGUINESTI_STAFF_IMPACT,
                Effect.SANGUINE,
            ),
            // Wiki "Tumeken's shadow": ⌊Magic / 3 + 1⌋, speed 5; soul 2 + chaos 5; 20,000; gear magic attack and damage × 3.
            Staff(
                "tumekens_shadow",
                intArrayOf(Objs.TUMEKENS_SHADOW),
                Objs.TUMEKENS_SHADOW_UNCHARGED,
                20_000,
                5,
                listOf(Objs.SOULRUNE to 2, Objs.CHAOSRUNE to 5),
                { it / 3 + 1 },
                Seqs.TOA_SOT_CAST_B,
                SpotAnims.TUMEKENS_SHADOW_CASTING,
                SpotAnims.TUMEKENS_SHADOW_TRAVEL,
                SpotAnims.TUMEKENS_SHADOW_IMPACT,
                Effect.SHADOW,
            ),
            // Wiki "Warped sceptre": ⌊(8 × Magic + 96) / 37⌋; chaos 2 + earth 5; 20,000.
            Staff(
                "warped_sceptre",
                intArrayOf(Objs.WARPED_SCEPTRE),
                Objs.WARPED_SCEPTRE_UNCHARGED,
                20_000,
                4,
                listOf(Objs.CHAOSRUNE to 2, Objs.EARTHRUNE to 5),
                { (8 * it + 96) / 37 },
                -1,
                SpotAnims.VFX_WARPED_SCEPTRE_CAST,
                SpotAnims.VFX_WARPED_SCEPTRE_PROJECTILE_PROJECTILE,
                SpotAnims.VFX_WARPED_SCEPTRE_PROJECTILE_IMPACT,
            ),
            // Wiki "Thammaron's sceptre": ⌊Magic / 3⌋ - 8; "Accursed sceptre": Magic / 3 - 6; 1 revenant ether each, 16,000.
            Staff(
                "thammaron",
                intArrayOf(Objs.WILD_CAVE_SCEPTRE_CHARGED),
                Objs.WILD_CAVE_SCEPTRE_UNCHARGED,
                16_000,
                4,
                listOf(Objs.WILD_CAVE_SHARD to 1),
                { maxOf(1, it / 3 - 8) },
                -1,
                -1,
                SpotAnims.REVENANT_MAGIC_TRAVEL,
                SpotAnims.REVENANT_MAGIC_IMPACT,
                onPlayers = true,
            ),
            Staff(
                "accursed",
                intArrayOf(Objs.WILD_CAVE_ACCURSED_CHARGED),
                Objs.WILD_CAVE_ACCURSED_UNCHARGED,
                16_000,
                4,
                listOf(Objs.WILD_CAVE_SHARD to 1),
                { maxOf(1, it / 3 - 6) },
                -1,
                -1,
                SpotAnims.REVENANT_MAGIC_TRAVEL,
                SpotAnims.REVENANT_MAGIC_IMPACT,
                onPlayers = true,
            ),
            // Wiki "Eye of Ayak": ⌊Magic / 3⌋ - 6, speed 3; death 2 + chaos 1 (or a demon tear); 50,000.
            Staff(
                "eye_of_ayak",
                intArrayOf(Objs.EYE_OF_AYAK),
                Objs.EYE_OF_AYAK_UNCHARGED,
                50_000,
                3,
                listOf(Objs.DEATHRUNE to 2, Objs.CHAOSRUNE to 1),
                { maxOf(1, it / 3 - 6) },
                -1,
                SpotAnims.VFX_AYAK_PLAYER_NORMAL_SPOTANIM,
                SpotAnims.VFX_AYAK_NORMAL_PROJECTILE,
                SpotAnims.VFX_AYAK_NORMAL_IMPACT,
            ),
            // Wiki "Bone staff": ⌊Magic / 3⌋ + 5 (+10 against rats); chaos 1; 20,000; no uncharged obj in the cache here.
            Staff(
                "bone_staff",
                intArrayOf(Objs.RAT_BONE_STAFF),
                -1,
                20_000,
                4,
                listOf(Objs.CHAOSRUNE to 1),
                { it / 3 + 5 },
                -1,
                -1,
                SpotAnims.SLAYER_TOTS_PROJECTILE,
                SpotAnims.SLAYER_TOTS_IMPACT,
            ),
        )

    private val byObj: Map<Int, Staff> = STAVES.flatMap { s -> s.charged.map { it to s } }.toMap()
    private val byUncharged: Map<Int, Staff> = STAVES.filter { it.uncharged >= 0 }.associateBy { it.uncharged }

    fun of(obj: Int): Staff? = byObj[obj]

    fun ofAny(obj: Int): Staff? = byObj[obj] ?: byUncharged[obj]

    val allObjs: Collection<Int> get() = byObj.keys + byUncharged.keys

    private fun key(staff: Staff) = "combat.charges.${staff.family}"

    fun charges(player: PlayerRef, staff: Staff): Long = player.saved(key(staff)) ?: 0L

    /** CM21: one built-in cast at [target]; false (and the attack stops) without charges or on a player. */
    fun attack(player: PlayerRef, target: EntityRef, staff: Staff, stance: Stance): Boolean {
        if (target is PlayerRef && !staff.onPlayers) {
            player.message("You can't use this weapon's spell on other players.")
            return false
        }
        val left = charges(player, staff)
        if (left <= 0) {
            player.message("Your weapon has no charges left. You need to charge it.")
            return false
        }
        player.setSaved(key(staff), left - 1)
        player.setAttackClock(player.tick + staff.speed)
        val weapon = player.worn(Wear.WEAPON)
        player.anim(staff.castAnim.takeIf { it >= 0 } ?: WeaponData.of(weapon)?.attackAnims?.firstOrNull { it >= 0 } ?: Seqs.HUMAN_CASTING)
        if (staff.castSpot >= 0) player.spotAnim(staff.castSpot, height = CAST_HEIGHT)
        CombatSystem.announceAttack(player, target, CombatStyle.MAGIC)
        val distance = player.distanceTo(target)
        val delay = Formulas.magicDelay(distance)
        (if (staff.effect == Effect.SHADOW) ProjType.TUMEKENS else ProjType.MAGIC).launch(player, target, staff.projectile, delay)
        CombatVisuals.block(target, delay)
        val multiplier = if (staff.effect == Effect.SHADOW) SHADOW_MULTIPLIER else 1
        player[Attr.LAST_SPELL] = null // a powered staff's own spell is not Magic Dart (slayer-equipment.md SE9)
        val attackRoll = Attacks.attackRoll(player, stance, AttackType.MAGIC, magicBonusMultiplier = multiplier, target = target)
        val defenceRoll = Attacks.defenceRoll(target, AttackType.MAGIC)
        val hit = Formulas.hits(attackRoll, defenceRoll, player::random)
        val arrive = target.ticksUntilHit(delay) * CYCLES_PER_TICK
        target.spotAnim(if (hit) staff.impact else SpotAnims.FAILEDSPELL_IMPACT, height = IMPACT_HEIGHT, delay = arrive)
        if (hit) land(player, target, staff, stance, multiplier, delay) else CombatSystem.queueHit(target, player, 0, HitStyle.MAGIC, delay)
        if (charges(player, staff) <= 0) uncharge(player, staff)
        return true
    }

    private fun land(player: PlayerRef, target: EntityRef, staff: Staff, stance: Stance, multiplier: Int, delay: Int) {
        val damagePercent =
            minOf(MAX_SHADOW_PERCENT, player.bonus(Param.MAGIC_DAMAGE) / MAGIC_DAMAGE_UNIT * multiplier) +
                player.activePrayers().sumOf { it.effect.magicDamage }
        var max = Formulas.magicMaxHit(staff.max(player.level(Stat.MAGIC)), damagePercent)
        max = TargetGear.apply(max, TargetGear.of(player, target, CombatStyle.MAGIC))
        if (target is PlayerRef && target.protects(CombatStyle.MAGIC)) max = Formulas.protectedMaxHit(max)
        var damage = Formulas.damage(max, player::random)
        val sanguine = staff.effect == Effect.SANGUINE && player.random(SANGUINE_CHANCE) == 0
        if (sanguine) damage += SANGUINE_BONUS
        damage = minOf(Formulas.DAMAGE_CAP, damage)
        CombatSystem.queueHit(target, player, damage, HitStyle.MAGIC, delay) {
            if (staff.effect == Effect.VENOM && player.random(PERCENT) < VENOM_CHANCE) Poison.envenom(target)
            if (sanguine) {
                player.hitpoints = minOf(player.maxHitpoints, player.hitpoints + damage / 2)
                target.spotAnim(SpotAnims.SANGUINESTI_STAFF_HEAL)
            }
        }
        // Wiki "Powered staff": no base xp; 2 Magic per damage (defensive: 1.33 Magic + 1 Defence), 1.33 Hitpoints.
        if (damage > 0) {
            if (stance == Stance.LONGRANGE_MAGIC) {
                player.addXp(Stat.MAGIC, damage * 4.0 / 3)
                player.addXp(Stat.DEFENCE, damage.toDouble())
            } else {
                player.addXp(Stat.MAGIC, damage * 2.0)
            }
            player.addXp(Stat.HITPOINTS, damage * 4.0 / 3)
        }
    }

    /** CM22: runes used on the staff buy as many charges as they pay for, up to its capacity. */
    fun charge(player: PlayerRef, staffSlot: Int, staffObj: Int) {
        val staff = ofAny(staffObj) ?: return
        val backpack = player.inv(Invs.INV)
        val room = staff.capacity - charges(player, staff)
        val affordable = staff.runes.minOf { (obj, n) -> backpack.total(obj) / n }
        val adding = minOf(room, affordable)
        if (adding <=
            0
        ) {
            return player.message(
                if (room <=
                    0
                ) {
                    "Your weapon is already fully charged."
                } else {
                    "You don't have the items needed to charge it."
                },
            )
        }
        val ok =
            player.transaction("charge staff") {
                staff.runes.all { (obj, n) -> remove(Invs.INV, obj, (n * adding).toInt()) } &&
                    (staff.uncharged < 0 || staffObj != staff.uncharged || replaceAt(Invs.INV, staffSlot, staffObj, staff.charged.first()))
            }
        if (!ok) return
        player.setSaved(key(staff), charges(player, staff) + adding)
        player.message("Your weapon now has ${charges(player, staff)} charges.")
    }

    fun check(player: PlayerRef, obj: Int) {
        val staff = ofAny(obj) ?: return
        player.message("Your weapon has ${charges(player, staff)} charges left.")
    }

    /** CM22: out of charges the wielded staff becomes its uncharged obj. */
    private fun uncharge(player: PlayerRef, staff: Staff) {
        if (staff.uncharged < 0) return
        val weapon = player.worn(Wear.WEAPON)
        player.transaction("staff uncharged") { replaceAt(Invs.WORN, Wear.WEAPON, weapon, staff.uncharged) }
        player.message("Your weapon has run out of charges.")
        player.stopInteraction()
    }

    private const val CAST_HEIGHT = 92
    private const val IMPACT_HEIGHT = 124
    private const val CYCLES_PER_TICK = 30
    private const val PERCENT = 100
    private const val MAGIC_DAMAGE_UNIT = 10

    /** Wiki "Tumeken's shadow": gear magic attack and damage × 3, damage capped at 100%. */
    private const val SHADOW_MULTIPLIER = 3
    private const val MAX_SHADOW_PERCENT = 100

    /** Wiki "Sanguinesti staff": 1 in 5, +8 damage. */
    private const val SANGUINE_CHANCE = 5
    private const val SANGUINE_BONUS = 8

    /** Wiki "Trident of the swamp": 25% venom. */
    private const val VENOM_CHANCE = 25
}
