import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("tp.kotlin-library")
}

// KSP processor: generates each content module's plugin registry at compile time (brief §6, DECISIONS D-023).
dependencies {
    implementation(libs.ksp.api)
}

// The processor runs inside the Gradle/Kotlin daemon, whose JVM may be older than the project toolchain (e.g. a
// Gradle on JDK 21 with toolchain 25), so its bytecode targets 17.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}
