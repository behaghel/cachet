#!/usr/bin/env bash
set -euo pipefail

echo "🔍 Validating OpenAPI schema..."
yamllint schemas/openapi.yaml

# Install and use redocly for OpenAPI validation
if ! command -v redocly &> /dev/null; then
    echo "📦 Installing @redocly/cli..."
    npm install -g @redocly/cli
fi

redocly lint schemas/openapi.yaml
echo "✅ Schema validation passed!"
