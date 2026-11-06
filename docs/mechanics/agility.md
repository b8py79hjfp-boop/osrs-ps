# Agility

`content/skills/.../Agility.kt`, data in `AgilityData.kt`. The Wiki side of the data is re-runnable with
`tools/wiki-import/agility_courses.py` (course order, loc ids, option text, level, xp, and each loc's cache placement from
`cache-dump map` squares). Shared skilling rules: skilling.md.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-06:
- (G) "Gnome Stronghold Agility Course", (D) "Draynor Village Rooftop Course", (A) "Al Kharid Rooftop Course",
  (V) "Varrock Rooftop Course": obstacle order, xp, "Chance to fail", lap totals, damage ranges of the failing obstacles; and each
  obstacle's page (e.g. "Log balance (Gnome Stronghold Agility Course)", "Tightrope (Al Kharid Rooftop Course)"): loc ids, option text,
  required level
- (M) "Marks of grace" (https://oldschool.runescape.wiki/w/Marks_of_grace): spawn cooldown, chances, level penalty, spawn tiles
- (L) the cache (cache 2757): loc placements (`cache-dump map`), loc ops (all obstacles are op 1), collision (`cache-dump collision`), gameval
  seqs by name (`human_walk_logbalance` 762, `human_reachforladder` 828, `human_climbing_down` 740, `human_pipesqueeze` 746, `human_ropeswing_long` 751,
  `human_walk_sidestepl` 756, `agility_shortcut_wall_jumpdown` 2586, `human_jump_hurdle` 1603, `zipline_slide` 1602), obj `grace` 11849

## Rules

| # | Rule | Source |
|---|---|---|
| AG1 | An obstacle is a loc with op 1 (cache option text in the table). It needs the course's Agility level (current level). The click plays the obstacle's animation, delays the player for its tick count, then moves the player to the far side with `teleport`, plays animation -1 and gives the obstacle's xp. One crossing per click. | G, D, A, V, L |
| AG2 | Course data: obstacles in lap order, xp per obstacle, level, lap bonus (below). The Gnome course gives 50 xp on finishing the pipe (Wiki "Course Completion Bonus"); on the rooftop courses the Wiki folds the bonus into the last obstacle's xp (Draynor crate 79, Varrock edge 143.7), so no separate bonus is given. Lap totals match the Wiki: Gnome 110.5, Draynor 120, Al Kharid 216, Varrock 270. | G, D, A, V |
| AG3 | Lap tracking: starting the first obstacle begins a lap; each obstacle must be the next one in order or the lap is lost; the last obstacle of an unbroken lap completes it. A completed lap gives the lap bonus (Gnome) and the mark of grace roll. | OPEN-AG2 (modelled; not stated on the pages read) |
| AG4 | Marks of grace: when a lap completes and at least three real minutes have passed since the last mark spawned (the minute of the last spawn is saved with the player, so it survives logout), a mark spawns with chance 2/6; with the player's unboosted Agility 20 or more over the course's level the chance is 80% lower (2/30). It spawns on one of the course's spot tiles, picked uniformly, for that player only. | M |
| AG5 | The Gnome course's 2/6 is the "all other courses" figure; the Wiki gives no Gnome-specific chance (OPEN-AG5). | M |

### Courses (obstacle loc id / option / level / xp / landing)

Gnome Stronghold (level 1, lap bonus 50):

| # | Obstacle | Loc | Xp | Moves the player to |
|---|---|---|---|---|
| 1 | Log balance | 23145 Walk-across | 10 | (2474, 3429, 0) after 7 ticks |
| 2 | Obstacle net | 23134 Climb-over | 10 | 3 tiles south, level 1 (2 ticks) |
| 3 | Tree branch | 23559 Climb | 6.5 | (2473, 3420, 2) (2 ticks) |
| 4 | Balancing rope | 23557 / 23558 Walk-on | 10 | (2483, 3420, 2) (6 ticks) |
| 5 | Tree branch | 23560 Climb-down | 6.5 | (2487, 3420, 0) (2 ticks) |
| 6 | Obstacle net | 23135 Climb-over | 10 | 2 tiles north (2 ticks) |
| 7 | Obstacle pipe | 23138 / 23139 Squeeze-through | 7.5 | 7 tiles north (7 ticks) |

Draynor Village Rooftop (level 1; "can no longer be failed" since 8 May 2024):

| # | Obstacle | Loc | Xp | Landing |
|---|---|---|---|---|
| 1 | Rough wall | 11404 Climb | 5 | (3102, 3279, 3) |
| 2 | Tightrope 1 | 11405 Cross | 8 | (3091, 3276, 3) |
| 3 | Tightrope 2 | 11406 Cross | 7 | (3092, 3266, 3) |
| 4 | Narrow wall | 11430 Balance | 7 | (3088, 3261, 3) |
| 5 | Wall | 11630 Jump-up | 10 | (3088, 3255, 3) |
| 6 | Gap | 11631 Jump | 4 | (3096, 3256, 3) |
| 7 | Crate | 11632 Climb-down | 79 | (3103, 3261, 0) |

Al Kharid Rooftop (level 20): rough wall 11633 (12 xp) -> (3273, 3192, 3); tightrope 14398 (36) -> (3272, 3172, 3); cable 14402 (48) ->
(3284, 3166, 3); zip line 14403 Teeth-grip (48) -> (3317, 3163, 1); tropical tree 14404 (12) -> (3316, 3175, 2); roof top beams 11634 Climb (6) ->
(3316, 3180, 3); tightrope 14409 (18) -> (3302, 3187, 3); gap 14399 Jump (36) -> (3300, 3196, 0).

Varrock Rooftop (level 30): rough wall 14412 (13.5) -> (3220, 3414, 3); clothes line 14413 Cross (23) -> (3208, 3414, 3); gap 14414 Leap (19) ->
(3192, 3415, 1); wall 14832 Balance (28) -> (3193, 3402, 3); gap 14833 (10) -> (3208, 3397, 3); gap 14834 (24.5) -> (3232, 3402, 3); gap 14835 (4.5)
-> (3236, 3408, 3); ledge 14836 Hurdle (3.5) -> (3236, 3415, 3); edge 14841 Jump-off (143.7) -> (3237, 3414, 0).

Mark of grace spots (Wiki "Mark of grace spawns", plane-0 pins lifted to the roof level): Gnome (2486, 3421, 2); Draynor (3099, 3279, 3), (3088, 3274, 3),
(3092, 3266, 3), (3099, 3259, 3); Al Kharid (3315, 3161, 1), (3266, 3166, 3), (3284, 3172, 3), (3276, 3185, 3), (3302, 3187, 3). Varrock has none (OPEN-AG3).

## Tick timeline

Click at T-1, arrival (or the loc already adjacent) at T: animation starts and the player is delayed (`ticks` per obstacle); at T + ticks the player
is teleported, the animation cleared and the xp given; the next click is acted on in the next input phase. Verified headlessly: the Gnome lap
gives 10, 20, 26.5, 36.5, 43, 53 and 110.5 xp (53 + 7.5 + 50) and the Draynor lap 5, 13, 20, 27, 37, 41 and 120.

## OPEN

- **OPEN-AG1:** movement is a jump. Plugin-api has no forced-movement (exact move) message and protocol-api has no ExactMove visual (RSProt has the
  encoder, the adapter does not use it), so the player stays on the starting tile for the obstacle's ticks and then appears on the far tile. The
  animation ids are gameval names (see above), not checked on a client; the tick counts are the walking distance or a short estimate, not the real
  durations (the Wiki only has whole laps: Gnome 34 s, Draynor 72 ticks, Al Kharid 107 ticks).
- **OPEN-AG2:** failing. Al Kharid tightrope 1 and zip line (1-5 damage) and Varrock clothes line and wall (3-8 and 2-5 damage) can be failed (Wiki
  "Chance to fail"), but no page gives the chance or where a failed player lands, so obstacles never fail. When they do, the hit should be a
  `DamageRequest` (plugin-api). Lap tracking (AG3) is modelled, not sourced.
- **OPEN-AG3:** geometry. Landing tiles are derived from the cache (ropes' end decorations, free roof tiles in the collision render) for Gnome,
  Draynor and Al Kharid, and are the tile beside the next obstacle for Varrock; none are checked against a client or the real course. The cache marks
  most rooftop tiles as non-walkable, so a landing tile must be next to the next obstacle's loc. Varrock's mark-of-grace tiles are not set because
  their levels are unknown. Players are not forced onto the obstacle's start tile (nets, pipes and branches keep the player's own tile).
- **OPEN-AG4:** marks of grace use the ground-item rules of ground-items.md: untradeable items are visible only to the owner and disappear after 300 ticks
  (G3), the Wiki says 10 minutes (1000 ticks) and that the timer is paused while logged out. Rolling again on the Seers and Ardougne diaries, and the
  2-minute cooldown reductions, are not implemented (no diary state).
- **OPEN-AG5:** the mark chance of the Gnome course.
- **OPEN-AG6:** the Agility-based run energy restore (Wiki "Run energy"), the Agility cape, graceful outfit (run energy weight) and the lap counters / messages
  are not implemented; other courses and shortcuts are not covered.
