plugins {
    id("tp.kotlin-app")
}

// Release-client game patcher (brief §4.2): rewrites constants in RuneLite's injected game client by value.
application {
    mainClass = "tp.client.gamepatch.GamePatchKt"
}

dependencies {
    implementation(libs.asm)
    implementation(libs.kotlinx.serialization.json)
}

// `./gradlew :client:gamepatch:patchGameClient`: patches the mirrored injected client with this machine's RSA key and
// the site from client/runelite/release.properties (used by the root `releaseClient` task).
val releaseSite =
    providers.fileContents(rootProject.layout.projectDirectory.file("client/runelite/release.properties")).asText.map { text ->
        text
            .lines()
            .firstOrNull { it.startsWith("site=") }
            ?.removePrefix("site=")
            ?.trim() ?: error("site= missing in release.properties")
    }
tasks.register<JavaExec>("patchGameClient") {
    group = "tickperfect"
    description = "Patches .data/client/artifacts/injected-client into .data/client/patched/ (RSA key + URLs, by value)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "tp.client.gamepatch.GamePatchKt"
    workingDir = rootDir
    val site = releaseSite
    argumentProviders +=
        CommandLineArgumentProvider {
            listOf(
                ".data/client/artifacts/injected-client-1.13.6.jar",
                ".data/client/patched/injected-client-1.13.6.jar",
                "--site",
                site.get(),
            )
        }
}
