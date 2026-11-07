# Runecraft

`content/skills/.../Runecraft.kt`, data in `RunecraftData.kt` (one `Temple` row per regular runic altar). Shared skilling rules:
skilling.md. Sources, fetched as wikitext from oldschool.runescape.wiki on 2026-11-06:

- (R) "Runecraft" (https://oldschool.runescape.wiki/w/Runecraft): rune table (levels, xp, essence), essence, talismans, tiaras,
  altars, "Multiple runes" table
- (M) "Mysterious ruins": ruins pins (tile of each ruin), ids, "Enter" with a tiara
- (A) "Runic altar" and the thirteen altar pages ("Air Altar" ... "Wrath Altar"): altar id and interior pin, tiara xp, quest
  requirements, entry rules; "Chaos Altar" (maze), "Cosmic Altar" (four portals)
- (T) "Talisman", "Tiara", "Elemental talisman", "Catalytic talisman"
- (C) the cache (cache 2757): loc ids, ops, multi-loc varbits, exit-portal and altar placements (`cache-dump find-locs`), gameval
  symbols (`human_runecraft` 791, `runecrafting` spot anim 186, varbits `rc_no_tally_required_*`)

## Rules

| # | Rule | Source |
|---|---|---|
| RC1 | **Entering.** Each ruin is a multi-loc: the placed loc (e.g. 34813 air) turns into child 0 (no op) or child 1 (op 1 "Enter") by the varbit `rc_no_tally_required_<rune>` (607 air ... 13782 blood, 6220 wrath). A talisman of the altar, or the elemental / catalytic talisman of its group, used on the ruins takes the player in. The "Enter" op needs the altar's tiara, the elemental / catalytic tiara of its group, or a Runecraft cape (and trimmed) worn; the server sets the varbit to 1 for the worn head and cape and re-checks the worn items when the op arrives. Tele Block does not stop entering. Message "You feel a powerful force take hold of you...". | M, A, T, C |
| RC2 | **Inside.** The player arrives on the walkable tile next to the exit portal that is nearest the altar; cosmic has four portals and the arrival one is picked at random (A: "randomly sent to one of the four portals"), blood two. The portal ("Use", op 1) returns the player to the walkable tile around the 3x3 ruins nearest the middle of its south side. | A, C; tiles OPEN-RC1 |
| RC3 | **What can be bound.** Rune essence works at air, mind, water, earth, fire and body; pure essence at every altar. Daeyalt, dark and blood essence, cores and pouches are OPEN-RC3. | R |
| RC4 | **One action.** "Craft-rune" (op 1), or rune / pure essence used on the altar, turns every usable essence in the backpack into runes in the same tick (no delay: Wiki "Chaos Altar" changes, 23 March 2022). Runes per essence = 1 + the number of "Multiple runes" thresholds the level reaches, with the level held before the action. Animation `human_runecraft` 791, spot anim `runecrafting` 186. | R (Multiple runes), A, C |

- **RC5: levels and xp** (per essence, not per rune; sources R, A):

  | Rune | Level | Xp | Essence | 2x, 3x, ... at level |
  |---|---|---|---|---|
  | air | 1 | 5 | rune / pure | 11, 22, 33, 44, 55, 66, 77, 88, 99 |
  | mind | 2 | 5.5 | rune / pure | 14, 28, 42, 56, 70, 84, 98 |
  | water | 5 | 6 | rune / pure | 19, 38, 57, 76, 95 |
  | earth | 9 | 6.5 | rune / pure | 26, 52, 78, 104 |
  | fire | 14 | 7 | rune / pure | 35, 70 |
  | body | 20 | 7.5 | rune / pure | 46, 92 |
  | cosmic | 27 | 8 | pure | 59 |
  | chaos | 35 | 8.5 | pure | 74 |
  | nature | 44 | 9 | pure | 91 |
  | law | 54 | 9.5 | pure | 95 |
  | death | 65 | 10 | pure | 99 |
  | blood (true altar) | 77 | 10.5 | pure | none |
  | wrath | 95 | 8 | pure | none (OPEN-RC2) |

- **RC6: tiaras** (sources A, T). A tiara and the altar's talisman in the backpack, either one used on the altar: both are used up and the talisman tiara appears, with the Wiki's xp (air 25, mind 27.5, water 30, earth 32.5, fire 35, body 37.5, cosmic 40, chaos 37.5, nature 45, law 47.5, death 50, blood 52.5, wrath 52.5). A gold tiara and the elemental / catalytic talisman make the elemental tiara (40 at the elemental altars) or catalytic tiara (60 at mind, body, cosmic and wrath, 42.5 at chaos, nature, law, death and blood).

### Altars (altar loc at tile; exit portal loc at tile; ruin tile)

air 34760 (2843,4833), portal 34748 (2841,4828), ruin (2984,3291) | mind 34761 (2785,4840), 34749 (2793,4827), (2981,3513) | water 34762
(2715,4835), 34750 (2727,4832), (3184,3164) | earth 34763 (2657,4840), 34751 (2655,4829), (3305,3473) | fire 34764 (2584,4837), 34752
(2574,4850), (3312,3254) | body 34765 (2522,4839), 34753 (2521,4833), (3052,3444) | cosmic 34766 (2141,4832), 34754 at (2121,4833),
(2142,4812), (2142,4854), (2163,4833), (2407,4376) | chaos 34769 (2270,4841), 34757 (2273,4856,3), (3059,3590) | nature 34768 (2399,4840), 34756
(2400,4834), (2868,3018) | law 34767 (2463,4831), 34755 (2464,4817), (2857,3380) | death 34770 (2204,4835), 34758 (2208,4829),
(1859,4638) | blood 43479 (3231,4831), 43478 at (3224,4832) and (3240,4832), 43476 / 43477 / 25380 at (3560,9780) | wrath 34772 (2334,4833,1),
34759 (2335,4825,1), (2445,2824). Ruin locs: the multi-loc parents `*TEMPLE_RUINED` 34813-34824 and 25380, with both children
registered. Interior and ruin pins are the Wiki's; every placement was checked against `cache-dump find-locs`.

## Checked headless (temporary scenario, deleted)

A player used a talisman on each of the thirteen ruins, arrived within two tiles of a portal and walked out through it back to the
tile beside the ruins; air: 5 pure + 3 rune essence at level 1 gave 8 air runes and 40 xp.

## OPEN

- **OPEN-RC1: arrival and exit tiles.** No source gives the tile a player arrives on inside a temple or leaves on outside the ruins.
  We use the walkable tile beside the portal nearest the altar, and the walkable tile of the ring around the ruins nearest the middle of
  the south side (collision from the cache). The Wiki says the body altar arrival is "on the north of the altar away from the portal";
  the chaos altar is a four-level maze whose arrival level is not stated (we use the level-3 portal at (2273,4856)); the blood altar's
  two portals are picked at random like cosmic's.
- **OPEN-RC2: wrath multiples.** The "Multiple runes" table has no wrath row: one rune per essence.
- **OPEN-RC3: other essence and bonuses.** Daeyalt essence at the regular altars (+50% xp), cores (mind, body, chaos), blood essence, runic
  extracts, Raiments of the Eye and the abyssal lantern are not implemented. (Pouches, dark essence and Ourania's daeyalt: RC12-RC17.)
- **OPEN-RC4: "Enter" varbit polling.** No equipment-changed event exists in plugin-api (equipment lives in `content/combat`). A soft
  timer every 3 ticks compares the worn head and cape slots and rewrites the 13 varbits when one changed, so the client may show the
  "Enter" op up to 3 ticks late. An `EquipmentChanged` event would replace it.
- **OPEN-RC5: quests.** Cosmic (Lost City), law (Troll Stronghold, to craft), death (Mourning's End Part II), blood (Sins of the Father) and
  wrath (Dragon Slayer II) have quest requirements that the ruins do not enforce yet. `RunecraftQuests` now reads quest state from the cache
  varp / varbit of `quest-states.tsv` (the Abyss rifts and the Astral altar use it), so the ruins could. Entrana's restrictions on law ruins
  and the Wilderness chaos ruin's skull are not modelled.
- **OPEN-RC6: texts.** "You bind the temple's power into <rune> runes.", "You feel a powerful force take hold of you..." and the
  failure lines ("You need a Runecraft level of N to craft X runes.", "You have no essence to bind.", "You need pure essence to bind X
  runes.") are not on the Wiki pages read.
- **OPEN-RC7: not implemented.** Guardians of the Rift (OPEN-RC19), the talisman "Locate" op (the Wiki gives only example messages,
  "The talisman pulls to the North-East.", no distances) and the Tunnel of Chaos portal.
- **OPEN-RC8: duplicate ruins.** The cache also holds a second mind ruin at (2469,5433) and a second earth ruin at (4009,4945); they
  enter the same temple (handlers are by loc id) but leave at the Wiki's pin.

## Combination runes, pouches, the Abyss and the other altars

Further sources (OSRS Wiki, fetched 2026-11-07): "Combination rune", "Binding necklace", "Magic Imbue", "Essence pouch", "Colossal pouch",
"Dark Mage (Abyss)", "Abyss", "Mage of Zamorak", "Enter the Abyss", "Skull (status)", "Ourania Altar" (+ `Module:Ourania Altar Table`), "Eniola",
"Astral Altar", "Dark Altar", "Dense runestone", "Dense essence block", "Dark essence block", "Dark essence fragments", "Blood Altar (Kourend)",
"Soul Altar (Kourend)". Cache 2757: loc / npc / varbit ids (`config`, `find-locs`). Code: `RunecraftCombination.kt`, `RunecraftPouches.kt`,
`RunecraftAbyss.kt`, `RunecraftOurania.kt`, `RunecraftZeah.kt`, `RunecraftQuests.kt`, `content/magic/MagicImbue.kt`. Quest state is read from the
cache varp / varbit of `quest-states.tsv` (`RunecraftQuests`).

| # | Rule | Source |
|---|---|---|
| RC7 | **Combination runes** (mist 6, dust 10, mud 13, smoke 15, steam 19, lava 23) at the air, water, earth and fire altars: use the other element's talisman, or its runes, on the altar (runes need the talisman in the backpack or Magic Imbue). Needs pure essence and as many of the other element's runes; `min(pure, runes)` are bound, each succeeding with 50% unless a binding necklace is worn; failed runes are lost with no xp; the talisman is used up. The elemental talisman and tiaras do not work. One rune per success (OPEN-RC10). | Combination rune, Runecraft, Air / Water Altar |
| RC8 | **Binding necklace** (cache `magic_emerald_necklace` 5521): worn, 100% success; one charge per batch crafted, 16 charges, kept per player; it crumbles at 0 and the next one starts full. | Binding necklace, Air Altar |
| RC9 | **Combination xp** per rune made: mist 8 / 8.5, dust 8.3 / 9, mud 9.3 / 9.5, smoke 8.5 / 9.5, steam 9.3 / 10, lava 10 / 10.5 (Wiki "Exp (Low)" / "Exp (High)"). OPEN-RC9: we give the high value at the altar whose own rune has the higher base xp (air 5 < water 6 < earth 6.5 < fire 7), the low one at the other. | Runecraft |
| RC10 | **Magic Imbue** (Lunar, level 82, 86 xp, 7 fire + 7 water + 2 astral): sets cache varbit `magic_imbue_active` for 21 ticks; one cast per 21 ticks. Runecraft reads the varbit. | Magic Imbue |
| RC12 | **Essence pouches** (small 3 / level 1, medium 6 / 25, large 9 / 50, giant 12 / 75, colossal 8 / 16 / 27 / 40 at 25 / 50 / 75 / 85): "Fill" (one kind of essence at a time), "Empty" (as many as the backpack takes), "Check". The base level is checked (no boosts). Contents and kind live in the cache varbits `*_essence_pouch` (603-606, 13682) and `*_essence_pouch_type`. | Essence pouch, Colossal pouch |
| RC13 | **Degrading**: each essence stored adds a random 0-3 to the pouch's wear counter; capacity steps down at the Wiki's counter thresholds (medium 400, destroyed 800; large 400 / 600 / 800, destroyed 1000; giant 200 / 300 / 400 / 600 / 800 / 1000, destroyed 1200; colossal 320 / 565 / 745 / 870 / 950 / 995 / 1015, destroyed 1021); the small pouch never degrades; a worn Runecraft cape or Max cape stops it. A fill that crosses a threshold stores only what fits the new capacity; essence beyond a lowered capacity returns to the backpack if there is room, else is lost. The first step swaps the item to its cache `_degrade` variant. Wear counters are account values `runecraft.wear.<pouch>`. | Essence pouch, Colossal pouch |
| RC14 | **Repairs**: the Dark Mage (npc 2583, op 3 "Repairs", spawned at (3039, 4834)) resets every counter and swaps degraded pouches back, in the backpack and the bank. | Dark Mage (Abyss), Essence pouch |
| RC15 | **Colossal pouch**: abyssal needle used on a small / medium / large / giant pouch (all four whole and empty) with base Runecraft 25 and Crafting 56: the four are used up, 1,000 Crafting xp. | Colossal pouch |
| RC16 | **Ourania altar** (loc 29631): pure and daeyalt essence (not rune essence) become one random rune each by the level band's cumulative table out of 10,000; xp is 1.7 x the rune's base xp (x1.5 more for daeyalt); the Ardougne medium diary (cache varbit `ardougne_diary_medium_complete`) gives an extra rune with 25% (air to chaos, astral), nature 22.5%, law 20%, death 17.5%, blood 15%, soul 10%, with no extra xp. Eniola (npc 8132, op 1 "Bank") takes 20 runes of one type (not combination runes) from the backpack and opens the bank (`OpenBankRequest`). The surface ladder (2452, 3231) and the cave ladder (3015, 5630), and the two cracks (3053, 5587) / (3055, 5586), move the player to the walkable tile beside the other end. | Ourania Altar, Eniola |
| RC17 | **Arceuus**: dense runestone (locs 8975 / 8977 / 8979, "Chip") needs Mining 38, Crafting 38, a pickaxe and a chisel; a dense essence block every 9 ticks, 12 Mining + 8 Crafting xp, 5% for two after the medium Kourend diary. The Dark Altar (loc 27979, or a block used on it) turns every dense block into a dark essence block: 2.5 Runecraft xp and 1 prayer point each (allowed at 0). A chisel on dark essence blocks gives 4 fragments (obj 7938) and 8 Crafting xp per block (Crafting 38). Kourend's blood (loc 27978, level 77, 23.8 xp) and soul (loc 27980, level 90, 29.7 xp) altars bind every fragment into a rune each. | Dense runestone, Dark Altar, Dark essence block / fragments, Blood / Soul Altar (Kourend) |
| RC18 | **Entering the Abyss**: the Mage of Zamorak (multi-npc 3228 on varp 492, op 4 "Teleport", spawned at (3107, 3555)) needs Enter the Abyss finished (varp 492 = 4); Tele Block does not matter. All Prayer points drain to 0 and the player is skulled unless an abyssal bracelet is worn (the skull goes 1,000 ticks after leaving through a rift). A configuration 0-11 is picked at random into cache varbit 625 (`rcu_abyssal_generator`) and the player lands outside the opening that is the blockage. | Abyss, Mage of Zamorak, Skull (status) |
| RC19 | **Outer ring**: twelve 3x2 multi-locs (`rcu_outer_multi1..12`, clockwise from the south) show, per configuration, blockage, rock (Mine, Mining + pickaxe), tendrils (Chop, Woodcutting + axe), boil (Burn-down, Firemaking + tinderbox), eyes (Distract, Thieving), gap (Squeeze-through, Agility) or the passage (Go-through, guaranteed). In configuration c the blockage is opening c and the passage opening c + 6; the table (`RunecraftAbyssContent.ROWS`) is the cache's. An attempt succeeds with (level + 1)% and gives 25 xp; the player moves to the other side of the opening. | Abyss, cache locs 26187-26192, 26208, 26250-26253, 26574 |
| RC20 | **Inner ring**: the 11 rifts `abyss_exit_to_<altar>` (op 1 "Exit-through") take the player into the temple without a talisman (cosmic needs Lost City, law Troll Stronghold, death Mourning's End Part II; the blood rift, parent 43848, needs Sins of the Father). Dark essence (block or fragments) used on the blood or soul rift, after crafting runes at Kourend's altar, redirects it (message from the Wiki); the blood rift's op 2 and the soul rift then go to Kourend's altar. | Abyss, Blood Altar (Kourend), Soul Altar (Kourend) |
| RC21 | **Astral altar** (loc 34771, Lunar Isle): needs Lunar Diplomacy (cache varbit 2448 = 190); astral runes from pure essence, level 40, 8.7 xp, a second rune per essence at level 82. | Astral Altar, Runecraft |
| RC22 | **Guardians of the Rift visitors.** A player who entered an altar from a portal guardian (`GuardiansOfTheRift`) imbues guardian essence and uncharged cells there instead of crafting normally, and the exit portal returns them to the portal guardian (hooks `GotrAltars.imbue` / `returnFromAltar` in `craft` / `leave`). | guardians-of-the-rift.md GR5-GR6 |
| RC23 | **Sunfire runes** (Varlamore): pure essence (9 xp) or daeyalt essence (13.5 xp), one fire rune and one sunfire splinter (obj 28924) per essence, used on a Shrine of Ralos (loc 52405, whose only cache op is "Bask"); level 33, 2x runes per essence at 49 and 3x at 98 (the level before the action); `min(essence, fire runes, splinters)` are bound in one action, pure first. Bonuses to the output do not raise the input cost. `RunecraftSunfire.kt`. Sources fetched 2026-11-07. | "Sunfire rune", "Sunfire splinters", "Shrine of Ralos", "Runecraft" (Multiple runes) |

### OPEN (this block)

- **OPEN-RC9** xp "Low / High" assignment per altar (above). **OPEN-RC10** combination runes get no level multiples (the Wiki's multiples table only lists single runes).
- **OPEN-RC11** Magic Imbue: the Wiki speed is 0 ticks (we use 1); the cast animation `human_cast_selfimbue` is a guess, no graphic is known. The spell is in
  `content/magic/MagicImbue.kt`; `magic-utility.md` OPEN-MU21 still lists it as missing.
- **OPEN-RC12** the values of the `*_essence_pouch_type` varbits (we use 1 rune, 2 pure, 3 daeyalt, 4 guardian), which essence an empty pouch takes first
  (pure), and every pouch / refusal text ("The pouch contains N pure essence.", "Your pouch is full.", ...): not on the Wiki. The Dark Mage's "Talk-to"
  (replacement small pouch and abyssal book) and Astral Contact are not implemented.
- **OPEN-RC13** colossal capacity between level bands: the Wiki says it "linearly scales downward starting from 27 for levels 75-84, ..."; we use the
  band's starting value. The colossal pouch is capped by the lower of its level capacity and its wear stage. The abyssal lantern (redwood) is not read.
- **OPEN-RC14** Apprentice Cordelia's repairs (25 abyssal pearls to unlock) and dropping a pouch on death (contents vanish) are not implemented.
- **OPEN-RC16** the crack / ladder landing tiles are the nearest walkable tiles beside the loc (`RcTiles.beside`); no source gives them. Eniola takes the
  first stack of 20 non-combination runes in the backpack: his payment-selection interface and quick-pay setting are not implemented, nor are runes from a
  rune pouch. Raiments of the Eye (+60% runes) are not read.
- **OPEN-RC17** the dense runestone never depletes (the Wiki says each player has an own instance; plugin-api has no per-player loc state); the mining
  animation is the bronze pickaxe's for all tools; the Arceuus lectern tablets and the 108-fragment cap are not implemented.
- **OPEN-RC18** the tiles inside and outside each opening are derived from the loc's placement and rotation (checked headless: the arrival tiles were
  walkable and a passage crossing moved the player 2-3 tiles toward the middle); the Mage of Zamorak's wander range (4) is ours. The monsters of the outer
  ring (abyssal leech, guardian, walker; npc 2584-2586), the Abyssal Nexus gap, the Abyss' multicombat flag, the Mage's dialogue and shop, and the Abyss'
  monsters are not implemented; the Enter the Abyss miniquest (quest-enter-the-abyss.md) sets varp 492 = 4 for the teleport to work. The Abyss skull uses the shared
  `SKULL_ICON` attribute; the PvP skull timer and ours do not know about each other.
- **OPEN-RC19 (resolved, remaining work OPEN-GR1..GR20)** Guardians of the Rift is `RunecraftGotr*.kt` / `GotrRewards.kt` (guardians-of-the-rift.md), Temple of the Eye and Enter the Abyss are in `content/quests` (quest-temple-of-the-eye.md, quest-enter-the-abyss.md).
- **OPEN-RC20** the ruins' "Enter" is still polled every 3 ticks (OPEN-RC4); plugin-api now has an `Equipped` event that could replace it.
- **OPEN-RC21** sunfire runes: the Wiki does not say how the shrine is used, and the cache loc has no crafting op; we use pure / daeyalt essence, fire runes or splinters used on the shrine (loc 52405 only, not the Moon version 52413). Twisted extracts (Desert Treasure II, level 60, up to 2-3 extracts per essence), Raiments of the Eye (+60%) and the abyssal lantern are not implemented. Messages are ours.
- **OPEN-RC22** Astral Contact (Lunar spell) to have the Dark Mage repair pouches from afar needs the NPC Contact interface (magic-utility.md OPEN-MU21), which is not in content/skills; the repair logic (`RunecraftPouches.repairAll`, private) would be reused.
- **Multiple runes table audit (2026-11-07)**: every row of the Wiki table (air, mind, water, earth, fire, body, cosmic, sunfire, chaos, astral, nature, law, death) matches `RunecraftData` / `RunecraftSunfire` / `ASTRAL_MULTIPLES`. The Wiki table has no blood, soul or wrath row (OPEN-RC2).
