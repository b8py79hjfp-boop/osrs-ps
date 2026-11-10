//! Brief §10 test 2 (`auth`), without a second factor (plan §1a): sign-in and refusals, lockout, CSRF and Origin
//! checks, idle and absolute expiry, and a demotion taking effect at the next rank re-check.

#![allow(clippy::unwrap_used, clippy::expect_used)]

mod common;

use axum::http::StatusCode;
use common::{Harness, ORIGIN, PASSWORD, location, set_cookie};
use lotus_panel::rank::Rank;
use time::Duration;

#[tokio::test]
async fn sign_in_and_refusals() {
    let h = Harness::new();
    h.hub.add("mod1", Rank::Moderator);
    h.hub.add("player1", Rank::Player);

    let r = h.try_sign_in("mod1", "wrong").await;
    assert_eq!(r.status(), StatusCode::UNAUTHORIZED);
    assert!(set_cookie(&r, "__Host-lotus").is_none());

    let r = h.try_sign_in("player1", PASSWORD).await;
    assert_eq!(r.status(), StatusCode::UNAUTHORIZED, "players can't sign in");
    assert!(set_cookie(&r, "__Host-lotus").is_none());

    // Sign-in form token missing, or a foreign origin: refused before the password is even checked.
    let (pre, token) = h.login_form().await;
    let form = format!("username=mod1&password={PASSWORD}&csrf={token}");
    assert_eq!(
        h.post("/login", "", &form, Some(ORIGIN)).await.status(),
        StatusCode::FORBIDDEN
    );
    let cookies = format!("__Host-lotus-login={pre}");
    assert_eq!(
        h.post("/login", &cookies, &form, Some("https://evil.test"))
            .await
            .status(),
        StatusCode::FORBIDDEN
    );

    let (session, _) = h.sign_in("mod1").await;
    assert_eq!(h.get("/", Some(&session)).await.status(), StatusCode::OK);
}

#[tokio::test]
async fn lockout_after_repeated_failures() {
    let h = Harness::new();
    h.hub.add("mod1", Rank::Moderator);
    for _ in 0..lotus_panel::auth::ACCOUNT_FAILURES {
        assert_eq!(h.try_sign_in("mod1", "wrong").await.status(), StatusCode::UNAUTHORIZED);
    }
    assert_eq!(
        h.try_sign_in("mod1", PASSWORD).await.status(),
        StatusCode::TOO_MANY_REQUESTS,
        "locked even with the right password"
    );
    h.clock.advance(lotus_panel::auth::LOCK_FOR + Duration::seconds(1));
    assert_eq!(
        h.try_sign_in("mod1", PASSWORD).await.status(),
        StatusCode::SEE_OTHER,
        "unlocked after 15 minutes"
    );
}

#[tokio::test]
async fn csrf_and_origin() {
    let h = Harness::new();
    h.hub.add("mod1", Rank::Moderator);
    let (session, csrf) = h.sign_in("mod1").await;
    let cookies = format!("__Host-lotus={session}");
    assert_eq!(
        h.post("/logout", &cookies, "", None).await.status(),
        StatusCode::FORBIDDEN,
        "no token"
    );
    assert_eq!(
        h.post("/logout", &cookies, "csrf=forged", None).await.status(),
        StatusCode::FORBIDDEN,
        "wrong token"
    );
    let ok = format!("csrf={csrf}");
    assert_eq!(
        h.post("/logout", &cookies, &ok, Some("https://evil.test"))
            .await
            .status(),
        StatusCode::FORBIDDEN,
        "foreign origin"
    );
    assert_eq!(
        h.get("/", Some(&session)).await.status(),
        StatusCode::OK,
        "still signed in"
    );
    let r = h.post("/logout", &cookies, &ok, Some(ORIGIN)).await;
    assert_eq!(r.status(), StatusCode::SEE_OTHER);
    assert_eq!(location(&r).as_deref(), Some("/login"));
    assert_eq!(
        location(&h.get("/", Some(&session)).await).as_deref(),
        Some("/login"),
        "signed out"
    );
}

