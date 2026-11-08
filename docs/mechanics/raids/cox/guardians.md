# Guardians (CoX combat room: the stone statues)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Guardian (Chambers of Xeric)" (Page), "Chambers of Xeric" (Main),
"Chambers of Xeric/Strategies" (Strat), "Chambers of Xeric/Challenge Mode" (CM), "Stone guardian" (a different monster from
Dragon Slayer II, not used); cache 2757 (`cache-dump config npc <id>`; the `stats` array is `[1,1,1,1,1,1]` on every Chambers
NPC because the raid scales them at runtime, so numbers come from the wiki infobox and the cache `params` map: params 5 / 6 / 7
= stab / slash / crush defence bonus, 10 = melee strength bonus; they agree with the infobox for ids 7569 / 7570); generated
symbols (`Npcs`, `Seqs`, `Locs`). Raid-wide rules: `../cox.md` (not part of this set). Seconds are converted at 0.6 s per tick.
Tick timelines below list only what a source states; everything else is `OPEN-GU<n>`. The wiki page title is "Guardian
(Chambers of Xeric)"; the cache symbol is `RAIDS_STONEGUARDIANS_*`.

## Data

- NPC ids: **7569** `RAIDS_STONEGUARDIANS_LEFT` and **7570** `RAIDS_STONEGUARDIANS_RIGHT` (name `<col=00ffff>Guardian</col>`,
  op2 "Attack", size **2**, category 1028; both with the same params), **7571** `RAIDS_STONEGUARDIANS_LEFT_DEAD` and **7572**
  `RAIDS_STONEGUARDIANS_RIGHT_DEAD` (no ops, category 1029, stand seq `HUMAN_DEAD` 3280; the demolished statue, examine "A
  demolished statue"). The same ids serve Normal and Challenge Mode. Two statues stand in the room. Wiki infobox size is blank.
  **Conflict 1** (ids per mode).
- Attack: melee slash, **attack speed 4**, max hit **20** (CM **33**), aggressive, XP bonus 7.5 / 10. Living stand / walk seq in the
  cache is 7526 `HUMAN_STAFFREADY_STATUE` (the statue uses a human pose); attack and stomp animation ids are not named
  (**OPEN-GU1**).
- Stats (infobox): Normal att 140, str 140, def 100, magic 1, ranged 1; CM att 210, str 210, def 150. Strength bonus +20.
  Defence bonuses stab **80**, slash **180**, crush **-10**, magic 0, ranged 0 (cache params 5 / 6 / 7 = 80 / 180 / -10, param 10
  = 20). Poison resistance 0, venom resistance 0 (the wiki infobox says 0, unlike most Chambers monsters; thralls are ignored:
  `immunethrall = Yes`).
- HP (infobox): **250** Normal, **375** CM, scaledhp. The wiki has the only party scaling formula in the set (Page "fig.2"):
  `H = 151 x (1 + floor(T / 2)) + floor(avgMining) x T`, with T the team size and avgMining the party's average Mining level. A solo
  player at 99 Mining gives `151 + 99 = 250`, the infobox value (so the infobox is the solo maxed figure); 250 x 1.5 = 375 is the
  CM value (the CM x1.5 rule, not a stated guardian formula: **OPEN-GU2**). Whether T is the real party size or the scaled size
  and the rounding of the average (the formula floors it) are as stated; the scaling of attack and strength with party size is
  **OPEN-GU3** (the Page says only that "strengths and hit points ... are dependent on the party size").
- Stat regeneration **1 per 8 ticks** (Mod Ash, Discord 9 Jul 2026).
- Scenery: pickaxe spawn `RAIDS_STONEGUARDIANS_PICKAXE` 41754 (the Strat says a permanent iron pickaxe spawn is next to the guardians;
  16 Jun 2021 added a pickaxe spawn to all Guardian rooms). Seeds: 3-5 each of buchu, golpar and noxifer when a guardian is
  destroyed (Page drops).

## Fight

- **GU1 Pickaxe only:** the guardians "can only be harmed with pickaxes; all other sources of damage will not count" (Page); other
  damage is reduced to 0 but secondary effects (debuffs, healing) still apply (Strat). The dragon warhammer special applies its
  Defence drain **before** the damage roll is set to zero (Mod Ash, 13 Apr 2018). A pickaxe special attack is possible but "does not give any
  significant speed increase" (Strat).
- **GU2 Damage multiplier:** damage dealt by the pickaxe roll is multiplied by `D = (50 + M + P) / 150` where M is the player's Mining
  level and P the pickaxe's level requirement (Mod Ash, 15 Jun 2018; "Void would have been factored in before this
  calculation"). The formula is capped so that the **crystal pickaxe deals the same damage as the dragon pickaxe** (Mod Ash,
  29 Jul 2019). The cap value of P (the dragon pickaxe requirement) and whether boosted levels count are **OPEN-GU4**.
  Rounding of the multiplied damage is **OPEN-GU4**.
