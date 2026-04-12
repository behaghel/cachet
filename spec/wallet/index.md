---
domain: wallet
status: draft
last-reviewed: 2026-04-12
---

# Wallet

> See spec/domains.yaml for description, classification, code paths, and context map.

## Ubiquitous Language

| Term | Meaning |
|------|---------|
| Vault | The user's collection of stored credentials |
| Cachet | A trust mark displayed as a badge — the visual representation of a verified credential |
| Trust Pack | A named bundle of predicates the user can earn (e.g., "Childcare Readiness") |
| Holder | The person who owns credentials in their wallet |
| Verifier | A business or person requesting credential verification |
| Disclosure | A specific claim within an SD-JWT that the holder can selectively reveal |
| Hardware-Backed | A credential whose signing key is stored in StrongBox/Secure Enclave |

## Subdomains

- **onboarding** — First-launch experience, educate user on trust concept
- **credentials** — Vault management, cachet detail, revocation display
- **verification-flow** — QR scanning, incoming requests, consent, results

## Key Concepts

<!-- TODO: define aggregates — StoredCredential, VerificationSession, ConsentDecision -->

## Invariants

<!-- TODO: e.g., "credentials are only presented after explicit user consent", "signing keys must be hardware-backed" -->

## Domain Events

<!-- TODO: credential.stored, credential.presented, consent.granted, consent.denied -->
