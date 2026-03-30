# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Skills

Use [$devenv-project-workflow](/Users/hubertbehaghel/.codex/skills/devenv-project-workflow/SKILL.md) for any `devenv` scaffolding, maintenance, or troubleshooting work in this repo.

Project-specific addenda for `$devenv-project-workflow`:

- All non-trivial project commands must run inside `devenv shell` or be prefixed with `devenv shell --`.
- Preserve the existing `devenv.nix` process model, Android SDK setup, and SecretSpec-based secrets workflow.
- Prefer existing scripts over inventing new entrypoints: `dev:services`, `dev:stop`, `dev:env:bootstrap` (alias `dev:secrets:bootstrap`), `dev:devenv:diagnose`, `fmt:go`, `lint:go`, `test:all`, `test:coverage`, `test:integration`, `android:*`, `schema:*`, and `ci:full`.
- For agent-driven verification, prefer non-interactive commands such as `devenv shell -- ci:full` or narrower task scripts. Do not use `devenv up` as an agent verification path.
- Treat `devenv shell -- ci:full` as the closest current full-project health gate. If you improve the workflow, keep it aligned with unit tests, integration tests, schema checks, Android unit tests, and coverage expectations.
- Keep service boot and validation logic aligned with the repo's existing health checks and scripts. If you add new health automation, it must use `/health`, never `/healthz`.
- Respect current service ports and local wiring: Verifier `8081`, Registry `8082`, Receipts `8083`, Issuance Gateway `8090`, and Android emulator access via `10.0.2.2:8090`.
- Preserve SecretSpec integration across local and deployed flows. Changes to secrets/bootstrap/deploy behavior should continue to work with `.env` locally and Secret Manager in GCP.

Use [$spec-driven-tdd](/Users/hubertbehaghel/.codex/skills/spec-driven-tdd/SKILL.md) for behavior changes that should start from an explicit spec and proceed via vertical-slice TDD.

Project-specific addenda for `$spec-driven-tdd`:

- This repo does not currently have a `spec/` tree. When a change needs specification-first work, create or extend `spec/` before implementation rather than burying the new behavior in ad hoc notes.
- Use existing artifacts as source material when drafting specs: OpenAPI files in `api/`, architecture docs in `docs/`, trust-pack docs in `docs/PACKS/`, receipts examples in `docs/RECEIPTS/`, and mobile/backend code paths under `mobile/` and `services/`.
- Follow the repo's idiomatic test layout. For Go services, prefer package-local `*_test.go` files instead of forcing a top-level `test/` directory.
- Define vertical slices as full request or user flows through the relevant surface. For service work, that usually means contract or request shape, handler/service behavior, and verification of the observable API result. For mobile work, include the emulator-backed flow where applicable.
- Any spec or test updates involving health checks must use `/health`. The `/healthz` path is forbidden here for architectural reasons and is guarded by `scripts/check-healthz.sh`.

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

## Pre-commit Hooks

The project has pre-commit hooks managed by devenv for:

- `gofmt` - Go code formatting
- `golangci-lint` - Go linting
- `prettier` - Code formatting

Run hooks manually: `devenv shell -- pre-commit run`
