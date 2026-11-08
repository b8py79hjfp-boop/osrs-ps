# ToA lobby, burial chamber and reward model

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki (API wikitext, copies in `/tmp/claude-0/ref/wiki-toa/`) "Chest
(Tombs of Amascut)" (Chest), "Calculator:Tombs of Amascut loot" and its Lua module "Module:Tombs of Amascut loot" (Calc),
"Tombs of Amascut" (Main), "Sarcophagus (Osmumten)" (Sar), "Rewards Niche" (Niche), "Fossilised dung" (Dung), "Cache of
runes" (Cache), "Thread of Elidinis" (Thread), "Jewel of the Sun", "Jewel of Amascut", "Keris partisan", "Perdu", "Cursed
phalanx" (Phalanx), "Masori crafting kit" (Kit), "Menaphite ornament kit" (MKit), "Remnant of Akkha", "Ancient remnant",
"Tumeken's guardian" (Pet), "Bad luck mitigation" (BLM), "Icthlarin's shroud", "Bank Camel", "Sack (Tombs of Amascut)",
"Scoreboard (Tombs of Amascut)", "Helpful Spirit", "Deposit pot", "Item Retrieval Service", and the update posts "Tombs of
Amascut" (24 Aug 2022, U-Rel), "Tombs of Amascut: Ornament Kits & More!" (8 Sep 2022, U-Orn), "Quest Speedrunning,
Wilderness Boss Rework Rewards & Poison Dynamite Betas" (14 Sep 2022, U-Q), "Tombs of Amascut Drop Mechanics & Osmumten's
Fang" (28 Oct 2022, U-Drop), "Summer Sweep Up: Combat" (25 Jun 2025, U-SSC), "More Doom Tweaks, Poll 84, & Summer Sweep Up
Changes" (20 Aug 2025, U-Aug); RuneLite "Tombs of Amascut" plugin facts (RL-TOA: `UniqueChanceCalculator`,
`PurpleWeightingManager`, `PointsTracker`; BSD-2, facts only), RuneLite core loot tracker (RL-Loot), and cache 2757 (obj / npc /
loc configs, generated symbols). Raid-wide rules: [../toa.md](../toa.md) (TA-ids). Open items here are `OPEN-TR<n>`. The unique
and common tables are server-side (the cache has only item configs), so every number below is the wiki's, the posts' or RL's;
anything none of them states is `OPEN`. Seconds convert at 0.6 s per tick.

**Changes after the anchor.** None of the pages read carries a ToA reward change dated after 2026-11-03. The newest are
19 Aug 2026 (item renames only), 20 Aug 2025 (purple weights interpolate every 5 levels, TR9), 25 Jun 2025 (lower unique chance
above level 300, fang and Lightbearer weights fall at 350 / 400 / 450 / 500, the Jewel of Amascut, jewel base rate 1/180 -> 1/50,
seed divisors, cache of runes quantities, the deposit pot), all inside the anchor.

## Lobby and burial chamber: places and ids

