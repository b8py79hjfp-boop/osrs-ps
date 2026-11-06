#!/usr/bin/env bash
# Builds the installers for one platform (brief §4.2, D-028): launcher + jlink runtime -> jpackage.
#
# Usage: client/dist/package.sh <os> <arch>      os: linux|windows|macos   arch: x64|arm64
# Run it on the target OS (jpackage cannot cross-package). Output: client/dist/build/out/<name>-<ver>-<os>-<arch>.*
#   linux    .deb and .tar.gz
#   windows  .msi and .exe
#   macos    .dmg and .pkg
# Needs: a JDK 17+ with jpackage (JPACKAGE_JDK, else JAVA_HOME, else PATH); on Linux fakeroot + dpkg-deb; on Windows
# WiX 3.x; on macOS Xcode command line tools. The runtime comes from the pinned JDK in jdk-versions.sh.
# Environment (all optional):
#   INSTALLER_VERSION  overrides installer.version in app.properties
#   UPDATE_URL         overrides the launcher's updateUrl (-Dlauncher.updateUrl in the installed app)
#   SKIP_LAUNCHER_BUILD=1   reuse client/launcher/build/install/launcher
#   REQUIRE_SIGNING=1  fail instead of warning when signing credentials are missing
#   signing variables: see sign.sh
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
# shellcheck source=jdk-versions.sh
source "$HERE/jdk-versions.sh"
# shellcheck source=sign.sh
source "$HERE/sign.sh"

die() { echo "package.sh: $*" >&2; exit 1; }
prop() { sed -n "s/^$2=//p" "$1" | tail -n 1; }

[ $# -eq 2 ] || die "usage: $0 <linux|windows|macos> <x64|arm64>"
OS="$1"; ARCH="$2"
jdk_select "$OS" "$ARCH" || exit 1

case "$(uname -s)" in
  Linux) HOST=linux ;; Darwin) HOST=macos ;; MINGW*|MSYS*|CYGWIN*) HOST=windows ;; *) die "unknown host $(uname -s)" ;;
esac
[ "$HOST" = "$OS" ] || die "jpackage builds for the host OS only: run on $OS (this is $HOST)"
EXE=""
if [ "$HOST" = windows ]; then   # native Windows tools (jpackage, WiX) need C:/... paths, which Git Bash also accepts
  EXE=".exe"; HERE="$(cygpath -m "$HERE")"; REPO="$(cygpath -m "$REPO")"
  [ -z "${JPACKAGE_JDK:-}" ] || JPACKAGE_JDK="$(cygpath -m "$JPACKAGE_JDK")"
  [ -z "${JAVA_HOME:-}" ] || JAVA_HOME="$(cygpath -m "$JAVA_HOME")"
fi

APP_PROPS="$HERE/app.properties"
NAME="$(prop "$APP_PROPS" name)"; ID="$(prop "$APP_PROPS" id)"; VENDOR="$(prop "$APP_PROPS" vendor)"
DESCRIPTION="$(prop "$APP_PROPS" description)"
VERSION="${INSTALLER_VERSION:-$(prop "$APP_PROPS" installer.version)}"
LP="$REPO/client/launcher/src/main/resources/tp/client/launcher/launcher.properties"
[ "$(prop "$LP" name)" = "$NAME" ] && [ "$(prop "$LP" id)" = "$ID" ] \
  || die "name/id in app.properties differ from launcher.properties (the launcher keeps its files in ~/.<id>)"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "installer version '$VERSION' must be major.minor.patch"

# --- jpackage (needs 17+) ------------------------------------------------------------------------------------------
if [ -n "${JPACKAGE_JDK:-}" ]; then JPACKAGE="$JPACKAGE_JDK/bin/jpackage$EXE"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jpackage$EXE" ]; then JPACKAGE="$JAVA_HOME/bin/jpackage$EXE"
else JPACKAGE="$(command -v jpackage || true)"; fi
[ -x "${JPACKAGE:-}" ] || die "jpackage not found: set JPACKAGE_JDK to a JDK 17+"
JP_MAJOR="$("$JPACKAGE" --version | cut -d. -f1)"
[ "$JP_MAJOR" -ge 17 ] 2>/dev/null || die "jpackage $JP_MAJOR is too old (need 17+)"

