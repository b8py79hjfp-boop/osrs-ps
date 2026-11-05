plugins {
    id("tp.kotlin-app")
}

application {
    mainClass = "tp.tools.cachedump.CacheDumpKt"
}

dependencies {
    implementation(project(":cache"))
}
