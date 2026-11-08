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
  underneath. Mushrooms about every four attacks (**OPEN-AG2**: count). The shield must be broken before it crashes.
- **AG2 inert guardians (4):** two reachable in melee, one ranged/magic only, one hit only by the laser; charging one
  removes about 40% of the shield (**OPEN-AG3**). In phase 2 shield breaks transfer to HP.
- **AG3 grounded:** after a 6-tick delay tendrils target players every 3 ticks; each tendril hits 3 times in a 3×3
  (moderate melee; Protect from Melee caps it at 11); tendrils spawn on the player's side opposite the boss. Grounded
  ends after 34 ticks or at 33% HP (**OPEN-AG5**: whether the 34 counts from the crash or after the 6-tick delay).
- **AG4 phase 2 (33% HP, flying):** alternates mushrooms and a magic laser at a random player, fatal with more than 2
  players alive unless reduced; Protect from Magic reduces it slightly (**OPEN-AG4**: numbers). Vengeance reflects
  step-under melee, rocks, tendrils and the laser.
- **AG5 mushrooms:** two near every player; 5 ticks after spawning, a mushroom within 1 tile of a player explodes:
  10 damage, poison and run disabled (re-enable at once) within 1 tile, plus
  the drowsy/forced-walk and prayer effects on a non-immune player (**OPEN-AG6**: exact); 3-4 damage within 2 tiles. Serpentine helm stops the poison and run
  loss.
- **AG6 archive key:** "A small object falls out of the defeated Archive Guardian." after 100 completions (Archive key).
- 3 Nov: circular clickbox under the Guardian (cache models 51447/51448, tfa.md TFA-D1).

## Tick timeline (stated parts)

- Grounded start T: tendrils start at T+6, then every 3 ticks; grounded lasts 34 ticks (start point OPEN-AG5) or
  until 33% HP.
- Mushroom spawned at T explodes at T+5.

## As implemented (content/combat TfaArchiveGuardian.kt, raids-tfa TfaBosses.kt)

- "Touch" on the Inert Guardian 16657 turns it into 16659 and it hunts the nexus. The HP bar shows the current pool:
  the flying shield (550 ... 1,950 by scale), then the grounded HP (1,100 ... 3,910) kept across phases.
- Shield at 0 → grounded (16660) for 34 ticks from the crash (OPEN-AG5), tendrils at +6 then every 3 ticks; back to
  a fresh shield if the time runs out, or phase 2 (flying, grounded HP) at 33% of grounded HP. Phase 2 alternates
  laser and mushrooms. Death of 16659 / 16660 ends the room.
- Defence/Magic drift by Strat's formula from damage taken, never above the lowest value reached, floored at 156.
- **OPEN-AG7:** tendril damage ("moderate"): 0-20 per hit, capped at 11 under Protect from Melee.
- **OPEN-AG8:** the four batteries (16662, Attack) stand at the cache battery bases and are active whenever a shield is
  up; destroying one takes 40% of the shield (phase 2: 40% of grounded HP as damage). The north-east battery is the
  laser-only one: players' hits do nothing to it; a laser target within 2 tiles of it lures the beam into it (the
  battery is charged, the player takes 10).
- **OPEN-AG4 model:** laser = 35 x players alive (fatal above two players at 99 HP), Protect from Magic -20%.
