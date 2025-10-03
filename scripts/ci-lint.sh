#!/usr/bin/env bash
set -euo pipefail

echo "🔍 Running golangci-lint on all services..."

# Use absolute paths and single commands to avoid cd issues in CI
echo "Linting verifier..."
(cd services/verifier && golangci-lint run)
echo "Linting registry..."
(cd services/registry && golangci-lint run)
echo "Linting receipts-log..."
(cd services/receipts-log && golangci-lint run)
echo "Linting connector-hub..."
(cd services/connector-hub && golangci-lint run)
echo "Linting transparency-log..."  
(cd services/transparency-log && golangci-lint run)
echo "Linting vouching-service..."
(cd services/vouching-service && golangci-lint run)
echo "Linting issuance-gateway..."
(cd services/issuance-gateway && golangci-lint run)
echo "✅ All services passed linting successfully"
