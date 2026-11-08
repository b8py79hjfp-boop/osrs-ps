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
  before. A sword hitting Ezekiel stuns him; a miss heals him (**OPEN-EZ1**: heal amount) (settled by captures, see capture-values.md). Sword
  charge adds at most 80 damage.
- **EZ3 enrage (below 35% HP):** he moves to one side, players are forced into a 3×3, three more swords. He repeats a
  lightning pattern three times in a row; "this attack repeats on an 11T cycle" (**OPEN-EZ4**: whether 11 ticks covers
  one pattern or all three) (settled by captures, see capture-values.md); dodging all three restores 9% special energy. Of the three safe-tile patterns, the middle
  one is never the last of a burst. He takes reduced damage in the enrage, increasing as his health falls (**OPEN-EZ5**). The patterns are
  cache dbtables 138-140 (`TFA_EZEKIEL_FINALE_*`: rotations 1-3, basic combos 1-4, attacks left/right/front with anim,
  hit delay, telegraph length, danger line, damage type, prayer) — **stripped**, so **OPEN-EZ2** (settled by captures, see capture-values.md): the patterns come from
  the wiki/Strat diagrams. The sword grid table (136, `TFA_SWORD_GRID_*`: horizontal/vertical centre, offset, flush,
  full) is also stripped (**OPEN-EZ3**) (settled by captures, see capture-values.md).
- 28 Oct: unavoidable sword damage at the P2 transition fixed. 3 Nov: faster navigators; Obligator 3×3 fix in P2;
  enrage animation not overwritten by stun.

## Tick timeline (stated parts)

- Lightning bursts at T and T+2. Enrage lightning: three patterns in a row; 11-tick cycle (scope OPEN-EZ4).

## As implemented (content/combat TfaEzekiel.kt)

Numbers and rotations come from the live captures (capture-values.md), timings from RuneLite where it has them (D-080).
Positions are template tiles shifted to the instance: arena 9x9 (x 4059-4067, z 5531-5539), he spawns on the middle
cell (south-west tile (4062, 5534)), hunt range 20. HP 3,000 at 1-2 raiders, 4,350, 5,610, 6,870, 8,130, 9,390,
10,650 at 8.

- **One clock, EZ1/EZ2:** melee, melee (5 ticks apart), then the special (5 after a melee), then 7 to the next
  melee. Target = the target the Boss base engages (no re-tank after magic: the old 1-in-3 re-tank, OPEN-EZ9, is not
  implemented; capture shows none).
- **Cleave:** hits the 16-tile ring round his 3x3 except the three tiles opposite the tank (mirror of the tank
  through his centre plus the two neighbours on that side), and also a raider under his 3x3 (0..12). Raiders within 6
  tiles are scanned. Damage: unprayed 14..27, through Protect from Melee 1..9, under him 0..12. Lands on the cast tick.
- **Magic (tank out of reach):** hits every raider in the room 2 ticks after the cast, 4..21 (**OPEN-EZ6**: capture
  is 5 casts, 13 hits, prayers unread; partly settled by captures, see capture-values.md).
- **Star (lightning), EZ2:** ring of 16 tiles round the 3x3, arms 3 wide one tile further, the middle tile one further
  still, clipped to the 9x9. Hits every raider on a star tile at S+3 and at S+5 (S+5 also under his 3x3), 20..34
  typeless each beat (**OPEN-EZ7**).
- **Swords (right after the star, same tick), EZ2/EZ3:** one of five layouts rolled out of 42 (capture counts):
  cross 21 (one column x in {4059, 4062, 4065} and one row z in {5531, 5534, 5537}; never the middle cell
  (4062,5534) nor (4059,5531)), three rows from the west 9 (z 5531/5534/5537), three columns from the north 6
  (x 4059/4062/4065), two rows z 5532/5536 6. Lanes are 3 tiles wide. Sword NPC `TFA_EZEKIEL_SWORD` (80 HP).
  - Flight: each sword flies 15 ticks after it appears (RuneLite LU; **OPEN-EZ8**, capture 14), the three-row and
    three-column layouts one sword a tick later each. A raider on the lane takes charge + 27..31 typeless
    (one splat). Charge = wind-spell damage the sword took, capped at 80 (swords themselves take no damage).
  - Perfect dodge: standing on the lane the tick before the flight and off it when it lands gives +3% special energy
    (constant 30 passed to `TfaKit.addSpecEnergy`).
  - A sword whose lane covers his tile hits him for charge + 12..15 typeless and weakens him (below).
  - If a cross or the two-row layout has all flights landed without any hitting him, he heals a tenth of max HP
    (fly+4, heal anim at fly+2), capped at full; the three-row and three-column layouts never heal him
    (**OPEN-EZ1**, replaces the old 50).
- **Weakened (sword hit), EZ2:** idle-to-weaken anim at W, he is held still W..W+9, weaken-to-idle anim at W+8, no
  attacks until W+12, and the cycle restarts at melee (**OPEN-EZ8** stun length: 12-tick restart from captures).
- **Finale, EZ3:** when HP falls under 33.5% (checked after every hit; the wiki text above says 35%) he jumps to the
  arena cell next to the cage (the middle cell) on the side of the first raider in the room; every raider is moved
  into the 3x3 cage. F+6 seven inert fence swords round the cage, F+8 three inert V swords, first volley F+12
  (**OPEN-EZ10**: position chosen by nearest side, replaces the old 6 tiles south of spawn). The finale casts only
  volleys: no star, no sword layouts.
  - Volley: three strikes at V+4, V+5, V+6 (RuneLite agrees), cycle every 11 ticks (**OPEN-EZ4**, covers all three).
    Order is a fixed six-volley cycle, not rolled: B l r / B r l / r B l / l B r / B r l / B l r (**OPEN-EZ2**).
    B = both sides: only the far row of the cage is safe; l / r = front row plus that side's column: the far two
    tiles of the other column are safe. Everyone not on a safe tile is hit 6..19 typeless (**OPEN-EZ5**: damage is two
    samples; not settled).
  - Dodging a whole volley (no strike hit) gives +9% special energy at V+7 (constant 90).
  - Reduced damage in the finale (**OPEN-EZ5** model): damage taken x(0.5 + HP fraction).
- Death removes the swords and other minions.
