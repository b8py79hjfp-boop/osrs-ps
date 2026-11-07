# Combat magic

Every combat spell cast at a target from the standard, Ancient and Arceuus spellbooks, by hand or by autocast, and the
Arceuus combat buffs. Spell data: `docs/research/combat-spells.tsv` (OSRS Wiki spell pages), turned into
`content/combat/.../CombatSpellTable.kt` by `tools/wiki-import/combat_spells_kt.py`. Tele Block and Vengeance live in
content/pvp (`PvpCombat`). Formulas F12-F16 are in `combat-formulas.md`; multi-target rules MG1-MG2 in
`combat-multi.md`; freezes Z1-Z7 in `freeze.md`; poison in `poison.md`.

## Rules

| # | Rule | Source |
|---|---|---|
| CM1 | A combat spell is cast with "Cast" on a player or NPC (approach range 10). It needs the Magic level (boosted level, as every spell check), the runes (`SpellRunes`: staves, combination runes, plain runes), and for some spells a wielded staff: Iban Blast (Iban's staff, upgraded), Magic Dart (slayer's staff, (e), staff of the dead, toxic staff of the dead, staff of light, staff of balance), the god spells (their god staff or the staves listed in the TSV). Wrong staff: "You need to wield the correct staff to cast this spell." (OPEN-CM1 text). | Wiki spell pages |
| CM2 | Cast speed 5 ticks; the attack clock is set on the cast tick. Hit delay `1 + ⌊(1 + d) / 3⌋` (barrage: d to the target's south-west tile). The cast plays the caster anim (the `_STAFF` variant with a staff, else the plain one: RS Mod `castStrikeAnim`), the cast spotanim at height 92, a projectile (start height 43, end 31, start 51 cycles: RS Mod `magic_spell`) and on the hit tick the impact spotanim at height 124, or the splash spotanim on a miss. | combat-timeline.md, RS Mod `ElementalSpells` / `ProjAnimBuilds` |
| CM3 | Accuracy: the magic attack roll (F14, +9 for every cast as the DPS calculator) against the magic defence roll (F15/F16). Elemental weakness (Strike..Surge only) adds its percent to the attack roll. Demonbane spells add 20% (40% with Mark of Darkness, 80% with Mark of Darkness and a purging staff). | Wiki "Elemental weakness", "Demonbane spells" |
| CM4 | Max hit: the base max, then `⌊base × (1 + magic damage %)⌋`, then the elemental weakness term `+ ⌊base × weakness %⌋`; Demonbane with Mark of Darkness +25% (+50% with a purging staff) after the equipment bonus. Protect from Magic (players) applies last (F17). | Wiki "Elemental weakness", "Demonbane spells" |
| CM5 | Strike..Surge base max is the max of the highest spell of that class the caster's current Magic level unlocks: Strike 2/4/6/8 at 1/5/9/13, Bolt 9/10/11/12 at 17/23/29/35, Blast 13/14/15/16 at 41/47/53/59, Wave 17/18/19/20 at 62/65/70/75, Surge 21/22/23/24 at 81/85/90/95 (OPEN-CM2: current vs base level; RS Mod uses the current level). Chaos gauntlets: +3 on Bolt spells. | Wiki "Wind Strike" .. "Wind Surge", "Chaos gauntlets" |
| CM6 | Magic Dart: `⌊Magic / 10⌋ + 10`; with the slayer's staff (e) against the current Slayer task `⌊Magic / 6⌋ + 13`, using one of its charges on a hit. Crumble Undead (15) only on undead: "This spell only affects skeletons, zombies, ghosts and shades". Demonbane only on demons (OPEN-CM3 text: "This spell only works on demons."). Iban Blast 25, one charge of the staff per cast on an NPC, two on a player (120 / 2500 charges, OPEN-CM4: charges are kept per player). | Wiki "Magic Dart", "Crumble Undead", "Iban Blast", "Slayer's staff (e)" |
| CM7 | God spells (Saradomin Strike, Claws of Guthix, Flames of Zamorak): max 20, 30 while Charge is active (`SharedAttrs.MAGIC_CHARGED_UNTIL`) and the matching god cape (normal or imbued) is worn. On a hit: Saradomin drains 1 Prayer point; Guthix lowers Defence by 1 + 5%; Zamorak lowers Magic by 1 + 5%; the drains do not stack and do not reapply until the stat is back at its base level. They first work only in the Mage Arena until cast 100 times (OPEN-CM5: the arena counter is not modelled; they work everywhere). | Wiki god spell pages, "Charge" |
| CM8 | Xp: the spell's base xp on every cast (hit or splash; the binds give their lower splash value on a splash: Bind 30, Snare 60, Entangle 70), plus per damage: 2 Magic and 1.33 Hitpoints; defensive autocast instead 1.33 Magic, 1 Defence and 1.33 Hitpoints per damage. | Wiki "Autocast", "Bind", "Snare", "Entangle"; RS Mod `SpellAttackManager.attemptCast` |
| CM9 | Effects land with the hit and only on a successful hit. Stat drain spells (Confuse, Weaken, Curse, Vulnerability, Enfeeble, Stun) deal no damage and lower the stat by their percent of the base level; they can't be cast if the stat is already lowered: "Your foe's <stat> has already been weakened." (OPEN-CM6 text). | Wiki debuff spell pages |
| CM10 | Binds: Bind 8, Snare 16, Entangle 24 ticks, +2 per swampbark piece (helm, body, legs) worn by the caster; "The holding effect is beginning to fade." 5 ticks before the end for players (OPEN-CM7: the exact tick); then the freeze immunity of Z2. Ice spells freeze 8 / 16 / 24 / 32 ticks, +10% (rounded down) with any ancient sceptre. | Wiki "Bind", "Snare", "Entangle", "Ancient sceptre" |
| CM11 | Smoke: 1 in 8 to poison on a hit at severity 10 (Rush, Burst) or 20 (Blitz, Barrage), +10% with an ancient sceptre. Shadow: lowers Attack by 10% (Rush, Burst) or 15% (Blitz, Barrage) of the base level if not already lowered. Blood: heals the caster 25% of the rolled damage (+2% per bloodbark piece, then ×1.1 with an ancient sceptre), not above the maximum Hitpoints; "You drain some of your opponent's health." | Wiki "Smoke spells", "Shadow spells", "Blood spells" |
| CM12 | Grasps: on a hit, a 10 / 25 / 50% chance (doubled by Mark of Darkness) to bind for 2 / 3 / 4 ticks; a target under Ward of Arceuus is bound for 1 tick. | Wiki "Grasp spells", "Ward of Arceuus" |
| CM13 | Burst and barrage hit up to 9 targets in multi-combat (MG1); each extra target rolls its own accuracy, damage and effect. | combat-multi.md |
| CM14 | Autocast: the combat tab's spell buttons open the autocast list (normal or defensive). With a spell chosen the player's attacks cast it (range 10, speed 5, `Stance.SPELL`); without the runes: "You do not have enough runes to cast this spell." and the attack stops. The choice is dropped when a weapon that can't autocast it is wielded. Weapons: every staff, battlestaff, wand and crozier (cache obj category 1 without its own attack range: powered staves have one) autocasts the 20 elemental spells; the Wiki tables list who autocasts the Ancient, Arceuus and special spells (`Autocast.kt`). | Wiki "Autocast" |
| CM15 | A manual cast while autocast is set continues the fight with the autocast spell; otherwise the fight stops after the cast. | RS Mod `continueCombatIfAutocast` |
| CM16 | Mark of Darkness (59, cosmic 1 + soul 1, 70 xp, cooldown 10 ticks): lasts 3 × base Magic ticks (×5 with a purging staff). "You have placed a Mark of Darkness upon yourself", 10 ticks before the end "Your Mark of Darkness is about to run out.", then "Your Mark of Darkness has faded away." | Wiki "Mark of Darkness" (27 August 2025 duration) |
| CM17 | Ward of Arceuus (73, cosmic 1 + nature 2 + soul 4, 83 xp, cooldown 50 ticks): lasts Magic level ticks; immune to corruption, grasps bind for 1 tick, demons deal 10% less damage (the reduction is ⌊damage / 10⌋). | Wiki "Ward of Arceuus" |
| CM18 | Lesser / Greater Corruption (64 / 85; death 1 + soul 2 / blood 1 + soul 3; 75 / 95 xp; cooldown 50 ticks): the caster's next successful hit on a player has a 50% chance (100% with Mark of Darkness) to corrupt: three Prayer drains 10 ticks apart (5 with Mark of Darkness) of 1, 2, 3 (lesser) or 2, 4, 6 (greater). Not on a corrupted player or one under Ward of Arceuus (the buff then waits). | Wiki "Lesser Corruption", "Greater Corruption", "Corruption (status)" |
| CM19 | Death Charge (80; cooldown 100 ticks): for 100 ticks, the caster's next NPC kill within 16 tiles restores 15% special attack energy, once; not if the caster was in PvP combat in the last 10 ticks. | Wiki "Death Charge" |

## Tick timeline (manual cast)

- Tick T: Cast reaches the server; within 10 tiles the ap script runs: checks (CM1), runes taken, attack clock T+5,
  anim, cast spotanim, projectile, accuracy and damage rolled, base xp given (CM8), hit queued for T + delay.
- Tick T + delay: the hit lands with the impact spotanim; the effect applies (CM9-CM12); damage xp was given at T.
- Autocast continues at T+5 if the target is still in range.

## OPEN

- **OPEN-CM1:** message for casting without the required staff (Wiki gives none).
- **OPEN-CM2:** whether the Strike..Surge ladder uses the current or the base Magic level (Wiki "Magic Dart" counts
  boosts; RS Mod uses the current level, as here).
- **OPEN-CM3:** the Demonbane message on a non-demon.
- **OPEN-CM4:** Iban's staff and the slayer's staff (e) keep charges per item; here they are per player (one saved
  counter each), starting full.
- **OPEN-CM5:** the Mage Arena 100-cast unlock of the god spells (varp `magearena_charge` 272 is a likely counter).
- **OPEN-CM6:** the message when a drain spell's stat is already lowered.
- **OPEN-CM7:** the tick of "The holding effect is beginning to fade."
- **OPEN-CM8:** the corruption chat messages ("a message ... for both the caster and target"; no text on the Wiki).
