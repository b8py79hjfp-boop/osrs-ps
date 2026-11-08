<!-- Researched 2026-11-08 from cache 2757 (rev 242), the disassembled rev-242 client scripts (RuneLite cache 1.13.6 ScriptLoader + Disassembler, 10,303 scripts) and the static map (cache-dump `map`, `collision`, `find-locs`). Tags: [R] read directly, [I] interpretation, [?] unknown. The helper readers (IF3 hook dumper, loc/npc definition dumper, per-cell flood scripts) were one-off scratch programs and are not in the repo. -->
# The Chambers of Xeric (CoX): client contract from cache 2757 (rev 242)

Sources: generated symbols in `symbols/build/generated/symbols/tp/symbols/*.java` (they equal RuneLite's gameval names; the cache calls
the raid **`RAIDS_*`**, the boss room parts **`OLM_*`**, the quest-ish extras **`RAIDQUEST_*`**); `./gradlew :tools:cache-dump:run`
(`config`, `interface`, `map`, `collision`, `dbrows`, `find-locs`); client scripts disassembled to rs2asm (ids are the cache script ids; the
names used below are mine); RuneLite `runelite/runelite@d62b158` (`InstanceTemplates`, the `raids` plugin) and the public plugins listed in
`runelite-timings.md` for the template identification (chunk references), the Wiki pages in `/tmp/claude-0/ref/wiki-cox` where the cache is
silent (always marked as Wiki). Nothing in this file is code. ToB analogue: `../tob/client-contract.md`; shared rules: `../client-contract.md`.

Each claim has a tag:
- **[R]**: read directly from cache data, script text, RuneLite source or a symbol.
- **[I]**: my interpretation of the script logic or of names, or a Wiki fact.
- **[?]**: unknown.

Packed component ids are `interface << 16 | component`. Vocabulary: the cache has **no separate Challenge Mode NPCs, locs or interfaces** except
the CM scoreboard 21 and the checkbox on the party-details screen; Challenge Mode is the **varbit `RAIDS_CHALLENGE_MODE` 6385** and a map-layout
setting (§7). `RAIDS_*` symbols named `RAIDQUEST_*`, `RAIDS_TEMPLE_*`, `RAIDS_MOUNTAINGUIDE_*` belong to the Mount Quidamortem area and the
"Library" side quest content, not to the instance. `POH_*`, `*_PET`, `CA_TASK_*`, `LEAGUE_*` and `COLLECTION_*` are listed only where they touch raid state.

---

## 1. Symbols

### 1.1 Varps (all `clientCode=0`) [R]
| id | name | notes |
|---|---|---|
| 1427 | RAIDS_PARTY_GROUPHOLDER | No varbits. **-1 = no party**; any other value = the player is in a raiding party. Script 1565 turns the party-list button into "Make party" / yellow "My party"; 1563 hooks a redraw on transmit. RuneLite's `raids` plugin resets its scout state when this changes [R] |
| 1428 | RAIDS_PERM_NONTRANSMIT | Varbits 27 (item-loss warning), 5414 (daily adverts), 5415 (party member id), 5417-5420 (prayer-enhance / overload timers). Persistent, not meant to redraw anything on its own |
| 1429 | RAIDS_PERM_TRANSMIT | Varbits 5421 (guide travel unlocked), 5509 (shared-storage warning dismissed), **6385 (challenge mode)**, **6386 (raid timer)**; also holds the unrelated rigour / augury / preserve unlock bits 1-3 and teleport-warning bits. The HUD redraws when it changes |
| 1430 | RAIDS_TEMP_TRANSMIT1 | Varbits 5423-5428 (leader flag, party size, progress, lobby minimums, channel member). The Raiding Party tab and the party-details screen redraw on it |
| 1431 | RAIDS_TEMP_TRANSMIT2 | Varbits 5431 (party score, 31 bits), 5432 (in dungeon). The HUD and the storage screens redraw on it |
| 1432 | RAIDS_TEMP_TRANSMIT3 | Varbits 5433, 5456, 5457, 5408, 10055. The rewards screen, the HUD and the party details redraw on it |
| 3062 | RAIDS_TEMP_TRANSMIT4 | Varbits 12285 (highest combat), 13069 (ironman sharing), 9539 (scaling), 9540 (scaled party size) |
| 4064 | RAIDS_CUSTOM_PARAMS | Varbits 9541 (remember last scaling), 3288 (remember last party type). No script reader: the leader's last chosen settings, **server-only** [R/I] |
| 4609 | RAIDS_PLAYERSCORE | No varbits. **The viewer's personal points**, all 32 bits; read by 1511 and 4660 (HUD "Lots!" above 2,000,000,000) [R] |
| 1532 | TOTAL_COMPLETED_XERICCHAMBERS | Normal completions. dbrow 4015 (`HISCORES_BOSSES_CHAMBERS_OF_XERIC`); read by 4774, 4778, 10310 and by 1530 [R] |
| 1735 | TOTAL_COMPLETED_XERICCHAMBERS_CHALLENGE | Challenge Mode completions. dbrow 4016; read by 962, 967, 2556, 2563, 4774, 4778, 10310 [R] |
| 4615 / 4616 | COLLECTION_COUNT_RAIDS / _MAX | Collection-log counters (read by 6482, 7041, 7818, 10294...), not raid state |

### 1.2 Varbits (base varp and bit range from `config varbit`) [R]
| varbit | name | varp | bits | width / max | client readers |
|---|---|---|---|---|---|
| 27 | RAIDS_ITEMLOSS_WARNING | 1428 | 0 | 1 | none (server-only popup flag [?]) |
| 5414 | RAIDS_DAILY_ADVERTS | 1428 | 1..4 | 4 / 15 | none; the leader's remaining adverts, passed to script 1524 by the server (§2.5) |
| 5415 | RAIDS_PARTYMEMBER_ID | 1428 | 5..11 | 7 / 127 | none (server-only [?]) |
| 5417 | RAIDS_PRAYERENHANCE_TIMER | 1428 | 12..18 | 7 / 127 | 5923 (buff bar) |
| 5418 | RAIDS_OVERLOAD_TIMER | 1428 | 19..23 | 5 / 31 | 5923 |
| 5419 | RAIDS_PRAYERENHANCE_RATE | 1428 | 24..29 | 6 / 63 | 5923 |
| 5420 | RAIDS_OVERLOAD_TIER | 1428 | 30..31 | 2 / 3 | 5923 |
| 12397 | RAIDS_PRAYER_ENHANCE_TIER | 403 | 26..27 | 2 / 3 | 5923 |
| 5421 | RAIDS_GUIDE_TRAVEL_UNLOCK | 1429 | 0 | 1 | NPC multis 7617 / 7618 / 12428 (§4) |
| 5509 | RAIDS_SHAREDSTORAGE_WARNING_DISMISSED | 1429 | 4 | 1 | 1616 (side panel 551) |
| **6385** | RAIDS_CHALLENGE_MODE | 1429 | 15 | 1 | 2294 (checkbox sprite) |
| **6386** | RAIDS_TIMER | 1429 | 16..31 | 16 / 65535 | 1510, 1511, 2289, 4657, 4658 (HUD, helper) |
| 5423 | RAIDS_CLIENT_ISLEADER | 1430 | 0 | 1 | 1540 (start button), loc multi 49999 (lobby steps) |
| 5424 | RAIDS_CLIENT_PARTYSIZE | 1430 | 1..7 | 7 / 127 | 1540 |
| **5425** | RAIDS_CLIENT_PROGRESS | 1430 | 8..10 | 3 / 7 | 1510, 1540, 2289, 4658, 4659 (§2.2) |
| 5426 | RAIDS_LOBBY_MINCOMBAT | 1430 | 11..17 | 7 / 127 | 1533, 1537 (party details) |
| 5427 | RAIDS_LOBBY_MINSKILLTOTAL | 1430 | 18..29 | 12 / 4095 | 1533, 1537 |
| 5428 | RAIDS_CLIENT_CHANNELMEMBER | 1430 | 30 | 1 | 1520 ("Kick" op on a member row) |
| **5431** | RAIDS_CLIENT_PARTYSCORE | 1431 | 0..30 | 31 / 2147483647 | 1511, 4660 (HUD total points) |
| **5432** | RAIDS_CLIENT_INDUNGEON | 1431 | 31 | 1 | 404, 1605, 1658; RuneLite `raids` uses it as "in the raid" [R] |
| 5433 | RAIDS_LOBBY_PARTYSIZE | 1432 | 0..6 | 7 / 127 | 1533 (preferred party size) |
| 5456 | RAIDS_CLIENT_REWARDINDICATOR | 1432 | 7..9 | 3 / 7 | loc multi 28848 (§5.4) |
| 5457 | RAIDS_CLIENT_NEEDSREWARDBOOK | 1432 | 10 | 1 | 1581 (extra book in the rewards chest) |
| **5408** | RAIDS_MAP_POOL_SELECTED | 1432 | 11..12 | 2 / 3 | 1533, 1540, 2294: **0 Small, 1 Large, 2 Full** (§7) |
| 10055 | RAIDS_DIED | 1432 | 28 | 1 | none (server-only [?]) |
| 12285 | RAIDS_CLIENT_HIGHESTCOMBAT | 3062 | 0..6 | 7 / 127 | 1540 ("Combat level: n" in the party tab) |
| 13069 | RAIDS_CLIENT_IRONSHARING_PERMITTED | 3062 | 7 | 1 | 1602, 1616 (storage warning text) |
| 9539 | RAIDS_SCALING | 3062 | 8..14 | 7 / 127 | 1533 (the "Scaling" row) |
| 9540 | RAIDS_CLIENT_PARTYSIZE_SCALED | 3062 | 15..21 | 7 / 127 | 1540 ("Scaled party size: n" when larger than 5424) |
| 9541 | RAIDS_REMEMBER_LAST_SCALING | 4064 | 0..6 | 7 / 127 | none (server-only) |
| 3288 | RAIDS_REMEMBER_LAST_PARTYTYPE | 4064 | 7..9 | 3 / 7 | none (server-only) |
| 3459 | RAIDS_USINGPRIVATESTORAGE | 115 | 8 | 1 | 1616, 1621 (side panel 551: item ops) |
| 12347 | COX_HELPER_DISABLED | 2856 | 29 | 1 | 4585, 4663, 6716 (options) |
| 12348 | COX_HELPER_SHOW_LESS | 2856 | 30 | 1 | 4654, 4655, 4657 |
| 11866 | OPTION_PRECISE_TIMING | 2856 | 15 | 1 | 2401 (time format in the HUD and the CM scoreboard) |
| 16107 / 16108 | OPTION_CAMERA_EFFECT_SHAMANS_DISABLED / _OLM_DISABLED | 3552 | 21 / 22 | 1 | 6716 (options page; the server must honour it before sending camera shake [I]) |
| 16184, 16187-16192 | RAIDS_BOOK_HOUNDMASTER / CREATUREKEEPER / VANGUARD / VESPULA / REWARDCHEST / TEKTON / VASANISTIRIO _READ | 203 | bits 5, 6, 7, 8, 9, 10, 11 | 1 each | book loc multis 30020, 30022, 30025, 30026, 30031 (§5.5) |
| 5463 | RAIDS_BOOK_VASANISTIRIO | 203 | 31 | 1 | none |
| 11995 | COLLECTION_RAIDS_COX_COMPLETED | 2947 | 0 | 1 | collection log |
| 12746-12892 | CA_TASK_XERICCHAMBERS_* / _CHALLENGE_* / CA_TOTAL_TASKS_* | | | | Combat Achievements |
| 5448 / 5449, 6380, 7254 | PET_INSURANCE_OLMPET, PET_MENAGERIE_OLM, POH_MENAGERIE_MULTIFORM_OLMPET, COLLECTION_ITEM_OLMPET | | | | Olmlet storage |
| 10342 | COLLECTION_ITEM_RAIDS_CHALLENGE_MORPH_LOWERDIGITS | 1854 | 24 | 1 | collection log (CM metamorph) |
| 6617, 16145 | POH_NEXUS_XERIC, CHARGES_XERICS_TALISMAN_QUANTITY | | | | Xeric's talisman / nexus |
| 13388 / 17314 / 10047 | LEAGUE_3_LOCK_BOSS_COX, LEAGUE_COX_CHALLENGE_MODE, LEAGUE_CHAMBERS_UNIQUES | | | | out of scope (Leagues) |

