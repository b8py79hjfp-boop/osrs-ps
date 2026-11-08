# Ocellus (room 5, final)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Ocellus", "The Fractured Archive/Strategies" (Strat, under
construction) and the 21 Oct / 28 Oct / 3 Nov update notes. Raid-wide rules: [../tfa.md](../tfa.md). Tick timelines
below list only what a source states; everything else is `OPEN` and gets a named config flag when implemented.

## Data

- Hooded Figure 16672. P1 16673/16674, P2 (the Blade) 16675/16676, P3 16677 (16682 is a debug copy without the 3 Nov
  clickbox). Size 3; max hit 24 (infobox); immune to cannon; poison immune.
- **P1** combat 1121, HP 2,500, magic and ranged, speed 5; def 215, magic 350, ranged 250, magic att +150, ranged att
  +150; defence stab 150, slash 95, crush 60, magic 100, light 50, standard 135, heavy 50. Drain floor 129.
- **P2 (Blade)** HP 1,000, def 250 (+35 levels during P2, weakest to crush); defence stab 150, slash 120, crush 10,
  magic 100, light 100, standard 110, heavy 80. No drain cap.
- **P3** combat 1601 (cache 1197, **OPEN-OC1**), HP 5,250, magic/melee/ranged, speed 3; att 325, str 250, def 250,
  magic 300, ranged 300; magic att +350, melee att +150; demon; defence stab 150, slash 100, crush 30, magic 200,
  ranged 250/250/250. Drain floor 150; offensive drain capped at 50%; defence no longer restored between quadrants.
- HP by players: P1 2,500 (1-2), 3,630, 4,680, 5,730, 6,780, 7,830, 8,880; "remainder" 5,250, 7,610, 9,820, 12,020,
  14,230, 16,430, 18,640 (**OPEN-OC2**: how P2's pool fits).

## Fight

- **OC1 P1:** moves between corners firing ranged (orange) and magic (blue); at each corner seven tendrils charge the
  player (they heal on charge); a slam within 2 tiles up to 35. At 66% and 25% shield he teleports to the centre,
  fires two tendrils four times, then "Embrace... annihilation..." (80+ total) or "Death... awaits..." (17-18) by how
  many tendrils were mitigated. Ranged barrage patterns: dbtable 141 `TFA_OCELLUS_BARRAGE_1..3` (phase, speed,
  column, row) — **stripped**, **OPEN-OC3**.
- **OC2 P2 (the Blade):** shield 0 → centre; four quadrants bounded by orange and purple portals; purple portals
  damage, two orange portals form a safe intersection; after five sets, purple pools and an orange portal to a new
  quadrant. The Blade stores damage and releases it each quadrant; half transfers to P3's HP. Longer immunity after an
  orange portal (3 Nov). **OPEN-OC4:** the P2 → P3 trigger.
- **OC3 P3:** four diagonal quadrants with floating rocks; each has a tank by orb order (28 Oct); +10% damage taken
  from players in line behind him; each quadrant ends with a destruction charge (leave or die). Cycle: two melee, a
  directional wave; every two cycles a special in the order Prayer → Untether → Prayer → Insolence (Untether skipped
  if the quadrant is phased early). Prayer: up to 3 sawblade explosions, overheads disabled for 3 ticks, purple pulse,
  1-tick re-enable window. Untether: void eruption 40+ unless inside the purple lines. Insolence: waves at three
  players. Secondary: Void Vine (root, 50+ unless broken); void spawns (Siphon, Augmenter, Rift). Censers: Bandosian
  (melee), Armadylean (ranged), Zarosian (+50% spec), Saradomin (+40 HP, +40 prayer). 3 Nov: Void Spin delay;
  Untether no longer pushes; P3 start tile fix; circular clickbox.
- **OC4 P4 (one quadrant left):** rises, Deflect Melee, floor turns to void; a half-health orb heal; void explosions
  every 2 ticks for six cycles; soul-split void blast 40+ unprayed, 6-7 prayed; floor patterns rotate melee / ranged /
  magic.

## Tick timeline (stated parts)

- P3 Prayer special: overheads off for 3 ticks, 1-tick re-enable window. P4: explosions every 2 ticks × 6.
