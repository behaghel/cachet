# Verification Protocol

> Revision 3 — 2026-04-05 (pure spec: moved implementation tracking to SECURITY_HARDENING_PLAN.md)
> Revision 2 — 2026-04-04 (full spec rewrite: threat model, crypto requirements, phases)
> Revision 1 — 2026-03-28 (flow sketch only)

## 1. Foundational Principles

1. **Local verification**: Credential signature checks and predicate evaluation happen on the verifier's device, never delegated to a server. If it can be done without a third party, it must be.

2. **Verifier-first**: The person demanding trust is the primary actor. The UX, terminology, and flow design reflect this. The verifier drives the interaction; the holder responds.

3. **Untrusted transport**: The relay is a stateless, dumb pipe. It cannot read, forge, tamper with, or suppress payloads. All messages are end-to-end encrypted and signed.

4. **Minimal disclosure**: Only the claims required to evaluate the requested predicates are disclosed. Raw values are avoided where predicates suffice (e.g., `age >= 18` instead of date of birth).

5. **Holder consent**: The holder sees exactly which claims will be disclosed and to whom, and must explicitly approve before any data leaves their device.

6. **Cryptographic proof, not trust**: Every claim in the protocol is backed by a verifiable cryptographic proof. Display names, DIDs, signatures, freshness, and holder binding are all machine-verifiable.

---

## 2. Actors

| Actor | Role | Trust Level |
|-------|------|-------------|
| **Verifier** | Demands proof (parent hiring a babysitter, buyer on a marketplace). Creates the verification session. Evaluates the result locally. | Trusted by themselves. Authenticated to the holder via signed request. |
| **Holder** | Proves themselves (the babysitter, the seller). Holds credentials in their device vault. Controls what to disclose. | Partially trusted. Cannot forge issuer signatures. Can withhold claims. |
| **Issuer** (e.g., Veriff) | Issued the credential. Their signature is the root of trust. | Trusted at issuance time. Not involved at verification time. |
| **Relay** | Stateless message broker. Facilitates transport between devices. | **Untrusted.** Cannot read payloads (encrypted). Cannot forge (signed). Can only drop messages (DoS — inherent to any transport). |
| **Cachet Backend** | Hosts pack registry, issuance gateway, transparency log. | **Not trusted for verification.** Used for issuance and async receipt logging only. |

---

## 3. Threat Model

### 3.1 Assumptions

- The holder's device has a hardware-backed secure element (StrongBox/TEE on Android, Secure Enclave on iOS).
- The verifier trusts their own device.
- The issuer's signing key is not compromised (if it is, all credentials from that issuer are invalid — this is handled by key rotation and revocation).
- Network transport (TLS) protects against passive eavesdropping at the transport layer; the protocol provides security above TLS via end-to-end encryption.

### 3.2 Threats and Mitigations

| # | Threat | Attack Scenario | Mitigation | Phase |
|---|--------|----------------|------------|-------|
| T1 | **Credential forgery** | Attacker crafts a credential with `issuer: "did:veriff:production"` and arbitrary claims | Issuer signature verification: parse SD-JWT JWS, resolve issuer DID, verify signature against public key | MVP |
| T2 | **Credential theft / replay** | Attacker intercepts a valid presentation and replays it to another session | Key Binding JWT (KB-JWT) with verifier-supplied `nonce` and `aud`. Hardware-backed holder key means stolen credential is unusable without the device. | MVP |
| T3 | **Cross-verifier replay** | Presentation intended for Verifier A is forwarded to Verifier B | `aud` claim in KB-JWT bound to the requesting verifier's DID/identifier | MVP |
| T4 | **Verifier-in-the-middle** | Attacker poses as verifier to Holder, simultaneously poses as holder to real Verifier. Forwards QR. Victim's presentation goes to the real verifier. | Audience binding (`aud` in KB-JWT matches QR originator). Verifier identity displayed to holder before consent. Channel binding via `sd_hash` in KB-JWT. | MVP |
| T5 | **Relay eavesdropping** | Relay operator reads credential claims in transit | End-to-end encryption: wallet encrypts VP to verifier's ephemeral public key (from QR). Relay sees only ciphertext. | MVP |
| T6 | **QR phishing (quishing)** | Attacker places malicious QR over legitimate one | Signed Request Objects — wallet verifies verifier identity before disclosing. Dynamic QR refresh (30-60s). Verifier identity displayed prominently. | MVP |
| T7 | **Revoked credential used** | Holder presents a credential that has been revoked | StatusList2021 check on every verification. Cached with short TTL. CDN distribution prevents issuer from learning which credential is checked. | MVP |
| T8 | **Device compromise** | Attacker gains full access to holder's device | Hardware-backed keys (non-exportable). Biometric gate before signing KB-JWT. Remote revocation of compromised credentials. Re-issuance flow for new device. | MVP |
| T9 | **Relay suppression** | Relay drops holder's response, making verifier believe no one scanned | Inherent DoS risk of any transport. Mitigated by: session timeout UX (retry prompt), future multi-relay support. The relay cannot selectively suppress without detection (verifier sees timeout, not success). | MVP |
| T10 | **Cross-presentation correlation** | Colluding verifiers link the same holder across presentations via identical issuer signature | SD-JWT limitation: base JWT is identical across presentations. **Accepted for MVP.** BBS+ signatures produce unlinkable derived proofs. | v2 |
| T11 | **Verifier over-collection** | Verifier requests more predicates than necessary | Holder consent screen shows exact claims. Pack definitions published and auditable. Future: purpose binding and regulatory enforcement. | MVP |
| T12 | **Issuer tracking** | Issuer learns when/where credentials are verified | Issuer not involved at verification time. StatusList fetched via CDN with cache. No phone-home. | MVP |
| T13 | **Session ID prediction** | Attacker guesses active relay sessions | Session IDs must have >= 128 bits of entropy (UUID v4 or equivalent). Session TTL <= 5 minutes. | MVP |
| T14 | **Webhook injection** | Attacker sends fabricated Veriff results to issuance gateway | HMAC-SHA256 webhook signature verification. **Fail closed** — reject if secret is unset or signature is missing. | MVP |

