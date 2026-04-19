# Proximity Verification Protocol

> Revision 1 — 2026-04-20 (initial spec)

Extension to the [Verification Protocol](../../security/verification-protocol.md) for co-present, fully-offline credential verification. Two devices in the same room complete the entire flow without any network connectivity.

## 1. Motivation

The relay-based flow (Section 4.1 of the Verification Protocol) requires both devices to reach the relay server. This fails in:

- Farmers' markets, outdoor events (no Wi-Fi, spotty cellular)
- Childcare drop-off lobbies with poor reception
- International travel (roaming restrictions)
- Privacy-maximizing scenarios (neither party wants network metadata logged)

Proximity verification eliminates the relay entirely. The cryptographic guarantees (issuer signature, holder binding, nonce freshness, audience binding, encryption) are identical.

## 2. Protocol Overview: Two-QR Dance

```
Verifier                                              Holder
   |                                                    |
   |  1. Create local session                           |
   |     (nonce + ephemeral X25519 key pair)            |
   |                                                    |
   |  2. Display session QR                             |
   |  ─ ─ ─ ─ ─ ─ ─ ─ ─ QR displayed ─ ─ ─ ─ ─ ─ ─ ►|
   |                                         Scans QR   |
   |                                                    |
   |                               3. Parse session     |
   |                                  params from QR    |
   |                                                    |
   |                               4. Show consent      |
   |                                  (same as relay    |
   |                                   IncomingRequest) |
   |                                                    |
   |                               5. Build VP:         |
   |                                  - Select discl.   |
   |                                  - Sign KB-JWT     |
   |                                    (nonce, aud)    |
   |                                  - Encrypt to vk   |
   |                                                    |
   |                               6. Display VP QR     |
   |◄ ─ ─ ─ ─ ─ ─ ─ ─  QR displayed  ─ ─ ─ ─ ─ ─ ─ ─|
   |  Scans QR                                          |
   |                                                    |
   |  7. Decrypt VP                                     |
   |  8. Verify locally (same 13-step pipeline)         |
   |  9. Show result                                    |
   |                                                    |
```

**Key difference from relay flow**: Steps 3-6 replace the relay fetch/post. The holder receives the request directly from the QR (not from a relay URL). The holder's response is displayed as a QR (not POSTed to a relay URL).

## 3. Session QR Payload

The verifier's QR encodes a URI with all session parameters inline (no `request_uri` indirection):

```
cachet://proximity?n={nonce}&vk={ephemeralPubKey}&pack={packId}&q={question}&p={predicates}
```

| Field | Format | Purpose | Size |
|-------|--------|---------|------|
| `n` | base64url, 22 chars | 128-bit random nonce | 22 B |
| `vk` | base64url, 44 chars | X25519 ephemeral public key (32 bytes) | 44 B |
| `pack` | string | Trust Pack ID (e.g., `childcare-readiness-v1`) | ~30 B |
| `q` | URL-encoded string | Human-readable question | ~50 B |
| `p` | comma-separated | Predicate IDs (e.g., `age_18,dbs_check`) | ~40 B |

**Total QR payload**: ~250-400 bytes. Fits comfortably in QR version 10 (174 bytes at level H, 652 bytes at level L).

### 3.1 Nonce Generation

Generated locally on the verifier device using `java.security.SecureRandom` (Android) or `SecRandomCopyBytes` (iOS). Must be >= 128 bits of entropy (same requirement as Section 3.2 T13 of the Verification Protocol).

### 3.2 Ephemeral Key Generation

X25519 key pair generated on-device. The private key is held in memory only (never persisted). The public key is embedded in the QR.

### 3.3 Session Lifetime

