# Commands, ranks and moderation

Source of truth: docs/plans/commands-brief.md. OSRS gives the official behaviour for only a few of these rules (§2).
The rest are ours, and each one says so.

| Id | Rule | Source |
|---|---|---|
| CMD1 | **OPEN.** The login response sends `staffModLevel` 2 for administrator and above, and `playerMod` for moderator and above. Which client scripts read these two values, and what each value unlocks, is unverified. To be checked in RuneLite. | RSProt `LoginResponse.Ok`; D-086 |
| CMD2 | **OPEN.** Chat crowns: `<img=0>` player moderator and `<img=1>` J-Mod in yells. The values sent in public chat, private messages, friends chat and clan chat follow the existing code. To be checked in RuneLite. | D-086 |
| CMD3 | The jail (brief §7) is a tile plus the square of `radius` tiles around it, from `[commands.jail]`; the default is the Draynor jail cell at (3128, 3245, 0), radius 2. A jailed player is moved inside when the jail is applied or at login, and is checked every tick: out of the area means they are put back, with the reason and the expiry shown. Teleport requests are refused with the same text. When the jail expires or is lifted (including while offline, via a saved flag), the player is sent to the world's home tile. OSRS has no player jail, so the place and the rules are ours. | brief §7; `cache-dump collision 3128 3245 0` (walkable) |
| CMD4 | A mute blocks public chat with "You are muted <until …> (<reason>) and cannot talk."; the hub refuses private, friends chat and clan messages. A yell mute blocks `::yell` only. Mutes, yell mutes and jails set while the player is offline apply at their next login (the login grant carries them). Changes made while the player is online are pushed by the hub to the player's world at once. | brief §6, §7 |
| CMD5 | Typing `::bank` makes the player say "Hey, everyone, I just tried to do something very silly!" (developers get the bank instead). Typing an emote's name without spaces (`::wave`, `::airguitar`) performs that emote, if it is unlocked. Both are official behaviour, still to be verified in game. | brief §2 |
| CMD6 | `::skull` gives the same skull as an unprovoked attack: 20 minutes (`PvpCombat.SKULL_TICKS`). `::kdr` counts PvP kills and deaths where the killer is a player; duels do not count. A PvP death resets the dead player's current streak. | brief §6; pvp.md |
