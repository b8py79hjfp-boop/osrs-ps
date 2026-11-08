# Command system — Claude Code prompt

Build the server's **command system**: five ranks, `::` commands, and a `::commands` interface that shows each player the commands they can use. For staff, the interface also lets them pick an online player, then a command, then its arguments.

**Ranks, lowest to highest:** player, moderator, administrator, owner, **developer**. Developer is the top rank.

The command suite starts from what RSPS servers and open-source codebases commonly ship (§6). It then adds tools specific to this server: PvP debugging, the tick inspector, phasing and raids.

---

## 0. How to work

1. **Read first.** Read `CLAUDE.md`, `docs/BRIEF.md`, `docs/PROGRESS.md` and `docs/DECISIONS.md`. Where the repo names things differently from this prompt, follow the repo and note the mapping in the plan.
2. **Build on what exists.**
   - The moderation module (BRIEF §12: kick, mute, ban, permissions as data, every staff action audited) and the append-only audit log. Commands are a front end to them.
   - Our existing **custom interface** for `::commands`. Find how it's built and delivered, and record what it can display. Extend it the same way; don't build a second one.
   - Existing commands: the tick-inspector trace command (BRIEF §5), `::ghosts` and `::phasing` (phasing), and `::raid` (raids). Move them onto the new registry. The raid table in §6 replaces the raids prompt's list of `::raid` commands.
3. **Save and plan.** Save this prompt verbatim as `docs/plans/commands-brief.md`. Then write `docs/plans/commands.md` covering:
   - what exists and what's missing;
   - how the custom interface works and what it can show;
   - the modules touched and the risks.
4. **Model policy.** BRIEF §0 item 8 applies.
   - **Main session (Opus 5.5, medium) owns:**
     - the rank model, permission checks and world policy (§3);
     - the parser's player and item resolution;
     - execution inside the tick and replay;
     - moderation, rank and audit persistence, and cross-world actions through the hub;
     - security (§8);
     - every command that changes combat state, items, XP or persistence.
   - **`scout`** finds the existing interface, moderation and audit code, and checks §2's client facts against RSProt and RuneLite.
   - **`implementer`** takes, once the main session has set the interfaces:
     - the interface views (§5);
     - informational, social and lookup commands;
     - visual dev tools: anim, gfx, sound, interfaces, vars;
     - doc generation.
   - **`verifier`** checks the registry against §6 and the permission matrix test against §1 and §3, without seeing the implementation.
5. **Kotlin.** All code is Kotlin and follows BRIEF §11.
   - Commands are declared with a type-safe Kotlin DSL (§3).
   - Ranks, argument types and results are sealed types or enums.
   - Ids are value classes.
   - Handlers on the world thread are plain synchronous Kotlin, or suspend scripts on the world dispatcher.
6. **Commit and report.** Commit at each green milestone (§11). Stop at the end and report.
7. **Stop and ask** before:
   - giving any rank a power §6 doesn't list;
   - letting an economy-affecting command run on a live world;
   - changing how logins or the moderation records are stored.

## 1. Ranks

- **The five ranks:** `PLAYER < MODERATOR < ADMINISTRATOR < OWNER < DEVELOPER`. Each rank can use every command of the ranks below it.
- **Targeting rule.** A staff command can only target a player of a strictly lower rank. Developers are the exception: they can target anyone except other developers. Everyone can target themselves.
- **Setting ranks.**
  - Owners can set any rank up to administrator. Developers can set any rank, including developer.
  - Nobody can change the rank of someone at or above their own rank, except a developer acting on a non-developer.
- **Bootstrap.** The first developer is set outside the game, with a CLI task (`./gradlew admin --setRank=<account>:DEVELOPER`) or a bootstrap list in TOML that is applied once and logged. There is no in-game path to the first developer.
- **Storage.**
  - The rank lives on the account in the accounts database and is loaded at login.
  - A change takes effect at once on every world through the hub, with no relog needed.
  - A demotion closes the staff views of the interface.
