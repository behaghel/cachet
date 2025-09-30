#!/usr/bin/env bash
set -euo pipefail

echo "Uninstalling app from device/emulator..."
if [ -z "$JAVA_HOME" ] && ! command -v java &> /dev/null; then
  echo "❌ Error: Java not found. Make sure you're running with DEVENV_ENABLE_ANDROID=1"
  echo "   Usage: DEVENV_ENABLE_ANDROID=1 devenv shell -- android:uninstall"
  exit 1
fi
# Try using gradle uninstallDebug task first
if cd mobile && ./gradlew --no-daemon :androidApp:uninstallDebug 2>/dev/null; then
  echo "✅ App uninstalled via Gradle"
else
  # Fallback to adb uninstall if gradle task doesn't work
  echo "Gradle uninstall failed, trying adb..."
  PACKAGE_ID=$(grep 'applicationId' mobile/androidApp/build.gradle.kts | sed 's/.*applicationId = "//' | sed 's/".*//')
  if [ -n "$PACKAGE_ID" ]; then
    adb uninstall "$PACKAGE_ID"
    echo "✅ App uninstalled via adb: $PACKAGE_ID"
  else
    echo "❌ Could not determine package ID from build.gradle.kts"
    exit 1
  fi
fi
