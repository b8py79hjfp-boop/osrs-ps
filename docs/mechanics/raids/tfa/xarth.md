# Xarth (room 4, option B)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Xarth", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- NPC 16684; combat 912; size 3; magic, ranged, melee; attack speed 4; max hit **OPEN-XA1** (partly settled by captures, see capture-values.md); demon (demonbane
  resistance 20%). Stats: att 250, str 225, def 170, magic 350, ranged 220; melee att +150 (str +30), magic att +250,
  ranged att +250 (str +30). Defence: stab 115, slash 105, crush 20, magic 205, light 125, standard 230, heavy 155.
  Water weakness 85%; immune to cannon. Defence drain capped at 60% (floor 102); offensive capped at 50%.
  HP: 1-2: 2,600; 3: 3,770; 4: 4,860; 5: 5,950; 6: 7,050; 7: 8,140; 8: 9,230.

## Fight

- **XA1 phase 1 (magic):** targets the closest player; hits stack burn. After 5 magic attacks: binds everyone, two
  searing projectiles, then a charged explosion; hide behind a pillar; a player acting as the pillar takes ~45 and those behind ~7. The shield
  goes after the first explosion.
- **XA2 phase 2 (pillars gone):** he launches flames into the arena that periodically spread to adjacent tiles; ranged (damage on impact, Protect from Missiles) and
  melee, static. Corpses spawn at the edges, walk to him and heal him for their remaining HP; freezes, water spells and
  Undead Grasp (24 ticks under Mark of Darkness) stop them; fire spells make more. Two corpses healing him in a short window trigger an
  arena-wide blast (**OPEN-XA2**: window and damage) (partly settled by captures, see capture-values.md).
- 3 Nov: faster navigators; ranged sound quieter. **OPEN-XA3:** tick timings. (settled by captures, see capture-values.md)

## As implemented (content/combat TfaXarth.kt: TfaXarth and TfaXarthCorpse)

Cycle, counts and damage come from the live captures (capture-values.md), the 4-tick attack clock from RuneLite
(D-080). HP by raiders 2..8: 2,600 / 3,770 / 4,860 / 5,950 / 7,050 / 8,140 / 9,230. Hunt range 20. He spawns at
(4190, 5534) (raids-tfa TfaBosses.kt). His first swing or the first hit on him engages him; from then on a fixed
script runs every tick and the normal attack clock only re-checks every 5 ticks.

- **Pillars (XA1, OPEN-XA9):** three 2x2 rubble NPCs (16685) at (6,2), (3,6), (3,-4) from his south-west tile
  (replaces four diagonals). The fall turns one into the destroyed type (16686).
- **Phase 1, three fixed 31-tick cycles (OPEN-XA3):** C = 6 ticks after engaging, the next C 31 after.
  - C, C+4, C+8, C+12, C+16: a fireball (projectile 4034) at the closest raider, lands 3 ticks later: 25 unprayed, 1..8
    through Protect from Magic (**OPEN-XA1**).
  - Burn (**OPEN-XA8**): each fireball that hits raises that raider's burn by 1, capped at 5; six typeless beats of the
    current level, 4 ticks apart, then back to 0. A new fireball restarts the six beats at the higher level (replaces
    1 x5).
  - C+20: says "Burn!", the root (seq 14991): every raider takes 19 magic and is bound 3 ticks.
  - C+23: the flame blast (seq 14992): 45 magic to a raider he has line of sight to, 7 to one behind a standing
    pillar or another raider on the line from his centre or without line of sight. The first blast ends the shield that
    halves all damage to him until then (**OPEN-XA6**, halving not settled by captures).
  - C+26: the pillar most raiders hid behind falls (a 100 splat on it, then transformed); with nobody behind one, the
    standing pillar closest to a raider. The third fall (D) starts phase 2 (**OPEN-XA7**, replaces 50% HP). He cannot
    die before it (lethal damage leaves him at 1 HP).
- **Phase 2:** at D: 8 arena fires (loc 62713). Attacks at D+7 and D+11, then a corpse volley at M = D+15 and every 21
  ticks (M, M+21, ...); four attacks at M+5, M+9, M+13, M+17 (**OPEN-XA3**). An attack is at the closest raider: melee
  9..11 (0 under Protect from Melee) if within 1 tile of him, else an arrow 11..29 rolled on impact (0 under Protect
  from Missiles) (**OPEN-XA1**).
- **Arena fires (OPEN-XA4):** 8 random tiles within 9 of his spawn at D, 3 more at M+12 each volley, never under him or
  on an existing fire; they stay. A raider on a fire tile takes 2 typeless every tick (replaces spreading flames).
- **Corpse volley (XA2, OPEN-XA2):** animation 15017; max(min(n, 2), (n + 1) / 2) corpses for n living raiders
  (1, 2, 2, 2, 3 ...), one every 2 ticks, each on a random one of 8 tiles round his middle (8 out cardinally, 6/6
  diagonally); a fireball effect on the tile, the corpse (16687, 62 HP) appears 3 ticks later and walks a tile a tick
  to him (RuneLite stands it 2 ticks; the capture shows walking from +1). On reaching within a tile it heals him its
  current HP (as a heal) and despawns. Two heals within 10 ticks blast every raider within 8 tiles of him for 43..48
  typeless (low confidence).
- **Corpse effects (TfaXarthCorpse):** water spells and freezes (Ice / Bind / Snare / Entangle) stop it 10 ticks
  (**OPEN-XA11**); Undead Grasp 24 ticks only while the caster's Mark of Darkness is active; a fire spell on it
  raises another corpse at one of the 8 tiles round him (**OPEN-XA10**). It never attacks.
- Death removes the minions and ends any burn in progress.
