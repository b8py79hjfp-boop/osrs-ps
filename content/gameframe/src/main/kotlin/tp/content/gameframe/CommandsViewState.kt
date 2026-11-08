package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.util.Rank

// The model of the `::commands` interface (commands brief section 5): every screen is a list of rows that the server builds, pages
// and sends as one string to the commands_rows clientscript. A click names a row by its position in the server's own list.

/** Per-player state while the interface is open; cleared when its modal closes. */
internal val CMD_STATE = AttrKey<CmdState>("commands_view")

/** Children each row draws in `commands_rows.cs2` (background, three text cells, spare, click cover). */
internal const val CHILDREN_PER_ROW: Int = 6

/** The click cover's sub id in a row (`commands_rows.cs2`: child `n * 6 + 5`). */
internal const val COVER_SUB: Int = 5

/** Rows per page, served by the server (commands brief section 5: for example 50). Matches the `$slot = 50` cap in `commands_rows.cs2`. */
internal const val PAGE_SIZE: Int = 50

/** Longest search text kept: the client sends at most a short string; anything longer is hostile (as the spawn menu does). */
internal const val MAX_QUERY: Int = 64

/** Longest text taken from a typed dialog (reasons, notes, names): one chat-line's worth, so a line never grows unbounded. */
internal const val MAX_TYPED: Int = 100

/** Characters that fit a column of the client's p11 font (about 5 px each): left 110 px, middle 246 px, right 96 px, full row 460 px. */
internal const val LEFT_CHARS: Int = 20
internal const val MID_CHARS: Int = 46
internal const val RIGHT_CHARS: Int = 18
internal const val INFO_CHARS: Int = 82

/** Where a screen sits in the tab bar (staff tabs are hidden below their rank). */
internal enum class CmdTab(val minRank: Rank, val component: Int) {
    COMMANDS(Rank.PLAYER, Components.Commands.TAB_COMMANDS),
    PLAYERS(Rank.MODERATOR, Components.Commands.TAB_PLAYERS),
    TICKETS(Rank.MODERATOR, Components.Commands.TAB_TICKETS),
    RECENT(Rank.MODERATOR, Components.Commands.TAB_RECENT),
}

/** How a row is drawn and whether it takes a click (the `kind` field of `commands_rows.cs2`). */
internal enum class RowKind(val flag: Int) {
    INFO(0),
    ITEM(1),
    HEADING(2),
}

/** One row. Only [RowKind.ITEM] rows with an [action] are clickable; everything else is read-only text. */
internal class CmdRow(
    val left: String,
    val mid: String = "",
    val right: String = "",
    val kind: RowKind = RowKind.INFO,
    val action: (PlayerRef.() -> Unit)? = null,
)

/**
 * A screen of the interface: [rows] is rebuilt on every draw from the current search text, so the list is always current
 * (online players, tickets, rights). [minRank] is checked again on every click, never trusted from the screen being open.
 * A [step] screen is one question of a guided run; the result of the run replaces the steps in the back stack.
 */
internal class CmdScreen(
    val title: String,
    val minRank: Rank = Rank.PLAYER,
    val searchable: Boolean = false,
    val step: Boolean = false,
    val empty: String = "Nothing to show.",
    val hint: String = "",
    val key: String = title,
    val rows: PlayerRef.(String) -> List<CmdRow>,
)

/**
 * A command and signature to run, with the arguments that are already known. [presets] gives the whole word for an argument
 * index (a chosen player); [prefixes] gives the first word of a text argument that continues with what the viewer types
 * (`raid end <player> <reason>`). Held by name and index, so the registry is read again when the run starts.
 */
internal class RunPlan(
    val command: String,
    val signature: Int,
    val presets: Map<Int, String> = emptyMap(),
    val prefixes: Map<Int, String> = emptyMap(),
)

/** A screen the viewer left, with the search and page to come back to. */
internal class CmdFrame(val screen: CmdScreen, val query: String, val page: Int)

