plugins {
    id("tp.kotlin-library")
}

dependencies {
    api(project(":util"))
    api(project(":protocol-api"))
    api(project(":routefinder"))
    api(project(":cache"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.logging)
}
