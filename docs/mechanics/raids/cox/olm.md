# The Great Olm (final room)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Great Olm" (Page; its subpage "Great Olm/Strategies" does not exist, the
strategy text is the "Great Olm" section of "Chambers of Xeric/Strategies" = Strat; "Great olm strategy" is a redirect to it),
"Chambers of Xeric" (Main), "Chambers of Xeric/Challenge Mode" (CM) and "Chambers of Xeric/Challenge Mode/Strategies" (CM-Strat),
"Olmlet"; Mod Ash posts quoted on the Page and Strat (27 Jul 2020, 26 Aug 2020, 6 Sep 2021, 23 Aug 2022); cache 2757 (`cache-dump config npc <id>`; the stats array is 1 / 1 / 1 / 1 / 1 / 1 for the three
fighting parts because the raid scales them at runtime, the `params` map carries the bonuses: params 0-4 = 60 on all three parts and
params 5 / 6 / 7 / 8 = stab / slash / crush / magic defence bonus, which agrees with the wiki); generated symbols (`Npcs`, `Seqs`,
`SpotAnims`, `Locs`, `Objs`). Raid-wide rules (party-size scaling, reward chest and points totals, death and respawn on the third
floor, items dropped on death): the CoX raid-wide doc `../cox.md`. Seconds are converted at 0.6 s per tick. Tick timelines below list
only what a source states; everything else is `OPEN-OL<n>`.

## Data

- NPC ids (cache; the head, left claw and right claw are three NPCs forming one monster):

  | Part | Spawning form | Fighting form | Dying form | Combat level |
  |---|---|---|---|---|
  | Head | `OLM_HEAD_SPAWNING` form **7551** (no ops, HP stat 100) | `OLM_HEAD` **7554** (op2 "Attack", size 5) | head death seq `OLM_HEAD_DEATH_FRONT` 7348 | 1043 |
  | Left claw (melee hand) | **7552** | `OLM_HAND_LEFT` **7555** | `OLM_HAND_LEFT_DYING` **7557** | 750 |
  | Right claw (mage hand) | **7550** | `OLM_HAND_RIGHT` **7553** | `OLM_HAND_RIGHT_DYING` **7556** | 549 |

  Fire NPC `OLM_FIREWALL_NPC` **7558** ("Fire", op1 "Douse", size 1). Pet `RAIDS_OLM_PET` **7520** and `POH_OLM_PET` 7519 (Olmlet page lists
  7519 / 7520). Item `OLMPET` 20851. Scenery (`Locs`): barrier `RAIDS_OLM_BARRIER` 29879 (the mystical barrier before the fight), head
  `OLM_HEAD_SPAWNING` 29880 / `OLM_HEAD` 29881 / `OLM_HEAD_CAVE` 29882, left hand 29883 / 29884 / 29885, right hand 29886 / 29887 / 29888,
  `OLM_CRYSTAL_BOMB` 29766, `OLM_ACID_POOL` 30032, `OLM_CRYSTAL_ATTACK_SMALL` 30033 / `_LARGE` 30034.
  **Conflict 1:** the wiki infobox assigns head 7551 and 7554 to Normal and Challenge Mode respectively (left 7552 / 7555, right
  7550 / 7553, the same pattern); the cache names show 7551 / 7552 / 7550 are the spawn-in forms (not attackable) and 7554 / 7555
  / 7553 the fighting forms for both modes. The cache is used.
- Stats (Page infobox, "scaled for a player with the highest combat level", Normal / Challenge Mode): head HP **800**, attack /
  strength / magic / ranged **250 / 375**, defence **150 / 225**, magic and ranged attack bonus 60, defence bonus stab / slash /
  crush / magic **200**, light / standard / heavy **50**, size 5, attack speed **4**, magic and ranged attacks, aggressive,
  poisonous, poison and venom immune (resistance 100), attributes dragon, xerician. Left claw: HP **600**, attack 250 / 375, strength
  250 / 375, defence **175 / 262**, magic 175 / 262, ranged 250 / 375, defence bonus stab / slash / crush **50**, magic **200**, light /
  standard / heavy 200, no attack (max hit 0). Right claw: HP **600**, same attack and strength, defence 175 / 262, **magic 87 / 130**,
  defence bonus stab / slash / crush **200**, magic **50**, light / standard / heavy 200, max hit 0. All three: 50% elemental
  weakness to earth spells (25 Jun 2025). "Unsure if it scales" is written next to the 60 attack bonuses in the wiki source.
