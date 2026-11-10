package tp.hub

import tp.util.Rank
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Out-of-game account administration (commands brief §1: the first developer is set here, never in game).
 *
 * `./gradlew admin --setRank=<account>:<RANK> --db=<jdbc url> [--dbUser=...] [--dbPassword=... | --dbPasswordFile=...]`:
 * sets the rank in the accounts database and writes the change to the audit log (actor `cli`). A running hub picks the
 * rank up at the account's next login (live pushes come with `::setrank`).
 *
 * `--setPassword=<account>` (same database options): sets a new password, read twice from the console (never the
 * command line), for a staff member who forgot theirs (panel/docs/operations.md). Audited.
 *
 * On the VPS it runs from the hub's own jars: `java -cp C:\lotus\nodes\lotus-hub\hub\lib\* tp.hub.AdminKt ...`.
 */
fun main(args: Array<String>) {
    val options = args.mapNotNull { a -> a.removePrefix("--").split('=', limit = 2).takeIf { it.size == 2 } }.associate { it[0] to it[1] }
    val db =
        options["db"] ?: usage("--db=<jdbc url> is required (the embedded dev hub's in-memory store has no CLI: use [ranks.bootstrap])")
    val dbPassword = options["dbPasswordFile"]?.let { Files.readString(Path.of(it)).trim() } ?: options["dbPassword"].orEmpty()
    val store = PostgresStore(db, options["dbUser"] ?: "postgres", dbPassword)
    when {
        options["setRank"] != null -> setRank(store, options.getValue("setRank"))
        options["setPassword"] != null -> setPassword(store, options.getValue("setPassword"))
        else -> usage("--setRank=<account>:<RANK> or --setPassword=<account> is required")
    }
}

private fun setRank(store: PostgresStore, arg: String) {
    val name = arg.substringBeforeLast(':').trim().replace('_', ' ')
    val rank = Rank.parse(arg.substringAfterLast(':')) ?: usage("unknown rank in '$arg' (${Rank.entries.joinToString()})")
    val account = store.account(name) ?: usage("no account '$name'")
    store.setRights(account.id, rank)
    store.audit("cli", "set-rank", "account:${account.id}", "${account.displayName} ${account.rights.stored} -> ${rank.stored}")
    println("${account.displayName}: ${account.rights} -> $rank (audited)")
}

private fun setPassword(store: PostgresStore, arg: String) {
    val account = store.account(arg.trim().replace('_', ' ')) ?: usage("no account '$arg'")
    val console = System.console() ?: usage("--setPassword needs a console (run it in PowerShell, not from a script)")
    val first = console.readPassword("New password for %s: ", account.displayName)?.let(::String).orEmpty()
    val again = console.readPassword("Again: ")?.let(::String).orEmpty()
    if (first != again) usage("the two passwords differ")
    // The game's own limits (Hub.changePassword).
    if (first.length !in MIN_PASSWORD..MAX_PASSWORD) usage("passwords are $MIN_PASSWORD-$MAX_PASSWORD characters")
    store.setPasswordHash(account.id, Passwords.hash(first))
    store.audit("cli", "set-password", "account:${account.id}", account.displayName)
    println("${account.displayName}: password changed (audited). Their open panel sessions stay until they expire or are revoked.")
}

/** Hub.changePassword's limits. */
private const val MIN_PASSWORD = 5
private const val MAX_PASSWORD = 20

private fun usage(problem: String): Nothing {
    System.err.println("admin: $problem")
    exitProcess(2)
}
