//! The Lotus control panel (panel/docs/brief.md, plan panel/docs/plan.md). `main.rs` is the command line; this
//! library is everything else, so the brief §10 tests drive the real router with a fake hub.

pub mod actions;
pub mod auth;
pub mod certs;
pub mod clock;
pub mod config;
pub mod control;
pub mod control_web;
pub mod db;
pub mod housekeeping;
pub mod hub;
pub mod jobs;
pub mod logs;
pub mod metrics;
pub mod net;
pub mod pages;
pub mod rank;
pub mod schedule;
pub mod secrets;
pub mod store;
pub mod units;
pub mod web;
