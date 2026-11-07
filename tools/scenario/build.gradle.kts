plugins {
    id("tp.kotlin-library")
}

// Scenario runner: drives the real engine headlessly (no network) with scripted per-tick input, forced PID and a fixed
// seed. Brief §9 test 3 (golden PvP scenarios) lives in src/test; tools/replay reuses the headless session.
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
    runtimeOnly(project(":content:world"))
    runtimeOnly(project(":content:magic"))
    runtimeOnly(project(":content:drops"))
    runtimeOnly(project(":content:shops"))
    runtimeOnly(project(":content:sailing"))
    runtimeOnly(project(":content:skills"))
    testImplementation(project(":symbols"))
    testRuntimeOnly(libs.logback.classic)
}

// The golden scenarios use the dev spawns (content/dev DevSpawns) rather than the world's Wiki spawns.
tasks.withType<Test>().configureEach {
    systemProperty("tp.devSpawns", "true")
    systemProperty("tp.worldSpawns", "false")
}
