package tp.content.raids.toa

import tp.content.raids.Party
import tp.plugin.PlayerRef
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * The five invocation presets (toa.md TA6; client-contract 4.3): slot `p` (0..4) is the three varps
 * `TOA_INVOCATIONS_PRESET_1A + 3p + {0, 1, 2}` = the words of script 6729. `TOA_PRESET_SELECTED` (14541) is the
 * highlighted slot + 1 (0 none). The caller ([ToaBoard]) has checked that the viewer leads the viewed, forming party.
 */
internal object ToaPresets {
    /** TA6: "up to five invocation presets". */
    const val SLOTS = 5

    /** Words per preset (client-contract 2.5). */
    private const val WORDS = 3

    private fun varp(slot: Int, word: Int): Int = Varps.TOA_INVOCATIONS_PRESET_1A + WORDS * slot + word

    /** The slot Select highlighted, or -1 when none (the varbit holds slot + 1). */
    private fun selected(p: PlayerRef): Int = (p.varbit(Varbits.TOA_PRESET_SELECTED) - 1).takeIf { it in 0 until SLOTS } ?: -1

    fun select(p: PlayerRef, slot: Int) {
        if (p.varbit(Varbits.TOA_PRESET_SELECTED) != slot + 1) p.setVarbit(Varbits.TOA_PRESET_SELECTED, slot + 1)
    }

    /** "Clear": empties the three varps of [slot]; the highlight stays where it is. */
    fun clear(p: PlayerRef, slot: Int) {
        for (w in 0 until WORDS) p.setVarp(varp(slot, w), 0)
    }

    /** "Save" (pause sub 7): the party's words into the selected slot. */
    fun save(p: PlayerRef, party: Party) {
        if (!ToaBoard.canEdit(p, party)) return
        val slot = selected(p)
        if (slot < 0) {
            p.message(NO_SLOT)
            return
        }
        val words = ToaInvocations.words(party)
        for (w in 0 until WORDS) p.setVarp(varp(slot, w), words[w])
        ToaBoard.setInvocations(p, party, words)
    }

    /**
     * "Load" (pause sub 6): the selected slot's words become the party's. The words are replayed through
     * [ToaInvocations.toggle] so exclusivity, prerequisites and the Blazing Tombs rule hold even for an old save.
     */
    fun load(p: PlayerRef, party: Party) {
        if (!ToaBoard.canEdit(p, party)) return
        val slot = selected(p)
        if (slot < 0) {
            p.message(NO_SLOT)
            return
        }
        val saved = IntArray(WORDS) { p.varp(varp(slot, it)) }
        if (saved.all { it == 0 }) {
            p.message(EMPTY_SLOT)
            return
        }
        var words = IntArray(WORDS)
        // Skip one already ticked as an earlier invocation's prerequisite: toggling it again would untick it and its dependants.
        for (inv in ToaInvocations.active(p, saved)) {
            if (!ToaInvocations.isActive(words, inv)) words = ToaInvocations.toggle(p, words, inv.key)
        }
        ToaBoard.setInvocations(p, party, words)
    }

    // OPEN-TAUI1: texts not captured; placeholders until seen in game.
    private const val NO_SLOT = "Select a preset first."
    private const val EMPTY_SLOT = "That preset is empty."
}
