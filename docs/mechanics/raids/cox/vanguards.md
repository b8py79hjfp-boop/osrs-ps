# Vanguards (CoX combat room: melee, ranged and magic Vanguard)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Vanguard" (Page), "Chambers of Xeric" (Main), "Chambers of
Xeric/Strategies" (Strat), "Chambers of Xeric/Challenge Mode" (CM); cache 2757 (`cache-dump config npc <id>`; the `stats` array is
`[1,1,1,1,1,1]` on every Chambers NPC because the raid scales them at runtime, so numbers come from the wiki infobox and the cache
`params` map: params 3 = magic attack bonus, 4 = ranged attack bonus, 5 / 6 / 7 = stab / slash / crush defence bonus, 8 = magic
defence bonus, 10 = melee strength bonus, 12 = ranged strength bonus, 65 = magic strength bonus; they agree with the infobox for
7527, 7528 and 7529); generated symbols (`Npcs`, `Seqs`, `SpotAnims`, `Locs`, `Objs`). Raid-wide rules: `../cox.md` (not part of
this set). Seconds are converted at 0.6 s per tick. Tick timelines below list only what a source states; everything else is
`OPEN-VG<n>`.

## Data

- NPC ids (the infobox uses the same ids in Normal and Challenge Mode): **7525** `RAIDS_VANGUARD_DORMANT` (size 3, no ops; underground),
  **7526** `RAIDS_VANGUARD_WALKING` (op1 "Attack"; the infobox "Moving" version), **7527** `RAIDS_VANGUARD_MELEE`, **7528**
  `RAIDS_VANGUARD_RANGED`, **7529** `RAIDS_VANGUARD_MAGIC` (all op2 "Attack", size **3**, category 1015, param 510 "Vanguard - Melee" /
  "- Ranged" / "- Magic"). No combat level, aggressive, poison and venom immune (resistance 100). **Attack speed 4**.
- Each fights with one style and each attack is **three hits** (VG2). Max hits (infobox, scaled for a maxed player): magic **22**,
  melee **18**, ranged **22**; CM **33** / **27** / **33**. XP bonus magic 27.5 (CM 40), melee 7.5 (12.5), ranged 5 (7.5).
- Stats (infobox): Normal att 150, str 150, def 160, magic 150, ranged 150; CM 225 / 225 / 240 / 225 / 225. Offensive bonuses: melee
  attack +20, strength +10; magic attack +40, magic strength +25; ranged attack +40, ranged strength +25. Defence bonuses:

  | Form | stab | slash | crush | magic | light | standard | heavy |
  |---|---|---|---|---|---|---|---|
  | Melee 7527 | 150 | 150 | 150 | **20** | 400 | 400 | 400 |
  | Ranged 7528 | **55** | **60** | **100** | 400 | 300 | 300 | 300 |
  | Magic 7529 | 315 | 340 | 400 | 110 | **50** | **50** | **50** |

  Cache params 5 / 6 / 7 / 8: melee 150 / 150 / 150 / 20, ranged 55 / 60 / 100 / 400, magic 315 / 340 / 400 / 110. So: ranged Vanguard is
  weak to melee, melee Vanguard is weak to magic, magic Vanguard is weak to ranged (combat triangle; Page).
- HP (infobox, scaledhp): **180** Normal, **280** CM (x1.56, not 1.5: **OPEN-VG1**). Party size and highest combat level: **OPEN-VG2** (no
  wiki formula). Stat regeneration: the Mod Ash list (Discord 9 Jul 2026) does not name the Vanguards, "otherwise default should be
  100", so **1 per 100 ticks** (stat drains are however undone by the reset, VG5).
