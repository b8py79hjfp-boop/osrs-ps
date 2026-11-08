# Archive Guardian (room 1)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Archive Guardian", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- NPC 16659 (16660 also listed by Strat); combat 984; size 5; crush; attack speed 5; max hit 25, laser 99+; flying
  attribute. Stats: att 250, str 240, def 260, magic 250, ranged 280; magic att +350 (str +55), ranged att +350 (str
  +55). Defence: stab 120, slash 110/90, crush 20, magic 130, light 75/50, standard 120/115, heavy 50; where the infobox gives two values the first is the flying version and the second grounded (the
  version pairing used for HP and weakness). Fire weakness 80% flying, 40% grounded. Immune to poison, venom, freeze; immune to Ancient
  Magicks. Cache: HP 550 (1100 grounded/unshielded).
- HP (flying / grounded) by players: 1-2: 550/1,100; 3: 800/1,600; 4: 1,030/2,060; 5: 1,260/2,520; 6: 1,490/2,980;
  7: 1,720/3,440; 8: 1,950/3,910.
- Defence drain capped at 60% (floor 156). Defence falls and Magic rises with damage:
  Defence = 260 − 130 × damage / grounded_base_hp; Magic = 250 + 125 × damage / grounded_base_hp (Strat).

## Fight

- **AG0** the Guardian targets the first player in orb order (the party leader should start the fight). Flying
  prevents melee except halberds. If defence-reducing specials were used, the lower defence value is kept when it
  updates.
- **AG1 phase 1 (flying):** ranged rock throws; tendrils behind it deal typeless damage; melee on a player standing
  underneath. Mushrooms about every four attacks (**OPEN-AG2**: count) (settled by captures, see capture-values.md). The shield must be broken before it crashes.
- **AG2 inert guardians (4):** two reachable in melee, one ranged/magic only, one hit only by the laser; charging one
  removes about 40% of the shield (**OPEN-AG3**) (settled by captures, see capture-values.md). In phase 2 shield breaks transfer to HP.
- **AG3 grounded:** after a 6-tick delay tendrils target players every 3 ticks; each tendril hits 3 times in a 3×3
  (moderate melee; Protect from Melee caps it at 11); tendrils spawn on the player's side opposite the boss. Grounded
  ends after 34 ticks or at 33% HP (**OPEN-AG5**: whether the 34 counts from the crash or after the 6-tick delay) (settled by captures, see capture-values.md).
- **AG4 phase 2 (33% HP, flying):** alternates mushrooms and a magic laser at a random player, fatal with more than 2
  players alive unless reduced; Protect from Magic reduces it slightly (**OPEN-AG4**: numbers) (settled by captures, see capture-values.md). Vengeance reflects
  step-under melee, rocks, tendrils and the laser.
- **AG5 mushrooms:** two near every player; 5 ticks after spawning, a mushroom within 1 tile of a player explodes:
  10 damage, poison and run disabled (re-enable at once) within 1 tile, plus
  the drowsy/forced-walk and prayer effects on a non-immune player (**OPEN-AG6**: exact; partly settled by captures, see capture-values.md); 3-4 damage within 2 tiles. Serpentine helm stops the poison and run
  loss.
- **AG6 archive key:** "A small object falls out of the defeated Archive Guardian." after 100 completions (Archive key).
- 3 Nov: circular clickbox under the Guardian (cache models 51447/51448, tfa.md TFA-D1).

## Tick timeline (stated parts)

- Grounded start T: tendrils start at T+6, then every 3 ticks; grounded lasts 34 ticks (start point OPEN-AG5) or
  until 33% HP.
- Mushroom spawned at T explodes at T+5.

## As implemented (content/combat TfaArchiveGuardian.kt, raids-tfa TfaBosses.kt)

Numbers and rotations come from the live captures (capture-values.md), timings from RuneLite (runelite-timings.md),
precedence per D-080.

- "Touch" on the Inert Guardian 16657 turns it into 16659 and it hunts the nexus. The HP bar shows the current pool:
  the flying shield (550 at 1-2 raiders, 800, 1,030, 1,260, 1,490, 1,720, 1,950 at 8), then the grounded HP (1,100 ...
  3,910) kept across phases. State is created on first use (attack, hit or incoming damage); scale = raid scale, min 2.
