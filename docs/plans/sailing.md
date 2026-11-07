# Sailing - research and implementation plan

Status: research document, no code. Scope: the Sailing skill (OSRS release 19 November 2025) for the rev-242 server.
Written from (a) the OSRS Wiki (every fact tagged with a source key, URLs in section 9), and (b) the rev-242 cache
(`.data/cache/242/disk`, symbols from `symbols/build/generated`, db tables dumped with `tools/cache-dump`,
disassembled client scripts in `/tmp/claude-0/ge-research/scripts`). Where wiki and cache disagree the cache wins for
anything the client reads, and the difference is listed in section 8.

## 0. Reading guide and provenance

- **The cache is a client cache.** Server-only db columns were stripped before shipping (verified: see 6.5). So the
  following are NOT in the cache and must come from the Wiki (`tools/wiki-import`) or be derived: port task XP and
  coin rewards, shoal data (all 6 shoal rows and 16 spawn rows are empty), shipwreck data (all 8 + 29 rows empty),
  chance-encounter weights, Barracuda trial start tiles/boundaries, dock land/sea coordinates, boat deck origin/entry
  (`Sailing_Boat.ORIGIN/ENTRY/ENTITY_DATA`), acceleration and turn speed (`DIRECTION16_ACCELERATION`,
  `DIRECTION16_TURNSPEED`), hazard areas, NPC boat stats.
- **Wiki date.** The Wiki describes the live game up to September 2026, i.e. after many Sailing balance changes. The
  rev-242 cache already contains the 13-hotspot sloop (added 18 March 2026 [W:Sailing]), so the cache is not the
  November 2025 launch state. The Wiki's current numbers are therefore the right default; each balance change that
  affects a number is dated in the tables where it matters.
- Tags: `[W:x]` Wiki page x (section 9), `[C:db N]` cache db table id N, `[C:sym]` generated symbols, `[C:cs2 N]`
  client script N, `[C:map]` cache map (`find-locs` / `map` / `collision`), `[D]` derived by me from the above (the
  derivation is stated), `OPEN` unresolved.

## 1. Overview and progression

### 1.1 What Sailing is

Members-only skill, only skill exclusive to OSRS, released 19 Nov 2025; training requires completing the
**Pandemonium** quest [W:Sailing]. The player captains a boat (a world entity, see `docs/mechanics/world-entities.md`)
which they own (up to 5 at level 91), steered from a helm, with a deck on which they and guests walk. XP comes from the
activities in 1.4. The skill replaces the Combat Options tab with the *Sailing Options* side panel while on a boat
[W:Sailing][W:Opts]. The game boundary is a "world border" where sailing past it is refused with a message
[W:Sailing][W:Haz]. Members only: on a free-to-play world a lapsed member's boat is moved safely to its last docking
point [W:Boat]. A varbit `CONTENT_RESTRICT_SAILING_SERVERSIDE` (18166) exists in the cache; its purpose is OPEN-S3.

### 1.2 Pandemonium (quest 174, novice, short, 1 quest point)

Start: talk to **Will** or **Anne** on the dock east of the Food Store in Port Sarim (start tile 3025,3209) [W:Pandemonium].
No skill or quest requirements; hammer and saw are provided during the quest.

| Step | Content | Source |
|---|---|---|
| Job interview | Will and Anne take the player aboard *their* boat (cache boat type 3, "Will and Anne's boat", Small 2x5) and put the player at the helm. Sail south through the Bay of Sarim to the pirate wreck east of the Karamja Shipyard (3031,3035). | [W:Pandemonium] |
| Wreck | Anne asks to lower the salvaging hook; the player gets a *mysterious medallion* and an *old cup*; Will takes the medallion, Anne pushes the player overboard. Varbit `SAILING_INTRO_REACHED_WRECK` 18315. | [W:Pandemonium][C:sym] |
| The Pandemonium | Player washes up on the island. Talk to 'Squawking' Steve Beanie, then Ribs in the pub (tale of the Crimson Kraken, Barnacle Barnaby), Steve again (mentions Old Grog). | [W:Pandemonium] |
| Raft | Junior Jim on the second docked ship takes the old cup and gives a **raft**; takes the player to the shipyard. Pick up hammer and saw from the crates; board the gangplank, **build a cargo hold** in the raft's single facility hotspot, disembark. Jim gives the **Captain's log**. | [W:Pandemonium] |
| First cargo | Board the raft at the NE gangplank, navigate north to Port Sarim, **dock using the buoy** south of the southernmost dock, disembark by the north gangplank, take the crate from the ledger table at the west end of the dock (weapons must be unequipped), board, deposit the crate in the basic cargo hold. Varbit `SAILING_INTRO_RECEIVED_REPAIRKITS` 18316 (the 2 repair kits). | [W:Pandemonium][C:sym] |
| Delivery | Sail back, park inside the buoy perimeter, withdraw the crate, disembark by the NW gangplank, deposit on the ledger table, talk to Jim; talk to Steve; he introduces Old Grog. Quest complete. | [W:Pandemonium] |

Two special courier tasks belong to the quest: cache `Port_Task` 599 "Pandemonium ship part delivery" (level 1, Port
Sarim -> The Pandemonium) and 600 "Pandemonium pirate looty delivery" (level 12, used by Prying Times) [C:db 197][W:Courier].

**Rewards:** 1 quest point; 300 Sailing XP (+100 XP when the cargo is delivered = 400 total); access to Sailing and The
Pandemonium; a **raft**; 25 sawmill coupons (wood plank); 2 repair kits; a spyglass [W:Pandemonium]. Since 27 Mar 2026
trimming sails on Will and Anne's boat gives no XP [W:Pandemonium].

Cache pieces: db `Quest`; varp `SAILING_INTRO_PRIMARY` 4959; varbits `SAILING_INTRO` 18314, `_REACHED_WRECK` 18315,
`_RECEIVED_REPAIRKITS` 18316; interface `SAILING_INTRO_HUD` 345 (scripts 627-629, 1487-1489, 2372, 2457-2458,
4735-4737, 6361 reference it) [C:sym][C:cs2]; NPCs `SAILING_INTRO_WILL_*` 14957-14961, `SAILING_INTRO_ANNE_*`
14962-14966, `SAILING_INTRO_RIBS` 14970-14971, `SAILING_INTRO_GROG` 14976-14977, `PANDEMONIUM_*` 14866-14871, Captain
Anne `SAILING_CAPN_ANNE01` 14878, `SAILING_YANN01` 14877; locs `SAILING_INTRO_WRECK` 58208,
`SAILING_INTRO_NAVIGATING` 59620, `SAILING_INTRO_HELM` 59621/59622, `SAILING_BOAT_SAILS_INTRO` 59553,
`SAILING_BOAT_MAST_INTRO` 29505; objs `SAILING_INTRO_MEDALLION` 31337 + 2 more; boat bottles/deeds `SAILING_DEED_RAFT`
31993 [C:sym].

### 1.3 Level 1 -> 99 unlock table

Condensed from [W:LevelUp] (cross-checked against cache facility rows, 4.4). "Con" = Construction level needed to build
it (boostable unless noted). Levels for boats, boats owned, port-task slots, crew size, docking, Barracuda Trials and
the first/extra crew slots are **not boostable** [W:Sailing][W:Boat][W:Courier][W:Crew][W:BT].

