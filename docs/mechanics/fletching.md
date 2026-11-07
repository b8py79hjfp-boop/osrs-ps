# Fletching

`content/skills/.../Fletching.kt`, `FletchingData.kt`; every recipe is a row of `recipes.tsv` (OSRS Wiki `{{Recipe}}` templates,
`tools/wiki-import/recipes.py`, DECISIONS D-040). Shared rules (make-X menu, production batches, xp, messages): skilling.md
SK, MU, PR. Levels, xp and quantities are the Wiki's `skill1lvl` / `skill1exp` / `matNquantity` / `output1quantity`.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-06:
- (R) the `{{Recipe}}` templates of each product page: "Arrow shaft", "Javelin shaft", "Headless arrow", "Shortbow (u)", "Oak longbow",
  "Wooden stock", "Bronze crossbow (u)", "Bronze crossbow", "Bronze arrow", "Iron dart", "Bronze bolts", "Opal bolt tips",
  "Opal bolts", "Opal dragon bolts", "Iron javelin", ... (the full list is `ANY_OUTPUT_PAGES` in the importer)
- (T) "Pay-to-play Fletching training": "[bows] 3-ticks per bow ... stringing bows takes 2-ticks per bow", "darts or bolts which give
  experience every two clicks and can be made quickly", "actions are done every two game ticks (1.2 seconds) and in sets of 10 or
  15 ... These methods open a dialogue window", dart section: "the default setting is to use the crafting menu since the Easter 2023
  update", "two-click dart fletching" is an account setting
- (F) "Fletching" (Changes: 5 April 2023 "Players can now toggle a 'Make-x' interface for fletching darts"; 1 August 2019
  "Animations have been added for when a player tips arrows, bolts and darts")
- (L) the cache (2757): seqs `human_fletching` 1248, `human_fletching_add_*`, `xbows_fletching_*`, `xbows_stringing_*`, `stringing_*`;
  cache enum 1809 menu verbs ("Make", "String", "Make sets:")

## Rules

