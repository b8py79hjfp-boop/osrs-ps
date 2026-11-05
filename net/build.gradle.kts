plugins {
    id("tp.kotlin-library")
}

dependencies {
    api(project(":protocol-api"))
    implementation(project(":cache"))
    implementation(libs.kotlin.logging)
}
