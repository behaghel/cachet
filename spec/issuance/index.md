---
domain: issuance
status: draft
last-reviewed: 2026-04-12
---

# Issuance

> See spec/domains.yaml for description, classification, code paths, and context map.

## Ubiquitous Language

| Term | Meaning |
|------|---------|
| Credential Offer | An OpenID4VCI offer containing the issuer's endpoint and pre-authorized code |
| Pre-Authorized Code | A one-time code tied to a Veriff verification session |
| SD-JWT VC | A Selective Disclosure JWT Verifiable Credential — holder controls which claims are revealed |
| StatusList2021 | A bitstring-based revocation mechanism where each credential has an index |
| Veriff Session | An identity verification session with the Veriff provider |
| Credential Builder | Assembles the SD-JWT VC with selective disclosures and status entry |

## Key Concepts

<!-- TODO: define aggregates — IssuanceSession, Credential, StatusList -->

## Invariants

<!-- TODO: e.g., "a credential is never issued without a completed Veriff session", "revocation index is unique per credential" -->

## Domain Events

<!-- TODO: credential.issued, credential.revoked -->
