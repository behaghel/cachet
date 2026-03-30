---
name: Slice 7 decisions
description: Mobile wallet — delete duplicate models, typed credentialSubject, config module, fix OAuth content-type, persist consent receipts
type: project
---

Aligned decisions from Slice 7 (Mobile wallet):

1. Delete duplicate `network/model/NetworkModels.kt` — use types in `OpenID4VCIClient.kt` until generated types replace them.
2. Remove println debug statements from OpenID4VCIClient.
3. Create a config module for environment-specific values (base URL, timeouts, feature flags). User expected one already existed — high priority.
4. Fix OAuth content-type to application/x-www-form-urlencoded (coordinated with backend).
5. Use generated Kotlin types once spec is fixed. Replace `Map<String, JsonElement>` credentialSubject with typed model.
6. Parse dates at deserialization time (custom serializer or generated model).
7. Persist consent receipts via SQLDelight (same pattern as CredentialRepositoryImpl).
8. HTTP resilience: configure timeouts, add retry for transient failures.
9. Wire HttpTransparencyLogRepository in production DI (replace MockTransparencyLogRepository).
10. Once generated types adopted, replace hand-written network models with generated ones.

**Why:** 3-way model duplication, untyped credentialSubject, hardcoded IP, debug prints in prod code, in-memory repos lose data on process death.
**How to apply:** Priority 1 (correctness) can land independently. Priority 2 (type safety) depends on Slice 4 spec fixes.
