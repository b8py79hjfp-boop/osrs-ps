# Chambers of Xeric: timings and ids encoded in RuneLite and community plugins

Research notes, read 2026-11-08. Scope: every tick timing, counter, cooldown, template reference, layout rule and id mapping that the RuneLite core `raids` plugin
and community plugins hard-code for the Chambers of Xeric. Same use as `../tob/runelite-timings.md`: **facts only, no code copied**; use them in boss and room scripts
**only where the wiki and the existing room docs (`olm.md`, `tekton.md`, `vanguards.md`, `vasa.md`, `vespula.md`, `muttadiles.md`, `guardians.md`, `shamans.md`,
`mystics.md`, `puzzles.md`, `resources.md`, `lobby-rewards.md`) are silent** (BRIEF: most faithful to OSRS first). Plugin constants are client-side observations or heuristics,
not server code: each row says what the code *does*, and the confidence column says how much to trust it as a server rule. Where a room doc disagrees with a row, the room doc
wins or the point becomes `OPEN`. The cache side of the same ids is in `client-contract.md`.

## Sources

| Key | Plugin (hub name) | Repository @ commit | Root used in paths below | Licence |
|---|---|---|---|---|
| RL | RuneLite core `raids` plugin ("Chambers Of Xeric") and `InstanceTemplates` | `runelite/runelite@d62b158e83450b0708f053c753e3508c762a0803` (commit date 2026-11-03) | `runelite-client/src/main/java/net/runelite/client/plugins/raids/`; `runelite-api/src/main/java/net/runelite/api/InstanceTemplates.java` | BSD-2 |
| CA | CoX Additions (`cox-additions`, hub; Vanguards, Thieving, Timers) | `dey0/pluginhub-plugins@48a8c0a1ecbc40541e2e83d9f8793bf5640a63f5` | `src/main/java/de0/` | BSD-2 |
| CQ | CoX QoL / "CoX Additions" by Buchus (`cox-qol`, hub) | `MoreBuchus/buchus-plugins@64ee848dda5e3b443e739ada61af04e3d7d29c51` | `src/main/java/com/coxadditions/` | BSD-2 |
| AN | CoX Analytics (`cox-analytics`, hub) | `DangItOSRS/cox-analytics@60c3d373ef8956d60b549dc90bd2a37e7f843cb9` | `src/main/java/com/coxanalytics/` | BSD-2 |
| CS | Crab Stun Timers (`crab-stun-timers`, hub) | `AnkouOSRS/crab-stun-timers@84d0186027384e25c0af53684841d4bf699dc731` | `src/main/java/com/crabstuntimer/` | BSD-2 |
| CR | Crab Solver (`crab-solver`, hub) | `TheStonedTurtle/CrabSolver@503de15321ba896d720352fc77190fc9b17ac7aa` | `src/main/java/thestonedturtle/crabsolver/` | BSD-2 |
| RP | Raid Points Overlay (`raid-points-overlay`, hub) | `Trevor159/runelite-external-plugins@189b086af42404a12f052d223765eab1cef23243` | `src/main/java/trevor/raidpointsoverlay/` | BSD-2 |
| RS | Raid Speed Run Tracker (`raid-speed-run-tracker`, hub) | `garryhuang1/raid-speed-run-tracker@b383df0bd1c3d807e750626077c35a2a91dc7429` | `src/main/java/com/raidspeedruntracker/` | BSD-2 |
| TR | Tekton Reset Tracker (`tekton-reset-tracker`, hub) | `brodloy/tekton-reset-tracker@a1541c37c133addff3f32e33bad131409e2a08fa` | `src/main/java/com/tektonreset/` | BSD-2 |
| XK | xKylee "CoxHelper" (not on the hub; OpenOSRS) | `mithruir/open-osrs-plugins-xkylee@fc5f4ef4ae9f3dd942bd12267550be3b02279ead` | `coxhelper/src/main/java/net/runelite/client/plugins/coxhelper/` | **GPL-3.0: facts only** |
| SP | SpoonCoX plugins (`coxadditions`, `coxspoontimers`, `coxoverloadtimers`; not on the hub) | `9InchHog/spoonplugins-source@2ef42cbc9eec00a802af42fa6dc6106937799ca4` | `<plugin>/src/main/java/net/runelite/client/plugins/<plugin>/` | **GPL-3.0: facts only** |

