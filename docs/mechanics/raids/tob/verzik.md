# Verzik Vitur (room 6)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Verzik Vitur" (Page), "Theatre of Blood/Strategies"
(Strat), "Theatre of Blood/Entry Mode" (Entry), "Theatre of Blood/Hard Mode" (Hard), "Theatre of Blood" changelog (Main),
"Supporting Pillar" (Pillar), "Web (Verzik Vitur)" (Web), "Nylocas Athanatos", "Nylocas Matomenos", the Nylocas breed
pages, "Dawnbringer", the update notes "Interface Uplift Round 2" (1 Oct 2025, U-Uplift) and "Minigame Tweaks, Skilling
Adjustments & more!" (23 May 2024, U-May24); cache 2757 (`cache-dump config npc <id>`; stats array order attack,
defence, strength, hitpoints, ranged, magic; gamevals `VERZIK_*`, `TOB_VERZIK_*`). Raid-wide rules:
[../tob.md](../tob.md). Tick timelines below list only what a source states; everything else is `OPEN-VZ<n>`. Seconds are
converted at 0.6 s per tick.

## Data

- NPC ids (wiki; cache names in brackets). The wiki groups each phase's ids; the transition forms are separate NPCs:

| Mode | Phase 1 | Phase 2 | Phase 3 |
|---|---|---|---|
| Entry | 10830, 10831, 10832 | 10833, 10834 | 10835, 10836 |
| Normal | 8369 (`VERZIK_INITIAL`), 8370 (`_PHASE1`), 8371 (`_PHASE1_TO2_TRANSITION`) | 8372 (`_PHASE2`), 8373 (`_PHASE2_TO3_TRANSITION`) | 8374 (`_PHASE3`), 8375 (`VERZIK_DEATH_BAT`) |
| Hard | 10847, 10848, 10849 | 10850, 10851 | 10852, 10853 |

  Other NPCs: throne `VERZIK_THRONE_NPC` 8380; web 8376 (Entry 10837, Hard 10854); supporting pillar 8379 / 10840 / 10857
  (Normal / Entry / Hard); collapsing pillar 8377, 8378 / 10838, 10839 / 10855, 10856; creeper `TOB_VERZIK_CREEPER` 8386
  (presumed the P3 acid / web effect NPC, **OPEN-VZ1**); armoured Nylocas (Athanatos) 8384 (Entry 10844), blood Nylocas
  (Matomenos) 8385 (Entry 10845, Hard 10862), combat Nylocas 8381-8383 (Entry 10841-10843, Hard 10858-10860; see
  [nylocas.md](nylocas.md)). Quest NPCs, not the fight: 8250 is the "A Taste of Hope" version (race Vampyre / Nylocas, not human) and 11178 the "A Night at the Theatre" version (race Vampyre).
- Combat level: Entry **425 / 488 / 512**, Normal and Hard **1040 / 1265 / 1520**. Size **5 / 3 / 7**. Attack speed
  **14 / 4 / 7** (phase 3 becomes 5 on the enrage). Attack styles P1 magic; P2 crush, magic, ranged; P3 typeless crush,
  magic, ranged. Aggressive from P2. XP bonus Entry 0 / 7.5 / 30, Normal and Hard 5 / 30 / 57.5.
- Max hit (infobox): Entry P1 **60**, P2 **16** (lightning 16, bounce ~34 after standing underneath), P3 melee **36**,
  magic **20**, ranged **20**; Normal P1 **137**, P2 **47** (50 bounce, ~80 bounce after standing underneath), P3 melee
  **63**, magic **33**, ranged **33**; Hard P2 **44** (50 / ~80), the rest as Normal.
- HP (5 players): Entry **1,200 / 1,600 / 2,400** (cache base **300 / 400 / 600**); Normal and Hard **2,000 / 3,500 /
  3,500**. By party size: Normal and Hard P1 1,500 / 1,750 / 2,000, P2 and P3 2,625 / 3,062.5 / 3,500 (1-3 / 4 / 5
  players; rounding **OPEN-VZ2**); Entry (TB11) P1 300 / 570 / 810 / 1,020 / 1,200, P2 400 / 760 / 1,080 / 1,360 / 1,600,
  P3 600 / 1,140 / 1,620 / 2,040 / 2,400 for 1-5 players.
