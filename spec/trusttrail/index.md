# TrustTrail Domain

**Type:** core
**Language:** kotlin (KMP shared + Android)

## Purpose

TrustTrail extracts behavioral trust evidence from a user's email inbox —
entirely on-device — and produces structured, verified claims that feed into
cachet issuance. It transforms "rubbish" transactional emails into the foundation
of a user's trust profile as a cyber-citizen.

## Ubiquitous Language

| Term | Definition |
|------|-----------|
| **TrustTrail** | The accumulated set of verified behavioral claims from a user's email history |
| **Platform** | A known service (Vinted, HomeExchange, Care.com...) whose emails we can extract claims from |
| **Claim** | A structured fact extracted from an email (e.g., "sale of €45 on Vinted on 2026-03-15") |
| **Trust Level** | How the claim's authenticity was established: `cryptographic` (DKIM re-verified on device) or `mta_attested` (MTA's Authentication-Results header trusted) |
| **Confidence** | How certain the extraction pattern is about what it read (0.0–1.0). Minimum threshold: 0.7 |
| **Platform Discovery** | The headers-only scan that identifies which known platforms appear in the inbox |
| **Platform Consent** | The user's explicit approval to process emails from a specific platform |
| **Evidence Bundle** | The set of user-approved claims submitted to the issuance gateway |
| **Cold Start Scan** | The initial historical scan (configurable, default 6 months) that bootstraps the trust profile |
| **Polling Cycle** | Periodic check for new emails from consented platforms (WorkManager, >= 15 min) |

## Key Invariants

1. Raw email content never leaves the device
2. OAuth tokens never leave the device
3. Only user-approved, structured claims are transmitted to the backend
4. Forwarded emails are always rejected (break DKIM chain)
5. The module has zero imports from existing wallet domain code
6. All entry points are gated behind a feature flag
7. Foundational identity (Veriff) is a prerequisite for behavioral cachets

## Boundaries

- **Upstream:** Gmail API, Microsoft Graph API (email providers)
- **Downstream:** Issuance gateway (consumes evidence bundles — bridge spec TBD)
- **No dependency on:** wallet, verification, credentials, or any existing domain
