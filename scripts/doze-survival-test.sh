#!/usr/bin/env bash
# Stock-Android Doze survival test for Virgil's always-on service.
#
# The OEM-kill question (issue #1) needs a Xiaomi/Samsung in hand. But the
# *baseline* — does the foreground service survive plain AOSP Doze — can be
# measured on ANY connected device (a Pixel, an old phone, an emulator) right
# now. If Virgil dies under forced Doze on stock, that's a bug no OEM is to
# blame for; fix it before chasing MIUI.
#
# Requires a DEBUG build installed (uses `run-as` to read the private heartbeat
# log). For a release build, read the in-app readout instead: Settings →
# Testing → Service heartbeat.
#
# Usage: scripts/doze-survival-test.sh [--cycles N] [--idle SECS] [--maint SECS]
set -euo pipefail

PKG="com.virgil.app"
CYCLES=12          # ~ a few hours compressed; raise for a longer soak
IDLE_SECS=900      # time held in deep idle per cycle
MAINT_SECS=60      # maintenance-window length per cycle
GAP_THRESHOLD_MS=$((5 * 60 * 1000))

while [[ $# -gt 0 ]]; do
    case "$1" in
        --cycles) CYCLES="$2"; shift 2 ;;
        --idle)   IDLE_SECS="$2"; shift 2 ;;
        --maint)  MAINT_SECS="$2"; shift 2 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

if ! command -v adb >/dev/null 2>&1; then
    echo "adb not found on PATH" >&2; exit 1
fi
if [[ -z "$(adb devices | sed '1d' | grep -w device || true)" ]]; then
    echo "no device connected (adb devices shows none)" >&2; exit 1
fi
if [[ -z "$(adb shell pm list packages "$PKG" | tr -d '\r' || true)" ]]; then
    echo "$PKG not installed on device" >&2; exit 1
fi

cleanup() {
    echo "--- restoring normal power state ---"
    adb shell dumpsys deviceidle unforce >/dev/null 2>&1 || true
    adb shell dumpsys battery reset >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "=== Virgil Doze survival baseline: $CYCLES cycles, idle ${IDLE_SECS}s / maint ${MAINT_SECS}s ==="
echo "Make sure fall detection is ON and the foreground notification is up before continuing."
adb shell dumpsys battery unplug >/dev/null
adb shell dumpsys deviceidle enable >/dev/null

for ((c = 1; c <= CYCLES; c++)); do
    echo "[cycle $c/$CYCLES] forcing deep idle for ${IDLE_SECS}s"
    adb shell dumpsys deviceidle force-idle >/dev/null
    sleep "$IDLE_SECS"
    echo "[cycle $c/$CYCLES] maintenance window ${MAINT_SECS}s"
    adb shell dumpsys deviceidle unforce >/dev/null
    sleep "$MAINT_SECS"
done

echo "=== reading heartbeat log ==="
LOG="$(adb shell run-as "$PKG" cat files/heartbeat.log 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$LOG" ]]; then
    echo "heartbeat.log is empty or unreadable (release build? then read the in-app readout)." >&2
    exit 1
fi

# Gap analysis mirrors ServiceHeartbeat.findGaps: a forward jump in the
# elapsed-realtime column (col 2) beyond the threshold is a window the service
# was not beating; a decrease is a reboot, not a gap. A 'create' tag right
# after a gap means it was killed and START_STICKY revived it.
echo "$LOG" | awk -F',' -v thr="$GAP_THRESHOLD_MS" '
    { tag[NR] = $3; e = $2 + 0 }
    NR > 1 && e >= prev && (e - prev) > thr {
        gaps++
        printf "  GAP %.1f min  (last tag before: %s, first after: %s)\n", (e - prev)/60000, prevtag, $3
    }
    { prev = e; prevtag = $3 }
    END {
        printf "beats=%d  gaps_over_%dmin=%d\n", NR, thr/60000, gaps + 0
        if ((gaps + 0) == 0) print "PASS: service beat through every Doze cycle on this device."
        else print "FAIL: service stopped beating during Doze — investigate before trusting it."
    }'