- Stats (infobox; cache arrays agree): Entry P1 att 180, str 150, def 10, magic 180, ranged 180; P2 att 200, str 150, def
  120; P3 att 180, str 200, def 120; bonuses P1 magic attack +32, magic strength +140, ranged +32 / +50; P2 +32 / +50 /
  +32 / +50; P3 attack +32, strength +46, magic +20 / +5, ranged +20 / +5; defence P1 10 across, P2 stab 40 slash 20
  crush 40 magic 40 ranged 180, P3 stab 30 slash 10 crush 30 magic 60 ranged 100. Normal and Hard: P1 att 400, str 400, def
  **20**, magic 400, ranged 400, magic +80 attack / +150 strength, ranged +80 / +80, defence 20 across; P2 def **200**,
  magic 400, ranged 400, magic strength +80, ranged strength +80, defence stab 100 slash 60 crush 100 magic 70 ranged 250;
  P3 att 400, str 400, def **150**, magic 300, ranged 300, attack +80, strength +30, magic strength +5, ranged strength +5,
  defence stab 70 slash 30 crush 70 magic 100 ranged 230. P3 weak to fire spells by **50%** (25 Jun 2025).
- Her **magic defence is calculated from her Defence level** (not Magic) in all phases (Mod Ash, 9 Aug 2023); **her
  Defence cannot be lowered**: in P2 and P3 defence-draining specials give "Verzik Vitur absorbs the defence reduction!".
- **Supporting pillars:** six (map pins at x 3162 / 3174, y 4307 / 4313 / 4319), cache HP **185** Normal, **200** Entry,
  **185** Hard (the cache shows the same size 3 NPC; party-size scaling **OPEN-VZ3**).
- Drops: book "Verzik Vitur - Patient Record" (always unless read; obj `TOB_BOOK_VERZIK` 22514).

## Fight

- **VZ1 Start:** a player talks to Verzik to begin; dialogue in Entry. **Quick-start** (right-click) with at least one
  prior completion, Normal and Hard only (TB24). Music: unlocked on entering the throne room and during the fight.
- **VZ2 Phase 1 (shield, on the throne):** the HP bar is her shield. Damage per hitsplat is capped at **10 melee and 3
  magic or ranged**; the **Dawnbringer special** (75-150 magic damage, ignores accuracy) is the only way to hurt her
  properly. Entry: "around 2-4 special attacks". The party takes turns with the one Dawnbringer, dropping it for the next
  holder (orb order). She **charges a magic attack every 14 ticks (8.4 s)** for up to **137** (Protect from Magic halves
  it; it cannot be tick-eaten); a player behind a **pillar** makes the pillar take the hit instead: one hit per pillar
  per attack however many players hide behind it. A pillar at 0 collapses, dealing up to **70** (Entry 50) to players
  adjacent to it, pushing them a few tiles and stunning them. When the shield breaks she leaves the throne and **all
  remaining pillars collapse**, hurting players adjacent. Number of hits a pillar can take: **OPEN-VZ4**.
