#!/usr/bin/env bash
# Compliance checks for Virgil. Enforces docs/COMPLIANCE.md.
#
# Five checks:
#   1. No banned medical-claim vocabulary in user-facing text (§1),
#      plus banned capability-honesty phrasing (§11).
#   2. No banned Android permissions in the manifest.
#   3. foregroundServiceType must not be "health".
#   4. No banned network / analytics dependencies in gradle.
#   5. No banned network imports in Kotlin source.
#
# An inline marker "compliance-allow: <reason>" on the same line exempts
# that line from the vocabulary check only. Permissions, service type,
# deps, and imports have no inline allow — fix the code.
#
# Exits 0 on pass, 1 on any violation.

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

MANIFEST="android/app/src/main/AndroidManifest.xml"
GRADLE_FILES=(android/app/build.gradle.kts android/build.gradle.kts android/settings.gradle.kts)

red()   { printf '\033[31m%s\033[0m' "$*"; }
bold()  { printf '\033[1m%s\033[0m' "$*"; }
fail=0
section() { printf '\n%s %s\n' "$(bold '===')" "$(bold "$1")"; }
violation() {
    fail=1
    printf '  %s %s\n' "$(red 'FAIL')" "$1"
}
pass_msg() { printf '  ok — %s\n' "$1"; }

# -- Banned vocabulary (COMPLIANCE.md §1) -----------------------------------
# Regex uses word boundaries; case-insensitive. Keep each term tight to
# minimise false positives. Lines containing "compliance-allow:" skip the check.
BANNED_TERMS=(
    'medical device'
    'medical alert'
    'clinically'
    '\bclinical\b'
    '\bdiagnose[sd]?\b'
    '\bdiagnosis\b'
    '\bpatient[s]?\b'
    'vital signs'
    '\bheart rate\b'
    'blood pressure'
    '\bdisease[s]?\b'
    'prescription'
    '\btherapy\b'
    '\btreatment\b'
    'FDA approved'
    'FDA-approved'
    'CE marked'
    'CE-marked'
    'health monitor(ing)?'
    '\bseizure[s]?\b'
    'epileptic'
    'post[- ]op\b'
    'recovery from surgery'
    'recovering from surgery'
    'recovering from illness'
    # §11 — capability honesty. "for help" as Virgil's action implies
    # emergency services. Allowed when it's the user's own SMS voice
    # ("I need help" is fine — no "for help"). Narrative framing can
    # add an inline compliance-allow marker.
    '\bfor help\b'
)

# Files scanned for vocabulary. Everything user-facing: docs, manifesto,
# readme, string resources, and Kotlin source (catches string literals + KDoc).
VOCAB_GLOBS=(
    'MANIFESTO.md'
    'README.md'
    'README'
    'docs/**/*.md'
    'android/app/src/main/res/values*/strings.xml'
    'android/app/src/main/res/values*/arrays.xml'
    'android/app/src/main/java/**/*.kt'
    'android/app/src/main/kotlin/**/*.kt'
)

# Skip files that legitimately contain banned vocabulary:
#   - docs/COMPLIANCE.md quotes every banned word while defining the rule.
#   - docs/research/* is internal competitive / regulatory analysis, not
#     user-facing copy — it must be able to reference competitors by name
#     ("medical alert systems", etc.) without tripping the check.
#   - scripts/check-compliance.sh is the rule engine itself.
VOCAB_SKIP_REGEX='^docs/COMPLIANCE\.md$|^docs/research/|^scripts/check-compliance\.sh$'

section "1. Banned vocabulary (docs/COMPLIANCE.md §1)"
# Collect candidate files via git ls-files, then filter by globs.
mapfile -t ALL_TRACKED < <(git ls-files)
matched_files=()
for f in "${ALL_TRACKED[@]}"; do
    [[ "$f" =~ $VOCAB_SKIP_REGEX ]] && continue
    for pat in "${VOCAB_GLOBS[@]}"; do
        # shellcheck disable=SC2053
        if [[ "$f" == $pat ]]; then
            matched_files+=("$f")
            break
        fi
    done
done

