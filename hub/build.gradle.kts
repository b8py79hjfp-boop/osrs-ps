plugins {
    id("tp.kotlin-library")
}

// Phase 5: hub main (login authority, social, GE, hiscores, world directory) behind gRPC.
dependencies {
    implementation(project(":services"))
}
