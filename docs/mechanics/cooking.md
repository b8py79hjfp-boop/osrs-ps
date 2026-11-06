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

- **OPEN-CK2:** not applied: Hosidius Kitchen (needs the Kourend and Kebos diary state: +5% / +10% on the roll), the
  Lumbridge Castle range (needs Cook's Assistant: the charts' "Lumbridge range" line) and Hosidius kitchen ("Hosidius +5%/+10%" lines), Cooking cape effects of the max cape and other
  items that include it, cooking-gauntlet foods outside the five listed (cooked kyatt), and the boosts of invisible levels (SK OPEN-SK2).
- **OPEN-CK3 (closed):** the make-X menu is implemented (CK1) over the shared `SkillMulti` helper (D-038, D-040). Sourced by the Wiki
  "Cooking" page: with more than one food of the same kind "a picture of the food item will appear in the dialogue box", ranges
  left-clicked bring it up, "All" is the default, and "the cooking interface is skipped when only one raw food item is in the
  inventory" ("2-tick cooking"). Not sourced: the menu type (6, not the equivalent 7) and the title: OPEN-CK8. The 1-tick
  karambwan trick is not modelled.
- **OPEN-CK8:** the menu's title ("How many would you like to cook?"), its type (cache enum 1809 has "Cook" at 6, 7 and 8) and whether
  the picture is the raw food (used here) are not sourced. A quantity chosen while the player's cooking clock still runs (they
  cooked within the last 4 ticks) waits for the clock.
- **OPEN-CK4:** message wording is not sourced for: success ("You successfully cook the shrimps." follows RuneLite's
  chat matching, not a Wiki page), nothing to cook ("You have nothing to cook."), a baked good on a fire ("You can't cook
  that on a fire.") and the level requirement ("You need a Cooking level of N to cook this.").
- **OPEN-CK5:** not implemented: raw karambwan (two outcomes, needs Tai Bwo Wannai Trio state), foods that need an iron spit
  (rabbit, beast and bird meat, chompy), stew, curry and the rest of the Wiki's other foods, snails, eels other than the cave
  eel, sailing and Chambers of Xeric fish, raw potatoes, pizza toppings and the mud / garden / fish / botanical /
  mushroom / admiral / dragonfruit / wild / summer pies, wine, tea, Camdozaal and gnome food.
- **OPEN-CK6:** burnt meat and chicken cooked a second time (cooked item on a fire) are not handled.
- **OPEN-CK7:** a modal (such as the level-up popup) pauses an interaction until it is closed (interactions.md I6).
