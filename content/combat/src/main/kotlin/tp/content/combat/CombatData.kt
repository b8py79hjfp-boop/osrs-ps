package tp.content.combat

/**
 * Obj param ids for equipment stats, verified against cache 2757 (docs/mechanics/combat-data.md): e.g. Abyssal whip
 * {1=82, 10=82, 14=4}, Armadyl godsword {1=132, 2=80, 10=132, 11=8, 14=6}.
 */
internal object Param {
    const val ATTACK_STAB = 0
    const val ATTACK_SLASH = 1
    const val ATTACK_CRUSH = 2
    const val ATTACK_MAGIC = 3
    const val ATTACK_RANGED = 4
    const val DEFENCE_STAB = 5
    const val DEFENCE_SLASH = 6
    const val DEFENCE_CRUSH = 7
    const val DEFENCE_MAGIC = 8
    const val DEFENCE_RANGED = 9
    const val MELEE_STRENGTH = 10
    const val PRAYER = 11
    const val RANGED_STRENGTH = 12

    /** The second ranged strength param (twisted bow 20, necklace of anguish 5, Masori (f) 2): summed with [RANGED_STRENGTH]. */
    const val RANGED_STRENGTH_ALT = 189

    /** Ranged weapon attack range (magic shortbow 7, rune crossbow 7). */
    const val ATTACK_RANGE = 13

    /** Weapon attack speed in ticks (whip 4, godswords 6, granite maul 7). */
    const val ATTACK_SPEED = 14

    /** Magic damage %, in tenths of a percent (RS Mod `magic_damage`). OPEN-D1: unit not verified on 2757. */
    const val MAGIC_DAMAGE = 299

    /** Param ids 0..12 summed over worn objs. */
    const val BONUS_COUNT = 13
}

/** Worn inventory slots = appearance wear positions (client `wearpos`). */
internal object Wear {
    const val HEAD = 0
    const val CAPE = 1
    const val NECK = 2
    const val WEAPON = 3
    const val BODY = 4
    const val SHIELD = 5
    const val LEGS = 7
    const val HANDS = 9
    const val FEET = 10
    const val RING = 12
    const val AMMO = 13
    const val SLOTS = 14
}

internal enum class CombatStyle { MELEE, RANGED, MAGIC }

/** Attack type: which equipment attack bonus and which defence bonus it uses. */
internal enum class AttackType(val attackParam: Int, val defenceParam: Int, val style: CombatStyle) {
    STAB(Param.ATTACK_STAB, Param.DEFENCE_STAB, CombatStyle.MELEE),
    SLASH(Param.ATTACK_SLASH, Param.DEFENCE_SLASH, CombatStyle.MELEE),
    CRUSH(Param.ATTACK_CRUSH, Param.DEFENCE_CRUSH, CombatStyle.MELEE),
    RANGED(Param.ATTACK_RANGED, Param.DEFENCE_RANGED, CombatStyle.RANGED),
    MAGIC(Param.ATTACK_MAGIC, Param.DEFENCE_MAGIC, CombatStyle.MAGIC),
}

/** Combat stance and its invisible bonuses (Combat Options: CO:17-46). */
internal enum class Stance(
    val attackBonus: Int,
    val strengthBonus: Int,
    val defenceBonus: Int,
    val rangedBonus: Int = 0,
    val magicBonus: Int = 0,
) {
    ACCURATE(3, 0, 0),
    AGGRESSIVE(0, 3, 0),
    DEFENSIVE(0, 0, 3),
    CONTROLLED(1, 1, 1),
    ACCURATE_RANGED(0, 0, 0, rangedBonus = 3),
    RAPID(0, 0, 0),
    LONGRANGE(0, 0, 3),
    ACCURATE_MAGIC(0, 0, 0, magicBonus = 3),
    LONGRANGE_MAGIC(0, 0, 3, magicBonus = 1),

    /**
     * Manual spell casts. Magic accuracy uses +9 (the Wiki DPS calculator: `effectiveLevel += 9`, +2 more when
     * accurate); the Wiki "Damage per second/Magic" page shows +8 (SOURCE-CONFLICT, combat-formulas.md: we match the
     * calculator, brief §8). Expressed here as a +1 magic stance bonus on top of the shared +8.
     */
    SPELL(0, 0, 0, magicBonus = 1),
}

/** A weapon category's four combat-tab slots (null = unused). */
internal class WeaponCategory(val id: Int, val slots: List<Pair<Stance, AttackType>?>) {
    val style: CombatStyle get() = slots.firstNotNullOf { it }.second.style
}

/**
 * Weapon categories (client ids for varbit `combat_weapon_category`) and their stances and attack types. Ids, stances
 * and types: RS Mod `WeaponCategory`, `StyleEnums`, `TypeEnums` (ISC; three-style categories use slots 0, 1, 3).
 * The cache has no obj → weapon category mapping in rev 242 (combat-data.md), so the category of each weapon comes from
 * [WeaponData] (RS Mod's obj data, D-054); [of] maps the obj `category` param for weapons it doesn't list.
 */
internal object Weapons {
    private val A = Stance.ACCURATE
    private val AG = Stance.AGGRESSIVE
    private val D = Stance.DEFENSIVE
    private val C = Stance.CONTROLLED
    private val AR = Stance.ACCURATE_RANGED
    private val R = Stance.RAPID
    private val L = Stance.LONGRANGE
    private val AM = Stance.ACCURATE_MAGIC
    private val LM = Stance.LONGRANGE_MAGIC
    private val STAB = AttackType.STAB
    private val SLASH = AttackType.SLASH
    private val CRUSH = AttackType.CRUSH
    private val RANGED = AttackType.RANGED
    private val MAGIC = AttackType.MAGIC

