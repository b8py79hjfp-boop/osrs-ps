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
