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
- **BL3 Walk length:** the first walk lasts **39-47 ticks** (Page; 23.5-28.2 s) or **38-46** (Strat); later walks last
  **34-42 ticks** (20.4-25.2 s); if no player attacked it during the previous down the walk is **4 ticks longer**. For
  **5 ticks after a turn or a speed change** it cannot go down, which can stretch a walk past its maximum.
- **BL4 Down:** it stops for **32 ticks (19.2 s)**; the stomp is on the **30th tick of inactivity** (Strat) / "after 19.2
  seconds" (Page); it deals **40-80** (Entry max 40) in the surrounding area (radius not stated), can be tick-eaten, and
  restores any drained Defence. The team has about 18 s (5 hits close, 4 from range with a 4-tick weapon). After the
  stomp it walks again for 34-42 ticks.
- **BL5 Speed by HP:** above 60.0% it **walks**; from 60% down to 40% it **runs** (two tiles at a time); at 39.9% and
  lower it alternates between walking and running **for every attack made on it, hit or miss**. Strat: "from 60-100% it
  will walk, 40-59.9% run, 39.9% and lower alternate" (boundary tick **OPEN-BL4**).
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

3 Jun 2021 modes added; 21 Jun 2018 and 8 Jun 2018 falling flesh slower; 8 Nov 2023 line-of-sight explanation (not a
change); 2 May 2019 the Bloat "should now fall down in the correct position" (the down animation). No Bloat entry after
that on the Page.

## Open list

OPEN-BL1 turn probability when eligible and how the direction is chosen; OPEN-BL2 falling-flesh frequency, landing delay,
target selection and radius; OPEN-BL3 Entry falling-flesh trigger and amounts; OPEN-BL4 exact HP boundaries for run /
alternate; OPEN-BL5 stomp radius; OPEN-BL6 path coordinates, speed in tiles per tick, and how the "alternate" toggle
persists; OPEN-BL7 Entry and Hard flies / stomp ranges (only the maximum is stated) and whether Entry walk and down timings
equal Normal; OPEN-BL8 first-walk range (conflict below); OPEN-BL9 4- / 3-player HP rounding. **9 open items.**

## Conflicts between sources

1. First walk: 39-47 ticks (Page) vs 38-46 (Strat).
2. Stomp time: "after 19.2 seconds" (Page) vs "on the 30th tick of inactivity" of 32 (Strat); both kept, stomp at tick 30.
3. Entry limbs: after the first stomp (Entry page) vs from 90% HP (Page, Normal rule).
4. Solo Entry HP 20% (Page) vs cache 25% base (TB11).
