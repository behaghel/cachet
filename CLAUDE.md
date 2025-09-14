# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

**IMPORTANT: ALL commands in this project must be run within `devenv shell` or prefixed with `devenv shell --` to ensure proper environment and dependencies.**

### Quick Start

- `devenv shell -- dev:services` or `devenv up --detach` - Start all backend services
- `devenv shell` - Enter development environment with all tools
- `devenv processes stop` - Stop running services

### Development Environment (devenv)

The project uses devenv for dependency management including Android SDK. Key commands:

**Backend:**

- `devenv shell -- dev:services` - Start services via devenv processes (recommended)
- `devenv shell -- dev:stop` - Stop all services
- `devenv shell -- fmt:go` - Format Go code
- `devenv shell -- lint:go` - Lint Go code with golangci-lint
- `devenv shell -- test:all` - Run unit tests for all services
- `devenv shell -- test:coverage` - Run tests with coverage reports
- `devenv shell -- test:integration` - Run integration tests

**Android:**

- `devenv shell -- android:emulator` - Create and start Android emulator
- `devenv shell -- android:build` - Build Android app
- `devenv shell -- android:install` - Install app on emulator
- `devenv shell -- android:run` - Full setup (backend + Android app)
- `devenv shell -- android:test` - Run Android instrumented tests (requires emulator)
- `devenv shell -- android:test-unit` - Run unit tests for all modules

### Service Ports

- Verifier: 8081 (CACHET_VERIFIER_PORT)
- Registry: 8082 (CACHET_REGISTRY_PORT)
- Receipts: 8083 (CACHET_RECEIPTS_PORT)
- Issuance Gateway: 8090 (CACHET_ISSUANCE_PORT)

## ⚠️ Health Endpoints - CRITICAL LEARNING
**🚨 NEVER use `/healthz` for health checks in this project!**
- Cloud Run infrastructure intercepts `/healthz` and returns Google 404 pages before reaching our apps
- **Always use `/health` instead** - it works correctly on all platforms
- Pre-commit hooks and CI/CD prevent `/healthz` from being committed
- See `docs/HEALTH_ENDPOINTS.md` for detailed explanation and examples
- **This is a learned architectural constraint that must be maintained**

## 🔐 Secret Management - CRITICAL ARCHITECTURE
**🚨 ALL secrets in this project MUST be managed through SecretSpec for consistency!**
- **Local Development**: Use `secretspec run --provider dotenv` with `.env.local` (backed by `pass`)
- **CI/CD**: Use `secretspec run --provider env --profile ci` with GitHub Actions secrets
- **Production**: GCP Secret Manager injected via Cloud Run `--set-secrets`
- **Configuration**: All secrets declared in `secretspec.toml` with profiles
- See `docs/SECRETS_MANAGEMENT.md` for complete setup and examples
- **NEVER mix GitHub Actions native secrets with SecretSpec patterns**

## Architecture

### Service Structure

Cachet is a microservices-based trust provider with three core services:

1. **Verifier** (`services/verifier/`) - Manages Trust Pack lists and verifies credential presentations
   - Endpoints: `/packs` (GET), `/presentations/verify` (POST)
   - Returns verification badges and predicates

2. **Registry** (`services/registry/`) - Policy/pack registry service
   - Endpoints: `/policy/manifest` (GET)
   - Serves policy manifests with DID-based signing

3. **Receipts Log** (`services/receipts-log/`) - Consent receipts and transparency logging
   - Transparency log stub implementation

4. **Issuance Gateway** (`services/issuance-gateway/`) - OpenID4VCI credential issuance
   - Endpoints: `/oauth/token` (OAuth2), `/credential` (VC issuance), `/webhooks/veriff` (Veriff integration)
   - Issues SD-JWT VCs with StatusList2021 revocation support
   - Integrates with Veriff for foundational identity verification

### Technology Stack

- **Backend**: Go 1.22 with Chi router
- **Logging**: Zerolog for structured logging
- **Testing**: testify framework with coverage reporting
- **Common Module**: `services/common/` - Shared Go dependencies
- **APIs**: OpenAPI 3.0.3 specifications in `api/`
- **SDKs**: TypeScript (`sdk/typescript/`), Kotlin/Swift stubs (`sdk/kotlin/`, `sdk/swift/`)
- **Mobile**: KMM wallet placeholder (`mobile/`)
- **CI/CD**: GitHub Actions with automated testing, linting, security scanning

### Key Concepts

- **Trust Packs**: Reusable, privacy-preserving credential templates (e.g., "Childcare Readiness", "Safe Seller")
- **Presentations**: Verifiable credential bundles verified against policies
- **Policy Manifests**: DID-signed policy definitions with versioning

