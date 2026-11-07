# Giants' Foundry

`content/skills/.../GiantsFoundry.kt`, data and formulas in `GiantsFoundryData.kt`. Sources (all https://oldschool.runescape.wiki/w/<name>):
"Giants' Foundry", "Giants' Foundry/Strategies", "Giants' Foundry/Mould List", "Giants' Foundry Reward Shop", "Foundry Reputation",
"Kovac", "Crucible", "Mould jig", "Trip hammer", "Grindstone", "Polishing wheel", "Lava pool", "Waterfall (Giants' Foundry)",
"Preform storage". The gauges and ranges come from the cache's client scripts (disassembled): 6115 (HUD), 6117-6121 (sections, tool
ranges), 6101-6106 (commission words, mould text), 6083/6089 (reward shop). The sword is stored in the player's varbits, which the HUD
(interface 754, `Interfaces.GIANTS_FOUNDRY_HUD`, opened as the `OVERLAY_HUD`) reads: difficulty 13938, quality 13939, start quality 13950,
temperature 13948, completion 13949 (both gauges 0..1000), seeds 13940-13945 (the tool of sections 2..7), stored 13947.

## Rules

- **GF1: commission.** Kovac ("Commission") gives two descriptors: first Broad / Heavy / Light / Narrow, second Flat / Heavy / Light /
  Spiked, never Heavy+Heavy or Light+Light (Wiki "Giants' Foundry" > Preform creation). The words are written to varbits 13907 / 13908 using
  the cache enum 4372 (script 6101/6102) to map word -> value. Start time and the open-commission flag are saved per player.
- **GF2: metal.** The crucible takes 28 bars' worth (Wiki "Crucible"). Bars count 1; equipment counts bars-to-make minus 1 (scimitar,
  longsword, full helm, sq shield, claws 1; warhammer, battleaxe, chainbody, kiteshield, 2h, platelegs, plateskirt 2; platebody 4). Metals:
  bronze, iron, steel, mithril, adamant, rune (tier 1-6, Smithing 15 / 15 / 30 / 50 / 70 / 85). Counts live in varbits 13931-13936, the
  crucible state in 13937. Emptying destroys the metal.
- **GF3: metal score.** Only the two most abundant metals count (ties prefer the better one); V = 10 x tier x amount / 28; score =
  floor((floor(10 V1) + floor(10 V2) + floor(V1 V2)) / 10) (Wiki > Metal score). Checked against the Wiki's examples (60 pure rune, 130 for
  14 adamant + 14 rune).
- **GF4: mould score and the jig.** "Setup" on the jig picks a forte, blade and tip (menus sorted best first, as the Wiki says the
  interface does). The score is the sum of the two descriptors' scores over the three moulds (Wiki "Mould List"; table in the data file,
  checked against the "best default mould" table, e.g. Narrow Heavy 46). Six moulds per type are free; five per type are bought with
  reputation (300 / 300 / 350 / 400 / 450) and need their Smithing level to use (a boost works until the metal is poured). Selection is in
  varbits 13910-13912 (1-based), unlocks 13916-13930.
- **GF5: pour.** Needs a full crucible, a set-up jig and no unfinished sword. Quality = metal score + mould score (10..199); sections 3 / 4 /
  5 / 6 / 7 below 20 / 60 / 90 / 120 / above (script 6119 and the Wiki). The first section is the trip hammer; later sections draw a tool.
  The metal cools for `COOLING_TICKS` before the preform can be picked up with a bucket of water or ice gloves; it is equipped in the weapon slot.
- **GF6: gauges.** Each tool's temperature range is centred at 832 (hammer), 499 (grindstone) or 166 (polish) of 1000 and is
  `333 - metalScore x 167 / 130` wide (script 6121), inclusive at both ends (the HUD highlights it that way, script 6115). The sword's section
  is `completion x sections / 1000`.
- **GF7: machines.** Every 5th tick the trip hammer adds 2% progress and removes 2.5% temperature; every 2nd tick the grindstone adds
  1% / +1.5% and the polishing wheel 1% / -1.7% (Wiki > Refinement). The right tool at the right temperature earns Smithing xp (4.5 / 3.2 /
  3.3); a wrong tool or a temperature outside the tool's range costs 10 quality and no progress. Quality 0 ends the work ("too damaged").
