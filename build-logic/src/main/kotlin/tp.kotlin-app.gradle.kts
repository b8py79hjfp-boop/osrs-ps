plugins {
    id("tp.kotlin-base")
    application
}

tasks.named<JavaExec>("run") {
    // Run from the repo root so relative paths such as .data/ resolve the same way as in production.
    workingDir = rootDir
    standardInput = System.`in`
}
