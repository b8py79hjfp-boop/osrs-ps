# runelite-mcp

A RuneLite plugin that runs a small **MCP server** inside the client, so a Claude Code session on the same PC can
observe and drive the RuneLite client while we develop the tickperfect server: read game state, widgets, varps and
chat, click interface buttons exactly like a human would, run `::commands`, and (most useful) read a ring buffer of
client events that shows *what the client sent* and *what the server changed*.

Dev tool only. It is a standalone Gradle build, not part of the root build, so the main build and CI never download
RuneLite. Nothing from RuneLite is committed; the plugin jar contains only our classes.

## 1. Build

```bash
./gradlew -p tools/runelite-mcp build
```

Output: `tools/runelite-mcp/build/libs/runelite-mcp.jar` (our classes only; RuneLite supplies Gson, Guice and the API).
`build` also runs the `smoke` task, which starts the HTTP/JSON-RPC layer with fake tools and checks initialize,
tools/list, tools/call (text, image, error), the 405/401/403 paths and the bearer token. Run it alone with
`./gradlew -p tools/runelite-mcp smoke`. It needs no RuneLite client.

Compiles against `net.runelite:client:1.13.6` (the bootstrap for rev 242, docs/CLIENT.md) from `https://repo.runelite.net`,
Java 11 bytecode, no Lombok, no extra runtime dependencies (JDK `com.sun.net.httpserver` + what RuneLite ships).

## 2. Install as a sideloaded plugin

RuneLite 1.13.6 loads `*.jar` files from `~/.runelite/sideloaded-plugins/` **only in developer mode**
(`PluginManager.loadSideLoadPlugins`: `if (!developerMode) return;`). Plugins are discovered by scanning the jar for
`@PluginDescriptor` classes, so no `runelite-plugin.properties` is needed.

```bash
# Linux / macOS
mkdir -p ~/.runelite/sideloaded-plugins && cp tools/runelite-mcp/build/libs/runelite-mcp.jar ~/.runelite/sideloaded-plugins/
# Windows (PowerShell)
New-Item -ItemType Directory -Force $env:USERPROFILE\.runelite\sideloaded-plugins
Copy-Item tools\runelite-mcp\build\libs\runelite-mcp.jar $env:USERPROFILE\.runelite\sideloaded-plugins\
```

### Launched by RSProx (our dev path, docs/CLIENT.md)

Verified by reading the RSProx source and its launcher jar (`cdn.rsprox.net/runelite/launcher`, 2.7.4-SNAPSHOT):

- RSProx starts RuneLite with `--developer-mode` always (`RuneliteLauncher.getLaunchArgs`). No flag or setting is needed.
- Stock RuneLite ignores `--developer-mode` when started by its launcher and also demands `-ea`. RSProx's patched
  launcher contains a `DeveloperToolsPatcher` that removes both checks (the launcher-version test and the assertions
  test), so developer mode is on and sideloading works without extra JVM flags.
