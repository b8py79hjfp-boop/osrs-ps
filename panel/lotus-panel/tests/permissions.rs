//! Brief §10 test 1 (`permissions`): every route in the route table, tried logged out and as each staff rank, gives
//! the right result. The router is built from the same table, so a new route can't skip this test.

#![allow(clippy::unwrap_used, clippy::expect_used)]

mod common;

use axum::http::{Method, StatusCode};
use common::{Harness, location};
use lotus_panel::rank::Rank;
use lotus_panel::web::routes;

const VIEWERS: [Rank; 4] = [Rank::Moderator, Rank::Administrator, Rank::Owner, Rank::Developer];

#[tokio::test]
async fn every_route_for_every_rank() {
    let h = Harness::new();
    for rank in VIEWERS {
        h.hub.add(
            &format!("{}1", rank.label().to_lowercase().chars().take(8).collect::<String>()),
            rank,
        );
    }
    let mut checked = 0;
    for spec in routes() {
        // Logged out.
        let r = if spec.method == Method::GET {
            h.get(spec.sample, None).await
        } else {
            h.post(spec.sample, "", "", None).await
        };
        match (spec.min_rank, &spec.method) {
            (None, _) => assert!(
                ![StatusCode::UNAUTHORIZED, StatusCode::NOT_FOUND].contains(&r.status()),
                "{} {} is public but answered {}",
                spec.method,
                spec.path,
                r.status()
            ),
            (Some(_), &Method::GET) => {
                assert_eq!(
                    r.status(),
                    StatusCode::SEE_OTHER,
                    "{} {} logged out",
                    spec.method,
                    spec.path
                );
                assert_eq!(location(&r).as_deref(), Some("/login"));
            }
            (Some(_), _) => assert_eq!(
                r.status(),
                StatusCode::UNAUTHORIZED,
                "{} {} logged out",
                spec.method,
                spec.path
            ),
        }
        // Each rank, with a fresh session (a POST may sign that session out).
        for rank in VIEWERS {
            let name = format!("{}1", rank.label().to_lowercase().chars().take(8).collect::<String>());
            let (session, csrf) = h.sign_in(&name).await;
            let r = if spec.method == Method::GET {
                h.get(spec.sample, Some(&session)).await
            } else {
                h.post(
                    spec.sample,
                    &format!("__Host-lotus={session}"),
                    &format!("csrf={csrf}"),
                    None,
                )
                .await
            };
            let status = r.status();
            match spec.min_rank {
                // Public routes don't look at the session: no auth denial, whoever asks (POST /login still wants its
                // own form token, so 403 there is that check, not the guard).
                None => assert!(
                    ![StatusCode::UNAUTHORIZED, StatusCode::NOT_FOUND].contains(&status),
                    "{} {} as {rank} answered {status}",
                    spec.method,
                    spec.path
                ),
                Some(min) if rank < min => {
                    assert_eq!(
                        status,
                        StatusCode::NOT_FOUND,
                        "{} {} as {rank} must be hidden",
                        spec.method,
                        spec.path
                    );
                }
                Some(_) if spec.method == Method::GET => {
                    assert_eq!(status, StatusCode::OK, "{} {} as {rank}", spec.method, spec.path);
                }
                _ => assert!(
                    ![StatusCode::UNAUTHORIZED, StatusCode::FORBIDDEN, StatusCode::NOT_FOUND].contains(&status),
                    "{} {} as {rank} answered {status}",
                    spec.method,
                    spec.path
                ),
            }
            checked += 1;
        }
    }
    assert!(checked >= routes().len() * VIEWERS.len());
    // Anything outside the table doesn't exist.
    assert_eq!(h.get("/admin", None).await.status(), StatusCode::NOT_FOUND);
}
