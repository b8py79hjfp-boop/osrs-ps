# Cooking

`content/skills/.../Cooking.kt`, data in `CookingData.kt`. Shared rules (success roll, action clocks, filterable messages): skilling.md.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-06:
- (C) "Cooking", https://oldschool.runescape.wiki/w/Cooking (food levels and xp; "cook all" default; baked goods need a range)
- (B) each cooked food's Wiki page, its "Skilling success chart" (low / high per heat source and gauntlets), e.g.
  https://oldschool.runescape.wiki/w/Shrimps, /w/Shark; collected by `tools/wiki-import/cooking_rates.py`
- (G) "Cooking gauntlets", (K) "Cooking cape", (N) "Burnt food" (burnt item per food), (S) "Skilling success rate"
- (P) "Pay-to-play Cooking training": "Cooking most foods takes four game ticks"; (M) "Tick manipulation": 2-tick cooking
- (L) the cache (cache 2757): loc ops "Cook", anims `human_cooking` 896 (range) and `human_firecooking` 897 (fire)

## Rules

| # | Rule | Source |
|---|---|---|
| CK1 | Raw food used on a fire or range, or the "Cook" op of a range. With **one** raw item of that kind it is cooked at once on arrival. With **more than one** the make-X menu (skilling.md MU, type 6 "Cook", the raw food as the product) opens and the chosen quantity (1, 5, 10, X, All; 1..28) is cooked: the first when the quantity is chosen, the next every 4 ticks (the player's action clock, SK3). The single item is an op script that keeps its interaction, so moving away ends it; a menu choice is a production batch (skilling.md PR2), so any click ends it. A level-up popup also ends the batch (PR2). | C (menu, "All" default, skipped for one item), P, M |
| CK2 | Food level, xp, cooked and burnt items: see the table. Too low: "You need a Cooking level of N to cook this." Baked goods (bread, pitta, pies, pizza, cake) need a range; on a fire: "You can't cook that on a fire." | C, B, N |
| CK3 | Success is the skilling roll (SK1, current level) with the food's `low` / `high` for the heat source; a failure turns the item into its burnt item and gives no xp. | B, S |
| CK4 | Heat and modifiers: the chart's "Fire" or "Range" line ("Fire or Range" for both). Worn cooking gauntlets use the "Gauntlets" line (lobster, swordfish, monkfish, shark, anglerfish) on either heat source. The Cooking cape (untrimmed or trimmed) never burns anything. | B, G, K |
| CK5 | Animation 896 on a range, 897 on a fire, played with each item. Messages (filterable): "You successfully cook the <food>." ("bake" for baked goods) or "You accidentally burn the <food>." | L; burn text from N (Wiki "Burnt shrimp"), success text OPEN-CK4 |
| CK6 | Which locs: item-on-loc works on the cache locs listed in `RANGE_LOCS` (loc type with op "Cook": Range, Cooking range, Stove, Oven, Clay oven) and `FIRE_LOCS` (cache "Fire" locs showing the ordinary flame, including the fires players light); the "Cook" op is resolved by name. A loc named "Fire" is a fire, everything else a range. | L |

### More rules (CK9 to CK14)

| # | Rule | Source |
|---|---|---|
| CK9 | **Karambwan.** Raw karambwan cooks into the cooked karambwan from level 30 (190 xp), below that into the poison karambwan (level 1, 80 xp); both roll 70 / 255 on a fire or range, a failure is a burnt karambwan. Cooking it thoroughly really needs Tai Bwo Wannai Trio and Tinsay's lesson; neither exists, so only the level decides (OPEN-CK9). | Wiki "Raw karambwan", "Cooked karambwan", "Poison karambwan" |
| CK10 | **1-tick karambwan.** Opening a new cooking menu and choosing on the tick a karambwan is cooked cooks again at once. Modelled as: a make-X choice for a food with `ignoresClock` (karambwan) cooks its first item on the tick it is chosen even while the cooking clock runs; the rest follow every 4 ticks. The engine rule behind it is not sourced (OPEN-CK10). | Wiki "Tick manipulation" > 1-tick Cooking |
| CK11 | **Lumbridge range.** Loc 114 within 2 tiles of (3212, 3215) needs Cook's Assistant (varp 29 >= 2) and uses the charts' "Lumbridge range" roll for the 15 foods that have one (meat, chicken, shrimps, anchovies, sardine, herring, mackerel, trout, cod, pike, salmon, bread, redberry pie, meat pie, stew); other foods use the ordinary range roll. Gauntlets take precedence where both apply (none of those foods has a gauntlets line). | Wiki "Cooking range (Lumbridge Castle)", skilling charts |
| CK12 | **Hosidius Kitchen.** The clay ovens (loc 21302 at the pins (1677, 3622) and (1680, 3622)) need the easy Kourend and Kebos Diary (varbit `KOUREND_DIARY_EASY_COMPLETE`) and add 12 (easy) or 25 (elite, varbit `KOUREND_DIARY_ELITE_COMPLETE`) to both `low` and `high` of the range or gauntlets roll, "an additive 5% [or 10%] increased chance". The 12 and 25 are read off the charts for every food (e.g. tuna 58 / 372 -> 70 / 384 -> 83 / 397; lobster with gauntlets 55 / 368 -> 67 / 380). | Wiki "Hosidius Kitchen", "Cooking range", skilling charts |
| CK13 | **Preparation steps.** `cooking-prep.tsv` holds the item-on-item steps (36 rows, `tools/wiki-import/cooking_prep.py`): pot of flour with bucket, bowl or jug of water makes bread dough, pastry dough, pitta dough (58) or pizza base (35) and gives the pot and the container back; pastry dough with a pie dish makes a pie shell; shell with redberries (10), cooked meat or chicken (20) or a cooking apple (30) makes the uncooked pie; pizza base with tomato then cheese makes the uncooked pizza (35); plain pizza with meat (45, 26 xp), anchovies (55, 39) or pineapple (65, 45) the topped pizzas; tin, egg, flour and milk the uncooked cake (40); cake with chocolate (50, 30 xp); bowl of water with meat or potato then the other makes the uncooked stew (25); spice or 3 curry leaves with the stew the uncooked curry (60). Using one ingredient on another opens the make-X menu; a choice runs as a batch of 2-tick steps (the first after `first_ticks`). The cooked stew (117 xp) and curry (280 xp) are range foods in `CookingData.kt`. | Wiki product pages' `{{Recipe}}`, "Incomplete stew" |
| CK14 | **Wine.** Grapes with a jug of water make unfermented wine (35 Cooking, 0 xp, 3 ticks then 2). 20 ticks after the latest one is made (the timer restarts with every new one) all unfermented wine in the backpack ferments: the count is split into 15 batches plus the remainder one by one, each with one skilling roll 48 / 352 (about 60% at level 35, certain from 68); a success is a jug of wine and 200 xp, a failure a jug of bad wine. Unfermented wine held at login starts a timer. | Wiki "Jug of wine" (Mod Ash 7 and 8 August 2021) |

### CK3: the burn roll

Every food's `low` / `high` per heat source is in `CookingData.kt`, one line per food with its Wiki page. Example: shrimps
128 / 512 on both (certain from level 34), shark 1 / 202 on a fire, 1 / 232 on a range, 15 / 270 with gauntlets.

### Foods (level / xp / stop-burn fire, range, gauntlets)

| Food | Lvl | Xp | Fire | Range | Gauntlets |
|---|---|---|---|---|---|
| Raw beef, raw chicken, shrimps, anchovies | 1 | 30 | 34 | 34 | |
| Sardine | 1 | 40 | 38 | 38 | |
| Herring / mackerel | 5 / 10 | 50 / 60 | 41 / 45 | same | |
| Trout | 15 | 70 | 49 | 49 | |
| Cod | 18 | 75 | 51 | 49 | |
| Pike | 20 | 80 | 54 | 54 | |
| Salmon | 25 | 90 | 58 | 58 | |
| Tuna | 30 | 100 | 63 | 63 | |
| Cave eel | 38 | 115 | 74 | 74 | |
| Lobster | 40 | 120 | 74 | 74 | 64 |
| Bass | 43 | 130 | 79 | 79 | |
| Swordfish | 45 | 140 | 86 | 80 | 80 |
| Monkfish | 62 | 150 | 92 | 90 | 86 |
| Shark | 80 | 210 | never | never | 94 |
| Sea turtle | 82 | 211.3 | never | never | |
| Anglerfish | 84 | 230 | never | never | 97 |
| Dark crab | 90 | 215 | never | never | |
| Manta ray | 91 | 216.3 | never | never | |
| Bread dough / pitta dough | 1 / 58 | 40 | range only | 38 / 38 | |
| Redberry / meat / apple pie | 10 / 20 / 30 | 78 / 110 / 130 | range only | 45 / 54 / 63 | |
| Plain pizza | 35 | 143 | range only | 68 | |
| Cake | 40 | 180 | range only | 74 | |

Burnt items: burnt meat / chicken / shrimp, the five "burnt fish" (anchovies 323, trout family 343, herring family 357,
tuna and bass 367, sardine 369: ids from the Wiki item pages), lobster, swordfish, shark, manta, sea turtle, monkfish,
dark crab, anglerfish, cave eel, bread, pitta, pie, pizza and cake.

### Tick timeline

- Tick T (arrived at the loc, nothing cooked in the last 4 ticks; or, with the menu, the tick the quantity is chosen): first item
  cooked: roll, animation, item replaced in its slot, xp.
- T+4, T+8, ...: the next item (menu: weak-queue scripts, PR1; checked at T in `Cooking.kt`: first item at T, second at T+4). The action clock belongs to the player: clicking again within the 4 ticks waits for it.
- The slot the click used is cooked last: a use-on script only runs while that slot still holds the clicked item (interactions.md I10).

## OPEN

- **OPEN-CK2:** the Hosidius Kitchen and the Lumbridge Castle range are applied (CK11, CK12). Not applied: Cooking cape effects of the max
  cape and other items that include it, cooking-gauntlet foods outside the five listed (cooked kyatt), and the boosts of invisible
  levels (SK OPEN-SK2). The Lumbridge range is found by loc id and tile box, the Hosidius ovens likewise (the Wiki pins, not tile-exact);
  the Wiki's "Hosidius +10%" chart lines for other foods agree with +25.
- **OPEN-CK9:** thorough karambwan cooking is not gated by Tai Bwo Wannai Trio and Tinsay (neither implemented; varp 320). Level 30
  decides between the cooked and the poison karambwan (the Wiki's Feb 2024 default is the cooked one). The poison karambwan has no
  burn line on its page; it uses the cooked one's 70 / 255 as its chart does.
- **OPEN-CK10:** the exact rule behind the 1-tick karambwan trick is not on the Wiki (only the observed result). `Food.ignoresClock`
  models it; no other food has it.
- **OPEN-CK11:** unfermented wine in the bank also ferments in the game (Wiki "Jug of wine"); only the backpack is handled. Messages of the
  preparation steps and of wine are not sourced.
- **OPEN-CK12:** preparation steps play no animation (none on the Wiki), the stew's "Make-X is 1, then 3, then 4 ticks" and the baked
  goods' 1 tick on a single item are not modelled; steps with chopped onions, mushrooms, tuna, sweetcorn, baked potatoes (knife or
  range) and the dairy churn (butter, cheese) are not in `cooking-prep.tsv`.
- **OPEN-CK3 (closed):** the make-X menu is implemented (CK1) over the shared `SkillMulti` helper (D-038, D-040). Sourced by the Wiki
  "Cooking" page: with more than one food of the same kind "a picture of the food item will appear in the dialogue box", ranges
  left-clicked bring it up, "All" is the default, and "the cooking interface is skipped when only one raw food item is in the
  inventory" ("2-tick cooking"). Not sourced: the menu type (6, not the equivalent 7) and the title: OPEN-CK8. The 1-tick
  karambwan trick is modelled in CK10.
- **OPEN-CK8:** the menu's title ("How many would you like to cook?"), its type (cache enum 1809 has "Cook" at 6, 7 and 8) and whether
  the picture is the raw food (used here) are not sourced. A quantity chosen while the player's cooking clock still runs (they
  cooked within the last 4 ticks) waits for the clock.
