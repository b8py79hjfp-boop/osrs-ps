package tp.content.skills

import tp.symbols.Objs
import tp.symbols.Seqs

/** Animations, ticks and menu choices of Fletching (docs/mechanics/fletching.md). Recipes themselves are rows of `recipes.tsv`. */
internal object FletchingData {
    /** Sets of arrows and javelins take two ticks (Wiki "Pay-to-play Fletching training": "every two game ticks ... sets of 10 or 15"). */
    const val SET_TICKS = 2

    /** Recipes with 0 `ticks` are made at once, one set per use (darts, bolts; Wiki "Pay-to-play Fletching training"). */
    const val INSTANT_TICKS = 0

    /** The items that make a recipe a "string" job (bow string, crossbow string): the menu then uses the String verb. */
    val STRINGS: Set<Int> = setOf(Objs.BOW_STRING, Objs.XBOWS_CROSSBOW_STRING)

    /** Strung bows by lower-case product name -> the stringing animation (cache seqs `stringing_*`). */
    val BOW_STRINGING: Map<String, Int> =
        mapOf(
            "shortbow" to Seqs.STRINGING_SHORTBOW,
            "oak shortbow" to Seqs.STRINGING_OAK_SHORTBOW,
            "willow shortbow" to Seqs.STRINGING_WILLOW_SHORTBOW,
            "maple shortbow" to Seqs.STRINGING_MAPLE_SHORTBOW,
            "yew shortbow" to Seqs.STRINGING_YEW_SHORTBOW,
            "magic shortbow" to Seqs.STRINGING_MAGIC_SHORTBOW,
            "longbow" to Seqs.STRINGING_LONGBOW,
            "oak longbow" to Seqs.STRINGING_OAK_LONGBOW,
            "willow longbow" to Seqs.STRINGING_WILLOW_LONGBOW,
            "maple longbow" to Seqs.STRINGING_MAPLE_LONGBOW,
            "yew longbow" to Seqs.STRINGING_YEW_LONGBOW,
            "magic longbow" to Seqs.STRINGING_MAGIC_LONGBOW,
        )

    /** First word of the lower-case metal -> assembling an unstrung crossbow (cache seqs `xbows_fletching_*`). */
    val CROSSBOW_ASSEMBLY: Map<String, Int> =
        mapOf(
            "bronze" to Seqs.XBOWS_FLETCHING_WOOD_BRONZE,
            "blurite" to Seqs.XBOWS_FLETCHING_OAK_BLURITE,
            "iron" to Seqs.XBOWS_FLETCHING_WILLOW_IRON,
            "steel" to Seqs.XBOWS_FLETCHING_TEAK_STEEL,
            "mithril" to Seqs.XBOWS_FLETCHING_MAPLE_MITHRIL,
            "adamant" to Seqs.XBOWS_FLETCHING_MAHOGANY_ADAMANTITE,
            "runite" to Seqs.XBOWS_FLETCHING_YEW_RUNITE,
            "dragon" to Seqs.XBOWS_FLETCHING_YEW_DRAGON,
        )

    /** First word of the lower-case metal -> stringing a crossbow (cache seqs `xbows_stringing_crossbow_*`; "rune" uses the runite one). */
    val CROSSBOW_STRINGING: Map<String, Int> =
        mapOf(
            "bronze" to Seqs.XBOWS_STRINGING_CROSSBOW_BRONZE,
            "blurite" to Seqs.XBOWS_STRINGING_CROSSBOW_BLURITE,
            "iron" to Seqs.XBOWS_STRINGING_CROSSBOW_IRON,
            "steel" to Seqs.XBOWS_STRINGING_CROSSBOW_STEEL,
            "mithril" to Seqs.XBOWS_STRINGING_CROSSBOW_MITHRIL,
            "adamant" to Seqs.XBOWS_STRINGING_CROSSBOW_ADAMANTITE,
            "rune" to Seqs.XBOWS_STRINGING_CROSSBOW_RUNITE,
            "dragon" to Seqs.XBOWS_STRINGING_CROSSBOW_DRAGON,
        )

