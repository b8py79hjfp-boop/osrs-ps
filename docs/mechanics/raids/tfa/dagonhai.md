# The Dagon'hai (room 4, option A)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Dagon'hai Elder", "Dagon'hai Monk", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- **Elder** NPCs 16643/16644; **Monks** 16645/16646 (staff), 16647/16648 (book). Combat 427; size 2; magic and ranged;
  speed 6; HP 640 (cache: 7 once the shield is down); def 100, magic 290, ranged 260; magic att +250, ranged att +180.
  Defence 50/50/50, magic 100, ranged 80/80/80. Immune to cannon. Max hits **OPEN-DH1**.
- They share one health pool and base stats; defence reduction is per member; capped at 60% (floor 60) by the flag
  `tfa.dagonhai.defenceCap` (tfa.md OPEN-TFA14). Elder and Monks are poison and venom immune. Per-size HP **OPEN-DH2**.

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
  with imps spawn (**OPEN-DH4**, source conflict: Strat says 2 for the first three breaks and 3 for the fourth, and
  also "a single portal" at team size ≤ 4 and two corners for larger teams, "dropping three or two imps each"); players always max-hit imps. The fifth break
  explodes the cauldron: one monk dies, the rest are stunned at 7 HP.
- 3 Nov: faster navigators; cauldron explosion bug fixed. **OPEN-DH3:** all tick timings.

## As implemented (content/combat TfaDagonhai.kt)

- One shield pool (640 at 1-2 players, other sizes by Ezekiel's ratios, OPEN-DH2) mirrored on all three; five breaks,
  each restoring the shield (ritual -20%); the fifth turns them into the 7-HP unshielded types, kills a monk and stuns
  the rest for 20 ticks. Members take turns: a 6-tick channel (one of the four DH4 chants; any magic hit interrupts it
  and stuns the channeller for 5 ticks, +15% damage taken; OPEN-DH5: the element is not checked) or a tome (Zamorak
  25 + 10 around the target, Bandos up to 55, Saradomin heals 10-20). An uninterrupted channel adds 25% to the ritual;
  a full bar empowers all three (x1.5 for 30 ticks). Normal attacks: Elder 30, Monks 25 (OPEN-DH1); the Elder's hit
  the target and players within 3 tiles. The cauldron (DH1) and the imp portals (OPEN-DH4) are not modelled.
