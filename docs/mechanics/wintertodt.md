# Wintertodt

Code: `content/skills/.../Wintertodt.kt` (interactions; the world tick hook drives the game), `WintertodtGame.kt` (the shared game of a world),
`WintertodtRewards.kt` (reward cart loot). Shared skilling rules: skilling.md.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-07:
- (W) "Wintertodt", https://oldschool.runescape.wiki/w/Wintertodt
- (S) "Wintertodt/Strategies", https://oldschool.runescape.wiki/w/Wintertodt/Strategies (damage formulas by Mod Ed, attack rolls by Mod Ash)
- (WM) "Warmth", (B) "Brazier", (P) "Pyromancer", (RP) "Rejuvenation potion", (K) "Bruma kindling", (BR) "Bruma roots"
- (RC) "Reward cart" and Module:Wintertodt supply crate (the per-level loot weights)
- (C) the cache: locs 29308 to 29327 (`wint_*`), 55411 to 55416 and 55423 (reward cart multiloc on varbit 11435), npcs 7371 / 7372 /
  13793, interface 396 `wint_status`, varp 4487 `wint_int` = varbits 11434 `wint_warmth` (bits 0..9), 11435 `wint_reward_pool` (10..22),
  11436 `wint_needs_warning` (23), varp 1528 `total_wintertodt_kills`.

## Rules

| # | Rule | Source |
|---|---|---|
| WT1 | One shared game per world, in the prison north of the Doors of Dinh (loc 29322 at 1627,3964). "Enter" needs 50 Firemaking and puts the player at 1630,3968; from inside it leaves to 1630,3962. Without four warm items: "You feel the cold wind strike you as you enter; perhaps you should find some warmer clothes." Leaving, logging out or being teleported out loses the round's points. | W, WM, C |
| WT2 | Rounds: after the previous one ends (or when the first player enters an empty game) a 100 tick countdown runs, then the storm wakes with 100% energy, all braziers unlit and all pyromancers at full health. | W (Changes 2023), S |
| WT3 | Energy: every healthy pyromancer at a lit brazier takes 1% every 14 ticks, starting 2 to 8 ticks after the brazier was lit or she was healed. With all braziers out the storm regains 1% every 35 ticks, after a random first delay of up to 35 ticks. At 0% the round is subdued. Feeding does not change the pace. | S |
| WT4 | Attacks. Standard: every 10 ticks per player outside the safe area, it goes through when a roll below the maximum energy is below the energy left. Brazier and pyromancer attacks go through when the roll is above the energy left; a lit brazier is either shattered (heavy, hurts players next to it) or put out (light), a pyromancer loses 7 of her 14 health. Area: a 3x3 snowfall on a player who has no obstacle beside them. | S, W |
| WT5 | Damage is taken from the warmth meter in percent: standard `floor((16 - W - min(2B, 6)) * 100 / FM)`, brazier `2 * floor((10 - W) * 100 / FM)`, area `3 * floor((10 - W) * 100 / FM)`; FM is the base Firemaking level, W the worn warm items (at most 4), B the lit braziers. A cold hitsplat shows; hitpoints are not touched. At 0% warmth the player is killed through a `DamageRequest` for their full hitpoints. Warmth regenerates 8% a minute plus 1% per warm item. Eating food restores 35% (food that heals 4+ Hitpoints, not triangle sandwiches or wine; published by the consumables plugin as `Consumed`), a dose of rejuvenation potion 30% (its own "Drink" option here; the last dose leaves nothing). | S, WM, RP |
| WT6 | Lighting an unlit brazier (tinderbox or bruma torch, healthy pyromancer) takes 4 ticks, gives 25 points and 6x Firemaking level xp; players who began before the state changed all get the reward. Repairing a broken one (hammer) takes 4 ticks, 25 points, 4x Construction level xp. Feeding a lit brazier: root 10 points and 3x, kindling 25 points and 3.8x Firemaking level xp. | W, B, S |
| WT7 | Bruma roots: chopped on a 3 tick timer with the plain tree cut chances, 0.3x Woodcutting level xp. Knife on a root: 4 ticks per kindling (fletching knife: 3 from the second), 0.6x Fletching level xp, not in the safe area, a cold hit interrupts it. A bruma herb on an unfinished potion makes a 4-dose rejuvenation potion in the prison (0.1x Herblore level xp); Brew'ma does every pair at once for no xp. Crates hand out hammer, knife, bronze axe, tinderbox and unfinished potions. | W, K, BR, RP |
| WT8 | Healing a pyromancer ("Help" or a potion used on her) takes one dose from the potion with fewest doses, heals her fully, gives 75 points and sets her bolts going again. | P, RP, S |
| WT9 | Round end with 500+ points: Firemaking xp 100x base level, reward-cart rolls: 2 at 500, one more per 500, plus a 1% chance per 5 points toward the next; the kill count rises. Under 500: "You did not earn enough points to be worthy of a gift from the citizens of Kourend this time." Rolls wait in the cart (saved `wintertodt.rewards`, varbit 11435, at most 8,000). | W, RC |
| WT10 | Searching the cart gives one reward per 3 ticks ("Search"), five per click ("Big-search"); loot is rolled on searching, using current levels. Each roll: phoenix 1/5000 (if not owned), dragon axe 1/10000, tome of fire (empty) 1/1000, warm gloves 1/150 (a magic seed with 3 owned), bruma torch 1/150 (2 to 3 torstol seeds with 3 owned), the pyromancer piece owned least of (garb, hood, robe, boots on a tie) 1/150, burnt pages 7 to 29 1/45. Otherwise 25 slots: 3 each for logs (Woodcutting), gems (Crafting), ores (Mining), herbs (Herblore), seeds (Farming), fish (Fishing); 5 coins 2000 to 4999; 1 dynamite and 1 saltpetre 3 to 5 noted. Within a skill the items are tried in table order, each with chance `(floor(low + (high - low) * (L - 1) / 98) + 1) / 256`. | RC, Module:Wintertodt supply crate |
| WT11 | HUD: interface 396 on the HUD overlay while inside (closed again on leaving; a logout needs no close): texts "Wintertodt's Energy: N%", "Points: N", "Warmth: N%", varbit 11434 = warmth in tenths of a percent, varbit 11435 = cart rewards. | C |
| WT12 | Pyromancer outfit and warm clothing: see firemaking.md FM9 and the Wiki "Warmth" list (matched by cache name, at most 4 count). | W, WM |

