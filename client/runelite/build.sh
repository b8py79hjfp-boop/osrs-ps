#!/usr/bin/env bash
# Builds the release client fork: clones the pinned RuneLite tag, applies patches/*.patch, overlays the allowlist,
# licenses and branding, substitutes the release tokens, builds the shaded runnable jar and copies it to
# .data/client/fork/.
#
# Usage: client/runelite/build.sh [WORKDIR]
#   WORKDIR   scratch clone, outside the repository (default: ${TMPDIR:-/tmp}/tp-runelite-fork). It is wiped on
#             every run, but only if it carries the marker file this script writes.
# Environment:
#   RUNELITE_GIT    clone source (default https://github.com/runelite/runelite.git; a local clone works offline)
#   GRADLE_ARGS     extra arguments for the Gradle build in the clone (for example --offline)
#   SKIP_BUILD=1    stop after patching and substitution (for checking the patches)
#   PLUGINS         allowlist | open; overrides release.properties `plugins`. open also applies patches-open/
#                   (Plugin Hub on, every plugin loads, no release-host check; README.md "Plugin modes")
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
WORK="${1:-${TMPDIR:-/tmp}/tp-runelite-fork}"
MARKER=".tp-runelite-work"
RUNELITE_GIT="${RUNELITE_GIT:-https://github.com/runelite/runelite.git}"

die() { echo "build.sh: $*" >&2; exit 1; }

# prop FILE KEY: value of KEY in a key=value file (comments and blank lines ignored; last one wins)
prop() { sed -n "s/^$2=//p" "$1" | tail -n 1; }

# Escapes a replacement for the right-hand side of a sed s||| command.
sed_escape() { printf '%s' "$1" | sed -e 's/[\\|&]/\\&/g'; }

TAG="$(prop "$REPO/gradle.properties" osrs.runeliteTag)"
NAME="$(prop "$HERE/release.properties" name)"
SITE="$(prop "$HERE/release.properties" site)"
API="$(prop "$HERE/release.properties" api)"
BASE_COMMIT="$(prop "$HERE/release.properties" base.commit)"
PLUGINS="${PLUGINS:-$(prop "$HERE/release.properties" plugins)}"
PLUGINS="${PLUGINS:-allowlist}"
case "$PLUGINS" in allowlist|open) ;; *) die "plugins must be allowlist or open, not '$PLUGINS'" ;; esac
[ -n "$TAG" ] || die "osrs.runeliteTag is missing from gradle.properties"
[ -n "$NAME" ] && [ -n "$SITE" ] && [ -n "$API" ] || die "name, site and api must be set in release.properties"
[ -n "$BASE_COMMIT" ] || die "base.commit is missing from release.properties"

