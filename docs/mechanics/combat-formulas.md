# Combat formulas (accuracy, max hit)

Code: `content/combat/src/main/kotlin/tp/content/combat/formula/*`. Sources: OSRS Wiki pages, fetched as raw
wikitext from oldschool.runescape.wiki on 2026-11-06 (line numbers refer to those files):

| Key | Page |
|---|---|
| MM | https://oldschool.runescape.wiki/w/Maximum_melee_hit |
| MR | https://oldschool.runescape.wiki/w/Maximum_ranged_hit |
| MA | https://oldschool.runescape.wiki/w/Maximum_magic_hit |
| DM / DR / DA | https://oldschool.runescape.wiki/w/Damage_per_second/Melee, /Ranged, /Magic |
| CL | https://oldschool.runescape.wiki/w/Combat_level |
| CO | https://oldschool.runescape.wiki/w/Combat_Options |
| AT | https://oldschool.runescape.wiki/w/Attack_types |
| PP | https://oldschool.runescape.wiki/w/Protection_prayers |
| SA | https://oldschool.runescape.wiki/w/Special_attacks |

`⌊⌋` = floor. Levels are the current (boosted/drained) levels, except F21 (base levels).

## Melee

| # | Formula | Source |
|---|---|---|
| F1 | Effective strength = ⌊(⌊(Str) × Prayer⌋ + Style + 8) × Void⌋. Style: aggressive +3, controlled +1, else 0. Void (melee) 1.1. | MM:16, MM:62-74, DM:9-21 |
| F2 | Strength prayers: Burst of Strength 1.05, Superhuman 1.10, Ultimate 1.15, Chivalry 1.18, Piety 1.23. | MM:42-59 |
| F3 | Max hit = ⌊(EffStr × (StrBonus + 64) + 320) / 640⌋, then gear multipliers (floor), then special multiplier: ⌊⌊Base⌋ × Special⌋. | DM:53-65, MM:81-90 |
| F4 | Effective attack = ⌊(⌊Atk × Prayer⌋ + Style + 8) × Void⌋. Style: accurate +3, controlled +1. Prayers: Clarity 1.05, Improved Reflexes 1.10, Incredible Reflexes 1.15, Chivalry 1.15, Piety 1.20. | DM:71-106 |
| F5 | Attack roll = ⌊EffAtk × (EquipStab/Slash/Crush + 64) × Gear⌋. | DM:110-116 |
| F6 | Player effective defence = ⌊Def × PrayerDef⌋ + Style + 8 (defensive +3, controlled +1, longrange +3: CO:39). Defence roll = EffDef × (DefBonus + 64). | DM:121-139, CO:39 |
| F7 | NPC defence roll = (Def + 9) × (StyleDef + 64). | DM:133 |

## Ranged

| # | Formula | Source |
|---|---|---|
| F8 | Effective ranged strength = ⌊(⌊Rng × Prayer⌋ + Style + 8) × Void⌋; accurate +3. Prayers: Sharp Eye 1.05, Hawk Eye 1.10, Eagle Eye 1.15, Deadeye 1.18, Rigour 1.23. | MR:19-46 |
| F9 | Max hit = ⌊⌊0.5 + EffRS × (RangedStr + 64) / 640⌋ × Gear⌋, then ⌊Base × Special⌋. | MR:51-68 |
| F10 | Effective ranged attack = ⌊(⌊Rng × Prayer⌋ + Style + 8) × Void⌋; Rigour attack 1.20. Attack roll = ⌊EffAtk × (RangedAtk + 64) × Gear⌋. | DR:13-47 |
| F11 | Rapid style: attack speed −1 tick. Longrange: +2 attack range (max 10), +3 Defence. | DR:69, CO:37-39 |

## Magic

| # | Formula | Source |
|---|---|---|
| F12 | Spell base max hits: Fire Strike 8, Fire Bolt 12, Fire Blast 16, Fire Wave 20, Fire Surge 24 (Wind/Water/Earth scale with Magic level, MA:30, MA:42); Ice Rush 16, Ice Burst 22, Ice Blitz 26, Ice Barrage 30. | MA:48-165 |
| F13 | Damage = ⌊BaseMax × (1 + MagicDamage% + Void 5% + Prayer)⌋ (+ the other gear terms in MA:19-25). Prayer: Mystic Lore +1%, Mystic Might +2%, Mystic Vigour +3%, Augury +4%. | MA:19, MA:187-197 |
| F14 | Effective magic = ⌊Mag × Prayer⌋ + Style + 8 (+9 for plain casts: SOURCE-CONFLICT below; void: OPEN-F4) (accurate powered staff +3, longrange +1). Prayers: Mystic Will 1.05, Mystic Lore 1.10, Mystic Might 1.15, Mystic Vigour 1.18, Augury 1.25. Attack roll = ⌊EffMag × (MagicAtk + 64)⌋. | DA:20-64 |
| F15 | NPC magic defence roll = (9 + MagicLevel) × (MagicDef + 64). | DA:68 |
| F16 | Player magic defence uses "70% of the opponent's Magic level, and 30% of the opponent's Defence level". **OPEN-F1:** everything else (rounding, prayers, style, +8, × (MagicDef + 64)) is ours; implemented as ⌊0.7 × ⌊Mag × MagPrayer⌋⌋ + ⌊0.3 × ⌊Def × DefPrayer⌋⌋ + Style + 8, × (MagicDef + 64). | AT:37 |

