# The game tick

Code: `engine/src/main/kotlin/tp/engine/World.kt` (`runTick`) and `engine/.../tick/*`. Every phase below is a
named step in that code, and the tick inspector traces them by name.

## Sources

| # | Source |
|---|---|
| T1 | OSRS Wiki, "Game tick": https://oldschool.runescape.wiki/w/Game_tick |
| T2 | Henke's model, data-dependent/osrs-guides `skilling.md` §"Ticks via Henke's Model" (raw: https://raw.githubusercontent.com/data-dependent/osrs-guides/master/skilling.md) |
| T3 | osrs-docs Queues: https://osrs-docs.com/docs/mechanics/queues/ |
| T4 | osrs-docs Timers: https://osrs-docs.com/docs/mechanics/timers/ |
| T5 | osrs-docs Delays: https://osrs-docs.com/docs/mechanics/delays/ |
| T6 | osrs-docs Entity Interactions: https://osrs-docs.com/docs/mechanics/entity-interactions/ |
| T7 | Brief §5.3 (info pass, post-tick) |
| T8 | OSRS Wiki "Player identification number": https://oldschool.runescape.wiki/w/Player_identification_number |

## Cadence

A tick is 600 ms (T1). Ticks are scheduled on a fixed cadence anchored to the world's start instant, so there is no
drift. An overrun runs the next tick immediately and increments a missed-deadline counter. Logic phases are never
skipped (T7).

## Pipeline

```
1. pre-tick         clock++; drain cross-thread inbox (login requests are queued here, admitted in 5)
2. client input     for each player in PID order: up to 10 commands, in arrival order          (T2)
3. npcs, index order
     delay end      resume the suspended script if its delay finished                           (T5)
     timers         (NPCs: timers before queue; at most one timer)                              (T4)
     queue          (one strength)                                                               (T3)
     interactions + movement   T6 pseudocode, same as players (T6 "the same concept applies for NPCs")
4. players, PID order
     delay end      resume the suspended script if its delay finished                           (T5)
     strong check   if a strong script is queued: close modal (which removes weak scripts)       (T2, T3)
     queue          osrs-docs processing loop                                                    (T3)
     timers         (players: after queue)                                                       (T4)
     area queue                                                                                  (T2)
     interactions + movement   osrs-docs processInteractions (two blocks around movement)       (T6)
     logout check   close interface if trying to log out                                         (T2)
5. post-tick        logouts; bounded logins; PID shuffle when due                                (T7)
6. info pass        state frozen: sync avatars, build infos (parallel in RSProt), zone updates, flush once
```

## Consequences that must fall out (never special-cased)

| Consequence | Why | Source |
|---|---|---|
| A click in tick N acts in tick N+1 | input is read at the start of the next tick | T2 |
| NPC hits on players land the same tick; player hits on NPCs land a tick later | NPCs act before players; damage goes into the defender's queue (players: strong script; NPC queue strength is unspecified, scripts.md OPEN-S1) | T2, T3 |
| In PvP the lower PID's zero-delay hit lands the same tick, the higher PID's the next | players act in PID order; the victim's queue runs in its own turn | T2, brief §5.3 |
| Banker (NPC) interaction happens on arrival; booth (loc) a tick later | player/npc op is allowed after moving; loc op needs a tick without movement | T2, T6 |
| Prayer flicking doesn't drain | timers never fire on the tick they're added | T4 |

## PID

Lower PID acts first. A PID is assigned on login and "will randomise every 100 to 150 game ticks ... for each player";
on Bounty Hunter, PvP and tournament worlds every 40 to 60 ticks (T8, change of 9 August 2018).

Implementation: each player has their own window. When it expires, the player swaps to a uniformly random position in
the processing order. **OPEN-T1** (narrowed): how Jagex re-randomises one player's PID without moving another's isn't
documented; a swap moves exactly one other player.

## Interaction model (SOURCE-CONFLICT, decided)

Henke (T2) lists "interaction with objects/items → movement → interactions with players/npcs". osrs-docs (T6) gives
a single routine with two attempt blocks around movement. Both produce the banker-vs-booth timing. The brief requires
osrs-docs' semantics exactly (§5.4), so the engine implements T6's pseudocode for players and NPCs (DECISIONS D-020).

## Input cap

At most 10 commands per tick per player, the first ten in arrival order (T2's 28-herbs example: 10, 10, then 8 plus
the movement). **OPEN-T2** (narrowed): Henke's "rarely only the last ten commands will be executed" isn't modelled.
RSProt enforces the 10-user-packet threshold at decode time. Input is read in PID order (brief; Henke doesn't order it).
