package tp.content.combat

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.EntityRef
import tp.plugin.HitEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.SpellRunes
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * Combat magic (docs/mechanics/combat-magic.md): "Cast" on players and NPCs for every spell of [CombatSpellTable]
 * (approach range 10), and the Arceuus combat buffs Mark of Darkness, Ward of Arceuus, the corruptions and Death Charge.
 */
public class Magic : ContentPlugin {
    override fun ContentScope.register() {
        for (spell in CombatSpellTable.ALL) {
            onApPlayerT(spell.component) { target -> cast(this, target, spell) }
            onApNpcT(spell.component) { target -> cast(this, target, spell) }
        }
        onButton(Components.Magic_Spellbook.MARK_OF_DARKNESS, OP_CAST) { mark() }
        onButton(Components.Magic_Spellbook.WARD_OF_ARCEUUS, OP_CAST) { ward() }
        onButton(Components.Magic_Spellbook.LESSER_CORRUPTION, OP_CAST) { corruption(LESSER) }
        onButton(Components.Magic_Spellbook.GREATER_CORRUPTION, OP_CAST) { corruption(GREATER) }
        onButton(Components.Magic_Spellbook.DEATH_CHARGE, OP_CAST) { deathCharge() }
        events.on<HitEvent> { corrupt(it) }
        events.on<DeathEvent> { deathCharged(it) }
    }

    /** CM1, CM15: one cast within range 10 when the attack clock allows; autocast takes over afterwards if it is set. */
    private fun cast(script: PlayerScript, target: EntityRef, spell: CombatSpell) {
        val player = script.player
        if (script.currentApRange != SpellCasting.SPELL_RANGE) {
            script.apRange(SpellCasting.SPELL_RANGE)
            return
        }
        player.face(target)
        if (player.attackClock() > player.tick) {
            script.keepInteraction() // wait for the attack clock, then cast once
            return
        }
        if (!SpellCasting.cast(player, target, spell, manual = true)) return
        if (Autocast.spellOf(player) != null) CombatSystem.attackOpOf(target)?.let { player.interact(target, it) }
    }

    // ---- Arceuus buffs (CM16-CM19)

    /** A buff spell's level, spellbook, cooldown and runes; true once paid. */
    private fun PlayerRef.payBuff(
        level: Int,
        runes: List<Pair<Int, Int>>,
        readyKey: tp.plugin.AttrKey<Long>,
        cooldown: Long,
        xp: Double,
    ): Boolean {
        if (varbit(Varbits.SPELLBOOK) != SpellBook.ARCEUUS.varbit) return false
        if (level(Stat.MAGIC) < level) {
            message("You need a Magic level of $level to cast this spell.")
            return false
        }
        if ((this[readyKey] ?: 0L) > tick) return false
        if (!transaction("arceuus buff runes") { SpellRunes.take(this, this@payBuff, runes) }) {
            message(SpellCasting.NO_RUNES)
            return false
        }
        this[readyKey] = tick + cooldown
        addXp(Stat.MAGIC, xp)
        return true
    }

    /** CM16: 3 × base Magic ticks (×5 with a purging staff), a warning 10 ticks before the end. */
    private fun PlayerRef.mark() {
        if (!payBuff(MARK_LEVEL, MARK_RUNES, MARK_READY, MARK_COOLDOWN, MARK_XP)) return
        val purging = worn(Wear.WEAPON) == Objs.PURGING_STAFF
        val ticks = baseLevel(Stat.MAGIC) * MARK_PER_LEVEL * if (purging) PURGING_MULTIPLIER else 1
        this[SpellEffects.MARK_UNTIL] = tick + ticks
        message("You have placed a Mark of Darkness upon yourself")
        stopTimer(ArceuusBuffs.MARK_FADE)
        timer(ArceuusBuffs.MARK_FADE, 1, soft = true) {
            val left = (this[SpellEffects.MARK_UNTIL] ?: 0L) - tick
            when {
                left == MARK_WARNING.toLong() -> {
                    message("Your Mark of Darkness is about to run out.")
                }

                left <= 0 -> {
                    stopTimer(ArceuusBuffs.MARK_FADE)
                    this[SpellEffects.MARK_UNTIL] = null
                    message("Your Mark of Darkness has faded away.")
                }
            }
        }
    }

