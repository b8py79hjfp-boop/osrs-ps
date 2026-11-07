# Teleports

Code: `content/magic/*` (`Teleports`, `SpellTeleports`, `HomeTeleports`, `TeleportTablets`, `Jewellery`); plugin-api
additions `ContentScope.onItemOp`, `PlayerRef.choiceDialog`, `PlayerRef.startScript`, `SharedAttrs.WILDERNESS_LEVEL`
(DECISIONS D-030). Sources (OSRS Wiki raw wikitext fetched 2026-11-06 unless noted): the spell pages
(`<Name>_Teleport`, `Teleport_to_House`, `Teleport_to_Ape_Atoll`, `Lumbridge_Home_Teleport`,
`Edgeville_Home_Teleport`, `Lunar_Home_Teleport`), `Teleportation_spells` (S), `Home_teleport` (HT), `Teleport_tablet`
(TT), `Wilderness` (W: "Teleport restrictions"), `Teleblock` (TB), the item pages `Amulet_of_glory`,
`Games_necklace`, `Ring_of_dueling`, `Combat_bracelet`, `Skills_necklace`, `Ring_of_wealth`; our cache 2757 (config
dumps, seq lengths, interface 218/387 actions); RS Mod v2 (ISC) for the `Spellbook` varbit values and the chatbox
menu script. RS Mod has no teleport spell, tablet or jewellery script.

## Rules

