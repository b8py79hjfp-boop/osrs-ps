# Bork and the Smorks (room 2, option B)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Bork", "The Smorks", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- **Bork** NPC 16640; combat 871; size 4; crush; attack speed 7; max hit **OPEN-BK1** (settled by captures, see capture-values.md). Stats: att 200, str 200,
  def 300, magic 90, ranged 200; melee att +100, str +40. Defence: stab 140, slash 130, crush 10, magic 300, light 80,
  standard 50, heavy 5. Earth weakness 40%. Immune to cannon. Drain capped at 60% (floor 180).
  HP: 1-2: 1,500; 3: 2,180; 4: 2,810; 5: 3,440; 6: 4,070; 7: 4,700; 8: 5,330.
- Flat armour from 8, +2 for every 25% damage the **Smorks** take (max 14); 0 while stunned. Magic resistance: **OPEN-BK2** (partly settled by captures, see capture-values.md)
  (Bork page: 0 rising by 2 per 25%; Strat: 2 base).
- **The Smorks** NPC 16641; combat 640; size 3; ranged and magic; attack speed 7; flat armour 2. Stats: def 220,
  magic 185, ranged 185; magic att +185 (str +35), ranged att +250 (str +30). Defence: stab 220, slash 170, crush 160,
  magic 400, light 80, standard 150, heavy 80. Earth weakness 85%. No drain cap.
  HP: 1-2: 600; 3: 870; 4: 1,120; 5: 1,370; 6: 1,630; 7: 1,880; 8: 2,130.

## Fight

- **BK0** the Bork tank is the first player to enter; the next tank is named by overhead. Both bosses attack faster
  as the Smorks' HP falls.
- **BK1** Bork: AoE melee shockwave; first attack stronger if the target is out of melee range; overhead
  "Bork smash [name] now" marks an aggression switch.
- **BK2** Smorks: ranged and magic at all players not focused by Bork when 2+ are present (not tick-eatable). Incendiary cannonballs leave
  oil: 6-7 per tick and ignites. A heavy cannonball landing on Bork stuns him; melee on a dazed Bork always hits.
  Stomp covers the arena: hide behind the Smorks' cart.
- **BK3 enrage:** Bork dies first → the Smorks attack much faster, hit everyone and their auto-attacks hit harder; the Smorks die first → Bork stomps
  the whole arena for heavy melee damage. **OPEN-BK3:** every tick timing and max hit. (partly settled by captures, see capture-values.md)

## As implemented (content/combat TfaBork.kt: Bork and TfaSmorks)

Rotations and numbers come from the live captures (capture-values.md), timings from RuneLite and the cache (D-080).
HP by raiders 2..8: Bork 1,500 / 2,180 / 2,810 / 3,440 / 4,070 / 4,700 / 5,330; Smorks 600 / 870 / 1,120 / 1,370 /
1,630 / 1,880 / 2,130. Hunt range 20 for both.

- **Shared rate:** Bork's drum and the Smorks' slot clock use one rate: 7 ticks, then 6, 5, 4 once the Smorks are at
  or under 75 / 50 / 25% of their max HP (**OPEN-BK3**). The room ends when both are dead.
- **Bork's attack clock:** the first attack call only starts the clock; the first gong comes 8 ticks after he engages.
  He does nothing while stunned or stomping. With the Smorks alive each drum is a gong; with the Smorks dead each
  drum is a stomp set with no wind-up (below).
- **Gong (BK1):** two beats, T and T+2. Beat 1 reaches rings 1..max(1, his target's gap from his 4x4), beat 2 the
  next ring only; raiders in the Smorks' cart shadow are safe. Beat 1 16..28, beat 2 20..33, divided by 4 under
  Protect from Melee (4..7, 5..8) (**OPEN-BK1**, replaces max 40 and x1.5 first attack).
- **Cart shadow (OPEN-BK8, RuneLite LU Bork.java fit):** a tile in the one-tile ring round the Smorks (not under them) is
  safe when at least 13 of 100 sample points in it are hidden, seen from Bork's centre (his south-west tile + 2, 2),
  behind the Smorks' middle box (their south-west tile + 0.5 .. + 2.5).
- **Stomp set:** wind-up anim and "Bork very angry!!" after the wind-up delay, then four slams 3 ticks apart over the
  whole arena except the cart shadow: 14..50, or 18..33 under Protect from Melee; 3 ticks after the last slam a new
  random raider is targeted, and "Bork smash <name> now." is said only when the target changes (**OPEN-BK4**,
  replaces every 5th attack); the drum resumes 2 ticks later. He is held still for the whole set. Wind-up delay: 14
  after a stun, 3 after a missed bomb, 0 when the Smorks are dead. Timeline after a stun at L: stomp-in L+14, slams
  L+17/20/23/26, retarget L+29, drum L+31. This replaces the old 50 max and the old every-6th-attack stomp.
- **Stun (OPEN-BK7):** a big bomb landing while his 4x4 was over the tile the tick before stuns him 14 ticks
  (`NPC_BORK_14_CYCLE_STUN01`), then the stomp set above. While stunned melee always hits him and flat armour is 0.
  With the Smorks dead a stun starts no stomp set.
- **Missed bomb:** a bomb that lands off his 4x4 hurts no raider (replaces the old 20). If it lands one tile off his
  4x4 it hits him 25..28 typeless. Then, unless he is already stomping or stunned, a stomp set with wind-up 3 starts
  (slams at +6, 9, 12, 15 after landing; the KDoc quotes the capture's stomp at +5, code uses 3).
- **Armour (OPEN-BK2):** flat 10 off every melee hit while not stunned (fitted, 98:1), none while stunned; no
  magic-resistance modelling (untested in the captures). Replaces 8 + 2 per 25%.
- **Smorks slot cycle (BK2):** every rate ticks one of seven slots: shot, shot, napalm, nothing, shot, shot, big bomb.
  With Bork dead the first Smorks attack call only starts an 11-tick wait, then they act every 2 ticks and the
  cycle goes on (the big-bomb slot becomes a shot).
  - Shot (**OPEN-BK5**): one projectile at every raider except Bork's target (all raiders if Bork is dead or only one
    raider); style per shot chosen at random, magic (projectile 4057) or ranged (4056); lands (end + 15) / 30 ticks
    later. End value comes from the table 90, 90, 120, 150 indexed by (rate - 2) clamped to 0..3, so the code lands
    at 150 for rates 7, 6 and 5 and 120 for rate 4 (the KDoc/capture say 150 / 120 / 90 / 90); 90 with Bork dead.
    Damage: the matching protection prayer blocks it (a 1 one time in four); otherwise 11..30 (10..28 with Bork dead).
  - Napalm (proj 4045): thrown at whoever they attack, lands 4 ticks later: 25..28 typeless on its tile; if the tile is
    under Bork's 4x4 he takes 25..28. Leaves ragged tiles (loc 62544) for 199 ticks: the tile plus 1 or 2 streaks in
    random cardinal directions, 3..17 tiles in all (**OPEN-BK6**). A raider on one takes 2 typeless a tick and, if not
    already burning, a burn of 1..3 every 4 ticks, five times.
  - Big bomb (proj 4440, **OPEN-BK9** target): aimed at Bork's target's tile, lands 5 ticks after its slot; stun if
    Bork's 4x4 covered that tile the tick before the landing, else the missed-bomb branch above. Skipped if Bork has
    no target.
- A dazed Bork is always hit by melee (Boss.meleeAlwaysHits).
