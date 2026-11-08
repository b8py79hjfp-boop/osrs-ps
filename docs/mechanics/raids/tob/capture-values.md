# ToB values measured in RSProx captures

Read 2026-11-08. Research notes only: nothing here is implemented yet. Every value is a measurement another project
took from RSProx packet captures of the live game. Facts only: no code or prose is copied.

## Source

| Key | Repository @ commit | What it is |
|---|---|---|
| TB | `MRobertEvers/oldschool-clientc` @ `3cce15be706d028b6eafe53b080a808dc393baac` (2026-11-07), dir `docs/rsprox-analysis/tob/` | One file per room (`maiden.md`, `bloat.md`, `nylocas.md`, `sotetseg.md`, `xarpus.md`, `verzik.md`) plus full tool outputs (`*.out`) |

- **Licence:** no licence file at the repository root; treated as all rights reserved, facts only (see
  [../tfa/capture-values.md](../tfa/capture-values.md)).
- **Captures:** rsprox.net submissions 3299, 3285, 788 (rev 234, Grid Master), 1833, 1764, 553, 1779, 1837, 582
  (rev 227, Leagues V); Hard 1852, 1837; Entry 101, 554 (TB `README.md:20-36`). 28-31 Normal Mode kills per room.
- **Every world is seasonal.** Leagues V takes about a tenth off what a raider is dealt (the Maiden's heal off a pool is
  the splat + 1; TB `maiden.md:101-107`), and Last Stand holds raiders at 1 HP. **No damage range below is unmodified**;
  timings, counts, projectiles and heals the server computes itself are not affected.
- Projectile heights are in "our units" (raw ÷ 4); times in client cycles (30 a tick).

Confidence: **high** = n/n over the kills; **medium** = fitted or seasonal damage; **low** = 1-3 samples.

## Maiden

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| 10 t; first +9 | Attack every 10 t; first 9 t after her add (25 of 32) | TB `maiden.md:38-39` | 427/428 | high | — |
| proj 1577: 109 + 10d | Storm from her centre (SW+3,3), start 109, end 109 + 10 × Chebyshev distance; lands ⌊end/30⌋ t after (4 at 3 tiles ... 7 at 11) | TB `maiden.md:47-49` | 362/362 | high | — |
| nearest | Storm target: the raider nearest her centre tile | TB `maiden.md:117` | 750/750 | high | — |
| 30%; ⌊hit/5⌋+1 | Storm drain on the **throw** tick ("Your stats are drained."), 30% of storms; amount ⌊hit/5⌋ + 1 | TB `maiden.md:120-122` | 79/265, 69/75 | high | — (conflict: wiki 50%, (hit+1)/5) |
| proj 1578: 20 + 15d | Blood: one projectile per raider (start 20) plus two extras (start 45, end +25) within 2 of one raider; aimed at tiles | TB `maiden.md:57-60` | 388/388 | high | OPEN-MA2 (not settled: which raider) |
| 5..10 + 2c | Pool damage a roll of 5 to 10 + 2c (c = crabs leaked), every tick on it; heals her the same | TB `maiden.md:128-131` | 233 | medium | — (conflict: ours flat 10 + 2c) |
| 29 t; +10 | Trail life 29 t; blood spawn rises landing + 10; first step the tick after its add; from a pool 9% (nobody stood on it) / 12% (stood on) | TB `maiden.md:62`, `:71-72`, `:139-140` | 1226/1228, 50/50 | high | OPEN-MA3 (conflict: wiki ~33-50 t) |
| 1 tile/t; heal 2×HP | Matomenos walk from add + 1, one tile a tick; reaching her heals 2 × its HP (a full 75-HP crab 150) | TB `maiden.md:146-147` | 807/807 | high | OPEN-MA9; OPEN-MA4 (partly: party size of the 75 not stated) |
| 2/4/6/8 | Crabs a wave by party, on the form-change tick (70/50/30) | TB `maiden.md:79-80` | 31 kills | high | — |
| T, T+1, T+4, T+8 | Death: dying_a T, death_a T+1, dying_b T+4, deleted + remains loc + book T+8 | TB `maiden.md:163-166` | 31/31 | high | — |
| Hard: prayed ≤ 50 | Hard storm hits up to 50 through prayer | TB `maiden.md:119` | 113 | low | OPEN-MA8 (partly) |

