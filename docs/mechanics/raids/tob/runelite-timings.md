# Theatre of Blood: timings encoded in RuneLite plugins

Research notes, read 2026-11-08. Scope: every tick timing, attack cycle, cooldown, counter and id mapping that
RuneLite-ecosystem plugins hard-code for the six ToB rooms. Use these for boss scripts **only where the wiki is silent**
(BRIEF: most faithful to OSRS first). Plugin constants are client-side observations or heuristics, not server code:
each row says what the code *does*, and the confidence column says how much to trust it as a server rule. Room docs
(`maiden.md`, `bloat.md`, `nylocas.md`, `sotetseg.md`, `xarpus.md`, `verzik.md`) remain the authority. Where they
disagree with this file, they win, or the point becomes `OPEN`.

## Sources

| Key | Plugin (hub name) | Repository @ commit | Root used in paths below |
|---|---|---|---|
| BL | Blert (`blert`, hub) | `blert-io/plugin@3e8bf4e13fc0145fe6b90ba2eae95d2b5f1c2c16` | `src/main/java/io/blert/challenges/tob/` |
| TQ | Theatre of Blood QoL (`tobqol`, hub) | `damencs/tob-qol@3715d6ae24547fa87dc7ed82665e37b632407ba7` | `src/main/java/com/tobqol/` |
| MT | ToB Mistake Tracker (`tob-mistake-tracker`, hub) | `QuestingPet/TobMistakeTracker@8db1f59e1855bc96137e216521cf2ce7b5e29e28` | `src/main/java/com/tobmistaketracker/detector/` |
| NY | Nyloer (`nyloer`, hub) | `EIKOOT/nyloer@9b400ec733fb7d83c261affee4b8edf4feb3fb5f` | `src/main/java/com/nyloer/` |
| TU | ToB Utilities (`tob-utilities`, hub) | `NCG-RS/TobUtilities@e52a0bf8aac0a8ceb4bbaa187531bc34f9bc3e34` | `src/main/java/com/tobutilities/` |
| NPB | Nylo Progress Bar (`nylo-progress-bar`, hub) | `stadtehrsoftware/nylo-progress-bar@5e845dfafe2bcf72bfd4bf1f35371e05935bdee6` | `src/main/java/com/legionlexi/nyloprogressbar/` |
| SP | SpoonTob (not on the hub; unofficial client) | `9InchHog/spoonplugins-source@2ef42cbc9eec00a802af42fa6dc6106937799ca4` | `spoontob/src/main/java/net/runelite/client/plugins/spoontob/rooms/` |
| XK | xKylee "Theatre" (not on the hub; OpenOSRS) | `mithruir/open-osrs-plugins-xkylee@fc5f4ef4ae9f3dd942bd12267550be3b02279ead` | `theatre/src/main/java/net/runelite/client/plugins/theatre/` |
| RL | RuneLite core gamevals (names only) | `runelite/runelite@27fcc1da7757bbe25c49856b51d3a6fdf61db7ee` | `runelite-api/src/main/java/net/runelite/api/gameval/` |
| BW | Blert server/web (not a plugin; corroboration only) | `blert-io/blert@8997271b2798670058bb2780f72bcae3e5d06e5a` | repo root |

Also cloned and read, but they add no boss timing: `learner-tob` (macflag@7309102), `tob-qol-improvements`
(WLoumakis@9ccd0b9), `tob-infoboxes`, `tob-hm-timer`, `xarpus-exhumed-counter` (only counts exhumeds),
`nylo-death-indicators`, `furry-nyloer`, `ev-bloat` (cosmetic model). The `spoon-*` / `whospoonedit` hub plugins
track loot luck, not ToB mechanics. Clones are under `/tmp/claude-0/ref/plugins/`.

**Lineage.** SP is a fork of XK (same variable names, same P3 counter scheme), so the two are **one** source. BL was
written independently. Confidence: **High** = BL and SP/XK (or a second independent plugin) agree; **Med** = one
lineage, consistent with ids and cache names; **Low** = UI heuristic, a magic number with no stated meaning, or
known-buggy code.

**Counter idiom.** Most SP/XK counters are set in an event handler (AnimationChanged, NpcChanged, ProjectileMoved,
which run before GameTick in the client) and then decremented in `onGameTick` of the same tick. So "set to N+1" means
an N-tick cycle. The tables give the **effective cycle**, with the raw literal in brackets.

**Modes.** E = Entry (Story), N = Normal, H = Hard, `all` = every mode the code handles.

