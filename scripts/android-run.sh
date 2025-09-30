#!/usr/bin/env bash
set -euo pipefail

echo "🚀 Starting full development environment..."
echo "1. Starting backend services..."
devenv up --detach
sleep 3
echo "2. Building and installing Android app..."
cd mobile && ./gradlew --no-daemon :androidApp:installDebug
echo "3. Launching app..."
adb shell am start -n id.cachet.wallet.android/.MainActivity
echo "✅ Done! Backend running, app installed and launched."
echo "🔗 Backend: http://localhost:8090 (from emulator: http://10.0.2.2:8090)"
