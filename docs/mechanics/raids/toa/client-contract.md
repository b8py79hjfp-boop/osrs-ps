<!-- Researched 2026-11-08 from cache 2757 (rev 242): the 10,303 client scripts dumped with the repo's own `cache-pack script` reader (ScriptScan) plus a raw re-parse for string constants and switch tables, the IF3 component files of interfaces 481, 482 and 771-778 (hook lists decoded with the RuneLite 1.13.6 InterfaceLoader layout), `cache-dump` (`config`, `map`, `dbrows`, `find-locs`) and the generated symbols. Tags: [R] read directly, [I] interpretation, [?] unknown. Script NAMES are RS Mod's `clientscript.sym` (rev 233) names, checked against the script bodies; every script id is the cache id. The helper readers (script dumper, IF3 hook dumper, enum/struct/loc/npc dumpers) were one-off scratch programs and are not in the repo. -->
# Tombs of Amascut (ToA): client contract from cache 2757 (rev 242)

Sources: generated symbols in `symbols/build/generated/symbols/tp/symbols/*.java` (they equal RuneLite's gameval names; the cache calls the raid **`TOA_*`**);
`tools/cache-dump` (`config enum|struct|inv|varbit|loc|npc`, `interface`, `map`, `find-locs`, `dbrows`); the disassembled client scripts (`cache-pack script`); RuneLite
`runelite/runelite` `cache/.../InterfaceLoader`, `Opcodes` and `ScriptEvent` (opcode names, hook layout, event placeholders); the public ToA plugins under
`/tmp/claude-0/ref/plugins-toa/` (`tombs-of-amascut`, `tombs-of-amascut-stats`, `toa-points-tracker`; facts only: which ids they read) and the OSRS Wiki pages in
`/tmp/claude-0/ref/wiki-toa` where the cache is silent (always marked as Wiki). Nothing in this file is code. Sister documents: `../cox/client-contract.md`,
`../tob/client-contract.md`, `../tfa/client-contract.md`; shared rules: `../client-contract.md`.

Each claim has a tag:
- **[R]**: read directly from cache data, script text, RuneLite source or a symbol.
- **[I]**: my interpretation of the script logic or of names, or a Wiki fact.
- **[?]**: unknown. Every open point is listed with an id `OPEN-TC<n>` in section 10.

Packed component ids are `interface << 16 | component`. Script 4-digit ids below are cache ids. A **server-run script** is one that no other script calls, that no
interface hook or `if_seton*` command names, and that therefore only the server can start (RS `runclientscript` / `RUNCLIENTSCRIPT`); its trailer
`args int/string/long` is the signature the packet must carry. `i0..` are the script's own int arguments, `s0..` its strings.

Vocabulary: the cache calls the three modes **Entry** (raid level 0..149, `*_ENTRY` completion varp 3645), **Normal** (150..299, varp 3646) and **Expert**
(>= 300, varp 3647); the interface texts say "Entry", "Normal", "Expert" ([R] 6579, 6729, 6770, 6773, dbrows 4057/4058). A ToA "raid level" is the sum of the
level values of the active **invocations** (section 4). The four paths are **Scabaras** (boss Kephri), **Het** (Akkha), **Apmeken** (Ba-Ba) and **Crondis**
(Zebak) before **the Wardens**; the HUD component names say `AKKHA`/`APMEKAN` where the text says Het/Apmeken ([R] `Toa_Hud.NEXUS_AKKHA_LEVEL`).
`ITT_*` symbols and the NPC multis on varbit 13836 belong to the quest *Beneath Cursed Sands* (varp 3419), not to the instance.

---

## 1. Symbols

### 1.1 Varps (all `clientCode=0`) [R]
| id | name | notes |
|---|---|---|
| 3585 | TOA_PERM_TRANSMIT_1 | Varbits 14318-14322, 14324 and, **overlapping, `TOB_DAMAGE_TAKEN` 11469 (bits 7..21)**: a ToB varbit is packed into a ToA varp. The party list (script 6599 / 6600) and the scoreboard (6772) redraw when it transmits |
| 3586 | TOA_PERM_TRANSMIT_2 | `TOA_DAMAGE_DONE` (16 bit) and 16 `TOA_TITLE_ACHIEVED_*` bits. No reader |
| 3587 | TOA_PERM_TRANSMIT_3 | Two more title bits and `TOA_MIDRAIDLOOT_STATS_TIMER` (bits 2..11). Read by the buff bar (5923) |
| 3588 | TOA_TEMP_TRANSMIT_1 | Party status + `TOA_CLIENT_P0..P5`. **Hooked** by the HUD (6576) and the lobby overlay (6611) |
| 3589 | TOA_TEMP_TRANSMIT_2 | `P6`, `P7`, party slot, hide-players, vault chests 0-4, kicked flag. **Hooked** by the HUD |
| 3590 | TOA_TEMP_TRANSMIT_3 | Mid-raid energy flag, `TOA_CLIENT_PRIMARY0..7`, vault chests 5-7, sarcophagus. **Hooked** by the HUD |
| 3591 | TOA_TEMP_TRANSMIT_4 | `TOA_DAMAGE_DONE_CURRENT`, `TOA_DAMAGE_TAKEN_CURRENT` (15 bit each). No reader, **not hooked** by the HUD |
| 3592 | TOA_TEMP_TRANSMIT_5 | Path levels, raid level, current path, preset selection. **Hooked** by the HUD |
| 3603 | TOA_MYCONTROLLER | No varbits. **-1 = not in a party**; anything else = in a party. Script 6598 turns the party-list button into "Make Party" / "My Party"; 6594 hooks it. Same role as `TOB_MYCONTROLLER` 1740 / `TFA_MYCONTROLLER` 5913 [R] |
| 3606 | TOA_PERSONAL_CONTRIBUTION | No varbits, no client script. The player's final contribution points: RuneLite's points tracker treats a non-zero value as final and adds 5000 (`BASE_POINTS`) [R plugin]. "as of rev 232 personal points are transmitted at the end of the raid only" (plugin comment) |
| 3635 | TOA_PICKAXE_STORAGE | `TOA_PICKAXE_CHARGES` (16 bit), `TOA_PICKAXE_STORED` (5 bit). Loc multis 45468 / 49566 (section 7) |
| 3638 | TOA_CAMEL | Camel quest bits, secret chest, found secret passage. Loc multis 46223 / 46228 / 46232 |
| 3639 | TOA_REWARDS_INT | Lore-book bits (`TOA_BOOK_*`), pet morph unlocks and `STATRENEWAL_POTION_TIMER` (bits 0..4). No reader |
| 3641 | TOA_WARDENS | `TOA_WARDENS_BUFF_TYPE` (2 bit), `TOA_WARDENS_BUFF_VALUE` (10 bit). No reader |
| 3652 | TOA_PERM_TRANSMIT_4 | `TOA_DAMAGE_TAKEN` (16 bit), `TOA_CA_QUALIFIED` (bit 16) |
| 3680-3694 | TOA_INVOCATIONS_PRESET_1A..5C | **Fifteen full 32-bit varps = five presets x three words (A, B, C).** Read by script 6839 (the Presets list, section 4.3). A = invocation word 0, B = word 1, C = word 2 |
| 3645 / 3646 / 3647 | TOTAL_COMPLETED_TOMBSOFAMASCUT_ENTRY / (normal) / _EXPERT | Completion counts. Read by 4774, 4778, 10310 (Combat Achievement KC). dbrows 4057 (3646) and 4058 (3647) in the hiscores table 81 have a "Tombs of Amascut" and an "Expert Mode" row; Entry has none |
| 3419 | (ITT, quest) | `TOA_ENTRANCE_OPEN` 13837 (bit 8) opens the pyramid entrance loc multi 44596; the other bits are the quest (not ToA) |
| 3640 / 3642 | KEPHRI_TEMP / BABA_TEMP | Boss scratch varps; no varbits, no client reader |

Unnamed varps in the block that have **no ToA name and no script/loc/npc reader** [R]: 3599-3602 (varbits 14430-14438), 3609-3632 (24 varps = 48 sixteen-bit varbits 14382-14429),
3644, 3648-3651 (varbits 14473-14511, 14788), 3695 (24 one-bit varbits 14543-14566 + a 5-bit 14567) and 3696 (14568-14573). Their use (personal bests, statistics,
unlock flags) is [?] (OPEN-TC8).

### 1.2 Varbits (base varp and bit range from `config varbit`; "readers" = client scripts, loc multis) [R]
The `readers` column was produced by scanning every script for `push_varbit` / `pop_varbit` and every loc / npc definition for `multiVarbit`.

| varbit | name | varp | bits | width / max | readers |
|---|---|---|---|---|---|
| 14318 | TOA_PARTYLIST_FILTER | 3585 | 0..0 | 1 / 1 | 6600, 6601 |
| 14319 | TOA_SHOULD_HAVE_LOOT | 3585 | 1..1 | 1 / 1 | multi loc46224 |
| 14320 | TOA_SCOREBOARD_TAB | 3585 | 2..4 | 3 / 7 | 6773, 6774 |
| 14321 | TOA_MIDRAIDLOOT_CLAIMED | 3585 | 5..5 | 1 / 1 | none |
| 14322 | TOA_MIDRAIDLOOT_ROLLED | 3585 | 6..6 | 1 / 1 | none |
| 11469 | TOB_DAMAGE_TAKEN | 3585 | 7..21 | 15 / 32767 | none |
| 14324 | TOA_TITLE_SELECTED | 3585 | 22..29 | 8 / 255 | none |
| 14325 | TOA_DAMAGE_DONE | 3586 | 0..15 | 16 / 65535 | none |
| 14326 | TOA_TITLE_ACHIEVED_SWIMMER | 3586 | 16..16 | 1 / 1 | none |
| 14327 | TOA_TITLE_ACHIEVED_CHOCOLATETEAPOT | 3586 | 17..17 | 1 / 1 | none |
| 14328 | TOA_TITLE_ACHIEVED_LEECH | 3586 | 18..18 | 1 / 1 | none |
| 14329 | TOA_TITLE_ACHIEVED_SPECIALIST | 3586 | 19..19 | 1 / 1 | none |
| 14330 | TOA_TITLE_ACHIEVED_PICKYEATER | 3586 | 20..20 | 1 / 1 | none |
| 14331 | TOA_TITLE_ACHIEVED_PEASANT | 3586 | 21..21 | 1 / 1 | none |
| 14332 | TOA_TITLE_ACHIEVED_SHOWOFF | 3586 | 22..22 | 1 / 1 | none |
| 14333 | TOA_TITLE_ACHIEVED_TANK | 3586 | 23..23 | 1 / 1 | none |
| 14334 | TOA_TITLE_ACHIEVED_BALLER | 3586 | 24..24 | 1 / 1 | none |
| 14335 | TOA_TITLE_ACHIEVED_ENTOMOPHOBE | 3586 | 25..25 | 1 / 1 | none |
| 14336 | TOA_TITLE_ACHIEVED_MOTH | 3586 | 26..26 | 1 / 1 | none |
| 14337 | TOA_TITLE_ACHIEVED_CARRY | 3586 | 27..27 | 1 / 1 | none |
| 14338 | TOA_TITLE_ACHIEVED_ANCHOR | 3586 | 28..28 | 1 / 1 | none |
| 14339 | TOA_TITLE_ACHIEVED_GLUTTON | 3586 | 29..29 | 1 / 1 | none |
| 14340 | TOA_TITLE_ACHIEVED_SORCERER | 3586 | 30..30 | 1 / 1 | none |
| 14341 | TOA_TITLE_ACHIEVED_ARCHER | 3586 | 31..31 | 1 / 1 | none |
| 14342 | TOA_TITLE_ACHIEVED_BRAWLER | 3587 | 0..0 | 1 / 1 | none |
| 14343 | TOA_TITLE_ACHIEVED_CLUTCH | 3587 | 1..1 | 1 / 1 | none |
| 14344 | TOA_MIDRAIDLOOT_STATS_TIMER | 3587 | 2..11 | 10 / 1023 | 5923 |
| 14345 | TOA_CLIENT_PARTYSTATUS | 3588 | 0..1 | 2 / 3 | 6613 |
| 14346 | TOA_CLIENT_P0 | 3588 | 2..6 | 5 / 31 | 6579 |
| 14347 | TOA_CLIENT_P1 | 3588 | 7..11 | 5 / 31 | 6579 |
| 14348 | TOA_CLIENT_P2 | 3588 | 12..16 | 5 / 31 | 6579 |
| 14349 | TOA_CLIENT_P3 | 3588 | 17..21 | 5 / 31 | 6579 |
| 14350 | TOA_CLIENT_P4 | 3588 | 22..26 | 5 / 31 | 6579 |
| 14351 | TOA_CLIENT_P5 | 3588 | 27..31 | 5 / 31 | 6579 |
| 14352 | TOA_CLIENT_P6 | 3589 | 0..4 | 5 / 31 | 6579 |
| 14353 | TOA_CLIENT_P7 | 3589 | 5..9 | 5 / 31 | 6579 |
| 14354 | TOA_CLIENT_PARTYSLOT | 3589 | 10..13 | 4 / 15 | 6583 |
| 14355 | TOA_CLIENT_HIDEPLAYERS | 3589 | 14..14 | 1 / 1 | 6579 |
| 14356 | TOA_VAULT_CHEST_0 | 3589 | 15..17 | 3 / 7 | multi loc29994 |
| 14357 | TOA_VAULT_CHEST_1 | 3589 | 18..20 | 3 / 7 | multi loc44545 |
| 14358 | TOA_VAULT_CHEST_2 | 3589 | 21..23 | 3 / 7 | multi loc44547 |
| 14359 | TOA_VAULT_CHEST_3 | 3589 | 24..26 | 3 / 7 | multi loc46215 |
| 14360 | TOA_VAULT_CHEST_4 | 3589 | 27..29 | 3 / 7 | multi loc46216 |
| 3211 | TOA_KICKED_FROM_RAID | 3589 | 30..30 | 1 / 1 | none |
| 14361 | TOA_MIDRAIDLOOT_ENERGY_ACTIVE | 3590 | 1..1 | 1 / 1 | none |
| 14362 | TOA_CLIENT_PRIMARY0 | 3590 | 2..2 | 1 / 1 | 6579 |
| 14363 | TOA_CLIENT_PRIMARY1 | 3590 | 3..3 | 1 / 1 | 6579 |
| 14364 | TOA_CLIENT_PRIMARY2 | 3590 | 4..4 | 1 / 1 | 6579 |
| 14365 | TOA_CLIENT_PRIMARY3 | 3590 | 5..5 | 1 / 1 | 6579 |
| 14366 | TOA_CLIENT_PRIMARY4 | 3590 | 6..6 | 1 / 1 | 6579 |
| 14367 | TOA_CLIENT_PRIMARY5 | 3590 | 7..7 | 1 / 1 | 6579 |
| 14368 | TOA_CLIENT_PRIMARY6 | 3590 | 8..8 | 1 / 1 | 6579 |
| 14369 | TOA_CLIENT_PRIMARY7 | 3590 | 9..9 | 1 / 1 | 6579 |
| 14370 | TOA_VAULT_CHEST_5 | 3590 | 10..12 | 3 / 7 | multi loc46217 |
| 14371 | TOA_VAULT_CHEST_6 | 3590 | 13..15 | 3 / 7 | multi loc46218 |
| 14372 | TOA_VAULT_CHEST_7 | 3590 | 16..18 | 3 / 7 | multi loc46219 |
| 14373 | TOA_VAULT_SARCOPHAGUS | 3590 | 19..21 | 3 / 7 | multi loc46220/loc46221/loc46222 |
| 14374 | TOA_DAMAGE_DONE_CURRENT | 3591 | 0..14 | 15 / 32767 | none |
| 14375 | TOA_DAMAGE_TAKEN_CURRENT | 3591 | 15..29 | 15 / 32767 | none |
| 14376 | TOA_CLIENT_CRONDIS_LEVEL | 3592 | 0..2 | 3 / 7 | 6579 |
| 14377 | TOA_CLIENT_SCABARAS_LEVEL | 3592 | 3..5 | 3 / 7 | 6579 |
| 14378 | TOA_CLIENT_HET_LEVEL | 3592 | 6..8 | 3 / 7 | 6579 |
| 14379 | TOA_CLIENT_APMEKEN_LEVEL | 3592 | 9..11 | 3 / 7 | 6579 |
| 14380 | TOA_CLIENT_RAID_LEVEL | 3592 | 12..22 | 11 / 2047 | 6579 |
| 14381 | TOA_CLIENT_CURRENT_PATH | 3592 | 23..25 | 3 / 7 | 6579 |
| 14495 | TOA_CLIENT_RAID_LEVEL_STOP_AUTO_UPDATING | 3592 | 26..26 | 1 / 1 | none |
| 14541 | TOA_PRESET_SELECTED | 3592 | 27..30 | 4 / 15 | 6839 |
| 14439 | TOA_PICKAXE_CHARGES | 3635 | 0..15 | 16 / 65535 | none |
| 14440 | TOA_PICKAXE_STORED | 3635 | 16..20 | 5 / 31 | multi loc45468/loc49566 |
| 14441 | TOA_CAMEL_QUEST_STATE | 3638 | 0..1 | 2 / 3 | none |
| 14442 | TOA_CAMEL_QUEST_KEPHRI_SOLVED | 3638 | 2..2 | 1 / 1 | none |
| 14443 | TOA_CAMEL_QUEST_BABA_SOLVED | 3638 | 3..3 | 1 / 1 | none |
| 14444 | TOA_CAMEL_QUEST_AKKHA_SOLVED | 3638 | 4..4 | 1 / 1 | none |
| 14445 | TOA_CAMEL_QUEST_ZEBAK_SOLVED | 3638 | 5..5 | 1 / 1 | none |
| 14446 | TOA_CAMEL_NAME_LEARNED | 3638 | 6..6 | 1 / 1 | multi loc46223 |
| 14447 | TOA_SECRET_CHEST_STATUS | 3638 | 7..7 | 1 / 1 | multi loc46232 |
| 14542 | TOA_FOUND_SECRET_PASSAGE | 3638 | 8..8 | 1 / 1 | multi loc46228 |
| 14448 | STATRENEWAL_POTION_TIMER | 3639 | 0..4 | 5 / 31 | 5923 |
| 14449 | TOA_BOOK_KEPHRI | 3639 | 5..5 | 1 / 1 | none |
| 14450 | TOA_BOOK_BABA | 3639 | 6..6 | 1 / 1 | none |
| 14451 | TOA_BOOK_ZEBAK | 3639 | 7..7 | 1 / 1 | none |
| 14452 | TOA_BOOK_AKKHA | 3639 | 8..8 | 1 / 1 | none |
| 14453 | TOA_BOOK_WARDENS | 3639 | 9..9 | 1 / 1 | none |
| 14454 | TOA_BOOK_ICTHLARIN | 3639 | 10..10 | 1 / 1 | none |
| 14498 | TOA_UNLOCKED_PET_MORPH_AKKHA | 3639 | 11..11 | 1 / 1 | none |
| 14499 | TOA_UNLOCKED_PET_MORPH_BABA | 3639 | 12..12 | 1 / 1 | none |
| 14500 | TOA_UNLOCKED_PET_MORPH_KEPHRI | 3639 | 13..13 | 1 / 1 | none |
| 14501 | TOA_UNLOCKED_PET_MORPH_ZEBAK | 3639 | 14..14 | 1 / 1 | none |
| 14502 | TOA_UNLOCKED_PET_MORPH_DESTROYED_TUMEKEN | 3639 | 15..15 | 1 / 1 | none |
| 14503 | TOA_UNLOCKED_PET_MORPH_DESTROYED_ELIDINIS | 3639 | 16..16 | 1 / 1 | none |
| 14455 | TOA_WARDENS_BUFF_TYPE | 3641 | 0..1 | 2 / 3 | none |
| 14456 | TOA_WARDENS_BUFF_VALUE | 3641 | 2..11 | 10 / 1023 | none |
| 14323 | TOA_DAMAGE_TAKEN | 3652 | 0..15 | 16 / 65535 | none |
| 14671 | TOA_CA_QUALIFIED | 3652 | 16..16 | 1 / 1 | none |
| 13837 | TOA_ENTRANCE_OPEN | 3419 | 8..8 | 1 / 1 | multi loc 44596 (pyramid entrance, section 7) |
| 14292 / 14293 / 14294 / 14296 | BUFF_TOA_MIDRAIDLOOT_HEALING / PRAYER / ENERGY / STATS _DURATION_DISABLED | (options varp) | | 1 each | 6569-6572 (buff-bar options toggles), 4684, 6716 |
| 14463 / 14464 | ITT_USED_LAMP / ITT_MET_OSMUMTEN | 3419 | 15 / 16..17 | | NPC multi 11829 (the vault Osmumten, section 8) |
| 14475 | COLLECTION_RAIDS_TOA_COMPLETED | | | 1 | collection log (4103) |
| 14498-14503 | TOA_UNLOCKED_PET_MORPH_* | 3639 | 11..16 | 1 each | none (pet metamorph unlocks) |
| 14613-14671 | CA_TASK_TOA_* , TOA_CA_QUALIFIED | | | | Combat Achievements (generic) |

**Important encoding facts, all [R]:**
- `TOA_CLIENT_P0..P7` are **5 bits**; `TOA_CLIENT_PRIMARY0..7` one bit each; `TOA_CLIENT_*_LEVEL` three bits each (0..7); `TOA_CLIENT_RAID_LEVEL` **11 bits** (0..2047).
- **There is no varbit or varp for the active invocations.** The invocation bitfields travel as three ints in the arguments of the server-run script 6729 (section 2.4); the only
  invocation varps are the preset words 3680-3694. The symbols `TOA_CLIENT_*` the task brief expected for invocations do not exist in the cache.
- Writing a varp overwrites its neighbours: all varbits above are packed, so the server needs read-modify-write on 3585-3592 (3585 additionally carries the ToB counter).
- The raid timer is **not** a varbit (section 2.1); personal points are varp 3606, written once at the end.

### 1.3 Varcs [R]
| id | name | use |
|---|---|---|
| 178 | RAIDS_COUNT | Row counter shared with CoX/ToB. Reset to 0 by the onLoad scripts 6594 (party list) and 6615 (party details); incremented by 6601 / 6722 / 6727 |
| 1087 | RAIDS_COUNT_2 | Number of non-empty member rows added by 6722 ("Members (n)" tab label) |
| 59 | (generic) | Set to `clientclock` by 6594: base of the party age column (script 1574) |
| 180 / 181 | RAIDS_LISTSORT_LIST / _DETAILS | Client-local sort state of the party list / party details (reset to 1 or 0 when out of range) |
| 1081 | TOA_CLIENT_NEXTREFRESH | HUD redraw throttle: `clientclock + 30` set by 6583; 6585 zeroes it to force a redraw |
| 1082 / 1083 / 1084 | TOA_PARTYLIST_SCROLLPOS / TOA_INVOCATIONS_SCROLLPOS / TOA_SUMMARY_SCROLLPOS | client-local scroll positions |
| 1085 | TOA_RAID_SUMMARY_TOOLTIP | client-local (mobile tooltip toggle, 6593) |
| 1086 | TOA_INVOCATIONS_LAYOUT_REWARDS_HIDDEN | client-local layout state of the invocations tab (0/1/2), toggled by 5895 / 6732 |
| 1088-1098 | BUFF_STATRENEWAL_START, BUFF_MIDRAIDLOOT_STATS_START, BUFF_APMEKENS_SIGHT_END, BUFF_AKKHA_BURN / DARKNESS / FREEZE _END, BUFF_ZEBAK_BLEED_END, BUFF_TOA_MIDRAIDLOOT_HEALING / PRAYER / ENERGY / STATS _END | Buff-bar timers written by the server-run script 5927 (section 2.8) |
| 1099-1106 | TOA_CLIENT_NAME0..7 (strings) | The eight HUD player names. Written by the server-run script 6585 (8 strings); read by 6579 and by the RuneLite plugin (`getVarcStrValue(1099 + i)`) |

### 1.4 Invs [R]
| id | name | size | use |
|---|---|---|---|
| 807 / 808 / 809 | TOA_MIDRAIDLOOT_BUNDLE1 / 2 / 3 | 7 each | The three supply bundles drawn by script 6778 in interface 777 ("Life", "Chaos", "Power") |
| 810 | TOA_MIDRAIDLOOT_BAG | 28 | The "Raid Supplies" bag drawn by 6784 into 778:5; **inv-transmit hooked** on 810 |
| 811 | TOA_CHESTS | 9 | The vault chest reward inventory (interface 771). No client script draws it (OPEN-TC4) |

### 1.5 Interfaces and components [R] (names from `Components.java`; hooks decoded from the IF3 files)
- **481 TOA_HUD** (56 components): 0 UNIVERSE (onLoad **6576**, 32 component arguments), 1 ATMOSPHERIC (not used by any ToA script), 2 DODGER, 3 CONTENT, 4 CONTENT_NAMES, 5 CONTENT_DETAILS, 6 `_0_CONTAINER`, 7-8
  sprites, 9 `_0_FILLER`, then `_k_CONTAINER` at **6 + 4k** and `_k_FILLER` at **9 + 4k** for k = 0..7 (6, 10, 14, 18, 22, 26, 30, 34 / 9, 13, 17, 21, 25, 29, 33, 37), 38 TOOLTIP, 39 STATUS,
  40 RAID_STATE, 41 RAID_DIFFICULTY_GFX (sprite 8799 / 8800 / 8801), 42 RAID_LEVEL_TXT (text `Level: n`), 43 RAID_PATH_LEVELS_PATH (hidden), 44 PATHS_ICON, 45 PATHS_LEVEL, **46 RAID_TIMER** (text `00:00`),
  47 RAID_PATH_LEVELS_NEXUS, 48-55: icon + text pairs (texts **49 NEXUS_SCABARAS_LEVEL, 51 NEXUS_AKKHA_LEVEL (Het), 53 NEXUS_APMEKAN_LEVEL, 55 NEXUS_CRONDIS_LEVEL**).
- **482 TOA_RAID_SUMMARY** ("Raid Summary", 77 components): 0 UNIVERSE (onLoad **6586** `[482:37, 8, 24, 57, 65]`), 1 FRAME (onLoad 227 title; onClick 40), 3 TOOLTIP, 4 PLAYER_STATS_CONTAINER, 5-6 title,
  7 ROOM_TIMES_CONTAINER, **8 BOSS_TIMES_TABLE** (visible by default: 9 WARDENS_TIME, 10 WARDENS_TITLE, 11 BABA_TIME, 12 BABA_TITLE, 13 ZEBAK_TITLE, 14 ZEBAK_TIME, 15 AKKHA_TITLE, 16 AKKHA_TIME, 17 KEPHRI_TITLE, 18 KEPHRI_TIME),
  **24 PATH_TIMES_TABLE** (hidden by default: 25 CRONDIS_TIME, 26 CRONDIS_TITLE, 27 APMEKEN_TITLE, 28 APMEKEN_TIME, 29 HET_TITLE, 30 HET_TIME, 31 SCABARAS_TITLE, 72 SCABARAS_TIME), 34 ROOM_TIMES_TABS
  (**35 TAB_PATHS, 36 TAB_BOSSES**, action "View"), 37 PLAYER_STATS_TABLE, 38 LAYER_1 (header: 39 DEATHS_GRAPHIC, 40 DAMAGE_TAKEN_GRAPHIC, 41 DAMAGE_DEALT_GRAPHIC, 42 TITLE_TITLE, 43 NAME_TITLE), 44 STATS_BACKGROUND_CONTAINER,
  45 MVP_CONTAINER, **46 TITLES_COLUMN, 47 DEATHS_COLUMN, 48 DAMAGE_TAKEN_COLUMN, 49 DAMAGE_DEALT_COLUMN, 50 NAME_COLUMN**, 56 INVOCATIONS_CONTAINER (57 INVOCATIONS_TABLE: 58 RAID_LEVEL, 59 ACTIVE, 60 RAID_LEVEL_TITLE "Raid Level", 61 ACTIVE_TITLE "Active"),
  62 INVOCATIONS_TITLE, 64 TOTAL_TIMES_CONTAINER (65 TOTAL_TIMES_TABLE: **66 CHALLENGE_TOTAL, 67 OVERALL_TOTAL**, 68 OVERALL_TITLE "Overall", 69 CHALLENGE_TITLE "Challenge"), 70 TOTAL_TIMES_TITLE.
- **771 TOA_CHESTS** ("Tombs of Amascut", 11): 0 UNIVERSE, 1 FRAME (onLoad 227), 2 CONTENT, 3 model slot, **4 BANK** (action 6 "Bank-all"), 5 BANK_ICON, **6 INVENTORY** (action 1 "Take-all"), 7 INVENTORY_ICON, **8 DISCARD** (action 6 "Discard-all"),
  9 DISCARD_ICON, **10 ITEMS** (92x119 layer). The three buttons carry onOp **489** (sound only) and hover 4158: the same trio as ToB's interface 23 and CoX's 539. Only the verbs are fixed here; the server handles the packets.
- **772 TOA_PARTYLIST** ("Tombs of Amascut Party List", 67 components): 0 UNIVERSE (onLoad **6594** `[772:16, 772:62]`), 1 CONTENTS (host of the pause buttons), 2 FRAME (227), 3 MAIN, 4 LISTLAYER, 6 SORTAREA,
  sort headers **8 SORT_MEMBERS, 9 SORT_NAME, 10 SORT_COMPLETIONS, 11 SORT_INVOCATIONS, 12 SORT_RAIDLEVEL, 13 SORT_RAIDDIFFICULTY, 14 SORT_AGE**, 15 LISTAREA, **16 LIST**, **17-61 rows `_00`..`_44` (enum 4793: row index -> component)**,
  62 SCROLLBAR, **63 REFRESH, 64 MYPARTY, 65 FILTER** (enum 4794: component -> pause-button sub-id), 66 TOOLTIP.
- **773 TOA_LOBBY** (6): 0 UNIVERSE (onLoad **6611** `[773:1, 773:2, 773:4]`), 1 DODGER, 2 CONTENT (onLoad 991), 3 CONTENT_BACKGROUND, **4 NAMES_HEADER** (text), 5 NAMES.
- **774 TOA_PARTYDETAILS** (101 components, party details + invocations): 0 UNIVERSE (onLoad **6615**, 30 component arguments), 1 PAUSEBUTTONS, 2 CONTENTS, 3 FRAME, 4 BACK, 5 TABS, 6 MEMBERS_TAB, 7 APPLICANTS_TAB, 8 INVOCATIONS_TAB, 9 SUMMARY_TAB,
  16 MAIN, 17 MEMBERS (19 MEMBERS_HEADER with 21 NAME, 22 COMLEVEL, 23-29 ATTACK/STRENGTH/RANGED/MAGIC/DEFENCE/HITPOINTS/PRAYER, 30 COMPLETIONS), 31 MEMBERS_LIST_CONTAINER, **32 MEMBERS_LIST**, 33 APPLICANTS (35 APPLICANTS_HEADER, 37-46 columns),
  47 APPLICANTS_LIST_CONTAINER, **48 APPLICANTS_LIST**, **49 INVOCATIONS** (50 COM, 51 INVOCATIONS_SCROLLBAR, 52 INVOCATIONS_CONTENT), 53 SUMMARY (54, 55 SUMMARY_SCROLLBAR, 56 SUMMARY_CONTENT), 57 INVOCATIONS_SIDEPANEL
  (58 header, 59, 60 BOTTOM), 61 POPUP, **62 REFRESH, 63 ACTION, 64 UNBLOCK**, 65-67, 68 TOOLTIP, **69 INVOCATIONS_CLEAR, 70 INVOCATIONS_OPEN_REWARDS, 71 INVOCATIONS_PRESETS, 72 COMPLETIONS**, 73 RAID_LEVEL (text `Raid Level: n (m)`),
  74 RAID_MODE (text `Mode: ...`), 75 INVOCATIONS_LOOT_CONTENT, 76 INVOCATIONS_PRESETS_CONTENT, 77 PRESETS_INNER, 78-81, **82 PRESETS_BUTTON, 83 BTN_PRESETS_LOAD, 84 BTN_PRESETS_SAVE**, 87 RAID_LOOT_POTENTIAL,
  93 RAID_DIFFICULTY_GFX, 94 RAID_DIFFIUCULTY_INFO, 95 RAID_LEVEL_BAR, 96 RAID_LEVEL_INFO, 97 PRESETS_BACKGROUND, 98 PRESETS_BUTTON_CLICK, 99 PRESETS_NAME, 100 PRESETS_BUTTON_CLICK_PAUSE.
- **775 TOA_SCOREBOARD** ("Tombs of Amascut Statistics", 88): 0 UNIVERSE (onLoad **6771** `[775:0, 9, 10, 11, 85, 86, 87]`), 2 FRAME (227), 3 TABS, 8 SCROLLBAR host, 9-10, 11 DETAILS, tabs **85 STORY_TAB (Entry), 86 NORMAL_TAB, 87 HARD_TAB (Expert)** (action "View"),
  data cells 47-84 (section 2.7).
- **776 TOA_INVOCATIONS** ("Tombs of Amascut Invocations", 6): 0 UNIVERSE (onLoad **6609** `[776:4, 776:5]`), 2 FRAME (227), 3 CONTENT (scroll host, 714), 4 SCROLLBAR, 5 INVOCATIONS (the list host).
- **777 TOA_MIDRAID_LOOT** ("Supply Bundles", 13): 0 UNIVERSE (onLoad **6777** `[777:4, 7, 10, 6, 9, 12]`), 2 FRAME (227), **4 BUNDLE_1 (+ 5 title "Life"), 7 BUNDLE_2 ("Chaos"), 10 BUNDLE_3 ("Power")**, **6 SELECT_BUTTON_1, 9 SELECT_BUTTON_2, 12 SELECT_BUTTON_3** (action "Choose", click mask 0x2).
- **778 TOA_MIDRAIDLOOT_BAG** ("Raid Supplies", 6): 0 UNIVERSE (onLoad **6782** with one string argument = the title), 1 TITLE, **2 close button** (action "Close", onOp 29), 3 FRAME (onLoad 1191), **5 ITEMS** (inv 810).
- Packed ids used by the scripts: 481:46 = 31522862, 772:1 = 50593793, 772:16 = 50593808, 774:1 = 50724865, 774:98 = 50724962.
- **Missing interfaces** [R]: no ToA stats-board 'lobby' overlay beyond 773, no spectator UI, no separate raid-end loot interface (the vault chests are locs plus interface 771), and no interface for title selection
  (`TOA_TITLE_SELECTED` has no reader, OPEN-TC7).

---

## 2. Scripts, varbit encodings and what the server must send

### 2.1 HUD 481 (onLoad 6576): party orbs, names, levels, path, timer [R]
- **6576** registers the overlay placement (script 4729 with the layout constant 65256), builds the eight orb sub-components (6577 per slot), draws once (6579) and installs three hooks on 481:0:
  **onTimer -> 6578(0)**, **onVarTransmit varps 3588, 3589, 3590, 3592 -> 6578(1)** and **onStatTransmit stat 3 (Hitpoints) -> 6578(1)**. **6578** redraws when called by a var/stat event, or on the timer
  once `clientclock >= varc 1081` (so at most every 30 client cycles = 0.6 s). Varp **3591 does not redraw the HUD**, and neither does varc 1099-1106 by itself: 6585 zeroes varc 1081 for that.
- **6579 (draw)**, for member slot `k` = 0..7 calls 6583(k, `P_k`, `PRIMARY_k`, name varc `1099 + k`, container 481:(6+4k), filler 481:(9+4k), tooltip 481:38). **6583** [R]:
  - `P == 0`: slot hidden (and its tooltip cleared). The slot counts as visible otherwise; the container is sized by the number of visible slots: width 69, height `35 * rows - 1` with `rows = max(1, ceil(n / 2))` (two orbs per row).
  - Name text: the **first letter** of the name (`"-"` when the name is empty or the string `"null"`), or **"Me"** when `k == TOA_CLIENT_PARTYSLOT - 1` (so `PARTYSLOT` is the viewer's own slot, **1-based**; 0 = viewer not in a slot).
    Mouse-over (desktop) shows the full name through the tooltip script 526.
  - `P == 31`: orb sprite 3575 (`ORB_FILLER_16`), colour white, size 26x26 (full), no HP refresh (OPEN-TC2).
  - `P == 30`: name colour red 0xFF0000, filler height 0 (an empty orb). The RuneLite plugin reads "P - 1 over 26, values above 1.0 mean dead" so **30 = dead** [R plugin + script].
  - `P` 1..29: sprite 3576 (`ORB_FILLER_17`) when `PRIMARY_k == 1`, else 3574 (`ORB_FILLER_15`) (OPEN-TC1); filler height `min(26, P - 1)` px. So **P = 1 + round(26 * hp / maxhp)** (RuneLite: `max(P-1,0)/26`), at most 27 in use [I].
    For the viewer's own slot the HP fill ignores `P` and uses the local stat 3 (script 6584: `stat/stat_base * 26`, 1 px minimum while alive), so the server must still send `P != 0` for the own slot.
  - `TOA_CLIENT_HIDEPLAYERS` = 1 hides the whole slot list and sets its height to 0; 0 sizes it by the number of non-zero `P`.
- **Names**: the server-run **6585(s0..s7)** (8 strings, no ints) writes varcs 1099..1106 (`s0` -> `NAME0`) and zeroes varc 1081. Send it before/with the varps that make a slot visible. RuneLite's chat-commands style team counters use
  `P1..P7 != 0` ("player count = 1 + non-zero P1..P7"), so keep `P` non-zero for every member for the whole run [R plugin].
- **Level / path block** (6579 lines 131-307): raid level text `Level: <TOA_CLIENT_RAID_LEVEL>` (481:42) and the difficulty sprite on 481:41: **>= 300 -> 8801, >= 150 -> 8800, else 8799**
  (`TOA_DIFFICULTY_ICONS_2/1/0`). `TOA_CLIENT_CURRENT_PATH` (3 bit) picks the panel by a jump table (`switch` in 6579):

  | CURRENT_PATH | panel |
  |---|---|
  | 0 | **Nexus view**: the four-level block 481:47 is shown and the single-path block 481:43 hidden; 481:49 <- `SCABARAS_LEVEL`, 481:51 <- `HET_LEVEL`, 481:53 <- `APMEKEN_LEVEL`, 481:55 <- `CRONDIS_LEVEL`; height 95 |
  | 1 | single-path block with icon 8774 (`TOA_INVOCATION_ICONS_SMALL_4`) and text `SCABARAS_LEVEL`; height 70 |
  | 2 | icon 8775, text `HET_LEVEL` |
  | 3 | icon 8776, text `APMEKEN_LEVEL` |
  | 4 | icon 8777, text `CRONDIS_LEVEL` |
  | 5, 6 | both level blocks hidden, height 43 (the Wardens room; 5 vs 6 is not distinguished, OPEN-TC3) |
  | 7 | the switch has no case: both blocks keep their previous visibility, height 70 |

  The RuneLite `PathLevelTracker` parses these very widget texts (481:49/51/53/55 and 481:45) [R plugin], so keep the `TOA_CLIENT_*_LEVEL` varbits correct.
- **Raid timer 481:46**: **server-run 6580(int ticks, int frozen)** [R]. `ticks == -1` clears the timer (text "" and removes the timer hook). Otherwise it stores `ticks * 30 - clientclock` and either
  (`frozen == 1`) writes the time once (`m:ss` / `h:mm:ss`, or centiseconds when `OPTION_PRECISE_TIMING` 11866 / `OPTION_SEPARATE_HOURS` 11890 say so) and removes the hook, or (`frozen == 0`) installs a **client timer
  script 6581** on 481:46 that re-formats `clientclock + stored` (so the client counts by itself, 30 client cycles = one tick). Scripts 4159 / 4160 do the formatting (seconds / tenths).
  **There is no raid-timer varbit**; the server calls 6580 to start, resync and freeze (final time). Which tick value the Wiki timer uses ("starts on entering the first path, ends when the Warden reaches 0 hp") is a server rule, not cache data.
- **Server order**: open 481 once, `6585`, then varps 3588 / 3589 / 3590 / 3592 (each send triggers 6578), then `6580`.

### 2.2 Lobby overlay 773 (onLoad 6611) [R]
- 6611 places the overlay (4729, constant 52968) and hooks **onVarTransmit varp 3588 -> 6612 -> 6613**. **6613** switches on `TOA_CLIENT_PARTYSTATUS` (14345, 2 bits) and writes 773:4:
  **0 -> "No Party"** (colour 0xFF981F), **2 -> "Step Inside Now!"** (flashing via the shared name-flasher 2303 / 6614, one call per client cycle), **any other value (1, 3) -> "Party"**.
  Unlike ToB there is **no coordinate test** in the script: the server opens 773 only while the player is in the lobby (region 13454) and closes it on entry. The status is shared with the in-raid HUD's varp 3588.

### 2.3 Party list 772 (onLoad 6594), server-run 6601 [R]
- **6594** hides every row (enum 4793, 45 rows), writes "Loading...", sets `varc 178 = 0`, `varc 59 = clientclock`, builds the three pause buttons on **772:1 CONTENTS** from **enum 4794** (772:63 REFRESH = sub **0**, 772:64 MYPARTY = sub **1**, 772:65 FILTER = sub **2**; labels "Refresh", "Make Party"/"My Party", "Friends/Clan Only") and hooks
  **onVarTransmit 3603 -> 6597 (button label)** and **3585 -> 6599 (filter button look)**. The filter button is highlighted when `TOA_PARTYLIST_FILTER` (14318) is 1.
- **Server-run 6601(int row, string line)** [R, `args 1/1/0`]: call it **for every row 0..44 in order** (45 = size of enum 4793), the empty string for vacant rows. The layout, sort and scrollbar are finalised only
  when the call with `row >= 44` arrives. It returns silently unless the component `MAINMODAL` (10551312 in the stretch layout; remapped per client mode through `toplevel_getcomponents` 900 and an enum lookup, as for CoX / ToB) has an open sub-interface, so **open 772 first**. The row string has **14 `|`-separated fields**:

  | # | field | where it goes |
  |---|---|---|
  | 1 | leader name (tags kept) | column "Name" (772:9); the op base text uses the tag-stripped name; the Friends/Clan filter compares it with the local player |
  | 2-8 | up to seven further member names | only used by the Friends/Clan filter (script 5217: self, friend or clan mate); not displayed |
  | 9 | member count | "Members" column as `n/8` (772:8) |
  | 10 | completions requirement (`"0"` shows a grey `-`) | "Completions" column (772:10) |
  | 11 | invocation figure [I: number of active invocations] | "Invocations" column (772:11) |
  | 12 | raid level | "Raid Level" column (772:12) |
  | 13 | difficulty text (`Entry` / `Normal` / `Expert` [I]) | "Raid Difficulty" column (772:13) |
  | 14 | age in **ticks** as a decimal string | "Age" column (script 1574 shows `m:ss` ageing from `varc 59`) |

  The numeric columns are zero-padded to 3 digits by 1503 only for the client-side sort keys. With `TOA_PARTYLIST_FILTER` = 0 every row shows; with 1 only rows whose leader or members pass script 5217.
- **Click path** [R]: a row has the op **"View Party:"** (op 1, `<col=ff9040>leader</col>`); the click runs 6604, which resumes a hidden pause-button child created at the last row **on 772:16 (LIST) with sub-id = row index 0..44**.
  The three buttons resume children of **772:1** with sub 0 / 1 / 2. Sorting is client-local (varc 180, scripts 6606-6608) and sends nothing.
- Row-count emptiness: if no row had content (`varc 178 == 0` after the last call) the list shows "No parties are currently listed." (filter off) or "You have no friends or clanmates in a party." (filter on) and un-hides the three buttons.

### 2.4 Party details 774 (onLoad 6615) and the invocations tab [R]
This one interface serves the lobby party board (Members / Applicants), the Invocations editor and the Summary. Open it in `MAINMODAL`; every row script above returns silently if the slot is empty.

**Build sequence (all server-run):**
1. Open 774. Its onLoad **6615** zeroes `varc 178` and `1087`, writes "Loading..." into 774:16, hides the sub-panels, creates the hidden pause-button children of **774:1 PAUSEBUTTONS** (sub-ids below) and calls 6616 (size).
2. **6722(int role, string row)** once per member, **exactly 8 calls (rows 0..7, `""` for vacant rows)** [R: `6729` computes the applicant count as `varc 178 - 8`]. Row string: **10 `|` fields**
   `name|combat|attack|strength|ranged|magic|defence|hitpoints|prayer|completions` (empty field shows a grey `-`). Row 0 also sets the frame title: `Party of <name>` when the name is non-empty, else `Party Details`
   (the plugin `tombs-of-amascut-stats` matches `startsWith("Party of ")`). The viewer's own row is white, others 0xFF981F (case-insensitive compare with the local name). `role == 2` (leader) adds, for every non-empty row, the op 10
   **"Kick"** (row 0, the leader's own row, reads **"Quit Party"** instead) and the **"Move up" / "Move down"** arrow children; for any other role the rows carry no op at all (Wiki: the leader can promote members in the lobby, so Move up on row 1 plausibly promotes, OPEN-TC13).
3. **6727(string row)** once per applicant (same 10 fields); the applicant row index is `max(0, varc 178 - 8)`, at most 8 [R: accept/reject sub-ids cover rows 0..7].
4. **6729(int role, int minCompletions, int activeInvocations, int raidLevel, int tab, int w0, int w1, int w2)** [R, `args 8/0/0`], **last**:
   - `role`: **2 leader, 1 member, 3 applicant, 4 cannot apply (greyed "Apply"), 0/other can apply** (6730).
   - `raidLevel` -> text `Raid Level: <raidLevel> <col=ffffff>(<activeInvocations>)</col>` (774:73) and `Mode: Entry/Normal/Expert` (774:74) by `< 150 / 150..299 / >= 300`; side panel 6770 (bar, difficulty sprite, "Rewards Info" panel).
   - `tab`: **0 Members, 1 Applicants, 2 Invocations, 3 Summary** (the tab labels carry `(n)` counts from `varc 1087` and `varc 178 - 8`).
   - `w0, w1, w2`: the three **invocation words** (section 2.5).
   - `minCompletions` (774:72): shown as `Completions: <n>` when > 0, else `Set Completions` (leader, `role == 2`) or `Completions: -`.
   It also draws the Summary tab (6756 -> 6757, derived **client-side** from the words and the level: "Raid Modifiers", "Stat Modifiers", "Combat Restrictions", "Room Modifiers" blocks) and the Presets list (6839).
   A bare re-run of 6729 redraws the tabs and the invocation tiles without re-sending the rows (it only reads `varc 178 / 1087`) [I]; the other raids re-open the interface instead.
5. Optionally **6745(int index, string text)**: relabels one of the 12 enum-4792 buttons (script 890 on the button from enum 4792[index]); the pattern of ToB's 2329 [I].

**Pause-button children of 774:1 (RESUME_PAUSEBUTTON component = 774:1 = 50724865, sub-id as below)** [R: enum 4792, 5345, 6725, 6746, 6754]:

| sub | meaning |
|---|---|
| 0 | BACK (774:4) |
| 1 | REFRESH (774:62). The client also sends it by itself every **500 client cycles (10 s)** while tab 0 or 1 is shown (script 6748 -> 6785); treat it as an ordinary refresh request |
| 2 | UNBLOCK (774:64, leader, yellow) |
| 3 | COMPLETIONS (774:72); the leader's "Set" op shows `...` and resumes at once; script 6744 restores the old text after 80 cycles (1.6 s), so **re-send 6729 within that time** |
| 4 | ACTION (774:63): "Disband" (leader), "Leave" (member), "Withdraw" (applicant), "Apply" |
| 5 | INVOCATIONS_CLEAR (774:69, "Clear All", leader, tab 2) |
| 6 / 7 | BTN_PRESETS_LOAD / BTN_PRESETS_SAVE (774:83 / 84, "Load" / "Save") |
| 8 / 9 / 10 / 11 | MEMBERS_TAB / APPLICANTS_TAB / INVOCATIONS_TAB / SUMMARY_TAB (774:6-9, op "View") |
| 12 + row (row 0..7) | "Kick" (op 10) on member row `row`; row 0 reads "Quit Party" when it is the viewer's own |
| 20 + row | "Move up" on member row `row` |
| 28 + row | "Move down" on member row `row` |
| 36 + row | applicant row `row`: "Accept" (op 1) |
| 44 + row | applicant row `row`: "Reject" (op 10) |
| **52 + k (k = 0..45)** | **invocation tile `k`**: leader click on the tile (op 1 "Activate" / "Deactivate"); `k` is the enum-4664 key, not the invocation id |

Sub-ids 0..97 exist (12 enum buttons + 24 kick/up/down + 8 accept + 8 reject + 46 invocation tiles = 98); 6615 creates 12..97 hidden and the row scripts enable them, so a sub-id above 97 never arrives. Move up/down are plain pause buttons as well, **not** IF_BUTTON1 (unlike ToB).
Sorting (varc 181, scripts 6759-6764) is client-local. The "Set" op on COMPLETIONS needs a number dialog opened by the server; its bounds are not in the cache (OPEN-TC12).

**Other client -> server traffic on 774:**
| kind | component | meaning |
|---|---|---|
| IF_BUTTON1 / 2 | 774:98 (PRESETS_BUTTON_CLICK) child **0..4** | preset slot 1..5: op 1 **"Select"**, op 2 **"Clear"** (6839; the client only plays a sound, 6843). The server sets `TOA_PRESET_SELECTED` (14541) = slot + 1 (the highlight tests `14541 == index + 1`) and, for Clear, empties the 3 varps of the slot |
| (client-local) | 774:70 "Rewards", 774:71 "Presets" | toggle the loot-info / presets side panel via `varc 1086` (scripts 6731 / 350 / 5895 / 6732): **no packet that the server must act on** |
| (client-local) | tile hover | descriptions come from enum 4664 structs, section 4.1 |

### 2.5 Invocations: the bit layout [R]
**Key facts:** there are **46 invocations** (enum 4664 keys 0..45), each a struct. The struct param **1159 is the invocation id, 1-based** (`id = key + 1`). Active state is a bit in three 31-bit words
(scripts 6755 and 6842 are the same function):

```
id 1..30   -> word 0, bit id          (bit 0 unused)
id 31..61  -> word 1, bit id - 31
id 62..    -> word 2, bit id - 62     (no invocation uses it yet; reserved)
raid level = sum of struct param 1162 over the set bits
```

So word 0 = ids 1..30 at bits 1..30; word 1 = ids 31..46 at bits 0..15. The three words are exactly what is stored per preset in varps 3680 + 3p + {0,1,2} (p = 0..4) and what the server passes to 6729 as `w0, w1, w2`. The sum of every level value is 1170; the cache itself sets Expert from 300.
Prerequisite and exclusivity rules (struct params 1346 "requires", 1863 "minimum raid level") are **not enforced by the client**; the server must (OPEN-TC11).


### 2.6 Raid summary 482 (onLoad 6586) [R]
- **6586 -> 6587** draws the two tab buttons (`thinbox_tab_draw`, 482:35 "Paths", 482:36 "Bosses") and hooks **onOp 6588** on both (`6588(0)` on 482:36 shows the **boss** table, `6588(1)` on 482:35 shows the **path** table);
  the default is the boss table (6589(0)). The "View" ops are client-local; the server does not receive anything it must act on.
- **Server-run 6590(int dealt, int taken, int deaths, int titleStruct, int unused, int row, int rowCount, int mvp, string name)** [R, `args 8/1/0`] builds **one player row** (call it once per participant, `row` = 0..n-1 in table order,
  `rowCount` = n so the row height is `176 / n`):
  - `name` -> 482:50 NAME_COLUMN; `dealt` -> 482:49 DAMAGE_DEALT_COLUMN; `taken` -> 482:48 DAMAGE_TAKEN_COLUMN; `deaths` -> 482:47 DEATHS_COLUMN; a value of **-1 prints a grey "-"**; damage shows the number below 65535 and `Lots!` above;
    deaths show the number below 12 and `Lots!` otherwise.
  - `titleStruct` -> 482:46 TITLES_COLUMN as `'<name>'` where `<name>` is **struct param 1525**, and the hover text is struct param **1526** (the title description). **It is a struct id, not a title id**: map title id -> struct with enum **4668** (section 4.4); **-1 prints "-"**.
  - `mvp == 1` adds the MVP sprite 3104 beside the name (482:45 MVP_CONTAINER). The fifth int is unused.
  - On mobile the columns also install tooltips with script 6593; on desktop 6591 / 6592.
- **Everything else on 482 is plain text** that no script writes (like ToB's interface 363 / 459): the boss times (482:9, 11, 14, 16, 18), path times (482:25, 28, 30, 72), the Raid Level / Active values of the invocations table (482:58, 482:59) and the totals (482:66 CHALLENGE_TOTAL,
  482:67 OVERALL_TOTAL) are filled by `IF_SETTEXT` (OPEN-TC6). RuneLite's `RaidCompletionTracker` and the stats plugin parse chat, not this interface (section 9).

### 2.7 Scoreboard 775 (onLoad 6771) [R]
- **6771** hooks **onVarTransmit varp 3585 -> 6772** (tab look) and **onOp 6774** on the three tab buttons, then draws once (6773). **6774** does `set_varbit 14320` **locally** and redraws:
  **775:86 NORMAL_TAB -> 0, 775:85 STORY_TAB ("Entry") -> 1, 775:87 HARD_TAB ("Expert") -> 2** (6773 switch on `TOA_SCOREBOARD_TAB`: 1 Entry, 2 Expert, else Normal). The same IF_BUTTON1 reaches the server, which must set 14320 as well
  (keeping varp 3585 in sync, it also carries the ToB counter) and **re-send the data cells for that mode**.
- **Data cells** are plain text (no script writes them): `DATA_A_P` 47 / `DATA_A_G` 48 attempts (personal / global), `DATA_C_P` 49 / `DATA_C_G` 50 completions, `DATA_D_P` 51 / `DATA_D_G` 52 deaths, and for team sizes **SOLO, 2MAN .. 8MAN** the four best-time cells
  `_P_O`, `_P_R`, `_G_O`, `_G_R` (Personal / Global x Overall / Room): SOLO 53-56, 2MAN 57-60, 3MAN 61-64, 4MAN 65-68, 5MAN 69-72, 6MAN 73-76, 7MAN 77-80, 8MAN 81-84 [R names]. The time format is not in the cache (OPEN-TC6).

### 2.8 Mid-raid supplies: 777 (bundles), 778 (bag), buff bar [R]
- **777 (onLoad 6777 `[777:4, 7, 10, 6, 9, 12]`)**: draws inv **807 / 808 / 809** (7 slots each, 36x32 icons on a 44 px pitch from x = 5, y = 40, quantity from `inv_total`) with the op "Examine" (client-local, 487), and three "Choose" buttons. There is **no inv-transmit hook**: fill the invs
  **before** opening the interface. Client -> server: **IF_BUTTON1 on 777:6 / 777:9 / 777:12 = bundle 1 / 2 / 3** (RuneLite `BundleType` LIFE / CHAOS / POWER, plugin `nexus/BundleType`). Titles "Life", "Chaos", "Power" are static text.
- **778 (onLoad 6782(string title))**: the title string is the **onLoad hook argument** (`'Raid Supplies'`, static). 6784 draws **inv 810** as a 4-wide grid (36 px + 8 px gaps, 28 slots) into 778:5; **inv-transmit hooked on 810** (6783). Item ops: **1 "Withdraw-1", 2 "Withdraw-5", 3 "Withdraw-All", 4 "Drop", 9 "Examine"**
  (the dynamic children of 778:5, `child index = slot`, so IF_BUTTON 1..4 with component 778:5, child = slot, item). The close button 778:2 ("Close", onOp 29 = close self) sends IF_BUTTON1.
- **Mid-raid buff-bar entries** (server-run **5927(int buffId, int value)**, `args 2/0/0`): the generic buff setter; ToA's ids are `4368` Apmeken's Sight (varc 1090), `4369` Akkha burn (1091), `4370` Akkha darkness (1092), `4371` Akkha freeze (1093), `4372` Zebak bleed (1094), `4373` mid-raid healing (1095),
  `4374` prayer (1096), `4375` energy (1097), `4376` stats (1098) [R: switch keys in 5927; the keys are buff ids, not gamevals]. For all of these the **value is an end time in game ticks on the `MAP_CLOCK` clock**: the bar shows `value - varp 3079 (MAP_CLOCK)` remaining ticks and nothing when `value <= 0` (scripts 5923, 4678).
  The **stats** buff alone is read from the varbit `TOA_MIDRAIDLOOT_STATS_TIMER` (14344, 10 bits; the bar lasts `14344 * 25` units from the client-set `varc 1089`, script 5942; OPEN-TC9). `TOA_MIDRAIDLOOT_ENERGY_ACTIVE` (14361) has no client reader, but RuneLite reads it:
  a rise means "liquid adrenaline consumed"; likewise a rise of 14344 means "smelling salts consumed" [R plugin]. The options toggles 14292-14296 (`*_DURATION_DISABLED`) are client option bits (6569-6572).

### 2.9 Vault chests: interface 771 and the loc multis [R]
- Interface **771** needs inv **811** (9 slots) filled before opening. No client script draws it into 771:10 (OPEN-TC4); the three verbs "Take-all" (771:6, action 1), "Bank-all" (771:4, action 6), "Discard-all" (771:8, action 6) are plain IF_BUTTON on those components, as in ToB.
- The eight vault chests are **locs with a per-player multi** (section 7). `TOA_VAULT_CHEST_0..7` are 3-bit values in varps 3589 (chests 0-4) and 3590 (chests 5-7): value **2 -> "Chest" with op "Open" (mine)**, **4 -> an "emptied" chest variant (op "Search")**, every other value (0, 1, 3, 5, 6, 7) -> the not-mine chest (op "Open" as well, so the **server must reject opening somebody else's chest**).
  The Wiki (Chest (Tombs of Amascut)): "a key-shaped icon indicates which chest the player can open", pins at (3674|3687, 5139|5142|5145|5148).

### 2.10 Server-run script signature summary (checked with `cache-pack script`, cache 2757)
`6580 ii` (ticks, frozen), `6585 ssssssss`, `6590 iiiiiiii s`, `6601 i s`, `6722 i s`, `6727 s`, `6729 iiiiiiii`, `6745 i s`, `5927 ii`. All match the trailers (`args int/string/long`). The remaining ToA scripts are hook targets or helpers (sections 2.1-2.8 name their callers).

---

## 3. Interfaces: static hooks, server-run scripts, client traffic

| interface | static hooks | server-invoked scripts (no client caller) |
|---|---|---|
| 481 TOA_HUD | 481:0 onLoad **6576**; onTimer 6578(0); onVarTransmit 3588 / 3589 / 3590 / 3592 and onStatTransmit stat 3 -> 6578(1) (installed by 6576) | **6585**(8 s) names, **6580**(2 i) raid timer |
| 482 TOA_RAID_SUMMARY | 482:0 onLoad **6586**; 482:1 onLoad 227 ("Raid Summary"), onClick 40; tabs onOp 6588 | **6590**(8 i, s) one player row; the rest by IF_SETTEXT |
| 771 TOA_CHESTS | 771:1 onLoad 227 ("Tombs of Amascut"); buttons onOp 489 / hover 4158 | none (inv 811) |
| 772 TOA_PARTYLIST | 772:0 onLoad **6594**; 772:2 onLoad 227; rows onOp 6604 (set by 6603) | **6601**(i, s) x 45 |
| 773 TOA_LOBBY | 773:0 onLoad **6611**; 773:2 onLoad 991; onVarTransmit 3588 -> 6612 | none |
| 774 TOA_PARTYDETAILS | 774:0 onLoad **6615**; several 714 scroll hooks; hooks installed by 6729 / 6748 (timer) | **6722**(i, s) x 8, **6727**(s) per applicant, **6729**(8 i), **6745**(i, s) |
| 775 TOA_SCOREBOARD | 775:0 onLoad **6771**; 775:2 onLoad 227; onVarTransmit 3585 -> 6772; tabs onOp 6774 | none (cells by IF_SETTEXT) |
| 776 TOA_INVOCATIONS | 776:0 onLoad **6609**; 776:2 onLoad 227; 776:3 onLoad 714 | none |
| 777 TOA_MIDRAID_LOOT | 777:0 onLoad **6777**; 777:2 onLoad 227 | none |
| 778 TOA_MIDRAIDLOOT_BAG | 778:0 onLoad **6782**; 778:3 onLoad 1191; 778:2 onOp 29; onInvTransmit 810 -> 6783 | none |
| (buff bar) | | **5927**(buffId, value) |

Client -> server traffic [R unless marked]:

| kind | component | args | source |
|---|---|---|---|
| RESUME_PAUSEBUTTON | 772:1 | sub 0 refresh, 1 my party, 2 filter | enum 4794, 6594 |
| RESUME_PAUSEBUTTON | 772:16 | sub = row 0..44 ("View Party:") | 6604 |
| RESUME_PAUSEBUTTON | 774:1 | sub 0-97 (section 2.4) | enum 4792, 5345, 6725, 6746, 6754 |
| IF_BUTTON 1 / 2 | 774:98 child 0..4 | preset Select / Clear | 6839 |
| IF_BUTTON1 | 775:85 / 775:86 / 775:87 | scoreboard tab Entry / Normal / Expert | 6771 |
| IF_BUTTON1 | 777:6 / 777:9 / 777:12 | choose bundle 1 / 2 / 3 | IF3 actions |
| IF_BUTTON 1-4 | 778:5 child slot | Withdraw-1 / -5 / -All / Drop | 6784 |
| IF_BUTTON1 | 778:2 | Close | IF3 action |
| IF_BUTTON 6 / 1 / 6 | 771:4 / 771:6 / 771:8 | Bank-all / Take-all / Discard-all | IF3 actions |
| IF_BUTTON1 (ignorable) | 482:35 / 482:36 | summary tab switches (client-local) | 6587 |
| OPLOC | the lobby / vault / nexus locs of section 7 | | cache loc ops |

There are **no `IF_SCRIPT_TRIGGER`** hooks in any ToA script (no `crc` arguments to supply, like CoX / ToB, unlike TFA) [R]. The pause-button resume packets of the numeric "Set" op need a number dialog opened by the server.
**Send-order contracts** (silently wrong otherwise): party list = 6601 for **every** row 0..44 in order; party details = 6722 x 8, then 6727 per applicant, then 6729 last; `varc 178` is reset only by the onLoad scripts, so the server re-opens the interface to refresh (or re-runs 6729 alone, section 2.4);
supplies / chest invs before opening; the HUD names (6585) with the varps that reveal the slots.

---

## 4. Cache data tables: invocations, presets, titles, rewards info

### 4.1 Invocations: enum 4664, structs, params [R]
**All 46 invocations, their names, descriptions, levels and icons are in the cache**: enum **4664** (`in=int, out=struct`, keys 0..45) maps the list position to a struct. Struct params (read by 6610, 6750-6752, 6755, 6757, 6842):

| param | meaning |
|---|---|
| 1159 | invocation id, 1-based (the bit position source, section 2.5) |
| 1160 | name ("Try Again", ...) |
| 1161 | category: 3 attempts, 4 time limit, 5 helpful-spirit supplies, 6 initial path level-ups, 7 prayer, 8 combat restriction, 9 mid-raid path level-ups, 10 Akkha, 11 Kephri, 12 Zebak, 13 Ba-Ba, 14 Wardens, 15 Blazing Tombs (category 15 is skipped by 6609 and 6749 unless `league_world` (script 3160: bits of `MAP_FLAGS_CACHED` 3717) is true; the second condition, script 8214, is a stub that returns 0 in this cache, so **ids 45 and 46 are never offered on a normal world**) |
| 1162 | raid-level value (the `+n` the description text prints, "Increases Raid Level by n") |
| 1262 | description text (full sentence, e.g. "Limits the overall number of attempts that can be made within the raid to 10. ...") |
| 1297 / 1298 | icon sprite (active / inactive "black" variant), `TOA_INVOCATION_ICONS_n` / `_BLACK_n` (8722-8769) |
| 1299 | numeric value used by the summary text (6757): attempts allowed (10, 5, 3, 1), time limit in **ticks** (4000 = 40 min, 3500, 3000, 2500), supplies percentage (66, 33, 10), initial path level-ups (1, 2, 3), prayer reduction percent (10) |
| 1346 | prerequisite invocation struct (informational, enforced by the server) |
| 1521 | marker `1` on invocations that others depend on (Not Just a Head, Overclocked, Overclocked 2) [I] |
| 1863 | minimum raid level (300 for Blazing Tombs I / II) |

Table (key = enum 4664 key; struct = enum value; word / bit = section 2.5):

| key | struct | id | word / bit | name | category | level | value (1299) | extra |
|---|---|---|---|---|---|---|---|---|
| 0 | 417 | 1 | w0 bit 1 | Try Again | 3 (attempts) | 5 | 10 |  |
| 1 | 418 | 2 | w0 bit 2 | Persistence | 3 (attempts) | 10 | 5 |  |
| 2 | 419 | 3 | w0 bit 3 | Softcore Run | 3 (attempts) | 15 | 3 |  |
| 3 | 420 | 4 | w0 bit 4 | Hardcore Run | 3 (attempts) | 25 | 1 |  |
| 4 | 421 | 5 | w0 bit 5 | Walk for It | 4 (time limit) | 10 | 4000 |  |
| 5 | 422 | 6 | w0 bit 6 | Jog for It | 4 (time limit) | 15 | 3500 |  |
| 6 | 423 | 7 | w0 bit 7 | Run for It | 4 (time limit) | 20 | 3000 |  |
| 7 | 424 | 8 | w0 bit 8 | Sprint for It | 4 (time limit) | 25 | 2500 |  |
| 8 | 425 | 9 | w0 bit 9 | Need Some Help? | 5 (helpful spirit) | 15 | 66 |  |
| 9 | 426 | 10 | w0 bit 10 | Need Less Help? | 5 (helpful spirit) | 25 | 33 |  |
| 10 | 427 | 11 | w0 bit 11 | No Help Needed | 5 (helpful spirit) | 40 | 10 |  |
| 11 | 428 | 12 | w0 bit 12 | Walk the Path | 9 (mid-raid path levels) | 50 |  |  |
| 12 | 429 | 13 | w0 bit 13 | Pathseeker | 6 (initial path levels) | 15 | 1 |  |
| 13 | 430 | 14 | w0 bit 14 | Pathfinder | 6 (initial path levels) | 40 | 2 |  |
| 14 | 431 | 15 | w0 bit 15 | Pathmaster | 6 (initial path levels) | 50 | 3 |  |
| 15 | 432 | 16 | w0 bit 16 | Quiet Prayers | 7 (prayer) | 20 | 10 |  |
| 16 | 433 | 17 | w0 bit 17 | Deadly Prayers | 7 (prayer) | 20 |  |  |
| 17 | 434 | 18 | w0 bit 18 | On a Diet | 8 (combat restriction) | 15 |  |  |
| 18 | 435 | 19 | w0 bit 19 | Dehydration | 8 (combat restriction) | 30 |  |  |
| 19 | 436 | 20 | w0 bit 20 | Overly Draining | 8 (combat restriction) | 15 |  |  |
| 20 | 437 | 21 | w0 bit 21 | Lively Larvae | 11 (Kephri) | 5 |  |  |
| 21 | 438 | 22 | w0 bit 22 | More Overlords | 11 (Kephri) | 15 |  |  |
| 22 | 439 | 23 | w0 bit 23 | Blowing Mud | 11 (Kephri) | 10 |  |  |
| 23 | 440 | 24 | w0 bit 24 | Medic! | 11 (Kephri) | 15 |  |  |
| 24 | 441 | 25 | w0 bit 25 | Aerial Assault | 11 (Kephri) | 10 |  |  |
| 25 | 442 | 26 | w0 bit 26 | Not Just a Head | 12 (Zebak) | 15 |  | flag 1521=1 |
| 26 | 444 | 27 | w0 bit 27 | Arterial Spray | 12 (Zebak) | 10 |  | needs struct 442 |
| 27 | 445 | 28 | w0 bit 28 | Blood Thinners | 12 (Zebak) | 5 |  | needs struct 442 |
| 28 | 447 | 29 | w0 bit 29 | Upset Stomach | 12 (Zebak) | 15 |  |  |
| 29 | 448 | 30 | w0 bit 30 | Double Trouble | 10 (Akkha) | 20 |  |  |
| 30 | 449 | 31 | w1 bit 0 | Keep Back | 10 (Akkha) | 10 |  |  |
| 31 | 542 | 32 | w1 bit 1 | Stay Vigilant | 10 (Akkha) | 15 |  |  |
| 32 | 543 | 33 | w1 bit 2 | Feeling Special? | 10 (Akkha) | 20 |  |  |
| 33 | 603 | 34 | w1 bit 3 | Mind the Gap! | 13 (Ba-Ba) | 10 |  |  |
| 34 | 752 | 35 | w1 bit 4 | Gotta Have Faith | 13 (Ba-Ba) | 10 |  |  |
| 35 | 949 | 36 | w1 bit 5 | Jungle Japes | 13 (Ba-Ba) | 5 |  |  |
| 36 | 1275 | 37 | w1 bit 6 | Shaking Things Up | 13 (Ba-Ba) | 10 |  |  |
| 37 | 1276 | 38 | w1 bit 7 | Boulderdash | 13 (Ba-Ba) | 10 |  |  |
| 38 | 1278 | 39 | w1 bit 8 | Ancient Haste | 14 (Wardens) | 10 |  |  |
| 39 | 1688 | 40 | w1 bit 9 | Acceleration | 14 (Wardens) | 10 |  |  |
| 40 | 2874 | 41 | w1 bit 10 | Penetration | 14 (Wardens) | 10 |  |  |
| 41 | 2933 | 42 | w1 bit 11 | Overclocked | 14 (Wardens) | 10 |  | flag 1521=1 |
| 42 | 2934 | 43 | w1 bit 12 | Overclocked 2 | 14 (Wardens) | 10 |  | needs struct 2933; flag 1521=1 |
| 43 | 2971 | 44 | w1 bit 13 | Insanity | 14 (Wardens) | 50 |  | needs struct 2934 |
| 44 | 5892 | 45 | w1 bit 14 | Blazing Tombs I | 15 (Blazing Tombs) | 200 |  | min level 300 |
| 45 | 5893 | 46 | w1 bit 15 | Blazing Tombs II | 15 (Blazing Tombs) | 200 |  | min level 300 |

The summary text blocks (script 6757) are computed on the client from the active words: attempts / time limit / supplies / initial levels from params 1161 + 1299, enemy hitpoints / defence / accuracy / damage from the raid level through script 6758 (the thresholds of
that function are the client's own display; the actual combat scaling is server data, see `../../../mechanics` for the raid-level rule), "Prayer Effectiveness: 100 - sum of the 1299 of category 7".

### 4.2 Raid level and the three modes [R]
- Raid level = sum of param 1162 over the active invocations (script 6842), shown as `Raid Level: n (k)` with k = active count (774:73) and `Level: n` (481:42). **Entry < 150 <= Normal < 300 <= Expert** (HUD 6579, 6729, 6770); the difficulty sprite is 8799 / 8800 / 8801.
- The 11-bit client varbit `TOA_CLIENT_RAID_LEVEL` (14380) is what the HUD and the RuneLite plugins read; `TOA_CLIENT_RAID_LEVEL_STOP_AUTO_UPDATING` (14495) has no reader (OPEN-TC8). The side panel 6770 also draws the
  "Rewards Info" bar with marks at 150 and 300 on a scale of 600 (1000 in `league_world`) and an enum-driven loot list (section 4.5).
- Per-mode data that is not in the cache: boss stats and scaling by raid level, path levels' effects, attack timers, reward tables (Wiki only). Each needs its own `docs/mechanics` rule.

### 4.3 Presets [R]
Five presets, three varps each (3680 + 3p + {0,1,2}, p = 0..4). Script 6839 draws the list in 774 (scroll host 774:77 INVOCATIONS_PRESETS_INNER): for each slot it recomputes the level with 6842 and prints `Empty` (level 0), `Entry - n`, `Normal - n` or `Expert - n`,
highlighting the slot whose index + 1 equals `TOA_PRESET_SELECTED` (14541, 4 bits; 0 = none). Save / Load are pause buttons 7 / 6 on 774:1; the server saves the current words into the slot selected by 14541 and loads them back, then re-sends 6729 and the varps.
Selecting and clearing are IF_BUTTON 1 / 2 on 774:98 child 0..4 (section 2.4).

### 4.4 Titles [R]
The raid summary shows a title per player from **struct params 1525 (name), 1526 (description), 1527 (icon sprite), 1528 / 1529 (two numbers)**. Enum **4668** maps title id (1..17) to the struct; the ids with a struct are:

| title id | struct | name | description | sprite | 1528 / 1529 |
|---|---|---|---|---|---|
| 1 | 391 | The Leech | Had the lowest contribution in at least five encounters. | 3074 | 600 / 600 |
| 2 | 392 | The Swimmer | Spent at least 30 seconds in Zebak's pool. | 3103 | 500 / 500 |
| 3 | 393 | The Specialist | Used the most special attack energy. | 3070 | 100 / 100 |
| 4 | 394 | The Picky Eater | Used the least food. | 3070 | 100 / 100 |
| 5 | 395 | The Peasant | Finished the raid with the lowest value loadout. | 3070 | 100 / 1 |
| 6 | 396 | The Showoff | Finished the raid with the highest value loadout. | 3070 | 100 / 1 |
| 7 | 397 | The Tank | Took the most damage. | 3070 | 100 / 1 |
| 8 | 398 | The Baller | Dropped more than 20 orbs while fighting Akkha. | 3070 | 400 / 400 |
| 9 | 401 | The Entomophobe | Killed the most scarabs. | 3070 | 100 / 100 |
| 10 | 402 | The Moth | Died to a light beam in the Path of Het. | 3070 | 700 / 700 |
| 11 | 403 | The Carry | Had the highest overall damage. | 3070 | 100 / 1 |
| 12 | 406 | The Anchor | Died the most. | 3070 | 100 / 1 |
| 13 | 407 | The Glutton | Ate the most food. | 3070 | 100 / 100 |
| 14 | 408 | The Sorcerer | Dealt the most magic damage. | 3070 | 100 / 100 |
| 15 | 409 | The Archer | Dealt the most ranged damage. | 3070 | 100 / 100 |
| 16 | 411 | The Brawler | Dealt the most melee damage. | 3070 | 100 / 100 |
| 17 | 412 | The Clutch | Finished off the Wardens on their own. | 3070 | 500 / 1 |

The achieved bits `TOA_TITLE_ACHIEVED_*` (varps 3586 / 3587) are 18 flags named SWIMMER, CHOCOLATETEAPOT, LEECH, SPECIALIST, PICKYEATER, PEASANT, SHOWOFF, TANK, BALLER, ENTOMOPHOBE, MOTH, CARRY, ANCHOR, GLUTTON, SORCERER, ARCHER, BRAWLER, CLUTCH
in bit order 16..31, 0..1: that is **not** the order of enum 4668, and "Chocolate Teapot" has **no title struct** (a 18th title with no data in the cache, OPEN-TC7). `TOA_TITLE_SELECTED` (14324, 8 bits) has no reader. Wiki (the same page the descriptions mirror): "The Baller: dropped more than 20 orbs during the Akkha fight".

### 4.5 Rewards-info enums (client-drawn) [R]
Script 6770 draws the "Rewards Info" panel from enums: **4683** (16 reward objs: 27279 Thread of Elidinis, 27283, 27285, 27289, 30893 Jewel of Amascut, 25975 Lightbearer, 26219 Osmumten's Fang, 25985 Elidinis' Ward, 27277 Tumeken's Shadow, 27226 / 27229 / 27232 Masori set,
27352, 27248, 27255, 27372), **4677** (obj -> minimum raid level: 25975 / 26219 -> 50; 27226 / 27229 / 27232 / 25985 / 27277 / 27352 -> 150; 27372 -> 350; 27255 -> 425; 27248 -> 500) and **4678** (obj -> flag 1: 27248 / 27255 / 27372). The server sends nothing for it.
The percentages in the panel's children (RuneLite reads text containing "%") are client-computed.

### 4.6 DB tables, music and jingles [R]
- **44 (music)**, columns: 0 sort name, 1 name, 2 hint "in the Tombs of Amascut.", 3 midi id, 4 gameval-17 id, 5 unlock position `(group, bit)`:

  | dbrow | track | midi | gv17 | unlock | jingle |
  |---|---|---|---|---|---|
  | 3139 | Beneath Cursed Sands (`MUSIC_TOA_AMBIENCE`) | 518 | 730 | (22, 29) | `TOA_AMBIENCE` 546 |
  | 3145 | Into the Tombs (`MUSIC_TOA_LOBBY`) | 719 | 734 | (22, 28) | `TOA_LOBBY` 555 |
  | 3150 | Laid to Rest (`MUSIC_TOA_VICTORY`) | 563 | 731 | (22, 30) | `TOA_VICTORY` 545 |
  | 3148 | Test of Strength (Het, `MUSIC_TOA_PATH_HET`) | 720 | 732 | (23, 0) | `TOA_PATH_HET` 547 |
  | 3146 | Test of Companionship (Apmeken) | 509 | 741 | (23, 1) | `TOA_PATH_APMEKEN` 552 |
  | 3147 | Test of Resourcefulness (Crondis) | 439 | 733 | (23, 2) | `TOA_PATH_CRONDIS` 550 |
  | 3149 | Test of Isolation (Scabaras) | 432 | 738 | (22, 31) | `TOA_PATH_SCABARAS` 554 |
  | 3142 | A Mother's Curse (Kephri) | 622 | 737 | (23, 3) | `TOA_BOSS_KEPHRI` 553 |
  | 3140 | Sands of Time (Akkha) | 770 | 739 | (23, 4) | `TOA_BOSS_AKKHA` 548 |
  | 3141 | Ape-ex Predator (Ba-Ba) | 656 | 740 | (23, 5) | `TOA_BOSS_BABA` 549 |
  | 3144 | Jaws of Gluttony (Zebak) | 566 | 736 | (23, 6) | `TOA_BOSS_ZEBAK` 556 |
  | 3143 | Amascut's Promise (Wardens) | 938 | 735 | (23, 7) | `TOA_BOSS_WARDENS` 551 |

  Also `TOA_PATH_COMPLETE_JINGLE` 1111 and `TOA_BOSS_COMPLETE_JINGLE` 1110. Which track plays where is server logic. dbrows `ACTION_GET_TOA_UNIQUE` 6775 and `ACTION_COMPLETE_TOA` 6845 are named rows with no readable columns here; **`DROP_INSTANT_REVEAL_TOA` 17024 (table 135) is stripped** (empty row), like ToB's.
- **81 (hiscores bosses)**: dbrow **4057** "Tombs of Amascut" (field 1 = 4297, field 2 -> varp **3646**) and **4058** "Tombs of Amascut: Expert Mode" (4298, varp **3647**); no Entry row.
- There is no ToA stats / party table like TFA's tables 264 / 265: the party list, board and scoreboard are server data.

---

## 5. Modes, levels and what is server-only
- The client knows **only**: the raid level (varbit 14380 and the words of 6729), the three mode names and the 150 / 300 thresholds, the per-path levels (three bits each), the current path, the party orbs (P / PRIMARY / names / slot), the invocation text. It computes the raid-level sum itself for the details screen, so the **server's raid level and the words it sends must agree**.
- Server-only data (not in the cache): boss and minion stats and their scaling by raid level, path level and party size, attack patterns and timings of every room, the puzzles' logic, point values and the contribution formula behind varp 3606, the reward tables and unique-chance caps, death and "attempts" handling, the invocation rules (exclusions, prerequisites), the title assignment rules (only the descriptions are in the cache), the room-complete and Wardens phase messages. Each needs a `docs/mechanics/raids/toa/<topic>.md` rule with a source before code.
- Party size: 1-8 players (Wiki Tombs_of_Amascut: "1-8 players"); the HUD, party list ("n/8") and `P0..P7` agree.

---

## 6. Map [R from `cache-dump map`, `find-locs` and the loc/NPC definition dumps]

### 6.1 Which squares exist
RuneLite's room identification uses the **template region id** of the player's instance position (`WorldPoint.fromLocalInstance(...).getRegionID()`), exactly like CoX/TFA, and "in raid" = widget 481:40 exists and is visible (plugin `RaidStateTracker`).
**Therefore the server must build every ToA room as a copy of the square below, at the same plane layout, and must open interface 481 for the whole run**, or the room plugins, timers and loot trackers do not engage. Region id = `(squareX << 8) | squareZ`; all 13 squares are in the cache map [R].

| region | square | base (x, z) | role | locs | loc extent (x, z), planes with locs |
|---|---|---|---|---|---|
| **13354** | (52, 42) | 3328, 2688 | **Jaldraocht pyramid entrance (surface, not instanced)** | 1806 | 3328..3391 x 2688..2751, planes 0-3 |
| **13454** | (52, 142) | 3328, 9088 | **Lobby** (the pyramid interior); `REGION_LOBBY` in the plugins | 319 | 3344..3380 x 9097..9136, planes 0-1 |
| **14160** | (55, 80) | 3520, 5120 | **Nexus** (`RaidRoom.NEXUS`) | 4056 | 3520..3583 x 5120..5183, planes 0-1 |
| 14162 | (55, 82) | 3520, 5248 | Path of Scabaras puzzle | 2723 | 3520..3583 x 5257..5302, planes 0-2 |
| 14164 | (55, 84) | 3520, 5376 | Kephri boss | 3071 | 3527..3573 x 5386..5430, planes 0-2 |
| **14672** | (57, 80) | 3648, 5120 | **Tomb / vault (Osmumten's burial chamber, chests, sarcophagus)** (`RaidRoom.TOMB`) | 8421 | 3648..3710 x 5121..5182, planes 0-2 |
| 14674 | (57, 82) | 3648, 5248 | Path of Het puzzle | 1509 | 3657..3701 x 5257..5303, planes 0-2 |
| 14676 | (57, 84) | 3648, 5376 | Akkha boss | 4123 | 3648..3711 x 5376..5439, planes 0-2 |
| 15184 | (59, 80) | 3776, 5120 | **Wardens, obelisk phases (P1 / P2)** | 9240 | 3777..3839 x 5121..5183, planes 0-3 |
| 15186 | (59, 82) | 3776, 5248 | Path of Apmeken puzzle | 1037 | 3786..3834 x 5264..5299, planes 0-1 |
| 15188 | (59, 84) | 3776, 5376 | Ba-Ba boss | 3165 | 3778..3837 x 5387..5430, planes 0-2 |
| 15696 | (61, 80) | 3904, 5120 | **Wardens, final phase (P3)** | 1375 | 3905..3966 x 5121..5183, planes 0-2 |
| 15698 | (61, 82) | 3904, 5248 | Path of Crondis puzzle | 2972 | 3912..3960 x 5248..5311, planes 0-2 |
| 15700 | (61, 84) | 3904, 5376 | Zebak boss | 1490 | 3904..3963 x 5381..5438, planes 0-2 |

Each is a 64 x 64 tile square. Two neighbouring squares carry spill-over only: (60, 84) = 15444 (131 `TOA_*` locs at x 3901-3903, z 5399-5417, planes 0-2, the east edge of the Crondis / Zebak band) and (54, 82) = 13906 (156 `GREYBOX` locs at x 3516-3519, z 5261-5299, the west edge of Scabaras). (57, 87), (58, 87), (59, 87) are Motherlode mine squares and (60..62, 78) are farm / ditch decoration: **not ToA** [R]. No hallway squares exist between rooms: transitions are teleports / instance moves ("Quick-Enter", teleport crystals) [I].
Room ids match RuneLite `RaidRoom` (NEXUS 14160, CRONDIS 15698, ZEBAK 15700, SCABARAS 14162, KEPHRI 14164, APMEKEN 15186, BABA 15188, HET 14674, AKKHA 14676, WARDENS 15184 + 15696, TOMB 14672) and the stats plugin's `*_PUZZLE_ROOM_REGION_ID` / `*_ROOM_REGION_ID` constants [R plugins].

### 6.2 Key locs by square (x, z, plane, rotation) [R]
| square | locs |
|---|---|
| 13354 pyramid | entrance multi **44596** at **(3357, 2711, L1)**; the steps (`TOA_ENTRANCE_STEPS01-45`, `_SIDE01-20`) run from (3350, 2721) down to (3357, 2711); `TOA_ENTRANCE_LVL0_*` / `LVL1_*` / `LVL2_*` are the pyramid blocks |
| 13454 lobby | **Grouping Obelisk 46068** "Inspect" (3358, 9119, L0, r2); Scoreboard 46071 "Read" (3354, 9114, r1); **Invocation Board 46073** "Read" (3362, 9114, r1); Sack 46077 "Search" (3353, 9117); Gravestone chest 46078 "Claim" (3366, 9115); Cape chest 46080 "Search" (3367, 9121); lamp crate multi 46225 (3363, 9125); **lobby entry 46089 "Enter"** (5x1, (3357, 9112, r2)); **lobby exit 46087 "Leave"** (3x2, (3358, 9129, r2)); bank camel multi **46223** (3352, 9119, r3); **rewards niche multi 46224** (3370, 9114, r1); pickaxe cavity multi **49566** (3367, 9114, r1) |
| 14160 nexus | exit 45128 "Leave" (3549, 5163); path doors: **Scabaras 46155** (3559, 5155, r0), **Apmeken 46158** (3562, 5146, r1), **Crondis 46161** (3541, 5155, r3), **Het 46164** (3539, 5146, r3) (ops "Enter", "Quick-Enter"; the `_UNSELECTED` variants 46156 / 46159 / 46162 / 46165 swap in for paths not chosen); Wardens door **46168** "Entry" (7x3, ops Enter / Quick-Enter; placed by the server) |
| 14162 Scabaras | exit 45128 (3521, 5278); barriers 45135 "Barrier" (3532, 5274), (3532, 5286); continue **45337** "Entry" (3582, 5279); spawn holes 45343 "Passage" (3548, 5277), (3548, 5283); stepping stone 45396 (3560, 5280) |
| 14164 Kephri | exit **45144** (3533, 5407, r2) |
| 14672 tomb | **vault chests 0-7**: multis 29994 (3686, 5145, r1), 44545 (3673, 5145, r3), 44547 (3686, 5142, r1), 46215 (3673, 5142, r3), 46216 (3686, 5148, r1), 46217 (3673, 5148, r3), 46218 (3686, 5139, r1), 46219 (3673, 5139, r3) (chest `k` of 0..7 = `TOA_VAULT_CHEST_LOC k`); **sarcophagus multi 46220** (3679, 5140, r2) with barrier multi 46221 x10 around it ((3678..3682, 5140..5141)) and glow multi 46222 x6 ((3679..3681, 5140..5141)); scoreboard **44942** "Read" (3692, 5153); bags 44984 "Search" (3671, 5157); strange wall multi 46228 (3658, 5153); strange chest multi 46232 (3654, 5154, r1) |
| 14674 Het | exit 45128 (3699, 5278, r1); continue **45131** "Entry" (3659, 5278, r2); path barriers 45135 x6 (x 3669 and 3691, z 5279-5281); pickaxe statue multis 45468 x5 ((3662, 5275), (3662, 5283), (3679, 5267), (3679, 5291), (3695, 5284)) |
| 14676 Akkha | exit **46055** (3699, 5406, **L1**, r0) |
| 15184 Wardens P1/P2 | exit **45129** (`TOA_DOOR_EXIT_WARDENS`) (3806, 5178, **L1**); barrier 45136, crystals 45138 are server-placed |
| 15186 Apmeken | exit 45128 (3791, 5278, r3); barriers 45135 x6 (x 3796 and 3820); roof supports 45494 "Repair" x4 ((3803|3811, 5269|5289)); hammers 45497 "Take" x3; potions 45498 "Take" x3; continue **45500** "Entry" (3825, 5279) |
| 15188 Ba-Ba | exit **45844** (3788, 5407, r2) |
| 15698 Crondis | barriers 45135 x6 (x 3922 and 3950, z 5279-5281); continue **45397** "Entry" (3916, 5279, r2); waterfalls 45398 "Fill" x4 ((3926|3940, 5250|5306)); exit **45453** (3955, 5278, r1) |
| 15700 Zebak | rock steps 45509 "Climb-up" (3933, 5397), (3933, 5419); exit **45543** (3961, 5407, **L1**) |

Spawned by the server (not in the static map) [R]: all boss and puzzle NPCs, the path-complete teleport crystals 45137 / 45138, Wardens barriers / platforms (`TOA_WARDENS_*` locs), Het's mirrors 45455 / 45457, Scabaras' tile puzzles (`TOA_SCABARAS_*`).

---

## 7. Locs [R unless marked]

### 7.1 Entrances, doors, exits
| loc | id / symbol | name, size | ops | notes |
|---|---|---|---|---|
| pyramid entrance (multi) | **44596** `TOA_ENTRANCE` | 2x2 | | multi on varbit **13837 `TOA_ENTRANCE_OPEN`** (varp 3419 bit 8, quest state): `[44008, 44010]`: 0 -> **44008 `TOA_ENTRANCE_BLOCKED` "Blocked Entry"** (op "Inspect"), 1 -> **44010 `TOA_ENTRANCE_OPEN` "Entry"** (op "Enter"). Placed once at (3357, 2711, L1) |
| lobby entry | 46089 `TOA_LOBBY_RAID_ENTRY` | "Entry" 5x1 | Enter | the door into the Nexus; (3357, 9112) |
| lobby exit | 46087 `TOA_LOBBY_EXIT` | "Exit" 3x2 | Leave | (3358, 9129) |
| Nexus path doors | 46155 / 46158 / 46161 / 46164 `TOA_NEXUS_{SCABARAS,APMEKEN,CRONDIS,HET}_DOOR` | "Path of ..." 3x3 / 3x2 | Enter, Quick-Enter | `_UNSELECTED` twins 46156 / 46159 / 46162 / 46165 carry the same ops |
| Nexus Wardens door | 46168 `TOA_NEXUS_WARDENS_DOOR_OPEN` | "Entry" 7x3 | Enter, Quick-Enter | |
| room exits | 45128 `TOA_DOOR_EXIT`, 45129 `_EXIT_WARDENS`, 45130 `_EXIT_SMALL`, 45144 `TOA_ENTRANCE_KEPHRI_MAIN`, 45453 `TOA_CRONDIS_EXIT`, 45543 `TOA_ZEBAK_EXIT`, 45844 `TOA_ENTRANCE_BABA02`, 46055 `TOA_ENTRANCE_AKKHA01` | "Exit" | Leave | |
| room continue doors | 45131 `TOA_DOOR_CONTINUE` "Entry" 1x5, 45337 `TOA_SCABARAS_CONTINUE` 1x3, 45397 `TOA_PATH_CRONDIS_CONTINUE` 2x3, 45500 `TOA_PATH_APMEKEN_CONTINUE` 1x3 | "Entry" | Enter, Quick-Enter | the exit of each puzzle |
| path barriers | 45135 `TOA_PATH_BARRIER` "Barrier" 1x1; 45136 `TOA_WARDENS_BARRIER` | | Pass, Quick-Pass (Wardens: Pass) | |
| teleport crystals | 45137 `TOA_TELEPORT_CRYSTAL_CONTINUE`, 45138 `..._WARDENS` | "Teleport crystal" | Use | |

### 7.2 Lobby objects
| loc | id | ops | multi |
|---|---|---|---|
| **Grouping Obelisk** | 46068 `TOA_GROUPING_BOARD` 3x3 | Inspect | opens the party list 772 [I from the name] |
| Scoreboard (lobby) | 46071 `TOA_SCOREBOARD` | Read | opens 775 [I] |
| **Invocation Board** | 46073 `TOA_INVOCATION_BOARD` | Read | opens 776 [I] |
| Bank camel (multi) | parent **46223** `TOA_BANK_CAMEL_PARENT` 2x2 | | varbit **14446** `TOA_CAMEL_NAME_LEARNED`: `[46075 "Bank Camel", 46231 "Cam the Camel", -1]`; ops Bank, Collect (op 3). NPC form 11806 `TOA_BANK_CAMEL` (size 2) |
| **Rewards Niche** (multi) | parent **46224** `TOA_REWARDS_CHEST_LOBBY` | | varbit **14319** `TOA_SHOULD_HAVE_LOOT` (varp 3585 bit 1): `[46083, 46082, -1]`: 0 -> 46083 `..._CLOSED` "False Door" (op "Claim"), **1 -> 46082 `..._OPEN` "Rewards Niche" (op "Claim")**, >= 2 hidden. Wiki: "the Rewards Niche in the south-eastern corner of the lobby" |
| Gravestone chest | 46078 `TOA_LOBBY_GRAVESTONE_CHEST` | Claim | |
| Cape chest | 46080 `TOA_LOBBY_CAPE_CHEST` | Search | |
| Sack | 46077 `TOA_SACK_FULL` | Search | |
| Lamp crate | 46225 `TOA_LOBBY_LAMP_CRATE`, op variant 46085 "Crate" | Search | |
| Pickaxe cavity (multi) | parent 49566 `TOA_LOBBY_WALL02_CAVITY_PARENT` | Take-pickaxe, Deposit-pickaxe | varbit **14440** `TOA_PICKAXE_STORED` (5 bits, varp 3635): 24-entry list of pickaxe-tier variants (`[49568 EMPTY01, 49569 IRON01, ...]`; the order is the pickaxe tier, with Dragon / Infernal / Crystal / Trailblazer duplicates for the retired variants); the Het statue 45468 uses an analogous list on the same varbit |
| Deposit pot | 49583 `TOA_POTTERY_BANKDEPOSIT` | Deposit | |

### 7.3 Vault ("Osmumten's burial chamber", region 14672)
- **Chests**: eight multis `TOA_VAULT_CHEST_LOC0..7` on varbits 14356-14360 (varp 3589) and 14370-14372 (varp 3590), 3 bits each. The list is `[41696, 41696, 44786, 41696, 44790(/91/92), 41696]` and any value above 5 takes the last entry:
  **0, 1, 3, 5+ -> 41696 `..._NOTMINE_STANDARD` "Chest" (op "Open")**, **2 -> 44786 `..._MINE_STANDARD` "Chest" (op "Open")**, **4 -> 44790 / 44791 / 44792 `..._EMPTY` "Chest" (op "Search")** (the variant differs per chest). The *opened* chests 44787-44789 (`..._OPEN`, op "Search") are not in any list, so the server spawns or swaps them. The server must
  reject opening a "Chest" that is not the player's (only value 2 is, per player), [I]. Values 1 and 3 are listed explicitly but resolve to the same not-mine chest (probably slots for "rare" variants that this cache does not define); only 2 and 4 differ (OPEN-TC5).
- **Sarcophagus**: `TOA_VAULT_SARCOPHAGUS` (14373, 3 bits, varp 3590) drives three multis with the **same parity pattern** `[even, odd, even, odd, even]`: **46220** (3x2) `[44825 CLOSED "Sarcophagus" (no op), 44826 CLOSED_RARE "Sarcophagus" (op "Open"), 44825, 44826, 44825]`, **46221** barrier `[44938 BARRIER, 44939 BARRIER_RARE, ...]` and **46222** glow `[44996 TILE_GLOW_STANDARD, 44997 TILE_GLOW_RARE, ...]`. So **odd values (1, 3) = purple (unique rolled)**, even = white; the "Open" op exists only on the purple form; opened form 44934 `..._OPEN` (3x2, op "Search"). Wiki: "if a unique is rolled the flames glow purple ... the designated player opens the sarcophagus" [I]. Values 5-7 take the last (even) entry.
- Vault scoreboard 44942 "Read"; Bags 44984 "Search"; `TOA_VAULT_BOOK_SACKS`. Strange wall (multi **46228**, varbit 14542 `TOA_FOUND_SECRET_PASSAGE`: `[46239 hidden "Strange tablet", 46238 "Strange tablet" (op "Enter")]`) and strange chest (multi **46232**, varbit 14447 `TOA_SECRET_CHEST_STATUS`: `[46229 "Strange Chest" (op "Open"), 46230 open, -1]`) are the camel quest secret.
- Boss-room scenery that has ops: Het mirrors 45455 (Pick-up, Rotate-clockwise, Rotate-anticlockwise, Push) and 45457 (Clean), Het barriers 45462 / 45464 (Break), Crondis water 45398 / 45399 (Fill), Apmeken supports 45494 / 45495 (Repair), hammers 45497 / potions 45498 (Take), Zebak rock steps 45509 (Climb-up), Scabaras plates 45344 (Flip), buttons 45338 / 45355 (Push), memory-game tiles 45356-45364 (Activate), tablet 45339 (Inspect), stepping stone 45396 (Jump-to), hole 45343 (Crawl-through). The puzzle rules are server data.

---

## 8. NPCs [R]
Combat stats order in the definition: attack / defence / strength / hitpoints / ranged / magic. `cb` = combat level. **NPC ids are shared by every raid level**: scaling is server data (the plugins add the raid-level factor themselves, e.g. Akkha's Shadow `hp += hp * (4 * raidLevel / 10) / 100`).

**Guides and services**
| NPC | id / symbol | ops | notes |
|---|---|---|---|
| **Osmumten** (the guide before a path) | **11689** `TOA_OSMUMTEN_VIS` | Talk-to, **Proceed** | multis 11830-11833 (Baba / Zebak / Kephri / Akkha) pick it on the quest varbit 13836 |
| Osmumten (before the Wardens) | **11690** `TOA_OSMUMTEN_WARDENS_VIS` | Talk-to, **Begin** | multi 11834 |
| Osmumten / Spirit in the vault | multi **11829** `TOA_OSMUMTEN_VAULT` on varbit **14464** `ITT_MET_OSMUMTEN` (2 bits): `[11691 "Spirit" (Talk-to), 11692 "Osmumten" (Talk-to), 11693 "Osmumten" (Talk-to, **Leave**), -1]` | | |
| **Helpful Spirit** | **11694** `TOA_MIDRAIDLOOT_TRADER` | **Claim** | opens the supply bundles 777 |
| Amascut (cutscene guide) | 11696 `TOA_AMASCUT_VIS` | Talk-to | |
| Maisa | 11807 `TOA_MAISA_VIS` | Talk-to | lobby multi 11836 |
| Apmeken / Crondis / Het (lobby) | 11809 / 11810 / 11811 `TOA_*_VIS` | Talk-to | lobby multis 11837-11839; nexus/captured/freed forms 11654-11663, 11686-11688 and multis 11818-11828 (quest state 13836) |
| Bank camel | 11806 `TOA_BANK_CAMEL` (size 2) | | loc form in 7.2 |
| Player ghost | 11695 `TOA_PLAYER_GHOST` | | death marker [I] |

**Bosses and rooms (id, size, cb, hitpoints stat)**
| room | NPCs |
|---|---|
| Scabaras | 11697 Scarab (cb56, hp12); 11698 / 11699 `TOA_SCABARAS_GUESSER_OBELISK` / `_HIT` "Obelisk" (hp120; op "Hit") |
| Het | 11706 / 11707 "Het's Seal" protected / weakened (size 3, op "Destroy"), 11708 Orb of Darkness |
| Apmeken | baboons 11709-11718 (Brawler / Thrower / Mage x2 grades, Shaman cb74 hp40, Volatile cb72 hp8, Cursed cb70 hp20, Thrall cb52 hp2) |
| Crondis | 11700-11704 Palm of Resourcefulness (size 5, op "Water"), 11705 Crocodile (cb71 hp30) |
| **Kephri** | 11719 shielded / 11720 weak / 11721 enraged / 11722 dead (size 5, cb341, hp150 / 150 / 80 / 80), 11723 Scarab Swarm, 11724-11726 Soldier / Spitting / Arcane Scarab (cb89 hp40), 11727 Agile Scarab (cb53 hp30), `TOA_KEPHRI_*` |
| **Akkha** | 11789 spawn, 11790-11792 melee / range / mage, 11793-11796 enrage forms (size 3, cb337, hp400, stats 100/80/140/400/100/100), 11797-11799 Akkha's Shadow (cb108 hp70), 11800-11804 trail / unstable orbs, 11805 `AKKHA_HEADBAR_NPC` |
| **Ba-Ba** | 11778 Ba-Ba (size 5, cb359, hp380), 11779 coffin, 11780 digging, 11781 Baboon (cb77 hp10), 11782 / 11783 Boulder, 11784-11787 Rubble, 11788 `TOA_BABA_SARC_NPC` |
| **Zebak** | 11730 Zebak (size 9, cb371, hp580), 11731 tail, 11732 enraged, 11733 dead, 11734 tail dead, 11735 / 11736 Jug, 11737 Boulder, 11738 / 11739 Wave / Bloody Wave, 11740 / 11741 Crocodile, 11742 / 11743 Blood Cloud; 10680 `TOA_ZEBAK_TRANSMOG` |
| **The Wardens** | Elidinis' / Tumeken's Warden P1 11748 / 11749 (inactive 11746 / 11747; size 5, cb489, hp800), obelisks 11750-11752 (size 3, hp260), P2 11753-11758 (mage / range / exposed, hp140), P3 11759-11764 (hp880, cb489 inactive / 544 active, charging 11763 / 11764), deaths 11765 / 11766, Amascut phantom 11767 / 11768 (size 8), energy 11769, cores 11770 / 11771, phantoms 11774-11777 (Zebak / Ba-Ba / Kephri / Akkha) |
| cutscene copies | 12125-12132 `OSB10_TOA_*_CUTSCENE` |

**Pets**: `WARDEN_PET_AKKHA` 11846 (Akkhito, ops Talk-to / Metamorphosis / Pick-up) and POH forms (11840 ...).
**Not in the static map**: the cache holds no NPC spawn positions; all of the above are placed by the server (Wiki positions for Osmumten and the Helpful Spirit are in the Nexus / vault respectively).

---

## 9. Chat strings and plugin reads (fixtures, not cache data)
RuneLite's ToA plugins (`tombs-of-amascut`, `tombs-of-amascut-stats`, `toa-points-tracker`) react to these game messages [R plugin sources]:
- `Challenge started: Ba-Ba.` / `Kephri.` / `Akkha.` / `Zebak.` / `The Wardens.`
- `Challenge complete: Ba-Ba.` ... `Challenge complete: The Wardens.` (the complete line has the form `Challenge complete: <Path of> X ... Total: m:ss.t ...`, and `Challenge complete: The Wardens ... completion time: m:ss.t`), `Overall time to beat: mm:ss(.cc)`.
- `As Elidinis' Warden falls, Tumeken's Warden powers up!` / `As Tumeken's Warden falls, Elidinis' Warden powers up!`; `Elidinis' Warden uses the last of its power to restore Tumeken's Warden!` / `Tumeken's Warden uses ... restore Elidinis' Warden!`.
- `Your party failed to complete the challenge...`; a line ending `violently!`; `Total deaths`; `The number @...@N has been hastily chipped into the stone.`
- Menu texts the plugins match: "Enter"/"Quick-Enter" on a target containing "Entry", "Take-pickaxe" on "Statue", "Presets", "Open" on chests, "Crush" on Smelling salts, "Drink" on Liquid adrenaline.
Widgets the plugins read: HUD texts 481:45 / 49 / 51 / 53 / 55 (path levels) and sprite 481:41; party-details 774:32 (`MEMBERS_LIST`), 774:87 (`RAID_LOOT_POTENTIAL` children with "%"), the frame title `Party of ...`; party list 772:16; summary; 481:40 existence = in raid.
Varbits the plugins read: `TOA_CLIENT_P0..7`, `TOA_CLIENT_RAID_LEVEL`, `TOA_MIDRAIDLOOT_ENERGY_ACTIVE`, `TOA_MIDRAIDLOOT_STATS_TIMER`, `TOA_PICKAXE_STORED`; varp `TOA_PERSONAL_CONTRIBUTION`; varcs 1099-1106.

---

## 10. Blockers and open points
1. **Server-only data is not in the cache** (section 5). The client contract here is complete for the interfaces, but every number behind them is a rule that needs its own `docs/mechanics/raids/toa/*.md`.
2. **Invocation state is not a varp**: the server keeps the three words and sends them as ints of 6729 every time the screen is (re)built, and keeps presets in varps 3680-3694. The prerequisite / exclusivity rules (params 1346, 1863) are server rules.
3. **Send-order contracts** (silently wrong otherwise): section 3.
4. **The instance must use the template squares of section 6.1** and keep interface 481 open for the whole run (RuneLite's in-raid test). The x >= 6400 style coordinate rule of ToB does **not** exist in the ToA scripts [R: no `coordx` test in 6576-6585].

OPEN items (each implemented, when needed, behind a named config flag):
- **OPEN-TC1**: `TOA_CLIENT_PRIMARY0..7` (varp 3590 bits 2..9). Only effect: HUD orb sprite 3576 instead of 3574 (script 6583). Neither the cache, the plugins nor the Wiki say what it marks [?]. Default to 0 for all.
- **OPEN-TC2**: `TOA_CLIENT_P` value **31** (and the unused 28 / 29): script draws a full white orb with sprite 3575 and no HP update. Dead is 30. What 31 stands for (disconnected / spectator / hidden?) is not in the cache.
- **OPEN-TC3**: `TOA_CLIENT_CURRENT_PATH` values **5 and 6** (both give the compact "no level" panel) and 7 (default). 0..4 are decoded in section 2.1; the mapping of 5 / 6 to Wardens phases or "loot room" is unknown.
- **OPEN-TC4**: interface 771's item layer (771:10) has no drawing script in the cache. The server must supply the item display (standard inventory interface-item message) and inv 811 [I from ToB 23:12].
- **OPEN-TC5**: vault chest values 1 / 3 / 5-7 and sarcophagus values 5-7 (section 7.3); which of the two odd sarcophagus values means "mine" vs "someone else's" unique.
- **OPEN-TC6**: text formats of the IF_SETTEXT-only components: summary times (482), scoreboard cells (775:47-84), totals; and the `Raid Level`/`Active` figures of the summary invocations table.
- **OPEN-TC7**: titles: `Chocolate Teapot` has no struct in enum 4668; `TOA_TITLE_SELECTED` / `TOA_TITLE_ACHIEVED_*` have no reader; the id mapping between the achieved-bit order and enum 4668 is unknown; no interface selects a title.
- **OPEN-TC8**: varbits/varps without a client reader: `TOA_DAMAGE_DONE`, `TOA_DAMAGE_TAKEN`, `TOA_DAMAGE_DONE_CURRENT`, `TOA_DAMAGE_TAKEN_CURRENT`, `TOA_MIDRAIDLOOT_CLAIMED` / `_ROLLED`, `TOA_KICKED_FROM_RAID`, `TOA_CLIENT_RAID_LEVEL_STOP_AUTO_UPDATING`, `TOA_WARDENS_BUFF_TYPE` / `_VALUE`, `TOA_PERSONAL_CONTRIBUTION` (plugin only) and the unnamed varps of section 1.1. Encodings are the names' obvious ones; semantics are the server's.
- **OPEN-TC9**: the mid-raid stats buff: `5927` key 4376 writes varc 1098 but the buff-bar reader (5923) uses varc 1089 (client-set) times varbit 14344 times 25 (script 5942); the exact unit is not decoded.
- **OPEN-TC10**: gameframe slots: the cache does not say where 481, 482, 771, 773, 775, 776, 777, 778 are mounted. 772 / 774 / 6601-6729 require `MAINMODAL` (enum 900 -> 10551312 stretch / the fixed and resizable variants, as for CoX / ToB). 481 and 773 are overlays placed by script 4729 (constants 65256 / 52968). 778 is a floating window with its own Close button [I].
- **OPEN-TC11**: whether the invocation tiles' enable state is validated server-side only (yes: the client sends sub 52 + k whatever the state) and how exclusive groups (e.g. the four attempt invocations, the time limits, the three Pathseeker levels) resolve; only the 1346 prerequisites are in the cache.
- **OPEN-TC12**: bounds of the number dialog behind the "Set Completions" op.
- **OPEN-TC13**: whether "Move up" on row 1 promotes the member to leader (Wiki: leaders can promote members while the group forms) and what happens to rows 2-7.
- **OPEN-TC14**: script `6745(index, text)` (relabel a pause button) and `4792` entries 5 / 6 / 7 usage by the server in practice; script 890's exact effect was not decoded.
