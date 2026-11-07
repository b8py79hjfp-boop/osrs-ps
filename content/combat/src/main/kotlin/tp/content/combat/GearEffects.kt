package tp.content.combat

/**
 * Weapon passives and gear set effects on accuracy and max hit (docs/mechanics/gear-effects.md GE1-GE40). Pure
 * functions over a [Kit] (the worn item names) and a [Foe] (the target's combat data), so the DPS parity matrix can
 * drive them directly. Order and floors follow the Wiki DPS calculator (weirdgloop/osrs-dps-calc
 * `PlayerVsNPCCalc.getPlayerMax*`), which encodes the Wiki's per-item rules; every step is ⌊value × num / den⌋.
 *
 * Effects keyed on an NPC attribute (demon, dragon, kalphite, undead, ...) or a Slayer task never apply to players
 * ([Foe.isPlayer] has no attributes). PvP-specific rules are noted per effect in the mechanics doc.
 */
@Suppress("MagicNumber", "CyclomaticComplexMethod") // gear-effects.md rule tables: the Wiki's fractions, in order
internal object GearEffects {
    /** The worn item names, canonicalised: lower case, Barrows degradation suffix (" 100" .. " 25") removed. */
    class Kit(rawNames: Collection<String>) {
        val names: Set<String> = rawNames.map(::canon).toSet()

        fun has(vararg wanted: String): Boolean = wanted.any { it.lowercase() in names }

        fun all(vararg wanted: String): Boolean = wanted.all { it.lowercase() in names }

        fun any(predicate: (String) -> Boolean): Boolean = names.any(predicate)

        companion object {
            /** Usable Barrows states; a piece at " 0" keeps its suffix and so matches no set (Wiki: it "will lose ... its set bonus"). */
            private val DEGRADE = Regex(" (25|50|75|100)$")

            fun canon(name: String): String = name.lowercase().replace(DEGRADE, "")
        }
    }

    /** The target as gear effects see it. Players: no attributes, size 1, their own (boosted) Magic level. */
    class Foe(
        val attributes: Set<String>,
        val size: Int,
        val magicLevel: Int,
        val magicAttack: Int,
        val name: String,
        val isPlayer: Boolean,
        val onSlayerTask: Boolean,
    ) {
        fun isA(attribute: String): Boolean = attribute in attributes

        val isVampyre: Boolean get() = attributes.any { it.startsWith(VAMPYRE) }
    }

    /** One attack's situation: what is worn, at whom, how. */
    class Ctx(
        val kit: Kit,
        val foe: Foe,
        val type: AttackType,
        val inWilderness: Boolean = false,
        val special: Boolean = false,
    ) {
        val melee: Boolean get() = type.style == CombatStyle.MELEE
    }

    // ---- set and item predicates (GE1-GE8)

    /** GE1 Wiki "Void Knight equipment": top, robe and gloves (normal or elite, (or) too) plus the style's helm. */
    private fun Kit.voidRobes(elite: Boolean): Boolean {
        val tops =
            if (elite) {
                arrayOf(
                    "elite void top",
                    "elite void top (or)",
                )
            } else {
                arrayOf("void knight top", "void knight top (or)", "elite void top", "elite void top (or)")
            }
        val robes =
            if (elite) {
                arrayOf(
                    "elite void robe",
                    "elite void robe (or)",
                )
            } else {
                arrayOf("void knight robe", "void knight robe (or)", "elite void robe", "elite void robe (or)")
            }
        return has(*tops) && has(*robes) && has("void knight gloves")
    }

    fun Kit.meleeVoid(): Boolean = voidRobes(false) && has("void melee helm", "void melee helm (or)")

    fun Kit.rangedVoid(): Boolean = voidRobes(false) && has("void ranger helm", "void ranger helm (or)")

    fun Kit.eliteRangedVoid(): Boolean = voidRobes(true) && has("void ranger helm", "void ranger helm (or)")

    fun Kit.magicVoid(): Boolean = voidRobes(false) && has("void mage helm", "void mage helm (or)")

    fun Kit.eliteMagicVoid(): Boolean = voidRobes(true) && has("void mage helm", "void mage helm (or)")

