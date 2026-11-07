# Special attacks

Every weapon with a spec bar (cache enum 906, 288 objs, 95 specials). Data: `docs/research/special-attacks.tsv`
(Wiki "Special attacks" and each weapon page, cache enums 906 / 1739, RS Mod visuals), generated into
`content/combat/.../SpecialTable.kt`; behaviour in `Specials.kt` (bar, energy) and `SpecialEffects.kt` (rules). Every
number's source is the TSV row; conflicts and guesses are listed in docs/research/special-attacks.md (SA-1..SA-26).

## Rules

| # | Rule |
|---|---|
| SP1 | A weapon (or shield) has a special when enum 906 lists it; its energy cost is the enum value. |
| SP2 | Specials are melee, ranged, magic, or instant (buffs and the instant attacks). |
| SP3 | Energy (varp `sa_energy`, 0..1000) regains 10% every 50 ticks, every 25 with a lightbearer; full on death. |
| SP4 | The bar / orb arms the next attack (varp `sa_attack`), or fires a buff at once; without energy: "You do not have enough special attack energy left." (SA-25); a weapon without one: "This weapon does not have a special attack." |
| SP5 | Quick Smash, Momentum Throw, Ferocity and Shield Slam attack the current target at once, without the attack clock. |
| SP6 | Unleash spends all energy (at least 5%). |
| SP7 | Concentrated Shot takes 4 more ticks; Soul Rend's attack takes 5; Wrath of Amascut locks attacks for 8. |
| SP8 | Multi-target specials in multi-combat: Powerstab (1 tile round the user, 15), Spear Wall (1 tile, 17), Shield Bash (5 tiles, 11), Annihilate (3×3, 9; each further target 80% max), Sweep (3×3, 11), Chainhit (3 tiles, 5); one target outside multi. |
| SP9 | Accuracy: the row's multiplier on the attack roll; guaranteed specials always hit; Backstab / Snipe always hit an unharmed target; Unleash +5% per 5% energy; Feint rolls against a quarter of the defence roll. |
| SP10 | Saradomin's Lightning adds a 1-16 magic hit when the melee part hits. |
| SP11 | Max hit: the row's multiplier, with formulas for the godswords (⌊max × 1.1⌋ × 1.25 AGS, × 1.1 BGS), Powershot / Soulshot / Snapshot ((visible Ranged + 10) × (ranged strength + 64) / 64), Penance (+0.5% per Prayer point missing), Unleash (+2.5% per 5%), Immolate / Invocate (min(58 / 44, (99 + cap × Magic) / 99) with magic damage), Condemn and Soul Rend (the staff's max × 1.5 / 1.3), Swarm (40% each). Protection prayers apply except for Favour of the War God, Wild Stab and Unleash. |
| SP12 | Hit patterns: the row's hit count with independent rolls; Abyssal Puncture one roll for both (0.85 each); Descent of Darkness two arrows, min 5 (dragon arrows 8), each at most 48; Feint / Hamstring 20-120% of max; Disrupt 50-150%; Seeking Lunge ⌊0.7 × max⌋ + 1. |
| SP13 | Dragon claws: up to four accuracy rolls, the first success sets the 4-2-1-1 pattern (SA-11). |
| SP14 | Ice Cleave freezes 32 ticks on a hit; Binding Tentacle binds 8 ticks always and poisons (4) half the time. |
| SP15 | Healing Blade heals 50% of the damage (at least 10) and restores 25% as Prayer (at least 5); Toxic Siphon heals 50%. |
| SP16 | Warstrike drains the damage from Defence, Strength, Prayer, Attack, Magic, Ranged in turn; Sunder takes 10% of it from the first of Defence, Attack, Ranged, Magic. |
| SP17 | Smash (damage > 0) and Pulverize (on a hit) lower the target's current Defence by 30% / 35%, compounding. |
| SP18 | Weaken drains Attack, Strength, Defence by 5% of base + 1 (demons 10%); Backstab / Snipe / Soulshot lower Defence / Magic by the damage if not already lowered; Condemn lowers Defence and Magic to 15% below base. |
| SP19 | Favour of the War God drains the target's Prayer by the damage and restores the user's; Invocate restores 50% (≤120); Liquify boosts Strength, Attack, Defence by 25% of the damage (cap +3 + 10% of base). |
| SP20 | Sever (PvP) turns off protection prayers for 8 ticks; Energy Drain (PvP) takes 10% run energy; Impale adds a tick to the next attack; Shove stuns 5 ticks; Hammer Blow deals 5 even on a miss. |
| SP21 | Bear Down (20), Phantom Strike (PvP, the damage again) and Flames of Ralos (10) bleed as typeless hits of 5 every 2 ticks (SA-20). |
| SP31 | Buffs: Sanctuary +8 Defence; Rampage drains Attack, Defence, Ranged, Magic by 10% and boosts Strength by 10 + drained / 4; Lumber Up / Rock Knocker / Fishstabber +3 Woodcutting / Mining / Fishing with their shouts; Power of Death halves melee damage taken for 100 ticks. |

