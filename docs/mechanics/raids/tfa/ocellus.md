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
- **P3** combat 1601 (cache 1197, **OPEN-OC1** (partly settled by captures, see capture-values.md)), HP 5,250, magic/melee/ranged, speed 3; att 325, str 250, def 250,
  magic 300, ranged 300; magic att +350, melee att +150; demon; defence stab 150, slash 100, crush 30, magic 200,
  ranged 250/250/250. Drain floor 150; offensive drain capped at 50%; defence no longer restored between quadrants.
- HP by players: P1 2,500 (1-2), 3,630, 4,680, 5,730, 6,780, 7,830, 8,880; "remainder" 5,250, 7,610, 9,820, 12,020,
  14,230, 16,430, 18,640 (**OPEN-OC2**: how P2's pool fits) (settled by captures, see capture-values.md).

## Fight

- **OC1 P1:** moves between corners firing ranged (orange) and magic (blue); at each corner seven tendrils channel energy into Ocellus; a player standing on one mitigates it, takes some damage
  and gains "healing charges"; a slam within 2 tiles up to 35. At 66% and 25% shield he teleports to the centre,
  fires two tendrils four times, then "Embrace... annihilation..." (80+ total) or "Death... awaits..." (17-18) by how
  many tendrils were mitigated. Ranged barrage patterns: dbtable 141 `TFA_OCELLUS_BARRAGE_1..3` (phase, speed,
  column, row) — **stripped**, **OPEN-OC3** (partly settled by captures, see capture-values.md).
- **OC2 P2 (the Blade):** shield 0 → centre; four quadrants bounded by orange and purple portals; purple portals
  damage, two orange portals form a safe intersection; after five sets, purple pools and an orange portal to a new
  quadrant. The Blade stores damage and releases it each quadrant; half transfers to P3's HP. Longer immunity after an
  orange portal and a portal double-hit fix (3 Nov); damage per quadrant has diminishing returns, made more forgiving
  on 28 Oct (**OPEN-OC5**: the curve) (partly settled by captures, see capture-values.md). **OPEN-OC4:** the P2 → P3 trigger. (settled by captures, see capture-values.md)
- **OC3 P3:** four diagonal quadrants with floating rocks; each has a tank (28 Oct: the leader first, then orb order skipping dead players, with a tank-only sound and chat
  message); +10% damage from attacks made from directly opposite the tank; void pools under non-tanks; a spread void
  shot (20+) when the tank is unreachable; each quadrant ends with a destruction charge (leave or die). Cycle: two melee, a
  directional wave; every two cycles a special in the order Prayer → Untether → Prayer → Insolence (**OPEN-OC6** (settled by captures, see capture-values.md): the wiki's
  "Untether is skipped if the quadrant is phased early" is {{fact}}-tagged). Prayer: up to 3 sawblade explosions; the
  player has a 3-tick window to turn the overhead off (a purple pulse marks the last chance), then a 1-tick window to
  turn it back on; failing, Ocellus disables it and briefly blocks reactivation (**OPEN-OC7**: how long) (partly settled by captures, see capture-values.md). Untether: void eruption 40+ unless inside the purple lines. Insolence: waves at three
  players. Secondary (also {{fact}}-tagged): Void Vine (root, 50+ unless broken); void spawns (Siphon, Augmenter, Rift). Censers: Bandosian
  (melee), Armadylean (ranged), Zarosian (+50% spec), Saradomin (+40 HP, +40 prayer). 3 Nov: Void Spin delay;
  Untether no longer pushes; P3 start tile fix; censer proc fix; circular clickbox.
  21 Oct: Protect from Melee icon in P3; wrong-location attack pattern fix.
- **OC4 P4 (one quadrant left):** rises, Deflect Melee (Deflect Range later in the cycle), floor turns to void; the
  blue-wind orb takes half his current HP (rounded down); void explosions every 2 ticks for six cycles; a perfect
  dodge cuts the next attack's cooldown by 3 ticks; soul-split void blast 40+ unprayed, 6-7 prayed, its style shown by
  the pattern under the boss (**OPEN-OC8**: order) (settled by captures, see capture-values.md); he takes less damage from weapons slower than 2 ticks (33% less
  for 3t, 50% for 4t, 60% for 5t).

## Tick timeline (stated parts)

- P3 Prayer special: 3-tick window to deactivate, then a 1-tick window to reactivate. P4: explosions every 2 ticks × 6.

## As implemented (content/combat TfaOcellus.kt, TfaOcellusP1.kt .. TfaOcellusP4.kt)

Numbers and rotations come from the live captures (capture-values.md), timings from RuneLite (runelite-timings.md,
D-080). He spawns at (4125, 5679, level 1) as P1; the arena centre is his spawn + (1, 1). Every phase runs its own
per-tick script once he is engaged (his first swing or the first hit on him; hunt range 24), and a phase change
cancels the previous phase's loops. The first call sets `TFA_OCELLUS_MET` for raiders in the arena.

- **Pools (OPEN-OC2):** P1 has its own pool, 2,500 at 1-2 raiders, then 3,630 / 4,680 / 5,730 / 6,780 / 7,830 /
  8,880 at 3..8. P2, P3 and P4 share one pool of 5,250 at 1-2, then 7,610 / 9,820 / 12,020 / 14,230 / 16,430 /
  18,640: the Blade's damage is his. The old 1,000 HP Blade with half transfer is gone. P1 gone starts P2 (fresh
  shared pool); in P2 lethal damage leaves 1 HP (its end is a script); in P3 lethal damage leaves 1 HP and forces the
  quadrant check; P4 can die.