| # | Rule | Source |
|---|---|---|
| T1 | A teleport click is ignored while the player is delayed or already teleporting. The checks run in this order: spellbook (the ancient and lunar components share interface 218: the spell's book must be the active `spellbook` varbit, 0 standard / 1 ancient / 2 lunar / 3 arceuus; the Arceuus teleports and home teleport are in magic-utility.md AR1; runes are paid with `SpellRunes`: staves and combination runes, magic-utility.md SP1-SP3), Magic level (boosted level counts: `level`), Tele Block, Wilderness level, runes. Tablets and jewellery skip level and runes. | RS Mod `Spellbook`; S |
| T2 | The teleport is a stalled action: interrupt (route, interaction, modal, weak scripts; interactions.md I7), play the cast animation + graphic on the click tick, stall the player (`delay`) and move on the tick the stall ends, then reset the animation. Stall length = the spell's Wiki "speed" minus 1: standard and lunar teleports (speed 4) 3 ticks, ancient (speed 5) 4 ticks. Jewellery and tablets stall like the standard spell. The move itself is the engine's `teleport` (route cleared, interaction dropped). Runes, XP and the tablet or charge are taken at the click, before the stall. A second cast cannot start during the stall. | S speeds; scripts.md OPEN-S7 (osrs-docs: Falador "three tick delay"; Henke: "four") |
| T3 | Animations (cache symbols): standard `human_castteleport` 714 + spotanim `teleport_casting` 111 (cache seq 714 = 79 cycles, about 3 ticks); ancient `zaros_vertical_casting` 1979 + `zaros_teleport` 392; lunar seq 1816 + `lunar_teleport_spotanim` 747; tablet `poh_smash_magic_tablet` 4069 (44 cycles) then at tick 2 `poh_absorb_tablet_teleport` 4071 (41 cycles) with `poh_absorb_tablet_magic` 678. | cache names and seq lengths (`cache-dump config seq`) |
| T4 | Tele Block: `SharedAttrs.TELEBLOCK_UNTIL` greater than the current tick blocks every spell, tablet, jewellery and home teleport with the message below. Wilderness: `SharedAttrs.WILDERNESS_LEVEL` (kept current once per tick by `Wilderness.updateArea`, includes the PvP-world +15: wilderness.md W3) above the limit blocks the teleport: 20 for spells (all spellbooks), tablets, Lumbridge home, Games necklace and Ring of dueling; 30 for the Amulet of glory, Combat bracelet, Skills necklace and Ring of wealth. | W "Teleport restrictions" and "Member teleportation up to level 30 Wilderness"; TB |
| T5 | Cost, XP and destination of each spell are the Wiki infobox and cost table (rows below). The destination is the Wiki's map pin, or the centre of its map polygon. XP is granted at the click. | spell pages |
| H1 | Home teleports: Lumbridge (standard book), Edgeville (ancient, component `teleport_home_zaros`), Lunar Isle (lunar). No runes, level or XP. One shared cooldown of 3,000 ticks (30 minutes) from the move; the remaining time is reported in minutes. Cannot be cast within 16 ticks of combat (the logout lock, wilderness.md W7: "10 seconds or more after combat has ended (similar to logging out)"). | HT; Lumbridge_Home_Teleport |
| H2 | The default home cast takes 24 ticks and has five stages (seq / spotanim): `aide_drawing_chalk_circle` 4847 / `aide_chalk_circle` 800 at tick 0; `aide_sitting_down_crosslegged` 4850 / `aide_chalk_circle_static` 801 at 5; `aide_player_getting_book` 4853 / `aide_player_book_get` 802 at 7; `aide_reciting_incantation` 4855 / `aide_player_book_portal` 803 at 9; `aide_player_teleporting` 4857 / `aide_player_teleport` 804 at 14; the move at 24. | Wiki "Home teleport" (24 ticks default); stage ticks OPEN-TP10 |
| H3 | The home cast does not stall the player: it runs on a soft timer and ends in `teleport`, then the cooldown starts. | HT "interrupted at any time by most actions" |
| H4 | A home cast is interrupted (animation reset, no cooldown) by movement, starting an interaction, any combat, a Tele Block, or starting any other teleport. | HT, Lumbridge_Home_Teleport ("interrupted by combat") |
| B1 | Magic tablets: inventory op "Break" (cache iop 1). The tablet is consumed in a transaction; no level, runes or XP. The same Wilderness limit (20), Tele Block and stall as the stored spell; the tablet is not consumed when refused. Tablets: Varrock, Lumbridge, Falador, Teleport to House, Camelot, Ardougne, Watchtower, Kourend Castle, Civitas illa Fortis and the eight ancient teleports. | TT; cache obj 8007 (`iops = [0=Break, 1=Varrock, 2=Grand Exchange, 3=Toggle]`) |
| B2 | Trollheim and Ape Atoll have no tablet. Lunar spells have no tablets. | TT |
| J1 | Charged jewellery. Backpack op "Rub" (cache iop 4, button op 6) opens a chatbox menu of the destinations (`chatmenu`, client script 58); the pick teleports. The worn tab lists the destinations as ops 2.. (op 1 is Remove). Both paths re-check that the item is still there, then take one charge in a transaction (`replaceAt` to the item with one charge less, or the uncharged item, or `removeAt` if it crumbles), then teleport. | glory page ("rub" in the inventory, or right-click while equipped); cache objs |
| J2 | Charges and destinations (Wiki "Teleportation options"): see the table below. Games necklace: 8 charges, crumbles to dust after the last. Ring of dueling: 8, crumbles. Amulet of glory: up to 6 (4 normal), becomes the uncharged amulet. Combat bracelet: up to 6. Skills necklace: up to 6. Ring of wealth: up to 5. | item pages |
| J3 | Messages (quoted by the Wiki for the games necklace and ring of dueling): `You rub the necklace...` / `You rub the ring...`, then `Your games necklace has three uses left.` or, on the last charge, `Your games necklace crumbles to dust.` | Games_necklace:61, Ring_of_dueling:61 |
| J4 | An uncharged item answers `Your <item> has no charges.` | OPEN-TP11 |
| J5 | Changing the worn item's charge count republishes `AppearanceChanged`, so the look is re-sent (the obj id changes, or the item is gone). | — |
| C1 | `ContentScope.onItemOp(text)` hands a backpack op (by cache iop text) to one owning plugin, before the generic backpack buttons (`Equipment`); `PlayerRef.choiceDialog(title, choices)` opens `chatmenu` as the chatbox modal, enables pause-button events on its options and calls back with the 1-based pick (hostile input: a pick outside 1..count, or without a pending menu, is ignored; the pending menu is dropped at logout); `PlayerRef.startScript` runs a suspend script as a strong queue entry with delay 0. | RS Mod `ifChoice`, `ClientScripts.chatboxMultiInit` (script 58) |

### Spells

| Spell | Book | Level | XP | Runes | Destination | Tablet | Wiki unlock (not enforced) |
|---|---|---|---|---|---|---|---|
| Varrock Teleport | std | 25 | 35 | law 1, air 3, fire 1 | 3213,3424 | yes | — |
| Lumbridge Teleport | std | 31 | 41 | law 1, air 3, earth 1 | 3222,3218 | yes | — |
| Falador Teleport | std | 37 | 48 | law 1, air 3, water 1 | 2964,3378 | yes | — |
| Teleport to House | std | 40 | 30 | law 1, air 1, earth 1 | 3222,3218 (OPEN-TP4) | yes | — |
| Camelot Teleport | std | 45 | 55.5 | law 1, air 5 | 2757,3479 | yes | — |
| Kourend Castle Teleport | std | 48 | 58 | law 2, fire 1, water 1 | 1641,3673 | yes | Client of Kourend |
| Ardougne Teleport | std | 51 | 61 | law 2, water 2 | 2662,3305 | yes | Plague City |
| Civitas illa Fortis Teleport | std | 54 | 64 | law 2, earth 1, fire 1 | 1681,3133 | yes | Twilight's Promise |
| Watchtower Teleport | std | 58 | 68 | law 2, earth 2 | 2546,3114 | yes | Watchtower |
| Trollheim Teleport | std | 61 | 68 | law 2, fire 2 | 2890,3679 | no | Eadgar's Ruse |
| Ape Atoll Teleport | std | 64 | 74 | law 2, fire 2, water 2, banana 1 | 2798,2799 | no | Recipe for Disaster (Awowogei) |
| Paddewwa Teleport | anc | 54 | 64 | law 2, air 1, fire 1 | 3097,9881 | yes | Desert Treasure I |
| Senntisten Teleport | anc | 60 | 70 | law 2, soul 1 | 3320,3337 | yes | Desert Treasure I |
| Kharyrll Teleport | anc | 66 | 76 | law 2, blood 1 | 3494,3473 | yes | Desert Treasure I |
| Lassar Teleport | anc | 72 | 82 | law 2, water 4 | 3004,3470 | yes | Desert Treasure I |
| Dareeyak Teleport | anc | 78 | 88 | law 2, fire 3, air 2 | 2968,3696 | yes | Desert Treasure I |
| Carrallangar Teleport | anc | 84 | 94 | law 2, soul 2 | 3158,3666 | yes | Desert Treasure I |
| Annakarl Teleport | anc | 90 | 100 | law 2, blood 2 | 3288,3888 | yes | Desert Treasure I |
| Ghorrock Teleport | anc | 96 | 106 | law 2, water 8 | 2976,3872 | yes | Desert Treasure I |
| Moonclan Teleport | lunar | 69 | 66 | astral 2, law 1, earth 2 | 2113,3915 | no | Lunar Diplomacy |
| Ourania Teleport | lunar | 71 | 69 | astral 2, law 1, earth 6 | 2468,3246 | no | Lunar Diplomacy, Baba Yaga |
| Waterbirth Teleport | lunar | 72 | 71 | astral 2, law 1, water 1 | 2546,3755 | no | Lunar Diplomacy |
| Barbarian Teleport | lunar | 75 | 76 | astral 2, law 2, fire 3 | 2543,3569 | no | Lunar Diplomacy |
| Khazard Teleport | lunar | 78 | 80 | astral 2, law 2, water 4 | 2636,3167 | no | Lunar Diplomacy |
| Fishing Guild Teleport | lunar | 85 | 89 | astral 3, law 3, water 10 | 2613,3391 | no | Lunar Diplomacy |
| Catherby Teleport | lunar | 87 | 92 | astral 3, law 3, water 10 | 2801,3450 | no | Lunar Diplomacy |
| Ice Plateau Teleport | lunar | 89 | 96 | astral 3, law 3, water 8 | 2975,3938 | no | Lunar Diplomacy |
| Lumbridge Home Teleport | std | 0 | 0 | none | 3221,3218 | — | — |
| Edgeville Home Teleport | anc | 0 | 0 | none | 3088,3502 (centre of 3087..3090 x 3501..3504) | — | — |
| Lunar Home Teleport | lunar | 0 | 0 | none | 2094,3913 | — | — |

Ice Plateau Teleport first asks to confirm ("warns the player ... with an option to abort", Wiki), then casts.

### Jewellery

| Item | Charges | End of charges | Limit | Destinations (worn ops 2..) |
|---|---|---|---|---|
| Amulet of glory | 6 | uncharged amulet | 30 | Edgeville 3087,3496; Karamja 2918,3176; Draynor Village 3105,3251; Al Kharid 3293,3163 |
| Games necklace | 8 | crumbles | 20 | Burthorpe 2899,3553; Barbarian Outpost 2520,3571; Corporeal Beast 2967,4254; Tears of Guthix 3245,9500; Wintertodt Camp 1631,3940 |
| Ring of dueling | 8 | crumbles | 20 | Emir's Arena 3315,3235; Castle Wars 2440,3090; Ferox Enclave 3151,3635; Fortis Colosseum 1793,3107 |
| Combat bracelet | 6 | uncharged bracelet | 30 | Warriors' Guild 2882,3547; Champions' Guild 3192,3368; Monastery 3052,3490; Ranging Guild 2653,3439 |
| Skills necklace | 6 | uncharged necklace | 30 | Fishing Guild 2611,3390; Mining Guild 3049,9763; Crafting Guild 2933,3295; Cooking Guild 3144,3438; Woodcutting Guild 1662,3505; Farming Guild 1248,3719 |
| Ring of wealth | 5 | uncharged ring | 30 | Miscellania 2534,3862; Grand Exchange 3163,3478; Falador 2995,3375; Dondakan 2824,10168 |

Item ids are resolved at world start from the cache names `Amulet of glory(n)`, `Games necklace(n)`, `Ring of dueling(n)`,
`Combat bracelet(n)`, `Skills necklace(n)` and `Ring of wealth (n)` (and the uncharged names).

## OPEN

- **OPEN-TP1:** the Tele Block message. The Wiki (2 March 2017 change) says a blocked player is told how long remains
  but quotes no text. Implemented: `A magical force stops you from teleporting.` The Wilderness refusal
  (`You can't teleport above level N in the Wilderness.`), the combat refusal of home teleports (`You can't cast this
  spell while in combat.`), the cooldown text (`You need to wait another N minutes to cast this spell.`), the Ice Plateau
  warning text and the jewellery menu title are also our wording; the Wiki only describes them.
- **OPEN-TP2:** Tele Block and the Wilderness level are checked at the click only; a block that lands during the stall
  does not cancel the teleport. Teleports on PvP worlds use the displayed level (outside the Wilderness 15), so spells
  work only in safe zones there; the Wiki states the +15 only for combat. PvP-world special attack lock ("cannot teleport
  after using a special attack", PvP world page) is not implemented.
- **OPEN-TP3:** animation ids. The Wiki has no ids; they are cache symbols: standard 714/111 (symbol names say
  teleport casting), ancient 1979/392 (the id the combat module already uses for ancient casting; `zaros_teleport`),
  lunar 1816 (cache name `human_teleport_other_impact`; seq 1816 ends in a 2,000-cycle hold frame, which is why the
  animation is reset on arrival)/747, tablet 4069 then 4071+678 at tick 2 (44 cycles = 1.5 ticks, rounded up). Spot
  animation height is 0. T2: stall = Wiki speed - 1.
- **OPEN-TP4:** Teleport to House has no house to go to; it sends the player to our respawn point (3222,3218). The
  "Outside" op (cache interface 218 action 2) and the Group op are not handled.
- **OPEN-TP5:** quests and unlocks (see the spell table) cannot be checked: there is no quest or unlock state.
  `SpellTeleports.ENFORCE_UNLOCKS` (false) would refuse every spell with an unlock; flip it once quests exist. Tablets and
  jewellery destinations with unlocks (Tears of Guthix, Wintertodt/Zeah, Fortis Colosseum Hero title, Miscellania,
  Dondakan, Farming Guild level 45 "inside" tile) are not gated; the Farming Guild uses the Wiki's outside tile. Not
  handled either: the Varrock / Camelot / Watchtower diary destinations (Grand Exchange, Seers' bank, Yanille: cache
  interface 218 op 2 and the `Configure` op 10).
- **OPEN-TP6 (runes):** only the plain runes in the inventory are used. Elemental staves, tomes, combo runes, the rune
  pouch and Blighted teleport spell sacks (Wiki: a staff of air makes Camelot cost one law rune) are not modelled: the
  codebase has no staff-rune rule yet (content/combat `Magic` charges plain runes too).
- **OPEN-TP7:** ancient components `zarosteleport1..8` are mapped to Paddewwa .. Ghorrock by the numbering being in level
  order (cache gameval names; no text or tooltip confirms it); lunar `tele_ghorrock` is Ice Plateau Teleport (the old
  Ghorrock name). Verify with a real client.
- **OPEN-TP8:** the Tele Group spells, Arceuus spellbook teleports, Minigame Teleport and the Teleport to Target
  spell are not implemented; the home-teleport animation override (op 10) and the league home teleports neither.
- **TP9 (resolved):** the home teleport cooldown is varp `aide_tele_timer` (892) in minutes since the epoch, as
  RuneLite's `LAST_HOME_TELEPORT`. It is real time, saved with the account and shown by the client. It starts when the
  teleport happens; an interrupted cast does not start it (Wiki silent).
- **TP14:** Revenant Caves: "a delay of 2 ticks (1.2 seconds) upon selecting any method of teleportation before it is
  activated, provided the player is in combat with an NPC ... This delay can be interrupted by another incoming
  attack from an NPC, resulting in the teleport delay timer having to be run down anew." OPEN-TP14: "removed if the
  player has completed the hard Wilderness Diary" waits for diaries.
- **OPEN-TP10:** home stage ticks. The Wiki gives only the 24-tick total. Start ticks 0, 5, 7, 9, 14 are each earlier
  seq's length without its trailing hold frame (cycles 149, 43, 58, 150 at 30 cycles per tick, rounded up); the 1:1
  pairing of spotanims 800..804 with seqs 4847/4850/4853/4855/4857 follows the cache names.
- **OPEN-TP11:** messages of the dragonstone jewellery (glory, bracelet, necklace, ring of wealth): the Wiki quotes only
  the games necklace and ring of dueling. Implemented the same pattern: `You rub the amulet...`,
  `Your amulet of glory has 3 charges left.`, `Your amulet of glory has run out of charges.`. Eternal glory, trimmed
  glory and imbued rings are not handled.
- **OPEN-TP12:** the worn-tab op mapping (op 1 Remove, ops 2.. the destinations in the order of cache obj params
  451..456, which equal the Wiki's worn options) is inferred from the cache; the Ring of wealth's Boss Log and Coin
  Collection ops (5, 6) answer `That option isn't available yet.` Inventory "Rub" also has right-click sub-ops (cache
  `iopSubOps`). IF_SUBOP is now delivered: a right-click sub-option teleports to the destination whose name contains
  the sub-op text, and the plain "Rub" still opens the menu.
- **OPEN-TP13:** the chatbox menu allows 2..6 options (RS Mod stops at 5); the Skills necklace needs 6 and its layout is
  unverified against a real client.
