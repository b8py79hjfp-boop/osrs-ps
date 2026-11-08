# Chambers of Xeric puzzle rooms (Crabs, Ice Demon, Tightrope, Thieving)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Chambers of Xeric" (Main), "Chambers of Xeric/Strategies"
(Strat), "Chambers of Xeric/Challenge Mode" and "Chambers of Xeric/Challenge Mode/Strategies" (CM, CM-Strat), "Jewelled Crab"
(Crab), "Ice demon" (Demon), "Brazier (Chambers of Xeric)" (Brazier), "Sapling (Chambers of Xeric)" (Sapling), "Kindling
(Chambers of Xeric)" (Kindling), "Tightrope (Chambers of Xeric)" (Rope), "Deathly ranger" / "Deathly mage" (Ranger / Mage),
"Keystone crystal" (Key), "Corrupted scavenger" (Scav), "Chest (Chambers of Xeric)" (Chest), "Trough (Chambers of Xeric)"
(Trough), "Cavern grubs" (Grubs), "Cave snake" is in [resources.md](resources.md); cache 2757 (`cache-dump config npc <id>`;
stats array is 1 for every Chambers NPC because the raid scales them at runtime, so the numbers below come from the wiki
infobox and the `params` map; params 5 / 6 / 7 / 8 = stab / slash / crush / magic defence bonus, agrees with the wiki on
every NPC checked); generated symbols (`Npcs`, `Locs`, `Objs`, `SpotAnims`). Raid-wide rules (party size, scaling, layouts, point
totals and reward roll, storage units, death): the CoX raid-wide doc `../cox.md` (not part of this set). Potions, seeds,
herbs, food and the sapling / kindling item rules: [resources.md](resources.md). The Great Olm: [olm.md](olm.md). Seconds
are converted at 0.6 s per tick. Tick timelines below list only what a source states; everything else is `OPEN-PZ<n>`.

Scope: the four puzzle rooms. Two of them contain fighting (Ice demon, Tightrope); all four are skipped by no rule, they
are required to open the path (Main: "Puzzle rooms are considered Crabs, tightrope, Ice demon and the Corrupted scavenger").

## Data

- NPC ids (cache names confirm the wiki):
  - Jewelled Crab **7576** (grey / dim), **7577** (red), **7578** (green), **7579** (blue); op2 "Attack", op3 "Smash"; size 1; no
    combat level; category 1030. Orb NPCs `RAIDS_LASERCRABS_ENERGY_WHITE / _RED / _GREEN / _BLUE` **7580 / 7581 / 7582 / 7583**.
  - Ice demon **7584** (`RAIDS_ICEDEMON_NONCOMBAT`, op1 "Attack", before the storm is gone) and **7585**
    (`RAIDS_ICEDEMON_COMBAT`; params 5-8 = 70 / 70 / 110 / 40, param 14 = **3**); size 2. Icefiend **7586** (`RAIDS_ICEFIEND`,
    param 14 = 3, not attackable: the cache has no ops). **Conflict 1:** the wiki infobox assigns 7584 to Normal and 7585 to
    Challenge Mode, the cache names say non-combat / combat form, so the same pair serves both modes.
  - Deathly ranger **7559** and Deathly mage **7560** (both modes; op2 "Attack", size 1; ranger params 4 = 120 and 12 = 70,
    mage param 3 = 120).
  - Corrupted scavenger **7602** (`RAIDS_THIEVINGCHEST_BEAST_ACTIVE`) and **7603** (`_SLEEPING`); size 2, no ops. The wiki lists
    both ids under one infobox.
