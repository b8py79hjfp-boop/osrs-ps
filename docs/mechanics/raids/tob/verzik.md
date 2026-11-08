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
  [nylocas.md](nylocas.md)). Quest NPC 8250 is the human Verzik, not the fight.
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
  always hits); Protect from Melee does nothing against either. Every **fifth attack** she fires a **lightning ball**
  that bounces between players up to **4 times** in random order; if it passes through her she takes **15-20**
  (no accuracy roll); a player hit alone or on the fourth bounce takes up to **48** (25 with insulated boots, 23 Jun 2021
  fix for Hard). Normal bug: with only one player alive in a larger party the damage is **not capped** (23 May 2024:
  the Matomenos change was released, the lightning cap "couldn't be figured out for team raids").
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
    on a 3 x 3 around one player for up to **45**, healing her for **50% of the total damage**; Protect from Magic blocks the
    damage but she still heals half of what she would have dealt, and it is calculated during the animation. She kills the
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
     target; the ball deals **75% of the player's Hitpoints level** (74 at 99) unless **bounced** to a different adjacent
     player, which must repeat until it dissipates (three bounces in the Entry text); a ball that finds nobody in range or
     lands on the same player twice explodes for the full damage. Hard: it **kills outright** if the ball has not been
     bounced and two or more players are present (the Page says "more than two"), damage figures 89 / 74 / 49 for 1 / 2 / 3
     bounces, and Redemption triggered at the last moment cancels it; with one player left it works as Normal (**OPEN-VZ6**).
     A chat message is shown when the Hard ball bounces to you (9 Jun 2021).
- **VZ5 Enrage at 20% or lower:** she shouts (Strat: "I'm not finished with you just yet!"; Entry page: "I'm not done with you
  yet!"; **conflict**, **OPEN-VZ7**), the projectile max hit rises to **34**, her attack speed goes from 7 to **5**, and a
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

1 Oct 2025 quick-start (U-Uplift); 25 Jun 2025 P3 fire weakness; 23 May 2024 one Matomenos with one player left and a lightning
cap on that single survivor; 9 Aug 2023 magic defence from Defence (clarification); 23 Jun 2021 Nylocas explosion damage
no longer scales up in low scales, insulated boots reduce the Hard lightning, non-melee Hard Nylocas only hit by their
style, wave-20 fix, raid timer starts at the Maiden; 9 Jun 2021 hotfix (tornado minimum 5, Story-mode P3 +200 HP and P2 -200
HP and tweaks, Hard lightning damage and range, Nylocas detonation fix, bounce chat message); 4 Jun 2021 hotfix (green ball
damage up, no tick-eating the yellow in Hard); 3 Jun 2021 modes added; 12 Mar 2020 / 16 May 2019 / 2 May 2019 / 2 Aug 2018
fixes (web damage one cycle earlier, correct movement, damage computed on hit in P3, re-target non-caged players).

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
the melee is chosen; OPEN-VZ15 whether the P1 shield damage caps apply in Entry and Hard. **15 open items.**

## Conflicts between sources

1. Enrage text: "I'm not finished with you just yet!" (Strat) vs "I'm not done with you yet!" (Entry).
2. Urn bomb max 44 (Strat, Hard infobox) vs 47 (Normal infobox); lightning 48 (Strat) vs 50 bounce (infobox).
3. Combat Nylocas explosion: up to 46 (Strat) vs up to 63 (Page).
4. Hard green ball: "instantly kill if two or more players" (Hard page) vs "more than two" (Page); damage 89 / 74 / 49 stated next to the kill rule.
5. Lightning cap with one survivor: capped per update post vs uncapped per Strat's bug note and the wiki changelog.
6. Matomenos summon: "two" (Page) vs "one when a single player remains" (23 May 2024); not contradictory.
7. Solo Entry HP 20% (Maiden / Bloat / Vasilias pages) vs cache base 25% (TB11); the Dawnbringer "2-4 specials" in Entry matches the cache base 300 (300 / 112.5 = 2.7).
