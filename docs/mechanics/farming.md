# Farming

`content/skills/.../Farming.kt` (plugin: clicks and actions), `FarmingPatches.kt` (saved state, drawing), `FarmingGrowth.kt` (lazy
growth), `FarmingData.kt` (patch types, crops, constants), `FarmingSites.kt` (every farm), `FarmingTables.kt` (tsv readers),
`FarmingSaplings.kt`, `FarmingBins.kt`, `FarmingTools.kt`, `FarmingContracts.kt`; data in `farming-crops.tsv`, `farming-varbits.tsv`,
`farming-saplings.tsv`, `farming-payments.tsv`, `farming-contracts.tsv` and `farming-seedpacks.tsv` (importers in `tools/wiki-import/`).
Shared skilling rules: skilling.md. Sources, fetched as wikitext from oldschool.runescape.wiki on 2026-11-06 / 2026-11-07 unless noted:

- (F) "Farming" (https://oldschool.runescape.wiki/w/Farming): growth timing, harvest lives, tools, Farming Guild
- (D) "Disease (Farming)" / "Crop disease": disease rules, cures, the per-crop table
- (P) each crop's plant page `{{Farming info}}` (level, planting / harvest / check xp, growth time, seeds per patch) and
  `{{Farming yield calculator}}` (chance to save a harvest life), imported by `tools/wiki-import/farming_crops.py`
- (W) "Weeds", "Compost", "Supercompost", "Ultracompost", "Fruit tree", "Tree seed", "Oak tree (Farming)", "Limpwurt plant",
  "Tool Leprechaun", "Plant pot", "Seedling", "Sapling", "Seed dibber", "Spade", "Camphor tree", "Ironwood tree", "Rosewood tree"
- (S) "Seeds" and its `Allotment / Hops / Bush / Tree / Fruit tree / Special patches` sub-pages: the protection payments
  (`farming-payments.tsv`) and the flowers that protect allotments
- (B) "Compost bin": filling, rotting, upgrading, emptying
- (C2) "Farming contract" and "Seed pack": the 71 contract tasks, the roll counts and the three seed tables
  (`tools/wiki-import/farming_contracts.py`)
- (V) RuneLite `PatchImplementation.forVarbitValue`, `Produce` and `FarmingWorld` (timetracking plugin, BSD-2, adapted: D-044), imported
  by `tools/wiki-import/farming_varbits.py` (now all 20 patch layouts)
- (C) the cache (cache 2757): multi-loc parents and their `multiVarbit`, child names and ops (`cache-dump config loc`), seq frame
  lengths (`config seq`), placements (`find-locs`), gameval symbols (`Varbits.FARMING_TRANSMIT_A..N`, `Interfaces.FARMING_TOOLS`)

## Rules

| # | Rule | Source |
|---|---|---|
| F1 | **Patches are per player and drawn by varbit.** Each patch is a multi-loc parent (e.g. 8550 Falador north allotment) whose `multiVarbit` is one of the shared slots `FARMING_TRANSMIT_A..N` (4771-4775, 7904-7912); the value picks the child loc: weeds (0..3 = full..clear), seed stages, grown, diseased, dead, stump. A click reports the parent id, so the parent id names the patch; the server decides the action from the saved state, not from the child's op. A patch drawn from several locs (the Farming Guild redwood, a patch with a second copy on level 1) registers each id. Value layouts: `farming-varbits.tsv`; every crop's growing, diseased, dead, check and harvestable looks were checked to exist. | V, C |
| F2 | **Growth ticks are wall-clock.** A crop advances one stage at every multiple of its cycle of real time (epoch minutes): flowers, weeds, saplings 5; allotments, hops, seaweed, potato cactus 10; herbs, bushes 20; trees, mushrooms 40; cactus, belladonna, crystal tree 80; fruit trees, calquat, celastrus 160; spirit trees 320; hardwoods, redwood, anima plants, Hespori 640. A newly planted crop reaches stage 2 at the next multiple, which is usually sooner than a full cycle. Patches keep growing while the player is offline: the saved state carries the minute it was last brought up to, and every look at the patch simulates the ticks since (at most 64). Yew trees' first stage is 5 minutes. Number of stages per crop: `cycles` in `farming-crops.tsv`. | F (Growth timing), P |
| F3 | **Planting.** Seeds (allotment 3, hops 3-4, others 1) used on a raked patch of the right kind with a seed dibber; a sapling (tree, fruit tree, hardwood, calquat, spirit, celastrus, redwood, crystal) with a spade; needs the Farming level. Planting xp is given at once (rev 242: OPEN-FM1). Planting a sapling gives back the empty plant pot. A fresh patch holds full weeds. | P, F, W |
| F4 | **Disease.** At the end of a growth cycle a healthy crop in stage 1 .. `diseaseCycles` falls ill with chance n/128 (never in its first stage, never once grown, never when protected): n = base rounded up after compost cuts it to 1/2, 1/5, 1/10 (compost, supercompost, ultracompost); a live Iasor plant cuts it to a fifth; watered allotments, flowers and hops halve it (OPEN-FM3). Base: herbs 27, fruit trees 18, maple 13, magic 9; the rest OPEN-FM2 / OPEN-FA1. Poison ivy, the crystal tree, Hespori, anima plants and the Troll Stronghold, Weiss and Harmony herb patches never fall ill. A diseased crop stops; at the end of its next cycle, which uses the next slower tick (twice as long), it dies. Plant cure (allotment, flower, herb, hops, cactus, belladonna, mushroom, seaweed; leaves an empty vial) or secateurs (tree, fruit tree, spirit tree, bush, calquat, redwood; 3 in 4) put it back to growing; hardwoods and celastrus take either. Compost can be added in any stage before it is grown (18 / 26 / 36 xp, leaves an empty bucket) and sets the harvest lives where it counts (F6). Watering (cans with water, one dose each) lasts until the next growth tick. | D, W |
| F5 | **Weeds and clearing.** Raking (needs a rake) removes one weed level per pull for 4 xp and gives a weed each; weeds grow back one level every 5 minutes, also under compost. A dead crop, a stump, or a plant that has finished (bush, cactus, calquat, spirit tree, anima plant) is cleared with a spade (op "Clear") and leaves a raked patch (OPEN-FM5). A stump may take several tries (OPEN-FM7). | W, F, C |
| F6 | **Harvesting.** *Lives* (allotment, herb, hops, seaweed, celastrus, bush, cactus): a fully grown patch has 3 lives (bushes 4) + 1 / 2 / 3 for compost / supercompost / ultracompost where compost counts (allotment, herb, hops, seaweed, celastrus). Each pick gives one item and the harvest xp; then a roll keeps the life with chance (1 + floor(low x (99-L)/98 + high x (L-1)/98 + 0.5)) / 256 (low / high per crop: `cts_low`, `cts_high`; magic secateurs held or wielded raise both by 10%, not for seaweed; a live Attas plant by 5%), otherwise a life is lost; the patch is cleared when none are left; a bush or cactus keeps its plant and bears again after 4 / 3 ticks of 20 minutes (potato cactus 6 of 5). Allotments and hops are harvested with a spade in the backpack. *Single* (flower, belladonna, crystal tree): one harvest clears the patch; limpwurt gives 3 plus a bonus up to (level - 1) / 10 (OPEN-FM9), belladonna 6 plus a bonus up to 12 at 99 (OPEN-FA4), a crystal tree 28-32 shards. *Fruit* (fruit tree, calquat, mushroom): six items, one picked per action for the harvest xp; fruit trees regrow one every 40 minutes, calquat on its own cycle (OPEN-FA6), mushrooms not at all (the patch clears with the last one). | F (Variable crop yield), P |
| F7 | **Check-health and trees.** A grown tree, fruit tree, hardwood, redwood, calquat, spirit tree, celastrus, bush, cactus or crystal tree waits for "Check-health": big xp (`check_xp`), then it is choppable (tree, hardwood, redwood: the kind's level and axe rates, 1/8 chance to fall per log: stump, then a spade), bears fruit (fruit tree, calquat), is pickable (lives) or usable (spirit tree). With no fruit a fruit tree can be chopped (OPEN-FM6). Camphor, ironwood and rosewood use mahogany's axe rates (OPEN-FA2). | W, F, P |
| F8 | **Covered patches.** Every farm of the Wiki "Farming/Patch locations" that the cache has locs for: allotment + flower + herb + compost bin at Falador, Catherby, Ardougne, Port Phasmatys, Civitas illa Fortis, Hosidius, Prifddinas (crystal tree in place of the herb patch); allotment + herb at Harmony Island; herb at Troll Stronghold and Weiss; hops at Lumbridge, Seers' Village, Yanille, Entrana, Aldarin; bushes at the Champions' Guild, Rimmington, Ardougne, Etceteria; trees at Taverley, Falador, Varrock, Lumbridge, Gnome Stronghold, Auburnvale; fruit trees at Gnome Stronghold, Tree Gnome Village, Brimhaven, Catherby, Lletya, Kastori; spirit trees at Port Sarim, Etceteria, Brimhaven, Hosidius; calquat at Tai Bwo Wannai, Great Conch, Kastori; hardwood at Anglers' Retreat, Avium Savannah and the three Fossil Island patches; seaweed (2, underwater); cactus at Al Kharid; mushroom at Morytania; belladonna at Draynor Manor and Auburnvale; the Farming Guild (spirit tree, bush, 2 allotments, herb, cactus, tree, flower, redwood, fruit tree, celastrus, anima patch, giant compost bin) and the Hespori patch. While the player is within 52 tiles (half the client's 104-tile scene) of a farm's anchor, the farm's patch and bin states are written to the slots (every 3 ticks check, redraw once a minute); the nearest farm wins (OPEN-FM11). Not covered: grape vines, coral, quest patches (OPEN-FA6). | C, V |
| F9 | **Tool leprechaun.** A farming product (produce, grimy or clean herb, sapling other than spirit and crystal) used on a tool leprechaun (npc 0, 12109, 12765, 7757) is exchanged for its noted form, all of it at once; logs and untradeable items are not. | W |
| F10 | **Protection payments.** A farmer (the gardener NPCs of RuneLite's `FarmingWorld`; cache op 3 / op 4 "Pay (...)", or the payment item used on the farmer) takes the crop's price (`farming-payments.tsv`, noted allowed; one patch and one crop at a time) and the crop is then never diseased until it is cleared. Flower and herb patches, mushrooms, belladonna, the crystal tree, anima plants, Hespori and poison ivy cannot be protected. | S, D |
| F11 | **Flowers protect allotments.** A fully grown marigold (potato, onion, tomato), rosemary (cabbage), nasturtium (watermelon) or white lily (every crop) in the farm's flower patch stops its allotment crops falling ill (OPEN-FA14: judged when the allotment is looked at). | S |
| F12 | **Anima plants.** A live Attas plant in the Farming Guild's anima patch raises every harvest-life roll by 5%; a live Iasor plant cuts every disease chance to a fifth (never below 1/128). Kronos: OPEN-FA16. | F, D |
| FS1 | **Plant pots.** An empty plant pot used on an empty, weed-free patch with a gardening trowel in the backpack becomes a filled plant pot. | W (Plant pot) |
| FS2 | **Seedlings.** A tree seed (acorn, willow ... crystal acorn: `farming-saplings.tsv`) used on a filled plant pot with a trowel and the Farming level of the sapling makes the seedling. No xp is given (none is documented). | W (Seedling) |
| FS3 | **Watering.** A watering can with water used on a seedling uses one dose and makes the "(w)" seedling. | W (Seedling) |
| FS4 | **Growing up.** A watered seedling becomes a sapling at the next five-minute growth tick, also in the bank and while the player is offline (all watered seedlings in the backpack and bank grow at the first one's tick: OPEN-FA5). | W (Seedling), F |
| FB1 | **Compost bins.** A bin takes un-noted compostable items one at a time (the produce and herbs of the crops; weeds as normal compost; cactus spines, nightshade, logs, crystal shards no) until it holds 15 (30 in the giant bin), then "Close". It makes rotten tomatoes if every item was a tomato, supercompost if every item was supercompostable (crops needing Farming 47, except potato cactus and cotton boll; plus toadflax and giant seaweed), else compost. It cannot be opened for 35 to 70 minutes (the second 35-minute tick after closing: OPEN-FA12). "Open", then "Take" fills one empty bucket per action (rotten tomatoes need none); "Dump" empties the bin at any time but while rotting. An opened bin of compost takes one compost potion dose to become supercompost; an opened bin of supercompost takes 25 (50) volcanic ash to become ultracompost. | B |
| FB2 | **Bin drawing.** Bin values follow RuneLite's `COMPOST` / `BIG_COMPOST` layouts (filling by item count, rotting stage, ready, taking by bucket count). | V |
| FT1 | **Tool store.** "Exchange" (op 3) on a tool leprechaun opens interface 125 (side 126) and mirrors the counts into the `FARMING_TOOLS_*` varbits; "Deposit-all" (op 4) stores the backpack's tools. Limits: 100 rakes, spades, seed dibbers, secateurs, trowels; 1 watering can (any dose count); 1,000 plant cure, empty buckets, compost, supercompost, ultracompost. Noted items can be stored. | W, C |
| FT2 | **Varbit split.** A count above a varbit's width continues in the "extra" varbits as their high bits (rakes: `FARMING_TOOLS_RAKE` 1 bit + `FARMING_TOOLS_EXTRARAKES` 6 bits; buckets 5 + 3 + 2 bits ...). The widths add up to the Wiki limits for every tool, which is the evidence for the split (OPEN-FA13). | C |
| FG1 | **Farming Guild entrance.** "Open" on the guild's door (locs 34463 / 34464, `FarmingGuild.kt`) moves the player across it when their Farming level (boosts count) is 45 or more, else a message. | Wiki "Farming Guild" (https://oldschool.runescape.wiki/w/Farming_Guild), C |
| FC1 | **Contracts.** Guildmaster Jane (npc 8586 / 8587, "Contract" op 3) gives a task equally likely among the 71 tasks whose unlock level the player's Farming level (boosts count) has reached, not the same crop twice in a row. | C2 |
| FC2 | **Completion.** A task is done when the health of its crop in a Farming Guild patch is checked (crops with a health check), or when its patch is fully harvested (the others), whether the crop was planted before or after the task was given. | C2 |
| FC3 | **Reward and bookkeeping.** Jane hands out a seed pack of the task's tier and counts the contract. "Do you have anything easier?" swaps the task for one of a lower difficulty (easy ones cannot be swapped). A spirit seed, seedling or sapling is exchanged for a tier 5 pack. | C2 |
| FC4 | **Seed packs.** Taking from a pack rolls its tier's number of low / medium / high rewards (the medium and high counts random within the tier's range, the rest low), each roll a seed of that table by weight with a quantity in its range. | C2 |

## OPEN

- **OPEN-FM1.** Wiki "Farming" changes (3 June 2026, after rev 242): planting xp moves to the end of the harvest / check-health stage, dead crops
  give it when cleared, and growing plants can be dug up with a spade. Rev 242 gives the xp when planting and has no digging of
  growing plants; neither later change is implemented.
- **OPEN-FM2.** Disease chances of allotments, flowers, oak, willow and yew are unknown (Wiki: "unknown, except for the following crops").
  Allotments and flowers use the herbs' 27/128, oak / willow / yew maple's 13/128 (`FarmingConst.OPEN_*`). `diseaseCycles` is all stages
  but the first (the pattern of herbs, maple and magic), fruit trees 4 of 6 (Wiki).
- **OPEN-FM3.** How much watering lowers the chance is unpublished; halved (`FarmingConst.WATERED_DIVISOR`).
- **OPEN-FM4 (rest).** The per-player growth-tick offset (up to 31 minutes: Wiki "Growth timing") is 0; Gricoller's can, the bottomless compost
  bucket, scarecrows, Huasca (no Wiki `{{Farming info}}`), the Barbarian Training barehanded planting and pot destroying.
- **OPEN-FM5.** The state of a patch right after clearing: raked (stage 0) is assumed.
- **OPEN-FM6.** Fruit trees chopped down fall to one successful swing and give no logs or xp; farmed tree logs use the same success rates and
  xp as the regular tree of the kind; 1/8 depletion is stated for oak only and is used for all.
- **OPEN-FM7.** The stump-clearing success chance ("scales with Farming level, certain at 99"): level / 99 per try.
- **OPEN-FM8.** Action lengths come from the cache seq frame lengths (client ticks / 30, rounded): rake 2, plant 3, compost 5, water 5,
  cure 6, pick 2, dig 2, chop 4; the real tick counts are not published.
- **OPEN-FM9.** Limpwurt's bonus roots are uniform from 0 to the maximum; the Wiki gives only the maximum.
- **OPEN-FM10.** "Inspect" texts are generic, not the Wiki's per-model examines; "Guide" (op 4) is not handled.
- **OPEN-FM11.** Two farms closer than 104 tiles that share a slot (Catherby allotments and fruit tree, 50 tiles; Falador farm and Port Sarim
  spirit tree, 51) show the nearest one; the real game switches by map region, with special cases (RuneLite `FarmingWorld`).
- **OPEN-FM12.** The tool store's chat with the leprechaun is not imported (the interface is FT1).
- **OPEN-FA1.** Disease chances of the new crops: hops, bushes, cacti, mushrooms, belladonna, seaweed 27/128 (the herbs'); hardwoods,
  calquat, celastrus, redwood, spirit trees 13/128 (maple's). The Wiki publishes only fruit trees, maple, magic, herbs and coral (8/128).
  `diseaseCycles` is every stage but the first for all of them.
- **OPEN-FA2.** Camphor, ironwood and rosewood chop with Woodcutting levels 66 / 80 / 92 and xp 143.5 / 175 / 212.5 (Wiki, their tree
  pages); their axe success rates are not in `WoodcuttingData`, mahogany's are used.
- **OPEN-FA3.** The chance to save a harvest life of bushes, cacti, celastrus and the other crops without a `{{Farming yield calculator}}`
  is unpublished (the Wiki quotes only "75% to lose a life at level 55, 30% at 99" for cacti, which no pair of constants fits). The stand-in
  is `cts_low` 100, `cts_high` 180 (the Wiki: "CTS-high is the same across crop types"; the lows of allotments and hops are 82-140).
- **OPEN-FA4.** Belladonna: at least 6, at most 18 at level 99 (Wiki); the bonus between is uniform up to 12 x (level - 63) / 36. Gloves
  against its damage are not implemented.
- **OPEN-FA5.** Seedlings are not timed one by one: the first watered seedling starts a saved clock and every watered seedling in the
  backpack or bank becomes a sapling at the next five-minute tick after it. Seedlings elsewhere (ground, trade) do not grow.
- **OPEN-FA6.** Not covered: grape vines (Hosidius Vinery, 12 patches with their own varbits), coral frags (Great Conch), the quest patches
  (Unferth's, Kelda hops, Elder cadantine, Enriched snapdragon, Magic beans). Calquat regrowth (the Wiki says "Yes") uses its 160-minute
  cycle; RuneLite gives no regrowth tick for it.
- **OPEN-FA7.** Spirit tree travel (op 1 "Travel" of a grown spirit tree; no spirit tree network in `content/` yet) and the farmer's removal
  of a tree or fruit tree for 200 coins are not implemented.
- **OPEN-FA8.** The Hespori fight: the patch grows (3 x 640 minutes) and can be cleared with a spade but cannot be harvested; the boss,
  its 12,600 xp and seeds belong to combat content. No xp or loot is given for clearing.
- **OPEN-FA9.** Hops are harvested with a spade in the backpack like allotments (the in-game message; the Wiki does not say).
- **OPEN-FA10.** Contract eligibility reads the Wiki's "equal weighting out of all the entries for which you have the requirement" as one
  list of the three tables; the difficulty of "easier" is the table a task is listed in. The farmer-less dialogue texts are placeholders.
- **OPEN-FA11.** A seed pack carries no data; the tiers of the packs handed out are queued in a saved value and the oldest is rolled when
  a pack is opened (an unqueued pack counts as tier 3). "Take" and "Take-all" both give every seed at once (the rest to the floor); the
  per-seed take menu and "Check" are not implemented. Packs sold for Tithe Farm points would add the white lily seed (not implemented).
- **OPEN-FA12.** Compost bin timings: the rot ticks are 35 minutes (Wiki "35 to 70 minutes"; RuneLite's `Produce` has 40); an item goes in
  or a bucket comes out every 2 ticks; the value of a closed, rotted bin of rotten tomatoes in a regular bin is 160 (no layout row).
  Mixing warnings and the bottomless compost bucket are not implemented. Items other than farming produce (derived foods) are not
  compostable here.
- **OPEN-FA13.** Tool store interface: the cache has no client script decoder here, so the slot order is taken from the component names, the
  split of counts over varbits from their widths (FT2), the watering can varbit as doses + 1, the selected-quantity varbit (2 bits) as
  1 / 5 / X / All, and the item ops of the slots as op 1 = selected quantity, op 2 / 3 / 4 = 1 / 5 / 10, op 9 = all. Quantity X is not
  asked for. Magic secateurs are not stored separately.
- **OPEN-FA14.** Flowers protect allotments by the flower's state when the allotment is looked at (grown if it would have grown without
  disease), not tick by tick. The scarecrow (sweetcorn) is not implemented.
- **OPEN-FA15.** **Tithe Farm** is not implemented. It needs the minigame's instance and entry / exit (minigame teleport), the three seed
  tiers (Golovanova / Bologano / Logavano, up to 10,000 picked up) and Gricoller's fertiliser, per-plant watering deadlines (three waterings
  before minutes 1, 2 and 3 or the plant dies), the sacks (points per 3 fruit, bonus at the 75th and 100th), the 16,000 point cap, the
  "Farmer Gricoller's Rewards" shop interface (a new currency) and the tithe patch locs and per-fruit xp; none of these numbers is
  imported or checked against the cache yet.
- **OPEN-FA16.** The Kronos plant (a chance to skip a growth stage, shared by patches of that growth cycle): the chance is unpublished.
- **OPEN-FA17.** Diary effects: no disease at Hosidius (easy Kourend & Kebos), the Falador tree (elite Falador), the Civitas herb patch
  (16,000 glory), and the herb boosts (Kandarin +10 / +17 / +25, Kourend hard +10; Farming cape +5%); the Falador medium diary's +10% xp.
  The Troll Stronghold, Weiss and Harmony herb patches are disease-free without the unlock quests.
- **OPEN-FA18.** The Crystal tree's planting xp differs between its page (126) and the Wiki "Sapling" table (180); the page's is used. Seaweed
  patches need no diving gear here. Gardeners' and Jane's dialogue is not imported: only the functional messages exist.
- **OPEN-FA19.** Farming Guild tiers (Wiki "Farming Guild"): 45 Farming for the main area and east wing, 65 for the west wing and 85 for the
  north wing (a boost is enough to enter, and a drop below the level inside does not expel). Only the entrance door (FG1) has a loc; the
  Wiki says the inner doors "remain open with invisible barriers", which the cache has no clickable loc for. The 65 / 85 gates need a
  movement restriction by area (plugin-api has none: engine need) and are not implemented; the crop levels still gate planting (Hespori
  65, anima 76, spirit tree 83, celastrus 85, redwood 90 as in `farming-crops.tsv`). The refusal text of the door is not on the Wiki
  (placeholder), and the player is moved across the door instead of walking (it has no open model).
- **FA20 (closed).** The tool leprechaun's store asks for the bank PIN first (plugin-api `BankPinRequest`, bank-pin.md BP2); the Wiki's
  leprechaun dialogue lines carry `mtype = pin`.
- **OPEN-FA21.** Bottomless compost bucket (items 22994 empty / 22997 filled; Wiki "Bottomless compost bucket": holds up to 10,000 charges of
  one compost type, 2 charges per bucket of compost added, filled from a bin or buckets, used on patches, emptied by "Empty", stored by
  the leprechaun in its own slot). The charges and the compost type belong to the item, and plugin-api has no per-item data, so it is not
  implemented (a per-player stand-in would break trading and storage). Needs an item-instance charge API.
