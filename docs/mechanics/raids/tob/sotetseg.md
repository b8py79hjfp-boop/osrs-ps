# Sotetseg (room 4)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Sotetseg" (Page), "Theatre of Blood/Strategies" (Strat),
"Theatre of Blood/Entry Mode" (Entry), "Theatre of Blood/Hard Mode" (Hard), "Theatre of Blood" changelog (Main),
"Chest (Theatre of Blood)" (Supply); cache 2757 (`cache-dump config npc <id>`; stats array order attack, defence,
strength, hitpoints, ranged, magic; gamevals `TOB_SOTETSEG_*`). Raid-wide rules: [../tob.md](../tob.md). Tick timelines
below list only what a source states; everything else is `OPEN-SO<n>`. Seconds are converted at 0.6 s per tick.

## Data

- NPC ids: **Entry 10864** (non-combat) / **10865** (combat); **Normal 8387** (`TOB_SOTETSEG_NONCOMBAT`) / **8388**
  (`TOB_SOTETSEG_COMBAT`); **Hard 10867 / 10868**. Cache also has `TOB_SOTETSEG_CREEPER` 8389 (size 3, HP 1; the
  maze-tornado NPC, presumed). Combat level **336 / 995 / 1195**; size 5; attack speed **5**; crush (melee) and magic;
  not aggressive; poison and venom immune. XP bonus 15 / 37.5 / 40. Examine: "An intruder from the Shadow Realm,
  corrupted through haemalchemy."
- Max hit: Entry **20** melee, **22** ranged, **22** magic; Normal and Hard **45** melee, **50** ranged, **50** magic.
- HP: Entry cache base 560, infobox 2,240 for five; Normal and Hard 4,000. By party size: Normal and Hard 1-3 players
  3,000, 4 players 3,500, 5 players 4,000; Entry (TB11) 560 / 1,064 / 1,512 / 1,904 / 2,240 for 1-5 players.
- Stats: Entry att 180, str 140, def 150, magic 150, ranged 150, strength +20, magic attack -10, magic strength +26,
  ranged attack -10, ranged strength +26; defence stab / slash / crush 30, magic 10, light / standard / heavy 120.
  Normal att 250 (Hard **350**), str 250, def 200, magic 250, ranged 250, strength +48, magic attack -10, magic
  strength +60, ranged attack -10, ranged strength +60; defence stab / slash / crush 70, magic 30, ranged 150. The cache
  stats arrays agree (Normal [250, 200, 250, 4000, 250, 250], Hard attack 350).
- **Defence floor:** its Defence level can be lowered to a minimum of **100**; any drain is **reset each time players are
  transported to the shadow realm** (Page; Strat repeats it).
- Drops: book "The Shadow Realm" (always unless read; obj `TOB_BOOK_SOTETSEG` 22510); Long bone 1/400; Curved bone
  1/5,012.5 (Page; **OPEN-SO1** whether the bones are raid drops in all modes: the wiki lists them on the NPC).

## Fight

- **SO1 Layout:** Sotetseg stands **immobile in the northern part of a 14 x 15 tile arena**. Players pick positions
  around it (trio: east, west, north-west; 4-man: the corners of the corresponding row / column; 5-man: one more on the
  south-centre tile, Strat) to lengthen projectile travel.
- **SO2 Melee:** a horn lunge up to **45** (22 with Protect from Melee) against a player in melee range; the hitsplat is
  applied **one tick after** the attack and it cannot be tick-eaten (Page, Strat).
- **SO3 Projectiles:** it fires a small **red** projectile (magic; blocked fully by Protect from Magic). When it strikes
  a player it **splits into two more**, one **red** (magic) and one **grey** (ranged, blocked by Protect from Missiles),
  that ricochet to other players (Page; 21 Jun 2018: the magic and ranged hits can no longer stack on one player on the
  same game tick). Each is a successful hit of up to **50** when unprotected. Failing to protect against one **disables
  the player's protection prayers for 3 seconds (5 ticks)** (reduced on 2 Aug 2018); Retribution, Smite and Redemption
  still work. Up close the projectiles cannot be tick-eaten; at distance they can (Page). 24 Jan 2019 the orb visuals
  were tweaked so they stop disappearing.
