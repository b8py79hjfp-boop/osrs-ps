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
| CMD7 | **OPEN.** `::invisible` hides the avatar from every other player, because RSProt's `PlayerAvatar.hidden` is global; the brief wants equal or higher ranks to still see it. Player lists (`::players`, `::staff`, presence) do follow the rank rule. A per-viewer player-info filter in RSProt would close this. | brief §6; RSProt `PlayerAvatar.hidden` |
| CMD8 | `::freeze` keeps the target in place (`frozenUntil` at its maximum) until `::unfreeze`, which clears only a staff freeze. `::tele <place>` uses `content/staff/.../places.tsv`, whose destinations come from teleports.md. | brief §6 |
| CMD9 | **OPEN.** `::cancelupdate` sends a zero system-update timer to clear the client's countdown. Whether the 242 client hides the timer on 0 is unverified (RuneLite check). | brief §6 |
| CMD10 | **OPEN.** `::lockworld on` refuses non-staff logins with the "world full" response; OSRS has no "staff only" login code we could confirm. | brief §6; RSProt `LoginResponse` |
| CMD11 | `::rename` changes the display name and keeps the login username. The change is a RENAME record ("old -> new") in the history, and the player is kicked so their client shows the new name. Staff commands find accounts by username or display name. A renamed display name blocks both new registrations and other renames. | brief §6 |
| CMD12 | `::update <s>` starts the timer on every world through the hub. `::shutdown [s]` (with confirmation) starts it on this node's world only. When every world on the node has logged everyone out, the process exits; the shutdown hook waits for the final saves, and the supervisor restarts the node. `::command enable` lifts a `disabled` override or an impact class the world turned off, until restart, but never ECONOMY. `::config` changes only rate limits, the confirmation timeout and yell cooldowns. | brief §3, §6 |
| CMD13 | Raid tables (brief §6):
- **Owner subcommands** (OPERATIONS, live-safe) inspect a run, end it, remove a player from it, or pay out what it already earned. `::raid end`, `kick`, `reissue`, `waivefee` and `pb remove` wait for `::confirm`.
- **`kick` and `end`** use the framework's leave rules: no item loss, raid-only items removed, nothing paid. `end` then destroys the instance.
- **`reissue`** pays a stored, unpaid reward once and never re-rolls; a second call is refused.
- **`waivefee`** makes the player's next ToB or ToA reclaim free. **OPEN:** TFA has no reclaim fee in our code.
- **`watch`** makes you a hidden observer (instances.md IN11).
- **Developer subcommands** (`start`, `skip`, `room`, `seed`, `points … set`, `setkc`) need the world's ECONOMY.
- **Follow-ups:** `::raid level` (it needs ToA's invocation picker) and forcing a CoX layout with `::raid layout <code>` are not supported yet, and say so. `::raids all` (other worlds) is not built either. | brief §6; raids common.md |
