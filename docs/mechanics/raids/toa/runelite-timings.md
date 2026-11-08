# Tombs of Amascut: ids, messages and timings encoded in RuneLite plugins

Research notes, read 2026-11-08 (date anchor for "current" is 2026-11-03: cache 2757 and RuneLite core `d62b158`).
Scope: everything the RuneLite Tombs of Amascut plugins assume about the server, so that our implementation keeps them
working and uses their timings where the wiki is silent (BRIEF: most faithful to OSRS first). **Facts only, no code
copied.** Same use as `../tob/runelite-timings.md` and `../cox/runelite-timings.md`: plugin constants are client-side
observations or heuristics, not server code. Each row says what the code *does*; the confidence column says how far to
trust it as a server rule. No `docs/mechanics/raids/toa/<room>.md` exists yet; once one does, it wins over this file
where they disagree (or the point becomes `OPEN`).

Two kinds of rows matter for the server:

- **Contract rows** (regions, widgets, varbits, varcs, scripts, chat strings, NPC/loc ids the client or a plugin keys
  on): if we do not reproduce them, a plugin or the client UI silently breaks.
- **Timing rows** (hit delays, cycles, counters): only as good as the plugin author's observation. The big finding is
  negative: **no assigned plugin encodes an attack-cycle counter for any ToA boss** (see section 11). What they encode
  is *hit resolution* (graphics delays, projectile flight, which tile counts) and *phase detection* (NPC id changes).

Every id in this file was checked against cache 2757 (except client script ids, see section 14; generated symbols in
`symbols/build/generated/symbols/tp/symbols/*.java`, plus `cache-dump config npc|loc|obj|varbit|struct|hitmark` and
`cache-dump interface`, and `cache-dump map` for regions). The "Cache" column says `ok (SYMBOL)` when the cache symbol
and the name agree with what the plugin means, and `MISMATCH` when they do not.

## 1. Sources

| Key | Plugin (Hub name) | Repository @ commit (date) | Root used in paths below | Licence |
|---|---|---|---|---|
| LD | Tombs of Amascut (`tombs-of-amascut`, Hub; LlemonDuck) | `LlemonDuck/tombs-of-amascut@831c39b9d60f40965d221fe5ac6b4d19d61c97f4` (2026-09-10) | `src/main/java/com/duckblade/osrs/toa/` | BSD-2 (c) 2022 LlemonDuck |
| MT | Toa Mistake Tracker (`toa-mistake-tracker`, Hub; Questing Pet) | `QuestingPet/ToaMistakeTracker@c73766d005af28741be13dee635723f12a41815b` (2024-02-13) | `src/main/java/com/toamistaketracker/` | BSD-2 (c) 2022 Questing Pet |
| PT | ToA Points Tracker (`toa-points-tracker`, Hub; descriptor name "ToA Points Overlay"; AbusiveTuna) | `AbusiveTuna/ToA_Points@3d4bb736d24ced4b267878609819f5cfc96ddbd1` (2024-03-25) | `src/main/java/com/tuna/toa/` | BSD-2 (c) 2022 AbusiveTuna |
| ST | Tombs of Amascut Stats (`tombs-of-amascut-stats`, Hub; Red Rookie) | `sreilly64/TombsOfAmascutStats@2dc8a8ebf350b4a09e8f98b3a58b6064f4e1abfd` (2024-05-27) | `src/main/java/rr/raids/tombsofamascutstats/` | BSD-2 (c) 2023 Red Rookie |
| PH | ToA Puzzle Helper (`toa-puzzle-helper`, Hub, **disabled at owner request**; Tiiiiiiiiiim) | `TimOosterom/TombsOfAmascutHelper@411227d8b64d729209df866ef50e422e0425ad19` (2024-01-29) | `src/main/java/com/tim/tombshelper/` | BSD-2 (c) 2022 Tiiiiiiiiiim |
| SU | Toa Supplies (`toa-supplies`, Hub; Krill) | `jerrydzhang/toa-supplies@a20d0b061c34f3dc490c10ee742b33798a580984` (2023-04-09) | `src/main/java/com/toasupplies/` | BSD-2 (c) 2023 Jerry Zhang |
| TM | TOA Life no More (`toa-modern`, Hub; PaJau) | `PaJauKat/TOA-Modern@1e450996d64cc0cc79e7362f8875da69eb6ac6c7` (2022-10-10) | `src/main/java/Toacito/` | BSD-2 (c) 2022 PaJauKat |
| RL | RuneLite core (loot tracker, chat commands, timers and buffs, item stats, Discord, gamevals) | `runelite/runelite@d62b158e83450b0708f053c753e3508c762a0803` (2026-11-03) | `runelite-client/src/main/java/net/runelite/client/plugins/`, `runelite-api/src/main/java/net/runelite/api/gameval/` | BSD-2 |
| WK | OSRS Wiki pages and `Module:Tombs_of_Amascut_loot` (cross-check only, not a plugin) | `/tmp/claude-0/ref/wiki-toa/` | n/a | CC BY-NC-SA (read for facts only) |

All seven plugin clones are under `/tmp/claude-0/ref/plugins-toa/`. Paths below are relative to the root in the table.
Citations are `file:line` where a line was recorded, otherwise file only (grep the quoted constant).

**Staleness.** Only LD is current (2026-09). MT, PT, ST are 2024; SU is 2023; TM is 2022. A 2024 plugin can predate
balance changes; rows from those plugins that disagree with LD or the wiki are flagged in section 12.

**Lineage.** I found no shared code between the seven plugins, so "two plugins agree" is real corroboration (confidence
High) unless both merely import the same RuneLite gameval constant (then it only proves the constant). **Citation
shorthand:** a bare `:NN` after `ST` or `PT` is a line of that plugin's main class (`TombsOfAmascutStatsPlugin.java`,
`ToAPointsPlugin.java`).

**Confidence.** High = two independent plugins agree, or the plugin agrees with the cache **and** the wiki; Med = one
plugin, consistent with cache names/ids; Low = UI heuristic, unexplained magic number, a comment saying "hack", or
known-stale code.

**Tick idiom (read this before using any delay).** Plugin code reads `client.getTickCount()` in event handlers
(AnimationChanged, NpcSpawned, GraphicsObjectCreated, ProjectileMoved, HitsplatApplied, ChatMessage), most of which the
client fires while it processes the server update of tick N, before that tick's `GameTick` (same idiom as the ToB notes). "Activation tick = tick + delay" means
the hit is expected on the game tick that is `delay` after the one in which the client first saw the event. A client
cycle is 20 ms; `CYCLES_PER_GAME_TICK = 600 / 20 = 30` (MT `detector/BaseMistakeDetector.java:28`). Graphics-object delay
is `(startCycle - gameCycle) / 30` ticks; projectile flight is `remainingCycles / 30` ticks.

