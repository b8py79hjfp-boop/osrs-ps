package tp.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * `./gradlew upgradeRevision -Prev=<N> [-PcacheId=<openrs2 id>]`: the mechanical half of a revision upgrade
 * (docs/REVISION-UPGRADE.md).
 * - Finds the newest OpenRS2 cache for oldschool/live/en build N (`caches.json`), unless `-PcacheId` is given.
 * - Rewrites `osrs.revision` / `osrs.openrs2CacheId` in gradle.properties and `Revision.NUMBER`, which are the only places
 *   that name the revision.
 * - Prints the manual steps that follow: `setup` (pins the new checksums), vendoring RSProt N, the protocol adapter,
 *   symbols, the RuneLite bootstrap, and tests.
 */
@UntrackedTask(because = "Edits project files on request.")
public abstract class UpgradeRevisionTask : DefaultTask() {
    @get:Input
    @get:Optional
    public abstract val targetRevision: Property<String>

    @get:Input
    @get:Optional
    public abstract val cacheId: Property<String>

    @get:Internal
    public abstract val projectDir: DirectoryProperty

    @TaskAction
    public fun run() {
        val rev = targetRevision.orNull?.toIntOrNull() ?: throw GradleException("Usage: ./gradlew upgradeRevision -Prev=<N> [-PcacheId=<id>]")
        val root = projectDir.get().asFile
        val props = File(root, "gradle.properties")
        val current = Regex("""(?m)^osrs\.revision=(\d+)""").find(props.readText())?.groupValues?.get(1)?.toInt()
        if (current == rev) throw GradleException("Already on revision $rev.")
        val id = cacheId.orNull ?: findCache(rev)

        props.writeText(
            props
                .readText()
                .replace(Regex("""(?m)^osrs\.revision=\d+"""), "osrs.revision=$rev")
                .replace(Regex("""(?m)^osrs\.openrs2CacheId=\d+"""), "osrs.openrs2CacheId=$id"),
        )
        val revisionKt = File(root, "util/src/main/kotlin/tp/util/Revision.kt")
        revisionKt.writeText(revisionKt.readText().replace(Regex("""const val NUMBER: Int = \d+"""), "const val NUMBER: Int = $rev"))

        logger.lifecycle(
            """
            |Revision $current -> $rev (OpenRS2 cache $id): gradle.properties and Revision.kt updated.
            |Next (docs/REVISION-UPGRADE.md):
            |  1. ./gradlew setup                  (downloads cache $id; commit gradle/cache-checksums/$rev.sha256)
            |  2. vendor RSProt $rev into protocol/osrs-$rev/rsprot (keep its LICENSE; log it in DECISIONS.md)
            |  3. copy protocol/osrs-$current/src to protocol/osrs-$rev, fix compile errors against the new RSProt,
            |     switch :server to it (settings.gradle.kts + server/build.gradle.kts)
            |  4. ./gradlew build                  (symbols regenerate from the new cache; fix renamed constants)
            |  5. diff protocol with RSProx (scout), update the RuneLite bootstrap hash in docs/CLIENT.md
            |  6. golden scenarios + botswarm test 2 + a real-client login; then commit
            """.trimMargin(),
        )
    }

    /** Newest oldschool/live/en cache whose builds include [rev] (OpenRS2 `caches.json`). */
    private fun findCache(rev: Int): String {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).build()
        val request = HttpRequest.newBuilder(URI.create(CACHES_URL)).timeout(Duration.ofSeconds(TIMEOUT_SECONDS)).build()
        val body = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        @Suppress("UNCHECKED_CAST")
        val caches = groovy.json.JsonSlurper().parseText(body) as List<Map<String, Any?>>
        val best =
            caches
                .filter { it["game"] == "oldschool" && it["environment"] == "live" && it["language"] == "en" }
                .filter { c -> (c["builds"] as? List<*>).orEmpty().any { (it as? Map<*, *>)?.get("major") == rev } }
                .maxByOrNull { it["timestamp"].toString() }
                ?: throw GradleException("No OpenRS2 oldschool/live/en cache for build $rev yet; pass -PcacheId=<id>.")
        logger.lifecycle("OpenRS2 cache ${best["id"]} (${best["timestamp"]}) for build $rev.")
        return best["id"].toString()
    }

    private companion object {
        const val CACHES_URL = "https://archive.openrs2.org/caches.json"
        const val TIMEOUT_SECONDS = 30L
    }
}