## Raid-wide

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| 4-tick instance clock | Every ToB room runs on a per-instance 4-tick cycle. TQ sets `tickCycle = 2` the tick a player crosses the next room's entry line ("[2b, 3a]"), then `++tickCycle % 4` each tick | TQ `InstanceService.java:95-125` | Med | all |
| Nylo clock seed 3 | SP starts the Nylocas instance timer at `instanceTimer = 3` when a player stands on the bloat-room exit tile; `(t+1) % 4` per tick | SP `Nylocas/Nylocas.java:892,914` | Med | all |
| Xarpus clock seed 2 | Same idea: the timer seeds at 2 on the Sotetseg-room exit tiles | SP `Xarpus/Xarpus.java:421-424`; XK `Xarpus/Xarpus.java:296` | Med | all |
| Varbit 6447 | Room state (0 idle, 1 fight active, 2 used for Xarpus start) | TQ `api/game/RaidConstants.java:15`; SP `Bloat/Bloat.java:37` | High | all |
| Varbit 6448 | Boss HP bar value (0..1000 scale for Xarpus; BL reads it for every boss) | TQ `api/game/RaidConstants.java:16`, `rooms/xarpus/XarpusHandler.java:158` | High | all |
| Player-rhythm metronome | Per-room metronome lengths: Maiden 0 (off), Bloat 5, Nylocas 4, Sotetseg 4, Xarpus 4, Verzik 7 | TU `common/enums/Region.java:5-10` | Low (player cadence aid, not a boss rule) | all |

HP per party size (BL `TobNpc.java:38-177`; arrays are scale 1-3 / 4 / 5; Entry multiplies the single value by scale
except for Nylocas): Maiden 2625/3062/3500 (E 500×n); Matomenos 75/87/100; Blood spawn 90/105/120; small nylo 8/9/11,
big 16/19/22; Prinkipas (H) 300/350/400; Vasilias 1875/2187/2500 (E 360); Sotetseg 3000/3500/4000 (E 560); Verzik P1
1500/1750/2000, P2 2437/2843/3250, P3 2437/2843/3250 (E 240/320/320); P2 Matomenos 150/175/200; P2 small nylos 11.
These belong to the room docs. They are listed here only so the room docs can cross-check them.

## The Maiden of Sugadinti

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| anim 8091 | Blood throw (RL name `MAIDEN_ATTACK_BLOOD`) | BL `rooms/maiden/MaidenDataTracker.java:52`; TQ `rooms/maiden/commons/MaidenConstants.java` (`MAIDEN_BLOOD_TOSS_ANIM`); RL `AnimationID.java:8096` | High | all |
| anim 8092 | Regular attack (BL/TQ "auto"; RL name `MAIDEN_ATTACK_SPECIAL`) | BL `MaidenDataTracker.java:53`; RL `AnimationID.java:8097` | High (id) | all |
| anim 8093 / 8097 | Maiden death; Matomenos death (TQ `RED_CRAB_DEATH_ANIM`, SP crab-death hook) | TQ `MaidenConstants`; SP `Maiden/Maiden.java:510` | High | all |
| 10 ticks | Attack speed; the counter resets to 10 on each attack animation and on spawn | SP `Maiden/Maiden.java:92,175`; XK `Maiden/Maiden.java:114,166` | High (matches wiki speed 10) | E,N |
| HM speed-up, floor 3 | Hard only (`id >= 10822`): when an attack animation comes with `ticksUntilAttack > 1` left, `attSpd -= (ticksUntilAttack-1)`, clamped to ≥3. The next attack is due in `attSpd` ticks (raw `attSpd+1`). In effect the cadence ratchets down every time Maiden attacks early (crabs reaching her) | SP `Maiden/Maiden.java:535-542` | Med (single source; mechanism inferred from observation) | H |
| proj 1578 → spot 1579 | Blood throw projectile, then the lingering blood graphic | TQ `MaidenConstants`; MT `MaidenMistakeDetector.java:60`; RL `SpotanimID.java:1585-1586` | High | all |
| 65 + 15·d cycles | Blood projectile flight: 65 client cycles at 1 tile from the true hitbox, +15 per extra tile; the 2 extra splats on the furthest player are +25 cycles (always land 1 tick later). Land tick = `floor(remainingCycles / 30)` | MT `MaidenMistakeDetector.java:33-42,132-134` | Med | all |
| 11 ticks | Blood splat stays active exactly 11 ticks after landing | MT `MaidenMistakeDetector.java:44,65,183` | Med | all |
| 1 + 2 splats | One splat per player's tile, plus 2 extra on the furthest player | MT `MaidenMistakeDetector.java:31-33` | Med | all |
| obj 32984 | Blood-spawn trail tile (damaging) | BL `MaidenDataTracker.java:50`; MT `MaidenMistakeDetector.java:59` | High | all |
| 70 / 50 / 30 % | Crab-spawn thresholds. Maiden's NPC id steps per spawn: 8360→8361 (70), 8362 (50), 8363 (30); E 10814…, H 10822… (BL: id offset <2 = 70s, ==2 = 50s, else 30s) | SP `Maiden/Maiden.java:183,252-259,335-347`; BL `rooms/maiden/MaidenDataTracker.java:233-243`, `CrabSpawn.java:26-30` | High | all |
| 10 spawn tiles + 10 "scuffed" | N1-N3, N4 inner/outer, S1-S3, S4 inner/outer (world 3173..3185 × 4436/4456) and a +1/+1 "scuffed" variant of each | BL `rooms/maiden/MaidenCrab.java:165-185`; SP `Maiden.java:199-246`; TU `maiden/MaidenConstants.java:8-19` | High | all |
| NPC-index scuff | TU warns of a scuff risk from Maiden's NPC index: <58000 none … ≥63500 very likely (processing-order artefact) | TU `maiden/MaidenHandler.java:79,118-130` | Low | all |
| 2-tick delay | SP starts counting crab freeze timers 2 ticks after the crabs spawn (`nyloSpawnDelay = 2`); `crabTicksSinceSpawn = 8` drives a UI | SP `Maiden.java:98,192-193,441-449` | Low | all |
| Party base HP | 1-3: 2625, 4: 3062, 5: 3500 (threshold HP = floor(base × pct)) | SP `Maiden.java:87-89,636-646` | High | N,H |

