# Kephri (Tombs of Amascut, Path of Scabaras boss)

Date anchor 2026-11-03; cache 2757. Sources read 2026-11-08 (labels used below):

- OSRS Wiki pages (copies in `/tmp/claude-0/ref/wiki-toa/`): "Kephri" (Page), "Soldier Scarab" (Soldier), "Spitting Scarab"
  (Spitting), "Arcane Scarab" (Arcane), "Agile Scarab" (Agile), "Scarab Swarm (Tombs of Amascut)" (Swarm), "Egg (Tombs of
  Amascut)" (Egg), "Fossilised dung", "Tombs of Amascut/Strategies" Kephri section and "Kephri's Invocations" (Strat), "Tombs
  of Amascut" with its changelog (Main; `Tombs_of_Amascut.txt`, `Raid_level.txt` and `Invocations.txt` are byte-identical, so
  Main also covers the Raid Level and Invocations tables), update posts "Summer Sweep Up: Combat" 25 Jun 2025 (Sweep) and
  "Tombs of Amascut Drop Mechanics & Osmumten's Fang" (Drops).
- Cache 2757: `cache-dump config npc|loc|obj|seq|spotanim|struct`. NPC `stats` order is attack, defence, strength, hitpoints,
  ranged, magic. NPC params, read against the infobox on Akkha, Kephri and the scarabs: 3 = magic attack bonus, 4 = ranged
  attack bonus, 5 / 6 / 7 / 8 = stab / slash / crush / magic defence, 10 = melee strength bonus, 12 = ranged strength bonus, 14
  = attack speed, 65 = magic damage bonus. The cache has no melee attack bonus and no ranged defence param (infobox only).
  Param 26 is unlabelled (Kephri 5, spitting 4, arcane 5, soldier 1, agile 4, egg 2) and unused. Invocation text comes from
  cache structs 437-441 (params 1160 name, 1161 category, 1162 raid-level modifier, 1262 description; category 11 = Kephri).
  Generated symbols: `Npcs`, `Seqs`, `SpotAnims`, `Locs`, `Objs`.
- RuneLite plugins (facts only, in `/tmp/claude-0/ref/plugins-toa/`): MT = toa-mistake-tracker `c73766d` (`KephriDetector`,
  `WardensP3Detector`), TA = tombs-of-amascut (LlemonDuck) `831c39b` (`SwarmerOverlay`, `PathLevelTracker`), ST =
  tombs-of-amascut-stats `2dc8a8e`, PT = toa-points-tracker `3d4bb73`.

Raid-wide rules live in a `../toa.md` that does not exist yet. Seconds are converted at 0.6 s per tick; the plugin constant
is 30 client cycles per tick (600 ms / 20 ms). Timelines list only what a source states; everything else is `OPEN-KP<n>`. No
source gives a damage number by raid level for any Kephri attack, and none gives the HP thresholds of the shield phases.

## Data

- **Kephri NPC ids** (cache names agree with the Page infobox ids 11719 / 11720 / 11721 / 11722):

| Id | Cache name | Role | Ops | `stats` HP |
|---|---|---|---|---|
| 11719 | `TOA_KEPHRI_BOSS_SHIELDED` | shielded (Page "Shielded") | op2 Attack | **150** |
| 11720 | `TOA_KEPHRI_BOSS_WEAK` | recovering, stunned idle (stand anim 9580) | none | 150 |
| 11721 | `TOA_KEPHRI_BOSS_ENRAGE` | final phase (Page "Aggressive") | op2 Attack | **80** |
| 11722 | `TOA_KEPHRI_BOSS_DEAD` | dead | none, category -1 | 80 |

  Common to 11719-11722: size **5**, category 1692 (dead: -1), combat level 341, attribute kalphite, `stats` attack 0, defence
  80, strength 0, ranged 0, magic 125. MT lists 11719-11721 as the phase ids, 11722 as dead, and treats 11720 as the form in which
  Vengeance does not count (see Plugins).
