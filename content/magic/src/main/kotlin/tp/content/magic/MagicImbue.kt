package tp.content.magic

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.SpellRunes
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits

/**
 * Magic Imbue (docs/mechanics/runecraft.md RC11): for 21 ticks combination runes need no talisman. The effect is the cache
 * varbit `magic_imbue_active`, which the Runecraft plugin reads (content modules share state through vars, not code).
 */
public class MagicImbue : ContentPlugin {
    override fun ContentScope.register() {
        onButton(SPELL.component, SpellTeleports.OP_CAST) { cast() }
    }

    private fun PlayerRef.cast() {
        if (!Casting.ready(this, SPELL) { cast() }) return
        // Wiki "Magic Imbue": "This spell can only be cast once every 12.6 seconds (21 game ticks)".
        if ((this[READY_AT] ?: 0L) > tick) return
        if (!transaction("magic imbue runes") { SpellRunes.take(this, this@cast, SPELL.runes) }) return message(Casting.NO_RUNES)
        Casting.finish(this, SPELL)
        this[READY_AT] = tick + DURATION
        setVarbit(Varbits.MAGIC_IMBUE_ACTIVE, 1)
        timer(FADE, DURATION, soft = true) {
            setVarbit(Varbits.MAGIC_IMBUE_ACTIVE, 0)
            stopTimer(FADE)
        }
    }

    internal companion object {
        private val READY_AT = AttrKey<Long>("magic_imbue_ready_at")
        private val FADE = TimerId("magic.imbue")

        /** Wiki "Magic Imbue": no talisman needed for "12.6 seconds" and one cast per "21 game ticks". */
        const val DURATION = 21

        private const val NO_SPOT = -1

        /**
         * Wiki "Magic Imbue": level 82, 86 xp, fire 7 + water 7 + astral 2, speed 0 ticks (we use the 1 tick of the other
         * Lunar utility spells; OPEN-RC11). The cast animation is the cache `human_cast_selfimbue`; no graphic is known (OPEN-RC11).
         */
        @Suppress("MagicNumber")
        val SPELL =
            UtilitySpell(
                Components.Magic_Spellbook.MAGIC_IMBUE,
                Book.LUNAR,
                82,
                86.0,
                listOf(Objs.FIRERUNE to 7, Objs.WATERRUNE to 7, Objs.ASTRALRUNE to 2),
                1,
                Seqs.HUMAN_CAST_SELFIMBUE,
                NO_SPOT,
            )
    }
}
