package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.PlayerRef

/**
 * How a creature is caught (hunter.tsv `trap`): a bird snare, a box trap, by hand with a butterfly net, a deadfall, a net trap
 * (salamanders) or with a falcon (kebbits).
 */
internal enum class TrapKind(val tsv: String) {
    BIRD("bird"),
    BOX("box"),
    NET("net"),
    DEADFALL("deadfall"),
    NETTRAP("nettrap"),
    FALCON("falcon"),
}

/** [obj] items, [min]..[max] of them, given every time (Wiki `DropsLineSkill` rarity "Always"). */
internal class HunterLoot(val obj: Int, val min: Int, val max: Int)

/**
 * A Hunter creature (a row of `hunter.tsv`, written by tools/wiki-import/hunter_creatures.py): the Hunter [level] and [xp], the
 * catch roll [low] / [high] (Wiki "Skilling success chart"; [low2] / [high2] with a magic butterfly net, else the same), the
 * cache [npcs] and the [loot] (for nets the jar the creature is caught in). [part] is the rare creature part of Hunters' Rumours (-1 if
 * the creature has none).
 */
internal class Creature(
    val name: String,
    val kind: TrapKind,
    val level: Int,
    val xp: Double,
    val low: Int,
    val high: Int,
    val low2: Int,
    val high2: Int,
    val npcs: List<Int>,
    val loot: List<HunterLoot>,
    val part: Int,
)

/** The Hunter data tables, parsed once from `hunter.tsv`. */
internal object HunterData {
    private const val C_NAME = 0
    private const val C_TRAP = 1
    private const val C_LEVEL = 2
    private const val C_XP = 3
    private const val C_LOW = 4
    private const val C_HIGH = 5
    private const val C_LOW2 = 6
    private const val C_HIGH2 = 7
    private const val C_NPCS = 8
    private const val C_LOOT = 9
    private const val C_PART = 10
    private const val COLUMNS = 10

    /** Hunter level and traps allowed from it (Wiki "Hunter", Multiple traps: 1 -> 2, 40 -> 3, 60 -> 4, 80 -> 5). */
    private val TRAP_LIMITS = listOf(1 to 2, 40 to 3, 60 to 4, 80 to 5)

    /** Every creature by wiki page name. */
    val BY_NAME: Map<String, Creature> by lazy { CREATURES.associateBy { it.name } }

    /** All creatures. */
    val CREATURES: List<Creature> by lazy { load() }

    /** Creatures by cache npc type. */
    val BY_NPC: Map<Int, Creature> by lazy { CREATURES.flatMap { c -> c.npcs.map { it to c } }.toMap() }

    /** Traps a player of Hunter [level] may have laid; [wilderness] adds the extra trap (Wiki "Hunter": black chinchompas). */
    fun trapLimit(level: Int, wilderness: Boolean): Int {
        val base = TRAP_LIMITS.lastOrNull { level >= it.first }?.second ?: TRAP_LIMITS.first().second
        return if (wilderness) base + 1 else base
    }

    private fun load(): List<Creature> {
        val text = HunterData::class.java.getResource("hunter.tsv")?.readText() ?: error("hunter.tsv missing")
        return text
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split('\t') }
            .filter { it.size >= COLUMNS }
            .map { c ->
                val low = c[C_LOW].toInt()
                val high = c[C_HIGH].toInt()
                Creature(
                    c[C_NAME],
                    TrapKind.entries.first { it.tsv == c[C_TRAP] },
                    c[C_LEVEL].toInt(),
                    c[C_XP].toDouble(),
                    low,
                    high,
                    c[C_LOW2].toIntOrNull() ?: low,
                    c[C_HIGH2].toIntOrNull() ?: high,
                    c[C_NPCS].split(',').map { it.toInt() },
                    c[C_LOOT].split(';').map { part ->
                        val (obj, range) = part.split(':')
                        val (min, max) = range.split('-')
                        HunterLoot(obj.toInt(), min.toInt(), max.toInt())
                    },
                    c.getOrNull(C_PART)?.toIntOrNull() ?: -1,
                )
            }.toList()
    }
}

/**
 * Hunter state that the trap plugins share: the net traps laid by [HunterStaticTraps] count against the same per-level limit as bird
 * snares and box traps (docs/mechanics/hunter.md OPEN-HU4).
 */
internal object HunterShared {
    private val EXTRA = AttrKey<IntArray>("hunter.extraTraps")

    /** Net traps [p] has laid. */
    fun extra(p: PlayerRef): Int = p[EXTRA]?.get(0) ?: 0

    private val NORMAL = AttrKey<IntArray>("hunter.normalTraps")

    /** Bird snares and box traps [p] has laid (kept by [Hunter]). */
    fun normal(p: PlayerRef): Int = p[NORMAL]?.get(0) ?: 0

    /** Sets the number of bird snares and box traps [p] has laid. */
    fun setNormal(p: PlayerRef, count: Int) {
        val box = p[NORMAL] ?: IntArray(1).also { p[NORMAL] = it }
        box[0] = count
    }

    /** Changes [p]'s net-trap count by [delta]. */
    fun addExtra(p: PlayerRef, delta: Int) {
        val box = p[EXTRA] ?: IntArray(1).also { p[EXTRA] = it }
        box[0] = (box[0] + delta).coerceAtLeast(0)
    }
}
