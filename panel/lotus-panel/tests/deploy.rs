//! Brief §10 test 4 (`deploy`): against the fake service manager and hub, a deploy runs every step in order; a failed
//! health check stops it and leaves the remaining worlds on the old version; rollback puts the links back.

#![allow(clippy::unwrap_used, clippy::expect_used)]

mod common;

use common::{Harness, actor, finished};
use lotus_panel::control::{Topology, WorldUnit};
use lotus_panel::jobs::{JobState, JobStore};

fn two_worlds() -> Topology {
    let world = |unit: &str, id: i32| WorldUnit {
        unit: unit.into(),
        world_id: id,
        game_port: 43594,
        health_url: String::new(),
    };
    Topology {
        database: "lotus-postgresql".into(),
        hub: "lotus-hub".into(),
        worlds: vec![world("lotus", 1), world("lotus-2", 2)],
        logs: std::env::temp_dir(),
    }
}

const UNITS: [&str; 3] = ["lotus-hub", "lotus", "lotus-2"];

/// Two releases on disk, everything running the old one, the services up.
fn setup() -> Harness {
    let h = Harness::with_topology(two_worlds());
    h.add_release("1.0", "2026-11-01T00:00:00Z", &["CREATE TABLE IF NOT EXISTS a (x int)"]);
    h.add_release(
        "1.1",
        "2026-11-10T00:00:00Z",
        &[
            "CREATE TABLE IF NOT EXISTS a (x int)",
            "ALTER TABLE a ADD COLUMN IF NOT EXISTS y int NOT NULL DEFAULT 0",
        ],
    );
    for u in UNITS {
        h.deployer.links.point(u, "1.0").unwrap();
    }
    for u in ["lotus-postgresql", "lotus-hub", "lotus", "lotus-2"] {
        h.units
            .states
            .lock()
            .unwrap()
            .insert(u.into(), lotus_panel::units::ServiceState::Running);
    }
    h
}

fn calls(h: &Harness) -> Vec<String> {
    h.hub.calls.lock().unwrap().clone()
}

#[tokio::test(start_paused = true)]
async fn deploy_runs_every_step_in_order() {
    let h = setup();
    let job = h
        .deployer
        .deploy("1.1", 60, "Updating".into(), actor(), "owner1")
        .await
        .unwrap();
    assert_eq!(finished(&h.jobs, job).await, JobState::Succeeded);
    assert_eq!(
        calls(&h),
        [
            // Backup first: the release adds a schema statement.
            "backup 1.1",
            "hub lock 0",
            "hub update 60 Updating",
            // Worlds stop in reverse order (each saves everyone), then the hub.
            "unit stop lotus-2",
            "unit stop lotus",
            "unit stop lotus-hub",
            // Hub first, health-checked, then each world.
            "unit start lotus-hub",
            "unit start lotus",
            "unit start lotus-2",
            "hub unlock 0",
        ]
    );
    for u in UNITS {
        assert_eq!(h.running(u).as_deref(), Some("1.1"), "{u}");
    }
    assert_eq!(lotus_panel::deploy::history(&h.releases)[0].version, "1.1");
    let log = h.jobs.get(job).await.unwrap().unwrap().log;
    assert!(log.contains("backup written: pre-deploy-1.1.dump"), "{log}");
}

