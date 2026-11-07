# Sailing: shipwreck salvaging

Skeleton for milestone S6. Research: docs/plans/sailing.md section 5.2. Nothing is implemented; the cache has no wreck data
(db 202 / 203 are empty client-side), so S6 depends on Wiki data that is not imported yet.

Sources: OSRS Wiki https://oldschool.runescape.wiki/w/Shipwreck_salvaging, /Crew_Management, /Sailing_training, /Salvaging_hook
(facility tiers in /Facilities); cache 2757 salvage objs `SAILING_*_SHIPWRECK_SALVAGE` 32847-32861, `SALVAGING_RARE_*` 32863-32873,
varp `SAILING_SHIPWRECK_RAREITEMS` 5208; hook stats db 176 (subtype 1).

## Rules

| # | Rule | Source |
|---|---|---|
| S1 | Seven salvaging hook tiers (bronze 15 .. dragon 86; Construction 1, 9, 18, 30, 52, 66, 78); crew deckhandiness cap 1, 1, 1, 2, 2, 3, 4. | cache db 176 subtype 1; Wiki Facilities, Crew Management |
| S2 | Range 7 tiles from the edge of the wreck, measured from the hook's position. Player cycle 4 ticks (the 5 Dec 2025 hotfix restored 4 after a one-day 5); crew cycle 5 ticks. | Wiki Shipwreck salvaging, Sailing training |
| S3 | Wreck types (level, salvage XP, sorting XP, average life): small 15 (10, 5.5, 1 min), fisherman's 26 (17, 9, 3 min), Barracuda 35 (31, 15.5, 3 min), large 53 (48, 24, 3 min), pirate 64 (76, 31.5, 3 min), mercenary 73 (138, 63.5, 3:15), Fremennik 80 (162, 75, 3:40), merchant 87 (not captured yet). | Wiki Shipwreck salvaging |
| S4 | Crew salvage XP is 10 / 20 / 30 / 40% of the player's by deckhandiness 1..4 (3 and 4 reduced from 30 / 40 on 5 Dec 2025 as listed on the Wiki). | Wiki Crew Management |
| S5 | A cluster shares a spawn pool (typically 4 of 6 sites active); the despawn timer starts when anyone starts to salvage; salvaging is refused in hazardous waters. Salvage is sorted at a salvaging station (port or boat facility) for sorting XP and items. | Wiki Shipwreck salvaging |

## OPEN

- **OPEN-SV1:** wreck cluster positions and site lists (Wiki map pins) are not imported; `tools/wiki-import/sailing.py` needs a
  salvaging extension when S6 starts.
- **OPEN-SV2:** merchant wreck row, success rates per tier and loot tables.
- **OPEN-SV3:** exact despawn timer distribution (the Wiki gives averages only).