BUILD="$HERE/build"; CACHE="$BUILD/cache"; WORK="$BUILD/$OS-$ARCH"; OUT="$BUILD/out"
mkdir -p "$CACHE" "$OUT"; rm -rf "$WORK"; mkdir -p "$WORK"

# --- pinned JDK -> runtime -----------------------------------------------------------------------------------------
sha256_of() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
ARCHIVE="$CACHE/$JDK_ARCHIVE"
if [ ! -f "$ARCHIVE" ] || [ "$(sha256_of "$ARCHIVE")" != "$JDK_SHA256" ]; then
  echo "downloading JDK $JDK_VERSION for $OS-$ARCH"
  curl -fsSL -o "$ARCHIVE" "$JDK_URL"
fi
[ "$(sha256_of "$ARCHIVE")" = "$JDK_SHA256" ] || die "checksum mismatch for $JDK_URL"
mkdir -p "$WORK/jdk"
case "$ARCHIVE" in
  *.tar.gz) tar -xzf "$ARCHIVE" -C "$WORK/jdk" ;;
  *.zip) unzip -q "$ARCHIVE" -d "$WORK/jdk" 2>/dev/null || 7z x -y -o"$WORK/jdk" "$ARCHIVE" >/dev/null \
           || powershell.exe -NoProfile -Command "Expand-Archive -Force '$(cygpath -w "$ARCHIVE")' '$(cygpath -w "$WORK/jdk")'" ;;
esac
JDK_HOME="$(dirname "$(find "$WORK/jdk" -maxdepth 4 -type d -name jmods -print -quit)")"
[ -d "$JDK_HOME/jmods" ] || die "no jmods directory in $JDK_ARCHIVE"
"$HERE/build-runtime.sh" "$JDK_HOME" "$WORK/runtime"

