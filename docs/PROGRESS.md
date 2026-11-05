# Progress

## Phase checklist

- [x] **Phase 0 — Scaffold** — done 2026-11-05; CI green on Temurin 25.0.5
- [x] **Phase 1 — Cache and world data** — done 2026-11-05 (pending CI confirmation on JDK 25)
- [ ] **Phase 2 — Networking and client** — built; waiting on the real-client check (see Blocking)
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

- Phase 1 (2026-11-05):
  - 22 strict config decoders + IF1/IF3 interfaces; undecoded groups listed (D-012).
  - All 2,949 map squares / 5,021,872 locs (unnamed rev-242 layout, D-013); full-map collision with the client
    bridge rule (D-011, docs/mechanics/collision.md, verifier pass applied).
  - Vendored RS Mod routefinder (D-006 final); symbols generated as Java constant chains (D-014).
  - Test 1 (`./gradlew :tools:symbol-gen:test`) passes in 3.9 s (> 1 s budget, D-016).
  - tools/cache-dump: summary / config / interface / map / collision.

### Phase 1 measurements (4-core sandbox, JDK 21, cold JVM)

| Step | Time | Notes |
|---|---|---|
| Open cache + master index | ~0.1 s | |
| All configs (22 types) | 0.8–1.5 s | |
| Interfaces | ~0.1 s | |
| All map squares | 0.7–1.6 s | 4 decode threads |
| Full collision | 0.3–1.6 s | single-threaded |
| **Total boot data** | **~3.5 s** | under the §6 10 s dev-boot target without a snapshot |
| Heap | 384 MiB after GC (locs + map + collision); ~460 MiB live at end of boot | CollisionFlagMap's zone array alone is 16.7 M refs (~64 MiB) |

- Phase 2 (2026-11-05):
  - RSProt 242 vendored (D-005) + ground-truth diff vs RSProx (all opcodes/sizes match; 2 OPEN).
  - `protocol-api` contracts; `protocol/osrs-242` adapter. JS5 pre-encoded (120,398 groups, 204 MiB, 1.3 s parallel).
    Login with PoW/RSA/ISAAC/CRC via RSProt; bounded admissions per tick.
  - `net`: `/jav_config.ws` (Jagex template rewritten, D-017) + `/worldlist.ws`.
  - Engine slice: anchored 600 ms tick, PID-ordered phases, server-side routing, walk/run, build-area rebuilds, info pass.
  - `tools/botswarm`: headless rev-242 client + `Swarm` runner. **Test 2 passes** (JS5, login, rebuild, player info,
    walk, second observer).
  - docs/CLIENT.md, docs/mechanics/movement.md.

## Blocking

- **Phase 2 DoD needs a human with a GUI:** run RuneLite via RSProx (docs/CLIENT.md) on Windows or Linux. Confirm
  cache download, login, walking/running and seeing bots. This sandbox has no display. Report D-005 OPEN-A/B outcomes.
- Repo: github.com/b8py79hjfp-boop/osrs-ps. CI (JDK 25) runs setup → build → boot check. The dev sandbox still
  builds on JDK 21 locally (D-004); CI is the JDK 25 gate.

### Phase 0 acceptance (2026-11-05)

`./gradlew setup` downloaded cache 2757 and pinned its SHA-256s. `./gradlew :server:run --args=--boot-check` logged
`rev 242: 25 archives, 120,374 groups (23 populated archives, 174 ms)` and passed the archive 0/24 CRC and version
spot-checks. CI on Temurin 25.0.5 shows the same, with checksums matching `242.sha256`.

## Next

1. Real-client check (above), then Phase 3 plan: full tick pipeline, scripts/queues/timers/delays/interactions,
   plugin-api, varps/inventories/interfaces, tick inspector, replay, packet validation, item transactions.
   Also: RSProx-compatible `.bin` recording behind a dev flag (brief §4.3).
2. Ask for osrs-docs.com and oldschool.runescape.wiki to be reachable: brief §2's primary mechanics sources.

## Open questions

- collision.md OPEN-1..4 (terrain extension on square 64,73; group 25287 and square files 2–4; osrs-docs check;
  wall route blockers).
- D-012 undecoded config groups; gameval groups 16–19 unnamed.
- Interface IF3 model-size bytes (ComponentTypeDecoder KDoc).

- D-007: confirm the RuneLite 1.13.6 bootstrap against a live RSProx session in Phase 2.
