//! `lotus-panel certs`: the private PKI for the panel ↔ hub link (panel brief §8, plan §3). One CA signs the hub's
//! server certificate (127.0.0.1 / localhost) and the panel's client certificate; the hub accepts only clients signed by
//! this CA on its panel listener. Keys are ECDSA P-256 in PKCS#8 PEM, which both rustls and the hub's Netty read.

use std::path::Path;

use rcgen::{
    BasicConstraints, CertificateParams, DistinguishedName, DnType, ExtendedKeyUsagePurpose, IsCa, Issuer, KeyPair,
    KeyUsagePurpose,
};
use time::{Duration, OffsetDateTime};

/// The CA lives ten years; the leaf certificates two (re-run with `--force` to renew; our choice).
const CA_DAYS: i64 = 3650;
const LEAF_DAYS: i64 = 730;

pub const FILES: [&str; 6] = [
    "ca.pem",
    "ca.key.pem",
    "hub.pem",
    "hub.key.pem",
    "panel.pem",
    "panel.key.pem",
];

fn name(cn: &str) -> DistinguishedName {
    let mut dn = DistinguishedName::new();
    dn.push(DnType::OrganizationName, "Lotus");
    dn.push(DnType::CommonName, cn);
    dn
}

fn validity(params: &mut CertificateParams, days: i64) {
    let now = OffsetDateTime::now_utc();
    params.not_before = now - Duration::hours(1);
    params.not_after = now + Duration::days(days);
}

/// Writes the six files into [dir]. Refuses to overwrite existing ones unless [force].
pub fn generate(dir: &Path, force: bool) -> anyhow::Result<()> {
    if !force && let Some(existing) = FILES.iter().map(|f| dir.join(f)).find(|p| p.exists()) {
        anyhow::bail!(
            "{} already exists; pass --force to replace every certificate",
            existing.display()
        );
    }
    std::fs::create_dir_all(dir)?;

    let mut ca = CertificateParams::default();
    ca.distinguished_name = name("Lotus panel CA");
    ca.is_ca = IsCa::Ca(BasicConstraints::Constrained(0));
    ca.key_usages = vec![KeyUsagePurpose::KeyCertSign, KeyUsagePurpose::CrlSign];
    validity(&mut ca, CA_DAYS);
    let ca_key = KeyPair::generate()?;
    let ca_cert = ca.self_signed(&ca_key)?;
    let issuer = Issuer::new(ca, ca_key);

    let mut hub = CertificateParams::new(vec!["localhost".to_owned(), "127.0.0.1".to_owned()])?;
    hub.distinguished_name = name("lotus-hub");
    hub.key_usages = vec![KeyUsagePurpose::DigitalSignature];
    hub.extended_key_usages = vec![ExtendedKeyUsagePurpose::ServerAuth];
    validity(&mut hub, LEAF_DAYS);
    let hub_key = KeyPair::generate()?;
    let hub_cert = hub.signed_by(&hub_key, &issuer)?;

    let mut panel = CertificateParams::default();
    panel.distinguished_name = name("lotus-panel");
    panel.key_usages = vec![KeyUsagePurpose::DigitalSignature];
    panel.extended_key_usages = vec![ExtendedKeyUsagePurpose::ClientAuth];
    validity(&mut panel, LEAF_DAYS);
    let panel_key = KeyPair::generate()?;
    let panel_cert = panel.signed_by(&panel_key, &issuer)?;

    let files = [
        ("ca.pem", ca_cert.pem()),
        ("ca.key.pem", issuer.key().serialize_pem()),
        ("hub.pem", hub_cert.pem()),
        ("hub.key.pem", hub_key.serialize_pem()),
        ("panel.pem", panel_cert.pem()),
        ("panel.key.pem", panel_key.serialize_pem()),
    ];
    for (file, pem) in files {
        std::fs::write(dir.join(file), pem)?;
    }
    Ok(())
}
