# Gear effects: weapon passives, set effects, bolts, immunities

Code: `content/combat` `GearEffects.kt` (accuracy / max hit), `PostRoll.kt` (hit roll and after, bolts, immunities),
`SetEffects.kt` (on landing / when hit, burns, soulreaper, blood moon), `Blowpipe.kt`, `GearContext.kt` (inputs, gear
totals). Verified against the Wiki DPS calculator by `DpsParityTest` (`gear-matrix.json`, tools/dps-matrix
`GearMatrix.test.ts`: 33 rows, pre-roll [min, max] and max attack roll).

Sources:
- OSRS Wiki item pages, `https://oldschool.runescape.wiki/w/<Item>` (named per rule).
- Wiki DPS calculator `weirdgloop/osrs-dps-calc`, `src/lib/PlayerVsNPCCalc.ts` (`getPlayerMax*AttackRoll`,
  `getPlayerMax*Hit`, `getAttackerDist`, `isImmune`), `src/lib/BaseCalc.ts` (set predicates), `src/lib/Equipment.ts`
  (`calculateEquipmentBonusesFromGear`) and `src/lib/dists/bolts.ts`. It encodes the Wiki's per-item rules with the
  order of operations; only numbers and order are used, no code is copied (as D-024).

Every multiplier is ⌊value × num / den⌋ in the order listed. "+x%" on a running value is value + ⌊value × x / 100⌋.
Effects that name an NPC attribute (demon, dragon, kalphite, undead, golem, leafy, rat, shade, vampyre, xerician,
flying, fiery) come from the NPC's Infobox `attributes` (npc-combat.md) and never apply to players.

## Gear totals (GE8)

