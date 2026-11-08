# Akkha (Tombs of Amascut, Path of Het boss)

Date anchor 2026-11-03; cache 2757. Sources read 2026-11-08 (labels used below):

- OSRS Wiki pages (copies in `/tmp/claude-0/ref/wiki-toa/`): "Akkha" (Page), "Akkha's Shadow" (Shadow), "Unstable Orb" (Orb), "Orb of
  Darkness" (OrbD), "Tombs of Amascut/Strategies" Akkha section and "Akkha's Invocations" (Strat), "Tombs of Amascut" with its
  changelog (Main; the copies `Tombs_of_Amascut.txt`, `Raid_level.txt` and `Invocations.txt` are byte-identical, so "Main" also
  covers the Raid Level and Invocations tables), update posts "Summer Sweep Up: Combat" 25 Jun 2025 (Sweep) and "Tombs of Amascut Drop Mechanics & Osmumten's Fang" (Drops).
- Cache 2757: `cache-dump config npc|loc|obj|seq|spotanim|struct`. NPC `stats` array order is attack, defence, strength,
  hitpoints, ranged, magic (it agrees with the wiki infobox for Akkha). NPC params, read against the infobox on Akkha, Kephri
  and the scarabs: 3 = magic attack bonus, 4 = ranged attack bonus, 5 / 6 / 7 / 8 = stab / slash / crush / magic defence, 10 =
  melee strength bonus, 12 = ranged strength bonus, 14 = attack speed, 65 = magic damage bonus. The cache has no melee attack
  bonus and no ranged defence param (those come from the infobox only). Param 26 is unlabelled (Akkha 1, Kephri 5) and unused.
  Invocation text comes from cache structs 437-543 (params 1160 name, 1161 category, 1162 raid-level modifier, 1262 description;
  category 10 = Akkha, 11 = Kephri). Generated symbols: `Npcs`, `Seqs`, `SpotAnims`, `Locs`, `Objs`, `Varbits`.
- RuneLite plugins (facts only, in `/tmp/claude-0/ref/plugins-toa/`): MT = toa-mistake-tracker `c73766d` (`AkkhaDetector`,
  `WardensP3Detector`), TA = tombs-of-amascut (LlemonDuck) `831c39b` (`AkkhaShadowHealth`, `PathLevelTracker`), PT =
  toa-points-tracker `3d4bb73`, ST = tombs-of-amascut-stats `2dc8a8e`. toa-modern `1e45099` has only room region constants.

Raid-wide rules (party, raid level, points, reward chest, death) belong in a `../toa.md` that does not exist yet. Seconds are
converted at 0.6 s per tick; the plugin constant is 30 client cycles per tick (600 ms / 20 ms). Timelines list only what a
source states; everything else is `OPEN-AK<n>`. No source gives a tick delay between a projectile appearing and the hit
landing for Akkha, and no source gives a damage number by raid level for any Akkha attack except the 25 cap on unstable orbs.

## Data

- **NPC ids** (cache names, which agree with the Page id list 11789-11796):

| Id | Cache name | Role | Head icon sprite (archive 440) |
|---|---|---|---|
| 11789 | `AKKHA_SPAWN` | spawn / pre-fight form | 0 |
| 11790 | `AKKHA_MELEE` | melee form | 0 |
| 11791 | `AKKHA_RANGE` | ranged form | 1 |
| 11792 | `AKKHA_MAGE` | magic form | 2 |
| 11793 | `AKKHA_ENRAGE_SPAWN` | final-stand spawn form | 6 |
| 11794 | `AKKHA_ENRAGE_INITIAL` | final stand, first form | 6 |
| 11795 | `AKKHA_ENRAGE` | final stand | 6 |
| 11796 | `AKKHA_ENRAGE_DUMMY` | final stand, dummy | 6 |
| 11797 | `AKKHA_SHADOW` | shadow, attackable (op2 "Attack") | none |
| 11798 | `AKKHA_SHADOW_ENRAGE` | shadow, final stand (no ops) | none |
| 11799 | `AKKHA_SHADOW_ENRAGE_DUMMY` | shadow, final stand dummy (no ops) | none |
| 11800 / 11801 / 11802 / 11803 | `AKKHA_TRAIL_ORB_LIGHTNING / _DARKNESS / _BURN / _FREEZE` | trailing orbs: "Orb of Lightning", "Orb of Darkness", "Burning Orb", "Frozen Orb"; size 1, category 1713 | none |
| 11804 | `AKKHA_ENRAGE_ORB` | "Unstable Orb"; size 1, category 1714 | none |
| 11805 | `AKKHA_HEADBAR_NPC` | name "null", size 2, no ops; role not stated (presumed boss-bar helper) | none |

  Sprite 0 / 1 / 2 on forms 11790 / 11791 / 11792 is read as Protect from Melee / Missiles / Magic (the order of the cache
  `HeadIcon` set); sprite 6 on every final-stand form is read as the combined Magic-and-Missiles icon the Strat describes. The
  sprite images were not checked (**OPEN-AK1**). The Page lists the shadows as 11797-11799 and the orbs are not listed.
