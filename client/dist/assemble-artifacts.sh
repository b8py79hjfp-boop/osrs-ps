#!/usr/bin/env bash
# Collects the jars the signed manifest lists (input of `release publish`) into <out-dir>:
#   the mirrored bootstrap artifacts (.data/client/artifacts, `./gradlew setupClient`)
#   with the injected client replaced by the game-patched one (.data/client/patched, client/gamepatch)
#   and RuneLite's client jar replaced by our fork's (.data/client/fork, client/runelite/build.sh).
# Usage: client/dist/assemble-artifacts.sh <out-dir>
# ALLOW_STOCK_CLIENT=1 keeps the stock RuneLite client jar when the fork has not been built (dry runs only).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA="$(cd "$HERE/../.." && pwd)/.data/client"
die() { echo "assemble-artifacts.sh: $*" >&2; exit 1; }
[ $# -eq 1 ] || die "usage: $0 <out-dir>"
OUT="$1"
ls "$DATA"/artifacts/*.jar >/dev/null 2>&1 || die "no jars in $DATA/artifacts: run ./gradlew setupClient"

rm -rf "$OUT"; mkdir -p "$OUT"
cp "$DATA"/artifacts/*.jar "$OUT/"

injected="$(ls "$DATA"/patched/injected-client-*.jar 2>/dev/null | head -n 1 || true)"
[ -n "$injected" ] || die "no patched injected client in $DATA/patched: run client/gamepatch"
cp "$injected" "$OUT/$(basename "$injected")"

fork="$(ls "$DATA"/fork/client-*-shaded.jar 2>/dev/null | head -n 1 || true)"
if [ -n "$fork" ]; then
  name="$(basename "$fork" -shaded.jar).jar"   # client-1.13.6-shaded.jar replaces client-1.13.6.jar
  [ -f "$OUT/$name" ] || die "the bootstrap has no $name to replace"
  cp "$fork" "$OUT/$name"
  echo "client jar: fork ($name)"
elif [ "${ALLOW_STOCK_CLIENT:-}" = "1" ]; then
  echo "WARNING: fork not built, keeping the stock RuneLite client jar (dry run only)" >&2
else
  die "no fork jar in $DATA/fork: run client/runelite/build.sh"
fi
echo "assembled $(ls "$OUT" | wc -l) jars in $OUT"
