# Tempoross

Code: `content/skills/.../Tempoross.kt` (interactions, lobby, HUD, fishing, cooking, cannons, pool), `TemporossGame.kt` (one encounter: the
instanced cove, energy, essence, storm, attacks, fires, tether sites), `TemporossRewards.kt` (permits, reward pool, casket),
`TemporossConfig.kt` (every number; the OPEN ones are placeholders and live only there). Shared skilling rules: skilling.md.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-07:
- (W) "Tempoross", https://oldschool.runescape.wiki/w/Tempoross
- (S) "Tempoross/Strategies" (incl. "Amount of Fish Required - Solo Encounter")
- (RP) "Reward pool" and its templates `Reward pool/Rewards unique`, `.../levels 35-39` to `81+`, `.../other`; (CK) "Casket (Reward pool)"
- (RH) "Raw harpoonfish", (HF) "Harpoonfish", (CH) "Crystallised harpoonfish", (SF) "Fishing spot (Tempoross Cove)", (AC) "Ammunition crate",
  (HC) "Harpoonfish cannon", (F) "Fire (Tempoross Cove)", (TP) "Totem pole", (MA) "Mast (Tempoross)", (SP) "Spirit pool", (RL) "Rope ladder (Tempoross)",
  (SA) "Spirit Angler (Reward pool)", (RS) "Retired sailor", (FB) "Fish barrel", (TB) "Tackle box", (SO) "Spirit angler's outfit", (SK) "Spirit flakes"
- (C) the cache: map square 47,44 (the cove, 3008..3071 x 2816..2879, levels 0 to 2, with every `tempoross_*` loc), locs 40964 to 41356,
  npcs 8643 and 10565 to 10613, interface 437 `tempoross_hud`, varbits 11895 `tempoross_tethered`, 11897 `tempoross_current_points`,
  11936 `tempoross_rewardpermits` (drives the pool multiloc 41356), varp 2934 `total_tempoross_kills`

## Rules

