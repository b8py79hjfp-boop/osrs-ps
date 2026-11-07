# Pyramid Plunder

Sources: Wiki pages "Pyramid Plunder", "Urn (Pyramid Plunder)", "Grand Gold Chest", "Sarcophagus (Pyramid Plunder)", "Tomb Door",
"Tomb Door (exit)", "An anonymous looking door", "Speartrap", "Guardian mummy", "Scarab Swarm", "Mummy (Pyramid Plunder)",
"Jalsavrah Pyramid" (https://oldschool.runescape.wiki/w/Pyramid_Plunder and the pages above); the cache (map square 30,69, loc and
varbit configs, interface 428 `ntk_overlay`). Code: `content/skills` `PyramidPlunder.kt`, `PyramidPlunderData.kt`,
`pyramid-plunder-locs.tsv`. The eight rooms are the shared map square 30,69 level 0 (no instances): all state is per player in
varbits, which the client reads through the loc multi configs, so the same room looks different to each player.

| # | Rule | Source |
|---|---|---|
| PP1 | Requirements: 21 Thieving to start, +10 per room up to 91, base levels (not boostable). Starting is the Guardian mummy's op 3 "Start-minigame" (cache npc 1779); the rooms are entered through his lobby. | Wiki "Pyramid Plunder" Requirements |
| PP2 | The pyramid has four doors (north, east, south, west: cache `ntk_pyramid_door_*_multi`, op "Search"). One leads to the Guardian mummy's lobby, the other three to empty identical rooms. The right door is re-rolled every 25 minutes (2,500 ticks), the same for all players. The right door needs 21 Thieving (success roll low 160 / high 250); the others open at any level. A failed try stuns and deals 1-4 damage with "A mysterious bandaged hand punches you away from the door.  Ouch!". Leaving always puts the player outside the north door. | "An anonymous looking door", "Pyramid Plunder" Guide |
| PP3 | A game lasts five minutes (500 ticks of 0.6 s) from the start; at the end, or when the player leaves by an exit door, dies, teleports or logs out, they are put outside and the swarms and mummies they woke up are removed. | "Pyramid Plunder" Gameplay; "Scarab Swarm" (changes 24 September 2015) |
| PP4 | Eight rooms in the order of the Wiki (urn and sarcophagus map pins; the third room has 12 urns, the others 13; the third room sees the Keldagrim copy at the top of the map). Each room has 13 urns (12 in room 3), one grand gold chest, one sarcophagus, four tomb doors (cache locs `ntk_tomb_door1..4` with varbits `ntk_door1..4_state`), a speartrap and two exit doors. The player arrives inside the exit door whose corridor holds the speartrap. | Wiki; cache map square 30,69 (OPEN-PP2) |
| PP5 | Speartrap ("Pass"): a roll with low 160 / high 250; success gives 10 Thieving xp and the player walks past it; failure deals 1-4 damage. | "Speartrap" |
| PP6 | Urns: "Search" rolls low 100 / high 180 (130 / 220 once a snake is found and uncharmed); failing is a snake bite for 1-4. "Check for Snakes" rolls 160 / 250 and on success shows the snake ("You find a snake. It stares at you menacingly.", check xp); "Charm Snake" (a snake charm, cache `snake_flute`) always works and the next search always succeeds. Every chance falls by 1/256 per room after the first. Xp for a search is the urn xp (60, 90, 150, 215, 300, 450, 675, 825); checking first gives a third of it and searching the remaining two thirds. An urn gives one artefact of its room's range. | "Urn (Pyramid Plunder)", "Pyramid Plunder" Experience |
| PP7 | Grand gold chest ("Search"): opens at once; the Thieving roll (low 130 / high 220) failing is the trap: a level 98 scarab swarm (cache npc 1782) appears, 3 damage, no xp, the artefact is still given. Sceptre rolls per chest search and per sarcophagus attempt (even a failed one) at 1 in 4,200 / 2,800 / 1,600 / 950 / 800 / 750 / 650 / 650 by room. Sarcophagus ("Open"): repeats attempts until the Strength roll (low 80 - room Thieving level / high 270 - room level) passes, then gives Strength xp (20 .. 275) and with chance room / 9 wakes a level 84 mummy (cache npc 7658); "Search" on the open one gives the artefact. The swarm and the mummy only attack the player who woke them. | "Grand Gold Chest", "Sarcophagus (Pyramid Plunder)", "Scarab Swarm", "Mummy (Pyramid Plunder)" |
| PP8 | Finding the sceptre puts the player outside at once; the uncharged Pharaoh's sceptre (cache `pharaohs_sceptre`) goes to the backpack or, if full, the ground outside. | "Pharaoh's sceptre", "Pyramid Plunder" Sceptre |
| PP9 | Tomb doors: roll low 130 / high 220 (lockpick 160 / 250), minus 1 per room after the first; xp 40, 60, 100, 140, 200, 300, 450 (halved with a lockpick in the backpack). One door per room leads on (the "correct" ones are the same for all players and re-rolled for all rooms whenever a game starts); wrong doors have no consequence. After three wrong doors the fourth always lets the player through. In room 8 the doors leave the pyramid. Going on needs the next room's Thieving level. | "Tomb Door", "Pyramid Plunder" Gameplay |
| PP10 | HUD: interface 428 (`ntk_overlay`) in the HUD overlay slot; varbits `ntk_player_timer_count` (ticks since the start), `ntk_room_number` (1..8), `ntk_thieving_required` (the room's level) and `ntk_current_room_level` (room number). | cache varbits 2375..2377, 2364; RuneLite's Pyramid Plunder plugin uses the same varbits |

## Added OPEN

- **OPEN-PP1: HUD varbit meanings.** The cache only names the varbits. The timer unit (ticks since the start is used; seconds would
  fit the 10-bit field too), whether `ntk_thieving_required` shows the current or the next room's level, and what
  `ntk_current_room_level` holds (the room number is used) are unverified against a real client.
- **OPEN-PP2: room numbering and arrival tiles.** The cache does not number the rooms. The order (map components 1, 2, 3, 4, 5, 8,
  10, 9 of the map square, by flood fill) follows the Wiki's urn and sarcophagus map pins, the 12-urn third room and the
  Keldagrim copy remark. The tomb door pins list another order (1, 4, 8, 10, 9, 3, 5, 2); the two cannot both be right. The
  arrival tile (inside the exit door in line with the speartrap) and the tile past the trap are derived from loc geometry;
  walking past uses the router and falls back to a teleport.
- **OPEN-PP3: lobbies.** The cache has four antechambers (levels 2 and 3, `ntk_antechamber_exit`); which pyramid door leads to which
  is not recorded (the right door always leads to the first, the others to the rest in order), and the Guardian mummy's tile is
  not in the spawn data (placed 4 tiles inside the first lobby). His "Talk-to" dialogue is the generic NPC dialogue table's.
  Not done: the annoyed mummy's overhead texts when the sceptre is found, the sceptre's charging and teleports, the lobby's
  sarcophagus, the Wiki's "Rocky" pet (no pet system), selling artefacts to Simon Templeton.
- **OPEN-PP4: damage not given.** The Wiki says "walking past the trap without disarming it" hurts, "increasing with each room",
  without numbers; walking over a trap is not modelled. Snake bites also "poison the player for the same amount": plugin-api
  has no poison request (combat's `Poison` is internal), so the bite only hits. The scarab swarm's chest damage is 3 on the chest
  page and 1-4 on the main page (3 is used).
- **OPEN-PP5: urn loot.** "Unconfirmed which urns drop which artefacts and at what rate" (Wiki): an urn gives one of the artefacts
  of its room's range with equal chances. Chest and sarcophagus tables are the Wiki's.
- **OPEN-PP6: success chart shifts.** The 1/256 per room fall is stated for urns and doors only; it is applied to those, not to
  the chest, trap or the sarcophagus (whose chart already depends on the room). Chest and speartrap use the same chart in all
  rooms.
- **OPEN-PP7: stun and action lengths.** Not in the Wiki for this minigame: a stun is the pickpocket animation and 5 ticks,
  urn / door / trap actions take 2 ticks, a sarcophagus attempt 4. The sarcophagus' "opening" push state is not shown.
- **OPEN-PP8: texts.** Only "You find a snake. It stares at you menacingly." and the bandaged hand text are the Wiki's; every
  other message (bites, charming, empty urn, time up, sceptre found, wrong door) is ours.
- **OPEN-PP9: loc ids.** The client reports either a multi loc's parent or the child it shows (as for house portals); the
  parents and all children are registered, the room's loc is found by its origin tile (`pyramid-plunder-locs.tsv`).
- **OPEN-PP10: the mummy.** The cache has five mummy versions (7658..7662) without a rule for which a room uses: the first is
  used. The scarab swarm and the mummy are aggressive through the normal NPC hunt (content/combat `Aggression`); an
  `AttackCheck` denies every player but the one who woke them.
