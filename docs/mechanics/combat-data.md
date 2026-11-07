# Combat data in the cache (rev 242, cache 2757)

Verified with `./gradlew :tools:cache-dump:run --args="config obj <id>"` on cache 2757.

## Obj params (equipment)

| Param | Meaning | Check |
|---|---|---|
| 0–4 | Attack bonuses: stab, slash, crush, magic, ranged | whip {1=82}, AGS {1=132, 2=80} |
| 5–9 | Defence bonuses: stab, slash, crush, magic, ranged | dragon claws {5=13, 6=26, 7=7} |
| 10 | Melee strength | whip 82, AGS 132 |
| 11 | Prayer bonus | AGS 8 |
| 12 | Ranged strength (ammo) | parity matrix |
| 13 | Ranged attack range | magic shortbow 7, rune crossbow 7 |
| 14 | Attack speed (ticks) | whip 4, godswords 6, granite maul 7, rune crossbow 6 |
| 299 | Magic damage, tenths of a percent | occult necklace (parity matrix) |

Names and ids agree with RS Mod `BaseParams` (rev 233). Ids 434/436 are stat requirements (RS Mod `statreq1_*`).

## Weapon categories

Rev 242 objs carry no weapon-category opcode (RS Mod's opcode 212 is its own packing) and no cache enum maps objs or obj
categories to combat styles (searched with `cache-dump enums-with-keys`). We map the obj `category` field, checked per
weapon: 1 staves, 21 scimitars/longswords, 24 thrown, 25 daggers/shortswords, 26 mauls, 37 crossbows, 61 godswords,
64 bows, 65 claws, 150 whips. Stances and attack types per category: RS Mod `StyleEnums`/`TypeEnums` (ISC), cross-checked
by the DPS parity matrix. **OPEN-D2:** categories not in the table fall back to unarmed styles.

## Prayers

Enum 4956 lists the prayer objs (key = prayer id). Obj params: 630 bit in varp 83 (= varbit `prayer_allactive`
4101 bits, e.g. Protect from Melee 14 = varbit 4118), 1751 prayer-book component, 1752 name, 1753 level. Effects, drain
and conflict groups aren't in the cache: OSRS Wiki "Prayer" table (prayers.md).

## Not in the cache (OPEN)

- **N1 (resolved):** NPC attack speed, max hit, attack style, bonuses and respawn time come from the OSRS Wiki
  (npc-combat.md).
- **OPEN-N2:** NPC death length (the Wiki infobox has no field for it).
- **OPEN-V1:** per-weapon attack animations (visual only).

## Weapon data (server-side, not in the cache)

| # | Rule | Source |
|---|---|---|
| CD-W1 | Each weapon's category (the combat tab varbit `combat_weapon_category` and its stances / attack types, 27 categories) comes from `weapons.tsv`; weapons it doesn't list fall back to the obj `category` param mapping. | RS Mod `objs.toml` (ISC), `WeaponCategory`, `StyleEnums`, `TypeEnums` |
| CD-W2 | The attack anim is the weapon's anim for the selected combat-tab slot (else its first one, else the category default); a special attack's own anim wins. | RS Mod `objs.toml` `anim_stance1..4` |
| CD-W3 | When hit, a player plays the shield's block anim, else the weapon's, else `human_unarmedblock`, timed to the hit (not while moving); NPCs play their block anim (npc-combat.md N10). | RS Mod `combatPlayDefendAnim` |
| CD-W4 | While a weapon is wielded the player's base animations (ready, turn, walk ×4, run) are its own where given. Ranged attacks show the ammo's (or thrown weapon's) launch graphic at height 96 and its travel projectile in its shape (arrow, bolt, thrown, chinchompa, dark bow doubles); projectiles of every kind use the RS Mod `ProjAnimBuilds` shapes (heights in MAP_PROJANIM_V2 units), arriving on the hit tick. | RS Mod `objs.toml`, `PvNCombat`, `ProjAnimBuilds` |
| CD-W5 | Attack speed and range come from the cache params 14 / 13; `weapons.tsv` fills them where the cache has none. | RS Mod `objs.toml` |
