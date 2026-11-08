package tp.content.gameframe

import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.commands.Arg
import tp.plugin.commands.CommandDef
import tp.plugin.commands.CommandDispatcher
import tp.plugin.commands.CommandResult
import tp.plugin.commands.Signature
import tp.plugin.commands.Source
import tp.plugin.commands.Tags
import tp.plugin.commands.commandRegistry
import tp.plugin.commands.commandsConfig
import tp.plugin.commands.onlineEverywhere
import tp.plugin.commands.runCommandLine
import tp.symbols.Clientscripts
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.util.Rank

// Guided runs (commands brief section 5, items 1 and 3): ask for each argument with OSRS's own dialogs or a pick screen, show the
// typed equivalent in chat, then run it through the one pipeline as Source.INTERFACE. A destructive command answers
// AwaitingConfirm; the interface then shows Yes / No and, on Yes, runs `confirm` (the pipeline's own flow).

/** One question of a run: the words collected so far are immutable, so backing up and answering again is safe. */
private class Step(val plan: RunPlan, val def: CommandDef, val sig: Signature, val index: Int, val tokens: List<String>) {
    val arg: Arg<*> get() = sig.args[index]
}

internal fun PlayerRef.startRun(plan: RunPlan) {
    if (live() == null) return
    nextStep(plan, 0, emptyList())
}

private fun PlayerRef.resolve(plan: RunPlan): Pair<CommandDef, Signature>? {
    val registry = commandRegistry ?: return null
    val def = registry.find(plan.command) ?: return null
    // The pipeline repeats this check; failing early keeps a command the viewer lost from being asked about at all.
    if (!registry.usable(def, rank, commandsConfig)) return null
    val sig = def.signatures.getOrNull(plan.signature) ?: return null
    return def to sig
}

private fun PlayerRef.nextStep(plan: RunPlan, from: Int, collected: List<String>) {
    val (def, sig) = resolve(plan) ?: return notice("That command is not available.")
    var tokens = collected
    var i = from
    while (i < sig.args.size) {
        val arg = sig.args[i]
        val word = plan.presets[i] ?: (arg as? Arg.ChoiceArg)?.choices?.singleOrNull() ?: break
        tokens = tokens + word
        i++
    }
    if (i >= sig.args.size) return execute(def, tokens)
    ask(Step(plan, def, sig, i, tokens))
}

private fun PlayerRef.execute(def: CommandDef, tokens: List<String>) {
    runLine((listOf(def.name) + tokens).joinToString(" "))
}

private fun PlayerRef.notice(text: String) {
    if (live() != null) ifText(Components.Commands.NOTICE, cleanCell(text))
}

// ---- questions

private fun PlayerRef.supply(step: Step, value: String) {
    val prefix = step.plan.prefixes[step.index]
    val word = if (prefix == null) value else "$prefix $value"
    nextStep(step.plan, step.index + 1, step.tokens + word)
}

/** The optional argument was left out: the rest of the line is left out too (words are positional). */
private fun PlayerRef.skip(step: Step) = execute(step.def, step.tokens)

