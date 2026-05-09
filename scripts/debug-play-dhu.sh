#!/usr/bin/env bash
#
# Debug gaston with Android Auto Desktop Head Unit (DHU) and a USB-connected device.
# Performs checks, builds/installs the debug variant, starts DHU, then instructs how to attach the debugger.
#
# Usage:
#   ./scripts/debug-play-dhu.sh              # USB mode (default): build, install, run DHU
#   ./scripts/debug-play-dhu.sh --adb         # Use ADB tunneling instead of USB accessory
#   ./scripts/debug-play-dhu.sh --no-build    # Skip build/install (use existing debug build on device)
#   ./scripts/debug-play-dhu.sh --no-dhu      # Only run checks + build/install; do not start DHU
#
# Prerequisites (script will check):
#   - ANDROID_HOME or ANDROID_SDK_ROOT set (or default ~/Library/Android/sdk)
#   - adb in PATH
#   - Exactly one USB device connected
#   - Android Auto Desktop Head Unit installed via SDK Manager
#   - On phone: USB debugging on, Android Auto developer mode + Unknown sources enabled
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VARIANT="debug"
MODULE="androidApp"

# Colors for output (disable if not a TTY)
if [[ -t 1 ]]; then
  RED='\033[0;31m'
  GREEN='\033[0;32m'
  YELLOW='\033[1;33m'
  CYAN='\033[0;36m'
  BOLD='\033[1m'
  NC='\033[0m'
else
  RED= GREEN= YELLOW= CYAN= BOLD= NC=
fi

print_header()  { echo -e "\n${CYAN}${BOLD}== $*${NC}"; }
print_ok()      { echo -e "  ${GREEN}✓${NC} $*"; }
print_fail()    { echo -e "  ${RED}✗${NC} $*"; }
print_warn()    { echo -e "  ${YELLOW}!${NC} $*"; }
print_info()    { echo -e "  $*"; }

DO_BUILD=true
DO_DHU=true
USE_ADB=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --adb)      USE_ADB=true;  shift ;;
    --no-build) DO_BUILD=false; shift ;;
    --no-dhu)   DO_DHU=false;  shift ;;
    -h|--help)
      head -24 "$0" | tail -20
      exit 0
      ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

cd "$PROJECT_ROOT"

# -----------------------------------------------------------------------------
# 1. Environment and tools
# -----------------------------------------------------------------------------
print_header "1. Environment & tools"

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [[ ! -d "$SDK_ROOT" ]]; then
  print_fail "Android SDK not found."
  print_info "Set ANDROID_HOME or ANDROID_SDK_ROOT, or install SDK to $HOME/Library/Android/sdk"
  print_info "Android Studio: Settings → Appearance & Behavior → System Settings → Android SDK"
  exit 1
fi
print_ok "SDK root: $SDK_ROOT"

if ! command -v adb &>/dev/null; then
  print_fail "adb not found in PATH."
  print_info "Add SDK platform-tools to PATH, e.g. export PATH=\"\$ANDROID_HOME/platform-tools:\$PATH\""
  exit 1
fi
print_ok "adb: $(which adb)"

# -----------------------------------------------------------------------------
# 2. DHU (Desktop Head Unit)
# -----------------------------------------------------------------------------
DHU_DIR="${SDK_ROOT}/extras/google/auto"
DHU_BIN="${DHU_DIR}/desktop-head-unit"
if [[ ! -x "$DHU_BIN" ]]; then
  if [[ "$DO_DHU" == true ]]; then
    print_fail "DHU not found or not executable: $DHU_BIN"
    print_info "Install: Android Studio → SDK Manager → SDK Tools → Android Auto Desktop Head Unit Emulator"
    exit 1
  else
    print_warn "DHU not found (ignored because --no-dhu)."
  fi
else
  print_ok "DHU: $DHU_BIN"
fi

# -----------------------------------------------------------------------------
# 3. USB device (exactly one)
# -----------------------------------------------------------------------------
print_header "2. USB device"

# Only count lines ending with "device" (ready), not "unauthorized" or "offline"
DEVICE_LINE=$(adb devices | grep -E '^[[:graph:]]+[[:space:]]+device$' || true)
DEVICE_COUNT=$(echo "$DEVICE_LINE" | grep -c . 2>/dev/null || echo 0)

