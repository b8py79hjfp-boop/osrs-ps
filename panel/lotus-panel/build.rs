//! Compiles the hub's gRPC schema (services/src/main/proto/hub.proto) into the panel's client (panel brief §1).

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let proto = "../../services/src/main/proto/hub.proto";
    println!("cargo:rerun-if-changed={proto}");
    // A pinned protoc binary from crates.io, so no system protoc is needed (Windows included).
    let mut config = prost_build::Config::new();
    config.protoc_executable(protoc_bin_vendored::protoc_bin_path()?);
    tonic_prost_build::configure().build_server(false).compile_with_config(
        config,
        &[proto],
        &["../../services/src/main/proto"],
    )?;
    Ok(())
}