### 3.3 Out of Scope

- Physical coercion of the holder (social/legal problem, not cryptographic).
- Compromise of the issuer's root signing key (handled by key rotation ceremony, not by the verification protocol).
- DDoS against the relay (standard infrastructure protection — WAF, rate limiting).

---

## 4. Protocol Flow

### 4.1 Overview (single QR scan, cross-device)

```
Verifier                       Relay                        Holder
   |                             |                             |
   |  1. Create session          |                             |
   |  (signed Request Object     |                             |
   |   + ephemeral DH pubkey)    |                             |
   ├────────────────────────────►|                             |
   |◄────── session_id + URL ────|                             |
   |                             |                             |
   |  2. Display QR              |                             |
   |  (request_uri + verifier    |                             |
   |   ephemeral pubkey)         |                             |
   |─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┼─ ─ ─ ─ QR displayed ─ ─ ─►|
   |                             |                      Holder scans QR
   |                             |                             |
   |                             |  3. Fetch Request Object    |
   |                             |◄────────────────────────────|
   |                             |────── signed request ──────►|
   |                             |                             |
   |                             |  4. Holder verifies         |
   |                             |     verifier identity       |
   |                             |     Shows consent screen    |
   |                             |     (exact claims listed)   |
   |                             |     Holder approves         |
   |                             |                             |
   |                             |  5. Build VP:               |
   |                             |     - Select disclosures    |
   |                             |     - Sign KB-JWT           |
   |                             |       (nonce, aud, sd_hash) |
   |                             |     - Encrypt to verifier's |
   |                             |       ephemeral key         |
   |                             |                             |
   |                             |  6. POST encrypted VP       |
   |                             |◄────────────────────────────|
   |                             |                             |
   |  7. Receive encrypted VP    |                             |
   |◄────────────────────────────|                             |
   |                             |                             |
   |  8. Decrypt VP              |                             |
   |  9. Verify LOCALLY:         |                             |
   |     a. Parse SD-JWT         |                             |
   |     b. Verify issuer sig    |                             |
   |        (DID → public key)   |                             |
   |     c. Verify KB-JWT sig    |                             |
   |        (cnf → holder key)   |                             |
   |     d. Check nonce matches  |                             |
   |     e. Check aud matches    |                             |
   |     f. Check sd_hash        |                             |
   |     g. Verify disclosures   |                             |
   |        against _sd hashes   |                             |
   |     h. Evaluate predicates  |                             |
   |     i. Check freshness      |                             |
   |     j. Check revocation     |                             |
   |        (StatusList2021)     |                             |
   |                             |                             |
   | 10. Show result             |                             |
   |     (cachet granted/denied  |                             |
   |      per-predicate detail)  |                             |
   |                             |                             |
   | 11. Log receipt (async)     |                             |
   ├────────────────────────────►|        Log receipt (async) ─|
   |                             |                             |
```

### 4.2 What the QR Encodes

