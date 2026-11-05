plugins {
    id("tp.kotlin-vendored")
}

dependencies {
    implementation(libs.inline.logger)
    api(platform(libs.netty.bom))
    api(libs.netty.buffer)
    api(libs.netty.transport)
    api(libs.rsprot.buffer)
    api(libs.rsprot.compression)
    api(libs.rsprot.crypto)
    api(libs.rsprot.protocol)
    api(project(":protocol:osrs-242:rsprot:model"))
    api(project(":protocol:osrs-242:rsprot:internal"))
    api(project(":protocol:osrs-242:rsprot:common"))
}
