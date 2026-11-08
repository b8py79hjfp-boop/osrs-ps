package tp.content.gameframe

import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.commands.Arg
import tp.plugin.commands.Category
import tp.plugin.commands.CommandDef
import tp.plugin.commands.Tags
import tp.plugin.commands.commandRegistry
import tp.plugin.commands.commandsConfig
import tp.plugin.commands.lookupPlayer
import tp.plugin.commands.moderationHistory
import tp.plugin.commands.onlineEverywhere
import tp.plugin.commands.recentCommands
import tp.plugin.commands.sanctions
import tp.util.ModRecord
import tp.util.PlayerCard
import tp.util.Rank
import tp.util.Sanctions
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// The views of the interface (commands brief section 5): command list and detail, Players and the player card, Recent, and
// the lists commands publish. Each builder re-reads the registry and the viewer's rank, so nothing shown is stale.

// ---- the command list and a command's detail

internal fun commandsScreen(): CmdScreen =
    CmdScreen(
        title = "Commands you can use",
        searchable = true,
        empty = "No commands match.",
        hint = "Click a command for its usage and to run it. Type in the search box to narrow the list.",
    ) { query -> commandRows(query) }

private fun PlayerRef.commandRows(query: String): List<CmdRow> {
    val registry = commandRegistry ?: return emptyList()
    val word = query.trim().lowercase()
    val defs =
        registry
            .usableBy(rank, commandsConfig)
            .filter { d ->
                word.isEmpty() || word in d.name || d.aliases.any { word in it } || word in d.description.lowercase() ||
                    word in d.category.name.lowercase()
            }.sortedWith(compareBy({ it.category }, { it.name }))
    val rows = ArrayList<CmdRow>()
    var last: Category? = null
    for (d in defs) {
        if (d.category != last) {
            rows += heading(categoryLabel(d.category))
            last = d.category
        }
        val usage =
            d.signatures
                .firstOrNull()
                ?.usage
                .orEmpty()
        val name = d.name
        rows +=
            item(
                "::$name",
                Tags.escape(if (usage.isEmpty()) d.description else "$usage - ${d.description}"),
                d.aliases.joinToString(", ") { "::$it" },
            ) { go(detailScreen(name)) }
    }
    return rows
}

private fun categoryLabel(category: Category): String = category.name.lowercase().replaceFirstChar(Char::uppercase)

private fun detailScreen(name: String): CmdScreen =
    CmdScreen("::$name", hint = "Pick a way to run it. Every run goes through the same checks as typing it.", key = "command:$name") {
        detailRows(name)
    }

private fun PlayerRef.detailRows(name: String): List<CmdRow> {
    val registry = commandRegistry
    val def = registry?.find(name)
    // Section 2: a command the viewer cannot use is the same as one that does not exist.
    if (registry == null || def == null || !registry.usable(def, rank, commandsConfig)) {
        return infoRows("Unknown command. Type ::commands for a list.")
    }
    val rows = ArrayList<CmdRow>()
    rows += heading("::${def.name} - ${categoryLabel(def.category)}")
    rows += infoRows(Tags.escape(def.description))
    rows += heading("Usage")
    for ((i, sig) in def.signatures.withIndex()) {
        val line = def.usages[i]
        rows += infoRows(Tags.escape(line) + if (def.confirm || sig.confirm) " (asks to confirm)" else "")
    }
    if (def.aliases.isNotEmpty()) rows += infoRows("Aliases: " + def.aliases.joinToString(", ") { "::$it" })
    rows += heading("Examples")
    for (example in def.examples) rows += infoRows(Tags.escape(example))
    rows += heading("Details")
    val minimum = registry.effectiveRank(def, commandsConfig) ?: def.rank
    rows += infoRows("Minimum rank: ${rankLabel(minimum)}.")
    rows += infoRows("Needs a target player: ${if (def.target) "yes, a lower rank than you (or yourself)" else "no"}.")
    val confirms = def.confirm || def.signatures.any { it.confirm }
    rows += infoRows("Needs confirmation: ${if (confirms) "yes, you confirm before it runs (30 seconds)" else "no"}.")
    rows += heading("Run")
    for ((i, sig) in def.signatures.withIndex()) {
        val label = if (sig.args.isEmpty()) "Run now" else "Run with"
        val shown = if (sig.args.isEmpty()) "::${def.name}" else def.usages[i]
        rows += item(label, Tags.escape(shown)) { startRun(RunPlan(def.name, i)) }
    }
    return rows
}

// ---- lists published by commands

/** A list that a command published ([tp.plugin.commands.CommandListView]): its rows are the command's own text, wrapped. */
internal fun listScreen(title: String, lines: List<String>): CmdScreen =
    CmdScreen(
        title = title,
        searchable = true,
        empty = "Nothing to show.",
        hint = "Search narrows the lines. This list is only shown here.",
    ) { query ->
        val word = query.trim().lowercase()
        lines.filter { word.isEmpty() || word in it.lowercase() }.flatMap { infoRows(it) }
    }

// ---- recent

