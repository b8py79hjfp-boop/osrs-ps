package tp.plugin.commands

import tp.plugin.PlayerRef
import tp.util.Rank
import tp.util.commands.Impact

/** Groups in `::commands` and docs/commands.md (commands brief §5, §6). */
public enum class Category { GENERAL, INFO, SOCIAL, MODERATION, ADMINISTRATION, OPERATIONS, DEVELOPMENT, RAIDS }

/** How much of an execution goes to the audit log (commands brief §4). */
public enum class AuditLevel {
    /** Player commands that are not sensitive. */
    NONE,

    /** Logged without its arguments (passwords). */
    REDACTED,

    /** Actor, rank, command, arguments, target, world, tick, outcome and source. */
    FULL,
}

/** Where an invocation came from: typed in chat, or built by the `::commands` interface (§4: otherwise identical). */
public enum class Source { TYPED, INTERFACE }

/** What a handler reports; the pipeline turns it into the one-line reply and the audit outcome (§4). */
public sealed interface CommandResult {
    public val message: String?

    public class Done(override val message: String? = null) : CommandResult

    public class Failed(override val message: String) : CommandResult

    /** Work continues off the world thread; the result arrives later through the pre-tick inbox. */
    public class Pending(override val message: String? = null) : CommandResult
}

/** One parsed invocation as a handler sees it. Values are read with the signature's [Arg] handles: `ctx[target]`. */
public class CommandContext internal constructor(
    public val player: PlayerRef,
    public val command: CommandDef,
    public val source: Source,
    public val line: String,
    private val values: Map<Arg<*>, Any?>,
) {
    @Suppress("UNCHECKED_CAST")
    public operator fun <T> get(arg: Arg<T>): T = values[arg] as T

    /** The optional [arg]'s value, or null when it was not given. */
    @Suppress("UNCHECKED_CAST")
    public fun <T> opt(arg: Arg<T>): T? = values[arg] as T?

    public fun done(message: String? = null): CommandResult = CommandResult.Done(message)

    public fun fail(message: String): CommandResult = CommandResult.Failed(message)
}

/** One way to call a command: its arguments in order and the handler. */
public class Signature internal constructor(
    public val args: List<Arg<*>>,
    internal val handler: (CommandContext) -> CommandResult,
    /** This form is destructive on its own (`::raid end`), even when the command's others are not (§4). */
    public val confirm: Boolean = false,
) {
    public val usage: String get() = args.joinToString(" ") { it.usage }
}

/** A registered command (commands brief §3). Built once at startup with [CommandBuilder]; immutable afterwards. */
public class CommandDef internal constructor(
    public val name: String,
    public val aliases: List<String>,
    public val rank: Rank,
    public val category: Category,
    public val impact: Impact,
    public val description: String,
    public val examples: List<String>,
    public val signatures: List<Signature>,
    /** Shows up under a selected player in the interface; needs a player argument (§3). */
    public val target: Boolean,
    public val crossWorld: Boolean,
    public val confirm: Boolean,
    public val cooldownTicks: Int,
    public val audit: AuditLevel,
    /** Extra condition, e.g. "only inside the staff zone"; the reason when it fails. */
    public val predicate: ((PlayerRef) -> String?)?,
    /** Which module declared it (validation messages). */
    public val owner: String,
    /** Whether the world's config offers the command at all (e.g. `::discord` only with a configured URL). */
    public val available: (tp.util.commands.CommandsConfig) -> Boolean,
) {
    /** `::name <a> [b]` for each signature. */
    public val usages: List<String> get() = signatures.map { s -> "::$name${if (s.args.isEmpty()) "" else " " + s.usage}" }
}