| Id | Thing | Value | Source |
|---|---|---|---|
| TL1 | Grouping Obelisk, option "Inspect", pin (3358, 9119) | loc 46068 (46069 no-op, 46070 top); interfaces `TOA_PARTYLIST` 772, `TOA_LOBBY` 773, `TOA_PARTYDETAILS` 774, `TOA_INVOCATIONS` 776; flow in toa.md TA2-TA7 | Obelisk; cache |
| TL2 | Scoreboard "Read", pin (3355, 9115) | loc 46071 (46072 no-op); interface `TOA_SCOREBOARD` 775; global / personal attempts, completions, deaths, best times per mode and team size | Score; cache |
| TL3 | Invocation Board "Read" | loc 46073 (46074 no-op); a read-only list of the 46 invocations (toa.md TI1-TI46) | U-Rel; cache |
| TL4 | Bank Camel "Bank" / "Collect", pin (3360, 9117) radius 3 | loc 46075 (46076 no-op), named variant "Cam the Camel" 46231, npc 11806 size 2; talk to it by using a camulet on it; Bank Camel parent 46223 | Bank Camel; cache |
| TL5 | Sack "Search", pin (3353, 9117) | loc 46077; a find (needle or desert-grown grain, equal chances) has 0.4% at Thieving 1 rising to 3.9% at 99, always fails with a full inventory (Mod Ash, 31 Aug 2022); failure messages: `You go to search the sack, but the bank camel glares at you menacingly and spits in your direction.`, and with a camulet worn `Hey, get your hands off my food! Unless you'd like me to start eating the contents of your bank instead!` (the camel's line) | Sack; cache |
| TL6 | Item Retrieval chest "Claim" (the gravestone chest) | loc 46078 (46079 no-op): reclaim lost items, fee up to 500,000 coins (toa.md TA45) | Main; IRS; cache |
| TL7 | Cape chest "Search" | loc 46080 (46081 no-op): Icthlarin's shrouds (toa.md TA68) | Shroud; cache |
| TL8 | Rewards Niche / False Door "Claim", pin (3370, 9114) | multiloc 46224 on varbit `TOA_SHOULD_HAVE_LOOT` 14319: 0 = False Door 46083, 1 = Rewards Niche 46082, 2 = none (-1); no-op 46084 | Niche; cache |
| TL9 | Crate "Search" | loc 46085 / 46225 (parents `TOA_LOBBY_LAMP_CRATE*`); contents are not stated on the wiki. The antique lamp of *Into the Tombs* is given by Maisa, not by the crate; Maisa also prompts for the XP lamp (U-Orn) | Into; U-Orn; cache |
| TL10 | Lobby entry "Enter" / exit "Leave" | locs 46089 / 46087 (no-ops 46090 / 46088); pyramid entrance multiloc 44596 on varbit 13837 | cache |
| TL11 | Pickaxe cavity | locs 49566-49582 and 55810 / 55811 (empty, iron ... trailblazer, Leagues): deposit and retrieve the Path of Het pickaxe | U-P80; cache |
| TL12 | Maisa npc 11807 ("Talk-to"), lobby Maisa 11836 and the lesser gods 11837 / 11838 / 11839 shown by varbit 13836 (after *Into the Tombs*) | npcs | Maisa; cache |
| TL13 | Helpful Spirit npc 11694 ("Claim"), pin (3547, 5157); Deposit pot loc 49583 ("Deposit"), pin (3546, 5154) | the Nexus (region 14160); see toa.md TA51 | Spirit; Pot; cache |
| TL14 | Osmumten npcs: 11689 ("Talk-to", "Proceed"), 11690 ("Talk-to", "Begin"), 11692 ("Talk-to"), 11693 ("Talk-to", "Leave"); the vault spirit 11691 ("Talk-to"); pin (3680, 5144) | burial chamber (region 14672) | Osm; cache |
| TL15 | Sarcophagus, pin (3679, 5140) | closed (no unique) 44825, closed with a unique 44826 ("Open"), opened 44934 ("Search"); tile glows `TOA_VAULT_TILE_GLOW_STANDARD` 44996 and `_RARE` 44997 | Sar; cache |
| TL16 | Eight reward chests, pins (3674, 5148 / 5145 / 5142 / 5139) and (3687, same four y) | not-mine 41696 ("Open"), mine 44786 ("Open"), opened and emptied variants 44787-44792 ("Search"); per-player state varbits `TOA_VAULT_CHEST_0..7` 14356-14360 and 14370-14372, the sarcophagus owner `TOA_VAULT_SARCOPHAGUS` 14373 | Chest; cache |
| TL17 | Burial-chamber scoreboard "Read" (loc 44942, opens `TOA_RAID_SUMMARY` 482) and the bags "Search" (loc 44984, The jackal's torch) | locs | Main; cache |
| TL18 | Reward interface `TOA_CHESTS` 771 over inventory `TOA_CHESTS` 811 with buttons "Bank-all", "Take-all", "Discard-all" | RL-Loot reads the chest container plus `TOA_CLIENT_RAID_LEVEL`, `TOA_CLIENT_P0..P7` and `TOA_DAMAGE_DONE` when the interface opens (the server sets them first) | RL-Loot; cache; brief |

## Points used for rewards

| Id | Rule | Source |
|---|---|---|
| TR1 | **Contribution.** Rewards use each player's **contribution** = final Total points minus the 5,000 start (the Chest page's prose: "At the end of the raid, the starting 5,000 points are subtracted when calculating loot and purple percentage", cited to a JagexAsh reply of 17 Jul 2024 that itself only says "yup." to a user's question; the varp `TOA_PERSONAL_CONTRIBUTION` 3606 holds exactly this). The **team contribution** `T` is the sum over all players that took part, including players who left or disconnected ("their points will still be counted", Mod Ash, 28 Mar 2023). Points after the death penalties, the MVP bonuses and both caps (toa.md TA35-TA39). A Total below 5,000 gives a contribution of 0 (**OPEN-TR1**, clamp assumed). | Chest; U-Drop; RL-TOA |
| TR2 | **Personal versus team points.** Commons, the elite clue and (assumed) the pet use the **player's own** contribution `C`; the unique uses the team's `T`. The pet is "determined similarly to uniques" and the Calc module shows a per-person pet chance equal to the team chance divided by the team size, which is the average contribution; RL-TOA's overlay uses the team total for a party chance. **OPEN-TR2**, working assumption: pet chance from the player's own `C`, behind `toa.petUsesPersonalPoints` (default on). | Chest; Calc; RL-TOA |

## Unique roll

| Id | Rule | Source |
|---|---|---|
| TR3 | **Unique chance.** "A 1% chance to receive a unique item for every `10,500 - 20 x RL` total reward points", so the team's chance is `p = min(0.55, T / (100 x D))` with `D = 10,500 - 20 x RL'`. `RL'` (the Chest page's scaled level): `RL' = RL` for `RL <= 310`; `310 + (RL - 310) / 3` for `310 < RL <= 430`; `350 + (RL - 430) / 6` above 430. The Calc module and RL-TOA compute it with **integer** divisions in two steps (above 430 first `x = 430 + (RL - 430) / 2`, then `RL' = 310 + (x - 310) / 3`); that is what the server should do (**OPEN-TR3**: continuous on the page, integer in both programs; the difference is at most 2 levels, e.g. RL 500: 361.67 vs 361). Denominators `D` (integer rule): RL 0 -> 10,500; 150 -> 7,500; 300 -> 4,500; 350 -> 4,040; 400 -> 3,700; 450 -> 3,440; 500 -> 3,280; 550 -> 3,100; 600 -> 2,940. Example (Chest): at level 400, `D = 10,500 - 20 x 340 = 3,700` points per 1%. Calc also floors the denominator at 150,000 / 100 = 1,500 points, which no normal level reaches. | Chest; Calc; RL-TOA |
| TR4 | **Old formula (superseded).** The 28 Oct 2022 post had "raid levels from 400 to 550 only have 1/3 the effect", levels above 550 add nothing (RL clamped at 550) and example denominators of 4,500 at level 300 and 1,500 at level 550. The 25 Jun 2025 change "reduced overall purple chance for Raid Levels beyond 300" and the Chest page now shows the 310 / 430 break points (1/3 effect above 310, 1/6 above 430). RL-TOA still clamps the level at 550 for the unique chance. **OPEN-TR4:** whether levels 551-600 are clamped (RL-TOA, post: yes; Calc, Chest: no). Working assumption: no clamp, behind `toa.uniqueLevelClamp550` (default off); the difference at 600 is a denominator of 2,940 vs 3,100. | U-Drop; Chest; Calc; RL-TOA |
| TR5 | **One roll per raid, cap, recipient.** There is a single roll for the team ("Only one player per raid can receive a unique"). The chance is capped at **55%** and "excess points will not contribute towards a second roll". The recipient is drawn among the players with probability proportional to their contribution `C_i / T` ("the % of the unique being in your name is directly equivalent to the % of points you contributed"). **Leavers:** their points count in `T` (raising the team chance); whether a leaver can be drawn is not stated, working assumption: the draw is among players still in the raid at completion, with `T` still including leavers, behind `toa.leaverCanWinUnique` (default off, **OPEN-TR5**). Ties and zero-contribution players: weight 0. | Chest; U-Drop |
| TR6 | **Fewer than 1,500 points.** The sources say *Total* points: "If the player finished the raid with fewer than 1,500 points, they will instead receive fossilised dung" (Chest), Dung page "fewer than 1,500 total reward points" and the post "You must have at least 1,500 Total points to be eligible for rewards, otherwise you get some Dung". Item 27216, quantity 1 noted, "Always", no commons; loot sound 2136 (14 May 2025). Total starts at 5,000 and can fall below 1,500 through deaths (20% each, minimum 1,000). **Two readings, both OPEN (OPEN-TR6):** (A) the literal one, `Total < 1,500`; (B) the contribution `C = Total - 5,000 < 1,500`, which the Chest page's 1,126-coin minimum quantity (about 0.75 x 1,501) would support. No source decides; the working choice is the literal reading (A), behind `toa.dungThresholdOnContribution` (default off; on = reading B). Dung excludes commons; whether it also excludes tertiaries and the unique draw is assumed yes. The item is tradeable (cost 10). | Chest; Dung; U-Drop |
| TR7 | **Unique table at levels up to 300.** Weights Osmumten's fang 70, Lightbearer 70, Elidinis' ward 30, Masori mask 20, Masori body 20, Masori chaps 20, Tumeken's shadow (uncharged) 10, total 240 (= 7/24, 7/24, 3/24, 2/24 x3, 1/24 in the Chest drop table; "1/3.43, 1/3.43, 1/8, 1/12, 1/12, 1/12, 1/24"). | Chest; Calc; U-Drop |
| TR8 | **Unique item ids (cache):** Osmumten's fang 26219, Lightbearer 25975, Elidinis' ward 25985 (tradeable; the repaired "(f)" 27251 and "(or)" 27253 are not drops), Masori mask 27226, Masori body 27229, Masori chaps 27232, Tumeken's shadow (uncharged) 27277 (charged 27275 is not a drop). Uniques are given as items. | Chest; cache |
| TR9 | **Reweighting above level 300** (25 Jun 2025 at 350 / 400 / 450 / 500; since 20 Aug 2025 "scale and interpolate between Raid Level invocations ... adjusted every 5 Levels"). Only the Fang and the Lightbearer lose weight; the others stay 30 / 20 / 20 / 20 / 10. The Calc module (which says "temporary working implementation") and RL-TOA's `PurpleWeightingManager` agree, with integer rounding: Fang `70 - floor((RL-300)/5)` for 300-350; `60 - floor(2(RL-350)/5)` for 350-400; `40` for 400-450; `40 - floor((RL-450)/5)` for 450-500; `30` from 500. Lightbearer `70 - floor((RL-300)/5)` for 300-350; `60 - floor((RL-350)/5)` for 350-400; `50 - floor((RL-400)/5)` for 400-450; `40 - floor((RL-450)/10)` for 450-500; `35` from 500. At 350, 400, 450 and 500 this reproduces the Chest page's 1/3.67 / 1/4.75 / 1/4.5 / 1/5.5 (Fang) and 1/3.67 / 1/3.8 / 1/4.5 / 1/4.71 (Lightbearer). The rounding between those levels is the wiki's, not Jagex's (**OPEN-TR7**), flag `toa.purpleReweightRounding`. Table below. | Chest; Calc; RL-TOA; U-SSC; U-Aug |
| TR10 | **Raid-level thresholds and the extra roll.** Fang and Lightbearer need raid level **50**, the other five uniques **150** (the greyed-out icons in the invocations panel; Chest footnotes "0-49" and "0-149"; the release post: above 50 "a reasonable shot at the Lightbearer and Fang", Normal mode for the rest). If the unique drawn is **below its threshold** (`RL < level`) the game performs an additional **1/50** roll: success = the item is given, failure = "an untradeable tertiary item is received instead" (TR19). Effective chance of item `i`: `p x w_i / W x (1 if RL >= level_i else 1/50)`; the failed share is not redistributed. Example (post): RL 0 with 10,500 points, Tumeken's shadow = 1/100 x 1/24 x 1/50 = 1/120,000. | Chest; U-Drop; U-Rel; Calc |
| TR11 | **Visual and handover.** If a unique is rolled "the flames around Osmumten's sarcophagus will glow purple rather than white" (sarcophagus 44826 with glow tile 44997, else 44825 / 44996) and only the designated player can open it; opening plays an animation that reveals the item (the opened sarcophagus is loc 44934). The player's own chest is emptied because no common rolls are made for them. Where the winner's tertiary and challenge items go is **OPEN-TR8** (assumed: the winner's chest, as usual). A broadcast and its text for uniques (and the pet and jewels) are not on the wiki (**OPEN-TR9**). | Chest; Sar; cache |

Unique weights by raid level (integer rule of TR9; the five unchanged weights are 30 / 20 / 20 / 20 / 10 = Ward / Mask / Body / Chaps / Shadow):

| RL | Fang | Lightbearer | Total | RL | Fang | Lightbearer | Total |
|---|---|---|---|---|---|---|---|
| 0-300 | 70 | 70 | 240 | 405 | 40 | 49 | 189 |
| 305 | 69 | 69 | 238 | 425 | 40 | 45 | 185 |
| 310 | 68 | 68 | 236 | 450 | 40 | 40 | 180 |
| 325 | 65 | 65 | 230 | 455 | 39 | 40 | 179 |
| 350 | 60 | 60 | 220 | 475 | 35 | 38 | 173 |
| 355 | 58 | 59 | 217 | 495 | 31 | 36 | 167 |
| 375 | 50 | 55 | 205 | 500-600 | 30 | 35 | 165 |
| 400 | 40 | 50 | 190 | | | | |

## Common rewards

| Id | Rule | Source |
|---|---|---|
| TR12 | **Structure.** A player who did not receive a unique (and did not get dung) gets **three rolls** on the common table; all **27 entries have the same chance (1/27)** per roll. Inference, not stated by the ToA sources: the rolls are independent and the same entry can repeat (the CoX doc says its two rolls cannot match "unlike ToA and ToB", which is the only support). The chest is sealed with a key icon showing the player which chest to open when there is no unique. No other limit (the table is "shared across any raid level"). | Chest; Calc |
| TR13 | **Quantity.** `quantity = max(1, floor(floor(C / divisor) x scale))` where `scale` (Chest formula and Calc): 1 below level 300; `1.15 + 0.01 x floor((RL - 300) / 5)` from 300 (15% more at 300, 16% at 305, 35% at 400, 45% at 450, 75% at 600). Cache of runes ignores the formula (quantity 1). Rounding: Calc truncates the divide, then the scale, then takes at least 1; the Chest page prints the formula without floors. **OPEN-TR10:** exact versus random quantity (the loot table shows ranges), floor order, and the page's printed min-max ranges (e.g. coins 1,126-51,285, death rune 57-2,564), which cannot be reproduced from the formula at any level (the minima look like a 1,501-point entry scaled by 0.75, the maxima like 51,285 scaled points) and are therefore not used. | Chest; Calc |
| TR14 | **Entry mode factor (conflict).** The Calc module scales commons by **0.75 below level 150** ("decreased by 25%") and by nothing from 150 to 299, whereas the Chest page says level 300 gives "15% additional non-unique loot relative to a Entry/Normal mode clear" (equal for Entry and Normal). Working assumption: the module's 0.75 for Entry (an explicit, recently maintained code branch; the page sentence is loose prose), behind `toa.entryCommonScalePercent` (default 75; set 100 for the page's reading). **OPEN-TR11.** | Calc; Chest |
| TR15 | **Common table** (divisor from the Chest page, equal in Calc; item ids from the cache; "noted" as the page's quantity column). Entry order is the Calc order. Seed divisors rose on 25 Jun 2025 (toadflax 1,400 -> 2,000, ranarr 1,800 -> 2,500, torstol and snapdragon 2,200 -> 3,200). | Chest; Calc; U-SSC; cache |

| Item (id) | Divisor | Noted | Item (id) | Divisor | Noted |
|---|---|---|---|---|---|
| Coins (995) | 1 | no | Cactus spine (6016) | 600 | yes |
| Death rune (560) | 20 | no | Dragonstone (1615) | 600 | yes |
| Soul rune (566) | 40 | no | Battlestaff (1391) | 1,100 | yes |
| Gold ore (444) | 90 | yes | Coconut milk (5935) | 1,100 | yes |
| Dragon dart tip (11232) | 100 | no | Lily of the Sands (27272) | 1,100 | yes |
| Mahogany logs (6332) | 180 | yes | Toadflax seed (5296) | 2,000 | no |
| Sapphire (1607) | 200 | yes | Ranarr seed (5295) | 2,500 | no |
| Emerald (1605) | 250 | yes | Torstol seed (5304) | 3,200 | no |
| Gold bar (2357) | 250 | yes | Snapdragon seed (5300) | 3,200 | no |
| Potato cactus (3138) | 250 | yes | Dragon med helm (1149) | 4,000 | yes |
| Raw shark (383) | 250 | yes | Magic seed (5316) | 6,500 | no |
| Ruby (1603) | 300 | yes | Blood essence (26390) | 7,500 | no |
| Diamond (1601) | 400 | yes | Raw manta ray (389) | 450 | yes |
| Cache of runes (27293) | quantity 1 | no | | | |

Worked example (formula of TR13, `C = 20,000`): at level 300 (scale 1.15) coins 23,000, death rune 1,150, soul rune 575, gold ore 255,
dragon dart tip 230, mahogany logs 127, sapphire 115, ruby 75; at level 400 (1.35) coins 27,000, death rune 1,350, soul rune 675,
gold ore 299; at level 600 (1.75) coins 35,000, death rune 1,750; Entry (0.75) coins 15,000, death rune 750. At `C = 1,500` the
smallest entry (battlestaff, seeds, helm, essence) is 1.

| Id | Rule | Source |
|---|---|---|
| TR16 | **Cache of runes** (item 27293, untradeable, "Open"): "raid factors such as raid level or contribution points have no effect"; one rune type, equally likely blood, soul or death, with the quantity: 500-1,000 (weight 6/30 each type), 1,000-1,500 (3/30 each), 3,000 (1/30 each); average 1,125. Since 25 Jun 2025 (before: 1,000-1,500 / 1,800-2,400 / 2,800-3,600). Uniform inside a range (assumed). | Cache; U-SSC |

## Tertiaries, pet and the untradeable fallback

| Id | Rule | Source |
|---|---|---|
| TR17 | **Rolled per player** and "alongside any reward" (a unique does not stop them; dung does, assumed). The **ancient key** (27369) is not a chest reward: it comes from the Bank Camel after *Into the Tombs* (toa.md TA69). Thread of Elidinis: base **1/10** (U-Drop). Each Keris partisan jewel (Eye of the Corruptor 27285, Jewel of the Sun 27289, Breach of the Scarab 27283, Jewel of Amascut 30893): base **1/50** each, **4/50** for any jewel (1/180 and 1/60 before 25 Jun 2025). | Chest; U-SSC; U-Drop |
| TR18 | **Bad luck mitigation.** The chance interpolates linearly from the base rate at 0 kills to **3x** at a count of **1.5 x the base denominator**: `p(KC) = base x (1 + 2 x min(KC, 1.5 x D) / (1.5 x D))` with `D = 10` for the thread (3/10 at KC 15, 1/3.33) and `D = 50` for a jewel (3/50 at KC 75, 1/16.67; "any jewel" 4/50 rising to 12/50). Mod Ash (15 May 2023): "1.5 x" the denominator. The count is "the player's total raid completion count"; working assumption: Entry + Normal + Expert (varps 3645-3647), **OPEN-TR12**. **Thread:** after the first thread is received the chance is a flat **1/50** (Mod Ash, 5 May 2023); Perdu sells it for 350,000 coins to anyone who has received it at least once. | Chest; Thread; BLM; Perdu |
| TR19 | **Jewel duplicate protection.** A player who lacks one or more of the four jewels (the page says only that the player "has the jewel or the partisan in their possession"; the inventory, worn equipment and bank wording belongs to the challenge rewards, TR22, and is assumed to apply) is guaranteed an unowned one when the jewel roll succeeds, so unowned jewels are 1/37.5, 1/25, 1/12.5 effective when the player owns one, two, three jewels; with all four owned the base rates apply and duplicates are allowed (the 14 Sep 2022 post said "all three" because the Jewel of Amascut did not exist yet). **Fallback of a failed gated unique** (TR10): "an untradeable tertiary item is received instead"; which one (thread or an unowned jewel, drawn how) is **OPEN-TR13**, working assumption: a uniform draw over the untradeables the player may currently receive (thread unless owned and flat rule, unowned jewels), behind `toa.gatedFailFallback`. | Chest; U-Q; U-Drop |
| TR20 | **Elite clue scroll** (item 12073): chance `min(25%, C / 200,000)` ("1/100 for every 2,000 reward points up to a maximum of 25/100", Mod Ash 6 Mar 2024: "for each 2k points in your personal contribution, you get 1 percentage point of clue chance, up to a max of 25%"), multiplied by 1.05 with the Elite Combat Achievements perk ("as usual"); whether the cap or the perk applies first is **OPEN-TR14** (assumed: cap, then x1.05). Rolled on the personal contribution. | Chest; Calc |
| TR21 | **Pet: Tumeken's guardian** (item 27352; follower; the alternate Elidinis' guardian 27354 by Metamorphosis). 1% per `350,000 - 700 x RL''` of the player's points, `RL'' = RL` up to 400; `400 + (RL - 400) / 3` for 400-550; `450` above 550 (integer division as in Calc and RL-TOA; Chest prints the same). Denominators: RL 0 350,000; 150 245,000; 300 140,000; 400 70,000; 450 58,800; 500 46,900; 550 and above 35,000. `p = C / (100 x D_pet)` (no cap in Calc; the largest `C` of 59,000 at 35,000 gives 1.69%). Examples: level 300 with 20,000 points: 20,000 / 140,000 = 0.143%, 1/700 (post). Messages as for any pet: red `You have a funny feeling like you're being followed.`, with a follower already out the pet goes to the inventory, message with a follower out `You feel something weird sneaking into your backpack.` (red), with both full it is not received and must be claimed from Probita in East Ardougne. Possible in Entry mode at the 350,000 rate. | Chest; U-Drop; Calc; Pet; RL-TOA |

## Challenge rewards

| Id | Rule | Source |
|---|---|---|
| TR22 | **Common conditions.** Always given when met; every challenge requires **zero deaths for all party members**. The game checks the player's inventory, worn equipment **and bank** for the base item or its modified form; if present the roll is cancelled, otherwise one is generated (so the drop trick yields several). If the P4 Warden dies on the same tick as you, Combat Achievements count it deathless but loot does not. Several items are marked 'valuable' to stop accidental drops (28 Sep 2022: Masori crafting kit, Ancient remnant, and the Zebak, Kephri, Ba-Ba and Akkha remnants). | Chest; U-GIM |
| TR23 | **Table.** Masori crafting kit (27372): level **350+**, plus "at least 1500 points" per the Kit page. Menaphite ornament kit (27255): **425+**. Cursed phalanx (27248, on the fang): **500+** (a player who owns a phalanx or a fang (or) cannot get another). Remnant of Akkha (27377): **450+**, all Akkha invocations (TI30-TI33) and Akkha beaten at path level 4 or higher. Remnant of Zebak (27380): 450+, all Zebak invocations (TI26-TI29), level 4 or higher. Remnant of Ba-Ba (27378): 450+, TI34-TI38, level 4+. Remnant of Kephri (27379): 450+, TI21-TI25, level 4+. Ancient remnant (27381): 450+, all Wardens invocations (TI39-TI44), no path-level condition. Also the player must not already own the item the remnant is used with (U-Orn: "You have not already obtained the item used to morph your pet already"). **OPEN-TR15:** whether the 1,500-point condition applies to the other kits (assumed: no, Kit page only) and whether "zero deaths" counts a leaver or a disconnected player (assumed: the party as it stood at completion plus anyone who died). | Chest; Kit; MKit; Phalanx; U-Orn |
| TR24 | **One transmog per person per raid**, priority: Akkha, Zebak, Ba-Ba, Kephri, Ancient (Mod Ash 25 Jul 2023 also lists "Tumeken" between Kephri and Ancient; the Ancient remnant now covers both destroyed Wardens). Unlock varbits `TOA_UNLOCKED_PET_MORPH_{AKKHA,BABA,KEPHRI,ZEBAK,DESTROYED_TUMEKEN,DESTROYED_ELIDINIS}` 14498-14503. Pet forms: Akkhito 27382, Babi 27383, Kephriti 27384, Zebo 27385, destroyed guardians 27386 / 27387. | Chest; Pet; cache |

## Chests, sarcophagus and unclaimed loot

| Id | Rule | Source |
|---|---|---|
| TR25 | **Per-player chests.** The reward is computed for every player when the Wardens die; each of up to eight players has an own chest in the burial chamber (key icon shows which); loot is in the chest inventory (`TOA_CHESTS` 811). The designated unique winner opens the sarcophagus instead (TR11). Which chest belongs to which slot is **OPEN-TR17**: the only hint is a commented-out caption on the Chest page ("the numbered tiles indicate which player in the orb list can open the chest") that the wiki marks as unreliable ("something is off"); assumed: orb-list (`TOA_CLIENT_P0..7`) order. Ironmen are eligible when completing alongside others (Main). | Chest; Main |
| TR26 | **Computed once** at completion with the run's seeded RNG and **persisted before any chest can open** (brief 3.12); paying out uses the item-transaction API, all-or-nothing and idempotent per player per run. | brief |
| TR27 | **Interface.** Button order since 20 Aug 2025: Bin on the left, Bank and Inventory on the right ("Discard-all", "Bank-all", "Take-all"); "Bank-all" and "Take-all" move everything, "Discard-all" destroys it; items that are non-stackable commons come as bank notes where the table says noted (TR15); uniques, kits, remnants and jewels are un-noted items. | cache; Chest |
| TR28 | **Order of operations** (summary): (1) team roll `p` from `T` and `RL'` (TR3, TR5); (2) if it succeeds draw the recipient by `C_i / T`, then the item by weights (TR9) and apply the threshold roll (TR10); (3) per player: dung if `C < 1,500` (TR6) else, if no unique, three common rolls (TR12-TR13); (4) tertiaries (TR17-TR21) and the elite clue; (5) challenge rewards (TR22-TR24). | Chest |
| TR29 | **Rewards Niche.** If a player leaves the burial chamber without taking their loot "rewards can be found through the rewards niche in the south-east corner of the lobby room" (TL8). Re-entering a raid without claiming loses it; "the player will be warned of this prior to entering another raid". After claiming, the niche shows the False Door (varbit 14319 back to 0). **OPEN-TR16:** the warning text, whether an unopened sarcophagus unique is also stored, and whether it holds one run's loot only. | Chest; Niche; cache |

## Worked examples (integer rule)

| Case | Result |
|---|---|
| Solo, `C = 20,000`, RL 300 | `D = 4,500`, unique 4.444% (1/22.5); each item at weight/240: Fang 1.296%, Shadow 0.185%; pet 20,000 / 140,000 = 0.143% (1/700) |
| Four players, `C = 20,000` each, RL 400 | `T = 80,000`, `D = 3,700`, team unique 21.62%; each player wins it with 25% of that (5.41%); Fang 40/190, Lightbearer 50/190 |
| Cap | 55% needs `T >= 0.55 x 100 x D`: 203,500 points at RL 400 |
| RL 0, `T = 10,500` | `p = 1%`; Tumeken's shadow 1/24, then 1/50: 1/120,000 (U-Drop) |
| RL 40, `T = 40,000` | all seven uniques are below their thresholds (50 and 150), so every draw succeeds only at 1/50; otherwise an untradeable tertiary |

## Open list

OPEN-TR1 contribution clamp below 5,000; OPEN-TR2 pet from personal versus team points; OPEN-TR3 integer versus continuous `RL'`;
OPEN-TR4 raid-level clamp at 550 for uniques; OPEN-TR5 leaver as recipient; OPEN-TR6 dung threshold meaning and what it
excludes; OPEN-TR7 weight interpolation rounding; OPEN-TR8 where the unique winner's other items go; OPEN-TR9 broadcast texts;
OPEN-TR10 exact versus random common quantity, floor order, printed ranges; OPEN-TR11 Entry-mode 0.75; OPEN-TR12 kill-count
definition for bad luck mitigation; OPEN-TR13 fallback item for a failed gated unique; OPEN-TR14 elite clue cap and perk order;
OPEN-TR15 challenge-reward scope (1,500 points, leavers); OPEN-TR16 Niche warning text and contents; OPEN-TR17 chest-to-slot order. **17 open items** (plus
the raid-wide `OPEN-TA1` to `OPEN-TA36` in [../toa.md](../toa.md)).

## Conflicts between sources

1. Unique chance scaling: the 28 Oct 2022 post (levels 400-550 at 1/3 effect, clamp 550) vs the Chest page and Calc (310 / 430 break points, after the 25 Jun 2025 reduction): TR3, TR4. The Chest page wins (newer).
2. Integer (Calc, RL-TOA) versus continuous (Chest page) `RL'`: TR3, integer wins.
3. Entry-mode commons: x0.75 below 150 (Calc) vs equal to Normal (Chest prose): TR14, Calc wins.
4. Common-quantity ranges printed on the Chest page vs the formula: TR13, formula wins.
5. Warden phase-2 points stop after the third down (post) vs the fourth (RL-TOA): toa.md TA36, post wins.
6. Pet chance personal (Calc per-person) vs team (RL-TOA): TR2.
7. Jewel duplicate rule: "three jewels" (14 Sep 2022 post) vs four jewels (Chest, since the Jewel of Amascut): TR19, the Chest wins.
8. Dung threshold: Total (Chest, Dung, post) vs contribution (TR6, reading B): TR6.
9. Masori crafting kit: needs 1,500 points (Kit) vs the challenge section's only conditions of zero deaths and level (Chest): TR23.

## As built (2026-11-08)

- **TR29:** entering with unclaimed rewards first asks "Unclaimed rewards will be lost if you enter." (Enter the tomb
  anyway / Stay; the wording is OPEN-TR16).
- **TR11:** the winner's first Open plays seq 9505 (223 client cycles, about 8 ticks) on the purple sarcophagus. After
  it, the winner alone sees the opened form 44934 and the loot opens.

