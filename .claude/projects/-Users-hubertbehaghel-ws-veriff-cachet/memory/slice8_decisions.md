---
name: Slice 8 decisions
description: Cross-cutting synthesis — quality tier duplication, JSON casing, execution order, local tracking doc
type: project
---

Aligned decisions from Slice 8 (Cross-cutting synthesis):

1. Quality tier logic: backend determines tier at issuance, mobile reads and displays — no recalculation. Remove CredentialQuality.kt weighted-average recomputation.
2. JSON casing per governing spec: OAuth = snake_case (RFC 6749), VC = camelCase (W3C), quality fields in VC = camelCase.
3. Skeleton service deletion must include doc update flagging these as "planned but not yet implemented".
4. Schema integration test deletion must be replaced with structural compatibility check — CI step verifying generated Kotlin compiles against mobile usage.
5. SDK stubs: remove or invest. Don't keep hollow directories.
6. Documentation (ARCHITECTURE.md, SECURITY.md) must distinguish current state from aspirational.
7. Track refactoring via local doc, not GitHub issues (project is not public enough for that overhead).

**Why:** Duplicated divergent business logic between backend/mobile, inconsistent casing, aspirational docs masquerading as reality.
**How to apply:** Phase 0 (cleanup) is immediate. Phases 1-6 follow the dependency chain in the tracking doc.
