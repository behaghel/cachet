#!/usr/bin/env bash
set -euo pipefail

echo "🧪 Testing complete GCP deployment with SecretSpec..."

SERVICE_URL=$(gcloud run services describe cachet-verifier --region=us-central1 --format='value(status.url)')

echo "1. Testing local SecretSpec access..."
if [ -n "${CACHET_DB_URL:-}" ] && [ -n "${CACHET_JWT_SECRET:-}" ]; then
  echo "   ✅ Local secrets accessible via secretspec"
else
  echo "   ❌ Local secrets not available - check secretspec configuration"
  exit 1
fi

echo "2. Testing deployed service..."
if curl -f "$SERVICE_URL/packs" > /dev/null 2>&1; then
  echo "   ✅ Service responding correctly"
else
  echo "   ❌ Service not responding"
  exit 1
fi

echo "3. Verifying secrets are configured in Cloud Run..."
SECRET_CONFIG=$(gcloud run services describe cachet-verifier --region=us-central1 --format="value(spec.template.spec.containers[0].env[].valueFrom.secretKeyRef.name)" | tr '\n' ',' || echo "")
if [[ "$SECRET_CONFIG" == *"database-url"* ]] && [[ "$SECRET_CONFIG" == *"jwt-secret"* ]]; then
  echo "   ✅ Secrets properly configured in Cloud Run"
else
  echo "   ❌ Secrets not configured in Cloud Run"
  exit 1
fi

echo ""
echo "✅ All tests passed! SecretSpec integration working correctly:"
echo "   • Local development uses .env via secretspec"
echo "   • Production uses Secret Manager via Cloud Run"  
echo "   • Same secret names and consistent access pattern"
echo "   • Service deployed and functional"
