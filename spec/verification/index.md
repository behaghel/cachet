---
domain: verification
status: draft
last-reviewed: 2026-04-12
---

# Verification

> See spec/domains.yaml for description, classification, code paths, and context map.

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

## Domain Events

<!-- TODO: what events does this domain publish or consume -->
