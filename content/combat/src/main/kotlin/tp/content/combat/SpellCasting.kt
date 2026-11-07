package tp.content.combat

import tp.plugin.CombatSpellCast
import tp.plugin.DuelRules
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.plugin.SlayerTaskCheck
import tp.plugin.SpellRunes
import tp.symbols.Objs
import tp.symbols.SpotAnims
import tp.symbols.Varbits

/**
 * One combat spell cast at a target, by hand or by autocast (docs/mechanics/combat-magic.md CM1-CM15): the checks, the
 * rune payment, the visuals, the rolls of every target and the effects that land with the hit.
 */
internal object SpellCasting {
    /**
     * Casts [spell] from [player] at [target] now (the attack clock must allow it). [manual]: cast from the spellbook
     * (the spellbook must hold the spell). [defensive]: defensive autocast xp (CM8). False if nothing was cast.
     */
    fun cast(player: PlayerRef, target: EntityRef, spell: CombatSpell, manual: Boolean, defensive: Boolean = false): Boolean {
        if (!canCast(player, target, spell, manual)) return false
        if (!payCharges(player, target, spell)) return false
        if (!player.transaction("spell runes") { SpellRunes.take(this, player, spell.runes) }) {
            player.message(NO_RUNES)
            return false
        }
        player.events.publish(CombatSpellCast(player, spell.book.varbit))
        player.setAttackClock(player.tick + SPELL_SPEED)
        player.anim(if (wieldsStaff(player)) spell.staffAnim else spell.plainAnim)
        if (spell.castSpot >= 0) player.spotAnim(spell.castSpot, height = CAST_HEIGHT)
        CombatSystem.announceAttack(player, target, CombatStyle.MAGIC)
        val hit = strike(player, target, spell, defensive)
        player.addXp(Stat.MAGIC, if (hit) spell.xp else spell.splashXp)
        if (spell.multi) splash(player, target, spell, defensive)
        return true
    }

    /** CM1, CM6, CM9: level, spellbook, staff, target kind, and the drain spells' "already lowered" rule. */
    private fun canCast(player: PlayerRef, target: EntityRef, spell: CombatSpell, manual: Boolean): Boolean {
        if (!CombatSystem.canFight(player, target, HitStyle.MAGIC)) return false
        if (spell.effect.freezes() && player.duelRule(DuelRules.NO_MOVEMENT)) return fail(player, DuelChecks.HOLD_SPELLS)
        if (manual && player.varbit(Varbits.SPELLBOOK) != spell.book.varbit) return false // the client only shows this book
        if (player.level(Stat.MAGIC) < spell.level) return fail(player, "You need a Magic level of ${spell.level} to cast this spell.")
        if (spell.staves.isNotEmpty() && player.worn(Wear.WEAPON) !in spell.staves) return fail(player, WRONG_STAFF)
        val def = (target as? NpcRef)?.let { NpcCombatTable.of(it.type) }
        if (spell.only == SpellTarget.UNDEAD && def?.attributes?.contains(UNDEAD) != true) return fail(player, UNDEAD_ONLY)
        if (spell.only == SpellTarget.DEMON && def?.attributes?.contains(DEMON) != true) return fail(player, DEMON_ONLY)
        val debuff = spell.effect as? SpellEffect.Debuff
        if (debuff != null &&
            lowered(target, debuff.stat, debuff.npcStat)
        ) {
            return fail(player, "Your foe's ${STAT_NAMES[debuff.stat]} has already been weakened.")
        }
        return true
    }

    /** CM6 (OPEN-CM4): Iban's staff uses 1 charge per NPC cast, 2 per player cast; charges are counted per player. */
    private fun payCharges(player: PlayerRef, target: EntityRef, spell: CombatSpell): Boolean {
        if (spell.staves.isEmpty() || Objs.IBANSTAFF !in spell.staves) return true
        val upgraded = player.worn(Wear.WEAPON) == Objs.IBANSTAFF_UPGRADED
        val key = if (upgraded) IBAN_U_USED else IBAN_USED
        val capacity = if (upgraded) IBAN_U_CHARGES else IBAN_CHARGES
        val cost = if (target is PlayerRef) 2 else 1
        val used = player.saved(key) ?: 0L
        if (used + cost > capacity) return fail(player, NO_CHARGES)
        player.setSaved(key, used + cost)
        return true
    }

