# Lobby, nexus and Void vault

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "The Fractured Archive", "Chest (The Fractured Archive)", "Quartermaster (The Fractured Archive)", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

Non-combat rooms (implementer work once raids-core interfaces exist).

- **Lobby** (square 64, 86): Grouping Board (4138, 5547, 0, east), Strategy Board (west, spectating), records board,
  loot wheelbarrow, Ascension rocks (mining; Rock Golem 1/68,120), General Montoya, the Record
  Keeper, bank (3 Nov: re-open fix), raid door (4123, 5562, 0), exit (4126, 5505, 0). tfa.md TFA4, TFA17,
  TFA24.
- **Nexus / selection room** (square 64, 87): the west and east doors per row (cache dbtables 260 `TFA_NEXUS_DOORS`
  with closed / open / slightly-open loc ids — stripped, so door loc ids come from the map and Locs gamevals,
  **OPEN-NX1**); the Quartermaster (Liam) after room 3. Room entry tiles: dbtable 259 (stripped, **OPEN-NX2**: read from the
  map / observation).
- **Void vault** (square 64, 90): entrance portal (4127, 5772, 2); eight chest NPCs 16736-16751 morph on
  `TFA_VAULT_OWN_CHEST`; braziers lit before chests appear (3 Nov); purple braziers + northern unique chest
  (16730 → 16731) on a unique; standard chests: own 16733 → rift 16735, other players' 16732 → rift 16734. **OPEN-NX3:** the wiki
  map shows the vault on plane 1, the cache portal on plane 2. tfa.md TFA24.

## Flow as implemented (content/raids-tfa TfaFlow, TfaVault)

| # | Rule | Source |
|---|---|---|
| NX-F1 | The leader enters the Archive Door first: the run starts (scaling locked at max(2, party size), TFA9), the instance attaches next tick (IN6) and the leader stands before the Guardian. Other members enter through the same door while the run is still in its first room. | TFA4; framework RC2 |
| NX-F2 | The Inert Guardian (16657, size 5) waits in the nexus with its south-west tile at (4125, 5597), where the cache puts the guardian corpse on L2/L3. | cache map |
| NX-F3 | After a room ends: contribution split, prayers off (TFA15a), jingle `TFA_BOSS_DEFEATED`; the nexus offers the next section's west/east doors (Enter and Quick-enter both pick the room and move the party). | TFA6, TFA15a |
| NX-F4 | A side room's passageway (62765 "Exit") and transporter (63087 / 62610 / 63134, "Use" / "Quick-use") return the player to the nexus once the room is done. | cache ops |
| NX-F5 | After room 3 the Quartermaster 16761 stands by the supply wheelbarrow; after room 4 the passage 62766 to Ocellus exists; after Ocellus the Blade 16683 ("Commune", "Travel") carries the party to the vault. | TFA7; cache |
| NX-F6 | Entering the vault completes the raid (rewards once, KC, time). After `tp.tfa.brazierTicks` the chests appear: chest k = NPC 16736 + 2(k-1) at the k-th ring tile, plus the unique chest 16730 when someone got a unique. The own chest "Open" turns it into its OPEN type (the owner's rift) and opens interface 973 on `TFA_CHESTS`. Rift 16690 "Leave" / "Quick-leave" leaves to the lobby door. | TFA24; client-contract 2.6 |
| NX-F7 | Loot interface 973 (`TfaChestUi`): `TFA_CHESTS` (inv 1029) is bound to `973:11` with client script 149 (labels Take / Bank / Discard = ops 1-3). Buttons: BANK "Bank-all", INVENTORY "Take-all", DISCARD "Discard-all" (asked first). Each stack moves in its own item transaction: take = `move` to the backpack ("You don't have enough inventory space."), bank = `removeAt` + `add(Invs.BANK, unnoted obj)` without a bank open (items with obj param 59 refuse), discard = `removeAt` after a Yes/No menu. `TFA_HAS_CLAIMABLE_LOOT` is 1 exactly while the inv holds something (set on vault entry, after every op, on login); the lobby chest loc 63299 morphs 63300 (closed, no ops) / 63301 (open, op1 "Search" opens 973). | TFA24; client-contract 1.5, 2.6 |

OPEN items added with the flow:
- **OPEN-NX4** — nexus states: the square's four level copies are read as L0 = start, L2 = Guardian dead (corpse 62800,
  supply chest and wheelbarrow), L3 = also the passage 62766 to Ocellus (and the records room, which lives on L1/L3).
  The instance copies L0 only and swaps in L2 after the Guardian and L3 after room 4. [I]
