# Veriff Liveness Detection — Research for #58

**Date:** 2026-04-16
**Purpose:** Evaluate Veriff's liveness capabilities for holder presence verification before KB-JWT signing.

## Products Overview

Veriff offers three biometric products — only one fits our use case cleanly:

| Product | What it does | Our fit |
|---------|-------------|---------|
| **Biometric Liveness** | Stateless "is this a live person?" check. Single selfie, no stored template. | Not enough — proves liveness but not *which* person. |
| **Biometric Authentication** | Matches a new selfie against a stored biometric template from a prior enrollment + liveness. | Best fit — proves the holder is the same person who enrolled during issuance. |
| **Identity Verification** | Full document + selfie + liveness. | Already used at issuance time. Overkill for re-verification. |

### Recommendation: Biometric Authentication

Standalone liveness (product 1) only proves "a real human is present" — it doesn't prove it's *the same* human who holds the credential. Biometric Authentication (product 2) matches against the enrollment from the original Veriff identity verification session (which we already run during issuance). This gives us: **liveness + face match = proof the credential holder is physically present.**

As of January 2026, Veriff added a **"Selfie-to-Selfie"** mode that can match against a prior selfie without full document re-enrollment, which simplifies integration.

## Android SDK

- **Current version:** 8.0.0 (March 2026)
- **Dependency:** `implementation("com.veriff:veriff-library:$version")`
- **Maven repo:** `https://cdn.veriff.me/android/`
- **minSdkVersion:** 26 (Android 8.0+)
- **Requires:** Kotlin 1.9.0+, AGP 7.2.0+, AndroidX
- **Transitive deps of note:** ML Kit face detection, OkHttp 4.10, BouncyCastle 1.76, Compose BOM 2023.08

### Permissions (auto-merged)

Runtime: `CAMERA`, `RECORD_AUDIO`
Auto-granted: `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `NFC`, `VIBRATE`

All required — removing any causes crashes.

### SDK Flow

```
1. Backend creates session → POST /v1/sessions → gets sessionUrl
2. App launches SDK → Sdk.createLaunchIntent(activity, sessionUrl)
3. SDK captures passive selfie (no user actions — no blinking/nodding)
4. SDK submits images to Veriff servers automatically
5. App receives SDK result: DONE | CANCELED | ERROR
6. Actual decision arrives async via webhook to backend
```

Full branding customization available (colors, fonts, logo, 44+ languages).

## API Integration

### Session Creation

```http
POST /v1/sessions
X-AUTH-CLIENT: [API-KEY]
Content-Type: application/json

