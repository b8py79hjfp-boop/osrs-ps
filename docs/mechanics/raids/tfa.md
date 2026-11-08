# The Fractured Archive (TFA): fact sheet

Date anchor: as live on **2026-11-03** (cache 2757, captured 10:00 UTC that day). Sources read **2026-11-08**: OSRS Wiki
pages (API wikitext) "The Fractured Archive" (Main), "Chest (The Fractured Archive)" (Chest), "The Fractured
Archive/Strategies" (Strat, tagged under construction), "The Fractured Archive (miniquest)", "Quartermaster (The
Fractured Archive)", "Archivist's folio", "Orion", "Elemental fragments", "Living record (tier 1)", "Archive key",
"Alliance records", "Raids", the unique items' pages, and the updates "The Fractured Archive - Out Today" (20 Oct,
OutToday), "Halloween Event 2026, TFA Tweaks & More!" (28 Oct, Halloween), "Quest Month Kick Off, TFA Tweaks & More"
(3 Nov, QM3Nov), "The Fractured Archive - Everything You Need To Know" (6 Oct preview, Everything; its "on launch"
statements are used where no later source contradicts them). Client side: [tfa/client-contract.md](tfa/client-contract.md). Bosses: `tfa/<boss>.md`.

**Which build the cache matches (TFA-D1).** The 3 November update added clickboxes under the Archive Guardian and
Ocellus phase 3. In cache 2757 NPCs 16659/16660 and 16677 carry extra tiny models 51447/51448 that no other NPC uses
(the debug copy 16682 lacks them), last changed in the 2026-11-02 16:10 UTC build, while every other TFA model dates
from the launch build. So the cache **includes the 3 November update**, and these docs implement the post-3 November
state (DECISIONS D-079).

**Server data is not in the cache (TFA-D2).** TFA dbtables 136-141 and 259-263 (drop tables, door sections, room
entry configs, Ezekiel finale patterns, Ocellus barrages, supply shop) have rows but no column values: they are
server-only. Only their row names survive (gamevals), giving each table's item set. The stats tables 264/265 do carry
data. Numbers below therefore come from the wiki; anything it does not give is `OPEN`.

## Access

| Id | Rule | Source |
|---|---|---|
| TFA1 | Needs **While Guthix Sleeps**. The miniquest "The Fractured Archive" starts on the first attempt to enter the lobby. **OPEN-TFA1:** where it starts (Main: on entering the lobby; miniquest page: near the Ancient Guthixian Temple entrance; OutToday: a messenger on login after the hotfix gives a note). **Implemented (TfaMiniquest.kt):** it starts at the lobby opening, a stationary messenger at the pinned tile hands out the optional note; steps and OPEN-TFA19 / 20 / 23 / 25 in [tfa/lobby-nexus-vault.md](tfa/lobby-nexus-vault.md). | Main; miniquest page; OutToday |
| TFA2 | Reached from the Tears of Guthix cave with a lit sapphire lantern (a light creature carries the player), then crawling through the opening to the south-west; also by running from the Lumbridge Swamp Caves with a sapphire lantern (Strat); a Guthixian temple teleport lands nearby; the charged archivist's folio teleports to the lobby, just south-west of the banker. Cache: the only outside entrance loc is at (4050, 4567, 0), "Blocked cavity" until varbit `WGS_TFA_SHORTCUT_UNLOCKED` = 1, then "Opening" (Crawl-through). | Main; Strat; client-contract §5 |
| TFA3 | Group Ironmen lose prestige entering with a non-member; HCIM lose status and HCGIM a life on death inside. UIMs with items at Pete Kayer cannot enter; world 345 (permanent Deadman) had no access at release; limited accounts cannot enter (Everything). Ironmen are reward-eligible when completing alongside others. Since 28 Oct items stored at Pete Kayer are returned automatically. | Main; OutToday; Everything; Halloween |

## Party and structure