| # | Rule | Source |
|---|---|---|
| TM1 | 35 Fishing to take part. The rope ladder (loc 41305 at 3135,2840) has "Climb", "Quick-climb" and "Solo-start". Boarding puts the player on the ship's deck; the encounter starts 50 ticks after the first player boards or when 40 are aboard; "Solo-start" starts at once with one player. Each encounter is its own instance of the cove (several run at once); the crew arrives on the two ships. | W, RL, C |
| TM2 | The instance is a copy of map square 47,44 (3 levels). Everything in it comes from the cache: ships (level 1), masts, totems, shrines, supply crates, cannons, ammunition crates, pumps, docks. NPCs are spawned on the Wiki / cache tiles: six harpoonfish spots (3 north, 3 south, ids 10565 / 10568), two spirit pools (10570 inactive, 10571 active, 3x3), Tempoross (10572 surfaced, 10574 submerged, 10575 enraged), four crate NPCs (10576 to 10579: "Fill", "Check-ammo"), Captain Pudi (east) and Dudi (west) as instance hosts ("Forfeit" / "Leave"). | W, C |
| TM3 | Energy and essence scale with the crew: about 2.6k energy and 2.5k essence for one player, falling linearly to 1.8k / 1.7k per player at 40 players (hitsplat values x10 internal). A raw or crystallised harpoonfish drains 10 energy, a cooked one 15. | W (Mod Squid), S |
| TM4 | Energy phase: the cannons fire the stored fish and the storm rises; at 0% energy Tempoross submerges, the storm resets to 0 and the spirit pools become active. Essence phase: players harpoon the pools; each scatter takes essence (max hit 12), gives 55 points and restores 3% run energy, "The glowing fish scatter, shedding their magical scales." At 0% essence Tempoross is subdued. If its energy is full again first it re-emerges; this may repeat three times, after which it is enraged ("Tempoross has gathered too much energy. The storm is out of control!") and the storm rises quickly. | W, S, SP |
| TM5 | At 100% storm intensity the encounter is lost: the crew is returned to the Ruins of Unkah and points are lost. | W |
| TM6 | Attacks happen only while Tempoross has more than 10% energy and do no damage. Wave: "A colossal wave closes in..."; then it extinguishes all fires, pushes untethered players a few tiles, destroys their hammer / rope / bucket / bucket of water / harpoonfish / crystallised harpoonfish, and each mast or totem breaks with 15% to 25% (by storm intensity, rolled per site); tethered players are unaffected and cannot move during the wave and get 10 points. Lightning: "A strong wind blows as clouds roll in..." then fires start. Torrent: "A charged torrent of water surges towards a nearby cannon...", then "A cannon is stunned by lightning...": the cannon is electrified (loc 41242 to 41245) for a while and players at its crate are stunned ("The surge of lightning arcs from the cannon into you..."). | W, HC, C |
| TM7 | Tethering: with a rope in the backpack or the full spirit angler's outfit worn, "Tether" on an intact mast (west, east) or totem pole (north, south) ties the player to it (varbit 11895 = 2 / 3 / 1 / 4 draws that site tethered for that player); "Untether" undoes it. A broken site (locs 40996, 40997, 41010, 41011) is repaired with a hammer for 40 points and 4x Construction level xp. | W, TP, MA, SO, C |
| TM8 | Fires are 2x2 (npc 8643 "Douse" over loc 37582). A fire spreads at most twice. Standing in a fire destroys one of the losable items every 2 ticks. "Douse" with a bucket of water turns it into an empty bucket and gives 40 points. Water pumps (locs 41000 on the ships, 41004 on the docks) fill buckets. The crates by the ships hand out hammers, ropes, buckets (1 / 5 / 10 / X) and harpoons. | W, F, C |
| TM9 | Harpoonfish spots: level 35, harpoon (also barbed, dragon, infernal, crystal), roll every 5 ticks; success is 170 to 255 out of 256 from level 1 to 99 (dragon / infernal 178 to 267, crystal 187 to 280), 78.13% at 35. Jumping spots (npc 10569) give a 50% bonus fish after a successful roll. A catch is a raw harpoonfish; the crystal harpoon makes 1 in 3 crystallised, the infernal harpoon cooks 1 in 3. 5 points per fish. Catch xp = floor(450 + 550 (L - 35) / 64) / 100 for levels 35 to 69, floor(890 + 110 (L - 70) / 29) / 100 from 70; the full angler's / spirit angler's outfit adds 2.5%. | RH, SF, W |
| TM10 | Cooking at the shrine (loc 41236, "Cook-at" or raw fish on it): raw harpoonfish becomes harpoonfish in 3 ticks, 10 Cooking xp, never burns, 10 points. | HF, W |
| TM11 | Loading a crate ("Fill" or a fish used on the crate NPC): one fish per tick, cooked first. Points per fish: 65 cooked, 20 raw, 20 crystallised. Fishing xp per fish = catch xp x 10 (x 1.2 for crystallised). Cooked fish drain 15, others 10; the crate looks moderate at 8 stored and overflowing at 15 (locs 40972 to 40979). The spirit pool gives catch xp x 11 per scatter and its success roll uses the harpoon's rates from the base level. | W, AC, CH, S |
| TM12 | Points (max 32,000) turn into permits: none under 2,000; 1 at 2,000 plus 1 per 700 beyond; the remainder / 700 is the chance of one more. The kill count rises for everyone present. Permits are saved (`tempoross.permits`, at most 8,000) and drive varbit 11936, which picks the pool's look. | W, RP |
| TM13 | Reward pool (locs 41296 to 41304; "Net", "Check", "Big-search"): needs a small or big fishing net (the pool's Spirit Angler, npc 10605, "Take-net" gives a small one). "Net" rolls one reward every 3 ticks; "Big-search" five. 10 Fishing xp per reward. Rolls use the base Fishing level. | RP, SA |
| TM14 | One roll: independent uniques (soaked pages 149/8000 for 5 to 9, fish barrel 1/400 and tackle box 1/400 (25 pages instead if owned), big harpoonfish 1/1600, empty tome of water 1/1600, tiny tempor 1/8000, dragon harpoon 1/8000), then one main pick out of 6,400: spirit flakes 1,600 (32 to 64), casket 320, fish 3,600 by base level bracket (35-39, 40-45, 46-49, 50-75, 76-78, 79-80, 81+; weights 900, 810, 720, 630, 540; amounts 30-40, 20-30, 10-20, 7-12, 5-10; noted), junk 880 (plank 280, oak plank 200, seaweed 160, steel nails 80, feather 80, bait 80). | RP templates |
| TM15 | Casket (item 25590, "Open"): armour, jewellery, materials and coins out of 230 (CK table). The Retired sailor (npc 10612, "Exchange") takes a fish barrel, tackle box, empty tome of water or big harpoonfish for 25 soaked pages. | CK, RS |
| TM16 | HUD: interface 437 on the HUD overlay while inside: texts for energy, essence, storm intensity and points, varbit 11897 = points. Leaving, logging out, forfeiting, a win or a loss closes it. | C |
| TM17 | Outside the minigame, the harpoonfish spots stand in the open cove (never move) and can be fished like any spot (no points). The Ruins of Unkah NPCs (Pudi, the pool's Spirit Angler, the Retired sailor, four Spirit Anglers) are spawned at their Wiki pins. | SF, W |

### Tick timeline (an encounter)

- Tick 0: the first player boards; `lobbyEnd = tick + 50` (a solo start skips it). The cove instance is built, the crew is teleported in.
- Each tick (world tick hook): pending events (wave hit, cloud strikes, torrent), fires (spread, item loss), the double spot, then the phase.
- Energy phase: storm +1% every [STORM_TICKS_PER_PERCENT] ticks; each working cannon fires one stored fish every [CANNON_FIRE_TICKS]; first
  attack [ATTACK_FIRST_TICKS] after the phase began, then every 60 to 119 ticks, one of wave / lightning / torrent at random.
- Energy 0: Tempoross submerges (pools active), energy returns over [REGEN_TICKS_TO_FULL] ticks while players scatter essence; full: it resurfaces.
- Essence 0: `subdue`, permits are paid, the hosts become "Leave"; after 33 ticks everyone is returned to the dock.

## OPEN

- **OPEN-TM1:** solo energy. The Wiki says "around 260" (x10), the Strategies table (27 raw / 18 cooked fish for 27 free slots) and "4% left after 17 cooked
  fish" fit about 2,650; `ENERGY_SOLO = 2650`.
- **OPEN-TM2:** how fast the storm intensity rises is not published. 1% per 2 ticks (200 ticks to 100%) fits "load by 93% to 95%" after
  catching, cooking and running 17 to 19 fish; enraged 1% per tick.
- **OPEN-TM3:** how fast Tempoross regains energy while submerged: 75 ticks from empty to full (Strategies: about 18 scatters of 4 ticks).
- **OPEN-TM4:** the pool's damage per scatter is a fixed 120 (the Wiki's maximum hit of 12 x10); the real distribution is not given. "About 24 scatters
  for a solo game" is consistent with it and misses.
- **OPEN-TM5:** cannon behaviour. The Wiki says fish "are loaded" and "fired"; here the crate stores them and the cannon fires one every 2 ticks only while
  Tempoross is surfaced (this reproduces both "load 17 cooked fish, energy at 4%" and "fish loaded before it recovers still count when it
  returns"). Loading is one fish per tick; the order cooked, crystallised, raw follows Strategies; "Check-ammo" and several refusal
  messages are not sourced. A fish used on the crate loads one fish per click (the use-on path does not repeat).
- **OPEN-TM6:** every attack timing and amount: first attack 80 ticks, gaps 60 to 119, equal odds of the three attacks, wave warning 12 ticks and 4
  ticks of stillness, 3 lightning strikes after 8 ticks, fire spread every 10 ticks, torrent delay 4 ticks, cannon disabled 30 ticks, stun 4 ticks
  within 2 tiles of the crate. The wave destroys every losable item (the Wiki says "some"); a fire destroys one item per 2 ticks (Wiki: "items are
  lost every two ticks"). Wave push direction is a random free tile within 3. Construction xp for repairs goes to everyone (Wiki: only house owners).
  The Wiki states the attacks deal no damage, so no `DamageRequest` is used.
- **OPEN-TM7:** the jumping spot: first after 60 ticks, lasting 80, the next 40 after.
- **OPEN-TM8:** positions read off the cache (the ships stand on bridge tiles, so the cache's level 1 ship locs are level 0 here): the lobby deck (3139,2840), the dock below the ladder (3133,2840), the arrival tiles on the
  ships, the hosts' tiles, Tempoross' tile, and the rectangles lightning may strike. The "tile between the ammunition crates" rule (no push, no fires
  there) is not implemented: the cache collision marks that tile as a loc.
- **OPEN-TM9:** soaked pages from the pool are 5 to 9 on the pool's template and 5 to 25 on the Tempoross page (5 to 9 is used). The varbit widths
  were checked in the cache: permits 13 bits (8,000 fits), points 15 bits (32,000 fits), tethered 4 bits.
- **OPEN-TM10:** clue scroll tertiaries of the casket (easy 5/100, medium 3/100, hard 2/100) are not given; the Retired sailor's dialogue is not used.
- **OPEN-TM11:** the HUD bars are drawn by a client script from varps whose names are not in the cache; only the title texts ("Energy: N%", "Essence:
  N%", "Storm Intensity: N%", "Points: N") are written, and their wording is a guess. Lobby HUD (interface 687) is not driven.
- **OPEN-TM12:** unsourced wording: "You catch a raw harpoonfish.", "You have no raw harpoonfish to cook.", "You need a rope to tether yourself.",
  "You need a hammer to repair this.", "A colossal wave washes you away." and the other refusals. The ones quoted in TM4 and TM6 are the Wiki's.
- **OPEN-TM13: not implemented.** Spirit flakes' double-catch effect and their use at Gita Prymes (outfit upgrade) and the heron; fish barrel and tackle box
  behaviour (filling, emptying, the interface); tome of water charging; fish sack barrel; the dragon / infernal / crystal harpoon special attacks and Humidify;
  barehanded fishing and the barb-tail harpoon as a "no harpoon" case; the 20-tick "leave" options on the cannoneers; Spirit Angler stats board and
  personal bests; followers and pets in the encounter; items that cannot be set up on the ship; dying in the encounter; Flakes 'n' Flotsam beyond the
  existing shop row; the Tempor Tantrum Sailing trial.
- **OPEN-TM14:** `content/world/.../npc-spawns.tsv` places Tempoross npcs (10570, 10571, 10576 at 3047,2849; 10612, 10613 and others at Lumbridge-area tiles) from
  an import that matched ids to the wrong places; they spawn real-world copies that this plugin ignores (the pool NPC refuses outside an encounter).
  That file is outside this change.
