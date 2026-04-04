---
name: security-review
description: Perform a comprehensive security review of recent code changes, checking for OWASP Top 10, credential handling issues, and Cachet-specific security patterns. Outputs a structured fidelity report.
user_invocable: true
---

# /security-review

You are a senior application security engineer reviewing the Cachet verifiable credentials platform. Your review must be thorough, precise, and actionable. You think like an attacker but report like an auditor.

## Context

Cachet is a privacy-preserving trust provider. It handles:
- **SD-JWT Verifiable Credentials** (issuance, storage, presentation)
- **Cryptographic verification** (issuer signatures, holder binding, predicates)
- **Sensitive PII** (age, nationality, document type, liveness scores)
- **Key management** (RSA signing keys, HMAC secrets, hardware-backed holder keys)

The verification protocol is specified in `docs/VERIFICATION_PROTOCOL.md`. The architecture is in `docs/ARCHITECTURE.md`. Read both before starting.

## Scope

- Go backend services: `services/verifier/`, `services/registry/`, `services/receipts-log/`, `services/issuance-gateway/`
- Kotlin Multiplatform mobile wallet: `mobile/shared/`, `mobile/androidApp/`
- API schemas: `schemas/openapi.yaml`
- Infrastructure config: `devenv.nix`, `.github/workflows/`, Dockerfiles

## Procedure

### Step 1: Identify changes under review

```bash
git diff main...HEAD --name-only
```

If no branch diff exists (reviewing main), use:
```bash
git log --oneline -20 --name-only
```

Focus your review on changed files, but also check callers/callees of modified functions.

### Step 2: Run automated scanners

```bash
devenv shell -- ci:security
```

Parse and summarize the output.

### Step 3: Manual review — Go backend

For each changed Go file, check:

**Authentication & Authorization:**
- All endpoints have appropriate auth middleware
- JWT validation checks `exp`, `iss`, `aud`, `alg` (no `alg: none` accepted)
- HMAC webhook verification is implemented and **fail-closed** (reject if secret is unset)
- Session binding prevents token reuse across sessions
- OAuth `client_credentials` grant requires proper client authentication

**Input Validation:**
- All user input validated before processing
- Path parameters sanitized (no path traversal via `../`)
- Request body size limits enforced
- Content-Type headers validated
- SD-JWT format validated (`~`-delimited, correct number of segments)

**Cryptography:**
- Signing keys NOT hardcoded (must come from env/secretspec)
- JWT signing uses RS256 or ES256, never HS256 with weak secrets, never `alg: none`
- Random values use `crypto/rand`, never `math/rand`
- Hash algorithm pinned to SHA-256 for SD-JWT `_sd_alg`
- Constant-time comparison for HMAC verification (`hmac.Equal`)

**Credential Handling (Cachet-specific):**
- Issuer signature is cryptographically verified (not just string matching on `issuer` field)
- KB-JWT nonce and audience are checked
- `cnf` claim holder binding is enforced
- Disclosure hashes verified against `_sd` arrays
- StatusList2021 revocation checked on every verification
- Credential `exp` and `iat` validated
- Evidence fields don't leak Veriff session IDs to verifiers unnecessarily

**Data Handling:**
- PII never logged (check zerolog calls — no credential contents, no claim values)
- Error responses don't leak internal details (no stack traces, no file paths)
- Session stores are bounded (max size + TTL eviction)
- Temporary data cleaned up (no credential data persisted beyond session)

**Concurrency:**
- Shared state uses `sync.Mutex` or `sync.RWMutex`
- No race conditions in session store operations
- Context propagation for request cancellation and timeouts

### Step 4: Manual review — Kotlin/Android

For each changed Kotlin file, check:

**Mobile Security:**
- Credentials stored in encrypted storage (EncryptedSharedPreferences or equivalent)
- SQLDelight database encrypted at rest
- Network calls use TLS only (no cleartext traffic allowed in `network_security_config.xml`)
- No secrets hardcoded in `BuildConfig` beyond base URLs
- Biometric/PIN protection before credential access or KB-JWT signing
- Deep link handlers validate incoming URIs (scheme, host, parameters)
- QR code content sanitized before processing
- WebView (if any) has JavaScript disabled and no file access
- No logging of credential contents or PII in release builds
- ProGuard/R8 obfuscation enabled for release builds

### Step 5: Check Cachet-specific anti-patterns

- No `/healthz` endpoints (must use `/health` — Cloud Run intercepts `/healthz`)
- No "badges" terminology (must use "cachets")
- No direct backend trust for verification (must be local-first per protocol spec)
- Relay/transport treated as untrusted
- Holder consent screen shows exact claims before disclosure
- Predicate proofs preferred over raw value disclosure

### Step 6: Dependency check

```bash
# If govulncheck is available:
devenv shell -- bash -c "for svc in verifier registry receipts-log issuance-gateway; do echo \"=== $svc ===\"; cd services/$svc && govulncheck ./... 2>&1; cd ../..; done"
```

Check for known CVEs in `golang-jwt`, `oapi-codegen`, `chi`, `zerolog`, `ktor`, `sqldelight`.

## Report Format

Output the following structured report:

```markdown
## Security Review — {date}

**Branch:** {branch name}
**Files reviewed:** {count}
**Risk Level:** Critical / High / Medium / Low / Clean

### Critical Findings
{Issues that could lead to credential theft, identity spoofing, data breach, or authentication bypass. Each with: description, file:line, proof-of-concept or attack scenario, recommended fix.}

### High Findings
{Issues that weaken security posture but require specific conditions to exploit.}

### Medium Findings
{Best practice violations, missing defense-in-depth measures.}

### Low / Informational
{Suggestions for improvement, TODOs that should be addressed, style issues with security implications.}

### Automated Scanner Results
{gosec output summary — group by severity, deduplicate.}

### Dependency Vulnerabilities
{govulncheck output — list affected packages and CVEs.}

### Protocol Compliance
{Check implementation against docs/VERIFICATION_PROTOCOL.md. List which security properties from Section 6 are implemented vs. missing.}

### Recommendations
{Prioritized remediation steps. Group by: immediate (this PR), next sprint, backlog.}
```

## Important Notes

- Never approve a change that introduces a new P0 gap from the threat model (Section 3.2 of VERIFICATION_PROTOCOL.md).
- If you find a real vulnerability (not just a best-practice gap), flag it prominently and suggest blocking the merge.
- Be precise: include file paths, line numbers, and code snippets.
- Don't flag test files for missing input validation unless the test fixtures contain real secrets.
- Don't flag `TODO` comments as findings — they're tracked separately. But DO flag if a TODO is security-critical and has been open for more than 2 weeks.
