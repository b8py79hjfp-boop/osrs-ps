plugins {
    id("tp.kotlin-app")
}

application {
    mainClass = "tp.tools.replay.ReplayKt"
}

// Re-runs a recorded replay log (server `--record`) headlessly and diffs per-tick state hashes (brief §5.5).
dependencies {
    implementation(project(":tools:scenario"))
    implementation(project(":engine"))
    implementation(project(":plugin-api"))
    runtimeOnly(libs.logback.classic)
}