- **What the client sees.**
  - **Login response.** Rank maps to the login response's `staffModLevel` and `playerMod` fields (RSProt `LoginResponse`). The intended mapping:
    - moderator gets the player-mod flag, which unlocks the report interface's mute option;
    - administrator and above get the J-Mod staff level: hidden players still render for them, plus any other J-Mod client feature.

    Check this mapping. Read the clientscripts that call `staffmodlevel` and `playermod` to see exactly what each value unlocks, and record it in the plan.
  - **Chat icons.** Use the cache's existing mod-icon sprites: the player-moderator crown for moderators, and the J-Mod crown for administrators, owners and developers. This applies in public chat, private messages, friends chat and clan chat. Custom icons are out of scope (§13).
- **Hidden staff.** Staff in invisible mode are hidden from player lists, `::staff` and `::players`. Only staff of equal or higher rank see them.

## 2. Syntax and the client

- **How commands arrive.** A player types `::name args` in the chatbox. The client strips the `::` and sends RSProt's `ClientCheat(command, autocomplete)`. `autocomplete` is set when Tab is pressed in the official C++ client's console. RuneLite never sets it; answer with suggestions only if that's cheap.
- **RuneLite** posts a `CommandExecuted` event for every `::` command.
  - Its developer-tools plugin, in developer mode only, acts locally on `logger`, `getvarp`, `setvarp`, `getvarb`, `setvarb`, `addxp`, `setstat`, `anim`, `gfx`, `transform`, `wear`, `tex`, `alpha`, `zsort`, `sound`, `msg`, `setconf`, `getconf` and `modicons`. A developer in that mode who types one of our same-named commands gets both effects; document this.
  - Confirm through RSProx that every command still reaches the server.
- **Reserved names.** The registry rejects these at startup, because clients handle them:
  - every client: `renderself`, `toggleroof`, `toggleroofs`, `displayfps`, `settilerendermode`, `map` and `wiki`;
  - the official client: `toggleantidrag`, `uiquality`, `setwindowsize`, `chattimestamp` and `repairlt`;
  - RuneLite: `mouseovertext`, `getdrawdistance`, `setdrawdistance`, `zbuf` and `z`.
- **Official server-side behaviour stays.**
  - `::bank`, for anyone without the developer bank command, makes the player say "Hey, everyone, I just tried to do something very silly!"
  - Typing an emote's name (`::wave`, `::dance` and so on) performs that emote if it's unlocked.

  Verify both in game and keep them.
- **Parsing rules:**
  - Command names are case-insensitive, and aliases are allowed.
  - Arguments are separated by spaces; double quotes group words.
  - Player names follow the engine's existing name normalisation (OSRS treats spaces and underscores alike), so `::mute Big_Bob 1h spam` works.
- **Typed arguments,** each with its own parser and error message:
  - **Int and Long**, with `k`, `m` and `b` suffixes (`10k`, `1.5m`).
  - **Boolean:** `on`/`off`, `true`/`false` or `yes`/`no`.
  - **Duration:** `30s`, `10m`, `2h`, `7d` or `perm`.
  - **Player:** an online name. Offline accounts resolve through the hub where the command allows it. Partial names are disambiguated: list the matches and don't guess.
  - **Item, Npc and Loc:** an id or a cache name, with fuzzy search and a "did you mean". A noted variant can be requested.
  - **Coordinate:** `x y [level]`, `x,y,level`, or Jagex's `level,mx,mz,lx,lz` form.
  - **Skill:** a name or id.
  - **Rank,** and **enum choices**.
  - **Rest of line,** for reasons and messages.
- **Output.**
  - Use the game-message chat type, unless the client's console type also renders in RuneLite's chatbox. Check that and pick the one that works in both.
  - Long output (lists, histories, drop tables) goes to the interface, not into chat spam.
- **Unknown commands.** Reply "Unknown command. Type ::commands for a list." Add a "did you mean" drawn **only** from commands the player can use.
  - A staff command a player can't use gets the exact same reply, so its existence never leaks.
  - Wrong arguments get the command's usage line.

## 3. The registry

- **Declaring commands.** Commands are declared once in Kotlin, and a command can have several signatures. This sketch is illustrative, not binding:

  ```kotlin
  command("mute", rank = MODERATOR, category = MODERATION, impact = Impact.MODERATION) {
      aliases("silence")
      description("Mutes a player's public, private, clan and yell chat.")
      target()                          // shows up under a selected player in the interface
      signature(player("target"), duration("length"), text("reason")) { ctx -> moderation.mute(ctx) }
      example("::mute \"Big Bob\" 2h spamming")
      confirm = false
      audit = AuditLevel.FULL
  }
  ```