- **Kephri bonuses**: magic attack +125 (param 3), ranged attack 0, strength 0, magic damage +50 (param 65, infobox 50). Defence:
  stab **60**, slash **300**, crush **100**, magic **200** (params 5-8, infobox agrees); ranged light / standard / heavy
  **300** (infobox only). **Attack speed 6** (param 14, infobox). **Max hit 24** (infobox; scale not stated, **OPEN-KP2**).
  Attack style magic. Poison and venom resistance 100, cannon-immune, thralls not immune.
- **Elemental weakness** fire: **40%** on the shielded form, **35%** on the final form (infobox); Main says 40% since 29 May 2024
  and Strat says "35% more damage from fire spells" (conflict 6). Best melee style stab, then crush; slash is "highly resistant";
  ranged and magic very resistant (Strat).
- **Scarab Swarm** 11723 `TOA_KEPHRI_SHIELD_SCARAB`: size 1, combat 8, `stats` [1, 0, 1, 10, 1, 1] = **HP 10**, defence 0, op2
  Attack, defence bonuses **-100** all styles (params 5-8), infobox attack speed 4, not aggressive, cannon- and thrall-immune,
  fire weakness 40%. Players deal their **maximum hit** to swarms (Strat).
- **Soldier Scarab** 11724 `TOA_KEPHRI_GUARDIAN_MELEE`: size 3, category 1693, combat 89, `stats` [75, 80, 80, 40, 95, 100] =
  **HP 40**, defence 80; strength +55, magic attack +250, ranged attack +350; defence stab 15, slash 250, crush 30, magic **-15**
  (Sweep lowered magic defence from +10); melee (slash); **max hit 17**; fire weakness 50%; **attack speed 6 in the cache
  (param 14) versus 7 in the infobox** (conflict 3).
- **Spitting Scarab** 11725 `TOA_KEPHRI_GUARDIAN_RANGED`: same body as the soldier with `stats` attack 1; ranged; **max hit
  10**; **attack speed 3** (param 14 and infobox); poisonous; magic defence 250; fire weakness 50%.
- **Arcane Scarab** 11726 `TOA_KEPHRI_GUARDIAN_MAGE`: same body, `stats` attack 75; magic; **max hit 22**; **attack speed 6**;
  magic defence 75; fire weakness 50%.
- **Agile Scarab** 11727 `TOA_KEPHRI_SCARAB_RANGEKITE`: size 1, category 1694, combat 53, `stats` [60, 5, 20, 30, 40, 30] =
  **HP 30**, defence 5; magic and ranged attack +150, strength +25, ranged strength +5, magic damage +25; defence bonuses 0;
  ranged; **max hit 5**; **attack speed 4**; retreats from players at running speed (Agile). Its stats are not affected by raid
  level (Strat).
- **Eggs** 11728 `KEPHRI_EGG_EXPLODE` (small, light, explodes) and 11729 `KEPHRI_EGG_HATCH` (large, dark, hatches an Agile
  Scarab): name "Egg" (tinted), size 1, category 1694, `stats` HP **35**, defence 0, defence bonuses **-100**; players always
  inflict their maximum hit on eggs (Egg). The Page ids 11728 / 11729 are "Explode" / "Hatch".
- **Bomber (kamikaze) scarab**: Strat and Page call it a "Bomber Scarab" that behaves like Kephri's auto-attack; the cache has
  **no NPC** for it. It is the flying projectile `KEPHRI_KAMIKAZE_SCARAB` 2147 (seq 9593) with explosion graphic
  `KEPHRI_KAMIKAZE_SCARAB_EXPLODE` 2148 (seq 9600).
- **Kephri animations** (cache `Seqs`; the bracketed number is the cache `keyframeEnd`; if one unit is one client cycle then 30 =
  1 tick, which no source states): idle `NPC_KEPHRI_IDLE` 9572 (120), turn 9573 / 9574, walk `NPC_KEPHRI_WALK01` 9419,
  `NPC_KEPHRI_ATTACK_RANGED` 9575 (120), `_ATTACK_RANGED_RADIAL` 9576 (90), `_ATTACK_MAGIC_RADIAL` 9577 (90),
  `_ATTACK_SPECIAL_EGGS` 9578 (90), `_STUNNED` 9579 (90), `_STUNNED_IDLE` 9580 (90), `_STUNNED_WAKEUP` 9581 (210), death 9582
  (90), dead 9583 (30). Which of 9575-9577 is the fireball is not stated (**OPEN-KP3**).
