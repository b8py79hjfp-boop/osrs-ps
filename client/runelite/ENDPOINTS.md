# What the fork calls

Everything the patched RuneLite client requests over HTTP(S), so the hub can match it. `{site}` and `{api}` are the
`site` and `api` values in `release.properties` (dev: `http://127.0.0.1:8080` and `http://127.0.0.1:8080/runelite`);
`{static}` is `{api}/static`.

The HTTP client refuses every request whose host is not the host of one of `{site}`, `{api}` (also used for
`{static}`, config, session and ws) or loopback (`RuneLiteProperties.isReleaseHost`), so nothing else can leave the
machine. Anything not listed here is never requested.

This page describes the `allowlist` plugin mode. In `open` mode (README.md "Plugin modes") the host check is off and the
client also fetches the Plugin Hub from `https://repo.runelite.net/plugins` (manifest, plugin jars, icons); hub
plugins may contact any host of their own.

Behaviour on a missing endpoint is part of the contract: "required" means the client cannot start or the feature
breaks; "optional" means a 404/5xx only disables the feature (the failure is logged).

## Required

| Request | Response | Used by |
|---|---|---|
| `GET {site}/jav_config.ws` | The game's `jav_config` text: `key=value` lines, `param=key=value` lines, `msg=` lines ignored. The client needs `codebase`, `initial_jar` and `initial_class` (it fails with "Invalid or missing jav_config" otherwise) and passes every `param=` to the game. Retried 6 times, then startup fails. | `ClientLoader` |
| `GET {api}/config.json` | RuneLite's runtime config as JSON, an object whose fields are all optional. `{}` is valid. See "config.json" below. Not fetchable means the client starts without it. | `RuntimeConfigLoader` (once at startup) |

## Required for the world hopper and default world

`GET {api}/worlds.js` returns a RuneLite `WorldResult`. Field names and enum names are exactly those RuneLite's
`net.runelite.http.api.worlds` classes serialise with (checked against http-api 1.2.26):

```json
{"worlds":[{"id":301,"types":["MEMBERS","PVP"],"address":"127.0.0.1","activity":"PvP","region":"UNITED_KINGDOM","players":12}]}
```

- `id` int (world number); `address` string (host the world hopper and ping use); `activity` string; `players` int.
- `types`: any of `MEMBERS`, `PVP`, `BOUNTY`, `PVP_ARENA`, `SKILL_TOTAL`, `QUEST_SPEEDRUNNING`, `HIGH_RISK`,
  `HARDCORE_MAIN`, `LAST_MAN_STANDING`, `BETA_WORLD`, `LEGACY_ONLY`, `EOC_ONLY`, `NOSAVE_MODE`, `TOURNAMENT`,
  `FRESH_START_WORLD`, `DEADMAN`, `SEASONAL`. `[]` for a free world.
- `region`: `UNITED_STATES_OF_AMERICA`, `UNITED_KINGDOM`, `AUSTRALIA`, `GERMANY`, `BRAZIL`, `JAPAN`, `SINGAPORE`,
  `SOUTH_AFRICA`.
- Fetched at startup, then every 10 minutes while logged in (`WorldService`). If it fails the world hopper
  shows no worlds.

## Optional (features degrade without them)

