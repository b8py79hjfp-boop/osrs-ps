package tp.plugin.commands

import tp.plugin.PlayerRef
import tp.util.CoordGrid
import tp.util.Rank

/** What a parser looks up while reading arguments (the world's players and cache names); faked in CommandParserTest. */
public interface ParseContext {
    /** Online players on this world the viewer may see (hidden staff of a higher rank are left out). */
    public fun visiblePlayers(): List<PlayerRef>

    /** Cache names by kind: name (lower case) to the ids carrying it, unnoted objs only for [NameKind.OBJ]. */
    public fun names(kind: NameKind): Map<String, List<Int>>

    /** Whether [id] exists for [kind]. */
    public fun exists(kind: NameKind, id: Int): Boolean

    /** The noted form of obj [id], or null when it has none. */
    public fun notedOf(id: Int): Int?

    public companion object {
        /** The live context of [viewer]'s world: its players (as [viewer] may see them) and the cache names. */
        public fun of(viewer: PlayerRef): ParseContext = WorldParseContext(viewer)
    }
}

public enum class NameKind { OBJ, NPC, LOC }

/** A parsed duration: [millis] >= 0, or [PERMANENT]. */
@JvmInline
public value class CmdDuration(public val millis: Long) {
    public val permanent: Boolean get() = millis == PERMANENT_MILLIS

    override fun toString(): String = if (permanent) "permanent" else formatMillis(millis)

    public companion object {
        public const val PERMANENT_MILLIS: Long = -1
        public val PERMANENT: CmdDuration = CmdDuration(PERMANENT_MILLIS)
    }
}

/** A player argument: online on this world, or (where the command allows it) an account resolved later by the hub. */
public sealed interface PlayerTarget {
    public val name: String

    public class Online(public val player: PlayerRef) : PlayerTarget {
        override val name: String get() = player.username
    }

    public class Offline(override val name: String) : PlayerTarget
}

/** An obj argument: the id (the noted form when asked for with `noted:`). */
public class ObjPick(public val id: Int, public val noted: Boolean)

/** One argument's parse: the value and the tokens it took, or the error shown with the usage line. */
public sealed interface ParseResult<out T> {
    public class Ok<T>(public val value: T, public val consumed: Int) : ParseResult<T>

    public class Err(public val message: String) : ParseResult<Nothing>
}

/**
 * One typed argument of a signature (commands brief §2). [parse] reads from [tokens] at [at]; most arguments take one
 * token, a coordinate one to three, [Text] the rest of the line.
 */
public sealed class Arg<T>(public val name: String, public val optional: Boolean) {
    /** Usage form: `<name>` or `[name]`, with the type when it helps (`<length:duration>`). */
    public open val usage: String get() = if (optional) "[$name]" else "<$name>"

    internal abstract fun parse(tokens: List<String>, at: Int, ctx: ParseContext): ParseResult<T>

    /** Parses this argument from the start of [tokens] (the interface's dialogs, CommandParserTest). */
    public fun parseAt(tokens: List<String>, ctx: ParseContext): ParseResult<T> = parse(tokens, 0, ctx)

    /** A target argument marks a command as one that shows up under a selected player in the interface (§3). */
    public open val isPlayer: Boolean get() = false

    public class IntArg(name: String, optional: Boolean, public val min: Long, public val max: Long) : Arg<Int>(name, optional) {
        override fun parse(tokens: List<String>, at: Int, ctx: ParseContext): ParseResult<Int> =
            when (val r = Amounts.parse(tokens[at])) {
                null -> {
                    ParseResult.Err("'${tokens[at]}' is not a number")
                }

                else -> {
                    if (r !in maxOf(min, Int.MIN_VALUE.toLong())..minOf(max, Int.MAX_VALUE.toLong())) {
                        ParseResult.Err("$name must be $min..$max")
                    } else {
                        ParseResult.Ok(r.toInt(), 1)
                    }
                }
            }
    }

    public class LongArg(name: String, optional: Boolean, public val min: Long, public val max: Long) : Arg<Long>(name, optional) {
        override fun parse(tokens: List<String>, at: Int, ctx: ParseContext): ParseResult<Long> =
            when (val r = Amounts.parse(tokens[at])) {
                null -> ParseResult.Err("'${tokens[at]}' is not a number")
                else -> if (r < min || r > max) ParseResult.Err("$name must be $min..$max") else ParseResult.Ok(r, 1)
            }
    }