- Max hit (infobox, head): Normal **27** (phases 1 and 2), **28** (phase 3), **29** (phase 4); Challenge Mode **38** (phases 1
  and 2), **39** (phase 3), **41** (phase 4). Conflict 2: CM-Strat says "in solo encounters; phase 1 and 2 has a max of 39, while phase 3 and 4 has
  a max of ~41".
- **Stats of each part are drained separately** ("If stat draining attacks (dragon warhammer special) are used, they only affect the part
  of him that was hit") and **reset when a new phase starts** (Page).
- The head and hands are scaled by party size and team stats (Page "scaledhp = Yes"); the wiki gives no formula, so the exact HP,
  defence and max hit for any team size are `OPEN-OL2`.

## Structure (phases)

- **OL1 Phase count:** the fight has **at least four phases**: **three standard phases and a final head phase** for 1-7 players; **for
  every 8 players in the raid one more standard phase is added**, which "simply reuses one of the three powers from before" (Strat;
  Page says "at least four phases, with an additional phase for every 8 players"). A phase is progressed when both hands are
  disabled (OL18). Working assumption: standard phases = 3 + floor(players / 8), then the head phase (the exact step at 8 players is
  not stated, **OPEN-OL1**). CM-Strat says "only has three phases" for 1-7 (counting without the final head phase, conflict 3).
- **OL2 Powers:** the three powers are **fire, acid, crystal**. In each standard phase except the penultimate Olm uses **one** power,
  announced by the message "The Great Olm rises with the power of Acid!" (Strat; the equivalent fire / crystal texts are not
  quoted, **OPEN-OL3**). "All powers must be expended before they may be used again." Olm starts on the **west or east** side of
  the chamber and appears on the opposite side after each phase transition (Strat, CM-Strat); how the first side and the power order
  are chosen is **OPEN-OL4**.
- **OL3 Penultimate phase:** the last standard phase: the head glows with a green aura, **all three powers are available**, and the left hand
  gains the heal-from-damage ability (OL19). The max hit is "slightly" higher (infobox: phase 3 = 28).
- **OL4 Final phase (head phase):** both hands are gone; Olm does not move; "the head's healing is also lifted" so it can be harmed and
  killed; falling crystals fall continuously (OL21). The max hit is slightly higher again (infobox: phase 4 = 29).

## Head: basic attacks, targeting, rotation

- **OL5 Basic attacks:** the head attacks with **magic** (green orb / dark-green dragonfire-style projectile) and **ranged** (a small crystal chunk,
  `OLM_GENERIC_RANGE_PROJ` graphic 1340 / seq 7504); both deal the same damage (Page). There is a **1/5 chance to swap the style after each
  head attack** (Mod Ash, 27 Jul 2020). The attack hits **all players that Olm is looking at** (Strat). Protection prayer reduces the
  damage "by roughly 75-80%" (Page). Attack speed 4 (infobox). The roll for hit / miss and the damage distribution are `OPEN-OL5`.
- **OL6 Spheres:** Olm picks a sphere at random (red, green or purple) and sends it at the player. Unblocked it damages "approximately 50% of the
  player's current Hitpoints" (Strat "50%"); the matching protection prayer prevents all damage: **red = Protect from Melee, green =
  Protect from Missiles, purple = Protect from Magic**. If a protection prayer was active when the sphere launched, **that prayer is
  disabled and the player's Prayer points are reduced by 50%**. Projectile graphics `OLM_WEAK_MELEE / _RANGE / _MAGE _PROJ` 1345 / 1343 / 1341 and
  `_IMPACT` 1346 / 1344 / 1342. The frequency of spheres against basic attacks, which player is targeted and the sphere's flight time
  are `OPEN-OL6`. Whether the 50% is of current HP at the hit or at launch: `OPEN-OL6`.
- **OL7 Zones and the head's turning (Page, Strat "Safespotting"):**
  - Olm has three attack zones: **left, middle, right**. It always tries to face the zone with **the most players**. If its zone
    holds no player, it turns toward the zone with the most players and **skips that attack** in the rotation. If a player is in its
    zone but Olm turns anyway, no attack is skipped (Page).
  - If a hand is damaged within **4 ticks** and Olm does not scan a player, it turns to that hand's side (right hand damaged = right,
    left = left). All damage counts, including thralls and burns (Strat).
  - Eight safespot tiles (numbered 1-8 in the wiki picture) force a turn: spots **1 and 8** force a left turn when Olm faces middle or
    right; **4 and 5** force a right turn when it faces middle or left; **2 and 7** force it to the middle if it faces right; **3 and 6**
    force the middle if it faces left. The tiles' coordinates are in the picture only (**OPEN-OL7**). Position of the NPC's "true tile"
    is its south-west corner (Strat).
  - In a group the middle player(s) are the "head runners" who move left and right to move Olm's attention (CM-Strat).
