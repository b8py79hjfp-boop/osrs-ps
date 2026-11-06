plugins {
    id("tp.content")
}

// DPS parity matrix (Phase 4 done-when) reads obj params straight from the cache.
dependencies {
    testImplementation(project(":cache"))
    testImplementation(libs.kotlinx.serialization.json)
}
