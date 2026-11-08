<!-- Researched 2026-11-08 from cache 2757 (rev 242) and the disassembled rev-242 client scripts. Tags: [R] read directly, [I] interpretation, [?] unknown. The helper readers mentioned below were one-off scratch scripts and are not in the repo. -->
# The Theatre of Blood (ToB): client contract from cache 2757 (rev 242)

Sources: generated symbols in `symbols/build/generated/symbols/tp/symbols/*.java` (they equal RuneLite's gameval names);
`./gradlew :tools:cache-dump:run` (`config`, `interface`, `map`, `collision`, `dbrows`, `find-locs`); the rev-242 client
scripts disassembled to rs2asm (the earlier Neptune dump, 10,336 scripts; ids and call graph agree with the vendored
rsmod `clientscript.sym` names `tob_hud_init` = 2087 ... `tob_partylist_fillline` = 5220); RuneLite source (`/tmp/claude-0/ref/rl`)
for region ids and the plugins that read ToB state; the OSRS Wiki pages in `/tmp/claude-0/ref/wiki-tob` where the cache is
silent (always marked as Wiki). One-off read-only readers (not in the repo) read the JS5 store for IF3 hook arguments
(RuneLite `cache` 1.13.6 `InterfaceLoader`) and flood-filled the static collision. Nothing in this file is code.

Each claim has a tag:
- **[R]**: read directly from cache data, script text, RuneLite source or a symbol.
- **[I]**: my interpretation of the script logic or of names, or a Wiki fact.
- **[?]**: unknown.

Packed component ids are `interface << 16 | component`. Internal vocabulary: the cache calls **Entry Mode "STORY"**
(`*_STORY` NPCs, `TOTAL_COMPLETED_THEATREOFBLOOD_STORY`, `TOBQUEST` is a different thing: the quest) and uses `*_HARD` for
Hard Mode; the unsuffixed ids are Normal Mode. In the client, `enum 3852` names the three modes 0 "Normal", 1 "Entry", 2 "Hard" [R].

**Name collision:** every `SOTE_*` symbol (varps 2247-2309, varbits 8577-8749, interface 636 `SOTE_UPASS_BATTLE`) belongs to
Song of the Elves, not to Sotetseg. Sotetseg is always spelled `SOTETSEG` [R]. The `TOBQUEST*`, `MYQ4*`, `MYQ6*` and `SOTFA_*`
symbols belong to the quests around ToB (Sins of the Father, A Night at the Theatre) and are listed only where they touch raid
content.

---

## 1. Symbols

### 1.1 Varps (all `clientCode=0`)
| id | name | notes |
|---|---|---|
| 1737 | TOB_TEMP_TRANSMIT_2 | Varbits 6448, 6449 (wave progress), 6400 (throne visible), 6450-6454 (treasure chests), 8119 (bit 31, unnamed, no readers). The HUD redraws when it changes [R] |
| 1740 | TOB_MYCONTROLLER | No varbits. Script 2339: `!= -1` → party-list button text "My party" (yellow), else "Make party". 2337 hooks a redraw on transmit. Holds the party/controller id, -1 = none [R/I] |
| 1745 | TOB_TEMP_TRANSMIT_1 | Varbits 6440-6447: party status, own slot, five orbs, progress type. The HUD redraws when it changes [R] |
| 1746 | TOB_PERM_TRANSMIT_1 | Varbits 6455 (well), 6460 (points), 6461 (booby prize), 11958 (loot waiting), 12270 (scoreboard tab), 12271 (stamina pot), 12272 (last X). Persistent per player; scripts 2346 and 4490 redraw on transmit [R] |
| 1748 | TOTAL_COMPLETED_THEATREOFBLOOD | Normal Mode completions. Read by 4774, 4778, 10310 (Combat Achievements boss kc via dbrow 4054 `HISCORES_BOSSES_THEATRE_OF_BLOOD`). Multi-NPC varp of Verzik's start NPC 8369 (§4) [R] |
| 3052 | TOB_TEMP_TRANSMIT_3 | Varbits 12269 (hard Sotetseg tiles), 12988 (lobby friends filter), 14980 (crystal antispam). Script 5218 redraws the filter button on transmit [R] |
| 3055 | TOBQUEST_MAIN | Quest block: varbits 12971 `TOB_PROGRESS` (bits 0-5), 12277, 12276 `TOBQUEST`, 12972-12975, 14610, 15607, 6573, 10996. No raid state except `TOB_PROGRESS`, whose reader is unknown [?] |
| 3057 | TOTAL_COMPLETED_THEATREOFBLOOD_HARD | Hard Mode completions. 4774/4778/10310 (dbrow 4055). Multi-NPC varp of 10847 [R] |
| 3058 | TOTAL_COMPLETED_THEATREOFBLOOD_STORY | Entry Mode completions. Read by 4778 only [R] |

Unnamed varps and varbits next to the block exist but have no ToB name and no script reader in the ToB scripts: varp 1738
(varbits 6409-6414), 1739 (6415-6427), 1742 (6428-6434), 1743 (6429, 6435-6437, 6458, 16178-16183), 1744 (6438, 6439), 1747
(6457, 7868), 1749 (6459, 12238-12240, 12965-12970, 12987). Whether any belongs to ToB is [?]. Varp 1736 holds `MYQ4*` (quest).
Varbit 11469 `TOB_DAMAGE_TAKEN` (varp 3585, bits 7-21, 15 bits) has no reader in the cache [R]; its use is [?] (probably a
server-side or plugin counter).

### 1.2 Varbits (base varp and bit range from `config varbit`) [R]
| varbit | name | varp | bits | width / max | client readers |
|---|---|---|---|---|---|
| 6440 | TOB_CLIENT_PARTYSTATUS | 1745 | 0..1 | 2 / 3 | 2297 (HUD) |
| 6441 | TOB_CLIENT_PARTYSLOT | 1745 | 2..4 | 3 / 7 | 2297, 2298 |
| 6442-6446 | TOB_CLIENT_P0..P4 | 1745 | 5..9, 10..14, 15..19, 20..24, 25..29 | 5 / 31 each | 2297 → 2298; RuneLite chat-commands counts `P >= 1` as team size |
| 6447 | TOB_CLIENT_WAVEPROGRESS_TYPE | 1745 | 30..31 | 2 / 3 | 2297 |
| 6448 | TOB_CLIENT_WAVEPROGRESS_VAL | 1737 | 0..9 | 10 / 1023 | 2297 |
| 6449 | TOB_CLIENT_WAVEPROGRESS_MAX | 1737 | 10..19 | 10 / 1023 | 2297 |
| 6400 | TOB_VERZIK_THRONE_VISIBLE | 1737 | 20 | 1 | Loc multi 32736 (§2.8) |
| 6450-6454 | TOB_TREASUREROOM_CHEST_0..4 | 1737 | 21..22, 23..24, 25..26, 27..28, 29..30 | 2 / 3 each | Loc multis 33086-33090 (§2.7) |
| 6455 | TOB_LOBBY_WELL_CONTENTS | 1746 | 0..13 | 14 / 16383 | Loc multi 33085 (§2.7) |
| 6460 | TOB_MIDWAYCHEST_POINTS | 1746 | 14..18 | 5 / 31 | 2346, 2349 |
| 6461 | TOB_MIDWAYCHEST_BOOBYPRIZE | 1746 | 19 | 1 | none |
| 11958 | TOB_SHOULD_HAVE_LOOT | 1746 | 20 | 1 | Loc multis 41437 and 41743 (§2.7) |
| 12270 | TOB_SCOREBOARD_TAB | 1746 | 21..23 | 3 / 7 | 4492, 4493 |
| 12271 | TOB_MIDWAYCHEST_STAMINAPOT | 1746 | 24..27 | 4 / 15 | 2346, 4488 |
| 12272 | TOB_MIDWAYCHEST_LAST_X | 1746 | 28..31 | 4 / 15 | 2346, 4489 |
| 12269 | TOB_SOTETSEG_HARD_TILES_VISIBLE | 3052 | 0..2 | 3 / 7 | Loc multis 41750-41753 (§2.8) |
| 12988 | TOB_LOBBY_FRIENDS_FILTER | 3052 | 3 | 1 | 2340, 5219 |
| 14980 | TOB_CRYSTAL_ANTISPAM | 3052 | 4..10 | 7 / 127 | none |
| 12971 | TOB_PROGRESS | 3055 | 0..5 | 6 / 63 | none |
| 11469 | TOB_DAMAGE_TAKEN | 3585 | 7..21 | 15 / 32767 | none |
| 11994 | COLLECTION_RAIDS_TOB_COMPLETED | 2946 | 31 | 1 | collection log (generic) |
| 7279 | COLLECTION_ITEM_VERZIKPET | 1860 | 0..7 | 8 | collection log |
| 7288-7292 | COLLECTION_ITEM_SINHAZA_SHROUD_TIER1..5 | 1862 | 16..20 (1 bit each) | | collection log |
| 6407 / 6408 | PET_INSURANCE_VERZIK / PET_MENAGERIE_VERZIK | 1417 / 1416 | 8 / 8 | 1 | pet menagerie / insurance |
| 12235 | POH_MENAGERIE_MULTIFORM_VERZIKPET | | | | POH pet metamorph |
| 12695-12718, 12830-12855 | CA_TASK_THEATREOFBLOOD_* (normal, hard, story tasks) | | | | Combat Achievements |
| 12935 / 12938 / 12939 | CA_TOTAL_TASKS_COMPLETED_THEATREOFBLOOD / _HARD / _STORY | | | | Combat Achievements |
| 13061 / 13389 / 17313 | GIM_HARDCORE_TOB_DEATH / LEAGUE_3_LOCK_BOSS_TOB / LEAGUE_TOB_MODE | | | | out of scope (GIM / Leagues) |

Varbits that no script, loc or NPC multi reads: 6461, 14980, 12971, 11469 [R]. Their encodings are [?] (§8).

### 1.3 Varcs [R]
| id | name | use |
|---|---|---|
| 219 | TOB_CLIENT_NEXTREFRESH | 2297 sets it to `clientclock+30`. The HUD timer 2296 redraws when `clientclock >= it`. 2301 resets it to 0 (forces a redraw) |
| 220 | TOB_CLIENT_LASTPROGRESS | Animated fill of the progress bar (script 2305 eases it toward the target, 1 step per frame); reset to -1 when the bar is hidden |
| 330-334 | TOB_CLIENT_NAME0..4 (strings) | Written by the server-run script 2301 (5 strings), read by 2297 → 2298 |

The party UIs also use the generic varcs 59 (age base, set to `clientclock` by 2337), 178 (row counter), 179 (scroll position of
the party list), 180 (party-list sort state), 181 (party-details sort state). These are client-local [R].

### 1.4 Invs [R]
612 `TOB_CHESTS`, size 6. It is the reward inventory of the treasure-room chest interface 23, and the first slot is also shown
in the supplies interface 405 (script 2348 reads `inv_getobj(612, 0)`, §2.5). RuneLite's loot tracker reads it when
interface 23 opens inside regions 12867 or 14642.

### 1.5 Interfaces and components [R]
- **23 TOB_CHESTS**: 0 UNIVERSE, 1 FRAME (onLoad 227 title "Theatre of Blood"), 2 CONTENT, 3-5 type-6 model slots (COM_3, COM_4,
  COM_5), 6 BANK (op6 "Bank-all"), 7 BANK_ICON, 8 INVENTORY (op1 "Take-all"), 9 INVENTORY_ICON, 10 DISCARD (op6
  "Discard-all"), 11 DISCARD_ICON, 12 ITEMS (layer). The three buttons have `onOp` 489 (sound only) and hover scripts 273; the
  server handles every op. No client script references 23:12 or the item draw, so the server must run the standard inv-draw
  script for inv 612 into 23:12 [R/I].
- **28 TOB_HUD** (39 components, 28:0 .. 28:38):
  - 0 UNIVERSE, 1 ATMOSPHERIC (full-screen fade rectangle, §3), 2 SAFEZONE, 3 HUD, 4 LEFT_DODGER_NAMES, 5 NAMES_CONTAINER (onLoad 991),
    6 LEFT_DODGER_STATUS, 7 STATUS_CONTAINER, 8 MIDDLE_DODGER, 9 PROGRESS_CONTAINER (onLoad 714).
  - Inside NAMES_CONTAINER: 10 background sprite 297, **11 NAMES_HEADER** (text; the lobby status text, §2.2), 12 NAMES (text).
  - Inside STATUS_CONTAINER: 13 `SPECTATOR_CONTAINER` (group 14-15 inside it: 14 sprite 2138, **15 SPECTATOR_EXIT** with sprite 537 / 538 on
    hover and action 0 "Exit"; further groups 16..35 are not part of it), then the five orb groups `_k_CONTAINER` at **16 + 4k** (k = 0..4: 16,
    20, 24, 28, 32), each with a ring sprite 2138 (17 / 21 / 25 / 29 / 33), an orb sprite 1059 (18 / 22 / 26 / 30 / 34) and a filler group
    `_k_FILLER` at **19 + 4k** (19, 23, 27, 31, 35).
  - 36 PROGRESS_BAR, 37 PROGRESS_TEXT (both inside PROGRESS_CONTAINER 28:9), 38 TOOLTIP.
  - The onLoad is `[2087, this, SAFEZONE 28:2, HUD 28:3, LEFT_DODGER_NAMES 28:4, LEFT_DODGER_STATUS 28:6, MIDDLE_DODGER 28:8,
    NAMES_CONTAINER 28:5, NAMES_HEADER 28:11, PROGRESS_CONTAINER 28:9, PROGRESS_BAR 28:36, PROGRESS_TEXT 28:37, STATUS_CONTAINER
    28:7, (container, filler) × 5 = (28:16, 28:19) (28:20, 28:23) (28:24, 28:27) (28:28, 28:31) (28:32, 28:35), SPECTATOR_CONTAINER
    28:13, TOOLTIP 28:38]` [R].
- **50 TOB_PARTYDETAILS** (73 components, 50:0 .. 50:72; names from `Components.java` `Tob_Partydetails`) [R]:
  - 0 UNIVERSE (onLoad 2316 with the 14 component args listed in §2.3), 1 FRAME, 2 BACK, 3 CONTENTS, 4 TOP,
    8 HEADER (stat icons 18-26 inside it, each with the tooltip text "Combat level", "Attack level", "Strength level", "Ranged level",
    "Magic level", "Defence level", "Hitpoints level", "Prayer level", "Performance counter" via script 526 into TOOLTIP), 9 REFRESH,
    10 TIMER_CONTAINER, 14 TIMER (the auto-refresh countdown bar), 27 CURRENT (the five member rows), 28 MIDDLE (applicants area), 31 SORTAREA.
  - Sort headers 32-41: `SORT_NAME`, `SORT_COMLEVEL`, `SORT_ATTACK`, `SORT_STRENGTH`, `SORT_RANGED`, `SORT_MAGIC`, `SORT_DEFENCE`, `SORT_HITPOINTS`,
    `SORT_PRAYER`, `SORT_RUNS`; 42 APPLICANTS (applicant rows).
  - Footer: 46 UNBLOCK, 47 MODE, 49 SIZE, 53 LEVEL, 57 ACTION, 58 TOOLTIP, 61 LEADER_HIGHLIGHT, 62 SORT_BUTTONS (parent of the arrows).
  - 63-72 `P1_UP`, `P1_DOWN` .. `P5_UP`, `P5_DOWN` (static actions "Move up" / "Move down").
  - Packed ids: BACK 3276802, REFRESH 3276809, TIMER 3276814, CURRENT 3276827, MIDDLE 3276828, APPLICANTS 3276842, UNBLOCK 3276846, MODE 3276847,
    SIZE 3276849, LEVEL 3276853, ACTION 3276857.
- **363 TOB_SCOREBOARD** (67 components): 0 UNIVERSE (onLoad 4490 with args `[363:0, 363:4, 363:5, 363:6]`), 1 CONTENTS, 2 FRAME
  (onLoad 227 title "Theatre of Blood Statistics"), 3 TABS, 4 STORY_TAB (action "View"), 5 NORMAL_TAB, 6 HARD_TAB, 7-10 header boxes,
  11 MAIN, 12-25 background rectangles, 26-40 static labels ("Global", "Personal", "Attempts", "Completions", "Deaths", "Best Times",
  "Overall", "Challenge", "1 Player" .. "5 Player"), 41-66 the data cells named `DATA_A_P`, `DATA_A_G` (attempts personal / global),
  `DATA_C_P` / `DATA_C_G` (completions), `DATA_D_P` / `DATA_D_G` (deaths) and, for each team size SOLO, 2MAN, 3MAN, 4MAN, 5MAN, the four
  best-time cells `_P_O`, `_P_R`, `_G_O`, `_G_R` (Personal / Global × Overall / Room). No script writes the data cells, so the server
  fills them with IF_SETTEXT [R/I].
- **364 TOB_PARTYLIST** (68 components): 0 UNIVERSE (onLoad 2337, args `[364:17, 364:63]` = LIST, SCROLLBAR), 1 CANVAS, 2 FRAME
  (onLoad 227 title "Performers for the Theatre"), 3 CONTENTS (pause-button host), 4 LISTLAYER, 5-9 header cells, 10 SORTAREA, 11-16
  `SORT_MEMBERS`, `SORT_TARGETSIZE`, `SORT_NAME`, `SORT_COMLEVEL`, `SORT_MODE`, `SORT_AGE`, 17 LIST, **18-62 rows `_00`..`_44` (enum 1937:
  row index → component)**, 63 SCROLLBAR, 64 REFRESH, 65 TOOLTIP (`TOOLTIP` = 23855169), 66 MYPARTY, 67 FILTER.
- **405 TOB_MIDWAY_STORES** (9 components): 0 FRAME (onLoad 227 title "Theatre of Blood Supplies"), 1 CONTENT, 2 UNIVERSE (onLoad 2346 with
  `[405:4, 405:8, 405:3]`), 3 VAMPYRE_NOTE (model slot, shows the note from inv 612 slot 0), 4 POINTS_TEXT (text "Points Available:
  <col=ffffff>-</col>"), 5-7 backgrounds, 8 ITEMS (layer; script 2346 creates the stock here).
- **459 TOB_INFOBOARD** (title "Performance Details", 57 components): the strategy-table board. Static texts: "Death Counts", "Challenge
  Time", "MVP", "Player -", "Total Deaths", "Overall Time" and the six challenge names "The Maiden of Sugadinti", "Pestilent Bloat", "The
  Nylocas", "Sotetseg", "Xarpus", "Lady Verzik Vitur". Named components (component = packed id - 30081024): `PLAYERNAME0..4` = 459:22, 24, 26, 28, 30 and `DEATHS0..4` = 459:23, 25,
  27, 29, 31 (text cells in the group 459:15), `TOTALDEATHSNAME` 459:32 ("Total Deaths"), `TOTALDEATHS` 459:33, `CHALLENGE_TIME_TIME` 459:11,
  `MVP_PLAYER` 459:14 ("Player -"), `OVERALL_TIME_TIME` 459:36, and per room `CH1_NAME` 459:44 .. `CH6_NAME` 459:54 (even ids) with
  `CH1_TIME` 459:45 .. `CH6_TIME` 459:55 (odd ids) in the order Maiden, Bloat, Nylocas, Sotetseg, Xarpus, Verzik. No script references
  interface 459: the server fills it with IF_SETTEXT [R/I]. The five onLoad 714 hooks are scroll setups.
- **Other**: `TOB_CHESTS`, `TOB_HUD`, `TOB_PARTYDETAILS`, `TOB_SCOREBOARD`, `TOB_PARTYLIST`, `TOB_MIDWAY_STORES` and `TOB_INFOBOARD` are the
  only ToB interfaces in `Interfaces.java`. There is **no lobby overlay interface** (unlike TFA's 975): the "No party / Party / Step inside
  now!" text lives in the HUD (§2.2). There is no spectate-inventory interface. `SOTE_UPASS_BATTLE` 636 is Song of the Elves.

### 1.6 Other ToB symbols
- **Objs** [R]:
  - Uniques: 22323 SANGUINESTI_STAFF (22481 uncharged), 22324 GHRAZI_RAPIER, 22325 SCYTHE_OF_VITUR (22486 uncharged), 22326
    JUSTICIAR_FACEGUARD, 22327 JUSTICIAR_CHESTGUARD, 22328 JUSTICIAR_LEG_GUARDS (set 22438), 22322 Avernic defender and 22477 Avernic defender hilt
    (22441 broken), 22516 (`VERZIK_SPECIAL_WEAPON`, name "Dawnbringer"), 22473 `VERZIKPET` "Lil' Zik".
  - Ornaments: 25742 Holy ornament kit, 25744 Sanguine ornament kit, 25746 Sanguine dust ("TOB_HARDMODE_DUST"); holy: 25731 / 25733 staff,
    25734 rapier, 25736 / 25738 scythe; sanguine: 25739 / 25741 scythe (the `_OR`, `_BL` suffixes).
  - Shrouds 22494-22503 "Sinhaza shroud tier 1..5" (placeholders at +1).
  - Raid items: **22517 `TOB_TELEPORT` "Escape crystal"** (stackable, ring slot 12, iops Teleport / Wear / Auto-afk / Configure / Drop; params
    452-454 label the equip-slot ops); **25730 `TOB_BANDAGES` "Bandages"** (iops Heal / Destroy, untradeable); 22475 `TOB_VAMPYRE_MESSAGE` "Message"
    (Read / Drop). Wiki: Entry Mode gives 10 bandages in place of other supplies; the Message depends on performance.
  - Pets: 22473 "Lil' Zik" (iop Drop only; pet param 446 = 3856) and the metamorph pets 25748 "Lil' Maiden", 25749 "Lil' Bloat", 25750 "Lil'
    Nylo", 25751 "Lil' Sot", 25752 "Lil' Xarp".
  - Lore items 22504 (Serafina's diary), 22506 "The Butcher", 22508 "Arachnids of Vampyrium", 22510 "The Shadow Realm", 22512 "The Wild Hunt",
    22514 "Verzik Vitur - Patient Record" (symbols `TOB_BOOK_*`), 25753 `TOBQUEST_LAMP`, 25904 / 25906 Vampyric slayer helmet (VERZIK).
  - Midway shop stock (enum 1952): 12625 Stamina potion(4), 2434 Prayer potion(4), 6685 Saradomin brew(4), 3024 Super restore(4), 7058
    Mushroom potato, 385 Shark, 397 Sea turtle, 391 Manta ray.
- **Jingles** [R]: 854 VERZIK_AMBIENCE, 855 XARPUS_AMBIENCE, 856 PESTILENT_BLOAT_AMBIENCE, 857 NYLOCAS_COMBAT, 858 SOTETSEG_COMBAT, 859
  XARPUS_COMBAT, 860 WELCOME_TO_THE_THEATRE_6, 861 NYLOCAS_AMBIENCE, 862 MAIDEN_AMBIENCE, 863 PESTILENT_BLOAT_COMBAT, 864 MAIDEN_COMBAT, 865
  VERZIK_DEFEATED, 866 SOTETSEG_AMBIENCE, 867 VERZIK_COMBAT, **1025 TOB_BOSS_KILLED_JINGLE** (6 `DREAM_THEATRE` belongs to a quest).
- **Music** (table 44, columns 3 midi, 4 sprite, 5 `(18, bit)` music-unlock position) [R]:

  | dbrow | track | midi | unlock bit |
  |---|---|---|---|
  | 3132 | Welcome to the Theatre ("at Ver Sinhaza.") | 800 | (18, 8) |
  | 2885 | The Maiden's Sorrow (ambience) | 893 | (18, 10) |
  | 2886 | The Maiden's Anger (combat) | 680 | (18, 11) |
  | 2969 | Welcome to my Nightmare (Bloat ambience) | 759 | (18, 12) |
  | 2970 | The Nightmare Continues (Bloat combat) | 742 | (18, 13) |
  | 3054 | The Dark Beast Sotetseg (ambience) | 605 | (18, 14) |
  | 3055 | Power of the Shadow Realm (Sotetseg combat) | 755 | (18, 15) |
  | 3185 | It's not over 'til... (Verzik ambience) | 430 | (18, 16) |
  | 3186 | The Fat Lady Sings (Verzik combat) | 681 | (18, 17) |
  | 3187 | The Curtain Closes (Verzik defeated) | 715 | (18, 18) |
  | 2948 | Dance of the Nylocas (ambience) | 594 | (18, 19) |
  | 2949 | Arachnids of Vampyrium (Nylocas combat) | 696 | (18, 20) |
  | 3233 | Predator Xarpus (ambience) | 500 | (18, 21) |
  | 3234 | Last King of the Yarasa (Xarpus combat) | 708 | (18, 22) |

  All unlock hints read "in the Theatre of Blood.". The ambience/combat pairing is the jingle names above [R]; which jingle plays in which room
  phase is server logic [?]. Other dbrows: `SYNTH_TOB` 4858 and `ACTION_GET_TOB_UNIQUE` 6776 / `ACTION_COMPLETE_TOB` 6844 are named rows
  with no readable columns here [?]; `DROP_INSTANT_REVEAL_TOB` 17023 (table 135) is stripped.
- **Seqs** (`Seqs.java`) [R], by boss:
  - Maiden: 8090 MAIDEN_IDLE, 8091 ATTACK_BLOOD, 8092 ATTACK_SPECIAL, 8093 DEATH_A, 8094 DEATH_B, 14399 MAIDEN_SPAWN, 14400 VFX_MAIDEN_SPAWN,
    14520 MAIDEN_PET_IDLE; blood spawn 8101 walk / 8102 idle / 8103 death; 8099 TOB_BLOOD_SPLAT.
  - Bloat: 8080 READY, 8081 WALK, 8082 SLEEP, 8083 FLIES_LARGE, 8084 FLIES_SMALL, 8085 DEATH, 8086 CHAMBER_IDLE, 8087 SWINGING_CHAIN, 8088
    FALLING_FLESH, 8089 STUNNED_SHORT, 9031 WALK_PET.
  - Nylocas: 8073 NYLOCAS_PILLAR_PRECOLLAPSE, 8074 PILLAR_COLLAPSE, 8076-8079 TOB_SPIDER_TANK (idle / walk / death / spawn; used by Athanatos),
    shared nylo walk 8002 / 8003.
  - Sotetseg: 8136 WALK, 8137 IDLE, 8138 ATTACK_MELEE, 8139 ATTACK_RANGED, 8140 DEATH, 8141 WALL_FLOAT, 8142 SHADOW_PORTAL, 8143 TILE_GLOW, 8144
    SHARED_PROJECTILE, 8100 / 9004 / 9005 / 13134 TOB_SHADOW_PROJECTILE (+ SPAWN / DESPAWN / IDLE), 9032 WALK_PET.
  - Xarpus: 8058 IDLE, 8059 ATTACK_RANGED, 8060 ABSORB, 8061 FLY_UP, 8062 / 8063 DEATH_A / B, 8064-8066 EXHUMED start / loop / end, 8067-8069
    ACID_SPLAT start / loop / end, 8070 TOB_PURGATORY_STANCE, 9033 PET_IDLE.
  - Verzik: 8051 HUMAN_IDLE, 8052 PILLAR_COLLAPSE, 8053 / 8108 THRONE_TRANSFORM (initial / later), 8054 / 8055 CHATHEAD, 8104 PILLAR_FADE, 8105
    TREASURE_ROOM_TELEPORT_CRYSTAL, 8106 THRONE_ROOM_ARROW_FLOAT, 8107 / 8109 / 8110 / 8111 P1 idle / attack magic / defend / death, 8112-8118 P2
    spawn / idle / attack magic / lightning spot / attack melee / heal / death, 8119-8129 P3 spawn / idle / walk / walk pet / attack melee / magic /
    ranged / powerblast / webspin / death A / death B, 8130 WEBSPIN, 8131 RANGED_VILE, 8132 LIGHTNING_IMPACT, 8133 BLOOD_PROJECTILE, 8134 BLOOD_IMPACT,
    8135 PET_CHATHEAD_TALK, 9142 / 9143 VERZIK_YOUNG idle / walk, 13135 PHASE3_IDLE_PET, 14406 PHASE3_ATTACK_SUMMON.
  - Out of scope: 12647-12655 `LORE_BLOAT_LEECH_*`, 14381-14386 `NYLOCAS_QUEEN_*` (the Nylocas Queen is quest content).
- **SpotAnims** (`SpotAnims.java`) [R]:
  - Xarpus 1549-1557: EXHUMED_END, EXHUMED_ENERGYORB, ACIDPOOL_END_0..3, ACIDSPIT, ACIDSPLASH, GUANO.
  - Nylocas 1558-1567: SHIELDED, RANGEDPROJECTILE_SIZE1 / SIZEMID / SIZE2, DEATH_MELEE / RANGED / MAGIC × STANDARD / DETONATE.
  - Bloat 1568-1573 and 1575, 1576: FLIES_LARGE / SMALL, FALLING_FLESH1-4, STUNNED, BLOOD_SPLAT.
  - Maiden 1577-1579 and 3982-3985: SHADOW_PROJ, BLOOD_PROJ, LINGERING_BLOOD (+ _SW / _NW / _NE), VFX_MAIDEN_SPAWN.
  - Verzik 1580-1602 and 3028: PHASE1_PROJECTILE / IMPACT / IMPACTPILLAR, PHASE2_RANGED, RANGED_IMPACT, PHASE2_LIGHTNING, SPAWN_ARMOUREDTANK_PROJ,
    BLOODGLOBULE, POISONGLOBULE, SPAWNING, POISONAURA, BLOODPROJ, BLOODIMPACT, PHASE3_RANGEPROJ / MAGEPROJ, POWERBLAST_SAFEZONE (3028 `_QUICK`),
    POWERBLAST_PROJANIM_DOWN, POWERBLAST_PROTECTION, ACIDBOMB_PROJANIM / SMALL_IMPACT / IMPACT, P3_WEB_PROJ, P3_CREEPER_SPOT.
  - Sotetseg 1603-1608: ZAP, SHAREDATTACK, SHAREDATTACK_IMPACT, MAGING, RANGING, DRAIN.
- **Mid-raid shop (interface 405)** is **not** an omnishop table (unlike TFA): stock and prices come from enums 1952 (slot → obj) and 1953
  (obj → points) [R, §2.5].

---

## 2. Varbit encodings and the scripts that use them

### 2.1 HUD 28: party orbs, names, progress bar and spectator exit (onLoad 2087)
- 2087 builds the progress bar rectangles (two children, track + fill) and one text and one tiling-sprite child per orb (2295), then runs the
  first draw (2297) and registers four hooks on UNIVERSE 28:0, all running 2296 → 2297 [R]:
  - **onVarTransmit**: varps **1745, 1737, 1227, 1055** (1227 `XPDROPS_OPTIONS`, 1055 `CHAT_FILTER_ASSIST` are client option varps that change the
    layout; the server only needs to send 1745 and 1737).
  - **onResize**, **onSubChange**: full redraw.
  - **onTimer**: redraws only when `clientclock >= varc 219` (every 30 client cycles, 0.6 s).
- **2297 (draw)** [R]:
  1. **Lobby text.** If the player's coord lies in the box (3640, 3202, 0) .. (3684, 3236, 1) (Ver Sinhaza, the two args of script 247) and
     `PARTYSTATUS != 3`, the NAMES_CONTAINER shows the header text on 28:11: status **2** → "Step inside now!" with a colour that flashes every
     frame (scripts 2302 / 2303, `clientclock mod 90` ramp); status **0** → "No party" (0xFF981F); any other value (1) → "Party" (0xFF981F).
     Status 3 hides the container. Outside the box the container is hidden. (TFA's lobby text is a separate interface; here it shares the HUD.)
  2. **In-raid test.** The raid UI draws only when `coordx >= 6400` (script 1935) **and** `PARTYSTATUS == 2`. **The instance's real coordinates
     must have x >= 6400** (map square x >= 100) or the HUD never appears; with any other status or an outside coordinate the whole HUD hides and
     script 41 clears the dynamic children [R].
  3. **Progress bar** (28:36 / 28:37). `WAVEPROGRESS_TYPE` (6447) selects the style and `VAL` / `MAX` (6448 / 6449, 10 bits each) the fill:
     fill = `VAL * 16384 / max(MAX, 1)` as a width ratio (script 2305 eases it); text = `VAL * 1000 / max(MAX, 1)` shown as `<p/10>.<p%10>%`.

     | TYPE | track / fill colour | text |
     |---|---|---|
     | 0 | bar hidden, varc 220 reset | none |
     | 1 | track 0xFF0000, fill 0x0DC10D | "x.y%" |
     | 2 | track 0x7F5813, fill 0x7F7FFF | empty |
     | 3 | track 0x002620, fill 0x00D2AC | "<col=ff0000>x.y%</col>" |

     What each type is used for (waves, a boss meter, ...) is [?]; the name `WAVEPROGRESS` and the 10-bit pair suggest Nylocas waves for type 1 [I].
  4. **Orbs.** Slot k = 0..4 is drawn from `P_k` (6442 + k) and the name varc 330 + k via 2298. `PARTYSLOT` (6441): non-zero (1-based index of the
     viewer's own slot) → the spectator container 28:13 is hidden and STATUS_CONTAINER 28:7 is 34 × 174 px; **0 → the spectator container with the
     "Exit" button is shown** and 28:7 is 34 × 209 px. **2298 per slot** [R]:
     - `P == 0`: slot hidden.
     - Text is the first letter of the name ("-" if empty or "null"), or **"Me"** when `k == PARTYSLOT - 1`.
     - `P == 31`: filler sprite 1062, full 26 px, text colour 0x7F0000, no HP refresh.
     - `P == 30`: text colour 0xFF0000, filler height 0 (red name, empty orb).
     - `P` 1..29 (27 used): white text, filler sprite 1060, filler height `min(26, P - 1)` px. So **P = 1 + round(26 * hp / maxhp)** [I]. For the
       viewer's own slot 2300 ignores P and sizes the filler from the local `stat(3) / stat_base(3)` scaled to 26 px (minimum 1 px while HP > 0), and
       the slot has an onStatTransmit hook on stat 3.
     - Mouse over shows the full name on 28:38 (script 526); mouse leave clears it (script 40).
     - 30 = dead and 31 = a variant of "full" with dark-red name are [I]/[?]: what 31 stands for (disconnected? downed?) is not in the cache.
  5. **Names** come from varcs 330-334; the server sets them with **script 2301** (5 strings, `NAME0` first) which also zeroes varc 219 [R].
- **Team size for RuneLite**: the chat-commands plugin counts `min(P_k, 1)` over 6442-6446 for the kill-count / PB team size, so the server must
  keep P non-zero for every member during the run and at the completion message [R].
- **Spectator exit**: 28:15 has action 0 "Exit" and `onOp` 489 (sound only). The server must handle IF_BUTTON1 on 28:15 [R].

### 2.2 Lobby overlay
There is no separate overlay interface. The lobby text rides on the HUD (§2.1, item 1) and is driven by `PARTYSTATUS`:

| value | text in the lobby box | in-raid HUD |
|---|---|---|
| 0 | "No party" | hidden |
| 1 | "Party" | hidden |
| 2 | "Step inside now!" (flashing) | shown (if coord x >= 6400) |
| 3 | nothing | hidden |

Value 3 has no other reader [R]; its meaning is [?] (probably "run finished" or "in raid but not displayed"). The 2-bit status belongs to varp
1745, so the server must send 1745 when the party forms, when the run starts (2 for members in the lobby), and when members enter.

### 2.3 Party details 50 (onLoad 2316; server-run 2317, 2321, 2323, 2329) [R unless marked]
- **2316** (args: `this`, TOP 50:4, HEADER 50:8, TIMER 50:14, CURRENT 50:27, MIDDLE 50:28, SORTAREA 50:31, APPLICANTS 50:42, BACK 50:2, REFRESH
  50:9, UNBLOCK 50:46, SIZE 50:49, LEVEL 50:53, ACTION 50:57, MODE 50:47) zeroes varc 178, puts "Loading..." in CURRENT and MIDDLE, hides most groups
  and creates **hidden pause-button children under 50:0 UNIVERSE** (the host). Resumes arrive as RESUME_PAUSEBUTTON on **50:0** with sub-id =
  child index:
  - 0-6 from enum 1939 (component → index): **0 BACK, 1 REFRESH, 2 UNBLOCK, 3 SIZE ("Preferred Size"), 4 LEVEL ("Preferred Level"), 5 ACTION, 6 MODE**.
  - **7 + row** (row 0..4): "Kick" (op 10; row 0 is the viewer's own row and reads "Quit party") (2319 / 2320).
  - **12 + row** (row 0..9): accept applicant (op 1 "Accept"); **22 + row**: reject applicant (op 10 "Reject") (2330 / 2331).
  - So the sub-ids are 0-31. The Back button resumes sub 0 through 4496 / 4497; Refresh, Unblock, Size, Level, Mode and Action go through the
    generic buttons (890, 1558, 2326, 2327 for the leader's numeric rows). The "Set" ops on Size / Level / Mode only exist for the leader.
- **2317(int role, string row)**: one call per member row, row index = varc 178 which it increments; **exactly five calls (rows 0..4)**, empty
  string for vacant rows. Row format `name|combat|attack|strength|ranged|magic|defence|hitpoints|prayer|runs` ('|'-separated, split by script 632);
  empty fields show "-". The first call titles the frame "Party of <name>" (empty name → "Leaderless party"). The viewer's own row is white,
  others 0xFF981F (a case-insensitive compare with the local name). When `role == 2` (leader) and the name is non-empty the row gets the kick op and the
  "Move up" / "Move down" arrows 50:63-72 (script 3989, row 0 → `P1_UP/DOWN` .. row 4 → `P5_UP/DOWN`; they carry plain IF_BUTTON1 actions and
  only play a sound client-side, so the server must handle IF_BUTTON1 on those components and re-validate) [R/I]. The tenth column header carries
  the tooltip "Performance counter" (a completion count, [I]).
- **2321(string row)**: applicant row, same 10-field format, written to 50:42; the applicant index is `varc178 - 5` (max 10 applicants). The hidden
  sort-key child is the name with tags removed, padded by 1503 to 13 characters. With no applicants 2323 shows "No current applicants".
- **2323(int role, int size, int level, int mode)**: footer and settings; must come **after** the five 2317 calls and the 2321 calls. `size` and
  `level` render as `<label>: <value>` (":" + "-" when 0); `mode` goes through **enum 3852** (0 Normal, 1 Entry, 2 Hard). Only the leader
  (role 2) gets the "Set" op (script 2326 → 2327) on SIZE / LEVEL / MODE. The labels "Preferred Size", "Preferred Level", "Mode" are static. The ACTION
  button by role: **2 leader** "Disband" (red) plus UNBLOCK "Unblock" (yellow); **1 member** "Leave"; **3 applicant** "Withdraw"; **4 cannot
  apply** greyed "Apply"; **0 (any other)** "Apply" (green). Also hooks a 500-cycle auto-refresh timer on 50:0 (2332): the TIMER bar 50:14 shrinks
  over 500 client cycles (10 s), then the client itself resumes the REFRESH pause button (sub 1), so the server receives a REFRESH every 10 s
  while the screen stays open and should treat it as an ordinary request.
- **2329(int index, string)**: relabels one of the seven enum-1939 buttons ("tob_partydetails_forcebutton") [R/I].
- Sorting is client-local (varc 181; 2333-2336): clicking a header sorts the displayed applicants and members only; it sends no packet.

### 2.4 Party list 364 (onLoad 2337; server-run 2340)
- **2337** hides every row (enum 1937), shows "Loading...", clears the host 364:3 CONTENTS and creates the pause buttons from enum 1938: **sub 0
  REFRESH (364:64), 1 MYPARTY (364:66), 2 FILTER (364:67)**, all hosted on **364:3** [R]. It sets varc 59 = `clientclock` (the age base) and
  hooks varp transmits: **1740 → 2338** (My party / Make party) and **3052 → 5218** (the Friends/Clan filter look). The MYPARTY label is "Make
  party", or "<col=ffff00>My party</col>" when varp 1740 != -1; FILTER is "Friends/Clan" or green "<col=0dc10d>Friends/Clan</col>" when
  varbit 12988 = 1.
- **2340(int row 0..44, string)**: one call per row; **the layout is finalised only when the call with row index 44 (enum size - 1) arrives**, so
  the server must send **all 45 calls in order, 0..44**, with `""` for vacant rows [R]. If no row had content (varc 178 = 0) the list shows "No parties
  are currently listed." (filter off) or "You have no friends or clanmates in a party." (filter on).
- Row string: 10 '|'-separated fields:

  | # | field |
  |---|---|
  | 1 | leader name (shown in column 13, tags kept) |
  | 2-5 | other members (up to five members in all); used only for the Friends/Clan filter (script 5217: self, friend, or clan mate/non-ignored) |
  | 6 | member count (column 11 "Members") |
  | 7 | preferred size (column 12, shown "/ n", "-" when "0"); the cell is red when members >= preferred size |
  | 8 | preferred combat level (column 14, "-" when "0") |
  | 9 | mode text (column 15, e.g. "Normal" / "Entry" / "Hard") |
  | 10 | age in ticks (column 16; 1574 turns ticks into `m:ss` using varc 59; the server therefore sends `now - creation tick`) |

  The filter shows a row only if the viewer is a member/leader or the member names pass 5217 [R]. The mode text is whatever string the server sends;
  the party-details screen uses enum 3852, so use the same words [I].
- Rows carry the op **"View party:"** (op 1, `<col=ff9040>leader</col>`) on the row component itself (364:18 + row), i.e. a plain IF_BUTTON1 on
  364:18..62. Sorting is client-local (varc 180, scripts 2341-2343).

### 2.5 Mid-raid supplies 405 (onLoad 2346)
- **2346**(args `405:4`, `405:8`, `405:3`) sets the points text "Points Available: <col=ffffff>{varbit 6460}</col>" (and again on varp **1746** transmit via
  2349), draws the notes slot from **inv 612 slot 0** (2348, re-run on inv 612 transmit) and builds the stock in layer 405:8 from **enum 1952**:
  slot 0..7 → 12625, 2434, 6685, 3024, 7058, 385, 397, 391. For each: an item icon child (child index 0..7) and a text child (8..15) with the point
  price from **enum 1953** (obj → points): 12625 → 1, 2434 → 2, 6685 → 3, 3024 → 3, 7058 → 1, 385 → 1, 397 → 2, 391 → 2.
- **Stamina potion (12625)**: displayed with quantity **varbit 12271** (`cc_setobject_always_num`), greyed (transparency 128) at 0; ops "Value" (1),
  "Buy-1" (2), "Examine" (10) only. So 12271 is the number of stamina potions still available from this chest (Wiki: one per chest) and it
  redraws on varp 1746 [R/I].
- **Other items**: ops "Value" (1), "Buy-1" (2), **"Buy-<x>" (3) only when varbit 12272 != 0**, "Buy-All" (4), "Buy-X" (5), "Examine" (10). So
  12272 is the last custom quantity (Wiki: "previous quantities") [R/I].
- **Note slot 405:3**: op 1 "Take" and op 10 "Examine" on the first item of inv 612 (the Message / bandages / onion the Wiki lists) [R/I].
- Currency is varbit **6460** (0..31 points); **6461** (booby prize) has no reader [?]. Both live in the persistent varp 1746.
- Server packets: IF_BUTTON ops 1, 2, 3, 4, 5, 10 on **405:8** with the child index (0-7 for the stock) and IF_BUTTON1 / 10 on **405:3**. The
  interface needs IF_SETEVENTS on those components (not on 405:8 only: 405:3 is a model slot) [I].

### 2.6 Scoreboard 363 and performance board 459
- **363**: three tabs STORY_TAB 363:4 (Entry), NORMAL_TAB 363:5, HARD_TAB 363:6, each with action "View". 4490 hooks `onOp` → 4493 on each tab, which
  does `set_varbit 12270` **locally** to **1 (Entry, story tab), 0 (Normal), 2 (Hard)** and redraws the tab sprites (4492). The same IF_BUTTON
  reaches the server, which must set varbit 12270 too (keeping varp 1746 in sync) and **re-send the data cells for that mode** (5 team
  sizes × Personal/Global × Overall/Room times, plus attempts / completions / deaths) [R/I]. Global means world-wide; the cells are text.
  Text layout of the best times is not in the cache (the server chooses `m:ss.t` per the other raid messages) [?].
- **459** (Performance Details): static layout only; see §1.5. The Wiki describes it as opened by the strategy table; the player rows, deaths per
  player, MVP, the challenge (room) times and the overall time are all server text [R/I].

### 2.7 Chests, wells and lobby objects
- **Treasure-room chests (5)**: loc multis **33086..33090** (`TOB_TREASUREROOM_CHEST_LOC0..4`) on varbits **6450..6454**. Each has the same
  list `[32990, 32991, 32992, 32993, 32990]`, so (value 0..3, anything else → the last entry):

  | value | loc | symbol |
  |---|---|---|
  | 0 | 32990 "Monumental chest" (op1 "Open") | `TOB_TREASUREROOM_CHEST_NOTMINE_STANDARD` |
  | 1 | 32991 | `..._NOTMINE_RARE` (purple) |
  | 2 | 32992 | `..._MINE_STANDARD` |
  | 3 | 32993 | `..._MINE_RARE` |

  All four carry "Open", so the server must reject opening someone else's chest [R/I]. Chest index k belongs to party slot k [I]. The opened state
  is a different loc, 32994 `..._OPEN` (op1 "Search") or 41746 `..._OPEN_RARE` (op1 "Search"), spawned or swapped by the server (no multi points
  at them) [R/I]. Chest *i* shows the viewer's own state because the varbits are per-player [I]. Interface 23 and inv 612 are opened when the chest
  is used (RuneLite's loot tracker needs the inv before the interface).
- **Lobby rewards chest**: loc multi **41437** (also 41743, the same list; the NOOP variants 41744 / 41745 have no ops) on varbit **11958
  `TOB_SHOULD_HAVE_LOOT`**: `[41436, 41435, -1]` → value 0 = 41436 `REWARDS_CHEST_LOBBY_CLOSED`, value 1 = 41435 `..._OPEN`; both have op1 "Claim"
  [R]. So 11958 = 1 when the player has loot waiting (server sets varp 1746) [I].
- **Lobby wells**: loc multi **33085** on varbit **6455** (14 bits): `[32985, 32654]` → value 0 = 32985 "Vyre well" (ops Fill / Check), value >= 1 = 32654
  "Vyre well" (ops Charge-at / Fill / Check / Empty) [R]. 6455 is the amount stored in the well [I] ("Charge-at" refers to the blood-charged
  weapons; the Wiki lists Scythe / Sanguinesti staff charging).
- **Gravestone chest** 32656 "Chest" (op1 "Claim") sits twice in the lobby; its server rule is [?] (Wiki: Item Retrieval Service).
- **Midway chest locs**: 32758 "Chest" (op1 "Open") and 32759 (op2 "Search"); neither is placed in the static map (§5), so the server spawns them
  (Wiki: after Pestilent Bloat and after Sotetseg).

### 2.8 Boss-state locs and NPC multis
- **Verzik's throne**: multi 32736 on varbit 6400 `[-1, 32686]`: 0 = nothing, 1 = 32686 "Verzik's throne" (5x4) [R]. Placed at (3166, 4324, L1).
  Non-multi variants exist for the transitions: 32737 "Verzik's throne" (`..._THRONE_TRANSFORMING`, 3x4) and 32738 (`..._THRONE_DOOR_OPENED`,
  named "Treasure room", op1 "Enter", 3x4), which is the exit into the treasure room after the kill [R name, I purpose].
- **Sotetseg hard-mode tiles**: locs 41750 / 41751 / 41752 / 41753 (`..._LIGHTTILE_PARENT_1..4`, glow anim 8143) are multis on varbit 12269
  (`TOB_SOTETSEG_HARD_TILES_VISIBLE`, 3 bits) listing `41749` (light tile) at index **1, 2, 3, 4** respectively and `41748` (dark) otherwise
  (41750: `[41748, 41749, 41748]` .. 41753: `[41748,41748,41748,41748,41749,41748]`). So the server places one parent loc of each type on the tiles of
  group 1..4 and shows group n by setting 12269 = n; 0 or > 4 shows all dark [R/I]. Normal-mode maze tiles are plain locs: 33033 PLAINTILE, 33034
  DARKTILE (placed 210 times in each of the arena and the maze), 33035 LIGHTTILE (glow anim 8143), 33036 TRIGGEREDTILE, 33037 "Portal" (8x2, op1
  "Enter", `DARKREALM_EXIT`).
- **Lil' Zik / pet NPCs**: 8336 POH_VERZIK_PET and 8337 VERZIK_PET are both "Lil' Zik" (8337 ops Talk-to / Metamorphosis / Pick-up). The four
  metamorphs are NPCs 10870-10874 (and POH 10761-10765), see §4.
- **Verzik start NPC** (8369 / 10847 / 10830): §4 (multi on completion varps).

### 2.9 Spectator UI
- HUD (§2.1): `PARTYSLOT == 0` shows the spectator container 28:13 with the "Exit" button 28:15 and a wider header.
- NPC **8368 "Abigaila"** (ops Talk-to / **Scry**, `TOB_SPECTATOR_MISTHALIN`) offers spectating; a "Leaflet" 32533 (op1 "Read",
  `TOB_ANNOUNCEMENT_LEAFLET`) and an "Easel" 32685 (`TOB_SPECTATOR_MISTHALIN_EASEL`) decorate the spectators' area. In each room the L1 balcony
  has spectator barriers 32760 / 32761 / 32762 (no ops) [R]. Spectator entry, party slot (0) and the camera are server logic [?].
- There is no spectator-count display (unlike TFA) and no skip-phase / spectate-room button [R].

---

## 3. Interfaces: onLoad and open scripts

| interface | static hooks | server-invoked scripts (no client caller) |
|---|---|---|
| 23 TOB_CHESTS | 23:1 onLoad 227 (title). Ops 489 (sound only) | none (inv 612 → 23:12 by the standard inv-draw script) |
| 28 TOB_HUD | 28:0 onLoad **2087**; 28:5 onLoad 991; 28:9 onLoad 714 | **2301**(5 s) names; **2306**(i, i, s) fade; **2307**(s) portal; **2308**(s) Sotetseg fade-out. Client-internal (timers): 2296 / 2297 draw, 2304 / 2305 progress easing, 2310-2312 fade steps, 2313 / 2314 portal model, 2315 text clear |
| 50 TOB_PARTYDETAILS | 50:0 onLoad **2316**; 50:8 / 50:31 onLoad 714 | **2317**(i s), **2321**(s), **2323**(4 i), **2329**(i s) |
| 363 TOB_SCOREBOARD | 363:0 onLoad **4490**; 363:2 onLoad 227 | none (cells by IF_SETTEXT) |
| 364 TOB_PARTYLIST | 364:0 onLoad **2337**; 364:2 onLoad 227; 364:4 / 364:10 onLoad 714 | **2340**(i s) |
| 405 TOB_MIDWAY_STORES | 405:2 onLoad **2346**; 405:0 onLoad 227 | none |
| 459 TOB_INFOBOARD | 459:1 onLoad 227; 459:4-8 onLoad 714 | none |

- **Fade scripts (HUD 28:1 ATMOSPHERIC)**: 2306(trans, immediate, text) fades the full-screen rectangle to black with target transparency `trans`
  (0 opaque .. 255 clear) over 20 client cycles, or at once when `immediate == 1`; the optional text appears centred (font 645, colour 0xC80032) and clears after
  150 cycles (2315). 2307(text) fades to dark red 0x400000 and plays the portal model 35737 animation 7301 (2313 / 2314); 2308(text) fades to
  0xDFDFDF (the Sotetseg shadow-realm transition) [R]. Their use per room is [I].

Client → server traffic [R]:

| kind | component | args | source |
|---|---|---|---|
| IF_BUTTON1 | 28:15 | | spectator "Exit" |
| RESUME_PAUSEBUTTON | 50:0 | sub 0-31 (§2.3) | party details |
| IF_BUTTON1 | 50:63-72 | | member "Move up" / "Move down" |
| RESUME_PAUSEBUTTON | 364:3 | sub 0-2 (§2.4) | party list |
| IF_BUTTON1 | 364:18-62 | | "View party:" row |
| IF_BUTTON1 | 363:4 / 363:5 / 363:6 | | scoreboard tab (Entry / Normal / Hard) |
| IF_BUTTON 1, 2, 3, 4, 5, 10 | 405:8 (child 0-7), 405:3 (ops 1, 10) | | supplies |
| IF_BUTTON 6 / 1 / 6 | 23:6 / 23:8 / 23:10 | | Bank-all / Take-all / Discard-all |
| OPLOC | 32653, 32654, 32655, 32656, 32758, 32755 ..., see §5 | | locs |

There are **no IF_SCRIPT_TRIGGER** hooks in any ToB script (no `crc` args to supply), unlike TFA [R]. The pause-button resume packets
RESUME_PAUSEBUTTON need the number dialogs of the leader's Size / Level settings to be opened by the server (sub 3 and 4), whose bounds are [?].

---

## 4. NPCs [R]
The `stats` order is attack / defence / strength / hitpoints / ranged / magic (NPC opcodes 74-79). `cb` = combat level. Every mode id below was
read from `config npc`; "Entry" = the `*_STORY` ids, "Normal" = unsuffixed, "Hard" = `*_HARD`.

**The Maiden of Sugadinti** (size 6, stand seq 8090, ops Attack, category 1242):

| form | Normal | Entry | Hard |
|---|---|---|---|
| 100% / 70% / 50% / 30% | 8360 / 8361 / 8362 / 8363 | 10814 / 10815 / 10816 / 10817 | 10822 / 10823 / 10824 / 10825 |
| dying A / B | 8364 / 8365 | 10818 / 10819 | 10826 / 10827 |
| stats (cb) | 350/200/350/**3500**/350/350 (940) | 140/80/140/**500**/140/140 (324) | as Normal (1128) |
| Nylocas Matomenos `MAIDEN_ELEMENTAL` (size 2) | 8366: 100 × 6, cb 115 | 10820: 30/30/30/16/30/30, cb 68 | 10828: as Normal, cb 138 |
| Blood spawn `MAIDEN_BLOOD_SLUG` (size 1) | 8367: 1/0/1/120/1/0, cb 55 | 10821: HP 10, cb 35 | 10829: HP 120, cb 183 |

Also 11958 `MAIDEN_TRANSMOG` "The Maiden of Sugadinti", size 5, stats 1, no ops (a cinematic form, [?]).

**Pestilent Bloat** (size 5, ops Attack, stand 8080, walk 8081): Normal **8359** 250/100/340/**2000**/180/150 cb 870; Entry **10812**
100/80/180/**320**/72/120 cb 312; Hard **10813** 250/100/340/**2400**/180/150 cb 1044. 15556 `DEADMAN_BREACH_BLOAT` is a Deadman variant (HP 750).

**The Nylocas** (sizes 1 / 2; the colour name encodes the style: Ischyros melee, Toxobolos ranged, Hagios magic; all "Attack"):

| NPC | Normal | Entry | Hard |
|---|---|---|---|
| small incoming melee / ranged / magic | 8342 / 8343 / 8344 | 10774 / 10775 / 10776 | 10791 / 10792 / 10793 |
| big incoming (size 2) | 8345 / 8346 / 8347 | 10777 / 10778 / 10779 | 10794 / 10795 / 10796 |
| small fighting | 8348 / 8349 / 8350 | 10780 / 10781 / 10782 | 10797 / 10798 / 10799 |
| big fighting | 8351 / 8352 / 8353 | 10783 / 10784 / 10785 | 10800 / 10801 / 10802 |
| Vasilias (size 4) spawning / melee / magic / ranged | 8354 / 8355 / 8356 / 8357 | 10786 / 10787 / 10788 / 10789 | 10807 / 10808 / 10809 / 10810 |
| Prinkipas miniboss (size 3), Hard only: spawning / melee / magic / ranged | none | none | 10803 / 10804 / 10805 / 10806 |
| support pillar NPC (size 3, ops none) | 8358 | 10790 | 10811 |
| stats | small 200/1/160/**11**/200/1 cb 162; big 250/20/230/**22**/250/20 cb 260; Vasilias 400/50/350/**2500**/350/50 cb 800; pillar HP 130 | small 80/1/40/**2**/80/1 cb 46; big 100/6/90/**3**/100/6 cb 82; Vasilias melee / magic / ranged 160/50/140/**360**/140/20 cb 301 (the spawning form keeps 400/50/350/2500); pillar HP 155 | as Normal for the small and big ones; Prinkipas 400/25/230/**400**/250/25 cb 400; pillar HP 150 |

All the small / big ones use stand 8002 / walk 8003; the fighting forms are separate ids so the server can swap form on arrival [I].

**Sotetseg** (size 5, stand 8137, walk 8136): Normal **8387** noncombat / **8388** combat (Attack) / 8389 creeper (size 3, invisible helper);
stats 250/200/250/**4000**/250/250 cb 995. Entry **10864** / **10865** / 10866: 180/150/140/**560**/150/150 cb 336. Hard **10867** / **10868** / 10869:
350/200/250/**4000**/250/250 cb 1195.

**Xarpus** (stand 8060 / 8058): Normal 8338 static (size 3) / 8339 feeding (size 3) / **8340 combat (size 5, Attack)** / 8341 death (size 5); stats
1/250/1/**5000**/100/220 cb 960. Entry 10766 / 10767 / **10768** / 10769: 1/100/1/**520**/50/80 cb 331. Hard 10770 / 10771 / **10772** / 10773:
1/200/1/**6000**/100/220 cb 1160.

**Verzik Vitur** (size 5 / 3 / 7):

| form | Normal | Entry | Hard |
|---|---|---|---|
| initial (talk) | 8369 (multi, see below) | 10830: "Talk-to" only, cb 425 | 10847 (multi) |
| phase 1 | 8370: 400/20/400/**2000**/400/400 cb 1040, size 5 | 10831: 180/10/150/**300**/180/180 cb 425 | 10848: as Normal |
| P1 → P2 transition | 8371 (HP 3500) | 10832 | 10849 |
| phase 2 | 8372: size 3, 400/200/400/**3500**/400/400 cb 1265 | 10833: 200/120/150/**400**/180/180 cb 488 | 10850 |
| P2 → P3 transition | 8373 (size 7) | 10834 | 10851 |
| phase 3 | 8374: size 7, 400/150/400/**3500**/300/300 cb 1520 | 10835: 180/120/200/**600**/180/180 cb 512 | 10852 |
| death bat (size 3) | 8375 | 10836 | 10853 |
| web `<col=00ffff>Web</col>` (size 1, HP 10) | 8376 | 10837 (HP 1) | 10854 |
| collapsing pillar / fading rubble (size 5) | 8377 / 8378 | 10838 / 10839 | 10855 / 10856 |
| "Supporting Pillar" (size 3) | 8379: HP 185 | 10840: HP 200 | 10857: HP 185 |
| throne NPC (size 3, invisible) | 8380 | - | - |
| Nylocas melee / ranged / magic (size 2, "Attack") | 8381 / 8382 / 8383: HP 25 cb 162 | 10841 / 10842 / 10843: HP 3 cb 46 | 10858 / 10859 / 10860 |
| Nylocas Athanatos (size 3, def 50) | 8384: HP 180 cb 350 | 10844: HP 30 cb 107 | 10861 |
| Nylocas Matomenos (size 2) | 8385: def 40 HP 200 cb 115 | 10845: HP 20 cb 68 | 10862: cb 138 |
| creeper (size 1, invisible) | 8386 | 10846 | 10863 |

- The **start NPC is a multi on a completion varp** [R]: 8369 is `multiVarp = 1748` with `[14795, 14796]`: value 0 → **14795** "Verzik Vitur"
  (Talk-to), any value >= 1 → **14796** (Talk-to, **Quick-start**; the last entry is the default). Hard 10847 is `multiVarp = 3057` with
  `[14797, 14798]` and **both** have "Talk-to" + "Quick-start". Entry has no quick start. So the Quick-start op appears after the first completion of
  Normal Mode, and always in Hard Mode (the Hard forms probably expect completion elsewhere, [I]).
- 8250 `MYQ4_VERZIK_HUMAN` is quest content.

**Spectators and pets**: 8368 Abigaila (Talk-to, Scry); pets 8336 / 8337 Lil' Zik and the Metamorphosis NPC forms 10870 Lil' Maiden, 10871 Lil'
Bloat, 10872 Lil' Nylo, 10873 Lil' Sot, 10874 Lil' Xarp (ops Talk-to / Metamorphosis / Pick-up), POH forms 10761-10765 (Talk-to / Pick-up).
**Lobby**: 8326 / 8327 "Vampyre Juvenile" door guards (Talk-to); 8332-8335 Vyrelord / Vyrelady; Mysterious Stranger 8325 (multi on varbit 15607
`TOBQUEST_STRANGER_VIS`, 2 bits: `[10875 Talk-to, 10876 Talk-to + Trade, 16269, 16270, -1]`), 10877 / 11162 (no ops); orators 8323 / 8324
and citizens 8328-8331 are multis on the quest varbit 15464 `MYQ6`. Nothing here is placed in the static map except through server spawns (NPC spawns
are not in the cache).

---

## 5. Map [R from map locs and the static collision; room identification confirmed by loc symbol names]
"Free" below means flags 0 in `cache-dump collision` (static map only: server-spawned locs and NPCs are absent). Levels without map data read as
free in that tool, so for levels 2-3 only squares with locs on that level carry meaning. Tile coordinates are world coordinates of the static template.

**RuneLite region ids check out against the map squares** (id = square x << 8 | square z): Maiden **12613** = (49, 69) holds the 48 `MAIDEN` locs;
Bloat **13125** = (51, 69) holds the 311 `BLOAT` locs; Nylocas **13122** = (51, 66) holds 247 `NYLOCAS` locs; Sotetseg **13123** = (51, 67) holds 303
`SOTETSEG` locs and the maze **13379** = (52, 67) 253; Xarpus **12612** = (49, 68) holds 83 `XARPUS` locs; Verzik **12611** = (49, 67) holds 1015 `VERZIK`
locs; treasure room **12867** = (50, 67) holds 103 `TREASURE` + 502 `VERZIK` floor locs; the lobby **14642** = (57, 50) is Ver Sinhaza. RuneLite's
Discord plugin lists exactly 12611, 12612, 12613, 12867, **12869**, 13122, 13123, 13125, 13379 as the raid; 12869 = (50, 69) is the hallway east of the
Maiden (213 walkway locs only) [R]. No static data exists for 12868, 13124 or the Bloat→Nylocas hallway squares, so those links are not in the cache.
Squares (49, 66), (49, 70), (50, 66), (50, 68), (50, 70), (51, 68), (51, 70), (52, 66), (52, 68) are empty [R].

| square (id) | world box, per level | room | key locs (x, z, level) |
|---|---|---|---|
| (57, 50) (14642) | 3648..3711 × 3200..3263; locs on L0-L3 (L0 3648..3711 × 3200..3263, L1 3648..3698 × 3200..3248, L2 and L3 3648..3698 × 3200..3240) | **Ver Sinhaza lobby** | Raid entrance 32653 "Theatre of Blood" op "Enter" at (3678, 3216, L0) (7x2, rot 1: occupies x 3678..3679, z 3216..3222); Scoreboard 32987 op "Read" (3676, 3223); Notice board 32655 "Read" (3662, 3218, **L1**); gravestone Chest 32656 "Claim" (3657, 3215) and (3657, 3223); Vyre well multi 33085 at (3654, 3212), (3654, 3225), (3670, 3212), (3670, 3225); rewards chest multi 41437 at (3650, 3217); Bank booths 32666 (3649, 3207), (3651, 3207), (3652, 3207), closed booth 32667 (3650, 3207), Deposit box 32665 (3655, 3209); quest crypt staircase 42523 (3682, 3231) |
| (49, 69) (12613) | L0 3153..3199 × 4418..4465; L1 3155..3199 × 4422..4463; L2 3157..3190 × 4432..4461 | **Maiden** | Barrier 32755 "Pass" at x = 3185, z 4445..4448 (L0, rot 1; four locs); spectator barriers 32760-32762 L1 at (3174, 4430-4432) and (3190-3192, 4453) |
| (50, 69) (12869) | L0 3200..3227 × 4443..4461 (walkable 3200..3225 × 4445..4474); L1 3200..3225 × 4445..4455 | **hallway east of the Maiden** | corridor x 3186..3224, z 4445..4448 continuing into a vestibule 3213..3225 × 4449..4453 and a 2-wide corridor north at x 3215..3216 to z 4460 |
| (51, 69) (13125) | L0 3265..3325 × 4431..4464; L1 3269..3322 × 4433..4462; L2 3295..3296 × 4436..4458 | **Bloat** | Barriers 32755 "Pass" at x = 3287, z 4446..4449 (rot 3, west) and x = 3304, z 4446..4449 (rot 1, east) (L0); chain hooks 32949 on L1 at (3288, 4450), (3292, 4440), (3295, 4455), (3303, 4451), 32953 at (3289, 4454), (3302, 4454); boots hook 32970 (3297, 4440, L1); dead-merc multi 33084 (3295, 4445); spectator barriers 32760-32762 at (3280-3282, 4440) and (3307, 4433-4435) L1; supply chest (Wiki) at (3269, 4449) |
| (51, 66) (13122) | L0 3264..3327 × 4224..4286; L1 3264..3327 × 4224..4284 | **Nylocas** | Barrier 32755 "Pass" at **L1** (3295, 4255) and (3296, 4255) (the same tiles are blocked on L0); platform locs `NYLOCAS_PLATFORM_*` 3288..3303 × 4241..4256; four support pillars 3289..3300 × 4242..4253 (L1); corridor north x 3294..3297, z 4256..4283; hall exit at (3292..3306, 4272..4286) |
| (51, 67) (13123) | L0 3265..3295 × 4289..4334; L1 3264..3293 × 4288..4334; L2 3270..3288 × 4313..4314 | **Sotetseg** | Barrier 32755 "Pass" at z = 4307, x 3278..3281 (L0, rot 2); the 14 × 15 tile grid `DARKTILE` 33034 at 3273..3286 × 4310..4324; glass windows 3278..3281 × 4329..4333; spectator barriers 32760-32762 at (3273, 4300-4302) L1 |
| (52, 67) (13379) | **L3 only**: 3348..3373 × 4304..4335 | **Sotetseg maze / shadow realm** | `DARKTILE` 33034 14 × 15 grid at 3354..3367 × 4311..4325 (offset +81, +1 from the arena grid); "Portal" 33037 op "Enter" at (3357, 4330, L3) (8x2); walkable interior 3355..3366 × 4310..4329 |
| (49, 68) (12612) | L0 3159..3181 × 4376..4398; **L1 3155..3185 × 4374..4400** (arena); L2 3154..3186 × 4373..4401; L3 3149..3191 × 4368..4406 | **Xarpus** | Barriers 32755 "Pass" L1 at z = 4379 and z = 4395, x 3169..3171 (rot 2 and 0); arena door `ARENA_DOOR` 32750 at (3169, 4374, L1); **"Door" 32751 ops "Enter" and "Quick-enter" at (3169, 4400, L1)**; death-wall pieces 32752 at (3156, 4386/4388) and (3183, 4386/4388); dead-merc multi 32753 at (3180, 4390, L0) |
| (49, 67) (12611) | L0 3152..3183 × 4296..4329; L1 3153..3183 × 4297..4328 | **Verzik** | Entrance door 32701 at (3167, 4296, L0) (3x2, no ops); barrier row z = 4302 (33028 at x 3167..3169, connectors 33026 (3170, 4302), 33027 (3166, 4302)) between the justiciar hall (z 4296..4301) and the arena; Justiciar statues 33012 at (3160, 4301), (3166, 4298), (3170, 4298), (3176, 4301); throne multi 32736 at (3166, 4324, **L1**); south spectator wall 33021 along z = 4302 |
| (50, 67) (12867) | L0 3224..3250 × 4305..4334 | **Treasure room** | Stairs 32995 "Climb" (3237, 4305); Teleport crystal 32996 op "Use" (3246, 4315) (anim 8105); War table 33011 "Read" (3232, 4325) (3x5); **Strategy table 33014 "Read" (3228, 4318)** (Wiki agrees); Small chests 33016 "Check" at (3226, 4322), (3226, 4330), (3227, 4319), (3239, 4319), (3242, 4322), (3243, 4313); spectator gate 33025 (3234, 4323) |

Where the five **Monumental chests** stand is not in the static map (loc ids 32990-32994 and 33086-33090 are absent). The Wiki gives the five
anchors (3233, 4330), (3226, 4327), (3226, 4323), (3240, 4327), (3240, 4323), all level 0, all of which are free in the cache (the north chamber
3227..3243 × 4321..4332). Which anchor is `TOB_TREASUREROOM_CHEST_LOC0..4` is [?] (I would map by slot 0..4 in that order only as a guess).

**Free-tile flood fills** (flags 0, from a seed tile, 4-neighbour) [R]:

| region | seed | tiles | box (x × z) |
|---|---|---|---|
| Maiden arena, west of the barrier | (3184, 4446, L0) | 518 | 3161..3186 × 4436..4457 |
| Maiden antechamber (south corridor + east corridor + vestibule) | (3176, 4424, L0) or (3190, 4447, L0) | 182 | 3176..3224 × 4423..4459 |
| Bloat arena between the two barriers | (3288, 4447, L0) | 220 | 3288..3303 × 4440..4455 |
| Bloat outer ring and antechambers | (3270, 4449, L0) | 318 | 3270..3322 × 4433..4462 |
| Nylocas platform | (3295, 4252, L0) | 128 | 3290..3301 × 4243..4254 |
| Nylocas north corridor | (3295, 4260, L0) | 132 | 3294..3302 × 4256..4283 |
| Nylocas L1 (corridor + platform) | (3295, 4252, L1) | 362 | 3280..3311 × 4233..4283 |
| Sotetseg arena | (3279, 4318, L0) | 350 | 3273..3286 × 4306..4333 |
| Sotetseg vestibule / ring | (3279, 4300, L0) | 167 | 3275..3292 × 4294..4328 |
| Sotetseg maze | (3360, 4320, L3) | 240 | 3355..3366 × 4310..4329 |
| Xarpus arena (L1) | (3170, 4387, L1) | 175 | 3164..3176 × 4380..4394 |
| Verzik arena | (3168, 4310, L0) | 580 | 3154..3182 × 4303..4322 |
| Treasure room (corridor + hall + chamber) | (3238, 4316, L0) | 279 | 3226..3248 × 4307..4332 |
| Lobby hall and its forecourt | (3664, 3219, L0) | 456 | 3648..3682 × 3205..3233 |

**Candidate entry / key tiles, checked with `cache-dump collision` (centre flags 0x0 = free)**:

| room | tile (level) | meaning |
|---|---|---|
| Lobby | (3675, 3219, 0), (3664, 3219, 0), (3650, 3219, 0), (3676, 3222, 0) | just inside the entrance door, hall centre, in front of the rewards chest, in front of the scoreboard |
| Maiden | (3184, 4446), (3184, 4447) | arena side of the barrier |
| Maiden | (3186, 4446), (3190, 4447) | corridor side of the barrier (east) |
| Maiden | (3176, 4424) | south corridor end (a dead-end spawn candidate) |
| Maiden | (3174, 4446) | arena centre-west |
| Bloat | (3286, 4447) / (3288, 4447) | outside / inside the west barrier |
| Bloat | (3303, 4447) / (3305, 4447) | inside / outside the east barrier |
| Bloat | (3270, 4449) | in front of the supply chest |
| Nylocas | (3295, 4248, 0) | platform centre |
| Nylocas | (3295, 4260, 0) | north corridor |
| Nylocas | (3295, 4254, **1**) / (3295, 4256, **1**) | south / north of the barrier |
| Sotetseg | (3279, 4306) / (3279, 4308) | south / north of the barrier |
| Sotetseg | (3279, 4318) | arena centre |
| Sotetseg maze | (3360, 4320, **3**) / (3360, 4311, **3**) | maze centre / south row |
| Xarpus | (3170, 4378, **1**) / (3170, 4396, **1**) | south / north of the barriers |
| Xarpus | (3170, 4387, **1**) | arena centre |
| Verzik | (3168, 4304) / (3168, 4312) | arena south edge / centre |
| Treasure room | (3237, 4309) / (3238, 4316) | corridor / hall |
| Treasure room | (3227, 4324), (3241, 4328) | next to two of the Wiki chest anchors |

Not free (checked): (3650, 3208, 0) at the bank booths (0x8), (3169, 4373, 1) south of the Xarpus door (0x200000), (3168, 4300, 0) in the Verzik justiciar
hall (0x200000: the whole hall z 4296..4301 is blocked in the static map), (3235, 4326, 0) (war table, 0x20100). Whether the hall (and the antechambers that
read blocked) is reached by a plane shift or is purely decorative is [?].

Notes:
- Squares hold the template in one piece: every room is a separate zone cluster that the instance builder copies; the hallway links between rooms
  (Bloat → Nylocas → Sotetseg → Xarpus → Verzik) are **not in the cache** except the Maiden's east corridor in (50, 69). [R/I]
- The L1 locs (spectator barriers, chain hooks, Xarpus arena, Nylocas barrier, Verzik throne) mean that several rooms use level 1 for play or for
  spectating; the actual player level in each room is [?] (the Xarpus arena is the only room whose walkable flood is on L1 only).
- `Locs` for decoration are numerous (`TOB_DUNGEON_*` ids 32686-33084); the functional ones are in §2.7 / §2.8.
- The Wiki lists the sulphur-green "Barrier" ops: only 32755 carries "Pass"; 42516 (`TOB_ARENA_BARRIER_NOOP`) is the same loc without ops, 47336
  (`TOB_WALKWAY_VERZIK_BARRIER_OP`, "Resign") and the orator locs 32756 / 32757 "Vyre Orator" (Talk-to, **Resign**) are not placed statically.

---

## 6. Modes and what the cache gives per mode
- **Three modes** are first-class in the cache: separate NPC ids per room (§4), separate completion varps (1748, 3058, 3057), separate CA task sets and
  the scoreboard tabs (§2.6). `enum 3852` names them "Normal" / "Entry" / "Hard" for the party screens [R].
- Per-mode data that is in the cache: the NPC definition (HP, combat level, size, defence) of every room NPC; the Verzik start NPC's quick-start
  availability; the Sotetseg hard tiles (§2.8).
- Per-mode data that is **not** in the cache: attack timings, damage, tick cycles, scaling by team size (the Maiden / Bloat / Nylocas / Xarpus /
  Verzik HP stats are fixed at the team-of-3-or-5 value or the base value in the definition; scaling is server-side), Hard-only mechanics, drop tables.
  The Wiki (`/tmp/claude-0/ref/wiki-tob`) is the source for those and must be turned into `docs/mechanics` rules before implementation [R].

---

## 7. DB tables
- **44 (music)**: §1.6. **81 (hiscores bosses)**: dbrow 4054 "Theatre of Blood" (field 2 → varp **1748**), dbrow 4055 "Theatre of Blood: Hard Mode" (varp
  **3057**); no row for Entry Mode [R].
- **135**: `DROP_INSTANT_REVEAL_TOB` 17023 (stripped, the "instant reveal" setting for drops) [R].
- **Stripped / unreadable**: `SYNTH_TOB` 4858, `ACTION_GET_TOB_UNIQUE` 6776, `ACTION_COMPLETE_TOB` 6844 (named rows without columns we can read) [?].
- The party / scoreboard / stats of ToB are server data; there is no `Tob_Stats_*` table like TFA's tables 264 / 265 [R].

---

## 8. Blockers and open points
1. **Server-only data is not in the cache.** Attack patterns and timings of every boss, Maiden blood trails and scuttles, Bloat down/up cycle, Nylocas
   wave table, Sotetseg tile sequence, Xarpus phases, Verzik pillar and phase logic, HP scaling by team size, reward tables (unique chance by
   points, MVP), point values, damage-to-points rules and death rules. Wiki only; each needs `docs/mechanics` before code.
2. **Varbits with no client reader**: `TOB_MIDWAYCHEST_BOOBYPRIZE` 6461, `TOB_CRYSTAL_ANTISPAM` 14980, `TOB_PROGRESS` 12971, `TOB_DAMAGE_TAKEN` 11469, plus
   `TOB_SHOULD_HAVE_LOOT` 11958 and the chest varbits whose only reader is a loc multi (their meaning is inferred from names). Encodings must be chosen
   behind named config flags (`OPEN`).
3. **Semantics that are [I] or [?]**:
   - HUD orb value `P` = 30 and 31, and the unused values 28 and 29.
   - `PARTYSTATUS` 3.
   - `WAVEPROGRESS_TYPE` 1, 2, 3 (which room or mechanic uses which colour set).
   - The tenth party-details column ("runs" / "Performance counter").
   - The party-details Move up / Move down meaning (order of the loot slots / raid slot order) and which varbit orders the chests.
   - `TOBQUEST` / `TOB_PROGRESS` as raid requirements.
4. **Instance coordinate constraint**: the in-raid HUD requires `coordx >= 6400` and `PARTYSTATUS == 2` (script 1935 / 2297). The rest of the client
   state (map squares, REBUILD_REGION_V2 chunk copies of the templates in §5) is the instance builder's concern, but the x >= 6400 rule is a hard
   client contract and must be kept by the allocator [R].
5. **Send order contracts** (silently wrong otherwise): party details = 2317 × 5 (rows 0..4, empty string for vacant), then 2321 per applicant, then
   2323; party list = 2340 for **every** row 0..44 in order (the layout runs on row 44); varc 178 is reset by the onLoad scripts, so the server must
   re-open the interface for a refresh (as TFA does).
6. **Client-local state the server must mirror**: the scoreboard tab varbit 12270 (set by the client, then the IF_BUTTON reaches the server); the party
   sort order (never sent).
7. **Placement of server-spawned locs and NPCs**: midway chests 32758 / 32759, resign barrier 47336, orators 32756 / 32757, Monumental chests
   (32990-32994, 33086-33090), Sotetseg `LIGHTTILE` / `TRIGGEREDTILE` / hard tile parents, Exhumed 32743, acid pools 32744 / 41747, Verzik
   web locs 32734 / spin locs, and all NPC spawns (bosses, Abigaila, pets, Mysterious Stranger).
8. **Rooms missing in the cache**: the hallways between rooms other than the Maiden's east corridor, the arrival tiles of every room, the
   instance room order and the copy-and-rotate rules. The cache holds the nine template squares only; the player level in each room (L0 vs L1) is [?].
9. **Scoreboard / board text formats** (`m:ss.t`, MVP rules) are not in the cache. The chat-line formats that RuneLite parses ("Theatre of Blood
   total completion time", "Your completed Theatre of Blood count is: n", PB lines) are in RuneLite's `ChatCommandsPlugin` and in the Wiki; they are
   fixtures, not cache data.
