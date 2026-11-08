plugins {
    id("tp.kotlin-app")
}

application {
    mainClass = "tp.tools.raidsoak.RaidSoakKt"
}

// Raids brief section 7 (manual or nightly): instance soak, memory per instance and the 300-instance load, in process on
// the headless engine (tools/scenario), with every raid plugin loaded.
dependencies {
    implementation(project(":tools:scenario"))
    implementation(project(":content:raids-core"))
    implementation(project(":plugin-api"))
    runtimeOnly(libs.logback.classic)
}

tasks.named<JavaExec>("run") {
    systemProperty("tp.devSpawns", "true")
    systemProperty("tp.worldSpawns", "false")
    jvmArgs("-Xmx4g", "-XX:+UseZGC")
}
