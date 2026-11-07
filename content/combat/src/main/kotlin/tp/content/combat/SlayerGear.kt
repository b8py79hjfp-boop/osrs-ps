package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Varbits

/**
 * Slayer protective and required equipment (docs/mechanics/slayer-equipment.md SE1-SE10; OSRS Wiki "Slayer equipment"
 * and the monster / item pages, ids from their infoboxes). Only rules whose numbers the Wiki gives are applied; the rest
 * are OPEN in the doc.
 */
internal object SlayerGear {
    // ---- the Slayer helmet family substitutes for earmuffs, nose peg, facemask, spiny helmet (SE1)

    private val SLAYER_HELMETS: Set<Int> =
        setOf(
            11864,
            11865,
            25177,
            26674,
            19639,
            19641,
            25179,
            26675,
            19643,
            19645,
            25181,
            26676,
            19647,
            19649,
            25183,
            26677,
            21264,
            21266,
            25185,
            26678,
            21888,
            21890,
            25187,
            26679,
            23073,
            23075,
            25189,
            26680,
            24370,
            24444,
            25191,
            26681,
            25898,
            25900,
            25902,
            26682,
            25904,
            25906,
            25908,
            26683,
            25910,
            25912,
            25914,
            26684,
            29816,
            29818,
            29820,
            29822,
            33066,
            33068,
            33070,
            33072,
            33338,
            33439,
            33441,
            33443,
            33340,
            33445,
            33447,
            33449,
        )

    private fun PlayerRef.helmOr(vararg items: Int): Boolean = worn(Wear.HEAD) in SLAYER_HELMETS || items.any { wears(it) }

    private fun PlayerRef.wears(obj: Int): Boolean = (0 until WORN_SLOTS).any { worn(it) == obj }

    private const val WORN_SLOTS = 14

    /** An NPC attack changed by missing protection: [damage] always lands (unless [prayerBlocks] and the prayer is on). */
    class Unprotected(val damage: Int, val prayerBlocks: Boolean, val effect: (PlayerRef) -> Unit = {})

    /** SE2-SE8: the attack [npc] makes on [victim] without the protective item, or null to attack normally. */
    fun unprotected(npc: NpcRef, victim: EntityRef): Unprotected? {
        val p = victim as? PlayerRef ?: return null
        return when (npc.type) {
            in DUST_DEVILS -> if (p.helmOr(FACEMASK)) null else Unprotected(DUST_DEVIL_DAMAGE, false) { dustDevilDrain(it) }
            in SOURHOGS -> if (p.helmOr(REINFORCED_GOGGLES)) null else sourhog(npc)
            in CAVE_HORRORS -> if (p.wears(WITCHWOOD_ICON)) null else Unprotected(p.baseLevel(Stat.HITPOINTS) / SCREAM_DIVISOR, true)
            in COCKATRICE -> if (p.wears(MIRROR_SHIELD) || p.wears(V_SHIELD)) null else Unprotected(COCKATRICE_GAZE, true)
            in FEVER_SPIDERS -> if (p.wears(SLAYER_GLOVES)) null else Unprotected(p.baseLevel(Stat.HITPOINTS) / FEVER_DIVISOR, false)
            else -> null
        }
    }

    /** SE5 (Wiki "Sourhog"): 20-30 damage and current Attack and Defence lowered by 90%; "Argh! My eyes!". */
    private fun sourhog(npc: NpcRef): Unprotected =
        Unprotected(SOURHOG_MIN + npc.random(SOURHOG_MAX - SOURHOG_MIN + 1), prayerBlocks = false) {
            it.say("Argh! My eyes!")
            for (stat in intArrayOf(Stat.ATTACK, Stat.DEFENCE)) it.setLevel(stat, it.level(stat) / SOURHOG_KEEP_DIVISOR)
        }

    /** SE3 (Wiki "Dust devil"): Attack, Strength, Ranged, Magic fully drained; Agility, Defence, Prayer by 50%. */
    private fun dustDevilDrain(p: PlayerRef) {
        for (stat in intArrayOf(Stat.ATTACK, Stat.STRENGTH, Stat.RANGED, Stat.MAGIC)) p.setLevel(stat, 0)
        for (stat in intArrayOf(Stat.AGILITY, Stat.DEFENCE, Stat.PRAYER)) p.setLevel(stat, p.level(stat) / 2)
    }