## Pestilent Bloat

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| anim 8082 | "Down"/sleep (RL `TOB_BLOAT_SLEEP`); 8080 ready, 8081 walk, 8083/8084 flies large/small, 8085 death, 8089 stunned | TQ `rooms/bloat/commons/BloatConstants.java` (`DOWN_ANIM`); RL `AnimationID.java:8085-8094` | High | all |
| 32 ticks | Length of a down | BW `web/app/utils/stage-state/npc-state.ts:31` (`BLOAT_DOWN_TICKS`) | Med (BW is server-side analysis, not a plugin) | all |
| 34 ticks | Overhead between downs that is not walking: 32 down + 1 stand-up animation + 1 stationary | BW `common/migrations/backfill-bloat-downs.ts:14-16` | Med | all |
| 26..34 | SP/XK down-state machine: while animating, `downCount < 26` = down, `25 < downCount < 35` = "warn" (about to stand) | SP `Bloat/Bloat.java:302-308`; XK `Bloat/Bloat.java:178-184` | Med (agrees with 32 + stand-up) | all |
| 38 ticks | "Past threshold" state (4) once an up-timer reaches 38. SP resets the timer on **every** Bloat animation change and on room-state varbit changes; XK resets it only on spawn / door varbit | SP `Bloat.java:132,157-159,287,298`; XK `Bloat.java:89,125,163,174,208` | Low (meaning not stated; see conflicts) | all |
| model height 568 | Down pose detected by model height | SP `Bloat.java:310`; XK `Bloat.java:186` | Low | all |
| spot 1570-1573 | Falling hands/feet (RL `TOB_BLOAT_FALLING_FLESH1..4`) | BL `rooms/bloat/BloatDataTracker.java:40-41`; RL `SpotanimID.java:1577-1580` | High | all |
| spot 1576 | Blood squirt where a hand lands; a player on that tile is stunned (RL `TOB_BLOAT_BLOOD_SPLAT`; stun spot 1575) | MT `BloatMistakeDetector.java:26-28,41`; RL `SpotanimID.java:1582-1583` | High | all |
| 3 ticks | A hand hits the ground 3 game ticks after its graphic spawns | MT `BloatMistakeDetector.java:26` | Med | all |
| 4 ticks | Hand marker lifetime / "hands falling" window | SP `Bloat.java:62,142,268-270`; XK `Bloat.java:139` | Med | all |
| tank/chain/floor ids | Tank 32957/32955/32959/32960/32964/33084, top 32958/32962/32964/32965/33062, chains 32949-32954/32970, floor 32941/32942/32944/32946/32948 | TQ `BloatConstants.java`; SP `Bloat.java:58-60` | High | all |
| stomp safe distance | Safe-spot helper: distance from the destination ≥7 or ≤4 counts as safe (N/S paths: 8 or 3); the side offset is 2..4 | SP `Bloat/stomp/def/DistanceInfo.java:6-33`, `IPath.java:14-15,38` | Low (UI geometry) | all |

No plugin encodes fly (spot 1568/1569) timing or a Hard-mode difference for Bloat.

