#!/usr/bin/env bash
set -euo pipefail

echo "🧪 Running tests with coverage..."
set -euo pipefail  # Exit on any error

mkdir -p coverage
echo "Testing verifier..."
(cd services/verifier && go test -v -coverprofile=../../coverage/verifier.out -covermode=atomic ./...)
echo "Testing registry..."
(cd services/registry && go test -v -coverprofile=../../coverage/registry.out -covermode=atomic ./...)
echo "Testing receipts-log..."
(cd services/receipts-log && go test -v -coverprofile=../../coverage/receipts.out -covermode=atomic ./...)
echo "Testing issuance-gateway..."
(cd services/issuance-gateway && go test -v -coverprofile=../../coverage/issuance.out -covermode=atomic ./...)
echo "✅ All tests completed successfully with coverage"
