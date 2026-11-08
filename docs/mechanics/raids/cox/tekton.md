# Tekton (CoX combat room)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Tekton" (Page), "Chambers of Xeric" (Main), "Chambers of
Xeric/Strategies" (Strat), "Chambers of Xeric/Challenge Mode" (CM); cache 2757 (`cache-dump config npc <id>`; the `stats`
array is `[1,1,1,1,1,1]` on every Chambers NPC because the raid scales them at runtime, so numbers come from the wiki
infobox and the cache `params` map: params 5 / 6 / 7 / 8 = stab / slash / crush / magic defence bonus, 10 = melee strength
bonus; both agree with the infobox on every Tekton form); generated symbols (`Npcs`, `Seqs`, `Locs`, `Objs`).
Raid-wide rules (party size, scaling, layouts, point totals, reward roll, death): the CoX raid-wide doc `../cox.md` (not
part of this set). Seconds are converted at 0.6 s per tick. Tick timelines below list only what a source states; everything
else is `OPEN-TK<n>`.

## Data

- NPC ids (cache names confirm the wiki, **the same ids serve Normal and Challenge Mode**): **7540** `RAIDS_TEKTON_WAITING`
  (no ops; dormant at the anvil), **7541** `RAIDS_TEKTON_WALKING_STANDARD` (op2 "Attack"), **7542**
  `RAIDS_TEKTON_FIGHTING_STANDARD`, **7543** `RAIDS_TEKTON_WALKING_ENRAGED` (name "Tekton (enraged)"), **7544**
  `RAIDS_TEKTON_FIGHTING_ENRAGED`, **7545** `RAIDS_TEKTON_HAMMERING` (op1 "Attack"; the form at the anvil). Size **4**,
  category 1020, no combat level (0), aggressive, poison and venom immune (resistance 100). **Conflict 1** (ids per mode).
- Attack: melee, stab / slash / crush, **attack speed 3**. Attack bonus +64. Max hit (infobox, scaled for a maxed player):
  **52** standard, **59** enraged; Challenge Mode **78** / **87**.
- Stats (infobox, scaled for a maxed player): Normal att 390, str 390, def 205, magic 205, ranged 1; CM att 585, str 585,
  def 246, magic 246. Strength bonus 20 (standard) / 30 (enraged), no other offensive bonuses. Defence bonuses: standard
  stab 155, slash 165, crush 105; enraged 280 / 290 / 180; magic and ranged 0. Cache params 5 / 6 / 7 = 155 / 165 / 105
  (7541, 7542, 7545) and 280 / 290 / 180 (7543, 7544), param 10 = 20 / 30.
- HP (infobox, "scaledhp"): **300** Normal, **450** Challenge Mode. The infobox footnote says the numbers are "scaled for a player
  with maxed combat stats"; a hidden comment on the Page says Tekton heals 4 in "normal max solo (300 hp)" and 5 in
  "challenge max solo (450 hp)", so the infobox figures are read as the solo maxed values. How HP follows party size and
  the highest combat level is **OPEN-TK1** (the wiki has no formula for Tekton; see Scaling).
- Resistances: immune to ranged; magic damage reduced by **80%** (damage / 5; Mod Ash, 7 Sep 2023, answering that the
  Tekton rule is the same way as "divided by 5"); immune to binding. Elemental weakness **water 20%** (25 Jun 2025). XP bonus 35 / 52.5 (Normal
  standard / enraged), 50 / 77.5 (CM).