    /** CM13: burst and barrage hit up to 8 more targets around the primary one in multi-combat (MG1). */
    private fun splash(player: PlayerRef, target: EntityRef, spell: CombatSpell, defensive: Boolean) {
        val multiAt = player.worldAttr(SharedAttrs.MULTI_AREA) ?: return
        if (!multiAt(target.coord)) return
        val around: List<EntityRef> = player.npcsWithin(target.coord, SPLASH_RADIUS) + player.playersAround(target.coord, SPLASH_RADIUS)
        var extra = 0
        for (other in around) {
            if (extra >= MAX_EXTRA_TARGETS) break
            if (other == target || other == player || !multiAt(other.coord)) continue
            if (!CombatSystem.canFight(player, other, HitStyle.MAGIC, quiet = true)) continue
            CombatSystem.announceAttack(player, other, CombatStyle.MAGIC)
            strike(player, other, spell, defensive)
            extra++
        }
    }

    /** One target's rolls, projectile, impact and hit (CM2-CM12); true on a successful hit. */
    private fun strike(player: PlayerRef, target: EntityRef, spell: CombatSpell, defensive: Boolean): Boolean {
        val def = (target as? NpcRef)?.let { NpcCombatTable.of(it.type) }
        val weakness = if (spell.element != null && def?.weakElement == spell.element.word) def.weakPercent else 0
        val attackRoll =
            Attacks.attackRoll(player, Stance.SPELL, AttackType.MAGIC) * (PERCENT + weakness + demonbaneAccuracy(player, spell)) / PERCENT
        val defenceRoll = Attacks.defenceRoll(target, AttackType.MAGIC)
        val distance = if (spell.toSouthWest) southWestDistance(player, target) else player.distanceTo(target)
        val delay = Formulas.magicDelay(distance)
        val arrive = target.ticksUntilHit(delay) * CYCLES_PER_TICK
        shapeOf(spell).launch(player, target, spell.projectile, delay)
        CombatVisuals.block(target, delay)
        val freezeLands = spell.effect.freezes() && Bosses.freezeLands(player, target)
        val hit = Formulas.hits(attackRoll, defenceRoll, player::random) || freezeLands
        target.spotAnim(if (hit) spell.impact else SpotAnims.FAILEDSPELL_IMPACT, height = IMPACT_HEIGHT, delay = arrive)
        if (!hit) return false
        val damage =
            if (spell.effect is SpellEffect.Debuff) {
                0
            } else {
                var max = maxHit(player, target, spell, weakness)
                if (target is PlayerRef && target.protects(CombatStyle.MAGIC)) max = Formulas.protectedMaxHit(max)
                minOf(Formulas.DAMAGE_CAP, Formulas.damage(max, player::random))
            }
        val style = if (spell.effect is SpellEffect.Debuff) HitStyle.TYPELESS else HitStyle.MAGIC
        if (spell.effect !is SpellEffect.Debuff) {
            CombatSystem.queueHit(target, player, damage, style, delay) { SpellEffects.land(player, target, spell, damage) }
        } else {
            target.queueHit(delay) { SpellEffects.land(player, target, spell, damage) }
        }
        grantXp(player, damage, defensive)
        return true
    }

    /** CM4-CM7: the spell's base max with the caster's bonuses and the target's elemental weakness. */
    private fun maxHit(player: PlayerRef, target: EntityRef, spell: CombatSpell, weakness: Int): Int {
        val base = baseMax(player, target, spell)
        val percent = player.bonus(Param.MAGIC_DAMAGE) / MAGIC_DAMAGE_UNIT + player.activePrayers().sumOf { it.effect.magicDamage }
        var max = Formulas.magicMaxHit(base, percent) + base * weakness / PERCENT
        if (spell.only == SpellTarget.DEMON && SpellEffects.marked(player)) {
            max = max * (PERCENT + if (player.worn(Wear.WEAPON) == Objs.PURGING_STAFF) PURGING_DAMAGE else MARK_DAMAGE) / PERCENT
        }
        return max
    }

