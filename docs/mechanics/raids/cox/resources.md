# Chambers of Xeric resources (scavengers, farming, food, Herblore, kindling)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Chambers of Xeric" (Main), "Chambers of Xeric/Strategies" (Strat),
"Chambers of Xeric/Challenge Mode/Strategies" (CM-Strat), "Chambers of Xeric/Food" (Food), "Scavenger beast" (Beast), "Scavenger
runt" (Runt, removed), "Golpar seed" / "Buchu seed" / "Noxifer seed" (Seed pages), "Golpar (plant)" / "Buchu (plant)" / "Noxifer
(plant)" (Plant pages), "Herb patch (Chambers of Xeric)", "Weeds (Chambers of Xeric)" (Weeds), "Gourd tree", "Geyser", "Empty gourd
vial", "Cave snake" (Snake), the potion pages "Elder potion", "Elder (-)", "Elder (+)", "Twisted potion", "Twisted (+)", "Kodai
potion", "Kodai (+)", "Xeric's aid", "Xeric's aid (-)", "Xeric's aid (+)", "Revitalisation potion", "Revitalisation (-)",
"Revitalisation (+)", "Prayer enhance", "Prayer enhance (-)", "Prayer enhance (+)", "Overload (Chambers of Xeric)", "Overload (+)",
"Overload (-)", "Antipoison (+)", "Kindling (Chambers of Xeric)" (Kindling), "Sapling (Chambers of Xeric)" (Sapling), "Ice demon"
(Demon); cache 2757 (`cache-dump config npc <id>`) and generated symbols (`Npcs`, `Objs`, `Locs`). Raid-wide rules (party size and
scaling, point totals, **storage units, which are out of scope here**, death, items dropped on death): the CoX raid-wide doc
`../cox.md`. Puzzle rooms (Ice demon room and its use of kindling, Thieving, Crabs, Tightrope): [puzzles.md](puzzles.md). Seconds are
converted at 0.6 s per tick. Tick timelines list only what a source states; everything else is `OPEN-RS<n>`.

## Data

- NPCs: scavenger beast **7548** and **7549** (`RAIDS_SCAVENGER_BEAST_A / _B`; the wiki gives 7548 Normal and 7549 Challenge Mode,
  the cache names are variant A / B, conflict 1), size 2, op2 "Attack", category 1022. Cave snake / fishing snake `RAIDS_FISHING_SNAKE` **7594**.
  Bats `RAIDS_BAT_0` ... `RAIDS_BAT_6` **7587-7593**. Icefiend 7586 (see puzzles.md).
- Items (symbols; `Objs`): kindling `RAIDS_WOOD` **20799**; empty gourd vial `RAIDS_VIAL_EMPTY` **20800**; water-filled gourd vial
  `RAIDS_VIAL_WATER` **20801**; fishing bait (cave worms) `RAIDS_FISHINGBAIT` **20853**; burnt fish 20854; raw / cooked fish tier 0-6 =
  **20855 / 20856 ... 20867 / 20868** (pairs, +2 per tier); burnt bat 20869; raw / cooked bat tier 0-6 = **20870 / 20871 ... 20882 / 20883**;
  keystone 20884; cavern grubs 20885; herbs: noxifer grimy 20901, clean 20902, seed 20903; golpar grimy 20904, clean 20905, seed 20906;
  buchu grimy 20907, clean 20908, seed 20909; stinkhorn mushroom `RAIDS_STINKHORN_MUSHROOM` **20910**; endarkened juice **20911**;
  cicely **20912**. Potions `RAIDS_VIAL_<ELDER|TWISTED|KODAI|REVITALISATION|PRAYER|XERICAID|OVERLOAD>_` with suffix `WEAK_1..4`, none (standard)
  `_1..4`, `STRONG_1..4`: elder 20913-20924, twisted 20925-20936, kodai 20937-20948, revitalisation 20949-20960, prayer enhance
  20961-20972, Xeric's aid 20973-20984, overload 20985-20996 (dose 1 first, then 2, 3, 4 within each tier). Antipoison has no
  `RAIDS_VIAL_` symbol in the generated set (**OPEN-RS1**).
- Scenery: `RAIDS_FARMING_TOOLS` 29771 (tool rack), `RAIDS_GOURD_TREE` 29772, `RAIDS_WEEDS` 29773, `RAIDS_GEYSER` 29878, sapling
  29763 (stump 29764) per the wiki; energy pool `RAIDS_ENERGY_POOL` 30066.