vocab_fail=0
for term in "${BANNED_TERMS[@]}"; do
    # grep -inE -> case-insensitive, line numbers, extended regex.
    # -H forces filename display even when only one file is given.
    while IFS= read -r hit; do
        [[ -z "$hit" ]] && continue
        # Skip lines with the inline allow marker.
        if [[ "$hit" == *"compliance-allow:"* ]]; then
            # Still require a reason after the colon.
            if [[ "$hit" =~ compliance-allow:[[:space:]]*$ ]]; then
                violation "empty compliance-allow marker (reason required) — $hit"
                vocab_fail=1
            fi
            continue
        fi
        violation "banned term /$term/ — $hit"
        vocab_fail=1
    done < <(
        if (( ${#matched_files[@]} > 0 )); then
            grep -HinE "$term" "${matched_files[@]}" 2>/dev/null || true
        fi
    )
done
(( vocab_fail == 0 )) && pass_msg "no banned medical vocabulary in user-facing files"

# -- Banned permissions (COMPLIANCE.md §3) ----------------------------------
BANNED_PERMS=(
    'android.permission.INTERNET'
    'android.permission.ACCESS_NETWORK_STATE'
    'android.permission.READ_CONTACTS'
    'android.permission.WRITE_CONTACTS'
    'android.permission.CAMERA'
    'android.permission.RECORD_AUDIO'
    'android.permission.READ_EXTERNAL_STORAGE'
    'android.permission.WRITE_EXTERNAL_STORAGE'
    'android.permission.READ_PHONE_STATE'
    'android.permission.READ_CALL_LOG'
    'android.permission.PROCESS_OUTGOING_CALLS'
    'android.permission.BLUETOOTH'
    'android.permission.BLUETOOTH_CONNECT'
    'android.permission.BLUETOOTH_SCAN'
    'android.permission.BODY_SENSORS'
    'android.permission.ACTIVITY_RECOGNITION'
    'android.permission.FOREGROUND_SERVICE_HEALTH'
    'android.permission.ACCESS_BACKGROUND_LOCATION'
)

section "2. Banned permissions (docs/COMPLIANCE.md §3)"
perm_fail=0
if [[ -f "$MANIFEST" ]]; then
    for perm in "${BANNED_PERMS[@]}"; do
        if grep -q "\"$perm\"" "$MANIFEST"; then
            line=$(grep -n "\"$perm\"" "$MANIFEST" | head -1)
            violation "$MANIFEST: banned permission $perm — line $line"
            perm_fail=1
        fi
    done
else
    violation "manifest not found at $MANIFEST"
    perm_fail=1
fi
(( perm_fail == 0 )) && pass_msg "no banned permissions in AndroidManifest.xml"

# -- foregroundServiceType must not be "health" (COMPLIANCE.md §4) ---------
section "3. foregroundServiceType != \"health\" (docs/COMPLIANCE.md §4)"
fst_fail=0
if [[ -f "$MANIFEST" ]]; then
    while IFS= read -r hit; do
        [[ -z "$hit" ]] && continue
        violation "$MANIFEST: foregroundServiceType=\"health\" — $hit"
        fst_fail=1
    done < <(grep -nE 'foregroundServiceType="health"' "$MANIFEST" || true)
fi
(( fst_fail == 0 )) && pass_msg "no health foregroundServiceType declared"

# -- Banned dependencies (COMPLIANCE.md §2, §5) -----------------------------
# Patterns matched against gradle source. Keep specific so we don't catch
# legit AndroidX artifacts.
BANNED_DEP_PATTERNS=(
    'com\.squareup\.okhttp3'
    'com\.squareup\.retrofit2'
    'io\.ktor:ktor-client'
    'com\.android\.volley'
    'com\.google\.firebase'
    'com\.google\.android\.gms:play-services-ads'
    'com\.google\.android\.gms:play-services-analytics'
    'com\.google\.android\.gms:play-services-auth'
    'io\.sentry'
    'com\.bugsnag'
    'com\.crashlytics'
    'com\.google\.firebase:firebase-crashlytics'
    'com\.amplitude'
    'com\.mixpanel'
    'com\.datadog'
    'com\.newrelic'
    'com\.adjust'
    'com\.appsflyer'
    'io\.branch'
    'com\.segment\.analytics'
)

section "4. Banned dependencies (docs/COMPLIANCE.md §2, §5)"
dep_fail=0
for gf in "${GRADLE_FILES[@]}"; do
    [[ -f "$gf" ]] || continue
    for pat in "${BANNED_DEP_PATTERNS[@]}"; do
        while IFS= read -r hit; do
            [[ -z "$hit" ]] && continue
            violation "$gf: banned dependency /$pat/ — $hit"
            dep_fail=1
        done < <(grep -nE "$pat" "$gf" 2>/dev/null || true)
    done
done
(( dep_fail == 0 )) && pass_msg "no banned dependencies in gradle files"

# -- Banned imports in Kotlin source (COMPLIANCE.md §2) ---------------------
BANNED_IMPORTS=(
    '^import java\.net\.URL$'
    '^import java\.net\.HttpURLConnection'
    '^import javax\.net\.ssl'
    '^import okhttp3\.'
    '^import retrofit2\.'
    '^import io\.ktor\.client'
    '^import com\.google\.firebase\.'
    '^import com\.google\.android\.gms\.ads'
    '^import com\.google\.android\.gms\.analytics'
    '^import io\.sentry\.'
    '^import com\.bugsnag\.'
    '^import com\.amplitude\.'
    '^import com\.mixpanel\.'
)

section "5. Banned network/analytics imports in source (docs/COMPLIANCE.md §2)"
imp_fail=0
mapfile -t KT_FILES < <(git ls-files 'android/app/src/main/*.kt' 'android/app/src/main/**/*.kt')
for f in "${KT_FILES[@]}"; do
    [[ -f "$f" ]] || continue
    for pat in "${BANNED_IMPORTS[@]}"; do
        while IFS= read -r hit; do
            [[ -z "$hit" ]] && continue
            violation "$f: banned import /$pat/ — $hit"
            imp_fail=1
        done < <(grep -nE "$pat" "$f" 2>/dev/null || true)
    done
done
(( imp_fail == 0 )) && pass_msg "no banned imports in Kotlin source"

# -- Summary ---------------------------------------------------------------
echo
if (( fail )); then
    printf '%s %s\n' "$(red 'COMPLIANCE FAILED.')" "See docs/COMPLIANCE.md for the rule behind each failure."
    exit 1
fi
echo "All compliance checks passed."
