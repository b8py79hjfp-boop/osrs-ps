package tp.hub

import tp.util.Rank
import kotlin.system.exitProcess

/**
 * Out-of-game account administration (commands brief §1: the first developer is set here, never in game).
 *
 * `./gradlew admin --setRank=<account>:<RANK> --db=<jdbc url> [--dbUser=...] [--dbPassword=...]`: sets the rank in the
 * accounts database and writes the change to the audit log (actor `cli`). A running hub picks the rank up at the
 * account's next login (live pushes come with `::setrank`).
 */
fun main(args: Array<String>) {
    val options = args.mapNotNull { a -> a.removePrefix("--").split('=', limit = 2).takeIf { it.size == 2 } }.associate { it[0] to it[1] }
    val setRank = options["setRank"] ?: usage("--setRank=<account>:<RANK> is required")
    val db =
        options["db"] ?: usage("--db=<jdbc url> is required (the embedded dev hub's in-memory store has no CLI: use [ranks.bootstrap])")
    val name = setRank.substringBeforeLast(':').trim().replace('_', ' ')
    val rank = Rank.parse(setRank.substringAfterLast(':')) ?: usage("unknown rank in '$setRank' (${Rank.entries.joinToString()})")
    val store = PostgresStore(db, options["dbUser"] ?: "postgres", options["dbPassword"] ?: "")
    val account = store.account(name) ?: usage("no account '$name'")
    store.setRights(account.id, rank)
    store.audit("cli", "set-rank", "account:${account.id}", "${account.displayName} ${account.rights.stored} -> ${rank.stored}")
    println("${account.displayName}: ${account.rights} -> $rank (audited)")
}

private fun usage(problem: String): Nothing {
    System.err.println("admin: $problem")
    exitProcess(2)
}
