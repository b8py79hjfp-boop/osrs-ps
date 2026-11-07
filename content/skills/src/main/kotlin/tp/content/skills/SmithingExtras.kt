package tp.content.skills

import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * Smithing outfits and armour (docs/mechanics/smithing.md SM12-SM13): the Smiths' uniform's chance of a faster anvil
 * smith and the Varrock armour's chance of two bars at the Edgeville furnace.
 */
internal object SmithingExtras {
    /** Wiki "Smiths' uniform": each piece gives a 20% chance to take 1 tick off an anvil smith (5 to 4), 100% with the full set. */
    private const val UNIFORM_PERCENT_PER_PIECE = 20

    /** Worn-inventory slots (cache wear positions): body 4, legs 7, gloves 9, boots 10. */
    private const val BODY_SLOT = 4
    private const val LEGS_SLOT = 7
    private const val GLOVES_SLOT = 9
    private const val BOOTS_SLOT = 10

    /** The uniform's gloves are either pair (Wiki "Smiths' uniform": the smiths gloves (i) combine the gloves and the ice gloves). */
    private val UNIFORM_GLOVES = setOf(Objs.SMITHING_UNIFORM_GLOVES, Objs.SMITHING_UNIFORM_GLOVES_ICE)

    /** Ticks of the next anvil smith for [p]: [base], one less on the uniform's roll. */
    fun smithTicks(p: PlayerRef, base: Int): Int {
        val worn = p.inv(Invs.WORN)
        var pieces = 0
        if (worn.obj(BODY_SLOT) == Objs.SMITHING_UNIFORM_TORSO) pieces++
        if (worn.obj(LEGS_SLOT) == Objs.SMITHING_UNIFORM_LEGS) pieces++
        if (worn.obj(BOOTS_SLOT) == Objs.SMITHING_UNIFORM_BOOTS) pieces++
        if (worn.obj(GLOVES_SLOT) in UNIFORM_GLOVES) pieces++
        if (pieces == 0) return base
        return if (p.random(PERCENT) < pieces * UNIFORM_PERCENT_PER_PIECE) base - 1 else base
    }

    /** Wiki "Varrock armour": 10% chance of 2 bars at the Edgeville furnace, 1 up to steel, 2 up to mithril, 3 up to adamantite, 4 any. */
    private const val VARROCK_ONE_IN = 10

    /** Highest bar level each Varrock armour tier doubles (steel 30, mithril 50, adamantite 70; tier 4 has none). */
    private val TIER_MAX_LEVEL =
        mapOf(
            Objs.VARROCK_ARMOUR_EASY to 30,
            Objs.VARROCK_ARMOUR_MEDIUM to 50,
            Objs.VARROCK_ARMOUR_HARD to 70,
            Objs.VARROCK_ARMOUR_ELITE to Int.MAX_VALUE,
        )

    /** Wiki "Edgeville furnace" map polygon: x 3105..3111, z 3496..3502 (a tile of margin for where the player stands). */
    private val EDGEVILLE_X = 3104..3112
    private val EDGEVILLE_Z = 3495..3503

    fun varrockDouble(p: PlayerRef, r: Recipe): Boolean {
        val max = TIER_MAX_LEVEL[p.inv(Invs.WORN).obj(BODY_SLOT)] ?: return false
        val at = p.coord
        if (at.level != 0 || at.x !in EDGEVILLE_X || at.z !in EDGEVILLE_Z) return false
        return r.level <= max && p.random(VARROCK_ONE_IN) == 0
    }

    private const val PERCENT = 100
}
