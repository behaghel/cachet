#!/usr/bin/env bash
set -euo pipefail

echo "🚀 Running full CI pipeline locally..."

echo "📋 Step 1: Schema validation and generation..."
schema:validate
schema:generate

echo "🧪 Step 2: Backend tests..."
test:all
test:integration

echo "📱 Step 3: Mobile tests..."
android:test-unit

echo "🔄 Step 4: Schema compatibility tests..."
test:schema-integration

echo "🔍 Step 5: Quality checks..."
fmt:go
lint:go

echo "✅ Full CI pipeline completed successfully!"
echo "🎉 Ready to create pull request!"
