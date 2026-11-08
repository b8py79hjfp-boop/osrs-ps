package tp.plugin.commands

import tp.util.Rank

/**
 * docs/commands.md from the registry (commands brief §3: one source of truth): one table per rank, with usage,
 * aliases, impact class and whether a target is needed. Regenerate with `./gradlew :server:commandsDoc`.
 */
public object CommandDocs {
    public fun markdown(registry: CommandRegistry): String =
        buildString {
            appendLine("# Commands")
            appendLine()
            appendLine("Generated from the command registry by `./gradlew :server:commandsDoc`; do not edit by hand.")
            appendLine("Ranks, world policy, the interface and security are explained in docs/features/commands.md.")
            appendLine("Each rank can also use every command of the ranks below it. A world's `config/server.toml` can")
            appendLine("raise or lower a command's rank, or disable it; impact classes it does not enable are hidden there.")
            for (rank in Rank.entries) {
                val defs = registry.all.filter { it.rank == rank }.sortedBy { it.name }
                if (defs.isEmpty()) continue
                appendLine()
                appendLine("## ${rank.name.lowercase().replaceFirstChar(Char::uppercase)}")
                appendLine()
                appendLine("| Command | Usage | Aliases | Impact | Target | Does |")
                appendLine("|---|---|---|---|---|---|")
                for (d in defs) {
                    val usage = d.usages.joinToString("<br>") { "`${it.replace("|", "\\|")}`" }
                    val aliases = d.aliases.joinToString(", ") { "`::$it`" }.ifEmpty { "—" }
                    val flags =
                        buildList {
                            if (d.confirm) add("confirmation")
                            if (d.crossWorld) add("cross-world")
                        }
                    val does = d.description.replace("|", "\\|") + if (flags.isEmpty()) "" else " (${flags.joinToString(", ")})"
                    appendLine("| `::${d.name}` | $usage | $aliases | ${d.impact.name} | ${if (d.target) "yes" else "—"} | $does |")
                }
            }
        }
}
