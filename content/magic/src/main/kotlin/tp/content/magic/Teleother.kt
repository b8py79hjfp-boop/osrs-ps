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
import tp.util.CoordGrid

/**
 * Teleother Lumbridge, Falador and Camelot (docs/mechanics/magic-utility.md TO1-TO3): the target, who must have Accept Aid
 * on, is asked whether to be teleported and then goes to the town.
 */
public class Teleother : ContentPlugin {
    /** A teleother spell and where it sends the target. */
    private class Other(val spell: UtilitySpell, val town: String, val destination: CoordGrid)

    override fun ContentScope.register() {
        for (other in OTHERS) {
            onApPlayerT(other.spell.component) { target -> cast(this, other, target) }
        }
    }

    private fun cast(script: PlayerScript, other: Other, target: PlayerRef) {
        val caster = script.player
        if (script.currentApRange != RANGE) {
            script.apRange(RANGE)
            return
        }
        if (target == caster || !Casting.ready(caster, other.spell)) return
        // TO1: the target must have Accept Aid on; Tele Block and the Wilderness limit apply to the target (teleports.md T4).
        if (target.varbit(Varbits.OPTION_ACCEPTAID) != 1) return caster.message(NOT_ACCEPTING)
        if (Teleports.denial(target, Teleports.WILDERNESS_LIMIT) != null) return caster.message(CANNOT_TELEPORT)
        if (!caster.transaction("teleother runes") { SpellRunes.take(this, caster, other.spell.runes) }) {
            return caster.message(Casting.NO_RUNES)
        }
        Casting.finish(caster, other.spell)
        caster.face(target)
        target.spotAnim(SpotAnims.TELEPORT_OTHER_IMPACT)
        // TO2: the target answers a prompt; only a "Yes" while still free to teleport sends them.
        target.choiceDialog("${caster.username} wishes to teleport you to ${other.town}. Teleport?", listOf("Yes", "No")) { pick ->
            if (pick == 1 && !Teleports.isBusy(this) && Teleports.denial(this, Teleports.WILDERNESS_LIMIT) == null) {
                Teleports.start(this, OTHER_STAGES, OTHER_TICKS, other.destination)
            }
        }
    }

    private companion object {
        /** OPEN-MU4: no Wiki text for these refusals. */
        const val NOT_ACCEPTING = "That player is not accepting aid."
        const val CANNOT_TELEPORT = "That player can't be teleported right now."

        /** Spells are cast from up to 10 tiles (Wiki "Attack range": magic 10); OPEN-MU6 for teleother specifically. */
        const val RANGE = 10

        /** The target's teleport: seq 1816 (`human_teleport_other_impact`) like the Lunar teleports; stall as a standard cast. */
        val OTHER_STAGES = listOf(CastStage(0, Seqs.HUMAN_TELEPORT_OTHER_IMPACT, SpotAnims.TELEPORT_OTHER_IMPACT))
        const val OTHER_TICKS = 3

        /** Wiki spell infoboxes: speed 10 ticks. */
        const val CAST_TICKS = 10

        @Suppress("LongParameterList")
        private fun other(component: Int, level: Int, xp: Double, town: String, x: Int, z: Int, vararg runes: Pair<Int, Int>): Other =
            Other(
                UtilitySpell(
                    component,
                    Book.STANDARD,
                    level,
                    xp,
                    runes.toList(),
                    CAST_TICKS,
                    Seqs.HUMAN_TELEPORT_OTHER_CASTING,
                    SpotAnims.TELEPORT_OTHER_CASTING,
                ),
                town,
                CoordGrid(x, z, 0),
            )

        /**
         * Wiki "Teleother Lumbridge / Falador / Camelot": levels 74 / 82 / 90, xp 84 / 92 / 100, runes, and the destination
         * (the centre of the Lumbridge polygon; the Falador and Camelot pins).
         */
        @Suppress("MagicNumber")
        val OTHERS: List<Other> =
            listOf(
                other(
                    Components.Magic_Spellbook.TELEOTHER_LUMBRIDGE,
                    74,
                    84.0,
                    "Lumbridge",
                    3221,
                    3218,
                    Objs.SOULRUNE to 1,
                    Objs.EARTHRUNE to 1,
                    Objs.LAWRUNE to 1,
                ),
                other(
                    Components.Magic_Spellbook.TELEOTHER_FALADOR,
                    82,
                    92.0,
                    "Falador",
                    2964,
                    3378,
                    Objs.SOULRUNE to 1,
                    Objs.LAWRUNE to 1,
                    Objs.WATERRUNE to 1,
                ),
                other(
                    Components.Magic_Spellbook.TELEOTHER_CAMELOT,
                    90,
                    100.0,
                    "Camelot",
                    2757,
                    3479,
                    Objs.SOULRUNE to 2,
                    Objs.LAWRUNE to 1,
                ),
            )
    }
}