- Stat regeneration: **1 per 15 ticks** (Mod Ash, Discord 9 Jul 2026), default is 1 per 100.
- Animations (cache seq names): stand `TEKTON_SPAWN` 7473 (waiting / hammering), `TEKTON_IDLE` 7476, `TEKTON_READY` 7480,
  `TEKTON_READY_DEFEND` 7481, walk `TEKTON_WALK` 7477; attacks `TEKTON_ATTACK_STAB` 7482, `TEKTON_SLASH` 7483,
  `TEKTON_HAMMER_CRUSH` 7484; transitions `TEKTON_IDLE_TRANSITION_TO_ANVIL` 7475, `TEKTON_AVIL_TRANSITION_TO_IDLE` 7474,
  `TEKTON_IDLE_TRANSITION_TO_READY` 7478, `TEKTON_READY_TRANSITION_TO_IDLE` 7479; enraged set `TEKTON_IDLE_ENRAGED` 7485,
  `TEKTON_WALK_ENRAGED` 7486, `TEKTON_READY_ENRAGED` 7490, `TEKTON_READY_DEFEND_ENRAGED` 7491, `TEKTON_HAMMER_CRUSH_ENRAGED`
  7492, `TEKTON_ATTACK_STAB_ENRAGED` 7493, `TEKTON_SLASH_ENRAGED` 7494, `TEKTON_AVIL_TRANSITION_TO_IDLE_ENRAGED` 7487,
  `TEKTON_IDLE_TRANSITION_TO_READY_ENRAGED` 7488, `TEKTON_READY_TRANSITION_TO_IDLE_ENRAGED` 7489; death `TEKTON_DEATH` 7495.
  Which attack animation goes with which damage type is not stated (**OPEN-TK9**). Pet walk seqs `TEKTON_PET_WALK` 7983,
  `TEKTON_ENRAGED_PET_WALK` 8637 are not part of the fight.
- Scenery: `RAIDS_TEKTON_ANVIL` 29867, `RAIDS_TEKTON_FIRE` 30021 (the fire that blocks the entrance once he is awake; the
  Page trivia says food can be cooked on it), `RAIDS_TEKTON_WALKBLOCKER` 30023, `RAIDS_TEKTON_BOOK` 30022 (visible
  variant 13482). Book obj `RAIDS_TEKTON_BOOK` 20890 ("Tekton's journal"). **No spot anim symbol for the sparks was found**
  (grep of `SpotAnims` for TEKTON / SPARK / ANVIL gives only the Chasm of Fire anvil, which is a different place):
  **OPEN-TK10**.

## Scaling

- The infobox numbers are for a player with maxed combat stats. NPC stats are scaled to the player with the **highest**
  combat stats, not the average (7 Mar 2019). Party size at raid start sets the scale and it does not drop when players
  leave (Strat). Captain Rimor can switch level scaling off if nobody is level 115 or higher (Strat, 16 Jun 2021), and the
  leader can raise the scale without more players (29 Nov 2023).
- Challenge Mode: "all of the enemies' combat stats (including health) are increased by 50%" (CM). Tekton follows it
  (HP 300 -> 450, att / str 390 -> 585, max hits x1.5) except **defence, which is x1.2 (205 -> 246)**, as the 24 May 2018
  change says: "Tekton's defence will scale less harshly for challenge raids consisting of smaller numbers of players, and
  even less for solo players". The numbers of that less-harsh scale are **OPEN-TK1**.

## Fight

- **TK1 Start:** Tekton works at his anvil (7540 / 7545). "As soon as someone gets near him, he will notice them and begin
  the fight" (Page). Distance and the moment the entrance fire appears: **OPEN-TK2**.
- **TK2 Melee:** he walks to a player until contact, stops, becomes a solid NPC (nobody can walk under him) and attacks
  with melee every **3 ticks**, hitting "both the area in front of him and to his right simultaneously" (Page; the
  footprint is not stated, **OPEN-TK3**). Protection from melee works ("if not protected against", Strat; the reduction
  is **OPEN-TK4**). With several players around him he "will randomly change direction" (Page; the rule is **OPEN-TK3**).
  Flinching (leaving range before the attack registers) and running anticlockwise both avoid every hit (Strat), which
  means the damage is applied after a delay or against the position at the moment of resolution (**OPEN-TK3**).
- **TK3 Return to the anvil:** after "10-14 attacks" (Strat) or if he scans no player in melee range at his next attack, he
  stops and walks back to the anvil (the Page says "after a short duration of fighting"). He can be attacked on the way. A
  player standing under him as he returns takes "rapid stomp damage" (amount and tick rule **OPEN-TK5**). Since 31 Jan 2019
  he is "less sensitive to return to the anvil abruptly without trying to hit anyone" (the sensitivity is **OPEN-TK6**).
