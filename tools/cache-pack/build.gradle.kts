plugins {
    id("tp.kotlin-app")
}

application {
    mainClass = "tp.tools.cachepack.CachePackKt"
}

dependencies {
    implementation(project(":cache"))
    implementation(project(":util"))
    implementation(project(":tools:cache-pack:neptune:clientscript-compiler"))
    implementation(libs.commons.compress)
    // rs2asm disassembler + assembler for patches of existing client scripts (D-094); only what those classes use.
    implementation(libs.runelite.cache) { isTransitive = false }
    implementation(libs.guava)
    runtimeOnly(libs.logback.classic)
}
