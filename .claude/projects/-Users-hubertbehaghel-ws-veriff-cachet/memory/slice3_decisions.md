---
name: Slice 3 decisions
description: Testing quality refactoring — compile-time guarantees over runtime contract tests, add -race, delete dead schema tests
type: project
---

Aligned decisions from Slice 3 (Testing quality):

1. Favour compile-time guarantees via generated types over runtime schema contract testing
2. Delete dead `tests/schema-integration/schema_compatibility_test.go` (skipped, buggy)
3. Add `-race` to all go test invocations (CI and devenv scripts)
4. Unit tests for pure domain functions: validateVeriffSession (table-driven), calculateAge (leap years)
5. After DI refactoring: inject lightweight deps in tests, no RSA keygen per test
6. Test helpers to eliminate struct repetition (builder pattern)
7. Add tests for receipts-log after Pattern A refactoring
8. Coverage floor in CI, ratchet up over time

**Why:** Current tests assert stub behavior, miss domain logic, have dead code, and don't detect race conditions.
**How to apply:** Testing improvements depend on Slice 1 (DI) and Slice 2 (domain extraction) landing first.
