# Rune pouch

Scope: the rune pouch and divine rune pouch (`plugin-api` `RunePouch`, `SpellRunes`; `content/gameframe` `RunePouchItems`,
`BankStorage`, `RunePouchLoadouts`; death in `content/combat` `StorageDeath`). Sources: OSRS Wiki "Rune pouch" and
"Divine rune pouch" (https://oldschool.runescape.wiki/w/Rune_pouch, read 2026-11-07); OpenRune-Server
`content/skills/magic/rune-pouch` (ISC, DECISIONS D-077) for the varbit layout, the pouch interface's ops and its messages;
the rev-242 client scripts (CS n = cache script n) for the bank panel. Everything happens in the tick the click arrives.

| Id | Rule | Source |
|---|---|---|
| RP1 | A rune pouch holds 3 kinds of rune, the divine rune pouch 4, at most 16,000 of each. Any rune in cache enum 55 (`rune_compact_ids`) goes in; anything else: "You can only store runes in the rune pouch." **OPEN-RP1:** the custom amount ("X") is capped at 16,000. | Wiki ("store 16,000 of three types of runes"); enum 55; OpenRune |
| RP2 | Contents live in the client-visible varbits `rune_pouch_type_1..4` (the rune's enum 55 value) and `rune_pouch_quantity_1..4`, so the pouch interface, the spellbook and RuneLite read them. | OpenRune; varbits 29, 1622, 1623, 14285 / 1624-1626, 14286 |
| RP3 | Only a pouch in the backpack counts (spells, the interface, slot count). A pouch in the bank keeps its varbits. | Wiki; OpenRune |
| RP4 | Storing: up to the room in the slot holding that rune, else the first empty slot; no slot: "Your rune pouch has no room for another type of rune."; full: "Your rune pouch cannot hold any more of those runes." | OpenRune |
| RP5 | Spells use pouch runes as if they were in the backpack: the backpack is spent first, the rest comes out of the pouch when the cast's item transaction commits. | Wiki ("cast spells using the runes stored in the pouch") |
| RP6 | "Open" shows interface 190 (varp `if1` = slot count). Pouch slots: left click = the selected quantity (varbit 16201: one / five / all / custom 16206), Withdraw-1 / 5 / X-last / X / All, Examine; backpack runes: the same as Store; drag swaps pouch slots. No room: "You don't have enough inventory space." | OpenRune |
| RP7 | "Empty" moves every slot into the backpack as far as there is room. Empty pouch: "Your rune pouch is empty." | OpenRune |
| RP8 | Any rune used on the pouch stores all of it. | Wiki |
| RP9 | Needle + thread of Elidinis on a rune pouch, 75 Crafting: the divine rune pouch. "Revert" gives the thread back and the rune pouch; the 4th slot's runes are lost (asked first). | Wiki "Divine rune pouch" |
| RP10 | Messages without a source keep OpenRune's text. | OpenRune |
| RP11 | **Bank panel.** In the bank, op 1 "Configure" (CS 297 / 490) shows the bank-side rune pouch panel; its pouch row is `bankside:runepouch_top` children 1..n (CS 4881: Deposit-quantity / 1 / 5 / 10 / last X / X / All, Examine) and puts runes into the bank. "Deposit runes" banks every slot. While the panel is open, runes withdrawn from the bank go into the pouch while there is room. The backpack column's "Empty" (CS 2576, param 595) banks every slot. | Wiki ("Runes withdrawn from the bank while the interface is open will be put into the pouch"); CS 2576, 4881 |
| RP12 | **Load-outs.** Ten load-outs (A-J), each 4 rune / amount pairs in the varbits CS 4895 reads (`rune_pouch_loadout_a_rune1_bitsa` …; runes 1-2 of A, E, I span two varbits, the 3rd amount of D and H three). "Load": the pouch's runes go to the bank, then each load-out rune comes from the bank into the same pouch slot, as many as the bank has up to the amount. **OPEN-RP4:** short of runes: "You don't have enough runes in your bank to fill that load-out."; empty load-out: "That load-out is empty." | Wiki ("ten load-outs … types and quantities"); CS 4895 |
| RP13 | **Setting a slot.** "Set" / "Change" on a load-out row child 1-4 (CS 4885) opens the rune list (client side); its child is the rune's enum 55 value (nature rune = 10, seen in game), then an amount is asked. | CS 4885; in-game packet log |
| RP14 | **Names.** "Change loadout name" (CS 8089, varbit 18023 client side) opens the name list; child c stores enum 5864's value at index c − size(enum 5864) (CS 8092) in `rune_pouch_loadout_name_a..j` (18024-18033), an enum 5865 key. | CS 8089, 8092; enums 5864 / 5865 |
| RP15 | **Death.** PvP death (Wilderness or PvP world): an unlocked pouch is always lost (never kept), and the runes inside drop for the killer even from a trouver-locked pouch. PvM death: the pouch goes to the grave with its runes. | Wiki ("will lose the rune pouch, unless it has been locked … does not protect runes stored within") |

## Tick timeline

- Tick T: the click (IF_BUTTON / OPHELD / OPHELDU) arrives in the input phase; the item transaction and the varbits change
  in the same tick; the varbit and inv updates go out in that tick's info pass.
- Spell casts: the backpack part of the cost is in the cast's transaction; the pouch varbits drop when it commits.

## Not implemented

- "Rune-picking behaviour" (runes picked up or crafted going into the pouch) and the pouch name-tag setting.
