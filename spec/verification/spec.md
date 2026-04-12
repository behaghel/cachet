---
domain: verification
type: core
status: draft
last-reviewed: 2026-04-12
governs:
  - services/verifier/server.go
  - services/verifier/internal/eval/
  - services/verifier/internal/statuslist/
  - services/verifier/internal/session/
  - services/verifier/internal/jwe/
  - services/verifier/internal/identity/
  - services/verifier/internal/pack/
---

# Verification Domain — Behavioral Spec

## 1. Session Lifecycle

### POST /sessions

**Behavior:** Creates a time-bound verification session.

**Request:** Optional body with `packId`, `question`, `predicates`.

**Response (200):**
- `sessionId`: UUID v4 (>= 128-bit entropy)
- `nonce`: 128-bit cryptographically random, base64url-encoded
- `verifierDid`: this verifier's DID
- `ephemeralPubKey`: X25519 public key for E2E encryption (base64url)
- `requestObject`: signed JWT (only if IdentitySigner configured)

**Invariants:**
- Nonce is fresh per session (never reused)
- Session ID is globally unique
- Session expires after 5 minutes (TTL)
- Session is one-time-use: `Consume()` sets Used=true, second call returns error
- Ephemeral key generation is non-fatal: nil key if generation fails

### GET /.well-known/did.json

**Behavior:** Serves verifier's DID document for Request Object verification.

**Response (200):** DID document with JsonWebKey2020 verification method.
**Response (404):** No IdentitySigner configured.

---

## 2. Credential Verification

### POST /presentations/verify

**Behavior:** Evaluates credential presentations against a Trust Pack's requirements.

**Request:**
- `policyId`: Trust Pack identifier (required)
- `sessionId`: Session UUID (optional, enables crypto verification)
- `bundle.credentials`: Legacy VC array (optional)
- `sdJwtCredentials`: SD-JWT strings (optional, triggers crypto path)

**Response (200):**
- `cachet`: Pack label if granted, empty if denied
- `predicates`: Satisfied predicate IDs (null if none)
- `freshness`: "ok" | "stale" | "expired"
- `predicateResults[]`: Per-predicate status + reason
- `summary`: Grant decision with counts

### Verification Paths

**SD-JWT Path** (triggered when `sdJwtCredentials` non-empty AND DIDResolver configured):

1. If session provided and credential is JWE: decrypt with session's ephemeral private key
2. For each SD-JWT credential:
   a. Parse: split on `~`, extract issuer JWT + disclosures + optional KB-JWT
   b. Resolve issuer DID → ECDSA public key
   c. Verify issuer JWS signature (ES256 only)
   d. Validate `_sd_alg` == "sha-256"
   e. Verify all disclosure hashes exist in `_sd` array
   f. Merge verified claims
   g. If KB-JWT present:
      - Extract `cnf.jwk` from issuer JWT → holder's P-256 public key
      - Verify KB-JWT signature against holder key
      - Verify `typ` == "kb+jwt"
      - Verify `sd_hash` == sha256(issuerJWT~disc1~...~)
      - Verify `iat` <= 5 minutes ago (freshness)
      - Extract nonce and audience
3. If session provided and credential is holder-bound:
   - Validate nonce matches session nonce → 400 "nonce_mismatch" on failure
   - Validate audience matches verifier DID → 400 "audience_mismatch" on failure
4. Check revocation via StatusList2021

**Legacy Path** (triggered when no SD-JWT credentials or no DIDResolver):
- Evaluates `bundle.credentials` as-is, no cryptographic verification

### Freshness Check

Applied to all credentials:
- `expired`: now > credential expirationDate
- `stale`: now - issuedAt > 90 days
- `ok`: otherwise
- Single stale/expired credential taints entire response

### Predicate Evaluation

For each predicate in pack definition:
- Operators: `==`, `>`, `<`, `>=`, `<=`, `boolean`
- Type coercion: float64, float32, int, int64, *int, bool, *bool, string
- Issuer filtering: only evaluate credentials from `pred.IssuersAccepted`
- Status: "satisfied" | "failed" | "no_credential"
- First matching credential wins (short-circuit)

### Cachet Grant Decision

- `cachetGranted` = all **required** predicates satisfied
- Required: `pred.Required == nil || *pred.Required == true` (default true)
- Optional: `pred.Required == false`
- `cachet` label populated only if granted

### Error Responses (400)

| Code | Condition |
|------|-----------|
| unknown_pack | policyId not found in registry |
| invalid_session | Session expired, consumed, or not found |
| decryption_failed | JWE decryption failed |
| verification_failed | SD-JWT signature invalid |
| nonce_mismatch | KB-JWT nonce != session nonce |
| audience_mismatch | KB-JWT audience != verifier DID |
| credential_revoked | StatusList2021 bit is set |

---

## 3. Revocation Checking

### StatusList2021

- Credential includes `status` claim with `statusListCredential` URL and `statusListIndex`
- Fetch bitstring from issuer: base64url-decode → gzip-decompress → raw bytes
- Check bit at index: byte = index/8, bit = 7-(index%8) (MSB-first per W3C)
- Cache TTL: 5 minutes
- **Failure handling:** Network errors are non-fatal (log warning, continue)
- **Privacy:** Always fetch full bitstring (herd privacy)

---

## 4. Pack Registry Client

### GET /packs

**Behavior:** Proxies pack definitions from registry service.

**Response (200):** Array of pack summaries.
**Response (502):** Registry unavailable → "registry_error"

---

## 5. Threat Mitigations

| Threat | Mitigation |
|--------|-----------|
| T1: Credential Forgery | Issuer JWS signature verification (ES256) |
| T2: Credential Replay | KB-JWT iat <= 5 min + session one-time-use |
| T3: Cross-Verifier Replay | KB-JWT aud == session verifierDID |
| T5: Relay Eavesdropping | JWE with ephemeral X25519 key agreement |
| T6: QR Phishing | Signed Request Objects |
| T7: Revoked Credential | StatusList2021 check on every verification |
| T13: Session Prediction | UUID v4 (128-bit) + 5-min TTL |
