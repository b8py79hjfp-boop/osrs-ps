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
  searing projectiles, then a charged explosion; hide behind a pillar (pillar tank ~45 damage, others ~7). The shield
  goes after the first explosion.
- **XA2 phase 2 (pillars gone):** flames spread from the edges; ranged (damage on impact, Protect from Missiles) and
  melee, static. Corpses spawn at the edges, walk to him and heal him for their remaining HP; freezes, water spells and
  Undead Grasp (24 ticks) stop them; fire spells make more. Two corpses healing him in a short window trigger an
  arena-wide blast (**OPEN-XA2**: window and damage).
- 3 Nov: faster navigators; ranged sound quieter. **OPEN-XA3:** tick timings.
