# Motherlode Mine

`content/skills/.../Motherlode.kt`, `MotherlodeData.kt`, `Bags.kt`, veins in `resources/.../motherlode-veins.tsv` (baked from the
cache: every `Depleted vein` loc 26665..26668 of map square 58,88, with `upper` = placed on raw level 1; all veins are plane 0).
Mining of veins and rockfalls goes through `Mining.kt` (mining.md). Sources: Wiki "Motherlode Mine", "Ore vein", "Pay-dirt",
"Hopper (Motherlode Mine)", "Sack (Motherlode Mine)", "Strut", "Rockfall", "Prospector Percy", "Prospector Percy's Nugget Shop",
"Prospector kit", "Coal bag", "Gem bag" (all https://oldschool.runescape.wiki/w/<name>).

## Rules

- **ML1: veins.** Level 30, 60 xp, pay-dirt rolled at statRandom low 60 / high 105 (Wiki "Pay-dirt" success chart), the gem table
  before each attempt (Wiki "Ore vein" > Pre roll drops). The cache places every vein depleted, so the world activates them at
  start (`activateVeins`). The first pay-dirt mined from a vein starts its timer: 23-27 s on the lower floor, 36-40 s on the upper
  floor (38..45 / 60..67 ticks). When the timer is up the vein depletes with the next ore mined from it, so a vein nobody mines
  still gives one more ore (Wiki "Ore vein": "Veins not being mined at the time they should deplete will still allow one ore").
  A depleted vein respawns after 100 ticks.
- **ML2: the sack and its capacity.** 108 pay-dirt, 189 after the 200-nugget upgrade (`Varbits.MOTHERLODE_BIGGERSACK` 5556 mirrors it).
  Pay-dirt in the sack plus the tray counts against it; when full "Some ore is ready to be collected from the sack. It's getting
  full." is sent and no pay-dirt can be mined or deposited (Wiki "Motherlode Mine"). `Varbits.MOTHERLODE_SACK_TRANSMIT` 5558
  (varp 375, bits 8-15) carries the pay-dirt in the sack to the HUD (interface 382, `Interfaces.MOTHERLODE_HUD`), opened as the
  `OVERLAY_HUD` while inside the mine's map square.
- **ML3: hopper and tray.** Op "Deposit" moves as much pay-dirt as fits. At most 3 golden nuggets per deposit (Wiki "Pay-dirt").
  The tray reaches the sack 8 s (13 ticks) later and only while at least one water wheel runs; then the ores are added and the bonus
  xp is given (Wiki "Motherlode Mine" > Pay-dirt). The tray is saved, so logging out keeps it. The upper hopper needs 50 nuggets.
- **ML4: what a pay-dirt becomes.** A nugget with probability 1/32 (Wiki "Pay-dirt": 1/32, about 3.13%), else the cascade from the top
  tier the Mining level allows: runite (85) low -20 / high 5, adamantite (70) -90 / 50, mithril (55) -19 / 90, gold (40) -40 / 126,
  else coal (Wiki "Motherlode Mine" ore chance chart; "Coal is actually a 100% drop if you fail all other rolls"). The elite Falador
  diary (varbit `FALADOR_DIARY_ELITE_COMPLETE`) adds about 1 / 2 / 1 percentage points to runite / adamantite / mithril (Mod Ash,
  27 January 2022), applied as a second independent roll. Bonus xp per ore: coal 15, gold 15, mithril 30, adamantite 45, runite 75,
  nugget 0. Falador easy-to-hard diaries give nothing (Wiki).
- **ML5: search.** Op "Search" on the sack moves ores into the backpack tier by tier, a random amount of each tier at a time, until
  the backpack is full or the sack empty (Wiki "Sack (Motherlode Mine)" trivia, Mod Ash 4 February 2021). Nuggets come first and
  need no slot.
- **ML6: water wheels and struts.** Two struts hold the wheels; a repaired strut breaks 2, 3 or 4 rounds of 58 s later (97 ticks per
  round). Both broken stops the tray (Wiki "Strut"). Repair: op "Hammer" or a hammer / Imcando hammer used on the broken strut,
  a roll every tick with statRandom(Smithing, 30, 70) (Wiki "Strut" success chart; 12.11% at level 1, 27.73% at 99), xp 1.5 x the
  Smithing level. The state is world-wide. Locs: strut 26669 / broken 26670 (placed at another tile, 3742), wheel 26671 / 26672.
- **ML7: upper floor.** Level 57 base Mining and 100 nuggets (Wiki "Motherlode Mine"); the ladder 19047 (bottom) / 19049 (top)
  moves the player between the landings one tile beyond the ladders. `Varbits.MOTHERLODE_IN_RESTRICTED_AREA` (2086) selects the
  active ladder variant (0 = unlocked).
- **ML8: Prospector Percy.** Talk-to offers the three one-time purchases (upper level 100, upper hopper 50, larger sack 200 nuggets);
  Trade is the existing shop 295 (`shops.tsv`; the coal bag and gem bag rows now point at objs 12019 / 12020, the real bags).
- **ML9: rockfalls.** Op "Mine", no requirement, 10 xp, any pickaxe; they fall again after 18 s (30 ticks) (Wiki "Rockfall").
- **ML10: bags.** Coal bag (12019, open 24480): 27 coal, 36 with a Smithing cape; the Blast Furnace belt and the furnace
  smelting draw from it after the backpack's coal. Gem bag (12020 / 24481): 60 each of sapphire, emerald, ruby, diamond,
  dragonstone. Fill / Empty / Check / Open / Close; an open bag takes the coal or gems mined (Wiki "Coal bag", "Gem bag").
  One bag of each kind: contents are saved per player.
- **ML11: tick timeline.** Mining a vein is mining.md MN1. Deposit: tick T the pay-dirt leaves the backpack; T+13 the tray reaches the
  sack (if a wheel runs). Search is instant.
- **ML12: prospector outfit.** Helmet +0.4%, jacket +0.8%, legs +0.6%, boots +0.2% Mining xp, +0.5% for the full set (2.5%); the
  golden and Volcanic Mine copies count; Varrock armour 4 may replace the jacket (Wiki "Prospector kit"). It multiplies every
  Mining xp source (veins, rocks, rockfalls, the sack's bonus xp) through `MiningOutfit`.

## OPEN

- **OPEN-ML1:** message texts other than "Some ore is ready to be collected..." are from the live game's wording, not cited.
- **OPEN-ML2:** the HUD's use of varbit 5558 (sack only, or sack plus tray) and of the interface's second figure ("Deposits") is not
  decoded; 5558 carries the sack count.
- **OPEN-ML3:** the Wiki fixes the ore when the pay-dirt is mined; identical pay-dirt items cannot carry it, so the ore is rolled
  at the hopper with the Mining level of that moment (boost while mining, not while depositing, is not honoured).
- **OPEN-ML4:** depletion of a vein nobody is mining is lazy (it shows the next time someone mines it); the Wiki says the vein
  depletes at its time.
- **OPEN-ML5:** lower-floor inner-ring veins respawn after 150 ticks, the others after 100 (Wiki "Ore vein"); the inner ring's tiles
  are not listed, so all use 100.
- **OPEN-ML6:** the strut break round (2, 3 or 4) is assumed uniform; 58 s is rounded up to 97 ticks.
- **OPEN-ML7:** the diary bonus is a second independent roll, a close approximation of "added percentage points".
- **OPEN-ML8:** ladder landings (3755, 5675) and (3755, 5672) are the free tiles beyond the ladders in the collision data; the
  cache does not say where the climb ends. Entrances (Mining Guild gate, 60 Mining west entrance) are not gated.
- **OPEN-ML9:** the Volcanic Mine copies of the outfit and a worn Varrock armour 4 count for the set bonus.
- **OPEN-ML10:** repair and rockfall animations; the player-damage of a rockfall that falls on a player (4% of current Hitpoints + 1,
  then pushed out; Wiki "Rockfall") is not implemented (combat damage is not in plugin-api for skilling content).
- **OPEN-ML11:** Miners (NPCs), the Dark tunnel shortcut (54 Agility, medium Falador diary) and the Hammer / pickaxe crates are not implemented.
- **OPEN-ML12:** selling the helmet and bags back to Percy for 80% of the nuggets (Wiki "Prospector kit" / "Coal bag") is a shop
  buy-back setting of shop 295 (`buy` 800 per mille), not Percy-specific code.
- **OPEN-ML13:** bag "Destroy" and the gem bag's item-on-bag ("Use gem on bag") are not implemented; mined gems enter an open bag only when mined.