## Shared

| # | Formula | Source |
|---|---|---|
| F17 | Hit chance: if A > D, 1 − (D + 2) / (2 (A + 1)); else A / (2 (D + 1)). Server form: roll a = uniform[0, A], d = uniform[0, D]; hit if a > d. | DM:146-160 |
| F18 | Damage on a hit: uniform [0, Max]. Magic: a 0 becomes 1 (MA:221). Melee: "a 0 on a successful attack will be changed to a 1" (DM:155). **OPEN-F2:** the same for ranged; implemented the same. | MA:221, DM:155 |
| F19 | Protection prayers reduce player damage by 40%, NPC damage by 100%; typeless attacks ignore them. DM:64 applies ×6/10 to the max hit (step two). **OPEN-F3:** whether per roll or on the max hit, and rounding; implemented as DM:64 — the max hit is ⌊max × 6/10⌋ and the roll is uniform under it. | PP:27-29, DM:64 |
| F20 | Player damage cap 200. | MM:4 |
| F21 | Combat level = ⌊¼ (Def + HP + ⌊Prayer / 2⌋) + max(13/40 (Att + Str), 13/40 ⌊1.5 Rng⌋, 13/40 ⌊1.5 Mag⌋)⌋. | CL:37-57 |
| F22 | Special attacks roll against a fixed defence type (e.g. dragon dagger: slash). Attack-roll modifier +100% doubles the roll. | AT:5, SA:37, SA:177 |
| F23 | Salve amulet against undead NPCs (Infobox `attributes` undead): salve ×7/6 melee; (i) ×7/6 melee and ranged, ×23/20 (+15%) magic; (e) ×6/5 melee; (ei) ×6/5 all styles. Applied as ⌊roll × n/d⌋ to the attack roll and ⌊max × n/d⌋ to the max hit. Does not stack with F24; the salve wins. OPEN-F5: for magic the +15% / +20% is applied to the finished max hit, not added to the magic damage percent. | Wiki "Salve amulet", "(i)", "(e)", "(ei)" (Mod Ash: "It's 1/6") |
| F24 | Black mask / Slayer helmet against the player's current Slayer task: ×7/6 melee; imbued (i) also ×23/20 ranged and magic. Konar / Krystilia area limits and the awakened bosses are not modelled (OPEN-F6). | Wiki "Black mask", "Slayer helmet" (Mod Ash: "7/6") |

## Special attacks used in golden scenarios

| Weapon | Attack roll | Damage | Source |
|---|---|---|---|
| Dragon dagger | ×1.15, two hits | ×1.15 each | SA:2181-2193, MM:109 |
| Dragon claws | up to 4 rolls; first success decides | 100/75/50/25% by roll index | SA:2144-2165 |
| Armadyl godsword | ×2 | ×1.375 | SA:1281-1293 |
| Zamorak godsword | ×2 | ×1.1; freeze on hit | SA:2565-2578 |
| Granite maul | — | — (extra instant attack, 60% / ornate 50%) | SA:2241-2251 |
| Dark bow | — | ×1.3 (dragon arrows ×1.5) | SA:2119-2127 |

## SOURCE-CONFLICT (decided)

- Magic accuracy for plain spell casts (no stance bonus): DA:20-25 gives ⌊Mag × Prayer⌋ + 8; the Wiki DPS calculator
  uses + 9. Accurate (8+3 vs 9+2) and longrange (8+1 vs 9) agree. Brief §8 validates against the calculator, so plain
  casts use +9 (D-024; DPS parity matrix).
- Magic void: DA:22 applies ×1.45 before the stance/+8 terms; the calculator applies ⌊× 29/20⌋ after them. Void isn't
  modelled yet (OPEN-F4); when it is, follow the calculator.

## Notes

- 5% strength prayers give at least +1 level (MM:43 "Always boost ... by at least 1"; the calculator adds 1 at level
  ≤ 20 for Burst of Strength and Sharp Eye). Not a conflict.

- Elite ranged void: 1.125 for strength (MR:23), 1.1 for the attack roll (DR:17, DR:38). Not a conflict.
- Slayer/salve: DM floors before the target bonus (DM:61-62); MM floors then multiplies — equivalent.
- Dragon claws: first-roll −1 flat damage and the all-miss 0-or-2 rule (SA:2158, SA:2165). Dark bow special minimum
  5 (8 with dragon arrows) (SA:2127).