| Id | Rule | Source |
|---|---|---|
| TFA4 | 1-8 players, formed at the **Grouping Board** on the east side of the lobby (4138, 5547, 0); the interface toggles spectators on or off. Filters on the board: **OPEN-TFA2** (client scripts show a friends filter varbit `TFA_LOBBY_FRIENDS_FILTER`). | Main; OutToday; client-contract §2.4 |
| TFA5 | Since 28 Oct a party survives a wipe, and a member who logs out or disconnects and logs back in stays in it. | Halloween |
| TFA6 | Five of eight bosses per raid: (1) Archive Guardian; (2) Ezekiel **or** Bork and the Smorks; (3) Moia **or** Dreadnoth the Jellied; (4) the Dagon'hai (Elder + Monks) **or** Xarth; (5) Ocellus. After each boss the party returns to the selection room (the nexus) and picks the left or right door; the middle three rows always offer the same pair. | Main; Everything |
| TFA7 | After the third boss the **Quartermaster** (NPCs 16761/16762) arrives in the nexus and runs the supply shop (TFA16). | Quartermaster page; Strat |
| TFA8 | Map (cache): lobby square (64, 86); nexus + Archive Guardian (64, 87, four level copies); Ezekiel (63, 86); Dagon'hai (63, 87); Moia (63, 88); Xarth (65, 86); Bork (65, 87); Dreadnoth (65, 88); Ocellus (64, 88; arena on level 1); Void vault (64, 90). | client-contract §5 |

## Scaling and drains

