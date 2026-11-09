package tp.plugin.commands

import tp.util.Rank
import tp.util.commands.CommandOverride
import tp.util.commands.CommandsConfig
import tp.util.commands.Impact

/**
 * Every command of one world's content (commands brief §3): one source of truth for dispatch, the `::commands`
 * interface and docs/commands.md. Filled while plugins register, then [validate]d before the world starts.
 */
public class CommandRegistry {
    private val byName = LinkedHashMap<String, CommandDef>()
    private val byAlias = HashMap<String, CommandDef>()

    public val all: Collection<CommandDef> get() = byName.values

    internal fun add(def: CommandDef) {
        for (n in listOf(def.name) + def.aliases) {
            val clash = byName[n] ?: byAlias[n]
            require(clash == null) { "command ::$n of ${def.owner} clashes with ::${clash?.name} of ${clash?.owner}" }
        }
        byName[def.name] = def
        for (a in def.aliases) byAlias[a] = def
    }

    /** The command called [name] (or one of its aliases), case-insensitive. */
    public fun find(name: String): CommandDef? = name.lowercase().let { byName[it] ?: byAlias[it] }

    /**
     * Startup checks (§3): reserved names, missing description or example, a target command without a player
     * argument, a text argument that is not last, an optional argument before a required one. Duplicates fail in [add].
     * Returns the problems, empty when the registry is sound.
     */
    public fun validate(): List<String> {
        val problems = ArrayList<String>()
        for (def in byName.values) {
            for (n in listOf(def.name) + def.aliases) if (n in RESERVED) problems += "::$n (${def.owner}) is reserved by the client"
            if (def.description.isBlank()) problems += "::${def.name} (${def.owner}) has no description"
            if (def.examples.isEmpty()) problems += "::${def.name} (${def.owner}) has no example"
            if (def.signatures.isEmpty()) problems += "::${def.name} (${def.owner}) has no signature"
            if (def.target && def.signatures.none { s -> s.args.any { it.isPlayer } }) {
                problems += "::${def.name} (${def.owner}) is a target command without a player argument"
            }
            for (s in def.signatures) {
                val textAt = s.args.indexOfFirst { it is Arg.Text }
                if (textAt >= 0 && textAt != s.args.lastIndex) problems += "::${def.name}: a text argument must be last"
                val firstOptional = s.args.indexOfFirst { it.optional }
                if (firstOptional >= 0 && s.args.drop(firstOptional).any { !it.optional }) {
                    problems += "::${def.name}: a required argument follows an optional one"
                }
            }
        }
        return problems
    }

    /** [def]'s minimum rank on a world with [config] (§3 overrides; staff commands never below moderator), or null if disabled. */
    public fun effectiveRank(def: CommandDef, config: CommandsConfig): Rank? =
        when (val o = config.overrides[def.name]) {
            null -> def.rank
            is CommandOverride.Disabled -> if (def.name in config.runtimeEnabled) def.rank else null
            is CommandOverride.MinRank -> if (def.rank.isStaff && !o.rank.isStaff) Rank.MODERATOR else o.rank
        }

    /** Whether [viewer] may see and run [def] on this world: rank, override and the world's impact policy (§3). */
    public fun usable(def: CommandDef, viewer: Rank, config: CommandsConfig): Boolean {
        val needed = effectiveRank(def, config) ?: return false
        // `::command enable` lifts a world's impact switch, never ECONOMY's (§3: only the file allows that).
        val impactOk = config.allows(def.impact) || (def.name in config.runtimeEnabled && def.impact != Impact.ECONOMY)
        return viewer.atLeast(needed) && impactOk && def.available(config)
    }

    /** The commands [viewer] may use here, by name. */
    public fun usableBy(viewer: Rank, config: CommandsConfig): List<CommandDef> = byName.values.filter { usable(it, viewer, config) }

    /** §2: "did you mean", drawn only from commands [viewer] can use. */
    public fun suggest(name: String, viewer: Rank, config: CommandsConfig): List<String> {
        val usable = usableBy(viewer, config)
        val names = usable.flatMap { listOf(it.name) + it.aliases }
        return Names.suggestions(name.lowercase(), names)
    }

    /** The outcome of matching a line's arguments against a command's signatures. */
    public sealed interface Match {
        public class Parsed(public val signature: Signature, public val values: Map<Arg<*>, Any?>) : Match

        /**
         * No signature fits: the error of the signature that got furthest through the line (the first of those on a
         * tie), and the signatures that got that far ([closest]; all of them when none took a token), for the usage lines.
         */
        public class Usage(
            public val error: String?,
            public val progress: Int = 0,
            public val closest: List<Signature> = emptyList(),
        ) : Match
    }

    /** Tries [def]'s signatures in order with [args]; the first that takes every token wins. */
    public fun match(def: CommandDef, args: List<String>, ctx: ParseContext): Match {
        val misses = ArrayList<Pair<Signature, Match.Usage>>()
        for (s in def.signatures) {
            when (val r = tryParse(s, args, ctx)) {
                is Match.Parsed -> return r
                is Match.Usage -> misses += s to r
            }
        }
        val best = misses.maxOfOrNull { it.second.progress } ?: 0
        val closest = if (best == 0) def.signatures else misses.filter { it.second.progress == best }.map { it.first }
        // Subcommands (`::raid info ...`, `::raid parties`): a first word none of them takes names the words that work.
        val firsts = def.signatures.map { it.args.firstOrNull() }
        if (best == 0 && firsts.isNotEmpty() && firsts.all { it is Arg.ChoiceArg }) {
            if (args.isEmpty()) return Match.Usage(null, 0, def.signatures)
            val words = firsts.flatMap { (it as Arg.ChoiceArg).choices }.distinct()
            return Match.Usage("'${args[0]}' isn't one of: ${words.joinToString(", ")}", 0, emptyList())
        }
        return Match.Usage(misses.firstOrNull { it.second.progress == best }?.second?.error, best, closest)
    }

    private fun tryParse(s: Signature, args: List<String>, ctx: ParseContext): Match {
        val values = HashMap<Arg<*>, Any?>()
        var at = 0
        for (arg in s.args) {
            if (at >= args.size) {
                if (arg.optional) continue
                return Match.Usage("${arg.usage} is missing", at)
            }
            when (val r = arg.parse(args, at, ctx)) {
                is ParseResult.Ok -> {
                    values[arg] = r.value
                    at += r.consumed
                }

                is ParseResult.Err -> {
                    return Match.Usage(r.message, at)
                }
            }
        }
        if (at < args.size) return Match.Usage("too many arguments", at)
        return Match.Parsed(s, values)
    }

    public companion object {
        /** §2: names the clients handle themselves (official client, RuneLite and both). */
        public val RESERVED: Set<String> =
            setOf(
                "renderself",
                "toggleroof",
                "toggleroofs",
                "displayfps",
                "settilerendermode",
                "map",
                "wiki",
                "toggleantidrag",
                "uiquality",
                "setwindowsize",
                "chattimestamp",
                "repairlt",
                "mouseovertext",
                "getdrawdistance",
                "setdrawdistance",
                "zbuf",
                "z",
            )
    }
}
