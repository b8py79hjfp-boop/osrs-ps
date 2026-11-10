//! Housekeeping (brief §3, owner): disk use of logs, dumps, backups and releases; clearing dumps older than a week;
//! read-only checks of the panel's TLS certificate expiry and the firewall's open inbound ports.

use std::path::Path;
use std::time::{Duration, SystemTime};

/// Brief §3: dumps older than 7 days may be cleared.
pub const DUMP_MAX_AGE: Duration = Duration::from_hours(7 * 24);

/// Total bytes under [dir] (0 when it doesn't exist).
pub fn dir_size(dir: &Path) -> u64 {
    let Ok(entries) = std::fs::read_dir(dir) else { return 0 };
    entries
        .filter_map(Result::ok)
        .map(|e| match e.metadata() {
            Ok(m) if m.is_dir() => dir_size(&e.path()),
            Ok(m) => m.len(),
            Err(_) => 0,
        })
        .sum()
}

pub fn human(bytes: u64) -> String {
    const UNITS: [&str; 5] = ["B", "KB", "MB", "GB", "TB"];
    let mut v = bytes;
    let mut unit = 0;
    let mut rem = 0;
    while v >= 1024 && unit < UNITS.len() - 1 {
        rem = v % 1024;
        v /= 1024;
        unit += 1;
    }
    if unit == 0 {
        format!("{v} B")
    } else {
        format!("{v}.{} {}", rem * 10 / 1024, UNITS[unit])
    }
}

#[derive(Debug, Clone)]
pub struct DumpFile {
    pub name: String,
    pub size: u64,
    pub modified: SystemTime,
}

/// The dump files (top level of [dir]), newest first.
pub fn dumps(dir: &Path) -> Vec<DumpFile> {
    let Ok(entries) = std::fs::read_dir(dir) else {
        return Vec::new();
    };
    let mut out: Vec<DumpFile> = entries
        .filter_map(Result::ok)
        .filter_map(|e| {
            let m = e.metadata().ok()?;
            m.is_file().then(|| DumpFile {
                name: e.file_name().to_string_lossy().into_owned(),
                size: m.len(),
                modified: m.modified().unwrap_or(SystemTime::UNIX_EPOCH),
            })
        })
        .collect();
    out.sort_by_key(|d| std::cmp::Reverse(d.modified));
    out
}

/// A dump's name as the download link gives it: a plain file name, nothing that walks out of the directory.
pub fn safe_name(name: &str) -> bool {
    !name.is_empty()
        && name.len() <= 128
        && !name.starts_with('.')
        && name
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_' || b == b'.')
}

/// Deletes dumps older than [`DUMP_MAX_AGE`]; returns how many.
pub fn clear_old_dumps(dir: &Path) -> usize {
    let now = SystemTime::now();
    dumps(dir)
        .into_iter()
        .filter(|d| now.duration_since(d.modified).is_ok_and(|age| age > DUMP_MAX_AGE))
        .filter(|d| std::fs::remove_file(dir.join(&d.name)).is_ok())
        .count()
}

/// The newest expiry among Caddy's certificates for [domain] (Unix seconds), or why it couldn't be read.
pub fn tls_expiry(store: &Path, domain: &str) -> Result<i64, String> {
    fn walk(dir: &Path, domain: &str, best: &mut Option<i64>) {
        let Ok(entries) = std::fs::read_dir(dir) else { return };
        for e in entries.filter_map(Result::ok) {
            let path = e.path();
            if path.is_dir() {
                walk(&path, domain, best);
            } else if path.extension().is_some_and(|x| x == "crt") && path.to_string_lossy().contains(domain) {
                let Ok(pem) = std::fs::read(&path) else { continue };
                let Ok((_, p)) = x509_parser::pem::parse_x509_pem(&pem) else {
                    continue;
                };
                let Ok(cert) = p.parse_x509() else { continue };
                let t = cert.validity().not_after.timestamp();
                *best = Some(best.map_or(t, |b| b.max(t)));
            }
        }
    }
    let mut best = None;
    walk(store, domain, &mut best);
    best.ok_or_else(|| format!("no certificate for {domain} under {}", store.display()))
}

/// Inbound, enabled `Allow` firewall rules with their local ports (Windows; read from the firewall policy in the
/// registry, so no shell is needed). Each entry: (rule name, protocol, ports).
#[cfg(windows)]
pub fn firewall_open_ports() -> Result<Vec<(String, String, String)>, String> {
    use winreg::RegKey;
    use winreg::enums::HKEY_LOCAL_MACHINE;
    let key = RegKey::predef(HKEY_LOCAL_MACHINE)
        .open_subkey(r"SYSTEM\CurrentControlSet\Services\SharedAccess\Parameters\FirewallPolicy\FirewallRules")
        .map_err(|e| format!("can't read the firewall rules: {e}"))?;
    let mut out = Vec::new();
    for (_, value) in key.enum_values().filter_map(Result::ok) {
        let text = value.to_string();
        let fields: Vec<(&str, &str)> = text.split('|').filter_map(|f| f.split_once('=')).collect();
        let get = |k: &str| {
            fields
                .iter()
                .filter(|(key, _)| *key == k)
                .map(|(_, v)| *v)
                .collect::<Vec<_>>()
        };
        if get("Action") != ["Allow"] || get("Active") != ["TRUE"] || get("Dir") != ["In"] {
            continue;
        }
        let ports = get("LPort");
        if ports.is_empty() {
            continue;
        }
        let protocol = match get("Protocol").first().copied() {
            Some("6") => "TCP",
            Some("17") => "UDP",
            _ => "other",
        };
        let name = get("Name").first().copied().unwrap_or("(unnamed)").to_owned();
        out.push((name, protocol.to_owned(), ports.join(", ")));
    }
    out.sort();
    Ok(out)
}

#[cfg(not(windows))]
pub fn firewall_open_ports() -> Result<Vec<(String, String, String)>, String> {
    Err("the firewall check runs on Windows only".into())
}
