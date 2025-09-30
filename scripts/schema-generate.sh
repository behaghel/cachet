#!/usr/bin/env bash
set -euo pipefail

echo "🔧 Generating code from OpenAPI schema..."

echo "1. Generating Go models..."
mkdir -p generated/go
oapi-codegen -generate types -package models schemas/openapi.yaml > generated/go/models.go

echo "2. Generating Kotlin models..."
mkdir -p generated/kotlin
openapi-generator-cli generate \
  -i schemas/openapi.yaml \
  -g kotlin \
  -o generated/kotlin \
  --additional-properties=packageName=id.cachet.wallet.generated,serializationLibrary=kotlinx_serialization

echo "✅ Code generation completed!"
echo "📁 Generated files:"
echo "   - Go: generated/go/models.go"
echo "   - Kotlin: generated/kotlin/"
