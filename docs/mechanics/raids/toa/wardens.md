# The Wardens (final room: Elidinis' Warden and Tumeken's Warden)

Date anchor 2026-11-03 (cache 2757); sources read 2026-11-08. OSRS Wiki (API wikitext): "Tumeken's Warden" (TW), "Elidinis' Warden" (EW),
"Obelisk (Tombs of Amascut)" (Ob), "Core" (Core), "Energy Siphon" (ES), "Osmumten" (Osm), "Phantom (Tombs of Amascut)" (Ph), "Zebak's Phantom",
"Ba-Ba's Phantom", "Akkha's Phantom", "Kephri's Phantom" (PhZ, PhB, PhA, PhK), "Tumeken's guardian" (Pet), "Ancient remnant" (Rem), "The
wardens" (Book), "Tombs of Amascut" (Main: raid-level text, invocation table, changelog; the copies saved as "Invocations" and "Raid level"
are the same page), "Tombs of Amascut/Strategies" section "The Wardens" and its invocation table (Strat), "Chest (Tombs of Amascut)" (Chest),
"Liquid adrenaline" (LA), the update posts "Tombs of Amascut" (24 Aug 2022 with its hotfix table, U-Launch), "Summer Sweep Up: Combat" (25 Jun 2025,
U-Jun25) and "Summer Sweep-Up Gear & PvM Changes" (22 Jul 2026, U-Jul26). RuneLite plugins (facts only, nothing copied):
toa-mistake-tracker (MT: `WardensP1P2Detector`, `WardensP3Detector`, `ToaMistake`, README), tombs-of-amascut by LlemonDuck (LD:
`PointsTracker`), tombs-of-amascut-stats (ST), toa-points-tracker (PT: `ToANpc`, `ToARegion`); toa-modern, toa-puzzle-helper and toa-supplies hold
nothing on the Wardens. Cache 2757 (`cache-dump config npc|loc|obj <id>`, `cache-dump map 59 80` / `61 80`; the stats array order is attack,
defence, strength, hitpoints, ranged, magic; generated symbols `Npcs`, `Seqs`, `SpotAnims`, `Locs`, `Varbits`, `Varps`, `Components`, `DbRows`,
`Jingles`). The raid-wide ToA sheet `../toa.md` is not written yet (raid level, invocations as a system, points, rewards, deaths and attempts live
there); this file holds what is specific to the Wardens. Tick timelines list only what a source states; everything else is `OPEN-WD<n>`. Seconds
are converted at 0.6 s per tick. MT counts in client cycles: `CYCLES_PER_GAME_TICK` = 600 ms / 20 ms = **30**.

## Data

