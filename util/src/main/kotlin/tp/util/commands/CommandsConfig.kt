package tp.util.commands

import tp.util.Rank

/**
 * Impact classes (commands brief §3). Each world's policy enables a set of them; on live 1:1 worlds CONVENIENCE and
 * ECONOMY are off. CONVENIENCE changes gameplay but not items; ECONOMY creates or destroys items, XP or currency.
 */
enum class Impact { INFO, SOCIAL, ACCOUNT, MODERATION, OPERATIONS, DEBUG_VISUAL, CONVENIENCE, ECONOMY }

/**
 * The jail (commands brief §7): a tile and the square around it a jailed player cannot leave. Never in the Wilderness
 * or an instance (checked at boot). OSRS has no player jail, so the place is ours (docs/mechanics/commands.md CMD3).
 */
data class JailArea(val x: Int, val z: Int, val level: Int, val radius: Int) {
    fun contains(x: Int, z: Int, level: Int): Boolean =
        level == this.level && kotlin.math.abs(x - this.x) <= radius && kotlin.math.abs(z - this.z) <= radius

    companion object {
        /** The Draynor jail cell (Prince Ali Rescue), a walkable tile checked with `cache-dump collision`. */
        val DEFAULT: JailArea = JailArea(3128, 3245, 0, 2)
    }
}

/** A world TOML override of one command (commands brief §3): a different minimum rank, or disabled. */
sealed interface CommandOverride {
    data class MinRank(val rank: Rank) : CommandOverride

    data object Disabled : CommandOverride
}

/**
 * The commands policy of one world (commands brief §9), from `config/server.toml` (D-087). The defaults are a dev world:
 * every impact enabled, not live. ECONOMY can only come from the file, so it changes only with a restart.
 */
data class CommandsConfig(
    val live: Boolean = false,
    val enabledImpacts: Set<Impact> = Impact.entries.toSet(),
    val allowEconomyOnLive: Boolean = false,
    val rateLimitPerSecond: Int = DEFAULT_PER_SECOND,
    val rateLimitPerMinute: Int = DEFAULT_PER_MINUTE,
    val confirmTimeoutSeconds: Int = DEFAULT_CONFIRM_SECONDS,
    val yellCooldownSeconds: Map<Rank, Int> = DEFAULT_YELL_COOLDOWNS,
    val reasonPresets: List<String> = DEFAULT_REASONS,
    val urls: Map<String, String> = emptyMap(),
    /** `::rules`: the lines shown (commands brief §6); none configured shows a default line. */
    val rules: List<String> = emptyList(),
    val overrides: Map<String, CommandOverride> = emptyMap(),
    /** `::jail` (commands brief §7): where jailed players are kept. */
    val jail: JailArea = JailArea.DEFAULT,
    /**
     * `::command enable` (commands brief §6), until restart: these commands ignore a `disabled` override and an impact
     * class the world turned off, except ECONOMY, which only the file can allow (§3).
     */
    val runtimeEnabled: Set<String> = emptySet(),
    /** D-093: donator gameplay boosts (drop rate, xp, skilling extras). Off on live 1:1 worlds unless the file says so. */
    val donatorBoosts: Boolean = !live,
) {
    /**
     * Whether [impact] may run on this world (§3): enabled in the file, and ECONOMY never on a live world unless the
     * owner set `allow_economy_on_live`.
     */
    fun allows(impact: Impact): Boolean {
        if (impact !in enabledImpacts) return false
        if (impact == Impact.ECONOMY && live && !allowEconomyOnLive) return false
        return true
    }

    companion object {
        /** Commands brief §4: at most 5 commands a second and 30 a minute per player. */
        const val DEFAULT_PER_SECOND: Int = 5
        const val DEFAULT_PER_MINUTE: Int = 30

        /** Commands brief §4: a destructive command waits up to 30 s for `::confirm`. */
        const val DEFAULT_CONFIRM_SECONDS: Int = 30

        /** Commands brief §4: `::yell` cooldown, player 30 s, moderator 5 s, administrator and above none. */
        val DEFAULT_YELL_COOLDOWNS: Map<Rank, Int> = mapOf(Rank.PLAYER to 30, Rank.MODERATOR to 5)

        /** Commands brief §9 example presets. */
        val DEFAULT_REASONS: List<String> = listOf("Spam", "Offensive language", "Scamming", "Botting", "Rule breaking")

        /** A live 1:1 world's defaults (§3): CONVENIENCE and ECONOMY off. */
        val LIVE_IMPACTS: Set<Impact> = Impact.entries.toSet() - Impact.CONVENIENCE - Impact.ECONOMY
    }
}