- **OL8 Attack rotation (12 steps, loops):** Strat order: **empty event, standard attack, Crystal Burst, standard attack, empty
  event, standard attack, Lightning, standard attack, empty event, standard attack, Teleport (swap), standard attack**, then loops.
  The Page prints the same cycle shifted by one step (standard, empty, standard, burst, standard, empty, standard, lightning,
  standard, empty, standard, teleport). "Olm requires two standard attacks before using any of them." A step is not given a
  length by any source (attack speed 4 is the only figure), `OPEN-OL8`.
  - "Standard attack" means the basic ranged / magic attacks, spheres and the phase-specific abilities (Strat).
  - **Catch-up:** if a standard attack was skipped (nobody in the targeted zone), Olm uses a standard attack **in addition** to
    the next step's action. It catches up only the one step before, never cascading; it **never catches up special attacks** (crystal
    burst, lightning, teleport, healing hand), so a well-timed skip can remove a special entirely (Page, Strat).
- **OL9 Phase-specific attack choice (Mod Ash, 6 Sep 2021 and 23 Aug 2022):** at a standard attack there is a **1/3** chance (**1/2 in the final
  phase**) that it is replaced by one of the current power's attacks, unless on cooldown; the chance depends on cooldowns so the
  probability is not simple. When the roll succeeds it chooses among the available power attacks and tries not to repeat one
  already used in the phase (it can still repeat). If the fire wall has too little space, Olm does nothing and continues the rotation with no
  catch-up (Page). Healing-pool cooldown: **10 attacks** (29 Nov 2023). Other cooldown lengths: `OPEN-OL9`.

## Powers (phase-specific attacks)

- **OL10 Acid:**
  - **Acid Spray:** Olm spits several acid pools around the chamber (`OLM_ACID_SPIT` graphic 1354, head seq `OLM_HEAD_ATTACK_ACID_FRONT /
    _RIGHT / _LEFT` 7345 / 7346 / 7347, loc `OLM_ACID_POOL` 30032). A player on a pool takes **3-6 damage per tick** and is **poisoned
    starting at 2 damage** (Page); invisible pools damage fix: pools that are gone no longer hurt (7 Dec 2017).
  - **Acid Drip:** Olm covers one player in acid; that player generates pools under their feet: standing still deals damage,
    moving generates more pools; pool damage as above (Strat). Pool lifetime, the drip's duration, number and placement of spray pools:
    `OPEN-OL10`.
