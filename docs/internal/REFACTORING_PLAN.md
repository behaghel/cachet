# Cachet Refactoring Plan

Outcome of a global code review (March 2026) covering: simplicity, software design, spec/code alignment, best practices, testability, observability, and build reproducibility.

## Status legend

- `[ ]` — not started
- `[~]` — in progress
- `[x]` — done
- `[—]` — dropped / no longer relevant

---

## Phase 0: Cleanup (no dependencies)

### 0.1 Remove skeleton services
- [x] Delete `services/connector-hub/`, `services/transparency-log/`, `services/vouching-service/` source and Dockerfiles
- [x] Remove them from `devenv.nix` scripts (`ci:deps`, `ci:lint`, `ci:security`)
- [x] Remove them from CI container build job (job removed entirely — Dockerfiles are the build path)
- [x] Update `ARCHITECTURE.md` to flag Connector Hub, Vouching Service, and standalone Transparency Log as **planned, not yet implemented**

### 0.2 Delete dead tests
- [x] Delete `tests/schema-integration/schema_compatibility_test.go` (always skipped, contained bugs)
- [x] Replace with structural schema compatibility check: `test:schema-integration` now verifies generated Go types compile and generated Kotlin compiles against mobile

### 0.3 Mobile quick fixes
- [x] Delete duplicate `mobile/shared/src/commonMain/kotlin/id/cachet/wallet/network/model/NetworkModels.kt`
- [x] Remove `println("DEBUG: ...")` and unused `json` field from `OpenID4VCIClient.kt`

### 0.4 DevEx quick fixes
- [x] Remove Nix `containers` block from `devenv.nix` — Dockerfiles are the container build path
- [x] Fix `lint:go` script: removed `|| true`, now runs per-service properly
- [x] Add `-race` flag to all `go test` invocations in `ci:test`, `test:all`, `test:coverage`
- [x] Remove duplicate `yamllint` from `devenv.nix` packages
- [x] Fix `ci:security` script: use subshells `(cd ...)` instead of bare `cd` chaining
- [x] Remove AndroidManifest.xml debug step from `.github/workflows/ci.yml`
- [x] Fix coverage upload to include issuance-gateway
- [x] Remove container build CI job; integration job now depends on backend directly

### 0.5 Documentation accuracy
- [x] Mark aspirational vs implemented in `ARCHITECTURE.md` (status key + per-service markers + security note)
- [x] Mark SDK stubs (`sdk/kotlin/`, `sdk/swift/`) as placeholder with link to refactoring plan

---

## Phase 1: Spec — single source of truth

### 1.1 Fix the central spec
- [x] `schemas/openapi.yaml`: rename `/healthz` to `/health`
- [x] Add `VeriffSession.verification` sub-object: `liveness_score`, `overall_confidence`, `risk_score`, `timestamp`
- [x] Add `VeriffSession.person.confidence` and `VeriffSession.document.authenticity`
- [x] Reconcile `credentialSubject` to match code's nested structure: `personalData`, `verificationMetrics`, `evidence`
- [x] Standardize casing: camelCase for VC fields (`verificationMethod` not `verification_method`)
- [x] Fix OAuth `/oauth/token` to `application/x-www-form-urlencoded` per RFC 6749

### 1.2 Add missing schemas
- [x] Verifier: `Pack`, `VerifyRequest`, `VerifyResponse` schemas
- [x] Receipts-log: `/receipts/hash`, `/log/sth`, `/log/proof` request/response schemas
- [x] Registry: document YAML manifest structure

### 1.3 Per-service spec generation
- [x] Tag all paths by service (`tags: [verifier]`, `tags: [issuance-gateway]`, etc.)
- [x] Add `schema:split` script — YAML→JSON→jq filtering→YAML per service tag
- [x] Delete hand-maintained `api/openapi.{verifier,registry,receipts}.yaml`
- [x] Mark `api/` specs as generated (gitignored or `.generated` suffix)

### 1.4 Regenerate and close the loop
- [x] Regenerate Go types via `oapi-codegen`
- [x] Regenerate Kotlin types (consider lighter generator than openapi-generator-cli)
- [x] CI check: verify generated types are imported by services and mobile code

---

## Phase 2: Backend foundations

### 2.1 Shared scaffolding in `services/common/`
- [x] `common/server.go` — HTTP server builder (timeouts, chi, middleware stack, graceful shutdown)
- [x] `common/health.go` — shared health handler returning `{"status":"ok","service":"...","version":"..."}`
- [x] `common/logging.go` — zerolog setup, request-scoped logger middleware (injects request_id into context)
- [x] `common/errors.go` — structured JSON error responses matching spec `Error{error, message, details}`
- [x] Replace Chi `middleware.Logger` with zerolog-based request logger

### 2.2 Devenv modernisation
- [x] Devenv 2.x `ports.allocate` for all 4 services (verifier=8081, registry=8082, receipts=8083, issuance=8090)
- [x] Derive `env.CACHET_*_PORT` vars from port values
- [x] Service list variable in `devenv.nix` — define once, derive all per-service scripts
- [x] Shorten `enterShell` banner to ~5 lines, add `dev:help` script