#[tokio::test(start_paused = true)]
async fn failed_health_check_leaves_the_rest_on_the_old_version() {
    let h = setup();
    // World 1 doesn't come up on 1.1.
    h.probe.broken.lock().unwrap().push(("lotus".into(), "1.1".into()));
    let job = h
        .deployer
        .deploy("1.1", 10, String::new(), actor(), "owner1")
        .await
        .unwrap();
    assert_eq!(finished(&h.jobs, job).await, JobState::Failed);
    // The hub took the new release; world 1 went back to the old one and world 2 was never switched.
    assert_eq!(h.running("lotus-hub").as_deref(), Some("1.1"));
    assert_eq!(h.running("lotus").as_deref(), Some("1.0"));
    assert_eq!(h.running("lotus-2").as_deref(), Some("1.0"));
    let c = calls(&h);
    let tail: Vec<&str> = c
        .iter()
        .skip_while(|l| *l != "unit start lotus-hub")
        .map(String::as_str)
        .collect();
    assert_eq!(
        tail,
        [
            "unit start lotus-hub",
            "unit start lotus",
            "unit stop lotus",
            "unit start lotus",
            "unit start lotus-2",
            "hub unlock 0",
        ]
    );
    // Both worlds end up running on the old release; owners are alerted.
    assert!(h.alerts.0.lock().unwrap().iter().any(|t| t == "Deploy failed"));
    assert_eq!(lotus_panel::deploy::history(&h.releases).len(), 0);
}

#[tokio::test(start_paused = true)]
async fn rollback_restores_the_links() {
    let h = setup();
    let job = h
        .deployer
        .deploy("1.1", 5, String::new(), actor(), "owner1")
        .await
        .unwrap();
    assert_eq!(finished(&h.jobs, job).await, JobState::Succeeded);
    assert_eq!(lotus_panel::deploy::previous(&h.releases, "1.1"), None);
    // 1.0 was installed before the panel's first deploy; record it the way a first deploy would have.
    let back = h.deployer.plan("1.0").unwrap();
    assert!(back.schema.added.is_empty(), "going back adds nothing");
    assert!(
        back.rollback_blocked.is_none(),
        "an added column doesn't block a rollback"
    );
    let job = h
        .deployer
        .deploy("1.0", 5, String::new(), actor(), "owner1")
        .await
        .unwrap();
    assert_eq!(finished(&h.jobs, job).await, JobState::Succeeded);
    for u in UNITS {
        assert_eq!(h.running(u).as_deref(), Some("1.0"), "{u}");
    }
    // No backup on the way back: nothing new in the schema.
    assert_eq!(calls(&h).iter().filter(|l| l.starts_with("backup")).count(), 1);
    assert_eq!(
        lotus_panel::deploy::previous(&h.releases, "1.0").as_deref(),
        Some("1.1")
    );
}

#[tokio::test(start_paused = true)]
async fn a_release_that_drops_data_blocks_one_click_rollback() {
    let h = setup();
    h.add_release(
        "1.2",
        "2026-11-11T00:00:00Z",
        &[
            "CREATE TABLE IF NOT EXISTS a (x int)",
            "ALTER TABLE a DROP COLUMN IF EXISTS x",
        ],
    );
    let job = h
        .deployer
        .deploy("1.2", 5, String::new(), actor(), "owner1")
        .await
        .unwrap();
    assert_eq!(finished(&h.jobs, job).await, JobState::Succeeded);
    let back = h.deployer.plan("1.0").unwrap();
    assert!(back.rollback_blocked.is_some());
    assert!(
        h.deployer
            .deploy("1.0", 5, String::new(), actor(), "owner1")
            .await
            .is_err()
    );
    // A failed backup stops a deploy before anything is touched.
    *h.backup.fail.lock().unwrap() = true;
    h.add_release(
        "1.3",
        "2026-11-12T00:00:00Z",
        &[
            "CREATE TABLE IF NOT EXISTS a (x int)",
            "ALTER TABLE a DROP COLUMN IF EXISTS x",
            "CREATE TABLE IF NOT EXISTS b (x int)",
        ],
    );
    let before = calls(&h).len();
    let job = h
        .deployer
        .deploy("1.3", 5, String::new(), actor(), "owner1")
        .await
        .unwrap();
    assert_eq!(finished(&h.jobs, job).await, JobState::Failed);
    assert_eq!(calls(&h)[before..], ["backup 1.3".to_owned()]);
    assert_eq!(h.running("lotus-hub").as_deref(), Some("1.2"));
}
