# Raid framework — Claude Code prompt

Build a **raid framework** and use it to ship OSRS's four raids: **Chambers of Xeric (CoX)**, **Theatre of Blood (ToB)**, **Tombs of Amascut (ToA)** and **The Fractured Archive (TFA)**, which was released on 20 October 2026. All four must match the live game 1:1 as of our cache: rev 242, OpenRS2 cache 2757, built 2026-11-03.

**"Fully working" means:**
- A party can form, enter, play every room in every mode, die, disconnect, rejoin, finish and loot, exactly as on OSRS.
- RuneLite's raid features work unmodified against our server: CoX scouting and the points overlay, the Loot Tracker, `!kc` and personal-best tracking, and the collection log.
- The framework is generic, so a future raid is mostly a new definition plus its rooms.

---

## 0. How to work

1. **Read first.** Read `CLAUDE.md`, `docs/BRIEF.md`, `docs/PROGRESS.md`, `docs/DECISIONS.md`, and `docs/features/phasing.md` if it exists. Where the repo names things differently from this prompt, follow the repo and record the mapping in the plan.
2. **Save and plan.** Save this prompt verbatim as `docs/plans/raids-brief.md`. Then write `docs/plans/raids.md` covering:
   - what already exists: instancing, NPC scripting and AI, the death pipeline, the collection log, hiscores and broadcasts;
   - what's missing, the modules touched and the risks;
   - the definition of done for each phase in §10.
3. **Date anchor.** Implement each mechanic as it was live on **2026-11-03**.
   - Every wiki page has a changelog. Include changes up to that date and exclude later ones.
   - Record in each doc the date you checked the source.
   - Example: CoX unique weights changed on 12 August 2026, so use the new weights.
   - TFA changed every week after launch: hotfixes on 21 and 22 October, and updates on 28 October and 3 November. The cache was captured at 10:00 UTC on 3 November, so check whether it already contains that day's update. One way is to look for that update's client-side changes in the cache: it added clickboxes for the Archive Guardian and for Ocellus's third phase. Use the set of changes that matches the cache, and record which one in DECISIONS.md.
4. **Sources**, in this order of authority:
   1. the cache: configs, maps, interfaces, clientscripts, dbtables and gamevals. TFA keeps its drop tables, room configs and supply shop in dbtables (§1.5);
   2. osrs-docs.com mechanics, especially **Instancing** (BRIEF §2);
   3. the OSRS Wiki pages for each raid, room, boss and reward chest;
   4. RuneLite's open-source plugins, read as a record of what the client reads.

   Document every mechanic under `docs/mechanics/raids/`, with sources. Anything unverified is marked `OPEN`. Never invent numbers.
5. **Model policy.** BRIEF §0 item 8 applies.
   - **Main session (Opus 5.5, medium) owns:**
     - instancing and the raid lifecycle;
     - deaths, wipes and disconnects;
     - scaling into NPC stats, and points fed from combat events;
     - rewards and item transactions, and persistence;
     - RNG and determinism, and security;
     - **every boss and combat encounter**, because they're combat;
     - reviewing all subagent output.
   - **`scout`** researches: wiki pages and their changelogs, gamevals, the clientscripts that read raid varbits, and RuneLite plugin code. It produces a source list for every room.
   - **`implementer`** takes non-combat work once the main session has set the interfaces:
     - party boards and their interfaces;
     - HUD drivers, once the varbit encodings are documented;
     - puzzle, skilling and scavenger rooms;
     - the supply and storage interfaces;
     - data import, dev commands and doc drafts.
   - **`verifier`** checks every mechanics doc, reward formula and test expectation against its sources, without seeing the implementation.
6. **Kotlin.** All code is Kotlin and follows BRIEF §11.
   - Raid definitions are a type-safe Kotlin DSL.
   - Raid and room states and events are sealed hierarchies.
   - Ids (`PartyId`, `InstanceId`, `RaidRunId`) and coordinates are value classes.
   - Room and boss scripts use the existing suspend-script model on the world dispatcher.
   - Framework code on the world thread is plain synchronous Kotlin with no steady-state allocation.
7. **Commit and report.** Commit at each green milestone using conventional commits. Stop at the end of each phase (§10) and report.
8. **Stop and ask** before any of these:
   - changing tick order or the shared death pipeline in a way that could affect the golden PvP scenarios;
   - deviating from OSRS behaviour for any reason;
   - editing the cache. That should never be needed.

## 1. How OSRS does it

This is a research summary. Verify each fact against its source (§11) and record the source in the mechanics docs.

### 1.1 Common machinery

- **Instances are copies of template zones.** A raid copies 8×8 zones from fixed areas of the static map into a private instance, with a rotation (0–3) per zone.
  - The client gets the list of zone templates in the instance rebuild packet (`RebuildRegionV2` in RSProt 242) and renders the copy.
  - RuneLite identifies CoX rooms from those template references, and ToB and ToA rooms from the template region ids.
- **Raid state is ordinary game state.** It lives in varps, varbits and inventories that the cache gamevals name:
  - HUD and party state: `RAIDS_CLIENT_*`, `TOB_CLIENT_*`, `TOA_CLIENT_*` and `TFA_CLIENT_*`;
  - kill counts: `TOTAL_COMPLETED_*`;
  - chest state: `TOB_TREASUREROOM_CHEST_0..4`, `TOA_VAULT_CHEST_*` and `TFA_VAULT_CHEST_0..7_STATE`;
  - timers: `RAIDS_TIMER`;
  - invocation presets: `TOA_INVOCATIONS_PRESET_1A..5C`;
  - contribution: `RAIDS_PLAYERSCORE`, `TOA_PERSONAL_CONTRIBUTION` and `TOA_DAMAGE_DONE`.

  Cache clientscripts read these to draw party lists, HUD orbs, points, invocations and chests. Our job is to produce the same state; the client does the rest.
- **Parties are per world.** Each raid forms parties its own way:
  - CoX: the leader's friends chat plus the recruiting board;
  - ToB: the notice board in Ver Sinhaza, with apply, accept and reorder;
  - ToA: the Grouping Obelisk, with a party list, promotion and invocations;
  - TFA: the Grouping Board in its lobby.