    /** GE2 Wiki "Obsidian equipment": the TzHaar melee weapons that take the set bonus. */
    fun Kit.tzhaarWeapon(): Boolean =
        has(
            "tzhaar-ket-em",
            "tzhaar-ket-om",
            "tzhaar-ket-om (t)",
            "toktz-xil-ak",
            "toktz-xil-ek",
            "toktz-mej-tal",
        )

    fun Kit.obsidian(): Boolean = all("obsidian helmet", "obsidian platebody", "obsidian platelegs")

    fun Kit.berserkerNecklace(): Boolean = has("berserker necklace", "berserker necklace (or)")

    /** GE5 Wiki "Crystal armour": helm 1, legs 2, body 3 ("inactive" pieces do nothing). */
    fun Kit.crystalPieces(): Int =
        (if (has("crystal helm")) 1 else 0) + (if (has("crystal legs")) 2 else 0) + (if (has("crystal body")) 3 else 0)

    /** Crystal bows and the bow of faerdhinen; not inactive ones, nor the Gauntlet's (basic) / (attuned) / (perfected). */
    fun Kit.crystalBow(): Boolean =
        any { (it.startsWith("crystal bow") || it.startsWith("bow of faerdhinen")) && GAUNTLET_OR_INACTIVE.none(it::contains) }

    private val GAUNTLET_OR_INACTIVE = listOf("inactive", "(basic)", "(attuned)", "(perfected)")

    /** GE35 Wiki "Elemental amulet" / "Amulet of air" etc.: the amulet of the spell's element, or the elemental amulet. */
    fun Kit.elementalAmulet(element: String?): Boolean = element != null && has("elemental amulet", "amulet of $element")

    fun Kit.scythe(): Boolean = any { it.contains("scythe of vitur") && !it.contains("uncharged") }

    fun Kit.fang(): Boolean = has("osmumten's fang", "osmumten's fang (or)")

    fun Kit.keris(): Boolean = any { it.startsWith("keris") }

    /** Weapons that split each attack into two hits of half the max (Wiki pages; DPS calculator `isWearingTwoHitWeapon`). */
    fun Kit.twoHitWeapon(): Boolean = has("torag's hammers", "sulphur blades", "glacial temotli", "earthbound tecpatl")

    /** GE6 Barrows sets: the full set (any degradation), Karil / Ahrim / Torag also need the amulet of the damned. */
    fun Kit.dharok(): Boolean = all("dharok's helm", "dharok's platebody", "dharok's platelegs", "dharok's greataxe")

    fun Kit.verac(): Boolean = all("verac's helm", "verac's brassard", "verac's plateskirt", "verac's flail")

    fun Kit.guthan(): Boolean = all("guthan's helm", "guthan's platebody", "guthan's chainskirt", "guthan's warspear")

    fun Kit.damned(): Boolean = has("amulet of the damned", "amulet of the damned (full)")

    fun Kit.karilSet(): Boolean = all("karil's coif", "karil's leathertop", "karil's leatherskirt", "karil's crossbow")

    fun Kit.ahrimSet(): Boolean = all("ahrim's hood", "ahrim's robetop", "ahrim's robeskirt", "ahrim's staff")

    fun Kit.toragSet(): Boolean = all("torag's helm", "torag's platebody", "torag's platelegs", "torag's hammers")

    /** The damned forms: Karil's double hit, Ahrim's +30% proc, Torag's Defence, Dharok's recoil (Wiki "Amulet of the damned"). */
    fun Kit.karil(): Boolean = karilSet() && damned()

    fun Kit.ahrim(): Boolean = ahrimSet() && damned()

    fun Kit.toragDamned(): Boolean = toragSet() && damned()

    fun Kit.dharokDamned(): Boolean = dharok() && damned()

    fun Kit.blackMask(): Boolean = any { it.startsWith("black mask") || it.contains("slayer helmet") } || imbuedBlackMask()

    fun Kit.imbuedBlackMask(): Boolean =
        any { (it.startsWith("black mask") || it.contains("slayer helmet")) && it.endsWith("(i)") } || has("v's helm")

    fun Kit.smokeStaff(): Boolean = has("smoke battlestaff", "mystic smoke staff", "twinflame staff")

