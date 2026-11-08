# Pestilent Bloat (room 2)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Pestilent Bloat" (Page), "Theatre of Blood/Strategies"
(Strat), "Theatre of Blood/Entry Mode" (Entry), "Theatre of Blood/Hard Mode" (Hard), "Chest (Theatre of Blood)"
(Supply), "Bandages (Theatre of Blood)"; cache 2757. Raid-wide rules: [../tob.md](../tob.md). Tick timelines below list
only what a source states; everything else is `OPEN-BL<n>`. Seconds are converted at 0.6 s per tick.

## Data

- NPC ids: **Entry 10812**, **Normal 8359** (`TOB_BLOAT`), **Hard 10813**. Combat level **312 / 870 / 1044**; size 5;
  undead (Salve works); ranged attack, **attack speed 1** (flies hit every tick while active); aggressive; poison and
  venom immune. XP bonus 22.5 / 85 / 85.
- Max hit: flies **8 / 20 / 20**, stomp **40 / 80 / 80** (Entry / Normal / Hard).
- HP (infobox, scaled): Entry 1,280 for five (cache base 320), Normal 2,000, Hard 2,400. By party size: Normal 1-3
  players 1,500, 4 players 1,750, 5 players 2,000; Hard 1,800 / 2,100 / 2,400; Entry (TB11) 320 / 608 / 864 / 1,088 /
  1,280 for 1-5 players.
- Stats: Entry att 100, str 180, def 80, magic 120, ranged 72, attack bonus +60, strength bonus +46, ranged attack +72,
  ranged strength +2; Normal and Hard att 250, str 340, def 100, magic 150, ranged 180, attack bonus +150, strength
  bonus +82, ranged attack +180, ranged strength +4. Defence bonuses: stab / slash / crush Entry 10 / 5 / 10, Normal and
  Hard 40 / 20 / 40; magic 600 and light / standard / heavy 800 in all modes. Cache stats arrays match
  (Normal [250, 100, 340, 2000, 180, 150]).
- Drops: the book "The Butcher" (always, unless read). Obj `TOB_BOOK_BLOAT` 22506.

## Fight

- **BL1 Start:** the Bloat walks as soon as the party is in its room (a player has crossed the barrier, TB8). It walks
  a fixed path around the central tank; on entering the direction is random, the start position predictable. On entry it
  **cannot turn for 32 ticks** (19.2 s).
- **BL2 Flies:** while active the Bloat attacks anyone in line of sight with flies: **10-20 per tick** in Normal (Page;
  Strat "up to 20"), Protect from Missiles reduces it by **25%**. The flies **spread** to other players who have line of
  sight on the hit player, even if the Bloat cannot see them. Line of sight is a custom check: every tile on the side of
  the 5x5 Bloat nearest to the player is tried and the attack goes through if any has a clear view (8 Nov 2023, Mod Ash).
  Damage to the Bloat while it is active (walking) is **halved, rounded down**.