# --- launcher ------------------------------------------------------------------------------------------------------
if [ -z "${SKIP_LAUNCHER_BUILD:-}" ]; then ( cd "$REPO" && ./gradlew --console=plain :client:launcher:installDist ); fi
LAUNCHER_LIB="$REPO/client/launcher/build/install/launcher/lib"
[ -f "$LAUNCHER_LIB/launcher.jar" ] || die "$LAUNCHER_LIB/launcher.jar missing (build :client:launcher:installDist)"
mkdir -p "$WORK/input"; cp "$LAUNCHER_LIB"/*.jar "$WORK/input/"
[ ! -f "$REPO/client/runelite/LICENSES.txt" ] || cp "$REPO/client/runelite/LICENSES.txt" "$WORK/input/LICENSES.txt"

# --- jpackage ------------------------------------------------------------------------------------------------------
common=(--name "$NAME" --app-version "$VERSION" --vendor "$VENDOR" --description "$DESCRIPTION")
appopts=()
[ -z "${UPDATE_URL:-}" ] || appopts=(--java-options "-Dlauncher.updateUrl=$UPDATE_URL")
image=(--input "$WORK/input" --main-jar launcher.jar --main-class tp.client.launcher.LauncherKt \
       --runtime-image "$WORK/runtime" "${appopts[@]+"${appopts[@]}"}")
final() { # final <built file> <ext>: move into out/ under the release naming
  local dest="$OUT/$NAME-$VERSION-$OS-$ARCH.$2"; mv "$1" "$dest"; echo "built $dest"; PRODUCED+=("$dest")
}
PRODUCED=()
DEST="$WORK/jpackage"; mkdir -p "$DEST"

case "$OS" in
linux)
  icon=(--icon "$HERE/branding/app.png")
  "$JPACKAGE" --type app-image "${common[@]}" "${image[@]}" "${icon[@]}" --dest "$DEST/app"
  ( cd "$DEST/app" && tar -czf "$WORK/$NAME.tar.gz" "$NAME" )
  final "$WORK/$NAME.tar.gz" tar.gz
  "$JPACKAGE" --type deb "${common[@]}" "${image[@]}" "${icon[@]}" --dest "$DEST/pkg" \
    --linux-package-name "$ID" --linux-deb-maintainer "$(prop "$APP_PROPS" linux.maintainer)" \
    --linux-app-category "$(prop "$APP_PROPS" linux.category)" --linux-shortcut
  final "$(ls "$DEST"/pkg/*.deb)" deb
  ;;
windows)
  win_signing_enabled || warn_unsigned "WINDOWS_CERT_PFX is not set"
  icon=(--icon "$HERE/branding/app.ico")
  "$JPACKAGE" --type app-image "${common[@]}" "${image[@]}" "${icon[@]}" --dest "$DEST/app"
  win_sign "$DEST/app/$NAME/$NAME.exe"
  for t in msi exe; do
    "$JPACKAGE" --type "$t" "${common[@]}" --app-image "$DEST/app/$NAME" "${icon[@]}" --dest "$DEST/pkg" \
      --win-upgrade-uuid "$(prop "$APP_PROPS" win.upgrade.uuid)" --win-menu --win-shortcut --win-per-user-install
    win_sign "$DEST/pkg/$NAME-$VERSION.$t"
    final "$DEST/pkg/$NAME-$VERSION.$t" "$t"
  done
  ;;
macos)
  mac_signing_enabled || warn_unsigned "APPLE_SIGNING_USER is not set"
  mac_notarize_enabled || echo "WARNING: APPLE_ID/APPLE_TEAM_ID/APPLE_APP_PASSWORD not all set: the packages are not notarized" >&2
  mapfile -t sign < <(mac_sign_args)
  icon=(--icon "$HERE/branding/app.icns")
  mac=(--mac-package-identifier "$(prop "$APP_PROPS" mac.bundle.id)" --mac-package-name "$NAME")
  "$JPACKAGE" --type app-image "${common[@]}" "${image[@]}" "${icon[@]}" "${mac[@]}" "${sign[@]+"${sign[@]}"}" --dest "$DEST/app"
  for t in dmg pkg; do
    "$JPACKAGE" --type "$t" "${common[@]}" --app-image "$DEST/app/$NAME.app" "${icon[@]}" "${mac[@]}" \
      "${sign[@]+"${sign[@]}"}" --dest "$DEST/pkg"
  done
  mac_sign_dmg "$DEST/pkg/$NAME-$VERSION.dmg"
  mac_notarize "$DEST/pkg/$NAME-$VERSION.dmg" "$DEST/pkg/$NAME-$VERSION.pkg"
  final "$DEST/pkg/$NAME-$VERSION.dmg" dmg
  final "$DEST/pkg/$NAME-$VERSION.pkg" pkg
  ;;
esac

# --- smoke test: the installed launcher starts and, with no update server, refuses cleanly ---------------------------
if [ "$OS" != windows ] && [ -z "${SKIP_SMOKE:-}" ]; then
  if [ "$OS" = macos ]; then BIN="$DEST/app/$NAME.app/Contents/MacOS/$NAME"; else BIN="$DEST/app/$NAME/bin/$NAME"; fi
  SMOKE_HOME="$WORK/smoke-home"; mkdir -p "$SMOKE_HOME"
  # Port 9 (discard) is closed, so the update check fails fast; user.home is an empty directory, so no manifest has been cached.
  set +e
  msg="$(JAVA_TOOL_OPTIONS="-Duser.home=$SMOKE_HOME -Dlauncher.updateUrl=http://127.0.0.1:9/update" "$BIN" 2>&1)"; code=$?
  set -e
  grep -q "Can't reach the update server and no verified client is installed yet" <<<"$msg" \
    || { echo "$msg" >&2; die "smoke test failed: the packaged launcher did not report the expected message (exit $code)"; }
  echo "smoke test ok: launcher ran on the bundled runtime (exit $code)"
fi
echo "done: ${PRODUCED[*]}"
