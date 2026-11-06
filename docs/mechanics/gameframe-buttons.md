# Gameframe buttons

Scope: server-side handling of the standard fixed-mode gameframe tabs and orbs (`content/gameframe`). Behaviour source
for every row is RS Mod v2 (https://github.com/rsmod/rsmod, ISC; clone read at 2026-11-06) unless another source is
named; component, varp, varbit, seq and spotanim ids are our gameval symbols (cache 2757); op lists are from
`cache-dump interface <id>`. No code was copied: behaviour and id tables were re-expressed against `plugin-api`
(DECISIONS D-027). A button click is one IF_BUTTON packet, run directly in the player's turn (no queue); `onButton`
runs only while the button's interface is open (brief §12).

| # | Rule | Source |
|---|---|---|
| O1 | Run orb (`orbs:runbutton`) and `settings_side:runmode` toggle run mode and close the open modal. Run mode also writes varp `option_run` (173), which the orb displays; it is re-synced at login because the saved run mode is restored without a varp. | RS Mod `RunModeScript` (orb + settings button both call `toggleRun`), `ifClose` |
| O2 | XP drops orb (`orbs:xp_drops`): op 1 flips varbit `xpdrops_enabled` (4702); op 2 "Setup" opens `xpdrops_setup` (137) as the main modal. | Cache interface 160 actions (`"*"`, `"Setup"`), varbit name; RS Mod has no handler (OPEN-O2) |
| O3 | Quick-prayer orb op 2 "Setup" opens `quickprayer` (77) in the prayer side slot and enables op 1 on its 0..N slots; a slot click flips its bit in varbit `quickprayer_selected` (4102, same bit layout as `prayer_allactive`) after the Prayer level check; "Done" reopens the prayer book. **OPEN-O3:** the orb's op 1 (activate/deactivate) is not handled: it must set `prayer_allactive` from `quickprayer_selected`, start/stop the drain timer and refresh the head icon, which live in content/combat `Prayers`. Conflicting prayers selected together are also not resolved here. | RS Mod `QuickPrayerScript` |
| O4 | Special attack orb: handled in content/combat. Health orb "Cure", store and wiki orb: not handled (no poison; store/wiki are client features). | — |
| O5 | World map orb: no server handler (the map is client-drawn); the click is accepted silently. | RS Mod has no `orbs:worldmap` script |
| S1 | Settings tabs (`settings_tab`, `audio_tab`, `display_tab`) set varbit `settings_side_panel_tab` to 0/1/2. | RS Mod `SettingsSideScript` |
| S2 | "All Settings" opens `settings` (134) as the main modal. | RS Mod `openAllSettings` (an overlay there; a modal here so the client's close request closes it) |
| S3 | Accept aid, skull prevention flip varbits `option_acceptaid` (4180) and `skull_prevent_enabled` (13131). Accept aid is not yet read by trade code. | RS Mod `ControlSettingsScript` |
| S4 | Attack priority buttons (sub 1..5 player, 1..4 NPC) store sub-1 in varps `option_attackpriority` (1107) / `option_attackpriority_npc` (1306). Not yet read by the option-ordering code. | RS Mod `ControlSettingsScript` |
| S5 | Volume: icon mutes (saves the previous volume in `option_*_saved`, restores it, 100 when none saved); bobble sub n sets min(100, 5n). Varps `option_master_volume` (3796), `option_music` (168), `option_sounds` (169), `option_areasounds` (872). | RS Mod `AudioSettingsScript` |
| S6 | Zoom toggle (op 1) flips varbit `camera_zoom_mouse_disabled` (6357); op 2 "Restore Default Zoom" is client-side. | RS Mod `DisplaySettingsScript`, cache actions |
| S7 | Brightness slider: client-local; RS Mod only re-syncs the varbit, so the click is accepted silently. | RS Mod `DisplaySettingsScript` |
| S8 | **OPEN-S8:** not handled: house options and bond pouch (need a side-modal destination), client type buttons (gameframe switch), "Toggle new renderer", music-unlock-text toggle (not in `settings_side` in our cache), and resuming music when volume rises from 0 (no music message in plugin-api). | — |
| Q1 | Worn items tab "Equipment stats" opens `equipment` (84) as the main modal only. **OPEN-Q1:** the stat texts (RS Mod `updateBonuses`) need an IF_SETTEXT server message (not in protocol-api), the paired `equipment_side` (85) needs a second modal destination in the engine, and removing worn items from inside the panel is not wired. | RS Mod `EquipmentStats` |
| Q2 | Price checker and items kept on death answer with a "not available yet" game message. **OPEN-Q2/Q3:** both need paired interfaces plus inv-driven client scripts (RS Mod `GuidePriceScript`, `ItemsKeptOnDeathScript`). | RS Mod `equipment` module |
| Q4 | Call follower: "You do not have a follower." (no followers exist). | RS Mod `EquipmentTabScript` |
| E1 | Emote names come from cache enum 1000 (slot -> name). Op 1..3 are enabled on every slot at login; op 1 plays the emote seq, op 2 the `_loop` seq. Any emote closes the open modal and interrupts the current action. | RS Mod `EmotesScript.selectEmote`; cache enum 1000 |
| E2 | Unlock-gated emotes check `varbit >= state` (1, or 7 for the Lost Tribe goblin emotes) and otherwise send the unlock text as a game message. RS Mod shows the text in a message box; plugin-api has no dialogue, so it is a game message. | RS Mod `lockedLoopAnimDialog` |
| E3 | Looping emotes are cancelled (anim -1) when the player next moves. RS Mod uses the `emote_cancelanim` walk trigger; we poll with a soft timer. **OPEN-E3:** replace with an engine walk trigger. | RS Mod `onPlayerWalkTrigger(cancelanim)` |
| E4 | Skill cape emote: the worn cape (wear pos 1) is matched by cache name ("<Skill> cape" / "(t)", Max cape, Quest point cape, Music cape) and plays its seq + spotanim. RS Mod matches by content group and `statreq1_skill`; our cache has no gameval for either, so the name is used. | RS Mod `skillCapeEmote`, `skill_cape_anims/spots` |
| E5 | Premier Shield cycles bronze/silver/gold, 4-tick cooldown ("You're already doing that."); Crazy dance alternates style/lasso. Both counters are per session (RS Mod keeps them in varbits that our cache lacks). | RS Mod `premierShieldEmote`, `crazyDanceEmoteSelector` |
| E6 | Air guitar needs varbit `emote_musiccape`; the accompanying jingle is not played (no jingle message). | RS Mod `airGuitarEmote` |
| E7 | **OPEN-E7:** Uri transform, Relic unlock and the Achievement diary cape emote (NPC spawn, transmog, delays) answer "That emote isn't available yet."; "can't perform in combat" checks are not applied; Flap's chicken-outfit variant is not applied. | RS Mod `EmotesScript` |
| J1 | Side journal sub-tabs set varbit `side_journal_tab` (0 summary, 1 quests, 2 tasks) and open `account_summary_sidepanel` / `questlist` / `area_task` in `side_journal:tab_container`; the active tab is opened at login. The summary runs client scripts 3970 (time played) and 3954 (combat level). **OPEN-J1:** time played shows 0 (no playtime varp in our symbols). | RS Mod `JournalTabScript`, `journal-tab/PlayerExtensions` |
| J2 | **OPEN-J2:** Adventure Paths and Leagues sub-tabs are not handled. | — |
| M1 | **OPEN-M1:** the music tab (track list, area/shuffle/single mode, skip) needs a music-playing server message that protocol-api lacks. Friends/ignore/account tab buttons are client-script driven (add/delete friend arrive as social packets) and are not handled here. | — |
