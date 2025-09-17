# AGENTS.md — Operating Guide for Automation

This repository is wired for deterministic builds and tightly managed tooling. Any agent automation must preserve those guarantees by following the practices below.

## 1. Always Work Inside `devenv`
- **All commands run through `devenv shell -- …`** so the same nix-managed toolchain is used locally, in CI, and in production builds.
- To open an interactive shell: `devenv shell`
- Backend helpers:
  - `devenv shell -- dev:services` — start core services (verifier, registry, receipts, issuance)
  - `devenv shell -- dev:stop` — stop running services
  - `devenv shell -- fmt:go` / `lint:go` / `test:all` / `test:integration`
- Android helpers:
  - `devenv shell -- android:emulator`, `android:build`, `android:test`, etc.
- Never call `go test`, `gradlew`, or container tooling outside `devenv`; doing so bypasses pinned dependencies and breaks reproducibility.
- Configuration is loaded from `config/app-config.json`; Go services read it via `services/common/config` and mobile builds consume it through Gradle. Override with `CACHET_CONFIG_PATH` only when needed.
- Select environments with `CACHET_ENV` (backend/services) and Gradle property `-PcachetEnv=<env>`; all environments are declared once in `config/app-config.json` to keep tiers aligned.

## 2. Reproducible Build Doctrine
- Treat local runs as rehearsals for staging/production. Use the same scripts CI uses (`devenv shell -- ci:*`, container commands, etc.).
- Containers:
  - Build with `devenv container build <service>`
  - Push with `devenv container --registry docker://gcr.io/$PROJECT_ID/ copy <service>` (registry URL is the base only).
  - Service names must match Cloud Run names; ports must align (`PORT` env defaults documented in `devenv.nix`).
- Never edit generated artifacts or commit build outputs. If something must be regenerated, run the generator inside `devenv` and keep results out of git.
- When verifying changes, prefer scripted `devenv` tasks over ad-hoc commands. Document any deviation.

## 3. Health Endpoints
- **Use `/health`, never `/healthz`.** Cloud Run intercepts `/healthz`, so hitting it will return Google 404 pages. Scripts and services must expose `/health` only.

## 4. Secret and Configuration Management
- Secrets are defined centrally in `secretspec.toml` and accessed via SecretSpec.
  - Local: `devenv shell -- secretspec run --provider dotenv -- <command>`
  - CI: `devenv shell -- secretspec run --provider env --profile ci -- <command>`
  - Production: GCP Secret Manager via Cloud Run `--set-secrets`
- Never mix raw environment secrets or ad-hoc `.env` files. If a new secret is required, add it to `secretspec.toml` and the relevant profile.

## 5. Code & Git Hygiene Expectations
- Keep commits small, atomic, and conventionally labeled (`type(scope): message`). Each commit should build and test cleanly.
- Do not stage unrelated files. Generated directories (`build/`, `generated/`, `.gradle/`, etc.) stay out of git.
- Run formatters/linters via `devenv shell -- fmt:go`, `lint:go`, or equivalent language tools before committing.
- Pre-commit hooks are available: `devenv shell -- pre-commit run`.

## 6. Service & Data Model Overview
- **Verifier** (`services/verifier/`): serves Trust Pack lists and verifies presentations.
- **Registry** (`services/registry/`): serves policy manifests.
- **Receipts log** (`services/receipts-log/`): consent receipts + transparency log plumbing.
- **Issuance gateway** (`services/issuance-gateway/`): OpenID4VCI issuer, Veriff webhook intake.
- Mobile app is Kotlin Multiplatform (shared module + `androidApp/`). Networking hits the issuance gateway at `http://10.0.2.2:8090` by default for emulator parity.

## 7. Quick Reference Commands
| Task | Command |
| --- | --- |
| Start services | `devenv shell -- dev:services` |
| Stop services | `devenv shell -- dev:stop` |
| Go tests (all) | `devenv shell -- test:all` |
| Go lint | `devenv shell -- lint:go` |
| Android build | `devenv shell -- android:build` |
| Android unit tests | `devenv shell -- android:test-unit` |
| Run pre-commit hooks | `devenv shell -- pre-commit run` |
| Container build | `devenv container build <service>` |
| Container push | `devenv container --registry docker://gcr.io/$PROJECT_ID/ copy <service>` |

## 8. Before Submitting Changes
- Ensure relevant tests pass using the scripted commands (backend + mobile where applicable).
- Verify secrets remain referenced through SecretSpec and that no confidential values are committed or logged.
- Confirm health endpoints remain `/health`.
- Double-check that generated/build artifacts are not staged.
- Document any deviations from the standard scripts in PR/commit descriptions.

Adhering to the above keeps local, staging, and production environments in lockstep and prevents subtle drift that undermines trust in the build pipeline.
