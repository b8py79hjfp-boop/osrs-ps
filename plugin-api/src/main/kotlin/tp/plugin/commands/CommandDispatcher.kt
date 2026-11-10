package tp.plugin.commands

import tp.engine.World
import tp.plugin.AttrKey
import tp.plugin.PlayerRef
import tp.util.Rank
import tp.util.commands.CommandsConfig
import tp.util.commands.Impact

/**
 * The execution pipeline (commands brief §4), shared by typed commands and the `::commands` interface: parse, resolve
 * the command and signature, rank, targeting rule, world policy, rate limit and cooldown, confirmation, execute,
 * audit, reply. Runs on the world thread in the input phase (`ClientCheat` is client input).
 */
public class CommandDispatcher internal constructor(
    private val registry: CommandRegistry,
    /** Commands registered the old way (`onCommand`), until each moves onto the registry. */
    private val legacy: (PlayerRef, String, List<String>) -> Boolean,
) {
    /** Outcome of one line, for the interface and the tests (the player always gets the reply). */
    public sealed interface Outcome {
        public data object Unknown : Outcome

        public data object Dropped : Outcome

        public class Refused(public val message: String) : Outcome

        public class AwaitingConfirm(public val message: String) : Outcome

        public class Ran(public val result: CommandResult) : Outcome
    }

    /** Runs [line] (without the `::`) for [player]. */
    @Suppress("ReturnCount") // one guard per pipeline step (§4), in the brief's order
    public fun dispatch(player: PlayerRef, line: String, source: Source = Source.TYPED): Outcome {
        val tokens = Tokenizer.split(line)
        if (tokens.isEmpty()) return Outcome.Unknown
        val name = tokens[0].lowercase()
        val world = player.p.world
        val config = world.commands
        if (!allowedByRate(player, world.tick, config)) return Outcome.Dropped
        val def = registry.find(name)
        if (source == Source.PANEL) panelProblem(def, player, config)?.let { return refuse(player, it) }
        if (def == null || !registry.usable(def, player.rank, config)) {
            // §2: a command the player may not use gets exactly the unknown reply. The legacy path (rank-checked) and
            // the official fallbacks (`::bank`, emote names) still answer, so a same-named staff command never hides them.
            if (legacy(player, name, tokens.drop(1))) return Outcome.Ran(CommandResult.Done())
            return unknown(player, name, config)
        }
        def.predicate?.invoke(player)?.let { return refuse(player, it) }
        val ctx = WorldParseContext(player)
        val match = registry.match(def, tokens.drop(1), ctx)
        if (match is CommandRegistry.Match.Usage) {
            // An unknown subcommand's error already lists the words that work (CommandRegistry.match): no usage lines.
            if (match.closest.isEmpty() && match.error != null) return refuse(player, "${match.error}.")
            val usage = match.closest.ifEmpty { def.signatures }.joinToString(" or ") { def.usageOf(it) }
            return refuse(player, if (match.error == null) "Usage: $usage" else "${match.error}. Usage: $usage")
        }
        val parsed = match as CommandRegistry.Match.Parsed
        targetProblem(player, def, parsed.values)?.let { return refuse(player, it) }
        cooldownProblem(player, def, world.tick)?.let { return refuse(player, it) }
        val invocation = Invocation(def, parsed.signature, parsed.values, line, source)
        if (def.confirm || parsed.signature.confirm) return askConfirm(player, invocation, world.tick, config)
        return run(player, invocation)
    }

    /** `::confirm`: runs the waiting destructive command if it has not expired (§4). */
    internal fun confirm(player: PlayerRef): Outcome {
        val pending = player[PENDING] ?: return refuse(player, "There is nothing to confirm.")
        player[PENDING] = null
        if (player.tick > pending.expiresTick) return refuse(player, "That confirmation expired.")
        return run(player, pending.invocation)
    }

    private fun run(player: PlayerRef, inv: Invocation): Outcome {
        val ctx = CommandContext(player, inv.def, inv.source, inv.line, inv.values)
        val result =
            runCatching {
                inv.signature.handler(ctx)
            }.getOrElse { CommandResult.Failed("That command failed: ${it.javaClass.simpleName}") }
        if (inv.def.cooldownTicks > 0) cooldowns(player)[inv.def.name] = player.tick + inv.def.cooldownTicks
        audit(player, inv, result)
        recent(player, inv)
        result.message?.let(player::message)
        return Outcome.Ran(result)
    }

    private fun askConfirm(player: PlayerRef, inv: Invocation, tick: Long, config: CommandsConfig): Outcome {
        val ticks = config.confirmTimeoutSeconds * MS_PER_SECOND / MS_PER_TICK
        player[PENDING] = PendingConfirm(inv, tick + ticks)
        val message = "This will run ::${Tags.escape(
            inv.line,
        )} (${inv.def.description}) Type ::confirm within ${config.confirmTimeoutSeconds} s."
        player.message(message)
        return Outcome.AwaitingConfirm(message)
    }

    /** §1 targeting: a staff command reaches only a strictly lower rank (developers: anyone but developers); self is fine. */
    private fun targetProblem(player: PlayerRef, def: CommandDef, values: Map<Arg<*>, Any?>): String? {
        if (!def.rank.isStaff) return null
        for ((arg, value) in values) {
            if (!arg.isPlayer || value !is PlayerTarget.Online) continue
            val target = value.player
            if (target == player) continue
            if (!player.rank.canTarget(target.rank)) return "You can't use ::${def.name} on ${target.username}."
        }
        return null
    }

    /**
     * Panel brief §3: the console runs only commands marked remote, never ECONOMY or CONVENIENCE ones (the panel never
     * creates items, coins or XP), and only ones this actor may use; anything else gets one plain refusal.
     */
    private fun panelProblem(def: CommandDef?, player: PlayerRef, config: CommandsConfig): String? =
        when {
            def == null || !registry.usable(def, player.rank, config) -> {
                "Unknown command (or not one you may use)."
            }

            !def.remote || def.impact == Impact.ECONOMY || def.impact == Impact.CONVENIENCE -> {
                "::${def.name} needs you in the game; it can't run from the panel."
            }

            else -> {
                null
            }
        }

    private fun cooldownProblem(player: PlayerRef, def: CommandDef, tick: Long): String? {
        val until = cooldowns(player)[def.name] ?: return null
        if (tick >= until) return null
        val seconds = ((until - tick) * MS_PER_TICK + MS_PER_SECOND - 1) / MS_PER_SECOND
        return "You can use ::${def.name} again in $seconds s."
    }

    /** §4: at most [CommandsConfig.rateLimitPerSecond] a second and [CommandsConfig.rateLimitPerMinute] a minute; excess is dropped. */
    private fun allowedByRate(player: PlayerRef, tick: Long, config: CommandsConfig): Boolean {
        val window = player[RATE] ?: RateWindow().also { player[RATE] = it }
        window.trim(tick)
        if (window.inSecond(tick) >= config.rateLimitPerSecond || window.size >= config.rateLimitPerMinute) return false
        window.add(tick)
        return true
    }

    private fun unknown(player: PlayerRef, name: String, config: CommandsConfig): Outcome {
        val close = registry.suggest(name, player.rank, config)
        player.message(if (close.isEmpty()) UNKNOWN else "$UNKNOWN Did you mean ${close.joinToString(", ") { "::$it" }}?")
        return Outcome.Unknown
    }

    private fun refuse(player: PlayerRef, message: String): Outcome {
        player.message(message)
        return Outcome.Refused(message)
    }

    /** §4: moderator-and-above commands, and sensitive player ones, go to the append-only audit log. */
    private fun audit(player: PlayerRef, inv: Invocation, result: CommandResult) {
        val level = inv.def.audit
        if (level == AuditLevel.NONE) return
        val target =
            inv.values.entries
                .firstOrNull { it.key.isPlayer }
                ?.value
                .let { (it as? PlayerTarget)?.name } ?: "-"
        val outcome =
            when (result) {
                is CommandResult.Done -> "done"
                is CommandResult.Failed -> "failed: ${result.message}"
                is CommandResult.Pending -> "pending"
            }
        val args = if (level == AuditLevel.REDACTED) "<redacted>" else inv.line.substringAfter(' ', "")
        val world = player.p.world
        world.accounts?.audit(
            player.username,
            "cmd:${inv.def.name}",
            target,
            "rank=${player.rank.name} world=${world.config.id} tick=${world.tick} source=${inv.source.name.lowercase()} " +
                "outcome=$outcome args=$args",
        )
    }

    private fun recent(player: PlayerRef, inv: Invocation) {
        if (!player.rank.isStaff) return
        val list = player[RECENT] ?: ArrayDeque<String>().also { player[RECENT] = it }
        list.addFirst(inv.line)
        while (list.size > RECENT_MAX) list.removeLast()
    }

    /** The viewer's last [RECENT_MAX] commands, newest first (the interface's Recent view, §5). */
    public fun recentOf(player: PlayerRef): List<String> = player[RECENT]?.toList().orEmpty()

    private fun cooldowns(player: PlayerRef): HashMap<String, Long> =
        player[COOLDOWNS] ?: HashMap<String, Long>().also { player[COOLDOWNS] = it }

    internal class Invocation(
        val def: CommandDef,
        val signature: Signature,
        val values: Map<Arg<*>, Any?>,
        val line: String,
        val source: Source,
    )

    internal class PendingConfirm(val invocation: Invocation, val expiresTick: Long)

    /** Ticks of the last minute's commands, oldest first. */
    internal class RateWindow {
        private val ticks = ArrayDeque<Long>()
        val size: Int get() = ticks.size

        fun trim(now: Long) {
            while (ticks.isNotEmpty() && now - ticks.first() >= MINUTE_TICKS) ticks.removeFirst()
        }

        fun inSecond(now: Long): Int = ticks.count { now - it < SECOND_TICKS }

        fun add(now: Long) {
            ticks.addLast(now)
        }
    }

    internal companion object {
        const val UNKNOWN: String = World.UNKNOWN_COMMAND
        const val MS_PER_TICK: Int = 600
        const val MS_PER_SECOND: Int = 1000

        /** The rate windows in ticks: a minute is 100 ticks; a second rounds up to 2 ticks (the tick is the clock: replays). */
        const val MINUTE_TICKS: Long = 100
        const val SECOND_TICKS: Long = 2
        const val RECENT_MAX: Int = 10

        val PENDING = AttrKey<PendingConfirm>("command_pending_confirm")
        val RATE = AttrKey<RateWindow>("command_rate")
        val COOLDOWNS = AttrKey<HashMap<String, Long>>("command_cooldowns")
        val RECENT = AttrKey<ArrayDeque<String>>("command_recent")
    }
}

