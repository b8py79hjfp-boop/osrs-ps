# CoX lobby, Ancient chest and reward model

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki (API wikitext, copies in `/tmp/claude-0/ref/wiki-cox/`) "Chambers
of Xeric" (Main), "Chambers of Xeric/Strategies" (Strat), "Chambers of Xeric/Challenge Mode" (CM), "Ancient chest" (Chest),
"Calculator:Chambers of Xeric loot" and its Lua module "Module:Chambers of Xeric calculator" (Calc), "Olmlet" (Olm),
"Metamorphic dust" (Dust), "Twisted ancestral colour kit" (Kit), "Dark journal" (Journal), "Ancient tablet" (Tablet),
"Storage unit" (Store), "Recruiting board" (Board), "Captain Rimor" (Rimor), "Mount Quidamortem" (Mount), "Combat
Achievements" (CA), and the update posts "Summer Sweep Up - Agility & Chambers of Xeric Changes" (12 Aug 2026, U-Summer),
"Summer Sweep Up: Combat" (25 Jun 2025, U-SSC), "Run Energy Changes" (8 Jan 2025, U-Run25), "Bank QoL & Mini Menu
Improvements" (22 Jan 2025, U-Bank25), "Chambers of Xeric Improvements" (28 May 2020, U-CoX20), "A Kingdom Divided" (16 Jun 2021,
U-KD); cache 2757 (obj / npc / loc configs, generated symbols). Raid-wide rules: [../cox.md](../cox.md) (CX52-CX59 are the
summary of this doc). Open items here are `OPEN-CR<n>`. The unique and common tables are server-side (cache has only item
configs), so every number below is the wiki's; anything it does not state is `OPEN`.

**Changes after the anchor.** None of the pages read carries a CoX reward change dated after 2026-11-03. The newest is
12 Aug 2026 (unique weights, inside the anchor).

## Lobby (Mount Quidamortem): places and ids

