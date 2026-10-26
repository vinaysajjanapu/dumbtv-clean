#!/usr/bin/env bash
# Installs dumbtv home on the TV and gives it the HOME button.
#   ./launcher/install.sh            build from source, install, take over HOME
#   ./launcher/install.sh --build    same
#   ./launcher/install.sh --undo     hand HOME back to Amazon's launcher
# Pick the device with ADB_SERIAL when more than one is connected.
# Needs a JDK and the Android SDK. See launcher/README.md.
# The original project's release APK is a different app (tv.dumbtv.home). This tree
# does not install it.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

# Application id and Java package. The published dumbtv home (tv.dumbtv.home) is
# signed with another key, so it cannot be updated in place. If it is still
# installed, it is disabled after this one is up.
PKG=tv.vinay.dumbtv
GUARD=$PKG/$PKG.Guard
LEGACY=tv.dumbtv.home
# An earlier build of this fork used the new application id with the old class names.
SPLIT=$PKG/$LEGACY.Guard
OLD_GUARD=com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService
ADB=${ADB:-adb}

adb_() { "$ADB" ${ADB_SERIAL:+-s "$ADB_SERIAL"} "$@"; }
sh_() { adb_ shell "$@" </dev/null; }
installed() { sh_ pm path "$1" 2>/dev/null | grep -q '^package:'; }
# shellcheck disable=SC1091
source ../scripts/home.sh

mode=build
case ${1:-} in
  ""|--build) ;;
  --undo) mode=undo ;;
  *) echo "usage: $0 [--build | --undo]" >&2; exit 2 ;;
esac

command -v "$ADB" >/dev/null || { echo "error: adb not found — install Android platform-tools" >&2; exit 1; }
state=$(adb_ get-state 2>/dev/null || true)
[[ $state == device ]] || { echo "error: no TV connected (adb connect <tv-ip>:5555; set ADB_SERIAL if several)" >&2; exit 1; }

dev_sdk=$(sh_ getprop ro.build.version.sdk | tr -d '\r')
case $dev_sdk in ''|*[!0-9]*) dev_sdk=28 ;; esac

# Fire OS 6's `settings` command silently drops writes to enabled_accessibility_services
# (exit code 0, value unchanged); the settings content provider still takes them.
put_secure() {
  local got
  sh_ settings put secure "$1" "${2:-\"\"}"
  got=$(sh_ settings get secure "$1" | tr -d '\r')
  [[ $got == "$2" || ( -z $2 && $got == null ) ]] && return 0
  sh_ content insert --uri content://settings/secure --bind "name:s:$1" --bind "value:s:$2"
}

# Puts $1 (or nothing) in place of any launcher's service, keeping every other service.
set_guard() {
  local cur
  orig=$(sh_ settings get secure enabled_accessibility_services | tr -d '\r')
  [[ $orig == null ]] && orig=
  cur=$(printf '%s' "$orig" | tr ':' '\n' | { grep -vxF -e "$GUARD" -e "$PKG/.Guard" -e "$SPLIT" -e "$LEGACY/.Guard" -e "$LEGACY/$LEGACY.Guard" -e "$OLD_GUARD" -e '' || true; } | paste -sd: -)
  if [[ -n ${1:-} ]]; then cur=${cur:+$cur:}$1; fi
  # Clearing first makes the accessibility manager rebind a service whose process died. The
  # other services live only in $orig meanwhile: if this is cut short, the trap puts them back.
  trap 'put_secure enabled_accessibility_services "$orig" || true' EXIT
  trap 'exit 130' INT TERM
  put_secure enabled_accessibility_services ""
  sleep 1
  if [[ -n $cur ]]; then
    put_secure enabled_accessibility_services "$cur"
    put_secure accessibility_enabled 1
  else
    put_secure accessibility_enabled 0
  fi
  trap - EXIT INT TERM
}

if [[ $mode == undo ]]; then
  # force-stop also keeps the Fire OS 6 Keeper from restarting at the next boot.
  sh_ am force-stop $PKG >/dev/null 2>&1 || true
  sh_ appops set $PKG GET_USAGE_STATS default >/dev/null 2>&1 || true
  set_futurity off
  set_guard ""
  sh_ dumpsys deviceidle whitelist -$PKG >/dev/null 2>&1 || true
  sh_ am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1 || true
  echo "HOME handed back to Amazon's launcher (dumbtv home stays installed: adb uninstall $PKG)"
  exit 0
fi

build_apk() {
  ./build.sh
  APK=build/dumbtv-home.apk
}

# adb ends its output with a blank line, so take the line that carries the result.
install_apk() {
  out=$(adb_ install -r "$APK" 2>&1 | grep -m1 -E 'Success|Failure|failed' || true)
}

