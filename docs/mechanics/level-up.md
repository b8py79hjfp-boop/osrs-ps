# Level-ups

Gaining a base level of any stat (`PlayerRef.addXp`, which publishes `StatAdvanceEvent`) is announced to the player.
Implemented in `content/gameframe/.../LevelUp.kt`.

## Rules

- **LU1: chat message.** Every level-up prints a game message:
  - below 99: `Congratulations, you've just advanced your <Skill> level. You are now level <N>.`
  - at 99: `Congratulations, you've reached the highest possible <Skill> level of 99.`
  Source: RuneLite `ScreenshotPlugin.LEVEL_UP_MESSAGE_PATTERN` and `ScreenshotPluginTest` (text captured from the live game).
- **LU2: popup.** Unless the "Level-up interface" setting is off (varbit `option_level_up_message_disabled` = 1;
  Wiki "Skills" changes, 29 March 2023), interface `levelup_display` (233) opens in the chatbox:
  - `text1`: `<col=000080>Congratulations, you've just advanced a(n) <Skill> level.</col>`
  - `text2`: `Your <Skill> level is now <N>.`
  - the skill's icon layer is shown and the others hidden; `continue` is a pause button.
  Source: RuneLite `ScreenshotPluginTest` (`HUNTER_LEVEL_2_TEXT`, `LevelupDisplay.TEXT2` "Your Attack level is now 70.").
- **LU3: fireworks.** Spot anim `levelup_anim` (199); at level 99, `levelup_99_anim` (1388). Wiki "Skills": "Level up
  fireworks"; ids from the cache's gameval names.
- **LU4: several at once.** Wiki "Skills" changes, 9 April 2015: "You'll now see all level up messages when receiving
  more than one in a short amount of time." The popups queue; each continue shows the next. A popup closed any other way
  drops the rest of the queue (chat messages are already printed).
- **LU5: jingle.** The skill's `advance_<skill>` jingle (cache gameval names; Farming `farming_levelup`).

## OPEN

- **OPEN-LU1:** each skill has two jingles (`advance_x` and `advance_x2`); which one plays when is not documented. The first is used.
- **OPEN-LU2:** whether the chat message is also printed while the popup is enabled. It is (both are sent).
- **OPEN-LU3:** combat level-ups (`levelup_display:combat`, jingle `combat_level_up`) are not announced; the text is unsourced.
- **OPEN-LU4:** the article in `text1` ("a"/"an") follows English for vowel-initial skills; only "a Hunter" is sourced.
- **OPEN-LU5:** the level-99 popup uses the same two lines; the 99 popup text is unsourced. `levelup_99_skill` (seq 7636)
  and the all-99 `levelup_max` spot anim are not played.