    private fun baseMax(player: PlayerRef, target: EntityRef, spell: CombatSpell): Int {
        val magic = player.level(Stat.MAGIC)
        return when {
            spell.name == MAGIC_DART -> dartMax(player, target, magic)
            spell.staves.any { it in GOD_STAVES } && charged(player, spell) -> CHARGED_GOD_MAX
            spell.isBolt && player.worn(Wear.HANDS) == Objs.GAUNTLETS_OF_CHAOS -> spell.baseMaxAt(magic) + CHAOS_BONUS
            else -> spell.baseMaxAt(magic)
        }
    }

    /** CM6: slayer's staff (e) on task uses the stronger formula and one of its charges (only when the spell hits). */
    private fun dartMax(player: PlayerRef, target: EntityRef, magic: Int): Int {
        if (player.worn(Wear.WEAPON) != Objs.SLAYER_STAFF_ENCHANTED || target !is NpcRef) return magic / DART_DIVISOR + DART_BASE
        val check = SlayerTaskCheck(player, target)
        player.events.publish(check)
        if (!check.onTask) return magic / DART_DIVISOR + DART_BASE
        val used = (player.saved(DART_E_USED) ?: 0L) + 1
        if (used >= DART_E_CHARGES) {
            // Wiki "Slayer's staff (e)": out of charges it reverts to a slayer's staff.
            player.transaction(
                "slayer staff charges",
            ) { replaceAt(tp.symbols.Invs.WORN, Wear.WEAPON, Objs.SLAYER_STAFF_ENCHANTED, Objs.SLAYER_STAFF) }
            player.setSaved(DART_E_USED, null)
        } else {
            player.setSaved(DART_E_USED, used)
        }
        return magic / DART_E_DIVISOR + DART_E_BASE
    }

    /** CM7: Charge is active and the god cape (normal, imbued or max) of this spell's god is worn. */
    private fun charged(player: PlayerRef, spell: CombatSpell): Boolean {
        if ((player[SharedAttrs.MAGIC_CHARGED_UNTIL] ?: 0L) <= player.tick) return false
        val god =
            spell.name.substringBefore(' ').lowercase().let {
                if (it == "claws") {
                    "guthix"
                } else if (it == "flames") {
                    "zamorak"
                } else {
                    it
                }
            }
        val cape = player.worn(Wear.CAPE)
        return cape >= 0 && player.objName(cape).lowercase().let { "$god cape" in it || "$god max cape" in it }
    }

    private fun demonbaneAccuracy(player: PlayerRef, spell: CombatSpell): Int {
        if (spell.only != SpellTarget.DEMON) return 0
        if (!SpellEffects.marked(player)) return DEMONBANE_ACCURACY
        return if (player.worn(Wear.WEAPON) == Objs.PURGING_STAFF) PURGING_ACCURACY else MARK_ACCURACY
    }

    /** CM8: per damage 2 Magic, or defensively 1.33 Magic and 1 Defence; 1.33 Hitpoints either way. */
    private fun grantXp(player: PlayerRef, damage: Int, defensive: Boolean) {
        if (damage <= 0) return
        if (defensive) {
            player.addXp(Stat.MAGIC, damage * 4.0 / 3)
            player.addXp(Stat.DEFENCE, damage.toDouble())
        } else {
            player.addXp(Stat.MAGIC, damage * 2.0)
        }
        player.addXp(Stat.HITPOINTS, damage * 4.0 / 3)
    }

    /** Whether [target]'s stat is below its base level already (CM7, CM9, CM11). */
    fun lowered(target: EntityRef, stat: Int, npcStat: Int): Boolean =
        when (target) {
            is PlayerRef -> target.level(stat) < target.baseLevel(stat)
            is NpcRef -> npcStat >= 0 && target.level(npcStat) < target.baseLevel(npcStat)
        }

