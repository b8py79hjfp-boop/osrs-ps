# NPC shops

Code: `content/shops/.../{Shops,ShopStock,ShopCatalog}.kt`. Data: `shops.tsv` and `shop-items.tsv` from
`tools/wiki-import/shops.py` (428 shops, 4,377 items; OSRS Wiki `{{Infobox Shop}}` / `{{StoreLine}}`, CC BY-NC-SA 3.0).
Behaviour follows the Wiki and RS Mod `api/shops` (ISC, DECISIONS D-032).

Sources:
- OSRS Wiki "Shop" (https://oldschool.runescape.wiki/w/Shop), "General store" (https://oldschool.runescape.wiki/w/General_store)
- Module:Shop calculator (https://oldschool.runescape.wiki/w/Module:Shop_calculator)
- Module:StoreLine (https://oldschool.runescape.wiki/w/Module:StoreLine)
- Module:StoreTableHead (https://oldschool.runescape.wiki/w/Module:StoreTableHead)
- rev 242 client scripts 1074, 1075, 1076, 2228, 2230, 2231
- RS Mod `api/shops` (https://github.com/rsmod/rsmod)

## Rules

| # | Rule | Source |
|---|---|---|
| SH1 | The "Trade" op of an owner NPC opens the shop. The owner ids are the cache ids of the NPC pages linked in the infobox `owner` field that carry a "Trade" op. A "Trade" NPC without a shop says "This shop isn't available yet." | Wiki Infobox Shop; cache npc ops |
| SH2 | `shopmain` (300) is the main modal and `shopside` (301) goes over the backpack. Closing it, however it closes, puts the backpack back (side slot 3 and its item ops). | client scripts; RS Mod `Shops.open` |
| SH3 | The shop contents are an inv the client binds to (`shop` inv ids of the cache; the cache size is the slot count). The server sends it as a display inventory with `showInv`, then runs client script 1074 with (inv id, title, custom buy obj -1, custom amount 0, "Buy 50" shown 1), then `interface_inv_init` (149) binds the backpack to `shopside:items` with the labels "Value", "Sell 1", "Sell 5", "Sell 10", "Sell 50". | client scripts 1074, 1076; RS Mod `ClientScripts.shopMainInit` |
| SH4 | Shop item buttons are children `1..slots` of `shopmain:items` (script 1076 `cc_find` index = slot + 1); the packet's slot is that child. Ops: 1 = "Value" or, once a quantity is chosen, "Buy N"; 2..5 = Buy 1/5/10/50; 6 = "Value" while op 1 is a purchase; 10 = Examine. Backpack item ops: 1 Value, 2..5 Sell 1/5/10/50, 10 Examine. | client scripts 1074, 1076, 2228 |
| SH5 | The quantity buttons of the item panel (`shopmain:value`, `quantity1/5/10/50`) set varbit `shop_quantity` (6348) to 0..4. The client script 2231 sets it on the client at once and the click also reaches the server; the server keeps the same value because op 1's meaning depends on it. | client scripts 2228, 2230, 2231 |
| SH6 | Buying: "Players may buy any item in stock from a shop for a fixed value ... This value will increase by the same fixed percentage for each stock decrease". The price of one unit is `value * (sellmultiplier + delta * (baseStock - stock)) / 1000`, rounded down, at least 30% of the value (Wiki: "reaches 30% of the item's value") and at least 1; the Wiki calculator also caps it at `value * (sellmultiplier + 500%)`. A Wiki row with a fixed `sell` price is that price. Each unit of a bulk purchase is priced at the stock left after the previous one. | Wiki Shop (Pricing), General store (Player buy prices), Module:Shop calculator `buy`; RS Mod `calculateShopSellSingleValue` |
| SH7 | Selling: the price of one unit is `value * (buymultiplier - delta * (stock - baseStock)) / 1000`, rounded down, "a minimum of 10% of the item's value", at most `value * (1 + buymultiplier)`. Base stock is 0 for items that are not part of the regular stock. Each unit of a bulk sale is priced at the stock after the previous one. A Wiki row with a fixed `buy` price is that price. | Wiki Shop (Pricing), General store (Player sell prices), Module:Shop calculator `sell`; RS Mod `calculateShopBuySingleValue` |
| SH8 | `value` is the cache `cost` ("value" of the item, the low/high alchemy base). `sellmultiplier`, `buymultiplier` and `delta` are the `{{StoreTableHead}}` parameters in per mille of the value (Lumbridge General Store: 1300 / 400 / 30; delta 30 = 3% per unit). Worked example from the Wiki (Bob's Brilliant Axes, 1000 / 600 / 20, base 3 steel axes, value 200): sells for 120 with 3 in stock, 116 with 4; costs 200 with 3, 196 with 4. | Wiki Shop (Pricing example); Module:StoreTableHead |
| SH9 | A general store accepts any tradeable item; a specialist only the items it stocks (noted items are sold as their unnoted form). Coins and the shop's currency are never accepted. Messages: "You can't sell this item." (untradeable), "You can't sell this item to this shop.". | Wiki General store; RS Mod `invSell` |
| SH10 | Stock. Each shop item has a base stock. "Whenever a shop has less of a certain item than their maximum capacity of that item, the shop will start to restock on that item ... Only a single item will be re-added to the shop per restock." The restock time is the StoreLine `restock` in ticks. Items "that aren't part of the shop's regular stock, or exceed their maximum capacity, will slowly be removed": overstock above the base falls by one per restock time, and items not in the regular stock "destock at a rate of one per minute" (100 ticks) and leave the shop at 0. Default items stay in their slot at count 0. The clock is the world tick (an item changes when `tick / rate` advances, RS Mod `ShopRestockProcess`); steps are applied lazily when the stock is next looked at, which gives the same result. | Wiki Shop (Stock), General store (Anything goes); RS Mod `ShopRestockProcess` |
| SH11 | Stock is per world and shared by all players (Wiki: "Stock at any given shop always varies between worlds"). Clients with the shop open get the new stock within a tick. | Wiki Shop |
| SH12 | Buying stops at the stock, at the coins in the backpack and at the room in the backpack (free slots for loose items, one slot or a stack for stackables); the purchase is one transaction (coins out, items in). A short purchase says "You don't have enough coins." or "You don't have enough inventory space."; an empty shop says "That item is currently out of stock.". Other currencies read "You don't have enough <currency>.". | RS Mod `shopBuy` |
| SH13 | Value messages: `<item>: currently costs N coins.` (`1 coin`) and `<item>: shop will buy for N coins.` Other currencies use the currency's name. | RS Mod `examineShopValue`, `examineInvValue` |
| SH14 | Hostile input: shop buttons only act while the shop is open; the slot must hold the obj the client names; backpack buttons must name the obj in that slot; any other op is ignored. | brief §12 |

## Import (`tools/wiki-import/shops.py`)

- Every page using `{{Infobox Shop}}` (460). The first `{{StoreTableHead}}` table is the stock; wrapper rows such as
  `{{Tzhaar shop row}}` pass the same parameters.
- Items and currencies are matched to cache objs by name (not noted, not a placeholder, tradeable first, lowest id).
- The client inv id is the shop-named cache inv that shares the most words with the shop's name and `special` and has
  enough slots, else the smallest generic `...SHOP` inv (general stores: 40 slots). It is only the id the client binds to.
- Result: 428 shops, 4,377 items, 394 owner NPC ids.
  - 46 shops have no "Trade" NPC (bartenders, stalls and dialogue shops).
  - 12 NPC ids are claimed by two shops (the first row wins).
  - Skipped: 32 pages (7 without a stock table, 23 with a points currency, 2 with no usable item).
  - 218 StoreLine names match no cache obj (mostly colour variants such as `Skirt (lilac)` and `Decorative armour (red platebody)`).

## OPEN

- **OPEN-SH1.** Wiki sources disagree for an overstocked item's buying price: Module:Shop calculator divides the value by
  `stockDelta * delta + sellmultiplier`, the "General store" page says `Value * (130 - 3 * Stock) / 100`. The linear form
  (also RS Mod) is used. They agree at the base stock and one unit above it.
- **OPEN-SH2.** Pages with several stock tables (39): only the first is used. Karamja gloves prices (Tzhaar and Jiminua
  shops), ironman / group ironman separate stock (Wiki Shop: "Many shops have separate stock between Main accounts and
  Ironmen"), cape-of-accomplishment variants and shops that share stock ("Void Knight General Store") are not modelled.
- **OPEN-SH3.** A StoreLine without a restock time (399 items) restocks one unit per 100 ticks, like destocking.
- **OPEN-SH4.** Infinite-stock items (`stock=inf`, "The Flaming Arrow", "Farming Supplies") show and sell at most 10 at a
  time and never run out; the shown count is a guess.
- **OPEN-SH5.** The message of a full shop (a general store with every slot used) has no source. "The shop is too full to buy
  that item." is used and nothing is sold.
- **OPEN-SH6.** Messages come from RS Mod and memory, not from a cited OSRS text; the Wiki lists none. Plural handling of
  other currencies ("1 tokkul") is ours.
- **OPEN-SH7.** Shops that are opened from dialogue ("Could I buy a beer?", stalls, Trader Stan) have no "Trade" op and are
  waiting for the dialogue system. Points shops (23: Slayer, Soul Wars, ...) are not imported.
- **OPEN-SH8.** The Examine op does nothing (no examine texts exist yet).
- **OPEN-SH9.** The note/"Buy X" custom-amount button (`shopmain:note_button`, script 2461) and per-shop "Buy 50" disabling
  are not used; every shop shows Buy 50.
- **OPEN-SH10.** A shop does not close when the player walks away from the NPC unless the engine closes the modal itself.
