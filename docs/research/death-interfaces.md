# Grave and Death's Office interfaces (client-script research, rev 242)

Research input for OPEN-DE10 (docs/mechanics/death.md). Script ids refer to the decompiled client scripts; nothing
here is verified in a real client.

| Interface | Init script | Inv | Ops |
|---|---|---|---|
| GRAVESTONE_RETRIEVAL 602 | 1985 `gravestone_retrieval_init` (12 int args) | 525 gravestone | items: op2 Take-All, op9 Value, op10 Examine (IF_BUTTONX, sub = slot); buttons op1 Unlock / Take-All, op1 Bank-All, op1 Discard-All |
| DEATH_OFFICE 669 | 3490 `death_office_init` (10 int) | 636 death_permanent | items: op1 Select, op10 Examine; buttons 1 / 5 / X / All / Take-All |
| DEATH_COFFER 670 | 3479 `death_coffer_init` (8 int) | 93 inv | buttons 1 / 5 / X / All / Confirm |
| GRAVESTONE_GENERIC 672 | 3462 `gravestone_generic_init` (18 int) | 525 | items: op1 Take, op10 Examine; button Take-All |

- Grave timer HUD: varbit 10465 `gravestone_duration` in ticks (script 3459 counts it down client-side, shows "---"
  above 1500, hidden at 0 or with varbit 12139); varp 1697 is its transmit trigger.
- Fee display (script 3464): varbits 10472-10474 are slot-index thresholds (stored +1) splitting free and fee slots;
  tiers ×1,000 / ×10,000 / ×100,000 per count, cap 500,000 through script 1797 (ironman ×½, boss-area discounts ×¼).
- Coffer total: varp 261 (scripts 3481, 3492); varps 262 / 263 / 264 = selected slot / quantity / threshold in 670;
  varp 263 is also the per-item fee in 669. Varcs 399 / 400 carry fee and coffer for 672 (script 3478).
- The grave NPC families: basic 9856 + 9858..10112, angel 9857 + 10113..10367 (what selects one of the 256 is not
  known). Chat messages are all server-side.
- Unknown: the init scripts' argument roles (component ids), who opens the windows and where, the sacrifice op.
