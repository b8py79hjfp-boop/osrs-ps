# Blast Furnace

`content/skills/.../BlastFurnace.kt`, `BlastFurnaceData.kt`. Sources: Wiki "Blast Furnace", "Conveyor belt (Blast Furnace)", "Bar
dispenser", "Coffer (Blast Furnace)", "Pedals", "Stove (Blast Furnace)", "Pump (Blast Furnace)", "Temperature gauge", "Drive belt",
"Cogs (Blast Furnace)", "Pipes (Blast Furnace)", "Blast Furnace Foreman", "Melting pot", "Coal bag", "Ice gloves".
Basement: map square 30,77 (region 7757). The furnace contents are per player (the real game keeps them in varbits 941..959).

## Rules

- **BF1: access.** Below 60 base Smithing (not boostable) the foreman charges 2,500 coins for 10 minutes (real time), by his
  "Pay" option or dialogue; 60 Smithing is free. Without access the belt and the dispenser refuse. (Ring of Charos(a) halving is OPEN.)
- **BF2: the coffer.** Loc 29328 / 29329 "Use": deposit or withdraw coins (saved per player; varbits 5357 coins, 5356 flag). While
  the player is in the basement the dwarves cost 12 coins per tick; at 0 coins the dwarves stop (Wiki "Coffer").
- **BF3: the belt.** "Put-ore-on" puts every ore in the backpack (and the coal bag's coal) on the belt. Ores need the Smithing
  level of their lowest bar (message otherwise). Limits: 254 coal and 254 tin, 28 primary ores (with the bars waiting): "You
  should collect your bars before making any more." (Wiki "Blast Furnace" notes). The belt reaches the pot after 4 ticks (OPEN-BF1)
  and only while powered: the coffer pays the dwarves, or a player pedals, with the cogs and drive belt whole.
- **BF4: the melting pot** (Check, loc 9098): ores arriving join the pot and it smelts at once, half the coal of a normal furnace:
  bronze 1 copper + 1 tin; iron 1 iron ore (no failure; the Wiki's xp/hour table); silver 1; steel 1 iron + 1 coal; gold 1;
  mithril 1 + 2 coal; adamantite 1 + 3; runite 1 + 4. Iron ore with coal in the pot becomes steel, otherwise iron ("make sure
  coal is already loaded"). Ores lacking coal wait in the pot.
- **BF5: xp.** Full Smithing xp per bar, given when the bars are taken: bronze 6.2, iron 12.5, silver 13.6, steel 17.5, gold 22.5
  (56.2 with goldsmith gauntlets worn at the moment of taking), mithril 30, adamantite 37.5, runite 50 (Wiki "Blast Furnace" >
  Experience rates; "do not equip the ice gloves until the experience drops").
- **BF6: the dispenser.** "Take" (op 1) gives the bars that fit the backpack once they are cool or with ice gloves (or Smiths gloves
  (i)) worn; "Check" (op 2) lists them. Fresh bars are hot (varbit 936: 0 empty, 2 hot, 3 cooled) for 50 ticks (OPEN-BF3) or until
  a bucket of water is used on the dispenser (Wiki "Bar dispenser"); the sink fills empty buckets. Cooling for other players
  within 5 tiles is OPEN.
- **BF7: pedals.** Level 30 Agility; each tick of "Pedal" costs 0.5% run energy (50 of 10000) and gives 1 Agility xp (Wiki "Pedals"),
  stopping when the energy is gone. The belt moves while a player pedals.
- **BF8: cogs and drive belt.** While pedalling by hand a part may break (assumed 1 in 400 per tick, OPEN-BF4); the belt stops
  until a player repairs it with a hammer for 50 Crafting xp (Wiki "Cogs", "Drive belt"). The dwarves repair on a paid world: with
  coins in the coffer no part breaks (it is never pedalled by hand).
- **BF9: stove and pump.** "Collect" coke with a spade gives a spadeful; "Refuel" gives 5 Firemaking xp, 30 fill the stove (Wiki
  "Stove"); "Operate" the pump gives 2 Strength xp per tick while the stove has coke (Wiki "Pump"), level 30.

## OPEN

- **OPEN-BF1:** belt travel time to the melting pot (4 ticks assumed). The Wiki only gives whole-trip times.
- **OPEN-BF2:** this world is treated as a themed Blast Furnace world (coffer on); the manual path is used when the coffer is empty.
- **OPEN-BF3:** how long bars stay hot (50 ticks assumed).
- **OPEN-BF4:** break chances of the cogs / drive belt (1 in 400 per pedalled tick); the pipes are not modelled.
- **OPEN-BF5:** temperature, overheating ("disables it temporarily", 10 November 2021 change), the gauge and its warning signs are
  not modelled: the Wiki gives no numbers (stove burn rate, pump heat, red zone time). The stove is a counter that does not burn.
- **OPEN-BF6:** the Blast Furnace HUD (interface 474), the plan scroll, the "bars withdrawn" make-all interface, the contribution xp
  (1.5 xp, 10 tick cooldown, manual operation only), Ring of Charos(a), chromium ingots, lead / nickel / cupronickel / lovakite /
  perfect gold ores, the safe zone on PvP worlds, and the stairs into the basement are not implemented.
- **OPEN-BF7:** message texts (the foreman refusal, pedal / cog / drive belt messages) are not cited.
- **OPEN-BF8:** the coal bag is emptied onto the belt together with the backpack's coal (Wiki "Coal bag" counts "in bag + in inventory").
