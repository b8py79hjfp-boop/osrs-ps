# Freezes

Sources: OSRS Wiki "Freeze" (FR, https://oldschool.runescape.wiki/w/Freeze), Ice Rush/Burst/Blitz/Barrage, Bind,
Snare, Entangle, Ancient sceptre, Zamorak godsword, Abyssal tentacle, Protect from Magic; raw wikitext 2026-11-06;
brief §8.

| # | Rule | Source |
|---|---|---|
| Z1 | Ice Rush 8, Ice Burst 16, Ice Blitz 24, Ice Barrage 32 ticks; Bind 8, Snare 16, Entangle 24. | FR:9, FR:62-82 |
| Z2 | After a freeze ends: 5 ticks of immunity (Grasp spells: 2). Immunity is a soft timer. | FR:9, brief §8 |
| Z3 | Ancient sceptre: +10% rounded down (Barrage 35). | Ancient_sceptre:127-137 |
| Z4 | Zamorak godsword spec: 32 ticks on a successful hit; abyssal tentacle spec: 8 ticks regardless of hit. | Zamorak_godsword:56, Abyssal_tentacle:60 |
| Z5 | PvP: the freeze only holds while both players are within 10 tiles, checking x and y only. | FR:13 |
| Z6 | Protect from Magic doesn't change freeze application or length (Bind/Snare/Entangle halving removed 2019). | Protect_from_Magic:27-42 |
| Z7 | Messages: "You have been frozen!"; near the end "The freeze cast upon you is beginning to thaw". | FR:38-92 |

## OPEN

- **OPEN-Z1:** when the 10-tile break is checked (every tick in the frozen player's turn, implemented) and whether a
  broken freeze starts immunity (implemented: yes).
- **OPEN-Z2:** when the freeze starts relative to the hit: applied on the application tick with the hit
  (brief golden 3d counts 32 ticks from there).
