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
    implementation(libs.grpc.netty.shaded)
    implementation(platform(libs.netty.bom))
    implementation(project(":net"))
    runtimeOnly(libs.logback.classic)
}