| Id | Thing | Value | Source |
|---|---|---|---|
| CL1 | Recruiting board, three map pins (1224, 3562), (1246, 3562), (1240, 3549); option "Read" | scenery `RAIDS_PARTY_RECRUITMENT` 29776; interfaces `RAIDS_LOBBY_PARTYLIST` 499, `RAIDS_LOBBY_PARTYDETAILS` 507; flow in cox.md CX2-CX3 | Board; cache |
| CL2 | Captain Rimor at the entrance, square centred (1234, 3572) radius 4 | NPC 7595 (`RAIDS_TEMPLE_CAPTAIN`), options "Talk-to", "Claim-cape", "Set-difficulty"; capes: Xeric's guard 22388 (cost 400) and the four above it | Rimor; cache |
| CL3 | Lobby bank chest (the entrance room's, inside the Chambers) and the Mount Quidamortem bank | `RAIDS_BANK_CHEST_LOBBY` 47419 / `_WORKING` 47420 (added 3 May 2023); the mountain's own bank chest is a separate scenery | Main; cache |
| CL4 | Private storage unit "next to the bank chest" in the tent outside, at (1256, 3572) | scenery `RAIDS_STORAGE_LOBBY` 30107, option "Private", PIN protected (26 Jan 2017); interface `RAIDS_STORAGE_PRIVATE` 271 | Store; cache |
| CL5 | Entrance and exit steps, with "Reload" for the leader | `RAIDS_ENTRANCE_STEPS` 29777, `RAIDS_EXIT_STEPS` 29778, `RAIDS_EXIT_STEPS_MULTI` 49999, `RAIDS_EXIT_STEPS_RELOAD` 50000 | cache; U (29 Nov 2023) |
| CL6 | Scoreboard for the world's and the global best Challenge Mode times, outside the entrance (the Main page says CoX "lacks a team scoreboard", i.e. no per-player performance table) | scenery `RAIDS_CHALLENGE_SCORES` 32544 and interface `RAIDS_CHALLENGE` 21 (names only); per-world counters **OPEN-CR1** | CM; Main; cache |
| CL7 | Ancient chest in the Olm room (map pin (3233, 5751) in instance coordinates) | closed crystal `RAIDS_REWARD_CRYSTAL` 30027, opened `RAIDS_REWARD_CHEST` 30028 ("Search"); light locs `RAIDS_REWARD_LOOTBEAM` 28848, `_BASIC` 30029, `_SPECIAL` 30030, `_KIT` 37976, `_DUST` 37977; interface `RAIDS_REWARDS` 539 over inventory `RAIDS_REWARDS` 581; varbits `RAIDS_CLIENT_REWARDINDICATOR` 5456, `RAIDS_CLIENT_NEEDSREWARDBOOK` 5457 (names only); encodings **OPEN-CR14** | Chest; cache |

## Unique roll

| Id | Rule | Source |
|---|---|---|
| CR1 | "For every **8,676** total points obtained, a 1% chance to obtain a unique loot is given" (Chest, Main). **Conflict:** Strat says **8,675** and the Calc module divides by **8675** everywhere. Supporting 8,675: the Chest footnote "with 26,025 points per raid, the Olmlet rate is ~1/1,765" - 26,025 is exactly 3 x 8,675 (3.000%), and 0.03 / 53 = 1/1,767; and the cap arithmetic 570,000 / 8,675 = 65.706%, 570,000 / 8,676 = 65.698%, both "65.7%". **OPEN-CR2**, working assumption **8,675** (two sources including the calculator code, and the 26,025 figure), behind `cox.pointsPerUniquePercent` (default 8675). The brief says 8,676. | Chest; Main; Strat; Calc |
| CR2 | **Rolls.** The team's total points `T` give `n = min(6, ceil(T / 570,000))` unique rolls, each an independent chance: rolls 1..n-1 at `570,000 / D` percent = **65.7%** and the last at `(T - 570,000 x (n-1)) / D` percent, where `D` is CR1 and `T` is capped at **3,420,000** (6 x 570,000). Example (Chest): 855,000 points -> 65.7% for a first unique and 32.85% for a second. "Up to six unique rewards ... per raid" (16 Jun 2021). Expected uniques per team = `min(T, 3,420,000) / 867,500` (Calc). If a roll succeeds no common rewards are given **to the recipient**. The team total `T` is the team points of cox.md CX21, after the death penalty (CX30) and with the CM bonus (CX32). | Chest; Main; Calc; U-KD |
| CR3 | **Recipient.** The roll is won by the team; "the player's points will also determine who is more likely to obtain the unique drop, with higher points giving a greater chance". Calc models it as `personalPoints / teamPoints` (free-for-all) or `1 / teamSize` (split). Working assumption: the recipient of each roll is drawn with weight = personal points, independently per roll (the same player may win several), behind `cox.uniqueRecipientWeightedByPoints` (default on). **Leavers:** a player who left in progress can be drawn, but "if they would do so, the reward is discarded instead" (8 Jan 2025): the unique is lost, not redrawn. Ironmen are eligible (Main). **OPEN-CR3:** weight of zero-point players, tie handling, whether a discarded roll still consumes the Olmlet roll, and whether leavers' points are in the weights (assumed yes, since the unique goes with the points). | Chest; Main; Calc; U-Run25 |
| CR4 | **Unique table**, chosen by weight after the recipient is fixed (12 Aug 2026; both modes total the stated figure; all pieces are given as items, not notes, 28 May 2020): | Chest; Calc; U-Summer |

| Item (obj) | Normal (/60) | % | Challenge Mode (/56) | % | Old (/69) |
|---|---|---|---|---|---|
| Dexterous prayer scroll (21034) | 14 | 23.33 | 12 | 21.43 | 20 |
| Arcane prayer scroll (21079) | 14 | 23.33 | 12 | 21.43 | 20 |
| Twisted buckler (21000) | 4 | 6.67 | 4 | 7.14 | 4 |
| Dragon hunter crossbow (21012) | 4 | 6.67 | 4 | 7.14 | 4 |
| Dinh's bulwark (21015) | 3 | 5.00 | 3 | 5.36 | 3 |
| Ancestral hat (21018) | 4 | 6.67 | 4 | 7.14 | 3 |
| Ancestral robe top (21021) | 4 | 6.67 | 4 | 7.14 | 3 |
| Ancestral robe bottom (21024) | 4 | 6.67 | 4 | 7.14 | 3 |
| Dragon claws (13652) | 3 | 5.00 | 3 | 5.36 | 3 |
| Elder maul (21003) | 2 | 3.33 | 2 | 3.57 | 2 |
| Kodai insignia (21043) | 2 | 3.33 | 2 | 3.57 | 2 |
| Twisted bow (20997) | 2 | 3.33 | 2 | 3.57 | 2 |

- **CR5 Weight history and check.** Normal 14+14+4+4+3+4+4+4+3+2+2+2 = **60**; CM 12+12+4+4+3+4+4+4+3+2+2+2 = **56**; the
  pre-12 Aug 2026 total was **69** (the figure in the task and in older pages): the prayer scrolls fell 20 -> 14 (Normal) and
  20 -> 12 (CM), the ancestral pieces rose 3 -> 4. The post's table matches the Chest page and Calc. The announcement of a
  unique "in the game chat" has no text on the wiki (**OPEN-CR4**).
- **CR6 Olmlet.** Rolled **only** for a player who receives an item from the unique table, at **1/53** (raised from 1/65 on
  10 Jan 2019, from 1/650 on 9 Feb 2017). With 26,025 points per raid the Olmlet rate is about 1/1,765 (CR1). The Olmlet is a
  tertiary (the chest light does not change for it). Pet rules: obj 20851, follower NPCs 7519 / 7520; "You have a funny feeling
  like you're being followed." (follower slot free) or "You feel something weird sneaking into your backpack." (follower
  already out, pet to inventory); with both full the pet is claimed from Probita in East Ardougne. **OPEN-CR5:** when the
  player receives several unique items, whether the Olmlet is rolled once per item (assumed: once per received unique) and
  the broadcast text.