internal fun recentScreen(): CmdScreen =
    CmdScreen(
        title = "Your last commands",
        minRank = STAFF_RANK,
        empty = "You have not run a command yet.",
        hint = "Click a line to run it again.",
    ) { _ ->
        recentCommands().map { line ->
            val name = line.substringBefore(' ')
            item("::" + Tags.escape(name), Tags.escape(line.substringAfter(' ', "")), "Run again") { runLine(line) }
        }
    }

// ---- players

internal fun playersScreen(): CmdScreen =
    CmdScreen(
        title = "Players online",
        minRank = STAFF_RANK,
        searchable = true,
        empty = "No players match.",
        hint = "Click a player for their card and the commands you can use on them.",
    ) { query -> playerRows(query) }

private fun PlayerRef.playerRows(query: String): List<CmdRow> {
    val s = this[CMD_STATE] ?: return emptyList()
    val everyone = onlineEverywhere()
    val word = query.trim().lowercase()
    val worlds = everyone.map { it.worldId }.distinct().sorted()
    val rows = ArrayList<CmdRow>()
    rows +=
        item("World filter", if (s.worldFilter == 0) "All worlds" else "World ${s.worldFilter}", "Change") {
            s.worldFilter = cycle(listOf(0) + worlds, s.worldFilter)
            refilter()
        }
    rows +=
        item("Rank filter", s.rankFilter?.let(::rankLabel) ?: "All ranks", "Change") {
            s.rankFilter = cycle(listOf<Rank?>(null) + Rank.entries, s.rankFilter)
            refilter()
        }
    rows +=
        item("Status filter", s.statusFilter.label, "Change") {
            s.statusFilter = cycle(StatusFilter.entries, s.statusFilter)
            refilter()
        }
    val sorted = everyone.sortedWith(compareBy({ it.worldId }, { it.name.lowercase() }))
    for (who in sorted) {
        if (word.isNotEmpty() && word !in who.name.lowercase()) continue
        if (s.worldFilter != 0 && who.worldId != s.worldFilter) continue
        if (s.rankFilter != null && who.rank != s.rankFilter) continue
        if (!statusMatches(who.name, s.statusFilter)) continue
        val name = who.name
        rows +=
            item(
                Tags.escape(name),
                "World ${who.worldId}${if (name.equals(username, ignoreCase = true)) " (you)" else ""}",
                rankLabel(who.rank),
            ) {
                openCard(name)
            }
    }
    return rows
}

/** The next value after [current] in [values], wrapping round. */
private fun <T> cycle(values: List<T>, current: T): T = values[(values.indexOf(current) + 1) % values.size]

private fun PlayerRef.refilter() {
    val s = live() ?: return
    s.page = 0
    renderCommands()
}

/** Status filters know only players on this world (their sanctions are in memory here); other worlds never match. */
private fun PlayerRef.statusMatches(name: String, filter: StatusFilter): Boolean {
    if (filter == StatusFilter.ALL) return true
    val who = onlinePlayer(name) ?: return false
    val now = System.currentTimeMillis()
    return when (filter) {
        StatusFilter.ALL -> true
        StatusFilter.MUTED -> who.sanctions.muted(now)
        StatusFilter.JAILED -> who.sanctions.jailed(now)
        StatusFilter.FROZEN -> who.isFrozen
    }
}

// ---- the player card

private fun PlayerRef.openCard(name: String) {
    val s = live() ?: return
    s.cards.remove(name.lowercase())
    go(cardScreen(name))
}

private fun cardScreen(name: String): CmdScreen =
    CmdScreen(
        title = "Player: ${Tags.escape(name)}",
        minRank = STAFF_RANK,
        hint = "Click a command to run it on this player. Every run goes through the same checks as typing it.",
        key = "card:${name.lowercase()}",
    ) { cardRows(name) }

private fun PlayerRef.cardRows(name: String): List<CmdRow> {
    val s = this[CMD_STATE] ?: return emptyList()
    // Hidden staff are shown only to equal or higher ranks; the list is re-read so a player who went hidden vanishes.
    val entry =
        onlineEverywhere().firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: return infoRows("${Tags.escape(name)} is not online (or is hidden from you).")
    val data = s.cards.getOrPut(entry.name.lowercase()) { CardData() }
    if (!data.requested) {
        data.requested = true
        fetchCard(entry.name)
    }
    val rows = ArrayList<CmdRow>()
    rows += heading("${Tags.escape(entry.name)} - ${rankLabel(entry.rank)}")
    rows += infoRows("World ${entry.worldId}${if (entry.worldId == worldId) " (this world)" else ""}.")
    val here = onlinePlayer(entry.name)
    if (here != null) {
        rows += infoRows("Location: ${here.coord.x}, ${here.coord.z}, level ${here.coord.level}.")
        rows += infoRows("Combat level ${here.combatLevel}, total level ${totalLevel(here)}.")
    }
    rows += heading("Status")
    rows += data.error?.let { infoRows(it) } ?: data.status?.flatMap { infoRows(it) } ?: infoRows("Loading...")
    rows += heading("Last moderation entries")
    rows += data.history?.flatMap { infoRows(it) } ?: infoRows(if (data.error == null) "Loading..." else "Not available.")
    rows += targetRows(entry.name, entry.rank)
    return rows
}