- AG0: the woken Guardian engages the first party member in orb order.
- **Shielded (flying), AG1:** one 5-tick attack clock. Each slot is the special (mushrooms) when it is every 5th slot
  (four ranged between), else a slam if any raider is under its 5x5, else a rock throw.
  - Rock (projectile 4201, seq ranged attack): lands 4 ticks after the throw, accuracy-rolled ranged, 0..41 solo;
    **OPEN-AG11:** +5 max per raider in the room beyond three (capture 44..54 at 4-5 raiders, a party scaling not
    fitted). Protect from Missiles: 25% of the roll kept, capped at 10.
  - Slam (seq 14752): lands 1 tick later on whoever is still under its 5x5, 0..48 melee. **OPEN-AG9** (low-confidence
    damage; stepping out on the swing tick avoids it).
- **Mushrooms, AG5:** the special (seq 14754) puts, 1 tick later, two mushrooms by every raider in the arena (radius
  16): one on a random neighbour tile (8 directions) of the raider's current tile, one 3 tiles out in a random
  direction; skipped on a non-walkable tile or a live mushroom; at most 39 alive; they stay all fight until they burst.
  Loc 62578 appears, becomes the active loc 3 ticks later. A mushroom is checked every tick from active+1: the first
  tick a raider stands within 1 tile arms it, the next tick it bursts (despawn loc, 2-tick fade). Raiders within 1
  tile at the burst without a serpentine helm are poisoned 10 and told "You're too drowsy to run!"; run mode is forced
  off every tick for 17 ticks. No prayer drain and no bind (capture; replaces the old drain 10 + 2-tick bind).
- **Batteries, AG2:** four batteries at offsets (14,-5), (14,8), (-11,-5), (-11,8) from the Guardian's south-west tile
  (4125, 5597), i.e. (4139,5592), (4139,5605), (4114,5592), (4114,5605). The **south-east** one (4139,5592) is the
  inactive NPC type and inert all fight; the other three are active (Attack op, never attack), 180 HP solo
  (**OPEN-AG8:** +55 HP per raider beyond two for the "180..400 by party"). Destroying an active one: 3 ticks later the
  shield loses 40% of its max (e.g. 220 solo), capped at what is left. Only during the shielded phase. Batteries are
  re-summoned each time a fresh shield is raised.
- **Shield at 0 (grounded), AG3:** the shield reaching 0 does not kill it (HP set to 1); 3 ticks later it crashes
  (idle-to-ground seq, form 16660, grounded pool restored to the kept grounded HP). A raider exactly 1 tile from its
  5x5 as it lands takes a smash of 24..28 (melee). Vines start 5 ticks after landing, then one wave every 3 ticks.
  - Grounded lasts 35 ticks from the crash the first time, 40 on every later landing (RuneLite LU value; the
    captures give 35, then 41, then 47, +6 each: not yet applied). Counted from the crash (**OPEN-AG5**).
  - At the first slot with real HP under 34% of the grounded max there is no wave: it lifts 3 ticks later (clock
    +4) and enrages. Time running out lifts it 3 ticks later back to a fresh shield (batteries again). Death while
    grounded (HP to 1) goes straight to the enrage.
  - Vine wave: one vine (Npcs.ARCHIVE_GUARDIAN_TENTACLE) per living raider, on the tile one step past the raider away from the
    Guardian's middle, else toward it, else the raider's tile. It attacks 3, 6 and 9 ticks after it appears and
    despawns at +12. Each beat hits every living raider exactly 1 tile from the vine, once per tick however many
    vines reach: melee, never misses, 17..19, or flat 10 under Protect from Melee (**OPEN-AG7**, capture: 11 in 6 of
    33 prayed hits).
- **Enrage (phase 2, flying, grounded HP kept), AG4:** starts when it lifts under 34% (or dies grounded). While
  enraged it keeps attacking on the 5-tick clock: slam if a raider is under it, else rock (**OPEN-AG10:** the capture
  records only the 20-tick cycle, not the filler attacks); no mushrooms. Every 20 ticks from S: at S+5 the blast
  animation (seq 14754) and a blast roll of 42..49 divided by the raiders in the room, queued to land 5 ticks later
  (S+10, the RuneLite value), x1.5 for anyone not on Protect from Magic. At S+10 the next battery overloads (typeless
  hit equal to its HP), in the order (4139,5592), (4139,5605), (4114,5592), (4114,5605), at offsets from its spawn
  tile; once all four are gone the overload does nothing.
- Defence/Magic drift by Strat's formula from damage taken (Defence = 260 - 130 x damage / grounded base HP; Magic =
  250 + 125 x damage / grounded base HP), never above the lowest value reached, floored at 156.
- Death (phase 2 reaching 0) removes batteries and vines and clears mushrooms.
