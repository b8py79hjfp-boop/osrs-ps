# Ba-Ba, Guardian of Apmeken (Path of Apmeken boss)

Date anchor 2026-11-03 / cache 2757; sources read 2026-11-08. OSRS Wiki pages (API wikitext): "Ba-Ba" (Page), "Tombs of Amascut/Strategies" Ba-Ba and
Apmeken sections (Strat), "Tombs of Amascut" mechanics, invocation table and changelog (Main; the "Invocations" and "Raid level" pages are
byte-identical copies of the Main invocation section), "Boulder (Tombs of Amascut)" (Boulder), "Baboon" (Baboon), "Banana peel" (Peel), "Ba-Ba's
Throne" (Throne), "Perfect Ba-Ba" (CA-Perfect), "Ba-Bananza" (CA-Bananza), "Chompington" (CA-Chomp, used only for the shared defence-drain note);
cache 2757 (`cache-dump config npc|loc|seq|spotanim`, stats array order attack, defence, strength, hitpoints, ranged, magic; params 3 / 4 = magic / ranged
attack bonus, 5-8 = stab / slash / crush / magic defence, 10 = melee strength, 12 = ranged strength, 14 = attack speed in ticks, 65 = magic damage
bonus; each agrees with the infobox where both exist; ranged defence and melee attack bonuses are not in the cache params, so the infobox supplies
them); generated symbols (`Npcs`, `Seqs`, `SpotAnims`, `Locs`, `Objs`); RuneLite plugins, **facts only, no code copied** (BSD-2): MT =
`toa-mistake-tracker` (QuestingPet, `c73766d`, last commit **2024-02-13**: it predates the 25 Jun 2025 Ba-Ba rework, so where it disagrees with a dated
wiki change the wiki wins), ST = `tombs-of-amascut-stats` (`2dc8a8e`, 2024-05-27), PT = `tombs-of-amascut` PointsTracker (LlemonDuck, `831c39b`,
2026-09-10) and `toa-points-tracker` (`3d4bb73`), SW = `tombs-of-amascut` `BabaSarcophagusWarning`. Raid-wide rules (party, raid level, path levels,
invocations, points, death, rewards) belong in a ToA fact sheet that does not exist yet; the parts this fight needs are restated in Scaling. Seconds are
converted at 0.6 s per tick; a client cycle is 20 ms (30 per tick). Tick timelines list only what a source states; everything else is `OPEN-BB<n>`.
Cross-boss: the same attacks are reused by Ba-Ba's Phantom (NPC 11775) in the Wardens phase 3; that fight is not covered here.

## Data

- **Ba-Ba (cache, all three forms: name "Ba-Ba", size 5, category 1707, combat level 359, op2 "Attack", model rotation speed 32):**

| Id | Symbol | Role (source) | Att / Def / Str / HP / Rng / Mag | Defence bonus stab / slash / crush / magic | Atk speed | Stand / walk seq |
|---|---|---|---|---|---|---|
| **11778** | `TOA_BABA` | Phase 1 (100% -> 66.6%) and phase 2 (between the first and second boulder phases); ST: the NPC changes back to 11778 when the first boulder phase ends | 150 / 80 / 160 / 380 / 0 / 100 | 80 / 160 / 240 / 280 | **6** | 9741 `NPC_MANDRILL_IDLE` / 9737 |
| **11780** | `TOA_BABA_DIGGING` | The throne form while she throws boulders (ST: she "leaps to the top of the room and starts throwing boulders") | same as 11778 | same | 6 | 9750 `NPC_MANDRILL_ATTACK_SPECIAL_IDLE01` / 9737 |
| **11779** | `TOA_BABA_COFFIN` | Form after the **second** boulder phase (ST: "end of second boulder phase"), i.e. 33.3% -> 0 (**BB5**) | **200 / 100 / 220** / 380 / 0 / 100 | **100 / 200 / 320 / 400** | **4** | 9741 / 9737 |

  All three: melee strength bonus 26 (param 10), ranged strength 0, magic and ranged attack bonuses 0, magic damage bonus 0. Infobox (one stat line for
  the three ids, equal to the 11778 column): max hit **24**, aggressive, attack styles crush and ranged, HP **380** (`scaledhp`), ranged defence light
  200 / standard 200 / heavy **120** (200 until 29 May 2024), poison and venom immune (resistance 100), cannon immune, thralls allowed. Salve
  amulets have no effect (Page). XP bonus: none listed. Size 5. The infobox states nothing about the 11779 column; the cache is the authority
  (**BB5**, conflict 1).