- **What each command declares:**
  - name and aliases;
  - minimum rank and category;
  - description, signatures and examples;
  - whether it takes a target, whether it works cross-world, and whether it needs confirmation;
  - cooldown and rate limit;
  - audit level and impact class;
  - an optional extra predicate, such as "only inside the staff zone".
- **Impact classes and world policy.**
  - The classes are `INFO`, `SOCIAL`, `ACCOUNT`, `MODERATION`, `OPERATIONS`, `DEBUG_VISUAL`, `CONVENIENCE` and `ECONOMY`. `CONVENIENCE` means it changes gameplay but not items; `ECONOMY` means it creates or destroys items, XP or currency.
  - Each world's config enables a set of classes. On live 1:1 worlds, `CONVENIENCE` and `ECONOMY` are off. Dev and beta worlds enable everything.
  - `ECONOMY` can only be enabled in the TOML file followed by a restart, never at runtime, and never on a world flagged live without the owner's explicit `allow_economy_on_live = true`.
  - Developers are still the top rank: they can run anything the world's policy allows.
- **Overrides.** A world's TOML can raise or lower a command's minimum rank, or disable it. Staff commands can't go below moderator. Every override is logged at startup.
- **Validation at startup.** Fail on:
  - duplicate names or aliases;
  - reserved names (§2);
  - a missing description or example;
  - a target command without a player argument.
- **One source of truth.** The registry generates `docs/commands.md`: one table per rank, with usage, aliases, impact and whether a target is needed. The interface reads the same data.

## 4. Execution

- **When.** `ClientCheat` is a client-input packet (BRIEF §5.3, phase 2). It counts toward the 10 packets per tick and is acted on in the input phase.
  - Quick handlers run immediately.
  - Longer ones queue a suspend script.
  - Database and hub work (offline lookups, histories, alts, cross-world actions) runs off the world thread. Its result comes back through the pre-tick inbox, so `tools/replay` records it like any other input.
- **The pipeline, in order:**
  1. parse;
  2. resolve the command and signature;
  3. check the rank;
  4. check the targeting rule;
  5. check world policy;
  6. check the rate limit and cooldown;
  7. confirm, for destructive commands;
  8. execute;
  9. audit;
  10. reply.

  The interface (§5) calls the same pipeline with the same checks. A click and a typed command are indistinguishable after parsing.
- **Confirmation.** A destructive command (ban, IP ban, device ban, setrank, update, shutdown, kickall, rename, `::empty`, and the raid commands marked "Confirmation" in §6) shows exactly what will happen and waits up to 30 s for `::confirm`, or for the interface's confirm button.
- **Rate limits and cooldowns.**
  - Per player: at most 5 commands per second and 30 per minute; excess input is dropped.
  - `::yell` cooldown by rank: player 30 s, moderator 5 s, administrator and above none.
  - All of these are config.
- **Audit.**
  - Every moderator-and-above command is written to the append-only audit log: actor, rank, command, arguments, target, world, tick, outcome and source (typed or interface). Secrets such as passwords are redacted.
  - Player commands are audited only when sensitive: password change, report, ticket.
- **Replies.** Every command answers in one line: what happened, or why not. Errors show the usage.

## 5. The `::commands` interface

Use our existing custom interface (§0). Lists page server-side (for example 50 rows per page), so large player counts stay fast.

1. **Command list** (everyone):
   - **What it shows.** Every command the viewer can run on this world, grouped by category, with search. Each row shows the name, aliases, usage and a one-line description.
   - **The detail view** has the full description, every signature, examples, minimum rank, whether a target is needed and whether it needs confirmation.
   - **Run.** With no arguments, the command runs at once. Otherwise each argument is asked for with OSRS's own input dialogs (count, name, string and the item search), through RSProt's `ResumeP*Dialog` packets. The typed equivalent is shown before running, so players learn the syntax.