| Id | Rule | Source |
|---|---|---|
| TFA9 | Boss HP scales with the players present; a solo raid uses two-player scaling. Per-boss HP tables are on each boss page (tfa/*.md). Strat's "+43.63% per player after the second" fits the tables only roughly; the tables win. | Main; Raids; boss pages |
| TFA10 | Defence drain is capped at 60% of normal (floor = 40% drained) for the Archive Guardian, Bork, Ezekiel, Moia, Xarth and Ocellus (P1, P3); no cap for Dreadnoth, the Smorks and the Blade (Ocellus P2). The Dagon'hai trio are meant to be uncapped (OutToday). **OPEN-TFA14:** the Elder page says they have a 60% cap in error with a fix "expected on 11 November", citing a 5 November comment; no source dated by 3 Nov confirms the bug, so the flag `tfa.dagonhai.defenceCap` (default: the bug, 60%) decides. | 21 Oct hotfix (Main, boss pages); OutToday; Dagon'hai Elder page |
| TFA11 | Since 28 Oct offensive stat reduction is capped at 50% for Ocellus, Xarth and Dreadnoth. Since the 21 Oct hotfix Ocellus no longer restores Defence when moving quadrant in phase 3. | Halloween; Ocellus page; OutToday |

## Contribution

| Id | Rule | Source |
|---|---|---|
| TFA12 | Each room is worth **100 contribution**, split among the players by damage dealt; Ocellus is worth **300** across his phases (most in P3): **700** per raid. Damage on minions counts, with soft caps (**OPEN-TFA3**: the caps). | QM3Nov; Chest |
| TFA13 | A death costs **10% of the player's expected contribution** (700 / players): 35 in a duo, 8.75 in an eight (the wiki says "8 or 9"; **OPEN-TFA4**: rounding). Since 28 Oct team deaths cut the group's unique chance harder (**OPEN-TFA5**: the formula is not public; model in the reward doc). | QM3Nov; Chest; Halloween |

## Death, wipe, disconnect

| Id | Rule | Source |
|---|---|---|
| TFA14 | Every death is **dangerous**: HCIM lose status, HCGIM lose a life, the Item Retrieval Service's contents are lost. While teammates live, a dead player waits (spectates) until the room ends (**OPEN-TFA6**: the wiki only describes the wipe). On a **team wipe** the party is ejected to the lobby and unprotected items are in gravestones outside the entrance; reclaiming costs up to 500,000 coins, 75% less until the player's fifth completion. | Main; OutToday; Everything |
| TFA15 | Since 22 Oct food and potions are not lost on disconnect (inv `TFA_SUPPLIES_SNAPSHOT_INV`). Since 28 Oct a player who logs out in the loot room can still loot their chest. On 3 Nov a known issue said "Players currently can't loot from or leave the TFA room" (investigating); we implement the intended behaviour. | Main changelog; OutToday; Halloween; QM3Nov |
| TFA15a | Since 3 Nov the player's Prayer is turned off at the end of each room. | QM3Nov |

## Supplies

| Id | Rule | Source |
|---|---|---|
| TFA16 | The Quartermaster's "Alliance Supplies" shop sells for supply credits earned in the raid: Saradomin brew(4) 6 stock, Super restore(4) 4, Prayer potion(4) 4, Manta ray 10, Halibut 5, Giant krill 10, Prayer potion(2) 1, Ranging potion(2) 1, Super combat potion(2) 1, Leftovers (only with poor contribution), Alliance log. Prices as printed: brew 3, restore 3, prayer(4) 2, manta 2, halibut 2, krill 1, prayer(2) 1, ranging(2) 2, combat(2) 2. Cache row names match this list (`TFA_MIDRAID_SUPPLIES_SHOP_*`). **OPEN-TFA7:** how credits are earned ("your performance up until then does play a role") and whether the printed column is the credit price. Supplies cannot be banked during the raid. **Implemented (TfaSupplies.kt):** op1 "Resupply" opens the cache omnishop (client-contract 1.6); stock counts above, one stock per run shared by the party (**OPEN-TFA7b**, `-Dtp.tfa.supplyStockPerPlayer=true` gives each player their own); credits are granted once, when the third room is cleared (**OPEN-TFA7a**, invented placeholder: `round(share x 24)` capped at 127, where share = the member's points / an equal split of three rooms (3 x 100 / party size), capped at 1.5; `-Dtp.tfa.supplyCreditsBase`); a member under 0.5 of an equal split gets the failure shop 17151 with the free Leftovers, 1 each (**OPEN-TFA7c**, `-Dtp.tfa.supplyPoorShare`). Credits are cleared when a member enters or leaves. Quartermaster dialogue is the shop's own intro line (**OPEN-NX8**). | Quartermaster page; Everything; gamevals |

## Spectating

| Id | Rule | Source |
|---|---|---|
| TFA17 | Spectate through the **Strategy Board** on the west side of the lobby; up to 100 spectators per raid: "That raid has reached the maximum number of spectators." Spectators click party orbs for that player's view. The party can disallow spectators on the Grouping interface. | Main; OutToday; client-contract §2.1 |

## Rewards

| Id | Rule | Source |
|---|---|---|
| TFA18 | One unique roll per raid: **1/14** for 1-2 players; +4% of the base per player beyond two (table: 3 → 1/13.46, 4 → 1/12.96, 5 → 1/12.50, 6 → 1/12.07, 7 → 1/11.67, 8 → 1/11.29). Chest calls it "multiplicative", QM3Nov "additive"; the table fits 4% of the base per player. | Chest; QM3Nov |
| TFA19 | Recipient: contribution against the team total (deathless duo 490 / 210 → 70% / 30%). | Chest |
| TFA20 | Weights out of 15: Rondache 5, Zorya's tome (uncharged) 4, Horn of Bran 3, Ascension crossbows 2, Obligator (uncharged) 1. The cache's unique table rows are Obligator, Ascension crossbows, **Zeal**, Zorya's tome, Rondache. "Zeal" is the row of the Horn of Bran (the horn unlocks the Zeal prayer; no other Zeal item exists), so both sources agree. | QM3Nov; Chest; gamevals; Horn of Bran page |
| TFA21 | Without a unique the standard table is rolled **three times**; each roll has a 1/51 chance of 5× quantity. Rates per roll (/330): 11 each — battlestaff 34-37, dragon platelegs 1, dragon plateskirt 1, rune kiteshield 5-7, nature 301-385, astral 301-385, blood 301-385, soul 301-380, earth rune 3009-3662, sapphire 24-27, emerald 24-26, ruby 23-27, diamond 24-26, onyx bolt tips 50-58, earth orb 68-75; 7 each — grimy harralander 60-66, grimy ranarr 18-20, grimy toadflax 24-28, snapdragon seed 3-4, kwuarm seed 7-8, snape grass seed 7-8, yew seed 4-5, wine of Zamorak 32-36; 6 each — iron ore 851-915, adamantite ore 60-72, runite ore 25-26, desiccated page 30-36, aether catalyst 180-191, pure essence 1404-1632, mahogany logs 140-152, dragon arrowtips 350-369, raw shark 80-92; 55 — ascension shards 75-82. Cache row names list the same 33 items. **OPEN-TFA8:** whether quantity scales with contribution (cache column `COL_SCALING_MODE` exists, empty). | Chest; gamevals |
| TFA22 | Tertiaries: archivist's folio 1/15 if none owned; elemental fragments 1/20 with duplicate protection until all four are owned (varbits `TFA_MOTE_*_UNLOCKED`); elite clue 1/10 (better with the elite Combat Achievement tier: 1/9 per the brief, **OPEN-TFA9**); Orion 1/750 (**OPEN-TFA10**: QM3Nov "not dependent on anything" as long as no cabbage). | Chest; QM3Nov; Orion page |
| TFA23 | Poor performance gives only a **cabbage**; each death raises the chance; a cabbage blocks the other tertiaries. **OPEN-TFA11:** the cabbage rate. Common drops do not trigger valuable-drop broadcasts (fixed 28 Oct, except cabbages). | Chest; Halloween |
| TFA24 | **Void reward room**: after Ocellus, the Blade teleports the party to an island with eight chests, one per player; the player's own chest sparkles (vault chest NPCs 16736-16751 morph on varbit `TFA_VAULT_OWN_CHEST` = 1-based own index). Since 3 Nov chests appear only after the braziers are lit. With a unique the two braziers burn purple and a special chest appears on the northern platform. Opening a standard chest shatters it into a rift (the unique chest opens and shows its contents above it); the loot interface (`TFA_CHESTS`) offers bank, take and destroy; other players' rifts have no search option (3 Nov). Unclaimed loot waits in the lobby's loot wheelbarrow (`TFA_HAS_CLAIMABLE_LOOT`). | Chest; QM3Nov; client-contract §2.6 |

## Records

| Id | Rule | Source |
|---|---|---|
| TFA25 | KC in `TOTAL_COMPLETED_THEFRACTUREDARCHIVE`: `Your completed Fractured Archive count is: <col=ff0000>1</col>.` Time: `The Fractured Archive: Total completion time: <col=ef1020>2:25.80</col> (new personal best)` or `... <col=ef1020>2:54.60</col>. Personal best: 0:00.60`. **OPEN-TFA12:** these strings are from the brief; not yet seen in game or in RuneLite's captured fixtures. | brief §1.5 |
| TFA26 | Living record capes at 100, 500, 1,000, 1,500 and 2,000 completions (`TFA_CAPE_T1..5`), claimed in the records room behind the archive key door (the key drops from the first Archive Guardian kill after 100 completions; "You can gain another by defeating the Archive Guardian" — **OPEN-TFA13**: whether only when lost). **Implemented (TfaRecords.kt):** the key drops, guaranteed, on every Guardian kill to each member present with 100+ completions, no key and a locked room (so a lost key returns); the strange door, records room and claim locs are RR1-RR6 in [tfa/lobby-nexus-vault.md](tfa/lobby-nexus-vault.md) (OPEN-TFA28 / 31). | Main; Living record (tier 1); Archive key |
| TFA27 | Stats interface `TFA_STATS` (979): categories Overall + eight bosses (Ocellus hidden until `TFA_OCELLUS_MET`), rows attempts, completions, completions (practice), deaths, wipes, failures, time (one row per party size), from dbtables 264/265. Global and world stats reset daily. **Implemented (TfaStats.kt):** the lobby scoreboard (loc 62773, "Read") opens it; the Record Keeper (NPC 16692, TfaLobbyNpcs.kt) has "Talk-to" whose "View Stats?" opens it too. Personal counters are player saves `tfa.stats.<category>.<row>[.<party size>]` (category `overall` or the room id, row `attempts|completions|practice|deaths|wipes|failures|time`; times are the best ticks). Events: first entry into a run = Overall attempt (practice runs count too), room started = boss attempt, room cleared = boss completion (practice mode: the practice row) and boss time for the party size, death = Overall and room death, team wipe = Overall wipe, leaving a live run = Overall failure, completed = Overall completion and time (practice raids have no Overall row). **OPEN-TFA14:** there is no global service, so Global is this world's all-time value, Global Today and World Today are this world's UTC-day value; in-memory only, lost on restart; the semantics of "failures" and the party-size time rows with no record (sent as 0) are not sourced. Party panels (10131 and friends) are not sent. | client-contract §2.5; Alliance records |

## Loot implementation notes (TfaLoot.kt)

- **OPEN-TFA33:** when every member has 0 contribution the unique (if rolled) goes to one of them with equal odds (no
  source covers it; before, the last member always got it).

- **TFA21 (standard table)**: `TfaStandardTable` holds the 33 rows, weights asserted to sum to 330 at class init
  (15 x 11 + 8 x 7 + 9 x 6 + 55). Per roll: weight pick `next(330)`, quantity `min + next(max - min + 1)`, bonus
  `next(51) == 0` multiplies by 5; equal items from the three rolls are merged. Objs (by cache name): battlestaff 1391,
  dragon platelegs 4087, dragon plateskirt 4585, rune kiteshield 1201, nature 561, astral 9075, blood 565, soul 566,
  earth 557, grimy harralander 205, ranarr 207, toadflax 3049, snapdragon seed 5300, kwuarm seed 5299, snape grass seed
  22879, yew seed 5315, sapphire 1607, emerald 1605, ruby 1603, diamond 1601 (cut gems, the plain names), iron ore 440,
  adamantite 449, runite 451, ascension shards 34669, onyx bolt tips 9194, earth orb 575, wine of Zamorak 245, desiccated
  page 30640, aether catalyst 30771 (gameval `COSMIC_SOUL_CATALYST`; the Wiki infobox id), pure essence 7936 (gameval
  `BLANKRUNE_HIGH`), mahogany logs 6332, dragon arrowtips 11237 (gameval `DRAGON_ARROWHEADS`), raw shark 383.
- **OPEN-TFA17**: the Wiki table lists no "(noted)". Unstackable items whose quantity is above one are delivered **noted**
  (battlestaff 1392, platelegs 4088, plateskirt 4586, kiteshield 1202, herbs 206 / 208 / 3050, gems 1602-1608, ores
  441 / 450 / 452, earth orb 576, wine 246, shark 384, pure essence 7937, mahogany 8836): 900 loose ores cannot fit the
  six-slot claim inventory.
- **TFA22 / TFA23 (tertiaries)**, `TfaTertiaries`: folio 1/15 (inert folio 34722), fragment 1/20 then one of the four
  (34671 earth, 34673 water, 34675 air, 34677 fire), elite clue 1/10 (12073), Orion 1/750 (34646, gameval
  `OCELLUSPET`). Rolled for every player who did not get the cabbage. **OPEN-TFA16:** ownership checks need the player; rolled
  without duplicate protection (the folio can repeat, fragments never stop dropping). OPEN-TFA9 stays: the elite
  Combat Achievement 1/9 rate is not applied. There is no clue-scroll system on the server yet, so the elite scroll is
  an inert item.
- **OPEN-TFA18**: the Wiki lists the tertiaries under the *Standard* drop version only; they are rolled for unique
  recipients as well (the Orion text "not dependent on anything").
- **Cabbage (OPEN-TFA11)**: a player without a unique rolls the cabbage first with `min(1, deaths x 10%)` (constant
  `CABBAGE_CHANCE_PER_DEATH`, chosen to mirror the 10% death penalty of TFA13); a cabbage replaces the standard loot and
  all tertiaries (tag `junk`). Poor contribution is not part of the rule.

## Not now

Practice Mode (announced, not live on 3 Nov; the stats table already has a practice row and the party mode enum has
`Practice = 1` — kept hidden), and the "next week" balancing named on 3 Nov (Archive Guardian and Xarth passes,
Ascension shard availability, loot numbers) plus the Dagon'hai cap fix expected on 11 November.

## Related item rules (from the TFA updates)

- Rondache flat armour / magic resistance applies after other mitigation, including protection prayers (28 Oct).
- Zorya's tome special can no longer be pre-stacked (22 Oct).
- Ascension rocks in the lobby; a Rock Golem from them at 1/68,120 (3 Nov).
