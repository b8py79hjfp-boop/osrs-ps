package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.SpotAnims
import tp.symbols.Varbits

/**
 * Gear effects on the hit roll and after it (docs/mechanics/gear-effects.md GE40-GE60): multi-hit weapons, Verac's,
 * keris, Gadderhammer, Dharok's, the berserker necklace, vampyrebane, Karil's, Ahrim's, Mark of Darkness and the
 * enchanted bolts. Order follows the Wiki DPS calculator's `getAttackerDist`.
 */
@Suppress("MagicNumber", "CyclomaticComplexMethod") // gear-effects.md rule tables: the Wiki's fractions and chances
internal object PostRoll {
    /** One hitsplat of an attack; [later] ticks after the attack's own hit delay. */
    class Splat(var damage: Int, val accurate: Boolean, val later: Int = 0) {
        /** An effect that happens when this splat lands (bolt drains, stuns, poison). */
        var onLand: (() -> Unit)? = null
    }

    /** Rolls accuracy: Osmumten's fang (stab) rolls the attack twice against one defence roll (GE16). */
    fun accurate(player: PlayerRef, c: GearEffects.Ctx, attackRoll: Int, defenceRoll: Int): Boolean {
        val fang = with(GearEffects) { c.kit.fang() } && c.type == AttackType.STAB
        if (!fang) return Formulas.hits(attackRoll, defenceRoll, player::random)
        val attack = maxOf(player.random(attackRoll + 1), player.random(attackRoll + 1))
        return attack > player.random(defenceRoll + 1)
    }

    /** Uniform in [min, max], at least 1 (accurate zeroes become 1, F18). */
    fun damage(player: PlayerRef, min: Int, max: Int): Int {
        if (max <= 0) return 0
        val lo = min.coerceIn(0, max)
        return maxOf(1, lo + player.random(max - lo + 1))
    }

    /** The hitsplats of one melee attack (GE40-GE48). */
    fun melee(
        player: PlayerRef,
        c: GearEffects.Ctx,
        attackRoll: Int,
        defenceRoll: Int,
        min: Int,
        max: Int,
        unprotectedMax: Int = max,
        firstSure: Boolean = false,
    ): List<Splat> {
        val k = c.kit
        var sure = firstSure

        fun roll(m: Int): Splat {
            val hit = sure || accurate(player, c, attackRoll, defenceRoll)
            sure = false
            return Splat(if (hit) damage(player, min, maxOf(min, m)) else 0, hit)
        }
        val splats =
            with(GearEffects) {
                when {
                    k.scythe() -> {
                        (0 until c.foe.size.coerceIn(1, SCYTHE_HITS)).map { roll(max shr it) }
                    }

                    k.has("dual macuahuitl") -> {
                        // GE42 Wiki: the second splat fails if the first did; "spaced 1 game tick apart when attacking
                        // monsters" (players: OPEN-GE4, the same).
                        val first = roll(max / 2)
                        val second = if (first.accurate) roll(max - max / 2) else Splat(0, false)
                        listOf(first, Splat(second.damage, second.accurate, later = 1))
                    }

                    k.twoHitWeapon() -> {
                        listOf(roll(max / 2), roll(max - max / 2))
                    }

                    k.verac() && player.random(VERAC_CHANCE) == 0 -> {
                        // GE43 Wiki "Verac the Defiled's equipment": a quarter of attacks hit regardless of accuracy and
                        // protection prayers; against monsters they deal 1 extra damage (1..max + 1).
                        val m = unprotectedMax
                        listOf(Splat(if (c.foe.isPlayer) damage(player, 0, m) else 1 + player.random(m + 1), true))
                    }

                    else -> {
                        listOf(roll(max))
                    }
                }
            }
        for (s in splats) {
            if (!s.accurate || s.damage <= 0) continue
            s.damage = singleHitProcs(player, c, s.damage)
        }
        val dharok = with(GearEffects) { k.dharok() }
        val berserker = with(GearEffects) { k.berserkerNecklace() && k.tzhaarWeapon() }
        for (s in splats) {
            if (dharok) s.damage = dharokScale(player, s.damage)
            if (berserker) s.damage = s.damage * 6 / 5
            s.damage = vampyrebane(c, s.damage)
        }
        return splats
    }

