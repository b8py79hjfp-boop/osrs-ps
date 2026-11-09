package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.TeleportRequest
import tp.plugin.commands.Category
import tp.symbols.Clientscripts
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.util.CoordGrid
import tp.util.Rank
import tp.util.commands.Impact

/**
 * The teleport menu (`::teleport`, docs/mechanics/teleport-menu.md). Destinations come from `teleports.tsv` (every tile
 * sourced: the Wiki page's map or a coordinate the repo already uses, moved to the nearest standable tile). Every teleport
 * goes through the standard [TeleportRequest] (Tele Block, Wilderness above level 20, one cast at a time, the spell
 * animation; teleports.md T7), so the menu grants nothing a spell wouldn't. The server owns the rows: the client only
 * reports which row, category or button was clicked.
 */
public class TeleportMenu : ContentPlugin {
    override fun ContentScope.register() {
        command("teleport", Rank.PLAYER, Category.GENERAL, Impact.CONVENIENCE) {
            description("Opens the teleport menu; a name or search word teleports there or narrows the list.")
            example("::teleport", "::teleport zulrah", "::teleport agility")
            aliases("teleports", "tp")
            val search = text("destination", optional = true)
            signature(search) { ctx ->
                ctx.player.teleportCommand(ctx.opt(search).orEmpty())
                ctx.done()
            }
        }
        onModalClosed(Interfaces.TELEPORTS) {
            this[STATE] = null
            // Closed while its search box had the keyboard: give the chatbox its input back.
            clientScript(Clientscripts.CHAT_RESTORE_INPUT)
        }
        onScriptTrigger(Components.Teleports.SEARCHTEXT, "iis") { trigger ->
            val s = this[STATE] ?: return@onScriptTrigger
            s.query = typedText(trigger.args[2] as? String ?: "", MAX_TELE_QUERY)
            s.scroll = 0
            render()
        }
        // Focusing the search box is client-side (its onop script); op 1 is enabled so the click arrives and is ignored.
        onButton(Components.Teleports.SEARCHTEXT) { }
        onButton(Components.Teleports.NAV) { click -> navClick(click) }
        onButton(Components.Teleports.LIST) { click -> listClick(click) }
        onButton(Components.Teleports.TELEBTN) { this[STATE]?.selected?.let { go(it) } }
        onButton(Components.Teleports.FAVADD) { this[STATE]?.selected?.let { toggleFavourite(it) } }
        onButton(Components.Teleports.FAVREMOVE) { this[STATE]?.selected?.let { toggleFavourite(it) } }
        onButton(Components.Teleports.PREVBTN) { recents().firstOrNull()?.let { go(it) } }
    }

    private fun PlayerRef.teleportCommand(search: String) {
        val query = typedText(search, MAX_TELE_QUERY)
        if (query.isNotEmpty()) {
            val hits = Destinations.search(query)
            val exact = hits.firstOrNull { it.name.equals(query, ignoreCase = true) }
            val only = exact ?: hits.singleOrNull()
            if (only != null) return go(only)
        }
        open(query)
    }

    private fun PlayerRef.navClick(click: ButtonClick) {
        val s = this[STATE] ?: return
        if (click.slot < 0 || click.slot % NAV_CHILDREN != NAV_COVER) return
        val tab = Tab.entries.getOrNull(click.slot / NAV_CHILDREN) ?: return
        s.tab = tab
        s.query = ""
        s.scroll = 0
        clientScript(Clientscripts.TELEPORTS_SEARCH_CLEAR, Components.Teleports.SEARCHTEXT)
        render()
    }

    /** A click on a row's cover child (`slot % 7 == 6`), resolved against the rows the server drew. */
    private fun PlayerRef.listClick(click: ButtonClick) {
        val s = this[STATE] ?: return
        if (click.slot < 0 || click.slot % ROW_CHILDREN != ROW_COVER) return
        val index = click.slot / ROW_CHILDREN
        val dest = s.rows.getOrNull(index)?.dest ?: return
        when (click.op) {
            OP_TELEPORT -> {
                go(dest)
            }

            OP_FAVOURITE -> {
                toggleFavourite(dest)
            }

            else -> {
                s.selected = dest
                render()
            }
        }
    }

    internal companion object {
        /** Ops of a destination row (teleports_rows): Select, Teleport, Add/Remove favourite. */
        const val OP_TELEPORT = 2
        const val OP_FAVOURITE = 3
    }
}

