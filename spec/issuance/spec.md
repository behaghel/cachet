---
domain: issuance
status: draft
last-reviewed: 2026-04-12
---

# Issuance Domain — Behavioral Spec

## 1. OAuth2 Token Exchange

### POST /oauth/token

**Request (form-encoded):**
- `grant_type`: must be "client_credentials"
- `client_id`: required (non-empty)
- `scope`: optional
- `session_id`: optional, embedded in token if provided

**Response (200):**
```json
{
  "access_token": "RS256 signed JWT",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "..."
}
```

**Token claims:** sub, client_id, scope, iat, exp (1 hour), jti (UUID), session_id.

**Errors (400):**
- Missing/wrong grant_type → "unsupported_grant_type"
- Missing client_id → "invalid_request"

---

## 2. Credential Issuance

### POST /credential

**Auth:** Bearer token (RS256 validated against issuer's RSA public key).

**Request:**
```json
{
  "format": "vc+sd-jwt | jwt_vc | ldp_vc",
  "types": ["VerifiableCredential", "IdentityCredential"],
  "proof": { "jwk": { "kty": "EC", "crv": "P-256", ... } }
}
```

**Behavior:**

1. Validate bearer token (RS256) → 401 if invalid/expired
2. Extract `session_id` from token claims
3. Look up Veriff session in store → 400 "no_session" if not found
4. Validate session quality → 400 "validation_failed" if invalid
5. Format-specific credential building:

**vc+sd-jwt path:**
- Extract holder JWK from `proof.jwk` (for KB-JWT binding)
- Allocate StatusList2021 index
- Build SD-JWT credential (see Section 3)
- Return signed SD-JWT string

**jwt_vc / ldp_vc path:**
- Build legacy VerifiableCredential JSON object

**Errors:**
| Code | Condition |
|------|-----------|
| invalid_token (401) | Missing, invalid, or expired bearer token |
| invalid_request (400) | Malformed body or unsupported format |
| no_session (400) | Session ID not in store |
| validation_failed (400) | Session failed quality validation |
| server_error (500) | Status list full or credential building failed |

---

## 3. SD-JWT Credential Construction

### Non-Disclosable Claims (always visible)

- `iss`: Issuer DID (e.g., "did:veriff:production")
- `sub`: Subject DID
- `iat`: Unix timestamp (now)
- `exp`: Unix timestamp (now + 90 days)
- `jti`: URN UUID
- `vct`: Credential type URIs
- `status`: StatusList2021Entry (listCredential URL, index, purpose=revocation)
- `cnf`: Holder's JWK (if proof.jwk provided — enables KB-JWT binding)

### Selectively Disclosable Claims

- `age`: Calculated years from dateOfBirth
- `nationality`: Document country
- `documentType`: Document type
- `verified`: Boolean true
- `verificationLevel`: Quality tier (basic/standard/premium/gold)
- `verificationMethod`: "veriff"
- `overallConfidence`, `livenessScore`, `documentAuthenticity`, `riskScore`: Metrics

### SD-JWT Construction Algorithm

1. For each disclosable claim: generate 128-bit random salt, format as JSON array [salt, name, value], base64url-encode, compute SHA-256 hash
2. Build JWT payload: non-disclosable claims + `_sd` (hash array) + `_sd_alg: "sha-256"`
3. Sign with ES256 (issuer's P-256 key), header includes `typ: "vc+sd-jwt"`, `kid`
4. Concatenate: `issuerJWT~disclosure1~disclosure2~...~` (trailing `~` for KB-JWT slot)

---

## 4. Veriff Webhook

### POST /webhooks/veriff

**Signature Verification (HMAC-SHA256):**
- Read body, extract `X-HMAC-Signature` header
- Compute HMAC-SHA256(body, secret) → hex
- Compare with timing-safe equality
- **Fail-closed:** 500 if secret not configured, 401 if signature missing/invalid

**Session Quality Validation:**

| Tier | Confidence | Liveness | Authenticity |
|------|-----------|----------|-------------|
| Gold | >= 0.95 | >= 0.90 | >= 0.95 |
| Premium | >= 0.90 | >= 0.85 | — |
| Standard | >= 0.80 | — | — |
| Basic | (any approved) | — | — |

**Rejection criteria:** status != "approved", liveness in (0, 0.7), riskScore > 0.3.

**Response:**
- 200: Valid session stored (approved + quality check passed)
- 202: Acknowledged but not stored (not approved or failed quality)
- 401: Invalid signature
- 400: Missing session_id
- 500: Secret not configured

**Invariants:**
- Idempotent: same session_id → last write wins
- Invalid sessions don't block processing
- Webhook stores session data; credential issuance is a separate request

---

## 5. Status List Management

### GET /status/{listId}

Returns StatusList2021 bitstring credential. Cache-Control: max-age=300.

### POST /status/{listId}/revoke

Sets bit at given index to 1 (revoked). MSB-first bit ordering per W3C spec.

### Index Allocation

- Sequential allocation from list
- Default list "1": 16 KB = 131,072 credential slots
- Error when list full

---

## 6. Issuer Key Lifecycle

- **On startup:** Load from ISSUER_KEY_FILE or DEVENV_STATE/issuer-key.pem
- **If not found:** Generate new P-256 key and persist
- **If persistence fails:** Use ephemeral key (logged as error)
- **Critical invariant:** Key must persist across restarts — all previously issued credentials depend on it

### GET /.well-known/jwks.json

Returns issuer's ECDSA public key as JWK. Used by verifiers to resolve issuer DID.