    /** GE44-GE46: keris vs kalphites (1/51 triple), Gadderhammer vs shades (5% double, else +25%). */
    private fun singleHitProcs(player: PlayerRef, c: GearEffects.Ctx, damage: Int): Int {
        val k = c.kit
        var d = damage
        if (k.has("gadderhammer") && c.foe.isA(GearEffects.SHADE)) d = if (player.random(PERCENT) < GADDER_DOUBLE) d * 2 else d * 5 / 4
        if (with(GearEffects) { k.keris() } && c.foe.isA(GearEffects.KALPHITE) && player.random(KERIS_CHANCE) == 0) {
            d *= 3
            // Wiki "Keris partisan": the message shows only when the result is over 10 (OPEN-GE5: exactly 10).
            if (d > KERIS_MESSAGE_ABOVE) {
                val what = if (k.has("keris", "keris(p)", "keris(p+)", "keris(p++)")) "dagger" else "weapon"
                player.message("You slip your $what through a gap in the creature's chitin, landing a vicious blow.")
            }
        }
        return d
    }

    /** GE41 Wiki "Dharok the Wretched's equipment": damage × (1 + (max HP − HP) / 100 × max HP / 100). */
    private fun dharokScale(player: PlayerRef, damage: Int): Int {
        val maxHp = player.baseLevel(Stat.HITPOINTS)
        val missing = maxHp - player.hitpoints
        return (damage.toLong() * (DHAROK_DEN + missing * maxHp) / DHAROK_DEN).toInt()
    }

    /** GE47 vampyrebane weapons (Wiki "Vampyre" / "Silver weaponry"); Efaritay's aid adds 10% first. */
    private fun vampyrebane(c: GearEffects.Ctx, damage: Int): Int {
        if (!c.foe.isVampyre || damage <= 0) return damage
        val k = c.kit
        var d = damage
        val efaritay = k.has("efaritay's aid")

        fun scale(num: Int, den: Int) {
            if (efaritay) d = d * 11 / 10
            d = d * num / den
        }
        with(GearEffects) {
            when {
                k.has("blisterwood flail", "hallowed flail", "blisterwood stake") -> scale(125, 100)
                k.has("sunspear") -> scale(150, 100)
                k.has("blisterwood sickle") -> scale(23, 20)
                k.has("ivandis flail") -> scale(6, 5)
                k.any { it.startsWith("rod of ivandis") } && !c.foe.isA("vampyre3") -> scale(11, 10)
                k.silverWeapon(c.melee) && c.foe.isA("vampyre1") -> scale(11, 10)
            }
        }
        return d
    }

    /** The hitsplats of one ranged attack (GE49-GE58); bolts read the target's HP before this hit. */
    fun ranged(
        player: PlayerRef,
        target: EntityRef,
        c: GearEffects.Ctx,
        attackRoll: Int,
        defenceRoll: Int,
        max: Int,
        crossbow: Boolean,
    ): List<Splat> {
        val k = c.kit

        fun roll(): Splat {
            val hit = accurate(player, c, attackRoll, defenceRoll)
            return Splat(if (hit) damage(player, 0, max) else 0, hit)
        }
        val splats = if (k.has("tonalztics of ralos")) mutableListOf(roll(), roll()) else mutableListOf(roll())
        if (with(GearEffects) { k.karil() } && splats[0].accurate && player.random(KARIL_CHANCE) == 0) {
            splats += Splat(splats[0].damage / 2, true)
        }
        for (s in splats) s.damage = vampyrebane(c, s.damage)
        if (crossbow) Bolts.apply(player, target, c, splats[0], max)
        return splats
    }

    /** GE36 Mark of Darkness: demonbane spells deal +25% (+50% with the purging staff) to demons, after the roll. */
    fun markOfDarkness(player: PlayerRef, target: EntityRef, spell: GearEffects.SpellInfo, damage: Int): Int {
        if (!spell.demonbane || damage <= 0 || !SpellEffects.marked(player)) return damage
        val demon = (target as? NpcRef)?.let { NpcCombatTable.of(it.type) }?.attributes?.contains(GearEffects.DEMON) == true
        if (!demon) return damage
        val percent = if (GearContext.kit(player).has("purging staff")) PURGING_MARK else MARK
        return damage + damage * percent / PERCENT
    }

    /** GE37 Ahrim's set with the amulet of the damned: a quarter of magic hits deal 30% more. */
    fun ahrim(player: PlayerRef, damage: Int): Int {
        if (damage <= 0 || !with(GearEffects) { GearContext.kit(player).ahrim() }) return damage
        return if (player.random(AHRIM_CHANCE) == 0) damage * 13 / 10 else damage
    }

