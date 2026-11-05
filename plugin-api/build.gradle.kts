plugins {
    id("tp.kotlin-api")
}

// Stable content API (explicit API mode). Content modules depend only on this and :symbols.
dependencies {
    api(project(":util"))
    api(project(":symbols"))
    api(libs.kotlinx.coroutines.core)
}