- **NPCs (cache name; the wiki ids agree unless noted).** Form names: PHASE1 = charging, PHASE2 = fought with a shield, PHASE3 = restored.

  | Form | Elidinis | Tumeken | Notes (cache) |
  |---|---|---|---|
  | Phase 1 inactive | 11746 `TOA_WARDEN_ELIDINIS_PHASE1_INACTIVE` | 11747 `..TUMEKEN_PHASE1_INACTIVE` | no ops, stand seq `NPC_WARDENS_DORMANT01` 9665, size 5, level 489 |
  | Phase 1 charging | 11748 `..PHASE1` | 11749 `..PHASE1` | same, no ops (cannot be attacked) |
  | Phase 2 "mage" | 11753 `..PHASE2_MAGE` | 11756 | op2 Attack, size 5, level 489, stand seq `NPC_WARDENS_IDLE01` 9653, overhead icon archive 440 sprite **7** |
  | Phase 2 "range" | 11754 `..PHASE2_RANGE` | 11757 | as above, overhead icon sprite **8** |
  | Phase 2 core-ejected ("exposed") | 11755 `..PHASE2_EXPOSED` | 11758 | no ops, stand seq `NPC_WARDENS_KNEELING01` 9671, no overhead icon |
  | Phase 3 inactive (the one destroyed in phase 1 / lying in phase 2) | 11759 `..PHASE3_INACTIVE` | 11760 | no ops, stand seq `NPC_WARDENS_IDLE02` 9654; the wiki calls these "Destroyed" in phase 1 (EW/TW infobox ids 11759 / 11760) |
  | Phase 3 active | 11761 `..PHASE3` | 11762 | op2 Attack, **level 544**, stand seq `NPC_WARDENS_IDLE05` 9657 (IDLE04 is 9656); the wiki's "Enraged" version uses the same ids 11761 / 11762 as "Damaged" |
  | Phase 3 charging ("Invulnerable", siphon phase) | 11763 `..PHASE3_CHARGING` | 11764 | op2 Attack, level 544, stand seq `NPC_WARDENS_CHARGING01` 9683, overhead icon sprite **9** |
  | Phase 3 death | 11766 `TOA_WARDEN_P3_DEATH_ELIDINIS` (name "null") | 11765 `TOA_WARDEN_P3_DEATH_TUMEKEN` | size 5, combat level 1, stand seq `SPOTANIM_WARDENS01_DEATH02_IDLE01` 9696 (the lower id is Tumeken's here, unlike the other rows) |

  Other NPCs: obelisk **11750** (inactive, no ops), **11751** (phase 1, op2 Attack, stand seq `FX_OBELISK_WARDENS_PHASE01_IDLE01` 9720), **11752**
  (phase 2, no ops, stand seq `FX_OBELISK_WARDENS01_CHARGE` 9723), all size 3, name "Obelisk", level 0; core **11770** (Tumeken) / **11771** (Elidinis),
  name "Core", size 1, op2 Attack, stand seq `SPOTANIM_WARDENS01_CORE01_BEAT01` 9704; Energy Siphon **11772** `WARDENS_P3_ORB_BLUE` (name "Energy
  Siphon", size 1, op2 Attack, stand seq `FX_WARDENS_ENERGY_GROUNDED01` 9736; the wiki lists this id); **11773** `WARDENS_P3_ORB_RED` (name "null",
  combat level 74, no ops; role **OPEN-WD11**); **11769** `TOA_WARDENS_ENERGY` (name "null", size 1, stand seq 7571; role **OPEN-WD2**, presumed the phase 1
  orb); phantom of Amascut **11767** (`TOA_AMASCUT_P3`, stand seq `NPC_AMASCUT_GIANT01_IDLE01` 9716) and **11768** (`TOA_AMASCUT_P3_ENRAGE`, "gaping mouth",
  seq `NPC_AMASCUT_GIANT01_SUCK01` 9717; further seqs `_ENRAGE01` 9718, `_EXIT01` 9719), name "Phantom", **size 8**, level 0, no ops; the boss phantoms
  Zebak's **11774** `TOA_WARDENS_ZEBAK`, Ba-Ba's **11775**, Kephri's **11776**, Akkha's **11777** (names with "'s Phantom", size 5, no ops).
  Osmumten **11689** (op1 Talk-to, op3 Proceed), **11690** `TOA_OSMUMTEN_WARDENS_VIS` (op1 Talk-to, op3 **Begin**), vault forms **11691-11693**, cutscene forms
  `TOA_OSMUMTEN_*` **11829-11834** (11834 = `TOA_OSMUMTEN_WARDENS`, name "null"). Cutscene NPCs `OSB10_TOA_WARDEN_TUMEKEN_PHASE1_CUTSCENE` 12130,
  `..ELIDINIS_CUTSCENE` 12131, `..WARDENS_OBELISK_CUTSCENE` 12132. Not part of the raid: `DEADMAN_BREACH_WARDEN_TUMEKEN_RANGE / _MAGE` 15551 / 15552,
  `DEADMAN_ALL_STARS_MISSION_WARDEN_TUMEKEN_MAGE` 15698.
- **Stats (cache `stats`; base values that the raid scales at runtime).** Wiki infobox agrees on every value shown.

  | NPC | attack | defence | strength | HP | ranged | magic |
  |---|---|---|---|---|---|---|
  | P1 forms 11746-11749 | 200 | 100 | 150 | **800** | 100 | 100 |
  | P2 forms 11753-11758 | 300 | 100 | 150 | **140** | 190 | 190 |
  | P3 forms 11759-11764 | 150 | 150 | 150 | **880** | 150 | 150 |
  | Obelisk 11750-11752 | 200 | 100 | 150 | **260** | 100 | 100 |
  | Core 11770 / 11771 | 1 | 1 | 1 | 1 | 1 | 1 (raid-scaled, **OPEN-WD6**) |
  | Energy Siphon 11772 | 1 | 150 | 1 | 1 | 1 | 150 |
  | Zebak's Phantom 11774 (level 371) | 250 | 70 | 140 | 580 | 120 | 100 |
  | Ba-Ba's Phantom 11775 (level 359) | 150 | 80 | 160 | 380 | 0 | 100 |
  | Kephri's Phantom 11776 (level 341) | 0 | 80 | 0 | 150 | 0 | 125 |
  | Akkha's Phantom 11777 (level 337) | 100 | 80 | 140 | 400 | 100 | 100 |

  The wiki gives the phase 2 body as 140 HP ("Active") and **4,500** HP ("Core-ejected"), both `scaledhp = Yes`; the cache holds 140 on all P2 forms, so
  the 4,500 is not in the cache (the exposed form is the Warden's real health, see WD27). Phase 3 HP **880** (cache and wiki).
- **Param map (cache `params`, decoded by matching the wiki infobox).** 3 = magic attack bonus, 4 = ranged attack bonus, 5 / 6 / 7 / 8 = stab / slash / crush /
  magic defence bonus, 10 = melee strength bonus, 12 = magic strength bonus, 65 = ranged strength bonus, 14 = attack speed. Undecoded: 0, 26, 46, 509, 510 (a
  string = the display name), 1361, 1398 (`OPEN-WD16`).

  | Form | att | def stab / slash / crush / magic | str / mage str / range str | speed | wiki max hit |
  |---|---|---|---|---|---|
  | P1 | m 0, r 0 | 70 / 70 / 70 / 50 | 0 / 0 / 0 | 14 (unused: no attacks) | none |
  | Elidinis P2 | m 225, r 225, **param 0 = 280** | 70 / 70 / 70 / **-30** | 10 / 0 / 0 | **7** | **20** |
  | Tumeken P2 | as Elidinis | as Elidinis | 25 / 12 / 12 | **8** | **22** |
  | P3 (both) | m 230, r 300 | 40 / 40 / 20 / 20 | 40 / 40 / 40 | no param 14 | **26** |
  | Obelisk | 0 | 70 / 70 / 70 / 50 | 0 | none | 0 (16 for Monster Examine) |
  | Energy Siphon | 0 | magic **500**, rest 0 | 0 | none | none |

  Wiki infobox: P2 "dlight / dstandard / dheavy" 70 (agrees with 70 on stab / slash / crush), P2 melee attack bonus **0** (cache param 0 = **280**, conflict 8),
  P2 xp bonus Elidinis 5, Tumeken 7.5, P3 xp bonus 10 both, P3 defence level shown as 150 / 150 / **180** (damaged / invulnerable / enraged), obelisk
  light 60 / standard **20** / heavy 60 (standard ranged was 60 until 25 Jun 2025, Ob), obelisk magic 50, elemental weakness **Earth 50%** on both Wardens in P2
  and P3 (25 Jun 2025), poison and venom resistance 100, immune to cannons, thralls allowed. Both Wardens are aggressive, size 5, attack styles melee,
  ranged and magic (TW, EW).
- **Defence-reduction floors.** Obelisk: base 100, at most **-40** (floor 60). P3 Warden: base 150, at most **-30** (floor 120); after the enrage it is set to
  180 and may be lowered by at most **60** (floor 120 again). P2 Warden: not stated (`OPEN-WD5`). (Ob, TW, Strat)
- **Animations (`Seqs`, `NPC_WARDENS_*` unless noted).** WALK01-04 9649-9652; IDLE01-05 9653-9657; STOMP01 9658; MELEE01 9659; RANGED01 9660; MAGIC01 9661;
  DEATH01 9662; WAKE01 / 02 9663 / 9664; DORMANT01 9665; SPECIAL01 9666; IMPACT01 9667; LAUNCH01 9668; EXPLOSION01 9669; **KNEEL01 9670** (the "down", counted by LD);
  KNEELING01 9671; STANDUP01 9672; HEADLESS01 9673; ATTACKLEFT01 / 02 9674 / 9675; ATTACKRIGHT01 / 02 9676 / 9677; ATTACKCENTER01 / 02 9678 / 9679; STAGGER01
  9680; RELEASE01 9681; CHARGE01 9682; CHARGING01 9683; ENRAGING01 9684; ENRAGED01 9685; `TOA_WARDENS_BUFF_SPOTANIM` 9648. Projectile animations
  `PROJANIM_WARDENS01_MELEE01_SPIN01` 9701, `_RANGED01_SPIN01` 9702, `_ENTOMB01_SPIN01` 9703; `SPOTANIM_WARDENS01_CORE01_BEAT01` 9704; Amascut sucking
  9705-9709; `SPOTANIM_AMASCUT01_STRIKE01_FIRE01` 9710; `SPOTANIM_WARDENS01_DRONESTRIKE01_FIRE01` 9711; `TOA_WARDENS_DEBRIS_FLOATING` 9712. Player entomb
  `TOA_WARDENS_ENTOMB` 9713, `_ENTOMB_LOOP` **9714** (MT: the "bound" animation), `_ENTOMB_END` 9715. Pyramids `TOA_WARDENS01_PYRAMID_ATTACK` **9524** (MT),
  `_PYARMID_APPEAR` 9525, `_PYARMID_LEAVE` 9526, `_PYRAMID_LOAD` 9527, `_PYRAMID_INVISIBLE` 9528; `TOA_OBELISK01_GLOW` 9515. Obelisk
  `FX_OBELISK_WARDENS01_CAST` 9721, `WARDENS_P3_TILE` 9722, `..01_CHARGE_PART1-3` 9724-9726, `..02_BOMB` **9727**, `..02_OPEN` 9728, `..02_CLOSE` 9729, `..02_WAVE`
  9730, `..02_CAST` 9731, `..02_BEAM` **9732**, `..02_SWEEP` **9733**, `..02_DEATH` **9734**; `FX_WARDENS_ENERGY_DEATH01` 9735, `_GROUNDED01` 9736. The cache has no
  frame lengths for any of these (`frameLengths = []`, `totalFrameCycles = 0`; only `keyframeEnd`, e.g. KNEEL01 210, STANDUP01 120, ATTACKLEFT01 150), so animation
  lengths in ticks are unknown (`OPEN-WD17`).
- **Spot animations and projectiles (`SpotAnims`; the client's projectile ids are spot-animation ids; MT's role in brackets).** `WARDENS_LIGHTNING` **2197**
  (P3 red lightning, MT), `WARDENS_LIGHTNING_WARN` **2198** (obelisk skull, MT "bomb"), `WARDENS_DARKNESS_WARN` **2199** (MT "DDR" = Converging Beam),
  `WARDENS_BURN_WARN` **2200** (MT windmill), `FX_WARDENS02_BLOODWAVE01 / 02` 2201 / 2202, `WARDENS_FREEZE_WARN` 2203, divine projectile
  `TOA_WARDENS_PRAYER_MELEE_TRAVEL / _IMPACT` **2204** / 2205, `_RANGED_` **2206** / 2207, `_MAGIC_` **2208** / 2209 (and `_MAGIC_TRAVEL_SMALL` 3027), entomb
  `TOA_WARDENS_ENTOMB_TRAVEL / _END / _MISS` 2210 / 2211 / 2212, `TOA_WARDENS_MAGIC_IMPACT` 2213, `SPOTANIM_WARDENS01_MAGIC01 / 02_ACTIVATE01` 2214 / 2215,
  `..EXPLOSION01_EXPLODE01 / IDLE01` 2216 / 2217, `..WAKEUP02` 2218, `..IDLE05` 2219, **P3 floor tiles** `SPOTANIM_WARDENS01_UNDERTILE01_EXPLODE01-04`
  **2220-2223** (MT "earthquake"), `FX_WARDENS_BOMB01-03` 2224-2226 (**2226** = the siphon projectile, ST), `FX_WARDENS_CHARGING01` 2227,
  `SPOTANIM_WARDENS_TILES01` 2228, Amascut sucking 2229-2233, `SPOTANIM_AMASCUT01_STRIKE01` **2234** (MT: windmill hit),
  `SPOTANIM_WARDENS01_DRONESTRIKE01_FIRE01` **2235** (MT: converging-beam tile), `SPOTANIM_AMASCUT01_STRIKE01_HIGHLIGHT01` 2236 (MT declares it as
  "windmill shadow" but never uses it), phase 1 shots `SPOTANIM_WARDENS_PHASE01_BALL01` **2237** and `_BALL02` **2238** (MT: "death dot" and "disperse"),
  `PROJECTILE_WARDEN_TUMEKEN_CORE` 2239, `PROJECTILE_WARDEN_ELIDINIS_CORE` 2240, `PROJECTILE_WARDEN_RANGED01` 2241, `OBELISK_EXPLODE_ENERGY` 2196.
  Reused by phantoms (MT): Akkha magic **2253**, ranged **2255**; Zebak magic **2181** (`ZEBAK_MAGE_PROJANIM_SPLIT`), ranged **2187**
  (`ZEBAK_RANGED_FRAGMENT01`); Kephri bomb explosions `VFX_SCARAB_EXPLOSION01-04` **2156-2159**; Ba-Ba rock fall `TOA_BABA_ROCK_FALL` **2250**,
  `_FASTER` **2251**, `_FASTEST` 2252.
- **Scenery (`Locs`).** `TOA_WARDENS_BARRIER` **45136** ("Barrier", op1 Pass); `TOA_DOOR_EXIT_WARDENS` **45129** ("Exit", op1 Leave; map pin (3806, 5178, level
  1)); `WARDENS_DOOR` **45578** ("Tomb Door", op1 Leave); `WARDENS_TELEPORT` **45579** ("Teleport crystal", op1 Use, anim 9501; pin (3808, 5172, 1));
  `TOA_TELEPORT_CRYSTAL_CONTINUE_WARDENS` **45138** ("Teleport crystal", op1 Use, anim 8105); `WARDENS_P3_INDICATOR` 45580 / `_FINAL` 45581 (anim 5835,
  tile indicators); pillars 45582 / 45583, floor trims 45585-45588, pool and channel pieces 45589-45600, steps 45601-45604, charging platforms
  `TOA_WARDENS_DEFAULT_CHARGING_PLATFORM` 45605-45626 (pins (3792, 5152, 1) and (3820, 5152, 1)), `TOA_WARDENS_DEFAULT_OBELISK_PLATFORM` 45627 (pin (3806, 5152,
  0)), skulls scenery 45628-45630, warped-room pieces 45632-45725 (floor tile 45643-45649 with 45649 `_MISSING`, 71 `LVL0` pieces 45650-45720, outside crumble
  45721-45725), broken tiles `TOA_WARDENS_P3_BROKENTILE*` 45726-45738, tile warning / hit `TOA_TILE01_WARNING01 / HIT01 / WARNING02 / HIT02` 45739-45746 (the
  shadow and strike tiles, presumed, `OPEN-WD13`), `TOA_WARDENS_OBELISK_BASE` **45747** (pin (3807, 5153, 1)), pyramids `TOA_WARDENS_PYRAMID01 / 02` 45748 /
  45749 and `_LOAD` **45750** (red, MT) / **45751** (yellow, MT; anim 9527 in the cache). Objects: book `TOA_BOOK_WARDENS` **27310** (placeholder 27311);
  pets 27352-27354, 27381, 27386 / 27387 (see WD50 and WD51).
- **Client state.** Varbits `TOA_BOOK_WARDENS` 14453, `TOA_WARDENS_BUFF_TYPE` 14455 and `TOA_WARDENS_BUFF_VALUE` 14456 (a client-visible buff display; meaning
  **OPEN-WD16**), `PET_INSURANCE_WARDENS` 14470, `PET_MENAGERIE_WARDENS` 14471; varp `TOA_WARDENS` 3641; timer interface 482 components 9 (`WARDENS_TIME`
  31588361) and 10 (`WARDENS_TITLE` 31588362); music `MUSIC_TOA_BOSS_WARDENS` (DbRow 3143), jingle `TOA_BOSS_WARDENS` 551; unlocks "Amascut's Promise" during the
  fight and "Laid to Rest" on entering the burial chamber (Main). Hitsplat types **53** (own P2 shield damage), **54** (other players'), **55** (own maximum) mark
  the phase 2 shield damage (ST). Chat colours of the phase 1 shot warnings: blue (Elidinis) and orange (Tumeken) (Strat).
- **Regions and map pins.** Phase 1 and 2 room: region **15184**, map square (59, 80), base (3776, 5120); wiki pins Elidinis' Warden (3794, 5154), obelisk (3808,
  5154), Tumeken's Warden (3823, 5154) at plane 1, so Elidinis stands **west** and Tumeken **east**. Phase 3 room: region **15696**, square (61, 80), base (3904,
  5120); the Amascut phantom pin is (3937, 5142). Burial chamber: region **14672**, square (57, 80), Osmumten pin (3680, 5144). Nexus region 14160 (ST, PT, MT).
- **Chat messages (ST patterns; the first two are quoted by the plugin).** "Challenge started: The Wardens." / "As Elidinis' Warden falls, Tumeken's Warden powers
  up!" / "As Tumeken's Warden falls, Elidinis' Warden powers up!" / "Elidinis' Warden uses the last of its power to restore Tumeken's Warden!" / "Tumeken's
  Warden uses the last of its power to restore Elidinis' Warden!" / "Challenge complete: The Wardens." Phase 1 shot warning: "A large ball of energy is shot your way..."
  (Strat).

## Entry, arena and structure

- **WD1 Access.** The Wardens' door opens once all four paths are complete (Main: "Once all four paths are completed, the lower level ... becomes accessible");
  every party member speaks to Osmumten (NPC 11690 op3 "Begin"; Amascut in the miniquest "Into the Tombs") before the fight can start (Strat). The Nexus gate is loc 46167 `TOA_NEXUS_WARDENS_DOOR` ("Entry", no ops), 46168 `TOA_NEXUS_WARDENS_DOOR_OPEN` ("Entry", ops Enter / Quick-Enter; LD's quick-proceed labels it "wardens") and 46169 `_SIDE01` (no name, no ops); the barrier 45136 `TOA_WARDENS_BARRIER` is a separate loc whose role is `OPEN-WD14`.
- **WD2 Rooms.** The fight runs in two map areas: phases 1 and 2 in region 15184 (the obelisk room), phase 3 in the "warped" room, region 15696. The wiki and the
  plugins call the third part "phase 3" and the enrage "phase 4" (ST, LD); this sheet uses the wiki's three phases and calls the 5% state the enrage.
- **WD3 Timer and chat.** "Challenge started: The Wardens." is sent at the start; the raid timer ends when the Warden reaches **0 HP**, not at the end of the death
  animation (8 Sep 2022), and ST's "Challenge complete: The Wardens." pattern (ends "...completion time:..." in LD) is the chat line of the completion; its tick relative to the 0 HP hit is not stated (inference). The "Walk for It" family of time limits therefore stops at that hit.
- **WD4 Exit and re-entry.** The loc "Exit" 45129 (op Leave) leaves the room; leaving, deaths, the escape crystal and attempts are raid-wide
  (`../toa.md`). In a phase transition the player is not stuck in "purgatory" any more (8 Sep 2022 fix); dying at specific points no longer changes boss
  behaviour (25 Aug 2022 hotfix).

## Scaling

- **WD5 Raid level.** For every **5** raid levels an NPC's hitpoints, defence, accuracy and damage rise by **2%** additively, damage capping at **+150%** (Main:
  level 100 = +40%, level 500 = +200% on hitpoints, defence and accuracy, +150% on damage). Raid levels run 0 to 600; bands Entry 0-149, Normal 150-299,
  Expert 300-600. The Wardens use the same NPC ids in every band; there is no Entry-only Warden. Whether fixed-percent mechanics (siphon collision, shockwave,
  charged shots) follow the same formula: `OPEN-WD19`.
- **WD6 Group size.** Raids hold up to **8** players (ES: layouts for 1 and for 2 to 8 players, siphon HP up to 8). Warden hitpoints grow with the party ("Increased
  Warden's health in phase 2 for larger groups", 1 Sep 2022; the phase 3 line, "slightly increased the Warden's health in phase 3", has no group wording). Main's party rule: the 2nd and 3rd member add **90%** of a boss's base health each, the 4th and later **60%** each; whether it covers the Wardens and the core is unstated (`OPEN-WD6`). Stated group
  effects: phase 1 shot damage (WD14), siphon layout, siphon HP and time window (WD37-WD41).
- **WD7 Path levels and the Wardens (inference).** No source states that path levels leave the Wardens alone. Main says path level modifies "most enemy hitpoints and damage"; Pet and the
  8 Sep 2022 note only exclude the Wardens from the remnant's "path level 4+" requirement ("Path level must be at level 4+ (excluding the Wardens)"); the mechanic changes below are written for the four path bosses. Path level raises "most enemy hitpoints and damage" (first level +8%, each further +5%, up to six levels) and every two levels change that
  path's boss: Akkha (one more Memory Blast iteration, more damage from the white orbs in the enrage, faster detonation), Ba-Ba (faster Rockfall, wider
  sarcophagus energy blasts, boulders with more health), Kephri (faster auto-attacks, groups of three scarab swarms during the shield-charging phase), Zebak
  (faster auto-attacks, more so in the enrage, tidal-wave gap tightened by 1 tile per two levels) (Main, "Walk the Path" row). Whether the Wardens' own stats or the phase 3 phantoms use
  a path level is `OPEN-WD12`; Strat only says the phantoms' speed under Insanity "can be further increased depending on the boss's path level, if
  applicable".

## Phase 1: the obelisk and the charging Wardens

- **WD8 Setup.** Phase 1 uses the obelisk (NPC 11751, size 3, HP 260 scaled, op2 Attack) and the two charging Wardens (NPCs 11748 / 11749, not attackable). The
  obelisk emits orbs along an energy trough that "initially flow out on one trough, but it soon branches into three" and feed the Wardens (Strat, Ob).
- **WD9 Choosing the second phase's Warden.** A player blocks an orb stream by standing on either side of a trough; each absorbed orb costs the player **3**
  damage and "significantly reduces, although not prevents" that Warden's energy gain (Strat). When the obelisk's health reaches 0, "the Warden whose energy gain
  was disrupted the most will be destroyed" and the other powers up and is fought in phase 2 (Strat, Main). Blocking the **east** trough destroys Tumeken's
  Warden and powers Elidinis' (EW: "To power up Elidinis' Warden, a player must stand on the east trough"); blocking the **west** trough destroys Elidinis' and
  powers Tumeken's (TW). This agrees with the pins in Data (Tumeken east). Ob's picture caption ("blocks the western trough to disrupt Tumeken's Warden") says the
  opposite; the two Warden pages, the Strat caption and the pins win (conflict 1). A tie or nobody blocking: `OPEN-WD1`. The chat line on the swap is in Data.
- **WD10 Obelisk durability.** Defence 100 lowered to at most 60; weakest to standard ranged (standard ranged defence 20); any style is usable (Ob, Strat).
  Damage to it earns **1.5x** room points (LD, Chest).
- **WD11 Charge cycle.** A Warden that is "charged up" (a red circle fills under it and Amascut's seal forms in front of it, Strat) discharges one of two attacks;
  "The Wardens will alternate between UFOs and Charged Shot respectively until the phase ends", read as: a Warden's first discharge is the UFO attack (Strat). Charge
  rate, cycle count and the energy per orb: `OPEN-WD2`. Ancient Haste (see the invocation table) makes the Wardens charge faster.
- **WD12 Desynchronising.** Blocking only one side puts the two Wardens' discharges out of step; if both discharge simultaneously a player can avoid the UFO
  attack by standing at the arena edge next to the stairs (Strat). In a solo run the recommendation is to block the flow to Tumeken's orb.
- **WD13 UFOs (pyramids).** Small floating pyramids spread through the area and fire damaging lights (Strat). MT: the pyramid scenery 45750 (red) / 45751
  (yellow) plays animation 9524; the area hit is **3x3** centred on the pyramid and damages during a window that is the animation's window **shifted one tick later**: MT re-adds the 3x3 tiles at tickCount + 1 on every tick the pyramid shows animation 9524 (MT comment: "the pyramid
  animation starts 1 tick earlier and ends 1 tick earlier than when the player actually takes damage"). Count, placement, damage: `OPEN-WD4`.
- **WD14 Charged Shot.** A slow-moving ball of energy is shot at the players for "unavoidable damage" with the chat warning "A large ball of energy is shot your
  way..." (colour blue for Elidinis' small ball, orange for Tumeken's larger one) (Strat). Group: stand **apart** for Elidinis' (so the splash does not clip
  others) and **together** (on top of each other) for Tumeken's, otherwise "massive damage"; capped at **30** (Elidinis) and about **7** (Tumeken) when handled
  properly. Solo: no mitigation; Tumeken's is about 33% weaker; capped at **45** (Elidinis) and **30** (Tumeken) (Strat). Projectiles 2237 / 2238; which is which
  is `OPEN-WD3` (working assumption in OPEN). An orb that is mid-air with about **3 seconds (5 ticks)** to spare when the obelisk dies still deals damage; later
  than that it does not (Strat, bold note). Tick-healing against a shot is possible with exact timing (Strat).
- **WD15 Phase 1 end.** The obelisk's health reaches 0; the disrupted Warden is destroyed (inactive forms 11759 / 11760), the other activates (phase 2 form,
  chat line in Data) and engages. The supplies interface can be opened while a bomb is flying at the end of phase 1 (14 Sep 2022).

