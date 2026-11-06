# World hopping

| # | Rule | Source |
|---|---|---|
| H1 | A player cannot switch worlds while the logout lock is active (16 ticks after being hit, W7). Message: "You can't switch worlds until 10 seconds after the end of combat." | LO:9 (Logout button page); text OPEN-H1 |
| H2 | The World Switcher (interface 69) lists worlds as dynamic children of `worldswitcher:buttons`; op 1 is "Switch". The server answers a valid click with LOGOUT_TRANSFER (host, world id, world flags) and the client reconnects to that world by itself. | RSProt `LogoutTransfer` KDoc ("used for world-hopping purposes"); cache interface 69 |
| H3 | The hop is a logout followed by a login: the logout save is final and the hub holds the account until it is durable, so the login on the new world always loads the latest save. | brief §7, DECISIONS D-026 |

## Tick timeline

1. Tick T: IF_BUTTON on `worldswitcher:buttons` (sub = world id) runs in the player's turn.
2. If H1 allows it, LOGOUT_TRANSFER is queued and the session is closed. The player logs out at the end of tick T, and the final save goes to the hub.
3. The client reconnects to the target world. Its login waits on the hub until the T save is durable (`HubConfig.pendingSaveWaitMs`), then completes normally.

## OPEN

- **OPEN-H1:** the exact message text, and whether hopping uses the 16-tick logout lock or a separate 20-tick timer. The
  Wiki pages "Xlog" and "World switching" disagree in search snippets, and they could not be fetched to confirm. The
  current behaviour is the logout lock (W7) with the message above.
- **OPEN-H2:** the dynamic child's sub is assumed to be the world id. The world node checks it against the hub's
  directory and replies "That world is not available." when it does not match. The favourite slots
  (`worldswitcher:favourite_1/2`) are not handled yet.
- Client-side hops (RuneLite's World Hopper) send nothing to the server. The client disconnects and logs in elsewhere,
  so H3 still applies through the hub.

Sources: LO = https://oldschool.runescape.wiki/w/Logout_button ; Xlog = https://oldschool.runescape.wiki/w/Xlog ;
World switching = https://oldschool.runescape.wiki/w/World_switching
