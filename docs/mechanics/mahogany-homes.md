# Mahogany Homes

Code: `content/skills/.../MahoganyHomes.kt` (contractors, clients, hotspots, rewards, supply crate), `MahoganyData.kt` (tiers, homes,
loaders), `PlankSack.kt` (plank sack), the tables `mahogany-layouts.tsv` and `mahogany-hotspots.tsv`; the layouts are written by
`tools/wiki-import/mahogany.py`. Shared skilling rules: skilling.md. House and furniture: construction.md.

Sources (fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-07):
- (M) "Mahogany Homes" (tiers, level requirements, experience per plank, completion experience, carpenter points, rewards, changes),
  https://oldschool.runescape.wiki/w/Mahogany_Homes
- (X) "Mahogany Homes/Mechanics" (the client varbits 10554-10561, every layout of every house and tier with its plank, steel bar and
  experience totals), https://oldschool.runescape.wiki/w/Mahogany_Homes/Mechanics
- (A) "Amy" (options, stock), "Carpenter's outfit", "Plank sack", "Supply crate (Mahogany Homes)", "Cup of tea"
- (C) the cache (cache 2757): npcs 6439 / 7417 Amy (7417 has ops 3 Contract, 4 Last-tier contract, 5 Rewards), 10408-10413 Marlo, Ellie,
  Angelo, 10414-10424 the clients; the hotspot multi-locs (a parent with `multiVarbit` 10554..10561 and nine children), varbits
  `con_contract_hotspot_1_display` .. `_8_` 10554-10561, `con_contract_discussed` 10562, `plank_sack_*` 18171-18177

## Rules implemented

| # | Rule | Source |
|---|---|---|
| MH1 | Four tiers: Beginner (Construction level 1, regular planks), Novice (20, oak), Adept (50, teak), Expert (70, mahogany). The level may be boosted. Amy gives the first contract; after that Marlo (Varrock), Ellie (East Ardougne) and Angelo (Hosidius) give contracts too. A contract cannot be cancelled. "Last-tier contract" repeats the previous tier. | M, A |
| MH2 | A contract is a home (Jess, Noella, Ross in East Ardougne; Larry, Norman, Tau in Falador; Barbara, Leela, Mariah in Hosidius; Bob, Jeff, Sarah in Varrock) chosen at random and one of its layouts for the tier chosen at random (138 layouts, 2 to 4 per home and tier). A layout is up to eight furniture slots, slot n being varbit 10554 + n. | M, X |
| MH3 | The slot states are the child index of the hotspot multi-loc (cache): 0 and 2 the finished or untouched piece, 1 broken, 3 the old piece ("Remove"), 4 the empty hotspot ("Build"), 5 / 6 / 7 / 8 the new piece in regular / oak / teak / mahogany. A layout starts with its pieces at 1 (repair) or 3 (replace). The saved contract mirrors the eight varbits on login. | C; OPEN-CO13 |
| MH4 | A click on a hotspot reports the multi-loc parent id (as farming patches do); the parent id and the clicked tile name the slot (`mahogany-hotspots.tsv`: 88 placements found by scanning the map for parents with a varbit 10554-10561, assigned to the house whose Wiki map polygon is nearest). Only the home of the contract answers; anything else says "I'd better leave this alone right now." (Wiki, Trivia). | C, M |
| MH5 | "Remove" (state 3) makes the hotspot (4). "Build" (4) takes the layout's plank count of the tier's plank (from the backpack, then the plank sack), needs a hammer and a saw (Amy's saw, the Imcando hammer and the crystal saw count), sets 5 + tier. "Repair" (1) takes one plank or one steel bar, needs a hammer, sets 0. Nails are not needed. | M, X; OPEN-CO12 |
| MH6 | Experience per action: build, planks x (22.5, 48, 72, 112); repair with a plank (127.5, 160, 190, 240); repair with a steel bar 120; removing gives none. Checked against every row of the Wiki table: the totals of 132 of 138 layouts agree with the Wiki's plank, steel bar and experience columns (the six that do not are Sarah's, see OPEN-CO13). | M, X |
| MH7 | The client is talked to once every slot is finished. Completion gives (500, 1250, 2250, 2750) xp and (2, 3, 4, 5) carpenter points, a cup of tea that restores run energy to 100%, raises the completed counter (capped at 30,000) and clears the contract. All Construction xp here is multiplied by the carpenter's outfit: 0.4% helmet, 0.8% shirt, 0.6% trousers, 0.2% boots, +0.5% for the set (the same multiplier applies to house building and flat-packs). Before completion the client lists what is still to do. | M, A |
| MH8 | Amy's reward stock, paid in carpenter points: supply crate 25, Amy's saw 500, plank sack 350 (one only), Hosidius blueprints 2,000, carpenter's helmet 400, shirt 800, trousers 600, boots 200. A supply crate opens ("Open", "Open-all") to noted oak plank, teak plank, mahogany plank, soft clay, steel bar, bolt of cloth and limestone brick, each with its own quantity rolled out of 144 (Wiki drop table; averages 6.01, 2.53, 0.77, 9.69, 5, 0.38, 0.38). | A |
| MH9 | The plank sack holds 28 planks of any type (counts kept as one saved value, mirrored to the seven client varbits). "Fill" moves the backpack's planks in, "Empty" moves them out, "Check" tells the contents, using a plank on the sack fills it. Planks bought at the sawmill go into the sack first (Wiki, 1 November 2023); Mahogany Homes uses them straight from the sack. | A |

