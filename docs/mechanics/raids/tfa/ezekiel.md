# Ezekiel (room 2, option A)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Ezekiel", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- NPC 16652; combat 984; size 3; magic and melee; attack speed 5; max hit 77. Stats: att 250, str 150, def 200,
  magic 200, ranged 80, magic att +250. Defence: stab 150, slash 90, crush 35, magic 200, light 445, standard 370,
  heavy 320. Earth weakness 25%. Defence drain capped at 60% (floor 120).
- HP by players: 1-2: 3,000; 3: 4,350; 4: 5,610; 5: 6,870; 6: 8,130 (printed "8.130"); 7: 9,390; 8: 10,650.

## Fight

- **EZ1 tank:** the first player in the room, until they die; after a magic attack Ezekiel may mark another player
  as the tank. Melee cleaves almost 360°, safe tile directly behind him opposite the
  tank. If he cannot reach his target he casts magic at everyone.
- **EZ2 lightning:** every 2 melee attacks, a cross-shaped lightning AoE in two bursts 2 ticks apart (dodge diagonally
  or in an L). Then 3-4 Animated Swords spawn; a "perfect dodge" (in the sword's path, leaving on the same tick)
  restores 3% special energy; since 22 Oct the dodge works even if the player moved or attacked at range on the tick
  before. A sword hitting Ezekiel stuns him; a miss heals him (**OPEN-EZ1**: heal amount). Sword
  charge adds at most 80 damage.
- **EZ3 enrage (below 35% HP):** he moves to one side, players are forced into a 3×3, three more swords. He repeats a
  lightning pattern three times in a row; "this attack repeats on an 11T cycle" (**OPEN-EZ4**: whether 11 ticks covers
  one pattern or all three); dodging all three restores 9% special energy. Of the three safe-tile patterns, the middle
  one is never the last of a burst. He takes reduced damage in the enrage, increasing as his health falls (**OPEN-EZ5**). The patterns are
  cache dbtables 138-140 (`TFA_EZEKIEL_FINALE_*`: rotations 1-3, basic combos 1-4, attacks left/right/front with anim,
  hit delay, telegraph length, danger line, damage type, prayer) — **stripped**, so **OPEN-EZ2**: the patterns come from
  the wiki/Strat diagrams. The sword grid table (136, `TFA_SWORD_GRID_*`: horizontal/vertical centre, offset, flush,
  full) is also stripped (**OPEN-EZ3**).
- 28 Oct: unavoidable sword damage at the P2 transition fixed. 3 Nov: faster navigators; Obligator 3×3 fix in P2;
  enrage animation not overwritten by stun.

## Tick timeline (stated parts)

- Lightning bursts at T and T+2. Enrage lightning: three patterns in a row; 11-tick cycle (scope OPEN-EZ4).
