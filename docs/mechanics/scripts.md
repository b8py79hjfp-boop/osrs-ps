# Queues, timers and delays

Code: `engine/src/main/kotlin/tp/engine/script/*`. Rule ids here (D/Q/T) are local to this file. Sources: osrs-docs
Queues, Timers, Delays (tick.md T3–T5) and Henke (H, tick.md T2).

## Delays (D)

| # | Rule |
|---|---|
| D1 | Two kinds: **arrive delay** (1 tick if the entity moved this tick or last tick, else none) and **normal delay** (N ticks). Called from a script, they also suspend that script. |
| D2 | While delayed: queues don't process; timers count down but pause at 0; entity interactions aren't processed; route events don't run, but already-determined movement continues; most input is ignored. |
| D3 | A script that set a delay resumes at the start of the entity's turn on the tick the delay ends ("delay end" phase). |
| D4 | Interface clicks still arrive while delayed; each button script decides whether to act. |

## Player queue (Q)

| # | Rule |
|---|---|
| Q1 | One FIFO for every type (excluding the area queue). No known cap. |
| Q2 | Not processed while delayed, except soft scripts. |
| Q3 | At the start of processing: if any strong script is queued, close the modal; closing the modal removes every weak script. |
| Q4 | Loop over the queue until a full pass processes nothing (pseudocode `processQueue`). Each visited strong or soft script closes the modal **when visited**, even if it can't run yet (which also removes weak scripts). An entry runs only if (soft or not delayed) and no modal is open and it isn't scheduled in the future: the pseudocode applies the modal check to every type; the prose only mentions normal scripts. We follow the pseudocode. |
| Q5 | Once a script sets a delay, nothing further runs this tick except soft scripts. |
| Q6 | A script queued **while the queue is being processed** runs no earlier than the next tick (it is still visited by the loop, so a strong one still closes the modal). Scope is our reading: Henke's locator orb queues damage from client input and it runs in the same tick's queue step, so the rule can't apply to every script. **OPEN-S5:** exact scope. |
| Q7 | Weak scripts are removed by interruptions: interacting with an entity or a tile, inventory item actions, unequipping, opening or closing interfaces, dragging items. osrs-docs: "In general, it seems like any action which closes an interface also clears all weak scripts from the queue" (hedged at the source). |
| Q8 | Long queue: a normal script with a logout behaviour. *Accelerate* ignores the remaining delay on logout and runs as soon as allowed; *discard* drops it. |
| Q9 | Damage is a strong script (some exceptions use normal, e.g. recoil; some effects apply directly in the item script). |

Pseudocode followed verbatim: osrs-docs Queues §"Pseudocode" (`process()`, `processQueue()`, `canProcessScript()`).

## NPC queue (Q)

| # | Rule |
|---|---|
| Q10 | NPCs have one queue with one strength. osrs-docs marks the section incomplete. **OPEN-S1:** processed FIFO, skipping future entries and not processed while delayed (same as normal player scripts without modals). |

## Area queue (Q, H)

| # | Rule |
|---|---|
| Q11 | A separate FIFO for scripts triggered by area tiles: multiway borders, music unlocks, farming, bank zones, map chunk lines. |
| Q12 | With an interface open or while stalled, movement is blocked while the area queue is non-empty (H). **OPEN-S2:** exact processing rules (osrs-docs "incomplete"); currently FIFO, run when not delayed and no modal. |

## Timers (TM)

| # | Rule |
|---|---|
| T1 | A timer counts down from its interval and runs its script at 0. NPCs run timers **before** their queue, players **after**. |
| T2 | A timer never fires on the tick it is added. osrs-docs: new timers are "**presumably**" kept in a separate collection merged after processing; we do that. **OPEN-S6:** fire offset: an interval-N timer added at tick T first fires at T+N here (unverified). |
| T3 | **Soft timers** (players and NPCs) "tick down regardless of the state of the entity" and get no protected access. **OPEN-S3:** whether they also *fire* during a delay: Delays says timers pause at 0 while delayed (unqualified); Timers only says soft timers tick regardless; Henke: "for the most part, our timers don't go off" while stalled. Current behaviour: soft timers fire during delays. |
| T4 | **Normal timers** (players only) tick down regardless, but fire only when not delayed and no modal is open. Otherwise they halt at 0 and retry each tick; after firing they reset to their interval. |
| T5 | Players have no timer limit. NPCs can hold **one** timer. |
| T6 | Known soft: prayer drain, immunities (freeze, teleblock), stat replenishment, divine/overload, stamina, vengeance cooldown, … Known normal: toxins, cannon, antifire/antitoxin (15-tick interval), farming. |

## More OPEN items (verifier pass, 2026-11-05)

- **OPEN-S4:** whether the strong check (Q3) and weak removal run while the player is delayed. The pseudocode has no
  delay check; we run it every turn.
- **OPEN-S7:** delay-length to resume-tick offset. Henke: normal teleport stall "ends four ticks later"; osrs-docs media:
  Falador teleport "cause[s] a three tick delay". We use: delay(N) set on tick t blocks t..t+N-1, resumes at t+N.
- **OPEN-S8:** whether soft queue scripts that run while delayed get protected access. Currently they run with the
  player but `withProtectedAccess` refuses while delayed.
- Henke: with an interface open, walking is also blocked by some queued scripts and some timers. Not modelled yet.
- NPC delays: osrs-docs Delays applies to NPCs too ("queues do not get processed" while delayed), which answers part
  of OPEN-S1.

## Protected access (brief §5.4)

A script may mutate a player only through `ProtectedAccess`, which the engine grants only when the player is not
delayed and has no modal open. Soft timers never get it (T3). Queue scripts get it under Q4's conditions.
