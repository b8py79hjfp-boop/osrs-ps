package tp.content.magic

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.SpellRunes
import tp.plugin.Stats
import tp.symbols.Components
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * Shadow Veil (docs/mechanics/magic-utility.md SV1): lasts as many ticks as the caster's base Magic level and can be cast
 * once per 50 ticks. The thieving effect (a 15% chance to avoid the stun and damage of a failed pickpocket) is read by the
 * pickpocketing code through [VEILED_UNTIL].
 */
public class ShadowVeil : ContentPlugin {
    override fun ContentScope.register() {
        onButton(SPELL.component, SpellTeleports.OP_CAST) { cast() }
    }

    private fun PlayerRef.cast() {
        if (!Casting.ready(this, SPELL) { cast() }) return
        // SV1: "it can only be cast once every 30 seconds".
        if ((this[READY_AT] ?: 0L) > tick) return
        if (!transaction("shadow veil runes") { SpellRunes.take(this, this@cast, SPELL.runes) }) return message(Casting.NO_RUNES)
        Casting.finish(this, SPELL)
        // "0.6 * Y seconds, where Y is the player's base Magic level": one tick is 0.6 seconds, so Y ticks.
        this[VEILED_UNTIL] = tick + baseLevel(Stats.MAGIC)
        this[READY_AT] = tick + COOLDOWN
    }

    internal companion object {
        /** Tick at which the caster's Shadow Veil ends. */
        val VEILED_UNTIL = tp.plugin.SharedAttrs.SHADOW_VEIL_UNTIL

        private val READY_AT = AttrKey<Long>("shadow_veil_ready_at")

        /** Wiki "Shadow Veil": "it can only be cast once every 30 seconds" = 50 ticks. */
        const val COOLDOWN = 50L

        /**
         * Wiki "Shadow Veil": level 47, 58 xp, earth 5 + fire 5 + cosmic 5, speed 1 tick. Visuals: cache
         * `human_spellcast_shadowveil` and `fx_spellcast_shadowveil`.
         */
        @Suppress("MagicNumber")
        val SPELL =
            UtilitySpell(
                Components.Magic_Spellbook.SHADOW_VEIL,
                Book.ARCEUUS,
                47,
                58.0,
                listOf(Objs.EARTHRUNE to 5, Objs.FIRERUNE to 5, Objs.COSMICRUNE to 5),
                1,
                Seqs.HUMAN_SPELLCAST_SHADOWVEIL,
                SpotAnims.SHADOW_VEIL_CAST_SPOTANIM,
            )
    }
}
