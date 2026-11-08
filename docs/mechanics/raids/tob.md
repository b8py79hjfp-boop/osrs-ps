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
entries are 3 Nov 2026 (U-QM3Nov: escape crystal value 100,000 -> 10,000, Death sells crystals, Sanguinesti staff
animation), 19 Aug 2026 (scythe rename) and 12 Aug 2026 (party cooldown removed, Lil' Zik item renames). All are inside
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
| TB2 | There is **no formation cooldown**: "Removed the timeout/cooldown on making parties at the Theatre of Blood" (12 Aug 2026). The older daily quota (3 Mar 2021) is therefore gone. | Main (Changes); U-Summer |
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
| TB14 | A dead player is placed outside the fighting area (purgatory; seq `TOB_PURGATORY_STANCE` 8070) and rejoins the party when the current challenge is cleared by the others. Every death in the theatre is a **dangerous death** (HCIM lose status, HCGIM lose a life, "regardless of whether the team completes"), and the contents of an Item Retrieval Service are lost. | Main |
| TB15 | **Team wipe (Normal, Hard):** the raid ends, all unprotected items are lost and are reclaimed from a storage chest (like Torfinn's) for **100,000 coins**; untradeable items normally kept on death go into that chest too. Dying "elsewhere" without claiming first loses the stored items for good. Chest scenery: `TOB_SURFACE_GRAVESTONE_CHEST` 32656 (also `MYQ4_TOB_GRAVESTONE_CHEST` 32596, quest). **OPEN-TB4:** where wiped players appear (the entrance is assumed), whether more than one batch can be stored. | Main; Strat |
| TB16 | **Entry:** unlimited attempts; reclaiming is free; the party gets Bandages after a wipe, "the number of supplies depends on how much stuff you used in your previous attempt" (1 Mar 2023), and 3 more Bandages after dying at any boss (Entry page). Each failure takes away from the loot at the end. **OPEN-TB5:** conflict on what a wipe resets: Entry says losing the raid "means it must be started over"; the Maiden / Bloat / Vasilias pages say a wiped team is "taken back outside of the boss room and has unlimited attempts to fight the boss again". The retry of the current room is implemented as the working assumption (flag `tob.entry.wipeRestartsRoom`). **OPEN-TB6:** Bandage count after a wipe and the exact "used supplies" measure. | U-Entry23; Entry; Maiden |
| TB17 | **Disconnect or logout during a fight counts as a death** (HCIM lose status). A logout in a hallway (not in an encounter) is a safe death. On logging back in after an unsafe disconnect the game tries to reunite the player with the party and puts them in purgatory; if that fails it is a full death and the reclaim fee applies, and Item Retrieval contents are lost. Re-logging inside the theatre after Verzik dies re-syncs the player to the party while any member remains (26 Jul 2018). **OPEN-TB7:** the tile a hallway-logout player returns to; which rooms count as "encounter" (assumed: from the barrier crossing to the boss's death). | Main; Main (Changes) |
| TB18 | All normal teleports are blocked inside. The only way out is an **escape crystal** (obj `TOB_TELEPORT` 22517). Other raid items (Dawnbringer, bandages) are destroyed or dropped if taken out: see Xarpus doc. | Main; Crystal |

## Escape crystals

| Id | Rule | Source |
|---|---|---|
| TB19 | Price: sold by the Mysterious Stranger at the entrance (and by Rick in the Wizards' Tower and, since 3 Nov 2026, by Death in Death's Office, shop `Death`). **Conflict:** Main, Strat and the brief say 75,000 coins; Crystal says "for 10,000 coins", its infobox value is 10,000 (changed from 100,000 on 3 Nov 2026) and the cache `cost` is 10,000; the shop lines use a 1000 per-mille multiplier (= 100% of value). **OPEN-TB8:** the shop price. Working value 10,000 (cache + Crystal + the shop multiplier) behind the flag `tob.escapeCrystalPrice`; the 75,000 text predates 3 Nov and is left in the ToB pages. Stock 9,500, restock 1. | Crystal; MS; Death (shop); Main; cache |
| TB20 | Use: "Teleport" in or out of the raid; "Configure" on the crystal then using it on a party member that also has a crystal activates both (both consumed; 4 Aug 2021). Logging out while the crystal is in the inventory consumes it and teleports the player out on next login (26 Mar 2025; the update says this preserves HCIM status). Auto-AFK configuration (2-300 s of inactivity then damage), worn as a ring-of-life substitute (then it cannot teleport out). **OPEN-TB9:** whether using a crystal in the middle of a fight counts as a dangerous death (the Crystal page lists ToB among the activities where a crystal teleport "may count as dangerous deaths"; the 26 Mar 2025 note covers only the logout case). Antispam varbit `TOB_CRYSTAL_ANTISPAM`. | Crystal; U-Poll83 |

## Spectating

| Id | Rule | Source |
|---|---|---|
| TB21 | Spectate by attempting to enter the theatre or by talking to **Abigaila** (NPC 8368; upstairs in the Lumbridge General Store or beside the spirit tree north-east of the Grand Exchange). Choose a party member's or a spectator's name, or the most recently formed party. The target must be on the same world; if the target's Private chat is "Friends only" the spectator must be on their Friends list. No items may be carried except the oculus orb; the spectator must not be poisoned or diseased. Placement: ledges in Maiden / Bloat / Sotetseg, a ceiling web in Nylocas, a high ledge behind bars in Xarpus, a cage beside the throne in Verzik, beside the war table in the vault. Since 23 Oct 2024 scrying and public chat between spectators and fighters work in Nylocas, Xarpus and Verzik too. Spectators unlock room music. Camera: right-click ledges / chatbox rotation. **OPEN-TB10:** spectator cap, what a spectator sees when the party wipes, and the spectator enter / leave messages. | Main; Abig; U-Oct24 |

## Supplies

| Id | Rule | Source |
|---|---|---|
| TB22 | **Midway supply chests** open after Bloat and after Sotetseg; items are bought with points (prices and rules in [tob/lobby-rewards.md](tob/lobby-rewards.md) LR5-LR9). Points depend on each player's own performance; leftover points carry to the second chest. Normal / Hard only. | Main; Supply; Strat |
| TB23 | **Entry:** each chest instead holds **10 Bandages** (obj 25730); leftover Bandages do not carry over; the chest holds at most 10. Bandages are also provided after wipes (TB16). | Entry; Bandages; Supply |

## Verzik quick-start

| Id | Rule | Source |
|---|---|---|
| TB24 | Since 1 Oct 2025 Verzik has a right-click **Quick-start** option for players with at least one prior completion, in Normal and Hard; not in Entry. **OPEN-TB11:** which mode's completion counts and whether it is per party (all members) or per player; the dialogue skipped. | U-Uplift; Main |

## Rewards (summary; tables in [tob/lobby-rewards.md](tob/lobby-rewards.md))

| Id | Rule | Source |
|---|---|---|
| TB25 | Each player has their own monumental chest in the treasure vault; up to five chests; an arrow marks the player's own; a purple aura and an arrow design on the chest show a unique. One unique **replaces all commons**; otherwise the common table is rolled **three times** (duplicates allowed). Non-stackable commons are bank notes. | Main; Chest |
| TB26 | Unique chance for the team, **deathless**: **1/9.1** Normal, **1/7.7** Hard, none in Entry; independent of team size; allocated to one player, weighted by deaths and damage per boss, the MVP most likely. Deaths lower both the unique chance and the common quantities. The exact in-game formula is not public (**OPEN-TB12**). The model used is the one in the wiki's own calculator module (Calc), recorded in LR12. | Chest; Calc |
| TB27 | Model constants from Calc (all **OPEN-TB12** until a primary source exists): individual points = 3 per room entered (max 18) + MVP points (14 per raid, max 32 per player) - 4 per death, floor 0; maximum team score = 18 x players + 14; team unique chance = (1/9.1 or 1/7.7) x team score / maximum; player's share = player points / team points. MVP points (top damage on): Maiden 2, Bloat 2, Nylocas boss 1, Sotetseg 1, Xarpus 2, Verzik P1 2, P2 2, P3 2 = **14**. Damage on Nylocas Matomenos counts at Maiden and Verzik (3 Jun 2021). | Calc; Zik |
| TB28 | Unique weights (denominator **19** Normal, **18** Hard): Avernic defender hilt 8 (Hard 7), Ghrazi rapier 2, Sanguinesti staff 2, Justiciar faceguard 2, chestguard 2, legguards 2, Scythe of Vitur 1. The Hard hilt weight was lowered by 1 on 25 Jan 2022 (Mod Arcane). A player with 0 points gets a cabbage and a Message instead. | Chest; Hard; Calc |
| TB29 | Common quantity multipliers: **Entry 20%** of Normal ("80% less", raised from 15% on 1 Mar 2023); **Hard +15%** (x1.15) and a further +15% (x1.30 total) when the raid is finished inside the target time. Target times (total raid time including the gaps): 5 players 25:00, 4 players 27:00, 3 players 30:00, 2 players 35:00, 1 player 1:10:00 (the 3-5 player limits are the 4 Jun 2021 +2 minutes values). | Main; Hard; Chest; U-Entry23; Calc |
| TB30 | Tertiaries (roll per player; "scales down with the individual's performance", curve **OPEN-TB13**): elite clue scroll base 1/25 Entry, 3/25 Normal, 3.5/25 Hard; Lil' Zik 1/650 Normal, 1/500 Hard (range down to 1/6,500 and 1/5,000; maximum rate at 10 individual points; none in Entry); Hard only: Sanguine dust 1/275, Sanguine ornament kit 1/150, Holy ornament kit 1/100. **OPEN-TB14:** the Hard page says the Hard-exclusive rewards need the target time, the Chest page does not; the dust needs the time per Zik. Time requirement assumed for all three behind `tob.hardTertiariesNeedTargetTime`. | Chest; Hard; Zik |
| TB31 | Unclaimed loot: claimable at a chest just outside the Ver Sinhaza bank (7 Apr 2021); the Chest page also says unclaimed rewards "will be lost upon logging out". **OPEN-TB15:** which applies when (the brief asks to confirm). Working rule: unclaimed rewards persist at the lobby chest until claimed, behind `tob.unclaimedLootExpiresOnLogout` (default false). | Chest; Main (Changes) |

## Records

| Id | Rule | Source |
|---|---|---|
| TB32 | Kill-count varps: Normal `TOTAL_COMPLETED_THEATREOFBLOOD` = 1748, Hard `TOTAL_COMPLETED_THEATREOFBLOOD_HARD` = 3057, Entry `TOTAL_COMPLETED_THEATREOFBLOOD_STORY` = 3058 (Entry's old name). Collection-log counter varbit `COLLECTION_RAIDS_TOB_COMPLETED` 11994. Sinhaza shroud capes at 100 / 500 / 1,000 / 1,500 / 2,000 Normal+Hard completions (claimed from the Mysterious Stranger, "Change" to swap). **OPEN-TB16:** whether Entry completions count toward the capes (the wiki says "Normal/Hard"). | cache; Main; MS |
| TB33 | Messages (exact, from the brief's captured fixtures; **OPEN-TB17** until seen in game): `Your completed Theatre of Blood count is: <col=ff0000>73</col>.`; `Theatre of Blood completion time: <col=ff0000>17:00</col> (new personal best)`; `Theatre of Blood total completion time: <col=ff0000>24:40.20</col>. Personal best: 20:45.00`. Not on the wiki: the Hard / Entry variants (the KC message presumably names the mode), the per-room completion message (9 Jun 2021 added the mode name to it), the "has discovered a large ball" style boss messages beyond those quoted in room docs. The first message seems to report the challenge time and the second the overall time (Score defines both); not stated. | brief 1.3; Score |
| TB34 | Personal bests are kept per **mode and team-size bucket** (3 Jun 2021: split by group size; the old PB moved to the 5-player bucket). Buckets per Score: 1, 2, 3, 4, 5 players. **Challenge time** = time spent fighting bosses; **overall time** = from entering the raid to the finishing blow on Verzik. | U (3 Jun 2021, Main changes); Score |
| TB35 | Scoreboard in the eastern hall of Ver Sinhaza (scenery 32987, "Read"; interface `TOB_SCOREBOARD` 363, tab varbit `TOB_SCOREBOARD_TAB`) shows personal and global attempts, completions, deaths and best times per mode. The strategy table (33014, participants) and war table (33011, spectators) in the vault show per-player deaths, time per room, the MVP, challenge and overall time. | Score; Strategy table; War table |

## Open list (raid-wide)

OPEN-TB1 board rules; OPEN-TB2 Hard requirement details; OPEN-TB3 HP rounding; OPEN-TB4 wipe landing and storage; OPEN-TB5
Entry wipe scope; OPEN-TB6 Entry bandage count; OPEN-TB7 hallway tile and "encounter" bounds; OPEN-TB8 crystal price;
OPEN-TB9 crystal in a fight; OPEN-TB10 spectator cap; OPEN-TB11 quick-start eligibility; OPEN-TB12 reward formula;
OPEN-TB13 tertiary performance curve; OPEN-TB14 Hard tertiary time requirement; OPEN-TB15 unclaimed loot expiry;
OPEN-TB16 Entry and shrouds; OPEN-TB17 message strings. **17 open items.**

## Conflicts between sources (raid-wide)

1. Escape crystal price: 75,000 (Main, Strat, brief) vs 10,000 (Crystal, cache): TB19.
2. Entry solo HP "20% of original" (Maiden, Bloat, Vasilias pages) vs the 1 Mar 2023 cumulative scale (solo = 25% of the 5-player figure; cache agrees): TB11.
3. Entry wipe: restart whole raid (Entry) vs retry the room (boss pages): TB16.
4. Hard Sanguine dust and kits: needs target time (Hard, Zik) vs plain Hard drop (Main, Chest): TB30.
5. Unclaimed loot: persists at the lobby chest (7 Apr 2021) vs "lost upon logging out" (Chest): TB31.
6. Midway points: 6-13 per chest (Supply), tiers 10-13 / 8-11 / 6-9 (Strat), Hard "about 15, highest 18" (Hard): lobby-rewards LR6.
