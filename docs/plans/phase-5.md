# Phase 5 — Scale and operations

Done when the §7 load acceptance holds for 30 minutes: botswarm sustains 4,096+ players across ≥ 3 worlds within the
per-world budget (logic p99 ≤ 120 ms, info + flush p99 ≤ 80 ms, GC p99.9 < 2 ms), with tick-time histograms.

## Topology (brief §7)

- **hub** (separate JVM): login authority and accounts, persistence (PostgreSQL), friends/ignore/PMs, friends chat,
  Grand Exchange skeleton, hiscores, world directory, broadcasts, moderation. Exposed over gRPC (grpc-kotlin coroutine
  stubs, schema-first `.proto` in `services/src/main/proto`), streaming for chat and broadcasts; also the HTTP sign-up
  endpoint, health checks and the world list.
- **world-node** (JVM): 1..N worlds, each on its own platform thread and game port; shares cache, configs, collision.
  Talks to the hub only through the `services` interfaces.
- **services**: Kotlin interfaces + in-process implementations (dev, tests, single-JVM) + gRPC client/server adapters.

## Work order

1. `services`: interfaces (AccountService, SaveService, SocialService, ExchangeService, HiscoreService, WorldDirectory,
   BroadcastService, ModerationService), in-process impls, proto schema, gRPC adapters.
2. Persistence: versioned player saves (schema version + forward migrations), PostgreSQL store (+ file store for dev),
   write-behind on virtual threads, staggered autosaves, logout save durable before the account can log in again.
3. Accounts: Argon2id, sign-up HTTP endpoint on the hub, login throttling per IP and per account, connection limits.
4. World-node: N worlds per JVM, hub-backed login, saves on logout/autosave, world hopping (durable save → hub
   releases → client reconnects to the new world).
5. Social: friends/ignore lists, private messages, friends chat (rev-242 packets via RSProt), broadcasts.
6. Grand Exchange skeleton (offers, matching, collection) and hiscores — through the item transaction API.
7. Moderation (kick, mute, ban account/IP; permissions as data; audit log) and operations (system-update countdown +
   save-all shutdown, health checks, metrics and alerts on tick p99 / missed deadlines / save latency, PITR backup docs).
8. DDoS setup: PROXY protocol v2 (RSProt HAProxy module) behind a flag; never publish world-node addresses.
9. Revision upgrades: `./gradlew upgradeRevision -Prev=N` + docs/REVISION-UPGRADE.md.
10. Scale: botswarm behaviours (walk, clumped multi-combat, chat, gear-switch spam, world hops) across worlds,
    tick histograms, JFR allocation check, hot-loop allocation removal; scaled runs here (4 cores) and the full run
    on a 16-core machine.

## Risks

| Risk | Mitigation |
|---|---|
| 4,096 bots + 3 worlds exceed this 4-core sandbox | Measure scaled runs; the 30-minute acceptance runs on 16 cores (command in docs/LOAD-TEST.md) |
| Hot-loop allocation (coroutines, visuals lists, zone ops) | JFR allocation profiling; pool/flatten the per-tick paths found |
| Dupes across world hops | Logout save is acknowledged by the hub before the session lock is released; login waits for it |
| gRPC/protobuf with Kotlin 2.4 / JDK 25 | Pin versions in the catalog (D-026); fall back to in-process if a plugin breaks the build |