    /** GE7 Wiki revenant weapons: charged (the uncharged "(u)" forms have their own names), in the Wilderness. */
    private fun Ctx.revWeapon(): Boolean =
        inWilderness &&
            !foe.isPlayer &&
            when (type.style) {
                CombatStyle.MAGIC -> kit.has("accursed sceptre", "accursed sceptre (a)", "thammaron's sceptre", "thammaron's sceptre (a)")
                CombatStyle.RANGED -> kit.has("craw's bow", "webweaver bow")
                CombatStyle.MELEE -> kit.has("ursine chainmace", "viggora's chainmace")
            }

    fun Kit.silverWeapon(melee: Boolean): Boolean =
        if (melee) {
            has(
                "blessed axe",
                "ivandis flail",
                "blisterwood flail",
                "hallowed flail",
                "silver sickle",
                "silver sickle (b)",
                "emerald sickle",
                "emerald sickle (b)",
                "enchanted emerald sickle (b)",
                "ruby sickle (b)",
                "enchanted ruby sickle (b)",
                "blisterwood sickle",
                "silverlight",
                "darklight",
                "arclight",
                "rod of ivandis",
                "wolfbane",
            )
        } else {
            any { it.startsWith("silver bolts") } || has("blisterwood stake")
        }

    fun Kit.ratBoneWeapon(): Boolean = has("bone mace", "bone shortbow", "bone staff")

    private fun Ctx.slayerOn(): Boolean = !foe.isPlayer && foe.onSlayerTask

    private fun Ctx.undead(): Boolean = foe.isA(UNDEAD)

    private fun mul(value: Int, num: Int, den: Int): Int = (value.toLong() * num / den).toInt()

    private fun addPercent(value: Int, percent: Int): Int = value + mul(value, percent, PERCENT)

    // ---- melee (GE10-GE19)

    /** Effective attack level (prayers and stance already in [effective]) after the void boost. */
    fun meleeAccuracyLevel(effective: Int, kit: Kit): Int = if (kit.meleeVoid()) mul(effective, 11, 10) else effective

    /** The melee attack roll from the effective level ([meleeAccuracyLevel]) and the style's attack bonus. */
    fun meleeAttackRoll(c: Ctx, effective: Int, attackBonus: Int): Int {
        val k = c.kit
        val base = effective * (attackBonus + BONUS_BASE)
        var roll = base
        roll = salveOrMaskMelee(c, roll)
        if (k.tzhaarWeapon() && k.obsidian()) roll += mul(base, 1, 10)
        if (c.revWeapon()) roll = mul(roll, 3, 2)
        if (c.foe.isA(DEMON)) {
            if (k.has("arclight", "emberlight")) roll = addPercent(roll, 70)
            if (k.has("silverlight", "darklight")) roll = addPercent(roll, 60)
            if (k.has("bone claws", "burning claws")) roll = addPercent(roll, 5)
        }
        if (c.foe.isA(DRAGON)) {
            if (k.has("dragon hunter lance")) {
                roll = mul(roll, 6, 5)
            } else if (k.has("dragon hunter wand")) {
                roll = mul(roll, 7, 4)
            }
        }
        if (k.has("keris partisan of breaching") && c.foe.isA(KALPHITE)) roll = mul(roll, 133, 100)
        if (c.foe.isVampyre) {
            if (k.has("blisterwood flail", "blisterwood sickle")) roll = mul(roll, 105, 100)
            if (k.has("hallowed flail", "sunspear")) roll = mul(roll, 125, 100)
            if (k.silverWeapon(true) && k.has("efaritay's aid")) roll = mul(roll, 23, 20)
        }
        if (k.has("granite hammer") && c.foe.isA(GOLEM)) roll = mul(roll, 13, 10)
        if (c.type == AttackType.CRUSH) inquisitor(k).takeIf { it > 0 }?.let { roll = mul(roll, INQ_BASE + it, INQ_BASE) }
        return roll
    }

    /** Salve / black mask for melee (one of them; the salve wins; revenants: the amulet of avarice). */
    private fun salveOrMaskMelee(c: Ctx, value: Int): Int {
        val k = c.kit
        return when {
            c.foe.isPlayer -> value
            k.has("amulet of avarice") && c.foe.name.startsWith("Revenant") -> mul(value, 24, 20)
            k.has("salve amulet (e)", "salve amulet(ei)") && c.undead() -> mul(value, 6, 5)
            k.has("salve amulet", "salve amulet(i)") && c.undead() -> mul(value, 7, 6)
            k.blackMask() && c.slayerOn() -> mul(value, 7, 6)
            else -> value
        }
    }

