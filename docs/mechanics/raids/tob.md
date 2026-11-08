# Theatre of Blood (ToB): fact sheet

Date anchor: as live on **2026-11-03** (cache 2757, captured 10:00 UTC that day). Sources read **2026-11-08**: OSRS Wiki
pages (API wikitext, copies in `/tmp/claude-0/ref/wiki-tob/`) "Theatre of Blood" (Main), "Theatre of Blood/Entry Mode"
(Entry), "Theatre of Blood/Hard Mode" (Hard), "Theatre of Blood/Strategies" and ".../Strategies/Nylocas" (Strat,
Strat/Ny), "Monumental chest" (Chest), "Chest (Theatre of Blood)" (Supply), "Bandages (Theatre of Blood)" (Bandages),
"Escape crystal" (Crystal), "Mysterious Stranger" + "(shop)" (MS), "Death (shop)", "Abigaila" (Abig), "Lil' Zik" (Zik),
"Scoreboard (Theatre of Blood)" (Score), "Strategy table", "War table", "Calculator:Theatre of Blood loot" and its
Lua module "Module:Theatre of Blood calculator" (Calc), the room, NPC and unique-item pages, and the update notes
"Theatre of Blood: Entry Mode Improvements" (1 Mar 2023, U-Entry23), "Poll 83 QoL Changes" (26 Mar 2025, U-Poll83),
"Interface Uplift Round 2" (1 Oct 2025, U-Uplift), "Summer Sweep Up - Agility & Chambers of Xeric Changes" (12 Aug
2026, U-Summer), "Quest Month Kick Off, TFA Tweaks & More" (3 Nov 2026, U-QM3Nov), "Minigame Tweaks, Skilling
Adjustments & more!" (23 May 2024, U-May24), "Halloween Fixes & Miscellaneous Changes" (23 Oct 2024, U-Oct24).
"Cache" = cache 2757 read with `cache-dump` (NPC/obj configs) and the generated symbols (gamevals). Rooms:
[tob/maiden.md](tob/maiden.md), [tob/bloat.md](tob/bloat.md), [tob/nylocas.md](tob/nylocas.md),
[tob/sotetseg.md](tob/sotetseg.md), [tob/xarpus.md](tob/xarpus.md), [tob/verzik.md](tob/verzik.md); lobby, board, stores,
treasure room, rewards: [tob/lobby-rewards.md](tob/lobby-rewards.md). Client side (varbits, interface encodings): not yet
written (R4 client contract); symbol names are listed where known.

