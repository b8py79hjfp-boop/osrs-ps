# Run energy

Engine `move/RunEnergy.kt` (every tick, after movement), content `content/combat/.../RunEffects.kt`.
Source: OSRS Wiki "Energy", "Stamina potion", "Graceful outfit", "Ring of endurance".

## Rules

| # | Rule | Source |
|---|---|---|
| RE1 | Energy is 0..10,000 units (100% = 10,000; the orb shows ⌊units / 100⌋). Run can be switched on only with ≥ 100 units. With 0 the player walks and run switches off (varp `option_run` 173 = 0). | Wiki "Energy" |
| RE2 | A tick in which the player ran (2 steps) drains `⌊60 + 67 × clamp(weight kg, 0, 64) / 64⌋ × (1 − Agility / 300)` units (current Agility level), never below 0. Weight = backpack + equipment obj weights (cache, grams; stackables 0; graceful is negative). A single step (walk, or run to an adjacent tile) does not drain. | Wiki "Energy" |
| RE3 | Any other tick restores `⌊Agility / 10⌋ + 15` units, except while the player is delayed (agility obstacles and other delayed actions: "run energy will not regenerate while ... crossing an agility obstacle"). | Wiki "Energy" |
| RE4 | Stamina potion: +20% energy per dose; drain × 0.3 for 2 minutes (200 ticks); another dose resets the timer. Varbits `stamina_active` 25 and `stamina_duration` 24 (10-tick units) drive the orb. | Wiki "Stamina potion" |
| RE5 | Graceful: restore +3% per hood / cape / gloves / boots, +4% per top / legs, +10% more for the full set (30%); applied as `⌊R × (1 + boost)⌋`. | Wiki "Graceful outfit", "Energy" |
| RE6 | Ring of endurance (≥ 500 charges): drain × 0.85, not stacking with stamina. **OPEN-RE1:** the ring's charges are not implemented, so this is not applied. | Wiki "Ring of endurance" |

## OPEN

- **OPEN-RE1:** ring of endurance (above).
- **OPEN-RE2:** whether the drain units are floored before or after the Agility factor (the Wiki marks it unverified); here the base is floored, then multiplied with integer division.
- **OPEN-RE3:** the weight shown in the equipment tab is not sent (the client computes it); full restores (death, quest completion, pools) are per content.
