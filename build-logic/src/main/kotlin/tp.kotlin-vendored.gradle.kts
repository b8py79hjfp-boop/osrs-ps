// Third-party source kept verbatim (license + NOTICE in the module). No ktlint/detekt: we don't reformat upstream code.
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    kotlin("jvm")
    `java-library`
}

val libs = the<LibrariesForLibs>()
val javaVersion = providers.gradleProperty("osrs.javaVersion").map(String::toInt).get()

kotlin {
    jvmToolchain(javaVersion)
    explicitApi()
    compilerOptions {
        // RSProt compiles with -Xjvm-default=all (interface default methods, no DefaultImpls); match it.
        jvmDefault = org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY
    }
}