### 2.3 Services use generated types
- [x] Standardize all services on Pattern A: `main.go` + `server.go` + `server_test.go`
- [x] Refactor `receipts-log` out of single `main.go`
- [x] Verifier imports `generated/go/models` for Pack, VerifyRequest, VerifyResponse
- [x] Receipts-log imports `generated/go/models` for ReceiptHashRequest, ReceiptHashResponse, etc.
- [x] Issuance-gateway imports `generated/go/models` for CredentialRequest, CredentialResponse, TokenResponse

---

## Phase 3: Issuance gateway refactoring

### 3.1 Domain extraction
- [x] `internal/veriff/` — Session, ValidateSession, SessionStore interface + InMemoryStore
- [x] `internal/credential/` — VC builder, CalculateAge (leap year fixed)
- [x] `internal/oauth/` — IssueToken, ValidateBearer
- [x] `server.go` reduced from 500 LOC to ~170 LOC of pure HTTP wiring

### 3.2 Security
- [x] HMAC-SHA256 webhook signature verification (via X-HMAC-Signature header, VERIFF_WEBHOOK_SECRET env)
- [x] Session binding via token session_id claim (with temporary fallback)
- [x] RSA signing key injectable via ServerConfig

### 3.3 Correctness
- [x] Fix `calculateAge` — month+day comparison, not `YearDay()`
- [x] OAuth endpoint parses `application/x-www-form-urlencoded` per RFC 6749
- [x] Input validation: `format` enum, `client_id` required, `session_id` required
- [x] Dead `accessTokens` map removed
- [x] Thread-safe session store (sync.RWMutex)
- [x] Bounded session store with TTL eviction (max 1000, 1h TTL)

### 3.4 DI
- [x] `NewServerWithConfig(cfg ServerConfig)` with injectable signing key, session store
- [x] `SessionStore` interface for verified sessions
- [x] Structured JSON error responses on all error paths

---

## Phase 4: Testing

### 4.1 Domain unit tests
- [x] `ValidateSession` — 10 table-driven tests covering all thresholds (done in Phase 3)
- [x] `CalculateAge` — leap year edge cases (done in Phase 3)
- [x] `-race` flag on all test invocations (done in Phase 0)

### 4.2 Test infrastructure
- [x] Tests inject lightweight deps via `ServerConfig` (done in Phase 3)
- [x] `testServer(t)` helper in issuance-gateway tests (done in Phase 3)
- [x] Add tests for receipts-log: 6 tests covering all endpoints
- [x] Store tests: TTL eviction, max-size eviction, FindFirst skips expired

### 4.3 CI quality gates
- [x] Coverage floor at 50% in `ci:test` — verifier 78%, registry 58%, receipts 80%, issuance 69%
- [x] Structured error response tests (invalid JSON, missing fields, bad format)
- [x] HMAC signature verification tests (valid, missing, invalid)

---

## Phase 5: Mobile alignment

### 5.1 Config module
- [x] `shared/config/AppConfig.kt` — central config object with `configure()` and `reset()`
- [x] Extract `baseUrl` from hardcoded IP to `BuildConfig.CACHET_BASE_URL` (default `10.0.2.2:8090` for emulator)
- [x] Timeouts configurable via `AppConfig.requestTimeoutMs`

### 5.2 Type safety
- [x] Typed `CredentialSubject` data class matching OpenAPI spec (replaces `Map<String, JsonElement>`)
- [x] Typed `PersonalData`, `VerificationMetrics`, `VerificationEvidence` for nested fields
- [x] `extractQuality()` uses typed fields instead of JSON map access

### 5.3 Production readiness
- [x] OAuth content-type `application/x-www-form-urlencoded` (submitForm in KtorOpenID4VCIClient)
- [x] Quality tier recalculation removed — `CredentialQuality.kt` reads backend-determined values
- [x] Consent receipts persisted via SQLDelight (`SqlDelightConsentReceiptRepository`)
- [x] `HttpTransparencyLogRepository` wired in production DI (replaces mock)
- [x] HTTP resilience: `HttpTimeout` (connect 10s, socket 15s, request 30s) + `HttpRequestRetry` (2 retries, exponential backoff)

---

## Phase 6: Observability

### 6.1 Logging & health
- [x] Request-scoped zerolog with `request_id` in context — all handlers use `log.Ctx(r.Context())`
- [x] Structured health: `/health` (liveness) + `/ready` (dependency checks)
- [x] Update health endpoint in spec

### 6.2 OpenTelemetry
- [x] OTLP exporter with Cloud Run native support
- [x] Chi OTEL middleware for request span creation and propagation
- [x] Custom metrics: `cachet.credentials.issued`, `cachet.webhooks.received`, `cachet.webhooks.stored`, `cachet.quality_tier` (with format/tier/status attributes)

---

## Dependency graph

```
Phase 0 (cleanup)
    └── no dependencies, start immediately

Phase 1 (spec)
    └── depends on 0.1 (skeleton removal) for clean path list

Phase 2 (backend foundations)
    └── depends on Phase 1 (generated types must reflect fixed spec)

Phase 3 (issuance gateway)
    └── depends on 2.1 (common scaffolding) and 2.3 (generated types)

Phase 4 (testing)
    └── depends on 3.1 (domain extraction) and 3.4 (DI)

Phase 5 (mobile)
    └── depends on Phase 1 (spec) and 3.3 (OAuth form-encoded)

Phase 6 (observability)
    └── depends on 2.1 (common scaffolding provides middleware slot)
```
