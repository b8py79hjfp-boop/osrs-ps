# Progress

## Phase checklist

- [ ] **Phase 0 — Scaffold** — built; one done-criterion blocked (see below)
- [ ] Phase 1 — Cache and world data
- [ ] Phase 2 — Networking and client
- [ ] Phase 3 — Engine core
- [ ] Phase 4 — Combat and PvP
- [ ] Phase 5 — Scale and operations
- [ ] Phase 6 — Release client
- [ ] Phase 7+ — Content breadth

## Done

- Phase 0 (2026-11-05):
  - Gradle 9.8.2 wrapper (checksums verified), version catalog, convention plugins (`tp.kotlin-base/-library/-api/-app`,
    `tp.root`), all §5.1 modules except `client/*`.
  - Spotless/ktlint and detekt wired into `check`.
  - `setup` task (download, SHA-256 pin, extract, sanity check) and `generateRsa` task (verified locally).
  - JS5 reader: disk store (normal and extended sectors), container (none/bzip2/gzip, XTEA), index (protocols 5–7).
    Exercised against a synthetic cache: multi-sector extended group, big-smart ids, CRC without trailer, absent groups.
  - Server boot: opens the cache, logs the summary, verifies the §1 spot-checks, `--boot-check` for CI.
  - CI workflow; docs; `.claude/` settings and agents.
  - §13.2 checks recorded (D-002).

## Blocking

1. **OpenRS2 is blocked by this environment's network policy** (`archive.openrs2.org` → proxy 403), so `./gradlew setup`
   can't download cache 2757 here and the boot log can't be verified against the real cache. Options: allow
   `archive.openrs2.org` in the environment's network settings, or rely on CI (GitHub Actions has open egress).
2. **JDK 25 can't be installed here** (D-004). The build is verified on JDK 21; the first CI run is the JDK 25 gate.

## Next

1. Unblock the cache, run `./gradlew setup` (this writes and commits `gradle/cache-checksums/242.sha256`), run the boot
   check, and resolve D-008's group-count question.
2. Phase 1 plan (`docs/plans/phase-1.md`): config decoders (obj first in the main session, the rest to `implementer`),
   map/loc decoding, collision, gameval symbol generation from archive 24, cache-dump tool, test 1.
3. Decide D-006 (routing library) during Phase 1.

## Open questions

- D-008: does OpenRS2's group count include master-index groups?
- D-007: confirm the RuneLite 1.13.6 bootstrap against a live RSProx session in Phase 2.