    public class BoolArg(name: String, optional: Boolean) : Arg<Boolean>(name, optional) {
        override val usage: String get() = if (optional) "[on|off]" else "<on|off>"

        override fun parse(tokens: List<String>, at: Int, ctx: ParseContext): ParseResult<Boolean> =
            when (tokens[at].lowercase()) {
                "on", "true", "yes" -> ParseResult.Ok(true, 1)
                "off", "false", "no" -> ParseResult.Ok(false, 1)
                else -> ParseResult.Err("$name is on/off, true/false or yes/no")
            }
    }

    public class DurationArg(name: String, optional: Boolean) : Arg<CmdDuration>(name, optional) {
        override fun parse(tokens: List<String>, at: Int, ctx: ParseContext): ParseResult<CmdDuration> =
            Durations.parse(tokens[at])?.let { ParseResult.Ok(it, 1) }
                ?: ParseResult.Err("'${tokens[at]}' is not a duration (30s, 10m, 2h, 7d or perm)")
    }

    /** [allowOffline]: an unknown name is passed on as an account for the hub to resolve (cross-world, offline). */
    public class PlayerArg(name: String, optional: Boolean, public val allowOffline: Boolean) : Arg<PlayerTarget>(name, optional) {
        override val isPlayer: Boolean get() = true

        override fun parse(tokens: List<String>, at: Int, ctx: ParseContext): ParseResult<PlayerTarget> {
            val wanted = Names.normalise(tokens[at])
            val players = ctx.visiblePlayers()
            players.firstOrNull { Names.normalise(it.username) == wanted }?.let { return ParseResult.Ok(PlayerTarget.Online(it), 1) }
            // §2: partial names are disambiguated, never guessed.
            val matches = players.filter { Names.normalise(it.username).startsWith(wanted) }
            return when {
                matches.size == 1 -> {
                    ParseResult.Ok(PlayerTarget.Online(matches[0]), 1)
                }

                matches.size > 1 -> {
                    ParseResult.Err(
                        "'${tokens[at]}' matches ${matches.size} players: ${matches.take(MAX_LISTED).joinToString { it.username }}",
                    )
                }

                allowOffline -> {
                    ParseResult.Ok(PlayerTarget.Offline(tokens[at].replace('_', ' ').trim()), 1)
                }

                else -> {
                    ParseResult.Err("No player '${tokens[at]}' is online on this world.")
                }
            }
        }
    }

    public class CacheArg(name: String, optional: Boolean, public val kind: NameKind) : Arg<Int>(name, optional) {
        override fun parse(tokens: List<String>, at: Int, ctx: ParseContext): ParseResult<Int> =
            when (val r = Names.resolve(tokens[at], kind, ctx)) {
                is Names.Resolved.Found -> ParseResult.Ok(r.id, 1)
                is Names.Resolved.Missing -> ParseResult.Err(r.message)
            }
    }

    /** An obj by id or name; `noted:<item>` asks for the noted form (§2). */
    public class ObjArg(name: String, optional: Boolean) : Arg<ObjPick>(name, optional) {
        override fun parse(tokens: List<String>, at: Int, ctx: ParseContext): ParseResult<ObjPick> {
            val raw = tokens[at]
            val noted = raw.startsWith(NOTED_PREFIX, ignoreCase = true)
            val query = if (noted) raw.substring(NOTED_PREFIX.length) else raw
            return when (val r = Names.resolve(query, NameKind.OBJ, ctx)) {
                is Names.Resolved.Missing -> {
                    ParseResult.Err(r.message)
                }

                is Names.Resolved.Found -> {
                    if (!noted) return ParseResult.Ok(ObjPick(r.id, false), 1)
                    val n = ctx.notedOf(r.id) ?: return ParseResult.Err("'$query' has no noted form")
                    ParseResult.Ok(ObjPick(n, true), 1)
                }
            }
        }
    }

    /** A tile: `x y [level]`, `x,y,level` or Jagex's `level,mx,mz,lx,lz` (§2). */
    public class CoordArg(name: String, optional: Boolean) : Arg<CoordGrid>(name, optional) {
        override val usage: String get() = if (optional) "[x y [level]]" else "<x y [level]>"