- **SO4 Big ball:** after **10 projectile attacks** (Page, "launching 10 projectiles"; melee attacks do not count, Entry
  page) it fires a slow **large red ball** at one player; the message "`<name>` has discovered a large ball of energy
  coming their way..." (highlighted, 21 Jun 2018) names the target. It hits for "up to 188 damage (scaling with party size)" (Page); with two or more players alive in the arena it is a guaranteed kill on the target alone (121+,
  Strat). Players in the **3 x 3 around the target share the damage** (split equally; division not stated). The ball is
  tick-eatable and has the same timing at any distance (21 Jun 2018). Entry: **15** solo; in groups the Entry page says "this can hit 70+" while the Sotetseg page says "around 80 or so
  damage" (**OPEN-SO10**, conflict 3).
- **SO5 Shadow realm** at **66.6% and 33.3%** of its HP: it picks **one** player (white light and the message "Sotetseg
  chooses you...") and moves them to the Shadow Realm, a separate tiled maze; the others are teleported to the far end of
  the arena, and nothing Sotetseg fires while the maze is on (including the big ball) deals damage. The fade-to-white
  was made less glaring on 1 Dec 2021.
  - The runner follows a randomly generated path of red tiles to a portal that returns them; every **7 ticks** they take
    **1-3** chip damage. Arena players see the runner's current tile as a red glow and must walk the same path.
  - A wrong tile deals **6.67% of the player's current HP + 15** per tick (Entry **+ 11**) (Page, Strat; Page says it
    damages "the player", Strat says "nearby players"; **OPEN-SO2**).
  - When any player steps on the **fourth row** of the path a red vortex / tornado spawns and follows the maze path
    dealing "massive damage" to anyone it catches; stepping back to the third row despawns it. It does not appear for the
    runner themselves unless they are the only player. The runner waits on the third row so the others can position.
  - Moving diagonally and in L shapes is allowed; tiles cannot be run-skipped (Strat).
  - After the maze Sotetseg's Defence is restored (Page; the 4 Jun 2021 hotfix stopped the big ball from coming
    immediately after the maze).
- **SO6 Kill:** the second supply chest opens (TB22, TB23). Boss kill => restores (TB9).

## Per-mode differences

- **Entry:** max hits 20 / 22 / 22; HP 2,240 at five; the big ball 15 solo / 70+ (Entry page) or about 80 (Sotetseg page) in a group; wrong-tile damage +11.
- **Normal:** as above.
- **Hard:** att 350; at 66.6% / 33.3% **all but one** player are sent to the Shadow Realm and the maze is **divided
  among those sent, by orb order**; the tornado can spawn in the realm as well as in the overworld, and appears **earlier**:
  once the sent player lowest in orb order passes the third row it spawns inside the realm; if the player left in the
  overworld passes the third row it spawns in **both**; it launches **two** big balls at a time; the shared bomb damage in
  lower scales was reduced (9 Jun 2021 only) and a second set of balls is "often tick-eaten". Hard tile scenery:
  `TOB_SOTETSEG_HARD_DARKTILE` 41748, `_LIGHTTILE` 41749 and parents 41750-41753, visibility varbit
  `TOB_SOTETSEG_HARD_TILES_VISIBLE` 12269. Numbers **OPEN-SO3**.

## Tick timeline (stated parts)

- Attack every 5 ticks; melee hitsplat +1 tick after the attack animation.
- Projectile prayer lock 3 s (5 ticks) on a failed protect; shadow realm chip damage every 7 ticks; wrong tile every tick.
- Big ball after the 10th projectile; vortex when the fourth row is stepped on.

