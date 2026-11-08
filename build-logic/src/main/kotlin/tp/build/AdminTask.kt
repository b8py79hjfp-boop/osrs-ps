package tp.build

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * `./gradlew admin --setRank=<account>:<RANK> --db=<jdbc url> [--dbUser=..] [--dbPassword=..]` (commands brief §1):
 * runs the hub's `tp.hub.AdminKt` against the accounts database. The first developer is set here, never in game.
 */
abstract class AdminTask : JavaExec() {
    @get:Input
    @get:Option(option = "setRank", description = "<account>:<RANK>, e.g. Big_Bob:DEVELOPER")
    abstract val setRank: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "db", description = "The accounts database JDBC url")
    abstract val db: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "dbUser", description = "Database user (default postgres)")
    abstract val dbUser: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "dbPassword", description = "Database password")
    abstract val dbPassword: Property<String>

    @TaskAction
    override fun exec() {
        val out = mutableListOf("--setRank=${setRank.get()}")
        db.orNull?.let { out += "--db=$it" }
        dbUser.orNull?.let { out += "--dbUser=$it" }
        dbPassword.orNull?.let { out += "--dbPassword=$it" }
        setArgs(out)
        super.exec()
    }
}
