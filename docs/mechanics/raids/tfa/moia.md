# Moia (room 3, option A)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Moia", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- NPC 16667; combat 1064 (cache 1004, **OPEN-MO1**) (settled by captures, see capture-values.md); size 3; ranged and magic; attack speed 5; max hit 32 ranged,
  44 magic; poisonous. Stats: def 195, magic 350, ranged 200; magic att +300 (str +15), ranged att +350 (str +35).
  Defence: stab 20, slash 110, crush 15, magic 30, light 60, standard 120, heavy 140. Air weakness 80%. Drain capped
  at 60% (floor 117). HP 1,900 (base); per-size table **OPEN-MO2** (not on the page) (settled by captures, see capture-values.md).

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
  healing an additional 80 HP if one was consumed (amount depends on raid scale, **OPEN-MO5**) (settled by captures, see capture-values.md). A countdown starts at about 40 ticks and drops 5 per charge; each surviving binder deals about 25 damage to
  everyone (**OPEN-MO3**: exact) (settled by captures, see capture-values.md).
- Varbit `TFA_MOIA_SHADOW_SHIELD_AMOUNT` exists but no client script reads it (**OPEN-MO4**).
- 28 Oct: binder stacking damage fixed. 3 Nov: magic sound fix.

## Tick timeline (stated parts)

- Binder shuffle every 15 ticks. Ranged damage rolled at magic-land − 2. Enrage: consume at T+10; countdown ~40, −5
  per charge.

## As implemented (content/combat TfaMoia.kt: Moia and TfaMoiaBinder)

Rotation and numbers come from the live captures (capture-values.md), timings from RuneLite where it has them (D-080).
HP by raiders 2..8: 1,900 / 2,760 / 3,550 / 4,350 / 5,150 / 5,950 / 6,750 (x1.45 at three, +0.42 a raider after, rounded
to 10; 6-8 follow the same step, **OPEN-MO2** settled). Hunt range 20. All coordinates are offsets from her start tile
(W point (4055, 5662)).

- **Slot clock:** an action every 5 ticks from her first attack call: a dagger volley, or an event. Events start 25
  ticks after she appears and follow every 20 or 25 ticks (random), in the order blink, blink, infection.
- **Dagger volley:** one projectile 4214 (ranged) at every raider, lands 1 tick later, 5..32, 0 under Protect from
  Missiles.
- **Blink (event):** teleports to one of four points, W (+0,+0), N (+7,+7), E (+14,+0), S (+7,-7), i.e. radius 7 round
  the binders' middle (**OPEN-MO7**, replaces radius 9); throws projectile 4213 (magic) at every raider, lands 6 ticks
  later, 11..44, 0 under Protect from Magic (**OPEN-MO6**).
- **Infection (event, MO2):** projectile 4218 at one random raider; the first cough 3 ticks later (RuneLite LU; the
  capture has I+4). A raider or binder not already coughing gets 4 coughs, one every 8 ticks (**OPEN-MO8**: no prayer
  drain, replaces the old drain 10). Each cough spreads the infection to raiders and binders within 2 tiles. A
  coughing raider takes a 2 typeless splat the tick after the cough (none with a serpentine helm); a coughing binder
  hits every raider within 2 for 20 typeless. An entity that dies or leaves the world stops coughing.
- **Binders (MO1):** 8 spawn 6 ticks after she appears on a nine-point track round the middle (offsets from her start:
  (4,5), (8,3), (12,5), (12,1), (12,-3), (8,-1), (4,-3), (4,1), (8,1)); from +13 every 15 ticks each walks its leg to the
  next track point; from +23 every 30 ticks the track is refilled to six (stops once she has enraged). Binder HP 95
  (cache). A binder swings at the first raider within 2 tiles (its own tile included): the first swing 7 ticks after a
  leg starts, then every 5, 4..22, 0 under Protect from Melee (**OPEN-MO9**, replaces max 15). A raider on the binder's
  own tile is pushed one tile east and the binder says "Grah!". The old 3-damage shuffle hit is gone.
- **Change (34%, MO3):** at HP at or under 34% (checked after every hit) she says "Cease this futile resistance!";
  +1 every binder is healed to full; +14 she returns at the middle (+7,0) and heals 65 per standing binder, at most 215
  (**OPEN-MO5**, replaces +80); the first chant 2 ticks after the return (the slot clock is set to return + chant +
  1). Dagger volleys continue every slot while enraged.
- **Chants:** chant 1 says "Useless corpses... Give me your strength!", later ones "Again!". Gaps are 41, 39, 37,
  35 ticks and so on (minimum 7). Each chant sets a blast 5 ticks before the next chant; at the first slot on or after
  that tick every raider takes 25 typeless per binder still standing (25/50/75; **OPEN-MO3** settled). Nothing happens if
  no binder is left.
- MO0: she appears at her start tile (W point), outside melee reach. Death: says "Hmph", removes the binders.
- **OPEN-MO4** (shadow shield): not implemented.
- **OPEN-MO10:** the "Grah!" push direction is not captured: the raider goes to the first free tile east, west, north or
  south of them (never into a wall).
