# Commands

Generated from the command registry by `./gradlew :server:commandsDoc`; do not edit by hand.
Ranks, world policy, the interface and security are explained in docs/features/commands.md.
Each rank can also use every command of the ranks below it. A world's `config/server.toml` can
raise or lower a command's rank, or disable it; impact classes it does not enable are hidden there.

## Player

| Command | Usage | Aliases | Impact | Target | Does |
|---|---|---|---|---|---|
| `::confirm` | `::confirm` | — | INFO | — | Runs the destructive command waiting for confirmation (within 30 seconds). |
