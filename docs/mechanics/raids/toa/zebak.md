# Zebak, Guardian of Crondis (Path of Crondis boss)

Date anchor 2026-11-03 / cache 2757; sources read 2026-11-08. OSRS Wiki pages (API wikitext): "Zebak" (Page), "Tombs of Amascut/Strategies" Zebak and Crondis sections (Strat),
"Tombs of Amascut" mechanics, invocation table and changelog (Main; the "Invocations" and "Raid level" pages are byte-identical copies of the Main invocation section), "Jug (Tombs of Amascut)"
(Jug), "Wave (Tombs of Amascut)" (Wave), "Bloody Wave" (BWave), "Blood Cloud" (Cloud), "Blood Blitz" (Blitz), "Perfect Zebak" (CA-Perfect), "Chompington" (CA-Chomp), "Rockin' around the croc"
(CA-Rock), "All Praise Zebak" (CA-Praise), "Zebak's Phantom" and "Remnant of Zebak" (Phantom, Remnant); cache 2757 (`cache-dump config npc|loc|seq|spotanim`; stats array order attack, defence,
strength, hitpoints, ranged, magic; NPC params 3 / 4 = magic / ranged attack bonus, 5-8 = stab / slash / crush / magic defence, 10 = melee strength, 12 = ranged strength, 14 = attack speed in ticks,
65 = magic damage bonus, each agreeing with the infobox on Zebak; ranged defence and the melee attack bonus are not in the cache params, so the infobox supplies them); generated symbols (`Npcs`,
`Seqs`, `SpotAnims`, `Locs`, `Objs`); RuneLite plugins, **facts only, no code copied** (BSD-2): MT = `toa-mistake-tracker` (QuestingPet, `c73766d`, last commit **2024-02-13**, so before the
6 Aug 2025 wave refinement and the 8 Jan 2025 jug change; the wiki's dated changes win), PT = `tombs-of-amascut` PointsTracker (LlemonDuck, `831c39b`, 2026-09-10), ST =
`tombs-of-amascut-stats` (`2dc8a8e`). Raid-wide rules (party, raid level, path levels, invocations, points, death, rewards) belong in a ToA fact sheet that does not exist yet; the parts this fight
needs are restated in Scaling. Seconds are converted at 0.6 s per tick; a client cycle is 20 ms (30 per tick). Tick timelines list only what a source states; everything else is `OPEN-ZB<n>`.
Zebak's Phantom (NPC 11774) reuses his attacks in Wardens phase 3; that fight is not covered here.

## Data

- **Zebak (cache; name "Zebak", size 9, category 1695, combat level 371, op2 "Attack"):**

| Id | Symbol | Form | Att / Def / Str / HP / Rng / Mag | Params (3 / 4 / 5 / 6 / 7 / 8 / 10 / 12 / 14 / 65) | Stand seq |
|---|---|---|---|---|---|
| **11730** | `TOA_ZEBAK` | normal | 250 / 70 / 140 / **580** / 120 / 100 | 215 / 220 / 160 / 160 / 260 / 200 / 100 / 16 / **7** / 30 | 9618 `NPC_ZEBAK01_IDLE` |
| **11732** | `TOA_ZEBAK_ENRAGED` | enraged (examine "He seems a tad upset") | same | same, **attack speed 7 in the cache** (wiki 4, conflict 2) | 9618 |
| 11733 | `TOA_ZEBAK_DEAD` | dead body (examine "A big dead croc"), no ops, category 1696 | 1 across | none | 9636 |
| 11731 / 11734 | `TOA_ZEBAK_TAIL` / `_TAIL_DEAD` | "Zebak's Tail", size 9, no ops, no combat | 1 across | none | 9619 / 9637 |
| 11774 | `TOA_WARDENS_ZEBAK` | "Zebak's Phantom" (Wardens), size 5 | same as 11730 | same (no 5-8) | 9618 |

  Infobox (Page): combat 371, size 9, max hit **38** melee / **16** magic / **16** ranged, aggressive, attack styles melee / ranged / magic, attack speed **7** (enraged **4**), HP **580** (`scaledhp`), melee
  attack bonus +160, strength +100, magic attack +215 and damage +30, ranged attack +220 and strength +16, defence stab 160 / slash 160 / crush **260** / magic 200, ranged defence light / standard / heavy 110,
  poison and venom immune, cannon immune, thralls allowed, XP bonus 62.5 (normal), Experience modifiers stop scaling above 2,000 HP (24 May 2023).
- **Max hit check** (repo `docs/mechanics/combat-formulas.md`): melee F3 `(148 x 164 + 320) / 640 = 38` and ranged F9 `floor(0.5 + 128 x 80 / 640) = 16` both match the infobox; the magic 16 is the infobox
  value (no NPC magic formula is used). These are base values before the raid-level multiplier.
- **Other NPCs (cache):**

| Id | Symbol | Name / ops | Size | Stats (att / def / str / HP / rng / mag) | Notes |
|---|---|---|---|---|---|
| 11735 | `TOA_ZEBAK_JUG` | `<col=00ffff>Jug`, ops 1 Push, 3 Pull, 4 Hit | 1 | 1 / 1 / 1 / **5** / 1 / 1 | stationary jug (Jug infobox "Stationary") |
| 11736 | `TOA_ZEBAK_JUG_ROLLING` | same name, op2 Attack | 1 | 1 across, HP 1 | moving jug; walk seq 6 `BOULDER_ROLLING` (16 frames of 5 cycles = 80 cycles) |
| 11737 | `TOA_ZEBAK_SAFESPOT` | `<col=00ffff>Boulder`, no ops | 1 | 1 / 1 / 1 / **150** / 1 / 1 | the rock the roar breaks on: the Strat's "each rock has 150 health" |
| 11738 / 11739 | `TOA_ZEBAK_WAVE` / `_WAVE_BLOODY` | `<col=00ffff>Wave` / `Bloody Wave`, no ops, category 1698 | 1 | 1 across | stand and walk seq 7877 `DS2_TIDAL_PROJ` (8 frames of 3 cycles = 24 cycles) |
| 11740 | `TOA_ZEBAK_LANDCROC` | "Crocodile", no ops | 2 | 1 across | stand 2037, walk 2036 (same as the Crondis crocodile 11705) |
| 11741 | `TOA_ZEBAK_WATERCROC` | "Crocodile", no ops | 1 | **250** / 1 / **70** / 1 / 1 / 1; params 10 = 0, **14 = 2** | stand and walk seq 480; the biter in the water (Strat: "crocodiles in the waters will attempt to bite them") |
| 11742 | `TOA_ZEBAK_BLOOD_CLOUD` | "Blood Cloud", category 1699, no ops | 2 | 1 / 1 / 1 / **25** / 1 / 1 | stand 8102 `TOB_BLOOD_SPAWN_IDLE`, walk 8101 `TOB_BLOOD_SPAWN_WALK`; Cloud infobox "Normal" |
| 11743 | `TOA_ZEBAK_BLOOD_CLOUD_SMALL` | "Blood Cloud" | 1 | 1 / 1 / 1 / **30** / 1 / 1 | the "Thinned" version (Blood Thinners) |
| 11744 / 11745 | `SPOTANIM_ZEBAK_RANGED01_NPC` / `_JUGBREAK_NPC` | name "null", not interactable | 7 / 3 | 1 across | graphic carriers (a 7x7 rock fall effect and a 3x3 jug-break effect); their use is not stated |

  Derived, not stated: the water crocodile's max hit by F3 with strength 70 and no strength bonus is `(78 x 64 + 320) / 640 = 8`; its attack speed is the cache's **2**.
- **Scenery (cache loc ids):** acid pool locs `TOA_ZEBAK_VOMIT01..07` **45570-45576** (1x1, name "null", no block; the 7 variants are the pools MT detects), rock steps **45509** (2x1, op1 "Climb-up", the
  ramp out of the water), water **45508**, `TOA_ZEBAK_SHIELD01` **45577** (1x1 blocking walking and range; role not stated), teleport crystal `ZEBAK_TELEPORT` **45506** (room exit). The other
  `TOA_ZEBAK_*` locs 45507-45569 (debris, pillars, bushes, ripples, walls) are decoration, not mechanics.
- **Room:** map region **15700** (MT, ST, PT; the challenge room is 15698). Chat messages the plugins match: "Challenge started: Zebak." and "Challenge complete: Zebak." (ST).
- **Zebak animations (cache; "01" = head NPC 11730, "02" = tail NPC 11731; keyframe-range end in brackets; whether one keyframe is one 20 ms cycle is not established, OPEN-ZB10):**
  idle 9618 / 9619 (90), melee **9620** / 9621 (90), melee enraged 9622 / 9623 (60), ranged **9624** / 9625 (120), ranged enraged 9626 / 9627 (60), roar **9628** / 9629 (270), tail 9630 / 9631
  (150), special 9632 / 9633 (120), death 9634 / 9635 (120), dead 9636 / 9637 (30). There is no seq named magic; which animation the magic attack plays is OPEN-ZB5. Only 9620 (melee) and 9628 (roar)
  are confirmed by MT; the rest are cache names (tail = the wave slam, special = the spit of jugs and poison are readings of the names, OPEN-ZB5).
- **Projectile / graphic ids (cache names; the plugin-confirmed ones in bold):**

| Id | Symbol | Cache seq (cycles or keyframe end) | Role |
|---|---|---|---|
| 2176 / 2177 | `ZEBAK_MAGE_PROJANIM_INITIAL` / `_ENRAGED` | 9642 (90; enraged seq 9643 = 60) | magic pot, first leg |
| 2178 / 2179 | `ZEBAK_RANGE_PROJANIM_INITIAL` / `_ENRAGED` | 9639 (90; enraged seq 9640 = 60) | ranged rock, first leg |
| 2186 / 2185 | `ZEBAK_MAGE_SPLIT` / `ZEBAK_RANGED_SPLIT` | 9638 (60) / 9641 (90) | burst effect above the target (also `SPOTANIM_ZEBAK_MAGIC01` seq 9638, `SPOTANIM_ZEBAK_RANGED01` 2183 seq 9641) |
| **2181** / 2182 | `ZEBAK_MAGE_PROJANIM_SPLIT` / `ZEBAK_RANGE_PROJANIM_SPLIT` | 7571 (60 cycles) / none | second leg: the red orbs. **2181 is MT's magic projectile** |
| **2187** (2188-2190) | `ZEBAK_RANGED_FRAGMENT01..04` | none | second leg: the rock fragments. **2187 is MT's ranged projectile** |
| 2175 / 2180 | `ZEBAK_MELEE_PROJANIM_INITIAL` / `_SPLIT` | none | a melee "projectile" of unknown use (OPEN-ZB5) |
| 2172 | `ZEBAK_SAFESPOT_TRAVEL` | 9645 (90) | thrown roar rock; break effect 2191 `ZEBAK_SHIELD_BREAK` (seq 9646) |
| 2173 | `ZEBAK_WATERJUG_TRAVEL` | 9642 (90) | thrown jug; break 2192 `ZEBAK_WATERJUG_BREAK` (seq 9644); splash travel 2193 (seq 659, 8 cycles) |
| 2194 | `ZEBAK_VOMIT_PROJECTILE0` | 8133 (180 cycles = 6 ticks) | acid / poison spit that becomes the pool locs |
| 2195 | `ZEBAK_ROCK_FALL` | 8450 (260 cycles = 8.7 ticks) | rocks that fall at the wave's source side |
| **2184** | `ZEBAK_ROAR_WAVE_DUST` | 9647 (60 cycles = 2 ticks) | roar impact dust on each tile hit; also appears on a jug's landing tile for a tick (MT). Short versions 2953 and 3979 / 3980 / 3981 (rotated 90 / 180 / 270; seq 11596 = 46 cycles) |
| 2174 | `ZEBAK_DUSTFALL01` | 9498 | dust fall (name only) |

- **Client varbit:** `BUFF_ZEBAK_BLEED_DURATION_DISABLED` 14291 (cache symbol; presumably the bleed's duration display, role not stated).
- **Plugin facts for the wave and swimming:** swimming pose ids **772 / 773** (MT); a push moves a player at least 3 tiles along Y, sometimes 4 (MT). A bleed leaves temporary blood splatters on the tiles a
  player walks (Wave, BWave, Page trivia).
- **Drops** (Page): "Crondis' capture" (item `TOA_BOOK_ZEBAK` 27308; always, only if the player does not have it stored in their bank) and the **Fang** (always, only to the player who dealt the most damage; item
  `TOA_ZEBAK_FANG` 27219). Music: "Jaws of Gluttony". Remnant of Zebak (item 27380 `TOA_PET_MORPH_ZEBAK`): raid level 450+, zero deaths for the whole party, all Zebak invocations on, path level 4 or higher.
- **Points** (PT, raid-wide): damage to Zebak (11730 / 11732 / 11733 / 10680 `TOA_ZEBAK_TRANSMOG`) counts **1.5** points per HP; the room cap is **half** the normal one (10,000 against 20,000). The repo plan
  (`docs/plans/raids-brief.md`) lists the same 1.5x.

## Scaling

- **Raid level** (Main, Strat): per 5 raid levels, enemy **hitpoints, defence, accuracy and damage** rise 2% additively, i.e. `stat x (1 + RaidLevel / 250)`; **damage is capped at +150%**. The defence
  rise is a modifier on the **defence roll**, not the Defence level (Strat). Bands: Entry 0-149, Normal 150-299, Expert 300-600. CA-Chomp: "at the minimum raid level of 150, Zebak will have **930** health";
  the formula gives `580 x 1.6 = 928` (conflict 3; the page rounds). The wave damage "6 - 10 ... scales with invocation" (Page) and the blood blast "8-11" (Main, CA-Rock) are base ranges; how the cap applies
  to them is OPEN-ZB1.
- **Party size** (Main): players 2 and 3 each add **90%** of the base HP, players 4 and beyond each add **60%**. Rock, jug and cloud HP are the cache base; their scaling is OPEN-ZB1.
- **Path level** (Main): level 1 raises most enemy HP and damage by **+8%**, each further level by **+5%**, up to six levels (8 / 13 / 18 / 23 / 28 / 33%); mechanics change at levels **2** and **4** (cap 4).
  Zebak (Main, Strat, Page): **auto-attacks are faster** ("this speed only affects the projectile launch, not when it shatters into the team below") and **the Tidal Waves gap is one tile tighter per two
  levels**. Stated numbers: path level 0: speed **7**, enraged **4**; path level 4 (and above): speed **5**, enraged **2** (CA-Rock, CA-Praise). Path level 2 is not stated (OPEN-ZB4, working assumption 6 / 3
  behind `toa.zebak.pathSpeedTable`). Wave gap: at the lowest levels it is wide enough "to fire from ten tiles away without issue"; from level 2 "a slight chance that it will not be possible to attack, as the safe
  tile would be eleven tiles away" (Strat; the paragraph says "raid level 0-1 / 2+" but means path level, conflict 4); at level 4 "only a single gap" (CA-Rock).
- **Zebak invocations** (Main; raid level modifier in brackets): **Not Just a Head** (+15), **Arterial Spray** (+10, requires Not Just a Head), **Blood Thinners** (+5, requires Not Just a Head), **Upset
  Stomach** (+15; raised from +10 on 14 Sep 2022). Raid-wide ones that touch the fight: Quiet Prayers (+20, protection prayers 10% less effective; the Strat says Zebak's magic and ranged "can be fully blocked
  if Quiet Prayers are not enabled"), Deadly Prayers (+20, prayer drained by 20% of damage taken, not self-inflicted), Overly Draining (+15), On a Diet (+15), Dehydration (+30), Walk the Path (+50), Pathseeker
  (+15), Pathfinder (+40), Pathmaster (+50), attempt limits. TA lists the invocations in the order Not Just a Head 25, Arterial Spray 26, Blood Thinners 27, Upset Stomach 28 (zero-based; widget index = position x 3;
  client contract, not a game rule). The client shows the path level in varbit `TOA_CLIENT_CRONDIS_LEVEL` 14376 and the raid level in `TOA_CLIENT_RAID_LEVEL` 14380.

## Fight

- **ZB1 Arena and start:** Zebak (size 9) sits at one end with his tail NPC (11731); water surrounds the arena with a **ramp (rock steps, loc 45509) on each side**; rocks and rubble were added next to him on 1 Sep
  2022 to stop melee safespots, and standing on some rock tiles next to him no longer avoids the melee (21 Sep 2022); those rocks no longer block ranged attacks (8 Sep 2022). He is aggressive; the aggro rule with
  several players is not stated (OPEN-ZB12).
- **ZB2 Standard attacks:** one attack per cycle, styles **melee, ranged, magic**; speed **7**, enraged **4** (path level 4: 5 and 2). Which style is chosen when (the Page: melee "can be avoided simply by standing
  away from melee range"; ranged and magic come as the projectiles below) is not stated (OPEN-ZB5). "Zebak's standard attacks will be indicated by visual and audio cues" (Page, Strat).
- **ZB3 Melee** (Page, Strat, CA-Chomp, MT): the hardest hit (infobox 38 at base). Animation **9620** (enraged 9622). **Protect from Melee reduces it** ("the melee can hit through prayer, albeit with reduced
  accuracy and damage", Strat; the amount is OPEN-ZB6). A hit has a **chance to inflict bleed**: extra damage over time that "is increased further with movement" (Page, Strat; numbers OPEN-ZB6). The attack has "a one
  tick delay" (CA-Chomp) so a player who stays one tick at a time in range is never hit (flinch), but "it is possible to get killed in one hit on higher raid levels" (Strat). The range: the melee can start only
  against a player on certain tiles next to his body (CA-Chomp's picture caption: "Zebak's 1/2 tile melee attack range", the "red tiles"); the hit's effect then extends to a wider ring ("yellow tiles") and damages **every player**
  there, and a halberd from two tiles away is safe (CA-Chomp); the exact footprint is OPEN-ZB3. MT measured the hit **2 ticks after animation 9620 starts** and dropped the check, noting that Jagex was changing the timing and that the hit does not always
  bleed or damage (conflict 5; OPEN-ZB6).
- **ZB4 Magic** (Page, Strat, CA-Praise, MT): he "spits a pot into the air that splits into red orbs": initial projectile **2176** (enraged 2177), burst above the target, second leg **2181** (the red orbs).
  Fully blockable with **Protect from Magic** (Quiet Prayers off). "Damage is calculated exactly 2 ticks later" after the burst above the player (CA-Praise); "attacks will only deal damage right before impact"
  (Strat) so the prayer can be flicked on at the last moment; MT checks the overhead at the projectile's end cycle for the Wardens phantom and notes "3 ticks" from the second-leg event (conflict 6). The
  launch-to-burst time is not stated (OPEN-ZB5). Max hit 16 at base.
- **ZB5 Ranged:** he "spits a large rock into the air that splinters into smaller rocks": initial projectile **2178** (enraged 2179), burst, second leg **2187** (fragments); same timing and **Protect from
  Missiles** rules as ZB4. Animation **9624** (enraged 9626). Max hit 16 at base. Splash of both bursts: graphics 2185 / 2186. The animation of the magic attack is unknown (OPEN-ZB5).
- **ZB6 Special schedule** (Page, Strat): at **85%, 70%, 55% and 40%** of his HP he queues one of two specials (Great Roar, Tidal Waves) and **alternates** between them; "poison and jugs are always spit out
  with each special attack"; "should Zebak's health fall to the enrage point threshold before a queued attack, it will be discarded" (Strat). Which special is first is not stated (the Page and Strat list the roar
  first; OPEN-ZB2, working assumption roar at 85%, waves at 70%, roar at 55%, waves at 40%, flag `toa.zebak.specialOrder`). "Depending on raid level, gear and overall luck, it is possible to skip the last special."
- **ZB7 Acid (poison) pools** (Strat, MT): each special spits poison that splashes on a **random tile** of the arena and may splash again in a **3x3** around the impact tile (a **5x5** with Upset Stomach,
  Main). The pools use locs 45570-45576, last "permanently unless a jug shatters near them" (a jug's water disperses them) or a passing wave cleans them (not guaranteed). Standing in a pool deals "fairly low damage at the
  start, but quickly spikes to dangerous levels that will easily kill a player in a few ticks" and "inflicts poison status at 2 damage" (Strat). MT: a pool does **not** hurt on the tick it spawns; the first ones
  "sometimes only poison you and not damage you on the second tick". "Rarely, acid may not cover a tile behind a rock" (Strat). Per-tick damage, the spike curve, spit projectile 2194 landing time: OPEN-ZB7.
- **ZB8 Jugs** (Page, Jug, Strat): each special spits **jugs** (stationary NPC 11735, 5 HP, ops Push / Pull / Hit). A jug can be **pushed or pulled straight or diagonally** and rolls (NPC 11736) "two tiles further before
  stopping" (1 Sep 2022); it breaks when **attacked** (ranged or magic; magic works since 8 Jan 2025 and allows an immediate next attack since 6 Aug 2025) or when it **hits one of the rocks**, releasing water that clears
  the poison in a **5x5** area (**3x3** with Upset Stomach). The jugs have an **attack delay** so that only one shot is spent (28 Sep 2022), but not "if they are attacked before Zebak" (1 Sep 2022 wiki wording). The 1 Sep 2022 update post says instead "Jugs no longer set attack delay, but still respect it - so you should be able to pop a jug and hit Zebak immediately, but might have to wait if you hit Zebak before the jug"; the wiki wording and the 28 Sep entry differ from it (conflict 13), so the rule for who sets and who respects the delay is OPEN-ZB8. "Two extra jugs
  now appear during jug-related mechanics" (1 Sep 2022); the base count is not stated. "Every pattern that Zebak throws out will always have at least one jug that will create safespot(s)" (Strat). Diagonally
  pushing a jug into a rock can mis-apply the water ("indirect hits", Strat). The roar dust graphic 2184 also appears for a tick on the tile a jug lands on (MT). Number, positions, damage a jug does to a player it lands on
  ("getting hit by the water jugs does not fail" CA-Perfect, so it does hurt): OPEN-ZB8.
- **ZB9 Great Roar** (Page, Strat, CA-Perfect, MT): Zebak spits jugs, poison and **three stones (Page) / two or three rocks (Strat)** (NPC 11737, 150 HP each, thrown with graphic 2172 `ZEBAK_SAFESPOT_TRAVEL`, by name). The players then have "four of
  Zebak's attacks" (Page) to move the jugs next to the rocks and shatter them, clearing the poison behind the rocks, **before he roars** (animation **9628**, graphic **2184** on the tiles hit, MT). The roar "deals massive
  damage to any players caught in these soundwaves for **three hits**" and **knocks the player back**; the only protection is to stand **behind a rock, up to three tiles behind it**, which makes the rock take **50
  damage** (Strat; per hit or in total is OPEN-ZB9). First roar: rocks in front of him, poison directly behind them, jugs to the sides; second roar: the layout "adjusts further east" with jugs usually next to the
  rocks (Strat). Since 1 Sep 2022 the roar no longer knocks players into the safe edge areas. MT counts the 2184 tiles only while Zebak plays 9628. Roar damage, knock-back distance, the exact shadow of a rock (a cone
  or a line), rock placement rule: OPEN-ZB9.
- **ZB10 Tidal Waves** (Page, Wave, Strat): Zebak spits jugs and poison, then **slams his tail** (animation 9630 by name; boulders drop from the source side, graphic 2195 `ZEBAK_ROCK_FALL`, 260 cycles, by name) and a wave line sweeps the arena "from one
  side to the other". The first iteration "always starts to the south", the second "comes in from the north". A wave (NPC 11738, or 11739 when bloody) is a line with **gaps** to walk through; a player hit takes **6 - 10**
  damage ("scales with invocation") (the Strat and Wave pages call it "moderate", conflict 12) and is **pushed in the direction of the wave** (at least 3 tiles, sometimes 4, MT); waves also push jugs and clean some poison. A pushed player over the edge lands in the **water**: walking
  speed, cannot attack, must climb a ramp (rock steps), and **crocodiles** in the water (cache NPC 11741, speed 2) bite for melee damage, partly blocked by Protect from Melee (Strat). MT swim poses 772 / 773 and
  a "moved more than 2 tiles in Y" test detect a push. Wave speed, count, spacing, push distance, gap widths and the time from slam to first wave: OPEN-ZB10. Fixed: players can no longer stand on one particular tile in the water
  (5 Oct 2022); pushes no longer carry the player closer to Zebak while attacking (6 Aug 2025); waves interact "smarter" with pushback (25 Jun 2025).
- **ZB11 Bloody waves:** a wave that washes over blood splatters becomes a **Bloody Wave** (NPC 11739), whether the splatters come from a bleeding player or are the fixed ones at the north and south edges of the room
  (BWave, the latter since 30 Jun 2026 "The Blood Moon Rises"; BWave says the fixed splatters act on "the first waves at the start of the fight", so later waves are not covered by the sources). While a player is in the water, "Zebak will continue attacking alongside other crocodiles lurking in the waters" (Page). Its different effect, if any, is not stated (OPEN-ZB10).
- **ZB12 Not Just a Head:** Zebak "will occasionally cast a form of **Blood Blitz** on all players" with one of two effects (Main, CA-Rock, Blitz; the Strat text calls it Blood Barrage, conflict 7): (a) the blast
  hits **8 - 11** "regardless of the player's defence bonuses" unless **Protect from Magic** is used, and **heals Zebak** "roughly ~66%" of the damage (Main, CA-Rock) / "2x the damage inflicted" (Strat, conflict 8); a player
  standing close to others takes more and the blast splashes onto them ("players must spread apart"); or (b) it summons **Blood Clouds** (ZB13). The healing happens "if damage is inflicted"; the Strat adds that
  Protect from Magic blocks it and that "path levels and enrage determine whether barrages can be flicked against while also blocking his next projectile": for path levels **2 and 3** it can be flicked any time; at
  **0-1 and 4+** only before the enrage; "during enrage, blood barrage will hit you a tick before the next projectile hits" (Strat). The cadence, the 50/50 choice between (a) and (b), the area formula, the heal
  rounding and the animation: OPEN-ZB11.
- **ZB13 Blood Clouds:** (cache NPCs 11742 normal 25 HP size 2 and 11743 thinned 30 HP size 1). They "spawn to either side of the arena by the ramps" and follow a **random player** from the moment they appear; they
  lose **2 HP per tick / per tile moved** (Strat "2 damage to itself every tick"; Cloud "2 for every tile they move"; conflict 9) and, on touching a player, "siphon their health", dealing "rapid but light" damage and healing
  themselves (Cloud, CA-Rock). They have no aggro range: if nobody is within range when they appear they "wander about aimlessly and will not aggro even if the player gets close" (Strat). Roar rocks block them from a
  player behind a rock; standing on a tile next to the far-side ramp is safe because the walk time matches the self-damage lifetime (Strat; 25 HP at 2 per tick is about 12-13 ticks). MT detects the heal hitsplat on a
  cloud and flags the players on its tile. **Blood Thinners:** three clouds (the thinned NPC 11743) appear instead of one; the changelog says only that "the blood clouds ... slightly higher health" (8 Sep 2022; the Blood Thinners clouds in the Main changelog); that both versions got it is not stated. Spawn tile choice, speed, damage per
  contact, heal amount, aggro range: OPEN-ZB11.
- **ZB14 Arterial Spray:** "the radius and healing of Zebak's blood magic will be increased" (Main; needs Not Just a Head). The radius and the heal factor: OPEN-ZB11.
- **ZB15 Upset Stomach:** swaps the two AoEs: acid pools spread **5x5** (was 3x3) and jug water clears **3x3** (was 5x5) (Main; the Page and Jug say the same). Players must stage jugs so their smaller AoE leaves safe
  tiles for the wave (Strat). CA-Perfect repeats "water jugs have a 3x3 AoE and acid pools 5x5".
- **ZB16 Enrage (~25%):** "once Zebak falls below 25% of his health" (Strat; the Page says "~25%") he enters the enraged state: NPC id **11732**, attack speed 7 to **4** (path level 4: 2), "a distinct roar if sound
  effects are active", the examine text changes, and he **no longer does the roar or wave specials** (Strat). The cache has enraged variants of the melee and ranged animations (9622 / 9626) and of the projectiles (2177 / 2179; sequences 9640 / 9643 with a keyframe range of 60 against 90 for the normal ones). The cache speed of 11732 is 7, so the 4 is the server's (conflict 2). A queued special is dropped (ZB6). The blood spell continues (the Strat's enrage flick rules).
- **ZB17 Defence:** his Defence "can be lowered by a maximum of 20", from 70 to a minimum of **50** (Page, Strat); he does not reset it (CA-Chomp, as for Ba-Ba). Quiet Prayers makes his magic and ranged not
  fully blockable.
- **ZB18 Death, reset, drops:** drops as in Data; death animation **9634** (dead body 11733 with stand 9636); after a wipe Zebak "will now properly reset" (25 Aug 2022; before, he could perform specials on re-entry).
  Music "Jaws of Gluttony" while fighting.

## Per-attack reference

| Attack | Animation | Projectile / graphic | Delay to impact (stated) | Damage | Prayer | Telegraph | What the plugins time or detect |
|---|---|---|---|---|---|---|---|
| Melee (chomp) | **9620** (enraged 9622) | none | "one tick delay" (CA-Chomp) / hit 2 ticks after the animation (MT, dropped) | 38 base; chance to bleed | Protect from Melee reduces accuracy and damage | animation; AoE wider than the range | MT: removed (timing unreliable) |
| Magic | not named (OPEN-ZB5) | **2176 / 2177** pot, burst 2186, orbs **2181** | damage exactly **2 ticks** after the burst above the player (CA-Praise); launch speed from the attack speed | 16 base | Protect from Magic blocks fully (Quiet Prayers off) | pot in the air, red orbs, audio | MT (Wardens phantom): overhead checked at the end of projectile 2181 |
| Ranged | **9624** (enraged 9626) | **2178 / 2179** rock, burst 2185, fragments **2187** | 2 ticks after the burst | 16 base | Protect from Missiles blocks fully (Quiet Prayers off) | rock in the air, fragments, audio | MT (Wardens phantom): overhead checked at the end of projectile 2187 |
| Acid spit | special 9632 (name only) | **2194** (180 cycles) to pool locs 45570-45576 | pool does nothing on its first tick (MT) | low, rising fast; poison 2 | none | splash | MT: pool object ids, damage one tick after spawn |
| Jug throw | special 9632 (name only) | **2173**, break **2192** | not stated | landing hurts (amount OPEN) | none | graphic **2184** for a tick on the landing tile (MT) | not tracked |
| Great Roar | **9628** | rock throw **2172**, roar dust **2184** / 2953 / 3979-3981 | "four of Zebak's attacks" between the rocks and the roar (Page); three hits | massive, knock back; rock takes 50 | none | scream animation, audio | MT: 2184 tiles while 9628 plays |
| Tidal wave | tail slam 9630 | rocks **2195**, NPCs 11738 / 11739 | not stated | 6 - 10, push at least 3 tiles | none | rocks falling at the source side | MT: a Y jump above 2 tiles with a wave NPC alive |
| Blood Blitz | not named | not named | not stated | 8 - 11 at base; heals Zebak | Protect from Magic blocks (Main) | not stated | MT: heal hitsplat on a cloud only |
| Blood Cloud | walk 8101 | none | contact | rapid, light | none | cloud NPC | MT: heal hitsplat on the cloud = players on its tile |
| Crocodile bite | stand/walk 480 | none | speed 2 | derived 8 base | Protect from Melee partly (Strat) | in the water | none |

## Per-mode and per-scale differences

- **Raid mode** (Entry 0-149, Normal 150-299, Expert 300-600): no Zebak-specific rule; the CA tasks need raid level 150+ (Perfect Zebak, Chompington), 300+ (Rockin' around the croc, All Praise Zebak) and 450+ (Remnant). Perfect
  Zebak is also solo-able; it fails on damage from poison, off-prayer standard attacks, blood spawns and waves, or on pushing more than two jugs on a roar; roar, jug, and Blood Blitz damage are allowed. Chompington:
  melee only, thralls (ghostly, skeletal), recoil and the Voidwaker special fail it; ranged weapons may break jugs. Rockin' around the croc: all Zebak invocations and path level 4, deaths in solo allowed.
  All Praise Zebak: no prayer point lost, Deadly Prayers off is recommended.
- **Solo:** no change except HP and the blood spell spacing ("players must spread apart" applies to groups).
- **Party scale:** HP x (1 + 0.9 + 0.9 + 0.6 x (n - 3)) for n of at least 3.
- **Path level 2 / 4:** faster standard attacks (4: 5 and 2), tighter wave gap (-1 tile per two levels).

## Tick timeline (stated parts)

- Standard attack every **7** ticks (enraged **4**); path level 4: **5** and **2**. Magic and ranged damage **2 ticks** after the burst above the player. Melee "one tick delay". Specials queued at **85 / 70 / 55 / 40%**,
  alternating; four of Zebak's attacks between the roar's spit and the roar (about 28 ticks at speed 7, **derived**); the roar hits three times. Blood Cloud lifetime about 12-13 ticks (25 HP at 2 per tick,
  **derived**). Crocodile bite speed 2 (cache). The enrage threshold is 25%. Nothing else is stated.

## Changes up to 2026-11-03 that matter

25 Jun 2025 (drained stats fully restored at the end of a boss fight); 1 Sep 2022 (AoE no longer stacks: double damage from overlapping AoEs removed); 30 Jun 2026 (fixed blood splatters at the room edges turn waves into Bloody Waves); 6 Aug 2025 (immediate attack after hitting a jug with magic; wave pushback no longer moves the player closer);
25 Jun 2025 (waves interact "smarter" with pushback; the Palm of Resourcefulness water no longer scales linearly: not boss); 8 Jan 2025 (jugs can be hit with magic); 24 May 2023 (XP stops scaling after 2,000 HP);
5 Oct 2022 (no standing on a certain water tile); 28 Sep 2022 (jug attack delay so one shot is enough); 21 Sep 2022 (no melee avoidance by standing on rock tiles next to him); 14 Sep 2022 (crocodiles
cleaned up; Upset Stomach +10 to +15); 8 Sep 2022 (rocks next to him do not block ranged; Blood Clouds slightly more HP; path level mechanics at 2 and 4, +8 / 13 / ... %); 1 Sep 2022 (HP +10%; rubble to stop melee safespots; the roar no longer knocks into edge safe areas; two extra jugs; jugs no longer roll between
rocks and break more reliably; jugs have no attack delay if attacked before Zebak; jugs roll two tiles further); 25 Aug 2022 (reset after a wipe).

## Open list

OPEN-ZB1 scaling composition (raid level x party x path, rounding; wave / blood damage under the cap; HP of rocks, jugs, clouds, crocodiles; 928 vs 930); OPEN-ZB2 which special is first, exact threshold test (the discard rule is stated by the Strat); OPEN-ZB3 melee: bleed chance and damage, range and AoE geometry, Protect from Melee reduction; OPEN-ZB4 path level 2 attack speeds and launch/impact timing under faster speeds; OPEN-ZB5 animation
of the magic attack and the specials, style choice per cycle, melee projectile ids 2175 / 2180, launch-to-burst time; OPEN-ZB6 melee delay (1 vs 2 ticks) and the melee prayer reduction; OPEN-ZB7 pool damage per
tick, spike curve, spit landing time; OPEN-ZB8 jug count, positions, landing damage, attack delay length, rock-break rule; OPEN-ZB9 Great Roar (rock count 3 vs 2-3, damage, knock-back, rock damage 50 per hit or
total, coverage geometry, rock placement); OPEN-ZB10 Tidal Waves (speed, count, gaps, damage scaling, bloody-wave effect, slam-to-wave time, animation-length unit); OPEN-ZB11 Blood Blitz (cadence, choice, area,
heal 2x vs 66%, Arterial Spray numbers, cloud spawn / speed / damage / heal / aggro); OPEN-ZB12 target selection with several players, water crocodiles (count, roles of 11740 vs 11741, bite damage), and the ramp. **12
open items.** Working assumptions behind named flags: `toa.zebak.specialOrder` (roar first), `toa.zebak.pathSpeedTable` (level 2 = 6 / 3), `toa.zebak.bloodHealFactor` (default 0.66, the Main / CA-Rock number),
`toa.zebak.meleeDelayTicks` (default 2, MT's measurement), `toa.zebak.cloudSelfDamagePerTick` (default 2).

## Conflicts between sources

1. Great Roar rocks: "three stones" (Page) vs "two/three rocks" (Strat). Both kept (ZB9).
2. Enraged speed: infobox 4 and Strat 4 vs cache param 14 of NPC 11732 = 7 (equal to 11730). The wiki wins; the server sets the speed (the cache config does not carry it).
3. Zebak HP at raid level 150: 930 (CA-Chomp) vs the formula 580 x 1.6 = 928. The formula is used; the page rounds.
4. Wave gap labelled "raid level 0-1 / 2+" (Strat) vs "path level" (the same section's invocation text and the Page). Read as path level.
5. Melee delay: "one tick delay" (CA-Chomp) vs MT's 2 ticks after the animation start (2024, removed because Jagex was changing it). Both kept (ZB3).
6. Ranged / magic impact: "exactly 2 ticks" after the burst (CA-Praise) vs MT's comment "projectile hit (3 ticks later)"; MT's code uses the projectile end cycle. 2 ticks after the burst is used.
7. Blood spell name: "Blood Blitz" (Main, CA-Rock, CA-Perfect, Strat invocation table) vs "special version of Blood Barrage" (Strat text). Blood Blitz is used (3 sources).
8. Blood spell heal: "roughly ~66%" (Main, CA-Rock) vs "2x the damage inflicted" (Strat). The Main number is the working one; "slightly heal" (Strat table, Main) fits it.
9. Blood Cloud self-damage: "2 for every tile they move" (Cloud) vs "2 damage to itself every tick" (Strat); equal when it moves one tile per tick.
10. Zebak damage under Protect from Melee: "reduced accuracy and damage" (Strat) with no number; no conflict, an OPEN item.
11. Perfect Zebak "must not push more than two jugs on the roar" (CA-Perfect) vs the Strat's "at least one jug solves the roar": consistent, not a conflict; noted for the roar tests.
12. Wave damage: "6 - 10" (Page) vs "moderate" (Strat, Wave); the number is used, the word is a description.
13. Jug attack delay: see ZB8 (wiki wording, 28 Sep 2022 entry and the 1 Sep 2022 post disagree).
