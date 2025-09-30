#!/usr/bin/env bash
set -euo pipefail

echo "🚀 Starting complete webhook development environment..."
echo ""

# Check if ngrok is authenticated
if ! ngrok config check > /dev/null 2>&1; then
  echo "❌ ngrok not configured. Run 'webhook:setup' first."
  exit 1
fi

echo "This will start:"
echo "  1. Local issuance gateway (port 8090)"
echo "  2. ngrok tunnel for webhook reception"
echo ""
echo "📋 Setup steps:"
echo "  1. Wait for both services to start"
echo "  2. Note the ngrok HTTPS URL"
echo "  3. Update mobile app backend URL"
echo "  4. Test complete end-to-end Veriff flow"
echo ""

# Start issuance gateway in background
echo "Starting issuance gateway..."
cd services/issuance-gateway
PORT=8090 go run . &
GATEWAY_PID=$!

# Wait for it to start
echo "Waiting for gateway to start..."
sleep 3

# Start ngrok tunnel
echo "Starting ngrok tunnel..."
echo ""
ngrok http 8090 --log stdout

# Cleanup on exit
trap "kill $GATEWAY_PID 2>/dev/null" EXIT
