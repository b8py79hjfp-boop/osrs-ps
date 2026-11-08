# Moia (room 3, option A)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Moia", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- NPC 16667; combat 1064 (cache 1004, **OPEN-MO1**); size 3; ranged and magic; attack speed 5; max hit 32 ranged,
  44 magic; poisonous. Stats: def 195, magic 350, ranged 200; magic att +300 (str +15), ranged att +350 (str +35).
  Defence: stab 20, slash 110, crush 15, magic 30, light 60, standard 120, heavy 140. Air weakness 80%. Drain capped
  at 60% (floor 117). HP 1,900 (base); per-size table **OPEN-MO2** (not on the page).

## Fight

- **MO0** Moia starts outside the arena, so a ranged or magic weapon is needed.
- **MO1 undead binders:** 8 spawn spread evenly at the start and cycle clockwise; reshuffle every 15 ticks; melee anyone within 2 tiles (Protect
  from Melee blocks fully); a player underneath after a shuffle is pushed out with minor damage; respawned during the
  fight; Crumble Undead does not work.
- **MO2 rotation:** 3-4 ranged attacks, then she teleports to another side and fires a slow magic projectile; her
  ranged damage is decided 2 ticks before the magic lands. After 2 magic attacks, a smoke cough: poison ramping 2 → 5 →
  13 → up to 20 and prayer drain; at most 4 coughs; serpentine helm mitigates. The first cough's impact does not spread if the
  targeted player leaves the group within 8 ticks before the next cough; contact with players or binders spreads the
  poison.
- **MO3 enrage (33% HP):** heals all binders, they form a diamond, 10 ticks later she consumes the middle skeleton,
  healing an additional 80 HP if one was consumed (amount depends on raid scale, **OPEN-MO5**). A countdown starts at about 40 ticks and drops 5 per charge; each surviving binder deals about 25 damage to
  everyone (**OPEN-MO3**: exact).
- Varbit `TFA_MOIA_SHADOW_SHIELD_AMOUNT` exists but no client script reads it (**OPEN-MO4**).
- 28 Oct: binder stacking damage fixed. 3 Nov: magic sound fix.

## Tick timeline (stated parts)

- Binder shuffle every 15 ticks. Ranged damage rolled at magic-land − 2. Enrage: consume at T+10; countdown ~40, −5
  per charge.

## As implemented (content/combat TfaMoia.kt)

- 3-4 ranged (max 32), then a teleport to one of four sides (radius 9, OPEN-MO7) and a slow magic projectile (max 44,
  +4 ticks flight, OPEN-MO6); every second magic attack a cough (poison 2/5/13/20, prayer -10 OPEN-MO8, at most 4;
  the cough lands on one player and spreads for 8 ticks to anyone within a tile of a coughed player or a binder). Eight binders on a radius-6 ring rotate every 15 ticks (3 damage to anyone
  underneath) and are replaced until the enrage; they melee within 2 tiles (OPEN-MO9: max 15, Protect from Melee blocks).
- 33%: binders healed, one consumed 10 ticks later (+80 HP), then every 40 ticks 25 x surviving binders to everyone
  (OPEN-MO3). OPEN-MO2: HP for 3-8 players follows Ezekiel's ratios from the 1,900 base.
- MO0: she starts at her first side position (9 tiles out), outside melee reach.
