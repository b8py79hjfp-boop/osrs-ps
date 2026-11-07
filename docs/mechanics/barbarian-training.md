# Barbarian Training

Code: `content/skills/.../BarbarianTraining.kt` (plugin: Otto's spawn, bed, barbarian anvil, mixes), `BarbarianOtto.kt` (Otto's
dialogue), `BarbarianProgress.kt` (the state), `BarbarianData.kt` (mix and spear tables). Read by `Fishing.kt` (barbarian spots) and
`Firemaking.kt` (bow).

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-07:
- (BT) "Barbarian Training", https://oldschool.runescape.wiki/w/Barbarian_Training (requirements, sections, mix and spear tables)
- (TR) "Transcript:Barbarian Training" (Otto's lines per state), (O) "Otto Godblessed" (id 2914, map pin 2502,3489)
- (A) "Barbarian anvil" (loc 25349, message), (B) "Barbarian bed" (loc 25268), (AM) "Attack mix", (P) "Funeral pyre (Barbarian)"
- (C) the cache: varbits `brut_*` 3757 to 3768 (2 bits each, varp 1023) and 9613 `brut_miniquest` (6 bits, varp 4086); locs
  `brut_barbarian_bed` 25268 and `brut_anvil` 25349; npc `brut_otto` 2914; items `brut_fishing_rod` 11323, `brut_roe` 11324,
  `brut_caviar` 11326, the barbarian mixes 11429 to 11522, 11960, 12633, 21994, 22221, 26350 and the hastae 11367 to 11377.

## Rules

| # | Rule | Source |
|---|---|---|
| BT1 | State is kept in the cache varbits the client reads, one step per section: `brut_miniquest` 1 = started; `brut_fishing_r` 0 / 1 taught (take the rod) / 2 caught a leaping fish / 3 Otto accepted; `brut_fire` 0 / 1 taught / 2 oak logs lit with a bow / 3 Otto accepted; `brut_herb_potion` 0 / 1 taught / 2 attack mix handed in; `brut_smith_spear` 0 / 1 taught / 2 spear made; `brut_smith_hasta` 0 / 1 taught (needs the spear made) / 2 hasta made. | BT, C; values OPEN-BT1 |
| BT2 | Otto stands at 2502,3489 (wander 3). The first talk offers "You think so?" which starts the miniquest; the menu then has one option per section for the step the player is on (fishing rod, firemaking, spears / hastae, herblore) and "I'd better get going." Every start needs no skill level (the requirements are not needed to start). Lines are the Wiki transcript's. | TR, O, BT |
| BT3 | Barbarian bed ("Search"): gives a barbarian rod each time once Otto has taught the rod. Barbarian fishing spots (Fishing, FS12) are refused until `brut_fishing_r` >= 1. | B, BT |
| BT4 | Firemaking: a bow (Firemaking.kt FM7) lights logs for players with `brut_fire` >= 1 and 35 base Firemaking; the first oak logs lit with a bow set `brut_fire` to 2. Otto then accepts ("Fine news indeed!") and, on asking what follows, explains pyre ships. | BT, TR |
| BT5 | Barbarian anvil (a bar or its logs or a hammer used on it, or "Smith"): needs `brut_smith_spear` >= 1, a hammer, one bar and its logs (bronze + logs, iron + oak, steel + willow, mithril + maple, adamant + yew, rune + magic). It makes a spear (Smithing 5 / 20 / 35 / 55 / 75 / 90, 25 / 50 / 75 / 100 / 125 / 150 xp), or a hasta of the same level and xp once Otto has explained them (a menu asks which). The first spear sets `brut_smith_spear` 2, the first hasta `brut_smith_hasta` 2. Without the lesson: "This anvil is unfamiliar and barbaric, you can't seem to use it." | BT, A |
| BT6 | Barbarian mixes: a roe (Attack to Restore mixes) or caviar (all) used on the two-dose potion makes the two-dose mix for Herblore xp (attack 4 / 8 to extended super antifire 99 / 78, 29 rows in `BarbarianData.MIXES`), one per use, 1 tick. Otto must have asked for the attack mix (`brut_herb_potion` >= 1). Handing the attack mix (2) to Otto sets `brut_herb_potion` 2. Herblore needs the rod lesson accepted (`brut_fishing_r` 3). | BT, AM |

## OPEN

- **OPEN-BT1:** the values of the `brut_*` varbits are this server's own steps. The client's miniquest journal script that reads them is not
  decoded, so the journal and quest-list entries may show another progress. The varbit meanings by name (`fishing_s` barehanded,
  `fishing_r` rod, `fire`, `tracker`, `herb_potion`, `smith_spear`, `smith_hasta`, `craft_ship`) match the Wiki sections. The miniquest
  varbit `brut_miniquest` is only set to 1; its completion value is not known and completion is never set (Farming and the pyre ship are missing).
- **OPEN-BT2:** not implemented, with the Wiki rules read: barehanded fishing (harpoon fish without a harpoon, level 55 Fishing / 35
  Strength, `brut_fishing_s`; sequences `brut_player_hand_fishing_*` exist), Barbarian Farming (barehanded planting, pot smashing,
  `brut_farming_*`), Otto's notebook ("My notes"), his hasta repairs and Zamorakian spear / hasta conversion (300,000 or, with the Elite
  Kandarin diary, 150,000 coins), the spirits talk, the pot-smashing toggle, and the Wiki's requirement list to start (none is needed).
- **OPEN-BT3:** pyre ships (`brut_craft_ship`): the pyre sites (locs 25263 to 25293 near 2503,3498 and 2506,3518), chewed and mangled bones from
  the Ancient Cavern's mithril dragons, the peaceful and ferocious barbarian spirits, the reward table of "Chewed bones" and the prayer
  bone-burying bonus ("Table/Pyre ships" is a template that could not be fetched). The Ancient Cavern is not entered either (whirlpool,
  mithril dragons). Without a pyre ship the miniquest cannot complete here. The `ancient page` notes are also missing.
- **OPEN-BT4:** the wording of the refusals is not on the Wiki pages read: the barbarian spots ("You need to learn barbarian fishing from
  Otto Godblessed before you can fish here."), the bed without the lesson ("Nothing interesting happens."), the bed's find message, the
  anvil's missing material / level lines, Herblore's level line. The Tai Bwo Wannai Trio gate for spears is behind
  `BarbarianOtto.ENFORCE_TBWT` (off, like OPEN-FS11): the quest is not implemented, and the Wiki also asks for the combat xp claimed from Tamayu.
- **OPEN-BT5:** barbarian mixes can be made once Otto has asked for the attack mix; the Wiki says "after finishing this training stage" but the
  attack mix itself must be made before it can be handed in. Druidic Ritual (needed for Herblore) is not checked. "Make-all" for mixes
  (varbit `settings_barbarian_potion_makex`) is not implemented: one mix per use. **Drinking a mix** (the potion's effect plus 3 or 6
  Hitpoints per dose, with the food attack delay) belongs to the consumables table of content/combat and is not there yet, so the mixes cannot be drunk.
- **OPEN-BT6:** the barbarian anvil's cadence is not published; it uses the normal anvil's 5 ticks and the smithing animation `brut_human_smithing`.
  Wielding a hasta needs the smithing lesson (equipment requirements are outside this plugin), and Dragon Slayer II / the Kandarin diaries'
  checks of the miniquest read the same varbits but are not wired. The bow's own firemaking animations are not played.
- **OPEN-BT7:** Otto is spawned by this plugin (`npc-spawns.tsv` has no row for him); if the spawn table gains one, remove the spawn here.
