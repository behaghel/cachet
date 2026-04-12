---
story: verification-result
domain: wallet/verification-flow
status: draft
last-reviewed: 2026-04-12
persona: returning-holder
---

# Verification Result

**As a** returning-holder (or verifier),
**I want to** see the outcome of a verification,
**so that** I know whether the credentials passed or failed and why.

## Wireframes

- `cachet-04-result-pass.svg` — Pass result (all predicates satisfied)
- `cachet-04-result-pass-age.svg` — Pass result for age verification
- `cachet-05-result-fail.svg` — Fail result (predicates not satisfied)
- `cachet-05-result-fail-seller.svg` — Fail result for seller verification

## Acceptance Criteria

- **AC-1:** Pass result shows green success state with cachet badge
- **AC-2:** Fail result shows red failure state with clear reason
- **AC-3:** Predicate results are listed individually (pass/fail per predicate)
- **AC-4:** Result shows which pack was verified against
- **AC-5:** Result is visible to both holder and verifier (both sides see the outcome)
- **AC-6:** User can dismiss result and return to their previous context
- **AC-7:** A consent receipt is generated for every verification (pass or fail)

## Demo Scenarios

- **happy:** Pass result for Childcare Readiness and Age Verification
- **seller-only:** Fail result for Safe Seller (missing predicates)

## Navigation

```
Incoming Request → "Verify & Share" → Verification Result (pass or fail)
                                       └── Dismiss → Activity tab

Show QR (verifier) → Holder scans → Verification Result (pass or fail)
                                      └── Dismiss → Activity tab
```
