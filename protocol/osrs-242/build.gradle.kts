plugins {
    id("tp.kotlin-library")
}

// Adapter: implements :protocol-api on the vendored RSProt 242 (D-005). Only this module and :net import net.rsprot.
dependencies {
    api(project(":protocol-api"))
    implementation(project(":cache"))
    implementation(project(":symbols"))
    api(project(":protocol:osrs-242:rsprot:api"))
    implementation(project(":protocol:osrs-242:rsprot:shared"))
    implementation(libs.rsprot.buffer)
    implementation(platform(libs.netty.bom))
    implementation(libs.netty.buffer)
    implementation(libs.kotlin.logging)
}
