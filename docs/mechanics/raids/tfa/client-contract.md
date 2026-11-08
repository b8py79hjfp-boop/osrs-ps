<!-- Researched 2026-11-08 from cache 2757 (rev 242) and the disassembled rev-242 client scripts. Tags: [R] read directly, [I] interpretation, [?] unknown. The helper readers mentioned below were one-off scratch scripts and are not in the repo. -->
# The Fractured Archive (TFA): client contract from cache 2757 (rev 242)

Sources: generated symbols in `symbols/build/generated/symbols/tp/symbols/*.java`; `./gradlew :tools:cache-dump:run`
(`config`, `dbrows`, `interface`, `find-locs`); the rev-242 client scripts disassembled to rs2asm (Neptune). One-off read-only readers (not in the repo) read the JS5
store directly for raw NPC opcodes, IF3 hooks, map locs and group version stamps, which cache-dump does not print yet.

Each claim has a tag:
- **[R]**: read directly from cache data or the script text.
- **[I]**: my interpretation of the script logic or of names.
- **[?]**: unknown.

Packed component ids are `interface << 16 | component`.

---

## 1. Symbols

### 1.1 Varps (all `clientCode=0`)
| id | name | notes |
|---|---|---|
| 5894 | TOTAL_COMPLETED_THEFRACTUREDARCHIVE | Read by 1797, 4774, 4778, 10173, 10174 and 10310 (KC / collection-log / cape requirement) [R] |
| 5907 | TFA_PERM_TRANSMIT_1 | Packs 16380-16384. 977 FILTER button redraws on transmit (10087 → 10092) [R] |
| 5908 | TFA_PERM_TRANSMIT_2 | No varbits, no script refs [R] |
| 5909 | TFA_PERM_TRANSMIT_3 | No varbits, no script refs [R] |
| 5910 | TFA_TEMP_TRANSMIT_1 | Packs 16386-16392. HUD and lobby redraw on transmit [R] |
| 5911 | TFA_TEMP_TRANSMIT_2 | Packs 16393-16407. HUD redraws on transmit [R] |
| 5912 | TFA_TEMP_TRANSMIT_3 | Packs 16408-16425 (bits 0-1 unused). HUD redraws on transmit [R] |
| 5913 | TFA_MYCONTROLLER | 10091: `!= -1` → button text "My Party", else "Make Party". 977 MYPARTY redraws on transmit [R]. Holds the party/controller id; -1 = none [I] |
| 5916 | TFA_GATHERING_COORD_TEMP | No varbits, no refs. Server-only [I] |
| 5920 | TFA_MINIQUEST_MAIN | Packs 16426-16428 [R] |
| 5921 | (unnamed) | Varbits 16429 (0..11), 16430 (12..23), 16431 (24..28), 16432 (29..31). All unnamed and unreferenced. Possibly TFA, judged from the position between 5920 and 5922 [I] |
| 5922 | TFA_LOOT_TEMP_1 | Packs 16433 [R] |
| 5925 | TFA_REWARDS_1 | Packs 16434-16438 [R] |
| 5927 | TFA_PET_METAMORPHS | Packs 16440-16448 [R] |

Unnamed varps 5914, 5915, 5917-5919, 5923, 5924 and 5926 (ZORYAS_TOME_STACKS is 5926) exist but have no TFA name.

### 1.2 Varbits (base varp and bit range from `config varbit`) [R]
| varbit | name | varp | bits | client readers |
|---|---|---|---|---|
| 16321 | CA_TOTAL_TASKS_COMPLETED_FRACTURED_ARCHIVE | 4083 | 25..31 | 4780 (CA count switch) |
| 16326-16330 | COLLECTION_ITEM_TFA_CAPE_T1..T5 | 5729 | 24..28 | 10174 |
| 16331 | COLLECTION_ITEM_ARCHIVISTS_FOLIO | 5729 | 29 | none |
| 16322 / 16323 / 16324 | CHARGES_ARCHIVISTS_FOLIO / OBLIGATOR / ZORYAS_TOME _QUANTITY | 5892 / 5892 / 5893 | 0..15 / 16..31 / 0..15 | none |
| 16354 | WGS_TFA_SHORTCUT_UNLOCKED | 4234 | 23 | Loc multi 62542 (raid shortcut, §5) |
| 16380 | TFA_LOBBY_FRIENDS_FILTER | 5907 | 0 | 10093, 10094 |
| 16381 | TFA_SUPPLY_POINTS (0-127) | 5907 | 1..7 | 7257/7258 (omnishop currency getter/setter, currency dbrow 17149 "Supply Credit(s)") |
| 16382 | TFA_PREFERRED_MODE | 5907 | 8..10 | none |
| 16383 | TFA_RECORDS_ROOM_UNLOCKED | 5907 | 11 | Loc multi 63279: 0 → 63280 LOCKED, 1 → 63281 UNLOCKED |
| 16384 | TFA_OCELLUS_MET | 5907 | 12 | 10107 and 10123 hide stats category 17139 (Ocellus) while 0. NPC multi 16671: 0 → 16672 "Hooded Figure", 1 → 16673 "Ocellus" |
| 16386 | TFA_CLIENT_PARTYSTATUS | 5910 | 0..1 | 10047 |
| 16387-16392 | TFA_CLIENT_P0..P5 | 5910 | 2..6, 7..11, 12..16, 17..21, 22..26, 27..31 | 10034 → 10039 |
| 16393, 16394 | TFA_CLIENT_P6, P7 | 5911 | 0..4, 5..9 | 10034 → 10039 |
| 16395 | TFA_CLIENT_PARTYSLOT | 5911 | 10..13 | 10039 |
| 16396 | TFA_CLIENT_HIDEPLAYERS | 5911 | 14 | 10034 |
| 16397-16404 | TFA_VAULT_CHEST_0..7_STATE | 5911 | 15..22 (1 bit each) | **none** (no script, loc or NPC multi uses them) |
| 16405 | TFA_CLIENT_SPECTATE_MODE | 5911 | 23..24 | 10034, 10039, 10044 |
| 16406 | TFA_SPECTATE_SLOT | 5911 | 25..29 | 10044 |
| 16407 | TFA_CLIENT_SKIP_PHASE_MODE | 5911 | 30..31 | 10034 |
| 16408-16415 | TFA_CLIENT_PRIMARY0..7 | 5912 | 2..9 (1 bit each) | **none** |
| 16416 | TFA_HAS_CLAIMABLE_LOOT | 5912 | 10 | Loc multi 63299 (lobby rewards chest): 0 → 63300 CLOSED, 1 → 63301 OPEN |
| 16417 | TFA_CLIENT_CURRENT_PATH | 5912 | 11..13 | **none** |
| 16418 | TFA_MOIA_SHADOW_SHIELD_AMOUNT (0-31) | 5912 | 14..18 | **none** |
| 16419, 16421-16424 | TFA_CAPE_CLAIM_T1..T5_VISIBLE | 5912 | 19, 20, 21, 22, 23 | Loc multis 63287/89/91/93/95: 1 → *_VISIBLE loc (63288/90/92/94/96), else none |
| 16425 | TFA_CLIENT_NUM_EXTERNAL_SPECTATORS (0-255) | 5912 | 24..31 | 10034 |
| 16426 | TFA_MINIQUEST (0-255) | 5920 | 0..7 | 4024 (quest-state getter). NPC multis 16701-16703: value 6 → Armacus 16696 / Ivy 16698 / Eva 16697 |
| 16427 | TFA_WIZARD_APPRENTICE_MET | 5920 | 8 | none |
| 16428 | TFA_JUNA_DIALOGUE | 5920 | 9 | none |
| 16433 | TFA_VAULT_OWN_CHEST (0-15) | 5922 | 0..3 | NPC multis 16736-16751 (vault reward chests, §4) |
| 16434-16437 | TFA_MOTE_EARTH/AIR/FIRE/WATER_UNLOCKED | 5925 | 0..3 | none |
| 16438 | RONDACHE_CHARGES (0-127) | 5925 | 4..10 | 10148, 10159 (item text "n/20") |
| 16440-16447 | OCELLUS_PET_VARIANT_1 / XARTH / EZEKIEL / GUARDIAN / MOIA / BORK / JELLY / DAGONHAI _PET_UNLOCKED | 5927 | 0..7 | none |
| 16448 | TFA_PET_MORPH_RESERVE | 5927 | 8..31 | none |
| 16576 | POH_MENAGERIE_MULTIFORM_OCELLUSPET | | | 642 |