/** The command DSL (commands brief §3). */
public class CommandBuilder internal constructor(
    private val name: String,
    private val rank: Rank,
    private val category: Category,
    private val impact: Impact,
    private val owner: String,
) {
    private val aliases = ArrayList<String>()
    private var description: String = ""
    private val examples = ArrayList<String>()
    private val signatures = ArrayList<Signature>()
    private var target = false
    public var crossWorld: Boolean = false
    public var confirm: Boolean = false
    public var cooldownTicks: Int = 0
    public var audit: AuditLevel = if (rank.isStaff) AuditLevel.FULL else AuditLevel.NONE
    private var predicate: ((PlayerRef) -> String?)? = null
    private var available: (tp.util.commands.CommandsConfig) -> Boolean = { true }

    /** Lists the command only where [check] holds for the world's config (§6: URL commands need a URL). */
    public fun availableWhen(check: (tp.util.commands.CommandsConfig) -> Boolean) {
        available = check
    }

    public fun aliases(vararg names: String) {
        aliases += names
    }

    public fun description(text: String) {
        description = text
    }

    public fun example(vararg lines: String) {
        examples += lines
    }

    /** Marks the command as one that targets a player (it then needs a player argument). */
    public fun target() {
        target = true
    }

    public fun requires(check: (PlayerRef) -> String?) {
        predicate = check
    }

    public fun signature(vararg args: Arg<*>, handler: (CommandContext) -> CommandResult) {
        signatures += Signature(args.toList(), handler)
    }

    /** A destructive form: it waits for `::confirm` like a [confirm] command (commands brief §4). */
    public fun confirmedSignature(vararg args: Arg<*>, handler: (CommandContext) -> CommandResult) {
        signatures += Signature(args.toList(), handler, confirm = true)
    }

    // Argument factories (each call is a distinct handle: use it to read the value in the handler).
    public fun int(
        name: String,
        optional: Boolean = false,
        min: Long = Int.MIN_VALUE.toLong(),
        max: Long = Int.MAX_VALUE.toLong(),
    ): Arg.IntArg =
        Arg.IntArg(name, optional, min, max)

    public fun long(name: String, optional: Boolean = false, min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE): Arg.LongArg =
        Arg.LongArg(name, optional, min, max)

    public fun bool(name: String, optional: Boolean = false): Arg.BoolArg = Arg.BoolArg(name, optional)

    public fun duration(name: String, optional: Boolean = false): Arg.DurationArg = Arg.DurationArg(name, optional)

    public fun player(name: String, optional: Boolean = false, allowOffline: Boolean = false): Arg.PlayerArg =
        Arg.PlayerArg(name, optional, allowOffline)

    public fun item(name: String, optional: Boolean = false): Arg.ObjArg = Arg.ObjArg(name, optional)

    public fun npc(name: String, optional: Boolean = false): Arg.CacheArg = Arg.CacheArg(name, optional, NameKind.NPC)

    public fun loc(name: String, optional: Boolean = false): Arg.CacheArg = Arg.CacheArg(name, optional, NameKind.LOC)

    public fun coord(name: String, optional: Boolean = false): Arg.CoordArg = Arg.CoordArg(name, optional)

    public fun skill(name: String, optional: Boolean = false): Arg.SkillArg = Arg.SkillArg(name, optional)

    public fun rank(name: String, optional: Boolean = false): Arg.RankArg = Arg.RankArg(name, optional)

    public fun choice(name: String, vararg choices: String, optional: Boolean = false): Arg.ChoiceArg =
        Arg.ChoiceArg(name, optional, choices.toList())

    public fun text(name: String, optional: Boolean = false): Arg.Text = Arg.Text(name, optional)

    internal fun build(): CommandDef =
        CommandDef(
            name.lowercase(),
            aliases.map { it.lowercase() },
            rank,
            category,
            impact,
            description,
            examples.toList(),
            signatures.toList(),
            target,
            crossWorld,
            confirm,
            cooldownTicks,
            audit,
            predicate,
            owner,
            available,
        )
}

/** Splits a command line on spaces; double quotes group words (§2). */
public object Tokenizer {
    public fun split(line: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quoted = false
        var started = false
        for (c in line) {
            when {
                c == '"' -> {
                    quoted = !quoted
                    started = true
                }

                c.isWhitespace() && !quoted -> {
                    if (started) out += cur.toString()
                    cur.clear()
                    started = false
                }

                else -> {
                    cur.append(c)
                    started = true
                }
            }
        }
        if (started) out += cur.toString()
        return out
    }
}
