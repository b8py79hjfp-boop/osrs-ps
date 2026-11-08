package tp.content.raids.toa

import tp.content.raids.Party
import tp.plugin.PlayerRef

/** One invocation as the cache holds it (toa/client-contract.md 4.1: enum 4664, struct params). */
internal class Invocation(
    /** Enum 4664 key 0..45 (the 774:1 tile sub-id is 52 + key). */
    val key: Int,
    val struct: Int,
    /** Param 1159, 1-based: the bit position (client-contract 2.5). */
    val id: Int,
    val name: String,
    /** Param 1161. */
    val category: Int,
    /** Param 1162: the raid-level value. */
    val level: Int,
    /** Param 1299 (attempts, time limit in ticks, supply percent, path levels, prayer percent); 0 when absent. */
    val value: Int,
    /** Param 1346: the prerequisite invocation's struct, or -1. */
    val requires: Int,
    /** Param 1863: the minimum raid level, 0 when absent. */
    val minLevel: Int,
)

/**
 * The invocations (toa.md TA8-TA17, TI1-TI46): read once from the cache, kept as the three 31-bit words of script 6729 in
 * the party's settings (`toa_w0..2`). Raid level = the sum of param 1162 over the active set (client-contract 4.2).
 */
internal object ToaInvocations {
    const val WORD0 = "toa_w0"
    const val WORD1 = "toa_w1"
    const val WORD2 = "toa_w2"
    private val WORDS = arrayOf(WORD0, WORD1, WORD2)

    /** Category ids (param 1161, client-contract 4.1). */
    const val CAT_ATTEMPTS = 3
    const val CAT_TIME_LIMIT = 4
    const val CAT_HELPFUL_SPIRIT = 5
    const val CAT_PATH_LEVEL = 6
    const val CAT_PRAYER = 7
    const val CAT_RESTRICTION = 8
    const val CAT_WALK_THE_PATH = 9
    const val CAT_AKKHA = 10
    const val CAT_KEPHRI = 11
    const val CAT_ZEBAK = 12
    const val CAT_BABA = 13
    const val CAT_WARDENS = 14
    const val CAT_BLAZING = 15

    /** TA8: one active invocation at most in these categories. */
    private val EXCLUSIVE = setOf(CAT_ATTEMPTS, CAT_TIME_LIMIT, CAT_HELPFUL_SPIRIT, CAT_PATH_LEVEL)

    /** Enum 4664 (key -> invocation struct). */
    private const val ENUM_INVOCATIONS = 4664
    private const val P_ID = 1159
    private const val P_NAME = 1160
    private const val P_CATEGORY = 1161
    private const val P_LEVEL = 1162
    private const val P_VALUE = 1299
    private const val P_REQUIRES = 1346
    private const val P_MIN_LEVEL = 1863

    /** Ids 1..30 in word 0 (bit id), 31..61 in word 1 (bit id - 31), the rest in word 2 (client-contract 2.5). */
    private const val WORD0_LAST = 30
    private const val WORD1_FIRST = 31
    private const val WORD2_FIRST = 62

    @Volatile private var table: List<Invocation>? = null

    /** All invocations in enum order (cache data, identical for every world). */
    fun all(any: PlayerRef): List<Invocation> =
        table ?: any
            .enumInts(ENUM_INVOCATIONS)
            .entries
            .sortedBy { it.key }
            .map { (key, struct) ->
                Invocation(
                    key,
                    struct,
                    any.structInt(struct, P_ID) ?: key + 1,
                    any.structString(struct, P_NAME).orEmpty(),
                    any.structInt(struct, P_CATEGORY) ?: 0,
                    any.structInt(struct, P_LEVEL) ?: 0,
                    any.structInt(struct, P_VALUE) ?: 0,
                    any.structInt(struct, P_REQUIRES) ?: -1,
                    any.structInt(struct, P_MIN_LEVEL) ?: 0,
                )
            }.also { table = it }

    fun words(party: Party): IntArray = IntArray(WORDS.size) { party.settings[WORDS[it]] ?: 0 }

    fun words(settings: Map<String, Int>): IntArray = IntArray(WORDS.size) { settings[WORDS[it]] ?: 0 }

    fun setWords(party: Party, words: IntArray) {
        for (i in WORDS.indices) party.settings[WORDS[i]] = words.getOrElse(i) { 0 }
    }

    private fun wordOf(id: Int): Int =
        when {
            id <= WORD0_LAST -> 0
            id < WORD2_FIRST -> 1
            else -> 2
        }

    private fun bitOf(id: Int): Int =
        when {
            id <= WORD0_LAST -> id
            id < WORD2_FIRST -> id - WORD1_FIRST
            else -> id - WORD2_FIRST
        }

    fun isActive(words: IntArray, inv: Invocation): Boolean = words[wordOf(inv.id)] and (1 shl bitOf(inv.id)) != 0

    fun active(any: PlayerRef, words: IntArray): List<Invocation> = all(any).filter { isActive(words, it) }

    /** TA10: the sum of the active invocations' values. */
    fun raidLevel(any: PlayerRef, words: IntArray): Int = active(any, words).sumOf { it.level }

    /** The value (param 1299) of the active invocation in [category], or null. */
    fun activeValue(
        any: PlayerRef,
        words: IntArray,
        category: Int,
    ): Int? = active(any, words).firstOrNull { it.category == category }?.value

    fun hasNamed(any: PlayerRef, words: IntArray, name: String): Boolean = active(any, words).any { it.name == name }

    /**
     * The leader toggles tile [key] (TA8 / TA9, OPEN-TA4 working assumption): ticking a second invocation of an exclusive
     * category replaces the first; a missing prerequisite is ticked with it; unticking clears the dependants; Blazing
     * Tombs are offered only with [ToaConfig.blazingTombsEnabled] and from raid level 300 (param 1863).
     */
    fun toggle(any: PlayerRef, words: IntArray, key: Int): IntArray {
        val all = all(any)
        val inv = all.firstOrNull { it.key == key } ?: return words
        val out = words.copyOf()
        if (isActive(out, inv)) {
            clear(out, inv)
            var changed = true
            while (changed) {
                changed = false
                for (dep in all) {
                    if (!isActive(out, dep) || dep.requires < 0) continue
                    val pre = all.firstOrNull { it.struct == dep.requires }
                    if (pre != null && !isActive(out, pre)) {
                        clear(out, dep)
                        changed = true
                    }
                }
            }
            return out
        }
        if (inv.category == CAT_BLAZING && !ToaConfig.blazingTombsEnabled) return words
        if (inv.minLevel > 0 && raidLevel(any, out) < inv.minLevel) return words
        if (inv.category in EXCLUSIVE) for (other in all) if (other.category == inv.category) clear(out, other)
        var pre = all.firstOrNull { it.struct == inv.requires }
        while (pre != null && !isActive(out, pre)) {
            set(out, pre)
            pre = all.firstOrNull { it.struct == pre?.requires }
        }
        set(out, inv)
        return out
    }

    private fun set(words: IntArray, inv: Invocation) {
        words[wordOf(inv.id)] = words[wordOf(inv.id)] or (1 shl bitOf(inv.id))
    }

    private fun clear(words: IntArray, inv: Invocation) {
        words[wordOf(inv.id)] = words[wordOf(inv.id)] and (1 shl bitOf(inv.id)).inv()
    }

    // ---- named invocations the flow reads (cache names, TI1-TI46)

    const val WALK_THE_PATH = "Walk the Path"
}
