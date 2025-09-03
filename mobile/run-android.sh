#!/bin/bash

set -e

echo "🏗️  Building Cachet Wallet Android App..."

# Check if Android SDK is available
if [ -z "$ANDROID_HOME" ]; then
    echo "❌ ANDROID_HOME not set. Please install Android SDK."
    echo "📋 Instructions:"
    echo "   1. Install Android Studio or command line tools"
    echo "   2. Set ANDROID_HOME environment variable"
    echo "   3. Add \$ANDROID_HOME/platform-tools to PATH"
    exit 1
fi

# Check if emulator is running
if ! adb devices | grep -q "emulator"; then
    echo "❌ No Android emulator detected."
    echo "📋 To start an emulator:"
    echo "   emulator @YOUR_AVD_NAME"
    echo "   OR start from Android Studio"
    exit 1
fi

echo "✅ Android emulator detected"

# Build and install the app
echo "🔨 Building Android app..."
./gradlew :androidApp:assembleDebug

echo "📱 Installing app on emulator..."
./gradlew :androidApp:installDebug

echo "🚀 Launching Cachet Wallet..."
adb shell am start -n id.cachet.wallet.android/.MainActivity

echo "✅ Done! Cachet Wallet should now be running on the emulator."
echo "🔗 Backend endpoints:"
echo "   - Issuance Gateway: http://localhost:8090"
echo "   - From emulator: http://10.0.2.2:8090"