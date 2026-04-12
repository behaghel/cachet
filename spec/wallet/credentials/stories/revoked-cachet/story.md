---
story: revoked-cachet
domain: wallet/credentials
status: draft
last-reviewed: 2026-04-12
persona: revoked-holder
---

# Revoked Cachet

**As a** revoked-holder,
**I want to** clearly understand that a credential has been revoked and what I can do about it,
**so that** I'm not confused or alarmed, and I know my next steps.

## Wireframes

- `cachet-01-detail-revoked.svg` — Revoked cachet detail with revocation banner
- `holder-04-vault-revoked.svg` — Vault showing revoked card with visual distinction

## Acceptance Criteria

- **AC-1:** Revoked cachet card in vault shows distinct visual treatment (muted colors, revoked badge)
- **AC-2:** Revoked detail screen shows revocation banner with reason (when available)
- **AC-3:** Revoked detail still shows original predicates but marked as no longer valid
- **AC-4:** CTA to re-acquire the credential is available
- **AC-5:** Revoked status is determined by StatusList2021 check, not local state
- **AC-6:** Active cachets remain visually unaffected when one is revoked

## Demo Scenarios

- **revoked:** Vault with one revoked identity credential

## Navigation

```
My Cachets (revoked scenario) → Tap revoked card → Revoked Detail
                                                     ├── Revocation banner
                                                     ├── CTA: Re-acquire
                                                     └── Back → My Cachets
```

## References

- `docs/SPEC_REVOKED_CACHET_UX.md` — detailed revocation UX specification
