---
domain: verification
type: core
status: draft
last-reviewed: 2026-04-12
---

# Verification

Credential evaluation engine — verifies presentations against policies,
resolves DIDs, checks revocation status, evaluates predicates.

## Ubiquitous Language

| Term | Meaning |
|------|---------|
| Presentation | A bundle of verifiable credentials submitted for evaluation |
| Pack | A reusable template defining which credentials and predicates are required |
| Predicate | A claim-level evaluation rule (e.g., age >= 18) |
| Cachet | The trust mark awarded when a presentation satisfies a pack's requirements |
| Freshness | How recently a credential was issued or verified |
| Session | A time-bound verification interaction between holder and verifier |
| Request Object | A signed JWT containing the verification request parameters |

## Key Concepts

<!-- TODO: define aggregates — Session, Presentation, EvaluationResult -->

## Invariants

<!-- TODO: what must never break — e.g., "a cachet is never awarded without cryptographic verification of all required credentials" -->

## Context Map Relationships

- **registry** (customer-supplier): Fetches pack definitions via GET /packs
- **receipts** (customer-supplier): Sends consent events to receipts log
- **common** (shared-kernel): Uses server infra and generated model types
- **security** (partnership): Uses JWS/JWE verification, DID resolution
- **wallet** (upstream via ACL): Wallet consumes verification API through anti-corruption layer

## Domain Events

<!-- TODO: what events does this domain publish or consume -->
