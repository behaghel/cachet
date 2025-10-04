#!/usr/bin/env bash
set -euo pipefail

if ! command -v gcloud >/dev/null 2>&1; then
  echo "gcloud CLI not found; run this inside 'devenv shell'." >&2
  exit 1
fi

PROJECT_ID=${GCLOUD_PROJECT:-$(gcloud config get-value project 2>/dev/null)}
if [ -z "$PROJECT_ID" ] || [ "$PROJECT_ID" = "(unset)" ]; then
  echo "GCP project not set. Run 'devenv shell -- gcp:auth' or 'gcloud config set project <id>'." >&2
  exit 1
fi

REGION=${GCLOUD_REGION:-us-central1}
SERVICES=(
  "cachet-issuance-gateway"
  "cachet-verifier"
  "cachet-registry"
  "cachet-receipts-log"
)

echo "📋 Tailing staging logs in project $PROJECT_ID (region: $REGION)"
echo "   Services: ${SERVICES[*]}"
echo "   Press Ctrl+C to stop."

filter="resource.type=\"cloud_run_revision\" AND resource.labels.location=\"$REGION\" AND ("
for idx in "${!SERVICES[@]}"; do
  svc="${SERVICES[$idx]}"
  if [ $idx -gt 0 ]; then
    filter+=" OR "
  fi
  filter+="resource.labels.service_name=\"$svc\""
done
filter+=")"

gcloud beta logging tail "$filter" \
  --project="$PROJECT_ID" \
  --format="text" \
  --verbosity=error
