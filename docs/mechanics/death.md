# Deaths outside PvP, graves and Death's Office

How a player's items are split on an unsafe death outside player-versus-player combat, the grave that holds what was
lost, and Death's Office where expired graves end up. PvP deaths keep the drop to the killer (`combat-data.md`,
`PrayerEffects.DeathRules`); a safe death (Duel Arena, `SharedAttrs.SAFE_DEATH_AT`) keeps everything.

Sources: OSRS Wiki "Items Kept on Death" (IK), "Gravestone" (GR), "Death/Item Recovery Fees" (FE), "Death's Office"
(DO), "Death's Coffer" (DC), "Death (NPC)" (DN), "Death's Domain (scenery)" (DD), "Portal (Death's Office)" (PO).

## Rules

| # | Rule | Source |
|---|---|---|
| DE1 | A PvM death is a death not caused by a player (the last hit was not a player's) or outside the Wilderness and PvP worlds. The player keeps the 3 most valuable items (0 when skulled), +1 with Protect Item; value = Grand Exchange guide price, else the cache cost. | IK "Rules of thumb" |
| DE2 | Of the rest, cooked food and potions form a supply pile on the ground at the death tile (they despawn after 6000 ticks, one hour; the "supply piles" setting is on by default, OPEN-DE1: the setting is not modelled); in the Wilderness they are deleted instead. Everything else goes to the grave. | IK, GR "Supplies" |
| DE3 | The grave appears at the death tile; a death inside an instance puts it at the respawn point (OPEN-DE2: the Wiki says "outside the instance"). It is the NPC `grave` (9856) with Check and Loot, both for the owner only; it can be looted from up to 7 tiles with line of sight. | GR |
| DE4 | With a grave already in the world, the new items go into it where it is, except for a PvM death in the Wilderness while the grave is outside it (the grave moves to the death tile). Before the new items are added: of each unstackable obj only 28 stay, the excess goes to Death's Office ("Some items have been moved to Death's Office from your gravestone."); resources (unpowered orbs, pure essence, bones, ores, planks) go to Death's Office; food and potions in the grave drop to the ground under it. Then "Some of your items have been added to your previous gravestone." | GR "Gravestone storage" |
| DE5 | The grave lasts 1500 ticks (15 minutes) after respawning, counted only while the owner is logged in and not looking at the grave interface (OPEN-DE3: the "idle for more than 10 seconds" pause is not modelled); the timer restarts whenever the contents change. | GR, FE |
| DE6 | Loot takes the items out of the grave into the backpack, as many as fit (worn items stay in the grave when the backpack is full, OPEN-DE4: the auto-equip setting is not modelled). Fee per item by value: under 100,000 free; 100,000 to under 1m 1,000; 1m to under 10m 10,000; 10m and above 100,000; capped at 500,000 for one loot. The fee comes from Death's coffer, else from the bank; without the money nothing is taken: "You don't have enough money to pay the reclaim fee." (OPEN-DE5 text). | FE |
| DE7 | When the timer runs out the grave disappears and its items go to Death's Office storage (120 stacks; what does not fit is deleted). | GR, DO |
| DE8 | Death's Office (Death 9855 at 3180, 5727): entered by the Death's Domain coffins next to the respawn points (locs 38426, 39535, 39546, 39547, 39548, 39637, 42344, 42345, 52570, "Enter"); the portal (39549, "Use") at 3169, 5726 returns to the tile the player entered from, else the respawn point. "Collect" gives back the stored items: 5% of the value of each item worth 100,000 or more, from the coffer, else the bank. It also collects a live grave early at that rate. | DO, DN, DD, PO |
| DE9 | Death's coffer (loc 39550, "Sacrifice"): an item worth 10,000 or more, tradeable, adds 105% of its Grand Exchange price to the coffer (varp `death_coffer`), at most 2,100,000,000 per item and 2,147,483,647 in total; "You cannot trade that item to Death." / "The coffer is too full to accommodate that." The coffer only pays reclaim fees. | DC |
| DE10 | Untradeable items are never dropped for a killer: in a PvP death they go to the victim's grave as in DE3-DE5 (OPEN-DE6: breaking and the above-20-Wilderness rules are not modelled). | IK "Mechanics" |

## Tick timeline

- Tick T: the hit takes the player to 0; the death sequence starts (combat-timeline.md: anim at T+2).
- T+6: items are split (DE1, DE2), the grave spawns or is refilled (DE3, DE4), the player respawns; the grave timer starts.
- Each tick the owner is logged in: the grave timer counts down (DE5); at 0 the grave moves to Death's Office (DE7).

## OPEN

- **OPEN-DE1:** the supply-pile setting (always on here).
- **OPEN-DE2:** where an instance death's grave goes ("outside the instance").
- **OPEN-DE3:** the idle pause of the grave timer.
- **OPEN-DE4:** auto-equip from the grave.
- **OPEN-DE5:** the message when the fee can't be paid.
- **OPEN-DE6:** broken and mangled items, and the over-20-Wilderness untradeable rules.
- **OPEN-DE8:** the texts of Check ("Your gravestone will collapse in about N minutes."), of someone else's grave
  ("This isn't your gravestone."), of Death with nothing stored, of the paid fee and of the coffer total are ours.
- **OPEN-DE9:** the grave NPC is removed while its owner is logged out and comes back at login (the timer is paused
  then anyway); Death's dialogue (Talk-to) and his shop are not transcribed.
- **OPEN-DE7:** the first-death tutorial in Death's Office (Death explains graves; the mechanic "is enabled after the
  first time visiting Death"): graves are always enabled here.
