# Trading Post (custom feature)

Not an OSRS system: a server feature requested by the owner, modelled on the screenshots of another server's "Trading Post"
(D-070). Backend: `TradingPostService` (services), implemented in the hub (`hub/Hub.kt` `TradingPost`, tables `tp_listings`,
`tp_collect`, `tp_trades`), shared by every world. The interface is a packed custom interface (docs/plans: cache packing).

| # | Rule |
|---|---|
| TP1 | A player has at most 10 open listings ("Your listings n/10"). Listing takes the items from the backpack first (one item transaction), at a fixed price each. |
| TP2 | Any other player can buy part or all of a listing at its price at once: the world takes quantity x price coins first, the hub sells what is still there (never oversold: one lock) and the world refunds the rest. A changed price or a gone listing refunds everything. |
| TP3 | Coins from sales and the unsold rest of cancelled listings wait in the seller's collection box ("Collect"), taken in one transaction; what does not fit stays in the box. |
| TP4 | The listings view shows Item, Price ea, Seller ("You" for own) and Age (h:mm:ss since listed), searchable by item name or by player, sortable by each column; "Show all" clears the search. |
| TP5 | History lists the account's last 50 trades (bought or sold, item, quantity, price, other party). |
| TP6 | Untradeable items cannot be listed; prices are 1..2,147,483,647 and quantity x price must fit in the coin stack. |

- **OPEN-TP1** Tax, listing duration / expiry, and whether the seller is told when an item sells (a game message is sent when online).