| # | Rule | Source |
|---|---|---|
| FL1 | Every recipe starts by using one backpack item on another (`onUseOnItem`, either way round). The pair is the recipe's two materials, or its tool and its only material: knife on logs, chisel on a gem. All recipes whose pair is the same share one menu. | R |
| FL2 | **Make-X menu** (skilling.md MU, PR): knife on logs lists the products of that wood (arrow shafts, javelin shafts, shortbow (u), longbow (u), wooden stock for logs; oak, willow, maple, yew, magic: shafts, the two bows, the stock; teak and mahogany: the stock; redwood: shafts); stringing a bow or crossbow uses the "String" verb (type 10); sets of arrows, javelins and tipped bolts use "Make sets:" (type 2); everything else "Make" (type 22). The menu lists only products the player holds the materials for. The picked quantity is the number of actions (1..28), each making `output1quantity` items. | R, T, L |
| FL3 | **Darts and bolts are instant.** Recipes whose Wiki `ticks` is 0 (darts: tip + feather; bolts: unfinished bolt + feather) make one full set of 10 per use, with no menu, on the tick of the click: animation, the set, xp. The "Make-X for darts" account setting is not modelled (OPEN-FL1). | R, T, F |
| FL4 | **Ticks.** Bows (u) 3 ticks each (T); arrow shafts and stocks "Make-X is 2, then 3": the first at 2 ticks, then every 3 (R); stringing bows and crossbows 2 (R, T; the Bronze crossbow template says "1, then 2" and is followed); crossbows (u) 2; sets of arrows, javelins and tipped bolts 2 (T; the javelin templates say 1 and 2 by tier and are overridden by T); bolt tips cut with a chisel 5 (R). The first action is queued `first` ticks after the click (PR1). | R, T |
| FL5 | **Products** (level / xp): arrow shafts (logs 1 / 5 for 15; oak 15 / 10 for 30; willow 30 / 15 for 45; maple 45 / 20 for 60; yew 60 / 25 for 75; magic 75 / 30 for 90; redwood 90 / 35 for 105); javelin shafts (3 / 5, 15); shortbow (u) 5 / 5, longbow (u) 10 / 10; oak 20 / 16.5 and 25 / 25; willow 35 / 33.3 and 40 / 41.5; maple 50 / 50 and 55 / 58.3; yew 65 / 67.5 and 70 / 75; magic 80 / 83.3 and 85 / 91.5; strung bows level and xp as their unstrung bow (Wiki rounds 33.2 / 58.2 for some); stocks (wooden 9 / 6, oak 24 / 16, willow 39 / 22, teak 46 / 27, maple 54 / 32, mahogany 61 / 41, yew 69 / 50, magic 78 / 70). | R |
| FL6 | **Crossbows.** Stock + limbs gives the crossbow (u): bronze 9 / 12, blurite 24 / 32, iron 39 / 44, steel 46 / 54, mithril 54 / 64, adamant 61 / 82, runite 69 / 100, dragon 78 / 135 (the Wiki lists a hammer as a tool: it must be in the backpack). A crossbow string on it gives the crossbow: 9 / 6, 24 / 16, 39 / 22, 46 / 27, 54 / 32, 61 / 41, 69 / 50, 78 / 70. | R |
| FL7 | **Arrows and javelins.** 15 shafts + 15 feathers (any of the 8 feather kinds the Wiki lists) give 15 headless arrows (1 / 15 xp). 15 headless arrows + 15 arrowtips give 15 arrows: bronze 1 / 19.5, iron 15 / 37.5, steel 30 / 75, mithril 45 / 112.5, adamant 60 / 150, rune 75 / 187.5, amethyst 82 / 202.5, dragon 90 / 225. 15 javelin heads + 15 javelin shafts give 15 javelins: bronze 3 / 15, iron 17 / 30, steel 32 / 75, mithril 47 / 120, adamant 62 / 150, rune 77 / 187.5, amethyst 84 / 202.5, dragon 92 / 225. | R |
| FL8 | **Darts** (10 tips + 10 feathers): bronze 10 / 18, iron 22 / 38, steel 37 / 75, mithril 52 / 112, adamant 67 / 150, rune 81 / 188, amethyst 90 / 210, dragon 95 / 250. **Bolts** (10 unfinished + 10 feathers): bronze 9 / 5, blurite 24 / 10, iron 39 / 15, silver 43 / 25, steel 46 / 35, mithril 54 / 50, adamant 61 / 70, runite 69 / 100, dragon 84 / 120. | R |
| FL9 | **Gem bolts.** A chisel on a cut gem gives bolt tips (opal 11 / 1.5, jade 26 / 2, pearl 41 / 3.2, topaz 48 / 3.9, sapphire 56 / 4, emerald 58 / 5.5, ruby 63 / 6.3, diamond 65 / 7, dragonstone 71 / 8.2 for 12 tips; onyx 73 / 9.4 for 24; an oyster pearl gives 6 and oyster pearls 24). 10 tips on 10 bolts (opal on bronze, jade on blurite, pearl on iron, topaz on steel, sapphire and emerald on mithril, ruby and diamond on adamant, dragonstone and onyx on runite) give gem bolts at the tip's level and 16 / 24 / 32 / 39 / 47 / 55 / 63 / 70 / 82 / 94 xp; the same tips on dragon bolts give the dragon versions at level 84 with the xp of the tip. | R |
| FL10 | **Each action** re-checks level, tool and materials (the menu is only a request, MU4), then removes the materials and adds the product in one item transaction, then adds xp. A missing level, tool or material ends the batch with the message of `Production.shortfall`. A level-up ends the batch (PR3). If the backpack cannot take the product the batch ends silently. | PR |
| FL11 | **Animations** (cache seqs): knife 1248; headless arrow `add_feather` 8481; arrowtips `add_arrow_tips` 8480; darts and bolts with feathers per metal (`human_fletching_add_dart_feathers_*` 8482-8488 and 9108, `add_bolt_feathers_*` 8463-8471); tipped bolts per plain bolt metal (`add_bolt_tips_*` 8472-8479); crossbows (u) and crossbow stringing per metal (`xbows_fletching_*` 4436-4442 and 7860, `xbows_stringing_*` 6671-6677 and 7961); strung bows per wood and kind (`stringing_*` 6678-6689). The names say what each is for; no source gives the pairing with the Wiki recipes beyond the names. | L |
| FL12 | **Shields and staves (2026-11-07).** Knife on two logs: oak shield 27 / 50 xp, willow 42 / 83, maple 57 / 116.5, yew 72 / 150, magic 87 / 183, redwood 92 / 216 (7 ticks, the Wiki Recipe `ticks`; "Fletching wooden shields grants the same experience as a full longbow of the same level"); they join the knife-on-logs menu. Knife on celastrus bark (obj 22935): battlestaff, level 40, 80 xp, 4 ticks. Knife on redwood logs: redwood hiking staff, level 90, 10.5 xp, 3 ticks. | T (Wiki "Wooden Shields", "Celastrus bark", "Redwood hiking staff") |

## Not modelled / OPEN

- **OPEN-FL1:** the account setting that turns the dart make-X menu on (T, F) is not modelled; darts and bolts are always the two-click
  instant set. The menu would be type 2 "Make sets:" with up to 10 sets.
- **OPEN-FL2:** no success or failure messages are sent (none are sourced). The menu titles ("What would you like to make?", "...
  string?", "How many sets would you like to make?") are unsourced.
- **OPEN-FL3:** no animation for javelins (no cache seq is named for them), for cutting bolt tips with a chisel (`human_fletching_huntingbolts_chisel`
  11097 exists but its use is unsourced), nor for the black dart (no recipe: its Wiki page has no `{{Recipe}}`).
- **OPEN-FL4:** the fletching knife (2 ticks per bow instead of 3, T) and the jeweller's chisel (faster bolt tips) are not modelled; only the
  plain knife and chisel work.
- **OPEN-FL5:** not implemented: broad arrows and broad bolts (Slayer unlock), ogre arrows and other quest items, bone and other
  special tips, battlestaves (Crafting, CR9), unfinished bolts, arrowtips and limbs (Smithing), the log basket
  and bow string spool.
- **OPEN-FL6:** the Wiki recipe for crossbow (u) lists a hammer; if the real game needs none (this was not checked), remove the tool column from those rows.
- **OPEN-FL7:** quest, diary and unlock requirements of some recipes are not in the `{{Recipe}}` data and are not checked (members worlds only; no
  free-to-play restriction is applied).
- **OPEN-FL8:** the shield rows' 7 ticks are the Recipe `ticks` of the shield pages (bows take 3); the celastrus battlestaff's "4-tick cycle"
  trick and the fletching knife (31043) speed-up are not modelled. Amethyst bolt tips and broad bolts / arrows (Slayer unlock) stay missing for
  Fletching: bolt tips are Crafting (CR10) and the Wiki lists no unlock-free "amethyst bolts" Fletching row; the Slayer unlock state does not exist.
