plugins {
    id("tp.kotlin-library")
}

dependencies {
    api(project(":util"))
    implementation(project(":cache"))
    implementation(project(":protocol-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging)
}
