# Settings

Scope: `content/gameframe` `SettingsTab` (side panel), `AllSettings` (interface 134), `Login`. Sources: OpenRune-Server
`content/interfaces/settings` (ISC, DECISIONS D-078) for which player variable each setting writes, its default, range
and prompt (`settings-configs.tsv`) and the new-account defaults (`account-defaults.tsv`); the rev-242 client scripts.
All settings are player variables (varps / varbits); the player save keeps every non-zero varp, so a setting changed in
game is remembered across logins. Camera zoom level, brightness and the client window layout are stored by the client
itself (its local preferences), not by the server.

| Id | Rule | Source |
|---|---|---|
| ST1 | "All settings" (settings tab) opens interface 134 as the main modal (CS 2524 first). The client opens on category 0 and draws every setting from cache structs (enum 422 categories, param 745 setting lists). Writing the category / search varbits while it opens hides the window, so only clicks change them. | OpenRune `AllSettingsScript`; tested in game |
| ST2 | A row click (`settings:settings_clickzone`, sub = row of the shown list) selects that setting (varbit `settings_selected_setting`); a checkbox flips its variable (collection-log chat / popup, setting ids 85 / 171, are bits 0 / 1 of one varbit). | OpenRune |
| ST3 | A dropdown entry (`settings:dropdown_buttons`, sub / 3 = entry) writes that entry's value; for a keybind, any other keybind on the same key is cleared. The level-up popup dropdown (setting 315) writes `option_level_up_popup_list` and `option_level_up_message_disabled`. | OpenRune `SettingUtils` |
| ST4 | A number setting asks for an amount, clamps it to its range and also sets its enable-toggle setting when one is listed. | OpenRune |
| ST5 | Category tabs (`categories_clickzone`), search, "tooltips inside interfaces" and "declutter" toggles. | OpenRune |
| ST6 | Volume sliders (window and settings tab) set the volume varp client-side and report the final value on release as a script trigger: the window sends (setting id, volume), CS 9266 / 9272 / 9278 (master 319, music 30, sounds 31, area 32); the tab's slider bobbles send (volume). The server stores it; clicking a bobble slot still sets 5% steps. | CS 9266; OpenRune `AudioSettingsScript`; tested in game |
| ST7 | New-account defaults (each settings row's default, then OpenRune's account defaults: volumes 100 / 20 / 45 / 25, XP drops, Esc closes interfaces, …) are written once per account, only into variables still at 0, so existing choices are never overwritten. | OpenRune `setDefaultOptions` |
| ST8 | Login sends CS 626: the camera zoom limits (varcs 1338-1341, 128..896) that the scroll-wheel zoom clamps to; without them zoom does nothing. | CS 626 / 42; OpenRune `LoginScript` |
| ST9 | A colour setting opens interface 288 (`colour_pallet`) in `settings:popup` (CS 4185 with the current colour, CS 4020); Save submits the colour as a count reply (CS 614 `resume_countdialog`), stored as RGB + 1 (0 = the setting's default colour); CS 101 redraws chat. | CS 4185 / 614; OpenRune; tested in game |
| ST10 | Reset buttons ask "Yes." / "No." first: opaque chat colours (11 settings), split chat colour, quest list colours (back to default), volume sliders (100 / 20 / 45 / 30), keybinds (each keybind's default), and wipe playlist 1-3 (`music_playlist_n_track_1..100` cleared). The question in the chatbox closes the settings window. | OpenRune `confirmationSettings` |
