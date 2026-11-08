# TFA values measured in RSProx captures

Read 2026-11-08. Research notes only: nothing here is implemented yet. Every value is a measurement another project
took from RSProx packet captures of the live game, so it is evidence for what client plugins cannot see (damage, HP,
counts, heals). Facts only: no code or prose is copied.

## Source

| Key | Repository @ commit | What it is |
|---|---|---|
| GA | `MRobertEvers/oldschool-clientc` @ `3cce15be706d028b6eafe53b080a808dc393baac` (2026-11-07), dir `docs/rsprox-analysis/guthixian_archive/` | Per-boss specs (`*_plan.md`, `RAID_PLAN.md`, `hit_causes_audit.md`) and the analysis scripts that produced them (`tools/ez_*`, `bk_*`, `dn_*`, `oc_*`, `tfa_mech.py`, `scaling.py`, `adds.py`) |

- **Licence:** the repository has no licence file at its root (only vendored `3rd/*` licences) and the GitHub API was
  not reachable from this session to confirm. Treat it as all rights reserved: only the measured facts are taken. The
  clone is at `/tmp/claude-0/ref/oldschool-clientc` (sparse: `guthixian_archive/` and `tob/`).
- **The captures themselves are not in the repo.** Only the analyses are. Each row cites the analysis line that states
  the measurement; the session ids (`idx4294..4321`) are rsprox.net indexes.