    /** GE38 brimstone ring: a quarter of magic attacks roll against 90% of the target's defence roll. */
    fun brimstone(player: PlayerRef, defenceRoll: Int): Int {
        if (!GearContext.kit(player).has("brimstone ring") || player.random(BRIMSTONE_CHANCE) != 0) return defenceRoll
        player.message("Your attack ignored 10% of your opponent's magic defence.")
        return defenceRoll * 9 / 10
    }

    /** GE39 twinflame staff: standard Bolt / Blast / Wave spells add a second hitsplat of 40% of the first, a tick later. */
    fun twinflame(player: PlayerRef, spellName: String, standard: Boolean, damage: Int): Int {
        if (!standard || damage <= 0 || !GearContext.kit(player).has("twinflame staff")) return 0
        return if (TWINFLAME_SPELLS.any { spellName.endsWith(it) }) damage * 4 / 10 else 0
    }

    /** The Kandarin hard diary raises every bolt effect's chance by 10% (Wiki "Kandarin Diary" rewards). */
    fun kandarinHard(player: PlayerRef): Boolean = player.varbit(Varbits.KANDARIN_DIARY_HARD_COMPLETE) == 1

    private const val PERCENT = 100
    private const val SCYTHE_HITS = 3
    private const val VERAC_CHANCE = 4
    private const val KARIL_CHANCE = 4
    private const val AHRIM_CHANCE = 4
    private const val BRIMSTONE_CHANCE = 4
    private const val KERIS_CHANCE = 51
    private const val KERIS_MESSAGE_ABOVE = 10
    private const val GADDER_DOUBLE = 5
    private const val DHAROK_DEN = 10_000
    private const val MARK = 25
    private const val PURGING_MARK = 50
    private val TWINFLAME_SPELLS = listOf("Bolt", "Blast", "Wave")

    /**
     * Enchanted bolt effects (GE50-GE59, Wiki "Enchanted bolts" and each bolt's page), crossbows only. Chances differ
     * against players and monsters; the Kandarin hard diary adds a tenth of the base chance. Opal, jade, pearl, topaz,
     * ruby and diamond work regardless of accuracy; dragonstone, onyx, emerald and sapphire need a successful hit.
     */
    object Bolts {
        /** How a special changes the proc: Armadyl Eye doubles the base chance, Evoke guarantees it on a hit. */
        enum class Spec { NONE, ARMADYL_EYE, EVOKE }