The QR contains a URI, not the full request (QR capacity is ~2.9 KB max):

```
cachet://verify?request_uri=https://relay.cachet.id/sessions/{id}/request&vk={verifier_ephemeral_pubkey_base64url}
```

| Field | Purpose | Required |
|-------|---------|----------|
| `request_uri` | URL to fetch the full signed Request Object from the relay | Yes |
| `vk` | Verifier's ephemeral X25519 public key for E2E encryption (base64url) | Yes |

The QR refreshes every 30-60 seconds with a new session and new ephemeral key.

### 4.3 Request Object (fetched from relay)

A signed JWT (JWS) created by the verifier:

```json
{
  "header": {
    "alg": "ES256",
    "typ": "oauth-authz-req+jwt",
    "kid": "did:web:verifier.example#key-1"
  },
  "payload": {
    "client_id": "did:web:verifier.example",
    "client_id_scheme": "did",
    "response_type": "vp_token",
    "response_uri": "https://relay.cachet.id/sessions/{id}/response",
    "nonce": "n-0S6_WzA2Mj",
    "state": "{session_id}",
    "presentation_definition": {
      "id": "childcare-readiness-v1",
      "input_descriptors": [
        {
          "id": "age-check",
          "constraints": {
            "fields": [
              {
                "path": ["$.credentialSubject.age"],
                "filter": { "type": "number", "minimum": 18 }
              }
            ]
          }
        }
      ]
    },
    "client_metadata": {
      "client_name": "SafeNest Childcare",
      "logo_uri": "https://safenest.example/logo.png"
    },
    "iat": 1712188800,
    "exp": 1712189100
  }
}
```

The holder's wallet:
1. Fetches this JWT from the `request_uri`
2. Resolves the verifier's DID (`client_id`) to obtain their public key
3. Verifies the JWT signature
4. Displays the verifier identity (`client_name`, `logo_uri`) and requested claims to the holder
5. Proceeds only after explicit holder consent

### 4.4 Verifiable Presentation (holder → verifier)

The holder constructs:

1. **Selected disclosures** from the SD-JWT VC — only the claims required by the `presentation_definition`
2. **Key Binding JWT** (KB-JWT) proving holder possession:

```json
{
  "header": {
    "alg": "ES256",
    "typ": "kb+jwt"
  },
  "payload": {
    "nonce": "n-0S6_WzA2Mj",
    "aud": "did:web:verifier.example",
    "iat": 1712188830,
    "sd_hash": "base64url(sha256(sd_jwt_with_disclosures))"
  }
}
```

Signed with the holder's hardware-backed private key (the key referenced in the credential's `cnf` claim).

3. **Encryption envelope**: The combined SD-JWT + disclosures + KB-JWT is encrypted to the verifier's ephemeral X25519 key using ECDH-ES+A256KW / A256GCM (JWE Compact Serialization).

The encrypted payload is POSTed to the `response_uri` via the relay.

### 4.5 Local Verification (on verifier's device)

All steps happen on the verifier's device. No backend call is needed.

| Step | Action | Failure Mode |
|------|--------|-------------|
| **a** | Decrypt JWE using verifier's ephemeral private key | Reject: cannot decrypt |
| **b** | Parse SD-JWT: extract issuer JWT, disclosures, KB-JWT (separated by `~`) | Reject: malformed |
| **c** | Verify issuer signature: resolve issuer DID → public key, verify JWS | Reject: invalid signature or unknown issuer |
| **d** | Verify KB-JWT signature: extract `cnf.jwk` from issuer JWT, verify KB-JWT signed by that key | Reject: holder binding failed |
| **e** | Check `nonce` in KB-JWT matches the nonce from the Request Object | Reject: replay or session mismatch |
| **f** | Check `aud` in KB-JWT matches the verifier's `client_id` | Reject: audience mismatch (cross-verifier replay) |
| **g** | Check `sd_hash` in KB-JWT matches `sha256(sd_jwt~disclosure1~disclosure2~)` | Reject: disclosure tampering |
| **h** | Verify each disclosure hash against `_sd` arrays in the issuer JWT | Reject: disclosure not issued |
| **i** | Resolve claim values from verified disclosures | — |
| **j** | Evaluate predicates from the Cach'Pack definition against resolved claims | Per-predicate: satisfied / failed / no credential |
| **k** | Check credential expiration (`exp`) and issuance freshness (`iat`) | Expired: reject. Stale (>90d): flag, verifier decides |
| **l** | Fetch StatusList2021 bitstring, check credential's index | Revoked: reject. Suspended: flag. |
| **m** | Compute result: cachet granted if all required predicates satisfied and credential valid | Return structured result |