- **Akkha stats**: size 3, category 1711, combat level 337, aggressive, op2 "Attack". Cache `stats` [100, 80, 140, 400, 100,
  100] = attack 100, defence 80, strength 140, **HP 400**, ranged 100, magic 100 (infobox agrees; "scaledhp"). The 400 is read as
  the value at raid level 0, solo (TA uses the cache value 70 of the shadow as the base before scaling, see Scaling).
- **Bonuses**: attack +115 (infobox only), magic attack +115 (param 3), ranged attack +115 (param 4), melee strength +30 (param
  10), ranged strength +40 (param 12), magic damage +40 (param 65). Defence: stab **60**, slash **120**, crush **120**, magic
  **10** (params 5-8, infobox agrees); ranged light / standard / heavy **60 / 60 / 60** (infobox only).
- **Attack speed 6** ticks (param 14, infobox). **Max hit 55** (infobox, "scaledhp = Yes" row; it does not say which attack or
  which raid level, **OPEN-AK2**). Attack styles: slash, magic, ranged.
- **Immunities**: poison and venom resistance 100, freeze resistance 100, cannon-immune; thralls are not immune; "can now be
  affected by burn damage" (24 Jul 2024, Page). Akkha has no stated elemental weakness.
- **Akkha's Shadow** (Shadow, cache): ids 11797-11799 above, size 3, category 1712, combat level 108, cache `stats` [100, 30,
  140, 70, 100, 100] = **HP 70, defence 30**; bonuses and defence bonuses identical to Akkha (params 3-14, 65 equal); max hit
  22 and attack style "None" (infobox), aggressive No; **elemental weakness air 60%** (wind spells, 25 Jun 2025).
- **Animations** (cache `Seqs`; every attack has a spear and a sword variant, what selects the variant is **OPEN-AK3**). The
  number in brackets is the cache `keyframeEnd`; if one unit is one client cycle (20 ms) then 30 = 1 tick, which is **not
  stated by any source**: idle `NPC_AKKHA_IDLE_SPEAR` 9760 / `_SWORD` 9761 (60), `NPC_AKKHA_IDLE_SWORD_RAISED_VERY_HIGH`
  15156 (60); walk 9762-9769, 9809-9811, 9421; melee `NPC_AKKHA_ATTACK_MELEE_SPEAR` 9770 / `_SWORD` 9771 (60), sword
  high-priority variant 15157 (60); ranged `NPC_AKKHA_ATTACK_RANGED_SPEAR` 9772 / `_SWORD` 9773 (90); magic
  `NPC_AKKHA_ATTACK_MAGIC_SPEAR` 9774 / `_SWORD` 9775 (90) plus numbered variants `_01`-`_04` spear 9776-9779 and sword
  9780-9783 (90 each); `NPC_AKKHA_TELEPORT_DISAPPEAR_SPEAR` 9784 / `_SWORD` 9786, `_APPEAR_SPEAR` 9785 / `_SWORD` 9787 (30
  each); `NPC_AKKHA_ATTACK_STOMP_SPEAR` 9788 / `_SWORD` 9789 (60); spawn 9790 / 9791 (60); death 9792 / 9793 (60). Floor
  symbol glow `FX_SYMBOL_FLOOR_GLOW01` 9759 (cache `totalFrameCycles` 90, i.e. 3 ticks at 30 cycles per tick).
