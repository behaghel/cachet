# Plan: Android/Kotlin Unit Test Suite Refresh

## Context

PR #66 (security-hardening-p1) refactored `CredentialSubject` from `Map<String, Any>` to
a typed data class hierarchy. This broke 6 test files which were gutted to TODO stubs
rather than rewritten, removing **25 test methods** and all mock implementations.

Current state: 48 active tests across 4 files; 6 empty stubs; critical business logic
(issuance, verification, crypto parsing) completely untested. The client tier lacks the
safety net needed before shipping.

**Goal:** Rebuild the test suite to ~109 active tests across the model, use-case, crypto,
and mapper layers — prioritized by security risk.

---

## Architectural Decisions

1. **Hand-written fakes, no mockk.** The codebase has zero mockk usage. Two production
   fakes already exist (`InMemoryConsentReceiptRepository`, `MockTransparencyLogRepository`).
   Interfaces are small (3-6 methods) — manual fakes are low-maintenance and consistent
   with the existing pattern.

2. **Add `kotlinx-coroutines-test:1.7.3` to commonTest.** Every use-case function is
   `suspend`. Without `runTest {}` we cannot test them. Matches the existing
   `kotlinx-coroutines-core:1.7.3`.

3. **Shared test fixtures in commonTest.** Fakes and builders live under
   `commonTest/.../testfixtures/` to avoid duplication across test files.

4. **Delete dead stubs.** `SchemaCompatibilityTest` (superseded by ContractTest) and
   `OpenID4VCIClientTest` (thin interface, covered via IssuanceUseCaseTest) get deleted
   rather than rewritten.

5. **VerificationUseCase deferred.** It has 6 constructor deps including `expect` classes
   (`KeyManager`, `DIDResolver` with `HttpClient`). Proper testing requires an
   `androidUnitTest` source set or logic extraction — follow-up work.

---

## Slices

### Slice 0 — Infrastructure

**Add dependency** in `mobile/shared/build.gradle.kts`:
```kotlin
val commonTest by getting {
    dependencies {
        implementation(kotlin("test"))
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    }
}
```

**Create shared fakes** under `mobile/shared/src/commonTest/kotlin/id/cachet/wallet/testfixtures/`:

| File | Implements | Notes |
|------|-----------|-------|
| `FakeCredentialRepository.kt` | `CredentialRepository` | In-memory `MutableList<StoredCredential>` |
| `FakeOpenID4VCIClient.kt` | `OpenID4VCIClient` | Configurable `Result` fields per method |
| `FakeVerifierClient.kt` | `VerifierClient` | Configurable return values |
| `FakeRelayClient.kt` | `RelayClient` | Records posted payloads for assertion |
| `TestFixtures.kt` | — | `makeCredential()`, `makeStoredCredential()`, `makeConsentReceipt()`, `makePresentationRequest()` builders with sensible defaults |

**Delete dead stubs:**
- `mobile/shared/src/commonTest/.../integration/SchemaCompatibilityTest.kt`
- `mobile/shared/src/commonTest/.../network/OpenID4VCIClientTest.kt`

Tests added: 0 (infrastructure only)

---

### Slice 1 — SDJWTParser (security-critical, pure logic)

**File:** `mobile/shared/src/commonTest/.../domain/crypto/SDJWTParserTest.kt` (new)
**Production:** `mobile/shared/src/commonMain/.../domain/crypto/SDJWTParser.kt`

| Test | What it verifies |
|------|-----------------|
| `parse splits issuer JWT and disclosures` | Known SD-JWT → correct issuerJWT, disclosure count, claims |
| `parse requires tilde-separated parts` | No `~` → exception |
| `parse ignores empty segments between tildes` | `"jwt~~disc~"` → 1 disclosure |
| `parse skips KB-JWT segments` | 3-dot parts filtered out |
| `parse handles malformed base64 gracefully` | Invalid base64 → 0 disclosures, no crash |
| `selectivePresentation includes only requested claims` | 3 disclosures, request 2 → output has 2 |
| `selectivePresentation ends with trailing tilde` | KB-JWT slot preserved |

Tests added: **7**

---

### Slice 2 — KBJWTBuilder.computeSDHash (security-critical, pure logic)

**File:** `mobile/shared/src/commonTest/.../domain/crypto/KBJWTBuilderTest.kt` (new)
**Production:** `mobile/shared/src/commonMain/.../domain/crypto/KBJWTBuilder.kt`