- Scenery / items (symbols): thieving chest `RAIDS_THIEVINGCHEST_CLOSED` 29742, `_OPEN` 29743 (poison), `_EGGS_WHOLE` 29744
  ("after completion"), `_EGGS_HATCHED` 29745 (grubs), trough `RAIDS_THIEVINGCHEST_FOODTROUGH_EMPTY` 29746 / `_FULL` 29874, creature keeper
  corpse `_CREATUREKEEPER` 29875; braziers `RAIDS_ICEDEMON_BRAZIER_UNLIT` 29747 / `_LIT` 29748, storm `RAIDS_ICEDEMON_SNOW` 29876,
  spawns `RAIDS_ICEDEMON_TINDERBOX` 31634 and `RAIDS_ICEDEMON_AXE` 31862; tightrope `RAIDS_TIGHTROPE_END` 29750, barrier
  `RAIDS_TIGHTROPE_BARRIER` 29749; crab room hammer `RAIDS_LASERCRABS_HAMMER` 29711, big crystals `RAIDS_LASERCRABS_BIGCRYSTAL_1..5`
  29753-29757, small crystals `RAIDS_LASERCRABS_SMALLCRYSTAL_` BLACK 29758, CYAN 29759, MAGENTA 29760, YELLOW 29761, WHITE 29762; items
  `RAIDS_THIEVINGCHEST_GRUBS` 20885 (cavern grubs), `RAIDS_TIGHTROPE_KEYSTONE` 20884, `RAIDS_WOOD` 20799 (kindling). Graphics
  `RAIDS_ICEDEMON_ICEBALL_TRAVEL` 1324, `_HIT` 1325, `RAIDS_LASERCRABS_SHATTERCRYSTAL` 1326.
- Enemy stats (infobox, "scaled for a player with maxed combat stats"; the raid scales per team, details in the raid-wide
  doc; Normal / Challenge Mode, CM = x1.5 per the CM page):
  - Ice demon: size 2, HP **140 / 210**, attack 1, strength 1, defence **160 / 240**, magic **390 / 585**, ranged **390 / 585**;
    defence bonus stab 70, slash 70, crush 110, magic 40, light / standard / heavy 140 (cache confirms the first four);
    attack speed **3**; ranged and magic; max hit "Varies"; not aggressive; poison and venom immune; elemental weakness fire
    150%; XP bonus 2.5 / 7.5; attributes demon, xerician.
  - Deathly ranger: size 1, HP **120 / 180**, defence **155 / 232**, magic **155 / 232**, ranged **210 / 315**, ranged attack
    bonus 120, ranged strength 70, attack speed **4**, max hit **70 / 99**, not aggressive, poison immune. Deathly mage: HP
    120 / 180, defence 155 / 232, magic 210 / 315, magic attack bonus 120, attack speed 4, max hit **22** (CM blank in the
    infobox, **OPEN-PZ12**). All defence bonuses 0.
  - Jewelled Crab and corrupted scavenger: no infobox stats (**OPEN-PZ1**).
- Regeneration of stats (Mod Ash, 9 Jul 2026, quoted on the Crab and Demon pages): the crab restores 1 point per **1** tick, the ice
  demon at the default **100** ticks.

## Crabs (jewelled crab puzzle)

- **CR1 Room goal:** four coloured crystals in the chamber must be turned white. A carved sculpture continually emits a white
  orb, one at a time; a new orb appears as soon as the previous disappears. The orb has to be bounced off the crabs and hit the
  crystals. The crystals `SMALLCRYSTAL_BLACK / CYAN / MAGENTA / YELLOW / WHITE` exist as locs; white is the solved state (the
  wiki never says how the black one is used or what the starting state is, **OPEN-PZ2**).
- **CR2 Crab colour:** the crystals on a crab are changed by attacking them: **melee (or Smash with a hammer) = red, magic =
  blue, ranged = green**. A crab keeps its colour for about **5 s (8 ticks, rounding OPEN-PZ3)** (Strat). Smashing a crab with
  a hammer or a blunt weapon (hammer found in the room, elder maul, dragon warhammer, abyssal bludgeon, Torag's hammers,
  granite maul and others; blunt = crush bonus) stuns it in place and makes it temporarily red and keeps its aggression.
