plugins {
    id("tp.kotlin-library")
}

dependencies {
    implementation(project(":plugin-api"))
    implementation(project(":symbols"))
}
