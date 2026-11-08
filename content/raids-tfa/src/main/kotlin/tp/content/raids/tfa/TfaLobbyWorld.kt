package tp.content.raids.tfa

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Varps

/**
 * The lobby's world-scoped state beside raids-core's parties (plugins are shared by every world, so it hangs off the
 * world's attributes): when each party was made (the list's age column), who a leader has blocked from applying, and
 * the last stats seen of each player (rows of members who are offline; TFA5: they stay in the party).
 */
internal class TfaLobbyWorld {
    /** Party id -> tick it was made. */
    val created = HashMap<Int, Long>()

    /** Party id -> lower-case names blocked from applying to it (OPEN-TFA-UI3). */
    val blocked = HashMap<Int, MutableSet<String>>()

    /** Lower-case name -> [snapshot] fields. */
    val stats = HashMap<String, IntArray>()

    companion object {
        private val KEY = AttrKey<TfaLobbyWorld>("tfa_lobby_world")

        fun of(any: EntityRef): TfaLobbyWorld = any.worldAttr(KEY) ?: TfaLobbyWorld().also { any.setWorldAttr(KEY, it) }

        /** Fields of a stats row after the name: combat level, 7 combat stats, completions, practice completions. */
        const val FIELDS = 10

        /** A field the server does not know (offline player never seen): sent empty, the client draws "-". */
        const val UNKNOWN = -1

        /**
         * OPEN-TFA-UI4: the saved key of the practice completion count the rows show. Nothing increments it yet: raids-core
         * counts every completion in the normal KC varp and has no per-mode record.
         */
        const val PRACTICE_KC_KEY = "raids.tfa.practiceKc"

        /** The combat stats in the order of the row string (client-contract 2.3: att, str, rng, mag, def, hp, prayer). */
        private val ROW_STATS =
            intArrayOf(Stats.ATTACK, Stats.STRENGTH, Stats.RANGED, Stats.MAGIC, Stats.DEFENCE, Stats.HITPOINTS, Stats.PRAYER)

        fun snapshot(p: PlayerRef): IntArray {
            val out = IntArray(FIELDS)
            out[0] = p.combatLevel
            for (i in ROW_STATS.indices) out[1 + i] = p.baseLevel(ROW_STATS[i])
            out[1 + ROW_STATS.size] = p.varp(Varps.TOTAL_COMPLETED_THEFRACTUREDARCHIVE)
            out[2 + ROW_STATS.size] = (p.saved(PRACTICE_KC_KEY) ?: 0L).toInt()
            return out
        }
    }
}