- Scavenger beast stats (infobox, scaled for a maxed player; Normal / Challenge Mode): size 2, HP **30 / 45**, attack **120 / 180**,
  strength **120 / 180**, defence **45 / 67**, magic 1, ranged 1, crush attack style, attack speed **4**, max hit **13 / 27**,
  aggressive, **not** poison or venom resistant (resistance 0), all defence bonuses 0. Kills drop bones (100%).

## Scavengers

- **SC1 Where:** one scavenger room is guaranteed per raid and is always before the first puzzle room; usually one per floor, sometimes
  two (Main). The number of scavengers and (to a very mild degree) their strength scale with the party size; the numbers are
  **OPEN-RS2**. Scavengers spawn with a preference for locations near the players (11 Nov 2020). A kill message says who received
  the drop (11 Nov 2020). Small scavengers (**runts**) were removed on 31 Jan 2019; only the bigger beasts remain (Runt: `{{Gone}}`).
- **SC2 Drop mechanics:** each kill rolls the table below **twice** (29 Nov 2023: "Scavengers now only roll two drops at a time"; the Beast
  page also says "drop three items at a time", counting the bones, conflict 2) plus bones. Every entry has a listed rarity per roll:

  | Entry | Quantity | Rarity per roll |
  |---|---|---|
  | Fishing rod | 1 | 1/18 |
  | Iron pickaxe | 1 | 1/18 |
  | Iron axe | 1 | 1/18 |
  | Butterfly net | 1 | 1/18 |
  | Hammer | 1 | 1/18 |
  | Tinderbox | 1 | 1/18 |
  | Lockpick | 1 | 2/18 |
  | Cave worms | 30-50 | 2/18 |
  | Secondary set (endarkened juice, stinkhorn mushroom, cicely, always dropped together) | 5-14 / 3-11 / 3-6 | 2/18 |
  | Mallignum root plank | 2 | 2/18 |

  The rest of the 18 (4/18) is not listed: a roll can give nothing, assumption **OPEN-RS3**. Planks are stackable (29 Nov 2023) and
  were added to the table on 14 Feb 2024; they feed the storage units (raid-wide doc). The lockpick was added on 28 Nov 2019, taken from the
  fishing bait chance. Before 29 Nov 2023 the secondaries dropped separately; superseded.
- **SC3 Dropped to whom:** raid drops go to the player who dealt the most damage, with the usual Ironman rule (Main, 9 Mar 2017 / 11 Nov 2020).
- **SC4 Points:** combat points for damage dealt (raid-wide doc); nothing scavenger-specific is stated (**OPEN-RS4**).
- **SC5 Scavenger-room shortcuts:** large scavenger rooms contain a boulder (Strength), a rock (Mining) or a rotten sapling
  (Woodcutting) that can be cleared; the level needed is the team's average level plus or minus 2, capped at 99 (Strat); points
  = **5 x the level requirement** (Main). The Mining and Woodcutting shortcuts were sped up on 29 Nov 2023. The rolling of the plus / minus 2
  is **OPEN-RS5**.

**Normal vs Challenge Mode:** CM scavenger stats x1.5 (HP 45, max hit 27). Drops are the same table. CM has scavengers on all three
levels (CM page).

## Farming (seeds, herbs)

- **FM1 Seed sources:** seeds drop from the stone guardians, lizardman shamans and skeletal mystics (the three "demi-boss" rooms;
  solo-scale table in Strat: shamans 5, mystics 5, guardians 3 seeds) and from raking **weeds** at the start and end of floors (Main: "the last area in those floors and the first area in the lower floor";
  `RAIDS_WEEDS` 29773; the Weeds page pins 6 weed patches at floor starts and 3 at floor ends). A kill message says which player received the seed (9 Jan 2020). Seeds are shown on the floor for 30 minutes (30 Mar 2017).
- **FM2 Weeds:** "Rake"; once raking has started it continues until cancelled. Each success gives one of **buchu, golpar or noxifer
  seed(s)**, each with a listed rarity of **1/3**, quantity **1-3** (Weeds). The 31 Jan 2019 note says "the seeds given when raking weeds
  will now rotate between the different seeds available", while the table says random 1/3 each (conflict 3); the table is used, the
  rotation is **OPEN-RS6**. Rake, spade and seed dibber come from the tool rack (right-click selects one tool, 18 Jun 2020); a rake is
  also an "old tool".
