plugins {
    id("tp.kotlin-library")
}

dependencies {
    api(project(":util"))
    implementation(libs.commons.compress)
    implementation(libs.kotlin.logging)
}
