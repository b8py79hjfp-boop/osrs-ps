pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "tickperfect"

include(
    ":util",
    ":routefinder",
    ":cache",
    ":symbols",
    ":protocol-api",
    ":protocol:osrs-242",
    ":protocol:osrs-242:rsprot:api",
    ":protocol:osrs-242:rsprot:common",
    ":protocol:osrs-242:rsprot:desktop",
    ":protocol:osrs-242:rsprot:internal",
    ":protocol:osrs-242:rsprot:model",
    ":protocol:osrs-242:rsprot:shared",
    ":net",
    ":engine",
    ":plugin-api",
    ":content:gameframe",
    ":content:dev",
    ":content:combat",
    ":content:pvp",
    ":content:world",
    ":content:shops",
    ":content:sailing",
    ":content:quests",
    ":content:magic",
    ":content:drops",
    ":content:collectionlog",
    ":content:diaries",
    ":content:skills",
    ":content:raids-core",
    ":content:raids-tfa",
    ":content:raids-tob",
    ":content:raids-cox",
    ":services",
    ":server",
    ":hub",
    ":tools:cache-dump",
    ":tools:cache-pack",
    ":tools:cache-pack:neptune:runescript-parser",
    ":tools:cache-pack:neptune:runescript-compiler",
    ":tools:cache-pack:neptune:clientscript-compiler",
    ":tools:scenario",
    ":tools:botswarm",
    ":tools:replay",
    ":tools:wiki-import",
    ":tools:symbol-gen",
    ":tools:ksp-registry",
    ":client:gamepatch",
    ":client:launcher",
)
// client/runelite (the RuneLite fork) is a patch series built by its own RuneLite Gradle build, not included here.