Varbits with **no** script or loc reader: 27, 5414, 5415, 9541, 3288, 10055, 5463 [R]. Their encodings are what the names suggest (§9).
Varbit 12310 is *not* a CoX varbit (`KOUREND_SPAWN`); the symbol `RAIDS_VASANISTIRIO_BOOK_VIS` = 12310 is a **loc id** (§5.5).

### 1.3 Varcs [R]
| id | name | use |
|---|---|---|
| 178 | RAIDS_COUNT | Row counter of the lobby lists (reset by the onLoad scripts, incremented by the row scripts) |
| 179 | RAIDS_SCROLL | Scroll position of the lobby lists |
| 180 | RAIDS_LISTSORT_LIST | Sort state of the party list 499 (0..15, 24, 25) |
| 181 | RAIDS_LISTSORT_DETAILS | Sort state of the party details 507 and the Raiding Party tab 500 |
| 1087 | RAIDS_COUNT_2 | no reader found [?] |
| 59 | (generic) | Set to `clientclock` by 1563: the base of the party age column (script 1574) |
| 223 | (generic) | Redraw throttle of script 1508 (not CoX) |

### 1.4 Invs [R]
| id | name | size | use |
|---|---|---|---|
| 581 | RAIDS_REWARDS | 5 | The chest reward inventory, drawn by 1579 / 1581 into 539:5 (§2.9) |
| 582 | RAIDS_SHAREDSTORAGE | 1500 | Shared party storage. **Read with `invother_*` ops** (1592), i.e. the client expects it as a *mirrored/"other" inventory* (RSProt `UpdateInvFull` combinedId < -70000, see RSProt's doc comment for that class) [R/I] |
| 583 | RAIDS_PRIVATESTORAGE | 120 | Private storage, drawn by 1607 as an 8-wide grid, drag-reorderable (1609 / 1610) [R] |
| 93 | (player inventory, 28) | | drawn by 1614 into the side panel 551 |

### 1.5 Interfaces and components [R] (names from `Components.java`, counts from `Interfaces`)
- **21 RAIDS_CHALLENGE** ("Chambers of Xeric : Challenge Mode" leaderboard, 19 components): 0 BACKGROUNDS (the zebra stripes, filled by 2290), 1 LIST (onLoad 2290),
  2 UNIVERSE, 3 FRAME (onLoad 703 title), 4 CONTENT, 5 SCROLLING, 6 LIST_SCROLLBAR, 7 DISPLAY, 8-10 backgrounds, 11 BRACKET ("Bracket:"),
  12 GLOBAL_TITLE ("Global Best:"), 13 GLOBAL_TIME ("00:00:00"), 14 TARGET_TITLE ("Target Time:"), 15 TARGET_TIME, 16 WORLD_TIMES (5 lines),
  17 WORLD_NAMES (5 lines), 18 WORLD ("World XXX"). Lobby loc: `RAIDS_CHALLENGE_SCORES` 32544 "Score board" op1 "Read".
- **271 RAIDS_STORAGE_PRIVATE** (12): 0 UNIVERSE (onLoad 404), 1 FRAME (onLoad 227 "Private storage"), 2 OCCUPIEDSLOTS, 3 COM_3, 4 CAPACITY,
  5 SHARED (button "Shared"), 6 ITEMS, 7 SCROLLBAR, 8 COM_8, 9 WITHDRAW ("Withdraw all"), 10 BANK ("Deposit all" in the raid / "Bank all" outside), 11 PROMPT (text).
- **550 RAIDS_STORAGE_SHARED** (9): 0 UNIVERSE (onLoad 1589), 1 FRAME (onLoad 227 "Shared storage"), 2 OCCUPIEDSLOTS, 3 COM_3, 4 CAPACITY, 5 PRIVATE
  (button "Private"), 6 WARNING (text), 7 ITEMS, 8 SCROLLBAR.
- **551 RAIDS_STORAGE_SIDE** (9, the side panel that replaces the inventory): 0 UNIVERSE (onLoad 1614), 1 ITEMS (4-wide grid of inv 93), 2 WARNING (layer),
  3, 4 COM, 5 MESSAGECONTAINER (onLoad 392), 6 COM_6 (sprite), 7 TEXT ("Items donated here may be retrieved by other party-members ..."), 8 DISMISS (action "Dismiss").
- **499 RAIDS_LOBBY_PARTYLIST** ("Raiding Parties", 60): 0 UNIVERSE (onLoad 1563 `[499:15, 499:56]`), 1 CANVAS, 2 FRAME (onLoad 227), 3 CONTENTS (host of the
  pause buttons), 4 LISTLAYER, 5, 6, 7-14 sort headers (`SORT_MEMBERS` 7, `SORT_TARGETSIZE` 8, `SORT_NAME` 9, `SORT_COMLEVEL` 10, `SORT_SKILLTOTAL` 11,
  `SORT_SCALING` 12, `SORT_CHALLENGE` 13, `SORT_AGE` 14), 15 LIST, **16-55 rows `_00`..`_39` (enum 1537: row index → component)**, 56 SCROLLBAR,
  57 REFRESH, 58 TOOLTIP, 59 MYPARTY.
- **507 RAIDS_LOBBY_PARTYDETAILS** (the party being viewed, 48): 0 UNIVERSE (onLoad 1516, 18 component arguments), 1 CANVAS, 2 FRAME, 3 CONTENTS,
  4 LISTLAYER, 6 SORTAREA, 7-10 `SORT_NAME`, `SORT_COMLEVEL`, `SORT_SKILLTOTAL`, `SORT_KILLTOTAL`, 11 LIST (member rows), 12 SCROLLBAR, **settings rows:
  13 PREFERRED_SIZE, 17 PREFERRED_COMLEVEL, 21 PREFERRED_SKILLTOTAL, 22 SCALING_LAYER, 23 CHALLENGE_LAYER (checkbox 41 CHALLENGE_CHECKBOX is inside it),
  24 MAPPOOL_LAYER**, **buttons: 32 ADVERTISE, 33 DISBAND, 34 JOIN, 35 BACK, 36 REFRESH**, 29 TIMER, 30 STATS (the stats popup), 37 TOOLTIP.
- **500 RAIDS_SIDEPANEL** (the "Raiding Party" tab, 16): 0 UNIVERSE (onLoad 1538), 1 TITLE ("Raiding Party"), 2 REFRESH (action "Refresh"), 3 LISTLAYER, 5 LOADING
  ("Requesting data..."), 6 SORTLAYER, 7-9 `SORT_NAME`, `SORT_COMLEVEL`, `SORT_SKILLTOTAL`, 10 LIST (the member rows), 11 SCROLLBAR, 12 BOTTOM,
  **13, 14 START (action `*`, shown as "Start raid")**, 15 TOOLTIP.
- **513 RAIDS_OVERLAY** (the in-raid HUD, 4): 0 UNIVERSE (onLoad 1505 `[this, 513:2, 513:3]`), **1 FADER** (full-screen rectangle, hidden by default, fade scripts
  1512 / 1513), 2 DODGER, **3 DATA** (the points / time text).
- **539 RAIDS_REWARDS** ("Rewards", 12): 0 UNIVERSE (onLoad 1579 `[this, 539:5]`), 1 FRAME (onLoad 227), 2 CONTENT, 3, 4 model slots, **5 ITEMS**, 6 BANK (op6
  "Bank-all"), 7 BANK_ICON, 8 INVENTORY (op1 "Take-all"), 9 INVENTORY_ICON, 10 DISCARD (op6 "Discard-all"), 11 DISCARD_ICON.
- **710 HELPER_COX** (the mobile in-raid info panel, 6): 0 UNIVERSE, 1 SAFEZONE, 2 FRAME (onLoad 4690), 3 CONTENT, 4 SHOW_MORE_CLICKTHROUGH, **5 SHOW_MORE**
  (action `*`, "Show more" / "Show less", mouse over / leave → 44 with 1241 / 1240).
- Packed ids used by the scripts: 499:3 = 32702467, 499:15 = 32702479, 499:56 = 32702520, 500:3 = 32768003, 500:10 = 32768010, 507:3 = 33226755, 513:1 = 33619969,
  513:3 = 33619971, 539:5 = 35323909, 710:5 = 46530565, 271:6 = 17760262.
- **Missing interfaces** [R]: there is no lobby overlay (unlike TFA's 975): the lobby state lives in the Raiding Parties / Party Details screens and the loc multis. There is
  no interface for the room completion text (chat only, §2.3) and none for the mid-raid shop (CoX has none).

### 1.6 Other CoX symbols [R]
- **Objs**: `RAIDS_*` ids 20799-21079 and 25754-25765, among them the tools / secondaries (`RAIDS_WOOD` 20799, `RAIDS_VIAL_*` 20800-20996 and 25754-25765, `RAIDS_FISH*_RAW/COOKED`
  20855-20868, `RAIDS_BAT*_RAW/COOKED` 20870-20883, `RAIDS_TIGHTROPE_KEYSTONE` 20884, `RAIDS_THIEVINGCHEST_GRUBS` 20885 / `_BOOK` 20886, `RAIDS_VESPULA_HERB` 20892,
  `RAIDS_GRIMY_* / RAIDS_SEED_*` 20901-20909 for noxifer / golpar / buchu), the lore books (`RAIDS_VASANISTIRIO_BOOK` 20888, `_TEKTON_BOOK` 20890, `_VESPULA_BOOK` 20893,
  `_VANGUARD_BOOK` 20895, `_HOUNDMASTER_BOOK` 20897, `_REWARD_BOOK` 20899), storage items (`RAIDS_STORAGE_1..4` 21037-21039 and 24660, upgrade items 21040-21042 and 24661-24663),
  prayer scrolls / relic (`RAIDS_ANCIENT_RELIC` 21027, `_PRAYERSCROLL` 21034, `_PRAYERSCROLL_PRESERVE` 21047, `_PRAYERSCROLL_AUGURY` 21079, `_SPELLUNLOCK_TABLET` 21046), `RAIDS_PLANK` 21036,
  `RAIDS_CHALLENGE_MORPH` 22386 (CM cape / metamorph), `COX_CHALLENGE_CAPE_T1..T5` 22388-22396, pets `OLMPET` 20851, `TEKTONPET` 22378, `VANGUARDPET` 22380, `VASAPET` 22382,
  `VESPULAPET` 22384, `TEKTONENRAGEDPET` 24656, `VESPULAFLYINGPET` 24658, Xeric's armour `XERIC_HAT/TOP/ROBE/FABRIC/TALISMAN*` 13383-13393. Rewards are not in the client contract.
- **Jingles**: `OLM_BATTLE` **317**. **Music** (db table 44): "Fire in the Deep" (dbrow 2952 `MUSIC_OLMBATTLE`, midi 640, unlock `(17, 9)`, hint "in the Chambers of Xeric."),
  "Upper Depths" (3179, midi 417, `(17, 10)`), "Lower Depths" (2873, midi 775, `(17, 7)`), "Ascent" (2538, midi 682, `(17, 11)`, "at Mount Quidamortem."), "The Desolate Mage" (2653, midi 667, `(17, 8)`). Which room
  plays which track is server logic [?].
- **Seqs** (`Seqs.java`), by part [R]:
  - Olm head 7334-7348 (7334 INITIAL, 7335 SPAWN, 7336-7338 IDLE front / right / left, 7339-7344 turns, 7345-7347 `ATTACK_ACID` front / right / left, 7348 DEATH_FRONT), **enraged (final phase)
    head 7371-7383** (7371-7373 attack front / left / right, 7374-7376 idle, 7377-7382 rotations, 7383 SPAWN_ENRAGED); right hand 7349-7352 (initial, spawn, idle, death); **left hand 7353-7370, 7469**
    (7355 IDLE, 7356 CAST_STUN, 7357 CAST_HEAL, 7358 CAST_EARTHSHOCK, 7359 CAST_EARTHQUAKE, 7360 STUNNED, 7361 STUNNED_IDLE, 7362 UNSTUNNED, 7363-7369 the "gripping" variants of the same, 7370 DEATH,
    7469 TRANSITION_FROM_GRIP); Olm effects 7470 `OLM_SHATTERING_CRYSTAL`, 7471 SHOCKWAVE, 7472 CRYSTAL_EXPLODE, 7504 GENERIC_RANGE_PROJ, 7505 BURNWITHME_TRAVEL; pet 7395-7400.
  - Vasa 7407-7416 (DORMANT, SPAWN, STUN_SPAWN, ATTACK, WALK, HEAL_SPAWN, HEAL_IDLE, HEALED, DEATH, IDLE); pet walk 7985.
  - Vanguard 7427-7447 (DORMANT, SPAWN, WALK, IDLE_BASIC, HEAL, DEATH, then idle / defend / attack × MAGIC 7434-7436, MELEE 7439-7441, RANGED 7444-7446 plus the transitions); pet walk 7984.
  - Vespula 7448-7459 (WALK, IDLE, ATTACK_RANGED, DEFEND, TAKEOFF, FLYING, ATTACK_MELEE_FLYING, ATTACK_RANGED_FLYING, DEFEND_FLYING, LANDING, DEATH_WALKING, DEATH_FLYING); portal 7499-7501 ACTIVE / CLOSING / CLOSED; pet fly 8639.
  - Tekton 7473-7495 (SPAWN 7473, AVIL_TRANSITION_TO_IDLE 7474, IDLE_TRANSITION_TO_ANVIL 7475, IDLE 7476, WALK 7477, READY 7480, READY_DEFEND 7481, **ATTACK_STAB 7482, SLASH 7483, HAMMER_CRUSH 7484**, enraged
    IDLE 7485, WALK 7486, READY 7490, READY_DEFEND 7491, **HAMMER_CRUSH_ENRAGED 7492, ATTACK_STAB_ENRAGED 7493, SLASH_ENRAGED 7494**, DEATH 7495); enraged pet walk 8637.
  - Misc room anims: 7496-7498 thieving chest beast eat / tired / sleeping, 7502 `RAIDS_CENTRAL_CRYSTAL_SHATTER`, 7503 `RAIDS_OLMS_LAIR_ENTRANCE`, 7506 `RAIDS_CRYSTAL_SMASHING`, 7528 `RAIDS_BAREHANDED_BATCATCH`, 8641 / 8643 `RAIDS_BOSS_1_SWIRL` (vanguard swirl).
- **SpotAnims** (`SpotAnims.java`) [R]: Olm 1337-1363 (`OLM_GENERIC_RANGE_PROJ` 1340, `OLM_WEAK_MAGE_PROJ / IMPACT` 1341 / 1342, `_RANGE_` 1343 / 1344, `_MELEE_` 1345 / 1346, `OLM_FIREWALL_TRAVEL` 1347 / 1348,
  `OLM_BURNWITHME_TRAVEL` 1349, `_TRAVEL_PL` 1350, `_PL_SPOT` 1351, `OLM_CRYSTAL_BOMB_TRAVEL` 1352, `OLM_CRYSTALWAVE_SHATTER` 1353, `OLM_ACID_SPIT` 1354, `OLM_HEALME_SPIT` 1355, `OLM_SHOCKWAVE` 1356, `OLM_CRYSTALROCK_FALLING` 1357,
  `OLM_CRYSTAL_EXPLODE` 1358, `OLM_PLAYERSWAP_0..3` 1359-1362, `OLM_HEALME_SPOTANIM` 1363), `RAIDS_OLM_SHATTERCRYSTAL` 1337, `RAIDS_OLM_CENTRAL_SHATTERCRYSTAL` 1338, `OLM_CRYSTAL_BOMB_EXPLODE` 40;
  vanguard 56 / 57 (swirl low / high health), 1331-1334 (magic, range 0-2); Vasa 1327-1330 (magic / range travel + impact); ice demon 1324 / 1325; crabs `RAIDS_LASERCRABS_SHATTERCRYSTAL` 1326;
  Vespula 1364-1368 (poison, vespine hatch, portal attack, heal portal, explode); `XERIC_TELEPORT_SPOTANIM` 1612.
- **Sprites**: `RAIDS_CHALLENGE_ICON` 1627 (the CM marker in the party list), `HEADBAR_OLMTIMER_100_0 / _1` 1417 / 1418 (a head bar sprite pair named for the Olm timer; the headbar config using them is not identified [?]), checkbox
  sprites `CHECKBOX_0..5` 1215-1220 (§2.6).

---

## 2. Varbit encodings and the scripts that use them

### 2.1 Lobby: the party flow in one picture [I from §2.4-2.7]
The raid party **is the player's friends chat ("clan chat") channel**: the Kick op uses `clan_kickuser` after a `clan_getchatrank >= clan_getchatminkick` check (3764), the "Channel member" varbit 5428 gates the op, and the Wiki says
the recruiting board "gives the option to create a raid party in their clan chat; recruit rank and up can join". Flow: board loc `RAIDS_PARTY_RECRUITMENT` 29776 (op "Read") opens **499** (list of advertised parties); "Make party" / a row opens **507**
(the party and its settings); the leader's "Advertise" lists it on the board; the "Raiding Party" tab **500** is opened for members in the lobby and in the raid; "Start raid" there starts the instance (§2.5); the lobby entrance `RAIDS_ENTRANCE_STEPS` 29777 "Chambers of Xeric" (op "Enter") moves players in.
Nothing here is validated by the client; every setting is a server decision [R].

### 2.2 HUD 513 (onLoad 1505) and the progress / timer varbits [R]
- **1505**(this, 513:2, 513:3): registers the two layers with the generic overlay-placement script 4729 (constant 65256; its meaning is [?]) and hooks **onVarTransmit on varp 2856** (`COX_HELPER_*` / precise-timing options) → 4662 → 4663. **4663** (draw): clears 513:3,
  returns immediately when `script 100 == 1` (mobile client) **and** `COX_HELPER_DISABLED == 0` (the mobile helper panel 710 is used instead); otherwise builds a tiled background (sprite 297, +1192), two text children (colours
  0xFF981F label, 0xFFFFFF value, font 495, shadow) and hooks **onVarTransmit on varps 1431, 1432, 1429** → 1510.
- **1511** (the text): label column `Total:` / `<player name>:` / `Time:`; value column: **varbit 5431** with thousands separators (`Lots!` is not used here), **varp 4609** with separators (`<col=ffa82f>Lots!</col>` at ≥ 2,000,000,000), and
  **varbit 6386 formatted by 2275** (see below). Sets the panel height from `parawidth` of the strings. 4660 (the mobile helper variant) shows `Lots!` for 5431 > 10,000,000.
- **Timer**: 1510 arms a client **timer script 2289 on 513:3** while `6386 > 0 && 5425 < 5`; 2289 runs every client cycle but acts only when `clientclock mod 30 == 0` (30 cycles = 0.6 s = **one game tick**): it **increments varbit 6386 locally** and
  redraws (1511). So **the client advances the raid timer by itself; the server writes varbit 6386 (varp 1429) to start it and to resync it; one unit = one tick (16 bits, 65535 ticks ≈ 10.9 h)** [R]. 2275 shows `ticks × 0.6` seconds as `m:ss`
  or `h:mm:ss` (hours only when > 0); the precise-timing option (varbit 11866) switches to scripts 2410 / 2411 (centisecond formats) [R/I]. 4657 / 4658 / 4659 update the mobile helper clock every 50 cycles.
- **Progress `RAIDS_CLIENT_PROGRESS` 5425** [R, text from script 1540 for the Raiding Party tab]:

  | value | meaning (tab text) |
  |---|---|
  | 0 | not started: leader sees the "Start raid" button (`isleader == 1`), others read "Waiting for your leader to begin the raid..." |
  | 1 | "Your party is exploring the upper level..." |
  | 2 | "Your party has reached the middle level..." when `5408 == 2` (Full), else "Your party has reached the lower level..." |
  | 3 | "Your party has reached the lower level..." |
  | 4 | "Your party has reached the bottom..." (the Olm room) |
  | 5-7 | "Get out! Get out!<br>GET OUT!" (the raid is over / the countdown); **the HUD timer stops at ≥ 5** |

  Which value a floor writes under Small / Large layouts (two floors) is [?]: 2 and 3 both read "lower" there, so a two-floor run probably uses 1, 2 and 4 or 1, 3 and 4 [I].
- **Fade**: **1512** fades the screen to black (transparency of the rectangle 513:1 goes 255 → 0 over 25 client cycles, i.e. 0.5 s, and it is shown) and **1513** fades it back (0 → 255, hidden at 255), both via 1515; no arguments. Server-run (no client caller) [R]. Where the server uses them (room changes, death, the Olm entrance) is [?].
- **Buff icons**: 5923 draws prayer-enhance (tier from 12397 = 1..3, timer 5417, rate 5419) and overload (tier 5420, timer 5418) buff-bar entries. Overload cycles are 25 ticks (plugin fact, `runelite-timings.md`).
- **Helper panel 710 (mobile)**: 710:2 onLoad 4690 → 4691 draws the frame. The server calls **4653(string rows, int hide)** (no client caller): `hide == 1` hides 710:5; else 710:5 gets the "Show more / Show less" op (varbit 12348 toggled client-side
  by 4654) and the panel text comes from `rows`: a **'|'-separated list of alternating left / right strings** (left aligned label, right aligned value, 12 px per line, font 494) which 4657 renders; with `SHOW_LESS == 1` (12348) only the summary block is shown. The summary block
  is `Time: <varbit 6386 via 4661>` and `Total points: <5431>`, `Your points: <4609>` (4660). Client: 4585 toggles `COX_HELPER_DISABLED` from the options (mobile only, checked by 100) [R].

### 2.3 Chat strings the clients react to [R from RuneLite / plugin captures; see `runelite-timings.md`]
Game-chat type `FRIENDSCHATNOTIFICATION`: `The raid has begun!`, `<Floor> level complete! Duration: </col><col=ff0000>m:ss` (contains "level complete! Duration:"), `Congratulations - your raid is complete!`, `Your completed Chambers of Xeric count is: n` /
`... Challenge Mode count is: n`. Game messages: `The Great Olm rises with the power of acid.` / `... crystal.` / `... flame.`, `The Great Olm is giving its all. This is its final stand.`, `You have been paired with <col=ff0000>NAME</col>! The magical power will enact soon...`, the Olm sphere lines
(`The Great Olm fires a sphere of aggression/magical power/accuracy and dexterity your way. [Your prayers have been sapped.]`). The strings are fixtures for chat tests, not cache data.

### 2.4 Party list 499 (onLoad 1563) [R]
- **1563** hides the 40 rows (enum 1537), clears the list, writes "Loading..." (grey), `varc 178 = 0`, `varc 59 = clientclock`, builds two pause buttons on **499:3 CONTENTS** from **enum 1538: sub 0 = REFRESH (499:57), sub 1 = MYPARTY (499:59)** and hooks **onVarTransmit on varp 1427** → 1565 (label "Make party" / `<col=ffff00>My party</col>`).
- **Server-run 1566(int row, string)** — call it for **all 40 rows 0..39, in order**, with `""` for vacant rows; the layout, sort and scroll are finalised only when the call with row index **39** arrives (§3 note) [R]. Row string (**8 '|'-separated fields**, split by script 632):

  | # | field | column |
  |---|---|---|
  | 1 | leader name (tags kept; op base is the tag-stripped name) | 9 NAME |
  | 2 | member count | 7 MEMBERS |
  | 3 | preferred party size (`"0"` shows a grey `-`; shown as `/ n`; the cell is red when members ≥ preferred) | 8 TARGETSIZE |
  | 4 | preferred combat level (`"0"` → grey `-`) | 10 COMLEVEL |
  | 5 | preferred skill total (`"0"` → grey `-`) | 11 SKILLTOTAL |
  | 6 | scaling (`"0"` → grey `-`) | 12 SCALING |
  | 7 | challenge mode (`"0"` hides the icon, anything else shows sprite 1627) | 13 CHALLENGE |
  | 8 | age in **ticks** as a decimal string (1574 shows `ticks × 0.6` s as `m:ss` and ages it every 50 cycles from varc 59) | 14 AGE |
  | | hidden sort keys: the numeric fields are zero-padded by 1503 | |

  Rows carry the op **"View party:"** (`<col=ff9040>leader</col>`); a click runs 1568, which resumes a pause button on **499:15 (LIST) with the row index (0..39) as sub-id** [R/I: traced through the argument lists of 1567 / 1568]. Sorting is client-local (varc 180, scripts 1570-1573); it sends no packet.
- Row ops are on the dynamic children; the server receives `RESUME_PAUSEBUTTON(499:3, 0|1)` (refresh / my party) and `RESUME_PAUSEBUTTON(499:15, row)` [R/I].

### 2.5 Party details 507 (onLoad 1516) and the Raiding Party tab 500 (onLoad 1538)
- **507 onLoad 1516** (18 component arguments, listed in §3) clears the member list ("Loading..."), `varc 178 = 0`, hides the buttons. The screen is built by three server calls, in this order [R]:
  1. **1517(int, string)** once per member (the int is unused, send 0): row `name|combat|skilltotal|killtotal|<stats>`. The first four fields fill columns 7-10 (`name`, `combat level`, `skill total`, `kill total`); the 5th is the **'|'-separated list
     of the 24 skill levels in the order of enum 681** (Attack, Strength, Ranged, Magic, Defence, Hitpoints, Prayer, Agility, Herblore, Thieving, Crafting, Runecraft, Mining, Smithing, Fishing, Cooking, Firemaking,
     Woodcutting, Fletching, Slayer, Farming, Construction, Hunter, Sailing) which the **"Stats" op (1)** on the row shows in a popup (1521). A second op **"Kick" (6)** is added only when varbit 5428 == 1 and the viewer's friends-chat rank ≥ the kick rank; it runs 3764 → `clan_kickuser` (a friends-chat packet, not a raid packet). Rows turn red
     when the combat level (`5426`) or skill total (`5427`) is below the party minimums (1537).
  2. **1524(int role, int listed, int adverts, string title)** once, after all members: `role` **2 leader / 1 member / anything else applicant-or-outsider**; `listed == 1` (leader only) → button "De-list", else "Advertise (adverts)" with `adverts > 0`, else grey "Advertise (0)";
     leader also gets red "Disband"; a member gets red "Leave"; others green "Join". Always "Back" and "Refresh". `title` goes to the frame (228). It also builds the six settings rows (via 1530): "Preferred party size" (**5433**), "Preferred combat level" (**5426**), "Preferred skill total" (**5427**),
     "Scaling" (**9539**), "Challenge mode" (checkbox), "Map Layout" (**5408**: `Small` / `Large` / `Full`); an empty value renders as `name: ---`. Only for `role == 2` do the rows get a "Set" op (1531 → 1534).
  3. Nothing else: there is no applicant list and no leader-change UI [R].
- **Pause buttons on 507:3** (enum 1539: component → sub-id): **0 JOIN (507:34), 1 ADVERTISE (507:32), 2 DISBAND (507:33), 3 REFRESH (507:36), 4 BACK (507:35), 5 PREFERRED_SIZE (507:13), 6 PREFERRED_COMLEVEL (507:17), 7 PREFERRED_SKILLTOTAL (507:21),
  8 SCALING (507:22), 9 CHALLENGE (507:23), 10 MAP LAYOUT (507:24)**. The "Set" op shows `...` (grey) and **resumes the pause button at once**; 80 client cycles (1.6 s) later script 1535 restores the old text, so **the server must answer a setting change by re-sending 1517 × n and 1524**
  (re-opening the screen) within that time or the old value reappears (1534 / 1535) [R]. The number dialog for the numeric rows is opened by the server after the resume (bounds [?]).
- **Challenge checkbox** (2294, role-dependent sprite): leader → `5408 != 2`: 1219 (`CHECKBOX_4`); `5408 == 2`: `6385 == 1` → 1220, else 1218; non-leader → `5408 != 2`: 1216; `5408 == 2`: `6385 == 1` → 1217, else 1215. So the sprite reflects **Challenge Mode available only
  when the layout is Full** (the Wiki: "If full raids are toggled, the layout is the same as in challenge mode") [R/I].
- **Raiding Party tab 500** (members in the lobby and during the raid): onLoad **1538** `[this, 500:10, 500:11, 500:2, 500:5, 500:6, 500:12, 500:14]` hooks **varp transmits 1430 and 1429** → 1540 (header text `Party size: <5424> Combat level: <12285>` plus `<br>Scaled party size: <9540>` when 9540 > 5424; the status line by `5425`, §2.2; the **"Start raid"** button only when `5423 == 1 && 5425 == 0`).
  Rows are server-built in two steps [R]: **1547(int count)** creates `count` rows (7 children each) and the scroll size; then **1548(int row, string)** for `row` = 0..count-1: `name|combat|skilltotal` (name with tags: op base "Kick" via 1552 is the friends-chat kick again). "Start raid" is a **plain IF_BUTTON1 on 500:14**
  (1543 only plays a sound and shows `---` for 50 cycles); Refresh is an IF_BUTTON1 on 500:2 (action "Refresh") [R/I].
- **Fail-safe** [R]: 1517, 1524, 1547, 1548 and 1566 return silently unless the host slot of their screen has an open sub-interface (`if_hassub` on the slot from enum 900 → 10551312 / 10551374). Open the interface first.

### 2.6 Party-size scaling and "mega-scale" [I]
`RAIDS_SCALING` 9539 (7 bits) is the leader's chosen *scaled party size*; `RAIDS_CLIENT_PARTYSIZE_SCALED` 9540 is what the raid uses; 1540 shows "Scaled party size" only when it exceeds the real party size 5424. RuneLite plugins read 9540 for solo detection
(`== 1`) and 5424 for stun times (`runelite-timings.md`). The formulas behind NPC stats are server data [?].

### 2.7 Storage 271 / 550 / 551 [R]
- **Locs** (§5.3): `RAIDS_STORAGE_HOTSPOT` 29769 "Tiny storage unit" (ops "Private", "Build"), 29770 / 29779 / 29780 small / medium / large (ops "Shared", "Private", "Upgrade"), **37978 "Massive storage unit"** (ops "Shared", "Private", no upgrade), and the lobby unit **30107 "Storage unit"** (op "Private" only).
- **Private (271)**: onLoad **404** `[this, 271:6, 271:7, 271:2, 271:9, 271:10, 271:11, 271:5]`. It reads **inv 583** (120 slots, 8 per row), each slot with ops **1 "Withdraw", 2 "Withdraw-5", 3 "Withdraw-10", 4 "Withdraw-All", 5 "Withdraw-X", 10 "Examine"** and drag-reorder (`1609 / 1610`, `cc_setondrag`);
  `271:2` shows the number of occupied slots; "Withdraw all" (271:9); `271:10` reads **"Deposit all" when varbit 5432 == 1 (in the raid) else "Bank all"**, and the prompt 271:11 explains: inside, "Items stored here **cannot** be taken by other players. Any **non-raiding** items left in here **can** be retrieved outside."; outside, "You must retrieve **all your items** from the private storage before you can re-enter the Chambers of Xeric." The "Shared" button 271:5 switches to 550.
  Hooks: **inv-transmit on 583** (1606) and **varp 1431** (1604).
- **Shared (550)**: onLoad **1589** `[this, 550:6, 550:7, 550:8, 550:2, 550:5]`: **inv 582** (1500 slots) shown as **one entry per object of enum 1666** (152 raid objs: fish / bats raw and cooked 20853-20883, planks 21036, herbs and seeds 20901-20909, vials from 20800 on, ...) with the quantity from `invother_total(582, obj)`;
  ops as the private list. "Private" button (550:5). Hooks: **inv-transmit on 582** (1591) and **varp 499** (varbit 1777: the ironman type, for the warning text 1602).
- **Side panel (551)**: onLoad **1614** `[this, 551:2, 551:7, 551:8, 551:1]` draws the 28-slot inventory (**inv 93**) as a 4-wide grid; ops depend on `3459` (using private storage): shared screen → **"Donate", "Donate-5", "Donate-10", "Donate-All", "Donate-X", "Examine"** for objs in enum 1666 and a single greyed "Donate" otherwise; private → **"Store", "Store-5", "Store-10", "Store-All", "Store-X", "Examine"**
  on every item. The warning layer 551:2 hides when **5509 == 1 or 3459 == 1**; "Dismiss" (551:8) is an IF_BUTTON1 the server answers by setting 5509. The text 551:7 (1602 / 1624): varbit 1777 != 0 (ironman): "As an **Ironman** you **cannot retrieve** items once you have donated them to the party." when `1777 == 2` or `13069 == 0`; "... **may retrieve** items as everyone present is in your group." / "... as you have embarked on this raid alone." (script 5262 decides); non-ironman: "Items donated here may be retrieved by **other** party-members, or **may be lost** if the party dissolves." [R].
- **Client → server**: IF_BUTTON 1, 2, 3, 4, 5 on the item children of 271:6 / 550:7 / 551:1; IF_BUTTON 10 (Examine) is local; IF_BUTTON1 on 271:5 / 550:5 / 271:9 / 271:10 / 551:8; IF_BUTTON drag (`cc_setondrag`) inside 271:6 for reorder [R/I]. The opcode ops use the generic 177 (a 10-cycle fade only).

### 2.8 Challenge-Mode scoreboard 21 (onLoad 2290) [R]
- **2290**(`21:0`, `21:5`, `21:6`) fills the left list from **enum 1915** (13 brackets: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 23, 100 — the team-size upper bounds) with labels from **enum 1914** (`Solo`, `2 players` ... `10 players`, `11-15 players`, `16-23 players`, `24+ players`), each with the op "Select" (script 2291 just scrolls), and writes `World <map_world>` into 21:18.
- **Server-run 2292(int global, int target, int t1, int t2, int t3, int t4, int t5, string teams)** [R]: `global` → 21:13, `target` → 21:15 (both via 2401: `m:ss`, or centiseconds when 11866 == 1); `t1..t5` → the five lines of 21:16 (a time of 0 or -1 prints the placeholder "Grab a team and get Raiding!" in the names column); `teams` is **five '|'-separated name lists** → 21:17 as `1. <names><br>2. ...` and each preceded by `Team: ` when the time is set. Which bracket is shown is [?] (the "Select" op may also reach the server).

### 2.9 Rewards chest 539 (onLoad 1579) [R]
`539:5` draws **inv 581** (5 slots): items 0-2 on the first row, then the rest on the second row (32 + 5 px spacing; 3 and 4 offset by 36 + 10 px); when **varbit 5457 (`NEEDSREWARDBOOK`) == 1** a sixth icon of **obj 20899 `RAIDS_REWARD_BOOK`** is added at the end. Redraw hooks: **inv-transmit on 581** and **varp 1432** (1580). Buttons: 539:8 "Take-all" (op1), 539:6 "Bank-all" (op6), 539:10 "Discard-all" (op6): the same trio as ToB's 23. The chest loc is `RAIDS_REWARD_CHEST` 30028 "Ancient chest" (op1 "Search") and the
loot beam is a per-player multi (§5.4). RuneLite's loot tracker reads inv 581 when interface 539 opens; the standard inv-draw of 581 into 539:5 is done by the script above, so **set the inv before opening the interface** [R/I].

### 2.10 Other client-side readers
`6716` (options) reads 12347, 16107, 16108 only. `1658` (a settings screen) reads 5432. `404` / `1605` read 5432 (§2.7). `5923` reads the buff varbits (§2.2). `962`, `967`, `2556`, `2563`, `4774`, `4778`, `10310` read the completion varps (Combat Achievements / hiscore-style summaries). No CoX script reads the room names: **rooms are not shown in the client at all** (the Wiki's scouting is by plugins).

---

## 3. Interfaces: onLoad and server-run scripts

| interface | static hooks | server-invoked scripts (no client caller) |
|---|---|---|
| 21 RAIDS_CHALLENGE | 21:1 onLoad **2290** `[21:0, 21:5, 21:6]`; 21:3 onLoad 703 (title "Chambers of Xeric : Challenge Mode") | **2292**(7 int + 1 string) |
| 271 RAIDS_STORAGE_PRIVATE | 271:0 onLoad **404**; 271:1 onLoad 227 ("Private storage") | none |
| 499 RAIDS_LOBBY_PARTYLIST | 499:0 onLoad **1563** `[499:15, 499:56]`; 499:2 onLoad 227 ("Raiding Parties"); 499:4, 499:6 onLoad 714 | **1566**(int, string) × 40 |
| 500 RAIDS_SIDEPANEL | 500:0 onLoad **1538**; 500:3, 500:6 onLoad 714; 500:2 mouse leave / repeat → 273 | **1547**(int), **1548**(int, string) |
| 507 RAIDS_LOBBY_PARTYDETAILS | 507:0 onLoad **1516** `[507:2, 507:11, 507:12, 507:13, 507:17, 507:21, 507:22, 507:23, 507:24, 507:7, 507:8, 507:9, 507:10, 507:34, 507:32, 507:33, 507:35, 507:36]`; 507:4, 507:6 onLoad 714 | **1517**(int, string), **1524**(3 int, string) |
| 513 RAIDS_OVERLAY | 513:0 onLoad **1505** `[this, 513:2, 513:3]` | **1512**, **1513** (fades) |
| 539 RAIDS_REWARDS | 539:0 onLoad **1579** `[539:5]`; 539:1 onLoad 227 ("Rewards"); ops 489 (sound only), hover 4158 | none |
| 550 RAIDS_STORAGE_SHARED | 550:0 onLoad **1589** `[550:6, 550:7, 550:8, 550:2, 550:5]`; 550:1 onLoad 227 ("Shared storage") | none |
| 551 RAIDS_STORAGE_SIDE | 551:0 onLoad **1614** `[551:2, 551:7, 551:8, 551:1]`; 551:5 onLoad 392 | none |
| 710 HELPER_COX | 710:2 onLoad **4690**; 710:5 hover 44 | **4653**(string, int) |

Client → server traffic [R/I]:

| kind | component | args | source |
|---|---|---|---|
| RESUME_PAUSEBUTTON | 499:3 | sub 0 refresh, 1 my party | §2.4 |
| RESUME_PAUSEBUTTON | 499:15 | row 0..39 ("View party:") | §2.4 |
| RESUME_PAUSEBUTTON | 507:3 | sub 0-10 (§2.5) | enum 1539 |
| IF_BUTTON1 | 500:14 / 500:2 | | "Start raid" / "Refresh" |
| IF_BUTTON | 271:6 children 1-5 | withdraw ops | §2.7 |
| IF_BUTTON | 550:7 children 1-5 | withdraw ops | §2.7 |
| IF_BUTTON | 551:1 children 1-5 | donate / store ops | §2.7 |
| IF_BUTTON1 | 271:5, 271:9, 271:10, 550:5, 551:8 | shared / withdraw-all / deposit-or-bank-all / private / dismiss | §2.7 |
| IF_BUTTON 6 / 1 / 6 | 539:6 / 539:8 / 539:10 | bank-all / take-all / discard-all | §2.9 |
| IF_BUTTON1 | 710:5 | Show more / less (the client also flips 12348 itself, the server should mirror it) | §2.2 |

**Send-order contracts (silently wrong otherwise)**: party list = 1566 for **every** row 0..39 in order (the layout runs on row 39); party details = 1517 for each member, then 1524; side tab = 1547(n) then 1548 × n; varc 178 is reset by the onLoad scripts so the server re-opens the interface to refresh (the same trick as ToB / TFA).
No `IF_SCRIPT_TRIGGER` hooks exist in any CoX script (no `crc` args to supply) [R].

---

## 4. NPCs [R]

CoX NPC definitions have **combat level 0 and stat array 1/1/1/1/1/1** (only the three Olm parts carry a combat level 549 / 1043 / 750 and the NPC `params` carry bonuses — see `olm.md`): **HP, stats and the scaling by party size and by Challenge Mode are server data**. **There are no Challenge Mode NPC ids**: every id below is used in both modes [R, also stated by the `tekton-reset-tracker` source].
`stats` of the minion types that do have HP: Lux grub (7534-7537) HP stat 125; Olm spawning parts (7550-7552) HP stat 100.

| room (level) | NPC ids (symbol, name, size, ops) |
|---|---|
| Tekton (L1 of the 5280 band) | 7540 `RAIDS_TEKTON_WAITING` "Tekton" (4, no ops, at the anvil), 7541 `WALKING_STANDARD` (4, Attack), 7542 `FIGHTING_STANDARD` (4, Attack), 7543 `WALKING_ENRAGED` "Tekton (enraged)" (4, Attack), 7544 `FIGHTING_ENRAGED` (4, Attack), 7545 `HAMMERING` (4, op1 Attack). Stand seq 7473 (spawn) |
| Vanguards | 7525 `DORMANT` "Vanguard" (3, no ops), 7526 `WALKING` (3, op1 "Attack": the dug-in/downed form), 7527 `MELEE`, 7528 `RANGED`, 7529 `MAGIC` (3, op2 "Attack") |
| Vespula | 7530 `FLYING` "Vespula" (5, Attack), 7531 `ENRAGED` (5), 7532 `WALKING` (5), 7533 `PORTAL` "Abyssal portal" (4, Attack), 7534 / 7535 / 7536 / 7537 Lux grub healthy / sickly ("Feed") / infected ("Feed") / dead (3), 7538 / 7539 Vespine soldier flying / walking (3, Attack) |
| Vasa Nistirio | 7565 `DORMANT` "Rocks" (5), 7566 `WALKING` (5, Attack), 7567 `HEALING` (5, Attack), 7568 `CRYSTAL` "Glowing crystal" (4, Attack) |
| Stone guardians | 7569 `LEFT` / 7570 `RIGHT` "Guardian" (2, Attack), 7571 / 7572 dead forms |
| Muttadiles | 7561 `SUBMERGED` "Muttadile" (5), 7562 `JUNIOR` (3, Attack), 7563 adult (5, Attack), 7564 `MEAT_TREE` "Meat tree" (2, op1 "Cut") |
| Lizardman shamans | 7573 / 7574 `LIZARDSHAMAN_A / B` (3, Attack), 7575 `BLOCKER` "Spawn" (1, no ops) |
| Skeletal mystics | 7604 / 7605 / 7606 `SKELETONMYSTIC_A / B / C` (2, Attack) |
| Ice demon | 7584 `NONCOMBAT` (2, op1 Attack), 7585 `COMBAT` (2, op2 Attack), 7586 `ICEFIEND` (1, no ops) |
| Jewelled crabs | 7576-7579 grey / red / green / blue "Jewelled Crab" (1, ops Attack, **Smash**), 7580-7583 white / red / green / blue "Energy focus" (1) |
| Tightrope | 7559 "Deathly ranger", 7560 "Deathly mage" (1, Attack) |
| Thieving | 7602 / 7603 "Corrupted scavenger" active / sleeping (2, no ops); the "Creature keeper" is a loc (29875, op5 "Search") |
| Scavengers | 7548 / 7549 "Scavenger beast" (2, Attack); corridor boulder NPC 7489 (no ops) |
| Resource rooms | bats 7587-7593 Guanic / Prael / Giral / Phluxia / Kryket / Murng / Psykk (op1 "Catch"), 7594 "Cave snake" (3, fishing) |
| Great Olm | 7550 right claw spawning, 7551 head spawning, 7552 left claw spawning (5, no ops, HP stat 100); **7553 `OLM_HAND_RIGHT` (cb 549), 7554 `OLM_HEAD` (cb 1043), 7555 `OLM_HAND_LEFT` (cb 750)** (5, op2 Attack); 7556 / 7557 dying hands (cb 1); 7558 `OLM_FIREWALL_NPC` "Fire" (1, op1 **"Douse"**) |
| Party markers | 7490 `RAIDS_PARTY_GROUPHOLDER_LOBBY`, 7491 `RAIDS_PARTY_GROUPHOLDER_DUNGEON` (invisible, no name, size 1): holders of the party identity, their use is server logic [?] |
| Mount Quidamortem | 7482 "Captain Sdiar" (Talk-to, `ZEAH_RAIDS_CAPTAIN`), **7595 "Captain Rimor" (ops Talk-to, "Claim-cape", "Set-difficulty"; Wiki: at (1234, 3572))**, 7599 / 7600 "Mountain Guide" (1 op Talk-to / + "Travel"), 7601 "Swamp Priest" (`RAIDS_XERICIAN_PRIEST`), 7619 "Stuliette" (minecart, Travel); Library quest NPCs 7607-7614, 11112 / 11113 / 11151 (not raid) |
| Pets | 7520 `RAIDS_OLM_PET` "Olmlet" (Talk-to, "Metamorphosis", Pick-up), 7519 `POH_OLM_PET`; Tektiny 8202 / 8197 (+ enraged 9513 / 9511), Vanguard 8203 / 8198, Vasa Minirio 8204 / 8199, Vespina 8205 / 8200 (+ flying 9514 / 9512), Puppadile 8201 / 8196 (Metamorphosis / Pick-up; POH forms have Talk-to, Pick-up) |

**NPC multis** [R]:
- `RAIDS_MOUNTAINGUIDE_TEMPLE_MULTI` **7617** on varbit **5421**: `[7599, 7600, -1]` (value 0 → one-op guide, 1 → guide with "Travel", else hidden); `RAIDS_MOUNTAINGUIDE_WALL_MULTI` **7618** and `_SOUTH_MULTI` **12428**: `[-1, 7600, -1]` (visible only when the travel point is unlocked).
- `RAIDS_TEMPLE_GNOSI` 8190 / `_DUFFY` 8191 / `_HISTORIAN` 8192 on varbit **6358** (13 values): values 0-11 → the visible NPC 8023 Gnosi / 8163 Historian Duffy / 8182 Natural Historian (Talk-to), 12 → hidden.
No NPC multi exists for the bosses: the form changes (Tekton walking / fighting / enraged, Vanguard dig, Vespula land / fly, Olm spawn / fight) are **NPC id swaps by the server** [R/I] (the `RuneLite` plugins react to `NpcChanged`).

---

## 5. Locs [R unless marked]

### 5.1 Room structure
- `RAIDS_DOORWAY` **29789 "Passage"** 3x2 op1 "Enter": the doorway of every combat / puzzle / end room; the template has exactly one per room (offsets in §6.4). `RAIDS_BOSSENTRANCE` **29735** "Hole" 3x3 op1 "Enter" (the Olm entrance, FL_END3), `RAIDS_DESCENTTO1` **32542** / `RAIDS_DESCENTTO2` **29734** "Hole" 2x2 op1 "Enter" (the floor holes in FL_END1 / FL_END2), `RAIDS_ASCENTTO2` **32543** / `RAIDS_ASCENTTO3` **29995** "Rope" 1x3 op1 "Climb-up" (in FL_START2 / FL_START1), `RAIDS_BOSSEXIT` **29996** "Rope" 1x3 (Olm room, back up), `RAIDS_OLM_BARRIER` **29879** "Mystical barrier" 4x1 ops "Pass", "Quick-pass".
- Tendril doors (the room is locked until cleared): `RAIDS_DOGODILE_TENDRILS_ENTRANCE` **29767** "Noxious tendrils", `RAIDS_SHAMAN_TENDRILS_ENTRANCE` **29768** "Spirit tendrils", `RAIDS_VESPULA_TENDRILS_ENTRANCE` **30024** "Septic tendrils" (op1 "Pass"). They are **not placed statically**: server-spawned [I].
- Corridor shortcuts: `RAIDS_CORRIDOR_ROOTS` 29736 "Rotten sapling" (Chop) → `_CLEARED` 29737 "Rotten stump" (Cross); `_ROCKS` 29738 "Rocks" (Mine) → `_CLEARED` 29739 "Rubble" (Cross); `_BOULDER` 29740 "Boulder" (Push), plus NPC 7489. **Not in the static templates**, spawned per layout [R/I].
- Room-locking blockers (their disappearance is the room-complete signal for the timer plugins; **all server-spawned: none is in the static templates**): `RAIDS_SKELETALMYSTICS_SYMBOL` 29741 "Mark of Power", `RAIDS_TIGHTROPE_BARRIER` 29749 "Shimmering barrier" (op1 "Dispel"), `RAIDS_LASERCRABS_BIGCRYSTAL_1..5` 29753-29757 "Dark crystal", `RAIDS_ICEDEMON_SNOW` 29876 "Ice storm", `RAIDS_BLOCKAGE_PURPLE` 30015 / `_PURPLE_SMALL` 30016 / `_ORANGE` 30017 / `_GREEN` 30018 "Crystal" (Vasa, Tekton / Vanguards, Muttadiles), `RAIDS_VESPULA_BOIL_BLOCKING` 30070 "Boil" / `_BOIL_BURST` 30071, and loc **26209** (no `RAIDS_` symbol; the shamans / thieving / guardians blocker) [I].

### 5.2 Per-room functional locs
| room | locs |
|---|---|
| Tekton | `RAIDS_TEKTON_ANVIL` 29867, `RAIDS_TEKTON_FIRE` 30021 "Fire" (op1 Cross), `RAIDS_TEKTON_BOOK` 30022 (multi, §5.5), `RAIDS_TEKTON_WALKBLOCKER` 30023 |
| Vasa | `RAIDS_VASANISTIRIO_CRYSTAL_ON / _OFF` 29774 / 29775, `RAIDS_VASANISTIRIO_FIRE` 30019 "Magical fire" (Cross), `RAIDS_VASANISTIRIO_BOOK` 30020 |
| Vanguards / Muttadiles room | `RAIDS_VANGUARD_BOOK` 30026, `RAIDS_BLOOD_CRYSTAL` 30014 "Blood crystal", `RAIDS_BUCKET_OF_BLOOD` 30085, `RAIDS_HELLS_BELLS` 30086, `RAIDS_MEAT_TREE_FULL / _EMPTY` 30012 / 30013 (the Muttadile tree, "Rocks" / "Meat tree"), bronze axe `RAIDS_ICEDEMON_AXE` 31862 (op1 "Take") |
| Vespula | `RAIDS_VESPULA_HERB` 30068 "Medivaemia root" (op1 Pick) / `_HERB_EMPTY` 30069, `RAIDS_VESPULA_PORTAL` 30072 / `_PORTAL_CLOSED` 30073, `RAIDS_VESPULA_BOOK` 30025 |
| Guardians | pickaxe `RAIDS_STONEGUARDIANS_PICKAXE` 41754 (op1 "Take") |
| Mystics | `RAIDS_SACRIFICIAL_TABLE` 29863 (9 per cell), `RAIDS_MYSTICS_PORTAL_START / _MIDDLE / _END` 49996-49998 "Portal" (ops "Travel-End", "Travel" / "Travel-End" / "Travel-Start", "Travel-Start"; not in the static map) |
| Ice demon | braziers `RAIDS_ICEDEMON_BRAZIER_UNLIT` 29747 (op1 "Light") / `_LIT` 29748 (op1 "Fuel"), tinderbox 31634 (op1 Take), bronze axe 31862, saplings `RAIDS_WOODSOURCE_ROOTS` 29763 (op1 "Chop") / `_DEPLETED` 29764, `RAIDS_ICEDEMON_TINDERBOX` |
| Tightrope | `RAIDS_TIGHTROPE_END` 29750 "Tightrope" (op1 "Cross", two per cell), `RAIDS_TIGHTROPE_KEYSTONE_LOC` 29751 "Keystone crystal" (op1 "Take") |
| Crabs | hammer `RAIDS_LASERCRABS_HAMMER` 29711 (op1 Take), small crystals 29758-29762 (black / cyan / magenta / yellow / white), `RAIDS_LASERCRABS_XERIC_RELIEF` 29752 |
| Thieving | `RAIDS_THIEVINGCHEST_CLOSED` 29742 "Chest" (op1 "Open", 64 / 66 / 74 per variant), `_OPEN` 29743, `_EGGS_WHOLE` 29744, `_EGGS_HATCHED` 29745, trough `_FOODTROUGH_EMPTY` 29746 / `_FULL` 29874 (op1 "Deposit"), `_CREATUREKEEPER` 29875 "Creature keeper" (op5 "Search") |
| Resource (farming) | `RAIDS_PATCH_EMPTY` 29765 "Herb patch" (op2 Inspect), seed / growth / fully-grown patches 29997-30011 (`_FULLYGROWN`: Pick, Inspect, Clear), `RAIDS_FARMING_TOOLS` 29771 "Old tools" (ops Take / Take Rake / Take Spade / Take Seed Dibber), `RAIDS_GOURD_TREE` 29772 (Pick, Pick-lots), `RAIDS_WEEDS` 29773 (Rake), `RAIDS_GEYSER` 29878 "Geyser" (Use), fishing water edge locs |
| Scavenger | none functional in the static cells (energy wells only) |
| All resource / scav / ice / farm rooms | `RAIDS_ENERGY_POOL` **30066 "Energy well"** (op1 "Touch"; a glow overlay 30067) |

### 5.3 Storage, energy and the lobby
Storage locs and their ops are in §2.7. The static placements are in the templates (§6.4): END cells, Ice demon, Thieving, Farming, the Olm arena (2), the Mount Quidamortem lobby (1). `RAIDS_STORAGE_HOTSPOT` is the build site; the server swaps it for 29770 / 29779 / 29780 / 37978 on build [I].
Lobby of the instance (first room, LOBBY band): `RAIDS_BANK_CHEST_LOBBY` 47419 (closed) / `_WORKING` **47420 "Bank chest"** (ops "Use", "Collect"); the closed variant is the post-start state (Wiki: "closed shut once the raid begins") [I].
`RAIDS_EXIT_STEPS_MULTI` **49999** (2x3) on varbit **5423**: `[29778, 50000, -1]` → 0 = `RAIDS_EXIT_STEPS` 29778 "Steps" (op1 "Climb"), 1 = `RAIDS_EXIT_STEPS_RELOAD` 50000 (ops "Climb", **"Reload"**, leader only), anything else hidden. The Wiki: "a new 'Reload' option generates a new layout and boots anyone in the current instance" [R/I].

### 5.4 Reward chest and loot beams
- `RAIDS_REWARD_CHEST` **30028 "Ancient chest"** (op1 "Search"; not placed statically). `RAIDS_REWARD_CRYSTAL` 30027 "Crystal" (static, Olm arena (3233, 5751) L0).
- `RAIDS_REWARD_LOOTBEAM` **28848** is a multi on varbit **5456** (3 bits): `[-1, 30029, 30030, 37977, 37976, -1]` → 0 none, **1 `_LOOTBEAM_BASIC` 30029, 2 `_LOOTBEAM_SPECIAL` 30030, 3 `_LOOTBEAM_DUST` 37977, 4 `_LOOTBEAM_KIT` 37976**, 5+ none. It is placed once, statically, at (3233, 5751) on **level 1** (Olm arena). So the beam colour is per player (varp 1432) [R]; the values map to the
  unique class of the viewer's roll (basic / special / dust / kit are the loot-beam variants) [I].

### 5.5 Books (lore) [R]
`RAIDS_VASANISTIRIO_BOOK` 30020 (varbit 16192 → `[12310, -1]`), `RAIDS_TEKTON_BOOK` 30022 (16191 → `[13482, -1]`), `RAIDS_VESPULA_BOOK` 30025 (16189 → `[13483, -1]`), `RAIDS_VANGUARD_BOOK` 30026 (16188 → `[13484, -1]`), `RAIDS_HOUNDMASTER_BOOK` 30031 (16184 → `[13485, -1]`):
the visible forms are the **loc ids** 12310 "Nistirio's manifesto", 13482 "Tekton's journal", 13483 "Transdimensional notes", 13484 "Vanguard Judgement", 13485 "Houndmaster's Diary" (op5 "Take"), shown **while the player has not read the book**. `RAIDS_THIEVINGCHEST_CREATUREKEEPER` (varbit 16187) and the reward-chest book (16190) have no multi.

---

## 6. Map: CoX templates, the Olm arena, the lobby [R from map locs and static collision]

### 6.1 How the client identifies rooms
The instance is built from **template chunk references** (`REBUILD_REGION_V2` style: per 8x8 chunk `plane << 24 | xChunk << 14 | zChunk << 3 | rotation << 1`; RuneLite `InstanceTemplates.findMatch`, `x = (v >> 14 & 0x3FF) * 8`, `z = (v >> 3 & 0x7FF) * 8`, `plane = v >> 24 & 3`, `rotation = v >> 1 & 3`).
Every plugin that scouts the raid reads the chunk under a room's south-west tile and matches it against the **template bands below** (RuneLite `InstanceTemplates`, `cox-additions` `CoxUtil`: the two lists agree [R]). **Therefore the server must build the CoX instance by copying chunks from exactly these template tiles** (and levels) or every
room plugin, the loot trackers and the layout solver break. Room size is **32 × 32 tiles = 4 × 4 chunks** (`RaidRoom.ROOM_MAX_SIZE`). The instance scene holds a **4 × 2 grid of rooms per floor**; floors sit on **planes 3 (floor 1) and 2 (floor 2)** in RuneLite's layout code (§timings).

### 6.2 Template bands
The template area is `x 3264..3359` (map squares x = 51 (3264..3327) and 52 (3328..3359, half-used)), `z 5120..5759`. Each band is 32 tiles tall and holds **three 32 × 32 variant cells side by side**: `x0 = 3264` (chunk x 408), `3296` (412), `3328` (416), which `cox-additions` names **CCW / THRU / CW** (chunk x 102 / 103 / 104 in units of 4 chunks) = the **side the room is left through relative to where it was entered**:
the exit doorway `RAIDS_DOORWAY` is on the **west** side in CCW, on the **north** side in THRU, on the **east** side in CW, so the room is entered from the **south** in template orientation and the rotation bits of the reference rotate the whole cell (`room_exitside = (winding + rotation) & 3`, winding = `{CCW: 3, THRU: 0, CW: 1}`) [R for the cell geometry, I for the "entered from the south" reading].

| band z0 | map squares (x 51 / 52) | level 0 | level 1 | level 2 |
|---|---|---|---|---|
| 5120 | (51, 80) = 13136, (52, 80) = 13392 | FL_END1 (CCW cell only) | | |
| 5152 | (51, 80), (52, 80) | FL_END2 (CCW cell), FL_END3 (THRU cell) | | |
| 5184 | (51, 81) = 13137, (52, 81) = 13393 | LOBBY (raid start room: CCW, THRU, CW) | | |
| 5216 | (51, 81), (52, 81) | Scavengers (small) | Scavengers (large) | |
| 5248 | (51, 82) = 13138, (52, 82) = 13394 | Lizardman shamans | Skeletal mystics | Stone guardians |
| 5280 | (51, 82), (52, 82) | Vasa Nistirio | Tekton | Vespula |
| 5312 | (51, 83) = 13139, (52, 83) = 13395 | Vanguards | Muttadiles | |
| 5344 | (51, 83), (52, 83) | Ice demon | Tightrope | Jewelled crabs |
| 5376 | (51, 84) = 13140, (52, 84) = 13396 | Thieving (corrupted scavenger) | | |
| 5440 | (51, 85) = 13141, (52, 85) = 13397 | Resource room, fishing | Resource room, bats | |
| 5696 | (51, 89) = 13145, (52, 89) = 13401 | FL_START1 | | |
| 5728 | (51, 89), (52, 89) | FL_START2 | | |

(`RuneLite InstanceTemplates` lists `RAIDS_LOBBY 3264/5184`, `RAIDS_START 3264/5696`, `RAIDS_END 3264/5152 64x32`, the rooms as above; the bands 5120 (END1) and 5728 (START2) are not in it but are in `CoxUtil` [R].) The floor roles, from the loc contents:
- **FL_END1/2/3** carry the floor exits: END1 (3264, 5120) the hole `RAIDS_DESCENTTO1` 32542, END2 (3264, 5152) `RAIDS_DESCENTTO2` 29734, **END3 (3296, 5152) the Olm hole `RAIDS_BOSSENTRANCE` 29735**; **FL_START1** (z 5696) the rope `ASCENTTO3` 29995, **FL_START2** (z 5728) `ASCENTTO2` 32543. The pairing "END1 → floor 2, END2 → floor 3, END3 → Olm" is [I] (the names say so; the number of floors per layout is §7).
- Level 1 and 2 contain locs only where the band uses them (e.g. the 5216 band: levels 0 and 1; the 5120 and 5152 bands carry a handful of decoration locs on level 2 / x 3360 that are not part of any cell).
- Cell content counts (locs): every cell holds 930-1190 locs (floors, walls, decoration); the functional ones are listed in §5.2 and §6.4.

### 6.3 Room → template cell, with the raid's content names
Use `plane` = the template level for the chunk copy. Row key = `(room, level, band z0)`:

| room | level | z0 | notes |
|---|---|---|---|
| Lobby (raid start) | 0 | 5184 | bank chest `47420`; steps multi `49999`; Passage doorway |
| Floor-1 start | 0 | 5696 | rope `ASCENTTO3` at (16, 15) in all three variants, weeds |
| Floor-2 start | 0 | 5728 | rope `ASCENTTO2` at (16, 15) |
| Floor ends | 0 | 5120 / 5152 | see §6.2 |
| Scavengers small / large | 0 / 1 | 5216 | energy well only (static); the large cell is the one with the boulder / rock / sapling shortcut (spawned) |
| Shamans / Mystics / Guardians | 0 / 1 / 2 | 5248 | |
| Vasa / Tekton / Vespula | 0 / 1 / 2 | 5280 | |
| Vanguards / Muttadiles | 0 / 1 | 5312 | |
| Ice demon / Tightrope / Crabs | 0 / 1 / 2 | 5344 | |
| Thieving | 0 | 5376 | 64 / 66 / 74 `Chest` 29742 (CCW / THRU / CW) |
| Farming (fishing / bats) | 0 / 1 | 5440 | patches, tools, gourd tree, geyser, energy well |

The Wiki confirms the rooms and the CM order (CM: top level Tekton, crabs, scavengers, ice demon, shamans, resource; middle Vanguards, thieving, scavengers, Vespula, resource, tightrope; lower Guardians, Vasa, scavengers, mystics, Muttadiles, resource) [I]; floor generation is §7.

### 6.4 Key locs per cell (offsets `dx, dz, rotation` from the cell's south-west corner) [R]
`D` = `RAIDS_DOORWAY` 29789 (the exit passage; also the CCW=W / THRU=N / CW=E reading: the `dx, dz` below match it).

| cell (level, band) | CCW (x0 3264) | THRU (3296) | CW (3328) |
|---|---|---|---|
| Lobby (0, 5184) | D (3, 15, r0); bank chest (15, 17, r3); steps multi (14, 0, r0) | D (11, 21, r1); bank chest (7, 15, r2); steps multi (2, 1) | D (24, 12, r2); bank chest (5, 11, r3); steps multi (2, 1) |
| Floor end 1 (0, 5120) | D (11, 4, r1); hole `DESCENTTO1` (13, 17, r3); storage hotspot (23, 16); weeds (17, 18); energy well (16, 21) | | |
| Floor end 2 (0, 5152) | D (11, 4, r1); hole `DESCENTTO2` (13, 17, r3); storage hotspot (23, 16); weeds (17, 18); energy well (16, 21) | **Floor end 3:** D (18, 5, r1); `BOSSENTRANCE` hole (11, 22, r3); storage hotspot (12, 12, r1); weeds (19, 19); energy well (23, 18) | |
| Floor start 1 / 2 (0, 5696 / 5728) | rope (16, 15); weeds (13, 14) | rope (16, 15); weeds (15, 11) | rope (16, 15); weeds (23, 22) |
| Scav small (0, 5216) | energy well (19, 22) | (15, 11) | (9, 19) |
| Scav large (1, 5216) | energy well (2, 14) | (14, 13) | (28, 17) |
| Shamans (0, 5248) | D (1, 15, r0) | D (15, 28, r3) | D (26, 16, r0) |
| Mystics (1, 5248) | D (3, 16, r0); 9 sacrificial tables | D (13, 26, r1); 9 tables | D (22, 20, r2); 9 tables |
| Guardians (2, 5248) | D (8, 19, r0); pickaxe (20, 18) | D (14, 26, r1); pickaxe (9, 20, r3) | D (19, 22, r2); pickaxe (6, 22, r3) |
| Vasa (0, 5280) | D (1, 15, r2) | D (15, 28, r3) | D (28, 15, r0) |
| Tekton (1, 5280) | D (3, 14, r0); anvil (14, 22) | D (14, 26, r1); anvil (7, 13, r3) | D (26, 16, r2); anvil (12, 22) |
| Vespula (2, 5280) | D (0, 15, r2); portal (17, 21); 6 herb roots (2, 4), (2, 7), (2, 21), (3, 23), (4, 2), (5, 23) | D (15, 29, r3); portal (23, 11, r1); herbs (2, 26), (4, 28), (7, 27), (12, 26), (18, 24), (19, 26) | D (29, 15, r0); portal (11, 21); herbs (24, 15), (24, 17), (25, 3), (25, 19), (27, 9), (28, 6) |
| Vanguards (0, 5312) | D (1, 15, r0) | D (15, 29, r1) | D (24, 16, r2) |
| Muttadiles (1, 5312) | D (3, 16, r0); blood crystal (6, 4, r2); axe (18, 3); bucket (5, 10); bells (17, 2) | D (12, 25, r3); crystal (6, 4); axe (18, 3); bucket (7, 6); bells (14, 2) | D (26, 15, r0); crystal (26, 6, r1); axe (14, 4); buckets (3, 9), (24, 6); bells (17, 2) |
| Ice demon (0, 5344) | D (4, 21, r2); storage (21, 22, r3); tinderbox (16, 3); axe (20, 7); braziers (8, 18), (8, 25), (11, 20), (11, 23) | D (14, 26, r3); storage (14, 12, r2); tinderbox (16, 2); axe (22, 5); braziers (11, 24), (13, 21), (16, 21), (18, 24) | D (23, 18, r0); storage (8, 18, r1); tinderbox (12, 3, r2); axe (10, 4); braziers (18, 17), (18, 20), (21, 15), (21, 22) |
| Tightrope (1, 5344) | D (3, 16, r0); ends (18, 23, r1), (26, 23, r3); keystone (28, 26, r2) | D (13, 25, r3); ends (18, 23), (26, 23); keystone (28, 26) | D (28, 15, r0); ends (8, 18), (8, 26); keystone (5, 28, r1) |
| Crabs (2, 5344) | D (5, 21, r0); hammer (15, 1); small crystals black (19, 12), cyan (23, 15), magenta (18, 18), yellow (15, 22) | D (12, 24, r1); hammer (16, 0); black (15, 12), cyan (9, 12), magenta (10, 17), yellow (24, 16) | D (23, 22, r0); hammer (15, 3); black (13, 11), cyan (12, 23), magenta (12, 19), yellow (9, 10) |
| Thieving (0, 5376) | D (3, 14, r0); trough (7, 13, r2); storage (10, 12, r1); creature keeper (11, 14, r3); 64 chests | D (22, 24, r1); trough (21, 21, r3); storage (20, 19, r2); keeper (23, 18); 66 chests | D (25, 21, r2); trough (22, 23, r0); storage (20, 24, r3); keeper (20, 18, r0); 74 chests |
| Farm fishing (0, 5440) | patches (22, 18), (22, 19); storage (11, 25, r3); tools (17, 24); gourd tree (18, 22, r2); geyser (5, 22); energy well (22, 10) | patches (20, 9), (21, 9); storage (16, 23, r3); tools (23, 12, r1); gourd trees (4, 21), (8, 10); geyser (8, 23); well (10, 6) | patches (8, 8), (9, 8); storage (7, 18, r2); tools (10, 6, r2); gourd tree (22, 21, r3); geyser (22, 14, r3); well (25, 18) |
| Farm bats (1, 5440) | patches (18, 26), (19, 26); storage (12, 25, r3); tools (9, 23, r3); gourd (14, 25, r1); geyser (26, 21); well (19, 8) | patches (20, 9), (21, 9); storage (21, 16); tools (23, 12, r1); gourd (3, 18, r1); geyser (8, 13); well (13, 12) | patches (18, 12), (19, 12); storage (14, 16, r1); tools (20, 8, r2); gourd (16, 14); geyser (7, 19, r2); well (22, 17) |

The thieving chest coordinates of the CCW and THRU cells equal, tile for tile, the chest tables of the `cox-additions` plugin (`ChestData`), which also holds 10 candidate "solution" sets of 4 chests per rotation (`runelite-timings.md`); the CW table did not match in my parse (74 chests in the cache) [R/?].
Static NPC spawns and the room-lock blockers (§5.1) are not in the cache; the server places them [R].

### 6.5 Free-tile extents (flags 0) of the cells [R]
Walkable bounding boxes (x × z, tile coordinates), useful to sanity-check a copy: Shamans L0 CCW 3269..3292 × 5249..5276, THRU 3299..3326 × 5249..5276, CW 3334..3358 × 5249..5276; Vasa L0 3266..3291 × 5281..5307 / 3299..3323 × 5281..5309 / 3332..3354 × 5281..5308; Tekton L1 3265..3288 × 5281..5302 / 3307..3320 × 5281..5310 / 3335..3358 × 5281..5301;
Vanguards L0 3269..3288 × 5313..5337 / 3306..3320 × 5313..5339 / 3334..3358 × 5315..5336; Ice demon L0 3265..3289 × 5345..5372 / 3304..3318 × 5345..5374 / 3332..3358 × 5345..5369; Thieving L0 3265..3291 × 5377..5402 / 3300..3322 × 5377..5406 / 3331..3358 × 5377..5404;
End cells 3274..3286 × 5153..5175 and 3305..3321 × 5154..5176; Lobby cells 3265..3283 × 5187..5203, 3299..3312 × 5188..5214, 3331..3358 × 5188..5201. **The cell edges are all blocked** in the static collision (the connecting corridors are produced by the instance builder from the neighbouring doorways, not stored in the templates) [R]; the corridor generation rule is [?].

### 6.6 The Great Olm arena (map square (50, 89), id 12889, base (3200, 5696)) [R]
A fixed 64 × 64 square (`CoxUtil` matches the whole square, plane 0, as the Olm room template). Static locs: **mystical barrier `RAIDS_OLM_BARRIER` 29879 at (3231, 5729) rot 2** (the entrance from the south); **rope `RAIDS_BOSSEXIT` 29996 at (3232, 5718)**; two **storage hotspots** 29769 at (3228, 5727, r2) and (3237, 5727, r0);
steps `RAIDS_EXIT_STEPS` 29778 at (3231, 5755, r2); reward crystal `RAIDS_REWARD_CRYSTAL` 30027 at (3233, 5751) and the loot-beam multi 28848 at (3233, 5751) **level 1**; a green crystal blockage `RAIDS_BLOCKAGE_GREEN` 30018 at (3232, 5749); carved heads `RAIDS_OLMIC_HEAD2` 30035 at (3228, 5728) and (3235, 5728); decorations (rocks, plants, large crystals).
**Olm cave placeholders** (the three parts rise from these; the live locs 29880-29888 are swapped in by the server): head `OLM_HEAD_CAVE` **29882 "Large hole"** (5x8) at **(3220, 5738, r3)** and **(3238, 5738, r1)**; left hand `OLM_HAND_LEFT_CAVE` **29885 "Large rock"** at (3220, 5743, r3) and (3238, 5733, r1); right hand `OLM_HAND_RIGHT_CAVE` **29888 "Crystal structure"** at (3220, 5733, r3) and (3238, 5743, r1).
So the Olm sits on the **west or east wall** and the hands are swapped between phases (the head cave pair marks the two sides); the live forms are locs `OLM_HEAD_SPAWNING` 29880 / `OLM_HEAD` 29881, `OLM_HAND_LEFT_SPAWNING` 29883 / `OLM_HAND_LEFT` 29884, `OLM_HAND_RIGHT_SPAWNING` 29886 / `OLM_HAND_RIGHT` 29887 (all 5x8; each carries the Seqs of §1.6) plus the attackable NPCs 7550-7555 [R/I].
Dynamic Olm locs: `OLM_CRYSTAL_BOMB` 29766 "Crystal bomb", `OLM_ACID_POOL` 30032 "Acid pool", `OLM_CRYSTAL_ATTACK_SMALL / _LARGE` 30033 / 30034 "Small / Large crystals" (1x1, the falling-crystal shadows), `OLM_FIREWALL_NPC` 7558.
The arena is **not** inside the template bands: `x 3200..3263, z 5696..5759`; RuneLite matches it by region 12889 via the chunk reference [R]. Free area ≈ x 3212..3242 × z 5708..5755 (static).

### 6.7 Mount Quidamortem: lobby and the recruiting board (map square (19, 55), region id 4919, base (1216, 3520)) [R]
RuneLite's points overlay names region **4919** "RAID_BANK_REGION" [R]. Static locs of the raid area:

| loc | id | op(s) | placements (x, z, level, rot) |
|---|---|---|---|
| **Chambers of Xeric** (entrance steps) 4x1 | `RAIDS_ENTRANCE_STEPS` 29777 | "Enter" | **(1232, 3573, 0, r2)** |
| **Recruiting board** | `RAIDS_PARTY_RECRUITMENT` 29776 | "Read" | **(1224, 3562, r0), (1246, 3562, r1), (1240, 3549, r3)** (identical to the Wiki's three pins) |
| **Score board** | `RAIDS_CHALLENGE_SCORES` 32544 | "Read" | **(1224, 3565, r0), (1243, 3563, r1)** (the CM leaderboard, interface 21) |
| Bank chest (the mountain bank) | 30087 | "Use", "Collect" | (1254, 3572, r2) |
| Storage unit (lobby) | `RAIDS_STORAGE_LOBBY` 30107 | "Private" | **(1256, 3572, r0)** |
| Bank deposit box | 10529 | "Deposit" | (1252, 3571, r3) |
| Minecart (to Lovakengj) | 28835 | "Travel" | (1254, 3548, r3) |
| Dead trees | 1282 / 1289 | "Chop down" | many |

NPC spawns (Captain Rimor, Mountain Guide, Gnosi, Duffy, Natural Historian, Stuliette) are not in the cache; Wiki positions: Captain Rimor (1234, 3572).
The walkable lobby is the mountain top; the instance **entrance** is the steps (1232, 3573). The Raid entrance is a loc op, not a door: "Enter" moves the player (and, for the party leader, starts or joins the instance) to the LOBBY template cell, server-side [I].

---

## 7. Modes and layouts
- **Layout pool** (`RAIDS_MAP_POOL_SELECTED` 5408): **0 Small, 1 Large, 2 Full** (Wiki: "small, large and full-sized raids"; the Wiki: normal raids have 7 or 8 rooms per floor (small), exactly 8 per floor with 5 combat + 3 puzzle in the large pool, and **Full = the Challenge Mode layout**).
  Normal-mode layouts in the large / small pools are **one of 26 fixed codes** (RuneLite `LayoutSolver`, WooxSolo's data): two floors on a 4 × 2 grid each, e.g. `FSCCP.PCSCF - #WNWSWN#ESEENW` (floor-1 rooms `S C C P`..., `#` the start room, `¤` the floor end; letters S scavengers, F farming/resource, C combat, P puzzle; the direction strings are the path moves from the start; floor 2 is offset by +8 in room index). The combat rooms of a raid follow one of **four 8-room cycles** (RuneLite `RotationSolver`): (Tekton, Vasa, Guardians, Mystics, Shamans, Muttadiles, Vanguards, Vespula), (Tekton, Muttadiles, Guardians, Vespula, Shamans, Vasa, Vanguards, Mystics), (Vespula, Vanguards, Muttadiles, Shamans, Mystics, Guardians, Vasa, Tekton), (Mystics, Vanguards, Vasa, Shamans, Vespula, Guardians, Muttadiles, Tekton). Full details: `runelite-timings.md`.
- **Challenge Mode** = varbit **6385 = 1** (set on the leader's party by the checkbox on 507, only when the layout is Full, §2.5), the CM leaderboard (21), the completion varp 1735 / dbrow 4016, the chat suffix "Challenge Mode count", `RAIDS_CHALLENGE_ICON` in the party list. All stats +50 % except the Olm's health (Wiki); NPC ids are shared (§4). Three levels of rooms + Olm (Wiki).
- **Floors in the HUD** (`5425`): see §2.2.
- **Scaling** (`RAIDS_SCALING`, mega-scale) §2.6.

---

## 8. DB tables
- **81 (hiscores bosses)**: dbrow **4015** "Chambers of Xeric" (field 1 = 4288, field 2 → varp **1532**) and **4016** "Chambers of Xeric: Challenge Mode" (field 1 = 4296, field 2 → varp **1735**) [R].
- **44 (music)** and the others: §1.6. `SYNTH_COX` dbrow 4743, `ACTION_COX_UNIQUE` 6774, `ACTION_COMPLETE_COX` 6843 (named rows without columns readable here), `DROP_INSTANT_REVEAL_COX` 17022 (table 135, stripped), `BANK_CHAMBERS_OF_XERIC_TELEPORT` 15671, `BOSS_CHAMBERS_OF_XERIC_TELEPORT` 15903, `CHARGES_XERICS_TALISMAN` 4350 [R].
- The party, the scoreboard and the stats are server data; there is no CoX stats table in the cache [R].

---

## 9. Blockers and open points
1. **Server-only data is not in the cache**: room and boss stats and scaling, attack patterns and timings (see `runelite-timings.md`), the 26 layouts' room-to-cell mapping and the **connecting corridors** (the templates have closed edges, §6.5), point values and caps, reward tables, the room-complete messages, death handling. Each needs a `docs/mechanics` rule before code.
2. **Varbits with no reader**: 27, 5414, 5415, 9541, 3288, 10055, 5463 (§1.2). Choose encodings behind config flags: `RAIDS_PARTYMEMBER_ID` is the member index inside the party [I]; `RAIDS_DIED` a "died once" flag [I].
3. **Semantics that are [I] or [?]**: the progress values 2 / 3 under Small / Large layouts (§2.2); the use of the 7490 / 7491 group-holder NPCs; the Olm `HEADBAR_OLMTIMER` sprites; the bracket selection on the CM leaderboard (§2.8); the bounds of the numeric dialogs on 507; which music track plays where; the meaning of loc 26209 and of the "entered from the south" template orientation; the START1 / START2 pairing with END1 / END2 / END3.
4. **Interface mounting**: the cache does not say in which gameframe slot 513 (HUD), 500 (side tab) and 710 (mobile helper) are mounted; the row scripts only check that their host slot is open (§2.5). Choose the standard modal slot for 499 / 507 / 539 / 21 / 271 / 550 and the side-tab slot for 500 / 551 [?].
5. **Per-player varbit distribution**: 6385 / 6386 live in the *persistent* varp 1429 (it also carries unrelated unlock bits), 5423-5428 in the temp varp 1430, 5431-5432 in 1431, 5433 / 5456 / 5457 / 5408 / 10055 in 1432, 9539 / 9540 / 12285 / 13069 in 3062. Writing a varp overwrites its neighbours: use read-modify-write [R].
6. **Reward flow**: inv 581 (5 slots) must be filled before 539 opens; `RAIDS_CLIENT_REWARDINDICATOR` (5456) and `NEEDSREWARDBOOK` (5457) must be set before the loot beam / the book icon are needed. The loot beam loc is static at (3233, 5751, L1) of the Olm arena [R].
7. **Send-order contracts** are listed under §3. The mobile helper rows (4653) are free text: there is no format in the cache beyond the alternating left / right strings [R].
