package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.commands.AuditLevel
import tp.plugin.commands.Category
import tp.plugin.commands.CommandResult
import tp.plugin.commands.Source
import tp.plugin.commands.Tags
import tp.plugin.commands.auditStaffAction
import tp.plugin.commands.runCommandLine
import tp.plugin.commands.worldPlayers
import tp.symbols.Clientscripts
import tp.util.Rank
import tp.util.commands.Impact

// Help tickets (commands brief section 5, item 4): `::ticket <text>` for players; the Tickets tab for staff.

/** One open help ticket. */
internal class Ticket(val id: Int, val owner: String, val text: String, val openedTick: Long) {
    var claimedBy: String = ""
}

/**
 * The open tickets of one world, held as a world attribute (like the raid state in raids-core). They live in memory only:
 * they do not survive a restart, and a ticket belongs to the world its player opened it on.
 */
internal class TicketBoard {
    val open = LinkedHashMap<Int, Ticket>()
    var nextId = 1
}

private val BOARD = AttrKey<TicketBoard>("commands.tickets")

internal fun PlayerRef.ticketBoard(): TicketBoard = worldAttr(BOARD) ?: TicketBoard().also { setWorldAttr(BOARD, it) }

/** Longest ticket text kept: a few chat lines. */
private const val MAX_TICKET_CHARS: Int = 200

/** Open tickets one player may have at once: stops one player filling the staff list (our limit, not an OSRS rule). */
private const val MAX_OPEN_PER_PLAYER: Int = 3

/** Open tickets a world keeps: a bound on memory (our limit, not an OSRS rule). */
private const val MAX_OPEN: Int = 100

/** Time between a player's tickets: 50 ticks, 30 seconds (our limit; one tick is 600 ms, tick.md). */
private const val TICKET_COOLDOWN_TICKS: Int = 50

/** Milliseconds in one game tick (tick.md), for "opened n minutes ago". */
private const val MS_PER_TICK: Long = 600L

private const val MS_PER_MINUTE: Long = 60_000L

internal fun ContentScope.tickets() {
    command("ticket", Rank.PLAYER, Category.GENERAL, Impact.SOCIAL) {
        description("Sends a help ticket to the staff of this world; a moderator will look at it.")
        example("::ticket I am stuck behind a door in the Lumbridge cellar")
        // Commands brief section 4: tickets are one of the sensitive player commands that are audited.
        audit = AuditLevel.FULL
        cooldownTicks = TICKET_COOLDOWN_TICKS
        val text = text("message")
        signature(text) { ctx -> openTicket(ctx.player, ctx[text]) }
    }
    staffTicketCommands()
}

/**
 * The staff side as commands (panel brief §4 Moderation: the panel's ticket queue runs through the pipeline like every
 * panel action). `::tickets` prints one line per ticket in a fixed form, `#id | owner | open or claimer | minutes | text`,
 * which the panel reads; the same actions exist in the Tickets tab.
 */
private fun ContentScope.staffTicketCommands() {
    command("tickets", STAFF_RANK, Category.MODERATION, Impact.MODERATION) {
        description("Lists this world's open help tickets.")
        example("::tickets")
        remote = true
        signature { ctx ->
            val open =
                ctx.player
                    .ticketBoard()
                    .open.values
            if (open.isEmpty()) return@signature ctx.done("No open tickets.")
            for (t in open) {
                val minutes = (ctx.player.tick - t.openedTick) * MS_PER_TICK / MS_PER_MINUTE
                val by = t.claimedBy.ifEmpty { "open" }
                ctx.player.message("#${t.id} | ${Tags.escape(t.owner)} | ${Tags.escape(by)} | $minutes | ${Tags.escape(t.text)}")
            }
            ctx.done()
        }
    }
    command("claimticket", STAFF_RANK, Category.MODERATION, Impact.MODERATION) {
        description("Claims a help ticket, or releases it with off.")
        example("::claimticket 3", "::claimticket 3 off")
        remote = true
        val id = int("id", min = 1)
        val off = choice("off", "off", optional = true)
        signature(id, off) { ctx ->
            val t = ctx.player.ticketBoard().open[ctx[id]] ?: return@signature ctx.fail("There is no open ticket #${ctx[id]}.")
            val take = ctx.opt(off) == null
            if (take && t.claimedBy.isNotEmpty()) return@signature ctx.fail("Ticket #${t.id} is claimed by ${Tags.escape(t.claimedBy)}.")
            t.claimedBy = if (take) ctx.player.username else ""
            ctx.done(if (take) "You claimed ticket #${t.id}." else "Released ticket #${t.id}.")
        }
    }
    command("closeticket", STAFF_RANK, Category.MODERATION, Impact.MODERATION) {
        description("Closes a help ticket; the note is shown to the player.")
        example("::closeticket 3 the door is fixed now")
        remote = true
        val id = int("id", min = 1)
        val note = text("note", optional = true)
        signature(id, note) { ctx ->
            val t =
                ctx.player
                    .ticketBoard()
                    .open
                    .remove(ctx[id]) ?: return@signature ctx.fail("There is no open ticket #${ctx[id]}.")
            val text = typedText(ctx.opt(note).orEmpty(), MAX_TICKET_CHARS)
            val by = Tags.escape(ctx.player.username)
            ctx.player
                .onlinePlayer(t.owner)
                ?.message("Your help ticket #${t.id} was closed by $by${if (text.isEmpty()) "." else ": ${Tags.escape(text)}"}")
            ctx.done("Closed ticket #${t.id}.")
        }
    }
}

