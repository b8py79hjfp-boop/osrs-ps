# Slayer equipment (combat effects)

`content/combat/.../SlayerGear.kt`; hooks in `Combat.kt` (NPC attacks, `applyHit`) and use-item-on-NPC handlers.
Sources: OSRS Wiki "Slayer equipment" and each monster / item page (ids from their infoboxes). Research notes:
the Wiki gives exact numbers only for some rules; the others are OPEN below rather than guessed.

## Rules

| # | Rule | Source |
|---|---|---|
| SE1 | Any Slayer helmet (all 15 colours and their 3 imbued versions) counts as earmuffs, nose peg, facemask, spiny helmet and reinforced goggles. | Wiki "Slayer helmet" |
| SE2 | Cave horrors (1047-1051, superior 7401) against a player without a witchwood icon always scream for ⌊base Hitpoints / 10⌋; Protect from Melee blocks it. | Wiki "Cave horror" |
| SE3 | Dust devils (423, 11238, 7249, superior 7404) without a facemask always hit 16; Attack, Strength, Ranged, Magic drained to 0, Agility, Defence, Prayer halved. | Wiki "Dust devil" |
| SE4 | Cockatrice (419, 13030, superior 7393) without a mirror shield or V's shield: the gaze always hits 11; Protect from Melee blocks it. | Wiki "Cockatrice" |
| SE5 | Sourhogs (10435) without reinforced goggles: 20-30 damage, current Attack and Defence lowered by 90%, "Argh! My eyes!". | Wiki "Sourhog" |
| SE6 | Killerwatts (469/470) without insulated boots: max hit 15 (infobox). | Wiki "Killerwatt" |
| SE7 | Wall beasts (476) without a spiny helmet: max hit 18; banshees (414) without earmuffs: max hit 6 (infoboxes). | Wiki "Wall beast", "Banshee" |
| SE8 | Fever spiders (626) without Slayer gloves: always ⌊base Hitpoints / 8⌋ (12.5%). | Wiki "Fever spider", "Slayer gloves" |
| SE9 | Turoth (427-430, 10397) and kurask (410, 411, 7405) take damage only from leaf-bladed weapons (4158, 11902, 20727), broad ammunition (4160, 33601, 11875, 21316) or Magic Dart; harpie bug swarms (464) only with a lit bug lantern (7053) worn; warped creatures (12490-12504) only with a crystal chime in the backpack. Typeless damage (burn, poison, venom) always applies. | Wiki "Leafy (attribute)", "Harpie Bug Swarm", "Crystal chime" |
| SE10 | Finishing items: gargoyles (412, 413, 1543) with a rock hammer, granite hammer (kept) or rock thrownhammer below 10; rockslugs (421, 422, 14423) with a bag of salt below 5 ("The rockslug shrivels up and dies."); desert lizards (458-463, 12003, 12004) with an ice cooler below 5 ("The lizard shudders and collapses from the freezing water."); zygomites (537, 1024, 7797) with fungicide spray below 8. Salt and coolers are used up even when too early. Hits never kill them; with the Slayer auto-kill unlock (varbits 4027-4030) and the item carried they die at the threshold. | Wiki monster and item pages, "Slayer Rewards" |

## OPEN

- **OPEN-SE1:** stat drains of banshees, aberrant / deviant spectres (and their always-hit special), basilisks, basilisk knights, cockatrice, smoke devils: the Wiki gives no amounts; only damage is applied where given.
- **OPEN-SE2:** environmental damage: Smoke Dungeon (20 every 20 ticks, not below 1), Smoke Devil Dungeon (interval unknown), Karuulm floor (4 per tick without boots of stone; area bounds and the warning text are not on the Wiki). Not implemented.
- **OPEN-SE3:** finishing-item monsters stop at 1 hitpoint here; in game they sit at 0. Thresholds differ between Wiki pages (gargoyles 8/9/10, rockslugs ≤4 vs <4, zygomites <8 vs ≤8): the widest reading is used. Superiors (marble gargoyle, giant rockslug, mutated warped creatures) are not included. Messages for gargoyles and zygomites are not on the Wiki. The fungicide spray's charges are not used up.
- **OPEN-SE4:** lizardman shaman acid vs Shayzien armour (per-piece reduction unclear), the leaf-bladed battleaxe's +17.5%, Magic Dart's staff requirement is enforced by the spell itself (combat-magic.md CM1), fever spider disease, the Tortugan shield vs gryphons, venator and warped terrorbird earmuff effects, rune dragon electricity.
