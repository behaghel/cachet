#!/usr/bin/env bash
set -euo pipefail

echo "Running tests for all services..."
cd services/verifier && go test -v ./... && echo "✅ Verifier tests passed"
cd ../registry && go test -v ./... && echo "✅ Registry tests passed"  
cd ../receipts-log && go test -v ./... && echo "✅ Receipts-log tests passed"
cd ../issuance-gateway && go test -v ./... && echo "✅ Issuance gateway tests passed"