2. **Players** (moderator and above):
   - **The list.** Online players on every world, from hub presence that is pushed and cached on each world, not fetched each time the list opens. It is searchable by name and filterable by world, rank and status (muted, jailed, frozen, new account). Hidden staff appear only for viewers of equal or higher rank.
   - **The player card** shows name, rank, world, location (same world only), combat and total level, account age, status flags and the last five moderation entries.
   - **Commands.** Under the card are the commands this viewer may use on this player: target commands the viewer's rank allows, on players of lower rank. When the player is in a raid, owners and developers also get that run's commands: info, watch, kick and end.
3. **Guided run** (staff):
   - Pick a player, then a command, then arguments, with presets: durations of 1h, 1d, 7d, 30d or permanent, and reasons from a config list plus free text.
   - Destructive commands get a confirmation screen, then the result is shown.
   - The reverse order (command first, then player) works too, and every step can be backed out of.
4. **Tickets** (moderator and above): open help tickets, with claim, teleport to the player, and close with a note.
5. **Recent** (staff): the viewer's last 10 commands, so they can run one again.

Because a demotion closes the staff views, a stale screen can't act with old rights.

## 6. The command suite

**Sources.** Command sets in Void (Kotlin), RS Mod (Kotlin), Luna (Kotlin), Open RSC and 2009scape. Common RSPS conventions fill the gaps.

**Rules for this list:**
- Every command here is a requirement.
- Defaults follow the impact class (§3).
- "World-gated" means off on live 1:1 worlds.

### Player
| Command | Usage | Does |
|---|---|---|
| `::commands` | `::commands [search]` | Opens the interface. |
| `::help` | `::help <command>` | Usage and description in chat. |
| `::players` | `::players` | Counts for this world and all worlds, and opens the list of visible players. |
| `::staff` | `::staff` | Visible online staff, with rank and world. |
| `::yell` | `::yell <message…>` / `::yell off\|on` | Sends a line to every world with a rank tag; off/on stops or resumes receiving yells. |
| `::rules` | `::rules` | Shows the rules. |
| `::discord`, `::forums`, `::vote`, `::store`, `::hiscores` | — | Opens the configured URL (`UrlOpen`). Listed only when a URL is configured. |
| `::changepassword` | dialogs | Current password, then new password twice. Rate-limited. |
| `::time` (`::uptime`, `::serverinfo`) | — | Server time, uptime, world and player counts. |
| `::price` | `::price <item>` | Grand Exchange guide price. |
| `::drops` | `::drops <npc>` | Drop table with rates, in the interface. |
| `::kdr` | `::kdr [player]` | PvP kills, deaths, ratio and best streak. Needs PvP stat tracking, so add it. |
| `::home` | `::home` | World-gated `CONVENIENCE`: teleports home. |
| `::skull` | `::skull` | World-gated `CONVENIENCE`: skulls you, with confirmation. |
| `::empty` | `::empty` | World-gated `CONVENIENCE`: destroys your inventory, with confirmation. |
| `::bank`, emotes | — | The official behaviour (§2). |

### Moderator
| Command | Usage | Does |
|---|---|---|
| `::mute` / `::unmute` | `::mute <player> <duration> <reason…>` | Mutes public, private, clan and yell chat. Works cross-world and offline. |
| `::yellmute` / `::unyellmute` | same | Mutes yell only. |
| `::kick` | `::kick <player> [reason…]` | Works cross-world. |
| `::jail` / `::unjail` | `::jail <player> <duration> <reason…>` | Sends the player to the jail area with no way out until it expires. Applied on next login if they're offline. |
| `::warn` | `::warn <player> <reason…>` | Shows them a warning and records it. |
| `::note` | `::note <player> <text…>` | Private staff note. |
| `::history` | `::history <player>` | Moderation record, in the interface. |
| `::lookup` | `::lookup <player>` | Player card. |
| `::teleto` (`::goto`) | `::teleto <player>` | Same world. For another world, offers a hop first. |
| `::teletome` (`::bring`) | `::teletome <player>` | Same world only. Remembers their previous tile. |
| `::return` | `::return <player>` | Sends someone you brought back. |
| `::checkinv`, `::checkequip`, `::checkbank` | `<player>` | Read-only views of an online player's items. |
| `::pos` (`::mypos`, `::coords`) | — | Your tile, region and zone. |

