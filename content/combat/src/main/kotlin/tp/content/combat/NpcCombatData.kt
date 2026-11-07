package tp.content.combat

/**
 * One NPC's combat data from the OSRS Wiki's Infobox Monster (docs/mechanics/npc-combat.md N1). Levels and hitpoints
 * come from the cache instead; this holds what the cache lacks.
 */
internal class NpcCombatDef(
    val speed: Int,
    val max: Int,
    /** Max hit per style when the Wiki field names styles (e.g. `crush` 55, `ranged` 31); else empty. */
    val maxes: Map<String, Int>,
    val styles: Set<String>,
    val aggressive: Boolean,
    val respawn: Int,
    val meleeAttack: Int,
    val meleeStrength: Int,
    val magicAttack: Int,
    val magicDamage: Int,
    val rangedAttack: Int,
    val rangedStrength: Int,
    /** Defence bonuses: stab, slash, crush, magic, light, standard, heavy ranged ([DEF_STAB] ..). */
    val defence: IntArray,
    /** Damage of a poisonous hit's poison; 0 when not poisonous. */
    val poison: Int,
    val venomous: Boolean,
    val poisonImmune: Boolean,
    val venomImmune: Boolean,
    /** Infobox Monster `attributes` (demon, undead, dragon, ...), lower case. */
    val attributes: Set<String> = emptySet(),
    /** Elemental weakness (air, water, earth, fire; empty for none) and its percent (Wiki "Elemental weakness"). */
    val weakElement: String = "",
    val weakPercent: Int = 0,
) {
    val meleeTypes: List<AttackType> = MELEE_WORDS.mapNotNull { (word, type) -> type.takeIf { word in styles } }
    val hasRanged: Boolean get() = "ranged" in styles
    val hasMagic: Boolean get() = "magic" in styles

    /** The max hit for [style]: its own number when the Wiki gives one, else the page's max. */
    fun maxFor(style: CombatStyle): Int {
        val keys =
            when (style) {
                CombatStyle.MELEE -> listOf("stab", "slash", "crush", "melee")
                CombatStyle.RANGED -> listOf("ranged")
                CombatStyle.MAGIC -> listOf("magic")
            }
        return keys.firstNotNullOfOrNull { maxes[it] } ?: max
    }

    internal companion object {
        const val DEF_STAB = 0
        const val DEF_SLASH = 1
        const val DEF_CRUSH = 2
        const val DEF_MAGIC = 3
        const val DEF_LIGHT = 4
        const val DEF_STANDARD = 5
        const val DEF_HEAVY = 6

        /** Wiki melee style words -> attack type. A plain "Melee" is crush (npc-combat.md OPEN-N7). */
        private val MELEE_WORDS =
            listOf("stab" to AttackType.STAB, "slash" to AttackType.SLASH, "crush" to AttackType.CRUSH, "melee" to AttackType.CRUSH)
    }
}

/**
 * The Wiki NPC combat table (`npc-combat.tsv`, written by tools/wiki-import/npc_combat.py; CC BY-NC-SA 3.0,
 * data/PROVENANCE.md). Immutable, loaded once per JVM.
 */
internal object NpcCombatTable {
    private val DEFENCE_COLUMNS = listOf("dstab", "dslash", "dcrush", "dmagic", "dlight", "dstandard", "dheavy")
    private val byType: Map<Int, NpcCombatDef> = parse()

    val size: Int get() = byType.size

    fun of(type: Int): NpcCombatDef? = byType[type]

    private fun parse(): Map<Int, NpcCombatDef> {
        val text = NpcCombatTable::class.java.getResource("npc-combat.tsv")?.readText() ?: error("npc-combat.tsv missing")
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val col =
            lines
                .first()
                .split('\t')
                .withIndex()
                .associate { (i, name) -> name to i }
        val out = HashMap<Int, NpcCombatDef>(lines.size * 2)
        for (line in lines.drop(1)) {
            val cells = line.split('\t')

            fun s(name: String) = cells[col.getValue(name)]

            fun i(name: String) = s(name).toIntOrNull() ?: 0

            fun b(name: String) = s(name) == "1"
            out[i("id")] =
                NpcCombatDef(
                    speed = i("speed"),
                    max = i("max"),
                    maxes =
                        s("maxes")
                            .split(',')
                            .filter { ':' in it }
                            .associate { it.substringBefore(':') to (it.substringAfter(':').toIntOrNull() ?: 0) },
                    styles = s("styles").split(',').filter { it.isNotEmpty() }.toSet(),
                    aggressive = b("aggressive"),
                    respawn = i("respawn"),
                    meleeAttack = i("ab"),
                    meleeStrength = i("sb"),
                    magicAttack = i("am"),
                    magicDamage = i("mb"),
                    rangedAttack = i("ar"),
                    rangedStrength = i("rb"),
                    defence = DEFENCE_COLUMNS.map { i(it) }.toIntArray(),
                    poison = i("poison"),
                    venomous = b("venom"),
                    poisonImmune = b("pimm"),
                    venomImmune = b("vimm"),
                    attributes = s("attributes").split(',').filter { it.isNotEmpty() }.toSet(),
                    weakElement = s("weak"),
                    weakPercent = i("weakpct"),
                )
        }
        return out
    }
}
