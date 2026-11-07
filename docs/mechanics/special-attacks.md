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

## OPEN

- **OPEN-SP2:** Leagues / Deadman-only specials (Echo slash, Power of the Gods' blood sacrifice, Crystalline
  Severance, Brand, Lightning Strike, Ferocity's Flames of Cerberus, Explosive Shatter, Sol Slam, Entice, Tumeken's
  Light, Virulence, Behead, Break Shackles, Eclipse, Burning barrage, Scorching shackles, Scatter ashes, Blood infusion,
  Brutal Swing's stacks, Shield Slam charges) use only their generic row; the buffs among them say "This special attack
  is not available here."
- **OPEN-SP3:** enchanted-bolt specials (Armadyl Eye, Evoke) need bolt effects, not modelled; their accuracy applies.
- **OPEN-SP4:** player spec graphics are drawn at height 0 for melee and 96 for ranged / magic; the per-graphic heights
  are not in any source.
- **OPEN-SP5:** the dragonfire shield / wyvern shield operate attacks (no energy, 2-minute cooldown) are item ops, not
  done here.
- The research OPEN list (SA-1..SA-26) applies.
