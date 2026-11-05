package tp.engine.stat

import tp.protocol.api.ClientSession
import tp.protocol.api.ServerMessage

/**
 * Skill levels and xp. Stat ids follow the client's order (RuneLite `Skill`: attack 0 ... construction 22, sailing 23).
 * XP is stored in tenths, as the client and hiscores do (max 200,000,000.0 xp).
 */
class PlayerStats internal constructor() {
    private val xp = IntArray(COUNT) { if (it == HITPOINTS) Xp.forLevel(HITPOINTS_START) * XP_SCALE else 0 }
    private val base = IntArray(COUNT) { if (it == HITPOINTS) HITPOINTS_START else 1 }
    private val current = base.copyOf()
    private val dirty = BooleanArray(COUNT) { true }

    fun level(stat: Int): Int = current[stat]

    fun baseLevel(stat: Int): Int = base[stat]

    /** XP in tenths. */
    fun xpTenths(stat: Int): Int = xp[stat]

    /** Sets the current (boosted/drained) level; may exceed base (boosts) or be 0. */
    fun setLevel(stat: Int, level: Int) {
        if (current[stat] == level) return
        current[stat] = level.coerceIn(0, MAX_BOOSTED)
        dirty[stat] = true
    }

    /** Adds xp (tenths), capped at 200M; returns the number of levels gained. Current level rises with base. */
    fun addXp(stat: Int, tenths: Int): Int {
        if (tenths <= 0) return 0
        xp[stat] = minOf(MAX_XP_TENTHS.toLong(), xp[stat].toLong() + tenths).toInt()
        val newBase = Xp.levelFor(xp[stat] / XP_SCALE)
        val gained = newBase - base[stat]
        if (gained > 0) {
            base[stat] = newBase
            current[stat] += gained
        }
        dirty[stat] = true
        return gained
    }

    internal fun flush(session: ClientSession) {
        for (stat in 0 until COUNT) {
            if (!dirty[stat]) continue
            dirty[stat] = false
            session.send(ServerMessage.Stat(stat, current[stat], base[stat], xp[stat] / XP_SCALE))
        }
    }

    companion object {
        /** Skills in rev 242 (RuneLite `Skill`, sailing included). */
        const val COUNT = 24
        const val HITPOINTS = 3

        /** New characters start at 10 Hitpoints (OSRS Wiki "Hitpoints"). */
        const val HITPOINTS_START = 10
        const val XP_SCALE = 10

        /** 200,000,000 xp cap (OSRS Wiki "Experience"). */
        const val MAX_XP_TENTHS = 2_000_000_000

        /** Client stat level is a byte; boosts beyond 255 can't be shown. */
        private const val MAX_BOOSTED = 255
    }
}

/** Experience table (OSRS Wiki "Experience": xp(L) = floor(1/4 * sum_{l=1}^{L-1} floor(l + 300 * 2^(l/7)))). */
object Xp {
    const val MAX_LEVEL = 99

    private val table: IntArray =
        IntArray(MAX_LEVEL + 1).also { t ->
            var points = 0.0
            for (level in 2..MAX_LEVEL) {
                val l = level - 1
                @Suppress("MagicNumber") // the Wiki formula's constants
                points += kotlin.math.floor(l + 300.0 * Math.pow(2.0, l / 7.0))
                t[level] = (points / 4).toInt()
            }
        }

    fun forLevel(level: Int): Int = table[level.coerceIn(1, MAX_LEVEL)]

    fun levelFor(xp: Int): Int {
        var level = 1
        while (level < MAX_LEVEL && xp >= table[level + 1]) level++
        return level
    }
}