{
  "verification": {
    "endUserId": "holder-uuid",
    "vendorData": "session-correlation-id"
  }
}
```

Returns `verification.url` (for SDK) and `verification.id` (for tracking).

### Decision Webhook

```json
{
  "status": "success",
  "verification": {
    "id": "uuid",
    "status": "approved",       // or "declined"
    "code": 9001,               // 9001=approved, 9102=declined
    "reason": null,             // human-readable on failure
    "reasonCode": null,         // machine-readable on failure
    "decisionTime": "ISO 8601"
  }
}
```

**Webhook security:** HMAC-SHA256 of raw body signed with shared secret (`x-hmac-signature` header). Must respond HTTP 200 within 5,000ms. At-least-once delivery.

### Key Decline Reason Codes

| Code | Meaning |
|------|---------|
| 105 | Suspicious behaviour |
| 106 | Known fraud |
| 503 | Attempted deceit |
| 515 | Deceit via device screen |
| 546 | Face image quality insufficient |
| 547 | Face missing |

### Session Lifecycle

`created` → `started` → `submitted` → `approved` / `declined` / `expired` / `abandoned`

## Anti-Spoofing Guarantees

- **ISO/IEC 30107-3 Level 1 AND Level 2** PAD certified
- Tested by **iBeta** (NIST/NVLAP-accredited lab), Level 2 achieved September 2024
- **0% IAPAR** (Imposter Attack Presentation Accept Rate) — no Level 2 attacks succeeded

Level 2 tests included:
- 3D masks (resin, latex, silicone) costing up to $300
- Realistic dolls
- Deepfake videos
- Attacks prepared over 2-4 days by moderately skilled attackers

Multi-layered analysis: skin texture, facial contours, light reflections, device/emulator detection, virtual camera detection, behavioral signals.

## Performance

| Metric | Value |
|--------|-------|
| Liveness decision | Sub-second (fully automated) |
| Biometric authentication | ~1 second |
| Passive capture | Single selfie, no user actions |
| Webhook timeout | 5,000ms acknowledgment required |

## Offline Capability

**None.** The SDK captures on-device but all decisions are server-side. ML Kit face detection is used only for on-device face framing/guidance during capture. Network connectivity is mandatory.

This aligns with our existing architecture — the holder must be online to complete a verification flow anyway (relay communication, presentation submission).

## Pricing

Not publicly broken out for standalone products. Indicative ranges:

| Volume | Per-session estimate |
|--------|---------------------|
| <500/month | $4–5 |
| 500–5,000/month | $3–4 |
| 5,000–20,000/month | $2.50–3.50 |
| 20,000+/month | $2–3 (custom contract) |

Biometric Authentication is an add-on with its own per-session fee. Multi-year commitments unlock 10-20% discounts.

## Integration Plan for Cachet

### What we already have

- `VeriffService.kt` in `mobile/androidApp/` — wraps SDK launch, returns `VeriffResult(Success|Failure|Cancelled)`
- `services/issuance-gateway/internal/veriff/` — handles Veriff webhooks with HMAC verification
- Veriff identity verification sessions at issuance time create the **enrollment biometric template** we can match against

### What we need

1. **Backend: Liveness session endpoint** — the wallet requests a liveness session from our backend (not Veriff directly). Our backend creates the Veriff Biometric Authentication session, linking to the holder's original enrollment `endUserId`.

2. **Backend: Liveness webhook handler** — receives the Veriff decision, stores pass/fail keyed to the verification session. The wallet polls or receives a push to learn the outcome.

3. **Android: Liveness screen** — after consent, if CachPack requires liveness, launch the Veriff SDK. On `DONE`, wait for backend confirmation. On `CANCELED`/`ERROR`, show failure screen.

4. **CachPack policy flag** — per-pack `requiresLiveness: true` configuration, checked before KB-JWT signing.

5. **Gate KB-JWT signing** — `VerificationUseCase.respondViaRelay()` must check liveness result before calling `KBJWTBuilder.build()`.

### Sequence: Consent → Liveness → Sign

```
Holder                    App                     Backend              Veriff
  |                        |                        |                    |
  |-- tap Verify & Share ->|                        |                    |
  |                        |-- check pack policy -->|                    |
  |                        |<- requiresLiveness ----|                    |
  |                        |                        |                    |
  |                        |-- POST /liveness ------>|                    |
  |                        |                        |-- POST /v1/sessions ->|
  |                        |                        |<-- sessionUrl --------|
  |                        |<-- sessionUrl ---------|                    |
  |                        |                        |                    |
  |<-- launch Veriff SDK --|                        |                    |
  |-- selfie captured ---->|--------- SDK submits images ------------->|
  |                        |                        |                    |
  |                        |                        |<--- webhook: approved/declined
  |                        |<-- liveness passed ----|                    |
  |                        |                        |                    |
  |                        |-- sign KB-JWT -------->|                    |
  |                        |-- send presentation -->|                    |
  |<-- result screen ------|                        |                    |
```

### Open question

Should we use **Biometric Liveness** (stateless, cheaper, just proves "live human") or **Biometric Authentication** (matches against enrollment, proves "same person")? The answer depends on threat model:

- If the threat is *phone left unlocked on a table* → Biometric Authentication is needed (different person)
- If the threat is *automated/bot attack on the signing endpoint* → Biometric Liveness suffices

Given the issue #58 framing ("nobody else can use my phone to impersonate me"), **Biometric Authentication is the right choice** — it proves the person present is the credential holder, not just any live human.
