import com.google.protobuf.gradle.id

plugins {
    id("tp.kotlin-library")
    alias(libs.plugins.protobuf)
}

// Hub API: Kotlin interfaces (tp.services) + schema-first gRPC (src/main/proto) with grpc-kotlin coroutine stubs.
dependencies {
    api(project(":util"))
    api(libs.kotlinx.coroutines.core)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.grpc.kotlin.stub)
    api(libs.protobuf.kotlin)
    implementation(libs.grpc.netty.shaded)
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protobufJava.get()}" }
    plugins {
        id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpcJava.get()}" }
        id("grpckt") { artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.grpcKotlin.get()}:jdk8@jar" }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
                id("grpckt")
            }
            it.builtins { id("kotlin") }
        }
    }
}

// Generated code isn't ours to lint.
tasks.matching { it.name.startsWith("detekt") }.configureEach { (this as? SourceTask)?.exclude("**/generated/**") }
