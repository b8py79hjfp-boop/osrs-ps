# Vasa Nistirio (CoX combat room: Vasa and the four glowing crystals)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Vasa Nistirio" (Page), "Glowing crystal" (Crystal), "Chambers of
Xeric" (Main), "Chambers of Xeric/Strategies" (Strat), "Chambers of Xeric/Challenge Mode" (CM); cache 2757 (`cache-dump config npc
<id>`; the `stats` array is `[1,1,1,1,1,1]` on every Chambers NPC because the raid scales them at runtime, so numbers come from
the wiki infobox and the cache `params` map: params 4 = ranged attack bonus, 5 / 6 / 7 = stab / slash / crush defence bonus, 8 =
magic defence bonus; they agree with the infobox for 7566 and 7568); generated symbols (`Npcs`, `Seqs`, `SpotAnims`, `Locs`,
`Objs`). Raid-wide rules: `../cox.md` (not part of this set). Seconds are converted at 0.6 s per tick. Tick timelines below list
only what a source states; everything else is `OPEN-VA<n>`. Both the Page's and Strat's Vasa mechanics text carry an
`{{Obsolete|need to be updated with the COX QOL updates}}` tag; the 29 Nov 2023 change list is used to correct them (conflict 3).

## Data

- NPC ids (one id set serves Normal and Challenge Mode): **7565** `RAIDS_VASANISTIRIO_DORMANT` (name `<col=00ffff>Rocks</col>`, size
  **5**, no ops; the pile of rocks), **7566** `RAIDS_VASANISTIRIO_WALKING` (op2 "Attack", size 5, param 510 "Vasa Nistirio"; the
  fighting form), **7567** `RAIDS_VASANISTIRIO_HEALING` (the form at a crystal, same params), **7568**
  `RAIDS_VASANISTIRIO_CRYSTAL` ("Glowing crystal", size **4**, op2 "Attack"). Category 1027 (Vasa), aggressive, poison and venom
  immune, not undead (no Salve, no Crumble Undead; Page).
- Attack: ranged and magic, **attack speed 3**, max hit "Varies". XP bonus 7.5 / 10.
- Stats (infobox, scaled for a maxed player): Normal att 1, str 1, def 175, magic 230, ranged 230; CM def 262, magic 345, ranged 345.
  Ranged attack bonus +100 (cache param 4 = 100). Defence bonuses stab **170**, slash **190**, crush **40**, magic **400**, light
  40, standard 40, heavy 30 (cache 5 / 6 / 7 / 8 = 170 / 190 / 40 / 400; the heavy value was lowered by 20 on 29 Nov 2023). Strat:
  "weakest to crush, ... only slightly more resistant to ranged".
- HP (infobox, scaledhp): **300** Normal, **450** CM. Party size and highest combat level: **OPEN-VA1** (no wiki formula). Stat
  regeneration **1 per 10 ticks** (Mod Ash, Discord 9 Jul 2026).
- Glowing crystal (Crystal page): HP **120** (one value for both modes), att 1, str 1, def 100, magic 100 (CM 150), ranged 1; defence
  stab **-5**, slash **180**, crush **180**, magic 0 (cache params 5 / 6 / 7 = -5 / 180 / 180); max hit 0, attack speed 4 (only visible
  with Monster Examine; it never attacks). XP bonus 2.5. Immune to ranged (including the ranged thrall), takes **1/3** damage from
  magic (Strat "66% less"), weak to stab. Stat regeneration **1 per 9 ticks**. Four crystals, one in each corner.
- Animations (cache seq names): `VASA_DORMANT` 7407, `VASA_SPAWN` 7408, `VASA_STUN_SPAWN` 7409, `VASA_ATTACK` 7410, `VASA_WALK` 7411,
  `VASA_HEAL_SPAWN` 7412, `VASA_HEAL_IDLE` 7413, `VASA_HEALED` 7414, `VASA_DEATH` 7415, `VASA_IDLE` 7416 (7566 stand / walk =
  7416 / 7411, 7567 stand = 7413). Spot anims / projectiles `RAIDS_VASANISTIRIO_MAGIC_TRAVEL` 1327, `_MAGIC_IMPACT` 1328,
  `_RANGE_TRAVEL` 1329, `_RANGE_IMPACT` 1330. Which animation is the teleport charge and which is the boulder throw is not stated
  (`VASA_STUN_SPAWN` and `VASA_ATTACK` are guesses; **OPEN-VA2**).
- Scenery: `RAIDS_VASANISTIRIO_FIRE` 30019 (the purple magical fire at the entrance; food can be cooked on it once he is awake),
  `RAIDS_VASANISTIRIO_CRYSTAL_ON` 29774 / `_OFF` 29775 (crystal states), book `RAIDS_VASANISTIRIO_BOOK` 30020 (visible 12310),
  obj `RAIDS_VASANISTIRIO_BOOK` 20888.

## Fight

- **VA1 Start:** Vasa is a pile of rocks (7565). "Upon approaching the pile, he will animate himself and start the fight" (Page): he
  raises a purple fire at the entrance (it damages players who pass through) and opens with the teleport attack (VA2). Distance
  that triggers it, fire damage and the fire's removal at the end: **OPEN-VA3**.
- **VA2 Teleport attack:** he teleports every player in the arena, including players outside who stand next to the fire. Teleported
  players land either next to him or at the arena walls; a lone player always lands next to him; in groups he "tries to divide them
  into equal amounts" (Page, "four players ... two ... next to him and the other two by the edge"; the rule for odd numbers and
  who goes where is **OPEN-VA4**). He cannot be attacked while charging; teleported players are **stunned and cannot use prayers**
  until it ends. Projectiles then fall next to him and explode. Damage: solo = the player's **current HP minus 5** (Page example 94
  -> 89; Strat "accumulated health of teleported players - 5 / number of players next to him"); in a group it is the **accumulated
  HP of the players next to him** (Page) and players teleported away can run next to Vasa to split it; those with Protect from
  Magic take nothing (Page). Vengeance recoil on the blast works (7 Dec 2017) but gives no points (Strat). The exact group
  formula (is the 5 subtracted once or per player, and how the share is divided) is **OPEN-VA4** (conflict 1).
- **VA3 Boulders:** between teleports he throws boulders as a ranged attack at every player within **8** tiles (Page) / **9** tiles
  (Strat) of him, but not underneath him; they land on the player's tile at the moment of the throw, have a **3x3** area, and "deal
  heavy damage" (Strat). Protect from Missiles reduces accuracy and damage. Players underneath him take a rapid stomp of **up to 8
  per tick** (Page). Radius, damage and the hit timing: **OPEN-VA5**.
- **VA4 Crystal cycle:** after the teleport he activates one of the **four** corner crystals (a visibly brightening crystal) and walks
  to it. On arrival he becomes **invulnerable** and stops attacking while he "siphons" it. The first arrival starts a **40 second,
  66- or 67-tick** timer that only counts down while a crystal is active (pauses when none is active); at zero he leaves the
  crystal, returns to the middle and teleports again (Page). The crystals can be attacked (stab best; melee only, ranged immune,
  magic 1/3).
  - Destroying / disabling the crystal before the timer ends **negates the healing**; if he finishes siphoning he is healed in full
    for the amount siphoned (Page, 29 Nov 2023: "he will only heal if he fully siphons from the crystal, rather than healing over
    time"). The older 2020 quote "every 2 ticks, it heals 1% HP and (10% + 1) Defence" (Mod Ash, 19 May 2020) is **superseded** for
    the HP part (conflict 3); whether the Defence restoration still applies each 2 ticks or only on a full siphon is **OPEN-VA6**.
    Strat: "He will also restore (10% + 1) Defence off the crystal."
  - If the team disables the crystal in time Vasa still returns to the middle but immediately picks another crystal; "the speed in
    which he drains the crystal will increase for each one disabled" (Strat; the numbers and the 3rd-crystal remark are
    **OPEN-VA6**). If he finishes draining, the drain timer resets. The next crystal is always a different one than the last, unless
    the teleport attack was just used (Crystal page).
  - A player under him while he siphons takes a rapid stomp (Strat). Healing shows green hitsplats, a completed heal purple (Strat).
- **VA5 Death:** drops (Page, 100%): Nistirio's manifesto (unless read, 26 Mar 2025; `RAIDS_VASANISTIRIO_BOOK` 20888), 1 Overload (+)(4)
  (since 29 Nov 2023), 5 endarkened juice, 2 Xeric's aid (+)(4), 2 Twisted (+)(4) (`RAIDS_VIAL_*_STRONG_4` 20984, 20936; juice
  `RAIDS_ENDARKENED_JUICE` 20911). A message names the top damage dealer; potions are free for all (Strat). Strat lists "two
  Xeric's aid (+), an overload (+) and twisted (+)" (conflict 2).

## Points (stated)

- Combat points come from damage dealt (Main); the formula, cap and the mini-boss "decay" multiplier are **OPEN-VA7**. Vengeance recoil
  on the blast "will not yield any points" (Strat). Potions from the drop count toward the potion points cap (Main).
- Raid-wide death and reward rules: `../cox.md`.

## Per-mode differences

- **Normal:** HP 300, def 175, magic and ranged 230.
- **Challenge Mode:** HP 450, def 262, magic and ranged 345 (x1.5). The Crystal page's "challenge mode modifier removed" (16 Jun 2021) is
  the only CM note (conflict 4). CM-Strat: soloing, Vengeance after the guardians at 118 HP; "the player drops to around 100 to avoid
  getting killed after the teleport explosion"; "standing at the southern edge of Vasa's chamber will trigger the muttadiles to spawn".

## Tick timeline (stated parts)

- Attack speed 3. Teleport charge duration, boulder cooldown, walk time to crystal: not stated.
- Crystal timer: 40 s = 66 or 67 ticks, counting only while a crystal is active. Old siphon heal: 1% HP every 2 ticks (2020).
- Regeneration: Vasa 1 per 10 ticks, crystal 1 per 9.

## Changes up to 2026-11-03 that matter

29 Nov 2023 Vasa heals only after a full siphon, ranged defence bonus -20, drops an overload; 16 Jun 2021 CM crystals' modifier
removed; 10 Jan 2019 examine text; 7 Dec 2017 recoil works again. No Vasa entry after 29 Nov 2023.

## Open list

OPEN-VA1 HP and stat scaling by party size and highest combat level; OPEN-VA2 teleport and boulder animation ids; OPEN-VA3 trigger
distance, fire damage and fire removal; OPEN-VA4 the group blast formula and the teleport split rule; OPEN-VA5 boulder radius,
damage and timing, stomp tile rule; OPEN-VA6 Defence restoration timing and the drain-speed increase per disabled crystal;
OPEN-VA7 point values. **7 open items.** Working assumption: use the 29 Nov 2023 reading (full siphon only) and the solo blast `HP - 5`.

## Conflicts between sources

1. Blast formula: Page "accumulated hitpoints of all players teleported next to him" with a `current HP - 5` solo example vs Strat
   "(accumulated health of teleported players - 5 / number of players next to him)" (precedence unclear).
2. Drop: Page "2 Twisted (+)" vs Strat "twisted (+)" (one).
3. Healing: Page text "healing 1% every 2 ticks" (Mod Ash 2020) vs 29 Nov 2023 "only heal if he fully siphons". The newer change is used;
   both texts are in the Obsolete-tagged Mechanics section.
4. Crystal CM modifier: infobox magic defence 100 vs 150 (CM) against the 16 Jun 2021 "modifier removed" change; the infobox is used.
5. Boulder radius: Page "within 8 tiles" vs Strat "within 9 tiles".
