# Food and potions

Sources: OSRS Wiki "Food" (FO), "Food/Fast foods" (FF), "Cooked karambwan" (CK), "Saradomin brew" (SB),
"Shark" (SH), raw wikitext 2026-11-06; brief §8.

| # | Rule | Source |
|---|---|---|
| E1 | Standard food: eat delay 3 ticks (blocks further food), adds 3 to the attack clock. Halibut, karambwan, gnome foods add 2. | FO:7, FF:5-8, brief §8 |
| E2 | "Foods only add to an existing attack delay": the addition only matters when the clock is in the future. | FF:11 |
| E3 | Separate clocks: food, potion, karambwan. A karambwan can follow another food in the same tick; it blocks food, potions and karambwans for 3 ticks. Shark + karambwan = +5 attack delay. Marlin + brew + halibut in one tick works. | CK:30, FF:26-29, brief §8 |
| E4 | Potions have their own sip clock (no 3-tick food delay); brews don't delay attacks. | FF:29-40, FO:493 |
| E5 | Shark heals 20; karambwan 18. | SH:23, CK:24 |
| E6 | Saradomin brew: heals ⌊HP × 15/100⌋ + 2 (can overheal); Defence +⌊Def / 5⌋ + 2; Attack, Strength, Ranged, Magic −(⌊level / 10⌋ + 2). | SB:66-115 |

## OPEN

- **OPEN-E1:** potion sip clock length. Implemented: 3 ticks (same as food), separate from food.
- **OPEN-E2:** overheal cap for brews (HP + ⌊HP × 15/100⌋ + 2). Implemented as that cap.
- **OPEN-E3:** brew drain base level: formula uses the current level (SB:115) vs the table's "Overall level"
  (SB:117-135). Using the current level.