private fun PlayerRef.ask(step: Step) {
    val arg = step.arg
    val label = arg.name.replace('_', ' ')
    when (arg) {
        is Arg.PlayerArg -> {
            pickPlayer(step)
        }

        is Arg.ChoiceArg -> {
            pick(step, arg.choices.map { Option(it, "", it) })
        }

        is Arg.BoolArg -> {
            pick(step, listOf(Option("On", "", "on"), Option("Off", "", "off")))
        }

        is Arg.DurationArg -> {
            pick(
                step,
                DURATIONS.map { Option(it.first, it.second, it.second) },
                listOf("Another length..." to { typed(step, "Enter a duration (30m, 2h, 7d or perm):") }),
            )
        }

        is Arg.Text -> {
            if (arg.name.startsWith("reason", ignoreCase = true)) {
                val presets = commandsConfig.reasonPresets.map { Option(it, "", quoteToken(typedText(it))) }
                pick(step, presets, listOf("Type a reason..." to { typed(step, "Enter the reason:") }))
            } else {
                entryOrSkip(step) { typed(step, "Enter the $label:") }
            }
        }

        is Arg.IntArg -> {
            entryOrSkip(step) { if (arg.min >= 0) count(step, "Enter the $label:") else typed(step, "Enter the $label:") }
        }

        is Arg.LongArg -> {
            entryOrSkip(step) { typed(step, "Enter the $label (10k, 1.5m and so on):") }
        }

        is Arg.CacheArg -> {
            entryOrSkip(step) { typed(step, "Enter the $label (a name or an id):") }
        }

        is Arg.ObjArg -> {
            pick(
                step,
                emptyList(),
                listOf(
                    "Search for an item..." to { itemSearch(step) },
                    "Type a name or id..." to { typed(step, "Enter an item name or id:") },
                ),
            )
        }

        is Arg.CoordArg -> {
            val c = coord
            val here = "${c.x} ${c.z} ${c.level}"
            pick(
                step,
                listOf(Option("Where I stand", here, here)),
                listOf("Type a place..." to { typed(step, "Enter x y [level], x,y,level or a Jagex coordinate:", raw = true) }),
            )
        }

        is Arg.SkillArg -> {
            pick(step, (0 until Stats.COUNT).map { Option(Stats.name(it), "", Stats.name(it).lowercase()) })
        }

        is Arg.RankArg -> {
            pick(step, Rank.entries.map { Option(rankLabel(it), "", it.name.lowercase()) })
        }
    }
}

/** A required argument asks at once; an optional one first offers Skip next to the entry row. */
private fun PlayerRef.entryOrSkip(step: Step, open: () -> Unit) {
    if (step.arg.optional) pick(step, emptyList(), listOf("Enter a value..." to open)) else open()
}

private class Option(val label: String, val detail: String, val token: String)

/**
 * A screen of answers (one row each, carrying the word to put on the line), then entry rows that open a dialog, and Skip for
 * optional arguments.
 */
private fun PlayerRef.pick(step: Step, options: List<Option>, entries: List<Pair<String, () -> Unit>> = emptyList()) {
    val title = "::${step.def.name} - ${step.arg.name.replace('_', ' ')}"
    val screen =
        CmdScreen(
            title,
            step = true,
            hint = "Choose an answer, or Back to change an earlier one.",
            key = "step:${step.def.name}:${step.index}",
        ) { _ ->
            val rows = ArrayList<CmdRow>()
            for (o in options) rows += item(Tags.escape(o.label), Tags.escape(o.detail)) { supply(step, o.token) }
            for ((label, open) in entries) rows += item(label, "Type it yourself") { open() }
            if (step.arg.optional) rows += item("Skip", "Leave this out") { skip(step) }
            rows
        }
    go(screen)
}

private fun PlayerRef.pickPlayer(step: Step) {
    val screen =
        CmdScreen(
            "::${step.def.name} - ${step.arg.name.replace('_', ' ')}",
            searchable = true,
            step = true,
            empty = "No players match.",
            hint = "Click a player, or type a name (it can be offline for commands that allow it).",
            key = "step:${step.def.name}:${step.index}",
        ) { query ->
            val word = query.trim().lowercase()
            val rows = ArrayList<CmdRow>()
            rows += item("Type a name...", "Any account name") { typed(step, "Enter the player's name:", asName = true) }
            for (who in onlineEverywhere().sortedBy { it.name.lowercase() }) {
                if (word.isNotEmpty() && word !in who.name.lowercase()) continue
                val name = who.name
                rows += item(Tags.escape(name), "World ${who.worldId}", rankLabel(who.rank)) { supply(step, nameToken(name)) }
            }
            if (step.arg.optional) rows += item("Skip", "Leave this out") { skip(step) }
            rows
        }
    go(screen)
}

// ---- dialogs

/** True while the interface is still on the screen that asked: a late answer for an older screen is dropped. */
private fun PlayerRef.same(nav: Int): Boolean = this[CMD_STATE]?.nav == nav && isOpen(Interfaces.COMMANDS)

private fun PlayerRef.typed(step: Step, title: String, raw: Boolean = false, asName: Boolean = false) {
    val nav = this[CMD_STATE]?.nav ?: return
    clientScript(Clientscripts.COMMANDS_SEARCH_STOP)
    stringDialog(title) { answer ->
        if (!same(nav)) return@stringDialog
        val text = typedText(answer)
        if (text.isEmpty()) return@stringDialog
        supply(
            step,
            if (asName) {
                nameToken(text)
            } else if (raw) {
                text
            } else {
                quoteToken(text)
            },
        )
    }
}