### Data Flow

1. **Issuance**: Veriff webhook → Issuance Gateway → SD-JWT VC issued via OpenID4VCI
2. **Verification**: Clients request available Trust Packs from Verifier
3. **Presentation**: Credential presentations are verified against registered policies
4. **Results**: Verification results include badges, predicates, and freshness status
5. **Registry**: Provides policy manifests for Trust Pack definitions

### Mobile Wallet

- **Location**: `mobile/` - Kotlin Multiplatform Mobile (KMM) wallet app
- **Shared module**: `mobile/shared/` - Business logic, networking, data models
- **Android app**: `mobile/androidApp/` - Android-specific UI and platform integrations
- **Features**: OpenID4VCI credential issuance, SQLite credential vault, Jetpack Compose UI
- **Networking**: Uses `10.0.2.2:8090` to connect to local backend from emulator

### Development Files

- Trust Pack definitions: `docs/PACKS/`
- Receipt samples: `docs/RECEIPTS/`
- Policy manifest: `docs/POLICY_MANIFEST.yaml`
- Architecture docs: `docs/ARCHITECTURE.md`, `docs/TRANSPARENCY_LOG_DESIGN.md`

## Development Best Practices - CRITICAL LEARNINGS

### Integration Development Rules

**🚨 LEARNED FROM PHASE A INTEGRATION NIGHTMARE - DO NOT REPEAT**

When adding new features to the mobile app:

1. **Understand before building** - Always explore existing models/patterns first
2. **Start minimal** - Get the simplest possible version working, then iterate
3. **Validate early and often** - Compile after every small change, not at the end
4. **Respect existing architecture** - Extend existing classes, don't create parallel hierarchies
5. **Plan integration points** - How does new code connect to existing systems?
6. **🚨 BUILD USER-FACING VALUE FIRST** - Backend changes mean nothing if users can't see/use them

### Development Process

1. **Define the integration contract** - How does it connect to existing mobile architecture?
2. **Start with stub implementations** - Get the interfaces right first  
3. **Build incrementally** - One small working piece at a time
4. **Test as we go** - Compilation + basic functionality, not just at the end

**Example of what NOT to do**: Creating `QualityProfile`, `EnhancedPredicate`, and complex privacy vaults before ensuring they integrate with existing `VerifiableCredential` and `AvailablePredicate` classes.

**Example of what TO do**: Extend existing models incrementally, validate compilation frequently, build complexity gradually after core integration works.

**🚨 PHASE A LEARNING**: Building enhanced backend Veriff integration without connecting it to user-visible verification flow. Result: Users see no difference despite significant backend work. **Always ensure new capabilities are exposed in the UI before considering a feature "done".**

## 🚀 CI/CD & Deployment - CRITICAL LEARNINGS

### DevEnv Container Deployment

**🚨 LEARNED FROM COMPLEX CI/CD DEBUGGING - KEY PATTERNS**

#### Container Registry Configuration
- **Registry URL Format**: Use base registry URL only: `docker://gcr.io/$PROJECT_ID/`
- **Let devenv handle**: Container names and tags automatically appended
- **Wrong**: `docker://gcr.io/$PROJECT_ID/$SERVICE_NAME:latest` (causes concatenation errors)
- **Right**: `docker://gcr.io/$PROJECT_ID/` + `devenv container copy servicename`

#### Container Naming Convention
- **Container definition name** MUST match **Cloud Run service name**
- **Example**: If `SERVICE_NAME="cachet-issuance-gateway"`, container definition must be:
  ```nix
  issuance = {
    name = "cachet-issuance-gateway";  # NOT "cachet-issuance"
    ...
  }
  ```

#### Port Configuration Alignment
- **Container startup command** and **Cloud Run deployment** ports MUST match
- **Container**: `export PORT=${PORT:-8090}`
- **Deployment**: `--port 8090`
- **Mismatch causes**: "Container failed to start and listen on the port" errors

#### DevEnv Container Commands
```bash
# Correct usage patterns:
devenv container --registry docker://gcr.io/$PROJECT_ID/ copy servicename
devenv container build servicename  # For local testing
devenv container list               # See available containers
```

### CI/CD Debugging Strategy

**🚨 CRITICAL: Use local testing to accelerate feedback loops**

1. **Test deployment locally first**: `devenv shell -- gcp:deploy:service-name`
2. **Validate each component**: Registry auth, container build, container push, service deployment
3. **Fix issues locally**: Much faster than waiting for 30+ minute CI cycles
4. **Common local fixes**: Authentication (`gcloud auth configure-docker gcr.io`), project settings
5. **Push verified fixes**: Only push to CI after local validation succeeds

