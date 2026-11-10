import tp.build.GenerateRsaTask
import tp.build.SetupCacheTask
import tp.build.SetupClientTask
import tp.build.UpgradeRevisionTask

val revisionProp = providers.gradleProperty("osrs.revision")
val cacheIdProp = providers.gradleProperty("osrs.openrs2CacheId")

tasks.register<SetupCacheTask>("setup") {
    group = "tickperfect"
    description = "Downloads the OpenRS2 cache + keys for the configured revision into .data/cache/<rev>/ and verifies them."
    revision = revisionProp
    openrs2CacheId = cacheIdProp
    pinnedChecksums = layout.projectDirectory.file(revisionProp.map { "gradle/cache-checksums/$it.sha256" })
    outputDir = layout.projectDirectory.dir(revisionProp.map { ".data/cache/$it" })
}

tasks.register<GenerateRsaTask>("generateRsa") {
    group = "tickperfect"
    description = "Generates the 1024-bit login RSA keypair into .data/rsa/ and prints the modulus for proxy-targets.yaml."
    outputDir = layout.projectDirectory.dir(".data/rsa")
    force = providers.gradleProperty("force").map { it.isEmpty() || it.toBoolean() }.orElse(false)
}

tasks.register<UpgradeRevisionTask>("upgradeRevision") {
    group = "tickperfect"
    description = "Moves the project to another revision: -Prev=<N> [-PcacheId=<openrs2 id>] (docs/REVISION-UPGRADE.md)."
    targetRevision = providers.gradleProperty("rev")
    cacheId = providers.gradleProperty("cacheId")
    projectDir = layout.projectDirectory
}

tasks.register<SetupClientTask>("setupClient") {
    group = "tickperfect"
    description = "Downloads the pinned RuneLite bootstrap's artifacts into .data/client/ and verifies their sha256."
    bootstrapCommit = providers.gradleProperty("osrs.runeliteBootstrap")
    outputDir = layout.projectDirectory.dir(".data/client")
}

// ---- Release client (Phase 6, docs/CLIENT.md). Cross-platform; client/runelite/build.sh does the same in bash.

val forkClient =
    tasks.register<tp.build.ForkClientTask>("forkClient") {
        group = "tickperfect"
        description = "Builds the RuneLite fork (clone pinned tag, apply client/runelite/patches, overlay, build) into .data/client/fork/."
        repoDir = layout.projectDirectory
        val root = layout.projectDirectory
        val defaultWork = root.dir(System.getProperty("java.io.tmpdir") + "/tp-runelite-fork")
        workDir = providers.gradleProperty("forkWorkDir").map { root.dir(it) }.orElse(defaultWork)
        runeliteGit = providers.gradleProperty("runeliteGit").orElse("https://github.com/runelite/runelite.git")
        tag = providers.gradleProperty("osrs.runeliteTag")
        gradleArgs = providers.gradleProperty("forkGradleArgs")
        plugins = providers.gradleProperty("forkPlugins")
        javaHome = providers.gradleProperty("forkJavaHome")
    }

tasks.register<tp.build.MergeClientTask>("releaseClient") {
    group = "tickperfect"
    description = "Builds the release client: fork + patched game client merged into .data/client/fork/client-*-shaded.jar."
    dependsOn(forkClient, ":client:gamepatch:patchGameClient")
    forkJar = layout.projectDirectory.file(".data/client/fork/client-1.13.6-shaded.jar")
    patched = layout.projectDirectory.file(".data/client/patched/injected-client-1.13.6.jar")
}

tasks.register<Sync>("assembleReleaseArtifacts") {
    group = "tickperfect"
    description = "Collects the jars the signed update manifest lists into .data/client/release-artifacts/."
    dependsOn("releaseClient")
    into(layout.projectDirectory.dir(".data/client/release-artifacts"))
    from(layout.projectDirectory.dir(".data/client/artifacts")) {
        include("*.jar")
        exclude("client-*.jar", "injected-client-*.jar")
    }
    from(layout.projectDirectory.dir(".data/client/patched")) { include("injected-client-*.jar") }
    from(layout.projectDirectory.dir(".data/client/fork")) {
        include("client-*-shaded.jar")
        rename("client-(.*)-shaded\\.jar", "client-$1.jar")
    }
}
