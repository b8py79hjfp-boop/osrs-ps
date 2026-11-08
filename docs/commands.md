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
| `::time` | `::time` | `::uptime`, `::serverinfo` | INFO | — | Server time, uptime, world and player counts. |
| `::vote` | `::vote` | — | INFO | — | Opens the voting page. |
| `::yell` | `::yell <off\|on>`<br>`::yell <message…>` | — | SOCIAL | — | Sends a line to every world with your rank tag; ::yell off\|on stops or resumes receiving yells. |

## Moderator

| Command | Usage | Aliases | Impact | Target | Does |
|---|---|---|---|---|---|
| `::checkbank` | `::checkbank <player>` | — | MODERATION | yes | Read-only view of an online player's bank. (cross-world) |
| `::checkequip` | `::checkequip <player>` | — | MODERATION | yes | Read-only view of an online player's equipment. (cross-world) |
| `::checkinv` | `::checkinv <player>` | — | MODERATION | yes | Read-only view of an online player's inventory. (cross-world) |
| `::history` | `::history <player>` | — | MODERATION | yes | Shows the player's moderation record. (cross-world) |
| `::jail` | `::jail <player> <duration> <reason…>` | — | MODERATION | yes | Sends the player to the jail with no way out until it expires; applied at their next login if offline. (cross-world) |
| `::kick` | `::kick <player> [reason…]` | — | MODERATION | yes | Logs the player out. Works cross-world. (cross-world) |
| `::lookup` | `::lookup <player>` | — | MODERATION | yes | Shows the player's card: rank, world and sanctions in force. (cross-world) |
| `::mute` | `::mute <player> <duration> <reason…>` | — | MODERATION | yes | Mutes public, private, clan and yell chat. Works cross-world and offline. (cross-world) |
| `::note` | `::note <player> <text…>` | — | MODERATION | yes | Adds a private staff note to the player's record. (cross-world) |
| `::pos` | `::pos` | `::mypos`, `::coords` | INFO | — | Your tile, map square (region) and zone. |
| `::return` | `::return <player>` | — | MODERATION | yes | Sends a player you brought back to where they were. (cross-world) |
| `::teleto` | `::teleto <player>` | `::goto` | MODERATION | yes | Teleports you to the player; on another world, offers a hop first. (cross-world) |
| `::teletome` | `::teletome <player>` | `::bring` | MODERATION | yes | Brings the player to you (same world); ::return sends them back. (cross-world) |
| `::unjail` | `::unjail <player> [reason…]` | — | MODERATION | yes | Releases a jailed player. (cross-world) |
| `::unmute` | `::unmute <player> [reason…]` | — | MODERATION | yes | Lifts a mute. (cross-world) |
| `::unyellmute` | `::unyellmute <player> [reason…]` | — | MODERATION | yes | Lifts a yell mute. (cross-world) |
| `::warn` | `::warn <player> <reason…>` | — | MODERATION | yes | Shows the player a warning and records it. (cross-world) |
| `::yellmute` | `::yellmute <player> <duration> <reason…>` | — | MODERATION | yes | Mutes yell only. (cross-world) |

## Administrator

| Command | Usage | Aliases | Impact | Target | Does |
|---|---|---|---|---|---|
| `::ban` | `::ban <player> <duration> <reason…>` | — | MODERATION | yes | Account ban, cluster-wide. (cross-world) |
| `::ipban` | `::ipban <player> <duration> <reason…>` | — | MODERATION | yes | Bans the player's last IP address. (cross-world) |
| `::unban` | `::unban <player> [reason…]` | — | MODERATION | yes | Lifts an account ban. (cross-world) |
| `::unipban` | `::unipban <player> [reason…]` | — | MODERATION | yes | Lifts an IP ban. (cross-world) |
