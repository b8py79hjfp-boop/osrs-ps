plugins {
    id("tp.kotlin-vendored")
    antlr
}

description = "Neptune runescript-parser (MIT), vendored. See ../NOTICE.md."

val antlrPackage = "me.filby.neptune.runescript.antlr"

dependencies {
    antlr(libs.antlr.tool)
    api(libs.antlr.runtime)
    implementation(libs.guava)
    implementation(libs.inline.logger)
}

// The Gradle antlr plugin adds the whole ANTLR tool to the runtime classpath; keep it off (gradle/gradle#820).
configurations {
    api {
        setExtendsFrom(extendsFrom.filterNot { it == antlr.get() })
    }
}

tasks.generateGrammarSource {
    // The generated classes live in a package, so the output directory must mirror it.
    outputDirectory = layout.buildDirectory.dir("generated-src/antlr/main/${antlrPackage.replace('.', '/')}").get().asFile
    arguments = arguments + listOf("-no-listener", "-visitor", "-package", antlrPackage)
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}
