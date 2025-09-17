# Cachet Foundations Audit (v0.5)

## Executive Overview
- The shipping artifacts diverge sharply from the `PRODUCT_BRIEF.md` vision: most microservices are stubs, the proof/verifier flows do not exist, and real privacy guarantees are not yet implemented. Focus on delivering a smallest end-to-end credential issuance + verification loop before widening scope.
- Security posture is currently unsafe. Webhook secrets are logged, signature checks are bypassed, the "privacy vault" uses mock crypto, and mobile defaults to an unowned ngrok endpoint. These must be remediated before exposing any traffic.
- Developer ergonomics suffer from misconfigured ports, hard-coded local IPs, tracked build artifacts, and skipped tests. Cleaning these up will unblock reliable CI and reproducible local environments.
- Recommended first milestones: harden the issuance gateway, produce a functional verifier end-point aligned with the brief, remove mock cryptography, and re-establish clean repo/test baselines.

## Product & Architecture Alignment
### Findings
- The product brief promises Trust Packs, verifiable policy enforcement, transparency logging, and vouching; in code, only the verifier stub exposes `/packs` with two static entries and no badge logic (`services/verifier/server.go:24-87`).
- Registry, receipts log, connector hub, transparency log, and vouching services all return "ok" health responses with no domain APIs (`services/registry/server.go:17-55`, `services/receipts-log/main.go:12-51`, `services/connector-hub/main.go:11-35`, `services/transparency-log/main.go:11-35`, `services/vouching-service/main.go:11-35`).
- Mobile UX assumes rich credential quality data (`mobile/androidApp/src/main/kotlin/id/cachet/wallet/android/ui/CredentialsScreen.kt`) but backend never produces it.

### Suggestions
- Define a thin vertical slice: Veriff session creation → webhook processing → credential issuance → verifier badge evaluation. Remove or clearly mark placeholders until implemented to avoid false sense of completeness.
- Create service-specific READMEs aligning expected APIs with current implementation gaps so contributors know priorities.
- Freeze scope creep (e.g. “platinum” tiers, TEEs, SNARKs) until baseline flows are working, as per Phase A exit criteria in `PRODUCT_BRIEF.md`.

## Backend Services
### Findings
- Docker Compose maps host 8081/8082/8083 to container port 8080, but services listen on their own ports (8081/8082/8083), making local compose unusable (`infra/docker-compose.yaml:3-11`, `services/verifier/main.go:16-24`, `services/registry/main.go:16-24`, `services/receipts-log/main.go:36-51`).
- Connector Hub, Transparency Log, and Vouching services default to the same port 8090, guaranteeing clashes when run together (`services/connector-hub/main.go:19-23`, `services/transparency-log/main.go:19-23`, `services/vouching-service/main.go:19-23`).
- `services/issuance-gateway/server.go` is a 54k-line monolith with in-memory maps unsynchronised for concurrent access (`services/issuance-gateway/server.go:344-369`) and issues a credential to the first approved Veriff session regardless of the caller (`services/issuance-gateway/server.go:959-983`).

### Suggestions
- Align all service defaults with the ports declared in compose / Cloud Run (either change listeners to 8080 or fix the mappings).
- Introduce configuration per service (env or flags) so multiple processes can co-exist; treat port numbers as part of deployment contract and document them in `CLAUDE.md`/docs.
- Split issuance gateway functionality (OAuth, webhook ingestion, issuance) into smaller packages, add locks or dedicated stores for token/session state, and bind tokens to session IDs before credential creation.
- Add skeletal but real endpoints for registry/verifier/log services following the product APIs, even if returning mock data with TODOs.

## API & Schema Governance
### Findings
- The primary OpenAPI spec still advertises `/healthz` (`schemas/openapi.yaml:21-112`), violating the Cloud Run constraint noted in `CLAUDE.md`.
- Service-specific OpenAPI files are placeholders with no schemas (`api/openapi.verifier.yaml`, `api/openapi.registry.yaml`, `api/openapi.receipts.yaml`).
- Schema integration test harness is incomplete (`tests/schema-integration/schema_compatibility_test.go:195-206`), so contract drift goes unnoticed.

