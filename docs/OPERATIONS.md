# Operations

## Topology (brief §7)

```
players ──> DDoS proxy (PROXY v2) ──> world-node JVM(s): N worlds each ──gRPC──> hub ──> PostgreSQL
                                       :9090 /health /metrics                     :8080 jav_config, worldlist,
                                                                                  /register, /hiscores, /health
```

Start the hub first, then the world nodes:

```bash
./gradlew :hub:run --args="--db jdbc:postgresql://db/tp --db-user tp --db-password ... --public-url https://play.example --grpc-bind 10.0.0.2"
```

The hub's gRPC binds to 127.0.0.1 unless `--grpc-bind` names the private-network address the world nodes use. It is
unauthenticated: never bind it to a public interface. The control panel uses a separate mTLS listener (`--panel-*`,
panel/docs/plan.md).

```bash
./gradlew :server:run --args="--hub hub:43600 --worlds 3 --world 1 --public-host w1.example --proxy-protocol 10.0.0.0/8"
```

- Never publish world-node addresses. The world list advertises the proxy addresses, and `--proxy-protocol <cidrs>`
  makes the node accept connections only from those proxies and read the real client IP from them. IP limits, bans
  and login throttling all use that real IP.
- In development, the world node embeds the hub (memory or `--db`). `--loopback-hosts` puts world i on
  127.0.0.(i+1):43594 so a real client can hop. `--admins name` grants administrator rights.

## Health, metrics, alerts

| Endpoint | Meaning |
|---|---|
| `world-node:9090/health` | 200 `ok`, or 503 with the problems from the last 10 s alert window |
| `world-node:9090/metrics` | Prometheus: `tp_players`, `tp_ticks_total`, `tp_missed_deadlines_total`, `tp_tick_ms{phase=logic|info,quantile}`, `tp_save_*_ms` |
| `hub:8080/health` | `ok online=<n>` |

The node logs `ALERT ...` at ERROR level when, in a 10 s window:

- logic p99 is over 120 ms, or info p99 is over 80 ms (the §7 budget);
- any tick misses its 600 ms deadline;
- any save takes longer than 1 s.

Route these lines to paging. A sustained missed-deadline alert means the world is overloaded: move worlds to another
node.

## Moderation

Rights are data: `accounts.rights` is `PLAYER`, `MODERATOR` or `ADMIN`. Bootstrap the first administrator with
`--admins`, or with SQL: `UPDATE accounts SET rights='ADMIN' WHERE username='name'`.

- Moderators: `::kick ::mute ::unmute`.
- Administrators also: `::ban ::ipban ::unban ::broadcast ::update`.

Every action, login and rights grant is written to `audit_log`.

## Saves and shutdown

- Autosave runs every 500 ticks, staggered by player slot, and is written behind on virtual threads.
- A logout save is durable before the hub releases the account, so world hops and relogs never load stale data.
- `::update <seconds>` shows the client countdown, refuses new logins, and at zero logs everyone out with final saves.
- SIGTERM saves all players (final) before the process stops.

## PostgreSQL backups (PITR)

1. Enable WAL archiving in `postgresql.conf`: `wal_level = replica`, `archive_mode = on`,
   `archive_command = 'test ! -f /backup/wal/%f && cp %p /backup/wal/%f'`. Use object storage in production, for
   example with wal-g or pgBackRest.
2. Take a nightly base backup: `pg_basebackup -D /backup/base/$(date +%F) -Ft -z -X none`.
3. Keep 14 days of base backups plus the WAL since the oldest one.
4. To restore to time T:
   1. Stop the hub.
   2. Restore the newest base backup before T into a new data directory.
   3. Set `restore_command = 'cp /backup/wal/%f %p'` and `recovery_target_time = 'T'`, and create `recovery.signal`.
   4. Start PostgreSQL, check the data, then run `SELECT pg_wal_replay_resume();`.
   5. Start the hub.
5. Rehearse a restore every month on a scratch host. Record how long it took and the last audit_log row recovered.

Saves are jsonb with a `version` field (`PlayerSave.VERSION`). Older saves are migrated forward on load, so a restored
backup from an older build still loads.

## Load testing

See docs/LOAD-TEST.md.
