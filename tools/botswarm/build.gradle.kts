plugins {
    id("tp.kotlin-app")
}

application {
    mainClass = "tp.tools.botswarm.SwarmKt"
}

// Headless rev-242 client: reuses RSProt's prot tables, ISAAC and buffer writers (brief §7: "reuse our protocol module").
dependencies {
    implementation(project(":protocol:osrs-242:rsprot:desktop"))
    implementation(libs.rsprot.crypto)
    implementation(libs.rsprot.buffer)
    implementation(platform(libs.netty.bom))
    implementation(libs.netty.buffer)
    testImplementation(project(":server"))
    testImplementation(project(":cache"))
    testImplementation(project(":engine"))
    testImplementation(project(":protocol:osrs-242"))
}
