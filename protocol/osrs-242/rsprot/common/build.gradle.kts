plugins {
    id("tp.kotlin-vendored")
}

dependencies {
    api(platform(libs.netty.bom))
    api(libs.netty.buffer)
    api(libs.rsprot.protocol)
}
