# Signing hooks for package.sh (sourced). Secrets come only from the environment, never from the repo.
#
# Windows (Authenticode):
#   WINDOWS_CERT_PFX        path to the code-signing certificate (.pfx)
#   WINDOWS_CERT_PASSWORD   its password
#   WINDOWS_TIMESTAMP_URL   RFC 3161 timestamp server (default http://timestamp.digicert.com)
#   Uses signtool (Windows SDK) if found, else osslsigncode (Linux/macOS).
# macOS (Developer ID, hardened runtime, notarization):
#   APPLE_SIGNING_USER      the certificate's name without the prefix, e.g. "Example Ltd (ABCDE12345)"; jpackage looks up
#                           "Developer ID Application: <it>" and "Developer ID Installer: <it>" in the keychain
#   APPLE_KEYCHAIN          optional keychain path (CI uses a temporary one)
#   APPLE_ID, APPLE_TEAM_ID, APPLE_APP_PASSWORD   notarytool credentials (app-specific password)
# Any other case produces unsigned packages and prints a warning. REQUIRE_SIGNING=1 turns that warning into an error
# (release builds).

warn_unsigned() {
  echo "WARNING: $1 - the packages are UNSIGNED and will trigger SmartScreen/Gatekeeper warnings." >&2
  [ "${REQUIRE_SIGNING:-}" != "1" ] || { echo "REQUIRE_SIGNING=1: refusing to continue" >&2; exit 1; }
}

win_signing_enabled() { [ -n "${WINDOWS_CERT_PFX:-}" ]; }

# win_sign <file>...
win_sign() {
  win_signing_enabled || return 0
  [ -f "$WINDOWS_CERT_PFX" ] || { echo "WINDOWS_CERT_PFX is set but $WINDOWS_CERT_PFX does not exist" >&2; exit 1; }
  [ -n "${WINDOWS_CERT_PASSWORD:-}" ] || { echo "WINDOWS_CERT_PASSWORD is not set" >&2; exit 1; }
  local ts="${WINDOWS_TIMESTAMP_URL:-http://timestamp.digicert.com}" f st
  st="$(command -v signtool.exe || command -v signtool || true)"
  if [ -z "$st" ]; then   # Windows SDK installs signtool outside PATH
    st="$(ls -d "/c/Program Files (x86)/Windows Kits/10/bin/"*/x64/signtool.exe 2>/dev/null | sort -V | tail -n 1 || true)"
  fi
  for f in "$@"; do
    echo "signing $f"
    if [ -n "$st" ]; then
      "$st" sign /f "$WINDOWS_CERT_PFX" /p "$WINDOWS_CERT_PASSWORD" /fd sha256 /tr "$ts" /td sha256 "$f"
    elif command -v osslsigncode >/dev/null; then
      osslsigncode sign -pkcs12 "$WINDOWS_CERT_PFX" -pass "$WINDOWS_CERT_PASSWORD" -h sha256 -ts "$ts" -in "$f" -out "$f.signed"
      mv "$f.signed" "$f"
    else
      echo "neither signtool nor osslsigncode found" >&2; exit 1
    fi
  done
}

mac_signing_enabled() { [ -n "${APPLE_SIGNING_USER:-}" ]; }
mac_notarize_enabled() { [ -n "${APPLE_ID:-}" ] && [ -n "${APPLE_TEAM_ID:-}" ] && [ -n "${APPLE_APP_PASSWORD:-}" ]; }

# mac_sign_args: extra jpackage arguments that make it sign (empty when not configured)
mac_sign_args() {
  mac_signing_enabled || return 0
  printf '%s\n' --mac-sign --mac-signing-key-user-name "$APPLE_SIGNING_USER" --mac-entitlements "$HERE/entitlements.plist"
  [ -z "${APPLE_KEYCHAIN:-}" ] || printf '%s\n' --mac-signing-keychain "$APPLE_KEYCHAIN"
}

# mac_sign_dmg <dmg>: jpackage does not sign the disk image itself
mac_sign_dmg() {
  mac_signing_enabled || return 0
  codesign --force --timestamp --sign "Developer ID Application: $APPLE_SIGNING_USER" \
    ${APPLE_KEYCHAIN:+--keychain "$APPLE_KEYCHAIN"} "$1"
}

# mac_notarize <dmg-or-pkg>...: submit, wait, staple
mac_notarize() {
  mac_notarize_enabled && mac_signing_enabled || return 0
  local f
  for f in "$@"; do
    echo "notarizing $f"
    xcrun notarytool submit "$f" --apple-id "$APPLE_ID" --team-id "$APPLE_TEAM_ID" --password "$APPLE_APP_PASSWORD" --wait
    xcrun stapler staple "$f"
  done
}