- **VZ3 Phase 2 (flying, in the middle):** attack speed 4. She throws an **urn bomb** at each player's current tile every
  4 ticks (ranged; up to 44, halved by Protect from Missiles; damage on impact so a one-tile step avoids it); a player
  **next to her** is very likely body-slammed instead (crush, up to 45, knocks back 3 tiles and stuns 3 s = 5 ticks,
  exposing them to a bomb), a player **under** her may be stomped for up to 82 ("There's nothing for you there!",
  always hits); Protect from Melee does nothing against either. Every **fifth attack** (Entry page: "after every four bomb attacks", conflict 11) she fires a **lightning ball**
  that bounces between players up to **4 times** in random order; if it passes through her she takes **15-20**
  (no accuracy roll); a player hit alone or on the fourth bounce takes up to **48** (25 with insulated boots, 23 Jun 2021
  fix for Hard). Cap with only one player alive in a larger party (Normal): **OPEN-VZ16**, sources disagree: Strat
  has a bug note "The damage dealt by the lightning ball will not be capped in Normal Mode if there is only one player left
  alive in a team of multiple people", the Main changelog (23 May 2024) says "The lightning ball was supposed to be capped,
  but they couldn't figure it out for team raids", while the update post (U-May24) says "the Lightning Ball damage is
  capped, as in Hard Mode". Working assumption: not capped, behind the flag `tob.verzikSoloLightningCap` (default off).
  - **Nylocas:** at the start of P2 she summons combat Nylocas (25 HP each) that home on a random target and explode for
    up to **46** (up to 8 if 3 tiles away; Page says 63), or expire after about 15 s of movement (Strat); players "pop"
    them by letting them come within 3 tiles then moving away. After **every third lightning ball** there is a **25%**
    chance to summon them again; if an Athanatos is alive, none spawn.
  - **Nylocas Athanatos:** a purple one is launched as a slow projectile at a random player and lands for up to **78** at
    the centre tile (less one tile away); it then heals her **9-10 every few ticks**. A hit with a poison / venom weapon
    (or smoke spell, or the serpentine helm) makes it burst and deal up to **70** to her; raw damage is inefficient
    (regenerates, damage reduction, quick respawn; numbers **OPEN-VZ5**).
  - **Phase 2.5 at 35% or lower:** she stops attacking and summons **two Nylocas Matomenos** (red) to her left and right
    (**one** if a single player remains alive, Normal, 23 May 2024); damage dealt to her while she is summoning (about 3 s =
    5 ticks) **heals** her. She prioritises a blood spell (75% chance of the healing spell, Mod Ash 20 Jul 2020): an AoE
    on a 3 x 3 around one player for up to **45**, healing her for **50% of the total damage**; Protect from Magic: sources
    disagree (conflict 8): Strat "will block the attack, but will still heal her for half the damage she would have dealt"
    (Mod Ash, 20 Jul 2020, quoted there: "halved by prayer ... ~22, healing her for 11"), the Verzik page "negates all
    damage from the blood spells"; it is calculated during the animation. She kills the
    Matomenos if they persist too long and heals the HP they have left. No more explosive Nylocas or Athanatos after 35%;
    urn bombs and lightning continue. Hard: surviving Matomenos **explode** damaging nearby players based on their HP when she
    summons the next set.
- **VZ4 Phase 3 (on the floor):** after the transition she loses some Defence, Magic and Ranged levels and bonuses and gains
  melee accuracy (the P3 stat line), and again absorbs defence reductions. Attack speed **7**. She picks **one player** as her
  target for the phase and changes if they are **not in melee distance for more than 10 s** (the timer resets while they are
  in range; Strat, which contrasts this with the earlier phases where "Verzik frequently changes aggression"). Hard: she picks a **new target at
  each special**. Normal attacks: **ranged** (stomping front legs, barbs), **magic** (blue crackling orb) up to **33**
  (16 with the right prayer), both hitting all players in the room, and **melee** (crush, cannot be prayed) up to
  **63** to the target and every player adjacent to her, only when the target is in melee range (not out of range and not
  under her). **Four normal attacks, then one special**, in a fixed repeating order:
  1. **Nylocas** as in P2 but never an Athanatos.
  2. **Webs:** she walks to the middle (becoming invulnerable and a hard NPC, knocking players away) and throws webs at the
     players, three at a time if they are not on one tile. A web (10 HP, a separate NPC) **binds** the player on its tile
     until a teammate destroys it or it snaps and deals heavy damage; **aranea boots** negate the damage once per 8 ticks.
     The Scythe can slash webs (2 Aug 2018).
  3. **Power blast (yellows):** she becomes invulnerable and charges; one **yellow pool** per player appears, each protecting
     one player; a player not on a pool takes up to **80** (tick-eatable); pooled players are safe; pools stacked by several
     players protect none. There are **7 ticks** between the end of the special and her next normal attack. Hard: the blast
     strikes **three times per player** and three safe tiles per player appear.
  4. **Green ball:** the message "Verzik Vitur fires a powerful projectile in your direction..." (colour `0a721f`) names the
     target; the ball deals **75% of the player's Hitpoints level** (Strat: "75% ... (74 at level 99)"; the Verzik page says "up to
     74%", conflict 10) (74 at 99) unless **bounced** to a different adjacent
     player, which must repeat until it dissipates (three bounces in the Entry text); a ball that finds nobody in range or
     lands on the same player twice explodes for the full damage. Hard: it **kills outright** if the ball has not been
     bounced and two or more players are present (the Page says "more than two"), damage figures 89 / 74 / 49 for 1 / 2 / 3
     bounces, and Redemption triggered at the last moment cancels it; with one player left it works as Normal (**OPEN-VZ6**).
     A chat message is shown when the Hard ball bounces to you (9 Jun 2021). **Assumption:** the 9 Jun 2021 hotfix text calls it
     "the Acid Bomb at Verzik" (and "the acid bomb" in Story mode); this doc reads "Acid Bomb" as the green ball, which no source
     states (the lightning ball also bounces; **OPEN-VZ17**).
