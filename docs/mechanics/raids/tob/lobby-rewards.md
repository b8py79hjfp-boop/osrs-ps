# ToB lobby, board, midway stores, treasure room, chest, scoreboard

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Theatre of Blood" (Main), "Ver Sinhaza", "Ver Sinhaza bank",
"Mysterious Stranger" and "Mysterious Stranger (shop)", "Escape crystal" (Crystal), "Death (shop)", "Abigaila" (Abig),
"Chest (Theatre of Blood)" (Supply), "Message (Theatre of Blood)", "Bandages (Theatre of Blood)" (Bandages), "Monumental
chest" (Chest), "Strategy table", "War table", "Scoreboard (Theatre of Blood)" (Score), "Lil' Zik" (Zik), "Sanguine
dust", "Holy ornament kit", "Sanguine ornament kit", "Sinhaza shroud", "Calculator:Theatre of Blood loot" and its Lua
module "Module:Theatre of Blood calculator" (Calc), "Theatre of Blood/Hard Mode" (Hard), "Theatre of Blood/Entry Mode"
(Entry), the unique item pages (Avernic defender hilt, Ghrazi rapier, Sanguinesti staff, Justiciar faceguard / chestguard
/ legguards, Scythe of Vitur), the update notes (U-Entry23 1 Mar 2023, U-Poll83 26 Mar 2025, U-Uplift 1 Oct 2025, U-Summer
12 Aug 2026, U-QM3Nov 3 Nov 2026, "Summer Sweep-Up Gear & PvM Changes" 22 Jul 2026 = U-Gear); cache 2757 (obj / npc /
loc configs, generated symbols). Raid-wide rules: [../tob.md](../tob.md) (TB22-TB35 are the summary of this doc).
Rooms: [maiden.md](maiden.md), [bloat.md](bloat.md), [nylocas.md](nylocas.md), [sotetseg.md](sotetseg.md),
[xarpus.md](xarpus.md), [verzik.md](verzik.md). Open items here are `OPEN-LR<n>`.

## Lobby (Ver Sinhaza) and treasure vault: places and ids

| Id | Thing | Value | Source |
|---|---|---|---|
| LR1 | Raid entrance, notice board, bank, bank barrier, gravestone chest (reclaim chest), blood well (Vyre well), castle doors | scenery `TOB_SURFACE_RAID_ENTRANCE` 32653, `TOB_SURFACE_NOTICE_BOARD` 32655, `TOB_SURFACE_BANKBOOTH` 32666, `TOB_SURFACE_GRAVESTONE_CHEST` 32656, `TOB_SURFACE_BLOOD_WELL` 32654; varbit `TOB_LOBBY_WELL_CONTENTS` 6455 (the scythe's Vyre well charges, 6 Dec 2018) | cache; Scythe page |
| LR2 | Rewards chest "just opposite the bank booths" for forgotten loot | `TOB_REWARDS_CHEST_LOBBY_OPEN` 41435, `_CLOSED` 41436, `_MULTI` 41437, and no-op variants 41743-41745 | cache; Main (7 Apr 2021) |
| LR3 | Scoreboard in the eastern hall (3676, 3223), "Read" | scenery 32987, interface `TOB_SCOREBOARD` 363, tab varbit `TOB_SCOREBOARD_TAB` 12270 | Score; cache |
| LR4 | Mysterious Stranger at the entrance (3673, 3222) | NPC ids (Sarei) 8208, 10875, 10876, 10877, 11162; since The Blood Moon Rises (30 Jun 2026) the replacement 16269, 16270 serves the same shop; options "Talk-to", "Trade" (and "Change" for capes); the cache also has an NPC `TOB_STRANGER` 8325 | MS; cache |
| LR5 | Abigaila, spectator NPC: Lumbridge General Store upstairs (3210, 3249, 1) and by the Grand Exchange spirit tree (3185, 3516) | NPC 8368 (`TOB_SPECTATOR_MISTHALIN`), options "Talk-to", "Scry" | Abig; cache |
| LR6 | Vault (treasure room): monumental chests at (3226-3240, 4323-4327), strategy table (3228, 4318), war table (3232, 4325); room region 12867 | scenery: chest closed teammate's 32990, teammate's unique 32991, player's 32992, player's unique 32993, opened regular 32994, opened unique 41746; strategy table 33014; war table 33011; `TOB_LOOTTRACKER_DUMMY_LOC` 47338 | Chest; tables; cache |
| LR7 | Region ids (derived from the wiki map pins as `(x >> 6) << 8 \| (y >> 6)`): lobby 14642, vault 12867, supply chest room 13125, Nylocas room 13122, Verzik room 12611, Xarpus room 12612. Maiden and Sotetseg pins are not on the pages read (**OPEN-LR1**). The brief's RuneLite ids (ToB 12867, lobby 14642) agree. | derived | brief 2; pages |

## Notice board and parties

- **LR8** The board forms parties (TB1): interfaces `TOB_PARTYLIST` 364 and `TOB_PARTYDETAILS` 50, HUD `TOB_HUD` 28, info
  board `TOB_INFOBOARD` 459. Varbits `TOB_CLIENT_PARTYSTATUS` 6440, `TOB_CLIENT_PARTYSLOT` 6441, `TOB_CLIENT_P0..P4`
  6442-6446 (party member state), varcs `TOB_CLIENT_NAME0..4` 330-334. Encodings: from the client scripts in the R4 client
  contract, not guessed. A mode prompt appears on first use (1 Mar 2023). The leader may re-order (9 Sep 2021). If party
  members are still in the previous room's arena when a wave is started, the warning screen says so (12 Mar 2020; text
  **OPEN-LR2**). The wiki has no page for the board (its "Notice board" title is the Sailing object): everything else
  about application / acceptance flow is **OPEN-LR3**.

