plugins {
    id("tp.kotlin-library")
}

dependencies {
    api(project(":util"))
    api(project(":protocol-api"))
    api(project(":routefinder"))
    implementation(project(":cache"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging)
}