/** The menu's categories, in nav order: label, icon code (teleports_icon) and the destinations it lists. */
internal enum class Tab(val label: String, val code: String) {
    FAVOURITES("Favourites", "f"),
    RECENT("Recent", "r"),
    CITIES("Cities", "c"),
    RAIDS("Raids", "a"),
    BOSSES("Bosses", "b"),
    WILDERNESS("Wilderness", "w"),
    SKILLING("Skilling", "s"),
    MISC("Misc", "m"),
    ;

    companion object {
        fun ofCategory(category: String): Tab =
            when (category) {
                "cities" -> CITIES
                "raids" -> RAIDS
                "bosses" -> BOSSES
                "skilling" -> SKILLING
                else -> MISC
            }
    }
}

/**
 * One destination (a `teleports.tsv` row). [wilderness] is its Wilderness level (0 outside); [bossIcon] the frame of
 * `icon_boss_25x25` that shows the boss (teleport-menu.md TM4), or -1 for the category's icon.
 */
internal class Destination(
    val id: Int,
    val tab: Tab,
    val name: String,
    val at: CoordGrid,
    val wilderness: Int,
    val description: String,
    val bossIcon: Int,
) {
    /** The row / detail icon code (teleports_icon). */
    val icon: String = if (bossIcon >= 0) "k$bossIcon" else tab.code

    val lowerText: String = "$name $description ${tab.label}".lowercase()
}

internal object Destinations {
    val all: List<Destination> by lazy { load() }
    private val byId: Map<Int, Destination> by lazy { all.associateBy { it.id } }

    fun byId(id: Int): Destination? = byId[id]

    /** Destinations whose name, description or category contains every word of [query], name matches first. */
    fun search(query: String): List<Destination> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return all
            .filter { d -> words.all { it in d.lowerText } }
            .sortedBy { d -> if (words.all { it in d.name.lowercase() }) 0 else 1 }
    }

    private fun load(): List<Destination> {
        val text = Destinations::class.java.getResource("teleports.tsv")?.readText() ?: error("teleports.tsv missing")
        return text
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val c = line.split('\t')
                Destination(
                    id = c[COL_ID].toInt(),
                    tab = Tab.ofCategory(c[COL_CATEGORY]),
                    name = c[COL_NAME],
                    at = CoordGrid(c[COL_X].toInt(), c[COL_Z].toInt(), c[COL_LEVEL].toInt()),
                    wilderness = c[COL_WILDERNESS].toInt(),
                    description = c[COL_DESCRIPTION],
                    bossIcon = c.getOrNull(COL_ICON)?.toIntOrNull() ?: -1,
                )
            }.toList()
    }

    private const val COL_ID = 0
    private const val COL_CATEGORY = 1
    private const val COL_NAME = 2
    private const val COL_X = 3
    private const val COL_Z = 4
    private const val COL_LEVEL = 5
    private const val COL_WILDERNESS = 6
    private const val COL_DESCRIPTION = 7
    private const val COL_ICON = 9
}

/** One drawn row: a destination, or a heading / information line ([dest] null). */
internal class TeleRow(val dest: Destination?, val text: String = "", val heading: Boolean = false)

internal class TeleState(var tab: Tab) {
    var query: String = ""
    var selected: Destination? = null
    var rows: List<TeleRow> = emptyList()

    /** List scroll for the next draw: 0 for a new list, -1 to keep where the player scrolled (teleports_rows). */
    var scroll: Int = 0
}

private val STATE = AttrKey<TeleState>("teleport_menu")

/** Opens the menu (on Favourites when the player has any, else Cities), narrowed to [query] if given. */
internal fun PlayerRef.open(query: String = "") {
    val state = TeleState(if (favourites().isNotEmpty()) Tab.FAVOURITES else Tab.CITIES)
    state.query = query
    this[STATE] = state
    openModal(Components.Toplevel.MAINMODAL, Interfaces.TELEPORTS)
    ifEvents(Components.Teleports.NAV, 0 until Tab.entries.size * NAV_CHILDREN, IfEvents.op(1))
    ifEvents(
        Components.Teleports.LIST,
        0 until Destinations.all.size * ROW_CHILDREN + ROW_CHILDREN * EXTRA_ROWS,
        IfEvents.op(1) or IfEvents.op(TeleportMenu.OP_TELEPORT) or IfEvents.op(TeleportMenu.OP_FAVOURITE),
    )
    ifEvents(Components.Teleports.SEARCHTEXT, -1..-1, IfEvents.op(1) or IfEvents.SCRIPT_TRIGGER)
    render()
}

