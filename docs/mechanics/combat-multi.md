# Multi-target attacks

Burst and barrage spells (`content/combat/.../Magic.kt`) and chinchompas (`Chinchompas.kt`). The multi-combat map is the
pvp module's (RS Mod `multiway.json`), shared as the world attr `SharedAttrs.MULTI_AREA`.

## Rules

- **MG1: burst and barrage.** When the primary target stands in a multi-combat area, the spell also hits up to 8 more
  entities within one tile of the target (a 3×3; 9 targets in all). Each must itself be in multi-combat and attackable
  by the caster. Wiki "Ice Barrage": "able to target up to nine targets in a 3x3 grid around its primary target".
- **MG2: separate rolls.** Each target rolls its own accuracy against its own magic defence, its own damage and its
  own freeze. Wiki "Ice Barrage": "The spell's accuracy rolls for each opponent individually against their magic
  defence, unlike chinchompas."
- **MG3: one cast.** Runes and the attack clock are paid once, and the cast animation plays once.
- **CH1: fuse accuracy.** The ranged attack roll is scaled by fuse and distance:
  - short fuse (accurate): 100 / 75 / 50 % at 0–3 / 4–6 / 7+ tiles;
  - medium fuse (rapid): 75 / 100 / 75 %;
  - long fuse (longrange): 50 / 75 / 100 %.
  Source: Wiki "Chinchompa (weapon)", Mod Ash, 6 Aug 2016.
- **CH2: the explosion** hits the target, plus, in multi-combat, up to 8 attackable entities within one tile that are
  also in multi (Wiki "Chinchompa (weapon)": "hitting any targets in a 3x3 area, similar to burst and barrage").
- **CH3: one roll for all.** The primary target's accuracy roll decides whether every target is hit. Damage is rolled
  per target, and protect from missiles caps each player target separately. Wiki: "The initial target also serves as
  the accuracy roll for surrounding targets."
- **CH4: ammunition.** Each attack throws one chinchompa from the weapon slot; it is never retrieved. Chinchompas use
  the NPC heavy ranged defence column and the thrown-weapon hit delay. Projectiles: `chinchompa_grenade`,
  `big_chinchompa_grenade`, `black_chinchompa_grenade`. Throw animation: `human_chinchompa_attack` against players,
  `_pvn` against NPCs (gameval names).

## OPEN

- **OPEN-MG1:** spell base xp is given once per cast; damage xp is given per target.
- **OPEN-MG2:** the order of secondary targets when more than 8 qualify follows world order: NPCs first, then players.
- **OPEN-CH1:** the out-of-chinchompas message is not sourced.
- **OPEN-CH2:** the Slayer-task rule for secondary targets (Wiki "Ice Barrage") is not modelled.
- **OPEN-CH3:** the ice ancient sceptre bonus is not modelled.
