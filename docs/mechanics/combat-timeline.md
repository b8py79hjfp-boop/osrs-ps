# Combat timeline

Code: `engine/src/main/kotlin/tp/engine/combat/*`, `content/combat`. Sources: OSRS Wiki pages fetched as raw wikitext
on 2026-11-06 (file:line refers to those copies): Hit delay (HD, https://oldschool.runescape.wiki/w/Hit_delay),
Attack speed (AS, /w/Attack_speed), Prayer (PR, /w/Prayer), Auto Retaliate (AR, /w/Auto_Retaliate), Flinching (FL,
/w/Flinching), Special attacks (SA, /w/Special_attacks), Granite maul (GM, /w/Granite_maul); brief §8.

## The model (brief §8)

Every attack is: **attack tick** T (in the attacker's turn: attack clock ≤ T, target in range → accuracy and damage
rolled, protection prayers checked, attack clock set to T + speed) → the hit is **queued on the defender as a strong
script** with delay = hit delay → **application tick** (HP change, hitsplat, retaliation, death queue) in the
defender's own turn.

The "+1 when the defender is processed first" is not added by hand: the defender's queue for tick T has already run
if the defender was processed earlier, so the hit lands at T + delay + 1 (HD:6 "If the entity receiving the hit is
processed earlier than the entity dealing the hit, then the hit will be delayed by an additional one tick"; HD:8 NPCs
are processed before players; HD:10 PID decides in PvP). Queue rule: scripts.md Q6 does not apply (the hit is queued
in the attacker's turn, not during the defender's queue processing).

## Per attack type

| Attack | Roll tick | Protection check | Application tick (defender after attacker / before) | Source |
|---|---|---|---|---|
| Melee | T | T (prayers active in T, see P1) | T / T+1 | HD:1, HD:13 |
| Bows, crossbows, atlatl | T | T | T+d / T+d+1, d = 1 + ⌊(3 + dist) / 6⌋ | HD:54 |
| Dark bow 2nd arrow | T | T | d = 1 + ⌊(2 + dist) / 3⌋ | HD:114 |
| Thrown, chinchompas, blowpipe | T | T | d = 1 + ⌊dist / 6⌋ | HD:156 |
| Ballistae | T | T | d = 2 (dist 1–4), 3 (5–10) | HD:165-193 |
| Magic (spells, powered staves) | T | T | d = 1 + ⌊(1 + dist) / 3⌋ | HD:298 |
| Tumeken's shadow | T | T | d = 2 + ⌊(1 + dist) / 3⌋ | HD:342 |
| DFS/DFW/AWS specials | T | T | d = 2 + ⌊(4 + dist) / 6⌋ | HD:417 |
| ACB/ZCB spec, demonbane & Nightmare staff specs | T | T | d = 2 | HD:56, HD:300 |
| Grasp spells | T | T | d = 1 | HD:300 |

Distance: Chebyshev, edge to edge (HD:3). Barrage spells and thralls measure to the defender's south-west tile (HD:3).

## Rules

| # | Rule | Source |
|---|---|---|
| C1 | Attack when the attack clock ≤ now and the target is in range; then clock = now + weapon speed (rapid −1). The clock is global across weapons ("a 7 tick weapon ... then a 4 tick weapon ... wait 7 ticks"). | AS:8-10, brief §8 |
| C2 | Protection prayers are checked in the attacker's attack tick ("the tick your enemy attacks ... not necessarily the tick in which you take damage"). **OPEN-C3:** PR:508 is flicking advice about NPCs; no source fixes the PvP check tick for ranged/magic. Implemented: checked on the roll tick for every style. | PR:508 |
| C3 | Prayer state used by an attack: see prayers.md P1 (SOURCE-CONFLICT, decided by brief §9 3e). Prayer drain skips prayers on the tick they are activated, so a one-tick flick drains nothing. | PR:538, brief §9 3e |
| C4 | Auto retaliate: the player retaliates "1 tick after" being attacked and the attack delay is set to ⌈speed / 2⌉. **OPEN-C4:** the two numbers aren't reconciled by the source; implemented: on the hit's application the attack clock becomes max(clock, now + ⌈speed / 2⌉) and the target is set, so the swing happens when that clock allows. | AR:6 |
| C5 | NPC retaliation delay: ⌊speed / 2⌋ (FL:5) vs "halved, plus 1, rounded up" (AS:387). **SOURCE-CONFLICT / OPEN-C5:** equal for even speeds once the +1 processing-order delay of hits on NPCs is counted, different for odd speeds. Implemented: ⌊speed / 2⌋ from the hit's application. | FL:5, AS:387 |
| C8 | Attack speeds: weapon param 14 (cache); combat spells 5; powered staves 4; blowpipe +1 in PvP. Rapid −1. | AS:6, AS:271, combat-data.md |
| C9 | Eating: if the attack clock is in the future (weapon not ready), add the food's attack delay; if the weapon is ready (clock ≤ now), nothing ("If your weapon is ready to attack again then eating does not add any new delay"). | FF:11, brief §8 |
| C10 | Special energy is deducted on the attack tick when the spec is performed; a weapon switch in the same tick's input applies first (input precedes the player's turn; tick.md). Inventory item actions interrupt the current interaction (interactions.md I7); the attack click that follows sets the new one. | SA:12, interactions.md I7, tick.md |
| C6 | Granite maul special: no attack cooldown; the next attack can follow on the next tick. | GM:89 |
| C7 | Damage cap 200 for players. | combat-formulas.md F20 |

## Hit-delay exceptions not in the table

Blowpipe special: 2 at distance 4–5 (HD:132). Dragon claws special hit timing: not sourced (**OPEN-C6**). Thralls and
salamanders: HD:13, HD:197 (not implemented).

## OPEN

- **OPEN-C1:** visual timing (projectile and spot-anim client-cycle delays) is chosen so the visual lands on the
  application tick (brief §8); exact Jagex cycle values per weapon are not documented.
- **OPEN-C2:** whether a hit queued on a dead/teleported defender is discarded or still applied. Implemented:
  discarded if the defender died; applied otherwise.
