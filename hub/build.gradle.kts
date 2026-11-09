plugins {
    id("tp.kotlin-app")
}

application {
    mainClass = "tp.hub.HubMainKt"
    applicationName = "hub"
}

// Hub (brief §7): login authority, persistence, social, GE, hiscores, moderation, broadcasts; gRPC + HTTP.
dependencies {
    api(project(":services"))
    implementation(libs.kotlin.logging)
    implementation(libs.bouncycastle)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.grpc.netty.shaded)
    implementation(platform(libs.netty.bom))
    implementation(project(":net"))
    runtimeOnly(libs.logback.classic)
}

// Commands brief §1: `./gradlew admin --setRank=<account>:<RANK> --db=<jdbc url>` (the only task named admin, so the
// root invocation runs this one). The first developer is set here, never in game.
tasks.register<tp.build.AdminTask>("admin") {
    group = "tickperfect"
    description = "Sets an account's rank in the accounts database (--setRank=<account>:<RANK> --db=<jdbc url>)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "tp.hub.AdminKt"
}