- **Scarab animations**: walk 9584, walk backwards 9585, idle 9586 (60), melee attack `NPC_SCARAB_ATTACK` 9587 (60), ranged
  attack `NPC_SCARAB_RANGED_ATTACK` 9588 (60), spawn 9589 (90), death 9590 (120); flying set (the arcane scarab idles and
  attacks with it): takeoff 9591, idle 9592 / 9593, attack 9594, landing 9595, spawn 9596, death 9597-9599, explode 9600.
  Swarm: spawn 9605, idle 9606, **integrate (leak into Kephri) 9607**, **death 9608**. Shield visual seqs 9601-9604.
- **Projectiles and graphics** (cache `SpotAnims`; ids marked MT are confirmed as projectile ids by the plugin):
  - Fireball projectile **2266** (MT; the symbol name `VORKATH_AREA_TRAVEL_SMALL` is a shared id, not Kephri-specific);
    ground-shadow telegraphs `GARGBOSS_DEBRIS_SHADOW_120` **1447**, `_90` **1446**, `_60` **2111**; impact graphics
    `VFX_SCARAB_EXPLOSION01-04` **2156-2159** (05-08 are 2160-2163, "B" variants 2168-2171, unreferenced).
  - Dung: `KEPHRI_INDICATE_PLAYER_FOR_DUNG` 2146 (seq 9606, the flies around the target), `PROJECTILE_DUNGBALL01 / 02` 2164 /
    2165, `SPOTANIM_DUNGBALL_EXPLOSION01` 2166 (seq 9609), `KEPHRI_DUNG_DROP` 2145 (seq 9519), `VFX_SCARAB_TRAVEL` 2167. Dung
    locs (1x1, block walk and range): `TOA_KEPHRI_DUNG01-03` 45149-45151 and `TOA_KEPHRI_DUNG01-03_TILE` 62519-62521. Drop obj
    `TOA_KEPHRI_POO` 27214 ("Scarab dung", value 10).
  - Shield: `KEPHRI_SHIELD_ALIVE` 2141, `_DEAD` 2142, `_DEATH` 2143, `_REFORM` 2144; `KEPHRI_LIGHTNING` 2140.
  - Children: `TOA_KEPHRI_MAGE_CHARGE` 2149 (arcane charge swarm), `TOA_KEPHRI_MELEE_HEAL` 2150 (soldier swarm),
    `TOA_KEPHRI_MAGE_INTEGRATE` 2151, spitting scarab `TOA_SCARAB_RANGED_PROJECTILE01 / 02` 2152 / 2154 (seq 8133) and
    `_IMPACT01 / 02` 2153 / 2155 (seq 8134).
- **Regions** (ST, PT): Kephri room 14164, Path of Scabaras puzzle room 14162. ST: hitsplat type **11** is the healing hitsplat on
  shielded Kephri (shared with the Palm of Resourcefulness).

## Scaling

- **Raid level**: every 5 levels add **2% additively** to HP, defence and accuracy and 2% to damage capped at **+150%** (Main).
  **Path level**: the first level +8% HP and damage, each further +5%, up to six levels; mechanics change at path levels 2 and 4,
  capped at 4 (Main). **Party**: 2nd and 3rd member +90% of base HP each, 4th and later +60% each (Main). The order and rounding are
  **OPEN-KP1** (TA's shadow chain for Akkha is raid, then path, then party with rounding to 5 / 10; applying it to Kephri is an
  assumption behind `toa.scaling.model`).
- **Kephri's children** scale their combat stats "barring their Defence" with raid level (Strat); since Sweep (25 Jun 2025) the
  scarabs inside Kephri's fight **no longer scale their Defence**. Agile Scarabs and eggs are not affected by raid level (Strat).
  Swarm HP is "scaled" in the infobox but players always hit swarms and eggs for their max.
