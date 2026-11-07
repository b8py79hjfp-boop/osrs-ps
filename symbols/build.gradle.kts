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

val packedDir = rootProject.layout.projectDirectory.dir(revision.map { ".data/cache/$it-packed" })
val cachePack = configurations.dependencyScope("cachePack")
val cachePackClasspath =
    configurations.resolvable("cachePackClasspath") {
        extendsFrom(cachePack.get())
    }

dependencies {
    api(project(":util"))
    symbolGen(project(":tools:symbol-gen"))
    cachePack(project(":tools:cache-pack"))
}

// Custom interfaces, clientscripts and configs (content/*/src/main/resources/pack) packed into a copy of the cache that
// the server serves (DECISIONS D-071); their names reach tp.symbols through custom-symbols.tsv (D-074).
val packCache =
    tasks.register<JavaExec>("packCache") {
        description = "Packs content/*/src/main/resources/pack into .data/cache/<rev>-packed (tools/cache-pack)."
        classpath(cachePackClasspath)
        mainClass = "tp.tools.cachepack.CachePackKt"
        workingDir = rootProject.projectDir
        inputs.dir(cacheDir).withPropertyName("cache")
        inputs
            .files(rootProject.fileTree("content") { include("*/src/main/resources/pack/**") })
            .withPropertyName("packs")
        inputs.dir(rootProject.layout.projectDirectory.dir("tools/cache-pack/src/main/resources")).withPropertyName("symbols")
        outputs.dir(packedDir).withPropertyName("packed")
        args("pack")
    }

val generateSymbols =
    tasks.register<JavaExec>("generateSymbols") {
        description = "Generates tp.symbols.* from cache archive 24 (run ./gradlew setup first)."
        classpath(symbolGenClasspath)
        mainClass = "tp.tools.symbolgen.SymbolGenKt"
        dependsOn(packCache)
        inputs.dir(cacheDir).withPropertyName("cache")
        inputs.file(packedDir.map { it.file("custom-symbols.tsv") }).withPropertyName("custom")
        outputs.dir(generatedDir).withPropertyName("generated")
        // Plain strings: lambdas declared in a build script capture it, which the configuration cache can't store.
        args(
            cacheDir.get().asFile.absolutePath,
            generatedDir.get().asFile.absolutePath,
            packedDir
                .get()
                .file("custom-symbols.tsv")
                .asFile.absolutePath,
        )
    }

sourceSets.main {
    java.srcDir(generatedDir)
}

tasks.named("compileJava") { dependsOn(generateSymbols) }
tasks.named("compileKotlin") { dependsOn(generateSymbols) }
