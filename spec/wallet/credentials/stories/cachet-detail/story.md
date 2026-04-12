---
story: cachet-detail
domain: wallet/credentials
status: draft
last-reviewed: 2026-04-12
persona: returning-holder
---

# Cachet Detail

**As a** returning-holder,
**I want to** see the full details of a cachet,
**so that** I understand what it proves and how it's secured.

## Wireframes

- `cachet-01-detail.svg` — Standard cachet detail view
- `cachet-01-detail-hardware.svg` — Detail with hardware-backed indicator

## Acceptance Criteria

- **AC-1:** Shows cachet name, badge icon, and trust status prominently
- **AC-2:** Lists all predicates with their evaluation status (pass/fail)
- **AC-3:** Shows credential metadata: issuer, issuance date, expiry
- **AC-4:** Hardware-backed indicator shown when signing key is in StrongBox/Secure Enclave
- **AC-5:** Shows credential freshness status
- **AC-6:** Back navigation returns to My Cachets

## Demo Scenarios

- **happy:** Active credential with all predicates passing, hardware-backed variant

## Navigation

```
My Cachets → Tap card → Cachet Detail
                         ├── Hardware indicator (when keyAlias present)
                         └── Back → My Cachets
```
