plugins {
    id("tp.kotlin-vendored")
}

dependencies {
    implementation(platform(libs.netty.bom))
    api(libs.netty.buffer)
    implementation(libs.netty.transport)
    implementation(libs.netty.handler)
    implementation(libs.netty.native.epoll)
    implementation(libs.netty.native.kqueue)
    implementation(libs.netty.native.iouring)
    implementation(libs.netty.native.macos.dns.resolver)
    // Native transports per platform (same classifiers as upstream).
    for (classifier in listOf("linux-aarch_64", "linux-x86_64", "linux-riscv64")) {
        implementation(variantOf(libs.netty.native.epoll) { classifier(classifier) })
    }
    implementation(variantOf(libs.netty.native.kqueue) { classifier("osx-x86_64") })
    for (classifier in listOf("linux-aarch_64", "linux-x86_64")) {
        implementation(variantOf(libs.netty.native.iouring) { classifier(classifier) })
    }
    implementation(libs.inline.logger)
    api(libs.rsprot.protocol)
    api(libs.rsprot.compression)
    api(libs.rsprot.crypto)
    api(project(":protocol:osrs-242:rsprot:common"))
    api(project(":protocol:osrs-242:rsprot:model"))
    implementation(project(":protocol:osrs-242:rsprot:internal"))
    implementation(project(":protocol:osrs-242:rsprot:desktop"))
    implementation(project(":protocol:osrs-242:rsprot:shared"))
    implementation(libs.rsprot.buffer)
}