| Request | Response | Used by |
|---|---|---|
| `GET {api}/item/prices.js` | JSON array of `{"id":4151,"name":"Abyssal whip","price":1500000,"wikiPrice":1490000}` (`price` = GE guide price, `wikiPrice` = real-time price; both longs). Fetched at startup and then every 30 minutes. | `ItemManager` (item prices, Ground Items, Grand Exchange, Loot Tracker, Bank) |
| `GET {static}/item/stats.ids.min.json` | JSON object keyed by item id: `{"4151":{"equipable":true,"weight":0.453,"ge_limit":70,"equipment":{"slot":3,"is2h":false,"astab":0,"aslash":82,"acrush":0,"amagic":0,"arange":0,"dstab":0,"dslash":0,"dcrush":0,"dmagic":0,"drange":0,"str":82,"rstr":0,"mdmg":0,"prayer":0,"aspeed":4}}}`. `equipment` is omitted for non-equipment. | Item Stats, bank tooltips |
| `GET {static}/npcs/npcs.min.json` | JSON object keyed by npc id: `{"2":{"hitpoints":8}}` (RuneLite's `NpcInfo` has the single field `hitpoints`; other fields are ignored). | NPC health bar and kill-time plugins |
| `GET {api}/hiscore/<slug>/index_lite.json?player=<name>` | Jagex's `index_lite.json` shape: `{"skills":[{"name":"Overall","rank":1,"level":2277,"xp":4600000000},...],"activities":[{"name":"Clue Scrolls (all)","rank":1,"score":10}]}`; `404` means player not found. Slugs: `normal`, `ironman`, `hardcore_ironman`, `ultimate`, `deadman`, `seasonal`, `tournament`, `fresh_start`, `skiller_defence`, `skiller`, `hardcore_mains`. | Player-owned House only (the hiscore panel, chat commands and opponent stats are not available) |
| `POST/GET {api}/chat/{kc,qp,task,pb,gc,duels,layout,pets}?...` | RuneLite's chat-command service. Answering `404` is fine: submits return `false` and lookups fail quietly. | Slayer, Chambers of Xeric and Player-owned House submit the player's own task, layout or killcount |

### config.json

`RuntimeConfig` fields, all optional (`props` and `sysProps` are applied to the client, so the hub is trusted with
them):

```json
{
  "props": {},
  "sysProps": {},
  "outageMessage": null,
  "ignoreDeadNpcs": [], "forceDeadNpcs": [], "resetDeadOnChangeNpcs": [],
  "forceDeadAnimations": [], "healthCheckDeadNpcs": [],
  "outdatedClientVersions": [], "outdatedPluginVersions": []
}
```

When `outageMessage` is set (optionally bounded by `outageStart` / `outageEnd`, ISO-8601 instants), the client shows
that text in a dialog instead of its generic network error whenever the jav_config download fails or the game
reports an error. It does not stop a client that starts normally.

## Site pages (links opened in the player's browser; any HTTP status is acceptable)

`{site}/discord`, `/community`, `/wiki` (and `/wiki/<Plugin-Name>` from a plugin's "Help" menu),
`/support/troubleshooting`, `/support/dns`, `/news`, `/status`, `/ge/item/<itemId>`.

## Never requested

These RuneLite service endpoints are not called (the code was removed or cannot be reached): `/session/ping`,
`/telemetry`, `/xp/update`, `/pluginhub` and `/manifest`, `/account/*`, `/config` (cloud sync), `/loottracker`,
`/ws` (party). `{api}/session`, `{api}/ws` and the plugin hub URL exist only as properties so the client can start.
The game client's own traffic (JS5, login, the web links inside the game) is not governed by this document.

## Served by the hub (2026-11-06)

- `{api}/worlds.js`, `{api}/item/prices.js` (GE guide prices) and `{api}/config.json` (`{}`) come from `HubHttp`.
- `{static}/item/stats.ids.min.json` and `{static}/npcs/npcs.min.json` are generated from our cache by
  `tp.cache.export.RuneLiteStatic`:
  - **Item stats** (15,851 items): bonuses from obj params 0–14 and 299, `is2h` from wearpos2 = shield, weight
    converted from grams to kg.
  - **NPC hitpoints** (13,994 NPCs): cache NPC stat 3.
  - **Not in the cache:** GE buy limits. `ge_limit` is 0.
  - **Who writes them:** the world node rewrites both into `.data/runelite-static/` at every boot; for a standalone hub,
    run `cache-dump runelite-static`.
  - **Serving:** the hub serves the directory at `{api}/static/` and refuses any path that resolves outside it.
