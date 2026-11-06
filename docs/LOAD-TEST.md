# Load test (Phase 5 acceptance, brief §7)

**Done when:** botswarm holds 4,096+ players across ≥ 3 worlds for 30 minutes, within the per-world budget: logic
p99 ≤ 120 ms, info + flush p99 ≤ 80 ms, GC p99.9 < 2 ms. Tick histograms are recorded throughout.

## Full run (16-core server and a separate bot machine)

Server machine:

```bash
./gradlew :server:installDist
```

```bash
JAVA_OPTS="-Xms8g -Xmx8g -XX:+UseZGC -Xlog:gc*,safepoint:file=gc.log -XX:StartFlightRecording=filename=load.jfr,settings=profile" \
  server/build/install/world-node/bin/world-node --worlds 4 --max-per-ip 5000 --public-host <server-ip>
```

Bot machine, with one swarm per world (1,024 bots each, 4,096 total). Each swarm needs a different name prefix,
because the hub allows one session per account:

```bash
for w in 0 1 2 3; do JAVA_OPTS="-Xmx2g -Dbotswarm.powThreads=2 -Dbotswarm.rampMs=100" tools/botswarm/build/install/botswarm/bin/botswarm 1024 <server-ip> $((43594+w)) 3222 3218 mixed W$w & done
```

`mixed` splits the bots evenly: walkers near Lumbridge, clumped multi-combat fighters in the level-36 Wilderness
multi area, and gear switchers (whip/DDS every 1–2 ticks). For world hops, start the server with `--loopback-hosts`
(single machine) and add one swarm in `hop` mode.

Watch while it runs:

```bash
watch -n 10 'curl -s <server-ip>:9090/metrics | grep -E "players|0.99|missed"'
```

`/health` turns 503, and the log shows `ALERT` lines, whenever a 10 s window breaks the budget.

Record for the acceptance report (PROGRESS.md):

- `tp_tick_ms{phase,quantile}` per world at 30 minutes;
- `tp_missed_deadlines_total`;
- GC pauses from `gc.log` (p99.9);
- the top world-thread allocation sites from `jfr print --events jdk.ObjectAllocationSample load.jfr`.

## Scaled runs in this sandbox (4 cores; bots and server share them)

Command: 3 worlds, one `mixed` swarm of 300 bots per world, 175 s, with JFR.

| Run | Players | Logic p99 (w1/w2/w3) | Info p99 | Missed deadlines | World-thread alloc (sampled) |
|---|---|---|---|---|---|
| before allocation fixes | 405 | 159 / 68 / 73 ms | 67 / 47 / 29 ms | 2 / 2 / 2 | 245 MB |
| after (ZoneSync arrays, inspector) | 417 | 42 / 93 / 67 ms | 56 / 33 / 28 ms | 0 / 0 / 0 | 141 MB |

- Login throughput, not tick time, limited both runs. Argon2id hashing and the bots' proof-of-work share 4 cores with
  the server and three bot JVMs, which gives about 3 logins/s. The logic p99 includes login admission and save
  restore.
- The remaining world-thread allocation is mostly per login: PlayerVars, RSProt Player/NPC/WorldEntity info and the
  friends list. In steady state the main sources are RSProt's parallel info build (ForkJoin tasks) and inventory
  snapshots copied for the encoder.
- On a 4-core box, give each bot JVM `-Xmx512m`; with default heaps two bot JVMs were OOM-killed.

## 20-minute run in this sandbox (2026-11-06)

**Setup:**
- 3 worlds, 3 × 1,366 `mixed` bots ramped 220 ms apart, on 4 cores.
- The server (`-Xmx4g`, ZGC) and the three bot JVMs shared those cores.
- The tick histograms were reset (`/reset`) after the ramp, and then 20 minutes were measured.

**Results:**

| World | Players | Logic p99 / p99.9 / max | Info p99 / p99.9 / max | Missed deadlines in window |
|---|---|---|---|---|
| 1 | 1,151 | 33 / 45 / 67 ms | 130 / 213 / 226 ms | 0 |
| 2 | 986 | 33 / 48 / 58 ms | 117 / 209 / 235 ms | 0 |
| 3 | 871 | 38 / 51 / 53 ms | 97 / 159 / 184 ms | 0 |

- **Players:** 3,008 held for 20 minutes. The other 1,090 bots were dropped at login (see below).
- **Stability:** no world thread failed. Over the whole run, 10 ticks missed their deadline, all during the login ramp.
  The slowest save took 226 ms.
- **Logic:** well within its budget (p99 ≤ 120 ms).
- **Info + flush:** p99 97–130 ms against the 80 ms budget. Most of that is the bots' CPU: they do proof-of-work,
  ISAAC and packet decoding on the same 4 cores, and RSProt's parallel player-info build competes with them.
- **Logins:** this box could only reach 3,008 players. With every core busy, Argon2id hashing (capped at one hash
  per core) fell behind. Logins that waited more than RSProt's 40 s login idle time were dropped, 1,090 in all.
- **GC p99.9:** not measured. `-Xlog:gc` prints only ZGC cycles, not pauses, so the full-run command above now also
  logs safepoints.

**Bugs this run found and fixed:**
- Concurrent Argon2 hashing exhausted memory during the login storm. Hashing is now capped at one permit per core.
- A login whose client had already gone made RSProt throw from `accept()`, which killed the world thread. `admit` now
  refuses such logins, and the tick loop survives any escaped exception.

**Acceptance:** the §7 numbers (4,096+ players, info p99 ≤ 80 ms, GC p99.9 < 2 ms) still need the 16-core run above,
with the bots on another machine.
