//! The hub's panel API over mutual TLS (plan §3). The generated client is `pb::panel_client::PanelClient`.

use std::path::Path;

use tonic::transport::{Certificate, Channel, ClientTlsConfig, Identity};

#[allow(clippy::all, clippy::pedantic, missing_docs, unreachable_pub)]
pub mod pb {
    tonic::include_proto!("tp.hub.v1");
}

pub type Client = pb::panel_client::PanelClient<Channel>;

/// A lazily connecting channel: the panel starts (and shows the hub as down) even while the hub is stopped.
pub fn connect(endpoint: &str, ca: &Path, cert: &Path, key_pem: &str) -> anyhow::Result<Client> {
    let tls = ClientTlsConfig::new()
        .ca_certificate(Certificate::from_pem(std::fs::read(ca)?))
        .identity(Identity::from_pem(std::fs::read(cert)?, key_pem.as_bytes()));
    let channel = Channel::from_shared(endpoint.to_owned())?
        .tls_config(tls)?
        .connect_timeout(std::time::Duration::from_secs(3))
        .timeout(std::time::Duration::from_secs(10))
        .connect_lazy();
    Ok(pb::panel_client::PanelClient::new(channel))
}

/// Whether the hub answers: `GetRank` of account 0, which never exists and changes nothing.
pub async fn healthy(client: &Client) -> bool {
    client.clone().get_rank(pb::AccountRef { account_id: 0 }).await.is_ok()
}