## Nylocas (waves and Vasilias)

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| 31 waves | Total waves | BL `rooms/nylocas/NylocasDataTracker.java:56`; TQ `NylocasConstants` (`NYLOCAS_WAVES_TOTAL`); SP `NylocasWave.java:8`; NPB `WaveProgress.java:6` | High | all |
| 4 ticks | Base spawn cadence; waves only spawn on the room's 4-tick clock | BL `NylocasDataTracker.java:57`; SP `NylocasWave.java:128` (default `waveDelay` 4); NY `stats/StatsHandler.java:330` | High | all |
| natural delays | Ticks from wave *w* to wave *w+1*, w = 1..30: **4,4,4,4,16,4,12,4,12,8, 8,8,8,8,8,4,12,8,12,16, 8,12,8,8,8,4,8,4,4,4** (identical in all three) | BL `NylocasDataTracker.java:58-60`; SP `NylocasWave.java:11-41` (2nd ctor arg); NY `stats/StatsHandler.java:44-80` | High | all |
| stall = +4 | If the room is at cap when a wave is due, the wave slips one 4-tick cycle (repeats) | BL `NylocasDataTracker.java:117-126`; SP `Nylocas.java:799-803` (raw 3) | High | all |
| cap 12 → 24 | Alive cap before / from wave 20 | BL `NylocasDataTracker.java:55,90-93`; SP `Nylocas.java:314-320`; NY `StatsHandler.java:96-97,195` | High | E,N |
| cap 15 → 24 | Hard-mode cap before wave 20 is 15 | BL `NylocasDataTracker.java:90-91` | Med (see conflicts) | H |
| Prinkipas = 3 | The HM demi-boss counts as 3 nylos toward the cap | BL `NylocasDataTracker.java:86`; SP `Nylocas.java:414-418` | High | H |
| prince waves 10/20/30, 16 ticks | HM: the Prinkipas appears on waves 10, 20 and 30; the next wave is due 16 ticks (4 cycles) later | BL `NylocasDataTracker.java:96-99,300-303` | Med | H |
| 52 ticks | Wave nylo lifespan (despawn / "alive" timer) | SP `Nylocas/NyloInfo.java:8`; XK `Nylocas/Nylocas.java:342`; NY `npc/NyloerNpc.java:60` | High | all |
| split 6 / 4 | Big death with the standard anim (8005 melee / 7991 mage / 7998 range) → splits after 6 ticks; with the detonate anim (8006 / 7992 / 8000) → 4 ticks | SP `Nylocas.java:924-936`; RL `AnimationID.java` (`TOP_SPIDER_*_DEATH` / `_DEATH_DETONATE`) | Med | all |
| boss +16 ticks | Vasilias spawns 16 ticks after the last wave nylo dies, rounded up to the 4-tick clock; boss duration is also rounded up to 4 | NY `stats/StatsHandler.java:296-312` | Med | all |
| spawn tiles | Lanes (region-local): W (17,24)(17,25) + big (18,25); S (31,9)(32,9) + big (32,10); E (46,24)(46,25) + big (47,25). World: E 3310,4248/4249 (+3309,4248 big), W 3281,4248/4249, S 3295/3296,4233 | TQ `NylocasConstants` (`NYLOCAS_VALID_SPAWNS`); SP `NylocasSpawnPoint.java:9-17`; BL `rooms/nylocas/SpawnType.java:35-41`; NPB `WaveProgress.java:51-56` | High | all |
| wave table | 31 lane compositions (key E0 E1 S0 S1 W0 W1; i/t/h melee/range/mage, uppercase = big): 1 `_i_ht_`, 2 `t_i__h`, 3 `_h_ti_`, 4 `i_H__t`, 5 `_h_iT_`, 6 `I_t__h`, 7 `_iTh__`, 8 `t_i_H_`, 9 `_h__T_`(+W melee small, SP), 10 `Tttttt`, 11 `hhhhH_`, 12 `iiI_ii`, 13 `I_itht`, 14 `T_thih`, 15 `thH_ti`, 16 `h_i__t`, 17 `H_H_H_`, 18 `T_T_T_`, 19 `H_H_H_`, 20 `I_H_I_`, 21 `ttiihh`, 22 `H_htI_`, 23 `H_T_th`, 24 `I_H_T_`, 25 `H_T_I_`, 26 `H_I_H_`, 27 `H_I_H_`, 28 `thhiti`, 29 `thI_ti`, 30 `H_hiT_`, 31 `thhiti`. HM and Entry use the same compositions (SP tables) | BL `rooms/nylocas/NyloWave.java:54-81`; SP `NylocasWave.java:11-107` (cross-checked by script; see conflicts) | High | all |
| aggro ids | Aggressive variants: N 8348-8353, H 10797-10802, E 10780-10785 | TU `nylocas/NylocasConstants.java:7`; BL `TobNpc.java:56-98` | High | all |
| boss anims | 8004 melee, 7999 range, 7989 mage (RL `TOP_SPIDER_*_ATTACK`) | BL `NylocasDataTracker.java:62-64`; TQ `NylocasConstants` | High | all |
| 4 ticks | Vasilias attack speed (raw 5 on the attack anim) | XK `Nylocas/Nylocas.java:482-490,619` | Med | N |
| 10 ticks | Vasilias style-switch interval (raw 11 on NpcChanged; 10 on a detected id change) | SP `Nylocas.java:541-546,872-879`; XK `Nylocas.java:507-508,620` | High | N,H |
| 15 ticks | Same interval in Entry (ids 10787-10789) (raw 16) | SP `Nylocas.java:453,543,876` | Med | E |
| first attack 2 | After a switch XK sets the attack counter to raw 3, so the first attack comes 2 ticks after the transform | XK `Nylocas.java:507` | Low | N |
| Prinkipas switch 10 | HM demi-boss style switch counter (raw 10 at spawn) | SP `Nylocas.java:406-410,887-889` | Low | H |
| boss ids | Vasilias "dropping" / melee / mage / range: E 10786-10789, N 8354-8357, H 10807-10810; Prinkipas H 10803-10806; pillars 10790 / 8358 / 10811 | TQ `rooms/nylocas/commons/NylocasConstants.java` | High | all |

