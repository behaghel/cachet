#!/usr/bin/env bash
set -euo pipefail

echo "🚀 Deploying Issuance Gateway to Cloud Run with Veriff integration..."
set -euo pipefail

PROJECT_ID=$(gcloud config get-value project)
if [ -z "$PROJECT_ID" ] || [ "$PROJECT_ID" = "(unset)" ]; then
  echo "❌ GCP project ID not set. Please run 'gcloud config set project YOUR_PROJECT_ID'"
  exit 1
fi
echo "📋 Using GCP project: $PROJECT_ID"
SERVICE_NAME="cachet-issuance-gateway"

# Ensure service account has secret access (idempotent)
echo "🔐 Ensuring service account has Secret Manager access..."
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format="value(projectNumber)")
SERVICE_ACCOUNT="$PROJECT_NUMBER-compute@developer.gserviceaccount.com"
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SERVICE_ACCOUNT" \
  --role="roles/secretmanager.secretAccessor" \
  --quiet || echo "IAM binding already exists"

CURRENT_RUN_URL=$(gcloud run services describe $SERVICE_NAME --region=us-central1 --format='value(status.url)' 2>/dev/null || true)

if [ -n "$CURRENT_RUN_URL" ]; then
  WEBHOOK_ENV="VERIFF_WEBHOOK_EXTERNAL_URL=$CURRENT_RUN_URL/webhooks/veriff"
  echo "🔁 Using existing Cloud Run URL for webhooks: $CURRENT_RUN_URL"
else
  WEBHOOK_ENV=""
  echo "⚠️ Could not determine Cloud Run URL before deploy; webhooks will default to config value"
fi

CONFIG_PATH="${CACHET_CONFIG_PATH:-config/app-config.json}"
ACTIVE_VERIFF="test"
VERIFF_BASE_URL_OVERRIDE=""
if command -v jq >/dev/null 2>&1 && [ -f "$CONFIG_PATH" ]; then
  ACTIVE_VERIFF=$(jq -r '.environments.staging.activeVeriffIntegration // .veriff.defaultIntegration // "test"' "$CONFIG_PATH" 2>/dev/null || echo "test")
  if [ -z "$ACTIVE_VERIFF" ] || [ "$ACTIVE_VERIFF" = "null" ]; then
    ACTIVE_VERIFF="test"
  fi
  VERIFF_BASE_URL_OVERRIDE=$(jq -r --arg integration "$ACTIVE_VERIFF" '.veriff.integrations[$integration].baseUrl // ""' "$CONFIG_PATH" 2>/dev/null || echo "")
  if [ "$VERIFF_BASE_URL_OVERRIDE" = "null" ]; then
    VERIFF_BASE_URL_OVERRIDE=""
  fi
else
  echo "⚠️ Could not read $CONFIG_PATH; defaulting Veriff integration to 'test'"
  ACTIVE_VERIFF="test"
fi

ACTIVE_VERIFF_LOWER=$(printf "%s" "$ACTIVE_VERIFF" | tr '[:upper:]' '[:lower:]')
if [ -z "$VERIFF_BASE_URL_OVERRIDE" ]; then
  if [ "$ACTIVE_VERIFF_LOWER" = "production" ] || [ "$ACTIVE_VERIFF_LOWER" = "prod" ] || [ "$ACTIVE_VERIFF_LOWER" = "live" ]; then
    VERIFF_BASE_URL_OVERRIDE="https://api.veriff.com"
  else
    VERIFF_BASE_URL_OVERRIDE="https://stationapi.veriff.com"
  fi
fi

API_SECRET_NAME="veriff-${ACTIVE_VERIFF_LOWER}-api-key"
WEBHOOK_SECRET_NAME="veriff-${ACTIVE_VERIFF_LOWER}-webhook-secret"

echo "🎯 Active Veriff integration for staging: $ACTIVE_VERIFF"
echo "   ↳ Expected secrets: $API_SECRET_NAME, $WEBHOOK_SECRET_NAME"

for secret in "$API_SECRET_NAME" "$WEBHOOK_SECRET_NAME"; do
  if ! gcloud secrets describe "$secret" --format='value(name)' >/dev/null 2>&1; then
    echo "❌ Secret '$secret' not found in Secret Manager. Create it or add a new version before deploying."
    echo "   Hint: printf 'value' | gcloud secrets versions add $secret --data-file=-"
    exit 1
  fi
done

# Build and push container using Dockerfile (avoids skopeo digest issues)
echo "📦 Building Docker image for issuance gateway..."
docker build \
  --file services/issuance-gateway/Dockerfile \
  --tag gcr.io/$PROJECT_ID/$SERVICE_NAME:latest \
  .

echo "🚢 Pushing Docker image to Artifact Registry..."
docker push gcr.io/$PROJECT_ID/$SERVICE_NAME:latest

# Deploy to Cloud Run with SecretSpec-consistent secrets + Veriff credentials  
echo "🌐 Deploying to Cloud Run with secrets from Secret Manager..."
ENV_VARS="ENVIRONMENT=staging,CACHET_ENV=staging,VERIFF_ENVIRONMENT=$ACTIVE_VERIFF,VERIFF_BASE_URL=$VERIFF_BASE_URL_OVERRIDE"
if [ -n "$WEBHOOK_ENV" ]; then
  ENV_VARS="$ENV_VARS,$WEBHOOK_ENV"
fi

gcloud run deploy $SERVICE_NAME \
  --image gcr.io/$PROJECT_ID/$SERVICE_NAME:latest \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --port 8090 \
  --set-env-vars $ENV_VARS \
  --set-secrets CACHET_DB_URL=database-url:latest,CACHET_JWT_SECRET=jwt-secret:latest,VERIFF_API_KEY=$API_SECRET_NAME:latest,VERIFF_WEBHOOK_SECRET=$WEBHOOK_SECRET_NAME:latest

# Get the deployed service URL for webhook configuration
SERVICE_URL=$(gcloud run services describe $SERVICE_NAME --region=us-central1 --format='value(status.url)')

echo "✅ Issuance Gateway deployed successfully!"
echo "🔗 Service URL: $SERVICE_URL"
echo "🪝 Veriff Webhook URL: $SERVICE_URL/webhooks/veriff"