        @Suppress("ReturnCount") // one return per accepted form and per malformed piece
        override fun parse(tokens: List<String>, at: Int, ctx: ParseContext): ParseResult<CoordGrid> {
            val first = tokens[at]
            if (',' in first) {
                val parts = first.split(',').map { it.trim().toIntOrNull() ?: return ParseResult.Err("'$first' is not a coordinate") }
                return when (parts.size) {
                    2, 3 -> coord(parts[0], parts[1], parts.getOrElse(2) { 0 }, 1)
                    JAGEX_PARTS -> coord(parts[1] * SQUARE + parts[3], parts[2] * SQUARE + parts[4], parts[0], 1)
                    else -> ParseResult.Err("'$first' is not a coordinate (x,y,level or level,mx,mz,lx,lz)")
                }
            }
            val x = first.toIntOrNull() ?: return ParseResult.Err("'$first' is not a coordinate")
            val y = tokens.getOrNull(at + 1)?.toIntOrNull() ?: return ParseResult.Err("$name needs x and y")
            val level = tokens.getOrNull(at + 2)?.toIntOrNull()
            return coord(x, y, level ?: 0, if (level == null) 2 else 3)
        }

        private fun coord(x: Int, z: Int, level: Int, used: Int): ParseResult<CoordGrid> =
            if (x !in 0..MAX_AXIS || z !in 0..MAX_AXIS || level !in 0..MAX_LEVEL) {
                ParseResult.Err("$x, $z, $level is off the map")
            } else {
                ParseResult.Ok(CoordGrid(x, z, level), used)
            }
    }

    public class SkillArg(name: String, optional: Boolean) : Arg<Int>(name, optional) {
        override fun parse(tokens: List<String>, at: Int, ctx: ParseContext): ParseResult<Int> {
            val raw = tokens[at]
            raw.toIntOrNull()?.let {
                return if (it in
                    0 until tp.plugin.Stats.COUNT
                ) {
                    ParseResult.Ok(it, 1)
                } else {
                    ParseResult.Err("No skill $it")
                }
            }
            val wanted = raw.lowercase()
            val hit =
                (0 until tp.plugin.Stats.COUNT).firstOrNull {
                    tp.plugin.Stats
                        .name(it)
                        .lowercase()
                        .startsWith(wanted)
                }
            return hit?.let { ParseResult.Ok(it, 1) } ?: ParseResult.Err("No skill '$raw'")
        }
    }

    public class RankArg(name: String, optional: Boolean) : Arg<Rank>(name, optional) {
        override val usage: String get() = if (optional) "[rank]" else "<rank>"

        override fun parse(tokens: List<String>, at: Int, ctx: ParseContext): ParseResult<Rank> =
            Rank.parse(tokens[at])?.let { ParseResult.Ok(it, 1) }
                ?: ParseResult.Err("Ranks: ${Rank.entries.joinToString { it.name.lowercase() }}")
    }

    public class ChoiceArg(name: String, optional: Boolean, public val choices: List<String>) : Arg<String>(name, optional) {
        override val usage: String get() = choices.joinToString("|").let { if (optional) "[$it]" else "<$it>" }

        override fun parse(tokens: List<String>, at: Int, ctx: ParseContext): ParseResult<String> =
            choices.firstOrNull { it.equals(tokens[at], ignoreCase = true) }?.let { ParseResult.Ok(it, 1) }
                ?: ParseResult.Err("$name is one of ${choices.joinToString(", ")}")
    }

    /** The rest of the line (reasons, messages); always last. */
    public class Text(name: String, optional: Boolean) : Arg<String>(name, optional) {
        override val usage: String get() = if (optional) "[$name…]" else "<$name…>"

        override fun parse(tokens: List<String>, at: Int, ctx: ParseContext): ParseResult<String> =
            ParseResult.Ok(tokens.subList(at, tokens.size).joinToString(" "), tokens.size - at)
    }

    private companion object {
        const val MAX_LISTED = 5
        const val NOTED_PREFIX = "noted:"
        const val JAGEX_PARTS = 5
        const val SQUARE = 64
        const val MAX_AXIS = 16383
        const val MAX_LEVEL = 3
    }
}