## Common rewards

| Id | Rule | Source |
|---|---|---|
| CR7 | **Structure.** A player who did not win a unique gets **two rolls** on the common table, and "the two rolls cannot end on the same drop" (unlike ToA and ToB). Each of the 33 items has the same chance (1/33) per roll; the calculator weights them as **99** slots: weight 3 for every rune, arrow, ore, gem and "other" item, and for each herb weight **2** plus weight **1** for its seed (CR8, table below). A **Dark journal** is always added unless one is **in the bank** (the Journal page also lets a lost one be reclaimed from the chest). **Ancient tablet:** listed at 1/10 with "does not drop if one is already owned" and no two-roll marker, so it is taken as a separate 1/10 roll beside the two (the 33 items at 1/33 already sum to one certain drop per roll). **OPEN-CR6:** the tablet's mechanics (once per chest, 1/10, only for players on the common path or for every player), and whether "distinct" excludes the seed form of a herb already rolled. **OPEN-CR12:** the Dark journal check (the bank only, as the page says, or the inventory too) and the Journal page's own reclaim from the chest. | Chest; Calc; Journal; Tablet |
| CR8 | **Herbs and seeds** (since 25 Jun 2025, hotfix 26 Jun 2025): herb quantities were cut by 20% (40% for dwarf weed) and half of the cut amount became seeds at **1 seed per 7 herbs** (ranarr, snapdragon, torstol) or **1 per 8** (toadflax, irit, avantoe, kwuarm, cadantine, lantadyme, dwarf weed); seeds are no longer guaranteed: a rolled herb is a **seed with probability 33%** instead (Calc: weight 2 for the herb, 1 for its seed). Herbs are noted, seeds are not. **OPEN-CR13:** whether a double roll of the same herb type counts as "the same drop" when one comes out as a seed, and the seed quantity (the page gives none; Calc divides the herb divisor by 7 or 8, i.e. multiplies the divisor). | Chest; U-SSC; Calc |
| CR9 | **Quantity.** Calc: `quantity = floor(min(personalPoints, 131,071) / divisor)`; the chest page prints "1-N" where N is the value at 131,071 points (131,071 = 2^17 - 1). **OPEN-CR7:** whether the in-game quantity is exactly that value or a random amount from 1 to it; the calculator and the page agree on the maximum except three herbs where the page is 1 higher (avantoe 337 vs 336, kwuarm 289 vs 288, lantadyme 442 vs 441). Torn prayer scroll and Dark relic: 1. **OPEN-CR11:** the points used are the personal points at completion (after the death penalty and including points earned while a leaver is gone), assumed. All non-stackable items are given as bank notes (marked "noted" on the page; the runes, arrows, seeds, fangs and relics are not). Table below. | Chest; Calc; U-Bank25 |

Common table (divisor from Calc; "max" is the page's figure at 131,071 points; weight is Calc's out of 99):

