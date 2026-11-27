#!/usr/bin/env bash
# Full test on a running emulator: sends real texts with "adb emu sms send", checks in the phone's message
# store which ones Hush removed, drives every screen (screenshots + UI dumps in tests/ui/out) and fails if a
# step does not work, texts overlap, or the app crashed.
set -uo pipefail
API="${1:-0}"
cd "$(dirname "$0")"
OUT="$PWD/out"; rm -rf "$OUT"; mkdir -p "$OUT"
PKG=io.github.samaliyan.hush
APK=../../app/build/outputs/apk/release/app-release.apk
FAILS=0
log() { echo "[ui] $*" | tee -a "$OUT/report.txt"; }
fail() { log "FAIL: $*"; FAILS=$((FAILS+1)); }

dump() {
  timeout 30 adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; timeout 15 adb exec-out cat /sdcard/ui.xml > "$OUT/.ui.xml"
  if grep -q "t responding" "$OUT/.ui.xml" 2>/dev/null; then
    local w; w=$(python3 find_node.py "$OUT/.ui.xml" "Wait")
    [ -n "$w" ] && { echo "[ui] (closed a system 'not responding' dialog)" >> "$OUT/report.txt"; adb shell input tap $w; sleep 2; }
    timeout 30 adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; timeout 15 adb exec-out cat /sdcard/ui.xml > "$OUT/.ui.xml"
  fi
}
shot() {
  sleep "${2:-1.2}"
  adb exec-out screencap -p > "$OUT/$1.png"
  dump; cp "$OUT/.ui.xml" "$OUT/$1.xml"
  python3 check_layout.py "$OUT/$1.xml" "$PKG" >> "$OUT/report.txt" || fail "layout problem on $1"
  log "shot $1"
}
nfind() { dump; python3 find_node.py "$OUT/.ui.xml" "$1" "${2:-}"; }
tap() {
  local p; p=$(nfind "$1" "${2:-}")
  if [ -z "$p" ]; then fail "cannot find '$1'"; return 1; fi
  adb shell input tap $p; sleep 1; return 0
}
expect() { [ -n "$(nfind "$1" "${2:-}")" ] && log "ok: sees '$1'" || fail "expected '$1' on screen"; }
wait_for() {
  local t="${3:-15}"
  for i in $(seq 1 "$t"); do [ -n "$(nfind "$1" "${2:-}")" ] && { log "ok: sees '$1'"; return 0; }; sleep 1; done
  fail "expected '$1' within ${t}s"; return 1
}
back() { adb shell input keyevent KEYCODE_BACK; sleep 1; }
open_app() { adb shell am start -f 0x14000000 -n "$PKG/.MainActivity" > /dev/null; sleep 2.5; }
scroll_to() {
  for i in $(seq 1 12); do
    [ -n "$(nfind "$1" "${2:-}")" ] && return 0
    adb shell input swipe 540 1300 540 600 400; sleep 0.6
  done
  fail "could not scroll to '$1'"; return 1
}
scroll_top() { for i in 1 2 3 4; do adb shell input swipe 540 700 540 1800 200; done; sleep 0.5; }

