plugins {
    id("tp.kotlin-library")
}

// Scenario runner: drives the real engine headlessly (no network) with scripted per-tick input, forced PID and a fixed
// seed. Phase 4's golden tests (brief §9 test 3) are written on it; tools/replay reuses its headless session.
dependencies {
    api(project(":engine"))
    api(project(":protocol-api"))
    api(project(":util"))
    implementation(project(":cache"))
    implementation(project(":plugin-api"))
    runtimeOnly(project(":content:gameframe"))
    runtimeOnly(project(":content:dev"))
    runtimeOnly(project(":content:combat"))
    runtimeOnly(project(":content:pvp"))
}
