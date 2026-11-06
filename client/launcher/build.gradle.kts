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