## Midway supply chests (Normal and Hard)

- **LR9 Stock** (all stock unlimited, prices in points): Stamina potion(4) **1** (only **one per chest** since 3 Jun 2021),
  Prayer potion(4) **2**, Saradomin brew(4) **3**, Super restore(4) **3**, Mushroom potato **1**, Shark **1**, Sea turtle
  **2**, Manta ray **2**. Not for sale: a **Message** (obj 22475) whose text depends on performance (always present), an
  **Onion** ("given in place of all other rewards" when the player died at both Maiden and Bloat, or at both Nylocas and
  Sotetseg; the Strat page says Maiden and Bloat only, and that taking the onion keeps the player eligible for points at
  the next chest if they do not die in the next two fights; **OPEN-LR4** which two-room pairs apply), and Bandages in Entry
  (LR12). Scenery: closed 32758 ("Open"), opened 32759 ("Search"); chest at (3269, 4449). The shop interface is
  `TOB_MIDWAY_STORES` 405; varps `TOB_MIDWAYCHEST_POINTS` 6460, `TOB_MIDWAYCHEST_BOOBYPRIZE` 6461 (onion), `_STAMINAPOT` 12271,
  `_LAST_X` 12272 (the "previous quantities" memory added 3 Jun 2021).
- **LR10 Points:** each player gets points by their own performance: **6 to 13** per chest (Supply); Strat: above average
  **10-13**, average **8-11**, below average **6-9**; unspent points **carry over** to the second chest. Hard page: "assuming no
  deaths before or during Sotetseg the player will usually have an average of **15** points, though the highest is **18**"
  (probably both chests together, not stated). The formula (deaths, damage, MVP) is not published: **OPEN-LR5**.
- **LR11 Items spawned from a chest** appear to other players instantly if dropped (14 Jun 2018).
- **LR12 Entry mode:** the chest holds **10 Bandages** always, in place of all other supplies; leftover Bandages do not carry
  over; at most 10 are in the chest, uncollected ones are deleted (Bandages, Supply, Entry). Entry also issues Bandages on
  wipes and after deaths (TB16).
