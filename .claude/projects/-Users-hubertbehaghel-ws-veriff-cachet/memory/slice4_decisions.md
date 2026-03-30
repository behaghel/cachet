---
name: Slice 4 decisions
description: API schema design — single uber spec with tagged paths, per-service specs derived mechanically, close generation loop
type: project
---

Aligned decisions from Slice 4 (API & schema design + spec/code reconciliation):

1. **Single uber spec** `schemas/openapi.yaml` as sole source of truth. Paths tagged by service.
2. **Per-service specs derived mechanically** via redocly split/filter into `api/`. Never hand-edited. Generated artifacts.
3. Fix all spec/code discrepancies: /healthz→/health, add VeriffSession.verification sub-object, add Confidence/Authenticity fields, nested credentialSubject shape, camelCase throughout.
4. Add missing schemas: Pack, VerifyRequest, VerifyResponse (verifier), receipts-log endpoints.
5. Go services must import `generated/go/models` — compile-time enforcement.
6. Mobile must use generated Kotlin types (or type-alias). Replace `Map<String, JsonElement>` with typed models.
7. CI check: verify generated types are actually imported by services and mobile.
8. Consider lighter Kotlin generator (openapi-generator-cli produces 45+ infrastructure files).
9. Delete hand-maintained `api/openapi.{verifier,registry,receipts}.yaml` stubs — replaced by generated per-service specs.

**Why:** 4 specs with 3 stubs, generated types unused by anyone, spec diverges from code on almost every surface.
**How to apply:** Spec fixes must land before Slice 1's "use generated types" work, since services will import what the spec produces.
