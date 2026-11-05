plugins {
    id("tp.kotlin-vendored")
}

dependencies {
    api(platform(libs.netty.bom))
    api(libs.netty.buffer)
    implementation(libs.inline.logger)
    api(libs.commons.pool2)
    implementation(libs.fastutil)
    api(libs.rsprot.buffer)
    api(libs.rsprot.compression)
    api(libs.rsprot.crypto)
    api(libs.rsprot.protocol)
    api(project(":protocol:osrs-242:rsprot:common"))
    api(project(":protocol:osrs-242:rsprot:internal"))
}
