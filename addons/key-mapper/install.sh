#!/usr/bin/env bash
# Build Remote keys, install it and turn its accessibility service on.
#   TV_HOST=<tv ip> addons/key-mapper/install.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.orion1911.keymapper
SVC=$PKG/.KeyService
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/keymapper.apk
OUT=$(tv 'pm install -r /data/local/tmp/keymapper.apk; rm -f /data/local/tmp/keymapper.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# The key filter: add this service to the enabled list without dropping the others (the launcher's
# BoostService, for one), then open the screen. The list is read here, not in a remote case: adb shell
# quoting turned the earlier one-liner into a syntax error that only stderr showed.
ENABLED=$(tv 'settings get secure enabled_accessibility_services')
# The list keeps whichever form it has: this script writes ".KeyService", Android may have stored the
# full class name, so match the package and never add a second entry for it.
case "$ENABLED" in
    *$PKG*) ;;
    null|"") tv "settings put secure enabled_accessibility_services $SVC" ;;
    *) tv "settings put secure enabled_accessibility_services $ENABLED:$SVC" ;;
esac
tv 'settings put secure accessibility_enabled 1'
echo "enabled: $(tv 'settings get secure enabled_accessibility_services')"
tv "am start -n $PKG/.MainActivity >/dev/null; echo opened"
echo "Installed. Remote keys is open on the TV."