# --- the phone's message store, read with full rights through adb
inbox() { adb shell content query --uri content://sms/inbox --projection address:body 2>/dev/null; }
send() { adb emu sms send "$1" "$2" > /dev/null; log "sent from $1: $2"; }
# in_inbox TEXT SECONDS → waits until the text is stored by the messaging app
in_inbox() {
  for i in $(seq 1 "${2:-20}"); do inbox | grep -qF -- "$1" && return 0; sleep 1; done; return 1
}
# gone TEXT SECONDS → waits until the text is no longer in the store
gone() {
  for i in $(seq 1 "${2:-20}"); do inbox | grep -qF -- "$1" || return 0; sleep 1; done; return 1
}
expect_removed() { gone "$1" "${2:-20}" && log "ok: removed '$1'" || fail "'$1' was not removed"; }
# expect_kept TEXT [SECONDS] → the text arrives (within SECONDS, default 15) and is still there 3 s later
expect_kept() {
  in_inbox "$1" "${2:-15}" || { fail "'$1' is missing (should be kept)"; return; }
  sleep 3
  inbox | grep -qF -- "$1" && log "ok: kept '$1'" || fail "'$1' was removed (should be kept)"
}

log "API $API"
adb install -r "$APK" || { echo "install failed"; exit 1; }
adb logcat -c
SMSAPP=$(adb shell cmd role get-role-holders android.app.role.SMS | tr -d '\r')
log "default messaging app: ${SMSAPP:-none}"
[ -n "$SMSAPP" ] || fail "the emulator has no default messaging app"
# the messaging app may show notifications (Android 13+ asks for it), so hiding them can be checked
adb shell pm grant "$SMSAPP" android.permission.POST_NOTIFICATIONS 2>/dev/null
# open the messaging app once, so it finishes its own first start
adb shell monkey -p "$SMSAPP" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1; sleep 4
adb shell input keyevent KEYCODE_HOME

# 1. An advertising text that is on the phone before Hush is set up
send 20005555 "Old sale before setup"
# a fresh emulator's messaging app can be slow on its very first text: wait, and send once more if needed
if ! in_inbox "Old sale before setup" 40; then send 20005555 "Old sale before setup"; fi
in_inbox "Old sale before setup" 40 && log "ok: stored by the messaging app" || fail "the messaging app did not store the text"

# 2. First start: setup needed
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null; sleep 3
shot 01_first_start
expect "Setup needed"
expect "Hush"
adb shell pm grant $PKG android.permission.RECEIVE_SMS
adb shell pm grant $PKG android.permission.READ_SMS
open_app
shot 02_sms_allowed
expect "Setup needed"            # removing is still missing
expect_kept "Old sale before setup"

# 3. Permission to remove: the screen notices the command by itself
tap "Permission to remove"
shot 03_setup_page
expect "Not given yet"
scroll_to "Send to computer"
shot 03b_setup_page_bottom 0.5
scroll_top
adb shell appops set $PKG WRITE_SMS allow
wait_for "Done" "" 10
shot 04_setup_done
back
shot 05_main_active
expect "Active"

# 4. Clean up old texts
scroll_to "Clean up old texts"
tap "Clean up old texts"
wait_for "advertising text found" "^[0-9]+ advertising texts? found$" 20
shot 06_clean_list
expect "Old sale before setup"
tap "Remove" "^Remove [0-9]+ texts?$"
wait_for "Removed" "^Removed [0-9]+ texts?\.$" 20
shot 07_cleaned
expect_removed "Old sale before setup" 5
back

# 5. Texts arriving now
send 30001234 "Big sale today only"
send 10004455 "Your verification code is 48213"
send +989121234567 "Hi see you at 5"
send +989351112222 "حراج ویژه یلدا لغو11"
send 5000123 "Bank: bardasht: 1,000,000 Mande: 5,000,000"
expect_removed "Big sale today only" 25
expect_kept "Your verification code is 48213"
expect_kept "Hi see you at 5"
expect_kept "Bank: bardasht"
if inbox | grep -qF "لغو11"; then
  expect_removed "لغو11" 15
else
  # either removed already, or the emulator could not send a Persian text: tell them apart in the Removed list
  log "(Persian text not in the store; checked in Removed texts below)"
fi

# 6. Removed texts: listed, and one is restored
open_app
scroll_to "Removed texts"
tap "Removed texts"
shot 08_removed
expect "Big sale today only"
expect "Old sale before setup"
[ -n "$(nfind "" "لغو11")" ] && log "ok: Persian advertising text removed and listed" || fail "Persian advertising text not in Removed texts"
rows() { inbox | grep -c "^Row: "; }
N1=$(rows)
tap "Restore"
shot 09_restore_dialog
expect "Restore this text?"
tap "Restore" "(?i)^restore$"
sleep 3
shot 10_after_restore
N2=$(rows)
[ "$N2" -eq $((N1 + 1)) ] && log "ok: restored text is back in the store ($N1 → $N2)" || fail "restored text is not in the store ($N1 → $N2)"
# a restored text is not removed again by the next pass
send +989121234567 "Second hello"
expect_kept "Second hello"
N3=$(rows)
[ "$N3" -eq $((N2 + 1)) ] && log "ok: restored text stays" || fail "restored text was removed again ($N2 → $N3)"
back

# 7. Rules: add a block word through the screen, and try a text
scroll_to "Rules"
tap "Rules"
shot 11_rules
tap "Add a number or name"
adb shell input text "BadSender"
adb shell input keyevent KEYCODE_ENTER; sleep 1
tap "Add a word"
adb shell input text "lottery"
adb shell input keyevent KEYCODE_ENTER; sleep 1
shot 12_rules_added
expect "lottery"
expect "BadSender"
scroll_to "Sender (number or name)"
tap "Sender (number or name)"
adb shell input text "30001234"
adb shell input keyevent KEYCODE_BACK; sleep 0.8      # closes the keyboard
tap "Text"
adb shell input text "Hello"
adb shell input keyevent KEYCODE_BACK; sleep 0.5      # closes the keyboard
shot 13_rules_try
expect "Removed  ·  Sent from an advertising line"
back
send +989121112233 "You won the lottery today"
expect_removed "You won the lottery today" 25

# 8. Notification access: the notification of a removed text is taken away
adb shell cmd notification allow_listener "$PKG/$PKG.NotifListener"
sleep 2
open_app
shot 14_main_all_done
send 30007777 "Flash offer number two"
send +989121234567 "Real message check"
expect_removed "Flash offer number two" 25
sleep 4
adb shell dumpsys notification --noredact > "$OUT/notifications.txt"
if adb logcat -d | grep -q "BugleNotifications: Notification posted"; then
  log "ok: the messaging app shows notifications"
  grep -qF "Flash offer number two" "$OUT/notifications.txt" && fail "notification of a removed text is still shown" || log "ok: notification of the removed text is gone"
  adb logcat -d -s Hush:I | grep -q "notification cancelled" && log "ok: Hush took the notification away itself" || fail "Hush did not cancel any notification"
  grep -qF "Real message check" "$OUT/notifications.txt" && log "ok: the real text's notification stays" || fail "the real text's notification is missing"
else
  log "(the messaging app shows no notifications on this emulator: notification removal not checked)"
fi

# 9. Settings: sound only for real texts (dialog), pause, dark mode
open_app
scroll_to "Sound only for real texts"
tap "Sound only for real texts"
shot 15_sound_dialog
expect "Open its settings" "(?i)^open its settings$"
tap "Later" "(?i)^later$"
send +989121234567 "Real message with Hush sound"
expect_kept "Real message with Hush sound"
scroll_to "Remove advertising texts"
tap "Remove advertising texts"
scroll_top
shot 16_paused
expect "Paused"
send 30008888 "Sale while paused"
expect_kept "Sale while paused" 20
scroll_to "Remove advertising texts"
tap "Remove advertising texts"
scroll_top
adb shell cmd uimode night yes; sleep 2
open_app
shot 17_dark_main
adb shell cmd uimode night no

# 10. Restart of the app process: the store watcher works again after the app is updated
adb install -r "$APK" > /dev/null
sleep 3
send 30009999 "Sale after update"
expect_removed "Sale after update" 25

# --- crashes
adb logcat -d > "$OUT/logcat.txt"
if grep -E "FATAL EXCEPTION|AndroidRuntime: Process: $PKG" "$OUT/logcat.txt" | grep -q .; then
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80 | tee -a "$OUT/report.txt"
  fail "the app crashed"
fi
inbox > "$OUT/inbox_at_end.txt"
log "done: $FAILS failure(s)"
[ "$FAILS" -eq 0 ]