private fun openTicket(p: PlayerRef, raw: String): CommandResult {
    val text = typedText(raw, MAX_TICKET_CHARS)
    if (text.isEmpty()) return CommandResult.Failed("Say what you need help with: ::ticket <message>")
    val board = p.ticketBoard()
    if (board.open.values.count { it.owner.equals(p.username, ignoreCase = true) } >= MAX_OPEN_PER_PLAYER) {
        return CommandResult.Failed("You already have $MAX_OPEN_PER_PLAYER open tickets. Wait for the staff to answer.")
    }
    if (board.open.size >= MAX_OPEN) return CommandResult.Failed("The staff have too many tickets open right now. Try again later.")
    val ticket = Ticket(board.nextId++, p.username, text, p.tick)
    board.open[ticket.id] = ticket
    for (staff in p.worldPlayers()) {
        if (staff.rank.isStaff) staff.message("New help ticket #${ticket.id} from ${Tags.escape(p.username)}. Open ::commands, Tickets.")
    }
    return CommandResult.Done("Your help ticket #${ticket.id} has been sent. A moderator will look at it.")
}

// ---- the staff view

internal fun ticketsScreen(): CmdScreen =
    CmdScreen(
        title = "Open help tickets",
        minRank = STAFF_RANK,
        empty = "No open tickets.",
        hint = "Click a ticket to claim it, go to the player, or close it with a note. Tickets are lost on a restart.",
    ) { _ ->
        ticketBoard().open.values.map { t ->
            val id = t.id
            item("#$id ${Tags.escape(t.owner)}", Tags.escape(t.text), if (t.claimedBy.isEmpty()) "Open" else Tags.escape(t.claimedBy)) {
                go(ticketScreen(id))
            }
        }
    }

private fun ticketScreen(id: Int): CmdScreen =
    CmdScreen(
        "Ticket #$id",
        minRank = STAFF_RANK,
        hint = "Every action here is checked against your rank again.",
        key = "ticket:$id",
    ) { _ ->
        val t = ticketBoard().open[id] ?: return@CmdScreen infoRows("That ticket is closed.")
        val rows = ArrayList<CmdRow>()
        val minutes = (tick - t.openedTick) * MS_PER_TICK / MS_PER_MINUTE
        rows += heading("From ${Tags.escape(t.owner)}, opened $minutes min ago")
        rows += infoRows(Tags.escape(t.text))
        rows += heading("Actions")
        when {
            t.claimedBy.isEmpty() -> rows += item("Claim", "Mark it as yours") { claim(id, true) }
            t.claimedBy.equals(username, ignoreCase = true) -> rows += item("Release", "You claimed it") { claim(id, false) }
            else -> rows += infoRows("Claimed by ${Tags.escape(t.claimedBy)}.")
        }
        if (onlinePlayer(t.owner) != null) rows += item("Teleport to player", "::teleto ${Tags.escape(t.owner)}") { teleport(id) }
        rows += item("Close with a note", "Tells the player") { close(id) }
        rows
    }

private fun PlayerRef.claim(id: Int, take: Boolean) {
    if (!rank.atLeast(STAFF_RANK)) return
    val t = ticketBoard().open[id] ?: return renderCommands()
    if (take && t.claimedBy.isNotEmpty()) return renderCommands()
    t.claimedBy = if (take) username else ""
    auditStaffAction(if (take) "ticket:claim" else "ticket:release", t.owner, "ticket=$id")
    renderCommands()
}

private fun PlayerRef.teleport(id: Int) {
    val t = ticketBoard().open[id] ?: return
    // The pipeline checks rank, the targeting rule and world policy, and audits it like the typed command.
    runCommandLine("teleto ${nameToken(t.owner)}", Source.INTERFACE)
}

private fun PlayerRef.close(id: Int) {
    if (!rank.atLeast(STAFF_RANK)) return
    val nav = this[CMD_STATE]?.nav ?: return
    clientScript(Clientscripts.COMMANDS_SEARCH_STOP)
    stringDialog("Closing note for the player:") { answer ->
        val s = this[CMD_STATE]
        // Dropped if the interface moved on, or the rights went away while the dialog was open.
        if (s == null || s.nav != nav || !rank.atLeast(STAFF_RANK)) return@stringDialog
        val note = typedText(answer, MAX_TICKET_CHARS)
        val t = ticketBoard().open.remove(id) ?: return@stringDialog
        auditStaffAction("ticket:close", t.owner, "ticket=$id note=$note")
        onlinePlayer(
            t.owner,
        )?.message("Your help ticket #$id was closed by ${Tags.escape(username)}${if (note.isEmpty()) "." else ": ${Tags.escape(note)}"}")
        goBack()
    }
}
