package tp.content.combat

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DuelRules
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/** A combat spell (OSRS Wiki spell pages; combat-formulas.md F12, freeze.md Z1). */
internal class Spell(
    val component: Int,
    val level: Int,
    val baseMax: Int,
    val freeze: Int,
    val runes: List<Pair<Int, Int>>,
    val impact: Int,
    /** Barrage spells measure hit delay to the defender's south-west tile (HD:3). */
    val toSouthWest: Boolean,
)

/** Manual casting of combat spells on players and NPCs (approach range 10, spell speed 5: AS:6). */
public class Magic : ContentPlugin {
    override fun ContentScope.register() {
        for (spell in SPELLS) {
            onApPlayerT(spell.component) { target -> cast(this, target, spell) }
            onApNpcT(spell.component) { target -> cast(this, target, spell) }
        }
    }

    private fun cast(script: PlayerScript, target: EntityRef, spell: Spell) {
        val player = script.player
        if (script.currentApRange != SPELL_RANGE) {
            script.apRange(SPELL_RANGE)
            return
        }
        if (!CombatSystem.canFight(player, target, HitStyle.MAGIC)) return
        if (spell.freeze > 0 && player.duelRule(DuelRules.NO_MOVEMENT)) {
            player.message(DuelChecks.HOLD_SPELLS)
            return
        }
        player.face(target)
        val now = player.tick
        if (player.attackClock() > now) {
            script.keepInteraction() // wait for the attack clock, then cast once
            return
        }
        if (player.baseLevel(Stat.MAGIC) < spell.level) {
            player.message("You need a Magic level of ${spell.level} to cast this spell.")
            return
        }
        val hasRunes = player.transaction("runes") { spell.runes.all { (rune, count) -> remove(Invs.INV, rune, count) } }
        if (!hasRunes) {
            player.message("You do not have enough runes to cast this spell.")
            return
        }
        player.setAttackClock(now + SPELL_SPEED)
        // Manually cast spells get no invisible stance bonus (CO:49).
        val attackRoll = Attacks.attackRoll(player, Stance.SPELL, AttackType.MAGIC)
        val defenceRoll = Attacks.defenceRoll(target, AttackType.MAGIC)
        var max = Formulas.magicMaxHit(spell.baseMax, player.bonus(Param.MAGIC_DAMAGE) / MAGIC_DAMAGE_UNIT + prayerDamage(player))
        if (target is PlayerRef && target.protects(CombatStyle.MAGIC)) max = Formulas.protectedMaxHit(max)
        val distance = if (spell.toSouthWest) southWestDistance(player, target) else player.distanceTo(target)
        val delay = Formulas.magicDelay(distance)
        player.anim(Seqs.ZAROS_VERTICAL_CASTING)
        CombatSystem.announceAttack(player, target, CombatStyle.MAGIC)
        val hit = Formulas.hits(attackRoll, defenceRoll, player::random) || (spell.freeze > 0 && Bosses.freezeLands(player, target))
        val damage = if (hit) Formulas.damage(max, player::random) else 0
        // Visual lands on the application tick: 30 client cycles per tick (OPEN-C1).
        target.spotAnim(
            if (hit) spell.impact else SpotAnims.FAILEDSPELL_IMPACT,
            height = SPLASH_HEIGHT,
            delay =
                target.ticksUntilHit(delay) * CYCLES_PER_TICK,
        )
        if (!hit) return // a splash: no hit, no freeze (Z6 aside, freezes need a successful cast)
        CombatSystem.queueHit(target, player, damage, HitStyle.MAGIC, delay) {
            if (spell.freeze > 0) Freezes.freeze(target, player, spell.freeze)
        }
        player.addXp(Stat.MAGIC, damage * 2.0 + spell.baseXp())
        player.addXp(Stat.HITPOINTS, damage * 4.0 / 3)
    }

    private fun prayerDamage(player: PlayerRef): Int = player.activePrayers().sumOf { it.effect.magicDamage }

