# Load test (Phase 5 acceptance, brief §7)

**Done when:** botswarm holds 4,096+ players across ≥ 3 worlds for 30 minutes, within the per-world budget: logic
p99 ≤ 120 ms, info + flush p99 ≤ 80 ms, GC p99.9 < 2 ms. Tick histograms are recorded throughout.

## Full run (16-core server and a separate bot machine)

Server machine:

```bash
./gradlew :server:installDist
```

```bash
JAVA_OPTS="-Xms8g -Xmx8g -XX:+UseZGC -Xlog:gc*:file=gc.log -XX:StartFlightRecording=filename=load.jfr,settings=profile" \
  server/build/install/world-node/bin/world-node --worlds 4 --max-per-ip 5000 --public-host <server-ip>
```

Bot machine, with one swarm per world (1,024 bots each, 4,096 total). Each swarm needs a different name prefix,
because the hub allows one session per account:

```bash
for w in 0 1 2 3; do JAVA_OPTS="-Xmx2g" tools/botswarm/build/install/botswarm/bin/botswarm 1024 <server-ip> $((43594+w)) 3222 3218 mixed W$w & done
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