### 4.6 What the Relay Stores (per session)

| Field | Content | Encrypted? |
|-------|---------|-----------|
| Session ID | Random, >= 128 bits entropy | No (routing key) |
| Request payload | Signed Request Object JWT | No (public to anyone who scans the QR — by design) |
| Response payload | JWE-encrypted VP | **Yes** (only verifier can decrypt) |
| TTL | 5 minutes max | — |

The relay never interprets payloads. It is a key-value store with a TTL.

**What the relay can observe:** That a session was created (by some verifier IP) and that a response was posted (by some holder IP). It cannot read the VP contents. This metadata leakage is acceptable for MVP; future work includes Tor/mixnet routing.

---

## 5. Cryptographic Requirements

### 5.1 Credential Format: SD-JWT VC

Per `draft-ietf-oauth-sd-jwt-vc-08` and `draft-ietf-oauth-selective-disclosure-jwt-13`.

| Requirement | Specification |
|-------------|--------------|
| Issuer signature algorithm | ES256 (P-256) or EdDSA (Ed25519) |
| Selective disclosure hash | SHA-256 (`_sd_alg: sha-256`). No other algorithms accepted. |
| Holder binding | `cnf` claim with `jwk` containing the holder's public key |
| Status | `status` claim with `status_list` entry per W3C Bitstring Status List |
| Expiration | `exp` claim required. Max lifetime: 1 year. |
| Issuance date | `iat` claim required. |

### 5.2 Key Binding JWT (KB-JWT)

Per SD-JWT spec, section on Key Binding.

| Requirement | Specification |
|-------------|--------------|
| Algorithm | ES256 (must match `cnf` key type) |
| `typ` header | `kb+jwt` |
| `nonce` claim | Verifier-supplied, >= 128 bits entropy |
| `aud` claim | Verifier's `client_id` (DID) |
| `iat` claim | Current timestamp. Reject if > 5 minutes old. |
| `sd_hash` claim | `base64url(sha256(sd_jwt~disclosures~))` |
| Signing key | Must match key in credential's `cnf.jwk` |

### 5.3 End-to-End Encryption

| Requirement | Specification |
|-------------|--------------|
| Key agreement | X25519 ephemeral-ephemeral ECDH |
| JWE algorithm | ECDH-ES+A256KW |
| JWE encryption | A256GCM |
| JWE format | Compact Serialization |
| Verifier key lifetime | Single session only. New key per QR refresh. |

### 5.4 Holder Key Management

| Requirement | Specification |
|-------------|--------------|
| Key storage | Hardware-backed: Android StrongBox/TEE, iOS Secure Enclave |
| Key algorithm | P-256 (ES256) — supported by both platforms' secure elements |
| Key attestation | Required at issuance time. Android: Key Attestation certificate chain. iOS: App Attest. |
| Biometric gate | Required before signing KB-JWT for high-value cachets (configurable per Cach'Pack) |
| Key export | Impossible by design (hardware-backed) |
| Device migration | Re-issuance required. Old credential revoked. |

### 5.5 Revocation: Bitstring Status List

Per W3C Bitstring Status List (CR 2025).

| Requirement | Specification |
|-------------|--------------|
| Status purposes | `revocation` and `suspension` (separate lists) |
| Distribution | CDN-cached. `Cache-Control: max-age=300` (5 minutes). |
| Verification requirement | Verifier MUST check on every verification. No skip for "trusted" issuers. |
| Privacy | Verifier fetches entire bitstring (herd privacy). Never queries individual credential status. |
| Issuer correlation | Credential's `statusListIndex` is a persistent identifier — known limitation. Mitigated by CDN caching (issuer doesn't see individual checks). |

---

## 6. Security Properties

When fully implemented, the protocol provides:

| Property | Guarantee | Mechanism |
|----------|-----------|-----------|
| **Authenticity** | Credential was issued by a trusted issuer | Issuer JWS signature verified against resolved DID |
| **Integrity** | Claims have not been modified since issuance | SD-JWT disclosure hashes match `_sd` arrays |
| **Holder binding** | Presenter is the legitimate credential holder | KB-JWT signed by hardware-backed key bound via `cnf` |
| **Freshness** | Presentation was created for this specific session | Nonce in KB-JWT matches verifier's session nonce |
| **Audience restriction** | Presentation cannot be forwarded to another verifier | `aud` in KB-JWT matches requesting verifier's DID |
| **Confidentiality** | Relay and network observers cannot read claims | JWE encryption to verifier's ephemeral key |
| **Minimal disclosure** | Only requested claims are revealed | SD-JWT selective disclosure + Cach'Pack predicate design |
| **Non-revocation** | Credential has not been revoked or suspended | StatusList2021 bitstring check |
| **Verifier authentication** | Holder knows who is requesting their data | Signed Request Object with verifiable verifier DID |
| **Consent** | Holder explicitly approved disclosure | Consent screen before KB-JWT signing; receipt logged |