- **FM3 Plants:** two herb patches per resource room (Strat; Main: "one farming patch consisting of two herb patches"). Planting needs
  a seed dibber. A planted seed becomes the herb **30 seconds (50 ticks) later** (Plant pages, Strat: "fully grown in 30 seconds";
  growth sped up 29 Nov 2023), shown in five stages. The herb does not grow back after picking. Picking with a full inventory places
  the herb on the ground (2019). The number of herbs per patch **scales with party size** (31 Jan 2019, 16 Jun 2021; the
  amounts are **OPEN-RS7**). A party notice is shown when someone clears a patch (23 Feb 2017).

  | Seed | Farming level | Plant XP | Harvest XP | Herb |
  |---|---|---|---|---|
  | Golpar | 27 | 4 | 10 | grimy golpar |
  | Buchu | 39 | 6 | 15 | grimy buchu leaf |
  | Noxifer | 55 | 12 | 30 | grimy noxifer |

  Clean herbs are made from the grimy ones in the usual way; no Herblore level is stated for cleaning (**OPEN-RS8**). Which herb
  is used by which potion: HL1 below.
- **FM4 Water:** each resource room has a gourd tree and a geyser (HL2).

## Fishing, Hunting and Cooking (food)

- **FD1 Room type:** a resource room has a **50% chance of bats and 50% chance of fishing spots** (Food page; Strat/Main say rooms
  "without fishing spots" have **2-3 bats**). Raid food is not boosted by cooking gauntlets (Mod Ash, 7 Jan 2020).
- **FD2 Fishing:** needs a **fishing rod** and **cave worms** as bait (both scavenger drops). Three spots per room; **one of the
  three holds a cave snake**; the plain-fish spots and the snake spot swap places occasionally. While the snake is there the
  message "This seems like a very bad moment to fish here." is shown. The **cave snake** attacks the player and any player next
  to it for **3-6** damage and steals one **cooked** fish ("The snake stole some food off you!"); it never steals raw fish
  (Snake). Frequency of the swap and the snake's attack speed: **OPEN-RS9**.
- **FD3 Hunter:** bats need a **butterfly net** or a magic butterfly net; at **Hunter 99** they can be caught barehanded. The 2-3 bats
  fly around the room.
- **FD4 Tiers:** the fish and bat that can be caught depend on the level (the same level for hunting or fishing per tier); the
  Main page says the tier "is determined by the team's overall Hunter / Fishing level" (the meaning is **OPEN-RS10**). Cooking uses the same
  levels. Heals **5 + 3 x tier**:

  | Tier | Level (Fishing / Hunter and Cooking) | Raw fish | Fishing XP | Cooked fish | Cooking XP | Bat (Hunter XP / Cooking XP) | Heals |
  |---|---|---|---|---|---|---|---|
  | 0 | 1 | pysk | 20 | Pysk fish | 10 | Guanic bat (5 / 10) | 5 |
  | 1 | 15 | suphi | 23 | Suphi fish | 13 | Prael bat (9 / 13) | 8 |
  | 2 | 30 | leckish | 26 | Leckish fish | 16 | Giral bat (13 / 16) | 11 |
  | 3 | 45 | brawk | 29 | Brawk fish | 19 | Phluxia bat (17 / 19) | 14 |
  | 4 | 60 | mycil | 32 | Mycil fish | 22 | Kryket bat (21 / 22) | 17 |
  | 5 | 75 | roqed | 35 | Roqed fish | 25 | Murng bat (25 / 25) | 20 |
  | 6 | 90 | kyren | 38 | Kyren fish | 28 | Psykk bat (29 / 28) | 23 |

  The Main page's older table shows the same levels. Item symbols: `RAIDS_FISH<n>_RAW / _COOKED`, `RAIDS_BAT<n>_RAW / _COOKED`.
- **FD5 Cooking:** burning chance, the heat source (a fire needs a tinderbox and logs or kindling, kindling can be lit with a tinderbox
  per Kindling) and the cooking tick count are not stated (**OPEN-RS11**). The cache has `SKILL_FEATURE_COOKING_RAIDS_FISH<n>_COOKED` /
  `_BAT<n>_COOKED` DB rows for the recipes; they were not decoded here.