## Phase 2: the powered Warden, the obelisk's attacks and the core

- **WD16 Forms and prayers.** The active Warden always has two of the three protection prayers: **melee always**, plus magic (Tumeken initially) or ranged
  (Elidinis initially) (TW, EW, Strat). The cache shows one overhead icon per form (sprites 7 / 8) on the "mage" and "range" NPC ids; working reading:
  the NPC id named after the prayer it shows, so Tumeken starts as 11756 and Elidinis as 11754. What makes it switch and how often: `OPEN-WD7`. Tumeken's Warden
  prays against melee and magic, so ranged must be used first; Elidinis' prays against melee and ranged, so magic is used first (TW, EW).
- **WD17 Movement.** Both Wardens can target and chase; Elidinis' stops "a few tiles short" of its target; Tumeken's closes the gap and melees, making Protect from
  Melee mandatory (Strat). Tumeken's attack speed is **8**, Elidinis' **7** (cache param 14, wiki). Max hit **22** (Tumeken) / **20** (Elidinis).
- **WD18 Ordinary attacks.** Melee, ranged and magic. A red skull is the magic attack, a normal skull the ranged attack; each has its own animation
  (magic: raises a blade, zapping sound; ranged: rears back, clanging; seqs `NPC_WARDENS_MAGIC01` 9661 / `_RANGED01` 9660 / `_MELEE01` 9659) (Strat). Ranged and magic
  "only damage on impact", so a late prayer change still works (as at Zebak); melee "cannot be reacted to" (Strat). Projectile ids for the plain shots, flight
  times, style frequency, accuracy and damage rolls: `OPEN-WD7`.
