package tp.content.magic

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.ObjRef
import tp.plugin.PlayerScript
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * Telekinetic Grab (docs/mechanics/magic-utility.md TG1-TG3): picks up a ground item seen from up to 10 tiles away,
 * through obstacles, for a law and an air rune.
 */
public class TelekineticGrab : ContentPlugin {
    override fun ContentScope.register() {
        // The approach script runs within the spell's range and line of sight; the engine's interaction does the walking.
        onApObjT(SPELL.component) { obj -> grab(this, obj) }
    }

    private fun grab(script: PlayerScript, obj: ObjRef) {
        val player = script.player
        if (script.currentApRange != RANGE) {
            script.apRange(RANGE)
            return
        }
        if (!Casting.ready(player, SPELL)) return
        // TG2: the item must be on the caster's level.
        if (obj.coord.level != player.coord.level) return
        val taken =
            player.transaction("telekinetic grab") {
                SpellRunes.take(this, player, SPELL.runes) && take(obj, Invs.INV)
            }
        if (!taken) return player.message(if (player.inv(Invs.INV).freeSlots == 0) NO_SPACE else Casting.NO_RUNES)
        Casting.finish(player, SPELL)
        player.spotAnimAt(obj.coord, SpotAnims.TELEGRAB_IMPACT)
    }

    private companion object {
        /** OPEN-MU4: the game's usual full-backpack text; the Wiki gives none for this spell. */
        const val NO_SPACE = "You don't have enough inventory space to hold that item."

        /** Wiki "Telekinetic Grab": "up to 10 tiles away". */
        const val RANGE = 10

        /**
         * Wiki "Telekinetic Grab": level 33, 43 xp, law 1 + air 1, speed 3 ticks. Visuals: cache `human_casttelegrab` and
         * `telegrab_casting` (the impact graphic plays on the item's tile).
         */
        @Suppress("MagicNumber")
        val SPELL =
            UtilitySpell(
                Components.Magic_Spellbook.TELEGRAB,
                Book.STANDARD,
                33,
                43.0,
                listOf(Objs.LAWRUNE to 1, Objs.AIRRUNE to 1),
                3,
                Seqs.HUMAN_CASTTELEGRAB,
                SpotAnims.TELEGRAB_CASTING,
            )
    }
}
