package tp.content.magic

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.symbols.Components
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * The Lunar spells that help other players (docs/mechanics/magic-utility.md LS1-LS5): Cure Me, Cure Other, Cure Group,
 * Energy Transfer and Heal Other. The others need Accept Aid on.
 */
public class LunarSupport : ContentPlugin {
    override fun ContentScope.register() {
        onButton(CURE_ME.component, SpellTeleports.OP_CAST) { cureMe() }
        onButton(CURE_GROUP.component, SpellTeleports.OP_CAST) { cureGroup() }
        onApPlayerT(CURE_OTHER.component) { target -> onTarget(this, target) { cureOther(it) } }
        onApPlayerT(ENERGY_TRANSFER.component) { target -> onTarget(this, target) { energyTransfer(it) } }
        onApPlayerT(HEAL_OTHER.component) { target -> onTarget(this, target) { healOther(it) } }
    }

    /** Spell range for a target (Wiki "Attack range": magic 10; OPEN-MU6 for these spells). */
    private fun onTarget(script: PlayerScript, target: PlayerRef, cast: PlayerRef.(PlayerRef) -> Unit) {
        if (script.currentApRange != RANGE) {
            script.apRange(RANGE)
            return
        }
        if (target != script.player) script.player.cast(target)
    }

    private fun PlayerRef.cureMe() {
        if (!Casting.ready(this, CURE_ME) { cureMe() }) return
        if (!isToxic(this)) return message(NOT_POISONED)
        if (!pay(CURE_ME)) return
        cure(this)
    }

    private fun PlayerRef.cureOther(target: PlayerRef) {
        if (!Casting.ready(this, CURE_OTHER) { cureOther(target) }) return
        if (!acceptsAid(target)) return message(NOT_ACCEPTING)
        if (!isToxic(target)) return message(TARGET_NOT_POISONED)
        if (!pay(CURE_OTHER)) return
        face(target)
        cure(target)
    }

    /** LS3: everyone within one tile who has Accept Aid on and is poisoned (and the caster). */
    private fun PlayerRef.cureGroup() {
        if (!Casting.ready(this, CURE_GROUP) { cureGroup() }) return
        val group = (playersWithin(GROUP_RADIUS) + this).distinct().filter { it == this || acceptsAid(it) }
        if (group.none { isToxic(it) }) return message(NOT_POISONED)
        if (!pay(CURE_GROUP)) return
        group.forEach { cure(it) }
    }

    /** LS4: all special attack energy and 10 hitpoints for all of the target's run and special attack energy. */
    private fun PlayerRef.energyTransfer(target: PlayerRef) {
        if (!Casting.ready(this, ENERGY_TRANSFER) { energyTransfer(target) }) return
        if (!acceptsAid(target)) return message(NOT_ACCEPTING)
        if (hitpoints <= ENERGY_TRANSFER_HITPOINTS) return message("You need more hitpoints to cast this spell.")
        if (varp(Varps.SA_ENERGY) < FULL_SPEC) return message(NEEDS_SPEC)
        if (!pay(ENERGY_TRANSFER)) return
        face(target)
        hitpoints -= ENERGY_TRANSFER_HITPOINTS
        setVarp(Varps.SA_ENERGY, 0)
        target.runEnergy = MAX_RUN_ENERGY
        target.setVarp(Varps.SA_ENERGY, FULL_SPEC)
    }

    /** LS5: up to 75% of the caster's hitpoints go to the target, who can't take more than they are missing. */
    private fun PlayerRef.healOther(target: PlayerRef) {
        if (!Casting.ready(this, HEAL_OTHER) { healOther(target) }) return
        if (!acceptsAid(target)) return message(NOT_ACCEPTING)
        if (hitpoints * PERCENT < maxHitpoints * HEAL_OTHER_MIN_PERCENT) return message("You need more hitpoints to cast this spell.")
        val amount = minOf(hitpoints * HEAL_OTHER_SHARE_PERCENT / PERCENT, target.maxHitpoints - target.hitpoints)
        if (amount <= 0) return message(TARGET_NOT_HURT)
        if (!pay(HEAL_OTHER)) return
        face(target)
        hitpoints -= amount
        target.hitpoints += amount
    }

    private fun PlayerRef.pay(spell: UtilitySpell): Boolean {
        if (!transaction("lunar support runes") { SpellRunes.take(this, this@pay, spell.runes) }) {
            message(Casting.NO_RUNES)
            return false
        }
        Casting.finish(this, spell)
        return true
    }

    private fun acceptsAid(player: PlayerRef): Boolean = player.varbit(Varbits.OPTION_ACCEPTAID) == 1

    private fun isToxic(player: PlayerRef): Boolean = player.varp(Varps.POISON) > 0

    /**
     * LS2 (poison.md PO6): poison is cured with no immunity; venom becomes poison at the venom's current damage (Wiki "Cure
     * Me": "convert venom to poison").
     */
    private fun cure(player: PlayerRef) {
        val state = player.varp(Varps.POISON)
        when {
            state >= VENOM -> player.setVarp(Varps.POISON, minOf(VENOM_CAP, 2 * (state - VENOM_BASE)) * SEVERITY_PER_DAMAGE)
            state > 0 -> player.setVarp(Varps.POISON, 0)
        }
    }

