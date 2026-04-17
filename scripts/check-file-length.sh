#!/usr/bin/env bash
# Enforce a maximum line count on tracked source files.
# Keeps files small so AI has full file context and edits stay local.
# Usage: scripts/check-file-length.sh [MAX_LINES]
set -euo pipefail

MAX="${1:-500}"
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

INCLUDE_EXTS='\.(kt|kts|java|xml|sh|py|md|yml|yaml|toml|gradle)$'
EXCLUDE_PATHS='^(android/app/build/|android/\.gradle/|android/\.kotlin/|.*/build/|.*\.min\..*)'

fail=0
while IFS= read -r file; do
    [[ -z "$file" || ! -f "$file" ]] && continue
    [[ "$file" =~ $EXCLUDE_PATHS ]] && continue
    lines=$(wc -l < "$file")
    if (( lines > MAX )); then
        printf '%s: %d lines (limit %d)\n' "$file" "$lines" "$MAX" >&2
        fail=1
    fi
done < <(git ls-files | grep -E "$INCLUDE_EXTS" || true)

if (( fail )); then
    echo "" >&2
    echo "Split oversized files before adding features. Smaller files = better AI context." >&2
    exit 1
fi
