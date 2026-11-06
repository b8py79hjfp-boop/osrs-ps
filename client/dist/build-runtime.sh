#!/usr/bin/env bash
# Builds the jlink runtime the launcher and the RuneLite client run on (brief §4.2, D-028).
#
# Usage: client/dist/build-runtime.sh <jdk-home> <out-dir>
#   jdk-home   a full JDK with jmods/ (Temurin 11, or 17 for macOS arm64; see jdk-versions.sh)
#   out-dir    the runtime image to create (replaced if it exists)
# The module list is client/dist/modules.txt. Optional modules (leading '?') are added only if the JDK has them.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[ $# -eq 2 ] || { echo "usage: $0 <jdk-home> <out-dir>" >&2; exit 2; }
JDK="$1"; OUT="$2"

[ -d "$JDK/jmods" ] || { echo "build-runtime.sh: $JDK/jmods not found; jlink needs a full JDK, not a JRE" >&2; exit 1; }
EXE=""; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) EXE=".exe" ;; esac
JLINK="$JDK/bin/jlink$EXE"; JAVA="$JDK/bin/java$EXE"
[ -x "$JLINK" ] || { echo "build-runtime.sh: $JLINK missing" >&2; exit 1; }

available="$("$JAVA" --list-modules | sed 's/@.*//')"
modules=()
while read -r mod _; do
  case "$mod" in ''|'#'*) continue ;; esac
  if [ "${mod#\?}" != "$mod" ]; then
    mod="${mod#\?}"
    grep -qx "$mod" <<<"$available" || { echo "optional module $mod not in this JDK, skipped"; continue; }
  else
    grep -qx "$mod" <<<"$available" || { echo "build-runtime.sh: required module $mod missing from $JDK" >&2; exit 1; }
  fi
  modules+=("$mod")
done < "$HERE/modules.txt"
MODS="$(IFS=,; echo "${modules[*]}")"

rm -rf "$OUT"
"$JLINK" \
  --module-path "$JDK/jmods" \
  --add-modules "$MODS" \
  --strip-debug --no-header-files --no-man-pages --compress 2 \
  --output "$OUT"

echo "runtime: $OUT ($("$OUT/bin/java$EXE" -version 2>&1 | grep -m1 version))"
echo "modules: $MODS"