- **TK4 Repair and sparks:** at the anvil he repairs "roughly 1.11 to 1.33%" of max HP "every few ticks" (Page, hidden comment
  4 HP of 300 and 5 HP of 450; Strat "1.33% of his maximum health every few ticks"; the interval is **OPEN-TK6**). While
  he repairs, "two sparks" are sent at **each player** in the room; each has a **3x3** area and deals **10-20** damage
  (Strat; the Page says "moderate"), also to a player who was not the target; they "can be avoided by simply running two
  spaces away" (Strat). Up to **five sets** of sparks (Strat) / about 10 seconds (Page "around ~10 or so seconds", about 17
  ticks) before he stops; the delay between spark launch and landing, and the set interval, are **OPEN-TK7**.
- **TK5 Enrage:** when repairing ends he enters the enraged form (7543 / 7544), "increasing his melee defensive bonuses and
  max hit" (Strat): max hit 59 (CM 87), defence bonuses 280 / 290 / 180, strength bonus 30. He walks to a player and fights as
  in TK2. It lasts "around ~5 or so attacks he makes afterwards" (Strat) / "only last briefly" (Page); then he returns to
  the standard form, "which in turn drops all bonuses from the enraged form". Exact attack count: **OPEN-TK8**. Whether
  enraged Tekton also walks back to the anvil on the same 10-14 attack rule is not stated (**OPEN-TK8**).
- **TK6 Special attacks:** the **first** elder maul or dragon warhammer special that makes contact "will always land as a
  damaging successful hit"; if several land on the same tick only one gets it (Page); simultaneous specials are processed in
  **PID order** (Strat). After that they act normally; a warhammer or maul special that fails still drains **5%** of his
  (current) Defence (Strat). The Bandos godsword special drains **at least 10** Defence levels even on a miss (13 Apr
  2017). He does **not** reset his Defence when healing at the anvil (29 Nov 2023), so drains persist until he regenerates
  (TK7).
- **TK7 Regeneration:** drained stats come back at 1 per 15 ticks (Data).
- **TK8 Weaknesses and immunities:** see Data (ranged immune, magic 80% reduced, water weakness 20%, binding immune). How
  the water weakness combines with the 80% reduction is **OPEN-TK11** (both are stated for spells; no order is given).
- **TK9 Death:** drops (Page, "100%"): Tekton's journal (unless the player has read it, 26 Mar 2025), 5 stinkhorn mushrooms,
  2 Overload (+)(4), 1 Prayer enhance (+)(4), 1 Revitalisation (+)(4) (symbols `RAIDS_VIAL_OVERLOAD_STRONG_4` 20996,
  `RAIDS_VIAL_PRAYER_STRONG_4` 20972, `RAIDS_VIAL_REVITALISATION_STRONG_4` 20960, `RAIDS_STINKHORN_MUSHROOM` 20910). Onyx
  (obj `ONYX` 6573, rarity table below). The potions are shown to everyone and can be picked up by any player including ironmen;
  the onyx is the one exception (Strat). A message names the player who dealt the most damage. The Page's drop table does not differ by mode (conflict 3).