| Item | Divisor | Max (page) | Weight | Item | Divisor | Max (page) | Weight |
|---|---|---|---|---|---|---|---|
| Death rune | 36 | 3,640 | 3 | Silver ore (noted) | 20 | 6,553 | 3 |
| Blood rune | 32 | 4,095 | 3 | Coal (noted) | 20 | 6,553 | 3 |
| Soul rune | 20 | 6,553 | 3 | Gold ore (noted) | 44 | 2,978 | 3 |
| Rune arrow | 14 | 9,362 | 3 | Mithril ore (noted) | 32 | 4,095 | 3 |
| Dragon arrow | 202 | 648 | 3 | Adamantite ore (noted) | 166 | 789 | 3 |
| Grimy ranarr weed (noted) | 946 | 138 | 2 | Runite ore (noted) | 2,000 | 65 | 3 |
| Grimy toadflax (noted) | 624 | 210 | 2 | Uncut sapphire (noted) | 188 | 697 | 3 |
| Grimy irit leaf (noted) | 194 | 675 | 2 | Uncut emerald (noted) | 142 | 923 | 3 |
| Grimy avantoe (noted) | 389 | 337 | 2 | Uncut ruby (noted) | 242 | 541 | 3 |
| Grimy kwuarm (noted) | 454 | 289 | 2 | Uncut diamond (noted) | 508 | 258 | 3 |
| Grimy snapdragon (noted) | 1,560 | 84 | 2 | Lizardman fang | 28 | 4,681 | 3 |
| Grimy cadantine (noted) | 396 | 330 | 2 | Pure essence (noted) | 2 | 65,535 | 3 |
| Grimy lantadyme (noted) | 297 | 442 | 2 | Saltpetre (noted) | 28 | 4,681 | 3 |
| Grimy dwarf weed (noted) | 240 | 546 | 2 | Teak plank (noted) | 96 | 1,365 | 3 |
| Grimy torstol (noted) | 972 | 134 | 2 | Mahogany plank (noted) | 238 | 550 | 3 |
| Ranarr seed | 6,622 | - | 1 | Dynamite (noted) | 54 | 2,427 | 3 |
| Toadflax seed | 4,992 | - | 1 | Torn prayer scroll | - (1) | 1 | 3 |
| Irit seed | 1,552 | - | 1 | Dark relic | - (1) | 1 | 3 |
| Avantoe seed | 3,112 | - | 1 | Kwuarm seed | 3,632 | - | 1 |
| Snapdragon seed | 10,920 | - | 1 | Cadantine seed | 3,168 | - | 1 |
| Lantadyme seed | 2,376 | - | 1 | Dwarf weed seed | 1,920 | - | 1 |
| Torstol seed | 6,804 | - | 1 | | | | |

The divisors total 99 slots: 5 runes/arrows x 3 + 10 herbs x 2 + 10 seeds x 1 + 10 ores/gems x 3 + 8 others x 3 = 15 + 20 + 10 + 30 + 24.
Worked example (Calc default, 20,000 personal points): death rune 555, coal 1,000, ranarr weed 21, runite ore 10, pure essence 10,000.
Seed divisors are the herb's divisor x 7 or x 8 as in CR8 (6,622 = 946 x 7; 4,992 = 624 x 8). Per-item max for seeds is not on the page.

## Tertiaries and Challenge Mode

