//! Time, injectable so the session tests can move it (brief §10 test 2: idle and absolute expiry, the rank re-check).

use std::sync::Mutex;

use time::{Duration, OffsetDateTime};

pub trait Clock: Send + Sync {
    fn now(&self) -> OffsetDateTime;
}

pub struct SystemClock;

impl Clock for SystemClock {
    fn now(&self) -> OffsetDateTime {
        OffsetDateTime::now_utc()
    }
}

/// A clock that only moves when told to (tests).
pub struct ManualClock(Mutex<OffsetDateTime>);

impl ManualClock {
    pub fn new(start: OffsetDateTime) -> Self {
        Self(Mutex::new(start))
    }

    pub fn advance(&self, by: Duration) {
        let mut t = self.0.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
        *t += by;
    }
}

impl Clock for ManualClock {
    fn now(&self) -> OffsetDateTime {
        *self.0.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
    }
}
