#!/usr/bin/env bash
# Lists every entry of a built client jar that still mentions a Jagex, RuneLite or other third-party host.
# Usage: client/runelite/scan.sh [JAR]   (default: .data/client/fork/client-*-shaded.jar)
# Exit status is 1 when anything is found. The unpatched game client (obfuscated classes in the jar root) is
# expected to hit until client/gamepatch has rewritten it.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="${1:-$(ls "$HERE"/../../.data/client/fork/client-*-shaded.jar | head -n 1)}"
[ -f "$JAR" ] || { echo "scan.sh: no jar at $JAR" >&2; exit 2; }

DIR="$(mktemp -d)"
trap 'rm -rf "$DIR"' EXIT
unzip -q "$JAR" -d "$DIR"

PATTERNS='runelite\.net|runescape\.com|jagex\.com|osrs\.game|discord\.gg|discord\.com|patreon|wiseoldman\.net|runescape\.wiki'
found=0
for pat in 'runelite\.net' 'runescape\.com' 'jagex\.com' 'osrs\.game' 'discord\.gg' 'patreon' 'wiseoldman\.net' 'runescape\.wiki'; do
  hits="$(cd "$DIR" && grep -rlaiE "$pat" . | sort || true)"
  if [ -n "$hits" ]; then
    found=1
    echo "== $pat"
    echo "$hits" | sed 's/^/   /'
  fi
done
[ "$found" -eq 0 ] && echo "no hits for: $PATTERNS"
exit "$found"