| Sailing | Unlocks |
|---|---|
| 1 | Raft; 1 boat; dock Port Sarim and The Pandemonium; port tasks (courier) there; 1 task at a time; charting: Bay of Sarim, Kharidian Sea, generic (Captain's log) and spyglass tasks; basic cargo hold; wooden hull, bronze helm, wooden mast and linen sails, bronze keel |
| 5 | Dock Land's End, Hosidius (must have visited Kourend); lost wooden crates |
| 7 | 2 port tasks at a time |
| 10 | Dock Musa Point; chart Lumbridge Basin |
| 12 | Prying Times (Smithing 30); crate charting (crowbar); Mudskipper Sound at 13 |
| 15 | **Skiff**; 2 boats; dock Port Piscarilius; salvage small shipwrecks; bronze salvaging hook |
| 16-18 | Range (Con 6) 16; iron helm 17 (Con 14); dock Rimmington and oak cargo hold 18 (Con 11) |
| 20-22 | Dock Catherby, oak hull (Con 8) 20; iron salvaging hook 21 (Con 9); Current Affairs (Fishing 10), current-duck charting, iron keel (Con 17) 22 |
| 24-28 | Oak mast and linen sails 24 (Con 11; partial storm resistance); dock Brimhaven, flags/trims (Con 20), lost oak crates 25; fisherman's shipwrecks 26; steel hook 27 (Con 18); dock Ardougne, **3 tasks**, bronze cannon (Con 21) 28 |
| 29-34 | Teak cargo hold 29; dock Port Khazard, **bounty tasks**, **Barracuda Trials: The Tempor Tantrum** 30; teak hull 31; keg 33 (Con 25); dock Witchaven 34 |
| 35-40 | Barracuda shipwrecks, iron cannon 35; dock Entrana, teak mast and canvas sails 36; anchor 37; dock Civitas illa Fortis, sea-depth charting, steel helm 38; steel keel 39; dock Corsair Cove and Dognose Island, inoculation station (Con 37), **1 crewmate** (Jobless Jim) 40; Crab Quest |
| 41-47 | Recolour sails 41 (Con 27); dock Cairn Isle, Chinchompa Island, salvaging station 42 (Con 34); mithril hook 44; Troubled Tortugans, The Summer Shore, Little Pearl 45; dock Aldarin, Vatrachos, mahogany cargo hold 46; dock Onyx Crest, steel cannon 47 |
| 48-54 | Ruins of Unkah, mahogany hull 48; Shimmering Atoll 49; **Sloop**; 3 boats; Void Knights' Outpost, Port Roberts, ballistic attractor 50 (Animal Magnetism); Anglers' Retreat 51; The Red Reef, mahogany mast 52; large shipwrecks, wind catcher 53; Minotaurs' Rest, mithril keel 54 |
| 55-60 | Isle of Souls, **The Jubbly Jive**, mithril helm, teleport focus, **2 crewmates** 55; dock Isle of Bones, **4 tasks**, rope trawling net, chum station 56; weather charting, mithril cannon 57; Laguna Aurorae 58; adamant hook 59; Charred Island, camphor cargo hold, Cabin Boy Jenkins 60 |
| 61-66 | Tear of the Soul (boat renaming) 61; Rellekka, Wyrmscraig, Fallen From Grace 62; Wintumber, bosun's workbench 63; Crown Jewel, pirate shipwrecks 64; Etceteria, linen trawling net 65; Port Tyras, Lledrith, adamant keel 66 |
| 67-72 | Deepfin Point, camphor hull 67; Jatizso, Neitiznot, camphor mast, advanced chum station 68; Rainbow's End, adamant cannon 69; Prifddinas, fathom stone, **3 crewmates** 70; **The Gwenith Glide**, Sunbleak Island, adamant helm 72 |
| 73-80 | Ynysdail, mercenary shipwrecks, crystal extractor 73; Waterbirth, rune hook 74; Piscatoris, greater teleport focus 75; Lunar Isle, Buccaneers' Haven, hemp trawling net 76; **4 boats**, eternal brazier 78; Drumstick Isle, gale catcher 79; Weiss, Fremennik shipwrecks, ironwood cargo hold, rune cannon 80 |
| 81-99 | Ironwood hull 81; chum spreader 82; ironwood mast 83; cotton net 84, **5 tasks** 84; rune keel, **4 crewmates** 85; dragon hook 86; Grimstone, merchant shipwrecks, rune helm 87; rosewood cargo hold 89; Spotter Virginia 90; **5 boats**, fathom pearl 91; dragon cannon 92; rosewood hull 93; rosewood mast 94; **5 crewmates** (Sailor Jakob) 95; dragon helm 96; dragon keel 97; skillcape 99 [W:LevelUp] |

Quests with Sailing requirements: Prying Times 12, Current Affairs 22, Crab Quest 40, Troubled Tortugans 45, The Red
Reef 52, Fallen From Grace 62 (XP rewards 800 / 1,400 / 10,000 / 10,000 / 15,000 / 12,500) [W:Sailing]. These are
separate quest work items (S12), not part of the skill core.

### 1.4 XP sources (what to implement, with the numbers known)

| Source | XP | Source |
|---|---|---|
| Trimming sails | 10.5 / 19.5 / 30 / 48 / 64 / 80 / 125 per trim by sail tier (wooden..rosewood); with a wind catcher -25% and a wind mote is created (release = 40 XP, gale catcher 70) | [W:Mast][W:Sailing] |
| Ocean encounters | castaway 5x level on pickup, 10x level more on delivery; mysterious glows increasing; see 5.6 | [W:Enc] |
| Sea charting | one-off per task: 35 generic, 50 spyglass, 75 crate, 125 current, 175 diving, 250 weather; sea and ocean completion bonuses; full 358 tasks +100,000; total 691,270 | [W:Chart] |
| Courier / bounty | per task, see 4.3 | [W:Courier][W:Bounty] |
| Salvaging | 10-162 per salvage + 5.5-75 sorting, see 5.2 | [W:Salv] |
| Trawling | token Sailing XP per skilling roll (7/9/11/15 per net tier at correct depth) + Fishing XP | [W:Trawl] |
| Barracuda Trials | completion XP + one-off rank bonus, see 5.3 | [W:BT] |
| Cannon hits | scaled by damage dealt | [W:Sailing] |
| Quests | Pandemonium 300(+100) ... see 1.3 | [W:Sailing] |
| Construction | boat parts give Construction XP, not Sailing (e.g. wooden hull raft 19, dragon helm 780) | [W:Raft] |

## 2. Boats

### 2.1 Types, sizes, world-entity configs

Cache `Sailing_Boat` (db 166) has 4 rows [C:db 166]; the world-entity configs are archive 2 group 72 (`config
worldentity --all`: 14 configs). There is **no column linking the two in the cache** (`ENTITY_DATA` is server-only), so
the mapping below is by bounds (fine units, 128 per tile) and category [D].

| Boat | db row (type) | Sailing | Player cap | Crew cap | Hotspots (cache `FACILITY_AMOUNT`) | Recovery cost (cache) | World entity config | Bounds (w x l, fine) | Evidence |
|---|---|---|---|---|---|---|---|---|---|
| Raft ("Tiny 1x3") | 8110 (0) | 1 | 2 | 0 | 1 | 250 | **1** | 128 x 384 (1x3) | category 2395 |
| Skiff ("Small 2x5") | 8111 (1) | 15 | 5 | 2 | 7 | 3,750 (Wiki: 4,125) | **2** | 256 x 640 (2x5) | category 2395 |
| Sloop ("Medium 3x10") | 8112 (2) | 50 | 10 | 5 | 13 (Wiki infobox 11; the Wiki's Boat table says 9; 2 slots were added 18 Mar 2026) | 50,000 | **3** | 384 x 1280, offset -256 (3x10) | category 2395 |
| Will and Anne's boat ("Small 2x5") | 8113 (3) | 1 | 5 | 2 | static facility only | 3,750 | 10 or 11 (OPEN) | 256 x 640 | `TEMPORARY_BOAT`=1 |

Other configs (all OPEN as to exact use; no content decodes them yet) [C:db 195 empty rows][D]:

| Config | Bounds | Name / ops | Likely use |
|---|---|---|---|
| 4 | 512 x 512, no op2, op23=1 | none | OPEN (square, maybe the shipyard waterplane or a trial prop) |
| 5, 6, 7, 8 | 384 x 1152 (3x9) | "Ship", "Charter ship", "Pirate ship", "Strange ship" (flag14) | Large NPC ships (`Sailing_Npc_Boat` has 17 rows; NPC pirate/Kandarin large templates in 2.4) |
| 9 | 0 x 0 | "The Zenith", op "Board" | quest/story ship |
| 10, 11 | 256 x 640 | none | skiff-size boats other than the player's: Will and Anne's boat and Barracuda trial-master boats |
| 12 | 384 x 1280 | none | sloop-size non-player boat (trial-master / NPC) |
| 13, 14 | 384 x 1280 | "The Bark of the Bight", "The Four of a Kind", op2 "Attack" | enemy pirate ships (boat combat), category 2439 |

Boats a player may own: 1 (level 1), 2 (15), 3 (50), 4 (78), 5 (91); only one boat per port at a time [W:Boat]. Boat
slots are persisted in varps `SAILING_BOAT_1_DATA`..`_5_DATA` 5063/5072/5081/5090/5099, names `_NAME` 5064/5073/..., and
four customisation varps + extra-data varps each (e.g. `SAILING_BOAT_1_CUSTOMISATION_1..5` 5065-5068, 5463) [C:sym].

### 2.2 Core parts (cache values) [C:db 164, 177-186]

Stats are rows of `Sailing_Boat_Facility_Stats` (db 164, fields in 6.2). Units: speed values are fine units per tick
(128 per tile; BASESPEED 192 = 1.5 tiles/tick, matching the Wiki's "Boat speed 1.5") [D]. Tier order everywhere:
wooden, oak, teak, mahogany, camphor, ironwood, rosewood.

**Hulls** (raft calls them "bases"; Sailing/Con levels are identical for all three boat types) [W:Hull][C:db 178]:

| Tier | Sailing | Con | HP raft / skiff / sloop | Base speed (tiles/tick) | Speed cap raft / skiff / sloop | Recovery fee % |
|---|---|---|---|---|---|---|
| wooden | 1 | 1 | 20 / 30 / 40 | 1.5 | 2.5 / 3.0 / 3.5 | 0 |
| oak | 20 | 8 | 30 / 45 / 60 | 1.5 | 2.5 / 3.0 / 3.5 | 5 |
| teak | 31 | 23 | 40 / 60 / 80 | 2.0 | 2.5 / 3.0 / 3.5 | 10 |
| mahogany | 48 | 41 | 50 / 75 / 100 | 2.0 | 2.5 / 3.0 / 3.5 | 15 |
| camphor | 67 | 59 | 80 / 120 / 160 | 2.5 | 3.0 / 3.0 / 3.5 | 20 |
| ironwood | 81 | 75 | 100 / 150 / 200 | 2.5 | 3.0 / 3.0 / 3.5 | 25 |
| rosewood | 93 | 84 | 120 / 180 / 240 | 3.0 | 3.5 / 3.5 / 3.5 | 30 |

(Hull "defence" per Wiki: raft +1/5/10/15/20/25/30, skiff +10..+70, sloop +15..+75 [W:Hull].)
Materials: raft = 10 logs of the tier + 6 rope + 10 swamp tar (+5 lead bars teak..camphor, +5 cupronickel bars
ironwood/rosewood); skiff = 10 hull parts + 300 nails + 20 swamp tar (+5 bars); sloop = 16 large hull parts + 600 nails
+ 25 swamp tar (+5 bars). Nail tier follows log tier (bronze, iron, steel, mithril, adamantite, rune, dragon). Cache:
`Sailing_Boat_Hull.MATERIAL` rows hold (obj, qty) pairs [W:Hull][C:db 178]. Construction XP and shipwright fees in
[W:Raft]/[W:Hull].

**Masts and sails:**

| Tier | Sailing | Con | Storm res. | Boost ticks (raft mahogany 26, skiff/sloop 27) | Accel (cache) | Trim XP |
|---|---|---|---|---|---|---|
| wooden/linen | 1 | 1 | 0 | 20 | - | 10.5 |
| oak/linen | 24 | 11 | 1 (partial) | 22 | - | 19.5 |
| teak/canvas | 36 | 26 | 1 | 24 | - | 30 |
| mahogany/canvas | 52 | 45 | 1 | 26/27 | - | 48 |
| camphor/canvas | 68 | 60 | 2 (full) | 30 | 64 | 64 |
| ironwood/cotton | 83 | 77 | 2 | 33 | 64 | 80 |
| rosewood/cotton | 94 | 85 | 2 | 36 | 64 | 125 |

Wiki "acceleration" is 0.5 for wooden..mahogany and 1.0 for camphor+; the cache only stores `ACCELERATION=64` on
camphor+ (default for the others is not in the cache, OPEN-S3) [W:Mast][C:db 164]. Materials: raft 5 logs + 20 nails +
5 bolts; skiff 10 + 40 + 5; sloop 15 + 60 + 10 (bolt: linen for wooden/oak, canvas teak..camphor, cotton ironwood/rosewood).
Sail patterns: db 230 (31 rows, recolouring unlocked at level 41 Con 27) [C:db 230][W:LevelUp].

**Helms:** Sailing 1/17/38/55/72/87/96, Con 1/14/30/47/59/81/86 (bronze..dragon). Rapid resistance in the cache stat rows:
0,1,1,2,2,3,4 (Wiki: none / gentle / gentle / strong / strong / powerful / deadly); adamant and up also immune to tangled
kelp; rune and dragon carry `CRYSTALHELM_RESISTANCE` 1 and 2. Materials: 2/3/4 planks + 4/6/8 bars for raft/skiff/sloop;
dragon uses dragon metal sheets [W:Helm][C:db 164, 181]. `CREW_STAT_REQUIREMENT` on skiff/sloop helms = (helmsmanship 1,
sloop 3): a sloop needs crew helmsmanship 3 to be piloted by crew [W:Crew].

**Keels** (skiff and sloop only; the raft has none) [C:db 164 rows 8224-8237, db 177]: HP / armour per tier
skiff 50/100, 50/200, 60/300, 60/400, 70/600, 80/800, 80/1000; sloop 70/100, 70/200, 90/300, 90/400, 100/600, 120/800,
120/1000 (the skiff/sloop split is by row order 8224-8230 / 8231-8237, [D] - not linked explicitly in the cache here);
adamant and up carry `CRYSTAL_FLECKED_RESISTANT`. Sailing levels 1, 22, 39, 54, 66, 85, 97 (bronze..dragon)
[W:LevelUp]. Materials: db 177 `MATERIAL`.

**Other core parts:** eternal brazier (Sailing 78, Con 72; db 185, 3 rows: one per boat size), flags (three Barracuda
rewards; Sailing 25, Con 20; db 184), trims (db 186, 37 rows, Sailing/Con 25/20), hull ornaments (db 183, empty).
`ICY_SEAS_RESISTANT`=1 on the brazier row [C:db 164 row 8261]. Boat names: random on purchase; Sleve McDichael on Tear
of the Soul renames for one unnoted cider (level 61); the cape allows free choice (db 187 name options) [W:Boat].

### 2.3 Facilities and hotspots

Hotspots are positions on the deck where a facility loc can be built (`Sailing_Boat.HOTSPOT` = list of (deck tile,
code a, code b, `Sailing_Boat_Hotspot` row), a in {0,1,3}, b in {1,2,3} - probably loc rotation and footprint, OPEN).
Each hotspot row (db 175) lists the permitted `Sailing_Boat_Facility` rows (db 176). Counts: raft 1, skiff 7, sloop 13
[C:db 166, 175]. Raft hotspot may hold: cannon, salvaging hook, cargo hold, range, keg, wind/gale catcher, teleport focus
(no trawling nets, no anchor, no chum) [W:Raft][C:db 175 row 8526: 27 facility rows].

Facility catalogue (cache `FACILITY_SUBTYPE`; Sailing / Con levels from db 176, matching [W:LevelUp]):

| Subtype | Facility | Sailing / Con | Notes |
|---|---|---|---|
| 4 | Cannon x7: bronze, iron, steel, mithril, adamant, rune, dragon | 28/21, 35/28, 47/39, 57/50, 69/61, 80/76, 92/84 | max 2 per boat; Ranged level to fire 1,1,5,20,30,40,60; privateering 1,2,2,3,3,4,4 [C:db 162][W:Fac] |
| 1 | Salvaging hook x7: bronze, iron, steel, mithril, adamant, rune, dragon | 15/1, 21/9, 27/18, 44/30, 59/52, 74/66, 86/78 | crew deckhandiness 1,1,1,2,2,3,4 [W:Crew] |
| 2 | Cargo hold x7 (wood tiers; basic, oak..rosewood) | 1/1, 18/11, 29/21, 46/41, 60/53, 80/77, 89/84 | one per boat; sizes below [C:db 164] |
| 3 | Range | 16/6 | cook at sea |
| 5 | Chum station / advanced chum station | 56/45 / 68/61 | trawling bait |
| 13 | Chum spreader | 82/74 | |
| 6 | Inoculation station | 40/37 | fetid-water immunity |
| 7 | Salvaging station | 42/34 | sort salvage at sea |
| 8 | Wind catcher / gale catcher | 53/47 / 79/70 | max wind motes 2 / 3 |
| 9 | Teleport focus / greater teleport focus | 55/49 / 75/69 | summon boat / teleport to boat |
| 10 | Trawling net: rope, linen, hemp, cotton | 56/45, 65/61, 76/65, 84/73 (db 176 also has a stray 53 on one linen row) | not on rafts |
| 11 | Fathom stone / fathom pearl | 70/62 / 91/83 | locate shoals |
| 12 | Crystal extractor | 73/67 | needs Heart of Ithell |
| 14 | Anchor | 37/29 | holds position, allows rotation |
| 15 | Keg | 33/25 | needs barrel stand; holds one ale |
| 16 | Ballistic attractor | 50/44 | quest Animal Magnetism; 60% ammo saved (db 164 `AMMO_SAVE_PERCENTAGE` 60) |
| 17 | Bosun's workbench | 63/54 | quest The Red Reef; auto repair rate 10 (db 164 row 16256) |
| - | Recoloured sails | 41/27 | |

**Cargo hold capacity** (items, cache db 164 `CARGOHOLD_SIZE`): raft 20, 30, 45, 60, 80, 105, 120; skiff 30, 45, 60, 90,
120, 150, 180; sloop 40, 60, 90, 120, 160, 210, 240. Inventories `SAILING_BOAT_1..5_CARGOHOLD` 963-967; varps
`SAILING_BOAT_CARGOHOLD_INV` 5204 and `_SIDE_WHITELIST` 5205; whitelist db 196 [C:sym][C:db 164, 196].
Storable-item list (cargo crates, bounty parts, salvage, fish, kegs' ales, repair kits, cannonballs, offcuts, charting
tools) in [W:Cargo]. Crates, bounty items, salvage, fish and fish-filled crates are LOST on summon/recover/capsize/
teleport-off; kept: cannonballs, repair kits, offcuts, empty crates [W:Boat][W:Cargo].

**Kegs / ales** (stored in keg facility): Whirlpool surprise (invisible +2 Sailing for salvaging, crew min 2 H/D), Kraken
ink stout (ocean-encounter interval 120 -> 115 ticks), Perildance bitter (+1 cannon max hit), Trawler's trust (+5% extra
fish), Horizon's lure (+2.5% XP); multiple kegs of the same ale do not stack [W:Salv][W:Enc][W:Trawl]. Keg locs
59691-59698 [C:sym].

### 2.4 Deck templates in the cache map [C:map][C:db 166, 176-186]

The cache stores every boat deck as ordinary map data at level 1 in map squares (60,99) and (60,100) (region base
(3840,6336) and (3840,6400)); sea-level (level 0) holds the hull loc, level 1 holds the walkable deck. **Each hull
tier has its own template, 8 tiles apart in x**: tier t (0 = wooden .. 6 = rosewood) is at x offset +8*t, so the
templates are zone aligned (zone x = 3840 + 8t) [D from the 7 hull locs per boat spaced 8 apart and the repeating ring
collision in `collision 3860 6440 1 32`]. Parts (helm, sail, keel, flag, trim, brazier, facilities) are not placed in the
map; they are added by the server at the db coordinates below, which are given for tier 0 (add 8*hullTier to x) [D].

| Boat | Hull locs (level 0) | Walkable deck at level 1 | Helm | Sail | Hotspots | Keel / flag / trim / brazier (level 0 unless noted) |
|---|---|---|---|---|---|---|
| Raft (cfg 1) | 59494-59500 `SAILING_BOAT_HULL_KANDARIN_1X3_*` at (3843+8t, 6458) | x=3843, z=6458..6460 | (3843,6460) loc 59554+3*tier | (3843,6459) | 1 at (3843,6458) | flag (3843,6461), brazier (3844,6460); no keel |
| Skiff (cfg 2) | 59501-59507 `..._2X5_*` at (3843+8t, 6449) | x=3843..3844, z=6449..6454 (OPEN exact rect) | (3844,6454) | (3844,6452) | 7: (3843,6453), (3843,6452), (3843,6451), (3844,6451), (3843,6450), (3843,6449), (3844,6449) | keel (3842,6449), trim (3841,6449), flag (3843,6454 lvl 1), brazier (3845,6453) |
| Sloop (cfg 3) | 59508-59514 `..._3X8_*` at (3842+8t, 6435) | x=3842..3844, z=6436..6443 (OPEN exact rect) | (3843,6443) | (3844,6442) | 13: x=3842 z=6442,6441,6439,6438,6437,6436,6440; x=3844 z=6441,6439,6438,6437,6436,6440 | keel (3841,6435), trim (3841,6434), flag (3842,6443 lvl 1), brazier (3845,6442) |
| Will and Anne's boat | 59501 at (3843,6401); keel 59516, trim 59624 | x=3843..3844, z=6402..6406 | (3844,6406) loc 59620 | (3844,6404) | static facility (3843,6402) | keel (3842,6401), trim (3841,6401) |
| Intro copy of W&A boat | - | x=1496..1503, z=4512..4519, level 1 (the template the engine currently copies as "raft") | `Helm` loc 59621 at (1499,4514,1) | `Sails` loc 59553 at (1499,4513,1) | - | - |

**Correction to current engine tests:** the zone (1496,4512) used as the verified raft deck is the *Pandemonium intro
boat* (boat type 3, loc names "Helm"/"Sails"), not the player's raft (type 0), whose deck is the 1x3 strip at x=3843,
z=6458..6460 [D from db 181/166 coordinates + `find-locs Helm`/`Sails`]. Config 1 bounds (1x3) match the type-0 raft.

Other templates found with `find-locs Helm` (the only 5 locs named exactly "Helm") and `map 60 99`:
- NPC medium boats ("Kandarin 3x8 linen"): helm 60242 `SAILING_NPC_BOAT_BASIC_MEDIUM_HELM` at (3843,6371,1),
  (3851,6371,1), (3859,6371,1); sails 29526 at (3844/3852/3860,6371); hulls 59508/26196/47183 around z 6362-6368.
- NPC small boat: helm 60241 `..._BASIC_SMALL_HELM` at (3868,6398,1), sail 29516 at (3868,6397,1), hull 59501 (3867,6393).
- NPC large "Kandarin"/"Pirate" boats: locs 59467-59475 at (3843/3859/3875, 6388..6396) and 59476-59484 at (3851,
  6388..6396) (these are model pieces of a 5x20 ship, `BOATKIT_SHIPHULL_*` 59894-59906 at x=3884-3890, z=6376-6396).
- Barracuda trial-master boats (skiff size): hull 58930 at (3851+8k, 6353), sail 58932 (z 6356), helm 58933 (z 6358),
  k=0..4.
- Cannon prototypes: `SAILING_GOLD_CANNON` 59453 at (3843,6356,1).

Walkable-deck rects for skiff and sloop must be taken from the tile flags when copying (the ring of blocked tiles seen
in `collision` is the hull; the interior is walkable); the config bounds (2x5, 3x10) are larger than the hotspot extent
and are OPEN-B1 for the engine's pivot offset (OPEN-WE3).

### 2.5 Boarding, docking, boat-selection

- **Gangplank** (land side, locs `SAILING_GANGPLANK_*` 59835-59860, generic `SAILING_GANGPLANK_EMBARK/DISEMBARK` 59831/
  59832) options: Board, Board-previous, Board-friend (join another player's boat); on the boat: Disembark. Disembarking
  from a gangplank or mooring point sets the respawn dock (gravestone / capsize location) and persists; a mooring point
  (islands; `SAILING_MOORING_EMBARK/DISEMBARK` 59833/59834) works the same without a gangplank [W:Gangplank][W:Moor].
  Disembarking at a mooring point is not boostable [W:Moor]. Keyboard: space = most recent boat, 1-4 = other boats.
- **Buoy** (option Dock; locs `SAILING_DOCKING_BUOY_*` 59769-59830, one per mooring point): clicking sets the escape/
  capsize destination; not required to disembark, only being in range of the gangplank/mooring point [W:Buoy].
- **Boat selection interface** `SAILING_BOAT_SELECTION` (934; 35 components; scripts 8621-8654): shown by gangplank
  (Board), shipwright (Customise, Repair, Recover, Summon, Destroy, Rename, Bottle), teleport focus spells, the bank-cargo
  crew. The type is varbit `SAILING_BOAT_SELECTION_TYPE` 18553; db 149 `Boat_Selection_Type` has the 12 types:
  1 Personal Boats, 2 Customise, 3 Board, 4 Repair, 5 Recover (shows retrieval cost), 6/7 Summon, 8 Teleport To (greater
  focus), 9 Destroy, 10 Rename, 11 Move Cargo to Bank, 12 Bottle; flags per type: bottled/capsized not allowed, only
  boats at current port, needs (greater) teleport focus, needs empty hold. Varps `SAILING_BOAT_SELECTION` 5003, `_TEMP`
  5004, `_CURRENT_DOCK` 5005, `_TELEPORT_DOCK` 5006; varcs `SAILING_BOAT_SELECTION_EXPANDED_INFO` 1296, `_SCROLL_POS` 1297;
  `SAILING_LAST_PERSONAL_BOAT_BOARDED` 18554. Boat sprites db 147/148 [C:db 149][C:sym][C:cs2 8621-8654].
- Boarding sets: varbits `SAILING_BOARDED_BOAT` 19136, `_TYPE` 19137, `_WORLD` 19122, `_LAST_DOCK` 19145,
  `_LAST_STANDARD_DOCK` 19146, `_LAST_MOORING_POINT` 19147, `SAILING_PLAYER_IS_ON_PLAYER_BOAT` 19104, `SAILING_DIED_AT_SEA`
  19140; varp `SAILING_BOARDED_BOAT_NAME` 5053, `SAILING_LAST_DOCK` 5062 [C:sym].
- Guests: anyone (incl. ironmen) may "Board-friend"; cargo-hold access is a separate setting (26 Nov 2025) [W:Boat][W:Sailing].
- **Capsize** (boat HP 0): player returns to the last gangplank with inventory; gravestone rule on player death at
  sea; recovery by any shipwright for the recovery cost (50 coins/HP repair at Shipwright Seb) [W:Boat][W:Sailing].
- **Escape** helm option: abandon ship to the dock; own boat then needs recovery; cargo lost [W:Helm].
- **Boat teleport**: Summon Boat (Magic 56) moves the boat to the nearest mooring point (teleport focus needed);
  Teleport to Boat (Magic 67, greater focus). Both are not Sailing content in the narrow sense (spellbook entries,
  OPEN-S4 for spell ids).

## 3. Sailing movement

### 3.1 Controls and protocol surface

- Right-click the **helm**: *Navigate* locks the player at the wheel; the camera follows the boat and their clicks give
  headings; *Stop navigating* releases (the boat keeps its heading) [W:Sailing][W:Nav2023]. One navigator at a time.
  Crew with helmsmanship can steer instead (a sloop needs helmsmanship 3) [W:Crew].
- A white arrow around the boat follows the cursor; a click sets the **heading**. The rev-242 protocol has a dedicated
  incoming message `SetHeading(heading 0..15)`, a 16-direction scale of the 0..2048 angle (value = angle/128) and an
  outgoing `SetInteractionMode(worldId, tileMode, entityMode)` whose tile mode 2 = "Heading" (1 = Walk, 0 = disabled)
  [C: `protocol/osrs-242/rsprot/model/.../SetHeading.kt`, `SetInteractionMode.kt`]. Neither is wired in `protocol-api` or
  the adapter yet (grep: no references outside the vendored RSProt) - **protocol work for the main session**. The
  cache columns `DIRECTION16_ACCELERATION` / `DIRECTION16_TURNSPEED` confirm the 16-direction model.
- After the heading is reached the boat moves when the throttle is raised with the up/down arrows of the side panel
  (even reverse when stationary with sails unset); the *set/unset sails* button starts/stops immediately
  [W:Sailing][W:Opts]. Sail states in the cache: `SAIL_DOWN`, `SAIL_HALF`, `SAIL_FULL`, `SAIL_REVERSE` (db 180 rows,
  server-only content). Varbits: `SAILING_SIDEPANEL_SAIL_BUTTON_TOGGLED` 19174, `_BOAT_MOVE_MODE` 19175, `_HELM_STATUS`
  19176; settings `KEEP_SAILING_BOAT_WHEN_LEAVING_HELM` 18135, `SAILING_START_BOAT_WHEN_SETTING_HEADING` 18136
  (start moving when a heading is set) [C:sym].
- Boarding players are on the deck: `OpWorldEntity*` (click on a world entity), `SetActiveWorld` (client-side rebuild
  order) exist in RSProt; deck clicks are accepted by the engine already (WE5).

### 3.2 Speed model (what is known)

| Fact | Value | Source |
|---|---|---|
| Hull base speed | 1.5 .. 3.0 tiles/tick in 0.5 steps (cache 192..384 fine units) | [C:db 164][W:Hull] |
| Speed cap | 2.5 / 3.0 / 3.5 tiles/tick (320/384/448) by size/tier | [C:db 164] |
| Acceleration | sail tier: Wiki 0.5 (wooden..mahogany), 1.0 (camphor+); cache `ACCELERATION=64` camphor+ | [W:Mast][C:db 164] |
| Sail trim | sails can be trimmed every 30 s (50 ticks); a gust opens a trim window of about 12 s (20 ticks) (alpha/beta: gusts every 49 ticks, or 20 ticks after a boost ended); a trim gives a speed boost of 20..36 ticks by sail tier; trimmed by captain or by a crewmate (crew boost durations were made equal on 14 Jan 2026); other players need the sail's Sailing level, the captain is exempt (7 Jan 2026); cannot trim while wielding cargo | [W:Sailing][W:Alpha][W:Beta][W:Mast] |
| Wind boost | up to +0.5 tiles/tick; separate from rapids | [W:Rapids] |
| Rapids | each pass adds a stack (strong 2, powerful 3 when unboosted), 15-tick decay timer renewed per pass, cap 15 stacks, each stack +0.5 tiles/tick up to the speed cap; too low a helm = damage instead | [W:Rapids] |
| Hazard | ship "significantly slowed" in unprotected hazard areas; crew panic (orders cancelled) | [W:Haz][W:Ship] |
| Anchor | rotate in place, no movement; stops faster than furling sails | [W:Anchor] |
| Wind catcher | stores motes (2/3), release gives boost + XP; special-attack button releases (13 May 2026) | [W:Sailing][C:db 164] |
| Speed units in encounters | "quarter-tiles per tick"; a speed setting of 4 is the lowest throttle; ocean encounter timer ticks only when the boat enters a new tile | [W:Enc] |

Rapids note: Wiki says "Each stack adds 0.5 tiles/tick": the page also gives the 15-stack cap; the speed cap (hull) bounds it [W:Rapids].

### 3.3 Turning, collisions, boundaries

- Turning: the engine side is the existing WE3 rule (client turns at most 128/2048 per client cycle). The *rate the
  boat's heading changes* per tick is server-side. The 2023 design blog (not the released numbers) proposed 2 / 4 / 6
  ticks per 90 degrees for small / large / colossal vessels and arc-like turning [W:Nav2023]: **OPEN-M1**.
- Collisions: the game blocks the boat on land and rocks; running into a rock during Tempor Tantrum "kills momentum"
  [W:TT]; a stuck-reminder exists (`SAILING_BOAT_STUCK_REMINDER` 19116) and "Fixed a collision logic issue" (26 Nov 2025)
  [W:Sailing]. How the sea/land mask is encoded (water overlay, a new tile flag, or server-side data) is unknown: the
  level-0 `map` dump around the Bay of Sarim shows ordinary block flags on land only, not a sailable bit. **OPEN-M2**
  (most important engine question). *Update: resolved from the cache, see world-entities.md WE9 (sea overlays 441..623).* The boat footprint (config bounds) must clear the mask each tick.
- World border: impassable regions in Northern Ocean etc. show a message 4 times then a bell and screen shake, no
  damage [W:Haz]. Ocean list and levels in 4.1.
- A player on a boat has a root-world build area that follows the pivot (WE4).

### 3.4 Sailing Options side panel (`SAILING_SIDEPANEL` 937) and HUD

Replaces Combat Options (toggle by the anchor/crossed-swords button) [W:Opts]. Header = boat name and HP bar; three tabs
(Facilities, Stats, Crew). Facilities tab: one row per facility with crew-assignment, steering and repairs sections (not
reorderable); when navigating it shows sail set/unset, speed up/down and reverse. Stats tab: movement, overall combat (HP,
defence, armour), per-style defences, resistances. Crew tab: NPC crew (H/P/D and job) or player crew (captain, navigator).
Orders can be reordered in the shipyard side panel (13 May 2026) [W:Opts][W:Sailing].

Driver data the server must publish (client reads these; cs2 scripts 8713, 8778 and the 5xxx-8xxx range):
- Varps (transmit): `SAILING_SIDEPANEL_0..4` 5112-5116, `_BOAT_TYPE` 5117, `_BOAT_INFORMATION` 5118, `_FACILITY_STATES_0..12`
  (5119-5124, 5473, 5554-5556, 5572-5574), `_STATS` 5125, `_STATS_0/1` 5145/5146, `_BOAT_DEFENCE` 5147, `_BOAT_ARMOUR`
  5148 and 15 per-style armour/def totals 5149-5165, `_CREW_0..2` 5126-5128, `_AMMUNITION_COUNT_0..12`, `_FACILITY_ORDERS_A..M`
  5557-5569, `SAILING_BOAT_HP_STORAGE_1..5` 5140-5144, `SAILING_SIDEPANEL_PERM_TRANSMIT` 5111.
- Varbits (213 `SAILING_SIDEPANEL_*`): visibility 19151, tabs 19152, per-hotspot facility state `..._FACILITY_HOTSPOT0..10`
  19156-19166, sail 19154, helm 19155, keel 19167, hull 19168, flag 19169, brazier 19170, trim 19172, shipyard mode 19173,
  HP max/current 19177/19181, wind charges 19178/19179 (`BOAT_MAX_WIND_CHARGES` 11629), whirlpool active 19180,
  `PLAYER_AT_FACILITY_n`, order slots `FACILITY_ORDER_1..5` and `_FROMCAPTAIN` per hotspot (11630-13217),
  `AMMUNITION_TYPE_n` 13220-13298, `AMMO_FLAGS_n` 13299-13311, trawling net depths 19206-19209, repair kits 19210, weapon
  has-target/etc 19211-19227 and 20188-20197, `AUTO_REPAIR_RATE` 13312.
- Timers: varbits `SAILING_BOAT_TIME_TILL_TRIM` 19110, `_TIME_TRIM_WINDOW` 19111, `_SPEEDBOOST_DURATION` 19112,
  `_WINDS_TIMER_PAUSED` 19113; varcs `BUFF_SAILING_WINDS_TIMER_START` 1319, `_TRIM_TIMER_START` 1320,
  `_SPEEDBOOST_TIMER_START` 1321, `SAILING_LAST_BOAT_HP` 1342, `SAILING_SIDEPANEL_CAPTAIN_NAME` 1311, `_NAVIGATOR_NAME` 1312.
- DB (client reads): `Sailing_Sidepanel_Facility` 167 (12 rows: widget, can lock in, can be assigned, linked facility,
  priority type, icon), `_Widget_Button` 168 (10), `_Graphic` 170, `_Object` 171, `_Text` 172, `_Objbutton` 229; plus
  per-part `SIDEPANEL_LAYOUT_DATA` columns [C:db 167-172, 229].
- Settings varbits: `SETTINGS_SAILING_SIDE_PANE_ONLYONBOAT` 18131, `SETTINGS_SAILING_WIND_ON_ORB_DISABLED` 11624,
  `SETTINGS_SAILING_DROPITEM_WARNING` 18143, `SAILING_WARNING_TELEPORTOFFBOAT` 16620.
- `SAILING_MENU` (interface 72, 4 components, a model + "Close"; scripts 6575, 7336) is a small dialog frame, not the
  steering HUD [C:interface 72].
- Barracuda HUD uses interface `SAILING_BT_HUD` 931 (28 components) with varps `SAILING_BT_*` (5.3).

## 4. Ports and port tasks

### 4.1 Mooring points (docks) and seas

Cache `Sailing_Dock` (db 194): 61 rows with `DOCK_ID`, name, level, quest requirement, sprite; coordinates are
server-only. Dock coordinates below are from the cache map (buoy locs `SAILING_DOCKING_BUOY_*` 59769-59830, level 0)
[C:map]; wiki columns: SY shipwright, NB notice board, SS salvaging station, LT ledger table, CR crew registrar, DB bank
deposit box (Y available) [W:Moor].

**Buoy (sea-side dock) coordinates:** Port Sarim (3048,3186); The Pandemonium (3069,2981); Lands End (1515,3405); Musa Point (2961,3152); Hosidius (1719,3450); Rimmington (2908,3216); Catherby (2788,3409); Port Piscarilius (1840,3681); Brimhaven (2755,3223); Ardougne (2663,3261); Port Khazard (2687,3157); Witchaven (2746,3297); Entrana (2880,3330); Civitas Illa Fortis (1768,3147); Corsair Cove (2585,2847); Cairn Isle (2748,2944); Sunset Coast (1505,2977); The Summer Shore (3168,2364); Aldarin (1457,2975); Ruins of Unkah (3144,2818); Void Knights Outpost (2643,2678); Port Roberts (1859,3302); Red Rock (2810,2514); Rellekka (2633,3711); Etceteria (2618,3836); Port Tyras (2149,3117); Deepfin Point (1930,2755); Jatizso (2405,3776); Neitiznot (2304,3786); Prifddinas (2165,3321); Prifddinas (3189,6073); Piscatoris (2304,3696); Lunar Isle (2154,3886); Isle of Souls (2287,2830); Waterbirth Island (2538,3771); Weiss (2869,3972); Dognose Island (3066,2647); Remote Island (2975,2596); The Little Pearl (3361,2221); The Onyx Crest (2992,2293); Last Light (2845,2334); Charred Island (2657,2406); Vatrachos Island (1866,2980); Anglers Retreat (2460,2713); Minotaurs Rest (1949,3118); Isle of Bones (2540,2533); Tear of The Soul (2320,2764); Wintumber Island (2054,2613); The Crown Jewel (1770,2666); Rainbows End (2349,2263); Sunbleak Island (2189,2334); Shimmering Atoll (1563,2767); Laguna Aurorae (1208,2736); Chinchompa Island (1899,3420); Lledrith Island (2106,3181); Ynysdail (2216,3461); Buccaneers Haven (2077,3697); Drumstick Isle (2142,3526); Brittle Isle (1962,4053); Grimstone (2933,4061)

**Docks by level:**

| Docking point | Lvl | Other requirement | SY | NB | SS | LT | CR | DB | Wiki map xy |
|---|---|---|---|---|---|---|---|---|---|
| Port Sarim | 1 |  | Y | Y | Y | Y | - | Y | 3051,3193 |
| The Pandemonium | 1 |  | Y | Y | Y | Y | Y | Y | 3070,2987 |
| Land's End | 5 | Must have visited Kourend at least once before docking | Y | Y | Y | Y | - | Y | 1507,3403 |
| Hosidius | 5 | Must have visited Kourend at least once before docking | - | - | Y | Y | - | - | 1726,3452 |
| Musa Point | 10 |  | Y | Y | Y | Y | - | - | 2961,3146 |
| Port Piscarilius | 15 | Must have visited Kourend at least once before docking | Y | Y | Y | Y | - | Y | 1845,3687 |
| Rimmington | 18 |  | - | - | Y | - | - | - | 2906,3225 |
| Catherby | 20 |  | Y | Y | Y | Y | - | Y | 2796,3412 |
| Brimhaven | 25 |  | Y | Y | Y | Y | - | - | 2758,3230 |
| Ardougne | 28 |  | - | Y | Y | Y | - | Y | 2671,3265 |
| Port Khazard | 30 |  | Y | Y | Y | Y | - | Y | 2686,3162 |
| Witchaven | 34 |  | - | - | - | - | - | - | 2747,3305 |
| Entrana | 36 | Must not be wearing any prohibited items to dock here.  See the page on Entrana for details. | - | - | - | Y | - | - | 2879,3336 |
| Civitas illa Fortis | 38 | Completion of quest Children of the Sun Must have visited Varlamore at least once before docking | Y | Y | Y | Y | - | Y | 1775,3142 |
| Corsair Cove | 40 | Completion of quest The Corsair Curse | Y | Y | Y | Y | - | Y | 2580,2844 |
| Dognose Island | 40 |  | - | - | - | - | - | - | 3061,2631 |
| Cairn Isle | 42 |  | - | - | Y | Y | - | - | 2750,2952 |
| Chinchompa Island | 42 |  | - | - | - | - | - | - | 1893,3429 |
| Sunset Coast | 44 | Completion of quest Children of the Sun Must have visited Varlamore at least once before docking | - | - | Y | Y | - | - | 1512,2974 |
| Remote Island | 45 |  | - | - | - | - | - | - | 2972,2602 |
| The Summer Shore | 45 | Partial completion of quest Troubled Tortugans | - | Y | Y | Y | - | - | 3174,2367 |
| The Little Pearl | 45 | Partial completion of quest Troubled Tortugans | - | - | - | - | - | - | 3354,2216 |
| Aldarin | 46 | Completion of quest Children of the Sun Must have visited Varlamore at least once before docking | Y | Y | Y | Y | - | Y | 1452,2970 |
| Vatrachos Island | 46 | Completion of quest Children of the Sun | - | - | - | - | - | - | 1872,2984 |
| The Onyx Crest | 47 |  | - | - | - | - | - | - | 2997,2288 |
| Ruins of Unkah | 48 |  | - | Y | Y | Y | - | Y | 3144,2825 |
| Shimmering Atoll | 49 |  | - | - | - | - | - | - | 1557,2770 |
| Void Knights' Outpost | 50 |  | - | Y | Y | Y | - | Y | 2651,2678 |
| Port Roberts | 50 |  | Y | Y | Y | Y | Y | Y | 1861,3307 |
| Anglers' Retreat | 51 |  | - | - | - | - | - | - | 2466,2720 |
| Red Rock | 52 | Partial completion of quest The Red Reef | Y | Y | Y | Y | - | Y | 2809,2509 |
| Minotaurs' Rest | 54 | Completion of quest Children of the Sun | - | - | - | - | - | - | 1958,3116 |
| Isle of Souls | 55 |  | - | - | - | - | - | - | 2283,2822 |
| Isle of Bones | 56 |  | - | - | - | - | - | - | 2533,2530 |
| Laguna Aurorae | 58 |  | - | - | - | - | - | - | 1202,2732 |
| Charred Island | 60 |  | - | - | - | - | - | - | 2660,2394 |
| Tear of the Soul | 61 |  | - | - | - | - | - | - | 2318,2773 |
| Rellekka | 62 | Completion of quest The Fremennik Trials | Y | Y | Y | Y | - | - | 2630,3705 |
| Wyrmscraig | 62 |  | - | - | Y | - | - | - | 2568,2296 |
| Wyrmscraig Cavern | 62 | Partial completion of quest Fallen From Grace Can only be accessed with a raft or skiff | - | - | - | - | - | - | 2582,8606 |
| Wintumber Island | 63 |  | - | - | - | - | - | - | 2057,2605 |
| The Crown Jewel | 64 |  | - | - | - | - | - | - | 1765,2658 |
| Etceteria | 65 | Completion of quest Royal Trouble | - | Y | Y | Y | - | Y | 2613,3840 |
| Port Tyras | 66 | Completion of quest Regicide | - | Y | Y | Y | - | - | 2144,3120 |
| Lledrith Island | 66 |  | - | - | - | - | - | - | 2097,3187 |
| Deepfin Point | 67 |  | Y | Y | Y | Y | - | Y | 1923,2758 |
| Jatizso | 68 | Completion of quest The Fremennik Isles | - | - | Y | Y | - | Y | 2412,3780 |
| Neitiznot | 68 | Completion of quest The Fremennik Isles | - | - | Y | Y | - | Y | 2309,3782 |
| Rainbow's End | 69 |  | - | - | - | - | - | - | 2345,2269 |
| Prifddinas | 70 | Completion of quest Song of the Elves | Y | Y | Y | Y | - | Y | 2158,3324 |
| Sunbleak Island | 72 |  | - | - | - | - | - | - | 2189,2326 |
| Ynysdail | 73 |  | - | - | - | - | - | - | 2222,3464 |
| Waterbirth Island | 74 | Completion of quest The Fremennik Trials | - | - | - | - | - | - | 2543,3765 |
| Piscatoris | 75 | Completion of quest Swan Song | - | - | Y | Y | - | Y | 2304,3689 |
| Lunar Isle | 76 | Completion of quest Lunar Diplomacy | - | Y | Y | Y | - | Y | 2152,3881 |
| Buccaneers' Haven | 76 |  | - | - | - | - | - | - | 2080,3690 |
| Drumstick Isle | 79 |  | - | - | - | - | - | - | 2150,3529 |
| Weiss | 80 | Completion of quest Making Friends with My Arm | - | - | - | - | - | - | 2861,3971 |
| Brittle Isle | 81 |  | - | - | - | - | - | - | 1954,4055 |
| Grimstone | 87 |  | - | - | - | - | - | - | 2928,4055 |

Port task boards (locs `PORT_TASK_BOARD_*`, 34 notice-board locs incl. non-Sailing; x,z,level): Port Sarim (3030,3197,1); Pandemonium (3058,2985,1); Catherby (2803,3418,1); Port Khazard (2678,3162,1); Ruins Of Unkah (3145,2828,1); Rellekka (2629,3685,1); Port Piscarilius (1839,3691,1); Civitas Illa Fortis (1782,3142,0); Lands End (1502,3407,1); Musa Point (2940,3144,0); Ardougne (2676,3276,1); Brimhaven (2764,3227,1); Corsair Cove (2579,2853,1); The Summer Shore (3183,2368,1); Aldarin (1438,2969,1); Void Knights Outpost (2659,2672,1); Port Roberts (1872,3303,1); Red Rock (2806,2511,1); Etceteria (2617,3849,0); Port Tyras (2146,3123,1); Deepfin Point (1931,2761,1); Prifddinas (2163,3326,1); Prifddinas (3187,6078,1); Lunar Isle (2139,3884,1). Salvaging
stations, ledger tables (icon locs `LEDGER_TABLE_ICON_*` 58017-58609) and shipwrights (NPC `SAILING_SHIPWRIGHT_*`
15363-15377; `SAILING_SHIPYARD_MANAGER` 15351-15352, workers `SAILING_SHIPYARD_WORKER_*` 1555-15362) are
placed by the same map data [C:sym]. Quest/visit gating is in the dock row (`QUEST_REQUIRED`) and in Wiki's "Other
requirements": Kourend visited (Land's End, Hosidius, Port Piscarilius), Varlamore visited + Children of the Sun (Civitas
illa Fortis, Sunset Coast, Aldarin, Vatrachos, Minotaurs' Rest), The Corsair Curse, Troubled Tortugans (Summer Shore,
Little Pearl), The Red Reef (Red Rock), The Fremennik Trials (Rellekka, Waterbirth), Royal Trouble (Etceteria), Regicide
(Port Tyras), The Fremennik Isles (Jatizso, Neitiznot), Song of the Elves (Prifddinas), Swan Song (Piscatoris), Lunar
Diplomacy (Lunar Isle), Making Friends with My Arm (Weiss), Entrana: no prohibited items [W:Moor].

**Oceans and seas** (cache `Sailing_Sea` db 201: 81 rows = 6 oceans (Ardent 38, Unquiet 45, Shrouded 72, Sunset 38,
Western 66, Northern 78 by `REQUIRED_LEVEL`, plus a "Miscellaneous" group), 70 seas and 4 bonus groups (Drinks 12,
Currents 22, Dives 38, Weather 57)
with `CENTRE_COORD`; seas e.g. Kharidian Sea (3103,2968) L1, Bay of Sarim (3070,3177) L1, Lumbridge Basin L5, Mudskipper
Sound L5, Catherby Bay L12, The Storm Tempor (3069,2848) L24 ...). Nine oceans exist in total; Forgotten, Untamed,
Eastern and part of Sunset are inaccessible (unbypassable hazards) [W:Sailing][C:db 201]. `SAILING_SEA_*` varbits
18932-19100 hold per-sea charting completion; `SAILING_TRANSMIT_NEARBY_SEA_PLUS1` 19115 and `_IS_AT_SEA` 19117 tell the
client the sea it is in.

**Hazards** (cache db 200: stormy seas, fetid waters, fetid pool, crystal-flecked waters, profane waters, sunbaked seas,
tangled kelp, icy seas, scalding seas, endless winter, jagged reefs; areas server-only) [C:db 200][W:Haz]:

| Hazard | Where | Needs | Effect if unprotected |
|---|---|---|---|
| Stormy seas | Storm Tempor, Kharazi Strait | oak+ mast | 10 damage every few seconds |
| Jagged reefs | around Crandor, Aehecatl | none | slowed, 5 (Crandor) / 20 (Aehecatl) damage every few ticks |
| Fetid waters | Shrouded Ocean seas (Breakbone, Backwater, Zul-Egil, Mythic, Souls) | inoculation station | slowed; 5 damage / few ticks + disease |
| Crystal-flecked | Western Ocean seas (Tirannwn Bight, Porth Gwenith/Neigwl, Piscatoris Sea) | adamant+ keel | slowed; 20 damage / few seconds |
| Tangled kelp | Rainbow Reef, Southern Expanse | adamant+ helm | slowed; 4 damage / few ticks |
| Icy seas | Northern Ocean seas | eternal brazier | crew frozen, then player; increasing damage |
| Eternal cold, Sunbaked, Profane, Scalding, Cursed | inaccessible oceans | not currently bypassable | various |
| World border | Northern Ocean edge, Dusk's Maw, Southern Expanse parts ... | none | impassable, message x4 then bell/shake |

### 4.2 Port task system

- Slots (cache varbits `PORT_TASK_SLOT_0..4_ID/_CARGO_TAKEN/_CARGO_DELIVERED` 19574-19588, `PORT_TASK_EXTRA_SLOTS_UNLOCKED`
  19589, `PORT_TASK_LAST_CARGO_TAKEN` 19590, `PORT_TASKS_COMPLETED_TODAY` 19591, varp `PORT_TASKS_COMPLETED`): 1 task at
  level 1, 2 at 7, 3 at 28, 4 at 56, 5 at 84 (courier and bounty share the slots; bounty unlocks at 30) [W:Courier][W:Bounty].
  Task slot unlock level and the "accept" level are **not boostable**.
- **Notice board** (interface `PORT_TASK_BOARD` 941, 8 components; info panel `PORT_TASK_INFO` 942; scripts 7856, 8901,
  8912): lists the tasks of that board; each board has its own list and exactly one always-available bounty. Tasks can be
  cancelled at the board, via the Captain's log (`SAILING_LOG` 935) or any port master [W:Courier][W:Bounty].
  `Port_Task` db 197 columns present in the cache: `TASK_ID`, `NAME`, `TASK_TYPE` (0 courier, 1 bounty), `LEVEL_REQUIRED`,
  `STARTING_PORT`, `CARGO_PORT`, `ENDING_PORT` (db 194 rows), `CANCELLABLE`, `CARGO` (obj, qty), `BOUNTY_TARGET_ALIVE`/
  `_DEAD` (npc), `BOUNTY_OBJECT`, `_AMOUNT`, `_RARITY` (1/n), `TASK_BOARD_GRAPHIC`, `FLAVOUR_TEXT`. 606 rows = 443 courier +
  163 bounty. Missing (server): `XP_REWARD`, `GIVE_COIN_REWARD`, `COURIER_REWARD_SOURCE`, facility/combat requirements,
  `PORT_CHECKPOINT`, `DELIVERY_OBJECT`, `SIDE_EFFECT_ID` [C:db 197].
- **Courier flow** [W:Courier]: choose at board -> (both hands free, weapons unequipped) take crate(s) from the ledger
  table at `CARGO_PORT` -> carry to own boat -> load into the cargo hold -> sail to destination -> withdraw -> deposit on
  the ledger table at `ENDING_PORT`. Players can only use their own boat. Crew with deckhandiness 3-4 carry 2 crates;
  at a port with a deposit box the first crewmate banks the hold (needs deckhandiness >= 2 unless whirlpool surprise)
  [W:Courier][W:Moor]. Teleporting while carrying confiscates cargo immediately. Cargo crates are objs
  `CARGO_CRATE_*` 32435-33001 (440 objs); the carried crate sets `SAILING_CARRYING_CARGO` 19134 [C:sym].
  Warnings: `PORT_TASK_LOADING_BAY_WARNING_DISMISSED` 18137, `SAILING_PORT_TASK_CANCEL_WARNING_DISABLED` 18130,
  `SETTINGS_CARGO_IN_HOLD_WARNING` 18144.
- **Bounty flow** [W:Bounty]: kill the listed sea monster anywhere, bounty parts drop at the rarity, unique jingle on
  a part; parts are stackable, not bankable, storable in the hold (lost on recover/summon); claim at any port master; two
  tasks for the same monster allowed but only the first progresses until done; no multiple bounty rolls per kill (17 June
  2026); on-task drop-rate boosts for dragon cannon barrel, broken dragon hook, bottled storm, dragon metal sheet until
  the quantity is reached the first time. Bounty required counts are about 5x the launch values since 3 June 2026 (the
  cache already holds the new counts, e.g. albatross beak x5 at 1/10, feather x20-30 at 1/2).
- **Rewards:** Sailing XP; courier: 4/5 port **coin bag** or 1/5 port **reward bag** (size by base XP: tiny 0-399,
  small 400-999, medium 1,000-2,499, large 2,500-5,999, huge 6,000+; the bag is rolled before Horizon's lure
  multiplier); bounty: coins equal to base XP (before lure) + a coin bag; **shark paint 1/36** per completed task
  (3 Dec 2025); courier XP +10% since 3 June 2026 [W:Courier][W:Bounty][W:Sailing].
- **Port master** (NPC): cancel task, claim rewards, bounty hand-in. Port reward bags contain supplies including
  sawmill coupons [W:Sailing].

### 4.3 Task data and XP formula

- The Wiki lists all 439 standard courier tasks (`CourierTaskLine`: level, XP, notice board, cargo port, destination,
  crate item, quantity, task id) and the bounty list (`BountyTaskLine`: level, XP, board, monster, item, qty, rarity,
  task id), keyed by the same task ids as the cache rows. Import them with `tools/wiki-import` (XP per task) and take
  everything else from db 197 [W:Courier][W:Bounty][C:db 197].
- XP basis: "based on distance travelled, a round trip gives double" [W:Courier]. A first fit of the Wiki table shows XP
  is not a pure function of (cargo port -> destination): the same route varies with quantity and level (e.g.
  Aldarin -> Civitas illa Fortis 1,231 XP level 46 qty 2 vs 1,574 level 52 qty 4; Ardougne -> Port Tyras 3,635 level 66
  qty 6 vs 2,323 level 70 qty 6). Ratio observed: board = cargo port gives about half the XP of board != cargo port
  (Port Sarim -> Pandemonium 78 vs 155). **OPEN-T1: no closed formula** - ship the per-task XP as imported data, not a
  formula. Representative values: level 1 platebody crate 78 XP; level 50+ bounty 14,575 (albatross), 40,370 (armoured
  kraken) [W:Courier][W:Bounty].

### 4.4 Cargo hold interface

`SAILING_BOAT_CARGOHOLD` 943 (26 components) and `_SIDE` 944; script 8885; inventories 963-967 (above); the hold
panel also carries charting tools (withdrawable if not owned) and the facility settings varbits
(`SAILING_FACILITY_SETTING_*` 19131-19133). The hold does not accept noted items; the Bank Cargo crew option empties it
at a bank boat [W:Cargo][W:Crew].

## 5. Activities

### 5.1 Sea charting (358 tasks)

- Cache: `Sailing_Charting_Core` (db 154, 358 rows: `UNIQUE_ID`, `HINT`, `SAILING_SEA` + secondary, `GIVE_REPEAT_XP`,
  `CHARTING_TYPE` (links to the type tables), `HAZARD`, `REQUIRED_DOCK`) and 6 type tables: generic 94 (db 155), spyglass 57
  (156), current duck 54 (157), drink crate 65 (158), weather troll 31 (159), mermaid guide 57 (160); tool recovery db 161
  (5 tools). Per-type content (coords, camera, duck routes, drinks, weather data) is server-only (empty columns).
- Types and XP (one-off) [W:Chart]: generic (Captain's log, "Chart" option on a scenery loc) 35 XP, Sailing 1; spyglass (stand
  on bubbles, cutscene) 50; crate (crowbar, quest Prying Times, level 12) 75 and choose to drink the contents (some
  drinks have effects, some can kill; sealed-crate drinks `SAILING_CHARTING_DRINK_CRATE_*` NPCs 15160-15177); current
  (duck, Current Affairs, level 22) 125; diving (fishbowl helmet + diving apparatus or Medallion of the Deep, level 38,
  mermaid guide NPCs 15155-15159) 175; weather (portable weather station, level 57) 250. Sea and ocean completion bonuses
  (claimed at Chartin' Charles McAtless in Pandemonium Cave), +100,000 for all 358, total 691,270 XP; completing a sea
  places rapids (`SAILING_CHARTING_RAPIDS_*` locs 59376-59445, 70 sea rapids) [W:Chart]. Soup 1/30,000 per task.
- Interface: Captain's log `SAILING_LOG` 935 (24 scripts 8658-8927; filters via varcs `SAILING_CHARTING_*_FILTER`
  1298-1305; selection varc 1304), `SAILING_SPYGLASS` 936 for the spyglass camera. Completion varps:
  `SAILING_CHARTING_COMPLETION_0..11` 5016-5027, `_SEA_COMPLETION_*`/`_EVER_COMPLETE_*` 5028-5033, `_SEA_XP_OWED_0` 5034,
  `SAILING_CHARTING` 5035, `_TOTAL_ENTRIES` 5037, `_CURRENT_DUCK_RESET` 5038; varbits `SAILING_CHARTING_*` 18564-19609 (369
  ids), `SAILING_SHOW_CHARTING_HINTS` 18138, hint arrows (`SAILING_CURRENT_DUCK_HINTARROW_DISABLED` 18133,
  `SAILING_BARRACUDA_TRIALS_HINTARROW_DISABLED` 18134) [C:sym][C:cs2 8658-8927].
- Items: Captain's log obj `SAILING_LOG_INITIAL` 31985, spyglass 31803 and 75 `SAILING_CHARTING_*` objs 31803-31905.
- Charting levels and hazards are boostable (since 10 Dec 2025); the tasks themselves are "once-off" [W:Sailing].

### 5.2 Shipwreck salvaging

- Salvaging hook on the boat (7 tiers, table in 2.3, crew deckhandiness cap 1/1/1/2/2/3/4); range **7 tiles from the edge
  of the wreck, measured from the hook's position** (crew since 3 Dec 2025); player cycle: 4 ticks currently (3 Dec 2025
  changed it to 5 with higher success rates, the 5 Dec 2025 hotfix restored 4 ticks and kept the success rates); crew
  cycle 5 ticks; crew XP modifier 10/20/30/40% by deckhandiness (3 and 4 reduced to 30%/40% on 5 Dec 2025); invisible +2
  level from a whirlpool surprise keg while salvaging by hand; "tick manipulation" can reach roughly 2-2.5x XP
  [W:Salv][W:Crew][W:Training].
- 8 wreck types: small L15 (10 XP salvage, 5.5 sorting, 1 min), fisherman's L26 (17, 9, 3 min), Barracuda L35 (31, 15.5,
  3 min), large L53 (48, 24, 3 min), pirate L64 (76, 31.5, 3 min), mercenary L73 (138, 63.5, 3:15), Fremennik L80 (162, 75, 3:40),
  merchant L87 (row not captured here; read the Wiki page when S6 starts). Format: level (salvage XP, sorting XP, average
  wreck life). Wreck positions: the Wiki lists them as map pins [W:Salv]; the server needs them (cache clusters empty).
