// Content modules (content/<area>): plugin-api + symbols only, plus the KSP-generated plugin registry (brief §6).
plugins {
    id("tp.kotlin-library")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":plugin-api"))
    implementation(project(":symbols"))
    ksp(project(":tools:ksp-registry"))
}

ksp {
    // content/gameframe -> GameframeRegistry
    arg("tp.registry", project.name.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) })
}