---

## 7. Future Directions

The MVP protocol (Sections 1–6) uses SD-JWT with selective disclosure. Known limitations and their planned mitigations:

| Limitation | Mitigation | Mechanism |
|-----------|------------|-----------|
| Cross-presentation correlation (T10) | **BBS+ signatures** | Data Integrity `bbs-2023` — unlinkable derived proofs |
| Holder DID correlation | **Pairwise / pseudonymous DIDs** | Per-verifier holder identifiers |
| Predicate boundary leakage | **ZK predicate proofs** | ZK-SNARKs (Plonk/Halo2) — prove `age >= 18` without disclosing age |
| StatusList index as persistent ID | **Accumulator-based revocation** | Holder proves non-revocation without revealing credential index |
| Credential instance linkability | **Batch issuance** | Multiple short-lived instances; each presentation uses a different one |
| Relay metadata correlation | **Multi-relay + Tor/mixnet** | Anonymized transport layer; holder and verifier via different relays |

For implementation status and phased delivery plan, see [`docs/SECURITY_HARDENING_PLAN.md`](SECURITY_HARDENING_PLAN.md).

---

## 8. Consent Receipts and Transparency

After verification completes (regardless of outcome):

1. **Holder's device** generates a consent receipt containing:
   - Verifier identifier (DID)
   - Cach'Pack requested
   - Claims disclosed (list, not values)
   - Timestamp
   - Holder's signature

2. **Receipt hash** is submitted (async) to the Transparency Log — an append-only Merkle tree.
   - The log stores hashes, not receipt contents.
   - The holder retains the full receipt locally.
   - The verifier receives a minimal copy (TTL <= 90 days).

3. **Logging failure handling:**
   - Verification result is valid regardless of logging success (logging is async, non-blocking).
   - Failed log submissions are retried with exponential backoff.
   - If logging fails permanently, the receipt exists locally on both devices.
   - Future: alerting if log submission failure rate exceeds threshold.

---

## 9. What Still Needs Backend

| Component | Purpose | Trust Requirement |
|-----------|---------|-------------------|
| **Credential issuance** | Veriff webhook → Issuance Gateway → SD-JWT VC (OpenID4VCI) | Gateway trusted to issue correctly; holder verifies credential on receipt |
| **Transparency log** | Async receipt hash anchoring | Append-only integrity (Merkle proofs); no PII stored |
| **Pack registry** | Cach'Pack definitions, versions, jurisdiction variants | Signed manifests; could also be bundled with app |
| **Issuer registry** | DID documents, trusted issuer list, revocation endpoints | Signed; cached locally |
| **StatusList2021 host** | Bitstring publication and CDN distribution | Issuer-signed; CDN-cached |

---

## 10. References

| Spec | Version | URL |
|------|---------|-----|
| SD-JWT | draft-ietf-oauth-selective-disclosure-jwt-13 | https://datatracker.ietf.org/doc/draft-ietf-oauth-selective-disclosure-jwt/ |
| SD-JWT VC | draft-ietf-oauth-sd-jwt-vc-08 | https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/ |
| OpenID4VP | openid-4-verifiable-presentations-1_0-23 | https://openid.net/specs/openid-4-verifiable-presentations-1_0.html |
| OpenID4VCI | openid-4-verifiable-credential-issuance-1_0-15 | https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html |
| Bitstring Status List | W3C CR 2025 | https://www.w3.org/TR/vc-bitstring-status-list/ |
| DIF Presentation Exchange | v2.1.1 | https://identity.foundation/presentation-exchange/ |
| BBS+ (v2 target) | Data Integrity BBS Cryptosuites v1.0 | https://www.w3.org/TR/vc-di-bbs/ |
| JWE | RFC 7516 | https://datatracker.ietf.org/doc/html/rfc7516 |
| X25519 | RFC 7748 | https://datatracker.ietf.org/doc/html/rfc7748 |
| Android Key Attestation | — | https://developer.android.com/privacy-and-security/security-key-attestation |
| iOS App Attest | — | https://developer.apple.com/documentation/devicecheck |