- **TK10 Onyx rarity:** **1/450** in a solo Normal raid, "increasing in bigger parties" (Page: "As Tekton gets tougher based on
  the party size, the chance is therefore improved"); in Challenge Mode the rate is **lower for parties of 3 or fewer**:
  Mod Ash 16 Jun 2026 says that is a factor of 3 ("1/70 would become 1/210"). The infobox row shows 1/450 and 1/210. The
  per-size table is **OPEN-TK12**.

## Points (stated)

- Damage dealt in the chambers gives points based on damage (Main); how many per HP, the per-player cap, and the "decay"
  multiplier that applies when fighting mini-bosses (Strat) are **OPEN-TK13**. "Each point of HP damage dealt now gives an
  additional 2 points" is a 31 Jan 2019 change listed in the same bullet list as Tekton's defence cut; the wiki does not say
  which rooms it affects (**OPEN-TK13**).
- Potions from his drop award points to the player who made or received them when drunk, capped at 5 sips per raid player plus
  5 at a reduced amount (Main), with more if someone else drinks them. Death costs 40% of personal points (Main); raid-wide
  rules are in `../cox.md`.

## Per-mode differences

- **Normal:** as above, HP 300, max hit 52 / 59, defence 205.
- **Challenge Mode:** HP 450, att / str 585, defence 246 (x1.2), max hit 78 / 87 (x1.5). Tekton is the **first** room of the
  Challenge Mode layout (top level, with crabs, ice demon, shamans). CM-Strat: "two unprotected hits will likely result in
  death"; the players use Vengeance to bounce a tanked hit. Onyx rate worse for 3 or fewer (TK10).

## Tick timeline (stated parts)

- Attack every 3 ticks (Strat: "attacks on a three-tick cycle").
- Fighting: 10-14 attacks (Strat) -> walks to anvil -> repairs about 10 s (about 17 ticks, Page) with up to five spark sets
  (Strat) -> enraged for about 5 attacks (Strat) -> standard. The walk time, spark landing delay and the spark interval are
  not stated.
- Stat regeneration 1 per 15 ticks.

## Changes up to 2026-11-03 that matter

25 Jun 2025 water weakness 20% (Page); 29 Nov 2023 first DWH spec guaranteed (the same note covers the elder maul on the
Page text), no Defence reset on healing; 7 Sep 2023 magic clarification (Mod Ash); 31 Jan 2019 defence -25%, less sensitive
return to the anvil; 24 May 2018 CM defence scaling gentler for small teams; 13 Apr 2017 Bandos godsword minimum drain; 6 Apr
2017 DWH 5% on miss; 6 Jan 2017 hotfix "considerably buffed" ("massacring" capability) and offensive stats raised (Main). No Tekton entry after 25 Jun 2025.

## Open list

OPEN-TK1 how HP, attack, strength and defence follow party size and the highest combat level (wiki has no Tekton formula; the
24 May 2018 "less harsh" defence scale numbers); OPEN-TK2 notice range and entrance-fire timing; OPEN-TK3 hit footprint
(front plus right), the random direction rule and the damage timing that allows flinching; OPEN-TK4 Protect from Melee
reduction; OPEN-TK5 stomp damage and rule when a player stands under him; OPEN-TK6 repair interval, the "sensitivity" rule
for returning to the anvil; OPEN-TK7 spark landing delay, spark set interval, the five-set versus 10 s statement; OPEN-TK8
enraged duration in attacks and whether enraged Tekton also returns to the anvil; OPEN-TK9 attack animation per damage type;
OPEN-TK10 spark graphic and projectile ids (no symbol found); OPEN-TK11 water weakness versus the 80% magic reduction order;
OPEN-TK12 onyx rarity by party size and mode; OPEN-TK13 combat point formula, per-player cap and the mini-boss decay.
**13 open items.** Working assumptions (behind named config flags): HP and stats per the infobox for a maxed solo player; drained
Defence recovers only by the 15-tick rule.

## Conflicts between sources

1. NPC ids per mode: the Page lists 7540-7542 for Normal, 7543 enraged, 7545 for Normal CM and 7544 for enraged CM; the cache
   names say 7545 is the anvil (hammering) form and 7543 / 7544 are walking / fighting enraged, so the same ids serve both modes.
2. Enraged length: "around ~5 or so attacks" (Strat) vs "only last briefly" (Page): no number in the Page.
3. Drop: Page "2 Overload (+)(4)" (100%) vs CM text "pick up the overload(s) (one if solo)": the CM line is advice for
   ironmen, the Page drop table is used.
4. Repair length: "up to five sets of sparks" (Strat) vs "around ~10 or so seconds" (Page); both kept (TK4).
5. Dragon warhammer wording: Page says "elder maul or dragon warhammer" first special guaranteed; the 29 Nov 2023 change
   lists only the warhammer. The Page and Strat are used (maul included).
6. Magic: "takes 80% reduced Magic damage" (Page) vs "takes ... 20% from magic" (Strat): the same fact.
