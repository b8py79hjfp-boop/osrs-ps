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
    runtimeOnly(libs.logback.classic)
}
