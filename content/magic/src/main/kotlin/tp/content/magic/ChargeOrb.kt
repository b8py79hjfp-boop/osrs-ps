package tp.content.magic

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerScript
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * Charge Water, Earth, Fire and Air Orb (docs/mechanics/magic-utility.md CO1-CO2): cast on the matching obelisk, each
 * cast turns one unpowered orb into the elemental orb.
 */
public class ChargeOrb : ContentPlugin {
    /** An orb spell: the obelisk's element word (cache loc name), the orb it makes and the spell itself. */
    private class Orb(val element: String, val orb: Int, val spell: UtilitySpell)

    override fun ContentScope.register() {
        for (orb in ORBS) onOpLocT(orb.spell.component) { loc -> charge(this, orb, loc) }
    }

    private fun charge(script: PlayerScript, orb: Orb, loc: LocRef) {
        val player = script.player
        // CO1: only the obelisk of the spell's own element (Wiki: "Needs to be cast on a water obelisk").
        val name = player.locName(loc.id).lowercase()
        if (!name.contains("obelisk") || !name.contains(orb.element)) return player.message(WRONG_TARGET)
        if (!Casting.ready(player, orb.spell)) return
        val backpack = player.inv(Invs.INV)
        val slot = (0 until backpack.size).firstOrNull { backpack.obj(it) == Objs.STAFFORB }
        if (slot == null) return player.message(NO_ORB)
        val done =
            player.transaction("charge orb") {
                SpellRunes.take(this, player, orb.spell.runes) && replaceAt(Invs.INV, slot, Objs.STAFFORB, orb.orb)
            }
        if (!done) return player.message(Casting.NO_RUNES)
        Casting.finish(player, orb.spell)
    }

    private companion object {
        /** OPEN-MU4: the Wiki gives no text for a cast on the wrong target or without an orb. */
        const val WRONG_TARGET = "Nothing interesting happens."
        const val NO_ORB = "You need an unpowered orb to cast this spell."

        @Suppress("LongParameterList")
        private fun orb(component: Int, element: String, rune: Int, level: Int, xp: Double, orb: Int, spot: Int): Orb =
            Orb(
                element,
                orb,
                UtilitySpell(
                    component,
                    Book.STANDARD,
                    level,
                    xp,
                    listOf(Objs.COSMICRUNE to COSMIC_COST, rune to ELEMENT_COST),
                    ORB_TICKS,
                    Seqs.HUMAN_CASTCHARGEORB,
                    spot,
                ),
            )

        /** Wiki "Charge Water Orb" etc.: 3 cosmic runes and 30 of the element, plus the unpowered orb. */
        const val COSMIC_COST = 3
        const val ELEMENT_COST = 30

        /** Wiki: speed 3 ticks (6 on autocast). */
        const val ORB_TICKS = 3

        /**
         * Levels and xp from the Wiki spell pages: water 56 / 66, earth 60 / 70, fire 63 / 73, air 66 / 76. Visuals: cache
         * `human_castchargeorb` and `charge<element>orb_casting` (the cache calls air "wind").
         */
        @Suppress("MagicNumber")
        val ORBS: List<Orb> =
            listOf(
                orb(
                    Components.Magic_Spellbook.CHARGE_WATER_ORB,
                    "water",
                    Objs.WATERRUNE,
                    56,
                    66.0,
                    Objs.WATER_ORB,
                    SpotAnims.CHARGEWATERORB_CASTING,
                ),
                orb(
                    Components.Magic_Spellbook.CHARGE_EARTH_ORB,
                    "earth",
                    Objs.EARTHRUNE,
                    60,
                    70.0,
                    Objs.EARTH_ORB,
                    SpotAnims.CHARGEEARTHORB_CASTING,
                ),
                orb(
                    Components.Magic_Spellbook.CHARGE_FIRE_ORB,
                    "fire",
                    Objs.FIRERUNE,
                    63,
                    73.0,
                    Objs.FIRE_ORB,
                    SpotAnims.CHARGEFIREORB_CASTING,
                ),
                orb(
                    Components.Magic_Spellbook.CHARGE_AIR_ORB,
                    "air",
                    Objs.AIRRUNE,
                    66,
                    76.0,
                    Objs.AIR_ORB,
                    SpotAnims.CHARGEWINDORB_CASTING,
                ),
            )
    }
}