- **WD19 Special 1: Divine Projectile (Warden above 55% health).** The Warden **disables every player's overhead prayer** and fires a red scimitar (melee,
  2204 / impact 2205, seq 9701), white arrow (ranged, 2206 / 2207, seq 9702) or blue spell (magic, 2208 / 2209) at everyone; the matching prayer blocks it, otherwise
  "high damage" (Strat). It uses the magic attack animation. MT checks the player's overhead icon at the **projectile's arrival tick** (`getActivationTick` =
  remaining cycles / 30). Style choice, prayer-disable duration, damage: `OPEN-WD8`.
- **WD20 Special 2: Imprisonment (Warden below 55%).** A dark projectile (2210, seq 9703) is fired at the player's **position at the moment the attack starts**;
  a hit encases the player in stone (player seq 9713 -> loop 9714 -> end 9715; spot animations `_END` 2211, `_MISS` 2212): no moving or attacking, but prayers and eating
  still work (Strat). It shares the magic attack animation. Duration, target count, damage: `OPEN-WD8`. The 55% is of the Warden's real health (WD27), which is why
  the first core down should end below 55% (Strat).
- **WD21 Obelisk attacks.** While the Warden is up, the obelisk (NPC 11752, no ops) discharges, "in a set pattern; it can only perform a special attack again after
  the other attacks in that set have been exhausted" (Strat); a hit is "moderately high damage" and, with **Penetration**, temporarily disables overhead prayers
  (Strat; also lowers defence per Main, conflict 3). The lightning colour around the obelisk is the warning (Strat); cache warning spot animations match the colours:
  yellow = `WARDENS_LIGHTNING_WARN` 2198 (skull), red = `WARDENS_DARKNESS_WARN` 2199 (Converging Beam), orange = `WARDENS_BURN_WARN` 2200 (windmill); 2203
  `WARDENS_FREEZE_WARN` has no source. Order, cadence and damage of the three attacks: `OPEN-WD9`.
  - **Converging Beam** (obelisk seq `..02_BEAM` 9732; MT "DDR"): two beams head north and south, then two east and west; each spreads and converges back toward the
    obelisk; the safe place is the quadrant opposite where the beams first shone; a beam can be run across with correct timing. MT tile hits: graphics 2235
    (activation = graphic start tick **+1**) and 2199 (start tick **+0**), 2234 (added on the tick it is created, while the obelisk is in this phase). **It stops when the core
    is ejected** (21 Sep 2022).
  - **Windmill Beam** (seq `..02_SWEEP` 9733): beams rotate in four directions like a windmill, the floor glows briefly before each beam (Strat). The Strat text says
    "clockwise", its rotation picture caption says "anti-clockwise" (conflict 4, `OPEN-WD9`). MT: graphic 2200 and, in this phase, 2234 are added on their creation tick (the only obelisk tiles MT treats as instant).
  - **Lightning Skull** (seq `..02_BOMB` 9727): several red skulls land and lightning strikes outward in **7x7** areas centred on each; "the two tiles in each
    cardinal direction (not diagonally) of the tile where the skull will land" are safe (Strat). Skulls can no longer land under the obelisk (6 Aug 2025) or under the
    Warden (25 Jun 2025, Main). MT: graphic 2198 goes into a delayed tracker at the graphic's start tick (+0), and its comment says the outer ring of tiles activates one
    tile (tick) later than the inner ones; it is counted only while the obelisk's last seen animation is 9727.
  - Standing on the four central edge tiles or next to the obelisk exposes the player to these (Strat). Obelisk death anim 9734 plays at the phase end.
