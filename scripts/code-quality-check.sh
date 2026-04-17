#!/usr/bin/env bash
# Single source of truth for "did we break anything?".
# Pre-commit and CI both run this — no drift between local and CI.
# Modes:
#   fast   (default, used by pre-commit): file-length + kotlin compile
#   full   (used by CI): file-length + lint + unit tests
set -euo pipefail

MODE="${1:-fast}"
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

step() { printf '\n=== %s ===\n' "$1"; }

step "File length (max 500 lines)"
scripts/check-file-length.sh 500

step "Compliance (docs/COMPLIANCE.md)"
scripts/check-compliance.sh

case "$MODE" in
    fast)
        step "Kotlin compile (debug)"
        (cd android && ./gradlew --no-daemon compileDebugKotlin)
        ;;
    full)
        step "Lint + unit tests"
        (cd android && ./gradlew --no-daemon lintDebug testDebugUnitTest)
        ;;
    *)
        echo "Unknown mode: $MODE (expected: fast | full)" >&2
        exit 2
        ;;
esac

echo ""
echo "All checks passed."