private fun PlayerRef.count(step: Step, title: String) {
    val nav = this[CMD_STATE]?.nav ?: return
    clientScript(Clientscripts.COMMANDS_SEARCH_STOP)
    countDialog(title) { n -> if (same(nav)) supply(step, n.toString()) }
}

private fun PlayerRef.itemSearch(step: Step) {
    val nav = this[CMD_STATE]?.nav ?: return
    clientScript(Clientscripts.COMMANDS_SEARCH_STOP)
    objDialog("What item?") { obj -> if (same(nav) && obj >= 0) supply(step, obj.toString()) }
}

/** Duration answers: label and the word the parser reads (commands brief section 5: 1h, 1d, 7d, 30d or permanent). */
private val DURATIONS: List<Pair<String, String>> =
    listOf("One hour" to "1h", "One day" to "1d", "Seven days" to "7d", "Thirty days" to "30d", "Permanent" to "perm")

// ---- running

/**
 * Shows the typed equivalent in chat and runs [line] through the pipeline as the interface (the same checks as typing it;
 * the interface has no rights of its own). The outcome then becomes the next screen, unless the command moved the
 * interface itself (a list it published) or closed it.
 */
internal fun PlayerRef.runLine(line: String) {
    val before = live()?.nav ?: return
    message("Running ::${Tags.escape(line)}")
    val outcome = runCommandLine(line, Source.INTERFACE)
    val s = this[CMD_STATE] ?: return
    if (s.nav != before) return
    goResult(outcomeScreen(line, outcome))
}

private fun outcomeScreen(line: String, outcome: CommandDispatcher.Outcome?): CmdScreen {
    val shown = Tags.escape(line)
    val rows = ArrayList<CmdRow>()
    when (outcome) {
        null -> rows += infoRows("Commands are not available on this world.")
        CommandDispatcher.Outcome.Unknown -> rows += infoRows("Unknown command. Type ::commands for a list.")
        CommandDispatcher.Outcome.Dropped -> rows += infoRows("Too many commands at once. Wait a moment and try again.")
        is CommandDispatcher.Outcome.Refused -> rows += section("Not run", outcome.message)
        is CommandDispatcher.Outcome.AwaitingConfirm -> return confirmScreen(line, outcome.message)
        is CommandDispatcher.Outcome.Ran -> rows += resultRows(outcome.result)
    }
    rows += item("Run again", "::$shown") { runLine(line) }
    return CmdScreen("Result of ::$shown", hint = "The reply is also in your chat box.") { _ -> rows }
}

private fun resultRows(result: CommandResult): List<CmdRow> =
    when (result) {
        is CommandResult.Done -> section("Done", result.message ?: "The command ran.")
        is CommandResult.Failed -> section("Failed", result.message)
        is CommandResult.Pending -> section("Working", result.message ?: "The reply will appear in your chat box.")
    }

private fun section(title: String, text: String): List<CmdRow> = listOf(heading(title)) + infoRows(text)

/** The pipeline's own confirmation: the command is waiting; Yes runs `confirm`, No leaves it to expire (30 seconds). */
private fun confirmScreen(line: String, message: String): CmdScreen =
    CmdScreen(
        "Confirm ::${Tags.escape(line)}",
        hint = "The command waits for you; if you choose No it runs only if you type ::confirm in time.",
    ) { _ ->
        val rows = ArrayList<CmdRow>()
        rows += section("Are you sure?", message)
        rows +=
            item("Yes, run it", "::${Tags.escape(line)}") {
                val outcome = runCommandLine("confirm", Source.INTERFACE)
                val text =
                    if (outcome is CommandDispatcher.Outcome.Ran) {
                        "Confirmed. The reply is in your chat box."
                    } else {
                        "The confirmation was not accepted."
                    }
                goResult(CmdScreen("Confirmed", hint = "The reply is also in your chat box.") { _ -> infoRows(text) })
            }
        rows += item("No, go back", "") { goBack() }
        rows
    }