- Spawn model: all wrecks in a cluster share a pool, typically 4 of 6 sites active (every hotspot has 2 inactive after
  5 Dec 2025); the despawn timer starts when anyone starts to salvage; "You salvage all you can from the shipwreck
  before ..." on depletion [W:Salv]. Hazard gating: "it is not safe to salvage while in hazardous waters".
- Salvage items: objs `SAILING_*_SHIPWRECK_SALVAGE` 32847-32861; rare list `SALVAGING_RARE_*` 32863-32873; varp
  `SAILING_SHIPWRECK_RAREITEMS` 5208; interfaces use `SALVAGE_INTERFACE_DUMMY` 32875. Sorting at a salvaging station (port or
  facility) gives sorting XP and items, with a small Soup chance. Notable outputs: boat bottles, facility bottles,
  Sailors' amulet, seeds, dragon materials [W:Salv].
- Cache: `Sailing_Shipwreck_Cluster` 29 rows and `Sailing_Shipwreck` 8 rows (both empty client-side), animations db 204;
  locs `SAILING_SHIPWRECK*` see symbols; pirate-wreck intro loc 58208.

### 5.3 Barracuda Trials (3 trials)

| Trial | Level | Master / location | Requirements | Source |
|---|---|---|---|---|
| The Tempor Tantrum | 30 | Rum-dashed Ralph / Storm Tempor | skiff only, iron helm (gentle rapids), oak mast (storm); crew must be dismissed | [W:TT] |
| The Jubbly Jive | 55 | Gurtob / Backwater | skiff, inoculation station, mithril helm | [W:BT][W:JJ] |
| The Gwenith Glide | 72 | Gwyna / Porth Gwenith | skiff, adamant keel, Regicide | [W:BT][W:GG] |

