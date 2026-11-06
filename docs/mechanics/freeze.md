# Freezes

Sources: OSRS Wiki "Freeze" (FR, https://oldschool.runescape.wiki/w/Freeze), Ice Rush/Burst/Blitz/Barrage, Bind,
Snare, Entangle, Ancient sceptre, Zamorak godsword, Abyssal tentacle, Protect from Magic; raw wikitext 2026-11-06;
brief §8.

| # | Rule | Source |
|---|---|---|
| Z1 | Ice Rush 8, Ice Burst 16, Ice Blitz 24, Ice Barrage 32 ticks; Bind 8, Snare 16, Entangle 24. | FR:9, FR:62-82 |
| Z2 | After a freeze ends: 5 ticks of immunity (Grasp spells: 2). Immunity is a soft timer. | FR:9, brief §8 |
| Z3 | Ancient sceptre: +10% rounded down (Barrage 35). | Ancient_sceptre:127-137 |
| Z4 | Zamorak godsword spec: 32 ticks on a successful hit (SA:2566 says "20 seconds" ≈ 33; minor SOURCE-CONFLICT, using 32); abyssal tentacle spec: 8 ticks regardless of hit. | Zamorak_godsword:56, Abyssal_tentacle:60 |
| Z5 | PvP: the freeze only holds while both players are within 10 tiles, checking x and y only. | FR:13 |
| Z6 | Protect from Magic doesn't change freeze application or length (Bind/Snare/Entangle halving removed 2019). | Protect_from_Magic:27-42 |
| Z7 | Ice spells: "You have been frozen!" on applying, "The freeze cast upon you is beginning to thaw" near the end. Bind/Snare/Entangle: no message on applying, "The holding effect is beginning to fade." near the end. | FR:38-92 |

## Timeline (ours, for golden 3d)

A freeze of N ticks applied on tick A (the hit's application tick) blocks movement on ticks A .. A+N−1; immunity runs
A+N .. A+N+4 (5 ticks); a new freeze can land from A+N+5. **OPEN-Z3:** the off-by-one at both ends is unsourced.

## OPEN

- **OPEN-Z1:** FR:13 says the freeze only *restricts movement* while both players are within 10 tiles; it doesn't say
  the freeze ends. Implemented: checked every tick in the frozen player's turn; beyond 10 tiles (Chebyshev on x/z,
  > 10) the freeze ends and immunity starts (brief §9 3d: "the freeze breaks"). Exactly 10 counts as inside.
- **OPEN-Z2:** when the freeze starts relative to the hit: applied on the application tick with the hit
  (brief golden 3d counts 32 ticks from there).
