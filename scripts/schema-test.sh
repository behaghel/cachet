#!/usr/bin/env bash
set -euo pipefail

echo "🧪 Running schema compatibility tests..."

echo "1. Validating schema..."
yamllint schemas/openapi.yaml

echo "2. Generating temporary models..."
rm -rf /tmp/cachet-schema-test
mkdir -p /tmp/cachet-schema-test/go /tmp/cachet-schema-test/kotlin

oapi-codegen -generate types -package models schemas/openapi.yaml > /tmp/cachet-schema-test/go/models.go
openapi-generator-cli generate \
  -i schemas/openapi.yaml \
  -g kotlin \
  -o /tmp/cachet-schema-test/kotlin \
  --additional-properties=packageName=id.cachet.wallet.generated,serializationLibrary=kotlinx_serialization

echo "3. Testing Go compilation..."
cd /tmp/cachet-schema-test/go && go mod init test && go mod tidy && go build .

echo "✅ Schema compatibility tests passed!"