- **LR13 Bandages** (obj 25730, untradeable, unbankable, options "Heal" and "Destroy"; destroy text: "The vampyres might send
  you more if they like your performance."): heals **20** HP, restores a variable amount of Prayer (scaling with Prayer
  level, not documented, **OPEN-LR6**) and **20% run energy**, cures poison, gives the stamina potion effect, boosts combat
  stats, delays the next attack like food and can be combo-eaten with potions and karambwans. Boosts: Attack / Strength /
  Defence `floor(level x 15 / 100) + 4` (one lower than a super potion); Ranged `floor(level / 10) + 4`; Magic **static +4**
  (Bandages page) - the Entry page says the Magic boost has the ranging potion's scaling, but the 9 Jun 2021 hotfix reduced the
  Magic boost, so the static value is used (**OPEN-LR7**). Item sprite and name changes 16 Feb 2022 (cosmetic).

## Treasure vault and the monumental chest

- **LR14** After Verzik dies the throne opens onto the vault (TB25): up to five chests, one per player, with an arrow over the
  player's own (chest varbits `TOB_TREASUREROOM_CHEST_0..4` 6450-6454); a unique shows a purple aura and an arrow design;
  options "Open" then "Search". The interface is `TOB_CHESTS` 23 over inventory `TOB_CHESTS` 612 (the Loot Tracker reads it;
  `TOB_SHOULD_HAVE_LOOT` 11958 marks that unclaimed loot exists). Grand bookshelves hold every lore book (the bosses also drop them, unread
  ones only, 26 Mar 2025). The strategy table (participants) and war table (spectators) show the performance panel
  (LR22). Music "The Curtain Closes" on entry.
- **LR15 Unclaimed loot** is claimable at the lobby rewards chest (LR2); the Chest page adds "if they are not claimed
  otherwise, they will be lost upon logging out" (TB31, **OPEN-LR8**).

## Reward model (**OPEN-TB12**: the in-game formula is not published; this is the wiki calculator's model, Calc)

- **LR16 Points.** Per player: `points = max(0, 3 x (6 - roomsSkipped) + mvp - 4 x deaths)`, so at most 18 from rooms and 32 with
  all 14 MVP points. Maximum team score `max = 18 x N + 14` (N players). Team score `T = max(0, 3 x (6 N - roomsSkippedAll) + 14 - 4 x
  deathsAll)`; for a single player in the free-for-all view `T = max(points, that sum)`. Ratio `r = T / max`.
- **LR17 Unique roll.** Team chance `P = r / 9.1` (Normal) or `r / 7.7` (Hard); none in Entry. The unique goes to one player: the
  chance for player i is `P x points_i / T`. Deathless 4-player Normal: `max = 86`, `T = 86`, `r = 1`, `P = 1/9.1` (11.0%);
  Hard `P = 1/7.7` (13.0%). Zero individual points => the player gets a **cabbage** and a **Message** instead of any roll.
- **LR18 MVP points** (14 per raid): top damage at Maiden 2, Bloat 2, Nylocas (Vasilias) 1, Sotetseg 1, Xarpus 2, Verzik P1 2,
  P2 2, P3 2. Damage on Matomenos counts at Maiden and Verzik. Ties: **OPEN-LR9**.
- **LR19 Unique item** is chosen by weight (Chest, Calc, Hard):

| Item | Normal (/19) | Hard (/18) |
|---|---|---|
| Avernic defender hilt | 8 | 7 |
| Ghrazi rapier | 2 | 2 |
| Sanguinesti staff (uncharged) | 2 | 2 |
| Justiciar faceguard | 2 | 2 |
| Justiciar chestguard | 2 | 2 |
| Justiciar legguards | 2 | 2 |
| Scythe of Vitur (uncharged) | 1 | 1 |

