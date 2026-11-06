import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("tp.kotlin-app")
}

// Release-client launcher (brief §4.2): verifies a signed manifest, updates the client, starts it.
// It runs on the bundled runtime, which is the Java major RuneLite's own launcher bundles (11; D-028), so it targets 11
// and uses only the JDK (no Kotlin libraries beyond the stdlib).
application {
    mainClass = "tp.client.launcher.LauncherKt"
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 11
}

// `./gradlew :client:launcher:release --args="keygen .data/manifest"` / `--args="publish ..."` (ReleaseTool.kt).
tasks.register<JavaExec>("release") {
    group = "tickperfect"
    description = "Release tooling: manifest keygen and signed publish."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "tp.client.launcher.ReleaseToolKt"
    workingDir = rootDir
}

// Only the shipped code targets 11; resolution (incl. the shared JUnit 6 test platform) isn't tied to that target.
java {
    disableAutoTargetJvm()
}

// Dev builds trust the developer's own manifest key: if `.data/manifest/manifest-public.key` exists (from
// `release keygen .data/manifest`), it replaces the committed `publicKey` in launcher.properties. Release builds
// (CI) use the committed value.
val devPublicKey = providers.fileContents(rootProject.layout.projectDirectory.file(".data/manifest/manifest-public.key")).asText
tasks.named<ProcessResources>("processResources") {
    val key = devPublicKey.orNull?.trim()
    inputs.property("devPublicKey", key ?: "")
    filesMatching("**/launcher.properties") {
        if (!key.isNullOrEmpty()) filter { line -> if (line.startsWith("publicKey=")) "publicKey=$key" else line }
    }
}