### Administrator
| Command | Usage | Does |
|---|---|---|
| `::ban` / `::unban` | `::ban <player> <duration> <reason…>` | Account ban, cluster-wide. |
| `::ipban` / `::unipban` | `::ipban <player\|ip> <duration> <reason…>` | IP ban. |
| `::deviceban` / `::undeviceban` | `<player> <duration> <reason…>` | Bans by device identifier if the login block provides one. Check RSProt's login decoding and record what's available. |
| `::alts` | `::alts <player>` | Accounts sharing IPs or devices. Audited. |
| `::checkip` | `::checkip <player>` | Audited. |
| `::invisible` (`::hide`) | — | Hidden mode. Equal or higher ranks still see you. |
| `::freeze` / `::unfreeze` | `<player>` | Stops movement while you investigate. |
| `::tele` | `::tele <x> <y> [level]` / `<jagex coord>` / `<place>` | `<place>` uses a named-locations data file. |
| `::broadcast` | `::broadcast <message…> [--all]` | Game broadcast on this world, or every world. |

### Owner
| Command | Usage | Does |
|---|---|---|
| `::setrank` | `::setrank <player> <rank>` | Sets a rank up to administrator. |
| `::update` / `::cancelupdate` | `::update <seconds> [message…]` | System-update timer (`UpdateRebootTimerV2`), then a graceful restart. |
| `::shutdown` | `::shutdown [seconds]` | Graceful stop: saves everyone first. |
| `::saveall` | — | Saves every player now. |
| `::kickall` | `::kickall [reason…]` | Kicks everyone except staff. |
| `::lockworld` | `::lockworld on\|off` | Staff-only logins, for maintenance. |
| `::command` | `::command enable\|disable <name>` | Per world, until restart. Can't enable `ECONOMY` (§3). |
| `::config` | `::config <key> [value]` | Runtime-safe keys only. |
| `::audit` | `::audit [player\|staff] [filter]` | Audit log viewer. |
| `::rename` | `::rename <player> <new name>` | Display-name change, with history. |

### Developer
| Command | Usage | Does |
|---|---|---|
| `::item` (`::spawn`) | `::item <item> [amount] [noted]` | `ECONOMY`. Goes through the item-transaction API, journaled as a staff spawn. |
| `::bank` | — | `CONVENIENCE`: opens your bank. Elsewhere, the official joke. |
| `::master`, `::setlevel`, `::addxp`, `::resetstats` | `::setlevel <skill> <level>` | `ECONOMY`. |
| `::heal`, `::spec`, `::god`, `::kill` | `::heal [player]`, `::kill <player\|npc>` | `CONVENIENCE`. God mode means invulnerable, with unlimited run, prayer and special. |
| `::npc` / `::removenpc` | `::npc <npc>` | Temporary spawn. |
| `::loc` / `::removeloc` | `::loc <loc> [shape] [rotation]` | Temporary loc. |
| `::anim`, `::gfx`, `::sound`, `::song`, `::jingle` | `<id> […]` | `DEBUG_VISUAL`. |
| `::pnpc` / `::unpnpc` | `::pnpc <npc>` | `DEBUG_VISUAL`. |
| `::if`, `::closeif`, `::varp`, `::varbit`, `::cs2` | `::varbit <id> [value]` | Interface and variable tools. |
| `::find` | `::find <query> [item\|npc\|loc\|…]` | Searches cache names for ids. |
| `::clip`, `::noclip` | — | Shows collision flags; ignore collision. |
| `::debug` | `::debug <combat\|pid\|info\|phasing> on\|off` | `combat` prints each hit's accuracy roll, max hit and hit delay. `pid` shows nearby PID order. `info` shows player-info counts. |
| `::trace` | — | Streams your tick-inspector trace (BRIEF §5). |
| `::tick` | — | Tick time and budget for this world. |
| `::reload` | `::reload <drops\|shops\|spawns\|…>` | Hot-reloads data that's safe to reload, at a tick boundary. |
| `::bots` | `::bots spawn <n>\|clear` | Local bots. |
| `::copy` | `::copy <player>` | `ECONOMY`: copies their gear, inventory and stats. |