- **OPEN-CK4:** message wording is not sourced for: success ("You successfully cook the shrimps." follows RuneLite's
  chat matching, not a Wiki page), nothing to cook ("You have nothing to cook."), a baked good on a fire ("You can't cook
  that on a fire.") and the level requirement ("You need a Cooking level of N to cook this.").
- **OPEN-CK5:** not implemented: foods that need an iron spit (rabbit, beast and bird meat, chompy), the rest of the Wiki's other
  foods (scrambled egg, fried onions and mushrooms, snails), infernal and sacred eels (the infernal eel is eaten as it is, the sacred
  eel dissected, see fishing.md OPEN-FS16), sailing and Chambers of Xeric fish, raw potatoes and the mud / garden / fish / botanical /
  mushroom / admiral / dragonfruit / wild / summer pies (their raw forms need other skills' ingredients), tea, Camdozaal and gnome food.
  Raw karambwan, slimy eel, stew and curry were added (CK9, CK13). The Wiki's cooked sea turtle, manta ray, dark crab and anglerfish
  were already in the table; their fishing is Fishing Trawler or fishing.md FS13.
- **OPEN-CK6:** burnt meat and chicken cooked a second time (cooked item on a fire) are not handled.
- **OPEN-CK7:** a modal (such as the level-up popup) pauses an interaction until it is closed (interactions.md I6).