private fun totalLevel(who: PlayerRef): Int = (0 until Stats.COUNT).sumOf { who.baseLevel(it) }

private fun PlayerRef.fetchCard(name: String) {
    val key = name.lowercase()
    lookupPlayer(name) { error, card ->
        val data = this[CMD_STATE]?.cards?.get(key) ?: return@lookupPlayer
        if (error != null || card == null) data.error = Tags.escape(error ?: "No such account.") else data.status = statusLines(card)
        redrawCard(key)
    }
    moderationHistory(name) { error, records ->
        val data = this[CMD_STATE]?.cards?.get(key) ?: return@moderationHistory
        if (error != null) {
            if (data.error == null) data.error = Tags.escape(error)
        } else {
            data.history = records.take(HISTORY_SHOWN).map(::historyLine).ifEmpty { listOf("No records.") }
        }
        redrawCard(key)
    }
}

/** The last entries the card shows (commands brief section 5: "the last five moderation entries"). */
private const val HISTORY_SHOWN: Int = 5

private fun PlayerRef.redrawCard(key: String) {
    val s = this[CMD_STATE] ?: return
    if (s.screen.key == "card:$key" && isOpen(tp.symbols.Interfaces.COMMANDS)) renderCommands()
}

private fun statusLines(card: PlayerCard): List<String> {
    val now = System.currentTimeMillis()
    val s = card.sanctions

    fun line(label: String, active: Boolean, until: Long, reason: String): String =
        if (active) {
            "$label: ${Sanctions.describeUntil(
                until,
            )}${if (reason.isEmpty()) "" else " (${Tags.escape(reason)})"}."
        } else {
            "$label: no."
        }
    return listOf(
        line("Muted", s.muted(now), s.mutedUntil, s.muteReason),
        line("Yell-muted", s.yellMuted(now), s.yellMutedUntil, s.yellMuteReason),
        line("Jailed", s.jailed(now), s.jailedUntil, s.jailReason),
        line("Banned", Sanctions.inForce(card.bannedUntil, now), card.bannedUntil, ""),
    )
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

private fun historyLine(r: ModRecord): String {
    val lifted = if (r.liftedBy.isEmpty()) "" else " (lifted by ${Tags.escape(r.liftedBy)})"
    return "${DATE.format(
        Instant.ofEpochMilli(r.start),
    )} ${r.type.name.lowercase()} by ${Tags.escape(r.actor)}: ${Tags.escape(r.reason)}$lifted"
}

/**
 * The commands the viewer may use on this player (commands brief section 5): target commands usable by the viewer's rank, when the
 * targeting rule allows it, and for owners and developers the raid run commands. The pipeline checks all of it again on the run.
 */
private fun PlayerRef.targetRows(name: String, targetRank: Rank): List<CmdRow> {
    val registry = commandRegistry ?: return emptyList()
    val rows = ArrayList<CmdRow>()
    val self = name.equals(username, ignoreCase = true)
    if (self || rank.canTarget(targetRank)) {
        val defs = registry.usableBy(rank, commandsConfig).filter { it.target }.sortedBy { it.name }
        if (defs.isNotEmpty()) rows += heading("Commands for ${Tags.escape(name)}")
        for (def in defs) {
            for ((i, sig) in def.signatures.withIndex()) {
                val at = sig.args.indexOfFirst { it.isPlayer }
                if (at < 0) continue
                val plan = RunPlan(def.name, i, presets = mapOf(at to nameToken(name)))
                rows += item("::${def.name}", Tags.escape(def.usages[i]), "") { startRun(plan) }
            }
        }
    }
    val raid = registry.find("raid")
    if (raid != null && registry.usable(raid, rank, commandsConfig)) {
        rows += heading("Raid run of ${Tags.escape(name)} (if they are in one)")
        for (word in RAID_WORDS) {
            val i = raidSignature(raid, word)
            if (i < 0) continue
            // `raid end` takes "run|player reason" as one text, so the player is the first word of it.
            val plan =
                if (word ==
                    "end"
                ) {
                    RunPlan(raid.name, i, prefixes = mapOf(1 to nameToken(name)))
                } else {
                    RunPlan(
                        raid.name,
                        i,
                        presets =
                            mapOf(1 to nameToken(name)),
                    )
                }
            rows += item("::raid $word", Tags.escape(raid.usages[i]), "") { startRun(plan) }
        }
    }
    return rows
}

/** The raid run commands of a player's card (commands brief section 5: info, watch, kick and end). */
private val RAID_WORDS: List<String> = listOf("info", "watch", "kick", "end")

/** The signature of [raid] whose first word is exactly [word] (the `lit` choice), or -1. */
private fun raidSignature(raid: CommandDef, word: String): Int =
    raid.signatures.indexOfFirst { sig -> (sig.args.firstOrNull() as? Arg.ChoiceArg)?.choices == listOf(word) }
