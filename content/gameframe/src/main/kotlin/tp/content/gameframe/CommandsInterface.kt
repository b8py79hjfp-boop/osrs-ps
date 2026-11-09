package tp.content.gameframe

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.commands.CommandListView
import tp.plugin.commands.RankChanged
import tp.plugin.commands.Tags
import tp.plugin.commands.commandRegistry
import tp.symbols.Clientscripts
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.util.Rank

/**
 * The `::commands` interface (commands brief section 5; docs/plans/commands.md "How the custom interface works"): the packed
 * `commands` interface drawn by the `commands_*` clientscripts, built the way the Trading Post is. The server owns every row;
 * the client only reports which row of the page was clicked. The interface carries no rights: every action either goes
 * through the command pipeline (`runCommandLine`, which checks rank, targeting and world policy) or checks the viewer's
 * rank itself on each click, so a stale screen can never act with old rights (section 8).
 *
 * Views: the command list and detail (everyone), Players with the player card, guided runs, Tickets, Recent (staff), and
 * the lists that commands publish as [CommandListView] (long output, and private lists that never go to chat).
 */
public class CommandsInterface : ContentPlugin {
    override fun ContentScope.register() {
        tickets()
        events.on<CommandListView> { view ->
            view.handled = view.player.openCommands(null, listScreen(view.title, view.rows))
        }
        // A demotion closes the screens: what was open was built for the old rank (section 1).
        events.on<RankChanged> { change ->
            if (change.player.rank.ordinal < change.before.ordinal && change.player.isOpen(Interfaces.COMMANDS)) change.player.closeModal()
        }
        onModalClosed(Interfaces.COMMANDS) {
            this[CMD_STATE] = null
            // Closed while its search box had the keyboard: give the chatbox its input back.
            clientScript(Clientscripts.CHAT_RESTORE_INPUT)
        }
        onScriptTrigger(Components.Commands.SEARCHTEXT, "iis") { trigger -> search(trigger.args[2] as? String ?: "") }
        // Focusing the search box is client-side (its onop script); op 1 is enabled so the click arrives and is ignored.
        onButton(Components.Commands.SEARCHTEXT) { }
        for (tab in CmdTab.entries) onButton(tab.component) { openTab(tab) }
        onButton(Components.Commands.BACKBTN) { goBack() }
        onButton(Components.Commands.PREVBTN) { turnPage(-1) }
        onButton(Components.Commands.NEXTBTN) { turnPage(1) }
        onButton(Components.Commands.LIST) { click -> listClick(click) }
    }

    private fun PlayerRef.search(text: String) {
        val s = live() ?: return
        if (!s.screen.searchable) return
        val query = typedText(text, MAX_QUERY)
        s.clientSearch = query.isNotEmpty()
        s.query = query
        s.page = 0
        renderCommands()
    }

    private fun PlayerRef.openTab(tab: CmdTab) {
        val s = live() ?: return
        if (!rank.atLeast(tab.minRank)) return
        s.tab = tab
        s.back.clear()
        go(rootScreen(tab), push = false)
    }

    private fun PlayerRef.turnPage(by: Int) {
        val s = live() ?: return
        s.page += by
        renderCommands()
    }

    /** A click on the list: the cover child of a row (`slot % 6 == 5`) on this page, resolved against the server's own rows. */
    private fun PlayerRef.listClick(click: ButtonClick) {
        val s = live() ?: return
        if (click.slot < 0 || click.slot % CHILDREN_PER_ROW != COVER_SUB) return
        val row = s.rows.getOrNull(s.page * PAGE_SIZE + click.slot / CHILDREN_PER_ROW) ?: return
        row.action?.invoke(this)
    }
}

private fun rootScreen(tab: CmdTab): CmdScreen =
    when (tab) {
        CmdTab.COMMANDS -> commandsScreen()
        CmdTab.PLAYERS -> playersScreen()
        CmdTab.TICKETS -> ticketsScreen()
        CmdTab.RECENT -> recentScreen()
    }

/** Back: to the screen the viewer left, with its search and page. */
internal fun PlayerRef.goBack() {
    val s = live() ?: return
    val frame = s.back.removeLastOrNull() ?: return
    s.screen = frame.screen
    s.query = frame.query
    s.page = frame.page
    s.nav++
    renderCommands()
}

/**
 * The state if the interface is open and the viewer may still use the screen they are on. A viewer who lost the rank (a
 * demotion that raced the click) gets the interface closed instead of an action (section 8: rank is checked at execution).
 */
internal fun PlayerRef.live(): CmdState? {
    val s = this[CMD_STATE] ?: return null
    if (!isOpen(Interfaces.COMMANDS)) {
        this[CMD_STATE] = null
        return null
    }
    if (!rank.atLeast(s.screen.minRank) || s.tab?.let { rank.atLeast(it.minRank) } == false) {
        closeModal()
        return null
    }
    return s
}

/**
 * Opens the interface on the command list narrowed to [search], or on [first] (a command's list output). False when this
 * world has no command registry; the caller then answers in chat. Already open: moves to the new screen.
 */
