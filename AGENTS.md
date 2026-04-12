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

**Note:** The `$spec-driven-tdd` skill is retired. Its functionality is now split across four Claude Code plugins enabled in `devenv.nix`:

- **spec-driven** — Spec collection (`/spec-driven:collect-spec`), verification, adversarial review (`spec-challenger`)
- **spec-tdd** — Iterative vertical-slice TDD (`/spec-tdd:plan`, `/spec-tdd:iterate`, `tdd-coach`)
- **domain-tree** — Domain-driven structure (`/domain-tree:init`, `/domain-tree:check`, `boundary-enforcer`)
- **ux-stories** — User-story-driven UX (`/ux-stories:write`, `/ux-stories:scenarios`, `/ux-stories:deliver`, `story-guardian`)

The `spec/` tree is now established with `spec/domains.yaml`, behavioral specs, user stories, BDD scenarios, and personas. See `CLAUDE.md` for workflow details.

Project-specific addenda:

- Follow the repo's idiomatic test layout. For Go services, prefer package-local `*_test.go` files instead of forcing a top-level `test/` directory.
- Define vertical slices as full request or user flows through the relevant surface.
- Any spec or test updates involving health checks must use `/health`. The `/healthz` path is forbidden here for architectural reasons and is guarded by `scripts/check-healthz.sh`.
- UX changes require a user story in `spec/{domain}/stories/` before implementation.
- Core domain changes require a behavioral spec in `spec/{domain}/spec.md` before implementation.
- Shared kernel changes require notification of all consumers listed in `spec/domains.yaml`.

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