- **FD6 Points:** the player who cooked the food gets **4 + 8 x tier** points whenever the food heals anyone; **50% more** if a
  different player eats it. Psykk bats from the Thieving chest count as cooked by the player who found them. A **soft cap of 20 x players
  and hard cap of 40 x players** (points diminish gradually past 20 x players) (Main). The diminishing curve is **OPEN-RS12**.
  Xeric's aid heals more per slot, so food is rarely used in Normal (Main).

## Herblore (gourd vials and potions)

- **HL1 Making potions:** three tiers per potion: weak (-), standard, strong (+). Use a herb on a **water-filled gourd vial** while
  carrying the second ingredient. A potion is made as a **4-dose** vial. The Herblore level needed per tier:

  | Potion | Herb | Second ingredient | Weak (-) | Standard | Strong (+) |
  |---|---|---|---|---|---|
  | Elder | golpar | stinkhorn mushroom | 47, 6.5 XP | 59, 10 XP | 70, 13 XP |
  | Twisted | golpar | cicely | 47, 6.5 XP | 59, 10 XP | 70, 13 XP |
  | Kodai | golpar | endarkened juice | 47, 6.5 XP | 59, 10 XP | 70, 13 XP |
  | Revitalisation | buchu leaf | stinkhorn mushroom | 52, 13.5 XP | 65, 20 XP | 78, 26.5 XP |
  | Prayer enhance | buchu leaf | cicely | 52, 13.5 XP | 65, 20 XP | 78, 26.5 XP |
  | Xeric's aid | buchu leaf | endarkened juice | 52, 13.5 XP | 65, 20 XP | 78, 26.5 XP |
  | Antipoison | noxifer | cicely | 60, 13.5 XP | 75, 20 XP | 90, 26.5 XP |
  | Overload | noxifer | one elder, one twisted, one kodai | 60, 33.5 XP | 75, 50 XP | 90, 66.5 XP |

  Overload recipe: the three combat potions "of the same tier" (Strat) versus "any variant" (Overload page), conflict 4; the wiki
  recipe note says that if the player's Herblore is below the level for the tier of overload being made, the higher-tier combat potions make the
  highest overload tier available (Strat; Main: "downgrade into a normal overload upon creation"). The prayer enhance recipe notes
  2 ticks per craft (**OPEN-RS13**: the others are unstated).
- **HL2 Gourd vials:** the gourd tree gives **empty gourd vials** indefinitely ("Pick"; "Pick-lots" picks many); fill them by using one on
  the **geyser** (left-click "Use" since 19 Oct 2022) or with the **Humidify** spell. A gourd vial breaks when the last dose of a potion in
  it is drunk (Strat).
- **HL3 Raid-only:** none of the potions can be taken out of the Chambers (potion pages); all raid-unique items (potions, planks, food,
  kindling) are dropped on death (raid-wide doc). Enchant spells are not offered inside (14 Mar 2019) because the boosts are large; the Kodai
  potion "does not help in casting enchantment spells".
- **HL4 Combat potions (effects, boost = floor(base level x percent) + flat):**
  - **Elder** (Attack, Strength, Defence): weak **4 + 10%**, standard **5 + 13%**, strong **6 + 16%** of the base level.
  - **Twisted** (Ranged and Defence per the (+) page; the Strat table lists only Ranged): weak not on the pages read (**OPEN-RS14**),
    standard **5 + 13%**, strong **6 + 16%**. **Kodai** (Magic): weak not stated, standard **5 + 13%**, strong **6 + 16%**.
    The twisted and elder pages differ on which skills get the boost: the Strat table says elder = Attack, Strength, Defence;
    twisted = Ranged (Defence mentioned by the (+) page); conflict 5.
- **HL5 Overload:** boosts all combat stats (Attack, Strength, Defence, Ranged, Magic): weak **4 + 10%**, standard **5 + 13%**, strong
  **6 + 16%** of the base level, and **damages the player for 50 hitpoints** in hits of **10** over a few ticks (the hit count
  and interval are **OPEN-RS15**); the boost is **re-applied every 15 seconds (25 ticks) for 5 minutes (500 ticks)**, then the player is **healed 50**
  hitpoints. Overloads can be drunk to reset drained stats at that interval only. At base level 94-99 the strong boost is +21 (Overload (+) boost table; matches floor(level x 0.16) + 6).
  The Nightmare Zone overload is the same effect (Strat: "exactly the same as the one in Nightmare Zone").
- **HL6 Revitalisation:** restores all drained stats and Prayer. Strong: restores **11 + 30%** of the base level per dose (the wiki table
  starts at +11 for levels 1-3 and goes to +26 at 50-53, i.e. floor(base x 0.30) + 11). Weak and standard values: **OPEN-RS16**.
