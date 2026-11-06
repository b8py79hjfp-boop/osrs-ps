# Wilderness bosses, revenants and the maledictus

Code:
- Framework: `content/combat/.../Boss.kt` (`Boss`, `Bosses`, `BossCombat`).
- Bosses: `Callisto.kt`, `Vetion.kt`, `Venenatis.kt`, `WildernessBosses.kt`, `Dragonfire.kt`, `Revenants.kt`.

Sources: the OSRS Wiki pages for each boss and its "/Strategies" page, "Revenants", "Revenant maledictus",
"Bracelet of ethereum", "Dragonfire", and the updates "Wilderness Boss Rework" (and Tweaks). The raw wikitext is kept
outside the repo. Base stats, max hits per style, attack speeds and respawn times come from the Wiki infobox table
(`npc-combat.tsv`, npc-combat.md).

## Framework (B)

- **B1. Attack hand-off.** The generic NPC attack (npc-combat.md N1–N8) sets the attack clock and then hands the attack
  to the boss script. Poisonous hits still poison (N7).
- **B2. Hit hooks.** A landing hit on a boss goes through `incoming` (immunity, resistance), then `afterHit` (health
  thresholds). When hitpoints reach 0, `onLethal` decides: a phase change keeps the NPC alive, otherwise it dies.
  Damage per player is tracked for the top-damager credit.
- **B3. No reflection.** "All Wilderness bosses are now immune to Ring of Recoil and similar effects." This uses
  `SharedAttrs.REFLECT_IMMUNE`; PvpCombat skips Vengeance and recoil.
- **B4. Respawn pause.** Bosses "wait a short period of time after respawning before attacking players". OPEN-B2: the
  length is not published; we use 5 ticks.
- **B5. Minions.** Summoned minions (hellhounds, spiderlings, guardians) and the maledictus despawn when they die instead
  of respawning. A boss's minions are removed when it dies.
- **Aggression.** Bosses ignore tolerance (aggression.md AG4); revenants keep it. OPEN-B1 / OPEN-B3: hunt ranges are
  not published; we use the whole arena.

## Callisto / Artio (CA)

- **CA1. Melee.** A swipe with "AoE capabilities, damaging anyone else near his main target ... can also attack
  diagonally". Callisto hits 35–55 and Artio 15–35. Protect from Melee "reduces the damage by ~50%" (we halve it).
- **CA2. Ranged.** When "he cannot attack with melee", projectiles hit everyone in the den. Max 31 (Artio 17), and
  prayer blocks it fully.
- **CA3. Knockback.**
  - An orb hits "if Protect from Magic is not activated when the attack visually hits", so prayer is checked at impact.
  - It splashes the 3×3 around the target and pushes players 3 tiles away from the boss.
  - Damage is 5, plus 15 for each tile the push could not complete; it is 5 when a trap stops the push, plus the trap's
    own damage.
  - OPEN-CA2: frequency; "less frequently" after the tweak, and we use 1 in 6.
- **CA4. Bear traps.**
  - At 66 % and 33 % health he roars: he "becomes unfrozen" and deploys traps (loc `callisto_trap_loc`).
  - A trap deals up to 15 damage and briefly binds.
  - OPEN-CA1: trap count and lifetime; we use 10 traps for 25 ticks.
  - OPEN-CA3: bind length; we use 3 ticks.
- **CA5. Trap refresh.** After 33 %, traps "appear afterwards with every attack", and each set replaces the last.
- **CA6. Freezes always land.** "If Callisto is not already frozen and the attacking player has equipment with a total
  Magic accuracy greater than 0, the first attack that would freeze the boss is guaranteed to hit." The Artio page says
  "0 or higher"; OPEN-CA4, and we count 0.

## Vet'ion / Calvar'ion (VE)

