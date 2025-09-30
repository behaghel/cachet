#!/usr/bin/env bash
set -euo pipefail

echo "🔧 Setting up ngrok for webhook development..."
echo ""
echo "ngrok requires a free account to create tunnels."
echo ""
echo "📋 Steps to set up ngrok:"
echo "   1. Sign up: https://dashboard.ngrok.com/signup"
echo "   2. Get your authtoken: https://dashboard.ngrok.com/get-started/your-authtoken"
echo "   3. Run: ngrok config add-authtoken YOUR_TOKEN"
echo "   4. Test: webhook:tunnel"
echo ""
echo "⚡ Alternatively, run this one-liner after getting your token:"
echo "   ngrok config add-authtoken YOUR_TOKEN_HERE"
echo ""
echo "✅ Once set up, use 'webhook:tunnel' or 'webhook:dev' for testing"
