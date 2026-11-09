#!/usr/bin/env bash
# Runs our release RuneLite fork with the runelite-mcp plugin, for driving the client from Claude Code (dev only).
#
#   tools/runelite-mcp/dev-client.sh            # build what is missing, then start the client
#   tools/runelite-mcp/dev-client.sh --xvfb     # same, on a virtual display :99 (headless machines, CI sandboxes)
#
# What it does, all outside git (.data/ and ~/.tickperfect are not committed):
# 1. .data/client/dev/client-dev.jar: a copy of the fork jar (.data/client/fork, client/runelite/build.sh) whose plugin
#    allowlist also lists tp.runelitemcp.McpPlugin. The release jar refuses every plugin not on its allowlist, sideloaded
#    ones included, and must stay that way.
# 2. The plugin jar into ~/.tickperfect/sideloaded-plugins (the fork's RuneLite directory is ~/.tickperfect, not
#    ~/.runelite), and `runelite.mcpplugin=true` in every profile so it starts enabled.
# 3. java -ea -jar client-dev.jar --developer-mode (sideloading needs developer mode, which needs assertions).
# The MCP endpoint is then http://127.0.0.1:8765/mcp (README.md section 3 for `claude mcp add`).
set -euo pipefail
cd "$(dirname "$0")/../.."

FORK=.data/client/fork/client-1.13.6-shaded.jar
DEV_DIR=.data/client/dev
DEV_JAR=$DEV_DIR/client-dev.jar
PLUGIN=tools/runelite-mcp/build/libs/runelite-mcp.jar
RL_DIR=${TICKPERFECT_DIR:-$HOME/.tickperfect}
ALLOWLIST=net/runelite/client/plugins/allowlist.txt

[ -f "$FORK" ] || { echo "missing $FORK: run client/runelite/build.sh first" >&2; exit 1; }
[ -f "$PLUGIN" ] || ./gradlew -q -p tools/runelite-mcp build

if [ ! -f "$DEV_JAR" ] || [ "$FORK" -nt "$DEV_JAR" ]; then
    rm -rf "$DEV_DIR/x" && mkdir -p "$DEV_DIR/x/$(dirname "$ALLOWLIST")"
    cp "$FORK" "$DEV_JAR"
    unzip -p "$DEV_JAR" "$ALLOWLIST" > "$DEV_DIR/x/$ALLOWLIST"
    printf '\n# Dev copy only (%s): the runelite-mcp tool\ntp.runelitemcp.McpPlugin\n' "$DEV_DIR" >> "$DEV_DIR/x/$ALLOWLIST"
    (cd "$DEV_DIR/x" && zip -q "../$(basename "$DEV_JAR")" "$ALLOWLIST")
    rm -rf "$DEV_DIR/x"
    echo "built $DEV_JAR"
fi

mkdir -p "$RL_DIR/sideloaded-plugins"
cp "$PLUGIN" "$RL_DIR/sideloaded-plugins/"
for profile in "$RL_DIR"/profiles2/*.properties; do
    [ -f "$profile" ] || continue
    grep -q '^runelite.mcpplugin=' "$profile" || echo 'runelite.mcpplugin=true' >> "$profile"
done

if [ "${1:-}" = "--xvfb" ]; then
    pgrep -x Xvfb > /dev/null || (Xvfb :99 -screen 0 1280x900x24 > /dev/null 2>&1 &)
    export DISPLAY=:99
fi
echo "starting the client; MCP at http://127.0.0.1:8765/mcp once it is up (a first start creates the profile:"
echo "if the plugin is off, run this script again after the client has started once)"
exec java -ea -Xmx1g -jar "$DEV_JAR" --developer-mode