- **WD22 Shield and the balance bar.** The active Warden carries a shield (like the Nightmare's totems): every player hits it with **100% accuracy** regardless of
  equipment, "however, the damage dealt corresponds to the player's accuracy bonuses" (Strat); the damage formula was changed to benefit less from accuracy and have a
  lower ceiling (1 Sep 2022). The shield refills slowly if the Warden is not attacked. When the yellow bar above it is full the Warden is "thrown off balance" and ejects
  its core (WD23). The active body is the 140 HP stat (WD27). Formula, bar size, refill delay and P2 defence floor: `OPEN-WD5`. The own / other / own-max hitsplat
  types 53 / 54 / 55 are used for this damage (ST).
- **WD23 Core ejection.** The Warden kneels (KNEEL01 9670) and the core NPC (11770 Tumeken, 11771 Elidinis, op2 Attack, size 1) appears; the Warden becomes the
  "exposed" form (11755 / 11758). The ejected core flies as `PROJECTILE_WARDEN_TUMEKEN_CORE` 2239 / `PROJECTILE_WARDEN_ELIDINIS_CORE` 2240 (presumed; the cache names
  them, no source describes the flight).
- **WD24 Damaging the core.** Damage dealt to the core is applied to the Warden **five times over** (a 55 hit deals 275) (Core, Strat); players always deal their
  **maximum hit with melee** (since 26 Aug 2022 magic no longer gets the guaranteed max hit, so melee only). The core earns **0x** room points, the Warden's body
  absorbs the damage as its own (LD, Chest, ST). The core's own HP and its scaling: `OPEN-WD6`.
- **WD25 Exposure length by Warden HP at the moment of ejection (Core, Strat).**

  | Warden HP | Core exposed |
  |---|---|
  | 100% - 80% | **21 ticks** (12.6 s) |
  | 79.9% - 60% | **29 ticks** (17.4 s) |
  | 59.9% - 0% | **37 ticks** (22.2 s) |

  The exposure was shortened by 7 ticks on 1 Sep 2022 and made to lengthen with each ejection on 2 Sep 2022; the table above is the live one. After the time the
  Warden reinserts the core and stands up (STANDUP01 9672) and the shield must be broken again.
- **WD26 Downs and points.** A "down" is the Warden's KNEEL01 animation (LD counts it). Teams do a two-down or three-down cycle; damage to the phase 2 Wardens earns
  **2x** points, and from the **fourth** down on it earns none ("subsequent downs after the third will not yield any additional points", Strat; LD ignores damage once
  its counter exceeds 3) (LD, Chest).
- **WD27 Health pools (inference).** The reading below is inferred from the cache stat (140) and the wiki infobox (140 / 4,500). The 2 Sep 2022 post says "The Wardens' HP (or Poise) remains the same ... Increased the hitpoint of the Wardens' Core significantly", so the 4,500 may instead be the core's HP. Working reading: the 140 HP stat is the shield (the balance bar); the 4,500 HP pool (wiki "Core-ejected") is the Warden's real health and the one the
  55% / exposure thresholds and the core's x5 damage use (TW, EW, Core, ST: shield damage is recorded separately from the damage the Warden takes through the core). Scaling
  and Death Charge: the game treats the core as dead when the Warden reinserts it, which restores special attack energy (Strat); other on-death hooks `OPEN-WD15`; scaling `OPEN-WD6`.
- **WD28 End of phase 2.** When the core is destroyed the Warden's body collapses (death animation), the message "X's Warden uses the last of its power to restore
  Y's Warden!" appears, the obelisk grows unstable and **explodes**, and the room warps into the void (phase 3) (Strat). Potions dropped to make room should be picked up
  before the next phase (Strat). The restored Warden is Tumeken's if Elidinis' was fought, and Elidinis' if Tumeken's was fought (EW, TW).
- **WD29 In-flight effects at ejection.** The wiki states only that the Converging Beam stops (21 Sep 2022). MT clears all pending obelisk tiles (beam, windmill, skull) and
  the divine-projectile prayer check the moment the Warden becomes its exposed form and credits README line 94 ("Divine projectile ... nulled from core being out"); this
  is read as: nothing from phase 2's attacks hurts after the core is out (`OPEN-WD9`).
- **WD30 Acceleration (invocation).** The Warden attacks faster and the obelisk charges faster; amounts `OPEN-WD7`, `OPEN-WD9`.

## Phase 3: the restored Warden

- **WD31 Combat shape.** The restored Warden (NPC 11761 Elidinis / 11762 Tumeken, HP 880 scaled, defence 150, level 544) uses no protection prayers and no combat styles;
  it slams the floor for **typeless** damage, its defences are lower against melee and equal against ranged and magic than phase 2 (Strat). Max hit **26** (TW, EW).
  Defence floor 120. Room points **2.5x** (LD).
- **WD32 Start.** Phase 3 starts in the warped room (region 15696) after the movement delay that was reduced on 2 Sep 2022; the Warden does not attack during the
  transition (8 Sep 2022). ST observed a heal at the very start of phase 3 and a second one at the enrage (ST comment); the amount of the first is `OPEN-WD10`.
- **WD33 Slam rotation.** The Warden raises the floor to its right (east) first, then its left, then slams in **both** directions (Strat); seqs ATTACKRIGHT01 / 02
  9676 / 9677, ATTACKLEFT01 / 02 9674 / 9675, ATTACKCENTER01 / 02 9678 / 9679. For a side attack players stand at least one tile left or right of the centre
  line opposite the way the Warden faces; for the double slam they stay in the centre tiles in front of its face; the three central floor columns are enough
  (Strat). The slam travels outward, so a late reaction works if the player is **6 to 9 tiles** from the boss (Strat). Tile strikes are spot animations
  `UNDERTILE01_EXPLODE01-04` 2220-2223; MT treats a tile as damaging at the graphic's start tick **+0**. A safespot that avoided slams entirely was removed
  (29 Sep 2022).
- **WD34 Slam interval.** Default and invocation values conflict (conflict 5, `OPEN-WD10`): Main says Overclocked makes the slam "1 tick faster (**5** ticks per slam)",
  Overclocked 2 "2 ticks faster (**4** ticks per slam)", Insanity "3 ticks faster (**3** ticks per slam)", which fits a base of 6; Strat says the base attack speed is
  **7** and these "decrease them to 6, 4 and 3" (not self-consistent: 7 minus 1, 2, 3 is 6, 5, 4); the infoboxes give 7 (Elidinis) and 8 (Tumeken). The cache has no attack speed on any phase 3 form (no param 14).
- **WD35a Changelog facts.** Thralls and pets no longer appear on siphons or skulls (26 Aug 2022); siphons no longer misaligned in parties of 2+ (5 Oct 2022); players cannot click through the obelisk (1 Sep 2022); freezing a Warden as it wakes no longer removes it (5 Apr 2023); the Wardens are no longer immune to the life leech of the Sanguinesti staff and the amulet of blood fury (22 Jul 2026; U-Jul26 warns of "weirdness during P2 because damage calculations here are non-standard").
- **WD35 Death of a player.** The Warden no longer stops attacking briefly when a party member dies (14 Sep 2022).
- **WD36 Energy siphon thresholds.** At **80%, 60%, 40% and 20%** health the Warden stops slamming, shows all three protection prayers at once (charging form 11763 / 11764,
  overhead sprite 9, seqs CHARGE01 9682 / CHARGING01 9683) and launches energy siphons onto the arena (ES, Strat). The charging form is "invulnerable" (TW / EW infobox).
  A player standing on a tile where a siphon (skull) would appear is knocked away and takes no damage (ES). Each threshold fires once (read from "upon reaching 80%, 60%, 40%, and 20%"; no source says it explicitly).
- **WD37 Siphons.** NPC 11772, 1 hit point base scaled by the number of players still alive in the arena **when the set is deployed**: **1** for 1-3 players, **2** for
  4-5, **3** for 6-7, **4** for 8 (ES, Strat agree). There are two layouts, a solo layout and a group layout, chosen the same way (if two players remain and one dies
  the next set uses the solo layout) (Strat, ES; layout tiles `OPEN-WD11`). Siphons are immune to ranged and magic and must be killed with **melee**, which is guaranteed to
  hit and has **no delay** after the attack (ES); the Eclipse atlatl set effect also hurts them (ES trivia).
- **WD38 Reversing.** A siphon with enough damage turns to face the Warden ("reversed"); if all siphons are reversed in time they collide with the Warden and deal a
  percentage of its maximum health (reduced on 1 Sep 2022, value `OPEN-WD11`) and the slam rotation resets to the right unless Insanity is on (ES, Strat). The siphon
  projectile is spot animation 2226 (ST).
- **WD39 Failing.** If any siphon is not reversed in time the Warden detonates the remaining ones: a shockwave leaves the centre of the arena and hits players anywhere
  in the room, with damage scaling with **raid level and the number of untouched siphons** ("failing to reverse two siphons in higher enrage levels will result in
  instant death"), and the Warden takes no damage (ES, Strat). The shockwave is avoidable ("skull skip"): stand far from the centre and run in just after the
  explosion; the Warden then takes no damage from the mechanic (Strat). Damage figures and speed: `OPEN-WD11`.
- **WD40 Time window.** The time to reverse the siphons depends on group size and on Insanity (Insanity row); values `OPEN-WD11`. The base window was shortened on 1 Sep 2022; the 2 Sep 2022 post says "we don't think this needs a full revert" and adds **2-3 cycles** (by group size) to the base duration, while the Insanity window was "reverted fully" to release; the wiki changelog's "reverted to its original duration" fits only Insanity (conflict 12). Remaining unknown: the base values (`OPEN-WD11`).
- **WD41 Phantoms.** During the **second and third** siphon batches a phantom of an earlier boss appears behind the Warden and helps: Elidinis' Warden gives **Akkha's
  Phantom (11777)** then **Kephri's Phantom (11776)**; Tumeken's gives **Zebak's Phantom (11774)** then **Ba-Ba's Phantom (11775)** ("respectively", Strat). Invocations that
  apply to the original boss apply to the phantom (Strat); Strat adds that Zebak and Ba-Ba have no invocations that matter here (conflict 6). Phantom attacks: Zebak's uses its
  standard attacks (magic / ranged, MT ids 2181 / 2187; the prayer matters on impact, 3 ticks after launch); Ba-Ba's launches boulders only (MT ids 2250 / 2251 with 6 and 4 tick
  delays; avoid by moving one tile off the target spot); Akkha's alternates **ranged / magic every 3 attacks, starting with ranged** (Stay Vigilant: random; its style
  changes were reduced on 14 Sep 2022; MT ids 2253 magic, 2255 ranged, prayer matters on the spawn tick); Kephri's fires its fireball (MT: scarab explosion graphics
  2156-2159 hit on their creation tick; **Aerial Assault** adds damage and a 3x3 area). Spawn tick, position, stat scaling and cadence: `OPEN-WD12`.
