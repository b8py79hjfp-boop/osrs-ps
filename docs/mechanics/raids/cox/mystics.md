# Skeletal mystics (CoX combat room)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Skeletal mystic" (Page), "Chambers of Xeric" (Main), "Chambers of
Xeric/Strategies" (Strat), "Chambers of Xeric/Challenge Mode" (CM); cache 2757 (`cache-dump config npc <id>`; the `stats` array is
`[1,1,1,1,1,1]` on every Chambers NPC because the raid scales them at runtime, so numbers come from the wiki infobox and the cache
`params` map: params 3 = magic attack bonus, 5 / 6 / 7 = stab / slash / crush defence bonus, 8 = magic defence bonus, 10 = melee
strength bonus, 65 = magic strength bonus; they agree with the infobox for 7604-7606); generated symbols (`Npcs`, `Seqs`,
`Locs`). Raid-wide rules: `../cox.md` (not part of this set). Seconds are converted at 0.6 s per tick. Tick timelines below list
only what a source states; everything else is `OPEN-MY<n>`.

## Data

- NPC ids (one id set serves Normal and Challenge Mode): **7604** `RAIDS_SKELETONMYSTIC_A`, **7605** `RAIDS_SKELETONMYSTIC_B`, **7606**
  `RAIDS_SKELETONMYSTIC_C` (name "Skeletal Mystic", op2 "Attack", size **2**, category 1033, identical params; the three are the
  appearance / spawn variants, cf. the lizardman shaman A / B). Undead (Salve amulet works), attack range **10**, **not aggressive**
  (Page infobox "No"), poison and venom resistance **0** (the infobox, unlike most Chambers monsters).
- Attack: magic and melee, **attack speed 4**, max hit "Varies". XP bonus 20 / 30. Slayer category skeletons, 160 / 240 Slayer XP
  (Turael, Spria, Mazchna; it is on the Slayer lists, whether kills in the raid count is not stated).
- Magic attacks: the "standard" Fire Blast and "a damaging form of Vulnerability" (Page; Strat "Fire Blast or Vulnerability");
  the Vulnerability version also debuffs (effect, magnitude **OPEN-MY1**). Melee punch.
- Stats (infobox, scaled for a maxed player): Normal att 140, str 140, def 187, magic 140, ranged 1; CM att 210, str 210, def 280,
  magic 210. Attack bonus +85, strength +50, magic attack +40, magic strength +38 (cache params 3 = 40, 10 = 50, 65 = 38; melee
  attack +85 is not in the params). Defence bonuses stab **155**, slash **155**, crush **75**, magic **140**, light **115**, standard
  **115**, heavy **75** (cache 5 / 6 / 7 / 8 = 155 / 155 / 75 / 140). "Equally weak to ranged [heavy 75] and crush [75]" (Strat).
- HP (infobox, scaledhp): **160** Normal, **240** CM (x1.5). Party size and highest combat level: **OPEN-MY2** (no wiki formula).
  Stat regeneration **1 per 25 ticks** (Mod Ash, Discord 9 Jul 2026).
- **Count by raid scale** (Strat table; "raid scale", which includes the leader's manual scaling option, fixed to work for mystics on
  12 Dec 2023): scale 1-2 -> **3**, 3-5 -> **4**, 6-8 -> **5**, 9-11 -> **6**, 12-14 -> **7**, 15-17 -> **8**, 18-20 -> **9**, 21-23 -> **10**,
  24-26 -> **11**, 27+ -> **12**. The Strat prose says "at least three ... (dependent on party size)". The CM page names three
  mystics in its solo walkthrough ("the first mystic ... the other two").
- Animations: the cache stand / walk seqs are the shared skeleton ones, `SKELETON_UPDATE_READY` 5483 and `SKELETON_UPDATE_WALK` 5479
  (also `_ATTACK_WEAPON` 5485, `_DEFEND` 5489, `_DEATH` 5491 exist); the mystic's own attack animations and the magic spot anims
  are **not named** in the symbols (**OPEN-MY3**).
