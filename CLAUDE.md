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

## Pre-commit Hooks

The project has pre-commit hooks managed by devenv for:

- `gofmt` - Go code formatting
- `golangci-lint` - Go linting
- `prettier` - Code formatting

Run hooks manually: `devenv shell -- pre-commit run`
