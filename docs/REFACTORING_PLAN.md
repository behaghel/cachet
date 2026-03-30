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
- [ ] `schemas/openapi.yaml`: rename `/healthz` to `/health`
- [ ] Add `VeriffSession.verification` sub-object: `liveness_score`, `overall_confidence`, `risk_score`, `timestamp`
- [ ] Add `VeriffSession.person.confidence` and `VeriffSession.document.authenticity`
- [ ] Reconcile `credentialSubject` to match code's nested structure: `personalData`, `verificationMetrics`, `evidence`
- [ ] Standardize casing: camelCase for VC fields (`verificationMethod` not `verification_method`)
- [ ] Fix OAuth `/oauth/token` to `application/x-www-form-urlencoded` per RFC 6749

### 1.2 Add missing schemas
- [ ] Verifier: `Pack`, `VerifyRequest`, `VerifyResponse` schemas
- [ ] Receipts-log: `/receipts/hash`, `/log/sth`, `/log/proof` request/response schemas
- [ ] Registry: document YAML manifest structure

### 1.3 Per-service spec generation
- [ ] Tag all paths by service (`tags: [verifier]`, `tags: [issuance-gateway]`, etc.)
- [ ] Add `schema:split` script using redocly to generate per-service specs into `api/`
- [ ] Delete hand-maintained `api/openapi.{verifier,registry,receipts}.yaml`
- [ ] Mark `api/` specs as generated (gitignored or `.generated` suffix)

### 1.4 Regenerate and close the loop
- [ ] Regenerate Go types via `oapi-codegen`
- [ ] Regenerate Kotlin types (consider lighter generator than openapi-generator-cli)
- [ ] CI check: verify generated types are imported by services and mobile code

---

## Phase 2: Backend foundations

### 2.1 Shared scaffolding in `services/common/`
- [ ] `common/server.go` — HTTP server builder (timeouts, chi, middleware stack, graceful shutdown)
- [ ] `common/health.go` — shared health handler returning `{"status":"ok","service":"...","version":"..."}`
- [ ] `common/logging.go` — zerolog setup, request-scoped logger middleware (injects request_id into context)
- [ ] `common/errors.go` — structured JSON error responses matching spec `Error{error, message, details}`
- [ ] Replace Chi `middleware.Logger` with zerolog-based request logger

### 2.2 Devenv modernisation
- [ ] Service list variable in `devenv.nix` — define once, derive all per-service scripts
- [ ] Devenv 2.x `ports.allocate` for all 4 services (verifier=8081, registry=8082, receipts=8083, issuance=8090)
- [ ] Derive `env.CACHET_*_PORT` vars from port values
- [ ] Shorten `enterShell` banner to ~5 lines, add `dev:help` script

### 2.3 Services use generated types
- [ ] All services import `generated/go/models` instead of redeclaring types
- [ ] Standardize all services on Pattern A: `main.go` + `server.go` + `server_test.go`
- [ ] Refactor `receipts-log` out of single `main.go`

---

## Phase 3: Issuance gateway refactoring

### 3.1 Domain extraction
- [ ] `internal/veriff/` — `VeriffSession`, `validateVeriffSession()`, webhook HMAC signature verification
- [ ] `internal/credential/` — VC construction, age calculation, quality-to-VC mapping
- [ ] `internal/oauth/` — token creation, JWT validation middleware
- [ ] `server.go` becomes pure HTTP wiring (~50 lines)

### 3.2 Security
- [ ] HMAC-SHA256 webhook signature verification (secret via config/DI)
- [ ] Fix session-to-credential binding: token's `sub`/`client_id` maps to specific Veriff session ID
- [ ] RSA signing key loaded from config, not generated at startup

### 3.3 Correctness
- [ ] Fix `calculateAge` leap year bug (month+day comparison, not `YearDay()`)
- [ ] OAuth endpoint: parse `application/x-www-form-urlencoded` (not JSON)
- [ ] Input validation: `format` enum, `client_id` non-empty, `session_id` non-empty
- [ ] Remove dead `accessTokens` map (JWT signature + expiry is sufficient)
- [ ] Bounded session store with TTL eviction (or interface for Redis/DB)

### 3.4 DI
- [ ] `NewServer(cfg ServerConfig)` with injectable signing key, session store, clock
- [ ] `SessionStore` interface for verified sessions
- [ ] Structured JSON error responses on all error paths

---

## Phase 4: Testing

### 4.1 Domain unit tests
- [ ] `validateVeriffSession` — table-driven tests covering every threshold boundary
- [ ] `calculateAge` — leap year edge cases
- [ ] Concurrent webhook + credential issuance (race detector)

### 4.2 Test infrastructure
- [ ] Test helpers: `newTestSession(opts ...func(*VeriffSession))` builder
- [ ] Tests inject lightweight deps (fixed key, fake store, fake clock) — no RSA keygen per test
- [ ] Add tests for receipts-log endpoints

### 4.3 CI quality gates
- [ ] Coverage floor (start at 40%, ratchet up)
- [ ] Error response tests: verify JSON shape matches `Error` schema
- [ ] Negative/edge cases: expired token, malformed JWT, unknown credential format

---

## Phase 5: Mobile alignment

### 5.1 Config module
- [ ] Create `shared/config/` module for environment-specific values
- [ ] Extract `baseUrl` from hardcoded IP to `BuildConfig.CACHET_BASE_URL` (default `10.0.2.2:8090` for emulator)
- [ ] Timeouts, feature flags in config

### 5.2 Type safety
- [ ] Use generated Kotlin types for `VerifiableCredential` and `CredentialSubject` (replaces `Map<String, JsonElement>`)
- [ ] Parse `issuanceDate`/`expirationDate` at deserialization (custom serializer or generated model)
- [ ] Replace hand-written `TokenRequest`/`TokenResponse` in `OpenID4VCIClient.kt` with generated models

### 5.3 Production readiness
- [ ] Fix OAuth content-type to `application/x-www-form-urlencoded` (coordinated with Phase 3.3)
- [ ] Remove quality tier recalculation in `CredentialQuality.kt` — read `verificationLevel` from credential
- [ ] Persist consent receipts via SQLDelight (same pattern as `CredentialRepositoryImpl`)
- [ ] Wire `HttpTransparencyLogRepository` in production DI (replace mock)
- [ ] HTTP resilience: configure timeouts, add retry for transient failures

---

## Phase 6: Observability

### 6.1 Logging & health
- [ ] Request-scoped zerolog with `request_id` in context — all handlers use `log.Ctx(r.Context())`
- [ ] Structured health: `/health` (liveness) + `/ready` (dependency checks)
- [ ] Update health endpoint in spec

### 6.2 OpenTelemetry
- [ ] OTLP exporter with Cloud Run native support
- [ ] Chi OTEL middleware for request span creation and propagation
- [ ] Custom metrics: credential issuance rate, webhook processing, quality tier distribution

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