Tick timelines:
- Contract: tick T the op on the contractor arrives: tier chosen (menu), home and layout rolled with the world RNG, saved values and
  the eight varbits set in the same tick; Amy tells the home.
- Build / repair: tick T the op arrives: level, tools and materials checked, animation starts; T + 5: checked again, materials
  taken in one item transaction, the varbit moves, xp added. Remove: T + 1.
- Hand-in: tick T the client's "Talk-to" with every slot finished: xp, points, run energy and the clean-up in one tick.

## Data

- `mahogany-layouts.tsv` (138 rows, no header): tier, home, region, layout number, eight slot cells `state:count:material:name`, planks,
  bars, Wiki xp. 2 to 4 layouts per tier and home as the Wiki gives them (Beginner Norman 2 and Tau 4; Bob and Jeff 2 in Expert; Sarah 2).
- `mahogany-hotspots.tsv` (88 rows): home, x, z, level, multi-loc parent id, varbit. The map also holds a copy of the Varrock houses at
  x 3939 z 4920-4960 (ids 39981-39996), left out.

## OPEN

- **OPEN-CO12: actions.** The Wiki gives no tick count, tool list per action, animation or message text for the minigame. Build and
  repair wait 5 ticks (a house build, 5 in the furniture table), remove 1; build needs hammer and saw, repair a hammer, remove nothing;
  the wall animation is used for wall and wall-decor hotspots (shape <= 8), the plain one otherwise (`HUMAN_POH_BUILD`); all dialogue
  and game messages are plausible, not sourced.
- **OPEN-CO13: data quirks.** (a) The Mechanics page says value 2 is "removed, needs to be built" and 3 "needs to be removed"; the cache
  children say 3 is the old piece (op "Remove"), 4 the empty hotspot (op "Build") and 2 the untouched piece, which is what is used.
  (b) Sarah's rows list her shelves as 3 planks but her experience totals and the other pages say 2; 2 is used, which leaves her plank
  total one lower than the Wiki's column for Beginner, Adept and Expert. (c) Norman's Beginner rows list drawers 3 / double bed 2,
  swapped against the contract pages; the numbers are used as the table gives them (its totals agree).
- **OPEN-CO14: npcs and shop.** Cache 2757 has client npcs 10414-10424 for eleven houses; Norman is mapped to the plain npc 3266
  "Norman". The reward shop is a chatbox menu (4 entries a page), not the cache's `construction_contract_shop` inventory 673 interface.
  Hosidius blueprints are given as an item; the wall kit they unlock does not exist. The Wiki's "hotspots in the houses are somehow
  linked" (the same varbit slot in another house) is not reproduced: only the contract's house answers.
- **OPEN-CO15: plank sack.** The bank interface's Fill / Empty on the sack, using planks straight from the sack for house building and
  for flat-packs, and servants delivering into it (they never do, Wiki) are not implemented; only Mahogany Homes uses the sack's planks.