    private fun cat(id: Int, vararg slots: Pair<Stance, AttackType>?): WeaponCategory =
        WeaponCategory(id, if (slots.size == THREE) listOf(slots[0], slots[1], null, slots[2]) else slots.toList())

    val UNARMED = cat(0, A to CRUSH, AG to CRUSH, D to CRUSH)
    val AXE = cat(1, A to SLASH, AG to SLASH, AG to CRUSH, D to SLASH)
    val BLUNT = cat(2, A to CRUSH, AG to CRUSH, D to CRUSH)
    val BOW = cat(3, AR to RANGED, R to RANGED, L to RANGED)
    val CLAW = cat(4, A to SLASH, AG to SLASH, C to STAB, D to SLASH)
    val CROSSBOW = cat(5, AR to RANGED, R to RANGED, L to RANGED)
    val SALAMANDER = cat(6, AG to SLASH, R to RANGED, D to MAGIC, null)
    val CHINCHOMPA = cat(7, AR to RANGED, R to RANGED, L to RANGED)
    val GUN = cat(8, null, AG to CRUSH, null, null)
    val SLASH_SWORD = cat(9, A to SLASH, AG to SLASH, C to STAB, D to SLASH)
    val TWO_HANDED = cat(10, A to SLASH, AG to SLASH, AG to CRUSH, D to SLASH)
    val PICKAXE = cat(11, A to STAB, AG to STAB, AG to CRUSH, D to STAB)
    val POLEARM = cat(12, C to STAB, AG to SLASH, D to STAB)
    val POLESTAFF = cat(13, A to CRUSH, AG to CRUSH, D to CRUSH)
    val SCYTHE = cat(14, A to SLASH, AG to SLASH, AG to CRUSH, D to SLASH)
    val SPEAR = cat(15, C to STAB, C to SLASH, C to CRUSH, D to STAB)
    val SPIKED = cat(16, A to CRUSH, AG to CRUSH, C to STAB, D to CRUSH)
    val STAB_SWORD = cat(17, A to STAB, AG to STAB, AG to SLASH, D to STAB)
    val STAFF = cat(18, A to CRUSH, AG to CRUSH, D to CRUSH)
    val THROWN = cat(19, AR to RANGED, R to RANGED, L to RANGED)
    val WHIP = cat(20, A to SLASH, C to SLASH, D to SLASH)
    val BLADED_STAFF = cat(21, A to STAB, AG to SLASH, D to CRUSH)
    val BANNER = cat(22, A to SLASH, AG to SLASH, AG to CRUSH, D to SLASH)

    /** Godswords: the 2h sword stances under their own client id (RS Mod: the tab shows 23 for them). */
    val GODSWORD = cat(23, A to SLASH, AG to SLASH, AG to CRUSH, D to SLASH)
    val POWERED_STAFF = cat(24, AM to MAGIC, AM to MAGIC, LM to MAGIC)
    val BLUDGEON = cat(27, AG to CRUSH, AG to CRUSH, AG to CRUSH)
    val BULWARK = cat(28, A to CRUSH, null, null, AG to CRUSH)

    private val byId: Map<Int, WeaponCategory> =
        listOf(
            UNARMED,
            AXE,
            BLUNT,
            BOW,
            CLAW,
            CROSSBOW,
            SALAMANDER,
            CHINCHOMPA,
            GUN,
            SLASH_SWORD,
            TWO_HANDED,
            PICKAXE,
            POLEARM,
            POLESTAFF,
            SCYTHE,
            SPEAR,
            SPIKED,
            STAB_SWORD,
            STAFF,
            THROWN,
            WHIP,
            BLADED_STAFF,
            BANNER,
            GODSWORD,
            POWERED_STAFF,
            BLUDGEON,
            BULWARK,
        ).associateBy { it.id }

    fun byId(id: Int): WeaponCategory? = byId[id]

    /** Obj `category` (cache) → weapon category, for weapons [WeaponData] doesn't list (combat-data.md). */
    private val byObjCategory: Map<Int, WeaponCategory> =
        mapOf(
            1 to STAFF, // staves (Staff of air, Ancient staff, Ahrim's staff)
            21 to SLASH_SWORD, // scimitars, longswords
            24 to THROWN, // darts, knives, toxic blowpipe
            25 to STAB_SWORD, // daggers, shortswords
            26 to BLUNT, // mauls (granite maul, elder maul)
            37 to CROSSBOW, // rune crossbow, armadyl crossbow
            61 to TWO_HANDED, // godswords
            64 to BOW, // magic shortbow
            65 to CLAW, // dragon claws
            150 to WHIP, // abyssal whip
            572 to CHINCHOMPA, // grey, red and black chinchompas
        )

    fun of(objCategory: Int): WeaponCategory = byObjCategory[objCategory] ?: UNARMED

    /** The weapon category of [obj]: [WeaponData] first, then the obj category param. */
    fun forObj(obj: Int, objCategory: Int): WeaponCategory = WeaponData.of(obj)?.category?.let(::byId) ?: of(objCategory)

    private const val THREE = 3
}