Varbits 16332-16379 sit on unnamed varps 5895-5905 next to the TFA block. They are all unnamed, and none is read by a
script except 16347-16353 (WORN_ITEMS_*, script 3522). Their purpose is [?].

### 1.3 Varcs [R]
| id | name | use |
|---|---|---|
| 1569 | TFA_CLIENT_NEXTREFRESH | 10039 sets it to `clientclock+30`. The HUD timer redraws when `clientclock >= it`. 10042 resets it to 0 |
| 1570 | TFA_PARTYLIST_SCROLLPOS | 10094 (party list scroll) |
| 1571 / 1572 | TFA_SUMMARY_SCROLLPOS / TFA_RAID_SUMMARY_TOOLTIP | no refs |
| 1573-1580 | TFA_CLIENT_NAME0..7 (strings) | Written by 10042 (8 string args), read by 10034 |
| 1581 | TFA_STATS_IN_ACTIVE_RAID | Written by 10102 (arg); 1 adds a "Current" column and the live timer |
| 1582 | TFA_STATS_TAB | 0 = all stats, 1 = party (set by 10102 from 1581) |
| 1583 | TFA_STATS_ALL_SELECTED_CATEGORY | dbrow of table 264; default 17133 (Overall) |
| 1584 / 1585 | TFA_STATS_PARTY_TIMES_TABLE_ID / PARTY_STATS_TABLE_ID | Dynamic-table ids used by 10136, 10137 and 10139-10141 |
| 1586 / 1587 | TFA_STATS_TARGET_WIDTH / HEIGHT | no refs |

The party UIs also use the generic raids varcs RAIDS_COUNT 178, RAIDS_LISTSORT_LIST 180, RAIDS_LISTSORT_DETAILS 181,
RAIDS_COUNT_2 1087 and CLANWARS_OPPONENTCHANGETIME 59 (the party-list age base). No other RAIDS_*/COLLECTION_* symbols
are TFA-specific. RAIDS_ITEMLOSS_WARNING (varbit 27) is generic.

### 1.4 Invs [R]
1029 TFA_CHESTS (size 6), 1030 TFA_MIDRAID_SUPPLIES_SHOP (size 10), 1031 TFA_SUPPLIES_SNAPSHOT_INV (size 28).

### 1.5 Interfaces and components [R]
- **973 TFA_CHESTS**: 0 UNIVERSE, 1 FRAME (onLoad 227 "The Fractured Archive"), 2 CONTENT, 3 COM_3 (model 62364, hidden),
  4 COM_4 (model 62366), 5 BANK (op6 "Bank-all", clickmask 64), 6 BANK_ICON, 7 INVENTORY (op1 "Take-all", mask 2),
  8 INVENTORY_ICON, 9 DISCARD (op6 "Discard-all", mask 64), 10 DISCARD_ICON, 11 ITEMS (layer). onOp 489 only plays a
  sound; the server handles every op. No client script references 973:11 or inv 1029, so the server must populate the item
  layer, probably with a generic inv script such as 149 [I].
- **974 TFA_HUD**:
  - 0 UNIVERSE, 1 TRIGGER_GENERIC, 2 ATMOSPHERIC, 3 DODGER, 4 CONTENT, 5 CONTENT_NAMES, 6 CONTENT_DETAILS,
    7 CONTENT_SPECTATORS.
  - Per slot k = 0..7: `_k_CONTAINER` (8 + 4k), `_k_RING` (9 + 4k, sprite 2138), COM (10 + 4k, sprite 1059),
    `_k_FILLER` (11 + 4k).
  - 41 TOOLTIP, 42 STATUS, 43 RAID_STATE, 44 RAID_TIMER (text "00:00:00", font 496).
- **975 TFA_LOBBY**: 0 UNIVERSE (onLoad 10045), 1 DODGER, 2 CONTENT (onLoad 991), 3 CONTENT_BACKGROUND (sprite 297),
  4 NAMES_HEADER (text), 5 NAMES (text). No script writes 975:5, so the server sets it with IF_SETTEXT [I].