Read but without a boss timing: `team-kindling-tracker` (`brodloy`, fdb32d6; kindling counts, the only constant is a 2-tick "drop window"), `lizardman-shaman-minion-alert` (`baloooouu`, 86e111c;
alerts on NPC id 6768 "Spawn", not CoX-specific), `AdvancedRaidTracker` (`capslock13`, d66a707; ToB / ToA only, **no CoX code**). Clones are under `/tmp/claude-0/ref/plugins-cox/`
(BSD) and `/tmp/claude-0/ref/plugins/` (XK, SP). The GitHub code-search API was unavailable (the session token is rejected for `search/code`); the plugins were found through the
repository search and the Plugin Hub manifests in `/tmp/claude-0/ref/plugin-hub/plugins`.

**Lineage.** CA's `CoxUtil` (the chunk table) is copied verbatim into RS, so those two are **one** source; SP's `coxspoontimers` is a fork of CA's `coxtimers` (same split / `to_mmss` structure), also one source for the
room-complete detection. XK (`coxhelper`) is independent; CQ and SP `coxadditions` share a name and several counters (`41`, `23`, `10`) and may be related; RL is independent of all. Confidence: **High** = two independent lineages agree, or one agrees with the cache / the wiki;
**Med** = one lineage, consistent with ids and cache names; **Low** = UI heuristic, a magic number with no stated meaning, or conflicting code.

**Counter idiom.** XK sets a counter inside the event that observes the animation (`handleNpcs` runs in `onGameTick`, decrementing first and then applying the animation rule), so "set to N" means an N-tick cycle between observed animations.
SP/CA count in `onGameTick` and reset on chat messages. The tables give the **effective cycle**.

**Modes.** N = Normal, CM = Challenge Mode, `all` = every mode the code handles. No plugin keys NPC ids by mode: **the same NPC ids are used in Normal and Challenge Mode** (TR states it; XK / CA / CQ never branch on it except the checks named in the rows).