### Container Format Compatibility

- **DevEnv containers** use OCI format natively
- **Docker load** expects tar format → causes "archive/tar: invalid tar header" errors
- **Solution**: Use `devenv container copy` directly to registry, avoid intermediate docker load
- **Authentication**: Configure registry-specific auth: `gcloud auth configure-docker gcr.io --quiet`

### CI/CD Pipeline Architecture Insights

- **Pipeline duration**: ~30+ minutes for full validation (see `docs/CI_OPTIMIZATION_PLAN.md`)
- **Critical path**: Backend → Containers → Integration Tests → Deployment
- **Deployment triggers**: Only after all prerequisite jobs succeed
- **Secret management**: Consistent SecretSpec patterns across CI/CD and production

### Deployment Validation Checklist

✅ **Before pushing to CI:**
1. Container builds locally (`devenv container build servicename`)
2. Container pushes to registry (`devenv container copy servicename`)
3. Registry authentication configured
4. Container name matches service name
5. Port configuration aligned
6. Environment variables and secrets properly configured

✅ **CI/CD Health Indicators:**
1. ✓ Android App builds successfully
2. ✓ Backend (Go Services) passes tests
3. ✓ Build devenv Containers completes
4. ✓ Integration Tests pass
5. ✓ Deploy to GCP progresses through authentication
6. ✓ Container revision created and starts serving traffic

### Production Deployment

- **Cloud Run**: Managed container platform with auto-scaling
- **Registry**: Google Container Registry (gcr.io) for container images
- **Secrets**: Cloud Run `--set-secrets` integration with GCP Secret Manager
- **Networking**: HTTPS endpoints required for Veriff webhook integration
- **Monitoring**: Use GCP Console logs for troubleshooting container startup issues

## 🚨 Version Control Hygiene - CRITICAL LEARNING

### Binary File Exclusion - NEVER COMMIT THESE

**🚨 LEARNED FROM MASSIVE GITIGNORE CLEANUP - ENFORCE STRICT PATTERNS**

#### Gradle Build Artifacts
```gitignore
# Gradle daemon and cache files
mobile/.gradle/
mobile/*/.gradle/
**/build/
*.lock
*.bin
*.cache
*.dat
*.dump
```

#### Generated Code - ALWAYS EXCLUDED
```gitignore
# Generated content - NEVER commit
generated/
**/generated/
**/.openapi-generator/
*.generated.*
*_generated.*
**/*_pb.*
**/*.pb.go
```

#### Android Build Artifacts
```gitignore
# Android binary files
*.apk
*.aab
*.ap_
*.dex
*.hprof
*.class
*.so
*.dll
```

### Repository Cleanup Process

**When you find binary files in git history:**

1. **Immediate cleanup**: `git rm -r --cached path/to/binaries/`
2. **Fix .gitignore**: Add comprehensive patterns to prevent recurrence
3. **Verify exclusion**: `git status` should show binary directories as untracked
4. **Commit cleanup**: Document what was removed and why

### Critical Gitignore Patterns

```gitignore
# Build systems
**/.gradle/
**/build/
**/target/
**/dist/
**/node_modules/

# Generated content
generated/
**/generated/
*.generated.*

# Binary files
*.bin
*.dat
*.cache
*.dump
*.exe
*.dll
*.so
*.dylib

# IDE files
.idea/
.vscode/
*.iml

# OS files
.DS_Store
Thumbs.db
```

### Git Repository Health Check

**Regular maintenance commands:**
```bash
# Find binary files in git
git ls-files | grep -E '\.(bin|lock|cache|dat)$'

# Check for large files
git ls-files | xargs ls -la | sort -k5 -rn | head -10

# Find generated directories
find . -name "generated" -type d
find . -name ".gradle" -type d
```

### Why This Matters

- **Repository size**: Binary files bloat git history permanently
- **Build reproducibility**: Generated files create inconsistent builds
- **Developer experience**: Slower clones, larger checkouts
- **CI/CD efficiency**: More data to transfer and cache
- **Merge conflicts**: Binary files create unsolvable conflicts

### Prevention Strategy

1. **Proactive .gitignore**: Add patterns before first commit
2. **Pre-commit hooks**: Validate no binary files are committed
3. **Regular audits**: Monthly check for binary file contamination
4. **Team education**: Everyone knows what NOT to commit

## Pre-commit Hooks

The project has pre-commit hooks managed by devenv for:

- `gofmt` - Go code formatting
- `golangci-lint` - Go linting
- `prettier` - Code formatting

Run hooks manually: `devenv shell -- pre-commit run`
