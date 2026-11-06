# Dialogue

Code: plugin-api `Dialogue.kt`. These are suspending steps for content scripts: `chatNpc`, `chatPlayer`, `mesbox`,
`objbox` and `choice`, plus the `Expression` chat-head animations.

Sources: RS Mod (ISC) `ProtectedAccess` dialogue functions, `PlayerInterfaceExtensions` (`ifChatNpcSpecific`,
`ifChatPlayer`, `ifMesbox`, `ifObjbox`, `ifChoice`, `ifOpenChat`) and `TextAlignment`. These are adapted, and the
adaptation is logged in DECISIONS.md.

- **DL1. NPC chat** (`chat_left`): the NPC's chat head (IF_SETNPCHEAD) and expression animation (IF_SETANIM), its
  name and the text, and "Click here to continue". The NPC and the player face each other. The line also goes to the
  chat history as type 11 "name|text".
- **DL2. Player chat** (`chat_right`): the player's own head (IF_SETPLAYERHEAD), same layout.
- **DL3. Message box** (`messagebox`, client script 1508) and **item box** (`objectbox`, client script 2868 buttons,
  zoom 400).
- **DL4. Options** (`chatmenu`, client script 58 `chatbox_multi_init`): a title over 2–5 options. The pick is the
  pause-button sub-slot. Closing the menu ends the dialogue.
- **DL5. Pages.** Text wraps at 380 pixels (chat) or 470 pixels (message box) on the dialogue font's real glyph advances
  (cache archive 13, font `q8_full` = 497, RuneLite `FontLoader`), four lines a page. The text is centred with client
  script 600 at RS Mod's line heights: chat 16/28/20, message box 0/31/24/17.
- **DL6. Expressions.** `chatneu1..4`, `chathap1..4` and the rest are consecutive seq ids, one per line count.
- **Lifecycle.**
  - The script waits for RESUME_PAUSEBUTTON.
  - Closing the chatbox, moving, or opening another interface abandons the wait (`Player.closeModal`), which ends the
    dialogue as in OSRS.
  - The chat modal is unclamped (`chatmodal_unclamp` = 1) and the chatbox background is reset (client script 2379),
    as RS Mod's `ifOpenChat` does.
- **Verified in the client:** a two-page NPC line, the player's line, the option menu (keyboard and click) and the item
  box.
