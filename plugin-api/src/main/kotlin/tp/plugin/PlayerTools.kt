package tp.plugin

// Staff and developer reads and writes of a player's state (commands brief §6), kept out of PlayerRef's own body.

/** Saved keys starting with [prefix] (personal bests per mode and size, staff tools). */
public fun PlayerRef.savedKeys(prefix: String): List<String> =
    p.saved.keys
        .filter { it.startsWith(prefix) }
        .sorted()

/** The xp at which [level] starts (OSRS xp table, levels 1..99). */
public fun PlayerRef.xpForLevel(level: Int): Double =
    tp.engine.stat.Xp
        .forLevel(level)
        .toDouble()

/** XP of [stat] (whole xp). */
public fun PlayerRef.xp(stat: Int): Double = p.stats.xpTenths(stat) / XP_TENTHS_PER_XP

/**
 * Sets [stat]'s xp outright; base and current level follow it (developer ECONOMY tools only: commands brief §6).
 * Publishes no xp-drop or level-up event.
 */
public fun PlayerRef.setXp(stat: Int, xp: Double) {
    p.stats.setXp(stat, (xp * XP_TENTHS_PER_XP).toInt())
}

/** XP is stored in tenths (engine PlayerStats). */
private const val XP_TENTHS_PER_XP = 10.0
