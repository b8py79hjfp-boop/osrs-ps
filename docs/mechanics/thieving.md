# Thieving

`content/skills/.../Pickpocketing.kt`, `StallThieving.kt`, data in `ThievingData.kt` and the resource tables `pickpocket.tsv`
and `stalls.tsv` (written by `tools/wiki-import/thieving_pickpocket.py` and `thieving_stalls.py`, re-runnable). Shared rules
(success roll, filterable messages): skilling.md.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-06:
- (P) "Pickpocketing", https://oldschool.runescape.wiki/w/Pickpocketing (cadence, stun, coin pouch cap, equipment, thievable NPC and
  chest tables)
- (N) each NPC's page: `{{Thieving info}}` (level, xp, stun time, stun damage), `{{Skilling success chart}}` (`low` / `high` of the
  pickpocket roll: line 1 "Normal", line 2 "Gloves of silence"), `{{DropsLineSkill}}` (loot), e.g. /w/Man, /w/Master_Farmer
- (C) "Coin pouch", https://oldschool.runescape.wiki/w/Coin_pouch (item ids per NPC, coins per pouch, the 28 stack, Open / Open-all)
- (T) "Stun (status)", https://oldschool.runescape.wiki/w/Stun_(status) (Thieving stun and Dodgy necklace)
- (S) "Stall/Thievable" (https://oldschool.runescape.wiki/w/Stall/Thievable, level, xp, respawn time, XP/h derivation "1 tick to start
  the interaction and 2 ticks to perform the thieving animation") and each stall page (`{{Infobox Scenery}}` loc ids, loot)
- (K) "Skilling success rate" (SK1), "Marks of grace" is agility.md
- (L) the cache (cache 2757): the "Pickpocket" op on the NPCs (op 3 of most), op 2 "Steal-from" on the stall locs, gameval seqs
  `human_pickpocket` 881, `stunned_thieving` 1054 and spotanim `stunned_thieving` 245, obj `hunting_silent_gloves` 10075 (gloves of silence)

## Rules

| # | Rule | Source |
|---|---|---|
| TH1 | "Pickpocket" on an NPC with data below needs the listed Thieving level (current level). The data is chosen by the NPC's cache name; the Wiki's lumped NPCs share a row (Warrior / Al Kharid warrior, the gnomes, Knight of Ardougne / Varlamore, Bandit Camp bandits = npcs 690 and 695, elves = npcs 9055..9117, Darkmeyer vyres = npcs 9688..9713). | N, P, L |
| TH2 | Timeline: on the arrival tick T the player faces the NPC, plays animation 881, gets "You attempt to pick the <npc>'s pocket." and the roll is made. A success gives the loot and xp at T and delays the player 2 ticks (next attempt at T+2: "NPCs may be pickpocketed every two ticks"). | P, L |
| TH3 | Roll: SK1 with the NPC's `low` / `high`; wearing gloves of silence (worn slot 9) uses the chart's line 2. A failure: "You fail to pick the <npc>'s pocket.", the NPC faces the player and hits them for a random whole number in the Wiki damage range (plain hit, published as `DamageRequest`), the player plays the stun animation and spotanim, and cannot act for 2 + 8 ticks from T ("every pickpocket will take on average 2+8(1-p) ticks"; "further pickpocketing [blocked] for eight ticks"). | N, P, T, L |
| TH4 | Loot: the "Always" rows are given every time; the "main" rows are mutually exclusive slices of [0, 1) (their rarities add up to at most 1, the rest is nothing). A row's quantity is uniform in its range. Wiki "Coins" rows are coin pouches in game: the NPC's pouch obj is given instead and holds the Wiki coin amount (fixed, or a fresh roll in the range per pouch when opened). | N, C |
| TH5 | Coin pouches stack to 28 of one kind; at 28 the pickpocket is refused until they are opened. "Open" opens one pouch, "Open-all" the whole stack, for the sum of each pouch's amount; the pouches are removed and the coins added in one item transaction. | P, C, L |
| TH6 | Stall (op 2 "Steal-from" on the listed locs): needs the level. Stealing never fails. On the arrival tick the player plays animation 881 and gets "You attempt to steal from the <stall>."; two ticks later the loot (one roll of the stall's exclusive table) and xp are given and the stall is empty for its respawn: Wiki seconds / 0.6, rounded (2.4 s = 4 ticks). Players who steal on the same tick all succeed. A second thief arriving while it is empty gets "The stall has been emptied. It needs a moment to restock." | S, L |
| TH7 | While empty a 2x2 stall is shown as the "Market stall" loc (cache 634) through `addLoc` with the respawn as its duration; the loc reverts by itself. Stalls that are not 2x2 (loc ids 4874, 6945, 27537, 28823) only keep the cooldown. The Keldagrim bakery stall (loc 6163) respawns in 9.6 s = 16 ticks. | S, L |

### Pickpocketable NPCs (level / xp / stun damage / success low / high without and with gloves of silence)

| Wiki page | Lvl | Xp | Damage | Normal | Gloves |
|---|---|---|---|---|---|
| Man, Woman | 1 | 8 | 1 | 180 / 240 | 189 / 252 |
| Farmer | 10 | 14.5 | 1 | 150 / 240 | 157 / 252 |
| H.A.M. Member | 15 | 22.2 | 1-3 | 135 / 239 | 141 / 250 |
| Warrior, Al Kharid warrior | 25 | 26 | 2 | 100 / 240 | 105 / 252 |
| Rogue | 32 | 36.5 | 2 | 75 / 240 | 78 / 252 |
| Master Farmer | 38 | 43 | 3 | 90 / 240 | 94 / 252 |
| Guard | 40 | 46.8 | 2 | 50 / 240 | 52 / 252 |
| Bandit (Bandit Camp) | 53 | 79.4 | 3 | 50 / 240 | 52 / 252 |
| Knight of Ardougne / Varlamore | 55 | 84.3 | 3 | 50 / 240 | 52 / 252 |
| Watchman | 65 | 137.5 | 3 | 15 / 160 | 15 / 168 |
| Paladin | 70 | 131.8 | 3 | 35 / 160 | 36 / 168 |
| Gnome (and child, woman) | 75 | 133.3 | 1 | 33 / 140 | 34 / 147 |
| Hero | 80 | 163.3 | 3 | 39 / 160 | 40 / 168 |
| Vyre | 82 | 306.9 | 5 | 8 / 128 | 8 / 134 |
| Elf | 85 | 353.3 | 5 | 6 / 100 | 6 / 105 |
| TzHaar-Hur | 90 | 103.4 | 4 | -200 / 200 | -210 / 210 |

The loot of each row is in `pickpocket.tsv` (exact fractions as the Wiki prints them, e.g. Master Farmer's 485/1000 allotment seeds
as one overall rarity per seed). Coins per pouch: Man 3, Farmer 9, H.A.M. 1-21, Warrior 18, Rogue 25-40, Guard 30, Bandit 30,
Knight 50, Watchman 60, Paladin 80, Gnome 300, Hero 200-300, Vyre 230-315, Elf 280-350 (C).

### Stalls (level / xp / respawn / loc ids)

| Stall | Lvl | Xp | Respawn | Locs |
|---|---|---|---|---|
| Bakery | 5 | 16 | 2.4 s | 11730, 6163 (Keldagrim 9.6 s), 6945, 51937 |
| Tea | 5 | 16 | 2.4 s | 635 |
| Crafting | 5 | 20 | 4.8 s | 4874, 6166 |
| Silk | 20 | 24 | 4.8 s | 11729, 36569, 51933 |
| Fruit | 25 | 28.5 | 2.4 s | 28823, 27537 |
| Seed | 27 | 10 | 2.4 s | 7053 |
| Fur | 35 | 45 | 7.2 s | 11732, 4278, 51934 |
| Fish | 42 | 42 | 7.2 s | 4277, 4705, 4707, 31712 |
| Crossbow | 49 | 52 | 4.8 s | 17031 |
| Silver | 50 | 205 | 19.2 s | 11734, 6164, 36570 |
| Spice | 65 | 92 | 6 s | 11733, 36572, 51936 |
| Gem | 75 | 408 | 60 s | 11731, 6162, 36571, 51935 |

Loot per stall: `stalls.tsv` (Bakery: bread 5/20, cake 13/20, chocolate slice 2/20; silk, spice, tea, fur: one item; gem stall: sapphire 105/128,
emerald 17/128, ruby 5/128, diamond 1/128; and so on). Vegetable, wine, monkey, Port Roberts, Mor Ul Rek and Ape Atoll stalls are not covered.

## Tick timelines

Pickpocket, success: T arrive (anim, message, roll, loot, xp) -> delay to T+2 -> next click acts at T+2.
Pickpocket, failure: T arrive (anim, message, roll fails, hit, stun anim) -> delay to T+10.
Stall: T arrive (anim, message) -> T+2 (loot, xp, stall emptied for its respawn) -> the stall is back at T+2+respawn.

## OPEN

- **OPEN-TH1:** no sourced success chart: Cave goblin (36), Fremennik citizen (45), the Pollnivneach bandits (45, 55), Villager, Wealthy
  citizen, Menaphite thug, Pirate, Digsite workman. Their pages have no `{{Skilling success chart}}`; they are not pickpocketable here.
- **OPEN-TH2:** the stun hit is a `DamageRequest` (plugin-api `CombatEvents.kt`) that the combat module applies as a regular
  hit (it can kill), with no source so the player does not auto-retaliate. Whether OSRS counts the NPC as the attacker
  (retaliation, skull logic) is not sourced.
- **OPEN-TH3:** the 28 pouch cap is the base value; the Ardougne diaries raise it to 56, 84 and 140 (no diary state exists).
- **OPEN-TH4:** player messages are not on the Wiki and are written from game memory: "You attempt to pick the X's pocket.", "You pick
  the X's pocket.", "You fail to pick the X's pocket.", the level, pouch and restock lines, "You steal <item> from the <stall>.". The NPC's
  shout on a failure (text differs per NPC) and its attack animation are not sent.
- **OPEN-TH5:** the pre-roll tables (easy / medium / hard / elite clue scrolls, "Nothing") are skipped, so H.A.M. members, paladins, gnomes
  and heroes drop their main table slightly more often than on the Wiki; the Rocky pet is not rolled.
- **OPEN-TH6:** guards or the shopkeeper catching a thief at a stall (the Wiki says the owner calls a guard when the player is seen; no
  rule for sight, delay or what the guard does) and the owner refusing to trade for 10 minutes after a theft.
- **OPEN-TH7:** the empty-stall loc: the real replacement locs are not on the Wiki, so every 2x2 stall uses the "Market stall" loc 634.
- **OPEN-TH8:** movement: the Wiki says a stun blocks movement for nine ticks and pickpocketing for eight. The delay (2 + 8 = 10 ticks) blocks
  both for ten. The stun animation 1054 and spotanim 245 are the gameval `stunned_thieving` entries, not checked on a client; the spotanim's
  height is 0.
- **OPEN-TH9:** equipment and effects that change the roll or the loot: Thieving cape (+10%, only "cape + diary" is charted), Ardougne cloak
  and diary bonuses (+10% in Ardougne, everywhere after the hard diary), the Rogue outfit (double loot), Dodgy necklace (25% to avoid the
  stun, 10 charges), H.A.M. robes (ejection from the hideout), Hosidius / Shayzien / Varlamore guards and the
  Varlamore and Kastori farmers (they share the base NPC's data here).
- **OPEN-TH10:** chests and doors (lockpicking, trap searching and their respawn table in "Pickpocketing") are not implemented.
- **OPEN-TH11:** the "Open-all" and "Open" backpack ops are registered once for all items (`onItemOp` has one owner per text) and ignore any
  obj that is not a coin pouch; another plugin that needs the same op text must share the handler.
