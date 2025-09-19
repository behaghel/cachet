# Secrets Management with SecretSpec

This document describes Cachet's comprehensive secret management architecture using [SecretSpec](https://secretspec.dev/) for consistency across all environments.

## Overview

Cachet uses **SecretSpec** as the single source of truth for all secret management, ensuring consistency from local development to production deployment. All secrets are declared in `secretspec.toml` and accessed through a unified interface across different environments.

## Architecture

### Secret Declaration (secretspec.toml)

All secrets are declared in `secretspec.toml` with profiles for different environments:

```toml
[project]
name = "cachet"
revision = "1.0"

[profiles.default]
CACHET_DB_URL = { description = "PostgreSQL database connection URL", required = true }
CACHET_JWT_SECRET = { description = "JWT signing secret key", required = true }

# Veriff Integration
VERIFF_API_KEY = { description = "Legacy fallback Veriff API key", required = false }
VERIFF_BASE_URL = { description = "Veriff API base URL override", required = false, default = "https://stationapi.veriff.com" }
VERIFF_WEBHOOK_SECRET = { description = "Legacy fallback webhook signing secret", required = false }
VERIFF_WEBHOOK_BASE_URL = { description = "Base URL where Veriff should send webhooks", required = false, default = "http://localhost:8082" }
VERIFF_WEBHOOK_EXTERNAL_URL = { description = "Public HTTPS callback URL Veriff should call", required = false, default = "" }
VERIFF_ENVIRONMENT = { description = "Active Veriff integration (test|production)", required = false, default = "test" }
VERIFF_TEST_API_KEY = { description = "Veriff sandbox API key", required = false }
VERIFF_TEST_WEBHOOK_SECRET = { description = "Sandbox webhook signing secret", required = false }
VERIFF_PROD_API_KEY = { description = "Veriff production API key", required = false }
VERIFF_PROD_WEBHOOK_SECRET = { description = "Production webhook signing secret", required = false }

[profiles.ci]
# CI/CD Infrastructure secrets for deployment consistency
GCP_SA_KEY = { description = "Base64-encoded GCP service account key for deployment", required = true }
CACHIX_AUTH_TOKEN = { description = "Cachix authentication token for Nix caching", required = true }
```

### Environment-Specific Providers

| Environment | Provider | Secret Storage | Usage Pattern |
|-------------|----------|----------------|---------------|
| **Local Development** | `dotenv://.env` | `.env` (backed by `pass`) | `secretspec run -- command` |
| **CI/CD** | `env` | GitHub Actions secrets | `secretspec run --provider env --profile ci -- command` |
| **Production** | Cloud Run environment | GCP Secret Manager | Injected via `--set-secrets` |

> `devenv` exports `SECRETSPEC_PROVIDER=dotenv://.env` and `SECRETSPEC_PROFILE=default` for you, so running `secretspec` commands from within `devenv shell` automatically pulls secrets from `.env`.

## Local Development Setup

### 1. Initialize SecretSpec

```bash
devenv shell -- secretspec config init
```

### 2. Create `.env` with pass integration

```bash
# Copy the template and fill with secure values
cp .env.example .env

# .env (example)
CACHET_ENV="local"
CACHET_CONFIG_PATH=""
CACHET_DB_URL="postgresql://postgres:password@localhost:5432/cachet"
CACHET_JWT_SECRET="your-jwt-secret-here"

# Veriff Integration (using pass)
VERIFF_ENVIRONMENT="test"
VERIFF_TEST_API_KEY="$(pass veriff/cachet-sandbox-api-key 2>/dev/null || echo 'dummy-veriff-api-key-for-local')"
VERIFF_TEST_WEBHOOK_SECRET="$(pass veriff/cachet-sandbox-webhook-secret 2>/dev/null || echo 'dummy-webhook-secret-for-local')"
VERIFF_PROD_API_KEY="$(pass veriff/cachet-production-api-key 2>/dev/null || echo '')"
VERIFF_PROD_WEBHOOK_SECRET="$(pass veriff/cachet-production-webhook-secret 2>/dev/null || echo '')"
VERIFF_WEBHOOK_EXTERNAL_URL="$(pass veriff/cachet-webhook-external-url 2>/dev/null || echo 'https://your-ngrok-domain.ngrok-free.app')"
# `.env` is gitignored—never commit real secrets.
```

### 3. Run services with SecretSpec

```bash
# Start all backend services
devenv shell -- dev:up

# Run individual commands
devenv shell -- secretspec run -- go run ./services/issuance-gateway
```

## CI/CD Integration

### GitHub Actions Secrets

Configure these secrets in your GitHub repository settings:

- `GCP_SA_KEY`: Base64-encoded GCP service account JSON key
- `CACHIX_AUTH_TOKEN`: Cachix authentication token for Nix caching
- Application secrets are managed in GCP Secret Manager

### Workflow Usage

```yaml
- name: Deploy with SecretSpec consistency
  env:
    GCP_SA_KEY: ${{ secrets.GCP_SA_KEY }}
    CACHIX_AUTH_TOKEN: ${{ secrets.CACHIX_AUTH_TOKEN }}
  run: |
    devenv shell -- secretspec run --provider env --profile ci -- gcp:deploy:issuance-gateway
```

