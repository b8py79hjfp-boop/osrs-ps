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
| GE12 | History: "now extends to 40 previous listings". Each finished offer, or aborted offer that traded something, is a row (obj, bought/sold, amount, coins); panel 383 is filled by client scripts 1644/1645/1646. Coins are the actual trade prices (`gold`), which also drives the slot's spent/received figure. | OSRS Wiki "Grand Exchange" changes; client scripts 1644–1646 |
| GE13 | Abort: the offer stops trading, its unfilled part (coins for a buy, items for a sell) moves to the collect box, and the slot shows as finished (status 5, completed < total) until everything is collected. | client `na`, RuneLite `GrandExchangeOfferState.CANCELLED_*` |
| GE14 | Tax: "Most transactions ... are subject to a 2% tax ... capped at a maximum of 5 million coins per item", rounded down, paid by the seller; exempt items (bonds, low-level food, ammo, tools, teleports: Wiki list) pay none. The client shows the fee from varbit `ge_transmit_taxrate` in tenths of a percent (client script 5731: 20 = "2%"; script 779: fee = min(5,000,000, price × rate / 1000)). | OSRS Wiki "Grand Exchange#Convenience fee and item sink"; client scripts 779, 5731 |
| GE15 | Collecting (offer details, collect-all, collection box 402): item with a noted form, count 1: op 1 item, op 2 note; count > 1: op 1 notes, op 2 items; no noted form: op 2; op 3 "Bank". "Repeat Offer" re-creates the last offer; a history row's "Buy-offer" starts a buy offer for that item. | client scripts 141, 788, 792, 796, 816, 818, 1645, 9909 |
| GE16 | **Clerks are reached across the desk.** Exchange, History and Collect on a Grand Exchange Clerk work from up to 2 tiles away in line of sight, like bankers behind booths. | bank.md B20 (RS Mod Banker distance 2); seen in the client: "I can't reach that!" before |
| GE11 | Confirm is disabled by the client for a missing item, quantity or price ≤ 0, or a total over 2,149,631,130,647; the server re-checks everything. | client scripts 779, 780 |

## Implementation

Matching runs in the hub (`Exchange`). The world node moves coins and items only through item transactions.

The guide price is a volume-weighted moving average of completed trades, with history weight capped at 1,000 units.
An item that has never traded uses its cache `cost`. Worlds refresh the prices once a minute, and
`PlayerRef.objValue` uses them.

## OPEN

- **OPEN-GE1, guide price method.** Jagex updates the guide price daily from its own trade data, which is not public.
  The moving average is a stand-in.
- **OPEN-GE2, collection.** What doesn't fit the backpack is dropped under the player (private to them) rather than
  refused, so a collect never loses items (our choice; Jagex refuses with a message).
- **OPEN-GE3, Modify offer** (no source for its server behaviour): opens the setup screen filled from the offer
  (remaining quantity, price); Confirm aborts the old offer (everything comes back) and places the new one in the same
  slot.
- **OPEN-GE4:** "Offer" on a backpack item from the index page opens a sell offer in the first free slot (normal OSRS
  behaviour, not proven from the scripts).
- **OPEN-GE5:** custom ±N%: "Customise" asks for N (stored in varbit `ge_price_custom`, limited to 1..100 here); a click
  applies price × N / 100 (at least 1), like the ±5% buttons. Prices per item are limited to 2,147,483,647 (a coin stack's
  maximum; the hub stores Int prices).
