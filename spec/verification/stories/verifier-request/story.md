---
story: verifier-request
domain: verification
status: draft
last-reviewed: 2026-04-12
persona: verifier
---

# Verifier Request

**As a** verifier,
**I want to** select a Trust Pack and generate a QR code,
**so that** a holder can scan it and share their credentials with me.

## Wireframes

- `verify-01-new-request.svg` — Pack picker in verifier mode (choose what to verify)
- `verify-02-show-qr.svg` — QR code display while waiting for holder to scan

## Acceptance Criteria

- **AC-1:** Verifier can access "New Request" from Activity tab FAB
- **AC-2:** Pack picker in verifier mode shows same packs as holder mode
- **AC-3:** Selecting a pack generates a verification session and displays QR code
- **AC-4:** QR screen shows: pack name, session status (waiting/scanned/complete)
- **AC-5:** QR encodes a session URL that the holder's scanner can decode
- **AC-6:** When holder completes verification, verifier sees the result automatically

## Demo Scenarios

- **happy:** Select Childcare Readiness → Show QR → Holder scans → Pass result

## Navigation

```
Activity tab → FAB → "New Request" → Pack Picker (verifier mode)
                                      → Select pack → Show QR (waiting)
                                                       → Holder scans → Result
```
