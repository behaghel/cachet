#!/usr/bin/env bash
set -euo pipefail

echo "📦 Downloading dependencies..."
cd services/verifier && go mod download
cd ../registry && go mod download  
cd ../receipts-log && go mod download
cd ../common && go mod download
cd ../connector-hub && go mod download
cd ../transparency-log && go mod download
cd ../vouching-service && go mod download
echo "✅ Dependencies downloaded"
