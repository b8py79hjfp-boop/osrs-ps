# Sailing: crew (NPC crewmates)

Skeleton for milestone S9 (needs NPCs on decks, OPEN-WE2). Research: docs/plans/sailing.md section 5.5. `content/sailing` reads
`Sailing_Crew` (db 173) into `CrewMember`.

Sources: cache 2757 db 173 (`dbrows 173`), NPCs `SAILING_CREW_*` 15244-15350; OSRS Wiki https://oldschool.runescape.wiki/w/Crew_Management,
/Sailing, /Cargo_hold, /Mooring_point.

## Rules

| # | Rule | Source |
|---|---|---|
| C1 | Ten crewmates, stats (Helmsmanship / Privateering / Deckhandiness) from the cache by `UNIQUE_ID`: 1 = 2/1/3, 2 = 3/2/1, 3 = 1/4/1, 4 = 1/1/4, 5 = 4/1/1, 6 = 2/4/2, 7 = 3/3/2, 8 = 4/2/2, 9 = 3/2/3, 10 = 2/2/4. Names and levels by matching the triples against the Wiki (all unique): Jobless Jim 40 (id 1), Adventurer Ada 55 (2), Jittery Jim 70 (3), Jolly Jim 85 (4), Sailor Jakob 95 (5), Oarswoman Olga 65 (6), Bosun Zarah 80 (7), Spotter Virginia 90 (8), Ex-Captain Siad 50 (9), Cabin Boy Jenkins 60 (10). The id-to-name map is derived [D], not in the cache. | cache db 173; Wiki Crew Management |
| C2 | Crew stat requirement tuples `(stat, level)` on helms and facilities use stat 1 helmsmanship, 2 privateering, 3 deckhandiness (sloop helms need helmsmanship 3; cannons privateering 1-4; hooks deckhandiness 1-4). Derived by matching the Wiki's tier tables [D]. | cache db 176, 181; Wiki Crew Management |
| C3 | Crew size by Sailing level: 40 -> 1, 55 -> 2, 70 -> 3, 85 -> 4, 95 -> 5 (not boostable); boat capacity raft 0, skiff 2, sloop 5. Recruiting is a conversation on land; no cost; the Crew Registrar (The Pandemonium, Port Roberts, Barracuda starts) assigns slots. | Wiki Crew Management, Sailing |
| C4 | Jobs: idle, sails, helm, helm and sails, salvage, cargo, cannon, repairs, chum, trawl. Helmsmanship: helm and trim (trim XP 40 / 50 / 60 / 70% of full for 1..4); privateering: cannon max-hit multiplier `(P + 13) / 20`; deckhandiness: hook XP 10 / 20 / 30 / 40%, repairs, crates carried (3-4 carry two). A crewmate operates a facility only if the player has its level (7 Jan 2026). | Wiki Crew Management |
| C5 | At a port with a bank deposit box the first crewmate banks the hold (deckhandiness >= 2 unless Whirlpool surprise). Whirlpool surprise raises helmsmanship and deckhandiness to at least 2. Crew panic and cancel orders in hazards; they remember tasks across logout. | Wiki Crew Management, Mooring point |

## OPEN

- **OPEN-CR1:** recruitment quest gates (Ex-Captain Siad: The Tourist Trap; Cabin Boy Jenkins: Dragon Slayer I, Restless Ghost and
  ghostspeak; Oarswoman Olga: In Aid of the Myreque; Bosun Zarah: Royal Trouble and Hard Fremennik diary; Spotter Virginia: 100
  Tempoross subdues) come from the Wiki; the cache columns are stripped.
- **OPEN-CR2:** world NPC ids and order strings (db 173 `WORLD_NPC`, `ORDER_STRING_*`) are server-only; dialogue and strings must be
  written from the Wiki and RuneLite observation.
- **OPEN-CR3:** NPC pathing on a deck and engine support for NPCs on decks (OPEN-WE2).
