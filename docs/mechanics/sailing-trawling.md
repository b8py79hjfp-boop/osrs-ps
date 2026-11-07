# Sailing: deep sea trawling

Skeleton for milestone S7. Research: docs/plans/sailing.md section 5.4. Nothing is implemented; the shoal tables (db 190-192) are
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