All levels non-boostable; **only skiffs**; leaving the area for 10 s loses progress; trials finish when you sail away
(17 Dec 2025); ranks Swordfish, Shark, Marlin unlocked in order; each crate/supply pickup may give a sawmill coupon [W:BT][W:TT].
Rewards: Swordfish -> key (stormy/fetid/serrated), Shark -> barrel stand / captured wind mote / heart of Ithell (and
Whirlpool surprise from Ralph), Marlin -> fabric roll (flag) and a 1/240 Barracuda paint [W:BT][W:TT].

**Rank data decoded from the cache** (`Sailing_Bt_Trial_Core.RANK_DATA`, db 143, columns present: trial name, tracker
icons, requirement text, rank data, three rewards). Tuple per rank = (target ticks, laps, supplies required, bonus
XP x10, trial XP x10); verified against all three Wiki tables (Tempor 1:48 = 180 ticks, 2:51 = 285, 4:30 = 450, bonus 1,000/2,000/3,000, trial
XP 385/650/1,250; Jubbly 2:03 = 206 ticks, 3:00, 5:21, bonus 5,000/7,500/10,000, XP 1,700/3,000/6,200; Gwenith 2:00, 3:42,
6:09) [D][W:TT][W:JJ][W:GG]:

| Trial | Unranked (XP) | Swordfish | Shark | Marlin |
|---|---|---|---|---|
| Tempor Tantrum | 1 lap, 10 supplies, 150 | 180 ticks, 1 lap, 14 supplies, bonus 1,000, 385 | 285, 2, 25, 2,000, 650 | 450, 3, 36, 3,000, 1,250 |
| Jubbly Jive | 1, 16, 1,000 | 206, 1, 20, 5,000, 1,700 | 300, 2, 38, 7,500, 3,000 | 535, 3, 56, 10,000, 6,200 |
| Gwenith Glide | 3, 30, 2,050 | 200, 3, 30, 25,000, 3,050 | 370, 5, 59, 35,000, 7,250 | 615, 8, 96, 50,000, 16,050 |

