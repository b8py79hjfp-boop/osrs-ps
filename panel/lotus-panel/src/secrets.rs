//! Secrets as files in `[paths] secrets` (panel/docs/plan.md §1: the Windows stand-in for systemd credentials). The
//! install script restricts that directory to the panel's service account and Administrators; values never appear in
//! the config, the command line or the logs.

use std::io::Write;
use std::path::{Path, PathBuf};

#[derive(Debug, thiserror::Error)]
pub enum SecretError {
    #[error("invalid secret name {0:?}: use lower-case letters, digits and '-'")]
    Name(String),
    #[error("secret {name} is missing ({path}); set it with `lotus-panel secret set {name}`")]
    Missing { name: String, path: PathBuf },
    #[error("secret {name}: {source}")]
    Io { name: String, source: std::io::Error },
    #[error("secret {0} is empty")]
    Empty(String),
}

fn path_of(dir: &Path, name: &str) -> Result<PathBuf, SecretError> {
    let valid = !name.is_empty()
        && name.len() <= 64
        && name
            .bytes()
            .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'-');
    if !valid {
        return Err(SecretError::Name(name.to_owned()));
    }
    Ok(dir.join(name))
}

pub fn read(dir: &Path, name: &str) -> Result<String, SecretError> {
    let path = path_of(dir, name)?;
    let value = std::fs::read_to_string(&path).map_err(|source| {
        if source.kind() == std::io::ErrorKind::NotFound {
            SecretError::Missing {
                name: name.to_owned(),
                path: path.clone(),
            }
        } else {
            SecretError::Io {
                name: name.to_owned(),
                source,
            }
        }
    })?;
    let value = value.trim_end_matches(['\r', '\n']).to_owned();
    if value.is_empty() {
        return Err(SecretError::Empty(name.to_owned()));
    }
    Ok(value)
}

/// Writes [value] to a temporary file in [dir] and renames it over the secret, so a reader never sees half a value.
pub fn write(dir: &Path, name: &str, value: &str) -> Result<(), SecretError> {
    let path = path_of(dir, name)?;
    if value.trim().is_empty() {
        return Err(SecretError::Empty(name.to_owned()));
    }
    let io = |source| SecretError::Io {
        name: name.to_owned(),
        source,
    };
    std::fs::create_dir_all(dir).map_err(io)?;
    let tmp = dir.join(format!(".{name}.tmp"));
    let mut file = std::fs::File::create(&tmp).map_err(io)?;
    file.write_all(value.as_bytes()).map_err(io)?;
    file.sync_all().map_err(io)?;
    drop(file);
    std::fs::rename(&tmp, &path).map_err(io)
}
