# The Maiden of Sugadinti (room 1)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "The Maiden of Sugadinti" (Page), "Nylocas Matomenos" (Mato),
"Blood spawn" (Blood), "Theatre of Blood/Strategies" (Strat), "Theatre of Blood/Entry Mode" (Entry), "Theatre of
Blood/Hard Mode" (Hard), the 1 Mar 2023 Entry Mode update (U-Entry23); cache 2757 (`cache-dump config npc <id>`; stats
array order attack, defence, strength, hitpoints, ranged, magic). Raid-wide rules: [../tob.md](../tob.md). Tick
timelines below list only what a source states; everything else is `OPEN-MA<n>`.

## Data

- NPC ids (wiki infobox; cache confirms the names): **Entry 10814** (the cache has 10814-10819 for the phases);
  **Normal 8360** (100%), **8361** (70%), **8362** (50%), **8363** (30%) (+ dying forms 8364 / 8365 in the cache
  gamevals); **Hard 10822** (10822-10827 in the cache). Combat level **324 / 940 / 1128**; size 6; attacks with magic;
  attack speed **10**; aggressive; poison and venom immune (resistance 100).
- Max hit (infobox): Entry 18, Normal 36, Hard 36 (Hard's cap is raised during the fight, MA8).
- Stats (infobox, cache agrees): Entry att 140, str 140, def 80, magic 140, ranged 140, magic attack bonus +150, magic
  strength +12. Normal and Hard att 350, str 350, def 200, magic 350, ranged 350, magic attack bonus +300. All defensive
  bonuses 0 (stab, slash, crush, magic, light, standard, heavy). XP bonus 0.
- HP: Normal and Hard base **3,500** (5 players). By party size (TB10): 1-3 players 2,625; 4 players 3,062.5 (rounding
  **OPEN-MA1**); 5 players 3,500. Entry: cache base 500, infobox 2,000 for five; by party size (TB11): 1 -> 500, 2 -> 950,
  3 -> 1,350, 4 -> 1,700, 5 -> 2,000.
- **Nylocas Matomenos** (Mato): size 2; no attack (Monster Examine shows stab, max hit 11 / 1); 15% weakness to fire
  spells (25 Jun 2025); can be burned (24 Jul 2024); cannot be poisoned at the Maiden. Maiden ids: Entry **10820**
  (combat 68, HP 64 at five players, cache base 16), Normal **8366** (combat 115, HP 100, cache 100), Hard **10828**
  (combat 138, HP 100). Stats att / str / def / magic / ranged 100 (Entry cache 30 / 30 / 30 / 30 / 30). Defence
  bonuses 0. NPC `MAIDEN_ELEMENTAL` 8366 in the gamevals.
- **Blood spawn** (Blood): size 1; "does not attack" (max hit 1); ids **8367** (Normal, combat 55, HP 120), **10821**
  (Entry, wiki HP 7, cache combat 35 and HP 10), **10829** (Hard, cache combat 183, HP 120); 50% weakness to earth
  spells (25 Jun 2025). The blood trail it leaves deals **2-5** damage in Entry and **5-13** in Normal.

## Fight

- **MA1 Blackstorm (normal attack):** magic, every **10 ticks**, at one player. Target priority (Strat): closest player,
  then players on her north / east side, then orb order (the Page says closest, ties in party order). The attack always
  lands as a successful hit and deals **36.5 + 3.5 x c** (Strat) where c is the number of Matomenos that reached her; Protect
  from Magic halves it (**OPEN-MA11:** the formula gives 36.5 with c = 0 while the infobox max hit is 36: whether the
  result is rounded down, truncated or the cap is 36 plus the Matomenos term is not stated; likewise how the halved
  value rounds); it over-hits beyond the player's current HP so it cannot be tick-eaten. 50% chance to drain the
  target's combat stats by **(damage + 1) / 5**, on the style where the player's attack bonus is highest at that moment.
  Players she is not targeting need no prayer.
- **MA2 Blood splats:** thrown at the tile each player stands on (she uses a different animation than the Blackstorm: seq
  `MAIDEN_ATTACK_BLOOD` 8091 vs `MAIDEN_ATTACK_SPECIAL` 8092); she cannot use it again for the next **two** attacks
  (Strat). One player also gets additional splats at random inside a 5x5 around them: Strat says "One player will have two
  additional blood splats thrown randomly in a 5x5 area centred around them"; the Page says "blood spots at each player,
  and three blood spots at the player standing furthest away". These are compatible: the own-tile splat plus two
  additional makes three on the furthest player. **OPEN-MA2:** only which player is chosen when several are equally far. A splat deals
  **10 + 2 x c**, heals her by the same amount and drains prayer.
- **MA3 Blood spawns:** each splat has a **10%** chance of becoming a blood spawn, **20%** if one was stepped on (Strat);
  halved when all players dodge, and at most **one** spawn appears if they do (12 Aug 2021); at most **8** spawns at a
  time (9 Jan 2020). They wander randomly leaving trails: standing on one damages the player and heals her by the same,
  and raises the chance of another spawn; trails vanish after "roughly ~20-30 seconds" (Strat; 33-50 ticks converted,
  **OPEN-MA3**). Blood pools no longer appear inside her own area (8 Aug 2019).
- **MA4 Matomenos waves:** at **70%, 50% and 30%** of her HP, the first time she reaches each, she spawns **two
  Matomenos per player (at most 10)** from the north and south walls (her appearance changes); they walk straight to her
  and each that reaches her **heals her for twice its remaining HP** and increases `c` (the Blackstorm and splat
  damage). 10 spawn locations, five per side: N1-N4 and S1-S4 with two possible tiles for N4 and S4 (Strat; three in
  the north / south central area and two each in the NE / SE). With fewer than five players the locations are random
  (Page). Strat HP per Matomenos: 200, 175 in 4-man, 150 in trios and below; **conflict:** the Mato infobox and the cache
  say 100 for the Maiden's (200 is the Verzik variant) (**OPEN-MA4**; infobox / cache used).
- **MA5 Freezing:** the Matomenos can be frozen (ice spells, including bind and snare) and then killed; frozen ones do
  not advance. The chance to freeze scales with the player's effective magic attack bonus (including hidden bonuses such
  as Void) and is **100% at +140 or more** (9 Jan 2020); the Mato page gives the accuracy roll as (base Magic + 9) x 204
  (22,032 at 99 Magic) and a table of bonuses per Augury / Mystic Vigour / heart (**OPEN-MA5**: the exact roll, as the
  page itself flags the constant as measured). Dark Lure does not work on them.
