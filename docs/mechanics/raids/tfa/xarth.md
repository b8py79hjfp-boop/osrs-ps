# Xarth (room 4, option B)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Xarth", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- NPC 16684; combat 912; size 3; magic, ranged, melee; attack speed 4; max hit **OPEN-XA1**; demon (demonbane
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
  arena-wide blast (**OPEN-XA2**: window and damage).
- 3 Nov: faster navigators; ranged sound quieter. **OPEN-XA3:** tick timings.

## As implemented (content/combat TfaXarth.kt)

- Phase 1: magic at the closest player (max 35, OPEN-XA1); every sixth attack binds everyone for 5 ticks and 4 ticks
  later explodes: 45 with line of sight to him, 7 behind a pillar (OPEN-XA3). The shield halves damage until the first
  explosion (OPEN-XA6). Pillars are four rubble NPCs (16685; OPEN-XA9: 5 tiles out diagonally) that become 16686 in phase 2; cover is
  line of sight or a pillar or another player on the line from his centre. Each landed magic hit burns 1 every 4 ticks
  five times (OPEN-XA8).
- Phase 2 at 50% HP (OPEN-XA7): melee (35) when adjacent, else ranged (30) rolled on impact; flames every third attack
  on a player's tile spreading by one tile every 4 ticks three times (8 per tick, OPEN-XA4); a corpse every 15 ticks
  from 12 tiles out walks to him and heals him for its HP; two heals within 10 ticks blast everyone for 30 (OPEN-XA2).
  Water spells and freezes stop a corpse for 10 ticks (OPEN-XA11), Undead Grasp for 24, fire spells add a corpse.
- Undead Grasp holds a corpse 24 ticks only while the caster's Mark of Darkness is active (XA2).