## Rules added with the gear-effects audit (SP41-SP65)

Sources: each special's Wiki page (`https://oldschool.runescape.wiki/w/<Weapon>`), quoted in the research notes; the
Wiki DPS calculator (`PlayerVsNPCCalc`) where it models the special. Code: `SpecialRules.kt`, `Shields.kt`, `Zorya`.

- SP41 Specials that need a set or a resource check it before any energy is spent, with the game's refusal
  (Behead: soul stacks; Shield Slam: 10 charges; Blood infusion / Break Shackles / Eclipse: the full moon set; Scatter
  ashes: the Arceuus spellbook; the dark bow: at least 2 arrows). Specials hit through bolts and set effects (SP61) and
  keep the weapon's minimum hit (Osmumten's fang: Eviscerate rolls 15%..100% of the true max).
- SP42 Dragon longsword (bh): its special is one tick faster.
- SP43 Behead (soulreaper axe): consumes every soul stack; per stack +12% accuracy, +6% max, min hit 6% of max.
- SP44 Blood infusion (dual macuahuitl, blood moon set): max ×1.25 split ⌊half⌋ + rest, each half rolled on its own
  (×1.25 accuracy), costs ⌊25%⌋ of current Hitpoints hit or miss; Bloodrager (next attack a tick sooner) if either hits.
  No minimum (Mod Ash: "did not make it into the game").
