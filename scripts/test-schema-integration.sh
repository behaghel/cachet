#!/usr/bin/env bash
set -euo pipefail

echo "🧪 Running schema integration tests..."

echo "1. Testing Go schema compatibility..."
cd tests/schema-integration && go test -v .

echo "2. Testing Kotlin schema compatibility..."
cd mobile && gradle :shared:test --tests "*SchemaCompatibilityTest*"

echo "✅ Schema integration tests completed!"