- **OL11 Flame:**
  - **Deep Burn (Draining Fire):** a green fireball at one player (`OLM_BURNWITHME_TRAVEL` graphic 1349, seq 7505); the player shouts "Burn
    with me!". It deals **5 damage and drains every combat stat by 2, six times** (Strat: "inflict damage and stat drains 6 times, each
    occurrence 5 damage and draining all combat stats by 2"); "every few ticks" (Page): the interval is `OPEN-OL11`. If the burning player
    is next to a player who is not burning when a tick occurs, the fire **spreads**: the newcomer shouts "I will burn with you.",
    takes a fresh burn, and **the host's burn timer resets**.
  - **Fire Wall:** two walls of fire are shot at a player (`OLM_FIREWALL_TRAVEL` 1347 / 1348, NPC 7558 "Fire"). A player standing on a wall
    tile leaps out and takes **8-10 damage (Page; Strat: up to 10)**. Players left between the walls take **50-65 damage in 5 seconds
    (about 8 ticks, rounding `OPEN-OL12`)** unless they escape or a wall segment is doused. A segment is doused by clicking it while
    able to cast a **water spell, ice spell (16 Jun 2021) or Humidify (greater distance since 21 Mar 2019, vessels of water since 14 Feb 2019)**;
    the Page also names a filled watering can, Strat a water-filled gourd vial. The player "may leap into the gap between the walls".
    The wall needs free space; if it fails nothing happens (OL9). Wall lifetime and size are `OPEN-OL12`.
- **OL12 Crystal:**
  - **Falling Crystals (power attack):** Olm cries out and marks one player with a red aura; **11 spiked crystals** fall on them; any
    player under a crystal takes **15-20 (Page) / 16-20 (Strat)**; each crystal is a **1x1** area (Strat). Shadows are shown (7 Dec 2017).
  - **Crystal Bombs:** Olm shoots out a crystal bomb, **up to three, two for smaller groups** (`OLM_CRYSTAL_BOMB_TRAVEL` 1352, loc 29766,
    explosion graphics 40 / 1358). After several seconds it explodes in a **radius of 4 tiles**; damage is **15 at the rim and 15
    more for every tile closer, 60 directly on top** (Page, Strat). Bursting crystals no longer spawn under a bomb and invisible
    explosions are gone (7 Dec 2017). Delay and the "smaller groups" threshold: `OPEN-OL13`.

## Special attacks (non-head phases only; Strat)

All three are shown by an icon on the left hand (crystal, lightning, swirl) and happen on the rotation of OL8. They are unavailable
if the left hand is clenched (OL17) and are removed in the final phase (OL21).

- **OL13 Crystal Burst:** a crystal seedling is placed under **every player**; a few seconds later it bursts: a player on top is shoved to a
  nearby tile and takes heavy damage (locs `OLM_CRYSTAL_ATTACK_SMALL / _LARGE`, graphics `RAIDS_OLM_SHATTERCRYSTAL` 1337, `OLM_CRYSTALWAVE_SHATTER`
  1353). Damage and the burst delay: `OPEN-OL14`.
- **OL14 Lightning:** bolts of lightning run **north and/or south of the chamber** (`OLM_SHOCKWAVE` seq 7471 / graphic 1356). A hit
  electrocutes: **overhead prayers are disabled, moderate damage, the player is bound for a few seconds**. Damage, bind time, which lanes
  and how they are chosen: `OPEN-OL15`.
- **OL15 Teleport (swap):** shown by a swirl on the left hand (graphics `OLM_PLAYERSWAP_0..3` 1359-1362). Players are teleported and **take
  damage that grows with the distance between the target tiles**; standing on top of each other gives **0**, adjacent **5**, two tiles **10**,
  up to **50** at the greatest distance. **Group:** up to **six players** can be targeted, spread evenly; an odd number leaves one player
  untouched. **Solo:** a random tile in the arena up to **10 tiles** from the player's current position (29 Nov 2023: the portal spawns
  within 10 tiles in a solo encounter). Players must stand on each other (or the targeted tile in solo) to avoid damage (Strat). Only
  four player-swap graphics exist in the cache against "up to six" targeted players: `OPEN-OL16`.

## Hands

