#!/usr/bin/env bash
set -euo pipefail

echo "🌐 Starting ngrok tunnel for webhook development..."
echo ""
echo "This will expose your local issuance gateway (port 8090) to the internet"
echo "so Veriff can send webhooks to test the complete flow."
echo ""
echo "💡 Usage:"
echo "   1. Keep this running in one terminal"
echo "   2. Copy the HTTPS URL (e.g., https://abc123.ngrok.io)"
echo "   3. Update mobile app to use this URL"
echo "   4. Test complete Veriff webhook flow"
echo ""
echo "Press Ctrl+C to stop the tunnel..."
echo ""

# Check if ngrok is authenticated
if ! ngrok config check > /dev/null 2>&1; then
  echo "❌ ngrok not configured. Run 'webhook:setup' first."
  exit 1
fi

ngrok http 8090 --log stdout