**Changes after the anchor.** None of the ToB pages carries a ToB change dated after 2026-11-03. The newest ToB-relevant
entries are 3 Nov 2026 (update "Quest Month Kick Off, TFA Tweaks & More"; per the item and NPC changelogs on the
Escape_crystal page: value 100,000 -> 10,000 and "can now be bought from Death", Death (NPC) page: new crystal shop,
Sanguinesti_staff page: animation; the U-QM3Nov post file itself does not mention any of them), 19 Aug 2026 (scythe rename) and 12 Aug 2026 (party cooldown removed, Lil' Zik item renames). All are inside
the anchor. The Score page's global table is dated 1 Nov 2026 and is data, not a rule.

**Cache cross-check (TB-D1).** The cache confirms that the wiki's Entry Mode HP are 5-player figures: every Entry NPC
config holds the 1-player base (Maiden 500, Bloat 320, Sotetseg 560, Vasilias 360, Verzik 300/400/600) and the wiki shows
4x that (2000, 1280, 2240, 1440, 1200/1600/2400). See TB10. Escape crystal (obj 22517) has `cost = 10000` in the cache, so
the cache includes the 3 Nov change.

**Server data is not in the cache (TB-D2).** Drop tables, wave schedules, attack timers, damage numbers and the points
formula are server-side; the cache has only configs, gamevals and the client-visible state. Numbers below come from the
wiki; anything it does not state is `OPEN`.

## Party, modes, requirements

| Id | Rule | Source |
|---|---|---|
| TB1 | 1-5 players. Parties are formed at the **notice board** in Ver Sinhaza (interfaces `TOB_PARTYLIST` 364, `TOB_PARTYDETAILS` 50; scenery `TOB_SURFACE_NOTICE_BOARD` 32655). The leader can re-order the party (9 Sep 2021); the board has a filter for friends and clan mates (4 Aug 2021); the first time a player uses the board a prompt asks which mode they prefer (1 Mar 2023). Friends filter varbit `TOB_LOBBY_FRIENDS_FILTER`. **OPEN-TB1:** apply / accept / kick / leader-handover rules, size limits per mode and the board's text, none of which the wiki states (the wiki has no page for the board). | Main; U-Entry23; cache |
| TB2 | There is **no formation cooldown**: "Removed the timeout/cooldown on making parties at the Theatre of Blood" (12 Aug 2026). **OPEN-TB18:** the 3 Mar 2021 change ("The quota of daily party formations in the Theatre of Blood has increased.") is a separate quota; no source says the 12 Aug 2026 removal also removed it. Working assumption: no formation limit of any kind, behind the flag `tob.partyFormationQuota` (default off). | Main (Changes); U-Summer |
| TB3 | Modes: **Entry**, **Normal**, **Hard**. Entry was called Story mode until 28 Jul 2021. Only hard requirement for the raid: the quest Priest in Peril (access to Morytania); the starting quest "A Night at the Theatre" is finished by completing any mode. Skill levels on the Strat page are suggestions, not requirements. | Main; Entry; Strat |
| TB4 | **Hard mode needs at least one Normal completion for every participant** ("all participants must have at least one normal mode completion"). Entry needs nothing. **OPEN-TB2:** whether a Hard completion also satisfies the requirement (the wiki only says a Normal run) and whether it is checked on joining the party or on start (framework re-checks at both, RC7). | Hard; Main |
| TB5 | Group Ironmen lose prestige entering with a player outside their group; Ironmen are reward-eligible when completing alongside others; HCIM lose status and HCGIM a life on a death in the raid, in every mode (Entry included). | Main; Entry; Maiden |
| TB6 | The theatre is a safe zone on PvP worlds (28 Apr 2021). Bosses do not count for Slayer tasks. | Main (Changes) |

## Order, structure, restores

| Id | Rule | Source |
|---|---|---|
| TB7 | Linear and fixed, no randomisation: **Maiden -> Bloat -> Nylocas (Vasilias) -> Sotetseg -> Xarpus -> Verzik**. Levels shown by Main: 940, 870, 800, 995, 960, 1040 (Normal). | Main; Strat |
| TB8 | In every room before Verzik a **barrier** separates the party from the boss and the fight starts only once a player crosses it; a player can therefore skip a fight in a bigger party (this reduces that player's drops, see TB27). Each boss has a percentage health bar; the Nylocas build-up shows the four pillars' health instead. | Strat |
| TB9 | When a boss dies, HP, prayer, run energy and special attack energy of all party members are restored to full; drained stats are not. Entry: HP and prayer are replenished after each boss. On entering the raid special energy is set to full (the player's own amount is remembered and restored on exit), and special energy keeps regenerating inside (3 Jun 2021). The overall raid timer starts when the party starts the Maiden fight (23 Jun 2021). Music tracks per room: see Main. | Strat; Entry; Main (Changes) |

## Scaling

| Id | Rule | Source |
|---|---|---|
| TB10 | **Normal and Hard:** boss HP is the 5-player value for 5 players, **87.5%** for 4 and **75%** for 3 or fewer (21 Jun 2018 added the 4 and 3 steps). The wiki infobox HP are the 5-player values and Strat repeats the percentages. **OPEN-TB3:** rounding of 87.5% / 75% results (3,500 x 0.875 = 3,062.5; the NPC pages show floors, e.g. Nylocas 22 -> 19 / 16, so floor is the working assumption). | Main; Strat; Strat/Ny |
| TB11 | **Entry:** since 1 Mar 2023 HP scales by adding to the 1-player base **+90%** for the 2nd player, **+80%** for the 3rd, **+70%** for the 4th, **+60%** for the 5th (cumulative): x1.0, x1.9, x2.7, x3.4, x4.0. The infobox Entry HP are the x4.0 (5-player) values; the cache holds the base. Room pages still say a solo Entry boss has "20% of its original HP"; that is the pre-2023 text (x5 at five players). See conflicts in each room doc. | U-Entry23; cache; Maiden / Bloat / Vasilias pages |
| TB12 | Entry NPCs have reduced stats and deal "~50% less damage" than Normal. Hard NPCs have more HP but combat stats "usually the same or lower", with changed and added mechanics; Hard NPCs use separate ids (room docs). | Entry; Hard |
| TB13 | Per-mode numbers (HP, max hits, stats, ids) are on each room doc; the per-NPC "scaled HP" flag in the infobox means HP follows TB10/TB11. | room docs |

## Death, wipe, disconnect

| Id | Rule | Source |
|---|---|---|
| TB14 | A dead player is placed outside the fighting area (purgatory; seq `TOB_PURGATORY_STANCE` 8070) and rejoins the party when the current challenge is cleared by the others. Every death in the theatre is a **dangerous death** (HCIM lose status, HCGIM lose a life, "regardless of whether the team successfully completes the challenges or not"), and the contents of an Item Retrieval Service are lost. | Main |
| TB15 | **Team wipe (Normal, Hard):** the raid ends, all unprotected items are lost and are reclaimed from a storage chest (like Torfinn's) for **100,000 coins**; untradeable items normally kept on death go into that chest too. Dying "elsewhere" without claiming first loses the stored items for good. Chest scenery: `TOB_SURFACE_GRAVESTONE_CHEST` 32656 (also `MYQ4_TOB_GRAVESTONE_CHEST` 32596, quest). **OPEN-TB4:** where wiped players appear (the entrance is assumed), whether more than one batch can be stored. | Main; Strat |
| TB16 | **Entry:** unlimited attempts; reclaiming is free; the party gets Bandages after a wipe, "the number of supplies depends on how much stuff you used in your previous attempt" (1 Mar 2023), and 3 more Bandages after dying at any boss (Entry page). Each failure takes away from the loot at the end. **OPEN-TB5:** conflict on what a wipe resets: Entry says losing the raid "means it must be started over"; the Maiden / Bloat / Vasilias pages say a wiped team is "taken back outside of the boss room and has unlimited attempts to fight the boss again". The retry of the current room is implemented as the working assumption (flag `tob.entry.wipeRestartsRoom`). **OPEN-TB6:** Bandage count after a wipe and the exact "used supplies" measure. | U-Entry23; Entry; Maiden |
| TB17 | **Disconnect or logout during a fight counts as a death** (HCIM lose status). A logout in a hallway (not in an encounter) is a safe death. On logging back in after an unsafe disconnect the game tries to reunite the player with the party and puts them in purgatory; if that fails it is a full death and the reclaim fee applies, and Item Retrieval contents are lost. Re-logging inside the theatre after Verzik dies re-syncs the player to the party while any member remains (26 Jul 2018). **OPEN-TB7:** the tile a hallway-logout player returns to; which rooms count as "encounter" (assumed: from the barrier crossing to the boss's death). | Main; Main (Changes) |
| TB18 | All normal teleports are blocked inside. The only way out is an **escape crystal** (obj `TOB_TELEPORT` 22517). Other raid items (Dawnbringer) are destroyed or dropped if taken out: see Xarpus doc. **OPEN-TB19:** what happens to Bandages (Theatre of Blood) taken out of the raid; no source states it (the Bandages page only says uncollected bandages in the chest are deleted, TB23). | Main; Crystal |

## Escape crystals

| Id | Rule | Source |
|---|---|---|
| TB19 | Price: sold by the Mysterious Stranger at the entrance (and by Rick in the Wizards' Tower and, since 3 Nov 2026, by Death in Death's Office, shop `Death`). **Conflict:** Main, Strat and the brief say 75,000 coins; Crystal says "for 10,000 coins", its infobox value is 10,000 (the value was "decreased from 100,000 to 10,000" on 3 Nov 2026, update "Quest Month Kick Off, TFA Tweaks & More", per the Escape_crystal changelog; the U-QM3Nov post file does not mention it) and the cache `cost` is 10,000; the shop lines use a 1000 per-mille multiplier (= 100% of value). **OPEN-TB8:** the shop price. Working value 10,000 (cache + Crystal + the shop multiplier) behind the flag `tob.escapeCrystalPrice`; the 75,000 text is left in the ToB pages. The 75,000 price has no source-backed history: no changelog on any local page records a 75,000 price or its change, and the only recorded value history is 100,000 -> 10,000, so whether 75,000 was ever the shop price is unknown. Stock 9,500, restock 1. | Crystal; MS; Death (shop); Main; cache |
| TB20 | Use: "Teleport" in or out of the raid; the crystal can be used on a party member that also has a crystal to activate both (both consumed): added 4 Aug 2021 with the right-click option "Toggle"; the option was renamed "Configure" on 21 Jun 2023 (Crystal changelog; the page text now says select "Configure" then the first option). Logging out while the crystal is in the inventory consumes it and teleports the player out on next login (26 Mar 2025; the update says this preserves HCIM status). Auto-AFK configuration (2-300 s of inactivity then damage), worn as a ring-of-life substitute (then it cannot teleport out). **OPEN-TB9:** whether using a crystal in the middle of a fight counts as a dangerous death (the Crystal page lists ToB among the activities where a crystal teleport "may count as dangerous deaths"; the 26 Mar 2025 note covers only the logout case). Antispam varbit `TOB_CRYSTAL_ANTISPAM`. | Crystal; U-Poll83 |

## Spectating

| Id | Rule | Source |
|---|---|---|
| TB21 | Spectate by attempting to enter the theatre or by talking to **Abigaila** (NPC 8368; upstairs in the Lumbridge General Store or beside the spirit tree north-east of the Grand Exchange). Choose a party member's or a spectator's name, or the most recently formed party. The target must be on the same world; if the target's Private chat is "Friends only" the spectator must be on their Friends list. No items may be carried except the oculus orb; the spectator must not be poisoned or diseased. Placement: ledges in Maiden / Bloat / Sotetseg, a ceiling web in Nylocas, a high ledge behind bars in Xarpus, a cage beside the throne in Verzik, beside the war table in the vault. Since 23 Oct 2024 scrying and public chat between spectators and fighters work in Nylocas, Xarpus and Verzik too. Spectators unlock room music. Camera: right-click ledges / chatbox rotation. **OPEN-TB10:** spectator cap, what a spectator sees when the party wipes, and the spectator enter / leave messages. | Main; Abig; U-Oct24 |

## Supplies

| Id | Rule | Source |
|---|---|---|
| TB22 | **Midway supply chests** open after Bloat and after Sotetseg; items are bought with points (prices and rules in [tob/lobby-rewards.md](tob/lobby-rewards.md) LR9-LR13). Points depend on each player's own performance; leftover points carry to the second chest. Normal / Hard only. | Main; Supply; Strat |
| TB23 | **Entry:** each chest instead holds **10 Bandages** (obj 25730); leftover Bandages do not carry over; the chest holds at most 10. Bandages are also provided after wipes (TB16). | Entry; Bandages; Supply |

## Verzik quick-start

| Id | Rule | Source |
|---|---|---|
| TB24 | Since 1 Oct 2025 Verzik has a right-click **Quick-start** option for players with at least one prior completion, in Normal and Hard; not in Entry. **OPEN-TB11:** which mode's completion counts and whether it is per party (all members) or per player; the dialogue skipped. | U-Uplift; Main |

## Rewards (summary; tables in [tob/lobby-rewards.md](tob/lobby-rewards.md))

| Id | Rule | Source |
|---|---|---|
| TB25 | Each player has their own monumental chest in the treasure vault; up to five chests; an arrow marks the player's own; a purple aura and an arrow design on the chest show a unique. One unique **replaces all commons**; otherwise the common table is rolled **three times** (duplicates allowed). Non-stackable commons are bank notes. | Main; Chest |
| TB26 | Unique chance for the team, **deathless**: **1/9.1** Normal, **1/7.7** Hard, none in Entry; independent of team size; allocated to one player, weighted by deaths and damage per boss, the MVP most likely. Deaths lower both the unique chance and the common quantities. The exact in-game formula is not public (**OPEN-TB12**). The model used is the one in the wiki's own calculator module (Calc), recorded in lobby-rewards LR16-LR17. | Chest; Calc |
| TB27 | Model constants from Calc (all **OPEN-TB12** until a primary source exists): individual points = 3 per room entered (max 18) + MVP points (14 per raid, max 32 per player) - 4 per death, floor 0; maximum team score = 18 x players + 14; team unique chance = (1/9.1 or 1/7.7) x team score / maximum; player's share = player points / team points. MVP points (top damage on): Maiden 2, Bloat 2, Nylocas boss 1, Sotetseg 1, Xarpus 2, Verzik P1 2, P2 2, P3 2 = **14**. Damage on Nylocas Matomenos counts at Maiden and Verzik (3 Jun 2021). | Calc; Zik |
| TB28 | Unique weights (denominator **19** Normal, **18** Hard): Avernic defender hilt 8 (Hard 7), Ghrazi rapier 2, Sanguinesti staff 2, Justiciar faceguard 2, chestguard 2, legguards 2, Scythe of Vitur 1. The Hard hilt weight was lowered by 1 on 25 Jan 2022 (Mod Arcane). A player with 0 points gets a cabbage and a Message instead. | Chest; Hard; Calc |
| TB29 | Common quantity multipliers: **Entry 20%** of Normal ("80% less", raised from 15% on 1 Mar 2023); **Hard +15%** (x1.15) and a further +15% (x1.30 total) when the raid is finished inside the target time. Target times (total raid time including the gaps): 5 players 25:00, 4 players 27:00, 3 players 30:00, 2 players 35:00, 1 player 1:10:00 (the 3-5 player limits presumably include the 4 Jun 2021 hotfix's +2 minutes; the Hard page does not date the table). | Main; Hard; Chest; U-Entry23; Calc |
| TB30 | Tertiaries (roll per player; "Drop rate scales down based on the individual's performance", curve **OPEN-TB13**): elite clue scroll base 1/25 Entry, 3/25 Normal, 3.5/25 Hard; Lil' Zik 1/650 Normal, 1/500 Hard (range down to 1/6,500 and 1/5,000; maximum rate at 10 individual points; none in Entry); Hard only: Sanguine dust 1/275, Sanguine ornament kit 1/150, Holy ornament kit 1/100. **OPEN-TB14:** the Hard page says the Hard-exclusive rewards need the target time, the Chest page does not; the dust needs the time per Zik. Time requirement assumed for all three behind `tob.hardTertiariesNeedTargetTime`. | Chest; Hard; Zik |
| TB31 | Unclaimed loot: claimable at a chest just outside the Ver Sinhaza bank (7 Apr 2021); the Chest page also says unclaimed rewards "will be lost upon logging out". **OPEN-TB15:** which applies when (the brief asks to confirm). Working rule: unclaimed rewards persist at the lobby chest until claimed, behind `tob.unclaimedLootExpiresOnLogout` (default false). | Chest; Main (Changes) |

## Records

| Id | Rule | Source |
|---|---|---|
| TB32 | Kill-count varps: Normal `TOTAL_COMPLETED_THEATREOFBLOOD` = 1748, Hard `TOTAL_COMPLETED_THEATREOFBLOOD_HARD` = 3057, Entry `TOTAL_COMPLETED_THEATREOFBLOOD_STORY` = 3058 (Entry's old name). Collection-log counter varbit `COLLECTION_RAIDS_TOB_COMPLETED` 11994. Sinhaza shroud capes at 100 / 500 / 1,000 / 1,500 / 2,000 Normal+Hard completions (claimed from the Mysterious Stranger, "Change" to swap). **OPEN-TB16:** whether Entry completions count toward the capes (the wiki says "Normal/Hard"). | cache; Main; MS |
| TB33 | Messages (exact, from the brief's captured fixtures; **OPEN-TB17** until seen in game): `Your completed Theatre of Blood count is: <col=ff0000>73</col>.`; `Theatre of Blood completion time: <col=ff0000>17:00</col> (new personal best)`; `Theatre of Blood total completion time: <col=ff0000>24:40.20</col>. Personal best: 20:45.00`. Not on the wiki: the Hard / Entry variants (the KC message presumably names the mode), the per-room completion message (9 Jun 2021 added the mode name to it), the "has discovered a large ball" style boss messages beyond those quoted in room docs. The first message seems to report the challenge time and the second the overall time (Score defines both); not stated. | brief 1.3; Score |
| TB34 | Personal bests are kept per **mode and team-size bucket** (3 Jun 2021: split by group size; the old PB moved to the 5-player bucket). Buckets per Score: 1, 2, 3, 4, 5 players. **Challenge time** = time spent fighting bosses; **overall time** = from entering the raid to the finishing blow on Verzik. | U (3 Jun 2021, Main changes); Score |
| TB35 | Scoreboard in the eastern hall of Ver Sinhaza (scenery 32987, "Read"; interface `TOB_SCOREBOARD` 363, tab varbit `TOB_SCOREBOARD_TAB`) shows personal and global attempts, completions, deaths and best times per mode. The strategy table (33014, participants) and war table (33011, spectators) in the vault show per-player deaths, time per room, the MVP, challenge and overall time. | Score; Strategy table; War table |

## As implemented (content/raids-tob)

Structure: D-081. Definition `TobRaid` (raid-core `RaidDefinition`, id `tob`, 1-5 players, modes normal / entry / hard, nine map squares,
slots maiden, bloat, nylocas, sotetseg, xarpus, verzik, treasure; `autoAdvance` off). Fights are `content/combat` boss scripts; this module
spawns, scores and moves the party. Room docs have a matching `As implemented (content/combat ...)` section.

- **Entry and mode (TB1-TB4):** the notice board `TobBoard` (364 list, 50 details) holds forming parties (oldest first, at most 45 rows).
  Party setting `mode` 0 Normal, 1 Entry, 2 Hard (saved preferred mode asked on first use; a Hard preference falls back to Normal if the
  leader has no Normal or Hard completion). Hard needs `TOTAL_COMPLETED_THEATREOFBLOOD + _HARD > 0` for every member (OPEN-TB2: Hard counts),
  checked on setting the mode, on apply/accept and on the leader entering. The raid itself has no requirement (Priest in Peril is not
  checked). Leader: preferred size 0..5 and level 0..126, mode, kick (blocks re-apply, flag `tp.tob.board.kickBlocksReapply`), accept /
  reject (reject blocks, flag `rejectBlocksReapply`), unblock, move up/down (rows 1.. only, flag `reorderMovesLeader`), disband; members
  leave; applicants withdraw. The friends/clan filter button only toggles `TOB_LOBBY_FRIENDS_FILTER` and refreshes: the server does not
  filter the list. The leader enters at the theatre entrance (others get "Your party leader must enter the Theatre first." until the run
  starts, then follow); with no party the entrance offers spectating.
- **Rooms and barriers (TB7, TB8):** per room an antechamber entry and a wait tile (`TobRaid`). Barrier `TOB_ARENA_BARRIER` "Pass" moves the
  player two tiles across; the first crossing into the arena sets `engaged` and the fight start tick and records the player in
  `entered[room]` (3 points each room, TobLoot). While engaged no one can pass back out ("You can't leave while the fight is in
  progress."; OPEN-TB7 text). A room is left through an exit box at the end of its corridor (template boxes maiden `3213-3225 x
  4456-4460`, bloat `3320-3322 x 4433-4462`, nylocas L1 `3292-3306 x 4281-4286`, sotetseg `3273-3286 x 4332-4334`, xarpus L1 `3164-3176
  x 4374-4377`): the first member inside it advances the whole party. Verzik's room is left by the throne door (see Treasure room).
  Spawns (`TobBosses`): Maiden SW tile (3162, 4444), Bloat (3288, 4440), four supports L1 at (3289|3298, 4242|4251), Sotetseg (3277, 4325),
  Xarpus (3169, 4386, L1), Verzik (3166, 4312) (OPEN-TB7, chosen in free tiles). A room is won when every NPC of its "required" set is dead:
  Maiden the dying-b form or 30% form, Bloat, Vasilias' three forms, Sotetseg's combat form, Xarpus' combat form, Verzik's P3 form.
- **Boss death (TB9, TB27):** `roomWon`: room time added to the challenge time; MVP points to the top damage dealer of the room (Verzik:
  separately for each of her three forms by per-phase hit damage, MVP table in `TobRewards`); every present member gets HP to full, prayer
  to at least base and, outside Entry, run energy 10,000 and special energy 1,000 (Entry: HP and prayer only). Bloat and Sotetseg open a
  supply chest. After Verzik: overall time ends at her death, each member's points (3 x rooms entered + MVP) are credited, the target-time
  flag is set when `tick - run.startedTick <= TobLoot.targetTicks(partySize)` (7,000 / 3,500 / 3,000 / 2,700 / 2,500 ticks) and the throne
  door appears (loc `TOB_DUNGEON_VERZIK_THRONE_DOOR_OPENED` at (3166, 4323), 5 x 4); using it moves the party to the treasure room.
- **Scaling (TB10, TB11):** `TobScaling`: HP = floor(cache HP x permille / 1000), Normal / Hard 750, 750, 750, 875, 1000; Entry 1000, 1900, 2700,
  3400, 4000 for 1-5 players (minimum 1). Not scaled: blood spawns, webs, Nylocas supports (OPEN-NY4). Combat scripts apply the same table
  (`TobModes.scale`) to NPCs they spawn.
- **Deaths (TB14-TB17):** policy `WaitUntilRoomEnds`; Normal / Hard wipe = `FailRaid` with unprotected items stored in inventory `tob_reclaim`;
  Entry wipe = retry the room (flag `tp.tob.entry.wipeRestartsRoom`, default on, OPEN-TB5): once all present members are alive, the room's
  NPCs in the instance are despawned, every present member gets **3** Bandages and the room is entered afresh. An Entry death also owes the
  dead member 3 Bandages, paid when they are alive again (OPEN-TB6: no "used supplies" measure). Reclaim chest `TOB_SURFACE_GRAVESTONE_CHEST`
  "Claim": one transaction paying `tp.tob.reclaimFee` (100,000) and moving everything stored to the inventory; refused as a whole if it does not
  fit or the coins are missing. Disconnect = `DEATH_IN_ENCOUNTER`; a logout holding an escape crystal is a safe logout. Raid-only items (Bandages,
  Dawnbringer) are set in the definition; their removal is raid-core's.
- **Dawnbringer (XA6):** loc `TOB_DUNGEON_XARPUS_DEAD_MERC_MULTI` op1, once a run, only after Xarpus' room time is recorded; added to the
  taker's inventory with the quoted message. Crumbling is in TobVerzik (P1 shield break).
- **Supplies (TB22, TB23):** chests at Bloat (3269, 4449) and Sotetseg (3279, 4308) (OPEN-LR15). Normal / Hard: when the chest opens each present
  member is credited points from their score (MVP so far minus 4 per death) against the team average: above 10-13, average 8-11, below 6-9; within
  the tier the top value minus their deaths, not below the tier's bottom (model `tiers`; `flat` = 8, flag `tp.tob.supplyPointsModel`); points carry,
  capped at 31 (5-bit varbit). Interface 405: stock and prices from enums 1952 / 1953; Value, Buy 1 / last X / All / X; limited by points and space;
  Stamina potion(4) one per member per chest; the note slot gives the Message once. Entry: no points; the note slot is **10** Bandages per member per
  chest, taken by "Take" (leftovers are not carried).
- **Escape crystal (TB18-TB20):** "Teleport" removes one crystal and `Raids.leave`; used on a party member who holds one, both are consumed and both
  leave; antispam stamp in `TOB_CRYSTAL_ANTISPAM` (OPEN-TB20: stamps the tick of the last use so a repeat in the same tick does nothing; the varbit's
  real purpose is undocumented); logging out inside with one in the backpack or the ring slot consumes it and the next login leaves the raid. A
  crystal in a fight is a plain leave (OPEN-TB9). Not implemented: Auto-AFK, "Configure", the ring-of-life substitute.
- **Treasure room, loot (TB25-TB31):** entering it runs `Raids.complete(endedAt = Verzik's death)`: the reward roll is `TobLoot` and pays into the
  claim inventory `TOB_CHESTS` (612). Per member `own = max(0, points - 4 x deaths)`; team score `raw` = sum of (points - 4 x deaths), maximum
  `18 x members + 14`; ratio = team / maximum; the unique chance is `1/9.1` Normal, `1/7.7` Hard, none in Entry, times the ratio, rolled once and
  assigned to member i with probability `chance x own_i / team`; a member with 0 points gets a cabbage and the Message; the unique recipient gets one
  unique by weight (8 or 7, 2, 2, 2, 2, 2, 1; staff and scythe as uncharged) plus tertiaries; the others get 3 rolls on the 30-slot common table with
  quantity `floor(min x q x ratio)` up to +20% (q: Entry 0.20, Normal 1, Hard 1.15 or 1.30 in the target time; zero quantities drop out) plus
  tertiaries. Tertiaries: elite clue 1/25 x 1 / 3 / 3.5 (Entry / Normal / Hard) not scaled by performance; Lil' Zik (not Entry) linear between the
  worst (1/6,500 or 1/5,000) at 0 points and the best (1/650 or 1/500) at 10 points (`tp.tob.petFullRatePoints`); Hard: Sanguine dust 1/275,
  Sanguine kit 1/150, Holy kit 1/100, only inside the target time (flag `hardTertiariesNeedTargetTime`, default on). One chest per party slot (up to
  5) at the five anchors (OPEN-LR16 order), each on the viewer's own varbit (own and rare bits); only the owner opens it ("This is not your chest.").
  Interface 23: Take, Bank (refused for `no bank` items, placeholder not handled), Discard, Take-all, Bank-all, Discard-all, all through the item
  transaction API. `TOB_SHOULD_HAVE_LOOT` is 1 while 612 holds items; unclaimed loot waits at the lobby chest unless `unclaimedLootExpiresOnLogout`
  (default off, OPEN-TB15). Teleport crystal 32996 leaves.
- **Spectating (TB21):** Abigaila's "Scry" or the entrance without a party lists the 4 most recent open runs ("<leader>'s party") and Cancel.
  Refused when the player's party is locked in a raid, when anything but the oculus orb is carried (backpack or worn), or when poisoned or
  diseased. An observer is placed on the room's spectator tile once per room (Maiden (3176, 4431, L1), Bloat (3281, 4436, L1), Sotetseg (3276,
  4301, L1), Verzik (3168, 4326, L1), treasure room (3231, 4326); Nylocas and Xarpus use the wait tile); teleports are denied ("You can't teleport
  while spectating."); ends on the HUD Exit button, logout, or a failed / abandoned / closed run, returning to the origin tile. Not enforced: the
  Friends-only private chat rule, choosing by typed name, a cap (OPEN-TB10).
- **HUD (28) and board text:** `TobHud` every tick for each live run: overlay 28 opened, `PARTYSTATUS` 2, own `PARTYSLOT` (1-based; 0 spectator),
  orbs P0-P4 = 0 vacant, 30 dead / offline / not inside, else `1 + round(26 x HP / max)`, names through client script 2301 only when they change;
  `WAVEPROGRESS_TYPE` stays 0, so the Nylocas wave bar and the combined pillar bar are not driven (OPEN-TB-UI8). The lobby text: `PARTYSTATUS` 0 none,
  1 party, 2 run started and this member not yet inside, refreshed every 5 ticks in the lobby box.
- **Records, scoreboard, tables (TB32-TB35):** `RecordsDef` KC varps per mode (Normal 1748, Entry 3058, Hard 3057); messages: KC line (Hard / Entry
  wording assumed, OPEN-TB17) and one total-time line with the personal best (`raids.tob.pb`). `TobStatsLedger` keeps saved personal counters
  `tob.stats.<mode>.attempts / completions / deaths` and the best challenge and overall ticks per party size; the "Global" column is this world's
  in-memory counters (OPEN-LR11), reset on restart. Scoreboard 363 (tab varbit 12270), strategy / war table 459 (deaths, total deaths, MVP name, room
  times, challenge time = sum of room times, overall time).

### Gaps between this doc and the code

- TB2: the flag `tob.partyFormationQuota` does not exist (no formation limit of any kind).
- TB3: Priest in Peril is not checked (`TobConfig.requirement` returns null).
- TB5, TB14, TB17 (purgatory placement, HCIM / Group Ironman handling, hallway logout), TB18 (blocked teleports): not in this module; raid-core.
- TB9: the special-energy "remembered and restored on exit" and the raid timer starting at the Maiden fight are not visible here (the timer uses
  `run.startedTick`; the Maiden's fight start tick is held in `TobState` but not used for the overall time).
- TB19: `TobConfig.escapeCrystalPrice` is defined but nothing reads it; the Mysterious Stranger's `shops.tsv` row sets the price.
- TB20: Auto-AFK and "Configure" are not implemented.
- TB21: Friends-only chat rule and typed-name selection are not implemented.
- TB1: the friends/clan filter is not applied to the list; no board texts are captured (OPEN-TB-UI1).
- TB33: only one time line is produced (overall); the challenge-time line is not.
- TB29: target times are 7,000 / 3,500 / 3,000 / 2,700 / 2,500 ticks (docs' times without the 4 Jun 2021 +2 minutes).
- TB8 / TB34: a room's fight time and "rooms entered" come from the barrier crossing; if Verzik's room has no `TOB_ARENA_BARRIER` crossing, her room
  time would be 0 and her room would not count for the 3 points. Not checked against the map.
- Code-only OPEN ids with no entry in any doc: OPEN-TB20, OPEN-TB-UI1..UI8 (UI1 board texts, UI2 maximum preferred level 126, UI3 kick / reject block
  re-apply, UI4 list-row op flag, UI5 reorder may move the leader, UI7 orb 30 for dead / offline, UI8 wave bar hidden); OPEN-LR15..LR23 appear in code
  but lobby-rewards.md lists OPEN-LR1..LR14.

## Open list (raid-wide)

OPEN-TB1 board rules; OPEN-TB2 Hard requirement details; OPEN-TB3 HP rounding; OPEN-TB4 wipe landing and storage; OPEN-TB5
Entry wipe scope; OPEN-TB6 Entry bandage count; OPEN-TB7 hallway tile and "encounter" bounds; OPEN-TB8 crystal price;
OPEN-TB9 crystal in a fight; OPEN-TB10 spectator cap; OPEN-TB11 quick-start eligibility; OPEN-TB12 reward formula;
OPEN-TB13 tertiary performance curve; OPEN-TB14 Hard tertiary time requirement; OPEN-TB15 unclaimed loot expiry;
OPEN-TB16 Entry and shrouds; OPEN-TB17 message strings; OPEN-TB18 3 Mar 2021 party quota; OPEN-TB19 bandages taken out of the
raid; OPEN-TB20 the antispam varbit's purpose (working assumption: it stamps the tick of the last crystal use). **20 open items.**

## Conflicts between sources (raid-wide)

1. Escape crystal price: 75,000 (Main, Strat, brief) vs 10,000 (Crystal, cache): TB19.
2. Entry solo HP "20% of original" (Maiden, Bloat, Vasilias pages) vs the 1 Mar 2023 cumulative scale (solo = 25% of the 5-player figure; cache agrees): TB11.
3. Entry wipe: restart whole raid (Entry) vs retry the room (boss pages): TB16.
4. Hard Sanguine dust and kits: needs target time (Hard, Zik) vs plain Hard drop (Main, Chest): TB30.
5. Unclaimed loot: persists at the lobby chest (7 Apr 2021) vs "lost upon logging out" (Chest): TB31.
6. Midway points: Supply's 6-13 and Strat's tiers 10-13 / 8-11 / 6-9 are consistent; only the Hard page's "average of 15 points, though the highest is 18" differs (the Hard page does not say whether its figure is per chest or carried over): lobby-rewards LR10.
