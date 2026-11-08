# Ocellus (room 5, final)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Ocellus", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- Hooded Figure 16672. P1 16673/16674, P2 (the Blade) 16675/16676, P3 16677 (16682 is a debug copy without the 3 Nov
  clickbox). Size 3; max hit 24 (infobox); immune to cannon; poison immune.
- Defence is restored to full at each phase transition (only the P3 quadrant restore was removed, 21 Oct).
- **P1** combat 1121, demon (demonbane resistance 20%), HP 2,500, magic and ranged, speed 5; def 215, magic 350, ranged 250, magic att +150, ranged att
  +150; defence stab 150, slash 95, crush 60, magic 100, light 50, standard 135, heavy 50. Drain floor 129.
- **P2 (Blade)** HP 1,000, def 250 (+35 levels during P2, weakest to crush); defence stab 150, slash 120, crush 10,
  magic 100, light 100, standard 110, heavy 80. No drain cap.
- **P3** combat 1601 (cache 1197, **OPEN-OC1**), HP 5,250, magic/melee/ranged, speed 3; att 325, str 250, def 250,
  magic 300, ranged 300; magic att +350, melee att +150; demon; defence stab 150, slash 100, crush 30, magic 200,
  ranged 250/250/250. Drain floor 150; offensive drain capped at 50%; defence no longer restored between quadrants.
