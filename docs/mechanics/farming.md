# Farming

`content/skills/.../Farming.kt` (plugin), `FarmingGrowth.kt` (state and lazy growth), `FarmingData.kt` (patches, farms, tables);
data in `farming-crops.tsv` and `farming-varbits.tsv`. Shared skilling rules: skilling.md. Sources, fetched as wikitext from
oldschool.runescape.wiki on 2026-11-06 unless noted:

- (F) "Farming" (https://oldschool.runescape.wiki/w/Farming): growth timing, harvest lives, tools
- (D) "Crop disease": disease rules and the per-crop table
- (P) each crop's plant page `{{Farming info}}` (level, planting / harvest / check xp, growth time, seeds per patch) and
  `{{Farming yield calculator}}` (chance to save a harvest life), imported by `tools/wiki-import/farming_crops.py`
- (W) "Weeds", "Compost", "Supercompost", "Ultracompost", "Fruit tree", "Tree seed", "Oak tree (Farming)", "Limpwurt plant",
  "Tool Leprechaun"
- (V) RuneLite `PatchImplementation.forVarbitValue` (timetracking plugin, BSD-2, adapted: D-044), imported by
  `tools/wiki-import/farming_varbits.py`
- (C) the cache (cache 2757): multi-loc parents and their `multiVarbit`, child names and ops (`cache-dump config loc`), seq frame
  lengths (`config seq`), placements (`find-locs`), gameval symbols (`Varbits.FARMING_TRANSMIT_A..D`)

## Rules

| # | Rule | Source |
|---|---|---|
| F1 | **Patches are per player and drawn by varbit.** Each patch is a multi-loc parent (e.g. 8550 Falador north allotment) whose `multiVarbit` is one of four slots shared by every farm: `FARMING_TRANSMIT_A` 4771 (north allotment / tree / fruit tree), `_B` 4772 (south allotment / Gnome Stronghold fruit tree), `_C` 4773 (flower), `_D` 4774 (herb). The value picks the child loc: weeds (0..3 = full..clear), seed stages, grown, diseased, dead, stump. A click reports the parent id, so the parent id names the patch; the server decides the action from the saved state, not from the child's op. Value layouts: `farming-varbits.tsv`; every row was checked against the child names in the cache (diseased / dead rows name "Diseased" / "Dead"). | V, C |
| F2 | **Growth ticks are wall-clock.** A crop advances one stage at every multiple of its cycle of real time (epoch minutes): flowers and weeds 5, allotments 10, herbs 20, trees 40, fruit trees 160. A newly planted crop reaches stage 2 at the next multiple, which is usually sooner than a full cycle. Patches keep growing while the player is offline: the saved state carries the minute it was last brought up to, and every look at the patch simulates the ticks since (at most 64). Yew trees' first stage is 5 minutes. Number of stages per crop: `cycles` in `farming-crops.tsv`. | F (Growth timing), P |
| F3 | **Planting.** Seeds (allotment: 3, herb / flower: 1) or a sapling (tree / fruit tree: 1) used on a raked patch of the right kind; needs the Farming level, and a seed dibber (allotment, herb, flower) or a spade (tree, fruit tree). Planting xp is given at once (rev 242: OPEN-FM1). A fresh patch holds full weeds. | P, F |
| F4 | **Disease.** At the end of a growth cycle a healthy crop in stage 1 .. `diseaseCycles` falls ill with chance n/128 (never in its first stage, never once grown): n = base rounded up after compost cuts it to 1/2, 1/5, 1/10 (compost, supercompost, ultracompost); watered allotments and flowers halve it (OPEN-FM3). Base: herbs 27, fruit trees 18, maple 13, magic 9; the rest OPEN-FM2. A diseased crop stops; at the end of its next cycle, which uses the next slower tick (twice as long), it dies. Plant cure (allotment, flower, herb; leaves an empty vial) or secateurs (trees, 3 in 4) put it back to growing. Compost can be added in any stage before it is grown (18 / 26 / 36 xp, leaves an empty bucket) and sets the harvest lives. Watering (cans with water, one dose each) lasts until the next growth tick. | D, W |
| F5 | **Weeds and clearing.** Raking (needs a rake) removes one weed level per pull for 4 xp and gives a weed each; weeds grow back one level every 5 minutes, also under compost. A dead crop or a stump is cleared with a spade and leaves a raked patch (OPEN-FM5). A stump may take several tries (OPEN-FM7). | W, F |
| F6 | **Harvesting allotments, herbs, flowers.** A fully grown patch has 3 harvest lives + 1 / 2 / 3 for compost / supercompost / ultracompost. Each pick gives one item and the harvest xp; then a roll keeps the life with chance (1 + floor(low x (99-L)/98 + high x (L-1)/98 + 0.5)) / 256 (low / high per crop: `cts_low`, `cts_high`; magic secateurs held or wielded raise both by 10%), otherwise a life is lost; the patch is cleared when none are left. Allotments are harvested with a spade in the backpack. Flowers have one life; limpwurt gives 3 plus a bonus up to (level - 1) / 10 (OPEN-FM9). | F (Variable crop yield), P |
| F7 | **Trees.** A grown tree waits for "Check-health": big xp (`check_xp`), then it is choppable (the patch's tree kind, an axe, Woodcutting level) with 1/8 chance to fall per log (stump, then a spade). A grown fruit tree bears 6 fruit after the check, one picked per action for the harvest xp, a new fruit every 40 minutes; with no fruit it can be chopped (OPEN-FM6). Saplings are bought or found: growing them from acorns and seeds in plant pots is OPEN-FM4. | W, F, P |
| F8 | **Covered patches.** Allotment + flower + herb at Falador, Catherby, Ardougne and Port Phasmatys; trees at Taverley, Falador, Varrock, Lumbridge, Gnome Stronghold; fruit trees at Gnome Stronghold, Tree Gnome Village, Brimhaven, Catherby. While the player is within 52 tiles (half the client's 104-tile scene) of a farm's anchor, the farm's patch states are written to the slots (every 3 ticks check, redraw once a minute); the nearest farm wins (OPEN-FM11). | C, V |
| F9 | **Tool leprechaun.** A farming product (produce, grimy or clean herb) used on a tool leprechaun (npc 0, 12109) is exchanged for its noted form, all of it at once. | W |

## OPEN

- **OPEN-FM1.** Wiki "Farming" changes (3 June 2026, after rev 242): planting xp moves to the end of the harvest / check-health stage, dead crops
  give it when cleared, and growing plants can be dug up with a spade. Rev 242 gives the xp when planting and has no digging of
  growing plants; neither later change is implemented.
- **OPEN-FM2.** Disease chances of allotments, flowers, oak, willow and yew are unknown (Wiki: "unknown, except for the following crops").
  Allotments and flowers use the herbs' 27/128, oak / willow / yew maple's 13/128 (`FarmingConst.OPEN_*`). `diseaseCycles` is all stages
  but the first (the pattern of herbs, maple and magic), fruit trees 4 of 6 (Wiki).
- **OPEN-FM3.** How much watering lowers the chance is unpublished; halved (`FarmingConst.WATERED_DIVISOR`).
- **OPEN-FM4.** Plant pots, seedlings and the sapling stage of trees; watering trees; Gricoller's can; the compost bins (the compost
  buckets are used from the backpack); farmer payments that protect crops; flowers that protect allotments; scarecrows; the
  per-player growth-tick offset (up to 31 minutes: Wiki "Growth timing") is 0; hops, bush, cactus, mushroom, hardwood, spirit tree,
  calquat, celastrus, belladonna, grape, coral and seaweed patches; Hosidius, Civitas, Farming Guild, Harmony and Troll Stronghold patches; farming
  contracts; Huasca (no Wiki `{{Farming info}}`).
- **OPEN-FM5.** The state of a patch right after clearing: raked (stage 0) is assumed.
- **OPEN-FM6.** Fruit trees chopped down fall to one successful swing and give no logs or xp; farmed tree logs use the same success rates and
  xp as the regular tree of the kind; 1/8 depletion is stated for oak only and is used for all.
- **OPEN-FM7.** The stump-clearing success chance ("scales with Farming level, certain at 99"): level / 99 per try.
- **OPEN-FM8.** Action lengths come from the cache seq frame lengths (client ticks / 30, rounded): rake 2, plant 3, compost 5, water 5,
  cure 6, pick 2, dig 2, chop 4; the real tick counts are not published.
- **OPEN-FM9.** Limpwurt's bonus roots are uniform from 0 to the maximum; the Wiki gives only the maximum.
- **OPEN-FM10.** "Inspect" texts are generic, not the Wiki's per-model examines; "Guide" (op 4) is not handled.
- **OPEN-FM11.** Two farms closer than 104 tiles that share a slot (Catherby allotments and fruit tree, 50 tiles) show the nearest one; the real
  game switches by map region, with special cases (RuneLite `FarmingWorld`).
- **OPEN-FM12.** The tool store (leprechaun "Exchange") needs an interface; the leprechaun's chat is not imported.