### Raids (owner and developer)

These need the raid framework from the raids prompt. If it isn't built yet, register them when it lands, and note that in PROGRESS.md.

**Live worlds.** The owner commands work on live worlds because none of them can create value. They inspect a run, end it, remove a player from it, or pay out a reward the run already earned, at most once. A run is named by its id or by any player in it.

#### Owner
| Command | Usage | Does |
|---|---|---|
| `::raids` | `::raids [world\|all]` | Lists active runs: raid, mode, party, current room, elapsed time and run id. |
| `::raid info` | `::raid info <run\|player>` | Shows a run's party, scaling, current room, deaths, points or contribution, and timers. |
| `::raid parties` | — | Lists parties forming on this world's boards. |
| `::raid watch` / `::raid leave` | `::raid watch <run\|player>` | Joins the run's instance as a hidden observer. Observers aren't in the party and can't interact; they're never targeted, scaled, counted or rewarded. Audited. |
| `::raid kick` | `::raid kick <player> [reason…]` | Removes a player from their run safely: no item loss, raid-only items removed, no rewards. The party carries on, with scaling unchanged. Confirmation. |
| `::raid end` | `::raid end <run\|player> <reason…>` | Ends a run safely for everyone, by the same rules as kick, then destroys the instance. The party sees the reason. Confirmation. |
| `::raid reward` | `::raid reward <run> [player]` | Shows the run's stored reward results and roll log: seed, every roll, recipients and claim state. |
| `::raid reissue` | `::raid reissue <run> <player>` | Delivers a stored, unclaimed reward that a bug lost. It goes to that raid's unclaimed-loot store (ToB chest, ToA Rewards Niche, TFA loot wheelbarrow), or to the bank for CoX. Uses the item-transaction API keyed by run and player. Never re-rolls, and refuses if the reward was already paid. Confirmation. |
| `::raid waivefee` | `::raid waivefee <player>` | Lets a player take back items held after a wipe without the reclaim fee (ToB, ToA, TFA). Confirmation. |
| `::raid kc` / `::raid pb` | `::raid kc <player> [raid]` / `::raid pb <player> [raid]` | Shows kill counts and personal bests per mode and team size. |
| `::raid pb remove` | `::raid pb remove <player> <raid> <mode> <team size>` | Clears a bugged personal best for that mode and team size. Confirmation. |
| `::raid disable` / `::raid enable` | `::raid disable <raid>` | Stops new runs of a raid on this world until restart; runs in progress finish. |
| `::raid stats` | — | Shows live instance counts, build and attach times, outcomes, and the unique rate against expected. |

#### Developer
Every command here except `::raid layout` (printing only), `::raid points` (showing only) and `::raid simulate` is `ECONOMY`, because a run it touches can still pay out. So they work only on worlds that allow `ECONOMY` (§3).

| Command | Usage | Does |
|---|---|---|
| `::raid start` | `::raid start <raid> [mode] [size]` | Starts a run with you and virtual party members. |
| `::raid skip` / `::raid room` | `::raid skip` / `::raid room <name>` | Completes the current room, or jumps to one. |
| `::raid level` | `::raid level <n>` | Sets ToA's raid level, with matching invocations. |
| `::raid layout` | `::raid layout [code]` | Prints the CoX layout code. Forcing a layout with `code` is `ECONOMY`. |
| `::raid seed` | `::raid seed <n>` | Fixes the next run's RNG seed. |
| `::raid points` | `::raid points [player] [set <n>]` | Shows points. Setting them is `ECONOMY`. |
| `::raid setkc` | `::raid setkc <player> <raid> <mode> <n>` | Kill counts unlock capes and more. |

## 7. Moderation records

- **Records.** Mutes, yell-mutes, jails, warnings, notes and bans (account, IP and device) are records in the accounts database, owned by the hub, with:
  - actor, reason, start, expiry and scope;
  - who lifted them, and when.
- **Cross-world.** An action on a player on another world, or offline, applies everywhere through the hub, either immediately or on their next login.
- **What the player sees.** Each of these shows the reason and the expiry: a muted player trying to chat, a jailed player trying to leave, and a banned account trying to log in.
- **The jail area** is data: a location plus an area that blocks teleports and exits. It is never inside the Wilderness or an instance.
- **The report interface.** A moderator's mute option in the official report interface creates the same record as `::mute`.