    /** First word of the lower-case dart -> adding feathers (cache seqs `human_fletching_add_dart_feathers_*`). */
    val DART_FEATHERS: Map<String, Int> =
        mapOf(
            "bronze" to Seqs.HUMAN_FLETCHING_ADD_DART_FEATHERS_BRONZE,
            "iron" to Seqs.HUMAN_FLETCHING_ADD_DART_FEATHERS_IRON,
            "steel" to Seqs.HUMAN_FLETCHING_ADD_DART_FEATHERS_STEEL,
            "mithril" to Seqs.HUMAN_FLETCHING_ADD_DART_FEATHERS_MITHRIL,
            "adamant" to Seqs.HUMAN_FLETCHING_ADD_DART_FEATHERS_ADAMANT,
            "rune" to Seqs.HUMAN_FLETCHING_ADD_DART_FEATHERS_RUNE,
            "dragon" to Seqs.HUMAN_FLETCHING_ADD_DART_FEATHERS_DRAGON,
            "amethyst" to Seqs.HUMAN_FLETCHING_ADD_DART_FEATHERS_AMETHYST,
        )

    /** First word of the lower-case bolt -> adding feathers (cache seqs `human_fletching_add_bolt_feathers_*`). */
    val BOLT_FEATHERS: Map<String, Int> =
        mapOf(
            "bronze" to Seqs.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_BRONZE,
            "iron" to Seqs.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_IRON,
            "silver" to Seqs.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_SILVER,
            "blurite" to Seqs.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_BLURITE,
            "steel" to Seqs.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_STEEL,
            "mithril" to Seqs.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_MITHRIL,
            "adamant" to Seqs.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_ADAMANT,
            "runite" to Seqs.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_RUNE,
            "dragon" to Seqs.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_DRAGON,
        )

    /** First word of the lower-case plain bolt a gem tip goes on -> tipping it (cache seqs `human_fletching_add_bolt_tips_*`). */
    val BOLT_TIPS: Map<String, Int> =
        mapOf(
            "bronze" to Seqs.HUMAN_FLETCHING_ADD_BOLT_TIPS_BRONZE,
            "iron" to Seqs.HUMAN_FLETCHING_ADD_BOLT_TIPS_IRON,
            "blurite" to Seqs.HUMAN_FLETCHING_ADD_BOLT_TIPS_BLURITE,
            "steel" to Seqs.HUMAN_FLETCHING_ADD_BOLT_TIPS_STEEL,
            "mithril" to Seqs.HUMAN_FLETCHING_ADD_BOLT_TIPS_MITHRIL,
            "adamant" to Seqs.HUMAN_FLETCHING_ADD_BOLT_TIPS_ADAMANT,
            "runite" to Seqs.HUMAN_FLETCHING_ADD_BOLT_TIPS_RUNE,
            "dragon" to Seqs.HUMAN_FLETCHING_ADD_BOLT_TIPS_DRAGON,
        )

    /**
     * The animation of making [r]: [baseName] is the name of its first material (the plain bolt a gem tip goes on); -1 where no
     * animation is known (javelins, cutting bolt tips; fletching.md OPEN-FL3).
     */
    fun anim(r: Recipe, baseName: String): Int {
        val name = r.name.lowercase()
        val metal = name.substringBefore(' ')
        return when {
            r.tools.contains(Objs.KNIFE) -> Seqs.HUMAN_FLETCHING
            name.endsWith("crossbow (u)") -> CROSSBOW_ASSEMBLY[metal] ?: -1
            name.endsWith("crossbow") -> CROSSBOW_STRINGING[metal] ?: -1
            BOW_STRINGING.containsKey(name) -> BOW_STRINGING.getValue(name)
            name == "headless arrow" -> Seqs.HUMAN_FLETCHING_ADD_FEATHER
            name.endsWith(" arrow") -> Seqs.HUMAN_FLETCHING_ADD_ARROW_TIPS
            name.endsWith(" dart") -> DART_FEATHERS[metal] ?: -1
            name.endsWith(" bolts") && r.ticks == INSTANT_TICKS -> BOLT_FEATHERS[metal] ?: -1
            name.endsWith(" bolts") -> BOLT_TIPS[baseName.substringBefore(' ')] ?: -1
            else -> -1
        }
    }
}
