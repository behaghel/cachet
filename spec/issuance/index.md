---
domain: issuance
type: core
status: draft
last-reviewed: 2026-04-12
---

# Issuance

Credential lifecycle management — OAuth2 token exchange, SD-JWT VC issuance
via OpenID4VCI, Veriff identity verification webhooks, StatusList2021 revocation.

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

## Context Map Relationships

- **wallet** (open-host-service): Wallet consumes issuance via OpenID4VCI protocol
- **common** (shared-kernel): Uses server infra and generated model types
- **security** (partnership): Uses SD-JWT signing, credential building

## Domain Events

<!-- TODO: credential.issued, credential.revoked -->
