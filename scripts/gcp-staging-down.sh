#!/usr/bin/env bash
set -euo pipefail

echo "🔻 Suspending staging environment to minimize cost..."

echo "⏸️  Setting Cloud SQL activation policy to NEVER (manual)..."
gcloud sql instances patch cachet-db --activation-policy=NEVER --quiet

echo "🛑 Applying change and stopping instance..."
gcloud sql instances restart cachet-db --quiet || true
echo "   Cloud SQL will stop after restart completes. Storage charges may still apply."

echo "ℹ️  Cloud Run services scale to zero automatically; no additional action required."
echo "✅ Staging environment suspended. Run 'gcp:staging:up' to resume."
