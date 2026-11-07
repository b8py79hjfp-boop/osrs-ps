# Collection log (rev 242)

Code: `content/collectionlog/.../CollectionLog.kt` (rules), `LogData.kt` (TSV loader). Data: `collection-log.tsv`, written by
`tools/wiki-import/collection_log.py` from our cache (tab structs 471..475, page structs, enums) and two client scripts
(2735 counter lines, 4103 page-complete varbits). 125 pages (57 bosses, 4 raids, 11 clues, 22 minigames, 31 other),
1,738 distinct slots.

Sources:
- CLW: "Collection log", https://oldschool.runescape.wiki/w/Collection_log (fetched 2026-11-07)
- SET: "Settings", https://oldschool.runescape.wiki/w/Settings (the two notification settings)
- CS: client scripts of cache 2757 (exported as rs2asm): 2240 (interface load), 2388/2389 (tab), 2728 (tab button), 2730/2731
  (page list), 2732 (page items), 2735 (counter lines), 3310 (account summary rows), 3343 (notification popup), 4103
  (page-complete varbits), 5188/5189 (setting toggles), 7797/7798 (redraw everything)
- RL: RuneLite `CollectionLogPlugin` (chat pattern "New item added to your collection log: (.*)")

## Rules

| # | Rule | Source |
|---|---|---|
| CLOG1 | The log is tabs (Bosses, Raids, Clues, Minigames, Other) of pages, each page a list of item slots. Slots are tracked by the item, not by where it came from: an item on several pages is one count, and counts once in the header counter. | CLW: "Each entry in the log is tracked by the item itself, not where it was obtained. ... Items that are listed on multiple pages are not tracked separately. However, they only count as one in the counter displayed on the header" |
| CLOG2 | Only items with a slot are logged. The count per item is kept (the interface shows "n x item"). | CLW: "It records nearly every unique drop"; CS 2732 (`inv_total` of the item shown as the stack count) |
| CLOG3 | An NPC drop is logged for the player it is made for when it is created, whether or not it is picked up. Noted drops log the unnoted item. Other sources (chests, caskets, minigame rewards, pets) log through the same `LootEvent`; none of them exist in the server yet. | CLW: "records ... every unique drop the player has obtained"; timing is RL's chat message at kill time. OPEN-CLOG2 |
| CLOG4 | First time an item is logged: chat line `New item added to your collection log: <col=ef1020>Name</col>` if bit 0 of varbit `option_collection_new_item` (11959) is set; a popup (interface 660, script 3343, title "Collection log", text "New item:<br>Name") if bit 1 is set. The two settings are "Collection log - New addition notification" (chat) and "Collection log - New addition popup". | SET; CS 5188 (bit 0) / 5189 (bit 1); RL |
| CLOG5 | Opening: the modal interface 621 on the main modal, Bosses tab, first page. The client builds the lists itself; the server sets the header varps (`collection_count` 2943 = obtained, `collection_count_max` 2944 = distinct slots, 4612 = copy shown in the account summary), the page-complete varbits, loads the counts into inv `collection_transmit` (620, 500 slots) and then runs client script 7797 (redraw: tab, the tab's four layer components, tab struct, selected row). | CS 2240, 2389, 2731, 2732, 7798; inv 620 size 500 |
| CLOG6 | Clicks that reach the server: the five tab buttons ("View", components 4..8) show that tab's first page; a page row ("Check", child index = key of the tab's page enum) shows that page. Nothing else needs the server (search, scrolling, burger menu are client scripts). | CS 2728, 2731 (rows are created with `cc_setop` and no `onop` script), interface 621 component actions |
| CLOG7 | Counter lines above a page: `<label>=<varp>` pairs in the TSV (varps 2048, 2941, 2942 are reused by every page). Kill counts ("<NPC> kills") are counted per NPC name for the player the loot goes to (`DeathEvent.lootFor`), from the first kill after this feature. Other counters (completions, chests opened, delves, ...) are 0. | CS 2735; OPEN-CLOG3 |
| CLOG8 | A page is "completed" (green, varbit from script 4103) once every one of its slots is logged. | CS 4103; CLW "The colour of each section of the log changes based on its completed status" (changes list, 7 April 2021) |
| CLOG9 | Opening from the account summary tab: the row "Collections Logged" (op 1). Also `::collectionlog`. | CS 3310 / 3950 / 3952 |

## Storage

Counts are saved account values `clog.<key>` (`key` = the obj the client counts the slot under: cache enum 3721 remaps a few
log items to a variant obj, e.g. 13273 to 25624), kill counts `clog.kc.<npc name lower case>`. Inv 620 holds only what the
client has to read (the current page first, then other obtained items while slots remain) and is emptied when the log
closes, so it never reaches the save. D-057.

## OPEN

- **OPEN-CLOG1** Default of the notification setting for a new account: chat on, popup off (set once at first login). The Wiki
  lists the settings but not their defaults.
- **OPEN-CLOG2** Exactly when a drop is logged (at the kill, as RuneLite's chat hook shows) is taken from RL, the Wiki states no timing.
- **OPEN-CLOG3** Kill-count labels that are not "<NPC name> kills" (Revenant, Barrows, raids, clues, delves, Gauntlet, LMS, ...) and
  personal bests are not tracked; they show 0 / "N/A". Kills before this feature are not known.
- **OPEN-CLOG4** Per-item caps ("Zulrah's scales up to 65,535, bracelets of ethereum up to 250", CLW) are not applied; counts saturate at
  2^31-1.
- **OPEN-CLOG5** Ironman "use an item on the collection log" backfill, the "Collection log" item (22711, from The Collector), staves of
  collection and the Collection Overview interface (908) are not implemented.
- **OPEN-CLOG6** Search: results are drawn by the client from inv 620; a page reached by search that the server has not loaded shows its
  counts only if the item is among the first 500 obtained (CLOG5). Opening the page by its row always loads it.
- **OPEN-CLOG7** The popup is the generic notification (script 3343) with its default colour; the real popup's exact text and colour are
  unverified.
- **OPEN-CLOG8** The click slot of the "Collections Logged" row in the summary panel (child 6) is derived from script 3310's row order
  (Combat Level, Total Level, Total XP, quests, diaries, combat tasks, collections); verify with a real client.
- **OPEN-CLOG9** Pets, clue caskets, chests and minigame rewards do not publish `LootEvent` yet (the sources do not exist).
- **OPEN-CLOG10** The first draw is sent two ticks after the modal opens (the counts must reach the client first, vars and invs go out
  in the info pass); the real server's timing is unknown.
