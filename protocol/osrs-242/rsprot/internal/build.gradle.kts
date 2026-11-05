plugins {
    id("tp.kotlin-vendored")
}

dependencies {
    api(platform(libs.netty.bom))
    api(libs.netty.buffer)
    api(libs.netty.transport)
    api(libs.commons.pool2)
    implementation(libs.inline.logger)
    api(libs.rsprot.buffer)
    api(libs.rsprot.compression)
    api(libs.rsprot.crypto)
    api(libs.rsprot.protocol)
    api(project(":protocol:osrs-242:rsprot:common"))
    implementation(libs.fastutil)
}
