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
