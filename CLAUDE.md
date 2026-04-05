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

## Architecture

### Service Structure

Cachet is a microservices-based trust provider with three core services:

1. **Verifier** (`services/verifier/`) - Manages Cach'Pack lists and verifies credential presentations
   - Endpoints: `/packs` (GET), `/presentations/verify` (POST)
   - Returns cachets and predicates

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

- **Cach'Packs**: Reusable, privacy-preserving credential templates (e.g., "Childcare Readiness", "Safe Seller")
- **Presentations**: Verifiable credential bundles verified against policies
- **Policy Manifests**: DID-signed policy definitions with versioning

### Data Flow

1. **Issuance**: Veriff webhook → Issuance Gateway → SD-JWT VC issued via OpenID4VCI
2. **Verification**: Clients request available Cach'Packs from Verifier
3. **Presentation**: Credential presentations are verified against registered policies
4. **Results**: Verification results include cachets, predicates, and freshness status
5. **Registry**: Provides policy manifests for Cach'Pack definitions

### Mobile Wallet

- **Location**: `mobile/` - Kotlin Multiplatform Mobile (KMM) wallet app
- **Shared module**: `mobile/shared/` - Business logic, networking, data models
- **Android app**: `mobile/androidApp/` - Android-specific UI and platform integrations
- **Features**: OpenID4VCI credential issuance, SQLite credential vault, Jetpack Compose UI
- **Networking**: Uses `10.0.2.2:8090` to connect to local backend from emulator

### Development Files

- Cach'Pack definitions: `docs/PACKS/`
- Receipt samples: `docs/RECEIPTS/`
- Policy manifest: `docs/POLICY_MANIFEST.yaml`
- Architecture docs: `docs/ARCHITECTURE.md`, `docs/TRANSPARENCY_LOG_DESIGN.md`

## Security Guidelines

This project handles verifiable credentials and identity data. All code changes must follow:

1. **Never hardcode secrets** — use secretspec/env vars (see `.env.ci` for CI pattern)
2. **Validate all input** — especially JWT claims, webhook payloads, credential presentations
3. **Local-first verification** — the verifier never trusts the relay/transport layer (see `docs/VERIFICATION_PROTOCOL.md`)
4. **Minimal PII logging** — never log credential contents, only session IDs and status codes
5. **Use `/health` not `/healthz`** — Cloud Run intercepts `/healthz`
6. **Run `ci:security` before merging** — gosec must pass clean
7. **HMAC-verify webhooks** — Veriff webhook payloads must be HMAC-verified, fail-closed (reject if secret unset)
8. **SD-JWT cryptographic verification** — never trust credential fields by string matching; verify issuer JWS signature, KB-JWT holder binding, nonce, and audience
9. **Hardware-backed keys** — holder signing keys must be StrongBox/Secure Enclave backed; no software-only keys for credential operations

Security specification: `docs/VERIFICATION_PROTOCOL.md` (threat model in Section 3, crypto requirements in Section 5)

### Security Tooling

- **CI:** `ci:security` runs gosec on all Go services. The `security-review.yml` GitHub Action runs AI-powered security review on every PR.
- **On-demand:** Run `/security-review` in Claude Code for a comprehensive security assessment of current changes.
- **MCP:** Semgrep MCP available for real-time SAST — run `claude mcp add semgrep -- uvx semgrep-mcp` to enable.

## Pre-commit Hooks

The project has pre-commit hooks managed by devenv for:

- `gofmt` - Go code formatting
- `golangci-lint` - Go linting
- `prettier` - Code formatting

Run hooks manually: `devenv shell -- pre-commit run`
