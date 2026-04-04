# Verification Protocol

## Foundational Principles

1. **Local verification**: If verification can be done without third parties (including our own backend), it must be done so. Credential signature checks and predicate evaluation happen on the verifier's device, never delegated to a server.

2. **Verifier-first**: The app is more than a wallet — it is a wallet that drives outcomes. The verifier (the person demanding trust) is the primary actor. The UX, terminology, and flow design should reflect this.

3. **Untrusted transport**: A relay server may facilitate message delivery between devices, but it is never a trusted party. All payloads are cryptographically signed end-to-end. The relay cannot forge, tamper with, or suppress interactions.

## Protocol Overview

### Actors

- **Verifier**: The person demanding proof (parent hiring a babysitter, buyer on a marketplace)
- **Holder**: The person proving themselves (the babysitter, the seller)
- **Relay**: A stateless message broker. Untrusted. Used only for transport.
- **Issuer**: The entity that issued the credential (e.g. Veriff). Not involved at verification time.

### Flow (single QR scan)

```
Verifier                    Relay                     Holder
   │                          │                          │
   ├─ Create session ────────►│                          │
   │◄─ Session ID + URL ──────│                          │
   │                          │                          │
   ├─ Generate QR ────────────┼──── QR displayed ────────┤
   │  (signed request +       │                   Holder scans QR
   │   relay session URL)     │                          │
   │                          │                          │
   │                          │    Incoming Request shown │
   │                          │    Holder consents        │
   │                          │                          │
   │                          │◄── POST signed           │
   │                          │    presentation ──────────┤
   │                          │                          │
   │◄─ Poll / WS receives ───│                          │
   │   presentation           │                          │
   │                          │                          │
   ├─ Verify LOCALLY:         │                          │
   │  • Check issuer signature│                          │
   │  • Evaluate predicates   │                          │
   │  • Check freshness       │                          │
   │                          │                          │
   ├─ Show result             │                          │
   │                          │                          │
   ├─ Log receipt (async) ────►                          │
   │                          │     Log receipt (async) ──┤
```

### What the QR encodes

- Signed verification request (which Cach'Pack, which predicates)
- Relay session URL (e.g. `https://relay.cachet.id/sessions/{id}`)
- Verifier identity (display name, optional DID)
- Requested retention period

### What the relay stores (per session)

- Session ID (random, short-lived)
- Request payload (from verifier)
- Response payload (from holder, once submitted)
- TTL: sessions expire after ~5 minutes

The relay never interprets payloads. It is a key-value store with a TTL.

### What happens locally on the verifier's device

1. Parse the holder's signed presentation
2. Verify the issuer's signature against known/trusted DIDs
3. Evaluate each predicate against disclosed claims
4. Determine freshness from credential expiration/issuance dates
5. Compute result: cachet granted or not, with per-predicate breakdown

No backend call is needed for steps 1-5.

### Trust model

| Component | Trusted? | Why |
|-----------|----------|-----|
| Issuer (Veriff) | Yes | Their signature on the credential is the root of trust |
| Holder's device | Partially | Holder controls what to disclose; can't forge issuer signatures |
| Verifier's device | Yes (to verifier) | Runs the evaluation; verifier trusts their own device |
| Relay | No | Just transport; can't read, forge, or suppress signed payloads |
| Cachet backend | No (for verification) | Not involved in real-time verification. Used for issuance and async receipt logging |

## What still needs backend

- **Credential issuance**: Veriff webhook → Issuance Gateway → SD-JWT VC (OpenID4VCI)
- **Transparency log**: Async receipt anchoring after verification completes
- **Pack registry**: Cach'Pack definitions. Could also be bundled with the app or fetched lazily.