- **Projectiles and graphics** (cache `SpotAnims`; ids confirmed as projectile ids by MT): `AKKHA_MAGIC_TRAVEL` **2253** (no
  seq; magic projectile), `AKKHA_MAGIC_IMPACT` 2254 (seq 662), `AKKHA_RANGED_TRAVEL` **2255** (no seq; ranged projectile);
  quadrant graphics `AKKHA_LIGHTNING` **2256** (seq 8680), `AKKHA_DARKNESS` **2257** (seq 9416), `AKKHA_BURN` **2258** (seq
  664), `AKKHA_FREEZE` **2259** (seq 1959); `AKKHA_ENRAGE_ORB_IMPACT` **2260** (seq 693). `AKKHA_LIGHTNING_SMALL` 3763 and
  `ECHO_AKKHA_LIGHTNING` 3178 are other content, not used here.
- **Scenery**: hourglass locs (2x2, block walk) `AKKHA_ARENA_OUTER_LVL2_22 / _23 / _57 / _58` in three states, `_EMPTY01`
  46056-46059, `_EMPTY02` 46060-46063, `_FULL01` **46064-46067**; which of the four is which quadrant is not stated
  (**OPEN-AK4**). Arena scenery `AKKHA_ARENA_OUTER_LVL1_01-64` 45896-45959 and `LVL2_01-78` 45960-46037 is decoration. Drop
  objs: `TOA_AKKHA_ASHES` 27223 ("Eldritch ashes"); Het's capture is `TOA_BOOK_AKKHA` **27302** (cache name "Het's capture"); the Remnant of Akkha is `TOA_PET_MORPH_AKKHA` 27377.
- **Regions** (PT, ST): Akkha room 14676, Path of Het puzzle room 14674.
- **Quadrants** (Strat): north-west fire (burn), north-east skull (shadow / darkness), south-east lightning, south-west ice
  (freeze). OrbD agrees that the Orb of Darkness appears when the player moves in the north-east quadrant.
- **Wardens**: Akkha's Phantom in the Wardens fight reuses projectiles 2253 / 2255 (MT); it is not this fight.

## Scaling

- **Raid level**: for every 5 levels an NPC's HP, defence and accuracy rise **2% additively** and damage rises 2% capped at
  **+150%** (Main). Example from Main: level 100 is +40%, level 500 is +200% (+150% damage). TA computes shadow HP with
  `4 * level / 10` percent (integer division), the same 0.4% per level. The level at which the damage cap is reached
  (150 / 0.4 = 375) is arithmetic, not stated.
- **Path level**: the first level gives HP and damage **+8%**, each further level **+5%**, up to six levels (Main; TA uses
  `3 + 5 * level` percent, i.e. 8, 13, 18 ...). Boss mechanics change at path levels **2 and 4** and cap at 4 (Main): every two
  levels add **one Memory Blast iteration**, raise **unstable-orb damage** (still capped at 25), and (Main, Raid Level
  table only) make **Detonate trigger faster**. The amounts are **OPEN-AK6**.
- **Party size**: the 2nd and 3rd member each add **90%** of the base boss HP, the 4th and later each add **60%** (Main).
  TA applies, each on the running value and in this order, the raid level percent, the path level percent, then a party
  percent, and rounds values above 100 to the nearest 5 and above 300 to the nearest 10. Its party term is applied as `hp += hp * f / 10`, so `f = 9` means
  +90% of the running HP, for 2 players, `18` for 3, and for 4 or more `9 + 6 * (n - 3)`, which is lower for 4
  players than for 3 and so is read as a plugin bug; the Main rule wins (conflict 3). TA uses the chain for the shadows only
  (base 70); applying it to Akkha's 400 is an assumption (**OPEN-AK7**, working assumption: same order and rounding for Akkha,
  shadows and orbs, behind `toa.scaling.model`).
- **Enrage swap counter** scales with party size: 3 successful hits solo, **+1 per extra player** (Page, Strat).
- **Damage by raid level**: not stated for any attack. Unstable orbs scale "to the raid and path level" and are capped at 25
  (Strat). Protection effectiveness is changed by the Quiet Prayers invocation (-10%, cache struct 432) and prayer drain by
  Deadly Prayers (20% of damage taken, struct 433).

