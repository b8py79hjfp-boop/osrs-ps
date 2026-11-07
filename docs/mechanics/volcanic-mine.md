# Volcanic Mine

`content/skills/.../VolcanicMine.kt` (plugin), `VolcanicMineGame.kt` (the world's game state), `VolcanicMineData.kt` (tables). Sources (all
https://oldschool.runescape.wiki/w/<name>): "Volcanic Mine", "Boulder (Volcanic Mine)", "Petrified Pete", "Petrified Pete's Ore Shop",
"Volcanic chamber", "Blocked volcanic chamber", "Large rock", "Heat-proof vessel", "Numulite", "Template:FossilDropLines". The HUD
(interface 611, `Interfaces.FOSSIL_VOLCANIC_MINE`, opened as the `OVERLAY_HUD`) is varbit driven (client scripts 2020-2027): progress status 5941
(1 = counting down to the start, 2 = running), time remaining 5944 (ticks, counted down by the client), stability 5938, players 5937, points 5934,
chamber status 5936 (bit i set = chamber i blocked), vents A / B / C 5939 / 5940 / 5942 (127 = unknown, else the pressure percent).

One game per world: the state lives in the plugin instance (`VmGame`), nothing is global. Group size is 1 to 50.

## Rules

- **VM1: entry.** 50 Mining; 30 numulite each time or a one-off 3,000 for permanent access (Wiki "Volcanic Mine", "Petrified Pete"). Op "Pay" on
  Pete or the volcano entrance. The player gets a heat-proof vessel and arrives on the deck above the mine.
- **VM2: the game cycle.** The first entrant starts a 30 s (50 tick) preparation; everyone entering within it joins; nobody joins later ("You are no
  longer able to join a mine that is past its preparation phase", Wiki changes 14 September 2017). Then 10 minutes (1,000 ticks) run; at the end,
  or when the stability is 0, the volcano erupts and everyone inside dies (through the combat module's `DamageRequest`, as Wintertodt does). The
  stairs down are usable only while the game runs.
- **VM3: boulders.** One boulder in the central channel below 10 players, a second (western) from 10, a third (eastern) from 20. Each goes through
  five stages (giant attached, giant, large, medium, small); a stage has about 40 hitpoints per player (60 per player for the small one), shared
  by the boulders. A swing is one hitpoint. A finished boulder gives every player 100 points.
- **VM4: fragments.** A success gives a fragment with probability 1/20, 1/12, 1/8, 1/5, 1/3 by stage, else "You chip off a part of the boulder."
  (1 point, 5 xp). With no free slot a fragment becomes a chip. The kind cascades from runite (85, low -10 / high 80), adamantite (70,
  -90 / 90), mithril (55, -75 / 150), gold (50, -40 / 160), coal (50, -20 / 170), silver (50, 0 / 185), else iron; points 70 / 55 / 44 / 34 / 25
  / 20 / 15 and Mining xp 150 / 100 / 60 / 40 / 30 / 20 / 10. Xp goes through `MiningOutfit` (the prospector kit's bonus, giving the Wiki's
  3.895 per point).
- **VM5: other drops.** After a fragment: 1/20 calcite and 1/20 pyrophosphite (1-4 each); otherwise of 3,500: 900 numulite (5-8), 50 / 25 / 20 / 5
  unidentified small / medium / large / rare fossil (Wiki "Template:FossilDropLines" with fossil = 5, numulite = 4, access = 9/10).
- **VM6: chambers.** Three chambers (A west, B north, C east) take a large rock (from the rock piles) to block, raising pressure, and a pickaxe to
  unblock (25 xp), lowering it. Each action is worth 50 points, up to 300 per player. A vent "Check" shows its chamber's pressure to everyone; every
  5 minutes (500 ticks) the mine shifts, randomising the pressures and hiding the readings. Pressure should stay within 40-60%.
- **VM7: stability.** With less than 30 s (50 ticks) left the stability is set to 5% and holds, and nothing can be mined or fixed (Wiki >
  Eruption). Leaving: the exit gives the earned points as shop points and 3.8 Mining xp per point; dying or teleporting out gives nothing, and
  fragments, rocks and the vessel are removed.
- **VM8: the vessel.** "Throw-water" hardens the lava tile beside the player in a chosen direction (loc 30997 -> 30998) for 90 s (150 ticks); the
  tile turns orange (30999) 45 s in and white (31000) for its last 10 ticks, then melts back.
- **VM9: shop.** Ores (noted) per point 30 iron, 55 silver, 60 coal, 150 gold, 150 mithril, 300 adamantite, 855 runite; ash 40; calcite and
  pyrophosphite 70; teleport 200; large water container 10,000; ash covered tome 40,000; ore pack 4,000; prospector helmet 26,000, jacket 39,000, legs
  34,000, boots 21,000 (Wiki "Petrified Pete's Ore Shop"). Points are capped at 2^21 - 1. The shop is a paged option menu.

## OPEN

- **OPEN-VM1:** places. The lobby deck `LOBBY` (level 3 above the stairs), the landing two tiles beyond a staircase, the exit loc (31032, absent
  from the cache's map; placed at `EXIT_AT` at world start) and Pete's tile are read off the cache or guessed; the exit roles of the staircases and
  the entrance loc are assumed (top staircase = down, bottom = up, exit loc = leave). The boulder tiles are the Wiki's map pins.
- **OPEN-VM2:** pressure dynamics: a blocked chamber gains 1% and an open one loses 1% every `PRESSURE_STEP_TICKS`; initial pressures and blocked
  states are random at the start and every shift. The Wiki gives only the 40-60% target.
- **OPEN-VM3:** stability loss: 1% per `STABILITY_STEP_TICKS` per chamber outside 40-60, never regained. Stability-based points ("points given
  generously to those maintaining stability") are not given; solo "reduced vent destabilisation" is not modelled.
- **OPEN-VM4:** unblocking returns the large rock; the rock pile refills after `ROCK_RESPAWN_TICKS`.
- **OPEN-VM5:** initial and shifted chamber states and pressures are uniform.
- **OPEN-VM6:** the hitpoint split between several boulders (rounded-up share) and the small boulder's boost when not everyone mines it
  (`SMALL_BOOST` while fewer than all players swung in the last 6 ticks). The Wiki says only "boosted".
- **OPEN-VM7:** the boulder's mining success chart is missing from the Wiki; every swing succeeds, on the pickaxe's cycle.
- **OPEN-VM8:** the vessel's uses: 36 / 72 (item page) against 35 / 70 (Volcanic Mine page). The default "Throw-water" asks for a direction (the
  plugin-api has no facing direction), and the worn-item options are not wired.
- **OPEN-VM9:** the rock golem pet (1/58,000) is not rolled (no Mining pet exists in the repo yet).
- **Not implemented** (they need damage or combat rules): lava beasts, meteorites, earthquakes, stalactites, falling into the lava, the gas holes,
  item retrieval from Pete after a death, the volcanic mine teleport tablet's effect, the fossil storage and the shop interface (612).
