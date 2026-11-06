# Trading

Code:
- `content/gameframe/.../Trade.kt`.
- Atomic cross-player moves: plugin-api `ItemTx.moveTo`.
- The partner's offer is sent as the client's `invother` (combined id below -70000, RSProt `UpdateInvFull`).

Source: OSRS Wiki "Trading" (https://oldschool.runescape.wiki/w/Trading). The rev 242 client scripts 763 / 765
(offer screen), 1216 / 1217 / 9913 (per-slot values) define which vars and inventories the screens read.

## Rules

- **TR1. Request.** "right-click the person and select the 'Trade with' option. The message 'Sending trade offer...'
  will appear in the chat box, and '[player name] wishes to trade with you.' will appear in the other player's chat
  box. That person must then accept the invitation by clicking on that message."
  - The request is chat type 101, which the client makes clickable.
  - A partner with an interface open or in another trade gets "Other player is busy at the moment." A request stays
    open for 100 ticks.
- **TR2. Offering.**
  - "Offer", "Offer-5", "Offer-10", "Offer-All" and "Offer-X" on the side grid; "Remove..." on your own offer.
  - "Players may trade only with what is in their inventory." Untradeable items are refused.
  - Offers sit in inv `tradeoffer` (90) and every move is one item transaction.
- **TR3. Removal warning.** "If an item is removed from an offer, a big red flashing exclamation point will appear
  where the item used to be". This uses varps `traderemoved` / `traderemoved_other` (slot bits) and varbits
  `trade_this_player_removed` / `trade_other_player_removed`.
- **TR4. Accept.**
  - Any change clears both accepts.
  - Status reads "Other player has accepted." / "Waiting for other player...".
  - Both sides must have room for what they receive.
- **TR5. Confirm screen.** "players are able to review which items have been offered ... the full value is shown":
  exact counts, or "Absolutely nothing!".
- **TR6. Completion.** Both offers change hands in one transaction: all of it happens, or none.
- **TR7. Decline.** "the player will be notified with the 'Other player declined trade.' message". Closing either
  screen, walking away or logging out declines, and the offers go back.
- **TR8. Crash safety.** Offers are a saved inventory, so anything left in it returns to the backpack at the next
  login.

Verified in the client with two accounts: offer, mirror, accept, confirm and completion.

## OPEN

- **OPEN-TR1.** The wording of the inventory-space refusals.
- **OPEN-TR2.** Offer values for the wealth display use the cache `cost`, as the price checker does (OPEN-Q2a). OSRS
  uses Grand Exchange guide prices.
- **OPEN-TR3.** The side grid's "Value" op is not offered (the grid binding takes five labels).
