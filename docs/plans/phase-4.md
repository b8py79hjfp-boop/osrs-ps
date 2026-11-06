# Phase 4 — Combat and PvP

## Scope (brief §8, §9 test 3)

Everything in brief §8. **Done when** golden scenarios 3a–3h pass, the DPS-calculator parity matrix passes, and a
1v1 plus a 20-player multi fight in RuneLite time correctly in the tick inspector.

## Architecture

- **Engine (main session):** attack clock, hit queue (strong queue on the defender, delay = hit delay; the "+1 when
  the defender processes first" falls out of queue order, it is not added by hand), visuals on the protocol seam
  (animations, spot anims, hitsplats, head bars, face entity, projectiles), consumption clocks, freeze state with its
  distance break, combat logout lock, death hook. All world-scoped, deterministic, seeded RNG only.
- **Combat rules (main session, `content/combat` + `content/pvp`):** accuracy/max-hit formulas, weapon styles from
  cache enums/structs/params, prayers and drain, food/potions, specs as data plus hooks, freezes, vengeance/recoil,
  Smite/Redemption/Retribution, wilderness/skull/PJ/multi, Tele Block, death and items kept on death.
- **plugin-api** gains combat facades: `CombatRef`, hit events, equipment, prayer state; content stays on plugin-api.
- **Data:** equipment bonuses, attack speed and weapon category from obj params / enums / structs in the cache
  (param ids documented in docs/mechanics/combat-data.md). wiki-import only where the cache lacks data.

## Docs

`docs/mechanics/combat-timeline.md` (roll tick, protection-check tick, application tick per attack type, with sources),
`combat-formulas.md`, `prayers.md`, `consumables.md`, `freeze.md`, `wilderness.md`, `death.md`. Unverified → OPEN.
Verifier pass on each before the golden tests are written against them.

## Order of work

1. Research notes from the Wiki (downloaded raw) and RS Mod; mechanics docs; verifier pass.
2. Protocol seam for combat visuals; equipment (wield/remove, appearance, bonuses from cache).
3. Combat core: attack interaction, attack clock, hit delay, hit queue, formulas, hitsplats, retaliation, death.
4. Consumables and clocks; prayers + drain; freezes; specs; switches.
5. Golden scenarios 3a–3h on tools/scenario.
6. PvP world rules: wilderness levels/brackets, skull/PJ timers, single/multi (area queue), safe zones, logout lock,
   items kept on death, Protect Item, Tele Block, vengeance, recoil, Smite/Redemption/Retribution, projectiles.
7. DPS parity matrix; 1v1 + 20-bot multi fight checked in the tick inspector with RuneLite.

## Risks

| Risk | Mitigation |
|---|---|
| Wiki pages disagree or are vague on tick-level timing | combat-timeline.md marks OPEN with both quotes; config flag for the plausible choice |
| Cache lacks some weapon data (e.g. hit delay class) | data table in content with per-row source; wiki-import later |
| Visual timing (projectile/spotanim delays in client cycles) | derive from hit delay per RS Mod/RSProt docs; checked in RuneLite |
