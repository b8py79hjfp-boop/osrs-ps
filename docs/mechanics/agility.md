# Agility

`content/skills/.../Agility.kt`, data in `AgilityData.kt`. The Wiki side of the data is re-runnable with
`tools/wiki-import/agility_courses.py` (course order, loc ids, option text, level, xp, and each loc's cache placement from
`cache-dump map` squares). Shared skilling rules: skilling.md.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-06:
- (G) "Gnome Stronghold Agility Course", (D) "Draynor Village Rooftop Course", (A) "Al Kharid Rooftop Course",
  (V) "Varrock Rooftop Course": obstacle order, xp, "Chance to fail", lap totals, damage ranges of the failing obstacles; and each
  obstacle's page (e.g. "Log balance (Gnome Stronghold Agility Course)", "Tightrope (Al Kharid Rooftop Course)"): loc ids, option text,
  required level
- (C) "Canifis Rooftop Course", "Falador Rooftop Course", "Seers' Village Rooftop Course", "Pollnivneach Rooftop Course",
  "Rellekka Rooftop Course", "Ardougne Rooftop Course" and their obstacle pages (e.g. "Gap (Canifis Rooftop Course)", "Market stall
  (Pollnivneach Rooftop Course)"): order, loc ids, option text, level, xp, success charts ("Skilling success chart" low/high), fall damage
- (S) "Shortcut" (list of shortcuts: level and xp) and the shortcut pages ("Underwall tunnel (Falador)", "Underwall tunnel (Draynor
  Village)", "Fence (Varrock)")
- (B) "Barbarian Outpost Agility Course", "Wilderness Agility Course" and their obstacle pages (read; see OPEN-AG10)
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
| AG4 | Marks of grace: when a lap completes and at least three real minutes have passed since the last mark spawned (the minute of the last spawn is saved with the player, so it survives logout), a mark spawns with the course's chance: 2/6 by default, 2/5 Rellekka, 2/3 Canifis and Ardougne; with the player's unboosted Agility 20 or more over the course's level the chance is 80% lower (denominator times five), except on Canifis ("coding error" per the Wiki). It spawns on one of the course's spot tiles, picked uniformly, for that player only. The Rellekka, Pollnivneach, Seers' and Ardougne diary timers and rerolls are not implemented (OPEN-AG8). | M |
| AG5 | The Gnome course's 2/6 is the "all other courses" figure; the Wiki gives no Gnome-specific chance (OPEN-AG5). | M |

| AG6 | Failing: an obstacle with a `Fail` entry rolls `statRandom(AGILITY, low, high)` (the Wiki "Skilling success chart" low/high out of 256, with the current level). On a failure the lap state is kept (the obstacle is tried again), the player is dropped to the obstacle's fall tile after one tick and takes `floor(hitpoints / 17) + 2` damage as a `DamageRequest` (the combat module applies it and can kill; without it the hit is non-lethal). Set only where the chart and the damage formula are both on the Wiki: Canifis gap 3 (low 175, high 300), Pollnivneach market stall (low 60, high 300). | C |
| AG7 | Shortcuts (`Shortcuts.kt`, table in `Shortcut.kt`): a loc of the table with its option needs the shortcut's Agility level (current), plays the animation for its ticks, moves the player to the end of the shortcut farther from them and gives the Wiki xp (0 for the three in the table). Two-way, no failure, no other skill or quest. | S |

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
(3092, 3266, 3), (3099, 3259, 3); Al Kharid (3315, 3161, 1), (3266, 3166, 3), (3284, 3172, 3), (3276, 3185, 3), (3302, 3187, 3); Varrock (3185, 3394, 3),
(3191, 3394, 3), (3196, 3415, 1), (3238, 3406, 3), (3220, 3401, 1) (the pins give only x and z; the plane is the roof the cache collision shows free there, the
nearest obstacle's where it shows none: OPEN-AG3).

Higher rooftop courses (all obstacles op 1; xp, level and loc ids from the course and obstacle pages; "fail" = the Wiki says it can be failed, "chart" = a success chart exists):

| Course (level, marks) | Obstacles in order: loc / xp |
|---|---|
| Canifis (40; 2/3, no 20-over reduction) | tall tree 14843 Climb 10; gap 14844 8; gap 14845 8; gap 14848 10 (fail, chart 175/300); gap 14846 8; pole-vault 14894 Vault 10; gap 14847 11; gap 14897 175 (the lap's 240 xp includes it) |
| Falador (50; 2/6) | rough wall 14898 11; tightrope 14899 22; hand holds 14901 61 (fail until 66, no chart); gap 14903 27; gap 14904 26; tightrope 14905 61; tightrope 14911 53; gap 14919 30; ledge 14920 14; ledge 14921 13; ledge 14922 + 14923 13; ledge 14924 14; edge 14925 241 |
| Seers' Village (60; 2/6) | wall 14927 Climb-up 45; gap 14928 20 (fail until 79, no chart); tightrope 14932 20 (fail until 79, no chart); gap 14929 35; gap 14930 15; edge 14931 435 |
| Pollnivneach (70; 2/6) | basket 14935 Climb-on 10; market stall 14936 Jump-on 45 (fail, chart 60/300); banner 14937 Grab 65; gap 14938 Leap 35; tree 14939 Jump-to 75; rough wall 14940 5; monkeybars 14941 55; tree 14944 Jump-on 60; drying line 14945 Jump-to 540 (666 with the Desert hard diary: OPEN-AG8) |
| Rellekka (80; 2/5) | rough wall 14946 20; gap 14947 Leap 30; tightrope 14987 40 (fail until 85, 10% of the hitpoints, no chart; level 80); gap 14990 Leap 85 (level 80); gap 14991 Hurdle 25; tightrope 14992 105; pile of fish 14994 Jump-in 475 (615 with the Fremennik hard diary: OPEN-AG8) |
| Ardougne (90; 2/3, one spot) | wooden beams 15608 Climb-up 43; gap 15609 65 (level 90, fail until 95, no chart); plank 26635 Walk-on 50 (level 1); gap 15610 21 (level 90, fail until 95, no chart); gap 15611 28 (level 1); steep roof 28912 Balance-across 57; gap 15612 625 |

Mark of grace spots, lifted like Varrock's (planes: OPEN-AG3): Canifis (3506, 3495), (3501, 3505), (3490, 3501), (3478, 3496), (3478, 3484) on plane 3;
Falador (3045, 3345), (3049, 3357), (3019, 3345), (3013, 3345), (3015, 3333) on 3; Seers' (2727, 3498) on 3, (2707, 3493), (2710, 3493), (2712, 3481), (2698, 3465) on 2;
Pollnivneach (3347, 2969), (3353, 2975), (3359, 2983) on 1, (3357, 3002), (3362, 2993) on 2; Rellekka (2623, 3675), (2628, 3652), (2629, 3653), (2641, 3650), (2642, 3651) on 3;
Ardougne (2657, 3318) on 3.

Shortcuts (AG7): Falador underwall tunnel (level 26; locs 16527, 16528, "Climb-into"; ends (2948, 3309) and (2948, 3313)); Draynor underwall tunnel (level 42; locs 19032,
19036; ends (3066, 3257) and (3070, 3257)); Varrock fence (level 13; loc 16518 "Jump-over"; ends (3240, 3335) and (3240, 3334)). Level and xp from the Wiki list; the ends are the
free tiles on both sides in the cache collision.

## Tick timeline

Click at T-1, arrival (or the loc already adjacent) at T: animation starts and the player is delayed (`ticks` per obstacle); at T + ticks the player
is teleported, the animation cleared and the xp given; the next click is acted on in the next input phase. Verified headlessly: the Gnome lap
gives 10, 20, 26.5, 36.5, 43, 53 and 110.5 xp (53 + 7.5 + 50) and the Draynor lap 5, 13, 20, 27, 37, 41 and 120.

## OPEN

- **OPEN-AG1:** same-level obstacles use forced movement (`exactmove`, plugin-api `PlayerRef.exactMove`, RS Mod semantics: the
  player is on the far tile at once, the client glides there over the obstacle's ticks). Level changes (climbing) stay a jump
  after the obstacle's ticks. The animation ids are gameval names, not checked on a client; the tick counts are the walking
  distance or a short estimate, not the real durations (the Wiki only has whole laps: Gnome 34 s, Draynor 72 ticks, Al Kharid
  107 ticks).
- **OPEN-AG2:** failing, partly resolved by AG6. Without a chart on the Wiki the obstacle never fails: Al Kharid tightrope 1 and zip line (1-5 damage), Varrock clothes
  line and wall (3-8 and 2-5), Falador hand holds (until 66), Seers' gap 1 and tightrope (until 79), Rellekka tightrope 1 (until 85), Ardougne gaps 1 and 2 (until 95),
  Pollnivneach (nothing from 85). Only the cut-off level is known, not the low/high of the chart. Lap tracking (AG3) is modelled, not sourced.
- **OPEN-AG3:** geometry. Landing tiles are derived from the cache (ropes' end decorations, free roof tiles in the collision render) for Gnome,
  Draynor and Al Kharid, and are the tile beside the next obstacle for Varrock; none are checked against a client or the real course. The cache marks
  most rooftop tiles as non-walkable, so a landing tile must be next to the next obstacle's loc. Varrock's mark-of-grace tiles are not set because
  their levels are unknown. Players are not forced onto the obstacle's start tile (nets, pipes and branches keep the player's own tile).
- **OPEN-AG4:** marks of grace last 1000 ticks (Wiki: 10 minutes) through `createOnGround(..., lifetime)`; the Wiki's pause while logged out is not modelled.
- **OPEN-AG5:** the mark chance of the Gnome course.
- **OPEN-AG6:** the Agility cape and the lap counters / messages are not implemented. (Run energy restore and the graceful boost exist on main, run-energy.md.)
- **OPEN-AG7:** failure details: the animation, the ticks before the fall, the message and the fall tiles are not on the Wiki. The fall tile is the nearest free ground tile
  (cache collision) under the obstacle; the delay is one tick; the message is "You slip and fall to the ground." (not sourced). The damage of Rellekka's first tightrope
  (10% of the current hitpoints, rounded down) is known but its chance is not.
- **OPEN-AG8:** achievement diary effects: Pollnivneach drying line 666 xp and a 1/3 two-minute mark timer (Desert hard), Rellekka pile of fish 615 xp and the same
  timer (Fremennik hard), Seers' mark rerolls 5/10/15% (Kandarin easy/medium/hard), Ardougne elite timer and 25% reroll. Diary state lives in `content/diaries`
  (varbits per tier), which `content/skills` may not depend on. Proposal: a plugin-api query for a completed diary tier backed by the diary varbits.
- **OPEN-AG9:** shortcuts: only three simple two-way ones are in the table. The rest of the Wiki list (about 160) needs per-shortcut geometry (diagonal or multi-tile
  crossings, level changes, failure with its own xp, extra Strength/Ranged/grapple requirements, quests) that the cache collision does not show clearly enough, or is
  failable without a chart (log balances, stepping stones). Quest-gated shortcuts are skipped: `quest-states.tsv` has the varbit per quest, but most quests are not
  implemented (a handler could compare the quest varbit/varp with its `end` value).
- **OPEN-AG10:** courses not implemented. Barbarian Outpost (level 35; ropeswing 23131, log 23144, net 20211, ledge 23547, ladder 16682, three crumbling walls 1948, bonus
  46.3 Agility + 41.3 Strength; charts 200/280, 180/260, 200/280): the ladder loc 16682 has no placement in the cache (a multi-loc child, the placed ids are 16680, 16683,
  42487), failure damage and landing are not on the Wiki, the course is entered after Alfred Grimhand's Barcrawl and the mark spot is only "after the balancing ledge" at
  an unknown tile; the three walls share one loc id, which needs per-placement dispatch. Wilderness (level 52/49; pipe 23137, ropeswing 23132, stepping stones 23556,
  log 23542, rocks 23640 with the 498.9 lap bonus; charts 200/250, 180/250, 200/250; damage `floor(hp * 0.15) + 1` and `floor(hp * 0.2) + 1`): the dungeon fall tile is not
  on the Wiki and the course sits in a combat area with a dispenser (150,000 coin fee, looting bag, tickets). Not attempted for lack of Wiki or cache data: Ape Atoll (needs
  Monkey Madness I), Werewolf (dungeon course with sticks), Shayzien basic/advanced, Prifddinas (Song of the Elves), Colossal Wyrm (termite currency), Agility Pyramid, Brimhaven
  Agility Arena (minigame, tickets). The Grace shop (Rogues' Den, shop 148 in `shops.tsv` / `shop-items.tsv`) and the Agility Arena Store (shop 3) already exist with the Wiki
  stock; the marks-of-grace prices are the obj cost in the cache (hood 35, set 260).