- **OPEN-NX5** — whether one door click moves the whole party (implemented) or each player walks through.
- **OPEN-NX6** — vault chest ring, unique chest and rift positions (no source gives the layout); the vault is walkable on
  level 2 (the portal's level), which settles OPEN-NX3 for collision.
- **OPEN-NX7** — the brazier delay before the chests (flag `tp.tfa.brazierTicks`, default 5).
- **OPEN-NX8** — refusal texts (no party, leader first, sealed door, inactive transporter) and the Blade's Commune text.
- **OPEN-TFA15** — While Guthix Sleeps is not on this server, so the TFA1 requirement is behind `tp.tfa.requireWgs`
  (default off) and the outside opening's varbit is set on login.

## Records room, miniquest and lobby NPCs (content/raids-tfa TfaRecords, TfaMiniquest, TfaLobbyNpcs)

Sources: Wiki "Strange door", "Archive key", "Living record (tier 1)", "Living Record (scenery)", "The Fractured Archive
(miniquest)" and "Transcript:The Fractured Archive (miniquest)", "Record Keeper", "General Montoya", "Banker (The Fractured
Archive)", "Wizard apprentice" (read 2026-11-08); cache map 64,87 and `cache-dump collision`.

### Records room and archive key

| Id | Rule | Source |
|---|---|---|
| RR1 | The strange door is the multi 63279 at (4123, 5572) in the **nexus** wall (it exists on levels 0-3), so the records room is reached from inside a run ("found at the southern end of the Fractured Archive"), not from the lobby. 63280 "Strange door" (locked, no ops) / 63281 "Records room" (op 1 "Enter") by varbit `TFA_RECORDS_ROOM_UNLOCKED`. | Wiki Strange door; cache |
| RR2 | The archive key (obj 34658) used on the locked door consumes it (one transaction) and sets the varbit for good. | Wiki Archive key / Strange door |
| RR3 | "Enter" is refused with "The door has sealed itself while the Archive Guardian is active." while the Guardian (16659 / 16660) is alive in the run's nexus; otherwise the player is moved to (4107, 5574, 1), next to the exit loc 63282 (4109, 5573). | Wiki Strange door |
| RR4 | The room lives on level 1 of the nexus square (x 4096-4111, z 5568-5583 — claims, pond, exit; the corridor to x 4119 is not needed) and those four zones are copied into the run's instance at its start (OPEN-TFA31), so the room is the party's own and the raid's teleport and logout rules apply as anywhere in the run. The exit loc 63282 "Exit" returns the player to the instance tile in front of the door (4125, 5572). | cache map / collision |
| RR5 | Claim locs: multis 63287 / 63289 / 63291 / 63293 / 63295 (tier 1-5) draw their `*_VISIBLE` child (63288 ... 63296; ops 1 "Claim", 2 "Inspect") when the varbits 16419 / 16421-16424 are 1. The server sets them on login and after each completion (`RaidEvent.Completed`) to `completions >= obj param 2722` of the tier's cape (100 / 500 / 1000 / 1500 / 2000). Claim gives the tier's cape (obj 34659 / 61 / 63 / 65 / 67) in one transaction unless the player holds it in the backpack, worn or bank; Inspect (and a Claim that is too early) prints the shortfall line. | client-contract 1.2, 1.6; Wiki Living record |
| RR6 | Archive key drop (TFA26): when the Archive Guardian dies, each member present who has 100+ completions, has not unlocked the room and holds no key (backpack, worn, bank) gets the key (guaranteed, once per run) with "A small object falls out of the defeated Archive Guardian." (colour 3366ff). | Wiki Archive key / Living record (tier 1) |

OPEN items:
- **OPEN-TFA13 (resolved reading)** — the key drops guaranteed on every eligible Guardian kill, so a lost or destroyed key comes back (the item's destroy text: "You can gain another by defeating the Archive Guardian"). The Wiki gives no rate.
- **OPEN-TFA28** — texts nobody captured: the unlock line ("You unlock the door with the archive key."), "You have not unlocked the records room.", "You already have that Living record.", the claim line and the shortfall line (built from the tail of client script 10173 quoted in client-contract 1.6: "... completions of the Fractured Archive to gain that Record. Current completions: "). Also open: the cape's "Change-style" option (appearance switching between unlocked tiers) is not implemented; it is an item option outside this module.
- **OPEN-TFA31** — the records room's four level-1 zones (x 4096-4111, z 5568-5583; solid rock on level 0 below) are
  copied into each run's instance at the run's start, so it is per party; no Wiki statement says how Jagex instances it.

### Miniquest "The Fractured Archive" (varbit `TFA_MINIQUEST` 16426, quest row 17027, end value 8)

| Id | Rule | Source |
|---|---|---|
| MQ1 | Start (OPEN-TFA1 resolution): the **first pass through the lobby opening** (loc 62543 / its multi, from the temple cave) sets the varbit to 1 and plays "Entering the archive": Ivy Sophista, Armacus Selles and Eva Cashien brief the player; the extra lines run only for a player who has not taken the Alliance's message. Going through the Archive Door without the scene (folio, staff) also starts it silently. | Main page; transcript |
| MQ2 | The messenger NPC 16699 (Guardian of Armadyl) stands at the Wiki's start pin (4052, 4562) in the Guthixian temple cave. "Talk-to" hands out the Alliance's message (obj 34657, "Read" prints a placeholder); it is optional and only changes the entry scene. | miniquest page; 20 Oct update |
| MQ3 | Value 6 is set for every member present when the run completes (any value below 6). The multi NPCs 16701 (Armacus), 16702 (Ivy), 16703 (Eva) show their real NPC (16696 / 16698 / 16697) exactly at value 6; they stand at (4124, 5557), (4122, 5557), (4126, 5557) in the lobby, free tiles just south of the raid door's exit tile (4122, 5559). | cache `multiNpcs`; transcript "Finishing up" |
| MQ4 | "Talk-to" on any of the three at value 6 plays "Finishing up" (the Ocellus conversation), then "The others depart.", then the varbit becomes 8 (finished), the jingle `QUEST_COMPLETE_1` plays and the chat line is sent. No reward beyond "access to further runs of the Fractured Archive". | miniquest page |

OPEN items:
- **OPEN-TFA1 (resolved reading)** — the miniquest page pins the start near the temple entrance (4052, 4562), the main page says it starts on the first attempt to enter the lobby, the 20 Oct update has a messenger give a note on login. Implemented: the miniquest starts at the lobby opening (that is where the transcript's first scene must happen); the messenger stands at the pinned tile as the optional note-giver. No messenger walks up to the player on login.
- **OPEN-TFA19** — the infobox says the miniquest "unlocks the raid" and rewards "access to further runs", but the miniquest only finishes after a first completed run, so nothing is gated on it. `TfaConfig.requirement` is unchanged (only the `tp.tfa.requireWgs` flag).
- **OPEN-TFA20** — the values 2-5 and 7 are unused: the Wiki lists the in-raid conversations (Alliance Navigator, Armacus, Eva, Ivy, Ocellus) but not which value each sets, and those NPCs (16696-16698 inside the nexus, 16700, 16718-16723) are not spawned in raids yet.
- **OPEN-TFA23** — the text of the Alliance's message is not captured (placeholder).
- **OPEN-TFA25** — the completion chat line reuses content/quests' quest line (that module is internal and unreachable from a raid module); the miniquest wording and the reward scroll are not captured. The quest list reads the varbit by itself (client script 4024 / 4029).

### Lobby NPCs and furniture

| Id | Rule | Source |
|---|---|---|
| LN1 | Banker 16704 (already spawned at (4135, 5555) by content/world, matching the Wiki pin): ops Talk-to / Bank / Collect. "Bank" and "Collect" are the generic bank and Grand Exchange NPC option handlers. "Talk-to" is the Wiki transcript ("Oh, hello. How can I help?" + bank, collect, "What's your function here?" with the poem); the bank answer publishes `OpenBankRequest` (PIN check inside), collect publishes `OpenCollectionBox`. | Wiki Banker (TFA) |
| LN2 | Record Keeper 16692 (spawned at (4136, 5543) by content/world): Talk-to plays the transcript (introduction the first time, then the menu "What are you doing? / How are things? / Goodbye.") and "View Stats?" Yes opens the Alliance records (979). | Wiki Record Keeper; tfa.md TFA27 |
| LN3 | Wizard apprentice 16693 is spawned at (4115, 5539), the free tile east of the Wiki pin (4114, 5539) (a loc tile), in front of the ascension rocks; Talk-to is the generic transcript player. General Montoya 16705 (4117, 5550) is already spawned and uses it too. | Wiki Wizard apprentice |
| LN4 | Lobby bank chest 63275 (4121, 5553): op 1 "Use" is the generic bank chest handler, op 3 "Collect" opens the collection box. 63274 (4134, 5553) has no ops in the cache. | cache |

OPEN items:
- **OPEN-B1 / OPEN-TFA30** — the banker's "buy more bank slots" and "check my PIN settings" answers are omitted: plugin-api has no event for either.
- **OPEN-TFA27** — which Record Keeper branch plays when is not in the transcript importer's output; first-time / later is assumed (saved key `tfa.recordKeeper.met`).
- **OPEN-TFA29** — the lobby's ambient Alliance druids, builders, Temple Knights and Guardians of Armadyl (NPCs 16694 / 16695 / 16706-16723) have no ops in the cache (client-contract 4) and no Wiki page with placements, so none is spawned. The Sprite 16691 ("Look-at") is not implemented.
- The varbits `TFA_WIZARD_APPRENTICE_MET` (16427) and `TFA_JUNA_DIALOGUE` (16428) are not referenced by any client script (client-contract 1.2), so they are left alone.
- **NX-F8** (QM3Nov "navigators to take you out of rooms"): once a side room is done an Alliance Navigator 16700 stands
  next to its entry; "Follow" / "Quick-follow" return the player to the nexus like the passageway and transporter.
  OPEN-NX9: arrival delay (it appears at once) and whether it walks the player (it teleports).
