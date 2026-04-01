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

For detailed architecture, service structure, data flows, and key concepts, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

Quick reference for service locations:

| Service | Code | Endpoints |
|---------|------|-----------|
| Verifier | `services/verifier/` | `/packs` (GET), `/presentations/verify` (POST) |
| Registry | `services/registry/` | `/policy/manifest` (GET) |
| Receipts Log | `services/receipts-log/` | Consent receipts + transparency log |
| Issuance Gateway | `services/issuance-gateway/` | `/oauth/token`, `/credential`, `/webhooks/veriff` |
| Mobile Wallet | `mobile/` | KMM + Jetpack Compose; connects to backend via `10.0.2.2:8090` |

### Key files

- Trust Pack definitions: `docs/PACKS/`
- Receipt samples: `docs/RECEIPTS/`
- Policy manifest: `docs/POLICY_MANIFEST.yaml`
- Architecture: `docs/ARCHITECTURE.md`
- Vision and product context: `docs/VISION.md`

## Pre-commit Hooks

The project has pre-commit hooks managed by devenv for:

- `gofmt` - Go code formatting
- `golangci-lint` - Go linting
- `prettier` - Code formatting

Run hooks manually: `devenv shell -- pre-commit run`
