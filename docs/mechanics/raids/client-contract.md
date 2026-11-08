# Raids: client contract

What the client reads, so the server produces exactly that state. One file per raid; this page holds the shared rules
(brief §2).

- **Templates:** only official template zones or real regions (RuneLite's raid and loot plugins identify rooms from
  them).
- **Interfaces and inventories:** our generated symbols (`tp.symbols.Interfaces`, `Invs`).
- **Varps / varbits:** every encoding comes from the client scripts that read it; values no script reads are marked
  `OPEN` and get a named config flag. `TOTAL_COMPLETED_*` and `*_PERM_*` persist; `*_TEMP_*` reset.
- **Chat messages:** character for character including colour tags; RuneLite's captured strings are the fixtures;
  wiki-only strings are `OPEN` until seen in game.
- **Loot Tracker:** chest inventories (and for ToA its varbits) are set before the chest interface opens.
- **Licensing:** no RuneLite code in the server; RuneLite data used as fixtures is BSD-2, noted in `data/PROVENANCE.md`.

Per raid: [tfa/client-contract.md](tfa/client-contract.md).