## Changes up to 2026-11-03 that matter

3 Jun 2021 modes added; 4 Jun 2021 hotfix ("Sotetsegs big bomb should no longer happen immediately after the maze mechanic ends"; the green ball and yellow ball changes belong to
Verzik; "Increased the damage of the ball attacks in Hard Mode" is not attributed to a boss); 9 Jun 2021 hotfix (another attempted fix for invisible Hard big bombs; "Reduced the amount of damage the shared bomb at Sotetseg deals when split correctly in lower scales"); 1 Dec 2021 fade; 24 Jan 2019
orb tweak; 2 Aug 2018 consistent projectiles, shorter prayer lock; 21 Jun 2018 ball timing, message highlight, no stacked
magic and ranged. No Sotetseg change after 1 Dec 2021 on the Page.

## As implemented (content/combat TobSotetseg.kt)

Mode from the NPC id (`TobModes.of`). Entry damage = Normal / 2 (floor) for melee and orbs and the death ball has its own Entry
numbers; wrong-tile and tornado damage and the realm chip are not halved. HP is set by raids-tob `TobScaling`. Arena =
offsets -4..9 x -19..8 from its spawn tile (same level); "raiders" = living players there.

- **Wake (SO1):** asleep (non-combat id, no damage taken: `incoming` returns 0) until a raider stands at `z - spawn.z >= -17`
  (north of the barrier row). Then `transform(type + 1)` to the combat id, `huntRange` 20, first attack at tick + **5**.
  Each tick its Defence is raised to **100** if lower (the floor).
- **Attack clock (SO2/SO3):** re-armed to tick + **5** on every attack; **10** after a death ball; **5** after a maze. No attack
  while asleep or while a maze runs. Targets a player only.
- **Melee (SO2):** when the engine says the victim is in melee range (`distance = false`): roll `0..45` (Entry `0..22`),
  Protect from Melee = `raw / 2`, hit queued **+1** tick (typed melee; the roll is its own, no accuracy).
- **Orb:** otherwise a ranged-distance attack: counts toward the 10; anim 8139, projectile 1606 (height 43, start 23, end 203),
  lands **+6** ticks. At landing (skipped if the target is dying or a maze began): protected against magic = 0, else `0..50`
  (Entry `0..25`); an unprotected hit disables protection prayers for **5** ticks (`disableProtection`). The bounce happens
  whether or not the hit was protected: the (up to) two nearest other raiders within **9** (`distanceTo`, sorted by distance;
  ties = list order) get one **red** (magic; end `36 + 30 x max(3, d)` cycles) and one **grey** (ranged; end 216), each landing
  `end / 30` ticks later with the same damage rule against its own style, no further bounce. Magic-only first orb (the
  first orb is always the magic one; the grey/red pairing is fixed by distance order, **OPEN-SO7**).