/** OSRS text tags in user-typed text are shown, never interpreted (§8); the implementation lives in util. */
public typealias Tags = tp.util.Tags

/** [ParseContext] on a live world: its players and the cache names (built once per world, off the hot path). */
internal class WorldParseContext(private val viewer: PlayerRef) : ParseContext {
    private val world = viewer.p.world

    override fun visiblePlayers(): List<PlayerRef> {
        val out = ArrayList<PlayerRef>()
        world.forEachPlayer { p ->
            val ref = tp.plugin.refOf(p)
            // §1: hidden staff are seen only by equal or higher ranks.
            if (!p.invisible || viewer.rank.atLeast(p.rank) || ref == viewer) out += ref
        }
        return out
    }

    override fun names(kind: NameKind): Map<String, List<Int>> = CacheNames.of(world, kind)

    override fun exists(kind: NameKind, id: Int): Boolean =
        when (kind) {
            NameKind.OBJ -> world.data.configs.objs[id] != null
            NameKind.NPC -> world.data.configs.npcs[id] != null
            NameKind.LOC -> world.data.configs.locs[id] != null
        }

    override fun notedOf(id: Int): Int? {
        val t = world.data.configs.objs[id] ?: return null
        if (t.isNoted) return id
        return t.certLink.takeIf { it >= 0 }
    }
}

