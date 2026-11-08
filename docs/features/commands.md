# Commands, ranks and moderation

How `::` commands work on this server: ranks, world policy, the `::commands` interface and security. The command
list itself is generated: see [docs/commands.md](../commands.md) (`./gradlew :server:commandsDoc`). Design notes are in
docs/plans/commands.md, the rules and OPENs in docs/mechanics/commands.md, and the decisions in DECISIONS D-086 to D-092.

## Ranks

| Rank | Crown | Login staff fields | Can target | Can set ranks |
|---|---|---|---|---|
| Player | — | — | — | — |
| Moderator | player-moderator | `playerMod` | players | — |
| Administrator | J-Mod | `playerMod`, `staffModLevel` 2 | moderators and players | — |
| Owner | J-Mod | same | everyone below owner | up to administrator, on lower ranks |
| Developer | J-Mod | same | everyone except developers | any rank, on non-developers |

- **Ranks only add.** Each rank can use every command of the ranks below it.
- **Targeting.** A staff command reaches only a strictly lower rank (developers: anyone but another developer).
  Targeting yourself is always allowed. The world checks this for online targets, and the hub checks it for every
  target against the stored rank, so offline targets obey it too.
- **The first ranks** come from `[ranks.bootstrap]` in `config/server.toml`, applied once per account at its next
  login and audited. A trailing `*` matches a name prefix, e.g. `developers = ["bot*"]` on a dev world. There is no
  in-game path to the first developer; the other way in is `./gradlew admin --setRank=<account>:DEVELOPER --db=<jdbc>`.
- **`::setrank`** is checked and audited by the hub and takes effect at once: on the player's world, and in every
  world's presence. A demotion closes the player's staff views.
- **Hidden staff** (`::invisible`) are left out of player lists for lower ranks. The avatar is hidden from every
  player for now (OPEN-CMD7).

## World policy

Every command has an impact class. Each world's `[commands]` (or `[worlds.N]`) section in `config/server.toml`
enables a set of them:

| Impact | What it can do | Live worlds |
|---|---|---|
| INFO, SOCIAL, ACCOUNT | read-only, chat, own account | on |
| MODERATION, OPERATIONS, DEBUG_VISUAL | staff tools | on |
| CONVENIENCE | changes gameplay, not items (`::home`, `::skull`, `::heal`) | off |
| ECONOMY | creates or destroys items, xp or currency (`::item`, `::master`, the spawn menu, raid dev tools) | off, unless `allow_economy_on_live` |

- **ECONOMY** can only come from the file, and so only changes with a restart. `::command enable` can never turn it
  on.
- **Per-command overrides:** `[commands.overrides]` raises or lowers a command's rank, or disables it. Staff commands
  never go below moderator.
- **Hidden when off:** a command whose impact class the world turns off is hidden there, exactly like a command the
  rank can't use.
- **Old-style handlers** (`onCommand(name, rank, impact)`) obey the same rank and impact rules. So do the
  value-creating content tools (spawn menu, quest stages, diary tasks), which re-check on every click.
- **Runtime changes** (owner, until restart): `::command enable|disable <name>`, and `::config <key> [value]` for rate
  limits, the confirmation timeout and yell cooldowns.

## The pipeline

Typed commands and interface clicks run through the same pipeline (§4):

1. **Rate limit:** 5 a second and 30 a minute per player; excess is dropped.
2. **Lookup:** the command is looked up by name or alias. A command that doesn't exist, or that the player may not
   use here, gets exactly "Unknown command. Type ::commands for a list.", plus a "did you mean" drawn only from
   commands the player can use. Official behaviour stays: `::bank` (the joke) and emote names.
3. **Arguments:** typed arguments are parsed (amounts with `k`/`m`/`b`, durations, players, items/NPCs/locs by id or
   name, coordinates in three forms, skills, ranks, choices, rest of line). Wrong arguments get the usage line.
4. **Checks:** the targeting rule, then any cooldown.
5. **Confirmation:** destructive commands or forms wait up to 30 s for `::confirm`.
6. **Run:** the command runs on the world thread, in the input phase. Hub work replies through the pre-tick inbox.
7. **Audit:** moderator-and-above commands, and sensitive player commands with their arguments redacted, go to the
   hub's append-only audit log. Each row records actor, rank, command, arguments, target, world, tick, outcome and
   source (typed or interface).

## Moderation

- **Records.** Mutes, yell-mutes, jails, kicks, warnings, notes, renames and bans (account, IP and device) are rows in
  `moderation_records`, owned by the hub. Each row holds actor, reason, start, expiry, scope, and who lifted it and
  when. `::history` shows them; IP and device scopes are visible to administrators only.
- **Cross-world.** Everything applies through the hub: at once on the player's world, or at their next login when
  they are offline.
- **What the player sees.** A muted player who tries to chat sees the reason and the expiry, and so does a jailed
  player who tries to leave.
- **The jail** is `[commands.jail]`: a tile plus a square around it, never in the Wilderness or an instance. It is
  checked every tick, and teleports out are refused. On expiry or `::unjail` the player goes home, even if it
  expired while they were offline.
- **The report interface.** A moderator's "mute" in the official report interface writes the same record as `::mute`.

## The `::commands` interface

- **Commands (everyone):** the commands the viewer can use here, by category and with search. The detail view shows
  the signatures, examples, rank, and whether a target or confirmation is needed. Arguments are asked with OSRS's own
  dialogs; the typed equivalent is shown before it runs.
- **Players (moderator and above):** everyone online on every world, from hub presence cached on each world. The
  player card shows status and the last five records, plus the target commands the viewer may use on that player.
- **Guided run (staff):** player, command, arguments (duration presets and the configured reason presets), then a
  confirmation screen for destructive commands.
- **Tickets (moderator and above):** players open them with `::ticket`; staff can claim, teleport, and close with a
  note.
- **Recent (staff):** the viewer's last ten commands.
- **Private lists** (IP addresses, alts, the audit log) are only ever shown here, never in chat.

## Security

- **Rank is checked on the server for every execution.** Interface buttons carry no rights; a guided run is a command
  line dispatched as `Source.INTERFACE`.
- **Escaping.** Everything user-typed is escaped before it is shown (yells, reasons, notes, names), so `<col>`,
  `<img>` and `<br>` can't fake a crown or a rank tag.
- **Privacy.** IP addresses, device ids and alt links are administrator-only, audited on every view, and never shown
  in chat.
- **Items.** No command bypasses the item-transaction API. Staff spawns are transactions labelled `staff spawn`.
- **Passwords.** `::changepassword` asks for the current password, is rate-limited by the hub, and is audited without
  arguments.
- **Two-factor authentication** doesn't exist yet. It is a PROGRESS follow-up, after which it will be required for
  administrator and above.
- **RuneLite.** The developer-tools plugin, in developer mode, handles some same-named commands locally (`anim`,
  `gfx`, `setvarp` and others). A developer in that mode who types one of ours gets both effects.