### Suggestions
- Update OpenAPI specs to `/health` and re-run codegen; add linting to block `/healthz` regressions (leverage `scripts/check-healthz.sh`).
- Flesh out OpenAPI schemas per service, then regenerate SDKs/server stubs from them to ensure parity between docs and code.
- Finish the `startTestServer` helper so schema tests run in CI; integrate them into `devenv shell -- test:all`.

## Security & Privacy
### Findings
- Webhook secret is logged in plaintext and signature failures are explicitly bypassed (`services/issuance-gateway/server.go:1087-1101`, `services/issuance-gateway/server.go:1394-1415`).
- Privacy vault crypto uses deterministic pseudo-random keys, XOR “encryption”, and hashCode-based digests while claiming AES-256-GCM (`mobile/shared/src/commonMain/kotlin/id/cachet/wallet/domain/crypto/PrivacyVault.kt:82-187`, `mobile/shared/src/commonMain/kotlin/id/cachet/wallet/domain/model/ConsentReceipt.kt:209-239`).
- Mobile hard-codes an external ngrok URL for backend access (`mobile/androidApp/src/main/kotlin/id/cachet/wallet/android/ui/VeriffIntegration.kt:21-111`) and a personal LAN IP for issuer API (`mobile/shared/src/commonMain/kotlin/id/cachet/wallet/shared/di/SharedModule.kt:37-53`).
- Tokens are not scoped to sessions; any bearer of a freshly minted token can receive another user’s latest approved session (`services/issuance-gateway/server.go:959-1004`).

### Suggestions
- Enforce webhook signature verification: stop logging secrets, remove bypass, validate against `sha256=` header, and respond 401 on mismatch. Document key rotation.
- Replace simulated crypto with platform primitives (e.g. Android `Cipher`, Kotlin Multiplatform `okio` + libsodium) or mark the vault as “design only” until real implementations land; do not ship mock encryption.
- Parameterise backend base URLs via config (build variants/gradle properties) and forbid committing personal tunnels; supply sample `.env.local` values.
- Bind OAuth tokens to specific Veriff session IDs and purge `verifiedSessions` after issuance; introduce persistence/TTL and concurrency-safe access (mutex or channel) before production exposure.

## Mobile & SDKs
### Findings
- Koin module injects `OpenID4VCIClient` pointed to `http://192.168.1.199:8090`, failing on other networks (`mobile/shared/src/commonMain/kotlin/id/cachet/wallet/shared/di/SharedModule.kt:37-53`).
- `KtorOpenID4VCIClient` prints raw OAuth payloads to stdout (`mobile/shared/src/commonMain/kotlin/id/cachet/wallet/network/OpenID4VCIClient.kt:70-87`).
- `IssuanceUseCase` fabricates UUIDs via random ints (not RFC4122) and swallows repository errors (`mobile/shared/src/commonMain/kotlin/id/cachet/wallet/domain/usecase/IssuanceUseCase.kt:14-54`).
- Instrumentation test expects text that the UI never renders (`mobile/androidApp/src/androidTest/kotlin/id/cachet/wallet/android/WalletVerificationFlowTest.kt:96-108` vs `mobile/androidApp/src/main/kotlin/id/cachet/wallet/android/ui/WalletApp.kt:118-135`).

### Suggestions
- Inject base URLs via `BuildConfig` / Koin parameters using SecretSpec profiles; default to emulator-friendly addresses (`10.0.2.2`).
- Replace `println` debug statements with structured logging guarded by build config flags; never log secrets.
- Use real UUID generators (`UUID.randomUUID()`) and let repository methods bubble up failures to the UI for retry messaging.
- Align UI copy with tests or update tests accordingly; add screenshot/UI assertions that match actual designs.

