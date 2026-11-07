package tp.content.sailing

import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.choice

/**
 * The facilities stored in the player's full facility bottles (docs/mechanics/sailing-building.md FB1-FB3). A bottle item
 * carries no data, so the stored facility kinds (`FACILITY_BOTTLE_ID` of db 176, 1..42) are a list in the player's `saved`
 * values: `sailing.fbottle.count` entries, eight per Long (8 bits each) in `sailing.fbottle.n`. The list is the order the
 * facilities were bottled in; a bottle used up or emptied removes the first entry of that kind.
 */
internal object FacilityBottles {
    private const val COUNT_KEY = "sailing.fbottle.count"
    private const val KEY_PREFIX = "sailing.fbottle."
    private const val PER_LONG = 8
    private const val BITS = 8
    private const val MASK = (1L shl BITS) - 1

    /** The stored facility kinds in bottling order. */
    fun list(p: PlayerRef): List<Int> {
        val count = (p.saved(COUNT_KEY) ?: 0L).toInt()
        return List(count) { i ->
            val word = p.saved(KEY_PREFIX + i / PER_LONG) ?: 0L
            ((word ushr (i % PER_LONG * BITS)) and MASK).toInt()
        }
    }

    fun has(p: PlayerRef, kind: Int): Boolean = kind in list(p)

    fun add(p: PlayerRef, kind: Int) {
        write(p, list(p) + kind)
    }

    /** Removes the first stored [kind]; false if there is none. */
    fun take(p: PlayerRef, kind: Int): Boolean {
        val current = list(p)
        val at = current.indexOf(kind)
        if (at < 0) return false
        write(p, current.filterIndexed { i, _ -> i != at })
        return true
    }

    private fun write(p: PlayerRef, kinds: List<Int>) {
        val words = (kinds.size + PER_LONG - 1) / PER_LONG
        val old = ((p.saved(COUNT_KEY) ?: 0L).toInt() + PER_LONG - 1) / PER_LONG
        for (w in 0 until maxOf(words, old)) {
            var word = 0L
            for (j in 0 until PER_LONG) {
                val kind = kinds.getOrNull(w * PER_LONG + j) ?: break
                word = word or (kind.toLong() shl (j * BITS))
            }
            p.setSaved(KEY_PREFIX + w, if (w < words) word else null)
        }
        p.setSaved(COUNT_KEY, if (kinds.isEmpty()) null else kinds.size.toLong())
    }
}

/** Menus that pick one of the player's boats (the chatbox stand-in for the boat selection interface 934, OPEN-SEL1). */
internal object BoatPicker {
    /** The boat slot the player picks among [slots] (one slot is returned at once), or null if none or the menu was closed. */
    suspend fun PlayerScript.pickBoat(state: SailingState, title: String, slots: List<Int>): Int? {
        if (slots.isEmpty()) return null
        if (slots.size == 1) return slots.first()
        val answer = choice(title, *slots.map { label(state, player, it) }.toTypedArray())
        return slots.getOrNull(answer - 1)
    }

    fun label(state: SailingState, p: PlayerRef, slot: Int): String {
        val type = BoatSave.get(p, slot)?.type ?: return "Boat ${slot + 1}"
        return "${state.data.boat(type)?.name ?: "Boat"} (${slot + 1})"
    }
}