    /** Max hit without protection for killerwatts and wall beasts (SE6, SE7: Wiki infoboxes), else null. */
    fun unprotectedMax(npc: NpcRef, victim: EntityRef): Int? {
        val p = victim as? PlayerRef ?: return null
        return when (npc.type) {
            in KILLERWATTS -> if (p.wears(INSULATED_BOOTS)) null else KILLERWATT_MAX
            in WALL_BEASTS -> if (p.helmOr(SPINY_HELMET)) null else WALL_BEAST_MAX
            in BANSHEES -> if (p.helmOr(EARMUFFS)) null else BANSHEE_MAX
            else -> null
        }
    }

    // ---- SE9: monsters only certain weapons can hurt

    /** [damage] after SE9: 0 when a player's hit can't hurt [target]. */
    fun adjust(source: EntityRef?, target: EntityRef, style: HitStyle, damage: Int): Int =
        if (source is PlayerRef && target is NpcRef && !canDamage(source, target, style)) 0 else damage

    /** True when [player]'s current attack can damage [npc] at all (turoth / kurask, harpie bug swarms, warped creatures). */
    fun canDamage(player: PlayerRef, npc: NpcRef, style: HitStyle): Boolean =
        when {
            // Burn, poison and venom (typeless) still hurt them (Wiki "Kurask": "susceptible to burn damage").
            style == HitStyle.TYPELESS -> true

            else -> canDamageWith(player, npc, style)
        }

    private fun canDamageWith(player: PlayerRef, npc: NpcRef, style: HitStyle): Boolean =
        when (npc.type) {
            in LEAFY -> leafBladed(player, style)
            in HARPIE_BUG_SWARMS -> player.worn(Wear.SHIELD) == LIT_BUG_LANTERN
            in WARPED -> player.inv(Invs.INV).total(CRYSTAL_CHIME) > 0
            else -> true
        }

    private fun leafBladed(player: PlayerRef, style: HitStyle): Boolean =
        when (style) {
            HitStyle.MELEE -> player.worn(Wear.WEAPON) in LEAF_BLADED
            HitStyle.RANGED -> player.worn(Wear.AMMO) in BROAD_AMMO
            HitStyle.MAGIC -> player[Attr.LAST_SPELL] == "Magic Dart"
            HitStyle.TYPELESS -> true
        }

    // ---- SE10: finishing items

    /** A monster that needs a finishing item: below [threshold] hitpoints it can be finished with one of [items]. */
    class Finish(val threshold: Int, val items: IntArray, val autoVarbit: Int, val consumed: Boolean, val message: String?)

    fun finish(npcType: Int): Finish? = FINISHES.entries.firstOrNull { npcType in it.key }?.value

    /** Every (item, npc type, finish) pair, for the use-item-on-NPC handlers. */
    fun finishUses(): List<Triple<Int, Int, Finish>> =
        FINISHES.flatMap { (npcs, f) -> npcs.flatMap { npc -> f.items.map { Triple(it, npc, f) } } }

    /**
     * SE10: hits never take a finishing-item monster below 1 hitpoint (OPEN-SE3: the game keeps it at 0); with the
     * auto-finish unlock and the item carried it dies at the threshold.
     */
    fun finishOnHit(player: PlayerRef?, npc: NpcRef): Boolean {
        if (npc[FINISHED] == true) return false
        val f = finish(npc.type) ?: return false
        val item = player?.takeIf { it.varbit(f.autoVarbit) == 1 && npc.hitpoints < f.threshold }?.let { carried(it, f) }
        if (player != null && item != null) {
            if (f.consumed) player.transaction("slayer finishing item") { remove(Invs.INV, item, 1) }
            f.message?.let(player::message)
            npc[FINISHED] = true
            npc.hitpoints = 0
            return true
        }
        if (npc.hitpoints <= 0) npc.hitpoints = 1
        return false
    }

    private fun carried(player: PlayerRef, f: Finish): Int? = f.items.firstOrNull { player.inv(Invs.INV).total(it) > 0 }