## Raid-wide: layout, templates and the client's identification of rooms

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| varbit 5432 | "In the raid" flag (`RAIDS_CLIENT_INDUNGEON`); every plugin gates on it | RL `RaidsPlugin.java:267,365`; XK `CoxPlugin.java:431` (`Varbits.IN_RAID`); CA `coxvanguards/CoxVanguardsPlugin.java:66` | High | all |
| 16 slots, 8 per plane | The scouted raid is a 4 x 2 grid of rooms per plane, `rooms[16]`, index 0-7 on plane 3 (floor 1) and 8-15 on plane 2 (floor 2); room edge 32 tiles | RL `RaidsPlugin.java:104-107`, `Raid.java:37`, `RaidRoom.java:56` | High | N |
| lobby base | The instance's south-west lobby tile is found by the first wall object `BLACK_WALL` on plane 3; room origins are `gridBase + (dx * 32, -dy * 32)` | RL `RaidsPlugin.java:479-500,535-600` | High | N |
| chunk reference | `plane = v >> 24 & 3`, `x = (v >> 14 & 0x3FF) * 8`, `z = (v >> 3 & 0x7FF) * 8`, `rotation = v >> 1 & 3` for the 8x8 chunk under a room's tile, read from `getInstanceTemplateChunks()[plane][sceneX/8][sceneZ/8]` | RL `RaidsPlugin.java:641`, `InstanceTemplates.java:92-110` | High | all |
| template bands | (x 3264, width 96, height 32): LOBBY z 5184, START z 5696, END z 5152 (width 64), SCAVENGERS z 5216 (plane 0) / 5216 (plane 1), SHAMANS 5248 / MYSTICS 5248 plane 1 / GUARDIANS 5248 plane 2, VASA 5280 / TEKTON plane 1 / VESPULA plane 2, VANGUARDS 5312 / MUTTADILES plane 1, ICE_DEMON 5344 / TIGHTROPE plane 1 / CRABS plane 2, THIEVING 5376, FARMING 5440 (planes 0 and 1) | RL `InstanceTemplates.java:36-54` | High | all |
| template cells | Each band is three 32 x 32 variants at x 3264 / 3296 / 3328 = chunk x 102 / 103 / 104 (in units of 4 chunks): `CCW`, `THRU`, `CW`; extra bands FL_END1 (z 5120, x 3264), FL_END2 (z 5152, x 3264), FL_END3 (z 5152, x 3296), FL_START2 (z 5728); the Olm room is matched as the whole square (50, 89) on plane 0 | CA `util/CoxUtil.java:6-91,203`; RS `CoxUtil.java:7-90` (copy) | High (agrees with the cache, see `client-contract.md` §6) | all |
| exit side | `winding = ((x32 - 103) & 3)` (CCW 3, THRU 0, CW 1), `exitside = (winding + rotation) & 3`; "entrance angles: south = 0, west = 1, north = 2, east = 3" | CA `util/CoxUtil.java:223-232`, `coxthieving/ChestData.java:43` | Med | all |
| 26 fixed layouts | Normal-mode layouts are one of 26 codes `FLOOR1.FLOOR2 - #PATH1#PATH2` (the full list is below); a scouted room string is matched against them and accepted only if exactly one layout fits | RL `solver/LayoutSolver.java:56-84,101-127` (data: WooxSolo/raids-layout, MIT) | High | N |
| layout grammar | symbols S scavengers, F farming/resource, C combat, P puzzle, `#` start room, `¤` floor end; path letters N / E / S / W move the position by -4 / +1 / +4 / -1 on the 4-wide grid (a move that would leave the grid or cross the 3 -> 4 row seam is undone); after a floor's path the end room is placed and the next floor starts at `position + 8` | RL `solver/LayoutSolver.java:129-168,171-222` | High | N |
| combat cycles | The combat rooms of a raid follow the order of one of **four cyclic lists of 8**: (Tekton, Vasa, Guardians, Mystics, Shamans, Muttadiles, Vanguards, Vespula), (Tekton, Muttadiles, Guardians, Vespula, Shamans, Vasa, Vanguards, Mystics), (Vespula, Vanguards, Muttadiles, Shamans, Mystics, Guardians, Vasa, Tekton), (Mystics, Vanguards, Vasa, Shamans, Vespula, Guardians, Muttadiles, Tekton); two known rooms identify the cycle and the rest is filled in | RL `RotationSolver.java:42-47,87-130` | High (the plugin's output is relied upon by the community) | N |
| room types | `START # / END ¤ / SCAVENGERS S / FARMING F / COMBAT C / PUZZLE P / EMPTY`; combat = Tekton, Muttadiles, Guardians, Vespula, Shamans, Vasa, Vanguards, Mystics; puzzle = Crabs, Ice Demon, Tightrope, Thieving | RL `RaidRoom.java:31-52`, `RoomType.java:33-40` | High | N |
| CM layout | "All of the rooms are linear and use the same layout in each run" (three levels + Olm) | Wiki "Chambers of Xeric/Challenge Mode" lines 6-14 | High | CM |

The 26 layouts (WooxSolo's data, as listed by RL `LayoutSolver.java:58-83`; `.` separates the floors, `#` is the start room):
`FSCCP.PCSCF - #WNWSWN#ESEENW`, `FSCCS.PCPSF - #WSEEEN#WSWNWS`, `FSCPC.CSCPF - #WNWWSE#EENWWW`, `SCCFC.PSCSF - #EEENWW#WSEEEN`, `SCCFP.CCSPF - #NESEEN#WSWNWS`, `SCFCP.CCSPF - #ESEENW#ESWWNW`,
`SCFCP.CSCFS - #ENEESW#ENWWSW`, `SCFCPC.CSPCSF - #ESWWNWS#NESENES`, `SCFPC.CSPCF - #WSWWNE#WSEENE`, `SCFPC.PCCSF - #WSEENE#WWWSEE`, `SCFPC.SCPCF - #NESENE#WSWWNE`, `SCPFC.CCPSF - #NWWWSE#WNEESE`,
`SCPFC.CSPCF - #NEEESW#WWNEEE`, `SCPFC.CSPSF - #WWSEEE#NWSWWN`, `SCSPF.CCSPF - #ESWWNW#ESENES`, `SFCCP.CSCPF - #WNEESE#NWSWWN`, `SFCCS.PCPSF - #ENWWSW#ENESEN`, `SPCFC.CSPCF - #WWNEEE#WSWNWS`,
`SPCFC.SCCPF - #ESENES#WWWNEE`, `SPSFP.CCCSF - #NWSWWN#ESEENW`, `SCFCP.CSCPF - #ENESEN#WWWSEE`, `SCPFC.PCSCF - #WNEEES#NWSWNW`, `SFCCPC.PCSCPF - #WSEENES#WWWNEEE`, `FSPCC.PSCCF - #WWWSEE#ENWWSW`,
`FSCCP.PCSCF - #ENWWWS#NEESEN`, `SCPFC.CCSSF - #NEESEN#WSWWNE`. (Small and large pools are not distinguished by RL; the Wiki says the large pool always has 5 combat and 3 puzzle rooms per raid.)

## Raid-wide: chat, timer, points and party size

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| `The raid has begun!` | Friends-chat notification that starts the plugin timer (RL) and the splits (CA, SP) | RL `RaidsPlugin.java:108,295-299`; SP `coxspoontimers/CoxSpoonTimersPlugin.java:187` | High | all |
| `... level complete! Duration: </col><col=ff0000>m:ss` | Floor-complete message (friends-chat notification) carrying the floor duration; RL only matches `level complete!`, CA matches the whole `level complete! Duration: </col><col=ff0000>`; the "Upper" floor message differs (no total) | RL `RaidsPlugin.java:109,302-305`; CA `coxtimers/CoxTimersPlugin.java:101,112-123` | High | all |
| `Congratulations - your raid is complete!` | Raid end (stops the timer, Olm split) | RL `RaidsPlugin.java:110,307-312`; CA `CoxTimersPlugin.java:107-111` | High | all |
| `Your completed Chambers of Xeric count is: n` / `... Challenge Mode count is: n` | KC lines (run end markers) | SP `coxspoontimers/CoxSpoonTimersPlugin.java:193-200` | High | N / CM |
| varbit 6386 | The raid timer in **game ticks** (client-advanced, see `client-contract.md` §2.2); plugins read it as "the clock" | CA `CoxTimersPlugin.java:252`; AN `CoxAnalyticsPlugin.java:126,703`; RP `RaidPointsOverlayPlugin.java:39`; SP `CoxSpoonTimersPlugin.java:357-358` | High | all |
| varbit 5425 | Raid state; the HUD timer runs while `6386 > 0 && 5425 < 5`; RP re-runs client script 2289 every client tick to keep it moving while the widget is hidden | RP `RaidPointsOverlayPlugin.java:40,119-125` | High | all |
| script 1510 | The client's points / time widget refresh script (`RAID_POINT_WIDGET_SCRIPT`) | RP `RaidPointsOverlayPlugin.java:42,108-118` | High | all |
| varbit 5431 / varp 4609 | Total points / personal points; RL prints `Total points: x, Personal points: y (z%)` with `z = personal / (total / 100)` formatted `###.##` | RL `RaidsPlugin.java:314-337` | High | all |
| unique chance | `min(points / 867500, 0.657)` for personal and total points (1 % per 8,675 points, capped at 65.7 % = 570,000 points) | RP `RaidsPointsOverlay.java:122-125`; Wiki "Chambers of Xeric" (points section) | High | all |
| varbit 11866 | "Precise timing" setting: 0.6 s tick precision vs centiseconds in the game's own displays | AN `CoxAnalyticsPlugin.java:709`; SP `CoxSpoonTimersPlugin.java:363` | Med | all |
| varbit 5424 | Party size (real); 9540 is the **scaled** party size (`== 1` means solo for scaling purposes) | CS `CrabStunPlugin.java:74`; AN `CoxAnalyticsVarbits.java:5`; CA `coxvanguards/CoxVanguardsPlugin.java:168` | High | all |
| varbit 6385 | Challenge Mode flag | CA `CoxVanguardsPlugin.java:174`, `CoxTimersPlugin.java:165`; SP `CoxSpoonTimersPlugin.java:266` | High | CM |
| varbits 5417 / 5418 | Prayer-enhance cycles / overload cycles left (client-visible potion timers) | CQ `CoxAdditionsPlugin.java:275,287` | High | all |
| enhance duration | Ticks left = `varbit 5417 * floor(500 / (floor(prayer level / 2) + 31))` (the number of cycles is `floor(prayer / 2) + 31`) | CQ `CoxAdditionsPlugin.java:1464-1471,1486` | Med | all |
| overload duration | Ticks left = `varbit 5418 * 25` (a 5-minute overload = 500 ticks = 20 cycles of 25) | CQ `CoxAdditionsPlugin.java:1498`; SP `coxoverloadtimers/OverloadTimerPlugin.java:87` (5 min) | High | all |
| enhance dose tick | SP counts a prayer-enhance "dose" as 7 ticks on drinking and 6 afterwards (counter reload 6) | SP `coxadditions/CoxAdditionsPlugin.java:151,467-470,586-588` | Low | all |
| instance clock | A 4-tick room clock: SP seeds `instanceTimer = 5` on the raid-start / floor-start messages and counts `3..0` | SP `CoxAdditionsPlugin.java:163-164,501-505,629-633` | Low (UI heuristic) | all |
| HP regen cycle | 100 ticks per HP regeneration tick, 50 with the rapid-heal effect (used for the rope / health-regen overlay) | SP `CoxAdditionsPlugin.java:195-196,635-638` | Med (general OSRS rule) | all |
| HP hud varbits | The generic boss HP bar uses `HPBAR_HUD_HP` 6099 (current) / 6100 (base) with the varp `HP_HUD_NPC_ID`; CQ reads them for the Olm hands when the HUD npc is 7555 / 7553 | CQ `CoxAdditionsPlugin.java:1508-1514` | Med | all |

## Room-complete detection (what the server must make observable)

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| collision flag 0x100 | A room is "complete" when the tile of its blocker object loses the `0x100` collision flag (object gone). The blockers watched are the objects 26209 (shamans / thieving / guardians), 29741 (mystics), 29749 (tightrope), 29753-29757 (crabs), 29876 (ice demon), 30016 (Vasa), 30017 (Tekton / Vanguards), 30018 (Muttadiles), 30070 (Vespula) | CA `coxtimers/CoxTimersPlugin.java:84,182-198`; SP `coxspoontimers/CoxSpoonTimersPlugin.java:284-296` | High (the ids are all in the cache, see `client-contract.md` §5.1) | all |
| blocker spawn | The blockers are `GameObjectSpawned` events inside the instance (they are not in the static templates): the room is registered when its blocker spawns, with the room type taken from the chunk under it | CA `CoxTimersPlugin.java:182-216` | High | all |
| Olm start / phase end | `GameObjectSpawned` of loc 29881 (Olm head "ready") starts a phase, `GameObjectDespawned` of it ends it; the message `The Great Olm is giving its all. This is its final stand.` marks the head phase | CA `CoxTimersPlugin.java:126-128,157-161,212-217`; XK `CoxPlugin.java:457-495` | High | all |
| Muttadile tree | Tendrils loc 29767 spawning starts the Muttadile split (normal mode only); loc 30013 ("Meat tree", the empty-tree placeholder) spawning = tree cut | CA `CoxTimersPlugin.java:163-181` | Med | N |
| Ice demon pop | Graphics object 188 (smoke puff) in an Ice-demon template cell = the demon appears | CA `CoxTimersPlugin.java:220-243` | Med | all |
| room resets | Tekton: seeing any of NPC 7540-7545 means the Tekton room was reached; the same ids in CM; "progress past Tekton" = more than 24 tiles from where he died | TR `TektonResetTrackerPlugin.java:42-55,164` | Med | all |

## The Great Olm

NPC ids in plugins: head 7551 (spawning) / 7554, left claw 7552 / 7555 ("melee hand"), right claw 7550 / 7553 ("mage hand"); the visible parts are **game objects** 29880 / 29881 (head rising / ready), 29883 / 29884 (left), 29886 / 29887 (right) whose animation ids (7334-7383) are the cache's `OLM_*` seqs. All values below agree with the animations named in the cache.

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| 4 ticks | Head attack interval: after an attack the counter is reset to 4; every 4th attack advances the special cycle | XK `Olm.java:140,151-158,176` | High (Wiki attack speed 4, `olm.md`) | all |
| first attack: 6 / 8 | After the head rising animation (7335 or enraged 7383) the first attack comes 6 ticks later in the first phase, 8 ticks later in later phases | XK `Olm.java:267-272` | Med | all |
| special cycle 3 / 4 | The special attack index cycles 1..3 in normal phases and 1..4 in the final (head-only) phase; the left hand's cast animation resyncs it: crystals (7356 / 7366) -> 2, lightning (7358 / 7368) -> 3, portals (7359 / 7369) -> 4 in the final phase else 1, heal (7357 / 7367) -> 1 | XK `Olm.java:160-170,174-196,303-320` | Med | all |
| 45 ticks | Duration of the left claw's "clench" (crippled) after the animation 7360 (STUNNED); the plugin counts down from 45 | XK `Olm.java:57,78,117,123,210`, `OlmAnimation.java` (`LEFT_HAND_CRIPPLING` 7360, `_UNCRIPPLING` 7362 / 7364) | Med | all |
| burn 41 | Deep Burn ("burning overwhelming power") lasts 41 ticks; damage hits fall on the ticks outside the set {34, 33, 26, 25, 18, 17, 10, 9, 2, 1}, i.e. roughly every 8 ticks for 6 hits | XK `Victim.java:57`, `CoxOverlay.java:58,246-256`; SP `CoxAdditionsPlugin.java:246,451-453,530-533` | High (two lineages; resolves `OPEN-OL11` as "about 8 ticks, 6 hits"; the exact hit ticks are Low) | all |
| acid 23 | Acid ("smothered you in acid, it starts to drip off slowly") lasts 23 ticks | XK `Victim.java:58`; SP `CoxAdditionsPlugin.java:244,454-456,522-525` | High | all |
| teleport 10 | Swap-portal ("You have been paired with NAME! The magical power will enact soon...") enacts after 10 ticks; cancelled by "The teleport attack has no effect!" or a swap message | XK `Victim.java:59`, `Olm.java:40,245`; SP `CoxAdditionsPlugin.java:488-495,618-621` | High | all |
| crystal target 23 / 32 | "The Great Olm has chosen you as its target - watch out!" starts a crystal-mark countdown: SP uses 23 on the message and 32 as the initial/reset value | SP `CoxAdditionsPlugin.java:248,457-459,538-541,1128` | Low (conflicting values in one plugin) | all |
| orb 8 | After an Olm sphere (prayer-sap) message the incoming style is shown for 8 ticks (aggression = melee, magical power = mage, accuracy and dexterity = range) | SP `CoxAdditionsPlugin.java:472-483,597-606`; XK `CoxPlugin.java:181-192` | Med | all |
| heal pool 10 | A healing pool (projectile 1355 `OLM_HEALME_SPIT`, spot 1363) is tracked for 10 ticks | SP `CoxAdditionsPlugin.java:640-651` | Low | all |
| phase messages | `The Great Olm rises with the power of acid.` / `... crystal.` / `... flame.` (game messages) set the phase type; `The Great Olm's left claw clenches to protect itself temporarily.` marks the clench | XK `CoxPlugin.java:172-180`; SP `CoxAdditionsPlugin.java:449-450,463-465` | High | all |
| graphics / projectiles | Burn spot = `OLM_BURNWITHME_PL_SPOT` 1351 on the player; mage projectile 1341; range 1340; acid trail 1354 (`OLM_ACID_SPIT`); portal / swap graphic 1359-1362 (`OLM_PLAYERSWAP_*`); heal 1363. XK names them `OLM_BURN`, `OLM_MAGE_ATTACK`, `OLM_RANGE_ATTACK`, `OLM_ACID_TRAIL`, `OLM_TELEPORT`, `OLM_HEAL`; the numeric ids are my match to the cache names | XK `CoxPlugin.java:209-216,240`, `Olm.java:231-236`; CQ `CoxAdditionsPlugin.java:334` (orb ids 1341, 1343, 1345) | Med (match is [I]) | all |
| orb ids | The three styled projectiles the Olm head throws at a player: 1341 `OLM_WEAK_MAGE_PROJ`, 1343 `OLM_WEAK_RANGE_PROJ`, 1345 `OLM_WEAK_MELEE_PROJ` | CQ `CoxAdditionsPlugin.java:334`, `.../CoxAdditionsPlugin.java:1622-1634` | Med | all |
| phase count | Phases scale with party size: the plugins treat "7 or fewer players" as 2 pre-head hand phases and "8 or more" as 3; the Wiki: "at least four phases, with an additional phase for every 8 players" | SP `coxspoontimers/CoxSpoonTimersPlugin.java:235`; Wiki "Great Olm" line 204 | Low (plugin) / High (Wiki) | all |
| hand HP | The mage / melee hand default HP shown is 600 (a placeholder reset value, not a rule); the live value is `HPBAR_HUD_HP` 6099 | CQ `CoxAdditionsPlugin.java:318-326,482-487,1508-1514` | Low | all |
| head position | The head is kept in the cave placeholder pair: the NPC 7554 form means the head is up; `olmTile` is set only for the spawning form | CQ `CoxAdditionsPlugin.java:1203-1213,1278-1286,1306` | Low | all |

## Tekton, Guardians and the other combat rooms

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| Tekton autos 4 / 3 | Counter set to **4** after an `AUTO1..3` (and the three enrage variants) animation, **3** after a `FAST_AUTO` animation; the plugin marks Tekton melee only. **Conflicts with `tekton.md` (attack speed 3, stat regen 1 per 15 ticks): the room doc wins** | XK `CoxPlugin.java:369-381` | Low | all |
| Tekton anvil 15 | After the anvil animation the next attack counter is 15 ticks | XK `CoxPlugin.java:383-388` | Low | all |
| Tekton 27 / 47 | `tektonAttackTicks` is 27 on spawn and 47 on the anvil animation and counts down while "active"; the meaning is not stated (likely time until he returns to the anvil) | XK `CoxPlugin.java:265,384,423-426` | Low | all |
| Tekton NPCs | Waiting 7540 (at the anvil), walking 7541, fighting 7542, walking enraged 7543, fighting enraged 7544, hammering 7545; anvil loc 29867; the same ids in CM | TR `TektonResetTrackerPlugin.java:42-53`; XK `CoxPlugin.java:259-265` | High | all |
| Guardians 5 | Counter set to **5** when a guardian plays animation 430; **conflicts with `guardians.md` (attack speed 4)**, treat as Low | XK `CoxPlugin.java:83,392-402` | Low | all |
| Muttadile 4 | Muttadiles (7561-7563) and Guardians (7569-7572) are tracked as melee NPCs; no timing is applied to Muttadiles (the room doc has attack speed 4) | XK `CoxPlugin.java:267-272,300-311` | Low | all |
| Vanguards 4 | A 4-tick cycle counter runs while any of the three attacking forms (7527 melee, 7528 range, 7529 mage) exists and resets when they are all down (7526) | SP `CoxAdditionsPlugin.java:175-176,405-423,592-598` | Med | all |
| Vanguard forms | 7525 dormant, 7526 walking / "down", 7527 melee, 7528 ranged, 7529 mage; hits are healed back (`HEAL` hitsplat) | CA `CoxVanguardsPlugin.java:55-65,140-165`; CQ `CoxAdditionsPlugin.java:368-374` | High | all |
| Vanguard solo base HP | `180 * combat level / 126`, times 3/2 in Challenge Mode, used as the per-vanguard HP for a solo player; a downed vanguard (form 7526) regains **1 HP per tick** in the plugin's model | CA `CoxVanguardsPlugin.java:172-177,114-117` | Med (matches the +50 % CM rule) | solo, N / CM |
| Vasa crystal 67 | While Vasa is in the healing form (NPC 7567) the crystal countdown is 67 ticks; it reloads to 67 when Vasa walks (7566) and plays animation 7409 (`VASA_STUN_SPAWN`) | SP `CoxAdditionsPlugin.java:572-580` | Med | all |
| Meat tree 5 / 6 | The Muttadile tree is chopped on a **5-tick** cycle (6 after a successful "You hack away some of the meat." message); tree locs 30012 (full, "Rocks") / 30013 (empty, "Meat tree") | SP `CoxAdditionsPlugin.java:486-487,609-613,819` | Med | all |
| Herb patch 16 | The growth of a planted herb patch is tracked as 16 ticks per stage in SP (loc ids 29997-30011) and as **11** in CQ (`coxHerbTimer = 11`): **conflict**, both Low | SP `CoxAdditionsPlugin.java:254,545-558,657`, `overlays/CoxAdditionsOverlay.java:111-126`; CQ `CoxAdditionsPlugin.java:435,753-775,962-990` | Low | all |
| Crab stun | Jewelled-crab stun length (graphic 245) by **party size (varbit 5424)**: 1 -> **50 ticks**, 2-3 -> **30**, 4-5 -> **20**, 6 or more -> **10** | CS `TeamSize.java:4-7`, `CrabStunPlugin.java:91,123-140` | High (the room doc `puzzles.md` should be checked) | all |
| Crab crystals | Crystal colour -> skill that solves the room: yellow = Magic, cyan = Attack, magenta = Ranged, black = passive; tile marks for two template cells (regions 13395 and 13139, i.e. the CW cell of the crabs band and the CCW cell) | CR `CrabCrystal.java:32-37`, `CrabTile.java:36-58` | Med | all |
| Thieving chests | Closed chest 29742, poison/open chest 29743, grubs chest 29745 (`RAIDS_THIEVINGCHEST_*`); the plugin keeps, per cell and per rotation, **10 candidate sets of 4 chests**; the chest coordinates of the CCW (64) and THRU (66) cells equal the static cache (the CW table does not) | CA `coxthieving/CoxThievingPlugin.java:54-56`, `ChestData.java:5-106` | High (chest coordinates), Low (what the 4 chests mean) | all |
| Ice demon | Smoke-puff graphics 188 in the Ice-demon cell marks the demon spawning; braziers 29747 (unlit) / 29748 (lit); kindling counts are client-side only | CA `CoxTimersPlugin.java:220-243`; `team-kindling-tracker` (no timing) | Med | all |
| Shaman / Mystic / tightrope | No timing is encoded in the plugins read (only the blocker ids above) | | | |

## Cross-check against the cache and the room docs
- The Olm animation ids that XK / SP hard-code (7334-7383, 7355-7370 for the left hand) are exactly the cache's `Seqs` `OLM_HEAD_*` / `OLM_HAND_LEFT_*` ids (`client-contract.md` §1.6); the NPC ids 7550-7555, 7525-7529, 7540-7545, 7561-7564, 7565-7568, 7569-7572 equal the `RAIDS_*` / `OLM_*` NPC symbols.
- The room-complete blocker object ids all exist in the cache (`client-contract.md` §5.1) and none is in the static templates, which agrees with the plugins treating them as spawn events.
- The chunk table of CA / RS equals the template bands of RL `InstanceTemplates`, extended by FL_END1 (z 5120) and FL_START2 (z 5728), both present in the cache.
- Items for the room docs to reconsider: `OPEN-OL11` (Deep Burn: 41 ticks, 6 hits, about 8 ticks apart), `OPEN-OL10` (acid duration: 23 ticks on the player), the crab stun table, and the Tekton / Guardians attack speeds (conflicts above).

## Conflicts and holes
1. **Tekton attack cycle**: XK 4 / 3 vs the wiki 3 (`tekton.md`). Not a server rule.
2. **Guardians**: XK 5 vs the wiki 4 (`guardians.md`).
3. **Herb patch growth**: SP 16 vs CQ 11.
4. **Crystal-mark countdown**: SP 23 vs 32.
5. The plugins give no timing for Vasa's (other than the crystal), Vespula's, the Shamans', the Mystics', the Ice demon's or the Muttadiles' attacks; those rely on the wiki room docs.
6. The GitHub code search could not be used (token rejected), so this list is limited to the plugins reachable through the repository search and the Plugin Hub manifests; there may be other CoX plugins with timing constants not examined here.
