#!/usr/bin/env bash
set -euo pipefail

echo "Creating Android emulator..."
avdmanager create avd --force --name cachet-emulator --package 'system-images;android-34;google_apis_playstore;x86_64' || true
echo "Starting Android emulator..."
emulator @cachet-emulator -no-audio -no-window &
echo "Waiting for emulator to boot..."
adb wait-for-device
echo "✅ Android emulator ready"
