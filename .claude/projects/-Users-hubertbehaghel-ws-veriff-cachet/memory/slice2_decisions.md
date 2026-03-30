---
name: Slice 2 decisions
description: Issuance gateway refactoring — OAuth form-encoded, remove token store, webhook auth, fix concurrency and session binding
type: project
---

Aligned decisions from Slice 2 (Issuance Gateway deep-dive):

1. OAuth /oauth/token must use application/x-www-form-urlencoded per RFC 6749 — both spec and code need fixing
2. Remove dead `accessTokens` map entirely — JWT signature + expiry is sufficient
3. Add HMAC-SHA256 webhook signature verification for Veriff webhooks (secret via DI)
4. Fix concurrent map access — introduce SessionStore interface with mutex or sync.Map
5. Fix session-to-credential binding — token must map to specific Veriff session ID
6. Extract internal packages: internal/veriff, internal/credential, internal/oauth
7. Fix calculateAge leap year bug (use month+day comparison not YearDay)
8. Add input validation for format enum, client_id, session_id
9. Type CredentialResponse.Credential as VerifiableCredential not interface{}

**Why:** Critical security gaps (no webhook auth, race conditions, broken session binding), domain logic buried in handlers, dead code.
**How to apply:** Security fixes (1-5) are highest priority. Domain extraction (6) enables testability. Correctness (7-9) follows.