    /** CM17: Magic level ticks of protection. */
    private fun PlayerRef.ward() {
        if (!payBuff(WARD_LEVEL, WARD_RUNES, WARD_READY, WARD_COOLDOWN, WARD_XP)) return
        this[SpellEffects.WARD_UNTIL] = tick + level(Stat.MAGIC)
    }

    /** CM18: the next successful hit on a player may corrupt them. */
    private fun PlayerRef.corruption(kind: Int) {
        val (level, runes, xp) =
            if (kind ==
                GREATER
            ) {
                Triple(GREATER_LEVEL, GREATER_RUNES, GREATER_XP)
            } else {
                Triple(LESSER_LEVEL, LESSER_RUNES, LESSER_XP)
            }
        if (!payBuff(level, runes, CORRUPTION_READY, CORRUPTION_COOLDOWN, xp)) return
        this[ArceuusBuffs.CORRUPTION] = kind
    }

    private fun corrupt(hit: HitEvent) {
        val caster = hit.source as? PlayerRef ?: return
        val victim = hit.target as? PlayerRef ?: return
        val kind = caster[ArceuusBuffs.CORRUPTION] ?: return
        // "successful hit": a hit that dealt damage (OPEN-CM8); a warded or corrupted target keeps the buff waiting.
        if (hit.damage <= 0 || victim[ArceuusBuffs.CORRUPTED] == true || SpellEffects.warded(victim)) return
        caster[ArceuusBuffs.CORRUPTION] = null
        val marked = SpellEffects.marked(caster)
        if (!marked && caster.random(2) != 0) return
        victim[ArceuusBuffs.CORRUPTED] = true
        val interval = if (marked) MARKED_INTERVAL else CORRUPTION_INTERVAL
        var step = 0
        victim.timer(ArceuusBuffs.CORRUPT, interval, soft = true) {
            step++
            if (!SpellEffects.warded(this)) {
                setLevel(Stat.PRAYER, level(Stat.PRAYER) - step * kind)
            }
            if (step >= CORRUPTION_STEPS) {
                stopTimer(ArceuusBuffs.CORRUPT)
                this[ArceuusBuffs.CORRUPTED] = null
            }
        }
    }

    /** CM19: for 100 ticks, the next NPC kill within 16 tiles restores 15% special energy, once. */
    private fun PlayerRef.deathCharge() {
        if (!payBuff(DEATH_CHARGE_LEVEL, DEATH_CHARGE_RUNES, DEATH_CHARGE_READY, DEATH_CHARGE_COOLDOWN, DEATH_CHARGE_XP)) return
        this[ArceuusBuffs.DEATH_CHARGE_UNTIL] = tick + DEATH_CHARGE_TICKS
    }

    private fun deathCharged(death: DeathEvent) {
        val npc = death.entity as? NpcRef ?: return
        val player = death.killer as? PlayerRef ?: return
        if ((player[ArceuusBuffs.DEATH_CHARGE_UNTIL] ?: 0L) <= player.tick) return
        if (player.distanceTo(npc) > DEATH_CHARGE_RANGE) return
        val lastPvp = player[tp.plugin.SharedAttrs.LAST_COMBAT_TICK] ?: Long.MIN_VALUE
        if (player[Attr.LAST_HIT_BY] is PlayerRef && player.tick - lastPvp < PVP_GRACE) return
        player[ArceuusBuffs.DEATH_CHARGE_UNTIL] = null
        player.setVarp(Varps.SA_ENERGY, minOf(Specials.FULL_ENERGY, player.varp(Varps.SA_ENERGY) + DEATH_CHARGE_ENERGY))
    }

