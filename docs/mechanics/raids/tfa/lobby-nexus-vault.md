# Lobby, nexus and Void vault

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "The Fractured Archive", "Chest (The Fractured Archive)", "Quartermaster (The Fractured Archive)", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

Non-combat rooms (implementer work once raids-core interfaces exist).

- **Lobby** (square 64, 86): Grouping Board (4138, 5547, 0, east), Strategy Board (west, spectating), records board,
  loot wheelbarrow, Ascension rocks (mining; Rock Golem 1/68,120), General Montoya, the Record
  Keeper, bank (3 Nov: re-open fix), raid door (4123, 5562, 0), exit (4126, 5505, 0). tfa.md TFA4, TFA17,
  TFA24.
- **Nexus / selection room** (square 64, 87): the west and east doors per row (cache dbtables 260 `TFA_NEXUS_DOORS`
  with closed / open / slightly-open loc ids — stripped, so door loc ids come from the map and Locs gamevals,
  **OPEN-NX1**); the Quartermaster (Liam) after room 3. Room entry tiles: dbtable 259 (stripped, **OPEN-NX2**: read from the
  map / observation).
- **Void vault** (square 64, 90): entrance portal (4127, 5772, 2); eight chest NPCs 16736-16751 morph on
  `TFA_VAULT_OWN_CHEST`; braziers lit before chests appear (3 Nov); purple braziers + northern unique chest
  (16730 → 16731) on a unique; standard chests: own 16733 → rift 16735, other players' 16732 → rift 16734. **OPEN-NX3:** the wiki
  map shows the vault on plane 1, the cache portal on plane 2. tfa.md TFA24.

## Flow as implemented (content/raids-tfa TfaFlow, TfaVault)

| # | Rule | Source |
|---|---|---|
| NX-F1 | The leader enters the Archive Door first: the run starts (scaling locked at max(2, party size), TFA9), the instance attaches next tick (IN6) and the leader stands before the Guardian. Other members enter through the same door while the run is still in its first room. | TFA4; framework RC2 |
| NX-F2 | The Inert Guardian (16657, size 5) waits in the nexus with its south-west tile at (4125, 5597), where the cache puts the guardian corpse on L2/L3. | cache map |
| NX-F3 | After a room ends: contribution split, prayers off (TFA15a), jingle `TFA_BOSS_DEFEATED`; the nexus offers the next section's west/east doors (Enter and Quick-enter both pick the room and move the party). | TFA6, TFA15a |
| NX-F4 | A side room's passageway (62765 "Exit") and transporter (63087 / 62610 / 63134, "Use" / "Quick-use") return the player to the nexus once the room is done. | cache ops |
| NX-F5 | After room 3 the Quartermaster 16761 stands by the supply wheelbarrow; after room 4 the passage 62766 to Ocellus exists; after Ocellus the Blade 16683 ("Commune", "Travel") carries the party to the vault. | TFA7; cache |
| NX-F6 | Entering the vault completes the raid (rewards once, KC, time). After `tp.tfa.brazierTicks` the chests appear: chest k = NPC 16736 + 2(k-1) at the k-th ring tile, plus the unique chest 16730 when someone got a unique. The own chest "Open" turns it into its OPEN type (the owner's rift) and opens interface 973 on `TFA_CHESTS`. Rift 16690 "Leave" / "Quick-leave" leaves to the lobby door. | TFA24; client-contract 2.6 |
| NX-F7 | Loot interface 973 (`TfaChestUi`): `TFA_CHESTS` (inv 1029) is bound to `973:11` with client script 149 (labels Take / Bank / Discard = ops 1-3). Buttons: BANK "Bank-all", INVENTORY "Take-all", DISCARD "Discard-all" (asked first). Each stack moves in its own item transaction: take = `move` to the backpack ("You don't have enough inventory space."), bank = `removeAt` + `add(Invs.BANK, unnoted obj)` without a bank open (items with obj param 59 refuse), discard = `removeAt` after a Yes/No menu. `TFA_HAS_CLAIMABLE_LOOT` is 1 exactly while the inv holds something (set on vault entry, after every op, on login); the lobby chest loc 63299 morphs 63300 (closed, no ops) / 63301 (open, op1 "Search" opens 973). | TFA24; client-contract 1.5, 2.6 |

OPEN items added with the flow:
- **OPEN-NX4** — nexus states: the square's four level copies are read as L0 = start, L2 = Guardian dead (corpse 62800,
  supply chest and wheelbarrow), L3 = also the passage 62766 to Ocellus (and the records room, which lives on L1/L3).
  The instance copies L0 only and swaps in L2 after the Guardian and L3 after room 4. [I]
- **OPEN-NX5** — whether one door click moves the whole party (implemented) or each player walks through.
- **OPEN-NX6** — vault chest ring, unique chest and rift positions (no source gives the layout); the vault is walkable on
  level 2 (the portal's level), which settles OPEN-NX3 for collision.
- **OPEN-NX7** — the brazier delay before the chests (flag `tp.tfa.brazierTicks`, default 5).
- **OPEN-NX8** — refusal texts (no party, leader first, sealed door, inactive transporter) and the Blade's Commune text.
- **OPEN-TFA15** — While Guthix Sleeps is not on this server, so the TFA1 requirement is behind `tp.tfa.requireWgs`
  (default off) and the outside opening's varbit is set on login.
