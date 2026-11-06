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
- **OPEN-RC3: other essence and bonuses.** Daeyalt essence (+50% xp), dark essence at the Zeah blood and soul altars, cores (mind,
  body, chaos), blood essence, runic extracts, Raiments of the Eye, the abyssal lantern, essence pouches and the colossal pouch are not
  implemented.
- **OPEN-RC4: "Enter" varbit polling.** No equipment-changed event exists in plugin-api (equipment lives in `content/combat`). A soft
  timer every 3 ticks compares the worn head and cape slots and rewrites the 13 varbits when one changed, so the client may show the
  "Enter" op up to 3 ticks late. An `EquipmentChanged` event would replace it.
- **OPEN-RC5: quests.** Cosmic (Lost City), law (Troll Stronghold, to craft), death (Mourning's End Part II), blood (Sins of the Father) and
  wrath (Dragon Slayer II) have quest requirements; there is no quest state, so none is enforced. Entrana's restrictions on law ruins
  and the Wilderness chaos ruin's skull are not modelled.
- **OPEN-RC6: texts.** "You bind the temple's power into <rune> runes.", "You feel a powerful force take hold of you..." and the
  failure lines ("You need a Runecraft level of N to craft X runes.", "You have no essence to bind.", "You need pure essence to bind X
  runes.") are not on the Wiki pages read.
- **OPEN-RC7: not implemented.** The Abyss, the Zeah blood and soul altars, the Ourania (ZMI) altar, Guardians of the Rift, combination
  runes (and the binding necklace), Magic Imbue, the talisman "Locate" op (the Wiki gives only example messages, "The talisman pulls to
  the North-East.", no distances), the Astral altar and the Tunnel of Chaos portal.
- **OPEN-RC8: duplicate ruins.** The cache also holds a second mind ruin at (2469,5433) and a second earth ruin at (4009,4945); they
  enter the same temple (handlers are by loc id) but leave at the Wiki's pin.