- **MA6 Damage on Matomenos counts for the MVP** (3 Jun 2021). The MVP points for most damage at the Maiden: 2 (TB27).
- **MA7 Death:** she drops the book "Serafina's diary" (always) unless the player has read it (26 Mar 2025). Boss killed
  => restores (TB9). Book obj `TOB_BOOK_MAIDEN` 22504, all modes.

## Per-mode differences

- **Entry:** reduced stats and damage (max hit 18); fewer Matomenos (count **OPEN-MA6**; the Entry page says "At 70%,
  50%, and 30% health, she will spawn a red Nylocas Matomenos on either side of the arena", which can be read as one per
  side, but it gives no party-size rule or total); blood spawn trails 2-5; the
  Blood Nylocas HP was raised in the Story-mode hotfix of 9 Jun 2021 (value **OPEN-MA6**). Retry after a wipe: TB16.
- **Normal:** as above.
- **Hard:** always **10** Matomenos regardless of party size (Page; the Hard page says "all eight", see conflict 6); the **first five** that reach her also increase her attack
  speed (amount **OPEN-MA7**); blood spawns are **invulnerable** (still attackable) and their trails **never despawn**;
  her damage cap is raised so she can "hit faster and harder" when Matomenos reach her or players stand on splats;
  attacking the same player more than twice in a row raises her maximum hit (amounts **OPEN-MA8**). Hard solo (9 Jun 2021
  hotfix): the ramp damage is disabled and the S1 Matomenos spawn is removed.

## Tick timeline (stated parts)

- Blackstorm every 10 ticks. After a splat attack the splat cannot recur for the next 2 attacks (20 ticks).
- Blood trail lifetime about 20-30 s (33-50 ticks, converted at 0.6 s per tick).
- Matomenos spawn when HP first crosses 70, 50 and 30%; their travel time to her is **not stated** (**OPEN-MA9**).

## Changes up to 2026-11-03 that matter

3 Jun 2021 modes added; 9 Jun 2021 hotfix (Hard solo, Entry Blood Nylocas HP); 12 Aug 2021 fewer blood spawn positions and
a maximum of one spawn when the blood is dodged; 9 Jan 2020 freeze chance by magic attack bonus, spawn chance halved on
dodge, cap 8; 8 Aug 2019 pools; 18 Jun 2020 death animation shorter; 24 Jul 2024 burn on Nylocas; 25 Jun 2025 fire
weakness (Matomenos) and earth weakness (blood spawn). No ToB Maiden change dated after 25 Jun 2025.

