plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    // Lets precompiled script plugins read the version catalog type-safely.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