- **WD42 Phantom of Amascut.** The large phantom (NPC 11767, size 8) stands at the southern end of the warped room and only watches until the Warden reaches critical health,
  then opens its mouth (11768) and begins consuming the arena (Ph).
- **WD43 Enrage (first time below 5%).** The Warden stops slamming, shakes violently (ENRAGING01 9684, ENRAGED01 9685) and **heals 20% of its maximum health once**
  (TW, EW, Strat, ST sees a second heal hitsplat). Its Defence is set to **180** with the floor still **120** (so it can be lowered by 60). Slams end; red **lightning**
  (spot animation 2197) strikes the arena, each strike telegraphed by a **growing shadow** on the target tile and dealing "moderate damage"; MT treats the hit as landing on the
  graphic's creation tick. A just-struck tile has a small grace time before it can be targeted again (Strat). The lightning rate is "unaffected by invocations" (Strat)
  while the Insanity text says it strikes faster (conflict 7).
- **WD44 Row removal.** The phantom rips the furthest row of floor tiles out periodically, up to the row directly in front of the Warden, so the arena ends as one row
  (TW, EW, Ph); players are no longer pushed into unwalkable terrain when a row goes (1 Sep 2022). Row count and cadence: `OPEN-WD13`.
- **WD45 Enrage companions.** The boss phantoms keep attacking at an attack speed that Insanity raises "significantly" when the enrage starts (Strat; the same raise by path
  level "if applicable"); they shrink the usable space and can hurt a player who runs into them.
