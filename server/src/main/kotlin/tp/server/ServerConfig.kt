package tp.server

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tp.util.Rank
import tp.util.commands.CommandOverride
import tp.util.commands.CommandsConfig
import tp.util.commands.Impact
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * `config/server.toml` (D-087; commands brief §9). Every key is optional: a missing file or key keeps today's
 * defaults (a dev world). Unknown keys fail at boot, so a typo never silently turns a safety rule off.
 */
@Serializable
internal class ServerToml(
    val commands: CommandsToml = CommandsToml(),
    val ranks: RanksToml = RanksToml(),
    /** Per-world overrides by world id: `[worlds.2] live = true`. */
    val worlds: Map<String, WorldToml> = emptyMap(),
)

@Serializable
internal class CommandsToml(
    val live: Boolean? = null,
    @SerialName("enabled_impacts") val enabledImpacts: List<String>? = null,
    @SerialName("allow_economy_on_live") val allowEconomyOnLive: Boolean = false,
    @SerialName("rate_limit_per_second") val rateLimitPerSecond: Int = CommandsConfig.DEFAULT_PER_SECOND,
    @SerialName("rate_limit_per_minute") val rateLimitPerMinute: Int = CommandsConfig.DEFAULT_PER_MINUTE,
    @SerialName("confirm_timeout_seconds") val confirmTimeoutSeconds: Int = CommandsConfig.DEFAULT_CONFIRM_SECONDS,
    @SerialName("yell_cooldown_seconds") val yellCooldownSeconds: Map<String, Int>? = null,
    @SerialName("reason_presets") val reasonPresets: List<String>? = null,
    val urls: Map<String, String> = emptyMap(),
    val rules: List<String> = emptyList(),
    /** Command name to a rank name, or `disabled`. */
    val overrides: Map<String, String> = emptyMap(),
    val jail: JailToml? = null,
    /** D-093: donator gameplay boosts; default on for dev worlds, off for live ones. */
    @SerialName("donator_boosts") val donatorBoosts: Boolean? = null,
)

/** `[commands.jail]` (commands brief §7). */
@Serializable
internal class JailToml(val x: Int, val z: Int, val level: Int = 0, val radius: Int = tp.util.commands.JailArea.DEFAULT.radius)

/** One world's overrides of the shared `[commands]` (only the keys that differ). */
@Serializable
internal class WorldToml(
    val live: Boolean? = null,
    @SerialName("enabled_impacts") val enabledImpacts: List<String>? = null,
    @SerialName("allow_economy_on_live") val allowEconomyOnLive: Boolean? = null,
    val overrides: Map<String, String> = emptyMap(),
    @SerialName("donator_boosts") val donatorBoosts: Boolean? = null,
)

/** Commands brief §1: the first ranks, applied once each and audited (there is no in-game path to the first developer). */
@Serializable
internal class RanksToml(
    val bootstrap: BootstrapToml = BootstrapToml(),
)

@Serializable
internal class BootstrapToml(
    val developers: List<String> = emptyList(),
    val owners: List<String> = emptyList(),
    val administrators: List<String> = emptyList(),
    val moderators: List<String> = emptyList(),
)

/** The decoded file: the commands policy per world and the rank bootstrap. */
internal class ServerConfig(private val toml: ServerToml) {
    /** World [id]'s commands policy: the shared section with that world's overrides on top. */
    fun commands(id: Int): CommandsConfig {
        val c = toml.commands
        val w = toml.worlds[id.toString()]
        val live = w?.live ?: c.live ?: false
        val impacts =
            (w?.enabledImpacts ?: c.enabledImpacts)?.map(::impact)?.toSet()
                ?: if (live) CommandsConfig.LIVE_IMPACTS else Impact.entries.toSet()
        val overrides = (c.overrides + w?.overrides.orEmpty()).mapValues { (name, value) -> override(name, value) }
        return CommandsConfig(
            live = live,
            enabledImpacts = impacts,
            allowEconomyOnLive = w?.allowEconomyOnLive ?: c.allowEconomyOnLive,
            rateLimitPerSecond = c.rateLimitPerSecond,
            rateLimitPerMinute = c.rateLimitPerMinute,
            confirmTimeoutSeconds = c.confirmTimeoutSeconds,
            yellCooldownSeconds =
                c.yellCooldownSeconds?.mapKeys { (k, _) -> rank(k) } ?: CommandsConfig.DEFAULT_YELL_COOLDOWNS,
            reasonPresets = c.reasonPresets ?: CommandsConfig.DEFAULT_REASONS,
            urls = c.urls.filterValues { it.isNotBlank() },
            rules = c.rules,
            overrides = overrides,
            donatorBoosts = w?.donatorBoosts ?: c.donatorBoosts ?: !live,
            jail = c.jail?.let { tp.util.commands.JailArea(it.x, it.z, it.level, it.radius) } ?: tp.util.commands.JailArea.DEFAULT,
        )
    }

    /** Account name to its bootstrap rank (the highest list it appears in wins). */
    fun bootstrap(): Map<String, Rank> {
        val b = toml.ranks.bootstrap
        val out = LinkedHashMap<String, Rank>()
        for ((names, rank) in listOf(
            b.moderators to Rank.MODERATOR,
            b.administrators to Rank.ADMINISTRATOR,
            b.owners to Rank.OWNER,
            b.developers to Rank.DEVELOPER,
        )) {
            for (name in names) out[normalise(name)] = rank
        }
        return out
    }

    companion object {
        /** The default file, next to the working directory (the server runs from the repo root). */
        val DEFAULT_PATH: Path = Path.of("config", "server.toml")

        /** Reads [path]; a missing file is the defaults. Any error fails the boot with the file and key named. */
        fun load(path: Path = DEFAULT_PATH): ServerConfig {
            if (!Files.exists(path)) {
                logger.info { "config: $path not found, using defaults (dev world)" }
                return ServerConfig(ServerToml())
            }
            val text = Files.readString(path)
            val toml =
                runCatching { TOML.decodeFromString(ServerToml.serializer(), text) }
                    .getOrElse { throw IllegalStateException("config $path: ${it.message}", it) }
            val config = ServerConfig(toml)
            // Validate every world section now (rank and impact names), and log what changes the defaults.
            config.commands(0)
            for (id in toml.worlds.keys) {
                val n = id.toIntOrNull() ?: error("config $path: [worlds.$id] is not a world id")
                config.commands(n)
            }
            for ((name, value) in toml.commands.overrides) logger.info { "config: override ::$name = $value" }
            for ((id, w) in toml.worlds) for ((name, value) in w.overrides) logger.info { "config: world $id override ::$name = $value" }
            return config
        }

        fun normalise(name: String): String = name.trim().replace('_', ' ').lowercase()

        private val TOML = Toml(TomlInputConfig(ignoreUnknownNames = false))

        private fun impact(name: String): Impact =
            Impact.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: error("unknown impact class '$name'")

        private fun rank(name: String): Rank = Rank.parse(name) ?: error("unknown rank '$name'")

        private fun override(command: String, value: String): CommandOverride =
            if (value.equals("disabled", ignoreCase = true)) {
                CommandOverride.Disabled
            } else {
                CommandOverride.MinRank(Rank.parse(value) ?: error("override ::$command: unknown rank '$value'"))
            }
    }
}