| Test | What it verifies |
|------|-----------------|
| `computeSDHash returns base64url SHA-256` | No `+`, `/`, `=` chars; non-empty |
| `computeSDHash is deterministic` | Same input → same output |
| `computeSDHash differs for different inputs` | Two inputs → two outputs |

Note: `build()` requires `KeyManager` (expect class) — cannot test in commonTest. Deferred.

Tests added: **3**

---

### Slice 3 — VerifiableCredential model (rewrite CredentialTest stub)

**File:** `mobile/shared/src/commonTest/.../domain/model/CredentialTest.kt` (replace stub)
**Production:** `mobile/shared/src/commonMain/.../domain/model/Credential.kt`

| Test | What it verifies |
|------|-----------------|
| `isExpired false when expirationDate null` | Null safety |
| `isExpired false when future date` | Normal valid credential |
| `isExpired true when past date` | Expired credential |
| `isExpired false when unparseable date` | Graceful fallback |
| `getIssuanceInstant parses valid ISO-8601` | Happy path |
| `getIssuanceInstant null for garbage date` | Error handling |
| `getSubjectId returns subject id` | Field access |
| `data class serialization round-trips` | JSON encode/decode |

Tests added: **8**

---

### Slice 4 — CredentialQuality (pure logic, new test file)

**File:** `mobile/shared/src/commonTest/.../domain/model/CredentialQualityTest.kt` (new)
**Production:** `mobile/shared/src/commonMain/.../domain/model/CredentialQuality.kt`

| Test | What it verifies |
|------|-----------------|
| `fromString maps basic` | Enum parsing |
| `fromString maps premium case-insensitive` | Case handling |
| `fromString maps gold` | Enum parsing |
| `fromString returns BASIC for null` | Null default |
| `fromString returns BASIC for unknown` | Unknown default |
| `extractQuality uses verificationLevel from subject` | Extension function |
| `extractQuality uses metrics when present` | Metrics propagation |
| `extractQuality returns defaults when metrics absent` | Null safety |
| `getQualityBadge includes emoji and display name` | Display formatting |
| `meetsQualityThreshold true for high-quality` | Threshold logic |

Tests added: **10**

---

### Slice 5 — IssuanceUseCase (rewrite stub)

**File:** `mobile/shared/src/commonTest/.../domain/usecase/IssuanceUseCaseTest.kt` (replace stub)
**Production:** `mobile/shared/src/commonMain/.../domain/usecase/IssuanceUseCase.kt`
**Deps:** `FakeCredentialRepository`, `FakeOpenID4VCIClient`, `runTest`

| Test | What it verifies |
|------|-----------------|
| `requestCredential stores credential on success` | Happy path end-to-end |
| `requestCredential fails on token error` | Error propagation |
| `requestCredential fails on credential error` | Error propagation |
| `getStoredCredentials empty initially` | Empty state |
| `getStoredCredentials returns stored` | Retrieval |
| `revokeCredential marks as revoked` | State mutation |
| `getCredentialsByIssuer filters correctly` | Filtering logic |

Tests added: **7**

---

### Slice 6 — ConsentUseCase (rewrite TransparencyLogTest stub)

**File:** `mobile/shared/src/commonTest/.../domain/usecase/ConsentUseCaseTest.kt` (new, absorbs TransparencyLogTest scope)
**Production:** `mobile/shared/src/commonMain/.../domain/usecase/ConsentUseCase.kt`
**Deps:** `FakeCredentialRepository`, `InMemoryConsentReceiptRepository` (existing), `MockTransparencyLogRepository` (existing), `runTest`

| Test | What it verifies |
|------|-----------------|
| `validatePresentationRequest true for valid` | Happy path |
| `validatePresentationRequest false for blank RP` | Validation |
| `validatePresentationRequest false for short purpose` | Validation |
| `validatePresentationRequest false for empty predicates` | Validation |
| `generateConsentReceipt creates and stores` | End-to-end |
| `generateConsentReceipt anchors to transparency log` | Log integration |
| `presentCredential succeeds for valid credential` | Happy path |
| `presentCredential fails for missing credential` | Error case |
| `getConsentReceipts returns stored receipts` | Retrieval |

**Delete stub:** `TransparencyLogTest.kt`

Tests added: **9**

---

### Slice 7 — FakeCredentialRepository contract tests (rewrite stub)