## Sotetseg

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| 5 ticks | Attack speed (raw 6 on the attack anim) | BL `rooms/sotetseg/SotetsegDataTracker.java:58,220`; SP `Sotetseg/Sotetseg.java:222-223`; XK `Sotetseg/Sotetseg.java:154-158` | High | all |
| anim 8138 / 8139 | Melee / ball attack (RL `TOB_SOTETSEG_ATTACK_MELEE` / `_RANGED`) | BL `SotetsegDataTracker.java:51-52`; TQ `SotetsegConstants`; RL `AnimationID.java:8143-8144` | High | all |
| proj 1606 / 1607 | Magic orb / ranged orb (RL `TOB_SOTETSEG_MAGING` / `_RANGING`) | SP `Sotetseg.java:44-45`; TQ `SotetsegConstants`; RL `SpotanimID.java:1613-1614` | High | all |
| proj 1604 | Death ball (RL `TOB_SOTETSEG_SHAREDATTACK`); chat "A large ball of energy is shot your way..." | BL `SotetsegDataTracker.java:53`; TQ `SotetsegConstants` | High | all |
| 10 orbs → death ball | SP counts down from 10 for each **magic-orb projectile** launched from Sotetseg's tile during anim 8139; it resets to 10 on a death ball | SP `Sotetseg.java:84,164,199-209` | Med (counts projectiles, not attacks) | all |
| 10 ticks | Next regular attack 10 ticks after the death ball is launched (raw 11 on the projectile, 10 in onGameTick) | SP `Sotetseg.java:199-202,303-307`; XK `Sotetseg.java:219` | Med | all |
| ~15 ticks | Death ball travel time ("takes 15 ticks to land"; BL waits 20 as a buffer) | BL `SotetsegDataTracker.java:257-260` | Med | all |
| 66 % / 33 % | Maze procs. The HP varbit reads ~666 / ~333 at the proc; while in the maze it is locked at 2 (1st) / 1 (2nd) | BL `SotetsegDataTracker.java:311-321` | High | all |
| anim 1816 | Player teleport into the maze (RL `HUMAN_TELEPORT_OTHER_IMPACT`) | BL `SotetsegDataTracker.java:50,232-251`; TQ `SotetsegConstants` (`MAZE_TELE_ANIM`) | High | all |
| 4 ticks | A player who "dies" on the maze-proc tick is saved; BL treats the party as invulnerable for 4 ticks | BL `SotetsegDataTracker.java:236-249` | Med | all |
| NPC ids | Idle (maze) 8387 / E 10864 / H 10867; active 8388 / 10865 / 10868 | BL `TobNpc.java:109-116`; SP `Sotetseg.java:155-160,259` | High | all |
| maze objects | Ground objects 33033 (disabled overworld), 33034 (inactive / underworld), 33035 (active path; TQ adds 41750-41753); underworld portal 33037; rocks 33063-33065; rag graphic 505 | BL `SotetsegDataTracker.java:54-57`; TQ `SotetsegConstants` | High | all |
| regions | Overworld 13123, shadow realm (underworld) 13379; maze SW corner overworld (9,22), underworld (42,31) | SP `Sotetseg.java:52-57`; XK `Sotetseg.java:65-87` | High | all |

No plugin encodes a Hard-mode Sotetseg timing difference.

## Xarpus

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| NPC ids | Idle 8338 / E 10766 / H 10770; P1 (exhumeds) 8339 / 10767 / 10771; P2+P3 8340 / 10768 / 10772; 8341 / 10769 / 10773 (TQ: dead; SP: "P3") | TQ `rooms/xarpus/commons/XarpusTable.java:43-46`; SP `Xarpus/Xarpus.java:42-53` | High (ids) | all |
| anim 8059 | Ranged spit (RL `TOB_XARPUS_ATTACK_RANGED`); 8060 absorb (heal), 8061 fly up, 8062/8063 death | RL `AnimationID.java:8064-8068` | High (names) | all |
| 9 ticks | First counter value on spawn / phase start | SP `Xarpus.java:199`; XK `Xarpus/Xarpus.java:146,166` | Med | all |
| 4 ticks | P2 poison-spit cadence (counter resets to 4 at ≤0) | SP `Xarpus.java:329-335`; XK `Xarpus.java:259-265` | High (two plugins, but one lineage) | all |
| P3 trigger | P3 starts when Xarpus has overhead text (the screech); TQ also accepts HP varbit 6448 ≤ 250 (25 %) | SP `Xarpus.java:315`; XK `Xarpus.java:241`; TQ `rooms/xarpus/XarpusHandler.java:158` | High | all |
| 9 then 8 ticks | P3 turn/stare: first turn 9 ticks after the screech, then every 8 ticks | SP `Xarpus.java:315-325`; XK `Xarpus.java:241-257` | Med | all |
| obj 32743 | Exhumed ground object | SP `Xarpus.java:41`; XK `Xarpus.java:97`; TQ `XarpusConstants` (`ObjectID.EXHUMED`) | High | all |
| exhumed lifetime | XK 11 (counts 11..0, removed <0), TQ 12 (removed ≤0 before decrement), SP **11 N / 9 H** | XK `Xarpus.java:198,231-237`; TQ `rooms/xarpus/commons/ExhumedTracker.java:63-70`; SP `Xarpus.java:301,307-310` | Med (≈12-tick window in N; shorter in H) | N / H |
| exhumed count | Total P1 exhumeds by party size, N / H: 5 → 18/24, 4 → 15/20, 3 → 12/16, 2 → 9/13, 1 → 7/9 | SP `Xarpus.java:266-283` | Med | N,H |
| spot 1555 / 1550 | Acid spit / exhumed energy orb (RL names); acid-pool end 1551-1554 | RL `SpotanimID.java:1557-1563` | High (names) | all |