- **LR20 Common table** (rolled three times, uniform over 30 weight slots; a unique replaces all of them): vial of blood
  weight 2, 28 other items weight 1 each. Quantity for a roll: `lo = floor(min x q x r)`, `hi = floor(lo x 1.2)`, uniform in
  `[lo, hi]` (distribution **OPEN-LR10**; the range form matches the Chest page when r = 1 and q = 1). `q` is **1** Normal,
  **1.15** Hard, **1.30** Hard inside the target time (TB29), **0.20** Entry (from the pages; the module omits Entry). `min` and
  the printed full-score ranges (the Chest page marks vial of blood, coal, ores, glass, wine, herbs, battlestaff and the
  rune armour as noted): vial of blood 50 (printed **45**-60; **conflict**, 50 matches the x1.2 rule), death rune 500-600 (not noted), blood rune 500-600, swamp tar 500-600, coal 500-600, gold ore 300-360, molten
  glass 200-240, adamantite ore 130-156, runite ore 60-72, wine of Zamorak 50-60, potato cactus 50-60, grimy cadantine 50-60,
  avantoe 40-48, toadflax 37-44, kwuarm 36-43, irit leaf 34-40, ranarr weed 30-36, snapdragon 27-32, lantadyme 26-31, dwarf
  weed 24-28, torstol 20-24, battlestaff 15-18, rune battleaxe 4, rune platebody 4, rune chainbody 4, palm tree seed 3, yew
  seed 3, magic seed 3, mahogany seed 10-12. "All non-stackable items obtained are bank notes, unless it is a unique."
  Death rune, blood rune, swamp tar and the seeds carry no "(noted)" on the page, i.e. they are stackable or single items.
- **LR21 Tertiaries and pet** (per player, scaled by individual performance, curve **OPEN-TB13**): elite clue scroll
  1/25 Entry, 3/25 Normal, 3.5/25 Hard; Lil' Zik 1/650 Normal, 1/500 Hard, falling to 1/6,500 and 1/5,000; maximum rate with
  **10** individual points (maximum 32, or 18 without MVP; so about two deaths are tolerable, more if the player has MVP
  rooms); no pet in Entry. Hard only: Sanguine dust 1/275, Sanguine ornament kit 1/150, Holy ornament kit 1/100 (time
  requirement **OPEN-TB14**). The pet follows the player ("You have a funny feeling like you're being followed." / "You feel
  something weird sneaking into your backpack." when a follower is out; if both inventory and follower slot are occupied
  the pet is claimed from Probita). Lil' Zik metamorphoses into Lil' Maiden / Bloat / Nylo / Sot / Xarp with Sanguine dust.
  Pet objs: Lil' Zik 22473 (`VERZIKPET`), others 25748-25752; follower NPCs 8336, 8337 (Zik), 10761-10765 and 10870-10874.
  Kits: Holy 25742, Sanguine 25744; dust 25746. Messages quoted: the two pet strings above (red text).

## Records, scoreboard, tables

- **LR22 Performance panel** (strategy table / war table): deaths per player, total deaths, time per room, MVP at the top,
  challenge time and overall time at the bottom (Main, tables). Challenge time counts only boss fights; overall time runs from
  entering to the final blow on Verzik (Score); the raid timer starts at the Maiden fight (23 Jun 2021).
