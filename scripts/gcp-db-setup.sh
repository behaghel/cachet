#!/usr/bin/env bash
set -euo pipefail

echo "🗄️  Setting up Cloud SQL database..."
set -euo pipefail

PROJECT_ID=$(gcloud config get-value project)
INSTANCE_NAME="cachet-db"
DB_NAME="cachet"

# Create Cloud SQL instance
echo "Creating Cloud SQL PostgreSQL instance (db-f1-micro)..."
gcloud sql instances create $INSTANCE_NAME \
  --database-version=POSTGRES_15 \
  --tier=db-f1-micro \
  --storage-size=10 \
  --region=us-central1 \
  --activation-policy=ALWAYS \
  --root-password=temp-password-change-me

# Create database
gcloud sql databases create $DB_NAME --instance=$INSTANCE_NAME

# Get connection string
CONNECTION_NAME=$(gcloud sql instances describe $INSTANCE_NAME --format="value(connectionName)")

echo "✅ Database setup completed!"
echo "📋 Connection details:"
echo "   Instance: $INSTANCE_NAME"
echo "   Database: $DB_NAME"
echo "   Connection: $CONNECTION_NAME"
echo "⚠️  Remember to change the root password!"
