# Security Next Phases

## Context

Slices 0-5 + 8 delivered the minimum viable secure system: SD-JWT issuance, issuer sig verification, KB-JWT holder binding, nonce/audience binding, StatusList2021 revocation, webhook fail-closed. The relay service exists as a Go backend but isn't wired to mobile yet. This plan covers the remaining work to complete the security + verification flow.

---

## Phase A: Ship Current PR — DONE

Merged as behaghel/cachet#55.

---

## Phase B: Relay Mobile Integration — DONE

`KtorRelayClient` + `VerificationUseCase` relay flow + WalletApp wiring.
QR now encodes `cachet://verify?request_uri={relay}/sessions/{id}/request`.
Verifier creates relay session → holder fetches → builds KB-JWT presentation → posts to relay → verifier polls and verifies. Falls back to static QR if relay unavailable.

---

## Phase C: E2E Encryption + Signed Requests (Slices 6-7) — DONE

Slice 6: JWE encryption with ephemeral X25519 (ECDH-ES+A256KW / A256GCM).
Go `lestrrat-go/jwx/v2`, Kotlin `nimbus-jose-jwt` + Tink + BouncyCastle.
Relay sees only ciphertext. Backward compatible with plaintext.

Slice 7: Signed Request Objects (ES256 JWS, `typ: oauth-authz-req+jwt`).
Verifier signs with identity key, holder verifies via DID resolution
(`/.well-known/did.json`). Consent screen shows verified verifier name
or "Unverified requester" warning.

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