- **GF8: sweet spots.** Two per sword (three with 6 or 7 sections), a 10-tick window opening right after a machine tick (Wiki > Sweet Spot).
  A click inside the window arms the next tick: more progress, double xp, and one mistake's worth (10) of quality back, up to the start
  quality; the tool and temperature rules still apply. A click outside the window while working skips the next action ("Your attempt to
  overwork the preform ...").
- **GF9: hand-in.** Smithing xp = (floor(q^2/73) + floor(1.5 q) + 1) x 30; coins = 2 x xp, to the bank; reputation = q; run energy +50%;
  swords made counted; personal best = highest quality, then shortest time (Wiki > Handing in). A ruined sword gives 30 xp and 60 coins.
- **GF10: storage.** Placing the sword in the storage (or leaving the cave, or logging out) takes it off the player and keeps its state in the
  varbits; the Wiki's login message is sent when the player returns.
- **GF11: lava and waterfall.** Heat / dunk and cool / quench change the temperature every tick at a rate that grows the longer the player stays
  (ceiling 10% of the gauge per tick, Wiki > Strategies); away from them the sword cools slowly.
- **GF12: shop.** Kovac's stock and prices are the Wiki's; items other than the grog and the colossal blade sell back at 80% of the price
  when used on Kovac. The shop is a paged option menu (OPEN-GF13).
- **GF13: tick timeline.** The machine runs from the world tick, after players: the interaction re-runs every tick (`keepInteraction`) and sets
  the station and a heartbeat; the world tick then applies the lava / cooling change and, on the tool's period, the action.

## OPEN

- **OPEN-GF1:** crucible input. The Wiki's "Fill" prompt for how many of an item is replaced by adding every unit that fits (use on the crucible
  or the "Fill" option, which adds all metal items); excess metal is not accepted rather than destroyed.
- **OPEN-GF2:** which tool each later section needs. The HUD shows seeds 0..2 per section (0 hammer, 1 grindstone, 2 polishing wheel by script
  6121's argument order); the draw is uniform and never repeats the previous section's tool.
- **OPEN-GF3:** bronze needs Smithing 15 per the Wiki's alloy table (the text says "the same level as smelting", which is 1).
- **OPEN-GF4:** the Wiki says both "10 quality points are lost" per mistake and "subtract 5 for each mistake" for reputation. 10 is used
  (`MISTAKE_PENALTY`); reputation is the final quality. Whether a mistake also skips the tick's progress, and any message for it, are not given.
- **OPEN-GF5:** the preform's temperature when picked up (`START_TEMPERATURE`, the middle of the hammer's range).
- **OPEN-GF6:** the lava / waterfall rate curve (`SLOW_START`, `FAST_START`, `RAMP_GAIN`, `RAMP_CAP`) and natural cooling (`PASSIVE_COOLING`,
  which does not oscillate). Only the 10% per tick ceiling is sourced.
- **OPEN-GF7:** when sweet spots open (the i-th at a random progress in the i-th equal slice, drawn at pour) and by how much they multiply the
  progress (`SWEET_PROGRESS_MULTIPLIER`). The schedule is not saved across a login. The HUD's gold border has no varbit found in the scripts, so the
  window is not shown; a player cannot see it.
- **OPEN-GF8:** the Smiths' uniform's bonus progress per proc (`UNIFORM_BONUS_PROGRESS`); the chance is 20% per piece, 100% with all four.
- **OPEN-GF9:** the order of the six default moulds per type is the Wiki mould list's; the cache enums 4373-4375 (forte, blade, tip) hold the
  real order and were not decoded. It only matters for what the client's own mould screen would show (the interface 718 is not driven).
- **OPEN-GF10:** the jig's state values (varbit 13914: empty 0, set up 1, poured 2, cooling 3, cooled 4), the crucible's (13937: 0, 1, 2) and
  the storage's (13947) are assumed from the order of the multi-loc variants (44622-44624, 44625-44629). The cooling time `COOLING_TICKS`.
- **OPEN-GF11:** the Dwarf Cannon gate on the double ammo mould, the Sleeping Giants requirement for entering, the Bank PIN on the shop, and the
  Ultimate Ironman coin rule are not implemented. Kovac's tile, the arrival tile (cache exit loc 44636) and the cave mouth are read off the cache.
- **OPEN-GF12:** a click while a machine runs is detected as `Skilling.clock(...).fresh`; if the engine does not mark a re-click as fresh, neither
  the over-click miss nor the sweet spot click can happen (plugin-api would need a "this run is a new click" flag).
- **OPEN-GF13:** the native mould setup (718), crucible and reward shop (753) interfaces are not driven; dialogue lines, menu texts and
  messages other than the Wiki's quoted ones are not sourced.
- **Not implemented:** the Smiths' gloves + ice gloves combination, the colossal blade, ore pack, catalyst and grog effects, Kovac's quest
  dialogue, the collection log entries, per-action animation timing and the worn-item option variants.