- **Line heal (OPEN-OC10):** every closing P1 line a raider stands on is counted; on that raider's next hit that lands
  on Ocellus they are healed 4 per line - 1 + 0..1 + 0..2 (1 line 3..6, 4 lines 14..18).
- **Censers (OPEN-OC14):** four loc censers (62693-62700, with an inactive form id + 1) are placed, one per quadrant
  shuffled each fight, 4 tiles beyond the quadrant's landing spot away from the centre; the fight starts in the
  quadrant diagonally opposite the Bandosian one. Used at range: Bandosian = melee, Armadylean = ranged (+10% damage of
  that style for 100 ticks), Zarosian = +500 special-energy units, Saradomin = +40 HP and +40 prayer; the censer then
  goes inactive for the fight.
- **P1 (16674, OC1):** from his first step, every 5 ticks a volley at every raider, one element a walk (fire = ranged,
  water = magic, re-picked each walk): prayed 0, else an accuracy roll then 2..24; lands +3 within 4 tiles, +4 beyond.
  He walks corner to corner of (+-8, +-8) from the centre (placed after 16 ticks of walking); at a corner (charge, 20
  ticks) 7 crack lines are laid 2 ticks apart from his three inner tiles in turn at a random raider's direction, 14
  tiles long (**OPEN-OC3**), each closing 5 ticks after it appears; the smash at C+18 hits (28..30, melee, lands C+19) anyone
  within 2 tiles of him.
  - A closing line: each raider on it takes 3..5 typeless. Unless every raider stands on it, it adds 2 to the meter
    (max 96) and heals him 2 (replaces +20 HP / 5).
  - At 66% then 25% (checked on volley ticks) he goes to the middle (centre - 1): four beats 8 ticks apart, each two
    diagonal lines (fuses 6 and 8), then the strike: at meter 0..10 "Death... awaits...", one shot each raider
    17..19 typeless landing +1 (within 4 tiles) or +2; above 10 "Embrace... annihilation..." for 80 (**OPEN-OC11**,
    never recorded). The meter resets. Then back to walking.
- **P2, the Blade (16675, OC2):** from P2 start T, a fixed script identical every fight: rounds of 6 volleys at T+11,
  T+53 and T+95, one volley every 5 ticks (R+0..R+25). Each volley lights the portal lanes 4 ticks ahead (loc 62685)
  on the walls of the quadrant of the first raider; one row and one column portal stay dark per volley (the safe
  intersection) following one of three fixed sequences of six (RuneLite): the first sequence random, then in turn per
  round. A raider not in the dark crossing takes 19..25 typeless when it fires (**OPEN-OC5**). "This... is enough..."
  at T+123; P3 starts at T+131 (**OPEN-OC4**), the HP carried over.