/** Asks first for a Wilderness destination, then teleports through the standard checks and closes the menu. */
internal fun PlayerRef.go(dest: Destination, confirmed: Boolean = false) {
    if (dest.wilderness > 0 && !confirmed) {
        if (isOpen(Interfaces.TELEPORTS)) clientScript(Clientscripts.TELEPORTS_SEARCH_STOP)
        choiceDialog(
            if (dest.wilderness > TELEPORT_OUT_LIMIT) {
                "${dest.name}: level ${dest.wilderness} Wilderness, no teleporting out. Go?"
            } else {
                "${dest.name} is in level ${dest.wilderness} Wilderness. Teleport?"
            },
            listOf("Yes, teleport into the Wilderness.", "No."),
        ) { pick -> if (pick == 1) go(dest, confirmed = true) }
        return
    }
    val request = TeleportRequest(this, dest.at)
    events.publish(request)
    if (request.denied != null) return
    if (!request.handled) teleport(dest.at)
    remember(dest)
    if (isOpen(Interfaces.TELEPORTS)) closeModal()
}

internal fun PlayerRef.toggleFavourite(dest: Destination) {
    val favs = favourites().toMutableSet()
    if (!favs.add(dest.id)) favs.remove(dest.id)
    for (word in 0 until FAV_WORDS) {
        var bits = 0L
        for (id in favs) if (id / Long.SIZE_BITS == word) bits = bits or (1L shl (id % Long.SIZE_BITS))
        setSaved("$FAV_KEY$word", bits.takeIf { it != 0L })
    }
    if (this[STATE] != null) render()
}

/** Favourite destination ids: [FAV_WORDS] saved 64-bit words, bit `id % 64` of word `id / 64`. */
internal fun PlayerRef.favourites(): Set<Int> {
    val out = HashSet<Int>()
    for (word in 0 until FAV_WORDS) {
        val bits = saved("$FAV_KEY$word") ?: continue
        for (b in 0 until Long.SIZE_BITS) if (bits and (1L shl b) != 0L) out += word * Long.SIZE_BITS + b
    }
    return out
}

/** The last [RECENT_MAX] destinations, newest first: 16-bit ids, four per saved word. */
internal fun PlayerRef.recents(): List<Destination> {
    val out = ArrayList<Destination>()
    for (word in 0 until RECENT_WORDS) {
        val bits = saved("$RECENT_KEY$word") ?: continue
        for (i in 0 until IDS_PER_WORD) {
            val id = ((bits ushr (i * ID_BITS)) and ID_MASK).toInt()
            if (id != 0) Destinations.byId(id)?.let { out += it }
        }
    }
    return out
}

private fun PlayerRef.remember(dest: Destination) {
    val ids = (listOf(dest) + recents().filter { it.id != dest.id }).take(RECENT_MAX).map { it.id }
    for (word in 0 until RECENT_WORDS) {
        var bits = 0L
        for (i in 0 until IDS_PER_WORD) {
            val id = ids.getOrNull(word * IDS_PER_WORD + i) ?: 0
            bits = bits or (id.toLong() shl (i * ID_BITS))
        }
        setSaved("$RECENT_KEY$word", bits.takeIf { it != 0L })
    }
}

/** Draws everything: categories, the list, the selected destination and the buttons. */
internal fun PlayerRef.render() {
    val s = this[STATE] ?: return
    val favs = favourites()
    val recent = recents()
    val searching = s.query.isNotEmpty()
    clientScript(
        Clientscripts.TELEPORTS_NAV,
        Components.Teleports.NAV,
        navData(favs.size, recent.size),
        if (searching) -1 else s.tab.ordinal,
    )
    s.rows = rowsFor(s, favs, recent)
    val (title, empty) = titles(s, favs.isEmpty())
    ifText(Components.Teleports.HEADING, title)
    val data = s.rows.joinToString("|") { row -> rowData(row, s, favs, searching || s.tab == Tab.FAVOURITES || s.tab == Tab.RECENT) }
    clientScript(
        Clientscripts.TELEPORTS_ROWS,
        Components.Teleports.LIST,
        Components.Teleports.LIST_SCROLLBAR,
        Components.Teleports.STATUS,
        data,
        empty,
        s.scroll,
    )
    s.scroll = KEEP_SCROLL
    drawDetail(s.selected, favs)
    val last = recent.firstOrNull()
    ifHide(Components.Teleports.PREVBTN, last == null)
    ifText(Components.Teleports.PREVTEXT, last?.let { "Last: ${cell(it.name)}" } ?: "")
}

private fun navData(favourites: Int, recent: Int): String =
    Tab.entries.joinToString("|") { tab ->
        val count =
            when (tab) {
                Tab.FAVOURITES -> favourites
                Tab.RECENT -> recent
                Tab.WILDERNESS -> Destinations.all.count { it.wilderness > 0 }
                else -> Destinations.all.count { it.tab == tab }
            }
        "${tab.label};${tab.code};$count"
    }