- RSProx does not change the RuneLite directory: the client still uses `~/.runelite` (`RuneLite.RUNELITE_DIR` is
  `user.home/.runelite` in 1.13.6, and none of RSProx's patchers touch it). So the jar goes into the normal
  `~/.runelite/sideloaded-plugins/`. Note that this directory is shared with a normal RuneLite install; the plugin is
  marked `developerPlugin`, so a non-developer-mode RuneLite skips it.

Steps:

1. Copy the jar as above, start the server (`./gradlew :server:run`), start RSProx, choose the `tickperfect` target,
   launch RuneLite.
2. In the RuneLite console / `~/.runelite/logs/client.log` look for `Side-loading plugin ...runelite-mcp.jar`.
3. Open the plugin list, search **MCP Server**, switch it on (default off, RuneLite remembers it). The log then shows
   `MCP server listening on http://127.0.0.1:8765/mcp (N tools, token not required)`.

**Unverified:** this has not been run against a live client yet (the author's sandbox has none). Everything above is
from source. If the `Side-loading` line is missing, either the jar is in the wrong home directory (RSProx may run
with a different `user.home`, check the `Java VM arguments`/`RuneLite ... starting up, args:` lines in the log for
`--developer-mode`) or the RSProx build differs from 2.7.4-SNAPSHOT. If a class fails to load, the error is logged as
`error sideloading plugin`. If `NoClassDefFoundError: com/sun/net/httpserver/HttpServer` appears, the JVM that runs
the client lacks the `jdk.httpserver` module (RuneLite's own OAuth session code needs it too, so this is unlikely).

### Plain RuneLite (without RSProx), for reference

Run the client from source or with the launcher flag `--developer-mode`, and add `-ea` to the JVM args. A plain
RuneLite cannot talk to our server; this is only useful against another test setup.

## 3. Connect Claude Code (same PC)

```bash
claude mcp add --transport http runelite http://127.0.0.1:8765/mcp
```

With a bearer token set in the plugin config (recommended on shared machines):

```bash
claude mcp add --transport http runelite http://127.0.0.1:8765/mcp --header "Authorization: Bearer <token>"
```

(`-H` is the short form; add `--scope user` or `--scope project` to choose where it is stored. Check with
`claude mcp list` or `/mcp` inside a session.)

Plugin settings (RuneLite config panel, group `mcp`): **enabled**, **port** (default 8765), **bearer token**
(default empty = none), **event buffer size** (default 2000). Changing enabled/port/token restarts the server.

Security: binds to `127.0.0.1` only (not configurable); requests must come from a loopback address, and a `Host` or
`Origin` header naming anything but `127.0.0.1`/`localhost`/`[::1]` is rejected with 403 (DNS-rebinding guard).
Anyone who can reach the port can drive your client, so use the token on multi-user machines.

## 4. Tools

All client-state access runs on the client thread (`ClientThread.invoke` + a 5 s timeout). Results are one JSON text
part, except `screenshot` (PNG image part plus a size note). Failures come back as `isError: true`.

| Tool | What it does |
|---|---|
| `get_state` | Game state, name, world, location (x, y, plane), scene location, tick, run energy %, run flag, hp/prayer, all skills, top-level and sub interfaces (with names), selected widget, camera |
| `get_inventory` | Inventory (container 93) with item names; `container_id` for others (95 = bank while open) |
| `get_equipment` | Worn items with slot names |
| `get_nearby` | NPCs, players, ground items, objects within `radius` tiles: ids, names, world and **scene** coordinates (the p0/p1 for `menu_action`), actions |
| `get_widget` | Components of an interface group (or one component): packed id, type, text, name, actions, hidden, bounds, item id, child counts. `visible_only`, `children` (dynamic/static children, e.g. inventory slots) |
| `find_widgets` | Search all loaded interfaces by text/name, action, or item id; returns packed ids |
| `click_widget` | A real widget click: `menuAction(index, packedId, CC_OP, op, itemId, option, target)`; by packed id or group+child, optional dynamic child `index`, op number or option text; `dry_run` shows what would be sent |
| `menu_action` | Raw `client.menuAction(p0, p1, action, id, item_id, option, target)` for walk, NPC/object/ground item/player ops |
| `walk_to` | `WALK` menu action to a world tile inside the loaded scene |
| `send_chat` | Public chat line via the chatbox send script (5517) |
| `run_command` | A `::command`, typed as key events into the game canvas, then Enter |
| `get_messages` | Last N chat/game messages (ring buffer of 500) |
| `get_events` | Event ring buffer with tick numbers: `MenuOptionClicked`, `VarbitChanged`, `WidgetLoaded`, `WidgetClosed`, `GameStateChanged`, `StatChanged`, `ItemContainerChanged`, `ChatMessage`. Params `since_seq`, `types`, `limit` |
| `get_varps` | Given varp ids / varbit ids, or all non-zero varps |
| `screenshot` | PNG of the game canvas (next rendered frame), optional `scale` / `max_width` |

### Menu action cheat sheet (for `menu_action`)

These follow RuneLite's conventions; confirm once by clicking by hand and reading `get_events`
(`MenuOptionClicked` records `action`, `id`, `param0`, `param1` of a real click).

| Target | action | id | p0 | p1 |
|---|---|---|---|---|
| NPC op n | `NPC_FIRST_OPTION`..`NPC_FIFTH_OPTION` | npc index | 0 | 0 |
| Object op n | `GAME_OBJECT_FIRST_OPTION`..`FIFTH` | object id | scene x | scene y |
| Ground item op n (Take is usually 3rd) | `GROUND_ITEM_FIRST_OPTION`..`FIFTH` | item id | scene x | scene y |
| Player op n | `PLAYER_FIRST_OPTION`..`EIGHTH` | player index | 0 | 0 |
| Widget op (IF3) | `CC_OP` (`CC_OP_LOW_PRIORITY` for op > 5) | op number | child index or -1 | packed id |
| Walk | `WALK` | 0 | scene x | scene y |

`get_nearby` returns `index`, `id`, `scene_x`, `scene_y` for each entry. For `item_id` pass -1 when none.

### How `click_widget` mirrors a real click

For an IF3 component it sends type `CC_OP` (or `CC_OP_LOW_PRIORITY` for ops above 5, the same split
`MenuEntrySwapperPlugin` uses), identifier = op number, param0 = the component's `getIndex()` (-1 for a static
component, the slot for a dynamic child), param1 = `getId()` (packed id), and the component's item id. The client's
own menu code then sends the IF_BUTTONx packet, so the server sees what a human click produces. The call also fires
`MenuOptionClicked`, which `get_events` records.

### Chat and commands: what was chosen

- Plain chat: `client.createScriptEventBuilder(ScriptID.CHAT_SEND, text, 0, 0, 0, -1).build().setCanSendPackets(true).run()`.
  This is verbatim what RuneLite's `ChatInputManager.sendChatboxInput` does to resume a chatbox send (script 5517,
  chat type 0 = public).
- `::commands`: script 5517 does **not** handle them. In the two script dumps bundled with RuneLite that we read (5517 and 73)
  the `docheat` instruction (which sends CLIENT_CHEAT) appears only in the chatbox key handler (script 73, `ChatDefaultOnKey`, which reads the chat
  input varc 335). So `run_command` dispatches synthetic AWT key events (`KEY_PRESSED`/`KEY_TYPED`/`KEY_RELEASED`)
  straight to the game canvas, then Enter. The effect is the same as typing. If your client uses "Press Enter to
  Chat", pass `press_enter_first`.
- Both are fire-and-forget; read the result with `get_messages` / `get_events`.
- **Unverified:** the key-event path and `WALK` through `menuAction` have not been run against a live client.

## 5. Example debugging flow

"Click the run orb, then see what the client sent and what the server changed."

1. `get_events` with no arguments, note `latest_seq` (say 412). Or `get_state` for the current tick and run flag.
2. `find_widgets` with `action: "Toggle Run"` (or inspect `get_widget` for the minimap group) and read the packed id.
3. `click_widget` with that `widget`. Use `dry_run: true` first to see the exact `menuAction` parameters.
4. `get_events` with `since_seq: 412`. Expect:
   - `MenuOptionClicked` with `action: CC_OP`, `id: 1`, `param1` = the packed id, `widget_constant` if known. This is
     the input the client turned into an IF_BUTTON packet (the raw bytes are in RSProx's packet log).
   - `VarbitChanged` with `varp_id: 173` and the new value, if the server answered with a varp. If there is no
     `VarbitChanged`, the server did not send one.
5. `get_state` again to compare `run_enabled`, or `get_messages` for the game message.

Another: `run_command` `{"command": "tele 3222 3218"}`, then `get_events` filtered to
`types: ["GameStateChanged","WidgetLoaded"]` and `get_state` for the new location.

## 6. Source layout

| File | Role |
|---|---|
| `McpPlugin.java` | `@PluginDescriptor` entry point, config wiring, event capture |
| `McpConfig.java` | `@ConfigGroup("mcp")` settings |
| `McpServer.java` | HTTP + JSON-RPC (Streamable HTTP, JSON replies only). JDK + Gson only |
| `ClientTools.java` | The tools; client-thread bridge |
| `EventLog.java`, `ToolResult.java`, `ToolDef.java`, `Schema.java`, `GameNames.java` | helpers |
| `src/smoke/java/.../SmokeMain.java` | Smoke harness, not in the jar. `serve <port> [token]` runs the fake server for manual `curl` |

Protocol support: `POST /mcp` with `initialize` (echoes a supported `protocolVersion`, else 2025-06-18),
`notifications/*` (202, no body), `ping`, `tools/list`, `tools/call`; JSON-RPC batches are accepted. `GET`/`DELETE`
answer 405 (no SSE stream, no sessions).
