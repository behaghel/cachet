---
name: Slice 5 decisions
description: Observability — request-scoped logging, graceful shutdown, structured health/errors, OTEL planned
type: project
---

Aligned decisions from Slice 5 (Observability & operational readiness):

1. Request-scoped logging middleware: zerolog + request_id in context. Handlers use log.Ctx(r.Context()).
2. Replace Chi middleware.Logger with zerolog-based request logger. One format, one destination.
3. Graceful shutdown (SIGTERM handling) in all services, via shared server builder in common/.
4. Structured health response: {"status","service","version"}. Separate /ready (dependency checks) from /health (liveness).
5. Structured error responses per spec Error schema (dovetails with Slice 2).
6. OpenTelemetry (metrics + tracing) planned — not deferred indefinitely, user values observability highly.
7. Design middleware slot in common/ to accommodate OTEL when it lands.

**Why:** No request correlation, no graceful shutdown, two conflicting log formats, plain-text errors, zero metrics/tracing.
**How to apply:** Priorities 1-2 land with Slice 1's shared scaffolding. Priority 3 (OTEL) is a near-term follow-up, not a distant backlog item.
