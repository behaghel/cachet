#!/usr/bin/env bash
set -euo pipefail

    echo "🔐 Setting up Secret Manager with SecretSpec integration..."
    
    PROJECT_ID=$(gcloud config get-value project)
    
    # Generate secure database password
    echo "🔑 Generating secure database password..."
    DB_PASSWORD=$(openssl rand -base64 32)
    
    # Set the password for the postgres user
    echo "📝 Setting database password..."
    gcloud sql users set-password postgres \
      --instance=cachet-db \
      --password="$DB_PASSWORD"
    
    # Create database URL secret with proper connection string
    echo "🔐 Creating/updating database-url secret..."
    CONNECTION_NAME="$PROJECT_ID:us-central1:cachet-db"
    DATABASE_URL="postgresql://postgres:$DB_PASSWORD@/cachet?host=/cloudsql/$CONNECTION_NAME"
    
    # Try to create, but if it exists, add a new version
    if ! echo -n "$DATABASE_URL" | gcloud secrets create database-url --data-file=- 2>/dev/null; then
      echo "Secret already exists, updating with new version..."
      echo -n "$DATABASE_URL" | gcloud secrets versions add database-url --data-file=-
    fi
    
    # Create JWT secret
    echo "🔑 Creating/updating jwt-secret..."
    JWT_SECRET_VALUE=$(openssl rand -base64 32)
    if ! echo -n "$JWT_SECRET_VALUE" | gcloud secrets create jwt-secret --data-file=- 2>/dev/null; then
      echo "Secret already exists, updating with new version..."
      echo -n "$JWT_SECRET_VALUE" | gcloud secrets versions add jwt-secret --data-file=-
    fi
    
    # Create .env file for local development with secretspec
    echo "📝 Creating .env file for local development..."
    cat > .env << EOF
# Secrets for local development with secretspec
CACHET_DB_URL="$DATABASE_URL"
CACHET_JWT_SECRET="$JWT_SECRET_VALUE"
EOF
    
    echo "✅ Secrets created with SecretSpec integration!"
    echo "📋 Your secrets are now available via:"
    echo "   - CACHET_DB_URL (database connection)"  
    echo "   - CACHET_JWT_SECRET (JWT signing key)"
    echo "💡 These are accessible via secretspec in devenv and stored in GCP Secret Manager for production"
    echo "🔧 Local development will use the values from .env file"
