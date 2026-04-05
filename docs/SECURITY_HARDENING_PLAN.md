# Security Next Phases

## Context

Slices 0-5 + 8 delivered the minimum viable secure system: SD-JWT issuance, issuer sig verification, KB-JWT holder binding, nonce/audience binding, StatusList2021 revocation, webhook fail-closed. The relay service exists as a Go backend but isn't wired to mobile yet. This plan covers the remaining work to complete the security + verification flow.

---

## Phase A: Ship Current PR

1. Wait for CI green on behaghel/cachet#55
2. Fix any remaining lint/build issues
3. Merge to main

---

## Phase B: Relay Mobile Integration

**Goal:** The wallet can scan a verifier's QR, fetch the request from the relay, build a presentation, and post it back. The verifier polls the relay for the response.

### B1: RelayClient (Kotlin)

**New:** `mobile/shared/.../network/RelayClient.kt`
- `fetchRequest(requestUri: String): String` — GET the signed request object
- `postResponse(responseUri: String, body: ByteArray)` — POST the encrypted VP (or SD-JWT presentation)

**Modified:** `mobile/shared/.../config/AppConfig.kt` — add `relayUrl`
**Modified:** `mobile/shared/.../di/SharedModule.kt` — register RelayClient

### B2: QR Content Generation

**Modified:** QR share screen — encode `cachet://verify?request_uri=<relay>/sessions/{id}/request` instead of current static content. The verifier-side flow creates a relay session first, then generates QR from the session URL.

### B3: VerificationUseCase Relay Flow

**Modified:** `VerificationUseCase.kt`

Verifier path (person demanding trust):
1. Create relay session (POST to relay with request object)
2. Generate QR from session URL
3. Poll `GET /sessions/{id}/response` until holder responds
4. Verify the response locally

Holder path (person proving themselves):
1. Scan QR → parse `request_uri`
2. Fetch request from relay
3. Display consent screen with verifier identity + requested claims
4. Build SD-JWT presentation with selective disclosure + KB-JWT
5. POST to relay's response endpoint

---

## Phase C: E2E Encryption + Signed Requests (Slices 6-7)

**Goal:** Relay cannot read VP claims (T5). Holder verifies verifier identity before disclosing (T6).

### C1: Slice 6 — JWE Encryption

**Go (verifier):** Ephemeral X25519 key pair per session. QR includes verifier's ephemeral pubkey. Decrypt incoming JWE.
**Kotlin (mobile):** Encrypt VP to verifier's ephemeral key before posting to relay.
**Library:** `go-jose/v4` (Go), Tink (Kotlin)

### C2: Slice 7 — Signed Request Objects

**Go (verifier):** Sign the request object as JWS with verifier's ES256 key.
**Kotlin (mobile):** Verify JWS before showing consent screen. Display verified verifier name/logo.

---

## Phase D: Polish + Hardening (later)

### D1: QR Scanner
- Add ML Kit Vision or ZXing decoder to Android app
- Parse `cachet://verify?request_uri=...` URI scheme
- Wire into holder-side relay flow from Phase B

### D2: Biometric Gate
- Android KeyStore already supports `setUserAuthenticationRequired(true)`
- Wire into `KeyManager.android.kt` — require biometric before `sign()`
- Configurable per Cach'Pack (high-value cachets require biometric)
- Needs `androidx.biometric:biometric` dependency

### D3: Threagile Threat Model
- Create `docs/threagile.yaml` describing all services, data flows, trust boundaries
- Add `threagile` to devenv.nix packages
- Add `ci:threat-model` script that generates HTML report
- Run in CI on architecture-affecting PRs (new services, new endpoints)

### D4: iOS KeyManager
- Secure Enclave `actual` implementation for `KeyManager.kt`
- P-256 key generation via `SecKey` API
- Signing via `SecKeyCreateSignature`

---

## UX Impact Assessment

See separate section below.