- **HL7 Prayer enhance:** gradually restores Prayer points for one dose: the strong variant lasts **290 seconds (about 483 ticks)** and restores
  1 point **activation_amount = floor(Prayer level / 2) + 31** times, once every **floor(500 / activation_amount) ticks** (Mod Ash, 5 Feb 2023,
  who also says "or 63, whichever is lower"; that cap only bites below activation_amount 8, so it never applies, conflict 6). At Prayer 82
  and above that is one point per 6 ticks. The standard and weak potions last 275 or 290 seconds
  (a chat warning is shown for the 290 s case); their activation counts and intervals are **OPEN-RS17**.
- **HL8 Xeric's aid:** works like a Saradomin brew; heals Hitpoints, raises Defence, and lowers Attack, Strength, Ranged and Magic. It
  can heal above the maximum:

  | Tier | HP | Defence | Drain (Attack, Strength, Ranged, Magic) |
  |---|---|---|---|
  | Weak (-) | 7% + 1 | 14% + 1 | 7% + 1 |
  | Standard | 12% + 2 | 18% + 2 | 9% + 2 |
  | Strong (+) | 15% + 5 | 20% + 5 | 10% + 4 |

  Rounded down. HP and drain use the base HP level for the heal and the **current** stat level for the drain (standard page formulas).
  Mod Ash's formula quoted on the weak and standard pages ("HP: TIER plus ((5 x TIER) + 2) percent. Defence: TIER plus ((4 x TIER) + 10)
  percent") gives 3 + 17% and 3 + 22% for tier 3, not 15% + 5 and 20% + 5, conflict 7; the per-tier values above are used.
- **HL9 Antipoison:** cures poison and gives immunity for **90 seconds (150 ticks) / 6 minutes (600 ticks) / 9 minutes (900 ticks)**
  (weak / standard / strong). Antipoisons were made makeable inside on 16 Jun 2021.
- **HL10 Points from potions:** points go to the player who made the potion (or took it from a boss) when it is drunk: capped at **5 doses
  x the number of players, plus five more at a reduced amount**; more if a different player drinks it; the cap counts doses, not points,
  and overloads give the most points (Main). Amounts: **OPEN-RS18**. Fixed on 12 Dec 2023: "Potions now correctly award points when sipped."
  Required Herblore: 75 for overload, 90 for overload (+).
- **HL11 Boss potion drops (Strat, solo scale):** the table lists per boss the potions dropped: Tekton overload (+) x2 / enhance x1 /
  revitalisation x1 / stinkhorn x5; Vespula overload x1 / enhance x1 / Xeric's aid x2 / revitalisation x1; Vasa overload x1 / aid x2 / twisted x2 /
  endarkened juice x5; Vanguards overload 1-3 / enhance 1 / aid 4 / revitalisation 2 / twisted 1 / elder 1 / kodai 1; Muttadiles overload x2 /
  enhance x2 / aid x1 / revitalisation x1 / twisted x2; shamans 5 seeds; mystics 5 seeds; guardians 3 seeds; Ice demon endarkened juice 7 /
  stinkhorn 7 / cicely 5; Thieving 5 / 5 / 4. The variants are the strong ones (the table headers read "(+)"). These are the room docs'
  business; they are listed here so the supply balance is visible.

## Kindling and saplings (for the Ice Demon)

- **KN1 Saplings:** the corridor before the Ice Demon room has saplings (tree id 29763, stump 29764). The tool is any axe (a bronze axe
  spawn is in the room). A sapling gives kindling per successful chop; XP depends on the amount. The stump respawns "after a few seconds"
  (Sapling; the infobox gives a time of 30 seconds, conflicting in wording: **OPEN-RS19**).
- **KN2 Yield:** **pick a random integer 0 .. floor(visible Woodcutting level / 12) inclusive, an outcome of 0 becomes 1**
  (Mod Ash, 17 Jun 2021; boosts count). The maximum is 8 at level 96. Average kindling and XP per success (Woodcutting level at
  which the maximum is first reached):

  | Max | Level | Average kindling | XP at max | Average XP |
  |---|---|---|---|---|
  | 1 | 1 | 1.0000 | 30 | 30.0000 |
  | 2 | 24 | 1.3333 | 45 | 35.0000 |
  | 3 | 36 | 1.7500 | 55 | 40.0000 |
  | 4 | 48 | 2.2000 | 62.5 | 44.5000 |
  | 5 | 60 | 2.6667 | 68.5 | 48.5000 |
  | 6 | 72 | 3.1429 | 73.5 | 52.0714 |
  | 7 | 84 | 3.6250 | 77.7 | 55.2750 |
  | 8 | 96 | 4.1111 | 81.5 | 58.1889 |

  The Kindling page prints 2.2667 for max 5, the Demon page 2.6667; the formula (outcome 0 counts as 1) gives 2.6667, conflict 8.
