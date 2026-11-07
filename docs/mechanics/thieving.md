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
- (B) "Blackjack", "Bandit (Pollnivneach)", "Menaphite Thug", "Blackjacking" (Pay-to-play Thieving training), 2026-11-07: knock-out charts,
  the two guaranteed pickpockets, the witness rule, 10 xp per knock-out
- (R) "Rogue equipment" (15% per piece, 100% with all five), "Dodgy necklace" (25%, 10 charges), "Shadow Veil", "Thieving cape" (x1.1),
  "Ardougne Diary" and "Pickpocketing" (diary +10%, pouch cap 28 / 56 / 84 / 140), 2026-11-07
- (X) "Chest (Aldarin Villas)" and the "Chest (...)" pages named in "Pickpocketing" (Thievable chests), "Stall/Thievable", 2026-11-07
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
| TH8 | **Roll modifiers.** The roll of TH3 is taken from the NPC's chart `low` / `high`: with the Ardougne diary (hard or elite complete: anywhere; medium: inside Ardougne) both ends are multiplied by 1.1 and rounded down, replacing the gloves of silence line (the Wiki: "The Ardougne Diary perks override the gloves of silence"); otherwise gloves of silence use line 2 of the chart. A worn Thieving cape (or trimmed) then multiplies the result by 1.1 again, rounded down ("((chance x 1.1) x 1.1)"). The diary is read from the tier-complete varbits (`ARDOUGNE_DIARY_*_COMPLETE`, content/diaries), the Wiki check being "unlocked the diary reward". The coin pouch cap (TH5) is 28, 56 (medium), 84 (hard) or 140 (elite complete). | R, N, P; OPEN-TH13 |
| TH9 | **Rogue outfit.** Each worn piece (mask, top, trousers, gloves, boots) adds 15% to the chance that a pickpocket's loot is doubled; all five make it certain. Doubling multiplies each item's quantity by 2, except a coin pouch, which becomes one pouch plus the coins a pouch holds (a fresh roll of its range). Xp is unchanged. Stalls and chests are not affected. | R |
| TH10 | **Stun avoidance on a failed roll.** Shadow Veil is tried first (15%); if it fails, the dodgy necklace (worn) is tried (25%), which costs one of its 10 charges (kept per player, not per necklace; the tenth crumbles it to dust). A save leaves the player unstunned and unhurt, the attempt still fails and costs the 2-tick delay of a success. | R, T; OPEN-TH4 |
| TH11 | **Chests** (`thieving-chests.tsv`, written by `tools/wiki-import/thieving_chests.py`): "Picklock" on the closed chest of Aldarin Villas (loc 54773, level 36, 200 xp) plays `human_picklock_chest` 3691, rolls the unlock two ticks later with the chart `0 / 150` (`25 / 175` with a lockpick, cache obj 1523, in the backpack). Success: one roll of the loot table (coins 100-200 1/3, 250-500 1/6, 600-1200 1/15; silver bar and gold bar 1/20; uncut sapphire 4/75, emerald 1/25, ruby 2/75, diamond 1/75; spinach roll 1/15; 10-20 blessed bone shards 1/30; eclipse red 1/30; the uncommon seed table, 4/60 spread by the template's weights out of 1048), the xp, and the open chest loc 54774 for 3 ticks (the Wiki "1.8 seconds"). Failure: a second roll with the chart `73 / 17` teleports the player to the well (1379,2971). | X; OPEN-TH15 |
| TH12 | **Blackjacking** (Pollnivneach). "Knock-Out" (op 5) needs a blackjack wielded (oak, willow, maple, in any of the three types), the Thieving level of the target (bearded bandit 45, other bandit 55, Menaphite Thug 65) and no other conscious blackjack target in line of sight within 8 tiles ("Perhaps I shouldn't do this here, I think another bandit will see me."); it is refused during combat ("You can't knock-out the bandit during combat."). The roll is the chart `80 / 240` (thug `78 / 240`). Success: the target is knocked out (anim 1874, spotanim 348, frozen) for 5 ticks, 10 xp, and two guaranteed pickpockets: "Pickpocket" on it then cannot fail, gives the coins directly (40, 50 and 60, doubled by TH9) and the xp (65, 84.3, 137.5), plays the bury-bone animation and delays the player 2 ticks. "Lure" (op 4) makes the target follow the player for 30 ticks. The Feud gate exists in code and is off (`Blackjack.REQUIRE_THE_FEUD`). | B; OPEN-TH12 |

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
| Vegetable | 2 | 10 | 1.2 s | 4706, 4708 |
| Counter (Gu'Tanoth) | 15 | 6.4 | 7.2 s | 2792, 2793 |
| Wine | 22 | 27 | 4.8 s | 14011 |
| Monkey food ("Food Stall") | 5 | 16 | 3.6 s | 4875 |
| Monkey general | 5 | 25 | 4.8 s | 4876 |
| Magic | 65 | 90 | 7.2 s | 4877 |
| Scimitar | 65 | 210 | 19.2 s | 4878 |

Loot per stall: `stalls.tsv` (Bakery: bread 5/20, cake 13/20, chocolate slice 2/20; silk, spice, tea, fur: one item; gem stall: sapphire 105/128,
emerald 17/128, ruby 5/128, diamond 1/128; and so on). The vegetable, wine, Ape Atoll (food, general, magic, scimitar) and Gu'Tanoth counter stalls were added on 2026-11-07 (TH6, same rules). Port Roberts and Mor Ul Rek stalls are not covered (OPEN-TH14).

## Tick timelines

Pickpocket, success: T arrive (anim, message, roll, loot, xp) -> delay to T+2 -> next click acts at T+2.
Pickpocket, failure: T arrive (anim, message, roll fails, hit, stun anim) -> delay to T+10.
Stall: T arrive (anim, message) -> T+2 (loot, xp, stall emptied for its respawn) -> the stall is back at T+2+respawn.

## OPEN

- **OPEN-TH1:** no sourced success chart: Cave goblin (36), Fremennik citizen (45), the awake Pollnivneach bandits (45, 55), Villager, Wealthy
  citizen, Pirate, Digsite workman. Their pages have no `{{Skilling success chart}}`; they are not pickpocketable here. The awake Menaphite
  Thug has a chart (50 / 160, gloves 52 / 168, diary 55 / 176, cape + diary 60 / 193) but no stun damage; awake pickpockets of the Pollnivneach
  bandits and thugs answer "Nothing interesting happens." and only the knocked-out pickpocket works (TH12).
- **OPEN-TH2:** the stun hit is a `DamageRequest` (plugin-api `CombatEvents.kt`) that the combat module applies as a regular
  hit (it can kill), with no source so the player does not auto-retaliate. Whether OSRS counts the NPC as the attacker
  (retaliation, skull logic) is not sourced.
- **OPEN-TH3:** (resolved by TH8: the pouch cap follows the Ardougne diary tier varbits.)
- **OPEN-TH4:** player messages are not on the Wiki and are written from game memory: "You attempt to pick the X's pocket.", "You pick
  the X's pocket.", "You fail to pick the X's pocket.", the level, pouch and restock lines, "You steal <item> from the <stall>.". The dodgy necklace's "Your dodgy necklace crumbles to dust." is also from memory. The NPC's
  shout on a failure (text differs per NPC) and its attack animation are not sent.
- **OPEN-TH5:** the pre-roll tables (easy / medium / hard / elite clue scrolls, "Nothing") are skipped, so H.A.M. members, paladins, gnomes
  and heroes drop their main table slightly more often than on the Wiki; the Rocky pet is not rolled.
- **OPEN-TH6:** guards or the shopkeeper catching a thief at a stall (the Wiki says the owner calls a guard when the player is seen; no
  rule for sight, delay or what the guard does) and the owner refusing to trade for 10 minutes after a theft.
- **OPEN-TH7:** the empty-stall loc: the real replacement locs are not on the Wiki, so every 2x2 stall uses the "Market stall" loc 634.
- **OPEN-TH8:** movement: the Wiki says a stun blocks movement for nine ticks and pickpocketing for eight. The delay (2 + 8 = 10 ticks) blocks
  both for ten. The stun animation 1054 and spotanim 245 are the gameval `stunned_thieving` entries, not checked on a client; the spotanim's
  height is 0.
- **OPEN-TH9:** the Shadow Veil (SV1), Thieving cape, Ardougne diary, Rogue outfit and dodgy necklace are implemented (TH8-TH10). Still open:
  H.A.M. robes (ejection from the hideout), Hosidius / Shayzien / Varlamore guards and the Varlamore and Kastori farmers (they share the
  base NPC's data here), the max cape and other capes that include the Thieving cape (only obj 9777 / 9778 are recognised), scroll boxes
  (the clue pre-roll, OPEN-TH5, so the Rogue outfit's scroll box exception is moot), the dodgy necklace's "Check" op and a charge message.
- **OPEN-TH10:** doors, trap searching and the trapped chests are not implemented (the Aldarin Villas chests are: TH11; see OPEN-TH15).
- **OPEN-TH11:** the "Open-all" and "Open" backpack ops are registered once for all items (`onItemOp` has one owner per text) and ignore any
  obj that is not a coin pouch; another plugin that needs the same op text must share the handler.
- **OPEN-TH12 (blackjacking):** not on the Wiki: how long a knocked-out NPC stays down (5 ticks, `Blackjack.KNOCKOUT_TICKS`), the wake-up (no
  animation is sent), the player's knock-out animation (none), the messages ("You smack the X over the head and render them unconscious.",
  "You fail to knock out the X.", "You need to be wielding a blackjack ..."), the witness radius (8 tiles) and whether unconscious NPCs
  count as witnesses (they do not), what "Lure" does (the NPC follows the player for 30 ticks), the knock-out xp of the Menaphite Thug (the Wiki
  states 10 only for the bandits; 10 is used) and the thug's chart (the "Blackjack" page says low 80, the thug's own page 78; 78 is used). A
  failed knock-out does not make the NPC attack (combat is not driven from content), the "pickpocket with a full inventory resets aggression"
  trick and the 7 / 9 max hit retaliation are not modelled. The Feud gate (`Blackjack.REQUIRE_THE_FEUD`) is `false`: the quest is not in
  content/quests and the varp 435 stage needed is not sourced. Players need no members check (OPEN-PT5 applies).
- **OPEN-TH13 (diary):** "within Ardougne" for the medium tier is a bounding box (x 2456..2688, z 3264..3344) because the Wiki gives no bounds,
  and the check uses the tier-complete varbit although a Jagex quote says the reward must be unlocked (claimed from Two-pints).
- **OPEN-TH14 (Port Roberts):** the Port Roberts stalls (loc ids 58100..58106; levels and xp on "Stall/Thievable") are left out: their pages give
  no respawn ("N/A") and the Wiki says the market guards "behave uniquely" (see "Port Roberts#Marketplace thieving").
- **OPEN-TH15 (chests):** only the Aldarin Villas chests are implemented. The 10-coin, nature rune, 50-coin, steel arrowtip, blood rune and Ardougne
  Castle chests ("Search for traps", trap damage, the open loc ids), the Rogues' Castle chests (diary-dependent loot, noted items, 701.7 xp), the Dorgesh-Kaan chests, the
  Isle of Souls chest and the pirate chests are not. For Aldarin: the 2-tick delay before the roll, the messages ("You attempt to pick the lock on
  the chest.", "You fail to pick the lock.", "You find some treasure in the chest."), the player's unlock animation (`human_picklock_chest`), the "1.8 seconds"
  read as the time the open chest stays open, the teleport chance chart `73 / 17` (the text says roughly 1 in 5 to 1 in 14 failures, with "more data
  is needed") and the well tile (1379,2971, the centre of the Wiki's map square) are assumptions. The uncommon seed rows add up to 7859/7860 of
  the table; the missing 1/7860 falls to the last row. Clue-scroll pre-rolls are skipped (OPEN-TH5).
