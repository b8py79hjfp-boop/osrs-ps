# Lizardman shamans (CoX combat room)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Lizardman shaman (Chambers of Xeric)" (Page), "Lizardman shaman" (the
surface monster, Surface), "Chambers of Xeric" (Main), "Chambers of Xeric/Strategies" (Strat), "Chambers of Xeric/Challenge Mode"
(CM); cache 2757 (`cache-dump config npc <id>`; the `stats` array is `[1,1,1,1,1,1]` on every Chambers NPC because the raid scales
them at runtime, so numbers come from the wiki infobox and the cache `params` map: params 4 = ranged attack bonus, 5 / 6 / 7 =
stab / slash / crush defence bonus, 8 = magic defence bonus, 10 = melee strength bonus, 12 = ranged strength bonus; they agree
with the infobox for 7573 / 7574); generated symbols (`Npcs`, `Seqs`, `SpotAnims`, `Locs`). Raid-wide rules: `../cox.md` (not part
of this set). Seconds are converted at 0.6 s per tick. Tick timelines below list only what a source states; everything else is
`OPEN-SH<n>`.

## Data

- NPC ids (the infobox gives one id pair for Normal and Challenge Mode): **7573** `RAIDS_LIZARDSHAMAN_A` and **7574**
  `RAIDS_LIZARDSHAMAN_B` (the lighter and darker model; name "Lizardman shaman", op2 "Attack", size **3**, category 882, same
  params), **7575** `RAIDS_LIZARDSHAMAN_BLOCKER` (name "Spawn", size 1, no ops; the purple spawns that "block the way forward" and
  explode, cf. surface 6768 `ZEAH_LIZARDSHAMAN_SPAWN`). Aggressive, poison and venom immune (resistance 100). The surface shaman
  ids are 6766 / 6767 (`ZEAH_LIZARDSHAMAN_1`, `_2`) and 7744 / 7745 (`LIZARDMAN_CAVE_SHAMAN_*`) with the same param 50 = 90 as 7573.
- Attack: melee and ranged, **attack speed 4**; Page max hit "**65 (unconfirmed)**". Poison from **12** ("Yes (12)"); the surface
  shaman poisons from 10. Slayer category lizardmen, assigned by Chaeldar, Nieve, Duradel; not counted for Sherlock's master clue.
- Stats (infobox, scaled for a maxed player): Normal att 130, str 130, def 210, magic 130, ranged 130; CM att 195, str 195, def 315,
  magic 195, ranged 195. Attack bonus +58, strength +52, ranged attack +56, ranged strength +49, magic 0. Defence bonuses
  stab **102**, slash **160**, crush **150**, magic **160**, light **-10**, standard 0, heavy 0 (cache params 5 / 6 / 7 / 8 = 102 /
  160 / 150 / 160, param 4 = 56, 10 = 52, 12 = 49). XP bonus 17.5 / 27.5.
- HP (infobox, scaledhp): **190** Normal, **285** CM (x1.5). The surface shaman has 150. How HP and stats follow party size and highest
  combat level: **OPEN-SH1** (no wiki formula).
- **Count by raid scale** (Strat table, the raid's scale, not the number of players present): scale 1-4 -> **2**, 5-9 -> **3**,
  10-14 -> **4**, 15+ -> **5**. Page: "a maximum of five shamans ... a minimum of two". Strat text says "at least two (dependent on
  party size, although capped at 4)": conflict 1.
- Stat regeneration **1 per 20 ticks** (Mod Ash, Discord 9 Jul 2026).
- Spot anims (the surface set is reused): `LIZARDSHAMAN_SPIT_ACID` 1293, `LIZARDSHAMAN_ACID_SPLASH` 1294,
  `LIZARDSHAMAN_SPAWN_EXPLODE` 1295. Stand / walk seqs in the cache are 7191 / 7195 (`SHAY_LIZARD_WARRIOR_READY` /
  `_WALK`, shared with the lizardman warrior; `_ATTACK_MELEE` 7192, `_ATTACK_RANGED` 7193, `_DEFEND` 7194, `_DEATH` 7196 exist).
  The shaman's own jump, spit and spawn-summon animation ids are **not named** in the symbols (**OPEN-SH2**).
- Scenery: spirit tendrils at the entrance `RAIDS_SHAMAN_TENDRILS_ENTRANCE` 29768 ("one or two sets of spirit tendrils" to stop
  players safespotting the shamans away from the chamber; Strat). Drops: Big bones, 5-10 each of buchu, golpar and noxifer seeds
  (`RAIDS_SEED_*`), long bone 1/400, curved bone 1/5012.5.

## Fight

