plugins {
    id("tp.kotlin-library")
}

// KSP processor: generates each content module's plugin registry at compile time (brief §6, DECISIONS D-023).
dependencies {
    implementation(libs.ksp.api)
}
