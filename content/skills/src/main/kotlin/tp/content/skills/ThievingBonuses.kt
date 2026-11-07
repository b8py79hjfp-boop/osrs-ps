package tp.content.skills

import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits
import kotlin.math.floor

/**
 * Equipment and diary effects on Thieving (docs/mechanics/thieving.md TH8-TH10): the pickpocket success rate (gloves of
 * silence, Ardougne diary, Thieving cape), the coin pouch cap, the Rogue outfit's double loot and the stun-avoiding
 * Shadow Veil and dodgy necklace.
 */
internal object ThievingBonuses {
    /** Worn-inventory slots (cache wear positions). */
    const val HEAD_SLOT = 0
    const val CAPE_SLOT = 1
    const val AMULET_SLOT = 2
    const val WEAPON_SLOT = 3
    const val BODY_SLOT = 4
    const val LEGS_SLOT = 7
    const val GLOVES_SLOT = 9
    const val BOOTS_SLOT = 10

    /** Thieving cape and its trimmed form (cache objs "Thieving cape" 9777, "Thieving cape(t)" 9778). */
    private val THIEVING_CAPES = setOf(9777, 9778)

    /** Wiki "Thieving cape": "an additional 10% chance of being successful ... ((chance x 1.1) x 1.1)". */
    private const val CAPE_FACTOR = 1.1

    /** Wiki "Pickpocketing" / "Ardougne Diary": the medium and hard tiers add 10% to the pickpocket chance. */
    private const val DIARY_FACTOR = 1.1

    /** Wiki "Pickpocketing": the coin pouch cap per Ardougne diary tier (none or easy, medium, hard, elite). */
    private const val POUCH_LIMIT_BASE = 28
    private const val POUCH_LIMIT_MEDIUM = 56
    private const val POUCH_LIMIT_HARD = 84
    private const val POUCH_LIMIT_ELITE = 140

    /**
     * The Ardougne cities as a bounding box (West and East Ardougne), for the medium diary's "within Ardougne" bonus. The Wiki
     * gives no bounds (OPEN-TH13), so this is an approximation: x 2456..2688, z 3264..3344 on level 0.
     */
    private val ARDOUGNE_X = 2456..2688
    private val ARDOUGNE_Z = 3264..3344

    /** Rogue outfit pieces (cache objs "Rogue top" 5553, mask 5554, trousers 5555, gloves 5556, boots 5557). */
    private val ROGUE_PIECES =
        listOf(
            HEAD_SLOT to Objs.ROGUESDEN_HELM,
            BODY_SLOT to Objs.ROGUESDEN_BODY,
            LEGS_SLOT to Objs.ROGUESDEN_LEGS,
            GLOVES_SLOT to Objs.ROGUESDEN_GLOVES,
            BOOTS_SLOT to Objs.ROGUESDEN_BOOTS,
        )

    /** Wiki "Rogue equipment": each piece adds 15% to the chance of double loot, the full set of five always doubles. */
    private const val ROGUE_PIECE_PERCENT = 15
    private const val ROGUE_SET_SIZE = 5
    private const val PERCENT = 100

    /** Wiki "Dodgy necklace" (cache obj 21143): a 25% chance to avoid the stun and damage, 10 charges shared by every necklace. */
    const val DODGY_NECKLACE = 21143
    private const val DODGY_PERCENT = 25
    private const val DODGY_CHARGES = 10L
    private const val DODGY_KEY = "thieving.dodgy_necklace_charges"

    /** Wiki "Shadow Veil": a 15% chance to avoid the stun; it is rolled before the dodgy necklace. */
    private const val VEIL_PERCENT = 15

