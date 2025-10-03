#!/usr/bin/env bash
set -euo pipefail

echo "🔒 Running security scan..."

# Install gosec if not already available
if ! command -v gosec &> /dev/null; then
  echo "📦 Installing gosec..."
  go install github.com/securecodewarrior/gosec/v2/cmd/gosec@latest || {
    echo "❌ Failed to install gosec"
    exit 1
  }
fi

# Run security scan on each service with proper Go module context
echo "🔍 Scanning services for security issues..."
echo "Scanning verifier..."
cd services/verifier && gosec -exclude-generated ./...
echo "Scanning registry..."
cd ../registry && gosec -exclude-generated ./...
echo "Scanning receipts-log..."
cd ../receipts-log && gosec -exclude-generated ./...
echo "Scanning connector-hub..."
cd ../connector-hub && gosec -exclude-generated ./...
echo "Scanning transparency-log..."
cd ../transparency-log && gosec -exclude-generated ./...
echo "Scanning vouching-service..."
cd ../vouching-service && gosec -exclude-generated ./...
echo "Scanning issuance-gateway..."
cd ../issuance-gateway && gosec -exclude-generated ./...

echo "✅ Security scan completed successfully"