**File:** `mobile/shared/src/commonTest/.../domain/repository/CredentialRepositoryTest.kt` (replace stub)
**Deps:** `FakeCredentialRepository`, `runTest`

| Test | What it verifies |
|------|-----------------|
| `store then getAll returns it` | Basic CRUD |
| `getById null for unknown` | Miss case |
| `getById returns stored` | Hit case |
| `getByIssuer filters correctly` | Query |
| `markRevoked sets flag` | State mutation |
| `delete removes credential` | Deletion |

Tests added: **6**

---

### Slice 8 — Android mapper tests (ActivityMapper + CachPackMapper)

**File:** `mobile/androidApp/src/test/.../mapper/ActivityMapperTest.kt` (new)
**Production:** `mobile/androidApp/src/main/.../mapper/ActivityMapper.kt`

| Test | What it verifies |
|------|-----------------|
| `toReceiptItem maps title from purpose` | Field mapping |
| `toReceiptItem maps counterparty from rpDisplayName` | Field mapping |
| `toReceiptItem LOGGED when log entry verified` | Status logic |
| `toReceiptItem PENDING when no log entry` | Status logic |
| `toHistoryEntry maps subtitle` | Field mapping |
| `toHistoryEntry sets proof count` | Counting |

**File:** `mobile/androidApp/src/test/.../mapper/CachPackMapperTest.kt` (new)
**Production:** `mobile/androidApp/src/main/.../mapper/CachPackMapper.kt`

| Test | What it verifies |
|------|-----------------|
| `childcare pack has 4 predicates` | Predicate set |
| `age pack has 1 predicate` | Predicate set |
| `seller pack has 4 predicates` | Predicate set |
| `all passed returns cachet name` | Result mapping |
| `not all passed returns Incomplete` | Result mapping |

Tests added: **11**

---

### Slice 9 — Fix broken WalletVerificationFlowTest

**Files:**
- `mobile/androidApp/src/androidTest/.../MockVeriffIntegration.kt`
- `mobile/androidApp/src/androidTest/.../WalletVerificationFlowTest.kt`

**Changes:**
- Fix `requestToken` signature (add `sessionId: String? = null`)
- Add `requestSDJWTCredential` override (throw `NotImplementedError`)
- Replace `Map<String, JsonElement>` credential subjects with typed `CredentialSubject`
- Update `createMockCredential()` helper

Tests added: 0 (fixes 7 existing broken tests)

---

## Summary

| Slice | Scope | New Tests | Running Total |
|-------|-------|-----------|---------------|
| 0 | Infrastructure (deps, fakes, delete dead stubs) | 0 | 0 |
| 1 | SDJWTParser | 7 | 7 |
| 2 | KBJWTBuilder | 3 | 10 |
| 3 | CredentialTest rewrite | 8 | 18 |
| 4 | CredentialQuality | 10 | 28 |
| 5 | IssuanceUseCase rewrite | 7 | 35 |
| 6 | ConsentUseCase (absorbs TransparencyLogTest) | 9 | 44 |
| 7 | CredentialRepository contract | 6 | 50 |
| 8 | ActivityMapper + CachPackMapper | 11 | 61 |
| 9 | Fix WalletVerificationFlowTest | 0 (fixes 7) | 61 |

**Before:** 48 active tests (+ 7 broken in androidTest)
**After:** 109 active tests, all compiling, 0 stubs remaining

---

## Verification

After each slice, run:
```bash
# Shared module tests (slices 1-7)
devenv shell -- bash -c "cd mobile && ./gradlew :shared:testDebugUnitTest"

# Android unit tests (slice 8)
devenv shell -- bash -c "cd mobile && ./gradlew :androidApp:testDebugUnitTest"

# Android instrumented tests (slice 9, requires emulator)
devenv shell -- android:test
```

Final validation:
```bash
devenv shell -- android:test-unit   # all unit tests across all modules
```

---

## Deferred (follow-up)

- **VerificationUseCase tests** — needs `expect` classes (`KeyManager`, `JWEEncryptor`,
  `JWSVerifier`) + `DIDResolver` with HttpClient. Requires androidUnitTest source set
  or logic extraction into pure functions.
- **KBJWTBuilder.build() tests** — same `KeyManager` dependency.
- **DIDResolver tests** — needs Ktor mock engine in commonTest.
- **CredentialRepositoryImpl (SQLDelight)** — needs Android instrumented tests with
  in-memory SQLite driver.
