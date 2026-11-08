# Chatbox re-skin (D-094)

The user asked for a cache re-skin of the chatbox (not RuneLite plugins): transparent, with timestamps, mentions
and tabs. Mock: docs/mockups/chatbox-reskin.png.

| Id | Rule | Source |
|---|---|---|
| CB1 | **Timestamps and transparency are the client's own.** Varbit 12384 (`settings_chat_timestamps`) is copied by client script 876 into the client's timestamp flag (cs2 command 5024; script 4742 reads it with 5025 and prefixes each line). Varbit 4608 (`chatbox_transparency`) makes the chatbox transparent in resizable mode (script 2373: resizable and non-zero). `ChatSkin` turns both on once per account at its first login; Settings can switch them off for good. | rev-242 disassembly (scripts 876, 2373, 4742); RuneLite `TimestampPlugin` |
| CB2 | **Mentions:** script 84 (the chatbox line builder, RuneLite's "ChatBuilder") is patched. After each history read, a message that is not a game message (type 0), was not sent by the viewer, and contains the viewer's name (tags removed, any case) is wrapped in `<col=ff9040>`. | `pack/cs2/patch/chatbox_mentions.diff`; locals from RuneLite's ChatBuilder.rs2asm comments |
| CB3 | **OPEN-CB1:** names with spaces. A name the client stores with non-breaking spaces won't match a typed space; this is unverified in game. | — |
| CB4 | **OPEN-CB2:** RuneLite's own overlay of script 84 checks the original's hash, so in our client the patched script replaces it, and RuneLite hooks tied to that overlay (chat-message building callbacks) don't run. To be checked in RuneLite. | RuneLite `scripts/ChatBuilder.rs2asm` |
| CB5 | **Tabs:** today's official tab bar (All, Game, Public, Private, Channel, Clan, Trade) is unchanged. New tabs need the chatbox interface (162) itself edited, which the packer can't do yet; they wait for the user's list. | — |