- **OL16 Damage mitigation:** head **66%** damage mitigation from all non-ranged attacks and it **restores all damage dealt to it after
  several ticks** outside the final phase; right hand (mage hand) 66% from all non-magic; left hand (melee hand) 66% from all non-melee
  (Page, Strat; the Page text for the head says "non-range", the Strat says "non-ranged"). In each phase before the last, damaging the head earns no
  points (CM-Strat). The delay before the head heals is `OPEN-OL17`.
- **OL17 Clench (left hand):** during all phases except the penultimate, if the left hand takes one hit equal to or more than **5% of its
  maximum health (1/20 of base health, Mod Ash 26 Aug 2020) within 8 ticks**, "specifically the eight ticks between null and special"
  (Strat), the hand **clenches and is invulnerable for roughly 30 seconds (50 ticks)**; it cannot use the generic specials during that time
  (OL13-OL15), the power's attacks still happen. The mechanic ends for the phase once the mage hand is disabled, so teams take the mage hand
  first. It also ends if the right hand dies (CM-Strat), and if the left hand is disabled first the specials stay off for the phase.
  Seqs: `OLM_HAND_LEFT_STUNNED` 7360 / `_STUNNED_IDLE` 7361 / `_UNSTUNNED` 7362 / `OLM_HAND_LEFT_GRIPPING_*` 7363-7369; the exact unit
  of "1/20" (base versus scaled health) is `OPEN-OL18`.
- **OL18 Disabling:** a phase ends when **both hands are disabled** (HP reaches 0; death seq `OLM_HAND_RIGHT_DEATH` 7352, `OLM_HAND_LEFT_DEATH`
  7370). Stats of each part reset at the new phase (Data).
- **OL19 Penultimate phase (healing hand):** the left hand gets an infinity symbol: while it is active, **any damage it takes heals it instead**. It
  is used **two attacks after the teleport** and lasts **two Olm attacks** (Strat); left hand seq `OLM_HAND_LEFT_CAST_HEAL` 7357. Both hands must be
  disabled within a time window: a disabled hand shows a **bar that fills in roughly 30 seconds (50 ticks)**; when it fills Olm regains the hand
  **with full health** and no points are awarded for damaging it again; damage to a recovered hand yields no points. Strat advice:
  leave both hands around 75 HP, then kill the melee hand first. The healing hand no longer persists after the heal ends and all
  healing pools block damage whichever the player stands on (7 Oct 2020). Fill time exact value: `OPEN-OL19`.

## Transitions and the final phase

- **OL20 Phase change (falling crystals):** when both hands are down Olm retreats underground and shakes the cavern; **crystals fall from above**
  with shadows telling where; **one crystal always targets a player**. The crystals have a **3x3 AoE**: directly under one **20-25 damage
  (Strat; Page 16-25)**, next to one **12-16 (Strat; Page 10-16)** (conflict 6). After **7-8** targeting crystals Olm appears on the other
  side with a new power (Strat).
- **OL21 Final phase:** crystals fall continuously until Olm dies, one at a time aimed at a random player (Page / Strat). Spheres and all
  powers' attacks remain; the forced teleport, lightning and crystal burst are removed (Strat; Page lists the swap among the specials without
  saying it is removed, conflict 5). New in this phase is the **Life Siphon (healing pool) attack**: Olm spits **two blue projectiles
  onto two random tiles** (`OLM_HEALME_SPIT` graphic 1355, `OLM_HEALME_SPOTANIM` 1363), which create a shield; the projectiles must be stood on within
  **6 seconds (10 ticks)** (Strat) / "several ticks later" (Page); every player not standing on one takes a moderate hit of **up to 18**, and Olm
  heals for **five times** the total damage dealt to the team. Placement rule and exact timing, and whether the Life Siphon shields and the "healing pools" of the 7 Oct 2020 and 29 Nov 2023 change notes are the same object (assumed): `OPEN-OL20`. The ratio of phase-attack to basic attacks is
  1/2 here (OL9).

## HP scaling, points and rewards