- Ranged strength is the sum of two params: 12 (ammo, bow of faerdhinen, blowpipe) and 189 (twisted bow 20, scorching
  bow 40, necklace of anguish 5, Masori (f) 2, Ava's assembler 2): `cache-dump config obj 20997` / `19547`.
- Keris partisan of amascut: −22 strength, −50 stab outside the Tombs of Amascut (not modelled, so always).
- Dinh's (blazing) bulwark: strength + max(0, ⌊(stab + slash + crush + ranged defence − 800) / 12⌋ − 38).
- Dizana's quiver / blessed / max cape (charged) with arrows in a bow: +10 ranged attack, +1 ranged strength.
- Blowpipe: the loaded darts' ranged strength adds to the blowpipe's (Wiki "Toxic blowpipe").
- Magic damage: Virtus +3% per piece for Ancient Magicks (GE33); elite void mage +5% (GE32). Tumeken's shadow ×3
  attack and damage bonus, damage ≤ 100% (existing, PoweredStaves).

## Melee (GE1-GE19)

Effective level: ⌊level × prayer⌋ + stance + 8, then void ×11/10 (GE1). Roll = effective × (bonus + 64). Then:

1. One of (Wiki "Salve amulet", "Black mask", "Amulet of avarice"): avarice vs revenants 24/20; salve (e)/(ei) vs
   undead 6/5; salve / salve (i) 7/6; black mask / slayer helmet (any colour, (i) or not) on task 7/6.
2. GE2 obsidian set (helmet, platebody, platelegs) with a TzHaar weapon (Tzhaar-ket-em/-om/-om (t), Toktz-xil-ak/-ek,
   Toktz-mej-tal): + ⌊base roll / 10⌋ (accuracy) and + ⌊base max / 10⌋ (damage).
3. GE7 revenant weapons (Viggora's / ursine chainmace, charged, in the Wilderness, NPC targets only): 3/2.
4. Demonbane vs demons: arclight / emberlight +70%, silverlight / darklight +60% (max hit; accuracy too per the
   calculator, OPEN-GE10: Wiki prose names damage only for silverlight), bone / burning claws +5%.
5. GE11 dragons: dragon hunter lance 6/5 (accuracy and damage); dragon hunter wand bash 7/4 accuracy, 7/5 damage.
6. GE12 kalphites: keris partisan of breaching accuracy 133/100; every keris: damage 133/100 (amascut 115/100), and
   1 in 51 hits deal triple, with the message "You slip your weapon through a gap in the creature's chitin, landing a
   vicious blow." ("dagger" for the keris dagger) only above 10 damage (OPEN-GE5: exactly 10).
7. Vampyrebane accuracy: blisterwood flail / sickle 105/100; hallowed flail / sunspear 125/100; a silver weapon with
   Efaritay's aid 23/20. Golems: granite hammer 13/10, barronite mace damage 23/20.
8. Damage only: leaf-bladed battleaxe vs leafy 47/40; colossal blade + min(2 × size, 10); rat bone weapons vs rats +10.
9. GE13 Wiki "Inquisitor's armour" (22 July 2026: no set bonus, no mace bonus): crush only, (200 + h) / 200 with
   h = helm 1 + hauberk 2 + plateskirt 2.
10. GE16 Wiki "Osmumten's fang": min hit ⌊max × 3 / 20⌋, max reduced by it outside the special (15-85%); stab
    accuracy rolls the attack twice against one defence roll (either wins).
11. Obligator (charged) vs size 2 / 3+: ×120% / ×140%, not with crush (calculator).
12. GE18 Wiki "Soulreaper axe": + ⌊Strength × 6 × stacks / 100⌋ to the prayed level (before the stance). A stack is
    added after every attack (hit or miss) up to 5; idle, one decays every 50 ticks. No HP cost (22 July 2026);
    OPEN-GE2: whether a decayed stack heals.
13. Crystal blessing: max × (40 + crystal pieces) / 40.

## Ranged (GE20-GE27)

Effective level: ⌊level × prayer⌋ + stance + 8; void (normal or elite) ×11/10 accuracy; damage ×11/10, elite ×9/8.
Eclipse atlatl / Hunter's spear: damage uses Strength and melee strength bonus (and the melee salve / mask).

1. GE5 crystal bow / bow of faerdhinen (not inactive, not the Gauntlet's): accuracy (20 + p) / 20, damage
   (40 + p) / 40 before the salve / mask, p = helm 1, legs 2, body 3 (Wiki "Crystal armour").
2. One of: avarice 24/20; salve (ei) 6/5; salve (i) 7/6; imbued mask on task 23/20. Damage: when the imbued mask
   applies, the revenant (+10), dragon hunter crossbow (+5) and scorching bow (+6) bonuses join its numerator /20
   instead of multiplying.
3. GE20 Wiki "Twisted bow": M = min(250 (350 vs Xerician), max(Magic level, magic attack)); accuracy ×
   min(140, 140 + ⌊(3M − 10) / 100⌋ − ⌊(⌊3M / 10⌋ − 100)² / 100⌋)%, damage × min(250, 250 + ⌊(3M − 14) / 100⌋ −
   ⌊(⌊3M / 10⌋ − 140)² / 100⌋)%. Players: their Magic level / magic attack bonus (OPEN-GE11).
4. Revenant bows (Craw's / webweaver, charged, Wilderness, NPCs): 3/2. Dragon hunter crossbow vs dragons: accuracy
   13/10, damage 5/4. Scorching bow vs demons +30% accuracy and damage.
5. Tonalztics of Ralos: max − ⌊max / 4⌋; charged, two independently rolled hits (GE49).
6. GE51 Wiki "Dark bow": two arrows per attack, each rolled, the second landing at 1 + ⌊(2 + distance) / 3⌋.
7. GE48 Karil's set with the amulet of the damned: a quarter of accurate hits add a second splat of half the damage.

## Magic (GE30-GE39)

Effective level: ⌊level × prayer⌋ + stance + 9 (D-024), void ×29/20 (+45%). Roll = effective × (bonus + 64).

1. Additive %: avarice +20; salve (ei) +20, (i) +15; Efaritay's aid with silver ammo vs vampyres +15; smoke battlestaff
   / mystic smoke staff / twinflame staff with a standard spell +10 (GE31).
2. Dragons: dragon hunter crossbow 13/10, lance 6/5, wand 7/4; then the imbued mask 23/20.
3. Demonbane spells vs demons +20% (+40% marked, doubled with the purging staff); revenant sceptres 3/2 (NPCs).
4. GE34 Wiki "Tome of water": water spells +10% accuracy (+20% vs players), binds / curses +20%. (The calculator uses
   +20% for water spells too: SOURCE-CONFLICT, we follow the Wiki text, OPEN-GE12.)
5. Elemental weakness: + ⌊base roll × weakness / 100⌋.

Max hit: base (spell, Charge +10 with the god cape, chaos gauntlets +3 on bolts, matching elemental amulet +2: GE35)
× (1000 + magic damage tenths) / 1000, where the tenths add smoke staff 100, salve (ei) 200 / (i) 150, avarice 200,
elite void 50, Virtus 30 per piece, prayers ×10; imbued mask 23/20; dragons lance 6/5, wand 7/5, crossbow 5/4;
revenant sceptres 3/2; weakness + ⌊base × weakness / 100⌋; GE34 tomes (charged, standard spells of their element)
+10% vs NPCs, vs players fire +50%, water +20%, earth +10% (OPEN-GE6).

After the roll: GE36 Mark of Darkness + ⌊damage × 25 / 100⌋ (50 with the purging staff) for demonbane spells vs
demons; GE37 Ahrim's set with the damned amulet: a quarter of hits ×13/10; GE38 brimstone ring: a quarter of magic
attacks roll against 9/10 of the defence roll, message "Your attack ignored 10% of your opponent's magic defence.";
GE39 twinflame staff: standard Bolt / Blast / Wave spells add a hit of ⌊40%⌋ of the first, one tick later.

## Hit roll and after (GE40-GE48)

- GE40 scythe of vitur (any charged form): min(max(size, 1), 3) hits, hit i at max ≫ i, each rolled.
- GE42 dual macuahuitl: halves ⌊max / 2⌋ and the rest; the second fails if the first did, and lands a tick later
  ("spaced 1 game tick apart when attacking monsters"; players OPEN-GE4: the same).
- Two-hit weapons (Torag's hammers, sulphur blades, glacial temotli, earthbound tecpatl): two independent halves.
- GE43 Verac's set: a quarter of attacks hit regardless of accuracy and protection prayers, rolling 1..max + 1 vs
  monsters, 1..max vs players (OPEN-GE1: whether Protect from Melee's 40% still applies to a player).
- GE44-46 keris triple (above), Gadderhammer vs shades: 1 in 20 doubles, else +25%.
- GE41 Dharok's set: damage × (10000 + (max HP − HP) × max HP) / 10000.
- Berserker necklace with a TzHaar weapon: ×6/5.
- GE47 vampyrebane damage (Efaritay's aid ×11/10 first): blisterwood flail / hallowed flail / blisterwood stake
  125/100, sunspear 150/100, blisterwood sickle 23/20, Ivandis flail 6/5, rod of Ivandis (not tier 3) 11/10, other
  silver vs tier 1 11/10.

## Enchanted bolts (GE50-GE59)

Crossbows only. Chances (PvP / PvM, Wiki "Enchanted bolts", Mod Ash's table); the Kandarin hard diary adds a tenth of
the base chance. "Any" = works on a miss too.

| Bolt | PvP / PvM | Needs a hit | Effect |
|---|---|---|---|
| Opal | 5 / 5 | no | + ⌊Ranged / 10⌋ (zaryte crossbow /9) |
| Jade | 6 / 6 | no | knocked down 8 ticks; players: Protect from Magic blocks, Agility dodge −16% (1) .. 110% (99) (OPEN-GE9) |
| Pearl | 6 / 6 | no | + ⌊Ranged / 20⌋, / 15 vs fiery (players with a fire staff / fire cape); a water staff blocks it |
| Topaz | 4 / – | no | players only: Magic −1 |
| Sapphire | 5 / 25 | yes (OPEN-GE13) | PvP: drains ⌊Ranged / 20⌋ Prayer (≤ theirs), shooter +half; PvM: shooter + ⌊⌊NPC Ranged / 10⌋ / 8⌋ |
| Emerald | 54 / 55 | yes | poison 5 (6 with the zaryte crossbow) |
| Ruby | 11 / 6 | no | needs ≥ 10 HP; damage min(100, ⌊target HP × 20%⌋) (zcb 22%, cap 110); costs ⌊own HP / 10⌋ |
| Diamond | 5 / 10 | no | damage 0..⌊max × 115%⌋ (zcb 126%) |
| Dragonstone | 6 / 6 | yes | + ⌊Ranged × 20%⌋ (zcb 22%); not vs fiery / dragons, nor players with antifire, an anti-dragon shield or Protect from Magic |
| Onyx | 10 / 11 | yes | damage 0..⌊max × 120%⌋ (zcb 132%), heals ⌊damage / 4⌋; never vs undead |

## Set effects on landing and when hit (GE70-GE78)

- GE70 Guthan's set: a quarter of successful melee hits heal the damage (≤ max HP; +10 over with the damned amulet).
- GE71 Torag's set vs players: a quarter of successful hits take 20% run energy (OPEN-GE7: 2,000 of 10,000).
- GE72 Karil's set vs players: a quarter of hits lower Agility by ⌊Agility / 5⌋.
- GE73 Ahrim's set: a quarter of non-splashing spells lower Strength by 5.
- GE74 Dharok's set with the damned amulet: a quarter of hits taken recoil ⌊15%⌋ to the attacker (typeless).
- GE75 Torag's set with the damned amulet: Defence level +1% per 1% Hitpoints missing (OPEN-GE8: the amulet page says
  per Hitpoint point).
- GE76 Eclipse atlatl with the eclipse moon set: 20% of successful hits burn. Burn (Wiki "Burn"): 10 damage, 1 every
  4 ticks, at most 5 burns at once, typeless (OPEN-GE14: NPC burn immunity not in our NPC data).
- GE78 Blood moon set with the dual macuahuitl: a roll ≤ 32 of 0..99 (the lower of two when both splats hit) makes
  the next attack a tick sooner.

## Blowpipe (GE60-GE64)

Wiki "Toxic blowpipe": Zulrah's scales and one kind of unpoisoned dart, up to 16,383 each (kept with the player: an
obj has no state here, and the blowpipe only trades empty). Check / Unload / Uncharge ops; worn Check. Each shot uses
a scale 2 times in 3 and a dart like other ammo (Ava's devices keep theirs, 80% of the rest drop under the target).
GE62: attack speed +1 against players (4, a shortbow's). GE64: lost on a PvP death it drops empty with its scales and
darts. OPEN-GE3: exact Check / load messages.

## Salamanders and new magic weapons (GE90-GE92)

- GE90 Wiki "Salamander": every attack burns one of the salamander's own tar from the ammo slot (swamp lizard: guam,
  orange: marrentill, red: tarromin, black: harralander, tecu: irit); without it the attack doesn't happen
  (OPEN-GE16: the message).
- GE91 Blaze (magic): accuracy as a spell (+9), max ⌊(Magic × (s + 64) + 320) / 640⌋ with s = 56 / 59 / 77 / 92 / 104,
  then the magic damage chain; Magic and Defence experience (Wiki `Module:CombatStyles`: defensive magic).
- GE92 Zorya's tome: a powered staff, ⌊Magic / 3⌋ − 10, speed 3, 3 astral + 2 nature runes per charge, 20,000 charges,
  not against players (special: special-attacks.md SP50).

## Immunities (GE81-GE84)

- Flying (Wiki "Flying (attribute)"): no melee damage except polearms (and salamanders, calculator).
- Rat bone weapons only damage rats.
- Vampyres tier 3: only the Ivandis flail, blisterwood sickle / flail / stake, hallowed flail or sunspear; tier 2: silver
  weaponry or the rod of Ivandis, Efaritay's aid alone deals half.
- Typeless damage (burns, poison) is never stopped.

## Open

- OPEN-GE1 Verac's proc vs a player's Protect from Melee.
- OPEN-GE2 whether decayed Soulreaper stacks heal.
- OPEN-GE3 blowpipe message wording; whether darts alone make the pipe "loaded".
- OPEN-GE4 dual macuahuitl spacing against players.
- OPEN-GE5 keris message at exactly 10 damage.
- OPEN-GE6 tome of earth against players.
- OPEN-GE7 Torag's run energy drain base.
- OPEN-GE8 Torag's damned Defence: per % or per point.
- OPEN-GE9 jade bolts Agility curve reading.
- OPEN-GE10 silverlight / darklight accuracy.
- OPEN-GE11 twisted bow's input against players.
- OPEN-GE12 tome of water accuracy for water spells (Wiki 10% vs calculator 20%).
- OPEN-GE13 sapphire bolts needing a hit.
- OPEN-GE14 burn immunity.
- OPEN-GE15 scythe of vitur hitting more than one target in its arc.
- OPEN-GE16 the salamander's no-tar message.