## Verzik Vitur

### Ids and animations

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| NPC ids | E 10830-10836, N 8369-8375, H 10847-10853 (initial, P1, P1→2, P2, P2→3, P3, bat); quick-start initial N `VERZIK_INITIAL_QUICKSTART`, H `_HARD_QUICKSTART` | SP `Verzik/Verzik.java:255-288`; BL `TobNpc.java:119-147`; TQ `rooms/verzik/commons/VerzikMap.java:45-58` | High | all |
| 8109 | P1 attack (dawnbringer-phase magic) | BL `rooms/verzik/VerzikDataTracker.java:51`; SP `Verzik.java:69`; RL `AnimationID.java:8114` | High | all |
| 8114 | P2 ranged/magic attack (RL `VERZIK_PHASE2_ATTACK_MAGIC`) | BL `VerzikDataTracker.java:52`; SP `Verzik.java:70` | High | all |
| 8116 | P2 bounce (RL `VERZIK_PHASE2_ATTACK_MELEE`); bounced player plays anim 1157 and gets spot 245 | BL `VerzikDataTracker.java:53,61`; MT `VerzikP2MistakeDetector.java:38` | High | all |
| 8117 | P2 heal / reds start (RL `VERZIK_PHASE2_HEAL`) | SP `Verzik.java:72`; XK `Verzik/Verzik.java:133`; TQ `VerzikMap.java:77` | High | all |
| 8118 | P2 death = P3 transition | BL `VerzikDataTracker.java:54`; TQ `VerzikMap.java:78` | High | all |
| 8123 / 8124 / 8125 | P3 melee / magic / ranged (8125 also used for the green ball) | BL `VerzikDataTracker.java:55-57`; TQ `VerzikMap.java:79-80`; MT `MistakeDetectors/VerzikMeleeChancedTracker.java:34-38` | High | all |
| 8126 / 8127 | P3 yellows ("powerblast") / web spin | BL `VerzikDataTracker.java:58`; TQ `VerzikMap.java:81-82`; RL `AnimationID.java:8131-8132` | High | all |
| 8128 | P3 death | BL `VerzikDataTracker.java:59` | High | all |
| proj 1580 | P1 projectile (RL `VERZIK_PHASE1_PROJECTILE`; 1582 = pillar impact) | RL `SpotanimID.java:1587-1589` | High (name) | all |
| proj 1583 / 1585 / 1586 / 1591 | P2 "cabbage" ranged / lightning (zap) / purple-crab spawn / blood-magic | BL `VerzikDataTracker.java:63-66`; SP `Verzik.java:79-84` | High | all |
| gfx 1584 | P2 ranged impact ("bomb" tile) | MT `VerzikP2MistakeDetector.java:37`; RL `SpotanimID.java:1591` | High | all |
| obj 41747 | HM P2 acid pool | SP `Verzik.java:85`; MT `VerzikP2MistakeDetector.java:39` | High | H |
| gfx 1595 / 1596 / 1597 | Yellow pool / yellow falling / player protected | TQ `VerzikMap.java:84-85`; SP `Verzik.java:81-82`; BL `VerzikDataTracker.java:79` | High | all |
| proj 1598 | Green ball (acid bomb); chat "Verzik Vitur fires a powerful projectile in your direction..." / "A powerful projectile bounces into your direction..." | TQ `VerzikMap.java:87-89`; SP `Verzik.java:83` | High | all |
| gfx 1602 | Tornado hit on a player (RL `VERZIK_P3_CREEPER_SPOT`) | BL `VerzikDataTracker.java:62`; MT `VerzikP3MistakeDetector.java:53` | High | all |
| obj 32734; NPC 8376 / 10837 / 10854 | P3 web game object; web NPCs N / E / H | MT `VerzikP3MistakeDetector.java:52`; BL `VerzikDataTracker.java:78` | High | all |
| tornado NPC | 8386 / E 10846 / H 10863 | SP `Verzik.java:65-67`; BL `TobNpc.java:175-177` | High | all |

### Phase 1

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| 14 ticks | P1 attack speed | BL `VerzikDataTracker.java:69,778`; SP `Verzik.java:801-807`; XK `Verzik.java:424` | High | all |
| +1 tick | The attack registers 1 tick after anim 8109 starts | BL `VerzikDataTracker.java:460-463` | Med | all |
| 18 ticks | First P1 attack 18 ticks after the P1 NPC appears | SP `Verzik.java:757-763`; XK `Verzik.java:384` | Med | all |
| dawn ≥75 | BL credits a Verzik hitsplat ≥75 to a dawnbringer spec made in the last 4 ticks | BL `VerzikDataTracker.java:70,226,560-575` | Low (attribution heuristic) | all |
| item 22516 | Dawnbringer ground item (drop/pick-up tracked during P1) | BL `VerzikDataTracker.java:81,590-605` | High | all |

