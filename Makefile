# Makefile for Virgil
# Run with BUILD=prod for release build, or BUILD=debug (default) for debug.

APP_ID := com.virgil.app/.ui.MainActivity
GRADLEW := cd android && ./gradlew

# Build variant: debug (default) or prod (release)
# Device IDs
SAMSUNG_ID := 616ecbcf
PIXEL4A_ID := 0B201JECB13875
PIXEL9A_ID := 59101JEBF02652

BUILD ?= debug
ifeq ($(BUILD),prod)
  GRADLE_VARIANT := Release
else
  BUILD := debug
  GRADLE_VARIANT := Debug
endif

.PHONY: all
all: help

# === Build ===

.PHONY: assemble
assemble:
	$(GRADLEW) assemble$(GRADLE_VARIANT)

.PHONY: install
install:
	@if adb devices | grep -q 'device$$'; then \
		$(GRADLEW) install$(GRADLE_VARIANT); \
	else \
		echo "No device connected. Building APK only."; \
		$(GRADLEW) assemble$(GRADLE_VARIANT); \
	fi

.PHONY: check
check:
	$(GRADLEW) check

.PHONY: lint
lint:
	$(GRADLEW) lint$(GRADLE_VARIANT)

.PHONY: test
test:
	$(GRADLEW) test$(GRADLE_VARIANT)UnitTest

.PHONY: clean
clean:
	$(GRADLEW) clean

# === Device ===

.PHONY: devices
devices:
	adb devices

.PHONY: run
run: install
	@adb shell am start -n $(APP_ID)

.PHONY: run-pixel4a
run-pixel4a: install
	adb -s $(PIXEL4A_ID) shell am start -n $(APP_ID)

.PHONY: run-pixel9a
run-pixel9a: install
	adb -s $(PIXEL9A_ID) shell am start -n $(APP_ID)

.PHONY: run-samsung
run-samsung: install
	adb -s $(SAMSUNG_ID) shell am start -n $(APP_ID)

.PHONY: logs
logs:
	@trap 'cd android && ./gradlew --stop' EXIT INT TERM; adb logcat --pid=$$(adb shell pidof com.virgil.app)

.PHONY: clear-data
clear-data:
	adb shell pm clear com.virgil.app

# === Debug / fall-replay (debug builds only) ===

# Replay a canned accelerometer trace through the running FallDetectionService.
# TRACE=fall|sit_hard|walk|drop_on_couch (see DebugTraces.kt). Default: fall.
TRACE ?= fall
TRACES_DIR := /sdcard/Android/data/com.virgil.app/files/traces
LOCAL_TRACES := traces

.PHONY: trigger-fall
trigger-fall:
	adb shell am broadcast \
		-a com.virgil.app.DEBUG_TRIGGER_FALL \
		-n com.virgil.app/.debug.DebugFallTriggerReceiver \
		--es trace $(TRACE)

# Replay a previously recorded CSV (sitting on device) through the service.
# FILE=trace-YYYYMMDD-HHMMSS[-label].csv (name only, looked up in TRACES_DIR).
.PHONY: trigger-fall-file
trigger-fall-file:
	@[ -n "$(FILE)" ] || { echo "usage: make trigger-fall-file FILE=trace-....csv"; exit 1; }
	adb shell am broadcast \
		-a com.virgil.app.DEBUG_TRIGGER_FALL \
		-n com.virgil.app/.debug.DebugFallTriggerReceiver \
		--es file $(FILE)

# Start recording raw accelerometer to a CSV on the device.
# LABEL=human-readable tag (optional), appended to filename.
.PHONY: start-record
start-record:
	adb shell am broadcast \
		-a com.virgil.app.DEBUG_RECORD_START \
		-n com.virgil.app/.debug.SensorRecorderReceiver \
		$(if $(LABEL),--es label $(LABEL),)

.PHONY: stop-record
stop-record:
	adb shell am broadcast \
		-a com.virgil.app.DEBUG_RECORD_STOP \
		-n com.virgil.app/.debug.SensorRecorderReceiver

.PHONY: list-traces
list-traces:
	@adb shell ls -1 $(TRACES_DIR) 2>/dev/null || echo "(no traces yet)"

.PHONY: pull-traces
pull-traces:
	@mkdir -p $(LOCAL_TRACES)
	adb pull $(TRACES_DIR) $(LOCAL_TRACES)/ || true
	@echo "Pulled to ./$(LOCAL_TRACES)/"

# === Quality ===

.PHONY: quality
quality:
	@scripts/code-quality-check.sh full

.PHONY: quality-fast
quality-fast:
	@scripts/code-quality-check.sh fast

.PHONY: compliance
compliance:
	@scripts/check-compliance.sh

.PHONY: hooks-install
hooks-install:
	@git config core.hooksPath .githooks
	@echo "Hooks installed: .githooks/ is now the hooks path."

# === Maintenance ===

.PHONY: clean-daemons
clean-daemons:
	$(GRADLEW) --stop

# === Help ===

.PHONY: help
help:
	@echo "Virgil — Your silent guardian"
	@echo ""
	@echo "Build:"
	@echo "  make assemble       - Build APK (no device needed). BUILD=prod for release."
	@echo "  make install        - Build + install on connected device. BUILD=prod for release."
	@echo "  make check          - Run all Gradle checks (lint + tests)."
	@echo "  make lint           - Run Android lint."
	@echo "  make test           - Run unit tests."
	@echo "  make clean          - Clean build outputs."
	@echo ""
	@echo "Device:"
	@echo "  make run            - Install and launch on connected device."
	@echo "  make run-pixel4a    - Install and run on Pixel 4a (ID: $(PIXEL4A_ID))."
	@echo "  make run-pixel9a    - Install and run on Pixel 9a (ID: $(PIXEL9A_ID))."
	@echo "  make run-samsung    - Install and run on Samsung (ID: $(SAMSUNG_ID))."
	@echo "  make devices        - List connected ADB devices."
	@echo "  make logs           - Tail app logs (Ctrl+C to stop)."
	@echo "  make clear-data     - Wipe app data on device."
	@echo ""
	@echo "Debug (debug builds only):"
	@echo "  make trigger-fall              - Replay canned trace. TRACE=fall|sit_hard|walk|drop_on_couch."
	@echo "  make trigger-fall-file FILE=x  - Replay a recorded CSV (name only, in device traces dir)."
	@echo "  make start-record [LABEL=tag]  - Start on-device accelerometer recording."
	@echo "  make stop-record               - Stop recording."
	@echo "  make list-traces               - List recorded traces on the device."
	@echo "  make pull-traces               - Pull recorded traces into ./traces/."
	@echo ""
	@echo "Quality:"
	@echo "  make quality        - Full check (file length + compliance + lint + unit tests). Same as CI."
	@echo "  make quality-fast   - Fast check (file length + compliance + compile). Same as pre-commit."
	@echo "  make compliance     - Compliance rules only (see docs/COMPLIANCE.md)."
	@echo "  make hooks-install  - Point git at .githooks/ (one-time per clone)."
	@echo ""
	@echo "Maintenance:"
	@echo "  make clean-daemons  - Stop idle Gradle daemons (frees ~4GB each)."
	@echo ""
	@echo "Build variant: BUILD=debug (default) or BUILD=prod"
