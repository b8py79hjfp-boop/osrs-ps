# Progress

## Phase checklist

- [x] **Phase 0 — Scaffold** — done 2026-11-05; CI green on Temurin 25.0.5
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

- None. Repo: github.com/b8py79hjfp-boop/osrs-ps. CI (JDK 25) runs setup → build → boot check. The dev sandbox still
  builds on JDK 21 locally (D-004); CI is the JDK 25 gate.

### Phase 0 acceptance (2026-11-05)

`./gradlew setup` downloaded cache 2757 and pinned its SHA-256s. `./gradlew :server:run --args=--boot-check` logged
`rev 242: 25 archives, 120,374 groups (23 populated archives, 174 ms)` and passed the archive 0/24 CRC and version
spot-checks. CI on Temurin 25.0.5 shows the same, with checksums matching `242.sha256`.

## Next

1. Phase 1 plan (`docs/plans/phase-1.md`): config decoders (obj first in the main session, the rest to `implementer`),
   map/loc decoding, collision, gameval symbol generation from archive 24, cache-dump tool, test 1.
2. Decide D-006 (routing library) during Phase 1.

## Open questions

- D-007: confirm the RuneLite 1.13.6 bootstrap against a live RSProx session in Phase 2.
