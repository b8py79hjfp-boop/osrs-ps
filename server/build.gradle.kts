plugins {
    id("tp.kotlin-app")
    // Typed config (config/world.toml, D-087): ktoml decodes into @Serializable classes.
    kotlin("plugin.serialization")
}

application {
    mainClass = "tp.server.MainKt"
    applicationName = "world-node"
}

dependencies {
    implementation(project(":util"))
    implementation(project(":cache"))
    implementation(project(":net"))
    implementation(project(":engine"))
    runtimeOnly(project(":content:gameframe"))
    runtimeOnly(project(":content:staff"))
    runtimeOnly(project(":content:dev"))
    runtimeOnly(project(":content:magic"))
    runtimeOnly(project(":content:drops"))
    runtimeOnly(project(":content:collectionlog"))
    runtimeOnly(project(":content:diaries"))
    runtimeOnly(project(":content:shops"))
    runtimeOnly(project(":content:sailing"))
    runtimeOnly(project(":content:quests"))
    runtimeOnly(project(":content:skills"))
    runtimeOnly(project(":content:raids-core"))
    runtimeOnly(project(":content:raids-tfa"))
    runtimeOnly(project(":content:raids-tob"))
    runtimeOnly(project(":content:raids-cox"))
    runtimeOnly(project(":content:raids-toa"))
    implementation(project(":plugin-api"))
    implementation(project(":services"))
    implementation(project(":hub"))
    implementation(project(":protocol:osrs-242"))
    implementation(project(":symbols"))
    implementation(project(":routefinder"))
    implementation(libs.rsprot.crypto)
    implementation(project(":content:combat"))
    implementation(project(":content:pvp"))
    implementation(project(":content:world"))
    implementation(libs.kotlin.logging)
    implementation(libs.ktoml.core)
    runtimeOnly(libs.logback.classic)
}

// Commands brief §3: regenerates docs/commands.md from the command registry (a boot check that also writes the file).
tasks.register<JavaExec>("commandsDoc") {
    group = "tickperfect"
    description = "Writes docs/commands.md from the command registry."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "tp.server.MainKt"
    workingDir = rootDir
    args("--boot-check", "--commands-doc", "docs/commands.md")
}
