plugins {
    id("tp.kotlin-app")
}

// Release-client game patcher (brief §4.2): rewrites constants in RuneLite's injected game client by value.
application {
    mainClass = "tp.client.gamepatch.GamePatchKt"
}

dependencies {
    implementation(libs.asm)
    implementation(libs.kotlinx.serialization.json)
}