- **Starting locks things.** When the raid starts, the party and its scaling are fixed. Nobody joins afterwards, and leaving doesn't change scaling.
- **Teleports out are blocked**, except for escape crystals where the raid allows them.
- **Deaths, disconnects and wipes** follow per-raid rules (§1.2 to §1.5).
- **Rewards follow one pattern:** the raid's points or contribution feed one or more unique rolls, then a recipient is chosen by weighting, then commons and tertiaries are rolled. The results go into chests, and the raid sends broadcasts, collection-log entries, kill counts and PB messages.

### 1.2 Chambers of Xeric

- **Party:**
  - 1–100 players, in the leader's friends chat channel plus the recruiting board. The board sets minimum combat level, minimum skill total and party size (`RAIDS_LOBBY_*`).
  - There's a scaled-party-size option for adding "virtual players" (`RAIDS_SCALING`, `RAIDS_CLIENT_PARTYSIZE_SCALED`).
  - The leader starts the raid. No one can join afterwards, and the bank chest closes.
  - If the leader leaves, the highest-ranked friends-chat member (minimum rank: recruit) becomes leader. If no one qualifies, everyone is removed.
- **Layout:**
  - Floors 1 and 2 are generated; floor 3 is the Great Olm.
  - Map pools are regular (equal numbers of small and medium layouts), large (5 combat and 3 puzzle rooms) and full (the Challenge Mode layout), selected with `RAIDS_MAP_POOL_SELECTED`. Challenge Mode (CM) uses one fixed layout with every combat and puzzle room.
  - Room templates match RuneLite's `InstanceTemplates`: x 3264, y 5152–5696, levels 0–2, rooms 96×32.
  - Combat rooms follow one of four fixed rotations (RuneLite's `RotationSolver`).
  - Floor layouts match the 26 patterns in RuneLite's `LayoutSolver` (24 of one size, 2 larger). Where a pool contains layouts RuneLite doesn't list, take them from the wiki and mark them `OPEN`.
  - The lobby staircase's "Reload" option rebuilds the layout and boots everyone in the instance. CM layouts can't be rerolled.
  - On start, a message states the layout size.
- **Scaling:**
  - NPCs scale with party size (including the scaled size) and use the stats of the party member with the highest combat stats.
  - Players can opt out of level scaling via Captain Rimor.
  - Puzzle skill requirements scale with the team's skill levels.
- **Points:**
  - Earned from damage, with points scaling linearly with combat level.
  - Also earned from skilling: storage units give 100 per tier, scavenger shortcuts give 5 × the level requirement, and cooking and potions have caps per party size. Thieving gives 115 per grub, with its own cap formula.
  - Death costs 40% of the player's personal points. If those are under 5% of the team total, the team loses 5% instead.
  - The points overlay reads `RAIDS_CLIENT_PARTYSCORE` and `RAIDS_PLAYERSCORE`.
- **Death:** the player respawns at the start of the previous floor. Raid-only items drop. The death is safe and never ejects the player.
- **Storage:**
  - Units come in four tiers: Small, Medium, Large and Massive. They need Construction 30, 60, 90 or 99 and hold 250, 500, 1,000 or 1,500 shared items and 30, 60, 90 or 120 personal items.
  - The private storage outside the raid persists between raids and must be empty before entering again.
- **Uniques:**
  - 1% per 8,676 total points, capped at 65.7% (570,000 points). Points beyond the cap roll again, for up to 6 uniques.
  - The recipient is weighted by personal points. Players who left can win a unique roll, but that reward is discarded.
  - Weights since 12 August 2026, normal mode (out of 60): Dexterous 14, Arcane 14, twisted buckler 4, dragon hunter crossbow 4, Dinh's bulwark 3, each ancestral piece 4, dragon claws 3, elder maul 2, Kodai insignia 2, twisted bow 2.
  - Challenge Mode (out of 56): both prayer scrolls are 12; everything else is the same.
  - Olmlet is 1/53 per broadcast unique. In CM within the target time, metamorphic dust is 1/400 and the twisted ancestral colour kit is 1/75.
- **Commons and chest:**
  - Commons are two distinct rolls whose quantity scales with personal points up to 131,071. Find the per-item quantity rule on the Ancient chest page or its calculator; if it isn't stated, mark it `OPEN`.
  - Elite clues are 1/12 (1/11 with elite Combat Achievements) and only roll when no unique was won. A dark journal is given unless the player already has one in their bank.
  - The chest's light is white for commons, purple for a unique, turquoise for dust and green for the kit.
  - Unclaimed loot drops on the ground when the player leaves.
  - The chest uses the `RAIDS_REWARDS` interface and inventory.
- **Records:**
  - Kill counts are kept in `TOTAL_COMPLETED_XERICCHAMBERS` and `TOTAL_COMPLETED_XERICCHAMBERS_CHALLENGE`. The message is `Your completed Chambers of Xeric count is: <col=ff0000>51</col>.`
  - Completion message, including its stray `</col>` tags: `<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>41:10</col> Personal best: </col><col=ff0000>40:03</col>`
  - On a new PB, the ending is ` (new personal best)</col>` instead.
  - Team-size buckets are Solo, 2 to 10 players, 11-15, 16-23 and 24+.

### 1.3 Theatre of Blood

- **Party:**
  - 1–5 players, formed at the notice board (`TOB_PARTYLIST`, `TOB_PARTYDETAILS`). The leader can reorder the party, and there are friends and clan filters.
  - There's no formation cooldown; it was removed on 12 August 2026.
  - Modes are Entry, Normal and Hard. Hard needs a Normal completion.
- **Order:** fixed: Maiden, Bloat, Nylocas, Sotetseg, Xarpus, Verzik.
  - Supply chests (`TOB_MIDWAY_STORES`, with points in `TOB_MIDWAYCHEST_POINTS`) appear after Bloat and after Sotetseg. In Entry mode they hold 10 bandages.
  - Players with a completion can quick-start Verzik.
- **Scaling:**
  - Boss HP is 75% for teams of 3 or fewer, 87.5% for 4 and 100% for 5.
  - Entry mode does about 50% less damage. Hard mode has more HP and changed mechanics.
- **Death:**
  - A dead player waits outside the fight until the room is cleared.
  - A team wipe fails the raid. Unprotected items are lost but can be reclaimed for 100,000 coins. In Entry mode reclaiming is free, attempts are unlimited and bandages are given after a wipe.
  - Deaths are dangerous: Hardcore Ironmen lose their status.
  - Disconnecting in a fight counts as a death; logging out in a hallway is safe.
  - On login, the player rejoins their party in "purgatory" if possible; otherwise it's a full death with the reclaim fee.
- **Exit:**
  - Escape crystals (75,000 coins) are the only way out. One can be used on a party member, consuming both players' crystals.
  - Logging out while holding one teleports you out on your next login.
- **Spectating:**
  - Spectators join through the theatre entrance or Abigaila, choosing a player by name or the most recent party.
  - They must be on the same world, and they respect the target's "friends only" private-chat setting.
  - They can bring no items except the oculus orb.
- **Uniques:**
  - Each player has their own chest (`TOB_CHESTS`, with state in `TOB_TREASUREROOM_CHEST_0..4`).
  - The chance is 1/9.1 for a deathless Normal raid and 1/7.7 for Hard; Entry has none.
  - Deaths lower both the unique chance and common quantities. The exact formula isn't public: mark it `OPEN` and cite the model you use.
  - The recipient is weighted by deaths and damage, so the MVP is most likely. The 14 MVP points are split by top damage per boss: Maiden 2, Bloat 2, Nylocas 1, Sotetseg 1, Xarpus 2 and each Verzik phase 2.
  - Weights in Normal (out of 19): Avernic defender hilt 8; Ghrazi rapier, Sanguinesti staff and each Justiciar piece 2; Scythe of Vitur 1. In Hard (out of 18), the hilt drops to 7.
- **Commons and chest:**
  - Three rolls, duplicates allowed. A unique replaces all commons.
  - Entry mode gives 20% of Normal loot. Hard mode gives +15%, and another +15% within the target time.
  - Lil' Zik is 1/650 (1/500 in Hard), scaled by performance.
  - Unclaimed loot can be claimed at the chest outside the Ver Sinhaza bank. The chest page also says unclaimed loot is lost on logout; confirm which rule applies when.
- **Records:**
  - Kill counts: `TOTAL_COMPLETED_THEATREOFBLOOD`, `_HARD` and `_STORY`.
  - Messages: `Theatre of Blood completion time: <col=ff0000>17:00</col> (new personal best)`, `Theatre of Blood total completion time: <col=ff0000>24:40.20</col>. Personal best: 20:45.00` and `Your completed Theatre of Blood count is: <col=ff0000>73</col>.`
  - The scoreboard (`TOB_SCOREBOARD`) and strategy table show deaths, MVP and wave times.

### 1.4 Tombs of Amascut

- **Party:**
  - 1–8 players, formed at the Grouping Obelisk (`TOA_PARTYLIST`, `TOA_PARTYDETAILS`, `TOA_LOBBY`). The leader can promote members.
  - Invocations are chosen in `TOA_INVOCATIONS`. Five presets are stored in varps.
- **Raid level:**
  - 0–600, set by invocations. Entry mode is 0–149, Normal 150–299 and Expert 300 and up.
  - Invocation categories: attempts, time limits, Helpful Spirit, path level, restoration, prayer, boss-specific, and Blazing Tombs I and II (which need raid level 300).
- **Structure:**
  - Four paths in any order. Each is a puzzle room then a boss: Apmeken then Ba-Ba, Crondis then Zebak, Het then Akkha, Scabaras then Kephri. Then come the Wardens.
  - Path levels are shown in `TOA_CLIENT_*_LEVEL`. The first level adds 8%, each later one 5%, up to six levels.
  - Walk the Path adds random level-ups, announced by "You hear a mysterious rumbling coming from the Path of [god]."
- **Scaling:**
  - The 2nd and 3rd members each add 90% of base HP; the 4th and later add 60% each.
  - Every 5 raid levels add 2% to HP, defence and accuracy. Damage scales the same way up to a cap; confirm the cap.
- **Points:**
  - Everyone starts with 5,000 points, which are subtracted at the end.
  - Players earn 1 point per damage, with per-NPC multipliers: Wardens' core 0×, Het's seal 2.5×, Wardens phase 3 2.5×, phase 2 2×, Ba-Ba 2×, obelisk and Zebak 1.5×, baboons 1.2×, Kephri's scarabs 0.5×.
  - Each room's MVP gets +300 × team size.
  - Death costs 20% of total points, minimum 1,000.
  - Points cap at 64,000 per player and 20,000 per room.
- **Death:**
  - Deaths are unlimited by default.
  - With an attempts invocation, a team wipe uses an attempt and restarts the room. Out of attempts, unprotected items are lost (reclaimable for up to 500,000 coins) and the team is ejected.
  - A player who disconnects rejoins if a teammate is still logged in. Logging out during an encounter counts as a death.
  - An escape crystal turns a wipe into an eject.
- **Supplies:** the Helpful Spirit gives supplies after two paths and again before the Wardens (`TOA_MIDRAID_LOOT`, inventories `TOA_MIDRAIDLOOT_BUNDLE1..3` and `TOA_MIDRAIDLOOT_BAG`). The amounts depend on invocations.
- **Uniques:**
  - The chance is 1% per (10,500 − 20 × RL) points, capped at 55%. RL is the raid level up to 310; above that it's 310 + (level − 310)/3 up to level 430, then 350 + (level − 430)/6.
  - Only one unique per raid. The recipient is weighted by points, and points from players who disconnected still count.
  - The weights change with raid level (see the table on the chest page). A unique below its raid-level threshold needs an extra 1/50 roll.
  - The pet is 1% per (350,000 − 700 × RL′) points, where RL′ has its own scaling.
- **Commons and chest:**
  - Three rolls, only when there's no unique. Quantity = points / divisor, multiplied at level 300+ by (1.15 + 0.01 × (level − 300)/5).
  - Under 1,500 points, the player gets fossilised dung instead.
  - There are also tertiaries and challenge rewards.
  - Each player has their own chest (`TOA_CHESTS`). The sarcophagus flames turn purple for a unique.
  - Unclaimed loot goes to the Rewards Niche.
- **Records:**
  - Kill counts: `TOTAL_COMPLETED_TOMBSOFAMASCUT`, `_ENTRY` and `_EXPERT`.
  - Messages: `Challenge complete: The Wardens. Duration: <col=ef1020>8:30</col><br>Tombs of Amascut challenge completion time: <col=ef1020>8:31</col> (new personal best)`. There are Entry Mode and Expert Mode variants, plus `Tombs of Amascut total completion time: <col=ef1020>0:01</col> (new personal best)`.
  - The raid summary (`TOA_RAID_SUMMARY`) shows titles (`TOA_TITLE_ACHIEVED_*`).

### 1.5 The Fractured Archive

TFA was released on 20 October 2026 as the fourth raid. It changed every week after launch, so check every fact against the date anchor (§0).

- **Access:**
  - The lobby is in the Chasm of Tears. You reach it from the Tears of Guthix cave with a lit sapphire lantern; a Guthixian temple teleport lands nearby.
  - Entry needs While Guthix Sleeps. The first attempt to enter starts the miniquest "The Fractured Archive" (`TFA_MINIQUEST*`).
- **Party:**
  - 1–8 players, formed at the Grouping Board in the lobby (`TFA_LOBBY`, `TFA_PARTYLIST`, `TFA_PARTYDETAILS`, `TFA_LOBBY_FRIENDS_FILTER`).
  - Since 28 October, parties survive a wipe and a logout-and-reconnect.
- **Structure:** each raid fights five of the eight bosses. After each boss, players pick a door in a selection room:
  1. the Archive Guardian (always first);
  2. Ezekiel, or Bork and the Smorks;
  3. Moia, or Dreadnoth the Jellied. The Quartermaster's supply shop appears after this one;
  4. Xarth, or the Dagon'hai;
  5. Ocellus (always last).

  Both bosses in a pair are worth the same contribution, so the choice is about speed and safety. The doors and room configs are cache dbtables (`TFA_NEXUS_DOORS_SECTION_1..3`, `TFA_ROOMDATA_*`).
- **Scaling:**
  - Boss HP scales with the number of players present, and a solo raid uses two-player scaling. The exact HP table isn't on the wiki, so mark it `OPEN` until it's verified.
  - Stat drains are capped. Offensive stat reduction stops at 50% for Ocellus, Xarth and Dreadnoth. Defence drain stops at 60% of normal for the Archive Guardian, Bork, Ezekiel, Moia, Xarth and Ocellus.
- **Contribution:**
  - Every boss is worth 100 points except Ocellus, which is worth 300, for 700 per raid. Points are split by damage dealt. Damage on adds counts, up to a cap per room.
  - Each death costs 10% of the player's expected contribution: 35 points in a duo.
  - Since 28 October, team deaths cut the group's unique chance harder. The formula isn't public: mark it `OPEN` and cite the model you use.
- **Death:**
  - Every death is dangerous. Hardcore Ironmen lose their status, Hardcore Group Ironmen lose a life, and the contents of the Item Retrieval Service are lost.
  - A team wipe ejects the party and leaves unprotected items in gravestones outside the entrance. They can be reclaimed for up to 500,000 coins, or 75% less until the player's fifth clear.
- **Disconnects:**
  - Since 22 October, food and potions aren't lost on disconnect (`TFA_SUPPLIES_SNAPSHOT_INV`).
  - A player who logs out in the loot room can still loot their chest.
- **Supplies:** the Quartermaster's shop sells food and potions for supply points. It uses `TFA_SUPPLY_POINTS` and the inventory `TFA_MIDRAID_SUPPLIES_SHOP`, with its stock in the `TFA_MIDRAID_SUPPLIES_SHOP*` dbtables.
- **Spectating:** through the lobby's strategy board, up to 100 spectators per raid. Trying to join a full raid gives "That raid has reached the maximum number of spectators."
- **Uniques:**
  - The chance is 1/14 for solo and duo. It rises by 4% of that base for each player beyond two: about 1/13.46 for three and 1/11.29 for eight. The wiki calls the increase "multiplicative", but its table fits 4% of the base per player, so follow the table.
  - Only one unique drops per raid. The recipient is drawn by contribution share: in a deathless duo with 490 and 210 points, the chances are 70% and 30%.
  - The wiki's weights, out of 15, are Rondache 5, Zorya's tome 4, Horn of Bran 3, Ascension crossbows 2 and Obligator 1. The cache's `TFA_DROPTABLE_UNIQUE` rows are the authority. They include an entry named `ZEAL`, so reconcile the two sources and record what you find.
- **Commons and tertiaries:**
  - Without a unique, the standard table is rolled three times. Each roll has a 1/51 chance of five times the quantity.
  - Archivist's folio: 1/15, only if the player doesn't own one.
  - Elemental fragments: about 1/20 overall with duplicate protection, stopping once all four are owned (`TFA_MOTE_*_UNLOCKED`).
  - Elite clue: 1/10, or 1/9 after the elite Combat Achievements tier.
  - Orion (pet): the chest page lists 1/750, but another source says the rate is unknown. Verify it.
  - A poor performance gives a cabbage, and each death raises the chance of one. A cabbage blocks other tertiaries.
  - The drop tables are cache dbtables (`TFA_DROPTABLE_*`).
- **Chests:**
  - The Void reward room has one chest per player (`TFA_VAULT_CHEST_0..7_STATE`, `TFA_VAULT_OWN_CHEST`). Chests appear only after the braziers are lit (from the 3 November update), and the player's own chest sparkles.
  - A unique turns the two braziers purple and adds a special chest on the northern platform.
  - Opening a chest shatters it, leaving a rift. The loot interface (`TFA_CHESTS`) offers bank, take and destroy. Other players' rifts have no search option (from the 3 November update).
  - Unclaimed loot waits in the lobby's loot wheelbarrow (`TFA_HAS_CLAIMABLE_LOOT`).
  - Common drops must not trigger valuable-drop broadcasts. Launch week had that bug; the 28 October fix covered everything except cabbages.
- **Records:**
  - Kill count: `TOTAL_COMPLETED_THEFRACTUREDARCHIVE`, with the message `Your completed Fractured Archive count is: <col=ff0000>1</col>.`
  - Time: `The Fractured Archive: Total completion time: <col=ef1020>2:25.80</col> (new personal best)` or `The Fractured Archive: Total completion time: <col=ef1020>2:54.60</col>. Personal best: 0:00.60`.
  - Living record capes are awarded at 100, 500, 1,000, 1,500 and 2,000 completions (`TFA_CAPE_T1..5`).
  - The stats interface (`TFA_STATS`) shows party times and stats.
- **Data in the cache.** Unlike the older raids, which have no dbtables, TFA keeps most of its data in 135 of them. They hold the drop tables, room and door configs, the supply shop, the capes, and some boss patterns (`TFA_EZEKIEL_FINALE_*`, `TFA_OCELLUS_BARRAGE_*`, `TFA_SWORD_GRID_*`). Read them, because they outrank the wiki.

## 2. Client contract

Write this down in `docs/mechanics/raids/client-contract.md`, then follow it exactly.

- **Official templates only.**
  - Build CoX rooms from the template areas listed in RuneLite's `InstanceTemplates`. Lay out the floors with Jagex's geometry, so that RuneLite's `LayoutSolver` and `RotationSolver` recognise every layout we generate.
  - Build ToB, ToA and TFA from their real regions. For example, RuneLite's Loot Tracker checks ToB region 12867 and lobby 14642.
- **Interfaces and inventories:** use our generated symbols.
  - CoX interfaces: `RAIDS_LOBBY_PARTYLIST`, `RAIDS_LOBBY_PARTYDETAILS`, `RAIDS_SIDEPANEL`, `RAIDS_OVERLAY`, `RAIDS_REWARDS`, `RAIDS_STORAGE_PRIVATE`, `RAIDS_STORAGE_SHARED`, `RAIDS_STORAGE_SIDE` and `RAIDS_CHALLENGE`.
  - ToB interfaces: `TOB_PARTYLIST`, `TOB_PARTYDETAILS`, `TOB_HUD`, `TOB_MIDWAY_STORES`, `TOB_CHESTS`, `TOB_SCOREBOARD` and `TOB_INFOBOARD`.
  - ToA interfaces: `TOA_PARTYLIST`, `TOA_LOBBY`, `TOA_PARTYDETAILS`, `TOA_INVOCATIONS`, `TOA_HUD`, `TOA_MIDRAID_LOOT`, `TOA_MIDRAIDLOOT_BAG`, `TOA_CHESTS`, `TOA_RAID_SUMMARY` and `TOA_SCOREBOARD`.
  - TFA interfaces: `TFA_LOBBY`, `TFA_PARTYLIST`, `TFA_PARTYDETAILS`, `TFA_HUD`, `TFA_SPECTATE_HUD_INV`, `TFA_STATS` and `TFA_CHESTS`.
  - Inventories: `RAIDS_REWARDS`, `RAIDS_SHAREDSTORAGE`, `RAIDS_PRIVATESTORAGE`, `TOB_CHESTS`, `TOA_CHESTS`, `TOA_MIDRAIDLOOT_BUNDLE1..3`, `TOA_MIDRAIDLOOT_BAG`, `TFA_CHESTS`, `TFA_MIDRAID_SUPPLIES_SHOP` and `TFA_SUPPLIES_SNAPSHOT_INV`.
- **Varps and varbits.**
  - Start from the names in §1. Then search the gamevals for `RAIDS_`, `TOB_`, `TOA_`, `TFA_`, `TOTAL_COMPLETED_` and `COLLECTION_COUNT_RAIDS*`.
  - Work out every encoding from the clientscripts that read it, by disassembling them with our cache tools, or add a dumper in `tools/`. Don't guess.
  - Persist what OSRS persists, so the collection log, adventure log and HUD read correct values. The names are a strong hint: `*_PERM_*` and `TOTAL_COMPLETED_*` persist, and `*_TEMP_*` reset.
- **Chat messages:**
  - Reproduce them character for character, including colour tags.
  - RuneLite's `ChatCommandsPluginTest` holds captured real messages; use them as test fixtures.
  - Where a message isn't captured, take it from the wiki and mark it `OPEN` until it's seen in game.
- **Loot Tracker:** when `RAIDS_REWARDS`, `TOB_CHESTS`, `TOA_CHESTS` or `TFA_CHESTS` opens, it reads the chest inventory. For ToA it also reads `TOA_CLIENT_RAID_LEVEL`, `TOA_CLIENT_P0..P7` and `TOA_DAMAGE_DONE`. Set them before the interface opens.
- **Licensing:** don't copy RuneLite code into the server. RuneLite data you reuse as a fixture or cross-check (layout codes, rotations, template coordinates) is BSD-2: keep its notice in `data/PROVENANCE.md`.

## 3. Framework design

### 3.1 Modules

- **`engine`** (core):
  - instancing (§3.2);
  - per-instance NPC stat overrides;
  - area tags written in template coordinates that follow the copy;
  - hooks for the death policy and for a lost session.
- **`content/raids-core`:** the framework itself: party and formation, lifecycle, rooms, scaling, the contribution ledger, policies, rewards, records, spectating, client drivers and the DSL.
- **`content/raids-tob`, `content/raids-cox`, `content/raids-toa` and `content/raids-tfa`:** each raid's definition, rooms and encounters.
- **`data/raids/`:** reward tables, weights, divisors and NPC stats, imported with `tools/wiki-import` and listed in `data/PROVENANCE.md`.

### 3.2 Instances (engine)

Build instancing, or finish it, following osrs-docs' Instancing page.

- **Allocation.** Allocate instance space per world and free it on destroy.
- **Building from templates.** Copy zone templates with rotation across all levels: collision, locs (with rotated shape and orientation), NPC spawns and map squares.
  - Rotated templates are precomputed once and shared.
  - An instance stores only its dynamic overlay: loc changes, ground items and NPCs.
- **Off the world thread.** Build the instance off the world thread and attach it in pre-tick.
  - Send `RebuildRegionV2` when a player enters, and the normal rebuild when they leave.
  - Re-send the region as players move between distant parts, such as CoX floors.
- **Template coordinates.** Content is written in template coordinates. The framework maps them to instance coordinates (rotation-aware) for spawns, locs, areas and scripts.
- **Guaranteed destroy.**
  1. Eject everyone, applying the raid's leave policy.
  2. Remove NPCs, locs and ground items.
  3. Free the space.

  No leaks.
- **Restarts.** After a restart, players saved inside an instance log in at the raid's exit with that raid's rules applied: raid-only items are removed and wipe rules apply where they would.
- **Phasing.** Instances are already excluded from world phasing.

### 3.3 Raid definitions (DSL)

Each raid is declared once:
- id, modes, party limits and how the party forms;
- the layout strategy: a fixed sequence (ToB), player-chosen paths (ToA), a choice between paired rooms (TFA), or procedural pools (CoX);
- its rooms;
- the scaling function and points rules;
- death, disconnect, teleport and item policies;
- the reward function;
- records: KC varps, messages and PB buckets;
- client drivers.

This sketch is illustrative, not binding:

```kotlin
val theatreOfBlood = raid(RaidId.TOB) {
    modes(ENTRY, NORMAL, HARD)
    party(size = 1..5, formation = NoticeBoard)
    layout = fixedSequence(MAIDEN, BLOAT, NYLOCAS, SOTETSEG, XARPUS, VERZIK)
    deaths = waitUntilRoomCleared(onWipe = failRaid(reclaimFee = 100_000))
    disconnect = deathInEncounter(safeInHallway = true, rejoin = Purgatory)
    rewards = tobRewards
}
```

### 3.4 Party and formation

- **The party core:** a leader, ordered slots, applicants and invites, with kick, promote and reorder. Parties are per world and have explicit states.
- **Formation per raid.** Each raid's surface (§1.1) is bound to its interfaces.
- **Requirements** are checked at formation and again at start: quests, a Normal completion for ToB Hard, and minimum combat and skill totals.
- **Leader controls.** Only the leader changes party settings, including ToA invocations and the CoX scaling and map pool.
- **Account-type rules:** Group Ironman prestige rules and Ironman loot rules (`RAIDS_CLIENT_IRONSHARING_PERMITTED`).

### 3.5 Lifecycle

**States:**
1. `Forming`
2. `Lobby`: the instance is built, scouting works in CoX, and the bank is available.
3. `Started`: the party and scaling are locked, timers start and varbits are set.
4. Room states from the definition's room graph.
5. An ending: `Completed` (the reward room), `Failed` (a wipe, or out of attempts) or `Abandoned` (everyone left).
6. `Closed`: the instance is destroyed after a grace period.

**Events:** entered, left, died, disconnected, reconnected, room started, room cleared, room failed and raid completed. Content and other plugins subscribe to these through the event bus.

### 3.6 Rooms and encounters

- **A room** has templates, spawns, barriers, a start trigger, a completion condition, a checkpoint, timers, per-room damage and MVP tracking, and an optional death override.
- **Encounters** use the existing NPC script and AI, and the combat primitives, and are tick-accurate (BRIEF §5.4 and §8).
- **One doc per room.** Each room gets `docs/mechanics/raids/<raid>/<room>.md` with:
  - the attack cycle, special attacks, phase transitions and spawn timings, as tick timelines;
  - scaling and points;
  - sources and anything `OPEN`.

### 3.7 Scaling

- A `ScalingContext` is captured at start and never changes. It holds:
  - party size and scaled size;
  - the highest combat stats in the party;
  - mode, raid level, invocations and path levels.
- Each raid's scaling function turns it into per-instance NPC stat overrides at spawn: HP, levels, max hits and NPC counts.
- Never mutate shared NPC definitions.

### 3.8 Points and contribution

- **One ledger per run** holds personal and team points. It is fed by:
  - combat events: damage to this run's raid NPCs only, with per-NPC multipliers;
  - kills, room completions and skilling actions;
  - MVP rules, death penalties and caps.
- The ledger drives the HUD varps and survives a player's disconnect.

### 3.9 Deaths, wipes and attempts

Death policies hook into the engine's death pipeline:
- **Where the player goes:** respawn at a checkpoint (CoX), or wait until the room is cleared (ToB and ToA).
- **What a wipe does:** fail the raid with a reclaim fee, use an attempt and restart the room, or eject with Item Retrieval.
- **Items and account status:** whether items are kept or lost; Hardcore and Group Ironman effects; and whether the death counts as safe or dangerous.

### 3.10 Disconnects, leaving and rejoining

- **Outcomes.** Per raid, a lost session counts as a death in an encounter and is safe in a hallway.
- **Rejoining.** A player can rejoin into purgatory, or is ejected on login if the run is gone. The run keeps each player's membership for rejoining until it closes.
- **Leaving.** This covers escape crystals (including use on others and logout-holding rules), voluntary exits and leader handover.

### 3.11 Raid-only items, storage and supplies

- **Raid-only items.** Flag items as raid-only, using a cache param if one exists and a data list otherwise.
  - They are removed on exit, journaled.
  - They drop on death where the raid says so (CoX).
  - They can never leave the instance: no bank, trade, Grand Exchange, or ground outside.
- **Storage.** CoX storage units: shared storage per run, plus persistent private storage retrieved outside.
- **Supplies.** ToB midway stores use points, or bandages in Entry mode. ToA has the Helpful Spirit bundles. TFA has the Quartermaster's shop, and TFA food and potions survive a disconnect.

### 3.12 Rewards

- **Computed once.** At completion, each raid's reward function computes every player's `RewardResult` once, using the run's seeded RNG. It contains uniques, commons, tertiaries and challenge rewards.
- **Saved before anything opens.** Persist the result atomically before any chest can open.
- **Paying out.** Chests pay through the item-transaction API: all-or-nothing, journaled and idempotent per player per run.
- **Visuals and side effects:**
  - chest light, aura and sarcophagus effects through the same varbits and locs OSRS uses;
  - broadcasts for uniques and pets;
  - collection log entries;
  - noted items and untradeable items, and Ironman rules.
- **Unclaimed loot** follows OSRS: CoX drops it on the ground when you leave, ToB keeps it at the chest outside the bank, ToA keeps it in the Rewards Niche, and TFA keeps it in the lobby's loot wheelbarrow.

### 3.13 Records

- KC varps and PBs per mode and team-size bucket.
- Raid, room, wave and challenge timers.
- Exact messages, the adventure log and scoreboards.
- Hiscore categories through the hub.

### 3.14 Spectating (ToB and TFA)

- A spectator is an instance occupant with no party role. They are never targeted, scaled, counted or rewarded, and their actions are restricted.
- **Joining rules:**
  - ToB spectators must be on the same world, and the target's "friends only" setting is respected.
  - TFA spectators join through the lobby's strategy board, up to 100 per raid.

### 3.15 Client drivers

- One driver per raid maps run state to that raid's HUD, party and points varps and varbits.
- Updates use the existing dirty tracking and are sent at the end of the tick.
- Encodings come from `client-contract.md`.

### 3.16 Determinism, replay and security

- **Determinism.** Each run has its own RNG, seeded from the world RNG at start. Every roll is logged, and `tools/replay` reproduces a run.
- **Security:**
  - One claim per player per run, and rewards are saved before payout.
  - No joining after start, and the CoX rule that a leaver's unique is discarded.
  - Points come only from this run's NPCs.
  - Every interface op checks that its component is actually open.
  - Movement and interactions stay inside the instance.
  - Teleports are refused except escape crystals.
  - Raid-only items stay in.
  - Write a short threat list in the plan.

## 4. Raid content

- **ToB:**
  - all six rooms in Entry, Normal and Hard;
  - spectating, midway stores and quick-start;
  - the scoreboard, strategy table and treasure room.
- **CoX:**
  - every combat room: Tekton, Muttadiles, Guardians, Vespula, Shamans, Vasa, Vanguards and Mystics;
  - every puzzle room: Crabs, Ice Demon, Tightrope and Thieving;
  - scavenger, farming and resource rooms, and the Great Olm;
  - Challenge Mode, the map pools, Reload and scouting;
  - storage units, raid skilling and the raid-only potions.
- **ToA:**
  - the four puzzle rooms (Apmeken, Crondis, Het and Scabaras) and their bosses (Ba-Ba, Zebak, Akkha and Kephri);
  - the Wardens and every invocation;
  - Entry, Normal and Expert;
  - attempts, the Helpful Spirit and the raid summary with titles.
- **TFA:**
  - all eight bosses: the Archive Guardian, Ezekiel, Bork and the Smorks, Moia, Dreadnoth the Jellied, the Dagon'hai, Xarth and Ocellus;
  - the selection-room doors, the Quartermaster, and the Void reward room with its braziers, chests and rifts;
  - entry through the Chasm of Tears, the While Guthix Sleeps requirement and the start of the miniquest;
  - spectating, the stats interface and the Living record capes.
- **Procedure for each room:**
  1. `scout` collects sources.
  2. The main session writes the room doc.
  3. `verifier` checks it.
  4. The room is implemented.
  5. The main session reviews it.

  Commit per room.

## 5. Config (typed `RaidsConfig` loaded from TOML; per-world overrides)

```toml
[raids]
max_instances_per_world = 600
empty_instance_grace_ticks = 100
[raids.cox]
enabled = true
[raids.tob]
enabled = true
[raids.toa]
enabled = true
[raids.dev]          # dev worlds only
commands = false
```

OSRS rules aren't config; they live in each raid's definition and in `data/raids/`.

## 6. Observability and dev tools

- **Metrics:**
  - instances per raid, parties forming, and run outcomes;
  - room clear times and deaths per room;
  - uniques rolled against the expected rate;
  - payouts and unclaimed rewards;
  - instance build and attach times, and memory per instance.
- **Dev commands** (dev worlds only): `::raid` (status), `::raid start <raid> [mode] [size]`, `::raid skip`, `::raid level <n>`, `::raid god`, `::raid seed <n>`, `::raid points` and `::raid layout` (prints the CoX layout code).
- **Tick inspector:** logs lifecycle events, room transitions and every reward roll.

## 7. Budgets (botswarm `raids` profile and a soak run)

- **Load.** 300 concurrent instances per world (75 of each raid), with bot parties moving through rooms. The BRIEF §7 per-world budgets must still hold.
- **Build and attach.** Instance build p99 ≤ 50 ms off the world thread, and attach ≤ 1 ms on it.
- **Memory.** Record memory per instance, aiming for ≤ 4 MB for CoX and ≤ 2 MB for ToB and ToA.
- **Soak.** 10,000 instance create-and-destroy cycles: no growth in heap or native memory, and no leaked NPCs, locs or ground items.
- **Allocation.** No steady-state allocation on the world thread from the raid systems. Building and destroying instances are exempt.

## 8. Tests (minimal)

1. **`RaidLifecycleScenario`** (`tools/scenario`): a test-only fixture raid built from real template zones, with a party of three.
   - Scaling stays locked after a member leaves.
   - Room order is followed.
   - The death policy applies, and disconnect and rejoin work.
   - Rewards are computed once and persisted; a second chest claim does nothing.
   - The leaver gets nothing.
   - The instance is destroyed and raid-only items are removed, with no leaks.
   - Replay produces identical state hashes.
2. **`RaidRewardMathTest`**, a table-driven check against the wiki's published numbers:
   - CoX: 1% per 8,676 points; 65.7% at 570,000 points; 32.85% for a second unique at 855,000; the 40% and 5% death rules.
   - ToA: 1% per 3,700 points at raid level 400; the 55% cap; a common multiplier of 1.35 at level 400; the death penalty of 20% with a 1,000 minimum; the 64,000 and 20,000 caps.
   - ToB: 1/9.1, 1/7.7 and none in Entry; 14 MVP points split.
   - TFA: 1/14 for solo and duo, about 1/13.46 for three and 1/11.29 for eight; 700 contribution per raid; a death costs 10% of expected contribution (35 in a duo); a 490/210 duo split gives 70% and 30%.
   - Weight tables total 60 and 56 for CoX and 19 and 18 for ToB. ToA's weights match its table at each raid level, and TFA's match `TFA_DROPTABLE_UNIQUE`.
3. **`TobSmokeScenario`, `CoxSmokeScenario`, `ToaSmokeScenario` and `TfaSmokeScenario`.** A dev party with god mode and one-shot kills clears every room in every mode. Across its runs, the TFA test takes every door at least once. Each asserts:
   - exact chat messages, using RuneLite's captured strings where they exist;
   - KC varp increments and PBs per bucket;
   - chest contents that are valid against the tables;
   - full cleanup.

   The CoX test also generates 1,000 seeds per pool. Every generated layout of a size RuneLite's `LayoutSolver` covers must match one of its codes, and combat rooms must follow one of the four rotations.

The §7 profile and soak run are manual or nightly. Nothing else.

## 9. Kotlin conventions specific to raids

- **Definitions** are immutable values built by the DSL at startup. **Runs** hold mutable state, owned by the world thread.
- **Rooms** never hold instance coordinates in fields. They hold template coordinates and resolve them through the run's instance.
- **Reward functions** are pure: `(RunSnapshot, Rng) -> Map<PlayerId, RewardResult>`.

## 10. Order of work

- **R0 — Research and plan.** Write `docs/plans/raids.md`, plus `docs/mechanics/raids/{common,cox,tob,toa,tfa,client-contract}.md`, and stub every room doc with its sources. `verifier` checks the fact sheets.
- **R1 — Instancing** in the engine, if it's missing or incomplete (§3.2).
- **R2 — Framework** (`raids-core`), the fixture raid, and tests 1 and 2.
- **R3 — ToB.** It goes first because it has a fixed order and at most five players, yet it exercises deaths and spectating, wipes and reclaiming, supplies, per-player chests and three modes.
- **R4 — CoX:** procedural layouts and scouting fidelity, points, storage, skilling, large parties, CM and Olm.
- **R5 — ToA:** invocations, raid level, paths, attempts, the Helpful Spirit and the Wardens.
- **R6 — TFA:** the paired-room choices, contribution, the Quartermaster, spectating, the Void reward room, and the data that comes from dbtables. It reuses the most framework pieces, so it goes last.
- **R7 — Load and soak (§7)**, end-to-end checks in RuneLite, and the docs.

If PROGRESS.md gives a reason to change this order, change it and say why in DECISIONS.md.

## 11. Sources to start from

- **OSRS Wiki:**
  - [Chambers of Xeric](https://oldschool.runescape.wiki/w/Chambers_of_Xeric), [Ancient chest](https://oldschool.runescape.wiki/w/Ancient_chest) and [Storage unit](https://oldschool.runescape.wiki/w/Storage_unit);
  - [Theatre of Blood](https://oldschool.runescape.wiki/w/Theatre_of_Blood), [Entry Mode](https://oldschool.runescape.wiki/w/Theatre_of_Blood/Entry_Mode), [Hard Mode](https://oldschool.runescape.wiki/w/Theatre_of_Blood/Hard_Mode) and [Monumental chest](https://oldschool.runescape.wiki/w/Monumental_chest);
  - [Tombs of Amascut](https://oldschool.runescape.wiki/w/Tombs_of_Amascut) and [Chest (Tombs of Amascut)](https://oldschool.runescape.wiki/w/Chest_(Tombs_of_Amascut));
  - [The Fractured Archive](https://oldschool.runescape.wiki/w/The_Fractured_Archive) and [Chest (The Fractured Archive)](https://oldschool.runescape.wiki/w/Chest_(The_Fractured_Archive)), plus the `TFA_*` dbtables in our cache;
  - each room's and boss's own page, with its changelog.
- **RuneLite** (`runelite/runelite`):
  - `runelite-client/.../plugins/raids/`: `RaidsPlugin`, `RotationSolver` and `solver/LayoutSolver`;
  - `runelite-api/.../InstanceTemplates.java` and `runelite-api/.../gameval/*`;
  - `plugins/loottracker/LootTrackerPlugin.java`;
  - `plugins/chatcommands/ChatCommandsPlugin.java` and its test.
- **Protocol and engine:** osrs-docs.com Instancing (BRIEF §2), and RSProt `osrs-242` `RebuildRegionV2`.

## 12. Done when

- **Playable.** All four raids can be completed in every mode, both through RSProx with stock RuneLite and with the release client.
- **RuneLite works:**
  - its Raids plugin scouts our CoX layouts, including the rotation, and shows points;
  - the Loot Tracker records loot from all four raids' chests;
  - chat commands record KC and PBs from our messages;
  - the collection log shows the KCs and items.
- **Tests.** The §8 tests pass, and every existing test is green, including the golden PvP scenarios, which stay unchanged.
- **Budgets.** The §7 budgets hold.
- **Docs.** Every room doc and fact sheet cites its sources, the `OPEN` list has been reviewed, and DECISIONS.md and PROGRESS.md are updated.

## 13. Not now

List these in PROGRESS.md:

- new custom raids;
- the 2023 CoX custom-layout and paid-scaling proposal, which never shipped as described: implement only what did ship (the scaled party size, Reload and the full-layout toggle);
- TFA's Practice Mode, which was announced but not released by the anchor date, and the TFA balancing changes planned for 9 November 2026;
- Leagues and Deadman variants;
- raid Combat Achievements (emit the events only);
- cross-world parties.
