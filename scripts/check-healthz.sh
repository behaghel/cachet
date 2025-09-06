#!/bin/bash

# check-healthz.sh - Script to prevent /healthz endpoints from being committed
# This script should be run in CI/CD pipelines and as a pre-commit hook

set -euo pipefail

echo "Checking for forbidden /healthz endpoints..."

# Search for /healthz in Go files
healthz_files=$(find . -name "*.go" -type f | xargs grep -l '"/healthz"' 2>/dev/null || true)

if [ -n "$healthz_files" ]; then
    echo "❌ ERROR: Found /healthz endpoints in Go files:"
    echo "$healthz_files"
    echo ""
    echo "🚨 /healthz is reserved by Cloud Run infrastructure and will return Google 404 pages"
    echo "   instead of your application's response. Use /health instead."
    echo ""
    echo "Files with /healthz endpoints:"
    for file in $healthz_files; do
        echo "  📁 $file:"
        grep -n '"/healthz"' "$file" | sed 's/^/    /'
    done
    echo ""
    echo "To fix this issue:"
    echo "  1. Replace all '/healthz' with '/health' in the affected files"
    echo "  2. Update any tests that use '/healthz' to use '/health'"
    echo "  3. Add explanatory comments about why /healthz doesn't work on Cloud Run"
    exit 1
fi

echo "✅ No forbidden /healthz endpoints found"