    /** The player uses [obj] on [npc]: kills it below the threshold; consumable items are used up either way (Wiki). */
    fun use(player: PlayerRef, npc: NpcRef, f: Finish, obj: Int) {
        if (f.consumed) player.transaction("slayer finishing item") { remove(Invs.INV, obj, 1) }
        if (npc.hitpoints >= f.threshold) return
        f.message?.let(player::message)
        npc[FINISHED] = true
        npc.hitpoints = 0
        CombatSystem.applyHit(npc, player, 0, HitStyle.TYPELESS)
    }

    private val FINISHED = tp.plugin.AttrKey<Boolean>("slayer_finished")

    // ---- ids (Wiki infoboxes, docs/mechanics/slayer-equipment.md)

    private const val FACEMASK = 4164
    private const val EARMUFFS = 4166
    private const val SPINY_HELMET = 4551
    private const val REINFORCED_GOGGLES = 24942
    private const val WITCHWOOD_ICON = 8923
    private const val MIRROR_SHIELD = 4156
    private const val V_SHIELD = 24266
    private const val SLAYER_GLOVES = 6720
    private const val INSULATED_BOOTS = 7159
    private const val LIT_BUG_LANTERN = 7053
    private const val CRYSTAL_CHIME = 28577

    private val DUST_DEVILS = setOf(423, 11238, 7249, 7404)
    private val SOURHOGS = setOf(10435)
    private val CAVE_HORRORS = setOf(1047, 1048, 1049, 1050, 1051, 7401)
    private val COCKATRICE = setOf(419, 13030, 7393)
    private val FEVER_SPIDERS = setOf(626)
    private val KILLERWATTS = setOf(469, 470)
    private val WALL_BEASTS = setOf(476)
    private val BANSHEES = setOf(414)
    private val LEAFY = setOf(427, 428, 429, 430, 10397, 410, 411, 7405)
    private val HARPIE_BUG_SWARMS = setOf(464)
    private val WARPED = (12490..12504).toSet()

    private val LEAF_BLADED = setOf(4158, 11902, 20727)
    private val BROAD_AMMO = setOf(4160, 33601, 11875, 21316)

    /** Cave horror scream ⌊base HP / 10⌋, fever spider 12.5% = ⌊base HP / 8⌋. */
    private const val SCREAM_DIVISOR = 10
    private const val FEVER_DIVISOR = 8
    private const val DUST_DEVIL_DAMAGE = 16

    /** 90% drained = a tenth kept. */
    private const val SOURHOG_KEEP_DIVISOR = 10
    private const val SOURHOG_MIN = 20
    private const val SOURHOG_MAX = 30
    private const val COCKATRICE_GAZE = 11
    private const val KILLERWATT_MAX = 15
    private const val WALL_BEAST_MAX = 18
    private const val BANSHEE_MAX = 6

    private val FINISHES: Map<Set<Int>, Finish> =
        mapOf(
            // Gargoyles: rock hammer / granite hammer kept, rock thrownhammer used up; 9 or lower per "Slayer task/Gargoyles".
            setOf(412, 413, 1543) to Finish(10, intArrayOf(4162, 21742, 21754), Varbits.SLAYER_AUTOKILL_GARGOYLES, false, null),
            // Rockslugs: bag of salt at 4 or less (Wiki "Rockslug").
            setOf(421, 14423, 422) to
                Finish(5, intArrayOf(4161), Varbits.SLAYER_AUTOKILL_ROCKSLUGS, true, "The rockslug shrivels up and dies."),
            // Desert lizards: ice cooler below 5 (Wiki "Desert Lizard").
            setOf(458, 459, 460, 461, 462, 463, 12003, 12004) to
                Finish(
                    5,
                    intArrayOf(6696),
                    Varbits.SLAYER_AUTOKILL_DESERTLIZARDS,
                    true,
                    "The lizard shudders and collapses from the freezing water.",
                ),
            // Zygomites: fungicide spray (10..1 charges = 7421..7430) under 8 (Wiki "Mutated Zygomite").
            setOf(537, 1024, 7797) to Finish(8, (7421..7430).toList().toIntArray(), Varbits.SLAYER_AUTOKILL_ZYGOMITES, false, null),
        )
}
