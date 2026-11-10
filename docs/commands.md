# Commands

Generated from the command registry by `./gradlew :server:commandsDoc`; do not edit by hand.
Ranks, world policy, the interface and security are explained in docs/features/commands.md.
Each rank can also use every command of the ranks below it. A world's `config/server.toml` can
raise or lower a command's rank, or disable it; impact classes it does not enable are hidden there.

## Player

| Command | Usage | Aliases | Impact | Target | Does |
|---|---|---|---|---|---|
| `::changepassword` | `::changepassword` | — | ACCOUNT | — | Changes your password: the current one, then the new one twice. |
| `::commands` | `::commands [search…]` | — | INFO | — | Lists the commands you can use here; a search word narrows the list. |
| `::confirm` | `::confirm` | — | INFO | — | Runs the destructive command waiting for confirmation (within 30 seconds). |
| `::discord` | `::discord` | — | INFO | — | Opens the Discord page. |
| `::donator` | `::donator`<br>`::donator <player…>` | — | INFO | — | Your donator tier and its perks; staff can look up another player's tier. |
| `::drops` | `::drops <npc>` | — | INFO | — | Shows an NPC's drop table with rates. |
| `::empty` | `::empty` | — | CONVENIENCE | — | Destroys everything in your inventory. (confirmation) |
| `::forums` | `::forums` | — | INFO | — | Opens the forums page. |
| `::help` | `::help <command…>` | — | INFO | — | Shows a command's usage, aliases and description. |
| `::hiscores` | `::hiscores` | — | INFO | — | Opens the hiscores page. |
| `::home` | `::home` | — | CONVENIENCE | — | Teleports you home. |
| `::kdr` | `::kdr [player]` | — | INFO | — | PvP kills, deaths, ratio and best streak (yours, or an online player's). |
| `::players` | `::players` | — | INFO | — | Player counts for this world and all worlds, and the list of visible players. |
| `::price` | `::price <item>` | — | INFO | — | Grand Exchange guide price of an item. |
| `::rules` | `::rules` | — | INFO | — | Shows the rules. |
| `::skull` | `::skull` | — | CONVENIENCE | — | Skulls you, as attacking another player would. (confirmation) |
| `::staff` | `::staff` | — | INFO | — | Visible online staff, with rank and world. |
| `::store` | `::store` | — | INFO | — | Opens the store page. |
| `::teleport` | `::teleport [destination…]` | `::teleports`, `::tp` | CONVENIENCE | — | Opens the teleport menu; a name or search word teleports there or narrows the list. |
| `::ticket` | `::ticket <message…>` | — | SOCIAL | — | Sends a help ticket to the staff of this world; a moderator will look at it. |
| `::time` | `::time` | `::uptime`, `::serverinfo` | INFO | — | Server time, uptime, world and player counts. |
| `::vote` | `::vote` | — | INFO | — | Opens the voting page. |
| `::yell` | `::yell <off\|on>`<br>`::yell <message…>` | — | SOCIAL | — | Sends a line to every world with your rank tag; ::yell off\|on stops or resumes receiving yells. |
| `::yelltag` | `::yelltag <tag\|off…>` | — | SOCIAL | — | Sets the tag shown before your yells (Legendary donators and above), or clears it with off. |

## Moderator

| Command | Usage | Aliases | Impact | Target | Does |
|---|---|---|---|---|---|
| `::checkbank` | `::checkbank <player>` | — | MODERATION | yes | Read-only view of an online player's bank. (cross-world) |
| `::checkequip` | `::checkequip <player>` | — | MODERATION | yes | Read-only view of an online player's equipment. (cross-world) |
| `::checkinv` | `::checkinv <player>` | — | MODERATION | yes | Read-only view of an online player's inventory. (cross-world) |
| `::claimticket` | `::claimticket <id> [off]` | — | MODERATION | — | Claims a help ticket, or releases it with off. |
| `::closeticket` | `::closeticket <id> [note…]` | — | MODERATION | — | Closes a help ticket; the note is shown to the player. |
| `::history` | `::history <player>` | — | MODERATION | yes | Shows the player's moderation record. (cross-world) |
| `::jail` | `::jail <player> <duration> <reason…>` | — | MODERATION | yes | Sends the player to the jail with no way out until it expires; applied at their next login if offline. (cross-world) |
| `::kick` | `::kick <player> [reason…]` | — | MODERATION | yes | Logs the player out. Works cross-world. (cross-world) |
| `::lookup` | `::lookup <player>` | — | MODERATION | yes | Shows the player's card: rank, world and sanctions in force. (cross-world) |
| `::mute` | `::mute <player> <duration> <reason…>` | — | MODERATION | yes | Mutes public, private, clan and yell chat. Works cross-world and offline. (cross-world) |
| `::note` | `::note <player> <text…>` | — | MODERATION | yes | Adds a private staff note to the player's record. (cross-world) |
| `::pos` | `::pos` | `::mypos`, `::coords` | INFO | — | Your tile, map square (region) and zone. |
| `::return` | `::return <player>` | — | MODERATION | yes | Sends a player you brought back to where they were. (cross-world) |
| `::sc` | `::sc <message…>` | `::staffchat` | SOCIAL | — | Sends a line to every member of staff online, on every world. |
| `::teleto` | `::teleto <player>` | `::goto` | MODERATION | yes | Teleports you to the player; on another world, offers a hop first. (cross-world) |
| `::teletome` | `::teletome <player>` | `::bring` | MODERATION | yes | Brings the player to you (same world); ::return sends them back. (cross-world) |
| `::tickets` | `::tickets` | — | MODERATION | — | Lists this world's open help tickets. |
| `::unjail` | `::unjail <player> [reason…]` | — | MODERATION | yes | Releases a jailed player. (cross-world) |
| `::unmute` | `::unmute <player> [reason…]` | — | MODERATION | yes | Lifts a mute. (cross-world) |
| `::unyellmute` | `::unyellmute <player> [reason…]` | — | MODERATION | yes | Lifts a yell mute. (cross-world) |
| `::warn` | `::warn <player> <reason…>` | — | MODERATION | yes | Shows the player a warning and records it. (cross-world) |
| `::yellmute` | `::yellmute <player> <duration> <reason…>` | — | MODERATION | yes | Mutes yell only. (cross-world) |

## Administrator

| Command | Usage | Aliases | Impact | Target | Does |
|---|---|---|---|---|---|
| `::alts` | `::alts <player>` | — | MODERATION | yes | Accounts sharing an IP address or device with the player. Audited. (cross-world) |
| `::ban` | `::ban <player> <duration> <reason…>` | — | MODERATION | yes | Account ban, cluster-wide. (cross-world) |
| `::broadcast` | `::broadcast <message…>` | — | OPERATIONS | — | A game broadcast on this world; end with --all for every world. |
| `::checkip` | `::checkip <player>` | — | MODERATION | yes | The IP addresses the player logged in from. Audited. (cross-world) |
| `::deviceban` | `::deviceban <player> <duration> <reason…>` | — | MODERATION | yes | Bans the player's last device id. (cross-world) |
| `::freeze` | `::freeze <player>` | — | MODERATION | yes | Stops the player moving while you investigate. |
| `::invisible` | `::invisible` | `::hide` | MODERATION | — | Hidden mode on or off: left out of player lists for lower ranks. |
| `::ipban` | `::ipban <player> <duration> <reason…>` | — | MODERATION | yes | Bans the player's last IP address. (cross-world) |
| `::tele` | `::tele <x y [level]>`<br>`::tele <place…>` | — | OPERATIONS | — | Teleports you to a tile (x z [level], x,z,level or Jagex level,mx,mz,lx,lz) or a named place. |
| `::unban` | `::unban <player> [reason…]` | — | MODERATION | yes | Lifts an account ban. (cross-world) |
| `::undeviceban` | `::undeviceban <player> [reason…]` | — | MODERATION | yes | Lifts a device ban. (cross-world) |
| `::unfreeze` | `::unfreeze <player>` | — | MODERATION | yes | Lets a frozen player move again. |
| `::unipban` | `::unipban <player> [reason…]` | — | MODERATION | yes | Lifts an IP ban. (cross-world) |

## Owner

| Command | Usage | Aliases | Impact | Target | Does |
|---|---|---|---|---|---|
| `::audit` | `::audit [player\|staff filter…]` | — | OPERATIONS | — | Shows the audit log: a player's or staff member's rows, optionally filtered. |
| `::cancelupdate` | `::cancelupdate` | — | OPERATIONS | — | Cancels the system-update timer on every world. |
| `::command` | `::command <enable\|disable> <command…>` | — | OPERATIONS | — | Enables or disables a command on this world until restart (never ECONOMY on). |
| `::config` | `::config <rate_limit_per_second\|rate_limit_per_minute\|confirm_timeout_seconds\|yell_cooldown_player\|yell_cooldown_moderator> [value]` | — | OPERATIONS | — | Shows or changes a runtime-safe config key on this world until restart. |
| `::donated` | `::donated <player> <dollars [note]…>` | — | OPERATIONS | yes | Records a donation in dollars; the player's donator tier follows their total. (cross-world) |
| `::kickall` | `::kickall [reason…]` | — | OPERATIONS | — | Kicks everyone on this world except staff. (confirmation) |
| `::lockworld` | `::lockworld <on\|off>` | — | OPERATIONS | — | Staff-only logins on this world, for maintenance. |
| `::raid` | `::raid <info> <run\|player…>`<br>`::raid <parties>`<br>`::raid <watch> <run\|player…>`<br>`::raid <leave>`<br>`::raid <kick> <player> [reason…]`<br>`::raid <end> <run\|player reason…>`<br>`::raid <reward> <run [player]…>`<br>`::raid <reissue> <run> <player>`<br>`::raid <waivefee> <player>`<br>`::raid <kc> <player> [raid…]`<br>`::raid <pb> <remove> <player raid mode size…>`<br>`::raid <pb> <player…>`<br>`::raid <disable\|enable> <raid…>`<br>`::raid <stats>`<br>`::raid <start> <raid [mode] [size]…>`<br>`::raid <skip>`<br>`::raid <room> <room\|next…>`<br>`::raid <level> <n>`<br>`::raid <layout> [code…]`<br>`::raid <seed> <n>`<br>`::raid <points> [[player] [set n]…]`<br>`::raid <setkc> <player raid mode n…>` | — | OPERATIONS | — | Raid tools (owner): info, parties, watch, leave, kick, end, reward, reissue, waivefee, kc, pb, disable, enable, stats. Developer only, and only where ECONOMY is allowed: start, skip, room, level, seed, points set, setkc. |
| `::raids` | `::raids` | — | OPERATIONS | — | Lists this world's active runs: raid, mode, party, room, elapsed time and run id. |
| `::rename` | `::rename <player> <new name…>` | — | OPERATIONS | yes | Changes a player's display name, kept in their history; their login name stays. (cross-world) |
| `::saveall` | `::saveall` | — | OPERATIONS | — | Saves every player on this world now. |
| `::setrank` | `::setrank <player> <rank>` | — | OPERATIONS | yes | Sets a player's rank (owners: up to administrator). (cross-world) |
| `::shutdown` | `::shutdown [seconds]` | — | OPERATIONS | — | Stops this world gracefully, saving everyone first. (confirmation) |
| `::update` | `::update <seconds> [message…]` | — | OPERATIONS | — | Starts the system-update timer on every world, then a graceful restart. |

## Developer

| Command | Usage | Aliases | Impact | Target | Does |
|---|---|---|---|---|---|
| `::addxp` | `::addxp <skill> <xp>` | — | ECONOMY | — | Adds xp to a skill. |
| `::anim` | `::anim <id> [delay]` | — | DEBUG_VISUAL | — | Plays animation (seq) <id> on you; -1 stops it. |
| `::bank` | `::bank` | — | CONVENIENCE | — | Opens your bank. |
| `::closeif` | `::closeif` | — | OPERATIONS | — | Closes the modal interface. |
| `::copy` | `::copy <player>` | — | ECONOMY | yes | Copies an online player's equipment, inventory and stats onto you. |
| `::cs2` | `::cs2 <id> [args…]` | — | OPERATIONS | — | Runs client script <id>; numeric arguments are sent as ints, the rest as strings. |
| `::find` | `::find <query…>` | — | OPERATIONS | — | Searches cache names for ids; add item, npc or loc as the last word to pick the kind. |
| `::gfx` | `::gfx <id> [height] [delay]` | — | DEBUG_VISUAL | — | Plays spot animation <id> on you, optionally raised by height and delayed. |
| `::god` | `::god` | — | CONVENIENCE | — | God mode on or off: invulnerable, with unlimited run, prayer and special attack. |
| `::heal` | `::heal [player]` | — | CONVENIENCE | yes | Restores hitpoints, prayer, run energy and special attack (yours or a player's). |
| `::if` | `::if <id>` | — | OPERATIONS | — | Opens interface <id> as the main modal. |
| `::item` | `::item <item> [amount] [noted]` | — | ECONOMY | — | Spawns an item into your inventory (a staff spawn). |
| `::jingle` | `::jingle <id>` | — | DEBUG_VISUAL | — | Plays jingle <id> over the music. |
| `::kill` | `::kill <player>`<br>`::kill <npc>` | — | CONVENIENCE | yes | Kills an online player, or the nearest NPC of a type. |
| `::loc` | `::loc <loc> [shape] [rotation]` | — | ECONOMY | — | Puts a temporary loc on your tile (10 minutes, or until ::removeloc). |
| `::master` | `::master` | — | ECONOMY | — | Sets every skill to 99. |
| `::npc` | `::npc <npc>` | — | ECONOMY | — | Spawns NPC <npc> on the tile next to you until removed with ::removenpc. |
| `::removeloc` | `::removeloc` | — | ECONOMY | — | Removes the locs you placed with ::loc (the map's own loc comes back). |
| `::removenpc` | `::removenpc` | — | OPERATIONS | — | Removes the NPCs you spawned with ::npc. |
| `::resetstats` | `::resetstats` | — | ECONOMY | — | Resets every skill to level 1 (Hitpoints 10). (confirmation) |
| `::setlevel` | `::setlevel <skill> <level>` | — | ECONOMY | — | Sets a skill's level (1-99), with the xp for it. |
| `::song` | `::song <id>` | — | DEBUG_VISUAL | — | Plays music track <id> (cache music id). |
| `::spec` | `::spec` | — | CONVENIENCE | — | Fills your special attack energy. |
| `::tick` | `::tick` | — | OPERATIONS | — | Shows this world's current tick. |
| `::trace` | `::trace` | — | OPERATIONS | — | Streams your tick-inspector trace; run again to stop. |
| `::varbit` | `::varbit <id> [value]` | — | ECONOMY | — | Shows varbit <id>, or sets it to [value] (must fit its bits). |
| `::varp` | `::varp <id> [value]` | — | ECONOMY | — | Shows varp <id>, or sets it to [value]. |