- **Arcane Scarab relocation counter** scales with party size (2 + players, see KP9). The Page's HP row (150 shielded, 80 final)
  is the solo, raid-level-0 value (infobox "scaledhp"), the same reading as Akkha.
- Path-level effects (Main, Strat): auto-attacks become **faster** per two levels (amount **OPEN-KP4**); in the shield-charging
  intermission **groups of three** swarms may appear instead of one, "the frequency of these increase at raid level 4"
  (read as path level 4, **OPEN-KP5**). Path levelling no longer changes the Arcane Scarab's charge speed (2 Sep 2022).
- Prayer invocations: Quiet Prayers -10% protection (cache struct 432), Deadly Prayers drains Prayer by 20% of damage taken (433).

## Rules

- **KP1 Forms and phases.** Kephri is shielded (11719), her shield is a scarab swarm pool (graphic 2141) that must be broken
  before she can be damaged. When the shield is gone she becomes **dazed** (11720, no "Attack" op, stunned anim 9579 then 9580),
  calls her **children** and the swarms restore **one shield charge**; at the end of the intermission she wakes (9581) back
  into 11719 (MT treats any change among 11719-11721 as a phase transition). After the last shield the form changes to 11721 (final), whose
  HP is 80 (the real health pool). The number of shield breaks is a source conflict: the Page says "two phases of shields", Strat says
  the swarm "after the third shield" dissipates and starts "her fourth and final phase", and TA only counts two intermissions
  with swarm waves (conflict 1). The HP at which each break happens is not stated (**OPEN-KP6**).
- **KP2 Main attack.** A **fireball** launched into the air "every few ticks" at **speed 6** (cache, infobox) with a magic roll.
  A **shadow on the ground** forewarns the target tile(s); a player on it takes "immense damage" and Protect from Magic
  mitigates **about 66%** (Strat). MT times the hit from the shadow graphic: **1447 -> 4 ticks, 1446 -> 3 ticks, 2111 -> 2
  ticks** after the shadow appears (which shadow is used when is **OPEN-KP7**). It lands on a single tile, or a **3x3** with
  **Aerial Assault**. Impact graphics 2156-2159 appear on every tile of the hit area. MT notes the bomb deals **no damage while
  Kephri's health is 0** (the down state). Path levels speed the attack up, 2 and 4 (Page, Strat). The damage number is the 24
  max hit of the infobox only (conflict 7).
- **KP3 Dung Strike (shielded phases).** Flies buzz around the target (graphic 2146) as a warning; Kephri then throws the player
  back to the rear of the arena ("Kephri throws you back!"), the player is **stunned 2 seconds** (about 3 ticks), and a **row of
  dung** falls along the knock-back direction, shrinking the space. If the player does not move out of the way they
  take **light damage** and are shoved to a walkable tile within **ten tiles**; with none free they take **rapid damage and die
  quickly** (Strat, Page). Targets are chosen in **party-list order, top to bottom** (Strat); with **Blowing Mud** two players
  are hit, the second instance continuing round-robin (first instance players 1 and 2, second players 3 and 1 in a trio); no effect in
  solo. If too many scarabs are alive she uses Dung Strike **in place of Mass Incubation**; the count is **OPEN-KP8**. When the
  swarm around her dissipates (start of the final phase) she uses **one Dung Strike on all players**; a chained second one
  can stun-lock (Strat).
