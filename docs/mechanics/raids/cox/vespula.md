# Vespula (CoX combat room: Vespula, abyssal portal, lux grubs, vespine soldiers)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Vespula" (Page), "Abyssal portal" (Portal), "Vespine soldier" (Soldier),
"Lux grub" (Grub), "Chambers of Xeric" (Main), "Chambers of Xeric/Strategies" (Strat), "Chambers of Xeric/Challenge Mode" (CM);
cache 2757 (`cache-dump config npc <id>`; the `stats` array is `[1,1,1,1,1,1]` on every Chambers NPC because the raid scales
them at runtime, except the lux grubs whose HP slot is 125; numbers come from the wiki infobox and the cache `params` map:
params 8 = magic defence bonus, 10 = melee strength bonus, 12 = ranged strength bonus); generated symbols (`Npcs`, `Seqs`,
`SpotAnims`, `Locs`, `Objs`). Raid-wide rules: `../cox.md` (not part of this set). Seconds are converted at 0.6 s per tick.
Tick timelines below list only what a source states; everything else is `OPEN-VE<n>`.

## Data

- NPC ids (the infobox gives one id list for Normal and Challenge Mode; the cache has no CM ids):
  - Vespula **7530** `RAIDS_VESPULA_FLYING` (op2 "Attack"), **7531** `RAIDS_VESPULA_ENRAGED`, **7532** `RAIDS_VESPULA_WALKING`
    (grounded). Size **5**, category 1016, no combat level, attributes xerician and flying, **not aggressive** (retaliates), poison
    and venom immune, attack speed **3**.
  - Abyssal portal **7533** `RAIDS_VESPULA_PORTAL` (name `<col=00ffff>Abyssal portal</col>`, size **4**, op2 "Attack", param 510
    "Abyssal Portal"); also a scenery loc `RAIDS_VESPULA_PORTAL` 30072 and `RAIDS_VESPULA_PORTAL_CLOSED` 30073. Attack speed
    **2**.
  - Lux grubs **7534** `_HEALTHY`, **7535** `_SICKLY` (op1 "Feed"), **7536** `_INFECTED` (op1 "Feed"), **7537** `_DEAD`; size 3,
    stats slot 125, categories 1017 / 1018.
  - Vespine soldier **7538** `RAIDS_VESPULA_VESPINE_FLYING`, **7539** `RAIDS_VESPULA_VESPINE_WALKING`; size 3; op2 "Attack";
    attack speed **4**.
- Vespula attacks (infobox): ranged max **14**, stomp **8**, sting **20** (Normal; the CM max hit field is empty, **OPEN-VE1**).
  Poison starts at **20** (poisonous "Yes (20)"). Styles ranged and typeless.
- Stats (infobox, scaled for a maxed player): Vespula Normal att 150, str 150, def 88, magic 88, ranged 150; CM 225 / 225 / 132 /
  132 / 225. Strength bonus -8, ranged strength -8, other offensive bonuses 0. Defence bonuses stab / slash / crush 0, magic **70**,
  light 20, standard 60, heavy 60. Elemental weakness **fire 50%** (29 May 2024).
- Portal: att 1, str 1, def 176 (CM 264), magic 176 (CM 264), ranged 1; defence magic 60, light 140, standard 140, heavy 110 (lowered
  from 140, 29 May 2024); fire weakness 50% (29 May 2024); HP **250** Normal, **370** CM; max hit 0, no combat attack
  (it drains prayer, VE6).
- Soldier: att 150 (CM 225), str 150 (CM 225), def 40 (60), magic 40 (60), ranged 1; strength bonus -8, ranged strength -8; defence
  bonus magic 30 (cache param 8 = 30), others 0; HP **100** Normal, **150** CM; max hit **14** (CM **20**); style crush; poison
  starts at **15**.
- HP (infobox, "scaledhp"): Vespula **200** Normal, **288** CM (x1.44, not 1.5; **OPEN-VE2**). Portal 250 -> 370 (x1.48). The party size
  and highest combat level rule is **OPEN-VE3** (no wiki formula).
- Lux grub HP **125** (cache stat slot; 29 Nov 2023 raised it from 100); a medivaemia blossom heals a grub **30** (Strat).
- Stat regeneration (Mod Ash, Discord 9 Jul 2026): Vespula **1 per 10 ticks**, portal **1 per 45**, lux grubs **never** (10,000),
  vespine soldier flying **1 per 16**, walking **never**.