- **Max hit check** (repo `docs/mechanics/combat-formulas.md` F3 with effective strength = Strength + 8, no style bonus): 11778 / 11780
  `(168 x 90 + 320) / 640 = 24` matches the infobox; the same formula with the cache 11779 numbers gives `(228 x 90 + 320) / 640 =` **32**. That 32 is
  **derived, not stated** (**BB5**).
- **Other NPCs:**

| Id | Symbol | Name / ops | Size | Cache stats (att / def / str / HP / rng / mag) | Notes |
|---|---|---|---|---|---|
| 11781 | `TOA_BABA_BABOON` | "Baboon", op2 Attack, combat 77 | 1 | 0 / 50 / 0 / **10** / 80 / 50 | params: ranged attack +80, ranged strength +20, defence stab 10 / slash 50 / crush 50 / magic 50; infobox ranged defence 50 across, **attack speed 4**, max hit "1?", ranged, HP 10 (not scaled), cannon immune, poison and venom immune; stand 9741, walk 9739; spawn anims 9752 / 9753 `NPC_MANDRILL_SPAWN01/02`, despawn 9754 / 9755, explode 9756 (used by the Volatile baboon in the challenge room) |
| 11782 | `TOA_BABA_BOULDER` | `<col=00ffff>Boulder`, op2 Attack | 3 | 1 / 500 / 1 / **25** / 1 / 1 | rolling boulder; stand and walk seq 9518 `TOA_BOULDER_ROLLING` (16 frames of 4 cycles = 64 cycles, about 2.1 ticks per loop) |
| 11783 | `TOA_BABA_BOULDER_WEAK` | same name, the **cracked** version | 3 | same | players always land the Ranged max hit on it (Boulder, Strat) |
| 11784-11787 | `TOA_BABA_RUBBLE` .. `_4` | `<col=00ffff>Rubble`, no ops | 3 | 1 / 1 / 1 / **25** / 1 / 1 | four variants of the debris pile; Strat gives different HP (**BB6**, conflict 3); MT looks the NPC up by the name "Rubble" |
| 11788 | `TOA_BABA_SARC_NPC` | name "null", no ops, not interactable | 1 | 1 / 1 / 1 / **60** / 1 / 1 | the NPC that carries the sarcophagus health bar; SW assumes a health scale of 30 (**BB7**, conflict 4) |
| 11775 | `TOA_WARDENS_BABA` | "Ba-Ba's Phantom" | 5 | as 11778 | Wardens only |

- **Scenery (cache loc ids):** **45752** `TOA_BABA_COFFIN_CLOSED` "Sarcophagus" (1x1, blocks walking and range), **45753** `TOA_BABA_COFFIN_OPEN`
  "Open Sarcophagus", **45754** `BABA_TELEPORT` "Teleport crystal" (room exit), **45755** `TOA_BABA_BANANA_PEEL` "Banana peel" (1x1, no block),
  **45756** `THRONE_TOMB_BABA01` "Ba-Ba's Throne" (3x2, no ops; non-interactive scenery, Throne), `TOA_BABA_TILES01..12` 45826-45837, walls
  45777-45786 / 45841-45843, steps 45825, `TOA_BABA_DEBRIS_STONE02..07` 45851-45856 (decor). NPC and loc names are the cache's, no loc named "debris pile"
  was found for the fight (the debris is the NPC 11784-11787).
- **Room:** map region **15188** (ST and MT; the challenge room is region 15186). Chat messages the plugins match: "Challenge started: Ba-Ba." and
  "Challenge complete: Ba-Ba." (ST). The boss shows no combat-level scaling in the examine.