- **KP4 Mass Incubation (shielded phases).** Animation 9578. Eggs are splattered across the arena: small light eggs
  (11728) and large dark eggs (11729). Unattended, they **explode in a 3x3** (light eggs damage the area around them) or
  **hatch Agile Scarabs** (dark eggs). **Lively Larvae** raises the dark eggs from 2 to **4** (cache: "double the number of eggs
  containing an Agile Scarab"; Main 2 to 4; Strat says 3-5, conflict 2). Fuse and hatch time, light-egg count, damage and
  radius numbers are **OPEN-KP9**. Dung landing on a dark egg does not destroy it but **instantly kills the scarab when it hatches**
  (Strat). Agile Scarabs attack at range, are very accurate (accuracy bonus +150), hit up to 5, and run away if approached.
- **KP5 Intermission: children.** On shield depletion Kephri summons "her children"; attackable children are weak to stab like
  her (Strat). **Base**: first break -> **one Spitting Scarab**; second break -> **one Soldier and one Arcane Scarab**. **More
  Overlords** adds one more: a Soldier at the first break and a Spitting Scarab at the second (Main, Strat). Spawn places:
  Spitting NE of Kephri, Soldier E, Arcane SE (Strat). Kill them before resuming the fight.
- **KP6 Shield swarms.** Swarms spawn at **six edge points** (NE, SE, S, SW, NW, N); the first point is random, then **clockwise,
  occasionally skipping one**. First intermission: **28 swarms**, of which the first **18** heal; second: **24**, first **14**
  heal; the rest do not reach her in time. **Spawn speed accelerates in four steps**: 4 swarms every 4 ticks, 4 every 3, 4
  every 2, then the last 16 one **every tick** (stated for the first intermission, the second is not given, **OPEN-KP10**).
  Each swarm that arrives plays animation 9607 and heals the shield **about 10% or less** (less for larger teams); overcharge is
  capped at roughly **15% above** her maximum. Each swarm has 10 HP and defence bonuses of -100.
  Path levels 2+ may send **groups of three**. **Medic!** keeps spawning swarms from the edges outside the shield phases
  (cache: "continuous shield scarab spawns"; Main: they move **50% slower**) and they delay the fight if not cleared; rate **OPEN-KP11**.
- **KP7 Spitting Scarab.** Ranged, speed 3, max hit 10, projectile 2152 / 2154 (impact 2153 / 2155). Its hits can poison starting at
  6 and "can hit through protection prayers" (Spitting). Strat says it hits **all players within 8 tiles** and, later in the
  same section, **within a ten tile radius** (conflict 4). It can be trapped by two dung rows if the player runs west.
- **KP8 Soldier Scarab.** Melee, one target, max hit 17, speed 6 or 7 (conflict 3). While Kephri is down it sends out an
  unattackable swarm (graphic 2150) that recharges her shield **about every 5 seconds (about 8 ticks) for roughly 5-7 charges**;
  if she is on real health it heals her instead; the amount scales with the scarab's **current HP** (Strat, Soldier). It can be trapped
  by dung east of Kephri (top and bottom tiles aligned with her).
- **KP9 Arcane Scarab.** Does not attack players directly; it **charges a red swarm** around itself (3x3 on itself, graphic 2149).
  A fully charged swarm hits **all players** for well over **65 damage** without Protect from Magic and Protect from Magic
  cuts it by about **66%** ("even through prayer"). It **relocates** (dispersing the charge) after **2 + N successful hits** (N =
  players in the raid; misses count, thralls and burn do not), or on a single hit of **39 or more** (Mod Ash, 28 Oct 2024) (conflict 5).
  Relocation order SE -> NW -> mid-south -> NE (Strat). It always first appears south-east of Kephri. Charge time, the charge-release
  delay and the "within 4 ticks" qualifier are **OPEN-KP12**.
- **KP10 Final phase.** Kephri becomes 11721 with HP 80 and **no longer regenerates HP** (1 Sep 2022); her defence is reset
  to 80 (Page). Soldier scarabs are described as healing her "on actual health" (Strat), which conflicts with that note
  (conflict 9). Dung Strike and Mass Incubation are used only while shielded (Page); the Page and Strat do not say which attacks run in the final phase besides the fireball, which is assumed to continue (**OPEN-KP16**).
- **KP11 Defence drains.** Her base defence "can be lowered by a maximum of 20 damage", to a minimum of **60**, and is reset to
  80 for the final phase (Page, Strat); a drain bug was fixed on 8 Sep 2022. Specials: the bone dagger special **guarantees a hit** on Kephri when used first
  (6 Aug 2025). The Strat advises dumping damaging specials instead of draining unless a soulflame horn is used.
- **KP12 Death and rewards.** Drops: Scabaras' capture (once, if not banked) and **Scarab dung** (obj 27214, only to the player
  with the most damage). Spitting, Soldier and Arcane Scarabs give **0.5x** room points; Kephri 1x; per-room cap 20,000 room points
  (Drops).

