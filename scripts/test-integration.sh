#!/usr/bin/env bash
set -euo pipefail

echo "Running integration tests..."
devenv up --detach
sleep 5
# Note: Using /health instead of /healthz - Cloud Run intercepts /healthz requests
curl -f http://localhost:8081/health && echo "✅ Verifier healthy"
curl -f http://localhost:8082/health && echo "✅ Registry healthy" 
curl -f http://localhost:8083/health && echo "✅ Receipts healthy"
curl -f http://localhost:8090/health && echo "✅ Issuance gateway healthy"
devenv processes stop
