package tp.content.skills

import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * Mining enhancers (docs/mechanics/mining.md MN12-MN15; Wiki "Mining enhancers"): the amulet of glory's gem odds, mining
 * gloves that keep a rock from depleting, and the 10% / 5% extra ore of the Varrock armour and the Mining cape.
 */
internal object MiningEnhancers {
    /** Worn-inventory slots (cache wear positions): cape 1, amulet 2, body 4, gloves 9. */
    private const val CAPE_SLOT = 1
    private const val AMULET_SLOT = 2
    private const val BODY_SLOT = 4
    private const val GLOVES_SLOT = 9

    /** Wiki "Mining enhancers": Varrock armour 1..4 give a 10% chance of an extra ore; the Mining cape 5%. */
    const val VARROCK_EXTRA_ONE_IN = 10
    const val CAPE_EXTRA_ONE_IN = 20

    /** Wiki "Mining gem drop table": with a charged glory a failed 1/256 gets another 3/256 roll (about 1/64). */
    const val GLORY_EXTRA_GEM_IN_256 = 3

    /** Wiki "Gem rocks" success chart: normal 28 / 70, charged glory 84 / 210. */
    const val GLORY_GEM_LOW = 84
    const val GLORY_GEM_HIGH = 210

    /** Varrock armour 1..4 (cache objs 13104..13107) -> tier. */
    private val VARROCK =
        mapOf(
            Objs.VARROCK_ARMOUR_EASY to 1,
            Objs.VARROCK_ARMOUR_MEDIUM to 2,
            Objs.VARROCK_ARMOUR_HARD to 3,
            Objs.VARROCK_ARMOUR_ELITE to 4,
        )

    /** Charged glory objs, resolved by cache name when the world starts (the (1)..(6) amulets, the (t) ones and the eternal glory). */
    private var glories: Set<Int> = emptySet()

    /** Gloves objs by name: [0] mining gloves, [1] superior, [2] expert. */
    private var gloves: IntArray = IntArray(0)

    fun resolve(world: WorldRef) {
        val names = (1..GLORY_CHARGES).flatMap { listOf("Amulet of glory($it)", "Amulet of glory(t$it)") } + "Amulet of eternal glory"
        glories = names.flatMap { world.objIdsNamed(it) }.toSet()
        gloves = GLOVES_NAMES.map { world.objIdsNamed(it).minOrNull() ?: -1 }.toIntArray()
    }

    /** Whether [p] wears a charged amulet of glory. */
    fun glory(p: PlayerRef): Boolean = p.inv(Invs.WORN).obj(AMULET_SLOT) in glories

    /**
     * Varrock-armour tier a rock needs to benefit (Wiki "Mining enhancers"): 1 up to gold (clay, copper, tin, blurite, iron,
     * silver, coal, gold), 2 mithril, sandstone and granite, 3 adamantite, 4 runite and amethyst; 0 = never.
     */
    private fun rockTier(rock: Rock): Int {
        if (rock.kind == RockKind.CASCADE) return TIER_MITHRIL
        if (rock.kind != RockKind.ORE && rock.kind != RockKind.AMETHYST) return 0
        return when {
            rock.kind == RockKind.AMETHYST || rock.level >= RUNITE_LEVEL -> TIER_RUNITE
            rock.level >= ADAMANT_LEVEL -> TIER_ADAMANT
            rock.level >= MITHRIL_LEVEL -> TIER_MITHRIL
            else -> 1
        }
    }

    /** Extra ores [p] gets from this success (0..2): Varrock armour 10% for rocks up to its tier, cape 5% up to adamantite. */
    fun extraOres(p: PlayerRef, rock: Rock): Int {
        val tier = rockTier(rock)
        if (tier == 0) return 0
        val worn = p.inv(Invs.WORN)
        var extra = 0
        val armour = VARROCK[worn.obj(BODY_SLOT)] ?: 0
        if (armour >= tier && p.random(VARROCK_EXTRA_ONE_IN) == 0) extra++
        val cape = worn.obj(CAPE_SLOT).let { it == Objs.SKILLCAPE_MINING || it == Objs.SKILLCAPE_MINING_TRIMMED }
        if (cape && tier <= TIER_ADAMANT && p.random(CAPE_EXTRA_ONE_IN) == 0) extra++
        return extra
    }

    /**
     * How many times the worn gloves save [rock] from depleting (Wiki "Mining enhancers"): mining gloves 1 for copper, tin,
     * iron, silver, coal and gold; superior 2 there and 1 for mithril and adamantite; expert 3, 2, 1 and 1 for sandstone and
     * runite and amethyst.
     */
    fun glovesSaves(p: PlayerRef, rock: Rock): Int {
        val worn = p.inv(Invs.WORN).obj(GLOVES_SLOT)
        val level = gloves.indexOf(worn).takeIf { it >= 0 && worn >= 0 } ?: return 0
        val base = rock.kind == RockKind.ORE && rock.level in BASE_GLOVE_LEVELS && rock.yields[0].obj in GLOVE_BASE_ORES
        val mid = rock.kind == RockKind.ORE && (rock.level == MITHRIL_LEVEL || rock.level == ADAMANT_LEVEL)
        val top =
            rock.kind == RockKind.AMETHYST || rock.level == RUNITE_LEVEL || (rock.kind == RockKind.CASCADE && rock.level == SANDSTONE_LEVEL)
        return when {
            base -> level + 1
            mid -> if (level >= 1) level else 0
            top -> if (level >= 2) 1 else 0
            else -> 0
        }
    }

    private const val GLORY_CHARGES = 6
    private const val TIER_MITHRIL = 2
    private const val TIER_ADAMANT = 3
    private const val TIER_RUNITE = 4
    private const val MITHRIL_LEVEL = 55
    private const val ADAMANT_LEVEL = 70
    private const val RUNITE_LEVEL = 85
    private const val SANDSTONE_LEVEL = 35
    private val BASE_GLOVE_LEVELS = 1..40
    private val GLOVE_BASE_ORES = setOf(Objs.COPPER_ORE, Objs.TIN_ORE, Objs.IRON_ORE, Objs.SILVER_ORE, Objs.COAL, Objs.GOLD_ORE)
    private val GLOVES_NAMES = listOf("Mining gloves", "Superior mining gloves", "Expert mining gloves")
}