internal fun PlayerRef.openCommands(search: String?, first: CmdScreen? = null): Boolean {
    if (commandRegistry == null) return false
    val screen = first ?: commandsScreen()
    val query = typedText(search.orEmpty(), MAX_QUERY)
    val open = this[CMD_STATE]
    if (open != null && isOpen(Interfaces.COMMANDS)) {
        if (first == null) {
            // `::commands` again: back to the list.
            open.tab = CmdTab.COMMANDS
            open.back.clear()
        }
        // Output of a command run from the interface is stacked, so Back returns to where the viewer was.
        go(screen, push = first != null)
        open.query = if (screen.searchable) query else ""
        renderCommands()
        return true
    }
    val state = CmdState(screen, if (first == null) CmdTab.COMMANDS else null)
    state.query = if (screen.searchable) query else ""
    this[CMD_STATE] = state
    openModal(Components.Toplevel.MAINMODAL, Interfaces.COMMANDS)
    ifEvents(Components.Commands.LIST, 0 until PAGE_SIZE * CHILDREN_PER_ROW, IfEvents.op(1))
    ifEvents(Components.Commands.SEARCHTEXT, -1..-1, IfEvents.op(1) or IfEvents.SCRIPT_TRIGGER)
    for (tab in CmdTab.entries) ifHide(tab.component, !rank.atLeast(tab.minRank))
    renderCommands()
    return true
}

/** Moves to [screen] (stacking the one left unless [push] is false), clearing search and page. */
internal fun PlayerRef.go(screen: CmdScreen, push: Boolean = true) {
    val s = live() ?: return
    if (!rank.atLeast(screen.minRank)) return
    if (push) s.back.addLast(CmdFrame(s.screen, s.query, s.page))
    s.screen = screen
    s.query = ""
    s.page = 0
    s.nav++
    renderCommands()
}

/**
 * Shows the end of a guided run: the question screens in the back stack are dropped, so Back from the result returns to
 * where the run started (the command's page or the player's card).
 */
internal fun PlayerRef.goResult(screen: CmdScreen) {
    val s = live() ?: return
    if (s.screen.step) {
        while (s.back
                .lastOrNull()
                ?.screen
                ?.step == true
        ) {
            s.back.removeLast()
        }
        val origin = s.back.removeLastOrNull()
        if (origin != null) {
            s.screen = origin.screen
            s.query = origin.query
            s.page = origin.page
        }
    }
    go(screen)
}

/** Draws the current screen: tab colours, heading, one page of rows, and the visibility of Back / Prev / Next / search. */
internal fun PlayerRef.renderCommands() {
    val s = this[CMD_STATE] ?: return
    val built = s.screen.rows(this, s.query)
    val rows = ArrayList<CmdRow>(built.size + 1)
    if (s.screen.searchable && s.query.isNotEmpty()) {
        rows +=
            item("Clear search", "Show everything again") {
                val st = live()
                if (st != null) {
                    st.query = ""
                    st.page = 0
                    st.nav++
                    st.clientSearch = false
                    clientScript(Clientscripts.COMMANDS_SEARCH_CLEAR, Components.Commands.SEARCHTEXT)
                    renderCommands()
                }
            }
    }
    rows += built
    s.rows = rows
    val pages = maxOf(1, (rows.size + PAGE_SIZE - 1) / PAGE_SIZE)
    s.page = s.page.coerceIn(0, pages - 1)
    val data =
        rows
            .drop(s.page * PAGE_SIZE)
            .take(PAGE_SIZE)
            .joinToString("|") { r ->
                val wide = r.kind != RowKind.ITEM
                val left = clip(cleanCell(r.left), if (wide) INFO_CHARS else LEFT_CHARS)
                "$left;${clip(cleanCell(r.mid), MID_CHARS)};${clip(cleanCell(r.right), RIGHT_CHARS)};${r.kind.flag}"
            }
    clientScript(
        Clientscripts.COMMANDS_ROWS,
        Components.Commands.LIST,
        Components.Commands.LIST_SCROLLBAR,
        Components.Commands.STATUS,
        data,
        s.screen.empty,
    )
    val shown =
        if (rows.size >
            PAGE_SIZE
        ) {
            " (${rows.size}), page ${s.page + 1} of $pages"
        } else if (rows.isEmpty()) {
            ""
        } else {
            " (${rows.size})"
        }
    val searching = if (s.query.isEmpty()) "" else " matching \"${cleanCell(Tags.escape(s.query))}\""
    ifText(Components.Commands.HEADING, cleanCell(s.screen.title) + searching + shown)
    ifText(Components.Commands.NOTICE, cleanCell(s.screen.hint))
    for (tab in CmdTab.entries) ifColour(tab.component, if (tab == s.tab) ACTIVE_TAB else IDLE_TAB)
    ifHide(Components.Commands.BACKBTN, s.back.isEmpty())
    ifHide(Components.Commands.PREVBTN, s.page == 0)
    ifHide(Components.Commands.NEXTBTN, s.page >= pages - 1)
    ifHide(Components.Commands.SEARCHBAR, !s.screen.searchable)
    ifHide(Components.Commands.SEARCHTEXT, !s.screen.searchable)
}

/** The rank that may use the staff views (tabs, player cards, tickets, recent). */
internal val STAFF_RANK: Rank = Rank.MODERATOR

/** Tab link colours: white for the open tab, the interface orange for the others (same orange as the trading post). */
private const val ACTIVE_TAB: Int = 0xFFFFFF
private const val IDLE_TAB: Int = 0xFF981F
