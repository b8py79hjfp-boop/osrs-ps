plugins {
    id("tp.kotlin-app")
}

application {
    mainClass = "tp.tools.symbolgen.SymbolGenKt"
}

dependencies {
    implementation(project(":cache"))
}
