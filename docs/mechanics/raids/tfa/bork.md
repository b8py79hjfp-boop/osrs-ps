# Bork and the Smorks (room 2, option B)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Bork", "The Smorks", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- **Bork** NPC 16640; combat 871; size 4; crush; attack speed 7; max hit **OPEN-BK1**. Stats: att 200, str 200,
  def 300, magic 90, ranged 200; melee att +100, str +40. Defence: stab 140, slash 130, crush 10, magic 300, light 80,
  standard 50, heavy 5. Earth weakness 40%. Immune to cannon. Drain capped at 60% (floor 180).
  HP: 1-2: 1,500; 3: 2,180; 4: 2,810; 5: 3,440; 6: 4,070; 7: 4,700; 8: 5,330.
- Flat armour from 8 at full HP, +2 every 25% damage (max 14); 0 while stunned. Magic resistance: **OPEN-BK2**
  (Bork page: 0 rising by 2 per 25%; Strat: 2 base).
- **The Smorks** NPC 16641; combat 640; size 3; ranged and magic; attack speed 7; flat armour 2. Stats: def 220,
  magic 185, ranged 185; magic att +185 (str +35), ranged att +250 (str +30). Defence: stab 220, slash 170, crush 160,
  magic 400, light 80, standard 150, heavy 80. Earth weakness 85%. No drain cap.
  HP: 1-2: 600; 3: 870; 4: 1,120; 5: 1,370; 6: 1,630; 7: 1,880; 8: 2,130.

## Fight

- **BK1** Bork: AoE melee shockwave; first attack stronger if the target is out of melee range; overhead
  "Bork smash [name] now" marks an aggression switch.
- **BK2** Smorks: ranged and magic at all players when 2+ are present (not tick-eatable). Incendiary cannonballs leave
  oil: 6-7 per tick and ignites. A heavy cannonball landing on Bork stuns him; melee on a dazed Bork always hits.
  Stomp covers the arena: hide behind the Smorks' cart.
- **BK3 enrage:** Bork dies first → the Smorks attack much faster and hit everyone; the Smorks die first → Bork stomps
  the whole arena for heavy melee damage. **OPEN-BK3:** every tick timing and max hit.
