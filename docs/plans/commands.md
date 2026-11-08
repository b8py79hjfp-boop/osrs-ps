# Command system: plan

The brief is [commands-brief.md](commands-brief.md) (saved verbatim). This plan covers what exists, what's missing,
how the custom interface works, the modules touched, and the risks. The facts come from the `scout` map of
2026-11-08 and use file:line references.

## Name mapping (repo → brief)

| Brief | Repo today | Plan |
|---|---|---|
| Ranks PLAYER < MODERATOR < ADMINISTRATOR < OWNER < DEVELOPER | `services Rights { PLAYER, MODERATOR, ADMIN }` (Services.kt:26); `Player.staffLevel` 0/1/2 | `Rank` enum with five values. `ADMIN` is read as `ADMINISTRATOR` (save and DB value `ADMIN` kept, so no data migration). `OWNER` and `DEVELOPER` are added. |
| Moderation module | hub `ModerationService.apply` (Hub.kt:341-363), `Sanction` (Services.kt:348) | Extended with yell-mute, jail, warn, note and device ban, plus expiry and lift records (§7). |
| Audit log | `audit_log(id, at, actor, action, target, detail)` (PostgresStore.kt:602) | Extended to the §4 fields: rank, world, tick, outcome, source. |
| Custom interface | `spawn_menu.if3` + OpenRune cs2 (content/dev pack); the only custom interface | A new `commands.if3` built the same way (see below). |
| Config | CLI flags only (Main.kt; `--admins`) | `config/server.toml` (TOML, the user's choice; D-087). |

## What exists

- **Dispatch.**
  - `ClientCommand.Cheat` is handled at World.kt:456-459: the text is split on whitespace, the first token is
    lowercased, and `content.onCommand` is called. Unhandled input returns "Unknown command: …".
  - The tick inspector and replay already record `Cheat` (TickInspector.kt:32, Replay.kt:58/99).
- **Commands.**
  - **Dev** (DevCommands.kt, no rank check): `pos`, `run`, `tele`, `hp`, `setlevel`, `spellbook`, `max`, `kit`,
    `wild`, `trace`, `instance`, `item`, `if`, `npc`, `fight`, `empty`, `xp`.
  - **`spawn`** (SpawnMenu.kt:27, ADMIN only).
  - **Staff** (StaffCommands.kt:14-27): moderator `kick`, `mute`, `unmute`; admin `ban`, `ipban`, `unban`,
    `broadcast`, `update`.
  - **`raid`** (RaidsPlugin.kt:35, the load-profile dev command).
  - **`quest`, `collectionlog`, `diary`**, each with its own ad hoc rank check.
  - **`::ghosts` and `::phasing` don't exist.** There is no phasing system in the repo, so their move to the registry
    waits for phasing.
- **Moderation.**
  - Mutes and bans are columns on `accounts` (`muted_until`, `banned_until`); IP bans are a table (`ip_bans`).
  - Kick goes through hub kick sinks.
  - Bans are enforced at login (Hub.kt:107/128). Mutes are enforced in public chat (World.kt:601), PMs (Hub.kt:303)
    and friends chat (FriendChats.kt:69). **Clan chat is not verified.**
  - Report abuse gives a 48 h mute (HubAccountBridge.kt:258).
- **Login response.** `staffModLevel = profile.staffLevel`; `playerMod = false` is hardcoded (Network.kt:462-463);
  `NoAuthenticator` is sent.
- **Chat icons.**
  - **Public chat:** modicon is hardcoded 0 (RspSession.kt:223-226).
  - **PMs, friends chat, clan chat:** they send `rights.ordinal` (WorldLinks.kt:64/93, ClanLinks.kt:42).
  - The sprite mapping is not verified.
- **Dialogs.** `countDialog`, `stringDialog`, `objDialog` (item search), `choiceDialog` and `confirmOverlay`
  (Refs.kt:388-442) on `ResumeCount/String/Obj/Pause`. `UrlOpen` exists only in vendored RSProt.
- **Presence.** The hub knows `online` (account → world) but has no RPC that lists every online player.
- **Two-factor authentication:** none. Login is password-only.

## How the custom interface works (what `::commands` reuses)

- **Source.** `content/<module>/src/main/resources/pack/interfaces/*.if3` is TOML-like: an `[[interface]]` with
  `on_load` hooks, and `[[component]]`s of type layer, rectangle, text, graphic, model or line, with position, size,
  options, text and scroll size (tools/cache-pack If3.kt, InterfacePacker.kt).
- **Behaviour.** cs2 scripts (`pack/cs2/script/*.cs2`, Neptune compiler) and configs (inventories, varps and varbits
  in `pack/configs/*.toml`).
- **Packing.** `./gradlew :tools:cache-pack:run --args="pack"` writes `.data/cache/242-packed`, which the server
  serves.
- **From the server:**
  - opened with `openModal(MAINMODAL, id)`; `ifEvents` unlocks ops;
  - rows go out as one string of fixed-width labels through a server-run client script (`clientScript(id, args…)`,
    Int/Long/String only, SpawnMenu.kt:76-86);
  - item grids go out as an inventory;
  - search arrives as IF_SCRIPTTRIGGER, buttons through `onButton`.
- **So it can show:** a search box, a scrolled list of text rows (paged server-side), a detail text block, buttons,
  and a status line. That's enough for the command list, the player list and card, the guided run, tickets and
  recent commands (§5). Arguments use the OSRS dialogs already wrapped.

## What's missing (work)

1. **Ranks.**
   - Five ranks; `staffLevel` refreshed live through the hub on a rank change (today it's set only at login,
     World.kt:1294).
   - Demotion closes the staff views.
   - Login mapping: `playerMod` for moderator, `staffModLevel` for administrator and up. To be checked against the
     cache cs2 that reads `staffmodlevel` and `playermod`.
   - Icons in all four chats: public chat currently sends 0.
   - Bootstrap: a CLI task plus the config list, applied once and audited. `--admins` stays as a deprecated alias.
2. **Registry and parser.**
   - Kotlin DSL; typed arguments (§2); startup validation (duplicates, reserved names, descriptions and examples,
     target commands without a player argument).
   - Per-world overrides; `docs/commands.md` generated from the registry.
   - `content.onCommand` becomes the single entry: a `CommandRegistry` in plugin-api, with
     `ContentScope.command(...)`. The existing `onCommand` registrations move onto it.
3. **Pipeline (§4).** The steps run in this order:
   1. parse;
   2. resolve;
   3. rank;
   4. targeting rule;
   5. world policy (impact classes, live flag, `allow_economy_on_live`);
   6. rate limit and cooldown;
   7. confirm (`::confirm`, 30 s);
   8. execute;
   9. audit;
   10. reply.

   Off-thread hub work returns through the pre-tick inbox, so replay sees it. Unknown or forbidden commands get the
   identical reply, with "did you mean" drawn only from usable commands.
4. **Moderation records (§7).**
   - Yell-mute, jail (a data area that blocks teleports and exits), warn, note, device ban, history.
   - Expiry plus who lifted a record and when.
   - Offline and cross-world application through the hub; the report interface's mute writes the same record.
   - The storage change goes through the existing `ALTER … IF NOT EXISTS` pattern plus new tables. Logins and the
     existing columns stay as they are; **stop and ask before changing how logins or moderation records are stored**
     (brief §0.7).
5. **The command suite (§6),** rank by rank. Each needs:
   - **`UrlOpen`:** a protocol-api message plus the adapter.
   - **Hub presence list:** a new RPC with a pushed cache, for `::players`, the Players view and `::staff`.
   - **PvP stat tracking:** for `::kdr`.
   - **Named-locations file:** for `::tele <place>`.
   - **Device id:** check what RSProt's login block decodes.
   - **The raid owner and developer tables:** they replace the current `::raid` load command (it becomes
     `::raid start` / `::raid skip` …).
6. **Interface views (§5):** a new `commands.if3` with cs2 in the same pack style. Command list, Players and card,
   guided run, Tickets, Recent.
7. **Tests (§10):** `CommandParserTest`, `CommandPermissionScenario`, `CrossWorldModerationScenario`.

## Modules touched

| Module | Change |
|---|---|
| `services` | `Rank` (replaces `Rights`); moderation record types; presence and moderation protos |
| `hub` | rank changes and pushes; records and history; presence list; audit fields; bootstrap |
| `engine` | `Player.rank` (live); `Cheat` routed to the registry; rate-limit counters; jail area check; invisible flag in info |
| `protocol-api`, `protocol/osrs-242` | `UrlOpen`; login `playerMod`; public-chat modicon |
| `plugin-api` | `CommandRegistry`, DSL, argument types, parser, pipeline, confirmation |
| `server` | config loading and per-world policy; bootstrap CLI; `docs/commands.md` generation task |
| `content/gameframe` | staff, social and info commands; the interface views |
| `content/dev` | dev commands moved onto the registry |
| `content/raids-core` | the §6 raid commands |
| `tools/scenario` | the two scenarios |

## Risks

- **Rights at the edges.** Every check must sit in the pipeline, never in buttons. The interface only builds an
  invocation and goes through the same path (§8).
- **Determinism.** Off-thread lookups must come back through the inbox; commands affect replay only through recorded
  input.
- **Moderation storage.** New columns and tables on live accounts data: additive only, no rewrites. Anything else
  waits for the user.
- **Economy on live.** The policy defaults are off; enabling them is config-only plus a restart (brief §3, §0.7).
- **Moving existing commands.** `onCommand` callers in five modules move to the registry. Behaviour stays as it is
  and is covered by the scenario.
- **Client features.** What `staffmodlevel` and `playermod` actually unlock must be read from the cache cs2 before
  mapping. RuneLite's dev-tools plugin shadows same-named commands such as `anim`, `gfx` and `setvarp` locally; this
  gets documented.

## Order (brief §11)

1. This plan, then the config-format decision.
2. Ranks.
3. Registry and parser.
4. Pipeline.
5. The suite, rank by rank, raid commands last.
6. Interface.
7. Tests, then the RuneLite check (needs the user).

The work is committed at each green milestone.

## Status

- **Milestone 2, ranks:** done (D-087). Five ranks, the login `playerMod` / `staffModLevel`, crowns in all four chats,
  the TOML config with per-world policy, and the bootstrap (config list plus `./gradlew admin`). The rest comes with
  the commands that use them:
  - live rank pushes, with `::setrank`;
  - hidden staff, with `::invisible`;
  - demotion closing the staff views, with the interface.
- **Client facts:**
  - OPEN-CMD1 and OPEN-CMD2 stay open for the RuneLite check.
  - Device bans use RSProt's 24-byte login `uuid` (LoginBlockDecoder.kt:135-138).
  - Output stays on game messages (type 0); the console type (99) is not confirmed to render in RuneLite.
  - RuneLite's developer-tools plugin acts locally on 19 command names (`DevToolsPlugin`, lines 336-568).

