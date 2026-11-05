plugins {
    id("tp.kotlin-app")
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
    runtimeOnly(project(":content:dev"))
    implementation(project(":plugin-api"))
    implementation(project(":services"))
    implementation(project(":protocol:osrs-242"))
    implementation(project(":symbols"))
    implementation(project(":routefinder"))
    implementation(libs.rsprot.crypto)
    implementation(project(":content:combat"))
    implementation(project(":content:pvp"))
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)
}