    /** Whether the Ardougne diary bonus (+10% pickpocket chance) applies to [p] at their position now (Wiki "Pickpocketing"). */
    private fun diaryBoost(p: PlayerRef): Boolean {
        if (p.varbit(Varbits.ARDOUGNE_DIARY_HARD_COMPLETE) != 0 || p.varbit(Varbits.ARDOUGNE_DIARY_ELITE_COMPLETE) != 0) return true
        val at = p.coord
        return p.varbit(Varbits.ARDOUGNE_DIARY_MEDIUM_COMPLETE) != 0 && at.x in ARDOUGNE_X && at.z in ARDOUGNE_Z
    }

    private fun boost(value: Int, factor: Double): Int = floor(value * factor).toInt()

    /**
     * The roll to use for [p] on an NPC whose chart reads [normal] (and [gloves] with gloves of silence): the diary bonus replaces
     * the gloves' 5% (Wiki "Pickpocketing": "The Ardougne Diary perks override the gloves of silence"), and the Thieving cape
     * multiplies whichever applies by 1.1 (Wiki "Thieving cape").
     */
    fun pickpocketRate(p: PlayerRef, normal: Rate, gloves: Rate): Rate {
        val worn = p.inv(Invs.WORN)
        val base =
            when {
                diaryBoost(p) -> Rate(boost(normal.low, DIARY_FACTOR), boost(normal.high, DIARY_FACTOR))
                worn.obj(GLOVES_SLOT) == Objs.HUNTING_SILENT_GLOVES -> gloves
                else -> normal
            }
        if (worn.obj(CAPE_SLOT) !in THIEVING_CAPES) return base
        return Rate(boost(base.low, CAPE_FACTOR), boost(base.high, CAPE_FACTOR))
    }

    /** How many coin pouches of one kind [p] may hold (Wiki "Pickpocketing": 28, 56, 84, 140 by Ardougne diary tier). */
    fun pouchLimit(p: PlayerRef): Int =
        when {
            p.varbit(Varbits.ARDOUGNE_DIARY_ELITE_COMPLETE) != 0 -> POUCH_LIMIT_ELITE
            p.varbit(Varbits.ARDOUGNE_DIARY_HARD_COMPLETE) != 0 -> POUCH_LIMIT_HARD
            p.varbit(Varbits.ARDOUGNE_DIARY_MEDIUM_COMPLETE) != 0 -> POUCH_LIMIT_MEDIUM
            else -> POUCH_LIMIT_BASE
        }

    /** Whether this pickpocket's loot is doubled by the Rogue outfit: 15% per piece worn, 100% with all five (Wiki "Rogue equipment"). */
    fun rogueDoubles(p: PlayerRef): Boolean {
        val worn = p.inv(Invs.WORN)
        val pieces = ROGUE_PIECES.count { (slot, obj) -> worn.obj(slot) == obj }
        if (pieces == 0) return false
        return pieces == ROGUE_SET_SIZE || p.random(PERCENT) < pieces * ROGUE_PIECE_PERCENT
    }

    /**
     * Whether a failed pickpocket spares [p] the stun and damage: Shadow Veil first (15%), then the dodgy necklace (25%, one
     * charge per save; the last charge turns it to dust). Wiki "Pickpocketing" / "Dodgy necklace": "the Veil tries first ... If that
     * fails, the NPC shouts, and then the necklace does its 25% roll" (36.25% together).
     */
    fun stunAvoided(p: PlayerRef): Boolean {
        val veil = (p[SharedAttrs.SHADOW_VEIL_UNTIL] ?: 0L) > p.tick
        if (veil && p.random(PERCENT) < VEIL_PERCENT) return true
        if (p.inv(Invs.WORN).obj(AMULET_SLOT) != DODGY_NECKLACE || p.random(PERCENT) >= DODGY_PERCENT) return false
        val left = (p.saved(DODGY_KEY) ?: DODGY_CHARGES) - 1
        if (left > 0) {
            p.setSaved(DODGY_KEY, left)
            return true
        }
        p.setSaved(DODGY_KEY, null)
        if (p.transaction("dodgy necklace") { remove(Invs.WORN, DODGY_NECKLACE, 1) }) p.message("Your dodgy necklace crumbles to dust.")
        return true
    }
}