### Tick timeline (a round)

- Tick 0: the first player enters an idle game; `startAt = tick + 100`.
- Tick 100: the storm wakes (energy 100). Players click braziers: the click arms a 4 tick weak queue; at +4 the brazier loc becomes the lit one, the pyromancer's first bolt is 2 to 8 ticks later, then every 14.
- Each tick: pending warnings (4 ticks after being announced) resolve, then the 8-tick attack roll and the 20-tick area roll.
- Energy 0: `endRound` pays everyone inside, resets the game and starts the next 100 tick countdown.

## OPEN

- **OPEN-WT1:** the prison and safe-area bounds are estimates from loc positions (prison x 1604..1652, z 3965..4030; safe lobby
  x 1624..1636, z 3965..3987); the exact walls and the Doors of Dinh walk-through are not known. The game advances from the
  world tick hook (one clock, not the players' timers), but with nobody inside it is not simulated: the first entrant
  restarts the countdown (the storm's energy and the braziers reset).
- **OPEN-WT2:** the unit of varbit 11434 (treated as tenths of a percent), the cold hitsplat id (the generic damage hitmark 16/17 is
  used), the brazier / pyromancer / energy icons of interface 396 (their client script is not decoded; energy, points and warmth are
  sent as text), the respawn-timer varbit 7980 (not driven) and the area-attack varbit 5354 (not driven).
- **OPEN-WT3:** how often the storm rolls its brazier / pyromancer / area attacks (8 and 20 ticks are placeholders), the warning
  delay (4 ticks), the heavy-to-light ratio (even) and which pyromancer or brazier is picked (uniform) are not published.
- **OPEN-WT4:** the feeding cadence (3 ticks) and herb-picking cadence (2 ticks) are placeholders; no feeding, repairing,
  lighting, fletching or tending animation except the fire-lighting one (human_createfire) is played (sequence ids unknown).
- **OPEN-WT5:** healing a full-health pyromancer is refused ("The pyromancer is not hurt."), which the Wiki does not state; the
  exact wording of several refusals is not sourced. The last dose of a potion leaves nothing behind.
- **OPEN-WT6:** the bruma-root success chance uses the plain tree table (the Wiki says a steel axe at level 46 is enough to max
  it, which the table reproduces); infernal axe's "does not burn bruma roots" holds because this path never calls the burn.
- **OPEN-WT9:** warmth from food covers only what the consumables plugin publishes (`Consumed` for the foods of its table); foods
  it does not know (cake, pies, hunter meat's two bites worth 70%) restore nothing. The Wiki's Warmth page lists no hot drinks, so
  none are handled. Drinking a rejuvenation potion has no potion clock (the consumables clocks are internal to content/combat).
- **OPEN-WT7:** missing: ring of life and phoenix necklace saving at 10% / 20%,
  Heal Other, Rapid Heal, regen bracelet, Hitpoints cape (warmth regeneration), High Level Alchemy being blocked, the Agility
  pillars (`wint_pillar_jump`), the gap shortcut, Ignisia's exchange of extra uniques for crates or pages, the Phoenix pet (the
  reward gives the pet item; there is no pet follower system), tome of fire charging, the bruma torch "Swap" option, Construction
  xp only for house owners (everyone gets it here), the music tracks, and the Kourend diary task.
- **OPEN-WT8:** weak queues are removed by walking clicks, so fletching and potion making stop when the player starts to run, unlike
  OSRS where you can fletch while running.
