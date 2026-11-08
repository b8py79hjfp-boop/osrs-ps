plugins {
    id("tp.content")
}

dependencies {
    implementation(project(":content:raids-core"))
    testImplementation(project(":tools:scenario"))
    testRuntimeOnly(libs.logback.classic)
}

// The smoke scenario runs the real engine headlessly (tools/scenario) without the world's Wiki spawns.
tasks.withType<Test>().configureEach {
    systemProperty("tp.devSpawns", "true")
    systemProperty("tp.worldSpawns", "false")
}
