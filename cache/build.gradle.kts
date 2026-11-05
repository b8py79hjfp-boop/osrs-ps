plugins {
    id("tp.kotlin-library")
}

dependencies {
    api(project(":util"))
    api(project(":routefinder"))
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.logging)
}