private fun rowsFor(s: TeleState, favs: Set<Int>, recent: List<Destination>): List<TeleRow> {
    if (s.query.isNotEmpty()) {
        // Search results grouped under their category, in nav order.
        val hits = Destinations.search(s.query)
        return hits.groupBy { it.tab }.toSortedMap().flatMap { (tab, list) ->
            listOf(TeleRow(null, tab.label, heading = true)) +
                list.map { TeleRow(it) }
        }
    }
    val list =
        when (s.tab) {
            Tab.FAVOURITES -> Destinations.all.filter { it.id in favs }
            Tab.RECENT -> recent
            Tab.WILDERNESS -> Destinations.all.filter { it.wilderness > 0 }
            else -> Destinations.all.filter { it.tab == s.tab }
        }
    return list.map { TeleRow(it) }
}

private fun titles(s: TeleState, noFavourites: Boolean): Pair<String, String> {
    if (s.query.isNotEmpty()) {
        val count = s.rows.count { it.dest != null }
        return "Search: \"${cell(s.query)}\" ($count)" to "Nothing matches \"${cell(s.query)}\"."
    }
    val count = s.rows.size
    val empty =
        when (s.tab) {
            Tab.FAVOURITES -> if (noFavourites) "No favourites yet. Right-click a destination and choose Add favourite." else ""
            Tab.RECENT -> "Your recent teleports will appear here."
            else -> ""
        }
    return "${s.tab.label} ($count)" to empty
}

/** `name;right;icon;favourite;kind;selected` (teleports_rows). [mixed]: the list mixes categories, so rows show theirs. */
private fun rowData(row: TeleRow, s: TeleState, favs: Set<Int>, mixed: Boolean): String {
    val d = row.dest ?: return "${cell(row.text)};;;0;${if (row.heading) 2 else 0};0"
    val right =
        when {
            d.wilderness > 0 -> "Wild ${d.wilderness}"
            mixed -> d.tab.label
            else -> ""
        }
    val kind = if (d.wilderness > 0) 3 else 1
    val fav = if (d.id in favs) 1 else 0
    val selected = if (s.selected?.id == d.id) 1 else 0
    return "${cell(d.name)};${cell(right)};${d.icon};$fav;$kind;$selected"
}

private fun PlayerRef.drawDetail(d: Destination?, favs: Set<Int>) {
    ifHide(Components.Teleports.TELEBTN, d == null)
    ifHide(Components.Teleports.FAVADD, d == null || d.id in favs)
    ifHide(Components.Teleports.FAVREMOVE, d == null || d.id !in favs)
    if (d == null) {
        clientScript(Clientscripts.TELEPORTS_DETAIL, Components.Teleports.DETAIL, "", "", "", "")
        return
    }
    val warning =
        when {
            d.wilderness > TELEPORT_OUT_LIMIT -> {
                "Level ${d.wilderness} Wilderness: players can attack you, and you must walk below level 21 to teleport out."
            }

            d.wilderness > 0 -> {
                "Level ${d.wilderness} Wilderness: other players can attack you here."
            }

            else -> {
                ""
            }
        }
    clientScript(Clientscripts.TELEPORTS_DETAIL, Components.Teleports.DETAIL, d.name, d.icon, d.description, warning)
}

/** Text for one row field: no field or row separators. */
private fun cell(text: String): String = cleanCell(text).replace(";", ",").replace("|", "/")

private const val NAV_CHILDREN = 5
private const val NAV_COVER = 4
private const val ROW_CHILDREN = 7
private const val ROW_COVER = 6

/** teleports_rows: keep the player's scroll position. */
private const val KEEP_SCROLL = -1

/** Search results add one heading row per category. */
private val EXTRA_ROWS = Tab.entries.size

private const val MAX_TELE_QUERY = 40

/** Spells and jewellery work up to this Wilderness level (teleports.md T4, `Teleports.WILDERNESS_LIMIT`). */
private const val TELEPORT_OUT_LIMIT = 20

private const val FAV_KEY = "teleport_fav_"

/** Favourite words: ids up to 255 (teleports.tsv ids are stable and below that). */
private const val FAV_WORDS = 4
private const val RECENT_KEY = "teleport_recent_"
private const val RECENT_WORDS = 2
private const val IDS_PER_WORD = 4
private const val ID_BITS = 16
private const val ID_MASK = 0xFFFFL
private const val RECENT_MAX = RECENT_WORDS * IDS_PER_WORD