## As implemented (content/combat TobMaiden.kt)

Mode comes from the NPC id (`TobModes.of`: Entry = `_STORY` block, Hard = `_HARD` block, else Normal). Entry damage is
`TobModes.damage` = Normal value / 2 (floor), applied to the Blackstorm and the splat (not to the trail, which has its own
range). Boss HP and Matomenos HP are set by `TobModes.scale` / raids-tob `TobScaling` (TB10 / TB11, base = cache HP).

- **Clock:** `spawned` sets her first attack to tick + **9**, then `attack` re-arms at tick + `speed` (**10**; Hard lowers it,
  below). A per-tick loop (every tick, via `queueHit(1)`) runs, in this order: pools/trails, Matomenos, blood spawns. No
  raiders in her arena (`TfaKit.arena`: living players within 24) = no attack, no re-arm.
- **Attack choice:** if the blood lockout is spent she throws blood with chance **1/2**, else the Blackstorm. After a blood throw
  the lockout is **2**, decremented on every Blackstorm, so two Blackstorms follow a blood throw (MA2). **OPEN-MA10 (code
  label):** the 1/2 odds are not sourced. Clash: maiden.md's own OPEN-MA10 is a different item (see Open list).
- **Blackstorm (MA1):** target = raider nearest her centre (SW tile + 3, +3; Chebyshev), capture-based. Anim 8092, projectile
  1577 height 43/43, start 109, end 109 + 10 x distance cycles; the hit is queued `end / 30` ticks later (4 ticks at 3
  tiles, 7 at 11). Style magic, `HitStyle.MAGIC`.
  - Damage `(365 + 35c) / 10` (integer division, c = Matomenos that reached her): 36, 40, 43, 47, 50, 54, 57, 61, 64, 68,
    71 for c = 0..10 (**OPEN-MA11**: rounded down). Entry: halved (18 at c = 0). Protect from Magic: `hit / 2` (floor); the
    storm always lands, no accuracy roll.
  - Hard: consecutive storms on the same player count (`sameTarget`; a different target resets it to 1; blood throws do not
    count). From the 3rd on, `hit += 5 x (sameTarget - 2)` (uncapped unprayed). Prayed Hard = `min(50, hit / 2 + 5 x
    max(0, sameTarget - 2))`, i.e. the ramp is in `hit` already and is added again after halving (**OPEN-MA8**: ramp 5 and
    prayed cap 50 are from captures).
  - Drain on the throw tick (not on landing): **30%** (`random(100) < 30`), Attack, Strength and Defence each lowered by
    `dmg / 5 + 1` using the final (post-prayer) damage, floor 0, message "Your stats are drained." **OPEN-MA12:** which stats
    (melee three assumed); capture says 30% and floor(hit / 5) + 1, the wiki (MA1) 50% and (damage + 1) / 5.
- **Blood throw (MA2/MA3):** anim 8091. One projectile 1578 (height 43 -> 0, start 20, end 20 + 15 x distance) per raider
  at the tile the raider stands on when she throws, landing `end / 30` ticks later; plus **2** extra splats at random
  walkable tiles within **2** of the furthest raider (furthest from her centre; ties = first in list), landing `(20 + 15d +
  25) / 30` ticks later (d = distance of that tile). No "which player when tied" rule beyond list order (**OPEN-MA2**).
  - On landing: lingering-blood graphic, a splat pool lives until landing tick + **11**; it is removed when tick > that.
  - **10** ticks after landing it may raise a blood spawn: not if 8 blood spawns are alive; chance **12%** if some raider
    stood on that pool, else **9%** (capture-values; the wiki says 10% / 20%); at most **one** spawn per throw if no pool of
    that throw was stood on (12 Aug 2021). Spawn appears on the pool tile, `huntRange` 0.
  - Pool damage per tick on a raider standing on it: `5..10 + 2c` (inclusive roll), Entry halved after adding; typeless; heals
    her by the same amount (capped at max HP); drains prayer by `raw / 2` (**OPEN-MA13**: wiki says only "drains prayer").
    Only the first pool on a tile is used; standing on it marks the pool "stood on".