    /** GE13 Wiki "Inquisitor's armour": +0.5% per piece (helm 1, hauberk 2, plateskirt 2 halves), crush only. */
    private fun inquisitor(k: Kit): Int =
        (if (k.has("inquisitor's great helm")) 1 else 0) + (if (k.has("inquisitor's hauberk")) 2 else 0) +
            (if (k.has("inquisitor's plateskirt")) 2 else 0)

    /**
     * The melee min and max hit. [prayed] is the strength level after prayers, before the stance; [baseLevel] the
     * (boosted) Strength level; [stance] the stance's strength bonus; [soulreaperStacks] 0..5.
     */
    fun meleeMax(c: Ctx, baseLevel: Int, prayed: Int, stance: Int, strengthBonus: Int, soulreaperStacks: Int = 0): IntArray {
        val k = c.kit
        var effective = prayed
        if (k.has("soulreaper axe") && !c.special) effective += mul(baseLevel, soulreaperStacks.coerceIn(0, MAX_SOULREAPER) * 6, PERCENT)
        effective += stance + EFFECTIVE_BASE
        if (k.meleeVoid()) effective = mul(effective, 11, 10)
        val base = Formulas.maxHit(effective, strengthBonus)
        var max = base
        var min = 0
        if (k.has("crystal blessing")) max = mul(max, CRYSTAL_DAMAGE_BASE + k.crystalPieces(), CRYSTAL_DAMAGE_BASE)
        max = salveOrMaskMelee(c, max)
        val demon = c.foe.isA(DEMON)
        if (demon && k.has("arclight", "emberlight")) max = addPercent(max, 70)
        if (demon && k.has("bone claws", "burning claws")) max = addPercent(max, 5)
        if (k.tzhaarWeapon() && k.obsidian()) max += mul(base, 1, 10)
        if (c.foe.isA(DRAGON) && k.has("dragon hunter lance")) max = mul(max, 6, 5)
        if (c.foe.isA(DRAGON) && k.has("dragon hunter wand")) max = mul(max, 7, 5)
        if (k.keris() && c.foe.isA(KALPHITE)) max = if (k.has("keris partisan of amascut")) mul(max, 115, 100) else mul(max, 133, 100)
        if (c.foe.isA(GOLEM) && k.has("barronite mace")) max = mul(max, 23, 20)
        if (c.foe.isA(GOLEM) && k.has("granite hammer")) max = mul(max, 13, 10)
        if (c.revWeapon()) max = mul(max, 3, 2)
        if (demon && k.has("silverlight", "darklight", "silverlight (dyed)")) max = addPercent(max, 60)
        if (c.foe.isA(LEAFY) && k.has("leaf-bladed battleaxe")) max = mul(max, 47, 40)
        if (k.has("colossal blade")) max += minOf(c.foe.size * 2, COLOSSAL_CAP)
        if (k.ratBoneWeapon() && c.foe.isA(RAT)) max += RATBANE
        if (c.type == AttackType.CRUSH) inquisitor(k).takeIf { it > 0 }?.let { max = mul(max, INQ_BASE + it, INQ_BASE) }
        if (k.fang()) {
            min = mul(max, 3, 20)
            if (!c.special) max -= min
        }
        if (k.has("obligator") && c.foe.size >= 2 &&
            c.type != AttackType.CRUSH
        ) {
            max = mul(max, PERCENT + if (c.foe.size == 2) 20 else 40, PERCENT)
        }
        return intArrayOf(min, max)
    }

    // ---- ranged (GE20-GE27)

    fun rangedAccuracyLevel(effective: Int, kit: Kit): Int =
        if (kit.rangedVoid() ||
            kit.eliteRangedVoid()
        ) {
            mul(effective, 11, 10)
        } else {
            effective
        }

