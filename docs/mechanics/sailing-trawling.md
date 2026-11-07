# Sailing: deep sea trawling

Milestone S7: NOT implemented (blocked, see "Why not"). Research: docs/plans/sailing.md section 5.4. The shoal tables (db 190-192) are
empty in the shipped cache.

Sources: OSRS Wiki https://oldschool.runescape.wiki/w/Deep_sea_trawling, /Facilities (net rows), /Sailing; cache 2757 locs
`SAILING_SHOAL_CLICKBOX_*` 59734-59742, nets 59755-59766, fathom 59767-59768, inventory `SAILING_TRAWLING_NET` 968, objs `FISH_CRATE_*`
32366-32385, varp / varbits for net depths 19206-19209.

## Rules

| # | Rule | Source |
|---|---|---|
| T1 | Nets: rope (Sailing 56, Con 45), linen (65, 61), hemp (76, 65), cotton (84, 73). Not buildable on rafts. Max fish per catch 2 / 3 / 4 / 5; deckhandiness needed 1 / 2 / 3 / 4; shared capacity 250 fish. A stray cache row gives linen Con 53; use 61. | Wiki Deep sea trawling; cache db 176 subtype 10 |
| T2 | Sea depths: shallow, moderate, deep; hemp and cotton nets cover deeper water. A wrong depth halves the catch and the token XP. | Wiki Deep sea trawling |
| T3 | Skilling roll every 3 ticks (player, passive), 2 ticks if the player clicks every tick, 5 ticks for crew (gather rate 4 to 3 on 20 May 2026). Token Sailing XP per roll 7 / 9 / 11 / 15 by net tier, plus Fishing XP. | Wiki Deep sea trawling |
| T4 | Bait (chum station / spreader) gives +20% fish per catch, one offcut per stop, per player since 15 Jul 2026. Trawler's trust ale +5% fish. | Wiki Deep sea trawling |
| T5 | Shoals follow fixed patrols with stops; the fathom stone shows direction, the fathom pearl a hint arrow; disengage after 5 minutes of inactivity (15 Jul 2026). | Wiki Deep sea trawling |

## OPEN

- **OPEN-TR1:** shoal patrol paths, speeds, stop tables and drop tables (db 190-192 stripped; the Wiki gives per-shoal pages).
- **OPEN-TR2:** fish per catch distribution and fishing-rod alternative rates (the Wiki states about -2% every 5 ticks).
- **OPEN-TR3:** pets: Soup 1/360,000 per roll, Heron odds not captured.

## Why not (S7 handed back)

What the cache and Wiki give: the nets (locs 59755-59766, ops Operate / Raise / Lower / Collect-from / Modify; inventory 968), the shoal clickbox locs 59734-59742
(6 x 6, op 5 "Rod-fish", never placed in the map), the fish and crate objs, the net / chum / locator facilities (already buildable), per-shoal stop
time (90 s, 72 s, 60 s, 48 s, 42 s, 30 s), depth set, bait, Fishing level, the fish and the Fishing XP per catch (112.5 for giant krill), mixed
shoal rules and the rare fish odds (Wiki Deep sea trawling, shoal pages).

What is missing, so that no catch could be rolled without inventing it:
1. **Shoal patrols** (OPEN-TR1): the paths, speeds, stop points and which stop a shoal is at when. The Wiki shows one map pin per area and says the movement
   is predetermined. Needed: per shoal type a list of (tile, stop) waypoints and a speed in tiles per tick.
2. **Catch chance** (OPEN-TR2): the Wiki gives no success rate for a roll, only "level and net" shape and that depth halves the quantity. Needed: the
   `statRandom` low / high pair per shoal.
3. **An entity that moves on the sea and can be clicked**: the shoal is a 6 x 6 loc. plugin-api has no moving loc and no loc teleport; an NPC cannot
   path on water. Proposal (engine, main session): `WorldRef.moveLoc(id, from, to)` or a world entity type for shoals that `onWorldEntityTick` can move with
   `WorldEntityRef.move`; and `WorldRef.locAt(tile, shape)` (EntityRef has it, WorldRef does not).
4. The Sailing Options side-panel varbits for the net depths (19206-19209) and the fathom stone / pearl hint arrow need the engine's hint-arrow support.

With 1-3 the rest is table work in the style of `Salvaging.kt`: net ops on the deck locs, a depth state per net, the catch roll every 3 ticks (2 with a
click each tick, 5 for crew), 250-fish shared capacity, bait use, trust-ale extra fish.