## Production Deployment

### GCP Secret Manager Setup

1. **Setup GCP infrastructure**:
   ```bash
   devenv shell -- gcp:setup
   devenv shell -- gcp:db:setup
   ```

2. **Configure secrets in GCP Secret Manager**:
   ```bash
   devenv shell -- gcp:secrets:setup
   ```

3. **Deploy with secrets**:
   ```bash
   devenv shell -- gcp:deploy:issuance-gateway
   ```

### Cloud Run Secret Injection

Services are deployed with secrets automatically injected from GCP Secret Manager:

```bash
gcloud run deploy cachet-issuance-gateway \
  --set-env-vars VERIFF_ENVIRONMENT=production \
  --set-secrets CACHET_DB_URL=database-url:latest, \
               CACHET_JWT_SECRET=jwt-secret:latest, \
               VERIFF_PROD_API_KEY=veriff-prod-api-key:latest, \
               VERIFF_PROD_WEBHOOK_SECRET=veriff-prod-webhook-secret:latest

# Staging example (remain on sandbox integration)
gcloud run deploy cachet-issuance-gateway-staging \
  --set-env-vars VERIFF_ENVIRONMENT=test \
  --set-secrets VERIFF_TEST_API_KEY=veriff-test-api-key:latest, \
               VERIFF_TEST_WEBHOOK_SECRET=veriff-test-webhook-secret:latest
```

## Secret Flow Diagram

```
📝 secretspec.toml (declaration)
    ├── Local Development
    │   └── pass → .env → secretspec run
    ├── CI/CD
    │   └── GitHub Actions secrets → env vars → secretspec --provider env --profile ci  
    └── Production
        └── GCP Secret Manager → Cloud Run → application environment
```

## Commands Reference

### Local Development

```bash
# Check secret availability
devenv shell -- secretspec check

# Run with secrets
devenv shell -- dev:up
devenv shell -- secretspec run -- android:run

> `dev:up` clears any pre-existing `CACHET_CONFIG_PATH` before loading secrets so the Go services fall back to their built-in config resolver.
```

### CI/CD

```bash
# Run deployment with CI profile
devenv shell -- secretspec run --provider env --profile ci -- gcp:deploy:issuance-gateway

# Check CI secrets
devenv shell -- secretspec check --provider env --profile ci
```

### Production

```bash
# Setup GCP secrets (one-time)
devenv shell -- gcp:secrets:setup

# Deploy services
devenv shell -- gcp:deploy:issuance-gateway
devenv shell -- gcp:deploy:verifier
```

## Security Best Practices

### 1. Never Store Secrets in Code
- ❌ **Never** commit secrets to git repositories
- ❌ **Never** hardcode secrets in configuration files
- ✅ **Always** declare secrets in `secretspec.toml`
- ✅ **Always** use SecretSpec providers for secret access

### 2. Environment Separation
- **Local**: Use `pass` (Unix password store) for secure local storage
- **CI/CD**: Use GitHub Actions secrets with SecretSpec environment provider
- **Production**: Use GCP Secret Manager with proper IAM controls

### 3. Secret Rotation
- Secrets in GCP Secret Manager support versioning
- Update secrets using `gcp:secrets:setup` script
- Cloud Run services automatically use latest secret versions

### 4. Access Control
- GCP Service accounts have minimal required permissions
- Secrets are scoped to specific services via IAM bindings
- Local development uses personal credentials via `pass`

## Troubleshooting

### Common Issues

1. **Secret not found error**:
   ```bash
   # Check if secret is declared
   devenv shell -- secretspec check
   
   # Verify secret exists in storage
   pass show veriff/cachet-api-key  # Local
   gcloud secrets versions list veriff-api-key  # GCP
   ```

2. **Provider not configured**:
   ```bash
   # Initialize SecretSpec configuration
   devenv shell -- secretspec config init
   ```

3. **GCP authentication failure**:
   ```bash
   # Check GCP authentication
   gcloud auth list
   devenv shell -- gcp:auth
   ```

### Debug Commands

```bash
# List all declared secrets
devenv shell -- secretspec check

# Test secret access
devenv shell -- secretspec get VERIFF_API_KEY

# Run with debug output
devenv shell -- secretspec run -- env | grep VERIFF
```

## Migration from Legacy Secret Management

If migrating from direct environment variables or other secret management:

1. **Add secrets to `secretspec.toml`**
2. **Update commands to use `secretspec run`**
3. **Configure appropriate providers per environment**
4. **Remove direct secret references from scripts**

### Before (Legacy)
```bash
export VERIFF_API_KEY="secret-value"
go run ./services/issuance-gateway
```

### After (SecretSpec)
```bash
devenv shell -- secretspec run -- go run ./services/issuance-gateway
```

## Related Documentation

- [SecretSpec Official Documentation](https://secretspec.dev/)
- [devenv SecretSpec Integration](https://devenv.sh/integrations/secretspec/)
- [GCP Secret Manager Documentation](https://cloud.google.com/secret-manager/docs)
- [`CLAUDE.md`](../CLAUDE.md) - Development commands and practices
- [`docs/HEALTH_ENDPOINTS.md`](./HEALTH_ENDPOINTS.md) - Health check constraints
