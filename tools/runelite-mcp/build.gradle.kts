plugins {
    java
}

group = "tp.tools"
version = "0.1.0"

// RuneLite stable release whose bootstrap matches OSRS rev 242 (docs/CLIENT.md, docs/DECISIONS.md D-007).
val runeliteVersion = "1.13.6"

repositories {
    maven {
        url = uri("https://repo.runelite.net")
        content { includeGroupByRegex("net\\.runelite.*") }
    }
    mavenCentral()
}

dependencies {
    // All of these are provided by the running RuneLite client at runtime; none is bundled into the jar.
    // Non-transitive on purpose: resolving the client's full dependency tree (jogl, jocl, ...) is slow and
    // flaky, and the plugin only needs the few libraries below.
    compileOnly("net.runelite:client:$runeliteVersion") { isTransitive = false }
    compileOnly("net.runelite:runelite-api:$runeliteVersion") { isTransitive = false }
    compileOnly("com.google.code.gson:gson:2.8.5")
    compileOnly("com.google.inject:guice:4.1.0")
    compileOnly("javax.inject:javax.inject:1")
    compileOnly("org.slf4j:slf4j-api:1.7.25")
    compileOnly("org.jetbrains:annotations:26.0.2") // @Nullable and friends, referenced by the RuneLite API
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 11
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

// One plain jar: plugin classes only. No dependencies are bundled (RuneLite supplies them).
tasks.jar {
    archiveBaseName = "runelite-mcp"
    archiveVersion = ""
}

// Smoke test of the HTTP / JSON-RPC layer with fake tools. Separate source set: never part of the plugin jar.
val smokeSources = sourceSets.create("smoke") {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath
}

val smoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Starts the MCP server with fake tools and exercises initialize/tools/list/tools/call over HTTP."
    classpath = smokeSources.runtimeClasspath
    mainClass = "tp.runelitemcp.SmokeMain"
}

tasks.check { dependsOn(smoke) }
