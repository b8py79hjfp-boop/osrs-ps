//! Service logs (brief §4 Logs): each service's current log file under `<logs>\<unit>\<unit>_rCURRENT.log` (written by
//! the service wrapper, rotated at 10 MB), read from the end, filtered by level and text, and followed live.

use std::io::{Read, Seek, SeekFrom};
use std::path::{Path, PathBuf};

/// How much of the end of a log the page reads (the rotation size; our choice).
pub const TAIL_BYTES: u64 = 10 * 1024 * 1024;
/// Lines shown at most (our choice: a page stays fast on a phone).
pub const MAX_LINES: usize = 1000;

pub const LEVELS: [&str; 5] = ["ERROR", "WARN", "INFO", "DEBUG", "TRACE"];

pub fn current(logs: &Path, unit: &str) -> PathBuf {
    logs.join(unit).join(format!("{unit}_rCURRENT.log"))
}

/// The last [`TAIL_BYTES`] of [path] as lines.
pub fn tail(path: &Path) -> std::io::Result<Vec<String>> {
    let mut f = std::fs::File::open(path)?;
    let len = f.metadata()?.len();
    let start = len.saturating_sub(TAIL_BYTES);
    f.seek(SeekFrom::Start(start))?;
    let mut buf = Vec::new();
    f.read_to_end(&mut buf)?;
    let text = String::from_utf8_lossy(&buf);
    let mut lines: Vec<String> = text.lines().map(str::to_owned).collect();
    if start > 0 && !lines.is_empty() {
        lines.remove(0); // a partial first line
    }
    Ok(lines)
}

/// A line's level, as the game's and the panel's logs write it (`… INFO …`, `"level":"WARN"`).
pub fn level_of(line: &str) -> Option<&'static str> {
    LEVELS
        .iter()
        .copied()
        .find(|l| line.contains(&format!(" {l} ")) || line.contains(&format!("\"level\":\"{l}\"")))
}

/// [level] and anything more severe ([`LEVELS`] order); `text` matches case-insensitively; `since` is `HH:MM` (the
/// game's lines start with the time of day).
pub fn filter(lines: &[String], level: Option<&str>, text: &str, since: &str) -> Vec<String> {
    let max = level.and_then(|l| LEVELS.iter().position(|x| *x == l));
    let text = text.to_lowercase();
    let mut out: Vec<String> = lines
        .iter()
        .filter(|l| match (max, level_of(l)) {
            (Some(m), Some(lv)) => LEVELS.iter().position(|x| *x == lv).is_some_and(|p| p <= m),
            (Some(_), None) => false,
            (None, _) => true,
        })
        .filter(|l| text.is_empty() || l.to_lowercase().contains(&text))
        .filter(|l| since.is_empty() || l.get(..5).is_some_and(|hhmm| hhmm >= since))
        .cloned()
        .collect();
    let skip = out.len().saturating_sub(MAX_LINES);
    out.drain(..skip);
    out
}