- **976 TFA_PARTYDETAILS** (69 comps, full list in `Components.java`):
  - Header: 1 PAUSEBUTTONS, 3 FRAME, 4 BACK, 7 EXTERNAL_SPECTATE_TOGGLE, 8 PREFERRED_MODE, 12 RAID_MODE ("Mode: Entry",
    hidden).
  - Tabs: 13 TABS, 14 MEMBERS_TAB, 15 APPLICANTS_TAB.
  - Members: 18 MAIN, 19 MEMBERS, 21 MEMBERS_HEADER, 23-33 MEMBERS_NAME / COMLEVEL / ATTACK / STRENGTH / RANGED / MAGIC /
    DEFENCE / HITPOINTS / PRAYER / COMPLETIONS / COMPLETIONS_PRACTICE, 35 MEMBERS_LIST.
  - Applicants: 36 APPLICANTS (hidden), 38 APPLICANTS_HEADER, 40-50 APPLICANTS_* columns (49 is PRACTICE and 50 is
    COMPLETIONS, the reverse of the member order), 52 APPLICANTS_LIST.
  - Footer: 53 BOTTOM, 54 POPUP, 55 REFRESH, 56 ACTION, 57 PREFERRED_SIZE, 58 UNBLOCK, 59 TOOLTIP,
    60 PREFERRED_COMPLETIONS, 64 PREFERRED_COMPLETIONS_TEXT ("Preferred Completions: 100"), 68 PREFERRED_SIZE_TEXT
    ("Preferred Party size: 8").
