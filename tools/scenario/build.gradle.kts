plugins {
    id("tp.kotlin-library")
}

// Phase 3: golden-scenario runner (brief §9 test 3) drives the real engine headlessly.
dependencies {
    implementation(project(":engine"))
}
