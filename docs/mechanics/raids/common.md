# Raids: common machinery

Rules every raid shares; per-raid fact sheets refine them (`tfa.md`; CoX, ToB and ToA sheets are written in their
phases, docs/plans/raids.md). Sources: osrs-docs.com "Instancing" (BRIEF §2), the OSRS Wiki raid pages, and the
existing engine rules in docs/mechanics/instances.md. Read 2026-11-08.

| Id | Rule | Source |
|---|---|---|
| RC1 | A raid is an instance built from copies of the raid's template zones in the static map (rotation 0-3 per zone); the client draws it from REBUILD_REGION_V2 (instances.md IN2-IN4). TFA uses its real squares x 63-65, z 86-88 and (64, 90). | osrs-docs Instancing; tfa/client-contract.md §5 |
| RC2 | Parties are per world. Starting the raid locks the party and its scaling: nobody joins afterwards, and members leaving do not change scaling. | Wiki raid pages (TFA: tfa.md TFA4-TFA6, TFA9) |
| RC3 | Raid state is ordinary game state (varps, varbits, inventories) named by the cache gamevals; client scripts draw HUDs, party lists and chests from it. | per-raid client contracts |
| RC4 | Teleports out are refused except where the raid allows them (escape crystals, ToB). **OPEN-RC1:** TFA's rule (no escape item is documented). | Wiki raid pages |
| RC5 | Rewards: points or contribution feed the unique roll(s); a recipient is chosen by weight; commons and tertiaries are rolled; results go into chests; broadcasts, collection-log entries, KC and PB messages follow. Rewards are computed once per run and paid through item transactions, once per player. | Wiki chest pages; brief §3.12 |
| RC6 | Deaths, wipes and disconnects follow each raid's rules (TFA: tfa.md TFA13-TFA15). | per-raid sheets |

## Framework rules (content/raids-core)

| Id | Rule | Source |
|---|---|---|
| RC7 | A party is per world: leader, ordered members, applicants, leader-only settings; it survives logouts. Starting re-checks requirements for every member, locks membership and scaling (`ScalingContext`), seeds the run's RNG from the world RNG and builds the instance; members enter the first room when it attaches (next tick, instances.md IN6). | brief 3.4-3.7 |
| RC7a | **One party at a time, across raids.** Making or joining a party for one raid (board, entry, or acceptance as an applicant) leaves a still-forming party of another raid, with "You leave your party for <raid>." Only a party of the same raid, or one whose run has started, blocks ("You are already in a party."). Before this, a forgotten TFA party blocked the ToB board without showing anywhere. Our rule: in OSRS the raids' lobbies are far apart, so the case doesn't come up. | the user's report 2026-11-09 |
| RC8 | Rooms follow the definition's slots in order; a slot with several rooms waits for the party's choice, and a choice not on offer is refused. Content starts and clears rooms; the framework records which rooms were taken. | brief 3.3, 3.6 |
| RC9 | Only damage on NPCs spawned for the run counts. Normalised raids (TFA) split each room's contribution by damage share when the room clears; others add damage × multiplier as points. Deaths subtract the raid's death penalty. | brief 3.8; tfa.md TFA12-TFA13 |
| RC10 | Inside a run, a death follows the raid's policy through `DeathPolicyRequest` (deaths outside raids are unchanged): wait in the room with items kept while a teammate lives; with nobody left, a wipe ejects everyone to the exit with every member's unprotected items in graves at the raid's grave tile (members who died earlier lose theirs too), or fails the raid. | brief 3.9; tfa.md TFA14 |
| RC11 | A logout keeps membership; logging back in inside a live run rejoins (to the room's wait tile if dead). If the run is gone the player lands at the saved exit and raid-only items are removed (instances.md IN8). Teleports from inside a run are refused (**OPEN-RC2**: message text). | brief 3.10-3.11 |
| RC12 | Completion computes every member's reward once from a snapshot with the run's RNG and pays it into the raid's claim inventory (persisted with the player; claiming moves items out, so it cannot happen twice). Members who left get nothing. **OPEN-RC3:** a member offline at completion is paid on login while the run lives; after a restart that reward is lost. KC varp, completion time (`m:ss.cc`, 0.6 s per tick) and PB per party size follow. | brief 3.12-3.13 |
| RC13 | A run with nobody inside and online closes after 100 ticks (immediately once it has ended and everyone left): its instance, NPCs and ground items go (IN9) and the party can form again. | brief section 5 |

