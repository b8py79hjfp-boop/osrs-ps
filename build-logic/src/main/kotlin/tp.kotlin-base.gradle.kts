import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("com.diffplug.spotless")
    id("dev.detekt")
}

val libs = the<LibrariesForLibs>()
val javaVersion = providers.gradleProperty("osrs.javaVersion").map(String::toInt).get()

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion.toString())
        progressiveMode = true
        // Brief §11: no `!!`, `internal` by default — enforced by review + detekt; these keep warnings honest.
        extraWarnings = true
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Tests must be deterministic and headless (brief §9).
    systemProperty("java.awt.headless", "true")
    // Test 1 needs the cache; it locates it from the repo root.
    systemProperty("tp.repoRoot", rootDir.absolutePath)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

extensions.configure<DetektExtension> {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootDir.resolve("config/detekt/detekt.yml"))
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
