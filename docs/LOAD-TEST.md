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

**Acceptance:** the user ran the full test (about 4,000 players) on 2026-11-06 and reported it complete. The
detailed numbers listed under "Record for the acceptance report" (tick percentiles per world, missed deadlines, GC
p99.9, allocation sites) haven't been recorded here yet.

## Raids (raids brief section 7, 2026-11-08)

Manual or nightly runs; nothing here is a test. All of them were run on this 4-core sandbox.

### Soak and memory: `tools/raid-soak`

These run in process on the headless engine (no sockets).

```bash
./gradlew :tools:raid-soak:run --args="soak 10000"
```

```bash
./gradlew :tools:raid-soak:run --args="memory 25"
```

```bash
./gradlew :tools:raid-soak:run --args="load 75 500"
```

**Soak (10,000 create-run-destroy cycles, the four raids in turn; G1, 4 GB heap):**
- **Leaks:** none in NPCs, pending respawns, instances, instance-space loc changes or base locs, ground objs or
  runs. The tool prints the counts against the baseline every 1,000 cycles.
- **Heap and RSS:** heap after full GC was 541 MB at cycle 1,000 and still 541 MB at cycle 10,000. RSS was flat
  (2,010–2,011 MB) after the heap reached its working size.
- **Instance build (off the world thread):** p50 3.4 ms, p99 12.1 ms against a 50 ms budget. The maximum, 308 ms, is
  each raid's first, cold build, which fills the shared template cache.
- **Attach (world thread):** p50 0.17 ms, p99 0.41 ms against a 1 ms budget. The maximum was 3.5 ms: a single
  outlier in 10,004 attaches, most likely a GC pause landing on it.

**Memory per instance (25 live instances of one raid, heap after GC):**

| Raid | Per instance | Budget |
|---|---|---|
| ToB | 0.61 MB | 2 MB |
| CoX | 0.57 MB | 4 MB |
| ToA | 1.02 MB | 2 MB |
| TFA | 0.66 MB | — |

**Headless load:** 300 live instances, 75 of each raid, every run changing rooms every 50 ticks, 4,119 NPCs. Logic
p50 7 ms, p99 14 ms. This run encodes no info packets; the networked run below does.

### Networked load: botswarm `raids` mode

300 real-protocol bots each run a solo raid (75 instances of each raid) through `::raid <id>` and `::raid next`,
taking a room step every 30 s.

```bash
JAVA_OPTS="-Xms4g -Xmx4g -XX:+UseZGC -XX:StartFlightRecording=filename=raids.jfr,settings=profile" server/build/install/world-node/bin/world-node --max-per-ip 5000
```

```bash
JAVA_OPTS="-Xmx1g -Dbotswarm.powThreads=2 -Dbotswarm.rampMs=150" tools/botswarm/build/install/botswarm/bin/botswarm 300 127.0.0.1 43594 3222 3218 raids R
```

After all 300 instances are live, call `curl localhost:9090/reset` and measure a five-minute window (this resets the
tick and instance histograms and the alert baseline).

| Run | Logic p50 / p99 / max | Info p99 | Missed deadlines | Alerts |
|---|---|---|---|---|
| Before the fixes | 57 / 100 / 128 ms | 30 ms | 0 | 1 window at 129 ms |
| After the square index | 18 / 33 / 45 ms | 31 ms | 0 | 0 |
| After the allocation fixes | 18 / 32 / 41 ms | 39 ms | 0 | 0 (2 during the login and start-up burst, before the window) |

**What the runs found and fixed:**
- **Attach over budget.** Attach took 3.5 ms at p50 and 35 ms at p99 (ToA 12 ms on average). Fixes:
  - The build now computes each zone's final collision and base-loc table off the world thread; attach just swaps
    them in.
  - Loc keys go through fmix64, because the old packing folded x into z and filled HashMap bins.
  - Zone ownership is a BitSet instead of boxed integers.
- **Leaks.** Turned base locs that start outside the instance were registered in a foreign zone and never removed.
  Emptied zone lists also stayed in their maps.
- **Memory.** ToA used 5.6 MB per instance. Base locs are now packed at about 8 bytes each (`ZoneBases`), and empty
  zones share one copy-on-write tile array.
- **Tick time.** 43% of the world thread was NPC aggression: every NPC scanned every player, ToB's tick scanned every
  NPC once per run, and name lookups were linear. Players and NPCs are now indexed by map square, and results keep
  PID and index order, so the golden scenarios and replays are unchanged.
- **Allocation.** The world thread allocated about 4.5 GB in a 10-minute profile, 223 MB of it from raid systems.
  Fixes:
  - one cached `PlayerRef` / `NpcRef` per entity;
  - raid world ticks rewritten without per-tick lists;
  - aggression uses `playersWithinScratch`.

  What remains in raid code is lists built when a boss attacks (every few ticks), not every tick. The JFR
  attribution is in the table below.
- **Budgets now:** logic p99 32 ms (budget 120 ms), info p99 39 ms (80 ms), attach p99 0.41 ms (1 ms), build p99
  12 ms (50 ms). Every raid's memory is within its aim.
- **Allocation still open:** raid code still allocates at attack time (lists of raiders, tiles), about 5% of the world
  thread's sampled allocation. The engine and protocol allocate the rest: NPC info, the info pass, timers.
  Steady-state zero from raid systems needs the boss scripts moved to the scratch query style (raids brief section
  7).
- **Alert after reset.** `/reset` used to raise a false 2,000 ms alert, because the window diff went negative.

| Profile | World-thread allocation (sampled) | From raid systems |
|---|---|---|
| Before | 4,565 MB | 223 MB |
| After cached refs and list-free ticks | 4,106 MB | 151 MB |
| After the aggression scratch list | 2,906 MB | 146 MB |

