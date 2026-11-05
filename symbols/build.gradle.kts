plugins {
    id("tp.kotlin-library")
}

// Symbols are generated from cache archive 24 at build time and never committed (brief §0.6, DECISIONS D-014).
// The cache comes from `./gradlew setup`.
val revision = providers.gradleProperty("osrs.revision")
val cacheDir = rootProject.layout.projectDirectory.dir(revision.map { ".data/cache/$it/disk" })
val generatedDir = layout.buildDirectory.dir("generated/symbols")
val symbolGen = configurations.dependencyScope("symbolGen")
val symbolGenClasspath =
    configurations.resolvable("symbolGenClasspath") {
        extendsFrom(symbolGen.get())
    }

dependencies {
    api(project(":util"))
    symbolGen(project(":tools:symbol-gen"))
}

val generateSymbols =
    tasks.register<JavaExec>("generateSymbols") {
        description = "Generates tp.symbols.* from cache archive 24 (run ./gradlew setup first)."
        classpath(symbolGenClasspath)
        mainClass = "tp.tools.symbolgen.SymbolGenKt"
        inputs.dir(cacheDir).withPropertyName("cache")
        outputs.dir(generatedDir).withPropertyName("generated")
        // Plain strings: lambdas declared in a build script capture it, which the configuration cache can't store.
        args(cacheDir.get().asFile.absolutePath, generatedDir.get().asFile.absolutePath)
    }

sourceSets.main {
    java.srcDir(generatedDir)
}

tasks.named("compileJava") { dependsOn(generateSymbols) }
tasks.named("compileKotlin") { dependsOn(generateSymbols) }