- **Death ball (SO4):** when `balls >= 10` the next attack (melee included) is the ball: counter reset, anim 8139, all raiders get
  the red message "`<name>` has discovered a large ball of energy coming their way...", target = a uniformly random raider
  (not the melee victim), projectile 1604 end 480, impact graphic at the target's tile **at landing (+16)**. Sharers =
  raiders within 1 tile of the target (3 x 3) at landing; damage total is fixed at the throw and split `total / sharers`
  (floor), typeless, ignores prayer, not delayed further. Total: Normal / Hard `min(188, 47 x raiders)` (47 / 94 / 141 / 188 for
  1..4+; **OPEN-SO6**; the code KDoc labels the 47 per raider "OPEN-SO4", a clash with this doc's SO4 = maze generation);
  Entry 15 for 1 raider, **80** for 2+ (**OPEN-SO10**). Skipped if a maze begins before landing.
- **Shadow realm (SO5):** `afterHit` starts a maze when `hp x 1000 <= maxHp x 666` and again at `x 333` (equal counts; at most
  one per hit; the threshold is spent even if no raiders are in the arena at that moment). Runners: one uniformly random raider; Hard
  with 2+ raiders = **all but the last** in raid (orb) order (unlisted names last). Runner(s) get "Sotetseg chooses you...", anim
  1816 and teleport to the realm grid (offset +77, -14 from spawn, level +3) at the path's first tile; everyone else teleports to
  the arena's south spot (+2, -17). The boss stops interacting. Hard does **not** divide the maze: every runner uses the same path
  (**OPEN-SO3**).
  - **Path (code label OPEN-SO5; this doc's SO4):** 14 wide x 15 deep grid; start x random 0..13 on row 0; each row a random
    drift of -2..+2 (clamped), walked tile by tile on that row, then up to the next row, ending on row 14. The path is lit as scenery
    (`TOB_SOTETSEG_LIGHTTILE`) on the realm tiles for 200 ticks; the arena shows a `TRIGGERED` tile for 1 tick under each runner.
  - **Chip:** every **7** ticks of maze age each runner takes a drain graphic and `1..3` typeless damage landing **+2**.
  - **Wrong tile (SO2):** a runner in the realm grid off the path, or an arena raider on the mirrored arena grid (-4, -15 from
    spawn) off the path, takes every tick `hitpoints x 667 / 10000 + 15` (Entry `+ 11`), typeless, with a zap graphic; only
    that player (the Page reading, **OPEN-SO2**).
  - **Tornado (OPEN-SO8):** starts (index 0 of the path) when an arena raider is on grid row >= **3** (fourth row); removed when an
    arena raider is on a row < 2 and no arena raider is on row >= 3. Each tick it moves one path index (the last is held), zaps
    that arena tile and hits **50** typeless every raider within 1 tile of it. Since runners are not in the arena it never targets
    them (the "only player" case of SO5 is not modelled, nor Hard's realm tornado).
  - **End:** when a runner's grid row passes 14, or after **200** ticks (**OPEN-SO5 code label**: longest maze, not sourced), or
    on Sotetseg's death: maze cleared, surviving runners teleport to the arena's south spot, Defence set back to its base level, ball
    counter 0, next attack tick + 5 (4 Jun 2021 hotfix). Orbs and death balls that land during a maze do nothing.
  - Not enforced: the KDoc "no damage while the maze is on"; `incoming` only checks asleep, so raiders left in the arena can still
    damage it.
- **Not scripted here:** Hard's two simultaneous big balls, the shadow-realm Defence "reset on transport" (it is reset at maze
  end), death sequence/book (T+3) and chest (raids-tob).

## Open list

OPEN-SO1 bone drops; OPEN-SO2 who the wrong-tile blast damages and its radius; OPEN-SO3 Hard numbers (ball damage, shared
division, tornado timing); OPEN-SO4 maze generation (grid size, number of rows, path-length distribution, the seed
quoted by Strat is a simulator seed); OPEN-SO5 how the sent player is chosen (random assumed) and whether the trigger
fires once per threshold; OPEN-SO6 big-ball damage by party size 1-4 (only 188 at five and "121+" are stated), hit delay
and how damage is split; OPEN-SO7 projectile travel and hit delays, which of the pair goes to which target, the
orb-spacing rule; OPEN-SO8 vortex speed, damage and what "massive" means; OPEN-SO9 4-man / 3-man HP rounding; OPEN-SO10
Entry numbers beyond those quoted. **10 open items.**

## Conflicts between sources

1. Wrong-tile damage: "the player" (Page, Entry) vs "nearby players" (Strat).
2. Big ball: "up to 188 damage (scaling with party size)" (Page) vs "121+" guaranteed kill with two or more players (Strat); not contradictory if 121 is a lower scale.
3. Entry big ball in groups: "70+" (Entry page) vs "around 80 or so" (Sotetseg page); the 15 for solo agrees.
