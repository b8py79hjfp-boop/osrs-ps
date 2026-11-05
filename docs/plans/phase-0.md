# Phase 0 — Scaffold

## Scope

- Gradle 9.8.2 multi-module build, version catalog, convention plugins in `build-logic/`, configuration cache on.
- Every module from brief §5.1 except `client/*` (Phase 6, separate Java/RuneLite build). Empty modules are fine.
- `CLAUDE.md`, `docs/BRIEF.md`, `docs/PROGRESS.md`, `docs/DECISIONS.md`, this plan.
- `.claude/settings.json` (Opus 5.5, medium) and `.claude/agents/{scout,implementer,verifier}.md`.
- `./gradlew setup`: downloads OpenRS2 cache 2757 + `keys.json` into `.data/cache/242/`, records SHA-256s, verifies them.
- `./gradlew generateRsa`: 1024-bit keypair into `.data/rsa/`; prints the modulus.
- Minimal JS5 disk reader (store, container, XTEA, index) so the server can boot and log
  `rev 242: 25 archives, 120,374 groups` and verify the §1 spot-checks. This reader is the foundation for Phase 1.
- CI (GitHub Actions): setup → build (compile, ktlint, detekt, tests) → boot check.

## Modules touched

`build-logic`, `util` (Revision), `cache` (js5), `server` (main + spot checks); all others are empty shells with their
dependency edges declared.

## Risks

| Risk | Mitigation |
|---|---|
| OpenRS2 blocked by this environment's network policy | Code is written and its logic exercised on a synthetic cache; the real check runs in CI or once the host is allowed. **Blocking** for "done". |
| No JDK 25 in this environment | D-004: the repo stays on 25, the sandbox overrides locally, and CI runs 25. |
| Maven Central 429s | D-009 local mirror. |
| detekt 2.0 is alpha | Pinned; it can be swapped for a stable release later without touching code. |
| Group-count semantics vs OpenRS2's figure | D-008 OPEN; resolved on the first real run. |

## Definition of done

1. `./gradlew build` is green. **Met here on JDK 21; CI must confirm on 25.**
2. The server boots and logs `rev 242: 25 archives, 120,374 groups` against the real cache 2757, and the spot-checks
   pass. **Blocked here: the OpenRS2 download is denied by the network policy.**
