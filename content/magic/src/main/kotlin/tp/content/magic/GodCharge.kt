package tp.content.magic

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.plugin.SpellRunes
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * Charge (docs/mechanics/magic-utility.md CH1-CH2): imbues the caster for 700 ticks and can be cast once per 100. The
 * stronger god spells themselves belong to combat, which reads [CHARGED_UNTIL].
 */
public class GodCharge : ContentPlugin {
    override fun ContentScope.register() {
        onButton(SPELL.component, SpellTeleports.OP_CAST) { cast() }
    }

    private fun PlayerRef.cast() {
        if (!Casting.ready(this, SPELL) { cast() }) return
        // CH2: "Charge can only be cast once every 60 seconds".
        if ((this[READY_AT] ?: 0L) > tick) return message(TOO_SOON)
        if (!transaction("charge runes") { SpellRunes.take(this, this@cast, SPELL.runes) }) return message(Casting.NO_RUNES)
        Casting.finish(this, SPELL)
        this[READY_AT] = tick + COOLDOWN
        this[CHARGED_UNTIL] = tick + DURATION
        message(CHARGED)
        stopTimer(FADE)
        timer(FADE, DURATION.toInt(), soft = true) {
            stopTimer(FADE)
            if ((this[CHARGED_UNTIL] ?: Long.MAX_VALUE) <= tick) {
                this[CHARGED_UNTIL] = null
                message(FADED)
            }
        }
    }

    internal companion object {
        /** Tick at which the caster's Charge runs out; combat reads it for the god spells (combat-magic.md). */
        val CHARGED_UNTIL = SharedAttrs.MAGIC_CHARGED_UNTIL

        private val READY_AT = AttrKey<Long>("magic_charge_ready_at")
        private val FADE = TimerId("god_charge")

        /** Wiki "Charge": seven minutes (700 game ticks). */
        const val DURATION = 700L

        /** Wiki "Charge": cooldown 100 ticks. */
        const val COOLDOWN = 100L

        /** OPEN-MU4: the Wiki gives no text for a cast during the cooldown. */
        const val TOO_SOON = "You can't cast Charge again yet."

        /** Wiki "Charge": the activation and expiry messages. */
        const val CHARGED = "You feel charged with magic power."
        const val FADED = "Your magical charge fades away."

        /**
         * Wiki "Charge": level 80, 180 xp, fire 3 + blood 3 + air 3. Speed is blank on the Wiki, so the 3 ticks of the other
         * utility spells apply (OPEN-MU2); seq 811 (`human_casting`), no graphic (OPEN-MU3).
         */
        @Suppress("MagicNumber")
        val SPELL =
            UtilitySpell(
                Components.Magic_Spellbook.CHARGE,
                Book.STANDARD,
                80,
                180.0,
                listOf(Objs.FIRERUNE to 3, Objs.BLOODRUNE to 3, Objs.AIRRUNE to 3),
                3,
                Seqs.HUMAN_CASTING,
                -1,
            )
    }
}