#[tokio::test]
async fn idle_expiry() {
    let h = Harness::new();
    h.hub.add("mod1", Rank::Moderator);
    let (session, _) = h.sign_in("mod1").await;
    h.clock.advance(Duration::minutes(29));
    assert_eq!(
        h.get("/", Some(&session)).await.status(),
        StatusCode::OK,
        "29 minutes idle is fine"
    );
    h.clock.advance(Duration::minutes(31));
    assert_eq!(
        location(&h.get("/", Some(&session)).await).as_deref(),
        Some("/login"),
        "31 minutes idle ends it"
    );
}

#[tokio::test]
async fn absolute_expiry() {
    let h = Harness::new();
    h.hub.add("mod1", Rank::Moderator);
    let (session, _) = h.sign_in("mod1").await;
    let mut elapsed = Duration::ZERO;
    while elapsed + Duration::minutes(25) <= Duration::hours(12) {
        h.clock.advance(Duration::minutes(25));
        elapsed += Duration::minutes(25);
        assert_eq!(
            h.get("/", Some(&session)).await.status(),
            StatusCode::OK,
            "active at {elapsed}"
        );
    }
    h.clock.advance(Duration::minutes(25));
    assert_eq!(
        location(&h.get("/", Some(&session)).await).as_deref(),
        Some("/login"),
        "12 hours is the limit"
    );
}

#[tokio::test]
async fn demotion_at_next_recheck() {
    let h = Harness::new();
    h.hub.add("owner1", Rank::Owner);
    let (session, _) = h.sign_in("owner1").await;
    assert_eq!(h.get("/staff/sessions", Some(&session)).await.status(), StatusCode::OK);

    h.hub.set_rank("owner1", Rank::Moderator);
    h.clock.advance(Duration::seconds(30));
    assert_eq!(
        h.get("/staff/sessions", Some(&session)).await.status(),
        StatusCode::OK,
        "before the re-check"
    );
    h.clock.advance(Duration::seconds(31));
    assert_eq!(
        h.get("/staff/sessions", Some(&session)).await.status(),
        StatusCode::NOT_FOUND,
        "owner page gone"
    );
    assert_eq!(
        h.get("/", Some(&session)).await.status(),
        StatusCode::OK,
        "still a moderator"
    );

    h.hub.set_rank("owner1", Rank::Player);
    h.clock.advance(Duration::seconds(61));
    assert_eq!(
        location(&h.get("/", Some(&session)).await).as_deref(),
        Some("/login"),
        "no longer staff"
    );
}

#[tokio::test]
async fn owner_signs_others_out_with_password() {
    let h = Harness::new();
    h.hub.add("owner1", Rank::Owner);
    let mod_id = h.hub.add("mod1", Rank::Moderator);
    let (owner, csrf) = h.sign_in("owner1").await;
    let (moderator, _) = h.sign_in("mod1").await;
    let cookies = format!("__Host-lotus={owner}");

    let wrong = format!("csrf={csrf}&account_id={mod_id}&password=wrong");
    assert_eq!(
        h.post("/staff/sessions/revoke", &cookies, &wrong, Some(ORIGIN))
            .await
            .status(),
        StatusCode::FORBIDDEN
    );
    assert_eq!(
        h.get("/", Some(&moderator)).await.status(),
        StatusCode::OK,
        "nothing changed"
    );

    let right = format!("csrf={csrf}&account_id={mod_id}&password={PASSWORD}");
    assert_eq!(
        h.post("/staff/sessions/revoke", &cookies, &right, Some(ORIGIN))
            .await
            .status(),
        StatusCode::SEE_OTHER
    );
    assert_eq!(
        location(&h.get("/", Some(&moderator)).await).as_deref(),
        Some("/login"),
        "signed out everywhere"
    );
    assert!(
        h.hub
            .audits
            .lock()
            .unwrap()
            .iter()
            .any(|a| a.starts_with("panel revoke sessions") && a.ends_with(" ok"))
    );
}