Lost supplies give a fixed XP each (Tempor 15; Gwenith 35); the lap XP column in the Wiki is "cumulative lap XP" [W:TT][W:GG].
Cache: interfaces `SAILING_BT_HUD` 931 (28 comps), `_SELECTION` 932, `_STATISTICS` 933 (scoreboard); varps `SAILING_BT_*` 4985-5000
(`_TIME_START` 4987, `_OBJECTIVES` 4990-4992, `_PERFORMANCE_1..5` 4993-4997, `_TRIAL_*_COMPLETED` 4998-5000); 143
varbits `SAILING_BT_*` 18410-18552 (incl. `_PB` times); varc `SAILING_BT_HUD_COMPACT` 1295; locs: boundary buoy 59236
(placements around each course, e.g. Tempor 2217..3131 x 2756..3063), crates `SAILING_BT_TEMPOR_TANTRUM_*` 59237-59280,
Jubbly 59121-59229 (pillars/toad supplies), Gwenith 58978-59093 (crystals, portals `Sailing_Bt_Gwenith_Glide_*` db 144-145),
scoreboard 58926-58928, master boats 58930-58933; NPCs `SAILING_BT_TRIAL_MASTER_*` 15094-15108, `BARRACUDA_*` 14816-14826
(Mako, Mora, Blenny, Percy, Tetra, Griems, Cochu, Ling, Barb, Belle, Ide), `SAILING_BT_JUBBLY_JIVE_BIRD` 15114,
`SAILING_BT_GWENITH_GLIDE_RESONANCE_CRYSTAL` 15112-15113, `SAILING_BT_RAT` 15109. Rapids stack decay (above) and storm /
lightning clouds / rocks are the course mechanics [W:TT][W:Rapids]. Mechanics beyond the Wiki (lap logic, portal
destinations, pillar sequences, start tiles, boundary rects) are server data, **OPEN-B2**.