- HP, defence and max hit scale with party size and the highest combat stats in the team (Main 7 Mar 2019: "NPC stats are now scaled based on the player with the
  highest combat stats"), and "an Olm in a 100-man run will hit much harder than that in a solo encounter" (Strat); "this scaling will not lower
  if players leave". The formulas are not on the wiki (`OPEN-OL2`). Monster Examine can be cast on all parts (14 Feb 2019).
- **Points:** damage to Olm's parts earns points like other raid monsters (raid-wide doc), **except**: damage to the head outside the final phase
  yields no points (CM-Strat) and no points for damaging a hand that was recovered or any hand while Olm has regained control of it (Main).
  Points per damage and cap: raid-wide doc.
- **Reward:** killing the head collapses it into the chasm and shatters the exit crystal; players claim rewards from the chest (Strat). The unique roll
  and the pet: **Olmlet pet 1/53 per unique obtained** (Olmlet page; it was 1/650 before 9 Feb 2017, 1/65 until the dragon sword / harpoon /
  thrownaxe removal of 10 Jan 2019). It follows the owner, has Talk-to, Metamorphosis and Pick-up; **metamorphic dust** (CM, 1/400 within the time below)
  unlocks the forms Puppadile, Tektiny, Enraged Tektiny, Vanguard, Vasa Minirio, Vespina and Flying Vespina; one dust is enough (24 Mar 2021).
  The Olmlet page text says "five other pets", the CM page lists seven forms (conflict 7). If the player has a follower and a full inventory
  the pet is claimed from Probita. Pet ids per form (wiki): Puppadile 8196 / 8201, Tektiny 8202 / 8197, Enraged Tektiny 9511 / 9513, Vanguard 8198 / 8203,
  Vasa Minirio 8204 / 8199, Vespina 8200 / 8205, Flying Vespina 9512 / 9514 (not verified in the cache here).
- Fires cannot be lit in Olm's chamber (7 Dec 2017); logging out inside to drop supplies without losing points was disabled (date not read from the changelog).

## Per-mode differences (Normal vs Challenge Mode)

- **Stats:** attack, strength, defence, magic and ranged of all three parts are x1.5 in CM (the infobox: 250 -> 375, 150 -> 225, 175 -> 262, 87 -> 130).
  **Olm's HP does not change in CM** ("Apart from the Great Olm's health, all of the enemies' combat stats (including health) are increased by 50%");
  the infobox shows 800 / 600 for both.
- **Max hit:** 27 (phases 1-2) / 28 (phase 3) / 29 (phase 4) becomes 38 / 39 / 41 (conflict 2). The CM-Strat advises keeping HP above 60.
- **Mechanics:** unchanged ("The same tactics are applied as in normal mode"). The CM-Strat notes that the right hand's higher Magic level makes
  staves other than Tumeken's shadow miss often, the left hand's higher Defence lowers special accuracy, and the head's higher Magic level makes
  the twisted bow very effective.
- **Phase count:** the same rule (CM-Strat: three phases for a small team, an extra phase per 8 players).
- **Rewards:** CM completion inside the time limit (Number of players -> 1: 1 h 10 min, 2: 1 h 5 min, 3: 50 min, 4: 45 min, 5-10: 42 min, 11-15: 45
  min, 16-23: 1 h, 24+: 1 h 20 min) gives a **1/75 twisted ancestral colour kit** and a **1/400 metamorphic dust**, plus **5,000 points** to the team total
  (CM page: "per player for the total team points count" versus the 28 Feb 2019 "added to the team's reward rather than individual party members";
  the sum is `OPEN-OL21`).

## Tick timeline (stated parts)

- Head attack speed **4** ticks; 1/5 style switch per head attack.
- Rotation of 12 steps (OL8); a step's length is not stated.
- Head turns to a hand's side if that hand was damaged within **4** ticks.
- Clench window **8** ticks; clench lasts about **30 s = 50** ticks.
- Hand-recovery bar about **30 s = 50** ticks.
- Falling crystals at a phase change: 7-8 targeted crystals; the time between crystals and the underground time are not stated.
- Fire wall: **5 s (about 8 ticks)** between the walls closing and the damage; Deep Burn: 6 hits, interval "every few ticks".
- Life Siphon: **6 s = 10 ticks**. Healing hand: starts two attacks after the teleport and lasts two Olm attacks.
- Healing-pool cooldown 10 attacks. Swap: portal within 10 tiles (solo).
- Overload pulses every 15 s (25 ticks) for 5 minutes and prayer enhance restore intervals matter for the fight length: [resources.md](resources.md).

## Changes up to 2026-11-03 that matter

7 Jan 2026 burning claws now burns Olm's melee hand; 25 Jun 2025 earth spell weakness 50%; 29 Nov 2023 swap portal within 10 tiles in solo and a 10-attack cooldown
on the healing pool; 3 May 2023 two storage units added to Olm's antechamber; 16 Jun 2021 fire wall can be extinguished with ice spells; 7 Oct 2020 the healing special no
longer persists and all healing pools block damage; 18 Jun 2020 Olm health bar HUD; 21 Mar 2019 Humidify range; 14 Feb 2019 Humidify and vessels of water
douse the flame, Monster Examine on all parts; 7 Dec 2017 invisible explosion, crystal burst under a bomb, acid pool, shadow and fire-lighting fixes; 2 Aug 2018
name capitalised; 9 Feb 2017 Olmlet rate raised.

## Open list

OPEN-OL1 phase count at exactly 8 players (3 + floor(n/8) assumed); OPEN-OL2 HP, defence and max hit by team size and combat level; OPEN-OL3 the other power
announcements and the power order / first side; OPEN-OL4 first power and side selection; OPEN-OL5 hit chance and damage distribution of the basic attacks and
prayer reduction; OPEN-OL6 sphere frequency, target and timing; OPEN-OL7 safespot tile coordinates; OPEN-OL8 length of a rotation step and the catch-up timing;
OPEN-OL9 other cooldowns; OPEN-OL10 acid pools (lifetime, counts, drip length); OPEN-OL11 Deep Burn interval; OPEN-OL12 fire wall timing, lifetime and the rounding of
5 s; OPEN-OL13 crystal bomb timing and the small-group threshold; OPEN-OL14 crystal burst damage and delay; OPEN-OL15 lightning damage, binding time and lanes; OPEN-OL16
swap target count against the four swap graphics; OPEN-OL17 head heal delay; OPEN-OL18 clench threshold basis (base vs scaled); OPEN-OL19 hand-recovery bar time;
OPEN-OL20 Life Siphon placement and timing; OPEN-OL21 CM bonus points. **21 open items.**

## Conflicts between sources

1. Head / hand ids: wiki Normal 7551 / CM 7554 (and the same pattern for the hands) vs the cache: spawn form and fighting form for both modes.
2. CM max hit: infobox 38 (phases 1-2) / 39 / 41 vs CM-Strat "phase 1 and 2 has a max of 39, phase 3 and 4 ~41".
3. Number of phases: "at least four" (Page) / "four in total, three standard and a head phase" (Strat) vs "only has three phases" (CM-Strat).
4. Clench: "equal to at least 1/20 of its base health" (Page, Mod Ash) vs "equal to or more than 5% of its maximum health within 8 ticks" (Strat): the
   same fraction; the base-versus-maximum wording is OPEN-OL18.
5. Standard-attack share: the Page says a phase-specific attack replaces "one of the standard attacks" with chance 1/3, the Strat says the power
   abilities "can replace his spheres or basic attacks"; consistent, spheres and basic attacks are both replaceable.
6. Phase-change crystals: Page 16-25 / 10-16 vs Strat 20-25 / 12-16; falling crystals of the power: Page 15-20 vs Strat 16-20.
7. Number of metamorphosis forms: "five other pets" (Olmlet) vs seven listed forms (CM).
8. Rotation offset: Page begins with a standard attack, Strat with an empty event; both loop the same 12 steps. Strat used.
9. Fire wall douse tools: Page names filled watering cans, Strat names water-filled gourd vials (both listed).
10. Fire wall damage on being clipped: 8-10 (Page) vs "up to 10" (Strat).
