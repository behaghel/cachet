#!/usr/bin/env bash
set -euo pipefail

echo "🧪 Running Android instrumented tests..."
echo "1. Checking emulator connection..."
adb devices | grep device || (echo "❌ No Android emulator detected. Run 'android:emulator' first." && exit 1)
echo "2. Building and running tests..."
cd mobile && ./gradlew --no-daemon :androidApp:connectedAndroidTest
echo "✅ Android tests completed!"
echo "📊 Test results available in mobile/androidApp/build/reports/androidTests/"
