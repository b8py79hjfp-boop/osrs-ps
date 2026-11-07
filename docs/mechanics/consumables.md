# Food and potions

Sources: OSRS Wiki "Food" (FO), "Food/Fast foods" (FF), "Cooked karambwan" (CK), "Saradomin brew" (SB),
"Shark" (SH), raw wikitext 2026-11-06; brief §8.

| # | Rule | Source |
|---|---|---|
| E1 | Standard food: eat delay 3 ticks (blocks further food), adds 3 to the attack clock. Halibut, karambwan, gnome foods add 2. | FO:7, FF:5-8, brief §8 |
| E2 | "Foods only add to an existing attack delay" / "If your weapon is ready to attack again then eating does not add any new delay": add the delay only if the attack clock is in the future (clock > now). | FF:11 |
| E3 | Separate clocks: food, potion, karambwan. A karambwan can follow another food in the same tick (not first, not twice). Shark + karambwan = +5 attack delay. Marlin + brew + halibut in one tick works. | CK:30, FF:26-29 |
| E3b | A karambwan blocks food, potions and karambwans for 3 ticks. Brief §8 only; the Wiki doesn't state the potion block or the length (**OPEN-E4**). | brief §8 |
| E4 | Potions have their own sip clock (no 3-tick food delay); brews don't delay attacks. | FF:29-40, FO:493 |
| E5 | Shark heals 20; karambwan 18. | SH:23, CK:24 |
| E6 | Saradomin brew: heals ⌊HP × 15/100⌋ + 2 (can overheal); Defence +⌊Def / 5⌋ + 2; Attack, Strength, Ranged, Magic −(⌊level / 10⌋ + 2). | SB:66-115 |

| E7 | Heals: lobster 12, swordfish 14, monkfish 16, shark 20, manta ray 22, dark crab 22, tuna potato 22; anglerfish ⌊x/10⌋ + 2⌊x/25⌋ + 5⌊x/93⌋ + 2 by Hitpoints level and may overheal by that amount. | item pages |
| E8 | Super restore: 8 + ⌊base/4⌋ to each lowered stat except Hitpoints (Prayer included). Prayer potion: 7 + ⌊Prayer level/4⌋. | Super restore, Prayer potion |
| E9 | Boosts: super attack/strength/defence and super combat ⌊L × 15/100⌋ + 5; ranging ⌊L/10⌋ + 4; bastion ranged ⌊L/10⌋ + 4 and defence ⌊15%⌋ + 5; magic potion +4; stamina +20% run energy. | potion pages |
| E10 | Barbarian mixes (two doses): the base potion's effect plus 3 or 6 Hitpoints per dose (Wiki table), and the food attack delay (+3 ticks while the attack clock runs). | Wiki "Barbarian Training#Herblore" |
| E11 | Divine super combat: as super combat. Sanfew serum: 4 + ⌊30% base⌋ to every lowered skill but Hitpoints, 6 min poison immunity. Ancient / forgotten brew: Magic +2 + 5% / +3 + 8% of base, Prayer +2 + 10% of base up to 5% over base, Attack/Strength/Defence −(2 + 10% current). Guthix rest (cup, 4 doses): +5 Hitpoints up to 5 over max, +5% run energy. | Wiki potion pages |

Items are matched by cache name; potion dose chains come from the names "X(4)" … "X(1)" → vial (229).

## OPEN

- **OPEN-E1:** potion sip clock length. Implemented: 3 ticks (same as food), separate from food.
- **OPEN-E2:** overheal cap for brews (HP + ⌊HP × 15/100⌋ + 2). Implemented as that cap.
- **OPEN-E5:** a boost doesn't lower a higher current level and is computed from the base level (pages say "SkillLevel").
- **OPEN-E3:** brew drain base level: formula uses the current level (SB:115) vs the table's "Overall level"
- **OPEN-E6:** a mix whose base potion has no effect in the potion table here (e.g. Relicym's balm, Fishing / Hunter / Agility potion, Zamorak and Ancient brew) only heals.
- **OPEN-E7:** divine potions' 5-minute no-drain period and their 10 Hitpoint cost are not modelled.
- **OPEN-E8:** Guthix rest's poison / venom reduction and Sanfew's disease immunity are not modelled (no disease system).
  (SB:117-135). Using the current level.
