# Looting bag

Scope: `content/gameframe` `LootingBag`, `BankStorage` (bank panel), `Backpack` (pickups, destroy); death in
`content/combat` `StorageDeath`. Source: OSRS Wiki "Looting bag" (https://oldschool.runescape.wiki/w/Looting_bag, read
2026-11-07) and the rev-242 client scripts (CS n). Everything happens in the tick the click arrives.

| Id | Rule | Source |
|---|---|---|
| LB1 | The bag (11941, open 22586) holds 28 stacks in inv 516 (`looting_bag`); stackables and notes stack. | Wiki; inv 516 |
| LB2 | Only tradeable items go in ("Only tradeable items can be put in the bag."), and only in the Wilderness ("You can't put items in the bag unless you're in the Wilderness."). A bag in a bag is refused. **OPEN-LB1:** message wording; Ferox Enclave and PvP-world areas follow the `IN_WILDERNESS` attribute. | Wiki |
| LB3 | Using an item on the bag asks One / Five / All / X, or stores all when "Settings" says so (varbit `lootingbag_useallitems`). "Deposit" shows interface 81 over the backpack (CS 497 draws inv 93, CS 1235 the values) with Store-1 / 5 / All / X. Not enough room: "The bag's too full." | Wiki |
| LB4 | "Open" (→ 22586) makes tradeable items picked up in the Wilderness go straight into the bag; "Close" stops it. | Wiki |
| LB5 | "Check" shows the contents and their value on interface 81 (CS 497 with inv 516). | Wiki |
| LB6 | **Bank.** Op 1 "View" in the bank (CS 297 / 490) shows the bank-side panel; clicking an item banks it with the bank's quantity ops (CS 4881), "Deposit loot" banks everything. The bank takes the items straight from inv 516 in one transaction. Everything in the bag is tradeable, so the Wiki's "unbankable items will be moved to the player's inventory" never applies. | Wiki |
| LB7 | **Destroy.** In the Wilderness the contents drop, visible to everyone at once, except un-noted food, potions and vials of water, which are destroyed; elsewhere every item inside is lost. | Wiki |
| LB8 | **Death.** PvP death: the bag is always lost and the killer gets the contents except food and potions (deleted). PvM death in the Wilderness: the bag is destroyed (never kept) and all its contents go to the grave. PvM death elsewhere: the bag follows the normal rules and keeps its contents. | Wiki |

## Tick timeline

- Tick T: the click arrives in the input phase; inv 516 and the backpack change in one item transaction; the inv updates
  go out in that tick's info pass.
- Death: at the item split of the death (death.md DE1), before the grave is made.
