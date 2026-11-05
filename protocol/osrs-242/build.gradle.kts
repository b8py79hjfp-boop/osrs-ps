plugins {
    id("tp.kotlin-library")
}

// Phase 2: vendored RSProt osrs-242 (MIT) behind our protocol-api. See docs/DECISIONS.md D-005.
dependencies {
    api(project(":protocol-api"))
}