- **Blood spawns:** do not attack; each tick, if no pool is on its tile it leaves a trail pool there (lasts **29** ticks, Hard
  never: `until = -1`; also a scenery loc `TOB_MAIDEN_BLOOD`, shape 22, same lifetime), and with chance 1/2 it walks to a random
  tile within **3**. Trail damage per tick to a raider on it: Normal/Hard `5..13`, Entry `2..5`, typeless (not halved again),
  heals her by the same amount; no prayer drain. Hard spawns take 0 damage (`incoming` returns 0, still attackable).
- **Matomenos (MA4):** form change on `afterHit` when `hp x 100 <= maxHp x {70, 50, 30}` (equal counts); a hit crossing several
  thresholds fires each. Each: `transform(type + 1)` keeping HP, then a wave. Count: Normal `min(10, 2 x raiders)`
  (at least 1 raider counted), Hard **10** (**9** when 1 raider or fewer), Entry **2** (**OPEN-MA6**). Tiles: a random subset
  of 10 offsets from her spawn tile (N `(11,12) (15,12) (19,12) (22,12) (23,11)`, S the same x at z -8, `(23,-7)`), not
  balanced per side, and Hard solo drops a random tile rather than S1 (**OPEN-MA9**: x columns from RuneLite). HP =
  `TobModes.scale` of cache HP (so 100 base, i.e. the infobox/cache value, not Strat's 200/175/150; **OPEN-MA4**). They
  inherit the raid-run tag (damage counts for MVP, MA6). Born tick recorded; from the **next** tick they `walkTo` her centre
  unless frozen (MA5: any freeze, no accuracy roll is modelled). When the gap to her footprint is <= 1 they despawn, she
  heals `2 x` its current HP (capped) and `c` rises by 1. Walk speed is the engine's (**OPEN-MA9**).
- **Hard speed-up (MA7):** each of the first **5** Matomenos that reach her lowers `speed` by 1 (min **3**, which five
  steps from 10 never reach); effective from her next attack. Not the RuneLite "attacked early" ratchet (**OPEN-MA7**).
- **Death (MA7):** plays dying_a (`MAIDEN_DEATH_A`) and dismisses all minions. The captured T+1 / T+4 / T+8 death sequence is not
  scripted here; the book and restores belong to raids-tob.
- **Not implemented here:** Matomenos MVP is only the run tag; Blackstorm Hard cap beyond the prayed 50, Entry blood-spawn HP
  raise (9 Jun 2021) and Entry Blackstorm formula beyond halving are not modelled.

## Open list

OPEN-MA1 4-man HP rounding; OPEN-MA2 splat target among equally far players; OPEN-MA3 trail lifetime in ticks and spawn / move rules of the
blood spawn; OPEN-MA4 Matomenos HP (200/175/150 vs 100); OPEN-MA5 exact freeze roll; OPEN-MA6 Entry Matomenos count / HP and
Entry Blood Nylocas HP; OPEN-MA7 Hard attack speed per Matomenos; OPEN-MA8 Hard cap and max-hit ramp numbers; OPEN-MA9
Matomenos walk speed / arrival time, spawn tile coordinates and the delay between the HP threshold and the spawn;
OPEN-MA10 the stat-drain target stat when two bonuses tie, the Blackstorm cap in Hard, the Entry Blackstorm formula (only
the max hit 18 is stated), and the Entry splat damage; OPEN-MA11 Blackstorm rounding and 36.5 vs max hit 36 (code: `(365 + 35c) / 10`, floor, halved with `/ 2`);
OPEN-MA12 the stats the Blackstorm drain lowers (code: Attack, Strength, Defence; 30% of storms, `dmg / 5 + 1`);
OPEN-MA13 the prayer a blood splat drains (code: half its damage). **13 open items.**

## Conflicts between sources

1. Matomenos HP at the Maiden: Strat 200 / 175 / 150 vs infobox 100 and cache 100 (Verzik variant 200).
2. Solo Entry HP "20% of original" (Page) vs cache base 500 of 2,000 (25%) and U-Entry23.
3. Not a conflict: Page ("three at the furthest player") and Strat (own tile plus "two additional" on one player) agree; see MA2 / OPEN-MA2.
4. Blood spawn Entry HP 7 (wiki) vs 10 (cache), and combat level 55 for all modes (wiki) vs 35 / 55 / 183 (cache).
5. Wipe in Entry: retry the room (Page) vs restart the raid (Entry page): TB16.
6. Hard Matomenos count: **10** (Page: "will always spawn the maximum number of Nylocas Matomenos (10)"; also the 2-per-player,
   at-most-10 rule of MA4) vs "all eight" (Hard page). 10 is better supported (it matches the Maiden page and the MA4 cap)
   and is used.