- **Player-side ids seen by MT** (names are the cache's; all are reused generic assets): slam impact graphic **1103** (`DARKBOW_SMOKE_ARROW_IMPACT`),
  boulder-hit animation **7210** (`HUMAN_KNOCKDOWN`), knock-back animation **9799** (`HUMAN_TROLL_FLYBACK_MERGE`), pit fall animation **4366**
  (`QUEST_LUNAR_FALLING_ON_PLATFORM`; defined in MT but not used by its detection), banana slip animation **4030** (`ROYAL_HUMAN_SLIP_FALL`) with graphic
  **1575** (`TOB_BLOAT_STUNNED`), rubble absorb-explosion graphic **1463** (`EARTHSURGE_IMPACT`), banana fall animation (peel) **9758**
  `TOA_BABA_BANANA_FALL` (162 cycles = 5.4 ticks, cache).
- **Ba-Ba animations (cache, all generic "mandrill" names; keyframe range end in brackets, an animation length in cycles only if the 20 ms keyframe
  unit holds, which is not established, **BB11**):** idle 9741 (90), walk 9737 / 9738 backwards (90), walk 02 9739 / 9740 (60), melee 9742
  `NPC_MANDRILL_ATTACK_MELEE` (60), melee **9743** `NPC_BABA_ATTACK_MELEE` (60), ranged 01 **9744** (90), ranged 02 9745 (90), magic 9746 / 9747
  (60 each), special jump 9748 (60), special ranged 9749 (60), special idle 9750 (60), special end 9751 (60). No death animation was found by name
  (**BB11**).
- **Projectile / spot-animation ids (cache names, no source states which attack uses which):** `TOA_BABOON_RANGED_TRAVEL_1/2` **2242 / 2243** (no
  seq), `TOA_BABA_RANGED_TRAVEL` **2244**, `TOA_BABA_BALLS_TRAVEL` **2245**, `TOA_BABA_SKULL_PROJ` **2246** (seq 3942, 10 cycles),
  `TOA_BABOON_MAGIC_TRAVEL_1/2` 2247 / 2248, `SPOTANIM_MANDRILL_EXPLODE` 2249; falling-rock graphics `TOA_BABA_ROCK_FALL` **2250** (seq 8836, 208
  cycles), `_FASTER` **2251** (seq 9805, 155 cycles), `_FASTEST` **2252** (seq 9806, 102 cycles). The reading "2244 is the thrown boulder, 2245 the
  sarcophagus energy ball, 2242 / 2243 the baboon shots" is from the names alone (**BB11**).
- **Drops** (Page): "Apmeken's capture" (always, only if the player has not read it; item `TOA_BOOK_BABA` 27304) and **Big banana** (always, only to the
  player who dealt the most damage; item `TOA_BABA_BANANA` 27221). Remnant of Ba-Ba (item 27378 `TOA_PET_MORPH_BABA`): raid level 450+, zero deaths for the
  whole party, all Ba-Ba invocations on, path level 4 or higher. Pet transmog NPC `WARDEN_PET_BABA` 11847.
- **Points** (PT): damage to Ba-Ba (11778 / 11779 / 11780) is worth **2.0** points per HP, boulders **0.0**; the Apmeken-path baboons (11709-11718) 1.2,
  the fight baboon 11781 is not in the table (so 1.0). Room cap 20,000 (the shared `MAX_ROOM_POINTS`). The repo plan lists the same 2x for Ba-Ba. Raid-wide; not
  restated elsewhere here.

## Scaling

- **Raid level** (Main, Strat): for every 5 raid levels, enemy **hitpoints, defence, accuracy and damage** rise 2% additively, i.e. `stat x (1 + RaidLevel
  / 250)`; **damage is capped at +150%** (RL 500 gives HP, defence and accuracy +200%, damage +150%). The defence increase is a modifier on the
  **defence roll**, not on the Defence level (Strat), so a Defence-draining special lowers the base level. Raid level bands: Entry 0-149, Normal 150-299,
  Expert 300-600 (Main). Boulders are an exception to the party and path rules, below. Whether the raid level also scales the boulders and the
  baboons: **OPEN-BB1**.
- **Party size** (Main): for bosses, players 2 and 3 each add **90%** of the base HP, players 4 and beyond each add **60%**. Baboons are static 10 HP
  (Baboon, 25 Jun 2025). Boulders: the party effect is **half** (Boulder).
- **Path level** (Main): level 1 raises most enemy HP and damage by **+8%**, each further level by **+5%**, up to six levels (8 / 13 / 18 / 23 / 28 / 33%;
  8 Sep 2022 changed it from +10 / 20 / 30 / 40 / 50 / 60%); the mechanics change at levels **2** and **4** (cap 4; 8 Sep 2022). Ba-Ba (Main, Strat): the rockfall
  drops faster, the sarcophagus energy blast gets a wider AoE, the boulders get more HP. Boulders do not use the +8 / +5 rule: +**10%** at levels 2-3,
  +**25%** at level 4+, applied after the party step (Boulder).
- **Boulder HP, base 25** (cache, Strat): the Boulder page's table (path level 0 / 1 / 2 / 3 / 4) is reproduced exactly by `floor(25 x (1 + 0.45 + 0.45 + 0.3
  x (n - 3)))`, then `floor(x 1.10)` for path levels 2-3 or `floor(x 1.25)` for level 4+, with a floor after each step (all 40 table values checked,
  **derived**, not stated): 1 player 25 / 25 / 27 / 27 / 31; 2: 36 / 36 / 39 / 39 / 45; 3: 47 / 47 / 51 / 51 / 58; 4: 55 / 55 / 60 / 60 / 68; 5: 62 / 62 /
  68 / 68 / 77; 6: 70 / 70 / 77 / 77 / 87; 7: 77 / 77 / 84 / 84 / 96; 8: 85 / 85 / 93 / 93 / 106. The table does not say at which raid level it was measured
  (**OPEN-BB1**).
- **Invocations** that change this fight (Main, Strat; raid level modifier in brackets): Mind the Gap! (+10), Gotta Have Faith (+10), Jungle Japes (+5), Shaking Things
  Up (+10), Boulderdash (+10, "optional" in the Strat toggle column). Fight-relevant raid-wide ones: Quiet Prayers (+20, protection prayers 10% less
  effective), Deadly Prayers (+20, prayer drained by 20% of damage taken in the raid, not self-inflicted: a hit of 5 drains 1), Overly Draining (special
  attacks cost 100%), On a Diet, Dehydration, the path-level invocations Walk the Path / Pathseeker / Pathfinder / Pathmaster, and the attempt limits.
  Plugin list position (TA `Invocation` enum, widget index = position x 3): Mind the Gap! 33, Gotta Have Faith 34, Jungle Japes 35, Shaking Things Up 36,
  Boulderdash 37 (client contract, not a game rule). Client display varbits: path level `TOA_CLIENT_APMEKEN_LEVEL` 14379, raid level `TOA_CLIENT_RAID_LEVEL`
  14380.

## Fight

- **BB1 Entry and targeting:** she is aggressive to the first player who enters the arena; with several players she "will change aggression afterwards" based
  on their current Hitpoints, and "a base tank can keep aggro on her by keeping their health low" (Strat). The exact rule and the re-target
  interval: **OPEN-BB2**. Music and the room-entry cutscene: not covered.
- **BB2 Standard attacks:** speed **6** (**4** in the last form, BB5). Melee (crush, max **24**, cache 11778 / 11780) when the target is in range; Strat: "Protect
  from Melee will block all damage" since 25 Jun 2025 (it reduced the damage to 33% on 29 May 2024, 25% afterwards, 100% since). Which animation plays (9742
  or 9743) and whether she has an ordinary ranged or magic auto attack (the infobox lists crush and ranged; the Perfect Ba-Ba task says "Ba-Ba's attacks
  off-prayer"): **OPEN-BB3**. How Quiet Prayers (10% less effective) interacts with "fully blocks": **OPEN-BB3**.
- **BB3 Special attack selection:** Ba-Ba has four specials besides the boulder phases: Shockwave (BB4), Rockfall (BB5a), Rock Throw (BB5b) and Baboon's Discharge
  (BB6). **No source gives the order, the cadence or the weights.** Stated links only: after the Rock Throw, "if debris was destroyed, Ba-Ba will use Rockfall
  again" (Strat); the Shockwave doubles as a "stomp" when the target stays underneath her (BB4). **OPEN-BB4**; working assumption: she makes a normal
  attack on every tick of her cycle and uses a special on a fixed cycle, behind the flag `toa.baba.specialCycle`.
- **BB4 Shockwave** (Page, Strat): she faces one of **eight** directions and shadows appear on the ground in front of her in a **cross-shaped pattern**; shortly
  afterwards she swipes the area and deals damage "dependent on the player's position" if the player did not move. Area: "rough 5x5" (Page, Strat), **7x7** with
  Shaking Things Up (Page, Strat); the invocation text says "3x3 (+1 tile to the north / west / east / south) to 5x5 (+1 tile ...)" (Main, Strat). The two
  readings agree if the figures in the first pair are the **bounding boxes of a plus shape** (a 3x3 block with one extra tile out of each side, 5 wide; with the
  invocation a 5x5 block with one extra tile per side, 7 wide) (conflict 5, **OPEN-BB5**). **Stomp:** if her target stays under her "for a prolonged time" she
  uses the same attack and "reacts much faster" (Page, Strat; the duration is **OPEN-BB5**). Telegraph: ground shadow; graphic on the hit tile: **1103** (MT).
  Damage, hit delay and animation: **OPEN-BB5**.
- **BB5a Rockfall** (Page, Strat, Main): she swipes her claws; rocks fall from the ceiling. **One rock per player** lands on the tile the player was on when the
  ability started, and does heavy damage to a player still under the shadow. At the same time **two large piles of debris** drop (the Rubble NPCs 11784-11787,
  size 3), shown by the same shadow; each has a 3x3 AoE, players directly under the shadow are damaged, players not under it are "harmlessly pushed aside".
  Debris HP: "10 health, increasing by 10 for every player in the room" (Strat; **BB6**). Fall duration by path level: the falling-rock graphic 2250 hits
  **6 ticks** after it appears, 2251 after **4 ticks** (MT, also used by the Wardens phase 3 detector); graphic 2252 `_FASTEST` has no stated delay.
  The cache fall animations agree in shape: 2250 = seq 8836 (68 frames of 3 cycles), 2251 = seq 9805 (53 frames of 2, then 15 of 3), 2252 = seq 9806 (53
  frames of 1, then 15 of 3); the first 53 frames last 159 / 106 / 53 cycles = 5.3 / 3.5 / 1.8 ticks, which round up to the stated 6 and 4 and suggest
  **2 ticks** for 2252 (**inference**, **OPEN-BB6**: the mapping of 2250 / 2251 / 2252 to path levels 0-1 / 2-3 / 4+ is also inferred from the names and the "every two
  levels" text). The debris is destroyed by the scream at each boulder phase (BB8). Damage of a rock: "heavy" (Strat); MT treats a hit as a mistake only when a
  hitsplat lands on the tile at the activation tick and the player is not in the knock-back animation 9799.
- **BB5b Rock Throw** (Page, Strat, MT): she throws a large boulder at **every player**; it lands about **6 ticks** after it is used (Strat "~6 ticks") / **7 ticks** after her
  animation **9744** starts (MT: "seemingly always 7 ticks later"; conflict 6). The hit is "massive" and "less in a group than solo, though still very high" (Strat).
  The damage is **fully nulled** for a player standing next to an unopened sarcophagus, a baboon or a debris pile; since 25 Jun 2025 the damage "is now always
  split completely with any appropriate object, including sarcophagi" (Main). Each such object takes damage from the throw: debris **10**, an unopened
  sarcophagus **30**, a baboon **12** (Strat; a baboon has 10 HP, so a shared hit kills it). MT's "next to" test is the 5x5 block around the 3x3 debris (the footprint plus one
  tile on every side) and it observed that a correctly handled throw deals about 4 or less, a failed one about 40 or more ("over-hits"), and that a debris with
  too many players beside it spares only some of them (the others take the hit, the debris takes one hitsplat per protected player). The capacity of an object, the
  exact damage, the delay choice and the effect of the 25 Jun 2025 change on MT's older "half damage next to a baboon or sarcophagus" note: **OPEN-BB7**. If the
  phase threshold is crossed while a throw is in the air, MT sees the pending throws cleared (its tracking treats a hit on the debris with a different graphic as
  the phase change), so the throw may not land: **OPEN-BB7**.
- **BB6 Baboon's Discharge** (Page, Strat, Baboon): she calls **1-4** baboons (Page, Main 25 Jun 2025; reduced from 1-8; Strat says "2-4 depending on party size"; conflict
  7), level 77, **10 HP**, ranged attackers (speed 4); "players are guaranteed to land max hits on them" (25 Jun 2025). They target the **sarcophagi** at the arena edges.
  "Periodically" one stops attacking players and tries to open a sarcophagus, dealing **20 damage per hit** to it. An opened sarcophagus (loc 45753) "starts discharging a
  deluge of projectiles" / "unstable energy in a conical pattern in front of it" into the arena; it deals damage to a player in a **1x1** area since 1 Sep 2022 (was 3x3), and
  the potential AoE grows every two path levels (Main, Strat). **Gotta Have Faith:** the blasts "deal additional damage based on the player's remaining prayer
  points" (Main); the Strat adds "increasing in damage the lower the player's Prayer points", which conflicts with the neutral invocation text on the direction
  (conflict 8, **OPEN-BB8**). Sarcophagus HP: cache **60**, SW assumes **30** (and a wiki line says a Rock Throw does 30 to an unopened one) (**OPEN-BB8**). SW
  also observes the sarcophagus health bar (so low health is visible). Number per party size, spawn tiles, cadence, which sarcophagus is chosen, blast rate and damage,
  how many sarcophagi exist: **OPEN-BB8**. **Jungle Japes:** each baboon killed drops **two banana peels** (loc 45755); a player who steps on or runs past one takes "a small amount of
  damage" and is **stunned 3 seconds (5 ticks)** (Main, Peel; two peels since 25 Jun 2025, one before; spawn on death since 1 Sep 2022). MT sees slip animation 4030 or graphic
  1575 on the player (it deduplicates for 3 ticks, its own constant), and notes that a peel killed with a 1-tick attack never appears. Damage: **OPEN-BB9**.
- **BB7 Phase 2 and 3 thresholds:** at **66.6%** and **33.3%** of her HP (Strat; Page and Main say 66% and 33%) she stops attacking, jumps to her throne and screams (BB8).
  Rounding of the thresholds on scaled HP: **OPEN-BB1**. ST labels the room splits "Phase 1, Boulders 1, Phase 2, Boulders 2, Phase 3" and reads the NPC id
  changes 11778 -> 11780 (phase start), 11780 -> 11778 (end of boulders 1), 11778 -> 11780, 11780 -> 11779 (end of boulders 2). There is **no enrage** in the wiki; the
  cache's stronger form 11779 is BB10.
- **BB8 Rolling Stones** (Page, Strat, Boulder): she leaps to her throne (form 11780, animation 9748 named `..._SPECIAL_JUMP01`) and "unleashes a massive scream,
  knocking all players to the other side of the arena" ("to the bottom") and **destroying any debris** (one can persist if phased at the right time). She then throws
  boulders (3x3 NPCs, op Attack) that **roll down the slope**; they are killed by damage, must be destroyed to avoid a hit, and **instantly destroy debris and kill
  baboons** they touch. Waves of **four** boulders (Page) with "one of the **five** boulders in each row / column" cracked (Page, Strat; conflict 9); the Strat
  adds: the first column's cracked boulder is one of the **middle three** starting points, each later column's is up to **two positions north or south** of the
  previous one; a cracked boulder takes the player's **Ranged max hit** regardless of accuracy. A player who makes contact in front of a boulder is knocked back
  **four tiles** and takes **heavy** damage (Strat; the Page says "moderate"), plus a knock-down animation 7210 (MT). **End:** after **ten sets** of boulders, or when a
  player hits her with **melee** "past the boulder starting point", she stops and attacks again (Page, Strat). Boulderdash doubles the speed at which she sends
  boulders (Strat) / they "spawn twice as fast" (Main). Boulder HP: Scaling. MT: a boulder's collision starts 1-2 ticks after it appears (it waits **3**), boulders
  roll toward **decreasing X**, are removed one tick before they reach the low wall column, and a player cannot be hit by one two ticks in a row. Rolling speed (tiles
  per tick), set interval, set geometry, damage, whether she can be damaged on the throne, and when debris returns: **OPEN-BB10**.
- **BB9 Mind the Gap!:** the pit at the bottom of the arena "becomes a danger zone" and any player knocked into it, "either from a boulder knockback or from the scream", is **killed
  instantly**; safe spots are the sides of the area (outside the middle columns where the gap is) or the very top row (Page, Strat, Main). MT's geometry: the pit is the **10**
  region-local tiles x 20-21, y 30-34 of region 15188 (a 2 x 5 block) and the boulder wall column is x = 22; the plugin treats standing there as the "gap" mistake and
  the player falls with animation 4366. Without the invocation the pit is harmless (the Page implies it, MT triggers regardless; **OPEN-BB10**).
- **BB10 Last form (33.3% and below):** the cache switches to NPC **11779**: attack 200, defence 100, strength 220, defence bonuses 100 / 200 / 320 / 400, **attack speed 4**
  (derived max hit 32). The wiki's single stat block and the Strat say nothing about a change; the only sources are the cache and ST's reading of the id change.
  Whether the defence-drain floor stays 60 (Page "can be lowered by a maximum of 20 damage ... down to 60", from the base 80) or follows the new base 100 (80?), and
  whether the faster speed and higher bonuses apply from the end of the second boulder phase or from the 33.3% threshold: **OPEN-BB11**.
- **BB11 Defence:** she "does not reset" drained Defence and drains cap at **-20** (min 60) (Page, CA-Chomp); a bug that made it undrainable was fixed on 8 Sep 2022.
- **BB12 Death and reset:** drops as in Data. The room restarts after a wipe (no Ba-Ba-specific rule is stated). Death animation, music and the reward-chest
  hand-off: **OPEN-BB3**.
- **BB13 Fixed bugs that stay fixed:** players can no longer walk inside the rubble (14 Sep 2022, and the 26 Aug 2022 hotfix: "can no longer safespot Ba-Ba using
  the fallen rubble"); boulders may no longer become invisible (8 Sep 2022); players cannot get stuck inside a big rock (1 Sep 2022).

## Per-attack reference

| Attack | Animation | Projectile / graphic | Delay to impact (stated) | Damage | Prayer | Telegraph | What the plugins time or detect |
|---|---|---|---|---|---|---|---|
| Melee | 9742 or 9743 (**OPEN-BB3**) | none | not stated | max 24 at base, x raid-level multiplier (cap +150%) | Protect from Melee blocks all (25 Jun 2025) | none stated | none |
| Shockwave / stomp | not named (**OPEN-BB5**) | ground graphic **1103** on hit tiles | "shortly afterwards" (no ticks) | position dependent, not stated | none stated | cross-shaped ground shadow, faces one of 8 directions | MT: graphic 1103 spawning under the player = mistake |
| Rockfall (falling rocks + debris) | claw swipe, not named | falling-rock graphics **2250 / 2251 / 2252** at each target tile | **6** ticks (2250), **4** ticks (2251), 2252 unstated (inferred 2) | "heavy" | none | shadow on the tile | MT: graphic at spawn tick + delay, hitsplat on that tile |
| Rock Throw | **9744** (named `RANGED_01`) | thrown boulder, likely **2244**, rubble absorb graphic **1463** | "~6" (Strat) / **7** from animation start (MT) | massive; fully nulled next to debris / sarcophagus / baboon; object takes 10 / 30 / 12 | none | boulder in the air tracking the player | MT: animation 9744 + 7 ticks, large hitsplat (> 15) off a safe tile |
| Baboon shot | ranged, speed 4 | **2242 / 2243** (name only) | not stated | "1?" | Protect from Missiles (presumed) | none | none |
| Sarcophagus blast | none (object `TOA_BABA_COFFIN_OPEN`) | **2245** likely (name only) | not stated | 1x1 area, grows with path level; more with Gotta Have Faith | none stated | open sarcophagus in a cone | SW: tile box colour by sarcophagus HP |
| Rolling boulder | NPC walk 9518 | none | rolls; spawn +3 ticks before MT trusts collision | heavy, knock back 4 tiles | none | shadow / visible boulder | MT: animation 7210, X movement > 2 in one tick, or tile inside the 3x3 |
| Scream / knock back | jump 9748, special idle 9750 | none | immediate | with Mind the Gap! lethal if landing in the pit | none | she leaps to the throne | MT: previous-tick tile in the pit |
| Banana peel | slip animation 4030 | graphic 1575 | on step | small, stun 3 s (5 ticks) | none | the peel | MT: animation 4030 or graphic 1575 |

## Per-mode and per-scale differences

- **Raid mode** (Entry 0-149, Normal 150-299, Expert 300-600): no Ba-Ba-specific rule; everything is the raid-level multiplier. The CA tasks require raid level 150+ (Perfect Ba-Ba, "in a group of two or
  more") and 300+ with path level 4 and all Ba-Ba invocations (Ba-Bananza).
- **Solo:** no baboon count, a lower throw damage ("less than in a group"), debris HP 10 or 20 (BB5a); Perfect Ba-Ba needs a group.
- **Party scale:** HP x (1 + 0.9 + 0.9 + 0.6 x (n - 3)); boulders half that.
- **Path level 2 / 4:** faster rockfall, wider sarcophagus blast, boulder HP +10% / +25% after party scaling.

## Tick timeline (stated parts)

- Normal attack every **6** ticks (last form **4**, cache); Rock Throw hits **6-7** ticks after it starts; Rockfall rocks hit **6 / 4** ticks after the graphic appears
  (2250 / 2251); a ten-set boulder phase at each of 66.6% and 33.3%; banana stun 5 ticks (3 s); the baboon attack speed is 4; the sarcophagus takes 20 per baboon
  hit; a Rock Throw does 10 / 30 / 12 to debris / sarcophagus / baboon. Nothing else is stated (the boulder rolling speed, the set interval and the
  shockwave delay are all OPEN).

## Changes up to 2026-11-03 that matter

25 Jun 2025 (Protect from Melee fully blocks; Rock Throw damage always fully split with debris, baboons and sarcophagi; 1-4 baboons, 10 HP, guaranteed max hit, two peels; cursed
baboons and shamans in the challenge room are not in this fight); 29 May 2024 (Protect from Melee 33% -> 25%; ranged heavy defence 200 -> 120; challenge-room baboons static HP);
14 Sep 2022 (no walking inside rubble); 8 Sep 2022 (boulders may no longer become invisible; Defence drain bug fixed; path-level damage 8 / 13 / ... %; mechanics at levels 2 and 4);
1 Sep 2022 (peels on baboon death with much less damage; sarcophagus blast 1x1 instead of 3x3; players cannot get stuck in rock); 26 Aug 2022 (no rubble safespots). No Ba-Ba entry after 25 Jun 2025.

## Open list

OPEN-BB1 scaling composition (raid level x party x path, rounding), threshold rounding of 66.6% / 33.3%, whether the raid level scales boulders and baboons;
OPEN-BB2 target selection rule and its interval; OPEN-BB3 melee animation, ordinary ranged / magic attacks, Quiet Prayers vs full block, death animation;
OPEN-BB4 special selection order and cadence; OPEN-BB5 Shockwave and stomp (delay, animation, geometry, damage, stomp trigger); OPEN-BB6 Rockfall (2252 delay, level mapping, rock damage, debris placement, debris HP);
OPEN-BB7 Rock Throw (delay 6 vs 7, object capacity, damage, phase-change interaction); OPEN-BB8 Baboon's Discharge (baboon count by party size, spawn tiles, cadence,
sarcophagus HP and count, blast shape / rate / damage, Gotta Have Faith direction); OPEN-BB9 banana damage; OPEN-BB10 Rolling Stones (speed, interval, geometry, damage,
damageable on throne, debris return, pit without the invocation); OPEN-BB11 last form (11779) and defence-drain floor; animation lengths; OPEN-BB12 (reserved: reward and wipe hand-off). **11 open items.**
Working assumptions (behind named flags): `toa.baba.specialCycle` (BB3), `toa.baba.lastFormFromThreshold` (BB10, default: 11779 from the end of the second boulder phase as ST reads it),
`toa.baba.gotta­HaveFaithMorePrayerMoreDamage` (BB6, default off), `toa.baba.rubbleHpPerPlayer` (default 10 x players, the reading that makes a solo throw destroy it), `toa.baba.sarcophagusHp` (default 60,
the cache value).

## Conflicts between sources

1. Last form: cache 11779 (attack 200, defence 100, strength 220, speed 4) vs the wiki infobox (one stat line, speed 6) and the Strat (no mention). **Cache wins** (authority 1), supported by ST's id change.
2. Shockwave size: "5x5, or 7x7" (Page, Strat) vs "3x3 (+1 tile ...) to 5x5 (+1 tile ...)" (Main, Strat invocation table). Read as bounding boxes of a plus shape (BB4); both kept.
3. Debris HP: cache 25 on all four Rubble NPCs vs "10 health, increasing by 10 for every player" (Strat) vs "takes 10 damage (instantly destroying it)" in a solo. The Strat wins as the stated player-facing
   behaviour (the cache value equals the boulder's and is the unscaled base).
4. Sarcophagus HP: cache 60 vs SW's assumed 30 (a health-bar scale, "should be 30") vs the Strat's "unopened sarcophagus 30 damage" absorbed. Cache kept as the working value.
5. Rock Throw delay: "~6 ticks" (Strat) vs 7 ticks after animation 9744 (MT). Different anchors may explain it; both kept (BB5b).
6. Throw protection: MT's older note "you can half it by standing next to a baboon / sarcophagus" vs "always split completely" (Main, 25 Jun 2025). The dated wiki change wins.
7. Baboon count: "1-4" (Page, Baboon, Main) vs "2-4 depending on party size" (Strat). The 25 Jun 2025 changelog wins.
8. Gotta Have Faith: "based on the player's remaining prayer points" (Main, neutral) vs "increasing in damage the lower the player's Prayer points" (Strat).
9. Boulder set: "waves of four boulders ... one of the five boulders" (Page, self-contradictory) vs "one of the five boulders in each column" (Strat). Five lanes used; whether a set has four or five boulders: OPEN-BB10.
10. Boulder-hit damage: "moderate" (Page) vs "heavy" (Strat). Heavy used.
11. Boulder HP table (Boulder) is for "various team sizes and path levels" without a raid level: kept (OPEN-BB1).
