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
    ":content:combat",
    ":content:pvp",
    ":services",
    ":server",
    ":hub",
    ":tools:cache-dump",
    ":tools:scenario",
    ":tools:botswarm",
    ":tools:replay",
    ":tools:wiki-import",
    ":tools:symbol-gen",
)
// client/* (release client) is added in Phase 6; it is a separate Java/RuneLite build.