/** Cache name indexes per world (lower-case name to ids), built on first use. */
internal object CacheNames {
    private val KEY = AttrKey<HashMap<NameKind, Map<String, List<Int>>>>("command_cache_names")

    fun of(world: World, kind: NameKind): Map<String, List<Int>> {
        @Suppress("UNCHECKED_CAST")
        val cache = world.attrs.getOrPut(KEY) { HashMap<NameKind, Map<String, List<Int>>>() } as HashMap<NameKind, Map<String, List<Int>>>
        return cache.getOrPut(kind) {
            val configs = world.data.configs
            when (kind) {
                NameKind.OBJ -> {
                    configs.objs
                        .filter {
                            !it.isNoted && !it.isPlaceholder && it.name != "null"
                        }.groupBy({ Names.normalise(it.name) }, { it.id })
                }

                NameKind.NPC -> {
                    configs.npcs.filter { it.name != "null" }.groupBy({ Names.normalise(it.name) }, { it.id })
                }

                NameKind.LOC -> {
                    configs.locs.filter { it.name != "null" }.groupBy({ Names.normalise(it.name) }, { it.id })
                }
            }
        }
    }
}

/** Is this rank allowed to see a command row? (exposed for the interface). */
public fun CommandRegistry.visibleTo(rank: Rank, config: CommandsConfig): List<CommandDef> = usableBy(rank, config)
