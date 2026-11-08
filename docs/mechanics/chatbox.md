# Chatbox re-skin (D-094, D-096)

The user asked for a cache re-skin of the chatbox (not RuneLite plugins): transparent, with timestamps, mentions
and tabs. Mock: docs/mockups/chatbox-reskin.png.

| Id | Rule | Source |
|---|---|---|
| CB1 | **Timestamps and transparency are the client's own.** Varbit 12384 (`settings_chat_timestamps`) is copied by client script 876 into the client's timestamp flag (cs2 command 5024; script 4742 reads it with 5025 and prefixes each line). Varbit 4608 (`chatbox_transparency`) makes the chatbox transparent in resizable mode (script 2373: resizable and non-zero). `ChatSkin` turns both on once per account at its first login; Settings can switch them off for good. | rev-242 disassembly (scripts 876, 2373, 4742); RuneLite `TimestampPlugin` |
| CB2 | **Mentions:** script 84 (the chatbox line builder, RuneLite's "ChatBuilder") is patched. After each history read, a message that is not a game message (type 0), was not sent by the viewer, and contains the viewer's name (tags removed, any case) is wrapped in `<col=ff9040>`. | `pack/cs2/patch/chatbox_mentions.diff`; locals from RuneLite's ChatBuilder.rs2asm comments |
| CB3 | **OPEN-CB1:** names with spaces. A name the client stores with non-breaking spaces won't match a typed space; this is unverified in game. | — |
| CB4 | **OPEN-CB2:** RuneLite's own overlay of script 84 checks the original's hash, so in our client the patched script replaces it, and RuneLite hooks tied to that overlay (chat-message building callbacks) don't run. To be checked in RuneLite. | RuneLite `scripts/ChatBuilder.rs2asm` |
| CB5 | **Tabs (D-096):** the tab bar is All, Game, Public, Private, Channel, Clan, Trade, **Mentions, Yell, Staff**, 41 px each. Staff (162:575) is shown to staff only. A custom tab shows the All view filtered by patched script 193: Mentions = lines that are not game messages, not the viewer's own, and contain the viewer's name; Yell = game messages starting `[Yell]`; Staff = game messages starting `[Staff]` (tags removed). Clicking an official tab leaves the custom tab (patched 2823). | `pack/interface-patches/chatbox_tabs.toml`; `pack/cs2/patch/chatbox_tab_*.diff`; rev-242 scripts 175, 178, 193, 2823 |
| CB6 | **Names in public chat (D-096):** a staff member or donator on the viewer's world is drawn with our icon (staff rank first, else donator tier) and the donator name colour, from the server's roster in 162:579. The client's own crown is replaced for them; everyone else is unchanged. Private, friends and clan chat names are unchanged. | `chatbox_mentions.diff` hunk 2; `ChatSkin` |
| CB7 | **Staff chat:** `::sc <text>` (moderator and above) goes to staff on every world as `[Staff] <icon>name: text`. | `ModeratorCommands`; D-096 |
| CB8 | **OPEN-CB3:** the tab labels in font 494 at 41 px ("Mentions" is the widest) and the custom tabs' hover art are unverified in the real client; the layout follows the official tabs' modes. | — |
