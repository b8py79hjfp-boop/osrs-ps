# The Dagon'hai (room 4, option A)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Dagon'hai Elder", "Dagon'hai Monk", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- **Elder** NPCs 16643/16644; **Monks** 16645/16646 (staff), 16647/16648 (book). Combat 427; size 2; magic and ranged;
  speed 6; HP 640 (cache: 7 once the shield is down); def 100, magic 290, ranged 260; magic att +250, ranged att +180.
  Defence 50/50/50, magic 100, ranged 80/80/80. Immune to cannon. Max hits **OPEN-DH1** (partly settled by captures, see capture-values.md).
- They share one health pool and base stats; defence reduction is per member; capped at 60% (floor 60) by the flag
  `tfa.dagonhai.defenceCap` (tfa.md OPEN-TFA14). Elder and Monks are poison and venom immune. Per-size HP **OPEN-DH2** (settled by captures, see capture-values.md).

## Fight

- **DH1** the cauldron moves around the centre; running into it shoves the player for up to 9 damage.
- **DH2** Elder: ranged (grey projectile) and magic (surge spells) hitting the target and players within 3 tiles.
- **DH3 ritual bar:** each successful Dagon'hai charge (a channel the players failed to disrupt) adds 25%; a full bar
  empowers all three. The fight opens with the Elder's elemental channel; players must be on the standard spellbook;
  splashing still interrupts.
- **DH4 elemental channel:** "I'll feed the flames!" (fire), "I'll guide the flow!" (water), "I'll focus the winds!"
  (air), "I'll strengthen the bond!" (earth) — interrupt with that element's spell; a stunned member takes 15% more
  damage.
- **DH5 tome empowerment:** Zamorak (~25 damage + fireballs), Bandos (up to 55 + shockwave), Saradomin (heals 10-20
  or 50 + lightning). Tomes cycle Zamorak > Bandos > Saradomin. Order: Monk N tome, Monk S channel, Elder channel, Monk S tome,
  Monk N channel, Elder channel.
- **DH6 shield breaks:** after each break they channel Zamorak to restore the shield (ritual stability −20%); portals
  with imps spawn (**OPEN-DH4** (settled by captures, see capture-values.md), source conflict: Strat says 2 for the first three breaks and 3 for the fourth, and
  also "a single portal" at team size ≤ 4 and two corners for larger teams, "dropping three or two imps each"); players always max-hit imps. The fifth break
  explodes the cauldron: one monk dies, the rest are stunned at 7 HP.
- 3 Nov: faster navigators; cauldron explosion bug fixed. **OPEN-DH3:** all tick timings. (settled by captures, see capture-values.md)

## As implemented (content/combat TfaDagonhai.kt: TfaDagonhai and TfaDagonhaiImp)

The cycle and numbers come from the live captures (capture-values.md), timings from RuneLite (D-080). The Elder and the
two Monks share one HP pool (every member shows the same hitpoints after each hit) and one shield: 640 at 1-2 raiders,
930, 1,200, 1,470, 1,730, 2,000, 2,270 at 8 (x1.45 at three, +0.42 a raider after, rounded to 10; **OPEN-DH2**). Hunt
range 20. The first call of any member builds the trio and the cauldron (focus NPC 16649, stepping round a radius-4
ring) and starts the cycle.

- **50-tick cycle (DH3, OPEN-DH3):** t0 a Monk channels; t13 the other Monk searches ("What have we here?"); t16 it
  calls the god; t21 the book strikes (the capture has it flying at t20); t23 the cauldron moves; t25 the first Monk
  channels again; t50 repeats, rotating which Monk is first each cycle. Only Monks channel (the Elder never does).
  The cycle stops once the trio are unshielded.
- **Channel (DH4):** the Monk yells one of the four chants at random (fire "I'll feed the flames!", water "I'll guide
  the flow!", air "I'll focus the winds!", earth "I'll strengthen the bond!"); it completes 18 ticks later for ritual
  +25 unless interrupted. A magic hit (a splash still counts) of that chant's element by spell name (Wind / Water / Earth
  / Fire) interrupts it ("Argh!") and stuns the Monk 11 ticks; a stunned member takes 115% damage.
- **God and book (DH5):** gods in turn Zamorak, Bandos, Saradomin ("Zamorak empowers!", "Bandosian strength!",
  "Saradominist zeal!"). At t21 the first raider on the lane (a floor line from the Monk to the cauldron) catches the
  book: the Monk says "Curse you!" and the god's effect hits the catcher. No catcher: it hits the cauldron, ritual +25.
  - Zamorak: catcher 23..27 magic, then 3 typeless every 4 ticks, five times; fire bolts telegraphed on every raider's
    tile hit 10 (magic) 3-5 ticks later if the raider is still on that tile (**OPEN-DH1**, RuneLite glyph delay; pool
    damage unrecorded).
  - Bandos: catcher takes 51 melee; a square ring spreads from the catcher's tile one tile a tick up to 8 tiles out,
    hitting each raider on the ring's tick for 23..29 (only if still at that ring distance) (**OPEN-DH7**).
  - Saradomin: the catcher is healed 13; 27 strike tiles at random within 6 of the centre, 4 ticks after the
    telegraph, hit a raider standing on them for 0..13 magic; 6 healing tiles, 5 ticks after the telegraph, heal any
    trio member under them by 20 (**OPEN-DH8**, replaces 50 / 20 lightning; the capture gives the catcher heal 13 and the
    27 / 6 tiles, not the tile heal).
- **Elder attack:** only the Elder attacks, every 5 ticks while not stunned or channelling: ranged or magic at random,
  the target and any raider within 1 of them, 0..30, 0 under the matching protection, lands 5 ticks later
  (**OPEN-DH1**). Monks have no normal attack.
- **Ritual and enrage:** ritual +25 per completed channel or uncaught book; each shield break takes 20 off (floor 0).
  At 100 the trio enrage: from 6 ticks later, every 5 ticks, two magic bolts of 15..22 at every raider (**OPEN-DH1**);
  ritual stops accruing.
- **Shield break (DH6, OPEN-DH4):** every break heals all three to full shield, the cauldron (1,000) loses 200, the
  Elder says "Zamorak, restore us!" and two rifts (16651, 20 HP) open at one site, 2 tiles apart, at a corner chosen by
  break number from (-7,7), (7,-7), (7,7), (-7,-7) round the centre. Imps (16650) hatch from each rift at +8, +11 and
  +14 (two a wave, six in all), within 1 tile of the rift; the rifts close at +19. Imp: 44 HP, swings every 4 ticks at
  a raider beside it for 0..39 (0 under Protect from Melee) and leaves after two swings (**OPEN-DH6**, replaces max 8).
- **Fifth break:** all three become the unshielded types (7 HP), stunned 20 ticks, and the cauldron is removed with a
  lightning effect; none die from the explosion (3 Nov).
- **Cauldron (DH1):** each cycle at t23 it steps to the next of eight ring tiles round the centre; a raider under it
  is shoved 2 tiles away and takes 0..9 typeless.