## Rules

- **AK1 Targeting.** Akkha becomes aggressive towards the **first player** who enters the arena and focuses nobody else until
  he performs a mechanic (Strat). After a special attack he re-targets the player **closest to his centre tile**; on equal
  distance, the one with the **highest defence bonus** of stab, slash, crush, ranged and magic, checking players in **party
  order**; Dinh's bulwark makes him treat that player's defence as **50% higher** (Page, Mod Ash 12 Aug 2026). He keeps the
  first candidate unless a later one is strictly closer, or equally close with a strictly higher effective bonus. A designated
  tank can hold him by standing close; the tank may swap to melee gear during Memory Blast to keep aggro (Strat).
- **AK2 Style cycle and overhead prayer.** Akkha uses one combat style and one matching overhead protection at a time: melee
  form 11790 (Protect from Melee), ranged form 11791 (Missiles), magic form 11792 (Magic). The base order is **melee ->
  ranged -> magic** (Page); "Akkha now only protects against the same style that he's attacking with" (Sweep). The Page only says he prays against the
  style he is attacking with; Strat says the overhead "protects him from attacks of that style". What a hit in that style does (0 or a reduction) is **OPEN-AK8**. Players "stop attacking" when he changes style and prayer (14 Sep 2022).
- **AK3 Style change.** He changes style (and prayer) after a special attack (see AK4) **or** on a 60 second timer. The sources
  disagree on what the timer measures: the Page says "unable to attack his target for more than a minute", Main and the 1 Sep 2022
  note say he changes "if he has not done so after 60 seconds" (the last style change); conflict 7 (**OPEN-AK19**). The Page's "six auto-attacks followed by a
  special" and "1/4 and 1/5 chance" (Mod Ash, 17 Mar 2025) are older than the 25 Jun 2025 rework and are superseded (conflict
  1). He only changes style "whenever he is able to attack players" (Strat), which is what makes kiting possible.
