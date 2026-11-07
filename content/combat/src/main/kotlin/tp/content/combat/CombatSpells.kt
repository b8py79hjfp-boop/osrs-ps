package tp.content.combat

/** Spellbook varbit values (varbit `spellbook`: 0 standard, 1 ancient, 2 lunar, 3 arceuus). */
internal enum class SpellBook(val varbit: Int) {
    STANDARD(0),
    ANCIENT(1),
    ARCEUUS(3),
}

/** The element of a Strike..Surge spell, for elemental weakness (Wiki "Elemental weakness": the NPC column's words). */
internal enum class Element(val word: String) {
    AIR("air"),
    WATER("water"),
    EARTH("earth"),
    FIRE("fire"),
}

/** Who a spell may be cast on (CM6). */
internal enum class SpellTarget { ANY, UNDEAD, DEMON }

/** What a successful cast does besides its damage (combat-magic.md CM7, CM9-CM12). */
internal sealed interface SpellEffect {
    data object None : SpellEffect

    /** No damage: lowers [stat] (player id) / [npcStat] (NPC index) by [percent] of the base level, if not lowered (CM9). */
    class Debuff(val stat: Int, val npcStat: Int, val percent: Int) : SpellEffect

    /** On a hit: lowers the stat by [flat] + [percent] of the base level if not lowered (shadow spells, god spells). */
    class Drain(val stat: Int, val npcStat: Int, val percent: Int, val flat: Int) : SpellEffect

    /** Binds (CM10): swampbark adds 2 ticks a piece. */
    class Bind(val ticks: Int) : SpellEffect

    /** Ice spells (CM10): an ancient sceptre adds 10%. */
    class Freeze(val ticks: Int) : SpellEffect

    /** Smoke spells (CM11): 1 in 8 to poison at [severity]. */
    class Poison(val severity: Int) : SpellEffect

    /** Blood spells (CM11): heal [percent] of the rolled damage. */
    class Heal(val percent: Int) : SpellEffect

    /** Grasp spells (CM12): [chance] percent to bind for [ticks]. */
    class Grasp(val chance: Int, val ticks: Int) : SpellEffect
}

/**
 * A combat spell (docs/mechanics/combat-magic.md; data in [CombatSpellTable]). [ladder] holds (level, max) pairs of the
 * Strike..Surge class (CM5); [staves] the objs one of which must be wielded to cast it (empty: any).
 */
internal class CombatSpell(
    val component: Int,
    val name: String,
    val book: SpellBook,
    val level: Int,
    val baseMax: Int,
    val xp: Double,
    val splashXp: Double,
    val runes: List<Pair<Int, Int>>,
    val staves: IntArray,
    val effect: SpellEffect,
    val multi: Boolean,
    val staffAnim: Int,
    val plainAnim: Int,
    val castSpot: Int,
    val projectile: Int,
    val impact: Int,
    val autocast: Boolean,
    val ladder: IntArray?,
    val element: Element?,
    val only: SpellTarget,
) {
    /** CM5: the max of the highest spell of this class unlocked at Magic [level]; [baseMax] for the other spells. */
    fun baseMaxAt(level: Int): Int {
        val steps = ladder ?: return baseMax
        var max = steps[1]
        for (i in steps.indices step 2) if (level >= steps[i]) max = steps[i + 1]
        return max
    }

    /** Barrage spells measure the hit delay to the defender's south-west tile (HD:3). */
    val toSouthWest: Boolean get() = multi && name.endsWith("Barrage")

    val isBolt: Boolean get() = ladder != null && name.endsWith("Bolt")

    companion object {
        private val BY_COMPONENT: Map<Int, CombatSpell> by lazy { CombatSpellTable.ALL.associateBy { it.component } }

        fun of(component: Int): CombatSpell? = BY_COMPONENT[component]
    }
}
