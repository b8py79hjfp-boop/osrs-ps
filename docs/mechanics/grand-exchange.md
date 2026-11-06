# Grand Exchange (skeleton)

| # | Rule | Source |
|---|---|---|
| GE1 | A new offer matches the oldest compatible opposite offers. The trade happens at the price of the offer placed first, and a buyer gets the difference back in coins. | OSRS Wiki "Grand Exchange" |
| GE2 | Each item has a guide price that drives value-based features: items kept on death ordering, the price checker, the risk shown on Items kept on death. | OSRS Wiki "Grand Exchange#Guide price" |
| GE3 | Offers have 8 slots. Coins (buy) or items (sell) are taken when the offer is placed and come back through the collection box. | OSRS Wiki "Grand Exchange" |

## Implementation

Matching runs in the hub (`Exchange`). The world node moves coins and items only through item transactions.

The guide price is a volume-weighted moving average of completed trades, with history weight capped at 1,000 units.
An item that has never traded uses its cache `cost`. Worlds refresh the prices once a minute, and
`PlayerRef.objValue` uses them.

## OPEN

- **OPEN-GE1, guide price method.** Jagex updates the guide price daily from its own trade data, which is not public.
  The moving average is a stand-in.
- **OPEN-GE2, GE interface.** There is no Grand Exchange interface yet; offers are placed with `::gebuy` / `::gesell`.