## Attack reference

| Attack | Animation | Projectile / graphic | Delay to impact | Damage | Prayer | Telegraph |
|---|---|---|---|---|---|---|
| Fireball | one of 9575 / 9576 / 9577 (OPEN-KP3) | projectile 2266, impact 2156-2159 | 2, 3 or 4 ticks after the shadow appears (MT) | max hit 24 is the only number (OPEN-KP2) | Magic, about -66% | ground shadow 2111 / 1446 / 1447 |
| Aerial Assault fireball | as above | as above, 3x3 | as above | "significantly more" (cache) | Magic | as above |
| Bomber scarab | 9591-9600 flying set | projectile 2147, explosion 2148 | same as Kephri's, independent of spawn point (Strat) | as the fireball (incl. 3x3 under Aerial Assault) | Magic | scarab in flight |
| Dung Strike | not stated | 2146 warning, 2164 / 2165 balls, 2145 drop, 2166 | stun 2 s, delay OPEN-KP8 | light | none | flies around the player, message |
| Mass Incubation | 9578 | eggs 11728 / 11729 | fuse OPEN-KP9 | explode 3x3, hatch scarabs | none | eggs on the floor |
| Spitting Scarab | 9588 | 2152 / 2154 (+ 2153 / 2155) | not stated | max hit 10, poison from 6 | passes through prayer | scarab 11725 |
| Soldier Scarab | 9587 | heal swarm 2150 | not stated | max hit 17 | Melee | scarab 11724 |
| Arcane Scarab | 9594 (its stand anim; attack anim not stated) | charge 2149, integrate 2151 (role not stated) | charge time OPEN-KP12 | over 65 unprotected | Magic, about -66% | red swarm builds around it |
| Agile Scarab | generic scarab ranged 9588 (not confirmed) | not stated | not stated | max hit 5 | Missiles | egg hatching |

## What the RuneLite plugins time or detect

| Plugin value | Meaning | Confidence |
|---|---|---|
| MT: shadow graphics 1447 / 1446 / 2111 -> 4 / 3 / 2 ticks | fireball flight time from shadow spawn | high |
| MT: impact graphics 2156-2159; the bomb creates one on each tile of its area (incl. 3x3); the exploding scarab and egg create one only on the 1x1 tile, yet the scarab still damages 3x3 under Aerial Assault | the same ids are reused by bomb, scarab and egg | high |
| MT: bomb projectile 2266, exploding scarab 2147 | projectile ids | high |
| MT: hit needs the player's **previous-tick** tile in the set; Vengeance-popped bomb damage is not a mistake except in phase 11720 | Vengeance does not count in the swarm phase | low |
| MT: bomb does no damage while Kephri's health ratio is 0 | down state | medium |
| TA: swarm leak = animation 9607, swarm death = 9608; Kephri down = 9579, up = 9581 | state machine of the intermission | high |
| TA: swarm "wave" = spawn tick group (new wave each tick with a spawn); first down: waves **1-18** heal, wave **19** heals only if the swarm's NPC index is below Kephri's; second down: waves **1-14**, wave **15** the same special case | which swarms can reach her | medium |
| ST: hitsplat type 11 on "Kephri" = shield healing amount | healing is a hitsplat of that type | high |
| MT / ST / PT: NPC ids 11719-11721, dead 11722, scarabs 11724-11726; region 14164 / 14162 | identification | high |

## Invocations (Kephri)

Cache category 11 (struct number in brackets); modifier from cache param 1162, equal to Main for all five.