- Cache magic defence by form: 7530 flying 70 (param 8), 7531 enraged **190**, 7532 walking **60**, portal 7533 60, soldier 7538 /
  7539 30. The wiki prints 70 for all Vespula forms (**Conflict 3**).
- Animations (cache seq names): `VESPULA_IDLE` 7449, `VESPULA_WALK` 7448, `VESPULA_ATTACK_RANGED` 7450, `VESPULA_DEFEND` 7451,
  `VESPULA_TAKEOFF` 7452, `VESPULA_FLYING` 7453, `VESPULA_ATTACK_MELEE_FLYING` 7454, `VESPULA_ATTACK_RANGED_FLYING` 7455,
  `VESPULA_DEFEND_FLYING` 7456, `VESPULA_LANDING` 7457, `VESPULA_DEATH_WALKING` 7458, `VESPULA_DEATH_FLYING` 7459; grubs
  `LUXGRUB_IDLE_HEALTHY` 7463, `_SICKLY` 7464, `_INFECTED` 7465, `LUXGRUB_DEATH` 7466, `LUXGRUB_VESPINE_HATCH_SPOTANIM` 7467,
  `LUXGRUB_DEAD` 7468; portal `RAIDS_VESPULAR_PORTAL_ACTIVE` 7499, `_CLOSING` 7500, `_CLOSED` 7501. Spot anims
  `RAIDS_VESPULA_POISON` 1364, `RAIDS_VESPULA_VESPINE_HATCH` 1365, `RAIDS_VESPULA_PORTAL_ATTACK` 1366 (the green crescent),
  `RAIDS_VESPULA_VESPINE_HEALPORTAL` 1367, `RAIDS_VESPULA_VESPINE_EXPLODE` 1368. Scenery: `RAIDS_VESPULA_TENDRILS_ENTRANCE`
  30024, herbs `RAIDS_VESPULA_HERB` 30068 / `_EMPTY` 30069 (the medivaemia plants), `RAIDS_VESPULA_BOIL_BLOCKING` 30070 /
  `_BURST` 30071 (use not stated: **OPEN-VE4**), book loc `RAIDS_VESPULA_BOOK` 30025, book obj `RAIDS_VESPULA_BOOK` 20893.

## Fight

- **VE1 Start:** Vespula, four lux grubs and the abyssal portal are present. Vespula "will no longer interact with her grubs until
  the party has entered the room" (31 Jan 2019). She is not aggressive but retaliates when attacked, with rapid ranged attacks
  (Page). Tendrils appear at the entrance when the fight starts and damage players who leave through them (Strat). Damage and
  trigger: **OPEN-VE5**.
- **VE2 Flying state:** while flying she can only be harmed by magic, ranged or halberds (Strat; halberds since 6 Aug 2025). Attacks
  poison from 20 (Page). She attacks every 3 ticks.
- **VE3 Stinging the grubs:** while flying she "periodically" stings a grub; it then shows a meter above its head that slowly
  depletes. If it reaches zero the grub bursts and a **vespine soldier** appears; the soldier heals Vespula and the portal while alive
  (Page; Soldier). Blossoms restore the meter (Grub) / HP by 30 (Strat). Sting interval, depletion rate and heal amounts are **OPEN-VE6**.
- **VE4 Vespine soldier:** flying form (7538) attacks with a crush attack, max 14, poison from 15, speed 4. When its HP gets low it
  drops to the ground (7539), becomes immune to ranged and must be killed with melee; if not killed it **explodes after 20
  seconds** (about 33 ticks) for moderate damage to anyone near (Soldier, Strat). A new grub appears "soon after" the soldier dies
  (rate **OPEN-VE6**). The HP threshold for dropping: **OPEN-VE7**.
- **VE5 Landing:** on reaching **20%** of her HP (Page; the Portal page says 23%, conflict 1) she "has a chance to land each time
  she is attacked", guaranteed if she would reach 1 HP first. She stays on the ground for about **20 seconds** (about 33 ticks, Page
  "~20 seconds") while the portal can be safely attacked; destroying it ends the encounter. If the portal survives, she takes off
  again and restores her health (amount **OPEN-VE7**). Melee can be used on the ground.
- **VE6 Portal:** the portal is deep in the room, **7 tiles** from the accessible area (Portal page; Strat: needs a weapon range of 7
  or more, 8 recommended). When attacked it drains the prayer of every player in range (7 tiles) by **3 every 2 ticks** (a green
  crescent projectile); at 0 prayer it deals **3 damage** instead; the Redemption method does not take it. Portal regeneration is
  1 per 45 ticks (lowered to a third of the old rate, 31 Jan 2019). Fire weakness 50%.