- **VE1. Lightning.**
  - Bolts hit 3×3 areas after the tiles glow.
  - Solo: one bolt on or next to the player and four at random. Groups: one per player, capped at seven.
  - Max 44 (Calvar'ion 26) on the centre tile. OPEN-VE2: the edge damage is only "light"; we use a quarter of the max.
  - OPEN-VE5: the glow-to-strike time; we use 3 ticks.
  - Prayer does not apply; the bolts are dodged.
- **VE2. Shield bash.**
  - Only in melee distance.
  - Shadows mark either 1 tile around him on every side, or 2 tiles thick on the side facing the target.
  - Up to 35 damage (Calvar'ion 26), and "unable to attack for the next 4.8 seconds" (8 ticks).
- **VE3. Hellhounds.**
  - At half health he summons two hellhounds (6613/6614; Calvar'ion 12107/12108). They are melee.
  - "complete damage immunity while present".
  - OPEN-VE3: we give 90 % resistance during the 2-tick summon.
  - Each form summons them once.
- **VE4. Enraged form.**
  - The first form's death becomes the enraged form (6612 / 11994) at full health, with a ring of orange lightning.
  - Attack speed 5 (Wiki infobox). The second form's death is final.
  - OPEN-VE4: we revert to the first form, healed, if he is not killed within 500 ticks.
- OPEN-VE1: how he picks an attack when in melee range. We use lightning, bash and melee, a third each.

## Venenatis / Spindel (VN)

- **VN1. Attack cycle.**
  - Melee when the player is adjacent ("the only normal attack ... within melee distance").
  - Otherwise 8 ranged attacks, then 8 magic, moving every 4 attacks.
  - OPEN-VN1: where she moves; we use a random tile within 3.
- **VN3. Spiderlings.**
  - Summoned on the first ranged attack of each cycle (12000 / 12001). OPEN-VN3: we summon 2 per call, with at most 10
    alive.
  - Each spiderling hit drains 1 prayer "regardless if prayed against or hits".
  - While they live, her hits gain a component that ignores prayer. OPEN-VN2: we use 3 per spiderling, capped at 30.
  - Spindel: "Player ... damage is slightly reduced while spiderlings are alive". OPEN-VN4: we reduce it by 10 %.
- **VN4. Sticky web.**
  - Cast on the third magic attack. It lands on the target's tile and spreads 7×5 (locs `wbr_venenatis_web_*`).
  - Every tick, each player inside takes 3 damage, −3 prayer and −10 % run energy.
  - Aranea boots negate the first tick.
  - OPEN-VN5: the web lifetime; we use 8 ticks.

## Chaos Elemental (CE)

- **CE1. Discord.** Melee, ranged or magic damage, "magic damage most frequently". OPEN-CE1: we use magic ½, ranged ¼
  and melee ¼.
- **CE2. Confusion.** "teleports players a short space or several spaces away". OPEN-CE2: up to 6 tiles.
- **CE3. Madness.** "unequips up to four items ... The weapon of the player is always prioritised". It is never used
  when the backpack is full.
- OPEN-CE3: special frequency; we use 1 in 6 each.

## Scorpia (SC)

- **SC1. Melee.** Stab, poison 20. It drains 2 prayer "whether successful or not" when Protect from Melee is on.
- **SC2. Guardians.** "Once Scorpia falls below 50% health, the next attack against her, whether successful or not, will
  cause her to summon two Scorpia's guardians." This happens once per life.
- **SC3. Guardian healing.** Guardians heal her 8 every 3 ticks within 3 tiles. One that hasn't healed for 25 ticks
  despawns.
- **SC4. Offspring.** Scorpia's offspring live in the pit. They are world spawns, and the importer now reads
  disambiguated pages.

## Chaos Fanatic (CF)

- **CF1. Attack choice.** A red magic attack, max 31, fully blocked by prayer. After the cooldown and at the start
  of a fight: 10/15 red attack, 4/15 explosion, 1/15 disarm.
- **CF2. Explosion.** Three slow projectiles; one always targets the player's tile at launch. Then "a 12-attack
  cooldown" of red attacks only.
  - OPEN-CF1: damage; we use the infobox special max of 31.
  - OPEN-CF2: flight time; we use 4 ticks.
- **CF3. Disarm.** An unavoidable projectile that unequips the weapon. A full backpack negates it.

## Crazy archaeologist (CR)

- **CR1. Attacks.** Ranged, or melee "if the player is next to him". Prayer nullifies each.
- **CR2. Rain of knowledge.** He says "Rain of knowledge!" and throws three books in an arc; each explodes 3×3. One
  splits into two more that explode nearby after a delay. OPEN-CR1: "Periodically"; we use 1 in 5.

## King Black Dragon (KB) and dragonfire (DF)

- **KB1. Attack choice.** "if it's in melee range, it's got a 1/3 chance of doing melee, 1/3 chance of doing fire and
  1/3 of doing one of the ice/toxic/shocking attacks". Out of range it uses fire or a special, half each. The three
  specials are equally likely. Melee max is 25, blocked by prayer.
- **KB2. Special effects.**
  - On a hit, each special has "up to 1/2" chance of its effect, "1/8 for a well-protected player".
  - Toxic: poison 8. Shocking: −2 to all stats (OPEN-KB2: Hitpoints excluded). Icy: freeze 10 ticks.
- **DF1. Antifire potions.**
  - Antifire 6 min and extended 12 min, in varbit `antifire_potion` (30-tick units).
  - Super antifire 3 min and extended super 6 min, in varbit `super_antifire_potion` (20-tick units).
  - Units follow RuneLite `TimersAndBuffsPlugin`.
- **DF2. KBD max hits** (Wiki "Dragonfire" table, fire / special):

  | Protection | Fire | Special |
  |---|---|---|
  | None | 65 | 50 |
  | Shield | 15 | 10 |
  | Protect from Magic | 20 | 15 |
  | Shield + Protect from Magic | 15 | 10 |
  | Antifire | 50 | 50 |
  | Super antifire | 0 | 50 |
  | Shield + antifire / super | 0 | 10 |
  | Protect from Magic + antifire | 5 | 15 |
  | Protect from Magic + super | 0 | 15 |

  Shields: anti-dragon shield, dragonfire shield/ward and ancient wyvern shield, each charged or uncharged.
- **DF3. Protected.** Any of the above counts as protected for KB2.
- OPEN-KB1: dragonfire has no accuracy roll here; damage is uniform up to the max.

## Revenants (RV)

- **RV1. Style choice.** "By default, all revenants attack with Magic, but can quickly adapt based on the player's
  defensive bonuses and prayers." OPEN-RV1: we pick the unprotected style with the lowest defence bonus, with melee
  only in reach.
- **RV2. Magic.** The magic attack freezes and "hits up to nine other players on the same tile". OPEN-RV2: freeze
  length; we use 8 ticks.
- **RV3. Healing.** Below 50 % health, when attacking and off cooldown, they heal 20. The cooldown is 15 ticks, and they
  heal "8-25 times, with equal chance of each integer".
- **RV4. Bracelet of ethereum.**
  - A charged bracelet takes 75 % off each attack, "with each attack consuming one charge". The charges are in varbit
    `charges_bracelet_of_ethereum_quantity`.
  - The bracelet turns uncharged at 0.
  - "Revenants are also made tolerant when a charged bracelet is worn."

## Revenant maledictus (RM)

- **RM1. Spawning.**
  - On each revenant kill in the caves there is a chance of accumulated revenant combat levels (per world) / a flat
    rate. Spawning resets the count and starts a 45-minute cooldown.
  - It appears in one of the 11 Wiki areas (north, middle or south).
  - OPEN-RM3: the rate; we use 40,000.
- **RM2. While active.**
  - Players in the caves are told which part it appeared in. OPEN-RM1: the wording.
  - It leaves after 10 minutes.
  - The minimap hint arrow is not sent yet.
- **RM3. Attacks.**
  - It attacks every player in range: a standard ranged spike, then a special after every few standard attacks
    (OPEN-RM4: every 3).
  - The specials cycle ice → blood → air.
  - Protection prayers halve its damage.
- **RM4. Ice and blood.** Both hit the players' marked tiles. Ice freezes; blood heals it for half the total damage
  dealt.
- **RM5. Air.** Typeless, 5×5, up to 25 and "less near the edges". OPEN-RM2: we halve it on the edge.