## Testing & Quality
### Findings
- Schema test suite is skipped (see above) and there is no automated verifier/registry coverage beyond basic handlers.
- Mobile shared tests still reference hand-written mocks even though an actual SQLDelight repository exists, and some helper builders set `issuanceDate` to an `Instant`, which will not compile on JVM (`mobile/shared/src/commonTest/kotlin/id/cachet/wallet/domain/repository/CredentialRepositoryTest.kt:29-74`).
- Webhook and credential issuance logic lack integration tests to detect regressions.

### Suggestions
- Prioritise end-to-end tests for issuance gateway (token → credential) and webhook validation with both happy path and bad signature cases.
- Update shared tests to use proper sample data (`Clock.System.now().toString()` etc.) and exercise `CredentialRepositoryImpl` via an in-memory SQLDelight driver.
- Add UI smoke tests for the Android app that mock backend responses via `MockWebServer` rather than sleeping.

## Infrastructure & DevOps
### Findings
- `devenv` scripts run sequential `cd` commands without subshell isolation; a failing command leaves later steps in the wrong directory (`devenv.nix:65-158`).
- Cloud Run / port conventions encoded in docs are not enforced in configs (see port issues above).
- SecretSpec lists required keys, but local code bypasses secrets or embeds defaults (`secretspec.toml`, `docs/SECRETS_MANAGEMENT.md` vs `services/issuance-gateway/server.go:1087-1097`).

### Suggestions
- Wrap per-service commands in subshells (`(cd ...)`) or use `just` recipes to avoid directory bleed; ensure CI reuses those commands.
- Add automated checks (pre-commit or CI) to verify docker-compose alignment (e.g. unit test reading env defaults).
- Wire SecretSpec loading into the Go services (e.g. fail fast when `VERIFF_WEBHOOK_SECRET` absent) and document local `.env.local` setup in README.

## Documentation & Knowledge Transfer
### Findings
- `README.md` offers a one-line quick start with no mention of SecretSpec, mobile prerequisites, or architecture topology.
- `docs/TRANSPARENCY_LOG_DESIGN.md` is a single sentence despite the prominence of transparency logs in the product pitch.
- CLAUDE guidelines are extensive but not mirrored for human contributors.

### Suggestions
- Expand `README.md` with environment setup (devenv, SecretSpec, mobile emulator), service diagrams, and links to key docs.
- Flesh out transparency log, pack registry, and issuer onboarding docs with current implementation status and next steps.
- Create a contributor-focused runbook derived from `CLAUDE.md` so expectations survive tool changes.

## Repository Hygiene & Operations
### Findings
- Large binaries and generated artifacts are committed: e.g. 10 MB `services/issuance-gateway/issuance-gateway`, `mobile/androidApp/build/**`, `mobile/shared/build/**`, OpenAPI generator scaffolds, and `coverage/*.out` despite `.gitignore` rules.
- A scaffold zip (`cachet_scaffold.zip`) remains in the repo.

### Suggestions
- Purge committed artifacts (`git rm --cached ...`) and ensure `.gitignore` patterns match; consider adding a pre-commit hook that blocks binaries >1 MB unless explicitly allowed.
- Store generated SDKs in release bundles, not source control; rely on generation scripts invoked via CI.
- Regularly run `git clean -Xdn` to verify ignore rules, and document clean-room clone/build expectations.

## Immediate Next Steps (Suggested Order)
1. Fix security-critical issues: webhook verification, removal of mock crypto, elimination of hard-coded external endpoints.
2. Correct port/config mismatches and remove committed build artifacts to stabilise local + CI environments.
3. Implement a minimal issuance→verification flow with real persistence, schema coverage, and integration tests.
4. Update OpenAPI specs / docs to reflect the functioning endpoints and wire schema checks into CI.
5. Iterate on mobile networking/configuration to align with the hardened backend.