### 5.4 Deep sea trawling (hybrid Fishing)

- Shoals follow fixed patrols with stops; cache db 190 `Sailing_Shoal` (6 rows: target speed, turn speed, acceleration,
  levels, bait, depth rates), 191 `_Specific` (16 spawns/patrol), 192 `_Droptable` (9) are **empty client-side**; shoal
  clickboxes `SAILING_SHOAL_CLICKBOX_*` 59734-59742 (9 types) are locs; shoal fish objs include raw giant krill, haddock,
  yellowfin, halibut, bluefin, marlin (+ cooked/purple variants) and fish crates `FISH_CRATE_*` 32366-32385 [W:Trawl][C:sym].
- Nets: rope L56, linen L65, hemp L76, cotton L84 (Con 45/61/65/73); sea depths shallow / +moderate / +deep (hemp,
  cotton); max fish per catch 2/3/4/5; deckhandiness 1/2/3/4; net locs 59755-59766 (per boat size), inventory
  `SAILING_TRAWLING_NET` 968, shared capacity 250 fish; wrong depth halves the catch [W:Trawl][C:sym].
- Rates: skilling roll every 3 ticks (player, passive), 2 ticks if the player clicks every tick, 5 ticks for crew (gather
  rate was 4 -> 3 on 20 May 2026); token Sailing XP per roll 7/9/11/15 (rope/linen/hemp/cotton, halved at wrong depth);
  bait (chum station / spreader) gives +20% fish per catch (one offcut per stop; baiting is per player since 15 July
  2026); Trawler's trust +5% extra fish; fishing rod alternative every 5 ticks at about -2% [W:Trawl][W:Sailing].
- Tracking: fathom stone (direction) / fathom pearl (hint arrow); icon on the world map. Disengage after 5 min inactivity
  (15 July 2026). `SAILING_PLAYER_TRAWLING_SHOAL_BAITED_STEP` 15687, `SAILING_FATHOM_STONE_LAST_SHOAL` 19144 [C:sym].
- Pets: Soup 1/360,000 per roll, Heron varies; Angler's paint [W:Trawl].
- The old minigame interfaces `TRAWLER_*` (366-368, Fishing Trawler) are unrelated to this.

### 5.5 Crew (NPC crewmates)

- Crew Registrar (The Pandemonium, Port Roberts, and at Barracuda trial starts) assigns recruited crewmates to slots; the
  recruiting conversation is with the crewmate on land; no cost [W:Crew]. Crew size by level: 40 -> 1, 55 -> 2, 70 -> 3, 85 -> 4,
  95 -> 5 (not boostable); boat capacity raft 0, skiff 2, sloop 5.
- 10 crewmates (cache `Sailing_Crew` db 173, stats Helmsmanship/Privateering/Deckhandiness; recruited/unrecruited/boat/
  cargo NPC variants `SAILING_CREW_*` 15244-15350):

| Cache id (by H/P/D match) | Crewmate | Level | Location | H / P / D | Other |
|---|---|---|---|---|---|
| 1 | Jobless Jim | 40 | Port Sarim | 2 / 1 / 3 | - |
| 9 | Ex-Captain Siad | 50 | Desert Mining Camp | 3 / 2 / 3 | The Tourist Trap |
| 2 | Adventurer Ada | 55 | The Pandemonium | 3 / 2 / 1 | - |
| 10 | Cabin Boy Jenkins | 60 | Crandor | 2 / 2 / 4 | Dragon Slayer I, Restless Ghost, ghostspeak |
| 6 | Oarswoman Olga | 65 | Burgh de Rott | 2 / 4 / 2 | In Aid of the Myreque |
| 3 | Jittery Jim | 70 | Port Khazard | 1 / 4 / 1 | - |
| 7 | Bosun Zarah | 80 | Etceteria | 3 / 3 / 2 | Royal Trouble, Hard Fremennik diary |
| 4 | Jolly Jim | 85 | Brimhaven | 1 / 1 / 4 | - |
| 8 | Spotter Virginia | 90 | Ruins of Unkah | 4 / 2 / 2 | 100 Tempoross subdues |
| 5 | Sailor Jakob | 95 | Catherby | 4 / 1 / 1 | - |

(Cache `Sailing_Crew` rows carry only `UNIQUE_ID` 1-10, boat/cargo NPC, sprite and H/P/D; the id-to-name mapping above is
derived by matching the H/P/D triples against the Wiki, every triple is unique [D][C:db 173][W:Crew]. Whirlpool surprise
raises H and D to a minimum of 2 for crewmates [W:Crew].)
- Orders (strings per job in the server columns): idle, sails, helm, helm+sails, salvage, cargo, cannon, repairs, chum, trawl.
  Helmsmanship = helm and sail trimming (trim XP 40/50/60/70% of full for H 1-4); privateering = cannon max-hit
  multiplier `(P + 13) / 20`, with fixed multipliers 0.7/0.75/0.8/0.85 per tier listed too; deckhandiness = hook XP
  10/20/30/40%, repair bonus, crates carried [W:Crew]. Crew operate a facility only if the *player* has its level
  (7 Jan 2026) and cannot operate hazards' disabled stations (panic). Crewmates remember tasks across logout.
- Interface `SAILING_CREW` 938 (cs2 1702), varbits `SAILING_CREW_*` 18310-19613 (48; `_SELECTED_CREW_ID` 18310, `_SELECTED_SLOT_ID`
  18311, slot/visibility/unlock sets), varps `SAILING_CREW_UNLOCKS_1` 5167, `_VISIBILITY_1` 5168, `_PERM_1/2` 5169-5170,
  `_HELD_CARGO_0..4` 5174-5178, `SAILING_SIDEPANEL_CREW_ASSIGNATION` 19106.