/** Amount parsing: `10k`, `1.5m`, `2b` (§2). */
public object Amounts {
    @Suppress("ReturnCount") // integer, decimal and overflow cases each end the parse
    public fun parse(raw: String): Long? {
        val s =
            raw
                .trim()
                .lowercase()
                .replace(",", "")
                .replace("_", "")
        if (s.isEmpty()) return null
        val mult =
            when (s.last()) {
                'k' -> THOUSAND
                'm' -> MILLION
                'b' -> BILLION
                else -> 1L
            }
        val digits = if (mult == 1L) s else s.dropLast(1)
        digits.toLongOrNull()?.let { return runCatching { Math.multiplyExact(it, mult) }.getOrNull() }
        val d = digits.toDoubleOrNull() ?: return null
        if (mult == 1L || d.isNaN() || d.isInfinite()) return null
        val v = d * mult
        if (v > Long.MAX_VALUE.toDouble() || v < Long.MIN_VALUE.toDouble()) return null
        return v.toLong()
    }

    private const val THOUSAND = 1_000L
    private const val MILLION = 1_000_000L
    private const val BILLION = 1_000_000_000L
}

/** Durations: `30s`, `10m`, `2h`, `7d`, `perm` (§2). */
public object Durations {
    public fun parse(raw: String): CmdDuration? {
        val s = raw.trim().lowercase()
        if (s == "perm" || s == "permanent") return CmdDuration.PERMANENT
        if (s.length < 2) return null
        val n = s.dropLast(1).toLongOrNull() ?: return null
        if (n < 0) return null
        val unit =
            when (s.last()) {
                's' -> SECOND
                'm' -> MINUTE
                'h' -> HOUR
                'd' -> DAY
                else -> return null
            }
        return runCatching { CmdDuration(Math.multiplyExact(n, unit)) }.getOrNull()
    }

    internal const val SECOND: Long = 1_000
    internal const val MINUTE: Long = 60 * SECOND
    internal const val HOUR: Long = 60 * MINUTE
    internal const val DAY: Long = 24 * HOUR
}

internal fun formatMillis(ms: Long): String =
    when {
        ms % Durations.DAY == 0L && ms > 0 -> "${ms / Durations.DAY}d"
        ms % Durations.HOUR == 0L && ms > 0 -> "${ms / Durations.HOUR}h"
        ms % Durations.MINUTE == 0L && ms > 0 -> "${ms / Durations.MINUTE}m"
        else -> "${ms / Durations.SECOND}s"
    }

/** Name matching shared by players, objs, NPCs and locs (§2: spaces and underscores alike; did you mean). */
public object Names {
    public fun normalise(name: String): String = name.trim().replace('_', ' ').lowercase()

    public sealed interface Resolved {
        public class Found(public val id: Int) : Resolved

        public class Missing(public val message: String) : Resolved
    }

    /** [raw] as an id, or an exact name; otherwise a "did you mean" of the closest names. */
    public fun resolve(raw: String, kind: NameKind, ctx: ParseContext): Resolved {
        val label = kind.name.lowercase()
        raw.toIntOrNull()?.let { return if (ctx.exists(kind, it)) Resolved.Found(it) else Resolved.Missing("No $label $it") }
        val wanted = normalise(raw)
        val names = ctx.names(kind)
        names[wanted]?.firstOrNull()?.let { return Resolved.Found(it) }
        val close = suggestions(wanted, names.keys)
        return Resolved.Missing(
            if (close.isEmpty()) "No $label named '$raw'" else "No $label named '$raw'. Did you mean: ${close.joinToString(", ")}?",
        )
    }

    /** Up to three candidates: names starting with or containing [wanted], else within edit distance 2. */
    public fun suggestions(wanted: String, candidates: Collection<String>): List<String> {
        val starts = candidates.filter { it.startsWith(wanted) }.sortedBy { it.length }
        if (starts.isNotEmpty()) return starts.take(MAX_SUGGESTIONS)
        val contains = candidates.filter { wanted in it }.sortedBy { it.length }
        if (contains.isNotEmpty()) return contains.take(MAX_SUGGESTIONS)
        return candidates
            .asSequence()
            .filter { kotlin.math.abs(it.length - wanted.length) <= MAX_EDITS }
            .map { it to distance(it, wanted) }
            .filter { it.second <= MAX_EDITS }
            .sortedBy { it.second }
            .take(MAX_SUGGESTIONS)
            .map { it.first }
            .toList()
    }

    /** Levenshtein distance. */
    public fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }

    private const val MAX_SUGGESTIONS = 3
    private const val MAX_EDITS = 2
}
