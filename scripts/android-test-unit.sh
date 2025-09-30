#!/usr/bin/env bash
set -euo pipefail

echo "🧪 Running unit tests..."

if [ -z "$JAVA_HOME" ] && ! command -v java &> /dev/null; then
  echo "❌ Error: Java not found. Make sure you're running with DEVENV_ENABLE_ANDROID=1"
  echo "   Usage: DEVENV_ENABLE_ANDROID=1 devenv shell -- android:test-unit"
  exit 1
fi

(
  cd mobile

  echo "1. Running shared module tests (:shared:testDebugUnitTest)..."
  ./gradlew --no-daemon :shared:testDebugUnitTest

  echo "2. Running Android unit tests (:androidApp:testDebugUnitTest)..."
  ./gradlew --no-daemon :androidApp:testDebugUnitTest
)

echo "✅ Unit tests completed!"
echo "📊 Test results available in mobile/*/build/reports/tests/"
