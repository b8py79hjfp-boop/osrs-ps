# Bork and the Smorks (room 2, option B)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Bork", "The Smorks", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- **Bork** NPC 16640; combat 871; size 4; crush; attack speed 7; max hit **OPEN-BK1**. Stats: att 200, str 200,
  def 300, magic 90, ranged 200; melee att +100, str +40. Defence: stab 140, slash 130, crush 10, magic 300, light 80,
  standard 50, heavy 5. Earth weakness 40%. Immune to cannon. Drain capped at 60% (floor 180).
  HP: 1-2: 1,500; 3: 2,180; 4: 2,810; 5: 3,440; 6: 4,070; 7: 4,700; 8: 5,330.
- Flat armour from 8, +2 for every 25% damage the **Smorks** take (max 14); 0 while stunned. Magic resistance: **OPEN-BK2**
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
  the whole arena for heavy melee damage. **OPEN-BK3:** every tick timing and max hit.

## As implemented (content/combat TfaBork.kt)

- Bork: shockwave on everyone within 2 tiles of his 4x4 plus his target (OPEN-BK1: max 40, the first attack x1.5 when
  the target is out of melee range); "Bork smash <name> now" re-targets every 5th attack (OPEN-BK4); flat armour
  8 + 2 per 25% of Smorks damage (max 14), 0 while stunned; with the Smorks dead he stomps the arena (OPEN-BK3: 50).
- Smorks: volleys at everyone Bork is not focused on (OPEN-BK5: max 25, alternating ranged/magic; x1.5 and speed 3
  once Bork is dead); oil every 4th attack, 3x3 for 8 ticks at 6-7 per tick (OPEN-BK6); a heavy cannonball every 6th
  at a player, 20 within 1 tile, stunning Bork for 8 ticks when it lands next to or on him (OPEN-BK7).
- A dazed Bork is always hit by melee (Boss.meleeAlwaysHits).
- Both attack from every 7 ticks down to 4 as the Smorks' HP falls (OPEN-BK3). The room ends when both are dead.
- BK2 cart: every 6th Bork attack while the Smorks live is a 2-tick wind-up stomp over the whole arena (max 50); a
  player with the Smorks' cart on the line from Bork is shielded (OPEN-BK8).
