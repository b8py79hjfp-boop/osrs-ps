# Music (area music and unlocks)

Implementation: `content/gameframe/.../MusicTab.kt`. Rows of the Music tab clicks are in gameframe-buttons.md M1.
Sources: cache 2757 db table `music` (44, dumped with `./gradlew :tools:cache-dump:run --args="dbrows 44"`), RS Mod v2
`api/music` + `api/player/music` + `api/music-plugin` (ISC, read 2026-11-06), OSRS Wiki "Music" / "Music player"
(behaviour described there; no tick timeline is documented).

## Cache columns (table `music`, 888 rows)

| Column | Meaning (decoded from the cache) |
|---|---|
| sortname 0, displayname 1 | list sort key / shown name |
| unlockhint 2 | text ending a sentence: "in Lumbridge.", "automatically.", "while fighting Scurrius, the Rat King."; default "somewhere." |
| duration 3 | track length, unit unverified (OPEN-MU6) |
| midi 4 | midi id sent in MIDI_SONG |
| variable 5 | (1-based index into varps `musicmulti_1..28`, bit 0..31); unset on the 24 hidden rows |
| automatic_unlock 6 | 1 on 17 rows (Newbie Melody, Scape Original, ...): unlocked from the start |
| area 7 | int 1..63, present on 203 of 888 rows: the **music area group** a track plays in. Rows sharing a value are the area's modern-mode track set (all 6 Lumbridge tracks have 1) |
| area_default 8 | int 1..63, one row per value (none for 15): the area this track is the classic-mode track of (Harmony -> 1) |
| hidden 9 | 1 = not listed in the tab (24 rows, none has an area) |
| holiday 10, secondary_track 11, parent_track 12, release_type 13, restricted_content 14 | not used |

`area` / `area_default` are plain ints (column type 0), not references to the `area` config group (archive 2 group 18: 1155
files, all empty; the server packs its own `area` data, RS Mod `AreaBuilds`). Table 128 `music_area_group` has one row listing
areas 30..35. The cache therefore says which tracks belong to which area number, but **not which tiles are in that area**.

## Rules

| # | Rule | Source |
|---|---|---|
| MU1 | Unlock bit of a track = bit `b` of varp `musicmulti_i` for `variable = (i, b)`. Tracks with `automatic_unlock` are set at login without text. | cache columns; RS Mod `MusicRepository.unlockVarps`; Wiki "Music" |
| MU2 | Entering a music area in modern mode (`music_area_mode` varbit 0) unlocks every track of the area; with play mode Area (varp `musicplay` = 1) one of them plays. Classic mode (varbit 1) unlocks and plays only the `area_default` track and leaves the modern tracks locked. Playing the track already playing does nothing. Unlocking shows "You have unlocked a new music track: <name>." unless varbit `music_unlock_text_toggle` is 1. | RS Mod `MusicPlayer.enterArea/unlock` |
| MU3 | `musicplay` values: 0 Single (RS Mod "Manual"), 1 Area, 2 Shuffle; the Area/Shuffle/Single buttons (`music:area/shuffle/single`, op 1) set it. At login the mode is reset to Area unless varbit `use_previous_music_mode_on_login` is set. | RS Mod `MusicPlayMode`, `MusicAreaScript.setDefaultModes`; varbit name from the cache |
| MU4 | Skip Track (`music:skip`): Area mode picks another track of the current area (modern mode); Shuffle picks a random unlocked listed track other than the current; Single does nothing. Now-playing text `music:now_playing_text` is set to the track name on play. | RS Mod `playNext` (the button itself has no RS Mod handler) |
| MU5 | Clicking a locked track plays nothing and sends a message with the row's `unlockhint`. | task brief; wording not sourced |
| MU7 | Area changes are noticed by a soft per-player timer that only looks the area up when the player's tile changed; the effect runs through the area queue (scripts.md Q11). | scripts.md Q11 |
| MU8 | `::unlockmusic` unlocks every track (dev), `::musicarea n` acts as entering music area n (dev, to exercise MU2 without geometry). | dev tooling, not OSRS |

## OPEN

- **MU1 (resolved 2026-11-06): area geometry.** It is imported from the OSRS Wiki by `tools/wiki-import/music_areas.py`:
  - **Sources:** each track's Infobox `map` field (inline `{{Map}}` shapes) and the shared `Map:<page>` maps
    (`{{Music track map}}`). Named `{{Map polygon}}` shapes are expanded through the Wiki.
  - **Joined to the cache by display name.** Tracks in a cache area group are named by the area number. Other tracks are
    single-track areas named `track:<name>`, which MusicTab gives synthetic ids from 1000. Hidden and holiday-event
    tracks are excluded.
  - **Order:** smallest polygons come first, so specific places win over the region around them.
  - **Coverage:** all 63 cache areas, 579 tracks in total. The 117 tracks without a map are instances, minigames and
    boss rooms.
  - **Spot checks:** Lumbridge 1, Varrock 4, Falador 5, Draynor 2, Catherby 41, Ardougne 46, Wilderness 10, Brimhaven
    Dungeon 18, Edgeville "Forever", Grand Exchange "The Trade Parade".
  - **To refresh:** run the commands in the script's docstring.
- **OPEN-MU2:** whether the real game plays modern-area tracks as a seeded shuffled playlist (RS Mod's `MusicPlaylist`, itself
  marked TODO(emulation)) or at random; we pick a uniformly random other track from the world RNG. Whether entering a modern area
  unlocks all tracks at once is RS Mod's behaviour, unverified.
- **OPEN-MU3:** exact meaning of `use_previous_music_mode_on_login` / `settings_music_default_track_on_area_entry` /
  `settings_music_player_hide_tracks` (hide-tracks may reorder or shorten the client's list, see M1b).
- **OPEN-MU4:** Skip semantics beyond RS Mod's `playNext`; the playlist / dropdown features of the tab are not handled.
- **OPEN-MU5:** exact locked-track message wording and the unlock text colour (`e00a19` is RS Mod's opaque-chat colour).
- **OPEN-MU6:** `duration` unit and track-end progression: when a track ends nothing follows (no per-player clock, varbits
  `music_curr_*` are not maintained), so Area/Shuffle do not advance on their own.
- **M1b conclusion:** the client script that builds the list (archive 12) cannot be read here (no clientscript decoder in `cache/`).
  What the cache does show: the column is called `sortname`, 24 rows are `hidden`, and a "hide tracks" setting exists, so the
  assumption (slot = index in the `sortname`-sorted list of non-hidden rows) stays an assumption. Verify against a real client by
  clicking a known track (e.g. the first and last rows) and comparing the played midi.
