#!/bin/bash

set -e

echo "🚀 Cachet Wallet - Complete Development Test"
echo "=========================================="
echo

# Check if backend is running
echo "🔍 Checking backend status..."
if curl -f -s http://localhost:8090/healthz > /dev/null; then
    echo "✅ Backend issuance gateway running on http://localhost:8090"
else
    echo "❌ Backend not running. Start with:"
    echo "   cd /home/hubertbehaghel/tmp/cachet/services/issuance-gateway && go run ."
    exit 1
fi

# Check if emulator is running
echo "🔍 Checking emulator status..."
if adb devices | grep -q "emulator"; then
    echo "✅ Android emulator detected"
    DEVICE=$(adb devices | grep emulator | cut -f1)
    echo "   Device: $DEVICE"
else
    echo "❌ No Android emulator detected"
    echo "📋 To start emulator:"
    echo "   1. Open Android Studio"
    echo "   2. Start AVD Manager"
    echo "   3. Create/start emulator with API 34"
    echo "   OR use command line:"
    echo "   emulator @YOUR_AVD_NAME"
    exit 1
fi

echo
echo "🔨 Building and installing Cachet Wallet..."

# Build the app
if ./gradlew :androidApp:assembleDebug; then
    echo "✅ App built successfully"
else
    echo "❌ Build failed"
    exit 1
fi

# Install the app
if ./gradlew :androidApp:installDebug; then
    echo "✅ App installed on emulator"
else
    echo "❌ Installation failed"
    exit 1
fi

echo
echo "🚀 Launching Cachet Wallet..."

# Launch the app
adb shell am start -n id.cachet.wallet.android/.MainActivity

echo
echo "✅ Done! Cachet Wallet should now be running on the emulator."
echo
echo "🧪 Testing the issuance flow:"
echo "1. You should see 'Welcome to Cachet Wallet' screen"
echo "2. Tap 'Start Identity Verification' button"
echo "3. App will simulate Veriff verification process"
echo "4. After 3 seconds, a new credential should appear"
echo "5. The credential will show:"
echo "   - Title: Identity Credential"
echo "   - Issued by: Cachet" 
echo "   - Status: Verified"
echo "   - Claims: verification_method=veriff, verified=true"
echo
echo "🔗 Network connectivity:"
echo "   App → http://10.0.2.2:8090 → Backend http://localhost:8090"
echo
echo "📊 Monitor backend logs to see the API calls"

# Show some backend logs
echo
echo "🔍 Recent backend activity:"
curl -s -X POST http://localhost:8090/oauth/token \
  -H "Content-Type: application/json" \
  -d '{"grant_type": "client_credentials", "client_id": "test-connection", "scope": "credential_issuance"}' \
  > /dev/null && echo "✅ OAuth2 endpoint working"

echo "🎉 Ready to test the complete issuance flow!"