case "$WORK" in
  "$REPO"|"$REPO"/*) die "WORKDIR must be outside the repository: $WORK" ;;
esac

# --- clone the pinned tag -------------------------------------------------------------------------------------
if [ -e "$WORK" ]; then
  [ -f "$WORK/$MARKER" ] || die "$WORK exists and was not created by this script; choose another WORKDIR"
  rm -rf "$WORK"
fi
mkdir -p "$WORK"
case "$RUNELITE_GIT" in /*) SRC="file://$RUNELITE_GIT" ;; *) SRC="$RUNELITE_GIT" ;; esac
git clone --quiet --branch "$TAG" --depth 1 "$SRC" "$WORK/src" || die "cannot clone tag $TAG from $RUNELITE_GIT"
touch "$WORK/$MARKER"
ACTUAL="$(git -C "$WORK/src" rev-parse HEAD)"
[ "$ACTUAL" = "$BASE_COMMIT" ] || die "tag $TAG is $ACTUAL, expected $BASE_COMMIT (release.properties base.commit)"

# --- apply the patch series -----------------------------------------------------------------------------------
cd "$WORK/src"
git config user.name "tickperfect build"
git config user.email "build@localhost"
PATCHES=("$HERE"/patches/*.patch)
[ -e "${PATCHES[0]}" ] || die "no patches in $HERE/patches"
echo "plugin mode: $PLUGINS"
git am --quiet --3way "${PATCHES[@]}" || die "a patch did not apply (see README.md, 'Rebasing onto a new tag')"
if [ "$PLUGINS" = open ]; then
  # RuneLite's hub icons and manifest certificate come from the pinned commit, not from the repository.
  mapfile -t RESTORE < <(grep -v '^[[:space:]]*\(#\|$\)' "$HERE/patches-open/restore-from-base.txt")
  git checkout "$BASE_COMMIT" -- "${RESTORE[@]}" || die "cannot restore the open-mode files from $BASE_COMMIT"
  git commit --quiet -m "Open plugins: restore the Plugin Hub icons and manifest certificate"
  OPEN_PATCHES=("$HERE"/patches-open/*.patch)
  [ -e "${OPEN_PATCHES[0]}" ] || die "no patches in $HERE/patches-open"
  git am --quiet --3way "${OPEN_PATCHES[@]}" || die "an open-mode patch did not apply"
fi

# --- overlay: allowlist, licenses, branding, release tokens ---------------------------------------------------
RES="runelite-client/src/main/resources"
cp "$HERE/allowlist.txt" "$RES/net/runelite/client/plugins/allowlist.txt"
cp "$HERE/LICENSES.txt" "$RES/LICENSES.txt"
cp -R "$HERE/branding/resources/." "$RES/"

E_NAME="$(sed_escape "$NAME")"; E_SITE="$(sed_escape "$SITE")"; E_API="$(sed_escape "$API")"
for f in "$RES/net/runelite/client/runelite.properties" "$RES/LICENSES.txt"; do
  sed -i -e "s|@@NAME@@|$E_NAME|g" -e "s|@@SITE@@|$E_SITE|g" -e "s|@@API@@|$E_API|g" "$f"
done
if grep -rIl '@@[A-Z]*@@' runelite-client/src runelite-client/build.gradle.kts 2>/dev/null; then
  die "unsubstituted release tokens remain in the files listed above"
fi

# Commit the overlay so the build does not mark the client as a dirty build (runelite.dirty).
git add -A
git commit --quiet -m "Release overlay: allowlist, licenses, branding and tokens"

[ -z "${SKIP_BUILD:-}" ] || { echo "SKIP_BUILD set: patched tree is in $WORK/src"; exit 0; }

# --- build ----------------------------------------------------------------------------------------------------
# shellcheck disable=SC2086
./gradlew ${GRADLE_ARGS:-} --no-daemon :client:assemble -x javadoc

VERSION="$(prop gradle.properties project.build.version)"
JAR="runelite-client/build/libs/client-$VERSION-shaded.jar"
[ -f "$JAR" ] || die "expected $JAR was not produced"

OUT="$REPO/.data/client/fork"
mkdir -p "$OUT"
cp "$JAR" "$OUT/"
FORK_JAR="$OUT/$(basename "$JAR")"

# --- game client patch (brief §4.2 change set 2) ---------------------------------------------------------------
# The shaded jar bundles RuneLite's injected game client unpatched (Jagex modulus and URLs). Patch it by value with
# client/gamepatch (each rule must match its pinned count) and overwrite those classes inside the shaded jar.
INJECTED="$REPO/.data/client/artifacts/injected-client-$VERSION.jar"
[ -f "$INJECTED" ] || die "missing $INJECTED: run ./gradlew setupClient first"
PATCHED="$REPO/.data/client/patched/injected-client-$VERSION.jar"
( cd "$REPO" && ./gradlew -q :client:gamepatch:run --args="$INJECTED $PATCHED --site $SITE" )
CLASSES="$(mktemp -d)"
( cd "$CLASSES" && unzip -q "$PATCHED" '*.class' )
N_PATCHED="$(cd "$CLASSES" && find . -name '*.class' | wc -l)"
( cd "$CLASSES" && find . -name '*.class' -print0 | xargs -0 jar uf "$FORK_JAR" )
rm -rf "$CLASSES"
echo "replaced $N_PATCHED game-client classes in $(basename "$FORK_JAR") with the patched build"
( cd "$OUT" && sha256sum "$(basename "$JAR")" > "$(basename "$JAR").sha256" )
echo "built $OUT/$(basename "$JAR")"
