# Bank deposit box

Scope: interface `bank_depositbox` (192) and the locs that open it (`content/gameframe`: `DepositBox`, deposits run by `Bank`).
No OSRS Wiki text was reachable while this was written (the host was outside the session's network), so every row is from the
rev-242 cache (`cache-dump interface 192`, `config loc`, `cache-pack script`; `CS n` = cache client script n) and from RuneLite's
`ShiftDepositMode` (BSD-2) for op numbers. Texts and rules the cache cannot show are `OPEN`. A click is one IF_BUTTON packet
handled in the input phase; each deposit is one `BankTransfer`, which `Bank` runs as one item transaction (brief section 12).

| # | Rule | Source |
|---|---|---|
| D1 | **Opening.** Op 1 "Deposit" of a loc named "Bank deposit box", "Deposit box", "Deposit pot" or "Bank deposit pot" (any case) opens 192 as the main modal (no side panel: the backpack is drawn inside, from inv 93 via the hook CS 144 installs, the worn items from inv 94). The server sends inv 93 / 94 and enables ops 1..6 and 10 on `bank_depositbox:inventory` children 0..27 (the children are made by CS 146). | `cache-dump config loc` (all op1 = "Deposit"); CS 144 (RuneLite `BANK_DEPOSITBOX_INIT`), CS 146, CS 172 |
| D2 | **Backpack item ops** (CS 172, RuneLite `ShiftDepositMode`): op 1 = the left-click quantity of varbit `DEPOSITBOX_MODE` (4430: 0 one, 1 five, 2 ten, 3 last X, 4 all; CS 172 sets op 1 only for modes 1-4), op 2 = 1, 3 = 5, 4 = 10, 5 = X (count dialog, remembered in varp `DEPOSITBOX_REQUESTEDQUANTITY` 1794, which op 1 uses in mode 3), 6 = all, 10 = examine. The slot and obj of the packet must match the backpack; the dialog's answer re-checks both. | CS 172, RuneLite `ShiftDepositMode` |
| D3 | **Quantity buttons** (`_1`, `_5`, `_10`, `all`) set `DEPOSITBOX_MODE` 0, 1, 2, 4. The X button: op 1 selects mode 3 (mode 0 if no X was ever typed), op 2 "Set custom quantity" asks for a number first. | `cache-dump interface 192` (component 38 has op 2 "Set custom quantity"); CS 376 (mode highlight reads varbit 4430) |
| D4 | **Deposit rules** are the bank's (bank.md B8): notes go in as the unnoted item, stack onto the item's slot or replace its placeholder, else a new slot at the end of the selected tab (`bank_currenttab`), items with obj param 59 stay ("A magical force prevents you from banking this item!"), a full bank answers "You don't have enough space in your bank account." and a full stack "You already have a full stack of that item in the bank." | bank.md B8 |
| D5 | **Deposit inventory / Deposit worn items** (`deposit_inv`, `deposit_worn`): every distinct obj of inv 93 / 94, in slot order, one deposit each; an empty source answers "You have nothing to deposit."; worn deposits publish `AppearanceChanged`. The worn button is hidden by varbit `DEPOSITBOX_HIDEDEPOSITWORN` (20095), toggled by `depositworn_toggle`. | CS 9334; bank.md B9 |
| D6 | **Deposit loot** (`deposit_lootingbag`): same as the bank's looting bag button (bank.md B33), only with a looting bag in the backpack. | `cache-dump interface 192`; bank.md B33 |
| D7 | **Worn slot ops**: op 2 deposits that slot, op 10 examines (the slot components carry ops 1, 2 and 10 like the bank's worn page). | `cache-dump interface 192` |

## OPEN

- **OPEN-DB1:** which tab new items go to. Implemented as the bank does (the tab selected last, `bank_currenttab`; main if none).
- **OPEN-DB2:** no bank PIN is asked and the potion store (bank.md B22) is not used. Both unverified.
- **OPEN-DB3:** the meaning of worn slot op 1 (the bank's is "remove to backpack"); not handled.
- **OPEN-DB4:** the backpack column's op 9 (wear / eat) is not offered: varps 261-264 are cleared on open. Deposit locks (`lock_menu`, op 10 "Lock-slot") are not implemented.
- **OPEN-DB5:** the messages of a deposit-all into a full bank: each obj answers separately with D4's text, not "Your bank cannot hold your items.".
- **OPEN-DB6:** loc 29108 "Bank Deposit Pot" (multi loc 29109 "MM_DEPOSIT_BOX", varbit 5027) is wired by name; its owning minigame is not checked.