if [[ "$DEVICE_COUNT" -eq 0 ]]; then
  print_fail "No device connected via USB."
  echo ""
  print_info "Do this:"
  print_info "  1. Connect the phone with a USB cable."
  print_info "  2. On phone: Settings → Developer options → USB debugging = ON."
  print_info "  3. Accept the 'Allow USB debugging?' prompt on the phone."
  print_info "  4. Run: adb devices"
  echo ""
  exit 1
fi

if [[ "$DEVICE_COUNT" -gt 1 ]]; then
  print_fail "More than one device connected. Unplug others or use: adb -s <serial> ..."
  adb devices -l
  exit 1
fi

DEVICE_SERIAL=$(echo "$DEVICE_LINE" | head -1 | awk '{print $1}')
print_ok "Single device: $DEVICE_SERIAL"

# -----------------------------------------------------------------------------
# 4. User checklist (Android Auto on phone)
# -----------------------------------------------------------------------------
print_header "3. Android Auto on phone (please confirm)"

echo -e "  On your ${BOLD}phone${NC}, ensure:"
print_info "  • Android Auto is installed and updated."
print_info "  • Developer mode: Android Auto → About → tap Version ~10× → Developer settings ON."
print_info "  • Developer settings → 'Unknown sources' / 'Add new cars' = ON."
print_info "  • Phone connected via USB, screen unlocked (for USB accessory mode)."
if [[ "$USE_ADB" == true ]]; then
  print_info "  • Developer settings → 'Start head unit server' = ON (for ADB tunneling)."
fi
echo ""

# -----------------------------------------------------------------------------
# 5. Build and install debug
# -----------------------------------------------------------------------------
if [[ "$DO_BUILD" == true ]]; then
  print_header "4. Build & install ($VARIANT)"

  print_info "Running: ./gradlew :${MODULE}:installFullDebug ..."
  if ! ./gradlew ":${MODULE}:installFullDebug" --no-daemon -q; then
    print_fail "Build or install failed."
    exit 1
  fi
  print_ok "Installed $VARIANT on device."
else
  print_header "4. Build & install (skipped with --no-build)"
  print_warn "Ensure debug build is already installed on the device."
fi

# -----------------------------------------------------------------------------
# 6. Start DHU or print attach instructions
# -----------------------------------------------------------------------------
if [[ "$DO_DHU" == false ]]; then
  print_header "5. DHU (skipped with --no-dhu)"
  echo ""
  echo -e "${BOLD}Next steps:${NC}"
  print_info "1. Start DHU manually: ./scripts/run-dhu.sh"
  print_info "2. On phone: open gaston once so Android Auto sees it."
  print_info "3. In DHU window: start Android Auto session."
  print_info "4. Android Studio: Run → Attach Debugger to Android Process → select gaston."
  echo ""
  exit 0
fi

print_header "5. Start DHU"

echo ""
echo -e "${BOLD}After DHU is running:${NC}"
print_info "1. On phone: open gaston once so Android Auto sees it."
print_info "2. In DHU window: start Android Auto session; gaston should appear."
print_info "3. Android Studio: Run → Attach Debugger to Android Process → select gaston."
print_info "4. Set breakpoints in VoiceAppService, VoiceSession, MainScreen, etc."
echo ""

CONFIG="config/default_720p.ini"
if [[ "$USE_ADB" == true ]]; then
  print_info "ADB tunneling mode. On phone: Start head unit server = ON."
  if adb forward tcp:5277 tcp:5277 2>/dev/null; then
    print_ok "adb forward tcp:5277 tcp:5277 done."
  else
    print_warn "adb forward failed. Try: adb kill-server && adb start-server && adb forward tcp:5277 tcp:5277"
  fi
  print_info "Launching DHU with config: $CONFIG"
  cd "$DHU_DIR" && exec "$DHU_BIN" -c "$CONFIG"
else
  print_info "USB accessory mode. Keep phone connected and screen unlocked."
  print_info "Launching DHU: $DHU_BIN --usb -c $CONFIG"
  cd "$DHU_DIR" && exec "$DHU_BIN" --usb -c "$CONFIG"
fi
