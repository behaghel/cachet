---
domain: common
type: shared-kernel
status: draft
last-reviewed: 2026-04-12
consumers:
  - verification
  - issuance
  - registry
  - receipts
  - relay
---

# Common (Shared Kernel)

HTTP server infrastructure (chi router, middleware, health, logging, tracing),
OpenAPI-generated model types, and shared schemas.

## Shared Types

<!-- TODO: list every type in the kernel and which domains consume it -->

## Change Rules

- No unilateral changes — all consumers must agree
- Contract tests required from each consumer
- Prefer value objects over entities
