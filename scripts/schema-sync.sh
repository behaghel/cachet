#!/usr/bin/env bash
set -euo pipefail

echo "🔄 Synchronizing schemas across codebase..."

echo "1. Running validation..."
yamllint schemas/openapi.yaml

echo "2. Generating fresh models..."
schema:generate

echo "3. Running compatibility tests..."
schema:test

echo "4. Updating mobile project..."
# Copy generated Kotlin models to mobile project
cp -r generated/kotlin/src/main/kotlin/* mobile/shared/src/commonMain/kotlin/ 2>/dev/null || true

echo "5. Running tests..."
test:all

echo "✅ Schema synchronization completed!"