- Animations (cache seq names): `VANGUARD_DORMANT` 7427, `VANGUARD_SPAWN` 7428, `VANGUARD_WALK` 7429, `VANGUARD_IDLE_BASIC` 7430,
  `VANGUARD_HEAL` 7431, `VANGUARD_DEATH` 7432; magic `VANGUARD_IDLE_MAGIC` 7434, `_DEFEND_MAGIC` 7435, `_ATTACK_MAGIC` 7436; melee
  `VANGUARD_IDLE_MELEE` 7439, `_DEFEND_MELEE` 7440, `_ATTACK_MELEE` 7441; ranged `VANGUARD_IDLE_RANGED` 7444, `_DEFEND_RANGED` 7445,
  `_ATTACK_RANGED` 7446; transitions 7433, 7437, 7438, 7442, 7443, 7447. Spot anims / projectiles: `RAIDS_VANGUARD_MAGIC` 1331,
  `RAIDS_VANGUARD_RANGE_0` 1332, `_1` 1333, `_2` 1334 (the three ranged projectiles); health swirls
  `RAIDS_VANGUARD_SWIRL_LOW_HEALTH_SPOTANIM` 56 and `RAIDS_VANGUARD_SWIRL_HIGH_HEALTH_SPOTANIM` 57. Pet walk seq `VANGUARD_PET_WALK` 7984.
- Scenery / objs: book loc `RAIDS_VANGUARD_BOOK` 30026 (visible 13484), obj `RAIDS_VANGUARD_BOOK` 20895. Potions are `RAIDS_VIAL_ELDER_STRONG_4`
  20924, `_TWISTED_` 20936, `_KODAI_` 20948, `_REVITALISATION_` 20960, `_PRAYER_` 20972, `_XERICAID_` 20984, `_OVERLOAD_` 20996.

## Fight

- **VG1 Start:** the three Vanguards start underground (7525). "When a player gets near one of the holes, all three will burst from
  the ground" (Page). Their aggression range was reduced and one spawn position shifted on 21 May 2020. The range value is
  **OPEN-VG3**.
- **VG2 Attacks:** each attack is **three hits** every 4 ticks. The melee Vanguard hits its target three times. The magic and ranged
  Vanguards launch **three projectiles**: one is aimed at the target and "the other two are launched elsewhere", so a player can be
  hit by two or all three (Page, Strat; AoE shape and the placement of the other two are **OPEN-VG4**). Protection prayers "will not
  nullify their damage, but ... reduce it by a significant amount" (Page; Strat "significantly reduce the accuracy and damage"); the
  reduction is **OPEN-VG4**. Target selection (the nearest, the first attacker, etc.) is **OPEN-VG3**.
- **VG3 Equal damage rule:** damage must be "spread equally". If one Vanguard's HP is ahead of the lowest by more than the
  threshold, all three shuffle and **fully heal**: threshold **40%** in groups of four or fewer, **33.3%** (Page "33.3%", Strat "33%") in
  groups of five or more (28 May 2020 changed four-or-fewer from 33% to 40%; 6 Apr 2017 raised it from 20% to 33%). The Strat words it
  as "the Vanguard with the most health cannot be more than 33% of its maximum hitpoints ahead of the Vanguard with the least"; so
  the difference is read as a fraction of max HP (the exact comparison, strict or not, and which party size counts, real or scaled,
  are **OPEN-VG5**).
- **VG4 Warning swirls:** near the threshold a dark spiral appears around the Vanguard with the most health and a light one around the
  Vanguard with the least (Strat; Page: a swirling mass that brightens the closer they are to a forced heal); spot anims 56 and 57.
  The activation margin is **OPEN-VG5**.