## Bloat

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| 38-47 t | First sleep 38-47 t after the fight track; awake 38-47 t between stomp and next sleep | TB `bloat.md:36`, `:40` | 28/30, 35/37 | high | OPEN-BL4 (timing; conflict: ours walk 34-42) |
| +30 / +31 / +33 | Stomp at sleep seq + 30, its hit + 31, rise + 33 | TB `bloat.md:38-39`, `:112` | 37/37, 60/60 | high | — |
| ≥ 6 tiles, sight | Stomp hits raiders up to 6+ tiles off her 5×5 as sight allows; none of 3 under her hit | TB `bloat.md:113` | 3 under | low | OPEN-BL5 (partly) |
| 35-69 / 37-83 | Stomp damage Normal / Hard (seasonal) | TB `bloat.md:114` | 37, 23 | medium | OPEN-BL7 (Hard, partly) |
| 4 or 6 t; 16 (15) | Falling flesh: waves every 4 (55) or 6 (28) t, 16 or 15 a wave, random tiles (2 of 1513 on a player), splat 3 t after; hit on the splat tick to a raider on the tile; first own step at splat + 4 | TB `bloat.md:55-58`, `:121-123` | 95 waves, 44/44 | high | OPEN-BL2 |
| 23-42 / 27-53 | Flesh damage Normal / Hard (seasonal) | TB `bloat.md:124` | 25, 19 | medium | — |
| every tick | Flies: a seen raider takes a splat every tick, landing next tick; Normal 9-18 unprayed, 7-13 under Protect from Missiles (seasonal) | TB `bloat.md:101`, `:104` | 77, 10 | medium | — |
| spread | A swarm thrown from the tile of a raider flown the tick before, at a raider 1-9 tiles away (about a third each distance) | TB `bloat.md:105-106` | 250/282 | medium | — |
| death +3 | Death seq on the killing tick, deleted + books + chest open 3 t later | TB `bloat.md:138-144` | 30/30 | high | — |

## Nylocas

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| 4, 4, 4, 4, 16 | First waves 4 t apart then 16 (25 of 30); later gaps 8-64 as the cap holds them; last waves 16, 8, 12, 8, 8, 8, 4, 8; incoming nylos move a tile a tick | TB `nylocas.md:51-56` | 30 kills | high | OPEN-NY5 (partly: lane tiles not given) |
| 52 / 55 t | Lifetime: small 52, big 55; big death splits at +7 | TB `nylocas.md:26`, `:65-67` | 994, 78 | high | — |
| every 3 t | Support bite every 3 t per nylo (no splat; the bar updates) | TB `nylocas.md:76-78` | 20201 gaps | high | OPEN-NY12 (rate only) |
| ≤ 2 tiles; 10/tile | Wave nylos throw from at most 2 tiles; flights 10 cycles a tile (ranged start 39, magic 25) | TB `nylocas.md:84-104` | 1999 throws | high | — |
| T+3; 24 | Support collapse: rockfall aimed at each raider lands T+3 (one sample 24 damage); rubble T+7 | TB `nylocas.md:134-138` | 10 collapses, 1 hit | low (dmg) | OPEN-NY12 (partly) |
| 10 t, 2 attacks | Vasilias: form change every 10 t, 2 attacks a form; first attack +1 or +4 after landing; melee adjacent only; deleted 5 t after death seq | TB `nylocas.md:112-117` | 251/251, 279/279 | high | OPEN-NY15 (partly) |
| lands 56-92 t | Vasilias lands 56-92 t after the last wave's add | TB `nylocas.md:55` | 28 | medium | — |

## Sotetseg

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| 5 t; 10 after death ball | Attack every 5 t; 10 t after a death ball | TB `sotetseg.md:34` | 390 | high | — |
| 203 cycles; +6/+7 | Ball (red `tob_sotetseg_maging`): start 23, end 203 at every distance; lands +6, splats +7 | TB `sotetseg.md:43-44` | 382/393 | high | OPEN-SO7 |
| reach 9; 216 / 36+30·max(d,3) | Bounces: one per other raider within 9 tiles, at most two, one of each colour; grey end 216, red 36 + 30 × max(tiles, 3); nearest raider in 90% of singles | TB `sotetseg.md:58-66` | 332/393 | high | OPEN-SO7 |
| 480 cycles; +16 | Death ball end 480 at any distance; splat, impact and sound on landing + 16 | TB `sotetseg.md:73-76` | 27 | high | OPEN-SO7 (conflict: BL plugin ~15 t) |
| every 7 t; 1-3 | Shadow realm drain every 7 t, its hit (1-3) 2 t after | TB `sotetseg.md:88` | 83/83 | high | — |
| 1 t; zap ±1 | Wrong tile: changes for 1 t; zap to each raider one tile off it; the hit that tick or the next | TB `sotetseg.md:96-101` | 170 | medium | OPEN-SO2 (partly) |
| hum every tick | Tornado hums every tick at its 3×3; two alive at once in 50 lives | TB `sotetseg.md:107` | 77 | high | OPEN-SO8 (partly: no speed/damage) |
| T+3 | Death seq on the killing tick; deleted, book, chest open T+3 | TB `sotetseg.md:114-115` | 28/28 | high | — |