- HP by players: P1 2,500 (1-2), 3,630, 4,680, 5,730, 6,780, 7,830, 8,880; "remainder" 5,250, 7,610, 9,820, 12,020,
  14,230, 16,430, 18,640 (**OPEN-OC2**: how P2's pool fits).

## Fight

- **OC1 P1:** moves between corners firing ranged (orange) and magic (blue); at each corner seven tendrils channel energy into Ocellus; a player standing on one mitigates it, takes some damage
  and gains "healing charges"; a slam within 2 tiles up to 35. At 66% and 25% shield he teleports to the centre,
  fires two tendrils four times, then "Embrace... annihilation..." (80+ total) or "Death... awaits..." (17-18) by how
  many tendrils were mitigated. Ranged barrage patterns: dbtable 141 `TFA_OCELLUS_BARRAGE_1..3` (phase, speed,
  column, row) — **stripped**, **OPEN-OC3**.
- **OC2 P2 (the Blade):** shield 0 → centre; four quadrants bounded by orange and purple portals; purple portals
  damage, two orange portals form a safe intersection; after five sets, purple pools and an orange portal to a new
  quadrant. The Blade stores damage and releases it each quadrant; half transfers to P3's HP. Longer immunity after an
  orange portal and a portal double-hit fix (3 Nov); damage per quadrant has diminishing returns, made more forgiving
  on 28 Oct (**OPEN-OC5**: the curve). **OPEN-OC4:** the P2 → P3 trigger.
- **OC3 P3:** four diagonal quadrants with floating rocks; each has a tank (28 Oct: the leader first, then orb order skipping dead players, with a tank-only sound and chat
  message); +10% damage from attacks made from directly opposite the tank; void pools under non-tanks; a spread void
  shot (20+) when the tank is unreachable; each quadrant ends with a destruction charge (leave or die). Cycle: two melee, a
  directional wave; every two cycles a special in the order Prayer → Untether → Prayer → Insolence (**OPEN-OC6**: the wiki's
  "Untether is skipped if the quadrant is phased early" is {{fact}}-tagged). Prayer: up to 3 sawblade explosions; the
  player has a 3-tick window to turn the overhead off (a purple pulse marks the last chance), then a 1-tick window to
  turn it back on; failing, Ocellus disables it and briefly blocks reactivation (**OPEN-OC7**: how long). Untether: void eruption 40+ unless inside the purple lines. Insolence: waves at three
  players. Secondary (also {{fact}}-tagged): Void Vine (root, 50+ unless broken); void spawns (Siphon, Augmenter, Rift). Censers: Bandosian
  (melee), Armadylean (ranged), Zarosian (+50% spec), Saradomin (+40 HP, +40 prayer). 3 Nov: Void Spin delay;
  Untether no longer pushes; P3 start tile fix; censer proc fix; circular clickbox.
  21 Oct: Protect from Melee icon in P3; wrong-location attack pattern fix.
- **OC4 P4 (one quadrant left):** rises, Deflect Melee (Deflect Range later in the cycle), floor turns to void; the
  blue-wind orb takes half his current HP (rounded down); void explosions every 2 ticks for six cycles; a perfect
  dodge cuts the next attack's cooldown by 3 ticks; soul-split void blast 40+ unprayed, 6-7 prayed, its style shown by
  the pattern under the boss (**OPEN-OC8**: order); he takes less damage from weapons slower than 2 ticks (33% less
  for 3t, 50% for 4t, 60% for 5t).

## Tick timeline (stated parts)

- P3 Prayer special: 3-tick window to deactivate, then a 1-tick window to reactivate. P4: explosions every 2 ticks × 6.

## As implemented (content/combat TfaOcellus.kt)

- P1 (16674, scaled P1 HP): alternating ranged/magic (24), a slam (35) on players within 2 tiles, every eighth attack a
  corner channel of seven tendrils (unmitigated: +20 HP each; a mitigating player takes 5; OPEN-OC10); at 66% and 25%
  a centre blast: "Embrace... annihilation..." 80 if fewer than half the players stand on its eight tendrils, else
  "Death... awaits..." 17 (OPEN-OC11).
- P2 (16675, 1,000 HP, OPEN-OC2), on its own 5-tick timer: each set lays purple lines on the players' rows and
  columns with one orange crossing (a safe tile) per player; purple off the crossing = 20 after 2 ticks (OPEN-OC5).
  Players' damage on the Blade is stored and lands at the end of every fifth set (a quadrant), x0.8 per earlier
  quadrant (OPEN-OC5 diminishing returns). P3 starts when the Blade's pool is gone (OPEN-OC4) with the "remainder" HP minus
  half the damage the Blade took.
- P3 (16677): cycles of two melee (30; a 20 void shot at everyone when the target is out of reach) and a wave (25 at the
  target and players next to them); every second cycle a special: Prayer (overheads still on after 3 ticks are turned
  off, 20 damage; OPEN-OC7), Untether (40 beyond 2 tiles of him; OPEN-OC13), Prayer, Insolence (waves at three
  players). Quadrants, tanks, rocks, censers and the vine are not modelled (OPEN-OC9).
- P4 below 25% of P3's pool: melee halved (Deflect Melee), slow weapons 33/50/60% less, six void explosion cycles every
  2 ticks then soul-split blasts (40-45 unprayed, 6-7 prayed, healing him a quarter; OPEN-OC8: style alternates).
- His first action sets `TFA_OCELLUS_MET` for the players in the arena.
- P3 also: the tank is the first party member in orb order alive in the arena (raids-core tags raid NPCs with the
  orb order); attacks from directly opposite the tank deal +10%; void pools under non-tanks (10 after 2 ticks,
  OPEN-OC15); every third special a Void Vine roots a non-tank (50 unless broken within 8 ticks, OPEN-OC16); every 12
  attacks a void spawn in turn (Siphon heals 10 per melee, Augmenter +20% melee, Rift attacks; OPEN-OC17); quadrants
  end at 75% and 50% with a destruction charge (50 within 3 tiles after 4 ticks) and he moves to the next diagonal
  quadrant (6 out, OPEN-OC9).
- **OPEN-OC14:** the censers (Bandosian, Armadylean, Zarosian, Saradomin) have no loc, NPC or obj in cache 2757, so they
  are not built.
- P1 barrage every fifth attack: a row and a column through up to three players, 20 to anyone still on one after 2
  ticks (OPEN-OC3: the stripped barrage patterns). P4: on entry the wind orb takes half his current HP (rounded down);
  Deflect Melee while the void explosions run, Deflect Range during the blasts; a player off their explosion tile when it
  lands has their next attack's cooldown cut by 3 ticks.