## 8. Security

- **Rank is checked on the server for every execution**, whether the command was typed or clicked. Interface buttons never carry rights of their own.
- **Escape everything user-typed** before showing it (yell, reasons, notes, names in lists). OSRS text tags such as `<col>`, `<img>` and `<br>` must not pass through, so nobody can fake a crown or a rank tag.
- **Privacy.** IP addresses, device identifiers and alt links are administrator and above only. They never appear in chat, and every view is audited.
- **Account security.** If the account system supports two-factor authentication, require it for administrator and above. If it doesn't, add that to PROGRESS.md as a follow-up.
- **No item bypass.** No command bypasses the item-transaction API.
- **Rank changes** are audited and take effect at once.
- **Passwords.** `::changepassword` needs the current password, is rate-limited, and is logged without arguments.

## 9. Config (a typed `CommandsConfig` loaded from TOML; per-world overrides)

```toml
[commands]
enabled_impacts = ["INFO", "SOCIAL", "ACCOUNT", "MODERATION", "OPERATIONS", "DEBUG_VISUAL"]
allow_economy_on_live = false
rate_limit_per_second = 5
rate_limit_per_minute = 30
confirm_timeout_seconds = 30
yell_cooldown_seconds = { PLAYER = 30, MODERATOR = 5 }
reason_presets = ["Spam", "Offensive language", "Scamming", "Botting", "Rule breaking"]
[commands.urls]
discord = ""
forums = ""
vote = ""
store = ""
hiscores = ""
[commands.overrides]     # e.g. teleto = "ADMINISTRATOR", skull = "disabled"
[ranks.bootstrap]        # applied once at startup and logged, e.g. developers = ["account-name"]
```

## 10. Tests (minimal)

1. **`CommandParserTest`**, table-driven:
   - quoting, and names with spaces and underscores;
   - each typed argument (durations, `k`/`m`/`b` amounts, every coordinate form, items by id and name, ambiguous players);
   - "did you mean" that never suggests commands above the player's rank.
2. **`CommandPermissionScenario`** (`tools/scenario`), with one player of each rank:
   - Every registered command can be seen and used exactly at its minimum rank.
   - The targeting rule holds.
   - World policy blocks `ECONOMY` on a live config.
   - Confirmation works.
   - Audit entries are written.
   - A guided interface run (player, command, arguments, confirm) produces the same invocation as the typed command.
   - The generated `docs/commands.md` is up to date.
   - If the raid framework is built: `::raid end` ejects everyone with no item loss and pays nothing. `::raid reissue` pays a stored unclaimed reward once and refuses a second time.
3. **`CrossWorldModerationScenario`** uses two worlds in one JVM, plus the hub. Mute, jail, kick and ban:
   - a player on the other world;
   - an offline player, applied on their next login.

   Then reverse each one and check the history.

Nothing else.

## 11. Order of work

1. Plan (§0). `scout` maps the existing interface, moderation and audit code.
2. Ranks: storage, the login response mapping, icons and the bootstrap.
3. Registry and parser; startup validation; doc generation.
4. Execution pipeline, audit, rate limits and confirmation.
5. The command suite (§6), rank by rank, with moderation records and cross-world actions. The raid commands come once the raid framework exists.
6. Interface views (§5).
7. Tests (§10), then a manual check in RuneLite through RSProx: every rank, typed and clicked.

## 12. Done when

- §10 passes, and every existing test is green, including the golden PvP scenarios, which stay unchanged.
- Every command in §6 works for its rank, both typed and through the interface, and fails cleanly below it.
- Staff can pick an online player on any world, choose a command, fill in arguments and confirm.
- Rank crowns show, and the client unlocks what the staff level should unlock.
- `docs/commands.md` is generated, and `docs/features/commands.md` explains the ranks, world policy, interface and security.

## 13. Not now

List these in PROGRESS.md:

- donator, YouTuber and other cosmetic ranks, and custom chat icons;
- a web moderation panel and a Discord bot;
- spectating another player's camera;
- per-staff macros;
- automated bot detection.
