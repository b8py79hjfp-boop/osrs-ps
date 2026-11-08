# The Nylocas and Nylocas Vasilias (room 3)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Nylocas" (race page), "Nylocas Ischyros", "Nylocas
Toxobolos", "Nylocas Hagios" (the three share one stat block), "Nylocas Vasilias" (Page), "Nylocas Prinkipas",
"Nylocas Matomenos" / "Nylocas Athanatos" (used in the Verzik room), "Support (Theatre of Blood)", "Theatre of
Blood/Strategies" (Strat), "Theatre of Blood/Strategies/Nylocas" (Strat/Ny: the wave chart), "Theatre of Blood/Entry
Mode" (Entry), "Theatre of Blood/Hard Mode" (Hard); cache 2757 (`cache-dump config npc <id>`, stats array order attack,
defence, strength, hitpoints, ranged, magic; gamevals `TOB_NYLOCAS_*`). Raid-wide rules: [../tob.md](../tob.md). Tick
timelines below list only what a source states; everything else is `OPEN-NY<n>`. Seconds are converted at 0.6 s per tick.

The room: 31 waves of small and large Nylocas are sent through three lanes (east, south, west) towards four **supports**
(pillars). The party must clear them before the pillars are destroyed; after the last wave the boss **Nylocas Vasilias**
drops into the arena. The health bar shown during the waves is the combined pillar health; the progress bar is driven
by varbits `TOB_CLIENT_WAVEPROGRESS_TYPE` / `_VAL` / `_MAX` (6447-6449).

## Data: the three breeds (Ischyros melee, Toxobolos ranged, Hagios magic)

All three have identical numbers (infobox); only the attack style differs: Ischyros stab, Toxobolos ranged, Hagios magic.
Attack speed **3** for all; not aggressive in the infobox sense (the "aggro" ones are picked by id, below). Fire spell
weakness 15% (25 Jun 2025); can be burned (24 Jul 2024).

| Variant | Combat | Size | Max hit | HP (Normal/Hard: 5 players; Entry: solo cache base) | att / str / def / magic / ranged |
|---|---|---|---|---|---|
| Small, Normal and Hard | 162 | 1 | 17 | 11 | 200 / 160 / 1 / 1 / 200 |
| Large, Normal and Hard | 260 | 2 | 24 | 22 | 250 / 230 / 20 / 20 / 250 |
| Small, Entry | 46 | 1 | 5 | 2 (solo; cache base) | 80 / 40 / 1 / 1 / 80 |
| Large, Entry | 82 | 2 | 10 | 3 (solo; cache base) | 100 / 90 / 6 / 6 / 100 |
| Verzik's version, Normal and Hard | 162 | 2 | (blank) | 25 | (blank): see [verzik.md](verzik.md) |
| Verzik's version, Entry | 46 | 2 | (blank) | 3 | (blank) |

Other infobox bonuses: Normal small magic attack +1073 and magic strength +928, ranged strength -15; large +500, +500, 0;
Entry small +505, +280, -30; large +400, +360, -6 (the values are as printed; **OPEN-NY1** whether they matter, as the
Nylocas never make a magic accuracy roll against players with them). All defensive bonuses 0. Poison and venom
resistance 100 in every version, Verzik's included (Ischyros, Toxobolos and Hagios pages hold one value for all six
versions; Athanatos and Vasilias also 100).

- **HP by party size, Normal / Hard (5 -> 4 -> 3 or fewer):** small 11 / 9 / 8, large 22 / 19 / 16 (Strat); those equal the
  floor of 87.5% and 75% (TB10). Entry: "solo Entry mode smalls have two Hitpoints and larges have three" (Entry), which is the
  cache base; the wiki gives no values for 2-5 players and the TB11 multipliers would give fractions (2 x 1.9 = 3.8), so
  Entry Nylocas HP above one player is **OPEN-NY2**.
- **NPC ids** (wiki infobox; the cache names them `TOB_NYLOCAS_INCOMING_*` (walking in) and `TOB_NYLOCAS_FIGHTING_*`):