- **Revision and date:** rev **241**, 2026-10-20 to 10-22, normal worlds (GA `README.md:3`, `RAID_PLAN.md:7`). That is
  **before the 28 Oct and 3 Nov hotfixes**, so values those patches changed (Ocellus P2 diminishing returns, P3 tank
  order, Dreadnoth offensive drain cap, the Dagon'hai Defence cap) may be stale.
- **Prayers:** most party runs were spectated, so other raiders' prayers are not on the wire. Where GA re-transcribed
  with `--bas` the overheads are read from appearance blocks (`hit_causes_audit.md:152-169`); elsewhere "0" splats are
  read as prayed hits and the non-zero tail as unprayed. The confidence column says which.
- **Ids:** GA names gamevals. Each name below was resolved against our cache 2757 symbols
  (`symbols/build/generated/symbols/tp/symbols/*.java`); the Guardian ids GA prints (e.g. projectile 4201, seqs
  14745-14755) equal ours, so rev 241 and cache 2757 agree on these ids. Adds' HP were checked with
  `./gradlew :tools:cache-dump:run --args="config npc <id>"` (stats order att, def, str, **hp**, ranged, magic).
- Projectile times are client cycles (30 a tick); "lands +n" is server ticks after the throw.

Party sizes by session (GA `RAID_PLAN.md:9-20`): 4296-4298 solo; 4300 duo; 4301 trio; 4302 five; 4303 duo then five;
4304 trio then duo; 4306 four; 4308 four (B0aty); 4294 five; 4314 duo; 4315 trio; 4318/4319 five; 4320 trio.

Confidence: **high** = a rule GA states with n/n counts; **medium** = fitted or prayers unread; **low** = 1-3 samples.
**Settles** names the OPEN id a row resolves (fully or "partly"); "conflict" means it contradicts our doc, the wiki or a
plugin value (listed again at the end).

## Raid-wide scaling

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| ×1.000 / ×1.450 / +0.420 per raider | Boss HP = the 1-2 player figure × 1.00 at 1-2 raiders, 1.45 at 3, 1.87 at 4, 2.29 at 5, rounded to the nearest 10; 6-8 unrecorded (same step assumed) | GA `RAID_PLAN.md:96-100` (`tools/scaling.py`, bar varbit 6100 vs HUD names) | 32/32 boss×party pairs | high | OPEN-MO2, OPEN-DH2 (method); confirms our HP tables |
| table | Bar base HP 1 / 2 / 3 / 4 / 5: Guardian shield 550/550/800/1030/1260, health 1100/1100/1600/2060/2520; Ezekiel -/3000/4350; Bork -/1500/2180/2810/3440; Smorks -/600/870/1120/1370; Dreadnoth -/2100/3050/3930/4810; Xarth -/2600/3770/4860/5950; Ocellus P1 -/-/3630/4680; P2-4 -/-/7610/9820 | GA `RAID_PLAN.md:84-94` | 8+ fights | high | — (all equal our tables) |
| party locked | Adds follow the raiders **still standing in the room**, not the party that started | GA `RAID_PLAN.md:115-117` | — | high | — |
| ×1.06 / ×1.12 / ×1.19 | Shown combat level (`level_change`) × party at 3 / 4 / 5 raiders (+0.065 a raider); none sent at 1-2. Fits Ezekiel 1043, Moia 1064, Dagon'hai 452, binders 143 at three; Dreadnoth 941/994/1056 at 3/4/5 | GA `guardian_plan.md:186`, `dreadnoth_plan.md:183`, `moia_plan.md:28` | 6/6 + spot checks | high | OPEN-MO1 |

## Archive Guardian

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| 5 t | One attack clock: ranged, special, melee each take a slot; a swoosh (reposition) takes none, next = max(last+5, swoosh+3) | GA `guardian_plan.md:34-35` | 145/145, 37/37, 28/28 | high | — |
| every 5th slot | Special (mushrooms) every 5th slot, 4 ranged between (26 of 31; 5 pairs show 5) | GA `guardian_plan.md:37` | 31 pairs | medium | OPEN-AG2 (cadence) |
| proj 4201, 48→116 | Ranged rock: `VFX_GUARDIAN_RANGED_PROJANIM01` start 48, end 116 at every distance, angle 127, heights 0/0; lands throw +4 | GA `guardian_plan.md:41-42`, `:128` | 243/243 | high | — |
| 0..41; PfMissiles 0..10 | Ranged damage solo: max 41 unprayed, Protect from Missiles takes 75% (max 10, even spread); read at landing. Parties: 44..54 unprayed at 4-5 raiders (a party scaling of the max, not fitted) | GA `guardian_plan.md:43`; `hit_causes_audit.md:211-212` | 137 prayed, 5 + 6 unprayed | medium | — |
| 2 per raider | Mushrooms: one on a tile beside the raider's tile of the tick before, one exactly 3 out; never on a wall or a live mushroom; at most 39 alive; stay all fight | GA `guardian_plan.md:52-56` | 171 specials, 929 mushrooms | high | OPEN-AG2 |
| +3, burst +1 | Mushroom active 3 t after spawn (loc 62578 → 62579); bursts on T+1 where T is the first tick ≥ active+1 a raider stands within 1 | GA `guardian_plan.md:57-58` | 468/469 | high | — |
| poison 10, drowsy 17 t | Burst+1: raiders within 1 at the burst's end poisoned 10 (splat 10, decays 10, 9, 8, every 30 t), "You're too drowsy to run!" for 17 t. No prayer drain or bind recorded; stray 2..4 poison splats unexplained | GA `guardian_plan.md:59`, `:201-203` | 44/51 | high | OPEN-AG6 (partly; conflict: our 10 prayer + 2-t bind) |
| 180 HP; −40% shield | Battery 180 HP solo (cache 16662 stat4 180; "180..400 by party"); at 0: +1 despawn seq, +3 the shield loses 40% of its max (220 solo) capped at what is left | GA `guardian_plan.md:65-66`; `hit_causes_audit.md:191` | 12/12, 17/17 | high | OPEN-AG3, OPEN-AG8 (partly) |
| SE battery inert | The south-east battery (4139,5592) has ops 0 all fight, is never hit by a raider, destroyed only by the enrage's first overload | GA `guardian_plan.md:29` | 5/5 | high | OPEN-AG8 (conflict: ours says north-east) |
| shield 0 → +3 | Shield at 0: 3 t later `idle_to_ground01` (14747), grounded form 16660, bar 1100 | GA `guardian_plan.md:67` | 5/5 | high | — |
| 35 / 41 / 47 t | Grounded 35 t the first time, +6 each later landing; vines from landing +5 then every 3 t; he lifts 3 t after the first slot with no wave | GA `guardian_plan.md:68`, `:83` | 24/24, 8/8, 2/2 | high | OPEN-AG5 (conflict: wiki 34, LU 40) |
| < 34% HP | Early lift: from the +11 slot, a slot with real HP under 34% has no wave, he lifts 3 t later and enrages (33.4..33.8% lifted; 34.3..34.6% did not) | GA `guardian_plan.md:71` | 23 lifts | high | OPEN-AG5 |
| 1 vine a raider | Vines: one per living raider each wave, on the tile one step past the raider away from his middle (else toward him, else the raider's tile); they block each other | GA `guardian_plan.md:84-85` | 463/464 waves | high | — |
| +3, +6, +9; gone +13 | Vine attacks 3, 6, 9 t after its add, despawns +12, deleted +13 | GA `guardian_plan.md:87` | 1140/1140 | high | — |
| 17..19; PfMelee 10 (11) | Vine hit: melee, every living raider exactly 1 tile from a vine; one hit a beat however many reach; never misses. Unprayed or PfMissiles 17..19; Protect from Melee 10 (11 in 6 of 33), read as 40% off | GA `guardian_plan.md:88-89` | 101 hits | high | OPEN-AG7 |
| 42..49 ÷ raiders, ×1.5 | Enrage blast (the "laser"): every 20 t, one roll of 42..49 split by the raiders in the room, ×1.5 off Protect from Magic. Prayed: 21..23 (two), 14..15 (three), 10..11 (four), 8..9 (five). Solo unrecorded | GA `hit_causes_audit.md:177`; `guardian_plan.md:94-95` | 63 blasts | high (party) | OPEN-AG4 (conflict: our 35 × players alive) |
| S, +5, +10 | Enrage cycle: S, +5 `enraged_phase01` (14754), +10 a battery overloaded (its HP as one hit); order (4139,5592), (4139,5605), (4114,5592), (4114,5605) | GA `guardian_plan.md:94` | party fights | high | OPEN-AG4 (timing) |
| 0..48 | Slam (`melee_attack01` 14752): when any raider is under his 5×5 as it comes due; lands +1 on whoever is still under (37, 48 through PfMissiles); stepping out on the swing tick avoids it (0/21) | GA `guardian_plan.md:99`, `:204` | 24/24 trigger, 2 hits | low (damage) | OPEN-AG9 (conflict: no "lash behind him" exists) |
| 24..28 | Landing smash: a raider 1 tile from his 5×5 as he lands; never at 2+ | GA `guardian_plan.md:100` | 6/6, 0/136 | high | OPEN-AG9 |

## Ezekiel

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| M, M, S | One clock: melee → melee 5 t, melee → special 5 t, special → melee 7 t (8 if he walked) | GA `ezekiel_plan.md:70-77` | 38/38, 9/10 | high | — |
| W+12 | Weakened (sword through him): `idle_to_weaken01` W, `weaken_to_idle01` W+8, next attack W+12, cycle restarts at melee; no step W+1..W+9 | GA `ezekiel_plan.md:77`, `:189-193` | 6/6, 0/30 steps | high | OPEN-EZ8 (stun length) |
| 13 of 16 ring tiles | Cleave hits the 16-tile ring except the 3 tiles opposite the tank (mirror through his centre plus its two neighbours on that side); also a raider under his 3×3 (0..12) | GA `ezekiel_plan.md:85-97` | 39/39, 7/8 | high | — |
| PfMelee 1..9; 14..27 | Cleave damage: 1..9 through Protect from Melee (32/38 non-zero), 14..27 unprayed | GA `ezekiel_plan.md:512` | 63 hits | medium | — |
| 4..21 | Magic (tank out of reach after an attack's due tick): every raider in the room, hit 2 t after the cast; prayers unread | GA `ezekiel_plan.md:111-115` | 5 casts, 13 hits | low | OPEN-EZ6 (partly) |
| S+3, S+5 | Star (lightning): ring at delay 34, arms 3 wide one out at 36, middle tile 2 out at 38, 3 out at 40, clipped to the 9×9; hits every raider on a star tile at S+3 and at S+5 (S+5 also under his 3×3) | GA `ezekiel_plan.md:134-148` | 54/54, 9/9 + 3/3 | high | OPEN-EZ7 |
| 20..34 | Star damage, typeless, both beats | GA `ezekiel_plan.md:151`, `:515` | 66 | high | OPEN-EZ7 |
| 5 layouts | Swords (16653, 80 HP, cache confirmed): cross (one column x ∈ {4059,4062,4065} and one row z ∈ {5531,5534,5537}; never the middle cell or (4059,5531)) 21/42; three rows from the west 9; three columns from the north 6; two rows z 5532/5536 6 | GA `ezekiel_plan.md:153-168` | 42 specials | high | OPEN-EZ3 |
| S+14 | A sword flies 14 t after its add (line layouts one a tick: S+14, 15, 16); 12 tiles across; headbar 87 is its 13-t (390-cycle) clock | GA `ezekiel_plan.md:169-180` | 15/15 lines | high | OPEN-EZ8 (conflict: LU 15 t, ours 4) |
| 27..31 / 52..63 | Sword band on a raider: one sword 27..31, two 52..63, charged sword its charge + 21..30 (75, 84, 102); burn 2 every 2..5 t | GA `ezekiel_plan.md:181-183`, `:263-266`, `:516-517` | ~10 | medium | OPEN-EZ8 |
| charge + 12..15 | A sword through his south-west lane hits him for its charge + 12..15 (one splat per flight, summed); charge = wind-surge damage, capped at 80 | GA `ezekiel_plan.md:225-251` | 32/32 flights | high | — |
| 10% of max | Heal when a cross or the two rows all miss his SW tile: fly+2 seq, fly+4 heal splat of a tenth of max HP (300/3000, 435/4350), capped at full; a missed line layout heals nothing | GA `ezekiel_plan.md:202-210` | 5/5, 2/2 | high | OPEN-EZ1 (conflict: our 50) |
| first in; then nearest | Tank = first raider through the transporter; on the tank's death the raider nearest his 3×3, "Face me!". No re-tank after magic seen | GA `ezekiel_plan.md:42-47` | 8/8, 3/3 | high | OPEN-EZ9 (no evidence for 1-in-3) |
| ≤ 33.5% | Finale trigger (1457/4350, 1003/3000); F he jumps to the arena cell next to the cage on the side he was nearest; F+6 cage + 7 fence swords; F+8 three V swords | GA `ezekiel_plan.md:345-351` | 2/2 | high | OPEN-EZ10 (conflict: ours 6 south of spawn) |
| F+12, every 11 | Volley = 3 strikes at V+4, V+5, V+6; the 11-t cycle covers all three | GA `ezekiel_plan.md:352-366` | 83/83 volleys | high | OPEN-EZ4 |
| fixed 6-cycle | Strike order is not rolled: B l r / B r l / r B l / l B r / B r l / B l r (B = both sides, safe far row; l/r = front + side, safe far two tiles of the other column); "both" never last | GA `ezekiel_plan.md:354-379` | 249/249 strikes | high | OPEN-EZ2 (conflict: LU "random order") |
| 6, 19 | Volley damage on an unsafe tile | GA `ezekiel_plan.md:381`, `:518` | 2 | low | OPEN-EZ5 (not settled) |

## Bork and the Smorks

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| 7 → 6 → 5 → 4 t | Bork's drum and the Smorks share rate R: 7, then 6, 5, 4 at 75 / 50 / 25% of the Smorks' HP | GA `bork_plan.md:55` | 2 fights | high | OPEN-BK3 (partly) |
| engage+8 / +13 | First gong engage+8, first Smork slot +13 | GA `bork_plan.md:169` | 13/13 | high | — |
| T, T+2 | Gong: two beats; beat 1 reach = max(1, his target's distance from his 4×4 the tick before), beat 2 one ring; tile delay = Manhattan from (2,2) of his 4×4; the cart's shadow is safe | GA `bork_plan.md:23-42` | 105/105, 117/117 | high | — |
| 16..28 / 20..33; ÷4 | Beat 1: 16..28 unprayed, Protect from Melee 4..7 (a quarter, floored); beat 2: 20..33 (PfM 5..8) | GA `bork_plan.md:271-272` | 408, 314 | high | OPEN-BK1 |
| 7-slot cycle | Smorks: shot, shot, napalm, -, shot, shot, big bomb, every R | GA `bork_plan.md:47` | 4 cycles | high | OPEN-BK6, OPEN-BK7 (cadence) |
| one per raider but tank | A shot: one projectile at every raider except Bork's target, one style a shot at random (runs like mmmm, never alternating); proj 4057 magic / 4056 ranged, start 0, end 150/120/90/90 at R 7/6/5/4; lands +(end+15)/30 | GA `bork_plan.md:48`, `:170` | 268 shots | high | OPEN-BK5 (conflict: ours alternate) |
| ≤ 32 | Shot damage, Bork alive: blocked 70%, 1 23%, 11..30 7%; Bork dead: blocked 52%, 1 22%, 2..9 20%, 10..28 6%; max seen 32 | GA `bork_plan.md:149`, `:278-279` | 243, 181 | medium | OPEN-BK5 |
| every 2 t | Bork dead: from death+11 the Smorks shoot every raider every 2 t, end 90, the cycle going on | GA `bork_plan.md:177` | 3 fights | high | OPEN-BK5 (conflict: ours speed 3) |
| 25..28; 2/t; burn | Napalm (proj 4045) lands +4: 25..28 on its tile; patch 3..17 tiles in 1-2 streaks; ragged tiles (loc 62544) 199 t; 2 a tick on one (hitsplat 67); burn 1/2/3 (39/40/8) every 4 t ×5 | GA `bork_plan.md:49-50`, `:175-176`, `:274-277` | 8, 24, 87 | high | OPEN-BK6 (conflict: wiki "6-7 per tick") |
| 25..28 to Bork | Napalm on his 4×4 or a big bomb one tile off it hits Bork 25..28 | GA `bork_plan.md:173-174` | 5/5, 3/4 | high | — |
| +5; stun 14 t | Big bomb (proj 4440) lands +5 after its shot; under his 4×4 (tick before) = stunned L..L+13 (seq 14545 `14_CYCLE_STUN01`), every hit on him lands; off him = no raider damage (0 splats within 1) | GA `bork_plan.md:51`, `:61-63`, `:171`, `:280` | 39/39, 0 | high | OPEN-BK7 (conflict: ours 8 t + 20 dmg; LU 16 t) |
| L+17, 20, 23, 26 | After a stun: stomp-in L+14 ("Bork very angry!!"), slams L+17/20/23/26 over the arena but the cart's shadow; retarget L+29; drum L+31/33 | GA `bork_plan.md:63-68` | 10/10, 34/34 | high | OPEN-BK8, OPEN-BK3 |
| 14..48; PfM 18..33 | Slam damage: 14..50 off Protect from Melee, 18..33 under it | GA `hit_causes_audit.md:179`; `bork_plan.md:273` | 27 | medium | OPEN-BK8 |
| miss branch | Missed bomb: L+5 stomp, slams L+6/9/12/15, new target L+18, drum L+20..22 | GA `bork_plan.md:172` | 8/8 | high | OPEN-BK3 |
| random, on anger only | Retarget only after a stun or missed bomb, to a random raider; "Bork smash <name> now." only when it changes | GA `bork_plan.md:67`, `:178-179` | 31+6, 32 | high | OPEN-BK4 (conflict: ours every 5th attack) |
| flat armour 10 | Fitted: 10 off every melee hit while undazed (98:1 over none), 0 dazed; dazed melee never misses (1 zero in 84). Magic resistance 2: untested | GA `bork_plan.md:234-261` | 84 + 36 swings | medium | OPEN-BK2 (armour conflict: ours 8 + 2/25%) |

## Moia

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| 1900 / 2760; lvl 1004 / 1064 | HP 1900 at 1-2, 2760 at three (×1.45); level 1004 base, 1064 at three | GA `moia_plan.md:26-28` | 4 fights | high | OPEN-MO1, OPEN-MO2 |
| 5 t slots | An action every 5 t from her add: dagger volley or an event | GA `moia_plan.md:32` | 30/30 slots | high | — |
| proj 4214, lands +1 | Dagger volley: one at every raider (start 10, end 30); unprayed 5..32, 471/485 zero (prayed) | GA `moia_plan.md:39`, `:98` | 485 | medium | — |
| T, T, I | Events: blink, blink, infection, the first 25 t after her add, then every 20 or 25 | GA `moia_plan.md:40` | 4/4 | high | — |
| 4 points | Blink to one of W (4055,5662), N (4062,5669), E (4069,5662), S (4062,5655) (her SW tiles; centre ring middle (4063,5663), so radius 7) | GA `moia_plan.md:41` | 18/18 | high | OPEN-MO7 (conflict: ours radius 9) |
| proj 4213, lands +6 | Magic dagger at every raider on a blink, end 180; unprayed 11..44, 100/115 zero | GA `moia_plan.md:41-42`, `:99` | 18/18 | high | OPEN-MO6 (as LU) |
| proj 4218, +4; 2 | Infection at one random raider (end 120); cough at landing (I+4), poison splat 2 the tick after, every 8 t, 4 coughs; spreads to raiders and binders within 2; a binder coughs for 20 | GA `moia_plan.md:43`, `:52-54`, `:102-103` | 9/9, 139 | high | OPEN-MO8 (partly: no prayer drain seen) |
| 8; 9-point track | 8 binders at E+6 on a 9-point track round C, legs of 4 tiles every 15 t from E+13; refill every 30 t from E+23 back to six | GA `moia_plan.md:71-75` | 483 legs, 14/15 | high | — |
| 4..22, reach 2 | Binder swing at a raider within 2 (its own tile included): first 7 t after a leg starts, then every 5; 4..22, 2/65 zero; on its own tile "Grah!" pushes a tile | GA `moia_plan.md:76-78`, `:100` | 66 swings | high | OPEN-MO9 (conflict: ours max 15) |
| 95 HP | Binder HP (cache 16670 stat4 95) | GA `moia_plan.md:80`; cache | — | high | — |
| ≤ 34% at a slot | Change: "Cease this futile resistance!"; +1 every binder healed to full (15..126), she leaves; +14 back at the middle | GA `moia_plan.md:61-64` | 4/4 | high | — |
| 195 / 215 | Return heal at 2760: 65 a binder standing, at most 215 (195, 215, 215); the duo's not on the wire | GA `moia_plan.md:64`, `:176` | 3 | medium | OPEN-MO5 (conflict: wiki +80) |
| C+41, +39, +37, +35 | Chants: C1 = return+2, then 41, 39, 37, 35 t apart; countdown headbar 77 over B = gap − 5 (36, 34, 32, 30) | GA `moia_plan.md:87-89` | 4/4, 14/14 | high | OPEN-MO3 (as LU) |
| 25 × survivors | Blast at C'−5: 25 to every raider per empowered binder still standing (25/50/75) | GA `moia_plan.md:91`, `:101` | 7/7 | high | OPEN-MO3 |

## Dreadnoth the Jellied

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| 2 t a tile | Crawl one tile every 2 t; first step 2 t after the engage face | GA `dreadnoth_plan.md:27-30` | 343/408 | high | — |
| 4 t; within 1 | Melee on a 4-t clock whenever any raider is within a tile of his 5×5; hits every raider within 1; never on a step tick | GA `dreadnoth_plan.md:38-45` | 64 swings | high | — |
| PfM 0..30 | Melee through Protect from Melee: 2..30 even (0..45 cut to two-thirds, fitted); unprayed only 7, 25 | GA `dreadnoth_plan.md:284-286` | 33 + 2 | medium | — |
| 85 / 65 / 45% | Slam phase k = k slams 6 t apart; J jump, J+1 hit on every raider + 3 shots (proj 4211; ends 90/120/150, land J+4..J+6) | GA `dreadnoth_plan.md:77-84` | 4/4 | high | — |
| 22..44; shots 1..33 | Slam 22..44 unprayed (Protect from Melee 0); slam shots 1..33 (Protect from Missiles 0) | GA `dreadnoth_plan.md:204-205` | 141, 581 | high | — |
| min(12, 3·n·k) | Jellies a phase = min(12, 3 × living raiders × k), split over its k slams, ≤3 a raider: 6/12/12 at two, 9/11-12/11-12 at three, 12/11/11 at four, 12/12/12 at five; hatch at the last J+8 | GA `dreadnoth_plan.md:85-95`; `RAID_PLAN.md:123` | 12 phases | high | OPEN-DR1 (conflict: ours 1 per 2 players) |
| 75 HP; 5..10 | Jelly 16666: HP 75 (cache stat4 75); first swing hatch+2 then every 3; unprayed 5..10 (8 misses in 28), Protect from Melee 0 | GA `dreadnoth_plan.md:99-110`, `:289`; cache | 28 | medium | — (conflict: our 61) |
| 7 | Seven jellies jump out at death (K+3), on 7 distinct tiles of his 5×5, never the middle | GA `dreadnoth_plan.md:139`, `:338` | 9/9 | high | OPEN-DR2 |
| 7/8 every 2 t | Blood tile (loc 62609): 7 or 8 on the first tick a raider starts on it, then every 2nd; stays until the room is won; 1-3 at a slam, 1-5 at a landing, own tile + maybe one neighbour at a jelly's death | GA `dreadnoth_plan.md:115-121`, `:332-335` | 189/202 | high | OPEN-DR3 (conflict: ours 3×3 for 10 t, 6-8 a tick) |
| ~23-26% | Enrage "*glub glub*" (23.4-25.8%); jump02 +5 then every 8 (9..14 now and then); landing +3 | GA `dreadnoth_plan.md:129-131` | 4/4, 97/97 | high | — |
| centred or 5 tiles | Leap: centred on his target if within 5 tiles each way, else 5 tiles in the nearest of 8 ways, clamped to the arena | GA `dreadnoth_plan.md:130` | 97/97 | high | — |
| 25..44; heal = dealt | Landing: under his 5×5 25..44 and he heals what he deals (28..44 a raider, 89 for two); not under him 16..38 (Protect from Melee 0); proj02 (4212) on a tile 19..41 | GA `dreadnoth_plan.md:209-211`; `hit_causes_audit.md:183` | 4 + 11, 174 | medium | OPEN-DR4 (conflict: ours flat 35) |
| crush heal | Jellies under his landing are deleted and he heals their HP left (28, 51, 3, 281 for ten, 75, 199) | GA `dreadnoth_plan.md:298-302` | 6/6 | high | OPEN-DR5 |
| ×0.6 / ×0.8 | Freeze resistance: he holds floor(n × 0.6), jellies 0.8 (barrage 32 → 19 / 25); 5-t immunity after a thaw | GA `dreadnoth_plan.md:56-73` | 13/13 | high | — |

## The Dagon'hai

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| 640 / 930 | Shared shield 640 at two (cache stat4), 930 at three (×1.45); cauldron 1000 at two and three | GA `dagonhai_plan.md:32`, `:134` | 4 fights | high | OPEN-DH2 |
| −200; 5 breaks | Each break: the cauldron loses 200, all three healed to full; the fifth break is the kill | GA `dagonhai_plan.md:33`, `:36` | 5/5 | high | — |
| 2 rifts; 6 imps | Each break: two rifts (16651, HP 20) at one site; imps two at a time at +8, +11, +14; rifts gone +19 | GA `dagonhai_plan.md:34` | 27 imps | high | OPEN-DH4 (conflict: Strat 2-3 imps, ours 3/2) |
| 0..39 | Imp (16650, HP 44 cache): swings every 4 t beside its raider (9..39 non-zero), despawns after two swings | GA `dagonhai_plan.md:35`, `:127` | 27 | medium | OPEN-DH6 (conflict: ours max 8) |
| 50-t cycle | t0 channel; 13 search; 16 throw; 20 book flies, 21 its attack; 23 cauldron moves; 25 the other monk channels | GA `dagonhai_plan.md:40-50` | 26 cycles | high | OPEN-DH3 |
| 18 t; +25 | A channel left 18 t completes (ritual +25); a spell of its element interrupts it, stun 11 t | GA `dagonhai_plan.md:59-61` | 40/40 | high | OPEN-DH3 (as LU) |
| Zam → Bandos → Sara | Book god order; a raider on the lane catches it, else it hits the cauldron (ritual +25) | GA `dagonhai_plan.md:67-69` | 22/22 | high | — |
| 23..27 + 3×5 | Zamorak: catcher 23..27, then 3 every 4 t five times; fire bolts to every raider's tile (pool damage unrecorded) | GA `dagonhai_plan.md:75`, `:123-124` | 2 casts | medium | OPEN-DH1 (partly) |
| 51; ring 23..29 | Bandos: catcher 51; a square ring from the origin, 1 tile per tick (delay 30k) to 8 out, 23..29 | GA `dagonhai_plan.md:76`, `:125` | 3 | medium | OPEN-DH7 |
| 0..13; heal 13 | Saradomin: 27 strike tiles (0..13), 6 healing tiles; the catcher healed 13 | GA `dagonhai_plan.md:77`, `:126` | 2 casts | medium | OPEN-DH8 (conflict: ours 50 / 20) |
| 5 t; 0..30 | Elder: ranged or magic every 5 t while free, at a raider and any beside them; lands +5; 0..30 | GA `dagonhai_plan.md:81-87`, `:122` | 170 | medium | OPEN-DH1 |
| 15..22 ×2 | Enrage (ritual 100): from E+6 every 5 t two bolts of 15..22 at every raider; wipe 18..23 t after E | GA `dagonhai_plan.md:95`, `:128` | 3/3 wipes | medium | OPEN-DH1 |

## Xarth

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| 3 pillars | Pillars: 2×2 rubble npcs at (6,2), (3,6), (3,−4) from his SW (4190,5534) | GA `xarth_plan.md:15` | 2/2 | high | OPEN-XA9 (conflict: ours 4 diagonals) |
| 31-t cycle ×3 | Phase 1: fireballs C, +4, +8, +12, +16 (proj 4034, lands +3); C+20 AoE + bind 3 t; C+23 flame blast; C+26 the hid-behind pillar falls; first C = engage + 6 | GA `xarth_plan.md:20-30` | 2/2 × 3 | high | OPEN-XA3 |
| third fall | Phase 2 starts at the third pillar's fall (D), not an HP threshold | GA `xarth_plan.md:35-39` | 2/2 | high | OPEN-XA7 (conflict: ours 50%) |
| D+15, every 21 | Phase 2: arrows D+7, D+11; corpse volley at M = D+15 then every 21: fireballs 2 t apart at tiles 8 from his middle, a corpse +3; then 4 arrows every 4 | GA `xarth_plan.md:40-42` | 4 fights | high | OPEN-XA3; OPEN-XA2 (corpse source, conflict: ours every 15 t) |
| 1, 2, 2, 2, 3 | Corpses a volley by living raiders = max(min(n,2), ⌊(n+1)/2⌋) | GA `xarth_plan.md:53-57`; `RAID_PLAN.md:124` | 4 fights | high | — |
| 62 HP; heal = HP | Corpse 16687 (cache 62) walks a tile a tick from +1; reaching him heals its HP as a splat; the 200-heal blast is not recorded | GA `xarth_plan.md:61-68`, `:77-80` | 4/4 | high | OPEN-XA2 (partly) |
| +1, cap 5 | Burn (hitsplat 74): +1 per fireball that hits, never past 5, six beats 4 t apart, then back to 1 | GA `xarth_plan.md:72-75`; `hit_causes_audit.md:180` | 980/983 | high | OPEN-XA8 (conflict: ours 1 ×5) |
| 8 + 3 a volley | Arena fires (loc 62713): 8 random at D, 3 more at M+12 each volley; "fire tile 2" splats unexplained | GA `xarth_plan.md:39`, `:43`, `:154` | 2/2 | medium | OPEN-XA4 (conflict: ours spreading flames) |
| PfMagic 1..8; 25 | Fireball: 1..8 through Protect from Magic (52/59 non-zero), unprayed 25 | GA `xarth_plan.md:148` | 59 + 1 | medium | OPEN-XA1 |
| 11..29 | Arrow unprayed (120/125 zero under Protect from Missiles) | GA `xarth_plan.md:150` | 125 | medium | OPEN-XA1 |
| 9..11; 19; 43, 48 | Melee 9, 9, 10, 11; AoE 19; phase 2 wave 43, 48 inside its ring | GA `xarth_plan.md:151-153` | 4, 1, 2 | low | OPEN-XA1 (partly) |
| 100 to a pillar | A pillar's fall puts a 100 splat on it | GA `hit_causes_audit.md:182` | 18/18 | high | — |

## Ocellus

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| P1 2500; P2-4 5250 | P1 2500 at 1-2 (3630, 4680); one pool for P2-P4, 5250 at 1-2 (7610, 9820): the Blade's damage is his (7610 → 7018 across P2) | GA `ocellus_plan.md:23`, `:252-255` | 2 fights | high | OPEN-OC2 (conflict: our 1,000-HP Blade + half transfer) |
| 5 t; 30/95, 30/125 | P1 volley at every raider every 5 t from his first step; fire (4232, ranged) or water (4229, magic), one element a walk (changes 14 in 65); end 95 within 4 tiles, 125 beyond | GA `ocellus_plan.md:43-49`, `:641` | 256 shots | high | — |
| 0..25 | Volley: prayed 0; unprayed accuracy roll (5 of 13 zero) then 2..24 | GA `ocellus_plan.md:51-52`, `:166` | 13 | medium | — |
| 7 lines, 3 tendrils | Corner charge: 7 crack lines every 2 t from C, from his three inner tiles in turn; explode C+18, blast floor C+19 | GA `ocellus_plan.md:31-32`, `:62-79` | 21/21 | high | OPEN-OC3 (partly) |
| +2 meter, heal 2; 3..5 | A closing line adds 2 to the meter and heals him 2 unless every raider stands on it; each raider on it takes 3..5 | GA `ocellus_plan.md:96-101`; `:639` | 261/261, 232 | high | OPEN-OC10 (conflict: ours +20 HP, 5) |
| 4 a line − 1 + d2 + d3 | A raider's line heal, paid on their next landing hit: 1 line 3..6, 2 lines 7..10, 3 lines 10..14, 4 lines 14..18 | GA `ocellus_plan.md:206-208` | 386 heals | medium | OPEN-OC10 |
| 28..30 | Smash: within 2 of his 3×3 on the explosion tick, hit E+1 | GA `ocellus_plan.md:110-116`, `:642` | 3 | low | — (ours 35) |
| 66% / 25% | Middle: teleport at ≤ 66% or ≤ 25% (checked on volley ticks); four beats 8 t apart, two diagonals each | GA `ocellus_plan.md:34-35`, `:82-91` | 64/64 | high | — |
| 17..19, 1 shot | Strike "Death... awaits..." every time: one shot a raider at meters 0..10 of 96, each its own 17..19; lands +1 (≤4 tiles) or +2; "Embrace..." never recorded | GA `ocellus_plan.md:38-39`, `:102-108` | 13/13 | high | OPEN-OC11 (partly: >10 meter unrecorded) |
| T+11, 53, 95 | P2: fixed script; rounds of 6 volleys at R+0..25, the gap portals one of three fixed sequences (first random, then orange → red → green); fired line 19..25 | GA `ocellus_plan.md:262`, `:279`, `:644` | 39/39 rounds, 10 | high | OPEN-OC5 (partly) |
| T+123 / T+131 | P2 → P3 is a fixed script, not HP: "This... is enough..." T+123, becomes P3 T+131; same to the tick in 4 fights | GA `ocellus_plan.md:264-273` | 4/4 | high | OPEN-OC4 |
| lvl 1601 | P3 `level_change` 1601 at three raiders (cache 1197; does not fit the ×1.06 rule) | GA `ocellus_plan.md:268` | 1 | low | OPEN-OC1 (partly) |
| triple, triple, big | P3 rotation: triple (basic +0, +3, slash +6; next +10), triple, then spin and Insolence by turns; volley in place of a triple when the target is not beside him | GA `ocellus_plan.md:320-330` | 7 fights | high | OPEN-OC6 |
| h ≤ Q+8 / Q+1 | Untether replaces Insolence when HP ≤ next quarter + 8%; quadrant falls at ≤ Q+1 (Q = 75/50/25), at once unless spinning or unleashing | GA `ocellus_plan.md:339-353` | 15/15, 21/21 | high | OPEN-OC6, OPEN-OC9 |
| party #2, #3, ... | P3 tank: the 2nd raider in HUD order, the next each segment, wrapping; vine never on the tank while another lives | GA `ocellus_plan.md:578` | 14/14 | high | — (pre-28 Oct) |
| PfM 1..7; 12, 19 | Triple swings: Protect from Melee 1..7 (87/125 non-zero), unprayed 12, 19 | GA `ocellus_plan.md:646` | 125 | medium | — |
| 15..27 | Spin: hits on each loop seq and the out seq, blocked by Protect from Melee if back on in time; held through it, ours treats it as unprayed (cost unrecorded) | GA `ocellus_plan.md:647`, `:935` | 78 | medium | OPEN-OC7 (partly) |
| 25..28 / 39..41 | Slash 25..28; Insolence superior slash 39..41, each at a random raider's tile | GA `ocellus_plan.md:648-649`, `:933` | — | medium | — |
| U+6..18; U+29/30 | Untether: pools on every raider at U+6/10/14/18, landing +4: 23..29 unprayed, Protect from Magic 0; U+29 35..42 to everyone not under him, U+30 34..41 to those under (18..21 if hit at U+29) | GA `ocellus_plan.md:355-362`, `:934`, `:936` | 13/13, 7/7 | high | OPEN-OC13 |
| 6..11; 6..7 / 2 t | Void bombs: one per standing quadrant, two batches a segment on non-tanks' tiles, 6..11 on its tile; the patch left 6..7 every 2 t (stays into P4) | GA `ocellus_plan.md:651-652`, `:812`, `:932` | 39/39 | high | OPEN-OC15 (conflict: ours 10 after 2 t under non-tanks) |
| 1 vine a segment | Vine with the first triple after the segment's first spin, unless within 8% of the next quarter; never the tank | GA `ocellus_plan.md:332-337`, `:931` | 17, 31/31 | high | OPEN-OC16 (partly: damage unrecorded) |
| 44 HP; max(1, n/2) | P3 rifts 44 HP (cache 16678-16681 stat4 44), one kind a segment in the order Augmenter → Void Rift → healing; count max(1, living/2) (1 at three, 2 at four) | GA `ocellus_plan.md:317`, `:733-737`; `RAID_PLAN.md:125` | 7/7 | high | OPEN-OC17 |
| heal 12/14 every 4 t; 39..41 | Healing rift heals him 12 or 14 every 4 t (blocked by a raider on the line); its killer is healed 22; an unkilled rift detonates 30 t after its add: 39..41 to every raider | GA `ocellus_plan.md:739-744`, `:887`, `:938` | 10, 29/32 | high | OPEN-OC17 (conflict: Siphon heal per melee) |
| locs 62693-62700 | The four censers exist (`TFA_OCELLUS_ARTIFACT_*` + `_INACTIVE`, confirmed in our cache 2757); used at range; 43 lights across all phases | GA `ocellus_plan.md:16`, `:374-390`, `:942` | 7/7 | high | OPEN-OC14 |
| ½ HP; 60-t cycle | P4: wind at D+4 and D+35 hits every raider for half their health; Deflect Melee (icon 15) D+0..30, Deflect Missiles/Magic (16) D+31..59, Soul Split (11) at D+25, D+53 | GA `ocellus_plan.md:397-400`, `:676-686` | 11/11 cycles | high | OPEN-OC8 (deflect order as TR, conflict LU) |
| random kind; 5..8 +c | Hybrid (D+24, D+52): kind random (16 magic, 13 ranged, 4 melee); one value for the whole party, 5..8 under the matching prayer, +1 a cycle from the third (max +3); unprayed 35 once | GA `ocellus_plan.md:807`, `:889-890` | 28/28 casts | high | OPEN-OC8 (conflict: ours alternates) |
| 14..16 | P4 explosions: six after each wind, 2 t apart, on the raider's start-of-tick tile, land next tick | GA `ocellus_plan.md:712`, `:797` | 38/38 | high | — |
| 9..11 a tick | P4 void floor every tick on it (ring D+5..18, quadrant D+37..48) | GA `ocellus_plan.md:891` | 11 | medium | — |
| orb: wind back +8 | Orb taken: heals the wind's hit (+8 on its ready tick only); not taken by W+25 / W+19: he heals that raider's wind hit | GA `ocellus_plan.md:715-717`, `:801-803` | 87/87, 13/13 | high | — |
| 2, 3, 4, 5 | P4 rifts at D+59: 2, 3, 4, then 5 every cycle, any party | GA `ocellus_plan.md:401`, `:888` | 11/11 | high | OPEN-OC17 |
| ⌊d/10⌋ back | A deflect blocks the hit and returns a tenth of it the next tick | GA `ocellus_plan.md:892` | 3/7 | low | — |

## OPEN ids not settled by the captures

AG (none fully open), EZ5 (finale damage reduction), BK2 magic resistance (untested), MO4 (shadow shield: never seen),
XA6 (shield halving), XA11 (corpse freeze lengths), OC3 stripped barrage dbtable (the cracks' geometry is recorded,
the table is not), OC5 damage curve, OC7 the held-prayer cost, OC16 vine damage, DH1 the Monks' own attacks.
Raid-wide TFA ids (TFA1-31, NX, UI) are not touched: the captures are spectator views of fights.

## Conflicts

With our docs / the wiki:
- Guardian grounded 35, 41, 47 (not 34); enrage blast divides by party (not 35 × players); the inert battery is the
  **south-east** one; no "lash behind him" attack; mushroom burst has no prayer drain or bind recorded.
- Ezekiel heal 10% of max (not 50); finale strikes follow a fixed 6-cycle; finale position by nearest side.
- Bork: stun 14 t (not 8; the gameval name `14_CYCLE_STUN01` agrees); missed bomb hurts no raider (not 20); retarget
  only on anger; shots one style at random; flat armour fits 10 (not 8 + 2 per 25%); fire tiles 2 a tick (wiki 6-7).
- Moia: points radius 7 (not 9); binder max 22 (not 15); return heal 195-215 (wiki +80).
- Dreadnoth: jellies min(12, 3·n·k) (not 1 per 2 players); blood permanent, 7-8 every 2 t; landing heal = damage dealt;
  jelly HP 75 in cache 2757 (our doc's 61 is the wiki's).
- Dagon'hai: 6 imps a break from 2 rifts; imps hit to 39; Saradomin heals the catcher 13.
- Xarth: 3 pillars; phase 2 on the third fall, not 50%; burn stacks to 5; corpses from volleys every 21 t.
- Ocellus: one pool for P2-P4; P1 line heal 2 (not 20); P3 hybrid kind random; censers exist (OC14).

With the plugin values in [runelite-timings.md](runelite-timings.md):
- LU Guardian "40 t on every later down": captures 41 then 47 (+6 each).
- LU Ezekiel sword launch 15 t after spawn: captures 14 (S → S+14). LU finale "random order": captures a fixed cycle.
- LU Bork stun 16 t: captures 14 t to the stomp-in (L+14).
- LU Moia first cough +3 t: captures I+4 (the infection's end 120 → +4).
- LU Xarth corpse "stands 2 t": captures walking from +1.
- LU vs TR P4 deflect order: captures back TR (Deflect Melee first).
- Agree: Moia magic +6 and chants 41/39/37; Dagon'hai channel 18 t and Elder 5 t; Xarth 4-t clock; Ocellus P2 portal
  sequences (LU's are the captures' minus one); P3 triple 3 t apart.
