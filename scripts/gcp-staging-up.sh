#!/usr/bin/env bash
set -euo pipefail

echo "🔺 Resuming staging environment..."

echo "▶️  Restoring Cloud SQL activation policy to ALWAYS..."
gcloud sql instances patch cachet-db --activation-policy=ALWAYS --quiet

echo "🚀 Starting Cloud SQL instance..."
gcloud sql instances restart cachet-db --quiet

echo "⏳ Waiting for Cloud SQL to become RUNNABLE..."
for attempt in {1..30}; do
  STATE=$(gcloud sql instances describe cachet-db --format='value(state)' 2>/dev/null || echo "UNKNOWN")
  echo "   Cloud SQL state: $STATE"
  if [ "$STATE" = "RUNNABLE" ]; then
    break
  fi
  sleep 10
done

if [ "$STATE" != "RUNNABLE" ]; then
  echo "❌ Cloud SQL did not become RUNNABLE within expected time."
  exit 1
fi

echo "📦 Redeploying issuance gateway so staging matches Cloud Run..."
devenv run gcp:deploy:issuance-gateway

echo "✅ Staging environment is back online."