The QR is valid for a single verification. There is no relay session to expire. Freshness is enforced by the KB-JWT `iat` check (must be within 5 minutes of the verifier's local clock). Clock skew tolerance: 60 seconds.

## 4. Response QR Payload

The holder's QR contains the JWE compact serialization of the encrypted VP:

```
cachet-vp:{jwe_compact_serialization}
```

The `cachet-vp:` prefix distinguishes this from session QRs and relay QRs. The JWE payload contains:

```
{issuer_jwt}~{disclosure1}~{disclosure2}~...~{kb_jwt}
```

### 4.1 Size Budget

| Component | Typical Size |
|-----------|-------------|
| JWE header (ECDH-ES+A256KW) | ~120 B |
| Encrypted key | ~48 B |
| IV | ~16 B |
| SD-JWT issuer JWT | ~400-600 B |
| 4 disclosures | ~400 B |
| KB-JWT | ~250 B |
| Auth tag | ~22 B |
| JWE overhead (dots, base64url) | ~100 B |
| **Total JWE** | **~1,400-1,600 B** |
| With `cachet-vp:` prefix | ~1,410-1,610 B |

**QR capacity at level L**: 2,953 bytes (version 40). **Margin**: ~1,300 bytes.

**Threshold**: If the JWE exceeds 2,500 bytes, the holder displays a "Payload too large for QR" message and suggests using the relay flow instead. Future: chunked QR (Phase 2) or BLE (Phase 3).

## 5. Verification Pipeline

Identical to Section 4.5 of the Verification Protocol, with one addition at the start:

| Step | Action | Failure Mode |
|------|--------|-------------|
| **0** | Strip `cachet-vp:` prefix, validate JWE format | Reject: not a VP QR |
| **a-m** | Same as relay flow | Same failure modes |

The `LocalVerifier` class is reused without modification. The transport abstraction ensures the same `verify()` method is called regardless of how the VP arrived.

## 6. Threat Model Extension

New threats specific to proximity mode:

| # | Threat | Scenario | Mitigation |
|---|--------|----------|------------|
| T15 | **Shoulder surfing (session QR)** | Bystander photographs verifier's QR and scans it on their own device | Nonce is single-use (verifier accepts only one response). Bystander's VP would have different holder binding. If bystander responds first, legitimate holder's response is ignored (verifier already completed). Verifier should shield screen. |
| T16 | **Shoulder surfing (VP QR)** | Bystander photographs holder's VP QR | VP is encrypted to verifier's ephemeral key. Bystander cannot decrypt without the private key (held only in verifier's memory). |
| T17 | **QR screenshot replay** | Attacker saves a photo of a previous VP QR and shows it to a new verifier | KB-JWT nonce is bound to the specific session. New verifier generates a new nonce. Replayed VP's nonce won't match. |
| T18 | **Clock manipulation** | Attacker sets device clock far in the future to use an expired credential | KB-JWT `iat` checked against verifier's local clock. If holder's `iat` is more than 60 seconds from verifier's clock, reject. Credential `exp` checked against verifier's clock (not holder's). |

### 6.1 Threats Inherited from Relay Flow

T1 (forgery), T2 (replay), T3 (cross-verifier), T7 (revocation), T8 (device compromise), T10 (correlation) — all apply unchanged. T4 (MITM) is **reduced** in proximity: no relay to intercept. T5 (eavesdropping) is **reduced**: no network transit (but T16 replaces it). T6 (QR phishing) applies: attacker could place a fake proximity QR.

### 6.2 Accepted Risks

- **Visual proximity assumption**: The protocol assumes both parties are co-present. It does not prove physical proximity (no distance bounding). A remote attacker with a live video feed of the QR could participate. This is acceptable because the same risk exists with any QR-based flow.

## 7. Transport Abstraction

The verification flow is factored into a `VerificationTransport` interface:

```kotlin
interface VerificationTransport {
    suspend fun createSession(params: SessionParams): TransportSession
    suspend fun receiveRequest(sessionData: String): VerifiedRequest
    suspend fun sendResponse(sessionData: String, payload: ByteArray)
    suspend fun awaitResponse(session: TransportSession): ByteArray
}
```

| Method | Relay impl | Proximity impl |
|--------|-----------|----------------|
| `createSession` | POST to verifier + relay backend | Generate nonce + X25519 locally |
| `receiveRequest` | GET from relay URL | Parse QR URI params |
| `sendResponse` | POST to relay response URL | Encode as QR + display |
| `awaitResponse` | Poll relay GET | Scan holder's QR |

The `VerificationUseCase` accepts a transport instance. `LocalVerifier` and `buildSDJWTPresentation()` are transport-agnostic.

## 8. Error Handling

| Error | User-Facing Behavior |
|-------|---------------------|
| VP too large for QR (> 2,500 B) | Holder sees "Credential too large for in-person verification. Use online verification instead." |
| Invalid session QR scanned | Holder sees error, scanner stays open for retry |
| Verifier scans non-VP QR | Verifier sees error, scanner stays open for retry |
| JWE decryption fails | Verifier sees "Could not read response. Ask them to try again." |
| Nonce mismatch | Verifier sees "Response doesn't match this session. Start over." |
| Cache miss (no pack definition) | Verifier sees "Trust Pack not available offline. Connect to internet to update." |
| Cache miss (no issuer DID) | Verifier sees "Issuer key not available offline. Connect to internet to update." |

## 9. Future Extensions

- **Phase 2: Chunked QR** — animated QR sequence for payloads > 2.5KB
- **Phase 3: BLE transport** — QR for session establishment, BLE GATT for VP transfer
- **Phase 4: NFC tap** — NFC carries session params instead of QR, VP via QR or BLE
- **Signed proximity requests** — verifier signs the session params (currently unsigned, unlike relay flow's Request Object)
