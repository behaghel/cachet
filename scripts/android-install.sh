#!/usr/bin/env bash
set -euo pipefail

echo "Installing app on device/emulator..."

if [ -z "$JAVA_HOME" ] && ! command -v java &> /dev/null; then
  echo "❌ Error: Java not found. Make sure you're running with DEVENV_ENABLE_ANDROID=1"
  echo "   Usage: DEVENV_ENABLE_ANDROID=1 devenv shell -- android:install"
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "❌ jq is required for android:install. Run this inside 'devenv shell'."
  exit 1
fi

CONFIG_PATH="${CACHET_CONFIG_PATH:-config/app-config.json}"
if [ ! -f "$CONFIG_PATH" ]; then
  echo "❌ Unable to locate configuration at $CONFIG_PATH"
  exit 1
fi

DEFAULT_ENV=$(jq -r '.defaultEnvironment // "local"' "$CONFIG_PATH" 2>/dev/null || echo "local")
if [ -z "$DEFAULT_ENV" ] || [ "$DEFAULT_ENV" = "null" ]; then
  DEFAULT_ENV="local"
fi

SELECTED_ENV="${CACHET_ENV:-$DEFAULT_ENV}"
if [ -z "$SELECTED_ENV" ] || [ "$SELECTED_ENV" = "null" ]; then
  SELECTED_ENV="$DEFAULT_ENV"
fi

if [ "$SELECTED_ENV" = "local" ] || [ "$SELECTED_ENV" = "ci" ]; then
  echo "🛠️  Installing debug build for $SELECTED_ENV"
  (
    cd mobile || exit 1
    CACHET_ENV="$SELECTED_ENV" ./gradlew --no-daemon :androidApp:installDebug -PcachetEnv="$SELECTED_ENV"
  )
  exit 0
fi

if ! command -v adb &> /dev/null; then
  echo "❌ Error: adb not found. Ensure Android platform tools are on your PATH."
  exit 1
fi

if ! adb devices | grep -w "device" >/dev/null; then
  echo "❌ No connected device/emulator detected. Connect one and retry."
  exit 1
fi

APK_DIR="mobile/androidApp/build/outputs/apk/release"
APK_PATH=""

if [ -d "$APK_DIR" ]; then
  APK_PATH=$(find "$APK_DIR" -maxdepth 1 -type f -name "*release*.apk" ! -name "*-unsigned.apk" -print -quit 2>/dev/null || true)
  if [ -z "$APK_PATH" ]; then
    APK_PATH=$(find "$APK_DIR" -maxdepth 1 -type f -name "*.apk" -print -quit 2>/dev/null || true)
  fi
fi

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
  echo "❌ Release APK not found in $APK_DIR"
  echo "   Run android:build after switching to the desired environment."
  exit 1
fi

if echo "$APK_PATH" | grep -q -- "-unsigned.apk$"; then
  echo "❌ Release APK at $APK_PATH is unsigned and cannot be installed."
  echo "   Re-run the build with signing credentials (e.g. provide CACHET_ANDROID_SIGNING_KEYSTORE)."
  exit 1
fi

PACKAGE_ID=$(grep 'applicationId' mobile/androidApp/build.gradle.kts | sed 's/.*applicationId = "//' | sed 's/".*//')
if [ -n "$PACKAGE_ID" ]; then
  if adb shell pm list packages | grep -q "$PACKAGE_ID"; then
    echo "ℹ️ Removing existing package $PACKAGE_ID before installing..."
    if ! adb uninstall "$PACKAGE_ID" >/dev/null; then
      echo "⚠️ Failed to uninstall existing $PACKAGE_ID; continuing with install attempt."
    fi
  fi
fi

if adb install -r "$APK_PATH"; then
  echo "✅ Installed release APK for $SELECTED_ENV: $APK_PATH"
else
  echo "❌ Failed to install $APK_PATH"
  exit 1
fi
