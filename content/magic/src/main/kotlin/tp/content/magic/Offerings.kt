package tp.content.magic

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.OfferRemains
import tp.plugin.PlayerRef
import tp.plugin.SpellRunes
import tp.symbols.Components
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * Demonic and Sinister Offering (magic-utility.md OF1-OF3, Wiki spell pages): offer up to three demonic ashes / bones
 * from the backpack for 300% of their Prayer xp; the Prayer side is content/skills `RemainsOffering`.
 */
public class Offerings : ContentPlugin {
    override fun ContentScope.register() {
        onButton(DEMONIC.component, SpellTeleports.OP_CAST) { cast(DEMONIC, ashes = true) }
        onButton(SINISTER.component, SpellTeleports.OP_CAST) {
            // OF2: Sinister Offering needs A Kingdom Divided (cache Quest row 81: varbit 12296 = 150 when finished).
            if (varbit(KINGDOM_DIVIDED_VARBIT) < KINGDOM_DIVIDED_DONE) return@onButton message(NEEDS_QUEST)
            cast(SINISTER, ashes = false)
        }
    }

    private fun PlayerRef.cast(spell: UtilitySpell, ashes: Boolean) {
        if (!Casting.ready(this, spell)) return
        if (!SpellRunes.has(this, spell.runes)) return message(Casting.NO_RUNES)
        val offer = OfferRemains(this, ashes, MAX_OFFERED, MULTIPLIER)
        events.publish(offer)
        if (offer.offered == 0) return offer.refusal?.let(::message) ?: Unit
        if (!transaction("offering runes") { SpellRunes.take(this, this@cast, spell.runes) }) return message(Casting.NO_RUNES)
        Casting.finish(this, spell)
    }

    private companion object {
        /** OF1: up to three remains per cast, 300% of their xp. */
        const val MAX_OFFERED = 3
        const val MULTIPLIER = 3.0

        const val KINGDOM_DIVIDED_VARBIT = 12296
        const val KINGDOM_DIVIDED_DONE = 150

        /** OPEN-OF1: the refusal text is ours. */
        const val NEEDS_QUEST = "You need to complete A Kingdom Divided to cast this spell."

        /** Wiki "Demonic Offering": level 84, 175 xp, soul 1 + wrath 1, 9 ticks (shared cooldown, 5.4 s). */
        @Suppress("MagicNumber")
        val DEMONIC =
            UtilitySpell(
                Components.Magic_Spellbook.DEMONIC_OFFERING,
                Book.ARCEUUS,
                84,
                175.0,
                listOf(Objs.SOULRUNE to 1, Objs.WRATHRUNE to 1),
                9,
                Seqs.HUMAN_CAST_OFFERING,
                SpotAnims.DEMONIC_OFFERING_CAST_SPOTANIM,
            )

        /** Wiki "Sinister Offering": level 92, 180 xp, blood 1 + wrath 1, 9 ticks. */
        @Suppress("MagicNumber")
        val SINISTER =
            UtilitySpell(
                Components.Magic_Spellbook.SINISTER_OFFERING,
                Book.ARCEUUS,
                92,
                180.0,
                listOf(Objs.BLOODRUNE to 1, Objs.WRATHRUNE to 1),
                9,
                Seqs.HUMAN_CAST_OFFERING,
                SpotAnims.SINISTER_OFFERING_CAST_SPOTANIM,
            )
    }
}