- SP45 Brutal Swing (crimson kisten): four accuracy rolls; n successes deal 70+20(n−1)% .. 110+20(n−1)% of max in one
  splat (the row's 4000‰ accuracy is replaced by 1000‰).
- SP46 Break Shackles (blue moon spear, set): +1.5% accuracy (uncapped) and damage (≤ +112.5%) per tick of bind left on
  the target, then the bind is removed.
- SP47 Dragon mace (bh): Shatter rolls against 60% of the crush defence.
- SP48 Division (tonalztics): each successful hit lowers Defence by the target's Magic / 8; against monsters the first
  hit's drain counts for the second roll, not in PvP.
- SP49 Burning barrage (burning claws): up to three rolls, first success sets the pattern (75-175% split 25/25/50, 15%
  burn per splat; 50-150% split 50/50 then −1, −1, +2, 30%; 25-125% then +1, +1, −2, 45%; none: 0-2 total).
- SP50 Zorya's flow: the tome's powered-staff max ×1.6, accuracy ×2.5 (×2.2 / ×4 with stored charges); a hit stores 3
  buffed attacks (≤ 5, 30 s, refreshed per use): +150% accuracy, +60% damage, every 2 ticks. Not against players.
- SP51 Eclipse (atlatl, set): magic defence, ×1.5 accuracy; the atlatl's max plus the target's remaining burn damage
  (≤ 50; half to the min), burns consumed. OPEN-SP18: ranged or magic attack roll (magic used).
- SP52 Pulsate (Dawnbringer): 75-150, guaranteed, ignoring gear, prayer and level.
- SP53 Shield Slam (Rondache, no energy, from the spec bar when the weapon has none): +6 crush accuracy per charge,
  flat x + x% damage for x charges (24 at 20), all charges consumed.
- SP54 Descent of Darkness / Dragons: two arrows (2 ammo), ×1.3 (×1.5 with dragon arrows), minimum 5 / 8 per arrow even
  on a miss, at most 48; the (bh) bow 7 / 10 and rolls from the minimum.
- SP55 Scorching shackles: demons only ("Scorching shackles won't work against a non-demon enemy.", energy refunded):
  bound 20 ticks and a 5-damage burn, 1 at once, hit or miss.
- SP56 Blood Sacrifice (ancient godsword): see the rule in code; 25 typeless after 8 ticks unless 5+ tiles away, heal
  ≤ 15% of the target's max HP, capped 25 (15 vs players; OPEN-SP12 changelog says 14).
- SP57 Rondache charges: its +2 flat armour / magic resistance takes up to 2 off a hit not stopped by prayer, charging
  that much (≤ 2 per hit, once per 2 ticks, ≤ 20).
- SP58-SP60 Dragonfire shield / ward ("Activate") and ancient wyvern shield ("Operate"), worn op 1: one charge, range 10,
  cooldown 192 ticks (wyvern 200: OPEN-SP13), up to 25 (wyvern 15), Defence-based accuracy against magic defence
  (OPEN-SP15), not against dragons / wyverns; an anti-dragon shield caps it at 3 against players, a super antifire
  blocks it (OPEN-SP14: plain antifire). Dragonfire shields absorb one charge per dragon breath (≤ 50); "Inspect",
  "Empty", worn "Check". Wyvern charging with fossils / numulites is OPEN-SP19.
- SP61 Bolt effects in specials: Armadyl Eye doubles the base chance (the Kandarin diary then adds a tenth of the base:
  10% → 21%); Evoke guarantees the effect on a successful hit.
- SP62 Tumeken's Light: Tombs of Amascut only (refused here).
- SP63 Virulence (noxious halberd): only while poisoned / venomed; cures it (no immunity); the next accurate halberd hit
  deals at least the hit the affliction would have dealt (lost on weapon change / logout).
- SP64 Entice (soulflame horn): up to 3 other players within 3 tiles with Accept Aid on; 25% energy per ally
  (OPEN-SP16); their and the user's next melee attack within 10 ticks: first accuracy roll sure vs monsters, +50% vs
  players. No ally: "You blow your horn into the wind. No-one nearby is able to listen."
- SP65 Scatter ashes (purging staff, Arceuus): casts the best demonbane spell the player can (runes needed, OPEN-SP17);
  a kill refunds the energy and makes the next attack 3 ticks sooner.
- Momentum Throw counts as a normal attack: the attack delay restarts (Quick Smash does not).

## OPEN

- **OPEN-SP2:** Leagues / Deadman-only specials (Echo slash, Power of the Gods' blood sacrifice, Crystalline
  Severance, Brand, Lightning Strike, Ferocity's Flames of Cerberus, Explosive Shatter, Sol Slam, Entice, Tumeken's
  Light, Virulence, Behead, Break Shackles, Eclipse, Burning barrage, Scorching shackles, Scatter ashes, Blood infusion,
  Brutal Swing's stacks, Shield Slam charges) use only their generic row; the buffs among them say "This special attack
  is not available here."
- ~~OPEN-SP3~~ closed: Armadyl Eye / Evoke bolt effects are SP61.
- **OPEN-SP4:** player spec graphics are drawn at height 0 for melee and 96 for ranged / magic; the per-graphic heights
  are not in any source.
- ~~OPEN-SP5~~ closed: the shield operate attacks are SP58-SP60.
- **OPEN-SP12** Blood Sacrifice PvP heal cap (15 / 14). **OPEN-SP13** wyvern shield cooldown ticks. **OPEN-SP14**
  plain antifire against the shield blasts. **OPEN-SP15** shield blast attack bonus. **OPEN-SP16** Entice energy
  formula. **OPEN-SP17** Scatter ashes runes. **OPEN-SP18** Eclipse attack roll. **OPEN-SP19** wyvern shield charging.
- OPEN-SP2 also covers the Leagues / Deadman weapons' specials: Brand (infernal tecpatl), Crystalline Severance,
  Explosive Shatter (lithic sceptre), Lightning Strike / Lingering Lightning (thunder khopesh): not obtainable here.
- The research OPEN list (SA-1..SA-26) applies.
