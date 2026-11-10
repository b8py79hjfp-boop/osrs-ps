//! Brief §10 test 3 (`control`): against a fake service manager and a fake hub, start brings units up in order, a
//! graceful stop runs the countdown and saves before stopping, a cancel stops nothing, and a crash loop stops retrying.

#![allow(clippy::unwrap_used, clippy::expect_used)]

mod common;

use common::{Harness, actor, finished};
use lotus_panel::control::Scope;
use lotus_panel::jobs::JobState;
use lotus_panel::units::ServiceState;
use time::Duration;

fn calls(h: &Harness) -> Vec<String> {
    h.hub.calls.lock().unwrap().clone()
}

#[tokio::test(start_paused = true)]
async fn start_brings_units_up_in_order() {
    let h = Harness::new();
    let job = h.control.start(Scope::All, "owner1").await.unwrap();
    assert_eq!(finished(&h.jobs, job).await, JobState::Succeeded);
    assert_eq!(
        calls(&h),
        [
            "unit start lotus-postgresql",
            "unit start lotus-hub",
            "unit start lotus"
        ]
    );
}

#[tokio::test(start_paused = true)]
async fn graceful_stop_counts_down_then_stops_in_reverse_order() {
    let h = Harness::new();
    let job = h.control.start(Scope::All, "owner1").await.unwrap();
    finished(&h.jobs, job).await;
    h.hub.calls.lock().unwrap().clear();

    let started = tokio::time::Instant::now();
    let job = h
        .control
        .stop(Scope::All, 120, "Back soon".into(), false, actor(), "owner1")
        .await
        .unwrap();
    assert_eq!(finished(&h.jobs, job).await, JobState::Succeeded);
    assert!(
        started.elapsed() >= std::time::Duration::from_secs(120),
        "the whole countdown ran"
    );
    // Lock, timer, then the world (its wrapper's Ctrl+C saves everyone), then the hub; the database stays up.
    assert_eq!(
        calls(&h),
        [
            "hub lock 0",
            "hub update 120 Back soon",
            "unit stop lotus",
            "unit stop lotus-hub"
        ]
    );
    assert_eq!(
        h.units.states.lock().unwrap()["lotus-postgresql"],
        ServiceState::Running
    );
}

#[tokio::test(start_paused = true)]
async fn restart_unlocks_after_health() {
    let h = Harness::new();
    let job = h.control.start(Scope::All, "owner1").await.unwrap();
    finished(&h.jobs, job).await;
    h.hub.calls.lock().unwrap().clear();
    let job = h
        .control
        .stop(Scope::World(1), 10, String::new(), true, actor(), "owner1")
        .await
        .unwrap();
    assert_eq!(finished(&h.jobs, job).await, JobState::Succeeded);
    assert_eq!(
        calls(&h),
        [
            "hub lock 1",
            "hub update 10 ",
            "unit stop lotus",
            "unit start lotus",
            "hub unlock 1"
        ]
    );
}

#[tokio::test(start_paused = true)]
async fn cancel_stops_nothing() {
    let h = Harness::new();
    let job = h.control.start(Scope::All, "owner1").await.unwrap();
    finished(&h.jobs, job).await;
    h.hub.calls.lock().unwrap().clear();
    let job = h
        .control
        .stop(Scope::All, 300, String::new(), false, actor(), "owner1")
        .await
        .unwrap();
    tokio::time::sleep(std::time::Duration::from_secs(30)).await;
    assert!(h.control.cancel().await);
    assert_eq!(finished(&h.jobs, job).await, JobState::Cancelled);
    assert_eq!(
        calls(&h),
        ["hub lock 0", "hub update 300 ", "hub cancel", "hub unlock 0"]
    );
    assert_eq!(h.units.states.lock().unwrap()["lotus"], ServiceState::Running);
}

#[tokio::test(start_paused = true)]
async fn one_job_at_a_time() {
    let h = Harness::new();
    let _job = h
        .control
        .stop(Scope::All, 300, String::new(), false, actor(), "owner1")
        .await
        .unwrap();
    assert!(
        h.control.start(Scope::All, "owner2").await.is_err(),
        "a second job is refused while one runs"
    );
}

#[tokio::test(start_paused = true)]
async fn crash_loop_stops_retrying() {
    let h = Harness::new();
    let job = h.control.start(Scope::All, "owner1").await.unwrap();
    finished(&h.jobs, job).await;
    h.hub.calls.lock().unwrap().clear();

    h.control.watch_once().await; // healthy baseline
    for crash in 1..=3 {
        h.probe.crashed.lock().unwrap().push("lotus".into());
        h.control.watch_once().await;
        h.probe.crashed.lock().unwrap().clear();
        h.clock.advance(Duration::minutes(2));
        h.control.watch_once().await; // the wrapper brought it back
        if crash < 3 {
            assert!(calls(&h).is_empty(), "crash {crash}: still retrying");
        }
    }
    assert_eq!(calls(&h), ["unit stop lotus"], "the third crash in 10 minutes stops it");
    assert_eq!(h.alerts.0.lock().unwrap().as_slice(), ["Crash loop: lotus"]);
    let views = h.control.views().await;
    assert_eq!(views.iter().find(|v| v.1 == "lotus").unwrap().2.label(), "failed");

    // It stays down: further passes don't touch it.
    h.control.watch_once().await;
    assert_eq!(calls(&h), ["unit stop lotus"]);
}
