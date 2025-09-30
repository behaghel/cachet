#!/usr/bin/env bash
set -euo pipefail

echo "🔐 Authenticating with Google Cloud..."
gcloud auth login
echo "✅ Successfully authenticated with GCP"
