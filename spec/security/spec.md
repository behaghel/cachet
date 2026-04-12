---
domain: security
status: draft
last-reviewed: 2026-04-12
---

# Security Domain — Behavioral Spec

## 1. Threat Model

14 threats identified in verification-protocol.md Section 3.

| ID | Threat | Severity | Mitigation | Status |
|----|--------|----------|------------|--------|
| T1 | Credential Forgery | Critical | Issuer JWS verification (ES256) | Implemented |
| T2 | Credential Replay | High | KB-JWT nonce + 5-min iat freshness + session one-time-use | Implemented |
| T3 | Cross-Verifier Replay | High | KB-JWT aud binding to verifier DID | Implemented |
| T4 | Verifier-in-the-Middle | High | Signed Request Objects + KB-JWT holder binding | Implemented |
| T5 | Relay Eavesdropping | High | JWE with ephemeral X25519 ECDH-ES+A256KW/A256GCM | Implemented |
| T6 | QR Phishing | Medium | Signed Request Objects (verifier identity before consent) | Implemented |
| T7 | Revoked Credential | High | StatusList2021 check on every verification | Implemented |
| T8 | Device Compromise | High | Hardware-backed keys (StrongBox/Secure Enclave) | Implemented (mobile) |
| T9 | Session DoS | Medium | 5-min TTL, in-memory store with eviction | Implemented |
| T10 | Cross-Presentation Correlation | Medium | Future: BBS+ unlinkable proofs | Not implemented |
| T11 | Verifier Collusion | Medium | Future: pairwise DIDs | Not implemented |
| T12 | Transport Metadata Leakage | Low | Future: Tor/mixnet relay | Not implemented |
| T13 | Session ID Prediction | Medium | UUID v4 (128-bit) + crypto/rand nonce | Implemented |
| T14 | Webhook Injection | Critical | HMAC-SHA256 fail-closed | Implemented |

---

## 2. Cryptographic Algorithms

### Issuer Credential Signing
- **Algorithm:** ES256 (ECDSA with SHA-256, P-256 curve)
- **Key:** Persistent P-256 private key (PEM file)
- **Rotation:** Manual (not automated in MVP)
- **Critical:** Key loss invalidates all issued credentials

### Holder Key Binding
- **Algorithm:** ES256 (ECDSA P-256)
- **Key:** Hardware-backed (Android StrongBox / iOS Secure Enclave)
- **Property:** Non-exportable — cannot be extracted even by app
- **Binding:** `cnf.jwk` claim in credential links to holder's public key

### Access Token Signing
- **Algorithm:** RS256 (RSA-SHA256, 2048-bit)
- **Lifetime:** 1 hour

### Request Object Signing
- **Algorithm:** ES256 (ECDSA P-256)
- **Purpose:** Verifier identity proof (T6 mitigation)
- **Failure mode:** Non-fatal (omitted from response if signing fails)

### End-to-End Encryption
- **Key Agreement:** ECDH with X25519 ephemeral keys
- **Wrapping:** ECDH-ES+A256KW
- **Encryption:** A256GCM (authenticated)
- **Format:** JWE Compact Serialization (RFC 7516)

### Selective Disclosure
- **Hash:** SHA-256 (only algorithm accepted: `_sd_alg: "sha-256"`)
- **Salt:** 128-bit cryptographically random per disclosure
- **Format:** base64url(json([base64url(salt), claim_name, value]))

### Nonce Generation
- **Source:** crypto/rand (Go), SecureRandom (Kotlin)
- **Size:** 128 bits (16 bytes)
- **Encoding:** base64url

### Webhook Authentication
- **Algorithm:** HMAC-SHA256
- **Encoding:** hex string
- **Comparison:** Timing-safe (hmac.Equal)
- **Policy:** Fail-closed (reject if secret unset)

---

## 3. SD-JWT Verification Algorithm

Input: `issuerJWT~disc1~disc2~...~[kbjwt]`

1. Split on `~` → issuer JWT, disclosures, optional KB-JWT
2. Resolve issuer DID → ECDSA P-256 public key
3. Verify issuer JWS signature (require ES256)
4. Validate `_sd_alg` == "sha-256"
5. For each disclosure: verify sha256(disclosure) exists in `_sd` array
6. Merge verified claims from disclosures
7. If KB-JWT present:
   a. Extract `cnf.jwk` → holder's P-256 public key
   b. Verify KB-JWT signature (ES256)
   c. Verify `typ` == "kb+jwt"
   d. Verify `sd_hash` == sha256(issuerJWT~disc1~...~)
   e. Verify `iat` <= 5 minutes
   f. Extract nonce and audience

---

## 4. Mobile Crypto Components

### SDJWTParser (Kotlin)
- Holder-side parsing (no signature verification)
- Splits on `~`, decodes disclosures as JSON arrays
- `selectivePresentation()`: filters disclosures by requested claim names

### KBJWTBuilder (Kotlin)
- Creates `{"alg":"ES256","typ":"kb+jwt"}` header
- Computes `sd_hash` = sha256(sdJwtWithDisclosures)
- Payload: nonce, aud, iat, sd_hash
- Signs with holder's hardware-backed key via KeyManager

### JWEEncryptor (Kotlin expect class)
- Encrypts plaintext to verifier's ephemeral X25519 public key
- ECDH-ES+A256KW / A256GCM
- Returns JWE Compact Serialization

### KeyManager (Kotlin expect class)
- Platform-specific key generation and signing
- Android: StrongBox Keymaster
- iOS: Secure Enclave

---

## 5. Fail-Open vs Fail-Closed

### Fail-Closed (reject on failure)
- Webhook signature verification (T14)
- Credential signature verification (T1)
- KB-JWT expired or stale (T2)
- Nonce/audience mismatch (T2, T3)
- Credential revoked (T7)

### Fail-Open (continue on failure)
- Revocation check network error (log warning)
- Request Object signing failure (omit from response)
- Ephemeral key generation failure (nil key, no encryption)

---

## 6. Future (v2)

- BBS+ signatures for unlinkable derived proofs (T10)
- Pairwise DIDs per verifier relationship (T11)
- ZK predicate proofs (prove age >= 18 without disclosing age)
- Accumulator-based revocation (non-index-based)
- Multi-relay + Tor/mixnet transport (T12)