The Page says the raid shamans "share the same attack set" as the surface ones, with a stronger poison blob. The surface rules
below come from the Surface page.

- **SH1 Room:** several layouts exist; there are at least two shamans and "several spawns blocking the way forward" (7575). Tendrils
  stop players from safespotting them from outside the chamber. The chamber has safespots but the player must attack first to lure
  them (Strat); the CM-Strat says to hug the edges so they do not jump (CM) and use the western edge's chasm with a weapon of
  range greater than 8 tiles. A weapon with a range of 10 can safespot from afar (Strat). Aggro range and the lure rule:
  **OPEN-SH3**.
- **SH2 Standard attacks:** melee and ranged, every 4 ticks; they "are able to attack with melee diagonally if the player is in range"
  (Surface). Protect from Missiles and distance recommended. Style choice rule: **OPEN-SH4**.
- **SH3 Acid spit (poison blob):** a green projectile that on the surface deals **25-30** (Surface). In Chambers "their poison blob
  attack is ~25% stronger, dealing up to **39** damage to players ... without the appropriate Shayzien armour protection" (Page);
  Strat says "up to **40**". Poison from 12, "ignores prayers" (Surface wording). Shayzien armour tiers 1-4 reduce it, tier 5
  negates it (Surface); whether and how that applies inside the raid, and the minimum damage, are **OPEN-SH5**.
- **SH4 Jump:** a jump that lands on the tile area where the player stood; **20-25 typeless** in a **3x3** area, not used if the
  landing spot is blocked (Surface; the temple variant is 2x2 and not relevant). It cannot be attacked until it has landed; the
  hitbox can be clicked mid-jump. Whether it scales in the raid: **OPEN-SH5**.
- **SH5 Spawns:** a shaman summons **3** purple spawns next to the player (ground shaking), which explode after a few seconds for
  **5-10** depending on proximity, in a 5x5 area (a radius of 2 tiles) (Surface); since 8 Feb 2018 spawns "will no longer occur in
  quick succession" (cooldown value **OPEN-SH6**). The special attacks have no cooldown except the spawn summon (Surface).
  Special-attack selection chances: **OPEN-SH6**.
- **SH6 Death:** drops as in Data; a message names the top damage dealer; potions and seeds are free for all. The shamans do not
  respawn (not stated; **OPEN-SH7**).

## Points (stated)

- Combat points come from damage dealt (Main); formula, per-player cap and the mini-boss "decay" multiplier are **OPEN-SH8**. Shamans
  are a "combat encounter, not a boss" (Strat); whether the decay multiplier applies to them is **OPEN-SH8**.
- Raid-wide death and reward rules: `../cox.md`.

## Per-mode differences

- **Normal:** HP 190, def 210, att / str 130.
- **Challenge Mode:** HP 285, att / str 195, def 315 (x1.5). The shaman room is on the top level, after the Ice demon (CM layout is
  static). The count still follows the raid scale; there is no other CM change stated. CM-Strat: "Clear shamans as normal."

## Tick timeline (stated parts)

- Attack speed 4. Regeneration 1 per 20 ticks. Spawn explosion: "after a few seconds" (not a number). No other timings stated.

## Changes up to 2026-11-03 that matter

8 Feb 2018 spawn summons no longer quick succession (Surface changelog; affects the raid shamans too). The Chambers shaman page has
no changelog section; nothing dated after 14 Jan 2019 (temple spawn radius, Surface only) concerns them.

## Open list

OPEN-SH1 HP and stat scaling by party size and highest combat level; OPEN-SH2 the shaman's attack animation ids (names missing);
OPEN-SH3 aggro range and lure rule; OPEN-SH4 style choice (melee versus ranged); OPEN-SH5 acid damage range inside the raid
(39 vs 40), armour interaction, and whether jump / spawn values are scaled; OPEN-SH6 special attack selection chance and spawn
cooldown; OPEN-SH7 respawn; OPEN-SH8 combat point formula and decay. **8 open items.** Working assumption: the Surface special
attack rules, with the acid capped at the Page's 39 (config flag for 40); minimum damage unknown.

## Conflicts between sources

1. Count: Strat text "capped at 4" vs Strat table (15+ -> 5) and Page "maximum of five"; the table is used.
2. Acid maximum: Page "up to 39" vs Strat "up to 40". Base 25-30 x 1.25 is 31-37.5, neither value.
3. Max hit "65 (unconfirmed)" (Page infobox) vs surface max hits 31 / 21: the raid shaman is a different (scaled) monster; no source
   explains the figure.
Not a conflict: Strat says the shamans share the mechanics "outside" and "their poison spit is increased and can deal up to 40", the Page says "~25%
   stronger ... 39": the same remark.