- NPCs on deck are required to see crew at their posts: engine gap OPEN-WE2.

### 5.6 Ocean encounters

- Timer: while moving a 120-tick countdown (115 with Kraken ink stout) runs only when the boat enters a new tile; at 0 an
  event spawns with chance 1/6, then 2/6, ... until guaranteed after 6 intervals; spawns ahead of the bow, vanish after 1
  minute; +20 ticks back to the timer each disembark (max 120); reset on logout/spawn [W:Enc].
- Types and weights (cache db 150 has 7 rows, weights server-only): strong winds ~60%, mysterious glow ~12%, lost crate ~12%
  (tiers by Sailing level, sizes: wooden 5, oak 25, teak 50, mahogany 70, camphor 85, ironwood 95), castaway, others
  (clam/pearls, turtle clue, ...) [W:Enc][C:db 150-153]. NPCs `SAILING_CHANCE_ENCOUNTERS_*` 15115-15150,
  `SAILING_CHANCE_ENCOUNTER_*` 15124-15136; varbits `SAILING_CHANCE_ENCOUNTERS_CLAM_STATE` 18555.
- Strong winds allow up to 4 rapid trims (extra speed and XP) [W:Rapids].

### 5.7 Boat combat and crew combat (main session only)

Cannons (cache db 162: ranged level, accuracy, damage, ammunition tier, `ATTACK_RATE` 7, `ATTACK_RANGE` 10) fire at sea
creatures within 180 degrees of the cannon; Wiki says "15 tiles" with a citation-needed tag, the cache says 10 (use 10);
the player's HP is replaced by boat HP in combat; boat HP 0 = capsize; repair kits (objs `BOAT_REPAIR_KIT` 31964+, 7
kinds) heal; ammo = cannonballs (db 165, 22 rows, ids/types), Ballistic attractor saves 60%; creatures (NPC symbols
15194-15235: sharks, narwhal, orca, krakens, rays, birds, sea mogre, dolphins) with bounty drops (objs 32809-32846). This is
combat and stays in the main session (S10); everything else in this plan avoids it [C:db 162, 165][W:Sailing].

## 6. Cache inventory

### 6.1 Interfaces (symbols `Interfaces`)