### Phase 2

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| 4 ticks | P2 attack speed | BL `VerzikDataTracker.java:71,780`; SP `Verzik.java:812-818`; XK `Verzik.java:435` | High | all |
| 3 ticks | First P2 attack after the P2 NPC appears | BL `VerzikDataTracker.java:72,743`; SP `Verzik.java:766-773`; XK `Verzik.java:390` | High | all |
| 12 ticks | Gap from reds (8117 anim / Matomenos spawn) to the next attack | BL `VerzikDataTracker.java:73,767-773`; SP `Verzik.java:822-828`; XK `Verzik.java:445` | High | all |
| 7 attacks | Attacks per reds cycle. SP/XK: the 7th attack after reds is followed by an 8-tick gap. BL: after 7 attacks it stops predicting until the next reds | BL `VerzikDataTracker.java:74,665-668,770`; SP `Verzik.java:816-821`; XK `Verzik.java:439` | Med (agree on 7, differ on what follows) | all |
| zap ≥4 apart | Lightning (1585): BL requires ≥4 attacks between zaps; SP counts 4 ranged balls (once per 4-tick window) and accepts a zap when fewer than 2 remain | BL `VerzikDataTracker.java:111,170-176`; SP `Verzik.java:212,416-430` | Med | all |
| purple: 21 / ≥16 | Purple crab (Athanatos, proj 1586): SP sets "attacks until next purple" to 21; BL uses ≥16 only as a false-positive guard ("not the actual minimum") | SP `Verzik.java:425,772`; BL `VerzikDataTracker.java:112-114` | Low | all |
| bounce check | BL samples bounce candidates (players in melee range or under Verzik) 1 tick before each P2 attack; a bounce graphic within 3 ticks of anim 8116 is the bounce | BL `VerzikDataTracker.java:256-258,548-557,607-622` | Med | all |
| acid 14 ticks | HM acid pool (obj 41747) timer | SP `Verzik.java:464-467,637-641` | Med | H |
| reds ids | P2 Matomenos 8385 / E 10845 / H 10862; P2 nylos 8381-8383 / E 10841-10843 / H 10858-10860; Athanatos 8384 / 10844 / 10861 | SP `Verzik.java:289-310`; BL `TobNpc.java:154-172` | High | all |

### Phase 3

| Value | Meaning | Source | Conf. | Mode |
|---|---|---|---|---|
| 6 ticks | First P3 attack after the P3 NPC appears | SP `Verzik.java:774-781`; XK `Verzik.java:396` | Med | all |
| 7 ticks | P3 base attack speed | SP `Verzik.java:840-846,877`; XK `Verzik.java:464,470,482,494`; TU `Region.java:10` (metronome 7) | High | all |
| 5 ticks (7−2) | Enraged speed: once the first tornado spawns, every P3 cycle is 2 ticks shorter; on the enrage tick the pending timer is cut to 5 unless yellows are pending with >7 left | SP `Verzik.java:311-322,753,872-878`; XK `Verzik.java:186-194,356,497-503` | High | all |
| min 5 ticks | BL's floor between P3 attacks (consistent with the enraged speed) | BL `VerzikDataTracker.java:76,713` | High | all |
| rotation | Specials every 5th attack: count 5 crabs, 10 webs, 15 yellows, 20 green ball, then wrap (`count % 20`). BL: 4 regular attacks, then the special; order CRABS → WEBS → YELLOWS → BALL | SP `Verzik.java:74-77,834-870,905-918`; XK `Verzik.java:135-138`; BL `VerzikDataTracker.java:75,691-735`, `rooms/verzik/VerzikSpecial.java:27-43` | High | all |
| crabs, ball: + attack | Crabs and the green ball come with a normal attack; webs and yellows replace one | BL `VerzikDataTracker.java:722-733` | Med | all |
| webs | Verzik stops targeting and walks to the centre. When she targets again, the counter is set to 10 and the next attack comes 10 ticks later | SP `Verzik.java:784-794`; XK `Verzik.java:404-410`; BL `VerzikDataTracker.java:266-269,697-708` | Med | all |
| yellows 21 / 27 | Next attack 21 ticks after the yellows (XK `14 + 7`); HM 27 | SP `Verzik.java:850-857`; XK `Verzik.java:476` | Med | N / H |
| yellows land 14 | Pools resolve 14 ticks after they appear (TQ, SP, XK) | TQ `rooms/verzik/VerzikHandler.java:299-311`; SP `Verzik.java:209,657-716`; XK `Verzik.java:360-373` | High | N |
| HM yellows | HM: 3 pools per player (BL `×3`). SP resolves them in 3 waves: 14, then +3, then +3 (20 ticks). TQ uses a single 17-tick timer | BL `VerzikDataTracker.java:517-521`; SP `Verzik.java:658-666`; TQ `VerzikHandler.java:305-308` | Med (see conflicts) | H |
| green 12 | Next attack 12 ticks after the green ball (SP comment: "12 During Purples?") | SP `Verzik.java:864-868`; XK `Verzik.java:489` | Med | all |
| green bounce 3 | SP's bounce window: 3 ticks after the ball lands | SP `Verzik.java:432-441,627-634` | Low | all |
| tornado 18 | A player's tornado re-spawns 18 ticks after it despawns (or after it hits them, gfx 1602) | SP `Verzik.java:395-398,587-590` | Med | all |
| tornado heal ×3 | BL matches a Verzik heal hitsplat equal to 3 × the tornado damage to the player; one heal per player per 10 ticks | BL `VerzikDataTracker.java:541-546,786-806` | Med | all |
| tornado follow | Tornadoes move every tick toward their player: TQ shifts its 2-position trail every tick, SP keeps the previous tile | TQ `rooms/verzik/commons/Tornado.java`, `VerzikHandler.java:298`; SP `Verzik.java:725-747` | Med | all |

