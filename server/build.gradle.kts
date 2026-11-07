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
    runtimeOnly(project(":content:magic"))
    runtimeOnly(project(":content:drops"))
    runtimeOnly(project(":content:collectionlog"))
    runtimeOnly(project(":content:shops"))
    runtimeOnly(project(":content:sailing"))
    runtimeOnly(project(":content:skills"))
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
    runtimeOnly(libs.logback.classic)
}