- **VZ5 Enrage at 20% or lower:** she shouts (Strat: "I'm not finished with you just yet!"; Entry page: "I'm not done with you
  yet!"; **conflict**, **OPEN-VZ7**), the projectile max hit rises to **34** (Strat; conflict 9), her attack speed goes from 7 to **5**, and a
  **purple tornado** is summoned **for every player**, each homing on its own player (never another's). A tornado that
  touches its player deals **50% of that player's current HP** (never less than **5**, 9 Jun 2021) and she **heals triple the
  damage**; it also heals her if the player dies to something else. A new tornado spawns **16 ticks** after the old one hit.
  Specials continue in the same order.
- **VZ6 Hard "second wind":** at **5% or lower** for the first time she yells "You think this is over?" and heals **30% of her
  HP** once.
- **VZ7 Death:** her throne collapses revealing a trapdoor / secret entrance to the treasure vault (Main, Strat); music "The
  Curtain Closes" on entering the vault. The party's chests: [lobby-rewards.md](lobby-rewards.md).

## Per-mode differences

- **Entry:** HP and damage as above (max hits 60 / 16 / 36-20-20); pillar collapse 50; unlimited retries with Bandages after
  each failed attempt; the Dawnbringer special "around 2-4 specials"; a poisoned weapon is required for the Athanatos;
  insulated boots mandatory solo; no quick-start.
- **Normal:** as above.
- **Hard (23 Jun and 9 Jun 2021 hotfixes included):** P1 pillar hits drop rubble in 1 x 1 AoEs up to 3 tiles from the
  pillar, stunning and dealing up to **35**; P2 urn bombs do about 20% more and leave a **poison pool for 16 ticks**
  dealing **9-10** poison damage; P2/P3 combat Nylocas rotate melee -> ranged -> magic at set intervals and can only be
  damaged by their current style (no reflect; 9 Jun 2021); Matomenos explode when she summons the next set; lightning
  bounce range +50% and insulated boots now reduce it; P3 power blast thrice per player; green ball rule above; second wind
  at 5%; she re-targets at each special.

## Tick timeline (stated parts)

- P1: magic charge every 14 ticks; the first window allows 4 attacks with a 4-tick weapon (3 with a 5-tick) after the start.
- P2: attack every 4 ticks (urn bombs); lightning every 5th attack (20 ticks); slam stun 5 ticks; 25% re-summon check after
  every third lightning ball; Athanatos heal every few ticks; Hard poison pool 16 ticks.
- P3: attack every 7 ticks (enrage 5); 4 normal attacks then a special; 7 ticks between the end of the power blast and the next
  normal attack; tornado re-spawn 16 ticks; aranea boots 8 ticks; web 10 HP; target change after 10 s (about 17 ticks)
  out of melee range.

## Changes up to 2026-11-03 that matter

1 Oct 2025 quick-start (U-Uplift); 25 Jun 2025 P3 fire weakness; 23 May 2024 one Matomenos with one player left (the lightning cap on that single survivor is
disputed: OPEN-VZ16); 9 Aug 2023 magic defence from Defence (clarification); 23 Jun 2021 Nylocas explosion damage
no longer scales up in low scales, insulated boots reduce the Hard lightning, non-melee Hard Nylocas only hit by their
style, wave-20 fix, raid timer starts at the Maiden; 9 Jun 2021 hotfix (tornado minimum 5, Story-mode P3 +200 HP and P2 -200
HP and tweaks, Hard lightning damage and range, Nylocas detonation fix, bounce chat message); 4 Jun 2021 hotfix (green ball
damage up, no tick-eating the yellow in Hard); 3 Jun 2021 modes added; 16 May 2019 / 2 May 2019 / 2 Aug 2018
fixes (web damage one cycle earlier, correct movement, damage computed on hit in P3, re-target non-caged players).

## As implemented (content/combat TobVerzik.kt)

One script (`TobVerzik`) over her forms; `TobVerzikAthanatos` for the Athanatos. Mode from the NPC id; form id = mode base (Normal
8369, Entry 10830, Hard 10847) + kind (1 P1, 2 P1->P2, 3 P2, 4 P2->P3, 5 P3, 7 web, 8 collapsing pillar, 10 pillar, 11-13 combat
Nylocas, 14 Athanatos, 15 Matomenos, 16 tornado), plus 1 for Normal's kinds above 10. Entry damage = Normal / 2 (floor) via
`TobModes.damage` where noted ("E/2"). `become` re-transforms with full stats and applies `TobModes.scale` HP. Arena radius 20.
Per-tick loop from the start; each tick her Defence is reset to base in P2/P3; nothing happens before `nextAttack`.

- **Start (VZ1, TB24, OPEN-TB11):** `start(npc, player, quick)` only in phase 0. Quick-start is refused in Entry or when the player's
  `TOTAL_COMPLETED_THEATREOFBLOOD + _HARD` varps sum to 0 (so any Normal or Hard completion of that player counts, Entry does not).
  Then throne anim, P1 form, six pillars at offsets from her spawn `(-4,-5) (8,-5) (-4,1) (8,1) (-4,7) (8,7)`, cache HP (not
  scaled; **OPEN-VZ3**), first attack at tick + **18**.
- **P1 shield (VZ2, OPEN-VZ15):** `incoming`: 0 before start; otherwise a hit is `min(damage, 10)` melee or `min(damage, 3)` other, in
  every mode, unless the hitter has the Dawnbringer **worn** (full damage; the code does not test for the special attack).
  - Attack every **14** ticks: magic anim, one bolt per raider: projectile 1580 (start 20, end `20 + 15 x d`), lands `end / 30 + 1`
    ticks later, `0..137` (Entry `0..60`), Protect from Magic `/ 2`. If a pillar footprint lies on the line from her centre to the
    raider, the bolt goes to that pillar instead (only the first raider per pillar per volley; others behind it skipped).
  - **Pillar (OPEN-VZ4):** takes the raider's bolt roll as damage (the same `0..137` roll, before Protect from Magic). Hard: each pillar hit also drops **3** rubble
    tiles within 3 of the pillar; a raider exactly on a tile takes `0..35` and is frozen **5** ticks. At 0 HP it collapses: raiders within 2
    of its centre take `0..70` (Entry `0..50`, explicit, not halved), are teleported 1 tile away from it and frozen **5**; it becomes
    the collapsing form and despawns 3 ticks later.
  - **Shield break** (lethal hit in P1): death anim, the Dawnbringer is removed from every raider's inventory and worn slot ("The
    weapon falls apart in your hand as Verzik's shield is destroyed."), every remaining pillar collapses, HP held at 1 (no damage while in
    transition: `onLethal`), **4** ticks later P1->P2 form and `nextAttack = tick + 20` (counted from that tick, so **T+24** from the
    break; capture-values has first P2 attack T+20), at **+17** from the break the P2 form (full HP) at spawn + (1,1) with the start
    summon of combat Nylocas.
- **P2 (VZ3):** attack every **4** ticks. Per attack, in this order: at HP <= 35% (checked at an attack time) or every **7** attacks after
  that, the reds instead of an attack; with the reds active **75%** an attack is the blood spell; every **5th** attack (all P2 attacks
  counted, from 0) is the lightning ball; every **21st** attack (not once the reds started, and only if no Athanatos lives) also
  launches an Athanatos; otherwise urn bombs.
  - **Urn bombs / slam / stomp:** per raider by gap to her footprint: under her (gap 0) "There's nothing for you there!" and `1..82`
    (E/2) melee, immediate; beside her (gap 1) the melee anim, `0..45` (E/2) melee, thrown **3** tiles away from her centre
    (teleport) and frozen **5** ticks; otherwise a bomb at their current tile: projectile (start 0, `30 x (2d / 3)` cycles, min 30),
    lands `max(1, 2d / 3)` ticks later: a raider still on the tile takes `0..44` (E/2; Hard `0..53`, **OPEN-VZ12 code label**; this
    doc's VZ12 is the Nylocas homing item, a clash), Protect from Missiles `/ 2`, ranged. Hard leaves an acid pool on the tile for **14**
    ticks (docs say 16): `9..10` typeless per tick to a raider on it (the pool is Hard-only).
  - **Lightning:** counter +1; 4 hops: first from her centre to a random raider, then to a random raider other than the previous (one
    raider = the same, one hop); each hop's flight `max(1, 2 x max(1, d) / 3)` ticks (d from the previous point), the delays cumulate; a
    hop after the first whose line crosses her footprint hurts her `15..20` typeless when it arrives. Only the last raider takes
    damage: `0..48` (E/2), or `0..25` with insulated boots (item 7159) in every mode (so boots raise Entry's 24 cap to 25). Not
    scripted: bounce chat, Hard range +50%, the OPEN-VZ16 cap (the code has no cap logic at all and the damage is the same).
    After every 3rd lightning ball, outside the reds, 1/**4** chance (25%) of another Nylocas set unless an Athanatos is alive.
  - **Combat Nylocas (OPEN-VZ12 homing; code label OPEN-VZ14, a clash with this doc's VZ14):** one per raider at a random tile within 4
    of her centre, random style id (no script gives them a style rule or reflect, Hard included), scaled HP, each homing on a random
    raider, one tile a tick, up to **25** ticks. At gap <= 1 from its target: every raider with gap <= 1 takes `0..46` and gap <= 3
    `0..8` (E/2), typeless, then it despawns; at 25 ticks it despawns silently.
  - **Athanatos (VZ3, OPEN-VZ5):** random raider's tile; lands in `3 x (2d / 3)` ticks (min 1): raiders within 1 of the tile take `0..78`
    on the tile, `0..39` beside (E/2), typeless; then it spawns there (scaled HP) and heals her `9..10` every **4** ticks while both live.
    A hit on it by a raider with the serpentine helm, a spell starting "Smoke", or a weapon in the code's set (`TOXIC_BLOWPIPE_LOADED` and its ornament form, `TOXIC_SOTD_CHARGED`, `TOXIC_TOTS_CHARGED`, `TOXIC_TOTS_I_CHARGED`,
    Dragon / Abyssal dagger (p), (p+), (p++)) bursts it: `0..70` typeless to her and it despawns. Other
    hits just damage it; no regeneration, reduction or respawn delay (OPEN-VZ5).
  - **Reds (35%):** any live Matomenos is despawned first (her HP += its HP; Hard: raiders within 1 of it take its HP `/ 2`); then
    **2** Matomenos (**1** if one raider or fewer) at her left (-2) and right (+2 + size - 1) edges, scaled HP; she plays the heal anim,
    the next attack is in **12** ticks, and for **5** ticks every positive hit on her heals her instead (damage returned 0).
  - **Blood spell:** random raider's tile; anim; projectile 60 cycles; lands **2** ticks later: everyone within 1 of the tile rolls
    `0..45` (E/2); under Protect from Magic they take 0, others the roll; she heals `sum of the unprayed rolls / 2` (prayed targets
    still count toward the heal).
  - **P2 end** (lethal in P2): death anim, HP 1, **2** ticks later P2->P3 transition form, all minions dismissed; P3 form (full HP) **4**
    ticks after that, "Behold my true nature!", `nextAttack = tick + 12` counted from the transition start (**T+14** from the lethal
    hit; capture has T+12).
- **P3 (VZ4, VZ5, OPEN-VZ14 selection):** one target kept in `target` (random first), re-rolled (may repeat) when it has been out of
  melee reach (gap != 1) for **17** ticks; with no raiders she re-checks next tick. Attack every **7** ticks (**5** once enraged).
  `attacks` is the same counter as P2 and is **not reset** on entering P3 (the first special comes when the running total reaches a
  multiple of 5). Every 5th attack is a special in rotation (counter `specials`, order 0 Nylocas, 1 webs, 2 yellows, 3 green ball);
  Hard picks a new random target at each special (used from the next attack). Special 0 also does a normal attack that tick.
  - **Normal attack:** target at gap 1 -> melee anim, the target and every raider at gap 1 are hit `0..63` (E/2), melee, delay 1, no
    prayer step. Otherwise 50/50 magic or ranged for the whole room: projectile `min((53 magic / 62 ranged) + 15 d, 179)` cycles,
    every raider rolls `0..33` (`0..34` enraged; E/2), prayer for the style = `min(raw, 16)`, landing `end / 30` ticks. (Which attack she
    picks is the code's 50/50 and melee-if-adjacent: this doc's OPEN-VZ14 meaning.)
  - **Webs (OPEN-VZ10, code label OPEN-VZ11):** spin anim, she teleports to spawn + (-1,-1), invulnerable **12** ticks; 3 volleys at +1, +4, +7;
    per volley and raider **3** webs (1 if all raiders, more than one, share a tile): the first on the raider's tile, the others at a random
    walkable tile within 1; flight `gap / 2` ticks (min 1). A web NPC (cache HP) spawns; a raider on the tile is frozen **8** ticks; at 8 ticks if
    the web still exists and the raider is still on it, `30` typeless (E/2), skipped once per 8 ticks with aranea boots; the web despawns.
    A web killed earlier skips the damage but the freeze stays. Next attack in 12 + **10** ticks.
  - **Yellows (OPEN-VZ11 real meaning):** anim, invulnerable until the last landing. One random tile per raider within 6 of spawn + (2,2),
    safe-zone graphic. **14** ticks later (Hard 3 waves at 14, 17, 20, each with a new set) a raider standing exactly on a pool tile and
    alone on it gets the protection graphic; every other raider takes `0..80` (E/2) typeless. Next attack **21** ticks after the cast
    (Hard **27**).
  - **Green ball (OPEN-VZ6):** target gets "Verzik Vitur fires a powerful projectile in your direction..." (colour 0a721f), projectile 1598
    start 62 end 242, lands in **8** ticks. Holder: the first listed raider within 1 of the holder who has not yet held it gets "A powerful
    projectile bounces into your direction..." and receives it **2** ticks later, up to **3** bounces. When it stops: Normal/Entry/Hard solo:
    damage `baseHitpoints x 75 / 100` (74 at 99; E/2), or 0 if it had already bounced 3 times; Hard with 2+ raiders at the throw: kills the
    holder when unbounced, else `89 / 74 / 49` after 1 / 2 / 3 bounces. Next attack **12** ticks after.
  - **Enrage (VZ5, OPEN-VZ7):** `afterHit` at HP <= 20%: she says "I'm not finished with you just yet!" (every mode; the Entry text differs
    per the Entry page), speed 5, and a pending longer wait is cut to 5. From then each raider without a tornado gets one at her centre
    once its `tornadoDue` has passed: it walks to its raider; on contact `max(5, raider HP / 2)` typeless to the raider, she heals
    **3 x** that, it despawns and the next is due **16** ticks later (also runs while she waits for `nextAttack`).
  - **Hard second wind (VZ6):** at HP <= 5% once: "You think this is over?", +30% of max HP.
- **Death:** `VERZIK_PHASE3_DEATH_A` anim, minions dismissed; chest/vault in raids-tob.
- Not modelled: Hard combat-Nylocas style rotation, Hard lightning range/bounce, P1 Dawnbringer special detection, the OPEN-VZ16 flag.

## Open list

OPEN-VZ1 the `TOB_VERZIK_CREEPER` NPC role; OPEN-VZ2 4- / 3-player HP rounding; OPEN-VZ3 pillar HP by party size and mode;
OPEN-VZ4 hits a pillar takes before collapsing and the debris timing in Hard; OPEN-VZ5 Athanatos regeneration, damage
reduction, respawn delay and the poison hit; OPEN-VZ6 Hard green ball (kill threshold, damage table 89 / 74 / 49 and
bounce rules) and the Entry bounce count; OPEN-VZ7 enrage and second-wind shout text, and the 20% / 5% checks (once
only?); OPEN-VZ8 urn bomb damage (44 vs infobox 47) and Entry numbers; OPEN-VZ9 the P1 -> P2 and P2 -> P3 transition
timings, her starting tiles and the P3 target selection of the first target; OPEN-VZ10 web throw cadence, snap timer and
damage; OPEN-VZ11 yellow pool count / placement and the power-blast damage in Hard and Entry; OPEN-VZ12 Nylocas homing
speed, exact lifetime (15 s of movement) and explosion damage (46 vs 63); OPEN-VZ13 tornado speed and the exact
unspecified "heal for triple" rounding; OPEN-VZ14 which normal attack she picks (weights of melee / ranged / magic) and when
the melee is chosen; OPEN-VZ15 whether the P1 shield damage caps apply in Entry and Hard. OPEN-VZ16 lightning cap with one survivor; OPEN-VZ17 whether "Acid Bomb" is the green ball. **17 open items.**

## Conflicts between sources

1. Enrage text: "I'm not finished with you just yet!" (Strat) vs "I'm not done with you yet!" (Entry).
2. Urn bomb max 44 (Strat, Hard infobox) vs 47 (Normal infobox); lightning 48 (Strat) vs 50 bounce (infobox).
3. Combat Nylocas explosion: up to 46 (Strat) vs up to 63 (Page).
4. Hard green ball: "instantly kill if two or more players" (Hard page) vs "more than two" (Page); damage 89 / 74 / 49 stated next to the kill rule.
5. Lightning cap with one survivor: capped (update post U-May24) vs uncapped (Strat bug note) vs "supposed to be capped, but they couldn't figure it out for team raids" (Main changelog): OPEN-VZ16.
6. Matomenos summon: "two" (Page) vs "one when a single player remains" (23 May 2024); not contradictory.
7. Solo Entry HP 20% (Maiden / Bloat / Vasilias pages) vs cache base 25% (TB11); the Dawnbringer "2-4 specials" in Entry matches the cache base 300 (300 / 112.5 = 2.7).
8. Blood spell under Protect from Magic: Strat quotes Mod Ash "hits everyone near its target up to 45 (halved by prayer) ...
   if she just hit 1 person for up to 45, and they prayed, that'd be ~22, healing her for 11" and its own text says the prayer
   "will block the attack, but will still heal her for half the damage she would have dealt"; the Verzik page says Protect from
   Magic "negates all damage from the blood spells". The heal and damage under prayer are unresolved.
9. P3 projectile (ranged / magic) max hit: "up to 34 damage" (Verzik page text) vs 33 (Verzik infobox, Strat; Strat gives 34 only after the enrage).
10. Green ball damage: "up to 74% of their Hitpoints level" (Verzik page) vs 75% (Strat, which adds "74 at level 99").
11. Entry lightning ball frequency: "After every four bomb attacks" (Entry page) vs "Every five attacks" (Strat, and the every-fifth
    rule of VZ3). Every fifth is used for Normal and Hard; the Entry timing is unresolved.
