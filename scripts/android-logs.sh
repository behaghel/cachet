#!/usr/bin/env bash
set -euo pipefail

echo "📱 Streaming Android device/emulator logs..."
echo "📍 Use Ctrl+C to stop log streaming"
echo "🔍 Filtering for Cachet wallet app logs..."
echo ""

# Check if ADB is available
if ! command -v adb &> /dev/null; then
  echo "❌ Error: adb not found. Make sure Android SDK is installed."
  exit 1
fi

# Get connected devices and select the first one
DEVICES=$(adb devices | grep -E '\tdevice$' | cut -f1)
if [ -z "$DEVICES" ]; then
  echo "❌ No Android device/emulator detected."
  echo "   Make sure your device is connected or emulator is running."
  adb devices
  exit 1
fi

# Get the first device
FIRST_DEVICE=$(echo "$DEVICES" | head -n1)
echo "🔗 Connected devices:"
adb devices
echo ""
echo "📱 Using device: $FIRST_DEVICE"
echo ""

# Clear old logs and start streaming from the selected device
adb -s "$FIRST_DEVICE" logcat -c  # Clear existing logs

# Stream logs with better filtering for mobile apps
echo "🔍 Starting log stream (filtered for Cachet app)..."
echo "   Monitoring: App crashes, network errors, Veriff integration, OkHttp requests"
echo ""

adb -s "$FIRST_DEVICE" logcat \
  -s "AndroidRuntime:E" \
  -s "System.err:*" \
  -s "CachetWallet:*" \
  -s "VeriffIntegration:*" \
  -s "OkHttp:*" \
  -s "NetworkSecurityConfig:*" \
  -s "id.cachet.wallet:*" \
  -s "*:E" \
  -s "*:W" \
| while read line; do
  # Highlight important patterns
  if echo "$line" | grep -qiE "(crash|exception|error|failed|veriff|cachet)"; then
    echo "🔴 $line"
  elif echo "$line" | grep -qiE "(warn|warning)"; then
    echo "🟡 $line" 
  else
    echo "ℹ️  $line"
  fi
done