## Hard-mode changes stated in code (summary)

| Room | Change | Source |
|---|---|---|
| Maiden | Attack cadence ratchets down from 10 toward 3 each time she attacks early | SP `Maiden.java:535-542` |
| Nylocas | Cap 15 (vs 12) before wave 20; Prinkipas on waves 10/20/30, counts as 3, next wave after 16 ticks; Prinkipas switches every ~10 | BL `NylocasDataTracker.java:90-99,301`; SP `Nylocas.java:406-418` |
| Xarpus | More exhumeds (24/20/16/13/9 by party size), shorter lifetime (9 vs 11) | SP `Xarpus.java:266-301` |
| Verzik P2 | Acid pools (41747), 14-tick timer | SP `Verzik.java:464-467` |
| Verzik P3 | 3 yellows per player in sequence (+3 / +3 ticks); next attack after yellows 27 instead of 21 | SP `Verzik.java:658-666,852-855`; BL `VerzikDataTracker.java:519-521` |
| Bloat, Sotetseg | None encoded | — |

Entry: the only Entry-specific timing is Vasilias's 15-tick style switch (SP). Every other counter is shared.

## Conflicts and code defects

1. **Bloat hand graphics.** SP/XK accept any graphics object 1560-1590 as a hand (`Bloat.java:139-142`); BL uses
   1570-1573 (`BloatDataTracker.java:40-41`). RL gamevals name 1570-1573 `TOB_BLOAT_FALLING_FLESH1..4` and 1560-1567
   are nylocas graphics. **Use 1570-1573**, with 1576 as the landing splat.
2. **Bloat 38-tick up-timer.** SP resets it on every Bloat animation change; XK only at spawn or the door varbit. The
   number is identical, its reset point is not, and neither states what it means. Mark `OPEN`; the 32-tick down (BW)
   and the 26..34 warn window (SP/XK) are consistent with each other.
3. **Nylocas HM cap.** BL: 15 before wave 20 in Hard. SP and NY: 12 in every mode (SP does read the mode for its wave
   tables). Wiki needed; BL is the newest and mode-aware.
4. **Nylo wave 9 / big slots.** BL's 6-slot key cannot hold a west big together with a west small, so wave 9 reads
   `_h__T_` in BL, while SP lists range big W + melee small W-south + mage small E-north. Several SP waves place the east big on
   `EAST_NORTH` where BL's key uses slot E0 (an encoding difference, not a composition change). Otherwise all 31
   waves match (`/tmp/claude-0/ref/notes/cmpwaves.py`).
5. **Exhumed lifetime.** 11 (XK), 12 (TQ), 11 N / 9 H (SP). The counter idioms differ by one tick; the true window is
   about 11-12 ticks in Normal and shorter in Hard.
6. **Xarpus 8341.** SP calls 8341 / 10769 / 10773 "P3"; TQ calls them the dead form. Both detect P3 by the screech's
   overhead text (TQ also by HP ≤ 25 %). Treat P3 as a state of 8340, and 8341 as death.
7. **Verzik HM yellows.** SP: three resolutions at 14 / 17 / 20 ticks. TQ: one 17-tick timer. BL: 3 × players pools.
   SP's sequence is the most specific. TQ's 17 matches SP's second wave.
8. **Verzik P2 after the 7th attack.** SP/XK: an 8-tick gap. BL: no prediction until the next reds. Purple-crab
   spacing: SP 21 attacks, BL "≥16, not the real minimum".
9. **Maiden 8092 naming.** BL/TQ call it the auto; RL names it `MAIDEN_ATTACK_SPECIAL` (8091 = `_BLOOD`). The ids agree;
   only the labels differ.
10. **Code defects to ignore:** TQ `VerzikConstants.java:69` maps `HM_VERZIK_P3_NPC` to the Normal id (its
    `VerzikMap` is right). SP `Verzik.java:766` lists 10832 (the Entry P1→P2 transition) instead of 10833 for Entry P2.
    SP `Maiden.java:252` uses 10814 for Entry 70 %, while `:335` uses 10815.
11. **Sotetseg death-ball counter.** Only SP counts it (10 magic-orb projectiles); BL has no counter. SP counts
    projectiles, so with several targets the count may not equal 10 *attacks*. Mark `OPEN`.

## Leads not followed (outside "RuneLite plugins")

- `MRobertEvers/oldschool-clientc` `docs/rsprox-analysis/tob/*.md` and `docs/TOB_RESEARCH.md`: RSProx packet captures of
  ToB. These are likely the best source for projectile travel ticks.
- `blert-io/blert` (server) `common/`, `bcf/docs/spec.md`: event specs; it is BW above.
- `SuperNerdEric/runelogs` `src/utils/bloatDownEvents.ts`, `npcAttackAnimationNames.ts`.
