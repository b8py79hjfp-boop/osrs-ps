# Phase 2 — Networking and client

## Scope

1. **Protocol (D-005 executed).** Vendor RSProt `protocol/osrs-242/*` (6 submodules: api, common, desktop, internal,
   model, shared) at commit `67d2136` into `protocol/osrs-242/`, MIT headers and LICENSE kept, unmodified. Depend
   on RSProt's published shared modules (`buffer`, `crypto`, `compression`, `haproxy`, `protocol` at
   `1.0.0-ALPHA-20261014`). Verified identical to the 242 commit: zero diff in those paths.
2. **Ground-truth diff.** Compare the vendored RSProt 242 codecs with RSProx's `osrs-242` decoders (opcodes, sizes,
   field order and transforms), both directions. `scout` produces the raw table; the main session decides. Any
   mismatch is fixed in a listed local patch, or documented as OPEN.
3. **`protocol-api`.** Our own interfaces for what the engine needs: login requests and responses, inbound player
   commands (walk, …), outbound messages (rebuild, player info, var/inv later). Engine code never imports
   `net.rsprot.*`. Only `protocol/osrs-242` (adapter) and `net/` do.
4. **`net/`.**
   - RSProt `NetworkService` bootstrap (Netty 4.2: io_uring → epoll → NIO).
   - JS5 from pre-encoded groups in direct buffers, with the master index from our `Js5Cache`.
   - Login: PoW on new logins, RSA on a pool, ISAAC, archive-CRC check against our master index, bounded admissions
     per tick.
   - HTTP `/jav_config.ws` + `/worldlist.ws`, using RSProx's HTTP server as the format reference.
5. **Minimal engine slice** (Phase 3 completes it): world with a 600 ms anchored tick, player slots 1–2047, PID
   order. Login → place player → `REBUILD_NORMAL` → player info.
   - Walk/run with server-side routing (vendored routefinder on the Phase 1 collision map), 1 or 2 tiles per tick.
   - Clicks received in tick N act in tick N+1.
   - Info pass after logic.
   - Just enough to walk and to see other players (bots). No scripts, queues or interactions yet.
6. **RSA + client config.** `generateRsa` exists. Add `docs/CLIENT.md`: exact `proxy-targets.yaml`, bootstrap pin
   `b4dab8e7…` (D-007), troubleshooting.
7. **botswarm client + test 2.** `tools/botswarm`: a headless client that speaks real rev-242. JS5 handshake and a
   few group requests; login with PoW solve, RSA login block, ISAAC and CRCs; then a handful of game packets
   (decode the rebuild/player info frame boundary, send walk). Test 2 runs it against an in-process server on
   localhost. Bots also stand in the world so a real client "sees bots".

## Modules touched

`protocol/osrs-242/*` (vendored), `protocol-api`, `net`, `engine` (slice), `server` (wiring + config), `tools/botswarm`,
`docs/CLIENT.md`.

## Risks

| Risk | Mitigation |
|---|---|
| RSProt 242 is explicitly untested upstream | Step 2 diff against RSProx (independent decoders); test 2; real-client test by the user |
| Vendored code built with Kotlin 1.9 against our 2.4 / JDK 25 | Compile as vendored modules without lint; any source change is listed in NOTICE |
| No GUI in this sandbox: RuneLite via RSProx can't run here | Everything up to the wire is tested by test 2. The real-client run (and "CLIENT.md works from a clean machine") needs the user on Windows/Linux. Phase 2 can't be marked done until that run happens. |
| Client-side encoding for the bot (no reference client lib) | Mirror RSProt's server-side decoders; test 2 is the check, then the real client confirms |
| Engine slice designed in a hurry, then redone | Build it on the Phase 3 shape: tick phase list, PID-ordered player array, world-owned RNG |

## Definition of done

- Test 2 passes (in CI).
- Boot serves JS5, login and HTTP. A bot logs in, walks and is visible to other bots in player info.
- docs/CLIENT.md written. The user confirms that RuneLite via RSProx downloads the cache, logs in, walks/runs and
  sees bots.
