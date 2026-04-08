# Contributing to Cachet

Welcome to Cachet. This guide covers everything you need to go from zero to a merged PR.

## Prerequisites

- [devenv](https://devenv.sh) -- manages Go, Android SDK, Node, pre-commit hooks, and all tooling
- Git
- An Android emulator (or a connected device) for mobile work

## Getting started

```bash
git clone <repo-url> && cd cachet
devenv shell                              # enter dev environment
direnv allow                              # trust .envrc (one-time)
```

If shell startup seems stuck, run `./scripts/diagnose-devenv-shell.sh` to diagnose.

### Running the project

```bash
devenv shell -- dev:services              # start all backend services
devenv shell -- android:emulator          # create and start Android emulator
devenv shell -- android:run               # build + install + launch the app
```

### Running tests

```bash
devenv shell -- test:all                  # Go unit tests for all services
devenv shell -- test:integration          # integration tests
devenv shell -- test:coverage             # unit tests with coverage reports
devenv shell -- android:test-unit         # Android/KMM unit tests
devenv shell -- android:test              # Android instrumented tests (requires emulator)
```

### Service ports

| Service | Port |
|---------|------|
| Verifier | 8081 |
| Registry | 8082 |
| Receipts Log | 8083 |
| Issuance Gateway | 8090 |

## Development workflow

### 1. Branch from `main`

```bash
git checkout -b feature/your-feature main
```

One focus per branch. A branch that fixes a bug and adds a feature is two branches.

### 2. Schema first

All API changes start with the OpenAPI specification, not the code:

```bash
# 1. Edit the spec
$EDITOR schemas/openapi.yaml

# 2. Validate
devenv shell -- schema:validate

# 3. Generate types
devenv shell -- schema:generate

# 4. Sync generated models into codebase
devenv shell -- schema:sync

# 5. Run schema tests
devenv shell -- schema:test
```

Never hand-edit generated files. If the generated output is wrong, fix the schema.

### 3. Write tests first

Follow TDD: write a failing test that describes the behaviour, then implement the minimum code to make it pass, then refactor.

### 4. Format and lint

```bash
devenv shell -- fmt:go                    # format Go code
devenv shell -- lint:go                   # lint with golangci-lint
devenv shell -- pre-commit run            # run all pre-commit hooks
```

Run these before every push. CI will catch what you miss, but it's faster to catch it locally.

### 5. Commit with intention

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(wallet): add consent receipt display
fix(verifier): correct predicate evaluation for age ranges
refactor(issuance): extract credential builder into domain layer
docs: update verification protocol with relay TTL
```

Rules:
- **One concern per commit.** Don't mix a bug fix with a refactor.
- **Explain why, not what.** The diff shows what changed; the message should say why.
- **Never commit secrets.** `.env`, credentials, API keys -- these stay out of version control.

### 6. Open a pull request

- Keep PRs focused and reviewable -- ideally under 400 lines changed
- Write a clear description: what changed, why, and how to test it
- Link related issues
- Ensure all CI checks pass before requesting review

## Quality gates

Every PR must pass before merge:

| Gate | What it checks |
|------|----------------|
| Schema validation | OpenAPI spec is valid, generated models in sync |
| Unit tests | `test:all` and `android:test-unit` green |
| Integration tests | `test:integration` green |
| Linting | `golangci-lint`, `prettier`, `gofmt` clean |
| Security | No new vulnerabilities (Semgrep, gosec) |
| Pre-commit hooks | All hooks pass (`pre-commit run`) |

## Health endpoints

**Use `/health`, never `/healthz`.** Cloud Run intercepts `/healthz` before it reaches our services. Pre-commit hooks enforce this. See [docs/HEALTH_ENDPOINTS.md](docs/HEALTH_ENDPOINTS.md) for the full explanation.

## Project layout

```
services/
  verifier/              # Cach'Pack presentation validation
  registry/              # Policy and pack definitions
  receipts-log/          # Consent receipts + transparency log
  issuance-gateway/      # Veriff webhook -> SD-JWT VC issuance
  common/                # Shared Go module (server, logging, errors)
mobile/
  androidApp/            # Android wallet (Jetpack Compose)
  shared/                # KMM shared logic (networking, data, domain)
api/                     # OpenAPI 3.0.3 specifications
schemas/                 # Source-of-truth OpenAPI for code generation
sdk/                     # TypeScript, Kotlin, Swift SDK stubs
design/
  wireframes/            # UX wireframes (SVG)
  badges/                # Cachet badge assets
  logo/                  # Brand identity
docs/                    # Project reference documentation
  internal/              # Roadmaps, plans, and status trackers
  PACKS/                 # Cach'Pack definitions (JSON, with jurisdiction variants)
  RECEIPTS/              # Sample consent receipts
```

## Key terminology

If you're new to the project, these terms appear everywhere:

| Term | Meaning |
|------|---------|
| **Cachet** | A time-boxed, contextual verification credential |
| **Cach'Pack** | A reusable bundle of checks for a context (e.g., "Childcare Readiness") |
| **Predicate** | A proven property (`age >= 18`) without the raw attribute (birthdate) |
| **Holder** | The person being verified -- their credentials live on their device |
| **Verifier** | The person demanding trust -- they initiate verification |
| **Consent Receipt** | Signed record of what was proven, to whom, and why |

## Getting help

- **Architecture questions**: start with [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- **Protocol questions**: start with [docs/VERIFICATION_PROTOCOL.md](docs/VERIFICATION_PROTOCOL.md)
- **Business context**: see [docs/VISION.md](docs/VISION.md) and [docs/BUSINESS_MODEL.md](docs/BUSINESS_MODEL.md)
- **Stuck?** Open an issue describing what you tried and where you got blocked