- **BL3 Walk length:** the first walk lasts **39-47 ticks** (Page, which prints "23.5 to 28.2 seconds"; 39 ticks is 23.4 s at 0.6 s per tick, so
  the page's 23.5 is rounded up; 47 ticks is 28.2 s) or **38-46** (Strat); later walks last
  **34-42 ticks** (20.4-25.2 s); if no player attacked it during the previous down the walk is **4 ticks longer**. For
  **5 ticks after a turn or a speed change** it cannot go down, which can stretch a walk past its maximum.
- **BL4 Down:** it stops for **32 ticks (19.2 s)**; the stomp is on the **30th tick of inactivity** (Strat) / "after 19.2
  seconds" (Page); it deals **40-80** (Entry max 40) in the surrounding area (radius not stated), can be tick-eaten, and
  restores any drained Defence. The team has about 18 s (5 hits close, 4 from range with a 4-tick weapon). After the
  stomp it walks again for 34-42 ticks.
- **BL5 Speed by HP:** at 60% or more it **walks**; between 40% and 60% it **runs** (two tiles at a time); at 39.9% and
  lower it alternates between walking and running **for every attack made on it, hit or miss**. Sources: Page "up to
  60.0%, it will always walk; between 40% and 60%, it will always run" and "At 39.9% and lower, the Bloat will
  alternate between walking or running for every attack made on it regardless if it hits or not"; the Entry page
  "from 60-100% of his health, he will walk, at 40-59.9% he will run, and from 39.9% and lower he will alternate
  between running and walking depending on how many hits he takes"; Strat "between 40% to 60% of its health, it will
  always run, moving two tiles at a time. Below 40%, it can either run or walk, alternating between walking and running
  for every hit made on it upon falling below 40% health". Boundary difference: at exactly 60.0% the Entry page and the
  Page's "up to 60.0%" walk while Strat's "40% to 60%" runs; the 40.0% boundary and 39.9% agree up to wording (exact
  boundary **OPEN-BL4**, the Entry/Page reading is used).
- **BL6 Direction changes:** it may change direction while walking, with a **32-tick cooldown** (Normal, Entry; starts at
  32 on room entry; counts down only while walking; the timer persists between walks). Whether a turn happens when
  eligible is random (probability not stated, **OPEN-BL1**).
- **BL7 Falling flesh (mutilated hands):** shadows on the floor mark where a limb lands; a hit deals **30-50** and
  **stuns** the player for a few seconds. Normal: only while the Bloat walks and once HP is below **90%**. Landing delay
  was lengthened by one tick on 8 Jun 2018 and one more on 21 Jun 2018 (numbers **OPEN-BL2**).
- **BL8 Post-room:** when it dies the first supply chest opens (TB22, TB23).

## Per-mode differences

- **Entry:** flies 8 max, stomp 40 max, HP 1,280 at five players; "after Bloat's first stomp" limbs begin to fall (Entry
  page; differs from the Normal 90% trigger, **OPEN-BL3**); the solo Bloat "can scale down to 20% of its HP" is the
  pre-2023 text (cache base 25%, TB11).
- **Hard:** falling flesh falls **all the time**, from the moment the first player enters, and keeps falling while the
  Bloat is down; the Bloat turns more: eligible every **16 ticks** (Normal 32) and can turn several times in one walk.
  HP 2,400. Other numbers as Normal.

## Tick timeline (stated parts)

- Room entry: no turn for 32 ticks; first walk 39-47 (or 38-46) ticks.
- Walk (34-42 ticks) -> down 32 ticks, stomp on the 30th down tick -> walk again. +4 walk ticks if unattacked; 5-tick
  no-down window after any turn or speed change. Turn cooldown 32 ticks (Hard 16).
- Flies: every tick while active, to every player with line of sight.

## Changes up to 2026-11-03 that matter

Bloat page changelog: 3 Jun 2021 modes added; 21 Jun 2018 and 8 Jun 2018 falling flesh slower. 8 Nov 2023 line-of-sight
explanation (Mod Ash; not a change). The main ToB page changelog (not the Bloat page) has 2 May 2019: "The Pestilent bloat
within the Theatre of Blood should now fall down in the correct position" (what "correct position" means is not stated).
No Bloat page entry after 3 Jun 2021.

## As implemented (content/combat TobBloat.kt)

Mode from the NPC id (`TobModes.of`). Entry damage = Normal / 2 (floor) where noted. HP: raids-tob `TobScaling`. Arena = the 16 x 16
square from its spawn tile (its SW corner); "raiders" = living players inside it. A per-tick script runs from spawn (`attack` is a
no-op that re-arms +5).

- **Start (BL1):** waits until a raider is in the arena (no walking before). Then direction clockwise/anticlockwise 50/50, first
  sleep at tick + **38..47** (uniform), turn lock **32** (BL6).
- **Awake tick order:** walk, flies, falling flesh, then sleep check: sleeps once `tick >= awakeUntil` and `tick >= noDownUntil`.
  Later walks last **38..47** too (+**4** if nobody hit it while it was down, BL3), not the doc's 34-42 (capture).
- **Walk (BL1/BL5/BL6):** ring of four corners `(0,0) (11,0) (11,11) (0,11)` from the arena SW; one tile a tick walking, two running,
  turning at corners. HP% = `hp x 100 / maxHp` (floor): `>= 60` walks; `40..59` runs; `< 40` keeps its current gait and **every
  incoming hit from a player** flips it (**OPEN-BL4**: so exactly 60% walks, exactly 40% runs). A change of gait to running sets
  "no sleep" for **5** ticks. Direction turn: the cooldown (32, Hard 16) counts down on walking ticks; once 0 each walking tick has
  1/**8** to reverse (**OPEN-BL1**, the odds are an assumption) and resets the cooldown and sets the 5-tick no-sleep window.
- **Damage to it:** while walking every hit is `damage / 2` (floor); while down full damage (and marks "hit while down").
- **Flies (BL2):** each walking tick, raiders with line of sight and not under it ("seen") plus any other raider that a seen raider
  has line of sight to and who is on a different tile ("spread"). Each: graphic, `10..20` (Entry `5..8`, **OPEN-BL9**: the code
  KDoc label for Entry flies; this doc's BL9 is the 3/4-player HP rounding, a clash), Protect from Missiles x 75 / 100, queued +1,
  typeless. Not done while down. Hard same as Normal.
- **Sleep (BL4):** `anim TOB_BLOAT_SLEEP`. At down tick **+30** the ready anim; **+31** the stomp: every raider with gap 1..**6** from
  its footprint and line of sight takes `40..80` (Entry halved, 20-40), typeless, immediately (no delay), not those under it
  (gap 0); then Defence restored to at least base. **+33** it rises (walk anim), next awake time set. Hard flesh keeps falling
  while down; Normal/Entry not.
- **Falling flesh (BL7, OPEN-BL2/BL3):** active: Normal walking and `hp% < 90`; Hard always (also down); Entry walking and after its
  first stomp. A wave when `tick >= nextWave` and raiders exist; next wave in **4** ticks with 55/83 chance else **6**; **16** or **15**
  (50/50) random tiles in the 16 x 16 arena (duplicates collapse), shadow graphics cycling flesh1-4; **3** ticks later splat graphic
  and every raider standing on a tile takes `30..50` (Entry halved) typeless, stun graphic and frozen for **5** ticks (**OPEN-BL8
  code label**, this doc's BL8 is the first-walk range: clash).
- **Death:** plays the death anim only; book, chest and restores belong to raids-tob.
- Not modelled: Hard-specific flies/stomp numbers (**OPEN-BL7**), the doc's "stomp can be tick-eaten" (it is instant), the exact
  boundary rules beyond the above.

## Open list

OPEN-BL1 turn probability when eligible and how the direction is chosen; OPEN-BL2 falling-flesh frequency, landing delay,
target selection and radius; OPEN-BL3 Entry falling-flesh trigger and amounts; OPEN-BL4 exact HP boundaries for run /
alternate; OPEN-BL5 stomp radius; OPEN-BL6 path coordinates, speed in tiles per tick, and how the "alternate" toggle
persists; OPEN-BL7 Entry and Hard flies / stomp ranges (only the maximum is stated) and whether Entry walk and down timings
equal Normal; OPEN-BL8 first-walk range (conflict below); OPEN-BL9 4- / 3-player HP rounding. **9 open items.**

## Conflicts between sources

1. First walk: 39-47 ticks (Page) vs 38-46 (Strat).
2. Stomp time: "after 19.2 seconds" (Page) vs "on the 30th tick of inactivity" of 32 (Strat); both kept, stomp at tick 30.
3. Solo Entry HP 20% (Page) vs cache 25% base (TB11).
4. Speed boundary at 60.0%: walk (Entry page, Page "up to 60.0%") vs run (Strat "between 40% to 60%"): BL5.

Not a conflict: Entry limbs "after Bloat's first stomp" (Entry page) against the 90% HP trigger is the Entry rule against the
Normal rule (see Per-mode differences, OPEN-BL3).