- **VG5 Shuffle:** they also shuffle every **20 to 36 ticks** on their own (Mod Ash, 14 Sep 2021, "I think"). While shuffling they move
  around the room clockwise or anticlockwise, cannot be targeted ("in their shell"; attacks made before they started to move still
  land) and lose aggression on their current target; players in their path take **3-6** damage every few ticks. They reemerge once
  all three are in position. A hit that would be fatal while they shuffle leaves the Vanguard at **1 HP** (Strat; Page: "survive
  and restore 1 health"; 23 Jun 2021 they can no longer become immortal at 0 HP). **All drained stats are restored** when they
  retreat (Page). "Vanguards can be damaged during their transition phase" (16 Jun 2021 change); the transition window is
  **OPEN-VG6**. A player standing under the ranged Vanguard stops it moving (Strat).
- **VG6 Reset:** the forced heal sends all three through the same shuffle as VG5; "tweaks to the reset mechanic" were made on 28 Feb 2019.
  Whether HP, defence and aggression are restored identically: **OPEN-VG6**.
- **VG7 Death:** the book "Vanguard judgement" is dropped once all three are dead (unless read, 26 Mar 2025). Each Vanguard drops
  (since 29 Nov 2023): melee -> Elder (+)(4), 2 Xeric's aid (+)(4); ranged -> Twisted (+)(4), 1 Xeric's aid (+)(4), 2 Revitalisation
  (+)(4); magic -> Kodai (+)(4), 1 Xeric's aid (+)(4), 1 Prayer enhance (+)(4). **Overload (+)(4)**: each has a 1/3 chance, at least one is
  given per Vanguard room, up to a maximum of 3 (Page; 21 May 2020 guarantee). A message names the top damage dealer; potions are free
  for all (Strat).

## Points (stated)

- Combat points come from damage dealt (Main); the formula, cap and mini-boss "decay" multiplier are **OPEN-VG7**. Nothing room-specific is
  stated. Raid-wide rules: `../cox.md`.

## Per-mode differences

- **Normal:** HP 180, max hits 22 / 18 / 22.
- **Challenge Mode:** HP 280, att / str / def and magic / ranged 225 / 240 (x1.5), max hits 33 / 27 / 33. CM-Strat: avoid tanking more than
  one Vanguard at a time ("will likely result in a swift death, especially from the melee or ranged"); tactics otherwise as Normal. CM
  is on the middle level.

## Tick timeline (stated parts)

- Attack speed 4, three hits each. Shuffle every 20-36 ticks. Path damage 3-6 "every few ticks". No timings for spawn, projectile
  flight or re-emerge.

## Changes up to 2026-11-03 that matter

26 Jun 2024 hotfix (appearance); 29 Nov 2023 standardized drops; 23 Jun 2021 no immortality at 0 HP; 16 Jun 2021 damageable during
transition; 28 May 2020 visual cue, 40% threshold for four or fewer; 21 May 2020 defences lowered, HP raised, melee Vanguard's
magic and melee defences swapped, aggression range reduced, a spawn shifted, overload guaranteed; 28 Feb 2019 reset tweaks; 6 Apr
2017 threshold 20% -> 33%; 2 Feb 2017 health bars shown; 19 Jan 2017 potion drops. No entry after 26 Jun 2024.

## Open list

OPEN-VG1 CM HP multiplier (x1.56); OPEN-VG2 HP and stat scaling by party size and highest combat level; OPEN-VG3 aggression range and
target selection; OPEN-VG4 projectile AoE / placement and protection-prayer reduction; OPEN-VG5 exact threshold comparison, party
size used and the swirl margin; OPEN-VG6 transition damage window, forced-heal details; OPEN-VG7 point values. **7 open items.**
Working assumptions: threshold compared to current-HP difference over max HP, strict "greater than"; party size = scaled raid size.

## Conflicts between sources

1. Threshold wording: Page "40% in groups of four or less, and 33.3% in groups of five or more" vs Strat "33% of its maximum
   hitpoints" (same numbers; the Strat is explicit about max HP).
2. Targetability: Page "cannot be attacked while in their shell" vs the 16 Jun 2021 Main change "Vanguards can be damaged during their transition phase".
3. Fatal hit while shuffling: Page "survive and restore 1 health" vs Strat "left at 1 hitpoint" (probably the same).
4. CM HP 280 (x1.56) vs the CM page "increased by 50%" (270).
