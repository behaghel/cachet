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

For detailed architecture, service structure, data flows, and key concepts, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

Service ports: Verifier `8081`, Registry `8082`, Receipts `8083`, Issuance Gateway `8090`. Mobile emulator connects via `10.0.2.2:8090`.

Key files: `docs/PACKS/` (Trust Pack definitions), `docs/RECEIPTS/` (samples), `docs/POLICY_MANIFEST.yaml`, `docs/VISION.md` (product context).

## Pre-commit Hooks

The project has pre-commit hooks managed by devenv for:

- `gofmt` - Go code formatting
- `golangci-lint` - Go linting
- `prettier` - Code formatting

Run hooks manually: `devenv shell -- pre-commit run`