- **977 TFA_PARTYLIST**: 0 UNIVERSE (onLoad 10087), 1 CONTENTS (pause-button host), 2 FRAME (onLoad 227 "The Fractured
  Archive Party List"), 3 MAIN, 4 LISTLAYER, 6 SORTAREA, 8-12 SORT_MEMBERS / NAME / COMPLETIONS / RAIDDIFFICULTY / AGE,
  13 LISTAREA, 14 LIST, 15-59 rows `_00`..`_44` (enum 6058: row index → component), 60 SCROLLBAR, 61 REFRESH,
  62 MYPARTY, 63 FILTER, 64 TOOLTIP.
- **978 TFA_SPECTATE_HUD_INV**: only 0 ITEMS (layer). No client script references it, so the server populates it. The inv
  is probably 1031 or the spectated player's inventory [?].
- **979 TFA_STATS** (47 comps): 0 INFINITY, 1 UNIVERSE, 2 TOOLTIP, 3 FRAME, 4 CONTENT_CONTAINER, 5-19 BOOK_* sprites,
  21 "Alliance Log" title text, 24 ALL_STATS, 26 PARTY_STATS, 29 CLOSE_BUTTON, 30 SIDE_TABS_CONTAINER,
  31 TOP_TABS_CONTAINER, 33 ALL_STATS_LOADING_SPINNER (hidden), 34 ALL_STATS_TABLES, 35-42 PARTY_PANEL_MVP*,
  43 PARTY_PANELS_CONTAINER, 44 PARTY_PANEL_STATS_BACKGROUND, 45 PARTY_PANEL_STATS, 46 PARTY_PANEL_TIMES. There is no
  static onLoad; the server starts it with clientscript 10102 [I].

### 1.6 Other TFA symbols
- Objs [R]:
  - 34656 TFA_OCELLUS_P2_SWORD_DISCHARGE, 34657 TFA_START_MESSAGE, 34658 TFA_RECORDS_ROOM_KEY.
  - 34659/61/63/65/67 TFA_CAPE_T1..T5 "Living record (tier n)". Param 2722 = completions required: 100 / 500 / 1000 /
    1500 / 2000. Script 10173 messages "…completions of the Fractured Archive to gain that Record. Current completions: ".
  - Motes 34671-34678, 34679 TFA_ZEAL_REWARD, 34722/34723 ARCHIVISTS_FOLIO, 34729 TFA_MIDRAID_SUPPLIES_NOTE,
    34730 TFA_MIDRAID_SUPPLIES_LEFTOVERS.
- Jingles: 754-763 (boss and lobby music) and 1167 TFA_BOSS_DEFEATED. Archive-17 and 18 gamevals carry the same names
  (887-896, 886).
- Gameval19 (11-21: TFA_EZEKIEL_FINALE, VAULT, OCELLUS, NEXUS, LOBBY, BORK, XARTH, DAGONHAI, JELLY, MOIA, EZEKIEL) pairs
  with dbrows 16990-17000 of table 234 Ambient_Sfx, whose columns are stripped. These are per-room area keys [I].
- Seqs: 86 TFA_*. SpotAnims: 47 TFA_*.
- Music dbrows 17001-17010 (table 44) [R]:

  | dbrow | track | midi | variable (col 5) |
  |---|---|---|---|
  | 17001 | Into the Archive (lobby) | 891 | (27, 22) |
  | 17002 | The Archive Guardian | 887 | (27, 23) |
  | 17003 | Blade of Forgotten Justice (Ezekiel) | 890 | (27, 24) |
  | 17004 | Bork's Beats | 888 | (27, 25) |
  | 17005 | Triple Threat (Dagon'hai) | 889 | (27, 26) |
  | 17006 | Hellatine (Jelly) | 895 | (27, 27) |
  | 17007 | Daughter of Darkness (Moia) | 892 | (27, 28) |
  | 17008 | Forged Through Fire (Xarth) | 896 | (27, 29) |
  | 17009 | Artifacts of Old (Ocellus p1) | 893 | (27, 30) |
  | 17010 | Ascension (Ocellus p2) | 894 | (27, 31) |

  All unlock hints read "in the Fractured Archive.".
- Mid-raid shop, omnishop table 39 [R]:
  - 17150 "Alliance Supplies" uses inv 1030 and currency 17149 ("Supply Credit(s)", which is varbit 16381). 17151 is the
    failure variant with an extra "Please try harder…" text and adds the LEFTOVERS item.
  - Stock (table 40, item → price in credits): 34729 note 0, 34730 leftovers 0, 32312 krill 1, 391 manta ray 2,
    32336 halibut 2, 141 prayer potion(2) 1, 2434 prayer potion(4) 2, 6685 Saradomin brew(4) 3, 3024 super restore(4) 3,
    171 (2-dose ranging potion) 2, 12699 (2-dose combat-type potion) 2. The last two item names come from the dbrow gameval
    names [I].

---

## 2. Varbit encodings and the scripts that use them

### 2.1 HUD 974: party orbs, names and spectate (onLoad 10031)
- 10031 builds the text and graphic children of each slot (10032). It then calls 10034 and registers three refresh hooks
  on UNIVERSE, all running 10033 → 10034 [R]:
  - **onTimer**: redraws only when `clientclock >= varc 1569`.
  - **onVarTransmit**: varps **5910, 5911, 5912**. The server must send these varps; the client watches nothing else.
  - **onStatTransmit**: stat 3 (Hitpoints), so the player's own orb tracks local HP.
- **10039 (per slot)**: args are slot, P value, container, filler, tooltip component and name. [R]
  - **P == 0**: the slot is empty and its container is hidden.
  - **Otherwise**: the text is the name's first letter, or "-" if the name is empty or "null". The slot shows **"Me"**
    when `slot == PARTYSLOT-1` and SPECTATE_MODE != 2. PARTYSLOT is 1-based and 0 means none [I].
  - **P == 31**: filler sprite 3575 at full 26 px, white text, and a "Spectate" op only in spectate mode 2.
  - **P == 30**: red text (0xFF0000), filler height 0 (dead or empty), no op.
  - **P == 1..29** (meaningful up to 27): white text, filler sprite 4548, filler height = `min(26, P-1)` px. So
    **P = 1 + round(26·hp/maxhp)** (HP fraction). A "Spectate" op is added when SPECTATE_MODE != 0. This orb
    interpretation is [I].
  - **Own slot ("Me")**: 10041 ignores P and computes the filler from the local `stat(3)/stat_base(3)` scaled to 26,
    with a minimum of 1 px while HP > 0.
  - Mouse-over shows the full name. Clicking "Spectate" runs 10040, which sends **IF_SCRIPT_TRIGGER** (rsprot
    `IfScriptTrigger`, cs2 opcode 2929) with crc **-1813229297**, component **974:5 CONTENT_NAMES**, obj -1 and args
    `(slot, 1)` "ii". The Room button sends `(-1, 1)`.
- **10034** [R]:
  - **HIDEPLAYERS = 1**: the names column is hidden (size 0). Otherwise its width is based on the visible count.
  - **SPECTATE_MODE**: 0 = not spectating. Any non-0 value adds a "Spectate Room" button (10040 with slot -1). 2 = external
    spectator: adds a **"Leave"** button, never shows "Me", and suppresses Skip [I: 1 = in-raid spectating of
    teammates, 2 = external].
  - **SKIP_PHASE_MODE**: 0 = hidden. Otherwise a "Skip Phase" button appears unless SPECTATE_MODE == 2. It is opaque when
    the value is 1 and transparent (trans 150) for other values [I: 1 = can vote, 2/3 = voted or waiting]. Clicking runs
    10035, which sends IF_SCRIPT_TRIGGER crc **1373305897**, component **974:1 TRIGGER_GENERIC**, arg **1** ("i").
    "Leave" sends the same trigger with arg **2**. After the click the button's op is cleared locally.
  - **10043/10044 rings**: slot k's ring is sprite **8612** when `SPECTATE_MODE != 0 && SPECTATE_SLOT-1 == k`, else 2138.
    SPECTATE_SLOT is 1-based.
  - **NUM_EXTERNAL_SPECTATORS > 0**: shows CONTENT_SPECTATORS (eye sprite 4909 plus the comma-formatted count, tooltip
    "Spectators"). When 0 it is hidden.
- **Names (server-run 10042)**: signature `ssssssss` (8 strings → varcs 1573..1580 in order, NAME0 first). It also resets
  NEXTREFRESH [R].
- **Raid timer (server-run 10036)**: signature `ii` = (ticks, frozen) [R].
  - ticks == -1: RAID_TIMER shows "-".
  - Otherwise the client counts in client cycles (`ticks*30 - clientclock` offset), formatted by 10038 with
    OPTION_PRECISE_TIMING 11866 and OPTION_SEPARATE_HOURS 11890.
  - frozen == 1: draws once and stops. frozen == 0: ticks every client frame.
  - So ticks = elapsed game ticks at send time [I].

### 2.2 Lobby overlay 975 (onLoad 10045 → 10047)
NAMES_HEADER text depends on **TFA_CLIENT_PARTYSTATUS**, re-evaluated on varp 5910 transmit [R]:

| value | text |
|---|---|
| 0 | "No Party" (orange 0xFF9C1F) |
| 1 | "Party" |
| 2 | "Step Inside Now!" (colour flashing every frame via 2303) |

### 2.3 Party details 976 (onLoad 10049; server-run 10053, 10058, 10060, 10072) [R unless marked]
- **10049** shows "Loading...". It then creates hidden pause-button children under **976:1 PAUSEBUTTONS** (10 + 24 + 7 + 7
  = 48). Clicks reach the server as **RESUME_PAUSEBUTTON** on 976:1 with sub-id:
  - 0-9: enum 6057 buttons. 0 BACK, 1 REFRESH, 2 UNBLOCK, 3 PREFERRED_SIZE, 4 PREFERRED_COMPLETIONS, 5 PREFERRED_MODE,
    6 ACTION (Apply / Withdraw / Leave / Disband / Refresh / Unblock, set by 10061), 7 MEMBERS_TAB, 8 APPLICANTS_TAB,
    9 EXTERNAL_SPECTATE_TOGGLE.
  - **10+row**: kick member row (op "Kick", or "Quit Party" on own row 0) (10056).
  - **18+row / 26+row**: "Move up" / "Move down" (10054).
  - **34+row**: accept applicant. **41+row**: reject applicant (10073).
  - The preferred size/completions inputs (10065/10070) run a count dialog and then resume the pause button.
- **10053(int viewerRole, string row)**: one call per member row (row index = varc 178, which it increments).
  - Row string: `name|combat|att|str|rng|mag|def|hp|prayer|completions|practiceCompletions` ('|'-separated, via 632).
    Empty fields render "-".
  - The first call titles the frame "Party of <name>" (or "Party Details").
  - viewerRole == 2 (leader) enables Kick/Quit and Move up/down [I: 2 = leader].
- **10058(string row)**: applicant row, same field layout, written into the APPLICANTS_* columns (row = varc178-8).
- **10060(role, prefCompletions, mode, prefSize, spectateFlag, tab)**: settings and footer.
  - mode is rendered through enum **6051**: 0 "Normal", 1 "Practice".
  - spectateFlag 1 → op "Enable spectating" and tooltip "Spectating disabled". 0 → "Disable spectating". Leader only
    (role 2).
  - tab 0 = members, 1 = applicants.
- **10072(int buttonIdx, string label)** relabels an enum-6057 button [I].

### 2.4 Party list 977 (onLoad 10087; server-run 10094)
- **10087** clears rows and shows "Loading...". It creates pause-button children on **977:1 CONTENTS** from enum 6059:
  0 REFRESH, 1 MYPARTY ("Make Party"/"My Party" from varp 5913), 2 FILTER ("Friends/Clan Only", toggled look from varbit
  16380). It hooks varp transmits 5913 → 10090 and 5907 → 10092 [R].
- **10094(int rowIdx 0..44, string)**: rowIdx maps to a component through enum 6058 [R]. Fields:

  | # | field |
  |---|---|
  | 1 | leader name |
  | 2..8 | other members, used only for the friends/clan filter |
  | 9 | member count |
  | 10 | max size, shown as "9/10" |
  | 11 | preferred completions ("0" → "-") |
  | 12 | mode/difficulty text |
  | 13 | age, rendered by 1574 relative to varc 59 |

  - With TFA_LOBBY_FRIENDS_FILTER = 1, a row shows only if the player is the leader or some member is a friend/clan mate
    (5217). Rows carry the op "View Party:".
  - Empty-list texts: "No parties are currently listed." and "You have no friends or clanmates in a party.".
  - The 1..13 field order is decoded from the store order in 10094 and the use in 10095 [R/I].

### 2.5 Stats 979
- **Server-run 10102(int inActiveRaid)**: sets varc 1581, sets 1582 (tab) to match, defaults 1583 to 17133 and builds the
  UI. Clientscript 10124 hides the loading spinner [R].
- Selecting a category runs 10123. If the category is new, it sends **IF_SCRIPT_TRIGGER crc -1935749631, component
  979:34 ALL_STATS_TABLES, args (categoryDbrow)**. Column headers are Personal, Global, Global Today, World Today, plus
  "Current" when 1581 = 1 [R].
- **Server-run 10125(category, statRow, partySize, current, flag, personal, global, globalToday, worldToday)**: 9 ints,
  one table row each [R].
  - It reads table 265 columns (packed field ids 1085440 + 16·col).
  - partySize > 0 appends " (n Player)" when ROW_PER_PARTY_SIZE.
  - When IS_TIME, flag == 0 and current > -1 in an active raid, the current cell is a live timer.
  - Arg roles are partly [I].
- 10126 is a **debug-only** op ("DEBUG: Set values", gated by 3720), trigger crc -652584508 on 979:24, args "iii". Ignore
  it.
- Party panels: 10131(int) builds them; 10136(row, v1..v4, name) fills a party stats row; 10137(v1, v2, v3) fills the
  totals row (row 8); 10139(i, i, plus three array args, signature `XWWiiii`) fills the party times. All [R signatures; I
  semantics]. 10110 is the message "You are currently viewing your party stats.".

### 2.6 Vault and lobby chests
- Vault chest NPCs 16736..16751 (chest 1..8, CLOSED/OPEN pairs) are multi-NPCs on **TFA_VAULT_OWN_CHEST (16433)**
  [R]:
  - Chest N shows the "MINE" variant when the value == N: 16733 "Open" while closed, 16735 "Search" while open.
  - Otherwise it shows NOTMINE (16732 / 16734, no ops). Out-of-range values also fall back to NOTMINE.
  - So 16433 = **1-based** index of the player's own chest.
- The closed/open state is chosen by which NPC id the server spawns or changes. **TFA_VAULT_CHEST_0..7_STATE are not read
  anywhere on the client**, so they are server-side or client-unused [R/I].
- Unique chest: 16730 (closed, "Open") / 16731 (open, "Search"). Vault exit: 16690 "Rift" ("Leave", "Quick-leave").
- Lobby rewards chest loc 63299 follows HAS_CLAIMABLE_LOOT (§1.2). The chest interface 973 is server-driven.

---

## 3. Interfaces: onLoad and open scripts

| interface | static hooks | server-invoked scripts (no client caller) |
|---|---|---|
| 973 TFA_CHESTS | 973:1 onLoad 227 (title). Ops → 489 (sound only) | none |
| 974 TFA_HUD | 974:0 onLoad **10031** (args: CONTENT, DODGER, NAMES, 8×(CONTAINER, FILLER), TOOLTIP, DETAILS, SPECTATORS) | **10036**(ii) timer, **10042**(8 s) names |
| 975 TFA_LOBBY | 975:0 onLoad **10045**(DODGER, CONTENT, NAMES_HEADER); 975:2 onLoad 991 | none (NAMES set with IF_SETTEXT [I]) |
| 976 TFA_PARTYDETAILS | 976:0 onLoad **10049**; 976:21/34/38/51 onLoad 714 (scroll) | **10053**(i s), **10058**(s), **10060**(6 i), **10072**(i s) |
| 977 TFA_PARTYLIST | 977:0 onLoad **10087**(LIST, SCROLLBAR); 977:2 onLoad 227; 977:6/13 onLoad 714 | **10094**(i s) |
| 978 TFA_SPECTATE_HUD_INV | none | none found |
| 979 TFA_STATS | none | **10102**(i), **10124**(), **10125**(9 i), **10131**(i), **10136**(5 i, 1 s), **10137**(3 i), **10139** |

Client → server traffic [R]:

| kind | crc | component | args | source |
|---|---|---|---|---|
| IF_SCRIPT_TRIGGER | 1373305897 | 974:1 | int: 1 = skip phase, 2 = leave spectating | 10035 |
| IF_SCRIPT_TRIGGER | -1813229297 | 974:5 | (slot or -1, 1) | 10040 |
| IF_SCRIPT_TRIGGER | -1935749631 | 979:34 | (category) | 10123 |
| IF_SCRIPT_TRIGGER (debug) | -652584508 | 979:24 | (i, i, i) | 10126 |
| RESUME_PAUSEBUTTON | | 976:1 | sub-ids 0-47 (§2.3) | 976 party details |
| RESUME_PAUSEBUTTON | | 977:1 | subs 0-2 | 977 party list |
| RESUME_PAUSEBUTTON | | 977 list rows | | 977 party list |

The rsprot `IfScriptTrigger` decoder needs these signatures supplied by the server.

---

## 4. NPCs [R]
The `stats` order is attack / defence / strength / hitpoints / ranged / magic (NPC opcodes 74-79). 

| id | gameval | name | size | cb | att/def/str/HP/rng/mag | ops / notes |
|---|---|---|---|---|---|---|
| 16657 | TFA_GUARDIAN_START | Inert Guardian | 5 | 0 | – | "Touch". Models 62570, 62571 |
| 16659 | TFA_GUARDIAN | Archive Guardian | 5 | 984 | 250/260/240/**550**/280/250 | Attack. Height 660. Models 62570, 62571, **51447** |
| 16660 | TFA_GUARDIAN_UNSHIELDED | Archive Guardian | 5 | 984 | 250/260/240/**1100**/280/250 | same models incl. **51447** |
| 16658 | TFA_GUARDIAN_DEAD | Archive Guardian | 5 | 0 | – | model 62570 only |
| 16661/16662/16663 | GUARDIAN_BATTERY inactive/active/destroyed | Inert Guardian | 2 | 0 | active: 1/50/1/180/1/50 | active has Attack |
| 16664 | ARCHIVE_GUARDIAN_TENTACLE | Vine | 1 | 0 | – | – |
| 16652 | TFA_EZEKIEL | Ezekiel | 3 | 984 | 250/200/150/**3000**/80/200 | Attack |
| 16653 | TFA_EZEKIEL_SWORD | Animated Sword | 3 | 0 | 1/115/1/80/1/90 | Attack. 16654 noop, 16655 finale vfx, 16656 death sword |
| 16640 | TFA_BORK | Bork | 4 | 871 | 200/300/200/**1500**/200/90 | Attack |
| 16641 | TFA_SMORK | The Smorks | 3 | 640 | 1/220/1/**600**/185/185 | Attack. 16642 TFA_BORK_SMORK_HP_UI_HANDLER (invisible, cb 1) |
| 16643/16644 | TFA_DAGONHAI_LEADER (/_UNSHIELDED) | Dagon'hai Elder | 2 | 427 | 1/100/1/**640** (unshielded **7**)/260/290 | Attack |
| 16645-16648 | DAGONHAI_MINION_1/2 (/_UNSHIELDED) | Dagon'hai Monk | 2 | 427 | same as the Elder (640 / 7 HP) | Attack |
| 16649 | TFA_DAGONHAI_FOCUS | Cauldron | 3 | 0 | HP 1000 | op "hidden" |
| 16650 | TFA_DAGONHAI_DEMON | Imp | 1 | 168 | 250/50/195/44/66/125 | Attack. 16651 Demonic rift (HP 20) |
| 16665 | TFA_JELLY_BOSS | Dreadnoth the Jellied | 5 | 888 | 280/125/285/**2100**/250/220 | Attack |
| 16666 | TFA_JELLY_MINION | Jelly | 1 | 101 | 200/100/160/75/1/100 | Attack |
| 16667 | TFA_MOIA | Moia | 3 | 1004 | 1/195/1/**1900**/200/350 | Attack |
| 16670 | TFA_MOIA_MINION | Undead binder | 1 | 135 | 1/225/170/95/1/50 | Attack |
| 16684 | TFA_XARTH | Xarth | 3 | 912 | 250/170/225/**2600**/220/350 | Attack. Height 460 |
| 16687/16688 | TFA_XARTH_CORPSE (/_DOUSED) | Corpse | 1 | 89 | 50/100/40/62/1/100 | Attack. 16685/16686 rubble (invisible) |
| 16671 | TFA_OCELLUS_INTRO | (multi on 16384) | 3 | – | – | → 16672 Hooded Figure (Talk-to) / 16673 Ocellus (Talk-to, Proceed) |
| 16674 | TFA_OCELLUS_P1 | Ocellus | 3 | 1121 | 1/215/1/**2500**/250/350 | Attack |
| 16675 | TFA_OCELLUS_P2 | (null) | 3 | 1197 | 325/250/250/5250/300/300 | Non-interactable, op "hidden" |
| 16676 | TFA_OCELLUS_P2_SWORD_HITBOX | The Blade | 3 | 0 | 1/250/1/1000/1/220 | Attack. Height 900 (the P2 hitbox NPC) |
| 16677 | TFA_OCELLUS_P3 | Ocellus | 3 | 1197 | 325/250/250/**5250**/300/300 | Attack. Models 62671, **51448** |
| 16682 | TFA_OCELLUS_P3_ANIM_DEBUG | Ocellus | 3 | 1197 | – | Model 62671 only (no 51448) |
| 16678/16679/16680 | P3 bomb portals | Void Rift / Void Siphon / Void Augmenter | 1 | 138 | 1/175/1/44/1/300 | Attack |
| 16681 | TFA_OCELLUS_P3_VOID_VINE | Void Vine | 1 | 0 | HP 44 | Attack |
| 16683 | TFA_BLADE_NPC_VAULT_ENTRY | The Blade | 3 | 0 | – | Commune, Travel |
| 16761 / 16762 | TFA_MIDRAID_SUPPLIES_NPC (/_INACTIVE) | Quartermaster | 1 | 0 | – | Resupply, hidden, Talk-to / Talk-to, hidden |
| 16692 | TFA_RECORD_KEEPER | Record Keeper | 1 | 0 | – | Talk-to, hidden |
| 16693 | TFA_WIZARD_APPRENTICE | Wizard apprentice | 1 | 0 | – | Talk-to |
| 16696 / 16697 / 16698 | ARMACUS_SELLES / EVA_CASHIEN / TFA_IVY_SOPHISTA | | 1 | 126 / 126 / 0 | – | Talk-to |
| 16699 | TFA_MESSENGER | Guardian of Armadyl | 1 | 0 | – | Talk-to |
| 16700 | TFA_ROOM_NAVIGATOR | Alliance Navigator | 1 | 0 | – | Follow, Quick-follow |
| 16704 | TFA_LOBBY_BANKER | Banker | 1 | 0 | – | Talk-to, Bank, Collect |
| 16705 | GENERAL_MONTOYA | General Montoya | 1 | 92 | – | Talk-to |
| 16691 | TFA_GATHERING_SPRITE | Sprite | 1 | 0 | – | Look-at |
| 16694/16695, 16706-16723 | lobby alliance druids / builders / Temple Knights / Guardians of Armadyl | | 1 | | – | ambient, no ops |
| 16730-16751 | vault chests | §2.6 | | | | |
| 16631-16639, 16763-16771 | pets and POH pets | Orion, Xari, Archie, Chubbles, Cole, Zeke, Mimi, Dork | | | | Talk-to, Metamorph, Emote, Pick-up |

Cutscene NPCs: 16727-16729 and 16752-16760 (Knights of the Round Table, King Arthur). Ending NPCs: 16701-16703, multi on
16426. Invisible helpers: 16668 / 16669 (1×1 / 2×2).

---

## 5. Map [R from map locs; room identification is I]
All TFA content lives in map squares x 63..65, z 86..88, plus (64,90). No TFA_ locs exist elsewhere except the entrance.
NPC spawns are not in the cache.

| square | world box (level) | room | key locs (x, z, level) |
|---|---|---|---|
| (64,86) | 4099..4156 × 5504..5567 (L0; L1 decor) | **Lobby** | Grouping Board 62767 (4138,5547,0) "Inspect"; Archive Door 62768 raid entry (4123,5562,0) "Enter"; Opening 63009 lobby exit (4126,5505,0) "Enter", with 63010 on L1; bank chests 63274 (4134,5553) / 63275 (4121,5553); Alliance records scoreboard 62773 (4135,5544); rewards chest 63299 (4136,5551); supply chest 62788 (4137,5533); Strategy board 63083 (4116,5547); Ascension rocks 63269-63271 (4110-4117, 5532-5539) |
| (64,87) | 4096..4159 × 5568..5631, **four level copies L0-L3** | **Nexus** plus the **Archive Guardian** arena | Battery bases at (4114/4139, 5591/5604) on every level; side doors per section: West 63088/63096/63102 at x 4113 and East 63091/63099/63105 at x 4141, z 5584/5597/5610 ("Enter", "Quick-enter"); records-room door multi 63279 (4123,5572); records room on L1/L3 (cape-claim multis 63287-63295 at 4099-4106 × 5571-5578, pond 63283, exit 63282 (4109,5573)); supply wheelbarrow 62787 / chest 62788 (4120-4121, 5578-5579) and guardian corpse 62800 (4125,5597) on L2/L3; Passageway 62766 (4124,5625, L1/L3) towards Ocellus |
| (63,86) | 4032..4090 × 5504..5561 | **Ezekiel** | training dummies, transporter 63087 (4073,5535), passageway 62765 (4085,5533), dark door 63108 (4086,5533) |
| (63,87) | 4032..4094 × 5568..5622 | **Dagon'hai** (one Dagon'hai book at 4049,5597 and scorch at 4066,5599 [I]) | transporter 63087 (4079,5599), passageway (4088,5597) |
| (63,88) | 4032..4091 × 5632..5685 | **Moia** | transporter 62610 (4074,5663), passageway (4086,5661) |
| (65,86) | 4164..4217 × 5509..5561 | **Xarth** | Xarth portal 62745 (4205,5532), vivarium entrances, transporter (4178,5535), passageway (4169,5533) |
| (65,87) | 4161..4217 × 5577..5621 | **Bork and the Smorks** | grass kit, ladder 62823 (4174,5589), transporter (4180,5599), passageway (4164,5597) |
| (65,88) | 4160..4216 × 5632..5688 | **Dreadnoth (soul prison)** | octagram (4191,5663), transporter 63134 (4177,5663), portals 63133, passageway (4169,5661) |
| (64,88) | L0 4102..4152 × 5632..5695; L1 4105..4146 × 5632..5692 | **Ocellus** antechamber and arena | L0 plinths 62963 at x 4120/4134 × z 5637/5644/5651; L1 artefacts 62964-62969 on those plinths; entrance 62950/62951 (4126-4127,5660,1); The Blade loc 62706 (4127,5673,1); final braziers (4118/4136 × 5664/5682, L1) |
| (64,90) | 4096..4159 × 5760..5823 (L0-L3) | **Void reward vault** | star skybox L0-L3; walkways/platforms/braziers L1 (4123-4132 × 5775-5809); entrance portal 62990 (4127,5772,2); steps 62989 (4127,5802,2) |

Notes:
- Ocellus quadrant walls/platforms, the in-raid scoreboard, vault exit loc 63297, loot wheelbarrow 63302 and the loot
  tracker source are **not placed** statically. The server spawns them.
- Vault-exit NPC 16690 placements found at (2104-2105, 3906-3909) are unrelated to TFA geometry (probably a reuse) [?].
- **Entrance**: loc 62542 LUC2_RAID_ENTRY_MULTI at **(4050, 4567, 0)**, square (63,71), in the LUC2_GT (Guthixian temple /
  chasm) cave kit. It is a multi on WGS_TFA_SHORTCUT_UNLOCKED:
  - 0 or 2 → 53638 "Blocked cavity".
  - 1 → 62543 "Opening" ("Crawl-through").
  - Level 1 holds loc 54113 LUC2_RAID_ENTRY2 "Opening".

  This is the Chasm of Tears-side shortcut into the lobby [I]. No other TFA entrance loc (name containing Archive or
  Fractured) exists outside the raid squares. The ordinary route (e.g. through Juna, TFA_JUNA_DIALOGUE) is server- or
  dialogue-driven [?].

---

## 6. Did the cache take the 3 Nov 2026 clickbox update? **Very likely yes**
- **[R]** The Archive Guardian (16659 and 16660) carries an extra third model **51447**. Ocellus P3 (16677) carries an
  extra model **51448**.
  - Neither model appears on any other NPC.
  - The debug copy of P3 (16682) and the inert or dead Guardians lack them.
  - Both are tiny models (180 and 175 bytes; type-3 header: 17 vertices, 16 faces, per-face alpha flag set). Pets that copy
    the boss models (Archie 16634, Orion 16763) also lack them.
- **[R]** JS5 per-group version stamps (unix time): NPC config group 2/9 and models 51421-51459 (including 51447/51448)
  were last changed in the **2026-11-02 16:09-16:10 UTC** build. Every other TFA model (62xxx) dates from
  **2026-10-19** (launch). Script and interface groups are also stamped 2026-11-02.
- **[I]** The extra low-poly, alpha-capable models on exactly the two named NPCs are the added clickboxes, and they arrived
  in the 2 Nov pre-release build that shipped on 3 Nov. This cache only has stamps; it does not hold the 19 Oct NPC
  definitions. So "before vs after" for the NPC defs is inferred from the model stamps and from the debug variant lacking
  the model, not from a diff. NPC opcodes have no dedicated clickbox field; the decoder accepts every opcode.

---

## 7. DB tables
- **Stripped** (rows exist, no columns) [R]:
  - 136 (17028-35), 137 (17036-40), 138 (17041-43), 139 (17044-47), 140 (17048-50), 141 (17051-53).
  - 259 (17054-61, one per room: NEXUS, EZEKIEL, XARTH, DAGONHAI, BORK, MOIA, JELLY, OCELLUS).
  - 260 (17062-64, nexus door sections 1-3).
  - 261 (17065 unique, 17072 folio, 17075 motes, 17081 resources).
  - 262 (17066, 17073, 17076, 17082, 17084, 17090, 17096, 17102, 17111).
  - 263 (17067-17120, item entries).

  The gameval row names in `DbRows.java` give the drop-table structure. Unique entries: obligator, ascension crossbows,
  zeal, Zorya's tome, rondache. Other tables: folio, motes, runes, gems, alchables, herbs/seeds and misc resources. Rates,
  quantities and coords are absent.
- **264 Tfa_Stats_Category** (cols: 0 title, 1 short title, 2 sprite 25×25, 3 associated_room, 4 rows) [R]:

  | dbrow | title | short | sprite | room | rows |
  |---|---|---|---|---|---|
  | 17133 | Overall | Overall | 8360 | – | 17142, 17143, 17145, 17146, 17147, 17148 |
  | 17134 | Archive Guardian | | 8361 | 1 | 17142, 17143, 17144, 17145, 17148 |
  | 17137 | Ezekiel | | 8365 | 2 | same 5 |
  | 17141 | Xarth | | 8363 | 3 | same 5 |
  | 17136 | The Dagon'hai | | 8364 | 4 | same 5 |
  | 17135 | Bork and the Smorks | Bork | 8367 | 5 | same 5 |
  | 17138 | Moia | | 8366 | 6 | same 5 |
  | 17140 | Dreadnoth the Jellied | Dreadnoth | 8362 | 7 | same 5 |
  | 17139 | Ocellus | | 8575 | 8 | same 5 (hidden on the client until TFA_OCELLUS_MET) |

  The associated_room values give the room index order 1..8. Overall has no practice row but adds wipes and failures.
- **265 Tfa_Stats_Row** (cols: 0 title, 1 global_max, 2 personal_max, 3 current_max, 4 is_time, 5 unrecorded_threshold,
  6 auto_persist_mode, 7 row_per_party_size, 8 persistent_mode_variants_enum) [R]:

  | dbrow | title | data |
  |---|---|---|
  | 17142 | Attempts | – |
  | 17143 | Completions | personal_max 2000000 |
  | 17144 | Completions (Practice) | – |
  | 17145 | Deaths | current_max 15, unrecorded_threshold -1 |
  | 17146 | Wipes | unrecorded_threshold -1 |
  | 17147 | Failures | unrecorded_threshold -1 |
  | 17148 | Completion Time | global/personal/current max 30000 (ticks, about 5 h [I]), is_time = true, row_per_party_size = true (one row per party size, shown "n Player") |

- Other populated TFA rows [R]: music (§1.6), omnishop 17149-17162 (§1.6), dynamic-table themes 17012/17013 (table 134),
  drop instant reveal 17026 (table 135), UI highlight ping 17176/17177.

---

## 8. Blockers and open points
1. **Server-only data is stripped.** That covers Ezekiel sword grids, stars, finale rotations, combos and attacks (with
   timings and prayer types), Ocellus barrage patterns, per-room entry coords and facing, nexus door locs/coords and every
   drop table (rates, quantities, scaling). These must come from the Wiki or observation. The cache provides only names
   and row counts.
2. **Varbits with no client reader**: TFA_VAULT_CHEST_n_STATE, TFA_CLIENT_PRIMARY0-7, TFA_CLIENT_CURRENT_PATH,
   TFA_MOIA_SHADOW_SHIELD_AMOUNT, TFA_PREFERRED_MODE. Their encoding cannot be confirmed from the client. Values must be
   chosen behind named config flags (`OPEN`). Moia's shield probably reaches the client through the generic HPBAR_HUD
   varps 5990/5991 [?].
3. **Semantics that are [I]**:
   - P-slot codes 30 and 31 (30 = dead, 31 = special/spectatable).
   - SPECTATE_MODE 1 vs 2.
   - SKIP_PHASE_MODE values above 1.
   - The leader role value 2 in 10053/10060.
   - Arg roles of 10125, 10136, 10137 and 10139.
   - The exact payload for "flag" in 10060.
4. **Script-trigger payloads**: IF_SCRIPT_TRIGGER crcs are server-script crcs. The server must map the crcs 1373305897,
   -1813229297 and -1935749631 to handlers and decode args with the signatures above. Treat them as hostile input.
5. **Inventory layers** for 973:11 (inv 1029, 6 slots) and 978:0 have no client script. The server must run the standard
   inv-draw script and set if-events. Which inv the spectate HUD shows is unknown.
6. **NPC spawns, instance layout and copy semantics** are not in the cache: the four level copies of the nexus, the room
   order and the vault spawn points. The cache gives room geometry only. Six side rooms surround the nexus, the Guardian
   fights in the nexus itself, Ocellus is north and the vault is at (64,90).