## Xarpus

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| 7/12, 9/8, 12/8, 15/4 | Exhumeds: solo 7 every 12 t, duo 9 every 8, trio 12 every 8, four 15 every 4; first 8-11 t after the start; life 11 t | TB `xarpus.md:57-59` | 286/286 | high | OPEN-XA1 (conflict: Strat 12/9/6) |
| 20 / 16 / 12 / 9 | Heal per orb by party (1/2/3/4); first orb 3 t after the add, then every tick uncovered; heal lands ⌊end/30⌋+1 after the orb (end 10 × distance) | TB `xarpus.md:60-63` | 88/88 | high | OPEN-XA4 |
| 8-9 t | Last exhumed close → combat transform | TB `xarpus.md:65` | 28 | high | OPEN-XA1 (what ends P1) |
| S−2 lock; 4 t; first +9 | Spit every 4 t, first 9 t after the transform; targets the tile at the end of tick S−2 | TB `xarpus.md:27-37`, `:71-72` | 22/22, 464/464 | high | — |
| 30 + 30·max(1,d) | Spit flight; 1 or 2 splashes a landing at other raiders' locked tiles (else random arena tiles, pooled or not) | TB `xarpus.md:73`, `:84-85`, `:110` | 603 | high | OPEN-XA5 (partly) |
| 2-7 poison | Pool and spit: poison splat 2-7 Normal, 1-10 Hard (seasonal); a splat every tick on a pool | TB `xarpus.md:78`, `:178-181` | 151 | medium | OPEN-XA5 (partly) |
| 237-281 ‰ | Stare starts at HUD 23.7-28.1%, 3 t after the last spit, "Screeeeech!" + "Xarpus begins to stare intently."; turns every 8 t | TB `xarpus.md:161-163` | 28/28, 122/122 | high | OPEN-XA7 (partly) |
| 41-72 | Stare retaliation: one poison splat a tick, 41-72 Normal, 50-74 Hard (seasonal) | TB `xarpus.md:183-184` | — | medium | OPEN-XA3 (partly) |
| 3-7 ×2 | Guano (a raider under him): two damage splats the next tick; the spit is never held back | TB `xarpus.md:146-149`, `:182` | 29/45 | medium | OPEN-XA10 (partly) |

## Verzik

| Value | Meaning | Source | n | Conf. | Settles |
|---|---|---|---|---|---|
| +4 / +17 / +20 | P1 death → transition npc +4, becomes P2 +17, first P2 attack +20 | TB `verzik.md:14-21` | 28/28 | high | OPEN-VZ9 |
| +2 / +6 / +12 | P2 death → transition +2, P3 +6 ("Behold my true nature!"), first P3 attack +12 | TB `verzik.md:23-25` | 27/27 | high | OPEN-VZ9 |
| proj 1580: 20 + 15d | P1 bolt; hit ⌊end/30⌋ t after the cast | TB `verzik.md:48-50` | 50/50 | high | — |
| 30·⌊2d/3⌋ | P2 bomb, blood and lightning flights; Athanatos 90·⌊2d/3⌋ (hatches 6/9/12/15 t) | TB `verzik.md:69-78` | 180/182 | high | — |
| ≤ 35 | P2 slam damage (18 non-zero, seasonal); thrown 3 tiles (stomp under her 5), stun to T+6 (T+7) | TB `verzik.md:138-144` | 57/57 | low (dmg) | — |
| 26-57 | Pillar collapse damage (seasonal), within 2 of its centre, pushed 1 tile | TB `verzik.md:145-147` | 30 | medium | OPEN-VZ4 (not settled) |
| min(53/62 + 15d, 179) | P3 magic / ranged autos; green ball start 62 end 242; webs 30 × ⌊d/2⌋; yellows 261 → 405 | TB `verzik.md:94-104` | — | high | OPEN-VZ10 (web flight) |
| 8386 = tornado | `tob_verzik_creeper` 8386 is the tornado; one per raider with "I'm not finished with you just yet!" | TB `verzik.md:113-120` | 83 lives | high | OPEN-VZ1; OPEN-VZ7 (partly: text) |
| 1 tile/t; 3×⌊hp/2⌋; T+16 | Tornado walks a tile a tick; on touch heals her 3 × ⌊raider HP / 2⌋, hits ⌊hp/2⌋ (seen 0.85× on seasonal worlds); next tornado T+16 | TB `verzik.md:121-126` | 13/13, 24/24 | high | OPEN-VZ13 (conflict: SP plugin 18 t) |
