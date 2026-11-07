# Firemaking

`content/skills/.../Firemaking.kt`, data in `BurnableLog.kt`. Shared rules (success roll, filterable messages): skilling.md.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-06:
- (F) "Firemaking", https://oldschool.runescape.wiki/w/Firemaking
- (R) "Fire", https://oldschool.runescape.wiki/w/Fire
- (T) "Tinderbox", https://oldschool.runescape.wiki/w/Tinderbox
- (P) "Pay-to-play Firemaking training" / "Free-to-play Firemaking training" (tick cadence of power-firemaking)
- (C) the cache (cache 2757): ground op 4 "Light" on every log, loc `fire` 26185, anim `human_createfire` 733.

## Rules

| # | Rule | Source |
|---|---|---|
| FM1 | Using a tinderbox on logs in the backpack (either order), on logs on the ground, or the "Light" ground op (op 4) starts an attempt. A player timer rolls it every 3 ticks until it lights. Walking away, losing the logs or the tinderbox, or a modal opening ends or pauses it (scripts.md T4). | F, T, C; interval OPEN-FM1 |
| FM2 | Level and xp per log: the Wiki's "List of combustible logs" (normal and achey 1 / 40 ... rosewood 92 / 268). Too low: "You need a Firemaking level of N to burn <logs>." | F, T |
| FM3 | The roll is the skilling success roll (SK1) with low 64, high 512 for every log (65/256 at level 1, certain from level 43). A fire needs a free centrepiece layer (loc shape 10) on its tile: no other fire and no object. Otherwise "You can't light a fire here." | F ("Success chance"), R, T |
| FM4 | On success the log is consumed, the fire loc (26185) appears on the tile for 60 to 119 seconds (100 + 0..98 ticks), the xp is given and "The fire catches and the logs begin to burn." is sent. When the fire burns out, ashes (592) appear on its tile. | F, R |
| FM5 | After lighting, a player standing on the fire walks one step: west, else east, else south, else north; if all four are blocked (collision only), they stay. | F, R |
| FM6 | A gnomish firelighter (red, green, blue, purple, white) used on plain logs coats them ("You coat the logs with the <colour> chemicals."), one of each. Coloured logs are lit with a tinderbox at level 1, always light on the first try, give 50 xp (40 + 10) and make the matching coloured fire loc (red 26186, green 26575, blue 26576, white 20000, purple 20001). | Wiki "Gnomish firelighters", C |
| FM7 | A bow used on logs lights them like a tinderbox (same roll, same xp) once Barbarian Training's firemaking part is done; the Wiki excludes twisted, cursed goblin, dark, crystal, ogre, rain and signed oak bows. Gated here on varbit `BRUT_FIRE` >= 1 (Otto has taught it, barbarian-training.md BT1) and 35 base Firemaking; the first oak logs lit with a bow move it to 2. | Wiki "Barbarian Training" |
| FM8 | Forester's campfire (`ForesterCampfire.kt`): a log used on a fire (plain or coloured) turns it into a campfire loc (49927 to 49932) that burns 99 ticks + the log's "burn time added" (3 plain, 10 oak, 17 willow, 19 teak, 22 arctic pine, 24 maple, 26 mahogany, 31 yew, 32 blisterwood, 38 magic, 45 redwood). "Tend-to" adds more logs, capped at 300 ticks in total, each with its normal xp; no tinderbox is needed once it burns. A new campfire needs a tinderbox and no other campfire within 5 tiles ("There's a Forester's Campfire nearby, help tend to that one or move further away."). "Check" answers by remaining ticks: up to 58 "The embers glow softly.", 118 "The flames flicker gently.", 178 "The fire burns steadily.", 238 "The fire burns brightly.", else "The roaring fire crackles invitingly." Burn-out leaves ashes. | Wiki "Forester's Campfire" |
| FM9 | Pyromancer outfit: +0.8% (garb), +0.6% (robe), +0.4% (hood), +0.2% (boots) Firemaking xp, +0.5% more for the full set (2.5%). It applies to lit logs, braziers, campfires, the infernal axe's burnt logs and the Wintertodt reward xp. | Wiki "Pyromancer outfit", "Pyromancer garb/robe/hood/boots" |

### Tick timeline

- Tick T (input phase, backpack): "You attempt to light the logs." and anim 733 at once; the timer is started.
- T+3: first roll. Success: fire, xp, message; the player's step is routed and taken on this tick. Failure: anim 733 again, next roll at T+6, T+9, ...
- With a click every time the previous log lights, logs light every 4 ticks (the Wiki's perfect cadence): click T, lit T+3, step, next click T+4.
- Ground logs: the same, started when the player has reached the log (an op script), so the first roll is 4 ticks after arrival
  (a timer started from an interaction first fires one tick later than one started from input).

## OPEN

- **OPEN-FM1:** the roll interval is not documented. The Wiki says only that a log is lit every 4 ticks with perfect play (P);
  3 ticks to the first roll plus the click on the following tick gives that. Retries use the same interval.
- **OPEN-FM2:** "60-119 secs randomly" (Mod Ash via R) is converted to 100..198 ticks; the exact tick range is unknown.
- **OPEN-FM3:** the extra "can't light" locations (buildings, banks, doorways, closed doors, plants and ferns, vine
  adjacency, minigame areas) are only covered through the centrepiece-layer check. Plants and ferns that sit on another layer
  are not detected.
- **OPEN-FM4:** OSRS drops the log on the ground when the attempt starts; here it stays in the backpack until the roll
  succeeds (no ground-item removal in the plugin API). Lighting a ground log needs one free backpack slot (take then
  remove in one transaction). The fire goes on the log's tile, the player may be next to it (no step then). The messages for
  a missing tinderbox ("You need a tinderbox to light a fire.") and a full backpack ("You don't have enough room to light
  that.") are not sourced.
- **OPEN-FM5:** not implemented: pyre logs (they only burn on Shades of Mort'ton pyres), lanterns and torches, the charged
  gnomish firelighter item (it stores charges and has no item-charge store here), the firelighter-on-Phoenix recolour.
  Wintertodt is in wintertodt.md.
- **OPEN-FM6:** ashes are queued on the lighting player (normal queue, owner = that player first). They are lost if the
  player logs out before the fire burns out, and delayed while the player has a modal open (scripts.md Q4). A world-level
  burn-out hook is not available to plugins.
- **OPEN-FM7:** a level-up popup (modal) pauses an attempt until it is closed (scripts.md T4); OSRS keeps skilling through it.
- **OPEN-FM8:** the Barbarian Training firemaking gate reads the miniquest state (`BRUT_FIRE`, `BarbarianProgress`); until Otto has
  taught it a bow on logs answers "Nothing interesting happens." The bow animations (`brut_player_firemaking_*`) are not played. The bow
  list is by cache name (shortbow, longbow, Seercull, strung); the Wiki's exclusion list is not exhaustive for other bows.
  White and purple coloured fires use the cache's `trail_white_fire` / `trail_purple_fire` locs (20000 / 20001).
- **OPEN-FM9:** Forester's campfire: the tick cost per tended log is not published (975 logs an hour with immediate tending
  is about 6 ticks, used here); "at least 5 tiles between them" is read as a Chebyshev distance above 5; camphor, ironwood
  and rosewood logs have no published burn time and cannot be added; the tending animation, the quantity interface of the
  real "Tend-to" and the "Rest" option are not implemented.