**Hit tile rule (applies to every MT detector).** MT tests the raider's tile **at the end of the previous tick**
(`previousWorldLocation`, set after each tick's detection in `ToaMistakeTrackerPlugin.java:150-153`) against the hit
tiles that are active **this** tick. This is MT's assumption (the player's position at event time already includes this
tick's movement, so MT uses the older one). It is consistent with area damage being evaluated against pre-move
positions; if our hit processing uses post-move positions, MT will show false positives and negatives.

## 2. Regions (the client and every plugin identify rooms by template region)

LD, MT, PT, ST and PH call `WorldPoint.fromLocalInstance(client, localLocation).getRegionID()`: the region id of the
**template chunk** under the player's instance tile, not the instance's own region (TM instead tests `getMapRegions()` for
14160). So our instance builder must (a) copy ToA rooms
from these template squares, and (b) send the dynamic-region (instance) chunk table so each chunk maps back to its
template zone. A room that is not an unrotated translation of its template breaks MT, which converts template
region-local tiles to scene tiles by a fixed offset (MT `detector/boss/BabaDetector.java` and
`detector/puzzle/CrondisPuzzleDetector.java`, `computeGapTiles` / `computeTiles`). ToA rooms are never rotated in these
plugins' assumptions.

| RT | Region (square x,z) | Room | Plugins keying on it | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT1 | 13454 (52,142) | Lobby outside the raid (party board, invocations); LD `inLobby`; MT `RAID_LOBBY_OUTSIDE` = "not in raid"; PT `TOA_LOBBY` (= not in raid); ST lobby; RL Discord names it `RAIDS_JALTEVAS_PYRAMID` | LD `util/RaidStateTracker.java:23`; MT `RaidRoom.java`; PT `ToARegion.java`; ST `:90`; RL `plugins/discord/DiscordGameEventType.java:340` | ok: map square exists, 319 locs | High |
| RT2 | 14160 (55,80) | Nexus (raid lobby inside; helpful-spirit bundle choice; path doors) | LD `RaidRoom.NEXUS`; MT `RAID_LOBBY_INSIDE`; PT `MAIN_AREA`; ST; TM `Toacito/lobby/Lobby.java` (via `getMapRegions`); RL Discord | ok, 4056 locs | High |
| RT3 | 14162 (55,82) | Path of Scabaras puzzle room | LD, MT, PT, ST, PH (`TOA_PUZZLE_KEPHRI`); RL Discord | ok, 2723 locs | High |
| RT4 | 14164 (55,84) | Kephri | same | ok, 3071 locs | High |
| RT5 | 15698 (61,82) | Path of Crondis puzzle room | LD, MT, PT, ST, PH (`TOA_PUZZLE_ZEBAK`) | ok, 2972 locs | High |
| RT6 | 15700 (61,84) | Zebak | same | ok, 1490 locs | High |
| RT7 | 14674 (57,82) | Path of Het puzzle room | LD, MT, PT, ST, PH (`TOA_PUZZLE_AKKHA`), TM (`AKKHA_PATH_REGION`) | ok, 1509 locs | High |
| RT8 | 14676 (57,84) | Akkha | same | ok, 4123 locs | High |
| RT9 | 15186 (59,82) | Path of Apmeken puzzle room | LD, MT, PT (`PUZZLE_MONKEY`), ST, PH (`TOA_PUZZLE_BABA`) | ok, 1037 locs | High |
| RT10 | 15188 (59,84) | Ba-Ba | same; TM (`BABA_REGION`) | ok, 3165 locs | High |
| RT11 | 15184 (59,80) | Wardens phases 1 and 2 (obelisk, P2 wardens, core) | LD (`WARDENS`), MT (`WARDENS_P1_P2`), PT, ST (`WARDENS_OBELISK_ROOM`), TM (`WARDEN_P12_REGION`) | ok, 9240 locs | High |
| RT12 | 15696 (61,80) | Wardens phase 3 and the enrage ("P4" in MT/LD text) | LD (second `WARDENS` region), MT (`WARDENS_P3`), PT (`BOSS_WARDEN_FINAL`), ST | ok, 1375 locs | High |
| RT13 | 14672 (57,80) | Tomb / loot room (sarcophagus chests); RL Discord names it `RAIDS_OSMUMTENS_BURIAL_CHAMBER` | LD `RaidRoom.TOMB`; PT `CHEST_ROOM`; ST `TOA_LOOT_ROOM` | ok, 8421 locs | High |
| RT14 | 13392 (52,80) | "limbo" (comment only, no logic) | MT `RaidRoom.java` | ok, 126 locs | Low |
| RT15 | 13906 (54,82), 15444 (60,84), 15953/15954/15955 (62,81..83) | Unexplained: listed as "all of them so the panel doesn't continuously hide during the raid" | PH `TombsHelperPlugin.java` | ok, but 15953-15955 have 0 locs; 13906 156 locs, 15444 131 locs | Low |

Room-to-path mapping used by MT's death attribution (`detector/death/DeathDetector.java`): Het+Akkha -> "Path of Het";
Crondis+Zebak -> Crondis; Scabaras+Kephri -> Scabaras; Apmeken+Ba-Ba -> Apmeken; both Wardens regions -> Wardens.
Important naming trap: **ToA paths and their bosses are named by different gods in the code**: LD's `kephriPathLevel`
= Scabaras, `akkhaPathLevel` = Het, `babaPathLevel` = Apmeken, `zebakPathLevel` = Crondis; PH calls the Het puzzle room
"AKKHA", the Apmeken puzzle room "BABA", the Scabaras puzzle room "KEPHRI", the Crondis puzzle room "ZEBAK".

## 3. Raid state, HUD, party and levels (the client contract)

### 3.1 "Am I in a raid" and the HUD

| RT | Value | Meaning / trigger | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT16 | interface 481 child 40 visible, or region in the table above (except 13454) | LD treats the player as in the raid while either holds; `inRaid` stays true for **3 ticks** after both stop (`raidLeaveTicks = 3`) "to alleviate unexpected delays between rooms" | LD `util/RaidStateTracker.java:24-25,56-62` | ok: `481:40` is a type-0 container (`TOA_HUD`, RL `ToaHud.RAID_STATE`) | High |
| RT17 | `481:42` text | Raid level text. Cache default `"Level: 0"`. PT strips non-digits and parses (`replaceAll("[^0-9]","")`) | PT `ToAPointsPlugin.java` | ok: type-4 text, `"Level: 0"` (RL `ToaHud.RAID_LEVEL_TXT`) | Med |
| RT18 | `481:45` text | Current room's **path level** as a bare integer, parsed with `Integer.parseInt` (no markup allowed) | LD `features/PathLevelTracker.java:97-104` | ok: type-4 text `"0"` (RL `PATHS_LEVEL`) | High |
| RT19 | `481:49` Scabaras, `481:51` Het, `481:53` Apmeken, `481:55` Crondis | Per-path levels shown only in the Nexus; same bare-integer parse. Order in the HUD is Scabaras, Het, Apmeken, Crondis | LD `PathLevelTracker.java:75-92` | ok: all type-4 `"0"` (RL `NEXUS_SCABARAS/AKKHA/APMEKAN/CRONDIS_LEVEL`) | High |
| RT20 | `481:46` text, script 6581 | Raid timer text (cache default `"00:00"`). LD appends `" / MM:00"` (or `" / MM:00.00"` in precise mode) after script 6581 runs, and widens two parent containers to 200 px | LD `features/timetracking/TargetTimeManager.java:30-31,102-124` | ok: type-4 `"00:00"`. Script 6581 not named in our symbols (no script table) | Med |
| RT21 | `481:4` container, script 6579 | The party HP orbs. LD hides it after script 6579 (`BUILD_ORBS_WIDGET_SCRIPT_ID`) and shrinks the parent to 95 px | LD `features/hporbs/HpOrbManager.java:21-22,76` | ok: type-0 | Med |
| RT22 | script 6585 | `TOA_HUD_INIT_STATUS_NAMES`: MT loads the raider list when it finishes; the names are not in the varcs until "a few ticks" after entering the Nexus | MT `RaidState.java:39,111` | not verifiable (no script names in symbols) | Med |
| RT23 | varbit 11866 (varp 2856 bit 15) | `OPTION_PRECISE_TIMING`: 1 = timers with decimals. LD `TimerMode`; ST formats times with one decimal digit when set | LD `util/TimerMode.java`; ST `:65,232` | ok (`OPTION_PRECISE_TIMING`) | High |
| RT24 | **Raid / room timer start and stop, as the plugins see them.** No plugin reads the HUD timer's state. Raid start = chat line `You enter the Tombs of Amascut` (LD points reset). Room clock start = `Challenge started: <room>.`; stop = `Challenge complete: <room>.` (ST). Elapsed time is computed from `client.getTickCount()` differences: ST shows `ticks x 600 ms` as `M:SS`, or `M:SS.t` (one tenths digit) when varbit 11866 is set. Splits taken from the chat line itself (LD) use the server's own `Total:` / `completion time:` text | The server owns the timer value and the printed times; the plugins only re-display them. Our printed times must therefore be `ticks x 0.6 s` | LD `SplitsTracker.java:32,34`; ST `:66,1394-1412` | n/a | Med |

### 3.2 Party, HP orbs, player count

| RT | Value | Meaning | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT25 | varbits 14346..14353 = `TOA_CLIENT_P0..P7` (5 bits each: varp 3588 bits 2-6, 7-11, 12-16, 17-21, 22-26, 27-31; varp 3589 bits 0-4, 5-9) | Slot i is **absent when 0**; otherwise `value - 1` is the HP bucket out of **26** (`hpFactor = max(v-1,0)/26`). A value above the 26 scale is "reported over 100% for death" and LD greys the bar | LD `features/hporbs/HealthBarsOverlay.java:76,86,118` | ok: bit ranges as listed (`cache-dump config varbit`) | High |
| RT26 | varc strings 1099..1106 = `TOA_CLIENT_NAME0..NAME7` | Player names by slot; slot `i` pairs with varbit `14346+i`. Solo raid = varc 1100 empty | LD `HealthBarsOverlay.java:73-85`; MT `RaidState.java:40-41,161-163` | ok (`TOA_CLIENT_NAME0..7`) | High |
| RT27 | varbits `TOA_CLIENT_P1..P7` != 0 (+1 for self) | LD player count = `1 + count(P1..P7 != 0)`. RL (loot tracker, chat commands) counts `min(P0..P7, 1)` summed | LD `RaidStateTracker.java:96-105`; RL `loottracker/LootTrackerPlugin.java:869-880`, `chatcommands/ChatCommandsPlugin.java:2956-2967` | ok. The two formulas agree only if P0 is always non-zero while the client is in the raid (it is the viewer's own slot in LD's reading) | Med |
| RT28 | player names from varcs matched against `client.getPlayers()` | MT retries each tick until every named raider is a loaded player; room change events are only posted after that | MT `RaidState.java` | n/a | Med |

### 3.3 Raid level, mode, path levels

| RT | Value | Meaning | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT29 | varbit 14380 `TOA_CLIENT_RAID_LEVEL` = varp 3592 bits 12-22 (11 bits, 0-2047) | The raid level. Read by LD (points, unique chance, Akkha shadow HP, cursed phalanx check `>= 500`) and the RL loot tracker (metadata). | LD `PointsTracker.java`, `AkkhaShadowHealth.java:114`; RL `LootTrackerPlugin.java:870` | ok | High |
| RT30 | varbits 14376 Crondis, 14377 Scabaras, 14378 Het, 14379 Apmeken (3 bits each: varp 3592 bits 0-2, 3-5, 6-8, 9-11); 14381 `CURRENT_PATH` (bits 23-25); 14495 `RAID_LEVEL_STOP_AUTO_UPDATING` (bit 26) | These varbits exist in the cache. **No assigned plugin reads them**: LD reads the HUD text widgets instead. The cache gives the bit layout the server must populate for the client scripts | cache only | ok | High (cache) |
| RT31 | The packed varps are named `TOA_TEMP_TRANSMIT_1..7` (varps 3588..3594; 3588 = P0..P5 HP + party status, 3589 = P6/P7 + chest keys + party slot, 3590 = energy flag + primary flags + chests + sarcophagus, 3591 = damage done / taken current, 3592 = path levels + raid level + current path), `TOA_PERM_TRANSMIT_1..4` (3585, 3586, 3587, 3652: scoreboard / damage done / midraid-loot timer / damage taken) and `TOA_PERSONAL_CONTRIBUTION` (3606) | The server writes these "transmit" varps to the client; every varbit in sections 3 and 8 is a bit range inside one of them | cache (`cache-dump config varbit`, `varp` symbols) | ok | High (cache) |
| RT32 | Entry 0-149, Normal 150-299, Expert 300+ | LD `RaidMode` colours the party board by these ranges | LD `util/RaidMode.java` | WK `Tombs_of_Amascut.txt`: Normal 150-299, Expert 300-600 (agrees); Entry 0-149 is the complement | High |
| RT33 | `774:73` text `"Raid Level: N <col=ffffff>(M)</col>"`; `774:74` text `"Mode: Entry"` (cache defaults `"Raid Level: 0 <col=ffffff>(0)</col>"`) | LD parses `N` as `text.split(":")[1].split("<")[0]` when not in a raid (party board) | LD `features/pointstracker/PurpleWeightingManager.java` | ok: type-4 | High |
| RT34 | wiki: +2% HP/defence/accuracy/damage per 5 levels, damage capped +150% | LD's Akkha-shadow HP model: `hp += hp * (4 * raidLevel / 10) / 100` (integer, 0.4% per level) | LD `AkkhaShadowHealth.java:114-116` | WK `Tombs_of_Amascut.txt:90` agrees | High |
| RT35 | first path level +8%, each further +5% (up to 6 levels); every 2 path levels change a boss's mechanics, capped at 4 | LD shadow HP: `pathLevelFactor = 3 + 5 * level` percent (level 1 = 8) | LD `AkkhaShadowHealth.java:121`; WK `Tombs_of_Amascut.txt:190` | agrees | High |
| RT36 | 2nd and 3rd member add 90% of base HP each, 4th+ add 60% each | LD shadow HP: size 2 -> +90%, 3 -> +180%, `n >= 4` -> +180% + 60% per member beyond 3 (tenths) | LD `AkkhaShadowHealth.java:126-133`; WK `Tombs_of_Amascut.txt:53` | agrees | High |
| RT37 | base 70, rounding to 5 (`hp > 100`) or 10 (`hp > 300`) after scaling | Akkha's Shadow max HP. The HP comes from a health-bar ratio against this computed max, because "NPCManager does not have this information" | LD `AkkhaShadowHealth.java:31,112-142`; WK `Akkhas_Shadow.txt` `hitpoints = 70` | NPC `AKKHA_SHADOW` 11797 ok | High |

### 3.4 Invocations (party board)

| RT | Value | Meaning | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT38 | interface 774 (`TOA_PARTYDETAILS`); child 3 header container (its child 1 is the title text `"Party of N"`); child 51 scrollbar; child 52 invocation list; child 57 right-hand container; child 58 info container; child 87 loot-potential container | Layout LD (presets, screenshot, purple weighting) depends on | LD `features/invocationpresets/InvocationPresetsManager.java:47-51,346`; `InvocationScreenshot.java:67-75`; `PurpleWeightingPartyBoardManager.java:72` | ok: all present; `774:87` is `RAID_LOOT_POTENTIAL` | High |
| RT39 | invocation `i` (0-based, enum order) occupies **three consecutive children** of `774:52`, starting at index `3 * i` | The 44 invocations LD knows are in the same order as cache structs 417..2971 (param 1159 = 1..44), with the same raid levels: verified by script (0 diffs for all 44) | LD `util/Invocation.java` (`getWidgetIx = ordinal * 3`) | ok: names and levels match for 44/44 | High |
| RT40 | child `3*i` has `getOnOpListener()[3] == 1` when the invocation is active | The invocation toggle state is carried as arg 3 of the on-op listener of the first child (client script data), not as a varbit. Our interface scripts must pass `1`/`0` there | LD `InvocationPresetsManager.java:287-299` | n/a | High |
| RT41 | scripts 6729 (build party interface), 6732 (toggle reward panel), 6617 (party widget; LD screenshot), 6770 (init loot rewards; LD purple weighting), 6722 (party-details add member; LD ignore list), 6601 (party list add line) | LD hooks `ScriptPostFired` of these to redraw its overlays; if we replace these scripts, ids must be preserved | LD `InvocationPresetsManager.java`; `InvocationScreenshot.java:75`; `PurpleWeightingPartyBoardManager.java:27`; `IgnoreListPartyBoardHighlighter.java:26-27` | not verifiable (no script names) | Med |
| RT42 | varc 1086 `TOA_INVOCATIONS_LAYOUT_REWARDS_HIDDEN` | LD screenshot reads it to know whether the reward panel is open | LD `InvocationScreenshot.java:73` | ok | Med |
| RT43 | structs 5892 / 5893 "Blazing Tombs I / II": raid level +200 each, requires raid level 300, description mentions League relics / Event rewards (struct params 1159 = 45 / 46, 1863 = 300) | **Not in any plugin**: LD's `Invocation` enum stops at `INSANITY` (44 entries). Cache 2757 has 46 invocation structs. WK `Invocations.txt:428-445` lists them as Leagues / Grid Master only | cache; WK | ok: present in cache | High (cache) |
| RT44 | varps 3680..3694 `TOA_INVOCATIONS_PRESET_1A..5C` (15 varps) and varbit 14541 `TOA_PRESET_SELECTED` | In-game preset storage. LD presets are client-side config only and do not use these | cache only | ok | Low |

### 3.5 Chat messages the plugins parse (exact strings)

All are `GAMEMESSAGE`. "Prefix" = `startsWith`, "equals" = exact after `Text.removeTags`.

| RT | String / regex | Consumer and effect | Source | Conf. |
|---|---|---|---|---|
| RT45 | prefix `You enter the Tombs of Amascut` | LD PointsTracker `reset()` (new raid); LD TargetTimeManager stops its history scan on **equals** `You enter the Tombs of Amascut` (no target time). So, when a raid has no target time, the message is exactly that text; the `Overall time to beat:` line is a separate message | LD `PointsTracker.java:67`; `TargetTimeManager.java:33,60` | Med |
| RT46 | regex `Overall time to beat: (\d\d):\d\d(?:\.\d\d)?` | The target minutes (group 1) shown in the raid timer. Seconds in the message are ignored | LD `TargetTimeManager.java:34-35` | Med |
| RT47 | `Challenge started: Ba-Ba.`, `Kephri.`, `Akkha.`, `Zebak.`, `The Wardens.`; for puzzles `Challenge started: Path of Het.` (equals) | ST starts a room clock on each (regex `find`; constants at `:108-112`); LD's beam timer starts on the Het one. The boss rooms use the boss name (`Ba-Ba.`, `Kephri.`, `Akkha.`, `Zebak.`, `The Wardens.`); the **Het puzzle uses "Path of Het."** (other paths presumably "Path of Crondis/Scabaras/Apmeken.": only Het is observed) | ST `:108-112`; LD `BeamTimerTracker.java:30` | High (bosses) / Med (puzzles) |
| RT48 | `Challenge complete: Ba-Ba.` / `Kephri.` / `Akkha.` / `Zebak.` / `The Wardens.`; puzzles `Challenge complete: Path of Het.` | ST ends the room; LD Kephri swarm saver (prefix `Challenge complete: Kephri.`); LD beam timer (equals); LD DepositPickaxe (prefix `Challenge complete`) | ST `:113-116,121`; LD `SwarmerOverlay.java:48` | High |
| RT49 | regex `Challenge complete: (?:Path of )?([A-Za-z-]+).*Total:.*?([0-9]+:[.0-9]+).*` | LD splits: group 1 is the room name (`Crondis`, `Zebak`, `Scabaras`, `Kephri`, `Apmeken`, `Ba-Ba`, `Het`, `Akkha`), group 2 the running **Total** time. So puzzle completions read `Challenge complete: Path of X. ... Total: <time>` and boss completions `Challenge complete: <Boss>. ... Total: <time>` | LD `SplitsTracker.java:32` | High |
| RT50 | regex `Challenge complete: The (Wardens).*?completion time:.*?([0-9]+:[.0-9]+).*`. Real shape (RL tests): `Challenge complete: The Wardens. Duration: <col=ef1020>8:30</col><br>Tombs of Amascut[: Expert Mode / : Entry Mode] challenge completion time: <col=ef1020>8:31</col> (new personal best)` or `... . Personal best: 8:31`, then `Tombs of Amascut total completion time: <col=ef1020>0:01</col> (new personal best)`, then `Your completed Tombs of Amascut[: Expert Mode / : Entry Mode] count is: <col=ff0000>N</col>.` | LD splits (group 2 = the **challenge completion time**, not the Duration); RL chat commands PB + KC. **Order matters**: RL handles "PB then KC" (the PB line arrives before the KC line); team size comes from the `TOA_CLIENT_P0..P7` varbits at the moment the KC line is read | LD `SplitsTracker.java:34`; RL `ChatCommandsPluginTest.java:1359-1427`, `ChatCommandsPlugin.java:109-114,455-490` | High |
| RT51 | prefix `Your party failed to complete the challenge` (LD points and the Kephri saver use the shorter prefix `Your party failed to complete`) | Used by every puzzle solver, the Apmeken wave overlay, the Kephri swarm saver and the points tracker to reset the room: a **wipe** | LD `PointsTracker.java:70`, `SwarmerOverlay.java:49`, `ApmekenWaveOverlay.java:144` | High |
| RT52 | suffix `violently!` | Het solver resets on it too (some second fail/reset line) | LD `HetSolver.java` | Low |
| RT53 | prefix `You have died` | LD points: `-max(20% of total, 1000)` per death | LD `PointsTracker.java:69,261-263` | High |
| RT54 | contains `Total deaths` | Raid-summary line; LD cursed-phalanx detector sets "no longer eligible for the kit" when it appears | LD `features/tomb/CursedPhalanxDetector.java` | Med |
| RT55 | equals `The fumes filling the room suddenly ignite!`, `Damaged roof supports cause some debris to fall on you!`, `Your group is overwhelmed by Amascut's corruption!` | MT team mistakes (vent / pillar / corruption) in the Apmeken puzzle | MT `ApmekenPuzzleDetector.java` | High |
| RT56 | regex `The number @.*@(\d+) has been hastily chipped into the stone.` (applied **after** `removeTags`; the pattern expects literal `@` characters around the number) | Scabaras addition puzzle target number | LD `AdditionPuzzleSolver.java` | Med |
| RT57 | `As Elidinis' Warden falls, Tumeken's Warden powers up!`; `As Tumeken's Warden falls, Elidinis' Warden powers up!` (obelisk phase done); `Elidinis' Warden uses the last of its power to restore Tumeken's Warden!`; `Tumeken's Warden uses the last of its power to restore Elidinis' Warden!` (P2 done) | ST phase boundaries: obelisk -> P2 and P2 -> P3, and which warden is fought in P3 | ST `:117-120` | Med |
| RT58 | `You quickly apply the dressing to your wounds.` (silk dressing); `You crack the crystal in your hand.` (blessed crystal scarab); `You drink some of the potion, reducing the energy cost of your special attacks.</col>` (liquid adrenaline) | RL timers-and-buffs start these timers | RL `timersandbuffs/TimersAndBuffsPlugin.java:114-116,1105-1121` | High |

## 4. Nexus, helpful spirit, mid-raid supplies, Osmumten

| RT | Value | Meaning / trigger | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT59 | locs 46157 Scabaras, 46166 Het, 46163 Crondis, 46160 Apmeken (`..._DOOR_CLOSED`), 46168 Wardens door (`..._DOOR_OPEN`) | TM sets a flag when a game object with that id **spawns** in region 14160. LD's quick-proceed uses the *open* variants 46155 Scabaras, 46164 Het, 46161 Crondis, 46158 Apmeken. So the Nexus swaps each path door between a closed and an open loc | TM `Lobby.java:60-68`; LD `QuickProceedSwaps.java:42-55` | ok: `TOA_NEXUS_*_DOOR_CLOSED` / `_OPEN` / `TOA_NEXUS_*_DOOR`; names "Path of Scabaras / Het / Crondis / Apmeken", Wardens door "Entry" | Med |
| RT60 | TM: `closed doors == 2` -> first bundle screen, `== 4` -> second | TM hides the Life / Chaos / Power "Choose" buttons per config at those two moments. The count is of the **four path doors**, not the Wardens door | TM `Lobby.java:75-98` | n/a | Med |
| RT61 | interface 777 (`TOA_MIDRAID_LOOT`); children 6 / 9 / 12 = "Choose" buttons of bundles 1 / 2 / 3; LD maps LIFE = 777:6, CHAOS = 777:9, POWER = 777:12 | LD outlines the configured bundle and deprioritises the others (menu type `CC_OP`) | LD `features/nexus/BundleType.java`, `HelpfulSpiritHighlighter.java`; TM `Lobby.java` | ok: `777:6`, `777:9`, `777:12` each have action `0="Choose"` | High |
| RT62 | first helpful-spirit pass when the number of completed challenge names is **<= 4**, else second | LD counts names from `Challenge complete: <name>.` (text up to the first `.`), so each finished path contributes **two** names (puzzle "Path of X" and its boss): 2 paths = 4 entries, 4 paths = 8 | LD `HelpfulSpiritHighlighter.java:162`; `util/RaidCompletionTracker.java` | n/a | Med |
| RT63 | NPC 11694 "Helpful Spirit", op1 "Claim" | The vendor that opens interface 777 | cache (not used by plugins directly) | ok (`TOA_MIDRAIDLOOT_TRADER`) | Low |
| RT64 | NPCs 11689 (op3 "Proceed", post-demi-boss), 11690 (op3 "Begin", pre-warden), 11693 (op3 "Leave", loot room) | LD makes the "Proceed" / "Begin" / "Leave" op the left-click default (`Talk-to` option checked by name) | LD `QuickProceedSwaps.java:35-38,96` | ok (`TOA_OSMUMTEN_VIS`, `_WARDENS_VIS`, `_VAULT_VIS_2`) | Med |
| RT65 | locs: 45505 Kephri / 45506 Zebak / 45866 Akkha / 45754 Ba-Ba "Teleport crystal"; 45131 Het exit, 45337 Scabaras exit, 45397 Crondis exit, 45500 Apmeken exit (all "Entry"); 45135 "Barrier" (option "Pass") | LD swaps their default menu entry to Enter / Use / Pass | LD `QuickProceedSwaps.java:42-55,104-112` | ok (`KEPHRI_TELEPORT` etc., `TOA_*_CONTINUE`, `TOA_PATH_BARRIER`) | Med |
| RT66 | inventory 810 `TOA_MIDRAIDLOOT_BAG` (also 807-809 bundles, 811 chests); interface 778 `TOA_MIDRAIDLOOT_BAG`; item 27314 "Supplies" | SU recounts potion doses whenever interface 778 loads or container 810 changes | SU `ToaSuppliesPlugin.java:24,56` | ok (`Invs.TOA_MIDRAIDLOOT_BAG` 810; item 27314 "Supplies") | High |
| RT67 | menu options `Withdraw 1`, `Withdraw All`, `Resupply` on item 27314; `Use` of a potion on the Supplies item | SU snapshots the inventory, waits **1 game tick** (`waitTicks = 1`), then diffs the inventory to infer how many doses left the bag. A `Use`-on adds doses by charge count | SU `ToaSuppliesPlugin.java:78,111-118` | n/a | Med |
| RT68 | Nectar 27315/27317/27319/27321 (4..1 doses), Tears of Elidinis 27327/27329/27331/27333, Ambrosia 27347 (2) / 27349 (1); overlay tracks 27315 / 27327 / 27347 with max 4 / 4 / 2 doses | The bag stores doses; SU keys by the full-dose item | SU `RaidsPotions.java`, `ToaSuppliesOverlay.java:20-21` | ok: names Nectar (n), Tears of Elidinis (n), Ambrosia (n). **Silk dressing 27323/27325, Blessed crystal scarab 27335/27337, Liquid adrenaline 27339/27341, Smelling salts 27343/27345 are in the bag too but SU ignores them** | High |
| RT69 | RL: liquid adrenaline 150 s from the chat line; silk dressing 100 ticks; blessed crystal scarab 40 ticks; smelling salts = varbit 14344 value x **25 ticks** (10-bit varbit, varp 3587 bits 2-11); liquid adrenaline timer removed when varbit 14361 returns to 0 (1 bit, varp 3590 bit 1) | Buff timers shown by core; the varbits are the authority | RL `timersandbuffs/GameTimer.java:95-99`; `TimersAndBuffsPlugin.java:582-596` | ok (`TOA_MIDRAIDLOOT_STATS_TIMER`, `TOA_MIDRAIDLOOT_ENERGY_ACTIVE`) | Med |
| RT70 | LD: refuses to re-use Smelling salts / Liquid adrenaline within a client-side cooldown (default 15 s each; UI max 480 s / 150 s). Detects use by the energy varbit rising or the stats varbit rising | Client convenience only | LD `SmellingSaltsCooldown.java`, `AdrenalineCooldown.java` | n/a | Low |
| RT71 | RL item-stat preview: Honey locust +20 HP and prayer-pot-style prayer restore; Silk dressing heals 5 now + 5 x 20 over time; Blessed crystal scarab restores 8 + 8 x 9 prayer; Nectar +15% +3 HP and `perc(0.2, -5)` on each combat stat; Smelling salts +`perc(.16, 11)` on each combat stat; Tears of Elidinis prayer `perc(.25, 10)` and `perc(.25, 3)` restore on each combat stat; Ambrosia HP `perc(.25, 2 + missing)`, prayer `perc(.2, 5 + missing)`, run energy +100 | A client-side **preview** of the effect (RL ItemStatChanges). Not cross-checked with the wiki here | RL `itemstats/ItemStatChanges.java:353-363`, `potions/Ambrosia.java` | ok (`TOA_SUPPLY_*`, `TOA_HONEY_LOCUST` 27351) | Low |
| RT72 | items 27315-27351 as listed in the cache: HEAL 27315..27321 (Nectar), HEAL_OVERTIME 27323/27325 (Silk dressing), PRAYER 27327..27333 (Tears), PRAYER_OVERTIME 27335/27337 (Blessed crystal scarab), ENERGY 27339/27341 (Liquid adrenaline), STATS 27343/27345 (Smelling salts), PANICHEAL 27347/27349 (Ambrosia) | Identity map for the table above | cache | ok | High (cache) |

## 5. Paths

### 5.1 Path of Crondis (puzzle) and Zebak

| RT | Value | Meaning / trigger | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT73 | NPCs 11700..11704 "Palm of Resourcefulness" (size 5; ops "Water" on 11700-11703, none on 11704) | MT matches by **name** `Palm of Resourcefulness` after `Text.removeTags` (the cache name has `<col=00ffff>` tags) | MT `CrondisPuzzleDetector.java:108` | ok (`TOA_CRONDIS_TREE_1..5`) | High |
| RT74 | player anim 827 (`HUMAN_PICKUPFLOOR`) | While the anim plays on a **waterfall tile** the raider "has water"; on a **palm tile** the raider is watering | MT `CrondisPuzzleDetector.java:109,205` | ok (name is generic; animation reused) | Med |
| RT75 | template region-local tiles (region 15698): waterfalls (23,7) (24,7) (26,7) (27,7) (37,7) (38,7) (40,7) (41,7) (23,57) (24,57) (26,57) (27,57) (37,57) (38,57) (40,57) (41,57); palm-watering ring: (30..34,29), (35,30..34), (30..34,35), (29,30..34) | 16 pickup tiles (x 23, 24, 26, 27 and 37, 38, 40, 41 on rows y = 7 and y = 57) and the four 5-tile edges of a square around the palm centre (32,32) | MT `CrondisPuzzleDetector.java` | n/a | Med |
| RT76 | palm hitsplat type 11 (`CYAN_UP`) with amount < 100 | Counts one "low water" event; the watering raider who **lost water to damage** (damage hitsplat 16/17 while carrying) is blamed ("This jug feels a little light...") | MT `CrondisPuzzleDetector.java:110-112,195-197` | ok: hitmark 11 and 15 exist | Low |
| RT77 | LD estimate: +400 to total points when leaving the Crondis puzzle (the wiki module's comment: 200 health per person, 2 points per health); PT: palm hits not "mine" -> `amount / players * 2`, room cap 10,000 (solo 20,000) | Hitsplat types 53 / 55 / 11 are "not mine, not others" | LD `PointsTracker.java:230-235`; PT `ToAPointsPlugin.java:100-118` | WK module: "Crondis - palm watering, 400 x team size" (agrees with LD) | High (LD) / Low (PT, see section 12) |

Zebak (region 15700):

| RT | Value | Meaning / trigger | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT78 | NPCs 11730 `TOA_ZEBAK`, 11732 `TOA_ZEBAK_ENRAGED`, 11733 `TOA_ZEBAK_DEAD`, 10680 `TOA_ZEBAK_TRANSMOG` (all "Zebak"); tail 11731 | LD points factor **1.5** on 11730, 11732, 11733, 10680; ST groups damage by the name "Zebak" | LD `PointsTracker.java:99-102`; PT `ToANpc.java` (11730, 11732) | ok: names "Zebak" size 9 (10680 size 5) | High |
| RT79 | locs 45570..45576 (`TOA_ZEBAK_VOMIT01..07`, no name) | Acid tiles. MT counts a mistake when the raider stood on one at the end of the previous tick; a tile **does not hurt on the tick it spawns**, so detection starts the tick after (tile is added to the active set after the next tick). Removed on despawn | MT `ZebakDetector.java:65` | ok | Med |
| RT80 | graphics object 2184 + Zebak anim 9628 | Scream/earthquake: hit tiles are the 2184 graphic tiles, counted only while Zebak's last animation was 9628 | MT `ZebakDetector.java:67-68` | ok: gfx `ZEBAK_ROAR_WAVE_DUST`, seq `NPC_ZEBAK01_ATTACK_ROAR` | Med |
| RT81 | NPC named `Wave` (11738); `Bloody Wave` is 11739 | A raider is "hit" when a Wave NPC exists and the raider moved **more than 2 tiles in Y in one tick** (waves push at least 3), excluding raiders who were swimming the tick before (pose 772 `HUMAN_SWIM` / 773 `HUMAN_SWIM_READY`: jumping out of the water can move 3 tiles) | MT `ZebakDetector.java:64,70` | ok for 11738; **MT's equality `Wave` does not match the cache name "Bloody Wave" (11739)** | Med |
| RT82 | NPC `Blood Cloud` (11742 size 2, 11743 size 1); `HEAL` hitsplat (type 6) on it | The cloud's tile is a mistake tile for the following tick evaluation | MT `ZebakDetector.java:71` | ok; hitmark 6 exists | Med |
| RT83 | anim 9620 (`NPC_ZEBAK01_ATTACK_MELEE`) with hit delay **2 ticks** | Declared but **unused** in MT's logic | MT `ZebakDetector.java:61-62` | ok | Low |
| RT84 | room-point cap 10,000 in the Zebak room (`MAX_ROOM_POINTS / 2`); item 27219 `TOA_ZEBAK_FANG` spawn = MVP of the room | See section 7 | LD `PointsTracker.java:473` | ok | High |

### 5.2 Path of Scabaras (puzzle) and Kephri

| RT | Value | Meaning / trigger | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT85 | ground loc 45344 `LIGHTSOUT_TILE_OFF` ("Pressure plate") at a start tile; lit light = game object 45384 (`TOA_SCABARAS_FX01`) on a tile. Four candidate start tiles in **scene coordinates**: (36,56), (36,44), (53,56), (53,44) | LD finds the board by testing which start tile has the ground object. It reads 8 lights (the 3x3 grid minus the centre) at 2-tile spacing from the start (`x + 2*col`, `y - 2*row`) | LD `LightPuzzleSolver.java:39-45,136-160` | ok (`TOA_SCABARAS_LIGHTSOUT_TILE_OFF`, `TOA_SCABARAS_FX01`) | Med |
| RT86 | XOR table, one 8-bit row per tile (bit i = tile i flips): `01110101`, `10111010`, `11001101`, `11001110`, `01110011`, `10110011`, `01011101`, `10101110`. Solution = XOR of the rows of every tile that is **off**; bits set in the result are the tiles to press | The lights puzzle is linear over GF(2); the table is the neighbour pattern of each press. Re-solved whenever an FX01 object spawns or despawns | LD `LightPuzzleSolver.java:46-54,185` | n/a | Med |
| RT87 | ground locs 45345..45353 (`TOTALTILES_TILE1..9`, values 1..9) and lit game objects FX05 (1), FX06 (2), FX07 (3), FX08 (4), FX09 (5), FX10 (6), FX03 (7), FX11 (8), FX12 (9); the board is a 5x5 grid whose **top-left start tile has ground loc 45353** (value 9) | Active tile = any of those game objects on the tile. Index `y*5 + x` | LD `AdditionPuzzleSolver.java:70-76,212` | ok. LD labels 1 Line, 2 Knives, 3 Triangle, 4 Diamond, 5 Hand, 6 Bird, 7 Crook, 8 Wiggle, 9 Foot | Med |
| RT88 | target number -> optimal tile-index sets (index = row*5 + col): 20 {5,11,17}; 21 {10,11,17}; 22 {10,11,12,18,24}; 23 {5,6,7,8,14}; 24 {5,11,17,23}; 25 {10,11,12,13}; 26 {9,10,11,12,13}; 27 {4,5,6,7,8}; 28 {0,1,7,13,19}; 29 {10,11,12,13,19}; 30 {10,11,12,13,14}; 31 {0,6,12,13,14}; 32 {2,3,4,6,10}; 33 {4,5,6,7,8,9,14}; 34 {10,11,12,13,14,19}; 35 {9,10,11,12,13,14,19}; 36 {0,1,2,3,4,9,14}; 37 {10,11,12,13,14,19,24}; 38 {0,5,6,10,12,18,24}; 39 {2,3,4,7,10,11,12}; 40 {4,9,10,11,12,13,14}; 41 {0,4,6,9,12,13,14}; 42 {0,5,9,10,11,12,13}; 43 {0,1,5,7,10,13,19}; 44 {0,5,10,11,14,17,18}; 45 {0,1,2,3,4,5,10}. Targets below 20 are ignored | Hand-built table of "optimal" solutions (fewest steps), targets 20-45. The room's target is announced by the chat line in section 3.5 | LD `AdditionPuzzleSolver.java:190` | n/a | Med |
| RT89 | ground locs 45365..45373 (`MEMORYGAME_TILE1..9`, name "Symbol"): Line, Knives, Crook, Diamond, Hand, Star, Bird, W (wiggle), Boot. A tile is "matched" when a game object FX05 (Line), FX06 (Knives), FX03 (Crook), FX08 (Diamond), FX09 (Hand), FX04 (Star), FX10 (Bird), FX11 (Wiggle), FX12 (Boot) appears on the same tile | The memory board. LD numbers the symbols for display 1 Line, 2 Crook, 3 Hand, 4 Bird, 5 Knives, 6 Diamond, 7 Star, 8 Wiggle, 9 Boot, with the comment "line, crook, hand, bird are always auto-completed in solos". Pairs: 18 tiles (`HashMap(18)`) | LD `MatchingPuzzleSolver.java` | ok. **The addition board uses the same FX objects for different symbols** (FX03 = Crook in both; FX04 only in matching, FX07 only in addition) | Med |
| RT90 | ground locs 45340 `SIMONSAYS_TILE_UP` (plate), 45341 `_TILE_DOWN`, 45342 `_TILE_DOWN_PLAYER`; spotanim 302 `MM_ROOFCOLLAPSE` | Simon-says: each `DOWN` spawn is the next step of the displayed sequence (queue of **5** positions kept). Two `DOWN` spawns **in the same game tick** = the display finished (puzzle done). A `DOWN_PLAYER` spawn = the player stepped correctly. Spotanim 302 on a tile whose ground object is `UP` = a wrong step: the sequence resets | LD `SequencePuzzleSolver.java:35,74` | ok (`TOA_SCABARAS_SIMONSAYS_TILE_*`, `MM_ROOFCOLLAPSE`) | Med |
| RT91 | NPC 11698 (`GUESSER_OBELISK`, name "Obelisk", op2 "Hit") becomes 11699 (`_HIT`) when struck | LD records the **order** of obelisks that changed to 11699 (`obeliskOrder`, up to 6) and resets the count when one reverts to 11698 | LD `scabaras/overlay/ObeliskPuzzleSolver.java` | ok | Med |
| RT92 | obelisk NPC first seen at scene tile (36,57) -> highlight the **lower** flame; (53,57) -> **upper**; (36,45) -> **upper**; (53,45) -> **lower**. Flames are game object 45135 (`TOA_PATH_BARRIER`, "Barrier") at scene (28,54) upper and (28,42) lower | Shows which barrier flame to click to skip the obelisk puzzle (solo-only option) | LD `SkipObeliskOverlay.java:55-65,196` | ok | Med |
| RT93 | All LD Scabaras constants above are **scene coordinates** (0..103), not region coordinates | They hold only if our scene origin for this room equals the real server's, i.e. the dynamic-region chunk the client centres on must be the same as OSRS gives for the entry tile | LD constants | n/a | High (as a requirement) |
| RT94 | LD: +300 on leaving Scabaras (3 puzzles per player); PT: +300 (`puzzlePointsAssumption`) after leaving the Scabaras puzzle room into Kephri's | Estimate of puzzle points | LD `PointsTracker.java:218-222`; PT `ToAPointsPlugin.java:161-170` | WK module: "Scabaras - puzzles, 300 x team size" | Med |

Kephri (region 14164):

| RT | Value | Meaning / trigger | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT95 | 11719 `BOSS_SHIELDED`, 11720 `BOSS_WEAK` (no ops), 11721 `BOSS_ENRAGE`, 11722 `BOSS_DEAD` (all "Kephri", size 5); 11723 "Scarab Swarm"; 11724 "Soldier Scarab", 11725 "Spitting Scarab", 11726 "Arcane Scarab"; 11727 "Agile Scarab"; 11728 / 11729 "Egg" | ST: phases by `NpcChanged`: 11719 -> **11720** = shield depleted, swarm phase begins; back to **11719** = shield regained (Kephri attacks again); **11721** = green health bar exposed (final phase). Order of events: 11719 -> 11720 -> 11719 -> 11720 -> 11719 -> 11721. MT: 11720 is the "unvengeable" phase; 11722 = dead (all bomb tracking stops) | ST `:1132-1157`; MT `KephriDetector.java:58-66,142` | ok (`TOA_KEPHRI_BOSS_*`, `_SHIELD_SCARAB`, `_GUARDIAN_*`, `_SCARAB_RANGEKITE`, `KEPHRI_EGG_*`). ST/LD use legacy names `KEPHRI`, `KEPHRI_11720`, `KEPHRI_11721` | High |
| RT96 | Kephri anim 9579 (`NPC_KEPHRI_STUNNED`) = goes down; 9581 (`NPC_KEPHRI_STUNNED_WAKEUP`) = gets up | LD counts downs (`kephriDownCount`) and resets the wave counter at each down | LD `SwarmerOverlay.java:43-44,184-195` | ok | High |
| RT97 | swarm NPC 11723; anim 9607 (`..._INTEGRATE01`) = swarm **leaks** into Kephri (heals); 9608 (`..._DEATH01`) = swarm dies | While Kephri is down, swarms spawning on the same game tick belong to one **wave** (`waveNumber++` only when the spawn tick differs from the last). Leaks are counted per (down, wave) | LD `SwarmerOverlay.java:45-46,164-176` | ok | Med |
| RT98 | swarm "can heal Kephri" rule: down 1: waves **1..18**, plus wave **19** if the swarm's NPC index < Kephri's NPC index; down 2: waves **1..14**, plus wave **15** under the same index test | An observation of how many swarms arrive before Kephri stands up. It encodes the swarm count per down (about 18-19 and 14-15) | LD `SwarmerOverlay.java:203-228` | n/a | Med |
| RT99 | graphics objects 1447 (`GARGBOSS_DEBRIS_SHADOW_120`), 1446 (`_90`), 2111 (`_60`): hit **4 / 3 / 2 ticks** after the graphic appears; hit tile = the graphic's tile | Kephri's falling-debris shadows. The names say 120 / 90 / 60 client cycles = 4 / 3 / 2 ticks (30 cycles per tick) | MT `KephriDetector.java:45-48,118-120` | ok: names agree with the delays | High |
| RT100 | A bomb hit is **ignored** when Kephri's health ratio is 0 ("can't do damage when her health is 0 until she phase transitions") and when the raider had a Vengeance proc that tick outside the swarm phase (11720) | Avoids false positives | MT `KephriDetector.java` | n/a | Low |
| RT101 | NPCs 11728 `KEPHRI_EGG_EXPLODE`, 11729 `KEPHRI_EGG_HATCH`; MT defines `KEPHRI_EGG_EXPLODE` ("I've been hatched!") but no detector emits it | Declared, unused | MT `ToaMistake.java` | ok | Low |
| RT102 | projectile 2147 (`KEPHRI_KAMIKAZE_SCARAB`) declared as `EXPLODING_SCARAB_PROJECTILE_ID`; 2266 declared as `KEPHRI_BOMB_PROJECTILE_ID`; graphics 2156..2159 declared as `KEPHRI_BOMB_GRAPHICS_ID` | All three are declared and unused in Kephri's own detector. 2156..2159 *are* used in the Wardens P3 detector | MT `KephriDetector.java:51-55` | 2147 ok; **2266 MISMATCH** (`VORKATH_AREA_TRAVEL_SMALL`); 2156-2159 ok (`VFX_SCARAB_EXPLOSION01..04`) | Low |
| RT103 | hitsplat type 11 (`CYAN_UP`) on an NPC named "Kephri" = shield healing | ST sums it as Kephri's total healing ("Hitsplat ID is shared with Palm of Resourcefulness") | ST `:71,1335` | ok | Med |
| RT104 | LD points factors: 11724, 11725, 11726 = **0.5**; 11723 = 1.0 | Matches WK (Spitting, Soldier and Arcane Scarabs 0.5x). PT agrees (0.5 for the same three ids) | LD `PointsTracker.java:103-105`; PT `ToAPointsPlugin.java:355-360` | ok | High |

### 5.3 Path of Het (puzzle) and Akkha

| RT | Value | Meaning / trigger | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT105 | NPC 11706 `TOA_HET_GOAL` "Het's Seal (protected)", 11707 `_VULNERABLE` "Het's Seal (weakened)" (size 3, op "Destroy"); caster statue loc 45486 `TOA_HET_SOURCE` | LD's beam timer: seal becomes 11706 (protected) -> timer starts at +**10** ticks; becomes 11707 (weakened) -> timer stops | LD `BeamTimerTracker.java:108-115` | ok | High |
| RT106 | beam graphics objects 2114 `FX_BEAM_ATTACK03` ("horizontal"), 2064 `FX_BEAM_ATTACK01` ("vertical"), 2120 `FX_BEAM_IMPACT01` ("crash into an object"); beam period **9 ticks** | Each beam graphic resets `nextFireTick = tick + 9`. `Challenge started: Path of Het.` sets `tick + 9 + 1`; `Challenge complete: Path of Het.` clears. "At least one of the three is guaranteed to happen; there are others for each corner direction" | LD `BeamTimerTracker.java:30-40,78-115` | ok | High |
| RT107 | MT: gfx 2064 / 2114 on a tile = light beam; gfx 379 (`SHADOW_RUSH_IMPACT`) = Orb of Darkness ("Embrace Darkness!") | Light: mistake when the raider stood on the tile the previous tick, but the mistake is **delayed one tick** (the hitsplat shows a tick later). Orb: mistake needs both the tile **and** a hitsplat on the raider in the same tick | MT `HetPuzzleDetector.java:43-44,103-105` | ok; 379 is a generic reused graphic name | Med |
| RT108 | NPC 11708 `TOA_HET_ORB` "Orb of Darkness" | Not used by logic | cache | ok | Low |
| RT109 | mirror 45455 `HET_MIRROR` ("Mirror", movable), 45456 `HET_MIRROR_FIXED`, 45457 `HET_MIRROR_DIRTY` ("Mirror (dirty)"); barriers 45458 / 45460 `BARRIER_INTERNAL` / `_END` (static), 45462 / 45464 `BARRIER_DESTRUCTABLE_INTERNAL` / `_END` (can be mined), 45466 `BARRIER_DESTROYED` (rubble left); 29733 `INVISIBLE_TYPE8_NONBLOCKING` ("this object is placed to clear previous objects"); target "Shielded statue" 45485 `TOA_HET_TARGET` | LD's board model states: 45455 movable mirror, 45456 mirror, 45457 dirty mirror, 45458 / 45460 static wall, 45462 / 45464 breakable wall, 45466 removed wall, 29733 empty | LD `het/solver/HetTileState.java`; `HetSolver.java` | ok (`TOA_HET_*`) | High |
| RT110 | puzzle space = 21 x 21 tiles; base = shielded statue's min scene tile minus (12, 9); object orientation = `(config & 0b11000000) >> 6` with 0 NE, 1 SE, 2 SW, 3 NW | Self-calibrating: no absolute scene coordinates | LD `HetSolver.java:38,114`; `HetTileState.java` | n/a | Med |
| RT111 | 9 solutions, each = list of movable-mirror placements (puzzle-space x,y, orientation) with every tile on the beam line required **empty**: S1 (5,10) NE, (5,18) SE, (14,18) SW; S2 (5,10) SE, (5,6) NE, (18,6) NW, (18,10) SW; S3 (4,10) SE, (4,2) NE, (13,2) NW; S4 (3,10) SE, (3,2) NE, (17,2) NW, (17,11) SW; S5 (2,10) NE, (2,17) SE, (13,17) SW; S6 (3,10) NE, (3,17) SE, (14,17) SW; S7 (5,10) NE, (5,13) SE, (12,13) SW; S8 (4,10) SE, (4,3) NE, (13,3) NW; S9 (5,10) SE, (5,3) NE, (13,3) NW | The puzzle's 9 beam routes. The room is a fixed 21 x 21 board; every route has 3-4 mirrors and its first mirror is on row y = 10 at x 2..5 | LD `HetSolution.java` | n/a | Med |
| RT112 | Het solver resets on `Your party failed to complete the challenge` or a message ending `violently!`; "completed" on any `Challenge complete` | | LD `HetSolver.java:88,101` | n/a | Med |
| RT113 | varbit 14440 `TOA_PICKAXE_STORED` (varp 3635 bits 16-20) != 0 -> a pickaxe is stored; varbit 14439 `TOA_PICKAXE_CHARGES` (bits 0-15); locs 46089 `LOBBY_RAID_ENTRY` ("Entry"), 45131 `DOOR_CONTINUE` ("Entry"), 49566 `LOBBY_WALL02_CAVITY_PARENT`, 45468 `HET_STATUE_PARENT`; menu `Take-pickaxe` (target contains "Statue") and `Enter` (target contains "Entry") are deprioritised when a pickaxe is carried or stored | The Het room needs a pickaxe; the Nexus has a pickaxe storage. LD's list of pickaxe items: 1265 bronze, 1267 iron, 1269 steel, 12297 black, 1273 mithril, 1271 adamant, 1275 rune, 11920 / 12797 dragon, 23677 / 25376 / 30351 dragon (or), 13243 / 13244 infernal (+uncharged), 25063 / 30345 infernal (or), 25369 / 30346 infernal (or, uncharged), 23680 / 23682 crystal (+inactive), 20014 3rd age, 23276 gilded, 25112 echo | LD `het/pickaxe/*.java` | ok: ids are the ones in the cache (`cache-dump config obj`). **TOA_PICKAXE_STORAGE varp is 3635** | Med |

Akkha (region 14676):

| RT | Value | Meaning / trigger | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT114 | 11789 `AKKHA_SPAWN`, 11790 `_MELEE`, 11791 `_RANGE`, 11792 `_MAGE`, 11793 `_ENRAGE_SPAWN`, 11794 `_ENRAGE_INITIAL`, 11795 `_ENRAGE`, 11796 `_ENRAGE_DUMMY` (all "Akkha", size 3); shadow 11797 `AKKHA_SHADOW`, 11798 `_ENRAGE`, 11799 `_ENRAGE_DUMMY` ("Akkha's Shadow"); orbs 11800 `TRAIL_ORB_LIGHTNING`, 11801 `_DARKNESS`, 11802 `_BURN` ("Burning Orb"), 11803 `_FREEZE` ("Frozen Orb"), 11804 `AKKHA_ENRAGE_ORB` ("Unstable Orb"); 11805 `AKKHA_HEADBAR_NPC` | ST: `NpcChanged` to 11795 = enrage start. Shadows: spawn #1 -> despawn (Akkha at **80%**) -> spawn #2 -> despawn (**60%**) -> spawn #3 -> despawn (**40%**) -> spawn #4 -> despawn (**20%**) -> enrage. Splits are the tick differences. LD only knows 11797 (HP overlay), so the **enrage shadow 11798 gets no HP overlay** | ST `:1159,1178-1230`; LD `AkkhaShadowHealth.java` | ok (`AKKHA_*`). ST/LD use legacy names `AKKHAS_SHADOW`, `AKKHA_11795` | High |
| RT115 | graphics objects 2256 `AKKHA_LIGHTNING`, 2257 `AKKHA_DARKNESS`, 2258 `AKKHA_BURN`, 2259 `AKKHA_FREEZE` on quadrant tiles | MT's "memory blast / quadrant bomb": the graphic's tile is a hit tile on activation tick = `tick + (startCycle - gameCycle)/30 + 0`. A raider who is hit on two consecutive ticks is reported **once** until they leave | MT `AkkhaDetector.java:79,89` | ok | Med |
| RT116 | full hourglass locs 46064..46067 (`AKKHA_ARENA_OUTER_LVL2_22/23/57/58_FULL01`, 2x2) exist **and** an "Akkha" NPC is present = hourglass special; then activation tick = `tick + (startCycle - gameCycle - 9)/30 + 1` (`HOURGLASS_SPECIAL_SUBTRACTED_CYCLES = 9`, "3 cycles per tile, up to 3 tiles away"; `HOURGLASS_SPECIAL_HIT_DELAY_IN_TICKS = 1`) | "The animation is reversed but the hit tiles are *not* reversed, they're inversed from the memory special." If Akkha despawns for the memory special when the hourglass fills, the memory special happens first, then the hourglass special starts when Akkha returns | MT `AkkhaDetector.java:78,86-87` | ok | Med |
| RT117 | NPCs named `Orb of Lightning`, `Orb of Darkness`, `Burning Orb`, `Frozen Orb` (11800-11803): when one **despawns**, its last tile is a hit tile (checked the same tick). `Unstable Orb` (11804): graphic **2260** (`AKKHA_ENRAGE_ORB_IMPACT`) on the NPC = popped; hit tile = the NPC's world tile moved **1 tile toward the client-local tile** ("the moving orb despawns too late and changes graphics too early"); a mistake needs a hitsplat on the raider that tick | Trail orb and unstable-orb hits | MT `AkkhaDetector.java:80-83` | ok | Med |
| RT118 | shadow "inactive" = NPC model override amount != 0 ("white-out") -> HP shown as -1 | Client-side visual state of the shadow | LD `AkkhaShadowHealth.java` | n/a | Low |
| RT119 | Het obelisk / seal factor **2.5** on NPC 11707 (`TOA_HET_GOAL_VULNERABLE`); PT calls the same NPC `HET_OBELISK` | Points for mining the seal | LD `PointsTracker.java:107`; PT `ToANpc.java` | ok (name is "Het's Seal (weakened)", not an obelisk) | High |

### 5.4 Path of Apmeken (puzzle) and Ba-Ba

| RT | Value | Meaning / trigger | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT120 | tier-1: 11709 Brawler, 11710 Thrower, 11711 Mage; tier-2: 11712 Brawler, 11713 Thrower, 11714 Mage; 11715 Shaman; 11716 Volatile (`_ZOMBIE`); 11717 Cursed; 11718 Thrall; Ba-Ba's own 11781 "Baboon" | LD lists every one of these as a "baboon" and additionally lists 11716 as "volatile". Points factor **1.2** on 11709-11718 (all of them, tier 1 and 2) | LD `apmeken/ApmekenBaboonIndicator.java`; `PointsTracker.java:86-95` | ok (`TOA_PATH_APMEKEN_BABOON_*`; levels 52-74) | High |
| RT121 | 8 waves: 1 {Brawler 2, Shaman 1}; 2 {Thrower 2, Volatile 1}; 3 {Mage 2, Cursed 1}; 4 {Thrower 2, Special 3}; 5 {Mage 2, Shaman 1, Special 2}; 6 {Brawler 2, Shaman 1, Special 2}; 7 {Brawler 1, Thrower 1, Shaman 1, Cursed 2}; 8 {Shaman 1, Volatile 2, Special 2} ("Special" = the pale baboons) | The composition of each wave as observed (counts per wave: 3, 3, 3, 5, 5, 5, 5, 5 = 34 baboons) | LD `apmeken/Baboon.java` | n/a | Med |
| RT122 | wave counter increments on the next `GameTick` after **any NPC spawns at region-local (24,40), (32,40), (40,40), (24,24), (32,24) or (40,24)** (the NPC's world location reduced to its 64x64 region); reset to 0 on `Your party failed to complete the challenge` | The six baboon spawn points inside the room | LD `ApmekenWaveOverlay.java:34-39,136` | n/a | Med |
| RT123 | loc 45493 `TOA_PATH_APMEKEN_VENOM_POOL` ("Venom pool") | A pool **does not hurt on the tick it spawns** (detection starts a tick later); despawn removes the tile | MT `ApmekenPuzzleDetector.java:53` | ok | Med |
| RT124 | graphic **131** (`FIREBLAST_IMPACT`, reused) | The volatile baboon's explosion: hits the **3x3** around the graphic's tile on the tick it appears (delay 0) | MT `ApmekenPuzzleDetector.java:54` | ok (generic graphic reused) | Med |
| RT125 | graphics 2138 `TOA_PATH_APMEKEN_GAS` (vent) and 2139 `TOA_PATH_APMEKEN_RUBBLE` (pillar) | Declared but unused: the vent / pillar / corruption failures are detected from the **chat lines** in section 3.5 | MT `ApmekenPuzzleDetector.java:51-52` | ok | Low |
| RT126 | LD: +450 on leaving Apmeken; wiki module: "Apmeken - traps, 450 x team size" (6 cycles x 75); PT: +450 after the Ba-Ba puzzle (`BOSS_BABA` entry) | Estimate | LD `PointsTracker.java:224-228`; PT `:166` | agrees | Med |
| RT127 | **Apmeken "sight" widgets: not encoded in any assigned plugin.** MT only groups the three failure chat lines under the label "Apmeken Sight" | Nothing to reproduce except the three chat strings | MT `ToaMistake.java` | n/a | High (negative) |

Ba-Ba (region 15188):

| RT | Value | Meaning / trigger | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT128 | 11778 `TOA_BABA`, 11779 `TOA_BABA_COFFIN`, 11780 `TOA_BABA_DIGGING` (all "Ba-Ba", size 5); 11781 "Baboon"; boulders 11782 `TOA_BABA_BOULDER`, 11783 `_WEAK` ("Boulder", size 3); rubble 11784..11787 ("Rubble", size 3); 11788 `TOA_BABA_SARC_NPC` (no name) | LD gives points factor **2.0** to 11778 / 11779 / 11780 and **0** to both boulders. ST derives phases from `NpcChanged`: to **11780** = "Ba-Ba leaps to the top of the room and starts throwing boulders" (boulder phase begins); to **11778** = end of boulder phase 1; to **11779** = end of boulder phase 2 | LD `PointsTracker.java:84-85,96-98`; ST `:1116-1130` | ok for ids; **ST's comments disagree with the cache symbol names** (cache: 11779 COFFIN, 11780 DIGGING) | Med |
| RT129 | graphics 2250 `TOA_BABA_ROCK_FALL` -> hit **6 ticks** after it appears; 2251 `TOA_BABA_ROCK_FALL_FASTER` -> **4 ticks** | Falling-boulder tile hazards. The mistake needs the raider's previous tile on the hit tile **and** a hitsplat on the raider that tick, and a Rubble NPC must still exist; a raider in the knock-back anim 9799 is excluded | MT `BabaDetector.java:106-109,257,310-311` | ok (names say ROCK_FALL / FASTER) | High (the same ids and 6 / 4 are used in Wardens P3) |
| RT130 | Ba-Ba anim 9744 (`NPC_MANDRILL_ATTACK_RANGED_01`) = the projectile boulder; explosion **7 ticks** later (`PROJECTILE_BOULDER_DELAY_IN_TICKS`). A raider is safe on the **5x5** block around each Rubble (SW tile -1 .. +3 in both axes). Damage heuristic: a hitsplat > **15** is "large" (a safe hit is about 4 or less, a failed one about 40+, "it also always seems to over-hit", "you can half it by standing next to a baboon / sarcophagus") | Rubble-shelter mechanic. MT admits the multi-hitsplat resolution is "super hacky ... written at 4am without testing in a group" | MT `BabaDetector.java:120-134` | ok | Low |
| RT131 | rolling boulders: NPC "Boulder" tracked from **3 ticks after it spawns** (collision "doesn't really start for 1-2 ticks"). Footprint = 3x3 tiles at +1..+3 in X from the NPC's SW tile; players are pushed toward **-X**. A raider is rolled when: previous tile in the footprint, or moved > 2 tiles in -X in one tick (only during a boulder phase), or anim 7210 (`HUMAN_KNOCKDOWN`); a raider cannot be rolled two ticks in a row; a boulder that vanishes at the wall x (region-local tile (22,25)) extends the footprint **one more tick** one tile further west | Boulder-phase hit model | MT `BabaDetector.java:96-105,129` | ok | Med |
| RT132 | graphics **1103** (`DARKBOW_SMOKE_ARROW_IMPACT`, reused) on a tile = slam hit tile (checked the same tick) | Ba-Ba's slam | MT `BabaDetector.java:111` | **MISMATCH in name** (generic reused graphic `DARKBOW_SMOKE_ARROW_IMPACT`); the plugin keys on the id only | Low |
| RT133 | region-local tiles (20..21, 30..34) in region 15188; fall anim 4366 (`QUEST_LUNAR_FALLING_ON_PLATFORM`) declared unused | The gap ("Mind the Gap!" invocation): standing there at the end of the previous tick = fell, reported once per player | MT `BabaDetector.java:90-103` | ok; 4366 name is generic | Med |
| RT134 | loc 45755 `TOA_BABA_BANANA_PEEL` ("Banana peel", "if you 1-tick the banana it never even spawns"); player anim 4030 (`ROYAL_HUMAN_SLIP_FALL`) or graphic 1575 (`TOB_BLOAT_STUNNED`) = slip; re-slip within **3 ticks** is not counted again | | MT `BabaDetector.java:114-117` | ok (generic anim / graphic reused) | Med |
| RT135 | graphic 1463 (`EARTHSURGE_IMPACT`) on a Rubble NPC = a hit on the rubble; any other graphic during a hitsplat on the Rubble = phase transition (clears pending projectile boulders) | | MT `BabaDetector.java:119` | ok (generic graphic) | Low |
| RT136 | NPC 11788 `TOA_BABA_SARC_NPC`: health bar scale **30** (assumed before the bar is ever shown); "low" when `ratio <= scale / 2` | LD's sarcophagus highlight | LD `BabaSarcophagusWarning.java` | ok | Med |
| RT137 | item 27221 `TOA_BABA_BANANA` ("Big banana") spawn in Ba-Ba's room = MVP item | See section 7. PT uses 27221 too | LD `PointsTracker.java:327-336`; PT `:251` | ok | High |

## 6. The Wardens

Regions 15184 (P1 obelisk and P2) and 15696 (P3 and enrage).

| RT | Value | Meaning / trigger | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT138 | Elidinis' 11746 `PHASE1_INACTIVE`, 11748 `PHASE1`; Tumeken's 11747 `PHASE1_INACTIVE`, 11749 `PHASE1` (all size 5, level 489, **no ops**); P1 obelisk 11750 `P1_OBELISK_NPC_INACTIVE`, 11751 `P1_OBELISK_NPC` (op "Attack"), P2 obelisk 11752 | LD points factor **0** on all four warden P1 ids, **1.5** on 11750 / 11751 / 11752 | LD `PointsTracker.java:108-116` | ok | High |
| RT139 | Elidinis' 11753 `PHASE2_MAGE`, 11754 `_RANGE`, 11755 `_EXPOSED`; Tumeken's 11756 `PHASE2_MAGE`, 11757 `_RANGE`, 11758 `_EXPOSED` (size 5, level 489; "Exposed" has no ops) | LD factor **2.0** for mage / range forms, **0** for exposed; P2 forms are capped at "3 downs" (see below). MT: NpcChanged to 11755 / 11758 = the "health phase" (the core is up): all DDR / windmill / bomb / special-prayer tracking is cleared ("DDR special can no longer deal damage here as of 09/21/2022") | LD `PointsTracker.java:117-122`; MT `WardensP1P2Detector.java:101` | ok (`TOA_WARDEN_*_PHASE2_*`) | High |
| RT140 | inactive 11759 (Elidinis), 11760 (Tumeken); active 11761, 11762 (level 544); charging 11763, 11764 | LD factor **2.5** on 11761-11764, **0** on the inactive ones. PT's `*_FINAL` = 11761 / 11762 only (2.5) | LD `PointsTracker.java:123-126`; PT `ToANpc.java` | ok | High |
| RT141 | cores 11770 `TUMEKEN_CORE`, 11771 `ELIDINIS_CORE` (name "Core", op2 "Attack"); Energy Siphon 11772 `WARDENS_P3_ORB_BLUE` (op2 "Attack"), 11773 `_ORB_RED` (no name); phantoms 11774 Zebak's, 11775 Ba-Ba's, 11776 Kephri's, 11777 Akkha's; 11767 / 11768 Amascut "Phantom" size 8; 11765 / 11766 P3 death (no name); 11769 `WARDENS_ENERGY` | LD factor **0** on both cores, **1.0** on Energy Siphon 11772. PT labels 11770 as ELIDINIS core and 11771 as TUMEKEN core (the cache says the reverse) with the same factor 0, so no effect | LD `PointsTracker.java:81-83`; PT `ToANpc.java` | ok. **PT's core labels are swapped**; harmless | High |
| RT142 | NPC anim 9670 (`NPC_WARDENS_KNEEL01`) on a P2 warden = one **down** | LD counts downs per raid; after the **third** down points stop for P2 warden forms: the check is `wardenDowns > 3`, i.e. points keep counting until the **4th** down animation starts. The count resets on a room failure message. The wiki says "no additional points beyond their third down" | LD `PointsTracker.java:78,300-302,351-353` | ok | Med (off-by-one OPEN) |
| RT143 | hitsplat types **53** (`DAMAGE_ME_POISE`), **54** (`DAMAGE_OTHER_POISE`), **55** (`DAMAGE_MAX_ME_POISE`) on the P2 wardens | ST treats them as shield damage ("<Warden> Shielded"); PT uses 53 / 55 / 11 that are neither mine nor others' to approximate team damage. The server must keep sending these hitmark types on the shielded wardens or ST's and PT's accounting breaks | ST `:67-69,1381-1387`; PT `:100` | ok: hitmark 53, 54, 55 exist | High |
| RT144 | red pyramid loc 45750 and yellow pyramid loc 45751 (3x3, no name, `PYRAMID01/02_LOAD`); the pyramid's dynamic-object animation **9524** (`TOA_WARDENS01_PYRAMID_ATTACK`) means it is about to hit the 3x3 around its centre, with a **1 tick** delay | P1 pyramid hit model (detected per tick from the object's current animation, not an event) | MT `WardensP1P2Detector.java:73-76` | ok | Med |
| RT145 | projectiles 2237 `SPOTANIM_WARDENS_PHASE01_BALL01` (death dot) and 2238 `..._BALL02` (disperse) | Declared, unused | MT `WardensP1P2Detector.java:77-78` | ok | Low |
| RT146 | obelisk NPC (name `Obelisk`) anim 9732 (`FX_OBELISK_WARDENS02_BEAM`) = DDR phase, 9733 (`..._SWEEP`) = windmill, 9727 (`..._BOMB`) = bombs, 9734 (`..._DEATH`) = death | The current obelisk attack decides how the graphics below are read | MT `WardensP1P2Detector.java:88-91` | ok | Med |
| RT147 | graphic 2235 (`..._DRONESTRIKE01_FIRE01`) = DDR tile, hit **1 tick** after it appears; graphic 2199 (`WARDENS_DARKNESS_WARN`) = DDR lightning, hit **0 ticks**; graphic 2234 (`SPOTANIM_AMASCUT01_STRIKE01`) while the obelisk is in DDR = hit on the same tick | DDR | MT `WardensP1P2Detector.java:82-87,92` | ok | Med |
| RT148 | graphic 2234 while the obelisk is in the windmill phase = **instant** hit tile (same tick); graphic 2200 (`WARDENS_BURN_WARN`) = windmill lightning, instant; graphic 2236 (`..._HIGHLIGHT01`) declared, unused | Windmill | MT `WardensP1P2Detector.java:83-84,93` | ok | Med |
| RT149 | graphic 2198 (`WARDENS_LIGHTNING_WARN`), counted only while the obelisk is in the bomb phase, hit **0 ticks** after it appears | Bombs | MT `WardensP1P2Detector.java:85` | ok | Med |
| RT150 | player anim 9714 (`TOA_WARDENS_ENTOMB_LOOP`) = bound (entombed) -> mistake once per tick | P2 bind | MT `WardensP1P2Detector.java:94` | ok | Med |
| RT151 | projectiles 2204 (`TOA_WARDENS_PRAYER_MELEE_TRAVEL`) = melee, 2206 (`..._RANGED_...`) = ranged, 2208 (`..._MAGIC_...`) = magic: the "special prayer" attack. The prayer is checked on the tick the projectile **lands** (`remainingCycles / 30`); a projectile first seen with **<= 30 cycles** left is ignored ("hanging around from a previous attack"). No overhead icon = a miss | P2 special attack that requires a specific overhead prayer | MT `WardensP1P2Detector.java:96-100`; `OverheadTracker.java` | ok | Med |
| RT152 | graphics 2220..2223 (`SPOTANIM_WARDENS01_UNDERTILE01_EXPLODE01..04`) = earthquake tile, hit **0 ticks**; 2156..2159 (Kephri bombs) instant; 2250 / 2251 Ba-Ba rocks **6 / 4 ticks**; 2197 (`WARDENS_LIGHTNING`) instant | P3 attacks by phantom | MT `WardensP3Detector.java:61-81` | ok | Med |
| RT153 | Akkha phantom projectiles 2253 (`AKKHA_MAGIC_TRAVEL`) = needs Magic, 2255 (`AKKHA_RANGED_TRAVEL`) = needs Ranged: prayer checked on the **spawn tick**. Zebak phantom projectiles 2181 (`ZEBAK_MAGE_PROJANIM_SPLIT`) = Magic, 2187 (`ZEBAK_RANGED_FRAGMENT01`) = Ranged: prayer checked when it **lands** ("3 ticks later") and the mistake is queued for that tick | Two different prayer-check timings in the same phase | MT `WardensP3Detector.java:64-72,189-193` | ok | Med |
| RT154 | projectile 2226 (`FX_WARDENS_BOMB03`) | ST calls it the "energy siphons (red skulls)" flying to or from a warden in P3; two consecutive moves with the same position mean the siphons were killed. Heal hitsplats on a warden: one at the start of P3 and one at the enrage ("phase 4") | ST `:1343-1360,1316-1330` | **MISMATCH in name** (`FX_WARDENS_BOMB03`) | Low |
| RT155 | `Challenge started: The Wardens.` starts the obelisk clock; obelisk done on `As X's Warden falls, Y's Warden powers up!`; P2 done on `X's Warden uses the last of its power to restore Y's Warden!`; P3 ends on `Challenge complete: The Wardens.`; the P3 -> enrage split is the second warden heal hitsplat | The phase boundaries a splits plugin keys on | ST (see section 3.5) | n/a | Med |
| RT156 | LD disables camera shake while the player is in either Wardens region ("P4 Wardens") | Client-side | LD `CameraShakeDisabler.java` | n/a | Low |
| RT157 | room point caps: **60,000** in the Wardens rooms (`MAX_ROOM_POINTS * 3`); Warden pet ids: 27352 Tumeken's guardian, 27354 Elidinis' guardian (`WARDENPET_*`) | See section 7. ST uses the pet items as infobox icons | LD `PointsTracker.java:470`; ST `:70-77` | ok | High |
| RT158 | varp 3641 `TOA_WARDENS`; varbits 14455 `TOA_WARDENS_BUFF_TYPE` (2 bits), 14456 `TOA_WARDENS_BUFF_VALUE` (10 bits) | Present in the cache; **no plugin reads them** | cache only | ok | Low |

## 7. Points model (LD, PT; wiki cross-check)

The plugins model what the server computes but never sends. Everything below is a client reconstruction; the server
rule is the wiki's drop-mechanics page (`WK Update:Tombs_of_Amascut_Drop_Mechanics_&_Osmumtens_Fang.txt`) and the
`Module:Tombs_of_Amascut_loot` page, which agree with LD on every constant below unless a conflict is noted.

| RT | Value | Meaning | Source | Check | Conf. |
|---|---|---|---|---|---|
| RT159 | start with **5,000** total points | | LD `PointsTracker.java:73`; PT `ToAPointsPlugin.java:44` | WK: "You start the raid with 5,000 Total points" | High |
| RT160 | **Personal points are not sent to the client during the raid.** `varp 3606 TOA_PERSONAL_CONTRIBUTION` is set once, at the end ("as of client rev 232, personal points are transmitted to the client at the END OF THE RAID ONLY"). LD then replaces its estimate with `internal total = value + 5000` and displays `value` | So the varp holds the personal points **excluding** the 5,000 base. Our server must write varp 3606 at raid end (and keep it 0 before) or LD never "finalizes" | LD `PointsTracker.java:361-370,377-379` | cache: varp 3606 present | Med |
| RT161 | room points += `damage x factor` (integer truncation) for **your own** hitsplats on NPCs; total points also +same; capped per room at **20,000** (Wardens rooms 60,000 = 3 x, Zebak room 10,000 = 1/2), total capped at **64,000** | LD adds to room and total **at the same time**; the room points reset to 0 on `Challenge complete` or a failure message | LD `PointsTracker.java:74-76,300-320,465-477` | WK: 20,000 room cap, 64,000 total cap. WK does not give the Zebak 10,000 / Wardens 60,000 caps | Med |
| RT162 | LD comment: the blog "was describing points earning slightly wrong"; points go to total immediately and "there is no special behaviour when wiping a room; the 20% points lost is intended to account for that" | Disagrees with WK / PT, where room points are added to the total only when the room completes | LD `PointsTracker.java:50-58` | **CONFLICT**: see section 12, C4 | Low |
| RT163 | each death: `total -= max(20% of total, 1000)`, floor 0 (LD); PT: `loss = max(total * 20 / 100, 1000)`, total set to 0 when total < 1000 | | LD `PointsTracker.java:261-267`; PT `ToAPointsPlugin.java:282-295` | WK: "Death subtracts 20% from your Total points, with a minimum of 1,000" | High |
| RT164 | on `Your party failed to complete`: room points = 0 and the Wardens down counter = 0; PT also zeroes room points when a solo player dies | | LD `PointsTracker.java:270-276`; PT `:270-280` | n/a | Med |
| RT165 | per-NPC damage multipliers (LD, by cache NPC id): **0** 11770, 11771 (cores), 11782, 11783 (boulders), 11746-11749 (Wardens P1 forms), 11755, 11758 (P2 exposed), 11759, 11760 (P3 inactive); **1.0** 11772 (Energy Siphon), 11723 (Kephri shield scarab); **0.5** 11724, 11725, 11726 (Soldier / Spitting / Arcane Scarab); **1.2** 11709-11718 (all ten baboon ids incl. tier 2, Volatile, Cursed, Thrall); **1.5** 11730, 11732, 11733, 10680 (Zebak), 11750, 11751, 11752 (Wardens obelisks); **2.0** 11778, 11779, 11780 (Ba-Ba), 11753, 11754, 11756, 11757 (P2 wardens); **2.5** 11707 (Het seal), 11761-11764 (P3 wardens); everything else **1.0** | The factor table | LD `PointsTracker.java:80-128` | WK: cores 0x, monkeys 1.2x, Ba-Ba 2x, Zebak 1.5x, the three scarabs 0.5x, Het obelisk 2.5x, P1 obelisk 1.5x, P2 wardens 2x (no points beyond their third down), P3 wardens 2.5x; all agree. Not in WK: boulders 0, siphon 1.0, shield scarab 1.0 | High |
| RT166 | PT table: 1.2 on 11709, 11710, 11711, 11715, 11716, 11717, 11718 (**tier-1 only**); 2.0 on 11778 and the four P2 ids 11753, 11754, 11756, 11757; 0.0 on 11783 (**weak boulder only**), cores, inactive/exposed P1-P3 ids; 2.5 on 11707 and 11761 / 11762; 1.5 on 11730, 11732, 11751 (**not** 11750 / 11752); 0.5 on 11724-11726; PT's unlisted ids default to 1.0 | PT is a subset of LD with label errors (section 12, C1, C9) | PT `ToAPointsPlugin.java:305-376`, `ToANpc.java` | ok | Med |
| RT167 | P2 warden forms count only while `wardenDowns <= 3`; `wardenDowns` increments on NPC anim 9670 of a P2 form | See RT142 | LD `PointsTracker.java:131-138,300-302,351-353` | WK: "no additional points beyond their third 'down'" | Med |
| RT168 | MVP: when item **27214** (`TOA_KEPHRI_POO` "Scarab dung"), **27219** (`TOA_ZEBAK_FANG` "Fang"), **27221** (`TOA_BABA_BANANA` "Big banana") or **27223** (`TOA_AKKHA_ASHES` "Eldritch ashes") **spawns** in the room, total += `300 x team size`, once per item per raid. The Wardens give no item, so LD has no MVP for them | LD `ItemSpawned` | LD `PointsTracker.java:140-145,327-336`; PT `ToAPointsPlugin.java:251-265` (same four ids, scoped to the boss region) | ok: all four ids; WK: "MVP bonus of 300 x Group Size to your Total points upon room completion" | High |
| RT169 | PT `mvpAssumption`: +300 (flat, not x team size) when leaving a puzzle room into its boss room, and +300 when leaving the Wardens P3 room into the chest room ("wardens don't drop an item so assumption is needed") | WK module adds `MVP = 2700 x team size` (= 9 rooms x 300 per person) | PT `ToAPointsPlugin.java:196-250` | **OPEN**: LD counts only the four boss items; PT counts puzzles and Wardens at a flat 300; WK module uses 9 rooms. Which rooms award MVP is not settled by the plugins | Low |
| RT170 | LD room-exit estimates: leaving **Scabaras** +300 to personal total (and +300 "non party"), **Apmeken** +450 (+300), **Crondis** +400 (+300), **Het** +0 (+300 non party). LD TODO: "50 per puzzle completed and 50 to each player in the lane on completion; memory: 100 pts per tile matched only to the second player; Apmeken 25 per pillar repaired / 25 per vent neutralized, -25 for a mistimed one, 25 to sight on corruption clear, -25 on fail, 25 to healer on corruption cure" | The author's partial model of puzzle points | LD `PointsTracker.java:47-55,218-241` | WK module: Scabaras 300 x team, Apmeken 450 x team, Crondis 400 x team | Med |
| RT171 | unique chance (LD / WK module): `adjusted = min(raidLevel, 550)`; if `> 310` then (`if > 430: adjusted = 430 + (adjusted - 430) / 2`) then `adjusted = 310 + (adjusted - 310) / 3`; denominator = `10,500 - 20 x adjusted`; chance = `min(55%, points / denominator)`. Pet: `adjusted = min(raidLevel,550)`; if `> 400` then `400 + (adjusted - 400) / 3`; denominator = `350,000 - 700 x adjusted` | The drop-rate formula; 55% cap | LD `UniqueChanceCalculator.java:9-23`; WK `Module:Tombs_of_Amascut_loot.txt:162-170` | WK module identical for uniques; pet has a branch `> 550 -> 450` which equals the LD result at 550 | High |
| RT172 | PT: unique and pet both use `level > 400 -> 400 + (level - 400) / 3`, `denominator = 10,500 - 20 x level` (pet 350,000 - 700 x level), no 55% cap, and requires level >= 150 for the displayed chance | PT follows the **older blog text** ("raid levels 400-550 only have 1/3 the effect") | PT `ToAPointsOverlay.java:70-110` | WK blog says the same, but the newer module is LD's. **PT is out of date** | Low |
| RT173 | unique weights at raid level < 300: Osmumten's Fang **70**, Lightbearer **70**, Elidinis' Ward **30**, Masori mask / body / chaps **20** each, Tumeken's Shadow **10** (= 7 / 7 / 3 / 2 / 2 / 2 / 1 per 24) | | LD `PurpleWeightingManager.java` | WK blog: same ratio | High |
| RT174 | Lightbearer: `70 - lerp(450..500, 5) - lerp(300..450, 30)`; Fang: `70 - lerp(450..500, 10) - lerp(350..400, 20) - lerp(300..350, 10)`; both x50 for levels 50..149 ("ward + masori + shadow are reduced with an additional 1/50 roll", simulated by multiplying these two by 50). Wiki module gives Fang 30 at >=500, 40 at 450, 40 at 400, 60 at 350, 70 at 300 and Lightbearer 35 / 40 / 50 / 60 / 70 for the same levels: identical to LD at those points | The raid-level weight shift for the two common uniques | LD `PurpleWeightingManager.java:135-147`; WK `Module...:Tombs_of_Amascut_loot.txt:117-145` | agrees | Med |
| RT175 | purple ids: 26219 Osmumten's fang (`OSMUMTENS_FANG`), 25975 Lightbearer, 25985 Elidinis' ward, 27226 / 27229 / 27232 Masori mask / body / chaps, 27277 Tumeken's shadow (uncharged); cursed phalanx 27246 (`OSMUMTENS_FANG_ORNAMENT`) / 27248 (`..._KIT`) | | LD `Purple.java`, `CursedPhalanxDetector.java` | ok: all names agree | High |

## 8. Tomb (loot room) and reward interface

| RT | Value | Meaning | Source | Cache 2757 | Conf. |
|---|---|---|---|---|---|
| RT176 | interface 771 `TOA_CHESTS` loads when the chest opens; container 811 `TOA_CHESTS` (RL `InventoryID.TOA_CHESTS`); RL loot tracker records loot with metadata `[raidLevel, teamSize, raidDamage]`, reading varbit 14325 `TOA_DAMAGE_DONE` (16 bits, varp 3586 bits 0-15) at that moment and the raid level / party varbits | The loot tracker and LD's dry streak key on interface 771 | RL `loottracker/LootTrackerPlugin.java:864-880`; LD `DryStreakTracker.java` | ok | High |
| RT177 | varbits 14356..14360 and 14370..14372 `TOA_VAULT_CHEST_0..7` (3 bits each); value **2** = "a purple (key) sits in that chest"; varbit 14373 `TOA_VAULT_SARCOPHAGUS` (3 bits, varp 3590 bits 19-21): **odd = the shared sarcophagus is purple** | LD recolors the sarcophagus: purple + none of the eight chest varbits == 2 means the purple is **yours**; if some chest varbit == 2 the purple belongs to someone else | LD `SarcophagusRecolorer.java:69-75,169-174` | ok (`TOA_VAULT_CHEST_*`, `TOA_VAULT_SARCOPHAGUS`) | High |
| RT178 | loc 46221 `TOA_VAULT_BARRIER_PARENT` (wall object whose model LD recolors), loc 44934 `TOA_VAULT_SARCOPHAGUS_OPEN` ("Opened sarcophagus", plays the chest sound when it spawns "as the player starts looting the chest") | | LD `SarcophagusRecolorer.java`; `SarcophagusOpeningSoundPlayer.java:47` | ok. 46221's cache name says barrier parent: LD calls it the sarcophagus wall object | Med |
| RT179 | `TOA_VAULT_SARCOPHAGUS` odd on entering the Tomb region resets LD's purple dry streak; otherwise it increments | LD client convenience | LD `DryStreakTracker.java` | ok | Low |
| RT180 | in the Tomb region with raid level >= **500** and the player carries / wears 27246 or 27248, the "Open" menu click is consumed while the player is still "eligible" (the `Total deaths` line has not been seen yet) with the chat line `Remove and/or drop cursed phalanx before doing that.` | Reflects an in-game rule that a cursed phalanx affects the 500+ chest | LD `CursedPhalanxDetector.java` | ok | Low |
| RT181 | animation 9493 `TOA_SOT_CAST_B` (Tumeken's shadow cast) is excluded from RL's animation smoothing | The only ToA id in RL core outside the plugins above (RL has no ToA NPC, loc or graphic ids and no raid timer or boss helper) | RL `animsmoothing/AnimationSmoothingPlugin.java:97` | ok (`TOA_SOT_CAST_B` 9493) | Low |
| RT182 | pet items 27382 Akkhito, 27383 Babi, 27384 Kephriti, 27385 Zebo (path pets), 27352 Tumeken's guardian, 27354 Elidinis' guardian | ST infobox icons | ST `:70-77` | ok (`WARDENPET_*`) | Low |

## 9. Hit-resolution and tick digest (all values from the sections above)

"Delay" = ticks between the client seeing the event (graphic created / anim changed) and the damage tick. "Same tick" = 0.

| RT | Source of the hit | Delay / rule | Detail |
|---|---|---|---|
| RT183 | Het beam period | **9 ticks** between beam impacts; seal protected again -> next beam 10 ticks later | RT106, RT105 |
| RT184 | Het light beam / dark orb | graphic 2064 / 2114 / 379 on the tile; light is counted **1 tick later** (hitsplat delay) | RT107 |
| RT185 | Akkha quadrant bombs 2256-2259 | 0 ticks (hit tick = tick the graphic appears + `(startCycle - cycle)/30`) | RT115 |
| RT186 | Akkha hourglass special | graphic start minus 9 cycles, then +1 tick | RT116 |
| RT187 | Kephri debris 1447 / 1446 / 2111 | **4 / 3 / 2 ticks** | RT99 |
| RT188 | Zebak acid locs 45570-45576 | harmless the tick they spawn | RT79 |
| RT189 | Zebak wave push | >2 tiles displacement in Y in one tick | RT81 |
| RT190 | Ba-Ba rocks 2250 / 2251 | **6 / 4 ticks** | RT129 |
| RT191 | Ba-Ba projectile boulder anim 9744 | **7 ticks** to the explosion; damage > 15 means unsheltered | RT130 |
| RT192 | rolling boulder | collision from 3 ticks after the NPC spawns; wall-despawn extends 1 tick | RT131 |
| RT193 | banana slip | re-slip within 3 ticks ignored | RT134 |
| RT194 | Apmeken venom pool 45493 | harmless the tick it spawns | RT123 |
| RT195 | volatile baboon graphic 131 | 3x3, same tick | RT124 |
| RT196 | Wardens P1 pyramid anim 9524 | **1 tick**, 3x3 | RT144 |
| RT197 | Wardens P2 DDR graphic 2235 | **1 tick** | RT147 |
| RT198 | DDR lightning 2199, bombs 2198, windmill 2234 / 2200, P3 earthquake 2220-2223, P3 lightning 2197 | same tick | RT147, RT148, RT149, RT152 |
| RT199 | Wardens P3 Akkha projectiles 2253 / 2255 | prayer checked on the **spawn tick** | RT153 |
| RT200 | Wardens P3 Zebak projectiles 2181 / 2187; P2 special prayer 2204 / 2206 / 2208 | prayer checked when the projectile **lands** (`remainingCycles / 30`); a projectile with <= 30 cycles left when first seen is ignored | RT153, RT151 |
| RT201 | leaving the raid | LD keeps `inRaid` true for **3 ticks** after the last ToA region / HUD | RT16 |
| RT202 | supplies | SU diffs the inventory **1 tick** after a withdraw | RT67 |
| RT203 | buff timers | liquid adrenaline 150 s; silk dressing 100 ticks; blessed crystal scarab 40 ticks; smelling salts varbit x 25 ticks | RT69 |

## 10. What the server must provide so these plugins keep working

Derived list; each item cites the rows that prove it.

1. Template regions in section 2, with instance chunk tables that map back to them, unrotated (RT1 .. RT13).
2. Interface 481 shown for the whole raid with children 4, 40, 42, 45, 46, 49, 51, 53, 55 populated (text children are
   parsed as bare integers where noted) (RT16, RT17, RT18, RT19, RT20).
3. Varp 3592 carrying raid level (bits 12-22), the four path levels (bits 0-11), current path (23-25); varp 3588 / 3589
   carrying `P0..P7` as 5-bit HP buckets 0-26 (+1; 0 = absent) and varcs 1099-1106 carrying names (RT29,
   RT30, RT25, RT26).
4. Party board interface 774 with the invocation list at child 52 in three-children-per-invocation layout and on-op
   arg 3 = 1 when active, plus the script ids in RT41.
5. The chat lines in section 3.5, with the exact wording and the PB -> KC order of the Wardens completion block
   (RT50).
6. NPC id changes for phases: Kephri 11719/11720/11721, Ba-Ba 11778/11779/11780, Akkha 11795 (enrage) and shadow
   11797 spawn/despawn at 80/60/40/20%, Het seal 11706/11707, Wardens exposed forms 11755/11758, Scabaras obelisk
   11698/11699 (RT95, RT128, RT114, RT105, RT139, RT91).
7. Hitmark types 11 / 15 (palm and Kephri shield), 53 / 54 / 55 (P2 wardens) and 6 (heal) (RT143, RT76).
8. Graphics, animation and projectile ids listed per room: reuse the OSRS ids, because MT keys on them.
9. Varp 3606 written once at raid end (RT160); varbits 14325 (damage done) and 14361 / 14344 for buffs (RT176,
   RT69); varbits 14356-14373 for the chest room (RT177).
10. Scabaras puzzle board positions identical to OSRS in scene coordinates (RT93).

## 11. What no assigned plugin encodes (negative results)

These were asked for and are **absent**. Do not look for them in these plugins; use the wiki, `docs/mechanics/` room
docs or RSProx captures instead.

| RT | Topic | Finding |
|---|---|---|
| RT204 | Boss attack-cycle counters (Akkha, Zebak, Ba-Ba, Kephri, Wardens phases 1-3) | None. Unlike ToB's SpoonTob, no ToA plugin counts a boss's attack interval. The only "period" is Het's 9-tick beam (RT106). Boss attack speeds, special-attack rotations and Wardens phase timers must come from the wiki or captures |
| RT205 | Akkha memory blast: iteration count and spacing | Not encoded. MT only handles the **hit delay of each quadrant bomb graphic** (RT115, RT116). The wiki says path levels add Memory Blast iterations (every two levels) and speed up the detonation special |
| RT206 | Zebak attack counters, jug / waterfall timings, rolling jug, wave spacing | Not encoded. Only acid, earthquake, wave push and blood cloud hit rules (RT79 .. RT82) |
| RT207 | Kephri swarm spawn interval / egg timers / bomb cycle | Not encoded. Only the wave counting rule and the debris shadow delays (RT97, RT99) |
| RT208 | Ba-Ba boulder cadence, boulder speed in tiles per tick, slam interval | Not encoded beyond collision start (3 ticks after spawn) and rock fall delays (RT131, RT129) |
| RT209 | Warden phase timers (P1 obelisk length, P2 down duration, P3 rotation, enrage onset) | Not encoded. ST measures phase **lengths** for display only; boundaries are chat lines and heal hitsplats (RT155, RT154) |
| RT210 | Apmeken "sight" (vent / pillar / corruption) widgets | Not encoded: MT detects the three failures from chat lines only (RT55, RT127) |
| RT211 | Crondis puzzle solver, Zebak solver | No solver exists in any assigned plugin (PH's panel shows static pictures for Kephri and Ba-Ba only) |
| RT212 | PH (`toa-puzzle-helper`) | Displays three static PNGs (Kephri lights, Kephri sums, Ba-Ba solo wave spawns); no widget, varbit or timing is used beyond the region list in section 2 |
| RT213 | Amascut (P3 "Phantom" 11767 / 11768 size 8, `TOA_AMASCUT_P3`) | Not referenced by any plugin |
| RT214 | varp 3641 `TOA_WARDENS`, varbits 14455 / 14456 (Wardens buff type / value) | In the cache, read by none |

## 12. Conflicts between plugins (and between plugins and the wiki)

| C | Conflict | Rows | Resolution suggested |
|---|---|---|---|
| C1 | **Baboon multiplier.** PT gives 1.2 only to the tier-1 brawler / thrower / mage (11709-11711) plus 11715-11718; the tier-2 forms 11712-11714 fall through to 1.0. LD and the wiki give 1.2 to all monkeys | RT165, RT166, RT120 | Use LD (1.2 on 11709-11718) |
| C2 | **Boulder multiplier.** PT zeroes only 11783 (weak boulder); LD zeroes 11782 **and** 11783. The wiki is silent | RT165, RT166 | Use LD. Mark the 11782 value `OPEN` if exact |
| C3 | **Wardens obelisk multiplier.** PT gives 1.5 to 11751 only; LD to 11750, 11751 and 11752. The wiki gives P1 obelisk 1.5 (it does not say which ids) | RT166 | Use LD |
| C4 | **When room points reach the total.** LD (author's "insider knowledge"): immediately, and death takes 20% of a total that already contains this room's points. Wiki blog and PT: room points are added to the total at room completion; the blog's worked example (250 damage on Ba-Ba x2 = 500 room points, die at 5,000 total -> 4,000, room end 4,000 + 500 + 1,200 MVP = 5,700) gives 5,700 under the blog model and 5,600 under LD's (total 5,500 before the death, -1,100, + 1,200); the gap is 20% of the room points | RT162, RT163 | `OPEN`. Needs a captured raid-end total to settle. Until then follow the wiki (room points added at completion) |
| C5 | **Unique-chance formula.** LD / wiki module: 310 (/3) and 430 (/2) breakpoints for uniques, 400 (/3) for the pet, 55% cap. PT (and the older blog text): 400 (/3) for both, no cap | RT171, RT172 | Use LD / the wiki module |
| C6 | **Which rooms award MVP.** LD: four boss rooms via the dropped item, 300 x team. PT: those four plus a flat +300 per puzzle room and +300 at the Wardens. Wiki module: 9 rooms x 300 per person | RT168, RT169 | `OPEN` |
| C7 | **Third down.** LD stops P2 warden points when `wardenDowns > 3` (after the 4th down starts); the wiki says "no additional points beyond their third down". Off by one at most | RT142, RT167 | Follow the wiki: stop when the third down has ended. Mark `OPEN` |
| C8 | **Zebak waves.** MT matches the NPC name `Wave` exactly. The cache has two wave NPCs, 11738 "Wave" and 11739 "Bloody Wave"; the bloody variant would be missed | RT81 | Keep both NPC ids as in the cache; the plugin gap is MT's |
| C9 | **Label swaps in PT** (no behavioural effect): cores 11770 / 11771 are labelled Elidinis / Tumeken (cache: 11770 = Tumeken's core, 11771 = Elidinis' core); mage / range of the Tumeken P2 forms (11756 / 11757) are swapped; 11748 / 11749 are labelled "INACTIVE_P1" (cache: they are `PHASE1`; the `_INACTIVE` ones are 11746 / 11747); 11707 is called an obelisk (it is Het's weakened seal) | RT141, RT138, RT119 | None needed; do not copy PT's labels |
| C10 | **Ba-Ba id meanings.** ST's comments: 11780 = "leaps to the top and throws boulders", 11778 = end of boulder phase 1, 11779 = end of boulder phase 2. The cache names 11779 `COFFIN` and 11780 `DIGGING` | RT128 | The id transitions are what the client sees; keep them. Names are the cache's |
| C11 | **Het "seal" naming.** LD: `HET_GOAL` / `HET_GOAL_VULNERABLE`; PT: `HET_OBELISK`; cache: "Het's Seal (protected / weakened)". Same ids | RT105 | None |
| C12 | **Wardens P1 ids.** PT treats 11748 / 11749 as the inactive P1 forms, LD as the active `PHASE1` forms with 11746 / 11747 inactive. Both multiply by 0, so no effect on points. Cache: none of the four has ops | RT138 | None |
| C13 | **Path-level source.** LD reads HUD text widgets (481:45 / 49 / 51 / 53 / 55); the cache also has varbits 14376-14379 for the same data and no plugin reads them. Server must keep both consistent | RT18, RT30 | Populate both |
| C14 | **Raid-level source.** LD reads varbit 14380 in-raid and the party-board text `Raid Level: N` outside. PT reads `481:42` text. These must show the same number | RT29, RT33, RT17 | Populate all three |
| C15 | **Region list.** PH lists 13906, 15444, 15953, 15954, 15955 as ToA regions; no other plugin does. They exist in the cache (15953-15955 with no locs) | RT15 | Treat as unknown template squares; check what they hold before assuming they are unused |
| C16 | **`Challenge complete` shape.** LD's split regex wants `... Total: <time>` for rooms and `completion time:` for the Wardens; RL's tests show the Wardens line as `Duration: <t><br>Tombs of Amascut... challenge completion time: <t>`. So room lines carry `Total:` and the Wardens line carries `challenge completion time:`; the room-line text between the name and `Total:` is not shown by any test | RT49, RT50 | Reproduce LD's regex shape for rooms; copy RL's test strings for the Wardens |
| C17 | **`You enter the Tombs of Amascut`.** PT / LD points use `startsWith`; LD's target-time scan uses `equals`. If the real line has a period or suffix, the target-time scan would miss the "no target time" case (harmless: it then scans older lines) | RT45 | Send exactly `You enter the Tombs of Amascut` |
| C18 | **ST P3 -> enrage split.** ST sets `wardensP4EnrageHeal = true` unconditionally after its check, so the second heal logic is entered on every later heal. A client bug, not a server rule; the intent ("heals twice: at the start of P3 and at the enrage") is the useful fact | RT154 | Ignore the code, keep the two-heal fact |
| C19 | **Item ids via gameval.** RuneLite's gameval `ItemID` has nested `Cert` / `Placeholder` classes with ids one higher than the real items (e.g. `Placeholder.TOA_ZEBAK_FANG` = 27220 vs the real 27219). The plugins use the top-level constants (27214, 27219, 27221, 27223). Use the top-level ids | RT168 | Use cache `obj` ids |

## 13. Out of date relative to cache 2757 (date anchor 2026-11-03)

| O | Plugin | What is stale | Evidence |
|---|---|---|---|
| O1 | LD | `Invocation` enum has **44** entries; cache 2757 has **46** invocation structs. Blazing Tombs I and II (structs 5892 / 5893, +200 raid level each, require raid level 300; League / event only per the wiki) are unknown to LD. The 44 known ones keep their struct order, so presets should keep working (the widget order itself was not checked) | RT39, RT43 |
| O2 | LD | Akkha's **enrage shadow 11798** (and 11799) are not in `AkkhaShadowHealth`, so no HP overlay in the enrage phase (if the shadow appears then) | RT114 |
| O3 | MT | `Wave` exact-name match misses `Bloody Wave` 11739 | RT81 |
| O4 | MT | Last commit 2024-02-13; the DDR comment "as of 09/21/2022" shows the age of the P2 special rules; later balance updates are not reflected | RT139 |
| O5 | MT | Declared constants with cache names that do not fit: 2266 (`VORKATH_AREA_TRAVEL_SMALL` used as Kephri bomb projectile; unused), 1103 (`DARKBOW_SMOKE_ARROW_IMPACT` used as Ba-Ba slam), 2226 (`FX_WARDENS_BOMB03` called energy siphons in ST) | RT102, RT132, RT154 |
| O6 | PT | Unique / pet chance uses the pre-310/430 breakpoints; tier-2 baboons and two obelisk ids are missing from its factor table | RT172, RT166 |
| O7 | PT, ST, SU, TM | 2022-2024 code; all ids they use still exist in the cache with the intended names (checked), but they predate the points formula changes noted above | section 1 |
| O8 | SU | Tracks only Nectar, Tears and Ambrosia; the bag also holds Silk dressing, Blessed crystal scarab, Liquid adrenaline and Smelling salts | RT68 |
| O9 | RL | Core `GameTimer` models silk dressing 100 ticks and blessed crystal scarab 40 ticks as fixed; salts as varbit x 25 | RT69 |
| O10 | cache | Cache objects with no plugin coverage: NPCs 11697 Scarab (Scabaras room), 11705 / 11740 / 11741 Crocodile, 11727 Agile Scarab, 11735 / 11736 Jug, 11737 Boulder (Zebak safe spot), 11739 Bloody Wave, 11767 / 11768 Amascut phantoms, 11774-11777 boss phantoms (Wardens P3), 11806 Bank Camel | cache |

## 14. What was searched and what was not read

- Read at the level of every constant and event handler: LD (logic classes under `features/**` and `util/**`), MT
  (`detector/**`, `RaidState`, `Raider`, `ToaMistake`), PT (all classes), ST (constants and every `Subscribe`; the
  chat-message and infobox formatting was skimmed), PH (all), SU (all), TM (all). Skimmed or skipped: UI-only classes
  (overlay drawing, panels, config pages, `UpdateNotifier`, `ConfigMigrationService`, preset serialization, the Swarmer
  panel, splits overlays).
- RuneLite core `d62b158`: `grep` for `Amascut`, `TOA_`, `TombsOf`, regions 14160 / 13454 / 14672 / 15184 / 15696 gave
  the loot tracker, chat commands, timers and buffs, item stats, Discord activity, widget overlay positions
  (`WidgetOverlay`: `ToaLobby.CONTENT`, `ToaHud.CONTENT` are plain `TOP_LEFT` overlays) and the gameval tables. No core
  ToA plugin exists (no raid timer or boss helper).
- Wiki pages in `/tmp/claude-0/ref/wiki-toa/` were used only to check plugin constants (points table, unique
  formula, path-level and party-size scaling, invocation list, raid-mode ranges, loot module).
- Cache: `cache-dump config npc|loc|obj|varbit|varp|struct|hitmark`, `cache-dump interface 481|774|777|772|776`,
  `cache-dump map` for every region, and the generated symbols for seq / spotanim / varp / varc names. Script names are
  not in our symbols, so scripts 6579 / 6581 / 6585 / 6601 / 6617 / 6722 / 6729 / 6732 / 6770 are **not** verified.
- Other Plugin Hub ToA plugins seen in the manifest list (`/tmp/claude-0/ref/plugin-hub/plugins`) but **not read**:
  `toa-custom-presets`, `toa-gear-check`, `toa-invocation-display`, `toa-invocation-renamer`, `toa-invocations`,
  `toa-keris-reminder`, `toa-missing-pickaxe-indicator`, `toa-pot-sound`, `toa-spellbook-reminder`, `toaghost-plugin`,
  and `tombs-of-amascut-puzzle-helper` (cloned at `/tmp/claude-0/ref/plugins-toa-extra/boyd-puzzle-helper`, 2022,
  disabled for memory use; Scabaras light / matching / simon-says only; not read). `advanced-raid-tracker`
  (`capslock13`, `d66a707`, in `/tmp/claude-0/ref/plugins-cox/`) has ToA only in its UI/filters (no tracking code).
- Leads not followed: RSProx captures (`MRobertEvers/oldschool-clientc` `docs/rsprox-analysis/`) are the likely source
  for the missing attack cycles in section 11; `blert-io/plugin` / `blert-io/blert` were not checked for ToA.
