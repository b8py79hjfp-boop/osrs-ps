# Smithing

`content/skills/.../Smithing.kt`, `SmithingData.kt`; bar recipes in `recipes.tsv` (OSRS Wiki, `tools/wiki-import/recipes.py`,
DECISIONS D-038). Anvil products, bars and levels come from the cache. Shared rules (success roll, messages, the make-X menu and
production batches): skilling.md SK, MU, PR.

## Smelting (furnace)

- **SM1: how it starts.** Clicking a furnace ("Smelt", op 2 of the cache locs named "Furnace") or using an ore on it opens the
  make-X menu (skilling.md MU, type "Smelt") with every bar the server smelts, lowest level first: bronze, iron, silver, steel,
  gold, mithril, adamantite, runite. Metal bars are chosen, not ores: the player picks the bar and a quantity. Locs: every cache
  loc named "Furnace" with the "Smelt" op except the Blast Furnace one (op "Coffer").
- **SM2: tick timeline.** One bar per 5 ticks. With a quantity above 1 the first bar lands 4 ticks after the choice, a single
  bar 6 ticks after it (Wiki "Bronze bar", Recipe ticks note: "Make-X is 4, then 5. A single bar is 6."). The animation
  (`human_furnace`, seq 899) plays at the choice and again before every bar.
  **OPEN-SM1:** the Wiki's iron, silver, steel, mithril, adamantite and runite pages list 4 ticks without that note; the Wiki's
  own "Free-to-play Smithing training" says "5 ticks (3 seconds) it takes to smelt a bar in a furnace", so every bar uses the
  bronze figures.
- **SM3: recipes** (level and xp from the Wiki `{{Recipe}}` of each bar page; the Wiki lists xp rounded to tenths: 6.2, 13.7):

  | Bar | Level | Ores | xp |
  |---|---|---|---|
  | bronze | 1 | 1 copper, 1 tin | 6.2 |
  | iron | 15 | 1 iron | 12.5 |
  | silver | 20 | 1 silver | 13.7 |
  | steel | 30 | 1 iron, 2 coal | 17.5 |
  | gold | 40 | 1 gold | 22.5 |
  | mithril | 50 | 1 mithril, 4 coal | 30 |
  | adamantite | 70 | 1 adamantite, 6 coal | 37.5 |
  | runite | 85 | 1 runite, 8 coal | 50 |

  Every other bar's ores are used up, the bar is added and "You retrieve a bar of X." is sent (RuneLite `SmeltingPlugin`
  matches that text for all bars). The ores and the bar are one item transaction.
- **SM4: iron.** Iron ore succeeds 1 time in 2 whatever the level; a failure uses up the ore and gives nothing, no xp
  (Wiki "Smelting", "Success rate"). The text is "The ore is too impure and you fail to refine it." (OPEN-SM5). Wearing a ring
  of forging makes it always succeed.
- **SM5: ring of forging.** 140 charges per ring; each successful iron bar made while it is worn uses one; on the last the ring is
  removed from the worn items and "Your Ring of Forging has melted." is sent, and the batch ends; the next ring starts with 140
  again (Wiki "Ring of forging"; RuneLite `ItemChargePlugin`: `RING_OF_FORGING_USED_TEXT` "You retrieve a bar of iron.",
  `RING_OF_FORGING_BREAK_TEXT`, 140). The charges are per player, not per ring: they are kept as the content value
  `smithing.forging` (smelts left; absent = 140). The "Check" and "Break" ops of the ring are not implemented (OPEN-SM6).
- **SM6: goldsmith gauntlets.** Worn gloves "Gauntlets of goldsmithing" give 56.2 xp for a gold bar instead of 22.5 (Wiki
  "Smelting": "22.5 (56.2)"; the Wiki's "Goldsmith gauntlets" page notes the boost is added to the base xp in game, 22.5 + 22.5 x 1.5,
  and that a Nov 2025 Sailing update touched the Superheat Item path; the furnace figure on the Smelting page is used).

## Anvil

- **SM7: how it starts.** Clicking an anvil ("Smith", op 1), using a hammer (or imcando hammer) on it, or using a bar on it
  opens the smithing interface (312, `MAINMODAL`) for a bar type. Without a hammer: "You need a hammer to work the metal with.".
  The metal is the bar used on the anvil, else the highest tier the player carries (OPEN-SM3). Locs: cache locs named "Anvil"
  with the op "Smith" (the Sangvesti and PvP-arena anvils are left out).
