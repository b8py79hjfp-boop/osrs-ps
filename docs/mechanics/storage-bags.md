# Herb sack, seed box, gem bag (bank)

Scope: `content/skills` `HerbSack`, `SeedBox`, `Bags` (gem bag bank op), `Farming` (seed box on a patch). Sources: OSRS Wiki
"Herb sack", "Silklined herb sack", "Seed box", "Gem bag" (https://oldschool.runescape.wiki/w/Herb_sack, `/w/Seed_box`,
`/w/Gem_bag`, read 2026-11-08); cache obj params (param 595 = the bank's storage op text, param 709 = a seed's patch type);
rev-242 client scripts (CS n). Everything happens in the tick the click arrives.

## Herb sack

| Id | Rule | Source |
|---|---|---|
| HS1 | Holds up to 30 (silklined: 100) of each of the 15 grimy normal herbs (guam … torstol, with huasca and snapdragon), in player-saved keys. **OPEN-HS1:** fill messages and the Check line format ("N x Grimy …"). | Wiki |
| HS2 | "Fill" (left click) stores every grimy herb in the backpack up to the room; using a grimy herb on the sack stores that herb; anything else: "You can't possibly put that in there, it would spoil your sack." | Wiki |
| HS3 | "Empty": lowest level herb first, as far as the backpack has room; in the bank, "Empty" (param 595, bank.md B30) banks everything. Empty sack: "The herb sack is empty." | Wiki |
| HS4 | "Check": "You look in your herb sack and see:" then each stored herb. | Wiki |
| HS5 | "Open" / "Close": an open sack takes grimy herbs picked up off the ground or harvested from herb patches while there is room. | Wiki |
| HS6 | Pristine spider silk used on the herb sack makes the silklined herb sack; the herbs stay. **OPEN-HS2:** the message. | Wiki "Silklined herb sack" |
| HS7 | Death: the herbs stay in the sack, except on a PvP death, when they drop for the killer (one obj per herb), even if the sack is kept. **OPEN-HS3:** the "Always empty herb sacks on death" setting (varbit 15316) is not modelled. | Wiki |

## Seed box

| Id | Rule | Source |
|---|---|---|
| SB1 | Six kinds of seed, any amount, in inv 573 (`seed_box`). A seed is any obj with param 709 (seeds, spores, frags). **OPEN-SB1:** messages. | Wiki; inv 573; param 709 |
| SB2 | "Fill" stores every seed in the backpack (a seventh kind stays: "Your seed box is full."); using a seed on the box stores it. | Wiki |
| SB3 | "Empty" moves every kind into the backpack; in the bank, "Empty" (param 595) banks them straight from inv 573. | Wiki |
| SB4 | "Check" opens interface 128 (`hosidius_seedbox`, inv 573 drawn by CS 149 / 153 in the seed layer, drag to reorder; "Fill box from inventory" / "Empty all to inventory" buttons) and lists the contents in chat. | Wiki (3 June 2026, 20 July 2022); interface 128 |
| SB5 | "Open" / "Close": an open box takes seeds picked up while it can. | Wiki |
| SB6 | Used on a farming patch, the box plants the first seed, in box order, that fits the patch, the Farming level and the seed count. **OPEN-SB2:** the message when none fits. | Wiki (3 June 2026) |

Death: the seed box is an untradeable item with its contents in inv 573, so they follow it (grave, Death's Office);
**OPEN-SB3:** the "Always empty seed box on death" setting (varbit 15314) is not modelled.

## Gem bag in the bank

| Id | Rule | Source |
|---|---|---|
| GB1 | The gem bag's bank op is "Empty" (param 595): every gem goes into the bank. An open gem bag also takes uncut gems picked up off the ground ("You put it straight into your open gem bag."). | Wiki "Gem bag"; obj param 595 |