| Name | Raid level | Cache text | Wiki text |
|---|---|---|---|
| **Lively Larvae** | +5 | "Kephri will double the number of eggs containing an Agile Scarab during her egg attack." (437) | dark eggs 2 -> **4** (Main); Strat: "2 to 3-5" |
| **More Overlords** | +15 | "During healing phases, Kephri will summon an additional scarab." (438) | a Soldier at the first threshold, a Spitting Scarab at the second; not toggled in solo |
| **Blowing Mud** | +10 | "Kephri's dung special will be twice as potent." (439) | Dung Strike hits **two** players; no effect in solo |
| **Medic!** | +15 | "Kephri will have continuous shield scarab spawns." (440) | swarms spawn from the arena edges outside shield phases, **50% slower** (Main) |
| **Aerial Assault** | +10 | "Kephri's main attack will do significantly more damage and hit a larger area." (441) | auto-attack range from a single tile to **3x3**, more damage; also bomber scarabs (and Kephri's Phantom in the Wardens) |

## Open items

- **OPEN-KP1** scaling order and rounding for raid, path and party factors on Kephri and her children.
- **OPEN-KP2** damage numbers: fireball by raid level (24 versus "immense"), Aerial Assault bonus, dung "light" and "rapid" damage,
  egg explosion damage, bomber damage. Assumption: infobox values at raid level 0 with a config table. Flag `toa.kephri.damage_table`.
- **OPEN-KP3** which attack animation is the fireball and which are radial.
- **OPEN-KP4** how much faster the auto-attack gets per two path levels.
- **OPEN-KP5** the trio-swarm chance at each path level and what "raid level 4" means.
- **OPEN-KP6** HP thresholds of the shield breaks, how many breaks there are (two or three), the size of one "shield charge".
- **OPEN-KP7** rule choosing shadow graphic 1447 / 1446 / 2111 (distance from Kephri, projectile length).
- **OPEN-KP8** Dung Strike: delay to impact, "too many scarabs" threshold, knock-back distance, how frequently it fires.
- **OPEN-KP9** Mass Incubation: egg counts (light), fuse and hatch times, explosion radius and damage.
- **OPEN-KP10** swarm spawn schedule of the second intermission, whether "every k ticks" counts before or after each spawn.
- **OPEN-KP11** Medic! spawn rate and swarm speed (50% slower than what).
- **OPEN-KP12** Arcane Scarab charge time, release delay, damage, the "4 tick" qualifier on the 39 hit; Spitting radius 8 versus 10.
- **OPEN-KP13** whether Vengeance bounces bomb damage and in which phase.
- **OPEN-KP14** whether the soldier's swarm heals Kephri in the final phase (conflict 9).
- **OPEN-KP15** spawn timing of the children after the shield breaks (positions are fixed, timing is not).
- **OPEN-KP16** which attacks Kephri uses in the final phase (the fireball is assumed; Dung Strike once at the start).

**16 open items.** Working assumptions sit behind named config flags (`toa.scaling.model`, `toa.kephri.*`).

## Conflicts between sources

1. **Shield breaks.** Page: "After two phases of shields"; Strat: children at the first and second threshold, "after the third shield is
   cleared" the final phase; TA: only two intermissions. Strat is the most specific and TA fits it; used as three shield depletions,
   two intermissions with swarms (OPEN-KP6).
2. **Lively Larvae.** Cache "double", Main "2 to 4" agree; Strat "2 to 3-5" loses.
3. **Soldier attack speed.** Cache param 14 = 6, infobox 7. Cache wins (6).
4. **Spitting radius.** Strat has both 8 and ten tiles; unresolved (OPEN-KP12).
5. **Arcane hit threshold.** Arcane page 39 or more in one hit (Mod Ash tweet 28 Oct 2024) versus Strat "over 40" and "over 39+
   within 4 ticks". The Arcane page wins.
6. **Fire weakness.** Infobox 35% / 40%, Strat 35%, Main change 40%. Infobox per form is used.
7. **Fireball size.** Infobox max hit 24 versus "immense damage" in Strat; kept as OPEN-KP2.
8. **Medic!.** Cache "continuous", Main "50% slower"; both kept.
9. **Final phase healing.** Strat: soldier scarab heals her if she is on real health; Main (1 Sep 2022): "Kephri no longer
   regenerates HP during her final phase". Treated as OPEN-KP14.
10. **Blowing Mud.** Cache "twice as potent" versus Strat "no effect in solo encounters". Strat used.
11. **Bomber scarab.** Strat names an NPC-like "Bomber Scarab"; the cache has none (projectile 2147). The projectile reading is used.
