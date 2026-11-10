//! The Lotus control panel (panel/docs/brief.md, plan panel/docs/plan.md). `main.rs` is the command line; this
//! library is everything else, so the brief §10 tests drive the real router with a fake hub.

pub mod auth;
pub mod certs;
pub mod clock;
pub mod config;
pub mod db;
pub mod hub;
pub mod net;
pub mod rank;
pub mod secrets;
pub mod store;
pub mod web;