- **GU3 Weakness:** very resistant to stab (80) and slash (180); crush (-10) is the weakest, so a crush style is used (Strat).
- **GU4 Melee attack:** a "cleaving" slash every 4 ticks (Strat); Protect from Melee is optional but recommended (Strat); the
  reduction is **OPEN-GU5**. Targeting when both statues are in range: **OPEN-GU5**.
- **GU5 Stomp:** "occasionally" a guardian stomps the ground; "a rock falls from the ceiling", dealing "moderate to heavy" damage
  in a **3x3** area centred on the target's tile **at the moment the attack was performed** (Strat); it is avoided by moving two
  tiles away ("flinching"). Chance per attack, damage range and the delay before the rock lands are **OPEN-GU6**.
- **GU6 Blocking the passage:** a player who tries to enter the passage they guard is knocked back for moderate damage (Page);
  the shove awards some points (Main, below). Damage range, cooldown and the area are **OPEN-GU7**.
- **GU7 Death:** a destroyed statue becomes the demolished form (7571 / 7572); the room is passable once both are destroyed (Strat: "the
  statues must be destroyed"). The potion / seed pick-up and "most damage" message follow the other combat rooms (Strat).
  Respawn: none is stated (**OPEN-GU8**).

## Points (stated)

- Stepping between the statues so that they push the player away "deals damage and awards some points"; the cap "is currently
  believed to scale non-linearly with the number of players" (Mod Ash, 3 Jun 2024), "but the exact formula is not currently
  known" (Main). Points per push and the cap are **OPEN-GU9**.
- Damage dealt to them gives combat points like other NPCs, subject to the same open questions as the other rooms (**OPEN-GU9**).

## Per-mode differences

- **Normal:** HP by the formula (250 solo at 99 Mining), max hit 20.
- **Challenge Mode:** HP 375 (x1.5), att / str / def x1.5, max hit 33. CM-Strat: "Guardians are cleared as in normal mode."

## Tick timeline (stated parts)

- Attack every 4 ticks.
- Stomp: telegraphed (the target tile is fixed when the attack is made); the rock lands later (delay not stated).
- Regeneration of drained stats: 1 per 8 ticks.

## Changes up to 2026-11-03 that matter

3 Jun 2024 cap remark (Mod Ash, not a change); 29 Jul 2019 crystal pickaxe cap; 15 Jun 2018 damage modifier formula (Mod Ash); 13 Apr
2018 DWH special ordering; 16 Jun 2021 pickaxe spawn in all Guardian rooms. The Page has no changelog section; nothing is dated
after 29 Jul 2019 except the Mod Ash remarks.

## Open list

OPEN-GU1 attack and stomp animation / graphic ids; OPEN-GU2 CM HP formula (x1.5 assumed); OPEN-GU3 attack and strength scaling
with party size and highest combat level; OPEN-GU4 the cap value of P, boosted levels and damage rounding for the pickaxe
multiplier; OPEN-GU5 Protect from Melee reduction and targeting between the two statues; OPEN-GU6 stomp chance, damage and
landing delay; OPEN-GU7 knock-back damage, area and cooldown; OPEN-GU8 respawn; OPEN-GU9 points per push, the cap formula and
combat points. **9 open items.** Working assumptions: use the HP formula above with T = party size at raid start (the wiki's
"team size"); CM = x1.5.

## Conflicts between sources

1. Ids per mode: the infobox assigns 7569 / 7571 to Normal and 7570 / 7572 to Challenge Mode, but the cache names them left / right
   statue and alive / dead (identical params), so one pair serves both modes.
2. Resistances: the Guardian infobox lists poison / venom resistance 0 while most other CoX room monsters list 100 (the skeletal mystic also lists 0); the cache has no
   such field. The infobox is used.
Not a conflict: Strat "cleaving melee attack" and the Page "attack with Melee" agree (infobox style Slash).