- **SM8: the interface is driven by a varbit.** Varbit `smithing_bar_type` (3216) is 1 bronze, 2 iron, 3 steel, 4 mithril,
  5 adamant, 6 rune (cache enum 1253 maps it to the bar obj; value 7 is a members alloy this server does not smith). The
  interface's own client script 430 (set up by script 415, re-run on varp changes) shows, per component, the product obj,
  the bars it needs (cache enum 845), its level (enum 846) and the "1 bar" / "N bars" text, greying what the player cannot make.
  Which product is on which component per metal is script 430's table, copied into `SmithingData.ANVIL_BARS` (the Sailing
  "Keel parts" and "Chain" entries are left out). Claws show once varp 314 >= 80, dart tips once varp 197 >= 16 (script 430's
  conditions; the server checks the same varps).
- **SM9: choosing.** Clicking a product (op 1, "Smith") re-validates level, bars and hammer (messages "You need a Smithing level
  of N to make X." / "You don't have enough Xs to make Y.") and opens the make-X menu (type "Smith") with that one product for
  the quantity (OPEN-SM2). Interface 312 has a quantity row (`makex`: 1, 5, 10, X, All) that no client script drives; it is
  hidden and the menu takes its place.
- **SM10: tick timeline and xp.** One item per 5 ticks, the first 5 ticks after the choice (Wiki "Smithing": "a time interval of 5
  ticks (roughly 3 seconds) per item created, regardless of the items being created or how many bars are being used"; the first
  offset is OPEN-PR1). Animation `human_smithing` (seq 898), message "You hammer the bronze and make a bronze dagger."
  (OPEN-SM5). The bars are removed and the product(s) added in one transaction. xp = bars x 12.5 x tier (bronze 1, iron 2, steel
  3, mithril 4, adamant 5, rune 6): Wiki "Smithing": "Smithing experience is calculated by taking the experience granted from 1
  bar and multiplying it by the number of bars used ... This works for all bars and all smithing items, with a few exceptions such
  as cannonballs." Items per smith: cache enum 844 (nails 15, dart tips 10, arrowtips 15, knives 5, bolts 10, javelin tips 5,
  everything else 1).
- **SM11: products** (bars needed and bronze levels from cache enums 845 / 846; level 0 there means 1; iron starts at level 15, steel 30,
  mithril 50, adamant 70, rune 85, each item at its bronze level plus that offset: iron dagger 15, rune dagger 85):
  dagger 1 bar (level 1), axe 1 (1), mace 1 (2), medium helm 1 (3), bolts 1 (3), dart tips 1 (4), sword 1 (4), wire 1 (4),
  arrowtips 1 (5), scimitar 2 (5), longsword 2 (6), limbs 1 (6), full helm 2 (7), knives 1 (7), square shield 2 (8), warhammer 3
  (9), battleaxe 3 (10), chainbody 3 (11), kiteshield 3 (12), claws 2 (13), 2h sword 3 (14), platelegs 3 (16), plateskirt 3 (16),
  platebody 5 (18), nails 1 (4). The cache holds the complete table (`config enum 845` / `846`).

## OPEN

- **OPEN-SM1:** smelting ticks per bar (SM2).
- **OPEN-SM2:** how the live anvil takes a quantity. The cache shows the quantity row on interface 312 but no client script
  uses it; the make-X menu reuses the shared, verified `skillmulti` flow instead of guessing the row's server protocol.
- **OPEN-SM3:** which metal opens when the anvil is clicked while carrying several bar types (highest tier is used).
- **OPEN-SM4:** Varrock armour (chance of 2 bars at the Edgeville furnace) and the Smiths' uniform (20% per piece to take 1 tick
  off anvil smithing) are not modelled; neither are the Blast Furnace, cannonballs, blurite / elemental / lovakite / lead /
  cupronickel bars, Superheat Item, the dragon-tier smithing and the Sailing items.
- **OPEN-SM5:** message texts "The ore is too impure and you fail to refine it." and "You hammer the X and make Y." are from
  memory of the live game; only "You retrieve a bar of X." and the ring messages are sourced (RuneLite).
- **OPEN-SM6:** ring of forging Check / Break ops; charges are a saved content value and are not shown by the item.
- **OPEN-SM7:** a smelt or smith is not stopped when the player is attacked (OPEN-PR2).
- **OPEN-SM8:** the furnace and anvil loc id lists are the cache locs named "Furnace" / "Anvil" with the matching op; quest- or
  minigame-specific furnaces and anvils with other ops are not included.