    fun rangedAttackRoll(c: Ctx, effective: Int, rangedBonus: Int): Int {
        val k = c.kit
        var roll = effective * (rangedBonus + BONUS_BASE)
        if (k.crystalBow()) roll = mul(roll, CRYSTAL_ACCURACY_BASE + k.crystalPieces(), CRYSTAL_ACCURACY_BASE)
        if (!c.foe.isPlayer) {
            roll =
                when {
                    k.has("amulet of avarice") && c.foe.name.startsWith("Revenant") -> mul(roll, 24, 20)
                    k.has("salve amulet(ei)") && c.undead() -> mul(roll, 6, 5)
                    k.has("salve amulet(i)") && c.undead() -> mul(roll, 7, 6)
                    k.imbuedBlackMask() && c.slayerOn() -> mul(roll, 23, 20)
                    else -> roll
                }
        }
        if (k.has("twisted bow")) roll = tbow(roll, tbowMagic(c.foe), accuracy = true)
        if (c.revWeapon()) roll = mul(roll, 3, 2)
        if (k.has("dragon hunter crossbow", "dragon hunter crossbow (t)", "dragon hunter crossbow (b)") &&
            c.foe.isA(DRAGON)
        ) {
            roll = mul(roll, 13, 10)
        }
        if (k.has("scorching bow") && c.foe.isA(DEMON)) roll = addPercent(roll, 30)
        return roll
    }

    /** Whether ranged damage scales with Strength (Wiki "Eclipse atlatl": Strength level and melee strength bonus). */
    fun strengthScaledRanged(kit: Kit): Boolean = kit.has("eclipse atlatl", "hunter's spear")

    /** The ranged min and max hit; [effective] already holds the (Strength for the atlatl) level, prayer and stance. */
    fun rangedMax(c: Ctx, effective: Int, strengthBonus: Int): IntArray {
        val k = c.kit
        val atlatl = strengthScaledRanged(k)
        var eff = effective
        if (k.eliteRangedVoid()) {
            eff = mul(eff, 9, 8)
        } else if (k.rangedVoid()) {
            eff = mul(eff, 11, 10)
        }
        var max = Formulas.maxHit(eff, strengthBonus)
        if (k.crystalBow()) max = mul(max, CRYSTAL_DAMAGE_BASE + k.crystalPieces(), CRYSTAL_DAMAGE_BASE)
        var rev = c.revWeapon()
        var dragon = k.has("dragon hunter crossbow", "dragon hunter crossbow (t)", "dragon hunter crossbow (b)") && c.foe.isA(DRAGON)
        var demon = k.has("scorching bow") && c.foe.isA(DEMON)
        if (!c.foe.isPlayer) {
            when {
                k.has("amulet of avarice") && c.foe.name.startsWith("Revenant") -> {
                    max = mul(max, 24, 20)
                }

                (k.has("salve amulet(ei)") || (atlatl && k.has("salve amulet (e)"))) && c.undead() -> {
                    max = mul(max, 6, 5)
                }

                (k.has("salve amulet(i)") || (atlatl && k.has("salve amulet"))) && c.undead() -> {
                    max = mul(max, 7, 6)
                }

                atlatl && k.blackMask() && c.slayerOn() -> {
                    max = mul(max, 7, 6)
                }

                k.imbuedBlackMask() && c.slayerOn() -> {
                    // The revenant, dragonbane and demonbane bonuses add to the imbued mask's 15% instead of stacking.
                    var num = 23
                    if (rev) num += 10
                    if (dragon) num += 5
                    if (demon) num += 6
                    rev = false
                    dragon = false
                    demon = false
                    max = mul(max, num, 20)
                }
            }
        }
        if (k.has("twisted bow")) max = tbow(max, tbowMagic(c.foe), accuracy = false)
        if (rev) max = mul(max, 3, 2)
        if (dragon) max = mul(max, 5, 4)
        if (demon) max = addPercent(max, 30)
        if (k.ratBoneWeapon() && c.foe.isA(RAT)) max += RATBANE
        if (k.has("tonalztics of ralos")) max -= max / 4
        return intArrayOf(0, max)
    }

    /** GE20 Wiki "Twisted bow": the target's Magic level or magic attack bonus, whichever is higher, capped at 250 (350 Xerician). */
    fun tbowMagic(foe: Foe): Int = minOf(if (foe.isA(XERICIAN)) TBOW_CAP_COX else TBOW_CAP, maxOf(foe.magicLevel, foe.magicAttack))