- **WD46 Death.** At 0 HP the Warden "loses its grip on the arena" and the Amascut phantom consumes it (death forms 11765 / 11766, seq 9662); a white teleport crystal
  (loc 45579 / 45138) appears where it stood and teleports the party to Osmumten's burial chamber (Strat, TW, EW, Ph). The timer stops at the 0 HP hit (WD3). If the Warden
  dies on the same tick as a player, combat achievements count it as deathless while the challenge rewards do not (Chest). Sequence lengths: `OPEN-WD14`.

## Invocations of the Wardens

| Invocation | Raid level | Effect (Main / Strat) | Requires |
|---|---|---|---|
| Ancient Haste | **+10** | phase 1: the Wardens charge at a faster rate | none |
| Acceleration | **+10** | phase 2: the Warden attacks faster and the obelisk charges faster | none |
| Penetration | **+10** | phase 2: obelisk specials deal more damage and temporarily disable overhead prayers; Main adds "and lower defence level" (conflict 3, `OPEN-WD18`) | none |
| Overclocked | **+10** | phase 3: the slam is performed 1 tick faster (Main: 5 ticks per slam) | none |
| Overclocked 2 | **+10** | phase 3: slam 2 ticks faster (Main: 4 ticks per slam) | Overclocked |
| Insanity | **+50** | phase 3: slam 3 ticks faster (Main: 3 ticks per slam); less time to reverse siphons (a few ticks over the minimum, more lenient in groups); after the siphon set the slam resumes where it left off instead of restarting at the right; red lightning strikes faster; floor rows are removed faster; phantoms attack significantly faster in the enrage | Overclocked 2 |
| Overly Draining | **+15** (category Restoration, not Wardens) | all special attacks use **100%** energy; liquid adrenaline halves that to 50% (LA: a 25% weapon then costs 12.5%, i.e. the 12.5% quoted by Strat) | none |

Acceleration, Overclocked and Overclocked 2 were +15 each until 8 Sep 2022, now +10 (Main changelog). The Ancient remnant (WD51) needs "all Wardens invocations" (the six rows
above the last; Overly Draining is not one of them, Chest). The invocation menu, its categories and prerequisites in general: `../toa.md`.

## Rewards, Osmumten and the pet

- **WD47 Burial chamber.** The crystal leads to Osmumten's burial chamber (region 14672). Osmumten (spirit 11691, then the vault forms 11692 / 11693, op Talk-to) commends the
  party and warns that Amascut's forces will return (Osm). The reward chest, the sarcophagus ("Osmumten's sarcophagus" glows purple when a unique is rolled) and the points
  formula are in `../toa.md`; the Wardens' own contribution is below.
- **WD48 Points.** Room points are damage dealt times a multiplier: **obelisk 1.5**, **phase 2 Wardens 2** (none after the third down), **phase 3 Wardens 2.5**, **core 0**,
  siphons 1.0 (LD maps NPC 11772 to 1.0); the MVP bonus is 300 x team size; 20,000 room cap and 64,000 total cap (Chest). LD's `getRoomPointsCap()` returns `MAX_ROOM_POINTS * 3` = **60,000** for the Wardens (and `/ 2` for Zebak) — conflict 11 with Chest's 20,000. LD also gives factor 0.0 to the exposed bodies 11755 / 11758, the phase 1 and inactive Wardens and the core, and 2.5 to the phase 3 charging forms 11763 / 11764; the Raid Summary names the player who "Finished off the Wardens on their own" "The Clutch" (Main).
- **WD49 The wardens (book).** Obj 27310 is dropped by "either Tumeken's Warden or Elidinis' Warden depending on which is killed last", always, only if the player has not read it
  or stored it (TW, Main; varbit 14453 records it). It is not tradeable.
- **WD50 Tumeken's guardian (pet).** Obj 27352 (follower NPCs 11652 / 11812, Elidinis' guardian 27354 with NPCs 11653 / 11813), a tiny version of the Warden, obtainable in every
  mode (Entry at a much lower rate). Chance: **1% for every (350,000 - 700 x RL) total reward points**, with RL = the raid level up to 400, then 400 + (level - 400) / 3 up to
  550, and 450 above 550 (Chest). It follows, has Talk-to, Metamorphosis (default switch between Tumeken's and Elidinis' guardian) and Pick-up; with a follower already out and a
  full inventory it is claimed from Probita; a received pet with a follower already out goes to the backpack ("You feel something weird sneaking into your backpack.");
  otherwise it appears ("You have a funny feeling like you're being followed.") (Pet).
- **WD51 Damaged guardians.** The **Ancient remnant** (27381) unlocks Tumeken's damaged guardian (27386, NPCs 11844 / 11850) and Elidinis' damaged guardian (27387, NPCs 11845 /
  11851). It is a guaranteed challenge reward: raid level **450+**, **zero deaths** for every party member, all Wardens invocations on; one pet remnant per player per raid, priority
  Akkha, Zebak, Ba-Ba, Kephri, Ancient (Pet, Rem, Chest; the quoted Mod Ash tweet lists "Kephri, Tumeken, Ancient", conflict 9). The game checks inventory, worn items and bank and
  cancels the roll if the item exists. A Warden that dies on the same tick as a player breaks the reward (WD46).

## Tick timeline (stated parts)

- Phase 1: block-or-not window ends at the obelisk's death; a shot with at least **5 ticks** of flight left still hurts (WD14); pyramid damage 1 tick after the animation (WD13).
- Phase 2: Warden attack every **7** (Elidinis) / **8** (Tumeken) ticks (infobox speed; Acceleration lowers it, amount not stated); core exposed **21 / 29 / 37** ticks (WD25);
  ranged/magic hit on impact, divine projectile prayer checked on arrival; obelisk tiles hit on the tick the graphic starts (+1 for 2235) (MT).
- Phase 3: slam every **5 / 4 / 3** ticks with Overclocked / Overclocked 2 / Insanity (Main; base `OPEN-WD10`); siphon thresholds 80 / 60 / 40 / 20%; enrage below 5%, once; lightning
  tile hit at the graphic start tick (MT); boulder fall 6 and 4 ticks after the graphic (MT).
