# Poison and venom (rev 242)

Code: `content/combat/.../Poison.kt` (`Poison`, `Toxic`), antipoison potions in `Consumables.kt`.

Sources (OSRS Wiki raw wikitext, 2026-11-06): Poison (PO), Venom (VE), Serpentine helm (SH), Toxic blowpipe (TB).
Client encoding: RuneLite 1.13.6 `PoisonPlugin` and `TimersAndBuffsPlugin` (RL), which read varp `poison` (102) the
way the client's HP orb does.

| # | Rule | Source |
|---|---|---|
| PO1 | State lives in varp `poison` (102): 1..999,999 = poison severity; ≥ 1,000,000 = venom; negative = immunity; 0 = none. | RL (`VENOM_THRESHOLD = 1000000`, `VENOM_VALUE_CUTOFF = -38`) |
| PO2 | Poison and venom hit "once every 30 game ticks (18 seconds)". Toxins are normal timers: no damage and no wear-off while an interface is open; the overdue hit lands once it closes (scripts.md T4/T6). First hit one interval after being poisoned (OPEN-PO2). | PO "Mechanics", scripts.md T6 |
| PO3 | Poison hit = ⌊(severity + 4) / 5⌋ (= ⌈severity / 5⌉, RL); severity −1 per hit, cured at 0. A source with starting damage D has severity 5·D (reanimated kalphite: severity 30 → 6 damage, 105 total). A new poison replaces a weaker one ("duration can be reset while the enemy is poisoned"). | PO "Mechanics", "Notes" |
| PO4 | Venom: first hit 6, +2 per hit, capped at 20. Encoded as 1,000,000 + n, hit = min(20, 2 × (n + 3)), n + 1 per hit. | VE "Venom damage", RL |
| PO5 | A venomous attack on a target immune to venom but not to poison poisons it at severity 26 (6 damage). | PO weapon table ("Venom converted to poison") |
| PO6 | Antipoison family: on venom, a non-anti-venom dose converts venom to poison at the venom's current damage (a second dose cures). Otherwise it cures poison and sets immunity. Immunity per dose: antipoison 1.5 min, superantipoison 6, antidote+ 9, antidote++ 12, anti-venom 12.3, anti-venom+ 15, extended anti-venom+ 17.7 min = 5/20/30/40/41/50/59 intervals (stored as −N, counting up each interval). Anti-venom cures venom outright. | PO curing table, VE "Curing venom" |
| PO7 | Immunity values below −38 also block venom: antidote++ gives 18–36 s, anti-venom 36–54 s, anti-venom+ 3.6 min of venom immunity, matching VE. | RL, VE |
| PO8 | NPC poison runs on the NPC's own timer; "if a poisoned monster goes out of combat ... poison is immediately removed". Poison immunity per NPC comes from the Wiki NPC table (npc-combat.md). | PO "Notes" |
| PO9 | Weapon poison: "(p)" 20, "(p+)" 25, "(p++)" 30 severity; chance "1/4 for melee, 1/8 for ranged ... Damage must be non-zero"; ranged poison severity −14. Ranged poison comes from the ammo for bows/crossbows, else from the weapon. | PO "Weapon poisoning" |
| PO10 | Toxic blowpipe, toxic staff of the dead, trident of the swamp: 25% to envenom on a hit; 100% against NPCs with a serpentine helm. | VE weapon table, TB |
| PO11 | Serpentine helm: immune to poison and venom while worn; vs NPCs, a melee hit envenoms 1 in 6 (non-poisoned weapon) or 1 in 2 (poisoned weapon). | SH, PO table |
| PO12 | Poisonous NPCs "may do so regardless of whether or not they inflict any damage, even if protection prayers are used". Starting damage: the infobox `poisonous` number. | PO intro |
| PO13 | Poison and venom damage are typeless hits with their own hitsplats (cache hitmark 65/66 poison, 5 venom; RS Mod `hitmark.sym`), no retaliation; death clears them. | PO, VE, RS Mod |

| PO14 | Other modules poison or envenom through plugin-api `PoisonRequest` (urn snakes, traps); the same rules apply. | - |

## OPEN

- **OPEN-PO1:** an NPC's chance to poison per attack. Implemented: 1/4 (the melee weapon chance).
- **OPEN-PO2:** whether the first poison hit lands immediately or one interval later. Implemented: one interval later.
- **PO3 (resolved):** the trident of the swamp envenoms 25% of its hits (combat-magic.md CM21), combat spells cast with the toxic staff of the dead roll the PO10 venom chance, smoke spells poison (CM11).
- **OPEN-PO4:** serpentine helm charges (Zulrah's scales) aren't tracked; any worn serpentine helm counts.
- Not modelled: HP-orb click to cure, Guthix rest's poison reduction, strange fruit, poison message variants (Sanfew's antipoison: consumables.md E11).
