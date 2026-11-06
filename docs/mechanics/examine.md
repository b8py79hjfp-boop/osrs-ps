# Examine

Code:
- `content/gameframe/.../Examines.kt`.
- Data: `examines.tsv` (`tools/wiki-import/examines.py`).
- Packets OPLOC6 / OPNPC6 / OPOBJ6 become `ClientCommand.Examine`, checked against the cache configs.

Source: OSRS Wiki "Examine" (https://oldschool.runescape.wiki/w/Examine). Texts come from the Wiki's Infobox Item /
Monster / NPC / Scenery `examine` fields. The cache has no examine texts.

- **EX1. Where.**
  - Items: backpack, worn tab, bank and ground items.
  - NPCs and scenery: the right-click Examine.
  - Noted items use their unnoted text.
  - Messages use the client's examine chat types: 27 item, 28 NPC, 29 scenery.
- **EX2. Big stacks.** "If it is a stack of 100,000 or more items (such as a large stack of coins), the text will
  instead state the exact number of items in the stack, e.g. 111,237 x Coins."
- **EX3. Members items on free worlds** ("Login to a members' world to use this object."): every world here is a
  members world.

## OPEN

- **OPEN-EX1.** Ids without a Wiki text answer "It's a <name>." so the player still gets a reply.