| Breed / size | Normal (incoming / fighting) | Hard (incoming / fighting) | Entry (incoming / fighting) | Verzik (Normal, Hard, Entry) |
|---|---|---|---|---|
| Ischyros small | 8342 / 8348 | 10791 / 10797 | 10774 / 10780 | 8381, 10858, 10841 (all size 2) |
| Ischyros large | 8345 / 8351 | 10794 / 10800 | 10777 / 10783 | |
| Toxobolos small | 8343 / 8349 | 10792 / 10798 | 10775 / 10781 | 8382, 10859, 10842 |
| Toxobolos large | 8346 / 8352 | 10795 / 10801 | 10778 / 10784 | |
| Hagios small | 8344 / 8350 | 10793 / 10799 | 10776 / 10782 | 8383, 10860, 10843 |
| Hagios large | 8347 / 8353 | 10796 / 10802 | 10779 / 10785 | |

  The wiki lists the "fighting" ids (8348-8353, 10797-10802) as the aggressive Nylocas ("aggro's") for the RuneLite
  highlighter. **OPEN-NY3:** whether "incoming" ids are the pillar-attackers and "fighting" ids the ones that attack the
  party (assumed from the names and the wiki highlight list).
- **Supports (pillars):** four NPCs per room, cache `TOB_NYLOCAS_SUPPORT` 8358 (HP **130**, size 3); Entry NPC 10790 (HP
  **155**) and Hard NPC 10811 (HP **150**) are the cache's per-mode supports (assumed by position next to the other Entry
  and Hard room NPC ids; **OPEN-NY4**). Scenery states: support 32862, breaking 32863, broken 32864 (map pins: four tiles
  3291/3301 x 4244/4254). Whether HP scales with party size is not stated (9 Jun 2021 raised the Story-mode pillar HP "by
  5-10 depending on group size", values not stated; **OPEN-NY4**).

## Waves and rules

- **NY1 Waves:** the Strat page and the wave chart list **31 waves**; the Entry page says "30 waves" (**conflict**, the
  chart is used). The waves are fixed: the only variables are how often a wave spawns and which style the small
  Nylocas from a dying large one take (random colours, Entry page). Wave spacing in ticks is **not stated** (**OPEN-NY5**).
- **NY2 Cap:** up to **12** Nylocas may be alive before the next wave is held back in waves 1-19; from wave **20** the cap
  is **24** (Strat, Strat/Ny). Entry reduced the maximum (9 Jun 2021 hotfix, value **OPEN-NY6**) and the forced delay
  between waves.
- **NY3 Styles:** each can only be damaged by its own style: ranged on Toxobolos (green), magic on Hagios (blue), melee on
  Ischyros (grey). Attacking with the wrong style makes it immune to that player (Strat: "can no longer damage them";
  Entry: "impervious to all further attacks"). Melee needs the Nylocas to have reached the platform; they can be ranged /
  magicked earlier. **Hard:** every normal Nylocas acts like the Vasilias: a wrong-style hit is reflected on the attacker
  (capped at the Nylocas' HP) and the player is prevented from attacking it.
- **NY4 Aggro and pillars:** most Nylocas attack the pillars; a few ("aggro's", marked `!` below) stop short and attack
  the party. From wave **16** some Nylocas "flicker", swapping style in a fixed pattern; an attack made just before the
  swap still damages the attacker (Strat). Frozen Nylocas do not hit pillars (Entry page, "ice barrage"); all colours can
  be frozen.
- **NY5 Split:** a killed large Nylocas leaves **two small** ones of any style (Strat; the Entry page says random colours).
- **NY6 Explosion:** a Nylocas self-destructs **30 seconds (50 ticks) after spawning**, hitting within 2 tiles: small up to
  **18**, large up to **21** (Strat); Entry up to about 8 (Entry page, avoid by being at least two tiles away).
- **NY7 Pillars:** each pillar has HP; at 0 it collapses and drops rocks on **all players in the room**: "up to 50" (Strat) /
  "30+" (Entry page) per collapse, which can stack when pillars fall together. **If all four collapse the whole party dies
  instantly** (a wipe).
- **NY8 After the waves:** clearing wave 31 makes **Nylocas Vasilias** drop from the middle of the arena (Strat). A short
  gap lets the team heal and boost. Once the third (Hard) Prinkipas or the last wave is out "no further nylocas will
  appear unless going for the Royal Affairs achievement" (Hard page); the combat-achievement condition is an event only.
- **NY9 Death or logout:** as TB14 / TB17; a wipe in Entry: TB16. A death while a pillar falls has no extra rule.

### Wave chart (Strat/Ny, 31 waves)

Notation (derived by script from the wiki's colour markup, which has no text form): `S` small, `L` large, then the printed
style (`melee`, `range`, `mage`); `!` aggressive; `~a>b>c` flickering, the three colours of the printed word's letter groups
in order (the wiki says "change styles twice before entering the attackable arena"); `+` two spawns in the same lane;
`-` none. In Hard an extra Prinkipas joins waves 10, 20 and 30. At the two wave 21 west entries the printed label differs
from the last colour (53 flickering entries in the chart, 2 differ); the label is used and the oddity is **OPEN-NY7**
(it may be a wiki typo).

| Wave | East | South | West |
|---|---|---|---|
| 1 | S:melee | S:mage | S:range! |
| 2 | S:range | S:melee! | S:mage |
| 3 | S:mage! | S:range | S:melee |
| 4 | S:melee | L:mage | S:range |
| 5 | S:mage | S:melee | L:range |
| 6 | L:melee | S:range | S:mage |
| 7 | L:melee | S:mage + L:range! | - |
| 8 | S:range | S:melee | L:mage! |
| 9 | S:mage | - | L:range! + S:melee |
| 10 | S:range! + L:range | S:range + S:range | S:range + S:range! |
| 11 | S:mage! + S:mage | S:mage + S:mage | L:mage! |
| 12 | S:melee! + S:melee | L:melee | S:melee + S:melee! |
| 13 | L:melee! | S:range + S:melee | S:range + S:mage! |
| 14 | L:range! | S:mage + S:range | S:mage + S:melee! |
| 15 | S:mage! + S:range | L:mage | S:melee + S:range! |
| 16 | S:range~mage>melee>range | S:range~melee>mage>range | S:range~range>mage>range |
| 17 | L:mage~mage>melee>mage | L:mage~mage>melee>mage | L:mage~mage>melee>mage |
| 18 | L:range~range>mage>range | L:range~range>mage>range | L:range~range>mage>range! |
| 19 | L:mage~mage>melee>mage! | L:mage~mage>melee>mage | L:mage~mage>melee>mage |
| 20 | L:melee~melee>range>melee | L:melee~mage>range>melee! | L:melee~melee>range>melee |
| 21 | S:range~range>melee>range + S:range~range>melee>range! | S:melee~melee>mage>melee + S:melee~melee>range>melee! | S:mage~mage>melee>range + S:range~mage>range>mage |
| 22 | L:melee~mage>range>melee! | S:melee~range>mage>melee + S:melee~mage>range>melee | L:melee~melee>range>melee |
| 23 | L:melee~mage>range>melee | L:melee~range>mage>melee! | S:melee~mage>range>melee + S:range~range>mage>range |
| 24 | L:melee! | L:mage! | L:melee~range>mage>melee! |
| 25 | L:mage~mage>melee>mage | L:range | L:melee! |
| 26 | L:mage | L:melee~melee>mage>melee | L:mage! |
| 27 | L:mage~mage>melee>mage | L:range~melee>mage>range! | L:mage |
| 28 | S:mage~mage>melee>mage + S:melee~range>mage>melee | S:melee~melee>range>melee + S:range~mage>melee>range | S:melee~melee>mage>melee + S:mage~range>melee>mage! |
| 29 | S:melee~mage>range>melee + S:mage~range>melee>mage! | L:melee | S:mage~melee>range>mage! + S:range~range>melee>range |
| 30 | L:mage! | S:melee~melee>range>melee + S:melee~mage>range>melee | L:range~range>melee>range |
| 31 | S:mage~mage>range>mage + S:range~range>melee>range | S:range~melee>mage>range + S:range~mage>melee>range | S:mage~melee>range>mage + S:range~range>melee>range |

## Nylocas Vasilias (boss)

- **Ids:** Normal `NYLOCAS_BOSS_SPAWNING` 8354 (spawn), melee 8355, magic 8356, ranged 8357; Entry 10786 (spawn) and
  10787 / 10788 / 10789; Hard 10807 / 10808 / 10809 / 10810. Combat **301 / 800 / 800**; size 4; attack speed **4**;
  aggressive; XP bonus 7.5 / 20 / 20. Max hit **24 / 70 / 105** (Entry / Normal / Hard) over stab, magic, ranged. 15% fire
  weakness (25 Jun 2025). Poison and venom immune.
- **HP:** Normal and Hard base 2,500 for five (Hard same; 1-3 players 1,875, 4 players 2,187.5); Entry 1,440 for five
  (cache base 360 for one player; TB11 scale).
- **Stats:** Normal and Hard att 400, str 350, def 50, magic 50, ranged 350, magic attack +600, magic strength +600,
  strength +60, ranged strength +60; Entry att 160, str 140, def 50, magic 20, ranged 140, magic attack +380, magic strength
  +460, strength +40, ranged strength +40. Defence bonuses 0. (The Entry spawn NPC 10786 in the cache still carries the
  Normal stat line; the wiki's Entry line matches NPCs 10787-10789.)
- **NY10 Style cycle:** it spawns in its **melee** form (white carapace) and changes every **10 ticks (6 s)** to one of
  the *other two* styles (never the same twice in a row): blue = magic, green = ranged, white = melee. The player's attack
  stops at each change. Entry: it takes longer to switch (value **OPEN-NY8**). 2 Aug 2018: it can no longer attack
  instantly after a switch; 24 Oct 2019: it can no longer be stalled.
- **NY11 Wrong style:** a hit in the wrong style is **reflected to the attacker and heals the Vasilias for the same amount
  (100%)** (21 Jun 2018, previously 50%).
- **NY12 Damage:** up to 70 off-prayer and 17 when the right prayer is on, melee being fully blocked by Protect from Melee
  (Strat). Hard: the ranged attack also hits everyone near its target: the Vasilias page says "everyone standing within 2
  squares of its target. It is advised to spread out with a 3-tile gap", while the Hard page says "everyone within 3
  tiles of its primary target" and "spread at least three tiles away" (**OPEN-NY9**, conflict 4; radius 2 is the working value); the magic attack **bounces** through the rest of the
  party unless all are on one tile ("death-dot"), ignoring prayers and defence, at half damage if within 2 tiles of the
  target and more otherwise (numbers **OPEN-NY10**); max hit 105.
- **NY13 Kill:** drop "Arachnids of Vampyrium" (always unless read; obj `TOB_BOOK_NYLOCAS` 22508). Boss kill => restores
  (TB9).

## Nylocas Prinkipas (Hard only)

One spawns with waves **10, 20 and 30** and stays in the middle of the arena attacking the party instead of the pillars.
Same mechanics as the Normal Vasilias: carapace-coloured style, a wrong-style hit is reflected and heals it, a style
change every **10 ticks** to another style. Ids melee 10803 / 10804, magic 10805, ranged 10806; combat 400; size 3; attack
speed 4; max hit 38; HP **400 for five players** (300 for 1-3, 350 for 4: the Hard page lists 300 / 350 / 400 by party
size). Stats: the wiki infobox says att 200, str 175, def 25, magic 25, ranged 175, magic attack +300, magic strength
+300, strength +30, ranged strength +30, but the cache config (all four Prinkipas ids) holds att **400**, def 25, str
**230**, ranged **250**, magic 25, HP 400. The 9 Jun 2021 hotfix buffed the Prinkipas ("they were incorrectly very
inaccurate"), so the infobox is read as pre-buff and the cache values are used (**OPEN-NY11**: the bonuses are not in
the cache config). 15% fire weakness. The same hotfix shortened the Hard wave delay after a Prinkipas. The Hard page
advises killing the first two at once and ignoring the third until the room is clear.

## Per-mode differences (summary)

- **Entry:** Nylocas HP 2 / 3 for one player; lower max hits (5 / 10; Vasilias 24); a lower cap on live Nylocas and a
  shorter forced delay between waves (9 Jun 2021); slower Vasilias style switch; pillar HP higher (9 Jun 2021: +5-10 with
  party size); explosions about 8.
- **Normal:** the chart and rules above.
- **Hard:** other ids; every Nylocas reflects wrong-style damage; Prinkipas at waves 10 / 20 / 30; Vasilias ranged splash and
  bouncing magic; Vasilias max hit 105; pillar HP 150 (cache).

## Tick timeline (stated parts)

- Vasilias style change every 10 ticks; attack every 4 ticks.
- Nylocas explode 30 s (50 ticks) after spawning.
- Cap 12 -> 24 active at wave 20.

## Changes up to 2026-11-03 that matter

3 Jun 2021 modes added; 9 Jun 2021 hotfix (Entry reduced delay, cap, recoil, pillar HP; Hard Prinkipas delay and stats,
wave-20 double spawn fixed, Nylocas boss mage split damage capped at 75); 4 Jun 2021 hotfix (Hard ranged splash raised,
ball damage); 23 Jun 2021 (incorrect transition on wave 20 fixed); 18 Jun 2020 death animation; 24 Oct 2019 no stalling;
2 Aug 2018 no instant attack after switch; 21 Jun 2018 room shortened (waves removed), boss HP 2,000 -> 2,500 and max hit 58 ->
70, reflect 50% -> 100%; 24 Jul 2024 burn; 25 Jun 2025 fire weakness.

## Open list

OPEN-NY1 relevance of the magic bonus lines; OPEN-NY2 Entry Nylocas HP above one player; OPEN-NY3 incoming vs fighting ids;
OPEN-NY4 pillar HP by mode and party size; OPEN-NY5 wave spacing and lane tiles / walk speed; OPEN-NY6 Entry live cap and
delay; OPEN-NY7 wave 21 label oddities and flicker timing; OPEN-NY8 Entry Vasilias switch interval; OPEN-NY9 Hard ranged
splash radius; OPEN-NY10 Hard magic bounce damage; OPEN-NY11 Prinkipas bonuses; OPEN-NY12 pillar damage per Nylocas attack,
the attack range / cycle against pillars, and the damage to the party when a pillar falls (50 vs 30+); OPEN-NY13 which
waves' Nylocas are "aggro" in Entry and Hard (the chart is Normal's; assumed identical); OPEN-NY14 small / large HP by
party size in Entry; OPEN-NY15 Vasilias hit delays and animation ticks. **15 open items.**

## Conflicts between sources

1. 30 waves (Entry page) vs 31 (Strat, chart).
2. Pillar collapse damage: 30+ (Entry page) vs up to 50 (Strat).
3. Explosion: about 8 in Entry (Entry page) vs small 18 / large 21 for Normal (Strat); consistent if the first is Entry only.
4. Hard ranged splash radius: "within 2 squares" with a "3-tile gap" advised (Vasilias page) vs "within 3 tiles" (Hard page).
5. Solo Entry Nylocas HP "20% of original" (Vasilias page) vs cache base 2 / 3 and the 1 Mar 2023 scale.
6. Wave 21 west label vs colour oddity (NY7).
7. Prinkipas stats: wiki infobox (att 200 / str 175 / ranged 175) vs cache (400 / 230 / 250); the 9 Jun 2021 buff explains it.