- **CR3 Orb colour rule:** the white orb that reaches a crab takes the crab's colour and continues; **a coloured orb cannot
  touch a crab of a different colour and disappears** (Strat; CM-Strat: "a coloured orb cannot change colour again - if a green
  orb hits a red crab, it will disappear"). The crystal table (Strat, Crab):

  | Crystal | Orb that must hit it | Style |
  |---|---|---|
  | Black | White | none |
  | Yellow | Blue | magic |
  | Cyan (blue) | Red | melee |
  | Magenta (purple) | Green | ranged |

- **CR4 Reflection:** CM-Strat: "The orb will reflect to the right, from the orb's perspective, when it encounters a crab." The
  Strat and Crab pages do not state the direction (**OPEN-PZ4**, CM-Strat used for both modes).
- **CR5 Orb damage:** a player who intercepts the orb takes damage "scaled to their current health" (Strat); the CM-Strat range is
  **10-30** (Normal formula **OPEN-PZ5**).
- **CR6 Aggression:** crabs are aggressive with a low aggression range and "fairly accurate" melee; Protect from Melee is the
  advice; stay within 3 tiles so a crab does not lose interest (Strat). Stats and max hit are not stated (OPEN-PZ1). The room needs
  **three** crabs in every layout; more players in the raid means more crabs (excess ones wander into the orb path and are
  distracted by other players); the count per party size is **OPEN-PZ6**.
- **CR7 Stun duration (Mod Ash, 4 Mar 2020, ticks, a random value in the range):**

  | Party size | Ticks |
  |---|---|
  | 1 | 50-60 |
  | 2-3 | 30-40 |
  | 4-5 | 20-30 |
  | 6+ | 10-20 |

- **CR8 Attacking a crab:** no attack delay after a hit on a crab (29 Nov 2023); the player stops attacking after the first hit
  unless clicked again, and if the attack changes the colour there is no automatic re-attack. A player with magic attack bonus
  **-63 or higher** (any bonus higher than -64) does not splash on a crab; Tumeken's shadow splashes at -64 or lower (12 Dec 2023).
  Before 29 Nov 2023 splashes were possible at any bonus; that rule is superseded.
- **CR9 Thralls (16 Sep 2026):** thrall attacks change a crab's colour like a player's: zombified thrall = red, skeletal thrall =
  green, ghostly thrall = blue. Newer than the Main and Strat texts, which do not mention it.
- **CR10 Points:** "attacking or being targeted by one of the crabs (including ones not used to bounce the floating crystal) will
  reward some points upon each successful crystal colour change" (Main). The amount per change and the cap are **OPEN-PZ7**.
  31 May 2018: Challenge Mode crabs gave the wrong point amount, fixed.
- **CR11 Completion:** not stated what finishes the room or opens the path (**OPEN-PZ2**). No reward items are listed for this
  room (contrast Ice demon and Thieving, which give potion secondaries).

**Normal vs Challenge Mode:** the CM page lists the crab room among the rooms that "isn't made harder" (CM-Strat: "This is one of
the few rooms in Challenge Mode raids that isn't made harder"). The Crab page and the wiki do not state any CM stat for the crabs.
In CM the room is at a fixed place on the top level.

## Ice Demon

- **ID1 Setup:** the demon stands in an ice storm that blocks the way. Four braziers surround it; **one icefiend per player
  (maximum four)** stands at a brazier. A permanent item spawn of a **tinderbox** and a **bronze axe** is in the room (tinderbox
  added 26 Oct 2017, axe 8 Feb 2018). Saplings in the corridor before the room give kindling (rules in
  [resources.md](resources.md), KN1-KN4). The demon cannot be attacked while the storm lasts.
- **ID2 Lighting (Firemaking):** lighting an unlit brazier with kindling uses the standard Firemaking skill roll: **8% at level 1,
  rising linearly to 78% at level 99** (Mod Ash, 16 Jul 2026); a failed attempt leaves it unlit. Firemaking levels are
  boostable ("visible level", assumption from the Woodcutting precedent, **OPEN-PZ8**). Adding wood to a lit brazier is as
  effective as to an unlit one (31 Jan 2019).
- **ID3 Burning:** each brazier burns on a timer that depends on the kindling in it, "weighted by the players in the raid"; the
  speed at which the demon defrosts depends on how much kindling is burning. For large parties braziers burn out faster
  (23 Mar 2017). Timer length per kindling and the party weighting are **OPEN-PZ9**. Solo needs **54 kindling** to force the
  demon out (Strat; the page does not say whether that already covers icefiend removals, **OPEN-PZ9**).
- **ID4 Icefiends:** an icefiend only acts on the brazier in front of it. If that brazier is lit it tries to **remove one charge
  every 3 to 4 ticks (random), with a 1/6 chance of success each time** (Mod Ash, 13 Jul 2026). More kindling is needed
  in larger teams; the room is easier if the fires are lit on the same or near ticks (Strat).
- **ID5 Thaw:** once the ice storm is dispelled the icefiends vanish and the demon walks out (switch 7584 -> 7585) and engages the
  team. The threshold for "dispelled" is not stated (**OPEN-PZ9**).
- **ID6 Attacks:** attack speed **3** (cache param 14 = 3, infobox 3). Two attacks, both a **3x3 AoE** on the target tile: the
  **snow boulder / snowball** (ranged; the Page says "snow boulder", Strat says "snowball", conflict 2) and **Ice Burst**
  (magic; Strat: the projectile is much faster, hard to see, and "has a chance to freeze the player"). The demon reads the
  target's overhead prayer: **Protect from Missiles = only the boulder, Protect from Magic = only Ice Burst**; with neither it picks one
  (rule **OPEN-PZ10**). Damage values, the freeze chance and duration, and the projectile timings are **OPEN-PZ10**. A player can
  dodge by moving a few tiles; safespot corners left / right of the entrance exist if other players stay targetable (CM-Strat).
- **ID7 Damage reduction:** **all damage the demon takes is reduced by 67%**, except **fire spells** and **demonbane** weapons.
  Fire spells: elemental weakness **150%** (infobox; Page: "(which deal 250% damage)"; Strat: "150% more damage"; same figure
  read as bonus or multiplier). Demonbane: "all demonbane modifiers are increased by 15%" (Page, 22 Jul 2026 change text:
  "Increased the demonbane vulnerability from 100% to 115%"; Strat: "Demonbane deals 15% more damage"). Whether the 67% reduction
  applies before or after the fire and demonbane multipliers is **OPEN-PZ11**. Its magic defence is calculated from the Defence
  level (Mod Ash, 28 Feb 2019) and the magic defence bonus was lowered from 60 to 40 (29 Nov 2023).
- **ID8 Death and drops:** every player gets (100%): **fiendish ashes x1, stinkhorn mushroom x7, endarkened juice x7, cicely x5**
  (Demon drop table; Strat solo-scale table agrees; party scaling **OPEN-PZ13**). Added 29 Nov 2023 ("drops ... to each player").
  Storage units appear in this room (raid-wide doc).
- **ID9 Points:** per kindling and per brazier light: **36 points per kindling burned and 32 points for lighting a brazier**
  (Brazier, Kindling; both raised on 29 Nov 2023). Cap (Main): in Normal about **6.5 sets of 28 kindling for about 6,050
  points**; in a solo CM the maximum is **6,480** with the sequence "light one brazier with 28 kindling, add 28, add another 28,
  add exactly 21, then light a second brazier with 28 kindling". These totals do not reproduce from 36 and 32 alone
  (133 kindling x 36 + 2 x 32 = 4,852), so the real points are scaled by an unstated factor and cut off by an unstated formula
  (**OPEN-PZ14**). Damage dealt to the demon also earns combat points (raid-wide doc).

**Normal vs Challenge Mode:** CM stats x1.5 (HP 210, defence 240, magic and ranged 585); the CM layout puts it on the top level,
and the CM-Strat advice uses fire surge with twisted bows; the XP bonus is 7.5 instead of 2.5. The rest is unchanged
(CM-Strat: "killed much like in normal raids").

## Tightrope

- **TR1 Layout:** a platform with **2-4 deathly rangers and 2-4 deathly mages** (Strat: "Groups of 2-4"; the Ranger / Mage pages
  say two of each in solo and small groups) stands near a tightrope; a **keystone crystal** on the far side is needed to dispel the
  **shimmering barrier** (`RAIDS_TIGHTROPE_BARRIER`). The count is set by the party size at the start (Strat); the mapping of party
  size to enemy count is **OPEN-PZ15**.
- **TR2 Passive until crossing:** the enemies are not aggressive. If a player tries to cross the rope while they are not fighting
  someone else, they immediately focus the crosser (Strat, CM-Strat: "unless they are fighting another player"). Out-of-render
  safespot: with a weapon range 8 or more stand away until the enemies are no longer rendered, then they lose aggression
  (solo method, Strat); two safespot tiles exist per layout (one for the rangers, one for the mages).
- **TR3 Crossing:** requires Agility; the level requirement is **between 80% and 100% of the team's average Agility level, randomly
  scaled** (Mod Ash, 29 Dec 2020). Per player or per raid, and the roll's distribution, are **OPEN-PZ16**. Damage accumulated while
  on the rope is dealt "in one tick" after crossing (Strat / CM-Strat), which usually kills the crosser if the enemies are
  alive; experienced players time the crossing to avoid the hits (no timing numbers are stated, **OPEN-PZ17**).
- **TR4 Enemy attacks:** rangers **ranged**, attack speed 4, max hit **70** (CM **99**); mages **magic**, attack speed 4, max hit
  **22**. Both "can hit through prayer, but damage and accuracy are reduced": protection prayers cut damage by **50%** (CM-Strat:
  "simply reduces the deathly opponents' damage by 50%"). Whether accuracy is reduced and by how much: **OPEN-PZ18**. Kill the
  mages first (lower max hit); a "tank" takes a hit every 3 to 4 blowpipe hits so that the ranger keeps the same target (Strat).
- **TR5 Keystone:** the crossing player collects the keystone from the far side and returns; the item can be picked up by an
  Ironman from a teammate (21 Aug 2024; points still go to the original collector). **Using the keystone on the barrier dispels it and kills
  all surviving deathly rangers and mages** (Key, Ranger, Mage, Strat).
- **TR6 Drops:** each killed enemy drops bones (100%).
- **TR7 Points:** "some points" for using the keystone on the barrier (Main); the amount was adjusted on 28 Sep 2017 and is
  **OPEN-PZ19**. Damage on the enemies earns combat points while they live (raid-wide doc).

**Normal vs Challenge Mode:** HP / defence / magic / ranged x1.5; ranger max hit 99 (CM) vs 70; mage CM max hit not stated
(OPEN-PZ12). In CM the room is on the middle level; the CM-Strat has the same instructions as Normal, using ranged or magic only
("must be attacked with ranged (toxic blowpipe) or magic").

## Thieving (corrupted scavenger and the cavern grub chests)

- **TH1 Goal:** the corrupted scavenger blocks the path and has to be fed **cavern grubs** until it is full and falls asleep on the
  hay (switch 7602 -> 7603, `RAIDS_THIEVINGCHESTS_EAT / _TIRED / _SLEEPING` animations 7496 / 7497 / 7498). Grubs come from opening
  the many chests; a player puts them into the **trough** with "Deposit" (items: cavern grubs 20885, stackable, capped at **28** per
  inventory since 29 Nov 2023).
- **TH2 Grubs needed:** scaled linearly off the party's **total Thieving level**, up to a cap (Mod Ash, 12 Jun 2024); the
  **minimum** is **16 x players, or 30 solo** (Trough, Main). Everything between the minimum and the cap is unstated
  (**OPEN-PZ20**). The scavenger's hunger shows as a bar above its head.
- **TH3 Feeding:** once a player puts grubs in the trough the scavenger starts eating until full. If it has not been fed for at
  least **60 s (100 ticks)** after it stopped eating, its hunger starts regenerating (increased from 30 s on 31 Jan 2019; it "will
  now eat faster"; eating speed is **OPEN-PZ21**). Depositing at 10-15 grubs is advised.
- **TH4 Opening a chest:** no Thieving level requirement (removed 31 Jan 2019), no tools needed. One attempt per **tick** (Mod Ash, 3 Mar
  2019) until success. Chance without a lockpick: about **39% at level 1 to about 61% at level 99** (Mod Ash, 8 Jan 2024; the
  curve between is not stated, linear is the wiki chart's shape, **OPEN-PZ22**); a **lockpick** or **hairclip** in the inventory
  adds **21 percentage points** (Mod Ash, 6 Jan 2024). The wiki skilling chart plots 99 to 155 without a lockpick and 153 to 209 with one (level 1 to 99;
  the unit is not stated). A chest locks itself shortly after it is opened (time
  **OPEN-PZ23**).
- **TH5 Grub quantity (Mod Ash, 23 Dec 2020 and 6 Feb 2021):** pick a random integer **0 to floor(Thieving / 25)** inclusive, then raise
  it to the minimum **1** (or **2** at Thieving 95+).

  | Thieving level | 1 grub | 2 | 3 | 4 |
  |---|---|---|---|---|
  | 1-49 | always | | | |
  | 50-74 | 2/3 | 1/3 | | |
  | 75-94 | 2/4 | 1/4 | 1/4 | |
  | 95-99 | | 3/4 | 1/4 | |
  | 100+ | | 3/5 | 1/5 | 1/5 |

  (Boosted levels count, as for the Woodcutting precedent: **OPEN-PZ24**.) Experience is granted for each grub (CM-Strat: "experience
  only gained when grubs are collected"; amount **OPEN-PZ25**).
- **TH6 Special chests:** exactly **one** chest holds a one-time reward of **psykk bats (6)**: **one per player in the raid when
  opened, minimum 5, maximum 25** (Mod Ash, 7 Feb 2022; the bats count as food cooked by the finder). **Three** chests are poison
  chests: they release poison that deals **1-3** damage to anyone next to the chest, with no poison status and no immunity effect;
  they lock after opening (Chest, Strat). Chest positions per layout: the map pins show well over a hundred chest locations; which
  three and which one are special is per layout (**OPEN-PZ26**). Some layouts have circular formations of four chests.
- **TH7 Rewards on completion:** each player gets **endarkened juice, stinkhorn mushrooms and cicely** (Strat solo-scale table:
  **5 / 5 / 4**; added 29 Nov 2023; party scaling **OPEN-PZ13**). A player can search the creature keeper's corpse for the
  "Creature keeper's journal".
- **TH8 Points:** **115 points per grub deposited** (31 Jan 2019 "five times more", "slightly increased" 29 Nov 2023). The points have a cap:
  `maxPoints = max(4500, floor(totalThievingLevel / 6) x 150)` and `maxGrubs = ceil(maxPoints / 115)` (Main); solo with 99 Thieving gives
  4,500 and 40 grubs. The cap uses the grubs counted for points, which can be higher than the grubs needed to fill the scavenger.

**Normal vs Challenge Mode:** the CM page lists this room on the middle level; the CM-Strat says "The chests have a chance of
containing the cavern grubs required, though it may instead contain unhatched cocoons" and "A few chests may contain poison",
and "30 grubs will be needed" solo, while Strat and Chest say a successful opening **always** gives grubs (conflict 3; the
`EGGS_WHOLE` / `EGGS_HATCHED` locs exist). The unhatched-cocoon chance and the CM stat of the scavenger are **OPEN-PZ27**. The
psykk bat chest gives "five" bats in CM-Strat versus min 5 / max 25 in Chest (consistent for a solo raid).

## Changes up to 2026-11-03 that matter

16 Sep 2026 thralls colour the crabs; 22 Jul 2026 ice demon demonbane 100% -> 115%; 16 and 13 Jul 2026 Mod Ash on the brazier
lighting roll and the icefiend 1/6 (quoted, not a change); 9 Jul 2026 regeneration rates quoted; 21 Aug 2024 Ironman may pick up the
keystone; 29 May 2024 ice demon 150% fire weakness; 25 Jun 2025 demonbane vulnerability added; 12 Dec 2023 Tumeken's shadow splashes at
-64 or lower; 29 Nov 2023 room improvements (kindling stackable to 28, more points for braziers and kindling, ice demon magic
defence 60 -> 40 and drops for each player, no splashing on crabs above -64, no delay after hitting a crab, grubs stackable to 28,
guaranteed minimum 1 grub, higher chest success, more points per grub, completion items, faster herb growth); 8 Jan 2024 chest
success 39% / 61%; 23 Dec 2020 / 6 Feb 2021 grub quantity statements (Mod Ash); 29 Dec 2020 tightrope
Agility rule; 28 Feb 2019 ice demon defence lowered and magic defence from Defence, more kindling per chop; 31 Jan 2019 Thieving restriction
removed, five times grub points, scavenger eats faster and hunger delay 30 -> 60 s, wood in a lit brazier as effective as an unlit one;
23 Mar 2017 braziers burn out faster for large parties; 6 Apr 2017 thieving points doubled and the scavenger needs less food.

## Open list

OPEN-PZ1 crab and corrupted scavenger stats (HP, max hit, attack speed); OPEN-PZ2 crystal start state and the role of the black
crystal, how the crab room is completed; OPEN-PZ3 colour duration rounding (5 s = 8.33 ticks); OPEN-PZ4 orb reflection direction
in Normal; OPEN-PZ5 orb damage formula in Normal (CM range 10-30); OPEN-PZ6 crabs per party size; OPEN-PZ7 crab points per
colour change and cap; OPEN-PZ8 whether boosted levels count for the brazier roll; OPEN-PZ9 brazier timer per kindling, party
weighting and the thaw threshold; OPEN-PZ10 ice demon damage, freeze chance, attack choice with no protection prayer, projectile
timings; OPEN-PZ11 order of the 67% reduction and the fire / demonbane multipliers, and what "115%" means; OPEN-PZ12 mage max
hit in CM and CM stat scaling beyond x1.5; OPEN-PZ13 drop quantities by party size; OPEN-PZ14 kindling point scale factor and cutoff;
OPEN-PZ15 tightrope enemy count by party size; OPEN-PZ16 Agility requirement roll scope and distribution; OPEN-PZ17 crossing
and damage-stall timing; OPEN-PZ18 tightrope accuracy reduction; OPEN-PZ19 keystone points; OPEN-PZ20 grubs needed between the
minimum and the cap; OPEN-PZ21 scavenger eating speed; OPEN-PZ22 chest chance curve; OPEN-PZ23 chest re-lock delay; OPEN-PZ24
boosted Thieving for grub quantity; OPEN-PZ25 Thieving XP per grub; OPEN-PZ26 chest layouts (which three are poison, which
holds the bats); OPEN-PZ27 CM cocoon chance and CM scavenger scaling. **27 open items.**

## Conflicts between sources

1. Ice demon NPC ids: wiki infobox maps 7584 to Normal and 7585 to Challenge Mode; the cache names them non-combat / combat (the
   same two forms in both modes). The cache is used.
2. Ice demon attack name: "snow boulder" (Demon page) vs "snowball" (Strat). The same attack.
3. Thieving chests: "Chests will always guarantee grubs" (Strat, Chest) vs "may instead contain unhatched cocoons" (CM-Strat). Normal: always.
4. Ice demon fire damage: "150% more damage" (Strat) vs "deal 250% damage" (Demon page) vs "150% weakness" (infobox): the same figure
   as a bonus versus a multiplier.
5. Demonbane: "15% more damage" (Strat) vs "all demonbane modifiers are increased by 15%" / "from 100% to 115%" (Demon page).
6. Kindling average for max 5: 2.2667 (Kindling page) vs 2.6667 (Demon page); the formula (0 counts as 1) gives 2.6667 (see resources.md).
7. Kindling points: 36 per kindling and 32 per light vs the stated caps of 6,050 / 6,480: not reproducible.
8. Tightrope enemy counts: "2-4" rangers and mages (Strat) vs "two of each" in solo and small groups (Ranger / Mage).
9. Crystals: "four coloured crystals" (Strat) vs five small-crystal locs (black, cyan, magenta, yellow, white) in the symbols.
