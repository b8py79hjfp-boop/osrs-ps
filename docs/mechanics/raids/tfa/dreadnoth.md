# Dreadnoth the Jellied (room 3, option B)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Dreadnoth the Jellied", "Jelly (Dreadnoth)", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- NPC 16665; combat 888; size 5; melee and ranged; attack speed 4; max hit 45; demon (demonbane resistance 75%).
  Stats: att 280, str 285, def 125, magic 220, ranged 250; melee att +185 (str +35), ranged att +180 (str +20).
  Defence: stab 15, slash 20, crush 5, magic 20, light 100, standard 80, heavy 80. Fire weakness 85%; freeze
  resistance 40; immune to cannon. **No** defence drain cap; offensive drain capped at 50% (28 Oct).
  HP: 1-2: 2,100; 3: 3,050; 4: 3,930; 5: 4,810; 6: 5,690; 7: 6,570; 8: 7,460.
- **Jelly** NPC 16666; combat 101; size 1; crush; speed 5; max hit 17; HP 61; att 200, str 160, def 100, magic 100;
  defence stab 4, slash 5, crush 0, magic 15, light −45, standard 18, heavy −50; fire weakness 60%; range 1;
  freeze resistance 20; demon; immune to cannon; attacks drain prayer.

## Fight

- **DR1** moves about 1 tile per 2 ticks; melee hits everyone around him; the scorching bow special can root him.
- **DR2 slams** at 85%, 65% and 45% HP: 1, then 2, then 3 arena-wide slams (Protect from Melee), each followed by
  three ranged attacks (Protect from Missiles); floor jelly deals 6-8 per tick; he absorbs jellies he lands on and
  heals; jellies spawn (**OPEN-DR1**: count per party size).
- **DR3 enrage (25%):** "*glub glub*" — jumps and slams a random player, heals ~30-40 if they are underneath, then one
  ranged and one jelly-splatter projectile; no normal melee during the enrage.
- **DR4 death:** explodes into jellies: **OPEN-DR2** (Strat: about ten at 50% HP; jelly page: always 7).