    /**
     * GE20: accuracy 140 + ⌊(3M − 10) / 100⌋ − ⌊(⌊3M / 10⌋ − 100)² / 100⌋ % (≤ 140), damage
     * 250 + ⌊(3M − 14) / 100⌋ − ⌊(⌊3M / 10⌋ − 140)² / 100⌋ % (≤ 250).
     */
    fun tbow(value: Int, magic: Int, accuracy: Boolean): Int {
        val factor = if (accuracy) 10 else 14
        val cap = if (accuracy) 140 else 250
        val t2 = (3 * magic - factor) / 100
        val d = 3 * magic / 10 - 10 * factor
        val t3 = d * d / 100
        return mul(value, (cap + t2 - t3).coerceIn(0, cap), PERCENT)
    }

    // ---- magic (GE30-GE37)

    /** Magic spell traits gear effects read. */
    class SpellInfo(
        val standard: Boolean,
        val element: String?,
        val demonbane: Boolean,
        val bind: Boolean,
        val ancient: Boolean = false,
    )

    fun magicAccuracyLevel(effective: Int, kit: Kit): Int = if (kit.magicVoid()) mul(effective, 29, 20) else effective

    /**
     * The magic attack roll. [weaknessPercent]: the NPC's elemental weakness when the spell's element matches (added
     * from the base roll); [marked]: Mark of Darkness is active.
     */
    fun magicAttackRoll(c: Ctx, effective: Int, magicBonus: Int, spell: SpellInfo?, weaknessPercent: Int, marked: Boolean): Int {
        val k = c.kit
        val base = effective * (magicBonus + BONUS_BASE)
        var roll = base
        var additive = 0
        var mask = false
        if (!c.foe.isPlayer) {
            when {
                k.has("amulet of avarice") && c.foe.name.startsWith("Revenant") -> additive += 20
                k.has("salve amulet(ei)") && c.undead() -> additive += 20
                k.has("salve amulet(i)") && c.undead() -> additive += 15
                k.imbuedBlackMask() && c.slayerOn() -> mask = true
            }
            if (k.has("efaritay's aid") && c.foe.isVampyre && k.silverWeapon(false)) additive += 15
        }
        if (k.smokeStaff() && spell?.standard == true) additive += 10
        if (additive != 0) roll = mul(roll, PERCENT + additive, PERCENT)
        if (c.foe.isA(DRAGON)) {
            when {
                k.has("dragon hunter crossbow", "dragon hunter crossbow (t)", "dragon hunter crossbow (b)") -> roll = mul(roll, 13, 10)
                k.has("dragon hunter lance") -> roll = mul(roll, 6, 5)
                k.has("dragon hunter wand") -> roll = mul(roll, 7, 4)
            }
        }
        if (mask) roll = mul(roll, 23, 20)
        if (spell?.demonbane == true && c.foe.isA(DEMON)) {
            var percent = if (marked) 40 else 20
            if (k.has("purging staff")) percent *= 2
            roll = addPercent(roll, percent)
        }
        if (c.revWeapon()) roll = mul(roll, 3, 2)
        // GE34 Wiki "Tome of water": water spells +10% accuracy (+20% against players), curses / binds +20%.
        if (k.has("tome of water") && spell != null) {
            when {
                spell.bind -> roll = mul(roll, 6, 5)
                spell.element == "water" && spell.standard -> roll = if (c.foe.isPlayer) mul(roll, 6, 5) else mul(roll, 11, 10)
            }
        }
        if (weaknessPercent > 0) roll += mul(base, weaknessPercent, PERCENT)
        return roll
    }