    private fun southWestDistance(player: PlayerRef, target: EntityRef): Int {
        val a = player.coord
        val b = target.coord
        return maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))
    }

    @Suppress("MagicNumber")
    private fun Spell.baseXp(): Double =
        when (component) {
            Components.Magic_Spellbook.ICE_BARRAGE -> 52.0
            Components.Magic_Spellbook.ICE_BLITZ -> 46.0
            Components.Magic_Spellbook.ICE_BURST -> 40.0
            else -> 34.0
        }

    private companion object {
        /** Combat spell range 10 (Wiki "Attack range": magic 10). */
        const val SPELL_RANGE = 10

        /** Combat spells: 5 ticks (AS:6). */
        const val SPELL_SPEED = 5
        const val CYCLES_PER_TICK = 30
        const val SPLASH_HEIGHT = 124

        /** Magic damage param unit (OPEN-D1). */
        const val MAGIC_DAMAGE_UNIT = 10

        /** Ancient ice spells (OSRS Wiki spell pages: level, max, runes; freeze.md Z1). */
        @Suppress("MagicNumber")
        val SPELLS: List<Spell> =
            listOf(
                Spell(
                    Components.Magic_Spellbook.ICE_RUSH,
                    58,
                    16,
                    8,
                    listOf(Objs.DEATHRUNE to 2, Objs.CHAOSRUNE to 2, Objs.WATERRUNE to 2),
                    SpotAnims.ICE_RUSH_IMPACT,
                    false,
                ),
                Spell(
                    Components.Magic_Spellbook.ICE_BURST,
                    70,
                    22,
                    16,
                    listOf(Objs.DEATHRUNE to 2, Objs.CHAOSRUNE to 4, Objs.WATERRUNE to 4),
                    SpotAnims.ICE_BURST_IMPACT,
                    false,
                ),
                Spell(
                    Components.Magic_Spellbook.ICE_BLITZ,
                    82,
                    26,
                    24,
                    listOf(Objs.DEATHRUNE to 2, Objs.BLOODRUNE to 2, Objs.WATERRUNE to 3),
                    SpotAnims.ICE_BLITZ_IMPACT,
                    false,
                ),
                Spell(
                    Components.Magic_Spellbook.ICE_BARRAGE,
                    94,
                    30,
                    32,
                    listOf(
                        Objs.DEATHRUNE to 4,
                        Objs.BLOODRUNE to 2,
                        Objs.WATERRUNE to 6,
                    ),
                    SpotAnims.ICE_BARRAGE_IMPACT,
                    true,
                ),
            )
    }
}

/** Freezes with immunity and the PvP 10-tile break (docs/mechanics/freeze.md Z1–Z7, timeline). */
internal object Freezes {
    private const val IMMUNITY = 5
    private const val BREAK_DISTANCE = 10
    private val WATCH = TimerId("freeze_watch")

    /** Applies a freeze of [ticks] on the application tick, unless frozen or immune. */
    fun freeze(target: EntityRef, caster: EntityRef, ticks: Int) {
        val now = target.tick
        if (target.isFrozen || (target[Attr.FREEZE_IMMUNE_UNTIL] ?: 0L) > now) return
        target.frozenUntil = now + ticks
        target[Attr.FREEZE_IMMUNE_UNTIL] = now + ticks + IMMUNITY
        target[Attr.FROZEN_BY] = caster
        if (target is PlayerRef) {
            target.message("You have been frozen!")
            if (caster is PlayerRef) target.timer(WATCH, 1, soft = true) { watch(this) }
        }
    }

    /** Z5 / OPEN-Z1: beyond 10 tiles (x/z) from the caster the freeze ends and immunity starts. */
    private fun watch(player: PlayerRef) {
        val caster = player[Attr.FROZEN_BY]
        if (!player.isFrozen || caster == null) {
            player.stopTimer(WATCH)
            return
        }
        val a = player.coord
        val b = caster.coord
        val far = a.level != b.level || maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z)) > BREAK_DISTANCE
        if (far) {
            player.frozenUntil = player.tick
            player[Attr.FREEZE_IMMUNE_UNTIL] = player.tick + IMMUNITY
            player[Attr.FROZEN_BY] = null
            player.stopTimer(WATCH)
        }
    }
}