- **KN3 Depletion:** a successful chop topples the tree with chance **1/X**, where **X = max(3, 2 x players at the start) x 5** in the
  Ice Demon room (Mod Ash, 28 Jun 2020: elsewhere X = max(3, 2 x party size), the Demon room multiplies by 5): 1/15 solo, 1/50 for five
  players (Demon page).
- **KN4 Kindling item:** stackable, **cap 28 per stack**: a player holding 28 or more cannot chop (drop some to continue). Cannot be
  placed in storage units (12 Dec 2023). Kindling can be lit with a tinderbox to make a fire (Firemaking XP 48 listed). It is burned in the
  braziers for puzzle points (puzzles.md, ID9: 36 per kindling, 32 per brazier lit).

## Resource points (summary)

Cooking 4 + 8 x tier (soft cap 20 x players, hard cap 40 x players); potions up to 5 doses per player plus five; kindling 36 each and 32 per
lit brazier; shortcuts 5 x level; storage units are in the raid-wide doc. Totals and conversion to rewards: raid-wide doc.

## Changes up to 2026-11-03 that matter

12 Dec 2023 kindling no longer storable, potions award points when sipped; 29 Nov 2023 scavenger and herb changes (listed above); 14 Feb 2024
planks added to scavenger drops; 19 Oct 2022 geyser "Use"; 16 Jun 2021 antipoisons makeable, more herbs in big raids; 28 Jun 2020 /
17 Jun 2021 sapling depletion and kindling quotes; 18 Jun 2020 tool rack selection; 28 Nov 2019 lockpick drop; 14 Mar 2019 enchant spells
removed; 28 Feb 2019 more kindling per chop; 31 Jan 2019 scavenger runts removed, weed seeds rotate, more herbs in bigger raids.

## Open list

OPEN-RS1 antipoison item symbols and vial ids; OPEN-RS2 scavenger count and strength by party size; OPEN-RS3 empty-roll share of the
scavenger table; OPEN-RS4 scavenger points; OPEN-RS5 shortcut level roll; OPEN-RS6 weed seed rotation versus random choice; OPEN-RS7 herbs per patch
by party size; OPEN-RS8 cleaning herbs; OPEN-RS9 snake swap frequency and attack speed; OPEN-RS10 how the team's Hunter / Fishing level sets the
tier; OPEN-RS11 cooking heat source, burn chance and ticks; OPEN-RS12 cooking points diminishing curve; OPEN-RS13 craft ticks per potion;
OPEN-RS14 weak twisted / kodai boosts; OPEN-RS15 overload damage hit pattern; OPEN-RS16 weak and standard revitalisation values; OPEN-RS17
weak / standard prayer enhance activations; OPEN-RS18 potion point amounts per dose; OPEN-RS19 sapling stump respawn time. **19 open items.**

## Conflicts between sources

1. Scavenger ids: infobox 7548 Normal / 7549 CM vs cache variants A / B (same behaviour assumed).
2. Scavenger rolls: "rolls two drops at a time" (29 Nov 2023) vs "drop three items at a time" (Beast page text).
3. Weeds: random 1/3 seed table vs "rotate between the seeds" (31 Jan 2019).
4. Overload recipe: three combat potions "of the same tier" (Strat) vs "any variant" of each (Overload page).
5. Which skills the elder and twisted potions boost: Strat table (elder A/S/D, twisted Ranged) vs the (+) page text (twisted: Ranged and Defence).
6. Prayer enhance interval: "500 divided by that number, or 63 whichever is lower" (Mod Ash) vs the page formula floor(500 / amount).
7. Xeric's aid strong tier: Mod Ash's tier formula vs the page values 15% + 5 and 20% + 5.
8. Kindling average at max 5: 2.2667 vs 2.6667.
9. Cooking-point cap described as "soft cap 20 x players / hard cap 40 x players" (Main) with no curve.
