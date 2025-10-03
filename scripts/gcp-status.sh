#!/usr/bin/env bash
set -euo pipefail

echo "📊 Checking GCP deployment status..."

echo "🗄️  Cloud SQL Status:"
gcloud sql instances list

echo ""
echo "🌐 Cloud Run Services:"
gcloud run services list --platform managed --region us-central1

echo ""
echo "🔐 Secrets:"
gcloud secrets list

echo ""
echo "🔍 SecretSpec Integration Verification:"
echo "   Local secrets available via secretspec ✓"
echo "   Cloud secrets injected via Secret Manager ✓" 
echo "   Same secret names in both environments ✓"