| Id | Rule | Source |
|---|---|---|
| CR10 | **Elite clue scroll** 1/12, rolled "only ... when the player does not get a broadcasted unique reward" (so on the common path, once per player); with Elite Combat Achievements the perk is "5% increased chance to receive an elite clue scroll from all sources (excluding implings)", i.e. 1/12 x 1.05 = 1/11.43, while the brief says **1/11**. **Conflict**, **OPEN-CR8**: working assumption 1/12 x 1.05 via the perk multiplier (no rounding of the denominator), behind `cox.eliteClueMultiplier`. | Chest; CA; brief 1.2 |
| CR11 | **Challenge Mode tertiaries** (only CM, only if the raid ends inside the target time of cox.md CX50): **Twisted ancestral colour kit** (24670) **1/75** and **Metamorphic dust** (22386) **1/400**, rolled per player, in addition to a unique or commons (the Kit and Dust pages say so), repeatable after the first (28 May 2020 for dust, 24 Mar 2021 message); both are broadcast and have their own chest light. CM also adds 5,000 points per player to the team total (cox.md CX32); the Olmlet and commons are as in normal mode with the CM weights. **OPEN-CR9:** the time clock and scaled-size rule (cox.md OPEN-CX28), whether the dust/kit roll uses the scaled size for the band, and the broadcast texts. | CM; Dust; Kit; Chest |
| CR12 | **Chest light:** white (commons), **purple** (a unique), **turquoise** (dust), **green** (kit); several at once alternate. **Unclaimed rewards:** the Chest page says "any unclaimed regular loot is placed on the ground upon leaving the raid" (the brief says the same); the Store page says the storage unit "will contain ... any rewards the player did not retrieve from the ancient chest", the 26 Jan 2017 post sends them to the private storage "providing there is space", otherwise they drop, and 27 Jul 2022 lets Ultimate Ironmen drop them outside because they cannot use storage. **Conflict**, **OPEN-CR10**: working rule is the private storage first (newest dated source), the ground when full or for Ultimate Ironmen, behind `cox.unclaimedLootToStorage` (default on). | Chest; Store; U |
| CR13 | **Books and Ironman rules.** The raid's books and their read flags are in cox.md CX58. Ironmen: boss drops go to everyone when an Ironman did the most damage (9 Mar 2017); in the chest Ironmen get their own rolls ("eligible for rewards when completing alongside other players", Main). Unique items are given as items, not notes (28 May 2020). | Main; U |

## Worked examples (assumption D = 8,675; recompute with 8,676 for the alternative)

| Team points | Rolls | Chance per roll | Expected uniques |
|---|---|---|---|
| 26,025 | 1 | 3.00% | 0.030 |
| 100,000 | 1 | 11.53% | 0.115 |
| 570,000 | 1 | 65.71% | 0.657 |
| 855,000 | 2 | 65.71%, 32.85% | 0.986 |
| 3,420,000 and above | 6 | 65.71% each | 3.942 |

## Open list

OPEN-CR1 scoreboard per-world counters and layout (cache names only); OPEN-CR2 points per percent (8,675 vs 8,676);
OPEN-CR3 recipient weighting corner cases and discarded leaver rolls; OPEN-CR4 unique announcement text and the broadcast
texts for the Olmlet, dust and kit; OPEN-CR5 Olmlet when several uniques; OPEN-CR6 Ancient tablet roll and the meaning of
"distinct" rolls; OPEN-CR7 exact versus random common quantity and the three off-by-one maxima; OPEN-CR8 elite clue chance
with the Elite Combat Achievements perk; OPEN-CR9 CM target-time clock and band for scaled parties; OPEN-CR10 unclaimed
loot destination; OPEN-CR11 whether the common quantity uses personal points after the death penalty and after a leaver's
discard (assumed: yes, personal points at completion); OPEN-CR12 the Dark journal check (bank only, or bank plus inventory
and the Journal's own reclaim from the chest); OPEN-CR13 seed-versus-herb probability when the herb is rolled twice
(distinct rolls) and the quantities of seeds; OPEN-CR14 the reward chest interface encodings (`RAIDS_REWARDS` 539 and
inventory 581, `RAIDS_CLIENT_REWARDINDICATOR`, light locs) for the R4 client contract. Plus the raid-wide items in
[../cox.md](../cox.md). **14 open items.**

## Conflicts between sources

1. Points per 1% unique chance: 8,676 (Chest, Main, brief) vs 8,675 (Strat, Calc module): CR1.
2. Unique total weight: 69 (the task text, older pages, the update's "old weighting" column) vs 60 Normal / 56 CM (Chest, Calc, brief) after 12 Aug 2026: CR5. Not a source conflict once dated; recorded because the task text names 69.
3. Common maxima: Chest prints 337 / 289 / 442 for avantoe / kwuarm / lantadyme; Calc's divisors give 336 / 288 / 441: CR9.
4. Elite clue: 1/12 with a 5% perk (CA) vs the brief's 1/11: CR10.
5. Unclaimed rewards: on the ground (Chest, brief) vs private storage when there is room (Store, 26 Jan 2017, 27 Jul 2022): CR12.
6. Main's "the points multiplier ... remain the same" vs Strat's "will decrease" for leavers: cox.md CX31.