- **P3 (16677, OC3):** rotation of steps: triple, triple, then a big attack, repeating. Big attacks alternate spin and
  then Insolence, with Untether replacing Insolence when HP is within 8% above the next quarter (**OPEN-OC6**).
  - Tank: the 2nd raider in HUD (orb) order alive, the next each segment, wrapping; if the order is unknown, the
    nearest raider. Attacks from directly opposite the tank deal +10%.
  - Triple: a basic swing at the tank at +0 and +3, a slash at +6, next step +10. Basics 12..19, or 1..7 through
    Protect from Melee (**OPEN-OC12**), x1.2 while an Augmenter stands; the slash 25..28 at a random raider's tile,
    landing a tick later. If the tank is more than 1 tile away a hybrid volley (magic or ranged, 20 damage,
    **OPEN-OC12**) at everyone replaces the triple (7 ticks).
  - Spin: all prayers still on 6 ticks in are turned off; hits on each loop and the out (+7, +10, +13) of 15..27 on
    every raider not on Protect from Melee at that moment (**OPEN-OC7**); 16 ticks.
  - Insolence: says "Insolence...", 2 ticks later then every 5 ticks three superior slashes of 39..41 at random
    raiders' tiles.
  - Untether (33 ticks, **OPEN-OC13**): pools on every raider's tile at U+6, +10, +14, +18, landing 4 ticks later, 23..29
    (0 under Protect from Magic); U+29 35..42 typeless to everyone not under him, U+30 34..41 to those under him (18..21
    if hit at U+29).
  - Void bombs (**OPEN-OC15**): two batches a segment (first 15 ticks into P3, then 3 ticks after a move; the second
    1 tick after the first big attack that follows the first batch), one bomb per standing quadrant on non-tanks (all raiders if none),
    dropped when the raider has stood 2 ticks (at least 3 ticks after the mark), going off 2 ticks later for 6..11;
    each leaves a patch of 6..7 every 2 ticks that stays into P4.
  - Void vine (**OPEN-OC16**): one a segment on a non-tank, with the first triple after the segment's first spin unless
    within 8% of the next quarter; roots them 8 ticks, then 50 unless the vine was killed (damage unrecorded).
  - Rifts (44 HP, **OPEN-OC17**): at each segment start max(1, raiders / 2) of one kind, in turn Augmenter (+20% melee
    while standing), Void Rift (a 6..11 magic shot at a random raider every 4 ticks), healing (heals him 12 or 14 every
    4 ticks unless a raider stands on the line to him; its killer is healed 22). Any rift alive 30 ticks after it appears
    detonates for 39..41 on every raider.
  - Quadrants (**OPEN-OC9**): the quadrant falls at HP at or under 75 / 50 / 25 + 1%, at once unless he is spinning or
    unleashing (then when that ends): "Run!", 15 ticks later 50 typeless within 3 tiles of him (an untagged RuneLite
    value), then he moves to the next diagonal quadrant. The third move starts P4.
- **P4 (OC4):** "Enough!"; a 60-tick cycle from its start D. Deflect Melee D+0..30, Deflect Missiles/Magic D+31..59
  (**OPEN-OC8**): a deflected hit does 0 and returns a tenth of it the next tick; other hits are cut by weapon speed (33%
  at 3 ticks, 50% at 4, 60% at 5+).
  - Wind at D+4 and D+35: every raider loses half their health; an orb appears within 4 tiles; standing on it from W+8
    gives the health back, otherwise at W+25 (W+19 for the second wind) he heals that amount. Six explosions follow, 2
    ticks apart from W+2, on each raider's tile at that tick, landing next tick for 14..16; a raider who moved away has
    their next attack cut by 3 ticks.
  - Hybrid at D+24 and D+52: one kind for the party (16 magic : 13 ranged : 4 melee), 5..8 under the matching prayer,
    +1 a cycle from the third (max +3), 35 unprayed. Soul split at D+25 and D+53: magic or ranged at random, 40..45
    unprayed, 6..7 prayed, healing him a quarter of the damage dealt.
  - Void floor 9..11 a tick: a ring 2..4 tiles from him D+5..18; a quadrant D+37..48, a different one each cycle
    (**OPEN-OC8**: shapes). Patches from P3 persist.
  - Rifts at D+59: 2, 3, 4, then 5 every cycle, in turn healing, Void Rift, Augmenter.