- **LR23 Scoreboard** (Score): personal and global attempts, completions, deaths and best times per mode, per party size (1-5)
  for challenge and overall time. Global values as of 1 Nov 2026 (data, not rules; no global service here): attempts / completions
  / deaths Entry 2,366,753 / 833,119 / 5,521,382, Normal 21,299,875 / 12,212,813 / 47,875,396, Hard 2,980,369 / 1,557,557 /
  10,561,822. Best times (challenge / overall): Normal 1 player 35:14.40 / 39:02.40, 2 players 18:01.80 / 20:52.80, 3 players
  13:12.00 / 15:51.60, 4 players 11:48.00 / 13:49.20, 5 players 11:19.20 / 13:06.00; Hard 40:34.80 / 44:54.60, 22:21.60 /
  25:03.00, 15:26.40 / 17:31.20, 13:48.00 / 16:24.60, 12:36.60 / 15:17.40; Entry 9:28.80 / 12:06.00, 8:59.40 / 11:07.80, 9:09.60 /
  11:16.20, 9:09.00 / 10:41.40, 8:25.80 / 10:47.40. The interface is a stats view (as TFA's `TFA_STATS`): the per-world counters are
  **OPEN-LR11**.
- **LR24 Kill counts, shrouds, messages:** TB32-TB34. Collection log: 17 items (Main).
- **LR25 Books:** each boss drops its lore book ("Serafina's Diary", "The Butcher", "Arachnids of Vampyrium", "The Shadow Realm",
  "The Wild Hunt", "Verzik Vitur - Patient Record") unless already read (26 Mar 2025); objs 22504, 22506, 22508, 22510, 22512,
  22514; copies on the vault bookshelves.
- **LR26 Shrouds:** Sinhaza shroud tiers 1-5 at 100 / 500 / 1,000 / 1,500 / 2,000 completions, from the Mysterious Stranger
  ("Change" picks the worn tier among the unlocked ones).
- **LR27 Death's Office:** since 3 Nov 2026 Death also sells escape crystals ("Shop" option, stock 9,500). Rick sells them too
  (price TB19).

## Unique items (item data is outside R4's room work; recorded because the chest pays them)

| Item | Obj id(s) | Value | Notes up to 2026-11-03 |
|---|---|---|---|
| Avernic defender hilt | 22477 | 2,500,000 | tradeable, noted |
| Ghrazi rapier | 22324 | 5,000,000 | 80 Attack (11 May 2022); 22 Jul 2026: stab +94 -> +100, strength +89 -> +93 |
| Sanguinesti staff | 22481 uncharged, 22323 charged | 5,000,000 | 82 Magic (11 May 2022); 22 Jul 2026: 2 blood runes per charge (refund), leech chance 1/6 -> 1/5, +8 on leech, base max hit +1; 3 Nov 2026: leech animation no longer extends the target hitbox |
| Justiciar faceguard | 22326 | 2,000,000 | |
| Justiciar chestguard | 22327 | 6,000,000 | 17 Apr 2024: interferes with Ava's devices |
| Justiciar legguards | 22328 | 4,500,000 | |
| Scythe of Vitur | 22486 uncharged, 22325 charged | 4,000,000 | 80 Attack and 90 Strength (11 May 2022); 17 Jan 2024: slash accuracy +110 -> +125, 1 vial + 200 blood runes per 100 charges, charges used only on a damaging hit; 19 Aug 2026 rename only |

Item stats, equipment effects and charging are item content for another module; this doc only needs the ids to pay rewards.

## Open list

OPEN-LR1 Maiden and Sotetseg region ids; OPEN-LR2 wave-start warning text; OPEN-LR3 board flow (apply / accept / kick,
size per mode); OPEN-LR4 onion room pairs; OPEN-LR5 midway points formula; OPEN-LR6 Bandages Prayer restore; OPEN-LR7
Bandages Magic boost (Entry page vs hotfix); OPEN-LR8 unclaimed loot expiry (TB31); OPEN-LR9 MVP ties; OPEN-LR10 common
quantity distribution and the noted status of the stackable items; OPEN-LR11 scoreboard per-world counters and personal
panel layout; OPEN-LR12 the Message item texts (random, performance-dependent, not on the wiki); OPEN-LR13 how the Hard 30%
bonus and the rounding combine with deaths (Calc applies `floor` after multiplying q x r, which is the model used);
OPEN-LR14 which Mysterious Stranger NPC variant is shown (the pre-quest ids or the post-quest replacement) at the anchor.
Plus the raid-wide items in [../tob.md](../tob.md). **14 open items.**

## Conflicts between sources

1. Vial of blood minimum: 45 (Chest) vs 50 (Calc module).
2. Midway points: 6-13 per chest (Supply) vs tiers 10-13 / 8-11 / 6-9 (Strat) vs "15 average, 18 highest" (Hard).
3. Onion rule: Maiden + Bloat (Strat) vs Maiden + Bloat or Nylocas + Sotetseg (Supply).
4. Bandages Magic boost: ranging scaling (Entry) vs static +4 (Bandages).
5. Hard-exclusive tertiaries: need the target time (Hard, Zik) vs a plain Hard drop (Main, Chest).
6. Unclaimed loot: lobby chest (Main changelog) vs "lost upon logging out" (Chest).
7. Escape crystal price: TB19.
