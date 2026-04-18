#!/usr/bin/env bash
# Grab a screenshot from the connected device into ./screenshots/<name>.png
# Usage: ./scripts/screenshot.sh <name>
set -euo pipefail

name="${1:-virgil-$(date +%Y%m%d-%H%M%S)}"
out_dir="$(cd "$(dirname "$0")/.." && pwd)/screenshots"
mkdir -p "$out_dir"

remote="/sdcard/virgil-shot.png"
adb shell screencap -p "$remote"
adb pull "$remote" "$out_dir/${name}.png" >/dev/null
adb shell rm "$remote"

echo "saved: $out_dir/${name}.png"
