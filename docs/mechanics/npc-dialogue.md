# NPC dialogue

Code: `content/world/.../NpcTalk.kt`, data `content/world/.../npc-dialogue.tsv`, importer `tools/wiki-import/npc_dialogue.py`.
Steps: `chatNpc`, `chatPlayer`, `mesbox`, `choice` (docs/mechanics/dialogue.md).

Source: OSRS Wiki `Transcript:` namespace (id 120), pages starting `{{Transcript|npc}}`
(https://oldschool.runescape.wiki/w/Transcript:Hans). CC BY-NC-SA 3.0 (data/PROVENANCE.md).

- **ND1. Talk-to.** "Talk-to" on an NPC type that has a transcript plays it. It is registered as `onOpNpcOption`, which only
  applies to NPC types with no id-specific or named handler for that op, so bankers, shops and any quest NPC with its own
  script keep priority. Types without a transcript do nothing, as before.
- **ND2. Walk.** The transcript is a graph walked from node 0: NPC line (`chatNpc`, NPC name from the cache), player line
  (`chatPlayer`), message (`mesbox`; item boxes `{{tbox|pic=..}}` lose the picture), option menu (`choice`, title
  "Select an Option" by default). Closing a menu or the chatbox ends the conversation. `[player name]` is the player's name.
- **ND3. NPC ids.** Each transcript maps to the ids of the `Infobox NPC` / `Infobox Monster` (`id`, `idN`) on the page of the
  same name (redirects followed); all versions share the tree. Identical trees are stored once.
- **ND4. Which branch.**
  - The "Standard dialogue" section is used (else the first section that is not historical, overhead, treasure-trail,
    pickpocket or pre-release). With sub-sections and no lines of its own, the one whose heading reads negative
    ("not", "no", "hasn't", "before", ...), else the initial/first, is used.
  - `{{tcond}}` groups: the branch whose condition reads negative, else the first. A lone positive condition is dropped.
  - Options with `cond=` are dropped; a menu keeps at most five options; one remaining option plays directly.
  - `{{trandom}}`: the first reply.
- **ND5. Actions.** `{{tact|end}}` ends. Everything else the server cannot execute ends the conversation at that point:
  shop opens, item gifts, forward jumps (`below`/`continue`), free-text actions, text input, missing transcript parts.
  Jumps back are links: `initial` returns to the conversation's first menu, `previous`/`above` to the menu the branch
  came from (the first menu when there is none). `{{qact}}` descriptions (NPC/player gestures) are skipped without ending. Lines with an
  unresolved `[placeholder]` other than the player's name end the conversation before them.

## Coverage (import of 2026-11-06)

5,802 transcript pages; 3,104 NPC transcripts; 44 without NPC ids; 247 without usable dialogue (empty or only actions); 2,396
trees covering 5,535 NPC ids. Dropped: 3,417 other sections, 1,446 of 2,398 conditional branches, 262 conditional options,
44 options over five, 2,026 random replies. 1,250 jumps back to a menu are linked. Conversation ends at an action: 612 shop
opens, 180 item gifts, 860 forward jumps, 958 other, 57 missing parts, 193 placeholder lines.

## OPEN

- **OPEN-ND1. Conditions.** No quest, item, skill or ironman state is modelled; every player gets the default branch.
  Conversations for players further along in a quest are not reproduced.
- **OPEN-ND2. Actions.** Shops, item gifts, teleports, quest starts and forward jumps end the conversation. "Opens shop"
  could chain into the shop plugin; not done.
- **OPEN-ND3. Expressions.** Transcripts do not record chat-head expressions; all lines are `NEUTRAL`.
- **OPEN-ND4. Random replies and repeats.** The first reply of a random pick is always used; "talked to again" variants
  are not selected.
- **OPEN-ND5. Which NPC speaks.** Every non-player speaker is shown as the talked-to NPC (multi-NPC scenes are not split).
- **OPEN-ND6. Op text.** Only NPC types whose cache op text is "Talk-to" can reach the dialogue.
