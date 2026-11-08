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
