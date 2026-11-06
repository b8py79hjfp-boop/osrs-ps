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