        fun apply(player: PlayerRef, target: EntityRef, c: GearEffects.Ctx, s: Splat, max: Int, spec: Spec = Spec.NONE) {
            val ammo = c.kit.names.firstOrNull { it.endsWith("bolts (e)") } ?: return
            val pvp = target is PlayerRef
            val ranged = player.level(Stat.RANGED)
            val zcb = c.kit.has("zaryte crossbow")

            fun proc(pvpTenths: Int, pvmTenths: Int): Boolean =
                when (spec) {
                    Spec.EVOKE -> s.accurate
                    Spec.ARMADYL_EYE -> chance(player, if (pvp) pvpTenths else pvmTenths, doubled = true)
                    Spec.NONE -> chance(player, if (pvp) pvpTenths else pvmTenths)
                }
            when (ammo.substringBefore(' ')) {
                "opal" -> {
                    if (proc(50, 50)) {
                        show(target, SpotAnims.XBOWS_LUCKY_LIGHTENING_STRIKE_SPOT_ANIM) { s.damage += ranged / if (zcb) 9 else 10 }
                    }
                }

                "jade" -> {
                    if (proc(60, 60)) show(target, SpotAnims.XBOWS_EARTHS_FURY_SPOT_ANIM) { s.onLand = { jade(player, target) } }
                }

                "pearl" -> {
                    if (target is PlayerRef && waterStaff(target)) return
                    val fiery = if (target is PlayerRef) fireGear(target) else c.foe.isA(GearEffects.FIERY)
                    val divisor = if (fiery) 15 else 20
                    if (proc(60, 60)) {
                        val by = if (zcb) divisor - 2 else divisor
                        show(target, SpotAnims.XBOWS_SEA_CURSE_WATERFALL_SPOT_ANIM) { s.damage += ranged / by }
                    }
                }

                "topaz" -> {
                    if (target is PlayerRef && proc(40, 0)) {
                        show(target, SpotAnims.XBOWS_DOWN_TO_EARTH_SPOT_ANIM) {
                            s.onLand = { target.setLevel(Stat.MAGIC, maxOf(0, target.level(Stat.MAGIC) - 1)) }
                        }
                    }
                }

                "sapphire" -> {
                    if (s.accurate && proc(50, 250)) {
                        show(target, SpotAnims.XBOWS_CLEAR_MIND_GLOWING_SPOT_ANIM) { s.onLand = { sapphire(player, target) } }
                    }
                }

                "emerald" -> {
                    if (s.accurate && proc(540, 550)) {
                        show(target, SpotAnims.XBOWS_MAGICAL_POISON_SPOT_ANIM) {
                            s.onLand = { Poison.poison(target, (if (zcb) 6 else 5) * Poison.SEVERITY_PER_DAMAGE) }
                        }
                    }
                }

                "ruby" -> {
                    if (player.hitpoints >= RUBY_MIN_HP && proc(110, 60)) {
                        show(target, SpotAnims.XBOWS_BLOOD_SACRIFICE_SPOT_ANIM) {
                            s.damage = minOf(if (zcb) 110 else 100, target.hitpoints * (if (zcb) 22 else 20) / PERCENT)
                            player.hitpoints -= player.hitpoints / 10
                        }
                    }
                }

                "diamond" -> {
                    if (proc(50, 100)) {
                        val cap = max * (if (zcb) 126 else 115) / PERCENT
                        show(target, SpotAnims.XBOWS_DIAMOND_TIPS_SPOTANIM) { s.damage = player.random(cap + 1) }
                    }
                }

                "dragonstone" -> {
                    val immune =
                        if (target is PlayerRef) {
                            Dragonfire.antifire(target) > 0 || Dragonfire.hasShield(target) || target.protects(CombatStyle.MAGIC)
                        } else {
                            c.foe.isA(GearEffects.FIERY) || c.foe.isA(GearEffects.DRAGON)
                        }
                    if (s.accurate && !immune && proc(60, 60)) {
                        show(target, SpotAnims.XBOWS_DRAGONS_BREATH_SPOT_ANIM) { s.damage += ranged * (if (zcb) 22 else 20) / PERCENT }
                    }
                }

                "onyx" -> {
                    if (s.accurate && !c.foe.isA(GearEffects.UNDEAD) && proc(100, 110)) {
                        show(target, SpotAnims.XBOWS_LIFE_LEACH_SPOT_ANIM) {
                            s.damage = player.random(max * (if (zcb) 132 else 120) / PERCENT + 1)
                            player.hitpoints = minOf(player.maxHitpoints, player.hitpoints + s.damage / 4)
                        }
                    }
                }
            }
        }

        /**
         * [tenths] of a percent; Armadyl Eye doubles the base, and the Kandarin hard diary then adds a tenth of the
         * base (Wiki "Armadyl crossbow": "increased from 10% to a total of 21%").
         */
        private fun chance(player: PlayerRef, tenths: Int, doubled: Boolean = false): Boolean {
            val boosted = (if (doubled) tenths * 2 else tenths) + if (kandarinHard(player)) tenths / 10 else 0
            return boosted > 0 && player.random(PERMILLE) < boosted
        }

        private inline fun show(target: EntityRef, spot: Int, effect: () -> Unit) {
            effect()
            target.spotAnim(spot)
        }

        /**
         * GE52 jade: monsters are knocked down for 8 ticks; a player dodges on an Agility roll (−16% at 1 to 110% at
         * 99, linear; OPEN-GE9: read as the dodge chance) and Protect from Magic blocks it.
         */
        private fun jade(player: PlayerRef, target: EntityRef) {
            if (target is PlayerRef) {
                if (target.protects(CombatStyle.MAGIC)) return
                val agility = target.level(Stat.AGILITY)
                val dodge = JADE_DODGE_AT_1 + (agility - 1) * (JADE_DODGE_AT_99 - JADE_DODGE_AT_1) / (MAX_LEVEL - 1)
                if (player.random(PERCENT) < dodge) return
                target.message("You have been knocked down!")
            }
            Freezes.freeze(target, player, JADE_TICKS)
        }

