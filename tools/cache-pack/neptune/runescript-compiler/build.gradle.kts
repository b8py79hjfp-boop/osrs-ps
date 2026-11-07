plugins {
    id("tp.kotlin-vendored")
}

description = "Neptune runescript-compiler (MIT), vendored. See ../NOTICE.md."

dependencies {
    api(project(":tools:cache-pack:neptune:runescript-parser"))
    implementation(libs.guava)
    implementation(libs.inline.logger)
}
