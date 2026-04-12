---
story: scan-to-verify
domain: wallet/verification-flow
status: draft
last-reviewed: 2026-04-12
persona: returning-holder
---

# Scan to Verify

**As a** returning-holder,
**I want to** scan a verifier's QR code and review the verification request,
**so that** I can decide whether to share my credentials.

## Wireframes

- `cachet-02-qr-scan.svg` — QR scanner camera view
- `cachet-03-incoming-request.svg` — Verification request with required disclosures

## Acceptance Criteria

- **AC-1:** QR scanner opens camera with clear viewfinder frame
- **AC-2:** Scanning a valid verifier QR decodes the session and shows Incoming Request
- **AC-3:** Incoming Request shows: verifier name, requested pack, required disclosures
- **AC-4:** Each disclosure is listed with its type (selective/always/never) so the holder understands what will be shared
- **AC-5:** User can "Verify & Share" (consent) or "Decline" (cancel)
- **AC-6:** Invalid QR shows error feedback, doesn't crash

## Demo Scenarios

- **happy:** Scanning QR → Incoming Request for Childcare Readiness pack

## Navigation

```
Activity tab → FAB → "Scan" → QR Scanner → Scan QR → Incoming Request
                                                       ├── "Verify & Share" → Result
                                                       └── "Decline" → Back to Activity
```