/** What the viewer's Players list is narrowed to (world, rank, status; commands brief section 5). */
internal enum class StatusFilter(val label: String) {
    ALL("All"),
    MUTED("Muted"),
    JAILED("Jailed"),
    FROZEN("Frozen"),
}

/** The asynchronous parts of a player card (hub lookups): arrive after the card is drawn. */
internal class CardData {
    var requested = false
    var status: List<String>? = null
    var history: List<String>? = null
    var error: String? = null
}

internal class CmdState(var screen: CmdScreen, var tab: CmdTab?) {
    val back = ArrayDeque<CmdFrame>()
    var query = ""
    var page = 0

    /** The rows of the current screen, all pages: clicks are resolved against this list, never against what the client says. */
    var rows: List<CmdRow> = emptyList()

    /** Counts every move to another screen; a dialog answer that arrives for an older count is dropped. */
    var nav = 0

    /** Whether the client's search box holds text (it is emptied when the screen changes). */
    var clientSearch = false
    var worldFilter = 0
    var rankFilter: Rank? = null
    var statusFilter = StatusFilter.ALL
    val cards = HashMap<String, CardData>()
}

// ---- text

/**
 * Makes [text] safe for the row string: the client reads `;` and `|` as separators, controls would break the layout, and
 * characters outside Latin-1 cannot be drawn by the chat font. Tags are left alone: callers escape user text first.
 */
internal fun cleanCell(text: String): String =
    buildString(text.length) {
        for (c in text) {
            append(
                when {
                    c == ';' -> ','
                    c == '|' -> '/'
                    c.isISOControl() -> ' '
                    c.code > LAST_LATIN1 -> '?'
                    else -> c
                },
            )
        }
    }

/** Highest code point of Latin-1, the range the client's fonts draw. */
private const val LAST_LATIN1: Int = 0xFF

/** Cuts [text] to [max] characters with ".." and never inside a tag (a cut `<col=ff` would swallow the text after it). */
internal fun clip(text: String, max: Int): String {
    if (text.length <= max) return text
    var cut = text.take(max - 2)
    val open = cut.lastIndexOf('<')
    if (open > cut.lastIndexOf('>')) cut = cut.substring(0, open)
    return "$cut.."
}

/** Breaks [text] at spaces into lines of at most [width] characters (a word longer than that is split). */
internal fun wrap(text: String, width: Int = INFO_CHARS): List<String> {
    val lines = ArrayList<String>()
    var line = StringBuilder()
    for (word in text.split(' ')) {
        for (piece in if (word.length > width) word.chunked(width) else listOf(word)) {
            if (line.isNotEmpty() && line.length + 1 + piece.length > width) {
                lines += line.toString()
                line = StringBuilder()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(piece)
        }
    }
    if (line.isNotEmpty()) lines += line.toString()
    return lines
}

/** Read-only rows for [text], wrapped. */
internal fun infoRows(text: String): List<CmdRow> = wrap(text).map { CmdRow(it) }

internal fun heading(text: String): CmdRow = CmdRow(text, kind = RowKind.HEADING)

internal fun item(left: String, mid: String = "", right: String = "", action: PlayerRef.() -> Unit): CmdRow =
    CmdRow(left, mid, right, RowKind.ITEM, action)

/**
 * What the player typed into a dialog or search box, made safe: controls and double quotes removed (a quote would break
 * the command line the interface builds), trimmed and capped at [MAX_TYPED]. Tags are escaped where it is shown.
 */
internal fun typedText(raw: String, max: Int = MAX_TYPED): String =
    raw
        .filterNot { it.isISOControl() || it == '"' }
        .trim()
        .take(max)

/** [token] as one word of a command line: double-quoted when it holds spaces (the tokenizer groups quotes, brief section 2). */
internal fun quoteToken(token: String): String = if (token.any { it.isWhitespace() }) "\"$token\"" else token

/** A player name as a command word: spaces become underscores (OSRS treats both alike, brief section 2). */
internal fun nameToken(name: String): String = name.trim().replace(' ', '_')

internal fun rankLabel(rank: Rank): String = rank.name.lowercase().replaceFirstChar(Char::uppercase)
