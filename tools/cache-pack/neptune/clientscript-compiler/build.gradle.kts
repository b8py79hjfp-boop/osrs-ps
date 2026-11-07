plugins {
    id("tp.kotlin-vendored")
}

description = "Neptune clientscript-compiler (MIT), vendored. See ../NOTICE.md."

kotlin {
    // Upstream does not use explicit API mode for this module.
    explicitApi = org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode.Disabled
}

dependencies {
    api(project(":tools:cache-pack:neptune:runescript-compiler"))
    implementation(platform(libs.netty.bom))
    implementation(libs.netty.buffer)
    implementation(libs.guava)
    implementation(libs.inline.logger)
}
