# Grand Exchange (skeleton)

| # | Rule | Source |
|---|---|---|
| GE1 | A new offer matches the oldest compatible opposite offers. The trade happens at the price of the offer placed first, and a buyer gets the difference back in coins. | OSRS Wiki "Grand Exchange" |
| GE2 | Each item has a guide price that drives value-based features: items kept on death ordering, the price checker, the risk shown on Items kept on death. | OSRS Wiki "Grand Exchange#Guide price" |
| GE3 | Offers have 8 slots. Coins (buy) or items (sell) are taken when the offer is placed and come back through the collection box. | OSRS Wiki "Grand Exchange" |

| GE4 | The GE opens 465 `ge_offers` as the main modal with 467 `ge_offers_side` over the backpack; the server enables the ops the client scripts create (slot children 2–4 ops 1–3, setup children 0–15 ops 1–2, collect-all, details status/collect, side items ops 1 and 10). | cache client scripts 773, 783, 794, 803, 816 |
| GE5 | Varps holding an obj must be -1 for "none": `tradingpost_search` (1151), `ge_last_searched`, `ge_last_offer_item`, `ge_itemsink_obj_0..7`; with 0 an empty slot renders as a sell offer. | client scripts 779, 5733 |
| GE6 | Slot state goes to the client with UPDATE_STOCKMARKET_SLOT: status bits 0–2 = state (2 active, 5 finished), bit 3 = sell; plus each slot's collect inv (518–523, 539, 540: slot 0 item, slot 1 coins). | client `na`, enum 150 |
| GE7 | Slot buttons: child 2 op1 view (client sets `ge_selectedslot` = slot + 1), op2 abort, op3 modify; child 3 create buy, child 4 create sell (`ge_newoffer_type` 0/1). Back sets `ge_selectedslot` 0. | client scripts 794, 798, 805 |
| GE8 | Buy item: client script 750 (chatbox item search) answered by RESUME_P_OBJDIALOG; the choice goes in `tradingpost_search` and `ge_last_searched`. | client scripts 750, 754 |
| GE9 | Sell item: "Offer" on a side-inventory item (467 child = backpack slot). | client script 783 |
| GE10 | Quantity and price buttons are applied by the client itself; the server repeats the same arithmetic: ±1, +1/+10/+100 (delta − 1 when moving off 1), +1K buying / All selling, max affordable = coins ÷ price; price ±1, ±5% (step price ÷ 20, at least 1), guide price, enter price. `ge_newoffer_price` (5753) is a 64-bit varp. | client scripts 777, 778, 5156 |
| GE11 | Confirm is disabled by the client for a missing item, quantity or price ≤ 0, or a total over 2,149,631,130,647; the server re-checks everything. | client scripts 779, 780 |

## Implementation

Matching runs in the hub (`Exchange`). The world node moves coins and items only through item transactions.

The guide price is a volume-weighted moving average of completed trades, with history weight capped at 1,000 units.
An item that has never traded uses its cache `cost`. Worlds refresh the prices once a minute, and
`PlayerRef.objValue` uses them.

## OPEN

- **OPEN-GE1, guide price method.** Jagex updates the guide price daily from its own trade data, which is not public.
  The moving average is a stand-in.
- **OPEN-GE2, collection.** Our hub hands over everything in a slot at once (collect and abort), into the inventory
  (items over 1 come back noted); "collect to bank" and the note/item choice are not distinguished. An aborted offer is
  removed at once instead of showing status 5 until collected.
- **OPEN-GE3, Modify offer** is not implemented.
- **OPEN-GE4:** "Offer" on a backpack item from the index page opens a sell offer in the first free slot (normal OSRS
  behaviour, not proven from the scripts).
- **OPEN-GE5:** the custom ±N% buttons, the history panel, the sell fee display and prices above 2,147,483,647 (the hub
  stores Int prices) aren't implemented.