- **VE7 Enrage:** attacking the portal while she is **flying** makes her enraged (7531, "eyes turn red"): she flies "back and
  forth in the area in front of the portal", stinging anyone in melee range for **5-20 typeless** damage; anyone **standing
  underneath** takes a stomp of **2-8 per tick**. She stays enraged until the portal is no longer attacked, then resumes normal
  behaviour (Page). Strat describes it as "head straight to the portal area, jabbing wildly". The Redemption method takes **6-24 per
  attack** (Strat). Target choice, hit footprint and speed are **OPEN-VE8**.
- **VE8 Death of the room:** destroying the portal also removes Vespula and her offspring (Portal). Drops (Page, 100%): Transdimensional
  notes (unless read, 26 Mar 2025), 2 Xeric's aid (+)(4), 1 Revitalisation (+)(4), 1 Prayer enhance (+)(4), 1 Overload (+)(4)
  (guaranteed since 29 Nov 2023). Potions from this room "will not contribute points when consumed" (Strat). The portal also
  spawns some potions on the ground when destroyed (7 Apr 2017). A message names the top damage dealer; potions are free for all
  (including ironmen).

## Points (stated)

- The portal "rewards more points per damage dealt" (31 Jan 2019, and 7 Apr 2017 "now gives more reward points per unit damage"); the
  numbers are **OPEN-VE9**. Damage to a **recovered** abyssal portal gives no points (Strat). "Each point of HP damage dealt now
  gives an additional 2 points" (31 Jan 2019) is listed without naming a room (**OPEN-VE9**). Damage elsewhere in the room is
  subject to the "mini-boss decay multiplier" (Strat).
- Raid-wide death and reward rules: `../cox.md`.

## Per-mode differences

- **Normal:** HP 200 / portal 250 / soldier 100; max hits 14 / 8 / 20.
- **Challenge Mode:** HP 288 / 370 / 150; att, str x1.5, def x1.5; soldier max hit 20. Vespula is in the middle level; the CM-Strat
  recommends the Redemption method. The CM Vespula max hit is not stated.

## Tick timeline (stated parts)

- Vespula attack speed 3, soldier 4, portal 2 (prayer drain 3 per 2 ticks).
- Landing: about 20 s on the ground (about 33 ticks), grounded soldier explodes after 20 s (about 33 ticks).
- Regeneration: 10 / 45 / never / 16 / never ticks as above.

## Changes up to 2026-11-03 that matter

6 Aug 2025 halberds can hit flying Vespula; 29 May 2024 fire weakness 50% on Vespula and the portal, portal ranged-heavy defence
140 -> 110; 29 Nov 2023 ranged strength lowered, guaranteed overload, grub HP 100 -> 125; 16 Jul 2020 no melee hit on players
when the portal dies; 31 Jan 2019 no grub interaction before entry, portal regen 1/3, more points; 7 Apr 2017 more portal points
and potion drops. No entry after 6 Aug 2025.

## Open list

OPEN-VE1 CM max hits; OPEN-VE2 why Vespula HP is x1.44 and the portal x1.48 in CM when the rule is x1.5; OPEN-VE3 HP / stat
scaling by party size and highest combat level; OPEN-VE4 the boil scenery; OPEN-VE5 tendril damage and the start trigger; OPEN-VE6
grub sting interval, depletion, heal amounts and respawn; OPEN-VE7 soldier ground threshold and Vespula's restored HP;
OPEN-VE8 enrage targeting, footprint and speed; OPEN-VE9 point values; OPEN-VE10 the enraged and walking magic defence values (conflict 3). **10 open items.**

## Conflicts between sources

1. Landing threshold: Page "20%" and Strat "20%" vs Portal page "she will stop flying once she reaches 23% of her Hitpoints"; 20%
   is used.
2. Portal range: Strat "range of 7 or greater" vs Portal "seven tiles away ... a weapon with an 8-tile range is highly
   recommended".
3. Magic defence: wiki prints 70 for all forms; cache params 8 = 70 (flying), 190 (enraged), 60 (walking). The cache is used per form
   (**OPEN-VE10**: whether 190 is intentional; the 7531 id is the enraged form).
4. Challenge Mode HP multiplier 1.44 / 1.48 vs the CM page "increased by 50%".
