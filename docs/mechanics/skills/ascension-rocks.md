# Ascension rocks (The Fractured Archive lobby)

`content/skills/.../AscensionRocks.kt` (world clock, sprites, pet) and the `ASCENSION` rock kind of `Mining.kt` / `MiningData.kt`
(the mining roll). Shared mining rules (clocks, pickaxes, messages): mining.md MN1-MN3, MN10; success roll: skilling.md SK1.

Sources: Wiki "Ascension rocks" https://oldschool.runescape.wiki/w/Ascension_rocks (incl. Changes 28 Oct and 3 Nov 2026), "Sprite"
https://oldschool.runescape.wiki/w/Sprite, "Ascension shards" https://oldschool.runescape.wiki/w/Ascension_shards, "Money making
guide/Mining ascension rocks" https://oldschool.runescape.wiki/w/Money_making_guide/Mining_ascension_rocks; the cache (`tools/cache-dump map 64 86`).

## Rules

- **AR1: mining.** Locs 63266..63268 (`TFA_GATHERING_ROCK_ACTIVE01..03`) have the op "Mine"; Mining level 80, 15 xp per shard, any pickaxe
  (the pickaxe's own level requirement still applies), product `ASCENSION_SHARDS` (34669). The tick timeline is mining.md MN1: click arms
  the clocks, swing next tick, a roll every pickaxe cycle. A roll never depletes the rock; the rock changes only by AR2. A player
  whose rock sank stops on the next swing (the loc under them is no longer theirs). The prospector outfit's xp bonus applies as for
  every Mining xp (mining.md MN15). "Ascension rocks cannot be tick manipulated": the cycle is the ordinary one, nothing extra is done.
- **AR2: the rotation (world-scoped).** Three of the lobby's ten rock tiles are active at a time. Every 295 ticks (Wiki: "they deplete
  every 295 ticks", MMG "every 177 seconds"), for everyone, regardless of who is mining or online (a world tick hook, state in
  `worldAttr`): the three active rocks are replaced by their inactive variant (the same loc id + 3; they "sink into the ground",
  no grey rock) and three other rocks are replaced by their active variant. The next rotation is due one period after the previous was
  due (no drift). The cache places all ten inactive; at world start the first three are raised and the first rotation is due 295 ticks later.
  Timeline: tick T (due): sink old three and raise new three in the same tick; T+295: again.
- **AR3: which rocks.** The ten sites are walked as a ring in the Wiki map's pin order (then the cache-only tile); rotation n raises
  ring positions `3n, 3n+1, 3n+2` (mod 10). Consecutive triples never overlap and every rock is used (OPEN-AR3).
- **AR4: no enhancers that give extra resources.** No Varrock armour / Mining cape extra ore, no mining gloves (the rock never
  depletes by mining), and the amulet of glory's extra gem chance is not applied (Wiki: "do not benefit from any mining enhancers that
  give extra resources"; whether the glory counts is OPEN-AR8). Bags (`Bags.autoStore`) still apply to gems.
- **AR5: pre-roll gems.** Before each roll the standard Mining gem drop table (mining.md MN9): 1 in 256, a hit consumes the attempt and
  gives nothing (70/128), sapphire (32), emerald (16), ruby (8) or diamond (2). Matches the page's pre-roll list (sapphire, emerald, ruby, diamond).
- **AR6: success chance.** `statRandom(MINING, 15, 15)`: `(1 + 15) / 256` = 6.25% at every level (OPEN-AR1). The Wiki gives no curve; this
  reproduces "130 shards per hour" at level 99 with a dragon pickaxe: a dragon cycle averages 3 - 1/6 ticks, so 6000 / 2.833 = 2,118
  rolls an hour, x 255/256 (gem pre-roll) x 6.25% = 132 shards.
- **AR7: Rock golem.** After each shard a 1 in 68,120 roll (Wiki, news post 3 Nov 2026). There is no follower system (drops.md), so the
  pet item (`SKILLPETMINING`, 13321) goes to the backpack or the floor, with a `LootEvent` (OPEN-AR6).
- **AR8: sprites.** While mining, a per-player countdown of 290..310 mining ticks (uniform, from the Wiki "Sprite": "every 290-310 ticks
  while actively mining") is reduced by the pickaxe cycle on every roll; at zero a sprite NPC (`TFA_GATHERING_SPRITE`, 16691) appears
  on a free tile next to the player and a new countdown is drawn. Op 1 "Look-at": 1 or 2 ascension shards, 50-89 Mining xp, "The sprite
  shatters, giving you some ascension shards." (the Wiki message), then it goes after the explode animation. A sprite nobody looks at
  is removed 50 ticks (30 s) after it appeared. It can wander 1 tile. The first player to look takes it.
- **AR9: Infernal and Crystal pickaxes.** The 28 Oct 2026 change ("Prevented the Infernal Pickaxe from smelting Ascension shards, as they
  cannot be smelted") is satisfied by construction: this server has no Infernal auto-smelting in Mining (mining.md OPEN-MN6), and
  shards are never smelted. The crystal pickaxe's charge use on these rocks is not implemented (no crystal tool charges exist yet, OPEN-AR9).

## OPEN

- **OPEN-AR1:** the rocks' success curve (low/high) is not on the Wiki; 15/15 is derived from the 130 shards/hour figure (AR6).
- **OPEN-AR2:** the cache has ten rock tiles; the Wiki map lists nine. The tenth, (4113, 5538) variant 2, is used (the cache is the source
  of truth, brief 2). Positions, variants and angles of the other nine match the Wiki pins and the cache exactly.
- **OPEN-AR3:** "three other rocks nearby become active" does not say which; the ring walk of AR3 is deterministic and uses no RNG.
  The first rotation raised at world start is ring positions 0..2 (starts there for lack of a source).
- **OPEN-AR4:** the sprite stays 2 ticks after "Look-at" for its explode animation (`TFA_GATHERING_SPRITE_EXPLODE`); real timing not read.
- **OPEN-AR5:** where the sprite appears ("near the player", a random free neighbour) and its wander range (1).
- **OPEN-AR6:** the Rock golem is given as an item (no follower system); the message text is the game's usual pet wording, not from a Wiki source.
- **OPEN-AR7:** the distribution of the sprite's 1-2 shards and 50-89 xp is assumed uniform; whether a sprite is private to the player whose
  mining made it is not documented (here anyone may look, first come); a "Look-at" ends the player's mining action here, while the Wiki says
  it does not interrupt mining.
- **OPEN-AR8:** whether the amulet of glory's gem bonus counts as an "enhancer that gives extra resources" is assumed yes.
- **OPEN-AR9:** the crystal pickaxe's charges; the sound of a sprite appearing and the 3 Nov "rotate" sound (no sound messages exist,
  skilling.md OPEN-SK1); the While Guthix Sleeps requirement listed on the money-making guide is not enforced (the rocks page states none).