        /**
         * GE55 sapphire: against players drains ⌊Ranged / 20⌋ Prayer (at most what they have), the shooter gains half;
         * against monsters the shooter gains ⌊⌊their Ranged / 10⌋ / 8⌋.
         */
        private fun sapphire(player: PlayerRef, target: EntityRef) {
            val gained =
                when (target) {
                    is PlayerRef -> {
                        val drain = minOf(player.level(Stat.RANGED) / 20, target.level(Stat.PRAYER))
                        target.setLevel(Stat.PRAYER, target.level(Stat.PRAYER) - drain)
                        drain / 2
                    }

                    is NpcRef -> {
                        target.level(NpcStat.RANGED) / 10 / 8
                    }
                }
            if (gained > 0) player.setLevel(Stat.PRAYER, minOf(player.baseLevel(Stat.PRAYER), player.level(Stat.PRAYER) + gained))
        }

        /** Wiki "Pearl bolts (e)": a player with a fire staff or fire cape counts as fiery; any water staff blocks it. */
        private fun fireGear(p: PlayerRef): Boolean {
            val kit = GearContext.kit(p)
            return kit.any { name -> FIRE_GEAR.any { name.contains(it) } }
        }

        private fun waterStaff(p: PlayerRef): Boolean = GearContext.kit(p).any { name -> WATER_STAVES.any { name.contains(it) } }

        private val FIRE_GEAR = listOf("fire cape", "staff of fire", "fire battlestaff", "mystic fire staff")
        private val WATER_STAVES = listOf("staff of water", "water battlestaff", "mystic water staff")

        private const val PERMILLE = 1000

        /** Wiki "Ruby bolts (e)": needs at least 10 Hitpoints; costs 10% of current Hitpoints. */
        private const val RUBY_MIN_HP = 10

        private const val JADE_TICKS = 8
        private const val JADE_DODGE_AT_1 = -16
        private const val JADE_DODGE_AT_99 = 110
        private const val MAX_LEVEL = 99
    }
}

/**
 * Weapon-dependent damage immunities (gear-effects.md GE81-GE84; Wiki "Flying (attribute)", "Vampyre (attribute)",
 * "Rat bone weapon"). Typeless damage (burns, poison) is never stopped here.
 */
@Suppress("CyclomaticComplexMethod") // gear-effects.md GE81-GE84 rule list
internal object Immunities {
    fun adjust(source: EntityRef?, target: EntityRef, style: HitStyle, damage: Int): Int {
        if (source !is PlayerRef || target !is NpcRef) return damage
        if (style == HitStyle.TYPELESS || damage <= 0) return damage
        val attributes = NpcCombatTable.of(target.type)?.attributes ?: return damage
        val kit = GearContext.kit(source)
        val category = source.weaponCategory()
        val melee = style == HitStyle.MELEE
        return with(GearEffects) {
            when {
                // Flying monsters: "immune to melee damage except for polearms" (salamanders too, per the calculator).
                melee && FLYING in attributes && category !== Weapons.POLEARM && category !== Weapons.SALAMANDER -> {
                    0
                }

                // Rat bone weapons only work against rats.
                kit.ratBoneWeapon() && RAT !in attributes -> {
                    0
                }

                // Tier 3: only the Ivandis flail, blisterwood sickle / flail / stake, hallowed flail or sunspear.
                VAMPYRE_3 in attributes && !tierThree(kit, melee) -> {
                    0
                }

                // Tier 2: silver weaponry (or the rod of Ivandis); Efaritay's aid alone deals half.
                VAMPYRE_2 in attributes && !tierTwo(kit, melee) -> {
                    if (kit.has("efaritay's aid")) damage / 2 else 0
                }

                else -> {
                    damage
                }
            }
        }
    }

    private fun tierThree(kit: GearEffects.Kit, melee: Boolean): Boolean = kit.has("blisterwood stake") || (melee && kit.has(*TIER_THREE))

    private fun tierTwo(kit: GearEffects.Kit, melee: Boolean): Boolean =
        tierThree(kit, melee) || kit.any { it.startsWith("rod of ivandis") } || with(GearEffects) { kit.silverWeapon(melee) }

    private val TIER_THREE = arrayOf("ivandis flail", "blisterwood sickle", "blisterwood flail", "hallowed flail", "sunspear")

    private const val VAMPYRE_2 = "vampyre2"
    private const val VAMPYRE_3 = "vampyre3"
}