    private fun wieldsStaff(player: PlayerRef): Boolean = player.weaponCategory() === Weapons.STAFF

    private fun southWestDistance(player: PlayerRef, target: EntityRef): Int {
        val a = player.coord
        val b = target.coord
        return maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))
    }

    private fun fail(player: PlayerRef, message: String): Boolean {
        player.message(message)
        return false
    }

    /** RS Mod `ProjAnimBuilds` shapes per spell family (the elemental and ancient spells use `magic_spell`). */
    private fun shapeOf(spell: CombatSpell): ProjType =
        when {
            spell.name == "Iban Blast" -> ProjType.IBAN
            spell.name == "Crumble Undead" -> ProjType.CRUMBLE
            spell.effect is SpellEffect.Bind -> ProjType.BIND
            spell.effect is SpellEffect.Debuff -> ProjType.CURSE
            else -> ProjType.MAGIC
        }

    private fun SpellEffect.freezes(): Boolean = this is SpellEffect.Freeze || this is SpellEffect.Bind

    /** Combat spells: 5 ticks (AS:6). */
    const val SPELL_SPEED = 5

    /** Wiki "Attack range": magic 10. */
    const val SPELL_RANGE = 10
    const val NO_RUNES = "You do not have enough runes to cast this spell."

    /** OPEN-CM1, OPEN-CM3, OPEN-CM4: the Wiki gives no text. */
    private const val WRONG_STAFF = "You need to wield the correct staff to cast this spell."
    private const val DEMON_ONLY = "This spell only works on demons."
    private const val NO_CHARGES = "Your staff has run out of charges."

    /** Wiki "Crumble Undead". */
    private const val UNDEAD_ONLY = "This spell only affects skeletons, zombies, ghosts and shades"
    private const val UNDEAD = "undead"
    private const val DEMON = "demon"
    private const val MAGIC_DART = "Magic Dart"
    private val STAT_NAMES = mapOf(Stat.ATTACK to "attack", Stat.STRENGTH to "strength", Stat.DEFENCE to "defence")
    private val GOD_STAVES = intArrayOf(Objs.SARADOMIN_STAFF, Objs.GUTHIX_STAFF, Objs.ZAMORAK_STAFF)

    /** RS Mod `ElementalSpells`: cast spotanim at height 92, impact at 124. */
    private const val CAST_HEIGHT = 92
    private const val IMPACT_HEIGHT = 124
    private const val CYCLES_PER_TICK = 30
    private const val PERCENT = 100

    /** Magic damage param unit (OPEN-D1). */
    private const val MAGIC_DAMAGE_UNIT = 10
    private const val SPLASH_RADIUS = 1
    private const val MAX_EXTRA_TARGETS = 8

    /** Wiki "Charge": god spells hit up to 30. */
    private const val CHARGED_GOD_MAX = 30

    /** Wiki "Chaos gauntlets": +3 on Bolt spells. */
    private const val CHAOS_BONUS = 3

    /** Wiki "Magic Dart": ⌊Magic / 10⌋ + 10; slayer's staff (e) on task ⌊Magic / 6⌋ + 13, 2,500 charges. */
    private const val DART_DIVISOR = 10
    private const val DART_BASE = 10
    private const val DART_E_DIVISOR = 6
    private const val DART_E_BASE = 13
    private const val DART_E_CHARGES = 2500L

    /** Wiki "Iban Blast": 120 charges, 2,500 for the upgraded staff. */
    private const val IBAN_CHARGES = 120L
    private const val IBAN_U_CHARGES = 2500L
    private const val IBAN_USED = "combat.iban_charges_used"
    private const val IBAN_U_USED = "combat.iban_u_charges_used"
    private const val DART_E_USED = "combat.slayer_staff_e_charges_used"

    /** Wiki "Demonbane spells": accuracy +20%, +40% marked, +80% marked with a purging staff; damage +25% / +50%. */
    private const val DEMONBANE_ACCURACY = 20
    private const val MARK_ACCURACY = 40
    private const val PURGING_ACCURACY = 80
    private const val MARK_DAMAGE = 25
    private const val PURGING_DAMAGE = 50
}
