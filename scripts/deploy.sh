#!/bin/bash

# Script to build and deploy APK for Android Auto testing
# Usage: ./scripts/deploy.sh [debug|release]
# Default: release

set -e

cd "$(dirname "$0")/.."

BUILD_TYPE="${1:-release}"

case "$BUILD_TYPE" in
    debug)
        TASK="assembleDebug"
        APK_PATH="androidApp/build/outputs/apk/debug/androidApp-debug.apk"
        ;;
    release)
        TASK="assembleRelease"
        APK_PATH="androidApp/build/outputs/apk/release/androidApp-release.apk"
        ;;
    *)
        echo "❌ Invalid build type: $BUILD_TYPE"
        echo "Usage: $0 [debug|release]"
        echo "Default: release"
        exit 1
        ;;
esac

echo "🚗 Building $BUILD_TYPE APK for Android Auto..."
./gradlew "$TASK"

if [ ! -f "$APK_PATH" ]; then
    echo "❌ Error: APK not found at $APK_PATH"
    exit 1
fi

echo "📱 Checking for connected devices..."
DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l | tr -d ' ')

if [ "$DEVICES" -eq "0" ]; then
    echo "❌ No Android devices found!"
    echo ""
    echo "Please ensure:"
    echo "  1. Your phone is connected via USB"
    echo "  2. USB debugging is enabled on your phone:"
    echo "     - Settings > About phone > Tap 'Build number' 7 times"
    echo "     - Settings > Developer options > Enable 'USB debugging'"
    echo "  3. You've accepted the USB debugging authorization prompt on your phone"
    echo ""
    exit 1
fi

echo "✅ Device found! Installing APK..."
adb install -r "$APK_PATH"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ App installed successfully!"
    echo ""
    echo "📋 Next steps for Android Auto:"
    echo "  1. On your phone, open the Android Auto app"
    echo "  2. Go to Settings (⋮ menu) > Developer settings"
    echo "  3. Enable 'Unknown sources' (if not already enabled)"
    echo "  4. Make sure 'Developer mode' is enabled (you mentioned you already did this)"
    echo "  5. Connect your phone to your car's Android Auto"
    echo "  6. The app 'gaston' should now appear in Android Auto"
    echo ""
    echo "💡 Tip: If the app doesn't appear, try:"
    echo "  - Restart Android Auto on your phone"
    echo "  - Unplug and reconnect your phone to the car"
    echo "  - Check that the app has necessary permissions in Android Settings"
else
    echo "❌ Installation failed!"
    exit 1
fi
