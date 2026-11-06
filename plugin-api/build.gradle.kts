plugins {
    id("tp.kotlin-api")
}

// Stable content API (explicit API mode). Content modules depend only on this and :symbols; the engine stays an
// implementation detail behind the facades here (brief §1, §6).
dependencies {
    implementation(libs.kotlin.logging)
    api(project(":util"))
    api(project(":symbols"))
    implementation(project(":engine"))
    implementation(project(":protocol-api"))
    implementation(libs.kotlinx.serialization.json)
}