- Raid timer: 40 / 35 / 30 / 25 minutes for the time-limit invocations, stopped at the Warden's 0 HP (Main).

## Source conflicts (which wins)

1. **Trough sides.** Ob caption: blocking the west trough disrupts Tumeken's. TW, EW, Strat caption, pins: blocking east destroys Tumeken's, west destroys Elidinis'. The
   latter agree with three sources and with the pins; they win.
2. **Wardens' P3 attack speed.** Infobox 7 (Elidinis) / 8 (Tumeken) in P2 and P3; Strat "by default 7"; Main implies 6; the obelisk infobox says attack speed 4 while the cache has none; the cache has none. Unresolved (`OPEN-WD10`).
3. **Penetration.** Strat: more damage + prayers disabled. Main table: also "lower defence level". Working: first two only (`OPEN-WD18`).
4. **Windmill direction.** Strat text clockwise, Strat figure caption anti-clockwise (`OPEN-WD9`).
5. **Slam ticks.** Main 5 / 4 / 3 (base 6) vs Strat 6 / 4 / 3 "from 7", which is not self-consistent. Strat's 4 and 3 equal Main's Overclocked 2 and Insanity; only Overclocked differs (6 vs 5). Main wins; base 7 versus 6 stays `OPEN-WD10`.
6. **Phantom invocations.** Strat: "any invocations that applied to their original will apply" vs "Zebak and Ba-Ba don't have any invocations that affects them". Compatible: Zebak and
   Ba-Ba invocations (Not Just a Head, Boulderdash, Shaking Things Up, ...) concern mechanics the phantoms do not use.
7. **Enrage lightning rate.** Strat: unaffected by invocations. Main/Strat Insanity row: red lightning strikes faster. Insanity row wins for Insanity; other invocations do not change it.
8. **Melee attack bonus.** Wiki P2 "attbns 0"; cache param 0 = 280 (the only attack param on the P2 forms besides 3 / 4). Authority order puts the cache first; unresolved which
   style it applies to (`OPEN-WD16`).
9. **Remnant order.** Pet/Rem/Chest list Akkha, Zebak, Ba-Ba, Kephri, Ancient; the tweet quoted on Chest lists "Akkha, Zebak, Baba, Kephri, Tumeken, Ancient". "Tumeken" is not an item in the table; the table order is used.
10. **Core exposure.** The 1 Sep 2022 note says 7 ticks shorter, the 2 Sep note says it lengthens per ejection; the Core/Strat table is the live state and is used.
11. **Wardens room-point cap.** Chest: 20,000 room points per room; LD: 3x that (60,000) in the Wardens. Unresolved; Chest is the documented rule (`../toa.md`).
12. **Skull-phase window.** Wiki changelog: reverted to its original duration (2 Sep 2022); the post: partial (+2-3 cycles) for the base, full revert for Insanity. The post wins.

## OPEN items (working assumption; suggested config flag)

- **OPEN-WD1 Tie or no blocking in phase 1.** Which Warden powers up. Assumption: strictly less accumulated energy is destroyed; on a tie the world RNG picks; flag
  `toa.wardens.p1TieBreak = random`.
- **OPEN-WD2 Phase 1 energy model.** Orb cadence, energy per orb, charge needed per discharge, cycle count, how Ancient Haste scales it, the role of NPC 11769. No working number exists in
  any source, so none is assumed: expose `toa.wardens.p1ChargeTicks` and `toa.wardens.ancientHasteFactor` unset and implement phase 1 only after the values are captured in a recorded run.
- **OPEN-WD3 Charged Shot specifics.** Which of 2237 / 2238 is whose (assumption from MT's names "disperse" = Tumeken's, "death dot" = Elidinis'), flight time, damage formula, the 30 / ~7 /
  45 / 30 caps as maxima only; flag `toa.wardens.p1ShotCaps`.
- **OPEN-WD4 UFO pyramids.** Count, spawn pattern, timing, damage per hit.
- **OPEN-WD5 Phase 2 shield.** Official formula unstated; the community calculator (`osrs-dps-calc` `applyP2WardensDamageModifier`, unofficial, not a source of record) removes 1/3 of the NPC defence roll from the player's max attack roll, maps the remainder linearly from 0-42,000 to a 15%-40% modifier, and deals between modifier and modifier + 20% of the normal max hit. Damage formula, shield and balance-bar size, refill delay, defence floor, accuracy to damage mapping; flag `toa.wardens.p2ShieldModel`.
- **OPEN-WD6 Health pools.** 4,500 and 140 at 1 player; core HP; whether Main's party rule (+90% / +60% of base HP, WD6) covers the Wardens and the core; the "75% reduction of core scaling with additional invocations"; rounding. Assumption:
  raid-level factor of WD5 only; flag `toa.wardens.p2HpPerPartySize`.
- **OPEN-WD7 Phase 2 ordinary attacks.** Style mix, projectile ids (2241 ranged named, magic unnamed), flight times, accuracy, prayer reduction, when the second prayer changes, Acceleration speed.
- **OPEN-WD8 Specials.** Cadence of Divine Projectile / Imprisonment, target selection, prayer-disable duration, entomb duration, damage.
- **OPEN-WD9 Obelisk attacks.** Order inside the "set", period, number of skulls, damage, windmill direction (the 21 Sep 2022 post settles that the cascading X, i.e. the Converging Beam, "no longer does damage if a Warden is downed, in line with their other attacks", which also supports WD29 for the other attacks); Acceleration speed-up; Penetration multiplier.
- **OPEN-WD10 Phase 3 timing.** Base slam interval (6 or 7), first slam delay, damage, tile travel speed, the phase 3 start heal, Overclocked values (see conflict 5).
  Assumption: base 6, Overclocked 5, Overclocked 2 4, Insanity 3; flag `toa.wardens.p3SlamTicks`.
- **OPEN-WD11 Siphons.** Layout tiles (solo and group), time window by group size and Insanity, damage to the Warden (% of max HP, after the 1 Sep 2022 reduction), shockwave damage / speed /
  the "reverse two" fatality, knockback of the skull tile, role of NPC 11773.
- **OPEN-WD12 Phantoms.** Spawn tick and tile, scaled stats, cadence, Insanity multiplier, path level use, melee "Keep Back" and "Double Trouble" style invocations (no source), damage.
- **OPEN-WD13 Enrage.** Lightning count and cadence, grace time, row cadence, number of rows, Insanity factors, damage, locs 45739-45746 role.
- **OPEN-WD14 Entry and exit.** Barrier 45136 role (gate locs 46167-46169 are known, WD1), party-wide dialogue gating, death and crystal sequence lengths, teleport tick, behaviour when the whole party dies in the room.
- **OPEN-WD15 Pet and core hooks.** The pet formula (WD50) covers every raid level, so Entry has no separate factor (the wiki's "much lower rate" follows from the low points); whether the core's reinsertion fires only Death Charge or every on-death hook. Assumption: Death Charge only; flag `toa.wardens.coreFiresDeathHooks = false`.
- **OPEN-WD16 Cache params.** Meaning of 0, 26, 46, 509, 510, 1361, 1398 and of varbits 14455 / 14456.
- **OPEN-WD17 Animation lengths.** The cache has no frame lengths for the Warden sequences.
- **OPEN-WD18 Penetration defence.** Assumption: only damage and overhead-prayer disable; flag `toa.wardens.penetrationLowersDefence = false`.
- **OPEN-WD19 Raid-level percentage on fixed-percent hits.** Assumption: only on hit-roll damage; flag `toa.wardens.scaleFixedPercentDamage = false`.