| Name | Id | Components | Drivers |
|---|---|---|---|
| SAILING_MENU | 72 | 4 | cs2 6575, 7336 (small dialog frame) |
| SAILING_INTRO_HUD | 345 | 9 | 13 scripts (Pandemonium HUD) |
| TRAWLER_OVERLAY / REWARD / START | 366 / 367 / 368 | - | Fishing Trawler (unrelated) |
| SAILING_BT_HUD | 931 | 28 | cs2 1705, 8606, 8608; db 143; varps 4985-5000; varc 1295 |
| SAILING_BT_SELECTION | 932 | - | cs2 1702, 1705 |
| SAILING_BT_STATISTICS | 933 | - | cs2 1705, 8620 |
| SAILING_BOAT_SELECTION | 934 | 35 | cs2 8621-8654 (14); db 149, 166; varbit 18553; varp 5003-5006 |
| SAILING_LOG (Captain's log) | 935 | - | 24 scripts 8658-8927; db 154, 201, 197; varcs 1298-1310 |
| SAILING_SPYGLASS | 936 | - | cs2 1705 |
| SAILING_SIDEPANEL | 937 | 41 | cs2 1705, 8713, 8778; db 167-172, 229; varps/varbits in 3.4 |
| SAILING_CREW | 938 | - | cs2 1702; db 173 |
| SAILING_CUSTOMISATION | 939 | - | 15 scripts 8809-8845; db 189, 188, 166; varp `SAILING_BOAT_CUSTOMISATION` 5189, `_BOAT` 5190; varbits `SAILING_BOAT_CUSTOMISATION_*` (slot, hotspot id, type, hide-more-detail); varcs 1314-1318 |
| PORT_TASK_BOARD | 941 | 8 | cs2 7856, 8901, 8912; db 197 columns above |
| PORT_TASK_INFO | 942 | - | cs2 8901 |
| SAILING_BOAT_CARGOHOLD | 943 | 26 | cs2 8885; inv 963-967; varp 5204 |
| SAILING_BOAT_CARGOHOLD_SIDE | 944 | - | whitelist varp 5205 |
| UNDERWATER | 940 | - | diving charting |

cs2 note: client scripts are anonymous (`.id` only). I mapped each interface to its scripts by scanning for packed
component constants (`interface << 16 | child`) and for Sailing varp/varbit/db references; scripts 8605-8950 are the Sailing
block [C:cs2].

### 6.2 DB tables (ids from `DbTables`, populated columns verified by `dbrows`)

Legend: rows; what is populated; what is stripped (server-only).

| Id | Table | Rows | Populated (client) | Stripped (server) |
|---|---|---|---|---|
| 143 | Sailing_Bt_Trial_Core | 3 | name, tracker icons, requirement text, RANK_DATA, 3 rewards | id, level, crew, start/boundary coords, paint odds |
| 144-146 | Gwenith crystal/portals, Jubbly pillars | 8/26/8 | none | all |
| 147-148 | Boat_Location / Facilities_Default sprite | 4/17 | all | - |
| 149 | Boat_Selection_Type | 12 | most | show-recent/health/empty-slots flags |
| 150-153 | Chance encounters (+rescue, lost goods) | 7/7/12/8 | none | all |
| 154-161 | Charting core + 6 types + tool recovery | 358, 94/57/54/65/31/57, 5 | ids, hint, sea, repeat XP flag, type, hazard, dock; type tables only the core link | coords, feedback strings, routes |
| 162, 165 | Combat facility, ammunition | 7, 22 | dummy obj, ranged level, accuracy, damage, tier, rate, range; ammo id/obj/type | anims, tiers' accuracy/strength |
| 163 | Combat support facility | 6 | none | all |
| 164 | Boat_Facility_Stats | 118 | all numeric stats (2.2) | - |
| 166 | Sailing_Boat | 4 | name, level, capacity, size, retrieval cost, part option lists, hotspots, facility amount, crew capacity, sprite | origin/entry/size, entity data, accel/turn, wake/emitters, gun port |
| 167-172, 229 | Sidepanel facility/widgets | 12/10/8/1/3/1 | all | - |
| 173 | Sailing_Crew | 10 | id, boat/cargo NPC, sprite, H/P/D | world NPC, requirements, order strings |
| 174 | Sailing_Shipyard | 1 | none | all (entry, build space, gangplank, NPC) |
| 175 | Boat_Hotspot | 21 | OPTION (allowed facility rows) | shape |
| 176-186, 230 | Boat_Facility / Keel / Hull / Sail / Sail_Fx / Steering / Steering_Fx / Hull_Ornament / Flag / Brazier / Trim / Sail_Pattern | 99/15/23/22/6/22/3/0/9/3/37/31 | name, description, locs, levels, materials, stats link | *_Fx rows all empty; hull origin |
| 187-189 | Name options, loc angles, customisation tab | 3/23/16 | all | large modifiers |
| 190-193 | Shoal, specific, droptable, anims | 6/16/9/3 | none | all |
| 194 | Sailing_Dock | 61 | id, name, level, quest, sprite | coords, rotation, zones, guaranteed bounty |
| 195, 231-233 | NPC boat, base stats, weapon, steering | 17/2/2/2 | none | all |
| 196 | Cargohold_Whitelist_Obj | 1 | all | - |
| 197 | Port_Task | 606 | see 4.2 | XP, coins, requirements |
| 199, 204 | Anchor / salvaging hook anims | 2/3 | none | all |
| 200 | Sea_Hazard | 11 | id, name | damage, frequency, area |
| 201 | Sailing_Sea | 81 | ids, names, type, ocean, levels, centre | rapids flag, emitters |
| 202-203 | Shipwreck cluster / wreck | 29/8 | none | all |
| 206-207 | Chartering destinations / costs | 24/1 | destinations (quest "Chartering" boat travel, unrelated to Sailing boats) | costs |
| 215 | Gun_Ports | 8 | none | all |

### 6.3 Varps, varbits, varcs, invs

- Varps (symbols `SAILING_*`/`PORT_*`): intro 4959; XP drops 4964-4965; Barracuda 4985-5000; boat selection 5003-5006;
  charting 5016-5038; combat 5040-5043; boarded name 5053; last dock 5062; boats 1-5 data/name/customisation 5063-5107 and
  5463-5472; sidepanel 5111-5165, 5473-5475, 5554-5574; crew 5167-5180; shipyard 5187-5190; cargohold 5204-5205;
  shipwreck rare items 5208; boat teleport last 5570-5571.
- Varbits: `SAILING_XP` 4149; sidepanel (213); boats `SAILING_BOAT_n_*` (247 incl. per-slot `_HOTSPOT_0..12`, `_SAIL_PATTERN`
  13313-13317, `_BRAZIER`, `_FLAG`, `_BOTTLE_PREVIOUS_PORT`); `SAILING_CREW_*` (48); `SAILING_BT_*` (143); `SAILING_CHARTING_*` (369);
  `SAILING_SEA_*` (169); `PORT_TASK_*` (18); settings (18130-18144, 18310-18316, 19101-19150).
- Varcs: 1295-1342 (Barracuda HUD, filters, scroll, log window, timers, last HP).
- Invs: 963-967 boat cargo holds, 968 trawling net.

### 6.4 NPCs, locs, objs (symbol groups)

- NPCs: transport traders `SAILING_TRANSPORT_TRADER_*` 1328-15546 (175 port NPCs), shipwrights 15363-15377, crew 15244-15350,
  creatures 15194-15236, charting NPCs 15152-15177, Barracuda masters 15094-15114, Port Roberts 14881-14915, Pandemonium
  14866-14871, intro 14957-14977, `SAILING_CHARTING_TUTOR_*` 288.
- Locs: boat model pieces (hull `SAILING_BOAT_HULL_KANDARIN_*`, sails 29497-59550, steering 29536-60564, keels 59516-59529,
  trims 59624-59659, flags 59485-59493, braziers 59707-59710), facilities (cannons 59446-59453, kegs 59691-59698,
  salvaging station 59699-59701, extractor 59702-59703, 2x5/3x8 facilities 59704-59714, teleport focus 59715-59716,
  trawling nets 59755-59766, fathom 59767-59768, anchors 59687-59690, cargo holds 60245-60642), docks/buoys/gangplanks, port
  task boards 58016-60309, Pandemonium scenery 58024-58094, Port Roberts 58095-58108, Barracuda 58926-59280, charting
  59281-59445.
- Objs: cargo crates 32435-33001, bounty parts 32809-32846, salvage 32847-32861, rare salvage 32863-32873, fish crates
  32366-32385, boat bottles/deeds 31989-32080, repair kits 31964-31982, paints `SAILING_PAINT_*` 32087-32113, sawmill
  coupons 32083.., charting 31803-31905, Sailors' amulet 32398-32399, skillguide objs 32118-34058, port task icon dummies
  (157) 32913-34385, combat dummies 33204-33210.

### 6.5 Why the server-only data is absent (verification)

`dbrows 190` (Sailing_Shoal) prints 6 rows with no columns; `dbrows 197` (Port_Task) prints 606 rows with 16 of 29 columns;
`dbrows 194` (Sailing_Dock) has no `LAND_COORD`/`SEA_COORD`; `dbrows 202/203` are empty. The `dbtable` definitions list all
columns, so the stripping is data, not a decoder issue [C:db].

## 7. Implementation plan

### 7.1 Conventions and ownership

Content goes under `content/sailing` (depends only on plugin-api + symbols). Data tables are loaded from the cache by a
small `SailingData` object (db 164-197 readers, symbol ids) and from a checked-in generated resource for Wiki-only data
(`data/sailing/*.json` produced by `tools/wiki-import`: task XP, shoal/shipwreck tables, encounter weights, wreck and
cluster positions). Every mechanic gets `docs/mechanics/sailing-<topic>.md` with sources, `OPEN` flagged. Engine, protocol,
tick order, combat and persistence changes stay with the main session; implementers only follow patterns.

### 7.2 Engine and protocol prerequisites (main session)

| # | Item | Needed by | State |
|---|---|---|---|
| E1 | **Zone updates on decks** (loc add/remove/change on the entity's instance, ground items) so customisation, facility states and hotspot locs show to observers - OPEN-WE1 | S1b, S4, S6, S7, S8 | main session is adding |
| E2 | **NPCs on decks** (crew, cargo-carriers, task NPCs) in the entity's NPC info - OPEN-WE2 | S9, S3 (crew carry), S1 (quest NPCs) | main session is adding |
| E3 | `SetHeading` incoming + `SetInteractionMode` outgoing + `OpWorldEntity*` mapped in protocol-api/adapter; heading in 16 steps | S2 | missing (3.1) |
| E4 | Sea/land mask for boats and per-tick footprint collision, hazard-area lookup, rapids/encounter-area polygons | S2 | OPEN-M2; polygon areas exist (`PolygonAreas.kt`) |
| E5 | Pivot offset from config bounds (OPEN-WE3) and per-tier template zones copying in a deck of more than one zone | S1 | partial |
| E6 | Per-world-entity tick system: heading, throttle, acceleration, rapids stacks, trim timers, hazard slows - deterministic, no allocation | S2 | design in S2 below |
| E7 | Boat ownership persistence (5 boat slots, name, parts, cargo hold, hp) in the player save | S1 | persistence = main |
| E8 | Instances for Barracuda courses if they are per-player (they are open sea in the cache, so likely no instance) | S8 | OPEN-B2 |
| E9 | Chat/hint-arrow, camera (spyglass cutscene), minimap-less interaction mode | S5 | partly existing |

### 7.3 Milestones

Dependency graph: S0 -> {S1}; S1 -> {S2, S3, S4}; S2 -> {S5, S6, S7, S8, S11}; S3 -> S10 (bounty); S4 -> {S6, S7, S9}; S9 needs E2.
S5, S6, S7, S8, S11 are independent of each other once S2 exists and can be executed in parallel by separate agents.

| Id | Milestone | Data / where from | Engine features | Depends on | Owner |
|---|---|---|---|---|---|
| **S0** | Data layer: typed readers for db 143-233 (boat, part, facility, hotspot, dock, sea, charting, port task, crew, boat selection), symbols facade, Wiki importers (courier/bounty XP, level-up table, hull/sail/helm/keel/facility stats cross-check), `docs/mechanics/sailing-*.md` skeletons | cache db + [W:*] | none | - | implementer (parallel: 3 agents: cache readers / wiki-import / mechanics docs) |
| **S1** | **Pandemonium + raft + docking/boarding**: quest stages (varbits 18314-18316, varp 4959, dialogue, cutscene boat), Will and Anne's boat, intro HUD 345, raft purchase/reward, boat ownership (slots, names), gangplank Board/Board-friend/Disembark/Board-previous, buoy Dock, mooring points, boat selection interface 934 (all 12 types, shipwright NPCs 15363+), deck instance creation from the per-tier template (2.4), respawn dock | db 166/149/194, locs 59831-59860 / 59769-59830, NPC ids 14957+ / 15363+, 2.4 coordinates | world entity + deck copy exist; E1 for part locs; E5 | S0 | main (quest/boat life cycle touches persistence) + implementer for dialogue |
| **S2** | **Steering/movement + HUD**: helm Navigate/Stop, heading (E3), throttle (speed arrows, reverse), sail set/unset, per-tick motion at fine units with acceleration/speed cap, turning (OPEN-M1), sea mask (E4), trim windows (50-tick gust, 20-tick window, boost durations, XP, wind motes), rapids stacks (15 ticks, cap 15), hazard slow/damage (stormy: oak mast...), anchor, side panel 937 publishing (varps/varbits 3.4), boat HP/capsize, camera follow, stop on disembark | cache db 164 stats, 3.2 table, hazards table 4.1 | E3, E4, E6 | S1 | main (tick order) + implementer for panel varbits |
| **S3** | **Ports and port tasks**: notice boards (interface 941/942, slots varbits), ledger tables (take/deposit crates, both hands free), cargo hold interface 943 + invs 963-967, courier completion + reward bags/coin bags/shark paint, port master (cancel/claim), captain's log task list, task slot unlocks by level; bounty accept/claim only (kills in S10) | db 197 (606 rows) + imported XP, objs 32435+ (440 crates), NPCs 15363+ | item transaction API for crates/hold | S1 | implementer |
| **S4** | **Boat building and facilities**: shipyard (build space, boat schematics board, customisation interface 939 + 15 scripts), hull/sail/helm/keel/flag/trim/brazier/facility builds with materials, Construction XP and boosts rules (support vs active facilities), shipwright fees for mithril/mahogany, loc swapping on the deck (E1), facility bottles and boat bottles, repair at Seb (50 gp/HP), recover/summon/destroy/rename, cargo hold sizes, stats recompute | db 164, 176-189, 230; Wiki materials and Construction XP | E1 (loc changes), item transaction API | S1 | implementer, main reviews item flow |
| **S5** | **Sea charting**: Captain's log interface 935, spyglass interface 936, 358 tasks across 6 tool types, per-type server data (coordinates from the Wiki: tile markers, SeaChartRow locations), sea/ocean completion bonuses, rapids placement, tool recovery, quests Prying Times / Current Affairs rewards | db 154-161, 201; Wiki `SeaChartRow` lists (level, type, location) + XP; locs 59281-59445 | spyglass camera cutscene; boost rules | S2 | implementer (large, split by tool type) |
| **S6** | **Salvaging**: hooks (7 tiers), wreck clusters and spawn pool, 4/5-tick cycle, range 7 from hook, depletion timers, salvage items, salvaging station (port + facility), sorting XP and drops, crew salvage, tick-manipulation behaviour | Wiki wreck map pins and tables ([W:Salv]), objs 32847-32873 | E1 for wreck locs on map (open sea, no deck needed except hook animations) | S2, S4 | implementer |
| **S7** | **Trawling**: nets (4), shoal entities (moving, depth changes, stops), baiting (chum station/spreader), fathom stone/pearl hint arrows, fish crates, Fishing XP, nets collect/transfer | Wiki [W:Trawl] shoal pages (not in cache), objs/locs 59734-59768 | moving non-boat entity on the sea (NPC or world entity) | S2, S4 | implementer |
| **S8** | **Barracuda Trials**: three trials with rank data (5.3 table), HUD 931/932/933, crates/supplies, rapids stacking, lightning clouds, pillars, portals/crystals, scoreboard PBs (varbits `SAILING_BT_*_PB`), keys/barrel stand/wind mote/heart of Ithell/fabric rolls, flags | db 143 + Wiki course data and tile markers, locs 58926-59280 | boundary rects, per-trial server state | S2 | implementer |
| **S9** | **Crew**: Crew Registrar interface 938, recruitment dialogues (10 NPCs with quest gates), assignment, crew jobs: helm/sails, cargo carry, banking at deposit-box ports, salvage/trawl/cannon, repairs, panic in hazards, persistence of assigned tasks | db 173, NPCs 15244-15350, Wiki [W:Crew] | E2 (NPCs on decks), NPC pathing on a deck | S2, S4 | implementer; cannon/combat jobs main |
| **S10** | **Boat combat, sea creatures, bounty tasks**: cannons, boat HP/armour/defence, creature NPCs and drops, bounty parts, Combat Achievements irrelevant, NPC boats | db 162/165, Wiki creature pages | combat core | S2, S3 | **main session only** |
| **S11** | **Encounters, kegs, wind catcher, teleport focus/boat spells, hazards polish**: ocean encounter timer and spawns, lost crates, castaways, kegs and ale effects, boat/facility bottles, summon/teleport spells, Soup | Wiki [W:Enc], db 150-153 (stripped) | none new | S2, S4 | implementer |
| **S12** | **Sailing quests** (Prying Times, Current Affairs, Crab Quest, Troubled Tortugans, The Red Reef, Fallen From Grace) and the Sailing-island content (slayer monsters, exclusive resources) | Wiki quest pages (not researched here) | per quest | S1-S9 | implementer per quest |

### 7.4 Per-milestone acceptance checks (tests are limited to BRIEF section 9; these are manual checks in the tick
inspector or real client)

S1: complete Pandemonium in RuneLite; board/disembark at both starter ports and a mooring point; boat survives logout.
S2: steer from the Pandemonium to Port Sarim; trim a gust; hit a rock; enter the Storm Tempor with and without an oak
mast; cross a rapid with iron helm and with bronze helm. S3: complete Port Sarim <-> Pandemonium tasks. S4: build a
wooden hull -> oak hull on a raft and check deck/loc swaps in a second observer's client. Others per area.

### 7.5 Risks

| Risk | Mitigation |
|---|---|
| Sea mask encoding unknown (M2) | main session spike: inspect overlay ids under sea tiles and loc-level flags first; fallback: derive from the Wiki ocean map plus "water overlay minus shore band" |
| Boat motion numbers (turn rate, throttle steps) unknown | start from cache stats and Wiki, mark OPEN, expose a named config flag; compare with RuneLite video timing |
| Deck walk areas / pivot offsets for skiff and sloop | verify with `collision` of each tier zone and the existing raft test at (3840,6456) |
| Server-only data volume (shipwreck clusters, shoal paths, charting coordinates, tile markers) | Wiki `SeaChartRow`, tile-marker JSON modules and map pins are machine-readable; wiki-import in S0 |
| Wiki balance drift versus the cache snapshot | cache wins for client-visible values; Wiki dated change log recorded per number |
| Content volume (358 tasks, 606 port tasks, 443 crates) | everything table-driven from the cache; no per-task code |

## 8. OPEN questions and known source conflicts

| Id | Question / conflict | Candidates |
|---|---|---|
| OPEN-M1 | Turn rate per tick and throttle steps (speed setting 1..n) | derive from client behaviour; 2023 blog says 2/4/6 ticks per 90 degrees but is design intent only |
| OPEN-M2 | How the sailable-sea mask and rock/land collision for boats is encoded | tile overlay type, a map flag (check `tile settings` bit 3+), or a server table |
| OPEN-M3 | Acceleration units (cache 64 vs Wiki 0.5/1.0) and the default for tiers without the stat | likely 128 fine units per 1.0 and default 32 |
| OPEN-M4 | Units of speed setting in the side panel ("quarter tiles per tick") vs cache fine units | 1 quarter = 32 fine units |
| OPEN-T1 | Courier/bounty XP formula | ship imported per-task XP |
| OPEN-B1 | Exact walkable deck rectangle and pivot offset for skiff/sloop; config bounds 2x5 / 3x10 vs hotspot extents | read tile flags of each template zone; compare with RuneLite |
| OPEN-B2 | Barracuda course rules beyond the Wiki (start tile, boundary, portals, pillars) | wiki-import tile markers; observe in RuneLite |
| OPEN-W1 | Config id of Will and Anne's boat and the trial-master boats (10/11/12) | check by loading each and matching bounds |
| OPEN-S1 | Hotspot tuple codes (a, b) | loc rotation + footprint; confirm by building each facility |
| OPEN-S2 | Which of the Wiki's post-launch changes (cannon rate, 4 vs 5 tick salvage) the server should reflect | follow the Wiki's current state; note `docs/DECISIONS.md` |
| OPEN-S3 | Whether `CONTENT_RESTRICT_SAILING_SERVERSIDE` (varbit 18166) is a members/world gate | set on non-Sailing worlds |
| OPEN-S4 | Spell and teleport-tablet ids for Summon Boat / Teleport to Boat | spellbook enums |
| Conflict | Skiff recovery cost 3,750 (cache) vs 4,125 (Wiki: grows with damage/parts) | use cache base + percent per hull |
| Conflict | Sloop hotspots 13 (cache) vs 11 (Wiki infobox) vs 9 (Boat table) | cache; Wiki lags the 18 Mar 2026 +2 |
| Conflict | Cannon range 10 (cache) vs 15 (Wiki, unsourced) | cache |
| Conflict | Linen trawling net Con 53 (one cache row) vs 61 (other rows and Wiki) | 61 |
| Conflict | Salvage cycle 4 ticks (5 Dec 2025 hotfix) vs 5 | 4 for players, 5 for crew |
| Note | `docs/mechanics/world-entities.md` "Verified" test used the Pandemonium intro boat zone (1496,4512), not the raft (2.4) | update the doc when S1 lands |

## 9. Sources (all fetched with `action=parse&prop=wikitext`, UA tickperfect-wiki-import/1.0)

Wiki base `https://oldschool.runescape.wiki/w/`:

- [W:Sailing] Sailing; [W:Pandemonium] Pandemonium; [W:LevelUp] Sailing/Level_up_table; [W:Training] Sailing_training;
  [W:Boat] Boat; [W:Raft] Raft; [W:Hull] Hull; [W:Helm] Helm; [W:Mast] Mast_and_sails; [W:Ship] Shipbuilding;
  [W:Fac] Facilities; [W:Cargo] Cargo_hold; [W:Moor] Mooring_point (= Docking_points); [W:Gangplank] Gangplank_(Sailing);
  [W:Buoy] Buoy_(Sailing); [W:Anchor] Anchor_(facility); [W:Opts] Sailing_Options; [W:Courier] Courier_tasks;
  [W:Bounty] Bounty_tasks; [W:Chart] Sea_charting; [W:Salv] Shipwreck_salvaging; [W:Trawl] Deep_sea_trawling;
  [W:BT] Barracuda_Trials; [W:TT] The_Tempor_Tantrum; [W:JJ] The_Jubbly_Jive; [W:GG] The_Gwenith_Glide;
  [W:Crew] Crew_Management; [W:Enc] Ocean_encounters; [W:Haz] Sailing_hazards; [W:Rapids] Rapids;
  [W:Nav2023] Update:Adding_A_New_Skill:_Sailing_Navigation_Mechanics; [W:Alpha] March_2025_Sailing_Alpha;
  [W:Beta] June_2025_Sailing_Beta; [W:Pre] Update:Sailing_Technical_Pre-Release.
- Full page URLs: prefix the name with the base above (spaces as underscores).

Cache/engine: `docs/mechanics/world-entities.md`, `docs/mechanics/instances.md`,
`cache/src/main/kotlin/tp/cache/config/worldentity/WorldEntityType.kt`,
`protocol/osrs-242/rsprot/model/.../SetHeading.kt`, `SetInteractionMode.kt`, `OpWorldEntity*.kt`,
`symbols/build/generated/symbols/tp/symbols/*.java`, `tools/cache-dump` (`dbrows`, `config worldentity`, `find-locs`, `map`,
`collision`), client scripts `/tmp/claude-0/ge-research/scripts/*.rs2asm`.