- **AK4 Special cadence.** A special attack **every 7 standard attacks**; with **Stay Vigilant** every **6** (Sweep, Strat).
  At attack speed 6 that is 42 ticks (36 with Stay Vigilant) between specials if the special takes no standard-attack slot, or 48
  (42) if it takes its own slot; the sources do not say which (derived, **OPEN-AK20**). Which special is chosen is
  not stated beyond "one of the following" (**OPEN-AK9**); Detonate is group-only (Page, Strat). With Stay Vigilant, after a
  special he swaps style and prayer **only if he has made a standard attack since the previous special** ("Don't get hit and he
  won't change styles", Sweep). Without Stay Vigilant the swap after every special is assumed from the Page text
  (**OPEN-AK10**).
- **AK5 Standard melee.** Hits only his target but **cleaves** onto players next to the target, and he uses it anyway while chasing
  the target: Page says when a player is **underneath him**, Strat when a non-target is **in his path** (conflict 8). Strat adds that
  he is **incapable of attacking diagonally**, which is why he runs in circles when kited. The melee attack hits hardest (Page).
  Protect from Melee reduces it by about **75%** (Strat, after Sweep). It cannot be fully blocked (Page).
- **AK6 Standard ranged and magic.** Hit **everyone in the arena** while he walks towards his target to close the gap (Page).
  Ranged is a sand projectile (2255), magic a glowing red sand projectile (2253). **Damage is calculated when the projectile
  spawns** (Strat) and MT reads the prayer on the spawn tick, so a prayer switch after the projectile exists does not help.
  Protection reduces ranged and magic by about **80-90%** (Strat). The flight time is **OPEN-AK11**.
- **AK7 Quadrant effects** (Strat). Every quadrant special adds a heavy hit plus: fire = **burn** that periodically deals **5**
  damage several times and can spread to adjacent unaffected players; shadow = low, rapid damage and **-3 to all combat stats**
  (combat skills only since Sweep); lightning = protection prayers **temporarily disabled**; ice = attack speed **+1 tick**
  temporarily, with the message "The ice around you melts away." when it ends. The cache has infobox buff varbits for burn
  14289, darkness 14290 and freeze 14295 (disable flags) and varcs 1091 / 1092 / 1093 (end times); lightning has none. The
  durations, burn count and spread rule are **OPEN-AK12**.
- **AK8 Detonate (group only).** Message "You have been marked for detonation!", all players turn white. Each player detonates
  in a plus-shaped pattern after a delay; standing in the same row or column as another player hurts both, and **all players on
  one tile** negate it (Page, Strat). With **Feeling Special?** the pattern can be cross-shaped (an X, Main; "more directions",
  cache), not applied in solos. Footprint (Strat "3x3 plus" versus Page "row or column"), delay, damage and the path-level speed-up
  are **OPEN-AK13** (conflict 2).
- **AK9 Memory Blast.** Akkha vanishes (teleport disappear 9784 / 9786), the floor markers glow in a set order (message
  "Sections of the room start to glow..."; glow anim 9759), then the three non-lit quadrants are blasted. Players must be in the
  lit quadrant at each burst or take a heavy hit plus that quadrant's effect; a precise run through the blast also avoids it
  (Page, Strat). **Four ticks** between bursts (two with Feeling Special?, Strat). The marked quadrants are always **adjacent**
  to the previous one, never opposite (Strat). **One extra iteration per two path levels** (Main). Shadow charging halts
  during the blast; if an hourglass fills at the same time, its attack fires after the blast ends (Strat, MT).
- **AK10 Trailing Orbs.** Message "Magical orbs begin to materialise around you!"; orbs are left on the tiles a **moving**
  player passes through (standing still spawns none); an orb detonates when a player is on it "with a minor delay", for heavy
  damage plus the quadrant effect; the orb NPC for the quadrant is 11800-11803 and darkness orbs deal low rapid damage
  (OrbD). End message "The magical orbs stop materialising." **Feeling Special?** adds one extra orb in front of the player,
  whether walking or running. With Feeling Special? and Double Trouble in solo, Memory Blast and Trailing Orbs are always
  used together; at path level 0 or 1 the blasts expire before the orbs, at level 2 and higher the orbs expire first, and at
  level 4 and higher much earlier than the blasts (Strat). Spawn rate, lifetime, delay and damage are **OPEN-AK14**.
- **AK11 Shadow phases (HP 80 / 60 / 40 / 20 %).** At each threshold Akkha becomes invulnerable and four shadows (11797) appear
  in the corners with the message "Shadows appear throughout the room!" (restores use "The shadows in the room are
  restored!", which fully heals all damaged shadows). One shadow must be killed to end the immunity and Akkha must be lured
  into **that** quadrant to be damaged, otherwise he "channels immunity from his shadows" (Page, Strat). Shadows have **HP 70
  base** (scaled), defence 30, the air weakness above, and "share the same combat stats as Akkha but lower Defence" (Strat).
  No percentage damage reduction is stated: the mechanic is **immunity outside the dispelled quadrant** (**OPEN-AK15**
  records that no numeric reduction exists in any source). A shadow's hourglass fills over time (loc 46056-46067 states) and a
  bar shows on its head; if it fills the shadow slams its spear and the quadrant is enveloped in its element **from the edge
  to the centre**; killing the shadow first cancels it, and the charge resets at each HP threshold. After the attack the shadow
  resumes charging "after a short time". Charge length and slam timing are **OPEN-AK15**.
- **AK12 Final stand (enrage).** At 0 HP Akkha **restores 20% of max HP**, merges into one corner shadow (forms 11793-11796,
  shadows 11798 / 11799) and turns on **Protect from Magic and Missiles together**, so only melee damages him. He no longer
  attacks players; he launches **white Unstable Orbs** (11804) across the arena. Each moves in **one direction**, shown by an
  arrow under it, and hits **one player only** (1 Sep 2022); the pop graphic is 2260. Damage is "moderately high", scales
  with raid and path level, **capped at 25**; it also drains Prayer when Deadly Prayers is on. **Protect from Magic removes
  25%** before the cap; below raid level 300 the cap is not reached so the prayer helps, at 300+ it only helps when several
  players share the tile (damage is split) (Strat, Page). "Fewer orbs" since Sweep; count and pattern are **OPEN-AK16**.
  He stays in one quadrant until **3 successful player hits** (+1 per extra player), then jumps to another shadow; thralls
  and misses do not count (burn damage is not mentioned for Akkha, **OPEN-AK21**), each hit of a multi-hit special counts separately (Strat, 30 Aug 2023).
- **AK13 Defence and drains.** Defence can be drained to a minimum of **70** (from 80), restores "significantly faster" than
  other bosses, and the magic-defence drain of the Eye of Ayak is permanent (Strat). Main (25 Jun 2025): drained stats are **fully restored at the end of a boss fight**. Rate during the fight is **OPEN-AK17**. Eye
  of Ayak attacks no longer turn Akkha (or the shadows) invisible (14 Jan 2026).
- **AK14 Death and rewards.** Drops: Het's capture (once, only if not banked) and Eldritch ashes (obj 27223, only to the
  player who dealt the most damage) (Page). No NPC point multiplier is listed for Akkha (Drops lists 0.5x for three scarabs and
  2.5x for the Het obelisk), so 1x is assumed; the per-room cap is 20,000 room points (Drops; PT
  clamps room points at 20,000).
- **AK15 AoE stacking.** Since 1 Sep 2022 fewer AoE mechanics stack: the post's example is a Kephri bomb on a tile where two players
  stood, which no longer deals double damage (the post lists no Akkha example; whether quadrant hits, Detonate and orbs stack is
  **OPEN-AK22**). MT agrees for quadrants ("ALL players on that tile are affected", one hit per tile).
- **AK16 Remnant of Akkha.** Obtained from the reward chest only at raid level **450+**, **zero deaths for all party members**, **all
  Akkha invocations enabled** (Double Trouble, Keep Back, Stay Vigilant, Feeling Special?) and Akkha defeated at **path level 4 or
  higher** (Remnant page, Main). Priority among remnants: Akkha, Zebak, Ba-Ba, Kephri, Tumeken, Ancient (Mod Ash, 25 Jul 2023).

## Attack reference

| Attack | Animation | Projectile / graphic | Delay to impact | Damage | Prayer | Telegraph |
|---|---|---|---|---|---|---|
| Melee | 9770 / 9771, 15157 | none | not stated (OPEN-AK11) | max hit 55 is the only number (OPEN-AK2) | Melee, about -75% | animation, head icon 0; hits target and neighbours |
| Ranged | 9772 / 9773 | 2255 | not stated (OPEN-AK11) | as above | Missiles, about -80-90% | animation, head icon 1; hits all players |
| Magic | 9774 / 9775 (+ 9776-9783) | 2253, impact 2254 | not stated (OPEN-AK11) | as above | Magic, about -80-90% | animation, head icon 2; hits all players |
| Keep Back | none (no animation) | none | before the ranged / magic hit | slightly below standard melee | Melee | none visible |
| Detonate | not stated | none stated | OPEN-AK13 | heavy | none stated | players turn white, message |
| Memory Blast | 9784 / 9786 disappear, 9785 / 9787 appear | 2256-2259 per quadrant | 4 ticks between bursts, 2 with Feeling Special? | heavy + effect | none | glow anim 9759, message, Akkha vanishes |
| Trailing Orbs | not stated | orb NPCs 11800-11803, 2256-2259 | "minor delay" | heavy (darkness: low, rapid) | none | message, orbs on the floor |
| Shadow slam | shadow anim not stated | 2256-2259 | OPEN-AK15 | heavy + effect | none | hourglass 46064-46067 full, head bar |
| Unstable Orb | 9794 `FX_ORB_DUST_IDLE` is the cache stand anim of the orb NPCs; no source ties it to this attack | pop 2260 | moves in one direction (arrow), speed OPEN-AK16 | up to 25 | Magic -25% before cap | arrow under the orb |
| Stomp | 9788 / 9789 | none | not stated | not stated | not stated | cache name only (OPEN-AK3) |

## What the RuneLite plugins time or detect

| Plugin value | Meaning | Confidence |
|---|---|---|
| MT: graphics objects 2256-2259 on the arena = quadrant bomb tiles | Memory Blast, hourglass slam and Detonate share these ids; "ALL players on that tile are affected" | high (client observation) |
| MT: activation tick = graphics-object start tick + **0** for Memory Blast / explode (comment: since 10/03/2022 all objects spawn at once with a **1-tick** start delay, so corners can no longer be skipped) | impact is on the tick the animation starts | medium |
| MT: hourglass slam subtracts **9 cycles** (3 cycles per tile, up to 3 tiles from the centre) and adds a **1-tick** delay; "outer tiles are on tick 1, everything else tick 2" | the slam graphics play in reverse, so the outer ring lands first | medium |
| MT: a hit needs the player's **previous-tick** tile in the set; **1-tick cooldown** between quadrant hits (two ticks in a row give one hitsplat) | position is evaluated before the move; no repeat hit next tick | medium |
| MT: hourglass special = at least one full hourglass (46064-46067) and Akkha present; if Akkha leaves for Memory Blast as an hourglass fills, the blast goes first | order rule, matches Strat | high |
| MT: elemental orbs 11800-11803 (names Orb of Lightning / Darkness / Burning Orb / Frozen Orb) hit on **NPC despawn** at the orb tile | orb explodes when stepped on | medium |
| MT: unstable orb pop graphic **2260** on NPC "Unstable Orb"; orb despawns one tick after hitting; one orb hits one player | final-stand hit detection | medium |
| MT (Wardens P3): projectiles 2253 = magic, 2255 = ranged; "Akkha prayer matters on spawn tick" | server checks the overhead on the launch tick | medium |
| TA: shadow base HP **70**, factors above, rounding 5 / 10 | shadow HP overlay | high for formula, see conflict 3 |
| TA / ST / PT: region 14676 (room), 14674 (puzzle) | room detection | high |

## Invocations (Akkha)

Cache category 10 (struct number in brackets); modifier from cache param 1162, equal to Main for all four.

| Name | Raid level | Cache text (struct) | Wiki text |
|---|---|---|---|
| **Double Trouble** | +20 | "Akkha will do two special attacks at once." (448) | same (Main, Strat); solo is not toggled; with Feeling Special? it pairs Memory Blast with Trailing Orbs (Strat) |
| **Keep Back** | +10 | "Akkha's ranged and magic attacks will also do melee damage to anyone next to him." (449) | the melee hit lands **first, with no animation**, slightly weaker than a standard melee; "anyone next to him" |
| **Stay Vigilant** | +15 | "Akkha will randomly swap between attack styles and protection prayers rather than at set intervals." (542) | special every **6** attacks; swap only after a special and only if a standard attack happened since the last special (Sweep, Strat); see conflict 1 |
| **Feeling Special?** | +20 | "Akkha's detonate special attack will trigger in more directions, his memory special attack will be faster and his trail special attack will spawn additional orbs." (543) | X-pattern Detonate (not in solos), Memory Blast gives **2 ticks** between bursts, Trailing Orbs spawns **1 extra orb** in front of the player |

Path-level and prayer invocations interact (Quiet Prayers, Deadly Prayers, Pathseeker +15 / Pathfinder +40 / Pathmaster +50, Walk
the Path +50; cache structs 428-433); their raid-wide rules belong to the missing `../toa.md`.

## Open items

- **OPEN-AK1** head icon sprite images for indices 0 / 1 / 2 / 6 (assumed Melee / Missiles / Magic / combined).
- **OPEN-AK2** the 55 max hit: which attack and which raid level; all special-attack damage by raid level. Assumption: 55 is the
  raid-level-0 solo melee max hit; special damage is a placeholder table with no source value, loaded from config. Flag
  `toa.akkha.damage_table`.
- **OPEN-AK3** which animation (spear / sword, magic 01-04, stomp 9788 / 9789, idle 15156, attack 15157) plays when. Assumption:
  spear set by default; stomp unused. Flag `toa.akkha.anim_set`.
- **OPEN-AK4** which hourglass loc belongs to which quadrant; hourglass fill time.
- **OPEN-AK5** resolved: Het's capture is obj 27302 (see Data); the drop rule is the Page's (once, if not banked).
- **OPEN-AK6** Memory Blast base iteration count; unstable-orb damage per path level; Detonate speed-up per path level.
- **OPEN-AK7** whether the party formula applies to Akkha's 400 HP and to accuracy / damage, and the rounding.
- **OPEN-AK8** what damage a hit with the prayed-against style does (0 versus a reduction).
- **OPEN-AK9** how the next special is chosen (random, fixed rotation), and whether Double Trouble can repeat one.
- **OPEN-AK10** style rotation without Stay Vigilant after a special (assumed: always swaps, melee -> ranged -> magic).
- **OPEN-AK11** projectile flight and melee hit delay in ticks for the three standard attacks. Unsourced working
  assumption: 1 tick for melee and 2 ticks for ranged and magic, behind `toa.akkha.hit_delay`.
- **OPEN-AK12** quadrant-effect durations: burn tick count and interval, spread radius, darkness tick damage and length, freeze
  duration, lightning prayer-off duration.
- **OPEN-AK13** Detonate: footprint (3x3 plus versus row / column reach), delay, damage, X-pattern geometry, path-level speed.
- **OPEN-AK14** Trailing Orbs: spawn rate, lifetime, detonation delay, damage, attack duration.
- **OPEN-AK15** shadow slam: charge time, slam timing; no numeric damage reduction exists for shadows or Akkha during shadow
  phases (immunity only); whether phase transitions at 80 / 60 / 40 / 20 use current or max HP.
- **OPEN-AK16** final stand: orb count, spawn pattern, speed, damage by raid and path level, Prayer drain amount.
- **OPEN-AK17** stat restoration rate during the fight and the exact drain floor rule (flat versus percent); end-of-fight restore is stated.
- **OPEN-AK18** Keep Back damage and area ("anyone next to him": which tiles).
- **OPEN-AK19** the "unable to attack for more than a minute" timer: 60 s is 100 ticks; the Page puts it on his last attack, Main and the 1 Sep 2022 note on his last style change (conflict 7).
- **OPEN-AK20** whether the special takes its own slot in the "every 7 (6) attacks" count (gap 42 or 48 ticks).
- **OPEN-AK21** whether burn damage counts towards the three-hit swap counter (not stated for Akkha).
- **OPEN-AK22** which Akkha AoE mechanics stack (quadrant hits, Detonate, orbs); only the Kephri bomb example is given.

**21 open items (OPEN-AK5 resolved).** Working assumptions sit behind named config flags (`toa.scaling.model`, `toa.akkha.*`).

## Conflicts between sources

1. **Stay Vigilant.** Cache struct 542: "randomly swap between attack styles and protection prayers". Page: 1/4 and 1/5 swap
   chances, minimum two attacks per style (Mod Ash, 17 Mar 2025), special every six. Sweep / Strat / Main (25 Jun 2025): fixed
   rule above. The cache text is an old UI string and the Page paragraph predates the rework, so the dated 25 Jun 2025 notes win.
2. **Detonate shape.** Page: "+ shaped pattern", avoid by not sharing a row or column; Strat: "3x3 plus-shaped", Feeling Special? "3x3
   cross-shaped"; Main: "X pattern". Treated as OPEN-AK13.
3. **Party formula.** Main: 2nd and 3rd +90% each, 4th+ +60% each. TA code, read literally, gives a lower factor for 4 players
   than for 3. Main wins.
4. **Special cadence.** Page "six auto-attacks followed by a special" versus Sweep "every 7, up from 6". Sweep wins.
5. **Protection.** Page "cannot be fully blocked by prayer similarly to Kephri" versus the invocation-free reductions in Strat
   (about 75% melee, 80-90% ranged / magic); Strat numbers used.
6. **Enrage swap.** Page "three successful hits (+1 per player)"; Strat "three in solo, one more per player"; Main (30 Aug 2023)
   limits it to the player's own successful hits. All consistent.
7. **60 second timer.** Page: since he was last unable to attack his target; Main and the 1 Sep 2022 note: since he last changed
   style. Neither is verified; OPEN-AK19.
8. **Melee while chasing.** Page: used if anyone is underneath him; Strat: if a non-target is in his path. OPEN, both recorded in AK5.