    private companion object {
        const val PERCENT = 100
        const val RANGE = 10

        /** Wiki "Cure Group": "all players within 1 square radius of the caster". */
        const val GROUP_RADIUS = 1

        /** OPEN-MU4: no Wiki text for these refusals. */
        const val NOT_ACCEPTING = "That player is not accepting aid."
        const val NOT_POISONED = "You are not poisoned."
        const val TARGET_NOT_POISONED = "That player is not poisoned."
        const val TARGET_NOT_HURT = "That player doesn't need healing."
        const val NEEDS_SPEC = "You need full special attack energy to cast this spell."

        /** Poison state encoding (poison.md PO1, PO4): venom from 1,000,000, damage 2 x (state - 999,997) up to 20. */
        const val VENOM = 1_000_000
        const val VENOM_BASE = 999_997
        const val VENOM_CAP = 20

        /** poison.md PO3: starting severity per point of damage. */
        const val SEVERITY_PER_DAMAGE = 5

        /** Special attack energy is 0..1000 (content/combat `Specials.FULL_ENERGY`) and run energy 0..10,000 (engine `Player`). */
        const val FULL_SPEC = 1000
        const val MAX_RUN_ENERGY = 10_000

        /** Wiki "Energy Transfer": "costs 100% special attack energy and 10 hitpoints". */
        const val ENERGY_TRANSFER_HITPOINTS = 10

        /** Wiki "Heal Other": up to 75% of the caster's hitpoints, and "at least 11% of their hitpoints" to cast. */
        const val HEAL_OTHER_SHARE_PERCENT = 75
        const val HEAL_OTHER_MIN_PERCENT = 11

        // Speeds are blank on the Wiki for these spells: the 3 ticks of the other utility spells (OPEN-MU2); visuals OPEN-MU3.
        @Suppress("LongParameterList")
        private fun spell(component: Int, level: Int, xp: Double, seq: Int, spot: Int, vararg runes: Pair<Int, Int>) =
            UtilitySpell(component, Book.LUNAR, level, xp, runes.toList(), 3, seq, spot)

        /** Wiki "Cure Me": level 71, 69 xp, astral 2 + cosmic 2 + law 1. */
        @Suppress("MagicNumber")
        val CURE_ME =
            spell(
                Components.Magic_Spellbook.CURE_ME,
                71,
                69.0,
                Seqs.LUNAR_HUMAN_MAGIC_SUMMON2,
                SpotAnims.QUEST_LUNAR_MAGIC_CURE_PLANTS,
                Objs.ASTRALRUNE to 2,
                Objs.COSMICRUNE to 2,
                Objs.LAWRUNE to 1,
            )

        /** Wiki "Cure Other": level 68, 65 xp, astral 1 + law 1 + earth 10. */
        @Suppress("MagicNumber")
        val CURE_OTHER =
            spell(
                Components.Magic_Spellbook.CURE_OTHER,
                68,
                65.0,
                Seqs.LUNAR_HUMAN_MAGIC_SUMMON2,
                SpotAnims.QUEST_LUNAR_MAGIC_CURE_PLANTS,
                Objs.ASTRALRUNE to 1,
                Objs.LAWRUNE to 1,
                Objs.EARTHRUNE to 10,
            )

        /** Wiki "Cure Group": level 74, 74 xp, astral 2 + law 2 + cosmic 2. */
        @Suppress("MagicNumber")
        val CURE_GROUP =
            spell(
                Components.Magic_Spellbook.CURE_GROUP,
                74,
                74.0,
                Seqs.LUNAR_HUMAN_MAGIC_SUMMON2,
                SpotAnims.QUEST_LUNAR_MAGIC_CURE_PLANTS,
                Objs.ASTRALRUNE to 2,
                Objs.LAWRUNE to 2,
                Objs.COSMICRUNE to 2,
            )

        /** Wiki "Energy Transfer": level 91, 100 xp, astral 3 + law 2 + nature 1. */
        @Suppress("MagicNumber")
        val ENERGY_TRANSFER =
            spell(
                Components.Magic_Spellbook.ENERGY_TRANS,
                91,
                100.0,
                Seqs.QUEST_LUNAR_SPELL_ENERGY_EXCHANGE,
                -1,
                Objs.ASTRALRUNE to 3,
                Objs.LAWRUNE to 2,
                Objs.NATURERUNE to 1,
            )

        /** Wiki "Heal Other": level 92, 101 xp, blood 1 + law 3 + astral 3. */
        @Suppress("MagicNumber")
        val HEAL_OTHER =
            spell(
                Components.Magic_Spellbook.HEAL_OTHER,
                92,
                101.0,
                Seqs.HUMAN_CASTHEAL,
                SpotAnims.HEAL_CASTING,
                Objs.BLOODRUNE to 1,
                Objs.LAWRUNE to 3,
                Objs.ASTRALRUNE to 3,
            )
    }
}
