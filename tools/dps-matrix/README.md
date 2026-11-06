# DPS-calculator parity matrix

`content/combat/src/test/resources/dps-matrix.json` holds max hits and max attack rolls recorded from the OSRS Wiki
DPS calculator (https://github.com/weirdgloop/osrs-dps-calc, the engine behind tools.runescape.wiki/osrs-dps) for a
grid of gear, levels, prayers and stances against an Abyssal demon. `content/combat`'s `DpsParityTest` recomputes every
row with our formulas and cache bonuses (brief §8, Phase 4 done-when).

Regenerate: clone the calculator, copy `Matrix.test.ts` to `src/tests/tp/`, install with its bundled Yarn
(`node .yarn/releases/yarn-4.9.2.cjs install --mode=skip-build`), run
`node .yarn/releases/yarn-4.9.2.cjs jest src/tests/tp/Matrix.test.ts`; it writes the JSON (path in the script).
Only the numbers are used (no calculator code is copied). Recorded from commit in DECISIONS D-024.
