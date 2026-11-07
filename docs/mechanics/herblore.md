# Herblore

`content/skills/.../Herblore.kt`; every recipe is a row of `recipes.tsv` (OSRS Wiki `{{Recipe}}` templates,
`tools/wiki-import/recipes.py`, DECISIONS D-040). Shared rules (make-X menu, production batches, xp, messages): skilling.md
SK, MU, PR. Levels, xp and ticks are the Wiki's `skill1lvl` / `skill1exp` / `ticks`.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-06:
- (H) "Herblore" (cleaning and unfinished potion table; Changes: 10 September 2020 "You may now clean all your herbs automatically
  after cleaning the first one in your Inventory")
- (R) the `{{Recipe}}` template of each herb page ("Guam leaf", "Ranarr weed", ...), each unfinished potion page ("Guam potion (unf)", ...)
  and each potion page ("Attack potion", "Super restore", "Stamina potion", "Anti-venom", "Divine super attack potion", ... the full
  list is `ANY_OUTPUT_PAGES` in the importer)
- (P) "Pestle and mortar" ("If used to auto-crush items, it will do so every three game ticks"; Changes: 14 August 2014 "Grinding items
  with a pestle and mortar now automatically begins to grind all of that item in your inventory") and the Recipe notes of "Unicorn
  horn dust", "Chocolate dust", "Dragon scale dust", "Crushed nest", "Kebbit teeth dust", "Goat horn dust" ("Auto-crushing ... is 0, then
  2, then 3 ticks")
- (L) the cache (2757): iop "Clean" on grimy herbs; seqs `human_herbing_vial` 363 and `human_herbing_grind` 364

## Rules

| # | Rule | Source |
|---|---|---|
| HB1 | **Cleaning.** The backpack op "Clean" on a grimy herb (checked against the slot, MU4-style) needs the herb's level ("You need a Herblore level of N to clean this herb."), turns that herb into the clean one in place, sends "You clean the dirt from the <herb>." (filterable) and gives the xp, all on the tick of the click. Level / xp: guam 3 / 2.5, marrentill 5 / 3.8, tarromin 11 / 5, harralander 20 / 6.3, ranarr 25 / 7.5, toadflax 30 / 8, irit 40 / 8.8, avantoe 48 / 10, kwuarm 54 / 11.3, snapdragon 59 / 11.8, cadantine 65 / 12.5, lantadyme 67 / 13.1, dwarf weed 70 / 13.8, torstol 75 / 15. A click interrupts like any inventory action (interactions.md I7). | H, R, L |
| HB2 | **Auto-clean.** After the first herb, the other grimy herbs of the same kind in the backpack are cleaned one per `CLEAN_TICKS` (1) as a weak-queue batch (a click ends it; a level-up ends it). The count is the number held at the click. The 1 tick is an assumption (OPEN-HB2). | H |
| HB3 | **Pestle and mortar.** Using the pestle and mortar on a unicorn horn, chocolate bar, blue dragon scale, kebbit teeth, goat horn or (empty) bird nest crushes every one in the backpack, no level and no xp: the first at once, the second 2 ticks later, then one every 3 ticks, with animation 364. A click ends the rest. The batch is a weak queue (PR2). | P |
| HB4 | **Unfinished potions.** A clean herb used on a vial of water (either way round) opens the make-X menu (type 22 "Make", one product, quantity 1..28). Each action takes 1 tick (Recipe `ticks`), animation 363, no xp: level guam 3, marrentill 5, tarromin 12, harralander 22, ranarr 30, toadflax 34, irit 45, avantoe 50, kwuarm 55, snapdragon 63, cadantine 66, lantadyme 69, dwarf weed 72, torstol 78. | H, R |
| HB5 | **Finished potions.** An unfinished potion used on its secondary opens the menu; each action takes 2 ticks (Recipe `ticks`; forgotten brews and ancient brews are not modelled), animation 363, and removes one unfinished potion and the secondary (the quantity the Wiki gives: 1 for ordinary potions, N for the (N)-dose scale and shard recipes such as extended antifire, stamina, anti-venom and divine potions) and adds the (3)-dose potion (the Wiki names the product with its dose). The first potion lands 2 ticks after the click (PR1). Examples (level / xp): attack 3 / 25, antipoison 5 / 37.5, strength 12 / 50, restore 22 / 62.5, energy 26 / 67.5, defence 30 / 75, agility 34 / 80, combat 36 / 84, prayer 38 / 87.5, super attack 45 / 100, superantipoison 48 / 106.3, fishing 50 / 112.5, super energy 52 / 117.5, hunter 53 / 120, super strength 55 / 125, weapon poison 60 / 137.5, super restore 63 / 142.5, super defence 66 / 150, antifire 69 / 157.5, ranging 72 / 162.5, magic 76 / 172.5, zamorak brew 78 / 175, saradomin brew 81 / 180, stamina 77, extended antifire 84, anti-venom 87, divine potions 70-86. | R |
| HB6 | **Each action** re-checks level and both materials (the menu is only a request, MU4), then removes the materials and adds the product in one item transaction, then adds xp. A level-up ends the batch (PR3). | PR |
| HB7 | **Which clicks open a menu.** Any two backpack items that are the two materials of a recipe, used on each other either way round (HB8 adds recipes of three or four materials). | R |
| HB8 | **Extra recipes (2026-11-07, `EXTRA_PAGES` in the importer; block `# extra` of `recipes.tsv`).** Recipes of three or four materials start from any two of their materials used on each other and the menu lists the ones the player holds everything for: **super combat potion** (90 / 150 xp, 2 ticks: super attack(4) + super strength(4) + super defence(4) + torstol or torstol potion (unf)), **Sanfew serum** (65; one step at a time: super restore(n) + unicorn horn dust gives mixture step 1 for 28.5 / 38 / 47.5 / 57 xp, + snake weed step 2 for 31.5 / 42 / 52.5 / 63, + nail beast nails the serum for 36 / 48 / 60 / 72), **Guthix rest** (18 / 59: cup of hot water + 2 guam leaf + harralander + marrentill), compost potion (22 / 60: harralander potion (unf) + volcanic ash), Relicym's balm (8 / 40: snakeweed mixture + rogue's purse), ancient brew (85 / 190: dwarf weed potion (unf) + nihil dust), forgotten brew (91, 36 xp a dose, 20 ancient essence a dose, 3 ticks), **divine super combat potion** (97, 0.5 xp a dose), and **tar** (pestle and mortar in the backpack, 15 swamp tar + a herb: guam 19 / 30, marrentill 31 / 42.5, tarromin 39 / 55, harralander 44 / 72.5; 15 tar, 3 ticks). | R |

## Not modelled / OPEN

- **OPEN-HB1:** decanting (Herblore NPCs, the Bob Barter / Decanting shop) and combining doses by using two potions together are not
  implemented.
- **OPEN-HB2:** the timing of auto-clean (HB2) is not sourced. Only the change note exists; one herb per tick is the most plausible
  reading and sits behind `CLEAN_TICKS`.
- **OPEN-HB3:** the make-X menu is assumed for unfinished and finished potions with the "Make" verb and no preselected quantity beyond
  the default of 28 (OPEN-PR3); the menu title is not sourced. The barbarian mixes' "make-all" setting (Wiki "Herblore" Changes) is not
  modelled.
- **OPEN-HB4:** messages are unsourced except those named in the rules: no "You mix the ..." line is sent for potions; the level message
  of HB1 and "You clean the dirt from ..." are the most plausible wording.
- **OPEN-HB5:** not implemented: recipes with three or more materials (coconut-milk antidote+ and antidote++ unfinished potions, super
  combat potion, Zamorak/other multi-step recipes), Mastering Mixology, swamp tar, barbarian mixes, Sanfew serum and its mixtures,
  Guthix balance, compost potion, relicym's balm, forgotten and ancient brews, huasca, the black dart, the herblore cape / Desert diary
  NPC services. Quest, diary and unlock requirements of the implemented recipes are not checked (members worlds only; no free-to-play
  restriction is applied).
- **OPEN-HB6:** the pestle's first-crush timing ("0, then 2, then 3") is read as: first at the click, second 2 ticks later, then every 3.
  The animation is played with every crush as in PR1; the Wiki's remark that it "will occasionally skip a tick" is not modelled.
- **OPEN-HB7:** HB5's "not implemented" list is partly done (HB8: super combat, Sanfew serum steps, Guthix rest, compost potion, Relicym's balm,
  ancient / forgotten brew, divine super combat, tar). Still open: Serum 207 (the Wiki recipe gives no ticks), Guthix balance (its
  unfinished potions are numbered "(unf) (1)" on the Wiki, one obj per dose in the cache; no match), the cup of hot water (it has no
  Recipe; Guthix rest also needs it, so the recipe is only usable with a cup obtained elsewhere), the herb tea mixes, the all-in-one Sanfew
  and Relicym's lines (0 ticks), coconut-milk antidotes, barbarian mixes (they are `BarbarianTraining.kt`, quest-gated), the Zamorak mix and
  the others of that family, Mastering Mixology (a minigame with its own stations, resin, levers and rewards: not feasible in this pass),
  decanting, amulet of chemistry, Zahur and the degrime items. Sanfew serum is "done one step at a time, no make-x menu" on the Wiki; here
  it uses the ordinary menu. Herb cleaning needs no change (HB1; grimy huasca is not in the Wiki's cleaning table read).
- **PROPOSAL (content/combat Consumables.kt, not edited here):** drinkable effects for the new potions, from the Wiki potion pages. Sanfew
  serum: restores 4 + 30% of the base level (rounded down) per dose in every skill except Hitpoints, 6 minutes of poison immunity, 15 minutes of
  disease immunity. Guthix rest: per dose heals 5 Hitpoints (may overheal to +5 at full health), restores 5% run energy, reduces venom to
  poison or poison damage by 1. Compost potion and Relicym's balm: no combat effect (disease cure; Farming). Ancient brew: Magic +2 + 5% of base,
  Prayer +2 + 10% of base (up to 5% over base), Attack, Strength and Defence drained by 2 + 10% of their current levels. Forgotten brew: Magic
  +3 + 8% of base, the same Prayer restore and the same drain. Divine super combat potion: Attack, Strength and Defence +5 + 15% for 5 minutes
  with natural drain disabled. (Super combat potion itself is already in the table.)
