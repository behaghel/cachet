#!/usr/bin/env bash
set -euo pipefail

echo "🏗️  Setting up GCP project for Cachet deployment..."
set -euo pipefail

# Check if authenticated
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | head -n1 > /dev/null; then
  echo "❌ Not authenticated with GCP. Run 'gcp:auth' first."
  exit 1
fi

# Set project (user will be prompted to select/create)
echo "Please select or create a GCP project:"
gcloud projects list
read -p "Enter project ID (or press Enter to create new): " PROJECT_ID

if [ -z "$PROJECT_ID" ]; then
  read -p "Enter new project ID (e.g., cachet-prod-123): " PROJECT_ID
  gcloud projects create $PROJECT_ID
  
  # Wait for project creation to propagate
  echo "⏳ Waiting for project creation to complete..."
  sleep 5
fi

gcloud config set project $PROJECT_ID
echo "📋 Using project: $PROJECT_ID"

# Ensure billing is enabled (critical for Cloud SQL and other services)
echo "🔍 Checking billing status..."
if ! gcloud billing projects list --filter="projectId:$PROJECT_ID" --format="value(billingEnabled)" | grep -q "True"; then
  echo "⚠️  Billing is not enabled for this project."
  echo "   Please enable billing at: https://console.cloud.google.com/billing/linkedaccount?project=$PROJECT_ID"
  echo "   Press Enter when billing is enabled..."
  read
fi

# Enable required APIs with error handling
echo "🔧 Enabling required GCP APIs..."
APIS=(
  cloudbuild.googleapis.com
  run.googleapis.com  
  sqladmin.googleapis.com
  secretmanager.googleapis.com
  containerregistry.googleapis.com
  cloudresourcemanager.googleapis.com
)

for api in "${APIS[@]}"; do
  echo "Enabling $api..."
  gcloud services enable $api || {
    echo "⚠️ Failed to enable $api - this may cause issues later"
  }
done

echo "🔐 Provisioning CI/CD service account access..."
DEFAULT_CI_SA="cachet-cicd@$PROJECT_ID.iam.gserviceaccount.com"
read -p "CI service account email [$DEFAULT_CI_SA]: " CI_SA_EMAIL
CI_SA_EMAIL="${CI_SA_EMAIL:-$DEFAULT_CI_SA}"

if ! gcloud iam service-accounts describe "$CI_SA_EMAIL" >/dev/null 2>&1; then
  CI_SA_NAME=$(echo "$CI_SA_EMAIL" | cut -d'@' -f1)
  echo "➕ Creating service account $CI_SA_NAME..."
  gcloud iam service-accounts create "$CI_SA_NAME" --display-name="Cachet CI/CD" || true
  CI_SA_EMAIL="$CI_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"
fi

echo "🛡️  Granting required roles to $CI_SA_EMAIL..."
for role in \
  roles/run.admin \
  roles/iam.serviceAccountUser \
  roles/secretmanager.secretAccessor \
  roles/cloudsql.viewer \
  roles/secretmanager.viewer \
  roles/storage.objectAdmin \
  roles/containeranalysis.admin \
  roles/artifactregistry.writer
do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:$CI_SA_EMAIL" \
    --role="$role" \
    --quiet || echo "⚠️  Failed to grant $role (requires appropriate permissions)"
done

echo "✅ GCP project setup completed!"
echo "📝 Next steps (run in order):"
echo "   1. Run 'gcp:db:setup' to create Cloud SQL database"
echo "   2. Run 'gcp:secrets:setup' to configure secrets with SecretSpec"
echo "   3. Run 'gcp:deploy:verifier' to deploy services"