- Scenery: `RAIDS_SKELETALMYSTICS_SYMBOL` 29741 (the powered sigil that blocks the way forward), `RAIDS_MYSTICS_PORTAL_START` 49996,
  `RAIDS_MYSTICS_PORTAL_MIDDLE` 49997, `RAIDS_MYSTICS_PORTAL_END` 49998.
- Drops: Big bones, 5-10 each of golpar, buchu and noxifer seeds, long bone 1/400, curved bone 1/5012.5 (Page).

## Fight

- **MY1 Room:** a sacrificial chamber with the counted mystics; they also block the way "with a powered sigil" (Strat). They are not
  aggressive; the player attacks first. Aggro / lure rule and whether all mystics join in at once: **OPEN-MY4** (Strat: "have one
  mystic attack the team at a time to lower overall damage, as they are fairly accurate").
- **MY2 Style choice:** magic (Fire Blast / Vulnerability) or melee. "They take the player's overhead prayers and defensive bonuses
  into account" (Strat). Protect from Magic "reduces the mystics' accuracy and damage by roughly 50%" (Strat). A mystic that can
  reach a player praying against magic **melees** them; if it cannot reach, it keeps casting magic (Page); the wiki says this also
  depends on the mystic's and the player's positions. A mystic using magic non-stop will not melee "until prompted" again (Strat).
  The selection probability and range rule (attack range 10 for magic, melee reach) is **OPEN-MY5**.
- **MY3 Not safespottable:** "The mystics have an attack range of 10 and cannot be safespotted" (Page), but the Strat describes a
  corner trap that makes a mystic try to melee, "get stuck", and "drastically reduce[s] the chance of a magic attack" (conflict 1).
  The CM-Strat repeats the corner trap, one mystic at a time, first mystic lured, "the other two may need to be forced into melee".
- **MY4 Stats:** Salve (ei) helps with ranged and magic; ranged is recommended because of their melee and prayer detection; magic is
  feasible only with Tumeken's shadow (Strat). The salve bonuses are not multiplied by the staff's passive (Strat).
- **MY5 Death:** each mystic drops as in Data. When the **last** mystic is killed, three portals appear, "one each at the start and end,
  and a last one in the middle" for fast teleporting across the room (Strat). Portal use rules (who, cooldown, whether once only):
  **OPEN-MY6**. A message names the top damage dealer; seeds are free for all.

## Points (stated)

- Combat points come from damage dealt (Main); the formula, cap and the mini-boss "decay" multiplier are **OPEN-MY7**. Nothing
  mystic-specific is stated. Raid-wide rules: `../cox.md`.

## Per-mode differences

- **Normal:** HP 160, def 187.
- **Challenge Mode:** HP 240, att / str / def x1.5. The mystics are on the lower level (after the Guardians and Vasa, before the
  Muttadiles). CM-Strat: use Tumeken's shadow in solo / duo, otherwise ranged with a salve (ei); in trios and larger the twisted bow
  and salve (ei) outperform best-in-slot magic. The count follows the raid scale as in Normal.

## Tick timeline (stated parts)

- Attack speed 4. Regeneration 1 per 25 ticks. No further timings stated.

## Changes up to 2026-11-03 that matter

12 Dec 2023 "Skeletal Mystics now scale correctly when manually scaling upon raid creation" (the only Page entry); 28 Feb 2019 a piece
of wall sticking into one mystic room removed (Main). No entry after 12 Dec 2023.

## Open list

OPEN-MY1 the damaging Vulnerability effect; OPEN-MY2 HP and stat scaling by party size and highest combat level; OPEN-MY3 attack
animations and spot anims; OPEN-MY4 aggro and lure rule; OPEN-MY5 style selection rule and chance; OPEN-MY6 portal rules; OPEN-MY7
point values. **7 open items.** Working assumption: count from the Strat table by the raid scale at start.

## Conflicts between sources

1. Safespotting: Page "cannot be safespotted" vs Strat / CM-Strat corner-trap technique. Both kept: no pathing exploit from range,
   but corner trapping works.
Not a conflict: Strat prose "at least three" and the table (3 at scale 1-2, 12 at 27+) agree. The infobox poison / venom resistance 0 differs from the 100 of most Chambers monsters but is the only value given.
