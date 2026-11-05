import tp.build.GenerateRsaTask
import tp.build.SetupCacheTask

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
