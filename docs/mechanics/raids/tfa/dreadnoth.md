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
  heals; jellies spawn (**OPEN-DR1**: count per party size) (settled by captures, see capture-values.md).
- **DR3 enrage (25%):** "*glub glub*" — jumps and slams a random player, heals ~30-40 if they are underneath, then one
  ranged and one jelly-splatter projectile; no normal melee during the enrage.
- **DR4 death:** explodes into jellies: **OPEN-DR2** (settled by captures, see capture-values.md) (Strat: about ten at 50% HP; jelly page: always 7).

## As implemented (content/combat TfaDreadnoth.kt: Dreadnoth and TfaJelly)

Rotation and numbers come from the live captures (capture-values.md), timings from RuneLite (D-080).
HP by raiders 2..8: 2,100 / 3,050 / 3,930 / 4,810 / 5,690 / 6,570 / 7,460. Hunt range 20. Holds floor(n x 0.6) of a
freeze (jellies 0.8). Jellies are 75 HP (cache; supersedes the 61 in the Data section).

- **Crawl, DR1:** he is held still on every other tick (one tile every 2 ticks).
- **Melee:** on a 4-tick clock whenever any raider is within one tile of his 5x5, hitting every raider within that
  tile at once: 0..45 melee, two-thirds of the roll under Protect from Melee. Nothing while a slam phase is running.
- **Slam phases (DR2):** triggered after a hit that leaves him at or under 85%, 65%, 45% of max HP (phase k = 1, 2,
  3), if no phase is running. A phase is k jumps (seq 14777) 6 ticks apart. Each jump lands 6 ticks after it starts
  (RuneLite; the capture has J+1): every raider in the room takes 22..44 melee (0 under Protect from Melee); three
  shots (projectile 4211) at random raiders land 3, 4, 5 ticks after the landing, 1..33 ranged (0 under Protect
  from Missiles); 1-3 blood tiles are dropped on random tiles within 4 of him. The attack clock and melee are held
  until 8 ticks after the last landing.
  - Jellies: min(12, 3 x raiders in the room x k) (**OPEN-DR1**, replaces 1 per 2 players), placed round the raiders in
    turn, within 2 tiles, hatching 8 ticks after the last landing.
- **Blood tiles (DR2, OPEN-DR3):** single tiles (loc 62609) that stay all fight. A raider standing on one takes 7..8
  typeless the first tick, then every 2nd tick while still on it. Replaces the 3x3 for 10 ticks of 6-8 a tick.
- **Enrage (DR3):** the tick after a hit that leaves him at or under 25% he says "*glub glub*", normal melee and
  slams stop, and the first leap is 5 ticks later, then every 8 ticks. A leap (seq 14780) is skipped while he is
  frozen/rooted (DR1). It is centred on his target if within 5 tiles each way, else 5 tiles in the nearest of 8
  directions. He lands 4 ticks after the start (RuneLite; the capture has +3). On landing: raiders under his 5x5 take
  25..44 melee and he heals what they take (**OPEN-DR4**, replaces flat 35); raiders one tile off take 16..38 (none
  under Protect from Melee); jellies under him are removed and he heals their current HP (**OPEN-DR5**); 1-5 blood
  tiles within 4 + his size of him. HP is capped at max.
- **Death (DR4, OPEN-DR2):** 3 ticks after his death seven jellies appear on seven distinct tiles of his 5x5, never
  the middle tile.
- **Jelly:** 75 HP; first swing 2 ticks after hatching, then every 3; melee 5..10, 0 under Protect from Melee; hunt
  range 16.