build_apk
install_apk
case $out in
  *Success*) ;;
  *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
    echo "error: $PKG is already installed and was signed with a different key." >&2
    echo "  Replacing it means uninstalling it (adb uninstall $PKG), which deletes its settings;" >&2
    echo "  on Fire OS 8 they cannot be backed up over adb first." >&2
    exit 1 ;;
  *) echo "error: adb install failed: $(printf '%s' "$out" | tail -1)" >&2; exit 1 ;;
esac
echo "installed $PKG"

sh_ dumpsys deviceidle whitelist +$PKG >/dev/null

# Fire OS 6.7 refuses third-party accessibility services (A11yAuthorizer), so the Guard
# cannot bind there. Keeper watches Amazon's home through UsageStats and calls
# moveTaskToFront instead, which is not subject to the 5 s app-switch delay.
if [[ $dev_sdk -lt 28 ]]; then
  set_guard ""
  sh_ appops set $PKG GET_USAGE_STATS allow
  start_home $PKG/.Home
  sleep 3
  if ! grep -q 'isForeground=true' <<<"$(sh_ dumpsys activity services $PKG 2>/dev/null)"; then
    echo "error: the Fire OS 6 home keeper did not start ($PKG/.Keeper)" >&2
    exit 1
  fi
  # Right after the start nothing may be resumed yet, and a single empty read stopped
  # the whole pass on a TV where the home screen was fine.
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    front=$(sh_ dumpsys activity activities | grep -m1 'ResumedActivity: ' || true)
    [[ $front == *"$PKG"* ]] && break
    sleep 1
  done
  if [[ -z $front ]]; then
    echo "note: could not read which app is in front; ./scripts/verify.sh checks the Keeper"
  elif [[ $front != *"$PKG"* ]]; then
    echo "error: dumbtv home is installed but not in front ($front)" >&2
    exit 1
  fi
  if installed "$LEGACY"; then
    sh_ pm disable-user --user 0 "$LEGACY" >/dev/null 2>&1 || true
    echo "note: $LEGACY is disabled so HOME stays on $PKG. Its settings are still on the TV."
  fi
  echo "home: dumbtv (Keeper, no accessibility; sdk $dev_sdk)"
  exit 0
fi

sh_ cmd package set-home-activity $PKG/.Home >/dev/null 2>&1 || true
set_futurity on
set_guard $GUARD
sleep 2
# The setting alone proves nothing: Fire OS decides whether the service is bound. On
# Fire OS 6.7 its A11yAuthorizer refuses services from packages Amazon does not trust.
# The dump names the service by its label, not its package, so match the label inside the
# current user's bound-services section (same parser verify.sh uses). Right after a fresh
# install a Fire TV Cube took about 8 s to bind it.
source ../scripts/accessibility.sh
user=$(sh_ am get-current-user | tr -d '\r')
bound=0
for _ in $(seq 1 15); do
  if sh_ dumpsys accessibility | guard_bound "${user:-0}"; then bound=1; break; fi
  sleep 1
done
if [[ $bound == 0 ]]; then
  echo "error: Fire OS did not bind the HOME guard ($GUARD)." >&2
  denied=$(sh_ logcat -d -b all | grep A11yAuthorizer | grep -c "$PKG" || true)
  if [[ ${denied:-0} -gt 0 ]]; then
    echo "  this build only runs accessibility services Amazon trusts (FireOSFwk A11yAuthorizer)," >&2
    echo "  so HOME cannot be taken over here. dumbtv home stays installed as a normal app;" >&2
    echo "  remove it with: adb uninstall $PKG" >&2
  fi
  set_futurity off
  set_guard ""
  exit 1
fi
start_home $PKG/.Home
if installed com.spocky.projengmenu; then
  echo "note: Projectivy is still installed but no longer used — adb uninstall com.spocky.projengmenu"
fi

sleep 2
# The published tv.dumbtv.home build is a different signature, so it cannot take this
# update. Leave it installed and disabled: that keeps its settings and stops it opening
# on HOME. adb shell pm enable --user 0 tv.dumbtv.home turns it back on.
if installed "$LEGACY"; then
  sh_ pm disable-user --user 0 "$LEGACY" >/dev/null 2>&1 || true
  echo "note: $LEGACY is disabled so HOME stays on $PKG. Its settings are still on the TV."
fi
printf 'accessibility: %s\n' "$(sh_ settings get secure enabled_accessibility_services)"
printf 'front window:  %s\n' "$(sh_ dumpsys window | grep -m1 mCurrentFocus | sed 's/.* u0 //; s/}$//')"