    private companion object {
        /** Spellbook button op 1, "Cast". */
        const val OP_CAST = 1
        const val LESSER = 1
        const val GREATER = 2

        /** Wiki "Mark of Darkness": 59, cosmic 1 + soul 1, 70 xp, cooldown 10, 3 ticks per base level, ×5 purging. */
        const val MARK_LEVEL = 59
        val MARK_RUNES = listOf(Objs.COSMICRUNE to 1, Objs.SOULRUNE to 1)
        const val MARK_XP = 70.0
        const val MARK_COOLDOWN = 10L
        const val MARK_PER_LEVEL = 3
        const val PURGING_MULTIPLIER = 5
        const val MARK_WARNING = 10
        val MARK_READY = tp.plugin.AttrKey<Long>("arceuus_mark_ready")

        /** Wiki "Ward of Arceuus": 73, cosmic 1 + nature 2 + soul 4, 83 xp, cooldown 50. */
        const val WARD_LEVEL = 73
        val WARD_RUNES = listOf(Objs.COSMICRUNE to 1, Objs.NATURERUNE to 2, Objs.SOULRUNE to 4)
        const val WARD_XP = 83.0
        const val WARD_COOLDOWN = 50L
        val WARD_READY = tp.plugin.AttrKey<Long>("arceuus_ward_ready")

        /** Wiki "Lesser / Greater Corruption": 64 / 85, death 1 + soul 2 / blood 1 + soul 3, 75 / 95 xp, cooldown 50. */
        const val LESSER_LEVEL = 64
        val LESSER_RUNES = listOf(Objs.DEATHRUNE to 1, Objs.SOULRUNE to 2)
        const val LESSER_XP = 75.0
        const val GREATER_LEVEL = 85
        val GREATER_RUNES = listOf(Objs.BLOODRUNE to 1, Objs.SOULRUNE to 3)
        const val GREATER_XP = 95.0
        const val CORRUPTION_COOLDOWN = 50L
        val CORRUPTION_READY = tp.plugin.AttrKey<Long>("arceuus_corruption_ready")

        /** Wiki "Corruption (status)": three drains 10 ticks apart (5 with Mark of Darkness). */
        const val CORRUPTION_INTERVAL = 10
        const val MARKED_INTERVAL = 5
        const val CORRUPTION_STEPS = 3

        /** Wiki "Death Charge": 80, cooldown 60 s, within 1 minute, 15% energy, 16 tiles, not after PvP in 10 ticks. */
        const val DEATH_CHARGE_LEVEL = 80
        val DEATH_CHARGE_RUNES = listOf(Objs.DEATHRUNE to 1, Objs.BLOODRUNE to 1, Objs.SOULRUNE to 1)
        const val DEATH_CHARGE_XP = 90.0
        const val DEATH_CHARGE_COOLDOWN = 100L
        const val DEATH_CHARGE_TICKS = 100L
        const val DEATH_CHARGE_RANGE = 16
        const val DEATH_CHARGE_ENERGY = 150
        const val PVP_GRACE = 10L
        val DEATH_CHARGE_READY = tp.plugin.AttrKey<Long>("arceuus_death_charge_ready")
    }
}

/** Freezes with immunity and the PvP 10-tile break (docs/mechanics/freeze.md Z1–Z7, timeline). */
internal object Freezes {
    private const val IMMUNITY = 5
    private const val BREAK_DISTANCE = 10

    /** OPEN-CM7: "The holding effect is beginning to fade." this many ticks before a bind ends. */
    private const val FADE_WARNING = 5
    private val WATCH = TimerId("freeze_watch")
    private val FADE = TimerId("bind_fade")

    /** Applies a freeze of [ticks] on the application tick, unless frozen or immune; [fade]: binds warn players (CM10). */
    fun freeze(target: EntityRef, caster: EntityRef, ticks: Int, fade: Boolean = false) {
        val now = target.tick
        if (target.isFrozen || (target[Attr.FREEZE_IMMUNE_UNTIL] ?: 0L) > now) return
        target.frozenUntil = now + ticks
        target[Attr.FREEZE_IMMUNE_UNTIL] = now + ticks + IMMUNITY
        target[Attr.FROZEN_BY] = caster
        if (target is PlayerRef) {
            target.message("You have been frozen!")
            if (caster is PlayerRef) target.timer(WATCH, 1, soft = true) { watch(this) }
            if (fade && ticks > FADE_WARNING) {
                target.timer(FADE, ticks - FADE_WARNING, soft = true) {
                    stopTimer(FADE)
                    if (isFrozen) message("The holding effect is beginning to fade.")
                }
            }
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