    /**
     * The magic max hit from [base] (spell or powered staff base, with Charge / chaos gauntlets already added);
     * [damageTenths]: equipment magic damage in tenths of a percent (shadow multiplier applied) plus prayers ×10.
     */
    fun magicMax(c: Ctx, base: Int, damageTenths: Int, spell: SpellInfo?, weaknessPercent: Int, elementalAmulet: Boolean): Int {
        val k = c.kit
        var baseMax = base
        if (elementalAmulet) baseMax += ELEMENTAL_AMULET
        var tenths = damageTenths
        if (k.smokeStaff() && spell?.standard == true) tenths += 100
        // GE32 Wiki "Elite Void Knight equipment": the elite mage set shows +5% magic damage.
        if (k.eliteMagicVoid()) tenths += 50
        // GE33 Wiki "Virtus robes": +3% magic damage per piece for Ancient Magicks spells.
        if (spell?.ancient == true) tenths += VIRTUS_TENTHS * VIRTUS.count { piece -> k.any { it.startsWith(piece) } }
        var mask = false
        if (!c.foe.isPlayer) {
            when {
                k.has("salve amulet(ei)") && c.undead() -> tenths += 200
                k.has("salve amulet(i)") && c.undead() -> tenths += 150
                k.has("amulet of avarice") && c.foe.name.startsWith("Revenant") -> tenths += 200
                k.imbuedBlackMask() && c.slayerOn() -> mask = true
            }
        }
        var max = baseMax + mul(baseMax, tenths, PERMILLE)
        if (mask) max = mul(max, 23, 20)
        if (c.foe.isA(DRAGON)) {
            when {
                k.has("dragon hunter lance") -> max = mul(max, 6, 5)
                k.has("dragon hunter wand") -> max = mul(max, 7, 5)
                k.has("dragon hunter crossbow", "dragon hunter crossbow (t)", "dragon hunter crossbow (b)") -> max = mul(max, 5, 4)
            }
        }
        if (c.revWeapon()) max = mul(max, 3, 2)
        if (weaknessPercent > 0) max += mul(baseMax, weaknessPercent, PERCENT)
        val tome =
            when (spell?.element) {
                "fire" -> "tome of fire"
                "water" -> "tome of water"
                "earth" -> "tome of earth"
                else -> null
            }
        // GE34 Wiki tomes (charged; standard spellbook): +10% against NPCs; against players fire +50%, water +20%
        // (earth's PvP value is not given: OPEN-GE6, +10%).
        if (tome != null && spell?.standard == true && k.has(tome)) {
            max =
                when {
                    !c.foe.isPlayer -> mul(max, 11, 10)
                    tome == "tome of fire" -> mul(max, 3, 2)
                    tome == "tome of water" -> mul(max, 6, 5)
                    else -> mul(max, 11, 10)
                }
        }
        return max
    }

    // ---- constants

    const val DEMON = "demon"
    const val DRAGON = "dragon"
    const val KALPHITE = "kalphite"
    const val UNDEAD = "undead"
    const val GOLEM = "golem"
    const val LEAFY = "leafy"
    const val RAT = "rat"
    const val SHADE = "shade"
    const val FIERY = "fiery"
    const val XERICIAN = "xerician"
    const val FLYING = "flying"
    const val VAMPYRE = "vampyre"

    private const val PERCENT = 100
    private const val PERMILLE = 1000
    private const val BONUS_BASE = 64
    private const val EFFECTIVE_BASE = 8

    /** Inquisitor's: (200 + halves) / 200. */
    private const val INQ_BASE = 200

    /** Crystal armour: accuracy (20 + pieces) / 20, damage (40 + pieces) / 40 (2.5% / 5% per piece-weight). */
    private const val CRYSTAL_ACCURACY_BASE = 20
    private const val CRYSTAL_DAMAGE_BASE = 40

    /** Wiki "Soulreaper axe": at most 5 stacks, +6% Strength each. */
    const val MAX_SOULREAPER = 5

    /** Wiki "Colossal blade": +2 per tile of target size, at most +10. */
    private const val COLOSSAL_CAP = 10

    /** Wiki "Bone mace" / "Bone shortbow": +10 damage against rats. */
    private const val RATBANE = 10

    private const val TBOW_CAP = 250
    private const val TBOW_CAP_COX = 350

    /** Virtus pieces (mask, robe top, robe bottom) and their per-piece magic damage, in tenths of a percent. */
    private val VIRTUS = listOf("virtus mask", "virtus robe top", "virtus robe bottom")
    private const val VIRTUS_TENTHS = 30

    /** Wiki "Amulet of the elements" / elemental amulets: +2 base damage to the matching element's spells. */
    private const val ELEMENTAL_AMULET = 2
}
