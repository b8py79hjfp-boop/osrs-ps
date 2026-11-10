//! Scheduled restarts (brief §3): daily or weekly, with the usual countdown, set on the server control page. A
//! schedule is skipped while another control job (such as a deploy) runs, and tried again at the next check.

use serde::{Deserialize, Serialize};
use time::{OffsetDateTime, Weekday};

pub const KEY: &str = "restart_schedule";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct RestartSchedule {
    pub enabled: bool,
    /// `daily`, or a weekday (`monday` … `sunday`).
    pub every: String,
    /// `HH:MM`, UTC.
    pub at: String,
    pub seconds: i32,
    pub message: String,
    /// The owner who set it: the countdown and lock run as them (the hub re-checks their rank each time).
    pub actor_id: i64,
    pub actor_name: String,
    /// Unix time of the last run, so one slot runs once.
    pub last_run: i64,
}

fn weekday(name: &str) -> Option<Weekday> {
    Some(match name {
        "monday" => Weekday::Monday,
        "tuesday" => Weekday::Tuesday,
        "wednesday" => Weekday::Wednesday,
        "thursday" => Weekday::Thursday,
        "friday" => Weekday::Friday,
        "saturday" => Weekday::Saturday,
        "sunday" => Weekday::Sunday,
        _ => return None,
    })
}

pub fn parse_at(at: &str) -> Option<(u8, u8)> {
    let (h, m) = at.split_once(':')?;
    let (h, m) = (h.parse::<u8>().ok()?, m.parse::<u8>().ok()?);
    (h < 24 && m < 60).then_some((h, m))
}

impl RestartSchedule {
    pub fn valid(&self) -> bool {
        parse_at(&self.at).is_some() && (self.every == "daily" || weekday(&self.every).is_some())
    }

    /// The most recent slot at or before [now], if this schedule has one today.
    fn slot(&self, now: OffsetDateTime) -> Option<OffsetDateTime> {
        let (h, m) = parse_at(&self.at)?;
        if self.every != "daily" && weekday(&self.every)? != now.weekday() {
            return None;
        }
        let slot = now.replace_time(time::Time::from_hms(h, m, 0).ok()?);
        (slot <= now).then_some(slot)
    }

    /// Whether a restart is due: a slot has passed within the last 15 minutes and hasn't run.
    pub fn due(&self, now: OffsetDateTime) -> bool {
        self.enabled
            && self
                .slot(now)
                .is_some_and(|slot| now - slot <= time::Duration::minutes(15) && self.last_run < slot.unix_timestamp())
    }
}
